# Chat UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Thymeleaf-based chat UI with SSE streaming to migrate 2 CLI demos to a web interface.

**Architecture:** Spring Boot serves a Thymeleaf page at `/`. Frontend sends chat messages via POST, receives real-time responses via SSE (SseEmitter). AgentService manages two ReActAgent instances (basic + tool) with in-memory sessions. The frontend renders three columns: agent selector, chat area, and collapsible debug panel.

**Tech Stack:** Spring Boot 3.3.6, Thymeleaf, SSE (SseEmitter), AgentScope 1.0.11, DashScope qwen-plus, vanilla HTML/CSS/JS

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `pom.xml` | Add thymeleaf dependency |
| Create | `src/main/java/com/msxf/agentscope/model/SimpleTools.java` | Tool methods extracted from ToolCallingExample |
| Create | `src/main/java/com/msxf/agentscope/service/AgentService.java` | Agent lifecycle, session storage, streaming orchestration |
| Create | `src/main/java/com/msxf/agentscope/controller/ChatController.java` | Page rendering, POST /chat/send, GET /chat/stream SSE |
| Create | `src/main/resources/templates/chat.html` | Thymeleaf template with inline CSS + JS |

---

### Task 1: Add Thymeleaf Dependency

**Files:**
- Modify: `pom.xml` (after line 47, inside `<dependencies>`)

- [x] **Step 1: Add spring-boot-starter-thymeleaf to pom.xml**

Add this block after the `spring-boot-starter-web` dependency (after line 47):

```xml
        <!-- Thymeleaf -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
```

- [x] **Step 2: Verify dependency resolves**

Run: `cd /Users/jiangkun/Documents/workspace/agentscope-demo && ./mvnw dependency:resolve -q`
Expected: BUILD SUCCESS (no errors)

