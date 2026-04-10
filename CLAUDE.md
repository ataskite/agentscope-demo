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

### Agent Types (AgentService)

| Type | Name | Tools/Skills | Use Case |
|------|------|--------------|----------|
| `basic` | Assistant | none | Simple conversation |
| `tool` | ToolAgent | SimpleTools (get_current_time, calculate_sum, get_weather) | Tool calling demo |
| `task` | TaskAgent | DocxParserTool, PdfParserTool (via SkillBox) | Document analysis |

Agents are cached in `ConcurrentHashMap<String, ReActAgent>`, created lazily via `createAgent()`.

### SkillBox + ClasspathSkillRepository Pattern

The `task` agent uses **SkillBox** for progressive tool disclosure:

1. Skills are markdown files at `src/main/resources/skills/<skill-name>/SKILL.md` with YAML frontmatter (`name`, `description`)
2. `ClasspathSkillRepository("skills")` loads skills from classpath (AutoCloseable)
3. `skillBox.registration().skill(repo.getSkill("x")).tool(new XTool()).apply()` binds a skill to a tool instance
4. The agent's `Toolkit` is shared between SkillBox and direct tool registration

```java
SkillBox skillBox = new SkillBox(toolkit);
try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
    skillBox.registration()
        .skill(repo.getSkill("docx"))
        .tool(new DocxParserTool())
        .apply();
}
builder.toolkit(toolkit).skillBox(skillBox);
```

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
├── controller/
│   └── ChatController.java           # SSE chat + file upload endpoints
├── service/
│   └── AgentService.java             # Agent factory, streaming logic
├── model/
│   └── SimpleTools.java              # Demo tools (@Tool annotated methods)
└── tool/
    ├── DocxParserTool.java           # DOCX parsing via Apache POI
    └── PdfParserTool.java            # PDF parsing via Apache PDFBox

src/main/resources/
├── application.yml                   # Config (api-key, multipart limits, logging)
├── skills/
│   ├── docx/SKILL.md                 # DOCX skill definition (YAML + markdown)
│   └── pdf/SKILL.md                  # PDF skill definition
└── templates/
    └── chat.html                     # Single-page chat UI (vanilla JS + SSE)
```

## Adding a New Skill

1. Create tool class in `src/main/java/com/msxf/agentscope/tool/` with `@Tool` methods
2. Create `src/main/resources/skills/<name>/SKILL.md` with YAML frontmatter
3. Add skill registration in `AgentService.createAgent()`'s `task` case:
   ```java
   skillBox.registration()
       .skill(repo.getSkill("name"))
       .tool(new YourTool())
       .apply();
   ```

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
