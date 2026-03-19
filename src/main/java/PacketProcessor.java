import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PacketProcessor {
    private final PacketParse parser;
    private final PacketRepo repo;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PacketProcessor() {
        this.parser = new PacketParse();
        this.repo = new PacketRepo();
    }

    public void process_batch(JsonNode batch) {
        JsonNode parsedBatch = parser.create_parsed_batch(batch);
        if (parsedBatch == null || parsedBatch.isEmpty()) {
            return;
        }
        repo.create_insert_job(parsedBatch);

        System.err.println("[J DEBUG] PARSED PACKET: " + parsedBatch.toPrettyString());
    }
}