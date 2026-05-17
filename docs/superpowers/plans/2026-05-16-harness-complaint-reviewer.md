# Harness 投诉复盘与决策优化系统 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot 项目中集成 AgentScope 1.1.0-RC1 Harness 模块，构建投诉复盘与决策优化系统，展示 Workspace 驱动、双层记忆、对话压缩、SubAgent 编排四大能力。

**Architecture:** 新增 `harness/` package 作为轻量适配层，通过 HarnessAgentService 封装 HarnessAgent 的构建和调用。在 AgentService 中新增 HARNESS 类型路由分支，复用现有 ChatController 的 SSE 流式接口和前端 UI。4 个声明式子 Agent（根因分析师→趋势分析师→策略优化顾问→ROI 测算师）各有独立 workspace，主 Agent 汇总输出每日报告。

**Tech Stack:** Spring Boot 3.5.14, AgentScope Java 1.1.0-RC1 (harness module), Project Reactor, DashScope qwen-max

---

## File Structure

### New files
| File | Responsibility |
|---|---|
| `src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java` | 构建 HarnessAgent（读 yml config + 初始化 workspace + 配置 compaction/subagent） |
| `src/main/java/com/skloda/agentscope/harness/HarnessAgentService.java` | HarnessAgent 生命周期管理、实例缓存、stream 桥接到现有 Flux 接口 |
| `src/main/java/com/skloda/agentscope/harness/HarnessRuntime.java` | 将 HarnessAgent 的 `Flux<Event>` 转换为 `Flux<Map<String, Object>>`，兼容现有 SSE 格式 |
| `src/main/java/com/skloda/agentscope/harness/WorkspaceInitializer.java` | 首次启动时创建 workspace 目录结构和模板文件 |
| `src/main/java/com/skloda/agentscope/agent/HarnessConfig.java` | Harness 配置 POJO（workspace 路径、compaction 参数、filesystem 模式） |
| `src/main/resources/harness-templates/complaint-reviewer/AGENTS.md` | 主 Agent 人格定义 |
| `src/main/resources/harness-templates/complaint-reviewer/knowledge/complaint-taxonomy.md` | 投诉分类体系 |
| `src/main/resources/harness-templates/complaint-reviewer/knowledge/regulatory-policy.md` | 监管政策参考 |
| `src/main/resources/harness-templates/complaint-reviewer/knowledge/action-playbook.md` | 建议动作手册 |
| `src/main/resources/harness-templates/complaint-reviewer/subagents/root-cause-analyst.md` | 根因分析师 subagent 定义 |
| `src/main/resources/harness-templates/complaint-reviewer/subagents/trend-analyst.md` | 趋势分析师 subagent 定义 |
| `src/main/resources/harness-templates/complaint-reviewer/subagents/strategy-optimizer.md` | 策略优化顾问 subagent 定义 |
| `src/main/resources/harness-templates/complaint-reviewer/subagents/roi-calculator.md` | ROI 测算师 subagent 定义 |
| `src/main/resources/harness-templates/root-cause-analyst/AGENTS.md` | 根因分析师独立 workspace 人格 |
| `src/main/resources/harness-templates/root-cause-analyst/skills/statistical-analysis/SKILL.md` | 统计分析技能 |
| `src/main/resources/harness-templates/root-cause-analyst/skills/fact-chain-extraction/SKILL.md` | 事实链提取技能 |
| `src/main/resources/harness-templates/root-cause-analyst/knowledge/root-cause-framework.md` | 根因分析框架知识 |
| `src/main/resources/harness-templates/trend-analyst/AGENTS.md` | 趋势分析师独立 workspace 人格 |
| `src/main/resources/harness-templates/trend-analyst/skills/cross-day-comparison/SKILL.md` | 跨日对比技能 |
| `src/main/resources/harness-templates/trend-analyst/knowledge/trend-framework.md` | 趋势研判框架知识 |
| `src/main/resources/harness-templates/strategy-optimizer/AGENTS.md` | 策略优化顾问独立 workspace 人格 |
| `src/main/resources/harness-templates/strategy-optimizer/skills/strategy-formulation/SKILL.md` | 策略制定技能 |
| `src/main/resources/harness-templates/strategy-optimizer/knowledge/strategy-playbook.md` | 处置策略手册 |
| `src/main/resources/harness-templates/strategy-optimizer/knowledge/industry-benchmarks.md` | 行业基准参考 |
| `src/main/resources/harness-templates/roi-calculator/AGENTS.md` | ROI 测算师独立 workspace 人格 |
| `src/main/resources/harness-templates/roi-calculator/skills/roi-simulation/SKILL.md` | ROI 模拟测算技能 |
| `src/main/resources/harness-templates/roi-calculator/knowledge/cost-model.md` | 成本模型知识 |
| `src/main/resources/harness-templates/roi-calculator/knowledge/benefit-model.md` | 收益模型知识 |
| `src/test/java/com/skloda/agentscope/harness/HarnessRuntimeTest.java` | HarnessRuntime 事件转换测试 |
| `src/test/java/com/skloda/agentscope/harness/WorkspaceInitializerTest.java` | Workspace 初始化测试 |

