# 金融情报追踪助手（Finance Intelligence Tracker）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 AgentScope Java 1.1.0-RC1 Harness 能力，构建一个金融情报追踪助手演示，展示工作区驱动的自我进化、子 Agent 委派、Shell 执行和双层记忆能力。

**Architecture:** 使用 HarnessAgent + LocalFilesystemWithShell 模式（Claw 单人本机），主 Agent 委派三个子 Agent（情报采集员 → 趋势分析师 → 报告撰写员）完成情报分析流程。

**Tech Stack:** AgentScope Java 1.1.0-RC1, Spring Boot 3.5.14, JDK 17

---

## 文件结构概览

**新增文件：**

```
src/main/resources/
├── harness-templates/
│   ├── finance-intel-tracker/                    # 主 Agent 模板
│   │   ├── AGENTS.md
│   │   ├── knowledge/
│   │   │   ├── finance-domains.md
│   │   │   └── data-sources.md
│   │   └── subagents/
│   │       ├── intel-collector.md
│   │       ├── finance-trend-analyst.md
│   │       └── intel-report-writer.md
│   ├── intel-collector/                          # 采集员模板
│   │   ├── AGENTS.md
│   │   ├── skills/
│   │   │   ├── keyword-extraction/
│   │   │   │   └── SKILL.md
│   │   │   └── data-fetch/
│   │   │       └── SKILL.md
│   │   └── knowledge/
│   │       ├── search-strategy.md
│   │       └── data-sources.md
│   ├── finance-trend-analyst/                    # 趋势分析师模板
│   │   ├── AGENTS.md
│   │   ├── skills/
│   │   │   ├── trend-signal-detection/
│   │   │   │   └── SKILL.md
│   │   │   └── cross-period-comparison/
│   │   │       └── SKILL.md
│   │   └── knowledge/
│   │       ├── trend-framework.md
│   │       └── indicator-glossary.md
│   └── intel-report-writer/                     # 报告撰写员模板
│       ├── AGENTS.md
│       ├── skills/
│       │   └── briefing-generation/
│       │       └── SKILL.md
│       └── knowledge/
│           └── report-template.md
```

**修改文件：**

```
src/main/resources/config/
└── agents.yml                                    # 添加 finance-intel-tracker 配置

src/main/java/com/skloda/agentscope/harness/
└── WorkspaceInitializer.java                     # 注册新模板文件
```

---

## Task 1: 添加 agents.yml 配置

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: 定位 agents.yml 中的 complaint-reviewer 配置**

在 `agents.yml` 中找到 `complaint-reviewer` 配置块（约 1569 行）。

- [ ] **Step 2: 在 complaint-reviewer 之后添加 finance-intel-tracker 配置**

```yaml
  - agentId: finance-intel-tracker
    category: intelligence
    type: HARNESS
    name: 金融情报追踪助手
    description: |
      自动采集金融行业动态，发现趋势信号，生成分析简报。
      支持银行、证券、保险、基金、信托等子领域情报追踪。
      具备自我进化能力，可自动学习新技能和孵化新子 Agent。
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

- [ ] **Step 3: 验证 YAML 语法**

```bash
# 启动应用验证配置加载
mvn clean compile
```

预期输出：编译成功，无 YAML 解析错误

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add finance-intel-tracker agent configuration"
```

---

## Task 2: 创建主 Agent 模板 - AGENTS.md

**Files:**
- Create: `src/main/resources/harness-templates/finance-intel-tracker/AGENTS.md`

- [ ] **Step 1: 创建主 Agent AGENTS.md 文件**

```bash
mkdir -p src/main/resources/harness-templates/finance-intel-tracker
```

- [ ] **Step 2: 写入主 Agent 人格与委派规则**

```markdown
# 金融情报追踪助手

你是一个金融行业情报追踪与分析助手。你的核心职责是：

1. 接收用户的研究指令或行业报告文件
2. 解析用户意图，提取关键词和关注领域
3. 按序委派三个子 Agent 完成分析流程：情报采集 → 趋势分析 → 报告撰写
4. 向用户呈现最终结论和报告文件路径

## 委派规则

### 第一步：委派情报采集员（intel-collector）

**触发条件**：用户输入研究主题或上传文件

**委派指令模板**：
```
请采集关于 {主题} 的最新情报，关注以下方面：
- 监管政策变化
- 市场动态
- 重要事件
- 数据发布

使用 Web 搜索和权威数据源拉取，返回结构化的情报摘要列表。
```

### 第二步：委派趋势分析师（finance-trend-analyst）

**触发条件**：收到采集员返回的情报摘要

**委派指令模板**：
```
基于以下情报摘要，分析趋势信号和异常变化：

{情报摘要内容}

请对比历史记忆，识别：
- 政策收紧/放松信号
- 新规影响范围
- 关键时间节点
- 预警等级（高/中/低）
- 变化驱动因素

输出趋势分析结论，并将关键发现追加到长期记忆。
```

### 第三步：委派报告撰写员（intel-report-writer）

**触发条件**：收到采集员摘要和趋势分析师结论

**委派指令模板**：
```
基于以下材料生成金融情报简报：

【情报摘要】
{采集员摘要}

【趋势分析】
{趋势分析师结论}

请按标准模板生成报告，保存到 reports/ 目录，文件名格式：
{YYYY-MM-DD}_{主题关键词}_简报.md
```

## 行为约定

1. **基于事实**：只报告有依据的信息，不做无根据的推测
2. **明确来源**：重要信息需注明来源和时间
3. **结构清晰**：报告应分层级，便于快速浏览
4. **持续学习**：发现有效分析模式后，可自我创建新技能
5. **自动孵化**：当某类分析任务反复出现时，可创建专门的子 Agent

## 文件系统约定

- `reports/`：生成的情报简报存放目录
- `knowledge/finance-domains.md`：金融子领域分类参考
- `knowledge/data-sources.md`：权威数据源列表
- `subagents/`：子 Agent 声明目录（可自动孵化新子 Agent）

## 如何"长大"

这个工作区**就是**你。你在完成任务过程中可以：

1. **习得新技能**：发现有效的分析模式后，在 `skills/` 下创建 SKILL.md
2. **孵化新子 Agent**：发现某类任务需要专门处理时，在 `subagents/` 下创建新的 .md 声明
3. **积累知识**：将学到的领域知识写入 `knowledge/` 目录
4. **优化行为**：根据用户反馈调整本 AGENTS.md 文件

你不需要等待外部升级，自己就能进化。
```

