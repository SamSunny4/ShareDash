use anyhow::{anyhow, Result};
use chrono::Utc;
use parking_lot::Mutex;
use qrcode::render::svg;
use qrcode::QrCode;
use rand::Rng;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairingSession {
    pub session_id: Uuid,
    pub pin_code: String,
    pub device_name: String,
    pub server_endpoint: String,
    pub qr_svg: String,
    pub created_at: i64,
    pub is_paired: bool,
    pub auth_token: Option<String>,
}

#[derive(Clone)]
pub struct PairingManager {
    sessions: Arc<Mutex<HashMap<Uuid, PairingSession>>>,
    device_id: String,
    device_name: String,
}

impl PairingManager {
    pub fn new(device_id: String, device_name: String) -> Self {
        Self {
            sessions: Arc::new(Mutex::new(HashMap::new())),
            device_id,
            device_name,
        }
    }

    pub fn device_id(&self) -> &str {
        &self.device_id
    }

    /// Generate a new 6-digit PIN and QR code session for mobile pairing
    pub fn create_session(&self, server_endpoint: &str) -> Result<PairingSession> {
        let session_id = Uuid::new_v4();
        let mut rng = rand::thread_rng();
        let pin_code = format!("{:06}", rng.gen_range(100_000..999_999));

        // Format pairing URL for phone camera QR scan
        let pairing_payload = format!(
            "sharedash://pair?session={}&pin={}&host={}&name={}",
            session_id, pin_code, server_endpoint, self.device_name
        );

        let qr = QrCode::new(pairing_payload.as_bytes())
            .map_err(|e| anyhow!("Failed to generate QR code: {}", e))?;

        let svg_image = qr
            .render::<svg::Color>()
            .min_dimensions(200, 200)
            .dark_color(svg::Color("#0f172a"))
            .light_color(svg::Color("#ffffff"))
            .build();

        let session = PairingSession {
            session_id,
            pin_code,
            device_name: self.device_name.clone(),
            server_endpoint: server_endpoint.to_string(),
            qr_svg: svg_image,
            created_at: Utc::now().timestamp(),
            is_paired: false,
            auth_token: None,
        };

        self.sessions.lock().insert(session_id, session.clone());
        Ok(session)
    }

    /// Verify a PIN submission from a connecting peer
    pub fn verify_pin(&self, session_id: Uuid, entered_pin: &str) -> Result<String> {
        let mut lock = self.sessions.lock();
        if let Some(session) = lock.get_mut(&session_id) {
            if session.pin_code == entered_pin {
                let token = Uuid::new_v4().to_string();
                session.is_paired = true;
                session.auth_token = Some(token.clone());
                return Ok(token);
            }
            return Err(anyhow!("Invalid 6-digit PIN entered"));
        }
        Err(anyhow!("Pairing session not found or expired"))
    }

    /// Check if an auth token is valid
    pub fn is_token_valid(&self, session_id: Uuid, token: &str) -> bool {
        let lock = self.sessions.lock();
        if let Some(session) = lock.get(&session_id) {
            if let Some(ref auth) = session.auth_token {
                return auth == token;
            }
        }
        false
    }
}
