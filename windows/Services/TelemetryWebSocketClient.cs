using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Windows;
using ShareDash.Windows.Models;

namespace ShareDash.Windows.Services;

public class TelemetryWebSocketClient
{
    private ClientWebSocket? _ws;
    private CancellationTokenSource? _cts;
    private readonly LogService _log = LogService.Instance;

    public bool IsConnected => _ws != null && _ws.State == WebSocketState.Open;

    public event Action<TelemetryData>? OnTelemetryReceived;
    public event Action<bool>? OnConnectionStateChanged;

    public void Start(string wsUrl = "ws://127.0.0.1:54321/ws/telemetry")
    {
        Stop();
        _cts = new CancellationTokenSource();
        _ = RunConnectionLoopAsync(wsUrl, _cts.Token);
    }

    public void Stop()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;

        if (_ws != null)
        {
            try
            {
                if (_ws.State == WebSocketState.Open)
                {
                    _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "Client closing", CancellationToken.None).Wait(500);
                }
                _ws.Dispose();
            }
            catch { }
            _ws = null;
        }

        NotifyState(false);
    }

    private async Task RunConnectionLoopAsync(string wsUrl, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                _ws = new ClientWebSocket();
                _ws.Options.KeepAliveInterval = TimeSpan.FromSeconds(5);
                await _ws.ConnectAsync(new Uri(wsUrl), ct);

                NotifyState(true);
                _log.Info("Connected to real-time Telemetry feed.");

                var buffer = new byte[64 * 1024];
                var ms = new MemoryStream();

                while (_ws.State == WebSocketState.Open && !ct.IsCancellationRequested)
                {
                    ms.SetLength(0);
                    WebSocketReceiveResult result;
                    do
                    {
                        result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
                        if (result.MessageType == WebSocketMessageType.Close)
                        {
                            break;
                        }
                        ms.Write(buffer, 0, result.Count);
                    }
                    while (!result.EndOfMessage);

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        break;
                    }

                    if (ms.Length > 0)
                    {
                        ms.Position = 0;
                        try
                        {
                            var telemetry = await JsonSerializer.DeserializeAsync<TelemetryData>(ms, cancellationToken: ct);
                            if (telemetry != null)
                            {
                                DispatchTelemetry(telemetry);
                            }
                        }
                        catch { }
                    }
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception)
            {
                NotifyState(false);
                // Wait 2 seconds before reconnecting
                try
                {
                    await Task.Delay(2000, ct);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
            }
            finally
            {
                NotifyState(false);
            }
        }
    }

    private void DispatchTelemetry(TelemetryData data)
    {
        if (Application.Current?.Dispatcher != null)
        {
            Application.Current.Dispatcher.InvokeAsync(() => OnTelemetryReceived?.Invoke(data));
        }
        else
        {
            OnTelemetryReceived?.Invoke(data);
        }
    }

    private void NotifyState(bool connected)
    {
        if (Application.Current?.Dispatcher != null)
        {
            Application.Current.Dispatcher.InvokeAsync(() => OnConnectionStateChanged?.Invoke(connected));
        }
        else
        {
            OnConnectionStateChanged?.Invoke(connected);
        }
    }
}