- [x] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add spring-boot-starter-thymeleaf dependency"
```

---

### Task 2: Create SimpleTools Model

**Files:**
- Create: `src/main/java/com/msxf/agentscope/model/SimpleTools.java`

- [x] **Step 1: Create SimpleTools class**

```java
package com.msxf.agentscope.model;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class SimpleTools {

    @Tool(name = "get_current_time", description = "Get the current date and time in a specific timezone")
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "Timezone name, e.g., 'Asia/Shanghai', 'America/New_York'") String timezone) {
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            LocalDateTime now = LocalDateTime.now(zoneId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return String.format("Current time in %s: %s", timezone, now.format(formatter));
        } catch (Exception e) {
            return "Error: Invalid timezone. Try 'Asia/Shanghai' or 'America/New_York'";
        }
    }

    @Tool(name = "calculate_sum", description = "Calculate the sum of two numbers")
    public double calculateSum(
            @ToolParam(name = "a", description = "First number") double a,
            @ToolParam(name = "b", description = "Second number") double b) {
        return a + b;
    }

    @Tool(name = "get_weather", description = "Get weather information for a city")
    public String getWeather(
            @ToolParam(name = "city", description = "Name of the city") String city) {
        Map<String, String> weatherData = new HashMap<>();
        weatherData.put("Beijing", "Sunny, 25°C");
        weatherData.put("Shanghai", "Cloudy, 22°C");
        weatherData.put("Guangzhou", "Rainy, 28°C");
        weatherData.put("Shenzhen", "Sunny, 30°C");
        return weatherData.getOrDefault(city, "Weather information not available for " + city);
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `cd /Users/jiangkun/Documents/workspace/agentscope-demo && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/model/SimpleTools.java
git commit -m "feat: add SimpleTools model extracted from ToolCallingExample"
```

---

### Task 3: Create AgentService

**Files:**
- Create: `src/main/java/com/msxf/agentscope/service/AgentService.java`

This service manages two ReActAgent instances. It uses `ConcurrentHashMap` to store agents by type and provides a `streamToEmitter` method that subscribes to the AgentScope `Flux<Event>` and writes SSE events to a `SseEmitter`.

Key design decisions:
- Agents are lazily initialized on first request per type
- API key is read from `agentscope.model.dashscope.api-key` in application.yml (which reads from env var `DASHSCOPE_API_KEY`)
- StreamOptions subscribes to `REASONING` and `TOOL_RESULT` events with incremental mode
- ContentBlock pattern matching handles ThinkingBlock, TextBlock, ToolUseBlock, ToolResultBlock

- [x] **Step 1: Create AgentService class**

```java
package com.msxf.agentscope.service;

import tool.com.skloda.agentscope.SimpleTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.Event;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.*;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    private final ConcurrentHashMap<String, ReActAgent> agents = new ConcurrentHashMap<>();

    public ReActAgent getAgent(String agentType) {
        return agents.computeIfAbsent(agentType, this::createAgent);
    }

    private ReActAgent createAgent(String agentType) {
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .build();

        ReActAgent.ReActAgentBuilder builder = ReActAgent.builder()
                .name(switch (agentType) {
                    case "tool" -> "ToolAgent";
                    default -> "Assistant";
                })
                .sysPrompt(switch (agentType) {
                    case "tool" -> "You are a helpful AI assistant with access to various tools. " +
                            "Use the appropriate tools when needed to answer questions accurately. " +
                            "Always explain what you're doing when using tools.";
                    default -> "You are a helpful AI assistant. Be friendly and concise.";
                })
                .model(model)
                .memory(new InMemoryMemory());

        if ("tool".equals(agentType)) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new SimpleTools());
            builder.toolkit(toolkit);
        } else {
            builder.toolkit(new Toolkit());
        }

        return builder.build();
    }

    public void streamToEmitter(String agentType, String message, SseEmitter emitter) {
        ReActAgent agent = getAgent(agentType);

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(message).build())
                .build();

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(false)
                .build();

        agent.stream(userMsg, streamOptions)
                .subscribe(
                        event -> {
                            try {
                                handleEvent(event, emitter);
                            } catch (Exception e) {
                                log.error("Error handling stream event", e);
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            try {
                                sendEvent(emitter, "error", Map.of("message", error.getMessage() != null ? error.getMessage() : "Unknown error"));
                            } catch (Exception e) {
                                log.error("Error sending error event", e);
                            }
                            emitter.complete();
                        },
                        () -> {
                            try {
                                sendEvent(emitter, "done", Map.of());
                            } catch (Exception e) {
                                log.error("Error sending done event", e);
                            }
                            emitter.complete();
                        }
                );
    }

    private void handleEvent(Event event, SseEmitter emitter) throws Exception {
        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) {
            return;
        }

        for (ContentBlock block : msg.getContent()) {
            switch (block) {
                case ThinkingBlock tb -> {
                    String thinking = tb.getThinking();
                    if (thinking != null && !thinking.isEmpty()) {
                        sendEvent(emitter, "thinking", Map.of("content", thinking));
                    }
                }
                case TextBlock tb -> {
                    String text = tb.getText();
                    if (text != null && !text.isEmpty()) {
                        sendEvent(emitter, "text", Map.of("content", text));
                    }
                }
                case ToolUseBlock tub -> {
                    sendEvent(emitter, "tool_call", Map.of(
                            "name", tub.getName() != null ? tub.getName() : "",
                            "params", tub.getInput() != null ? tub.getInput().toString() : "{}"
                    ));
                }
                case ToolResultBlock trb -> {
                    String resultText = trb.getOutput() != null
                            ? trb.getOutput().stream()
                              .filter(o -> o instanceof TextBlock)
                              .map(o -> ((TextBlock) o).getText())
                              .reduce("", (a, b) -> a + b)
                            : "";
                    sendEvent(emitter, "tool_result", Map.of(
                            "name", trb.getName() != null ? trb.getName() : "",
                            "result", resultText
                    ));
                }
                default -> {
                }
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String type, Map<String, Object> data) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(data);
        payload.put("type", type);
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().name("message").data(json));
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `cd /Users/jiangkun/Documents/workspace/agentscope-demo && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/service/AgentService.java
git commit -m "feat: add AgentService for agent lifecycle and SSE streaming"
```

---

### Task 4: Create ChatController

**Files:**
- Create: `src/main/java/com/msxf/agentscope/controller/ChatController.java`

The controller has three endpoints:
- `GET /` — renders the Thymeleaf chat page
- `POST /chat/send` — accepts `{agentType, message}`, creates an SseEmitter, starts streaming in a separate thread, returns the emitter ID
- `GET /chat/stream?sessionId=xxx` — returns the SseEmitter for the given session

Data flow: POST creates emitter → stores in map → returns sessionId → frontend opens EventSource with that ID → streaming begins.

- [x] **Step 1: Create ChatController class**

```java
package com.msxf.agentscope.controller;

import service.com.skloda.agentscope.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Autowired
    private AgentService agentService;

    @GetMapping("/")
    public String chat() {
        return "chat";
    }

    @PostMapping("/chat/send")
    @ResponseBody
    public Map<String, String> sendMessage(@RequestBody Map<String, String> request) {
        String agentType = request.getOrDefault("agentType", "basic");
        String message = request.get("message");

        if (message == null || message.isBlank()) {
            return Map.of("error", "Message cannot be empty");
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.warn("SSE emitter timed out for session: {}", sessionId);
        });
        emitter.onError(ex -> {
            emitters.remove(sessionId);
            log.error("SSE emitter error for session: {}", sessionId, ex);
        });

        // Start streaming in background thread
        executor.submit(() -> {
            try {
                agentService.streamToEmitter(agentType, message, emitter);
            } catch (Exception e) {
                log.error("Error during agent streaming", e);
                emitter.completeWithError(e);
            }
        });

        return Map.of("sessionId", sessionId);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream(@RequestParam String sessionId) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            SseEmitter errorEmitter = new SseEmitter();
            executor.submit(() -> {
                try {
                    errorEmitter.send(SseEmitter.event().name("message").data("{\"type\":\"error\",\"message\":\"Session not found\"}"));
                    errorEmitter.complete();
                } catch (Exception e) {
                    // ignore
                }
            });
            return errorEmitter;
        }
        return emitter;
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `cd /Users/jiangkun/Documents/workspace/agentscope-demo && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/controller/ChatController.java
git commit -m "feat: add ChatController with SSE endpoints"
```

