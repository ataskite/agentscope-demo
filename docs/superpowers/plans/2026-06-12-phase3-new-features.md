# AgentScope 2.0 Phase 3: 新特性 Demo 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 AgentScope 2.0 新特性创建 Demo agent，展示 HarnessAgent 的 Compaction 和 Sandbox 能力。

**Scope:** RC3 中 Compaction 和 Sandbox 可用（在 `agentscope-harness` 包内），AgUI/A2A 扩展包尚未发布。

**Tech Stack:** AgentScope 2.0.0-RC3, Spring Boot 3.5, Java 17, HarnessAgent

---

## File Structure

**Create:**
- `src/main/java/com/skloda/agentscope/runtime/HarnessAgentRuntime.java` — HarnessAgent 专用 runtime（已存在，需更新）
- `src/main/java/com/skloda/agentscope/harness/CompactionConfigFactory.java` — Compaction 配置工厂
- `src/main/java/com/skloda/agentscope/harness/FilesystemConfigFactory.java` — Sandbox 文件系统配置工厂

**Modify:**
- `src/main/resources/config/agents.yml` — 添加 compaction-demo 和 sandbox-demo 配置
- `pom.xml` — 验证依赖（agentscope-harness 已存在）

---

## Task 1: 验证 HarnessAgent 和 Compaction/Sandbox API

**Steps:**
1. 检查 RC3 中的 HarnessAgent API：
```bash
find ~/.m2/repository/io/agentscope -name "agentscope-harness-2.0.0-RC3.jar" -not -name "*sources*" | head -1 | xargs javap -public -cp {} io.agentscope.harness.agent.HarnessAgent 2>/dev/null | head -50
```

2. 检查 CompactionMiddleware 和 CompactionConfig：
```bash
find ~/.m2/repository/io/agentscope -name "agentscope-harness-2.0.0-RC3.jar" -not -name "*sources*" | head -1 | xargs javap -public -cp {} io.agentscope.harness.agent.middleware.CompactionMiddleware 2>/dev/null
find ~/.m2/repository/io/agentscope -name "agentscope-harness-2.0.0-RC3.jar" -not -name "*sources*" | head -1 | xargs javap -public -cp {} io.agentscope.harness.agent.memory.compaction.CompactionConfig 2>/dev/null
```

3. 检查 FilesystemConfig 和 Sandbox 后端：
```bash
find ~/.m2/repository/io/agentscope -name "agentscope-harness-2.0.0-RC3.jar" -not -name "*sources*" | head -1 | xargs jar tf | grep -i "filesystem\|FilesystemConfig" | head -10
find ~/.m2/repository/io/agentscope -name "agentscope-harness-2.0.0-RC3.jar" -not -name "*sources*" | head -1 | xargs jar tf | grep -i "sandbox" | grep -E "\.class$" | head -10
```

- [ ] 验证 API 可用性
- [ ] 记录关键类和方法签名

---

## Task 2: 创建 Compaction 配置工厂

**Create:** `src/main/java/com/skloda/agentscope/harness/CompactionConfigFactory.java`

根据 RC3 API 创建 CompactionConfig 实例。

```java
package com.skloda.agentscope.harness;

import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.stereotype.Component;

@Component
public class CompactionConfigFactory {

    public CompactionConfig createDefault() {
        // 根据实际 API 调整
        return CompactionConfig.builder()
                .enable(true)
                .threshold(10000)  // 10k tokens 触发压缩
                .compactionModel("dashscope:qwen-plus")
                .build();
    }
}
```

- [ ] 创建文件
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add CompactionConfigFactory`

---

## Task 3: 创建 Filesystem 配置工厂

**Create:** `src/main/java/com/skloda/agentscope/harness/FilesystemConfigFactory.java`

```java
package com.skloda.agentscope.harness;

import io.agentscope.harness.agent.filesystem.FilesystemConfig;
import org.springframework.stereotype.Component;

@Component
public class FilesystemConfigFactory {

    public FilesystemConfig createLocal() {
        // Local backend (开箱即用)
        return FilesystemConfig.local();
    }

    public FilesystemConfig createDocker() {
        // Docker backend (可选，需 Docker 环境)
        return FilesystemConfig.docker();
    }
}
```

- [ ] 创建文件
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add FilesystemConfigFactory`

---

## Task 4: 更新 HarnessAgentRuntime

**Modify:** `src/main/java/com/skloda/agentscope/harness/HarnessAgentRuntime.java`

- [ ] 读取当前文件
- [ ] 检查是否已使用新 API（EventSink）
- [ ] 如需要，更新以支持 Compaction 和 Sandbox 事件
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `refactor: update HarnessAgentRuntime for Compaction/Sandbox support`

---

## Task 5: 添加 agents.yml 配置

**Modify:** `src/main/resources/config/agents.yml`

添加两个 HARNESS 类型 demo agent：

1. **compaction-demo** (HARNESS)
   - systemPrompt: 长对话场景，触发上下文压缩
   - 配置 CompactionConfig
   - 展示自动压缩事件

2. **sandbox-demo** (HARNESS)
   - systemPrompt: 代码执行场景，使用沙箱
   - 配置 FilesystemConfig.local()
   - 展示安全代码执行

- [ ] 添加配置
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add compaction-demo and sandbox-demo agents`

---

## Task 6: 接入 AgentRuntimeFactory

**Modify:** `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java`

- [ ] 读取当前文件
- [ ] 检查 HARNESS 类型的处理（可能还是 fallback 到 SINGLE）
- [ ] 如需要，创建 `createHarnessRuntime()` 方法
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: wire HARNESS agent type in AgentRuntimeFactory`

---

## Task 7: 最终验证

- [ ] `mvn clean compile` — BUILD SUCCESS
- [ ] 验证 HARNESS 类型不再 fallback 到 SINGLE（或有明确说明）
- [ ] 验证 compaction-demo 和 sandbox-demo 配置存在
- [ ] Commit: `chore: Phase 3 complete — Compaction and Sandbox demos added`

---

## 注意事项

1. **AgUI/A2A 未发布** — RC3 中 `agentscope-extensions-agui` 和 `agentscope-extensions-a2a` 不存在，Phase 3 仅实现 Compaction 和 Sandbox。

2. **API 可能性** — HarnessAgent、CompactionConfig、FilesystemConfig 的具体 API 需通过 `javap` 验证后调整代码。

3. **运行时依赖** — Sandbox 的 Docker 后端需要本地 Docker 环境，Local 后端应开箱即用。

4. **事件展示** — Compaction 事件应在前端 Debug 面板展示（可能需要 Phase 4 前端适配）。
