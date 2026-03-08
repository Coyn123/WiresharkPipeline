# main.py
from capture import init_capture
from parser import parse_packet
from classifier import classify
from db import PacketDB
import time
import sys

def read_nonblocking(process):
    buffer = ""

    while True:
        c = process.stdout.read(1)

        # If process ended and buffer has content, yield it
        if c == "" and process.poll() is not None:
            if buffer:
                yield buffer
            return

        if not c:
            # No data available yet
            time.sleep(0.01)
            continue

        if c == "\n":
            yield buffer
            buffer = ""
        else:
            buffer += c


def main():
    print("[INFO] Starting main...", flush=True)

    process = None
    db = None

    # -----------------------------
    # Initialize TShark
    # -----------------------------
    try:
        print("[INFO] Initializing TShark capture...", flush=True)
        process = init_capture()
        if not process or not process.stdout:
            raise RuntimeError("TShark process failed to start or stdout unavailable")
        print("[INFO] TShark process started.", flush=True)
    except Exception as e:
        print(f"[ERROR] Initialization failed: {e}", flush=True)
        return

    # -----------------------------
    # Initialize DB
    # -----------------------------
    try:
        db = PacketDB()
        print("[INFO] Connected to database.", flush=True)
    except Exception as e:
        print(f"[ERROR] DB connection failed: {e}", flush=True)
        if process:
            process.terminate()
        return

    print("[INFO] Reading live packets...", flush=True)

    line_count = 0

    try:
        for line in read_nonblocking(process):

            line = line.strip()
            if not line:
                continue

            line_count += 1
            print(f"[DEBUG] Raw line {line_count}: {line}", flush=True)

            # Parse CSV line into structured packet
            parsed = parse_packet(line)
            if not parsed:
                continue

            # Insert main packet into DB
            try:
                packet_id = db.insert_packet(parsed["base"])
                if not packet_id:
                    continue
            except Exception as e:
                print(f"[WARN] Failed to insert packet: {e}", flush=True)
                continue

            # Classify protocol
            protocol = parsed["protocol"]
            proto_fields = parsed["proto_fields"]

            # Insert into protocol-specific tables
            try:
                if protocol == "TCP":
                    db.insert_tcp(packet_id, proto_fields)
                elif protocol == "UDP":
                    db.insert_udp(packet_id, proto_fields)
                elif protocol == "DNS":
                    db.insert_dns(packet_id, proto_fields)
                elif protocol == "TLS":
                    db.insert_tls(packet_id, proto_fields)
            except Exception as e:
                print(f"[WARN] Failed to insert {protocol} data: {e}", flush=True)

            # Progress log
            if line_count % 100 == 0:
                print(f"[INFO] Processed {line_count} packets", flush=True)

    except KeyboardInterrupt:
        print("\n[INFO] Capture stopped by user.", flush=True)

    except Exception as e:
        print(f"[ERROR] Capture failed: {e}", flush=True)

    finally:
        print("[INFO] Cleaning up...", flush=True)
        if process:
            try:
                process.terminate()
                print("[INFO] TShark subprocess terminated.", flush=True)
            except Exception as e:
                print(f"[WARN] Failed to terminate TShark: {e}", flush=True)

        if db:
            try:
                db.close()
                print("[INFO] Database connection closed.", flush=True)
            except Exception as e:
                print(f"[WARN] Failed to close DB: {e}", flush=True)


if __name__ == "__main__":
    main()
