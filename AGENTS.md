# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.14 + Java 17 demo for AgentScope (v2.0.0 GA), a Java agent framework with LLM-backed ReAct agents. Features multiple agent types: basic chat, tool-calling, document analysis, template-based document generation (Bank Invoice), RAG knowledge base, multi-modal (vision/audio), multi-agent collaboration (10 patterns), and Harness capabilities (Docker sandbox, Plan Mode, Task List, layered memory, skill self-learning, context compaction, OTel tracing).

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

### Agent Configuration (config/agents.yml + harness-agents.yml)

Agents are defined in `src/main/resources/config/agents.yml` and `harness-agents.yml` using kebab-case IDs (e.g., `chat-basic`, `plan-build-demo`).

**Configuration chain:** `agents.yml` → `AgentConfigService` → `AgentFactory` / `HarnessAgentFactory` → `AgentRuntimeFactory` → `AgentService` / `HarnessAgentService` (caches instances)

Each agent config includes: `agentId`, `name`, `description`, `systemPrompt`, `modelName`, `streaming`, `enableThinking`, `skills[]`, `userTools[]`, `systemTools[]`. Harness agents additionally support `harnessConfig` with: `executionMode`, `filesystemMode`, `compaction`, `memory`, `plan`, `taskListEnabled`, `skillLearning`, `permissionConfig`, `sandbox`, etc.

**Adding a new agent:**
1. Add entry to `config/agents.yml` (or `harness-agents.yml` for HARNESS type)
2. If it uses a new tool class, register it in `ToolRegistry` constructor
3. If it uses a new skill, create `skills/<name>/SKILL.md` and add mapping in `ToolRegistry`
4. For Harness agents, configure `harnessConfig` fields (see `HarnessConfig.java` for all options)
5. Restart the application — the new agent appears automatically in the UI

### Model Creation (ModelFactory)

All agent factories use `ModelFactory` (`model/ModelFactory.java`) as the unified model entry point. It wraps `ModelRegistry.resolve("dashscope:<modelName>", context)` with auto-prefixing and `ModelCreationContext` (apiKey, stream, enableThinking, DashScopeChatFormatter). DashScope provider auto-registers via Java SPI.

### Tool Registration

Tools are POJOs with `@Tool` annotated methods. Parameters use `@ToolParam(name, description)`:

```java
@Tool(name = "parse_docx", description = "Parse a .docx file...")
public String parseDocx(
    @ToolParam(name = "filePath", description = "Absolute path...") String filePath
) { ... }
```

Register directly via `toolkit.registerTool(new SimpleTools())` or bind via SkillRepository.

### Reactive Streaming Architecture (AgentScope 2.0)

`ChatController.sendMessage` returns `Flux<ServerSentEvent<String>>` directly. `AgentRuntime.stream(Msg)` uses `agent.streamEvents()` (2.0 native event stream) merged with `EventSink` for multi-agent events:

**AgentRuntime lifecycle:**
1. `AgentRuntimeFactory.createRuntime(agentId)` creates `ObservabilityHook` + `ReActAgent`
2. `AgentRuntime.stream(Msg)` returns `Flux<Map<String, Object>>` merging:
   - Agent lifecycle events (from `agent.streamEvents()`)
   - Manual multi-agent events (from `EventSink` for pipeline/routing/handoff)
3. `AgentEventMapper` converts `AgentEvent` → SSE `Map<String, Object>` (30+ event types)
4. `ChatController` converts events to SSE and completes the flux

### HarnessAgent Architecture

`HarnessAgentFactory` (@Component) builds `HarnessAgent` instances with 17+ builder capabilities wired from `HarnessConfig`:
- workspace, model (via ModelFactory), compaction, toolResultEviction
- filesystem (Docker sandbox / Local), memory (layered MemoryConfig)
- enablePlanMode, enableTaskList, enableMetaTool
- permissionContext, skillRepository
- enableSkillManageTool + enableSkillCurator (skill self-learning)
- maxContextTokens, additionalContextFile

**HarnessRuntime** uses `agent.stream()` (not `streamEvents()`) due to official GA gap: `streamEvents()` does not forward sub-agent events.

### Frontend (Modular JS + SSE)

Frontend is modular: `static/scripts/chat.js` (main SSE handler, 56 case branches) + `static/scripts/modules/` (debug.js, ui.js, agents.js, session.js, upload.js, knowledge.js, utils.js).

Handles: agent lifecycle, LLM tokens, tool calls, pipeline/handoff/routing/loop/debate events, plan/todo tools, require_user_confirm/approval HITL, source-based subagent output differentiation, memory compression, structured data.

### File Upload Flow

