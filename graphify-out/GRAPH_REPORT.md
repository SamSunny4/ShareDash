# Graph Report - ShareDash  (2026-10-18)

## Corpus Check
- 96 files · ~96,635 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 8, (none) 3, .properties 2)

## Summary
- 1688 nodes · 3895 edges · 97 communities (51 shown, 46 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 45 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `7faf92b1`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- api.rs
- HomeScreen.kt
- BleDiscoveryManager.kt
- BleDiscovery
- message.rs
- HotspotManager
- benchmark_suite.py
- SettingsScreen.kt
- MainActivity
- TransferForegroundService
- AndroidHttpServer
- manifest_db.rs
- cli_widgets.rs
- BluetoothRfcommServer
- MainActivity.kt
- BleCommandServer
- NeoCard
- chunker.rs
- dynamic_scheduler.rs
- hotspot.rs
- frame.rs
- TransferScreen.kt
- FrameType
- MockSimTransport
- ChunkPool
- failover_test.rs
- UdpDiscoveryManager.kt
- result
- SessionCrypto
- handshake.rs
- mdns.rs
- sparse_writer.rs
- .run_wireless_direct_wizard
- run_transport_chunk_worker
- lan.rs
- quic_inet.rs
- usb.rs
- Models.kt
- wifi_direct.rs
- UdpDiscoveryManager
- .executeRealTransfer
- TransferHistoryManager
- uuid
- TerminalCli
- app.js
- Theme.kt
- bluetooth.rs
- TransportMetrics
- TransferHistoryScreen.kt
- ⚡ ShareDash
- AndroidSparseFileWriter
- SettingsManager
- NavTab
- ⚡ ShareDash: Technical Architecture & Operational Guide
- AsyncTransport
- BridgeBadge
- cli.rs
- .send_file_multipath_loop
- TransportState
- AndroidTransportManager
- WifiCapsInfo
- ChunkState
- MultipathSpeedometer
- TransferUiStage
- pairing.rs
- 🤝 Contributing to ShareDash
- TransferEvent
- NetworkCallback
- PostResult
- sharedash
- TransferStatus
- ws_telemetry.rs
- 🗺️ ShareDash Product & Engineering Roadmap
- Contributor Covenant Code of Conduct
- README.md
- TransportKind
- PULL_REQUEST_TEMPLATE.md
- 🚀 Running Benchmarks
- ChunkState
- BleAdvertiser
- ⚡ High-Throughput Pipelined Multipath Streaming Engine
- ControlMessageWrapper
- ChunkStatus
- DiscoveryMethod
- Server

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 46 edges
2. `AppState` - 36 edges
3. `NeoCard()` - 35 edges
4. `TerminalCli` - 33 edges
5. `MockSimTransport` - 31 edges
6. `AndroidHttpServer` - 30 edges
7. `FrameType` - 30 edges
8. `NeoInset()` - 29 edges
9. `NeoButton()` - 28 edges
10. `BleCommandServer` - 26 edges

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

## Communities (97 total, 46 thin omitted)

### Community 0 - "api.rs"
Cohesion: 0.07
Nodes (53): ACTIVE_CHUNK_RECEIVERS, ActiveIncomingChunkSession, ADB_CACHE, ADB_CACHE_TTL_SECS, AdbCacheEntry, AppState, cancel_all_active_transfers(), cancel_transfer() (+45 more)

### Community 1 - "HomeScreen.kt"
Cohesion: 0.09
Nodes (3): DiscoveredPeer, BottomPillBar(), QuickShareRadar()

### Community 5 - "message.rs"
Cohesion: 0.18
Nodes (12): BenchmarkProbeMessage, BenchmarkRespMessage, ChunkAckMessage, ChunkRejectMessage, ChunkReqMessage, HelloMessage, HelloRespMessage, ManifestMessage (+4 more)

### Community 6 - "HotspotManager"
Cohesion: 0.08
Nodes (10): Active, Error, HotspotManager, ActionListener, ActionListener, ActionListener, ActionListener, HotspotState (+2 more)

### Community 7 - "benchmark_suite.py"
Cohesion: 0.10
Nodes (15): check_adb_state(), install_and_start_app(), perform_stream_transfer(), probe_phone(), run_cmd(), run_suite(), setup_adb(), get_file_sha256() (+7 more)

### Community 8 - "SettingsScreen.kt"
Cohesion: 0.09
Nodes (3): DiagnosticsItem(), SettingsScreen(), ShareDashColors

### Community 14 - "cli_widgets.rs"
Cohesion: 0.08
Nodes (25): animated_scan_progress(), BLUE, BOLD, BOLD_CYAN, BOLD_GREEN, BOLD_RED, BOLD_WHITE, BOLD_YELLOW (+17 more)

### Community 18 - "NeoCard"
Cohesion: 0.08
Nodes (12): ConnectingDialog(), UsbPromptDialog(), WifiDisconnectPromptDialog(), WirelessWarningDialog(), ConnectedScreen(), SendCategoryCard(), DiscoveryScreen(), HomeScreen() (+4 more)

### Community 19 - "chunker.rs"
Cohesion: 0.17
Nodes (7): AdaptiveChunker, ChunkInfo, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE, MIN_CHUNK_SIZE, test_build_manifest_from_temp_file(), TransferManifest

### Community 21 - "hotspot.rs"
Cohesion: 0.10
Nodes (19): apply_and_connect_profile(), check_pc_wifi_adapter_enabled(), connect_to_phone_hotspot(), create_hotspot(), create_hotspot_with_band(), detect_hotspot_gateway(), detect_usb_tethering_peer(), detect_usb_tethering_peer_detailed() (+11 more)

### Community 22 - "frame.rs"
Cohesion: 0.05
Nodes (38): FLAG_BENCHMARK, FLAG_COMPRESSED, FLAG_ENCRYPTED, FLAG_NONE, FLAG_PRIORITY, Frame, FRAME_HEADER_LEN, FrameCodec (+30 more)

### Community 23 - "TransferScreen.kt"
Cohesion: 0.12
Nodes (6): LegendChip(), PieceVisualizerGrid(), ActiveTransferScreen(), TransferCategoryItem(), TransferIdleScreen(), TransferScreen()

### Community 24 - "FrameType"
Cohesion: 0.12
Nodes (17): Frame, FrameHeader, FrameType, BENCHMARK_PROBE, BENCHMARK_RESP, CAPABILITIES, CHUNK_ACK, CHUNK_DATA (+9 more)

### Community 26 - "ChunkPool"
Cohesion: 0.15
Nodes (3): ChunkPool, ChunkPoolInner, InFlightInfo

### Community 27 - "failover_test.rs"
Cohesion: 0.18
Nodes (6): mock_responder(), test_cryptographic_corruption_rejection_and_recovery(), mock_responder(), test_work_stealing_and_cable_disconnect_failover(), mock_responder(), test_multipath_concurrent_transports()

### Community 30 - "SessionCrypto"
Cohesion: 0.22
Nodes (3): SessionCrypto, test_encryption_roundtrip(), test_hashers()

### Community 31 - "handshake.rs"
Cohesion: 0.15
Nodes (10): build_local_capabilities(), HandshakeState, Established, Failed, Idle, SynReceived, SynSent, SessionInfo (+2 more)

### Community 32 - "mdns.rs"
Cohesion: 0.13
Nodes (7): CURRENT_APP_VERSION, DiscoveredPeer, DISCOVERY_BROADCAST_PORT, is_version_compatible(), MIN_SUPPORTED_APP_VERSION, PeerBeacon, PeerDiscovery

### Community 33 - "sparse_writer.rs"
Cohesion: 0.16
Nodes (5): FileMetadata, FileTime, set_file_mtime(), SparseWriter, test_sparse_writer_write_out_of_order()

### Community 35 - ".run_wireless_direct_wizard"
Cohesion: 0.19
Nodes (6): draw_spinner_frame(), print_ok(), print_phase_header(), print_step_result(), print_tree_item(), with_spinner()

### Community 36 - "run_transport_chunk_worker"
Cohesion: 0.18
Nodes (7): DynamicWorkDispatcher, run_transport_chunk_worker(), stream_upload_channel(), TransferChunkInfo, ChannelProgress, draw_transfer_progress(), init_transfer_progress()

### Community 40 - "Models.kt"
Cohesion: 0.14
Nodes (17): ChunkInfo, FileMeta, TransferDirection, RECEIVED, SENT, TransferManifest, TransferRecord, TransferStatus (+9 more)

### Community 41 - "wifi_direct.rs"
Cohesion: 0.17
Nodes (3): detect_wifi_capabilities(), WifiCapabilities, WifiDirectTransport

### Community 44 - ".executeRealTransfer"
Cohesion: 0.26
Nodes (5): ChunkTask, TransportEndpoint, ChunkVisualItem, SchedulerTelemetry, TransportStats

### Community 48 - "app.js"
Cohesion: 0.16
Nodes (6): initMultipathCanvas(), drawDeviceNode(), render(), initSpeedCalculator(), formatTime(), updateCalculations()

### Community 50 - "bluetooth.rs"
Cohesion: 0.13
Nodes (8): COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID, COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID

### Community 51 - "TransportMetrics"
Cohesion: 0.18
Nodes (4): AggregateMetrics, ChunkVisualState, SchedulerTelemetry, TransportMetrics

### Community 52 - "TransferHistoryScreen.kt"
Cohesion: 0.09
Nodes (5): CompactTransferRecordRow(), formatFileSize(), getFileIcon(), openTransferredFile(), TransferHistoryScreen()

### Community 53 - "⚡ ShareDash"
Cohesion: 0.10
Nodes (20): 🧪 Automated Test Suite, Build from Source via Gradle:, 🤝 Contributing, Direct Installation via ADB:, 🚀 Key Highlights, 📜 License, 📊 Live Benchmark Performance, Option A: Launch Interactive Auto-Connect Wizard (+12 more)

### Community 57 - "NavTab"
Cohesion: 0.33
Nodes (5): NavTab, HISTORY, HOME, SETTINGS, TRANSFER

### Community 58 - "⚡ ShareDash: Technical Architecture & Operational Guide"
Cohesion: 0.11
Nodes (19): 1. Performance Summary Matrix, 1. Wifite-Style Terminal Interface (Default), 2. Fluent Mica Web Dashboard, 2. Speedup & Efficiency Analysis, 📱 Android Companion App Architecture, 🖥️ CLI & Web UI User Interfaces, 🔒 Cryptographic Security & Verification, 🌟 Executive Summary & Offline Philosophy (+11 more)

### Community 60 - "BridgeBadge"
Cohesion: 0.43
Nodes (4): BridgeBadge(), RecommendationRow(), SmartBridgeStrip(), TransportRecommendationCard()

### Community 62 - ".send_file_multipath_loop"
Cohesion: 0.32
Nodes (6): get_dir_size(), package_directory_to_zip(), print_channel_summary(), print_fail(), prompt(), prompt_choice()

### Community 63 - "TransportState"
Cohesion: 0.25
Nodes (8): TransportState, Active, Benchmarking, Closed, Connected, Connecting, Degraded, Disconnected

### Community 65 - "WifiCapsInfo"
Cohesion: 0.18
Nodes (9): WifiCapsInfo, detect_pc_wifi_caps(), HotspotHostChoice, Pc, Phone, select_best_hotspot_host(), select_optimal_hotspot_host(), select_optimal_pc_to_pc_host() (+1 more)

### Community 66 - "ChunkState"
Cohesion: 0.40
Nodes (5): ChunkState, COMPLETED, CORRUPTED, IN_FLIGHT, PENDING

### Community 68 - "TransferUiStage"
Cohesion: 0.40
Nodes (5): TransferUiStage, COMPLETED, FAILED, TRANSFERRING, VERIFYING

### Community 70 - "🤝 Contributing to ShareDash"
Cohesion: 0.15
Nodes (13): 1. Rust Core Engine (Windows / Linux), 2. Android Companion App, Android / Kotlin, 📜 Code of Conduct, 📐 Coding Guidelines & Standards, 💬 Commit Message Conventions, 🤝 Contributing to ShareDash, 🛠️ Development Setup (+5 more)

### Community 71 - "TransferEvent"
Cohesion: 0.18
Nodes (10): TransferEvent, Cancelled, ChunkCompleted, ChunkCorrupt, Completed, Failed, Progress, Started (+2 more)

### Community 73 - "PostResult"
Cohesion: 0.50
Nodes (4): PostResult, NOT_FOUND, RETRY, SUCCESS

### Community 80 - "TransferStatus"
Cohesion: 0.20
Nodes (8): TransferRecord, TransferStatus, Active, Cancelled, Completed, Failed, Paused, Pending

### Community 82 - "🗺️ ShareDash Product & Engineering Roadmap"
Cohesion: 0.20
Nodes (9): 📱 Android Enhancements, 🎯 Current Status (v0.1.0), 🔮 Future Vision (v1.0.0+), 💡 Have an idea?, 🖥️ Native Cross-Platform PC Clients, 🚀 Near-Term Milestones (v0.2.0 - v0.3.0), ⚡ Protocol & Transfer Optimizations, 🗺️ ShareDash Product & Engineering Roadmap (+1 more)

### Community 84 - "Contributor Covenant Code of Conduct"
Cohesion: 0.22
Nodes (7): Attribution, Contributor Covenant Code of Conduct, Enforcement, Enforcement Responsibilities, Our Pledge, Our Standards, Scope

### Community 85 - "README.md"
Cohesion: 0.25
Nodes (5): 🚨 Reporting a Vulnerability, 🔒 Security & Cryptographic Architecture, 🛡️ Security Policy, Supported Versions, What to Include:

### Community 86 - "TransportKind"
Cohesion: 0.25
Nodes (8): TransportKind, Bluetooth, InternetQuic, Lan, MockSim, Usb, WifiDirect, TransportUpdateMessage

### Community 87 - "PULL_REQUEST_TEMPLATE.md"
Cohesion: 0.29
Nodes (6): ✅ Contributor Checklist, 📋 Description, 🔗 Related Issue(s), 📸 Screenshots / Logs (if applicable), 🧪 Testing & Verification, 🛠️ Type of Change

### Community 88 - "🚀 Running Benchmarks"
Cohesion: 0.29
Nodes (6): 1. Generate Test File, 2. Run Analytical Transfer Optimization, 3. Run Live ADB Phone Benchmark, 🚀 Running Benchmarks, 📁 Script Overview, 🛠️ ShareDash Diagnostic & Benchmarking Scripts

### Community 90 - "ChunkState"
Cohesion: 0.33
Nodes (5): ChunkState, Completed, Corrupt, InFlight, Pending

### Community 92 - "⚡ High-Throughput Pipelined Multipath Streaming Engine"
Cohesion: 0.40
Nodes (5): 1. 📦 Adaptive Chunk Sizing, 2. 🌊 3 Pipelined In-Flight Workers Per Transport, 3. ⚡ Android Concurrent `FileChannel` Disk Writes, 4. 📈 Exact Per-Channel Bandwidth Accounting, ⚡ High-Throughput Pipelined Multipath Streaming Engine

### Community 94 - "ChunkStatus"
Cohesion: 0.40
Nodes (5): ChunkStatus, Completed, Corrupted, InFlight, Unassigned

### Community 95 - "DiscoveryMethod"
Cohesion: 0.50
Nodes (4): DiscoveryMethod, Ble, HttpProbe, Udp

## Knowledge Gaps
- **110 isolated node(s):** `sharedash`, `SUCCESS`, `NOT_FOUND`, `RETRY`, `Idle` (+105 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 623 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **46 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HotspotManager` connect `HotspotManager` to `MainActivity.kt`, `MainActivity`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **What connects `sharedash`, `SUCCESS`, `NOT_FOUND` to the rest of the system?**
  _110 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `api.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.07496194824961948 - nodes in this community are weakly interconnected._
- **Why does `MockSimTransport` connect `MockSimTransport` to `api.rs`, `lan.rs`, `failover_test.rs`, `TransportMetrics`, `AsyncTransport`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Should `HomeScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09061224489795919 - nodes in this community are weakly interconnected._
- **Why does `MainActivity` connect `MainActivity` to `AndroidTransportManager`, `BleDiscoveryManager.kt`, `HotspotManager`, `UdpDiscoveryManager`, `TransferForegroundService`, `.executeRealTransfer`, `TransferHistoryManager`, `MainActivity.kt`, `BleCommandServer`, `SettingsManager`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Should `BleDiscoveryManager.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.12 - nodes in this community are weakly interconnected._