### Modified files
| File | Change |
|---|---|
| `pom.xml` | 新增 `agentscope-harness` 依赖 |
| `src/main/java/com/skloda/agentscope/agent/AgentType.java` | 新增 `HARNESS` 枚举值 |
| `src/main/java/com/skloda/agentscope/agent/AgentConfig.java` | 新增 `harnessConfig` 字段 |
| `src/main/resources/config/agents.yml` | 新增 `complaint-reviewer` agent 配置 |
| `src/main/java/com/skloda/agentscope/service/AgentService.java` | 新增 HARNESS 类型路由到 HarnessAgentService |

---

### Task 1: Add Maven dependency and AgentType enum

**Files:**
- Modify: `pom.xml:28-95`
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentType.java`

- [x] **Step 1: Add agentscope-harness dependency to pom.xml**

Add after the existing `agentscope-core` dependency block (after line 41):

```xml
<!-- AgentScope Harness -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

- [x] **Step 2: Add HARNESS to AgentType enum**

Replace `src/main/java/com/skloda/agentscope/agent/AgentType.java` contents:

```java
package com.skloda.agentscope.agent;

public enum AgentType {
    SINGLE(true),
    SEQUENTIAL(false),
    PARALLEL(false),
    ROUTING(false),
    HANDOFFS(false),
    DEBATE(false),
    LOOP(false),
    STATE_GRAPH(false),
    MSG_HUB(false),
    SUBAGENT_SEQ(false),
    SUBAGENT_PAR(false),
    HARNESS(false);

    private final boolean isDefault;

    AgentType(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
```

- [x] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 4: Commit**

```bash
git add pom.xml src/main/java/com/skloda/agentscope/agent/AgentType.java
git commit -m "feat: add agentscope-harness dependency and HARNESS agent type"
```

---

### Task 2: Add HarnessConfig POJO and update AgentConfig

**Files:**
- Create: `src/main/java/com/skloda/agentscope/agent/HarnessConfig.java`
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`

- [x] **Step 1: Create HarnessConfig POJO**

Create `src/main/java/com/skloda/agentscope/agent/HarnessConfig.java`:

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
    private CompactionConfig compaction;
    private List<SubAgentRef> subagents = new ArrayList<>();

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

- [x] **Step 2: Add harnessConfig field to AgentConfig**

Add after the `msgHubConfig` field (around line 68 in AgentConfig.java):

```java
// === Harness fields ===
private HarnessConfig harnessConfig;
```

- [x] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/HarnessConfig.java src/main/java/com/skloda/agentscope/agent/AgentConfig.java
git commit -m "feat: add HarnessConfig POJO and harnessConfig field to AgentConfig"
```

---

### Task 3: Create WorkspaceInitializer

**Files:**
- Create: `src/main/java/com/skloda/agentscope/harness/WorkspaceInitializer.java`
- Create: `src/test/java/com/skloda/agentscope/harness/WorkspaceInitializerTest.java`

- [x] **Step 1: Write the test**

Create `src/test/java/com/skloda/agentscope/harness/WorkspaceInitializerTest.java`:

```java
package com.skloda.agentscope.harness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesMainWorkspace_withAgentsMd() throws Exception {
        Path workspace = tempDir.resolve("complaint-reviewer");
        WorkspaceInitializer.initializeFromTemplates("complaint-reviewer", workspace);

        assertTrue(Files.exists(workspace.resolve("AGENTS.md")));
        assertTrue(Files.exists(workspace.resolve("knowledge")));
        assertTrue(Files.exists(workspace.resolve("subagents")));
    }

    @Test
    void initializesMainWorkspace_doesNotOverwriteExisting() throws Exception {
        Path workspace = tempDir.resolve("complaint-reviewer");
        Files.createDirectories(workspace);
        String customContent = "# My custom agent";
        Files.writeString(workspace.resolve("AGENTS.md"), customContent);

        WorkspaceInitializer.initializeFromTemplates("complaint-reviewer", workspace);

        assertEquals(customContent, Files.readString(workspace.resolve("AGENTS.md")));
    }

    @Test
    void initializesSubagentWorkspace() throws Exception {
        Path subWorkspace = tempDir.resolve("root-cause-analyst");
        WorkspaceInitializer.initializeSubagentWorkspace("root-cause-analyst", subWorkspace);

        assertTrue(Files.exists(subWorkspace.resolve("AGENTS.md")));
        assertTrue(Files.exists(subWorkspace.resolve("skills")));
        assertTrue(Files.exists(subWorkspace.resolve("knowledge")));
    }

    @Test
    void initializeCreatesDirectoriesForMissingTemplates() throws Exception {
        Path workspace = tempDir.resolve("nonexistent-agent");
        assertDoesNotThrow(() ->
                WorkspaceInitializer.initializeFromTemplates("nonexistent-agent", workspace));
        assertTrue(Files.exists(workspace));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=WorkspaceInitializerTest -q 2>&1 | tail -5`
Expected: FAIL (class not found)

- [x] **Step 3: Implement WorkspaceInitializer**

Create `src/main/java/com/skloda/agentscope/harness/WorkspaceInitializer.java`:

