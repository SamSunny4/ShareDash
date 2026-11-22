# Graph Report - ShareDash  (2026-11-22)

## Corpus Check
- 105 files · ~733,319 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 21 file(s) not represented in the graph (top: .xml 8, (none) 3, .toml 2)

## Summary
- 1784 nodes · 4194 edges · 97 communities (59 shown, 38 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 98 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9d6aed44`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- api.rs
- frame.rs
- HomeScreen.kt
- ShareDashApp
- widgets.rs
- handshake.rs
- TransferScreen.kt
- benchmark_suite.py
- render_wizard_view
- TransferForegroundService
- AndroidHttpServer
- manifest_db.rs
- cli_widgets.rs
- bluetooth.rs
- MainActivity.kt
- TerminalCli
- NeoCard
- sparse_writer.rs
- dynamic_scheduler.rs
- hotspot.rs
- .executeRealTransfer
- chunker.rs
- FrameType
- MockSimTransport
- ChunkPool
- failover_test.rs
- WifiCapsInfo
- theme.rs
- crypto.rs
- AsyncTransport
- mdns.rs
- NavTab
- How ShareDash works
- cli.rs
- lan.rs
- quic_inet.rs
- usb.rs
- Models.kt
- render_pc2pc_view
- UdpDiscoveryManager
- message.rs
- TransferHistoryManager
- Desktop guide
- select_optimal_pc_to_pc_host
- app.js
- Theme.kt
- HotspotManager
- PieceGrid.kt
- TransferHistoryScreen.kt
- ShareDash
- MainActivity
- AndroidSparseFileWriter
- SettingsManager
- MultipathSpeedometer
- AndroidPairingCoordinator
- ChunkState
- BridgeBadge
- BleDiscovery
- .send_file_multipath_loop
- TransportKind
- GuiEngine
- Desktop sending path
- ConnectedScreen.kt
- app.rs
- TransferUiStage
- .run_interactive_loop
- Contributing
- WizardEvent
- NetworkCallback
- Roadmap
- sharedash
- SettingsScreen.kt
- Diagnostic and benchmark scripts
- Security
- print_warn
- Contributor Covenant Code of Conduct
- TransportMetrics
- PULL_REQUEST_TEMPLATE.md
- DiscoveryMethod
- wifi_direct.rs
- TransferViewState
- anyhow
- Color.kt
- result
- TransportKind
- ControlMessageWrapper

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
- `render_home_view()` --calls--> `panel()`  [INFERRED]
  src/gui/home_view.rs → src/gui/widgets.rs

## Import Cycles
- None detected.

## Communities (97 total, 38 thin omitted)

### Community 0 - "api.rs"
Cohesion: 0.07
Nodes (53): ACTIVE_CHUNK_RECEIVERS, ActiveIncomingChunkSession, ADB_CACHE, ADB_CACHE_TTL_SECS, AdbCacheEntry, AppState, cancel_all_active_transfers(), cancel_transfer() (+45 more)

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
Cohesion: 0.19
Nodes (19): render_transfer_view(), banner(), fluid_progress_bar(), log_list(), panel(), phase_header(), piece_map_visualizer(), progress_bar() (+11 more)

### Community 5 - "handshake.rs"
Cohesion: 0.15
Nodes (10): build_local_capabilities(), HandshakeState, Established, Failed, Idle, SynReceived, SynSent, SessionInfo (+2 more)

### Community 6 - "TransferScreen.kt"
Cohesion: 0.14
Nodes (4): ActiveTransferScreen(), TransferCategoryItem(), TransferIdleScreen(), TransferScreen()

### Community 7 - "benchmark_suite.py"
Cohesion: 0.09
Nodes (15): check_adb_state(), install_and_start_app(), perform_stream_transfer(), probe_phone(), run_cmd(), run_suite(), setup_adb(), get_file_sha256() (+7 more)

### Community 9 - "render_wizard_view"
Cohesion: 0.11
Nodes (9): render_home_view(), render_info_view(), action_button(), back_button(), card_button(), channel_badge(), nav_button(), page_heading() (+1 more)

### Community 13 - "manifest_db.rs"
Cohesion: 0.08
Nodes (15): ChunkState, Completed, Corrupt, InFlight, Pending, ManifestDb, test_manifest_db_lifecycle(), TransferRecord (+7 more)

### Community 14 - "cli_widgets.rs"
Cohesion: 0.08
Nodes (23): BleDevice, animated_scan_progress(), BLUE, BOLD, BOLD_CYAN, BOLD_GREEN, BOLD_RED, BOLD_WHITE (+15 more)

### Community 15 - "bluetooth.rs"
Cohesion: 0.13
Nodes (8): COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID, COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID

### Community 16 - "MainActivity.kt"
Cohesion: 0.08
Nodes (5): DeviceIdentity, PostResult, NOT_FOUND, RETRY, SUCCESS

### Community 17 - "TerminalCli"
Cohesion: 0.16
Nodes (6): TerminalCli, draw_spinner_frame(), print_ok(), print_step_result(), print_tree_item(), with_spinner()

### Community 18 - "NeoCard"
Cohesion: 0.09
Nodes (11): ConnectingDialog(), UsbPromptDialog(), WifiDisconnectPromptDialog(), WirelessWarningDialog(), ConnectedScreen(), SendCategoryCard(), HomeScreen(), UsbFirstScreen() (+3 more)

### Community 19 - "sparse_writer.rs"
Cohesion: 0.16
Nodes (4): FileTime, set_file_mtime(), SparseWriter, test_sparse_writer_write_out_of_order()

### Community 20 - "dynamic_scheduler.rs"
Cohesion: 0.12
Nodes (12): MultipathScheduler, TransferEvent, Cancelled, ChunkCompleted, ChunkCorrupt, Completed, Failed, Progress (+4 more)

### Community 21 - "hotspot.rs"
Cohesion: 0.10
Nodes (19): apply_and_connect_profile(), check_pc_wifi_adapter_enabled(), connect_to_phone_hotspot(), create_hotspot(), create_hotspot_with_band(), detect_hotspot_gateway(), detect_usb_tethering_peer(), detect_usb_tethering_peer_detailed() (+11 more)

### Community 22 - ".executeRealTransfer"
Cohesion: 0.24
Nodes (5): ChunkTask, TransportEndpoint, ChunkVisualItem, SchedulerTelemetry, TransportStats

### Community 23 - "chunker.rs"
Cohesion: 0.16
Nodes (8): FileMetadata, AdaptiveChunker, ChunkInfo, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE, MIN_CHUNK_SIZE, test_build_manifest_from_temp_file(), TransferManifest

### Community 24 - "FrameType"
Cohesion: 0.09
Nodes (18): Frame, FrameHeader, FrameType, BENCHMARK_PROBE, BENCHMARK_RESP, CAPABILITIES, CHUNK_ACK, CHUNK_DATA (+10 more)

### Community 26 - "ChunkPool"
Cohesion: 0.11
Nodes (8): ChunkPool, ChunkPoolInner, ChunkStatus, Completed, Corrupted, InFlight, Unassigned, InFlightInfo

### Community 27 - "failover_test.rs"
Cohesion: 0.13
Nodes (6): mock_responder(), test_cryptographic_corruption_rejection_and_recovery(), mock_responder(), test_work_stealing_and_cable_disconnect_failover(), mock_responder(), test_multipath_concurrent_transports()

### Community 28 - "WifiCapsInfo"
Cohesion: 0.15
Nodes (8): BleAdvertiser, WifiCapsInfo, detect_pc_wifi_caps(), HotspotHostChoice, Pc, Phone, select_best_hotspot_host(), select_optimal_hotspot_host()

### Community 29 - "theme.rs"
Cohesion: 0.14
Nodes (23): accent_blue(), accent_cyan(), accent_green(), accent_red(), accent_yellow(), bg_active(), bg_base(), bg_elevated() (+15 more)

### Community 30 - "crypto.rs"
Cohesion: 0.15
Nodes (3): SessionCrypto, test_encryption_roundtrip(), test_hashers()

### Community 32 - "mdns.rs"
Cohesion: 0.13
Nodes (7): CURRENT_APP_VERSION, DiscoveredPeer, DISCOVERY_BROADCAST_PORT, is_version_compatible(), MIN_SUPPORTED_APP_VERSION, PeerBeacon, PeerDiscovery

### Community 33 - "NavTab"
Cohesion: 0.22
Nodes (6): BottomPillBar(), NavTab, HISTORY, HOME, SETTINGS, TRANSFER

### Community 35 - "How ShareDash works"
Cohesion: 0.15
Nodes (13): Binary-protocol library, Component boundaries, Discovery and connection setup, How ShareDash works, HTTP surface, Pairing is distinct from transfer authorization, PC setup, Phone setup (+5 more)

### Community 36 - "cli.rs"
Cohesion: 0.14
Nodes (7): DiscoveredItem, DynamicWorkDispatcher, run_transport_chunk_worker(), stream_upload_channel(), TransferChunkInfo, ChannelProgress, draw_transfer_progress()

### Community 40 - "Models.kt"
Cohesion: 0.21
Nodes (12): ChunkInfo, FileMeta, TransferDirection, RECEIVED, SENT, TransferManifest, TransferRecord, TransferStatus (+4 more)

### Community 41 - "render_pc2pc_view"
Cohesion: 0.20
Nodes (4): DiscoveredDevice, PcToPcViewState, render_pc2pc_view(), connection_steps()

### Community 44 - "message.rs"
Cohesion: 0.18
Nodes (12): BenchmarkProbeMessage, BenchmarkRespMessage, ChunkAckMessage, ChunkRejectMessage, ChunkReqMessage, HelloMessage, HelloRespMessage, ManifestMessage (+4 more)

### Community 46 - "Desktop guide"
Cohesion: 0.18
Nodes (11): ADB connection, Add Wi-Fi, Connect an Android phone, Connect another computer, Current limits, Desktop guide, Navigation, Receive files (+3 more)

### Community 48 - "app.js"
Cohesion: 0.15
Nodes (10): fallbackCopy(), initCopyButtons(), initMultipathCanvas(), bezier(), drawNode(), drawStaticFrame(), render(), initSpeedCalculator() (+2 more)

### Community 50 - "HotspotManager"
Cohesion: 0.07
Nodes (10): Active, Error, HotspotManager, ActionListener, ActionListener, ActionListener, ActionListener, HotspotState (+2 more)

### Community 52 - "TransferHistoryScreen.kt"
Cohesion: 0.09
Nodes (5): CompactTransferRecordRow(), formatFileSize(), getFileIcon(), openTransferredFile(), TransferHistoryScreen()

### Community 53 - "ShareDash"
Cohesion: 0.18
Nodes (11): Android companion, Build and run the desktop app, Capabilities, Development checks, First transfer, License and support, Performance, Repository map (+3 more)

### Community 59 - "ChunkState"
Cohesion: 0.40
Nodes (5): ChunkState, COMPLETED, CORRUPTED, IN_FLIGHT, PENDING

### Community 60 - "BridgeBadge"
Cohesion: 0.43
Nodes (4): BridgeBadge(), RecommendationRow(), SmartBridgeStrip(), TransportRecommendationCard()

### Community 62 - ".send_file_multipath_loop"
Cohesion: 0.32
Nodes (6): get_dir_size(), package_directory_to_zip(), print_channel_summary(), print_fail(), prompt(), prompt_choice()

### Community 63 - "TransportKind"
Cohesion: 0.40
Nodes (5): TransportKind, BLUETOOTH, LAN, USB, WIFI_DIRECT

### Community 64 - "GuiEngine"
Cohesion: 0.05
Nodes (10): Args, GuiChunkInfo, GuiEngine, GuiWorkDispatcher, package_directory_to_zip(), run_gui_chunk_worker(), Args, Server (+2 more)

### Community 65 - "Desktop sending path"
Cohesion: 0.40
Nodes (5): Chunk sizing, Content preparation, Desktop sending path, Dispatch and retries, Progress reporting

### Community 67 - "app.rs"
Cohesion: 0.30
Nodes (5): HardwareMonitorData, HomeStatus, PendingPairData, InfoViewState, InterfaceEntry

### Community 68 - "TransferUiStage"
Cohesion: 0.40
Nodes (5): TransferUiStage, COMPLETED, FAILED, TRANSFERRING, VERIFYING

### Community 70 - "Contributing"
Cohesion: 0.22
Nodes (9): Benchmarking, Choose the correct layer, Code and documentation standards, Contributing, Desktop review, Development setup, Pull requests, Transfer review (+1 more)

### Community 71 - "WizardEvent"
Cohesion: 0.11
Nodes (12): WizardEvent, CapsSynchronized, HotspotCredsTransferred, Log, Phase2Start, Phase3OptimalHost, Phase4HotspotActive, Phase5UsbConfirmed (+4 more)

### Community 73 - "Roadmap"
Cohesion: 0.40
Nodes (5): Desktop usability, Implemented foundations, Longer-term exploration, Reliability and security priorities, Roadmap

### Community 81 - "Diagnostic and benchmark scripts"
Cohesion: 0.40
Nodes (5): Diagnostic and benchmark scripts, Prepare a fixture, Record results consistently, Run a hardware experiment, Run a model

### Community 82 - "Security"
Cohesion: 0.40
Nodes (5): Current implementation, Operating guidance, Reporting a vulnerability, Security, Security work still required

### Community 83 - "print_warn"
Cohesion: 0.50
Nodes (3): print_table(), print_warn(), strip_ansi()

### Community 84 - "Contributor Covenant Code of Conduct"
Cohesion: 0.25
Nodes (7): Attribution, Contributor Covenant Code of Conduct, Enforcement, Enforcement Responsibilities, Our Pledge, Our Standards, Scope

### Community 86 - "TransportMetrics"
Cohesion: 0.14
Nodes (12): AggregateMetrics, ChunkVisualState, SchedulerTelemetry, TransportMetrics, TransportState, Active, Benchmarking, Closed (+4 more)

### Community 87 - "PULL_REQUEST_TEMPLATE.md"
Cohesion: 0.29
Nodes (6): ✅ Contributor Checklist, 📋 Description, 🔗 Related Issue(s), 📸 Screenshots / Logs (if applicable), 🧪 Testing & Verification, 🛠️ Type of Change

### Community 88 - "DiscoveryMethod"
Cohesion: 0.50
Nodes (4): DiscoveryMethod, Ble, HttpProbe, Udp

### Community 89 - "wifi_direct.rs"
Cohesion: 0.16
Nodes (3): detect_wifi_capabilities(), WifiCapabilities, WifiDirectTransport

### Community 91 - "TransferViewState"
Cohesion: 0.22
Nodes (8): ChannelStats, TransferProgressSnapshot, SendModeChoice, Multipath, UsbOnly, WifiOnly, TransferViewState, TransferResultData

### Community 98 - "TransportKind"
Cohesion: 0.25
Nodes (8): TransportKind, Bluetooth, InternetQuic, Lan, MockSim, Usb, WifiDirect, TransportUpdateMessage

## Knowledge Gaps
- **126 isolated node(s):** `sharedash`, `SUCCESS`, `NOT_FOUND`, `RETRY`, `Idle` (+121 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 607 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **38 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AndroidHttpServer` connect `AndroidHttpServer` to `AndroidHttpServer.kt`, `MainActivity`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **What connects `sharedash`, `SUCCESS`, `NOT_FOUND` to the rest of the system?**
  _126 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `api.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.07496194824961948 - nodes in this community are weakly interconnected._
- **Why does `MockSimTransport` connect `MockSimTransport` to `api.rs`, `lan.rs`, `TransportMetrics`, `failover_test.rs`, `AsyncTransport`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Should `frame.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.0519774011299435 - nodes in this community are weakly interconnected._
- **Why does `MainActivity` connect `MainActivity` to `UdpDiscoveryManager`, `TransferForegroundService`, `TransferHistoryManager`, `MainActivity.kt`, `HotspotManager`, `.executeRealTransfer`, `SettingsManager`, `FrameType`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Should `HomeScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09210526315789473 - nodes in this community are weakly interconnected._