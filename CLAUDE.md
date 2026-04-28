# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.13 + Java 17 demo for AgentScope (v1.0.11), a Java agent framework with LLM-backed ReAct agents. Features multiple agent types: basic chat, tool-calling, document analysis, multi-modal support (vision/audio), RAG knowledge base, session management, web search, and multi-agent collaboration (sequential pipelines, routing, handoffs).

## Build & Run

```bash
# Build
mvn clean compile

# Run (requires DASHSCOPE_API_KEY env var)
export DASHSCOPE_API_KEY=your_key_here
mvn spring-boot:run

# Or with -D
mvn spring-boot:run -Dspring-boot.run.arguments="--agentscope.model.dashscope.api-key=your_key"
```

App runs on http://localhost:8080.

## Architecture

### Agent Configuration (config/agents.yml)

Agents are defined in `src/main/resources/config/agents.yml` using kebab-case IDs (e.g., `chat-basic`, `task-document-analysis`).

**Configuration chain:** `agents.yml` → `AgentConfigService` → `AgentFactory` → `AgentRuntimeFactory` → `AgentService` (caches instances)

Each agent config includes: `agentId`, `name`, `description`, `systemPrompt`, `modelName`, `streaming`, `enableThinking`, `modality` (text/vision/audio), `skills[]`, `userTools[]`, `systemTools[]`, `ragEnabled`, `autoContext`.

**Agent types:**
- **SINGLE**: Standard ReAct agent with tools/skills
- **SEQUENTIAL**: Executes sub-agents in series (output of one feeds into next)
- **PARALLEL**: Fanout pipeline where all sub-agents receive same message concurrently
- **ROUTING**: LLM intelligently routes to appropriate sub-agent based on request content
- **HANDOFFS**: Intent-based agent switching with explicit trigger rules (keywords/intent/explicit)

**Adding a new agent:**
1. Add entry to `config/agents.yml`
2. If it uses a new tool class, register it in `ToolRegistry` constructor
3. If it uses a new skill, create `skills/<name>/SKILL.md` and add mapping in `ToolRegistry`
4. Restart the application — the new agent appears automatically in the UI

### Tool Registration

Tools are POJOs with `@Tool` annotated methods. Parameters use `@ToolParam(name, description)`:

```java
@Tool(name = "parse_docx", description = "Parse a .docx file...")
public String parseDocx(
    @ToolParam(name = "filePath", description = "Absolute path...") String filePath
) { ... }
```

Register directly via `toolkit.registerTool(new SimpleTools())` or bind via SkillBox.

### Session Management

`SessionManagerService` manages conversation persistence:

- **SessionContext**: Holds `ReActAgent`, `Memory`, `SessionManager`
- **Storage**: JSON files in `${user.home}/.agentscope/demo-sessions/`
- **Lifecycle**: Create → Cache → Auto-save on completion
- **API**: `/api/sessions` for list/create/delete

Each session maintains its own agent instance with isolated memory.

### Knowledge Service (RAG)

`KnowledgeService` provides vector similarity search:

- **Embedding**: DashScope text-embedding-v3 (1024 dimensions)
- **Storage**: InMemoryStore for demo
- **Readers**: PDF, DOCX, TXT, MD
- **Retrieval**: Configurable limit and score threshold
- **API**: `/api/knowledge/upload` to add documents

### Reactive Streaming Architecture

`ChatController.sendMessage` returns `Flux<ServerSentEvent<String>>` directly. `AgentService.streamEvents` uses `AgentRuntime` which merges hook events with agent text stream:

**AgentRuntime lifecycle:**
1. `AgentRuntimeFactory.createRuntime(agentId)` creates fresh `ObservabilityHook` + `ReActAgent`
2. `AgentRuntime.stream(Msg)` returns `Flux<Map<String, Object>>` merging:
   - Hook events (timeline/metrics via `ObservabilityHook`)
   - Agent text stream (incremental `TextBlock` from `agent.stream()`)
