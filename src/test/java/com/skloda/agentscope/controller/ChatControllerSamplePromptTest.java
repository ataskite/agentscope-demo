package com.skloda.agentscope.controller;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.SamplePrompt;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerSamplePromptTest {

    @Test
    void listAgentsReturnsSamplePromptPreviewsOnly() {
        ChatController controller = controllerWithPrompt(
                "请审查下面这份合同，提取合同编号、双方主体、付款条款、违约责任、终止条款和主要风险点。"
                        + "完成审查后，请发起人工审批；审批通过后调用报告生成工具生成合同审查报告。"
                        + "甲方：蓝海科技有限公司。乙方：星河软件服务有限公司。");

        List<AgentConfig> agents = controller.listAgents();

        String promptPreview = agents.get(0).getSamplePrompts().get(0).getPrompt();
        assertTrue(promptPreview.endsWith("..."));
        assertFalse(promptPreview.contains("星河软件服务有限公司"));
    }

    @Test
    void getSamplePromptReturnsFullPromptByIndex() {
        String fullPrompt = "请生成合同审查报告。甲方：蓝海科技有限公司。乙方：星河软件服务有限公司。";
        ChatController controller = controllerWithPrompt(fullPrompt);

        ResponseEntity<?> response = controller.getSamplePrompt("contract-review-workflow", 0);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(fullPrompt, body.get("prompt"));
        assertEquals("触发人工审批", body.get("expectedBehavior"));
    }

    @Test
    void getSamplePromptReturnsNotFoundForMissingIndex() {
        ChatController controller = controllerWithPrompt("短提示");

        ResponseEntity<?> response = controller.getSamplePrompt("contract-review-workflow", 1);

        assertEquals(404, response.getStatusCode().value());
    }

    private static ChatController controllerWithPrompt(String prompt) {
        ChatController controller = new ChatController(null, null, null, null, null);
        ReflectionTestUtils.setField(controller, "agentConfigService", new StubAgentConfigService(prompt));
        return controller;
    }

    private static class StubAgentConfigService extends AgentConfigService {
        private final AgentConfig config;

        StubAgentConfigService(String prompt) {
            config = new AgentConfig();
            config.setAgentId("contract-review-workflow");
            config.setName("合同审查工作流");
            config.setSamplePrompts(List.of(new SamplePrompt(prompt, "触发人工审批")));
        }

        @Override
        public List<AgentConfig> getAllAgents() {
            return List.of(config);
        }

        @Override
        public Optional<AgentConfig> findAgentConfig(String agentId) {
            if (config.getAgentId().equals(agentId)) {
                return Optional.of(config);
            }
            return Optional.empty();
        }
    }
}
