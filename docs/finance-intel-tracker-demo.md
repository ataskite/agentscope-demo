# 金融情报追踪助手演示

## 概述

基于 AgentScope Java 1.1.0-RC1 Harness 能力构建的金融情报追踪助手演示。展示工作区驱动的自我进化、子 Agent 委派、Shell 执行和双层记忆能力。

## 快速开始

### 1. 启动应用

```bash
export DASHSCOPE_API_KEY=sk-xxx
mvn spring-boot:run
```

### 2. 访问界面

http://localhost:8080

### 3. 选择 Agent

在 Agent 列表中选择"金融情报追踪助手"

## 示例对话

### 示例 1：监管政策分析

**用户输入**：
```
分析近期银行理财监管政策变化
```

**Agent 响应**：
1. 委派情报采集员搜索相关信息
2. 委派趋势分析师识别趋势信号
3. 委派报告撰写员生成简报
4. 返回报告摘要和文件路径

### 示例 2：市场动态追踪

**用户输入**：
```
关注保险资金运用新规动态
```

**Agent 响应**：
- 执行类似的情报分析流程
- 生成保险资金运用主题的报告

### 示例 3：文件上传分析

**操作**：上传行业报告 PDF 文件

**Agent 响应**：
- 解析文件内容
- 结合网络搜索补充信息
- 生成综合分析报告

## 工作区位置

```
~/.agentscope/finance-intel-tracker/
├── AGENTS.md                # 主 Agent 人格与委派规则
├── MEMORY.md                # 主 Agent 长期记忆
├── memory/                  # 每日记忆流水账
├── knowledge/               # 领域知识库
│   ├── finance-domains.md   # 金融子领域分类
│   └── data-sources.md      # 权威数据源列表
├── skills/                  # 可自我增长的技能目录
├── reports/                 # 生成的情报简报
├── subagents/               # 子 Agent 声明（可自动孵化）
└── agents/                  # 子 Agent 运行时数据
    ├── intel-collector/
    │   └── workspace/
    ├── finance-trend-analyst/
    │   └── workspace/
    └── intel-report-writer/
        └── workspace/
```

## 自我进化能力

### 技能自动学习

当 Agent 发现有效的分析模式时，会自动在 `skills/` 目录创建新技能。例如：
- 发现高效搜索关键词组合 → 创建 `skills/efficient-search-patterns/SKILL.md`
- 发现自定义信号检测逻辑 → 创建 `skills/custom-signals/SKILL.md`

### 子 Agent 自动孵化

当某类分析任务反复出现时，主 Agent 会创建新的子 Agent 声明。例如：
- 用户频繁询问汇率影响 → 创建 `subagents/forex-analyst.md`

### 双层记忆

- 每日记忆：`memory/YYYY-MM-DD.md`（短期流水账）
- 长期记忆：`MEMORY.md`（自动合并，跨日积累）

## 技术架构

```
用户输入
    ↓
主 Agent: finance-intel-tracker
    ↓ 委派
子 Agent 1: intel-collector（情报采集员）
    ↓ 搜索 + Shell 拉取
子 Agent 2: finance-trend-analyst（趋势分析师）
    ↓ 信号检测 + 历史对比
子 Agent 3: intel-report-writer（报告撰写员）
    ↓ 生成报告
返回用户
```

## 展示的 Harness 能力

| 能力 | 实现方式 |
|------|---------|
| 工作区驱动的自我进化 | AGENTS.md + skills/ + subagents/ 自动发现 |
| 子 Agent 委派与隔离 | 三个子 Agent 各自独立工作区 |
| Shell 执行能力 | intel-collector 使用 curl/python 拉取数据 |
| 双层记忆 | finance-trend-analyst 维护 MEMORY.md |
| 文件系统操作 | intel-report-writer 使用 write_file 生成报告 |

## 从 Claw 扩展到 Builder

当前使用 Claw（单人本机）模式。扩展到 Builder（多租户企业）只需：

1. 添加 JWT 认证
2. 换 LocalFilesystemWithShell → CompositeFilesystem
3. 配置命名空间隔离（自动路径重写）
4. Agent 逻辑（AGENTS.md、skills、subagents）完全不用改
