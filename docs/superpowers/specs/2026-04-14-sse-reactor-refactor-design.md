# SSE Reactor 重构设计文档

## 概述

将当前基于 `SseEmitter` 的 SSE 实现替换为 Reactor `Flux<ServerSentEvent>` 模式。保留 Spring MVC + Tomcat，仅 SSE 流式部分使用 Reactor，消除 session 管理，简化为单端点直接流式架构。

## 决策记录

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 迁移范围 | 仅 SSE 部分用 Reactor 重写 | 改动最小，风险最低，其他接口保持传统 MVC |
| 架构模式 | 单端点直接流式 | 消除 session 管理，架构最简洁 |
| 桥接方案 | Sinks.Many / Flux.create | 保留 ObservabilityHook 回调机制，桥接到 Flux |
| 错误处理 | 优雅降级 | 流内发送 error + done 事件，不触发浏览器连接错误 |

## 架构变更

### 变更前后对比

```
变更前:
POST /chat/send → 创建 SseEmitter + sessionId → 返回 {sessionId}
GET /chat/stream?sessionId= → 返回 SseEmitter → TaskExecutor 异步推事件

变更后:
POST /chat/send → 直接返回 Flux<ServerSentEvent<String>> → 流式响应
```

### 涉及的文件

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `ChatController.java` | 重写 | 移除 SseEmitter/session，send 端点返回 Flux |
| `AgentService.java` | 重写 | streamToEmitter → createStreamFlux，返回 Flux |
| `ObservabilityHook.java` | 不变 | BiConsumer 回调机制由 AgentService 桥接到 Sink |
| `application.yml` | 修改 | 移除 TaskExecutor 线程池配置 |
| `chat.js` | 重写 | EventSource → fetch + ReadableStream |
| `chat.html` | 微调 | 如有 SSE 相关 HTML 结构调整 |
| `pom.xml` | 不变 | spring-boot-starter-web 已传递引入 reactor-core |

### 不变的组件

- Agent 配置链：AgentConfig / AgentConfigService / AgentFactory
- 工具注册：ToolRegistry / 所有 Tool 类
- Agent 定义：agents.json
- Skill 定义：skills/ 目录下所有 SKILL.md
- 文件上传/下载接口：保持传统 MVC

## 后端设计

### ChatController

**移除：**
- `ConcurrentHashMap<String, SseEmitter> emitters` 字段
- `TaskExecutor taskExecutor` 注入
- `GET /chat/stream` 端点

**改造 POST /chat/send：**

```java
@PostMapping(value = "/chat/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> sendMessage(@RequestBody Map<String, String> request) {
    String agentId = request.getOrDefault("agentId", "chat.basic");
    String message = request.get("message");
    String filePath = request.get("filePath");
    String fileName = request.get("fileName");

    return agentService.createStreamFlux(agentId, message, filePath, fileName)
        .map(data -> {
            String json = objectMapper.writeValueAsString(data);
            return ServerSentEvent.<String>builder()
                .event("message")
                .data(json)
                .build();
        })
        .concatWith(Flux.just(
            ServerSentEvent.<String>builder()
                .event("message")
                .data("{\"type\":\"done\"}")
                .build()
        ))
        .onErrorResume(e -> Flux.just(
            ServerSentEvent.<String>builder()
                .event("message")
                .data(objectMapper.writeValueAsString(
                    Map.of("type", "error", "message", e.getMessage())))
                .build(),
            ServerSentEvent.<String>builder()
                .event("message")
                .data("{\"type\":\"done\"}")
                .build()
        ));
}
```

关键点：
- Spring MVC 原生支持返回 `Flux<ServerSentEvent>`（Tomcat 下使用异步 Servlet）
- `concatWith` 保证 done 事件在最后
- `onErrorResume` 实现优雅降级

### AgentService

**方法签名变更：**

```java
// 变更前:
void streamToEmitter(String agentId, String message, String filePath,
                     String fileName, SseEmitter emitter)

// 变更后:
Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                           String filePath, String fileName)
```

**实现：**

```java
public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                   String filePath, String fileName) {
    return Flux.create(sink -> {
        // 1. 创建 Hook，桥接到 sink
        ObservabilityHook hook = new ObservabilityHook();
        BiConsumer<String, Map<String, Object>> sseConsumer = (type, data) -> {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            payload.put("type", type);
            sink.next(payload);
        };
        hook.addConsumer(sseConsumer);

        // 2. 创建 Agent（含 Hook）
        ReActAgent agent = agentFactory.createAgent(agentId, hook);

        // 3. 构建消息（文件附件逻辑不变）
        String actualMessage = message;
        if (filePath != null && !filePath.isBlank()) {
            actualMessage = String.format(
                "[用户上传了文件: %s, 路径: %s]\n\n%s", fileName, filePath, message);
        }
        Msg userMsg = Msg.builder()
            .role(MsgRole.USER)
            .content(TextBlock.builder().text(actualMessage).build())
            .build();

        // 4. 订阅 AgentScope 的响应式流
        StreamOptions streamOptions = StreamOptions.builder()
            .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
            .incremental(true)
            .includeReasoningResult(true)
            .build();

        agent.stream(userMsg, streamOptions)
            .subscribe(
                event -> {
                    // 仅提取文本内容发送 text 事件，生命周期事件由 Hook 处理
                    Msg msg = event.getMessage();
                    if (msg != null && msg.getContent() != null) {
                        for (ContentBlock block : msg.getContent()) {
                            if (block instanceof TextBlock tb && !event.isLast()) {
                                String text = tb.getText();
                                if (text != null && !text.isEmpty()) {
                                    sink.next(Map.of("type", "text", "content", text));
                                }
                            }
                        }
                    }
                },
                error -> sink.error(error),
                () -> {
                    hook.removeConsumer(sseConsumer);
                    hook.reset();
                    sink.complete();
                }
            );
    });
}
```

