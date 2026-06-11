# AgentScope Java 2.0 Evolution Roadmap

> Last reviewed: 2026-06-11  
> Current local baseline: Spring Boot 3.5.14, Java 17, `agentscope.version=2.0.0-RC1`

## 参考来源

- AgentScope Java 2.0 官方文档: https://java.agentscope.io/v2/zh/intro.html
- AgentScope Java V1 迁移指南: https://java.agentscope.io/v2/zh/docs/change-log.html
- AgentScope Java Release Notes: https://java.agentscope.io/v2/zh/docs/others/release-notes.html
- AgentScope Java GitHub: https://github.com/agentscope-ai/agentscope-java
- 用户给出的公众号文章: https://mp.weixin.qq.com/s/X2p4olp1gPzQlmIMVKN2rw

> 说明: 当前环境无法直接读取公众号正文，本文以官方文档、Release Notes、GitHub README 与本地代码为准；公众号文章可作为后续人工复核材料。

## 当前项目状态

项目已经不是一个简单 1.x demo，而是一个混合态的 2.0 迁移项目：

- 已升级到 `2.0.0-RC1`，`mvn -q -DskipTests compile` 当前通过。
- 已有 Spring Boot UI、SSE、文档解析、结构化输出、RAG、长期记忆、MCP、权限 demo、middleware demo、Harness 适配层、多模态上传、工作流记录等能力。
- 仍大量依赖 1.x/RC1 兼容 API：`Hook`、`SkillBox`、`agent.stream()`、`Event/EventType`、`Session/SessionKey`、`RAGMode/Knowledge/RetrieveConfig`、`LongTermMemory`、`StructuredOutputReminder`。
- `AgentRuntimeFactory` 中 `HARNESS` 分支仍 fallback 到 SINGLE，但 `AgentService` 已经能绕过它走 `HarnessAgentService`。
- Pipeline 包删除后，`SEQUENTIAL/PARALLEL/DEBATE/LOOP/MSG_HUB/SUBAGENT_SEQ/SUBAGENT_PAR` 已禁用，尚未用 2.0 subagent/middleware/event stream 重建。
- `docs/superpowers/specs/2026-06-09-agentscope-2.0-upgrade-design.md` 已明确把 Hook、SkillBox、Memory、streamEvents、Harness、Permission、Workspace 等作为后续工作。

## 优先级原则

1. 先追 RC3/stable API，避免继续在 RC1 兼容层上扩功能。
2. 先迁移事件、状态、权限这些横切契约，再做展示型 demo。
3. 保留当前可运行 UI 和样例，不一次性重写全部 agent。
4. 每一阶段都要有一个前端可演示场景和一组回归测试。

## P0: 版本基线与破坏性迁移

目标: 从 `2.0.0-RC1` 对齐到当前官方 2.0 RC3 或正式版，并消除会在近期删除的核心断点。

任务:

- 升级 `pom.xml` 的 `agentscope.version` 到当前官方版本，补齐 extension 坐标变化。
- 按 RC2/RC3 Release Notes 处理 `Session` 到 `AgentStateStore + RuntimeContext(userId, sessionId)` 的迁移。
- 移除或隔离 `sessionKey(SimpleSessionKey.of(agentId))`、`JsonSession`、`InMemorySession` 这类 RC1 过渡实现。
- 给 `AgentFactory` 增加 provider 字符串模型配置入口，例如 `dashscope:qwen-plus`，为后续 `ModelRegistry`、fallback model 做准备。
- 增加一次 "2.0 API smoke test"，覆盖普通 ReActAgent、工具调用、结构化输出、session 恢复。

验收:

- `mvn -q -DskipTests compile`
- `mvn test`
- Basic Chat、Tool Calling、Document Analysis 在 UI 上仍可用。

## P1: 事件流与可观测体系正迁移

目标: 用 2.0 原生 `streamEvents()` + `AgentEvent` 替换当前 `Hook + agent.stream()` 的双流拼装。

任务:

- 将 `AgentRuntime.stream()` 改为消费 `agent.streamEvents(new UserMessage(...), RuntimeContext)`。
- 建立 `AgentEvent -> SSE Map` 的统一转换层，覆盖:
  - text/thinking delta
  - tool call start/delta/end
  - tool result delta/end
  - `AgentResultEvent`
  - `CustomEvent`
  - `HintBlockEvent`
  - `RequireUserConfirmEvent` / `UserConfirmResultEvent`
  - `AgentEvent.source`，用于 main/subagent 分流展示
- 将 `ObservabilityHook` 迁移为 `MiddlewareBase` 或事件流消费器，避免继续依赖 `io.agentscope.core.hook.*`。
- 前端 debug panel 改为按类型化事件渲染，不再推断工具名缓存；RC3 的 `toolCallName` 可直接使用。

验收:

- Debug panel 能展示 LLM、thinking、tool call、tool result、final result。
- 合同审批、银行发票这类受控工具仍能暂停、确认、恢复。
- `Hook` import 数量显著下降，新增事件转换测试。

## P1: 权限与 HITL 统一

