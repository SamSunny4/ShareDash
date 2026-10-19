using System.IO;

namespace ShareDash.Windows.Models;

public class TransferItem
{
    public string FilePath { get; set; } = string.Empty;
    public string FileName => Path.GetFileName(FilePath);
    public long FileSizeBytes { get; set; }
    public bool IsDirectory { get; set; }

    public string FormattedSize => FormatBytes(FileSizeBytes);

    public string Icon => IsDirectory ? "📁" : GetFileIcon(FileName);

    public static string FormatBytes(long bytes)
    {
        string[] suffixes = { "B", "KB", "MB", "GB", "TB" };
        int counter = 0;
        decimal number = bytes;
        while (Math.Round(number / 1024) >= 1)
        {
            number /= 1024;
            counter++;
        }
        return $"{number:n1} {suffixes[counter]}";
    }

    private static string GetFileIcon(string name)
    {
        var ext = Path.GetExtension(name).ToLowerInvariant();
        return ext switch
        {
            ".mp4" or ".mkv" or ".mov" or ".avi" => "🎬",
            ".mp3" or ".wav" or ".flac" or ".aac" => "🎵",
            ".jpg" or ".jpeg" or ".png" or ".webp" or ".gif" => "🖼️",
            ".zip" or ".rar" or ".7z" or ".tar" or ".gz" => "📦",
            ".pdf" or ".doc" or ".docx" or ".txt" => "📄",
            ".exe" or ".msi" or ".apk" => "⚡",
            _ => "📄"
        };
    }
}
