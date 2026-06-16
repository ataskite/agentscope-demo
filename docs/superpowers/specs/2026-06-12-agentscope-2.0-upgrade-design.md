# AgentScope 2.0 (RC3) 迁移与核心功能演示设计

> 日期: 2026-06-12（2026-06-16 据 RC3 实测 + 代码现状纠偏修订）
> 状态: Phase 1 ✅ 已落地 · Phase 2 ✅ 已落地（手写方案，非原设计） · Phase 3 🟡 部分 · Phase 4 ⚠️ 部分
> 目标: **以 AgentScope 2.0.0-RC3 为基准**，完成 1.0 废弃 API 清理，让项目的所有核心功能
> （单 agent、多 agent、HITL、文档处理、RAG、结构化输出）在 RC3 上稳定运行并可视化演示。
> 不追求用 RC3 不存在的原语"重设计"，而是把 RC3 实际提供的能力用对、演示好。

> ⚠️ 基准说明: 本文档锁定 **2.0.0-RC3**，不追"正式版"或假设未来版本会提供的能力。
> 所有方案均经 RC3 jar 实测验证（javap）。若后续升级版本，需重新核对本文。

## 背景与现状

### 当前版本
- AgentScope: `2.0.0-RC3`（本项目锁定版本；最新可能已有更新，但不主动追）
- 项目已完成 Part A 必须迁移（SessionManager、Pipeline 包、state 包重构等）
- 7 种 Pipeline 多 Agent 模式已全部恢复并支持流式事件（见 Phase 2）

### RC3 多 Agent 编排能力实测结论（设计基准）

> 这是 2026-06-16 纠偏的核心依据。原设计文档假设 RC3 有 Middleware 编排 / SubAgentTool
> 程序化编排 / pipeline 包，**实测均不成立**。

| 设计文档原假设 | RC3 实测 | 对本项目的影响 |
|------|----------|-----------|
| Middleware 五阶段可做多 agent 编排 | ❌ `MiddlewareBase` 是**单 agent 拦截器**（改 Msg/拦工具），无 `invoke`/`call`/`delegate` 方法，无法调用其他 agent | 多 agent 编排必须手写 runtime |
| SubAgentTool + AgentManager 程序化编排 | ⚠️ `SubAgentTool` 是 **LLM 驱动工具**（LLM 决定调谁）；无独立的 `AgentManager` 类（只有 harness 的 `DefaultAgentManager` 注册表） | subagent_seq/par 不能原生编排，保留手写 |
| 1.0 pipeline 包可移植/删除 | ❌ `io.agentscope.core.pipeline.*` 包在 RC3 **不存在** | 无旧实现可删 |
| MsgHub / Roundtable 团队消息原语 | ❌ core+harness 搜索命中数 0 | debate/msghub 手写 Mono 链 |
| streamEvents() + AgentEvent 实时流 | ✅ 成立，`ReActAgent.streamEvents(Msg)` 返回 `Flux<AgentEvent>`（30 种类型） | 这是 Phase 1/2 流式改造的基础 |
| RuntimeContext 传递数据 | ✅ `put/get`（String/Class 键）可用 | 单 agent 内传上下文可用 |

**结论: RC3 的多 agent 编排本质是"1 个父 agent + N 个 SubAgentTool，由 LLM 决定编排"。
没有程序化的顺序/并发/循环/辩论编排器。** 因此本项目的 7 种 pipeline 模式采用手写 runtime
+ `streamEvents()` 实现流式编排，而非原设计文档的 Middleware 方案。

### 审计结论（废弃 API）
- **Part A 必须迁移项: 0** — 已全部完成
- **Part B 推荐迁移项** — 状态见下表
- RAG 和长期记忆模块官方标注"推进中"，v2 替代方案尚未上线，暂不迁移

### 废弃 API 清单与迁移状态

