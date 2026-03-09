def classify(values):
    if len(values) < 7:
        return "UNKNOWN"

    ip_proto = values[4]

    if not ip_proto:
        return "UNKNOWN"

    try:
        proto = int(ip_proto)
    except:
        return "UNKNOWN"

    # DNS first (UDP or TCP)
    if len(values) > 16 and values[16]:
        return "DNS"

    # TLS handshake next (always TCP)
    if len(values) > 20 and values[20]:
        return "TLS"

    # Then raw TCP/UDP
    if proto == 6:
        return "TCP"
    if proto == 17:
        return "UDP"

    return "IP"
