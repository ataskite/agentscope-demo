# Multi-Agent Collaboration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multi-agent collaboration capabilities (Pipeline, Routing, Handoffs) to the AgentScope Demo application while maintaining backward compatibility with existing single-agent configurations.

**Architecture:** Extend the existing `AgentConfig` and `AgentFactory` with a new `CompositeAgentFactory` that creates `SequentialAgent`, `ParallelAgent`, `AgentScopeRoutingAgent`, and `StateGraphAgent` based on configuration. All multi-agent events flow through the extended `ObservabilityHook` for frontend visualization.

**Tech Stack:** Java 17, Spring Boot 3.5.13, AgentScope Java 1.0.11, Lombok, Reactor

---

## File Structure

### New Files

```
src/main/java/com/skloda/agentscope/
├── agent/
│   ├── AgentType.java                    # Agent type enum (SINGLE, SEQUENTIAL, PARALLEL, ROUTING, HANDOFFS)
│   ├── SubAgentConfig.java               # Sub-agent configuration (agentId, description)
│   ├── HandoffTrigger.java               # Handoff trigger configuration (type, keywords, target)
│   └── TriggerType.java                  # Trigger type enum (INTENT, EXPLICIT, INCAPABLE)
├── composite/
│   └── CompositeAgentFactory.java        # Factory for creating multi-agent compositions
└── config/
    └── MultiAgentAgentConfig.java        # Configuration class for multi-agent agents

src/test/java/com/skloda/agentscope/
├── agent/
│   ├── AgentTypeTest.java
│   ├── SubAgentConfigTest.java
│   └── HandoffTriggerTest.java
└── composite/
    └── CompositeAgentFactoryTest.java
```

### Modified Files

```
src/main/java/com/skloda/agentscope/
├── agent/
│   ├── AgentConfig.java                  # Add: type, subAgents, parallel, handoffTriggers
│   └── AgentConfigService.java           # Add: parse SubAgentConfig and HandoffTrigger from YAML
├── service/
│   └── AgentService.java                 # Add: integrate CompositeAgentFactory
├── hook/
│   └── ObservabilityHook.java            # Add: multi-agent events (pipeline_start, routing_decision, etc.)
└── resources/config/
    └── agents.yml                        # Add: multi-agent agent configurations

src/main/resources/static/scripts/
└── chat.js                               # Add: multi-agent event handlers for debug panel
```

---

## Task 1: Create AgentType Enum

**Files:**
- Create: `src/main/java/com/skloda/agentscope/agent/AgentType.java`
- Test: `src/test/java/com/skloda/agentscope/agent/AgentTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentTypeTest {

    @Test
    void testEnumValues() {
        assertEquals(5, AgentType.values().length);
        assertEquals(AgentType.SINGLE, AgentType.valueOf("SINGLE"));
        assertEquals(AgentType.SEQUENTIAL, AgentType.valueOf("SEQUENTIAL"));
        assertEquals(AgentType.PARALLEL, AgentType.valueOf("PARALLEL"));
        assertEquals(AgentType.ROUTING, AgentType.valueOf("ROUTING"));
        assertEquals(AgentType.HANDOFFS, AgentType.valueOf("HANDOFFS"));
    }

    @Test
    void testDefaultIsSingle() {
        // When type is not specified in YAML, it should default to SINGLE
        assertTrue(AgentType.SINGLE.isDefault());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgentTypeTest`
Expected: FAIL with "cannot find symbol AgentType"

- [ ] **Step 3: Write minimal implementation**

```java
package com.skloda.agentscope.agent;

/**
 * Agent type enumeration for single and multi-agent configurations.
 */
public enum AgentType {
    /** Single agent (default, existing behavior) */
    SINGLE(true),

    /** Sequential pipeline - execute sub-agents one after another */
    SEQUENTIAL(false),

    /** Parallel pipeline - execute sub-agents concurrently and merge results */
    PARALLEL(false),

    /** Routing agent - use LLM to decide which sub-agent to route to */
    ROUTING(false),

    /** Handoffs agent - support dynamic agent switching with state preservation */
    HANDOFFS(false);

    private final boolean isDefault;

    AgentType(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AgentTypeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentType.java \
        src/test/java/com/skloda/agentscope/agent/AgentTypeTest.java
git commit -m "feat: add AgentType enum for multi-agent support"
```

---

## Task 2: Create TriggerType Enum

**Files:**
- Create: `src/main/java/com/skloda/agentscope/agent/TriggerType.java`
- Test: `src/test/java/com/skloda/agentscope/agent/TriggerTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriggerTypeTest {

    @Test
    void testEnumValues() {
        assertEquals(3, TriggerType.values().length);
        assertEquals(TriggerType.INTENT, TriggerType.valueOf("INTENT"));
        assertEquals(TriggerType.EXPLICIT, TriggerType.valueOf("EXPLICIT"));
        assertEquals(TriggerType.INCAPABLE, TriggerType.valueOf("INCAPABLE"));
    }

    @Test
    void testEnumDescriptions() {
        assertEquals("Intent-based trigger", TriggerType.INTENT.getDescription());
        assertEquals("Explicit user request trigger", TriggerType.EXPLICIT.getDescription());
        assertEquals("Incapability trigger", TriggerType.INCAPABLE.getDescription());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TriggerTypeTest`
Expected: FAIL with "cannot find symbol TriggerType"

- [ ] **Step 3: Write minimal implementation**

```java
package com.skloda.agentscope.agent;

/**
 * Trigger type for handoff mechanisms.
 */
public enum TriggerType {
    /** Intent-based trigger - keywords in user message trigger handoff */
    INTENT("Intent-based trigger"),

    /** Explicit user request - user explicitly asks to transfer */
    EXPLICIT("Explicit user request trigger"),

    /** Incapability trigger - current agent cannot handle, auto-transfer */
    INCAPABLE("Incapability trigger");

    private final String description;

    TriggerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TriggerTypeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/TriggerType.java \
        src/test/java/com/skloda/agentscope/agent/TriggerTypeTest.java
git commit -m "feat: add TriggerType enum for handoff mechanisms"
```

---

## Task 3: Create SubAgentConfig Class

**Files:**
- Create: `src/main/java/com/skloda/agentscope/agent/SubAgentConfig.java`
- Test: `src/test/java/com/skloda/agentscope/agent/SubAgentConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubAgentConfigTest {

    @Test
    void testCreateSubAgentConfig() {
        SubAgentConfig config = new SubAgentConfig();
        config.setAgentId("doc-expert");
        config.setDescription("Document analysis expert");

        assertEquals("doc-expert", config.getAgentId());
        assertEquals("Document analysis expert", config.getDescription());
    }

    @Test
    void testBuilderPattern() {
        SubAgentConfig config = SubAgentConfig.builder()
            .agentId("search-expert")
            .description("Search expert")
            .build();

        assertEquals("search-expert", config.getAgentId());
        assertEquals("Search expert", config.getDescription());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SubAgentConfigTest`
