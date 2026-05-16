# 投诉复盘与决策优化系统 — Harness Demo 设计文档

## 背景

基于 AgentScope Java 1.1.0-RC1 的 Harness 模块，构建一个"基于事实链的投诉根因复盘与处置 ROI 测算平台"。系统以每日监管首投复盘数据为输入，自动完成根因分析、策略优化建议、ROI 测算三层递进分析。

### 业务上下文

每日产生 37-56 份监管首投复盘报告，核心数据模式：
- **投诉类型分布**：费用类占 55-62%（集中在逸骊会员费 + 担保费），催收类占 30%，扣款异议和征信类各约 7%
- **客户行为**：75% 的监管投诉是内部多次未承接后的外溢，非突然投诉；近 15 日投诉 ≥3 的客户占 69%
- **根因本质**：费用争议本质是"证据争议"（要证明没乱收，而非只要退钱），催收投诉核心是"谁在催、怎么催、会不会碰家人单位"
- **机制断点**：3999 线未发挥"监管来电处置"作用；费用争议响应模板缺失；停催指令与系统同步存在断点
- **策略方向**：建立"证据三件套"、分层让利、硬流程建设，不做全面退费也不硬扛

## Harness 四大核心能力展示

| Harness 能力 | 在本场景中的体现 |
|---|---|
| **Workspace 驱动** | AGENTS.md 定义投诉复盘分析师人格；knowledge/ 存放投诉分类体系、监管政策、处置策略手册 |
| **双层记忆沉淀** | 每日分析后 MemoryFlushHook 自动提炼事实（如"今日费用类占比 62.1%"）到 memory/YYYY-MM-DD.md；MemoryConsolidator 周期性合并为长期记忆；跨日对比趋势（如"逸骊会员费投诉加速"） |
| **对话压缩** | 多轮分析（上传数据 → 根因分析 → 追问 → 深挖 → 策略 → ROI）超阈值自动压缩，保留关键事实不丢失 |
| **SubAgent 编排** | 3 个子 Agent 独立 workspace：根因分析师、策略优化顾问、ROI 测算师，按序 spawn |

## 架构设计

### 整体方案：轻量适配层

新增 `harness/` package，通过 `HarnessAgentService` 封装 HarnessAgent 构建/调用/缓存，复用现有 ChatController 的 SSE 流式接口和前端 UI。在 agents.yml 中新增 `HARNESS` 类型，AgentService 路由到 HarnessAgentService。

```
用户请求
  │
  ▼
ChatController (现有，不改动)
  │
  ▼
AgentService (新增 HARNESS 类型路由分支)
  │
  ├─ type != HARNESS → AgentRuntimeFactory (现有逻辑，不变)
  │
  └─ type == HARNESS → HarnessAgentService (新增)
                         │
                         ├─ HarnessAgentFactory (构建 HarnessAgent)
                         ├─ HarnessRuntime (桥接 Flux<Event> → Flux<Map>)
                         └─ WorkspaceInitializer (首次初始化 workspace)
```

### 包结构

```
src/main/java/com/skloda/agentscope/
├── harness/                              # 新增 package
│   ├── HarnessAgentFactory.java          # HarnessAgent 构建工厂
│   ├── HarnessAgentService.java          # 生命周期管理 + 缓存 + 流式桥接
│   └── HarnessRuntime.java              # Flux<Event> → Flux<Map<String,Object>> 转换
├── agent/
│   ├── AgentType.java                    # 新增 HARNESS 枚举值
│   └── AgentConfig.java                  # 新增 harnessConfig 字段
└── service/
    └── AgentService.java                 # 新增 HARNESS 路由分支
```

### 数据流

