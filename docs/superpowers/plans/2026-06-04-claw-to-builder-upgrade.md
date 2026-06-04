# Claw → Builder 升级实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在同一 Spring Boot 进程中同时演示 Claw（本机直连）和 Builder（命名空间隔离）两种 HARNESS 模式，用户选择不同 agent 自动走不同路径。

**Architecture:** 在 `agents.yml` 拆分为两个文件（`agents.yml` + `harness-agents.yml`）的基础上，`HarnessConfig` 新增 `executionMode` 字段区分 CLAW/BUILDER。Builder 模式使用框架内置的 `LocalFilesystemSpec` 实现命名空间隔离，通过 `RuntimeContext.userId` 驱动路径重写。前端为 Builder agent 增加用户选择器。

**Tech Stack:** Spring Boot 3.5.14, AgentScope 1.1.0-RC2 (agentscope-harness), vanilla JS

---

### Task 1: 拆分 agents.yml 为 agents.yml + harness-agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml` (删除 1568-1630 行的 HARNESS agent 定义)
- Create: `src/main/resources/config/harness-agents.yml`

- [ ] **Step 1: 提取 HARNESS agent 到 harness-agents.yml**

读取 `agents.yml` 第 1568-1630 行，将其内容移入新文件 `src/main/resources/config/harness-agents.yml`，保持 YAML 结构相同（顶层 `agents:` 列表）：

```yaml
agents:
  # === Harness Demo: Complaint Reviewer (Claw Mode) ===
  - agentId: complaint-reviewer
    category: collaboration
    type: HARNESS
    name: 投诉复盘分析师
    description: 基于事实链的投诉根因复盘与处置ROI测算平台。上传每日投诉数据，自动完成根因分析→趋势对比→策略优化→ROI测算。
    modelName: qwen-max
    streaming: true
    enableThinking: false
    harnessConfig:
      workspace: "${user.home}/.agentscope/complaint-reviewer"
      filesystemMode: LOCAL
      executionMode: CLAW
      compaction:
        triggerMessages: 30
        keepMessages: 10
        flushBeforeCompact: true
      subagents:
        - name: root-cause-analyst
          description: 根因分析师，基于投诉数据做事实链分析和根因分类
        - name: trend-analyst
          description: 趋势分析师，跨日对比投诉数据变化趋势和预警信号
        - name: strategy-optimizer
          description: 策略优化顾问，基于根因和趋势推荐优化策略
        - name: roi-calculator
          description: ROI 测算师，对不同策略做成本影响测算和模拟
    samplePrompts:
      - prompt: "分析今日投诉数据，做根因分析和ROI测算"
        expectedBehavior: "触发完整分析链：根因分析→趋势对比→策略优化→ROI测算"
      - prompt: "今天的费用类投诉有什么变化趋势？"
        expectedBehavior: "基于历史记忆做跨日趋势对比"

  # === Harness Demo: Finance Intelligence Tracker (Builder Mode) ===
  - agentId: finance-intel-tracker
    category: intelligence
    type: HARNESS
    name: 金融情报追踪助手
    description: |
      自动采集金融行业动态，发现趋势信号，生成分析简报。
      支持银行、证券、保险、基金、信托等子领域情报追踪。
      具备自我进化能力，可自动学习新技能和孵化新子 Agent。
      [Builder 多用户隔离模式]
    modelName: qwen-max
    streaming: true
    enableThinking: false
    harnessConfig:
      workspace: "${user.home}/.agentscope/finance-intel-tracker"
      filesystemMode: LOCAL
      executionMode: BUILDER
      isolationScope: USER
      compaction:
        triggerMessages: 30
        keepMessages: 10
        flushBeforeCompact: true
      subagents:
        - name: intel-collector
          description: 情报采集员，使用 Web 搜索和 Shell 拉取数据，具备搜索策略自我进化能力
        - name: finance-trend-analyst
          description: 趋势分析师，对比历史数据发现趋势信号，维护双层记忆，可自定义检测逻辑
        - name: intel-report-writer
          description: 报告撰写员，生成结构化情报简报，可根据反馈调整报告格式
    samplePrompts:
      - "分析近期银行理财监管政策变化"
      - "关注保险资金运用新规动态"
      - "证券行业风控指引有什么更新"
      - "基金销售新规对行业的影响"
```

