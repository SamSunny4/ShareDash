# Prompt: Build a Multipath Local + Internet File Transfer System

You are an expert systems/networking engineer and software architect. Design and implement a cross-platform file-transfer application that maximizes real-world transfer speed between devices by automatically discovering, benchmarking, selecting, and combining every useful transport available.

## Core Goal

Build an application for phone ↔ laptop/desktop file transfer with these principles:

- No cloud dependency for local transfers.
- Use Bluetooth and/or USB primarily for discovery, control, capability exchange, and connection setup when appropriate.
- Use the fastest available payload transports.
- Prefer direct device-to-device Wi-Fi when it is genuinely faster than the existing LAN/Wi-Fi path.
- Combine multiple simultaneous transports when doing so increases aggregate throughput.
- Split files into independently transferable chunks, similar in concept to BitTorrent pieces.
- Dynamically schedule chunks across transports according to measured throughput, latency, reliability, and congestion.
- Support resumable transfers without restarting the entire file.
- Verify every transferred chunk cryptographically.
- Support both local transfers and Internet transfers using the same high-level transfer protocol.
- Never assume that a nominal technology label such as “Wi-Fi 6” is faster; benchmark the actual available path.

## Initial Target

Prioritize:

1. Android phone ↔ Windows laptop/desktop.
2. Local network and direct Wi-Fi transfers.
3. USB transfers.
4. Internet transfers between two devices when they are not on the same LAN.

Design the architecture so macOS, Linux, iOS, and additional device types can be added later.

## High-Level Architecture

Use a layered design:

```text
Application/UI
      ↓
Transfer Controller
      ↓
Chunk Manager / Scheduler
      ↓
Transport Abstraction Layer
      ↓
USB | Wi-Fi Direct/Hotspot | Existing LAN | Ethernet | Internet/QUIC | Bluetooth fallback
```

The transfer scheduler must not depend on a single transport.

## Device Discovery

Use appropriate mechanisms such as:

- Bluetooth discovery.
- USB connection detection.
- mDNS/Bonjour/local service discovery where applicable.
- Wi-Fi Direct or hotspot discovery/negotiation where supported.
- QR code or short pairing code for user-approved pairing.
- A lightweight signaling service for Internet mode.

Bluetooth should not be used for large-file payload transfer when a faster path is available. Its main roles should include nearby discovery, pairing, capability exchange, and fallback control/data when necessary.

## Capability Exchange

After discovery, devices should exchange capabilities such as:

- Device identifier.
- OS and application version.
- Supported transports.
- USB generation/mode if detectable.
- Wi-Fi generation/capabilities.
- Supported frequency bands.
- Channel width where detectable.
- MIMO/spatial-stream capability where detectable.
- Wi-Fi Direct/hotspot support.
- IPv4/IPv6 support.
- Ethernet availability.
- QUIC/HTTP3 support.
- Maximum concurrent streams.
- Available storage.
- Battery/power state where relevant.

Do not make transport selection from capability metadata alone. Capability information only determines candidate paths.

## Transport Selection

Generate candidate paths, then benchmark them.

Example:

```text
Existing LAN:        650 Mbps
Direct Wi-Fi:        420 Mbps
USB:                 3.2 Gbps
Bluetooth:           2 Mbps
```

The system should select USB + LAN rather than blindly selecting direct Wi-Fi because it is newer.

Benchmark using short, safe probes and then continue measuring during the real transfer.

Relevant metrics:

- Sustained throughput.
- RTT/latency.
- Packet loss/retransmissions.
- Connection establishment time.
- Stability.
- CPU cost.
- Thermal throttling if detectable.
- Battery impact where applicable.

## Direct Wi-Fi Strategy

When both devices support an appropriate direct Wi-Fi mechanism:

1. Detect whether direct mode is possible.
2. Establish it if allowed by the platform.
3. Benchmark it against the existing LAN/Wi-Fi connection.
4. Keep it only if it provides a real benefit.
5. Tear it down cleanly when no longer needed.

Do not assume that Wi-Fi 6/6E automatically means higher actual throughput than an existing router connection.

Prefer actual measured performance.

## Multipath Transfer

This is a core feature.

A file must be divided into chunks/pieces:

```text
File
 ├─ Chunk 0
 ├─ Chunk 1
 ├─ Chunk 2
 ├─ Chunk 3
 ├─ ...
 └─ Chunk N
```

Each transport independently requests/receives chunks:

```text
USB   → Chunk 0
Wi-Fi → Chunk 1
USB   → Chunk 2
Wi-Fi → Chunk 3
USB   → Chunk 4
```

The scheduler must dynamically adjust allocation.

Do NOT permanently assign a fixed percentage of the file to each transport.

Example:

```text
USB = 400 MB/s
Wi-Fi = 100 MB/s
```

The scheduler should naturally give USB more work while still using Wi-Fi if aggregate throughput improves.

## Work Stealing / Dynamic Scheduling

