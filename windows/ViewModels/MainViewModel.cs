using System.Collections.ObjectModel;
using System.IO;
using System.Net.NetworkInformation;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;
using Microsoft.Win32;
using ShareDash.Windows.Models;
using ShareDash.Windows.Services;

namespace ShareDash.Windows.ViewModels;

public enum AppScreenState
{
    Scanning,     // Initial radar scan & device discovery
    Connecting,   // Animated connecting sequence
    Connected,    // Ready to transfer (Drag & drop zone)
    Transferring, // Live speed & progress screen (same as phone)
    Completed     // Transfer summary with BLAKE3 verification
}

public class ShareDashPeerItem : ViewModelBase
{
    private string _deviceId = string.Empty;
    public string DeviceId
    {
        get => _deviceId;
        set => SetField(ref _deviceId, value);
    }

    private string _name = string.Empty;
    public string Name
    {
        get => _name;
        set => SetField(ref _name, value);
    }

    private string _ipAddress = string.Empty;
    public string IpAddress
    {
        get => _ipAddress;
        set => SetField(ref _ipAddress, value);
    }

    public int Port { get; set; } = 54321;
    public string OsType { get; set; } = "Phone"; // "Phone" or "PC"

    private string _connectionType = "Wi-Fi Direct";
    public string ConnectionType
    {
        get => _connectionType;
        set => SetField(ref _connectionType, value);
    }

    private string _shortTransport = "Wi-Fi Direct";
    public string ShortTransport
    {
        get => _shortTransport;
        set => SetField(ref _shortTransport, value);
    }

    private bool _hasUsb;
    public bool HasUsb
    {
        get => _hasUsb;
        set => SetField(ref _hasUsb, value);
    }

    private bool _hasWifi;
    public bool HasWifi
    {
        get => _hasWifi;
        set => SetField(ref _hasWifi, value);
    }

    public int Rssi { get; set; } = -50;

    public string Icon => OsType.Equals("PC", StringComparison.OrdinalIgnoreCase) ? "💻" : "📱";
    public string SignalBadge => HasUsb ? "⚡ 3.2 Gbps Line Speed" : "●●●● High Speed";
}

public class MainViewModel : ViewModelBase
{
    private readonly EngineSupervisor _engineSupervisor = new();
    private readonly ApiClient _api = new();
    private readonly TelemetryWebSocketClient _telemetryWs = new();
    private readonly MultipathSender _sender = new();
    private readonly LogService _log = LogService.Instance;
    private readonly DispatcherTimer _pollTimer = new();

    #region Screen State
    private AppScreenState _currentState = AppScreenState.Scanning;
    public AppScreenState CurrentState
    {
        get => _currentState;
        set
        {
            if (SetField(ref _currentState, value))
            {
                OnPropertyChanged(nameof(IsScanning));
                OnPropertyChanged(nameof(IsConnecting));
                OnPropertyChanged(nameof(IsConnected));
                OnPropertyChanged(nameof(IsTransferring));
                OnPropertyChanged(nameof(IsCompleted));
            }
        }
    }

    public bool IsScanning => CurrentState == AppScreenState.Scanning;
    public bool IsConnecting => CurrentState == AppScreenState.Connecting;
    public bool IsConnected => CurrentState == AppScreenState.Connected;
    public bool IsTransferring => CurrentState == AppScreenState.Transferring;
    public bool IsCompleted => CurrentState == AppScreenState.Completed;
    #endregion

    #region Active Device & Connection
    private ShareDashPeerItem? _activeDevice;
    public ShareDashPeerItem? ActiveDevice
    {
        get => _activeDevice;
        set => SetField(ref _activeDevice, value);
    }

    private string _statusSubtext = "Wi-Fi Direct & USB discoverable as ShareDash Desktop";
    public string StatusSubtext
    {
        get => _statusSubtext;
        set => SetField(ref _statusSubtext, value);
    }