| 类别 | 废弃 API | 替代方案 | 状态 |
|------|----------|----------|------|
| B.2 Hook | `io.agentscope.core.hook.*` | `MiddlewareBase`（ApprovalMiddleware）/ EventSink 桥接（ObservabilityHook） | ✅ 已清理（0 残留） |
| B.3 Memory | `io.agentscope.core.memory.Memory` | `AgentStateStore` + `AgentState` | ✅ 已清理 |
| B.1 SkillBox | `SkillBox(toolkit)` | `ClasspathSkillRepository` / `FileSystemSkillRepository` | ✅ 已清理 |
| B.4 Event | `Event`/`EventType`/`EventSource` | `AgentEvent` 体系（30 类型化事件，见 AgentEventMapper） | ✅ 已清理 |
| B.4 Stream | `agent.stream()` | `agent.streamEvents()` | ✅ 已迁移（AgentRuntime + 7 个 pipeline runtime）；⚠️ HarnessRuntime 仍用 stream() |
| A.1 StructuredOutput | `.structuredOutputReminder()` | 删除（模型层原生支持） | ✅ 已清理 |
| B.5 RAG | `.knowledge()`/`.ragMode()`/`.retrieveConfig()` | 暂不迁移（v2 未上线） | ⏸ 保持 v1 |
| B.6 LTM | `.longTermMemory()`/`.longTermMemoryMode()` | 暂不迁移（v2 未上线） | ⏸ 保持 v1 |

---

## Phase 1: 基础升级 & 废弃清理 ✅ 已落地

> 341 测试全绿，0 废弃 API 残留（除 RAG/LTM 按计划保持 v1）。

### 1.1 版本升级 ✅

**改动:**
- `pom.xml`: `agentscope.version` 从 `2.0.0-RC1` → `2.0.0-RC3`（或正式版）
- 检查 RC1→RC3 之间的 API 变更（新事件类型如 `AgentResultEvent`/`CustomEvent`/`HintBlockEvent`）
- `mvn clean compile` 验证

### 1.2 Hook → Middleware / EventSink 迁移 ✅（实际方案与原设计不同）

> ⚠️ 纠偏: 原设计要求 "ObservabilityHook → ObservabilityMiddleware 继承 MiddlewareBase 五阶段"。
> 实际实现走了**不同的路径**——ObservabilityHook 不继承 MiddlewareBase，而是改成 EventSink 桥接。
> 原因: RC3 的 `streamEvents()` 已自带完整生命周期事件（agent_start/llm_start/thinking/llm_end/
> tool_start/tool_end/agent_end），不再需要 Hook 来手动捕获。因此 ObservabilityHook 只保留"手动
> 多 agent 事件"（pipeline_start 等）的发射职责，委托给 EventSink。

**ApprovalHook → ApprovalMiddleware（✅ 按原设计）:**
- 继承 `MiddlewareBase`
- `onActing` 阶段拦截 tool 调用，触发 HITL 审批流程
- 保持与 `ApprovalService` 的集成

**ObservabilityHook → EventSink 桥接（✅ 替代原 Middleware 方案）:**
- 不继承 `MiddlewareBase`，不再实现 `Hook`
- 自动生命周期事件: 由 `AgentRuntime` 消费 `agent.streamEvents()` + `AgentEventMapper` 转换
- 手动多 agent 事件: 由 `EventSink`（Sinks.Many）发射，ObservabilityHook 桥接
- 事件发射方式保持 SSE 兼容

**实际文件（已落地）:**
- `middleware/ApprovalMiddleware.java`（✅ 新建，继承 MiddlewareBase）
- `hook/ObservabilityHook.java`（✅ 原地改写为 EventSink 桥接，保留类名未重命名）
- `runtime/EventSink.java`（✅ 新建，手动多 agent 事件总线）
- `runtime/AgentEventMapper.java`（✅ 新建，30 种 AgentEvent → SSE Map 纯函数）
- `agent/AgentFactory.java`、`composite/CompositeAgentFactory.java`: `.hook()` 已移除

### 1.3 SkillBox → SkillRepository ✅

**改动:**
- `agent/AgentFactory.java`:
  - `new SkillBox(toolkit)` → `ClasspathSkillRepository` 或 `FileSystemSkillRepository`
  - `.skillBox(skillBox)` → `.skillRepository(skillRepository)`
- 注册至少一个 repository 后，`DynamicSkillMiddleware` 自动安装

### 1.4 Memory 清理 ✅

**改动:**
- `runtime/AgentRuntimeFactory.java`: 删除 `@Deprecated` 的 `createRuntimeWithMemory()` 方法
- `composite/CompositeAgentFactory.java`: 删除废弃的 Memory 参数重载
- 确认所有路径已走 `AgentStateStore`/`Session`

### 1.5 structuredOutputReminder 移除 ✅

**改动:**
- `agent/AgentFactory.java`: 删除 `.structuredOutputReminder(...)` 调用
- 结构化输出已由模型层原生支持，无需额外配置

