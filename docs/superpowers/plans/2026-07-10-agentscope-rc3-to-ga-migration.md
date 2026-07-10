# AgentScope RC3 → 2.0.0 GA 迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目从 AgentScope 2.0.0-RC3（pom 已先行改为 RC5/GA 版本号但 Java 代码未跟）迁到 2.0.0 GA，使 `mvn clean compile` 通过、341 个单测全绿、核心端到端路径不回归。

**Architecture:** 采用 spec 方案 A「最小依赖补丁」——核心改动只有两项：(1) pom 新增 `agentscope-extensions-model-dashscope` 扩展依赖并确认版本号为 `2.0.0`；(2) 4 个 Java 文件里 DashScope 相关类从 `io.agentscope.core.*` 旧包迁移到 `io.agentscope.extensions.model.dashscope` 新包。RC5 的 provider 模块化是 RC3→GA 之间唯一的 breaking change，RC4 与 GA 本身无破坏性变更，故 builder 调用与方法签名都不变，纯 import 迁移 + 编译驱动。

**Tech Stack:** Java 17, Maven, Spring Boot 3.5.14, AgentScope 2.0.0 GA（`agentscope-core` / `agentscope-harness` / `agentscope-spring-boot-starter` / `agentscope-extensions-rag-simple` / `agentscope-extensions-memory-bailian` / **新增** `agentscope-extensions-model-dashscope`）。

## Global Constraints

（全部逐字摘自 spec，每个 task 隐式包含本节要求）

- 目标版本：`agentscope 2.0.0`（GA，2026-07-10 发布）
- 迁移方案：方案 A 最小依赖补丁——**仅** 加 dashscope 扩展依赖 + 版本号到 GA + DashScope 类包名迁移，不引其他改动
- 实际 Java 包前缀：`com.skloda.agentscope`（注意：AGENTS.md 写的 `com.msxf` 已过时，以代码实际为准）
- DashScopeChatModel 新包名（spec 已确定）：`io.agentscope.extensions.model.dashscope.DashScopeChatModel`
- DashScopeChatFormatter / DashScopeTextEmbedding 新子包：编译驱动确认（spec 3.1 明确「确切子包编译时确认」）
- builder 调用与方法签名**不变**，纯 import 迁移
- 配置零改动：`application.yml` / `agents.yml` / `harness-agents.yml` 均不改
- 明确不做（ROADMAP 积压，留给后续，本计划严禁触碰）：HarnessRuntime 迁 streamEvents、compaction/sandbox 接线 HarnessAgentFactory、前端 7 个新 SSE 事件渲染、CLAUDE.md 架构描述同步、RAG/LTM v2 迁移
- 当前分支：`agentscope-latest-upgrade`；迁移作为**独立 commit**；失败 `git revert` 该 commit 回 RC3 基线（已提交的 trace 增强 commit `871547d` 不受影响）

---

## File Structure

| 文件 | 责任 | 本次改动 |
|----|----|----|
| `pom.xml` | 依赖与版本管理 | 确认 `agentscope.version=2.0.0`；新增 `agentscope-extensions-model-dashscope` 依赖 |
| `src/main/java/com/skloda/agentscope/agent/AgentFactory.java` | 单 agent 构造，含 DashScopeChatModel.builder() | 迁移 2 个 import（DashScopeChatModel + DashScopeChatFormatter） |
| `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java` | 多 agent 编排构造 | 迁移 2 个 import（DashScopeChatModel + DashScopeChatFormatter） |
| `src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java` | Harness agent 构造 | 迁移 1 个 import（DashScopeChatModel） |
| `src/main/java/com/skloda/agentscope/service/KnowledgeService.java` | RAG 知识服务（embedding 路径，重点回归） | 迁移 1 个 import（DashScopeTextEmbedding） |
| `docs/superpowers/specs/2026-07-10-agentscope-rc3-to-ga-migration-design.md` | 本迁移设计文档 | 更新状态行：设计已确认→已实现 |
| `ROADMAP.md` | 项目路线图与版本基线 | 更新版本基线行：RC3 → 2.0.0 GA |

**分解决策：** 这是一次高度内聚的迁移，4 个 Java 文件是同一个 breaking change（provider 模块化）的不同落点，且必须同时编译通过才有意义——因此它们合并为**一个 Task**（Task 3），一次编译验证。pom 改动（Task 2）与 Gate（Task 1）各自是独立可验证的交付物，单独成 Task。收尾（Task 6）是独立的文档同步，单独成 Task 以便 reviewer 独立审阅。

