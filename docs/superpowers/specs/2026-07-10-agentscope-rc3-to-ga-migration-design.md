# AgentScope 2.0.0-RC3 → 2.0.0 GA 迁移设计

> 日期: 2026-07-10
> 状态: 设计已确认，待实现
> 目标版本: `agentscope 2.0.0`（GA，2026-07-10 发布）
> 当前基线: pom 锁定 `2.0.0-RC5`、Java 代码仍是 RC3 import 路径；RC3 下 341 测试全绿

## 1. 背景与现状

### 1.1 项目当前状态
- `pom.xml` 的 `agentscope.version` 已被改为 `2.0.0-RC5`（commit `871547d`），但 Java 代码仍是 RC3 的 import 路径（`DashScopeChatModel` 等仍在 `io.agentscope.core.*`）。
- 这是"版本号先行、代码未跟"状态。由于 RC5 已将 DashScope provider 从 core 模块化移出，**当前 pom 在 RC5 下实际无法编译**——必须连同代码一起迁到 GA。
- 项目核心功能在 RC3 上稳定运行，详见既有设计文档 `docs/superpowers/specs/2026-06-12-agentscope-2.0-upgrade-design.md` 与 `ROADMAP.md`。

### 1.2 RC3 → GA 的变更实质
- **RC4 (2026-06-18)**：新增（Harness 异步工具、持久化 spawn registry）+ 修复，无 breaking。
- **RC5 (2026-07-07)**：**唯一的 breaking change —— 模型提供商模块化**。OpenAI / Gemini / Anthropic / DashScope / Ollama 从 `agentscope-core` 拆为独立的 `agentscope-extensions-model-*` 模块。
- **GA (2026-07-10)**：相对 RC5 **零 breaking**，仅新增（`AllToolsDeniedEvent`、`PostgresDistributedStore`、DashScope Spring Boot builder customizers）+ 修复（`seedSystemMsg` NIO block、Anthropic 并行工具调用拆分等）。

> 结论：RC3 → GA 的全部 breaking 集中在 RC5 的 provider 模块化一项。RC4 与 GA 均无破坏性变更。

### 1.3 GA 发布情况
- v2.0.0 GA 于 2026-07-10 03:10 UTC 发布（commit `44c304e`）。GitHub release 当前仍标 "Pre-release"，**Maven Central 同步状态待确认**（见前置 Gate）。

## 2. 迁移范围与目标

### 2.1 目标（纯技术迁移）
让项目在 2.0.0 GA 上：
1. `mvn clean compile` 通过
2. `mvn test` 341 测试全绿
3. 核心功能端到端不回归

### 2.2 方案选型
采用**方案 A：最小依赖补丁**——仅加 `agentscope-extensions-model-dashscope` 依赖 + 版本号到 GA + DashScope 类包名迁移。贴合现状（项目用 `DashScopeChatModel.builder()` 手动构建模型，未用 auto-config model bean）。

### 2.3 明确不做（ROADMAP 积压项，留给后续）
- `HarnessRuntime` 迁 `streamEvents()`
- compaction / sandbox Factory 接线到 `HarnessAgentFactory`
- 前端 7 个新 SSE 事件类型渲染
- `CLAUDE.md` 架构描述同步
- RAG / LTM v2 迁移（保持 v1，与 RC3 一致）

## 3. 关键调研结论

### 3.1 DashScope provider 模块化（RC5 breaking 的核心）
- DashScope 等 provider 从 `agentscope-core` 拆到 `agentscope-extensions-model-dashscope`（PR #1947，6/29）。
- `DashScopeChatModel` 新包名：`io.agentscope.extensions.model.dashscope.DashScopeChatModel`（`DashScopeModelProvider` 同模块）。
- `DashScopeChatFormatter`（旧 `io.agentscope.core.formatter.dashscope`）、`DashScopeTextEmbedding`（旧 `io.agentscope.core.embedding.dashscope`）同迁移到 dashscope 扩展，确切子包编译时确认。

### 3.2 DeepSeek provider 归属（消除最大不确定性）
- GA 的 ModelProvider SPI 仅内置 OpenAI / DashScope / Gemini / Anthropic / Ollama 五个，**无独立 DeepSeek provider**。
- 项目通过 `DashScopeChatModel.builder()` 直接构建模型，`deepseek-v4-flash` 作为 model-name 传入（百炼 DashScope 代理 DeepSeek），**不走 ModelRegistry 字符串正则解析**（故不受 `dashscope:.+` / `qwen.+` 匹配规则影响）。
- 结论：deepseek 不构成技术障碍，builder 调用与 model-name 不变。

