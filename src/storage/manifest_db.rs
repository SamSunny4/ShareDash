use anyhow::{Context, Result};
use chrono::Utc;
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use uuid::Uuid;

use crate::storage::chunker::TransferManifest;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TransferStatus {
    Pending,
    Active,
    Paused,
    Completed,
    Failed,
    Cancelled,
}

impl TransferStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Pending => "PENDING",
            Self::Active => "ACTIVE",
            Self::Paused => "PAUSED",
            Self::Completed => "COMPLETED",
            Self::Failed => "FAILED",
            Self::Cancelled => "CANCELLED",
        }
    }

    #[allow(clippy::should_implement_trait)]
    pub fn from_str(s: &str) -> Self {
        match s {
            "ACTIVE" => Self::Active,
            "PAUSED" => Self::Paused,
            "COMPLETED" => Self::Completed,
            "FAILED" => Self::Failed,
            "CANCELLED" => Self::Cancelled,
            _ => Self::Pending,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ChunkState {
    Pending,
    InFlight,
    Completed,
    Corrupt,
}

impl ChunkState {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Pending => "PENDING",
            Self::InFlight => "IN_FLIGHT",
            Self::Completed => "COMPLETED",
            Self::Corrupt => "CORRUPT",
        }
    }

    #[allow(clippy::should_implement_trait)]
    pub fn from_str(s: &str) -> Self {
        match s {
            "IN_FLIGHT" => Self::InFlight,
            "COMPLETED" => Self::Completed,
            "CORRUPT" => Self::Corrupt,
            _ => Self::Pending,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransferRecord {
    pub transfer_id: Uuid,
    pub title: String,
    pub is_outgoing: bool,
    pub total_bytes: u64,
    pub total_files: usize,
    pub chunk_size: u32,
    pub total_chunks: usize,
    pub completed_chunks: usize,
    pub root_hash: String,
    pub destination_path: String,
    pub status: TransferStatus,
    pub created_at: String,
    pub updated_at: String,
}

pub struct ManifestDb {
    conn: Mutex<Connection>,
    db_path: PathBuf,
}

impl ManifestDb {
    pub fn open(db_path: impl AsRef<Path>) -> Result<Self> {
        let db_path = db_path.as_ref().to_path_buf();
        if let Some(parent) = db_path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        let conn = Connection::open(&db_path)
            .with_context(|| format!("Failed to open SQLite database at {:?}", db_path))?;

        // Enable WAL mode for high concurrency and crash resilience
        conn.execute_batch(
            "
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;
            PRAGMA foreign_keys = ON;

            CREATE TABLE IF NOT EXISTS transfers (
                transfer_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                is_outgoing INTEGER NOT NULL,
                total_bytes INTEGER NOT NULL,
                total_files INTEGER NOT NULL,
                total_chunks INTEGER NOT NULL,
                chunk_size INTEGER NOT NULL,
                completed_chunks INTEGER NOT NULL DEFAULT 0,
                root_hash TEXT NOT NULL,
                destination_path TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS transfer_files (
                transfer_id TEXT NOT NULL,
                file_index INTEGER NOT NULL,
                relative_path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                modified_timestamp INTEGER NOT NULL,
                chunk_start_index INTEGER NOT NULL,
                chunk_count INTEGER NOT NULL,
                sha256_hash TEXT NOT NULL,
                PRIMARY KEY(transfer_id, file_index),
                FOREIGN KEY(transfer_id) REFERENCES transfers(transfer_id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS transfer_chunks (
                transfer_id TEXT NOT NULL,
                chunk_id INTEGER NOT NULL,
                file_index INTEGER NOT NULL,
                offset INTEGER NOT NULL,
                length INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                blake3 TEXT NOT NULL,
                state TEXT NOT NULL,
                assigned_transport TEXT,
                completed_at TEXT,
                PRIMARY KEY(transfer_id, chunk_id),
                FOREIGN KEY(transfer_id) REFERENCES transfers(transfer_id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_chunks_state ON transfer_chunks(transfer_id, state);
            ",
        )?;

        Ok(Self {
            conn: Mutex::new(conn),
            db_path,
        })
    }

    pub fn open_in_memory() -> Result<Self> {
        let conn = Connection::open_in_memory()?;
        conn.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS transfers (
                transfer_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                is_outgoing INTEGER NOT NULL,
                total_bytes INTEGER NOT NULL,
                total_files INTEGER NOT NULL,
                total_chunks INTEGER NOT NULL,
                chunk_size INTEGER NOT NULL,
                completed_chunks INTEGER NOT NULL DEFAULT 0,
                root_hash TEXT NOT NULL,
                destination_path TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS transfer_files (
                transfer_id TEXT NOT NULL,
                file_index INTEGER NOT NULL,
                relative_path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                modified_timestamp INTEGER NOT NULL,
                chunk_start_index INTEGER NOT NULL,
                chunk_count INTEGER NOT NULL,
                sha256_hash TEXT NOT NULL,
                PRIMARY KEY(transfer_id, file_index)
            );

            CREATE TABLE IF NOT EXISTS transfer_chunks (
                transfer_id TEXT NOT NULL,
                chunk_id INTEGER NOT NULL,
                file_index INTEGER NOT NULL,
                offset INTEGER NOT NULL,
                length INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                blake3 TEXT NOT NULL,
                state TEXT NOT NULL,
                assigned_transport TEXT,
                completed_at TEXT,
                PRIMARY KEY(transfer_id, chunk_id)
            );
            ",
        )?;

        Ok(Self {
            conn: Mutex::new(conn),
            db_path: PathBuf::from(":memory:"),
        })
    }

    pub fn db_path(&self) -> &Path {
        &self.db_path
    }

    /// Save a newly offered/created transfer manifest into the database
    pub fn save_transfer(
        &self,
        manifest: &TransferManifest,
        is_outgoing: bool,
        destination_path: &str,
    ) -> Result<()> {
        let mut conn = self.conn.lock().unwrap();
        let tx = conn.transaction()?;

        let now = Utc::now().to_rfc3339();
        let transfer_id_str = manifest.transfer_id.to_string();

        tx.execute(
            "INSERT OR REPLACE INTO transfers (
                transfer_id, title, is_outgoing, total_bytes, total_files, total_chunks,
                chunk_size, completed_chunks, root_hash, destination_path, status,
                created_at, updated_at
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 0, ?8, ?9, ?10, ?11, ?12)",
            params![
                transfer_id_str,
                manifest.title,
                if is_outgoing { 1 } else { 0 },
                manifest.total_bytes,
                manifest.total_files,
                manifest.total_chunks,
                manifest.chunk_size,
                manifest.root_hash,
                destination_path,
                TransferStatus::Active.as_str(),
                now,
                now
            ],
        )?;

        for file in &manifest.files {
            tx.execute(
                "INSERT OR REPLACE INTO transfer_files (
                    transfer_id, file_index, relative_path, size_bytes,
                    modified_timestamp, chunk_start_index, chunk_count, sha256_hash
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
                params![
                    transfer_id_str,
                    file.file_index,
                    file.relative_path,
                    file.size_bytes,
                    file.modified_timestamp,
                    file.chunk_start_index,
                    file.chunk_count,
                    file.sha256_hash
                ],
            )?;
        }

        for chunk in &manifest.chunks {
            let initial_state = if is_outgoing {
                ChunkState::Completed.as_str()
            } else {
                ChunkState::Pending.as_str()
            };

            tx.execute(
                "INSERT OR REPLACE INTO transfer_chunks (
                    transfer_id, chunk_id, file_index, offset, length,
                    sha256, blake3, state, assigned_transport, completed_at
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, NULL, NULL)",
                params![
                    transfer_id_str,
                    chunk.chunk_id,
                    chunk.file_index,
                    chunk.offset,
                    chunk.length,
                    chunk.sha256,
                    chunk.blake3,
                    initial_state
                ],
            )?;
        }

        tx.commit()?;
        Ok(())
    }

    /// Mark a chunk completed upon cryptographic verification
    pub fn mark_chunk_completed(
        &self,
        transfer_id: Uuid,
        chunk_id: u32,
        transport_id: &str,
    ) -> Result<u32> {
        let mut conn = self.conn.lock().unwrap();
        let tx = conn.transaction()?;

        let now = Utc::now().to_rfc3339();
        let transfer_id_str = transfer_id.to_string();

        tx.execute(
            "UPDATE transfer_chunks SET state = 'COMPLETED', assigned_transport = ?1, completed_at = ?2
             WHERE transfer_id = ?3 AND chunk_id = ?4",
            params![transport_id, now, transfer_id_str, chunk_id],
        )?;

        tx.execute(
            "UPDATE transfers SET completed_chunks = (
                SELECT COUNT(*) FROM transfer_chunks WHERE transfer_id = ?1 AND state = 'COMPLETED'
            ), updated_at = ?2 WHERE transfer_id = ?1",
            params![transfer_id_str, now],
        )?;

        let completed: u32 = tx.query_row(
            "SELECT completed_chunks FROM transfers WHERE transfer_id = ?1",
            params![transfer_id_str],
            |row| row.get(0),
        )?;

        let total: u32 = tx.query_row(
            "SELECT total_chunks FROM transfers WHERE transfer_id = ?1",
            params![transfer_id_str],
            |row| row.get(0),
        )?;

        if completed >= total {
            tx.execute(
                "UPDATE transfers SET status = 'COMPLETED', updated_at = ?1 WHERE transfer_id = ?2",
                params![now, transfer_id_str],
            )?;
        }

        tx.commit()?;
        Ok(completed)
    }

    /// Get list of all pending / uncompleted chunk IDs for a transfer
    pub fn get_uncompleted_chunks(&self, transfer_id: Uuid) -> Result<Vec<u32>> {
        let conn = self.conn.lock().unwrap();
        let transfer_id_str = transfer_id.to_string();

        let mut stmt = conn.prepare(
            "SELECT chunk_id FROM transfer_chunks
             WHERE transfer_id = ?1 AND state != 'COMPLETED'
             ORDER BY chunk_id ASC",
        )?;

        let rows = stmt.query_map(params![transfer_id_str], |row| row.get::<_, u32>(0))?;
        let mut chunks = Vec::new();
        for r in rows {
            chunks.push(r?);
        }
        Ok(chunks)
    }

    /// Get list of all completed chunk IDs (useful for resume negotiation)
    pub fn get_completed_chunks(&self, transfer_id: Uuid) -> Result<Vec<u32>> {
        let conn = self.conn.lock().unwrap();
        let transfer_id_str = transfer_id.to_string();

        let mut stmt = conn.prepare(
            "SELECT chunk_id FROM transfer_chunks
             WHERE transfer_id = ?1 AND state = 'COMPLETED'
             ORDER BY chunk_id ASC",
        )?;

        let rows = stmt.query_map(params![transfer_id_str], |row| row.get::<_, u32>(0))?;
        let mut chunks = Vec::new();
        for r in rows {
            chunks.push(r?);
        }
        Ok(chunks)
    }

    /// Retrieve all transfers for dashboard UI
    pub fn list_transfers(&self) -> Result<Vec<TransferRecord>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT transfer_id, title, is_outgoing, total_bytes, total_files, total_chunks,
                    chunk_size, completed_chunks, root_hash, destination_path, status,
                    created_at, updated_at
             FROM transfers ORDER BY created_at DESC",
        )?;

        let rows = stmt.query_map([], |row| {
            let id_str: String = row.get(0)?;
            let status_str: String = row.get(10)?;
            Ok(TransferRecord {
                transfer_id: Uuid::parse_str(&id_str).unwrap_or_default(),
                title: row.get(1)?,
                is_outgoing: row.get::<_, i32>(2)? == 1,
                total_bytes: row.get(3)?,
                total_files: row.get(4)?,
                total_chunks: row.get(5)?,
                chunk_size: row.get(6)?,
                completed_chunks: row.get(7)?,
                root_hash: row.get(8)?,
                destination_path: row.get(9)?,
                status: TransferStatus::from_str(&status_str),
                created_at: row.get(11)?,
                updated_at: row.get(12)?,
            })
        })?;

        let mut list = Vec::new();
        for r in rows {
            list.push(r?);
        }
        Ok(list)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::chunker::ChunkInfo;

    #[test]
    fn test_manifest_db_lifecycle() {
        let db = ManifestDb::open_in_memory().unwrap();
        let manifest = TransferManifest {
            transfer_id: Uuid::new_v4(),
            title: "vacation_photos".to_string(),
            total_bytes: 10 * 1024 * 1024,
            total_files: 2,
            chunk_size: 4 * 1024 * 1024,
            total_chunks: 3,
            root_hash: "abcd1234".to_string(),
            files: vec![],
            chunks: vec![
                ChunkInfo {
                    chunk_id: 0,
                    file_index: 0,
                    offset: 0,
                    length: 4 * 1024 * 1024,
                    sha256: "h0".to_string(),
                    blake3: "b0".to_string(),
                },
                ChunkInfo {
                    chunk_id: 1,
                    file_index: 0,
                    offset: 4 * 1024 * 1024,
                    length: 4 * 1024 * 1024,
                    sha256: "h1".to_string(),
                    blake3: "b1".to_string(),
                },
                ChunkInfo {
                    chunk_id: 2,
                    file_index: 1,
                    offset: 0,
                    length: 2 * 1024 * 1024,
                    sha256: "h2".to_string(),
                    blake3: "b2".to_string(),
                },
            ],
        };

        db.save_transfer(&manifest, false, "/downloads").unwrap();

        let uncompleted = db.get_uncompleted_chunks(manifest.transfer_id).unwrap();
        assert_eq!(uncompleted, vec![0, 1, 2]);

        // Complete chunk 1 via USB
        let comp = db.mark_chunk_completed(manifest.transfer_id, 1, "USB").unwrap();
        assert_eq!(comp, 1);

        let uncompleted = db.get_uncompleted_chunks(manifest.transfer_id).unwrap();
        assert_eq!(uncompleted, vec![0, 2]);

        let completed = db.get_completed_chunks(manifest.transfer_id).unwrap();
        assert_eq!(completed, vec![1]);
    }
}
