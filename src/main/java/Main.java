import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;

public class Main {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter stdout = new PrintWriter(new OutputStreamWriter(System.out), true);
        PacketProcessor pp = new PacketProcessor();

        System.err.println("[JAVA] Ready");

        String line;

        while ((line = stdin.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            try {
                JsonNode batch   = mapper.readTree(line);
                JsonNode packets = batch.path("packets");

                if (packets.isMissingNode() || packets.isEmpty()) {
                    System.err.println("[JAVA] no packets, skipping");
                    stdout.println("{\"status\": \"skipped\", \"count\": 0}");
                    continue;
                }

                pp.process_batch(packets);

                stdout.println("{\"status\": \"ok\", \"count\": " + packets.size() + "}");

            } catch (Exception e) {
                System.err.println("[ERROR] PROCESSING BATCH FAILURE: " + e.getMessage());
                stdout.println("{\"status\": \"error\", \"count\": 0}");
            }
        }
        pp.close();
        System.err.println("[JAVA] shutting down");
    }
}