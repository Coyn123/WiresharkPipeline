def classify(values):
    """
    Classify based on raw CSV values.
    values = list of CSV fields
    """

    # Must have at least IP header fields
    if len(values) < 7:
        return "UNKNOWN"

    ip_proto = values[4]

    # If protocol field missing → unknown
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

    # DNS is UDP/TCP port 53 or has DNS fields
    if len(values) > 16 and values[16]:
        return "DNS"

    # TLS handshake fields
    if len(values) > 20 and values[20]:
        return "TLS"

    return "IP"
