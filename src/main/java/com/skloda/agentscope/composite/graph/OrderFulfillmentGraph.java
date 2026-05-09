package com.skloda.agentscope.composite.graph;

import com.skloda.agentscope.agent.StateConfig;
import com.skloda.agentscope.agent.StateTransition;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom state machine for order fulfillment workflow.
 * States with agents: LLM decides which transition to take.
 * States without agents: user input triggers deterministic transitions.
 */
public class OrderFulfillmentGraph {

    private final List<StateConfig> states;
    private final Map<String, ReActAgent> stateAgents;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();
    private String currentState;

    public OrderFulfillmentGraph(List<StateConfig> states, Map<String, ReActAgent> stateAgents) {
        this.states = states;
        this.stateAgents = stateAgents;
        this.currentState = states.isEmpty() ? null : states.get(0).getName();
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    public Mono<Msg> execute(Msg userMsg) {
        return processState(userMsg, currentState);
    }

    private Mono<Msg> processState(Msg userMsg, String stateName) {
        StateConfig state = findState(stateName);
        if (state == null) {
            return Mono.just(buildTextMsg("未知状态: " + stateName));
        }

        if (state.getAgent() != null && stateAgents.containsKey(stateName)) {
            ReActAgent agent = stateAgents.get(stateName);
            emit("graph_agent_call", Map.of("state", stateName, "agent", state.getAgent()));

            Msg agentInput = buildAgentInput(stateName, userMsg);
            return agent.call(agentInput)
                    .flatMap(agentOutput -> {
                        String decision = extractDecision(agentOutput);
                        StateTransition matched = findMatchingTransition(state, decision);
                        if (matched == null) {
                            matched = state.getTransitions().get(0);
                        }

                        String fromState = stateName;
                        currentState = matched.getTarget();

                        emit("graph_transition", Map.of(
                                "fromState", fromState,
                                "toState", matched.getTarget(),
                                "trigger", matched.getCondition() != null ? matched.getCondition() : "agent_decision"
                        ));

                        String agentText = extractText(agentOutput);
                        StateConfig nextState = findState(matched.getTarget());
                        if (nextState == null || nextState.getTransitions() == null || nextState.getTransitions().isEmpty()) {
                            return Mono.just(buildTextMsg(agentText));
                        }
                        return processState(userMsg, matched.getTarget());
                    });
        } else {
            String inputText = extractText(userMsg);
            StateTransition matched = findEventTransition(state, inputText);
            if (matched == null) {
                return Mono.just(buildTextMsg(
                        "当前状态: " + stateName + "\n可用操作: " +
                        formatAvailableEvents(state)));
            }

            String fromState = stateName;
            currentState = matched.getTarget();

            emit("graph_transition", Map.of(
                    "fromState", fromState,
                    "toState", matched.getTarget(),
                    "trigger", matched.getEvent()
            ));

            StateConfig nextState = findState(matched.getTarget());
            if (nextState == null || nextState.getTransitions() == null || nextState.getTransitions().isEmpty()) {
                return Mono.just(buildTextMsg("流程已到达终态: " + matched.getTarget()));
            }
            return processState(userMsg, matched.getTarget());
        }
    }

    private Msg buildAgentInput(String stateName, Msg userMsg) {
        String context = "当前订单状态: " + stateName + "\n";
        String userInput = extractText(userMsg);
        context += "用户输入: " + userInput + "\n\n";
        context += "请根据订单状态和用户输入，做出决策。在回复中用【决策:xxx】格式表明你的决策。";
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(context).build())
                .build();
    }

    private String extractDecision(Msg agentOutput) {
        String text = extractText(agentOutput);
        Pattern pattern = Pattern.compile("【决策:(.+?)】");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase();
        }
        return text.toLowerCase();
    }

    private StateTransition findMatchingTransition(StateConfig state, String decision) {
        for (StateTransition t : state.getTransitions()) {
            if (t.getCondition() != null && decision.contains(t.getCondition().toLowerCase())) {
                return t;
            }
        }
        return null;
    }

    private StateTransition findEventTransition(StateConfig state, String input) {
        if (state.getTransitions() == null) return null;
        for (StateTransition t : state.getTransitions()) {
            if (t.getEvent() != null && input.toLowerCase().contains(t.getEvent().toLowerCase())) {
                return t;
            }
        }
        Map<String, String> eventAliases = Map.of(
                "submit", "提交",
                "review", "审核",
                "pay", "支付",
                "ship", "发货",
                "retry", "重试"
        );
        for (StateTransition t : state.getTransitions()) {
            if (t.getEvent() != null) {
                String alias = eventAliases.getOrDefault(t.getEvent(), "");
                if (!alias.isEmpty() && input.contains(alias)) {
                    return t;
                }
            }
        }
        return null;
    }

    private String formatAvailableEvents(StateConfig state) {
        if (state.getTransitions() == null) return "";
        StringBuilder sb = new StringBuilder();
        Map<String, String> labels = Map.of(
                "submit", "提交订单", "review", "审核", "pay", "支付",
                "ship", "发货", "retry", "重试");
        for (StateTransition t : state.getTransitions()) {
            if (t.getEvent() != null) {
                String label = labels.getOrDefault(t.getEvent(), t.getEvent());
                sb.append(label).append("(").append(t.getEvent()).append(") ");
            }
        }
        return sb.toString().trim();
    }

    private StateConfig findState(String name) {
        for (StateConfig s : states) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void reset() {
        currentState = states.isEmpty() ? null : states.get(0).getName();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private Msg buildTextMsg(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}