---

## Task 1: 前置 Gate — Maven 可用性检查

**Files:**
- Read-only 验证，不改任何文件

**Interfaces:**
- Consumes: 无
- Produces: 「2.0.0 GA 已同步到 Maven Central」这一前提事实。**Task 2 及之后全部依赖此 Gate 通过**；Gate 失败则整个计划中止，不得改 pom。

> **为什么单独成 Task：** spec 第 4 节明确这是「不可绕过」的 Gate。GA GitHub release 仍标 Pre-release，Maven Central 同步状态待确认。Maven 未同步前改 pom 会导致编译断裂（ROADMAP 已警示）。Gate 必须先过。

- [ ] **Step 1: 检查 agentscope-core 2.0.0 是否已在 Maven Central 可用**

Run:
```bash
mvn dependency:get -Dartifact=io.agentscope:agentscope-core:2.0.0 -U
```
Expected: `BUILD SUCCESS`。若输出 `Could not find artifact ...` 或 `404`/`502`，说明 Maven Central 尚未同步——**中止计划**，向用户报告并等待同步，不要继续后续 Task。

- [ ] **Step 2: 检查 dashscope 扩展 2.0.0 是否可用**

Run:
```bash
mvn dependency:get -Dartifact=io.agentscope:agentscope-extensions-model-dashscope:2.0.0 -U
```
Expected: `BUILD SUCCESS`。这是本次新增依赖，必须单独验证其 GA 版本已发布。

- [ ] **Step 3: 确认 harness / starter / rag-simple / memory-bailian 的 GA 版本可用（批量）**

Run:
```bash
for a in agentscope-harness agentscope-spring-boot-starter agentscope-extensions-rag-simple agentscope-extensions-memory-bailian; do
  echo "=== $a ===";
  mvn dependency:get -Dartifact=io.agentscope:$a:2.0.0 -U || echo "MISSING: $a";
done
```
Expected: 每个 artifact 都 `BUILD SUCCESS`，无 `MISSING`。任一缺失则中止计划并报告。

> Gate 全部通过后无需 commit（未改任何文件），直接进入 Task 2。

---

## Task 2: pom.xml — 确认版本号 + 新增 dashscope 扩展依赖

**Files:**
- Modify: `pom.xml:25`（版本号，确认已是 `2.0.0`）
- Modify: `pom.xml:28-110`（dependencies 段，新增一项）

**Interfaces:**
- Consumes: Task 1 Gate 通过（2.0.0 GA artifacts 在 Maven 可用）
- Produces: pom 声明了 `agentscope-extensions-model-dashscope:2.0.0`，使 Task 3 的新 import 包能被解析；其余 agentscope 依赖随 `${agentscope.version}` 自动升 GA。

- [ ] **Step 1: 确认 agentscope.version 已为 2.0.0**

Run:
```bash
grep -n "agentscope.version" pom.xml
```
Expected: `<agentscope.version>2.0.0</agentscope.version>`（第 25 行）。工作区已有未提交改动 `M pom.xml` 把它从 RC5 改到了 2.0.0——确认即可，**无需修改**。若显示的仍是 RC5，则编辑第 25 行改为 `2.0.0`。

- [ ] **Step 2: 新增 dashscope 扩展依赖**

在 `pom.xml` 的 `<dependencies>` 段内、`agentscope-core` 依赖块之后插入新依赖。定位锚点：紧接在 `agentscope-core` 的 `</dependency>` 之后、`agentscope-harness` 之前插入。

修改位置：`pom.xml:37-41` 是现有的 core 依赖块，在其后（第 41 行 `</dependency>` 之后）插入：

```xml
        <!-- AgentScope DashScope Model Provider (RC5+ 模块化：从 core 拆出) -->
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-extensions-model-dashscope</artifactId>
            <version>${agentscope.version}</version>
        </dependency>
```

- [ ] **Step 3: 校验 pom.xml 语法（解析）**

Run:
```bash
mvn -q help:effective-pom >/dev/null && echo "POM OK"
```
Expected: 输出 `POM OK`，无 XML 解析错误。

- [ ] **Step 4: 校验依赖树能解析到 dashscope 扩展（下载 + 确认坐标）**

Run:
```bash
mvn -q dependency:list | grep dashscope
```
Expected: 出现一行类似 `io.agentscope:agentscope-extensions-model-dashscope:jar:2.0.0:compile`。若为空或报错，说明 Step 2 插入位置/坐标有误，回到 Step 2 修正。

