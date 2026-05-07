# P3: Planning & Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PlanNotebook, AutoContextMemory demo, and Bailian long-term memory to the AgentScope demo app.

**Architecture:** Extend existing AgentConfig + AgentFactory + agents.yml chain. No new service classes. PlanNotebook is in agentscope-core (no new dep). Bailian memory needs one new Maven artifact. All new features are observable through existing Debug Panel.

**Tech Stack:** AgentScope Java 1.0.12, BailianLongTermMemory, PlanNotebook, AutoContextMemory, Spring Boot 3.5.14

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `pom.xml` | Add `agentscope-extensions-memory-bailian` dependency |
| Modify | `src/main/java/com/skloda/agentscope/agent/AgentConfig.java` | Add `planEnabled` field, `LongTermMemoryConfig` inner class |
| Modify | `src/main/java/com/skloda/agentscope/agent/AgentFactory.java` | Add PlanNotebook enable, long-term memory creation methods |
| Modify | `src/main/resources/config/agents.yml` | Upgrade `project-planner`, add `long-conversation`, add `personal-assistant` |
| Create | `src/test/java/com/skloda/agentscope/agent/AgentConfigPlanAndMemoryTest.java` | Tests for new config fields |
| Create | `src/test/java/com/skloda/agentscope/agent/AgentFactoryPlanAndMemoryTest.java` | Tests for PlanNotebook and LTM factory methods |
| Modify | `docs/ROADMAP.md` | Update P3 status |

---

### Task 1: Add Bailian Memory Dependency

**Files:**
- Modify: `pom.xml:76-88` (after the `agentscope-extensions-rag-simple` dependency block)

- [ ] **Step 1: Add the Bailian memory dependency to pom.xml**

After the `agentscope-extensions-rag-simple` dependency (line 88), add:

```xml
        <!-- AgentScope Bailian Long-term Memory -->
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-extensions-memory-bailian</artifactId>
            <version>${agentscope.version}</version>
        </dependency>
```

- [ ] **Step 2: Verify dependency resolves**

Run: `mvn dependency:resolve -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add agentscope-extensions-memory-bailian dependency for P3"
```

---