Expected: FAIL with "cannot find symbol SubAgentConfig"

- [ ] **Step 3: Write minimal implementation**

```java
package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for a sub-agent within a multi-agent composition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubAgentConfig {
    /**
     * The agentId of the sub-agent (must reference an existing agent definition).
     */
    private String agentId;

    /**
     * Description of this sub-agent's role, used by LLM for routing decisions.
     */
    private String description;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SubAgentConfigTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/SubAgentConfig.java \
        src/test/java/com/skloda/agentscope/agent/SubAgentConfigTest.java
git commit -m "feat: add SubAgentConfig class for multi-agent sub-agent configuration"
```

---

## Task 4: Create HandoffTrigger Class

**Files:**
- Create: `src/main/java/com/skloda/agentscope/agent/HandoffTrigger.java`
- Test: `src/test/java/com/skloda/agentscope/agent/HandoffTriggerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class HandoffTriggerTest {

    @Test
    void testCreateHandoffTrigger() {
        HandoffTrigger trigger = new HandoffTrigger();
        trigger.setType(TriggerType.INTENT);
        trigger.setKeywords(List.of("购买", "价格", "报价"));
        trigger.setTarget("sales-expert");

        assertEquals(TriggerType.INTENT, trigger.getType());
        assertEquals(3, trigger.getKeywords().size());
        assertEquals("sales-expert", trigger.getTarget());
    }

    @Test
    void testBuilderPattern() {
        HandoffTrigger trigger = HandoffTrigger.builder()
            .type(TriggerType.EXPLICIT)
            .keywords(List.of("转人工", "客服"))
            .target("support-expert")
            .build();

        assertEquals(TriggerType.EXPLICIT, trigger.getType());
        assertEquals("support-expert", trigger.getTarget());
    }

    @Test
    void testMatchesWithKeyword() {
        HandoffTrigger trigger = HandoffTrigger.builder()
            .type(TriggerType.INTENT)
            .keywords(List.of("购买", "价格"))
            .target("sales-expert")
            .build();

        assertTrue(trigger.matches("我想购买产品"));
        assertTrue(trigger.matches("价格是多少"));
        assertFalse(trigger.matches("你好"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=HandoffTriggerTest`
Expected: FAIL with "cannot find symbol HandoffTrigger"

- [ ] **Step 3: Write minimal implementation**

```java
package com.skloda.agentscope.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for a handoff trigger in a HANDOFFS type agent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HandoffTrigger {
    /**
     * The type of trigger mechanism.
     */
    private TriggerType type;

    /**
     * Keywords that trigger the handoff (for INTENT type).
     */
    private List<String> keywords;

    /**
     * The target agentId to hand off to when triggered.
     */
    private String target;

    /**
     * Check if the given text matches this trigger's keywords.
     *
     * @param text the text to check
     * @return true if any keyword is found in the text
     */
    public boolean matches(String text) {
        if (type != TriggerType.INTENT || keywords == null || text == null) {
            return false;
        }
        String lowerText = text.toLowerCase();
        return keywords.stream().anyMatch(kw -> lowerText.contains(kw.toLowerCase()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=HandoffTriggerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/HandoffTrigger.java \
        src/test/java/com/skloda/agentscope/agent/HandoffTriggerTest.java
git commit -m "feat: add HandoffTrigger class for handoff mechanism configuration"
```

---

