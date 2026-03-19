from capture import init_capture
from services import start_services
import time
import json
import subprocess
import threading


class JavaParser:
    def __init__(self, jar_path):
        self.seq = 0
        self.batch = []
        self.proc = subprocess.Popen(
            ["java", "-jar", jar_path],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, bufsize=1,
        )
        threading.Thread(target=self.drain_stderr, daemon=True).start()

    def drain_stderr(self):
        for line in self.proc.stderr:
            print(f"  {line}", end="", flush=True)

    def add_to_batch(self, raw_json: str):
        self.batch.append(json.loads(raw_json))

    def flush_batch(self, seq) -> dict:
        if self.proc.poll() is not None:
            return {"status": "error", "count": 0}
        try:
            payload = json.dumps({"cmd": "flush", "seq": seq, "packets": self.batch})
            self.proc.stdin.write(payload + "\n")
            self.proc.stdin.flush()
            self.batch = []
            resp = self.proc.stdout.readline()
            return json.loads(resp) if resp else {"status": "error", "count": 0}
        except Exception as e:
            print(f"[ERROR] ERROR IN FLUSH: {e}", flush=True)
            return {"status": "error", "count": 0}

    def close(self):
        self.proc.stdin.close()
        self.proc.wait()


class Pipeline:
    JAR_PATH = r"target/wireshark-parser-1.0-SNAPSHOT.jar"
    FLUSH_INTERVAL = 15.0
    MAX_BATCH = 1000

    def __init__(self):
        self.process = None
        self.java = None
        self.seq = 0
        self.line_count = 0
        self.last_flush = time.time()

    # Startup

    def start(self):
        print("[INFO] Starting pipeline...", flush=True)
        self.init_tshark()
        self.init_java()
        if not start_services():
            print("[ERROR] Failed to start required services, exiting")
            exit(1)


    def init_tshark(self):
        print("[INFO] Initializing TShark capture...", flush=True)
        self.process = init_capture()
        if not self.process or not self.process.stdout:
            raise RuntimeError(
                "TShark process failed to start or stdout unavailable")
        threading.Thread(target=self.drain_tshark_stderr,
                         daemon=True).start()
        print("[INFO] TShark process started.", flush=True)

    def drain_tshark_stderr(self):
        for line in self.process.stderr:
            print(f"  [tshark] {line}", end="", flush=True)

    def init_java(self):
        print("[INFO] Initializing Java parser...", flush=True)
        self.java = JavaParser(self.JAR_PATH)
        print("[INFO] Java parser started.", flush=True)

    # Packet stream

    def read_packets(self):
        import select
        while True:
            ready = select.select([self.process.stdout], [], [], 5.0)[0]
            if ready:
                line = self.process.stdout.readline()
                if not line:
                    break
                line = line.strip()
                if not line or line.startswith('{"index":'):
                    continue
                yield line
            else:
                # timeout — flush whatever we have
                if len(self.java.batch) > 0:
                    self.flush()

    # Flush

    def should_flush(self):
        if len(self.java.batch) == 0:
            return False  # never flush empty batch
        return (
            time.time() - self.last_flush >= self.FLUSH_INTERVAL
            or len(self.java.batch) >= self.MAX_BATCH
        )

    def flush(self):
        self.seq += 1
        result = self.java.flush_batch(self.seq)
        self.last_flush = time.time()
        print(f"[INFO] Java batch result: {result}", flush=True)

    # Main loop

    def run(self):
        print("[INFO] Reading live packets...", flush=True)
        try:
            for raw in self.read_packets():
                if not raw:
                    continue

                self.line_count += 1
                self.java.add_to_batch(raw)

                if self.should_flush():
                    self.flush()

        except KeyboardInterrupt:
            print("\n[INFO] Capture stopped by user.", flush=True)
        except Exception as e:
            print(f"[ERROR] Capture failed: {e}", flush=True)

    # Shutdown
    def stop(self):
        print("[INFO] Cleaning up...", flush=True)

        # Final flush
        if self.java and self.line_count > 0:
            self.flush()

        if self.java:
            self.java.close()
            print("[INFO] Java process terminated.", flush=True)

        if self.process:
            try:
                self.process.terminate()
                print("[INFO] TShark subprocess terminated.", flush=True)
            except Exception as e:
                print(
                    f"[WARN] Failed to terminate TShark: {e}", flush=True)


# Spawn
if __name__ == "__main__":
    pipeline = Pipeline()
    try:
        pipeline.start()
        pipeline.run()
    finally:
        pipeline.stop()
