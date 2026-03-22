import sys
import time
import queue
import threading
import subprocess
from config import get_active_interface, TSHARK_PATH


# tshark process
def init_capture():
    cmd = [
        TSHARK_PATH,
        "-n",
        "-i", get_active_interface(),
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

        "-e", "ipv6.src",
        "-e", "ipv6.dst",
        "-e", "ipv6.nxt",
        "-e", "ipv6.hlim",

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

        time.sleep(0.2)

        if process.poll() is not None:
            raise RuntimeError(f"TShark exited immediately:\n{process.stderr.read()}")

        return process

    except (OSError, RuntimeError) as e:
        print(f"[ERROR] TShark failed to start: {e}", flush=True)
        sys.exit(1)

def drain_stderr(process):
    for line in process.stderr:
        print(f"  [tshark] {line}", end="", flush=True)


# packet reader
def read_packets(process, on_idle=None):
    q = queue.Queue()

    def reader():
        for line in process.stdout:
            q.put(line)
        q.put(None) # sentinel

    threading.Thread(target=reader, daemon=True).start()

    while True:
        try:
            line = q.get(timeout=5.0)
        except queue.Empty:
            if on_idle:
                on_idle()
            continue
        if line is None:
            break
        line = line.strip()
        if not line or line.startswith('{"index":'):
            continue
        yield line