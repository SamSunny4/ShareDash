using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Security.Cryptography;
using ShareDash.Windows.Models;

namespace ShareDash.Windows.Services;

public class TargetEndpoint
{
    public string Name { get; set; } = "Wi-Fi";
    public string Ip { get; set; } = string.Empty;
    public int Port { get; set; } = 54321;
    public bool IsUsb => Name.Contains("USB", StringComparison.OrdinalIgnoreCase);
}

public class ChunkWork
{
    public uint ChunkId { get; set; }
    public long Offset { get; set; }
    public int Length { get; set; }
}

public class ChannelMetrics
{
    public string Name { get; set; } = string.Empty;
    public long BytesTransferred { get; set; }
    public double CurrentSpeedMbS { get; set; }
    public int ChunksCompleted { get; set; }
}

public class MultipathSender
{
    private readonly LogService _log = LogService.Instance;
    private readonly HttpClient _http;
    private CancellationTokenSource? _cancelCts;

    public bool IsTransferActive { get; private set; }

    public event Action<double, double, double, double, double, List<ChannelMetrics>>? OnProgress;
    // (progressPct, speedMbS, speedGbps, elapsedSecs, etaSecs, channels)

    public event Action<TransferResult>? OnCompleted;
    public event Action<string>? OnFailed;

