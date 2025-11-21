package com.chatflow;

public class MessageMetadata {
    private final ChatMessage message;
    private final long sentTimestamp;
    private long receivedTimestamp;
    private int retryCount;

    public MessageMetadata(ChatMessage message) {
        this.message = message;
        this.sentTimestamp = System.currentTimeMillis();
        this.retryCount = 0;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public long getSentTimestamp() {
        return sentTimestamp;
    }

    public long getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(long receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public long getLatency() {
        return receivedTimestamp - sentTimestamp;
    }
}