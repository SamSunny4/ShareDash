# Graph Report - ShareDash  (2026-11-06)

## Corpus Check
- 95 files · ~97,362 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 8, (none) 3, .properties 2)

## Summary
- 1671 nodes · 3892 edges · 88 communities (49 shown, 39 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 46 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `cb05c773`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- api.rs
- frame.rs
- HomeScreen.kt
- gui.rs
- bluetooth.rs
- message.rs
- ConnectedScreen.kt
- benchmark_suite.py
- TransferScreen.kt
- TransferForegroundService
- AndroidHttpServer
- manifest_db.rs
- cli_widgets.rs
- BleDiscovery
- MainActivity.kt
- .run_wireless_direct_wizard
- NeoCard
- sparse_writer.rs
- dynamic_scheduler.rs
- hotspot.rs
- .executeRealTransfer
- chunker.rs
- Protocol
- MockSimTransport
- ChunkPool
- failover_test.rs
- FrameType
- WifiCapsInfo
- crypto.rs
- handshake.rs
- mdns.rs
- NavTab
- UdpDiscoveryManager.kt
- result
- run_transport_chunk_worker
- LanTransport
- QuicInetTransport
- UsbTransport
- Models.kt
- TerminalCli
- UdpDiscoveryManager
- TransportState
- TransferHistoryManager
- main.rs
- ChunkState
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
- select_optimal_pc_to_pc_host
- BridgeBadge
- cli.rs
- .send_file_multipath_loop
- DiscoveryMethod
- TransferUiStage
- 🤝 Contributing to ShareDash
- NetworkCallback
- sharedash
- SettingsScreen.kt
- TransferStatus
- 🗺️ ShareDash Product & Engineering Roadmap
- Contributor Covenant Code of Conduct
- README.md
- PULL_REQUEST_TEMPLATE.md
- 🚀 Running Benchmarks
- wifi_direct.rs
- ⚡ High-Throughput Pipelined Multipath Streaming Engine
- PairingScreen.kt
- usb.rs
- TransportMetrics
- ChunkState

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 42 edges
2. `AppState` - 40 edges
3. `TerminalCli` - 39 edges
4. `NeoCard()` - 35 edges
5. `ShareDashGui` - 32 edges
6. `MockSimTransport` - 31 edges
7. `AndroidHttpServer` - 30 edges
8. `FrameType` - 30 edges
9. `NeoInset()` - 29 edges
10. `NeoButton()` - 28 edges

## Surprising Connections (you probably didn't know these)
- `test_select_optimal_pc_to_pc_host_and_band()` --calls--> `select_optimal_pc_to_pc_host()`  [INFERRED]
  tests/test_pc_to_pc.rs → src/hotspot.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/corruption_test.rs → src/transport/mock_sim.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/failover_test.rs → src/transport/mock_sim.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/multipath_benchmark.rs → src/transport/mock_sim.rs
- `MainActivity` --references--> `HotspotManager`  [EXTRACTED]
  android/app/src/main/java/com/sharedash/app/MainActivity.kt → android/app/src/main/java/com/sharedash/app/discovery/HotspotManager.kt

## Import Cycles
- None detected.

## Communities (88 total, 39 thin omitted)

### Community 0 - "api.rs"
Cohesion: 0.05
Nodes (55): PairingManager, PairingSession, ACTIVE_CHUNK_RECEIVERS, ActiveIncomingChunkSession, ADB_CACHE, ADB_CACHE_TTL_SECS, AdbCacheEntry, AppState (+47 more)

### Community 1 - "frame.rs"
Cohesion: 0.05
Nodes (38): FLAG_BENCHMARK, FLAG_COMPRESSED, FLAG_ENCRYPTED, FLAG_NONE, FLAG_PRIORITY, Frame, FRAME_HEADER_LEN, FrameCodec (+30 more)

### Community 3 - "gui.rs"
Cohesion: 0.06
Nodes (22): AMBER_WARN, BG_DARK, calc_dir_size(), CARD_BG, CARD_BG_HOVER, CARD_BORDER, CYAN_ACCENT, CYAN_GLOW (+14 more)

### Community 4 - "bluetooth.rs"
Cohesion: 0.13
Nodes (8): COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID, COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID

### Community 5 - "message.rs"
Cohesion: 0.07
Nodes (24): BenchmarkProbeMessage, BenchmarkRespMessage, ChunkAckMessage, ChunkRejectMessage, ChunkReqMessage, ControlMessageWrapper, HelloMessage, HelloRespMessage (+16 more)

### Community 7 - "benchmark_suite.py"
Cohesion: 0.09
Nodes (15): check_adb_state(), install_and_start_app(), perform_stream_transfer(), probe_phone(), run_cmd(), run_suite(), setup_adb(), get_file_sha256() (+7 more)

### Community 9 - "TransferScreen.kt"
Cohesion: 0.15
Nodes (6): LegendChip(), PieceVisualizerGrid(), ActiveTransferScreen(), TransferCategoryItem(), TransferIdleScreen(), TransferScreen()

### Community 14 - "cli_widgets.rs"
Cohesion: 0.08
Nodes (24): animated_scan_progress(), BLUE, BOLD, BOLD_CYAN, BOLD_GREEN, BOLD_RED, BOLD_WHITE, BOLD_YELLOW (+16 more)

### Community 16 - "MainActivity.kt"
Cohesion: 0.08
Nodes (5): DeviceIdentity, PostResult, NOT_FOUND, RETRY, SUCCESS

### Community 17 - ".run_wireless_direct_wizard"
Cohesion: 0.20
Nodes (7): draw_spinner_frame(), print_ok(), print_phase_header(), print_step_result(), print_tree_item(), print_warn(), with_spinner()

### Community 18 - "NeoCard"
Cohesion: 0.14
Nodes (10): ConnectingDialog(), UsbPromptDialog(), WifiDisconnectPromptDialog(), WirelessWarningDialog(), DiscoveryScreen(), HomeScreen(), UsbFirstScreen(), NeoButton() (+2 more)

### Community 19 - "sparse_writer.rs"
Cohesion: 0.16
Nodes (4): FileTime, set_file_mtime(), SparseWriter, test_sparse_writer_write_out_of_order()

### Community 20 - "dynamic_scheduler.rs"
Cohesion: 0.14
Nodes (12): MultipathScheduler, TransferEvent, Cancelled, ChunkCompleted, ChunkCorrupt, Completed, Failed, Progress (+4 more)

### Community 21 - "hotspot.rs"
Cohesion: 0.10
Nodes (19): apply_and_connect_profile(), check_pc_wifi_adapter_enabled(), connect_to_phone_hotspot(), create_hotspot(), create_hotspot_with_band(), detect_hotspot_gateway(), detect_usb_tethering_peer(), detect_usb_tethering_peer_detailed() (+11 more)

### Community 22 - ".executeRealTransfer"
Cohesion: 0.26
Nodes (5): ChunkTask, TransportEndpoint, ChunkVisualItem, SchedulerTelemetry, TransportStats

### Community 23 - "chunker.rs"
Cohesion: 0.19
Nodes (8): FileMetadata, AdaptiveChunker, ChunkInfo, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE, MIN_CHUNK_SIZE, test_build_manifest_from_temp_file(), TransferManifest

### Community 24 - "Protocol"
Cohesion: 0.20
Nodes (4): Frame, FrameHeader, Protocol, AndroidTransportManager

### Community 26 - "ChunkPool"
Cohesion: 0.12
Nodes (8): ChunkPool, ChunkPoolInner, ChunkStatus, Completed, Corrupted, InFlight, Unassigned, InFlightInfo

### Community 27 - "failover_test.rs"
Cohesion: 0.18
Nodes (6): mock_responder(), test_cryptographic_corruption_rejection_and_recovery(), mock_responder(), test_work_stealing_and_cable_disconnect_failover(), mock_responder(), test_multipath_concurrent_transports()

### Community 28 - "FrameType"
Cohesion: 0.13
Nodes (14): FrameType, BENCHMARK_PROBE, BENCHMARK_RESP, CAPABILITIES, CHUNK_ACK, CHUNK_DATA, CHUNK_REJECT, CHUNK_REQ (+6 more)

### Community 29 - "WifiCapsInfo"
Cohesion: 0.17
Nodes (8): BleAdvertiser, WifiCapsInfo, detect_pc_wifi_caps(), HotspotHostChoice, Pc, Phone, select_best_hotspot_host(), select_optimal_hotspot_host()

### Community 30 - "crypto.rs"
Cohesion: 0.14
Nodes (3): SessionCrypto, test_encryption_roundtrip(), test_hashers()

### Community 31 - "handshake.rs"
Cohesion: 0.14
Nodes (10): build_local_capabilities(), HandshakeState, Established, Failed, Idle, SynReceived, SynSent, SessionInfo (+2 more)

### Community 32 - "mdns.rs"
Cohesion: 0.13
Nodes (7): CURRENT_APP_VERSION, DiscoveredPeer, DISCOVERY_BROADCAST_PORT, is_version_compatible(), MIN_SUPPORTED_APP_VERSION, PeerBeacon, PeerDiscovery

### Community 33 - "NavTab"
Cohesion: 0.22
Nodes (6): BottomPillBar(), NavTab, HISTORY, HOME, SETTINGS, TRANSFER

### Community 36 - "run_transport_chunk_worker"
Cohesion: 0.18
Nodes (7): DynamicWorkDispatcher, run_transport_chunk_worker(), stream_upload_channel(), TransferChunkInfo, ChannelProgress, draw_transfer_progress(), init_transfer_progress()

### Community 40 - "Models.kt"
Cohesion: 0.14
Nodes (17): ChunkInfo, FileMeta, TransferDirection, RECEIVED, SENT, TransferManifest, TransferRecord, TransferStatus (+9 more)

### Community 44 - "TransportState"
Cohesion: 0.25
Nodes (8): TransportState, Active, Benchmarking, Closed, Connected, Connecting, Degraded, Disconnected

### Community 47 - "ChunkState"
Cohesion: 0.40
Nodes (5): ChunkState, COMPLETED, CORRUPTED, IN_FLIGHT, PENDING

### Community 48 - "app.js"
Cohesion: 0.16
Nodes (6): initMultipathCanvas(), drawDeviceNode(), render(), initSpeedCalculator(), formatTime(), updateCalculations()

### Community 50 - "HotspotManager"
Cohesion: 0.07
Nodes (10): Active, Error, HotspotManager, ActionListener, ActionListener, ActionListener, ActionListener, HotspotState (+2 more)

### Community 52 - "TransferHistoryScreen.kt"
Cohesion: 0.09
Nodes (5): CompactTransferRecordRow(), formatFileSize(), getFileIcon(), openTransferredFile(), TransferHistoryScreen()

### Community 53 - "⚡ ShareDash"
Cohesion: 0.10
Nodes (20): 🧪 Automated Test Suite, Build from Source via Gradle:, 🤝 Contributing, Direct Installation via ADB:, 🚀 Key Highlights, 📜 License, 📊 Live Benchmark Performance, Option A: Launch Interactive Auto-Connect Wizard (+12 more)

### Community 58 - "⚡ ShareDash: Technical Architecture & Operational Guide"
Cohesion: 0.11
Nodes (19): 1. Performance Summary Matrix, 1. Wifite-Style Terminal Interface (Default), 2. Fluent Mica Web Dashboard, 2. Speedup & Efficiency Analysis, 📱 Android Companion App Architecture, 🖥️ CLI & Web UI User Interfaces, 🔒 Cryptographic Security & Verification, 🌟 Executive Summary & Offline Philosophy (+11 more)

### Community 60 - "BridgeBadge"
Cohesion: 0.43
Nodes (4): BridgeBadge(), RecommendationRow(), SmartBridgeStrip(), TransportRecommendationCard()

### Community 62 - ".send_file_multipath_loop"
Cohesion: 0.32
Nodes (6): get_dir_size(), package_directory_to_zip(), print_channel_summary(), print_fail(), prompt(), prompt_choice()

### Community 63 - "DiscoveryMethod"
Cohesion: 0.50
Nodes (4): DiscoveryMethod, Ble, HttpProbe, Udp

### Community 68 - "TransferUiStage"
Cohesion: 0.40
Nodes (5): TransferUiStage, COMPLETED, FAILED, TRANSFERRING, VERIFYING

### Community 70 - "🤝 Contributing to ShareDash"
Cohesion: 0.15
Nodes (13): 1. Rust Core Engine (Windows / Linux), 2. Android Companion App, Android / Kotlin, 📜 Code of Conduct, 📐 Coding Guidelines & Standards, 💬 Commit Message Conventions, 🤝 Contributing to ShareDash, 🛠️ Development Setup (+5 more)

### Community 81 - "TransferStatus"
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

### Community 87 - "PULL_REQUEST_TEMPLATE.md"
Cohesion: 0.29
Nodes (6): ✅ Contributor Checklist, 📋 Description, 🔗 Related Issue(s), 📸 Screenshots / Logs (if applicable), 🧪 Testing & Verification, 🛠️ Type of Change

### Community 88 - "🚀 Running Benchmarks"
Cohesion: 0.29
Nodes (6): 1. Generate Test File, 2. Run Analytical Transfer Optimization, 3. Run Live ADB Phone Benchmark, 🚀 Running Benchmarks, 📁 Script Overview, 🛠️ ShareDash Diagnostic & Benchmarking Scripts

### Community 89 - "wifi_direct.rs"
Cohesion: 0.17
Nodes (3): detect_wifi_capabilities(), WifiCapabilities, WifiDirectTransport

### Community 92 - "⚡ High-Throughput Pipelined Multipath Streaming Engine"
Cohesion: 0.40
Nodes (5): 1. 📦 Adaptive Chunk Sizing, 2. 🌊 3 Pipelined In-Flight Workers Per Transport, 3. ⚡ Android Concurrent `FileChannel` Disk Writes, 4. 📈 Exact Per-Channel Bandwidth Accounting, ⚡ High-Throughput Pipelined Multipath Streaming Engine

### Community 98 - "TransportMetrics"
Cohesion: 0.26
Nodes (4): AggregateMetrics, ChunkVisualState, SchedulerTelemetry, TransportMetrics

### Community 107 - "ChunkState"
Cohesion: 0.33
Nodes (5): ChunkState, Completed, Corrupt, InFlight, Pending

## Knowledge Gaps
- **115 isolated node(s):** `sharedash`, `SUCCESS`, `NOT_FOUND`, `RETRY`, `Idle` (+110 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 600 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **39 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TerminalCli` connect `TerminalCli` to `api.rs`, `gui.rs`, `run_transport_chunk_worker`, `lib.rs`, `main.rs`, `.run_wireless_direct_wizard`, `WifiCapsInfo`, `cli.rs`, `.send_file_multipath_loop`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **What connects `sharedash`, `SUCCESS`, `NOT_FOUND` to the rest of the system?**
  _115 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `api.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.051096491228070175 - nodes in this community are weakly interconnected._
- **Why does `AndroidHttpServer` connect `AndroidHttpServer` to `AndroidHttpServer.kt`, `MainActivity`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **Should `frame.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.05323653962492438 - nodes in this community are weakly interconnected._
- **Should `HomeScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.06949152542372881 - nodes in this community are weakly interconnected._
- **Should `gui.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.06384180790960452 - nodes in this community are weakly interconnected._