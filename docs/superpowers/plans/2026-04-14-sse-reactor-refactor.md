# SSE Reactor Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SseEmitter-based SSE with Reactor Flux<ServerSentEvent>, simplifying to single-endpoint direct streaming.

**Architecture:** Keep Spring MVC + Tomcat. AgentService returns `Flux<Map<String, Object>>` using `Flux.create()` to bridge ObservabilityHook callbacks and AgentScope's reactive stream into a single Flux. ChatController returns `Flux<ServerSentEvent<String>>` directly. Frontend replaces EventSource with fetch + ReadableStream.

**Tech Stack:** Spring Boot 3.5.13, Reactor Core (transitive from spring-boot-starter-web), Spring MVC async Servlet support.

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/java/com/msxf/agentscope/service/AgentService.java` | Modify | Returns `Flux<Map<String, Object>>` instead of writing to SseEmitter |
| `src/main/java/com/msxf/agentscope/controller/ChatController.java` | Modify | Single `POST /chat/send` returns `Flux<ServerSentEvent<String>>`, remove session/stream logic |
| `src/main/resources/application.yml` | Modify | Remove `spring.task.execution` block |
| `src/main/resources/static/scripts/chat.js` | Modify | Replace EventSource with fetch + ReadableStream + SSE parser |

---

### Task 1: Refactor AgentService — SseEmitter → Flux

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/service/AgentService.java`

- [ ] **Step 1: Replace the entire AgentService.java**

Replace the file content with the new Flux-based implementation. The key changes:
- Remove `SseEmitter` import, add Reactor imports
- Change method signature from `void streamToEmitter(...)` to `Flux<Map<String, Object>> createStreamFlux(...)`
- Use `Flux.create(sink -> { ... })` to bridge events
- ObservabilityHook's BiConsumer writes to `sink.next()` instead of `emitter.send()`
- AgentScope's `agent.stream()` subscribe also writes to `sink.next()`
- Cleanup (hook.removeConsumer + hook.reset) moves to `onComplete` callback

```java
package com.msxf.agentscope.service;

import agent.com.skloda.agentscope.AgentFactory;
import hook.com.skloda.agentscope.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Agent service that bridges AgentScope's reactive stream and
 * ObservabilityHook events into a single Flux for SSE delivery.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentFactory agentFactory;

    public AgentService(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                      String filePath, String fileName) {
        return Flux.create(sink -> {
            // 1. Create Hook, bridge events to sink
            ObservabilityHook hook = new ObservabilityHook();
            BiConsumer<String, Map<String, Object>> sseConsumer = (type, data) -> {
                Map<String, Object> payload = new LinkedHashMap<>(data);
                payload.put("type", type);
                sink.next(payload);
            };
            hook.addConsumer(sseConsumer);

            try {
                // 2. Create Agent with hook
                ReActAgent agent = agentFactory.createAgent(agentId, hook);

                // 3. Build message (file attachment logic unchanged)
                String actualMessage = message;
                if (filePath != null && !filePath.isBlank()) {
                    String fileInfo = String.format("[用户上传了文件: %s, 路径: %s]\n\n", fileName, filePath);
                    actualMessage = fileInfo + message;
                }

                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(actualMessage).build())
                        .build();

                // 4. Subscribe to AgentScope's reactive stream
                StreamOptions streamOptions = StreamOptions.builder()
                        .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                        .incremental(true)
                        .includeReasoningResult(true)
                        .build();

                agent.stream(userMsg, streamOptions)
                        .subscribe(
                                event -> {
                                    try {
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
                                    } catch (Exception e) {
                                        log.error("Error handling stream event", e);
                                    }
                                },
                                error -> {
                                    log.error("Stream error", error);
                                    sink.error(error);
                                },
                                () -> {
                                    hook.removeConsumer(sseConsumer);
                                    hook.reset();
                                    sink.complete();
                                }
                        );
            } catch (Exception e) {
                log.error("Error creating agent stream", e);
                hook.removeConsumer(sseConsumer);
                hook.reset();
                sink.error(e);
            }
        });
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/service/AgentService.java
git commit -m "refactor: convert AgentService from SseEmitter to Flux<ServerSentEvent>"
```

---

