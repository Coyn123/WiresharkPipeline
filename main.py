from capture import init_capture
import time
import json
import subprocess
import threading


class JavaParser:
    def __init__(self, jar_path):
        self.seq  = 0
        self.proc = subprocess.Popen(
            ["java", "-jar", jar_path],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, bufsize=1,
        )
        threading.Thread(target=self.drain_stderr, daemon=True).start()

    def drain_stderr(self):
        for line in self.proc.stderr:
            print(f"  [Java] {line}", end="", flush=True)

    def send_line(self, raw_json: str):
        self.proc.stdin.write(raw_json.replace("\n", " ") + "\n")
        self.proc.stdin.flush()

    def flush_batch(self, seq) -> dict:
        if self.proc.poll() is not None:
            return {"status": "error", "count": 0}
        self.proc.stdin.write(json.dumps({"cmd": "flush", "seq": seq}) + "\n")
        self.proc.stdin.flush()
        resp = self.proc.stdout.readline()
        return json.loads(resp) if resp else {"status": "error", "count": 0}

    def close(self):
        self.proc.stdin.close()
        self.proc.wait()


class Pipeline:

    JAR_PATH       = r"target\wireshark-parser-1.0-SNAPSHOT.jar"
    FLUSH_INTERVAL = 15.0
    MAX_BATCH      = 1000

    def __init__(self):
        self.process    = None
        self.java       = None
        self.seq        = 0
        self.line_count = 0
        self.last_flush = time.time()

    # Startup

    def start(self):
        print("[INFO] Starting pipeline...", flush=True)
        self.init_tshark()
        self.init_java()

    def init_tshark(self):
        print("[INFO] Initializing TShark capture...", flush=True)
        self.process = init_capture()
        if not self.process or not self.process.stdout:
            raise RuntimeError("TShark process failed to start or stdout unavailable")
        threading.Thread(target=self.drain_tshark_stderr, daemon=True).start()
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
        for line in self.process.stdout:
            line = line.strip()
            if not line or line.startswith('{"index"}'):
                continue
            #print(f"[DEBUG] Raw packet:\n{line}\n", flush=True)
            yield line

    # Flush

    def should_flush(self):
        return (
            time.time() - self.last_flush >= self.FLUSH_INTERVAL
            or self.line_count % self.MAX_BATCH == 0
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
                self.java.send_line(raw)

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
                print(f"[WARN] Failed to terminate TShark: {e}", flush=True)


# Spawn
if __name__ == "__main__":
    pipeline = Pipeline()
    try:
        pipeline.start()
        pipeline.run()
    finally:
        pipeline.stop()