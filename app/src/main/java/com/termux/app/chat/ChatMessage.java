package com.termux.app.chat;

/**
 * Data model representing a single chat message in an SSH session.
 */
public class ChatMessage {

    public static final int TYPE_SENT = 0;
    public static final int TYPE_RECEIVED = 1;
    public static final int TYPE_SYSTEM = 2;

    private long id;
    private String sessionId;
    private String content;
    private int type;
    private long timestamp;
    private String remoteHost;

    /** No-arg constructor. */
    public ChatMessage() {
    }

    /** Full constructor. */
    public ChatMessage(long id, String sessionId, String content, int type, long timestamp, String remoteHost) {
        this.id = id;
        this.sessionId = sessionId;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
        this.remoteHost = remoteHost;
    }

    // --- Factory methods ---

    public static ChatMessage createSentMessage(String sessionId, String content, String remoteHost) {
        ChatMessage msg = new ChatMessage();
        msg.sessionId = sessionId;
        msg.content = content;
        msg.type = TYPE_SENT;
        msg.timestamp = System.currentTimeMillis();
        msg.remoteHost = remoteHost;
        return msg;
    }

    public static ChatMessage createReceivedMessage(String sessionId, String content, String remoteHost) {
        ChatMessage msg = new ChatMessage();
        msg.sessionId = sessionId;
        msg.content = content;
        msg.type = TYPE_RECEIVED;
        msg.timestamp = System.currentTimeMillis();
        msg.remoteHost = remoteHost;
        return msg;
    }

    public static ChatMessage createSystemMessage(String sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.sessionId = sessionId;
        msg.content = content;
        msg.type = TYPE_SYSTEM;
        msg.timestamp = System.currentTimeMillis();
        msg.remoteHost = null;
        return msg;
    }

    // --- Getters and Setters ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }
}
