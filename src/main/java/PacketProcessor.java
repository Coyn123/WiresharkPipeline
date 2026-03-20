import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class PacketProcessor {
    private final PacketParse parser;
    private final PacketRepo repo;
    private final PacketIndex index;

    public PacketProcessor() {
        this.parser = new PacketParse();
        this.repo = new PacketRepo();
        this.index = new PacketIndex(repo);
        this.index.retryUnindexed();
    }

    public void process_batch(JsonNode batch) {
        try {
            JsonNode parsedBatch = parser.create_parsed_batch(batch);

            if (parsedBatch == null || parsedBatch.isEmpty()) {
                System.err.println("[J WARN] parsedBatch is empty, skipping");
                return;
            }

            List<Integer> insertedIds = repo.create_insert_job(parsedBatch);
            if (insertedIds == null) {
                System.err.println("[J ERROR] MariaDB insert failed, skipping ES");
                return;
            }

            boolean esSuccess = index.index_batch(parsedBatch);
            if (!esSuccess) {
                System.err.println("[J ERROR] ES index failed — es_indexed stays false");
                return;
            }

            repo.mark_es_indexed(insertedIds);

        } catch (Exception e) {
            System.err.println("[J ERROR] process_batch failed: " + e.getMessage());
        }
    }
}