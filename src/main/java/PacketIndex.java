import com.fasterxml.jackson.databind.JsonNode;
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

public class PacketIndex {
    private final RestHighLevelClient client;
    private static final String INDEX = "structured";

    public PacketIndex() {
        this.client = new RestHighLevelClient(
                RestClient.builder(new HttpHost("localhost", 9200, "http"))
        );
        System.err.println("[J DEBUG] Elasticsearch client connected");
    }

    public boolean index_batch(JsonNode parsedBatch) {
        try {
            BulkRequest bulkRequest = new BulkRequest();

            for (JsonNode packet : parsedBatch) {
                ObjectNode doc = (ObjectNode) packet.deepCopy();
                doc.put("@timestamp", packet.path("base").path("timestamp").asText());

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