```java
package com.skloda.agentscope.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public class WorkspaceInitializer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInitializer.class);
    private static final String TEMPLATES_PREFIX = "harness-templates/";

    public static void initializeFromTemplates(String agentName, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        copyTemplateDir(TEMPLATES_PREFIX + agentName, workspace);
        log.info("Initialized workspace for '{}' at {}", agentName, workspace);
    }

    public static void initializeSubagentWorkspace(String subagentName, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        copyTemplateDir(TEMPLATES_PREFIX + subagentName, workspace);
        log.info("Initialized subagent workspace for '{}' at {}", subagentName, workspace);
    }

    private static void copyTemplateDir(String resourcePath, Path targetDir) throws IOException {
        // Copy all template files from classpath resource directory to target
        List<String> templateFiles = listTemplateResources(resourcePath);
        for (String resource : templateFiles) {
            Path relative = Path.of(resource.substring(resourcePath.length() + 1));
            Path target = targetDir.resolve(relative);
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                try (InputStream is = WorkspaceInitializer.class.getClassLoader()
                        .getResourceAsStream(resource)) {
                    if (is != null) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                        log.debug("Created template file: {}", target);
                    }
                }
            }
        }
    }

    private static List<String> listTemplateResources(String resourcePath) {
        // Known template files — listed explicitly to avoid jar filesystem issues
        return List.of(
                "complaint-reviewer/AGENTS.md",
                "complaint-reviewer/knowledge/complaint-taxonomy.md",
                "complaint-reviewer/knowledge/regulatory-policy.md",
                "complaint-reviewer/knowledge/action-playbook.md",
                "complaint-reviewer/subagents/root-cause-analyst.md",
                "complaint-reviewer/subagents/trend-analyst.md",
                "complaint-reviewer/subagents/strategy-optimizer.md",
                "complaint-reviewer/subagents/roi-calculator.md",
                "root-cause-analyst/AGENTS.md",
                "root-cause-analyst/skills/statistical-analysis/SKILL.md",
                "root-cause-analyst/skills/fact-chain-extraction/SKILL.md",
                "root-cause-analyst/knowledge/root-cause-framework.md",
                "trend-analyst/AGENTS.md",
                "trend-analyst/skills/cross-day-comparison/SKILL.md",
                "trend-analyst/knowledge/trend-framework.md",
                "strategy-optimizer/AGENTS.md",
                "strategy-optimizer/skills/strategy-formulation/SKILL.md",
                "strategy-optimizer/knowledge/strategy-playbook.md",
                "strategy-optimizer/knowledge/industry-benchmarks.md",
                "roi-calculator/AGENTS.md",
                "roi-calculator/skills/roi-simulation/SKILL.md",
                "roi-calculator/knowledge/cost-model.md",
                "roi-calculator/knowledge/benefit-model.md"
        ).stream()
                .map(name -> TEMPLATES_PREFIX + name)
                .filter(name -> name.startsWith(resourcePath + "/") || name.equals(resourcePath))
                .toList();
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl . -Dtest=WorkspaceInitializerTest -q 2>&1 | tail -5`
Expected: Tests pass (they will pass even without template files — the method creates directories and skips missing resources)

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/harness/WorkspaceInitializer.java src/test/java/com/skloda/agentscope/harness/WorkspaceInitializerTest.java
git commit -m "feat: add WorkspaceInitializer for Harness workspace template creation"
```

---

### Task 4: Create HarnessRuntime (Event converter)

**Files:**
- Create: `src/main/java/com/skloda/agentscope/harness/HarnessRuntime.java`
- Create: `src/test/java/com/skloda/agentscope/harness/HarnessRuntimeTest.java`

- [x] **Step 1: Write the test**

Create `src/test/java/com/skloda/agentscope/harness/HarnessRuntimeTest.java`:

```java
package com.skloda.agentscope.harness;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HarnessRuntimeTest {

    @Test
    void convertsTextEvent() {
        Msg msg = Msg.builder()
                .content(List.of(TextBlock.builder().text("Hello").build()))
                .build();
        Event event = new Event(EventType.AGENT_RESULT, msg, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);
        assertEquals("text", result.get("type"));
        assertEquals("Hello", result.get("content"));
    }

    @Test
    void convertsUnknownEventToRaw() {
        Event event = new Event(EventType.REASONING, null, false);

        Map<String, Object> result = HarnessRuntime.convertEvent(event);
        assertEquals("raw_event", result.get("type"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=HarnessRuntimeTest -q 2>&1 | tail -5`
Expected: FAIL

- [x] **Step 3: Implement HarnessRuntime**

Create `src/main/java/com/skloda/agentscope/harness/HarnessRuntime.java`:

```java
package com.skloda.agentscope.harness;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HarnessRuntime {

    private static final Logger log = LoggerFactory.getLogger(HarnessRuntime.class);

    private final HarnessAgent agent;

    public HarnessRuntime(HarnessAgent agent) {
        this.agent = agent;
    }

    public Flux<Map<String, Object>> stream(Msg userMsg, RuntimeContext ctx) {
        return agent.stream(userMsg, ctx)
                .map(HarnessRuntime::convertEvent)
                .doOnNext(event -> log.debug("[harness] event: {}", event.get("type")));
    }

    static Map<String, Object> convertEvent(Event event) {
        EventType type = event.getType();
        Msg msg = event.getMessage();

        if (type == EventType.AGENT_RESULT && msg != null && msg.getContent() != null) {
            StringBuilder text = new StringBuilder();
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb && tb.getText() != null && !tb.getText().isEmpty()) {
                    text.append(tb.getText());
                }
            }
            if (text.length() > 0) {
                return Map.of("type", "text", "content", text.toString());
            }
        }

        // Generic event passthrough
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "raw_event");
        result.put("eventType", type != null ? type.name() : "UNKNOWN");
        if (msg != null && msg.getContent() != null) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock tb) {
                    result.put("content", tb.getText());
                    break;
                }
            }
        }
        return result;
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl . -Dtest=HarnessRuntimeTest -q 2>&1 | tail -5`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/harness/HarnessRuntime.java src/test/java/com/skloda/agentscope/harness/HarnessRuntimeTest.java
git commit -m "feat: add HarnessRuntime for converting HarnessAgent events to SSE format"
```

