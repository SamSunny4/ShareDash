# Distribution artifacts

This directory contains checked-in packages. They may predate the current source tree; filenames alone do not establish a build revision or signing status.

| File | Description |
| --- | --- |
| `sharedash.exe` | Windows terminal client and HTTP service |
| `sharedash.apk` | Packaged Android companion |
| `ShareDash-debug.apk` | Android debug package |

A desktop GUI executable is not currently checked into this directory. Build the current GUI from the repository root:

```powershell
cargo build --locked --release --features gui --bin sharedash-gui
.\target\release\sharedash-gui.exe
```

The existing `run_windows_app.bat` launches the development GUI through Cargo; it does not run `dist/sharedash.exe`.

To install a packaged Android app from the repository root:

```powershell
adb install -r dist/sharedash.apk
```

For source builds, prerequisites, and connection instructions, see the [README](../README.md) and [desktop guide](../DESKTOP_GUIDE.md). Published assets, when available, are listed under [GitHub Releases](https://github.com/SamSunny4/ShareDash/releases).
