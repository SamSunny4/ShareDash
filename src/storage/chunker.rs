use crate::protocol::crypto::SessionCrypto;
use crate::protocol::message::FileMetadata;
use anyhow::{anyhow, Context, Result};
use serde::{Deserialize, Serialize};
use sha2::Digest;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};
use uuid::Uuid;

pub const DEFAULT_CHUNK_SIZE: u32 = 4 * 1024 * 1024; // 4 MB default
pub const MIN_CHUNK_SIZE: u32 = 1024 * 1024; // 1 MB min
pub const MAX_CHUNK_SIZE: u32 = 32 * 1024 * 1024; // 32 MB max

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChunkInfo {
    pub chunk_id: u32,
    pub file_index: u32,
    pub offset: u64,
    pub length: u32,
    pub sha256: String,
    pub blake3: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransferManifest {
    pub transfer_id: Uuid,
    pub title: String,
    pub total_bytes: u64,
    pub total_files: u32,
    pub chunk_size: u32,
    pub total_chunks: u32,
    pub root_hash: String,
    pub files: Vec<FileMetadata>,
    pub chunks: Vec<ChunkInfo>,
}

pub struct AdaptiveChunker;

impl AdaptiveChunker {
    /// Calculate optimal chunk size based on total transfer payload
    pub fn calculate_chunk_size(total_bytes: u64) -> u32 {
        if total_bytes < 50 * 1024 * 1024 {
            // < 50 MB
            MIN_CHUNK_SIZE // 1 MB
        } else if total_bytes < 500 * 1024 * 1024 {
            // 50 MB - 500 MB
            2 * 1024 * 1024 // 2 MB
        } else if total_bytes < 5 * 1024 * 1024 * 1024 {
            // 500 MB - 5 GB
            4 * 1024 * 1024 // 4 MB
        } else if total_bytes < 20 * 1024 * 1024 * 1024 {
            // 5 GB - 20 GB
            8 * 1024 * 1024 // 8 MB
        } else {
            // > 20 GB
            16 * 1024 * 1024 // 16 MB
        }
    }

    /// Recursively collect all files from a directory or single file path
    pub fn scan_path(target_path: &Path) -> Result<Vec<(PathBuf, String)>> {
        let mut file_entries = Vec::new();

        if target_path.is_file() {
            let filename = target_path
                .file_name()
                .ok_or_else(|| anyhow!("Invalid filename"))?
                .to_string_lossy()
                .to_string();
            file_entries.push((target_path.to_path_buf(), filename));
        } else if target_path.is_dir() {
            let root_dir = target_path.canonicalize()?;
            Self::walk_directory(&root_dir, &root_dir, &mut file_entries)?;
        } else {
            return Err(anyhow!("Path does not exist: {:?}", target_path));
        }

        file_entries.sort_by(|a, b| a.1.cmp(&b.1));
        Ok(file_entries)
    }

    fn walk_directory(
        root_dir: &Path,
        current_dir: &Path,
        files: &mut Vec<(PathBuf, String)>,
    ) -> Result<()> {
        for entry in std::fs::read_dir(current_dir)? {
            let entry = entry?;
            let path = entry.path();
            if path.is_dir() {
                Self::walk_directory(root_dir, &path, files)?;
            } else if path.is_file() {
                let relative = path
                    .strip_prefix(root_dir)?
                    .to_string_lossy()
                    .replace('\\', "/");
                files.push((path, relative));
            }
        }
        Ok(())
    }

    /// Build a complete TransferManifest with chunk layout and cryptographic hashes
    pub fn build_manifest(paths: &[PathBuf], custom_chunk_size: Option<u32>) -> Result<TransferManifest> {
        let mut all_scanned_files = Vec::new();

        for p in paths {
            let entries = Self::scan_path(p)?;
            all_scanned_files.extend(entries);
        }

        if all_scanned_files.is_empty() {
            return Err(anyhow!("No files found to build transfer manifest"));
        }

        let mut total_bytes: u64 = 0;
        for (abs_path, _) in &all_scanned_files {
            let meta = std::fs::metadata(abs_path)
                .with_context(|| format!("Failed to read metadata for {:?}", abs_path))?;
            total_bytes += meta.len();
        }

        let chunk_size = custom_chunk_size.unwrap_or_else(|| Self::calculate_chunk_size(total_bytes));

        let mut files_metadata = Vec::new();
        let mut chunks_info = Vec::new();
        let mut global_chunk_id: u32 = 0;
        let mut all_chunk_hashes = Vec::new();

        for (file_idx, (abs_path, rel_path)) in all_scanned_files.iter().enumerate() {
            let meta = std::fs::metadata(abs_path)?;
            let file_size = meta.len();
            let mtime = meta
                .modified()
                .map(|t| {
                    t.duration_since(std::time::UNIX_EPOCH)
                        .unwrap_or_default()
                        .as_secs() as i64
                })
                .unwrap_or(0);

            let chunk_start_index = global_chunk_id;
            let mut file_chunks_count = 0;

            if file_size == 0 {
                // Empty file representation
                let empty_hash = SessionCrypto::compute_sha256(b"");
                let empty_b3 = SessionCrypto::compute_blake3(b"");
                let chunk = ChunkInfo {
                    chunk_id: global_chunk_id,
                    file_index: file_idx as u32,
                    offset: 0,
                    length: 0,
                    sha256: empty_hash.clone(),
                    blake3: empty_b3.clone(),
                };
                all_chunk_hashes.push(empty_hash.clone());
                chunks_info.push(chunk);
                global_chunk_id += 1;
                file_chunks_count += 1;
            } else {
                let mut f = File::open(abs_path)?;
                let mut offset: u64 = 0;
                let mut buf = vec![0u8; chunk_size as usize];

                while offset < file_size {
                    let to_read = std::cmp::min(chunk_size as u64, file_size - offset) as usize;
                    f.seek(SeekFrom::Start(offset))?;
                    f.read_exact(&mut buf[..to_read])?;

                    let chunk_sha = SessionCrypto::compute_sha256(&buf[..to_read]);
                    let chunk_b3 = SessionCrypto::compute_blake3(&buf[..to_read]);

                    let chunk = ChunkInfo {
                        chunk_id: global_chunk_id,
                        file_index: file_idx as u32,
                        offset,
                        length: to_read as u32,
                        sha256: chunk_sha.clone(),
                        blake3: chunk_b3,
                    };

                    all_chunk_hashes.push(chunk_sha);
                    chunks_info.push(chunk);
                    global_chunk_id += 1;
                    file_chunks_count += 1;
                    offset += to_read as u64;
                }
            }

            // Compute whole file hash
            let mut full_file = File::open(abs_path)?;
            let mut file_hasher = sha2::Sha256::new();
            let mut stream_buf = vec![0u8; 64 * 1024];
            loop {
                let n = full_file.read(&mut stream_buf)?;
                if n == 0 {
                    break;
                }
                sha2::Digest::update(&mut file_hasher, &stream_buf[..n]);
            }
            let file_full_sha = hex::encode(sha2::Digest::finalize(file_hasher));

            files_metadata.push(FileMetadata {
                file_index: file_idx as u32,
                relative_path: rel_path.clone(),
                size_bytes: file_size,
                modified_timestamp: mtime,
                is_executable: false,
                chunk_start_index,
                chunk_count: file_chunks_count,
                sha256_hash: file_full_sha,
            });
        }

        // Calculate Root Merkle Hash over all chunk hashes
        let mut root_hasher = sha2::Sha256::new();
        for h in &all_chunk_hashes {
            sha2::Digest::update(&mut root_hasher, h.as_bytes());
        }
        let root_hash = hex::encode(sha2::Digest::finalize(root_hasher));

        let title = if all_scanned_files.len() == 1 {
            all_scanned_files[0].1.clone()
        } else {
            format!("{} items", all_scanned_files.len())
        };

        Ok(TransferManifest {
            transfer_id: Uuid::new_v4(),
            title,
            total_bytes,
            total_files: files_metadata.len() as u32,
            chunk_size,
            total_chunks: chunks_info.len() as u32,
            root_hash,
            files: files_metadata,
            chunks: chunks_info,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn test_adaptive_chunk_sizes() {
        assert_eq!(AdaptiveChunker::calculate_chunk_size(10 * 1024 * 1024), 1024 * 1024);
        assert_eq!(AdaptiveChunker::calculate_chunk_size(200 * 1024 * 1024), 2 * 1024 * 1024);
        assert_eq!(AdaptiveChunker::calculate_chunk_size(2 * 1024 * 1024 * 1024), 4 * 1024 * 1024);
        assert_eq!(AdaptiveChunker::calculate_chunk_size(10 * 1024 * 1024 * 1024), 8 * 1024 * 1024);
        assert_eq!(AdaptiveChunker::calculate_chunk_size(50 * 1024 * 1024 * 1024), 16 * 1024 * 1024);
    }

    #[test]
    fn test_build_manifest_from_temp_file() {
        let temp_dir = tempfile::tempdir().unwrap();
        let file_path = temp_dir.path().join("sample_test.bin");

        let sample_data = vec![0xAB; 2500 * 1024]; // 2.5 MB
        let mut f = File::create(&file_path).unwrap();
        f.write_all(&sample_data).unwrap();
        f.flush().unwrap();

        let manifest = AdaptiveChunker::build_manifest(&[file_path], Some(1024 * 1024)).unwrap();
        assert_eq!(manifest.total_files, 1);
        assert_eq!(manifest.total_bytes, 2500 * 1024);
        assert_eq!(manifest.total_chunks, 3); // 1MB + 1MB + 500KB
        assert_eq!(manifest.chunks[0].length, 1024 * 1024);
        assert_eq!(manifest.chunks[1].length, 1024 * 1024);
        assert_eq!(manifest.chunks[2].length, (2500 - 2048) * 1024); // 462,848 bytes
        assert_eq!(manifest.root_hash.len(), 64);
    }
}
