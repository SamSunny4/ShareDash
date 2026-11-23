use std::process::Output;
use std::time::Duration;

use tokio::process::Command;

pub(super) const PHONE_PORT: u16 = 54321;
pub(super) const BRIDGE_PORT: u16 = 54325;

async fn run_command(command: &mut Command) -> Option<Output> {
    #[cfg(windows)]
    command.creation_flags(0x08000000); // CREATE_NO_WINDOW

    let output = tokio::time::timeout(Duration::from_secs(4), command.kill_on_drop(true).output())
        .await
        .ok()?
        .ok()?;
    if output.status.success() {
        Some(output)
    } else {
        tracing::debug!(
            "ADB command failed: {}",
            String::from_utf8_lossy(&output.stderr).trim()
        );
        None
    }
}

pub async fn find() -> Option<String> {
    let sdk_adb = std::env::var("LOCALAPPDATA")
        .ok()
        .map(|path| format!("{path}\\Android\\Sdk\\platform-tools\\adb.exe"));
    for candidate in sdk_adb.as_deref().into_iter().chain(["adb"]) {
        if run_command(Command::new(candidate).arg("version"))
            .await
            .is_some()
        {
            return Some(candidate.to_owned());
        }
    }
    None
}

pub async fn devices(adb: &str) -> Vec<String> {
    let Some(output) = run_command(Command::new(adb).arg("devices")).await else {
        return Vec::new();
    };
    String::from_utf8_lossy(&output.stdout)
        .lines()
        .filter_map(|line| {
            let mut fields = line.split_whitespace();
            let serial = fields.next()?;
            (fields.next() == Some("device")).then(|| serial.to_owned())
        })
        .collect()
}

pub async fn run_on_device(adb: &str, serial: &str, args: &[&str]) -> Option<Output> {
    run_command(Command::new(adb).args(["-s", serial]).args(args)).await
}

fn has_mapping(output: Output, source: &str, destination: &str) -> bool {
    String::from_utf8_lossy(&output.stdout).lines().any(|line| {
        let mut fields = line.split_whitespace().rev();
        fields.next() == Some(destination) && fields.next() == Some(source)
    })
}

pub async fn ensure_reverse(adb: &str, serial: &str, server_port: u16) {
    let bridge = format!("tcp:{BRIDGE_PORT}");
    let desktop_server = format!("tcp:{server_port}");
    if run_on_device(adb, serial, &["reverse", "--list"])
        .await
        .is_some_and(|output| has_mapping(output, &bridge, &desktop_server))
    {
        return;
    }
    // Android listens on 54321 itself and sends to the desktop through 54325.
    run_on_device(adb, serial, &["reverse", &bridge, &desktop_server]).await;
}

pub async fn ensure_forward(adb: &str, serial: &str) -> bool {
    let bridge = format!("tcp:{BRIDGE_PORT}");
    let phone_server = format!("tcp:{PHONE_PORT}");
    if run_on_device(adb, serial, &["forward", "--list"])
        .await
        .is_some_and(|output| {
            String::from_utf8_lossy(&output.stdout).lines().any(|line| {
                let mut fields = line.split_whitespace();
                fields.next() == Some(serial)
                    && fields.next() == Some(bridge.as_str())
                    && fields.next() == Some(phone_server.as_str())
            })
        })
    {
        return true;
    }
    run_on_device(adb, serial, &["forward", &bridge, &phone_server])
        .await
        .is_some()
}
