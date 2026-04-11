# Agent Configuration Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor agent creation to use a JSON-based configuration layer with AgentId routing, and make the frontend dynamically load and display agent configs.

**Architecture:** A `config/agents.json` file defines all agents. `AgentConfigService` loads and serves configs. `AgentFactory` builds `ReActAgent` instances from configs using `ToolRegistry` for tool/skill resolution. `AgentService` becomes a thin routing layer. Frontend loads agents via REST API and supports read-only config viewing.

**Tech Stack:** Spring Boot 3.5, Java 21, Jackson, AgentScope 1.0.11, vanilla JS

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/resources/config/agents.json` | Agent definitions (all agents in one file) |
| Create | `src/main/java/com/msxf/agentscope/config/AgentConfig.java` | Single agent config entity |
| Create | `src/main/java/com/msxf/agentscope/config/AgentsConfig.java` | Root wrapper for JSON deserialization |
| Create | `src/main/java/com/msxf/agentscope/config/AgentConfigService.java` | Load and query agent configs |
| Create | `src/main/java/com/msxf/agentscope/config/AgentFactory.java` | Build ReActAgent from config |
| Create | `src/main/java/com/msxf/agentscope/tool/ToolRegistry.java` | Map tool/skill names to instances |
| Modify | `src/main/java/com/msxf/agentscope/service/AgentService.java` | Remove hardcoded creation, use AgentFactory |
| Modify | `src/main/java/com/msxf/agentscope/controller/ChatController.java` | Add agent listing APIs, use agentId |
| Modify | `src/main/resources/templates/chat.html` | Dynamic agent loading + config viewer |

---

### Task 1: Create agents.json Configuration File

**Files:**
- Create: `src/main/resources/config/agents.json`

- [ ] **Step 1: Create the config directory and file**

```json
{
  "agents": [
    {
      "agentId": "chat.basic",
      "name": "Basic Chat",
      "description": "Simple conversation with AI assistant",
      "systemPrompt": "You are a helpful AI assistant. Be friendly and concise.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": [],
      "tools": []
    },
    {
      "agentId": "tool.calculator",
      "name": "Tool Calling",
      "description": "AI with time, calculator, weather tools",
      "systemPrompt": "You are a helpful AI assistant with access to various tools. Use the appropriate tools when needed to answer questions accurately. Always explain what you're doing when using tools.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": [],
      "tools": ["SimpleTools"]
    },
    {
      "agentId": "task.document-analysis",
      "name": "Task Agent",
      "description": "Document analysis with file parsing",
      "systemPrompt": "You are a document analysis assistant. When the user uploads a document, use the appropriate skill to parse it and then fulfill the user's request based on the extracted content. Support .docx, .pdf, and .xlsx file analysis.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": ["docx", "pdf", "xlsx"],
      "tools": []
    },
    {
      "agentId": "template.docx-editor",
      "name": "Template Editor",
      "description": "Word template variable replacement",
      "systemPrompt": "You are a Word document template editor. When users upload a .docx template, help them fill in variables. First parse the document to identify placeholders, then ask for values if needed, then replace them using edit_docx.",
      "modelName": "qwen-plus",
      "streaming": true,
      "enableThinking": true,
      "skills": ["docx-template"],
      "tools": []
    }
  ]
}
```

- [ ] **Step 2: Verify file is valid JSON**

Run: `cat src/main/resources/config/agents.json | python3 -m json.tool > /dev/null && echo "Valid JSON"`

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config/agents.json
git commit -m "feat: add agents.json configuration file for agent definitions"
```

---

### Task 2: Create AgentConfig and AgentsConfig Entity Classes

**Files:**
- Create: `src/main/java/com/msxf/agentscope/config/AgentConfig.java`
- Create: `src/main/java/com/msxf/agentscope/config/AgentsConfig.java`

- [ ] **Step 1: Create AgentConfig.java**

