import com.fasterxml.jackson.databind.JsonNode;

public class PacketProcessor {
    private final PacketParse parser;
    private final PacketClassify classifier;
    private final PacketRepo repo;

    public PacketProcessor() {
        this.parser = new PacketParse();
        this.classifier = new PacketClassify();
        this.repo = new PacketRepo();
    }

    public void processLine(JsonNode batch) {
        System.err.println("[J DEBUG] Full payload: " + batch.toPrettyString());
    }
}