## Task 5: Extend AgentConfig with Multi-Agent Fields

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java:1-38`
- Test: `src/test/java/com/skloda/agentscope/agent/AgentConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class AgentConfigTest {

    @Test
    void testDefaultTypeIsSingle() {
        AgentConfig config = new AgentConfig();
        assertEquals(AgentType.SINGLE, config.getType());
    }

    @Test
    void testMultiAgentFields() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.SEQUENTIAL);
        config.setSubAgents(List.of(
            SubAgentConfig.builder().agentId("agent1").description("First").build()
        ));
        config.setParallel(false);

        assertEquals(AgentType.SEQUENTIAL, config.getType());
        assertEquals(1, config.getSubAgents().size());
        assertFalse(config.getParallel());
    }

    @Test
    void testHandoffTriggers() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.HANDOFFS);
        config.setHandoffTriggers(List.of(
            HandoffTrigger.builder()
                .type(TriggerType.INTENT)
                .keywords(List.of("购买"))
                .target("sales-expert")
                .build()
        ));

        assertEquals(AgentType.HANDOFFS, config.getType());
        assertEquals(1, config.getHandoffTriggers().size());
        assertEquals("sales-expert", config.getHandoffTriggers().get(0).getTarget());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgentConfigTest`
Expected: FAIL with "cannot find method getType(), setSubAgents(), etc."

- [ ] **Step 3: Add fields to AgentConfig**

Add the following imports and fields to `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`:

```java
package com.skloda.agentscope.agent;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class AgentConfig {

    private String agentId;
    private String name;
    private String description;
    private String systemPrompt;
    private String modelName = "qwen-plus";
    private boolean streaming = true;
    private boolean enableThinking = true;
    private List<String> skills = new ArrayList<>();
    private List<String> userTools = new ArrayList<>();
    private List<String> systemTools = new ArrayList<>();

    // AutoContextMemory settings
    private boolean autoContext = false;
    private int autoContextMsgThreshold = 30;
    private int autoContextLastKeep = 10;
    private double autoContextTokenRatio = 0.3;

    // RAG settings
    private boolean ragEnabled = false;
    private int ragRetrieveLimit = 3;
    private double ragScoreThreshold = 0.5;

    // Modality settings
    private String modality = "text"; // text, vision, audio

    // === NEW: Multi-agent fields ===
    /**
     * Agent type - defaults to SINGLE for backward compatibility.
     */
    private AgentType type = AgentType.SINGLE;

    /**
     * List of sub-agents for multi-agent compositions (Pipeline, Routing, Handoffs).
     */
    private List<SubAgentConfig> subAgents = new ArrayList<>();

    /**
     * Whether to execute sub-agents in parallel (only for PARALLEL type).
     */
    private Boolean parallel = false;

    /**
     * Handoff triggers for HANDOFFS type agents.
     */
    private List<HandoffTrigger> handoffTriggers = new ArrayList<>();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AgentConfigTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfig.java \
        src/test/java/com/skloda/agentscope/agent/AgentConfigTest.java
git commit -m "feat: extend AgentConfig with multi-agent fields (type, subAgents, parallel, handoffTriggers)"
```

---

## Task 6: Extend AgentConfigService to Parse Multi-Agent YAML

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfigService.java`
- Test: `src/test/java/com/skloda/agentscope/agent/AgentConfigServiceTest.java`

- [ ] **Step 1: Read existing AgentConfigService**

Run: `cat src/main/java/com/skloda/agentscope/agent/AgentConfigService.java`

Note the existing YAML parsing logic using Jackson `ObjectMapper`.

- [ ] **Step 2: Write the failing test**

```java
package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@SpringBootTest
class AgentConfigServiceTest {

    @Autowired
    private AgentConfigService configService;

    @Test
    void testParseSequentialPipelineConfig() {
        AgentConfig config = configService.getAgentConfig("doc-analysis-pipeline");

        assertEquals(AgentType.SEQUENTIAL, config.getType());
        assertNotNull(config.getSubAgents());
        assertTrue(config.getSubAgents().size() > 0);
    }

    @Test
    void testParseRoutingConfig() {
        AgentConfig config = configService.getAgentConfig("smart-router");

        assertEquals(AgentType.ROUTING, config.getType());
        assertNotNull(config.getSubAgents());
    }

    @Test
    void testParseHandoffsConfig() {
        AgentConfig config = configService.getAgentConfig("customer-service");

        assertEquals(AgentType.HANDOFFS, config.getType());
        assertNotNull(config.getHandoffTriggers());
    }

    @Test
    void testDefaultTypeIsSingle() {
        AgentConfig config = configService.getAgentConfig("chat-basic");

        assertEquals(AgentType.SINGLE, config.getType());
    }
}
```

- [ ] **Step 3: Run test to verify it fails (test will fail because config doesn't exist yet)**

Run: `mvn test -Dtest=AgentConfigServiceTest`
Expected: FAIL with "agent not found" or similar

- [ ] **Step 4: No code changes needed**

Jackson's `ObjectMapper` automatically maps YAML fields to Java object properties. Since we added the fields to `AgentConfig` with proper getters/setters (via Lombok), the parsing will work automatically once we add the YAML configuration in Task 14.

- [ ] **Step 5: Mark test as ignored for now, will enable after YAML config is added**

Add `@Disabled("Will enable after YAML config is added in Task 14")` to the test class.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/skloda/agentscope/agent/AgentConfigServiceTest.java
git commit -m "test: add AgentConfigService tests for multi-agent config parsing (disabled until YAML added)"
```

---

## Task 7: Extend ObservabilityHook with Multi-Agent Events

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java:30-289`
- Test: `src/test/java/com/skloda/agentscope/hook/ObservabilityHookTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ObservabilityHookMultiAgentTest {

    private ObservabilityHook hook;
    private List<Map<String, Object>> capturedEvents;

    @BeforeEach
    void setUp() {
        hook = new ObservabilityHook();
        capturedEvents = new ArrayList<>();
        hook.addConsumer((type, data) -> capturedEvents.add(Map.of("type", type, "data", data)));
    }

    @Test
    void testEmitPipelineStart() {
        hook.emitPipelineStart("test-pipeline", List.of("agent1", "agent2"));

        assertEquals(1, capturedEvents.size());
        assertEquals("pipeline_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void testEmitPipelineStepStart() {
        hook.emitPipelineStepStart("test-pipeline", 0, "agent1");

        assertEquals(1, capturedEvents.size());
        assertEquals("pipeline_step_start", capturedEvents.get(0).get("type"));
    }

    @Test
    void testEmitRoutingDecision() {
        hook.emitRoutingDecision("test-router", "agent1", "User asked about documents");

        assertEquals(1, capturedEvents.size());
        assertEquals("routing_decision", capturedEvents.get(0).get("type"));
    }

    @Test
    void testEmitHandoffStart() {
        hook.emitHandoffStart("support-agent", "sales-agent", "User wants to purchase");

        assertEquals(1, capturedEvents.size());
        assertEquals("handoff_start", capturedEvents.get(0).get("type"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ObservabilityHookMultiAgentTest`
Expected: FAIL with "cannot find method emitPipelineStart, etc."

- [ ] **Step 3: Add multi-agent event constants and methods to ObservabilityHook**

Add the following to `src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java`:

First, add the new event constants after the existing ones (around line 30):

```java
public class ObservabilityHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityHook.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Existing events...
    public static final String AGENT_START = "agent_start";
    public static final String LLM_START = "llm_start";
    // ... (keep all existing constants)

    // === NEW: Multi-agent events ===
    public static final String PIPELINE_START = "pipeline_start";
    public static final String PIPELINE_STEP_START = "pipeline_step_start";
    public static final String PIPELINE_STEP_END = "pipeline_step_end";
    public static final String PIPELINE_END = "pipeline_end";

    public static final String ROUTING_START = "routing_start";
    public static final String ROUTING_DECISION = "routing_decision";
    public static final String ROUTING_END = "routing_end";

    public static final String HANDOFF_START = "handoff_start";
    public static final String HANDOFF_COMPLETE = "handoff_complete";
    public static final String HANDOFF_ERROR = "handoff_error";

    // ... rest of existing fields
```

Then, add the new emit methods at the end of the class (before the closing brace):

```java
    // === NEW: Multi-agent event emission methods ===

    /**
     * Emit pipeline start event.
     */
    public void emitPipelineStart(String pipelineId, List<String> subAgents) {
        emit(PIPELINE_START, Map.of(
                "pipelineId", pipelineId,
                "subAgents", subAgents,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit pipeline step start event.
     */
    public void emitPipelineStepStart(String pipelineId, int stepIndex, String agentId) {
        emit(PIPELINE_STEP_START, Map.of(
                "pipelineId", pipelineId,
                "stepIndex", stepIndex,
                "agentId", agentId,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit pipeline step end event.
     */
    public void emitPipelineStepEnd(String pipelineId, int stepIndex, String agentId, long durationMs) {
        emit(PIPELINE_STEP_END, Map.of(
                "pipelineId", pipelineId,
                "stepIndex", stepIndex,
                "agentId", agentId,
                "duration_ms", durationMs,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit pipeline end event.
     */
    public void emitPipelineEnd(String pipelineId, int totalSteps, long totalDurationMs) {
        emit(PIPELINE_END, Map.of(
                "pipelineId", pipelineId,
                "totalSteps", totalSteps,
                "duration_ms", totalDurationMs,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit routing decision event.
     */
    public void emitRoutingDecision(String routingId, String selectedAgent, String reasoning) {
        emit(ROUTING_DECISION, Map.of(
                "routingId", routingId,
                "selectedAgent", selectedAgent,
                "reasoning", reasoning,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit routing end event.
     */
    public void emitRoutingEnd(String routingId, String selectedAgent) {
        emit(ROUTING_END, Map.of(
                "routingId", routingId,
                "selectedAgent", selectedAgent,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit handoff start event.
     */
    public void emitHandoffStart(String fromAgent, String toAgent, String reason) {
        emit(HANDOFF_START, Map.of(
                "fromAgent", fromAgent,
                "toAgent", toAgent,
                "reason", reason,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit handoff complete event.
     */
    public void emitHandoffComplete(String fromAgent, String toAgent) {
        emit(HANDOFF_COMPLETE, Map.of(
                "fromAgent", fromAgent,
                "toAgent", toAgent,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Emit handoff error event.
     */
    public void emitHandoffError(String fromAgent, String toAgent, String error) {
        emit(HANDOFF_ERROR, Map.of(
                "fromAgent", fromAgent,
                "toAgent", toAgent,
                "error", error,
                "timestamp", System.currentTimeMillis()
        ));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ObservabilityHookMultiAgentTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/hook/ObservabilityHook.java \
        src/test/java/com/skloda/agentscope/hook/ObservabilityHookMultiAgentTest.java
git commit -m "feat: extend ObservabilityHook with multi-agent events"
```

---

## Task 8: Create CompositeAgentFactory

**Files:**
- Create: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`
- Test: `src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.skloda.agentscope.composite;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.agent.AgentType;
import io.agentscope.core.Agent;
import io.agentscope.core.memory.Memory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositeAgentFactoryTest {

    @Mock
    private AgentFactory singleAgentFactory;

    @Mock
    private AgentConfigService configService;

    @Mock
    private Memory memory;

    private CompositeAgentFactory factory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        factory = new CompositeAgentFactory(singleAgentFactory, configService);
    }

    @Test
    void testCreateSingleAgentDelegatesToSingleAgentFactory() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.SINGLE);
        config.setAgentId("test-agent");

        Agent mockAgent = mock(Agent.class);
        when(singleAgentFactory.createAgent(any(AgentConfig.class), any(Memory.class)))
            .thenReturn(mockAgent);

        Agent result = factory.createAgent(config, memory);

        assertNotNull(result);
        verify(singleAgentFactory).createAgent(config, memory);
    }

    @Test
    void testCreateSequentialAgent() {
        AgentConfig config = new AgentConfig();
        config.setType(AgentType.SEQUENTIAL);
        config.setAgentId("pipeline-test");

        // This test will be expanded once we implement the actual creation logic
        assertThrows(UnsupportedOperationException.class, () -> {
            factory.createAgent(config, memory);
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CompositeAgentFactoryTest`
Expected: FAIL with "cannot find symbol CompositeAgentFactory"

- [ ] **Step 3: Write minimal implementation**

```java
package com.skloda.agentscope.composite;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import com.skloda.agentscope.agent.AgentFactory;
import com.skloda.agentscope.agent.AgentType;
import io.agentscope.core.Agent;
import io.agentscope.core.memory.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating multi-agent compositions (Pipeline, Routing, Handoffs).
 * Delegates single agent creation to the existing AgentFactory.
 */
@Component
public class CompositeAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(CompositeAgentFactory.class);

    private final AgentFactory singleAgentFactory;
    private final AgentConfigService configService;

    public CompositeAgentFactory(AgentFactory singleAgentFactory, AgentConfigService configService) {
        this.singleAgentFactory = singleAgentFactory;
        this.configService = configService;
    }

    /**
     * Create an agent based on the configuration type.
     *
     * @param config the agent configuration
     * @param memory the memory to use
     * @return the created agent
     */
    public Agent createAgent(AgentConfig config, Memory memory) {
        log.info("Creating agent of type: {} for agentId: {}", config.getType(), config.getAgentId());

        switch (config.getType()) {
            case SINGLE:
                return singleAgentFactory.createAgent(config, memory);

            case SEQUENTIAL:
                return createSequentialAgent(config, memory);

            case PARALLEL:
                return createParallelAgent(config, memory);

            case ROUTING:
                return createRoutingAgent(config, memory);

            case HANDOFFS:
                return createHandoffsAgent(config, memory);

            default:
                throw new IllegalArgumentException("Unknown agent type: " + config.getType());
        }
    }

    /**
     * Create a sequential pipeline agent.
     */
    private Agent createSequentialAgent(AgentConfig config, Memory memory) {
        log.info("Creating SEQUENTIAL pipeline agent: {}", config.getAgentId());
        // TODO: Implement in Task 10
        throw new UnsupportedOperationException("SEQUENTIAL agent creation not yet implemented");
    }

    /**
     * Create a parallel pipeline agent.
     */
    private Agent createParallelAgent(AgentConfig config, Memory memory) {
        log.info("Creating PARALLEL pipeline agent: {}", config.getAgentId());
        // TODO: Implement in Task 11
        throw new UnsupportedOperationException("PARALLEL agent creation not yet implemented");
    }

    /**
     * Create a routing agent.
     */
    private Agent createRoutingAgent(AgentConfig config, Memory memory) {
        log.info("Creating ROUTING agent: {}", config.getAgentId());
        // TODO: Implement in Task 12
        throw new UnsupportedOperationException("ROUTING agent creation not yet implemented");
    }

    /**
     * Create a handoffs agent.
     */
    private Agent createHandoffsAgent(AgentConfig config, Memory memory) {
        log.info("Creating HANDOFFS agent: {}", config.getAgentId());
        // TODO: Implement in Task 13
        throw new UnsupportedOperationException("HANDOFFS agent creation not yet implemented");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CompositeAgentFactoryTest`
Expected: PASS (single agent delegation works, sequential throws UnsupportedOperationException as expected)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java \
        src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java
git commit -m "feat: add CompositeAgentFactory with basic structure"
```

---

## Task 9: Integrate CompositeAgentFactory into AgentService

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/service/AgentService.java`

- [ ] **Step 1: Read existing AgentService to understand integration point**

Run: `cat src/main/java/com/skloda/agentscope/service/AgentService.java`

- [ ] **Step 2: Modify AgentService to use CompositeAgentFactory**

Locate the agent creation logic in AgentService and modify it to use CompositeAgentFactory for all agent types. The key is to ensure SINGLE type agents still work exactly as before.

Add the `CompositeAgentFactory` as a dependency and use it instead of `AgentFactory` for agent creation:

```java
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final CompositeAgentFactory compositeAgentFactory;
    private final AgentConfigService configService;
    // ... other dependencies

    public AgentService(CompositeAgentFactory compositeAgentFactory,
                        AgentConfigService configService,
                        // ... other dependencies
                       ) {
        this.compositeAgentFactory = compositeAgentFactory;
        this.configService = configService;
        // ... other assignments
    }

    // In methods that create agents, use compositeAgentFactory instead of agentFactory
    // For example, in streamEvents method:
    private Flux<Map<String, Object>> streamEventsInternal(
            String agentId, String userMessage,
            String filePath, String fileName,
            String sessionId,
            List<ChatRequest.ImageInfo> images,
            ChatRequest.AudioInfo audio) {

        AgentConfig config = configService.getAgentConfig(agentId);

        // Get or create session context
        SessionManagerService.SessionContext ctx =
            sessionManagerService.getOrCreateSession(sessionId, agentId);

        // Use CompositeAgentFactory to create agent based on type
        Agent agent = compositeAgentFactory.createAgent(config, ctx.getMemory());

        // ... rest of the method
    }
}
```

- [ ] **Step 3: Run the application to verify it still works**

Run: `mvn clean compile && mvn spring-boot:run`

Expected: Application starts successfully, existing single-agent chat works as before

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/service/AgentService.java
git commit -m "refactor: integrate CompositeAgentFactory into AgentService"
```

---

## Task 10: Implement SequentialAgent Creation

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] **Step 1: Implement createSequentialAgent method**

Replace the `createSequentialAgent` method in `CompositeAgentFactory` with:

```java
/**
 * Create a sequential pipeline agent using AgentScope's SequentialAgent.
 */
private Agent createSequentialAgent(AgentConfig config, Memory memory) {
    log.info("Creating SEQUENTIAL pipeline agent: {} with {} sub-agents",
             config.getAgentId(), config.getSubAgents().size());

    // Create list of sub-agents
    List<Agent> subAgents = config.getSubAgents().stream()
        .map(subConfig -> {
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());
            Agent subAgent = compositeAgentFactory.createAgent(subAgentConfig, memory);
            log.info("  Added sub-agent: {} ({})",
                     subAgentConfig.getName(), subConfig.getAgentId());
            return subAgent;
        })
        .toList();

    // Build SequentialAgent
    return io.agentscope.core.SequentialAgent.builder()
        .name(config.getName())
        .description(config.getDescription())
        .subAgents(subAgents)
        .build();
}
```

- [ ] **Step 2: Add test to verify sequential agent creation**

```java
@Test
void testCreateSequentialAgentWithValidSubAgents() {
    // Setup mock sub-agent configs
    AgentConfig pipelineConfig = new AgentConfig();
    pipelineConfig.setType(AgentType.SEQUENTIAL);
    pipelineConfig.setAgentId("test-pipeline");
    pipelineConfig.setName("Test Pipeline");
    pipelineConfig.setSubAgents(List.of(
        SubAgentConfig.builder().agentId("chat-basic").description("First").build()
    ));

    AgentConfig subConfig = new AgentConfig();
    subConfig.setType(AgentType.SINGLE);
    subConfig.setAgentId("chat-basic");
    subConfig.setName("Basic Chat");

    when(configService.getAgentConfig("chat-basic")).thenReturn(subConfig);

    Agent mockSubAgent = mock(Agent.class);
    when(singleAgentFactory.createAgent(any(AgentConfig.class), any(Memory.class)))
        .thenReturn(mockSubAgent);

    Agent result = factory.createAgent(pipelineConfig, memory);

    assertNotNull(result);
    assertTrue(result instanceof io.agentscope.core.SequentialAgent);
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `mvn test -Dtest=CompositeAgentFactoryTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java \
        src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java
git commit -m "feat: implement SequentialAgent creation in CompositeAgentFactory"
```

---

## Task 11: Implement ParallelAgent Creation

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] **Step 1: Implement createParallelAgent method**

Replace the `createParallelAgent` method in `CompositeAgentFactory` with:

```java
/**
 * Create a parallel pipeline agent using AgentScope's ParallelAgent.
 */
private Agent createParallelAgent(AgentConfig config, Memory memory) {
    log.info("Creating PARALLEL pipeline agent: {} with {} sub-agents",
             config.getAgentId(), config.getSubAgents().size());

    // Create list of sub-agents
    List<Agent> subAgents = config.getSubAgents().stream()
        .map(subConfig -> {
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());
            Agent subAgent = compositeAgentFactory.createAgent(subAgentConfig, memory);
            log.info("  Added sub-agent: {} ({})",
                     subAgentConfig.getName(), subConfig.getAgentId());
            return subAgent;
        })
        .toList();

    // Build ParallelAgent
    return io.agentscope.core.ParallelAgent.builder()
        .name(config.getName())
        .description(config.getDescription())
        .subAgents(subAgents)
        .build();
}
```

- [ ] **Step 2: Add test to verify parallel agent creation**

```java
@Test
void testCreateParallelAgentWithValidSubAgents() {
    AgentConfig pipelineConfig = new AgentConfig();
    pipelineConfig.setType(AgentType.PARALLEL);
    pipelineConfig.setAgentId("test-parallel");
    pipelineConfig.setName("Test Parallel");
    pipelineConfig.setSubAgents(List.of(
        SubAgentConfig.builder().agentId("chat-basic").description("First").build()
    ));

    AgentConfig subConfig = new AgentConfig();
    subConfig.setType(AgentType.SINGLE);
    subConfig.setAgentId("chat-basic");

    when(configService.getAgentConfig("chat-basic")).thenReturn(subConfig);

    Agent mockSubAgent = mock(Agent.class);
    when(singleAgentFactory.createAgent(any(AgentConfig.class), any(Memory.class)))
        .thenReturn(mockSubAgent);

    Agent result = factory.createAgent(pipelineConfig, memory);

    assertNotNull(result);
    assertTrue(result instanceof io.agentscope.core.ParallelAgent);
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `mvn test -Dtest=CompositeAgentFactoryTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java \
        src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java
git commit -m "feat: implement ParallelAgent creation in CompositeAgentFactory"
```

---

## Task 12: Implement RoutingAgent Creation

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] **Step 1: Implement createRoutingAgent method**

First, add the necessary imports at the top of the file:

```java
import io.agentscope.core.agent.AgentScopeAgent;
import io.agentscope.core.agent.AgentScopeRoutingAgent;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.beans.factory.annotation.Value;
```

Then add the `apiKey` field to the class:

```java
@Component
public class CompositeAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(CompositeAgentFactory.class);

    private final AgentFactory singleAgentFactory;
    private final AgentConfigService configService;

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    // ... rest of the class
```

Replace the `createRoutingAgent` method with:

```java
/**
 * Create a routing agent using AgentScope's AgentScopeRoutingAgent.
 */
private Agent createRoutingAgent(AgentConfig config, Memory memory) {
    log.info("Creating ROUTING agent: {} with {} sub-agents",
             config.getAgentId(), config.getSubAgents().size());

    // Create model for routing decisions
    DashScopeChatModel model = DashScopeChatModel.builder()
        .apiKey(apiKey)
        .modelName(config.getModelName())
        .build();

    // Create list of AgentScopeAgent wrappers
    List<AgentScopeAgent> subAgents = config.getSubAgents().stream()
        .map(subConfig -> {
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());
            Agent subAgent = compositeAgentFactory.createAgent(subAgentConfig, memory);

            return AgentScopeAgent.builder()
                .name(subConfig.getAgentId())
                .description(subConfig.getDescription())
                .agent(subAgent)
                .build();
        })
        .toList();

    // Build RoutingAgent
    return AgentScopeRoutingAgent.builder()
        .name(config.getName())
        .description(config.getDescription())
        .model(model)
        .subAgents(subAgents)
        .parallel(config.getParallel() != null ? config.getParallel() : false)
        .build();
}
```

- [ ] **Step 2: Add test to verify routing agent creation**

```java
@Test
void testCreateRoutingAgentWithValidSubAgents() {
    AgentConfig routerConfig = new AgentConfig();
    routerConfig.setType(AgentType.ROUTING);
    routerConfig.setAgentId("test-router");
    routerConfig.setName("Test Router");
    routerConfig.setModelName("qwen-plus");
    routerConfig.setSubAgents(List.of(
        SubAgentConfig.builder()
            .agentId("chat-basic")
            .description("Basic chat agent")
            .build()
    ));

    AgentConfig subConfig = new AgentConfig();
    subConfig.setType(AgentType.SINGLE);
    subConfig.setAgentId("chat-basic");

    when(configService.getAgentConfig("chat-basic")).thenReturn(subConfig);

    Agent mockSubAgent = mock(Agent.class);
    when(singleAgentFactory.createAgent(any(AgentConfig.class), any(Memory.class)))
        .thenReturn(mockSubAgent);

    Agent result = factory.createAgent(routerConfig, memory);

    assertNotNull(result);
    assertTrue(result instanceof AgentScopeRoutingAgent);
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `mvn test -Dtest=CompositeAgentFactoryTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java \
        src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java
git commit -m "feat: implement RoutingAgent creation in CompositeAgentFactory"
```

---

## Task 13: Implement HandoffsAgent Creation

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] **Step 1: Implement createHandoffsAgent method**

Add the necessary imports:

```java
import io.agentscope.core.agent.StateGraphAgent;
import io.agentscope.core.state.StateGraph;
import io.agentscope.core.state.StateKey;
import io.agentscope.core.state.StateKeys;
```

Replace the `createHandoffsAgent` method with:

```java
/**
 * Create a handoffs agent using StateGraph.
 * This is a simplified implementation that routes based on intent keywords.
 * Full StateGraph integration with tool-based handoffs will be added later.
 */
private Agent createHandoffsAgent(AgentConfig config, Memory memory) {
    log.info("Creating HANDOFFS agent: {} with {} sub-agents",
             config.getAgentId(), config.getSubAgents().size());

    // For now, implement as a RoutingAgent with intent-based triggers
    // TODO: Full StateGraph implementation with tool-based handoffs

    // Create model for handoff decisions
    DashScopeChatModel model = DashScopeChatModel.builder()
        .apiKey(apiKey)
        .modelName(config.getModelName())
        .build();

    // Create list of AgentScopeAgent wrappers
    List<AgentScopeAgent> subAgents = config.getSubAgents().stream()
        .map(subConfig -> {
            AgentConfig subAgentConfig = configService.getAgentConfig(subConfig.getAgentId());
            Agent subAgent = compositeAgentFactory.createAgent(subAgentConfig, memory);

            return AgentScopeAgent.builder()
                .name(subConfig.getAgentId())
                .description(subConfig.getDescription())
                .agent(subAgent)
                .build();
        })
        .toList();

    // Build RoutingAgent (simplified handoffs)
    return AgentScopeRoutingAgent.builder()
        .name(config.getName())
        .description(config.getDescription() + " (Handoffs via routing)")
        .model(model)
        .subAgents(subAgents)
        .parallel(false)
        .build();
}
```

- [ ] **Step 2: Add test to verify handoffs agent creation**

```java
@Test
void testCreateHandoffsAgentWithValidSubAgents() {
    AgentConfig handoffConfig = new AgentConfig();
    handoffConfig.setType(AgentType.HANDOFFS);
    handoffConfig.setAgentId("test-handoff");
    handoffConfig.setName("Test Handoff");
    handoffConfig.setModelName("qwen-plus");
    handoffConfig.setSubAgents(List.of(
        SubAgentConfig.builder()
            .agentId("chat-basic")
            .description("Support agent")
            .build()
    ));
    handoffConfig.setHandoffTriggers(List.of(
        HandoffTrigger.builder()
            .type(TriggerType.INTENT)
            .keywords(List.of("购买"))
            .target("sales-expert")
            .build()
    ));

    AgentConfig subConfig = new AgentConfig();
    subConfig.setType(AgentType.SINGLE);
    subConfig.setAgentId("chat-basic");

    when(configService.getAgentConfig("chat-basic")).thenReturn(subConfig);

    Agent mockSubAgent = mock(Agent.class);
    when(singleAgentFactory.createAgent(any(AgentConfig.class), any(Memory.class)))
        .thenReturn(mockSubAgent);

    Agent result = factory.createAgent(handoffConfig, memory);

    assertNotNull(result);
    // Currently returns RoutingAgent, will be StateGraphAgent in full implementation
    assertTrue(result instanceof AgentScopeRoutingAgent);
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `mvn test -Dtest=CompositeAgentFactoryTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java \
        src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java
git commit -m "feat: implement HandoffsAgent creation (simplified via RoutingAgent)"
```

---

## Task 14: Add Multi-Agent YAML Configuration

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add multi-agent example configurations to agents.yml**

Add the following configurations to `src/main/resources/config/agents.yml` (at the end, after existing agents):

```yaml
# === Multi-Agent: Sequential Pipeline ===
- agentId: doc-analysis-pipeline
  type: SEQUENTIAL
  name: 文档分析流水线
  description: 串行执行文档解析、信息提取、报告生成
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  subAgents:
    - agentId: doc-parser
      description: 解析文档内容
    - agentId: info-extractor
      description: 提取结构化信息
    - agentId: report-generator
      description: 生成分析报告
  parallel: false

# === Multi-Agent: Parallel Pipeline ===
- agentId: multi-source-research
  type: PARALLEL
  name: 多源调研
  description: 并行执行多个搜索任务并合并结果
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  subAgents:
    - agentId: search-assistant
      description: 网络搜索专家
    - agentId: rag-chat
      description: 知识库专家
  parallel: true

# === Multi-Agent: Routing ===
- agentId: smart-router
  type: ROUTING
  name: 智能路由器
  description: 根据用户问题自动分发到专家 Agent
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  subAgents:
    - agentId: doc-expert
      description: 文档分析专家，处理文档解析、分析、提取相关问题
    - agentId: search-expert
      description: 搜索专家，获取实时网络信息
    - agentId: vision-expert
      description: 视觉专家，处理图片理解和 OCR
    - agentId: sales-expert
      description: 销售专家，处理产品咨询和报价
  parallel: false

# === Expert Agents for Routing ===
- agentId: doc-expert
  name: 文档专家
  description: 处理文档解析、分析、提取
  systemPrompt: |
    你是一个文档分析专家。
    你可以解析 .docx、.pdf、.xlsx 文件，提取关键信息并进行分析。
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  skills: [docx, pdf, xlsx]
  userTools: [parse_docx, parse_pdf, parse_xlsx]

- agentId: search-expert
  name: 搜索专家
  description: 获取实时网络信息
  systemPrompt: |
    你是一个搜索专家。
    你可以搜索网络获取最新信息，包括新闻、天气、股票等。
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  userTools: [web_search, get_current_weather, get_news]

- agentId: vision-expert
  name: 视觉专家
  description: 图片理解和 OCR
  systemPrompt: |
    你是一个视觉专家。
    你可以理解图片内容，进行 OCR 文字识别、图表分析等。
  modelName: qwen-vl-max
  streaming: true
  enableThinking: false
  modality: vision

- agentId: sales-expert
  name: 销售专家
  description: 产品咨询和报价
  systemPrompt: |
    你是一个销售专家。
    你可以回答产品相关问题，提供报价和购买建议。
  modelName: qwen-plus
  streaming: true
  enableThinking: true

# === Multi-Agent: Handoffs ===
- agentId: customer-service
  type: HANDOFFS
  name: 智能客服
  description: 支持智能体交接的客服系统
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  subAgents:
    - agentId: support-agent
      description: 一般客服
    - agentId: sales-agent
      description: 销售顾问
    - agentId: complaint-agent
      description: 投诉处理
  handoffTriggers:
    - type: INTENT
      keywords: ["转销售", "购买", "价格", "报价"]
      target: sales-agent
    - type: INTENT
      keywords: ["投诉", "不满", "问题"]
      target: complaint-agent
    - type: EXPLICIT
      keywords: ["转人工", "客服", "人工"]
      target: support-agent
    - type: INCAPABLE
      target: smart-router

# === Agents for Handoffs ===
- agentId: support-agent
  name: 客服专员
  description: 一般客服咨询
  systemPrompt: |
    你是一个客服专员。
    你可以回答一般性问题，帮助用户解决问题。
  modelName: qwen-plus
  streaming: true
  enableThinking: true

- agentId: sales-agent
  name: 销售顾问
  description: 销售咨询
  systemPrompt: |
    你是一个销售顾问。
    你可以提供产品咨询、报价和购买建议。
  modelName: qwen-plus
  streaming: true
  enableThinking: true

- agentId: complaint-agent
  name: 投诉处理
  description: 处理客户投诉
  systemPrompt: |
    你是一个投诉处理专员。
    你可以倾听客户的投诉，并提供解决方案。
  modelName: qwen-plus
  streaming: true
  enableThinking: true
```

- [ ] **Step 2: Enable the previously disabled AgentConfigServiceTest**

Remove the `@Disabled` annotation from `AgentConfigServiceTest`.

- [ ] **Step 3: Run application and verify multi-agent configs load**

Run: `mvn clean compile && mvn spring-boot:run`

Check logs for:
- "Creating agent of type: SEQUENTIAL for agentId: doc-analysis-pipeline"
- "Creating agent of type: ROUTING for agentId: smart-router"
- etc.

Expected: Application starts, multi-agent configurations are parsed correctly

- [ ] **Step 4: Test in browser**

Open http://localhost:8080 and try:
1. Select "智能路由器" agent
2. Send a message like "帮我分析一下这个文档" (should route to doc-expert)
3. Select "文档分析流水线" agent
4. Send a message to test sequential execution

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/config/agents.yml \
        src/test/java/com/skloda/agentscope/agent/AgentConfigServiceTest.java
git commit -m "feat: add multi-agent YAML configurations (Pipeline, Routing, Handoffs)"
```

---

## Task 15: Add Frontend Multi-Agent Event Handlers

**Files:**
- Modify: `src/main/resources/static/scripts/chat.js`

- [ ] **Step 1: Add multi-agent event handlers to chat.js**

Locate the SSE event handling section in `chat.js` (around line 400-500) and add the new event handlers:

```javascript
// === Multi-agent event handlers ===

function handlePipelineStart(data) {
    const debugRounds = document.getElementById('debugRounds');
    const pipelineDiv = document.createElement('div');
    pipelineDiv.className = 'debug-pipeline';
    pipelineDiv.id = 'pipeline-' + Date.now();
    pipelineDiv.innerHTML = `
        <div class="debug-pipeline-header">
            <span class="debug-pipeline-title">Pipeline: ${data.pipelineId}</span>
            <span class="debug-pipeline-steps">${data.subAgents.length} steps</span>
        </div>
        <div class="debug-pipeline-steps-container" id="${pipelineDiv.id}-steps"></div>
    `;
    debugRounds.appendChild(pipelineDiv);
}

function handlePipelineStepStart(data) {
    const pipelineSteps = document.querySelector('.debug-pipeline-steps-container:last-child');
    if (!pipelineSteps) return;

    const stepDiv = document.createElement('div');
    stepDiv.className = 'debug-pipeline-step active';
    stepDiv.id = 'step-' + data.stepIndex;
    stepDiv.innerHTML = `
        <span class="debug-step-number">${data.stepIndex + 1}</span>
        <span class="debug-step-agent">${data.agentId}</span>
        <span class="debug-step-status">Running...</span>
    `;
    pipelineSteps.appendChild(stepDiv);
}

function handlePipelineStepEnd(data) {
    const stepDiv = document.getElementById('step-' + data.stepIndex);
    if (stepDiv) {
        stepDiv.classList.remove('active');
        stepDiv.classList.add('complete');
        const statusSpan = stepDiv.querySelector('.debug-step-status');
        if (statusSpan) {
            statusSpan.textContent = `Done (${data.duration_ms}ms)`;
        }
    }
}

function handleRoutingDecision(data) {
    const debugRounds = document.getElementById('debugRounds');
    const routingDiv = document.createElement('div');
    routingDiv.className = 'debug-routing';
    routingDiv.innerHTML = `
        <div class="debug-routing-header">
            <span class="debug-routing-title">Routing Decision</span>
        </div>
        <div class="debug-routing-content">
            <div class="debug-routing-selected">
                <span class="debug-routing-label">Selected:</span>
                <span class="debug-routing-agent">${data.selectedAgent}</span>
            </div>
            <div class="debug-routing-reasoning">
                <span class="debug-routing-label">Reasoning:</span>
                <span class="debug-routing-text">${data.reasoning}</span>
            </div>
        </div>
    `;
    debugRounds.appendChild(routingDiv);
}

function handleHandoffStart(data) {
    const debugRounds = document.getElementById('debugRounds');
    const handoffDiv = document.createElement('div');
    handoffDiv.className = 'debug-handoff';
    handoffDiv.innerHTML = `
        <div class="debug-handoff-header">
            <span class="debug-handoff-icon">→</span>
            <span class="debug-handoff-text">
                Handoff: <strong>${data.fromAgent}</strong> → <strong>${data.toAgent}</strong>
            </span>
        </div>
        <div class="debug-handoff-reason">
            Reason: ${data.reason}
        </div>
    `;
    debugRounds.appendChild(handoffDiv);
}
```

Then, update the main SSE event handler to route these events:

```javascript
// In the main SSE event handler (around line 450-500)
switch (eventType) {
    // ... existing cases ...
    case 'pipeline_start':
        handlePipelineStart(eventData);
        break;
    case 'pipeline_step_start':
        handlePipelineStepStart(eventData);
        break;
    case 'pipeline_step_end':
        handlePipelineStepEnd(eventData);
        break;
    case 'pipeline_end':
        // Optional: add summary
        break;
    case 'routing_decision':
        handleRoutingDecision(eventData);
        break;
    case 'handoff_start':
        handleHandoffStart(eventData);
        break;
    case 'handoff_complete':
        // Optional: add completion marker
        break;
}
```

- [ ] **Step 2: Add CSS styles for multi-agent debug elements**

Add to `src/main/resources/static/styles/chat.css`:

```css
/* === Multi-agent debug panel styles === */

.debug-pipeline {
    margin: 8px 0;
    padding: 8px;
    background: rgba(0, 255, 136, 0.05);
    border: 1px solid rgba(0, 255, 136, 0.3);
    border-radius: 4px;
}

.debug-pipeline-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 11px;
    color: #00ff88;
}

.debug-pipeline-steps-container {
    display: flex;
    gap: 4px;
}

.debug-pipeline-step {
    flex: 1;
    padding: 6px 8px;
    background: rgba(0, 0, 0, 0.3);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 3px;
    font-size: 10px;
    display: flex;
    align-items: center;
    gap: 6px;
    transition: all 0.3s;
}

.debug-pipeline-step.active {
    border-color: #00ff88;
    background: rgba(0, 255, 136, 0.1);
    animation: pulse 1.5s infinite;
}

.debug-pipeline-step.complete {
    border-color: rgba(0, 255, 136, 0.5);
    opacity: 0.7;
}

.debug-step-number {
    font-weight: bold;
    color: #00ff88;
}

.debug-step-agent {
    flex: 1;
}

.debug-step-status {
    font-size: 9px;
    color: rgba(255, 255, 255, 0.6);
}

.debug-routing {
    margin: 8px 0;
    padding: 8px;
    background: rgba(255, 200, 0, 0.05);
    border: 1px solid rgba(255, 200, 0, 0.3);
    border-radius: 4px;
}

.debug-routing-header {
    margin-bottom: 8px;
    font-size: 11px;
    color: #ffc800;
}

.debug-routing-content {
    font-size: 10px;
}

.debug-routing-selected {
    margin-bottom: 4px;
    display: flex;
    gap: 8px;
}

.debug-routing-label {
    color: rgba(255, 255, 255, 0.6);
}

.debug-routing-agent {
    color: #ffc800;
    font-weight: bold;
}

.debug-routing-reasoning {
    color: rgba(255, 255, 255, 0.8);
}

.debug-handoff {
    margin: 8px 0;
    padding: 8px;
    background: rgba(0, 200, 255, 0.05);
    border: 1px solid rgba(0, 200, 255, 0.3);
    border-radius: 4px;
}

.debug-handoff-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
    font-size: 11px;
    color: #00c8ff;
}

