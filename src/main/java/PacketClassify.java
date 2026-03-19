import com.fasterxml.jackson.databind.JsonNode;

public class PacketClassify {

    public PacketRecords.Protocol classify(JsonNode packet) {
        JsonNode layers = packet.path("layers");

        if (!layers.path("tls_record_version").isMissingNode()) return PacketRecords.Protocol.TLS;
        if (!layers.path("dns_qry_name").isMissingNode()) return PacketRecords.Protocol.DNS;
        if (!layers.path("udp_srcport").isMissingNode()) return PacketRecords.Protocol.UDP;
        if (!layers.path("tcp_srcport").isMissingNode()) return PacketRecords.Protocol.TCP;

        return PacketRecords.Protocol.UNKNOWN;
    }
}