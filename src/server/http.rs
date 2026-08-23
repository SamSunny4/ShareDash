use axum::{
    routing::{get, post},
    Router,
};
use parking_lot::Mutex;
use std::collections::HashMap;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use tower_http::cors::{Any, CorsLayer};
use axum::http::{header, StatusCode, Uri};
use axum::response::IntoResponse;
use rust_embed::RustEmbed;

use crate::discovery::{BleDiscovery, PairingManager, PeerDiscovery};
use crate::scheduler::metrics::SchedulerTelemetry;
use crate::server::api::{
    cancel_transfer, confirm_pair_session, connect_to_remote_peer, create_pair_session, detect_transports,
    get_connection_bridges, get_device_info, get_discovered_peers, get_pairing_status, get_pending_pair_request,
    handle_incoming_pair_request, list_transfers, open_received_folder, open_windows_hotspot_settings, prepare_transfer,
    receive_transfer_chunk, respond_to_pair_request, start_benchmark_transfer, upload_and_transfer_files, verify_pair_pin, AppState,
};
use crate::server::ws_telemetry::ws_telemetry_handler;
use crate::storage::manifest_db::ManifestDb;

#[derive(RustEmbed)]
#[folder = "sharedash-ui/"]
struct UiAssets;

async fn static_file_handler(uri: Uri) -> impl IntoResponse {
    let path = uri.path().trim_start_matches('/');
    let file_path = if path.is_empty() { "index.html" } else { path };

    match UiAssets::get(file_path) {
        Some(content) => {
            let mime = mime_guess::from_path(file_path).first_or_octet_stream();
            (
                [
                    (header::CONTENT_TYPE, mime.as_ref()),
                    (header::CACHE_CONTROL, "no-cache, no-store, must-revalidate"),
                    (header::PRAGMA, "no-cache"),
                    (header::EXPIRES, "0"),
                ],
                content.data,
            ).into_response()
        }
        None => {
            if let Some(index) = UiAssets::get("index.html") {
                let mime = mime_guess::from_path("index.html").first_or_octet_stream();
                (
                    [
                        (header::CONTENT_TYPE, mime.as_ref()),
                        (header::CACHE_CONTROL, "no-cache, no-store, must-revalidate"),
                        (header::PRAGMA, "no-cache"),
                        (header::EXPIRES, "0"),
                    ],
                    index.data,
                ).into_response()
            } else {
                (StatusCode::NOT_FOUND, "404 Not Found - ShareDash UI assets not found").into_response()
            }
        }
    }
}

pub struct Server {
    port: u16,
    #[allow(dead_code)]
    ui_dir: PathBuf,
    device_id: String,
    device_name: String,
    db_path: PathBuf,
}

impl Server {
    pub fn new(
        port: u16,
        ui_dir: PathBuf,
        device_id: String,
        device_name: String,
        db_path: PathBuf,
    ) -> Self {
        Self {
            port,
            ui_dir,
            device_id,
            device_name,
            db_path,
        }
    }

    pub async fn run(self) -> anyhow::Result<()> {
        let (telemetry_tx, _) = tokio::sync::broadcast::channel::<SchedulerTelemetry>(512);

        let manifest_db = Arc::new(ManifestDb::open(&self.db_path)?);
        let pairing_mgr = Arc::new(PairingManager::new(self.device_id.clone(), self.device_name.clone()));
        let discovery = Arc::new(PeerDiscovery::new(
            self.device_id.clone(),
            self.device_name.clone(),
            std::env::consts::OS.to_string(),
            self.port,
        ));

        // Start local UDP peer discovery beacon
        if let Err(e) = discovery.start().await {
            tracing::warn!("Could not start UDP discovery: {}", e);
        }

        // Start local BLE peer discovery scanner
        let ble_discovery = Arc::new(BleDiscovery::new());
        if let Err(e) = ble_discovery.start().await {
            tracing::warn!("Could not start BLE discovery: {}", e);
        }

        let state = AppState {
            device_id: self.device_id,
            device_name: self.device_name,
            server_port: self.port,
            pairing_mgr,
            discovery,
            ble_discovery,
            manifest_db,
            telemetry_tx: telemetry_tx.clone(),
            active_transfers: Arc::new(Mutex::new(HashMap::new())),
            pending_pair: Arc::new(Mutex::new(None)),
            active_paired_peer: Arc::new(Mutex::new(None)),
            outgoing_pair: Arc::new(Mutex::new(None)),
        };

        let cors = CorsLayer::new()
            .allow_origin(Any)
            .allow_methods(Any)
            .allow_headers(Any);

        let ws_telemetry_tx = telemetry_tx.clone();
        let app = Router::new()
            // REST Endpoints
            .route("/api/v1/info", get(get_device_info))
            .route("/api/v1/peers", get(get_discovered_peers))
            .route("/api/v1/bridges", get(get_connection_bridges))
            .route("/api/v1/transports/detect", get(detect_transports))
            .route("/api/v1/pair/create", post(create_pair_session))
            .route("/api/v1/pair/verify", post(verify_pair_pin))
            .route("/api/v1/pair/request", post(handle_incoming_pair_request))
            .route("/api/v1/pair/incoming", get(get_pending_pair_request))
            .route("/api/v1/pair/respond", post(respond_to_pair_request))
            .route("/api/v1/pair/confirm", post(confirm_pair_session))
            .route("/api/v1/pair/connect", post(connect_to_remote_peer))
            .route("/api/v1/pair/status", get(get_pairing_status))
            .route("/api/v1/storage/open_folder", post(open_received_folder))
            .route("/api/v1/hotspot/open-settings", post(open_windows_hotspot_settings))
            .route("/api/v1/transfers", get(list_transfers))
            .route("/api/v1/transfers/chunk", post(receive_transfer_chunk))
            .route("/api/v1/transfers/send", post(upload_and_transfer_files))
            .route("/api/v1/transfers/prepare", post(prepare_transfer))
            .route("/api/v1/transfers/:id/cancel", post(cancel_transfer))
            .route("/api/v1/benchmark/start", post(start_benchmark_transfer))
            // WebSocket Telemetry Feed
            .route(
                "/ws/telemetry",
                get(move |ws| ws_telemetry_handler(ws, ws_telemetry_tx.subscribe())),
            )
            // Static UI Frontend Files (Embedded)
            .fallback(static_file_handler)
            .layer(cors)
            .with_state(state.clone());

        let addr = SocketAddr::from(([0, 0, 0, 0], self.port));
        let listener = tokio::net::TcpListener::bind(addr).await?;

        let cli = Arc::new(crate::cli::TerminalCli::new(state.clone()));
        tokio::spawn(async move {
            cli.run_interactive_loop().await;
        });

        axum::serve(listener, app).await?;

        Ok(())
    }
}
