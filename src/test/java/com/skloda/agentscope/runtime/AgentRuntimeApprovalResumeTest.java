package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRuntimeApprovalResumeTest {

    @Test
    void approvalResumeWithoutUserMessageCallsAgentWithoutSyntheticInput() {
        ReActAgent agent = mock(ReActAgent.class);
        when(agent.getName()).thenReturn("合同审查工作流");
        when(agent.call()).thenReturn(Mono.just(Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .textContent("报告已生成")
                .build()));

        AgentRuntime runtime = new AgentRuntime(agent, new ObservabilityHook());

        List<Map<String, Object>> events = runtime.stream(null, true)
                .takeUntil(event -> "done".equals(event.get("type")))
                .collectList()
                .block();

        verify(agent).call();
        verify(agent, never()).call(org.mockito.ArgumentMatchers.any(Msg.class));
        assertEquals("text", events.get(0).get("type"));
        assertEquals("done", events.get(events.size() - 1).get("type"));
    }

    @Test
    void approvalResumeFormatsToolResultAndDoesNotWaitForAnotherModelTurn() {
        ReActAgent agent = mock(ReActAgent.class);
        List<Hook> hooks = new ArrayList<>();
        when(agent.getName()).thenReturn("合同审查工作流");
        when(agent.getHooks()).thenReturn(hooks);
        when(agent.call()).thenReturn(Mono.just(Msg.builder()
                .name("合同审查工作流")
                .role(MsgRole.TOOL)
                .content(List.of(ToolResultBlock.of("call-1", "generate_contract_review_report",
                        TextBlock.builder()
                                .text("""
                                        {"success":true,"fileName":"contract_review.md","downloadUrl":"/chat/download?fileId=contract_review.md","riskLevel":"HIGH","summary":"报告已生成"}
                                        """)
                                .build())))
                .build()));

        AgentRuntime runtime = new AgentRuntime(agent, new ObservabilityHook());

        List<Map<String, Object>> events = runtime.stream(null, true)
                .takeUntil(event -> "done".equals(event.get("type")))
                .collectList()
                .block();

        assertEquals(0, hooks.size(), "temporary resume hook should be removed after completion");
        Map<String, Object> textEvent = events.stream()
                .filter(event -> "text".equals(event.get("type")))
                .findFirst()
                .orElseThrow();
        String content = textEvent.get("content").toString();
        assertTrue(content.contains("报告已生成"));
        assertTrue(content.contains("[下载合同审查报告](/chat/download?fileId=contract_review.md)"));
        assertEquals("done", events.get(events.size() - 1).get("type"));
    }

    @Test
    void approvalResumeFormatsToolResultWhenJsonIsWrappedAsStringLiteral() {
        ReActAgent agent = mock(ReActAgent.class);
        List<Hook> hooks = new ArrayList<>();
        when(agent.getName()).thenReturn("合同审查工作流");
        when(agent.getHooks()).thenReturn(hooks);
        when(agent.call()).thenReturn(Mono.just(Msg.builder()
                .name("合同审查工作流")
                .role(MsgRole.TOOL)
                .content(List.of(ToolResultBlock.of("call-1", "generate_contract_review_report",
                        TextBlock.builder()
                                .text("\"{\\\"success\\\":true,\\\"fileName\\\":\\\"contract_review.md\\\",\\\"downloadUrl\\\":\\\"/chat/download?fileId=contract_review.md\\\",\\\"riskLevel\\\":\\\"MEDIUM\\\",\\\"summary\\\":\\\"报告已生成\\\"}\"")
                                .build())))
                .build()));

        AgentRuntime runtime = new AgentRuntime(agent, new ObservabilityHook());

        List<Map<String, Object>> events = runtime.stream(null, true)
                .takeUntil(event -> "done".equals(event.get("type")))
                .collectList()
                .block();

        String content = events.stream()
                .filter(event -> "text".equals(event.get("type")))
                .findFirst()
                .orElseThrow()
                .get("content")
                .toString();
        assertTrue(content.contains("[下载合同审查报告](/chat/download?fileId=contract_review.md)"));
        assertTrue(content.contains("风险等级：MEDIUM"));
    }
}
