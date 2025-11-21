package com.chatflow;

public class ChatResponse {
    private Integer userId;
    private String username;
    private String message;
    private String clientTimestamp;
    private String messageType;
    private String status;
    private String serverTimestamp;

    public ChatResponse(Integer userId, String username, String message,
                        String clientTimestamp, String messageType,
                        String status, String serverTimestamp) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.clientTimestamp = clientTimestamp;
        this.messageType = messageType;
        this.status = status;
        this.serverTimestamp = serverTimestamp;
    }

    // Getters for Jackson serialization
    public Integer getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return message;
    }

    public String getClientTimestamp() {
        return clientTimestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getStatus() {
        return status;
    }

    public String getServerTimestamp() {
        return serverTimestamp;
    }
}
