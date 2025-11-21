package com.chatflow;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceMetrics {
    private final ConcurrentLinkedQueue<MessageMetadata> completedMessages;
    private final AtomicInteger successCount;
    private final AtomicInteger failedCount;
    private final AtomicInteger totalConnections;
    private final AtomicInteger reconnectionCount;
    private final ConcurrentHashMap<Integer, AtomicInteger> roomMessageCounts;
    private final ConcurrentHashMap<String, AtomicInteger> messageTypeCounts;
    private final AtomicLong startTime;
    private final AtomicLong endTime;

    // For throughput tracking (10-second buckets)
    private final ConcurrentHashMap<Long, AtomicInteger> throughputBuckets;

    public PerformanceMetrics() {
        this.completedMessages = new ConcurrentLinkedQueue<>();
        this.successCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
        this.totalConnections = new AtomicInteger(0);
        this.reconnectionCount = new AtomicInteger(0);
        this.roomMessageCounts = new ConcurrentHashMap<>();
        this.messageTypeCounts = new ConcurrentHashMap<>();
        this.startTime = new AtomicLong(0);
        this.endTime = new AtomicLong(0);
        this.throughputBuckets = new ConcurrentHashMap<>();
    }

    public void recordSuccess(MessageMetadata metadata) {
        successCount.incrementAndGet();
        completedMessages.add(metadata);

        // Track per-room counts
        Integer roomId = metadata.getMessage().getRoomId();
        roomMessageCounts.computeIfAbsent(roomId, k -> new AtomicInteger(0)).incrementAndGet();

        // Track message type counts
        String messageType = metadata.getMessage().getMessageType();
        messageTypeCounts.computeIfAbsent(messageType, k -> new AtomicInteger(0)).incrementAndGet();

        // Track throughput (10-second buckets)
        long bucketKey = metadata.getReceivedTimestamp() / 10000; // 10-second buckets
        throughputBuckets.computeIfAbsent(bucketKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void recordFailure() {
        failedCount.incrementAndGet();
    }

    public void recordConnection() {
        totalConnections.incrementAndGet();
    }

    public void recordReconnection() {
        reconnectionCount.incrementAndGet();
    }

    public void setStartTime(long time) {
        startTime.set(time);
    }

    public void setEndTime(long time) {
        endTime.set(time);
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public void writeToCSV(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("timestamp,messageType,latency,status,roomId");

            for (MessageMetadata metadata : completedMessages) {
                writer.printf("%d,%s,%d,OK,%d%n",
                        metadata.getReceivedTimestamp(),
                        metadata.getMessage().getMessageType(),
                        metadata.getLatency(),
                        metadata.getMessage().getRoomId()
                );
            }
        }
        System.out.println("Metrics written to " + filename);
    }

    public void printStatistics() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE ANALYSIS REPORT");
        System.out.println("=".repeat(80));

        // Basic counts
        System.out.println("\n--- Message Statistics ---");
        System.out.println("Total messages sent: " + (successCount.get() + failedCount.get()));
        System.out.println("Successful messages: " + successCount.get());
        System.out.println("Failed messages: " + failedCount.get());
        System.out.printf("Success rate: %.2f%%%n",
                (successCount.get() * 100.0 / (successCount.get() + failedCount.get())));

        // Timing
        System.out.println("\n--- Timing Statistics ---");
        long totalTime = endTime.get() - startTime.get();
        System.out.printf("Total runtime: %.2f seconds%n", totalTime / 1000.0);
        double throughput = successCount.get() / (totalTime / 1000.0);
        System.out.printf("Overall throughput: %.2f messages/second%n", throughput);

        // Connection stats
        System.out.println("\n--- Connection Statistics ---");
        System.out.println("Total connections established: " + totalConnections.get());
        System.out.println("Reconnections: " + reconnectionCount.get());

        // Latency statistics
        if (!completedMessages.isEmpty()) {
            System.out.println("\n--- Latency Statistics ---");
            DescriptiveStatistics stats = new DescriptiveStatistics();

            for (MessageMetadata metadata : completedMessages) {
                stats.addValue(metadata.getLatency());
            }

            System.out.printf("Mean response time: %.2f ms%n", stats.getMean());
            System.out.printf("Median response time: %.2f ms%n", stats.getPercentile(50));
            System.out.printf("95th percentile: %.2f ms%n", stats.getPercentile(95));
            System.out.printf("99th percentile: %.2f ms%n", stats.getPercentile(99));
            System.out.printf("Min response time: %.2f ms%n", stats.getMin());
            System.out.printf("Max response time: %.2f ms%n", stats.getMax());
            System.out.printf("Standard deviation: %.2f ms%n", stats.getStandardDeviation());
        }

        // Per-room statistics
        System.out.println("\n--- Per-Room Statistics ---");
        List<Map.Entry<Integer, AtomicInteger>> sortedRooms = new ArrayList<>(roomMessageCounts.entrySet());
        sortedRooms.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, AtomicInteger> entry : sortedRooms) {
            double roomThroughput = entry.getValue().get() / (totalTime / 1000.0);
            System.out.printf("Room %d: %d messages (%.2f msg/s)%n",
                    entry.getKey(), entry.getValue().get(), roomThroughput);
        }

        // Message type distribution
        System.out.println("\n--- Message Type Distribution ---");
        int totalMessages = successCount.get();
        for (Map.Entry<String, AtomicInteger> entry : messageTypeCounts.entrySet()) {
            double percentage = (entry.getValue().get() * 100.0) / totalMessages;
            System.out.printf("%s: %d (%.2f%%)%n",
                    entry.getKey(), entry.getValue().get(), percentage);
        }

        System.out.println("\n" + "=".repeat(80));
    }

    public Map<Long, Integer> getThroughputBuckets() {
        Map<Long, Integer> result = new HashMap<>();
        for (Map.Entry<Long, AtomicInteger> entry : throughputBuckets.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }

    public long getStartTime() {
        return startTime.get();
    }
}