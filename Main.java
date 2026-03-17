import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.util.Map;

public class Main {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        // Wrap stdin/stdout — this is the idle-wait mechanism
        // BufferedReader.readLine() blocks until Python writes a line, then returns it
        BufferedReader stdin  = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter    stdout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));

        // Log to stderr so it doesn't pollute the stdout wire protocol
        System.err.println("[Java] Ready");

        String line;
        while ((line = stdin.readLine()) != null) {  // blocks here between batches
            try {
                JsonNode envelope = mapper.readTree(line);

                int seq   = envelope.get("seq").asInt();
                int count = envelope.get("batch").size();  // just count for now

                // Ack back to Python
                String response = mapper.writeValueAsString(Map.of(
                        "seq",    seq,
                        "status", "ok",
                        "count",  count
                ));

                stdout.println(response);
                stdout.flush();  // Without this, Python's readline() blocks forever

                System.err.printf("[Java] seq=%d  packets=%d%n", seq, count);

            } catch (Exception e) {
                // Always respond even on error — otherwise Python hangs waiting
                String err = mapper.writeValueAsString(Map.of(
                        "status",  "error",
                        "message", e.getMessage()
                ));
                stdout.println(err);
                stdout.flush();
                System.err.println("[Java] Error: " + e.getMessage());
            }
        }

        System.err.println("[Java] stdin closed, shutting down");
    }
}
