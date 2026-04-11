# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.3.6 + Java 21 demo for AgentScope (v1.0.11), a Java agent framework with LLM-backed ReAct agents. Features three agent types: basic chat, tool-calling, and task-based document analysis with file parsing skills.

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

### Agent Configuration (config/agents.json)

Agents are defined in `src/main/resources/config/agents.json` using namespace-format IDs (e.g., `chat.basic`, `task.document-analysis`).

**Configuration chain:** `agents.json` → `AgentConfigService` → `AgentFactory` → `AgentService` (caches instances)

Each agent config includes: `agentId`, `name`, `description`, `systemPrompt`, `modelName`, `streaming`, `enableThinking`, `skills[]`, `tools[]`.

**Adding a new agent:**
1. Add entry to `config/agents.json`
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

### SSE Streaming Architecture

`ChatController.sendMessage` returns a `sessionId`; frontend polls `/chat/stream?sessionId=...` for events. `AgentService.streamToEmitter` subscribes to `agent.stream()` and converts events to SSE JSON:

| Event Type | Content | Frontend Display |
|------------|---------|------------------|
| `thinking` | reasoning content | Debug panel |
| `text` | agent response text | Main chat area |
| `tool_call` | tool name + params | Debug panel |
| `tool_result` | tool output | Debug panel |
| `error` | error message | Alert |
| `done` | empty | Ends stream |

### File Upload Flow

1. Frontend sends file to `POST /chat/upload` (MultipartFile)
2. Saved to `{java.io.tmpdir}/agentscope-uploads/{uuid}{ext}`
3. Response: `{fileId, fileName, filePath}`
4. Frontend stores in `uploadedFile`, shows tag, auto-switches to task agent
5. On send, `filePath` + `fileName` included in `/chat/send` body
6. `AgentService.streamToEmitter` prepends file info to message (e.g., `[用户上传了文件: x.docx, 路径: /tmp/...]`)

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
    ├── PdfParserTool.java            # PDF parsing via Apache PDFBox
    └── XlsxParserTool.java           # XLSX parsing via Apache POI

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

## Adding a New Agent

1. Add entry to `src/main/resources/config/agents.json`
2. If using a new tool class, create it in `tool/` with `@Tool` methods
3. Register the tool in `ToolRegistry` constructor: `registry.put("toolName", ToolClass::new)`
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

## Dependencies

- `agentscope-spring-boot-starter` 1.0.11
- `agentscope-core` 1.0.11
- Apache POI 5.3.0 (DOCX parsing)
- Apache PDFBox 3.0.4 (PDF parsing)

## Configuration

Key config in `application.yml`:
- `agentscope.model.dashscope.api-key`: Set via `DASHSCOPE_API_KEY` env var
- `spring.servlet.multipart.max-file-size`: 50MB (file upload limit)
- `logging.level.io.agentscope: DEBUG` for AgentScope logs
