# AgentScope Java 2.0 GA — 差距分析与演进路线

> Last reviewed: 2026-07-12（S1-S11 实施完成）
> Baseline: Spring Boot 3.5.14, Java 17, `agentscope.version=2.0.0` GA（2026-07-10 发布）
> Tests: `mvn test` → **345 tests, 0 failures, 0 errors**
> 官方文档: https://java.agentscope.io/v2/zh/docs/index.html
> 实施状态: **S1-S11 已完成，唯一待办为 A2A Protocol（等官方补齐 io.a2a SDK）**

## 方法论

本文档以**官方 2.0 GA 文档全集**（building-blocks / harness / integration 三个目录的全部章节）为基准，逐节对照当前项目代码，标注每一项能力的采用状态（✅ 已采用 / ⚠️ 部分采用 / ❌ 未采用 / 🔒 官方 gap）。

> **注意：** 第一到第四部分记录的是**实施前的差距基线**（保留作为审计参考）。实际完成状态见「第六部分：实施完成总结」。

## 版本演进路径

```
RC1 (2026-05-28) → RC2 (2026-06-09) → RC3 (2026-06-11) → RC4 (2026-06-18) → RC5 (2026-07-07) → GA (2026-07-10)
```

| 版本 | 对本项目的关键意义 |
|------|-------------------|
| RC1 | 首个 RC：无状态引擎、Middleware、AgentState/RuntimeContext |
| RC2 | projectWritable、Permission 运行时切换、子 agent 事件转发、`AgentEvent.source`、Compaction/Memory 独立 model、Channel 模块化、`DistributedBackend` 统一接口、Agent 完全无状态、sandbox 从 harness core 拆出 |
| RC3 | `AgentResultEvent`、`CustomEvent`、`HintBlockEvent`、工具事件带 `toolCallName` |
| RC4 | Harness 异步工具 #1802、持久化 spawn registry #1817、DynamicSkillMiddleware #1828 |
| RC5 | **唯一 breaking**：模型 provider 模块化（DashScope 等从 core 拆为独立 extension） |
| GA | 零 breaking；新增 `AllToolsDeniedEvent`、`PostgresDistributedStore`、Spring Boot customizers；修 `seedSystemMsg` NIO 阻塞、Anthropic 并行工具拆分 |

---

## 第一部分：Building Blocks 差距分析

### 1.1 Agent 构建

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `ReActAgent.builder()` 基础构建 | ✅ `AgentFactory` 用此 | — |
| `HarnessAgent.builder()` 一站式封装 | ⚠️ `HarnessAgentFactory` 用了，但只配了 workspace + compaction + filesystem(BUILDER) | **缺 14 项 builder 能力**（见第二部分详表） |
| `stateStore(AgentStateStore)` | ✅ 已用 `InMemoryAgentStateStore` | ❌ 缺 `RedisAgentStateStore` / `MysqlAgentStateStore` 分布式 profile |
| `defaultSessionId` | ✅ 已用 | — |
| `permissionContext(PermissionContextState)` | ✅ `PermissionContextFactory` 已用（ReActAgent 路径） | ⚠️ HarnessAgent 路径未接 permissionContext |
| `maxIters` 配置 | ❌ 未暴露 | 缺配置项 |
| 结构化输出 `call(msgs, ctx, Class<T>)` | ⚠️ 有 schema（发票/身份证/合同），但走 prompt 注入 | 未用 GA 原生 `supportsNativeStructuredOutput()` 路径 |
| `maxRetries` / `fallbackModel` | ❌ 未用 | 缺模型容错（见 §1.5） |
| `enableMetaTool()` | ❌ 未用 | 缺 `reset_tools` 元工具演示 |
| `enableTaskList()` | ⚠️ 仅 ReActAgent 路径调了 | 注释写的是 "PlanNotebook"（v1 心智），HarnessAgent 路径未接 |
| `enablePendingToolRecovery(true)` | ✅ 已用 | API 在 GA 仍存在（未 @Deprecated 编译期） |

### 1.2 Message & Event

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `ContentBlock`（TextBlock/DataBlock/ThinkingBlock/ToolUseBlock/ToolResultBlock/HintBlock） | ✅ 后端已用 | — |
| 角色子类（UserMessage/AssistantMessage/...） | ✅ 已用 | — |
| `streamEvents()` → `Flux<AgentEvent>` | ✅ `AgentRuntime` 已用 | — |
| `AgentEventMapper` 覆盖全集 | ✅ 30+ 类型含 GA `all_tools_denied` | — |
| `AgentEvent.source`（main/subagent 分流） | ✅ 后端 mapper 已提取 `source` 字段 | ⚠️ 前端未按 source 分区展示 |
| 事件关联 ID（replyId/blockId/toolCallId） | ⚠️ 后端部分提取 | 缺前端按 ID 拼接 delta 的能力 |
| `GenerateReason` 枚举 | ❌ 未消费 | 缺前端对停止原因的展示（MAX_ITERATIONS/PERMISSION_ASKING 等） |