```java
package com.msxf.agentscope.config;

import java.util.ArrayList;
import java.util.List;

public class AgentConfig {

    private String agentId;
    private String name;
    private String description;
    private String systemPrompt;
    private String modelName = "qwen-plus";
    private boolean streaming = true;
    private boolean enableThinking = true;
    private List<String> skills = new ArrayList<>();
    private List<String> tools = new ArrayList<>();

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public boolean isStreaming() { return streaming; }
    public void setStreaming(boolean streaming) { this.streaming = streaming; }

    public boolean isEnableThinking() { return enableThinking; }
    public void setEnableThinking(boolean enableThinking) { this.enableThinking = enableThinking; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
}
```

- [ ] **Step 2: Create AgentsConfig.java**

```java
package com.msxf.agentscope.config;

import java.util.List;

public class AgentsConfig {

    private List<AgentConfig> agents;

    public List<AgentConfig> getAgents() { return agents; }
    public void setAgents(List<AgentConfig> agents) { this.agents = agents; }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/msxf/agentscope/config/
git commit -m "feat: add AgentConfig and AgentsConfig entity classes"
```

---

### Task 3: Create ToolRegistry

**Files:**
- Create: `src/main/java/com/msxf/agentscope/tool/ToolRegistry.java`

ToolRegistry maps tool/skill names to `Supplier<Object>` instances. It pre-registers all known tool-to-skill bindings from the existing codebase.

- [ ] **Step 1: Create ToolRegistry.java**

```java
package com.msxf.agentscope.tool;

import com.msxf.agentscope.model.SimpleTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, Supplier<Object>> registry = new ConcurrentHashMap<>();

    public ToolRegistry() {
        // Direct tool registrations (referenced by tools field in config)
        registry.put("SimpleTools", SimpleTools::new);

        // Skill-to-tool mappings (referenced by skills field in config)
        registry.put("docx", DocxParserTool::new);
        registry.put("pdf", PdfParserTool::new);
        registry.put("xlsx", XlsxParserTool::new);
        registry.put("docx-template", DocxParserTool::new);

        log.info("ToolRegistry initialized with {} mappings: {}", registry.size(), registry.keySet());
    }

    public void register(String name, Supplier<Object> supplier) {
        registry.put(name, supplier);
    }

    /**
     * Get a new tool instance by name.
     * @throws IllegalArgumentException if name is not registered
     */
    public Object getTool(String name) {
        Supplier<Object> supplier = registry.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Tool not registered: " + name);
        }
        return supplier.get();
    }

    public boolean hasTool(String name) {
        return registry.containsKey(name);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/tool/ToolRegistry.java
git commit -m "feat: add ToolRegistry for tool/skill name-to-instance mapping"
```

---

### Task 4: Create AgentConfigService

**Files:**
- Create: `src/main/java/com/msxf/agentscope/config/AgentConfigService.java`

Loads `agents.json` on startup, builds a lookup map, and provides query methods.

- [ ] **Step 1: Create AgentConfigService.java**

```java
package com.msxf.agentscope.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class AgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);

    private final Map<String, AgentConfig> configMap = new LinkedHashMap<>();
    private final List<AgentConfig> allAgents = new ArrayList<>();

    @Value("classpath:config/agents.json")
    private Resource configFile;

    @PostConstruct
    public void init() {
        try (InputStream is = configFile.getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            AgentsConfig agentsConfig = mapper.readValue(is, AgentsConfig.class);

            for (AgentConfig config : agentsConfig.getAgents()) {
                if (config.getAgentId() == null || config.getAgentId().isBlank()) {
                    log.warn("Skipping agent config with missing agentId");
                    continue;
                }
                if (configMap.containsKey(config.getAgentId())) {
                    log.warn("Duplicate agentId: {}, using first occurrence", config.getAgentId());
                    continue;
                }
                configMap.put(config.getAgentId(), config);
                allAgents.add(config);
                log.info("Loaded agent config: {} ({})", config.getName(), config.getAgentId());
            }

            log.info("Loaded {} agent configurations", allAgents.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load agents.json configuration", e);
        }
    }

    /**
     * Get config by agentId.
     * @throws IllegalArgumentException if agentId not found
     */
    public AgentConfig getAgentConfig(String agentId) {
        AgentConfig config = configMap.get(agentId);
        if (config == null) {
            throw new IllegalArgumentException("Agent config not found: " + agentId);
        }
        return config;
    }

    public Optional<AgentConfig> findAgentConfig(String agentId) {
        return Optional.ofNullable(configMap.get(agentId));
    }

    public List<AgentConfig> getAllAgents() {
        return Collections.unmodifiableList(allAgents);
    }

    public boolean exists(String agentId) {
        return configMap.containsKey(agentId);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/config/AgentConfigService.java
git commit -m "feat: add AgentConfigService for loading and querying agent configs"
```

