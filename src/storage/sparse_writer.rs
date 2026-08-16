use crate::protocol::message::FileMetadata;
use anyhow::{anyhow, Context, Result};
use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::{Seek, SeekFrom, Write};
use std::path::{Component, Path, PathBuf};
use std::sync::Mutex;

pub struct SparseWriter {
    base_dir: PathBuf,
    files_meta: HashMap<u32, FileMetadata>,
    file_handles: Mutex<HashMap<u32, File>>,
}

impl SparseWriter {
    pub fn new(base_dir: impl AsRef<Path>, files: Vec<FileMetadata>) -> Result<Self> {
        let base_dir = base_dir.as_ref().to_path_buf();
        fs::create_dir_all(&base_dir)
            .with_context(|| format!("Failed to create base directory: {:?}", base_dir))?;

        let mut files_meta = HashMap::new();
        for meta in files {
            files_meta.insert(meta.file_index, meta);
        }

        let writer = Self {
            base_dir,
            files_meta,
            file_handles: Mutex::new(HashMap::new()),
        };

        writer.preallocate_all_files()?;
        Ok(writer)
    }

    /// Sanitize relative path to strictly forbid path traversal outside base_dir
    pub fn sanitize_relative_path(rel_path: &str) -> Result<PathBuf> {
        let path = Path::new(rel_path);
        let mut clean_path = PathBuf::new();

        for comp in path.components() {
            match comp {
                Component::Normal(c) => clean_path.push(c),
                Component::CurDir => {}
                _ => {
                    return Err(anyhow!(
                        "Security Error: Illegal path component in relative path: {:?}",
                        rel_path
                    ))
                }
            }
        }

        if clean_path.as_os_str().is_empty() {
            return Err(anyhow!("Empty or invalid relative path"));
        }

        Ok(clean_path)
    }

    /// Pre-allocates each target file so that random offset chunk writing can occur safely
    fn preallocate_all_files(&self) -> Result<()> {
        for meta in self.files_meta.values() {
            let sanitized = Self::sanitize_relative_path(&meta.relative_path)?;
            let full_path = self.base_dir.join(sanitized);

            if let Some(parent) = full_path.parent() {
                fs::create_dir_all(parent)?;
            }

            let file = OpenOptions::new()
                .read(true)
                .write(true)
                .create(true)
                .truncate(false)
                .open(&full_path)
                .with_context(|| format!("Failed to create/open file {:?}", full_path))?;

            // Pre-set file length
            file.set_len(meta.size_bytes)
                .with_context(|| format!("Failed to set file length for {:?}", full_path))?;

            let mut handles = self.file_handles.lock().unwrap();
            handles.insert(meta.file_index, file);
        }

        Ok(())
    }

    /// Write chunk data at exact byte offset in the destination file
    pub fn write_chunk(&self, file_index: u32, offset: u64, data: &[u8]) -> Result<()> {
        let mut handles = self.file_handles.lock().unwrap();

        if let Some(file) = handles.get_mut(&file_index) {
            file.seek(SeekFrom::Start(offset))
                .with_context(|| format!("Failed to seek to offset {} in file {}", offset, file_index))?;
            file.write_all(data)
                .with_context(|| format!("Failed to write chunk data at offset {} in file {}", offset, file_index))?;
            file.flush()?;
            Ok(())
        } else {
            Err(anyhow!("File handle for file_index {} not found", file_index))
        }
    }

    /// Finalize a completed file by setting its modification timestamp
    pub fn finalize_file(&self, file_index: u32) -> Result<()> {
        let meta = self
            .files_meta
            .get(&file_index)
            .ok_or_else(|| anyhow!("File index {} not found in manifest", file_index))?;

        let sanitized = Self::sanitize_relative_path(&meta.relative_path)?;
        let full_path = self.base_dir.join(sanitized);

        // Close handle if open
        {
            let mut handles = self.file_handles.lock().unwrap();
            handles.remove(&file_index);
        }

        if meta.modified_timestamp > 0 {
            let mtime = std::time::UNIX_EPOCH + std::time::Duration::from_secs(meta.modified_timestamp as u64);
            let _ = filetime::set_file_mtime(&full_path, filetime::FileTime::from_system_time(mtime));
        }

        Ok(())
    }

    pub fn base_dir(&self) -> &Path {
        &self.base_dir
    }
}

// Minimal filetime fallback if not using external crate
mod filetime {
    use std::fs::File;
    use std::path::Path;
    use std::time::SystemTime;

    pub struct FileTime(SystemTime);
    impl FileTime {
        pub fn from_system_time(st: SystemTime) -> Self {
            Self(st)
        }
    }

    pub fn set_file_mtime(path: &Path, ft: FileTime) -> std::io::Result<()> {
        let f = File::open(path)?;
        f.set_modified(ft.0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sparse_writer_write_out_of_order() {
        let temp_dir = tempfile::tempdir().unwrap();
        let meta = FileMetadata {
            file_index: 0,
            relative_path: "sub/test_video.bin".to_string(),
            size_bytes: 1024 * 1024, // 1 MB
            modified_timestamp: 1700000000,
            is_executable: false,
            chunk_start_index: 0,
            chunk_count: 2,
            sha256_hash: "".to_string(),
        };

        let writer = SparseWriter::new(temp_dir.path(), vec![meta]).unwrap();

        // Write chunk 1 (second half) first
        let chunk1_data = vec![0xBB; 512 * 1024];
        writer.write_chunk(0, 512 * 1024, &chunk1_data).unwrap();

        // Write chunk 0 (first half) second
        let chunk0_data = vec![0xAA; 512 * 1024];
        writer.write_chunk(0, 0, &chunk0_data).unwrap();

        writer.finalize_file(0).unwrap();

        let target_file = temp_dir.path().join("sub/test_video.bin");
        assert!(target_file.exists());

        let read_bytes = fs::read(&target_file).unwrap();
        assert_eq!(read_bytes.len(), 1024 * 1024);
        assert_eq!(&read_bytes[..512 * 1024], &chunk0_data[..]);
        assert_eq!(&read_bytes[512 * 1024..], &chunk1_data[..]);
    }
}