3. `ChatController` converts events to SSE and completes the flux
4. `AgentRuntime.close()` cleans up hook consumer and completes sink

| Event Type | Source | Content | Frontend Display |
|------------|--------|---------|------------------|
| `agent_start` | Hook (PreCallEvent) | agent name, input count | Debug panel |
| `llm_start` | Hook (PreReasoningEvent) | model name, call number | Debug panel |
| `thinking` | Hook (ReasoningChunkEvent) | incremental thinking content | Debug panel |
| `llm_end` | Hook (PostReasoningEvent) | token usage, tool calls | Debug panel |
| `tool_start` | Hook (PreActingEvent) | tool name, params | Debug panel |
| `tool_end` | Hook (PostActingEvent) | tool result, duration | Debug panel |
| `agent_end` | Hook (PostCallEvent) | total LLM/tool calls, duration | Debug panel |
| `pipeline_start` | Multi-agent | pipeline ID, step count | Debug panel |
| `pipeline_step_start` | Multi-agent | step index, agent ID | Debug panel |
| `pipeline_step_end` | Multi-agent | step completion | Debug panel |
| `routing_decision` | Multi-agent | selected agent, reasoning | Debug panel |
| `handoff_start` | Multi-agent | from/to agent, reason | Debug panel |
| `text` | Agent stream | incremental response text | Main chat area |
| `error` | Hook/ErrorEvent | error message | Alert |

### ObservabilityHook

`hook/ObservabilityHook.java` captures the full agent lifecycle and emits structured events for the debug panel:

- **Timeline tracking**: agent_start → llm_start → thinking → llm_end → tool_start → tool_end → agent_end
- **Metrics collection**: token counts (input/output/total), LLM time, tool durations
- **Tool call details**: name, parameters, result preview, success status
- **Skill identification**: recognizes `load_skill_through_path` and extracts skill names
- **Multi-agent events**: pipeline, routing, and handoff tracking

### File Upload Flow

1. Frontend sends file to `POST /chat/upload` (MultipartFile)
2. Saved to `{java.io.tmpdir}/agentscope-uploads/{uuid}{ext}`
3. Response: `{fileId, fileName, filePath, fileType}`
4. Frontend stores in `uploadedFile`/`uploadedImages`/`uploadedAudio`, shows tag
5. Auto-switches agent based on file type (doc→task-agent, image→vision, audio→voice)
6. On send, file info included in `/chat/send` body as `filePath`/`fileName` or `images[]`/`audio`
7. `AgentService.streamEvents` prepends file info to message

**Supported formats:**
- Documents: .docx, .pdf, .xlsx
- Images: .jpg, .jpeg, .png, .gif, .webp
- Audio: .wav, .mp3, .m4a, .mp4

### Multi-Modal Support

**Vision agents** (`modality: vision`):
- Model: qwen-vl-max
- Input: Images via `images[]` array in ChatRequest
- Use cases: OCR, chart analysis, scene understanding, invoice/ID card extraction

**Audio agents** (`modality: audio`):
- Model: qwen-audio-turbo
- Input: Audio via `audio` object in ChatRequest
- Use cases: Speech-to-text, voice interaction

### Multi-Agent Collaboration

`CompositeAgentFactory` creates multi-agent compositions:

**Sequential Pipeline** (`type: SEQUENTIAL`):
- Sub-agents execute in series
- Output of one agent feeds into the next
- Example: `doc-analysis-pipeline` → doc-expert → search-expert

**Parallel Pipeline** (`type: PARALLEL`):
- All sub-agents receive the same message
- Execute concurrently (configurable via `parallel` flag)
- Results are aggregated

**Routing Agent** (`type: ROUTING`):
- LLM intelligently selects which sub-agent to handle the request
- Sub-agents registered as `SubAgentTool` instances
- System prompt includes sub-agent descriptions for routing decisions
- Example: `smart-router` routes to doc-expert/search-expert/vision-expert/sales-expert

