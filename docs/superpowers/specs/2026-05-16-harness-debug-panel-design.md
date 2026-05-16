# Harness Debug Panel 可观测性设计

## 背景

Harness 投诉复盘分析师已集成到项目中，但右侧 debug panel 对 Harness agent 完全空白。`HarnessRuntime` 只将 `AGENT_RESULT` 转为 `text` 事件，其余事件沦为无用的 `raw_event`。用户无法观测到：4 个 SubAgent 的执行链、memory flush/compaction、workspace context 注入、内置工具调用等核心 Harness 操作。

## 方案：Hook + Event Stream 双层观测

### 架构

```
HarnessAgent.stream(msg, ctx)     Event Stream 解析
         │                              │
         ▼                              ▼
   Flux<Event> ──parse──▶ llm_start/end, thinking, tool_start/end, text
                                    │
                                    ▼
                            Sinks.Many ◀─── HarnessDebugHook（自定义 Hook）
                                    │           │
                                    │           ├─ PreActingEvent → agent_spawn → harness_subagent_start
                                    │           ├─ PostActingEvent → agent_send → harness_subagent_end
                                    │           ├─ PostCallEvent → harness_memory_flush
                                    │           └─ PreReasoningEvent → harness_workspace_inject / harness_compaction
                                    │
                                    ▼
                         Flux.merge(streamEvents, hookEvents)
                                    │
                                    ▼
                    prepend agent_start, append agent_end + done
                                    │
                                    ▼
                          Flux<Map<String, Object>> → SSE → 前端 debug panel
```

### 新增事件类型

**Harness 专属事件（由 HarnessDebugHook 产生）：**

| 事件类型 | 触发时机 | 数据 |
|---|---|---|
| `harness_subagent_start` | PreActingEvent 检测到 `agent_spawn` 工具调用 | `{subagentName, task, timestamp}` |
| `harness_subagent_end` | PostActingEvent 检测到 `agent_send` 工具返回 | `{subagentName, resultPreview, duration_ms, timestamp}` |
| `harness_memory_flush` | PostCallEvent 检测到 MemoryFlushHook 执行 | `{factsCount, memoryFile, timestamp}` |
| `harness_compaction` | PreReasoningEvent 检测到 CompactionHook 触发 | `{triggerMessages, keptMessages, timestamp}` |
| `harness_workspace_inject` | PreReasoningEvent 检测到 WorkspaceContextHook 注入 | `{workspace, contextSize, timestamp}` |

**复用现有事件（由 Event Stream 解析产生）：**

| 事件类型 | 来源 Event | 说明 |
|---|---|---|
| `agent_start` / `agent_end` | 合成 | 流开始/结束时合成，包含 agent name 和总耗时 |
| `llm_start` / `llm_end` | REASONING | 从 ToolUseBlock 提取工具调用信息，包含 token 和耗时 |
| `thinking` | REASONING | 从 TextBlock/ThinkingBlock 提取推理内容 |
| `tool_start` / `tool_end` | TOOL_RESULT | 复用现有格式，新增 harness 内置工具识别 |
| `text` | AGENT_RESULT | 主 Agent 最终响应文本 |
| `done` | 合成 | 流结束时发送 |

### Debug Panel 时间线呈现

```
◎ agent_start: 投诉复盘分析师
  ◎ llm_start [qwen-max]
  ✓ llm_end (1.2s, 150 tokens)
  ◎ harness_workspace_inject
  ✓ harness_workspace_inject (12KB context)
  ◎ harness_subagent_start: root-cause-analyst
    ◎ llm_start [qwen-max]
    ✓ llm_end (0.8s, 120 tokens)
    ◎ tool_start: memory_search
    ✓ tool_end: memory_search (0.3s)
    ◎ llm_start [qwen-max]
    ✓ llm_end (2.1s, 350 tokens)
  ✓ harness_subagent_end: root-cause-analyst (3.5s) → 费用类占62%...
  ◎ harness_subagent_start: trend-analyst
  ✓ harness_subagent_end: trend-analyst (2.8s) → 费用类占比环比+5.2%...
  ◎ harness_subagent_start: strategy-optimizer
  ✓ harness_subagent_end: strategy-optimizer (4.1s) → 推荐证据三件套+分层让利...
  ◎ harness_subagent_start: roi-calculator
  ✓ harness_subagent_end: roi-calculator (3.2s) → ROI排序: 证据三件套(3.2x)...
  ◎ llm_start [qwen-max]
  ✓ llm_end (2.0s, 280 tokens)
  ⚡ harness_memory_flush: 5 条事实 → memory/2026-05-16.md
✓ agent_end (18.3s, 2 LLM, 6 tools)
```

## 文件变更

### 新增文件

| 文件 | 职责 |
|---|---|
| `src/main/java/com/skloda/agentscope/harness/HarnessDebugHook.java` | 自定义 Hook，实现 `Hook` 接口，通过 Consumer 回调输出 5 种 Harness 事件 |

### 修改文件