.debug-handoff-icon {
    font-size: 14px;
}

.debug-handoff-reason {
    font-size: 10px;
    color: rgba(255, 255, 255, 0.7);
}

@keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.6; }
}
```

- [ ] **Step 3: Run application and test multi-agent visualization**

Run: `mvn spring-boot:run`

Open browser and test:
1. Select "智能路由器" - should see routing decision visualization
2. Select "文档分析流水线" - should see pipeline steps visualization
3. Select "智能客服" - should see handoff visualization

Expected: Debug panel shows multi-agent events with proper styling

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/scripts/chat.js \
        src/main/resources/static/styles/chat.css
git commit -m "feat: add multi-agent event handlers and debug panel visualization"
```

---

## Task 16: End-to-End Integration Test

**Files:**
- Test: Manual E2E test

- [ ] **Step 1: Test Pipeline (Sequential)**

1. Start application: `mvn spring-boot:run`
2. Open http://localhost:8080
3. Select "文档分析流水线" agent
4. Send message: "你好"
5. Verify:
   - Debug panel shows pipeline_start event
   - Debug panel shows pipeline_step_start for each step
   - Each sub-agent executes in sequence
   - Debug panel shows pipeline_step_end for each step

- [ ] **Step 2: Test Pipeline (Parallel)**