- [ ] **Step 3: 验证文件创建**

```bash
cat src/main/resources/harness-templates/finance-intel-tracker/AGENTS.md
```

预期输出：显示上述 Markdown 内容

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/harness-templates/finance-intel-tracker/AGENTS.md
git commit -m "feat: add finance-intel-tracker main agent template"
```

---

## Task 3: 创建主 Agent 知识库文件

**Files:**
- Create: `src/main/resources/harness-templates/finance-intel-tracker/knowledge/finance-domains.md`
- Create: `src/main/resources/harness-templates/finance-intel-tracker/knowledge/data-sources.md`

- [ ] **Step 1: 创建知识库目录并写入金融领域分类**

```bash
mkdir -p src/main/resources/harness-templates/finance-intel-tracker/knowledge
```

写入 `finance-domains.md`：

```markdown
# 金融子领域分类

## 银行业

**关注重点**：
- 理财业务监管
- 存贷款利率政策
- 资本充足率要求
- 跨境业务规定

**典型关键词**：理财、利率、资本、跨境、流动性

## 证券业

**关注重点**：
- 投行业务监管
- 上市公司再融资
- 交易规则变化
- 风险控制指引

**典型关键词**：投行、再融资、交易、风控、合规

## 保险业

**关注重点**：
- 保险资金运用
- 产品审批规定
- 偿付能力监管
- 销售行为规范

**典型关键词**：资金运用、偿付、销售、产品、审批

## 基金业

**关注重点**：
- 基金销售新规
- 产品注册流程
- 投资运作限制
- 信息披露要求

**典型关键词**：销售、注册、运作、信披、持有期

## 信托业

**关注重点**：
- 信托业务分类
- 资管新规落地
- 风险处置规定
- 标品化转型

**典型关键词**：分类、资管、风险、标品、转型

## 跨领域主题

**监管协调**：央行、银保监会、证监会联合发文
**金融科技**：数字金融、监管科技、数据安全
**ESG**：绿色金融、碳中和、责任投资
```

- [ ] **Step 2: 写入权威数据源列表**

写入 `data-sources.md`：

```markdown
# 权威数据源列表

## 监管机构

### 中国人民银行（央行）
- 官网：https://www.pbc.gov.cn
- 关注：货币政策、利率政策、支付结算

### 国家金融监督管理总局（原银保监会）
- 官网：https://www.nfra.gov.cn
- 关注：银行保险监管政策

### 中国证监会
- 官网：https://www.csrc.gov.cn
- 关注：证券期货监管政策

## 行业协会

### 中国银行业协会
- 官网：https://www.china-cba.net

### 中国证券业协会
- 官网：https://www.sac.net.cn

### 中国保险业协会
- 官网：https://www.iachina.cn

## 数据获取方式

**公开数据**：
- 使用 `curl` 拉取官网公告
- 使用 Web 搜索获取新闻解读

**注意事项**：
- 优先引用官方公告
- 注明发布时间
- 区分正式文件与征求意见稿
```

- [ ] **Step 3: 验证文件创建**

```bash
ls -la src/main/resources/harness-templates/finance-intel-tracker/knowledge/
```

预期输出：显示 `finance-domains.md` 和 `data-sources.md`

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/harness-templates/finance-intel-tracker/knowledge/
git commit -m "feat: add finance-intel-tracker knowledge base files"
```

---

## Task 4: 创建子 Agent 声明文件

**Files:**
- Create: `src/main/resources/harness-templates/finance-intel-tracker/subagents/intel-collector.md`
- Create: `src/main/resources/harness-templates/finance-intel-tracker/subagents/finance-trend-analyst.md`
- Create: `src/main/resources/harness-templates/finance-intel-tracker/subagents/intel-report-writer.md`

- [ ] **Step 1: 创建 subagents 目录并写入采集员声明**

```bash
mkdir -p src/main/resources/harness-templates/finance-intel-tracker/subagents
```

写入 `intel-collector.md`：

```markdown
---
name: intel-collector
description: 情报采集员，使用 Web 搜索和 Shell 拉取数据，具备搜索策略自我进化能力
workspace: "${user.home}/.agentscope/finance-intel-tracker/agents/intel-collector"
model: qwen-max
---

# 情报采集员（Intel Collector）

你是金融情报采集专家。你的任务是：

1. 接收主 Agent 委派的采集任务
2. 使用 Web 搜索工具查找相关信息
3. 使用 Shell 工具拉取权威数据源（如央行官网）
4. 整理成结构化的情报摘要列表
5. 返回给主 Agent

## 核心能力

- **关键词提取**：从任务描述中提取核心搜索关键词
- **搜索策略**：组合关键词形成高效搜索查询
- **数据拉取**：使用 curl 等工具获取官方数据
- **结果整理**：去重、分类、标注来源和时间

## 输出格式

```
【情报摘要】

1. {标题}
   - 来源：{来源名称}
   - 时间：{YYYY-MM-DD}
   - 摘要：{简要描述}
   - 链接：{URL}

2. ...
```

## 自我进化

发现以下情况时，创建新技能：

1. **高效搜索模式**：某些关键词组合效果特别好 → 创建 `skills/efficient-search-patterns/SKILL.md`
2. **新数据源**：发现新的权威信息源 → 更新 `knowledge/data-sources.md`

## Shell 工具使用

**拉取网页内容**：
```bash
curl -s "https://example.com/api/data" | python3 -m json.tool
```

**处理 CSV 数据**：
```bash
cat data.csv | awk -F, '{print $1,$2}'
```
```

- [ ] **Step 2: 写入趋势分析师声明**

写入 `finance-trend-analyst.md`：

