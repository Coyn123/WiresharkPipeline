import time
import subprocess
import threading
from config import check_dependencies, MAX_BATCH, FLUSH_INTERVAL
from services import start_services, JavaParser
from capture import init_capture, drain_stderr, read_packets


class Pipeline:

    def __init__(self):
        self.tshark = None
        self.java = None
        self.count = 0
        self.last_flush = time.time()
        self.interrupted = False

    # Startup
    def start(self):
        print("[INFO] Starting pipeline...", flush=True)
        if not start_services():
            print("[ERROR] Failed to start required services, exiting", flush=True)
            exit(1)

        print("[INFO] Initializing TShark capture...", flush=True)
        self.tshark = init_capture()
        threading.Thread(target=drain_stderr, args=(self.tshark,), daemon=True).start()
        print("[INFO] TShark process started.", flush=True)

        print("[INFO] Initializing Java parser...", flush=True)
        self.java = JavaParser()
        print("[INFO] Java parser started.", flush=True)

    #Flush
    def should_flush(self):
        if not self.java.batch:
            return False
        return (
            len(self.java.batch) >= MAX_BATCH
            or time.time() - self.last_flush >= FLUSH_INTERVAL
        )

    def flush(self):
        result = self.java.flush()
        self.last_flush = time.time()
        print(f"[INFO] Java batch result: {result}", flush=True)

    # Main loop
    def run(self):
        print("[INFO] Reading live packets...", flush=True)
        try:
            for raw in read_packets(self.tshark, on_idle=self.flush):
                self.count += 1
                self.java.add(raw)
                if self.should_flush():
                    self.flush()

        except KeyboardInterrupt:
            self.interrupted = True
            print("\n[INFO] Capture stopped by user.", flush=True)
        except Exception as e:
            print(f"[ERROR] Capture failed: {e}", flush=True)

    # Shutdown
    def stop(self):
        print("[INFO] Cleaning up...", flush=True)

        if self.java and self.count > 0:
            try:
                if self.java.is_alive():
                    self.flush()
            except Exception as e:
                print(f"[WARN] Final flush failed: {e}", flush=True)

        if self.java:
            self.java.close()
            print("[INFO] Java process terminated.", flush=True)

        if self.tshark:
            try:
                self.tshark.terminate()
                self.tshark.wait(timeout=5)
                print("[INFO] TShark subprocess terminated.", flush=True)
            except subprocess.TimeoutExpired:
                self.tshark.kill()
                print("[WARN] TShark force killed.", flush=True)
            except Exception as e:
                print(f"[WARN] Failed to terminate TShark: {e}", flush=True)

#Entry point
if __name__ == "__main__":
    pipeline = Pipeline()
    try:
        check_dependencies()
        pipeline.start()
        pipeline.run()
    finally:
        pipeline.stop()