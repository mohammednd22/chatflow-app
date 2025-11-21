package com.chatflow;

public class ErrorResponse {
    private String error;
    private String message;
    private String timestamp;

    public ErrorResponse(String error, String message, String timestamp) {
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
    }

    // Getters for Jackson serialization
    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
