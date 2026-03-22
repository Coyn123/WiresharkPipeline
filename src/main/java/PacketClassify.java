import com.fasterxml.jackson.databind.JsonNode;

public class PacketClassify {

    public PacketRecords.Protocol classify(JsonNode packet) {
        JsonNode layers = packet.path("layers");

        if (!layers.path(PacketFields.TLS_RECORD_VERSION).isMissingNode()) return PacketRecords.Protocol.TLS;
        if (!layers.path(PacketFields.DNS_QRY_NAME).isMissingNode()) return PacketRecords.Protocol.DNS;
        if (!layers.path(PacketFields.UDP_SRC_PORT).isMissingNode()) return PacketRecords.Protocol.UDP;
        if (!layers.path(PacketFields.TCP_SRC_PORT).isMissingNode()) return PacketRecords.Protocol.TCP;

        return PacketRecords.Protocol.UNKNOWN;
    }
}