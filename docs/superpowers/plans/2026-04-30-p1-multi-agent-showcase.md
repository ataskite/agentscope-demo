# P1 Multi-agent Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Supervisor agent (ROUTING reuse), Debate/Review demo (new DEBATE type with custom DebatePipeline), and enhance existing Customer Service and Doc Pipeline prompts.

**Architecture:** Supervisor reuses ROUTING pattern — zero code changes. Debate uses a new `DebatePipeline implements Pipeline<Msg>` that runs debaters in parallel via Reactor then passes aggregated viewpoints to a Judge agent. Both are wired through the existing `CompositeAgentFactory` → `AgentRuntimeFactory` → `PipelineAgentRuntime` dispatch chain.

**Tech Stack:** Java 17, AgentScope 1.0.11 (Pipeline interface, AgentBase.call), Reactor (Flux.merge, Mono), SnakeYAML, vanilla JS frontend.

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/java/com/skloda/agentscope/agent/AgentType.java` | Modify | Add `DEBATE` enum value |
| `src/main/java/com/skloda/agentscope/composite/DebatePipeline.java` | **Create** | Custom Pipeline: parallel debaters + judge synthesis |
| `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java` | Modify | Add `createDebateAgent()` method |
| `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java` | Modify | Add DEBATE dispatch in both `createRuntime()` and `createRuntimeWithMemory()` |
| `src/main/resources/config/agents.yml` | Modify | Add 5 new agents, update 5 existing agent prompts |
| `src/test/java/com/skloda/agentscope/agent/AgentTypeTest.java` | Modify | Update enum count assertion |
| `src/test/java/com/skloda/agentscope/composite/DebatePipelineTest.java` | **Create** | Unit tests for DebatePipeline |
| `src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java` | Modify | Add debate agent creation tests |
| `src/test/java/com/skloda/agentscope/runtime/AgentRuntimeFactoryTest.java` | Modify | Add DEBATE dispatch tests |

---

### Task 1: Add DEBATE enum value to AgentType

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentType.java`
- Modify: `src/test/java/com/skloda/agentscope/agent/AgentTypeTest.java`

- [ ] **Step 1: Add DEBATE to the enum**

In `src/main/java/com/skloda/agentscope/agent/AgentType.java`, add `DEBATE(false)` after `HANDOFFS(false)`:

```java
public enum AgentType {
    SINGLE(true),
    SEQUENTIAL(false),
    PARALLEL(false),
    ROUTING(false),
    HANDOFFS(false),
    DEBATE(false);

    private final boolean isDefault;

    AgentType(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
```

- [ ] **Step 2: Update the enum count test**

In `src/test/java/com/skloda/agentscope/agent/AgentTypeTest.java`, change the count from 5 to 6:

```java
@Test
void testEnumValues() {
    assertEquals(6, AgentType.values().length);
}
```

- [ ] **Step 3: Run tests to verify**

