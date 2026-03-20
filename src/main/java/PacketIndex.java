import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PacketIndex {
    private final RestClient client;
    private final PacketRepo repo;
    private final Connection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String INDEX = "structured";

    public PacketIndex(PacketRepo repo) {
        this.repo = repo;
        this.connection = repo.getConnection();
        this.client = RestClient.builder(
                new HttpHost("localhost", 9200, "http")
        ).build();
        System.err.println("[J DEBUG] Elasticsearch client connected");
    }
    private String convertToISO8601(String rawTimestamp) {
        try {
            java.time.format.DateTimeFormatter inputFmt = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss");
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(rawTimestamp, inputFmt);
            return ldt.atOffset(java.time.ZoneOffset.UTC).toString();
        } catch (Exception e) {
            System.err.println("[J WARN] Could not parse timestamp: " + rawTimestamp);
            return rawTimestamp;
        }
    }
    public void retryUnindexed() {
        String sql = "SELECT id, packet FROM structured WHERE es_indexed = false";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            ArrayNode missed = objectMapper.createArrayNode();
            java.util.List<Integer> ids = new java.util.ArrayList<>();

            while (rs.next()) {
                ids.add(rs.getInt("id"));
                missed.add(objectMapper.readTree(rs.getString("packet")));
            }

            if (missed.isEmpty()) return;

            System.err.println("[J DEBUG] Retrying " + missed.size() + " un-indexed packets");

            boolean esSuccess = index_batch(missed);
            if (esSuccess) {
                repo.mark_es_indexed(ids);
            }

        } catch (Exception e) {
            System.err.println("[J ERROR] retryUnindexed failed: " + e.getMessage());
        }
    }
    public boolean index_batch(JsonNode parsedBatch) {
        try {
            StringBuilder bulkBody = new StringBuilder();

            for (JsonNode packet : parsedBatch) {
                ObjectNode doc = (ObjectNode) packet.deepCopy();
                String rawTs = packet.path("base").path("timestamp").asText(null);
                if (rawTs != null && !rawTs.isBlank()) {
                    doc.put("@timestamp", convertToISO8601(rawTs));
                } else {
                    doc.put("@timestamp", java.time.Instant.now().toString());
                }
                // bulk action line
                bulkBody.append("{\"index\":{\"_index\":\"").append(INDEX).append("\"}}\n");
                // document line
                bulkBody.append(objectMapper.writeValueAsString(doc)).append("\n");
            }

            Request request     = new Request("POST", "/_bulk");
            request.setEntity(new StringEntity(bulkBody.toString(), ContentType.APPLICATION_JSON));
            Response response   = client.performRequest(request);

            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode responseJson = objectMapper.readTree(responseBody);

            if (responseJson.path("errors").asBoolean(false)) {
                System.err.println("[J ERROR] Bulk index had failures");
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