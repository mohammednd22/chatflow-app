package com.chatflow;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PooledConnection extends WebSocketClient {
    private final Integer roomId;
    private final CountDownLatch connectionLatch;
    private final AtomicBoolean connectionSuccess;
    private final PerformanceMetrics metrics;
    private final BlockingQueue<String> responseQueue;
    private final AtomicBoolean isHealthy;
    private volatile long lastActivity;

    public PooledConnection(URI serverUri, Integer roomId,
                            CountDownLatch connectionLatch,
                            AtomicBoolean connectionSuccess,
                            PerformanceMetrics metrics) {
        super(serverUri);
        this.roomId = roomId;
        this.connectionLatch = connectionLatch;
        this.connectionSuccess = connectionSuccess;
        this.metrics = metrics;
        this.responseQueue = new LinkedBlockingQueue<>();
        this.isHealthy = new AtomicBoolean(true);
        this.lastActivity = System.currentTimeMillis();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        connectionSuccess.set(true);
        connectionLatch.countDown();
        lastActivity = System.currentTimeMillis();
    }

    @Override
    public void onMessage(String message) {
        responseQueue.offer(message);
        lastActivity = System.currentTimeMillis();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isHealthy.set(false);
        if (remote) {
            System.out.println("Connection closed by server for room " + roomId +
                    ": " + code + " - " + reason);
        }
    }

    @Override
    public void onError(Exception ex) {
        isHealthy.set(false);
        connectionLatch.countDown();
        System.err.println("WebSocket error for room " + roomId + ": " + ex.getMessage());
    }

    @Override
    public void onWebsocketPong(org.java_websocket.WebSocket conn, Framedata f) {
        lastActivity = System.currentTimeMillis();
    }

    public boolean isConnected() {
        return isOpen() && isHealthy.get();
    }

    public String waitForResponse(long timeoutMs) throws InterruptedException {
        return responseQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void sendPing() {
        if (isConnected()) {
            try {
                sendPing();
                lastActivity = System.currentTimeMillis();
            } catch (Exception e) {
                isHealthy.set(false);
            }
        }
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public Integer getRoomId() {
        return roomId;
    }
}