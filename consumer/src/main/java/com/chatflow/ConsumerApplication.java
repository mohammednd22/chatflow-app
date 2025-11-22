package com.chatflow;

import com.rabbitmq.client.Channel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConsumerApplication {

    private final List<MessageConsumer> consumers;
    private final ExecutorService executorService;
    private final MessageProcessor processor;
    private final RedisPublisherOptimized redisPublisher;
    private final BatchDatabaseWriter databaseWriter;
    private final MessageRepository repository;
    private final int consumersPerRoom;
    private final int roomCount;
    private final boolean persistenceEnabled;

    public ConsumerApplication(int consumersPerRoom, int roomCount, boolean persistenceEnabled) {
        this.consumersPerRoom = consumersPerRoom;
        this.roomCount = roomCount;
        this.persistenceEnabled = persistenceEnabled;
        this.consumers = new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(consumersPerRoom * roomCount);
        this.redisPublisher = new RedisPublisherOptimized();

        // Initialize a database if persistence enabled
        if (persistenceEnabled) {
            try {
                System.out.println("\nInitializing database...");
                DatabaseConfig.initialize();
                this.repository = new MessageRepository();
                this.databaseWriter = new BatchDatabaseWriter(repository);
                System.out.println("✓ Database initialized");
            } catch (Exception e) {
                System.err.println("Failed to initialize database: " + e.getMessage());
                throw new RuntimeException("Database initialization failed", e);
            }
        } else {
            this.repository = null;
            this.databaseWriter = null;
            System.out.println("Running WITHOUT database persistence");
        }

        this.processor = new MessageProcessor(redisPublisher, databaseWriter);
    }

    public void start() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("   ChatFlow Message Consumer - Starting...");
        System.out.println("   Rooms: 1-" + roomCount);
        System.out.println("   Consumers per room: " + consumersPerRoom);
        System.out.println("   Total consumer threads: " + (consumersPerRoom * roomCount));
        System.out.println("   Prefetch count: " + ConsumerConfig.PREFETCH_COUNT);
        System.out.println("   Redis Pub/Sub: Enabled");
        System.out.println("   Database Persistence: " + (persistenceEnabled ? "ENABLED" : "DISABLED"));
        System.out.println("═══════════════════════════════════════════════════════════");

        // PRE-CREATE ALL ROOM QUEUES
        try {
            System.out.println("\nCreating room queues...");
            Channel setupChannel = ConsumerConfig.createChannel();

            for (int roomId = 1; roomId <= roomCount; roomId++) {
                String queueName = ConsumerConfig.getRoomQueueName(roomId);

                try {
                    setupChannel.queuePurge(queueName);
                    System.out.println("  Purged existing queue: " + queueName);
                } catch (Exception e) {
                    // Queue might not exist yet - that's ok
                }

                Map<String, Object> queueArgs = new HashMap<>();
                queueArgs.put("x-dead-letter-exchange", "chat.dlx.exchange");
                queueArgs.put("x-dead-letter-routing-key", "dlq");
                queueArgs.put("x-max-length", 50000);

                setupChannel.queueDeclare(queueName, true, false, false, queueArgs);
                setupChannel.queueBind(queueName, "chat.exchange", String.valueOf(roomId));

                System.out.println("  ✓ Created queue: " + queueName);
            }

            setupChannel.close();
            System.out.println("All room queues created successfully!\n");

        } catch (Exception e) {
            System.err.println("Failed to create room queues: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // START DATABASE WRITER
        if (persistenceEnabled && databaseWriter != null) {
            databaseWriter.start();
        }

        // START CONSUMERS
        int consumerId = 1;

        for (int roomId = 1; roomId <= roomCount; roomId++) {
            for (int i = 0; i < consumersPerRoom; i++) {
                MessageConsumer consumer = new MessageConsumer(consumerId++, roomId, processor);
                consumers.add(consumer);
                executorService.submit(consumer);
            }
        }

        startMonitoringThread();

        System.out.println("All " + consumers.size() + " consumers started successfully!");
    }

    private void startMonitoringThread() {
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10000); // Report every 10 seconds

                    long processed = processor.getProcessedCount();
                    long failed = processor.getFailedCount();
                    double avgTime = processor.getAverageProcessingTime();
                    long broadcasts = redisPublisher.getPublishedCount();

                    int activeConsumers = (int) consumers.stream()
                            .filter(MessageConsumer::isRunning)
                            .count();

                    System.out.println("\n" + "─".repeat(60));
                    System.out.println("CONSUMER STATS");
                    System.out.println("─".repeat(60));
                    System.out.println("Active consumers: " + activeConsumers + "/" + consumers.size());
                    System.out.println("Messages processed: " + processed);
                    System.out.println("Redis broadcasts: " + broadcasts);

                    if (persistenceEnabled && databaseWriter != null) {
                        System.out.println("DB queue depth: " + databaseWriter.getQueueDepth());
                        System.out.println("DB writes: " + databaseWriter.getWrittenCount());
                    }

                    System.out.println("Messages failed: " + failed);
                    System.out.printf("Average processing time: %.3f ms%n", avgTime);

                    if (processed > 0) {
                        double successRate = (processed * 100.0) / (processed + failed);
                        System.out.printf("Success rate: %.2f%%%n", successRate);
                        double throughput = processed / 10.0; // per reporting interval
                        System.out.printf("Throughput: %.2f msg/s%n", throughput);
                    }
                    System.out.println("─".repeat(60) + "\n");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.setName("consumer-monitor");
        monitorThread.start();
    }

    public void shutdown() {
        System.out.println("\nShutting down consumers...");

        for (MessageConsumer consumer : consumers) {
            consumer.shutdown();
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        // Shutdown database writer (flushes pending writes)
        if (persistenceEnabled && databaseWriter != null) {
            databaseWriter.shutdown();
        }

        // Shutdown database connection pool
        if (persistenceEnabled) {
            DatabaseConfig.shutdown();
        }

        ConsumerConfig.shutdown();
        redisPublisher.shutdown();

        System.out.println("\n" + "═".repeat(60));
        System.out.println("FINAL STATISTICS");
        System.out.println("═".repeat(60));
        System.out.println("Total messages processed: " + processor.getProcessedCount());
        System.out.println("Total Redis broadcasts: " + redisPublisher.getPublishedCount());

        if (persistenceEnabled && repository != null) {
            System.out.println("Total DB inserts: " + repository.getInsertedCount());
            System.out.println("DB insert failures: " + repository.getFailedCount());
        }

        System.out.println("Total messages failed: " + processor.getFailedCount());
        System.out.printf("Average processing time: %.3f ms%n", processor.getAverageProcessingTime());
        System.out.println("═".repeat(60));
    }

    public static void main(String[] args) {
        // Configuration
        int consumersPerRoom = 5;
        int roomCount = ConsumerConfig.ROOM_COUNT;
        boolean persistenceEnabled = Boolean.parseBoolean(
                System.getenv().getOrDefault("ENABLE_PERSISTENCE", "true")
        );

        if (args.length > 0) {
            try {
                consumersPerRoom = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid consumer count, using default: " + consumersPerRoom);
            }
        }

        if (args.length > 1) {
            persistenceEnabled = Boolean.parseBoolean(args[1]);
        }

        ConsumerApplication app = new ConsumerApplication(consumersPerRoom, roomCount, persistenceEnabled);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nReceived shutdown signal...");
            app.shutdown();
        }));

        app.start();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
        }
    }
}