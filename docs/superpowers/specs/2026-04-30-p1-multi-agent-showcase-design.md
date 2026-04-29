# P1: Multi-agent Showcase Design

**Date**: 2026-04-30
**Status**: Draft
**Phase**: P1 — Multi-agent Showcase (from ROADMAP.md)

## Goal

Add Supervisor agent and Debate/Review demo as new multi-agent collaboration patterns, enhance existing Customer Service and Doc Pipeline showcases with better prompts and debug panel events, so users can understand the difference between pipeline, routing, handoffs, supervisor, and debate from the UI alone.

## Scope

| Item | Type | Backend Code | Config Only |
|------|------|-------------|-------------|
| Supervisor agent | New agent | No | Yes (ROUTING reuse) |
| Debate/Review demo | New agent + new type | Yes (~80 lines) | No |
| 4 debate expert agents | New agents | No | Yes |
| Customer Service enhancement | Prompt tuning | No | Yes |
| Doc Pipeline enhancement | Prompt tuning | No | Yes |
| Debug panel events | Frontend + hook | Yes (events) | No |

---

## 1. Supervisor Agent

**Agent ID**: `super-supervisor`
**Category**: `collaboration`
**Type**: `ROUTING` (reuses existing routing infrastructure)

### Architecture

Reuses the existing ROUTING pattern in `CompositeAgentFactory.createRoutingAgent()`. The Supervisor's system prompt instructs it to call **multiple sub-agent tools in sequence** for a single user request. AgentScope's ReAct agent natively supports multi-round tool calls, so the Supervisor can:

1. Call `doc-expert` to parse a document
2. Take the result, call `search-expert` to find supplementary information
3. Synthesize both results into a comprehensive answer

### Configuration

```yaml
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
```

### Implementation notes

- No Java code changes. Pure YAML + prompt design.
- Debug panel already shows sequential `tool_start`/`tool_end` events for each sub-agent call via the existing ObservabilityHook.

---

## 2. Debate/Review Demo

**Agent ID**: `debate-review`
**Category**: `collaboration`
**Type**: `DEBATE` (new enum value)

### Architecture

```
User question
    │
    ├─→ debate-optimist ─→ optimistic view ─┐
    ├─→ debate-critic   ─→ critical view  ─┼─→ debate-judge → final verdict
    └─→ debate-analyst  ─→ data analysis  ─┘
```

**Two phases:**
1. **Parallel debate phase**: First N-1 sub-agents execute in parallel via FanoutPipeline, each producing an independent viewpoint
2. **Judgment phase**: The last sub-agent (Judge) receives all viewpoints and produces a synthesized verdict

### Backend changes

**Key constraint**: AgentScope's `FanoutPipeline` implements `Pipeline<List<Msg>>` (not `AgentBase`), so it **cannot** be nested inside `SequentialPipeline.addAgents()` which requires `List<AgentBase>`. Instead, we create a custom `DebatePipeline` class that implements `Pipeline<Msg>` and handles both phases internally.

#### 2.1 `AgentType.java` — add DEBATE enum

```java
SINGLE, SEQUENTIAL, PARALLEL, ROUTING, HANDOFFS, DEBATE
```

#### 2.2 New class: `DebatePipeline.java` in `composite/` package

A custom Pipeline implementation that orchestrates parallel debate + judge synthesis. Fits the existing `PipelineAgentRuntime` which calls `pipeline.execute(userMsg).block()` and checks `result instanceof Msg`.

```java
package com.skloda.agentscope.composite;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.*;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

public class DebatePipeline implements Pipeline<Msg> {
    private final List<AgentBase> debaters;
    private final AgentBase judge;

    public DebatePipeline(List<AgentBase> debaters, AgentBase judge) {
        if (debaters.size() < 2) {
            throw new IllegalArgumentException("DEBATE requires at least 2 debaters + 1 judge");
        }
        this.debaters = debaters;
        this.judge = judge;
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
                    // Aggregate all viewpoints into a single message for the judge
                    Msg judgeInput = buildJudgeInput(input, viewpoints);
                    // Phase 2: Judge synthesizes
                    return judge.call(judgeInput);
                });
    }

    private Msg buildJudgeInput(Msg originalQuestion, List<Msg> viewpoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 待评估的提案\n\n");

        // Extract original question text
        for (ContentBlock block : originalQuestion.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText()).append("\n\n");
            }
        }

        sb.append("## 各位专家的观点\n\n");
        for (int i = 0; i < viewpoints.size(); i++) {
            sb.append("### 专家 ").append(i + 1).append(" 的观点\n\n");
            for (ContentBlock block : viewpoints.get(i).getContent()) {
                if (block instanceof TextBlock tb) {
                    sb.append(tb.getText()).append("\n\n");
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

#### 2.3 `CompositeAgentFactory.java` — add `createDebateAgent()`

```java
public DebatePipeline createDebateAgent(AgentConfig config, Memory memory) {
    List<AgentBase> subAgents = (memory != null)
            ? createSubAgentsWithMemory(config, memory)
            : createSubAgents(config);

    if (subAgents.size() < 3) {
        throw new IllegalArgumentException("DEBATE requires at least 3 sub-agents (2+ debaters + 1 judge)");
    }

    // Last sub-agent is the judge, rest are debaters
    List<AgentBase> debaters = subAgents.subList(0, subAgents.size() - 1);
    AgentBase judge = subAgents.get(subAgents.size() - 1);

    return new DebatePipeline(debaters, judge);
}
```

#### 2.4 `AgentRuntimeFactory.java` — add DEBATE dispatch

Add `case DEBATE` branches in both `createRuntime()` and `createRuntimeWithMemory()` switch expressions. Returns `PipelineAgentRuntime` wrapping the `DebatePipeline` (since `DebatePipeline implements Pipeline<Msg>`, it fits the existing `PipelineAgentRuntime(config.getAgentId(), debatePipeline, hook)` constructor).

#### 2.5 Debate events in `PipelineAgentRuntime`

No new SSE event types needed for the initial implementation. The existing `pipeline_start` / `pipeline_end` events work. The judge's final response streams as normal `text` events. If we want more granular events (debate_start, debate_judgment), that would be an enhancement in `DebatePipeline.execute()` via callbacks — deferred to post-P1.

### New expert agents for debate

Four new agents in `agents.yml`, all with `category: expert`:

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

### Debate agent configuration

```yaml
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