---

### Task 5: Create AgentFactory

**Files:**
- Create: `src/main/java/com/msxf/agentscope/config/AgentFactory.java`

Builds `ReActAgent` instances from `AgentConfig` using `ToolRegistry` for tool/skill resolution. This replaces all the hardcoded `switch` logic in the old `AgentService.createAgent()`.

- [ ] **Step 1: Create AgentFactory.java**

```java
package com.msxf.agentscope.config;

import com.msxf.agentscope.tool.ToolRegistry;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    private final AgentConfigService configService;
    private final ToolRegistry toolRegistry;

    public AgentFactory(AgentConfigService configService, ToolRegistry toolRegistry) {
        this.configService = configService;
        this.toolRegistry = toolRegistry;
    }

    public ReActAgent createAgent(String agentId) {
        AgentConfig config = configService.getAgentConfig(agentId);
        log.info("Creating agent: {} ({})", config.getName(), agentId);

        // Build model from config
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .stream(config.isStreaming())
                .enableThinking(config.isEnableThinking())
                .formatter(new DashScopeChatFormatter())
                .build();

        // Build agent
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(config.getName())
                .sysPrompt(config.getSystemPrompt())
                .model(model)
                .memory(new InMemoryMemory());

        Toolkit toolkit = new Toolkit();

        // Register tools directly (from tools field in config)
        for (String toolName : config.getTools()) {
            if (toolRegistry.hasTool(toolName)) {
                toolkit.registerTool(toolRegistry.getTool(toolName));
                log.info("  Registered tool: {} for agent: {}", toolName, agentId);
            } else {
                log.error("  Tool not found in registry: {} (agent: {})", toolName, agentId);
            }
        }

        // Register skills with their tool bindings (from skills field in config)
        if (!config.getSkills().isEmpty()) {
            SkillBox skillBox = new SkillBox(toolkit);
            try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
                for (String skillName : config.getSkills()) {
                    if (toolRegistry.hasTool(skillName)) {
                        skillBox.registration()
                                .skill(repo.getSkill(skillName))
                                .tool(toolRegistry.getTool(skillName))
                                .apply();
                        log.info("  Registered skill: {} for agent: {}", skillName, agentId);
                    } else {
                        log.error("  Tool for skill not found in registry: {} (agent: {})", skillName, agentId);
                    }
                }
            } catch (Exception e) {
                log.error("  Failed to load skills for agent: {}", agentId, e);
            }
            builder.toolkit(toolkit).skillBox(skillBox);
        } else {
            builder.toolkit(toolkit);
        }

        return builder.build();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/config/AgentFactory.java
git commit -m "feat: add AgentFactory for creating agents from configuration"
```

---

### Task 6: Refactor AgentService to Use AgentFactory

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/service/AgentService.java`

Remove all hardcoded agent creation logic. Replace with `AgentFactory` delegation. Change parameter name from `agentType` to `agentId`. Keep streaming logic unchanged.

- [ ] **Step 1: Rewrite AgentService.java**

Replace the entire file content with:

```java
package com.msxf.agentscope.service;

