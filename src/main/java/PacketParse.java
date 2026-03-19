import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PacketParse {
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final PacketClassify classifier = new PacketClassify();

    public PacketParse() {}

    public JsonNode create_parsed_batch(JsonNode batch) {
        ArrayNode parsedBatch = mapper.createArrayNode();

        for (JsonNode packet : batch) {
            PacketRecords.Protocol protocol = classifier.classify(packet);
            PacketRecords.BasePacket base = parse_base(packet);
            Object valuePacket = parse_value(packet, protocol);

            ObjectNode parsedPacket = mapper.createObjectNode();
            parsedPacket.put("protocol", protocol.name());
            parsedPacket.set("base", mapper.valueToTree(base));
            parsedPacket.set("layers", mapper.valueToTree(valuePacket));

            parsedBatch.add(parsedPacket);
        }

        return parsedBatch;
    }

    public PacketRecords.BasePacket parse_base(JsonNode packet) {
        return mapper.convertValue(packet, PacketRecords.BasePacket.class);
    }

    public Object parse_value(JsonNode packet, PacketRecords.Protocol protocol) {
        PacketRecords.Layers layers = mapper.convertValue(
                packet.path("layers"), PacketRecords.Layers.class
        );

        return switch (protocol) {
            case TCP -> new PacketRecords.TcpPacket(
                    get(layers.tcpSrcPort()),
                    get(layers.tcpDstPort()),
                    get(layers.tcpFlags()),
                    get(layers.tcpSeq()),
                    get(layers.tcpAck()),
                    get(layers.tcpWindowSize())
            );
            case UDP -> new PacketRecords.UdpPacket(
                    get(layers.udpSrcPort()),
                    get(layers.udpDstPort()),
                    get(layers.udpLength())
            );
            case DNS -> new PacketRecords.DnsPacket(
                    get(layers.dnsQryName()),
                    get(layers.dnsQryType()),
                    get(layers.dnsA()),
                    get(layers.dnsFlagsRcode())
            );
            case TLS -> new PacketRecords.TlsPacket(
                    get(layers.tlsSni()),
                    get(layers.tlsRecordVersion()),
                    get(layers.tlsCipherSuite()),
                    get(layers.tlsHandshakeType())
            );
            case UNKNOWN -> null;
        };
    }

    private String get(java.util.List<String> list) {
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }
}