Run: `mvn -Dtest=AgentTypeTest test -q`
Expected: PASS (1 test)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentType.java src/test/java/com/skloda/agentscope/agent/AgentTypeTest.java
git commit -m "feat: add DEBATE enum value to AgentType"
```

---

### Task 2: Create DebatePipeline class

**Files:**
- Create: `src/main/java/com/skloda/agentscope/composite/DebatePipeline.java`

- [ ] **Step 1: Create DebatePipeline.java**

Create `src/main/java/com/skloda/agentscope/composite/DebatePipeline.java`:

```java
package com.skloda.agentscope.composite;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class DebatePipeline implements Pipeline<Msg> {

    private final List<AgentBase> debaters;
    private final AgentBase judge;

    public DebatePipeline(List<AgentBase> debaters, AgentBase judge) {
        if (debaters == null || debaters.size() < 2) {
            throw new IllegalArgumentException("DEBATE requires at least 2 debaters + 1 judge");
        }
        if (judge == null) {
            throw new IllegalArgumentException("DEBATE requires a judge agent");
        }
        this.debaters = debaters;
        this.judge = judge;
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        // Phase 1: Run debaters in parallel
        List<Mono<Msg>> debaterMonos = debaters.stream()
                .map(d -> d.call(input))
                .toList();

        return Flux.merge(debaterMonos)
                .collectList()
                .flatMap(viewpoints -> {
                    Msg judgeInput = buildJudgeInput(input, viewpoints);
                    return judge.call(judgeInput);
                });
    }

    Msg buildJudgeInput(Msg originalQuestion, List<Msg> viewpoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 待评估的提案\n\n");

        if (originalQuestion != null && originalQuestion.getContent() != null) {
            for (ContentBlock block : originalQuestion.getContent()) {
                if (block instanceof TextBlock tb) {
                    sb.append(tb.getText()).append("\n\n");
                }
            }
        }

        sb.append("## 各位专家的观点\n\n");
        for (int i = 0; i < viewpoints.size(); i++) {
            sb.append("### 专家 ").append(i + 1).append(" 的观点\n\n");
            Msg vp = viewpoints.get(i);
            if (vp != null && vp.getContent() != null) {
                for (ContentBlock block : vp.getContent()) {
                    if (block instanceof TextBlock tb) {
                        sb.append(tb.getText()).append("\n\n");
                    }
                }
            }
        }

        sb.append("---\n\n请综合以上所有专家的观点，给出你的最终裁决。");

        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .build();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/DebatePipeline.java
git commit -m "feat: add DebatePipeline for parallel debate + judge synthesis"
```

---

### Task 3: Add DebatePipeline unit tests

**Files:**
- Create: `src/test/java/com/skloda/agentscope/composite/DebatePipelineTest.java`

- [ ] **Step 1: Create test file**

Create `src/test/java/com/skloda/agentscope/composite/DebatePipelineTest.java`:

```java
package com.skloda.agentscope.composite;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebatePipelineTest {

    private Msg textMsg(String text) {
        return Msg.builder()
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : msg.getContent()) {
            if (b instanceof TextBlock tb) sb.append(tb.getText());
        }
        return sb.toString();
    }

    @Test
    void constructorRequiresAtLeastTwoDebaters() {
        AgentBase debater = new FakeAgent("debater");
        AgentBase judge = new FakeAgent("judge");

        assertThrows(IllegalArgumentException.class,
                () -> new DebatePipeline(List.of(debater), judge));
    }

    @Test
    void constructorRequiresNonNullDebaters() {
        AgentBase judge = new FakeAgent("judge");
        assertThrows(IllegalArgumentException.class,
                () -> new DebatePipeline(null, judge));
    }

    @Test
    void constructorRequiresNonNullJudge() {
        AgentBase d1 = new FakeAgent("d1");
        AgentBase d2 = new FakeAgent("d2");
        assertThrows(IllegalArgumentException.class,
                () -> new DebatePipeline(List.of(d1, d2), null));
    }

    @Test
    void buildJudgeInputAggregatesAllViewpoints() {
        AgentBase d1 = new FakeAgent("d1");
        AgentBase d2 = new FakeAgent("d2");
        AgentBase judge = new FakeAgent("judge");

        DebatePipeline pipeline = new DebatePipeline(List.of(d1, d2), judge);

        Msg question = textMsg("Should we adopt remote work?");
        List<Msg> viewpoints = List.of(
                textMsg("Remote work boosts productivity"),
                textMsg("Remote work hurts team cohesion")
        );

        Msg result = pipeline.buildJudgeInput(question, viewpoints);
        String text = extractText(result);

        assertTrue(text.contains("待评估的提案"));
        assertTrue(text.contains("Should we adopt remote work?"));
        assertTrue(text.contains("专家 1"));
        assertTrue(text.contains("Remote work boosts productivity"));
        assertTrue(text.contains("专家 2"));
        assertTrue(text.contains("Remote work hurts team cohesion"));
        assertTrue(text.contains("最终裁决"));
    }

    @Test
    void buildJudgeInputHandlesNullQuestionContent() {
        AgentBase d1 = new FakeAgent("d1");
        AgentBase d2 = new FakeAgent("d2");
        AgentBase judge = new FakeAgent("judge");

        DebatePipeline pipeline = new DebatePipeline(List.of(d1, d2), judge);

        Msg question = Msg.builder().build();
        List<Msg> viewpoints = List.of(textMsg("viewpoint"));

        Msg result = pipeline.buildJudgeInput(question, viewpoints);
        String text = extractText(result);

        assertTrue(text.contains("待评估的提案"));
        assertTrue(text.contains("最终裁决"));
    }

    /**
     * Minimal AgentBase stub for testing.
     * Returns a fixed text message when called.
     */
    static class FakeAgent extends AgentBase {

        private final String responseText;

        FakeAgent(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public reactor.core.publisher.Mono<Msg> call(Msg msg) {
            return reactor.core.publisher.Mono.just(textMsg(responseText));
        }

        @Override
        public reactor.core.publisher.Mono<Msg> call(Msg msg, Class<?> structuredOutputClass) {
            return call(msg);
        }
    }
}
```

Note: The `FakeAgent` inner class extends `AgentBase` and returns a fixed response via `call()`. This lets us test `buildJudgeInput()` and constructor validation without mocking framework internals. The `execute()` method itself can't be easily unit-tested without a full AgentScope model setup, so we test the decomposition logic (`buildJudgeInput`) and validation instead.

- [ ] **Step 2: Run tests**

Run: `mvn -Dtest=DebatePipelineTest test -q`
Expected: PASS (5 tests)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/skloda/agentscope/composite/DebatePipelineTest.java
git commit -m "test: add DebatePipeline unit tests for validation and judge input building"
```

---

### Task 4: Add createDebateAgent() to CompositeAgentFactory

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] **Step 1: Add the createDebateAgent method**

In `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`, add this method after the existing `createParallelAgent()` method (after line 149):

```java
    /**
     * Create a debate pipeline: first N-1 sub-agents run as debaters in parallel,
     * the last sub-agent is the judge that receives all viewpoints and synthesizes a verdict.
     */
    public DebatePipeline createDebateAgent(AgentConfig config, Memory memory) {
        if (config.getSubAgents() == null || config.getSubAgents().isEmpty()) {
            throw new IllegalArgumentException("DEBATE agent requires at least one sub-agent: " + config.getAgentId());
        }

        if (config.getSubAgents().size() < 3) {
            throw new IllegalArgumentException("DEBATE agent requires at least 3 sub-agents (2+ debaters + 1 judge): " + config.getAgentId());
        }

        log.info("Creating DEBATE pipeline for: {} with {} sub-agents",
                config.getAgentId(), config.getSubAgents().size());

        List<AgentBase> subAgents = (memory != null)
                ? createSubAgentsWithMemory(config, memory)
                : createSubAgents(config);

        List<AgentBase> debaters = subAgents.subList(0, subAgents.size() - 1);
        AgentBase judge = subAgents.get(subAgents.size() - 1);

        return new DebatePipeline(debaters, judge);
    }
```

Also add the import at the top of the file if not already present (it won't be — it's a new class in the same package, so no import needed).

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java
git commit -m "feat: add createDebateAgent() to CompositeAgentFactory"
```

---

### Task 5: Add debate tests to CompositeAgentFactoryTest

**Files:**
- Modify: `src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java`

- [ ] **Step 1: Add debate agent creation tests**

Append these tests to the end of `src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java`, before the closing brace of the class:

```java
    // --- Debate agent tests (P1) ---

    @Test
    void testCreateDebateAgentRequiresSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("debate-agent");
        config.setSubAgents(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> factory.createDebateAgent(config, null));
    }

    @Test
    void testCreateDebateAgentWithNullSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("debate-agent");
        config.setSubAgents(null);

        assertThrows(IllegalArgumentException.class,
                () -> factory.createDebateAgent(config, null));
    }

    @Test
    void testCreateDebateAgentRequiresAtLeastThreeSubAgents() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("debate-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("sub-1").build(),
                SubAgentConfig.builder().agentId("sub-2").build()
        ));

        assertThrows(IllegalArgumentException.class,
                () -> factory.createDebateAgent(config, null));
    }

    @Test
    void testCreateDebateAgentCreatesPipeline() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("debate-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("debater-1").description("First debater").build(),
                SubAgentConfig.builder().agentId("debater-2").description("Second debater").build(),
                SubAgentConfig.builder().agentId("judge").description("Judge").build()
        ));

        ReActAgent mockD1 = mock(ReActAgent.class);
        ReActAgent mockD2 = mock(ReActAgent.class);
        ReActAgent mockJudge = mock(ReActAgent.class);
        when(singleAgentFactory.createAgent("debater-1")).thenReturn(mockD1);
        when(singleAgentFactory.createAgent("debater-2")).thenReturn(mockD2);
        when(singleAgentFactory.createAgent("judge")).thenReturn(mockJudge);

        DebatePipeline pipeline = factory.createDebateAgent(config, null);

        assertNotNull(pipeline);
        verify(singleAgentFactory).createAgent("debater-1");
        verify(singleAgentFactory).createAgent("debater-2");
        verify(singleAgentFactory).createAgent("judge");
    }

    @Test
    void testCreateDebateAgentWithSharedMemory() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("debate-agent");
        config.setSubAgents(List.of(
                SubAgentConfig.builder().agentId("debater-1").build(),
                SubAgentConfig.builder().agentId("debater-2").build(),
                SubAgentConfig.builder().agentId("judge").build()
        ));

        when(singleAgentFactory.createAgentForSession("debater-1", mockMemory)).thenReturn(mockAgent);
        when(singleAgentFactory.createAgentForSession("debater-2", mockMemory)).thenReturn(mockAgent);
        when(singleAgentFactory.createAgentForSession("judge", mockMemory)).thenReturn(mockAgent);

        DebatePipeline pipeline = factory.createDebateAgent(config, mockMemory);

        assertNotNull(pipeline);
        verify(singleAgentFactory).createAgentForSession("debater-1", mockMemory);
        verify(singleAgentFactory).createAgentForSession("debater-2", mockMemory);
        verify(singleAgentFactory).createAgentForSession("judge", mockMemory);
    }
