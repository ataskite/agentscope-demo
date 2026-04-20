# 第三阶段：多智能体协作设计文档

**日期**: 2026-04-20
**项目**: AgentScope Demo
**阶段**: Phase 3 - 多智能体协作（进阶）
**状态**: 设计完成，待实施

---

## 1. 概述

### 1.1 目标

在现有单 Agent 架构基础上，增加多 Agent 协作能力，支持：

1. **Pipeline 多 Agent 管道** - 串联/并联/循环执行多个 Agent
2. **Routing 路由分发** - 智能分类并路由到专业 Agent
3. **Handoffs 智能体交接** - 动态切换 Agent 并保持对话历史

### 1.2 演示场景

**智能客服系统** - 一个完整的客服场景，展示三种协作模式的组合使用：

- 用户咨询问题 → **Routing** 自动分类 → **Pipeline** 处理复杂任务 → **Handoffs** 转接专家
- 9 个专家 Agent：文档、搜索、视觉、发票、销售、技术支持、投诉、订单、客服

### 1.3 设计原则

1. **向后兼容** - 现有单 Agent 配置和代码无需修改
2. **统一配置** - 所有 Agent 类型都在 `agents.yml` 中配置
3. **统一运行时** - 多 Agent 使用相同的 Runtime 和 Hook 机制
4. **可组合性** - Pipeline、Routing、Handoffs 可以互相嵌套

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         ChatController                          │
│                    (SSE Streaming + Upload)                      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AgentService                                │
│  ┌──────────────┐  ┌──────────────────────────────────────┐   │
│  │  AgentRoute  │  │    CompositeAgentFactory (NEW)        │   │
│  │    (Cache)   │  │  ┌─────────┐ ┌──────┐ ┌──────────┐  │   │
│  │              │  │  │Pipeline │ │Route │ │ Handoffs │  │   │
│  └──────────────┘  │  │ Agent   │ │ Agent│ │   Agent  │  │   │
│                     │  └─────────┘ └──────┘ └──────────┘  │   │
│                     └──────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AgentRuntimeFactory                          │
│         (Creates Runtime with Hook + Agent + Sink)              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ObservabilityHook (Extended)                  │
│           ┌─────────────────────────────────────────┐           │
│           │   Multi-Agent Events (NEW)              │           │
│           │  - pipeline_start, pipeline_step        │           │
│           │  - routing_decision, handoff_start      │           │
│           └─────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流

**Pipeline 流程：**
```
用户请求 → ChatController → AgentService → SequentialAgent
                ↓                                    ↓
          SSE Stream                           SubAgent1 → SubAgent2 → SubAgent3
                ↑                                    ↓
          ObservabilityHook                    每步都 emit 事件
```

**Routing 流程：**
```
用户请求 → ChatController → AgentService → RoutingAgent
                ↓                                    ↓
          SSE Stream                           LLM 决策 → 选择 SubAgent
                ↓                                                    ↓
          ObservabilityHook                                    执行并 emit 事件
```

**Handoffs 流程：**
```
用户请求 → ChatController → AgentService → HandoffsAgent
                ↓                                    ↓
          SSE Stream                           StateGraph (状态机)
                ↓                                    ↓
          ObservabilityHook                    Agent A → [触发条件] → Agent B
                                                     ↑
                                                工具调用 @Tool(transfer_to_xxx)
```

---

## 3. 组件设计

### 3.1 扩展 AgentConfig

```java
public class AgentConfig {
    // 现有字段
    private String agentId;
    private String name;
    private String description;
    private String modelName;
    private String systemPrompt;
    private Boolean streaming;
    private Boolean enableThinking;
    private List<String> skills;
    private List<String> userTools;
    private List<String> systemTools;

    // 新增：Agent 类型
    private AgentType type;  // SINGLE, SEQUENTIAL, PARALLEL, ROUTING, HANDOFFS

    // 新增：多 Agent 配置
    private List<SubAgentConfig> subAgents;
    private Boolean parallel;
    private List<HandoffTrigger> handoffTriggers;
}

public enum AgentType {
    SINGLE,      // 单 Agent（现有，默认值）
    SEQUENTIAL,  // Pipeline 串行
    PARALLEL,    // Pipeline 并行
    ROUTING,     // 路由分发
    HANDOFFS     // 智能体交接
}

public class SubAgentConfig {
    private String agentId;
    private String description;
}

public class HandoffTrigger {
    private TriggerType type;  // INTENT, EXPLICIT, INCAPABLE
    private List<String> keywords;
    private String target;
}

public enum TriggerType {
    INTENT,      // 意图识别触发
    EXPLICIT,    // 显式请求触发
    INCAPABLE    // 能力不足触发
}
```

### 3.2 CompositeAgentFactory