目标: 把自定义 `ApprovalHook` 与 AgentScope 2.0 Permission/HITL 统一，形成真正的权限 demo。

任务:

- 将 `approvalTools` 映射为 `PermissionContextState` 的 ask rules。
- 把 `permissionMode` 前端选择器对齐官方模式: `EXPLORE`、`ACCEPT_EDITS`、`BYPASS`、`DONT_ASK`。
- 消费 `RequireUserConfirmEvent` 生成当前前端的 `pending_approval` SSE。
- 用 `UserConfirmResultEvent` 走恢复，不再靠临时 `StopAfterApprovedToolHook`。
- 保留合同报告、银行发票作为真实业务审批样例。

验收:

- 同一个工具在不同 mode 下能 allow/ask/deny。
- 审批状态跨 session 不丢。
- 权限模式切换有后端测试和前端手测脚本。

## P1: Skill 与工具系统对齐

目标: 从 `SkillBox` 迁到 `AgentSkillRepository`，并把工具能力沉淀为 2.0 demo。

任务:

- 用 `ClasspathSkillRepository` / `FileSystemSkillRepository` 注册 skills，替代 `builder.skillBox(skillBox)`。
- 引入 `skillFilter` 和 `dynamicSkillsEnabled` 配置项，区分普通 ReActAgent 与 HarnessAgent。
- 将本地 tools 按 `ToolGroup` 分组: document、invoice、search、filesystem、approval。
- 梳理 `systemTools/userTools/approvalTools` 的边界，形成文档化的安全默认值。

验收:

- `docx/pdf/xlsx/bank_invoice_java` skill 不依赖 SkillBox 仍能加载。
- 工具列表、skill 列表、前端 agent 预览一致。

## P2: Harness 工程化主线

目标: 让 Harness 不只是补充 demo，而是 2.0 工程化能力主展示线。

任务:

- 移除 `AgentRuntimeFactory` 中 `HARNESS -> SINGLE` fallback，统一由 `HarnessAgentService` 管理。
- 升级 `HarnessRuntime` 到 `HarnessAgent.streamEvents()`，展示 `source` 路径和子 agent 事件转发。
- 引入 `LocalFilesystemSpec.projectWritable`，做一个代码生成/文档修改类 demo。
- 增加 Plan Mode demo: `enablePlanMode()` + 用户确认后写入。
- 增加 Task List demo: `enableTaskList(true)`，替代旧 `PlanNotebook/create_plan` 心智。
- Harness workspace 中沉淀 `AGENTS.md`、`MEMORY.md`、skills、subagents、tools.json 白名单。

验收:

- Complaint Reviewer 或 Finance Intel Tracker 能完整跑主 agent + subagents。
- 前端能区分 main/subagent 输出。
- workspace 文件读写可审计、可恢复。

## P2: 多智能体模式重建

目标: 用 2.0 推荐的 subagent、supervisor、handoffs、routing 和 event stream 重建已禁用的 7 类多 agent 模式。

任务:

- `SEQUENTIAL`: 用 subagent 同步调用或轻量 workflow 重新实现。
- `PARALLEL`: 用后台委派 + source event 聚合。
- `DEBATE`: 用 declared subagents + round controller 重新实现。
- `LOOP`: 用 state graph 或 middleware 控制迭代上限。
- `MSG_HUB`: 对齐 2.0 `MsgHub`/团队消息能力，避免复活旧 pipeline。
- `SUBAGENT_SEQ/PAR`: 迁到 Harness subagent API。
- 每个模式保留一个最小可演示 agent，不追求一次性恢复旧全部复杂度。

验收:

- 配置中不再有 "disabled during AgentScope 2.0 migration" 的用户可见断路。
- Multi-Agent debug panel 能展示每个 agent source、耗时、工具调用。

## P2: RAG、记忆与上下文工程

目标: 从旧 core RAG/LongTermMemory API 迁到 2.0 integration/harness 风格。

任务:

- 评估 `agentscope-extensions-rag-simple` 在当前 2.0 版本的正式 API，替换 `builder.knowledge().ragMode().retrieveConfig()`。
- 把 `KnowledgeService` 拆成 ingestion、store、retrieval、status 四个边界，减少与 AgentFactory 耦合。
- 为个人助手迁移长期记忆配置，优先使用 2.0 memory integration 或 Harness memory。
- 引入 compaction 独立模型配置，避免长对话压缩占用主模型。
- 对大工具结果做落盘占位，配合 2.0 context overflow 兜底。

验收:

- RAG Chat 和 Agentic RAG 均可运行。
- 重启后知识库索引状态可恢复。
- 长对话 demo 能触发压缩并在 debug panel 中看到事件。

## P2: 模型与生产容错

目标: 展示 2.0 模型统一入口和容错能力。

任务:

- `agents.yml` 支持 `model: dashscope:qwen-plus` 形式，兼容旧 `modelName`。
- 增加 `maxRetries`、`fallbackModel`、`stopOnReject` 配置。
- 支持 OpenAI-compatible、DeepSeek、Ollama 的示例配置，但默认仍使用 DashScope。
- 对模型调用错误增加可展示事件和前端错误卡片。

