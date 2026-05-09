# P6: Advanced Multi-Agent Patterns Design

## Overview

P6 adds 5 advanced multi-agent patterns to the demo, showcasing AgentScope's full multi-agent capabilities: Loop Pipeline, StateGraph, MsgHub, and two Subagents patterns (sequential handoff + parallel delegation).

Implementation order: Loop Pipeline → StateGraph → MsgHub → Subagents Sequential → Subagents Parallel.

## Architecture

### New AgentType Enums

Add 5 new types to `AgentType`:

```
LOOP, STATE_GRAPH, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR
```

### Directory Structure

```
composite/
├── CompositeAgentFactory.java        (assembly dispatch only, no business logic)
├── pipeline/
│   ├── LoopPipeline.java             (write-review-revise loop)
│   ├── RoundTablePipeline.java       (expert roundtable discussion)
│   ├── TaskOrchestratorPipeline.java (sequential task handoff)
│   └── TaskDispatcherPipeline.java   (parallel task delegation)
└── graph/
    └── OrderFulfillmentGraph.java    (order fulfillment state graph)

runtime/
├── AgentRuntime.java                 (existing: SINGLE/ROUTING/HANDOFFS/STATE_GRAPH)
├── PipelineAgentRuntime.java         (existing, extended: LOOP/SUBAGENT_SEQ/SUBAGENT_PAR)
└── MsgHubRuntime.java                (new: MsgHub group chat)
```

### Runtime Mapping

| AgentType | Runtime | Notes |
|-----------|---------|-------|
| LOOP | PipelineAgentRuntime | Wraps LoopPipeline |
| STATE_GRAPH | AgentRuntime | StateGraph is a special ReActAgent |
| MSG_HUB | MsgHubRuntime | New runtime for group conversation |
| SUBAGENT_SEQ | PipelineAgentRuntime | Wraps TaskOrchestratorPipeline |
| SUBAGENT_PAR | PipelineAgentRuntime | Wraps TaskDispatcherPipeline |

### Design Principles

- Every Pipeline class implements `Flux<Map<String, Object>> stream(Msg input)`
- `CompositeAgentFactory` only reads config, creates agent instances, delegates to Pipeline builders
- All patterns reuse the existing `ObservabilityHook` event system with new event types
- Each sub-agent in Subagents patterns has its own toolkit (fixing the current ROUTING limitation)

---

## Pattern 1: Loop Pipeline — Copywriting Refinement

### Flow

```
User → Writer(write draft) → Critic(review) → Quality Check
                                                    ├─ PASS → Output final
                                                    └─ FAIL → Writer(revise) → Critic → ...
                                                          ↑___________________↓
```

### Configuration

```yaml
agentId: copywriter-refiner
type: LOOP
loopConfig:
  maxIterations: 3
  exitCondition: AUTO        # AUTO=LLM judges / FIXED=always max iterations
subAgents:
  - agentId: writer
    role: WRITER
  - agentId: critic
    role: CRITIC
```

### Implementation (LoopPipeline.java)

- Receives Writer + Critic agents
- Each iteration: Writer generates/revises → Critic reviews → Critic output contains `APPROVED` or `NEEDS_REVISION`
- `maxIterations` prevents infinite loops
- Critic feedback automatically becomes next iteration's Writer input

### Events

```
pipeline_start
  loop_start { iteration: 1 }
    step_start { agent: writer, role: WRITER }
    step_end
    step_start { agent: critic, role: CRITIC }
    step_end
    loop_iteration_result { approved: false, feedback: "..." }
  loop_start { iteration: 2 }
    ...
  loop_end { totalIterations: 2, finalApproved: true }
pipeline_end
```

---

## Pattern 2: StateGraph — Order Fulfillment

### State Diagram

```
  ┌─────────┐
  │ CREATED │
  └────┬────┘
       ↓ submit
  ┌──────────┐
  │ SUBMITTED │
  └────┬──────┘
       ↓ review
  ┌──────────┐
  │ REVIEWING │
  └──┬───┬───┘
 approve  reject
   ↓        ↓
┌─────────┐ ┌──────────┐
│ APPROVED│ │ REJECTED │
└────┬────┘ └──────────┘
     ↓ pay
┌─────────┐
│ PAYING  │
└──┬───┬──┘
success  fail
  ↓        ↓
┌──────┐ ┌───────────┐
│ PAID │ │ PAY_FAILED│
└──┬───┘ └─────┬─────┘
   ↓ ship      ↓ retry
┌──────────┐   (back to PAYING)
│ SHIPPING │
└──┬───┬───┘
done   lost
  ↓      ↓
┌──────┐ ┌────────────────┐
│ DONE │ │LOST_IN_TRANSIT │
└──────┘ └────────────────┘
```

