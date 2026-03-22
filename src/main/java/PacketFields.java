public final class PacketFields {

    private PacketFields() {}

    // Unix epoch timestamp in milliseconds — we parse this into a readable
    // datetime string before storing. PacketRecords.BasePacket.formatTimestamp().
    public static final String TIMESTAMP  = "timestamp";

    public static final String IP_SRC = "ip_src";
    public static final String IP_DST = "ip_dst";
    public static final String IP_VERSION = "ip_version";
    public static final String IP_PROTO = "ip_proto";
    public static final String FRAME_LEN = "frame_len";// total packet size in bytes
    public static final String IP_TTL = "ip_ttl";// time-to-live hop count

    //IPV6
    public static final String IPV6_SRC   = "ipv6_src";// source IP (IPv6 only)
    public static final String IPV6_DST   = "ipv6_dst";// destination IP (IPv6 only)
    public static final String IPV6_NXT   = "ipv6_nxt"; // next header — equivalent of ip_proto
    public static final String IPV6_HLIM  = "ipv6_hlim";


    public static final String TCP_SRC_PORT = "tcp_srcport";
    public static final String TCP_DST_PORT = "tcp_dstport";
    public static final String TCP_FLAGS = "tcp_flags";// hex bitmask e.g. "0x002" = SYN
    public static final String TCP_SEQ = "tcp_seq";// sequence number
    public static final String TCP_ACK = "tcp_ack";// acknowledgement number
    public static final String TCP_WINDOW_SIZE = "tcp_window_size";// receive window size


    public static final String UDP_SRC_PORT = "udp_srcport";
    public static final String UDP_DST_PORT = "udp_dstport";
    public static final String UDP_LENGTH   = "udp_length";   // payload length in bytes

    public static final String DNS_QRY_NAME = "dns_qry_name"; // domain being looked up e.g. "google.com"
    public static final String DNS_QRY_TYPE = "dns_qry_type"; // record type: 1=A, 28=AAAA, 15=MX etc.
    public static final String DNS_A = "dns_a"; // resolved IPv4 address (in responses)
    public static final String DNS_FLAGS_RCODE = "dns_flags_rcode"; // 0=no error, 3=NXDOMAIN etc.


    public static final String TLS_SNI = "tls_handshake_extensions_server_name";
    public static final String TLS_RECORD_VERSION = "tls_record_version";// raw hex e.g. "0x0303"
    public static final String TLS_CIPHER_SUITE = "tls_handshake_ciphersuite";
    public static final String TLS_HANDSHAKE_TYPE = "tls_handshake_type";// 1=ClientHello, 2=ServerHello
}