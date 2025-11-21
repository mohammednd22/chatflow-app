package com.chatflow;

import com.chatflow.QueuedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous batch database writer
 * Decouples message consumption from database writes for optimal throughput
 */
public class BatchDatabaseWriter {

    // Tunable parameters - test with different values
    private static final int DEFAULT_BATCH_SIZE = Integer.parseInt(
            System.getenv().getOrDefault("DB_BATCH_SIZE", "1000")
    );
    private static final long DEFAULT_FLUSH_INTERVAL_MS = Long.parseLong(
            System.getenv().getOrDefault("DB_FLUSH_INTERVAL_MS", "500")
    );
    private static final int WRITER_THREADS = Integer.parseInt(
            System.getenv().getOrDefault("DB_WRITER_THREADS", "4")
    );

    private final MessageRepository repository;
    private final BlockingQueue<QueuedMessage> writeQueue;
    private final ExecutorService writerExecutor;
    private final ScheduledExecutorService flushScheduler;
    private final AtomicBoolean running;
    private final AtomicLong queuedCount;
    private final AtomicLong writtenCount;

    private final int batchSize;
    private final long flushIntervalMs;

    public BatchDatabaseWriter(MessageRepository repository) {
        this(repository, DEFAULT_BATCH_SIZE, DEFAULT_FLUSH_INTERVAL_MS);
    }

    public BatchDatabaseWriter(MessageRepository repository, int batchSize, long flushIntervalMs) {
        this.repository = repository;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;

        // Bounded queue to prevent memory issues
        this.writeQueue = new LinkedBlockingQueue<>(50000);
        this.writerExecutor = Executors.newFixedThreadPool(WRITER_THREADS);
        this.flushScheduler = Executors.newScheduledThreadPool(1);
        this.running = new AtomicBoolean(false);
        this.queuedCount = new AtomicLong(0);
        this.writtenCount = new AtomicLong(0);

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║        Batch Database Writer Initialized                   ║");
        System.out.println("║  Batch size: " + batchSize + "                                         ║");
        System.out.println("║  Flush interval: " + flushIntervalMs + "ms                                    ║");
        System.out.println("║  Writer threads: " + WRITER_THREADS + "                                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * Start the batch writer threads
     */
    public void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }

        // Start writer threads
        for (int i = 0; i < WRITER_THREADS; i++) {
            final int threadId = i;
            writerExecutor.submit(() -> runWriter(threadId));
        }

        // Start periodic flush scheduler
        flushScheduler.scheduleAtFixedRate(
                this::logStats,
                10, 10, TimeUnit.SECONDS
        );

        System.out.println("✓ Batch database writer started");
    }

    /**
     * Queue a message for writing (non-blocking)
     */
    public boolean queueMessage(QueuedMessage message) {
        if (!running.get()) {
            return false;
        }

        try {
            boolean queued = writeQueue.offer(message, 100, TimeUnit.MILLISECONDS);
            if (queued) {
                queuedCount.incrementAndGet();
            }
            return queued;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Writer thread main loop
     */
    private void runWriter(int threadId) {
        List<QueuedMessage> batch = new ArrayList<>(batchSize);
        long lastFlushTime = System.currentTimeMillis();

        while (running.get() || !writeQueue.isEmpty()) {
            try {
                // Collect messages into batch
                QueuedMessage msg = writeQueue.poll(100, TimeUnit.MILLISECONDS);

                if (msg != null) {
                    batch.add(msg);
                }

                // Flush conditions: batch full OR time elapsed
                long currentTime = System.currentTimeMillis();
                boolean batchFull = batch.size() >= batchSize;
                boolean timeToFlush = (currentTime - lastFlushTime) >= flushIntervalMs;

                if (!batch.isEmpty() && (batchFull || timeToFlush || !running.get())) {
                    flushBatch(batch, threadId);
                    batch.clear();
                    lastFlushTime = currentTime;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Writer thread " + threadId + " error: " + e.getMessage());
            }
        }

        // Final flush
        if (!batch.isEmpty()) {
            flushBatch(batch, threadId);
        }

        System.out.println("Writer thread " + threadId + " stopped");
    }

    /**
     * Flush a batch to database
     */
    private void flushBatch(List<QueuedMessage> batch, int threadId) {
        if (batch.isEmpty()) {
            return;
        }

        long startTime = System.nanoTime();

        try {
            int inserted = repository.batchInsertMessages(batch);
            writtenCount.addAndGet(inserted);

            long duration = (System.nanoTime() - startTime) / 1_000_000; // ms

            if (duration > 1000) { // Log slow batches
                System.out.printf("⚠ Slow batch [Thread %d]: %d messages in %d ms%n",
                        threadId, batch.size(), duration);
            }

        } catch (Exception e) {
            System.err.println("Failed to flush batch [Thread " + threadId + "]: " + e.getMessage());
        }
    }

    /**
     * Graceful shutdown - flush all pending writes
     */
    public void shutdown() {
        System.out.println("\nShutting down batch database writer...");
        running.set(false);

        // Stop accepting new writes
        flushScheduler.shutdown();

        // Wait for writers to finish
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
        }

        System.out.println("✓ All pending writes flushed");
        printFinalStats();
    }

    /**
     * Get current write queue depth
     */
    public int getQueueDepth() {
        return writeQueue.size();
    }

    /**
     * Get total messages queued
     */
    public long getQueuedCount() {
        return queuedCount.get();
    }

    /**
     * Get total messages written
     */
    public long getWrittenCount() {
        return writtenCount.get();
    }

    /**
     * Periodic stats logging
     */
    private void logStats() {
        System.out.println("\n" + "─".repeat(60));
        System.out.println("DATABASE WRITER STATS");
        System.out.println("─".repeat(60));
        System.out.println("Write queue depth: " + getQueueDepth());
        System.out.println("Messages queued: " + queuedCount.get());
        System.out.println("Messages written: " + writtenCount.get());
        System.out.println("DB inserts: " + repository.getInsertedCount());
        System.out.println("DB failures: " + repository.getFailedCount());

        long pending = queuedCount.get() - writtenCount.get();
        System.out.println("Pending writes: " + pending);

        DatabaseConfig.printPoolStats();
    }

    private void printFinalStats() {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("DATABASE WRITER FINAL STATS");
        System.out.println("═".repeat(60));
        System.out.println("Total messages queued: " + queuedCount.get());
        System.out.println("Total messages written: " + writtenCount.get());
        System.out.println("Total DB inserts: " + repository.getInsertedCount());
        System.out.println("Total DB failures: " + repository.getFailedCount());

        double successRate = (repository.getInsertedCount() * 100.0) / queuedCount.get();
        System.out.printf("Success rate: %.2f%%%n", successRate);
        System.out.println("═".repeat(60));
    }
}