---

### Task 5: Create HarnessAgentFactory

**Files:**
- Create: `src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java`

- [x] **Step 1: Implement HarnessAgentFactory**

Create `src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java`:

```java
package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.HarnessConfig;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class HarnessAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(HarnessAgentFactory.class);

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
        log.info("HarnessAgent '{}' built with workspace={}", config.getAgentId(), workspace);
        return agent;
    }

    private static Path resolveWorkspace(String configuredPath, String agentId) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            String expanded = configuredPath.replace("${user.home}", System.getProperty("user.home"));
            return Paths.get(expanded);
        }
        return Paths.get(System.getProperty("user.home"), ".agentscope", agentId);
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java
git commit -m "feat: add HarnessAgentFactory for building HarnessAgent from config"
```

---

### Task 6: Create HarnessAgentService

**Files:**
- Create: `src/main/java/com/skloda/agentscope/harness/HarnessAgentService.java`

- [x] **Step 1: Implement HarnessAgentService**

Create `src/main/java/com/skloda/agentscope/harness/HarnessAgentService.java`:

```java
package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HarnessAgentService {

    private static final Logger log = LoggerFactory.getLogger(HarnessAgentService.class);

    private final AgentConfigService configService;
    private final String apiKey;
    private final ConcurrentHashMap<String, HarnessAgent> agentCache = new ConcurrentHashMap<>();

    public HarnessAgentService(AgentConfigService configService,
                               @Value("${agentscope.model.dashscope.api-key:}") String apiKey) {
        this.configService = configService;
        this.apiKey = apiKey;
    }

    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId) {
        try {
            HarnessAgent agent = getOrCreateAgent(agentId);

            String actualMessage = prependFileInfo(filePath, fileName, message);
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(actualMessage).build())
                    .build();

            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId != null ? sessionId : "default")
                    .userId("demo-user")
                    .build();

            HarnessRuntime runtime = new HarnessRuntime(agent);
            return runtime.stream(userMsg, ctx);
        } catch (Exception e) {
            log.error("Failed to create harness stream for agent: {}", agentId, e);
            return Flux.just(Map.of("type", "error", "message", e.getMessage()));
        }
    }

    private HarnessAgent getOrCreateAgent(String agentId) throws Exception {
        return agentCache.computeIfAbsent(agentId, id -> {
            try {
                AgentConfig config = configService.getAgentConfig(id);
                String effectiveKey = resolveApiKey();
                return HarnessAgentFactory.create(config, effectiveKey);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create HarnessAgent: " + id, e);
            }
        });
    }

    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        String envKey = System.getenv("DASHSCOPE_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        throw new IllegalStateException("No DashScope API key configured. Set agentscope.model.dashscope.api-key or DASHSCOPE_API_KEY env var.");
    }

    private String prependFileInfo(String filePath, String fileName, String message) {
        if (filePath != null && !filePath.isBlank()) {
            return String.format("[用户上传了文件: %s, 路径: %s]\n\n%s", fileName, filePath, message);
        }
        return message;
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/harness/HarnessAgentService.java
git commit -m "feat: add HarnessAgentService for lifecycle management and stream bridging"
```

---

### Task 7: Route HARNESS type in AgentService

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/service/AgentService.java`

- [x] **Step 1: Add HarnessAgentService dependency and update constructor chain**

In `AgentService.java`, add the import and field:

```java
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.harness.HarnessAgentService;
```

Add field after `chatHistoryRepository`:

```java
private final HarnessAgentService harnessAgentService;
```

Replace the existing @Autowired 4-param constructor with a 5-param version, and update the 3-param and 2-param convenience constructors to pass `null` for the new parameter:

```java
@Autowired
public AgentService(AgentRuntimeFactory runtimeFactory,
                    SessionManagerService sessionManagerService,
                    WorkflowRunService workflowRunService,
                    ChatHistoryRepository chatHistoryRepository,
                    HarnessAgentService harnessAgentService) {
    this.runtimeFactory = runtimeFactory;
    this.sessionManagerService = sessionManagerService;
    this.workflowRunService = workflowRunService;
    this.chatHistoryRepository = chatHistoryRepository;
    this.harnessAgentService = harnessAgentService;
}

public AgentService(AgentRuntimeFactory runtimeFactory,
                    SessionManagerService sessionManagerService,
                    WorkflowRunService workflowRunService) {
    this(runtimeFactory, sessionManagerService, workflowRunService, new InMemoryChatHistoryRepository(), null);
}