### Task 2: Refactor ChatController — single Flux endpoint

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/controller/ChatController.java`

- [ ] **Step 1: Replace the entire ChatController.java**

Key changes:
- Remove imports: `SseEmitter`, `TaskExecutor`, `ConcurrentHashMap`, `UUID`
- Add imports: `Flux`, `ServerSentEvent`, `ObjectMapper`
- Remove fields: `emitters`, `taskExecutor`
- Remove `GET /chat/stream` endpoint entirely
- Rewrite `POST /chat/send` to return `Flux<ServerSentEvent<String>>`
- Inject `ObjectMapper` for JSON serialization

```java
package com.msxf.agentscope.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import agent.com.skloda.agentscope.AgentConfig;
import agent.com.skloda.agentscope.AgentConfigService;
import service.com.skloda.agentscope.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chat Controller with reactive SSE streaming.
 * POST /chat/send returns Flux<ServerSentEvent> directly — no session management needed.
 */
@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentConfigService agentConfigService;

    @GetMapping("/")
    public String chat() {
        return "chat";
    }

    /**
     * Send message endpoint — returns SSE stream directly.
     */
    @PostMapping(value = "/chat/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<String>> sendMessage(@RequestBody Map<String, String> request) {
        String agentId = request.getOrDefault("agentId", "chat.basic");
        String message = request.get("message");
        String filePath = request.get("filePath");
        String fileName = request.get("fileName");

        if (message == null || message.isBlank()) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("message")
                            .data("{\"type\":\"error\",\"message\":\"Message cannot be empty\"}")
                            .build(),
                    ServerSentEvent.<String>builder()
                            .event("message")
                            .data("{\"type\":\"done\"}")
                            .build()
            );
        }

        return agentService.createStreamFlux(agentId, message, filePath, fileName)
                .map(data -> {
                    try {
                        String json = objectMapper.writeValueAsString(data);
                        return ServerSentEvent.<String>builder()
                                .event("message")
                                .data(json)
                                .build();
                    } catch (Exception e) {
                        return ServerSentEvent.<String>builder()
                                .event("message")
                                .data("{\"type\":\"error\",\"message\":\"Serialization error\"}")
                                .build();
                    }
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("message")
                                .data("{\"type\":\"done\"}")
                                .build()
                ))
                .onErrorResume(e -> {
                    log.error("Agent stream error", e);
                    String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    try {
                        return Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data(objectMapper.writeValueAsString(
                                                Map.of("type", "error", "message", errMsg)))
                                        .build(),
                                ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data("{\"type\":\"done\"}")
                                        .build()
                        );
                    } catch (Exception ex) {
                        return Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data("{\"type\":\"error\",\"message\":\"Internal error\"}")
                                        .build(),
                                ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data("{\"type\":\"done\"}")
                                        .build()
                        );
                    }
                });
    }

    /**
     * List all available agent configurations.
     */
    @GetMapping("/api/agents")
    @ResponseBody
    public List<AgentConfig> listAgents() {
        return agentConfigService.getAllAgents();
    }

    /**
     * Get a specific agent configuration by agentId.
     */
    @GetMapping("/api/agents/{agentId}")
    @ResponseBody
    public ResponseEntity<?> getAgentConfig(@PathVariable String agentId) {
        Optional<AgentConfig> config = agentConfigService.findAgentConfig(agentId);
        if (config.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Agent not found: " + agentId));
        }
        return ResponseEntity.ok(config.get());
    }

    /**
     * Get detailed information about a skill (including description).
     */
    @GetMapping("/api/skills/{skillName}")
    @ResponseBody
    public ResponseEntity<?> getSkillInfo(@PathVariable String skillName) {
        Map<String, Object> skillInfo = agentConfigService.getSkillInfo(skillName);
        if (skillInfo == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Skill not found: " + skillName));
        }
        return ResponseEntity.ok(skillInfo);
    }

    /**
     * Get detailed information about a tool (including description).
     */
    @GetMapping("/api/tools/{toolName}")
    @ResponseBody
    public ResponseEntity<?> getToolInfo(@PathVariable String toolName) {
        Map<String, Object> toolInfo = agentConfigService.getToolInfo(toolName);
        if (toolInfo == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Tool not found: " + toolName));
        }
        return ResponseEntity.ok(toolInfo);
    }

    /**
     * File upload endpoint.
     */
    @PostMapping("/chat/upload")
    @ResponseBody
    public Map<String, String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Map.of("error", "File is empty");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return Map.of("error", "File name is missing");
        }

        String lowerName = originalName.toLowerCase();
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".pdf") && !lowerName.endsWith(".xlsx")) {
            return Map.of("error", "Only .docx, .pdf, and .xlsx files are supported");
        }

        try {
            String fileId = UUID.randomUUID().toString();
            String ext = lowerName.substring(lowerName.lastIndexOf('.'));
            String savedName = fileId + ext;
            Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(savedName);
            file.transferTo(filePath.toFile());

            log.info("File uploaded: {} -> {}", originalName, filePath);

            return Map.of(
                    "fileId", fileId,
                    "fileName", originalName,
                    "filePath", filePath.toAbsolutePath().toString()
            );
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return Map.of("error", "Failed to save file: " + e.getMessage());
        }
    }

    /**
     * File download endpoint - serves uploaded and edited files.
     */
    @GetMapping("/chat/download")
    @ResponseBody
    public ResponseEntity<Resource> downloadFile(@RequestParam String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            Path filePath = uploadDir.resolve(fileId);

            if (!filePath.toFile().exists() && !fileId.contains(".")) {
                File[] matchingFiles = uploadDir.toFile().listFiles((dir, name) ->
                        name.startsWith(fileId) && (name.endsWith(".docx") || name.endsWith(".pdf") || name.endsWith(".xlsx"))
                );
                if (matchingFiles != null && matchingFiles.length > 0) {
                    filePath = matchingFiles[0].toPath();
                }
            }

            File file = filePath.toFile();
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            String fileName = file.getName();
            String contentType = "application/octet-stream";
            if (fileName.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (fileName.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (fileName.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }

            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to download file: {}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/controller/ChatController.java
git commit -m "refactor: ChatController — single Flux endpoint, remove SseEmitter/session"
```

---

### Task 3: Clean up application.yml

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Remove the task execution configuration block**

Remove lines 10-18 of `src/main/resources/application.yml` (the entire `spring.task.execution` block):

**Before:**
```yaml
spring:
  application:
    name: agentscope-demo
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
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

**After:**
```yaml
spring:
  application:
    name: agentscope-demo
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "chore: remove TaskExecutor config — Reactor uses async Servlet threads"
```

---

### Task 4: Refactor frontend chat.js — EventSource → fetch + ReadableStream

**Files:**
- Modify: `src/main/resources/static/scripts/chat.js`

- [ ] **Step 1: Replace state variables at the top of the file**

In `src/main/resources/static/scripts/chat.js`, replace lines 1-9:

**Before:**
```javascript
/* ===== STATE ===== */
let currentAgent = null;
let isStreaming = false;
let currentEventSource = null;
let messageCount = 0;
let uploadedFile = null;
let agentRawMarkdown = '';  // Accumulated raw markdown for current agent response

const agents = {};
```

**After:**
```javascript
/* ===== STATE ===== */
let currentAgent = null;
let isStreaming = false;
let currentAbortController = null;
let messageCount = 0;
let uploadedFile = null;
let agentRawMarkdown = '';  // Accumulated raw markdown for current agent response

const agents = {};

/* ===== SSE PARSER ===== */
function createSSEParser() {
    var buffer = '';
    return {
        parse: function(chunk, onEvent) {
            buffer += chunk;
            var lines = buffer.split('\n');
            buffer = lines.pop();
            var currentEvent = {};
            for (var i = 0; i < lines.length; i++) {
                var line = lines[i];
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

- [ ] **Step 2: Replace the sendMessage function**

Replace the entire `sendMessage` function (lines 333-637) with the new fetch + ReadableStream implementation. The event handling `switch` block stays exactly the same — only the transport mechanism changes:

```javascript
async function sendMessage() {
    var input = messageInput;
    var message = input.value.trim();
    if ((!message && !uploadedFile) || isStreaming) return;

    chatEmpty.style.display = 'none';

    document.querySelectorAll('.round-body:not(.collapsed)').forEach(function(body) {
        body.classList.add('collapsed');
    });

    appendMessage('user', message, uploadedFile);
    messageCount++;

    input.value = '';
    input.style.height = 'auto';

    var fileInfo = uploadedFile;
    removeFile();
    setStreamingState(true);
    startRound(message);

    var typingEl = document.createElement('div');
    typingEl.className = 'typing-indicator';
    typingEl.id = 'typingIndicator';
    typingEl.innerHTML = '<div class="message-avatar"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg></div><div class="dots"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>';
    chatMessages.appendChild(typingEl);
    scrollToBottom(chatMessages);

    currentThinkingBox = null;
    currentAgentMessageWrapper = null;
    thinkingContent = '';
    currentFileInfo = null;
    currentFileInfo = fileInfo;

    var agentBubble = null;

    if (fileInfo) {
        createThinkingBox(fileInfo);
    }

    try {
        currentAbortController = new AbortController();

        var response = await fetch('/chat/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                agentId: currentAgent,
                message: message,
                filePath: fileInfo ? fileInfo.filePath : null,
                fileName: fileInfo ? fileInfo.fileName : null
            }),
            signal: currentAbortController.signal
        });

        if (!response.ok) {
            removeTypingIndicator();
            appendMessage('error', '请求失败: ' + response.status);
            endRound('error');
            setStreamingState(false);
            return;
        }

        removeTypingIndicator();
        messageCount++;

        var reader = response.body.getReader();
        var decoder = new TextDecoder();
        var parser = createSSEParser();

        while (true) {
            var result = await reader.read();
            if (result.done) break;

            parser.parse(decoder.decode(result.value, { stream: true }), function(event) {
                if (event.event === 'message' && event.data) {
                    var payload;
                    try {
                        payload = JSON.parse(event.data);
                    } catch (e) {
                        return;
                    }

                    switch (payload.type) {

                        // ===== HOOK LIFECYCLE EVENTS (from ObservabilityHook) =====

                        case 'agent_start':
                            if (currentRound) {
                                addTimelineRow('phase', 'Agent Start', payload.agentName || '', 'running');
                            }
                            break;

                        case 'agent_end':
                            if (currentRound) {
                                var totalDur = payload.duration_ms || 0;
                                addTimelineRow('phase', 'Agent End', formatDuration(totalDur), 'ok');
                                currentRound.totalLlmCalls = payload.totalLlmCalls || 0;
                                currentRound.totalToolCalls = payload.totalToolCalls || 0;
                            }
                            break;

                        case 'llm_start':
                            if (currentRound) {
                                var callNum = payload.callNumber || (currentRound.llmCallCount + 1);
                                currentRound._currentLlmStart = Date.now();
                                currentRound._currentLlmCallNum = callNum;
                                currentRound._currentLlmRow = addTimelineRow('llm', 'LLM #' + callNum, (payload.modelName || '') + ' ...', 'running');
                            }
                            break;

                        case 'llm_end':
                            if (currentRound) {
                                currentRound.llmCallCount++;
                                var tokens = payload.totalTokens ? payload.totalTokens.toLocaleString() + ' tokens' : '';
                                var llmTimeSec = payload.llmTime ? payload.llmTime : 0;
                                var llmDurMs = 0;
                                if (currentRound._currentLlmStart) {
                                    llmDurMs = Date.now() - currentRound._currentLlmStart;
                                }
                                var timeStr = llmDurMs > 0 ? formatDuration(llmDurMs) : (llmTimeSec > 0 ? (llmTimeSec >= 1 ? llmTimeSec.toFixed(1) + 's' : (llmTimeSec * 1000).toFixed(0) + 'ms') : '');

                                currentRound.inputTokens += (payload.inputTokens || 0);
                                currentRound.outputTokens += (payload.outputTokens || 0);
                                currentRound.totalTokens += (payload.totalTokens || 0);
                                currentRound.llmTime += llmTimeSec;

                                if (currentRound._currentLlmRow) {
                                    var row = currentRound._currentLlmRow;
                                    var metricsEl = row.querySelector('.rtl-metrics');
                                    var statusEl = row.querySelector('.rtl-status');
                                    if (metricsEl) metricsEl.textContent = [tokens, timeStr].filter(Boolean).join(' ');
                                    if (statusEl) statusEl.textContent = '✓';
                                    row.classList.remove('status-running');
                                    row.classList.add('status-ok');
                                    currentRound._currentLlmRow = null;
                                }

                                updateRoundMetrics();
                                currentRound._currentLlmStart = null;
                            }
                            break;

                        case 'thinking':
                            if (currentRound && !currentRound.thinkingCycleStart) {
                                currentRound.thinkingCycleStart = Date.now();
                            }
                            var thinkingText = payload.content || 'Processing...';
                            updateThinkingBox(thinkingText, fileInfo);
                            break;

                        case 'reasoning_text':
                            var rText = payload.content || '';
                            if (rText) {
                                updateThinkingBox(rText, fileInfo);
                            }
                            break;

                        case 'tool_start':
                            if (currentRound && currentRound.thinkingCycleStart) {
                                currentRound.thinkingTime += Date.now() - currentRound.thinkingCycleStart;
                                currentRound.thinkingCycleStart = null;
                            }
                            var tName = payload.name || 'unknown';
                            var tParams = payload.params || '{}';
                            var tParamsPreview = payload.paramsPreview || tParams.substring(0, 50);
                            var isSkill = payload.isSkill === true;
                            var tSkillName = payload.displayName || '';

                            if (isSkill) {
                                updateThinkingBox('📖 Loading skill: ' + (tSkillName || '...'), fileInfo);
                            } else {
                                updateThinkingBox('⚡ ' + tName + '(' + tParamsPreview + ')', fileInfo);
                            }

                            if (currentRound) {
                                currentRound.toolCallCount++;
                                currentRound._currentToolStart = Date.now();
                                var rowType = isSkill ? 'skill' : 'tool';
                                var rowLabel = isSkill ? ('Skill → ' + (tSkillName || '')) : ('Tool → ' + tName);
                                currentRound._currentToolRow = addTimelineRow(rowType, rowLabel, '...', 'running');
                                currentRound._currentToolIsSkill = isSkill;
                                updateRoundMetrics();
                            }
                            break;

                        case 'tool_end':
                            var teName = payload.name || 'unknown';
                            var teResult = payload.result || '';
                            var teDurMs = payload.duration_ms || -1;
                            var tePreview = payload.resultPreview || (teResult.length > 100 ? teResult.substring(0, 100) + '...' : teResult);
                            var teIsSkill = payload.isSkill === true;
                            updateThinkingBox('✓ ' + teName + ': ' + tePreview, fileInfo);

                            var targetRound = currentRound || (rounds.length > 0 ? rounds[rounds.length - 1] : null);
                            if (targetRound) {
                                if (teDurMs > 0) {
                                    targetRound.toolTime += teDurMs;
                                } else if (targetRound._currentToolStart) {
                                    targetRound.toolTime += Date.now() - targetRound._currentToolStart;
                                }
                                targetRound._currentToolStart = null;

                                if (targetRound._currentToolRow) {
                                    var tRow = targetRound._currentToolRow;
                                    var tmEl = tRow.querySelector('.rtl-metrics');
                                    var tsEl = tRow.querySelector('.rtl-status');
                                    var durStr = teDurMs >= 0 ? formatDuration(teDurMs) : '';
                                    if (tmEl) tmEl.textContent = durStr;
                                    if (tsEl) tsEl.textContent = teDurMs >= 0 ? '✓' : '✗';
                                    tRow.classList.remove('status-running');
                                    tRow.classList.add(teDurMs >= 0 ? 'status-ok' : 'status-fail');
                                    targetRound._currentToolRow = null;
                                }

                                var metricsEl2 = document.getElementById('round-metrics-' + targetRound.number);
                                if (metricsEl2) updateRoundMetricsForRound(targetRound);
                            }
                            break;

                        // ===== STREAM CONTENT EVENTS =====

                        case 'text':
                            if (currentRound && currentRound.thinkingCycleStart) {
                                currentRound.thinkingTime += Date.now() - currentRound.thinkingCycleStart;
                                currentRound.thinkingCycleStart = null;
                            }
                            collapseThinkingBox();
                            if (!agentBubble) {
                                agentBubble = addAgentBubble();
                                agentRawMarkdown = '';
                            }
                            agentRawMarkdown += (payload.content || payload.text || '');
                            agentBubble.classList.add('md-render');
                            agentBubble.innerHTML = renderMarkdown(agentRawMarkdown);
                            scrollToBottom(chatMessages);
                            break;

                        case 'done':
                            completeThinkingBox();
                            isStreaming = false;
                            setStreamingState(false);
                            endRound('success');
                            scrollToBottom(chatMessages);
                            break;

                        case 'error':
                            completeThinkingBox();
                            if (!agentBubble) {
                                agentBubble = addAgentBubble();
                            }
                            agentBubble.classList.remove('md-render');
                            agentBubble.style.whiteSpace = 'pre-wrap';
                            agentBubble.textContent += '\n\n[ERROR] ' + (payload.message || payload.content || 'Unknown error');
                            agentBubble.closest('.message').classList.add('error');
                            endRound('error');
                            isStreaming = false;
                            setStreamingState(false);
                            scrollToBottom(chatMessages);
                            break;
                    }
                }
            });
        }

    } catch (err) {
        if (err.name === 'AbortError') {
            // User cancelled — not an error
            completeThinkingBox();
            isStreaming = false;
            setStreamingState(false);
            endRound('error');
            return;
        }
        completeThinkingBox();
        removeTypingIndicator();
        appendMessage('error', '[NETWORK ERROR] ' + err.message);
        endRound('error');
        setStreamingState(false);
    } finally {
        currentAbortController = null;
    }
}