import com.msxf.agentscope.config.AgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentFactory agentFactory;
    private final ConcurrentHashMap<String, ReActAgent> agents = new ConcurrentHashMap<>();

    public AgentService(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    public ReActAgent getAgent(String agentId) {
        return agents.computeIfAbsent(agentId, agentFactory::createAgent);
    }

    public void streamToEmitter(String agentId, String message, String filePath, String fileName, SseEmitter emitter) {
        ReActAgent agent = getAgent(agentId);

        String actualMessage = message;
        if (filePath != null && !filePath.isBlank()) {
            String fileInfo = String.format("[用户上传了文件: %s, 路径: %s]\n\n", fileName, filePath);
            actualMessage = fileInfo + message;
        }

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(actualMessage).build())
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
                default -> {}
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

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/service/AgentService.java
git commit -m "refactor: simplify AgentService to use AgentFactory for agent creation"
```

---

### Task 7: Add Agent Listing API Endpoints and Update ChatController

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/controller/ChatController.java`

Add `AgentConfigService` dependency, add two new API endpoints (`GET /api/agents`, `GET /api/agents/{agentId}`), and update `sendMessage` to use `agentId` instead of `agentType`.

- [ ] **Step 1: Update ChatController imports and dependencies**

Add `AgentConfigService` import and autowired field. Add new imports for `ResponseEntity`, `Optional`, `List`, and `AgentConfig`:

In the import section, add:
```java
import com.msxf.agentscope.config.AgentConfig;
import com.msxf.agentscope.config.AgentConfigService;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
```

Add the dependency injection field after `private AgentService agentService;`:
```java
@Autowired
private AgentConfigService agentConfigService;
```

- [ ] **Step 2: Add agent listing endpoints**

Add these two endpoints before the `uploadFile` method:

```java
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
```

- [ ] **Step 3: Update sendMessage to use agentId**

In the `sendMessage` method, change line:
```java
String agentType = request.getOrDefault("agentType", "basic");
```
to:
```java
String agentId = request.getOrDefault("agentId", "chat.basic");
```

Also update the `streamAsync` call from `streamAsync(agentType, ...` to `streamAsync(agentId, ...`.

- [ ] **Step 4: Update streamAsync parameter name**

Change the `streamAsync` method signature from:
```java
public void streamAsync(String agentType, String message, String filePath,
                       String fileName, SseEmitter emitter, String sessionId) {
```
to:
```java
public void streamAsync(String agentId, String message, String filePath,
                       String fileName, SseEmitter emitter, String sessionId) {
```

And update the internal call from `agentService.streamToEmitter(agentType, ...` to `agentService.streamToEmitter(agentId, ...`.

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -q`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/msxf/agentscope/controller/ChatController.java
git commit -m "feat: add agent listing API endpoints, update to use agentId routing"
```

---

### Task 8: Frontend - Dynamic Agent Loading and agentId Routing

**Files:**
- Modify: `src/main/resources/templates/chat.html`

Remove hardcoded agent cards from HTML. Replace hardcoded `agents` JS object with dynamic loading from `/api/agents`. Update `selectAgent()` and `sendMessage()` to use agentId. Update file upload auto-switch.

- [ ] **Step 1: Remove hardcoded agent cards from HTML**

In the `<div class="agent-list">` section, remove all four hardcoded `<div class="agent-card">` elements (lines 1283-1311). The agent-list div should become empty:

```html
<div class="agent-list" id="agentList">
    <!-- Agent cards rendered dynamically by loadAgents() -->
</div>
```

- [ ] **Step 2: Update JavaScript state section**

Replace the entire state section (from `/* ===== STATE ===== */` through the `agents` const) with:

```javascript
/* ===== STATE ===== */
let currentAgent = null;
let isStreaming = false;
let currentEventSource = null;
let messageCount = 0;
let uploadedFile = null;

const agents = {};
```

- [ ] **Step 3: Add loadAgents and renderAgentCard functions**

Add these functions after the DOM REFERENCES section, before INPUT HANDLING:

```javascript
/* ===== LOAD AGENTS ===== */
async function loadAgents() {
    try {
        const response = await fetch('/api/agents');
        const agentList = await response.json();

        const agentListEl = document.getElementById('agentList');
        agentListEl.innerHTML = '';

        agentList.forEach(function(agent) {
            agents[agent.agentId] = {
                name: agent.name,
                desc: agent.description,
                config: agent
            };

            var card = document.createElement('div');
            card.className = 'agent-card';
            card.dataset.agentId = agent.agentId;
            card.onclick = function() { selectAgent(agent.agentId); };

            var namespace = agent.agentId.split('.')[0].toUpperCase();
            card.innerHTML =
                '<div class="agent-card-icon">' + namespace.substring(0, 2) + '</div>' +
                '<div class="agent-card-info">' +
                    '<div class="agent-card-name">' + escapeHtml(agent.name) + '</div>' +
                    '<div class="agent-card-desc">' + escapeHtml(agent.description) + '</div>' +
                '</div>';

            agentListEl.appendChild(card);
        });

        // Select first agent by default
        if (agentList.length > 0) {
            selectAgent(agentList[0].agentId);
        }
    } catch (err) {
        addDebugEntry('error', '[INIT] Failed to load agents: ' + err.message);
    }
}
```

- [ ] **Step 4: Update selectAgent function**

Replace the existing `selectAgent` function with:

```javascript
function selectAgent(agentId) {
    removeFile();
    if (isStreaming) return;
    if (!agents[agentId]) return;
    currentAgent = agentId;

    document.querySelectorAll('.agent-card').forEach(function(card) {
        card.classList.toggle('active', card.dataset.agentId === agentId);
    });

    chatHeaderName.textContent = agents[agentId].name;
    chatHeaderDesc.textContent = agents[agentId].desc;

    chatMessages.innerHTML = '';
    messageCount = 0;
    chatMessages.appendChild(chatEmpty);
    chatEmpty.style.display = 'flex';

    debugLog.innerHTML = '';
}
```

- [ ] **Step 5: Update sendMessage to use agentId**

In the `sendMessage` function, change the request body from:
```javascript
body: JSON.stringify({
    agentType: currentAgent,
    message: message,
    filePath: fileInfo ? fileInfo.filePath : null,
    fileName: fileInfo ? fileInfo.fileName : null
})
```
to:
```javascript
body: JSON.stringify({
    agentId: currentAgent,
    message: message,
    filePath: fileInfo ? fileInfo.filePath : null,
    fileName: fileInfo ? fileInfo.fileName : null
})
```

- [ ] **Step 6: Update file upload auto-switch**

In the `handleFileSelect` function, change:
```javascript
if (currentAgent !== 'task') {
    selectAgent('task');
}
```
to:
```javascript
if (currentAgent !== 'task.document-analysis') {
    selectAgent('task.document-analysis');
}
```

- [ ] **Step 7: Add loadAgents() call at initialization**

At the bottom of the script (in the `/* ===== INIT ===== */` section), replace:
```javascript
messageInput.focus();
```
with:
```javascript
loadAgents();
messageInput.focus();
```

- [ ] **Step 8: Verify compilation and test in browser**

Run: `mvn spring-boot:run`

Open http://localhost:8080 and verify:
1. Agent cards load dynamically from the API
2. First agent is auto-selected
3. Clicking an agent card switches the chat
4. Sending a message works (the agentId is sent to backend)

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: dynamic agent loading from API with agentId-based routing"
```

---

### Task 9: Frontend - Agent Configuration Viewer

**Files:**
- Modify: `src/main/resources/templates/chat.html`

Add a read-only config viewer modal that shows when clicking an info icon on an agent card. Matches the cyberpunk terminal theme.

- [ ] **Step 1: Add config viewer CSS**

Add these styles at the end of the existing `<style>` block (before `</style>`):

```css
/* ===== CONFIG VIEWER MODAL ===== */
.config-modal-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.7);
    z-index: 500;
    display: flex;
    align-items: center;
    justify-content: center;
    backdrop-filter: blur(4px);
    animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

