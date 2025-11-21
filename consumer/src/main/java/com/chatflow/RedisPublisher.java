package com.chatflow;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.atomic.AtomicLong;

public class RedisPublisher {
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    private static final String CHANNEL_PREFIX = "chatroom:";

    private final JedisPool jedisPool;
    private final AtomicLong publishedCount;

    public RedisPublisher() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(200);
        poolConfig.setMaxIdle(50);
        poolConfig.setMinIdle(10);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWaitMillis(5000);

        this.jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
        this.publishedCount = new AtomicLong(0);

        System.out.println("Redis publisher initialized: " + REDIS_HOST + ":" + REDIS_PORT);
    }

    public boolean publishToRoom(String roomId, String messageJson) {
        try (Jedis jedis = jedisPool.getResource()) {
            String channel = CHANNEL_PREFIX + roomId;
            jedis.publish(channel, messageJson);
            publishedCount.incrementAndGet();
            return true;
        } catch (Exception e) {
            System.err.println("Failed to publish to Redis: " + e.getMessage());
            return false;
        }
    }

    public long getPublishedCount() {
        return publishedCount.get();
    }

    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            System.out.println("Redis publisher connection closed");
        }
    }
}
