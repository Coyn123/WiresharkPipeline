def classify(values):
    # Must have at least IP header fields
    if len(values) < 7:
        return "UNKNOWN"

    ip_proto = values[4]

    if not ip_proto:
        return "UNKNOWN"

    try:
        proto = int(ip_proto)
    except:
        return "UNKNOWN"

    if proto == 6:
        return "TCP"
    if proto == 17:
        return "UDP"

    if len(values) > 16 and values[16]:
        return "DNS"

    if len(values) > 20 and values[20]:
        return "TLS"

    return "IP"