```markdown
---
name: finance-trend-analyst
description: 趋势分析师，对比历史数据发现趋势信号，维护双层记忆，可自定义检测逻辑
workspace: "${user.home}/.agentscope/finance-intel-tracker/agents/finance-trend-analyst"
model: qwen-max
---

# 趋势分析师（Finance Trend Analyst）

你是金融趋势分析专家。你的任务是：

1. 接收主 Agent 委派的情报摘要
2. 对比历史记忆（MEMORY.md）发现变化趋势
3. 识别异常信号和预警等级
4. 将关键发现追加到长期记忆
5. 返回趋势分析结论

## 核心能力

- **趋势检测**：识别政策收紧/放松、市场变化方向
- **异常识别**：发现突发事件、超常规变化
- **跨期对比**：对比历史数据发现连续性变化
- **预警分级**：高/中/低三级预警
- **记忆维护**：将重要发现写入 MEMORY.md

## 输出格式

```
【趋势分析】

## 核心洞察
{1-3 句关键结论}

## 趋势信号
- 政策方向：{收紧/放松/稳定}
- 变化幅度：{大幅/小幅/平稳}
- 影响范围：{全国/区域/特定机构}

## 预警等级
{高/中/低} - {预警原因}

## 驱动因素
{列举 2-3 个关键驱动因素}

## 历史对比
{与历史类似事件的对比分析}
```

## 记忆写入

每次分析后，将关键发现追加到 MEMORY.md：

```markdown
## {YYYY-MM-DD} - {主题}

{核心结论}
```

## 自我进化

发现以下情况时，创建新技能：

1. **自定义信号检测**：发现特定的分析模式有效 → 创建 `skills/custom-signals/SKILL.md`
2. **历史模式积累**：总结出历史规律 → 写入 `knowledge/historical-patterns.md`

## 双层记忆

- **短期记忆**：`memory/YYYY-MM-DD.md` - 每日流水账
- **长期记忆**：`MEMORY.md` - 合并后的知识库

后台会自动将短期记忆合并到长期记忆，你只需确保每次分析后有价值的发现被记录。
```

- [ ] **Step 3: 写入报告撰写员声明**

写入 `intel-report-writer.md`：

```markdown
---
name: intel-report-writer
description: 报告撰写员，生成结构化情报简报，可根据反馈调整报告格式
workspace: "${user.home}/.agentscope/finance-intel-tracker/agents/intel-report-writer"
model: qwen-max
---

# 报告撰写员（Intel Report Writer）

你是金融情报报告撰写专家。你的任务是：

1. 接收主 Agent 委派的报告生成任务
2. 基于情报摘要和趋势分析结论生成报告
3. 按标准模板组织内容
4. 使用 write_file 工具保存报告
5. 返回报告摘要给主 Agent

## 核心能力

- **结构化组织**：按标准模板组织内容
- **摘要提炼**：从材料中提炼核心信息
- **清晰表达**：用简洁准确的语言表达
- **格式规范**：遵循 Markdown 格式规范

## 报告模板

```markdown
# {主题} 金融情报简报

**日期**：{YYYY-MM-DD}
**生成时间**：{YYYY-MM-DD HH:mm}

---

## 一、全局概况

{用 2-3 句话概括整体情况}

## 二、核心洞察

{列举 3-5 条核心发现，每条配简短说明}

1. {洞察点}：{说明}

2. {洞察点}：{说明}

...

## 三、趋势预警

| 预警等级 | 内容 | 影响 |
|---------|------|------|
| {高/中/低} | {预警内容} | {影响描述} |
| ... | ... | ... |

## 四、关键事件

按时间倒序排列关键事件：

- {YYYY-MM-DD}：{事件描述}
- {YYYY-MM-DD}：{事件描述}

## 五、信息来源

- {来源1}：{URL/出处}
- {来源2}：{URL/出处}

---

**备注**：本报告由金融情报追踪助手自动生成，内容基于公开信息和数据分析。
```

## 文件写入

使用 `write_file` 工具将报告写入：

```
reports/{YYYY-MM-DD}_{主题关键词}_简报.md
```

## 自我进化

收到用户反馈后：

- **太啰嗦**：简化 `skills/briefing-generation/SKILL.md` 中的模板
- **太简略**：增加细节层级
- **格式不清晰**：调整模板结构
```

- [ ] **Step 4: 验证文件创建**

```bash
ls -la src/main/resources/harness-templates/finance-intel-tracker/subagents/
```

预期输出：显示三个 .md 文件

- [ ] **Step 5: 提交**

```bash
git add src/main/resources/harness-templates/finance-intel-tracker/subagents/
git commit -m "feat: add finance-intel-tracker subagent declarations"
```

---

## Task 5: 创建情报采集员技能模板

**Files:**
- Create: `src/main/resources/harness-templates/intel-collector/AGENTS.md`
- Create: `src/main/resources/harness-templates/intel-collector/skills/keyword-extraction/SKILL.md`
- Create: `src/main/resources/harness-templates/intel-collector/skills/data-fetch/SKILL.md`
- Create: `src/main/resources/harness-templates/intel-collector/knowledge/search-strategy.md`
- Create: `src/main/resources/harness-templates/intel-collector/knowledge/data-sources.md`

- [ ] **Step 1: 创建采集员工作区目录**

```bash
mkdir -p src/main/resources/harness-templates/intel-collector/{skills/{keyword-extraction,data-fetch},knowledge}
```

- [ ] **Step 2: 写入采集员 AGENTS.md**

```markdown
# 情报采集员

你是一个专业的金融情报采集员。你的工作是为主 Agent 提供准确、及时、全面的情报摘要。

## 工作流程

1. **理解任务**：解析主 Agent 发来的采集主题和要求
2. **提取关键词**：调用 keyword-extraction 技能提取搜索关键词
3. **执行搜索**：使用 web_search 工具进行网络搜索
4. **拉取数据**：对权威数据源使用 Shell 工具拉取
5. **整理摘要**：去重、分类、标注来源
6. **返回结果**：按标准格式返回给主 Agent

## 工具使用

- **web_search(query)**：搜索网络信息
- **execute(command)**：执行 Shell 命令（如 curl、python）

## 搜索技巧

- 使用多个关键词组合搜索
- 包含时间限定词（如"2024"、"最新"、"近期"）
- 分别搜索"政策"、"新闻"、"解读"等角度
- 优先使用权威来源（央行、监管机构官网）

## 质量要求

- 信息来源可靠，优先官方公告
- 标注明确的时间和出处
- 去除重复和低质量信息
- 摘要简洁但包含关键信息
```