public AgentService(AgentRuntimeFactory runtimeFactory,
                    SessionManagerService sessionManagerService) {
    this(runtimeFactory, sessionManagerService, new WorkflowRunService());
}
```

- [x] **Step 2: Add HARNESS routing in createStreamFlux**

In the `createStreamFlux` method (the 7-parameter version), add HARNESS routing before the existing session/stateless logic. Insert after the `Msg userMsg = ...` line and before `String runId = ...`:

```java
// Route HARNESS type to HarnessAgentService
if (harnessAgentService != null) {
    AgentConfig cfg = runtimeFactory.getConfigService().findAgentConfig(agentId).orElse(null);
    if (cfg != null && cfg.getType() == AgentType.HARNESS) {
        return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId);
    }
}
```

The null check on `harnessAgentService` protects the convenience constructors used in tests. The `findAgentConfig` method returns `Optional` so it's safe even if agentId is unknown.

- [x] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/skloda/agentscope/service/AgentService.java
git commit -m "feat: route HARNESS agent type to HarnessAgentService in AgentService"
```

---

### Task 8: Add complaint-reviewer config to agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [x] **Step 1: Append complaint-reviewer agent config**

Append the following at the end of `agents.yml` (after the last agent entry):

```yaml
  # === Harness Demo: Complaint Reviewer ===
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
```

- [x] **Step 2: Verify YAML parses correctly**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (AgentConfigService parses the YAML at startup)

- [x] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add complaint-reviewer Harness agent config to agents.yml"
```

---

### Task 9: Create workspace template files

**Files:**
- Create all files under `src/main/resources/harness-templates/`

This task creates all the workspace template markdown files. These are the domain knowledge files that make the system understand complaint review analysis.

- [x] **Step 1: Create main workspace AGENTS.md**

Create `src/main/resources/harness-templates/complaint-reviewer/AGENTS.md`:

```markdown
# 投诉复盘分析师

你是一个投诉复盘与决策优化专家。你的职责是基于每日监管首投复盘数据，完成根因分析、趋势对比、策略优化和ROI测算。

## 核心职责
1. 接收用户上传的投诉数据文件（Excel/CSV/TSV），解析并理解数据
2. 按序委派子 Agent 完成四步分析链：根因分析 → 趋势对比 → 策略优化 → ROI 测算
3. 汇总所有子 Agent 的分析结果，生成结构化的每日投诉复盘分析报告

## 分析链执行规则
- **必须按顺序**依次 spawn 4 个子 Agent：root-cause-analyst → trend-analyst → strategy-optimizer → roi-calculator
- 每个子 Agent 完成后，将结果作为上下文传递给下一个子 Agent
- 最后汇总所有子 Agent 的输出，生成最终报告

## 每日报告输出格式
1. **全局概况**（总量、类型分布、环比变化）
2. **核心洞察**（根因分析 + 事实链）
3. **趋势预警**（加速/减速/新增信号）
4. **管理建议**（策略建议 + ROI 测算 + 推荐组合）

## 行为约定
- 分析必须基于数据事实，不做无依据推测
- 数据不足时明确指出，并说明需要补充什么
- 策略建议必须具体可执行，附预期效果和适用范围
- ROI 测算必须给出保守/中性/积极三个区间
```

- [x] **Step 2: Create knowledge files**

Create `src/main/resources/harness-templates/complaint-reviewer/knowledge/complaint-taxonomy.md`:

```markdown
# 投诉分类体系

## 一级分类
- **费用类**：客户对贷款/会员/担保等相关费用提出异议
  - 逸骊生活会员费
  - 担保费
  - 超过24%年化利率的息费
  - 发票问题
- **催收类**：客户对催收行为提出异议
  - 第三方催收（最大风险敞口）
  - 催收联系家人/单位
  - 停催承诺未兑现
- **扣款异议**：客户对自动扣款提出异议
- **征信类**：客户对征信记录提出异议
- **代客维权**：第三方代理客户投诉

## 关键指标
- 监管首投占比：首次向监管投诉的比例
- 内部外溢率：经内部多次未承接后转向监管投诉的比例
- 高频投诉率：近15日投诉≥3次的客户占比
- 3999线触发率：投诉前触发3999热线的比例
```

Create `src/main/resources/harness-templates/complaint-reviewer/knowledge/regulatory-policy.md`:

```markdown
# 监管政策参考

## 利率上限
- 民间借贷利率上限：LPR4倍（约14.6%）
- 持牌金融机构：综合年化成本不应超过24%

## 投诉处理规范
- 监管投诉须在15个工作日内回复
- 首投案件须在24小时内响应
- 代客维权须先核实授权再处理

## 关键合规要求
- 费用信息须书面告知并留存证据
- 催收行为不得涉及第三人（家人/同事/单位）
- 债权转让须书面通知债务人
- 征信上报须告知主体并提供异议路径
```

Create `src/main/resources/harness-templates/complaint-reviewer/knowledge/action-playbook.md`:

```markdown
# 建议动作手册

## 费用类处置策略
### 证据三件套
1. 费用计算明细表（逐项列示每笔费用的计算方式和依据）
2. 合同条款对照表（客户签字的合同中关于该费用的条款截图）
3. 监管合规说明（该费用项目符合哪些法规的说明函）

