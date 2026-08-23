//! Windows Mobile Hotspot management module.
//!
//! Uses PowerShell / `netsh` to control the Windows Mobile Hotspot,
//! query connected clients, and determine the hotspot gateway IP.

use anyhow::Result;
#[cfg(not(target_os = "windows"))]
use anyhow::anyhow;
use std::time::Duration;
use crate::discovery::WifiCapsInfo;

#[derive(Debug, Clone)]
pub struct HotspotInfo {
    pub ssid: String,
    pub password: String,
    pub band: String,
    pub gateway_ip: String,
    pub is_active: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub enum HotspotHostChoice {
    Pc,
    Phone,
}

/// Detect local PC Wi-Fi adapter capabilities.
pub async fn detect_pc_wifi_caps() -> WifiCapsInfo {
    #[cfg(target_os = "windows")]
    {
        if let Ok(output) = tokio::process::Command::new("netsh")
            .args(["wlan", "show", "drivers"])
            .output()
            .await
        {
            let text = String::from_utf8_lossy(&output.stdout);
            let mut standard = "Wi-Fi 5 (802.11ac)".to_string();
            let mut max_freq = 5.0;
            let mut max_bw = 80;
            let mut max_phy = 866;
            let mut bands = vec!["2.4 GHz".to_string(), "5 GHz".to_string()];

            if text.contains("802.11be") || text.contains("Wi-Fi 7") {
                standard = "Wi-Fi 7 (802.11be)".to_string();
                max_freq = 6.0;
                max_bw = 320;
                max_phy = 4804;
                bands.push("6 GHz".to_string());
            } else if text.contains("802.11ax") || text.contains("Wi-Fi 6") {
                if text.contains("6 GHz") || text.contains("Wi-Fi 6E") {
                    standard = "Wi-Fi 6E (802.11ax)".to_string();
                    max_freq = 6.0;
                    max_bw = 160;
                    max_phy = 2402;
                    bands.push("6 GHz".to_string());
                } else {
                    standard = "Wi-Fi 6 (802.11ax)".to_string();
                    max_freq = 5.0;
                    max_bw = 160;
                    max_phy = 1200;
                }
            }

            return WifiCapsInfo {
                wifi_standard: standard,
                max_frequency_ghz: max_freq,
                max_channel_width_mhz: max_bw,
                max_phy_rate_mbps: max_phy,
                supported_bands: bands,
            };
        }
    }

    WifiCapsInfo {
        wifi_standard: "Wi-Fi 6 (802.11ax)".to_string(),
        max_frequency_ghz: 5.0,
        max_channel_width_mhz: 160,
        max_phy_rate_mbps: 1200,
        supported_bands: vec!["2.4 GHz".to_string(), "5 GHz".to_string()],
    }
}

/// Automatically select the device with superior Wi-Fi hardware to host the hotspot.
pub fn select_best_hotspot_host(pc_caps: &WifiCapsInfo, phone_caps: &WifiCapsInfo) -> HotspotHostChoice {
    let pc_score = pc_caps.max_phy_rate_mbps + if pc_caps.supported_bands.iter().any(|b| b.contains("6 GHz")) { 500 } else { 0 };
    let phone_score = phone_caps.max_phy_rate_mbps + if phone_caps.supported_bands.iter().any(|b| b.contains("6 GHz")) { 500 } else { 0 };

    if pc_score >= phone_score {
        HotspotHostChoice::Pc
    } else {
        HotspotHostChoice::Phone
    }
}

/// Create and start a Windows Mobile Hotspot.
///
/// Uses the modern Windows Mobile Hotspot via `netsh wlan` commands.
/// Falls back to opening Settings if direct control fails.
pub async fn create_hotspot(ssid: &str, password: &str, band_5ghz: bool) -> Result<HotspotInfo> {
    #[cfg(target_os = "windows")]
    {
        // Step 1: Configure the hosted network
        let _ = band_5ghz;

        // Try using netsh wlan to set up a hosted network
        let configure = tokio::process::Command::new("netsh")
            .args([
                "wlan",
                "set",
                "hostednetwork",
                &format!("mode=allow"),
                &format!("ssid={}", ssid),
                &format!("key={}", password),
            ])
            .output()
            .await;

        match configure {
            Ok(output) => {
                let stdout = String::from_utf8_lossy(&output.stdout);
                tracing::info!("Hotspot configure: {}", stdout.trim());

                if stdout.contains("success") || stdout.contains("hosted network") || output.status.success() {
                    // Step 2: Start the hosted network
                    let start = tokio::process::Command::new("netsh")
                        .args(["wlan", "start", "hostednetwork"])
                        .output()
                        .await;

                    match start {
                        Ok(start_output) => {
                            let start_msg = String::from_utf8_lossy(&start_output.stdout);
                            tracing::info!("Hotspot start: {}", start_msg.trim());

                            if start_msg.contains("started") || start_output.status.success() {
                                return Ok(HotspotInfo {
                                    ssid: ssid.to_string(),
                                    password: password.to_string(),
                                    band: if band_5ghz { "5 GHz".to_string() } else { "Auto".to_string() },
                                    gateway_ip: "192.168.137.1".to_string(),
                                    is_active: true,
                                });
                            }
                        }
                        Err(e) => {
                            tracing::warn!("Failed to start hostednetwork: {}", e);
                        }
                    }
                }
            }
            Err(e) => {
                tracing::warn!("netsh hostednetwork not available: {}", e);
            }
        }

        // Step 1: Use the modern Windows Mobile Hotspot via WinRT API
        let ps_script = format!(
            r#"
            try {{
                $tetheringManager = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType = WindowsRuntime]
                $connectionProfile = [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType = WindowsRuntime]::GetInternetConnectionProfile()
                if ($connectionProfile) {{
                    $manager = $tetheringManager::CreateFromConnectionProfile($connectionProfile)
                    $config = $manager.GetCurrentAccessPointConfiguration()
                    $config.Ssid = '{ssid}'
                    $config.Passphrase = '{password}'
                    try {{
                        $config.Band = [Windows.Networking.NetworkOperators.TetheringWiFiBand]::Auto
                    }} catch {{}}

                    $action = $manager.ConfigureAccessPointAsync($config)
                    while ($action.Status -eq [Windows.Foundation.AsyncStatus]::Started) {{
                        Start-Sleep -Milliseconds 50
                    }}

                    if ($manager.TetheringOperationalState -ne [Windows.Networking.NetworkOperators.TetheringOperationalState]::On) {{
                        $op = $manager.StartTetheringAsync()
                        $timeout = 0
                        while ($manager.TetheringOperationalState -ne [Windows.Networking.NetworkOperators.TetheringOperationalState]::On -and $timeout -lt 30) {{
                            Start-Sleep -Milliseconds 300
                            $timeout++
                        }}
                    }}

                    if ($manager.TetheringOperationalState -eq [Windows.Networking.NetworkOperators.TetheringOperationalState]::On) {{
                        Write-Host "HOTSPOT_STARTED"
                    }} else {{
                        Write-Host "HOTSPOT_FAILED"
                    }}
                }} else {{
                    Write-Host "NO_INTERNET_PROFILE"
                }}
            }} catch {{
                Write-Host "ERROR:$($_.Exception.Message)"
            }}
            "#,
            ssid = ssid,
            password = password,
        );

        let ps_result = tokio::process::Command::new("powershell")
            .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", &ps_script])
            .output()
            .await;

        match ps_result {
            Ok(output) => {
                let stdout = String::from_utf8_lossy(&output.stdout);
                tracing::info!("PowerShell hotspot result: {}", stdout.trim());

                if stdout.contains("HOTSPOT_STARTED") {
                    return Ok(HotspotInfo {
                        ssid: ssid.to_string(),
                        password: password.to_string(),
                        band: if band_5ghz { "5 GHz".to_string() } else { "Auto".to_string() },
                        gateway_ip: detect_hotspot_gateway().await.unwrap_or("192.168.137.1".to_string()),
                        is_active: true,
                    });
                }
            }
            Err(e) => {
                tracing::warn!("PowerShell hotspot config failed: {}", e);
            }
        }

        // Fallback: Open Windows Settings only if automatic WinRT fails
        let _ = tokio::process::Command::new("powershell")
            .args(["-NoProfile", "-Command", "Start-Process 'ms-settings:network-mobilehotspot'"])
            .spawn();

        Ok(HotspotInfo {
            ssid: ssid.to_string(),
            password: password.to_string(),
            band: if band_5ghz { "5 GHz".to_string() } else { "Auto".to_string() },
            gateway_ip: "192.168.137.1".to_string(),
            is_active: false,
        })
    }

