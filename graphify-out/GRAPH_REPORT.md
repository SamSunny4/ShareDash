# Graph Report - ShareDash  (2026-10-19)

## Corpus Check
- 118 files · ~103,715 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 8, (none) 3, .properties 2)

## Summary
- 2077 nodes · 4409 edges · 116 communities (68 shown, 48 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 89 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `3c3999ed`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- api.rs
- FrameType
- HomeScreen.kt
- system_windows
- bluetooth.rs
- message.rs
- ConnectedScreen.kt
- benchmark_suite.py
- TransferForegroundService
- AndroidHttpServer
- manifest_db.rs
- cli_widgets.rs
- ChunkVisualState
- MainActivity.kt
- TerminalCli
- TransferScreen.kt
- RelayCommand
- dynamic_scheduler.rs
- hotspot.rs
- frame.rs
- Window
- FrameType
- MockSimTransport
- ChunkPool
- failover_test.rs
- PairingRequest
- .SendSingleFileAsync
- chunker.rs
- handshake.rs
- mdns.rs
- AppScreenState
- run_transport_chunk_worker
- LanTransport
- QuicInetTransport
- UsbTransport
- Models.kt
- .run_interactive_loop
- UdpDiscoveryManager
- BleDevice
- TransferHistoryManager
- main.rs
- MainViewModel
- app.js
- Theme.kt
- HotspotManager
- AsyncTransport
- TransferHistoryScreen.kt
- ⚡ ShareDash
- MainActivity
- AndroidSparseFileWriter
- SettingsManager
- MultipathSpeedometer
- ⚡ ShareDash: Technical Architecture & Operational Guide
- ScaleTransform
- BridgeBadge
- cli.rs
- .send_file_multipath_loop
- PulseRing1
- ChunkGridControl
- CenterGlow
- UsbFirstScreen.kt
- ConnectionBridgesResponse
- TransferUiStage
- MultipathSender.cs
- 🤝 Contributing to ShareDash
- Task
- .connectToWifiNetwork
- pairing.rs
- sharedash
- SettingsScreen.kt
- TransferStatus
- 🗺️ ShareDash Product & Engineering Roadmap
- ChannelMetrics
- Contributor Covenant Code of Conduct
- README.md
- LogService
- PULL_REQUEST_TEMPLATE.md
- 🚀 Running Benchmarks
- wifi_direct.rs
- TransferEvent
- ⚡ High-Throughput Pipelined Multipath Streaming Engine
- TransferResult
- Color.kt
- usb.rs
- TelemetryData
- MainViewModel.cs
- deserialize
- MainWindow
- AdapterInfo
- DiscoveredPeer
- ResourceDictionary
- FrameCodec
- ShareDash.Windows.Models
- ChunkState
- TransferItem
- ShareDashPeerItem
- UsbBridgeStatus
- TransportMetric
- ShareDash.Windows.csproj
- Colors.xaml
- PostResult
- Frame
- AndroidPairingCoordinator

## God Nodes (most connected - your core abstractions)
1. `MainViewModel` - 76 edges
2. `Window` - 64 edges
3. `MainActivity` - 42 edges
4. `AppState` - 36 edges
5. `NeoCard()` - 35 edges
6. `TerminalCli` - 33 edges
7. `MockSimTransport` - 31 edges
8. `AndroidHttpServer` - 30 edges
9. `FrameType` - 30 edges
10. `NeoInset()` - 29 edges

## Surprising Connections (you probably didn't know these)
- `test_select_optimal_pc_to_pc_host_and_band()` --calls--> `select_optimal_pc_to_pc_host()`  [INFERRED]
  tests/test_pc_to_pc.rs → src/hotspot.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/corruption_test.rs → src/transport/mock_sim.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/failover_test.rs → src/transport/mock_sim.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/multipath_benchmark.rs → src/transport/mock_sim.rs
- `MainViewModel` --calls--> `RelayCommand`  [INFERRED]
  windows/ViewModels/MainViewModel.cs → windows/ViewModels/RelayCommand.cs

## Import Cycles
- None detected.

## Communities (116 total, 48 thin omitted)

### Community 0 - "api.rs"
Cohesion: 0.07
Nodes (53): ACTIVE_CHUNK_RECEIVERS, ActiveIncomingChunkSession, ADB_CACHE, ADB_CACHE_TTL_SECS, AdbCacheEntry, AppState, cancel_all_active_transfers(), cancel_transfer() (+45 more)

### Community 1 - "FrameType"
Cohesion: 0.08
Nodes (25): FrameType, BenchmarkProbe, BenchmarkResp, Capabilities, ChunkAck, ChunkData, ChunkReject, ChunkReq (+17 more)

### Community 3 - "system_windows"
Cohesion: 0.15
Nodes (4): ShareDash.Windows.Controls, ShareDash.Windows, Application, App

### Community 4 - "bluetooth.rs"
Cohesion: 0.07
Nodes (14): COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID, BleAdvertiser, BleDiscovery, COMMAND_CHAR_UUID, DiscoveryMethod (+6 more)

### Community 5 - "message.rs"
Cohesion: 0.07
Nodes (24): BenchmarkProbeMessage, BenchmarkRespMessage, ChunkAckMessage, ChunkRejectMessage, ChunkReqMessage, ControlMessageWrapper, HelloMessage, HelloRespMessage (+16 more)

### Community 7 - "benchmark_suite.py"
Cohesion: 0.10
Nodes (15): check_adb_state(), install_and_start_app(), perform_stream_transfer(), probe_phone(), run_cmd(), run_suite(), setup_adb(), get_file_sha256() (+7 more)

### Community 14 - "cli_widgets.rs"
Cohesion: 0.09
Nodes (23): animated_scan_progress(), BLUE, BOLD, BOLD_CYAN, BOLD_GREEN, BOLD_RED, BOLD_WHITE, BOLD_YELLOW (+15 more)

### Community 15 - "ChunkVisualState"
Cohesion: 0.25
Nodes (8): ChunkVisualItem, ChunkId, State, ChunkVisualState, CompletedUsb, CompletedWifi, InFlight, Pending

### Community 16 - "MainActivity.kt"
Cohesion: 0.06
Nodes (7): DeviceIdentity, BottomPillBar(), NavTab, HISTORY, HOME, SETTINGS, TRANSFER

### Community 17 - "TerminalCli"
Cohesion: 0.14
Nodes (9): TerminalCli, draw_spinner_frame(), print_ok(), print_step_result(), print_tree_item(), print_warn(), with_spinner(), WifiCapsInfo (+1 more)

### Community 18 - "TransferScreen.kt"
Cohesion: 0.06
Nodes (16): ConnectingDialog(), LegendChip(), PieceVisualizerGrid(), UsbPromptDialog(), WifiDisconnectPromptDialog(), WirelessWarningDialog(), DiscoveryScreen(), HomeScreen() (+8 more)

### Community 21 - "hotspot.rs"
Cohesion: 0.07
Nodes (26): apply_and_connect_profile(), check_pc_wifi_adapter_enabled(), connect_to_phone_hotspot(), create_hotspot(), create_hotspot_with_band(), detect_hotspot_gateway(), detect_usb_tethering_peer(), detect_usb_tethering_peer_detailed() (+18 more)

### Community 22 - "frame.rs"
Cohesion: 0.15
Nodes (9): FLAG_BENCHMARK, FLAG_COMPRESSED, FLAG_ENCRYPTED, FLAG_NONE, FLAG_PRIORITY, FRAME_HEADER_LEN, MAGIC_BYTES, MAX_PAYLOAD_SIZE (+1 more)

### Community 23 - "Window"
Cohesion: 0.04
Nodes (50): ActiveDevice.HasUsb, ActiveDevice.HasWifi, ActiveDevice.Icon, ActiveDevice.Name, BackToScanningCommand, CancelTransferCommand, CheckUsbAndConnectCommand, CompletedChunksCount (+42 more)

### Community 24 - "FrameType"
Cohesion: 0.09
Nodes (18): Frame, FrameHeader, FrameType, BENCHMARK_PROBE, BENCHMARK_RESP, CAPABILITIES, CHUNK_ACK, CHUNK_DATA (+10 more)

### Community 26 - "ChunkPool"
Cohesion: 0.12
Nodes (8): ChunkPool, ChunkPoolInner, ChunkStatus, Completed, Corrupted, InFlight, Unassigned, InFlightInfo

### Community 27 - "failover_test.rs"
Cohesion: 0.18
Nodes (6): mock_responder(), test_cryptographic_corruption_rejection_and_recovery(), mock_responder(), test_work_stealing_and_cable_disconnect_failover(), mock_responder(), test_multipath_concurrent_transports()

### Community 28 - "PairingRequest"
Cohesion: 0.22
Nodes (9): PairingRequest, AppVersion, InitiatorDeviceId, InitiatorIp, InitiatorName, InitiatorPort, PinCode, Status (+1 more)

### Community 29 - ".SendSingleFileAsync"
Cohesion: 0.17
Nodes (4): MultipathSender, IsTransferActive, TelemetryWebSocketClient, IsConnected

### Community 30 - "chunker.rs"
Cohesion: 0.05
Nodes (15): SessionCrypto, test_encryption_roundtrip(), test_hashers(), FileMetadata, AdaptiveChunker, ChunkInfo, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE (+7 more)

### Community 31 - "handshake.rs"
Cohesion: 0.16
Nodes (10): build_local_capabilities(), HandshakeState, Established, Failed, Idle, SynReceived, SynSent, SessionInfo (+2 more)

### Community 32 - "mdns.rs"
Cohesion: 0.12
Nodes (7): CURRENT_APP_VERSION, DiscoveredPeer, DISCOVERY_BROADCAST_PORT, is_version_compatible(), MIN_SUPPORTED_APP_VERSION, PeerBeacon, PeerDiscovery

### Community 33 - "AppScreenState"
Cohesion: 0.33
Nodes (6): AppScreenState, Completed, Connected, Connecting, Scanning, Transferring

### Community 36 - "run_transport_chunk_worker"
Cohesion: 0.17
Nodes (7): DynamicWorkDispatcher, run_transport_chunk_worker(), stream_upload_channel(), TransferChunkInfo, ChannelProgress, draw_transfer_progress(), init_transfer_progress()

### Community 40 - "Models.kt"
Cohesion: 0.16
Nodes (16): ChunkInfo, ChunkState, COMPLETED, CORRUPTED, IN_FLIGHT, PENDING, ChunkVisualItem, FileMeta (+8 more)

### Community 44 - "BleDevice"
Cohesion: 0.25
Nodes (8): BleDevice, Address, Name, PeripheralId, Rssi, SignalColor, SignalPercent, SignalQuality

### Community 45 - "TransferHistoryManager"
Cohesion: 0.12
Nodes (10): TransferDirection, RECEIVED, SENT, TransferRecord, TransferStatus, CANCELLED, COMPLETED, FAILED (+2 more)

### Community 47 - "MainViewModel"
Cohesion: 0.04
Nodes (45): MainViewModel, ActiveDevice, BackToScanningCommand, CancelTransferCommand, CheckUsbAndConnectCommand, CompletedChunksCount, ConnectionMethodText, ContinueWithWifiCommand (+37 more)

### Community 48 - "app.js"
Cohesion: 0.16
Nodes (6): initMultipathCanvas(), drawDeviceNode(), render(), initSpeedCalculator(), formatTime(), updateCalculations()

### Community 50 - "HotspotManager"
Cohesion: 0.07
Nodes (10): Active, Error, HotspotManager, ActionListener, ActionListener, ActionListener, ActionListener, HotspotState (+2 more)

### Community 51 - "AsyncTransport"
Cohesion: 0.10
Nodes (10): AsyncTransport, TransportMetrics, TransportState, Active, Benchmarking, Closed, Connected, Connecting (+2 more)

### Community 52 - "TransferHistoryScreen.kt"
Cohesion: 0.10
Nodes (5): CompactTransferRecordRow(), formatFileSize(), getFileIcon(), openTransferredFile(), TransferHistoryScreen()

### Community 53 - "⚡ ShareDash"
Cohesion: 0.10
Nodes (20): 🧪 Automated Test Suite, Build from Source via Gradle:, 🤝 Contributing, Direct Installation via ADB:, 🚀 Key Highlights, 📜 License, 📊 Live Benchmark Performance, Option A: Launch Interactive Auto-Connect Wizard (+12 more)

### Community 54 - "MainActivity"
Cohesion: 0.13
Nodes (3): ChunkTask, MainActivity, TransportEndpoint

### Community 58 - "⚡ ShareDash: Technical Architecture & Operational Guide"
Cohesion: 0.11
Nodes (19): 1. Performance Summary Matrix, 1. Wifite-Style Terminal Interface (Default), 2. Fluent Mica Web Dashboard, 2. Speedup & Efficiency Analysis, 📱 Android Companion App Architecture, 🖥️ CLI & Web UI User Interfaces, 🔒 Cryptographic Security & Verification, 🌟 Executive Summary & Offline Philosophy (+11 more)

### Community 59 - "ScaleTransform"
Cohesion: 0.50
Nodes (4): CenterGlowScale, PulseRing1Scale, PulseRing2Scale, ScaleTransform

### Community 60 - "BridgeBadge"
Cohesion: 0.43
Nodes (4): BridgeBadge(), RecommendationRow(), SmartBridgeStrip(), TransportRecommendationCard()

### Community 62 - ".send_file_multipath_loop"
Cohesion: 0.32
Nodes (6): get_dir_size(), package_directory_to_zip(), print_channel_summary(), print_fail(), prompt(), prompt_choice()

### Community 63 - "PulseRing1"
Cohesion: 0.67
Nodes (3): PulseRing1, PulseRing2, Border

### Community 64 - "ChunkGridControl"
Cohesion: 0.06
Nodes (18): ShareDash.Windows.Converters, ChunkGridControl, ChunkStates, InFlightBrush, PendingBrush, UsbBrush, WifiBrush, DualColorProgressRing (+10 more)

### Community 67 - "ConnectionBridgesResponse"
Cohesion: 0.13
Nodes (15): ConnectionBridgesResponse, Lan, RecommendedAction, Usb, WifiDirect, LanBridgeStatus, Connected, LocalIp (+7 more)

### Community 68 - "TransferUiStage"
Cohesion: 0.40
Nodes (5): TransferUiStage, COMPLETED, FAILED, TRANSFERRING, VERIFYING

### Community 69 - "MultipathSender.cs"
Cohesion: 0.13
Nodes (5): ShareDash.Windows.Services, ChunkWork, ChunkId, Length, Offset

### Community 70 - "🤝 Contributing to ShareDash"
Cohesion: 0.15
Nodes (13): 1. Rust Core Engine (Windows / Linux), 2. Android Companion App, Android / Kotlin, 📜 Code of Conduct, 📐 Coding Guidelines & Standards, 💬 Commit Message Conventions, 🤝 Contributing to ShareDash, 🛠️ Development Setup (+5 more)

### Community 71 - "Task"
Cohesion: 0.12
Nodes (9): DeviceInfo, AppVersion, DeviceId, DeviceName, LocalIps, OsName, ServerPort, ApiClient (+1 more)

### Community 81 - "TransferStatus"
Cohesion: 0.20
Nodes (8): TransferRecord, TransferStatus, Active, Cancelled, Completed, Failed, Paused, Pending

### Community 82 - "🗺️ ShareDash Product & Engineering Roadmap"
Cohesion: 0.20
Nodes (9): 📱 Android Enhancements, 🎯 Current Status (v0.1.0), 🔮 Future Vision (v1.0.0+), 💡 Have an idea?, 🖥️ Native Cross-Platform PC Clients, 🚀 Near-Term Milestones (v0.2.0 - v0.3.0), ⚡ Protocol & Transfer Optimizations, 🗺️ ShareDash Product & Engineering Roadmap (+1 more)

### Community 83 - "ChannelMetrics"
Cohesion: 0.40
Nodes (5): ChannelMetrics, BytesTransferred, ChunksCompleted, CurrentSpeedMbS, Name

### Community 84 - "Contributor Covenant Code of Conduct"
Cohesion: 0.22
Nodes (7): Attribution, Contributor Covenant Code of Conduct, Enforcement, Enforcement Responsibilities, Our Pledge, Our Standards, Scope

### Community 85 - "README.md"
Cohesion: 0.25
Nodes (5): 🚨 Reporting a Vulnerability, 🔒 Security & Cryptographic Architecture, 🛡️ Security Policy, Supported Versions, What to Include:

### Community 86 - "LogService"
Cohesion: 0.16
Nodes (6): EngineSupervisor, IsActive, IsManagedProcess, LogService, Instance, Messages

### Community 87 - "PULL_REQUEST_TEMPLATE.md"
Cohesion: 0.29
Nodes (6): ✅ Contributor Checklist, 📋 Description, 🔗 Related Issue(s), 📸 Screenshots / Logs (if applicable), 🧪 Testing & Verification, 🛠️ Type of Change

### Community 88 - "🚀 Running Benchmarks"
Cohesion: 0.29
Nodes (6): 1. Generate Test File, 2. Run Analytical Transfer Optimization, 3. Run Live ADB Phone Benchmark, 🚀 Running Benchmarks, 📁 Script Overview, 🛠️ ShareDash Diagnostic & Benchmarking Scripts

### Community 89 - "wifi_direct.rs"
Cohesion: 0.13
Nodes (4): BandwidthTracker, detect_wifi_capabilities(), WifiCapabilities, WifiDirectTransport

### Community 91 - "TransferEvent"
Cohesion: 0.18
Nodes (10): TransferEvent, Cancelled, ChunkCompleted, ChunkCorrupt, Completed, Failed, Progress, Started (+2 more)

### Community 92 - "⚡ High-Throughput Pipelined Multipath Streaming Engine"
Cohesion: 0.40
Nodes (5): 1. 📦 Adaptive Chunk Sizing, 2. 🌊 3 Pipelined In-Flight Workers Per Transport, 3. ⚡ Android Concurrent `FileChannel` Disk Writes, 4. 📈 Exact Per-Channel Bandwidth Accounting, ⚡ High-Throughput Pipelined Multipath Streaming Engine

### Community 93 - "TransferResult"
Cohesion: 0.09
Nodes (20): TransferResult, AvgSpeedGbps, AvgSpeedMbS, CompletedAt, FileName, IntegrityOk, SizeMb, TimeSecs (+12 more)

### Community 96 - "TelemetryData"
Cohesion: 0.11
Nodes (17): AggregateMetrics, AggregateMbps, ElapsedSeconds, EtaSeconds, ProgressPct, TotalBytesExpected, TotalBytesTransferred, ChunkStateInfo (+9 more)

### Community 98 - "deserialize"
Cohesion: 0.43
Nodes (3): AggregateMetrics, ChunkVisualState, SchedulerTelemetry

### Community 100 - "AdapterInfo"
Cohesion: 0.25
Nodes (8): AdapterInfo, Description, InterfaceType, IpAddress, IsFastPathCandidate, Name, Speed, Status

### Community 101 - "DiscoveredPeer"
Cohesion: 0.25
Nodes (8): DiscoveredPeer, DeviceId, DisplaySubtitle, FriendlyName, IpAddress, IsPaired, Port, SupportedTransports

### Community 103 - "ResourceDictionary"
Cohesion: 0.31
Nodes (8): bd, PART_Indicator, PART_Track, ResourceDictionary, RootGrid, TemplateRoot, Border, Grid

### Community 107 - "ChunkState"
Cohesion: 0.33
Nodes (5): ChunkState, Completed, Corrupt, InFlight, Pending

### Community 108 - "TransferItem"
Cohesion: 0.13
Nodes (12): TransferItem, FileName, FilePath, FileSizeBytes, FormattedSize, Icon, IsDirectory, TargetEndpoint (+4 more)

### Community 109 - "ShareDashPeerItem"
Cohesion: 0.12
Nodes (14): ShareDashPeerItem, ConnectionType, DeviceId, HasUsb, HasWifi, Icon, IpAddress, Name (+6 more)

### Community 110 - "UsbBridgeStatus"
Cohesion: 0.33
Nodes (6): UsbBridgeStatus, Connected, DeviceModel, IsAdbReady, Message, SpeedGbps

### Community 111 - "TransportMetric"
Cohesion: 0.33
Nodes (6): TransportMetric, CurrentGbps, CurrentMbps, TotalBytes, TransportId, TransportKind

### Community 116 - "PostResult"
Cohesion: 0.50
Nodes (4): PostResult, NOT_FOUND, RETRY, SUCCESS

## Knowledge Gaps
- **355 isolated node(s):** `sharedash`, `SUCCESS`, `NOT_FOUND`, `RETRY`, `Idle` (+350 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 880 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **48 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ChunkGridControl` connect `ChunkGridControl` to `HomeScreen.kt`, `system_windows`?**
  _High betweenness centrality (0.238) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `MainViewModel` (e.g. with `.Error()` and `RelayCommand`) actually correct?**
  _`MainViewModel` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `sharedash`, `SUCCESS`, `NOT_FOUND` to the rest of the system?**
  _355 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `api.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.07496194824961948 - nodes in this community are weakly interconnected._
- **Why does `ShareDash.Windows.Models` connect `ShareDash.Windows.Models` to `MainViewModel.cs`, `system_windows`, `MultipathSender.cs`, `ChunkVisualState`?**
  _High betweenness centrality (0.143) - this node is a cross-community bridge._
- **Should `FrameType` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Why does `MainViewModel` connect `MainViewModel` to `MainViewModel.cs`, `AppScreenState`, `MainWindow`, `Task`, `TransferItem`, `ShareDashPeerItem`, `ChunkVisualState`, `.SendSingleFileAsync`, `RelayCommand`, `LogService`, `Window`, `TransferResult`?**
  _High betweenness centrality (0.112) - this node is a cross-community bridge._