.config-modal {
    width: 520px;
    max-width: 90vw;
    max-height: 80vh;
    background: var(--bg-card);
    border: 1px solid var(--border-accent);
    display: flex;
    flex-direction: column;
    box-shadow: 0 0 40px rgba(0, 245, 255, 0.2), inset 0 0 40px rgba(0, 245, 255, 0.03);
    clip-path: polygon(16px 0, 100% 0, 100% calc(100% - 16px), calc(100% - 16px) 100%, 0 100%, 0 16px);
    animation: modalIn 0.3s ease;
}

@keyframes modalIn {
    from { opacity: 0; transform: translateY(-20px) scale(0.95); }
    to { opacity: 1; transform: translateY(0) scale(1); }
}

.config-modal-header {
    padding: var(--space-lg) var(--space-xl);
    border-bottom: 1px solid var(--border-dim);
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.config-modal-title {
    font-family: var(--font-heading);
    font-size: 12px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 2px;
    color: var(--neon-cyan);
    text-shadow: 0 0 10px rgba(0, 245, 255, 0.4);
}

.config-modal-close {
    width: 28px;
    height: 28px;
    background: none;
    border: 1px solid var(--border-dim);
    color: var(--text-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    transition: all 0.2s ease;
    clip-path: polygon(4px 0, 100% 0, 100% calc(100% - 4px), calc(100% - 4px) 100%, 0 100%, 0 4px);
}

.config-modal-close:hover {
    border-color: var(--status-error);
    color: var(--status-error);
    box-shadow: 0 0 10px rgba(255, 71, 87, 0.3);
}

.config-modal-body {
    padding: var(--space-lg) var(--space-xl);
    overflow-y: auto;
    flex: 1;
}

.config-modal-body::-webkit-scrollbar {
    width: 4px;
}

.config-modal-body::-webkit-scrollbar-thumb {
    background: var(--border-dim);
    border-radius: 2px;
}

.config-field {
    margin-bottom: var(--space-md);
}

.config-field-label {
    font-size: 10px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 1px;
    color: var(--text-muted);
    margin-bottom: 4px;
}

.config-field-value {
    font-size: 12px;
    color: var(--text-primary);
    line-height: 1.5;
}

.config-field-value.mono {
    font-family: var(--font-body);
    background: var(--bg-void);
    padding: var(--space-sm) var(--space-md);
    border: 1px solid var(--border-dim);
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 150px;
    overflow-y: auto;
}

.config-field-value.tags {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
}

.config-tag {
    display: inline-block;
    padding: 2px var(--space-sm);
    background: rgba(0, 245, 255, 0.1);
    border: 1px solid var(--border-accent);
    font-size: 10px;
    color: var(--neon-cyan);
    clip-path: polygon(4px 0, 100% 0, 100% calc(100% - 4px), calc(100% - 4px) 100%, 0 100%, 0 4px);
}

.config-tag.tool {
    background: rgba(255, 184, 0, 0.1);
    border-color: rgba(255, 184, 0, 0.3);
    color: #FFB800;
}

.config-tag.none {
    background: rgba(88, 86, 112, 0.1);
    border-color: var(--border-dim);
    color: var(--text-muted);
}

.config-divider {
    border: none;
    border-top: 1px solid var(--border-dim);
    margin: var(--space-lg) 0;
}

/* Agent card info button */
.agent-card-info-btn {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 18px;
    height: 18px;
    background: none;
    border: 1px solid transparent;
    color: var(--text-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    opacity: 0;
    transition: all 0.2s ease;
    padding: 0;
    line-height: 1;
}

.agent-card:hover .agent-card-info-btn {
    opacity: 0.6;
}

.agent-card-info-btn:hover {
    opacity: 1 !important;
    border-color: var(--neon-cyan);
    color: var(--neon-cyan);
}
```

- [ ] **Step 2: Update renderAgentCard to include info button**

In the `loadAgents` function, update the card.innerHTML to include the info button. Replace the innerHTML assignment with:

```javascript
card.innerHTML =
    '<button class="agent-card-info-btn" onclick="event.stopPropagation(); showAgentConfig(\'' + agent.agentId + '\')" title="View config">i</button>' +
    '<div class="agent-card-icon">' + namespace.substring(0, 2) + '</div>' +
    '<div class="agent-card-info">' +
        '<div class="agent-card-name">' + escapeHtml(agent.name) + '</div>' +
        '<div class="agent-card-desc">' + escapeHtml(agent.description) + '</div>' +
    '</div>';
```

- [ ] **Step 3: Add showAgentConfig and closeConfigModal functions**

Add these functions after the `loadAgents` function:

```javascript
/* ===== CONFIG VIEWER ===== */
function showAgentConfig(agentId) {
    var agent = agents[agentId];
    if (!agent || !agent.config) return;

    var config = agent.config;

    var skillsHtml;
    if (config.skills && config.skills.length > 0) {
        skillsHtml = '<div class="config-field-value tags">' +
            config.skills.map(function(s) { return '<span class="config-tag">' + escapeHtml(s) + '</span>'; }).join('') +
            '</div>';
    } else {
        skillsHtml = '<div class="config-field-value tags"><span class="config-tag none">None</span></div>';
    }

    var toolsHtml;
    if (config.tools && config.tools.length > 0) {
        toolsHtml = '<div class="config-field-value tags">' +
            config.tools.map(function(t) { return '<span class="config-tag tool">' + escapeHtml(t) + '</span>'; }).join('') +
            '</div>';
    } else {
        toolsHtml = '<div class="config-field-value tags"><span class="config-tag none">None</span></div>';
    }

    var overlay = document.createElement('div');
    overlay.className = 'config-modal-overlay';
    overlay.id = 'configModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) closeConfigModal();
    };

    overlay.innerHTML =
        '<div class="config-modal">' +
            '<div class="config-modal-header">' +
                '<div class="config-modal-title">Agent Configuration</div>' +
                '<button class="config-modal-close" onclick="closeConfigModal()">&#x2715;</button>' +
            '</div>' +
            '<div class="config-modal-body">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Agent ID</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.agentId) + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Name</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.name) + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Description</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.description) + '</div>' +
                '</div>' +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Model</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.modelName) + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Streaming</div>' +
                    '<div class="config-field-value">' + (config.streaming ? 'Enabled' : 'Disabled') + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Thinking Mode</div>' +
                    '<div class="config-field-value">' + (config.enableThinking ? 'Enabled' : 'Disabled') + '</div>' +
                '</div>' +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">System Prompt</div>' +
                    '<div class="config-field-value mono">' + escapeHtml(config.systemPrompt || '') + '</div>' +
                '</div>' +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Skills</div>' +
                    skillsHtml +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Tools</div>' +
                    toolsHtml +
                '</div>' +
            '</div>' +
        '</div>';

    document.body.appendChild(overlay);
}

function closeConfigModal() {
    var modal = document.getElementById('configModal');
    if (modal) modal.remove();
}
```

- [ ] **Step 4: Add Escape key to close modal**

Add this event listener in the INIT section alongside `loadAgents()`:

```javascript
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') closeConfigModal();
});
```

- [ ] **Step 5: Verify in browser**

Run: `mvn spring-boot:run`

Open http://localhost:8080 and verify:
1. Hover over an agent card shows the "i" info button
2. Clicking "i" opens the config modal
3. Modal shows all config fields: Agent ID, Name, Description, Model, Streaming, Thinking, System Prompt, Skills, Tools
4. Skills and Tools show as styled tags (or "None")
5. Clicking overlay or X or pressing Escape closes the modal
6. Clicking the card itself still selects the agent (info button click does not propagate)

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: add agent configuration viewer modal to frontend"
```