---

### Task 5: Create chat.html Thymeleaf Template

**Files:**
- Create: `src/main/resources/templates/chat.html`

This is the main UI page. Use the **frontend-design** skill to produce a high-quality design. The template must include:

**Layout requirements:**
- Three-column layout: left sidebar (agent selector), center (chat), right (debug panel)
- Right debug panel is collapsible (toggle button, collapses to 28px strip)
- Top header bar with project title
- Responsive: chat area fills remaining width

**Left sidebar:**
- Title "Agents"
- Two clickable cards: "Basic Chat" (agentType=basic) and "Tool Calling" (agentType=tool)
- Active agent highlighted with accent color
- Icons/emojis for each agent type

**Chat area:**
- Top bar showing current agent name and description
- Scrollable message area
- User messages: blue bubble, right-aligned
- Agent messages: gray bubble, left-aligned
- Input area at bottom with text input and Send button
- Input disabled while agent is responding
- Loading indicator during response

**Debug panel:**
- Header with "Debug" label and collapse/expand button
- Scrollable log area with monospace font
- Color-coded log entries:
  - Thinking → blue (#1976d2)
  - Tool Call → green (#388e3c)
  - Tool Result → green (#388e3c)
  - Text/Response → orange (#f57c00)
  - Error → red (#d32f2f)
- Each entry shows timestamp + type tag + content

**JavaScript logic:**
- `selectAgent(type)` — switch agent, clear chat, update active state
- `sendMessage()` — POST to `/chat/send`, then open EventSource to `/chat/stream?sessionId=xxx`
- EventSource `onmessage` — parse JSON, route by `type` field:
  - `text` → append to agent message bubble
  - `thinking` → append to debug panel with blue tag
  - `tool_call` → append to debug panel with green tag
  - `tool_result` → append to debug panel with green tag
  - `done` → close EventSource, re-enable input
  - `error` → show error in chat and debug, close EventSource
- `toggleDebug()` — collapse/expand right panel
- Auto-scroll chat and debug to bottom on new content

- [x] **Step 1: Use frontend-design skill to create the template**

Invoke the `frontend-design` skill to produce a production-quality Thymeleaf template at `src/main/resources/templates/chat.html`. The template should be a complete HTML file with:
- `xmlns:th="http://www.thymeleaf.org"` on the html tag
- All CSS inline in a `<style>` block
- All JS inline in a `<script>` block
- No external dependencies (no CDN links)
- Modern, clean design with subtle shadows and rounded corners
- The SSE logic described above fully implemented in JS

- [x] **Step 2: Verify the page loads**

Run: `cd /Users/jiangkun/Documents/workspace/agentscope-demo && ./mvnw spring-boot:run`

Then open `http://localhost:8080` in a browser. Expected: The three-column chat UI renders correctly with no console errors.

Stop the server after verifying.

- [x] **Step 3: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: add chat.html Thymeleaf template with three-column SSE UI"
```

---

### Task 6: End-to-End Smoke Test

- [x] **Step 1: Start the application**

Run: `cd /Users/jiangkun/Documents/workspace/agentscope-demo && ./mvnw spring-boot:run`

- [x] **Step 2: Verify page renders**

Open `http://localhost:8080`. Check:
- Three columns visible (left agent list, center chat area, right debug panel)
- Two agent cards in left sidebar ("Basic Chat", "Tool Calling")
- Chat input area with text input and Send button
- Debug panel shows "Debug" header with collapse button

- [x] **Step 3: Test Basic Chat agent**

1. Click "Basic Chat" in left sidebar
2. Type "Hello, introduce yourself" in input
3. Click Send
4. Expected:
   - Input disabled during response
   - Chat bubble appears with streaming text in center
   - Debug panel shows thinking (blue) and text (orange) events
   - Input re-enables after response completes

- [x] **Step 4: Test Tool Calling agent**

1. Click "Tool Calling" in left sidebar
2. Type "What time is it in Shanghai?" in input
3. Click Send
4. Expected:
   - Debug panel shows tool_call event for `get_current_time` (green)
   - Debug panel shows tool_result event with time (green)
   - Chat shows agent's response with the time
   - Input re-enables after response completes

- [x] **Step 5: Test debug panel collapse**

1. Click collapse button on debug panel header
2. Expected: Right panel collapses to narrow strip showing "Debug" vertically
3. Click the strip to expand
4. Expected: Panel expands back to full width

- [x] **Step 6: Test agent switching**

1. Switch between Basic Chat and Tool Calling
2. Expected: Chat area clears, debug log retains history

Stop the server.

- [x] **Step 7: Final commit (if any fixes were needed)**

```bash
git add -A
git commit -m "fix: address issues found during smoke testing"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** All requirements from `2026-04-09-chat-ui-design.md` are covered
  - Three-column layout → Task 5
  - Collapsible debug panel → Task 5
  - SSE streaming → Tasks 3, 4
  - Two agent types (basic, tool) → Task 3
  - Three tools → Task 2
  - ConcurrentHashMap session storage → Tasks 3, 4
  - No login required → Task 4 (no auth)
- [x] **Placeholder scan:** No TBDs, TODOs, or vague instructions
- [x] **Type consistency:** Method names and types match across tasks
  - `AgentService.streamToEmitter(String, String, SseEmitter)` matches `ChatController` call
  - `sendEvent()` helper uses consistent Map-based payload
  - Frontend JS event types match backend SSE event types (text, thinking, tool_call, tool_result, done, error)
