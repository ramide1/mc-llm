package com.ramide1.mcllm;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private final App plugin;
    private Connection connection;
    private final Logger logger;

    public DatabaseManager(App plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        init();
    }

    private void init() {
        try {
            String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/history.db";
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user_id TEXT," +
                        "role TEXT," +
                        "content TEXT," +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
            }
            logger.info("SQLite database initialized successfully.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize SQLite database", e);
        }
    }

    public void saveMessage(String userId, String role, String content) {
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

    public List<ChatMessage> getHistory(String userId) {
        List<ChatMessage> history = new ArrayList<>();
        String sql = "SELECT role, content FROM history WHERE user_id = ? ORDER BY timestamp ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(new ChatMessage(rs.getString("role"), rs.getString("content")));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving history from database", e);
        }
        return history;
    }

    public void close() {
        try {
            if (connection != null) connection.close();
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

        public String getRole() { return role; }
        public String getContent() { return content; }
    }
}
