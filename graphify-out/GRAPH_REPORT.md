# Graph Report - ShareDash  (2026-11-06)

## Corpus Check
- 104 files · ~734,548 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 20 file(s) not represented in the graph (top: .xml 8, (none) 3, .properties 2)

## Summary
- 1781 nodes · 4136 edges · 93 communities (55 shown, 38 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 80 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `bba24fe5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- api.rs
- frame.rs
- HomeScreen.kt
- ShareDashApp
- widgets.rs
- message.rs
- TransferScreen.kt
- benchmark_suite.py
- PieceGrid.kt
- TransferForegroundService
- AndroidHttpServer
- manifest_db.rs
- cli_widgets.rs
- TransferStatus
- MainActivity.kt
- .run_wireless_direct_wizard
- NeoCard
- sparse_writer.rs
- dynamic_scheduler.rs
- hotspot.rs
- .onCreate
- chunker.rs
- FrameType
- MockSimTransport
- ChunkPool
- failover_test.rs
- TransportKind
- theme.rs
- crypto.rs
- handshake.rs
- mdns.rs
- NavTab
- UdpDiscoveryManager.kt
- run_transport_chunk_worker
- LanTransport
- quic_inet.rs
- UsbTransport
- Models.kt
- UdpDiscoveryManager
- TransportState
- TransferHistoryManager
- sharedash_gui.rs
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
- ControlMessageWrapper
- BridgeBadge
- bluetooth.rs
- .send_file_multipath_loop
- .postChunkDetailed
- run_gui_chunk_worker
- GuiEngine
- ConnectedScreen.kt
- app.rs
- TransferUiStage
- TerminalCli
- 🤝 Contributing to ShareDash
- WizardEvent
- .connectToWifiNetwork
- ws_telemetry.rs
- sharedash
- SettingsScreen.kt
- render_home_view
- 🗺️ ShareDash Product & Engineering Roadmap
- Contributor Covenant Code of Conduct
- README.md
- TransportMetrics
- PULL_REQUEST_TEMPLATE.md
- 🚀 Running Benchmarks
- wifi_direct.rs
- cli.rs
- TransferViewState
- ⚡ High-Throughput Pipelined Multipath Streaming Engine
- Color.kt
- usb.rs
- result

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 42 edges
2. `AppState` - 37 edges
3. `NeoCard()` - 35 edges
4. `TerminalCli` - 33 edges
5. `MockSimTransport` - 31 edges
6. `AndroidHttpServer` - 30 edges
7. `FrameType` - 30 edges
8. `NeoInset()` - 29 edges
9. `NeoButton()` - 28 edges
10. `BleDiscovery` - 27 edges

## Surprising Connections (you probably didn't know these)
- `test_select_optimal_pc_to_pc_host_and_band()` --calls--> `select_optimal_pc_to_pc_host()`  [INFERRED]
  tests/test_pc_to_pc.rs → src/hotspot.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/corruption_test.rs → src/transport/mock_sim.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/failover_test.rs → src/transport/mock_sim.rs
- `mock_responder()` --references--> `MockSimTransport`  [EXTRACTED]
  tests/multipath_benchmark.rs → src/transport/mock_sim.rs
- `render_home_view()` --calls--> `action_button()`  [INFERRED]
  src/gui/home_view.rs → src/gui/widgets.rs

## Import Cycles
- None detected.

## Communities (93 total, 38 thin omitted)

### Community 0 - "api.rs"
Cohesion: 0.05
Nodes (55): PairingManager, PairingSession, ACTIVE_CHUNK_RECEIVERS, ActiveIncomingChunkSession, ADB_CACHE, ADB_CACHE_TTL_SECS, AdbCacheEntry, AppState (+47 more)

### Community 1 - "frame.rs"
Cohesion: 0.05
Nodes (38): FLAG_BENCHMARK, FLAG_COMPRESSED, FLAG_ENCRYPTED, FLAG_NONE, FLAG_PRIORITY, Frame, FRAME_HEADER_LEN, FrameCodec (+30 more)

### Community 2 - "HomeScreen.kt"
Cohesion: 0.09
Nodes (3): DiscoveredPeer, QuickShareRadar(), DiscoveryScreen()

### Community 3 - "ShareDashApp"
Cohesion: 0.19
Nodes (7): ActiveView, Home, Info, PcToPc, Transfer, Wizard, ShareDashApp

### Community 4 - "widgets.rs"
Cohesion: 0.12
Nodes (22): render_pc2pc_view(), render_transfer_view(), action_button(), back_button(), banner(), card_button(), channel_badge(), log_list() (+14 more)

### Community 5 - "message.rs"
Cohesion: 0.18
Nodes (12): BenchmarkProbeMessage, BenchmarkRespMessage, ChunkAckMessage, ChunkRejectMessage, ChunkReqMessage, HelloMessage, HelloRespMessage, ManifestMessage (+4 more)

### Community 6 - "TransferScreen.kt"
Cohesion: 0.14
Nodes (4): ActiveTransferScreen(), TransferCategoryItem(), TransferIdleScreen(), TransferScreen()

### Community 7 - "benchmark_suite.py"
Cohesion: 0.10
Nodes (15): check_adb_state(), install_and_start_app(), perform_stream_transfer(), probe_phone(), run_cmd(), run_suite(), setup_adb(), get_file_sha256() (+7 more)

### Community 13 - "manifest_db.rs"
Cohesion: 0.09
Nodes (15): ChunkState, Completed, Corrupt, InFlight, Pending, ManifestDb, test_manifest_db_lifecycle(), TransferRecord (+7 more)

### Community 14 - "cli_widgets.rs"
Cohesion: 0.09
Nodes (22): animated_scan_progress(), BLUE, BOLD, BOLD_CYAN, BOLD_GREEN, BOLD_RED, BOLD_WHITE, BOLD_YELLOW (+14 more)

### Community 15 - "TransferStatus"
Cohesion: 0.40
Nodes (5): TransferStatus, CANCELLED, COMPLETED, FAILED, IN_PROGRESS

### Community 17 - ".run_wireless_direct_wizard"
Cohesion: 0.17
Nodes (9): draw_spinner_frame(), print_ok(), print_phase_header(), print_step_result(), print_table(), print_tree_item(), print_warn(), strip_ansi() (+1 more)

### Community 18 - "NeoCard"
Cohesion: 0.09
Nodes (11): ConnectingDialog(), UsbPromptDialog(), WifiDisconnectPromptDialog(), WirelessWarningDialog(), ConnectedScreen(), SendCategoryCard(), HomeScreen(), UsbFirstScreen() (+3 more)

### Community 19 - "sparse_writer.rs"
Cohesion: 0.17
Nodes (5): FileMetadata, FileTime, set_file_mtime(), SparseWriter, test_sparse_writer_write_out_of_order()

### Community 20 - "dynamic_scheduler.rs"
Cohesion: 0.12
Nodes (12): MultipathScheduler, TransferEvent, Cancelled, ChunkCompleted, ChunkCorrupt, Completed, Failed, Progress (+4 more)

### Community 21 - "hotspot.rs"
Cohesion: 0.07
Nodes (28): WifiCapsInfo, apply_and_connect_profile(), check_pc_wifi_adapter_enabled(), connect_to_phone_hotspot(), create_hotspot(), create_hotspot_with_band(), detect_hotspot_gateway(), detect_pc_wifi_caps() (+20 more)

### Community 22 - ".onCreate"
Cohesion: 0.35
Nodes (5): ChunkTask, ChunkVisualItem, SchedulerTelemetry, TransferRecord, TransportStats

### Community 23 - "chunker.rs"
Cohesion: 0.17
Nodes (7): AdaptiveChunker, ChunkInfo, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE, MIN_CHUNK_SIZE, test_build_manifest_from_temp_file(), TransferManifest

### Community 24 - "FrameType"
Cohesion: 0.09
Nodes (18): Frame, FrameHeader, FrameType, BENCHMARK_PROBE, BENCHMARK_RESP, CAPABILITIES, CHUNK_ACK, CHUNK_DATA (+10 more)

### Community 26 - "ChunkPool"
Cohesion: 0.12
Nodes (8): ChunkPool, ChunkPoolInner, ChunkStatus, Completed, Corrupted, InFlight, Unassigned, InFlightInfo

### Community 27 - "failover_test.rs"
Cohesion: 0.10
Nodes (6): mock_responder(), test_cryptographic_corruption_rejection_and_recovery(), mock_responder(), test_work_stealing_and_cable_disconnect_failover(), mock_responder(), test_multipath_concurrent_transports()

### Community 28 - "TransportKind"
Cohesion: 0.25
Nodes (8): TransportKind, Bluetooth, InternetQuic, Lan, MockSim, Usb, WifiDirect, TransportUpdateMessage

### Community 29 - "theme.rs"
Cohesion: 0.16
Nodes (19): accent_blue(), accent_cyan(), accent_green(), accent_magenta(), accent_red(), accent_yellow(), bg_active(), bg_base() (+11 more)

### Community 30 - "crypto.rs"
Cohesion: 0.13
Nodes (3): SessionCrypto, test_encryption_roundtrip(), test_hashers()

### Community 31 - "handshake.rs"
Cohesion: 0.16
Nodes (10): build_local_capabilities(), HandshakeState, Established, Failed, Idle, SynReceived, SynSent, SessionInfo (+2 more)

### Community 32 - "mdns.rs"
Cohesion: 0.12
Nodes (7): CURRENT_APP_VERSION, DiscoveredPeer, DISCOVERY_BROADCAST_PORT, is_version_compatible(), MIN_SUPPORTED_APP_VERSION, PeerBeacon, PeerDiscovery

### Community 33 - "NavTab"
Cohesion: 0.22
Nodes (6): BottomPillBar(), NavTab, HISTORY, HOME, SETTINGS, TRANSFER

### Community 36 - "run_transport_chunk_worker"
Cohesion: 0.18
Nodes (7): DynamicWorkDispatcher, run_transport_chunk_worker(), stream_upload_channel(), TransferChunkInfo, ChannelProgress, draw_transfer_progress(), init_transfer_progress()

### Community 40 - "Models.kt"
Cohesion: 0.13
Nodes (16): ChunkInfo, ChunkState, COMPLETED, CORRUPTED, IN_FLIGHT, PENDING, FileMeta, TransferDirection (+8 more)

### Community 44 - "TransportState"
Cohesion: 0.25
Nodes (8): TransportState, Active, Benchmarking, Closed, Connected, Connecting, Degraded, Disconnected

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

### Community 61 - "bluetooth.rs"
Cohesion: 0.07
Nodes (14): COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID, BleAdvertiser, BleDiscovery, COMMAND_CHAR_UUID, DiscoveryMethod (+6 more)

### Community 62 - ".send_file_multipath_loop"
Cohesion: 0.32
Nodes (6): get_dir_size(), package_directory_to_zip(), print_channel_summary(), print_fail(), prompt(), prompt_choice()

### Community 63 - ".postChunkDetailed"
Cohesion: 0.22
Nodes (5): PostResult, NOT_FOUND, RETRY, SUCCESS, TransportEndpoint

### Community 64 - "run_gui_chunk_worker"
Cohesion: 0.19
Nodes (4): GuiChunkInfo, GuiWorkDispatcher, package_directory_to_zip(), run_gui_chunk_worker()

### Community 65 - "GuiEngine"
Cohesion: 0.15
Nodes (3): DiscoveredDevice, GuiEngine, PcToPcViewState

### Community 67 - "app.rs"
Cohesion: 0.19
Nodes (6): HardwareMonitorData, HomeStatus, PendingPairData, InfoViewState, InterfaceEntry, render_info_view()

### Community 68 - "TransferUiStage"
Cohesion: 0.40
Nodes (5): TransferUiStage, COMPLETED, FAILED, TRANSFERRING, VERIFYING

### Community 70 - "🤝 Contributing to ShareDash"
Cohesion: 0.15
Nodes (13): 1. Rust Core Engine (Windows / Linux), 2. Android Companion App, Android / Kotlin, 📜 Code of Conduct, 📐 Coding Guidelines & Standards, 💬 Commit Message Conventions, 🤝 Contributing to ShareDash, 🛠️ Development Setup (+5 more)

### Community 71 - "WizardEvent"
Cohesion: 0.11
Nodes (12): WizardEvent, CapsSynchronized, HotspotCredsTransferred, Log, Phase2Start, Phase3OptimalHost, Phase4HotspotActive, Phase5UsbConfirmed (+4 more)

### Community 73 - "ws_telemetry.rs"
Cohesion: 0.22
Nodes (3): Server, handle_socket(), ws_telemetry_handler()

### Community 82 - "🗺️ ShareDash Product & Engineering Roadmap"
Cohesion: 0.20
Nodes (9): 📱 Android Enhancements, 🎯 Current Status (v0.1.0), 🔮 Future Vision (v1.0.0+), 💡 Have an idea?, 🖥️ Native Cross-Platform PC Clients, 🚀 Near-Term Milestones (v0.2.0 - v0.3.0), ⚡ Protocol & Transfer Optimizations, 🗺️ ShareDash Product & Engineering Roadmap (+1 more)

### Community 84 - "Contributor Covenant Code of Conduct"
Cohesion: 0.22
Nodes (7): Attribution, Contributor Covenant Code of Conduct, Enforcement, Enforcement Responsibilities, Our Pledge, Our Standards, Scope

### Community 85 - "README.md"
Cohesion: 0.25
Nodes (5): 🚨 Reporting a Vulnerability, 🔒 Security & Cryptographic Architecture, 🛡️ Security Policy, Supported Versions, What to Include:

### Community 86 - "TransportMetrics"
Cohesion: 0.29
Nodes (4): AggregateMetrics, ChunkVisualState, SchedulerTelemetry, TransportMetrics

### Community 87 - "PULL_REQUEST_TEMPLATE.md"
Cohesion: 0.29
Nodes (6): ✅ Contributor Checklist, 📋 Description, 🔗 Related Issue(s), 📸 Screenshots / Logs (if applicable), 🧪 Testing & Verification, 🛠️ Type of Change

### Community 88 - "🚀 Running Benchmarks"
Cohesion: 0.29
Nodes (6): 1. Generate Test File, 2. Run Analytical Transfer Optimization, 3. Run Live ADB Phone Benchmark, 🚀 Running Benchmarks, 📁 Script Overview, 🛠️ ShareDash Diagnostic & Benchmarking Scripts

### Community 89 - "wifi_direct.rs"
Cohesion: 0.16
Nodes (3): detect_wifi_capabilities(), WifiCapabilities, WifiDirectTransport

### Community 91 - "TransferViewState"
Cohesion: 0.25
Nodes (7): ChannelStats, TransferProgressSnapshot, SendModeChoice, Multipath, UsbOnly, WifiOnly, TransferViewState

### Community 92 - "⚡ High-Throughput Pipelined Multipath Streaming Engine"
Cohesion: 0.40
Nodes (5): 1. 📦 Adaptive Chunk Sizing, 2. 🌊 3 Pipelined In-Flight Workers Per Transport, 3. ⚡ Android Concurrent `FileChannel` Disk Writes, 4. 📈 Exact Per-Channel Bandwidth Accounting, ⚡ High-Throughput Pipelined Multipath Streaming Engine

## Knowledge Gaps
- **126 isolated node(s):** `sharedash`, `SUCCESS`, `NOT_FOUND`, `RETRY`, `Idle` (+121 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 613 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **38 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AndroidHttpServer` connect `AndroidHttpServer` to `AndroidHttpServer.kt`, `.onCreate`?**
  _High betweenness centrality (0.052) - this node is a cross-community bridge._
- **What connects `sharedash`, `SUCCESS`, `NOT_FOUND` to the rest of the system?**
  _126 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `api.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.0547008547008547 - nodes in this community are weakly interconnected._
- **Why does `MainActivity` connect `MainActivity` to `.connectToWifiNetwork`, `UdpDiscoveryManager`, `TransferForegroundService`, `TransferHistoryManager`, `MainActivity.kt`, `HotspotManager`, `.onCreate`, `SettingsManager`, `FrameType`, `.postChunkDetailed`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Should `frame.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.0519774011299435 - nodes in this community are weakly interconnected._
- **Should `HomeScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09210526315789473 - nodes in this community are weakly interconnected._
- **Should `widgets.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.12012012012012012 - nodes in this community are weakly interconnected._