# Contributing

ShareDash is an early-stage local file-transfer project. Changes should make the implemented behavior easier to use, verify, and maintain.

Read the [architecture guide](HOW_IT_WORKS.md) before changing transfer code. The desktop HTTP path and binary-protocol library have different behavior and validation requirements.

## Development setup

For the Windows application, install Rust with the MSVC toolchain, Visual Studio C++ build tools, and a Windows SDK. Use the repository's lockfile:

```powershell
git clone https://github.com/SamSunny4/ShareDash.git
cd ShareDash
cargo check --locked --all-targets
cargo check --locked --features gui --bin sharedash-gui
cargo run --locked --features gui --bin sharedash-gui
```

The GUI is optional. Always include `--features gui` when validating desktop changes. Windows is the target for the desktop/network orchestration; do not assume the full application is supported on another OS because a module contains platform-specific branches.

The repository limits Cargo to one build job in [.cargo/config.toml](.cargo/config.toml). A first GUI build can take substantially longer than an incremental build.

For debug logs in PowerShell:

```powershell
$env:RUST_LOG = "sharedash=debug,tower_http=info"
cargo run --locked --features gui --bin sharedash-gui
```

For the Android companion, use Java 17, Android SDK 35, and the checked-in Windows wrapper:

```powershell
cd android
.\gradlew.bat assembleDebug
```

## Choose the correct layer

| Change | Start here |
| --- | --- |
| Desktop layout, typography, labels, or reusable controls | `src/gui/*_view.rs`, `src/gui/widgets.rs`, `src/gui/theme.rs` |
| Desktop navigation or UI event handling | `src/gui/app.rs` |
| Desktop HTTP sender and folder packaging | `src/gui/engine.rs` |
| HTTP receive behavior and API contracts | `src/server/api.rs` |
| Discovery and network setup | `src/discovery/`, `src/hotspot.rs` |
| Binary framing, encryption, or scheduler | `src/protocol/`, `src/scheduler/`, `src/storage/` |
| Terminal interface | `src/cli.rs`, `src/cli_widgets.rs` |
| Android interface | `android/app/src/main/java/com/sharedash/app/ui/` |

Keep changes within the requested scope. Presentation changes should preserve engine behavior, API contracts, and existing callbacks unless the change explicitly calls for a behavioral fix.

## Validation

Run the checks relevant to your changes:

```powershell
cargo check --locked --all-targets
cargo check --locked --features gui --bin sharedash-gui
cargo test --locked --lib
cargo test --locked --test protocol_test --test corruption_test --test failover_test
```

Formatting and lint checks:

```powershell
cargo fmt --all -- --check
cargo clippy --locked --all-targets
cargo clippy --locked --features gui --bin sharedash-gui
```

For a scoped change, format the Rust files you touched with `rustfmt --edition 2021 <files>`. Avoid unrelated formatting churn; report pre-existing repository-wide failures separately.

### Desktop review

Check the default window and the minimum supported window size. Exercise overview navigation, connection steps, empty discovery, native file/folder dialogs, mode selection, preparing/active/completed/error states, and long filenames. Verify that overflowing content remains reachable by scrolling.

### Transfer review

Use disposable fixtures and record the hardware and software versions. Test each available connection mode, both transfer directions, cancellation, disconnection, and destination hashes. Include a large file and a nested folder.

Simulation tests do not validate the actual desktop HTTP path or Android interoperability. A passed GUI build is not evidence of a successful hardware transfer.

### Benchmarking

Read [scripts/README.md](scripts/README.md) before running hardware scripts. Some scripts install or launch the Android app, create large files, and change ADB forwarding. Distinguish simulated results from physical measurements.

## Code and documentation standards

- Keep blocking file/network work off the GPUI render path.
- Make errors actionable and avoid unsupported security or throughput claims.
- Use the shared theme and widget helpers for desktop changes.
- Preserve units: bytes, MiB/s, Mbps, and link rates are different quantities.
- Document implemented behavior separately from planned capabilities.
- Add tests for meaningful behavioral changes; use compilation and visual inspection for presentation changes.
- Do not commit credentials, personal logs, received files, or generated build output.

If the local graphify workflow is installed, query the graph before architecture work and run `graphify update .` after code changes. The source code remains authoritative when generated summaries disagree.

## Pull requests

Describe the problem, the resulting behavior, and the scope. Include the checks actually run and any hardware or environment limitations. Attach relevant UI screenshots for visual changes.

Use a focused branch and a descriptive commit message. The [pull request template](.github/PULL_REQUEST_TEMPLATE.md) provides the review checklist.

For bugs, use [GitHub Issues](https://github.com/SamSunny4/ShareDash/issues/new/choose) and include reproduction steps. Follow the [Code of Conduct](CODE_OF_CONDUCT.md). Report security issues privately as described in [SECURITY.md](SECURITY.md).
