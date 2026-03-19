import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;

public class Main {

    // JSON Parser
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        PacketProcessor pp = new PacketProcessor();

        System.err.println("[JAVA] Ready");

        String line;

        while((line = stdin.readLine()) != null) {

            line = line.trim();
            if(line.isEmpty()) continue;

            JsonNode batch = mapper.readTree(line);
            JsonNode packets = batch.get("packets");

            if (packets.isMissingNode()) {
                System.err.println("[JAVA] no layers, skipping");
                continue;
            }
            try {
                pp.process_batch(packets);
            } catch (Exception e){
                System.err.println("[ERROR] PROCESSING BATCH FAILURE: " + e.getMessage());
            }

        }
        System.err.println("[JAVA] shutting down");
    }
}