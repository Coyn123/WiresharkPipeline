import sys
import time
import subprocess

def init_capture():
    cmd = [
        r"C:\Program Files\Wireshark\tshark.exe",
        "-n", # don't resolve names
        "-i", "4", # network interface
        "-T", "fields",  # output fields
        "-E", "separator=,", # CSV separator
        "-E", "occurrence=f", # first occurrence only
        "-E", "header=n", # no headers
        # Packet level
        "-e", "frame.time",
        "-e", "ip.src",
        "-e", "ip.dst",
        "-e", "ip.version",
        "-e", "ip.proto",
        "-e", "frame.len",
        "-e", "ip.ttl",
        # TCP fields
        "-e", "tcp.srcport",
        "-e", "tcp.dstport",
        "-e", "tcp.flags",
        "-e", "tcp.seq",
        "-e", "tcp.ack",
        "-e", "tcp.window_size",
        # UDP fields
        "-e", "udp.srcport",
        "-e", "udp.dstport",
        "-e", "udp.length",
        # DNS fields
        "-e", "dns.qry.name",
        "-e", "dns.qry.type",
        "-e", "dns.a",
        "-e", "dns.flags.rcode",
        # TLS fields
        "-e", "tls.handshake.extensions_server_name",
        "-e", "tls.handshake.version",
        "-e", "tls.handshake.ciphersuite",
        "-e", "tls.handshake.type"
    ]
    try:
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )

        # Give tshark time to start and emit errors
        time.sleep(0.2)

        # Check for startup errors
        if process.poll() is not None:
            err = process.stderr.read()
            raise RuntimeError(f"TShark exited immediately:\n{err}")

        return process

    except (OSError, Exception, RuntimeError) as e:
        print("[ERROR] Failed:", e, flush=True)
        sys.exit(1)