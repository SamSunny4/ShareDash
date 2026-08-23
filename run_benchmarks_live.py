import os
import sys
import time
import json
import subprocess
import urllib.request
import urllib.error
from urllib.parse import urljoin

ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
TEST_FILE = "sharedash_1gb_benchmark.bin"
PORT_FORWARD = 54325
TARGET_APP_PORT = 54321

def run_cmd(cmd, check=True):
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=True)
    return res.returncode, res.stdout.strip(), res.stderr.strip()

def check_adb_device():
    code, out, _ = run_cmd(f'"{ADB_PATH}" devices -l')
    for line in out.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) >= 2:
            serial, state = parts[0], parts[1]
            return serial, state
    return None, "none"

def setup_adb_forward():
    code, out, err = run_cmd(f'"{ADB_PATH}" forward tcp:{PORT_FORWARD} tcp:{TARGET_APP_PORT}')
    code2, out2, err2 = run_cmd(f'"{ADB_PATH}" reverse tcp:{TARGET_APP_PORT} tcp:{TARGET_APP_PORT}')
    return code == 0

def install_and_launch_app(apk_path):
    print(f"Installing {apk_path} on phone...")
    code, out, err = run_cmd(f'"{ADB_PATH}" install -r "{apk_path}"')
    print(out)
    print("Launching ShareDash app...")
    code, out, err = run_cmd(f'"{ADB_PATH}" shell am start -n com.sharedash.app/.MainActivity')
    print(out)
    time.sleep(2)

def probe_endpoint(url):
    try:
        req = urllib.request.Request(urljoin(url, "/api/v1/info"), method="GET")
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
            return True, data
    except Exception as e:
        return False, str(e)

def stream_file_post(url, filepath):
    filename = os.path.basename(filepath)
    filesize = os.path.getsize(filepath)
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
            
        def read(self, size=-1):
            if self.phase == 0:
                self.phase = 1
                self.file = open(self.path, "rb")
                return header
            elif self.phase == 1:
                chunk = self.file.read(512 * 1024)
                if chunk:
                    return chunk
                self.file.close()
                self.phase = 2
                return footer
            return b""

    req = urllib.request.Request(
        urljoin(url, "/api/v1/transfers/send"),
        data=Streamer(filepath),
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(total_len)
        },
        method="POST"
    )
    
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            body = resp.read().decode("utf-8", errors="ignore")
            elapsed = time.time() - start
            mb_s = (filesize / (1024 * 1024)) / max(elapsed, 0.001)
            return True, elapsed, mb_s, body
    except Exception as e:
        elapsed = time.time() - start
        return False, elapsed, 0.0, str(e)

if __name__ == "__main__":
    serial, state = check_adb_device()
    print(f"Device: {serial} (State: {state})")
