import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.util.Iterator;
//import java.util.Map;

public class Main {

    // JSON Parser
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.err.println("[JAVA] Ready");

        String line;

        while((line = stdin.readLine()) != null) {

            JsonNode node = mapper.readTree(line);
            JsonNode layers = node.path("layers");

            if (layers.isMissingNode()) {
                System.err.println("[JAVA] no layers, skipping");
                continue;
            }

            Iterator<String> fieldNames = layers.fieldNames();
            for (JsonNode item : layers) {
                String key = fieldNames.next();
                System.err.println("[J DEBUG] KEY: " + key);
                System.err.println("[J DEBUG] ITEM: " + item);
            }

        }
        System.err.println("[JAVA] shutting down");
    }
}