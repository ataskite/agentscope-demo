# Agent Skill 文档解析功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 AgentScope Demo 中集成 Agent Skill 机制，支持上传 DOCX/PDF 文件并通过 Task Agent 自动加载技能解析文件内容。

**Architecture:** 新增 Task Agent（带 SkillBox 的 ReActAgent），技能以精简 SKILL.md 形式存放于 classpath（`src/main/resources/skills/`），用 ClasspathSkillRepository 加载。DocxParserTool 和 PdfParserTool 分别用 Apache POI 和 PDFBox 纯 Java 解析，通过 SkillBox.registration() 绑定到对应技能实现渐进式披露。前端新增上传按钮和 Task Agent 卡片。

**Tech Stack:** Spring Boot 3.3.6, AgentScope 1.0.11, Apache POI 5.3.0, Apache PDFBox 3.0.4, Thymeleaf

---

## File Structure

| 操作 | 文件 | 职责 |
|------|------|------|
| Create | `src/main/resources/skills/docx/SKILL.md` | DOCX 技能指令，指导 LLM 调用 parse_docx 工具 |
| Create | `src/main/resources/skills/pdf/SKILL.md` | PDF 技能指令，指导 LLM 调用 parse_pdf 工具 |
| Create | `src/main/java/com/msxf/agentscope/tool/DocxParserTool.java` | 用 Apache POI 解析 .docx 文件的 Tool 类 |
| Create | `src/main/java/com/msxf/agentscope/tool/PdfParserTool.java` | 用 Apache PDFBox 解析 .pdf 文件的 Tool 类 |
| Modify | `pom.xml` | 添加 POI、PDFBox 依赖 |
| Modify | `src/main/java/com/msxf/agentscope/service/AgentService.java` | 新增 "task" agent 类型，含 SkillBox + 技能加载 |
| Modify | `src/main/java/com/msxf/agentscope/controller/ChatController.java` | 新增 `/chat/upload` 端点，修改 `/chat/send` 支持文件路径 |
| Modify | `src/main/resources/templates/chat.html` | 上传按钮、Task Agent 卡片、文件上传 JS 逻辑 |
| Modify | `src/main/resources/application.yml` | multipart 配置 |

---

### Task 1: Add Maven Dependencies

**Files:**
- Modify: `pom.xml:28-68` (dependencies block)

- [x] **Step 1: Add POI and PDFBox dependencies to pom.xml**

在 `pom.xml` 的 `<dependencies>` 块中，在 `spring-boot-starter-test` 之前添加：

```xml
        <!-- Apache POI for DOCX parsing -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>

        <!-- Apache PDFBox for PDF parsing -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.4</version>
        </dependency>
```

- [x] **Step 2: Verify dependencies resolve**

Run: `cd /Users/jiangkun/Documents/workspace/agentscope-demo && mvn dependency:resolve -q`
Expected: BUILD SUCCESS, no errors

- [x] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add Apache POI and PDFBox dependencies for file parsing"
```

---

### Task 2: Create Skill Markdown Files

**Files:**
- Create: `src/main/resources/skills/docx/SKILL.md`
- Create: `src/main/resources/skills/pdf/SKILL.md`

- [x] **Step 1: Create skills directory and docx SKILL.md**

Create directory `src/main/resources/skills/docx/` and file `src/main/resources/skills/docx/SKILL.md`:

```markdown
---
name: docx
description: Use this skill when the user wants to read, analyze, or extract content from Word documents (.docx files). Triggers include: uploading a .docx file and asking to summarize, extract key points, translate, or analyze its content.
---

# DOCX Document Analysis

## Overview

A .docx file is a ZIP archive containing XML files. This skill provides a Java tool to parse .docx files and extract their full text content including headings, paragraphs, tables, and lists.

## Available Tool

### parse_docx

Extracts all text content from a .docx file.

**Parameters:**
- `filePath` (String, required): Absolute path to the .docx file on the server

**Returns:** Full text content of the document with structure preserved:
- Headings are marked with `#` prefix (level 1-6)
- Paragraphs are separated by blank lines
- Table content is formatted as rows
- List items are marked with bullets or numbers

## How to Use

1. The user uploads a .docx file — you will receive the file path in the message
2. Call `parse_docx` with the file path
3. Based on the extracted content, perform the user's requested task:
   - Summarize the document
   - Extract key points or action items
   - Answer questions about the content
   - Translate sections
   - Compare with other documents