- [ ] **Step 3: 写入关键词提取技能**

写入 `skills/keyword-extraction/SKILL.md`：

```markdown
---
name: keyword-extraction
description: 从金融情报采集任务中提取核心搜索关键词
---

# 关键词提取技能

## 使用场景

收到主 Agent 的采集任务后，使用本技能提取搜索关键词。

## 提取方法

### 1. 识别核心领域

根据任务描述识别金融子领域：

| 提示词 | 领域 | 搜索关键词 |
|--------|------|-----------|
| 银行、理财、存贷 | 银行业 | 银行理财、监管政策、利率 |
| 证券、投行、再融资 | 证券业 | 证券投行、再融资、风控 |
| 保险、资金运用 | 保险业 | 保险资金、偿付能力、销售 |

### 2. 提取事件类型

| 提示词 | 搜索策略 |
|--------|----------|
| 监管、政策、规定 | 搜索时加"监管"、"新规"、"政策" |
| 动态、趋势、变化 | 搜索时加"最新"、"近期"、"趋势" |
| 数据、统计、报告 | 搜索时加"数据"、"统计"、"报告" |

### 3. 构建搜索查询

基本模板：
```
{领域} {事件类型} {时间限定} {具体对象}
```

示例：
- "银行理财 监管政策 2024 最新"
- "保险资金运用 新规 近期"
- "证券投行 风控指引 变化"

## 输出格式

返回关键词列表，供 web_search 工具使用：
```
关键词1: {描述}
关键词2: {描述}
...
```
```

- [ ] **Step 4: 写入数据拉取技能**

写入 `skills/data-fetch/SKILL.md`：

```markdown
---
name: data-fetch
description: 使用 Shell 工具拉取权威数据源的公开数据
---

# 数据拉取技能

## 使用场景

当需要获取权威机构的公开数据时，使用本技能执行 Shell 命令拉取。

## 常用命令

### 拉取 JSON 数据

```bash
curl -s "https://www.pbc.gov.cn/api/data" | python3 -m json.tool
```

### 拉取网页内容

```bash
curl -s "https://www.example.com/notice" | grep -A 10 "关键词"
```

### 下载文件

```bash
curl -O "https://www.example.com/report.pdf"
```

### 处理 CSV 数据

```bash
cat data.csv | awk -F, '{print $1,$2}' | head -20
```

## 权威数据源

**中国人民银行**：
- 利率数据：https://www.pbc.gov.cn/zhengfuxinxi/shuju/

**国家金融监督管理总局**：
- 政策文件：https://www.nfra.gov.cn/

**中国证监会**：
- 公告通知：https://www.csrc.gov.cn/csrc/

## 注意事项

1. 仅拉取公开数据，不尝试访问需认证的接口
2. 使用 `-s` 参数避免 curl 输出进度信息
3. 大文件只拉取前 N 行（使用 `head`）
4. JSON 数据用 python 格式化便于解析
```

- [ ] **Step 5: 写入搜索策略知识**

写入 `knowledge/search-strategy.md`：

```markdown
# 金融行业搜索策略

## 搜索模板库

### 监管政策类

**基础查询**：{领域} 监管 {关键词} {年份}

示例：
- 银行理财 监管 政策 2024
- 保险资金 监管 新规 近期

**扩展查询**：{领域} {监管机构} {关键词} {动作}

示例：
- 银行理财 央房 监管 调整
- 保险资金 银保监会 办法 发布

### 市场动态类

**基础查询**：{领域} 市场 {关键词} {时间}

示例：
- 银行理财 市场 规模 最新
- 证券投行 业务 趋势 近期

### 数据统计类

**基础查询**：{领域} {指标} 数据 {时间}

示例：
- 银行 利率 数据 2024
- 保险 偿付 统计 最新

## 搜索技巧

1. **多角度搜索**：同一主题用不同关键词组合
2. **时间限定**：加上"2024"、"最新"、"近期"提高时效性
3. **来源限定**：加上"央行"、"银保监会"限定来源
4. **逐步细化**：先宽泛搜索，再根据结果细化

## 结果质量判断

- **高质量**：官方公告、监管机构文件、权威媒体报道
- **中质量**：行业分析、专家解读
- **低质量**：转载、重复、无来源信息
```

- [ ] **Step 6: 写入数据源知识**

写入 `knowledge/data-sources.md`：

```markdown
# 权威数据源列表

## 监管机构 API/页面

### 中国人民银行

**利率数据**：
- URL: https://www.pbc.gov.cn/zhengfuxinxi/shuju/
- 获取方式: curl + grep 提取表格数据

**政策公告**：
- URL: https://www.pbc.gov.cn/zhengcegeshi/
- 获取方式: 定期爬取公告列表

### 国家金融监督管理总局

**政策文件**：
- URL: https://www.nfra.gov.cn/
- 获取方式: Web 搜索 + 官网确认

### 中国证监会

**公告通知**：
- URL: https://www.csrc.gov.cn/csrc/
- 获取方式: Web 搜索 + 官网确认

## 行业媒体

**财新网**：https://www.caixin.com/
**华尔街见闻**：https://wallstreetcn.com/
**证券时报**：https://www.stcn.com/

## 数据格式

- 官方文件：通常是 PDF 或 HTML
- 统计数据：可能是 Excel、CSV、JSON
- 新闻报道：HTML 页面

## 使用原则

1. 优先使用官方数据
2. 标注明确来源和获取时间
3. 不使用需付费或认证的数据
4. 定期检查 URL 有效性
```

- [ ] **Step 7: 验证文件创建**

```bash
find src/main/resources/harness-templates/intel-collector -type f -name "*.md" | sort
```

