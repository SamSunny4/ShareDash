using System.Collections.ObjectModel;
using System.IO;
using System.Net.NetworkInformation;
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
    public string DeviceId { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string IpAddress { get; set; } = string.Empty;
    public int Port { get; set; } = 54321;
    public string OsType { get; set; } = "Phone"; // "Phone" or "PC"
    public string ConnectionType { get; set; } = "Bluetooth LE • Wi-Fi Direct";
    public int Rssi { get; set; } = -65;

    public string Icon => OsType.Equals("PC", StringComparison.OrdinalIgnoreCase) ? "💻" : "📱";
    public string SignalBadge => Rssi >= -60 ? "●●●● High Speed" : "●●●○ Good";
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

    private string _statusSubtext = "Bluetooth & Wi-Fi discoverable as ShareDash Desktop";
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

    private bool _isBluetoothEnabled = true;
    public bool IsBluetoothEnabled
    {
        get => _isBluetoothEnabled;
        set
        {
            if (SetField(ref _isBluetoothEnabled, value))
            {
                OnPropertyChanged(nameof(IsBluetoothOff));
            }
        }
    }

    public bool IsBluetoothOff => !IsBluetoothEnabled;
    #endregion

    #region Live Transfer Metrics (Same as Phone)
    private string _currentFileName = string.Empty;
    public string CurrentFileName
    {
        get => _currentFileName;
        set => SetField(ref _currentFileName, value);
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

    private double _progressPercentage;
    public double ProgressPercentage
    {
        get => _progressPercentage;
        set => SetField(ref _progressPercentage, value);
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
    #endregion

    #region Commands
    public ICommand SelectDeviceCommand { get; }
    public ICommand DisconnectCommand { get; }
    public ICommand SelectFilesCommand { get; }
    public ICommand SelectFolderCommand { get; }
    public ICommand CancelTransferCommand { get; }
    public ICommand BackToScanningCommand { get; }
    public ICommand TurnOnBluetoothCommand { get; }
    #endregion

    public MainViewModel()
    {
        SelectDeviceCommand = new RelayCommand(param => ConnectToDevice(param as ShareDashPeerItem));
        DisconnectCommand = new RelayCommand(Disconnect);
        SelectFilesCommand = new RelayCommand(PickFilesAndTransfer);
        SelectFolderCommand = new RelayCommand(PickFolderAndTransfer);
        CancelTransferCommand = new RelayCommand(CancelTransfer);
        BackToScanningCommand = new RelayCommand(BackToScanning);
        TurnOnBluetoothCommand = new RelayCommand(async () =>
        {
            _log.Info("Turning on Bluetooth...");
            await BluetoothRadioService.TurnOnBluetoothAsync();
            await CheckBluetoothStateAsync();
        });

        // Multipath progress wire
        _sender.OnProgress += (pct, speedMbS, speedGbps, elapsed, eta, channels) =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                ProgressPercentage = Math.Round(pct * 100, 1);
                CurrentSpeedMbS = Math.Round(speedMbS, 1);
                CurrentSpeedGbps = Math.Round(speedGbps, 2);
                EtaTimeFormatted = eta > 0 ? $"{TimeSpan.FromSeconds(eta):mm\\:ss}s" : "--:--";

                var usb = channels.FirstOrDefault(c => c.Name.Contains("USB", StringComparison.OrdinalIgnoreCase));
                var wifi = channels.FirstOrDefault(c => !c.Name.Contains("USB", StringComparison.OrdinalIgnoreCase));

                UsbSpeedMbS = Math.Round(usb?.CurrentSpeedMbS ?? 0, 1);
                WifiSpeedMbS = Math.Round(wifi?.CurrentSpeedMbS ?? 0, 1);
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
        _telemetryWs.OnTelemetryReceived += telemetry =>
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                if (telemetry.Status == "ACTIVE" || telemetry.Status == "VERIFYING")
                {
                    if (CurrentState != AppScreenState.Transferring)
                    {
                        CurrentFileName = telemetry.Title;
                        CurrentState = AppScreenState.Transferring;
                    }
                    CurrentSpeedMbS = telemetry.Aggregate.AggregateMbps;
                    CurrentSpeedGbps = (telemetry.Aggregate.AggregateMbps * 8.0) / 1000.0;
                    ProgressPercentage = telemetry.Aggregate.ProgressPct;
                    FormattedTransferred = TransferItem.FormatBytes((long)telemetry.Aggregate.TotalBytesTransferred);
                    FormattedTotal = TransferItem.FormatBytes((long)telemetry.Aggregate.TotalBytesExpected);
                    EtaTimeFormatted = $"{TimeSpan.FromSeconds(telemetry.Aggregate.EtaSeconds):mm\\:ss}s";
                }
                else if (telemetry.Status == "COMPLETED")
                {
                    if (CurrentState == AppScreenState.Transferring)
                    {
                        LatestResult = new TransferResult
                        {
                            FileName = telemetry.Title,
                            SizeMb = (double)telemetry.Aggregate.TotalBytesTransferred / (1024.0 * 1024.0),
                            TimeSecs = telemetry.Aggregate.ElapsedSeconds,
                            AvgSpeedMbS = telemetry.Aggregate.AggregateMbps,
                            IntegrityOk = true
                        };
                        CurrentState = AppScreenState.Completed;
                    }
                }
            });
        };

        // Periodic poller (every 1.5s) for BLE devices, USB status, and peers
        _pollTimer.Interval = TimeSpan.FromSeconds(1.5);
        _pollTimer.Tick += async (_, _) => await PollDevicesAsync();
        _pollTimer.Start();

        // Auto-initialize: Start engine, ensure Bluetooth/Wi-Fi active, and start scanning
        _ = InitializeAppAsync();
    }

    public async Task CheckBluetoothStateAsync()
    {
        bool on = await BluetoothRadioService.IsBluetoothOnAsync();
        IsBluetoothEnabled = on;
        if (!on)
        {
            StatusSubtext = "Bluetooth is turned off • Tap alert above to turn on";
        }
        else if (StatusSubtext.Contains("turned off"))
        {
            StatusSubtext = "Discoverable as ShareDash Desktop";
        }
    }

    private async Task InitializeAppAsync()
    {
        _log.Info("Opening ShareDash in Minimal Mode...");

        await CheckBluetoothStateAsync();
        await _engineSupervisor.EnsureEngineStartedAsync(54321);
        _telemetryWs.Start();

        await _api.TriggerBleScanAsync();
        if (IsBluetoothEnabled)
        {
            StatusSubtext = "Discoverable as ShareDash Desktop";
        }
    }

    private async Task PollDevicesAsync()
    {
        // 0. Verify real Windows Bluetooth Radio state
        await CheckBluetoothStateAsync();

        // 1. Check USB Cable connection
        var bridges = await _api.GetBridgesAsync();
        if (bridges != null)
        {
            IsUsbCableConnected = bridges.Usb.Connected;
            UsbModelName = bridges.Usb.DeviceModel ?? "Connected Phone";

            // If a USB device is plugged in, add it as a primary candidate
            if (IsUsbCableConnected)
            {
                AddOrUpdatePeer(new ShareDashPeerItem
                {
                    DeviceId = "usb-cable",
                    Name = bridges.Usb.DeviceModel ?? "Android Phone (USB 3.x)",
                    IpAddress = "192.168.42.129",
                    Port = 54321,
                    OsType = "Phone",
                    ConnectionType = "⚡ USB-C 3.x Cable (3.2 Gbps Turbo)",
                    Rssi = -30
                });
            }
        }

        // 2. Poll Bluetooth LE devices
        var bleDevices = await _api.GetBleDevicesAsync();
        foreach (var ble in bleDevices)
        {
            // Only show ShareDash devices or nearby phones
            string os = ble.Name.Contains("PC", StringComparison.OrdinalIgnoreCase) || ble.Name.Contains("Desktop", StringComparison.OrdinalIgnoreCase) ? "PC" : "Phone";
            AddOrUpdatePeer(new ShareDashPeerItem
            {
                DeviceId = ble.PeripheralId,
                Name = ble.Name,
                IpAddress = ble.Address,
                Port = 54321,
                OsType = os,
                ConnectionType = "📶 Bluetooth LE • Wi-Fi Direct",
                Rssi = ble.Rssi
            });
        }

        // 3. Poll UDP/LAN discovered peers
        var peers = await _api.GetDiscoveredPeersAsync();
        foreach (var p in peers)
        {
            string os = p.FriendlyName.Contains("PC", StringComparison.OrdinalIgnoreCase) ? "PC" : "Phone";
            AddOrUpdatePeer(new ShareDashPeerItem
            {
                DeviceId = p.DeviceId,
                Name = p.FriendlyName,
                IpAddress = p.IpAddress,
                Port = p.Port,
                OsType = os,
                ConnectionType = string.Join(" • ", p.SupportedTransports),
                Rssi = -50
            });
        }
    }

    private void AddOrUpdatePeer(ShareDashPeerItem item)
    {
        var existing = DiscoveredDevices.FirstOrDefault(d => d.DeviceId == item.DeviceId || (d.Name == item.Name && !string.IsNullOrWhiteSpace(item.Name)));
        if (existing == null)
        {
            DiscoveredDevices.Add(item);
        }
        else
        {
            existing.Rssi = item.Rssi;
            existing.ConnectionType = item.ConnectionType;
            if (!string.IsNullOrWhiteSpace(item.IpAddress) && !item.IpAddress.Contains(":"))
            {
                existing.IpAddress = item.IpAddress;
            }
        }
    }

    public void ConnectToDevice(ShareDashPeerItem? peer)
    {
        if (peer == null) return;
        ActiveDevice = peer;
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
                _log.Success($"Connected to {peer.Name}! Ready to drag and drop files.");
            });
        });
    }

    public void Disconnect()
    {
        ActiveDevice = null;
        CurrentState = AppScreenState.Scanning;
        StatusSubtext = "Bluetooth & Wi-Fi discoverable as ShareDash Desktop";
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
        if (IsUsbCableConnected || ActiveDevice.DeviceId.Contains("usb"))
        {
            targets.Add(new TargetEndpoint
            {
                Name = "⚡ USB 3.x Cable Fast-Path",
                Ip = "192.168.42.129",
                Port = ActiveDevice.Port
            });
        }

        // Add Wi-Fi endpoint
        if (!string.IsNullOrWhiteSpace(ActiveDevice.IpAddress) && ActiveDevice.IpAddress.Contains('.'))
        {
            targets.Add(new TargetEndpoint
            {
                Name = "📶 Wi-Fi 5GHz/6GHz",
                Ip = ActiveDevice.IpAddress,
                Port = ActiveDevice.Port
            });
        }
        else
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