```
1. 用户上传投诉数据文件（xlsx/csv/tsv）
   → ChatController.upload() → 保存到临时目录

2. 用户发送分析指令
   → ChatController.sendMessage()
   → AgentService.streamEvents()
   → 判断 type=HARNESS → HarnessAgentService.streamEvents()

3. HarnessAgentService.streamEvents():
   a. 首次调用时 → WorkspaceInitializer 初始化 workspace 目录结构
   b. HarnessAgentFactory 创建/获取缓存的 HarnessAgent
   c. 构造 RuntimeContext（sessionId + userId）
   d. 文件信息注入到消息前缀（复用现有逻辑）
   e. harnessAgent.stream(msg, ctx) → Flux<Event>

4. HarnessRuntime 桥接:
   Flux<Event> → Flux<Map<String, Object>>
   ├─ TextBlock → {"type": "text", "content": "..."}
   ├─ ToolCall → {"type": "tool_start", ...}
   ├─ ToolResult → {"type": "tool_end", ...}
   └─ SubAgent events → {"type": "subagent_*", ...}

5. ChatController 转为 SSE 返回前端
```

## Workspace 设计

### 主 Agent Workspace

```
~/.agentscope/complaint-reviewer/
├── AGENTS.md                          # 投诉复盘分析师人格定义
├── MEMORY.md                          # 长期记忆（MemoryConsolidator 自动维护）
├── knowledge/                         # 公共参考知识
│   ├── complaint-taxonomy.md          # 投诉分类体系（费用类/催收类/扣款异议/征信类/代客维权）
│   ├── regulatory-policy.md           # 监管政策参考（综合年化上限、投诉处理时限等）
│   └── action-playbook.md             # 建议动作手册（证据三件套、分层让利模型、3999线处置等）
├── skills/                            # 主 Agent 技能
│   └── complaint-data-parser/
│       └── SKILL.md                   # 投诉数据解析方法论
├── subagents/                         # 声明式子 Agent 定义
│   ├── root-cause-analyst.md
│   ├── strategy-optimizer.md
│   └── roi-calculator.md
├── memory/                            # 每日事实流水账（MemoryFlushHook 自动生成）
├── agents/                            # 会话持久化（自动生成）
└── sessions/                          # 对话日志（自动生成）
```

### 子 Agent 独立 Workspace

每个子 Agent 有独立 workspace（通过 subagent spec 的 workspace 字段指定）：

```
~/.agentscope/root-cause-analyst/
├── AGENTS.md                          # 根因分析师人格（专注事实链提取和分类）
├── skills/
│   ├── statistical-analysis/
│   │   └── SKILL.md                   # 统计分析方法论（分类统计、占比、趋势对比）
│   └── fact-chain-extraction/
│       └── SKILL.md                   # 事实链提取方法论（从 AI 复盘报告中提取事实节点）
└── knowledge/
    └── root-cause-framework.md        # 根因分析框架（5-Why、鱼骨图适配投诉场景）

~/.agentscope/strategy-optimizer/
├── AGENTS.md                          # 策略优化顾问人格
├── skills/
│   └── strategy-formulation/
│       └── SKILL.md                   # 策略制定方法论（评估维度、优先级排序）
└── knowledge/
    ├── strategy-playbook.md           # 处置策略手册（证据三件套、分层让利、硬流程）
    └── industry-benchmarks.md         # 行业基准参考

~/.agentscope/roi-calculator/
├── AGENTS.md                          # ROI 测算师人格
├── skills/
│   └── roi-simulation/
│       └── SKILL.md                   # ROI 模拟测算方法论
└── knowledge/
    ├── cost-model.md                  # 成本模型（退费支出、人力成本、系统改造成本）
    └── benefit-model.md               # 收益模型（投诉率下降、监管风险降低、客户留存）
```

## SubAgent 编排设计

### 分析链