    #[cfg(not(target_os = "windows"))]
    {
        Err(anyhow!("Hotspot management is only supported on Windows"))
    }
}

/// Stop the hosted network / mobile hotspot.
pub async fn stop_hotspot() -> Result<()> {
    #[cfg(target_os = "windows")]
    {
        let _ = tokio::process::Command::new("netsh")
            .args(["wlan", "stop", "hostednetwork"])
            .output()
            .await;

        // Also try the modern API
        let ps_script = r#"
            try {
                $tetheringManager = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType = WindowsRuntime]
                $connectionProfile = [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType = WindowsRuntime]::GetInternetConnectionProfile()
                if ($connectionProfile) {
                    $manager = $tetheringManager::CreateFromConnectionProfile($connectionProfile)
                    $manager.StopTetheringAsync().AsTask().Wait()
                }
            } catch {}
        "#;
        let _ = tokio::process::Command::new("powershell")
            .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps_script])
            .output()
            .await;

        Ok(())
    }
    #[cfg(not(target_os = "windows"))]
    {
        Ok(())
    }
}

/// Get the current hotspot status.
pub async fn get_hotspot_status() -> Option<HotspotInfo> {
    #[cfg(target_os = "windows")]
    {
        let output = tokio::process::Command::new("netsh")
            .args(["wlan", "show", "hostednetwork"])
            .output()
            .await
            .ok()?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        let mut ssid = String::new();
        let mut status_active = false;

        for line in stdout.lines() {
            let trimmed = line.trim();
            if trimmed.starts_with("SSID name") {
                if let Some(val) = trimmed.split(':').nth(1) {
                    ssid = val.trim().trim_matches('"').to_string();
                }
            }
            if trimmed.starts_with("Status") && trimmed.contains("Started") {
                status_active = true;
            }
        }

        if !ssid.is_empty() {
            Some(HotspotInfo {
                ssid,
                password: String::new(),
                band: "Auto".to_string(),
                gateway_ip: detect_hotspot_gateway().await.unwrap_or("192.168.137.1".to_string()),
                is_active: status_active,
            })
        } else {
            None
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        None
    }
}

/// Detect the hotspot gateway IP by looking for the 192.168.137.x interface.
pub async fn detect_hotspot_gateway() -> Option<String> {
    if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
        for (_, ip) in interfaces {
            let ip_str = ip.to_string();
            if ip_str.starts_with("192.168.137.") {
                return Some("192.168.137.1".to_string());
            }
        }
    }
    None
}

