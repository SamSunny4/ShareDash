using System.Text.Json.Serialization;

namespace ShareDash.Windows.Models;

public class DeviceInfo
{
    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = string.Empty;

    [JsonPropertyName("device_name")]
    public string DeviceName { get; set; } = "ShareDash Desktop";

    [JsonPropertyName("os_name")]
    public string OsName { get; set; } = "windows";

    [JsonPropertyName("app_version")]
    public string AppVersion { get; set; } = "0.1.0";

    [JsonPropertyName("server_port")]
    public int ServerPort { get; set; } = 54321;

    [JsonPropertyName("local_ips")]
    public List<string> LocalIps { get; set; } = new();
}

public class DiscoveredPeer
{
    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = string.Empty;

    [JsonPropertyName("friendly_name")]
    public string FriendlyName { get; set; } = string.Empty;

    [JsonPropertyName("ip_address")]
    public string IpAddress { get; set; } = string.Empty;

    [JsonPropertyName("port")]
    public int Port { get; set; } = 54321;

    [JsonPropertyName("supported_transports")]
    public List<string> SupportedTransports { get; set; } = new();

    [JsonPropertyName("is_paired")]
    public bool IsPaired { get; set; }

    public string DisplaySubtitle => $"{IpAddress}:{Port} • {string.Join(", ", SupportedTransports)}";
}
