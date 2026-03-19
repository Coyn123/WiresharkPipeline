import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PacketProcessor {
    private final PacketParse parser;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PacketProcessor() {
        this.parser = new PacketParse();
    }

    public void process_batch(JsonNode batch) {
        JsonNode parsedPacket = parser.create_parsed_batch(batch);
        if (parsedPacket == null || parsedPacket.isEmpty()) {
            return;
        }

        System.err.println("[J DEBUG] PARSED PACKET: " + parsedPacket.toPrettyString());
    }
}