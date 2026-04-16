# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.13 + Java 17 demo for AgentScope (v1.0.11), a Java agent framework with LLM-backed ReAct agents. Features multiple agent types: basic chat, tool-calling, document analysis, and template-based document generation (Bank Invoice).

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

Each agent config includes: `agentId`, `name`, `description`, `systemPrompt`, `modelName`, `streaming`, `enableThinking`, `skills[]`, `userTools[]`, `systemTools[]`.

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

### Reactive Streaming Architecture

`ChatController.sendMessage` returns `Flux<ServerSentEvent<String>>` directly — no session management needed. `AgentService.streamEvents` uses `AgentRuntime` which merges hook events with agent text stream:

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
| `text` | Agent stream | incremental response text | Main chat area |
| `error` | Hook/ErrorEvent | error message | Alert |

### ObservabilityHook

`hook/ObservabilityHook.java` captures the full agent lifecycle and emits structured events for the debug panel:

- **Timeline tracking**: agent_start → llm_start → thinking → llm_end → tool_start → tool_end → agent_end
- **Metrics collection**: token counts (input/output/total), LLM time, tool durations
- **Tool call details**: name, parameters, result preview, success status
- **Skill identification**: recognizes `load_skill_through_path` and extracts skill names

### File Upload Flow

1. Frontend sends file to `POST /chat/upload` (MultipartFile)
2. Saved to `{java.io.tmpdir}/agentscope-uploads/{uuid}{ext}`
3. Response: `{fileId, fileName, filePath}`
4. Frontend stores in `uploadedFile`, shows tag, auto-switches to task agent
5. On send, `filePath` + `fileName` included in `/chat/send` body
6. `AgentService.streamEvents` prepends file info to message (e.g., `[用户上传了文件: x.docx, 路径: /tmp/...]`)

### Bank Invoice Generator

Specialized agent (`bank-invoice`) that generates Excel and Word documents from templates:

- **Tool**: `BankInvoiceTool.generateInvoice()` with 12 parameters (name, idCard, phone, email, contract, loan, date, amount, bankAmount, feeType, invoice, serial)
- **Templates**: Located in `skills/bank_invoice_java/assets/`
- **Features**: Automatic name desensitization in filenames (张三丰 → 张某某), auto-submission date in Word doc
- **Output**: Two files saved to `{java.io.tmpdir}/agentscope-uploads/`

## Project Structure

```
src/main/java/com/msxf/agentscope/
├── AgentScopeDemoApplication.java    # Spring Boot entry point
├── agent/
│   ├── AgentConfig.java              # Agent config entity
│   ├── AgentConfigService.java       # Config loading and query service
│   └── AgentFactory.java             # Agent creation from config
├── controller/
│   └── ChatController.java           # Reactive SSE chat + file upload
├── service/
│   └── AgentService.java             # Agent routing with instance cache
├── model/
│   ├── ChatRequest.java              # Request payload (agentId, message, file info)
│   └── ChatEvent.java                # SSE event wrapper (type, content)
├── hook/
│   └── ObservabilityHook.java        # Hook for agent lifecycle events
├── runtime/
│   ├── AgentRuntime.java             # Runtime container (Agent + Hook + Sink)
│   └── AgentRuntimeFactory.java      # Factory for AgentRuntime instances
└── tool/
    ├── ToolRegistry.java             # Tool/skill name-to-instance mapping
    ├── SimpleTools.java              # Demo tools (@Tool annotated methods)
    ├── DocxParserTool.java           # DOCX parsing via Apache POI
    ├── PdfParserTool.java            # PDF parsing via Apache PDFBox
    ├── XlsxParserTool.java           # XLSX parsing via Apache POI
    └── BankInvoiceTool.java          # Bank invoice generation

src/main/resources/
├── application.yml                   # Config (api-key, multipart limits, logging)
├── config/
│   └── agents.yml                    # Agent definitions (YAML format)
├── skills/
│   ├── docx/SKILL.md                 # DOCX skill definition
│   ├── pdf/SKILL.md                  # PDF skill definition
│   ├── xlsx/SKILL.md                 # XLSX skill definition
│   ├── docx-template/SKILL.md        # DOCX template skill definition
│   └── bank_invoice_java/            # Bank invoice skill
│       ├── SKILL.md                  # Skill documentation
│       └── assets/                   # Template files
│           ├── bank_template.xlsx
│           └── bank_template.docx
└── templates/
    └── chat.html                     # Single-page chat UI (vanilla JS + SSE)
```

## Adding a New Agent

1. Add entry to `src/main/resources/config/agents.yml`
2. If using a new tool class, create it in `tool/` with `@Tool` methods
3. Register the tool in `ToolRegistry` constructor: `registry.put("toolName", ToolClass::new)`
4. If using skills, create `skills/<name>/SKILL.md` with YAML frontmatter
5. Register the skill-to-tool mapping in `ToolRegistry` constructor
6. Restart — agent appears in the UI automatically

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Chat UI page |
| POST | `/chat/send` | Send message, returns `Flux<ServerSentEvent<String>>` (body: `{agentId, message, filePath?, fileName?}`) |
| POST | `/chat/upload` | Upload file (multipart), returns `{fileId, fileName, filePath}` |
| GET | `/chat/download?fileId=` | Download file |

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
- `spring.servlet.multipart.max-file-size`: 50MB (file upload limit)
- `logging.level.io.agentscope: DEBUG` for AgentScope logs
