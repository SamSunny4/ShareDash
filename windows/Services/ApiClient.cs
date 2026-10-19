using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;
using ShareDash.Windows.Models;

namespace ShareDash.Windows.Services;

public class ApiClient
{
    private readonly HttpClient _http;
    private readonly LogService _log = LogService.Instance;

    public string BaseUrl { get; set; } = "http://127.0.0.1:54321";

    public ApiClient(string? baseUrl = null)
    {
        BaseUrl = baseUrl ?? "http://127.0.0.1:54321";
        _http = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };
    }

    public async Task<DeviceInfo?> GetInfoAsync()
    {
        try
        {
            return await _http.GetFromJsonAsync<DeviceInfo>($"{BaseUrl}/api/v1/info");
        }
        catch (Exception ex)
        {
            _log.Warn($"Failed to fetch device info: {ex.Message}");
            return null;
        }
    }

    public async Task<ConnectionBridgesResponse?> GetBridgesAsync()
    {
        try
        {
            return await _http.GetFromJsonAsync<ConnectionBridgesResponse>($"{BaseUrl}/api/v1/adapters");
        }
        catch (Exception ex)
        {
            _log.Warn($"Failed to fetch network bridges: {ex.Message}");
            return null;
        }
    }

    public async Task<List<DiscoveredPeer>> GetDiscoveredPeersAsync()
    {
        try
        {
            var res = await _http.GetFromJsonAsync<List<DiscoveredPeer>>($"{BaseUrl}/api/v1/peers");
            return res ?? new List<DiscoveredPeer>();
        }
        catch
        {
            return new List<DiscoveredPeer>();
        }
    }

    public async Task<bool> TriggerBleScanAsync()
    {
        try
        {
            var res = await _http.PostAsync($"{BaseUrl}/api/v1/ble/scan", null);
            return res.IsSuccessStatusCode;
        }
        catch (Exception ex)
        {
            _log.Error($"Trigger BLE scan error: {ex.Message}");
            return false;
        }
    }

    public async Task<List<BleDevice>> GetBleDevicesAsync()
    {
        try
        {
            var res = await _http.GetFromJsonAsync<List<BleDevice>>($"{BaseUrl}/api/v1/ble/devices");
            return res ?? new List<BleDevice>();
        }
        catch
        {
            return new List<BleDevice>();
        }
    }

    public async Task<(bool success, string ssid, string password, string gateway, string error)> StartHotspotAsync(string band = "5 GHz")
    {
        try
        {
            var payload = JsonContent.Create(new { band });
            var res = await _http.PostAsync($"{BaseUrl}/api/v1/hotspot/start", payload);
            var json = await res.Content.ReadFromJsonAsync<JsonElement>();

            bool ok = json.TryGetProperty("success", out var sProp) && sProp.GetBoolean();
            string ssid = json.TryGetProperty("ssid", out var ssProp) ? ssProp.GetString() ?? "" : "";
            string pass = json.TryGetProperty("password", out var pProp) ? pProp.GetString() ?? "" : "";
            string gw = json.TryGetProperty("gateway", out var gProp) ? gProp.GetString() ?? "" : "192.168.137.1";
            string err = json.TryGetProperty("error", out var eProp) ? eProp.GetString() ?? "" : "";

            return (ok, ssid, pass, gw, err);
        }
        catch (Exception ex)
        {
            return (false, "", "", "", ex.Message);
        }
    }

    public async Task<bool> OpenHotspotSettingsAsync()
    {
        try
        {
            var res = await _http.PostAsync($"{BaseUrl}/api/v1/hotspot/open-settings", null);
            return res.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> ConnectPairAsync(string targetIp, int targetPort, string pin, string? deviceName = null)
    {
        try
        {
            var payload = JsonContent.Create(new
            {
                target_ip = targetIp,
                target_port = targetPort,
                pin_code = pin,
                device_name = deviceName ?? "ShareDash Desktop"
            });
            var res = await _http.PostAsync($"{BaseUrl}/api/v1/pair/connect", payload);
            return res.IsSuccessStatusCode;
        }
        catch (Exception ex)
        {
            _log.Error($"Pair connect error: {ex.Message}");
            return false;
        }
    }

    public async Task<PairingRequest?> GetPairStatusAsync()
    {
        try
        {
            return await _http.GetFromJsonAsync<PairingRequest>($"{BaseUrl}/api/v1/pair/status");
        }
        catch
        {
            return null;
        }
    }

    public async Task<bool> RespondPairAsync(string action, string? targetName = null, string? step = null, string? targetDeviceId = null)
    {
        try
        {
            var payload = JsonContent.Create(new
            {
                action,
                target_name = targetName,
                step,
                target_device_id = targetDeviceId
            });
            var res = await _http.PostAsync($"{BaseUrl}/api/v1/pair/respond", payload);
            return res.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> CancelAllTransfersAsync()
    {
        try
        {
            var res = await _http.PostAsync($"{BaseUrl}/api/v1/transfers/cancel", null);
            return res.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> StartBenchmarkAsync(int sizeGb = 1)
    {
        try
        {
            var payload = JsonContent.Create(new { size_gb = sizeGb });
            var res = await _http.PostAsync($"{BaseUrl}/api/v1/benchmark/start", payload);
            return res.IsSuccessStatusCode;
        }
        catch (Exception ex)
        {
            _log.Error($"Start benchmark error: {ex.Message}");
            return false;
        }
    }
}
