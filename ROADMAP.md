# 🗺️ ShareDash Product & Engineering Roadmap

This document outlines the architectural vision, upcoming milestones, and planned features for **ShareDash**.

---

## 🎯 Current Status (v0.1.0)
- [x] **Zero-LAN / Zero-Internet Physical Engine**: Fully offline P2P bridging.
- [x] **Smart 5 GHz Hotspot Orchestration**: Automatic band-freeing, SoftAP creation, and direct Wi-Fi Direct credential exchange.
- [x] **Multipath Physical Aggregation**: Simultaneous USB 3.x Cable Fast-Path + 5 GHz Wi-Fi link streaming (78+ MB/s real-world throughput).
- [x] **Adaptive Chunker & Sparse Writer**: 2MB–64MB adaptive chunks with concurrent `FileChannel` reassembly.
- [x] **Zero-Corruption Verification**: CRC32 frame checksums, SHA-256 / BLAKE3 hash checks, AES-256-GCM encryption.
- [x] **Native Android Companion**: Jetpack Compose UI, Zero-LAN UDP discovery, Android Share Sheet target.
- [x] **Native Windows GPU Desktop App**: GPU-accelerated Rust GPUI desktop application with real-time HUD, AirDrop-style radar scan, 5-phase interactive auto-connect wizard, piece grid, and one-click transfers.
- [x] **Windows CLI Wizard**: Interactive terminal UI with real-time gauges, hardware inspect, and pin handshakes.

---

## 🚀 Near-Term Milestones (v0.2.0 - v0.3.0)

### 🖥️ Native Cross-Platform PC Clients
- [ ] **Linux Support**: CLI engine support for Linux network interfaces via `NetworkManager` / `wpa_supplicant` and Wi-Fi Direct.
- [ ] **macOS Support**: Core engine support for macOS via native Wi-Fi Direct bridging and USB Fast-Path.
- [ ] **Tray Minimization & Auto-Start**: Background tray daemon with instant pop-up notifications on file arrival.

### 📱 Android Enhancements
- [ ] **NFC Quick Tap-to-Pair**: Tap PC NFC reader or phone-to-phone for instant Wi-Fi Direct session bonding.
- [ ] **Folder & Batch Directory Tree Transfers**: Recursive directory preservation with streaming archive packing.
- [ ] **Multi-File Queue Management**: Pause, resume, re-order, and background batch processing.

---

## 🔮 Future Vision (v1.0.0+)

### 🌐 Universal Fallbacks
- [ ] **WebRTC Browser Receiver (Zero-Install)**: Instant temporary web-portal receiver for devices without native apps installed.
- [ ] **Local LAN Multi-Stream Bridge**: Aggregating gigabit Ethernet + Wi-Fi 6 on local office / home networks.

### ⚡ Protocol & Transfer Optimizations
- [ ] **QUIC Transport Layer Option**: Multi-stream multiplexing with native congestion control over UDP.
- [ ] **Multi-Device Mesh Broadcast**: Simultaneously stream a single file to 3+ phones / laptops over coordinated Wi-Fi Direct groups.

---

## 💡 Have an idea?
We'd love to hear your feedback! Open a [Feature Request](https://github.com/SamSunny4/ShareDash/issues/new?template=feature_request.yml) or start a conversation in [Discussions](https://github.com/SamSunny4/ShareDash/discussions).
