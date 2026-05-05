package com.skloda.agentscope.hook;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalHookTest {

    @Test
    void pendingToolCallsExposeStructuredInputForReadableReview() {
        ApprovalHook hook = new ApprovalHook(false, List.of("generate_contract_review_report"));
        Agent agent = new StubAgent();

        Msg reasoning = Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .content(ToolUseBlock.builder()
                        .id("call-1")
                        .name("generate_contract_review_report")
                        .input(Map.of(
                                "contractTitle", "软件开发服务合同",
                                "partyA", "蓝海科技有限公司",
                                "overallRiskLevel", "HIGH"))
                        .build())
                .build();

        hook.onEvent(new PostReasoningEvent(agent, "qwen-plus", null, reasoning)).block();

        Map<String, Object> pendingCall = hook.getPendingToolCallsForSse().get(0);
        assertTrue(hook.isApprovalTriggered());
        assertEquals("generate_contract_review_report", pendingCall.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputParams = (Map<String, Object>) pendingCall.get("inputParams");
        assertEquals("软件开发服务合同", inputParams.get("contractTitle"));
        assertEquals("蓝海科技有限公司", inputParams.get("partyA"));
        assertEquals("HIGH", inputParams.get("overallRiskLevel"));
    }

    private static class StubAgent implements Agent {
        @Override
        public String getAgentId() {
            return "contract-review-workflow";
        }

        @Override
        public String getName() {
            return "合同审查工作流";
        }

        @Override
        public void interrupt() {
        }

        @Override
        public void interrupt(Msg msg) {
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs) {
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, Class<?> structuredOutputClass) {
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, com.fasterxml.jackson.databind.JsonNode schema) {
            return Mono.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options) {
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, Class<?> structuredModel) {
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, com.fasterxml.jackson.databind.JsonNode schema) {
            return Flux.empty();
        }

        @Override
        public Mono<Void> observe(Msg msg) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> observe(List<Msg> msgs) {
            return Mono.empty();
        }
    }
}
