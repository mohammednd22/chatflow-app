
package com.chatflow;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectionPool {
    //private static final String SERVER_URL = "ws://chatflow-alb-1833520762.us-west-2.elb.amazonaws.com/chat/";
    private static final String SERVER_URL = "ws://34.211.84.91:8080/chat/";
    private static final int MAX_CONNECTIONS_PER_ROOM = 10;
    private static final long PING_INTERVAL_MS = 30000; // 30 seconds

    private final ConcurrentHashMap<Integer, BlockingQueue<PooledConnection>> roomPools;
    private final ScheduledExecutorService heartbeatExecutor;
    private final PerformanceMetrics metrics;

    public ConnectionPool(PerformanceMetrics metrics) {
        this.roomPools = new ConcurrentHashMap<>();
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);
        this.metrics = metrics;
        startHeartbeat();
    }

    public PooledConnection getConnection(Integer roomId) throws InterruptedException {
        BlockingQueue<PooledConnection> pool = roomPools.computeIfAbsent(
                roomId,
                k -> new LinkedBlockingQueue<>()
        );

        PooledConnection conn = pool.poll();

        if (conn == null || !conn.isConnected()) {
            // Create new connection
            conn = createConnection(roomId);
        }

        return conn;
    }

    public void returnConnection(Integer roomId, PooledConnection connection) {
        if (connection != null && connection.isConnected()) {
            BlockingQueue<PooledConnection> pool = roomPools.get(roomId);
            if (pool != null && pool.size() < MAX_CONNECTIONS_PER_ROOM) {
                pool.offer(connection);
            } else {
                connection.close();
            }
        }
    }

    private PooledConnection createConnection(Integer roomId) throws InterruptedException {
        try {
            URI serverUri = new URI(SERVER_URL + roomId);
            CountDownLatch connectionLatch = new CountDownLatch(1);
            AtomicBoolean connectionSuccess = new AtomicBoolean(false);

            PooledConnection pooledConn = new PooledConnection(
                    serverUri,
                    roomId,
                    connectionLatch,
                    connectionSuccess,
                    metrics
            );

            pooledConn.connect();

            if (connectionLatch.await(5, TimeUnit.SECONDS) && connectionSuccess.get()) {
                metrics.recordConnection();
                return pooledConn;
            } else {
                throw new RuntimeException("Connection timeout for room " + roomId);
            }
        } catch (Exception e) {
            throw new InterruptedException("Failed to create connection: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            for (BlockingQueue<PooledConnection> pool : roomPools.values()) {
                for (PooledConnection conn : pool) {
                    if (conn.isConnected()) {
                        conn.sendPing();
                    }
                }
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        heartbeatExecutor.shutdown();
        for (BlockingQueue<PooledConnection> pool : roomPools.values()) {
            for (PooledConnection conn : pool) {
                conn.close();
            }
        }
        roomPools.clear();
    }
}