```

- [x] **Step 2: Create pdf SKILL.md**

Create directory `src/main/resources/skills/pdf/` and file `src/main/resources/skills/pdf/SKILL.md`:

```markdown
---
name: pdf
description: Use this skill when the user wants to read or extract text from PDF files. If the user uploads a .pdf file or asks to analyze a PDF document, use this skill.
---

# PDF Document Analysis

## Overview

This skill provides a Java tool to parse PDF files and extract their text content from all pages.

## Available Tool

### parse_pdf

Extracts text content from a PDF file.

**Parameters:**
- `filePath` (String, required): Absolute path to the PDF file on the server

**Returns:** Extracted text content from all pages, including:
- Page-separated text
- Document metadata (title, author) if available

## How to Use

1. The user uploads a .pdf file — you will receive the file path in the message
2. Call `parse_pdf` with the file path
3. Based on the extracted content, perform the user's requested task:
   - Summarize the PDF content
   - Extract key information
   - Answer questions about the document
   - Compare multiple documents
```

- [x] **Step 3: Verify skill files are on classpath**

Run: `mvn process-resources -q && ls -la target/classes/skills/docx/ target/classes/skills/pdf/`
Expected: Both directories contain SKILL.md

- [x] **Step 4: Commit**

```bash
git add src/main/resources/skills/
git commit -m "feat: add docx and pdf skill markdown files for Task Agent"
```

---

### Task 3: Create DocxParserTool

**Files:**
- Create: `src/main/java/com/msxf/agentscope/tool/DocxParserTool.java`

- [x] **Step 1: Create the tool class**

Create file `src/main/java/com/msxf/agentscope/tool/DocxParserTool.java`:

```java
package com.msxf.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

public class DocxParserTool {

    private static final Logger log = LoggerFactory.getLogger(DocxParserTool.class);

