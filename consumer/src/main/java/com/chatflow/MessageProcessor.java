package com.chatflow;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicLong;

public class MessageProcessor {
    private final ObjectMapper objectMapper;
    private final AtomicLong processedCount;
    private final AtomicLong failedCount;
    private final AtomicLong totalProcessingTime;
    private final RedisPublisherOptimized redisPublisher;
    private final BatchDatabaseWriter databaseWriter;
    private final boolean persistenceEnabled;

    public MessageProcessor(RedisPublisherOptimized redisPublisher, BatchDatabaseWriter databaseWriter) {
        this.objectMapper = new ObjectMapper();
        this.processedCount = new AtomicLong(0);
        this.failedCount = new AtomicLong(0);
        this.totalProcessingTime = new AtomicLong(0);
        this.redisPublisher = redisPublisher;
        this.databaseWriter = databaseWriter;
        this.persistenceEnabled = (databaseWriter != null);

        if (persistenceEnabled) {
            System.out.println("✓ Message processor initialized WITH database persistence");
        } else {
            System.out.println("✓ Message processor initialized WITHOUT database persistence");
        }
    }

    public boolean processMessage(String messageJson) {
        long startTime = System.nanoTime();

        try {
            QueuedMessage queuedMessage = objectMapper.readValue(messageJson, QueuedMessage.class);
            ChatMessage chatMessage = queuedMessage.getChatMessage();
            String roomId = queuedMessage.getRoomId();

            // ========================================
            // 1. BROADCAST to room via Redis (real-time)
            // ========================================
            BroadcastMessage broadcast = new BroadcastMessage(
                    chatMessage.getUserId(),
                    chatMessage.getUsername(),
                    chatMessage.getMessage(),
                    chatMessage.getTimestamp(),
                    chatMessage.getMessageType(),
                    roomId,
                    System.currentTimeMillis()
            );

            String broadcastJson = objectMapper.writeValueAsString(broadcast);
            boolean published = redisPublisher.publishToRoom(roomId, broadcastJson);

            if (!published) {
                failedCount.incrementAndGet();
                return false;
            }

            // ========================================
            // 2. PERSIST to database (async, non-blocking)
            // ========================================
            if (persistenceEnabled) {
                boolean queued = databaseWriter.queueMessage(queuedMessage);
                if (!queued) {
                    System.err.println("⚠ Database write queue full - message dropped");
                }
            }

            // ========================================
            // 3. Update metrics
            // ========================================
            long processingTime = System.nanoTime() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            long count = processedCount.incrementAndGet();

            if (count % 100000 == 0) {
                double avgProcessingTime = totalProcessingTime.get() / (double) count / 1_000_000;
                long broadcasts = redisPublisher.getPublishedCount();
                long dbWrites = persistenceEnabled ? databaseWriter.getWrittenCount() : 0;

                System.out.printf("Processed %d msgs | Avg: %.3f ms | Broadcasts: %d | DB writes: %d%n",
                        count, avgProcessingTime, broadcasts, dbWrites);
            }

            return true;

        } catch (Exception e) {
            failedCount.incrementAndGet();
            System.err.println("Failed to process message: " + e.getMessage());
            return false;
        }
    }

    public long getProcessedCount() {
        return processedCount.get();
    }

    public long getFailedCount() {
        return failedCount.get();
    }

    public double getAverageProcessingTime() {
        long count = processedCount.get();
        if (count == 0) return 0;
        return totalProcessingTime.get() / (double) count / 1_000_000;
    }
}