/// Wait for a device to connect to the hotspot by polling for new IPs
/// in the 192.168.137.x subnet.
pub async fn wait_for_hotspot_client(timeout: Duration) -> Option<String> {
    let start = std::time::Instant::now();
    while start.elapsed() < timeout {
        if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
            for (_name, ip) in &interfaces {
                let ip_str = ip.to_string();
                // Look for the hotspot adapter with our gateway IP
                if ip_str == "192.168.137.1" {
                    // Try ARP table to find connected clients
                    if let Some(client_ip) = find_hotspot_client_arp().await {
                        return Some(client_ip);
                    }
                }
            }
        }
        tokio::time::sleep(Duration::from_secs(1)).await;
    }
    None
}

/// Check ARP table for devices in 192.168.137.x subnet.
pub async fn find_hotspot_client_arp() -> Option<String> {
    #[cfg(target_os = "windows")]
    {
        let output = tokio::process::Command::new("arp")
            .arg("-a")
            .output()
            .await
            .ok()?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        for line in stdout.lines() {
            let trimmed = line.trim();
            if trimmed.starts_with("192.168.137.") && !trimmed.starts_with("192.168.137.1 ") && !trimmed.starts_with("192.168.137.255") {
                if let Some(ip) = trimmed.split_whitespace().next() {
                    if ip != "192.168.137.1" && ip != "192.168.137.255" {
                        return Some(ip.to_string());
                    }
                }
            }
        }
        None
    }
    #[cfg(not(target_os = "windows"))]
    {
        None
    }
}

