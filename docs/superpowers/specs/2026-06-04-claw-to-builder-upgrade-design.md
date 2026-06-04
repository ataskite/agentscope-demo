# Claw → Builder 升级设计文档

**日期**: 2026-06-04
**版本**: 1.0
**类型**: 架构升级

---

## 1. 概述

在现有 AgentScope Demo 项目中，将 HARNESS agent 从纯 Claw 模式（单人本机直连）升级为 Claw + Builder 双模式共存。两种模式在同一 Spring Boot 进程中运行，用户选择不同 agent 自动走不同路径。

### 1.1 核心演示目标

展示 AgentScope Harness 的关键承诺：**同一份 Agent 逻辑，按需切换形态**。

- **Claw 模式**：`workspace(Path)` 直连本机文件系统，路径 `~/.agentscope/{agentId}/`
- **Builder 模式**：`LocalFilesystemSpec` + `NamespaceFactory`，路径自动变为 `~/.agentscope/users/{userId}/agents/{agentId}/`

Agent 的工作区模板、技能、子 Agent 声明完全相同，只是运行时容器不同。

### 1.2 范围界定

**包含：**
- `executionMode` 配置区分 CLAW / BUILDER
- `LocalFilesystemSpec` 命名空间隔离（框架内置）
- RuntimeContext 传递 userId（无 JWT，从前端参数传入）
- `agents.yml` 按类型拆分（非 HARNESS 留原文件，HARNESS 移至 `harness-agents.yml`）
- 前端 Builder agent 显示用户选择器

**不包含：**
- JWT / Spring Security 认证
- Agent 分享 ACL
- React SPA 重写
- Remote / Sandbox 文件系统后端
- Channel 路由

---

## 2. YAML 配置拆分

### 2.1 当前状态

`agents.yml` 已增长到 1629 行，包含所有 agent 类型（SINGLE、SEQUENTIAL、PARALLEL、ROUTING、HANDOFFS、DEBATE、LOOP、STATE_GRAPH、MSG_HUB、SUBAGENT_SEQ、SUBAGENT_PAR、HARNESS）。

### 2.2 拆分方案

| 文件 | 内容 |
|------|------|
| `config/agents.yml` | 所有非 HARNESS 类型 agent（截断到 HARNESS 之前的部分） |
| `config/harness-agents.yml` | 所有 HARNESS 类型 agent（CLAW 和 BUILDER 模式） |

### 2.3 AgentConfigService 改造

当前 `@Value("classpath:config/agents.yml")` 只加载一个文件。改为加载多个：

```java
@Value("classpath*:config/*-agents.yml")
private Resource[] configFiles;
```

遍历所有匹配文件，合并到同一个 `configMap` 中。每个文件独立加载，`agentId` 去重（先到先得 + warn 日志）。

---

## 3. 执行模式设计

### 3.1 HarnessConfig 扩展

```java
public class HarnessConfig {
    private String workspace;
    private String filesystemMode = "LOCAL";
    private String executionMode = "CLAW";     // CLAW 或 BUILDER
    private String isolationScope = "USER";    // BUILDER 模式专用：USER / AGENT / GLOBAL
    private CompactionConfig compaction;
    private List<SubAgentRef> subagents = new ArrayList<>();
}
```

### 3.2 agents.yml 示例

**Claw 模式（现有行为不变）：**

```yaml
- agentId: complaint-reviewer
  type: HARNESS
  harnessConfig:
    workspace: "${user.home}/.agentscope/complaint-reviewer"
    filesystemMode: LOCAL
    executionMode: CLAW           # 直连本机文件系统
    compaction:
      triggerMessages: 30
      keepMessages: 10
```

**Builder 模式（新增）：**

```yaml
- agentId: finance-intel-tracker
  type: HARNESS
  harnessConfig:
    workspace: "${user.home}/.agentscope/finance-intel-tracker"
    filesystemMode: LOCAL
    executionMode: BUILDER        # 命名空间隔离
    isolationScope: USER          # 按 userId 隔离
    compaction:
      triggerMessages: 30
      keepMessages: 10
    subagents:
      - name: intel-collector
        description: 情报采集员
```

### 3.3 HarnessAgentFactory 改造

**当前（仅 Claw）：**

```java
HarnessAgent agent = HarnessAgent.builder()
    .workspace(workspace)
    .build();
```

**改造后（双模式）：**

```java
HarnessAgent.Builder builder = HarnessAgent.builder()
    .name(config.getName())
    .model(model);

if ("BUILDER".equals(executionMode)) {
    // Builder 模式：LocalFilesystemSpec + NamespaceFactory
    builder.workspace(workspace)  // 仍然需要 workspace 做模板初始化
           .filesystem(new LocalFilesystemSpec());
} else {
    // Claw 模式：直连本机文件系统（当前行为）
    builder.workspace(workspace);
}
```

框架内部 `LocalFilesystemSpec.toFilesystem(workspace, namespaceFactory)` 会：
1. 从 `RuntimeContext` 获取 `userId`
2. 将路径重写为 `users/{userId}/agents/{agentId}/`
3. 在该路径下使用 `LocalFilesystemWithShell`