---

### Task 10: Update CLAUDE.md Documentation

**Files:**
- Modify: `CLAUDE.md`

Update the project documentation to reflect the new architecture.

- [ ] **Step 1: Update the Architecture section in CLAUDE.md**

In the "Agent Types (AgentService)" table, add a note about the new config-driven architecture. Update the agent type section to explain that agents are now configured via `agents.json`:

Add a new section after "## Architecture" heading:

```markdown
### Agent Configuration (config/agents.json)

Agents are defined in `src/main/resources/config/agents.json` using namespace-format IDs (e.g., `chat.basic`, `task.document-analysis`).

**Configuration chain:** `agents.json` → `AgentConfigService` → `AgentFactory` → `AgentService` (caches instances)

Each agent config includes: `agentId`, `name`, `description`, `systemPrompt`, `modelName`, `streaming`, `enableThinking`, `skills[]`, `tools[]`.

**Adding a new agent:**
1. Add entry to `config/agents.json`
2. If it uses a new tool class, register it in `ToolRegistry` constructor
3. If it uses a new skill, create `skills/<name>/SKILL.md` and add mapping in `ToolRegistry`
4. Restart the application — the new agent appears automatically in the UI
```

- [ ] **Step 2: Update the Project Structure section**

Replace the project structure with the updated version that includes the new `config/` package:

```markdown
## Project Structure

```
src/main/java/com/msxf/agentscope/
├── AgentScopeDemoApplication.java    # Spring Boot entry point
├── config/
│   ├── AgentConfig.java              # Agent config entity
│   ├── AgentsConfig.java             # Root config wrapper
│   ├── AgentConfigService.java       # Config loading and query service
│   └── AgentFactory.java             # Agent creation from config
├── controller/
│   └── ChatController.java           # SSE chat + agent listing APIs
├── service/
│   └── AgentService.java             # Agent routing with instance cache
├── model/
│   └── SimpleTools.java              # Demo tools (@Tool annotated methods)
└── tool/
    ├── ToolRegistry.java             # Tool/skill name-to-instance mapping
    ├── DocxParserTool.java           # DOCX parsing via Apache POI
    └── PdfParserTool.java            # PDF parsing via Apache PDFBox

src/main/resources/
├── application.yml                   # Config (api-key, multipart limits, logging)
├── config/
│   └── agents.json                   # Agent definitions (all agents in one file)
├── skills/
│   ├── docx/SKILL.md                 # DOCX skill definition
│   ├── pdf/SKILL.md                  # PDF skill definition
│   ├── xlsx/SKILL.md                 # XLSX skill definition
│   └── docx-template/SKILL.md        # DOCX template skill definition
└── templates/
    └── chat.html                     # Single-page chat UI (vanilla JS + SSE)