验收:

- 主模型失败时能切 fallback model。
- 不同 agent 可用不同 provider，不改 Java 代码。

## P3: 分布式、协议与生态对齐

目标: 从 demo 走向企业级部署样板。

任务:

- 引入 `DistributedBackend` 抽象: Redis/MySQL/OSS 至少实现一个可选 profile。
- 添加 OpenTelemetry middleware，接入 traceId 到 SSE/debug panel。
- 对齐 Studio/可视化调试能力，评估是否可以导出事件日志。
- 加强 MCP demo: `mcp-servers.yml` + workspace `tools.json` 白名单 + tool group 动态激活。
- 增加 A2A demo: 一个 agent 注册能力，另一个 agent 通过协议调用。
- 评估 AG-UI/Channel: 飞书/钉钉/GitHub IM 接入可以作为独立 showcase。
- 沙箱能力拆包后补充 Docker sandbox profile。

验收:

- 本地单机、Redis 分布式 profile 都能启动。
- 事件日志可追踪到单次用户请求。
- MCP/A2A 至少各有一个可运行样例。

## Demo 对齐矩阵

| 官方 2.0 能力 | 当前项目已有基础 | 建议 demo |
| --- | --- | --- |
| ReActAgent quickstart | Basic Chat | 保留为最小基线 smoke demo |
| Message & Event | SSE + debug panel | `streamEvents()` 类型化事件观察器 |
| Middleware | `middleware/` 三个示例类 | 审计日志、限流、上下文注入三合一 demo |
| Permission System | `PermissionContextFactory` + approvalTools | 文件写入 allow/ask/deny + 合同报告审批 |
| Tool System / ToolGroup | `ToolRegistry`、MCP toolGroups | 文档/搜索/文件系统工具分组激活 |
| Structured Output | 发票、身份证、合同 schema | 结构化提取 + 自动纠错失败样例 |
| Harness Workspace | `harness/` package、workspace templates | Complaint Reviewer 多 agent workspace |
| Plan Mode / Task List | 旧 `planEnabled` | 只读规划 -> 用户确认 -> 写入执行 |
| Subagent Streaming | Harness subagents | Finance Intel Tracker 子 agent source 展示 |
| RAG Knowledge Base | `KnowledgeService` | 本地知识库 + Agentic RAG |
| Memory / Compaction | 长对话、个人助手 | 独立轻量模型压缩与偏好记忆 |
| MCP | `mcp/` package、mcp-servers.yml | 文件系统/搜索 MCP 动态挂载 |
| A2A Protocol | 暂无完整 demo | 订单履约 agent 调用库存/客服 agent |
| DistributedBackend | 暂无 | Redis state store + 多副本 session 漂移恢复 |
| Channel / AG-UI | 暂无 | 飞书或 GitHub channel 独立 showcase |

## 建议排期

### Sprint 1: RC3/stable 基线

- 升级依赖与 extension 坐标。
- 完成 `AgentStateStore + RuntimeContext` 状态迁移。
- 保证核心单 agent demo 可跑。

### Sprint 2: 事件与权限

- 迁移 `streamEvents()`。
- 前端 debug panel 支持 AgentEvent。
- 权限/HITL 替换自定义 ApprovalHook。

### Sprint 3: Skill、工具、模型配置

- `SkillBox -> SkillRepository`。
- ToolGroup 和 MCP 分组完善。
- ModelRegistry/fallback model 配置化。

### Sprint 4: Harness 主展示线

- Harness runtime 正式接管 HARNESS agent。
- Plan Mode、Task List、projectWritable demo。
- 子 agent event source 可视化。

### Sprint 5: RAG/Memory/Multi-Agent 恢复

- RAG 与长期记忆迁到 2.0 推荐接口。
- 恢复 sequential/parallel/debate 等多 agent 模式。
- 增加端到端场景测试。

### Sprint 6: 生产化样板

- DistributedBackend profile。
- OTel/Studio/MCP/A2A/Channel/Sandbox showcase。
- README、AGENTS.md、demo 脚本和架构图同步。

## 近期不建议做

- 不建议继续基于 RC1 的 `Session`、`SkillBox`、`Hook` 扩新功能。
- 不建议立刻恢复旧 pipeline 代码；应以 2.0 subagent/event source 重新建模。
- 不建议把 RAG/LongTermMemory 深绑在旧 `builder.knowledge()` / `builder.longTermMemory()` 上。
- 不建议先做大 UI 改版；事件契约稳定后再做展示优化。

## 下一步可执行任务

1. 建 `docs/superpowers/specs/2026-06-11-agentscope-2-roadmap-execution.md`，把 P0/P1 细化为可执行规格。
2. 开分支 `codex/agentscope-2-roadmap-p0`。
3. 先升级到 RC3/stable 并修编译。
4. 为 `streamEvents()` 写事件转换器测试，再替换运行时。
5. 跑 `mvn test`，再补 UI 手测清单。
