package com.chatflow;

import com.chatflow.DatabaseConfig;
import com.chatflow.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Simple metrics API - returns all analytics in one JSON response
 */
public class MetricsAPI {

    private final MessageRepository repository;
    private final ObjectMapper objectMapper;
    private HttpServer server;

    public MetricsAPI() {
        this.repository = new MessageRepository();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/metrics", new MetricsHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Metrics API started on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                // Time range: last 24 hours
                Instant endTime = Instant.now();
                Instant startTime = endTime.minus(24, ChronoUnit.HOURS);

                // Collect all metrics
                Map<String, Object> metrics = new HashMap<>();

                // Core Query 1: Sample room messages
                metrics.put("sampleRoomMessages", repository.getMessagesForRoom(1, startTime, endTime).size());

                // Core Query 2: Sample user history
                metrics.put("sampleUserMessages", repository.getUserMessageHistory(1, startTime, endTime).size());

                // Core Query 3: Active users
                metrics.put("activeUsersLast24h", repository.countActiveUsers(startTime, endTime));

                // Core Query 4: Sample user rooms
                metrics.put("sampleUserRooms", repository.getRoomsForUser(1).size());

                // Analytics: Top users
                metrics.put("topUsers", repository.getTopUsers(10));

                // Analytics: Top rooms
                metrics.put("topRooms", repository.getTopRooms(10));

                // Analytics: Messages per minute
                metrics.put("messagesPerMinute", repository.getMessagesPerMinute(startTime, endTime));

                // Convert to JSON
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics);

                // Send response
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.getBytes().length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json.getBytes());
                }

            } catch (Exception e) {
                String error = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.sendResponseHeaders(500, error.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes());
                }
            }
        }
    }
}