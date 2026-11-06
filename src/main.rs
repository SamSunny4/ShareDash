use clap::Parser;
use std::path::PathBuf;
use std::sync::Arc;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use uuid::Uuid;

use sharedash::gui::ShareDashGui;
use sharedash::server::Server;
use sharedash::TerminalCli;

#[derive(Parser, Debug)]
#[command(name = "sharedash")]
#[command(about = "High-Performance Multipath Local + Internet File Transfer Engine", long_about = None)]
struct Args {
    /// Port to listen on for HTTP & WebSockets
    #[arg(short, long, default_value_t = 54321)]
    port: u16,

    /// Friendly device name
    #[arg(long, default_value = "ShareDash Desktop")]
    name: String,

    /// SQLite Manifest Database path
    #[arg(long, default_value = "./sharedash_data/manifest.db")]
    db_path: PathBuf,

    /// Run in terminal CLI wizard mode instead of Windows GUI
    #[arg(long)]
    cli: bool,
}

fn main() -> anyhow::Result<()> {
    // Initialize tracing subscriber
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "sharedash=warn,tower_http=warn".into()),
        )
        .with(tracing_subscriber::fmt::layer().with_target(false).without_time())
        .init();

    let args = Args::parse();

    // Stable Persistent Device ID (prevents ghost duplicate devices on Android)
    let device_id_file = std::path::PathBuf::from("./sharedash_data/device_id.txt");
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
        args.port,
        device_id,
        args.name,
        args.db_path,
    );

    let rt = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;

    if args.cli {
        // Run terminal CLI wizard
        rt.block_on(async {
            server.run().await
        })?;
    } else {
        // Run native Windows GUI application
        let (state, _server_handle) = rt.block_on(async {
            server.start_background().await
        })?;

        let cli = Arc::new(TerminalCli::new(state.clone()));
        let cli_gui = cli.clone();
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
                Ok(Box::new(ShareDashGui::new(cc, state, cli_gui, tokio_handle)))
            }),
        ) {
            eprintln!("Error launching ShareDash GUI: {}. Falling back to CLI mode...", e);
            rt.block_on(async {
                cli.run_interactive_loop().await;
            });
        }
    }

    Ok(())
}
