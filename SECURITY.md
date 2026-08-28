# 🛡️ Security Policy

## Supported Versions

We actively provide security patches and integrity updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| `0.1.x` (Latest Release) | :white_check_mark: |
| `main` Branch            | :white_check_mark: |
| `< 0.1.0`                | :x:                |

---

## 🔒 Security & Cryptographic Architecture

ShareDash is engineered from the ground up with a zero-trust, zero-cloud offline threat model:

1. **End-to-End Encryption**:
   - Every file chunk payload is encrypted using authenticated **AES-256-GCM** or **ChaCha20-Poly1305**.
   - Session keys are derived per transfer session using high-entropy ephemeral tokens exchanged during the Bluetooth LE / PIN verification handshake.

2. **Zero-Corruption Bit Integrity**:
   - Each individual frame is checked with **CRC32-Castagnoli** prior to deserialization to reject noisy physical link interference.
   - Chunks and complete files are verified via cryptographic **SHA-256** and **BLAKE3** digests.

3. **Path Traversal Mitigation**:
   - All inbound filenames are strictly sanitized against directory traversal attacks (`../`, absolute path overrides, control characters).
   - Files are written strictly within designated download directories.

4. **100% Offline & Private**:
   - ShareDash initiates **zero external network calls**, carries zero telemetry, and performs zero telemetry pings to third-party analytics servers.

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability in ShareDash, please do **NOT** open a public issue.

Instead, please report it via one of the following methods:
1. **GitHub Security Advisory**: Open a private advisory under [Security > Advisories](https://github.com/SamSunny4/ShareDash/security/advisories/new).
2. **Email**: Contact the maintainer directly at **samsunny4@github.com**.

### What to Include:
- Description of the vulnerability.
- Proof of Concept (PoC) or reproducible steps.
- Affected component(s) (e.g. Rust Core, Android App, BLE Protocol).
- Potential impact.

We will acknowledge receipt within **48 hours** and provide a timeline for resolution. Once resolved, we will publish a security advisory crediting you.
