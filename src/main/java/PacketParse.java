import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class PacketParse {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PacketClassify classifier = new PacketClassify();

    public PacketParse() {}

    public List<PacketRecords.ParsedPacket> create_parsed_batch(JsonNode batch) {
        List<PacketRecords.ParsedPacket> parsedBatch = new ArrayList<>();

        for (JsonNode packet : batch) {
            PacketRecords.Protocol protocol = classifier.classify(packet);
            PacketRecords.BasePacket base = parse_base(packet);
            PacketRecords.ProtocolDetail detail = parse_detail(packet, protocol);

            ObjectNode json = mapper.createObjectNode();
            json.put("protocol", protocol.name());
            json.set("base", mapper.valueToTree(base));
            json.set("detail", mapper.valueToTree(detail));

            parsedBatch.add(new PacketRecords.ParsedPacket(json, protocol, base, detail));
        }

        return parsedBatch;
    }

    public PacketRecords.BasePacket parse_base(JsonNode packet) {
        JsonNode layers = packet.path("layers");

        return new PacketRecords.BasePacket(
                packet.path(PacketFields.TIMESTAMP).textValue(),
                coalesce(first(layers, PacketFields.IP_SRC), first(layers, PacketFields.IPV6_SRC)),
                coalesce(first(layers, PacketFields.IP_DST), first(layers, PacketFields.IPV6_DST)),
                first(layers, PacketFields.IP_VERSION),
                coalesce(first(layers, PacketFields.IP_PROTO), first(layers, PacketFields.IPV6_NXT)),
                first(layers, PacketFields.FRAME_LEN),
                coalesce(first(layers, PacketFields.IP_TTL), first(layers, PacketFields.IPV6_HLIM))
        );
    }

    // WHY NO default CASE:
    // ProtocolDetail is sealed — the compiler verifies all permitted types are handled.
    public PacketRecords.ProtocolDetail parse_detail(JsonNode packet, PacketRecords.Protocol protocol) {
        JsonNode layers = packet.path("layers");

        return switch (protocol) {
            case TCP -> new PacketRecords.TcpPacket(
                    first(layers, PacketFields.TCP_SRC_PORT),
                    first(layers, PacketFields.TCP_DST_PORT),
                    first(layers, PacketFields.TCP_FLAGS),
                    first(layers, PacketFields.TCP_SEQ),
                    first(layers, PacketFields.TCP_ACK),
                    first(layers, PacketFields.TCP_WINDOW_SIZE)
            );
            case UDP -> new PacketRecords.UdpPacket(
                    first(layers, PacketFields.UDP_SRC_PORT),
                    first(layers, PacketFields.UDP_DST_PORT),
                    first(layers, PacketFields.UDP_LENGTH)
            );
            case DNS -> new PacketRecords.DnsPacket(
                    first(layers, PacketFields.DNS_QRY_NAME),
                    first(layers, PacketFields.DNS_QRY_TYPE),
                    first(layers, PacketFields.DNS_A),
                    first(layers, PacketFields.DNS_FLAGS_RCODE)
            );
            case TLS -> new PacketRecords.TlsPacket(
                    first(layers, PacketFields.TLS_SNI),
                    first(layers, PacketFields.TLS_RECORD_VERSION),
                    first(layers, PacketFields.TLS_CIPHER_SUITE),
                    first(layers, PacketFields.TLS_HANDSHAKE_TYPE)
            );
            case UNKNOWN -> null;
        };
    }

    private String coalesce(String a, String b) {
        return a != null ? a : b;
    }

    private String first(JsonNode layers, String field) {
        JsonNode node = layers.path(field);
        if (node.isMissingNode() || node.isNull()) return null;
        if (node.isArray()) return node.isEmpty() ? null : node.get(0).textValue();
        return node.textValue();
    }
}