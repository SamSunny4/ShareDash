# Graph Report - ShareDash  (2026-10-19)

## Corpus Check
- 119 files · ~104,665 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 8, (none) 3, .properties 2)

## Summary
- 2082 nodes · 4483 edges · 105 communities (61 shown, 44 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 91 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `7db213f3`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- api.rs
- ConnectedScreen.kt
- HomeScreen.kt
- BleDiscoveryManager.kt
- BleDiscovery
- message.rs
- HotspotManager
- benchmark_suite.py
- PieceGrid.kt
- MainActivity
- TransferForegroundService
- http.rs
- AndroidHttpServer
- manifest_db.rs
- cli_widgets.rs
- BluetoothRfcommServer
- MainActivity.kt
- BleCommandServer
- NeoCard
- TransferScreen.kt
- dynamic_scheduler.rs
- hotspot.rs
- frame.rs
- Window
- FrameType
- MockSimTransport
- ChunkPool
- failover_test.rs
- PairingRequest
- TelemetryWebSocketClient
- result
- handshake.rs
- mdns.rs
- chunker.rs
- UdpDiscoveryManager.kt
- .run_wireless_direct_wizard
- run_transport_chunk_worker
- LanTransport
- QuicInetTransport
- UsbTransport
- Models.kt
- TerminalCli
- UdpDiscoveryManager
- BleDevice
- TransferHistoryManager
- main.rs
- MainViewModel
- app.js
- Theme.kt
- bluetooth.rs
- TransportMetrics
- TransferHistoryScreen.kt
- ⚡ ShareDash
- AndroidSparseFileWriter
- build
- MultipathSpeedometer
- ⚡ ShareDash: Technical Architecture & Operational Guide
- ScaleTransform
- BridgeBadge
- cli.rs
- .send_file_multipath_loop
- PulseRing1
- UiConverters.cs
- CenterGlow
- ConnectionBridgesResponse
- TransferUiStage
- 🤝 Contributing to ShareDash
- Task
- NetworkCallback
- sharedash
- SettingsScreen.kt
- 🗺️ ShareDash Product & Engineering Roadmap
- .SendSingleFileAsync
- Contributor Covenant Code of Conduct
- README.md
- LogService
- PULL_REQUEST_TEMPLATE.md
- 🚀 Running Benchmarks
- wifi_direct.rs
- sparse_writer.rs
- BleAdvertiser
- ⚡ High-Throughput Pipelined Multipath Streaming Engine
- TransferResult
- PairingScreen.kt
- usb.rs
- TelemetryData
- MainViewModel.cs
- MainWindow
- ResourceDictionary
- AsyncTransport
- TransferItem
- ShareDashPeerItem
- DiscoveryMethod
- ShareDash.Windows.csproj
- Colors.xaml

## God Nodes (most connected - your core abstractions)
1. `MainViewModel` - 62 edges
2. `Window` - 49 edges
3. `MainActivity` - 47 edges
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
- `MainActivity` --references--> `BleCommandServer`  [EXTRACTED]
  android/app/src/main/java/com/sharedash/app/MainActivity.kt → android/app/src/main/java/com/sharedash/app/discovery/BleCommandServer.kt

## Import Cycles
- None detected.

## Communities (105 total, 44 thin omitted)

### Community 0 - "api.rs"
Cohesion: 0.05
Nodes (55): PairingManager, PairingSession, ACTIVE_CHUNK_RECEIVERS, ActiveIncomingChunkSession, ADB_CACHE, ADB_CACHE_TTL_SECS, AdbCacheEntry, AppState (+47 more)

### Community 5 - "message.rs"
Cohesion: 0.09
Nodes (21): BenchmarkProbeMessage, BenchmarkRespMessage, ChunkAckMessage, ChunkRejectMessage, ChunkReqMessage, ControlMessageWrapper, HelloMessage, HelloRespMessage (+13 more)

### Community 6 - "HotspotManager"
Cohesion: 0.08
Nodes (10): Active, Error, HotspotManager, ActionListener, ActionListener, ActionListener, ActionListener, HotspotState (+2 more)

### Community 7 - "benchmark_suite.py"
Cohesion: 0.10
Nodes (15): check_adb_state(), install_and_start_app(), perform_stream_transfer(), probe_phone(), run_cmd(), run_suite(), setup_adb(), get_file_sha256() (+7 more)

### Community 9 - "MainActivity"
Cohesion: 0.08
Nodes (6): ChunkTask, MainActivity, TransportEndpoint, ChunkVisualItem, SchedulerTelemetry, TransportStats

### Community 11 - "http.rs"
Cohesion: 0.05
Nodes (3): Server, handle_socket(), ws_telemetry_handler()

### Community 13 - "manifest_db.rs"
Cohesion: 0.08
Nodes (15): ChunkState, Completed, Corrupt, InFlight, Pending, ManifestDb, test_manifest_db_lifecycle(), TransferRecord (+7 more)

### Community 14 - "cli_widgets.rs"
Cohesion: 0.09
Nodes (21): animated_scan_progress(), BLUE, BOLD, BOLD_CYAN, BOLD_GREEN, BOLD_RED, BOLD_WHITE, BOLD_YELLOW (+13 more)

### Community 16 - "MainActivity.kt"
Cohesion: 0.06
Nodes (11): DeviceIdentity, PostResult, NOT_FOUND, RETRY, SUCCESS, BottomPillBar(), NavTab, HISTORY (+3 more)

### Community 18 - "NeoCard"
Cohesion: 0.17
Nodes (9): ConnectingDialog(), UsbPromptDialog(), WifiDisconnectPromptDialog(), WirelessWarningDialog(), HomeScreen(), UsbFirstScreen(), NeoButton(), NeoCard() (+1 more)

### Community 19 - "TransferScreen.kt"
Cohesion: 0.16
Nodes (4): ActiveTransferScreen(), TransferCategoryItem(), TransferIdleScreen(), TransferScreen()

### Community 20 - "dynamic_scheduler.rs"
Cohesion: 0.13
Nodes (12): MultipathScheduler, TransferEvent, Cancelled, ChunkCompleted, ChunkCorrupt, Completed, Failed, Progress (+4 more)

### Community 21 - "hotspot.rs"
Cohesion: 0.07
Nodes (26): apply_and_connect_profile(), check_pc_wifi_adapter_enabled(), connect_to_phone_hotspot(), create_hotspot(), create_hotspot_with_band(), detect_hotspot_gateway(), detect_usb_tethering_peer(), detect_usb_tethering_peer_detailed() (+18 more)

### Community 22 - "frame.rs"
Cohesion: 0.05
Nodes (38): FLAG_BENCHMARK, FLAG_COMPRESSED, FLAG_ENCRYPTED, FLAG_NONE, FLAG_PRIORITY, Frame, FRAME_HEADER_LEN, FrameCodec (+30 more)

### Community 23 - "Window"
Cohesion: 0.06
Nodes (35): ActiveDevice.Icon, ActiveDevice.Name, BackToScanningCommand, CancelTransferCommand, ConnectionType, BoolToVis, CurrentFileName, CurrentSpeedGbps (+27 more)

### Community 24 - "FrameType"
Cohesion: 0.09
Nodes (18): Frame, FrameHeader, FrameType, BENCHMARK_PROBE, BENCHMARK_RESP, CAPABILITIES, CHUNK_ACK, CHUNK_DATA (+10 more)

### Community 26 - "ChunkPool"
Cohesion: 0.09
Nodes (8): ChunkPool, ChunkPoolInner, ChunkStatus, Completed, Corrupted, InFlight, Unassigned, InFlightInfo

### Community 27 - "failover_test.rs"
Cohesion: 0.20
Nodes (6): mock_responder(), test_cryptographic_corruption_rejection_and_recovery(), mock_responder(), test_work_stealing_and_cable_disconnect_failover(), mock_responder(), test_multipath_concurrent_transports()

### Community 28 - "PairingRequest"
Cohesion: 0.20
Nodes (9): PairingRequest, AppVersion, InitiatorDeviceId, InitiatorIp, InitiatorName, InitiatorPort, PinCode, Status (+1 more)

### Community 30 - "result"
Cohesion: 0.08
Nodes (4): SessionCrypto, test_encryption_roundtrip(), test_hashers(), IntegrityVerifier

### Community 31 - "handshake.rs"
Cohesion: 0.15
Nodes (10): build_local_capabilities(), HandshakeState, Established, Failed, Idle, SynReceived, SynSent, SessionInfo (+2 more)

### Community 32 - "mdns.rs"
Cohesion: 0.12
Nodes (7): CURRENT_APP_VERSION, DiscoveredPeer, DISCOVERY_BROADCAST_PORT, is_version_compatible(), MIN_SUPPORTED_APP_VERSION, PeerBeacon, PeerDiscovery

### Community 33 - "chunker.rs"
Cohesion: 0.17
Nodes (7): AdaptiveChunker, ChunkInfo, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE, MIN_CHUNK_SIZE, test_build_manifest_from_temp_file(), TransferManifest

### Community 35 - ".run_wireless_direct_wizard"
Cohesion: 0.18
Nodes (8): draw_spinner_frame(), print_ok(), print_step_result(), print_table(), print_tree_item(), print_warn(), strip_ansi(), with_spinner()

### Community 36 - "run_transport_chunk_worker"
Cohesion: 0.18
Nodes (7): DynamicWorkDispatcher, run_transport_chunk_worker(), stream_upload_channel(), TransferChunkInfo, ChannelProgress, draw_transfer_progress(), init_transfer_progress()

### Community 40 - "Models.kt"
Cohesion: 0.10
Nodes (22): ChunkInfo, ChunkState, COMPLETED, CORRUPTED, IN_FLIGHT, PENDING, FileMeta, TransferDirection (+14 more)

### Community 41 - "TerminalCli"
Cohesion: 0.15
Nodes (5): TerminalCli, print_phase_header(), print_wizard_banner(), WifiCapsInfo, detect_pc_wifi_caps()

### Community 44 - "BleDevice"
Cohesion: 0.25
Nodes (8): BleDevice, Address, Name, PeripheralId, Rssi, SignalColor, SignalPercent, SignalQuality

### Community 47 - "MainViewModel"
Cohesion: 0.05
Nodes (32): MainViewModel, ActiveDevice, BackToScanningCommand, CancelTransferCommand, CurrentFileName, CurrentSpeedGbps, CurrentSpeedMbS, CurrentState (+24 more)

### Community 48 - "app.js"
Cohesion: 0.16
Nodes (6): initMultipathCanvas(), drawDeviceNode(), render(), initSpeedCalculator(), formatTime(), updateCalculations()

### Community 50 - "bluetooth.rs"
Cohesion: 0.13
Nodes (8): COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID, COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID

### Community 51 - "TransportMetrics"
Cohesion: 0.14
Nodes (12): AggregateMetrics, ChunkVisualState, SchedulerTelemetry, TransportMetrics, TransportState, Active, Benchmarking, Closed (+4 more)

### Community 52 - "TransferHistoryScreen.kt"
Cohesion: 0.09
Nodes (5): CompactTransferRecordRow(), formatFileSize(), getFileIcon(), openTransferredFile(), TransferHistoryScreen()

### Community 53 - "⚡ ShareDash"
Cohesion: 0.10
Nodes (20): 🧪 Automated Test Suite, Build from Source via Gradle:, 🤝 Contributing, Direct Installation via ADB:, 🚀 Key Highlights, 📜 License, 📊 Live Benchmark Performance, Option A: Launch Interactive Auto-Connect Wizard (+12 more)

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

### Community 64 - "UiConverters.cs"
Cohesion: 0.21
Nodes (5): ShareDash.Windows.Converters, BoolToVisibilityConverter, Invert, TabIndexToBooleanConverter, TabIndexToVisibilityConverter

### Community 67 - "ConnectionBridgesResponse"
Cohesion: 0.07
Nodes (29): AdapterInfo, Description, InterfaceType, IpAddress, IsFastPathCandidate, Name, Speed, Status (+21 more)

### Community 68 - "TransferUiStage"
Cohesion: 0.40
Nodes (5): TransferUiStage, COMPLETED, FAILED, TRANSFERRING, VERIFYING

### Community 70 - "🤝 Contributing to ShareDash"
Cohesion: 0.15
Nodes (13): 1. Rust Core Engine (Windows / Linux), 2. Android Companion App, Android / Kotlin, 📜 Code of Conduct, 📐 Coding Guidelines & Standards, 💬 Commit Message Conventions, 🤝 Contributing to ShareDash, 🛠️ Development Setup (+5 more)

### Community 71 - "Task"
Cohesion: 0.08
Nodes (18): DeviceInfo, AppVersion, DeviceId, DeviceName, LocalIps, OsName, ServerPort, DiscoveredPeer (+10 more)

### Community 82 - "🗺️ ShareDash Product & Engineering Roadmap"
Cohesion: 0.20
Nodes (9): 📱 Android Enhancements, 🎯 Current Status (v0.1.0), 🔮 Future Vision (v1.0.0+), 💡 Have an idea?, 🖥️ Native Cross-Platform PC Clients, 🚀 Near-Term Milestones (v0.2.0 - v0.3.0), ⚡ Protocol & Transfer Optimizations, 🗺️ ShareDash Product & Engineering Roadmap (+1 more)

### Community 83 - ".SendSingleFileAsync"
Cohesion: 0.08
Nodes (16): ChannelMetrics, BytesTransferred, ChunksCompleted, CurrentSpeedMbS, Name, ChunkWork, ChunkId, Length (+8 more)

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
Cohesion: 0.16
Nodes (3): detect_wifi_capabilities(), WifiCapabilities, WifiDirectTransport

### Community 90 - "sparse_writer.rs"
Cohesion: 0.18
Nodes (5): FileMetadata, FileTime, set_file_mtime(), SparseWriter, test_sparse_writer_write_out_of_order()

### Community 92 - "⚡ High-Throughput Pipelined Multipath Streaming Engine"
Cohesion: 0.40
Nodes (5): 1. 📦 Adaptive Chunk Sizing, 2. 🌊 3 Pipelined In-Flight Workers Per Transport, 3. ⚡ Android Concurrent `FileChannel` Disk Writes, 4. 📈 Exact Per-Channel Bandwidth Accounting, ⚡ High-Throughput Pipelined Multipath Streaming Engine

### Community 93 - "TransferResult"
Cohesion: 0.10
Nodes (20): TransferResult, AvgSpeedGbps, AvgSpeedMbS, CompletedAt, FileName, IntegrityOk, SizeMb, TimeSecs (+12 more)

### Community 96 - "TelemetryData"
Cohesion: 0.08
Nodes (24): ShareDash.Windows.Models, AggregateMetrics, AggregateMbps, ElapsedSeconds, EtaSeconds, ProgressPct, TotalBytesExpected, TotalBytesTransferred (+16 more)

### Community 97 - "MainViewModel.cs"
Cohesion: 0.07
Nodes (11): ShareDash.Windows.Services, ShareDash.Windows, ShareDash.Windows.ViewModels, Application, App, AppScreenState, Completed, Connected (+3 more)

### Community 103 - "ResourceDictionary"
Cohesion: 0.32
Nodes (7): bd, PART_Indicator, PART_Track, ResourceDictionary, TemplateRoot, Border, Grid

### Community 108 - "TransferItem"
Cohesion: 0.22
Nodes (7): TransferItem, FileName, FilePath, FileSizeBytes, FormattedSize, Icon, IsDirectory

### Community 109 - "ShareDashPeerItem"
Cohesion: 0.12
Nodes (11): ShareDashPeerItem, ConnectionType, DeviceId, Icon, IpAddress, Name, OsType, Port (+3 more)

### Community 111 - "DiscoveryMethod"
Cohesion: 0.50
Nodes (4): DiscoveryMethod, Ble, HttpProbe, Udp

## Knowledge Gaps
- **307 isolated node(s):** `sharedash`, `SUCCESS`, `NOT_FOUND`, `RETRY`, `Idle` (+302 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 860 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **44 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MockSimTransport` connect `MockSimTransport` to `api.rs`, `AsyncTransport`, `TransportMetrics`, `failover_test.rs`, `usb.rs`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `MainViewModel` (e.g. with `.Error()` and `.Info()`) actually correct?**
  _`MainViewModel` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `sharedash`, `SUCCESS`, `NOT_FOUND` to the rest of the system?**
  _307 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `api.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.0547008547008547 - nodes in this community are weakly interconnected._
- **Why does `AppState` connect `api.rs` to `mdns.rs`, `BleDiscovery`, `TerminalCli`, `http.rs`, `manifest_db.rs`, `dynamic_scheduler.rs`, `cli.rs`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **Should `HomeScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.0701344243132671 - nodes in this community are weakly interconnected._
- **Why does `BluetoothRfcommServer` connect `BluetoothRfcommServer` to `MainActivity`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._