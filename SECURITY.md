# Security

ShareDash is under active development. Its current desktop HTTP path is intended for trusted local connections and should not be treated as a hardened public file-transfer service.

## Current implementation

| Area | Behavior and boundary |
| --- | --- |
| Desktop file transport | Sends raw chunk bodies over HTTP. The GUI worker does not apply application-level AES-GCM encryption or TLS. |
| Chunk integrity | The desktop sender supplies CRC32. The Windows receiver validates CRC32 when present, or SHA-256 when supplied without CRC32. These checks do not authenticate a peer. |
| Pairing | PIN/QR and peer handshake helpers exist. The chunk receiver does not enforce their tokens as per-request authorization. |
| Binary protocol | `SessionCrypto` implements AES-256-GCM and hashing helpers, with an explicit unencrypted mode. These library capabilities are separate from the GUI HTTP path. |
| Listening interface | The HTTP server binds to `0.0.0.0`, allowing access through reachable interfaces. |
| API exposure | The router allows any CORS origin and disables the default request-body limit. Network setup and local folder actions are also registered as API routes. |
| Destination names | The chunk handler reduces filenames to a basename and replaces reserved characters. Other upload paths must be reviewed separately; this is not a blanket traversal-safety guarantee. |
| File collisions | A received filename can reuse an existing destination file. There is no general rename-on-conflict guarantee. |
| Completion | GUI success reflects sender worker outcomes, not an independent whole-file hash comparison. |

Implementation references: [GUI sender](src/gui/engine.rs), [HTTP handlers](src/server/api.rs), [server router](src/server/http.rs), [cryptographic utilities](src/protocol/crypto.rs).

## Operating guidance

Use a trusted local network or a direct connection, and keep the service off public interfaces and Internet port forwards. Accept pairing requests only from devices you recognize. Keep separate copies of important files and independently compare hashes where integrity is critical.

Hotspot or Wi-Fi link encryption is separate from application-level end-to-end encryption. A direct cable or an offline network does not by itself make every HTTP endpoint authenticated.

## Reporting a vulnerability

Do not post exploit details, pairing credentials, or sensitive logs in a public issue. Use the repository's [private vulnerability reporting page](https://github.com/SamSunny4/ShareDash/security/advisories/new).

Include:

- The affected commit or binary version.
- The affected entry point and connection method.
- Minimal reproduction steps using non-sensitive test data.
- Expected and observed behavior.
- The potential impact and any suggested mitigation.

The repository does not define a formal security support lifetime or guaranteed response time. Reports should identify the exact revision tested. Coordinate disclosure through the private report.

## Security work still required

Authentication for transfer and control requests, an authenticated encrypted desktop transport, bounded receive resources, and consistent receive-path validation require further work. See [ROADMAP.md](ROADMAP.md). This document describes current behavior; it is not a security audit or certification.
