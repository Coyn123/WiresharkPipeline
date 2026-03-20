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
            System.err.println("[J DEBUG] MySQL connected successfully");
        } catch (SQLException e) {
            System.err.println("[J ERROR] MySQL connection failed: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }


    // ── Public methods ───────────────────────────────────────────────────────
    public Boolean create_insert_job(JsonNode parsedBatch) {
        try {
            String structuredSql = "INSERT INTO structured (packet, es_indexed) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(structuredSql)) {
                for (JsonNode packet : parsedBatch) {
                    stmt.setString(1, packet.toString());
                    stmt.setBoolean(2, false);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            for (JsonNode packet : parsedBatch) {
                long baseId = insertBase(packet);
                insertLayer(packet, baseId);
            }

            connection.commit();
            connection.setAutoCommit(true);
            connection.setAutoCommit(false);
            System.err.println("[J DEBUG] Inserted " + parsedBatch.size() + " packets into MySQL");
            return Boolean.TRUE;

        } catch (SQLException e) {
            System.err.println("[J ERROR] Insert failed, rolling back: " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ex) {
                System.err.println("[J ERROR] Rollback failed: " + ex.getMessage());
            }
            return Boolean.FALSE;
        }
    }

    // Generic insert function
    private void insertRow(String table, long baseId, String[] columns, String[] values) throws SQLException {
        String placeholders = "?, " + "?, ".repeat(columns.length).replaceAll(", $", "");
        String sql = "INSERT INTO " + table + " (base_packet_id, " + String.join(", ", columns) + ") VALUES (" + placeholders + ")";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, baseId);
            for (int i = 0; i < values.length; i++) {
                stmt.setString(i + 2, values[i]);
            }
            stmt.executeUpdate();
        }
    }

    public Boolean mark_es_indexed(java.util.List<Integer> ids) {
        String sql = "UPDATE structured SET es_indexed = true WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int id : ids) {
                stmt.setInt(1, id);
                stmt.addBatch();
            }
            stmt.executeBatch();
            connection.commit();
            System.err.println("[J DEBUG] MySQL flagged " + ids.size() + " packets as es_indexed");
            return Boolean.TRUE;
        } catch (SQLException e) {
            System.err.println("[J ERROR] Failed to mark es_indexed: " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ex) {
                System.err.println("[J ERROR] Rollback failed: " + ex.getMessage());
            }
            return Boolean.FALSE;
        }
    }

    // Base insert
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

    // ── Layer routing ────────────────────────────────────────────────────────
    private void insertLayer(JsonNode packet, long baseId) throws SQLException {
        String protocol = text(packet, "protocol") != null ? text(packet, "protocol") : "UNKNOWN";
        JsonNode l = packet.path("layers");

        switch (protocol) {
            case "TCP" -> insertRow("tcp", baseId,
                    new String[]{"src_port", "dst_port", "flags", "seq", "ack", "window_size"},
                    new String[]{text(l,"srcPort"), text(l,"dstPort"), text(l,"flags"), text(l,"seq"), text(l,"ack"), text(l,"windowSize")}
            );
            case "UDP" -> insertRow("udp", baseId,
                    new String[]{"src_port", "dst_port", "length"},
                    new String[]{text(l,"srcPort"), text(l,"dstPort"), text(l,"length")}
            );
            case "DNS" -> insertRow("dns", baseId,
                    new String[]{"query_name", "query_type", "resolved_ip", "rcode"},
                    new String[]{text(l,"queryName"), text(l,"queryType"), text(l,"resolvedIp"), text(l,"rcode")}
            );
            case "TLS" -> insertRow("tls", baseId,
                    new String[]{"sni", "record_version", "cipher_suite", "handshake_type"},
                    new String[]{text(l,"sni"), text(l,"recordVersion"), text(l,"cipherSuite"), text(l,"handshakeType")}
            );
        }
    }

    private long getGeneratedKey(PreparedStatement stmt) throws SQLException {
        try (ResultSet keys = stmt.getGeneratedKeys()) {
            if (keys.next()) return keys.getLong(1);
            throw new SQLException("No generated key returned");
        }
    }
}