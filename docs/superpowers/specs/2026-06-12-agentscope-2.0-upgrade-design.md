# AgentScope 2.0 全面升级设计

> 日期: 2026-06-12
> 状态: Draft
> 目标: 全面对标 AgentScope 2.0 正式版，清理 1.0 废弃 API，用 2.0 原生思路重设计多 Agent 模式，添加新特性 Demo

## 背景与现状

### 当前版本
- AgentScope: `2.0.0-RC1`（最新为 `2.0.0-RC3`）
- 项目已完成 Part A 必须迁移（SessionManager、Pipeline 包、state 包重构等）
- 7 种 Pipeline 多 Agent 模式（SEQUENTIAL/PARALLEL/DEBATE/LOOP/MSG_HUB/SUBAGENT_SEQ/SUBAGENT_PAR）被显式禁用

### 审计结论
- **Part A 必须迁移项: 0** — 已全部完成
- **Part B 推荐迁移项: 7 类** — 废弃但仍可编译运行
- RAG 和长期记忆模块官方标注"推进中"，v2 替代方案尚未上线，暂不迁移

### 废弃 API 清单

| 类别 | 废弃 API | 替代方案 | 涉及文件数 |
|------|----------|----------|-----------|
| B.2 Hook | `io.agentscope.core.hook.*` | `MiddlewareBase` 五阶段 | 12+ |
| B.3 Memory | `io.agentscope.core.memory.Memory` | `AgentStateStore` + `AgentState` | 6 |
| B.1 SkillBox | `SkillBox(toolkit)` | `ClasspathSkillRepository` / `FileSystemSkillRepository` | 1 |
| B.4 Event | `Event`/`EventType`/`EventSource` | `AgentEvent` 体系（28 类型化事件） | 15 |
| B.4 Stream | `agent.stream()` | `agent.streamEvents()` | 3 |
| A.1 StructuredOutput | `.structuredOutputReminder()` | 删除（模型层原生支持） | 1 |
| B.5 RAG | `.knowledge()`/`.ragMode()`/`.retrieveConfig()` | 暂不迁移（v2 未上线） | 1 |
| B.6 LTM | `.longTermMemory()`/`.longTermMemoryMode()` | 暂不迁移（v2 未上线） | 1 |

---

## Phase 1: 基础升级 & 废弃清理

### 1.1 版本升级

**改动:**
- `pom.xml`: `agentscope.version` 从 `2.0.0-RC1` → `2.0.0-RC3`（或正式版）
- 检查 RC1→RC3 之间的 API 变更（新事件类型如 `AgentResultEvent`/`CustomEvent`/`HintBlockEvent`）
- `mvn clean compile` 验证

### 1.2 Hook → Middleware 迁移

这是 Phase 1 影响最大的改动。废弃的 `io.agentscope.core.hook` 包由 `LegacyHookDispatcher` 桥接，但仍需迁移。

**ObservabilityHook → ObservabilityMiddleware:**
- 继承 `MiddlewareBase`
- 五阶段映射:
  - `onAgent` — 替代 `PreCallEvent`/`PostCallEvent`（agent_start/agent_end）
  - `onReasoning` — 替代 `PreReasoningEvent`/`ReasoningChunkEvent`/`PostReasoningEvent`（llm_start/thinking/llm_end）
  - `onActing` — 替代 `PreActingEvent`/`PostActingEvent`（tool_start/tool_end）
  - `onModelCall` — 模型调用级别的拦截（token usage 追踪）
  - `onSystemPrompt` — 系统提示注入
- 事件发射方式: 通过 `Sinks.Many<Map<String, Object>>` 保持现有 SSE 兼容

**ApprovalHook → ApprovalMiddleware:**
- 继承 `MiddlewareBase`
- `onActing` 阶段拦截 tool 调用，触发 HITL 审批流程
- 保持与 `ApprovalService` 的集成

**文件更新清单:**
- `hook/ObservabilityHook.java` → `middleware/ObservabilityMiddleware.java`（重命名或原地改写）
- `hook/ApprovalHook.java` → `middleware/ApprovalMiddleware.java`
- `agent/AgentFactory.java`: `.hook()` → `.middleware()`
- `composite/CompositeAgentFactory.java`: 同上
- `runtime/AgentRuntime.java`: 移除 Hook 消费逻辑
- `runtime/AgentRuntimeFactory.java`: 更新工厂方法