| 文件 | 变更 |
|---|---|
| `src/main/java/com/skloda/agentscope/harness/HarnessRuntime.java` | 重构：注册 HarnessDebugHook + 解析 Event Stream + Sinks.Many 合并 + 合成 agent_start/end |
| `src/main/java/com/skloda/agentscope/harness/HarnessAgentFactory.java` | 修改：构建 HarnessAgent 时注册 HarnessDebugHook（如果 API 支持） |
| `src/main/resources/static/scripts/modules/debug.js` | 新增 `harness` 行类型 + 5 个 harness 事件处理分支 |
| `src/main/resources/static/styles/modules/debug.css` | 新增 `.timeline-row.harness` 样式（蓝色左边框，缩进） |

### HarnessDebugHook 设计

```java
public class HarnessDebugHook implements Hook {
    
    private final BiConsumer<String, Map<String, Object>> eventConsumer;
    
    @Override
    public int priority() { return 5; }
    
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreActingEvent pre) {
            // 检测 agent_spawn 工具调用 → emit harness_subagent_start
        }
        if (event instanceof PostActingEvent post) {
            // 检测 agent_send 工具返回 → emit harness_subagent_end
        }
        if (event instanceof PostCallEvent post) {
            // 检测 memory flush → emit harness_memory_flush
        }
        if (event instanceof PreReasoningEvent pre) {
            // 检测 workspace context 注入 → emit harness_workspace_inject
            // 检测 compaction → emit harness_compaction
        }
        return Mono.just(event);
    }
}
```

### HarnessRuntime 重构设计

```java
public class HarnessRuntime {
    
    private final HarnessAgent agent;
    private final Sinks.Many<Map<String, Object>> hookEventSink;
    
    public Flux<Map<String, Object>> stream(Msg userMsg, RuntimeContext ctx) {
        hookEventSink = Sinks.many().unicast().onBackpressureBuffer();
        
        Flux<Map<String, Object>> streamEvents = agent.stream(userMsg, ctx)
            .map(HarnessRuntime::parseEvent);   // REASONING/TOOL_RESULT → llm/tool events
        
        Flux<Map<String, Object>> hookEvents = hookEventSink.asFlux();
        
        return Flux.concat(
            Flux.just(Map.of("type", "agent_start", "agentName", agent.getName())),
            Flux.merge(streamEvents, hookEvents),
            Flux.just(Map.of("type", "agent_end")),
            Flux.just(Map.of("type", "done"))
        );
    }
}
```

### 前端事件处理

**debug.js 新增 harness 行类型：**

```javascript
// 在 addTimelineRow() 中新增 harness 类型
case 'harness':
    icon = row.status === 'running' ? '◈' : '◈✓';
    cssClass = 'timeline-row harness';
    break;

// 在事件分发中新增
case 'harness_subagent_start':
    addTimelineRow('harness', { label: `${data.subagentName} 启动中...`, status: 'running' });
    break;
case 'harness_subagent_end':
    updateTimelineRow('harness', { label: `${data.subagentName} (${data.duration_ms}ms)`, status: 'done' });
    break;
case 'harness_memory_flush':
    addTimelineRow('harness', { label: `事实沉淀: ${data.factsCount} 条 → ${data.memoryFile}`, icon: '⚡' });
    break;
case 'harness_compaction':
    addTimelineRow('harness', { label: `对话压缩: ${data.triggerMessages}条 → ${data.keptMessages}条`, icon: '⚙' });
    break;
case 'harness_workspace_inject':
    addTimelineRow('harness', { label: `Workspace context 注入 (${data.contextSize})`, icon: '📋' });
    break;
```

**debug.css 新增样式：**

```css
.timeline-row.harness {
    border-left: 3px solid #3b82f6;
    padding-left: 16px;
    margin-left: 8px;
    color: #60a5fa;
}
.timeline-row.harness .timeline-icon {
    color: #3b82f6;
}
```

## 降级策略

如果 HarnessAgent 的 Hook 注册 API 不支持外部自定义 Hook，则 fallback 为：

1. 纯 Event Stream 解析
2. 从 TOOL_RESULT 中识别 `agent_spawn`/`agent_send` 工具调用模式 → 推断 subagent 生命周期
3. 放弃 `harness_memory_flush` / `harness_compaction` / `harness_workspace_inject` 精确观测
4. 仅保留 `harness_subagent_start/end` + 复用现有 llm/tool 事件
5. 通过工具调用名称（`agent_spawn`, `agent_send`）和结果内容推断 subagent 名称和耗时

## 实现范围

### 必须实现
1. `HarnessDebugHook.java` — 自定义 Hook
2. `HarnessRuntime.java` 重构 — 双层事件合并
3. `HarnessAgentFactory.java` 修改 — 注册 Hook
4. `debug.js` — 新增 harness 行类型和事件处理
5. `debug.css` — 新增 harness 样式

### 不在本次范围
- SubAgent 内部执行的 LLM/tool 调用详情（HarnessAgent 架构限制）
- SubAgent 执行过程的折叠/展开交互
- Workspace 文件树可视化
- 前端 harness 事件的单元测试
