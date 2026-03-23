import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.Collections;
import java.util.List;

public class PacketRepo {
    private static final String URL = "jdbc:mysql://localhost:3306/nsm";
    private static final String USER = "root";
    private static final String PASS = "";

    private Connection connection;

    public PacketRepo() {
        try {
            this.connection = DriverManager.getConnection(URL, USER, PASS);
            this.connection.setAutoCommit(false);
            System.err.println("[J DEBUG] MySQL connected successfully");
        } catch (SQLException e) {
            throw new RuntimeException("[J ERROR] MySQL connection failed: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    public List<Integer> create_insert_job(List<PacketRecords.ParsedPacket> batch) {
        List<Integer> insertedIds = new java.util.ArrayList<>();
        try {
            String structuredSql = "INSERT INTO structured (packet, es_indexed) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(
                    structuredSql, Statement.RETURN_GENERATED_KEYS)) {
                for (PacketRecords.ParsedPacket p : batch) {
                    stmt.setString(1, p.json().toString());
                    stmt.setBoolean(2, false);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                try (PreparedStatement idStmt = connection.prepareStatement(
                        "SELECT id FROM structured ORDER BY id DESC LIMIT ?")) {
                    idStmt.setInt(1, batch.size());
                    try (ResultSet rs = idStmt.executeQuery()) {
                        while (rs.next()) insertedIds.add(rs.getInt(1));
                    }
                }
            }
                Collections.reverse(insertedIds);

            for (PacketRecords.ParsedPacket p : batch) {
                long baseId = insertBase(p.base(), p.protocol());
                insertLayer(p.detail(), baseId);
            }

            connection.commit();
            System.err.println("[J DEBUG] Inserted " + batch.size() + " packets into MySQL");
            return insertedIds;

        } catch (SQLException e) {
            System.err.println("[J ERROR] Insert failed, rolling back: " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ex) {
                System.err.println("[J ERROR] Rollback failed: " + ex.getMessage());
            }
            return null;
        }
    }

    // Generic insert
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

    public Boolean mark_es_indexed(List<Integer> ids) {
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

    private long insertBase(PacketRecords.BasePacket base, PacketRecords.Protocol protocol) throws SQLException {
        String sql = """
            INSERT INTO base
                (timestamp, ip_src, ip_dst, ip_version, ip_proto, frame_len, ip_ttl, protocol)
            VALUES (STR_TO_DATE(?, '%Y-%m-%d %H:%i:%s'), ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, base.timestamp());
            stmt.setString(2, base.ipSrc());
            stmt.setString(3, base.ipDst());
            stmt.setString(4, base.ipVersion());
            stmt.setString(5, base.ipProto());
            stmt.setString(6, base.frameLen());
            stmt.setString(7, base.ipTtl());
            stmt.setString(8, protocol.name());
            stmt.executeUpdate();
            return getGeneratedKey(stmt);
        }
    }

    private void insertLayer(PacketRecords.ProtocolDetail detail, long baseId) throws SQLException {
        if (detail == null) return;

        switch (detail) {
            case PacketRecords.TcpPacket t -> insertRow("tcp", baseId,
                    new String[]{"src_port", "dst_port", "flags", "seq", "ack", "window_size"},
                    new String[]{t.srcPort(), t.dstPort(), t.flags(), t.seq(), t.ack(), t.windowSize()}
            );
            case PacketRecords.UdpPacket u -> insertRow("udp", baseId,
                    new String[]{"src_port", "dst_port", "length"},
                    new String[]{u.srcPort(), u.dstPort(), u.length()}
            );
            case PacketRecords.DnsPacket d -> insertRow("dns", baseId,
                    new String[]{"query_name", "query_type", "resolved_ip", "rcode"},
                    new String[]{d.queryName(), d.queryType(), d.resolvedIp(), d.rcode()}
            );
            case PacketRecords.TlsPacket tl -> insertRow("tls", baseId,
                    new String[]{"sni", "record_version", "cipher_suite", "handshake_type"},
                    new String[]{tl.sni(), tl.recordVersion(), tl.cipherSuite(), tl.handshakeType()}
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