```
```

- [ ] **Step 3: Update "Adding a New Skill" section**

Replace the existing section with:

```markdown
## Adding a New Agent

1. Add entry to `src/main/resources/config/agents.json`
2. If using a new tool class, create it in `tool/` with `@Tool` methods
3. Register the tool in `ToolRegistry` constructor: `register("toolName", ToolClass::new)`
4. If using skills, create `skills/<name>/SKILL.md` with YAML frontmatter
5. Register the skill-to-tool mapping in `ToolRegistry` constructor
6. Restart — agent appears in the UI automatically

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Chat UI page |
| GET | `/api/agents` | List all agent configurations |
| GET | `/api/agents/{agentId}` | Get specific agent configuration |
| POST | `/chat/send` | Send message (body: `{agentId, message, filePath?, fileName?}`) |
| GET | `/chat/stream?sessionId=` | SSE stream for responses |
| POST | `/chat/upload` | Upload file (multipart) |
| GET | `/chat/download?fileId=` | Download file |
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md to reflect new agent configuration architecture"
```

---

## Self-Review

**Spec coverage check:**
- [x] agents.json config file → Task 1
- [x] AgentConfig/AgentsConfig entity classes → Task 2
- [x] ToolRegistry with pre-registered tools → Task 3
- [x] AgentConfigService with JSON loading → Task 4
- [x] AgentFactory with agent creation → Task 5
- [x] AgentService refactored to use AgentFactory → Task 6
- [x] GET /api/agents endpoint → Task 7
- [x] GET /api/agents/{agentId} endpoint → Task 7
- [x] Frontend dynamic agent loading → Task 8
- [x] Frontend config viewer → Task 9
- [x] Error handling (404 for unknown agent, startup validation) → Tasks 4, 7
- [x] Documentation update → Task 10

**Placeholder scan:** No TBD, TODO, or placeholder patterns found.

**Type consistency check:** `AgentConfig` fields match JSON keys. `ToolRegistry.getTool()` returns `Object` (consistent with `Toolkit.registerTool(Object)`). `AgentFactory.createAgent(String agentId)` signature matches `AgentService.agents.computeIfAbsent(agentId, agentFactory::createAgent)`. Frontend `agentId` field in request body matches ChatController's `request.getOrDefault("agentId", ...)`.