Use a shared pool of uncompleted chunks.

If one transport becomes slow, other transports should continue taking available chunks.

Example:

```text
USB finishes Chunk 12 early.
Wi-Fi is still processing Chunk 13.
USB should request Chunk 14 instead of waiting.
```

Avoid head-of-line blocking at the application chunk scheduler.

## Chunk Size

Choose chunk sizes adaptively or expose a configurable value.

Consider values such as 4 MB, 8 MB, 16 MB, 32 MB, or larger depending on:

- File size.
- Transport speed.
- Device RAM.
- Network conditions.
- Number of transports.

Avoid creating millions of tiny chunks for large files.

## Integrity

Every chunk must have integrity metadata.

Example:

```text
Chunk ID
Offset
Length
SHA-256
```

The receiver must validate each chunk before marking it complete.

For large files, optionally support a Merkle-tree/root hash so the final file can be verified efficiently.

A corrupted chunk must be re-requested automatically.

## Resume Support

Transfers must survive:

- Application restart.
- Temporary network loss.
- USB disconnect/reconnect.
- Wi-Fi disconnect/reconnect.
- Device sleep where possible.
- Crash.

Maintain a transfer manifest that records completed chunks.

Example:

```text
Chunk 0 ✓
Chunk 1 ✓
Chunk 2 ✗
Chunk 3 ✓
Chunk 4 ✓
```

On reconnect, transfer only missing or invalid chunks.

## Internet Mode

The same application must support remote transfers when devices are on different networks.

Prefer:

```text
Phone ←──── direct P2P Internet path ────→ Laptop
```

over:

```text
Phone → relay server → Laptop
```

Use a lightweight signaling/rendezvous service to help peers discover each other.

For NAT traversal, evaluate mechanisms such as:

- IPv6 direct connectivity.
- UDP hole punching.
- STUN.
- ICE-style candidate negotiation.
- TURN/relay as a final fallback.

Do not send large files through the relay unless P2P connectivity is impossible or the user explicitly selects relay mode.

## Internet Transport

Prefer QUIC/HTTP3 or another modern encrypted transport for Internet mode.

Use independent streams or logical chunk transfers so that multiple chunks can be in flight without unnecessary head-of-line blocking.

The local transfer protocol and Internet transfer protocol should share the same chunk/scheduler model even if their underlying transports differ.

## Security

Security is mandatory.

Requirements:

- Explicit user authorization before accepting a transfer.
- Pairing between devices.
- End-to-end encryption for payloads.
- Authentication of peers.
- Prevent unauthorized devices from discovering or accessing transfers.
- Reject malformed chunk metadata.
- Protect against path traversal when receiving files/folders.
- Sanitize filenames.
- Never execute received files automatically.
- Use authenticated encryption such as AES-GCM or ChaCha20-Poly1305 where appropriate.
- Use TLS/QUIC security for Internet transports.
- Do not log sensitive file contents.

## Folder Transfer

Support folders by representing them as a manifest:

```text
folder/
 ├─ file1.ext
 ├─ file2.ext
 └─ subfolder/
     └─ file3.ext
```

Transfer metadata and files reliably while preserving:

- Names.
- Relative paths.
- File sizes.
- Modification timestamps where supported.

## User Experience

The main UI should clearly show:

```text
Transfer: movie.mkv
Size: 24.6 GB

USB       382 MB/s
Wi-Fi      91 MB/s
Internet   --

Aggregate 473 MB/s

[█████████████████░░░]
18.9 / 24.6 GB

ETA: 12 sec
```

Also show active transports and allow the user to disable a transport manually.

The UI should never require technical knowledge from the user for normal operation.

Provide an advanced diagnostics screen for developers/power users.

## Adaptive Scheduler

Design a scheduler that considers:

- Current throughput.
- Recent throughput trend.
- RTT.
- Error rate.
- Retransmissions.
- Connection availability.
- Queue depth.
- Chunk completion time.

The scheduler should continuously rebalance work.

Possible strategy:

1. Start several probe chunks.
2. Measure completion rates per transport.
3. Estimate transport capacity.
4. Assign additional work proportionally.
5. Detect degradation.
6. Reduce work assigned to unstable paths.
7. Increase work assigned to recovered paths.
8. Always keep enough chunks in flight to utilize available capacity.

Avoid aggressive oscillation between transports.

## Example Scenarios

### Scenario A: USB + Wi-Fi

```text
USB:   400 MB/s
Wi-Fi: 100 MB/s
```

Use both if the measured aggregate is actually higher than USB alone.

### Scenario B: Direct Wi-Fi is faster

```text
Existing Wi-Fi: 150 Mbps
Direct Wi-Fi:   1.1 Gbps
USB:            unavailable
```

Use direct Wi-Fi.

### Scenario C: Existing LAN is faster

```text
Existing LAN: 650 Mbps
Direct Wi-Fi: 420 Mbps
USB:          unavailable
```