### 1.6 stream() → streamEvents() 迁移 🟡 部分

**改动:**
- `runtime/AgentRuntime.java`: ✅ 核心流式逻辑从 `agent.stream()` + Hook 消费 → `agent.streamEvents()` + `AgentEventMapper` 处理（30 种事件类型全覆盖）
- `runtime/StreamingAgentRuntime.java`: ✅ 接口未变
- `runtime/HarnessRuntime.java`: ⚠️ **仍用 `agent.stream(userMsg, runtimeContext)`**，未迁移到 `streamEvents()`
- 7 个 pipeline runtime（Sequential/Parallel/Debate/Loop/MsgHub/SubAgentSeq/SubAgentPar）: ✅ 已迁移到 `streamEvents()`（见 Phase 2）

**注意事项:**
- `HarnessRuntime` 未迁移原因: `HarnessAgent.streamEvents()` 暂不转发子 agent 事件。需要子 agent 事件流的场景暂保留 `stream()` 调用，等官方 `EventSource` 通道落地后再统一切换
- 这是 RC3 的已知限制，非本项目阻塞项

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

## Phase 2: Pipeline 模式迁移与流式化 ✅ 已落地（手写方案，非原设计）

> ⚠️ 重大纠偏: 原设计假设 RC3 有 Middleware 编排 / SubAgentTool 程序化编排 / pipeline 包，
> **实测均不成立**（详见"RC3 多 Agent 编排能力实测结论"）。因此 7 种 pipeline 模式没有用
> 原设计的 Middleware 方案，而是**保留手写 runtime + 迁移到 streamEvents()**，实现流式编排。

### 实际设计原则

RC3 没有多 agent 编排原语（Middleware 无法调用其他 agent；SubAgentTool 是 LLM 驱动；
pipeline 包已移除；无 MsgHub）。因此:
1. **手写 runtime 保留** — 每个 pipeline 模式仍用手写的 `Mono`/`Flux` 链编排（顺序/并发/循环）
2. **流式事件补全** — 每个子 agent 调用从 `agent.call()`（只拿最终 Msg）迁移到
   `MultiAgentStreamSupport.runSubAgent()`（走 `streamEvents()`），前端能收到每个子 agent
   的 thinking/tool/text delta
3. **统一基础设施** — `MultiAgentStreamSupport`（extractText 去重 + runSubAgent 流式桥）+
   `AgentEventMapper`（30 种事件 → SSE）

### 2.1 Sequential Pipeline（顺序执行）✅

**实际实现:** `runtime/SequentialRuntime.java`（手写 `chain.flatMap` + `runSubAgent`）
- 子 agent 按顺序执行，上一步输出注入下一步
- 每步通过 `runSubAgent` 走 `streamEvents()`，thinking/tool/text delta 全回流
- 事件流: `pipeline_start` → `pipeline_step_start` → 子 agent 事件 → `pipeline_step_end` → ... → `pipeline_end`

**Demo agent:** `doc-analysis-pipeline-v2` — 文档解析 → 信息搜索

### 2.2 Parallel Pipeline（并发执行）✅

**实际实现:** `runtime/ParallelRuntime.java`（手写 `Flux.merge(agentMonos)` + `runSubAgent`）
- 所有 agent 并发执行，各自接收原始输入
- 每个 agent 的事件共享同一个 `fluxSink`（线程安全），带 source 标签区分
- 结果聚合为 `### <agentId>` 分段

**Demo agent:** `parallel-analysis-v2` — 多角度并行分析

### 2.3 Debate（多专家辩论）✅

**实际实现:** `runtime/DebateRuntime.java`（手写 `Mono.then` 链 + `runSubAgent`）
- 多轮辩论，每轮各 expert 顺序发言，judge 最终综合
- 删除了内部重复的 `Events` 常量（直接用 `hook.emit*`）
- 事件流: `pipeline_start` → `round_message`(每位 expert) → `roundtable_summary`(judge)

**Demo agent:** `debate-review-v2` — 多专家辩论 + 裁判综合

### 2.4 Loop（迭代优化）✅（含 bug 修复）

**实际实现:** `runtime/LoopRuntime.java`（手写 `flatMap` 链 + `runSubAgent`）
- writer 生成 → critic 评审 → 检查退出条件（关键词匹配 APPROVED/通过）
- **bug 修复:** 原 `max-iterations` 路径丢失最终 writer 输出（无 text 事件），现已修复
- **重构:** 嵌套 fire-and-forget `.subscribe()` → `flatMap` Mono 链，消除竞态

