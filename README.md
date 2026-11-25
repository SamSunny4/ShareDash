# ShareDash

Local file transfer between Android phones, between a phone and a Windows PC, and between Windows PCs.

ShareDash includes a native Windows desktop application built with Rust and GPUI, an interactive terminal client, and a Jetpack Compose Android companion. Devices exchange files directly over reachable local connections; no cloud account is required.

**Status:** early development. The Rust package is version `0.1.0`. Hardware support and throughput depend on the device, cable, network adapter, and operating system.

[Desktop guide](DESKTOP_GUIDE.md) · [Architecture](HOW_IT_WORKS.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md)

## Capabilities

| Area | Current implementation |
| --- | --- |
| Desktop interface | Connection assistants, device details, native file and folder selection, transfer progress, and incoming-transfer status |
| USB connections | Android USB tethering or ADB port forwarding |
| Wireless connections | Phone-hosted Wi-Fi Direct, a Windows hotspot fallback for phone-to-PC setup, and local IP discovery for PC-to-PC |
| Multipath sending | Concurrent chunk workers across available USB and Wi-Fi targets |
| Folder transfer | Windows packages a selected folder into a ZIP. Android walks the selected folder tree and sends its files individually. |
| Progress | Bytes transferred, average send speed, estimated time remaining, and per-channel statistics |
| Integrity | CRC32 checks on the desktop HTTP chunk path; separate binary-protocol modules provide encryption and cryptographic hash utilities |

Multipath can use both connections, but it is not guaranteed to outperform the faster single connection. ShareDash's desktop HTTP transfer path does **not** provide application-level end-to-end encryption. See [Security](SECURITY.md) for the current trust boundary.

## Intentional behavior to preserve

