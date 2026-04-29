package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
