# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.14 + Java 17 demo for AgentScope (v2.0.0 GA), a Java agent framework with LLM-backed ReAct agents. Features multiple agent types: basic chat, tool-calling, document analysis, multi-modal support (vision/audio), RAG knowledge base, session management (AgentStateStore), web search, multi-agent collaboration (10 patterns: sequential, parallel, routing, handoffs, debate, loop, state graph, msg hub, subagents-sequential, subagents-parallel), and advanced capabilities via the AgentScope Harness (Docker sandbox, Plan Mode, Task List, layered memory, skill self-learning, context compaction, OTel tracing). Uses `ModelRegistry` unified model entry and `agent.streamEvents()` for typed event streaming (345 tests, 0 failures).

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

Register directly via `toolkit.registerTool(new SimpleTools())` or bind skills via `ClasspathSkillRepository` + `DynamicSkillMiddleware` (the 2.0 replacement for the legacy SkillBox API).

### Session Management

`SessionManagerService` manages conversation persistence on top of the 2.0 `AgentStateStore` API:

- **State store**: `AgentStateStore` implementations (`InMemoryAgentStateStore`, `JsonFileAgentStateStore`) replace the legacy `Session`/`SessionManager` API
- **SessionContext**: Holds the `ReActAgent`, memory, and bound `AgentStateStore`
- **Storage**: JSON files in `${user.home}/.agentscope/demo-sessions/` (via `JsonFileAgentStateStore`)
- **Lifecycle**: Create → Cache → Auto-save on completion
- **API**: `/api/sessions` for list/create/delete

Each session maintains its own agent instance with isolated memory and state.

### Knowledge Service (RAG)

`KnowledgeService` provides vector similarity search:

- **Embedding**: DashScope text-embedding-v3 (1024 dimensions)
- **Storage**: InMemoryStore for demo
- **Readers**: PDF, DOCX, TXT, MD
- **Retrieval**: Configurable limit and score threshold
- **API**: `/api/knowledge/upload` to add documents

### Reactive Streaming Architecture

`ChatController.sendMessage` returns `Flux<ServerSentEvent<String>>` directly. `AgentService.streamEvents` uses `AgentRuntime`, which merges automatic agent lifecycle events (from `agent.streamEvents()` returning `Flux<AgentEvent>`) with manual multi-agent events emitted via `EventSink`.

**AgentRuntime lifecycle (AgentScope 2.0):**
1. `AgentRuntimeFactory.createRuntime(agentId)` creates a fresh `ObservabilityHook` (now an EventSink bridge, not a `Hook` implementation) + `ReActAgent`
2. `AgentRuntime.stream(Msg)` returns `Flux<Map<String, Object>>` merging:
   - **Automatic lifecycle events** produced by `agent.streamEvents()` and mapped from `AgentEvent` types (`AGENT_START`, `LLM_START`, `REASONING_DELTA`, `LLM_END`, `TOOL_CALL_START`, `TOOL_CALL_END`, `AGENT_END`, `TEXT_BLOCK_DELTA`, ...)
   - **Manual multi-agent events** published through `EventSink` (pipeline, routing, handoff, loop, graph, msg hub, subagent task events)
3. `ChatController` converts the merged events to SSE and completes the flux
4. `AgentRuntime.close()` cleans up sink consumers and completes the flux

> Note: The deprecated `agent.stream()` (raw `TextBlock` iterator) is no longer used; `streamEvents()` is the single source of agent-side events.

