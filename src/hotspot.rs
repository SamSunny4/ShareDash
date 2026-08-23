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

/// Check if the PC has an active internet connection profile.
///
/// On Windows, `NetworkOperatorTetheringManager` requires an active Internet Connection Profile (ICS)
/// to start a Mobile Hotspot. Without an internet profile, Windows Hotspot fails immediately.
pub async fn check_pc_internet_connection() -> bool {
    #[cfg(target_os = "windows")]
    {
        let ps_script = r#"
            try {
                $profile = [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType = WindowsRuntime]::GetInternetConnectionProfile()
                if ($profile) {
                    $level = $profile.GetNetworkConnectivityLevel()
                    if ($level -eq [Windows.Networking.Connectivity.NetworkConnectivityLevel]::InternetAccess) {
                        Write-Output "INTERNET_OK"
                    } else {
                        Write-Output "NO_INTERNET"
                    }
                } else {
                    Write-Output "NO_INTERNET"
                }
            } catch {
                Write-Output "NO_INTERNET"
            }
        "#;

        if let Ok(output) = tokio::process::Command::new("powershell")
            .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps_script])
            .output()
            .await
        {
            let stdout = String::from_utf8_lossy(&output.stdout);
            if stdout.contains("INTERNET_OK") {
                return true;
            }
        }
    }

    // Fast TCP connectivity probe as fallback (DNS root servers)
    let probe_targets = ["1.1.1.1:53", "8.8.8.8:53"];
    for target in probe_targets {
        if let Ok(Ok(_)) = tokio::time::timeout(
            Duration::from_millis(500),
            tokio::net::TcpStream::connect(target),
        ).await {
            return true;
        }
    }

    false
}

/// Check if the PC is capable of creating a 5GHz band Mobile Hotspot right now.
pub async fn check_pc_5ghz_hotspot_available(has_internet: bool) -> bool {
    if !has_internet {
        // Windows Mobile Hotspot requires an active internet connection to share
        return false;
    }

    #[cfg(target_os = "windows")]
    {
        // Query Wi-Fi drivers and hosted network support
        if let Ok(output) = tokio::process::Command::new("netsh")
            .args(["wlan", "show", "drivers"])
            .output()
            .await
        {
            let text = String::from_utf8_lossy(&output.stdout);
            let supports_5g = text.contains("802.11ac") || text.contains("802.11ax") || text.contains("802.11be") || text.contains("5 GHz");
            return supports_5g;
        }
    }

    false
}

