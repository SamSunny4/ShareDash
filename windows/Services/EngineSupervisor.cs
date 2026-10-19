using System.Diagnostics;
using System.IO;
using System.Net.Http;

namespace ShareDash.Windows.Services;

public class EngineSupervisor
{
    private Process? _process;
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(2) };
    private readonly LogService _log = LogService.Instance;

    public bool IsManagedProcess => _process != null && !_process.HasExited;
    public bool IsActive { get; private set; }

    public async Task<bool> CheckIfRunningAsync(int port = 54321)
    {
        try
        {
            var res = await _http.GetAsync($"http://127.0.0.1:{port}/api/v1/info");
            IsActive = res.IsSuccessStatusCode;
            return IsActive;
        }
        catch
        {
            IsActive = false;
            return false;
        }
    }

    public async Task<bool> EnsureEngineStartedAsync(int port = 54321)
    {
        if (await CheckIfRunningAsync(port))
        {
            _log.Success($"ShareDash Engine is already running on port {port}.");
            return true;
        }

        string? exePath = FindEngineBinary();
        if (string.IsNullOrEmpty(exePath) || !File.Exists(exePath))
        {
            _log.Warn("Could not find prebuilt sharedash.exe. Please ensure target/release or dist/sharedash.exe exists, or run 'cargo run' manually.");
            return false;
        }

        _log.Info($"Launching ShareDash Engine: {exePath} on port {port}...");
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = exePath,
                Arguments = $"--port {port}",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                WorkingDirectory = Path.GetDirectoryName(exePath) ?? AppDomain.CurrentDomain.BaseDirectory
            };

            _process = new Process { StartInfo = psi, EnableRaisingEvents = true };
            _process.OutputDataReceived += (_, e) =>
            {
                if (!string.IsNullOrWhiteSpace(e.Data))
                {
                    ParseAndLogEngineOutput(e.Data);
                }
            };
            _process.ErrorDataReceived += (_, e) =>
            {
                if (!string.IsNullOrWhiteSpace(e.Data))
                {
                    _log.Log("ENGINE", e.Data);
                }
            };

            _process.Start();
            _process.BeginOutputReadLine();
            _process.BeginErrorReadLine();

            // Wait up to 5 seconds for the HTTP server to respond
            for (int i = 0; i < 25; i++)
            {
                await Task.Delay(200);
                if (await CheckIfRunningAsync(port))
                {
                    _log.Success($"ShareDash Engine started successfully on port {port} (PID: {_process.Id})");
                    return true;
                }
            }

            _log.Warn("Engine process started, but HTTP endpoint hasn't responded yet.");
            return true;
        }
        catch (Exception ex)
        {
            _log.Error($"Failed to launch ShareDash Engine: {ex.Message}");
            return false;
        }
    }

    private void ParseAndLogEngineOutput(string raw)
    {
        string trimmed = raw.Trim();
        if (trimmed.Contains("USB", StringComparison.OrdinalIgnoreCase))
        {
            _log.Usb(trimmed);
        }
        else if (trimmed.Contains("BLE", StringComparison.OrdinalIgnoreCase) || trimmed.Contains("Bluetooth", StringComparison.OrdinalIgnoreCase))
        {
            _log.Ble(trimmed);
        }
        else if (trimmed.Contains("Wi-Fi", StringComparison.OrdinalIgnoreCase) || trimmed.Contains("Hotspot", StringComparison.OrdinalIgnoreCase))
        {
            _log.Wifi(trimmed);
        }
        else if (trimmed.Contains("ERROR", StringComparison.OrdinalIgnoreCase) || trimmed.Contains("FAIL", StringComparison.OrdinalIgnoreCase))
        {
            _log.Error(trimmed);
        }
        else if (trimmed.Contains("SUCCESS", StringComparison.OrdinalIgnoreCase) || trimmed.Contains("READY", StringComparison.OrdinalIgnoreCase))
        {
            _log.Success(trimmed);
        }
        else
        {
            _log.Info(trimmed);
        }
    }

    public void Stop()
    {
        if (_process != null && !_process.HasExited)
        {
            try
            {
                _log.Info("Stopping ShareDash Engine process...");
                _process.Kill(entireProcessTree: true);
                _process.Dispose();
            }
            catch (Exception ex)
            {
                _log.Warn($"Error stopping engine: {ex.Message}");
            }
            finally
            {
                _process = null;
                IsActive = false;
            }
        }
    }

    private string? FindEngineBinary()
    {
        // Try multiple standard relative locations from working directory or app directory
        var candidates = new[]
        {
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "dist", "sharedash.exe"),
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "target", "release", "sharedash.exe"),
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "target", "debug", "sharedash.exe"),
            Path.Combine(Directory.GetCurrentDirectory(), "dist", "sharedash.exe"),
            Path.Combine(Directory.GetCurrentDirectory(), "target", "release", "sharedash.exe"),
            Path.Combine(Directory.GetCurrentDirectory(), "target", "debug", "sharedash.exe"),
            Path.Combine("E:\\ShareDash", "dist", "sharedash.exe"),
            Path.Combine("E:\\ShareDash", "target", "debug", "sharedash.exe")
        };

        foreach (var c in candidates)
        {
            try
            {
                var full = Path.GetFullPath(c);
                if (File.Exists(full))
                {
                    return full;
                }
            }
            catch { }
        }

        return "sharedash.exe";
    }
}