- [ ] **Step 2: 从 agents.yml 删除 HARNESS 部分**

从 `src/main/resources/config/agents.yml` 中删除第 1567 行（`# === Harness Demo: Complaint Reviewer ===`）到文件末尾的所有内容。保留第 1566 行（最后一行非 Harness 内容：`expectedBehavior: "拒绝执行，因为没有删除工具权限"`）。

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/config/agents.yml src/main/resources/config/harness-agents.yml
git commit -m "refactor: split HARNESS agents into harness-agents.yml"
```

---

### Task 2: AgentConfigService 支持加载多个 yml 文件

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfigService.java`

- [ ] **Step 1: 修改 Resource 注入方式**

将 `AgentConfigService.java` 中的单文件注入改为多文件注入。

在 `AgentConfigService.java` 中：

将第 33-34 行：
```java
@Value("classpath:config/agents.yml")
private Resource configFile;
```

替换为：
```java
@Value("classpath:config/agents.yml")
private Resource mainConfigFile;

@Value("classpath:config/harness-agents.yml")
private Resource harnessConfigFile;
```

- [ ] **Step 2: 修改 init() 方法加载两个文件**

将第 39-65 行的 `init()` 方法替换为：

```java
@PostConstruct
public void init() {
    List<Resource> configFiles = new ArrayList<>();
    configFiles.add(mainConfigFile);
    if (harnessConfigFile != null && harnessConfigFile.exists()) {
        configFiles.add(harnessConfigFile);
    }

    Yaml yaml = new Yaml(new Constructor(AgentsWrapper.class, new LoaderOptions()));
    for (Resource res : configFiles) {
        try (InputStream is = res.getInputStream()) {
            AgentsWrapper wrapper = yaml.load(is);

            for (AgentConfig config : wrapper.getAgents()) {
                if (config.getAgentId() == null || config.getAgentId().isBlank()) {
                    log.warn("Skipping agent config with missing agentId");
                    continue;
                }
                if (configMap.containsKey(config.getAgentId())) {
                    log.warn("Duplicate agentId: {}, using first occurrence", config.getAgentId());
                    continue;
                }
                configMap.put(config.getAgentId(), config);
                allAgents.add(config);
                log.info("Loaded agent config: {} ({})", config.getName(), config.getAgentId());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load agent config from " + res, e);
        }
    }

    log.info("Loaded {} agent configurations", allAgents.size());

    // Load skill descriptions
    loadSkillDescriptions();
}
```

注意：需要添加 `import java.util.ArrayList;` 和 `import java.util.List;`（`ArrayList` 和 `List` 已在现有导入中）。

- [ ] **Step 3: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfigService.java
git commit -m "feat: AgentConfigService loads both agents.yml and harness-agents.yml"
```

---

### Task 3: HarnessConfig 新增 executionMode 和 isolationScope 字段

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/HarnessConfig.java`

- [ ] **Step 1: 添加新字段**

在 `HarnessConfig.java` 中，在第 14 行 `private String filesystemMode = "LOCAL";` 后添加：

```java
private String executionMode = "CLAW";
private String isolationScope = "USER";
```

以及添加辅助方法：

```java
public boolean isBuilderMode() {
    return "BUILDER".equalsIgnoreCase(executionMode);
}
```

完整的 `HarnessConfig.java` 应该是：