/// Select the optimal device to host the hotspot based on PC internet connectivity,
/// 5GHz capability, and hardware PHY specs.
pub async fn select_optimal_hotspot_host(
    pc_caps: &WifiCapsInfo,
    phone_caps: &WifiCapsInfo,
) -> (HotspotHostChoice, String) {
    let has_internet = check_pc_internet_connection().await;
    if !has_internet {
        return (
            HotspotHostChoice::Phone,
            "PC has no active internet connection (Windows Mobile Hotspot requires internet sharing). Phone will host 5GHz Hotspot.".to_string(),
        );
    }

    let pc_can_5g_hotspot = check_pc_5ghz_hotspot_available(has_internet).await;
    if !pc_can_5g_hotspot {
        return (
            HotspotHostChoice::Phone,
            "PC 5GHz hotspot is unavailable on the current network interface. Phone will host 5GHz Hotspot.".to_string(),
        );
    }

    let pc_score = pc_caps.max_phy_rate_mbps + if pc_caps.supported_bands.iter().any(|b| b.contains("6 GHz")) { 500 } else { 0 };
    let phone_score = phone_caps.max_phy_rate_mbps + if phone_caps.supported_bands.iter().any(|b| b.contains("6 GHz")) { 500 } else { 0 };

    if pc_score >= phone_score {
        (
            HotspotHostChoice::Pc,
            "PC Wi-Fi selected as Primary 5GHz Hotspot Host (Internet active & superior PHY rate)".to_string(),
        )
    } else {
        (
            HotspotHostChoice::Phone,
            "Phone Wi-Fi selected as Primary 5GHz Hotspot Host (Superior PHY rate/bands)".to_string(),
        )
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

/// Fast parallel scan for connected hotspot client in 192.168.137.x subnet (< 300ms).
pub async fn fast_scan_hotspot_clients(port: u16) -> Option<String> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_millis(300))
        .build()
        .unwrap_or_default();

    // 1. Check ARP table first
    if let Some(arp_ip) = find_hotspot_client_arp().await {
        let url = format!("http://{}:{}/api/v1/info", arp_ip, port);
        if let Ok(resp) = client.get(&url).send().await {
            if resp.status().is_success() {
                return Some(arp_ip);
            }
        }
    }

    // 2. Parallel scan candidates .2 through .25 concurrently in 300ms
    let mut tasks = Vec::new();
    for last_octet in 2..=25 {
        let cand_ip = format!("192.168.137.{}", last_octet);
        let c = client.clone();
        tasks.push(async move {
            let url = format!("http://{}:{}/api/v1/info", cand_ip, port);
            if let Ok(resp) = c.get(&url).send().await {
                if resp.status().is_success() {
                    return Some(cand_ip);
                }
            }
            None
        });
    }

    let results = futures::future::join_all(tasks).await;
    for res in results {
        if let Some(ip) = res {
            return Some(ip);
        }
    }
    None
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

/// Open Windows Wi-Fi Settings directly for the user.
pub fn open_windows_wifi_settings() {
    #[cfg(target_os = "windows")]
    {
        let _ = std::process::Command::new("cmd")
            .args(["/c", "start", "ms-settings:network-wifi"])
            .spawn();
    }
}

/// Check if the PC's Wi-Fi adapter is enabled and turned on.
pub async fn check_pc_wifi_adapter_enabled() -> bool {
    #[cfg(target_os = "windows")]
    {
        // 1. Check via netsh interface show interface (fastest and doesn't require admin)
        if let Ok(output) = tokio::process::Command::new("netsh")
            .args(["interface", "show", "interface"])
            .output()
            .await
        {
            let stdout = String::from_utf8_lossy(&output.stdout);
            for line in stdout.lines() {
                let lower = line.to_lowercase();
                if lower.contains("wi-fi") || lower.contains("wireless") || lower.contains("wlan") {
                    if lower.contains("enabled") {
                        return true;
                    } else if lower.contains("disabled") {
                        return false;
                    }
                }
            }
        }

        // 2. Check via PowerShell Get-NetAdapter
        let ps_script = r#"
            $adapter = Get-NetAdapter | Where-Object { ($_.InterfaceDescription -match 'Wi-Fi|Wireless|802.11|Intel|Qualcomm|Realtek|MediaTek' -or $_.Name -match 'Wi-Fi') -and ($_.Status -ne 'Disabled') }
            if ($adapter) {
                Write-Output "WIFI_ON"
            } else {
                Write-Output "WIFI_OFF"
            }
        "#;
        if let Ok(output) = tokio::process::Command::new("powershell")
            .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps_script])
            .output()
            .await
        {
            let stdout = String::from_utf8_lossy(&output.stdout);
            return stdout.contains("WIFI_ON");
        }
    }
    true
}

/// Ensure the PC's Wi-Fi adapter is enabled and ready to connect.
pub async fn ensure_pc_wifi_adapter_enabled() -> bool {
    #[cfg(target_os = "windows")]
    {
        if !check_pc_wifi_adapter_enabled().await {
            tracing::info!("PC Wi-Fi adapter is disabled. Enabling Wi-Fi adapter...");
            let ps_script = r#"
                try {
                    Get-NetAdapter | Where-Object { $_.InterfaceDescription -match 'Wi-Fi|Wireless|802.11|Intel|Qualcomm|Realtek|MediaTek' -or $_.Name -match 'Wi-Fi' } | Enable-NetAdapter -Confirm:$false -ErrorAction SilentlyContinue
                } catch {}
            "#;
            let _ = tokio::process::Command::new("powershell")
                .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps_script])
                .output()
                .await;
            tokio::time::sleep(Duration::from_millis(600)).await;
            return check_pc_wifi_adapter_enabled().await;
        }
    }
    true
}

