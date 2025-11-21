package com.chatflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Simple HTTP health check endpoint for monitoring consumer status.
 */
public class ConsumerHealthCheck {
    private final int port;
    private final MessageProcessor processor;
    private HttpServer server;

    public ConsumerHealthCheck(int port, MessageProcessor processor) {
        this.port = port;
        this.processor = processor;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", new HealthHandler());
            server.createContext("/metrics", new MetricsHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Consumer health check server started on port " + port);
        } catch (IOException e) {
            System.err.println("Could not start health check server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = "{\"status\":\"UP\",\"timestamp\":\"" +
                        java.time.Instant.now().toString() + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                long processed = processor.getProcessedCount();
                long failed = processor.getFailedCount();
                double avgTime = processor.getAverageProcessingTime();

                String response = String.format(
                        "{\"processed\":%d,\"failed\":%d,\"avgProcessingTime\":%.3f,\"timestamp\":\"%s\"}",
                        processed, failed, avgTime, java.time.Instant.now().toString()
                );

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}