### Task 2: Extend AgentConfig with Plan and Memory Fields

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`
- Create: `src/test/java/com/skloda/agentscope/agent/AgentConfigPlanAndMemoryTest.java`

- [ ] **Step 1: Write failing tests for new config fields**

Create `src/test/java/com/skloda/agentscope/agent/AgentConfigPlanAndMemoryTest.java`:

```java
package com.skloda.agentscope.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigPlanAndMemoryTest {

    @Test
    void testPlanEnabledDefaultsToFalse() {
        AgentConfig config = new AgentConfig();
        assertFalse(config.isPlanEnabled());
    }

    @Test
    void testPlanEnabledCanBeSet() {
        AgentConfig config = new AgentConfig();
        config.setPlanEnabled(true);
        assertTrue(config.isPlanEnabled());
    }

    @Test
    void testLongTermMemoryDefaultsToNull() {
        AgentConfig config = new AgentConfig();
        assertNull(config.getLongTermMemory());
    }

    @Test
    void testLongTermMemoryConfigDefaults() {
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        assertEquals("none", ltm.getType());
        assertEquals("STATIC_CONTROL", ltm.getMode());
        assertEquals("default_user", ltm.getUserId());
    }

    @Test
    void testLongTermMemoryConfigCanBeSet() {
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("bailian");
        ltm.setMode("AGENT_CONTROL");
        ltm.setUserId("user-123");

        assertEquals("bailian", ltm.getType());
        assertEquals("AGENT_CONTROL", ltm.getUserId());
        assertEquals("user-123", ltm.getUserId());
    }

    @Test
    void testLongTermMemoryOnAgentConfig() {
        AgentConfig config = new AgentConfig();
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("bailian");
        config.setLongTermMemory(ltm);

        assertNotNull(config.getLongTermMemory());
        assertEquals("bailian", config.getLongTermMemory().getType());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=AgentConfigPlanAndMemoryTest -q 2>&1 | tail -10`
Expected: Compilation errors — `isPlanEnabled()`, `LongTermMemoryConfig` not found

- [ ] **Step 3: Add fields to AgentConfig**

In `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`, add after the `structuredOutputReminder` field (line 48):

```java
    // PlanNotebook settings
    private boolean planEnabled = false;

    // Long-term memory settings
    private LongTermMemoryConfig longTermMemory;
```

Add at the end of the class, before the closing `}`:

```java
    @Setter
    @Getter
    public static class LongTermMemoryConfig {
        private String type = "none";
        private String mode = "STATIC_CONTROL";
        private String userId = "default_user";
    }
```

- [ ] **Step 4: Fix the test — correct the assertion typo**

In the test file, fix line `assertEquals("AGENT_CONTROL", ltm.getUserId())` — it should check `getMode()`:

```java
    @Test
    void testLongTermMemoryConfigCanBeSet() {
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("bailian");
        ltm.setMode("AGENT_CONTROL");
        ltm.setUserId("user-123");

        assertEquals("bailian", ltm.getType());
        assertEquals("AGENT_CONTROL", ltm.getMode());
        assertEquals("user-123", ltm.getUserId());
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=AgentConfigPlanAndMemoryTest -q 2>&1 | tail -5`
Expected: Tests passed: 6

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfig.java src/test/java/com/skloda/agentscope/agent/AgentConfigPlanAndMemoryTest.java
git commit -m "feat: add planEnabled and LongTermMemoryConfig to AgentConfig"
```

---

### Task 3: Extend AgentFactory with PlanNotebook and Long-term Memory

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`
- Create: `src/test/java/com/skloda/agentscope/agent/AgentFactoryPlanAndMemoryTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/skloda/agentscope/agent/AgentFactoryPlanAndMemoryTest.java`:

```java
package com.skloda.agentscope.agent;

import com.skloda.agentscope.service.KnowledgeService;
import com.skloda.agentscope.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFactoryPlanAndMemoryTest {

    @Mock
    private AgentConfigService configService;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private KnowledgeService knowledgeService;

    private AgentFactory agentFactory;

    @BeforeEach
    void setUp() {
        agentFactory = new AgentFactory(configService, toolRegistry, knowledgeService);
        // Set apiKey via reflection since @Value isn't processed in unit tests
        try {
            var field = AgentFactory.class.getDeclaredField("apiKey");
            field.setAccessible(true);
            field.set(agentFactory, "test-api-key");
        } catch (Exception e) {
            fail("Failed to set apiKey: " + e.getMessage());
        }
    }

    private AgentConfig baseConfig(String agentId) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(agentId);
        config.setName("Test " + agentId);
        config.setModelName("qwen-plus");
        return config;
    }

    @Test
    void testParseLtmModeStaticControl() {
        AgentConfig config = baseConfig("test-agent");
        when(configService.getAgentConfig("test-agent")).thenReturn(config);

        // Parse STATIC_CONTROL (default)
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.STATIC_CONTROL,
                agentFactory.parseLtmMode("STATIC_CONTROL"));
    }

    @Test
    void testParseLtmModeAgentControl() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.AGENT_CONTROL,
                agentFactory.parseLtmMode("AGENT_CONTROL"));
    }

    @Test
    void testParseLtmModeBoth() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.BOTH,
                agentFactory.parseLtmMode("BOTH"));
    }

    @Test
    void testParseLtmModeNullDefaultsToStaticControl() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.STATIC_CONTROL,
                agentFactory.parseLtmMode(null));
    }

    @Test
    void testParseLtmModeUnknownDefaultsToStaticControl() {
        assertEquals(io.agentscope.core.memory.LongTermMemoryMode.STATIC_CONTROL,
                agentFactory.parseLtmMode("UNKNOWN"));
    }

    @Test
    void testCreateAgentWithPlanEnabledDoesNotThrow() {
        AgentConfig config = baseConfig("plan-agent");
        config.setPlanEnabled(true);
        when(configService.getAgentConfig("plan-agent")).thenReturn(config);

        assertDoesNotThrow(() -> agentFactory.createAgent("plan-agent"));
    }

    @Test
    void testCreateAgentWithBailianLongTermMemoryDoesNotThrow() {
        AgentConfig config = baseConfig("memory-agent");
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("bailian");
        ltm.setUserId("test-user");
        config.setLongTermMemory(ltm);
        when(configService.getAgentConfig("memory-agent")).thenReturn(config);

        assertDoesNotThrow(() -> agentFactory.createAgent("memory-agent"));
    }

    @Test
    void testCreateAgentWithNoneTypeDoesNotFail() {
        AgentConfig config = baseConfig("no-mem-agent");
        AgentConfig.LongTermMemoryConfig ltm = new AgentConfig.LongTermMemoryConfig();
        ltm.setType("none");
        config.setLongTermMemory(ltm);
        when(configService.getAgentConfig("no-mem-agent")).thenReturn(config);

        assertDoesNotThrow(() -> agentFactory.createAgent("no-mem-agent"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest=AgentFactoryPlanAndMemoryTest -q 2>&1 | tail -10`
Expected: Compilation errors — `parseLtmMode` not found on AgentFactory

- [ ] **Step 3: Add PlanNotebook support to AgentFactory.buildAgent()**

In `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`, add the following import at the top with the other imports:

```java
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.bailian.BailianLongTermMemory;
```

In `buildAgent()`, after line 103 (`.memory(memory)`) and before line 105 (`Toolkit toolkit = new Toolkit()`), add:

```java
        // Enable PlanNotebook if configured
        if (config.isPlanEnabled()) {
            builder.enablePlan();
            log.info("  Enabled PlanNotebook for agent: {}", agentId);
        }
```

In `buildAgent()`, after the RAG configuration block (after line 136) and before the hooks block (line 139), add:

```java
        // Configure long-term memory if enabled
        if (config.getLongTermMemory() != null && !"none".equals(config.getLongTermMemory().getType())) {
            LongTermMemory ltm = createLongTermMemory(config.getLongTermMemory());
            if (ltm != null) {
                LongTermMemoryMode mode = parseLtmMode(config.getLongTermMemory().getMode());
                builder.longTermMemory(ltm).longTermMemoryMode(mode);
                log.info("  Enabled long-term memory for agent: {} (type={}, mode={})",
                        agentId, config.getLongTermMemory().getType(), mode);
            }
        }
```

Add these two private methods at the end of the class, before the closing `}`:

```java
    /**
     * Create LongTermMemory instance based on config type.
     */
    LongTermMemory createLongTermMemory(AgentConfig.LongTermMemoryConfig config) {
        return switch (config.getType().toLowerCase()) {
            case "bailian" -> BailianLongTermMemory.builder()
                    .apiKey(apiKey)
                    .userId(config.getUserId())
                    .build();
            default -> null;
        };
    }

    /**
     * Parse long-term memory mode string to enum.
     */
    LongTermMemoryMode parseLtmMode(String value) {
        if (value == null) return LongTermMemoryMode.STATIC_CONTROL;
        return switch (value.trim().toUpperCase()) {
            case "AGENT_CONTROL" -> LongTermMemoryMode.AGENT_CONTROL;
            case "BOTH" -> LongTermMemoryMode.BOTH;
            default -> LongTermMemoryMode.STATIC_CONTROL;
        };
    }
```

Note: `createLongTermMemory` and `parseLtmMode` are package-private (no `private` modifier) so the test class in the same package can access them.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=AgentFactoryPlanAndMemoryTest -q 2>&1 | tail -5`
Expected: Tests passed: 8

- [ ] **Step 5: Run full test suite to check for regressions**

Run: `mvn test -q 2>&1 | tail -10`
Expected: BUILD SUCCESS, 0 failures

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java src/test/java/com/skloda/agentscope/agent/AgentFactoryPlanAndMemoryTest.java
git commit -m "feat: add PlanNotebook enable and Bailian long-term memory to AgentFactory"
```

---

### Task 4: Upgrade project-planner Agent Config

**Files:**
- Modify: `src/main/resources/config/agents.yml:465-506`

- [ ] **Step 1: Update the project-planner agent entry**

In `src/main/resources/config/agents.yml`, replace the entire `project-planner` block (lines 465-506) with:

```yaml
  - agentId: project-planner
    category: single
    name: Project Planner
    description: 项目规划和任务分解助手，支持创建和执行结构化计划
    systemPrompt: |
      你是一个项目规划助手，擅长将复杂任务分解为可执行的步骤。
      你可以帮助用户：
      1. 将复杂任务分解为具体的步骤和子任务
      2. 制定项目计划和时间表
      3. 识别任务依赖关系和风险
      4. 跟踪任务执行进度

      当用户提出项目或任务时：
      1. 首先理解目标和范围
      2. 使用 create_plan 工具创建结构化计划
      3. 按计划逐步执行子任务
      4. 完成每个子任务时调用 finish_subtask
      5. 全部完成后调用 finish_plan

      回答格式：
      - 使用清晰的标题和步骤编号
      - 标注优先级（高/中/低）
      - 标注预估时间
      - 提供必要的说明和建议
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    planEnabled: true
    autoContext: true
    autoContextMsgThreshold: 20
    autoContextLastKeep: 8
    autoContextTokenRatio: 0.3
    skills: []
    userTools: []
    systemTools: []
    samplePrompts:
      - prompt: "帮我规划一个两周内上线文档分析 Agent Demo 的项目计划"
        expectedBehavior: "使用 create_plan 创建结构化计划，逐步执行子任务"
      - prompt: "把"搭建知识库问答系统"拆成可以执行的任务清单"
        expectedBehavior: "创建计划，展示任务分解和依赖关系"
      - prompt: "评估一下下周交付这个 Demo 的主要风险，并给出应对方案"
        expectedBehavior: "创建风险分析计划，逐步评估并给出方案"
```

Key changes: removed `plan_notebook` from systemTools, added `planEnabled: true`, updated systemPrompt to reference planning tools.

- [ ] **Step 2: Verify YAML is valid and agent loads**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: upgrade project-planner with planEnabled and planning-aware prompt"
```

---

### Task 5: Add long-conversation Agent Config

**Files:**
- Modify: `src/main/resources/config/agents.yml` (add after project-planner, before Phase 3 section)

- [ ] **Step 1: Add long-conversation agent entry**

In `src/main/resources/config/agents.yml`, after the `project-planner` block (after the line with `expectedBehavior: "展示项目风险分析和行动计划"`), and before the `# ============================================================` Phase 3 comment, insert:

```yaml
  - agentId: long-conversation
    category: single
    name: 长对话助手
    description: 支持超长对话的技术顾问，自动压缩上下文
    systemPrompt: |
      你是一个技术顾问助手，擅长各种技术领域的深入讨论。
      你能够进行长时间的多轮对话，始终保持连贯性和准确性。
      当对话变得很长时，系统会自动管理上下文，你不需要特殊处理。
      请专注于提供准确、有深度的技术解答。
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    autoContext: true
    autoContextMsgThreshold: 20
    autoContextLastKeep: 8
    autoContextTokenRatio: 0.3
    skills: []
    userTools: []
    systemTools: []
    samplePrompts:
      - prompt: "我想从零开始学习微服务架构，请从基础概念开始逐步讲解"
        expectedBehavior: "开始详细的技术讲解，支持持续多轮深入讨论"
      - prompt: "帮我分析 Java Spring Boot 和 Quarkus 的技术选型"
        expectedBehavior: "深入对比分析，支持持续追问和细节讨论"
```

- [ ] **Step 2: Verify YAML is valid**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add long-conversation agent with AutoContextMemory demo"
```

---

### Task 6: Add personal-assistant Agent Config (Bailian LTM)

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add personal-assistant agent entry**

In `src/main/resources/config/agents.yml`, after the `long-conversation` block, and before the `# ============================================================` Phase 3 comment, insert:

```yaml
  - agentId: personal-assistant
    category: single
    name: 个人助手
    description: 跨会话记住你的偏好和习惯
    systemPrompt: |
      你是一个贴心的个人助手。
      你能记住用户在对话中表达的偏好和习惯，在后续对话中自动应用。
      例如：
      - 语言偏好（中文/英文）
      - 回答格式偏好（Markdown、列表、表格等）
      - 技术栈偏好
      - 沟通风格偏好

      当用户表达偏好时，认真记住并在之后的对话中遵循。
      如果用户问起，主动说明你记住的偏好。
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    skills: []
    userTools: []
    systemTools: []
    longTermMemory:
      type: bailian
      mode: STATIC_CONTROL
      userId: default_user
    samplePrompts:
      - prompt: "我喜欢用中文回答，格式用 Markdown，技术栈偏好 Java"
        expectedBehavior: "记住用户偏好，后续对话中遵循这些偏好"
      - prompt: "按我的偏好介绍一下你自己"
        expectedBehavior: "根据之前记住的偏好回答（中文、Markdown、Java）"
```

- [ ] **Step 2: Verify YAML is valid**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add personal-assistant agent with Bailian long-term memory"
```

---

### Task 7: Run Full Test Suite and Verify Build

**Files:** None (verification only)

- [ ] **Step 1: Run full test suite**

Run: `mvn test -q 2>&1 | tail -15`
Expected: BUILD SUCCESS, 0 failures

- [ ] **Step 2: Verify compile succeeds**

Run: `mvn clean compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Verify all three new agents are loadable**

Run: `mvn test -pl . -Dtest=AgentSamplePromptsConfigTest -q 2>&1 | tail -5`
Expected: Tests passed

- [ ] **Step 4: Commit any remaining changes (if needed)**

Only if there are unstaged changes.

---

### Task 8: Update ROADMAP

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Update P3 status in ROADMAP.md**

In `docs/ROADMAP.md`, update the following sections:

1. In the **Progress Snapshot** section, update Current TODO:
   - Change `- [ ] Start P3 Planning & Memory demos.` to `- [x] Start P3 Planning & Memory demos.`
   - Change `- [ ] Implement remaining AgentScope features as real demo scenarios.` to stay as-is (there are more phases).

2. Add completed items after the existing completed list:
```
- [x] P3 Planning & Memory: PlanNotebook, AutoContextMemory demo, Bailian long-term memory.
```

3. In the **Feature Coverage Matrix**, update:
   - `PlanNotebook (structured planning)` row: change `❌` → `✅`, update Demo Agent to `project-planner (planEnabled)`
   - `Long-term Memory — Mem0` row: keep as `❌` (not implemented, Bailian was chosen instead)
   - Add a note or update: `Long-term Memory — Bailian` → `✅`, `personal-assistant`

4. In **Section 7. Suggested Next Milestone**, update the checklist:
   - `[ ] PlanNotebook demo` → `[x] PlanNotebook demo`
   - `[ ] AutoContextMemory demo` → `[x] AutoContextMemory demo`
   - `[ ] Long-term memory demo: add Mem0 or ReMe` → `[x] Long-term memory demo: BailianLongTermMemory`

- [ ] **Step 2: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: update ROADMAP with P3 completion status"
```

---

## Self-Review

**Spec coverage check:**
- PlanNotebook integration → Task 2 (config) + Task 3 (factory) + Task 4 (agents.yml) ✅
- AutoContextMemory demo → Task 5 (agents.yml only) ✅
- Bailian long-term memory → Task 1 (dep) + Task 2 (config) + Task 3 (factory) + Task 6 (agents.yml) ✅
- Debug Panel observability → No changes needed, documented in plan ✅
- ROADMAP update → Task 8 ✅
- Acceptance criteria (agents in UI) → Task 7 verifies ✅

**Placeholder scan:** No TBD/TODO/fill-in-later found. All code blocks contain complete code.

**Type consistency:** `AgentConfig.LongTermMemoryConfig`, `AgentFactory.createLongTermMemory()`, `AgentFactory.parseLtmMode()` — names consistent across test and implementation. `planEnabled` / `isPlanEnabled()` / `setPlanEnabled()` consistent throughout.