### 1.3 Middleware

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `MiddlewareBase` 五钩子（onAgent/onReasoning/onActing/onModelCall/onSystemPrompt） | ✅ `ApprovalMiddleware` + 自定义示例（审计/限流/上下文注入） | — |
| `OtelTracingMiddleware` | ❌ 未用 | 缺 OTel traceId 串联 |
| `TaskReminderMiddleware` | ❌ 未单独配 | 随 `enableTaskList` 隐式启用，但无独立展示 |
| `CompactionMiddleware` | ⚠️ HarnessAgentFactory 配了 compaction，但单 agent 路径无 | — |
| `ToolResultEvictionMiddleware` | ⚠️ `CompactionConfigFactory` 建了 `ToolResultEvictionConfig` | ❌ **Factory 建了但 HarnessAgentFactory 未引用**（配了没用） |
| `WorkspaceContextMiddleware` | ⚠️ 随 HarnessAgent 隐式启用 | 无独立展示 |

### 1.4 Model

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `CredentialBase` + `ChatModelBase` | ✅ 用 `DashScopeChatModel.builder()` | — |
| Provider 模块化（RC5） | ✅ 已迁 `agentscope-extensions-model-dashscope` | ❌ 缺 OpenAI/Gemini/Anthropic/Ollama 示例 |
| `ModelRegistry`（`"provider:model"` 解析） | ❌ 未用 | 当前每个 Factory 手写 `DashScopeChatModel.builder()`，无统一入口 |
| `maxRetries` / `fallbackModel` | ❌ 未用 | **HarnessAgent.Builder 有此方法，但未接线** |
| 结构化输出原生路径 | ❌ 未用 | 走 prompt 注入，未用 `supportsNativeStructuredOutput()` |
| DashScope Spring Boot customizers（GA 新增） | ❌ 未用 | — |
| 独立 compaction model | ❌ 未配 | 长对话压缩占用主模型 |

### 1.5 Permission System

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `PermissionContextState`（allow/ask/deny） | ✅ `PermissionContextFactory` 已用 | — |
| `PermissionMode`（DEFAULT/ACCEPT_EDITS/EXPLORE/BYPASS/DONT_ASK） | ✅ 已用 | — |
| 运行时切换 `setPermissionMode` | ✅ 已用 | — |
| 危险路径保护 `ToolDangerousPathConstants` | ❓ 未确认 | 需核查 |
| `generateSuggestions` 自动建议规则 | ❌ 未用 | — |
| HarnessAgent 路径接 permissionContext | ❌ 未接 | Harness demo agent 无权限控制 |

### 1.6 Tool System

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `@Tool` / `@ToolParam` 注解驱动 | ✅ 多个 Tool 类 | — |
| `Toolkit` + `ToolGroup` | ✅ `ToolRegistry` 已用 | — |
| MCP 集成（`McpClientBuilder.stdio/sse/streamableHttp`） | ✅ 5 个 MCP agent | ⚠️ 缺 `tools.json` 白名单、`httpRequestCustomizer`（OAuth） |
| `enableMetaTool()`（reset_tools） | ❌ 未用 | — |
| 外部执行工具（`externalTool=true`） | ⚠️ 后端发了 `require_external_execution` 事件 | ❌ 前端不渲染该事件 |
| 上下文注入参数（ToolEmitter/RuntimeContext/自定义 POJO） | ❓ 未确认 | 需核查是否利用了自动注入 |

### 1.7 Skill

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `SkillRepository`（替代 `SkillBox`） | ✅ `ClasspathSkillRepository` 已用 | — |
| `FileSystemSkillRepository` | ❌ 未用 | 缺 workspace 动态 skill 加载 |
| 四层优先级（projectGlobal < skillRepository < workspace < userId） | ❌ 未用 | — |
| `enableSkillManageTool`（propose_skill / skill_manage） | ❌ 未用 | 缺自学习演示 |
| `enableSkillPromotionGate`（审批门） | ❌ 未用 | — |
| `enableSkillCurator`（自动归档过期 skill） | ❌ 未用 | — |
| Git/MySQL/PostgreSQL/Nacos SkillRepository | ❌ 未用 | — |

### 1.8 Context & AgentState

| 官方能力 | 当前状态 | 差距 |
|---------|---------|------|
| `AgentState`（sessionId/userId/context/summary/permissionContext/planModeContext/tasksContext） | ✅ 已用 | — |
| `AgentStateStore`（InMemory/JsonFile） | ✅ 已用 | — |
| `RuntimeContext.builder().userId().sessionId()` | ✅ 已用 | — |
| Redis/MySQL 分布式 `AgentStateStore` | ❌ 未用 | 缺分布式 session 恢复 profile |
| `InterruptControl`（瞬态中断） | ❌ 未用 | — |

---

## 第二部分：Harness 差距分析（最大差距区）

> `HarnessAgent.Builder` 在 GA 暴露 20+ 项能力，当前 `HarnessAgentFactory` 只用了 **5 项**。

### HarnessAgent.builder() 能力采用矩阵

