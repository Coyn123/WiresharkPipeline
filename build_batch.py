# build_batch.py

def insert_protocol_batches(db, packet_ids, proto_batches):

    for protocol in ["TCP", "UDP", "DNS", "TLS"]:
        proto_values = []

        for idx, proto_fields in proto_batches.get(protocol, []):
            pkt_id = packet_ids[idx]  # Map to correct packet_id

            if protocol == "TCP":
                tcp = proto_fields
                proto_values.append(
                    (pkt_id, tcp["src_port"], tcp["dst_port"], tcp["flags"],
                     tcp["seq"], tcp["ack"], tcp["window_size"])
                )
            elif protocol == "UDP":
                udp = proto_fields
                proto_values.append(
                    (pkt_id, udp["src_port"], udp["dst_port"], udp["length"])
                )
            elif protocol == "DNS":
                dns = proto_fields
                proto_values.append(
                    (pkt_id, dns["query_name"], dns["query_type"], dns["answer_ip"], dns["rcode"])
                )
            elif protocol == "TLS":
                tls = proto_fields
                proto_values.append(
                    (pkt_id, tls["sni"], tls["tls_version"], tls["cipher_suite"], tls["handshake_type"])
                )

        # Insert built tuples into the DB
        if proto_values:
            if protocol == "TCP":
                db.insert_tcp_batch(proto_values)
            elif protocol == "UDP":
                db.insert_udp_batch(proto_values)
            elif protocol == "DNS":
                db.insert_dns_batch(proto_values)
            elif protocol == "TLS":
                db.insert_tls_batch(proto_values)