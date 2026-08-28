# 🤝 Contributing to ShareDash

Thank you for your interest in contributing to **ShareDash**! Whether you are fixing a bug, adding high-performance network transport algorithms, polishing the Android Jetpack Compose UI, or writing documentation, your help is warmly welcomed.

---

## 🧭 Table of Contents
- [Code of Conduct](#-code-of-conduct)
- [How Can I Contribute?](#-how-can-i-contribute)
- [Development Setup](#-development-setup)
  - [Rust Core Engine (PC)](#1-rust-core-engine-windows--linux)
  - [Android Companion App](#2-android-companion-app)
- [Coding Guidelines & Standards](#-coding-guidelines--standards)
- [Commit Message Conventions](#-commit-message-conventions)
- [Submitting a Pull Request](#-submitting-a-pull-request)

---

## 📜 Code of Conduct
This project and everyone participating in it is governed by the [ShareDash Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## 💡 How Can I Contribute?
- **⭐ Star & Share**: Help more people discover ShareDash by starring the repo and sharing benchmarks!
- **🐛 Report Bugs**: Submit structured bug reports via [GitHub Issues](https://github.com/SamSunny4/ShareDash/issues/new/choose).
- **✨ Suggest Features**: Propose enhancements, new physical transport channels, or UI improvements.
- **💻 Submit Pull Requests**: Implement bug fixes, performance boosts, or new platform ports.

---

## 🛠️ Development Setup

### 1. Rust Core Engine (Windows / Linux)
Requirements:
- **Rust 1.78+**: Install via [rustup.rs](https://rustup.rs/)
- **Git**

```bash
# Clone the repository
git clone https://github.com/SamSunny4/ShareDash.git
cd ShareDash

# Verify compilation
cargo check --all-targets

# Run the test suite
cargo test --lib --release

# Run integration tests
cargo test --test protocol_test
cargo test --test corruption_test
cargo test --test test_tether_detection

# Run with debug logging
RUST_LOG=debug cargo run --release
```

### 2. Android Companion App
Requirements:
- **Android Studio** (Koala / Ladybug or newer)
- **JDK 17**
- **Android SDK API 34+**
- **Physical Android device** with Developer Options & USB Debugging enabled

```bash
cd android

# Compile debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📐 Coding Guidelines & Standards

### Rust Core
- Format all code with `cargo fmt --all` before committing.
- Run `cargo clippy --all-targets` and ensure no unhandled warnings remain.
- Favor explicit error handling with `anyhow` or custom typed errors; avoid `unwrap()` in production paths.
- Ensure all transport channels respect cancellation tokens (`tokio_util::sync::CancellationToken`) for clean disconnects.

### Android / Kotlin
- Follow official Kotlin coding conventions and Jetpack Compose best practices.
- Avoid performing disk I/O or cryptographic verification on the main/UI thread (use `Dispatchers.IO`).
- Maintain Material 3 Expressive UI theming.

---

## 💬 Commit Message Conventions
We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```text
<type>(<scope>): <short summary>

[optional body]

[optional footer(s)]
```

### Types:
- `feat`: A new feature (e.g., `feat(transport): add WebRTC browser fallback channel`)
- `fix`: A bug fix (e.g., `fix(ble): resolve peripheral advertisement drop on Android 14`)
- `perf`: A code change that improves performance (e.g., `perf(chunker): reduce sparse write mutex contention`)
- `docs`: Documentation changes (e.g., `docs(readme): add comparison matrix`)
- `refactor`: A code refactor that neither fixes a bug nor adds a feature
- `test`: Adding or updating test suites
- `ci`: Changes to CI/CD workflows and configuration

---

## 🚀 Submitting a Pull Request

1. **Fork the repository** on GitHub.
2. **Create a topic branch** from `main`:
   ```bash
   git checkout -b feat/my-awesome-feature
   ```
3. **Make your changes** and commit using Conventional Commits.
4. **Run the test suite** locally to ensure everything passes:
   ```bash
   cargo test --lib --release
   ```
5. **Push to your fork** and open a Pull Request against the `main` branch.
6. Fill out the [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md) completely.
