package com.skloda.agentscope.agent;

public enum TriggerType {
    INTENT("Intent-based trigger"),
    EXPLICIT("Explicit user request trigger"),
    INCAPABLE("Incapability trigger");

    private final String description;

    TriggerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