    public ObservableCollection<ShareDashPeerItem> DiscoveredDevices { get; } = new();

    private bool _isUsbCableConnected;
    public bool IsUsbCableConnected
    {
        get => _isUsbCableConnected;
        set => SetField(ref _isUsbCableConnected, value);
    }

    private string _usbModelName = string.Empty;
    public string UsbModelName
    {
        get => _usbModelName;
        set => SetField(ref _usbModelName, value);
    }

    private bool _isUsbPlugged;
    public bool IsUsbPlugged
    {
        get => _isUsbPlugged;
        set
        {
            if (SetField(ref _isUsbPlugged, value))
            {
                OnPropertyChanged(nameof(ConnectionMethodText));
            }
        }
    }

    private bool _isUsbPromptOpen;
    public bool IsUsbPromptOpen
    {
        get => _isUsbPromptOpen;
        set => SetField(ref _isUsbPromptOpen, value);
    }

    private ShareDashPeerItem? _pendingTargetDevice;
    public ShareDashPeerItem? PendingTargetDevice
    {
        get => _pendingTargetDevice;
        set => SetField(ref _pendingTargetDevice, value);
    }

    public string ConnectionMethodText
    {
        get
        {
            if (ActiveDevice == null) return "Wi-Fi Direct";
            bool hasUsb = ActiveDevice.HasUsb || IsUsbPlugged || IsUsbCableConnected;
            bool hasWifi = ActiveDevice.HasWifi || !hasUsb;

            if (hasUsb && hasWifi)
                return "⚡ USB Fast-Path + 📶 Wi-Fi Direct";
            if (hasUsb)
                return "⚡ USB 3.x Cable Fast-Path (3.2 Gbps)";
            return "📶 Wi-Fi Direct (5GHz Zero-LAN)";
        }
    }
    #endregion

    #region Live Transfer Metrics (Same as Phone)
    private string _currentFileName = string.Empty;
    public string CurrentFileName
    {
        get => _currentFileName;
        set => SetField(ref _currentFileName, value);
    }

    private double _progressPercentage;
    public double ProgressPercentage
    {
        get => _progressPercentage;
        set => SetField(ref _progressPercentage, value);
    }

    private double _currentSpeedMbS;
    public double CurrentSpeedMbS
    {
        get => _currentSpeedMbS;
        set => SetField(ref _currentSpeedMbS, value);
    }

    private double _currentSpeedGbps;
    public double CurrentSpeedGbps
    {
        get => _currentSpeedGbps;
        set => SetField(ref _currentSpeedGbps, value);
    }

    private string _formattedTransferred = "0 MB";
    public string FormattedTransferred
    {
        get => _formattedTransferred;
        set => SetField(ref _formattedTransferred, value);
    }

    private string _formattedTotal = "0 MB";
    public string FormattedTotal
    {
        get => _formattedTotal;
        set => SetField(ref _formattedTotal, value);
    }

    private string _etaTimeFormatted = "--:--";
    public string EtaTimeFormatted
    {
        get => _etaTimeFormatted;
        set => SetField(ref _etaTimeFormatted, value);
    }

    private double _usbSpeedMbS;
    public double UsbSpeedMbS
    {
        get => _usbSpeedMbS;
        set => SetField(ref _usbSpeedMbS, value);
    }

    private double _wifiSpeedMbS;
    public double WifiSpeedMbS
    {
        get => _wifiSpeedMbS;
        set => SetField(ref _wifiSpeedMbS, value);
    }

    private TransferResult? _latestResult;
    public TransferResult? LatestResult
    {
        get => _latestResult;
        set => SetField(ref _latestResult, value);
    }

    public ObservableCollection<ChunkVisualItem> VisualChunks { get; } = new();

    private int _totalChunksCount;
    public int TotalChunksCount
    {
        get => _totalChunksCount;
        set => SetField(ref _totalChunksCount, value);
    }