function stopStreaming() {
    if (currentAbortController) {
        currentAbortController.abort();
        currentAbortController = null;
    }
    isStreaming = false;
    setStreamingState(false);
}
```

Key differences from the old `sendMessage`:
- No `currentEventSource` variable or EventSource usage
- No `data.sessionId` or two-step request
- `fetch` with `signal: currentAbortController.signal` for cancellation support
- `response.body.getReader()` + `TextDecoder` + `createSSEParser()` for SSE parsing
- The `switch` on `payload.type` is identical — no changes to event handling logic
- `done` case no longer closes EventSource, just resets state
- `error` case no longer closes EventSource, just resets state
- `catch` block handles AbortError separately (user cancellation)
- No `onerror` handler needed (no EventSource)

- [ ] **Step 3: Update clearSession function**

In `clearSession`, replace `currentEventSource` reference. Find this line inside `clearSession`:

```javascript
function clearSession() {
    if (isStreaming) return;
```

Replace the entire `clearSession` function with:

```javascript
function clearSession() {
    if (isStreaming) return;
    chatMessages.innerHTML = '';
    messageCount = 0;
    agentRawMarkdown = '';
    currentThinkingBox = null;
    currentAgentMessageWrapper = null;
    thinkingContent = '';
    currentFileInfo = null;
    chatMessages.appendChild(chatEmpty);
    chatEmpty.style.display = 'flex';
    removeFile();
    debugRounds.innerHTML = '';
    rounds = [];
    currentRound = null;
    roundNumber = 0;
}
```

(This is actually identical — just making sure no `currentEventSource` reference remains.)

- [ ] **Step 4: Verify no references to `currentEventSource` or `EventSource` remain**

Run: `grep -n "EventSource\|currentEventSource" src/main/resources/static/scripts/chat.js`
Expected: no output (no matches)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/scripts/chat.js
git commit -m "refactor: frontend — replace EventSource with fetch + ReadableStream"
```

---

### Task 5: Build verification and integration test

**Files:** None (verification only)

- [ ] **Step 1: Full clean build**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Verify no SseEmitter references remain in Java code**

Run: `grep -rn "SseEmitter\|SseEmitter\|TaskExecutor" src/main/java/`
Expected: no output (no matches)

- [ ] **Step 3: Verify no session/stream references remain in Java code**

Run: `grep -rn "sessionId\|/chat/stream\|emitters" src/main/java/`
Expected: no output (no matches)

- [ ] **Step 4: Manual smoke test**

Start the application:
```bash
export DASHSCOPE_API_KEY=<your_key>
mvn spring-boot:run
```

Open http://localhost:8080 and verify:
1. Chat UI loads, agents appear in sidebar
2. Click an agent, type a message, press Enter
3. Streaming works — text appears incrementally
4. Thinking box shows/hides correctly
5. Debug panel shows timeline with LLM/tool events
6. File upload still works (upload a .docx, sends to task agent)
7. Stop/cancel works (if mid-stream, can send new message after)

- [ ] **Step 5: Commit final state if any fixes were needed**

```bash
git add -A
git commit -m "fix: address integration issues from SSE Reactor refactor"
```