```java
@Service
public class CompositeAgentFactory {

    private final AgentFactory singleAgentFactory;
    private final AgentConfigService configService;

    public Agent createAgent(AgentConfig config, Memory memory) {
        switch (config.getType()) {
            case SINGLE:
                return singleAgentFactory.createAgent(config, memory);
            case SEQUENTIAL:
                return createSequentialAgent(config, memory);
            case PARALLEL:
                return createParallelAgent(config, memory);
            case ROUTING:
                return createRoutingAgent(config, memory);
            case HANDOFFS:
                return createHandoffsAgent(config, memory);
            default:
                throw new IllegalArgumentException("Unknown agent type: " + config.getType());
        }
    }

    private SequentialAgent createSequentialAgent(AgentConfig config, Memory memory) {
        List<Agent> subAgents = config.getSubAgents().stream()
            .map(sub -> {
                AgentConfig subConfig = configService.getAgentConfig(sub.getAgentId());
                return createAgent(subConfig, memory);
            })
            .toList();

        return SequentialAgent.builder()
            .name(config.getAgentId())
            .subAgents(subAgents)
            .build();
    }

    private ParallelAgent createParallelAgent(AgentConfig config, Memory memory) {
        // 类似 SequentialAgent，但使用 ParallelAgent
    }

    private AgentScopeRoutingAgent createRoutingAgent(AgentConfig config, Memory memory) {
        List<AgentScopeAgent> subAgents = config.getSubAgents().stream()
            .map(sub -> {
                AgentConfig subConfig = configService.getAgentConfig(sub.getAgentId());
                Agent agent = createAgent(subConfig, memory);
                return AgentScopeAgent.builder()
                    .name(sub.getAgentId())
                    .description(sub.getDescription())
                    .agent(agent)
                    .build();
            })
            .toList();

        return AgentScopeRoutingAgent.builder()
            .name(config.getAgentId())
            .model(createModel(config))
            .subAgents(subAgents)
            .parallel(config.getParallel() != null ? config.getParallel() : false)
            .build();
    }

    private StateGraphAgent createHandoffsAgent(AgentConfig config, Memory memory) {
        // 使用 StateGraph 实现交接逻辑
        // 支持工具触发交接、意图识别触发等
    }
}
```

### 3.3 扩展 ObservabilityHook

```java
public class ObservabilityHook {

    // 现有事件...
    public static final String AGENT_START = "agent_start";
    public static final String LLM_START = "llm_start";
    public static final String THINKING = "thinking";
    public static final String LLM_END = "llm_end";
    public static final String TOOL_START = "tool_start";
    public static final String TOOL_END = "tool_end";
    public static final String AGENT_END = "agent_end";

    // 新增：多 Agent 事件
    public static final String PIPELINE_START = "pipeline_start";
    public static final String PIPELINE_STEP_START = "pipeline_step_start";
    public static final String PIPELINE_STEP_END = "pipeline_step_end";
    public static final String PIPELINE_END = "pipeline_end";

    public static final String ROUTING_START = "routing_start";
    public static final String ROUTING_DECISION = "routing_decision";
    public static final String ROUTING_END = "routing_end";

    public static final String HANDOFF_START = "handoff_start";
    public static final String HANDOFF_COMPLETE = "handoff_complete";
    public static final String HANDOFF_ERROR = "handoff_error";

    // 新增：发射方法
    public void emitPipelineStart(String pipelineId, List<String> subAgents) {
        emit(Map.of(
            "type", PIPELINE_START,
            "pipelineId", pipelineId,
            "subAgents", subAgents,
            "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitPipelineStepStart(String pipelineId, int stepIndex, String agentId) {
        emit(Map.of(
            "type", PIPELINE_STEP_START,
            "pipelineId", pipelineId,
            "stepIndex", stepIndex,
            "agentId", agentId,
            "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitRoutingDecision(String routingId, String selectedAgent, String reasoning) {
        emit(Map.of(
            "type", ROUTING_DECISION,
            "routingId", routingId,
            "selectedAgent", selectedAgent,
            "reasoning", reasoning,
            "timestamp", System.currentTimeMillis()
        ));
    }

    public void emitHandoffStart(String fromAgent, String toAgent, String reason) {
        emit(Map.of(
            "type", HANDOFF_START,
            "fromAgent", fromAgent,
            "toAgent", toAgent,
            "reason", reason,
            "timestamp", System.currentTimeMillis()
        ));
    }
}
```

---

## 4. 配置系统

### 4.1 扩展 agents.yml