    private int _completedChunksCount;
    public int CompletedChunksCount
    {
        get => _completedChunksCount;
        set => SetField(ref _completedChunksCount, value);
    }

    private int _usbChunksCount;
    public int UsbChunksCount
    {
        get => _usbChunksCount;
        set => SetField(ref _usbChunksCount, value);
    }

    private int _wifiChunksCount;
    public int WifiChunksCount
    {
        get => _wifiChunksCount;
        set => SetField(ref _wifiChunksCount, value);
    }

    private int _inFlightChunksCount;
    public int InFlightChunksCount
    {
        get => _inFlightChunksCount;
        set => SetField(ref _inFlightChunksCount, value);
    }

    private int _pendingChunksCount;
    public int PendingChunksCount
    {
        get => _pendingChunksCount;
        set => SetField(ref _pendingChunksCount, value);
    }

    private double _usbProgress;
    public double UsbProgress
    {
        get => _usbProgress;
        set => SetField(ref _usbProgress, value);
    }

    private double _wifiProgress;
    public double WifiProgress
    {
        get => _wifiProgress;
        set => SetField(ref _wifiProgress, value);
    }

    private double _peakSpeedMbS;
    public double PeakSpeedMbS
    {
        get => _peakSpeedMbS;
        set => SetField(ref _peakSpeedMbS, value);
    }
    #endregion

    #region Commands
    public ICommand SelectDeviceCommand { get; }
    public ICommand DisconnectCommand { get; }
    public ICommand SelectFilesCommand { get; }
    public ICommand SelectFolderCommand { get; }
    public ICommand CancelTransferCommand { get; }
    public ICommand BackToScanningCommand { get; }
    public ICommand ContinueWithWifiCommand { get; }
    public ICommand CheckUsbAndConnectCommand { get; }
    public ICommand DismissUsbPromptCommand { get; }
    #endregion

