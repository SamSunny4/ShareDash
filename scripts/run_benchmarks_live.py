import os
import sys
import time
import json
import subprocess
import urllib.request
import urllib.error
from urllib.parse import urljoin

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
TEST_FILE = os.path.join(PROJECT_ROOT, "sharedash_1gb_benchmark.bin")
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

if __name__ == "__main__":
    serial, state = check_adb_device()
    print(f"ADB Device: {serial} ({state})")
    if serial:
        setup_adb_forward()
        print("ADB forward established.")
