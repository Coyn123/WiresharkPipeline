from capture import init_capture
from parser import parse_packet
from classifier import classify
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
        tcp_batch = []
        udp_batch = []
        dns_batch = []
        tls_batch = []
        for line in read_line(process):

            line = line.strip()
            if not line:
                continue

            line_count += 1

            # Parse Here
            parsed = parse_packet(line)
            if not parsed:
                continue

            packet_batch.append(parsed["base"])
            idx = len(packet_batch) - 1 

            # Classify here
            protocol = parsed["protocol"]
            proto_fields = parsed["proto_fields"]


            # Insert here
            try:
                if protocol == "TCP":
                    tcp_batch.append((idx, proto_fields))
                elif protocol == "UDP":
                    udp_batch.append((idx, proto_fields))
                elif protocol == "DNS":
                    dns_batch.append((idx, proto_fields))
                elif protocol == "TLS":
                    tls_batch.append((idx, proto_fields))
            except Exception as e:
                print(f"[WARN] Failed to append {protocol} data: {e}", flush=True)



            # Progress here
            should_flush = False
            if line_count % 100 == 0:
                should_flush = True

            if time.time() - last_flush >= FLUSH_INTERVAL:
                should_flush = True
            if should_flush:
                print(f"[DEBUG] Raw line {line_count}: {line}", flush=True)
                print(f"[INFO] Processed {line_count} packets", flush=True)

                try:
                    packet_ids = db.insert_packet_batch(packet_batch)

                    if tcp_batch:
                        db.insert_tcp_batch(packet_ids, tcp_batch)
                    
                    if udp_batch:
                        db.insert_udp_batch(packet_ids, udp_batch)

                    if dns_batch:
                        db.insert_dns_batch(packet_ids, dns_batch)
                        
                    if tls_batch:
                        db.insert_tls_batch(packet_ids, tls_batch)
                    
                    packet_batch.clear()
                    tcp_batch.clear()
                    udp_batch.clear()
                    dns_batch.clear()
                    tls_batch.clear()

                    last_flush = time.time()

                    print(f"[INFO] Inserted batch of 100 packets", flush=True)
                    
                except Exception as e:
                    print(f"[WARN] Batch insert failed: {e}", flush=True)

    #Final Block Here
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

            if tcp_batch:
                db.insert_tcp_batch(packet_ids, tcp_batch)

            if udp_batch:
                db.insert_udp_batch(packet_ids, udp_batch)

            if dns_batch:
                db.insert_dns_batch(packet_ids, dns_batch)

            if tls_batch:
                db.insert_tls_batch(packet_ids, tls_batch)

        #DB Close Here
        if db:
            try:
                db.close()
                print("[INFO] Database connection closed.", flush=True)
            except Exception as e:
                print(f"[WARN] Failed to close DB: {e}", flush=True)


if __name__ == "__main__":
    main()
