using System.Collections.ObjectModel;
using System.Windows;

namespace ShareDash.Windows.Services;

public class LogMessage
{
    public DateTime Timestamp { get; set; } = DateTime.Now;
    public string Level { get; set; } = "INFO";
    public string Message { get; set; } = string.Empty;

    public string FormattedTime => Timestamp.ToString("HH:mm:ss.fff");

    public string Color => Level switch
    {
        "SUCCESS" or "OK" => "#00FFA3",
        "WARN" => "#F59E0B",
        "ERROR" or "FAIL" => "#EF4444",
        "USB" => "#00D2FF",
        "BLE" or "BT" => "#A855F7",
        "WIFI" => "#38BDF8",
        _ => "#94A3B8"
    };

    public string DisplayText => $"[{FormattedTime}] [{Level}] {Message}";
}

public class LogService
{
    private static readonly Lazy<LogService> _instance = new(() => new LogService());
    public static LogService Instance => _instance.Value;

    public ObservableCollection<LogMessage> Messages { get; } = new();

    public event Action<LogMessage>? OnMessageLogged;

    public void Log(string level, string message)
    {
        var item = new LogMessage
        {
            Timestamp = DateTime.Now,
            Level = level.ToUpperInvariant(),
            Message = message
        };

        if (Application.Current?.Dispatcher != null)
        {
            Application.Current.Dispatcher.InvokeAsync(() =>
            {
                if (Messages.Count > 1500)
                {
                    Messages.RemoveAt(0);
                }
                Messages.Add(item);
                OnMessageLogged?.Invoke(item);
            });
        }
        else
        {
            Messages.Add(item);
            OnMessageLogged?.Invoke(item);
        }
    }

    public void Info(string msg) => Log("INFO", msg);
    public void Success(string msg) => Log("SUCCESS", msg);
    public void Warn(string msg) => Log("WARN", msg);
    public void Error(string msg) => Log("ERROR", msg);
    public void Usb(string msg) => Log("USB", msg);
    public void Ble(string msg) => Log("BLE", msg);
    public void Wifi(string msg) => Log("WIFI", msg);

    public void Clear()
    {
        Application.Current?.Dispatcher?.Invoke(() => Messages.Clear());
    }
}
