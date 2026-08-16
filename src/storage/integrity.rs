use crate::protocol::crypto::SessionCrypto;
use anyhow::{anyhow, Result};
use sha2::Digest;

pub struct IntegrityVerifier;

impl IntegrityVerifier {
    /// Verify a received chunk against its manifest hash (supporting either SHA-256 or BLAKE3)
    pub fn verify_chunk(data: &[u8], expected_sha256: &str, expected_blake3: &str) -> Result<bool> {
        if !expected_sha256.is_empty() {
            let computed_sha = SessionCrypto::compute_sha256(data);
            if computed_sha.eq_ignore_ascii_case(expected_sha256) {
                return Ok(true);
            }
        }

        if !expected_blake3.is_empty() {
            let computed_b3 = SessionCrypto::compute_blake3(data);
            if computed_b3.eq_ignore_ascii_case(expected_blake3) {
                return Ok(true);
            }
        }

        Err(anyhow!(
            "Integrity validation failed! Data does not match SHA-256 ({}) or BLAKE3 ({})",
            expected_sha256,
            expected_blake3
        ))
    }

    /// Compute Merkle Root of an array of chunk hashes
    pub fn compute_merkle_root(chunk_hashes: &[String]) -> String {
        let mut hasher = sha2::Sha256::new();
        for h in chunk_hashes {
            sha2::Digest::update(&mut hasher, h.as_bytes());
        }
        hex::encode(sha2::Digest::finalize(hasher))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_verify_chunk_success_and_failure() {
        let data = b"Chunk Payload 12345";
        let sha = SessionCrypto::compute_sha256(data);
        let b3 = SessionCrypto::compute_blake3(data);

        assert!(IntegrityVerifier::verify_chunk(data, &sha, &b3).unwrap());

        let corrupted = b"Chunk Payload 1234X";
        assert!(IntegrityVerifier::verify_chunk(corrupted, &sha, &b3).is_err());
    }
}