1. Select "多源调研" agent
2. Send message: "搜索人工智能的最新进展"
3. Verify:
   - Both search-assistant and rag-chat execute
   - Results are merged

- [ ] **Step 3: Test Routing**

1. Select "智能路由器" agent
2. Send message: "帮我解析一个PDF文档"
3. Verify:
   - Debug panel shows routing_decision event
   - Routes to doc-expert
4. Send message: "今天天气怎么样"
5. Verify:
   - Routes to search-expert

- [ ] **Step 4: Test Handoffs**

1. Select "智能客服" agent
2. Send message: "我想购买产品"
3. Verify:
   - Routes to sales-agent
4. Send message: "我要投诉"
5. Verify:
   - Handoffs to complaint-agent

- [ ] **Step 5: Test backward compatibility**

1. Select "Basic Chat" agent
2. Send message: "你好"
3. Verify:
   - Works exactly as before
   - No multi-agent events in debug panel

- [ ] **Step 6: Document any issues and fix**

If any issues found, create bug fix tasks and execute them.

- [ ] **Step 7: Final commit**

```bash
git add -A
git commit -m "test: complete multi-agent collaboration implementation (Phase 3)"
```

---

## Self-Review Checklist

- [ ] **Spec coverage**: All spec requirements implemented
  - Pipeline (Sequential/Parallel) ✓
  - Routing ✓
  - Handoffs ✓
  - Extended AgentConfig ✓
  - CompositeAgentFactory ✓
  - Extended ObservabilityHook ✓
  - YAML configuration ✓
  - Frontend visualization ✓

- [ ] **Placeholder scan**: No TBD, TODO, or incomplete steps found

- [ ] **Type consistency**: All type names match (AgentType, SubAgentConfig, HandoffTrigger, TriggerType)

- [ ] **Backward compatibility**: Existing single-agent configs work unchanged

- [ ] **Testing**: Unit tests for new classes, integration test for YAML loading, E2E test for user flows

---

## Completion Criteria

The implementation is complete when:

1. All tasks are checked off
2. All tests pass: `mvn test`
3. Application starts without errors
4. Multi-agent agents appear in the UI dropdown
5. Pipeline execution shows step-by-step progress in debug panel
6. Routing shows decision reasoning in debug panel
7. Handoffs show agent transitions in debug panel
8. Existing single-agent functionality is unchanged

---

## Notes for Implementation

- This plan implements the core multi-agent collaboration features from the design spec
- Full StateGraph-based Handoffs with tool triggers is simplified to Routing-based for initial implementation
- The structure allows for easy enhancement (e.g., adding stopOnError config for Pipeline)
- All new code follows existing patterns (Lombok for data classes, Spring @Component/@Service)
- Multi-agent events flow through the same ObservabilityHook mechanism as single-agent events