```yaml
# === 现有单 Agent 配置保持不变 ===
- agentId: chat-basic
  name: Basic Chat
  description: 简单对话
  type: SINGLE           # 可选，默认为 SINGLE
  modelName: qwen-plus
  # ... 其他配置

# === 新增：Pipeline Agent ===
- agentId: doc-analysis-pipeline
  type: SEQUENTIAL
  name: 文档分析流水线
  description: 文档解析 → 信息提取 → 报告生成
  modelName: qwen-plus
  subAgents:
    - agentId: doc-parser
      description: 解析文档内容
    - agentId: info-extractor
      description: 提取结构化信息
    - agentId: report-generator
      description: 生成分析报告
  parallel: false

# === 新增：Routing Agent ===
- agentId: smart-router
  type: ROUTING
  name: 智能路由器
  description: 自动分发到专家 Agent
  modelName: qwen-plus
  subAgents:
    - agentId: doc-expert
      description: 文档分析专家
    - agentId: code-expert
      description: 代码分析专家
    - agentId: search-expert
      description: 搜索专家
    - agentId: sales-expert
      description: 销售专家
  parallel: false

# === 新增：Handoffs Agent ===
- agentId: customer-service
  type: HANDOFFS
  name: 智能客服
  description: 支持智能体交接
  modelName: qwen-plus
  subAgents:
    - agentId: support-agent
      description: 一般客服
    - agentId: sales-agent
      description: 销售顾问
    - agentId: complaint-agent
      description: 投诉处理
  handoffTriggers:
    - type: INTENT
      keywords: ["转销售", "购买", "价格"]
      target: sales-agent
    - type: INTENT
      keywords: ["投诉", "不满"]
      target: complaint-agent
    - type: EXPLICIT
      keywords: ["转人工", "客服"]
      target: support-agent
    - type: INCAPABLE
      target: smart-router
```

### 4.2 演示场景：智能客服系统

```yaml
# === 9 个专家 Agent ===
- agentId: doc-expert
  name: 文档专家
  description: 处理文档解析、分析、提取
  modelName: qwen-plus
  skills: [docx, pdf, xlsx]

- agentId: search-expert
  name: 搜索专家
  description: 获取实时网络信息
  modelName: qwen-plus
  userTools: [web_search, get_current_weather, get_news]

- agentId: vision-expert
  name: 视觉专家
  description: 图片理解和 OCR
  modelName: qwen-vl-max
  modality: vision

- agentId: invoice-expert
  name: 发票专家
  description: 发票信息提取
  modelName: qwen-vl-max
  modality: vision

- agentId: sales-expert
  name: 销售专家
  description: 产品咨询和报价
  modelName: qwen-plus

- agentId: tech-support-expert
  name: 技术支持专家
  description: 代码和技术问题
  modelName: qwen-plus

- agentId: complaint-expert
  name: 投诉处理专家
  description: 处理客户投诉
  modelName: qwen-plus

- agentId: order-expert
  name: 订单查询专家
  description: 查询订单状态
  modelName: qwen-plus

- agentId: support-expert
  name: 客服专家
  description: 一般客服咨询
  modelName: qwen-plus

# === 组合协作场景 ===
- agentId: smart-customer-service
  type: HANDOFFS
  name: 智能客服系统
  description: 支持 Routing + Pipeline + Handoffs 的完整客服系统
  modelName: qwen-plus
  subAgents:
    - agentId: doc-expert
    - agentId: search-expert
    - agentId: vision-expert
    - agentId: invoice-expert
    - agentId: sales-expert
    - agentId: tech-support-expert
    - agentId: complaint-expert
    - agentId: order-expert
    - agentId: support-expert
  handoffTriggers:
    - type: INTENT
      keywords: [文档, 解析, 提取]
      target: doc-expert
    - type: INTENT
      keywords: [搜索, 新闻, 天气]
      target: search-expert
    - type: INTENT
      keywords: [图片, OCR, 发票]
      target: vision-expert
    - type: INTENT
      keywords: [购买, 价格, 报价]
      target: sales-expert
    - type: INTENT
      keywords: [代码, 技术, bug]
      target: tech-support-expert
    - type: INTENT
      keywords: [投诉, 不满]
      target: complaint-expert
    - type: INTENT
      keywords: [订单, 查询]
      target: order-expert
    - type: INCAPABLE
      target: search-expert
```

---

## 5. 前端扩展

### 5.1 Debug 面板扩展

在现有 Debug 面板基础上增加多 Agent 可视化：

```javascript
// 新增事件类型处理
switch (event.type) {
    case 'pipeline_start':
        showPipelineTimeline(event.data);
        break;
    case 'pipeline_step_start':
        highlightPipelineStep(event.data.stepIndex);
        break;
    case 'pipeline_step_end':
        showStepResult(event.data);
        break;
    case 'routing_decision':
        showRoutingDecision(event.data.selectedAgent, event.data.reasoning);
        break;
    case 'handoff_start':
        showHandoffAnimation(event.data.fromAgent, event.data.toAgent);
        break;
}
```

