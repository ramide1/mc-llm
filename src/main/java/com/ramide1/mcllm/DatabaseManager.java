package com.ramide1.mcllm;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private final App plugin;
    private final Connection connection;
    private final Logger logger;
    private volatile int maxHistoryMessages;
    private boolean warnedDisconnected;

    public DatabaseManager(App plugin, int maxHistoryMessages) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.maxHistoryMessages = maxHistoryMessages;
        this.connection = init();
    }

    private Connection init() {
        Connection conn = null;
        try {
            String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/history.db";
            conn = DriverManager.getConnection(url);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user_id TEXT," +
                        "role TEXT," +
                        "content TEXT," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
            }
            logger.info("SQLite database initialized successfully.");
            return conn;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize SQLite database", e);
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
            return null;
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public synchronized void saveMessage(String userId, String role, String content) {
        if (!isConnected()) {
            if (!warnedDisconnected) {
                logger.warning("Database is not available. Messages will not be saved.");
                warnedDisconnected = true;
            }
            return;
        }
        String sql = "INSERT INTO history(user_id, role, content) VALUES(?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, role);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error saving message to database", e);
        }
    }

    public synchronized List<ChatMessage> getHistory(String userId) {
        List<ChatMessage> history = new ArrayList<>();
        if (!isConnected()) {
            if (!warnedDisconnected) {
                logger.warning("Database is not available. History will be empty.");
                warnedDisconnected = true;
            }
            return history;
        }
        String sql = "SELECT role, content FROM (" +
                "SELECT role, content, timestamp FROM history WHERE user_id = ? " +
                "ORDER BY timestamp DESC LIMIT ?) sub " +
                "ORDER BY timestamp ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setInt(2, maxHistoryMessages);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new ChatMessage(rs.getString("role"), rs.getString("content")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving history from database", e);
        }
        return history;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error closing database connection", e);
        }
    }

    public static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}