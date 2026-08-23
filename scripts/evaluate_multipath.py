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

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHUNK_SIZES_TO_TEST = [256 * 1024, 512 * 1024, 1024 * 1024, 2 * 1024 * 1024, 4 * 1024 * 1024, 8 * 1024 * 1024]
TEST_FILE = os.path.join(PROJECT_ROOT, "sharedash_1gb_benchmark.bin")

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
            
    body = StreamBody(filepath, header, footer)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, data=body, timeout=timeout) as resp:
            t1 = time.time()
            elapsed = t1 - t0
            speed_mb = (filesize / (1024 * 1024)) / elapsed
            return True, elapsed, speed_mb, resp.status
    except Exception as e:
        t1 = time.time()
        return False, t1 - t0, 0.0, str(e)

if __name__ == "__main__":
    print("ShareDash Multipath Evaluator")
    if not os.path.exists(TEST_FILE):
        print(f"Generating test file: {TEST_FILE}")
        subprocess = __import__("subprocess")
        subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "generate_test_file.py")])
    
    print(f"Ready with test file: {TEST_FILE} ({os.path.getsize(TEST_FILE):,} bytes)")