关键点：
- `Flux.create()` + sink 替代 SseEmitter
- ObservabilityHook 的 BiConsumer 回调桥接到 `sink.next()`
- AgentScope 的 `agent.stream()` 已是响应式流，直接 subscribe 推入 sink
- 无需 TaskExecutor，Reactor 在异步 Servlet 线程上调度

## 前端设计

### 核心变更：EventSource → fetch + ReadableStream

**移除：**
- `currentEventSource` 变量和所有 EventSource 相关代码
- 两步操作（先 POST 获取 sessionId，再连 EventSource）
- `EventSource.addEventListener` / `EventSource.onerror`

**新增 SSE 解析器：**

```javascript
function createSSEParser() {
    let buffer = '';
    return {
        parse(chunk, onEvent) {
            buffer += chunk;
            const lines = buffer.split('\n');
            buffer = lines.pop();  // 保留不完整的行
            let currentEvent = {};
            for (const line of lines) {
                if (line.startsWith('data:')) {
                    currentEvent.data = line.slice(5).trim();
                } else if (line.startsWith('event:')) {
                    currentEvent.event = line.slice(6).trim();
                } else if (line === '') {
                    if (currentEvent.data) {
                        onEvent(currentEvent);
                    }
                    currentEvent = {};
                }
            }
        }
    };
}
```

**流式消息发送：**

```javascript
let currentAbortController = null;

async function streamMessage(agentId, message, fileInfo) {
    isStreaming = true;
    currentAbortController = new AbortController();

    const response = await fetch('/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            agentId, message,
            filePath: fileInfo?.filePath ?? null,
            fileName: fileInfo?.fileName ?? null
        }),
        signal: currentAbortController.signal
    });

    if (!response.ok) {
        throw new Error('请求失败: ' + response.status);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    const parser = createSSEParser();

    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            parser.parse(decoder.decode(value, { stream: true }), (event) => {
                if (event.event === 'message' && event.data) {
                    const payload = JSON.parse(event.data);
                    handleEvent(payload);  // 复用现有事件处理逻辑
                }
            });
        }
    } catch (e) {
        if (isStreaming) {
            appendAssistantMessage('连接中断，请重试');
        }
    } finally {
        isStreaming = false;
        currentAbortController = null;
    }
}

function stopStreaming() {
    if (currentAbortController) {
        currentAbortController.abort();
        currentAbortController = null;
    }
    isStreaming = false;
    resetUI();
}
```

### 事件处理 — 不变

所有事件类型的 JSON 结构和处理逻辑完全保留：

```
agent_start, llm_start, thinking, llm_end, tool_start, tool_end, text, done, error
```

### SSE 格式兼容性

Spring 的 `Flux<ServerSentEvent>` 输出格式与之前 `SseEmitter` 一致：

```
event:message
data:{"type":"agent_start","agentName":"chat.basic",...}

event:message
data:{"type":"text","content":"你好"}

event:message
data:{"type":"done"}
```

## 配置变更

### application.yml

```yaml
# 移除:
spring:
  task:
    execution:
      pool:
        core-size: 10
        max-size: 50
        queue-capacity: 100
        keep-alive: 60s
        allow-core-thread-timeout: true
      thread-name-prefix: sse-
```

Reactor 使用异步 Servlet 线程，无需显式线程池配置。

### API 端点最终状态

| 方法 | 路径 | 返回类型 | 状态 |
|------|------|---------|------|
| GET | `/` | String (Thymeleaf) | 不变 |
| GET | `/api/agents` | List\<AgentConfig\> | 不变 |
| GET | `/api/agents/{id}` | AgentConfig | 不变 |
| POST | `/chat/send` | Flux\<ServerSentEvent\<String\>\> | 重写 |
| ~~GET~~ | ~~`/chat/stream`~~ | ~~SseEmitter~~ | 移除 |
| POST | `/chat/upload` | Map | 不变 |
| GET | `/chat/download` | ResponseEntity\<Resource\> | 不变 |

## 移除的组件清单

- `ConcurrentHashMap<String, SseEmitter> emitters` — session 存储
- `TaskExecutor` 线程池注入和使用
- `GET /chat/stream` 端点
- `SseEmitter` 的 timeout/completion/error 回调
- 前端 `currentEventSource` 变量和 EventSource 相关代码
- `spring.task.execution` 配置

## 错误处理策略

| 错误场景 | 处理方式 |
|---------|---------|
| Agent 处理异常（LLM 失败、工具错误） | `onErrorResume` 发送 error + done 事件 |
| HTTP 请求失败（网络、4xx/5xx） | 前端 `response.ok` 检查，抛异常提示用户 |
| 流传输中断 | 前端 try/catch 捕获，显示重试提示 |
| 用户主动取消 | AbortController.abort()，前端 resetUI() |