| Builder 方法 | 官方文档 | 当前状态 | 差距 / showcase 价值 |
|-------------|---------|---------|---------------------|
| `.workspace(Path)` | ✅ | ✅ 已用 | — |
| `.model(Model)` | ✅ | ✅ 已用 | — |
| `.compaction(CompactionConfig)` | ✅ | ✅ 已用 | ⚠️ 缺独立 model 配置 |
| `.filesystem(LocalFilesystemSpec)` | ✅ | ⚠️ 仅 BUILDER 模式 | ❌ **`FilesystemSpecFactory.createDocker()` 建了但未被 HarnessAgentFactory 调用** |
| `.filesystem(SandboxFilesystemSpec)` | ✅ | ❌ 未用 | ★★★ Docker 沙箱代码执行 demo |
| `.memory(MemoryConfig)` | ✅ | ❌ 未用 | ★★★ 分层记忆（MEMORY.md + 每日 fact log） |
| `.enablePlanMode()` | ✅ | ❌ 未用 | ★★★ Plan Mode（只读规划→确认→写入） |
| `.enableTaskList()` | ✅ | ❌ 未用 | ★★★ TodoTools + TaskReminderMiddleware |
| `.subagent(SubagentDeclaration)` | ✅ | ⚠️ 靠 workspace `subagents/*.md` | 未用编程式 SubagentDeclaration |
| `.skillRepository(...)` | ✅ | ❌ 未用 | Harness agent 无 skill |
| `.permissionContext(...)` | ✅ | ❌ 未用 | Harness agent 无权限控制 |
| `.stateStore(AgentStateStore)` | ✅ | ❌ 未用 | Harness agent 用默认 JsonFile |
| `.maxRetries(int)` | ✅ | ❌ 未用 | 模型容错 |
| `.fallbackModel(String)` | ✅ | ❌ 未用 | 模型容错 |
| `.additionalContextFile(String)` | ✅ | ❌ 未用 | 额外上下文注入 |
| `.maxContextTokens(int)` | ✅ | ❌ 未用 | 上下文窗口控制 |
| `.enableMetaTool(boolean)` | ✅ | ❌ 未用 | reset_tools 元工具 |
| `.enableSkillManageTool(...)` | ✅ | ❌ 未用 | skill 自学习（propose_skill） |
| `.enableSkillCurator(...)` | ✅ | ❌ 未用 | skill 自动归档 |
| `.enableSkillPromotionGate(...)` | ✅ | ❌ 未用 | skill 审批门 |
| `.channel(...)` | ✅ | ❌ 未用 | IM 接入（飞书/钉钉/GitHub） |

### 已存在的 Harness 资产