/// Generate a random ShareDash hotspot SSID and password.
pub fn generate_hotspot_credentials() -> (String, String) {
    let suffix: String = (0..4)
        .map(|_| {
            let idx = rand::random::<u8>() % 36;
            if idx < 10 {
                (b'0' + idx) as char
            } else {
                (b'A' + idx - 10) as char
            }
        })
        .collect();

    let password: String = (0..8)
        .map(|_| {
            let idx = rand::random::<u8>() % 62;
            if idx < 10 {
                (b'0' + idx) as char
            } else if idx < 36 {
                (b'A' + idx - 10) as char
            } else {
                (b'a' + idx - 36) as char
            }
        })
        .collect();

    (format!("ShareDash-5G-{}", suffix), password)
}

/// Connect the PC's Wi-Fi adapter to a phone-hosted hotspot.
///
/// Creates a WLAN profile and connects via `netsh wlan`.
pub async fn connect_to_phone_hotspot(ssid: &str, password: &str) -> Result<bool> {
    #[cfg(target_os = "windows")]
    {
        // Create a WLAN profile XML for the phone hotspot
        let profile_xml = format!(
            r#"<?xml version="1.0"?>
<WLANProfile xmlns="http://www.microsoft.com/networking/WLAN/profile/v1">
    <name>{ssid}</name>
    <SSIDConfig>
        <SSID>
            <name>{ssid}</name>
        </SSID>
    </SSIDConfig>
    <connectionType>ESS</connectionType>
    <connectionMode>manual</connectionMode>
    <MSM>
        <security>
            <authEncryption>
                <authentication>WPA2PSK</authentication>
                <encryption>AES</encryption>
                <useOneX>false</useOneX>
            </authEncryption>
            <sharedKey>
                <keyType>passPhrase</keyType>
                <protected>false</protected>
                <keyMaterial>{password}</keyMaterial>
            </sharedKey>
        </security>
    </MSM>
</WLANProfile>"#,
            ssid = ssid,
            password = password
        );

        // Write the profile XML to a temp file
        let temp_dir = std::env::temp_dir();
        let profile_path = temp_dir.join("sharedash_phone_hotspot.xml");
        tokio::fs::write(&profile_path, &profile_xml).await?;

        // Add the WLAN profile
        let add_result = tokio::process::Command::new("netsh")
            .args([
                "wlan",
                "add",
                "profile",
                &format!("filename={}", profile_path.display()),
            ])
            .output()
            .await;

        match add_result {
            Ok(output) => {
                let stdout = String::from_utf8_lossy(&output.stdout);
                tracing::info!("WLAN profile add: {}", stdout.trim());
            }
            Err(e) => {
                tracing::warn!("Failed to add WLAN profile: {}", e);
            }
        }

        // Connect to the network
        let connect_result = tokio::process::Command::new("netsh")
            .args(["wlan", "connect", &format!("name={}", ssid)])
            .output()
            .await;

        // Clean up temp file
        let _ = tokio::fs::remove_file(&profile_path).await;

        match connect_result {
            Ok(output) => {
                let stdout = String::from_utf8_lossy(&output.stdout);
                tracing::info!("WLAN connect: {}", stdout.trim());
                Ok(stdout.contains("successfully") || output.status.success())
            }
            Err(e) => {
                tracing::warn!("Failed to connect to phone hotspot: {}", e);
                Ok(false)
            }
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = (ssid, password);
        Ok(false)
    }
}

/// Wait for the PC to obtain an IP on the phone hotspot subnet (192.168.43.x or 192.168.49.x).
pub async fn wait_for_phone_hotspot_interface(timeout: Duration) -> Option<String> {
    let start = std::time::Instant::now();
    while start.elapsed() < timeout {
        if let Some(gateway) = detect_phone_hotspot_gateway().await {
            return Some(gateway);
        }
        tokio::time::sleep(Duration::from_secs(1)).await;
    }
    None
}

/// Detect if the PC is connected to a phone hotspot by looking for 192.168.43.x or 192.168.49.x interfaces.
/// Returns the phone's gateway IP (typically 192.168.43.1 or 192.168.49.1).
pub async fn detect_phone_hotspot_gateway() -> Option<String> {
    if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
        for (_, ip) in interfaces {
            let ip_str = ip.to_string();
            if ip_str.starts_with("192.168.43.") && ip_str != "192.168.43.1" {
                return Some("192.168.43.1".to_string());
            }
            if ip_str.starts_with("192.168.49.") && ip_str != "192.168.49.1" {
                return Some("192.168.49.1".to_string());
            }
        }
    }
    None
}

