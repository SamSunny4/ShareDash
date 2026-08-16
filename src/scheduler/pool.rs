use crate::storage::chunker::ChunkInfo;
use parking_lot::Mutex;
use std::collections::{HashMap, HashSet, VecDeque};
use std::sync::Arc;
use std::time::{Duration, Instant};

#[derive(Debug, Clone)]
pub struct InFlightInfo {
    pub chunk_id: u32,
    pub primary_transport_id: String,
    pub secondary_transport_id: Option<String>, // if stolen / duplicated
    pub dispatched_at: Instant,
    pub estimated_duration: Duration,
    pub attempt_count: u32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ChunkStatus {
    Unassigned,
    InFlight(String),
    Completed(String),
    Corrupted,
}

pub struct ChunkPoolInner {
    pub unassigned: VecDeque<u32>,
    pub in_flight: HashMap<u32, InFlightInfo>,
    pub completed: HashSet<u32>,
    pub chunks: HashMap<u32, ChunkInfo>,
    pub total_bytes: u64,
    pub completed_bytes: u64,
}

#[derive(Clone)]
pub struct ChunkPool {
    inner: Arc<Mutex<ChunkPoolInner>>,
}

impl ChunkPool {
    pub fn new(chunks: Vec<ChunkInfo>, already_completed: &[u32]) -> Self {
        let mut unassigned = VecDeque::new();
        let mut completed = HashSet::new();
        let mut chunks_map = HashMap::new();
        let mut total_bytes: u64 = 0;
        let mut completed_bytes: u64 = 0;

        let completed_set: HashSet<u32> = already_completed.iter().copied().collect();

        for chunk in chunks {
            total_bytes += chunk.length as u64;
            let id = chunk.chunk_id;
            if completed_set.contains(&id) {
                completed.insert(id);
                completed_bytes += chunk.length as u64;
            } else {
                unassigned.push_back(id);
            }
            chunks_map.insert(id, chunk);
        }

        let inner = ChunkPoolInner {
            unassigned,
            in_flight: HashMap::new(),
            completed,
            chunks: chunks_map,
            total_bytes,
            completed_bytes,
        };

        Self {
            inner: Arc::new(Mutex::new(inner)),
        }
    }

    /// Pull the next unassigned chunk from the shared pool
    pub fn take_next_unassigned(
        &self,
        transport_id: &str,
        transport_mbps: f64,
        rtt_ms: f64,
    ) -> Option<ChunkInfo> {
        let mut lock = self.inner.lock();
        if let Some(chunk_id) = lock.unassigned.pop_front() {
            if let Some(chunk) = lock.chunks.get(&chunk_id).cloned() {
                // Calculate expected duration for deadline tracking
                let mbps = transport_mbps.max(1.0);
                let bytes_per_sec = (mbps * 1_000_000.0) / 8.0;
                let transfer_secs = (chunk.length as f64) / bytes_per_sec;
                let estimated_duration = Duration::from_secs_f64(transfer_secs + (rtt_ms / 1000.0) + 0.1);

                lock.in_flight.insert(
                    chunk_id,
                    InFlightInfo {
                        chunk_id,
                        primary_transport_id: transport_id.to_string(),
                        secondary_transport_id: None,
                        dispatched_at: Instant::now(),
                        estimated_duration,
                        attempt_count: 1,
                    },
                );

                return Some(chunk);
            }
        }
        None
    }

    /// Work-Stealing: If the unassigned pool is empty, find stalled/slow chunks in-flight on other transports
    pub fn steal_stalled_chunk(&self, stealer_transport_id: &str) -> Option<ChunkInfo> {
        let mut lock = self.inner.lock();
        let now = Instant::now();

        let mut candidate_chunk_id = None;
        let mut max_overdue_ratio = 1.5; // Steal if exceeded 1.5x expected time

        for (id, info) in lock.in_flight.iter() {
            // Do not steal chunks already owned by the same transport or already stolen
            if info.primary_transport_id != stealer_transport_id && info.secondary_transport_id.is_none() {
                let elapsed = now.duration_since(info.dispatched_at);
                let overdue_ratio = elapsed.as_secs_f64() / info.estimated_duration.as_secs_f64().max(0.05);

                if overdue_ratio > max_overdue_ratio {
                    max_overdue_ratio = overdue_ratio;
                    candidate_chunk_id = Some(*id);
                }
            }
        }

        if let Some(chunk_id) = candidate_chunk_id {
            if let Some(info) = lock.in_flight.get_mut(&chunk_id) {
                info.secondary_transport_id = Some(stealer_transport_id.to_string());
                info.attempt_count += 1;
            }
            return lock.chunks.get(&chunk_id).cloned();
        }

        None
    }

    /// Mark a chunk as successfully verified and completed
    pub fn mark_completed(&self, chunk_id: u32, _transport_id: &str) -> bool {
        let mut lock = self.inner.lock();
        if lock.completed.contains(&chunk_id) {
            return false; // Already completed (e.g. duplicate from work-stealing)
        }

        lock.in_flight.remove(&chunk_id);
        lock.completed.insert(chunk_id);

        if let Some(chunk) = lock.chunks.get(&chunk_id) {
            lock.completed_bytes += chunk.length as u64;
        }

        true
    }

    /// Re-enqueue a failed/rejected chunk back to the unassigned queue
    pub fn return_to_pool(&self, chunk_id: u32) {
        let mut lock = self.inner.lock();
        lock.in_flight.remove(&chunk_id);
        if !lock.completed.contains(&chunk_id) && !lock.unassigned.contains(&chunk_id) {
            lock.unassigned.push_front(chunk_id); // High priority re-enqueue
        }
    }

    /// When a transport disconnects, immediately return all its active in-flight chunks to the pool
    pub fn return_all_from_transport(&self, transport_id: &str) -> Vec<u32> {
        let mut lock = self.inner.lock();
        let mut to_return = Vec::new();

        for (chunk_id, info) in lock.in_flight.iter() {
            if info.primary_transport_id == transport_id && info.secondary_transport_id.is_none() {
                to_return.push(*chunk_id);
            }
        }

        for id in &to_return {
            lock.in_flight.remove(id);
            if !lock.unassigned.contains(id) && !lock.completed.contains(id) {
                lock.unassigned.push_front(*id);
            }
        }

        to_return
    }

    pub fn is_all_completed(&self) -> bool {
        let lock = self.inner.lock();
        lock.completed.len() == lock.chunks.len()
    }

    pub fn stats(&self) -> (usize, usize, usize, u64, u64) {
        let lock = self.inner.lock();
        (
            lock.unassigned.len(),
            lock.in_flight.len(),
            lock.completed.len(),
            lock.completed_bytes,
            lock.total_bytes,
        )
    }

    pub fn get_chunk_info(&self, chunk_id: u32) -> Option<ChunkInfo> {
        let lock = self.inner.lock();
        lock.chunks.get(&chunk_id).cloned()
    }
}
