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

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
TEST_FILE = os.path.join(PROJECT_ROOT, "sharedash_1gb_benchmark.bin")
APK_PATH = os.path.join(PROJECT_ROOT, r"android\app\build\outputs\apk\debug\app-debug.apk")
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
    boundary = f"ShareDashBench{int(time.time()*1000)}"

    header = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
    ).encode("utf-8")
    footer = f"\r\n--{boundary}--\r\n".encode("utf-8")
    total_len = len(header) + filesize + len(footer)

    req = urllib.request.Request(
        target_url,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(total_len)
        },
        method="POST"
    )

    class StreamBody:
        def __init__(self, path, h, f):
            self.path = path
            self.h = h
            self.f = f
            self.f_handle = None
            self.h_done = False
            self.f_done = False

        def read(self, size=-1):
            if not self.h_done:
                self.h_done = True
                self.f_handle = open(self.path, "rb")
                return self.h
            if self.f_handle:
                chunk = self.f_handle.read(size if size > 0 else 1024 * 1024)
                if chunk:
                    return chunk
                self.f_handle.close()
                self.f_handle = None
            if not self.f_done:
                self.f_done = True
                return self.f
            return b""

    streamer = StreamBody(filepath, header, footer)
    print(f"\n🚀 Running {label}: Streaming {filesize_mb:.2f} MB to {target_url}...")
    t0 = time.time()

    try:
        with urllib.request.urlopen(req, data=streamer, timeout=300) as resp:
            t1 = time.time()
            elapsed = t1 - t0
            speed_mb = filesize_mb / elapsed
            speed_gbps = (filesize * 8) / (elapsed * 1_000_000_000)
            print(f"✔ {label} COMPLETED in {elapsed:.2f}s | Speed: {speed_mb:.2f} MB/s ({speed_gbps:.3f} Gbps)")
            return {
                "status": "PASS",
                "elapsed_sec": elapsed,
                "speed_mb_s": speed_mb,
                "speed_gbps": speed_gbps,
                "status_code": resp.status
            }
    except Exception as e:
        elapsed = time.time() - t0
        print(f"❌ {label} FAILED after {elapsed:.2f}s: {e}")
        return {"status": "FAIL", "error": str(e), "elapsed_sec": elapsed}

def run_suite():
    print("=" * 65)
    print("      ShareDash End-to-End Multipath Benchmark Suite")
    print("=" * 65)

    serial, state, raw = check_adb_state()
    print(f"ADB Device: {serial} ({state})")
    if not serial:
        print("❌ No ADB device found. Please connect an Android phone with USB debugging enabled.")
        return

    setup_adb()
    install_and_start_app()

    usb_url = "http://127.0.0.1:54325/api/v1/transfers/send"

    ok, info = probe_phone("http://127.0.0.1:54325")
    if not ok:
        print(f"❌ Cannot connect to phone HTTP server: {info}")
        return
    print(f"✔ Connected to: {info.get('device_name', 'Android')} (v{info.get('version', '?')})")

    if not os.path.exists(TEST_FILE):
        print(f"Creating test file: {TEST_FILE}...")
        subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "generate_test_file.py")])

    res = perform_stream_transfer(usb_url, TEST_FILE, "USB 3.x Line-Speed Transfer")
    print("\nBenchmark Result Summary:", json.dumps(res, indent=2))

if __name__ == "__main__":
    run_suite()