### 1.3 SkillBox → SkillRepository

**改动:**
- `agent/AgentFactory.java`:
  - `new SkillBox(toolkit)` → `ClasspathSkillRepository` 或 `FileSystemSkillRepository`
  - `.skillBox(skillBox)` → `.skillRepository(skillRepository)`
- 注册至少一个 repository 后，`DynamicSkillMiddleware` 自动安装

### 1.4 Memory 清理

**改动:**
- `runtime/AgentRuntimeFactory.java`: 删除 `@Deprecated` 的 `createRuntimeWithMemory()` 方法
- `composite/CompositeAgentFactory.java`: 删除废弃的 Memory 参数重载
- 确认所有路径已走 `AgentStateStore`/`Session`

### 1.5 structuredOutputReminder 移除

**改动:**
- `agent/AgentFactory.java`: 删除 `.structuredOutputReminder(...)` 调用
- 结构化输出已由模型层原生支持，无需额外配置

### 1.6 stream() → streamEvents() 迁移

**改动:**
- `runtime/AgentRuntime.java`:
  - 核心流式逻辑从 `agent.stream()` + Hook 消费 → `agent.streamEvents()` + `AgentEvent` 处理
  - 事件类型映射:
    - `AgentEventType.TEXT_BLOCK_DELTA` → `text` SSE 事件
    - `AgentEventType.AGENT_START` / `AGENT_END` → `agent_start` / `agent_end`
    - `AgentEventType.TOOL_CALL_START` / `TOOL_CALL_END` → `tool_start` / `tool_end`
    - 等等
- `runtime/StreamingAgentRuntime.java`: 同步迁移
- `runtime/HarnessRuntime.java`: 同步迁移

**注意事项:**
- `HarnessAgent.streamEvents()` 暂不转发子 agent 事件。需要子 agent 事件流的场景（Phase 2 的多 Agent 模式）暂保留 `stream()` 调用，等官方 `EventSource` 通道落地后再统一切换

### 1.7 暂不迁移

- **RAG 模块**（B.5）: `Knowledge`/`KnowledgeRetrievalTools`/`RAGMode`/`GenericRAGHook` 已废弃，但 v2 替代方案尚未上线。保持现有 `agentscope-extensions-rag-simple` 依赖
- **长期记忆模块**（B.6）: `LongTermMemory`/`LongTermMemoryMode` 已废弃，同上。保持现有 `agentscope-extensions-memory-bailian` 依赖

### 1.8 验证标准

- `mvn clean compile` 通过，废弃 API 警告仅剩 B.5/B.6
- 以下 agent 类型功能正常: SINGLE、ROUTING、HANDOFFS、STATE_GRAPH、HARNESS
- SSE 流式对话正常（text、thinking、tool_start/end 事件）
- 文件上传、MCP、Permission 功能正常
- RAG 和长期记忆功能正常（未迁移）

---

## Phase 2: Pipeline 模式用 2.0 思路重设计

### 设计原则

不是简单移植 1.0 的 `Pipeline`/`MsgHub` 实现，而是用 2.0 的三个核心能力重新思考:
1. **Middleware 五阶段** — 编排逻辑（顺序/并发/循环/路由）
2. **SubAgentTool + AgentManager** — agent 实例管理和调用
3. **streamEvents() + AgentEvent** — 实时事件流

### 2.1 Sequential Pipeline（顺序执行）

**2.0 设计:**
- 编排 Middleware: 按顺序调用各 SubAgent，将上一步输出注入下一步的 RuntimeContext
- 使用 `ReActAgent.Builder.fromAgent()` 派生子 agent builder
- 事件流: `pipeline_start` → `pipeline_step_start` → 子 agent 事件 → `pipeline_step_end` → ... → `pipeline_end`

**Demo agent:** `doc-analysis-pipeline-v2` — 文档解析 → 信息搜索

### 2.2 Parallel Pipeline（并发执行）