| Event Type | Source | Content | Frontend Display |
|------------|--------|---------|------------------|
| `agent_start` | AgentEvent (AGENT_START) | agent name, input count | Debug panel |
| `llm_start` | AgentEvent (LLM_START) | model name, call number | Debug panel |
| `thinking` | AgentEvent (REASONING_DELTA) | incremental thinking content | Debug panel |
| `llm_end` | AgentEvent (LLM_END) | token usage, tool calls | Debug panel |
| `tool_start` | AgentEvent (TOOL_CALL_START) | tool name, params | Debug panel |
| `tool_end` | AgentEvent (TOOL_CALL_END) | tool result, duration | Debug panel |
| `agent_end` | AgentEvent (AGENT_END) | total LLM/tool calls, duration | Debug panel |
| `pipeline_start` | EventSink (Multi-agent) | pipeline ID, step count | Debug panel |
| `pipeline_step_start` | EventSink (Multi-agent) | step index, agent ID | Debug panel |
| `pipeline_step_end` | EventSink (Multi-agent) | step completion | Debug panel |
| `routing_decision` | EventSink (Multi-agent) | selected agent, reasoning | Debug panel |
| `handoff_start` | EventSink (Multi-agent) | from/to agent, reason | Debug panel |
| `loop_start` | EventSink (Loop runtime) | iteration number | Debug panel |
| `loop_end` | EventSink (Loop runtime) | total iterations, final status | Debug panel |
| `loop_iteration_result` | EventSink (Loop runtime) | iteration, approved status | Debug panel |
| `graph_transition` | EventSink (StateGraph runtime) | from state, to state, trigger | Debug panel |
| `graph_agent_call` | EventSink (StateGraph runtime) | state name, agent ID | Debug panel |
| `roundtable_start` | EventSink (MsgHub runtime) | participants, rounds | Debug panel |
| `round_start` | EventSink (MsgHub runtime) | round number | Debug panel |
| `round_end` | EventSink (MsgHub runtime) | round completion | Debug panel |
| `round_message` | EventSink (MsgHub runtime) | agent, message content | Debug panel |
| `roundtable_summary` | EventSink (MsgHub runtime) | summary content | Debug panel |
| `task_delegate` | EventSink (Subagent runtime) | from, to, task | Debug panel |
| `task_start` | EventSink (Subagent runtime) | agent ID | Debug panel |
| `task_end` | EventSink (Subagent runtime) | agent, output preview | Debug panel |
| `task_aggregate` | EventSink (Subagent runtime) | total tasks | Debug panel |
| `text` | AgentEvent (TEXT_BLOCK_DELTA) | incremental response text | Main chat area |
| `error` | AgentEvent / EventSink | error message | Alert |

### ObservabilityHook (EventSink bridge)

`hook/ObservabilityHook.java` is no longer an AgentScope `Hook` implementation. In 2.0 it is a thin bridge that exposes an `EventSink` used to publish **manual** multi-agent events for the debug panel. Automatic per-agent lifecycle events (LLM, reasoning, tool calls, text deltas) are emitted directly by `agent.streamEvents()` as `AgentEvent`s and processed by `AgentRuntime`.

- **Timeline & metrics**: derived from `AgentEvent` types in `AgentRuntime` (AGENT_START → LLM_START → REASONING_DELTA → LLM_END → TOOL_CALL_START → TOOL_CALL_END → AGENT_END)
- **Manual multi-agent events**: pipeline, routing, handoff, loop, state graph, msg hub (roundtable), and subagent task events are forwarded to the `EventSink` and merged into the SSE stream
- **Skill identification**: recognizes `load_skill_through_path` and extracts skill names

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

`CompositeAgentFactory` creates multi-agent compositions. The legacy `Pipeline<Msg>` interface was removed in 2.0; each collaboration pattern is now backed by a dedicated `StreamingAgentRuntime` implementation:

- **SEQUENTIAL** → `SequentialRuntime`
- **PARALLEL** → `ParallelRuntime`
- **DEBATE** → `DebateRuntime`
- **LOOP** → `LoopRuntime`
- **MSG_HUB** → `MsgHubRuntime`
- **SUBAGENT_SEQ** → `SubAgentSeqRuntime`
- **SUBAGENT_PAR** → `SubAgentParRuntime`
- **ROUTING / HANDOFFS** → `AgentRuntime` with sub-agent tool binding
- **STATE_GRAPH** → `StateGraphRuntime` (driven by `OrderFulfillmentGraph`)

**Sequential Pipeline** (`type: SEQUENTIAL`, `SequentialRuntime`):
- Sub-agents execute in series
- Output of one agent feeds into the next
- Example: `doc-analysis-pipeline` → doc-expert → search-expert

**Parallel Pipeline** (`type: PARALLEL`, `ParallelRuntime`):
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

**Loop Pipeline** (`type: LOOP`, `LoopRuntime`):
- Write-review-revise pattern with iterative refinement
- Writer produces content, critic reviews, loop continues until quality threshold or max iterations
- Example: `copywriter-refiner` → writer → critic → (optional revision)

