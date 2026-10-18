# Graph Report - ShareDash  (2026-10-18)

## Corpus Check
- 106 files · ~96,151 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 19 file(s) not represented in the graph (top: .xml 8, (none) 3, .properties 2)

## Summary
- 1591 nodes · 3799 edges · 80 communities (38 shown, 42 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 45 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- BLE Discovery & Advertising
- Settings & Diagnostics UI
- BLE Discovery & Advertising
- BLE Discovery & Advertising
- Bluetooth RFCOMM Transport
- Wi-Fi Hotspot & Direct P2P
- Multipath Dynamic Scheduler
- Settings & Diagnostics UI
- Settings & Diagnostics UI
- Transfer Foreground Service
- Chunking & Transfer Engine
- Chunking & Transfer Engine
- CLI Terminal UI & Widgets
- Bluetooth RFCOMM Transport
- Android App Shell & Pairing UI
- Settings & Diagnostics UI
- Compose UI Components & Dialogs
- Chunking & Transfer Engine
- Multipath Dynamic Scheduler
- Wi-Fi Hotspot & Direct P2P
- Chunking & Transfer Engine
- Multipath Dynamic Scheduler
- Chunking & Transfer Engine
- Chunking & Transfer Engine
- Chunking & Transfer Engine
- Multipath Dynamic Scheduler
- Androidpairingcoordinator.Kt / Androidpairingcoordinator / .Confirmpairsession()
- Benchmarking & Performance Suite
- Aead / Aes256Gcm / Digest
- Frame Protocol & Serialization
- Ordering / Semaphore / Mdns.Rs
- Chunking & Transfer Engine
- Wi-Fi Hotspot & Direct P2P
- Chunking & Transfer Engine
- Benchmarking & Performance Suite
- Wi-Fi Hotspot & Direct P2P
- Benchmarking & Performance Suite
- Bluetooth RFCOMM Transport
- Benchmarking & Performance Suite
- Context / Coroutinescope / Discoveredpeer
- Multipath Dynamic Scheduler
- Transferhistorymanager.Kt / Context / Sharedpreferences
- Protocol.Kt / Bytebuffer / Byteorder
- CLI Terminal UI & Widgets
- Multipath Dynamic Scheduler
- Theme, Styling & Design System
- Frame Protocol & Serialization
- Multipath Dynamic Scheduler
- Compacttransferrecordrow() / Formatfilesize() / Getfileicon()
- Bytesmut / Item / .Decode()
- Chunking & Transfer Engine
- Settings & Diagnostics UI
- Settings & Diagnostics UI
- Chunking & Transfer Engine
- Benchmarking & Performance Suite
- Theme, Styling & Design System
- CLI Terminal UI & Widgets
- Multipath Dynamic Scheduler
- Benchmarking & Performance Suite
- Androidtransportmanager / .Closeall() / .Connecttransport()
- Wi-Fi Hotspot & Direct P2P
- Chunking & Transfer Engine
- Multipath Dynamic Scheduler
- Transferuistage / Completed / Failed
- Theme, Styling & Design System
- Wi-Fi Hotspot & Direct P2P
- Wi-Fi Hotspot & Direct P2P
- Android / Networkcallback / .Onavailable()
- Postresult / Not_Found / Retry
- Sharedash

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

## Communities (80 total, 42 thin omitted)

### Community 0 - "BLE Discovery & Advertising"
Cohesion: 0.05
Nodes (55): PairingManager, PairingSession, ACTIVE_CHUNK_RECEIVERS, ActiveIncomingChunkSession, ADB_CACHE, ADB_CACHE_TTL_SECS, AdbCacheEntry, AppState (+47 more)

### Community 4 - "BLE Discovery & Advertising"
Cohesion: 0.07
Nodes (14): COMMAND_CHAR_UUID, RESPONSE_CHAR_UUID, SHAREDASH_BLE_SERVICE_UUID, WIFI_CAPS_CHAR_UUID, BleAdvertiser, BleDiscovery, COMMAND_CHAR_UUID, DiscoveryMethod (+6 more)

### Community 5 - "Bluetooth RFCOMM Transport"
Cohesion: 0.05
Nodes (25): BenchmarkProbeMessage, BenchmarkRespMessage, ChunkAckMessage, ChunkRejectMessage, ChunkReqMessage, ControlMessageWrapper, FileMetadata, HelloMessage (+17 more)

### Community 6 - "Wi-Fi Hotspot & Direct P2P"
Cohesion: 0.08
Nodes (10): Active, Error, HotspotManager, ActionListener, ActionListener, ActionListener, ActionListener, HotspotState (+2 more)

### Community 7 - "Multipath Dynamic Scheduler"
Cohesion: 0.10
Nodes (15): check_adb_state(), install_and_start_app(), perform_stream_transfer(), probe_phone(), run_cmd(), run_suite(), setup_adb(), get_file_sha256() (+7 more)

### Community 13 - "Chunking & Transfer Engine"
Cohesion: 0.08
Nodes (15): ChunkState, Completed, Corrupt, InFlight, Pending, ManifestDb, test_manifest_db_lifecycle(), TransferRecord (+7 more)

### Community 14 - "CLI Terminal UI & Widgets"
Cohesion: 0.08
Nodes (25): animated_scan_progress(), BLUE, BOLD, BOLD_CYAN, BOLD_GREEN, BOLD_RED, BOLD_WHITE, BOLD_YELLOW (+17 more)

### Community 17 - "Settings & Diagnostics UI"
Cohesion: 0.13
Nodes (3): DiscoveredPeer, ConnectedScreen(), SendCategoryCard()

### Community 18 - "Compose UI Components & Dialogs"
Cohesion: 0.15
Nodes (10): ConnectingDialog(), UsbPromptDialog(), WifiDisconnectPromptDialog(), WirelessWarningDialog(), DiscoveryScreen(), HomeScreen(), UsbFirstScreen(), NeoButton() (+2 more)

### Community 19 - "Chunking & Transfer Engine"
Cohesion: 0.11
Nodes (8): AdaptiveChunker, ChunkInfo, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE, MIN_CHUNK_SIZE, test_build_manifest_from_temp_file(), TransferManifest, IntegrityVerifier

### Community 20 - "Multipath Dynamic Scheduler"
Cohesion: 0.12
Nodes (12): MultipathScheduler, TransferEvent, Cancelled, ChunkCompleted, ChunkCorrupt, Completed, Failed, Progress (+4 more)

### Community 21 - "Wi-Fi Hotspot & Direct P2P"
Cohesion: 0.11
Nodes (14): apply_and_connect_profile(), check_pc_wifi_adapter_enabled(), connect_to_phone_hotspot(), detect_usb_tethering_peer(), detect_usb_tethering_peer_detailed(), DirectPcPeer, ensure_pc_wifi_adapter_enabled(), fast_scan_hotspot_clients() (+6 more)

### Community 22 - "Chunking & Transfer Engine"
Cohesion: 0.08
Nodes (25): FrameType, BenchmarkProbe, BenchmarkResp, Capabilities, ChunkAck, ChunkData, ChunkReject, ChunkReq (+17 more)

### Community 23 - "Multipath Dynamic Scheduler"
Cohesion: 0.14
Nodes (6): LegendChip(), PieceVisualizerGrid(), ActiveTransferScreen(), TransferCategoryItem(), TransferIdleScreen(), TransferScreen()

### Community 24 - "Chunking & Transfer Engine"
Cohesion: 0.12
Nodes (17): Frame, FrameHeader, FrameType, BENCHMARK_PROBE, BENCHMARK_RESP, CAPABILITIES, CHUNK_ACK, CHUNK_DATA (+9 more)

### Community 26 - "Chunking & Transfer Engine"
Cohesion: 0.12
Nodes (8): ChunkPool, ChunkPoolInner, ChunkStatus, Completed, Corrupted, InFlight, Unassigned, InFlightInfo

### Community 27 - "Multipath Dynamic Scheduler"
Cohesion: 0.16
Nodes (6): mock_responder(), test_cryptographic_corruption_rejection_and_recovery(), mock_responder(), test_work_stealing_and_cable_disconnect_failover(), mock_responder(), test_multipath_concurrent_transports()

### Community 30 - "Aead / Aes256Gcm / Digest"
Cohesion: 0.14
Nodes (3): SessionCrypto, test_encryption_roundtrip(), test_hashers()

### Community 31 - "Frame Protocol & Serialization"
Cohesion: 0.13
Nodes (10): build_local_capabilities(), HandshakeState, Established, Failed, Idle, SynReceived, SynSent, SessionInfo (+2 more)

### Community 32 - "Ordering / Semaphore / Mdns.Rs"
Cohesion: 0.12
Nodes (7): CURRENT_APP_VERSION, DiscoveredPeer, DISCOVERY_BROADCAST_PORT, is_version_compatible(), MIN_SUPPORTED_APP_VERSION, PeerBeacon, PeerDiscovery

### Community 33 - "Chunking & Transfer Engine"
Cohesion: 0.16
Nodes (4): FileTime, set_file_mtime(), SparseWriter, test_sparse_writer_write_out_of_order()

### Community 35 - "Wi-Fi Hotspot & Direct P2P"
Cohesion: 0.19
Nodes (6): draw_spinner_frame(), print_ok(), print_phase_header(), print_step_result(), print_tree_item(), with_spinner()

### Community 36 - "Chunking & Transfer Engine"
Cohesion: 0.18
Nodes (7): DynamicWorkDispatcher, run_transport_chunk_worker(), stream_upload_channel(), TransferChunkInfo, ChannelProgress, draw_transfer_progress(), init_transfer_progress()

### Community 40 - "Bluetooth RFCOMM Transport"
Cohesion: 0.14
Nodes (17): ChunkInfo, FileMeta, TransferDirection, RECEIVED, SENT, TransferManifest, TransferRecord, TransferStatus (+9 more)

### Community 44 - "Multipath Dynamic Scheduler"
Cohesion: 0.26
Nodes (5): ChunkTask, TransportEndpoint, ChunkVisualItem, SchedulerTelemetry, TransportStats

### Community 48 - "Multipath Dynamic Scheduler"
Cohesion: 0.16
Nodes (6): initMultipathCanvas(), drawDeviceNode(), render(), initSpeedCalculator(), formatTime(), updateCalculations()

### Community 50 - "Frame Protocol & Serialization"
Cohesion: 0.15
Nodes (9): FLAG_BENCHMARK, FLAG_COMPRESSED, FLAG_ENCRYPTED, FLAG_NONE, FLAG_PRIORITY, FRAME_HEADER_LEN, MAGIC_BYTES, MAX_PAYLOAD_SIZE (+1 more)

### Community 51 - "Multipath Dynamic Scheduler"
Cohesion: 0.31
Nodes (4): AggregateMetrics, ChunkVisualState, SchedulerTelemetry, TransportMetrics

### Community 52 - "Compacttransferrecordrow() / Formatfilesize() / Getfileicon()"
Cohesion: 0.31
Nodes (5): CompactTransferRecordRow(), formatFileSize(), getFileIcon(), openTransferredFile(), TransferHistoryScreen()

### Community 57 - "Settings & Diagnostics UI"
Cohesion: 0.22
Nodes (6): BottomPillBar(), NavTab, HISTORY, HOME, SETTINGS, TRANSFER

### Community 60 - "Theme, Styling & Design System"
Cohesion: 0.43
Nodes (4): BridgeBadge(), RecommendationRow(), SmartBridgeStrip(), TransportRecommendationCard()

### Community 62 - "Multipath Dynamic Scheduler"
Cohesion: 0.32
Nodes (6): get_dir_size(), package_directory_to_zip(), print_channel_summary(), print_fail(), prompt(), prompt_choice()

### Community 63 - "Benchmarking & Performance Suite"
Cohesion: 0.25
Nodes (8): TransportState, Active, Benchmarking, Closed, Connected, Connecting, Degraded, Disconnected

### Community 65 - "Wi-Fi Hotspot & Direct P2P"
Cohesion: 0.33
Nodes (7): WifiCapsInfo, detect_pc_wifi_caps(), HotspotHostChoice, Pc, Phone, select_best_hotspot_host(), select_optimal_hotspot_host()

### Community 66 - "Chunking & Transfer Engine"
Cohesion: 0.40
Nodes (5): ChunkState, COMPLETED, CORRUPTED, IN_FLIGHT, PENDING

### Community 68 - "Transferuistage / Completed / Failed"
Cohesion: 0.40
Nodes (5): TransferUiStage, COMPLETED, FAILED, TRANSFERRING, VERIFYING

### Community 70 - "Wi-Fi Hotspot & Direct P2P"
Cohesion: 0.60
Nodes (5): create_hotspot(), create_hotspot_with_band(), detect_hotspot_gateway(), get_hotspot_status(), HotspotInfo

### Community 73 - "Postresult / Not_Found / Retry"
Cohesion: 0.50
Nodes (4): PostResult, NOT_FOUND, RETRY, SUCCESS

## Knowledge Gaps
- **110 isolated node(s):** `sharedash`, `SUCCESS`, `NOT_FOUND`, `RETRY`, `Idle` (+105 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 555 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **42 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HotspotManager` connect `Wi-Fi Hotspot & Direct P2P` to `Android App Shell & Pairing UI`, `Settings & Diagnostics UI`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **What connects `sharedash`, `SUCCESS`, `NOT_FOUND` to the rest of the system?**
  _110 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `BLE Discovery & Advertising` be split into smaller, more focused modules?**
  _Cohesion score 0.05173572228443449 - nodes in this community are weakly interconnected._
- **Why does `MockSimTransport` connect `Chunking & Transfer Engine` to `BLE Discovery & Advertising`, `Benchmarking & Performance Suite`, `Multipath Dynamic Scheduler`, `Multipath Dynamic Scheduler`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **Should `Settings & Diagnostics UI` be split into smaller, more focused modules?**
  _Cohesion score 0.08771929824561403 - nodes in this community are weakly interconnected._
- **Why does `MainActivity` connect `Settings & Diagnostics UI` to `Androidtransportmanager / .Closeall() / .Connecttransport()`, `BLE Discovery & Advertising`, `Wi-Fi Hotspot & Direct P2P`, `Context / Coroutinescope / Discoveredpeer`, `Transfer Foreground Service`, `Multipath Dynamic Scheduler`, `Transferhistorymanager.Kt / Context / Sharedpreferences`, `Android App Shell & Pairing UI`, `Settings & Diagnostics UI`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Should `Android App Shell & Pairing UI` be split into smaller, more focused modules?**
  _Cohesion score 0.11904761904761904 - nodes in this community are weakly interconnected._