**Demo agent:** `copywriter-refiner-v2` — 写作 → 评审 → 修改循环

### 2.5 StateGraph（状态机）✅ 保持不变

**现状:** `OrderFulfillmentGraph` 已有实现，状态转换逻辑完整。
**改动:** RC3 无 breaking change，保持不变。

### 2.6 MsgHub RoundTable（专家圆桌）✅

**实际实现:** `runtime/MsgHubRuntime.java`（手写 `Mono.then` 链 + `runSubAgent`）
- 多轮讨论，每轮各 expert 顺序发言，moderator 最终总结
- RC3 无 MsgHub 原语，用 `discussionLog` List 在 prompt 间传递上下文
- **工厂收敛:** `createMsgHubRuntime` 从内联 `createSingleAgent`（无状态）改为委托
  `CompositeAgentFactory.createMsgHubRuntime`（共享 AgentStateStore），与其余 6 个一致

**Demo agent:** `expert-roundtable-v2` — 多轮专家讨论

### 2.7 SubAgent Sequential/Parallel ✅（决策: 不原生化）

> ⚠️ 决策记录: 原设计要求"原生 SubAgentTool + AgentManager 编排"。实测 RC3 的 SubAgentTool
> 是 **LLM 驱动工具**（LLM 决定调谁、何时），会破坏 demo 的确定性保证（seq 顺序链、par 真并发）。
> 无独立 `AgentManager` 类。因此**保留手写 runtime + 补流式**，原生化降级为未来可选增强。

**实际实现:**
- `runtime/SubAgentSeqRuntime.java`（手写 `chain.flatMap` + `{prevOutput}` 模板 + `runSubAgent`）
- `runtime/SubAgentParRuntime.java`（手写 `Flux.merge` + `{input}` 模板 + `runSubAgent`）
- 事件流: `task_delegate` → `task_start` → 子 agent 事件 → `task_end` → `task_aggregate`

**Demo agents:**
- `report-generator-v2` (SUBAGENT_SEQ) — 研究 → 分析 → 报告撰写
- `project-manager-v2` (SUBAGENT_PAR) — 并行研究、设计、评估

### 2.8 实际文件清单（已落地）

> ⚠️ 纠偏: 原设计要求新建 7 个 `composite/pipeline/*Middleware.java`。实际**一个都没建**
> （RC3 的 Middleware 不能做编排）。改为改写 7 个 runtime + 1 个共享支持类。

**新增:**
- `runtime/MultiAgentStreamSupport.java` — 流式桥（extractText 去重 + runSubAgent：子 agent
  streamEvents → SSE，带 source 标签 + AGENT_RESULT/text-delta 兜底聚合）

**改写（streamEvents 化 + bug 修复 + 去重）:**
- `runtime/SequentialRuntime.java`
- `runtime/ParallelRuntime.java`
- `runtime/DebateRuntime.java`
- `runtime/LoopRuntime.java`
- `runtime/MsgHubRuntime.java`
- `runtime/SubAgentSeqRuntime.java`
- `runtime/SubAgentParRuntime.java`

**更新:**
- `runtime/AgentRuntimeFactory.java`: MSG_HUB 工厂收敛（委托 CompositeAgentFactory）
- `composite/CompositeAgentFactory.java`: ROUTING/HANDOFFS 已用 SubAgentTool（这是唯一原生多 agent 用法）

### 2.9 验证标准

- ✅ 7 种多 Agent 模式各有完整 demo agent（agents.yml 全部启用）
- 🟡 前端 SSE 事件正确显示在 Debug 面板 — **单测覆盖**（7 个 runtime 各有测试，341 全绿），
  但**端到端手测未做**（建议起服务跑 pipeline/loop/approval 真实路径）
- ✅ `agents.yml` 中禁用的模式全部恢复启用

---

## Phase 3: 新特性 Demo

## Phase 3: 新特性 Demo 🟡 部分完成

### 3.1 Context Compaction（上下文压缩）🟡 配置已加，未接线验证

**功能:** 当对话上下文过长时自动压缩，保留关键信息；超大工具结果 offload 到磁盘。

**实现:**
- 使用 `HarnessAgent` + `CompactionConfig`
- 配置独立轻量模型（如 `dashscope:qwen-plus`）做压缩
- Demo: 一个长对话场景，展示自动触发的压缩事件

