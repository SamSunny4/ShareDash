use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Key, Nonce,
};
use anyhow::{anyhow, Result};
use bytes::Bytes;
use rand::RngCore;
use sha2::{Digest, Sha256};

pub struct SessionCrypto {
    cipher: Option<Aes256Gcm>,
    session_key: [u8; 32],
}

impl SessionCrypto {
    pub fn new_unencrypted() -> Self {
        Self {
            cipher: None,
            session_key: [0u8; 32],
        }
    }

    pub fn from_key(key: [u8; 32]) -> Self {
        let cipher_key = Key::<Aes256Gcm>::from_slice(&key);
        let cipher = Aes256Gcm::new(cipher_key);
        Self {
            cipher: Some(cipher),
            session_key: key,
        }
    }

    pub fn generate_random_key() -> [u8; 32] {
        let mut key = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut key);
        key
    }

    pub fn session_key(&self) -> &[u8; 32] {
        &self.session_key
    }

    pub fn encrypt_payload(&self, plaintext: &[u8]) -> Result<Bytes> {
        match &self.cipher {
            Some(cipher) => {
                let mut nonce_bytes = [0u8; 12];
                rand::thread_rng().fill_bytes(&mut nonce_bytes);
                let nonce = Nonce::from_slice(&nonce_bytes);

                let ciphertext = cipher
                    .encrypt(nonce, plaintext)
                    .map_err(|e| anyhow!("Encryption failed: {}", e))?;

                // Output format: [12 bytes Nonce] + [Ciphertext + Auth Tag]
                let mut out = Vec::with_capacity(12 + ciphertext.len());
                out.extend_from_slice(&nonce_bytes);
                out.extend_from_slice(&ciphertext);
                Ok(Bytes::from(out))
            }
            None => Ok(Bytes::copy_from_slice(plaintext)),
        }
    }

    pub fn decrypt_payload(&self, payload: &[u8]) -> Result<Bytes> {
        match &self.cipher {
            Some(cipher) => {
                if payload.len() < 12 {
                    return Err(anyhow!("Payload too short for ciphertext nonce"));
                }
                let (nonce_slice, ciphertext) = payload.split_at(12);
                let nonce = Nonce::from_slice(nonce_slice);

                let plaintext = cipher
                    .decrypt(nonce, ciphertext)
                    .map_err(|e| anyhow!("Decryption failed: {}", e))?;

                Ok(Bytes::from(plaintext))
            }
            None => Ok(Bytes::copy_from_slice(payload)),
        }
    }

    pub fn compute_sha256(data: &[u8]) -> String {
        let mut hasher = Sha256::new();
        hasher.update(data);
        hex::encode(hasher.finalize())
    }

    pub fn compute_blake3(data: &[u8]) -> String {
        let hash = blake3::hash(data);
        hash.to_hex().to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encryption_roundtrip() {
        let key = SessionCrypto::generate_random_key();
        let crypto = SessionCrypto::from_key(key);

        let data = b"ShareDash High-Speed Multipath Transfer E2E Encrypted Payload";
        let encrypted = crypto.encrypt_payload(data).unwrap();
        assert_ne!(encrypted.as_ref(), data);

        let decrypted = crypto.decrypt_payload(&encrypted).unwrap();
        assert_eq!(decrypted.as_ref(), data);
    }

    #[test]
    fn test_hashers() {
        let data = b"TestData12345";
        let sha = SessionCrypto::compute_sha256(data);
        let b3 = SessionCrypto::compute_blake3(data);
        assert_eq!(sha.len(), 64);
        assert_eq!(b3.len(), 64);
    }
}
