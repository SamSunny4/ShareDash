use std::path::PathBuf;
use std::sync::Arc;
use clap::Parser;
use gpui::*;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

use sharedash::gui::{GuiEngine, ShareDashApp};

#[derive(Parser, Debug)]
#[command(name = "sharedash-gui")]
#[command(about = "ShareDash Native Windows GUI — High-Performance Multipath Transfer Engine")]
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

fn main() -> anyhow::Result<()> {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "sharedash=warn,tower_http=warn".into()),
        )
        .with(tracing_subscriber::fmt::layer().with_target(false).without_time())
        .init();

    let args = Args::parse();

    // Start background Tokio multi-thread runtime for the networking engine
    let rt = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;

    let engine = rt.block_on(async {
        GuiEngine::start(args.port, args.name, args.db_path).await
    })?;

    let engine_arc = Arc::new(engine);

    // Keep Tokio runtime entered on the main thread for the application lifetime
    let rt_leak = Box::leak(Box::new(rt));
    let _enter_guard = Box::leak(Box::new(rt_leak.enter()));

    // Launch GPUI native Windows Application
    Application::new().run(move |cx: &mut App| {
        let engine_clone = engine_arc.clone();

        let bounds = Bounds::centered(None, size(px(1020.0), px(720.0)), cx);
        let options = WindowOptions {
            window_bounds: Some(WindowBounds::Windowed(bounds)),
            titlebar: Some(TitlebarOptions {
                title: Some("ShareDash — Multipath File Transfer Engine".into()),
                ..Default::default()
            }),
            ..Default::default()
        };

        cx.open_window(options, move |_window, cx| {
            cx.new(|cx| ShareDashApp::new(engine_clone, cx))
        })
        .unwrap();
    });

    Ok(())
}
