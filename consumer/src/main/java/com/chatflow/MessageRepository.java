package com.chatflow;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for message persistence and analytics queries
 * Optimized for high throughput batch writes
 */
public class MessageRepository {

    private final AtomicLong insertedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    // Prepared statement SQL
    private static final String INSERT_MESSAGE_SQL =
            "INSERT INTO messages (message_id, room_id, user_id, username, message_text, " +
                    "message_type, client_timestamp, server_timestamp, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (message_id, created_at) DO NOTHING"; // Idempotent

    /**
     * Batch insert messages for maximum throughput
     * Uses prepared statements with batch execution
     */
    public int batchInsertMessages(List<QueuedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_MESSAGE_SQL)) {

            for (QueuedMessage qm : messages) {
                ChatMessage msg = qm.getChatMessage();

                // Generate unique message ID
                String messageId = UUID.randomUUID().toString();

                stmt.setString(1, messageId);
                stmt.setInt(2, Integer.parseInt(qm.getRoomId()));
                stmt.setInt(3, msg.getUserId());
                stmt.setString(4, msg.getUsername());
                stmt.setString(5, msg.getMessage());
                stmt.setString(6, msg.getMessageType());
                stmt.setTimestamp(7, Timestamp.from(Instant.parse(msg.getTimestamp())));
                stmt.setTimestamp(8, new Timestamp(qm.getReceivedTimestamp()));
                stmt.setTimestamp(9, new Timestamp(System.currentTimeMillis()));

                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            conn.commit();

            int successCount = 0;
            for (int result : results) {
                if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                    successCount++;
                }
            }

            insertedCount.addAndGet(successCount);

            return successCount;

        } catch (SQLException e) {
            failedCount.addAndGet(messages.size());
            System.err.println("Batch insert failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * CORE QUERY 1: Get messages for a room in time range
     * Target: < 100ms for 1000 messages
     */
    public List<MessageRecord> getMessagesForRoom(int roomId, Instant startTime, Instant endTime) {
        String sql = "SELECT message_id, room_id, user_id, username, message_text, " +
                "message_type, client_timestamp, server_timestamp, created_at " +
                "FROM messages " +
                "WHERE room_id = ? AND created_at BETWEEN ? AND ? " +
                "ORDER BY created_at DESC " +
                "LIMIT 1000";

        List<MessageRecord> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, roomId);
            stmt.setTimestamp(2, Timestamp.from(startTime));
            stmt.setTimestamp(3, Timestamp.from(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(extractMessageRecord(rs));
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * CORE QUERY 2: Get user's message history
     * Target: < 200ms
     */
    public List<MessageRecord> getUserMessageHistory(int userId, Instant startTime, Instant endTime) {
        String sql = "SELECT message_id, room_id, user_id, username, message_text, " +
                "message_type, client_timestamp, server_timestamp, created_at " +
                "FROM messages " +
                "WHERE user_id = ? AND created_at BETWEEN ? AND ? " +
                "ORDER BY created_at DESC " +
                "LIMIT 10000";

        List<MessageRecord> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setTimestamp(2, Timestamp.from(startTime));
            stmt.setTimestamp(3, Timestamp.from(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(extractMessageRecord(rs));
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * CORE QUERY 3: Count active users in time window
     * Target: < 500ms
     */
    public long countActiveUsers(Instant startTime, Instant endTime) {
        String sql = "SELECT COUNT(DISTINCT user_id) FROM messages " +
                "WHERE created_at BETWEEN ? AND ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(startTime));
            stmt.setTimestamp(2, Timestamp.from(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }

        return 0;
    }

    /**
     * CORE QUERY 4: Get rooms user has participated in
     * Target: < 50ms
     */
    public List<RoomParticipation> getRoomsForUser(int userId) {
        String sql = "SELECT room_id, MAX(created_at) as last_activity, COUNT(*) as message_count " +
                "FROM messages " +
                "WHERE user_id = ? " +
                "GROUP BY room_id " +
                "ORDER BY last_activity DESC";

        List<RoomParticipation> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new RoomParticipation(
                            rs.getInt("room_id"),
                            rs.getTimestamp("last_activity").toInstant(),
                            rs.getInt("message_count")
                    ));
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * ANALYTICS: Messages per second/minute
     */
    public List<MessageRate> getMessagesPerMinute(Instant startTime, Instant endTime) {
        String sql = "SELECT date_trunc('minute', created_at) as minute, " +
                "COUNT(*) as message_count " +
                "FROM messages " +
                "WHERE created_at BETWEEN ? AND ? " +
                "GROUP BY date_trunc('minute', created_at) " +
                "ORDER BY minute";

        List<MessageRate> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(startTime));
            stmt.setTimestamp(2, Timestamp.from(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new MessageRate(
                            rs.getTimestamp("minute").toInstant(),
                            rs.getLong("message_count")
                    ));
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * ANALYTICS: Most active users (top N)
     */
    public List<UserStats> getTopUsers(int limit) {
        String sql = "SELECT user_id, username, COUNT(*) as message_count " +
                "FROM messages " +
                "GROUP BY user_id, username " +
                "ORDER BY message_count DESC " +
                "LIMIT ?";

        List<UserStats> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new UserStats(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getLong("message_count")
                    ));
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * ANALYTICS: Most active rooms (top N)
     */
    public List<RoomStats> getTopRooms(int limit) {
        String sql = "SELECT room_id, COUNT(*) as message_count, " +
                "COUNT(DISTINCT user_id) as unique_users " +
                "FROM messages " +
                "GROUP BY room_id " +
                "ORDER BY message_count DESC " +
                "LIMIT ?";

        List<RoomStats> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new RoomStats(
                            rs.getInt("room_id"),
                            rs.getLong("message_count"),
                            rs.getInt("unique_users")
                    ));
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }

        return results;
    }

    private MessageRecord extractMessageRecord(ResultSet rs) throws SQLException {
        return new MessageRecord(
                rs.getString("message_id"),
                rs.getInt("room_id"),
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("message_text"),
                rs.getString("message_type"),
                rs.getTimestamp("client_timestamp").toInstant(),
                rs.getTimestamp("server_timestamp").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public long getInsertedCount() {
        return insertedCount.get();
    }

    public long getFailedCount() {
        return failedCount.get();
    }

    // Data classes for query results
    public static class MessageRecord {
        public final String messageId;
        public final int roomId;
        public final int userId;
        public final String username;
        public final String messageText;
        public final String messageType;
        public final Instant clientTimestamp;
        public final Instant serverTimestamp;
        public final Instant createdAt;

        public MessageRecord(String messageId, int roomId, int userId, String username,
                             String messageText, String messageType, Instant clientTimestamp,
                             Instant serverTimestamp, Instant createdAt) {
            this.messageId = messageId;
            this.roomId = roomId;
            this.userId = userId;
            this.username = username;
            this.messageText = messageText;
            this.messageType = messageType;
            this.clientTimestamp = clientTimestamp;
            this.serverTimestamp = serverTimestamp;
            this.createdAt = createdAt;
        }
    }

    public static class RoomParticipation {
        public final int roomId;
        public final Instant lastActivity;
        public final int messageCount;

        public RoomParticipation(int roomId, Instant lastActivity, int messageCount) {
            this.roomId = roomId;
            this.lastActivity = lastActivity;
            this.messageCount = messageCount;
        }
    }

    public static class MessageRate {
        public final Instant timestamp;
        public final long messageCount;

        public MessageRate(Instant timestamp, long messageCount) {
            this.timestamp = timestamp;
            this.messageCount = messageCount;
        }
    }

    public static class UserStats {
        public final int userId;
        public final String username;
        public final long messageCount;

        public UserStats(int userId, String username, long messageCount) {
            this.userId = userId;
            this.username = username;
            this.messageCount = messageCount;
        }
    }

    public static class RoomStats {
        public final int roomId;
        public final long messageCount;
        public final int uniqueUsers;

        public RoomStats(int roomId, long messageCount, int uniqueUsers) {
            this.roomId = roomId;
            this.messageCount = messageCount;
            this.uniqueUsers = uniqueUsers;
        }
    }
}