**当前状态（2026-06-16 核查）:**
- ✅ `compaction-demo` agent 配置已在 agents.yml（type: HARNESS）
- ✅ `harness/CompactionConfigFactory.java` 已创建
- ❌ **`HarnessAgentFactory` 未引用 `CompactionConfigFactory`** → 配置未真正生效
- ❌ 压缩事件触发未端到端验证

**待办:** 把 `CompactionConfigFactory` 接线进 `HarnessAgentFactory`，起服务验证压缩事件

**Demo agent:** `compaction-demo`
**依赖:** `agentscope-harness`（已有）

### 3.2 Sandbox 沙箱执行 🟡 配置已加，未接线验证

**功能:** 可插拔沙箱后端（Local/Docker/E2B/K8s/Daytona），安全执行代码和命令。

**实现:**
- 配置 `FilesystemConfig` 指定沙箱后端
- Demo 展示 agent 在隔离环境中执行代码
- 与 Permission 系统结合展示 HITL 审批

**当前状态（2026-06-16 核查）:**
- ✅ `sandbox-demo` agent 配置已在 agents.yml（type: HARNESS）
- ✅ `harness/FilesystemSpecFactory.java` 已创建
- ❌ **`HarnessAgentFactory` 未引用 `FilesystemSpecFactory`** → 配置未真正生效
- ❌ 沙箱执行未端到端验证

**待办:** 把 `FilesystemSpecFactory` 接线进 `HarnessAgentFactory`，验证 Local 后端；Docker 后端可选

**Demo agent:** `sandbox-demo`
**依赖:** 可能需要 `agentscope-extensions-sandbox-docker` 等新 artifact

### 3.3 AgUI 协议 ❌ 未做

**功能:** Agentic UI 协议，实时展示 agent 的思考、工具调用、文件变更等过程。

**当前状态:** ❌ 未开始（无 `agui-demo`、pom 无 `agentscope-extensions-agui` 依赖）

**待办:** 加 artifact 依赖 + 前端 SSE 事件适配 AgUI 格式 + 最小 demo

**依赖:** `agentscope-extensions-agui`（需确认 RC3 兼容版本）

### 3.4 A2A 协议 ❌ 未做

**功能:** Agent-to-Agent 协议，实现跨系统 agent 互操作。

**当前状态:** ❌ 未开始（无 `a2a-demo`、pom 无 `agentscope-extensions-a2a` 依赖）

**待办:** 加 artifact 依赖 + 两个 agent 互通信 demo

**依赖:** `agentscope-extensions-a2a`（需确认 RC3 兼容版本）

### 3.5 验证标准

- 🟡 Compaction/Sandbox: 配置已加但未接线生效，需验证
- ❌ AgUI/A2A: 未做

---

## Phase 4: 前端 & 文档更新 ⚠️ 部分完成

### 4.1 前端事件适配 🟡 部分

**当前状态:**
- ✅ `scripts/api.js` SSE 解析适配（fetch + ReadableStream，无 EventSource）
- ✅ `scripts/modules/debug.js` 处理 pipeline/routing/handoff/loop/graph/roundtable/task 等事件
- ✅ `scripts/chat.js` resume 解析器补 thinking/tool_start/tool_end（Phase 1 改动）
- ❌ **Step 2 新增的 7 个事件类型前端完全不渲染**（后端发、前端静默丢弃）:
  - `data_block_delta`、`subagent_exposed`、`tool_call_delta`、`tool_result_start`、
    `tool_result_delta`、`require_external_execution`、`external_execution_result`

**待办:** 在 `chat.js` 的 `switch(payload.type)` 为上述 7 个类型补 case（最小: 加 timeline row）

### 4.2 agents.yml 更新 ✅

- ✅ Phase 2 的 7 种新模式 agent 配置
- ✅ Phase 3 的 compaction/sandbox demo 配置
- ✅ 禁用的模式恢复启用
- ✅ 移除不再需要的旧配置项（如 `structuredOutputReminder`）

### 4.3 CLAUDE.md 同步 🟡 部分

**当前状态:**
- ✅ 版本号 RC3、多 agent 模式描述
- ❌ Hook → EventSink/Middleware 的架构描述未更新
- ❌ 新事件类型（AgentEventMapper 30 种）未记录
- ❌ Phase 3 的新依赖（AgUI/A2A/Sandbox）未更新

