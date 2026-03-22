import com.fasterxml.jackson.annotation.JsonProperty;

public class PacketRecords {
    public enum Protocol {
        TCP,
        UDP,
        DNS,
        TLS,
        UNKNOWN
    }
    public record BasePacket(
            String timestamp,
            @JsonProperty("ip_src") String ipSrc,
            @JsonProperty("ip_dst") String ipDst,
            @JsonProperty("ip_version") String ipVersion,
            @JsonProperty("ip_proto") String ipProto,
            @JsonProperty("frame_len") String frameLen,
            @JsonProperty("ip_ttl") String ipTtl
    ) {
        public BasePacket {
            timestamp = formatTimestamp(timestamp);
        }

        private static String formatTimestamp(String raw) {
            if (raw == null) return null;
            long millis = Long.parseLong(raw);
            return new java.sql.Timestamp(millis)
                    .toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    public sealed interface ProtocolDetail
            permits TcpPacket, UdpPacket, DnsPacket, TlsPacket {}

    public record TcpPacket(
            String srcPort,
            String dstPort,
            String flags,
            String seq,
            String ack,
            String windowSize
    ) implements ProtocolDetail {
        public TcpPacket {
            if (flags != null) {
                try {
                    // decode() handles "0x" prefix that tshark includes
                    int bits = Integer.decode(flags);
                    StringBuilder label = new StringBuilder();

                    if ((bits & 0x001) != 0) append(label, "FIN");
                    if ((bits & 0x002) != 0) append(label, "SYN");
                    if ((bits & 0x004) != 0) append(label, "RST");
                    if ((bits & 0x008) != 0) append(label, "PSH");
                    if ((bits & 0x010) != 0) append(label, "ACK");
                    if ((bits & 0x020) != 0) append(label, "URG");
                    if ((bits & 0x040) != 0) append(label, "ECE");
                    if ((bits & 0x080) != 0) append(label, "CWR");

                    flags = label.isEmpty() ? "NONE" : label.toString();
                } catch (NumberFormatException e) {
                    flags = "UNKNOWN (" + flags + ")";
                }
            }
        }
        private static void append(StringBuilder sb, String flag) {
            if (!sb.isEmpty()) sb.append("+");
            sb.append(flag);
        }
    }

    public record UdpPacket(
            String srcPort,
            String dstPort,
            String length
    ) implements ProtocolDetail {}

    public record DnsPacket(
            String queryName,
            String queryType,
            String resolvedIp,
            String rcode
    ) implements ProtocolDetail {
        public DnsPacket {
            if (queryType != null) {
                queryType = switch (queryType) {
                    case "1" -> "A";
                    case "2" -> "NS";
                    case "5" -> "CNAME";
                    case "6" -> "SOA";
                    case "12" -> "PTR";
                    case "15" -> "MX";
                    case "16" -> "TXT";
                    case "28" -> "AAAA";
                    case "33" -> "SRV";
                    case "255" -> "ANY";
                    default -> "UNKNOWN (" + queryType + ")";
                };
            }

            if (rcode != null) {
                rcode = switch (rcode) {
                    case "0" -> "NOERROR";
                    case "1" -> "FORMERR";// malformed query
                    case "2" -> "SERVFAIL";// server failed to complete
                    case "3" -> "NXDOMAIN";// domain does not exist
                    case "4" -> "NOTIMP";// query type not supported
                    case "5" -> "REFUSED";// server refused the query
                    default -> "UNKNOWN (" + rcode + ")";
                };
            }
        }
    }

    public record TlsPacket(
            String sni,
            String recordVersion,
            String cipherSuite,
            String handshakeType
    ) implements ProtocolDetail {
        public TlsPacket {
            if (recordVersion != null) recordVersion = toVersion(recordVersion);
            if (cipherSuite   != null) cipherSuite   = toCipher(cipherSuite);
            if (handshakeType != null) handshakeType = toHandshake(handshakeType);
        }
        private static String toVersion(String v) {
            return switch (v) {
                case "0x0301" -> "TLS 1.0";
                case "0x0302" -> "TLS 1.1";
                case "0x0303" -> "TLS 1.2/1.3";
                case "0x0304" -> "TLS 1.3";
                default       -> "UNKNOWN (" + v + ")";
            };
        }
        private static String toCipher(String c) {
            return switch (c.toLowerCase()) {
                // TLS 1.3 suites
                case "0x1301" -> "TLS_AES_128_GCM_SHA256";
                case "0x1302" -> "TLS_AES_256_GCM_SHA384";
                case "0x1303" -> "TLS_CHACHA20_POLY1305_SHA256";
                // ECDHE-ECDSA
                case "0xc02b" -> "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256";
                case "0xc02c" -> "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384";
                case "0xc023" -> "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256";
                case "0xc024" -> "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384";
                case "0xc009" -> "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA";
                case "0xc00a" -> "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA";
                // ECDHE-RSA
                case "0xc02f" -> "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
                case "0xc030" -> "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";
                case "0xc027" -> "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256";
                case "0xc028" -> "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384";
                case "0xc013" -> "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA";
                case "0xc014" -> "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";
                case "0xcca8" -> "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256";
                case "0xcca9" -> "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256";
                // RSA
                case "0x002f" -> "TLS_RSA_WITH_AES_128_CBC_SHA";
                case "0x0035" -> "TLS_RSA_WITH_AES_256_CBC_SHA";
                case "0x003c" -> "TLS_RSA_WITH_AES_128_CBC_SHA256";
                case "0x003d" -> "TLS_RSA_WITH_AES_256_CBC_SHA256";
                case "0x009c" -> "TLS_RSA_WITH_AES_128_GCM_SHA256";
                case "0x009d" -> "TLS_RSA_WITH_AES_256_GCM_SHA384";
                default -> "UNKNOWN (" + c + ")";
                };
            }
        }
        private static String toHandshake(String h) {
            return switch (h) {
                case "1" -> "CLIENT HELLO";
                case "2" -> "SERVER HELLO";
                case "4" -> "NEW SESSION TICKET";
                case "8" -> "ENCRYPTED EXTENSIONS";
                case "11" -> "CERT";
                case "12" -> "SERVER KEY EXCHANGE";
                case "13" -> "CERT REQUEST";
                case "14" -> "SERVER HELLO DONE";
                case "15" -> "CERT VERIFY";
                case "16" -> "CLIENT KEY EXCHANGE";
                case "20" -> "FINISHED";
                default -> "UNKNOWN (" + h + ")";
            };
    }
}