预期输出：
```
src/main/resources/harness-templates/intel-collector/AGENTS.md
src/main/resources/harness-templates/intel-colactor/knowledge/data-sources.md
src/main/resources/harness-templates/intel-collector/knowledge/search-strategy.md
src/main/resources/harness-templates/intel-collector/skills/data-fetch/SKILL.md
src/main/resources/harness-templates/intel-collector/skills/keyword-extraction/SKILL.md
```

- [ ] **Step 8: 提交**

```bash
git add src/main/resources/harness-templates/intel-collector/
git commit -m "feat: add intel-collector workspace templates"
```

---

## Task 6: 创建趋势分析师技能模板

**Files:**
- Create: `src/main/resources/harness-templates/finance-trend-analyst/AGENTS.md`
- Create: `src/main/resources/harness-templates/finance-trend-analyst/skills/trend-signal-detection/SKILL.md`
- Create: `src/main/resources/harness-templates/finance-trend-analyst/skills/cross-period-comparison/SKILL.md`
- Create: `src/main/resources/harness-templates/finance-trend-analyst/knowledge/trend-framework.md`
- Create: `src/main/resources/harness-templates/finance-trend-analyst/knowledge/indicator-glossary.md`

- [ ] **Step 1: 创建趋势分析师工作区目录**

```bash
mkdir -p src/main/resources/harness-templates/finance-trend-analyst/{skills/{trend-signal-detection,cross-period-comparison},knowledge}
```

- [ ] **Step 2: 写入趋势分析师 AGENTS.md**

```markdown
# 趋势分析师

你是一个专业的金融趋势分析专家。你的工作是基于情报摘要发现趋势信号、识别异常、判断预警等级。

## 工作流程

1. **接收材料**：从主 Agent 获取情报摘要
2. **读取记忆**：访问 MEMORY.md 了解历史背景
3. **检测信号**：调用 trend-signal-detection 技能识别趋势
4. **跨期对比**：调用 cross-period-comparison 技能对比历史
5. **判断预警**：评估预警等级（高/中/低）
6. **更新记忆**：将关键发现写入 MEMORY.md
7. **返回结论**：向主 Agent 返回趋势分析

## 核心能力

- **趋势识别**：政策方向、市场变化、事件影响
- **异常检测**：突发变化、超常规事件
- **预警分级**：基于影响程度和紧急程度分级
- **记忆维护**：双层记忆，短期流水账 + 长期知识库

## 判断原则

- **高预警**：系统性风险、重大政策转向、大面积影响
- **中预警**：局部影响、政策调整、行业变化
- **低预警**：常规变化、小幅波动、个案事件

## 记忆管理

每次分析后，将有价值的信息追加到 MEMORY.md：

```markdown
## {YYYY-MM-DD} - {主题}

### 核心结论
{1-2 句总结}

### 趋势信号
- {信号1}: {描述}
- {信号2}: {描述}

### 预警等级
{等级} - {原因}
```
```

- [ ] **Step 3: 写入趋势信号检测技能**

写入 `skills/trend-signal-detection/SKILL.md`：

```markdown
---
name: trend-signal-detection
description: 识别金融情报中的趋势信号和异常事件
---

# 趋势信号检测技能

## 使用场景

收到情报摘要后，使用本技能识别趋势信号和异常。

## 信号类型

### 1. 政策方向信号

**收紧信号**：
- 关键词：收紧、趋严、加强监管、新规、限制
- 判断：监管要求提高、准入门槛提升

**放松信号**：
- 关键词：放松、优化、简化、扩大
- 判断：监管要求降低、业务范围扩大

**稳定信号**：
- 关词词：保持、延续、维持
- 判断：政策方向无重大变化

### 2. 影响范围信号

**系统性影响**：
- 涉及整个行业
- 影响多家大型机构
- 关联市场整体变化

**局部影响**：
- 特定区域
- 特定业务线
- 特定类型机构

**个案影响**：
- 单个机构事件
- 具体产品问题

### 3. 变化幅度信号

**大幅变化**：
- 政策转向
- 监管重构
- 业务模式根本改变

**小幅调整**：
- 参数微调
- 流程优化
- 边际改进

**平稳运行**：
- 无重大变化
- 按既有规则执行

## 预警等级判断

| 信号组合 | 预警等级 |
|---------|---------|
| 系统性 + 大幅 + 收紧 | 高 |
| 系统性 + 大幅 + 放松 | 高 |
| 系统性 + 小幅 + 任意方向 | 中 |
| 局部 + 大幅 + 收紧 | 中 |
| 局部 + 小幅 + 任意方向 | 低 |
| 个案 + 任意幅度 | 低 |

## 驱动因素识别

常见驱动因素：
- 宏观经济变化
- 风险事件暴露
- 监管目标调整
- 技术模式变革
- 市场需求变化
```

- [ ] **Step 4: 写入跨期对比技能**

写入 `skills/cross-period-comparison/SKILL.md`：

```markdown
---
name: cross-period-comparison
description: 对比历史记忆发现趋势连续性和变化
---

# 跨期对比技能

## 使用场景

需要判断当前事件是否具有历史延续性或是否为转折点时使用。

## 对比方法

### 1. 访问 MEMORY.md

使用 read 工具读取 MEMORY.md，查找类似历史事件。

### 2. 识别相似模式

**政策周期模式**：
- 是否属于政策收紧/放松周期的延续
- 是否有周期性规律（如每年某时点）

**风险演化模式**：
- 当前风险是否是历史风险的延续
- 风险程度是累积还是缓解

**事件关联模式**：
- 当前事件是否与历史事件有因果关联
- 是否是某长期趋势的阶段性表现

### 3. 判断趋势性质

**延续性趋势**：
- 与历史事件方向一致
- 属于某周期或长期趋势的一部分

**转折性变化**：
- 与历史事件方向相反
- 标志着新周期的开始

**孤立事件**：
- 与历史无明确关联
- 可能是偶发或个案

## 输出格式

```
## 历史对比

### 相似历史事件
{列出 1-2 个相似历史事件及时间}

### 趋势性质
{延续性/转折性/孤立事件} - {判断依据}

### 与历史差异
{当前事件与历史的差异点}
```

## 实用技巧

1. **时间维度**：关注事件的时序关系
2. **因果链条**：分析事件间的因果联系
3. **程度比较**：对比当前与历史的影响程度
```