### Configuration

```yaml
agentId: order-fulfillment
type: STATE_GRAPH
states:
  - name: CREATED
    transitions:
      - event: submit
        target: SUBMITTED
  - name: SUBMITTED
    transitions:
      - event: review
        target: REVIEWING
  - name: REVIEWING
    agent: order-reviewer
    transitions:
      - condition: approved
        target: APPROVED
      - condition: rejected
        target: REJECTED
  - name: APPROVED
    transitions:
      - event: pay
        target: PAYING
  - name: PAYING
    agent: payment-agent
    transitions:
      - condition: success
        target: PAID
      - condition: fail
        target: PAY_FAILED
  - name: PAY_FAILED
    transitions:
      - event: retry
        target: PAYING
  - name: PAID
    transitions:
      - event: ship
        target: SHIPPING
  - name: SHIPPING
    agent: shipping-agent
    transitions:
      - condition: delivered
        target: DONE
      - condition: lost
        target: LOST_IN_TRANSIT
```

### Implementation (OrderFulfillmentGraph.java)

- Uses AgentScope `StateGraph` API to build state machine
- States with `agent`: call the LLM agent to decide which transition to take
- States without `agent`: pure deterministic transition triggered by user input (`event`)
- Agent decision points use system prompts to constrain output format for parseable state transitions

### Interaction

User sends messages like "提交订单" (trigger submit), "支付" (trigger pay). Agents automatically decide outcomes at review/payment/shipping nodes.

### Events

```
pipeline_start
  graph_transition { from: CREATED, to: SUBMITTED, trigger: "submit" }
  graph_transition { from: SUBMITTED, to: REVIEWING, trigger: "review" }
  graph_agent_call { state: REVIEWING, agent: order-reviewer }
  graph_transition { from: REVIEWING, to: APPROVED, condition: "approved" }
  ...
pipeline_end
```

---

## Pattern 3: MsgHub — Expert Roundtable Review

### Flow

```
                  ┌──────────────┐
User(doc/plan) ──→│  Moderator   │
                  │  (host)      │
                  └──┬───┬───┬───┘
                     ↓   ↓   ↓
            ┌─────┐ ┌─────┐ ┌─────┐
            │Arch │ │ DBA │ │ Sec │
            └──┬──┘ └──┬──┘ └──┬──┘
               ↓       ↓       ↓
            Round 1: Each expert speaks
            Round 2: Respond to each other
            Round 3: Reach consensus
               ↓       ↓       ↓
            ┌───────────────────┐
            │ Moderator summary │
            └───────────────────┘
```

### Configuration

```yaml
agentId: expert-roundtable
type: MSG_HUB
msgHubConfig:
  rounds: 3
  summaryRole: MODERATOR
subAgents:
  - agentId: moderator
    role: MODERATOR
  - agentId: architect
    role: EXPERT
  - agentId: dba-expert
    role: EXPERT
  - agentId: security-expert
    role: EXPERT
```

### Implementation (RoundTablePipeline.java)

- Uses AgentScope `MsgHub` to create group conversation context
- Each round: all EXPERT agents speak in parallel (seeing all previous messages)
- After `rounds` rounds of discussion, MODERATOR synthesizes a final review report
- MsgHub ensures every agent sees all historical messages in the group

### MsgHubRuntime.java

- Manages MsgHub lifecycle (create → broadcast → close)
- Each expert's speech emitted as a `round_message` event
- Moderator summary emitted as the final `text` event

### Events

```
pipeline_start
  roundtable_start { participants: [...], rounds: 3 }
  round_start { round: 1 }
    round_message { agent: architect, content: "..." }
    round_message { agent: dba-expert, content: "..." }
    round_message { agent: security-expert, content: "..." }
  round_end { round: 1 }
  round_start { round: 2 }
    ...
  round_end { round: 2 }
  round_start { round: 3 }
    ...
  round_end { round: 3 }
  roundtable_summary { agent: moderator }
pipeline_end
```

---

## Pattern 4a: Subagents Sequential — Report Generation

### Flow

```
User(topic) → Orchestrator
                    ↓
              ┌──────────┐
              │Researcher│ ← task: "Research background on {topic}"
              └────┬─────┘
                   ↓ research report
              ┌──────────┐
              │ Analyst  │ ← task: "Analyze research data: {prevOutput}"
              └────┬─────┘
                   ↓ analysis
              ┌──────────┐
              │ Writer   │ ← task: "Write final report: {prevOutput}"
              └────┬─────┘
                   ↓
              Final Report
```