**Handoffs Agent** (`type: HANDOFFS`):
- Intent-based agent switching with explicit trigger rules
- Trigger types: `INTENT` (keywords match), `EXPLICIT` (user requests)
- Example: `customer-service` handoffs to sales-agent on "价格/购买" keywords

**Configuration format:**
```yaml
agentId: smart-router
type: ROUTING
subAgents:
  - agentId: doc-expert
    description: 文档分析专家
  - agentId: search-expert
    description: 搜索专家

# OR for handoffs
agentId: customer-service
type: HANDOFFS
subAgents:
  - agentId: sales-agent
    description: 销售顾问
handoffTriggers:
  - type: INTENT
    keywords: ["购买", "价格"]
    target: sales-agent
```

### Bank Invoice Generator

Specialized agent (`bank-invoice`) that generates Excel and Word documents from templates:

- **Tool**: `BankInvoiceTool.generateInvoice()` with 12 parameters (name, idCard, phone, email, contract, loan, date, amount, bankAmount, feeType, invoice, serial)
- **Templates**: Located in `skills/bank_invoice_java/assets/`
- **Features**: Automatic name desensitization in filenames (张三丰 → 张某某), auto-submission date in Word doc
- **Output**: Two files saved to `{java.io.tmpdir}/agentscope-uploads/`

### Frontend Architecture

Modular vanilla JavaScript with ES6 imports:

```
scripts/
├── chat.js              # Main entry point, event handlers
├── api.js               # API wrappers (fetch, SSE parsing)
├── state.js             # Global state with getters/setters
└── modules/
    ├── agents.js        # Agent list, selection, config modal
    ├── session.js       # Session list, create, delete, switch
    ├── knowledge.js     # Knowledge doc list, upload, remove
    ├── upload.js        # File upload handling
    ├── debug.js         # Debug panel, timeline, metrics
    ├── ui.js            # Message rendering, modals, typing indicator
    └── utils.js         # Utilities (markdown, escapeHtml, formatting)
```

**Key patterns:**
- State management via `state.js` with Object.defineProperty getters/setters
- Module imports (no bundler, native ES6)
- SSE streaming via `createSSEParser()`
- Global functions exposed via `window.functionName = functionName`

## Project Structure