### 3.3 配置兼容性
- `application.yml` 的 `agentscope.dashscope.*` + `provider: dashscope` 在 GA 完全兼容（dashscope 仍为默认 provider），无需改动。

## 4. 前置 Gate（不可绕过）

GA（GitHub 仍标 Pre-release）必须先满足 Maven 可用性：
```bash
mvn dependency:get -Dartifact=io.agentscope:agentscope-core:2.0.0 -U
mvn dependency:get -Dartifact=io.agentscope:agentscope-extensions-model-dashscope:2.0.0 -U
```
**两条都成功才动 pom。** Maven 未同步前改 pom 会导致编译断裂（ROADMAP 已警示）。

## 5. 改动清单

### 5.1 pom.xml
- `agentscope.version`: `2.0.0-RC5` → `2.0.0`
- 新增依赖：`io.agentscope:agentscope-extensions-model-dashscope:${agentscope.version}`
- 其余依赖（core / harness / starter / rag-simple / memory-bailian）随 `${agentscope.version}` 自动升 GA

### 5.2 代码包名迁移（3 文件，编译驱动）
| 类 | 旧包 (core) | 新包 (extensions) | 落点 |
|----|----|----|----|
| `DashScopeChatModel` | `io.agentscope.core.model` | `io.agentscope.extensions.model.dashscope` | `AgentFactory` / `CompositeAgentFactory` / `HarnessAgentFactory` |
| `DashScopeChatFormatter` | `io.agentscope.core.formatter.dashscope` | dashscope 扩展内（编译确认子包） | `AgentFactory` / `CompositeAgentFactory` |
| `DashScopeTextEmbedding` | `io.agentscope.core.embedding.dashscope` | dashscope 扩展内（编译确认子包） | `KnowledgeService` |

builder 调用与方法签名不变，纯 import 迁移。

### 5.3 配置（零改动）
- `application.yml` / `agents.yml` / `harness-agents.yml` 均不改。

## 6. 验证矩阵

### 6.1 自动化
- `mvn clean compile`
- `mvn test`（341 全绿）

### 6.2 端到端手测（起服务，每类至少一个代表 agent）
- **SINGLE**：`chat-basic` / `tool-test-simple`（对话 + 工具调用）
- **多 agent**：`doc-analysis-pipeline`(SEQUENTIAL) 或 `smart-router`(ROUTING)（流式编排）
- **HARNESS**：`compaction-demo`（仍走 `agent.stream()`，RC3 已知限制，本次不改）
- **MCP**：任一 mcp agent（远程工具调用）
- **RAG**：`rag-chat`（**重点**：走 `DashScopeTextEmbedding`，包名迁移后必验）
- **文件上传 + 文档解析**：`task-document-analysis`
- **HITL 审批**：`contract-review-workflow` 或银行发票

### 6.3 重点回归
- 所有走 `DashScopeChatModel` 的路径
- RAG embedding 路径（`DashScopeTextEmbedding` 包名迁移后）

## 7. 风险与回滚

| 风险 | 处理 |
|----|----|
| Maven artifact 未同步 | Gate 拦截，不启动 |
| `DashScopeChatFormatter` / `DashScopeTextEmbedding` 新包名不确定 | 编译驱动确认，低成本 |
| `deepseek-v4-flash` 百炼账户不支持 | 端到端暴露，fallback 改回 `qwen-plus` |
| RC3→GA 跨 RC4/RC5 的未预期 API 变更 | 唯一确定 breaking 是 provider 模块化；其余靠编译 + 单测兜底 |

**回滚**：在 `agentscope-latest-upgrade` 分支，迁移作为独立 commit；失败 `git revert` 该 commit 回 RC3 基线。已提交的 trace 增强改动（`871547d`）不受影响。

## 8. 执行顺序
1. Gate 检查（Maven 可用性）
2. pom 改动（版本 + dashscope 扩展依赖）
3. 编译，按报错迁移包名（编译驱动）
4. `mvn test` 单测全绿
5. 端到端手测
6. 独立 commit
7. 轻量收尾（更新本设计文档状态行 + `ROADMAP.md` 版本基线行；**不**做 `CLAUDE.md` 架构描述等积压项）

## 9. 参考来源
- GA Release: https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.0
- Release Notes: https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/others/release-notes.md
- Model Provider SPI: https://java.agentscope.io/v2/zh/docs/19-model-provider-spi
- Spring Boot Starters: https://java.agentscope.io/v2/zh/docs/21-spring-boot-starters
- 既有 RC3 迁移设计: `docs/superpowers/specs/2026-06-12-agentscope-2.0-upgrade-design.md`
