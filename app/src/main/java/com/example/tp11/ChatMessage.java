package com.example.tp11;

/**
 * Model representing a chat message in the socket chat system.
 */
public class ChatMessage {
    public enum SenderType {
        SENT,
        RECEIVED,
        SYSTEM
    }

    private final String message;
    private final SenderType senderType;
    private final String timestamp;

    public ChatMessage(String message, SenderType senderType, String timestamp) {
        this.message = message;
        this.senderType = senderType;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public SenderType getSenderType() {
        return senderType;
    }

    public String getTimestamp() {
        return timestamp;
    }
}