```
src/main/java/com/skloda/agentscope/
├── AgentScopeDemoApplication.java    # Spring Boot entry point
├── agent/
│   ├── AgentConfig.java              # Agent config entity
│   ├── AgentConfigService.java       # Config loading and query service
│   ├── AgentFactory.java             # Single agent creation from config
│   ├── AgentType.java                # Enum: SINGLE, SEQUENTIAL, PARALLEL, ROUTING, HANDOFFS
│   ├── TriggerType.java              # Enum: INTENT, EXPLICIT (for handoff triggers)
│   ├── SubAgentConfig.java           # Sub-agent configuration with description
│   └── HandoffTrigger.java           # Handoff trigger rules (type, keywords, target)
├── composite/
│   └── CompositeAgentFactory.java    # Multi-agent composition factory (pipelines, routing, handoffs)
├── controller/
│   ├── ChatController.java           # Reactive SSE chat + file upload
│   └── KnowledgeController.java      # Knowledge base management API
├── service/
│   ├── AgentService.java             # Agent routing with instance cache
│   ├── SessionManagerService.java    # Session lifecycle management
│   └── KnowledgeService.java         # RAG knowledge base
├── model/
│   ├── ChatRequest.java              # Request payload (agentId, message, file info)
│   ├── ChatEvent.java                # SSE event wrapper (type, content)
│   ├── SessionInfo.java              # Session metadata
│   └── MultiModalMessage.java        # Multi-modal message wrapper
├── hook/
│   └── ObservabilityHook.java        # Hook for agent lifecycle events
├── runtime/
│   ├── AgentRuntime.java             # Runtime container (Agent + Hook + Sink)
│   ├── AgentRuntimeFactory.java      # Factory for AgentRuntime instances
│   └── PipelineAgentRuntime.java     # Runtime for sequential/parallel pipelines
├── config/
│   ├── CorrectSkillDiagnostic.java  # Skill loading diagnostic utility
│   ├── JarEnvironmentDiagnostic.java # JAR environment diagnostic
│   └── SkillFileSystemHelperDiagnosticRunner.java # Skill file system diagnostic
└── tool/
    ├── ToolRegistry.java             # Tool/skill name-to-instance mapping
    ├── SimpleTools.java              # Demo tools (@Tool annotated methods)
    ├── DocxParserTool.java           # DOCX parsing via Apache POI
    ├── PdfParserTool.java            # PDF parsing via Apache PDFBox
    ├── XlsxParserTool.java           # XLSX parsing via Apache POI
    ├── BankInvoiceTool.java          # Bank invoice generation
    └── WebSearchTool.java            # Web search (Tavily API: news, weather, stock, general search)

**WebSearchTool tools:**
- `web_search(query)`: General web search with Tavily API
- `get_current_weather(location)`: Get weather for a location
- `get_stock_price(symbol)`: Get stock price
- `get_news(category)`: Get latest news by category (Tavily API)

src/main/resources/
├── application.yml                   # Config (api-key, multipart limits, logging)
├── config/
│   └── agents.yml                    # Agent definitions (YAML format)
├── skills/
│   ├── docx/SKILL.md                 # DOCX skill definition
│   ├── pdf/SKILL.md                  # PDF skill definition
│   ├── xlsx/SKILL.md                 # XLSX skill definition
│   ├── docx-template/SKILL.md        # DOCX template skill definition
│   ├── bank_invoice_java/            # Bank invoice skill
│   │   ├── SKILL.md                  # Skill documentation
│   │   └── assets/                   # Template files
│   │       ├── bank_template.xlsx
│   │       └── bank_template.docx
│   └── bank_invoice/                 # Bank invoice skill (alternate version)
│       └── SKILL.md
├── static/
│   ├── scripts/                      # Frontend JavaScript modules
│   │   ├── chat.js                   # Main entry point
│   │   ├── api.js                    # API wrappers
│   │   ├── state.js                  # State management
│   │   └── modules/                  # Feature modules
│   │       ├── agents.js
│   │       ├── session.js
│   │       ├── knowledge.js
│   │       ├── upload.js
│   │       ├── debug.js
│   │       ├── ui.js
│   │       └── utils.js
│   ├── styles/                       # Modular CSS
│   │   ├── chat.css
│   │   └── modules/
│   │       ├── header.css
│   │       ├── sidebar.css
│   │       ├── chat.css
│   │       ├── debug.css
│   │       ├── modal.css
│   │       └── upload.css
│   └── vendor/                       # Third-party libraries
│       └── js/
│           ├── marked.min.js         # Markdown parser
│           └── highlight.min.js      # Syntax highlighting
└── templates/
    └── chat.html                     # Single-page chat UI (vanilla JS + SSE)
```

## Available Agent Types

### Single Agents
- **chat-basic**: Simple conversational AI with thinking enabled
- **tool-test-simple**: Time, calculator, and weather tools
- **task-document-analysis**: Document analysis (.docx, .pdf, .xlsx)
- **task-template-docx-editor**: Word template variable replacement
- **bank-invoice**: Bank invoice generator (Excel + Word)
- **rag-chat**: RAG-based knowledge base Q&A
- **vision-analyzer**: Image understanding (OCR, charts, scenes)
- **voice-assistant**: Speech-to-text voice assistant
- **invoice-extractor**: Invoice information extraction from images
- **idcard-extractor**: ID card information extraction from images
- **search-assistant**: Web search assistant (news, weather, stocks)
- **project-planner**: Project planning and task breakdown

### Expert Agents (for multi-agent compositions)
- **doc-expert**: Document parsing and analysis
- **search-expert**: Real-time web information retrieval
- **vision-expert**: Image understanding and OCR
- **sales-expert**: Product consultation and pricing
- **support-agent**: General customer service
- **sales-agent**: Sales consultation
- **complaint-agent**: Complaint handling

