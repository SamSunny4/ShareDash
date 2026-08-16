# ⚡ ShareDash

> **Next-Generation Multipath File Transfer System for Windows & Android**  
> Aggregate **USB 3.x Cable Fast-Path**, **Wi-Fi Direct P2P**, **Local Wi-Fi/LAN**, and **Internet QUIC** simultaneously into a single ultra-high-speed transfer pipeline with Quick Share radar discovery and cryptographic piece verification.

---

## 🚀 Key Features

| Feature | Description |
| :--- | :--- |
| 📡 **Quick Share Radar Discovery** | Live pulsing concentric wave detecting nearby Android phones and Windows PCs over **Bluetooth Low Energy (BLE)** & UDP beacon. |
| 🔌 **Smart Connection Bridges** | Dynamically detects and aggregates multiple physical connections (USB-C cable, Wi-Fi 6, Gigabit Ethernet) for **combined multi-gigabit throughput**. |
| 🏎️ **Dynamic Work-Stealing Scheduler** | Dynamically adapts in-flight chunk windows based on real-time EWMA bandwidth and steals straggling chunks from slower paths. |
| 🛡️ **Zero-Corruption Verification** | CRC32 binary framing, SHA-256 / BLAKE3 chunk hashing, AES-256-GCM encryption, and direct sparse-offset disk writes. |
| 📱 **Native Android Companion App** | Kotlin + Jetpack Compose app with Android System Share Sheet target (`ACTION_SEND`), background foreground service, and Bluetooth LE advertising. |
| 🌐 **Universal Web Portal** | Instant zero-install browser portal with QR code scanner and mobile upload for any phone or laptop. |

---

## 🏗️ Architecture Overview

```mermaid
graph TD
    subgraph UI ["User Interfaces"]
        WinUI["Quick Share Windows App (Fluent Mica UI)"]
        AndUI["ShareDash Android App (Jetpack Compose)"]
        WebPortal["Universal Web Portal (Mobile Browser)"]
    end

    subgraph Core ["ShareDash Multipath Core Engine"]
        Sched["Work-Stealing Dynamic Scheduler"]
        Pool["Thread-Safe In-Flight ChunkPool"]
        DB["SQLite WAL Manifest Database"]
        Writer["Direct-Offset Sparse File Writer"]
        Verif["SHA-256 / BLAKE3 Integrity Verifier"]
    end

    subgraph Transports ["Physical Multipath Channels"]
        USB["⚡ USB 3.x Cable Fast-Path (Up to 3.2 Gbps)"]
        P2P["📶 Wi-Fi Direct 5GHz / 6GHz P2P (Up to 1.4 Gbps)"]
        LAN["🏠 Local Area Network Multi-Stream TCP (650 Mbps)"]
        QUIC["🌐 Internet Remote QUIC (P2P Hole-Punching)"]
        BLE["🔵 Bluetooth Low Energy (Discovery & Capability Exchange)"]
    end

    WinUI <--> Core
    AndUI <--> Core
    WebPortal <--> Core
    
    Core <==> USB
    Core <==> P2P
    Core <==> LAN
    Core <==> QUIC
    Core <--> BLE
```

---

## 💻 Quick Start: Windows Desktop App

### Option A: One-Click Desktop Launcher
Double-click [`run_windows_app.bat`](file:///e:/ShareDash/run_windows_app.bat) in the project directory.

### Option B: PowerShell Launcher
```powershell
.\launch_windows_app.ps1
```

### Option C: Manual CLI Execution
```powershell
# Run backend engine on port 54321
cargo run --release -- --port 54321

# Open in Edge/Chrome standalone app mode:
# msedge.exe --app=http://127.0.0.1:54321 --window-size=1180,820
```

---

## 📱 Quick Start: Android App

The Android companion app is located in [`android/`](file:///e:/ShareDash/android).

### Open in Android Studio:
1. Launch **Android Studio**.
2. Select **Open** and choose `e:\ShareDash\android`.
3. Click **Run** on your physical Android phone or emulator.

### Build APK via Gradle CLI:
```powershell
cd android
.\gradlew assembleDebug
```
The output debug APK will be generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## 🧪 Automated Test Suite

ShareDash includes an extensive test suite verifying protocol framing, cryptographic hash recovery, mid-transfer cable disconnect failovers, and multipath benchmarks:

```powershell
cargo test
```

### Included Tests:
- `tests/protocol_test.rs`: Validates binary frame encoding/decoding, CRC32 checks, and control message serialization.
- `tests/multipath_benchmark.rs`: Simulates concurrent multi-transport chunk distribution and throughput aggregation.
- `tests/failover_test.rs`: Simulates cable disconnect mid-transfer; verifies that unacknowledged chunks are stolen and reassigned to active channels with zero data loss.
- `tests/corruption_test.rs`: Injects random bit-flips into incoming chunks; verifies cryptographic rejection, quarantine, and automatic re-fetch.

---

## 📁 Repository Structure

```text
e:/ShareDash/
  ├── Cargo.toml                    # Rust dependencies & compiler configuration
  ├── run_windows_app.bat           # One-click Windows desktop app launcher
  ├── launch_windows_app.ps1        # PowerShell desktop app launcher
  ├── src/
  │   ├── lib.rs & main.rs          # CLI entry points and exports
  │   ├── protocol/                 # Binary framing, CRC32, AES-256-GCM, messages
  │   ├── storage/                  # Adaptive chunker, sparse writer, SQLite manifest WAL
  │   ├── transport/                # USB, Wi-Fi Direct, LAN, QUIC, and mock simulator
  │   ├── scheduler/                # Work-stealing scheduler, dynamic windowing, telemetry
  │   ├── discovery/                # UDP beacon & QR/PIN pairing manager
  │   └── server/                   # Axum HTTP API & sub-10ms WebSocket telemetry feed
  ├── sharedash-ui/                 # Windows 11 Fluent acrylic web interface
  │   ├── index.html                # Quick Share discovery radar & transfer view
  │   ├── css/style.css             # Acrylic styles, concentric radar ripples, badges
  │   └── js/                       # Radar controller, speedometer, canvas piece visualizer
  ├── android/                      # Native Android Companion Project
  │   ├── app/                      # Kotlin + Jetpack Compose app module
  │   │   ├── src/main/java/com/sharedash/app/
  │   │   │   ├── MainActivity.kt
  │   │   │   ├── discovery/        # BLE scanner & UDP discovery
  │   │   │   ├── storage/          # Android sparse writer (Downloads/ShareDash/)
  │   │   │   ├── transport/        # Android multi-transport socket manager
  │   │   │   ├── service/          # Foreground transfer notification service
  │   │   │   └── ui/               # RadarView, Speedometer, PieceGrid, BridgeCards
  │   │   └── AndroidManifest.xml   # BLE, Wi-Fi, and Share Sheet intent filters
  │   └── build.gradle.kts
  └── tests/                        # Comprehensive integration test suite
```

---

## 🔒 Security & Privacy

- **Zero-Cloud Dependency**: All transfers flow directly peer-to-peer over local hardware links.
- **End-to-End Encryption**: Chunks are encrypted with **AES-256-GCM** using session keys established during pairing.
- **Path Sanitization**: Direct directory-traversal protection prevents writing outside designated download folders.
