package com.chatflow;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RedisPublisherOptimized {
    private static final String REDIS_HOST = System.getenv("REDIS_HOST");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv("REDIS_PORT"));
    private static final String CHANNEL_PREFIX = "chatroom:";

    private final JedisPool jedisPool;
    private final AtomicLong publishedCount;
    private final BlockingQueue<PublishTask> publishQueue;
    private final AtomicBoolean running;
    private final Thread publisherThread;

    private static class PublishTask {
        String roomId;
        String messageJson;

        PublishTask(String roomId, String messageJson) {
            this.roomId = roomId;
            this.messageJson = messageJson;
        }
    }

    public RedisPublisherOptimized() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(200);
        poolConfig.setMaxIdle(50);
        poolConfig.setMinIdle(10);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWaitMillis(5000);

        this.jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
        this.publishedCount = new AtomicLong(0);
        this.publishQueue = new LinkedBlockingQueue<>(10000);
        this.running = new AtomicBoolean(true);

        // Start background publisher thread
        this.publisherThread = new Thread(this::runPublisher);
        this.publisherThread.setName("redis-publisher");
        this.publisherThread.setDaemon(true);
        this.publisherThread.start();

        System.out.println("Redis publisher (optimized) initialized: " + REDIS_HOST + ":" + REDIS_PORT);
    }

    public boolean publishToRoom(String roomId, String messageJson) {
        try {
            publishQueue.offer(new PublishTask(roomId, messageJson));
            return true;
        } catch (Exception e) {
            System.err.println("Failed to queue for Redis: " + e.getMessage());
            return false;
        }
    }

    private void runPublisher() {
        while (running.get()) {
            try (Jedis jedis = jedisPool.getResource()) {
                Pipeline pipeline = jedis.pipelined();
                int batchSize = 0;

                // Batch up to 100 publishes at once
                while (batchSize < 100) {
                    PublishTask task = publishQueue.poll(10, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (task == null) break;

                    String channel = CHANNEL_PREFIX + task.roomId;
                    pipeline.publish(channel, task.messageJson);
                    batchSize++;
                }

                if (batchSize > 0) {
                    pipeline.sync(); // Execute all at once
                    publishedCount.addAndGet(batchSize);
                }

            } catch (Exception e) {
                System.err.println("Redis publisher error: " + e.getMessage());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public long getPublishedCount() {
        return publishedCount.get();
    }

    public void shutdown() {
        running.set(false);
        try {
            publisherThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            System.out.println("Redis publisher connection closed");
        }
    }
}