```

Also add the import for `DebatePipeline` at the top of the file:

```java
import com.skloda.agentscope.composite.DebatePipeline;
```

- [ ] **Step 2: Run tests**

Run: `mvn -Dtest=CompositeAgentFactoryTest test -q`
Expected: PASS (all tests including the 5 new debate tests)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/skloda/agentscope/composite/CompositeAgentFactoryTest.java
git commit -m "test: add debate agent creation tests to CompositeAgentFactoryTest"
```

---

### Task 6: Add DEBATE dispatch to AgentRuntimeFactory

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java`

- [ ] **Step 1: Add DEBATE dispatch methods**

In `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java`, make three changes:

**Change 1**: Add import for `DebatePipeline` after the existing imports (around line 8):

```java
import com.skloda.agentscope.composite.DebatePipeline;
```

**Change 2**: Add `case DEBATE` to the `createRuntime()` switch (line 53, before the `};`):

The switch at line 47-53 should become:

```java
        return switch (type) {
            case SINGLE -> createSingleRuntime(agentId);
            case SEQUENTIAL -> createSequentialRuntime(agentId);
            case PARALLEL -> createParallelRuntime(agentId);
            case ROUTING -> createRoutingRuntime(agentId);
            case HANDOFFS -> createHandoffsRuntime(agentId);
            case DEBATE -> createDebateRuntime(agentId);
        };
