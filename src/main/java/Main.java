import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.util.Map;

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

            JsonNode srcIp = layers.path("ip_src");

            if (!srcIp.isMissingNode()) {
                String ip = srcIp.get(0).asText();
                System.err.println("[JAVA] ip: " + ip);
            } else {
                System.err.println("[JAVA] no ip");
            }
        }
        System.err.println("[JAVA] shutting down");
    }
}