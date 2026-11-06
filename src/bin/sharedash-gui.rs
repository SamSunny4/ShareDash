use std::path::PathBuf;
use std::sync::Arc;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use uuid::Uuid;

use sharedash::gui::ShareDashGui;
use sharedash::server::Server;
use sharedash::TerminalCli;

fn main() -> anyhow::Result<()> {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "sharedash=warn,tower_http=warn".into()),
        )
        .with(tracing_subscriber::fmt::layer().with_target(false).without_time())
        .init();

    let device_id_file = PathBuf::from("./sharedash_data/device_id.txt");
    let device_id = if let Ok(existing_id) = std::fs::read_to_string(&device_id_file) {
        let trimmed = existing_id.trim().to_string();
        if !trimmed.is_empty() {
            trimmed
        } else {
            let new_id = Uuid::new_v4().to_string();
            let _ = std::fs::create_dir_all("./sharedash_data");
            let _ = std::fs::write(&device_id_file, &new_id);
            new_id
        }
    } else {
        let new_id = Uuid::new_v4().to_string();
        let _ = std::fs::create_dir_all("./sharedash_data");
        let _ = std::fs::write(&device_id_file, &new_id);
        new_id
    };

    let server = Server::new(
        54321,
        device_id,
        "ShareDash Desktop".to_string(),
        PathBuf::from("./sharedash_data/manifest.db"),
    );

    let rt = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;

    let (state, _server_handle) = rt.block_on(async {
        server.start_background().await
    })?;

    let cli = Arc::new(TerminalCli::new(state.clone()));
    let tokio_handle = rt.handle().clone();

    let native_options = eframe::NativeOptions {
        viewport: eframe::egui::ViewportBuilder::default()
            .with_inner_size([1020.0, 720.0])
            .with_min_inner_size([820.0, 600.0])
            .with_title("ShareDash — Multipath Offline P2P Transfer Engine")
            .with_drag_and_drop(true),
        ..Default::default()
    };

    if let Err(e) = eframe::run_native(
        "ShareDash",
        native_options,
        Box::new(move |cc| {
            Ok(Box::new(ShareDashGui::new(cc, state, cli, tokio_handle)))
        }),
    ) {
        eprintln!("ShareDash GUI exited with error: {}", e);
    }

    Ok(())
}