```
用户上传投诉数据 + 发送分析指令
    │
    ▼ 主 Agent (complaint-reviewer)
    │  解析数据，理解上下文，判断分析范围
    │
    ├─ Step 1: root-cause-analyst
    │  输入：投诉表格原始数据
    │  输出：投诉类型分布（费用类62%、催收类30%...）
    │        高频问题 Top-N（逸骊会员费、担保费、第三方催收...）
    │        事实链（客户行为模式：75%内部外溢、69%高频复投...）
    │        机制断点识别（3999线失效、停催同步断点...）
    │
    ├─ Step 2: strategy-optimizer
    │  输入：Step 1 的根因分析结果
    │  输出：策略建议列表（按优先级排序）
    │        - 费用类：证据三件套、分层让利、费用解释话术切换
    │        - 催收类：行为核查反馈、停催指令同步、责任归属声明
    │        - 流程类：3999线升级处置、高频预警、标准化承诺时限
    │        每条策略包含：预期效果、实施难度、适用范围
    │
    └─ Step 3: roi-calculator
        输入：Step 1 + Step 2 的结果
        输出：每条策略的 ROI 测算
              - 成本侧：直接退费敞口、人力投入、系统改造成本
              - 收益侧：投诉压降率、监管风险降低、客户留存提升
              - ROI 排序和推荐组合
              - 模拟不同投入力度下的效果区间
```

### SubAgent 定义文件格式

`workspace/subagents/root-cause-analyst.md`:
```yaml
---
name: root-cause-analyst
description: 根因分析师，基于投诉数据做事实链分析和根因分类
workspace: ${user.home}/.agentscope/root-cause-analyst
model: dashscope:qwen-max
---
你是投诉根因分析专家。你的任务是从每日投诉数据中提取事实链，识别根因模式。

## 分析框架
1. 投诉类型分布统计
2. 高频问题识别（Top-N）
3. 客户行为模式分析（投诉频次、是否内部外溢）
4. 机制断点识别
5. 趋势预警

## 输出格式
- 分类统计表
- 事实链清单
- 机制断点列表
- 趋势预警信号
```

（strategy-optimizer.md 和 roi-calculator.md 格式类似）

## Agent 配置

### agents.yml 新增配置

```yaml
agentId: complaint-reviewer
type: HARNESS
name: 投诉复盘分析师
description: 基于事实链的投诉根因复盘与处置ROI测算平台
modelName: qwen-max
streaming: true
harnessConfig:
  workspace: ${user.home}/.agentscope/complaint-reviewer
  filesystemMode: LOCAL
  compaction:
    triggerMessages: 30
    keepMessages: 10
    flushBeforeCompact: true
  subagents:
    - name: root-cause-analyst
      description: 根因分析师，基于投诉数据做事实链分析和根因分类
    - name: strategy-optimizer
      description: 策略优化顾问，基于根因分析结果推荐优化策略
    - name: roi-calculator
      description: ROI 测算师，对不同策略做成本影响测算和模拟
```

### AgentType 枚举新增

```java
public enum AgentType {
    SINGLE, SEQUENTIAL, PARALLEL, ROUTING, HANDOFFS, DEBATE,
    LOOP, STATE_GRAPH, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR,
    HARNESS  // 新增
}
```

## pom.xml 新增依赖

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 前端集成

复用现有 chat.html UI：
- Agent 列表中新增 "投诉复盘分析师"（type=HARNESS）
- SSE 流式交互完全复用
- Debug Panel 中展示 Harness 特有事件：
  - workspace context 注入
  - memory flush / consolidation
  - subagent spawn / completion
  - compaction 触发

新增前端事件类型：
- `subagent_start` / `subagent_end` — 子 Agent 开始/完成
- `memory_flush` — 事实沉淀
- `compaction` — 对话压缩

## 实现范围

### 必须实现
1. pom.xml 新增 agentscope-harness 依赖
2. AgentType 新增 HARNESS 枚举
3. AgentConfig 新增 harnessConfig 字段
4. HarnessAgentFactory — HarnessAgent 构建逻辑
5. HarnessAgentService — 生命周期管理 + 缓存
6. HarnessRuntime — Flux 桥接
7. WorkspaceInitializer — 模板文件自动生成
8. AgentService 新增 HARNESS 路由
9. Workspace 模板文件（AGENTS.md、knowledge、subagents、skills）
10. agents.yml 新增 complaint-reviewer 配置
11. 前端 agents.js 支持 HARNESS 类型

### 不在本次范围
- Remote / Sandbox 文件系统模式（仅 Local 模式）
- 异步 SubAgent 任务（仅同步 spawn）
- 前端 workspace 文件树可视化
- 自定义工具注册（使用 Harness 内置工具）