    @Tool(name = "parse_docx", description = "Parse a .docx file and extract its full text content including headings, paragraphs, tables, and lists. Returns the document text with structure preserved.")
    public String parseDocx(
            @ToolParam(name = "filePath", description = "Absolute path to the .docx file") String filePath) {
        log.info("Parsing DOCX file: {}", filePath);
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = extractParagraph(paragraph);
                    if (!text.isEmpty()) {
                        sb.append(text).append("\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    String tableText = extractTable(table);
                    if (!tableText.isEmpty()) {
                        sb.append(tableText).append("\n\n");
                    }
                }
            }

            if (sb.isEmpty()) {
                return "The document appears to be empty or contains no readable text.";
            }

            return sb.toString().trim();

        } catch (IOException e) {
            log.error("Failed to parse DOCX file: {}", filePath, e);
            return "Error parsing DOCX file: " + e.getMessage();
        }
    }

    private String extractParagraph(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return "";
        }

        String style = paragraph.getStyle();
        if (style != null) {
            switch (style) {
                case "Heading1", "heading 1" -> {
                    return "# " + text;
                }
                case "Heading2", "heading 2" -> {
                    return "## " + text;
                }
                case "Heading3", "heading 3" -> {
                    return "### " + text;
                }
                case "Heading4", "heading 4" -> {
                    return "#### " + text;
                }
            }
        }

        if (paragraph.getNumFmt() != null) {
            int level = paragraph.getNumIlvl() != null ? paragraph.getNumIlvl().intValue() : 0;
            String indent = "  ".repeat(level);
            return indent + "- " + text;
        }

        return text;
    }

    private String extractTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder rowText = new StringBuilder();
            for (int i = 0; i < row.getTableCells().size(); i++) {
                XWPFTableCell cell = row.getTableCells().get(i);
                if (i > 0) {
                    rowText.append(" | ");
                }
                rowText.append(cell.getText().trim());
            }
            sb.append(rowText).append("\n");
        }
        return sb.toString().trim();
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/tool/DocxParserTool.java
git commit -m "feat: add DocxParserTool using Apache POI"
```

---

### Task 4: Create PdfParserTool

**Files:**
- Create: `src/main/java/com/msxf/agentscope/tool/PdfParserTool.java`

- [x] **Step 1: Create the tool class**

Create file `src/main/java/com/msxf/agentscope/tool/PdfParserTool.java`:

```java
package com.msxf.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class PdfParserTool {

    private static final Logger log = LoggerFactory.getLogger(PdfParserTool.class);

    @Tool(name = "parse_pdf", description = "Parse a PDF file and extract text content from all pages. Returns the extracted text with page numbers and document metadata if available.")
    public String parsePdf(
            @ToolParam(name = "filePath", description = "Absolute path to the PDF file") String filePath) {
        log.info("Parsing PDF file: {}", filePath);
        StringBuilder sb = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {

            PDDocumentInformation info = document.getDocumentInformation();
            if (info != null) {
                String title = info.getTitle();
                String author = info.getAuthor();
                if ((title != null && !title.isBlank()) || (author != null && !author.isBlank())) {
                    sb.append("Document Info:\n");
                    if (title != null && !title.isBlank()) {
                        sb.append("  Title: ").append(title).append("\n");
                    }
                    if (author != null && !author.isBlank()) {
                        sb.append("  Author: ").append(author).append("\n");
                    }
                    sb.append("\n");
                }
            }

            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            if (totalPages <= 3) {
                String text = stripper.getText(document);
                sb.append(text);
            } else {
                for (int i = 1; i <= totalPages; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(document);
                    if (pageText != null && !pageText.isBlank()) {
                        sb.append("--- Page ").append(i).append(" ---\n");
                        sb.append(pageText.trim()).append("\n\n");
                    }
                }
            }

            if (sb.isEmpty()) {
                return "The PDF appears to be empty or contains no extractable text (it may be a scanned document).";
            }

            return sb.toString().trim();

        } catch (IOException e) {
            log.error("Failed to parse PDF file: {}", filePath, e);
            return "Error parsing PDF file: " + e.getMessage();
        }
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/msxf/agentscope/tool/PdfParserTool.java
git commit -m "feat: add PdfParserTool using Apache PDFBox"
```

---

### Task 5: Update application.yml for Multipart Config

**Files:**
- Modify: `src/main/resources/application.yml`

- [x] **Step 1: Add multipart configuration**

在 `application.yml` 中 `spring:` 节点下添加 `servlet.multipart` 配置：

```yaml
spring:
  application:
    name: agentscope-demo
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

最终 `spring:` 块应为：

```yaml
spring:
  application:
    name: agentscope-demo
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

- [x] **Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add multipart file upload limits (50MB)"
```

---

### Task 6: Modify AgentService — Add Task Agent with SkillBox

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/service/AgentService.java`

- [x] **Step 1: Add imports for skill classes**

在 AgentService.java 顶部 import 区域添加：

```java

```

- [x] **Step 2: Add "task" case to createAgent method**

在 `createAgent` 方法的 `switch` 语句中，在 `case "tool"` 和 `default` 之间添加 `case "task"` 分支。

将 `switch` 块替换为：

```java
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(switch (agentType) {
                    case "tool" -> "ToolAgent";
                    case "task" -> "TaskAgent";
                    default -> "Assistant";
                })
                .sysPrompt(switch (agentType) {
                    case "tool" -> "You are a helpful AI assistant with access to various tools. " +
                            "Use the appropriate tools when needed to answer questions accurately. " +
                            "Always explain what you're doing when using tools.";
                    case "task" -> "You are a document analysis assistant. " +
                            "When the user uploads a document, use the appropriate skill to parse it " +
                            "and then fulfill the user's request based on the extracted content. " +
                            "Support .docx and .pdf file analysis.";
                    default -> "You are a helpful AI assistant. Be friendly and concise.";
                })
                .model(model)
                .memory(new InMemoryMemory());

        Toolkit toolkit = new Toolkit();

        switch (agentType) {
            case "tool" -> {
                toolkit.registerTool(new SimpleTools());
                builder.toolkit(toolkit);
            }
            case "task" -> {
                SkillBox skillBox = new SkillBox(toolkit);
                try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
                    skillBox.registration()
                            .skill(repo.getSkill("docx"))
                            .tool(new DocxParserTool())
                            .apply();
                    skillBox.registration()
                            .skill(repo.getSkill("pdf"))
                            .tool(new PdfParserTool())
                            .apply();
                } catch (Exception e) {
                    log.error("Failed to load skills from classpath", e);
                }
                builder.toolkit(toolkit).skillBox(skillBox);
            }
            default -> builder.toolkit(toolkit);
        }

        return builder.build();
```

- [x] **Step 3: Modify streamToEmitter to support file path**

将 `streamToEmitter` 方法签名改为支持文件信息：

```java
    public void streamToEmitter(String agentType, String message, String filePath, String fileName, SseEmitter emitter) {
```

修改 `Msg` 构建部分，将文件信息拼入用户消息：

```java
        String actualMessage = message;
        if (filePath != null && !filePath.isBlank()) {
            String fileInfo = String.format("[用户上传了文件: %s, 路径: %s]\n\n", fileName, filePath);
            actualMessage = fileInfo + message;
        }

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(actualMessage).build())
                .build();
```

- [x] **Step 4: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/msxf/agentscope/service/AgentService.java
git commit -m "feat: add TaskAgent with SkillBox and skill-based file parsing"
```

---

### Task 7: Modify ChatController — Add Upload Endpoint

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/controller/ChatController.java`

- [x] **Step 1: Add multipart imports**

在 import 区域添加：

```java
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
```

- [x] **Step 2: Add upload endpoint**

在 `stream` 方法之后添加上传端点：

```java
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
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".pdf")) {
            return Map.of("error", "Only .docx and .pdf files are supported");
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
```

- [x] **Step 3: Modify sendMessage to support file params**

将 `sendMessage` 方法的请求解析部分更新，提取 `filePath` 和 `fileName`，并传递给 `agentService.streamToEmitter`：

```java
    @PostMapping("/chat/send")
    @ResponseBody
    public Map<String, String> sendMessage(@RequestBody Map<String, String> request) {
        String agentType = request.getOrDefault("agentType", "basic");
        String message = request.get("message");
        String filePath = request.get("filePath");
        String fileName = request.get("fileName");

        if (message == null || message.isBlank()) {
            return Map.of("error", "Message cannot be empty");
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(300_000L);

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

        executor.submit(() -> {
            try {
                agentService.streamToEmitter(agentType, message, filePath, fileName, emitter);
            } catch (Exception e) {
                log.error("Error during agent streaming", e);
                emitter.completeWithError(e);
            }
        });

        return Map.of("sessionId", sessionId);
    }
```

- [x] **Step 4: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/msxf/agentscope/controller/ChatController.java
git commit -m "feat: add file upload endpoint and pass file info to AgentService"
```

---

### Task 8: Update Frontend chat.html

**Files:**
- Modify: `src/main/resources/templates/chat.html`

这是最大的改动，涉及 CSS、HTML 和 JS 三部分。

- [x] **Step 1: Add upload button CSS**

在 `</style>` 标签之前添加上传相关样式：

```css
        /* ===== FILE UPLOAD ===== */
        .upload-btn {
            width: 40px;
            height: 40px;
            min-width: 40px;
            background: var(--bg-input);
            border: 1.5px solid var(--border-light);
            border-radius: var(--radius);
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            color: var(--text-secondary);
            transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
        }

        .upload-btn:hover {
            border-color: var(--accent);
            color: var(--accent);
        }

        .file-tag {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 10px;
            background: var(--accent-subtle);
            border: 1px solid var(--accent);
            border-radius: 20px;
            font-size: 12px;
            color: var(--accent);
            margin-bottom: 6px;
            max-width: 280px;
        }

        .file-tag-name {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .file-tag-remove {
            cursor: pointer;
            font-size: 14px;
            line-height: 1;
            opacity: 0.7;
        }

        .file-tag-remove:hover {
            opacity: 1;
        }
```

- [x] **Step 2: Add Task Agent card to sidebar**

在 agent-list 的 Tool Calling 卡片之后添加 Task Agent 卡片：

```html
                    <div class="agent-card" data-agent="task" onclick="selectAgent('task')">
                        <div class="agent-card-icon">&#x1F4C4;</div>
                        <div class="agent-card-info">
                            <div class="agent-card-name">Task Agent</div>
                            <div class="agent-card-desc">Document analysis with file parsing skills</div>
                        </div>
                    </div>
```

- [x] **Step 3: Replace input area HTML with upload-capable version**

将 `<div class="chat-input-area">` 整个块替换为：

```html
                <div class="chat-input-area">
                    <div id="fileTagArea"></div>
                    <div class="chat-input-row">
                        <button class="upload-btn" id="uploadBtn" onclick="document.getElementById('fileInput').click()" title="Upload file">&#x1F4CE;</button>
                        <input type="file" id="fileInput" accept=".docx,.pdf" style="display:none" onchange="handleFileSelect(this)">
                        <textarea class="chat-input" id="messageInput" placeholder="Type your message..." rows="1"></textarea>
                        <button class="send-btn" id="sendBtn" onclick="sendMessage()">Send</button>
                    </div>
                </div>
```

- [x] **Step 4: Add file upload state and handlers to JS**

在 `<script>` 的 `/* ===== STATE ===== */` 部分添加上传状态：

```javascript
        let uploadedFile = null; // { fileId, fileName, filePath }
```

在 `const agents = { ... }` 中添加 task agent：

```javascript
        const agents = {
            basic: { name: 'Basic Chat', desc: 'Simple conversation with AI assistant' },
            tool: { name: 'Tool Calling', desc: 'AI assistant with time, calculator, and weather tools' },
            task: { name: 'Task Agent', desc: 'Document analysis with file parsing skills' }
        };
```

在 `/* ===== INIT ===== */` 之前添加文件上传相关函数：

```javascript
        /* ===== FILE UPLOAD ===== */
        function handleFileSelect(input) {
            var file = input.files[0];
            if (!file) return;

            var lowerName = file.name.toLowerCase();
            if (!lowerName.endsWith('.docx') && !lowerName.endsWith('.pdf')) {
                addDebugEntry('error', 'Only .docx and .pdf files are supported');
                input.value = '';
                return;
            }

            addDebugEntry('tool_call', 'Uploading file: ' + file.name);

            var formData = new FormData();
            formData.append('file', file);

            fetch('/chat/upload', {
                method: 'POST',
                body: formData
            })
            .then(function(response) { return response.json(); })
            .then(function(data) {
                if (data.error) {
                    addDebugEntry('error', 'Upload failed: ' + data.error);
                    return;
                }
                uploadedFile = data;
                showFileTag(data.fileName);
                // Auto-switch to task agent
                if (currentAgent !== 'task') {
                    selectAgent('task');
                }
                addDebugEntry('tool_result', 'File uploaded: ' + data.fileName);
            })
            .catch(function(err) {
                addDebugEntry('error', 'Upload error: ' + err.message);
            });

            input.value = '';
        }

        function showFileTag(fileName) {
            var area = document.getElementById('fileTagArea');
            area.innerHTML = '<div class="file-tag">' +
                '<span class="file-tag-name">' + escapeHtml(fileName) + '</span>' +
                '<span class="file-tag-remove" onclick="removeFile()">&times;</span>' +
                '</div>';
        }

        function removeFile() {
            uploadedFile = null;
            document.getElementById('fileTagArea').innerHTML = '';
        }
```

- [x] **Step 5: Modify sendMessage to include file info**

在 `sendMessage` 函数中修改 fetch body，添加文件信息：

找到这行：
```javascript
                    body: JSON.stringify({ agentType: currentAgent, message: message })
```

替换为：
```javascript
                    body: JSON.stringify({
                        agentType: currentAgent,
                        message: message,
                        filePath: uploadedFile ? uploadedFile.filePath : null,
                        fileName: uploadedFile ? uploadedFile.fileName : null
                    })
```

在 `sendMessage` 函数中 `// Clear input` 注释行之后，添加清除文件 tag：

```javascript
            // Clear file after send
            removeFile();
```

- [x] **Step 6: Update selectAgent to clear file on agent switch**

在 `selectAgent` 函数开头添加：

```javascript
            removeFile();
```

- [x] **Step 7: Verify full page renders**

Run: `mvn spring-boot:run -q &`
Then: `curl -s http://localhost:8080/ | head -20`
Expected: HTML containing "Task Agent" and upload button HTML
Kill: stop the background process

- [x] **Step 8: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: add file upload UI, Task Agent card, and upload JS logic"
```

---

### Task 9: Full Build and Manual Verification

**Files:** None (verification only)

- [x] **Step 1: Clean build**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [x] **Step 2: Start application**

Run: `mvn spring-boot:run`

- [x] **Step 3: Verify frontend loads**

Open http://localhost:8080 in browser. Verify:
- Left sidebar shows 3 agent cards: Basic Chat, Tool Calling, Task Agent
- Input area has upload button (📎) next to textarea
- Clicking Task Agent switches the chat header

- [x] **Step 4: Test file upload flow**

1. Click Task Agent
2. Click 📎 button, select a .docx or .pdf file
3. Verify file tag appears above input
4. Type "请摘要这个文件" and send
5. Verify SSE stream shows tool_call and text response in debug panel

- [x] **Step 5: Verify other agents still work**

1. Switch to Basic Chat
2. Send "hello"
3. Verify response comes back normally
4. Switch to Tool Calling
5. Send "what time is it in Shanghai?"
6. Verify tool call and response work

- [x] **Step 6: Stop application and final commit (if any uncommitted fixes)**

Run: Stop the app (Ctrl+C)
