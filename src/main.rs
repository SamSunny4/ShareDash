use clap::Parser;
use std::path::PathBuf;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use uuid::Uuid;

use sharedash::server::Server;

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
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize tracing subscriber with clean terminal output
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

    server.run().await?;
    Ok(())
}