```java
package com.skloda.agentscope.agent;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class HarnessConfig {

    private String workspace;
    private String filesystemMode = "LOCAL";
    private String executionMode = "CLAW";
    private String isolationScope = "USER";
    private CompactionConfig compaction;
    private List<SubAgentRef> subagents = new ArrayList<>();

    public boolean isBuilderMode() {
        return "BUILDER".equalsIgnoreCase(executionMode);
    }

    @Setter
    @Getter
    public static class CompactionConfig {
        private int triggerMessages = 30;
        private int keepMessages = 10;
        private boolean flushBeforeCompact = true;
    }

    @Setter
    @Getter
    public static class SubAgentRef {
        private String name;
        private String description;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/skloda/agentscope/agent/HarnessConfig.java
git commit -m "feat: add executionMode and isolationScope to HarnessConfig"
```

---

### Task 4: HarnessAgentFactory 双模式分支

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java`

- [ ] **Step 1: 添加 import**

在文件顶部 import 区域添加：

```java
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
```

- [ ] **Step 2: 修改 builder 构建逻辑**

将第 46-51 行的 HarnessAgent builder 初始化：

```java
HarnessAgent.Builder builder = HarnessAgent.builder()
        .name(config.getName() != null ? config.getName() : config.getAgentId())
        .description(config.getDescription())
        .sysPrompt(config.getSystemPrompt())
        .model(model)
        .workspace(workspace);
```

替换为：

```java
HarnessAgent.Builder builder = HarnessAgent.builder()
        .name(config.getName() != null ? config.getName() : config.getAgentId())
        .description(config.getDescription())
        .sysPrompt(config.getSystemPrompt())
        .model(model)
        .workspace(workspace);

