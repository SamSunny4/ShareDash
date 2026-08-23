# 📦 ShareDash Distribution Artifacts

Pre-built binaries and companion packages for Windows and Android.

## 📥 Artifacts

| Artifact | Platform | Description |
| :--- | :--- | :--- |
| `sharedash.exe` | Windows 10 / 11 (x86_64) | Standalone desktop engine & Quick Share CLI wizard with embedded Fluent Web dashboard. |
| `sharedash.apk` | Android 8.0+ (API 26+) | Companion Android app with USB-First flow, Bluetooth Low Energy discovery, and System Share Sheet target. |
| `ShareDash-debug.apk` | Android 8.0+ | Debug build of the Android companion app. |

## 🚀 Installation & Usage

### Windows:
Run `sharedash.exe` directly or use the project launcher `run_windows_app.bat`.

### Android:
Install via ADB:
```bash
adb install -r dist/sharedash.apk
```
Or transfer `sharedash.apk` to your phone and install via your file manager.
