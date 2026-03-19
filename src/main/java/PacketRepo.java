import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;

public class PacketRepo {
    private static final String URL  = "jdbc:mysql://localhost:3306/nsm";
    private static final String USER = "root";
    private static final String PASS = "";

    private Connection connection;

    public PacketRepo() {
        try {
            this.connection = DriverManager.getConnection(URL, USER, PASS);
            this.connection.setAutoCommit(false);
            System.err.println("[J DEBUG] MariaDB connected successfully");
        } catch (SQLException e) {
            System.err.println("[J ERROR] MariaDB connection failed: " + e.getMessage());
        }
    }

    //Another Null helper.... Java....
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    public void create_insert_job(JsonNode parsedBatch) {
        try {
            String structuredSql = "INSERT INTO structured (packet) VALUES (?)";
            try (PreparedStatement stmt = connection.prepareStatement(structuredSql)) {
                for (JsonNode packet : parsedBatch) {
                    stmt.setString(1, packet.toString());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            for (JsonNode packet : parsedBatch) {
                long baseId = insertBase(packet);
                insertLayer(packet, baseId);
            }

            connection.commit();
            System.err.println("[J DEBUG] Inserted " + parsedBatch.size() + " packets");

        } catch (SQLException e) {
            System.err.println("[J ERROR] Insert failed, rolling back: " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ex) {
                System.err.println("[J ERROR] Rollback failed: " + ex.getMessage());
            }
        }
    }

    private long insertBase(JsonNode packet) throws SQLException {
        String sql = """
            INSERT INTO base
                (timestamp, ip_src, ip_dst, ip_version, ip_proto, frame_len, ip_ttl, protocol)
            VALUES (STR_TO_DATE(?, '%Y-%m-%d %H:%i:%s'), ?, ?, ?, ?, ?, ?, ?)
        """;
        JsonNode base = packet.path("base");
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, text(base, "timestamp"));
            stmt.setString(2, text(base, "ipSrc"));
            stmt.setString(3, text(base, "ipDst"));
            stmt.setString(4, text(base, "ipVersion"));
            stmt.setString(5, text(base, "ipProto"));
            stmt.setString(6, text(base, "frameLen"));
            stmt.setString(7, text(base, "ipTtl"));
            stmt.setString(8, text(packet, "protocol"));
            stmt.executeUpdate();
            return getGeneratedKey(stmt);
        }
    }

    private void insertLayer(JsonNode packet, long baseId) throws SQLException {
        String protocol = packet.path("protocol").asText("UNKNOWN");
        JsonNode layers = packet.path("layers");

        switch (protocol) {
            case "TCP" -> insertTcp(layers, baseId);
            case "UDP" -> insertUdp(layers, baseId);
            case "DNS" -> insertDns(layers, baseId);
            case "TLS" -> insertTls(layers, baseId);
        }
    }

    private void insertTcp(JsonNode l, long baseId) throws SQLException {
        String sql = "INSERT INTO tcp (base_packet_id, src_port, dst_port, flags, seq, ack, window_size) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1,   baseId);
            stmt.setString(2, text(l, "srcPort"));
            stmt.setString(3, text(l, "dstPort"));
            stmt.setString(4, text(l, "flags"));
            stmt.setString(5, text(l, "seq"));
            stmt.setString(6, text(l, "ack"));
            stmt.setString(7, text(l, "windowSize"));
            stmt.executeUpdate();
        }
    }

    private void insertUdp(JsonNode l, long baseId) throws SQLException {
        String sql = "INSERT INTO udp (base_packet_id, src_port, dst_port, length) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1,   baseId);
            stmt.setString(2, text(l, "srcPort"));
            stmt.setString(3, text(l, "dstPort"));
            stmt.setString(4, text(l, "length"));
            stmt.executeUpdate();
        }
    }

    private void insertDns(JsonNode l, long baseId) throws SQLException {
        String sql = "INSERT INTO dns (base_packet_id, query_name, query_type, resolved_a, rcode) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1,   baseId);
            stmt.setString(2, text(l, "queryName"));
            stmt.setString(3, text(l, "queryType"));
            stmt.setString(4, text(l, "resolvedIp"));
            stmt.setString(5, text(l, "rcode"));
            stmt.executeUpdate();
        }
    }

    private void insertTls(JsonNode l, long baseId) throws SQLException {
        String sql = "INSERT INTO tls (base_packet_id, sni, record_version, cipher_suite, handshake_type) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1,   baseId);
            stmt.setString(2, text(l, "sni"));
            stmt.setString(3, text(l, "recordVersion"));
            stmt.setString(4, text(l, "cipherSuite"));
            stmt.setString(5, text(l, "handshakeType"));
            stmt.executeUpdate();
        }
    }

    private long getGeneratedKey(PreparedStatement stmt) throws SQLException {
        try (ResultSet keys = stmt.getGeneratedKeys()) {
            if (keys.next()) return keys.getLong(1);
            throw new SQLException("No generated key returned");
        }
    }
}