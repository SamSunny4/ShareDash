using System.Text.Json.Serialization;

namespace ShareDash.Windows.Models;

public class BleDevice
{
    [JsonPropertyName("name")]
    public string Name { get; set; } = "Unknown Device";

    [JsonPropertyName("address")]
    public string Address { get; set; } = string.Empty;

    [JsonPropertyName("rssi")]
    public int Rssi { get; set; } = -70;

    [JsonPropertyName("peripheral_id")]
    public string PeripheralId { get; set; } = string.Empty;

    public string SignalQuality => Rssi switch
    {
        >= -50 => "Excellent (●●●●)",
        >= -65 => "Good (●●●○)",
        >= -80 => "Fair (●●○○)",
        _ => "Weak (●○○○)"
    };

    public string SignalColor => Rssi switch
    {
        >= -60 => "#00FFA3",
        >= -75 => "#00D2FF",
        _ => "#F59E0B"
    };

    public int SignalPercent => Math.Clamp((int)((Rssi + 100) * 1.66), 5, 100);
}

public class PairingRequest
{
    [JsonPropertyName("initiator_device_id")]
    public string InitiatorDeviceId { get; set; } = string.Empty;

    [JsonPropertyName("initiator_name")]
    public string InitiatorName { get; set; } = string.Empty;

    [JsonPropertyName("initiator_ip")]
    public string InitiatorIp { get; set; } = string.Empty;

    [JsonPropertyName("initiator_port")]
    public int InitiatorPort { get; set; } = 54321;

    [JsonPropertyName("pin_code")]
    public string PinCode { get; set; } = string.Empty;

    [JsonPropertyName("app_version")]
    public string AppVersion { get; set; } = "0.1.0";

    [JsonPropertyName("status")]
    public string Status { get; set; } = "PENDING";

    [JsonPropertyName("timestamp_ms")]
    public long TimestampMs { get; set; }
}
