package cc.claw.memory;

import cc.claw.ClawConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Component
public class MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryStore.class);

    private final ClawConfig config;
    private final Path dbPath;

    public MemoryStore(ClawConfig config) {
        this.config = config;
        this.dbPath = config.homePath().resolve("claw.db");
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(dbPath.getParent());
            log.info("Initializing SQLite database at {}", dbPath);
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT PRIMARY KEY,
                        title TEXT,
                        created_at INTEGER NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1,
                        message_count INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        tool_calls TEXT,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (session_id) REFERENCES sessions(id)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id TEXT NOT NULL,
                        type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        keywords TEXT,
                        importance INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (session_id) REFERENCES sessions(id)
                    )
                    """);
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create database directory: " + dbPath.getParent(), e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database: " + dbPath, e);
        }
    }

    public void createSession(String sessionId, String title) {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO sessions (id, title, created_at) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, title);
            ps.setLong(3, now);
            ps.executeUpdate();
            log.debug("Created session: {} ({})", sessionId, title);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create session: " + sessionId, e);
        }
    }

    public SessionInfo getSession(String sessionId) {
        String sql = "SELECT id, title, created_at, active, message_count FROM sessions WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SessionInfo(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getLong("created_at"),
                        rs.getInt("active") == 1,
                        rs.getInt("message_count")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get session: " + sessionId, e);
        }
        return null;
    }

    public void addMessage(String sessionId, String role, String content, String toolCalls) {
        long now = System.currentTimeMillis();
        String insertSql = "INSERT INTO messages (session_id, role, content, tool_calls, created_at) VALUES (?, ?, ?, ?, ?)";
        String updateSql = "UPDATE sessions SET message_count = message_count + 1 WHERE id = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (
                PreparedStatement insertPs = conn.prepareStatement(insertSql);
                PreparedStatement updatePs = conn.prepareStatement(updateSql)
            ) {
                insertPs.setString(1, sessionId);
                insertPs.setString(2, role);
                insertPs.setString(3, content);
                insertPs.setString(4, toolCalls);
                insertPs.setLong(5, now);
                insertPs.executeUpdate();

                updatePs.setString(1, sessionId);
                updatePs.executeUpdate();

                conn.commit();
                log.debug("Added message to session {} role={}", sessionId, role);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add message to session: " + sessionId, e);
        }
    }

    public List<MessageRecord> getMessages(String sessionId, int limit) {
        String sql = "SELECT id, session_id, role, content, tool_calls, created_at FROM messages WHERE session_id = ? ORDER BY id DESC LIMIT ?";
        List<MessageRecord> messages = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(new MessageRecord(
                        rs.getLong("id"),
                        rs.getString("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("tool_calls"),
                        rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get messages for session: " + sessionId, e);
        }
        return messages;
    }

    public void addMemory(String sessionId, String type, String content, String keywords, int importance) {
        long now = System.currentTimeMillis();
        String sql = "INSERT INTO memories (session_id, type, content, keywords, importance, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, type);
            ps.setString(3, content);
            ps.setString(4, keywords);
            ps.setInt(5, importance);
            ps.setLong(6, now);
            ps.executeUpdate();
            log.debug("Added memory type={} to session {}", type, sessionId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add memory to session: " + sessionId, e);
        }
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public List<MemoryEntry> searchMemories(String query, int limit) {
        String escapedQuery = escapeLike(query);
        String likeQuery = "%" + escapedQuery + "%";
        String sql = "SELECT type, content, keywords, importance FROM memories WHERE keywords LIKE ? OR content LIKE ? ORDER BY importance DESC LIMIT ?";
        List<MemoryEntry> results = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, likeQuery);
            ps.setString(2, likeQuery);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new MemoryEntry(
                        rs.getString("type"),
                        rs.getString("content"),
                        rs.getString("keywords"),
                        rs.getInt("importance")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search memories: " + query, e);
        }
        return results;
    }

    public void closeSession(String sessionId) {
        String sql = "UPDATE sessions SET active = 0 WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.debug("Closed session: {}", sessionId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close session: " + sessionId, e);
        }
    }

    public int cleanupOldMessages(int days) {
        long cutoff = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        String sql = "DELETE FROM messages WHERE created_at < ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                log.info("Cleaned up {} messages older than {} days", deleted, days);
            }
            return deleted;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to cleanup old messages", e);
        }
    }

    @PreDestroy
    void shutdown() {
        log.info("MemoryStore shutdown");
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
    }

    public record SessionInfo(
        String id,
        String title,
        long createdAt,
        boolean active,
        int messageCount
    ) {}

    public record MessageRecord(
        long id,
        String sessionId,
        String role,
        String content,
        String toolCalls,
        long createdAt
    ) {}
}