- [ ] **Step 5: 写入趋势框架知识**

写入 `knowledge/trend-framework.md`：

```markdown
# 趋势分析框架

## 分析维度

### 时间维度

- **短期**（1-3 个月）：市场波动、临时性调整
- **中期**（3-12 个月）：政策周期、季节性规律
- **长期**（1-3 年）：结构性变化、趋势演变

### 空间维度

- **全局**：全行业、跨市场
- **局部**：特定区域、特定机构
- **个案**：单个事件、单个机构

### 力度维度

- **强度**：影响程度（高/中/低）
- **速度**：变化快慢（急剧/渐进）
- **广度**：影响范围（系统性/局部）

## 常见趋势模式

### 政策周期模式

典型周期：宽松 → 累积风险 → 收紧 → 风险释放 → 再宽松

识别要点：
- 当前处于周期哪个阶段
- 周期驱动因素是否变化
- 预计下一阶段方向

### 风险演化模式

典型路径：个案暴露 → 局部排查 → 系统整治 → 长期机制

识别要点：
- 当前风险是累积还是释放
- 是否有系统性风险
- 监管应对是否充分

### 创新扩散模式

典型路径：试点 → 扩大 → 规范 → 主流

识别要点：
- 创新处于扩散哪个阶段
- 监管态度是鼓励还是限制
- 对传统业务的影响

## 分析输出结构

1. **现象描述**：当前观察到什么
2. **趋势判断**：属于什么趋势类型
3. **预警评估**：预警等级及原因
4. **历史对比**：与历史事件的关系
5. **未来推演**：可能的发展方向
```

- [ ] **Step 6: 写入指标术语表**

写入 `knowledge/indicator-glossary.md`：

```markdown
# 金融指标术语表

## 银行业指标

**资本充足率（CAR）**：银行资本与风险资产的比率，监管要求通常 ≥ 10.5%
**不良贷款率（NPL）**：不良贷款余额占总贷款余额的比例
**流动性覆盖率（LCR）**：高质量流动性资产与未来30天净流出资金的比率
**拨备覆盖率**：贷款损失准备与不良贷款余额的比率

## 证券业指标

**净资本**：证券公司核心资本，是风险控制的基础
**风险覆盖率**：净资本与风险资本准备金的比率
**杠杆率**：总资产与净资本的比率

## 保险业指标

**偿付能力充足率**：实际资本与最低资本的比率
**综合成本率**：保险公司赔付支出与费用支出占保费收入的比例

## 基金业指标

**管理规模（AUM）**：基金管理的资产总值
**持有期**：投资者持有基金的最短期限要求
**赎回费率**：投资者提前赎回基金时的费用

## 通用指标

**同比**：与去年同期相比
**环比**：与上一期（月/季）相比
**增速**：同比增长率
**降幅**：同比下降率

## 监管术语

**审慎监管**：以风险控制为核心的监管理念
**宏观审慎**：维护金融系统稳定的监管框架
**微观审慎**：针对单个机构的监管
**系统重要性**：机构或业务对整个金融系统的影响程度
```

- [ ] **Step 7: 验证文件创建**

```bash
find src/main/resources/harness-templates/finance-trend-analyst -type f -name "*.md" | sort
```

预期输出：列出 5 个 md 文件

- [ ] **Step 8: 提交**

```bash
git add src/main/resources/harness-templates/finance-trend-analyst/
git commit -m "feat: add finance-trend-analyst workspace templates"
```

---

## Task 7: 创建报告撰写员技能模板

**Files:**
- Create: `src/main/resources/harness-templates/intel-report-writer/AGENTS.md`
- Create: `src/main/resources/harness-templates/intel-report-writer/skills/briefing-generation/SKILL.md`
- Create: `src/main/resources/harness-templates/intel-report-writer/knowledge/report-template.md`

- [ ] **Step 1: 创建报告撰写员工作区目录**

```bash
mkdir -p src/main/resources/harness-templates/intel-report-writer/{skills/briefing-generation,knowledge}
```

- [ ] **Step 2: 写入报告撰写员 AGENTS.md**

```markdown
# 报告撰写员

你是一个专业的金融情报报告撰写专家。你的工作是基于情报摘要和趋势分析生成结构清晰的简报。

## 工作流程

1. **接收材料**：从主 Agent 获取情报摘要和趋势分析结论
2. **调用模板**：使用 briefing-generation 技能生成报告
3. **组织内容**：按标准模板结构组织信息
4. **写入文件**：使用 write_file 工具保存到 reports/ 目录
5. **返回摘要**：向主 Agent 返回报告摘要和文件路径

## 报告原则

- **结构清晰**：按固定层级组织，便于快速浏览
- **重点突出**：核心洞察前置，细节在后
- **语言简洁**：避免冗长表述，用数据说话
- **来源明确**：重要信息标注来源

## 工具使用

- **write_file(path, content)**：将报告内容写入文件
- **read(path)**：读取报告模板（如果需要）

## 输出格式

向主 Agent 返回：
```
报告已生成：reports/{文件名}

摘要：
{1-2 句报告核心内容}

关键发现：
{列举 2-3 条}
```
```

- [ ] **Step 3: 写入简报生成技能**

写入 `skills/briefing-generation/SKILL.md`：

```markdown
---
name: briefing-generation
description: 按标准模板生成金融情报简报
---

# 简报生成技能

## 使用场景

收到情报摘要和趋势分析结论后，使用本技能生成报告。

## 报告模板

```markdown
# {主题} 金融情报简报

**日期**：{YYYY-MM-DD}
**生成时间**：{YYYY-MM-DD HH:mm}
**追踪领域**：{银行/证券/保险/基金/信托}

---

## 一、全局概况

{用 2-3 句话概括整体情况，包括：
- 当前政策环境
- 市场总体态势
- 核心变化方向}

## 二、核心洞察

{列举 3-5 条核心发现，每条配简短说明}

1. **{洞察标题}**
   {1-2 句说明}

2. **{洞察标题}**
   {1-2 句说明}