**StateGraph** (`type: STATE_GRAPH`, `StateGraphRuntime`):
- Custom state machine with mixed deterministic and agent-driven transitions
- Event-driven transitions (user actions) and condition-driven transitions (agent decisions)
- Example: `order-fulfillment` → CREATED → SUBMITTED → REVIEWING → APPROVED → PAID → DONE

**MsgHub RoundTable** (`type: MSG_HUB`, `MsgHubRuntime`):
- Multi-round expert discussion with moderator synthesis
- Each expert speaks in sequence across multiple rounds, seeing all previous messages
- Example: `expert-roundtable` → architect, DBA, security-expert discuss, moderator summarizes

**Subagents Sequential** (`type: SUBAGENT_SEQ`, `SubAgentSeqRuntime`):
- TaskOrchestrator pattern with sequential task handoff and {prevOutput} chaining
- Each step receives previous step's output via template variables
- Example: `report-generator` → research → analysis → report writing

**Subagents Parallel** (`type: SUBAGENT_PAR`, `SubAgentParRuntime`):
- TaskDispatcher pattern with parallel task delegation and result aggregation
- All sub-agents receive same input concurrently, results collected and combined
- Example: `project-manager` → research, design, evaluation run in parallel

### AgentScope Harness (advanced demos)

Two `type: HARNESS` agents demonstrate 2.0 capabilities wired up through `HarnessAgentFactory` + `HarnessRuntime` and configured via `harness/CompactionConfigFactory` and `harness/FilesystemSpecFactory`:

- **compaction-demo**: Long conversations with automatic context compaction (CLAW execution mode, LOCAL filesystem) — triggers compaction after N messages while keeping the most recent K messages.
- **sandbox-demo**: Safe code execution in a local sandbox (BUILDER execution mode, `/tmp/agentscope-sandbox` workspace) — runs user-supplied code through an isolated filesystem backend.