Use these behaviors as regression checks when changing discovery, pairing, transfer workers, or either UI. If a path looks redundant or stops working, check which device and connection state used it in the last working release before removing it. Update this section when a product decision deliberately changes one of these behaviors. [V1_GUI_2 source baseline](https://github.com/SamSunny4/ShareDash/tree/V1_GUI_2)

| When | Expected behavior and reason |
| --- | --- |
| A phone starts wireless setup | The phone hosts a Wi-Fi Direct group and shows the network name and password for the other device to join. Request 5 GHz when preferred and fall back to automatic band selection if needed. Discovery uses UDP and HTTP. Bluetooth LE is intentionally absent. |
| Wi-Fi is off or already connected to a router | Ask to enable Wi-Fi only when the radio is off. If already on another network, explain that sharing the radio can reduce speed. Offer to open Wi-Fi settings so the user can turn off auto-reconnect and disconnect, or **Continue on this Wi-Fi**. Continuing keeps the router association while still starting Wi-Fi Direct. |
| A user connects a USB cable | Prefer the USB tethering interface when reachable. **Open tethering settings** targets Android’s tethering page and gives manual directions if the phone cannot open it. On Windows, authorized ADB forward and reverse connections provide a second USB path so either device can send. |
| A PC adds wireless to a phone connection | Try the phone-hosted group first. Keep the Windows hotspot as a fallback if that link fails. Honor a USB-only choice and keep Windows hotspot settings behind an explicit user action. For wireless-only setup, the PC can join the group shown on the phone and verify its owner. |
| A peer appears or accepts a request | Discovery lists candidates. Pairing confirmation supplies the transfer target. The Windows connection assistant should take the user to **Transfer** with the confirmed peer and usable links. Keep this distinct from detecting a cable or submitting a pairing request. |
| Both links reach the same peer | USB and Wi-Fi workers take different chunks from a shared queue. A faster link can carry more of the file. Preserve single-link sending, retries, offset writes for out-of-order chunks, and counting a successful retry only once. Android also supports older receivers through the single-stream send endpoint when the chunk endpoint returns HTTP 404. |
| An Android transfer is active | Reuse send buffers and file channels, keep background subnet scans out of the transfer, and retain foreground transfer notifications. The progress rings show each link’s contribution to total bytes. USB runs anticlockwise and Wi-Fi runs clockwise. Show aggregate progress when the transport breakdown is unavailable. |

Android also accepts files from the system share sheet, including multiple files. Keep those entry points working alongside the in-app file and folder pickers. [Android connection and send flow](android/app/src/main/java/com/sharedash/app/MainActivity.kt) · [Android receiver](android/app/src/main/java/com/sharedash/app/server/AndroidHttpServer.kt) · [Windows connection flow](src/gui/app.rs)

### Checking a change

- For setup changes, exercise Wi-Fi off, already on a router, and USB only. Confirm that accepting a peer reaches **Transfer** and that declining wireless setup leaves USB usable.
- For transfer changes, send in both directions, compare source and destination SHA-256 hashes, and check the USB-only, Wi-Fi-only, and combined paths affected by the change. Include a retry or interrupted link when changing worker or progress accounting.
- Phone-to-PC has been tested. A 25 MiB Android-emulator-to-Windows send also passed an independent SHA-256 comparison. Simultaneous USB and Wi-Fi Direct throughput between two physical phones still needs hardware verification.

## Build and run the desktop app

### Requirements

- Windows with a supported graphics driver for GPUI.
- Rust and Cargo, using the MSVC toolchain.
- Visual Studio Build Tools with the C++ build tools and Windows SDK.
- The Android companion app for phone transfers.
- ADB available on `PATH` when using the debugging-based USB connection. USB tethering is a separate connection method.

The repository does not currently declare a minimum supported Rust version. Use a toolchain compatible with the checked-in `Cargo.lock`.

From the repository root:

```powershell
cargo run --locked --release --features gui --bin sharedash-gui
```

For development:

```powershell
cargo run --locked --features gui --bin sharedash-gui
```

The existing `run_windows_app.bat` launcher runs the development GUI command.

To produce an executable:

```powershell
cargo build --locked --release --features gui --bin sharedash-gui
```

Output: `target/release/sharedash-gui.exe`.

### First transfer

1. Open ShareDash on both devices.
2. Select **Connect phone** or **Connect PC** in the desktop navigation.
3. Follow the connection steps and confirm the intended peer.
4. Open **Transfer**, choose a file or folder, and select the connection mode.
5. Select **Start transfer** and keep both devices connected until completion.

On Windows, received files are stored in:

```text
%USERPROFILE%\Downloads\ShareDash_Received
```

Use **Received files** on the overview or device-details page to open this folder. For connection-specific instructions and limitations, see the [desktop guide](DESKTOP_GUIDE.md).

## Terminal client

The terminal client remains a separate entry point:

```powershell
cargo run --locked --release --bin sharedash
```

Both executable entry points accept these options:

| Option | Default | Purpose |
| --- | --- | --- |
| `--port`, `-p` | `54321` | HTTP and WebSocket listening port |
| `--name` | `ShareDash Desktop` | Device name shown to peers |
| `--db-path` | `./sharedash_data/manifest.db` | SQLite manifest database path |

Example:

```powershell
cargo run --locked --features gui --bin sharedash-gui -- --name "Sam's PC" --port 54322
```

Changing the listening port does not rewrite every discovery or ADB default. Use the default port for ordinary device-to-device setup. The terminal executable starts its interactive menu alongside the server; `--port` is not a headless-mode switch.

## Android companion

The Android source is in [android/](android/). Its build configuration specifies minimum API 26, compile/target API 35, and Java 17.

Build with the checked-in Windows Gradle wrapper:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The output is `android/app/build/outputs/apk/debug/app-debug.apk`. Install a built APK with:

```powershell
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Run that installation command from the repository root. Existing packaged artifacts are listed in [dist/README.md](dist/README.md); they may predate the current source.

## Development checks

```powershell
cargo check --locked --features gui --bin sharedash-gui
cargo test --locked --lib
cargo test --locked --test protocol_test --test corruption_test --test failover_test
```

The GUI is behind an optional feature. A default `cargo check` does not validate the desktop views. Protocol and simulation tests do not replace a physical USB/Wi-Fi interoperability test.

See [CONTRIBUTING.md](CONTRIBUTING.md) for validation scope and review expectations.

## Repository map

| Path | Responsibility |
| --- | --- |
| [src/gui/](src/gui/) | Desktop state, views, widgets, and GUI transfer orchestration |
| [src/bin/sharedash_gui.rs](src/bin/sharedash_gui.rs) | GPUI application entry point |
| [src/main.rs](src/main.rs), [src/cli.rs](src/cli.rs) | Terminal entry point and interactive workflows |
| [src/server/](src/server/) | Axum HTTP endpoints, receive handlers, and WebSocket telemetry |
| [src/discovery/](src/discovery/) | UDP discovery, pairing helpers |
| [src/hotspot.rs](src/hotspot.rs) | Windows network inspection and hotspot orchestration |
| [src/protocol/](src/protocol/), [src/scheduler/](src/scheduler/), [src/storage/](src/storage/) | Binary protocol, scheduling, manifests, and storage utilities |
| [src/transport/](src/transport/) | Transport implementations and simulation support |
| [android/](android/) | Android companion application |
| [docs/](docs/) | Static project website; not a dashboard served by the transfer API |
| [tests/](tests/), [scripts/](scripts/) | Integration tests and benchmark utilities |

## Performance

Measure USB-only, Wi-Fi-only, and combined transfers on the same hardware and file before comparing results. Record the file size, direction, elapsed time, adapter and cable details, and destination hash.

The desktop displays binary units: MiB and MiB/s use 1,048,576 bytes. A Wi-Fi adapter's advertised Mbps link rate is not the file-transfer rate. Historical benchmark claims are not a guarantee for other systems. See [scripts/README.md](scripts/README.md) for the available tools.

## License and support

ShareDash is distributed under the [MIT License](LICENSE). Use [GitHub Issues](https://github.com/SamSunny4/ShareDash/issues/new/choose) for reproducible bugs and feature requests. Report vulnerabilities privately using the process in [SECURITY.md](SECURITY.md).
