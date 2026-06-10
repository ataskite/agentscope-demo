# 金融情报追踪助手（Finance Intelligence Tracker）设计文档

**日期**: 2024-12-02
**版本**: 1.0
**类型**: 新功能演示
**模式**: AgentScope Claw（单人本机）

---

## 1. 概述

基于 AgentScope Java 1.1.0-RC1 Harness 能力，构建一个金融情报追踪助手演示。该助手能够自动采集金融行业动态，发现趋势信号，生成分析简报，并具备自我进化能力。

### 1.1 设计目标

展示 AgentScope Harness 核心能力：
- 工作区驱动的自我进化（Agent 自己写入技能、调整行为）
- 子 Agent 委派与工作区隔离
- Shell 执行能力（数据拉取、文件处理）
- 双层记忆（每日记忆流水账 + MEMORY.md 合并）
- 文件系统操作（读写、报告生成）

### 1.2 参考文档

- [AgentScope Java Harness 发布文章](https://mp.weixin.qq.com/s/aZhE0aPDaARxJLDI6ZrSFw)
- [agentscope-claw 示例](https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-examples/agents/agentscope-claw)

---

## 2. Agent 架构

### 2.1 主 Agent

**ID**: `finance-intel-tracker`
**类型**: `HARNESS`
**工作区**: `${user.home}/.agentscope/finance-intel-tracker/`

**核心职责**：
1. 接收用户研究指令或文件上传
2. 解析用户意图，提取关键词和关注领域
3. 按序委派子 Agent：情报采集 → 趋势分析 → 报告撰写
4. 向用户呈现最终结论和报告路径
5. 自动孵化新子 Agent（当某类任务反复出现时）

### 2.2 子 Agent

#### 2.2.1 情报采集员（intel-collector）

**工作区**: `${user.home}/.agentscope/finance-intel-tracker/agents/intel-collector/workspace/`

**核心能力**：
- 使用 `web_search` 工具搜索网络信息
- 使用 `execute` 工具拉取公开数据（curl、sed、awk）
- 自我进化：发现有效搜索模式后写入新技能
- 自我进化：发现新权威信息源后更新知识库

**技能**：
- `keyword-extraction`: 从用户指令提取关键词和搜索策略
- `efficient-search-patterns`: （自我进化新增）高效搜索模式
- `data-fetch`: （Shell 相关）数据拉取与格式转换

**知识库**：
- `search-strategy.md`: 金融行业搜索策略模板
- `data-sources.md`: 央行、银保监会等权威数据源（可自我增长）

#### 2.2.2 趋势分析师（finance-trend-analyst）

**工作区**: `${user.home}/.agentscope/finance-intel-tracker/agents/finance-trend-analyst/workspace/`

**核心能力**：
- 接收采集员返回的情报摘要
- 使用技能检测趋势信号和异常事件
- 调用 `execute` 工具进行数据分析（Python/grep）
- 对比历史记忆（MEMORY.md）发现变化
- 将关键发现追加到 MEMORY.md

**技能**：
- `trend-signal-detection`: 趋势信号检测框架
- `cross-period-comparison`: 跨期对比分析
- `custom-signals`: （自我进化新增）自定义信号检测逻辑
- `data-analysis`: （Shell 相关）数据分析脚本执行

**知识库**：
- `trend-framework.md`: 趋势分析框架
- `indicator-glossary.md`: 金融指标术语表
- `historical-patterns.md`: 历史模式记录（可自我增长）

**记忆**：
- 启用双层记忆：每日流水账自动合并到 MEMORY.md
- 跨日积累趋势判断能力

#### 2.2.3 报告撰写员（intel-report-writer）

**工作区**: `${user.home}/.agentscope/finance-intel-tracker/agents/intel-report-writer/workspace/`

**核心能力**：
- 接收采集员摘要和趋势分析师结论
- 按模板生成结构化报告
- 使用 `write_file` 工具保存报告到主 Agent 的 `reports/` 目录
- 根据用户反馈调整报告格式（自我进化）

**技能**：
- `briefing-generation`: 情报简报生成规范

**知识库**：
- `report-template.md`: 报告模板和格式规范

---

## 3. 工作区结构

```
~/.agentscope/finance-intel-tracker/                    # 主 Agent 工作区
├── AGENTS.md                                            # 主 Agent 人格与委派规则
├── MEMORY.md                                            # 主 Agent 长期记忆（用户偏好）
├── memory/                                              # 每日记忆流水账
│   ├── 2024-12-02.md
│   └── ...
├── knowledge/
│   ├── finance-domains.md                               # 金融子领域分类
│   └── data-sources.md                                 # 权威数据源列表（可自我增长）
├── skills/                                              # 主 Agent 的技能（可自我增长）
├── reports/                                             # 生成的情报简报
│   ├── 2024-12-02_银行理财监管政策简报.md
│   └── ...
├── subagents/                                           # 子 Agent 声明（可自动孵化）
│   ├── intel-collector.md
│   ├── finance-trend-analyst.md
│   ├── intel-report-writer.md
│   └── [未来自动孵化的新 subagent]
└── agents/                                              # 子 Agent 运行时数据
    ├── intel-collector/
    │   └── workspace/
    │       ├── AGENTS.md
    │       ├── skills/
    │       │   ├── keyword-extraction/SKILL.md
    │       │   ├── efficient-search-patterns/SKILL.md  # 自我进化
    │       │   └── data-fetch/SKILL.md
    │       └── knowledge/
    │           ├── search-strategy.md
    │           └── data-sources.md
    ├── finance-trend-analyst/
    │   └── workspace/
    │       ├── AGENTS.md
    │       ├── MEMORY.md                                # 双层记忆
    │       ├── memory/
    │       ├── skills/
    │       │   ├── trend-signal-detection/SKILL.md
    │       │   ├── cross-period-comparison/SKILL.md
    │       │   ├── custom-signals/SKILL.md              # 自我进化
    │       │   └── data-analysis/SKILL.md
    │       └── knowledge/
    │           ├── trend-framework.md
    │           ├── indicator-glossary.md
    │           └── historical-patterns.md              # 可自我增长
    └── intel-report-writer/
        └── workspace/
            ├── AGENTS.md
            ├── skills/
            │   └── briefing-generation/SKILL.md          # 可自我调整
            └── knowledge/
                └── report-template.md
```

---

## 4. 数据流与交互

### 4.1 典型流程：情报分析

```
用户输入："分析近期银行理财监管政策变化"
        ↓
主 Agent 解析意图，提取关键词 [银行理财, 监管政策]
        ↓
委派 intel-collector
        ↓
采集员执行搜索 + 数据拉取
        ↓
返回情报摘要
        ↓
委派 finance-trend-analyst
        ↓
趋势分析师对比历史记忆，检测信号
        ↓
追加发现到 MEMORY.md
        ↓
委派 intel-report-writer
        ↓
报告撰写员生成简报
        ↓
写入 reports/ 目录
        ↓
主 Agent 呈现结论 + 报告路径
```

### 4.2 自我进化示例

**场景**：用户多次查询监管政策类问题

1. 趋势分析师检测到共同模式（都涉及"监管文件发布时间"）
2. 自动调用 `write_file` 创建新技能：`skills/policy-timeline/SKILL.md`
3. 下次同类查询时，自动使用该技能

### 4.3 Shell 执行示例

**场景**：查询最新存贷款利率数据

1. 采集员执行：`curl https://www.pbc.gov.cn/api/interest-rates`
2. 使用 `execute "python3 parse_rates.py"` 解析 JSON
3. 保存结果到 `knowledge/latest-rates.md`
4. 趋势分析师对比历史数据

### 4.4 子 Agent 自动孵化

**场景**：用户频繁询问汇率影响

1. 主 Agent 发现现有流程效率不足
2. 自动创建 `subagents/forex-analyst.md`
3. 下次汇率相关问题直接委派

### 4.5 双层记忆维护

- 每日对话结束时，`MemoryMaintenanceHook` 自动触发
- 将 `memory/2024-12-02.md` 合并到 `MEMORY.md`
- 趋势分析师的长期记忆持续增长

---

## 5. 实现要点

### 5.1 Java 类结构

使用现有基础设施，新增配置和模板：

```
src/main/java/com/skloda/agentscope/
├── harness/
│   ├── HarnessAgentFactory.java        # 已存在，支持新 agent
│   ├── HarnessAgentService.java        # 已存在，路由已支持
│   └── WorkspaceInitializer.java       # 需添加新模板
├── agent/
│   └── AgentConfigService.java         # 添加 agent 配置
└── controller/
    └── ChatController.java             # 已支持 HARNESS 路由
```

### 5.2 agents.yml 配置

```yaml
- agentId: finance-intel-tracker
  category: intelligence
  type: HARNESS
  name: 金融情报追踪助手
  description: 自动采集金融行业动态，发现趋势信号，生成分析简报...
  modelName: qwen-max
  streaming: true
  enableThinking: false
  harnessConfig:
    workspace: "${user.home}/.agentscope/finance-intel-tracker"
    filesystemMode: LOCAL
    compaction:
      triggerMessages: 30
      keepMessages: 10
      flushBeforeCompact: true
    subagents:
      - name: intel-collector
        description: 情报采集员，使用 Web 搜索和 Shell 拉取数据
      - name: finance-trend-analyst
        description: 趋势分析师，对比历史数据发现趋势信号
      - name: intel-report-writer
        description: 报告撰写员，生成结构化情报简报
  samplePrompts:
    - "分析近期银行理财监管政策变化"
    - "关注保险资金运用新规动态"
    - "证券行业风控指引有什么更新"
```

### 5.3 工作区模板

**主模板位置**: `src/main/resources/harness-templates/finance-intel-tracker/`
**子 Agent 模板位置**: `src/main/resources/harness-templates/{subagent-name}/`

需要在 `WorkspaceInitializer.java` 中注册这些模板文件。

### 5.4 Shell 工具

Harness 应自动提供以下工具：
- `execute`: 执行 Shell 命令
- `read`, `write`, `edit`: 文件操作
- `ls`, `grep`, `glob`: 文件搜索

### 5.5 记忆维护

在 `HarnessAgentFactory` 中配置：
```java
.memoryFlush(true)
.compaction(triggerMessages, 10)
```

---

## 6. 测试要点

### 6.1 功能测试

| 测试项 | 验证点 |
|--------|--------|
| 基本流程 | 三个子 Agent 按序执行，报告生成 |
| 自我进化 | 多次同类查询后，skills/ 目录新增技能 |
| Shell 执行 | 验证 execute 工具调用和数据保存 |
| 双层记忆 | 验证 memory/ 和 MEMORY.md 的创建与合并 |

### 6.2 边界测试

| 场景 | 预期行为 |
|------|----------|
| 无搜索结果 | 降级到本地知识库 |
| Shell 失败 | 记录错误，返回部分结果 |
| 子 Agent 超时 | 超时后返回部分结果 |

---

## 7. 错误处理

| 场景 | 处理策略 |
|------|----------|
| 网络搜索失败 | 降级到本地知识库检索 |
| Shell 执行失败 | 记录错误，返回部分结果 |
| 子 Agent 超时 | 设置 timeout，返回部分结果 |
| 文件写入失败 | 记录日志，返回内存结果 |

---

## 8. 部署与运行

**运行命令**:
```bash
export DASHSCOPE_API_KEY=sk-xxx
mvn spring-boot:run
```

**访问**: http://localhost:8080

**工作区位置**: `~/.agentscope/finance-intel-tracker/`

---

## 9. 后续扩展：Builder 模式

从 Claw 扩展到 Builder，主要改动如下：

| 改动项 | Claw 当前 | Builder 需要 | 工作量 |
|--------|----------|-------------|--------|
| 认证 | 无 | JWT 登录/权限 | 新增 Spring Security + JWT |
| 文件系统 | LocalFilesystemWithShell | CompositeFilesystem + 命名空间 | 换 Filesystem 实现 |
| 工作区路径 | `~/.agentscope/finance-intel-tracker/` | `users/{userId}/agents/{agentId}/` | 路径重写（自动） |
| Shell 执行 | 宿主机 | 可选 Docker 容器 | 配置沙箱模式 |
| 存储 | 本地磁盘 | 可选 Redis/OSS | 换 BaseStore 实现 |

**关键点**：Agent 逻辑（AGENTS.md、skills、subagents）完全不用改，只是换了运行时容器。

---

## 附录：参考链接

- [AgentScope Java Harness 发布文章](https://mp.weixin.qq.com/s/aZhE0aPDaARxJLDI6ZrSFw)
- [agentscope-claw 示例](https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-examples/agents/agentscope-claw)
- [agentscope-builder 示例](https://github.com/agentscope-ai/agentscope-java/tree/main/agentscope-examples/agents/agentscope-builder)