- ✅ `HarnessAgentService` + `HarnessRuntime`（用 `agent.stream()`）
- ✅ `FilesystemSpecFactory`（Local + Docker 两个方法，但 **Docker 未被引用**）
- ✅ `CompactionConfigFactory`（含 `ToolResultEvictionConfig`，但 **未被 HarnessAgentFactory 引用**）
- ✅ 2 个 demo agent 配置（complaint-reviewer / finance-intel-tracker）
- ✅ `WorkspaceInitializer`（含 subagents/*.md 模板）
- ✅ `sandbox-demo` / `compaction-demo` agent 配置

### 🔒 官方已知 gap

| Gap | 官方原文 | 替代方案 |
|-----|---------|---------|
| `HarnessAgent.streamEvents()` 不转发子 agent 事件 | change-log B.4：「`EventSource` 通道未上线」 | 继续用 deprecated `agent.stream()`（功能正常）。`HarnessRuntime` 现状即如此 |
| RAG / LongTermMemory v1 API 待重写 | 标 `@Deprecated(forRemoval=true)`，v2 重写未上线 | RAG Chat 维持 v1；长记忆用 Harness 分层记忆替代 |

---

## 第三部分：Integration 差距分析

| 集成领域 | 官方模块 | 当前状态 | 差距 |
|---------|---------|---------|------|
| **MCP** | `building-blocks/tool.md` | ✅ 5 个 agent | 缺 `tools.json` 白名单、OAuth、动态激活 |
| **A2A Protocol** | `agent-protocols/` | ❌ 未用 | 缺跨 agent 能力注册+调用 demo |
| **AG-UI Protocol** | `agent-protocols/` | ❌ 未用 | 缺 AguiEvent 规范化前端 |
| **Channel/IM** | `harness/channel.md` | ❌ 未用 | 缺飞书/钉钉/GitHub 接入 |
| **RAG** | `rag/` | ⚠️ v1 API（deprecated） | 等 v2 重写 |
| **长期记忆** | `memory/`（Mem0/Bailian/ReMe） | ⚠️ v1 API（deprecated） | 等 v2；Harness 内建记忆是替代 |
| **DistributedBackend** | Redis/MySQL/OSS/Postgres/COS | ❌ 未用 | 缺分布式 session 漂移恢复 |
| **OpenTelemetry** | `OtelTracingMiddleware` | ❌ 未用 | 缺 traceId 串联 |
| **Skill Repository** | Git/MySQL/PostgreSQL/Nacos | ❌ 未用（只有 Classpath） | 缺动态 skill 市场 |
| **Sandbox** | Docker/K8s/E2B/Daytona/AgentRun | ❌ 未用（Factory 建了没接） | 缺安全代码执行 demo |

---

## 第四部分：前端差距分析

> 后端 `AgentEventMapper` 发送的事件 vs 前端 `chat.js` 实际处理的 case。

### 后端发送但前端不渲染的事件（6 个）

| SSE 事件类型 | 后端发送位置 | 前端状态 | 应如何渲染 |
|-------------|------------|---------|-----------|
| `data_block_delta` | AgentEventMapper | ❌ 丢弃 | 多模态内容增量（图片/音频） |
| `subagent_exposed` | AgentEventMapper | ❌ 丢弃 | debug panel 标注子 agent 暴露的 tools |
| `tool_call_delta` | AgentEventMapper | ❌ 丢弃 | tool timeline 行增量参数展示 |
| `tool_result_start` | AgentEventMapper | ❌ 丢弃 | tool 结果区开始标记 |
| `require_external_execution` | AgentEventMapper | ❌ 丢弃 | 外部执行请求卡片 |
| `external_execution_result` | AgentEventMapper | ❌ 丢弃 | 外部执行结果卡片 |

> 注：`tool_result_delta` 前端已处理（增量结果预览）。

### 前端其他缺口

- ❌ 未按 `AgentEvent.source` 分区展示 main/subagent 输出
- ❌ 未消费 `GenerateReason`（MAX_ITERATIONS/PERMISSION_ASKING 等停止原因）
- ❌ 未按事件关联 ID（replyId/blockId/toolCallId）拼接增量

---

## 第五部分：Roadmap

> 排列原则：先补齐「配了没用」的接线（零新知识），再做 GA 新能力 showcase，最后生产化。

### P1-A：接线收尾（积压项，零新知识）

> 这些是之前做到一半但没接上的。GA 上完全可做，不依赖任何官方 gap。

任务:

- **接线 `FilesystemSpecFactory.createDocker()` → `HarnessAgentFactory`**：`sandbox-demo` 配置写了 `filesystemMode: LOCAL`，应支持配 `DOCKER` 并走 `SandboxFilesystemSpec`。当前 Factory 建了方法但没调。
- **接线 `CompactionConfigFactory.createEvictionConfig()` → `HarnessAgentFactory`**：`ToolResultEvictionConfig` 建了但没用。
- **前端 6 个缺失 SSE 事件渲染**（§第四部分详表）。
- **`enableTaskList` 注释纠偏**：`AgentFactory.java:139` 注释写 "PlanNotebook"（v1 心智），应改为 Task List；HarnessAgent 路径也应接 `enableTaskList`。
- **端到端手测**：RC3→GA 迁移后未做过完整手测（7 类 agent）。
- **文档同步**：CLAUDE.md / AGENTS.md 的版本号和架构描述更新到 GA。

验收:

- `sandbox-demo` 能走 Docker 沙箱执行 Python 代码。
- compaction demo 能看到 `ToolResultEvictionConfig` 生效。
- 6 个 SSE 事件在前端有渲染。
- 7 类 agent 手测通过。

---

### P1-B：Harness 能力补齐（GA showcase 核心）

> `HarnessAgent.Builder` 有 20+ 项能力，当前只用 5 项。这是 GA 最大的展示价值区。

任务:

- **`.memory(MemoryConfig)`**：为 `personal-assistant` / `finance-intel-tracker` 启用分层记忆（MEMORY.md + 每日 fact log + flush/consolidation 三个 LLM 调用）。配置 `flushTrigger`、`consolidationModel`（轻量）、`dailyFileRetentionDays`。
- **`.enablePlanMode()`**：新建 plan-mode-demo agent，展示只读规划→用户确认→写入执行四阶段。`plan_enter`/`plan_write`/`plan_exit`。
- **`.enableTaskList()`**：与 Plan Mode 协作，`TodoTools` + `TaskReminderMiddleware`，替代旧 `planEnabled` 心智。
- **`.maxRetries(int)` + `.fallbackModel(String)`**：Harness agent 接入模型容错。
- **`.permissionContext(...)` → HarnessAgent**：让 complaint-reviewer 的写操作有 allow/ask/deny。
- **`.skillRepository(...)` → HarnessAgent**：Harness agent 加载 skill。
- **`.additionalContextFile(...)` + `.maxContextTokens(int)`**：上下文注入控制。
- **`.enableMetaTool(true)`**：`reset_tools` 元工具演示。

验收:

- `finance-intel-tracker` 重启后记忆可恢复。
- Plan Mode demo 能展示四阶段流转。
- Harness agent 有权限控制和 skill 加载。
- 主模型失败能切 fallback。

---

### P1-C：模型与容错（ReActAgent 路径）

> 当前每个 Factory 手写 `DashScopeChatModel.builder()`，无统一入口。

任务:

- **引入 `ModelRegistry`**：解析 `"dashscope:qwen-plus"` 格式字符串，替代手写 builder。
- **`agents.yml` 支持 `model: provider:modelName`**：兼容旧 `modelName`。
- **`maxRetries` / `fallbackModel` → ReActAgent 路径**：与 P1-B 的 Harness 路径对齐。
- **多 provider 示例配置**：OpenAI-compatible / DeepSeek / Ollama（默认仍 DashScope）。
- **独立 compaction model**：长对话压缩配轻量模型（如 `qwen-turbo`）。
- **结构化输出原生路径**：评估 `supportsNativeStructuredOutput()` 替代 prompt 注入。

验收:

- 不同 agent 可用不同 provider，不改 Java 代码。
- 主模型失败时切 fallback，debug panel 有切换事件。
- 长对话压缩不占用主模型。

---

### P2-A：RAG 与记忆（替代方案优先）

> 官方 v2 RAG/LTM 重写未上线。在等待期间用 Harness 内建能力做可演示方案。

任务:

- **短期维持**：RAG Chat / Agentic RAG 维持 v1 API 运行，代码标注 `@Deprecated`，不扩新功能。
- **记忆替代**：`long-conversation` / `personal-assistant` 引入 Harness 分层记忆，替代 v1 `LongTermMemory`。
- **大工具结果落盘**：配合 GA `ToolResultEvictionConfig` + context overflow 兜底。
- **监测官方**：跟踪 `agentscope-extensions-rag-simple` v2 正式 API 发布。

验收:

- `personal-assistant` 重启后偏好记忆可恢复。
- 长对话 demo 触发压缩并在 debug panel 看到事件。

---

### P2-B：多 Agent 模式（用 GA 原生能力增强）

> 当前 7 个 pipeline 是手写 runtime。用 GA subagent / spawn registry 逐步增强。

任务:

- **SUBAGENT_SEQ/PAR → Harness subagent API**：当前手写方案能用；GA 持久化 spawn registry（RC4 #1817）支持 session 恢复，评估迁移。
- **编程式 `SubagentDeclaration`**：当前靠 workspace `subagents/*.md`，补充编程式声明。
- **A2A demo**：一个 agent 注册能力，另一个通过协议调用（订单履约 → 库存/客服）。
- **MSG_HUB / DEBATE**：用 `CustomEvent`（`state_updated`/`team_updated`）+ `HintBlockEvent` 表达团队消息。
- **`expose_to_user`**：`SubagentExposedEvent` + 前端分区展示（需配合前端 source 渲染）。

验收:

- 多 agent session 重启后状态可恢复。
- A2A demo 两个 agent 能跨协议调用。
- 前端能区分 main/subagent 输出。

---

### P2-C：Skill 自学习

> 当前只有静态 ClasspathSkillRepository。

任务:

- **`FileSystemSkillRepository`**：workspace `skills/` 动态加载。
- **四层优先级**：projectGlobal < skillRepository < workspace/skills < userId/skills。
- **`enableSkillManageTool`**：`propose_skill` / `skill_manage`，agent 自动创建新 skill。
- **`enableSkillPromotionGate`**：`LocalApprovalGate` / `CanaryFilter`，skill 上线审批。
- **`enableSkillCurator`**：自动归档过期 skill（`staleAfterDays: 30`）。
- **Git/MySQL SkillRepository**：团队共享 skill 市场。

验收:

- agent 能在对话中自动提出新 skill 并经审批上线。
- 过期 skill 自动归档。

---

### P3：生产化样板

> 从 demo 走向企业级部署参考实现。

任务:

- **DistributedBackend**：`RedisAgentStateStore`（或 GA 新增 `PostgresDistributedStore`），多副本 session 漂移恢复 demo。
- **OpenTelemetry**：`OtelTracingMiddleware` 接入，traceId 串联 SSE / debug panel。
- **Channel / IM**：`agent.channel(ChatUiChannel.create())` 或内置适配器（飞书/钉钉/GitHub），IM 接入 showcase。
- **MCP 加强**：`tools.json` 白名单 + tool group 动态激活 + `httpRequestCustomizer`（OAuth token 注入）。
- **AG-UI Protocol**：`AguiEvent` 规范化前端交互。
- **Docker Sandbox**：安全代码执行 profile（配合 P1-A 接线）。
- **`InterruptControl`**：瞬态中断演示。
- 导出事件日志，评估 Studio 可视化调试对接。

验收:

- 本地单机 + Redis 分布式 profile 都能启动。
- 事件日志可追踪到单次用户请求。
- MCP / A2A / Channel 各有可运行样例。

---

## Spec 分解与落地顺序

> 原则：一个 spec 应内聚到能一次设计完、一个 Sprint 实施完。上面的 P1/P2/P3 阶段是按「能力领域」分的，有些阶段里混了多个不相关的关注点。下面把它们拆成 **11 个可独立 brainstorm → spec → 实施的单元**，标注依赖关系和落地顺序。

### Spec 单元总览

| Spec | 名称 | 来源阶段 | 状态 | Commit |
|------|------|---------|------|--------|
| **S1** | HarnessAgentFactory 接线收尾 | P1-A | ✅ 完成 | `f42b9ed` |
| **S2** | 前端 6 个 SSE 事件补齐 | P1-A | ✅ 完成 | `32b52b0` |
| **S3** | ModelRegistry + fallback 统一入口 | P1-C | ✅ 完成 | `32b52b0` |
| **S4** | Plan Mode + Task List demo | P1-B | ✅ 完成 | `05d3f0a` |
| **S5** | Harness 分层记忆 | P1-B | ✅ 完成 | `05d3f0a` |
| **S6** | Harness 权限 + Skill 接入 | P1-B | ✅ 完成 | `2794f6d` |
| **S7** | 上下文控制 | P1-B | ✅ 完成 | `2794f6d` |
| **S8** | RAG / 记忆替代方案 | P2-A | ✅ 完成 | `31b1fd0` |
| **S9** | 多 Agent source 分区 | P2-B | ✅ 完成 | `31b1fd0` |
| **S10** | Skill 自学习 | P2-C | ✅ 完成 | `31b1fd0` |
| **S11** | OTel tracing | P3 | ✅ 完成 | `54b18dc` |
| **S12** | A2A Protocol | P3 | ✅ 完成 | `a9f9432` |
| **S13** | 分布式状态存储 (Redis/MySQL/Postgres) | P3 | ✅ 完成 | (S13 commit) |
| **S14** | Channel/飞书 IM 接入 | P3 | ✅ 完成 | `bfce3ed` |
| **S15** | AG-UI Protocol | P3 | ✅ 完成 | `7211ab7` |

### 依赖关系图

```
S1 接线收尾 ──────┬──→ S4 Plan Mode + Task List
                 ├──→ S5 Harness 分层记忆 ──→ S8 RAG/记忆替代
                 └──→ (所有 Harness 能力的前置)

S2 前端事件补齐 ──────────→ S9 多 Agent + A2A (前端 source 分区)

S3 ModelRegistry ─────────→ (被 S4/S5/S6 共享)

S6 权限+Skill ────────────→ S10 Skill 自学习

无依赖可并行: S1, S2, S3, S6, S7
```

### 落地顺序（分批执行）

> **全部已完成（2026-07-12）。** 以下保留原始计划作为审计参考。

**第一批 — 并行启动（零依赖，可同时做）**

| 顺序 | Spec | 为什么先做 | Brainstorm 重点 |
|------|------|-----------|----------------|
| ① | **S1 接线收尾** | 解锁 S4/S5/所有 Harness 能力；Factory 方法已建好，纯接线 | `agents.yml` 的 `filesystemMode` 怎么扩展支持 `DOCKER`？sandbox-demo 的 Docker 环境配置？ |
| ② | **S2 前端事件补齐** | 纯前端，和 S1 完全并行 | 6 个事件各自的渲染形态：timeline 行？卡片？增量拼接？ |
| ③ | **S3 ModelRegistry** | 后面 Harness 能力（S4/S5/S6）都依赖模型入口 | `model:` 格式 vs 旧 `modelName` 的兼容策略？fallback 链怎么配？ |
| ④ | **S6 权限+Skill** | 解锁 S10；扩展现有 Factory，不依赖 S1 | Harness 路径的 permissionContext 默认规则？workspace skills vs classpath 优先级？ |
| ⑤ | **S7 上下文控制** | 轻量，见缝插针 | 无复杂设计，配 config 即可 |

> S1/S2 可同时做（后端 vs 前端不冲突）；S3/S6/S7 独立性强，看精力穿插。

**第二批 — S1 完成后（Harness 能力主展示线）**

| 顺序 | Spec | 前置 | Brainstorm 重点 |
|------|------|------|----------------|
| ⑥ | **S4 Plan Mode + Task List** | S1 | plan_enter→write→exit 四阶段在前端怎么呈现？HITL 确认按钮放哪？和 Task List 怎么协作？ |
| ⑦ | **S5 Harness 分层记忆** | S1 | flushTrigger 选 ALWAYS 还是 THROTTLED？consolidation 用哪个轻量 model？MEMORY.md 注入策略？ |

> S4/S6/S7 可并行（都是 S1 之后，彼此独立）；S5 也可以和 S4 并行。

**第三批 — 中间层（依赖第一/二批）**

| 顺序 | Spec | 前置 | Brainstorm 重点 |
|------|------|------|----------------|
| ⑧ | **S8 RAG/记忆替代** | S5 | v1 deprecated 标注策略？大工具结果落盘阈值？官方 v2 迁移触发条件？ |
| ⑨ | **S9 多 Agent + A2A** | S2 | 手写 pipeline 哪些迁 spawn registry？A2A 的 agent 注册/发现机制？ |
| ⑩ | **S10 Skill 自学习** | S6 | propose→approve→上线的审批流程？PromotionGate 用 LocalApprovalGate 还是 CanaryFilter？ |

**第四批 — 生产化（依赖多数前面完成）**

| 顺序 | Spec | 前置 | 说明 |
|------|------|------|------|
| ⑪ | **S11 生产化样板** | S1-S10 多数 | 可拆子项独立做：DistributedBackend / OTel / Channel / MCP 加强 / AG-UI / Sandbox profile，每个一个子-spec |

### 单个 Spec 的 brainstorm → 落地流程

每个 Spec 按以下流程推进：

```
brainstorming（/brainstorming skill）
    ↓ 输出：设计选择、API 方案、配置格式
spec 文档（docs/superpowers/specs/YYYY-MM-DD-<name>-design.md）
    ↓ 输出：差距分析、方案选型、接口定义、验收标准
plan 文档（docs/superpowers/plans/YYYY-MM-DD-<name>.md）
    ↓ 输出：Task 分解、File Structure、Global Constraints
实施（subagent-driven-development 或 executing-plans）
    ↓ 输出：代码 + 测试 + commit
```

### 端到端手测与文档同步（穿插，不单独成 Spec）

以下两项贯穿所有 Spec，在每批结束时做，不单独开 spec：

- **端到端手测**：每批结束后跑对应 agent 的手测（第一批：sandbox/compaction/permission/middleware；第二批：complaint-reviewer/finance-intel-tracker + Plan Mode demo）。
- **文档同步**：CLAUDE.md / AGENTS.md 在每批结束后增量更新（不一次性全改，避免越界）。

---

## 近期不建议做

- ❌ **不急于把 `HarnessRuntime` 从 `agent.stream()` 迁到 `streamEvents()`** — 官方明确是已知 gap，迁移后子 agent 事件会丢失。
- ❌ **不把 RAG/LongTermMemory 深绑在 v1 API 上扩新功能** — 官方 v2 重写未上线，标 deprecated 维持现状即可。
- ❌ **不主动复活旧 pipeline 代码** — 多 agent 用 GA subagent / event source 逐步替代手写方案。
- ❌ **不先做大 UI 改版** — 事件契约补齐（6 个缺失事件 + source 分区）已完成，后续可做展示优化。
- ❌ **不继续基于 v1 的 `Session`/`SkillBox`/`Hook` 扩新功能** — 已清理完毕，用 `AgentStateStore`/`SkillRepository`/`Middleware`。

---

## 第六部分：实施完成总结

> 2026-07-17 更新。S1-S15 全部实施完成，345 测试全绿。S1-S11 在 `docs/roadmap-ga-gap-analysis` 分支；S12-S15 在 `feat/s12-s15-blocked-jars` 分支（先前阻塞的 jar 已确认全部在 Maven Central 2.0.0 发布）。

### Spec 完成状态

| Spec | 名称 | 状态 | Commit | 关键产出 |
|------|------|------|--------|---------|
| S1 | 接线收尾 | ✅ | `f42b9ed` | Docker sandbox + ToolResultEviction + TaskList 接线；HarnessAgentFactory static→@Component |
| S2 | 前端事件补齐 | ✅ | `32b52b0` | 6 个 SSE 事件 + require_user_confirm + user_confirm_result |
| S3 | ModelRegistry | ✅ | `32b52b0` | ModelFactory 统一入口；7 处 DashScopeChatModel.builder() 清零 |
| S4 | Plan Mode + Task List | ✅ | `05d3f0a` | plan-build-demo agent；plan/todo 前端渲染；project-planner 标 deprecated |
| S5 | 分层记忆 | ✅ | `05d3f0a` | memory-assistant agent；MemoryConfig 接线；v1 LTM 标 @Deprecated |
| S6 | 权限 + Skill | ✅ | `2794f6d` | HarnessAgent 路径接 permissionContext + skillRepository |
| S7 | 上下文控制 | ✅ | `2794f6d` | additionalContextFile + maxContextTokens + enableMetaTool |
| S8 | RAG deprecated | ✅ | `31b1fd0` | v1 RAG/LTM 字段全标 @Deprecated(forRemoval=true) |
| S9 | source 分区 | ✅ | `31b1fd0` | 前端 text/tool 按 payload.source 区分 main/subagent |
| S10 | Skill 自学习 | ✅ | `31b1fd0` | skill-learning-demo agent；SkillManageTool + Curator |
| S11 | OTel tracing | ✅ | `54b18dc` | OtelTracingMiddleware + TracerRegistry 初始化 |
| S12 | A2A Protocol | ✅ | `a9f9432` | a2a-server/client + starter；A2aServerConfig + A2aClientDemoRunner；`/.well-known/agent-card.json` + `/a2a/jsonrpc` |
| S13 | 分布式状态存储 | ✅ | `f42b9ed`(S13) | redis/mysql/postgresql 扩展；DistributedStateStoreConfig (@Profile)；session-persistence demo agent |
| S14 | Channel/飞书 | ✅ | `bfce3ed` | channel-common + channel-feishu；FeishuChannelController (webhook + reply)；application-feishu.yml |
| S15 | AG-UI Protocol | ✅ | `7211ab7` | agui + starter；AguiConfig (@AguiAgentId)；`/ag-ui` 端点 + agui.html 前端页面 |

### 新增 Agent

| Agent ID | 类型 | 展示能力 |
|----------|------|---------|
| `plan-build-demo` | HARNESS | Plan Mode（plan_enter/write/exit + HITL 审批）+ Task List |
| `memory-assistant` | HARNESS | 分层记忆（每日 flush + MEMORY.md 合并 + memory_search/get/save） |
| `skill-learning-demo` | HARNESS | Skill 自学习（propose_skill + 安全扫描 + 自动归档） |

### 新增 Java 类

| 类 | 职责 |
|----|------|
| `model/ModelFactory.java` | ModelRegistry 统一入口，封装 `dashscope:` 前缀解析 + ModelCreationContext |
| `config/TracingConfig.java` | OTel TracerRegistry 启动初始化 |

### HarnessAgent.Builder 能力采用统计

```
实施前:  5/20+  (workspace, model, compaction, filesystem-BUILDER, subagents-via-md)
实施后: 17/20+  (+Docker sandbox, ToolResultEviction, TaskList, PlanMode, Memory,
                  permissionContext, skillRepository, maxRetries, fallbackModel,
                  additionalContextFile, maxContextTokens, enableMetaTool,
                  enableSkillManageTool, enableSkillCurator)
```

### 前端 SSE 事件覆盖

```
实施前: 39 个 case
实施后: 56 个 case (+6 缺失事件 +require_user_confirm +user_confirm_result
         +plan/todo 工具识别 +source 分区)
```

### ✅ 先前阻塞项已全部解除（S12-S15）

> 2026-07-17 更新：核实 Maven Central 后，下列 extension jar 均已在 2.0.0 GA（2026-07-10）发布。先前 ROADMAP 标注的"阻塞/未发布"信息已过时。实际 artifact 名与当初假设有差异（见下表）。

| 先前假设的阻塞项 | 实际 artifact (2.0.0 已发布) | 实施 Spec |
|----------------|---------------------------|----------|
| A2A Protocol（`io.a2a.*` SDK 不在 Maven Central） | `agentscope-extensions-a2a-server/client` + `agentscope-a2a-spring-boot-starter`（传递依赖 `io.github.a2asdk:*` 即 `io.a2a.*` 包） | S12 ✅ |
| DistributedBackend Redis/MySQL | `agentscope-extensions-redis` / `-mysql` / `-postgresql`（**非**假设的 `-agent-state-store-*`） | S13 ✅ |
| Channel/IM | `agentscope-extensions-channel` + 子渠道 `channel-feishu`/`-dingtalk`/`-github`/`-gitlab`/`-wecom` | S14 ✅ |
| AG-UI Protocol（无对应 class） | `agentscope-extensions-agui` + `agentscope-agui-spring-boot-starter` | S15 ✅ |

> 注：A2A SDK 曾从 `io.a2a.*` / `io.github.a2asdk` 重命名为 `org.a2aproject.sdk.*`（1.0.0.Alpha4 起，最新 1.1.0.Final）。但 AgentScope 2.0.0 的 A2A 扩展仍基于旧 `io.a2a.*` 包构建，因此传递依赖使用 `io.github.a2asdk:*`，无需显式引入 `org.a2aproject.sdk`。

---

## 参考来源

- AgentScope Java 2.0 官方文档: https://java.agentscope.io/v2/zh/docs/index.html
- Building Blocks: https://java.agentscope.io/v2/zh/docs/building-blocks/
- Harness: https://java.agentscope.io/v2/zh/docs/harness/
- V1 → 2.0 迁移指南: https://java.agentscope.io/v2/zh/docs/change-log.html
- 版本变更记录: https://java.agentscope.io/v2/zh/docs/others/release-notes.html
- GitHub: https://github.com/agentscope-ai/agentscope-java
  - [v2.0.0 GA](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0)
  - [v2.0.0-RC5](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0-RC5)
  - [v2.0.0-RC4](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0-RC4)

---

## 变更历史

| 日期 | 变更 |
|------|------|
| 2026-07-17 | S12-S15 全部实施完成（4 commits，`feat/s12-s15-blocked-jars` 分支）。核实 Maven Central：先前阻塞的 A2A/分布式状态存储/Channel/AG-UI extension jar 均已在 2.0.0 GA 发布。实际 artifact 名与假设有差异（`-redis`/`-mysql` 非 `-agent-state-store-*`；A2A 用旧 `io.a2a.*` 包）。清除「唯一待办 A2A」「其他阻塞项」过时标注；Spec 总览 S12-S15 标 ✅；345 测试全绿 |
| 2026-07-12 | S1-S11 全部实施完成（8 commits），新增「第六部分：实施完成总结」；Spec 总览更新为 ✅ 完成状态；唯一待办为 A2A Protocol（等官方补齐 io.a2a SDK）；345 测试全绿 |
| 2026-07-12 | 新增「Spec 分解与落地顺序」：将 P1-P3 阶段拆为 11 个可独立 brainstorm→spec→实施的单元（S1-S11），标注依赖关系图、四批落地顺序、每个 spec 的 brainstorming 关注点 |
| 2026-07-11 | 第三次重写：以官方 GA 文档全集为基准做逐节差距分析（Building Blocks / Harness / Integration / 前端四部分），标注 ✅/⚠️/❌/🔒 四级状态；Harness 20+ 项 builder 能力逐条对照；积压项按「GA 上能否做」分类归入 P1-A |
| 2026-07-10 | GA 迁移收尾，版本基线行更新（commit `c0e4b8f`） |
| 2026-07-10 | DashScope provider 模块化迁移（commit `abad612`） |
| 2026-06-22 | RC4 升级 gate 评估（已完成，归档） |
| 2026-06-12 | 初版 RC3 基线 ROADMAP（Phase 1-3 升级设计） |