> Note: AgUI (S15), A2A (S12), distributed state store (S13), and Channel/feishu (S14) extensions are all now integrated — the extension jars were confirmed on Maven Central at 2.0.0 GA (2026-07-10). Each is profile-gated (`--spring.profiles.active=a2a|redis|mysql|postgresql|feishu|agui`). See ROADMAP.md §第六部分 for details.

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
│   ├── AgentType.java                # Enum: SINGLE, SEQUENTIAL, PARALLEL, ROUTING, HANDOFFS, DEBATE, LOOP, STATE_GRAPH, MSG_HUB, SUBAGENT_SEQ, SUBAGENT_PAR, HARNESS
│   ├── TriggerType.java              # Enum: INTENT, EXPLICIT (for handoff triggers)
│   ├── SubAgentConfig.java           # Sub-agent configuration with description
│   └── HandoffTrigger.java           # Handoff trigger rules (type, keywords, target)
│   ├── LoopConfig.java               # Loop pipeline configuration
│   ├── StateConfig.java              # StateGraph state configuration
│   ├── StateTransition.java          # StateGraph transition definition
│   └── MsgHubConfig.java             # MsgHub roundtable configuration
├── composite/
│   ├── CompositeAgentFactory.java    # Multi-agent composition factory (all 10 patterns)
│   └── graph/
│       └── OrderFulfillmentGraph.java     # Custom state machine example
├── controller/
│   ├── ChatController.java           # Reactive SSE chat + file upload
│   └── KnowledgeController.java      # Knowledge base management API
├── service/
│   ├── AgentService.java             # Agent routing with instance cache
│   ├── SessionManagerService.java    # Session lifecycle management (AgentStateStore-backed)
│   ├── ApprovalService.java          # Human-in-the-loop approval workflow
│   ├── WorkflowRunService.java       # Workflow run tracking
│   ├── ChatHistoryRepository.java + InMemoryChatHistoryRepository.java  # Chat history
│   ├── KnowledgeService.java         # RAG knowledge base
│   └── KnowledgeProperties.java      # Knowledge config properties
├── model/
│   ├── ChatRequest.java              # Request payload (agentId, message, file info)
│   ├── ChatEvent.java                # SSE event wrapper (type, content)
│   ├── SessionInfo.java              # Session metadata
│   └── MultiModalMessage.java        # Multi-modal message wrapper
├── hook/
│   ├── ObservabilityHook.java        # EventSink bridge for manual multi-agent events (no longer a Hook impl)
│   └── ApprovalHook.java             # Human-in-the-loop approval hook
├── runtime/
│   ├── AgentRuntime.java             # Runtime container (Agent + ObservabilityHook + EventSink)
│   ├── StreamingAgentRuntime.java    # Common interface for stream-based runtimes
│   ├── StructuredOutputAgentRuntime.java + StructuredOutputValidator.java  # Structured output runtime
│   ├── AgentRuntimeFactory.java      # Factory for all runtime types (11 patterns incl. HARNESS)
│   ├── SequentialRuntime.java        # Runtime for SEQUENTIAL pipeline
│   ├── ParallelRuntime.java          # Runtime for PARALLEL pipeline
│   ├── DebateRuntime.java            # Runtime for DEBATE pattern
│   ├── LoopRuntime.java              # Runtime for LOOP (write-review-revise) pattern
│   ├── MsgHubRuntime.java            # Runtime for MSG_HUB roundtable pattern
│   ├── SubAgentSeqRuntime.java       # Runtime for SUBAGENT_SEQ orchestration
│   ├── SubAgentParRuntime.java       # Runtime for SUBAGENT_PAR dispatch
│   ├── StateGraphRuntime.java        # Runtime for STATE_GRAPH agents
│   └── EventSink.java                # Manual multi-agent event publisher (Flux-backed)
├── harness/
│   ├── HarnessAgentFactory.java      # Factory for HARNESS-type agents (compaction, sandbox)
│   ├── HarnessAgentService.java      # Service wrapper for harness agents
│   ├── HarnessRuntime.java           # Runtime for harness agents
│   ├── CompactionConfigFactory.java  # Builds compaction config for long-conversation agents
│   ├── FilesystemSpecFactory.java    # Builds sandbox filesystem spec
│   └── WorkspaceInitializer.java     # Initializes sandbox workspace
├── middleware/
│   ├── MiddlewareRegistry.java       # Middleware chain registry (DynamicSkillMiddleware, etc.)
│   ├── AuditLoggingMiddleware.java   # Audit logging middleware
│   ├── DetailedAuditMiddleware.java  # Detailed audit middleware
│   ├── ContextEnrichmentMiddleware.java  # Context enrichment middleware
│   ├── MetricsCollectorMiddleware.java   # Metrics collection middleware
│   └── RateLimitMiddleware.java      # Rate limiting middleware
├── mcp/                              # MCP (Model Context Protocol) integration
│   ├── McpClientService.java         # MCP client
│   ├── McpDemoServer.java            # Demo MCP server
│   ├── McpServerConfig.java          # MCP server configuration
│   ├── McpServersWrapper.java        # MCP servers wrapper
│   ├── McpTransport.java             # MCP transport abstraction
│   └── ToolGroupConfig.java          # Tool group configuration
├── permission/
│   └── PermissionContextFactory.java # Permission context factory
├── schema/
│   ├── ContractMetadata.java         # Contract metadata schema
│   ├── IDCardData.java               # ID card data schema
│   └── InvoiceData.java              # Invoice data schema
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
- **rag-chat / rag-agent**: RAG-based knowledge base Q&A
- **vision-analyzer**: Image understanding (OCR, charts, scenes)
- **voice-assistant**: Speech-to-text voice assistant
- **invoice-extractor**: Invoice information extraction from images
- **idcard-extractor**: ID card information extraction from images
- **contract-extractor**: Contract metadata extraction
- **contract-review-workflow**: Multi-step contract review workflow
- **search-assistant**: Web search assistant (news, weather, stocks)
- **project-planner**: Project planning and task breakdown
- **long-conversation**: Long-context conversation agent
- **personal-assistant**: Personal assistant agent

### Harness Demos (AgentScope 2.0)
- **compaction-demo** (`type: HARNESS`): Long-conversation demo with automatic context compaction
- **sandbox-demo** (`type: HARNESS`): Safe code execution in a local sandbox (BUILDER mode)

