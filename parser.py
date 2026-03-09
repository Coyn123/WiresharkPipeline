from classifier import classify
from datetime import datetime

def parse_frame_time(ts_str):
    try:
        main, tz = ts_str[:-5], ts_str[-5:]

        if "." in main:
            date_part, frac = main.split(".", 1)
            main = f"{date_part}.{frac[:6]}"
        else:
            main = f"{main}.000000"

        dt = datetime.strptime(main + tz, "%Y-%m-%dT%H:%M:%S.%f%z")

        return dt.strftime("%Y-%m-%d %H:%M:%S")
    except Exception as e:
        print("[WARN] Failed to parse frame.time:", ts_str, e)
        return None





#0 - 6
BASE_FIELDS = [
    "timestamp", "src_ip", "dst_ip", "ip_version", "ip_proto", "frame_len", "ttl"
]
#7-12 
TCP_DB_FIELDS = ["src_port", "dst_port", "flags", "seq", "ack", "window_size"]
#13-15
UDP_DB_FIELDS = ["src_port", "dst_port", "length"]
#16-19
DNS_DB_FIELDS = ["query_name", "query_type", "answer_ip", "rcode"]
#20-23
TLS_DB_FIELDS = ["sni", "tls_version", "cipher_suite", "handshake_type"]

#Needed for empty str -> Null store
def safe_int(val):
    if not val:
        return None
    try:
        return int(val)
    except:
        try:
            return int(val, 16)
        except:
            return None


def parse_packet(line):
    try:
        line = line.strip().rstrip(",")
        if not line:
            return None

        values = line.split(",")

        # Ensure at least base fields exist
        if len(values) < 7:
            values += [""] * (7 - len(values))

        # Classify based on raw CSV
        proto = classify(values)

        # Base packet
        base = {
            "timestamp": parse_frame_time(values[0]),
            "src_ip": values[1],
            "dst_ip": values[2],
            "ip_version": safe_int(values[3]),
            "ip_proto": safe_int(values[4]),
            "frame_len": safe_int(values[5]),
            "ttl": safe_int(values[6]),
        }
        # Protocol-specific extraction
        if proto == "TCP":
            raw = values[7:13]
            raw += [""] * (6 - len(raw))
            proto_fields = dict(zip(TCP_DB_FIELDS, [safe_int(v) for v in raw]))

        elif proto == "UDP":
            raw = values[13:16]
            raw += [""] * (3 - len(raw))
            proto_fields = dict(zip(UDP_DB_FIELDS, [safe_int(v) for v in raw]))

        elif proto == "DNS":
            raw = values[16:20]
            raw += [""] * (4 - len(raw))
            proto_fields = dict(zip(DNS_DB_FIELDS, raw))
            proto_fields["rcode"] = safe_int(proto_fields["rcode"])

        elif proto == "TLS":
            raw = values[20:24]
            raw += [""] * (4 - len(raw))
            proto_fields = dict(zip(TLS_DB_FIELDS, raw))

        else:
            proto_fields = {}


        return {
            "protocol": proto,
            "base": base,
            "proto_fields": proto_fields
        }

    except Exception as e:
        print(f"[WARN] Failed to parse tshark line: {e}")
        return None