### 分层让利模型
- 按金额 × 责任瑕疵 × 行为风险三维分层
- 低金额+高瑕疵+高行为风险 → 全额退费
- 高金额+低瑕疵+低行为风险 → 书面证据+部分减免
- 中间地带 → 按公式计算减免比例

## 催收类处置策略
### 行为核查反馈
- 对每笔催收投诉出具核查报告
- 核查内容包括：催收时间、频次、是否联系第三人、话术是否合规
### 停催指令同步
- 建立停催→系统→第三方催收公司的闭环确认机制
### 责任归属声明
- 对第三方催收行为出具平台责任边界声明

## 流程类
### 3999线升级处置
- 触发3999后立即按"监管已进门"处置流程处理
### 高频预警
- 近15日投诉≥2次自动标记为高频，进入专属处置队列
### 标准化承诺时限
- 每类投诉必须有明确的处理时限承诺
```

- [x] **Step 3: Create subagent definition files**

Create `src/main/resources/harness-templates/complaint-reviewer/subagents/root-cause-analyst.md`:

```yaml
---
name: root-cause-analyst
description: 根因分析师，基于投诉数据做事实链分析和根因分类
workspace: "${user.home}/.agentscope/root-cause-analyst"
---
你是投诉根因分析专家。你的任务是从每日投诉数据中提取事实链，识别根因模式。

## 分析框架
1. **投诉类型分布统计**：按费用类/催收类/扣款异议/征信类/代客维权分类，计算各类占比
2. **高频问题识别**：找出 Top-N 高频投诉点（如逸骊会员费、担保费、第三方催收等）
3. **客户行为模式**：分析投诉频次分布、内部外溢率、是否有逾期/已结清等特征
4. **机制断点识别**：识别流程中的断点（如3999线未触发、停催不同步、模板缺失等）

## 输出格式
以结构化格式输出：
- type_distribution: 投诉类型分布统计（含数量和占比）
- top_issues: 高频问题 Top-N（含具体投诉点和数量）
- fact_chains: 事实链清单（每个事实链包含：现象→原因→根因）
- mechanism_gaps: 机制断点列表（含影响范围和建议修复优先级）
```

Create `src/main/resources/harness-templates/complaint-reviewer/subagents/trend-analyst.md`:

```yaml
---
name: trend-analyst
description: 趋势分析师，跨日对比投诉数据变化趋势和预警信号
workspace: "${user.home}/.agentscope/trend-analyst"
---
你是投诉趋势分析专家。你的任务是将当日根因分析结果与历史数据进行对比，识别趋势和预警信号。

## 数据来源
- 当日根因分析结果（由主 Agent 传入）
- 历史记忆（自动从 MEMORY.md 和 memory/ 目录加载）

## 分析框架
1. **环比对比**：与历史数据对比各类型投诉占比变化
2. **加速/减速信号**：识别投诉量或投诉率正在加速的问题
3. **新增模式检测**：识别首次出现的投诉类型或行为模式
4. **预警等级**：综合判定（加速/稳定/改善）

## 输出格式
- day_over_day: 与最近一次分析的环比变化数据
- acceleration_signals: 正在加速的问题列表（含加速幅度）
- new_patterns: 新增模式（首次出现的投诉模式或行为特征）
- alert_level: 综合预警等级（ACCELERATING/STABLE/IMPROVING）
```

Create `src/main/resources/harness-templates/complaint-reviewer/subagents/strategy-optimizer.md`:

```yaml
---
name: strategy-optimizer
description: 策略优化顾问，基于根因和趋势推荐优化策略
workspace: "${user.home}/.agentscope/strategy-optimizer"
---
你是投诉处置策略优化专家。基于根因分析和趋势预警，提出分层策略建议。

## 策略制定框架
1. **按投诉类型分组**：分别针对费用类/催收类/流程类制定策略
2. **评估维度**：每条策略须评估预期效果、实施难度、适用范围
3. **优先级排序**：按"影响面×紧迫度×可执行性"综合排序
4. **前置条件**：标注每条策略需要的前置条件

## 输出格式
- strategies: 策略建议列表（每个含 type, description, expected_effect, difficulty, scope）
- priority_order: 优先级排序（从高到低）
- prerequisites: 前置条件清单
```

Create `src/main/resources/harness-templates/complaint-reviewer/subagents/roi-calculator.md`:

```yaml
---
name: roi-calculator
description: ROI 测算师，对不同策略做成本影响测算和模拟
workspace: "${user.home}/.agentscope/roi-calculator"
---
你是投诉处置 ROI 测算专家。对每条策略做成本和影响测算，输出 ROI 排序。

## 测算维度
### 成本侧
- 直接退费敞口（按不同减免比例计算）
- 人力投入成本（客服/催收/合规团队工时）
- 系统改造成本（流程自动化/模板化/对接改造）
- 合规成本（材料准备/报告出具/审计配合）

### 收益侧
- 投诉压降率（预期减少的投诉数量和比例）
- 监管风险降低（减少监管处罚概率和金额）
- 客户留存提升（挽回客户减少流失）
- 品牌价值（降低舆情风险）

