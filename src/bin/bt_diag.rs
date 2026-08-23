use btleplug::api::{Central, Manager as _, Peripheral, ScanFilter, WriteType};
use btleplug::platform::Manager;
use std::time::Duration;
use tokio::time::sleep;
use uuid::Uuid as BleUuid;

pub const SHAREDASH_BLE_SERVICE_UUID: &str = "00005344-0000-1000-8000-00805f9b34fb";
pub const WIFI_CAPS_CHAR_UUID: &str = "00005345-0000-1000-8000-00805f9b34fb";
pub const COMMAND_CHAR_UUID: &str = "00005346-0000-1000-8000-00805f9b34fb";
pub const RESPONSE_CHAR_UUID: &str = "00005347-0000-1000-8000-00805f9b34fb";

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("=== Bluetooth Diagnostic & Communication Test ===");
    let manager = Manager::new().await?;
    let adapters = manager.adapters().await?;
    let adapter = adapters.into_iter().next().expect("No adapter");

    let sd_uuid = BleUuid::parse_str(SHAREDASH_BLE_SERVICE_UUID)?;
    let caps_uuid = BleUuid::parse_str(WIFI_CAPS_CHAR_UUID)?;
    let cmd_uuid = BleUuid::parse_str(COMMAND_CHAR_UUID)?;
    let resp_uuid = BleUuid::parse_str(RESPONSE_CHAR_UUID)?;

    println!("Scanning for ShareDash devices...");
    adapter.start_scan(ScanFilter::default()).await?;
    sleep(Duration::from_secs(5)).await;

    let peripherals = adapter.peripherals().await?;
    println!("Checking {} discovered peripherals...", peripherals.len());

    let mut found = false;
    for p in peripherals {
        if let Ok(Some(props)) = p.properties().await {
            let matches = props.services.iter().any(|s| *s == sd_uuid)
                || props.service_data.contains_key(&sd_uuid)
                || props.local_name.as_ref().map(|n| n.contains("A56") || n.contains("ShareDash") || n.contains("Sam")).unwrap_or(false);

            if matches {
                found = true;
                println!("\n==================================================");
                println!("🎯 Found Target Device: {:?} ({:?})", props.local_name, p.id());
                println!("   RSSI: {:?}", props.rssi);
                println!("   Services Advertised: {:?}", props.services);
                println!("   Service Data: {:?}", props.service_data);

                println!("\n🔗 Connecting to peripheral...");
                if let Err(e) = p.connect().await {
                    println!("❌ Connect failed: {:?}", e);
                    continue;
                }
                println!("✅ Connected successfully!");

                sleep(Duration::from_millis(200)).await;

                println!("🔍 Discovering GATT services...");
                if let Err(e) = p.discover_services().await {
                    println!("❌ Service discovery failed: {:?}", e);
                    let _ = p.disconnect().await;
                    continue;
                }

                let chars = p.characteristics();
                println!("📋 Characteristics found: {}", chars.len());
                let has_sd_service = chars.iter().any(|c| c.uuid == cmd_uuid || c.uuid == caps_uuid || c.uuid == resp_uuid);
                println!("   ShareDash GATT Service present: {}", if has_sd_service { "YES ✅" } else { "NO ❌" });

                for c in &chars {
                    if c.uuid == caps_uuid || c.uuid == cmd_uuid || c.uuid == resp_uuid {
                        println!("   -> ShareDash Char: {:?}, properties: {:?}", c.uuid, c.properties);
                    }
                }

                // 1. Test Read WIFI_CAPS
                if let Some(c) = chars.iter().find(|c| c.uuid == caps_uuid) {
                    println!("\n📖 Test 1: Reading WIFI_CAPS_CHAR...");
                    match p.read(c).await {
                        Ok(data) => println!("   ✅ Read Success: {}", String::from_utf8_lossy(&data)),
                        Err(e) => println!("   ❌ Read Failed: {:?}", e),
                    }
                }

                // 2. Test Write PING Command
                if let Some(c) = chars.iter().find(|c| c.uuid == cmd_uuid) {
                    println!("\n✉️ Test 2: Writing PING command to COMMAND_CHAR...");
                    let cmd = r#"{"cmd":"ping"}"#;
                    match p.write(c, cmd.as_bytes(), WriteType::WithResponse).await {
                        Ok(_) => println!("   ✅ Write (WithResponse) Success!"),
                        Err(e) => {
                            println!("   ⚠️ WithResponse failed ({:?}), trying WithoutResponse...", e);
                            match p.write(c, cmd.as_bytes(), WriteType::WithoutResponse).await {
                                Ok(_) => println!("   ✅ Write (WithoutResponse) Success!"),
                                Err(e2) => println!("   ❌ Write Failed: {:?}", e2),
                            }
                        }
                    }
                }

                // 3. Test Read Response
                if let Some(c) = chars.iter().find(|c| c.uuid == resp_uuid) {
                    println!("\n📥 Test 3: Reading response from RESPONSE_CHAR...");
                    sleep(Duration::from_millis(400)).await;
                    match p.read(c).await {
                        Ok(data) => println!("   ✅ Response Received: {}", String::from_utf8_lossy(&data)),
                        Err(e) => println!("   ❌ Response Read Failed: {:?}", e),
                    }
                }

                let _ = p.disconnect().await;
                println!("\n🔌 Disconnected gracefully.");
                break;
            }
        }
    }

    if !found {
        println!("⚠️ No ShareDash device detected in scan.");
    }

    Ok(())
}