---

## 3. Customer Service Enhancement

Only changes to `agents.yml` prompts. No code changes.

### customer-service system prompt

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

### Sub-agent prompt refinements

**support-agent**: Add structured troubleshooting steps and product knowledge context.

**sales-agent**: Add pricing tiers reference and consultative selling guidelines.

**complaint-agent**: Add de-escalation framework and escalation criteria.

### New sample prompts

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

---

## 4. Doc Pipeline Enhancement

Only changes to `agents.yml` prompts. No code changes.

### doc-analysis-pipeline system prompt

```yaml
systemPrompt: |
  你是一个文档研究流水线。你会按顺序执行两个步骤：
  1. 文档专家先解析文档内容，提取关键信息
  2. 搜索专家根据提取的信息，搜索补充资料

  最终输出应整合文档内容和搜索结果，给出全面的分析报告。
```

### doc-expert prompt refinement

Add explicit output format:

```yaml
systemPrompt: |
  你是一个文档分析专家。你可以解析 .docx、.pdf、.xlsx 文件。

  ## 输出格式
  1. 文档概要（一句话）
  2. 关键发现（3-5 条）
  3. 风险点（如有）
  4. 建议行动（如有）
```

### search-expert prompt refinement

Add context awareness for pipeline use:

```yaml
systemPrompt: |
  你是一个搜索专家。你可以搜索网络获取最新信息。

  ## 在流水线中工作时
  你会收到文档专家的分析结果。请基于这些信息搜索补充资料，
  找到文档中未覆盖的相关信息、最新动态或行业对比数据。

  ## 输出格式
  1. 搜索来源列表
  2. 补充信息摘要
  3. 与文档内容的关联分析
```

### New sample prompts

```yaml
samplePrompts:
  - prompt: "请分析这个文档，并搜索相关的最新信息"
    expectedBehavior: "文档专家先解析文档，然后搜索专家补充实时信息"
  - prompt: "帮我总结这份报告的主要内容，并查找相关行业动态"
    expectedBehavior: "先提取文档摘要，再搜索行业最新动态"
  - prompt: "分析这个合同的关键条款，并搜索相关法律条文作为参考"
    expectedBehavior: "解析合同条款，然后搜索法律背景信息"
```

---

## 5. Debug Panel Multi-agent Event Enhancement

### Supervisor multi-call display

Supervisor reuses ROUTING events (`tool_start`/`tool_end` for each sub-agent call). No new events needed. The debug panel already shows sequential tool calls with sub-agent names via ObservabilityHook.

### Debate display

Debate uses the existing `pipeline_start` / `pipeline_end` events from `PipelineAgentRuntime`. The judge's final response streams as `text` events. No new SSE event types for P1 — the debug panel shows a single pipeline execution with the judge's verdict.

### Frontend changes in `debug.js`

Minor: add `pipeline_start` / `pipeline_end` event display styling for the debate pipeline to distinguish it from regular sequential pipelines. Low priority for P1.

---

## 6. File Change Summary

| File | Action | Lines |
|------|--------|-------|
| `AgentType.java` | Add `DEBATE` enum value | 1 line |
| `DebatePipeline.java` | **New file** — custom Pipeline for debate pattern | ~60 lines |
| `CompositeAgentFactory.java` | Add `createDebateAgent()` method | ~15 lines |
| `AgentRuntimeFactory.java` | Add DEBATE dispatch cases | ~20 lines |
| `agents.yml` | Add 5 new agents + update 5 existing agents + prompts | ~200 lines YAML |

Total: ~5 files changed, ~1 new file, ~300 lines of new/modified code.

---

## 7. Success Criteria

- User can understand the difference between pipeline, routing, handoffs, supervisor, and debate from the UI alone
- Supervisor agent can call multiple sub-agents in sequence for a single request
- Debate demo shows parallel viewpoints from 3 experts + judge synthesis
- Customer Service and Doc Pipeline have improved prompts that produce more structured responses
- Debug panel shows clear event flow for all multi-agent patterns
- `mvn test` passes with 0 failures
- All new agents appear in the categorized accordion menu under the correct groups