Use existing LAN.

### Scenario D: Internet transfer

```text
Phone: mobile network
Laptop: home broadband
```

Use P2P Internet connectivity if possible; otherwise use an encrypted relay.

### Scenario E: Connection disappears

```text
USB: 350 MB/s
Wi-Fi: 80 MB/s
```

USB disconnects.

The scheduler should immediately continue using Wi-Fi without restarting the transfer.

### Scenario F: USB returns

USB reconnects and benchmarks at 330 MB/s.

The scheduler should automatically add USB back into the transport pool.

## Protocol Design

Create a clear application-layer protocol with message types such as:

```text
HELLO
CAPABILITIES
PAIR_REQUEST
PAIR_ACCEPT
TRANSFER_OFFER
TRANSFER_ACCEPT
MANIFEST
CHUNK_REQUEST
CHUNK_DATA
CHUNK_ACK
CHUNK_REJECT
TRANSPORT_ADD
TRANSPORT_REMOVE
CHECKPOINT
TRANSFER_PAUSE
TRANSFER_RESUME
TRANSFER_COMPLETE
TRANSFER_CANCEL
ERROR
```

Separate control messages from bulk data paths.

## Architecture Requirements

Use clean interfaces, for example:

```text
Transport
 ├─ connect()
 ├─ disconnect()
 ├─ benchmark()
 ├─ send()
 ├─ receive()
 ├─ getMetrics()
 └─ isAvailable()
```

Possible implementations:

```text
UsbTransport
LanTransport
WifiDirectTransport
BluetoothTransport
QuicTransport
RelayTransport
```

And:

```text
TransferController
ChunkManager
ChunkScheduler
IntegrityManager
ResumeManager
PeerManager
CapabilityManager
```

Keep transport-specific code isolated from file/chunk scheduling logic.

## Technology Selection

For Android, prefer Kotlin and official Android networking/USB APIs.

For Windows, choose a robust systems-capable stack such as Rust, C#, or another appropriate language. Explain the choice.

For shared protocol components, consider Rust if it materially improves correctness, performance, or cross-platform reuse.

Do not choose technologies merely because they are fashionable.

## Development Strategy

Implement incrementally:

### Phase 1
Android ↔ Windows over LAN with chunking, integrity, resume, and benchmarking.

### Phase 2
USB transport.

### Phase 3
Combine USB + LAN/Wi-Fi using the multipath scheduler.

### Phase 4
Bluetooth discovery/pairing.

### Phase 5
Direct Wi-Fi/hotspot/Wi-Fi Direct where platform APIs permit.

### Phase 6
Internet P2P with signaling + NAT traversal + QUIC.

### Phase 7
Relay fallback.

### Phase 8
Performance tuning, diagnostics, power/thermal optimization, and additional platforms.

## Important Constraints

Do not promise impossible performance.

The application should report:

- Raw transport throughput.
- Effective application throughput.
- Aggregate throughput.
- CPU usage.
- Errors/retries.
- Time spent connecting.

Measure real performance rather than relying on theoretical interface speeds.

Do not force every available transport into the transfer. A transport should only participate when it provides a net benefit.

Avoid excessive memory usage by streaming chunks rather than loading entire files into RAM.

Use backpressure so fast transports do not overwhelm the receiver.

## Testing

Create a serious benchmark suite.

Test:

- Small files.
- Large files.
- Thousands of files.
- Single transport.
- Multi-transport.
- Slow/fast Wi-Fi combinations.
- USB reconnect.
- Wi-Fi loss.
- Packet loss.
- High latency.
- NAT types.
- Internet P2P failure.
- Relay fallback.
- Interrupted transfers.
- Corrupted chunks.
- Device sleep/restart.
- Thermal throttling.
- Different storage speeds.

Compare:

```text
USB only
Wi-Fi only
USB + Wi-Fi
Direct Wi-Fi only
LAN only
Internet P2P
Internet relay
```

## Deliverables

Produce:

1. System architecture.
2. Protocol specification.
3. Transport abstraction interfaces.
4. Scheduler design.
5. Security model.
6. Android implementation plan.
7. Windows implementation plan.
8. Internet connectivity design.
9. Database/manifest format for resumable transfers.
10. Benchmark methodology.
11. MVP implementation.
12. Test plan.
13. Performance optimization roadmap.

When generating code, provide production-quality code rather than toy snippets. Clearly separate platform-specific code from shared protocol logic.

## Key Design Principle

The central idea is:

> **Treat USB, Wi-Fi, LAN, and Internet as interchangeable transport paths. Discovery and control determine which paths exist; benchmarking determines which are useful; a dynamic chunk scheduler distributes work across all useful paths; integrity and resumability make the transfer reliable.**

Do not build a simple file-sharing application. Build a **multipath adaptive transfer engine** that can exploit multiple physical/network interfaces at the same time while remaining reliable, secure, and cross-platform.
