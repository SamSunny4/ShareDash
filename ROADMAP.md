# Roadmap

This roadmap separates implemented capabilities from proposed work. It does not promise release dates or imply that planned features are already available.

## Implemented foundations

- Native Windows GPUI interface and separate terminal client.
- Android companion built with Jetpack Compose.
- UDP discovery helpers and USB tethering/ADB connection paths.
- Phone hotspot and Windows hotspot orchestration helpers.
- Desktop HTTP chunk sending with concurrent workers across available connections.
- File and ZIP-packaged folder sending, progress, cancellation, and receive status.
- Binary framing, cryptographic helpers, manifest storage, and simulation tests.

See [HOW_IT_WORKS.md](HOW_IT_WORKS.md) for the boundaries of each path.

## Reliability and security priorities

- Enforce authenticated sessions on transfer and control endpoints.
- Add authenticated encryption to the desktop transfer path.
- Verify whole-file integrity at completion and report it explicitly.
- Complete PC discovery-to-transfer state propagation and editable destination handling.
- Replace optimistic initial connection defaults with explicit unknown/detected states.
- Strengthen cancellation cleanup, receive validation, resource limits, and filename conflict handling.
- Validate empty files, interrupted transfers, and large directory archives across real devices.
- Add end-to-end HTTP transfer tests alongside the existing binary-protocol tests.

## Desktop usability

- Persistent transfer history and a managed multi-file queue.
- Resume support integrated with the active desktop transfer path.
- Accessible status announcements and broader keyboard-navigation coverage.
- Optional tray/background operation and incoming-transfer notifications.
- Reproducible GUI packaging and release checks.

## Longer-term exploration

- Native Linux and macOS network orchestration.
- A browser-based receiver.
- A real QUIC transport implementation.
- Additional simultaneous network paths and multi-device sending.
- Further Android batch and background workflow improvements.

Prioritize these features only after the current transfer and trust boundaries are well tested. Proposals should describe a concrete use case, implementation impact, and validation approach through [GitHub Issues](https://github.com/SamSunny4/ShareDash/issues/new/choose).