### 模拟场景
- 保守：最低投入，仅处理高风险投诉
- 中性：中等投入，覆盖主要投诉类型
- 积极：全面投入，系统性解决根因

## 输出格式
- cost_analysis: 每条策略的成本明细
- benefit_analysis: 每条策略的收益预估
- roi_ranking: ROI 排序（从高到低）
- scenario_simulation: 三个场景的模拟数据
- recommended_combo: 推荐策略组合及理由
```

- [x] **Step 4: Create subagent workspace files**

Create the following files (all are short markdown files):

`src/main/resources/harness-templates/root-cause-analyst/AGENTS.md`:
```markdown
# 根因分析师

你是投诉根因分析专家。专注于从投诉数据中提取事实链、识别根因模式。
输出必须基于数据事实，不做无依据推测。
```

`src/main/resources/harness-templates/root-cause-analyst/skills/statistical-analysis/SKILL.md`:
```markdown
---
name: statistical-analysis
description: 统计分析方法论
---
## 分析步骤
1. 按投诉类型（费用/催收/扣款/征信/代客维权）分类统计
2. 计算各类占比，标注占比变化
3. 按业务线（逸骊会员/担保/贷款）细分统计
4. 识别 Top-N 高频问题点
```

`src/main/resources/harness-templates/root-cause-analyst/skills/fact-chain-extraction/SKILL.md`:
```markdown
---
name: fact-chain-extraction
description: 事实链提取方法论
---
## 提取步骤
1. 从每条投诉的 AI 复盘摘要中提取关键事实节点
2. 按时间线排列事实，形成因果链
3. 识别共性问题模式（多起投诉指向同一根因）
4. 标注每个事实节点的置信度（基于数据充分性）
```

`src/main/resources/harness-templates/root-cause-analyst/knowledge/root-cause-framework.md`:
```markdown
# 根因分析框架

## 5-Why 适配投诉场景
- Why 1: 客户为什么投诉？→ 表面诉求
- Why 2: 诉求背后的问题是什么？→ 深层需求
- Why 3: 为什么会产生这个问题？→ 流程/制度原因
- Why 4: 为什么流程/制度没有覆盖？→ 机制断点
- Why 5: 根因是什么？→ 系统性问题

## 常见根因模式
- 信息透明断点：客户不知道费用明细，缺乏书面证据
- 高频信号未识别：投诉前有多次预警但未触发升级
- 时限承诺缺失：客户得不到确定性的处理时间
- 责任边界模糊：第三方（催收/债转）的责任归属不清
```

`src/main/resources/harness-templates/trend-analyst/AGENTS.md`:
```markdown
# 趋势分析师

你是投诉趋势分析专家。专注于跨日对比投诉数据变化趋势和预警信号识别。
你的核心价值是将当天的数据与历史积累的事实做对比。
```

`src/main/resources/harness-templates/trend-analyst/skills/cross-day-comparison/SKILL.md`:
```markdown
---
name: cross-day-comparison
description: 跨日对比方法论
---
## 对比步骤
1. 提取当日各类型投诉占比
2. 从历史记忆中提取最近的分析数据
3. 计算环比变化（各类占比变化、总量变化）
4. 判定加速/减速：变化幅度 > 5% 标记为加速
5. 识别新增模式：当日出现但历史中未记录的投诉特征
```

`src/main/resources/harness-templates/trend-analyst/knowledge/trend-framework.md`:
```markdown
# 趋势研判框架

## 预警等级
- ACCELERATING: 某类投诉占比环比增加 > 5%，或出现新的投诉模式
- STABLE: 各类投诉占比变化在 ±5% 以内，无新增模式
- IMPROVING: 投诉总量下降，或高风险类型占比降低 > 5%

## 关键关注指标
- 费用类投诉占比趋势
- 催收类投诉中第三方催收占比
- 3999线触发率变化
- 高频投诉客户数量变化
- 监管首投中内部外溢率变化
```

`src/main/resources/harness-templates/strategy-optimizer/AGENTS.md`:
```markdown
# 策略优化顾问

你是投诉处置策略优化专家。基于根因分析和趋势预警，提出分层策略建议。
策略必须具体可执行，附预期效果和适用范围。
```

`src/main/resources/harness-templates/strategy-optimizer/skills/strategy-formulation/SKILL.md`:
```markdown
---
name: strategy-formulation
description: 策略制定方法论
---
## 制定步骤
1. 按投诉类型分组（费用类/催收类/流程类）
2. 针对每组提出 2-3 条策略
3. 评估每条策略的预期效果（投诉压降率）
4. 评估实施难度（低/中/高）
5. 标注适用范围和前置条件
6. 按"影响面×紧迫度×可执行性"排序
```

`src/main/resources/harness-templates/strategy-optimizer/knowledge/strategy-playbook.md`:
```markdown
# 处置策略手册

## 费用类策略
1. 证据三件套（费用明细+合同条款+合规说明）
2. 分层让利（按金额×责任瑕疵×行为风险）
3. 费用解释话术切换（从"规则说明"到"证据展示"）

## 催收类策略
1. 行为核查反馈（对每笔投诉出具核查报告）
2. 停催指令同步（建立停催→系统→第三方闭环）
3. 责任归属声明（第三方催收行为平台责任边界声明）