**2.0 设计:**
- 编排 Middleware: 使用 `Flux.merge()` 并发调用多个 SubAgentTool
- 结果聚合: 收集所有子 agent 输出合并为最终结果
- 事件流: `pipeline_start` → 所有 `pipeline_step_start` 并发 → 各自完成 → `pipeline_end`

**Demo agent:** `parallel-analysis-v2` — 多角度并行分析

### 2.3 Debate（多专家辩论）

**2.0 设计:**
- 编排 Middleware 管理多轮辩论逻辑（轮次、发言顺序）
- 每个 expert 作为 SubAgentTool 注册
- Judge agent 收集所有观点后综合评判
- 事件流: 通过 `CustomEvent` 推送每位 expert 的实时发言

**Demo agent:** `debate-review-v2` — 多专家辩论 + 裁判综合

### 2.4 Loop（迭代优化）

**2.0 设计:**
- 编排 Middleware 实现循环控制:
  - 每轮: writer 生成 → critic 评审 → 检查退出条件
  - 退出条件: 质量阈值或 maxIterations
- 使用 `AgentState` 传递迭代间的上下文
- 事件流: `loop_start` → `loop_iteration_result`(每轮) → `loop_end`

**Demo agent:** `copywriter-refiner-v2` — 写作 → 评审 → 修改循环

### 2.5 StateGraph（状态机）

**现状:** `OrderFulfillmentGraph` 已有实现，状态转换逻辑完整。

**改动:** 检查事件类型是否需要适配 RC3 新增事件。如无 breaking change 则保持不变。

### 2.6 MsgHub RoundTable（专家圆桌）

**2.0 设计:**
- 编排 Middleware 管理轮次和发言顺序
- 使用 `HintBlockEvent` 或 `CustomEvent` 推送其他 expert 的发言给当前 speaker
- Moderator SubAgent 在所有轮次结束后综合总结
- 事件流: `roundtable_start` → `round_start` → `round_message`(每位) → `round_end` → ... → `roundtable_summary`

**Demo agent:** `expert-roundtable-v2` — 多轮专家讨论

### 2.7 SubAgent Sequential/Parallel（子 Agent 任务编排）

**2.0 设计:**
- 直接利用 2.0 原生 SubAgentTool + AgentManager:
  - Sequential: 链式委托，`{prevOutput}` 模板变量
  - Parallel: 并发分发，结果收集聚合
- 通过 `RuntimeContext` 传递 task 描述和上一步输出
- 事件流: `task_delegate` → `task_start` → 子 agent 事件 → `task_end` → `task_aggregate`

**Demo agents:**
- `report-generator-v2` (SUBAGENT_SEQ) — 研究 → 分析 → 报告撰写
- `project-manager-v2` (SUBAGENT_PAR) — 并行研究、设计、评估

### 2.8 文件更新

**新增/重写:**
- `composite/pipeline/SequentialMiddleware.java`
- `composite/pipeline/ParallelMiddleware.java`
- `composite/pipeline/DebateMiddleware.java`
- `composite/pipeline/LoopMiddleware.java`
- `composite/pipeline/RoundTableMiddleware.java`
- `composite/pipeline/SubAgentSeqMiddleware.java`
- `composite/pipeline/SubAgentParMiddleware.java`

**更新:**
- `composite/CompositeAgentFactory.java`: 移除 `throw UnsupportedOperationException`，接入新 Middleware
- `agent/AgentType.java`: 保持现有枚举
- `runtime/AgentRuntimeFactory.java`: 为新模式创建 runtime

**删除:**
- 旧的 `composite/pipeline/` 实现类（如 `LoopPipeline.java`、`RoundTablePipeline.java` 等）

### 2.9 验证标准

- 7 种多 Agent 模式各有完整 demo agent
- 每种模式的前端 SSE 事件正确显示在 Debug 面板
- `agents.yml` 中禁用的模式全部恢复启用

---

## Phase 3: 新特性 Demo

### 3.1 Context Compaction（上下文压缩）

**功能:** 当对话上下文过长时自动压缩，保留关键信息；超大工具结果 offload 到磁盘。

**实现:**
- 使用 `HarnessAgent` + `CompactionConfig`
- 配置独立轻量模型（如 `dashscope:qwen-plus`）做压缩
- Demo: 一个长对话场景，展示自动触发的压缩事件