### 5.2 可视化元素

**Pipeline 流程图：**
- 横向节点显示
- 当前步骤高亮
- 显示每步执行时间
- 中间结果预览

**Routing 决策树：**
- 显示 LLM 决策过程
- 展示各候选 Agent 的匹配度
- 显示最终选择和原因

**Handoffs 状态转换：**
- 当前 Agent 高亮
- 箭头指向目标 Agent
- 显示触发原因

---

## 6. 错误处理

### 6.1 Pipeline 错误

```
Pipeline 中某步失败 → emit pipeline_step_error → 检查 stopOnError
                     ↓
            stopOnError=true → 中止 Pipeline
            stopOnError=false → 继续下一步
```

### 6.2 Routing 错误

```
LLM 无法分类 → emit routing_error → fallback 到默认 Agent
                                    或请求用户澄清
```

### 6.3 Handoffs 循环检测

```
检测到 A → B → A 循环 → emit handoff_loop_error → 停止交接
```

### 6.4 超时处理

```
Agent 执行超时 → emit agent_timeout_error → 终止当前 Agent
```

---

## 7. 测试策略

### 7.1 单元测试

- `CompositeAgentFactoryTest` - 测试各种 Agent 类型创建
- `SequentialAgentTest` - 测试串行执行逻辑
- `RoutingAgentTest` - 测试路由决策
- `HandoffsAgentTest` - 测试交接触发

### 7.2 集成测试

- 端到端测试：用户请求 → 多 Agent → SSE 响应
- 配置加载测试：验证 `agents.yml` 解析
- Hook 事件测试：验证多 Agent 事件正确发送

### 7.3 演示场景测试

| 场景 | 类型 | 验证点 |
|------|------|--------|
| 文档处理流水线 | Pipeline | 串行执行、中间结果、错误处理 |
| 综合调研 | Parallel | 并行执行、结果合并 |
| 智能客服 | Routing | 自动分类、专家分发 |
| 客服交接 | Handoffs | 意图识别、动态切换 |

---

## 8. 实施步骤

### 第 1 步：基础架构
- 扩展 `AgentConfig` 添加 `AgentType` 枚举
- 创建 `SubAgentConfig` 和 `HandoffTrigger` 类
- 创建 `CompositeAgentFactory`
- 扩展 `ObservabilityHook` 添加多 Agent 事件

### 第 2 步：Pipeline 实现
- 实现 `SequentialAgent` 创建逻辑
- 实现 `ParallelAgent` 创建逻辑
- 配置示例：文档处理流水线
- 前端流程图可视化

### 第 3 步：Routing 实现
- 实现 `AgentScopeRoutingAgent` 创建逻辑
- 配置示例：智能路由器
- 前端决策树可视化

### 第 4 步：Handoffs 实现
- 实现 `StateGraphAgent` 创建逻辑
- 实现交接触发工具
- 配置示例：智能客服
- 前端状态转换可视化

### 第 5 步：演示场景
- 创建 9 个专家 Agent 配置
- 创建组合协作场景配置
- 完整演示流程测试

---

## 9. 文件清单

### 新增文件

```
src/main/java/com/skloda/agentscope/
├── agent/
│   ├── AgentType.java                    # Agent 类型枚举
│   ├── SubAgentConfig.java               # 子 Agent 配置
│   └── HandoffTrigger.java               # 交接触发器配置
├── factory/
│   └── CompositeAgentFactory.java        # 多 Agent 工厂
└── agent/
    ├── SequentialAgentWrapper.java       # SequentialAgent 包装
    ├── ParallelAgentWrapper.java         # ParallelAgent 包装
    ├── RoutingAgentWrapper.java          # RoutingAgent 包装
    └── HandoffsAgentWrapper.java         # HandoffsAgent 包装
```

### 修改文件

```
src/main/java/com/skloda/agentscope/
├── agent/AgentConfig.java                # 添加 type, subAgents 等字段
├── agent/AgentConfigService.java         # 支持多 Agent 配置解析
├── service/AgentService.java             # 集成 CompositeAgentFactory
├── hook/ObservabilityHook.java           # 添加多 Agent 事件
└── resources/config/agents.yml           # 添加多 Agent 配置
```

---

## 10. 附录

### 10.1 AgentScope Java API 参考

- `SequentialAgent` - 串行执行多个 Agent
- `ParallelAgent` - 并行执行多个 Agent
- `AgentScopeRoutingAgent` - 路由分发
- `StateGraph` - 状态图，用于 Handoffs

### 10.2 相关文档

- [ROADMAP-LITE.md](../../ROADMAP-LITE.md) - 功能迭代计划
- [AgentScope Java 官方文档](https://java.agentscope.io/zh/intro.html)
