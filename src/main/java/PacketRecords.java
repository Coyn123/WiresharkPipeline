import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class PacketRecords {

    // Shared Layers
    public record Layers(
            // Base
            @JsonProperty("frame_time") List<String> frameTime,
            @JsonProperty("ip_src") List<String> ipSrc,
            @JsonProperty("ip_dst") List<String> ipDst,
            @JsonProperty("ip_version") List<String> ipVersion,
            @JsonProperty("ip_proto") List<String> ipProto,
            @JsonProperty("frame_len") List<String> frameLen,
            @JsonProperty("ip_ttl") List<String> ipTtl,
            // TCP
            @JsonProperty("tcp_srcport") List<String> tcpSrcPort,
            @JsonProperty("tcp_dstport") List<String> tcpDstPort,
            @JsonProperty("tcp_flags") List<String> tcpFlags,
            @JsonProperty("tcp_seq") List<String> tcpSeq,
            @JsonProperty("tcp_ack") List<String> tcpAck,
            @JsonProperty("tcp_window_size") List<String> tcpWindowSize,
            // UDP
            @JsonProperty("udp_srcport") List<String> udpSrcPort,
            @JsonProperty("udp_dstport") List<String> udpDstPort,
            @JsonProperty("udp_length")  List<String> udpLength,
            // DNS
            @JsonProperty("dns_qry_name") List<String> dnsQryName,
            @JsonProperty("dns_qry_type") List<String> dnsQryType,
            @JsonProperty("dns_a") List<String> dnsA,
            @JsonProperty("dns_flags_rcode") List<String> dnsFlagsRcode,
            // TLS
            @JsonProperty("tls_handshake_extensions_server_name") List<String> tlsSni,
            @JsonProperty("tls_record_version") List<String> tlsRecordVersion,
            @JsonProperty("tls_handshake_ciphersuite") List<String> tlsCipherSuite,
            @JsonProperty("tls_handshake_type") List<String> tlsHandshakeType
    ) {}

    // Base
    public record BasePacket(
            String timestamp,
            String ipSrc,
            String ipDst,
            String ipVersion,
            String ipProto,
            String frameLen,
            String ipTtl
    ) {
        @JsonCreator
        public BasePacket(
                @JsonProperty("timestamp") String timestamp,
                @JsonProperty("layers")    Layers layers
        ) {
            this(
                    formatTimestamp(timestamp),
                    get(layers.ipSrc()),
                    get(layers.ipDst()),
                    get(layers.ipVersion()),
                    get(layers.ipProto()),
                    get(layers.frameLen()),
                    get(layers.ipTtl())
            );
        }

        //Format for DATETIME sql Type
        private static String formatTimestamp(String raw) {
            if (raw == null) return null;
            long millis = Long.parseLong(raw);
            return new java.sql.Timestamp(millis)
                    .toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        // null-safe helper — returns null instead of throwing if field is missing
        private static String get(java.util.List<String> list) {
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
        }
    }

    // Protocol value packets
    public record TcpPacket(
            String srcPort,
            String dstPort,
            String flags,
            String seq,
            String ack,
            String windowSize
    ) {}

    public record UdpPacket(
            String srcPort,
            String dstPort,
            String length
    ) {}

    public record DnsPacket(
            String queryName,
            String queryType,
            String resolvedIp,
            String rcode
    ) {}

    public record TlsPacket(
            String sni,
            String recordVersion,
            String cipherSuite,
            String handshakeType
    ) {
        public TlsPacket {
            recordVersion = switch (recordVersion) {
                case "0x0301" -> "TLS 1.0";
                case "0x0302" -> "TLS 1.1";
                case "0x0303" -> "TLS 1.2/1.3";
                case "0x0304" -> "TLS 1.3";
                default -> recordVersion != null ? "UNKNOWN (" + recordVersion + ")" : null;
            };
        }
    }

    // Protocol enum for classification
    public enum Protocol {
        TCP,
        UDP,
        DNS,
        TLS,
        UNKNOWN
    }
}