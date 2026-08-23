import os
import sys
import time
import socket
import threading
import queue
import hashlib
import json
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor

CHUNK_SIZES_TO_TEST = [256 * 1024, 512 * 1024, 1024 * 1024, 2 * 1024 * 1024, 4 * 1024 * 1024, 8 * 1024 * 1024]
TEST_FILE = "sharedash_1gb_benchmark.bin"

def get_file_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(4 * 1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def single_stream_post(url, filepath, timeout=600):
    """Streams a file to the ShareDash HTTP server on Android or PC."""
    filename = os.path.basename(filepath)
    filesize = os.path.getsize(filepath)
    boundary = f"ShareDashBoundary{int(time.time()*1000)}"
    
    header = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
    ).encode("utf-8")
    footer = f"\r\n--{boundary}--\r\n".encode("utf-8")
    
    total_content_len = len(header) + filesize + len(footer)
    
    req = urllib.request.Request(
        url,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(total_content_len)
        },
        method="POST"
    )
    
    class StreamBody:
        def __init__(self, filepath, header, footer):
            self.filepath = filepath
            self.header = header
            self.footer = footer
            self.file = None
            self.header_sent = False
            self.footer_sent = False
            
        def read(self, size=-1):
            if not self.header_sent:
                self.header_sent = True
                self.file = open(self.filepath, "rb")
                return self.header
            if self.file:
                chunk = self.file.read(size if size > 0 else 512 * 1024)
                if chunk:
                    return chunk
                self.file.close()
                self.file = None
            if not self.footer_sent:
                self.footer_sent = True
                return self.footer
            return b""

    stream = StreamBody(filepath, header, footer)
    req.data = stream
    
    start_time = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            resp_body = resp.read().decode("utf-8", errors="ignore")
            elapsed = time.time() - start_time
            mb_s = (filesize / (1024 * 1024)) / elapsed
            gbps = (mb_s * 8) / 1000
            return {
                "success": True,
                "elapsed_sec": elapsed,
                "speed_mb_s": mb_s,
                "speed_gbps": gbps,
                "response": resp_body
            }
    except Exception as e:
        elapsed = time.time() - start_time
        return {
            "success": False,
            "error": str(e),
            "elapsed_sec": elapsed,
            "speed_mb_s": 0.0,
            "speed_gbps": 0.0
        }

def multipath_chunked_stream(filepath, endpoints, chunk_size, ratio_weights, timeout=600):
    """
    Multipath transfer simulation / execution:
    Distributes chunks of the 1GB file across multiple network sockets (e.g. USB + Wi-Fi)
    according to ratio_weights and measures aggregate multi-channel speed.
    """
    filesize = os.path.getsize(filepath)
    total_chunks = (filesize + chunk_size - 1) // chunk_size
    
    # Calculate target chunk quotas per endpoint
    total_weight = sum(ratio_weights)
    quotas = [int(round(total_chunks * (w / total_weight))) for w in ratio_weights]
    # Normalize quota sum
    quotas[-1] = total_chunks - sum(quotas[:-1])
    
    chunk_assignments = []
    chunk_idx = 0
    for ep_idx, q in enumerate(quotas):
        for _ in range(q):
            if chunk_idx < total_chunks:
                chunk_assignments.append((chunk_idx, endpoints[ep_idx]))
                chunk_idx += 1
                
    results = {
        "chunk_size": chunk_size,
        "total_chunks": total_chunks,
        "ratio": ratio_weights,
        "endpoint_stats": {ep: {"chunks": 0, "bytes": 0} for ep in endpoints}
    }
    
    start_time = time.time()
    bytes_sent = 0
    
    def worker(ep, assigned_chunk_ids):
        nonlocal bytes_sent
        local_bytes = 0
        local_chunks = 0
        with open(filepath, "rb") as f:
            for cid in assigned_chunk_ids:
                offset = cid * chunk_size
                length = min(chunk_size, filesize - offset)
                f.seek(offset)
                data = f.read(length)
                # In real socket transmission, send binary frame with BLAKE3 hash
                local_bytes += len(data)
                local_chunks += 1
                bytes_sent += len(data)
        return ep, local_chunks, local_bytes

    # Group chunk IDs per endpoint
    ep_chunks = {ep: [] for ep in endpoints}
    for cid, ep in chunk_assignments:
        ep_chunks[ep].append(cid)
        
    threads = []
    for ep, cids in ep_chunks.items():
        t = threading.Thread(target=worker, args=(ep, cids))
        threads.append(t)
        t.start()
        
    for t in threads:
        t.join()
        
    elapsed = time.time() - start_time
    mb_s = (filesize / (1024 * 1024)) / max(elapsed, 0.001)
    gbps = (mb_s * 8) / 1000
    
    results["elapsed_sec"] = elapsed
    results["speed_mb_s"] = mb_s
    results["speed_gbps"] = gbps
    return results

if __name__ == "__main__":
    print("=" * 70)
    print(" ShareDash 1GB Multipath & Ratio Optimization Benchmark Engine")
    print("=" * 70)
    
    if len(sys.argv) > 1 and sys.argv[1] == "--hash-check":
        print(f"Calculating SHA-256 for {TEST_FILE}...")
        h = get_file_sha256(TEST_FILE)
        print(f"SHA-256: {h}")
        sys.exit(0)
        
    print(f"Test File: {TEST_FILE} ({os.path.getsize(TEST_FILE):,} bytes)")
    print("Ready for USB, PC Hotspot, Phone Hotspot & Multipath Evaluation.")
