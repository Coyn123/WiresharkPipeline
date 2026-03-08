def classify(values):
    # Less than 7 may be corrupted packet line
    if len(values) < 7:
        return "UNKNOWN"

    #IP Protocol for FSM-quality
    ip_proto = values[4]


    #Return Protocol Type
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