```

**Change 3**: Add `case DEBATE` to the `createRuntimeWithMemory()` switch (line 71, before the `};`):

The switch at line 65-71 should become:

```java
        return switch (type) {
            case SINGLE -> createSingleRuntimeWithMemory(agentId, memory);
            case SEQUENTIAL -> createSequentialRuntimeWithMemory(agentId, memory);
            case PARALLEL -> createParallelRuntimeWithMemory(agentId, memory);
            case ROUTING -> createRoutingRuntimeWithMemory(agentId, memory);
            case HANDOFFS -> createHandoffsRuntimeWithMemory(agentId, memory);
            case DEBATE -> createDebateRuntimeWithMemory(agentId, memory);
        };
```

**Change 4**: Add two private methods at the end of the class (before the closing brace), after the existing `createSingleRuntimeWithMemory()` method:

```java
    private PipelineAgentRuntime createDebateRuntime(String agentId) {
        log.debug("Creating DebateRuntime for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        DebatePipeline pipeline = compositeFactory.createDebateAgent(config, null);

        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }

    private PipelineAgentRuntime createDebateRuntimeWithMemory(String agentId, Memory memory) {
        log.debug("Creating DebateRuntime with shared memory for agent: {}", agentId);

        AgentConfig config = configService.getAgentConfig(agentId);
        ObservabilityHook hook = new ObservabilityHook();
        DebatePipeline pipeline = compositeFactory.createDebateAgent(config, memory);

        return new PipelineAgentRuntime(config.getAgentId(), pipeline, hook);
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java
git commit -m "feat: add DEBATE dispatch to AgentRuntimeFactory"
```

---

### Task 7: Add DEBATE dispatch tests to AgentRuntimeFactoryTest

**Files:**
- Modify: `src/test/java/com/skloda/agentscope/runtime/AgentRuntimeFactoryTest.java`

- [ ] **Step 1: Add DEBATE dispatch tests**

Append these tests to the end of `src/test/java/com/skloda/agentscope/runtime/AgentRuntimeFactoryTest.java`, before the closing brace:

```java
    @Test
    void createRuntimeReturnsPipelineRuntimeForDebateAgent() {
        AgentConfig config = config("debate-review", AgentType.DEBATE);
        when(configService.getAgentConfig("debate-review")).thenReturn(config);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntime("debate-review");

        assertInstanceOf(PipelineAgentRuntime.class, runtime);
        verify(compositeFactory).createDebateAgent(config, null);
    }

    @Test
    void createRuntimeWithMemoryReturnsPipelineRuntimeForDebateAgent() {
        AgentConfig config = config("debate-review", AgentType.DEBATE);
        when(configService.getAgentConfig("debate-review")).thenReturn(config);

        StreamingAgentRuntime runtime = runtimeFactory.createRuntimeWithMemory("debate-review", memory);

        assertInstanceOf(PipelineAgentRuntime.class, runtime);
        verify(compositeFactory).createDebateAgent(config, memory);
    }
```

Also add this import at the top if not present:

```java
import static org.mockito.ArgumentMatchers.isNull;
```

- [ ] **Step 2: Run tests**

Run: `mvn -Dtest=AgentRuntimeFactoryTest test -q`
Expected: PASS (all tests including the 2 new DEBATE tests)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/skloda/agentscope/runtime/AgentRuntimeFactoryTest.java
git commit -m "test: add DEBATE dispatch tests to AgentRuntimeFactoryTest"
```

---

### Task 8: Run full test suite to verify backend changes

- [ ] **Step 1: Run all tests**

Run: `mvn test -q`
Expected: BUILD SUCCESS, 0 failures, 0 errors

- [ ] **Step 2: Fix any failures if needed, then commit**

```bash
git add -A
git commit -m "fix: address test failures from DEBATE backend changes"
```

(Only if there are failures to fix.)

---

### Task 9: Add 4 debate expert agents to agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add debate expert agents**

In `src/main/resources/config/agents.yml`, add these 4 agents at the end of the expert agents section — after `complaint-agent` (the last expert) and before the `# === Multi-Agent: Sequential Pipeline ===` comment (around line 535). Insert before the `# === Multi-Agent:` line:

```yaml

  - agentId: debate-optimist
    category: expert
    name: 乐观派分析师
    description: 从积极角度分析提案的优势和机会
    systemPrompt: |
      你是一个乐观派分析师。你的职责是从积极的角度分析提案。
      总是寻找提案中的亮点、机会和潜在收益。
      提出建设性的改进建议，但基调是支持和鼓励。
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    samplePrompts:
      - prompt: "分析这个方案：引入 AI Agent 替代人工客服处理 80% 的常见问题"
        expectedBehavior: "从效率提升、成本节约、用户体验改善等角度给出乐观分析"

  - agentId: debate-critic
    category: expert
    name: 批评家
    description: 从风险角度分析提案的问题和隐患
    systemPrompt: |
      你是一个批评家。你的职责是从风险和问题的角度审视提案。
      指出潜在隐患、实施风险、成本超支可能性和失败模式。
      态度要犀利但基于事实，不是为了反对而反对。
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    samplePrompts:
      - prompt: "分析这个方案：引入 AI Agent 替代人工客服处理 80% 的常见问题"
        expectedBehavior: "从技术风险、用户满意度下降、边界 case 等角度给出批判分析"

  - agentId: debate-analyst
    category: expert
    name: 数据分析师
    description: 从数据和事实角度客观分析
    systemPrompt: |
      你是一个数据分析师。你的职责是从客观数据和事实的角度分析提案。
      关注数据支撑、量化指标、行业基准和可衡量的结果。
      避免主观判断，用数据说话。
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    samplePrompts:
      - prompt: "分析这个方案：引入 AI Agent 替代人工客服处理 80% 的常见问题"
        expectedBehavior: "引用行业数据、ROI 计算和量化指标进行客观分析"

  - agentId: debate-judge
    category: expert
    name: 裁判
    description: 综合所有专家观点，做出最终裁决
    systemPrompt: |
      你是一个公正的裁判。你的任务是综合多位专家的观点，做出最终裁决。

      ## 你的职责
      1. 仔细阅读每位专家的观点
      2. 找出共识和分歧点
      3. 评估各方论据的合理性
      4. 给出综合裁决和行动建议

      ## 输出格式
      ### 各方观点摘要
      - 乐观派：[核心观点]
      - 批评家：[核心观点]
      - 数据分析师：[核心观点]

      ### 共识点
      [列出各方都认同的要点]

      ### 分歧点
      [列出各方意见不同的要点]

      ### 最终裁决
      [你的综合判断和建议]
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    samplePrompts:
      - prompt: "请对以下三个专家的观点做出最终裁决"
        expectedBehavior: "综合所有观点，给出结构化的裁决报告"
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add 4 debate expert agents to agents.yml"
```

---

### Task 10: Add super-supervisor and debate-review agents to agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add debate-review and super-supervisor to the collaboration section**

In `src/main/resources/config/agents.yml`, add these 2 agents at the end of the file, after the existing `customer-service` agent (after the last `handoffTriggers` and `samplePrompts` of customer-service):

```yaml

# === Multi-Agent: Supervisor ===

  - agentId: super-supervisor
    category: collaboration
    type: ROUTING
    name: 超级主管
    description: 综合调度多个专家，协同完成复杂任务
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    systemPrompt: |
      你是一个超级主管 Agent，负责协调多个专家子 Agent 共同完成复杂任务。

      ## 核心能力
      你可以按需调用以下专家 Agent，一个任务中可以连续调用多个专家：
      - doc-expert：文档解析和分析
      - search-expert：实时网络搜索
      - vision-expert：图片理解和 OCR
      - sales-expert：产品咨询和报价

      ## 工作原则
      1. 分析用户请求，判断需要哪些专家协助
      2. 按顺序调用合适的专家（一次调用一个）
      3. 每次调用后，根据专家返回的结果决定是否需要继续调用其他专家
      4. 所有专家完成后，综合所有结果给出完整回答
      5. 回答中要说明调用了哪些专家以及各自的贡献

      ## 示例场景
      - "分析这份文档并搜索行业动态" → 调用 doc-expert → 调用 search-expert → 综合回答
      - "识别图片中的文字并翻译" → 调用 vision-expert → 综合回答
      - "查一下竞品定价并给出销售建议" → 调用 search-expert → 调用 sales-expert → 综合回答
    subAgents:
      - agentId: doc-expert
        description: 文档解析和分析专家，处理文档解析、内容提取和结构化分析
      - agentId: search-expert
        description: 搜索专家，获取实时网络信息、新闻、天气等
      - agentId: vision-expert
        description: 视觉专家，处理图片理解、OCR 文字识别、图表分析
      - agentId: sales-expert
        description: 销售专家，处理产品咨询、报价和购买建议
    samplePrompts:
      - prompt: "请帮我分析这份合同的风险点，并搜索相关法律法规作为参考"
        expectedBehavior: "先调用文档专家解析合同，再调用搜索专家查找法规，最后综合给出风险评估"
      - prompt: "识别这张发票图片的信息，然后搜索该供应商的最新动态"
        expectedBehavior: "调用视觉专家提取发票信息，调用搜索专家查询供应商，综合回答"
      - prompt: "帮我调研一下 AI Agent 市场的主要玩家，并给出销售建议"
        expectedBehavior: "调用搜索专家获取市场信息，调用销售专家给出建议"

# === Multi-Agent: Debate ===

  - agentId: debate-review
    category: collaboration
    type: DEBATE
    name: 专家辩论评审
    description: 多专家并行辩论，Judge 综合裁决
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    subAgents:
      - agentId: debate-optimist
        description: 从积极角度分析提案
      - agentId: debate-critic
        description: 从风险角度审视提案
      - agentId: debate-analyst
        description: 从数据角度客观分析
      - agentId: debate-judge
        description: 综合所有观点做出裁决
    samplePrompts:
      - prompt: "评估方案：公司全面采用远程办公模式"
        expectedBehavior: "三位专家并行分析，裁判综合裁决"
      - prompt: "分析提案：投入 200 万开发内部 AI 助手，预期 6 个月回本"
        expectedBehavior: "乐观/批评/数据三角度分析，裁判给出 ROI 判断"
      - prompt: "评估策略：放弃现有客户管理系统，迁移到新平台"
        expectedBehavior: "多角度风险收益分析，裁判给出迁移建议"
```

- [ ] **Step 2: Verify app starts**

Run: `mvn spring-boot:run` (then Ctrl+C after "Loaded ... agent configurations")
Expected: No YAML parse errors, log shows increased agent count

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add super-supervisor and debate-review collaboration agents"
```

---

### Task 11: Enhance Customer Service prompts

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Update customer-service systemPrompt**

Find the `customer-service` agent block (search for `agentId: customer-service`). Replace its existing `systemPrompt` with:

```yaml
    systemPrompt: |
      你是一个智能客服协调器。你的职责是准确理解用户意图，将请求转交给最合适的子代理。

      ## 子代理能力
      - support-agent：一般咨询、使用指导、常见问题
      - sales-agent：产品价格、购买方案、销售咨询
      - complaint-agent：投诉受理、问题升级、赔偿处理

      ## 转交规则
      - 包含"购买"、"价格"、"报价"、"套餐" → 转交 sales-agent
      - 包含"投诉"、"不满"、"退款"、"升级" → 转交 complaint-agent
      - 其他一般性问题 → 由 support-agent 处理

      ## 注意事项
      - 转交时简要说明原因，让用户知道正在切换到哪个专员
      - 保持友好专业的语气
      - 如果用户意图不明确，先由 support-agent 尝试处理
```

- [ ] **Step 2: Update customer-service samplePrompts**

Replace the existing `samplePrompts` of `customer-service` with:

```yaml
    samplePrompts:
      - prompt: "你好，我想咨询一下产品价格"
        expectedBehavior: "识别购买意图，自动切换到销售顾问"
      - prompt: "我对你们的服务很不满意，要投诉"
        expectedBehavior: "识别投诉意图，自动切换到投诉处理专员"
      - prompt: "请问你们的营业时间是什么时候？"
        expectedBehavior: "由一般客服回答常见问题"
      - prompt: "转人工客服"
        expectedBehavior: "显式切换到人工客服"
      - prompt: "我已经提交了投诉但没收到回复，帮我跟进一下"
        expectedBehavior: "识别升级需求，切换到投诉处理并主动跟进"
      - prompt: "我想先了解产品功能，再看价格，最后决定是否购买"
        expectedBehavior: "多轮对话中按需在不同专员间切换"
```

- [ ] **Step 3: Update support-agent systemPrompt**

Find `agentId: support-agent`. Replace its `systemPrompt` with:

```yaml
    systemPrompt: |
      你是一个客服专员。你可以回答一般性问题，帮助用户解决问题。

      ## 工作流程
      1. 确认用户的问题或需求
      2. 提供清晰、分步骤的解决方案
      3. 确认问题是否已解决
      4. 如无法解决，建议用户转接其他专员

      ## 常见问题范围
      - 产品功能说明
      - 使用指导和操作步骤
      - 文件上传和 Agent 使用帮助
      - 系统功能概览
```

- [ ] **Step 4: Update sales-agent systemPrompt**

Find `agentId: sales-agent`. Replace its `systemPrompt` with:

```yaml
    systemPrompt: |
      你是一个销售顾问。你可以提供产品咨询、报价和购买建议。

      ## 咨询流程
      1. 了解客户需求和团队规模
      2. 推荐适合的产品方案
      3. 提供报价和购买渠道
      4. 解答购买相关的疑问

      ## 方案推荐原则
      - 小团队（1-10 人）推荐基础版
      - 中型团队（10-50 人）推荐专业版
      - 企业客户（50+ 人）推荐企业版
      - 根据实际需求灵活推荐，不要过度推销
```

- [ ] **Step 5: Update complaint-agent systemPrompt**

Find `agentId: complaint-agent`. Replace its `systemPrompt` with:

```yaml
    systemPrompt: |
      你是一个投诉处理专员。你可以倾听客户的投诉，并提供解决方案。

      ## 处理流程
      1. 倾听并确认客户的投诉内容
      2. 对客户的不满表示理解和歉意
      3. 分析问题原因
      4. 提供具体的解决方案或补偿
      5. 确认客户是否接受方案

      ## 升级条件
      - 涉及金额较大（>10000 元）
      - 客户要求法律途径解决
      - 重复投诉 3 次以上未解决
      → 建议客户升级到高级投诉处理
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: enhance customer-service agent prompts and sample prompts"
```

---

### Task 12: Enhance Doc Pipeline prompts

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Update doc-analysis-pipeline systemPrompt**

Find `agentId: doc-analysis-pipeline`. Replace its `systemPrompt` with:

```yaml
    systemPrompt: |
      你是一个文档研究流水线。你会按顺序执行两个步骤：
      1. 文档专家先解析文档内容，提取关键信息
      2. 搜索专家根据提取的信息，搜索补充资料

      最终输出应整合文档内容和搜索结果，给出全面的分析报告。
```

Note: If the current config doesn't have a `systemPrompt` for `doc-analysis-pipeline`, add it after `enableThinking: true`.

- [ ] **Step 2: Update doc-expert systemPrompt**

Find `agentId: doc-expert`. Replace its `systemPrompt` with:

```yaml
    systemPrompt: |
      你是一个文档分析专家。你可以解析 .docx、.pdf、.xlsx 文件，提取关键信息并进行分析。
      当收到文档相关问题时，使用你的工具解析文档内容。

      ## 输出格式
      1. 文档概要（一句话）
      2. 关键发现（3-5 条）
      3. 风险点（如有）
      4. 建议行动（如有）
```

- [ ] **Step 3: Update search-expert systemPrompt**

Find `agentId: search-expert`. Replace its `systemPrompt` with:

```yaml
    systemPrompt: |
      你是一个搜索专家。你可以搜索网络获取最新信息，包括新闻、天气、股票等。

      ## 在流水线中工作时
      你会收到文档专家的分析结果。请基于这些信息搜索补充资料，
      找到文档中未覆盖的相关信息、最新动态或行业对比数据。

      ## 输出格式
      1. 搜索来源列表
      2. 补充信息摘要
      3. 与文档内容的关联分析
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: enhance doc-pipeline agent prompts with structured output format"
```

---

### Task 13: Run full test suite and verify

- [ ] **Step 1: Run all tests**

Run: `mvn test -q`
Expected: BUILD SUCCESS, 0 failures, 0 errors

- [ ] **Step 2: Verify app starts with all agents**

Run: `mvn spring-boot:run` (Ctrl+C after "Started AgentScopeDemoApplication")
Expected: Log shows "Loaded 25 agent configurations" (19 existing + 4 debate experts + 2 new collaboration agents = 25)

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: address issues from full verification"
```

---

### Task 14: Update ROADMAP.md

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Update Current TODO section**

In `docs/ROADMAP.md`, update the Current TODO section. Change:

```markdown
- [ ] Add P1 showcase demos (supervisor agent, debate/review, etc.).
```

To:

```markdown
- [x] Add P1 showcase demos (supervisor agent, debate/review, etc.).
```

- [ ] **Step 2: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: mark P1 multi-agent showcase as complete in ROADMAP"
```

---

## Self-Review

**1. Spec coverage:**
- Section 1 (Supervisor): Task 10 (YAML config) — covered
- Section 2 (Debate): Tasks 1-7 (backend), Task 9 (debate experts YAML), Task 10 (debate-review YAML) — covered
- Section 3 (Customer Service): Task 11 — covered
- Section 4 (Doc Pipeline): Task 12 — covered
- Section 5 (Debug panel): No code changes needed per spec ("no new SSE event types for P1") — covered
- Section 6 (File summary): All files listed — covered
- Section 7 (Success criteria): Task 13 verifies — covered

**2. Placeholder scan:** No TBDs, TODOs, or vague instructions found. All steps contain complete code.

**3. Type consistency:**
- `DebatePipeline implements Pipeline<Msg>` — matches `PipelineAgentRuntime` constructor which accepts `Pipeline<?>`
- `CompositeAgentFactory.createDebateAgent()` returns `DebatePipeline` — matches `AgentRuntimeFactory` usage
- `AgentType.DEBATE` — consistent across all switch expressions and tests
- All YAML agentIds match between subAgents references and actual agent definitions
