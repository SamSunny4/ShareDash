using System.Text.Json.Serialization;

namespace ShareDash.Windows.Models;

public class AdapterInfo
{
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string IpAddress { get; set; } = string.Empty;
    public string InterfaceType { get; set; } = string.Empty;
    public string Status { get; set; } = "Up";
    public string Speed { get; set; } = "1 Gbps";
    public bool IsFastPathCandidate => InterfaceType.Contains("USB", StringComparison.OrdinalIgnoreCase) || 
                                       InterfaceType.Contains("RNDIS", StringComparison.OrdinalIgnoreCase) ||
                                       Name.Contains("Wi-Fi", StringComparison.OrdinalIgnoreCase);
}

public class ConnectionBridgesResponse
{
    [JsonPropertyName("usb")]
    public UsbBridgeStatus Usb { get; set; } = new();

    [JsonPropertyName("wifi_direct")]
    public WifiDirectBridgeStatus WifiDirect { get; set; } = new();

    [JsonPropertyName("lan")]
    public LanBridgeStatus Lan { get; set; } = new();

    [JsonPropertyName("recommended_action")]
    public string RecommendedAction { get; set; } = string.Empty;
}

public class UsbBridgeStatus
{
    [JsonPropertyName("connected")]
    public bool Connected { get; set; }

    [JsonPropertyName("device_model")]
    public string? DeviceModel { get; set; }

    [JsonPropertyName("speed_gbps")]
    public double SpeedGbps { get; set; }

    [JsonPropertyName("is_adb_ready")]
    public bool IsAdbReady { get; set; }

    [JsonPropertyName("message")]
    public string Message { get; set; } = string.Empty;
}

public class WifiDirectBridgeStatus
{
    [JsonPropertyName("available")]
    public bool Available { get; set; }

    [JsonPropertyName("frequency")]
    public string Frequency { get; set; } = string.Empty;

    [JsonPropertyName("speed_mbps")]
    public double SpeedMbps { get; set; }

    [JsonPropertyName("message")]
    public string Message { get; set; } = string.Empty;
}

public class LanBridgeStatus
{
    [JsonPropertyName("connected")]
    public bool Connected { get; set; }

    [JsonPropertyName("local_ip")]
    public string LocalIp { get; set; } = string.Empty;

    [JsonPropertyName("speed_mbps")]
    public double SpeedMbps { get; set; }

    [JsonPropertyName("message")]
    public string Message { get; set; } = string.Empty;
}
