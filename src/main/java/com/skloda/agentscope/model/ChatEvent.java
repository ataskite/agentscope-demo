package com.skloda.agentscope.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * Unified SSE event payload sent to the frontend.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEvent {

    private final String type;
    private String message;
    private String content;

    private ChatEvent(String type) {
        this.type = type;
    }

    /**
     * "done" event — signals stream end.
     */
    public static ChatEvent done() {
        return new ChatEvent("done");
    }

    /**
     * "error" event with a human-readable message.
     */
    public static ChatEvent error(String message) {
        ChatEvent event = new ChatEvent("error");
        event.message = message;
        return event;
    }

    // --- Getters ---

}
