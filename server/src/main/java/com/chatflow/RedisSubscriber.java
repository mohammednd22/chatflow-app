package com.chatflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisSubscriber {
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    private static final String CHANNEL_PREFIX = "chatroom:";

    private final ConcurrentHashMap<String, Set<WebSocket>> roomConnections;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private Jedis jedis;
    private RoomSubscriber roomSubscriber;

    public RedisSubscriber(ConcurrentHashMap<String, Set<WebSocket>> roomConnections) {
        this.roomConnections = roomConnections;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void start() {
        executorService.submit(() -> {
            try {
                jedis = new Jedis(REDIS_HOST, REDIS_PORT);
                roomSubscriber = new RoomSubscriber();

                // Subscribe to all room channels (pattern subscription)
                String pattern = CHANNEL_PREFIX + "*";
                System.out.println("Redis subscriber listening on pattern: " + pattern);
                jedis.psubscribe(roomSubscriber, pattern);

            } catch (Exception e) {
                System.err.println("Redis subscriber error: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        if (roomSubscriber != null) {
            roomSubscriber.punsubscribe();
        }
        if (jedis != null) {
            jedis.close();
        }
        executorService.shutdown();
        System.out.println("Redis subscriber shut down");
    }

    private class RoomSubscriber extends JedisPubSub {
        @Override
        public void onPMessage(String pattern, String channel, String message) {
            try {
                // Extract room ID from channel
                String roomId = channel.substring(CHANNEL_PREFIX.length());

                // Get all connections for this room
                Set<WebSocket> connections = roomConnections.get(roomId);

                if (connections != null && !connections.isEmpty()) {
                    // Broadcast to all connected clients in the room
                    int broadcastCount = 0;
                    for (WebSocket conn : connections) {
                        if (conn.isOpen()) {
                            conn.send(message);
                            broadcastCount++;
                        }
                    }

                    if (broadcastCount > 0) {
                        // System.out.println("Broadcasted to " + broadcastCount + " clients in room " + roomId);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error broadcasting message: " + e.getMessage());
            }
        }

        @Override
        public void onPSubscribe(String pattern, int subscribedChannels) {
            System.out.println("Subscribed to pattern: " + pattern);
        }
    }
}