    public MultipathSender()
    {
        var handler = new SocketsHttpHandler
        {
            PooledConnectionIdleTimeout = TimeSpan.FromSeconds(60),
            MaxConnectionsPerServer = 32,
            EnableMultipleHttp2Connections = false
        };
        _http = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(60) };
    }

    public void Cancel()
    {
        if (IsTransferActive)
        {
            _log.Warn("Cancelling active transfer...");
            _cancelCts?.Cancel();
        }
    }

    public async Task TransferItemsAsync(List<TransferItem> items, List<TargetEndpoint> targets)
    {
        if (items.Count == 0 || targets.Count == 0)
        {
            OnFailed?.Invoke("No items or target endpoints selected.");
            return;
        }

        IsTransferActive = true;
        _cancelCts = new CancellationTokenSource();
        var ct = _cancelCts.Token;

        try
        {
            foreach (var item in items)
            {
                if (ct.IsCancellationRequested) break;

                string filePathToTransmit = item.FilePath;
                bool isTempZip = false;

                if (item.IsDirectory || Directory.Exists(item.FilePath))
                {
                    _log.Info($"Compressing directory '{item.FileName}' into streaming archive...");
                    filePathToTransmit = Path.Combine(Path.GetTempPath(), $"sharedash_{Guid.NewGuid():N}.zip");
                    ZipFile.CreateFromDirectory(item.FilePath, filePathToTransmit, CompressionLevel.Fastest, includeBaseDirectory: true);
                    isTempZip = true;
                }

                try
                {
                    await SendSingleFileAsync(filePathToTransmit, item.FileName, targets, ct);
                }
                finally
                {
                    if (isTempZip && File.Exists(filePathToTransmit))
                    {
                        try { File.Delete(filePathToTransmit); } catch { }
                    }
                }
            }
        }
        catch (OperationCanceledException)
        {
            _log.Warn("Transfer was cancelled.");
            OnFailed?.Invoke("Transfer cancelled by user.");
        }
        catch (Exception ex)
        {
            _log.Error($"Transfer failed: {ex.Message}");
            OnFailed?.Invoke(ex.Message);
        }
        finally
        {
            IsTransferActive = false;
        }
    }

    private async Task SendSingleFileAsync(string filePath, string displayFileName, List<TargetEndpoint> targets, CancellationToken ct)
    {
        var fileInfo = new FileInfo(filePath);
        long fileSize = fileInfo.Length;
        string transferId = Guid.NewGuid().ToString();

        _log.Success($"Starting Multipath Transfer for: '{displayFileName}' ({TransferItem.FormatBytes(fileSize)})");
        foreach (var t in targets)
        {
            _log.Info($"Multipath channel: [{t.Name}] -> {t.Ip}:{t.Port}");
        }

        // Adaptive chunk sizing matching ShareDash core: 1MB for small, 4MB standard, 8MB for >500MB
        int chunkSize = fileSize switch
        {
            > 500 * 1024 * 1024 => 8 * 1024 * 1024,
            > 50 * 1024 * 1024 => 4 * 1024 * 1024,
            _ => 1024 * 1024
        };

        var chunks = new ConcurrentQueue<ChunkWork>();
        uint chunkId = 0;
        for (long offset = 0; offset < fileSize; offset += chunkSize)
        {
            int length = (int)Math.Min(chunkSize, fileSize - offset);
            chunks.Enqueue(new ChunkWork { ChunkId = chunkId++, Offset = offset, Length = length });
        }

        uint totalChunks = chunkId;
        long totalBytesTransferred = 0;
        var stopwatch = Stopwatch.StartNew();

        var channelMetrics = targets.ToDictionary(
            t => t.Name,
            t => new ChannelMetrics { Name = t.Name }
        );

        // Progress sampling task
        var progressTask = Task.Run(async () =>
        {
            while (!ct.IsCancellationRequested && Interlocked.Read(ref totalBytesTransferred) < fileSize)
            {
                await Task.Delay(150, ct);
                double elapsed = Math.Max(0.01, stopwatch.Elapsed.TotalSeconds);
                long transferred = Interlocked.Read(ref totalBytesTransferred);

                double progressPct = fileSize > 0 ? (double)transferred / fileSize : 1.0;
                double speedMbS = (transferred / (1024.0 * 1024.0)) / elapsed;
                double speedGbps = (speedMbS * 8.0) / 1000.0;
                long remainingBytes = Math.Max(0, fileSize - transferred);
                double eta = speedMbS > 0 ? (remainingBytes / (1024.0 * 1024.0)) / speedMbS : 0;

                lock (channelMetrics)
                {
                    foreach (var kvp in channelMetrics)
                    {
                        kvp.Value.CurrentSpeedMbS = (kvp.Value.BytesTransferred / (1024.0 * 1024.0)) / elapsed;
                    }
                    OnProgress?.Invoke(progressPct, speedMbS, speedGbps, elapsed, eta, channelMetrics.Values.ToList());
                }
            }
        }, ct);

        // Spawn parallel chunk workers per channel: 6 workers for USB (high queue depth), 4 for Wi-Fi
        var workerTasks = new List<Task>();
        foreach (var target in targets)
        {
            int workerCount = target.IsUsb ? 6 : 4;
            for (int w = 0; w < workerCount; w++)
            {
                workerTasks.Add(Task.Run(async () =>
                {
                    using var stream = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read, 64 * 1024, FileOptions.Asynchronous);
                    var chunkBuffer = new byte[chunkSize];

                    while (!ct.IsCancellationRequested && chunks.TryDequeue(out var chunk))
                    {
                        stream.Seek(chunk.Offset, SeekOrigin.Begin);
                        int read = await stream.ReadAsync(chunkBuffer.AsMemory(0, chunk.Length), ct);
                        if (read <= 0) break;

                        string crcHex = ComputeCrc32Hex(chunkBuffer, read);

                        using var content = new ByteArrayContent(chunkBuffer, 0, read);
                        content.Headers.Add("x-transfer-id", transferId);
                        content.Headers.Add("x-file-name", displayFileName);
                        content.Headers.Add("x-file-size", fileSize.ToString());
                        content.Headers.Add("x-chunk-id", chunk.ChunkId.ToString());
                        content.Headers.Add("x-chunk-offset", chunk.Offset.ToString());
                        content.Headers.Add("x-chunk-length", read.ToString());
                        content.Headers.Add("x-chunk-crc32", crcHex);
                        content.Headers.Add("x-total-chunks", totalChunks.ToString());
                        content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");

                        string url = $"http://{target.Ip}:{target.Port}/api/v1/transfers/chunk";
                        var response = await _http.PostAsync(url, content, ct);

                        if (response.IsSuccessStatusCode)
                        {
                            Interlocked.Add(ref totalBytesTransferred, read);
                            lock (channelMetrics)
                            {
                                if (channelMetrics.TryGetValue(target.Name, out var m))
                                {
                                    m.BytesTransferred += read;
                                    m.ChunksCompleted++;
                                }
                            }
                        }
                        else
                        {
                            // Re-enqueue for retry
                            chunks.Enqueue(chunk);
                            await Task.Delay(50, ct);
                        }
                    }
                }, ct));
            }
        }

        await Task.WhenAll(workerTasks);
        stopwatch.Stop();

        try { await progressTask; } catch { }

        // Final result calculation
        double totalElapsed = Math.Max(0.01, stopwatch.Elapsed.TotalSeconds);
        double finalSpeedMbS = (fileSize / (1024.0 * 1024.0)) / totalElapsed;

        var usbChannel = channelMetrics.Values.FirstOrDefault(c => c.Name.Contains("USB", StringComparison.OrdinalIgnoreCase));
        var wifiChannel = channelMetrics.Values.FirstOrDefault(c => !c.Name.Contains("USB", StringComparison.OrdinalIgnoreCase));

        double? usbSpeed = usbChannel != null ? (usbChannel.BytesTransferred / (1024.0 * 1024.0)) / totalElapsed : null;
        double? wifiSpeed = wifiChannel != null ? (wifiChannel.BytesTransferred / (1024.0 * 1024.0)) / totalElapsed : null;
        double? usbPct = (usbChannel != null && fileSize > 0) ? ((double)usbChannel.BytesTransferred / fileSize) * 100.0 : null;
        double? wifiPct = (wifiChannel != null && fileSize > 0) ? ((double)wifiChannel.BytesTransferred / fileSize) * 100.0 : null;

        var result = new TransferResult
        {
            FileName = displayFileName,
            SizeMb = fileSize / (1024.0 * 1024.0),
            TimeSecs = totalElapsed,
            AvgSpeedMbS = finalSpeedMbS,
            UsbSpeedMbS = usbSpeed,
            UsbPct = usbPct,
            WifiSpeedMbS = wifiSpeed,
            WifiPct = wifiPct,
            TotalChunks = (int)totalChunks,
            IntegrityOk = true,
            CompletedAt = DateTime.Now
        };

        _log.Success($"Transfer COMPLETE! {result.SizeMb:F2} MB transferred in {result.TimeSecs:F2}s at {result.AvgSpeedMbS:F1} MB/s ({result.AvgSpeedGbps:F2} Gbps)");
        OnCompleted?.Invoke(result);
    }

    private static readonly uint[] Crc32Table = GenerateCrc32Table();

    private static uint[] GenerateCrc32Table()
    {
        uint poly = 0xEDB88320;
        var table = new uint[256];
        for (uint i = 0; i < 256; i++)
        {
            uint crc = i;
            for (int j = 8; j > 0; j--)
            {
                if ((crc & 1) == 1)
                    crc = (crc >> 1) ^ poly;
                else
                    crc >>= 1;
            }
            table[i] = crc;
        }
        return table;
    }

    public static string ComputeCrc32Hex(byte[] data, int length)
    {
        uint crc = 0xFFFFFFFF;
        for (int i = 0; i < length; i++)
        {
            byte b = data[i];
            crc = (crc >> 8) ^ Crc32Table[(crc ^ b) & 0xFF];
        }
        return (~crc).ToString("x8");
    }
}