### Multi-Agent Compositions
- **doc-analysis-pipeline** (SEQUENTIAL): Document parsing → info search
- **smart-router** (ROUTING): Intelligently routes to doc/search/vision/sales experts
- **customer-service** (HANDOFFS): Intent-based handoffs to support/sales/complaint agents

## Adding a New Agent

### Single Agent
1. Add entry to `src/main/resources/config/agents.yml`
2. If using a new tool class, create it in `tool/` with `@Tool` methods
3. Register the tool in `ToolRegistry` constructor: `registry.put("toolName", ToolClass::new)`
4. If using skills, create `skills/<name>/SKILL.md` with YAML frontmatter
5. Register the skill-to-tool mapping in `ToolRegistry` constructor
6. Restart — agent appears in the UI automatically

### Multi-Agent Composition
1. Create expert agents first (as single agents)
2. Add composition agent with `type: SEQUENTIAL|PARALLEL|ROUTING|HANDOFFS`
3. Define `subAgents` list with `agentId` and `description`
4. For HANDOFFS type, add `handoffTriggers` with `type`, `keywords`, and `target`
5. Restart — composition agent appears in UI with sub-agent dispatch logic

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Chat UI page |
| POST | `/chat/send` | Send message, returns `Flux<ServerSentEvent<String>>` (body: `{agentId, message, sessionId?, filePath?, fileName?, images[]?, audio?}`) |
| POST | `/chat/upload` | Upload file (multipart), returns `{fileId, fileName, filePath, fileType}` |
| GET | `/chat/download?fileId=` | Download file |
| GET | `/api/agents` | List all agent configurations |
| GET | `/api/agents/{agentId}` | Get specific agent configuration |
| GET | `/api/skills/{skillName}` | Get skill documentation |
| GET | `/api/tools/{toolName}` | Get tool documentation |
| GET | `/api/sessions` | List all sessions |
| POST | `/api/sessions` | Create new session (body: `{agentId}`) |
| DELETE | `/api/sessions/{sessionId}` | Delete session |
| GET | `/api/knowledge/documents` | List knowledge base documents |
| POST | `/api/knowledge/upload` | Upload document to knowledge base (multipart) |
| DELETE | `/api/knowledge/documents/{fileName}` | Remove document from knowledge base |

## Dependencies

- `agentscope-spring-boot-starter` 1.0.11
- `agentscope-core` 1.0.11
- Apache POI 5.5.1 (DOCX/XLSX parsing and generation)
- Apache PDFBox 3.0.7 (PDF parsing)
- Spring Boot 3.5.13
- Project Reactor (for reactive streaming)

## Configuration

Key config in `application.yml`:
- `agentscope.model.dashscope.api-key`: Set via `DASHSCOPE_API_KEY` env var
- `agentscope.session.storage-path`: Default `${user.home}/.agentscope/demo-sessions`
- `agentscope.knowledge.dimensions`: Vector embedding dimensions (default 1024)
- `spring.servlet.multipart.max-file-size`: 50MB (file upload limit)
- `logging.level.io.agentscope: DEBUG` for AgentScope logs

## Debugging

**Frontend debugging:**
- Open browser DevTools Console
- Look for `[upload]`, `[api]`, `[SSE]` prefixed logs
- Check Network tab for SSE stream and upload requests

**Backend debugging:**
- Check logs for `Session {} saved` messages
- Enable `DEBUG` logging for `io.agentscope`
- Monitor AgentScope hook events in console

**Common issues:**
- File upload not working: Check browser console for CORS or network errors
- Agent not appearing: Verify `agents.yml` syntax and tool registration
- Session not persisting: Check file permissions for session directory

## Auxiliary Directories

### `agent-harness/`
Experimental agent harness implementations (not part of main Spring Boot app).

### `skills/`
Python-based skill development environment for AgentScope skills (separate from Java resources under `src/main/resources/skills/`).