/// Detect a phone connected via USB tethering (RNDIS/NCM) without requiring USB debugging.
///
/// Strictly inspects active USB/RNDIS network interfaces on the host OS to prevent false positives
/// from standard Wi-Fi LAN or Ethernet connections. Returns (phone_ip, device_name) if found.
pub async fn detect_usb_tethering_peer_detailed() -> Option<(String, String)> {
    let mut candidate_ips: Vec<String> = Vec::new();
    let mut local_ips: Vec<String> = Vec::new();
    let mut usb_detected = false;

    #[cfg(target_os = "windows")]
    {
        // Query active network adapters on Windows specifically for USB/RNDIS/NCM adapters
        let ps_cmd = r#"Get-NetAdapter | Where-Object Status -eq 'Up' | ForEach-Object { $desc = $_.InterfaceDescription; $name = $_.Name; $idx = $_.InterfaceIndex; $isUsb = ($desc -match 'Remote NDIS|RNDIS|Samsung Mobile USB|CDC NCM|Apple Mobile Device|USB Ethernet|USB NDIS|USB 10/100|Tethering') -or ($name -match 'USB|RNDIS|NCM|Tether' -and $desc -notmatch 'Virtual|Wi-Fi|Wireless|Bluetooth|Hyper-V|WSL|TAP'); $isExcluded = $desc -match 'Wi-Fi|Wireless|802.11|VirtualBox|VMware|Hyper-V|WSL|Bluetooth|Loopback|TAP'; if ($isUsb -and -not $isExcluded) { $ip = (Get-NetIPAddress -InterfaceIndex $idx -AddressFamily IPv4 -ErrorAction SilentlyContinue | Select-Object -First 1).IPAddress; $gw = (Get-NetRoute -InterfaceIndex $idx -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Select-Object -First 1).NextHop; Write-Output "$idx|$name|$desc|$ip|$gw" } }"#;

        if let Ok(output) = tokio::process::Command::new("powershell")
            .args(["-NoProfile", "-Command", ps_cmd])
            .output()
            .await
        {
            if output.status.success() {
                let text = String::from_utf8_lossy(&output.stdout);
                for line in text.lines() {
                    let trimmed = line.trim();
                    if trimmed.is_empty() {
                        continue;
                    }
                    let parts: Vec<&str> = trimmed.split('|').collect();
                    if parts.len() >= 5 {
                        let ip = parts[3].trim();
                        let gw = parts[4].trim();
                        if !ip.is_empty() {
                            local_ips.push(ip.to_string());
                            usb_detected = true;
                            if let Ok(v4) = ip.parse::<std::net::Ipv4Addr>() {
                                let octets = v4.octets();
                                let prefix = format!("{}.{}.{}", octets[0], octets[1], octets[2]);
                                candidate_ips.push(format!("{}.1", prefix));
                                candidate_ips.push(format!("{}.129", prefix));
                                candidate_ips.push(format!("{}.63", prefix));
                                candidate_ips.push(format!("{}.254", prefix));
                            }
                        }
                        if !gw.is_empty() && gw != "0.0.0.0" {
                            candidate_ips.insert(0, gw.to_string());
                            usb_detected = true;
                        }
                    }
                }
            }
        }
    }

    #[cfg(not(target_os = "windows"))]
    {
        if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
            for (name, ip) in &interfaces {
                let name_lower = name.to_lowercase();
                let is_usb = (name_lower.starts_with("usb")
                    || name_lower.starts_with("rndis")
                    || name_lower.starts_with("ncm")
                    || name_lower.contains("usb"))
                    && !name_lower.contains("wlan")
                    && !name_lower.contains("wifi")
                    && !name_lower.contains("vir");

                if is_usb {
                    if let std::net::IpAddr::V4(v4) = ip {
                        if !v4.is_loopback() {
                            local_ips.push(ip.to_string());
                            usb_detected = true;
                            let octets = v4.octets();
                            let prefix = format!("{}.{}.{}", octets[0], octets[1], octets[2]);
                            candidate_ips.push(format!("{}.1", prefix));
                            candidate_ips.push(format!("{}.129", prefix));
                            candidate_ips.push(format!("{}.254", prefix));
                        }
                    }
                }
            }
        }
    }

    // STRICT CHECK: If no USB network adapter was detected, do NOT probe any general Wi-Fi / ARP IPs!
    if !usb_detected || candidate_ips.is_empty() {
        return None;
    }

    // Deduplicate and filter out local machine IPs
    let mut unique_candidates = Vec::new();
    for ip in candidate_ips {
        if !local_ips.contains(&ip) && !ip.starts_with("127.") && !unique_candidates.contains(&ip) {
            unique_candidates.push(ip);
        }
    }

    let client = reqwest::Client::builder()
        .timeout(Duration::from_millis(600))
        .build()
        .unwrap_or_default();

    // Concurrently probe candidate IPs strictly on the USB subnet
    let mut tasks = Vec::new();
    for ip in unique_candidates {
        let client_ref = client.clone();
        tasks.push(tokio::spawn(async move {
            let url = format!("http://{}:54321/api/v1/info", ip);
            if let Ok(resp) = client_ref.get(&url).send().await {
                if resp.status().is_success() {
                    if let Ok(json) = resp.json::<serde_json::Value>().await {
                        let name = json.get("device_name")
                            .and_then(|v| v.as_str())
                            .unwrap_or("Android Device")
                            .to_string();
                        return Some((ip, name));
                    }
                }
            }
            None
        }));
    }

    for task in tasks {
        if let Ok(Some(result)) = task.await {
            return Some(result);
        }
    }

    None
}

/// Detect a phone connected via USB tethering (RNDIS/NCM) without requiring USB debugging.
/// Returns the phone's IP on the tethering subnet if found.
pub async fn detect_usb_tethering_peer() -> Option<String> {
    detect_usb_tethering_peer_detailed().await.map(|(ip, _)| ip)
}