- [ ] **Step 5: 不单独 commit，进入 Task 3**

> pom 改动与代码 import 迁移必须在一个编译通过的 commit 里（spec 第 8 节执行顺序：pom → 编译 → 包名迁移 → test → 单一 commit）。此处先不 commit，合并到 Task 3/4 完成后的统一 commit（Task 5）。

---

## Task 3: Java 包名迁移（4 文件，编译驱动）

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java:13,18`
- Modify: `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java:15,16`
- Modify: `src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java:5`
- Modify: `src/main/java/com/skloda/agentscope/service/KnowledgeService.java:5`

**Interfaces:**
- Consumes: Task 2 完成（dashscope 扩展依赖已在 pom，新包可被解析）
- Produces: 4 个文件全部使用 GA 的新包名，为 Task 4 的 `mvn clean compile` 通过做准备。builder 调用与方法签名不变，故下游（AgentService / ChatController / runtime 层）无需改动。

> **迁移映射表（来自 spec 3.1）：**
> | 类 | 旧 import（RC3, core） | 新 import（GA, extensions） |
> |----|----|----|
> | `DashScopeChatModel` | `io.agentscope.core.model.DashScopeChatModel` | `io.agentscope.extensions.model.dashscope.DashScopeChatModel` |
> | `DashScopeChatFormatter` | `io.agentscope.core.formatter.dashscope.DashScopeChatFormatter` | `io.agentscope.extensions.model.dashscope.DashScopeChatFormatter`（子包编译驱动确认，见 Step 1） |
> | `DashScopeTextEmbedding` | `io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding` | `io.agentscope.extensions.model.dashscope.DashScopeTextEmbedding`（子包编译驱动确认，见 Step 1） |
>
> 注意：Formatter 与 Embedding 的确切子包 spec 标注「编译时确认」。最可能在 `io.agentscope.extensions.model.dashscope` 顶层包下（与 ChatModel 同包），但若编译报错，按 Step 1 的方法查实际包名。

- [ ] **Step 1: 用扩展 jar 反查 3 个类的确切新包名（消除 spec 的不确定项）**

先确保 dashscope 扩展 jar 已下载（Task 2 Step 4 已触发下载），再从本地 Maven 仓库解压 jar 查 class 路径：

Run:
```bash
JAR=$(find ~/.m2/repository/io/agentscope/agentscope-extensions-model-dashscope/2.0.0 -name "*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)
echo "JAR=$JAR"
unzip -l "$JAR" | grep -E "DashScopeChatModel|DashScopeChatFormatter|DashScopeTextEmbedding" | grep "\.class$"
```
Expected: 三行输出，形如：
```
  ...  io/agentscope/extensions/model/dashscope/DashScopeChatModel.class
  ...  io/agentscope/extensions/model/dashscope/DashScopeChatFormatter.class   （或 .../formatter/DashScopeChatFormatter.class）
  ...  io/agentscope/extensions/model/dashscope/DashScopeTextEmbedding.class   （或 .../embedding/DashScopeTextEmbedding.class）
```
把反斜杠转成点号即得确切包名。**记录下这三个包名**，后续 Step 2-5 用它们替换。若某个类找不到，说明该类在 GA 改名或移到别处——按 Step 6 的 fallback 处理。

- [ ] **Step 2: 迁移 AgentFactory.java 的 2 个 import**

文件：`src/main/java/com/skloda/agentscope/agent/AgentFactory.java`

第 13 行旧：
```java
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
```
第 18 行旧：
```java
import io.agentscope.core.model.DashScopeChatModel;
```

用 Step 1 查到的确切新包名替换。若 Step 1 确认两者同在 `io.agentscope.extensions.model.dashscope`，则替换为：
```java
import io.agentscope.extensions.model.dashscope.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
```
（若 Step 1 显示 Formatter 在子包如 `...dashscope.formatter`，则用那个确切路径。）文件内 builder 调用（第 121、126、200、205 行）**不动**。

- [ ] **Step 3: 迁移 CompositeAgentFactory.java 的 2 个 import**

文件：`src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

第 15 行旧：
```java
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
```
第 16 行旧：
```java
import io.agentscope.core.model.DashScopeChatModel;
```
替换为 Step 1 查到的确切新包名（同 Step 2）。文件内 builder 调用（第 127、132、147、152、249、254、268、273 行）**不动**。

- [ ] **Step 4: 迁移 HarnessAgentFactory.java 的 1 个 import**

文件：`src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java`

第 5 行旧：
```java
import io.agentscope.core.model.DashScopeChatModel;
```
替换为：
```java
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
```
注意：第 6 行 `import io.agentscope.core.model.Model;` **保留不动**（`Model` 接口仍在 core）。第 48 行 `Model model = DashScopeChatModel.builder()...` **不动**。

- [ ] **Step 5: 迁移 KnowledgeService.java 的 1 个 import（RAG embedding，重点回归）**

文件：`src/main/java/com/skloda/agentscope/service/KnowledgeService.java`

第 5 行旧：
```java
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
```
用 Step 1 查到的确切新包名替换。若确认在 `io.agentscope.extensions.model.dashscope`，替换为：
```java
import io.agentscope.extensions.model.dashscope.DashScopeTextEmbedding;
```
第 69 行 `DashScopeTextEmbedding embeddingModel = DashScopeTextEmbedding.builder()...` **不动**。

- [ ] **Step 6: 全局扫描确认无遗漏的旧 core DashScope import**

Run:
```bash
grep -rn "io.agentscope.core.model.DashScopeChatModel\|io.agentscope.core.formatter.dashscope\|io.agentscope.core.embedding.dashscope" src/ || echo "CLEAN: 无残留旧 import"
```
Expected: `CLEAN: 无残留旧 import`。若有残留，按 Step 2-5 同法迁移对应文件。

- [ ] **Step 7: 不单独 commit，进入 Task 4 编译验证**

> 4 个文件同属一个 breaking change，必须一起编译通过。留待 Task 5 统一 commit。

---

## Task 4: 编译 + 全量单测验证

**Files:**
- 无文件改动；仅运行验证命令

**Interfaces:**
- Consumes: Task 2（pom）+ Task 3（import 迁移）完成
- Produces: 「GA 上编译通过 + 341 测试全绿」的可验证事实，作为 Task 5 commit 的前置条件与 Task 6 手测的基础。

- [ ] **Step 1: 全量编译**

Run:
```bash
mvn clean compile
```
Expected: `BUILD SUCCESS`。

若失败，按报错类型处理：
- `package io.agentscope.extensions.model.dashscope does not exist` → dashscope 扩展依赖未生效，回到 Task 2 Step 2 检查 pom 插入位置/坐标
- `cannot find symbol class DashScopeChatFormatter/DashScopeTextEmbedding` → 子包名不对，回到 Task 3 Step 1 用 jar 查实际包名，修正 Task 3 Step 2/5 的 import
- 其余符号找不到 → builder API 可能有微调（spec 判断无 breaking，概率低），按报错行号就近查 GA javadoc 修正

- [ ] **Step 2: 全量单测**

Run:
```bash
mvn test
```
Expected: `BUILD SUCCESS`，`Tests run: 341`（spec 基线，全绿）。记录实际运行数；若数量略变（GA 可能带新测试）以「无失败」为准。

- [ ] **Step 3: 失败处理**

若有测试失败：
1. 判断是否与本次迁移相关（DashScope 路径 / RAG embedding / 模型构建）。相关失败须在本任务内修复后重跑 Step 2，不得 commit 失败状态。
2. 若失败明显与迁移无关（flaky / 环境相关），记录现象并向用户报告，确认后再决定是否阻塞 commit。

---

## Task 5: 独立 commit

**Files:**
- 提交 Task 2（pom）+ Task 3（4 个 Java 文件）的全部改动

**Interfaces:**
- Consumes: Task 4 Step 1/2 均通过
- Produces: 一个独立、可 `git revert` 的迁移 commit（spec 第 7 节回滚策略要求）。

- [ ] **Step 1: 复核改动范围（确认只含迁移相关文件）**

Run:
```bash
git status
```
Expected: 改动仅含 `pom.xml` + 4 个 Java 文件（AgentFactory / CompositeAgentFactory / HarnessAgentFactory / KnowledgeService）。**不应**含 `application.yml`、`agents.yml`、前端 html、CLAUDE.md 等积压项文件。若有多余改动，`git stash` 或剔除后再提交。

- [ ] **Step 2: 暂存并提交**

Run:
```bash
git add pom.xml \
  src/main/java/com/skloda/agentscope/agent/AgentFactory.java \
  src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java \
  src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java \
  src/main/java/com/skloda/agentscope/service/KnowledgeService.java
git commit -m "chore: 迁移 AgentScope RC3 → 2.0.0 GA（DashScope provider 模块化）

- pom: agentscope.version 确认为 2.0.0，新增 agentscope-extensions-model-dashscope 依赖（RC5 provider 模块化）
- 迁移 DashScopeChatModel/DashScopeChatFormatter/DashScopeTextEmbedding 从 io.agentscope.core.* 到 io.agentscope.extensions.model.dashscope
- builder 调用与方法签名不变；mvn test 341 全绿"
```
Expected: commit 成功。记下 commit hash（回滚时 `git revert <hash>`）。

---

## Task 6: 端到端手测 + 收尾文档更新

**Files:**
- Modify: `docs/superpowers/specs/2026-07-10-agentscope-rc3-to-ga-migration-design.md:4`（状态行）
- Modify: `ROADMAP.md:4-5`（版本基线行）

**Interfaces:**
- Consumes: Task 5 commit 完成（服务可在 GA 上启动）
- Produces: 端到端无回归的验证记录 + 设计文档与 ROADMAP 的版本基线同步到 GA。

> spec 第 6.2 节要求端到端手测覆盖 7 类 agent，第 8 节第 7 步要求收尾只更新「本设计文档状态行 + ROADMAP 版本基线行」，**不**做 CLAUDE.md 等积压项。

- [ ] **Step 1: 起服务**

Run:
```bash
mvn spring-boot:run
```
Expected: 应用在 `http://localhost:8080` 启动，日志无 `BeanCreationException` / DashScope 相关异常。启动失败则按报错排查（大概率是某个 DashScope bean 的 auto-config 因新扩展模块需要额外配置——但 spec 3.3 判断配置零改动兼容，若实际不符需回 Task 2 评估是否缺 starter 配置）。

- [ ] **Step 2: 手测 7 类 agent（UI 逐个验证，记录通过/失败）**

在 `http://localhost:8080` 对每类至少一个代表 agent 发一条消息，确认有正常流式回复且 debug 面板有事件。清单（逐项打勾）：

- [ ] **SINGLE**：`chat-basic` 或 `tool-test-simple`（对话 + 工具调用）
- [ ] **多 agent**：`doc-analysis-pipeline`(SEQUENTIAL) 或 `smart-router`(ROUTING)（流式编排）
- [ ] **HARNESS**：`compaction-demo`（仍走 `agent.stream()`，RC3 已知限制，本次不改——确认仍能跑即可）
- [ ] **MCP**：任一 mcp agent（远程工具调用）
- [ ] **RAG（重点）**：`rag-chat`——走 `DashScopeTextEmbedding`，包名迁移后**必验**。发一条需要检索知识库的问题，确认返回带知识库依据的回答。
- [ ] **文件上传 + 文档解析**：`task-document-analysis`（上传 docx/pdf，确认解析正常）
- [ ] **HITL 审批**：`contract-review-workflow` 或银行发票 agent

任一类严重回归（尤其 RAG / 所有走 DashScopeChatModel 的路径）则 `git revert <Task5 hash>` 回 RC3 基线并向用户报告。

- [ ] **Step 3: deepseek-v4-flash 兼容性确认（重点回归）**

确认当前默认 model `deepseek-v4-flash` 在百炼账户可用：观察 Step 2 中任一 chat agent 的实际 LLM 调用是否成功（debug 面板 `llm_end` 事件有正常 token usage，无 model-not-found 类错误）。若账户不支持，按 spec 第 7 节风险表 fallback 改回 `qwen-plus`（改 `agents.yml` 对应 agent 的 `modelName`）——但此改动属于配置 fallback，需单独评估，不纳入本迁移 commit。

- [ ] **Step 4: 停服务**

Run: 在 `mvn spring-boot:run` 的终端按 `Ctrl+C`，或 `pkill -f "spring-boot:run"`。
Expected: 进程退出。

- [ ] **Step 5: 更新设计文档状态行**

文件：`docs/superpowers/specs/2026-07-10-agentscope-rc3-to-ga-migration-design.md`

第 4 行旧：
```
> 状态: 设计已确认，待实现
```
改为：
```
> 状态: 已实现（2.0.0 GA，mvn test 全绿，端到端手测通过）
```

- [ ] **Step 6: 更新 ROADMAP 版本基线行**

文件：`ROADMAP.md`

第 4 行旧：
```
> Current local baseline: Spring Boot 3.5.14, Java 17, `agentscope.version=2.0.0-RC3`
```
改为：
```
> Current local baseline: Spring Boot 3.5.14, Java 17, `agentscope.version=2.0.0`
```

第 5 行旧：
```
> 上游最新: `2.0.0-RC4`（2026-06-18 发布于 GitHub，Maven Central 暂未同步）
```
改为：
```
> 上游最新: `2.0.0` GA（2026-07-10 发布，项目已迁至 GA）
```

> 注意：ROADMAP 其余提及 RC3/RC4 的描述性文字（如第 20、22、33 行）属历史记录，**不在本次收尾范围**（spec 明确只更新「版本基线行」），保留不动以免越界。

- [ ] **Step 7: 提交收尾改动**

Run:
```bash
git add docs/superpowers/specs/2026-07-10-agentscope-rc3-to-ga-migration-design.md ROADMAP.md
git commit -m "docs: RC3→2.0.0 GA 迁移收尾（设计状态 + ROADMAP 基线行）"
```
Expected: commit 成功。迁移全部完成。

---

## Self-Review

**1. Spec 覆盖检查（逐节核对 spec）：**
- §2.1 目标（compile / test 341 / 端到端不回归）→ Task 4（compile+test）、Task 6 Step 2（端到端）。✅
- §2.2 方案 A 最小依赖补丁 → Global Constraints 锁定 + Task 2/3 仅做 pom + import。✅
- §2.3 明确不做项 → Global Constraints 逐条列入「严禁触碰」，Task 6 Step 6 备注限定只改基线行。✅
- §3.1 DashScope 模块化包名 → Task 3 映射表 + Step 1 jar 反查消除不确定。✅
- §3.2 DeepSeek 归属（builder 直构、不走 registry）→ Global Constraints「builder 调用不变」+ Task 6 Step 3 兼容性确认。✅
- §3.3 配置零改动 → Global Constraints 锁定，无 task 改 yml。✅
- §4 前置 Gate → Task 1 完整覆盖两条 dependency:get + 其余 artifact。✅
- §5.1 pom → Task 2。✅
- §5.2 包名迁移 3 类 4 文件 → Task 3 映射表逐一对应（AgentFactory / CompositeAgentFactory / HarnessAgentFactory / KnowledgeService）。✅
- §5.3 配置零改动 → 无 task。✅
- §6.1 自动化 → Task 4。✅
- §6.2 端到端 7 类 → Task 6 Step 2 逐条。✅
- §6.3 重点回归（DashScopeChatModel 路径 + RAG embedding）→ Task 6 Step 2 标注 RAG 必验 + Step 3 deepseek 确认。✅
- §7 回滚（独立 commit、git revert）→ Task 5 独立 commit + Task 6 Step 2 失败 revert 指令。✅
- §8 执行顺序 7 步 → Task 1(Gate)→2(pom)→3(包名)→4(test)→6Step2(手测)→5(commit)→6Step5-6(收尾)。注意：spec 第 8 节顺序是「手测(5) → commit(6)」，本计划调整为「commit(5) → 手测(6)」——理由：手测需起服务，耗时且可能发现需回滚的问题；先 commit 一个编译+单测通过的稳定点，手测失败时 `git revert` 更干净。这一调整不违背 spec 意图（仍是独立 commit、失败可 revert）。✅
- §9 参考来源 → 实现时如需查证可直接用，无需单独 task。✅

**2. 占位符扫描：** 无 TBD/TODO；所有 import 迁移都给了确切旧串与「Step 1 查得的新串」的明确替换流程（新子包的两种可能都写了 fallback）；测试以 `mvn test` + 手测 checklist 形式给出，非「写测试」占位。✅

**3. 类型/命名一致性：** 三个类名（DashScopeChatModel / DashScopeChatFormatter / DashScopeTextEmbedding）在 Task 3 映射表、Step 2-5、Task 6 Step 2 复盘描述中全程一致；`io.agentscope.extensions.model.dashscope` 包名在 Global Constraints、File Structure、Task 3、Task 4 Step 1 报错处理中一致；`agentscope-extensions-model-dashscope` artifact 名在 Task 1/2/4 一致。✅

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-10-agentscope-rc3-to-ga-migration.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 每个 Task 派一个 fresh subagent，Task 间两阶段 review，快速迭代。适合本计划：Task 1 Gate 与 Task 3 包名查询可并行/独立，subagent 隔离性强。

**2. Inline Execution** - 在当前会话用 executing-plans 顺序执行，带 checkpoint review。适合需要紧密观察编译报错、逐步修正 import 子包的场景。

Which approach?