    public MainViewModel()
    {
        SelectDeviceCommand = new RelayCommand(param => ConnectToDevice(param as ShareDashPeerItem));
        DisconnectCommand = new RelayCommand(Disconnect);
        SelectFilesCommand = new RelayCommand(PickFilesAndTransfer);
        SelectFolderCommand = new RelayCommand(PickFolderAndTransfer);
        CancelTransferCommand = new RelayCommand(CancelTransfer);
        BackToScanningCommand = new RelayCommand(BackToScanning);

        ContinueWithWifiCommand = new RelayCommand(_ =>
        {
            IsUsbPromptOpen = false;
            ProceedConnect(PendingTargetDevice);
        });

        CheckUsbAndConnectCommand = new RelayCommand(async _ =>
        {
            IsUsbPromptOpen = false;
            await PollDevicesAsync();
            ProceedConnect(PendingTargetDevice);
        });

        DismissUsbPromptCommand = new RelayCommand(_ =>
        {
            IsUsbPromptOpen = false;
            PendingTargetDevice = null;
        });

        // Multipath progress wire
        _sender.OnProgress += (pct, speedMbS, speedGbps, elapsed, eta, channels) =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                ProgressPercentage = Math.Round(pct * 100, 1);
                CurrentSpeedMbS = Math.Round(speedMbS, 1);
                CurrentSpeedGbps = Math.Round(speedGbps, 2);
                if (speedMbS > PeakSpeedMbS)
                {
                    PeakSpeedMbS = Math.Round(speedMbS, 1);
                }
                EtaTimeFormatted = eta > 0 ? $"{TimeSpan.FromSeconds(eta):mm\\:ss}s" : "--:--";

                var usb = channels.FirstOrDefault(c => c.Name.Contains("USB", StringComparison.OrdinalIgnoreCase));
                var wifi = channels.FirstOrDefault(c => !c.Name.Contains("USB", StringComparison.OrdinalIgnoreCase));

                UsbSpeedMbS = Math.Round(usb?.CurrentSpeedMbS ?? 0, 1);
                WifiSpeedMbS = Math.Round(wifi?.CurrentSpeedMbS ?? 0, 1);

                double totalProg = Math.Clamp(pct, 0.0, 1.0);
                double totalB = (usb?.BytesTransferred ?? 0) + (wifi?.BytesTransferred ?? 0);
                if (totalB > 0)
                {
                    UsbProgress = Math.Clamp(totalProg * ((double)(usb?.BytesTransferred ?? 0) / totalB), 0.0, 1.0);
                    WifiProgress = Math.Clamp(totalProg - UsbProgress, 0.0, 1.0);
                }
                else
                {
                    UsbProgress = 0.0;
                    WifiProgress = totalProg;
                }
            });
        };

        _sender.OnTotalChunksDetermined += total =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                TotalChunksCount = total;
                CompletedChunksCount = 0;
                UsbChunksCount = 0;
                WifiChunksCount = 0;
                InFlightChunksCount = 0;
                PendingChunksCount = total;
                UsbProgress = 0;
                WifiProgress = 0;
                PeakSpeedMbS = 0;

                int displayCount = Math.Clamp(total, 16, 96);
                VisualChunks.Clear();
                for (int i = 0; i < displayCount; i++)
                {
                    VisualChunks.Add(new ChunkVisualItem { ChunkId = i, State = ChunkVisualState.Pending });
                }
            });
        };

        _sender.OnChunkStarted += chunkId =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                int displayCount = VisualChunks.Count;
                if (displayCount > 0 && TotalChunksCount > 0)
                {
                    int idx = (int)((chunkId * (long)displayCount) / Math.Max(1, TotalChunksCount));
                    idx = Math.Clamp(idx, 0, displayCount - 1);
                    if (VisualChunks[idx].State == ChunkVisualState.Pending)
                    {
                        VisualChunks[idx].State = ChunkVisualState.InFlight;
                        InFlightChunksCount++;
                        PendingChunksCount = Math.Max(0, TotalChunksCount - CompletedChunksCount - InFlightChunksCount);
                    }
                }
            });
        };

        _sender.OnChunkCompleted += (chunkId, isUsb) =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                CompletedChunksCount++;
                if (isUsb) UsbChunksCount++; else WifiChunksCount++;
                InFlightChunksCount = Math.Max(0, InFlightChunksCount - 1);
                PendingChunksCount = Math.Max(0, TotalChunksCount - CompletedChunksCount - InFlightChunksCount);

                int displayCount = VisualChunks.Count;
                if (displayCount > 0 && TotalChunksCount > 0)
                {
                    int idx = (int)((chunkId * (long)displayCount) / Math.Max(1, TotalChunksCount));
                    idx = Math.Clamp(idx, 0, displayCount - 1);
                    VisualChunks[idx].State = isUsb ? ChunkVisualState.CompletedUsb : ChunkVisualState.CompletedWifi;
                }
            });
        };

        _sender.OnCompleted += result =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                LatestResult = result;
                CurrentState = AppScreenState.Completed;
            });
        };

        _sender.OnFailed += err =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                _log.Error($"Transfer error: {err}");
                CurrentState = AppScreenState.Connected;
            });
        };

        // Telemetry WebSocket wire (listens for incoming transfers from phone/peer)
        _telemetryWs.OnTelemetryReceived += telem =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                if (telem.Status.Equals("ACTIVE", StringComparison.OrdinalIgnoreCase) ||
                    telem.Status.Equals("TRANSFERRING", StringComparison.OrdinalIgnoreCase))
                {
                    if (CurrentState != AppScreenState.Transferring)
                    {
                        CurrentState = AppScreenState.Transferring;
                    }

                    CurrentFileName = telem.Title;
                    ProgressPercentage = Math.Round(telem.Aggregate.ProgressPct, 1);
                    CurrentSpeedMbS = Math.Round(telem.Aggregate.AggregateMbps / 8.0, 1);
                    CurrentSpeedGbps = Math.Round(telem.Aggregate.AggregateMbps / 1000.0, 2);
                    EtaTimeFormatted = telem.Aggregate.EtaSeconds > 0 ? $"{TimeSpan.FromSeconds(telem.Aggregate.EtaSeconds):mm\\:ss}s" : "--:--";

                    FormattedTransferred = $"{telem.Aggregate.TotalBytesTransferred / (1024.0 * 1024.0):F1} MB";
                    FormattedTotal = $"{telem.Aggregate.TotalBytesExpected / (1024.0 * 1024.0):F1} MB";

                    double speedFromPhone = telem.Aggregate.AggregateMbps / 8.0;
                    if (speedFromPhone > PeakSpeedMbS)
                    {
                        PeakSpeedMbS = Math.Round(speedFromPhone, 1);
                    }

                    var usbTrans = telem.Transports.FirstOrDefault(t => t.TransportKind.Contains("Usb", StringComparison.OrdinalIgnoreCase));
                    var wifiTrans = telem.Transports.FirstOrDefault(t => !t.TransportKind.Contains("Usb", StringComparison.OrdinalIgnoreCase));

                    UsbSpeedMbS = Math.Round((usbTrans?.CurrentMbps ?? 0) / 8.0, 1);
                    WifiSpeedMbS = Math.Round((wifiTrans?.CurrentMbps ?? 0) / 8.0, 1);

                    ulong usbBytes = usbTrans?.TotalBytes ?? 0;
                    ulong wifiBytes = wifiTrans?.TotalBytes ?? 0;
                    ulong totalB = usbBytes + wifiBytes;

                    double totalProg = Math.Clamp(telem.Aggregate.ProgressPct / 100.0, 0.0, 1.0);
                    if (totalB > 0)
                    {
                        UsbProgress = Math.Clamp(totalProg * ((double)usbBytes / totalB), 0.0, 1.0);
                        WifiProgress = Math.Clamp(totalProg - UsbProgress, 0.0, 1.0);
                    }
                    else
                    {
                        UsbProgress = 0.0;
                        WifiProgress = totalProg;
                    }

                    if (VisualChunks.Count == 0)
                    {
                        int displayCount = 64;
                        for (int i = 0; i < displayCount; i++)
                        {
                            VisualChunks.Add(new ChunkVisualItem { ChunkId = i, State = ChunkVisualState.Pending });
                        }
                        TotalChunksCount = 64;
                    }

                    int completedVisual = (int)(totalProg * VisualChunks.Count);
                    int usbVisual = (int)(UsbProgress * VisualChunks.Count);

                    for (int i = 0; i < VisualChunks.Count; i++)
                    {
                        if (i < usbVisual)
                            VisualChunks[i].State = ChunkVisualState.CompletedUsb;
                        else if (i < completedVisual)
                            VisualChunks[i].State = ChunkVisualState.CompletedWifi;
                        else
                            VisualChunks[i].State = ChunkVisualState.Pending;
                    }
                    CompletedChunksCount = completedVisual;
                    UsbChunksCount = usbVisual;
                    WifiChunksCount = Math.Max(0, completedVisual - usbVisual);
                    PendingChunksCount = Math.Max(0, VisualChunks.Count - completedVisual);
                }
                else if (telem.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase))
                {
                    if (CurrentState == AppScreenState.Transferring)
                    {
                        LatestResult = new TransferResult
                        {
                            FileName = telem.Title,
                            SizeMb = telem.Aggregate.TotalBytesExpected / (1024.0 * 1024.0),
                            TimeSecs = telem.Aggregate.ElapsedSeconds,
                            AvgSpeedMbS = telem.Aggregate.AggregateMbps / 8.0,
                            IntegrityOk = true
                        };
                        CurrentState = AppScreenState.Completed;
                    }
                }
            });
        };

        // Periodic poller (every 1.5s) for USB status and Wi-Fi Direct peers
        _pollTimer.Interval = TimeSpan.FromSeconds(1.5);
        _pollTimer.Tick += async (_, _) => await PollDevicesAsync();
        _pollTimer.Start();

        // Auto-initialize: Start engine, ensure discoverable over Wi-Fi Direct and USB
        _ = InitializeAppAsync();
    }

    private async Task InitializeAppAsync()
    {
        _log.Info("Opening ShareDash in Wi-Fi Direct & USB Mode...");
        StatusSubtext = "Wi-Fi Direct & USB discoverable as ShareDash Desktop";
        await _engineSupervisor.EnsureEngineStartedAsync(54321);
        _telemetryWs.Start();
    }

    private async Task PollDevicesAsync()
    {
        // 1. Check USB Cable / ADB connection
        var bridges = await _api.GetBridgesAsync();
        bool usbCablePlugged = bridges != null && bridges.Usb.Connected;
        string? usbModel = bridges?.Usb?.DeviceModel;

        IsUsbCableConnected = usbCablePlugged;
        UsbModelName = usbModel ?? "Connected Phone";

        // 2. Poll UDP / Wi-Fi Direct discovered peers
        var peers = await _api.GetDiscoveredPeersAsync();

        // Track devices seen in this polling tick
        var seenNormalizedNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        // A) Process USB ADB Bridge if connected
        if (usbCablePlugged && !string.IsNullOrWhiteSpace(usbModel))
        {
            string normName = CleanDeviceName(usbModel);
            seenNormalizedNames.Add(normName);
            AddOrUpdateMergedPeer(normName, "127.0.0.1", 54321, "Phone", isUsbAdb: true, isUsbTether: false, isWifi: false);
        }

        // B) Process UDP / Wi-Fi Direct / Tethering peers
        foreach (var p in peers)
        {
            if (string.IsNullOrWhiteSpace(p.FriendlyName)) continue;
            string normName = CleanDeviceName(p.FriendlyName);
            seenNormalizedNames.Add(normName);

            string os = p.FriendlyName.Contains("PC", StringComparison.OrdinalIgnoreCase) || p.FriendlyName.Contains("Desktop", StringComparison.OrdinalIgnoreCase) ? "PC" : "Phone";

            bool hasTether = p.SupportedTransports.Any(t => t.Contains("tether", StringComparison.OrdinalIgnoreCase) || t.Contains("usb", StringComparison.OrdinalIgnoreCase))
                          || p.FriendlyName.Contains("Tethering", StringComparison.OrdinalIgnoreCase);
            bool hasWifi = p.SupportedTransports.Any(t => t.Contains("wifi", StringComparison.OrdinalIgnoreCase) || t.Contains("udp", StringComparison.OrdinalIgnoreCase) || t.Contains("lan", StringComparison.OrdinalIgnoreCase))
                        || (!hasTether && !p.FriendlyName.Contains("USB", StringComparison.OrdinalIgnoreCase));

            AddOrUpdateMergedPeer(normName, p.IpAddress, p.Port, os, isUsbAdb: false, isUsbTether: hasTether, isWifi: hasWifi);
        }

        // C) Prune peers that are no longer advertising
        var toRemove = DiscoveredDevices.Where(d => !seenNormalizedNames.Contains(CleanDeviceName(d.Name))).ToList();
        foreach (var r in toRemove)
        {
            DiscoveredDevices.Remove(r);
        }

        // Update overall USB plugged status
        IsUsbPlugged = usbCablePlugged || DiscoveredDevices.Any(d => d.HasUsb);
    }

    public static string CleanDeviceName(string raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "Device";
        // Remove parenthesized suffixes like (ADB 3.2 Gbps), (USB ADB), (USB Tethering), (Wi-Fi), etc.
        string s = Regex.Replace(raw, @"\s*\([^)]*\)", "").Trim();
        s = Regex.Replace(s, @"\s*(?:USB|ADB|Tethering|Wi-Fi|Wifi|LAN|Bluetooth|Fast-Path)\b.*$", "", RegexOptions.IgnoreCase).Trim();
        s = Regex.Replace(s, @"^(SM)\s+([A-Za-z0-9]+)", "$1-$2", RegexOptions.IgnoreCase);
        return string.IsNullOrWhiteSpace(s) ? raw.Trim() : s;
    }

    private void AddOrUpdateMergedPeer(string normName, string ip, int port, string os, bool isUsbAdb, bool isUsbTether, bool isWifi)
    {
        var existing = DiscoveredDevices.FirstOrDefault(d => string.Equals(CleanDeviceName(d.Name), normName, StringComparison.OrdinalIgnoreCase));
        if (existing == null)
        {
            existing = new ShareDashPeerItem
            {
                DeviceId = normName.ToLowerInvariant().Replace(" ", "-"),
                Name = normName,
                IpAddress = ip,
                Port = port,
                OsType = os,
                HasUsb = isUsbAdb || isUsbTether,
                HasWifi = isWifi
            };
            UpdateTransportBadges(existing);
            DiscoveredDevices.Add(existing);
        }
        else
        {
            if (isUsbAdb || isUsbTether)
            {
                existing.HasUsb = true;
                // Prioritize fast USB IP if available
                if (!string.IsNullOrWhiteSpace(ip) && (ip.StartsWith("127.") || ip.StartsWith("192.168.42.")))
                {
                    existing.IpAddress = ip;
                    existing.Port = port;
                }
            }
            if (isWifi)
            {
                existing.HasWifi = true;
                if (string.IsNullOrWhiteSpace(existing.IpAddress) || (!existing.HasUsb && !ip.StartsWith("127.")))
                {
                    existing.IpAddress = ip;
                    existing.Port = port;
                }
            }
            UpdateTransportBadges(existing);
        }
    }

    private static void UpdateTransportBadges(ShareDashPeerItem item)
    {
        if (item.HasUsb && item.HasWifi)
        {
            item.ConnectionType = "⚡ USB 3.2 Gbps • 📶 Wi-Fi Direct (Multipath)";
            item.ShortTransport = "⚡ USB + 📶 Wi-Fi";
        }
        else if (item.HasUsb)
        {
            item.ConnectionType = "⚡ USB-C 3.x Cable (3.2 Gbps Line Speed)";
            item.ShortTransport = "⚡ USB 3.2 Gbps";
        }
        else
        {
            item.ConnectionType = "📶 Wi-Fi Direct (Zero-LAN)";
            item.ShortTransport = "📶 Wi-Fi Direct";
        }
    }

    public void ConnectToDevice(ShareDashPeerItem? peer)
    {
        if (peer == null) return;

        // If connection was found through Wi-Fi and USB is not connected, pop up USB-C prompt
        bool isUsbAvailable = peer.HasUsb || IsUsbPlugged || IsUsbCableConnected;
        if (!isUsbAvailable)
        {
            PendingTargetDevice = peer;
            IsUsbPromptOpen = true;
            return;
        }

        ProceedConnect(peer);
    }

    public void ProceedConnect(ShareDashPeerItem? peer)
    {
        if (peer == null) return;
        IsUsbPromptOpen = false;
        ActiveDevice = peer;
        OnPropertyChanged(nameof(ConnectionMethodText));
        CurrentState = AppScreenState.Connecting;

        // Perform connection animation & pairing handshake
        Task.Run(async () =>
        {
            _log.Info($"Connecting to {peer.Name} ({peer.IpAddress})...");
            string pin = new Random().Next(100000, 999999).ToString();

            // Attempt pair connect if target has a valid IPv4
            if (!string.IsNullOrWhiteSpace(peer.IpAddress) && peer.IpAddress.Contains('.'))
            {
                await _api.ConnectPairAsync(peer.IpAddress, peer.Port, pin, "ShareDash Desktop");
            }

            // Smooth 700ms animation feel
            await Task.Delay(750);

            Application.Current.Dispatcher.Invoke(() =>
            {
                CurrentState = AppScreenState.Connected;
                OnPropertyChanged(nameof(ConnectionMethodText));
                _log.Success($"Connected to {peer.Name}! Ready to drag and drop files.");
            });
        });
    }

    public void Disconnect()
    {
        ActiveDevice = null;
        CurrentState = AppScreenState.Scanning;
        StatusSubtext = "Wi-Fi Direct & USB discoverable as ShareDash Desktop";
    }

    public void BackToScanning()
    {
        CurrentState = AppScreenState.Scanning;
    }

    public void PickFilesAndTransfer()
    {
        var dlg = new OpenFileDialog
        {
            Multiselect = true,
            Title = "Select Files to Transfer via ShareDash"
        };

        if (dlg.ShowDialog() == true && dlg.FileNames.Length > 0)
        {
            StartTransferForPaths(dlg.FileNames);
        }
    }

    public void PickFolderAndTransfer()
    {
        var dlg = new OpenFolderDialog
        {
            Title = "Select Folder to Transfer via ShareDash"
        };

        if (dlg.ShowDialog() == true && !string.IsNullOrEmpty(dlg.FolderName))
        {
            StartTransferForPaths(new[] { dlg.FolderName });
        }
    }

    public void StartTransferForPaths(string[] paths)
    {
        if (ActiveDevice == null) return;

        var items = new List<TransferItem>();
        foreach (var p in paths)
        {
            bool isDir = Directory.Exists(p);
            long sz = 0;
            if (isDir)
            {
                try
                {
                    sz = Directory.GetFiles(p, "*", SearchOption.AllDirectories).Select(f => new FileInfo(f).Length).Sum();
                }
                catch { }
            }
            else if (File.Exists(p))
            {
                sz = new FileInfo(p).Length;
            }

            items.Add(new TransferItem
            {
                FilePath = p,
                FileSizeBytes = sz,
                IsDirectory = isDir
            });
        }

        if (items.Count == 0) return;

        CurrentFileName = items[0].FileName;
        CurrentState = AppScreenState.Transferring;

        var targets = new List<TargetEndpoint>();

        // If USB connected, add USB endpoint (3.2 Gbps)
        if (IsUsbCableConnected || ActiveDevice.HasUsb)
        {
            targets.Add(new TargetEndpoint
            {
                Name = "⚡ USB 3.x Cable Fast-Path",
                Ip = ActiveDevice.IpAddress.StartsWith("127.") ? "127.0.0.1" : (ActiveDevice.IpAddress.StartsWith("192.168.42.") ? ActiveDevice.IpAddress : "192.168.42.129"),
                Port = ActiveDevice.Port
            });
        }

        // Add Wi-Fi endpoint
        if (!string.IsNullOrWhiteSpace(ActiveDevice.IpAddress) && ActiveDevice.IpAddress.Contains('.') && !ActiveDevice.IpAddress.StartsWith("127."))
        {
            targets.Add(new TargetEndpoint
            {
                Name = "📶 Wi-Fi Direct High-Speed",
                Ip = ActiveDevice.IpAddress,
                Port = ActiveDevice.Port
            });
        }
        else if (!ActiveDevice.HasUsb)
        {
            // Fallback default hotspot/direct IP
            targets.Add(new TargetEndpoint
            {
                Name = "📶 Wi-Fi Direct",
                Ip = "192.168.137.2",
                Port = ActiveDevice.Port
            });
        }

        Task.Run(async () =>
        {
            await _sender.TransferItemsAsync(items, targets);
        });
    }

    public void CancelTransfer()
    {
        _sender.Cancel();
        _ = _api.CancelAllTransfersAsync();
        CurrentState = AppScreenState.Connected;
    }

    public void StopEngine()
    {
        _engineSupervisor.Stop();
        _telemetryWs.Stop();
    }
}
