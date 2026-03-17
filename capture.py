import sys
import time
import subprocess
from datetime import datetime


def init_capture():
    cmd = [
        r"/Applications/Wireshark.app/Contents/MacOS/tshark",
        "-n",
        "-i", "1",
        "-T", "ek",
        "-l",
        "-E", "separator=,",
        "-E", "occurrence=f",
        "-E", "header=n",

        # Base fields
        "-e", "frame.time",
        "-e", "ip.src",
        "-e", "ip.dst",
        "-e", "ip.version",
        "-e", "ip.proto",
        "-e", "frame.len",
        "-e", "ip.ttl",

        # TCP
        "-e", "tcp.srcport",
        "-e", "tcp.dstport",
        "-e", "tcp.flags",
        "-e", "tcp.seq",
        "-e", "tcp.ack",
        "-e", "tcp.window_size",

        # UDP
        "-e", "udp.srcport",
        "-e", "udp.dstport",
        "-e", "udp.length",

        # DNS
        "-e", "dns.qry.name",
        "-e", "dns.qry.type",
        "-e", "dns.a",
        "-e", "dns.flags.rcode",

        # TLS
        "-e", "tls.handshake.extensions_server_name",
        "-e", "tls.record.version",
        "-e", "tls.handshake.ciphersuite",
        "-e", "tls.handshake.type",
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
