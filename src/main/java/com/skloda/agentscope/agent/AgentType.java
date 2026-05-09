package com.skloda.agentscope.agent;

public enum AgentType {
    SINGLE(true),
    SEQUENTIAL(false),
    PARALLEL(false),
    ROUTING(false),
    HANDOFFS(false),
    DEBATE(false),
    LOOP(false),
    STATE_GRAPH(false),
    MSG_HUB(false),
    SUBAGENT_SEQ(false),
    SUBAGENT_PAR(false);

    private final boolean isDefault;

    AgentType(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
