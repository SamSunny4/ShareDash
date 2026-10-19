namespace ShareDash.Windows.Models;

public enum ChunkVisualState
{
    Pending,
    InFlight,
    CompletedUsb,
    CompletedWifi
}

public class ChunkVisualItem
{
    public int ChunkId { get; set; }
    public ChunkVisualState State { get; set; } = ChunkVisualState.Pending;
}