## 流程类策略
1. 3999线升级处置（触发后按"监管已进门"处理）
2. 高频预警机制（近15日投诉≥2次自动标记）
3. 标准化承诺时限（每类投诉明确处理时限）
```

`src/main/resources/harness-templates/strategy-optimizer/knowledge/industry-benchmarks.md`:
```markdown
# 行业基准参考

## 投诉率基准
- 消费金融行业平均投诉率：0.5-1.5%
- 头部机构投诉率：< 0.3%
- 监管关注阈值：> 2%

## 处置时效基准
- 监管投诉回复：15个工作日
- 首投响应：24小时
- 普通投诉处理：5个工作日

## 减免率基准
- 费用类投诉减免率：15-30%（行业平均）
- 催收类投诉减免率：5-10%（以行为纠正为主）
- 全额退费率：< 5%（仅限高瑕疵案件）
```

`src/main/resources/harness-templates/roi-calculator/AGENTS.md`:
```markdown
# ROI 测算师

你是投诉处置 ROI 测算专家。对每条策略做成本和影响测算，输出 ROI 排序。
测算必须给出保守/中性/积极三个区间，并推荐最优策略组合。
```

`src/main/resources/harness-templates/roi-calculator/skills/roi-simulation/SKILL.md`:
```markdown
---
name: roi-simulation
description: ROI 模拟测算方法论
---
## 测算步骤
1. 列出每条策略的成本项（直接退费敞口、人力、系统改造、合规）
2. 估算每条策略的收益项（投诉压降率、监管风险降低、客户留存）
3. 计算 ROI = 总收益 / 总成本
4. 模拟三个场景：保守（最低投入）、中性（中等投入）、积极（全面投入）
5. 推荐 ROI 最高的策略组合
```

`src/main/resources/harness-templates/roi-calculator/knowledge/cost-model.md`:
```markdown
# 成本模型

## 直接退费敞口
- 全额退费：投诉涉及的全部费用
- 部分退费：按分层让利模型计算的减免金额
- 豁免金额：停止收取但已发生未收取的费用

## 人力成本
- 客服处理工时：150元/小时
- 合规审查工时：300元/小时
- 技术开发工时：500元/小时

## 系统改造成本
- 模板化改造：一次性投入
- 自动化流程：一次性投入 + 月度维护
- 对接改造（第三方催收/债转）：一次性投入
```

`src/main/resources/harness-templates/roi-calculator/knowledge/benefit-model.md`:
```markdown
# 收益模型

## 投诉压降收益
- 每减少1起监管投诉节省的处置成本：约2000-5000元
- 避免监管处罚的期望价值：按历史处罚金额 × 发生概率

## 客户留存收益
- 每挽回一个投诉客户的终身价值：按剩余贷款金额 × 50%
- 减少负面口碑传播的隐性收益

## 品牌合规收益
- 降低舆情风险：难以量化但长期价值高
- 提升监管评级：影响业务准入和额度审批
```

- [x] **Step 5: Verify all template files are in place**

Run: `find src/main/resources/harness-templates -name "*.md" | wc -l`
Expected: 23 files

- [x] **Step 6: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 7: Commit**

```bash
git add src/main/resources/harness-templates/
git commit -m "feat: add all workspace template files for complaint reviewer and 4 subagents"
```

---

### Task 10: Run tests and verify full integration

**Files:** None new

- [x] **Step 1: Run unit tests**

Run: `mvn test -q 2>&1 | tail -20`
Expected: All tests pass (including new HarnessRuntimeTest and WorkspaceInitializerTest)

- [x] **Step 2: Verify application starts**

Run: `DASHSCOPE_API_KEY=test mvn spring-boot:run -q &` then check logs for:
- "Loaded X agent configurations" (should include complaint-reviewer)
- "HarnessAgent 'complaint-reviewer' built with workspace=..."

Kill the process after verification.

- [x] **Step 3: Commit any fixes**

If any fixes were needed during integration testing, commit them:

```bash
git add -A
git commit -m "fix: integration fixes for Harness complaint reviewer"
```

---

### Task 11: Smoke test with real interaction

**Files:** None new

- [x] **Step 1: Start the application**

Run: `export DASHSCOPE_API_KEY=<your_key> && mvn spring-boot:run`

- [x] **Step 2: Open browser and verify**

Open http://localhost:8080
1. Verify "投诉复盘分析师" appears in the agent list
2. Select it, verify description shows "基于事实链的投诉根因复盘..."
3. Upload a sample complaint data file (xlsx/csv/tsv)
4. Send "分析今日投诉数据" and verify:
   - SSE stream starts
   - Agent spawns sub-agents (look for agent_spawn in debug)
   - Final output is a structured daily report

- [x] **Step 3: Verify workspace persistence**

Check `~/.agentscope/complaint-reviewer/`:
- AGENTS.md exists
- knowledge/ directory populated
- subagents/ directory populated
- memory/ directory created (after first analysis)
- agents/ directory created (after first analysis)

Check `~/.agentscope/root-cause-analyst/`:
- AGENTS.md exists
- skills/ and knowledge/ populated

- [x] **Step 4: Commit final state**

```bash
git add -A
git commit -m "feat: complete Harness complaint reviewer integration with 4 subagents"
```
