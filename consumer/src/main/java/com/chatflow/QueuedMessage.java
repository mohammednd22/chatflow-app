package com.chatflow;

public class QueuedMessage {
    private ChatMessage chatMessage;
    private String roomId;
    private long receivedTimestamp;

    public QueuedMessage() {}

    public QueuedMessage(ChatMessage chatMessage, String roomId) {
        this.chatMessage = chatMessage;
        this.roomId = roomId;
        this.receivedTimestamp = System.currentTimeMillis();
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }

    public void setChatMessage(ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public long getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(long receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }
}