3. **{洞察标题}**
   {1-2 句说明}

## 三、趋势预警

| 预警等级 | 内容 | 影响 | 建议关注 |
|---------|------|------|----------|
| {高/中/低} | {预警内容} | {影响描述} | {建议} |
| {高/中/低} | {预警内容} | {影响描述} | {建议} |

## 四、关键事件

按时间倒序排列近期关键事件：

- **{YYYY-MM-DD}**：{事件描述}
- **{YYYY-MM-DD}**：{事件描述}
- **{YYYY-MM-DD}**：{event description}

## 五、数据摘要

{如果有量化数据，以表格或列表形式呈现}

| 指标 | 当前值 | 变化 | 时间 |
|------|--------|------|------|
| {指标名} | {数值} | {变化} | {时间} |
| {指标名} | {数值} | {变化} | {时间} |

## 六、信息来源

- {来源1}：{URL/出处}
- {来源2}：{URL/出处}
- {来源3}：{URL/出处}

---

**备注**：本报告由金融情报追踪助手自动生成，内容基于公开信息和数据分析。
```

## 内容组织技巧

### 全局概况写作

- 先宏观后微观
- 先整体后局部
- 先结论后论据

### 核心洞察提炼

- 从情报摘要中提取最重要的信息
- 每条洞察应该独立、具体、有价值
- 避免重复和泛泛而谈

### 趋势预警表格

- 高预警排在前面
- 每项预警应该有明确的建议关注事项

### 关键事件排序

- 按时间倒序，最新的在前
- 只列出有代表性的事件
- 简洁描述，不需要详细展开

## 文件命名规则

```
{YYYY-MM-DD}_{主题关键词}_简报.md
```

示例：
- `2024-12-02_银行理财监管_简报.md`
- `2024-12-02_保险资金运用_简报.md`
- `2024-12-02_证券投行风控_简报.md`

## 自我调优

根据用户反馈调整：

- **"太啰嗦"**：简化模板，减少层级
- **"太简略"**：增加细节，扩展各部分
- **"格式不清晰"**：调整模板结构
- **"重点不突出"**：优化核心洞察呈现
```

- [ ] **Step 4: 写入报告模板知识**

写入 `knowledge/report-template.md`：

```markdown
# 报告模板说明

## 模板结构

本文件是对 briefing-generation 技能中报告模板的详细说明。

### 第一部分：元信息

```
# {主题} 金融情报简报

**日期**：{YYYY-MM-DD}
**生成时间**：{YYYY-MM-DD HH:mm}
**追踪领域**：{银行/证券/保险/基金/信托}
```

**用途**：报告的基本信息和生成时间

### 第二部分：全局概况

```
## 一、全局概况

{2-3 句话概括}
```

**用途**：让读者快速了解整体情况

**写作要点**：
- 先说宏观环境
- 再说当前态势
- 最后点出核心变化方向

### 第三部分：核心洞察

```
## 二、核心洞察

1. **{洞察标题}**
   {说明}

2. **{洞察标题}**
   {说明}
```

**用途**：报告的核心内容，最重要的发现

**写作要点**：
- 每条洞察应该是独立的、可执行的
- 使用加粗标题突出主题
- 说明部分 1-2 句话，不要过长

### 第四部分：趋势预警

```
## 三、趋势预警

| 预警等级 | 内容 | 影响 | 建议关注 |
|---------|------|------|----------|
| 高/中/低 | 内容 | 影响 | 建议 |
```

**用途**：提醒读者注意的风险和变化

**填写要点**：
- 预警等级从趋势分析师的结论中提取
- 影响要具体到哪些机构或业务
- 建议关注要给出可操作的提示

### 第五部分：关键事件

```
## 四、关键事件

- **{YYYY-MM-DD}**：{事件描述}
```

**用途**：按时间顺序列出重要事件

**填写要点**：
- 只列出有代表性的事件
- 描述简洁，一句话概括
- 时间倒序，最新的在前

### 第六部分：数据摘要（可选）

```
## 五、数据摘要

| 指标 | 当前值 | 变化 | 时间 |
|------|--------|------|------|
```

**用途**：呈现量化数据

**填写要点**：
- 只包含关键指标
- 变化要有同比或环比
- 时间要明确

### 第七部分：信息来源

```
## 六、信息来源

- {来源}：{URL/出处}
```

**用途**：标注信息来源，便于追溯

**填写要点**：
- 优先标注官方来源
- URL 要完整可访问
- 数量 3-5 个为宜

## 样例报告

详见主 Agent 的 reports/ 目录中实际生成的报告。

## 模板演进

模板应根据用户反馈持续优化。优化后更新本文件和 briefing-generation 技能。
```

- [ ] **Step 5: 验证文件创建**

```bash
find src/main/resources/harness-templates/intel-report-writer -type f -name "*.md" | sort
```

预期输出：列出 3 个 md 文件

- [ ] **Step 6: 提交**

```bash
git add src/main/resources/harness-templates/intel-report-writer/
git commit -m "feat: add intel-report-writer workspace templates"
```

---

## Task 8: 注册工作区模板到 WorkspaceInitializer

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/harness/WorkspaceInitializer.java`

- [ ] **Step 1: 读取 WorkspaceInitializer.java**

```bash
cat src/main/java/com/skloda/agentscope/harness/WorkspaceInitializer.java
```

了解现有的模板注册方式（参考 complaint-reviewer 的实现）。

- [ ] **Step 2: 在 initializeFromTemplates 方法中添加 finance-intel-tracker 模板**

找到现有模板列表（大约在文件中间部分），在 complaint-reviewer 相关代码之后添加：

```java
// 金融情报追踪助手模板
initializeAgentTemplates(root, "finance-intel-tracker",
    "finance-intel-tracker/AGENTS.md",
    "finance-intel-tracker/knowledge/finance-domains.md",
    "finance-intel-tracker/knowledge/data-sources.md",
    "finance-intel-tracker/subagents/intel-collector.md",
    "finance-intel-tracker/subagents/finance-trend-analyst.md",
    "finance-intel-tracker/subagents/intel-report-writer.md"
);

