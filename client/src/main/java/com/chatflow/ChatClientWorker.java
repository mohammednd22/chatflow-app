
package com.chatflow;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatClientWorker implements Runnable {
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_BACKOFF_MS = 100;
    private static final int RESPONSE_TIMEOUT_MS = 15000;

    private final BlockingQueue<ChatMessage> messageQueue;
    private final PerformanceMetrics metrics;
    private final ObjectMapper objectMapper;
    private final int messagesToSend;
    private final CountDownLatch completionLatch;
    private final AtomicBoolean running;
    private final ConnectionPool connectionPool;
    private final CircuitBreaker circuitBreaker;

    public ChatClientWorker(BlockingQueue<ChatMessage> messageQueue,
                            PerformanceMetrics metrics,
                            int messagesToSend,
                            CountDownLatch completionLatch,
                            ConnectionPool connectionPool,
                            CircuitBreaker circuitBreaker) {
        this.messageQueue = messageQueue;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper();
        this.messagesToSend = messagesToSend;
        this.completionLatch = completionLatch;
        this.running = new AtomicBoolean(true);
        this.connectionPool = connectionPool;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void run() {
        int messagesSent = 0;

        try {
            while (running.get() && messagesSent < messagesToSend) {
                // Backpressure: Check circuit breaker
                if (circuitBreaker.isOpen()) {
                    Thread.sleep(100);
                    continue;
                }

                ChatMessage message = messageQueue.poll(1, TimeUnit.SECONDS);

                if (message == null) {
                    continue;
                }

                // ADD: Slow down if queue has too many messages
                int queueSize = messageQueue.size();
                if (queueSize > 5000) {
                    Thread.sleep(10); // Slow down client when backed up
                }

                boolean success = sendMessageWithRetry(message);

                if (success) {
                    messagesSent++;
                    circuitBreaker.recordSuccess();
                } else {
                    metrics.recordFailure();
                    circuitBreaker.recordFailure();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            completionLatch.countDown();
        }
    }

    private boolean sendMessageWithRetry(ChatMessage message) {
        MessageMetadata metadata = new MessageMetadata(message);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // Check circuit breaker before each attempt
            if (!circuitBreaker.allowRequest()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                continue;
            }

            if (attempt > 0) {
                metadata.incrementRetryCount();
                int backoffTime = INITIAL_BACKOFF_MS * (int) Math.pow(2, attempt - 1);
                try {
                    Thread.sleep(backoffTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            PooledConnection connection = null;
            try {
                // Get connection from pool
                connection = connectionPool.getConnection(message.getRoomId());

                if (connection == null || !connection.isConnected()) {
                    if (attempt < MAX_RETRIES - 1) {
                        metrics.recordReconnection();
                        continue;
                    }
                    return false;
                }

                // Send message
                String jsonMessage = objectMapper.writeValueAsString(message);
                connection.send(jsonMessage);

                // Wait for response
                String response = connection.waitForResponse(RESPONSE_TIMEOUT_MS);

                if (response != null) {
                    metadata.setReceivedTimestamp(System.currentTimeMillis());
                    metrics.recordSuccess(metadata);

                    // Return connection to pool
                    connectionPool.returnConnection(message.getRoomId(), connection);
                    return true;
                } else {
                    // Timeout - don't return connection to pool
                    if (connection.isConnected()) {
                        connection.close();
                    }
                }

            } catch (Exception e) {
                if (connection != null && connection.isConnected()) {
                    connection.close();
                }

                if (attempt == MAX_RETRIES - 1) {
                    System.err.println("Failed to send message after " + MAX_RETRIES +
                            " attempts: " + e.getMessage());
                }
            }
        }

        return false;
    }

    public void shutdown() {
        running.set(false);
    }
}