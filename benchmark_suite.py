import os
import sys
import time
import json
import hashlib
import subprocess
import urllib.request
import urllib.error
from urllib.parse import urljoin
from concurrent.futures import ThreadPoolExecutor

ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
TEST_FILE = "sharedash_1gb_benchmark.bin"
APK_PATH = r"android\app\build\outputs\apk\debug\app-debug.apk"
EXPECTED_SHA256 = "2c06ade942ee3f17a048dd1064b2fab046a4bb95386d8bb41b68dc6711ac2af3"

def run_cmd(cmd):
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=True)
    return res.returncode, res.stdout.strip(), res.stderr.strip()

def check_adb_state():
    code, out, _ = run_cmd(f'"{ADB_PATH}" devices -l')
    for line in out.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) >= 2:
            return parts[0], parts[1], line
    return None, "disconnected", ""

def setup_adb():
    print("⚡ Configuring ADB port forwarding...")
    run_cmd(f'"{ADB_PATH}" forward tcp:54325 tcp:54321')
    run_cmd(f'"{ADB_PATH}" reverse tcp:54321 tcp:54321')
    print("✔ Forwarded: 127.0.0.1:54325 -> Phone:54321")

def install_and_start_app():
    if os.path.exists(APK_PATH):
        print(f"📦 Installing {APK_PATH}...")
        c, o, e = run_cmd(f'"{ADB_PATH}" install -r "{APK_PATH}"')
        print(f"  Install result: {o or e}")
    print("🚀 Launching ShareDash on phone...")
    run_cmd(f'"{ADB_PATH}" shell am start -n com.sharedash.app/.MainActivity')
    time.sleep(3)

def probe_phone(base_url):
    url = urljoin(base_url, "/api/v1/info")
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=4) as resp:
            data = json.loads(resp.read().decode())
            return True, data
    except Exception as e:
        return False, str(e)

def perform_stream_transfer(target_url, filepath, label="Transfer"):
    filename = os.path.basename(filepath)
    filesize = os.path.getsize(filepath)
    filesize_mb = filesize / (1024 * 1024)
    boundary = f"ShareDashBoundary{int(time.time()*1000)}"
    
    header = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
    ).encode("utf-8")
    footer = f"\r\n--{boundary}--\r\n".encode("utf-8")
    total_len = len(header) + filesize + len(footer)
    
    class Streamer:
        def __init__(self, path):
            self.path = path
            self.file = None
            self.phase = 0
            self.sent = 0
            self.start = time.time()
            self.last_report = time.time()
            
        def read(self, size=-1):
            if self.phase == 0:
                self.phase = 1
                self.file = open(self.path, "rb")
                return header
            elif self.phase == 1:
                chunk = self.file.read(512 * 1024)
                if chunk:
                    self.sent += len(chunk)
                    now = time.time()
                    if now - self.last_report >= 0.5:
                        pct = (self.sent / filesize) * 100
                        el = now - self.start
                        cur_mb_s = (self.sent / (1024 * 1024)) / max(el, 0.01)
                        print(f"\r  [{label}] Progress: {pct:.1f}% ({self.sent/(1024*1024):.1f}/{filesize_mb:.1f} MB) · {cur_mb_s:.1f} MB/s", end="", flush=True)
                        self.last_report = now
                    return chunk
                self.file.close()
                self.phase = 2
                return footer
            return b""

    endpoint = urljoin(target_url, "/api/v1/transfers/send")
    req = urllib.request.Request(
        endpoint,
        data=Streamer(filepath),
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(total_len)
        },
        method="POST"
    )
    
    start_time = time.time()
    try:
        with urllib.request.urlopen(req, timeout=1200) as resp:
            body = resp.read().decode("utf-8", errors="ignore")
            elapsed = time.time() - start_time
            mb_s = filesize_mb / max(elapsed, 0.01)
            gbps = (mb_s * 8) / 1000
            print(f"\r  [{label}] Done: 100.0% ({filesize_mb:.1f} MB) in {elapsed:.2f}s · Avg: {mb_s:.2f} MB/s ({gbps:.2f} Gbps)")
            return {
                "success": True,
                "elapsed_sec": elapsed,
                "speed_mb_s": mb_s,
                "speed_gbps": gbps,
                "response": body
            }
    except Exception as e:
        elapsed = time.time() - start_time
        print(f"\r  [{label}] Error after {elapsed:.2f}s: {e}")
        return {
            "success": False,
            "error": str(e),
            "elapsed_sec": elapsed,
            "speed_mb_s": 0.0,
            "speed_gbps": 0.0
        }

def verify_file_on_phone(phone_filename):
    # Check sha256 of received file on phone
    cmd = f'"{ADB_PATH}" shell sha256sum /sdcard/Download/ShareDash/{phone_filename}'
    c, o, e = run_cmd(cmd)
    if c == 0 and o:
        phone_hash = o.split()[0]
        match = (phone_hash == EXPECTED_SHA256)
        return match, phone_hash
    return False, o or e

if __name__ == "__main__":
    serial, state, raw = check_adb_state()
    print(f"ADB Status: {serial} -> {state}")
