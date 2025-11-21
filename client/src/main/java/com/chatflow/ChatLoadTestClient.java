package com.chatflow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ChatLoadTestClient {
    private static final int TOTAL_MESSAGES = 500000;
    private static final int WARMUP_THREADS = 32;
    private static final int WARMUP_MESSAGES_PER_THREAD = 1000;
    private static final int WARMUP_MESSAGES = WARMUP_THREADS * WARMUP_MESSAGES_PER_THREAD;
    private static final int MAIN_PHASE_MESSAGES = TOTAL_MESSAGES - WARMUP_MESSAGES;

    // Optimal thread count for main phase (can be tuned)
    private static final int MAIN_PHASE_THREADS = 64;

    private final BlockingQueue<ChatMessage> messageQueue;
    private final PerformanceMetrics metrics;
    private final ExecutorService executorService;
    private final ConnectionPool connectionPool;
    private final CircuitBreaker circuitBreaker;

    public ChatLoadTestClient() {
        // Use bounded queue to prevent memory issues and provide backpressure
        this.messageQueue = new LinkedBlockingQueue<>(10000);
        this.metrics = new PerformanceMetrics();
        this.executorService = Executors.newCachedThreadPool();
        this.connectionPool = new ConnectionPool(metrics);
        this.circuitBreaker = new CircuitBreaker();
    }

    public void runLoadTest() {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║         ChatFlow WebSocket Load Test - Starting...            ║");
        System.out.println("║              With Connection Pooling & Circuit Breaker         ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Start message generator thread
            Thread generatorThread = new Thread(
                    new MessageGenerator(messageQueue, TOTAL_MESSAGES)
            );
            generatorThread.start();

            // Wait a bit for queue to fill
            Thread.sleep(1000);

            // Phase 1: Warmup
            System.out.println("\n" + "─".repeat(60));
            System.out.println("PHASE 1: WARMUP - " + WARMUP_THREADS + " threads, " +
                    WARMUP_MESSAGES + " messages");
            System.out.println("─".repeat(60));

            long warmupStart = System.currentTimeMillis();
            metrics.setStartTime(warmupStart);

            runPhase(WARMUP_THREADS, WARMUP_MESSAGES_PER_THREAD);

            long warmupEnd = System.currentTimeMillis();
            double warmupTime = (warmupEnd - warmupStart) / 1000.0;
            double warmupThroughput = WARMUP_MESSAGES / warmupTime;

            System.out.println("\n✓ Warmup phase completed!");
            System.out.printf("  Duration: %.2f seconds%n", warmupTime);
            System.out.printf("  Throughput: %.2f messages/second%n", warmupThroughput);
            System.out.printf("  Circuit Breaker State: %s%n", circuitBreaker.getState());

            // Little's Law Analysis
            performLittlesLawAnalysis(warmupThroughput);

            // Phase 2: Main load test
            System.out.println("\n" + "─".repeat(60));
            System.out.println("PHASE 2: MAIN LOAD TEST - " + MAIN_PHASE_THREADS +
                    " threads, " + MAIN_PHASE_MESSAGES + " messages");
            System.out.println("─".repeat(60));

            int messagesPerThread = MAIN_PHASE_MESSAGES / MAIN_PHASE_THREADS;
            runPhase(MAIN_PHASE_THREADS, messagesPerThread);

            metrics.setEndTime(System.currentTimeMillis());

            // Wait for generator to finish
            generatorThread.join();

            System.out.println("\n✓ All phases completed!");
            System.out.printf("  Final Circuit Breaker State: %s%n", circuitBreaker.getState());

            // Print statistics
            metrics.printStatistics();

            // Write CSV
            metrics.writeToCSV("performance_metrics.csv");

            // Create visualization
            ThroughputVisualizer.createThroughputChart(
                    metrics.getThroughputBuckets(),
                    metrics.getStartTime(),
                    "throughput_chart.png"
            );

            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║              Load Test Completed Successfully!                 ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("Error during load test: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            connectionPool.shutdown();
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    private void runPhase(int threadCount, int messagesPerThread) throws InterruptedException {
        CountDownLatch phaseLatch = new CountDownLatch(threadCount);
        List<ChatClientWorker> workers = new ArrayList<>();

        // Create and start worker threads
        for (int i = 0; i < threadCount; i++) {
            ChatClientWorker worker = new ChatClientWorker(
                    messageQueue,
                    metrics,
                    messagesPerThread,
                    phaseLatch,
                    connectionPool,
                    circuitBreaker
            );
            workers.add(worker);
            executorService.submit(worker);
        }

        // Wait for all threads to complete
        phaseLatch.await();

        // Cleanup workers
        for (ChatClientWorker worker : workers) {
            worker.shutdown();
        }
    }

    private void performLittlesLawAnalysis(double observedThroughput) {
        System.out.println("\n" + "─".repeat(60));
        System.out.println("LITTLE'S LAW ANALYSIS");
        System.out.println("─".repeat(60));

        System.out.println("\nObserved Metrics:");
        System.out.printf("  Warmup throughput: %.2f msg/s%n", observedThroughput);
        System.out.printf("  Active connections: %d%n", WARMUP_THREADS);

        // Estimate average latency based on throughput and concurrency
        double estimatedLatency = (WARMUP_THREADS * 1000.0) / observedThroughput;
        System.out.printf("  Estimated avg latency: %.2f ms%n", estimatedLatency);

        System.out.println("\nPredictions for Main Phase:");
        double predictedThroughput = (MAIN_PHASE_THREADS * 1000.0) / estimatedLatency;
        System.out.printf("  Expected throughput: %.2f msg/s%n", predictedThroughput);
        System.out.printf("  Expected completion time: %.2f seconds%n",
                MAIN_PHASE_MESSAGES / predictedThroughput);

        System.out.println("─".repeat(60));
    }

    public static void main(String[] args) {
        ChatLoadTestClient client = new ChatLoadTestClient();
        client.runLoadTest();
    }
}