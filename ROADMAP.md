# 🗺️ ShareDash Product & Engineering Roadmap

This document outlines the architectural vision, upcoming milestones, and planned features for **ShareDash**.

---

## 🎯 Current Status (v0.1.0)
- [x] **Zero-LAN / Zero-Internet Physical Engine**: Fully offline P2P bridging.
- [x] **Smart 5 GHz Hotspot Orchestration**: Automatic band-freeing, SoftAP creation, and credential exchange over BLE.
- [x] **Multipath Physical Aggregation**: Simultaneous USB 3.x Cable Fast-Path + 5 GHz Wi-Fi link streaming (78+ MB/s real-world throughput).
- [x] **Adaptive Chunker & Sparse Writer**: 2MB–64MB adaptive chunks with concurrent `FileChannel` reassembly.
- [x] **Zero-Corruption Verification**: CRC32 frame checksums, SHA-256 / BLAKE3 hash checks, AES-256-GCM encryption.
- [x] **Native Android Companion**: Jetpack Compose UI, BLE GATT server, Android Share Sheet target.
- [x] **Windows CLI Wizard**: Interactive terminal UI with real-time gauges, hardware inspect, and pin handshakes.

---

## 🚀 Near-Term Milestones (v0.2.0 - v0.3.0)

### 🖥️ Native Cross-Platform PC Clients
- [ ] **Linux Support**: CLI engine support for Linux network interfaces via `NetworkManager` / `wpa_supplicant` and Bluetooth (`bluez`).
- [ ] **macOS Support**: Core engine support for macOS via CoreBluetooth and Wi-Fi bridging.
- [ ] **Desktop GUI**: Lightweight Fluent Design GUI for Windows built with Tauri / Slint / egui alongside the CLI wizard.

### 📱 Android Enhancements
- [ ] **NFC Quick Tap-to-Pair**: Tap PC NFC reader or phone-to-phone for instant BLE session bonding.
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
