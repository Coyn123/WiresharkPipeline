import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PacketIndex {
    private final RestHighLevelClient client;
    private final PacketRepo repo;          // fix 1: fields declared, assigned in constructor
    private final Connection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String INDEX = "structured";

    public PacketIndex(PacketRepo repo) {   // fix 2: repo passed as parameter
        this.repo = repo;                   // fix 3: assigned before use
        this.connection = repo.getConnection();
        this.client = new RestHighLevelClient(
                RestClient.builder(new HttpHost("localhost", 9200, "http"))
        );
        System.err.println("[J DEBUG] Elasticsearch client connected");
    }
    private String convertToISO8601(String rawTimestamp) {
        try {
            java.time.format.DateTimeFormatter inputFmt = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss");
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(rawTimestamp, inputFmt);
            return ldt.atOffset(java.time.ZoneOffset.UTC).toString(); // "2026-03-20T13:00:00Z"
        } catch (Exception e) {
            System.err.println("[J WARN] Could not parse timestamp: " + rawTimestamp);
            return rawTimestamp;
        }
    }

    public void retryUnindexed() {
        String sql = "SELECT packet FROM structured WHERE es_indexed = false";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            ArrayNode missed = objectMapper.createArrayNode();
            while (rs.next()) {
                JsonNode packet = objectMapper.readTree(rs.getString("packet"));
                missed.add(packet);
            }

            if (missed.isEmpty()) return;

            System.err.println("[J DEBUG] Retrying " + missed.size() + " un-indexed packets");

            boolean esSuccess = index_batch(missed);  // fix 4: call self, not index.index_batch
            if (esSuccess) {
                repo.mark_es_indexed(missed);
            }

        } catch (Exception e) {
            System.err.println("[J ERROR] retryUnindexed failed: " + e.getMessage());
        }
    }

    public boolean index_batch(JsonNode parsedBatch) {
        try {
            BulkRequest bulkRequest = new BulkRequest();

            for (JsonNode packet : parsedBatch) {
                ObjectNode doc = (ObjectNode) packet.deepCopy();
                String rawTs = packet.path("base").path("timestamp").asText(null);
                if (rawTs != null && !rawTs.isBlank()) {
                    doc.put("@timestamp", convertToISO8601(rawTs));
                } else {
                    doc.put("@timestamp", java.time.Instant.now().toString());
                }

                bulkRequest.add(new IndexRequest(INDEX)
                        .source(doc.toString(), XContentType.JSON)
                );
            }

            BulkResponse response = client.bulk(bulkRequest, RequestOptions.DEFAULT);

            if (response.hasFailures()) {
                System.err.println("[J ERROR] Bulk index had failures: " + response.buildFailureMessage());
                return false;
            }

            System.err.println("[J DEBUG] Inserted " + parsedBatch.size() + " packets into ES");
            return true;

        } catch (IOException e) {
            System.err.println("[J ERROR] Elasticsearch index failed: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            System.err.println("[J ERROR] Failed to close ES client: " + e.getMessage());
        }
    }
}