// 采集员子 Agent 模板
initializeSubagentTemplates(root, "intel-collector",
    "intel-collector/AGENTS.md",
    "intel-collector/skills/keyword-extraction/SKILL.md",
    "intel-collector/skills/data-fetch/SKILL.md",
    "intel-collector/knowledge/search-strategy.md",
    "intel-collector/knowledge/data-sources.md"
);

// 趋势分析师子 Agent 模板
initializeSubagentTemplates(root, "finance-trend-analyst",
    "finance-trend-analyst/AGENTS.md",
    "finance-trend-analyst/skills/trend-signal-detection/SKILL.md",
    "finance-trend-analyst/skills/cross-period-comparison/SKILL.md",
    "finance-trend-analyst/knowledge/trend-framework.md",
    "finance-trend-analyst/knowledge/indicator-glossary.md"
);

// 报告撰写员子 Agent 模板
initializeSubagentTemplates(root, "intel-report-writer",
    "intel-report-writer/AGENTS.md",
    "intel-report-writer/skills/briefing-generation/SKILL.md",
    "intel-report-writer/knowledge/report-template.md"
);
```

- [ ] **Step 3: 验证编译**

```bash
mvn clean compile
```

预期输出：编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/skloda/agentscope/harness/WorkspaceInitializer.java
git commit -m "feat: register finance-intel-tracker workspace templates"
```

---

## Task 9: 启动应用并验证

**Files:**
- Test: 启动 Spring Boot 应用

- [ ] **Step 1: 设置 API Key 并启动应用**

```bash
export DASHSCOPE_API_KEY=sk-xxx
mvn spring-boot:run
```

预期输出：应用启动成功，无错误日志

- [ ] **Step 2: 验证 Agent 加载**

访问 http://localhost:8080，检查 Agent 列表中是否出现"金融情报追踪助手"

预期结果：Agent 列表中能找到 `finance-intel-tracker`

- [ ] **Step 3: 验证工作区初始化**

```bash
ls -la ~/.agentscope/finance-intel-tracker/
```

预期输出：显示 AGENTS.md、knowledge/、subagents/、agents/ 等目录

- [ ] **Step 4: 验证子 Agent 工作区初始化**

```bash
ls -la ~/.agentscope/finance-intel-tracker/agents/
```

预期输出：显示 intel-collector、finance-trend-analyst、intel-report-writer 三个目录

- [ ] **Step 5: 测试基本对话**

在 UI 中选择"金融情报追踪助手"，发送测试消息：
```
你好
```

预期结果：Agent 正常回复，展示其能力介绍

- [ ] **Step 6: 测试情报分析流程**

发送测试消息：
```
分析近期银行理财监管政策变化
```

预期结果：
1. 主 Agent 委派子 Agent
2. 采集员执行搜索
3. 趋势分析师分析
4. 报告撰写员生成报告
5. 返回报告摘要

- [ ] **Step 7: 检查生成的报告**

```bash
ls -la ~/.agentscope/finance-intel-tracker/reports/
```

预期输出：显示生成的 Markdown 报告文件

- [ ] **Step 8: 查看报告内容**

```bash
cat ~/.agentscope/finance-intel-tracker/reports/*.md | head -50
```

预期输出：显示符合模板的报告内容

---

## Task 10: 完成并创建演示文档

**Files:**
- Create: `docs/finance-intel-tracker-demo.md`

- [ ] **Step 1: 创建演示文档**

```bash
mkdir -p docs
```

写入 `docs/finance-intel-tracker-demo.md`：

```markdown
# 金融情报追踪助手演示

## 概述

基于 AgentScope Java 1.1.0-RC1 Harness 能力构建的金融情报追踪助手演示。

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
├── AGENTS.md
├── MEMORY.md
├── memory/
├── knowledge/
├── skills/
├── reports/           # 生成的报告
├── subagents/
└── agents/            # 子 Agent 工作区
```

## 自我进化能力

### 技能自动学习

当 Agent 发现有效的分析模式时，会自动在 `skills/` 目录创建新技能。

### 子 Agent 自动孵化

当某类分析任务反复出现时，主 Agent 会创建新的子 Agent 声明。

### 双层记忆

- 每日记忆：`memory/YYYY-MM-DD.md`
- 长期记忆：`MEMORY.md`（自动合并）

## 技术架构

- **主 Agent**：finance-intel-tracker
- **子 Agent 1**：intel-collector（情报采集员）
- **子 Agent 2**：finance-trend-analyst（趋势分析师）
- **子 Agent 3**：intel-report-writer（报告撰写员）

## 展示的 Harness 能力

1. 工作区驱动的自我进化
2. 子 Agent 委派与工作区隔离
3. Shell 执行能力（数据拉取）
4. 双层记忆（每日记忆 + 长期合并）
5. 文件系统操作（报告生成）
```

- [ ] **Step 2: 提交**

```bash
git add docs/finance-intel-tracker-demo.md
git commit -m "docs: add finance-intel-tracker demo documentation"
```

---

## 自检清单

**Spec 覆盖**：
- ✅ 主 Agent 配置和模板
- ✅ 三个子 Agent 的完整模板
- ✅ 所有技能和知识库文件
- ✅ WorkspaceInitializer 注册
- ✅ 启动和测试验证
- ✅ 演示文档

**占位符扫描**：
- ✅ 无 TBD、TODO
- ✅ 所有步骤包含完整代码或命令
- ✅ 文件路径明确具体

**类型一致性**：
- ✅ 子 Agent 名称在各处一致
- ✅ 目录结构在各任务中一致

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2024-12-02-finance-intel-tracker.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - 我为每个任务分派一个全新的子 Agent，任务之间进行审查，快速迭代

**2. Inline Execution** - 在当前会话中使用 executing-plans 技能批量执行任务，设置检查点进行审查

**Which approach?**

**If Subagent-Driven chosen:**
- **REQUIRED SUB-SKILL:** 使用 superpowers:subagent-driven-development
- 每任务一个新子 Agent + 两阶段审查

**If Inline Execution chosen:**
- **REQUIRED SUB-SKILL:** 使用 superpowers:executing-plans
- 批量执行 + 设置检查点审查