1. Frontend sends file to `POST /chat/upload` (MultipartFile)
2. Saved to `{java.io.tmpdir}/agentscope-uploads/{uuid}{ext}`
3. Response: `{fileId, fileName, filePath}`
4. Frontend stores in `uploadedFile`, shows tag, auto-switches to task agent
5. On send, `filePath` + `fileName` included in `/chat/send` body

### Bank Invoice Generator

Specialized agent (`bank-invoice`) that generates Excel and Word documents from templates:

- **Tool**: `BankInvoiceTool.generateInvoice()` with 12 parameters
- **Templates**: Located in `skills/bank_invoice_java/assets/`
- **Features**: Automatic name desensitization in filenames, auto-submission date
- **Output**: Two files saved to `{java.io.tmpdir}/agentscope-uploads/`

## Project Structure

```
src/main/java/com/skloda/agentscope/
├── AgentScopeDemoApplication.java
├── agent/
│   ├── AgentConfig.java              # Agent config entity (+ HarnessConfig, nested configs)
│   ├── AgentConfigService.java       # Config loading (agents.yml + harness-agents.yml)
│   └── AgentFactory.java             # SINGLE/ROUTING/HANDOFFS agent creation
├── composite/
│   ├── CompositeAgentFactory.java    # Multi-agent patterns (routing, handoffs, pipelines)
│   └── graph/                        # State graph (order fulfillment)
├── config/
│   └── TracingConfig.java            # OTel TracerRegistry initialization
├── controller/
│   └── ChatController.java           # Reactive SSE chat + file upload
├── harness/
│   ├── HarnessAgentFactory.java      # @Component, builds HarnessAgent (17+ builder capabilities)
│   ├── HarnessAgentService.java      # Harness agent routing + cache
│   ├── HarnessRuntime.java           # Harness stream runtime (uses agent.stream())
│   ├── FilesystemSpecFactory.java    # Local/Docker filesystem spec factory
│   ├── CompactionConfigFactory.java  # Compaction + ToolResultEviction config factory
│   └── WorkspaceInitializer.java     # Workspace template initialization
├── hook/
│   └── ObservabilityHook.java        # EventSink-based observability
├── mcp/
│   └── McpClientService.java         # MCP client management
├── middleware/
│   ├── MiddlewareRegistry.java       # Middleware name→factory registry (incl. otel-tracing)
│   ├── ApprovalMiddleware.java       # HITL approval (consumes RequireUserConfirmEvent)
│   └── (audit/metrics/ratelimit/context middlewares)
├── model/
│   ├── ModelFactory.java             # ModelRegistry unified entry point
│   ├── ChatRequest.java
│   └── ChatEvent.java
├── permission/
│   └── PermissionContextFactory.java # PermissionContextState builder
├── runtime/
│   ├── AgentRuntime.java             # SINGLE runtime (agent.streamEvents + EventSink)
│   ├── AgentRuntimeFactory.java      # Runtime factory (all agent types)
│   ├── AgentEventMapper.java         # AgentEvent → SSE Map (30+ types)
│   └── MultiAgentStreamSupport.java  # Pipeline/routing/handoff stream utilities
├── service/
│   └── AgentService.java             # Agent routing with instance cache
├── schema/
│   └── (structured output schemas)
└── tool/
    ├── ToolRegistry.java             # Tool/skill name-to-instance mapping
    ├── SimpleTools.java              # Demo tools
    ├── DocxParserTool.java           # DOCX parsing (Apache POI)
    ├── PdfParserTool.java            # PDF parsing (Apache PDFBox)
    ├── XlsxParserTool.java           # XLSX parsing (Apache POI)
    └── BankInvoiceTool.java          # Bank invoice generation
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Chat UI page |
| POST | `/chat/send` | Send message, returns `Flux<ServerSentEvent<String>>` |
| POST | `/chat/upload` | Upload file (multipart) |
| GET | `/chat/download?fileId=` | Download file |

## Dependencies

- `agentscope-spring-boot-starter` 2.0.0
- `agentscope-core` 2.0.0
- `agentscope-harness` 2.0.0
- `agentscope-extensions-model-dashscope` 2.0.0 (DashScope provider, RC5 modularized)
- `agentscope-extensions-rag-simple` 2.0.0
- `agentscope-extensions-memory-bailian` 2.0.0
- Apache POI 5.5.1, Apache PDFBox 3.0.7
- Spring Boot 3.5.14
- Project Reactor

## Configuration

Key config in `application.yml`:
- `agentscope.model.dashscope.api-key`: Set via `DASHSCOPE_API_KEY` env var
- `spring.servlet.multipart.max-file-size`: 50MB
- `logging.level.io.agentscope: DEBUG` for AgentScope logs