if (harnessConfig.isBuilderMode()) {
    builder.filesystem(new LocalFilesystemSpec());
    log.info("Agent '{}' using BUILDER mode with LocalFilesystemSpec", config.getAgentId());
}
```

完整的 `create` 方法从第 19 行开始应为：

```java
public static HarnessAgent create(AgentConfig config, String apiKey) throws Exception {
    HarnessConfig harnessConfig = config.getHarnessConfig();
    if (harnessConfig == null) {
        throw new IllegalArgumentException("Agent '" + config.getAgentId() + "' is type HARNESS but has no harnessConfig");
    }

    // 1. Resolve workspace path
    Path workspace = resolveWorkspace(harnessConfig.getWorkspace(), config.getAgentId());

    // 2. Initialize workspace from templates (idempotent)
    WorkspaceInitializer.initializeFromTemplates(config.getAgentId(), workspace);

    // Also initialize all subagent workspaces
    for (HarnessConfig.SubAgentRef sub : harnessConfig.getSubagents()) {
        Path subWorkspace = workspace.getParent().resolve(sub.getName());
        WorkspaceInitializer.initializeSubagentWorkspace(sub.getName(), subWorkspace);
    }

    // 3. Build model
    String modelName = config.getModelName() != null ? config.getModelName() : "qwen-max";
    Model model = DashScopeChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .stream(true)
            .build();

    // 4. Build HarnessAgent
    HarnessAgent.Builder builder = HarnessAgent.builder()
            .name(config.getName() != null ? config.getName() : config.getAgentId())
            .description(config.getDescription())
            .sysPrompt(config.getSystemPrompt())
            .model(model)
            .workspace(workspace);

    if (harnessConfig.isBuilderMode()) {
        builder.filesystem(new LocalFilesystemSpec());
        log.info("Agent '{}' using BUILDER mode with LocalFilesystemSpec", config.getAgentId());
    }

    // 5. Configure compaction
    if (harnessConfig.getCompaction() != null) {
        HarnessConfig.CompactionConfig cc = harnessConfig.getCompaction();
        builder.compaction(CompactionConfig.builder()
                .triggerMessages(cc.getTriggerMessages())
                .keepMessages(cc.getKeepMessages())
                .flushBeforeCompact(cc.isFlushBeforeCompact())
                .build());
    }

    HarnessAgent agent = builder.build();
    log.info("HarnessAgent '{}' built with workspace={}, mode={}", config.getAgentId(), workspace,
            harnessConfig.isBuilderMode() ? "BUILDER" : "CLAW");
    return agent;
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java
git commit -m "feat: HarnessAgentFactory supports Builder mode via LocalFilesystemSpec"
```

---

### Task 5: ChatRequest 新增 userId，传递到 HarnessAgentService

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/model/ChatRequest.java`
- Modify: `src/main/java/com/skloda/agentscope/service/AgentService.java`
- Modify: `src/main/java/com/skloda/agentscope/harness/HarnessAgentService.java`

- [ ] **Step 1: ChatRequest 添加 userId 字段**

在 `ChatRequest.java` 的 `private String sessionId;` 后添加：

```java
private String userId;
```

并添加 getter/setter（仿照 sessionId 的模式）：

```java
public String getUserId() {
    return userId;
}

public void setUserId(String userId) {
    this.userId = userId;
}
```

- [ ] **Step 2: AgentService 传递 userId 到 HarnessAgentService**

在 `AgentService.java` 的 `createStreamFlux` 方法中（第 92-94 行），将：

```java
if (cfg != null && cfg.getType() == AgentType.HARNESS) {
    return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId);
}
```

改为：

```java
if (cfg != null && cfg.getType() == AgentType.HARNESS) {
    return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId, userId);
}
```

同时修改 `createStreamFlux` 的重载方法签名（第 73-77 行），在多模态版本的参数中加上 `String userId`：

```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId) {
    return createStreamFlux(agentId, message, filePath, fileName, sessionId, null, null, null);
}
```

以及多模态版本（第 82-86 行）：

```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio) {
```

改为：

```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio) {
    return createStreamFluxWithUserId(agentId, message, filePath, fileName, sessionId, images, audio, null);
}

public Flux<Map<String, Object>> createStreamFluxWithUserId(String agentId, String message,
                                                              String filePath, String fileName,
                                                              String sessionId,
                                                              List<ChatRequest.ImageFile> images,
                                                              ChatRequest.AudioFile audio,
                                                              String userId) {
```

在方法内部（第 92-94 行）改为：

```java
if (cfg != null && cfg.getType() == AgentType.HARNESS) {
    return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId, userId);
}
```

并在 `ChatController.java` 中 `sendMessage` 方法调用时传递 userId。

实际上这里为了最小化改动，更简洁的方案是：不改 `createStreamFlux` 的签名，而是在 `ChatController.sendMessage` 中直接调用 `harnessAgentService`。

让我重新考虑——更简单的方案：

**简化方案**：只改 `HarnessAgentService.createStreamFlux` 签名加 userId，`AgentService` 从 `ChatRequest` 获取 userId 传入。

步骤 2 和 3 的具体改动如下：

- [ ] **Step 2: 修改 HarnessAgentService 接受 userId**

在 `HarnessAgentService.java` 中，将 `createStreamFlux` 方法签名从：

```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                     String filePath, String fileName,
                                                     String sessionId) {
```

改为：

```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                     String filePath, String fileName,
                                                     String sessionId,
                                                     String userId) {
```

并将 `RuntimeContext` 构建中的 `userId` 从硬编码改为使用传入的值：

```java
String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : "demo-user";
RuntimeContext ctx = RuntimeContext.builder()
        .sessionId(sessionId != null ? sessionId : "default")
        .userId(effectiveUserId)
        .build();
```

- [ ] **Step 3: AgentService 传递 userId**

在 `AgentService.java` 中，将多模态版本的 `createStreamFlux` 方法签名加入 userId 参数。在 HARNESS 路由处传递它：

当前（第 82-95 行）：
```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio) {
    Msg userMsg = buildUserMessage(message, filePath, fileName, images, audio);

    // Route HARNESS type to HarnessAgentService
    if (harnessAgentService != null) {
        AgentConfig cfg = runtimeFactory.getConfigService().findAgentConfig(agentId).orElse(null);
        if (cfg != null && cfg.getType() == AgentType.HARNESS) {
            return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId);
        }
    }
```

改为：
```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio) {
    return createStreamFlux(agentId, message, filePath, fileName, sessionId, images, audio, null);
}

public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio,
                                                       String userId) {
    Msg userMsg = buildUserMessage(message, filePath, fileName, images, audio);

    // Route HARNESS type to HarnessAgentService
    if (harnessAgentService != null) {
        AgentConfig cfg = runtimeFactory.getConfigService().findAgentConfig(agentId).orElse(null);
        if (cfg != null && cfg.getType() == AgentType.HARNESS) {
            return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId, userId);
        }
    }
```

- [ ] **Step 4: ChatController 传递 userId**

在 `ChatController.java` 的 `sendMessage` 方法中（第 135-140 行），将：

```java
return agentService.createStreamFlux(
                request.getAgentId(), message,
                request.getFilePath(), request.getFileName(),
                sessionId,
                request.getImages(),
                request.getAudio())
```

改为：

```java
return agentService.createStreamFlux(
                request.getAgentId(), message,
                request.getFilePath(), request.getFileName(),
                sessionId,
                request.getImages(),
                request.getAudio(),
                request.getUserId())
```

- [ ] **Step 5: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/skloda/agentscope/model/ChatRequest.java src/main/java/com/skloda/agentscope/service/AgentService.java src/main/java/com/skloda/agentscope/harness/HarnessAgentService.java src/main/java/com/skloda/agentscope/controller/ChatController.java
git commit -m "feat: pass userId through ChatRequest to HarnessAgentService for namespace isolation"
```

---

### Task 6: 前端 - 添加 intelligence 分类和 Builder 用户选择器

**Files:**
- Modify: `src/main/resources/static/scripts/modules/agents.js`
- Modify: `src/main/resources/static/scripts/chat.js`
- Modify: `src/main/resources/static/scripts/state.js`
- Modify: `src/main/resources/templates/chat.html`

- [ ] **Step 1: 在 agents.js 的 CATEGORIES 中添加 intelligence 分类**

在 `agents.js` 的 `CATEGORIES` 数组中（第 8-11 行），在 collaboration 条目后添加：

```javascript
{ key: 'intelligence', label: '情报追踪',   icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>', color: 'orange' },
```

完整的 `CATEGORIES` 数组：

```javascript
const CATEGORIES = [
    { key: 'single',        label: '单体Agent',      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>', color: 'cyan'    },
    { key: 'expert',        label: '专家Agent',      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 1v4m0 14v4M4.22 4.22l2.83 2.83m9.9 9.9l2.83 2.83M1 12h4m14 0h4M4.22 19.78l2.83-2.83m9.9-9.9l2.83-2.83"/></svg>', color: 'green'   },
    { key: 'collaboration', label: '多智能体协作',   icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="5" r="3"/><circle cx="5" cy="19" r="3"/><circle cx="19" cy="19" r="3"/><line x1="12" y1="8" x2="5" y2="16"/><line x1="12" y1="8" x2="19" y2="16"/><line x1="5" y1="19" x2="19" y2="19"/></svg>', color: 'magenta' },
    { key: 'intelligence',  label: '情报追踪',      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>', color: 'orange'  },
];
```

- [ ] **Step 2: 在 agent 卡片中显示 Builder 标记**

在 `agents.js` 的 agent 卡片渲染中（第 74 行附近），在 `agent-card-name` div 中追加 Builder 标记。将：

```javascript
'<div class="agent-card-name">' + escapeHtml(agent.name) + '</div>' +
```

改为：

```javascript
'<div class="agent-card-name">' + escapeHtml(agent.name) +
    (agent.harnessConfig && agent.harnessConfig.executionMode === 'BUILDER' ? ' <span class="agent-badge builder">Builder</span>' : '') +
'</div>' +
```

注意：需要确保 `toAgentConfigPreview` 传递 `harnessConfig`。在 `ChatController.toAgentConfigPreview` 中添加：

```java
target.setHarnessConfig(source.getHarnessConfig());
```

- [ ] **Step 3: 在 state.js 中添加 builderUserId 状态**

在 `state.js` 的 state 对象中添加 `builderUserId`：

```javascript
const state = window.__agentScopeState || (window.__agentScopeState = {
    // ... existing fields ...
    currentFileInfo: null,
    builderUserId: 'demo-user'
});
```

并添加 window 属性：

```javascript
defineWindowStateProperty('builderUserId', {
    get: function() { return state.builderUserId; },
    set: function(val) { state.builderUserId = val; }
});
```

以及 export：

```javascript
export { currentAgent, isStreaming, /* ... existing ... */ currentFileInfo, builderUserId };
```

实际上 state.js 用的是 `var` 导出模式，不需要修改 export 语句（已有所有变量）。只需要在 state 对象和 window 属性中添加 `builderUserId`。

- [ ] **Step 4: 在 chat.html 中添加用户选择器容器**

在 `chat.html` 中，在 `chat-input-area` 区域内、消息输入框上方添加用户选择器。找到消息输入区域的容器，在输入框之前插入：

```html
<div id="builderUserSelector" class="builder-user-selector" style="display:none;">
    <label>用户：</label>
    <select id="builderUserSelect" onchange="window.builderUserId = this.value;">
        <option value="demo-user">demo-user</option>
        <option value="alice">alice</option>
        <option value="bob">bob</option>
        <option value="charlie">charlie</option>
    </select>
</div>
```

具体位置：在 `chat.html` 中找到 `id="messageInput"` 的容器，在其上方、`id="fileTagArea"` 的下方插入。

- [ ] **Step 5: 在 agents.js 的 selectAgent 中控制用户选择器显示**

在 `agents.js` 的 `selectAgent` 函数中（第 97 行），在设置 chatHeaderName 之后（第 152 行附近），添加用户选择器的显示/隐藏逻辑：

```javascript
// Show/hide Builder user selector
var builderSelector = document.getElementById('builderUserSelector');
if (builderSelector) {
    var isBuilder = agent.config && agent.config.harnessConfig && agent.config.harnessConfig.executionMode === 'BUILDER';
    builderSelector.style.display = isBuilder ? 'flex' : 'none';
}
```

- [ ] **Step 6: 在 chat.js 的 sendMessage 中传递 userId**

在 `chat.js` 的 `sendMessage` 函数中（第 80-86 行），在构建 payload 时添加 userId：

```javascript
var payload = {
    agentId: currentAgent,
    message: message,
    filePath: fileInfo ? fileInfo.filePath : null,
    fileName: fileInfo ? fileInfo.fileName : null,
    sessionId: currentSessionId || null,
    userId: window.builderUserId || null
};
```

- [ ] **Step 7: 添加 CSS 样式**

在 `src/main/resources/static/styles/modules/chat.css`（或 `header.css`）末尾添加：

```css
.builder-user-selector {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 12px;
    background: rgba(255, 165, 0, 0.08);
    border: 1px solid rgba(255, 165, 0, 0.2);
    border-radius: 6px;
    margin-bottom: 6px;
}
.builder-user-selector label {
    color: var(--text-secondary);
    font-size: 13px;
}
.builder-user-selector select {
    background: var(--bg-secondary);
    color: var(--text-primary);
    border: 1px solid var(--border-color);
    border-radius: 4px;
    padding: 2px 8px;
    font-size: 13px;
}
.agent-badge.builder {
    display: inline-block;
    font-size: 10px;
    padding: 1px 5px;
    border-radius: 3px;
    background: rgba(255, 165, 0, 0.15);
    color: #ff9500;
    border: 1px solid rgba(255, 165, 0, 0.3);
    vertical-align: middle;
    margin-left: 4px;
}
```

- [ ] **Step 8: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: 提交**

```bash
git add src/main/resources/static/scripts/ src/main/resources/static/styles/ src/main/resources/templates/chat.html
git commit -m "feat: frontend Builder user selector and intelligence category"
```

---

### Task 7: ChatController 传递 harnessConfig 到前端

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/controller/ChatController.java`

- [ ] **Step 1: 在 toAgentConfigPreview 中传递 harnessConfig**

在 `ChatController.toAgentConfigPreview` 方法中（第 313-348 行），在现有字段拷贝的末尾（第 339 行 `target.setType(source.getType());` 之后）添加：

```java
target.setHarnessConfig(source.getHarnessConfig());
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/skloda/agentscope/controller/ChatController.java
git commit -m "feat: expose harnessConfig in agent API for frontend Builder detection"
```

---

### Task 8: 编写集成测试

**Files:**
- Modify: `src/test/java/com/skloda/agentscope/harness/HarnessRuntimeTest.java`

- [ ] **Step 1: 添加 HarnessConfig executionMode 测试**

在 `HarnessRuntimeTest.java` 中添加测试：

```java
@Test
void harnessConfigDefaultsToClawMode() {
    com.skloda.agentscope.agent.HarnessConfig config = new com.skloda.agentscope.agent.HarnessConfig();
    assertFalse(config.isBuilderMode());
    assertEquals("CLAW", config.getExecutionMode());
}

@Test
void harnessConfigBuilderMode() {
    com.skloda.agentscope.agent.HarnessConfig config = new com.skloda.agentscope.agent.HarnessConfig();
    config.setExecutionMode("BUILDER");
    assertTrue(config.isBuilderMode());
}
```

需要添加的 import：

```java
import static org.junit.jupiter.api.Assertions.*;
```

（已存在）

- [ ] **Step 2: 运行测试**

Run: `mvn test -pl . -Dtest=HarnessRuntimeTest -q`
Expected: All tests PASS

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/skloda/agentscope/harness/HarnessRuntimeTest.java
git commit -m "test: add HarnessConfig executionMode tests"
```

---

### Task 9: 端到端验证

**Files:**
- No code changes

- [ ] **Step 1: 全量编译**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 全量测试**

Run: `mvn test -q`
Expected: All tests PASS

- [ ] **Step 3: 启动应用验证 agent 加载**

Run: `DASHSCOPE_API_KEY=test mvn spring-boot:run &`

等待启动完成后：

Run: `curl -s http://localhost:8080/api/agents | python3 -m json.tool | grep -A3 '"agentId"' | head -40`

Expected: 能看到 `complaint-reviewer` 和 `finance-intel-tracker` 两个 HARNESS agent

- [ ] **Step 4: 验证 Builder agent 的 harnessConfig**

Run: `curl -s http://localhost:8080/api/agents/finance-intel-tracker | python3 -m json.tool | grep -A10 harnessConfig`

Expected: `executionMode: "BUILDER"`, `isolationScope: "USER"`

- [ ] **Step 5: 验证 Claw agent 的 harnessConfig**

Run: `curl -s http://localhost:8080/api/agents/complaint-reviewer | python3 -m json.tool | grep -A10 harnessConfig`

Expected: `executionMode: "CLAW"`

- [ ] **Step 6: 浏览器验证**

打开 http://localhost:8080：
1. 确认 agent 列表中出现「情报追踪」分类，包含「金融情报追踪助手 [Builder]」
2. 选择 `finance-intel-tracker`，确认出现用户选择器（demo-user/alice/bob/charlie）
3. 选择 `complaint-reviewer`，确认用户选择器隐藏
4. 停止应用

- [ ] **Step 7: 最终提交**

如果一切正常，无需额外提交。如有修复则提交。
