using System.Text.Json.Serialization;

namespace ShareDash.Windows.Models;

public class TelemetryData
{
    [JsonPropertyName("transfer_id")]
    public Guid TransferId { get; set; }

    [JsonPropertyName("title")]
    public string Title { get; set; } = string.Empty;

    [JsonPropertyName("status")]
    public string Status { get; set; } = "IDLE"; // ACTIVE, VERIFYING, COMPLETED, CANCELLED, FAILED

    [JsonPropertyName("aggregate")]
    public AggregateMetrics Aggregate { get; set; } = new();

    [JsonPropertyName("transports")]
    public List<TransportMetric> Transports { get; set; } = new();

    [JsonPropertyName("chunk_states")]
    public List<ChunkStateInfo> ChunkStates { get; set; } = new();
}

public class AggregateMetrics
{
    [JsonPropertyName("total_bytes_expected")]
    public ulong TotalBytesExpected { get; set; }

    [JsonPropertyName("total_bytes_transferred")]
    public ulong TotalBytesTransferred { get; set; }

    [JsonPropertyName("progress_pct")]
    public double ProgressPct { get; set; }

    [JsonPropertyName("aggregate_mbps")]
    public double AggregateMbps { get; set; }

    [JsonPropertyName("elapsed_seconds")]
    public double ElapsedSeconds { get; set; }

    [JsonPropertyName("eta_seconds")]
    public double EtaSeconds { get; set; }
}

public class TransportMetric
{
    [JsonPropertyName("transport_id")]
    public string TransportId { get; set; } = string.Empty;

    [JsonPropertyName("transport_kind")]
    public string TransportKind { get; set; } = string.Empty; // "Usb", "WifiDirect", "Lan", "QuicInet"

    [JsonPropertyName("current_mbps")]
    public double CurrentMbps { get; set; }

    [JsonPropertyName("total_bytes")]
    public ulong TotalBytes { get; set; }

    public double CurrentGbps => (CurrentMbps * 8.0) / 1000.0;
}

public class ChunkStateInfo
{
    [JsonPropertyName("chunk_id")]
    public uint ChunkId { get; set; }

    [JsonPropertyName("state")]
    public string State { get; set; } = "Pending"; // Pending, InFlight, Completed, Failed
}

public class TransferResult
{
    public string FileName { get; set; } = string.Empty;
    public double SizeMb { get; set; }
    public double TimeSecs { get; set; }
    public double AvgSpeedMbS { get; set; }
    public double AvgSpeedGbps => (AvgSpeedMbS * 8.0) / 1000.0;
    public double? UsbSpeedMbS { get; set; }
    public double? UsbPct { get; set; }
    public double? WifiSpeedMbS { get; set; }
    public double? WifiPct { get; set; }
    public int TotalChunks { get; set; }
    public bool IntegrityOk { get; set; } = true;
    public DateTime CompletedAt { get; set; } = DateTime.Now;
}
