package com.skloda.agentscope.model;

import java.time.Instant;
import java.util.UUID;

/**
 * UI-facing chat transcript entry.
 */
public class ChatMessage {

    private String id;
    private String agentId;
    private String role;
    private String content;
    private String thinkingContent;
    private String createdAt;

    public ChatMessage() {
    }

    public ChatMessage(ChatMessage other) {
        this.id = other.id;
        this.agentId = other.agentId;
        this.role = other.role;
        this.content = other.content;
        this.thinkingContent = other.thinkingContent;
        this.createdAt = other.createdAt;
    }

    public static ChatMessage user(String content) {
        return of("user", content);
    }

    public static ChatMessage assistant(String content) {
        return of("assistant", content);
    }

    public static ChatMessage assistant(String content, String thinkingContent) {
        ChatMessage message = assistant(content);
        message.setThinkingContent(thinkingContent);
        return message;
    }

    public static ChatMessage of(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID().toString());
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(Instant.now().toString());
        return message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getThinkingContent() {
        return thinkingContent;
    }

    public void setThinkingContent(String thinkingContent) {
        this.thinkingContent = thinkingContent;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
