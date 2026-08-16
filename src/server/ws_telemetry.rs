use axum::{
    extract::ws::{Message, WebSocket, WebSocketUpgrade},
    response::IntoResponse,
};
use futures::StreamExt;
use tokio::sync::broadcast;

use crate::scheduler::metrics::SchedulerTelemetry;

pub async fn ws_telemetry_handler(
    ws: WebSocketUpgrade,
    telemetry_rx: broadcast::Receiver<SchedulerTelemetry>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, telemetry_rx))
}

async fn handle_socket(
    mut socket: WebSocket,
    mut telemetry_rx: broadcast::Receiver<SchedulerTelemetry>,
) {
    tracing::debug!("New WebSocket telemetry client connected");

    loop {
        tokio::select! {
            result = telemetry_rx.recv() => {
                match result {
                    Ok(telemetry) => {
                        if let Ok(json) = serde_json::to_string(&telemetry) {
                            if socket.send(Message::Text(json)).await.is_err() {
                                break;
                            }
                        }
                    }
                    Err(broadcast::error::RecvError::Lagged(_)) => {
                        // Client lagged behind, continue on next message
                        continue;
                    }
                    Err(broadcast::error::RecvError::Closed) => {
                        break;
                    }
                }
            }
            msg = socket.next() => {
                match msg {
                    Some(Ok(Message::Close(_))) | None => break,
                    _ => {}
                }
            }
        }
    }

    tracing::debug!("WebSocket telemetry client disconnected");
}