### Expert Agents (for multi-agent compositions)
- **doc-expert**: Document parsing and analysis
- **search-expert**: Real-time web information retrieval
- **vision-expert**: Image understanding and OCR
- **sales-expert**: Product consultation and pricing
- **support-agent**: General customer service
- **sales-agent**: Sales consultation
- **complaint-agent**: Complaint handling
- **writer**: Copywriting and content refinement
- **critic**: Content quality review and feedback
- **moderator**: Discussion facilitation and synthesis
- **architect**: System architecture and design review
- **dba-expert**: Database and storage optimization
- **security-expert**: Security and compliance assessment
- **order-reviewer**: Order validation and approval
- **payment-agent**: Payment processing
- **shipping-agent**: Logistics and delivery
- **researcher**: Topic research and investigation
- **analyst**: Data analysis and insights
- **report-writer**: Report generation
- **pm-researcher**: Project feasibility research
- **pm-designer**: Technical solution design
- **pm-evaluator**: Risk assessment

### Multi-Agent Compositions
**Phase 3 Patterns:**
- **doc-analysis-pipeline** (SEQUENTIAL): Document parsing → info search
- **smart-router** (ROUTING): Intelligently routes to doc/search/vision/sales experts
- **customer-service** (HANDOFFS): Intent-based handoffs to support/sales/complaint agents
- **debate-review** (DEBATE): Multi-expert parallel debate with judge synthesis

**P6 Advanced Patterns:**
- **copywriter-refiner** (LOOP): Write → review → revise loop until quality met
- **order-fulfillment** (STATE_GRAPH): Order submission → review → payment → shipping flow
- **expert-roundtable** (MSG_HUB): Multi-round expert discussion with moderator summary
- **report-generator** (SUBAGENT_SEQ): Research → analysis → report writing pipeline
- **project-manager** (SUBAGENT_PAR): Parallel research, design, and evaluation

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
2. Add composition agent with `type: SEQUENTIAL|PARALLEL|ROUTING|HANDOFFS|DEBATE|LOOP|STATE_GRAPH|MSG_HUB|SUBAGENT_SEQ|SUBAGENT_PAR|HARNESS`
3. Define `subAgents` list with `agentId` and `description`
4. For HANDOFFS type, add `handoffTriggers` with `type`, `keywords`, and `target`
5. For LOOP type, add `loopConfig` with `maxIterations` and `exitCondition`
6. For STATE_GRAPH type, add `states` list with name, agent, and transitions
7. For MSG_HUB type, add `msgHubConfig` with `rounds` and `summaryRole`
8. For SUBAGENT_SEQ/PAR type, add `taskTemplate` to each subAgent for task delegation
9. For HARNESS type, add `harnessConfig` with `executionMode`, `isolationScope`, `filesystemMode`, optional `workspace`, and `compaction` settings
10. Restart — composition agent appears in UI with sub-agent dispatch logic

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

- `agentscope-spring-boot-starter` 2.0.0
- `agentscope-core` 2.0.0
- `agentscope-harness` 2.0.0 (Docker sandbox, Plan Mode, Task List, layered memory, skill self-learning, compaction)
- `agentscope-extensions-model-dashscope` 2.0.0 (DashScope provider, RC5 modularized from core)
- `agentscope-extensions-rag-simple` 2.0.0 (v1 RAG API, @Deprecated — awaiting v2 rewrite)
- `agentscope-extensions-memory-bailian` 2.0.0 (v1 LTM API, @Deprecated — Harness MemoryConfig is GA replacement)
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
- Monitor `AgentEvent`s (from `agent.streamEvents()`) and `EventSink` payloads in console

**Common issues:**
- File upload not working: Check browser console for CORS or network errors
- Agent not appearing: Verify `agents.yml` syntax and tool registration
- Session not persisting: Check file permissions for session directory

## Auxiliary Directories

### `agent-harness/`
Experimental agent harness implementations (not part of main Spring Boot app). Harness-type demo agents live under `src/main/java/.../harness/` and are configured in `src/main/resources/config/harness-agents.yml`.

### `skills/`
Python-based skill development environment for AgentScope skills (separate from Java resources under `src/main/resources/skills/`).