### Configuration

```yaml
agentId: report-generator
type: SUBAGENT_SEQ
subAgents:
  - agentId: researcher
    role: RESEARCHER
    taskTemplate: "请调研以下主题的背景信息和最新进展：{input}"
  - agentId: analyst
    role: ANALYST
    taskTemplate: "基于以下调研结果进行深度分析：{prevOutput}"
  - agentId: report-writer
    role: WRITER
    taskTemplate: "根据以下分析结果撰写结构化报告：{prevOutput}"
```

### Implementation (TaskOrchestratorPipeline.java)

- Each step creates an independent Task wrapping the rendered `taskTemplate`
- Previous step's `TaskOutput` is injected as `{prevOutput}` variable
- Each sub-agent has its own toolkit (fixing the current ROUTING sub-agent tool limitation)

---

## Pattern 4b: Subagents Parallel — PM Delegation

### Flow

```
User(requirements) → PM Agent
                       ↓ decompose
              ┌────────┼────────┐
              ↓        ↓        ↓
         ┌─────┐  ┌─────┐  ┌─────┐
         │Research│ │Design│ │Evaluate│
         └──┬──┘  └──┬──┘  └──┬──┘
            ↓        ↓        ↓
         TaskOutput TaskOutput TaskOutput
            └────────┼────────┘
                     ↓
              PM Agent aggregates
```

### Configuration

```yaml
agentId: project-manager
type: SUBAGENT_PAR
subAgents:
  - agentId: pm-researcher
    role: RESEARCHER
    taskTemplate: "调研项目的技术可行性和市场情况：{input}"
  - agentId: pm-designer
    role: DESIGNER
    taskTemplate: "设计项目的技术方案和架构：{input}"
  - agentId: pm-evaluator
    role: EVALUATOR
    taskTemplate: "评估项目的风险和资源需求：{input}"
```

### Implementation (TaskDispatcherPipeline.java)

- Each sub-agent receives user input rendered through its `taskTemplate`
- All sub-agents execute in parallel via `Flux.merge()`
- All `TaskOutput` results collected, then orchestrator agent synthesizes final proposal
- Each sub-agent has full toolkit access

### Events (shared for both Subagents patterns)

```
pipeline_start
  task_delegate { from: orchestrator, to: researcher, task: "..." }
  task_start { agent: researcher }
  task_end { agent: researcher, outputPreview: "..." }
  task_delegate { from: orchestrator, to: analyst, task: "..." }
  task_start { agent: analyst }
  task_end { agent: analyst, outputPreview: "..." }
  ...
  task_aggregate { totalTasks: 3 }
pipeline_end
```

---

## Frontend & Debug Panel

### New Event Types

| Event Type | Source | Display |
|-----------|--------|---------|
| `loop_start` / `loop_end` | Loop Pipeline | Iteration counter, pass/fail status |
| `loop_iteration_result` | Critic review | approved/rejected + feedback summary |
| `graph_transition` | StateGraph | State node highlight, from→to path |
| `graph_agent_call` | StateGraph | Agent decision at state node |
| `roundtable_start` / `round_end` | MsgHub | Round number, participant list |
| `round_message` | Expert speech | Grouped by speaker, color-coded |
| `roundtable_summary` | Moderator | Final review report |
| `task_delegate` | Subagents | Delegation chain (who → whom, task) |
| `task_start` / `task_end` | Sub-task | Task card with output preview |
| `task_aggregate` | Aggregation | All sub-task results |

### debug.js Adaptation

- Existing timeline rendering uses `event_type` switch-case; add new branches
- StateGraph transitions: linear ASCII display (`CREATED → SUBMITTED → REVIEWING → APPROVED → ...`)
- MsgHub expert messages: grouped by round, collapsible
- Loop Pipeline: iteration progress indicator

### Agent Selection UI

New agents in `agents.yml` appear automatically in the agent selection list — no frontend changes needed.

---

## Config Model Changes

### AgentConfig additions

```java
// Loop config
LoopConfig loopConfig();

// StateGraph config
List<StateConfig> states();

// MsgHub config
MsgHubConfig msgHubConfig();

// Task template for sub-agents
String taskTemplate();
```

### New config classes

- `LoopConfig`: `maxIterations`, `exitCondition` (AUTO/FIXED)
- `StateConfig`: `name`, `agent`, `transitions[]`
- `StateTransition`: `event`/`condition`, `target`
- `MsgHubConfig`: `rounds`, `summaryRole`
