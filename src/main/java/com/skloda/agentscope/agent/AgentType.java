package com.skloda.agentscope.agent;

public enum AgentType {
    SINGLE(true),
    SEQUENTIAL(false),
    PARALLEL(false),
    ROUTING(false),
    HANDOFFS(false),
    DEBATE(false);

    private final boolean isDefault;

    AgentType(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
