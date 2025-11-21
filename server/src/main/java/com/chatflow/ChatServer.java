package com.chatflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

public class ChatServer extends WebSocketServer {
    private static final int PORT = 8080;
    private static final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<WebSocket, String> connectionToRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebSocket>> roomToConnections = new ConcurrentHashMap<>();
    private static final Pattern usernamePattern = Pattern.compile("^[a-zA-Z0-9]{3,20}$");

    private final MessagePublisher messagePublisher;
    private final RedisSubscriber redisSubscriber;

    public ChatServer(MessagePublisher messagePublisher) {
        super(new InetSocketAddress(PORT));
        this.messagePublisher = messagePublisher;
        this.redisSubscriber = new RedisSubscriber(roomToConnections);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String path = handshake.getResourceDescriptor();
        String roomId = extractRoomId(path);

        if (roomId != null) {
            connectionToRoom.put(conn, roomId);

            // Add to room connections
            roomToConnections.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(conn);

            System.out.println("New connection to room " + roomId +
                    " (Total in room: " + roomToConnections.get(roomId).size() + ")");
        } else {
            System.out.println("Invalid room ID. Closing connection.");
            conn.close(4000, "Invalid room ID");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String roomId = connectionToRoom.remove(conn);

        if (roomId != null) {
            Set<WebSocket> roomConns = roomToConnections.get(roomId);
            if (roomConns != null) {
                roomConns.remove(conn);
                if (roomConns.isEmpty()) {
                    roomToConnections.remove(roomId);
                }
            }
            System.out.println("Connection closed from room " + roomId);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String roomId = connectionToRoom.get(conn);

        if (roomId == null) {
            // Don't log or try to close - connection already dead
            return;
        }

        try {
            ChatMessage chatMessage = mapper.readValue(message, ChatMessage.class);
            ValidationResult validation = validateMessage(chatMessage);

            if (!validation.isValid()) {
                if (conn.isOpen()) {
                    sendErrorResponse(conn, "VALIDATION_ERROR", validation.getErrorMessage());
                }
                return;
            }

            QueuedMessage queuedMessage = new QueuedMessage(chatMessage, roomId);
            boolean published = messagePublisher.publishMessage(queuedMessage);

            if (published) {
                if (conn.isOpen()) {
                    try {
                        ChatResponse response = new ChatResponse(
                                chatMessage.getUserId(),
                                chatMessage.getUsername(),
                                chatMessage.getMessage(),
                                chatMessage.getTimestamp(),
                                chatMessage.getMessageType(),
                                "OK",
                                Instant.now().toString()
                        );
                        conn.send(mapper.writeValueAsString(response));
                    } catch (Exception e) {
                        // Connection closed during send - ignore silently
                    }
                }
            } else {
                if (conn.isOpen()) {
                    sendErrorResponse(conn, "QUEUE_ERROR", "Failed to queue message");
                }
            }

        } catch (JsonProcessingException e) {
            if (conn.isOpen()) {
                sendErrorResponse(conn, "PARSE_ERROR", "Invalid JSON format");
            }
        } catch (Exception e) {
            // Only log if it's not a connection issue
            if (!(e instanceof org.java_websocket.exceptions.WebsocketNotConnectedException)) {
                System.err.println("Error processing message: " + e.getMessage());
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            String roomId = connectionToRoom.remove(conn);
            if (roomId != null) {
                Set<WebSocket> roomConns = roomToConnections.get(roomId);
                if (roomConns != null) {
                    roomConns.remove(conn);
                }
            }
        }
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("ChatFlow WebSocket Server started on port " + PORT);
        setConnectionLostTimeout(60);

        // Start Redis subscriber
        redisSubscriber.start();
        System.out.println("Redis subscriber started for room broadcasting");
    }

    private String extractRoomId(String path) {
        try {
            String[] parts = path.split("/");
            if (parts.length >= 3 && parts[1].equals("chat")) {
                return parts[2];
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private ValidationResult validateMessage(ChatMessage message) {
        if (message.getUserId() == null || message.getUserId() < 1 || message.getUserId() > 100000) {
            return new ValidationResult(false, "userId must be between 1 and 100000");
        }

        if (message.getUsername() == null || !usernamePattern.matcher(message.getUsername()).matches()) {
            return new ValidationResult(false, "username must be 3-20 alphanumeric characters");
        }

        if (message.getMessage() == null || message.getMessage().length() < 1 ||
                message.getMessage().length() > 500) {
            return new ValidationResult(false, "message must be 1-500 characters");
        }

        if (message.getTimestamp() == null) {
            return new ValidationResult(false, "timestamp is required");
        }

        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(message.getTimestamp());
        } catch (DateTimeParseException e) {
            return new ValidationResult(false, "timestamp must be valid ISO-8601");
        }

        if (message.getMessageType() == null) {
            return new ValidationResult(false, "messageType is required");
        }

        try {
            MessageType.valueOf(message.getMessageType());
        } catch (IllegalArgumentException e) {
            return new ValidationResult(false, "messageType must be TEXT, JOIN, or LEAVE");
        }

        return new ValidationResult(true, null);
    }

    private void sendErrorResponse(WebSocket conn, String errorType, String errorMessage) {
        if (conn == null || !conn.isOpen()) {
            return; // Silently ignore
        }

        try {
            ErrorResponse errorResponse = new ErrorResponse(
                    errorType,
                    errorMessage,
                    Instant.now().toString()
            );
            conn.send(mapper.writeValueAsString(errorResponse));
        } catch (Exception e) {
            // Connection closed - ignore silently
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Initializing RabbitMQ connection...");
            RabbitMQConfig.initialize();

            MessagePublisher publisher = new MessagePublisher();

            ChatServer server = new ChatServer(publisher);
            server.start();

            HealthCheckServer healthServer = new HealthCheckServer(8081);
            healthServer.start();

            try {
                DatabaseConfig.initialize();
                MetricsAPI metricsAPI = new MetricsAPI();
                metricsAPI.start(8082); // Different port from health check
                System.out.println("Metrics API available at: http://localhost:8080/api/metrics");
            } catch (Exception e) {
                System.err.println("Failed to start metrics API: " + e.getMessage());
                // Continue without metrics - not critical
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down ChatFlow server...");
                try {
                    server.redisSubscriber.shutdown();
                    server.stop(1000);
                    healthServer.stop();
                    RabbitMQConfig.shutdown();
                } catch (InterruptedException e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                }
            }));

            System.out.println("ChatFlow server with Redis Pub/Sub is running...");

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}