#[cfg(target_os = "windows")]
async fn apply_and_connect_profile(ssid: &str, password: &str, auth_type: &str, profile_path: &std::path::Path) -> bool {
    let security_section = if password.is_empty() {
        r#"<security>
            <authEncryption>
                <authentication>open</authentication>
                <encryption>none</encryption>
                <useOneX>false</useOneX>
            </authEncryption>
        </security>"#.to_string()
    } else {
        format!(
            r#"<security>
            <authEncryption>
                <authentication>{auth_type}</authentication>
                <encryption>AES</encryption>
                <useOneX>false</useOneX>
            </authEncryption>
            <sharedKey>
                <keyType>passPhrase</keyType>
                <protected>false</protected>
                <keyMaterial>{password}</keyMaterial>
            </sharedKey>
        </security>"#,
            auth_type = auth_type,
            password = password
        )
    };

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
        {security}
    </MSM>
</WLANProfile>"#,
        ssid = ssid,
        security = security_section
    );

    let _ = tokio::fs::write(profile_path, &profile_xml).await;
    let _ = tokio::process::Command::new("netsh")
        .args([
            "wlan",
            "add",
            "profile",
            &format!("filename={}", profile_path.display()),
        ])
        .output()
        .await;

    let connect_result = tokio::process::Command::new("netsh")
        .args(["wlan", "connect", &format!("name={}", ssid)])
        .output()
        .await;

    if let Ok(out) = connect_result {
        let stdout = String::from_utf8_lossy(&out.stdout);
        tracing::info!("WLAN connect ({}) result: {}", auth_type, stdout.trim());
        stdout.contains("successfully") || out.status.success()
    } else {
        false
    }
}

/// Connect the PC's Wi-Fi adapter to a phone-hosted hotspot.
///
/// Creates a WLAN profile (supporting both WPA2PSK and WPA3-Personal SAE) and connects via `netsh wlan`.
pub async fn connect_to_phone_hotspot(ssid: &str, password: &str) -> Result<bool> {
    #[cfg(target_os = "windows")]
    {
        ensure_pc_wifi_adapter_enabled().await;

        let temp_dir = std::env::temp_dir();
        let profile_path = temp_dir.join("sharedash_phone_hotspot.xml");

        // Try WPA2PSK first
        let mut ok = apply_and_connect_profile(ssid, password, "WPA2PSK", &profile_path).await;

        // If not connected and password is provided, try WPA3SAE (for Android 12+ WPA3 hotspots)
        if !ok && !password.is_empty() {
            tracing::info!("Trying WPA3SAE profile for {}", ssid);
            ok = apply_and_connect_profile(ssid, password, "WPA3SAE", &profile_path).await;
        }

        let _ = tokio::fs::remove_file(&profile_path).await;
        Ok(ok)
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = (ssid, password);
        Ok(false)
    }
}

/// Wait for the PC to obtain an IP on the phone hotspot subnet.
pub async fn wait_for_phone_hotspot_interface(timeout: Duration, expected_gateway: Option<&str>) -> Option<String> {
    let start = std::time::Instant::now();
    let client = reqwest::Client::builder()
        .timeout(Duration::from_millis(600))
        .build()
        .unwrap_or_default();

    while start.elapsed() < timeout {
        // 1. If an expected gateway IP was returned by the phone, check if it's reachable
        if let Some(gw) = expected_gateway {
            let url = format!("http://{}:54321/api/v1/info", gw);
            if let Ok(resp) = client.get(&url).send().await {
                if resp.status().is_success() {
                    return Some(gw.to_string());
                }
            }
        }

        // 2. Detect gateway from local network interfaces
        if let Some(gateway) = detect_phone_hotspot_gateway().await {
            return Some(gateway);
        }

        tokio::time::sleep(Duration::from_millis(800)).await;
    }
    None
}

/// Detect if the PC is connected to a phone hotspot by looking for candidate gateway interfaces.
/// Returns the phone's gateway IP (typically 192.168.43.1, 192.168.49.1, 172.20.10.1, etc.).
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
            if ip_str.starts_with("172.20.10.") && ip_str != "172.20.10.1" {
                return Some("172.20.10.1".to_string());
            }
            if ip_str.starts_with("192.168.50.") && ip_str != "192.168.50.1" {
                return Some("192.168.50.1".to_string());
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