**待办:** Phase 2/3 收尾后同步 CLAUDE.md

### 4.4 验证标准

- 🟡 前端能正确展示所有 agent 类型的交互 — 旧事件 OK，7 个新事件类型待补
- ❌ Debug 面板展示所有事件类型 — 同上
- ❌ CLAUDE.md 反映最终代码结构 — 待同步

---

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **RC3 无多 agent 编排原语**（结构性，非临时） | 7 种 pipeline 模式无法用原生 Middleware/SubAgentTool 编排 | 采用手写 runtime + streamEvents；本设计文档已据实修订 |
| 锁定 RC3，不追正式版 | 若 RC3→正式版有 breaking change 需重新核对本文 | 升级版本时重新 javap 核对所有原语假设 |
| `HarnessAgent.streamEvents()` 不转发子 agent 事件 | HarnessRuntime 无法迁移到 streamEvents | 保持 `agent.stream()`，等官方 EventSource |
| RAG/LTM v2 替代方案上线时间不确定 | Phase 1 部分废弃 API 无法清理 | 保持 v1 API，标记 TODO |
| AgUI/A2A 扩展包稳定性 + RC3 兼容性 | Phase 3 可能受阻 | 先做最小可行 demo，复杂场景后置 |
| Docker 沙箱需要环境支持 | Sandbox demo 部分功能受限 | 提供 Local 后端 fallback |

---

## 执行顺序

```
Phase 1 (基础升级) ✅ ──→ Phase 2 (Pipeline 流式化) ✅ ──→ Phase 4 (前端&文档) ⚠️
                                          │                          ↑
                                          └→ Phase 3 (新特性 Demo) 🟡 ─┘
```

- Phase 1 ✅ 已完成
- Phase 2 ✅ 已完成（手写方案，非原设计）
- Phase 3 🟡 部分（compaction/sandbox 配了未接线；AgUI/A2A 未做）
- Phase 4 ⚠️ 部分（前端 7 个新事件未渲染；CLAUDE.md 待同步）

---

## 实际完成情况（2026-06-16）

| Phase | 状态 | 关键产出 | 测试 |
|-------|------|---------|------|
| Phase 1 基础升级 | ✅ | 版本 RC3、Hook→EventSink/Middleware、SkillRepository、Memory 清理、structuredOutput 移除、streamEvents 迁移、AgentEventMapper 30 事件 | 341 全绿 |
| Phase 2 Pipeline 流式化 | ✅ | 7 runtime 全部 streamEvents、MultiAgentStreamSupport、Loop bug 修复、MSG_HUB 工厂收敛 | +12 runtime 测试 |
| Phase 3 新特性 Demo | 🟡 | compaction/sandbox 配置+Factory（未接线）；AgUI/A2A 未做 | — |
| Phase 4 前端 & 文档 | ⚠️ | agents.yml ✅；前端 7 新事件待补；CLAUDE.md 待同步 | — |

### 关键决策记录

1. **ObservabilityHook 不继承 MiddlewareBase** — 改为 EventSink 桥接（streamEvents 已自带生命周期事件）
2. **7 pipeline 保留手写 runtime** — RC3 的 Middleware 无法调用其他 agent；SubAgentTool 是 LLM 驱动
3. **subagent_seq/par 不原生化** — LLM 驱动的 SubAgentTool 会破坏确定性保证
4. **HarnessRuntime 暂不迁移 streamEvents** — 等 HarnessAgent 支持 EventSource 转发
5. **RAG/LTM 保持 v1** — 等官方 v2 替代方案上线
6. **锁定 RC3** — 不追正式版，所有方案以 RC3 实测为准

### 下一步建议（按优先级）

1. **🔴 Phase 4.1 前端新事件处理** — Step 2 的 7 个新 SSE 类型前端不渲染，收益最高
2. **🟡 Phase 3.1/3.2 接线验证** — compaction/sandbox Factory 接入 HarnessAgentFactory + 端到端验证
3. **🟡 Phase 2.9 端到端手测** — 起服务跑 pipeline/loop/approval 真实路径（单测已绿）
4. **🟡 CLAUDE.md 同步** — Phase 2/3 收尾后更新架构描述
5. **🔴 Phase 3.3/3.4 AgUI/A2A** — 加 artifact 依赖 + 最小 demo（需确认 RC3 兼容）
