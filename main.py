from capture import init_capture
from parser import parse_packet
from classifier import classify
from build_batch import insert_protocol_batches
from db import PacketDB
import time
import sys

def read_line(process):
    buffer = ""

    #Line-by-line reader
    while True:
        c = process.stdout.read(1)

        if c == "" and process.poll() is not None:
            if buffer:
                yield buffer
            return

        if not c:
            time.sleep(0.01)
            continue

        if c == "\n":
            yield buffer
            buffer = ""
        else:
            buffer += c


def main():
    print("[INFO] Starting main...", flush=True)

    last_flush = time.time()
    FLUSH_INTERVAL = 15.0
    MAX_BATCH = 1000
    process = None
    db = None

    # Init TShark
    try:
        print("[INFO] Initializing TShark capture...", flush=True)
        process = init_capture()
        if not process or not process.stdout:
            raise RuntimeError("TShark process failed to start or stdout unavailable")
        print("[INFO] TShark process started.", flush=True)
    except Exception as e:
        print(f"[ERROR] Initialization failed: {e}", flush=True)
        return

    # Init DB
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
        packet_batch = []
        packet_light_batch = []

        proto_batches = {
            "TCP": [],
            "UDP": [],
            "DNS": [],
            "TLS": []
        }

        for line in read_line(process):

            line = line.strip()
            if not line:
                continue

            line_count += 1

            # Parse Here
            parsed = parse_packet(line)
            if not parsed:
                continue

            # Define here
            protocol = parsed.get("protocol")
            proto_fields = parsed.get("proto_fields")

            packet_batch.append(parsed.get("base"))
            idx = len(packet_batch) - 1 

            if protocol in proto_batches:
                light_packet = {
                    "timestamp": parsed["base"]["timestamp"],
                    "src_ip": parsed["base"]["src_ip"],
                    "dst_ip": parsed["base"]["dst_ip"],
                }
                packet_light_batch.append(light_packet)
                try:
                    proto_batches[protocol].append((idx, proto_fields))
                except Exception as e:
                    print(f"[WARN] Failed to append {protocol} data: {e}", flush=True)

            # Progress here
            should_flush = False

            if time.time() - last_flush >= FLUSH_INTERVAL or len(packet_batch) >= MAX_BATCH:
                should_flush = True

            if should_flush:
                print(f"[DEBUG] Raw line {line_count}: {line}", flush=True)
                print(f"[INFO] Processed {line_count} packets", flush=True)

                try:
                    packet_ids = db.insert_packet_batch(packet_batch)
                    insert_protocol_batches(db, packet_ids, proto_batches)

                    packet_batch.clear()
                    for batch in proto_batches.values():
                        batch.clear()
                    last_flush = time.time()

                    print(f"[INFO] Inserted batch of packets", flush=True)
                    
                except Exception as e:
                    print(f"[WARN] Batch insert failed: {e}", flush=True)

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

        # Final flush
        if packet_batch:
            packet_ids = db.insert_packet_batch(packet_batch)
            insert_protocol_batches(db, packet_ids, proto_batches)
            packet_batch.clear()

        if packet_light_batch:
            db.insert_light_batch(packet_light_batch)
            packet_light_batch.clear()

        for batch in proto_batches.values():
            batch.clear()

        # DB Close Here
        if db:
            try:
                db.close()
                print("[INFO] Database connection closed.", flush=True)
            except Exception as e:
                print(f"[WARN] Failed to close DB: {e}", flush=True)


if __name__ == "__main__":
    main()