**Demo agent:** `compaction-demo`
- 长对话 + 多次工具调用 → 触发 compaction
- Debug 面板展示 compaction 事件

**依赖:** `agentscope-harness`（已有）

### 3.2 Sandbox 沙箱执行

**功能:** 可插拔沙箱后端（Local/Docker/E2B/K8s/Daytona），安全执行代码和命令。

**实现:**
- 配置 `FilesystemConfig` 指定沙箱后端
- Demo 展示 agent 在隔离环境中执行代码
- 与 Permission 系统结合展示 HITL 审批

**Demo agent:** `sandbox-demo`
- Local 后端 demo（开箱即用）
- Docker 后端 demo（可选，需 Docker 环境）

**依赖:** 可能需要 `agentscope-extensions-sandbox-docker` 等新 artifact

### 3.3 AgUI 协议

**功能:** Agentic UI 协议，实时展示 agent 的思考、工具调用、文件变更等过程。

**实现:**
- 集成 `agentscope-extensions-agui`
- 前端 SSE 事件适配 AgUI 格式
- 展示完整的 agent 执行可视化

**Demo agent:** `agui-demo`
- 展示 thinking → tool_call → result 的完整可视化流程

**依赖:** `agentscope-extensions-agui`

### 3.4 A2A 协议

**功能:** Agent-to-Agent 协议，实现跨系统 agent 互操作。

**实现:**
- 集成 `agentscope-extensions-a2a`
- Demo: 两个 agent 互相发现和协作
- 展示 AgentCard、TaskSend 等核心概念

**Demo agent:** `a2a-demo`
- 两个 agent 通过 A2A 协议互相通信

**依赖:** `agentscope-extensions-a2a`

### 3.5 验证标准

- 每个新特性有独立 demo agent
- 前端能正确展示新特性相关的事件
- `agents.yml` 包含完整配置

---

## Phase 4: 前端 & 文档更新

### 4.1 前端事件适配

**改动:**
- `scripts/modules/debug.js`: 适配 `AgentEvent` 新事件类型
- `scripts/api.js`: SSE 解析适配新事件格式
- 新增 Pipeline 模式的前端展示组件（步骤进度条、路由决策、辩论轮次等）
- 新增新特性的前端组件（compaction 指示器、sandbox 状态、AgUI/A2A 标识）

### 4.2 agents.yml 更新

- Phase 2 的 7 种新模式 agent 配置
- Phase 3 的 4 个新特性 demo 配置
- 禁用的模式恢复启用
- 移除不再需要的旧配置项（如 `structuredOutputReminder`）

### 4.3 CLAUDE.md 同步

- 更新 AgentScope 版本号
- 更新架构描述（Hook → Middleware、Pipeline → Middleware 编排）
- 更新 Agent 类型列表（新增 Phase 2/3 的 demo）
- 更新依赖列表（新增 AgUI/A2A/Sandbox 等 artifact）
- 更新项目结构（新增/重命名的文件）

### 4.4 验证标准

- 前端能正确展示所有 agent 类型的交互
- Debug 面板展示所有事件类型
- CLAUDE.md 反映最终代码结构

---

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| RC3→正式版 API 变更 | Phase 1 需额外适配 | 关注 Release Notes，锁定版本 |
| `HarnessAgent.streamEvents()` 不转发子 agent 事件 | Phase 2 多 Agent 模式事件展示受限 | 需要子 agent 事件时暂用 `stream()` |
| RAG/LTM v2 替代方案上线时间不确定 | Phase 1 部分废弃 API 无法清理 | 保持 v1 API，标记 TODO |
| AgUI/A2A 扩展包稳定性 | Phase 3 可能受阻 | 先做最小可行 demo，复杂场景后置 |
| Docker 沙箱需要环境支持 | Sandbox demo 部分功能受限 | 提供 Local 后端 fallback |

---

## 执行顺序

```
Phase 1 (基础升级) ──→ Phase 2 (Pipeline 重设计) ──→ Phase 4 (前端&文档)
                                    │                        ↑
                                    └→ Phase 3 (新特性 Demo) ─┘
```

Phase 2 和 Phase 3 可以部分并行，Phase 4 在 Phase 2/3 完成后执行。
