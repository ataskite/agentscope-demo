package com.skloda.agentscope.composite.graph;

import io.agentscope.core.state.State;

/**
 * P3-7: persisted marker for {@link OrderFulfillmentGraph}'s current state.
 * Stored under the agent's session in the {@code AgentStateStore} so the graph no
 * longer resets to the initial state on every request (each request previously built
 * a fresh graph instance whose {@code currentState} field restarted the machine).
 * Plain POJO with a public no-arg constructor for JSON serialization.
 */
public class GraphCurrentState implements State {

    private String stateName;

    public GraphCurrentState() {
    }

    public GraphCurrentState(String stateName) {
        this.stateName = stateName;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }
}
