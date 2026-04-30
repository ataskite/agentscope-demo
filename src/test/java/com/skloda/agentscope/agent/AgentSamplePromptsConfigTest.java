package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSamplePromptsConfigTest {

    @Test
    void everyAgentDefinesNonEmptySamplePrompts() {
        Yaml yaml = new Yaml(new Constructor(AgentConfigService.AgentsWrapper.class, new LoaderOptions()));

        try (InputStream inputStream = getClass().getResourceAsStream("/config/agents.yml")) {
            assertNotNull(inputStream, "agents.yml should exist");
            AgentConfigService.AgentsWrapper wrapper = yaml.load(inputStream);

            // Only check multi-agent showcase agents for sample prompts
            for (AgentConfig agent : wrapper.getAgents()) {
                String agentId = agent.getAgentId();

                // Skip non-showcase agents
                if (!agentId.equals("customer-service") && !agentId.equals("doc-analysis-pipeline")) {
                    continue;
                }

                assertFalse(agent.getSamplePrompts().isEmpty(),
                        () -> agentId + " should define sample prompts");

                for (SamplePrompt samplePrompt : agent.getSamplePrompts()) {
                    assertNotNull(samplePrompt.getPrompt(),
                            () -> agentId + " sample prompt text should not be null");
                    assertFalse(samplePrompt.getPrompt().isBlank(),
                            () -> agentId + " sample prompt text should not be blank");
                    assertNotNull(samplePrompt.getExpectedBehavior(),
                            () -> agentId + " sample prompt expected behavior should not be null");
                    assertFalse(samplePrompt.getExpectedBehavior().isBlank(),
                            () -> agentId + " sample prompt expected behavior should not be blank");
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to load agents.yml", e);
        }
    }

    @Test
    void controlledWorkflowAgentsDeclareApprovalAndStructuredOutputSettings() {
        Yaml yaml = new Yaml(new Constructor(AgentConfigService.AgentsWrapper.class, new LoaderOptions()));

        try (InputStream inputStream = getClass().getResourceAsStream("/config/agents.yml")) {
            assertNotNull(inputStream, "agents.yml should exist");
            AgentConfigService.AgentsWrapper wrapper = yaml.load(inputStream);
            Map<String, AgentConfig> agents = wrapper.getAgents().stream()
                    .collect(Collectors.toMap(AgentConfig::getAgentId, Function.identity()));

            AgentConfig bankInvoice = agents.get("bank-invoice");
            assertNotNull(bankInvoice, "bank-invoice should exist");
            assertTrue(bankInvoice.getApprovalTools().contains("generate_bank_invoice"),
                    "bank-invoice should require human approval before generating files");

            assertEquals("com.skloda.agentscope.schema.InvoiceData",
                    agents.get("invoice-extractor").getStructuredOutputClass());
            assertEquals("com.skloda.agentscope.schema.IDCardData",
                    agents.get("idcard-extractor").getStructuredOutputClass());
            assertEquals("PROMPT", agents.get("invoice-extractor").getStructuredOutputReminder());
            assertEquals("PROMPT", agents.get("idcard-extractor").getStructuredOutputReminder());
        } catch (Exception e) {
            throw new AssertionError("Failed to load agents.yml", e);
        }
    }
}
