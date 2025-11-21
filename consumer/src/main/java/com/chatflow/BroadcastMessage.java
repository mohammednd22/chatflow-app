
package com.chatflow;

public class BroadcastMessage {
    private Integer userId;
    private String username;
    private String message;
    private String clientTimestamp;
    private String messageType;
    private String roomId;
    private Long serverTimestamp;

    public BroadcastMessage() {}

    public BroadcastMessage(Integer userId, String username, String message,
                            String clientTimestamp, String messageType,
                            String roomId, Long serverTimestamp) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.clientTimestamp = clientTimestamp;
        this.messageType = messageType;
        this.roomId = roomId;
        this.serverTimestamp = serverTimestamp;
    }

    // Getters and setters
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getClientTimestamp() { return clientTimestamp; }
    public void setClientTimestamp(String clientTimestamp) { this.clientTimestamp = clientTimestamp; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public Long getServerTimestamp() { return serverTimestamp; }
    public void setServerTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; }
}