### 3.4 userId 传递

**当前**：`RuntimeContext.builder().userId("demo-user").build()`

**改造后**：
- `ChatRequest` 新增 `userId` 字段
- `HarnessAgentService` 从 request 中读取 userId
- Claw 模式忽略 userId，Builder 模式使用 userId 做命名空间隔离

---

## 4. 前端变更

### 4.1 用户选择器

当选择的 agent 为 BUILDER 模式时，在聊天输入区上方显示一个简单的用户切换器：

- 下拉框：`demo-user` / `alice` / `bob` / `charlie`
- 切换用户后，Builder agent 的工作区自动隔离
- Claw 模式的 agent 不显示此选择器

### 4.2 Agent 列表展示

在 agent 列表中，Builder 模式的 agent 显示一个标记（如 `[Builder]`），帮助用户区分两种模式。

### 4.3 调试面板

Builder 模式事件中增加 `namespace` 字段，显示当前命名空间路径，方便验证隔离效果。

---

## 5. 工作区路径对比

```
Claw 模式 (complaint-reviewer):
~/.agentscope/complaint-reviewer/
├── AGENTS.md
├── skills/
├── subagents/
└── agents/              # 子 Agent 运行时数据

Builder 模式 (finance-intel-tracker, userId=alice):
~/.agentscope/finance-intel-tracker/users/alice/agents/finance-intel-tracker/
├── AGENTS.md
├── skills/
├── subagents/
└── agents/

Builder 模式 (finance-intel-tracker, userId=bob):
~/.agentscope/finance-intel-tracker/users/bob/agents/finance-intel-tracker/
├── AGENTS.md
├── skills/
├── subagents/
└── agents/
```

注意：`workspace` 路径仍然是模板初始化的根目录，命名空间隔离发生在运行时层面。

**Builder 模式下的模板初始化**：`WorkspaceInitializer` 仍然基于 `workspace` 根路径执行（与 Claw 相同）。当 `LocalFilesystemSpec` 首次为某用户创建命名空间子目录时，Agent 的 `AGENTS.md`、`skills/`、`subagents/` 等文件会被框架从 workspace 根路径自动投射到用户命名空间下。如果框架不自动投射，则在 `HarnessAgentFactory` 中增加一步：检测用户命名空间目录是否存在，不存在则从模板复制。

---

## 6. 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `config/agents.yml` | 修改 | 移除 HARNESS 类型 agent |
| `config/harness-agents.yml` | 新建 | 所有 HARNESS agent，含 executionMode 字段 |
| `AgentConfigService.java` | 修改 | 支持加载多个 yml 文件 |
| `HarnessConfig.java` | 修改 | 新增 executionMode、isolationScope 字段 |
| `HarnessAgentFactory.java` | 修改 | BUILDER 分支用 LocalFilesystemSpec |
| `HarnessAgentService.java` | 修改 | RuntimeContext 传入 userId |
| `ChatRequest.java` | 修改 | 新增 userId 字段 |
| `ChatController.java` | 修改 | 传递 userId 到 HarnessAgentService |
| `AgentConfig.java` | 修改 | 新增辅助方法判断 executionMode |
| `chat.js` | 修改 | Builder agent 显示用户选择器 |
| `agents.js` | 修改 | agent 列表显示模式标记 |
| `debug.js` | 修改 | Builder 事件显示 namespace |

---

## 7. 演示场景

### 7.1 场景一：Claw 模式演示

1. 选择 `complaint-reviewer`（Claw 模式）
2. 直接对话，agent 在 `~/.agentscope/complaint-reviewer/` 工作
3. 展示工作区驱动自我进化（技能学习、子 Agent 孵化）
4. Shell 命令直接在本机执行

### 7.2 场景二：Builder 模式演示

1. 选择 `finance-intel-tracker`（Builder 模式）
2. 用户选择器出现，选择 `alice`
3. 对话后，agent 在 `~/.agentscope/.../users/alice/agents/.../` 工作
4. 切换用户为 `bob`，再次对话
5. 展示 alice 和 bob 的工作区完全隔离——技能、记忆、子 Agent 互不干扰
6. 对比 Claw 模式，展示"同一份 Agent 逻辑，不同运行时容器"

### 7.3 场景三：对比验证

1. 用 `bob` 在 Builder agent 中学习一个新技能
2. 切换到 `alice`，该技能不存在
3. 证明命名空间隔离生效
4. 回到 Claw agent，展示完全不同的文件系统路径

---

## 8. 后续扩展路径

| 扩展方向 | 基于本设计需要改动的内容 |
|----------|--------------------------|
| JWT 认证 | 加 Spring Security + JwtAuthFilter，userId 从 token 解析 |
| Remote 文件系统 | `HarnessAgentFactory` 新增 `RemoteFilesystemSpec` 分支 |
| Sandbox 沙箱 | 新增 `SandboxFilesystemSpec` 分支 + Docker 配置 |
| Agent 分享 ACL | 新增 `AgentAclService` + 权限校验 |
| Channel 路由 | 新增钉钉/企微/飞书通道适配 |
