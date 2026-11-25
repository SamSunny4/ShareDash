# Desktop guide

This guide covers the native Windows application built with the `gui` feature. The terminal client and Android app have their own interfaces.

## Navigation

| Page | Use it to |
| --- | --- |
| Overview | Check USB, Wi-Fi, and pairing status; accept incoming connection requests; open received files |
| Connect phone | Run the phone connection assistant |
| Connect PC | Discover another ShareDash computer or try the saved network address |
| Transfer | Choose content, select a connection mode, send, cancel, and review results |
| Device details | Inspect adapters, addresses, receive location, and listening port; open Windows network settings |

The header shows the local device name and listening port. Longer pages scroll independently of the header and navigation.

## Connect an Android phone

Open the Android app before starting the desktop assistant.

### USB tethering

1. Connect the phone with a data-capable USB cable.
2. Enable USB tethering in the phone's network or hotspot settings.
3. Select **Connect phone** on the desktop.
4. Follow the assistant while it detects the tethering interface and contacts the phone.

The exact Android settings path depends on the device manufacturer.

### ADB connection

1. Install Android platform tools and make `adb` available on `PATH`.
2. Enable USB debugging on the phone and authorize this computer on the phone.
3. Check that `adb devices` reports the device as `device`, rather than `unauthorized` or `offline`.
4. Open the Android app, then start **Connect phone**.

The desktop maintains forwarding between local port `54325` and the phone's HTTP service on `54321`. It also configures a reverse connection for phone-to-computer traffic.

### Add Wi-Fi

The assistant exchanges Wi-Fi capabilities and tries to establish a phone-hosted hotspot connection, with Windows hotspot setup as a fallback. Availability depends on the adapters, drivers, and phone.

Choose **Use USB only** to keep sending over the cable. For a wireless-only connection, start Wi-Fi Direct in ShareDash on the phone, join the displayed network on Windows, then choose **Use wireless connection**. The assistant verifies the phone and opens **Transfer** after pairing. If setup stalls, check the network name against the one displayed on the phone and retry.

## Connect another computer

1. Open ShareDash on both computers.
2. Select **Connect PC** and wait for discovery.
3. Choose **Connect** beside the intended peer.
4. Accept the incoming request on the receiving computer's overview.
5. Proceed to the transfer page when a connection is ready.

**Scan again** repeats discovery. **Connect to saved address** uses the address displayed beside that action; the current interface does not include an editable IP-address field. The default is `192.168.137.1:54321`, which may not match your peer.

ShareDash discovers peers over the local IP link. File data uses the reachable IP connection. After pairing, the transfer screen shows the connected device.

## Send a file or folder

1. Open **Transfer** after establishing the connection.
2. Select **Choose file** or **Choose folder**.
3. Review the selected name, location, and size.
4. Select **USB only**, **Wi-Fi only**, or **USB + Wi-Fi**.
5. Select **Start transfer**.

Folders are packaged into an uncompressed ZIP archive before transmission. Allow temporary disk space for the archive. The archive contains files with relative paths; empty directories are not explicitly recorded.

During a transfer, the page displays:

- Average sender throughput since transfer start.
- Transferred bytes, percentage, and estimated remaining time.
- Per-channel bytes, speed, and acknowledged chunks.
- A segmented view of byte progress, not a per-chunk integrity certificate.

Use **Cancel transfer** to stop the sender. Cancellation may leave a partial file on the receiver. After completion, **Send another file** returns to content selection.

The completion result reports the sender's outcome. It does not certify a whole-file SHA-256 comparison. For an independent check between two Windows machines, compare:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath "C:\path\to\file"
```

## Receive files

Keep ShareDash running. An incoming transfer appears in the status strip above the active page.

Windows receives files under `%USERPROFILE%\Downloads\ShareDash_Received`. If `USERPROFILE` is unavailable, the implementation falls back to a `downloads` directory relative to the working directory.

The current chunk receiver reuses the destination filename. Existing files with the same name can be overwritten; move an existing copy before receiving another file with that name.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Application fails to bind its port | Another ShareDash process may already be using `54321`. Close the duplicate instance or choose a separate port for diagnostics. |
| USB device is absent | Use a data cable; enable tethering or authorize ADB; keep the phone app open. |
| Wi-Fi adapter is disabled | Open **Device details → Wi-Fi settings** and enable it. |
| No nearby computer appears | Keep both apps open, verify local reachability, and check whether firewall rules or network isolation block discovery. |
| Pairing succeeds but sending fails | Check the actual target address and connection availability on **Transfer**. |
| Hotspot setup stalls | Check the connection activity and Windows hotspot settings; try the available USB-only option. |
| Transfer speed is lower than the link rate | Compare single-channel transfers; check cable generation, storage speed, signal strength, and adapter sharing. |
| A folder takes time before progress appears | ShareDash creates the ZIP archive before starting file transmission. |
| Transfer is cancelled or reports an error | Treat the destination as potentially incomplete and resend after fixing the connection. |

For a useful bug report, include the selected workflow, device and OS versions, adapter/cable details, file size, and relevant error text. Remove pairing codes, hotspot passwords, and personal paths from shared logs.

## Current limits

- Use trusted local connections. The desktop chunk transport is HTTP without application-level encryption or enforced per-request authentication.
- Some initial hardware fields and transfer targets are defaults until discovery updates them.
- Live GUI transfers use in-memory progress. Restart-resume and a persistent GUI transfer queue are not implemented by this path.
- The desktop does not provide a general-purpose editable destination address.
- No fixed throughput is promised, and combining links can be slower than a fast single link.

See [HOW_IT_WORKS.md](HOW_IT_WORKS.md) for the implementation boundaries.
