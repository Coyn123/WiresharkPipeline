import subprocess
import threading
import time
import json
import requests
import pymysql
from config import (
    OS, ES_BIN, MYSQL_BIN,
    MYSQL_HOST, MYSQL_USER, MYSQL_PASS, MYSQL_DB,
    JAR_PATH
)


# MySQL
def start_mysql():
    print("[INFO] Starting MySQL...", flush=True)
    cmd = [MYSQL_BIN, "--console"] if OS == "Windows" else [MYSQL_BIN]
    if OS == "Windows":
        subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NEW_PROCESS_GROUP)
    else:
        subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    for _ in range(15):
        try:
            conn = pymysql.connect(
                host=MYSQL_HOST, user=MYSQL_USER,
                password=MYSQL_PASS, database=MYSQL_DB
            )
            conn.close()
            print("[INFO] MySQL is up", flush=True)
            return True
        except pymysql.err.OperationalError:
            time.sleep(2)

    print("[ERROR] MySQL failed to start", flush=True)
    return False


#Elasticsearch
def start_elasticsearch():
    print("[INFO] Starting Elasticsearch...", flush=True)
    cmd = ["cmd.exe", "/c", ES_BIN] if OS == "Windows" else [ES_BIN]
    if OS == "Windows":
        subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NEW_PROCESS_GROUP)
    else:
        subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    for _ in range(60):
        try:
            if requests.get("http://localhost:9200").status_code == 200:
                print("[INFO] Elasticsearch is up", flush=True)
                return True
        except requests.exceptions.ConnectionError:
            pass
        time.sleep(2)

    print("[ERROR] Elasticsearch failed to start", flush=True)
    return False


def start_services():
    return start_mysql() and start_elasticsearch()

# JavaParser
class JavaParser:
    def __init__(self):
        self.batch = []
        self.seq = 0
        self.proc = subprocess.Popen(
            ["java", "-jar", JAR_PATH],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, bufsize=1,
        )
        threading.Thread(target=self._drain_stderr, daemon=True).start()

    def _drain_stderr(self):
        for line in self.proc.stderr:
            print(f"  {line}", end="", flush=True)

    def is_alive(self):
        return self.proc.poll() is None

    def add(self, raw_json: str):
        self.batch.append(json.loads(raw_json))

    def flush(self) -> dict:
        if not self.is_alive():
            return {"status": "error", "count": 0}
        try:
            self.seq += 1
            payload = json.dumps({"cmd": "flush", "seq": self.seq, "packets": self.batch})
            self.proc.stdin.write(payload + "\n")
            self.proc.stdin.flush()
            self.batch = []
            resp = self.proc.stdout.readline()
            return json.loads(resp) if resp else {"status": "error", "count": 0}
        except Exception as e:
            print(f"[ERROR] Flush failed: {e}", flush=True)
            return {"status": "error", "count": 0}

    #Close Java
    def close(self):
        try:
            if self.proc.stdin and not self.proc.stdin.closed:
                self.proc.stdin.close()
        except Exception as e:
            print(f"[WARN] Could not close Java stdin: {e}", flush=True)

        try:
            self.proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            print("[WARN] Java process didn't exit cleanly, killing...", flush=True)
            self.proc.kill()
            self.proc.wait()
        except Exception as e:
            print(f"[WARN] Java wait failed: {e}", flush=True)