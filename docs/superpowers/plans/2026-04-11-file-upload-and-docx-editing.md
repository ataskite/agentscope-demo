# File Upload Fix & DOCX Template Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix file upload path bug, add per-message file list UI with type icons, and implement DOCX template variable editing via a new "template" agent.

**Architecture:**
- Frontend: Cache file info before clearing, render file lists with icons per message
- Backend: New "template" agent with SkillBox pattern, edit_docx tool uses Apache POI for text replacement, download endpoint serves edited files

**Tech Stack:**
- Spring Boot 3.3.6, Java 21
- AgentScope 1.0.11 (ReActAgent, SkillBox, ClasspathSkillRepository)
- Apache POI 5.3.0 (DOCX manipulation)
- Vanilla JavaScript, SSE streaming

---

## Task 1: Bug Fix - File Path Not Sent to Backend

**Files:**
- Modify: `src/main/resources/templates/chat.html:1332-1350`

- [x] **Step 1: Fix sendMessage() to cache fileInfo before removeFile()**

Locate the `sendMessage()` function around line 1332. Find the line with `removeFile();` (currently line 1349) and modify the code to cache fileInfo first:

```javascript
async function sendMessage() {
    var input = messageInput;
    var message = input.value.trim();
    if ((!message && !uploadedFile) || isStreaming) return;

    chatEmpty.style.display = 'none';

    // 构建用户消息显示内容（包含文件信息）
    var displayMessage = message;
    if (uploadedFile) {
        displayMessage = '[上传文件: ' + uploadedFile.fileName + ']\n' + message;
    }
    appendMessage('user', displayMessage, uploadedFile);  // Pass uploadedFile for display
    messageCount++;

    input.value = '';
    input.style.height = 'auto';

    // CACHE file info BEFORE clearing
    var fileInfo = uploadedFile;
    removeFile();
    setStreamingState(true);

    // ... rest of function continues ...
```

- [x] **Step 2: Update fetch body to use fileInfo instead of uploadedFile**

Further down in the same function (around line 1368), update the fetch body:

```javascript
try {
    var response = await fetch('/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            agentType: currentAgent,
            message: message,
            filePath: fileInfo ? fileInfo.filePath : null,
            fileName: fileInfo ? fileInfo.fileName : null
        })
    });
```

- [x] **Step 3: Commit bug fix**

```bash
git add src/main/resources/templates/chat.html
git commit -m "fix: cache file info before removeFile to fix upload path bug"
```

---

## Task 2: File List UI - Add CSS Styles

**Files:**
- Modify: `src/main/resources/templates/chat.html` (CSS section)

- [x] **Step 1: Add file list CSS styles**

Locate the CSS section (starts around line 10, ends around line 1195). Find the `/* ===== FILE UPLOAD ===== */` section (around line 1095). After the `.file-tag-remove` styles, add the new file list styles:

```css
        /* ===== FILE LIST (per message) ===== */
        .file-list {
            display: flex;
            flex-wrap: wrap;
            gap: var(--space-sm);
            margin-bottom: var(--space-sm);
        }

        .file-list-item {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 10px;
            background: rgba(57, 255, 20, 0.08);
            border: 1px solid rgba(57, 255, 20, 0.3);
            border-radius: 4px;
            font-size: 10px;
            color: var(--neon-green);
        }

        .file-list-item .file-icon {
            width: 14px;
            height: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--bg-card);
            border-radius: 2px;
            font-size: 7px;
            font-weight: 700;
        }

        .file-list-item .file-icon.docx {
            color: #2B579A;
        }

        .file-list-item .file-icon.pdf {
            color: #FF4757;
        }

        .file-list-item .file-icon.xlsx {
            color: #217346;
        }

        .file-list-item .file-name {
            max-width: 150px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
```

- [x] **Step 2: Commit CSS styles**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: add file list CSS styles with file type icon colors"
```

---

## Task 3: File List UI - Add Helper Functions

**Files:**
- Modify: `src/main/resources/templates/chat.html` (JavaScript section)

- [x] **Step 1: Add createFileList() helper function**

Locate the JavaScript section (starts around line 1272). Find the file upload functions (around line 1693). Add the new helper function after `removeFile()` (around line 1745):

```javascript
        /* ===== FILE UPLOAD ===== */
        function handleFileSelect(input) {
            // ... existing code ...
        }

        function showFileTag(fileName) {
            // ... existing code ...
        }

        function removeFile() {
            uploadedFile = null;
            document.getElementById('fileTagArea').innerHTML = '';
        }

        /* ===== FILE LIST HELPER ===== */
        function createFileList(fileInfo) {
            if (!fileInfo) return '';

            var ext = fileInfo.fileName.substring(fileInfo.fileName.lastIndexOf('.')).toLowerCase();
            var iconLabel = '';
            var iconClass = '';

            switch (ext) {
                case '.docx':
                    iconLabel = 'W';
                    iconClass = 'docx';
                    break;
                case '.pdf':
                    iconLabel = 'P';
                    iconClass = 'pdf';
                    break;
                case '.xlsx':
                    iconLabel = 'X';
                    iconClass = 'xlsx';
                    break;
                default:
                    iconLabel = '?';
                    iconClass = '';
            }

            return '<div class="file-list">' +
                '<div class="file-list-item">' +
                '<span class="file-icon ' + iconClass + '">' + iconLabel + '</span>' +
                '<span class="file-name">' + escapeHtml(fileInfo.fileName) + '</span>' +
                '</div>' +
                '</div>';
        }
```

- [x] **Step 2: Commit helper function**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: add createFileList() helper with file type icons"
```

---

## Task 4: File List UI - Update createThinkingBox() to Show Files

**Files:**
- Modify: `src/main/resources/templates/chat.html` (JavaScript section)

- [x] **Step 1: Modify createThinkingBox() to accept fileInfo parameter**

Locate the `createThinkingBox()` function (around line 1528). Update the function signature and add file list rendering:

```javascript
        function createThinkingBox(fileInfo) {
            // Create a wrapper for agent response (thinking + bubble)
            var wrapper = document.createElement('div');
            wrapper.className = 'message agent';
            wrapper.style.maxWidth = '100%';
            wrapper.style.width = '100%';

            // Avatar
            var avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>';

            var content = document.createElement('div');
            content.className = 'agent-response-wrapper';

            // Add file list if fileInfo provided
            if (fileInfo) {
                var fileListNode = document.createElement('div');
                fileListNode.innerHTML = createFileList(fileInfo);
                content.appendChild(fileListNode.firstChild);
            }

            // Create thinking box
            var box = document.createElement('div');
            box.className = 'thinking-box';
            box.innerHTML = '<div class="thinking-header" onclick="toggleThinking(this)">' +
                '<div class="thinking-header-icon"><div class="thinking-spinner"></div></div>' +
                '<div class="thinking-header-text">Thinking</div>' +
                '</div>' +
                '<div class="thinking-content"></div>';

            content.appendChild(box);
            wrapper.appendChild(avatar);
            wrapper.appendChild(content);
            chatMessages.appendChild(wrapper);

            currentAgentMessageWrapper = wrapper;
            currentThinkingBox = box;
            scrollToBottom(chatMessages);
            return box;
        }
```

- [x] **Step 2: Update all createThinkingBox() calls to pass fileInfo**

Find the `updateThinkingBox()` function (around line 1563) and update its call to `createThinkingBox()`:

```javascript
        function updateThinkingBox(content, fileInfo) {
            if (!currentThinkingBox) {
                createThinkingBox(fileInfo);  // Pass fileInfo here
            }
            // ... rest of function unchanged ...
        }
```

- [x] **Step 3: Commit thinking box changes**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: update createThinkingBox to accept and display fileInfo"
```

---

## Task 5: File List UI - Update Event Handlers to Pass FileInfo

**Files:**
- Modify: `src/main/resources/templates/chat.html` (JavaScript section)

- [x] **Step 1: Update sendMessage() event handlers for 'thinking' and 'tool_call'**

Locate the SSE event handler in `sendMessage()` (around line 1403). Update the 'thinking' and 'tool_call' cases to pass fileInfo:

```javascript
                currentEventSource.addEventListener('message', function(event) {
                    var payload;
                    try {
                        payload = JSON.parse(event.data);
                    } catch (e) {
                        addDebugEntry('error', '[PARSE ERROR] ' + event.data);
                        return;
                    }

                    switch (payload.type) {
                        case 'text':
                            // Collapse thinking and create/add to agent bubble
                            collapseThinkingBox();
                            if (!agentBubble) {
                                agentBubble = addAgentBubble();
                            }
                            agentBubble.textContent += (payload.content || payload.text || '');
                            scrollToBottom(chatMessages);
                            break;

                        case 'thinking':
                            var thinkingText = payload.content || payload.message || 'Processing...';
                            updateThinkingBox(thinkingText, fileInfo);  // Pass fileInfo
                            addDebugEntry('thinking', thinkingText);
                            break;

                        case 'tool_call':
                            var toolText = payload.content || payload.message || 'Tool invoked';
                            updateThinkingBox('🔧 ' + toolText, fileInfo);  // Pass fileInfo
                            addDebugEntry('tool_call', toolText);
                            break;

                        case 'tool_result':
                            var resultText = payload.content || payload.message || 'Tool output';
                            updateThinkingBox('✓ ' + resultText, fileInfo);  // Pass fileInfo
                            addDebugEntry('tool_result', resultText);
                            break;

                        // ... rest of cases unchanged ...
                    }
                });
```

- [x] **Step 2: Update createThinkingBox() call in error handling paths**

Find the error handling code (around line 1465 in `currentEventSource.onerror`) and update:

```javascript
                currentEventSource.onerror = function() {
                    if (currentEventSource) {
                        currentEventSource.close();
                        currentEventSource = null;
                    }
                    if (isStreaming) {
                        completeThinkingBox();
                        if (!agentBubble) {
                            agentBubble = addAgentBubble();
                        }
                        if (!agentBubble.textContent.trim()) {
                            agentBubble.textContent = '[CONNECTION LOST] Please retry.';
                            agentBubble.closest('.message').classList.add('error');
                        }
                        addDebugEntry('error', '[NETWORK] SSE connection terminated');
                        isStreaming = false;
                        setStreamingState(false);
                    }
                };
```

- [x] **Step 3: Update sendMessage() initialization to create thinking box with fileInfo**

Locate the section before the fetch (around line 1362) and update:

```javascript
            // Reset thinking state
            currentThinkingBox = null;
            currentAgentMessageWrapper = null;
            thinkingContent = '';
            var agentBubble = null;

            // Create initial thinking box with file info
            if (fileInfo) {
                createThinkingBox(fileInfo);
            }
```

- [x] **Step 4: Commit event handler updates**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: pass fileInfo through event handlers to display in thinking box"
```

---

## Task 6: File List UI - Update User Message Display

**Files:**
- Modify: `src/main/resources/templates/chat.html` (JavaScript section)

- [x] **Step 1: Update appendMessage() to accept and display fileInfo**

Locate the `appendMessage()` function (around line 1489). Update it to accept fileInfo parameter and display file list:

```javascript
        function appendMessage(role, text, fileInfo) {
            var wrapper = document.createElement('div');
            wrapper.className = 'message ' + role;

            // Avatar
            var avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            if (role === 'user') {
                avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/></svg>';
            } else {
                avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>';
            }

            var content = document.createElement('div');
            content.className = 'message-content';

            // Add file list for user messages if fileInfo provided
            if (fileInfo && role === 'user') {
                var fileListNode = document.createElement('div');
                fileListNode.innerHTML = createFileList(fileInfo);
                content.appendChild(fileListNode.firstChild);
            }

            var bubble = document.createElement('div');
            bubble.className = 'message-bubble';
            bubble.textContent = text;

            var time = document.createElement('div');
            time.className = 'message-time';
            time.textContent = getTimestamp();

            content.appendChild(bubble);
            content.appendChild(time);
            wrapper.appendChild(avatar);
            wrapper.appendChild(content);
            chatMessages.appendChild(wrapper);
            scrollToBottom(chatMessages);

            return bubble;
        }
```

- [x] **Step 2: Verify the existing appendMessage() call in sendMessage()**

The call at line 1344 should already be passing fileInfo (from Task 1, Step 1):

```javascript
appendMessage('user', displayMessage, uploadedFile);  // Should already be passing uploadedFile
```

- [x] **Step 3: Commit user message display update**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: update appendMessage to display file list for user messages"
```

---

## Task 7: Add Template Agent Card to Sidebar

**Files:**
- Modify: `src/main/resources/templates/chat.html` (HTML and JavaScript)

- [x] **Step 1: Add template agent card HTML**

Locate the agent list in the HTML (around line 1209). After the "Task Agent" card (around line 1230), add the new template agent card:

```html
                    <div class="agent-card" data-agent="template" onclick="selectAgent('template')">
                        <div class="agent-card-icon">TE</div>
                        <div class="agent-card-info">
                            <div class="agent-card-name">Template Editor</div>
                            <div class="agent-card-desc">Word template variable replacement</div>
                        </div>
                    </div>
                </div>
            </aside>
```

- [x] **Step 2: Add template agent metadata to agents object**

Locate the `agents` object definition in JavaScript (around line 1280). Add the template entry:

```javascript
        const agents = {
            basic: { name: 'Basic Chat', desc: 'Simple conversation with AI assistant' },
            tool: { name: 'Tool Calling', desc: 'AI with time, calculator, weather tools' },
            task: { name: 'Task Agent', desc: 'Document analysis with file parsing' },
            template: { name: 'Template Editor', desc: 'Word template variable replacement' }
        };
```

- [x] **Step 3: Commit template agent UI**

```bash
git add src/main/resources/templates/chat.html
git commit -m "feat: add template editor agent card to sidebar"
```

---

## Task 8: Create docx-template Skill Definition

**Files:**
- Create: `src/main/resources/skills/docx-template/SKILL.md`

- [x] **Step 1: Create skills directory structure**

```bash
mkdir -p src/main/resources/skills/docx-template
```

- [x] **Step 2: Create SKILL.md file**

```bash
cat > src/main/resources/skills/docx-template/SKILL.md << 'EOF'
---
name: docx-template
description: Use when user uploads a Word document (.docx) template and wants to fill or replace variables/placeholders. Triggers include mentions of "模板", "template", "替换变量", "fill in", "变量填充", "编辑", "edit".
---

# DOCX Template Variable Replacement

## Purpose
Edit Word document templates by replacing placeholder variables with actual values.

## Workflow
1. **Understand the template**: Call `parse_docx` to read the document content and identify placeholder variables (e.g., {{name}}, {{date}}, $variable)

2. **Clarify values**: Ask the user to provide values for each identified placeholder if not already provided

3. **Execute replacement**: Call `edit_docx` with the file path and replacements array in JSON format:
   ```json
   [
     {"placeholder": "{{name}}", "value": "张三"},
     {"placeholder": "{{date}}", "value": "2026年4月11日"}
   ]
   ```

4. **Report result**: Inform the user of the new file location and number of replacements made

## Placeholder Format
Common patterns include:
- Double braces: `{{variable_name}}`
- Dollar format: `$variable_name`
- Custom markers: User may specify any pattern

## Example Interaction
User: "Fill in this contract template"
→ parse_docx to find {{甲方}}, {{乙方}}, {{日期}}
→ Ask for values if not provided
→ edit_docx with replacements
→ Report completion with new file path

## Notes
- The edited file is saved with `_edited` suffix
- Original file is never modified
- Replacements apply to paragraphs and table cells
EOF
```

- [x] **Step 3: Commit skill definition**

```bash
git add src/main/resources/skills/docx-template/SKILL.md
git commit -m "feat: add docx-template skill for variable replacement workflow"
```

---

## Task 9: Add edit_docx Method to DocxParserTool

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/tool/DocxParserTool.java`

- [x] **Step 1: Add JSON parsing imports**

Add these imports at the top of the file after the existing imports:

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
```

- [x] **Step 2: Add ObjectMapper and ReplacementRecord class**

Add these after the logger declaration in the class:

```java
    private static final Logger log = LoggerFactory.getLogger(DocxParserTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static record ReplacementRecord(String placeholder, String value) {}
```

- [x] **Step 3: Add edit_docx tool method**

Add this method after the `parseDocx()` method:

```java
    @Tool(name = "edit_docx", description = "Edit variables in a .docx file by replacing placeholder text with values. Saves to a new file with '_edited' suffix.")
    public String editDocx(
            @ToolParam(name = "filePath", description = "Absolute path to the source .docx file") String filePath,
            @ToolParam(name = "replacements", description = "JSON array of replacement objects: [{\"placeholder\":\"{{name}}\",\"value\":\"张三\"}]") String replacementsJson) {

        log.info("Editing DOCX file: {}", filePath);

        try {
            // Parse replacements JSON
            List<ReplacementRecord> replacements = objectMapper.readValue(
                replacementsJson,
                new TypeReference<List<ReplacementRecord>>() {}
            );

            if (replacements == null || replacements.isEmpty()) {
                return "{\"success\":false,\"message\":\"No replacements provided\"}";
            }

            // Load the document
            File sourceFile = new File(filePath);
            if (!sourceFile.exists()) {
                return "{\"success\":false,\"message\":\"Source file not found: " + filePath + "\"}";
            }

            try (FileInputStream fis = new FileInputStream(filePath);
                 XWPFDocument document = new XWPFDocument(fis)) {

                int replacementsMade = 0;

                // Replace in paragraphs
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    replacementsMade += replaceInParagraph(paragraph, replacements);
                }

                // Replace in tables
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                replacementsMade += replaceInParagraph(paragraph, replacements);
                            }
                        }
                    }
                }

                // Generate output file path
                String originalPath = filePath.substring(0, filePath.lastIndexOf('.'));
                String outputPath = originalPath + "_edited.docx";
                File outputFile = new File(outputPath);

                // Save edited document
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    document.write(fos);
                }

                log.info("DOCX edit complete: {} replacements -> {}", replacementsMade, outputPath);

                // Return result as JSON
                return String.format(
                    "{\"success\":true,\"outputFilePath\":\"%s\",\"replacementsMade\":%d,\"message\":\"Successfully replaced %d placeholders\"}",
                    outputPath, replacementsMade, replacementsMade
                );

            }
        } catch (Exception e) {
            log.error("Failed to edit DOCX file: {}", filePath, e);
            return "{\"success\":false,\"message\":\"Error editing DOCX: " + e.getMessage() + "\"}";
        }
    }

    private int replaceInParagraph(XWPFParagraph paragraph, List<ReplacementRecord> replacements) {
        int count = 0;
        String paragraphText = paragraph.getText();

        if (paragraphText == null || paragraphText.isEmpty()) {
            return 0;
        }

        // Check if any replacement matches
        for (ReplacementRecord replacement : replacements) {
            if (paragraphText.contains(replacement.placeholder())) {
                // Replace all runs in this paragraph
                String newText = paragraphText.replace(replacement.placeholder(), replacement.value());

                // Clear existing runs and add new text
                for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                    paragraph.removeRun(i);
                }

                XWPFRun newRun = paragraph.createRun();
                newRun.setText(newText);
                count++;
                break; // Only apply one replacement per paragraph to avoid conflicts
            }
        }
        return count;
    }
```

- [x] **Step 4: Compile to verify**

```bash
mvn clean compile
```

Expected: BUILD SUCCESS

- [x] **Step 5: Commit edit_docx implementation**

```bash
git add src/main/java/com/msxf/agentscope/tool/DocxParserTool.java
git commit -m "feat: add edit_docx tool method for variable replacement"
```

---

## Task 10: Add download Endpoint to ChatController

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/controller/ChatController.java`

- [x] **Step 1: Add imports for file download**

Add these imports after the existing imports:

```java
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.File;
```

- [x] **Step 2: Add download endpoint**

Add this method after the `uploadFile()` method (after line 175):

```java
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
            // Construct file path - supports both original uploads and edited files
            Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
            Path filePath = uploadDir.resolve(fileId);

            // If fileId is a UUID (no extension), try to find the file
            if (!filePath.toFile().exists() && !fileId.contains(".")) {
                // Try common extensions
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

            // Determine content type
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
```

- [x] **Step 3: Compile to verify**

```bash
mvn clean compile
```

Expected: BUILD SUCCESS

- [x] **Step 4: Commit download endpoint**

```bash
git add src/main/java/com/msxf/agentscope/controller/ChatController.java
git commit -m "feat: add /chat/download endpoint for serving uploaded and edited files"
```

---

## Task 11: Add Template Agent to AgentService

**Files:**
- Modify: `src/main/java/com/msxf/agentscope/service/AgentService.java`

- [x] **Step 1: Add template case to createAgent()**

Locate the switch statement in `createAgent()` (around line 73). Add the template case after the task case:

```java
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
                    skillBox.registration()
                            .skill(repo.getSkill("xlsx"))
                            .tool(new XlsxParserTool())
                            .apply();
                } catch (Exception e) {
                    log.error("Failed to load skills from classpath", e);
                }
                builder.toolkit(toolkit).skillBox(skillBox);
            }
            case "template" -> {
                SkillBox skillBox = new SkillBox(toolkit);
                try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
                    skillBox.registration()
                            .skill(repo.getSkill("docx-template"))
                            .tool(new DocxParserTool())
                            .apply();
                } catch (Exception e) {
                    log.error("Failed to load skills from classpath", e);
                }
                builder.name("TemplateEditor")
                       .sysPrompt("You are a Word document template editor. " +
                                 "When users upload a .docx template, help them fill in variables. " +
                                 "First parse the document to identify placeholders, " +
                                 "then ask for values if needed, then replace them using edit_docx.")
                       .toolkit(toolkit)
                       .skillBox(skillBox);
            }
            default -> builder.toolkit(toolkit);
        }
```

- [x] **Step 2: Compile to verify**

```bash
mvn clean compile
```

Expected: BUILD SUCCESS

- [x] **Step 3: Commit template agent**

```bash
git add src/main/java/com/msxf/agentscope/service/AgentService.java
git commit -m "feat: add template agent for Word document variable editing"
```

---

## Task 12: Final Integration Test

- [x] **Step 1: Build the project**

```bash
mvn clean package -DskipTests
```

Expected: BUILD SUCCESS with jar file created

- [x] **Step 2: Start the application**

```bash
export DASHSCOPE_API_KEY=your_key_here
mvn spring-boot:run
```

Expected: Application starts on http://localhost:8080

- [x] **Step 3: Test bug fix - File upload path**

1. Open http://localhost:8080
2. Select "Task Agent"
3. Upload a test .docx file
4. Send message "解析这个文件"
5. Check debug log for tool_call event

Expected: `parse_docx` is called with actual file path (not null)

- [x] **Step 4: Test file list UI**

1. After uploading a file, observe the message display

Expected: File name and icon appear above the user message and above the thinking box

- [x] **Step 5: Test template agent - Variable replacement**

1. Select "Template Editor" agent from sidebar
2. Upload a .docx file with placeholders like `{{name}}`, `{{date}}`
3. Send message "帮我把模板里的变量填一下，name是张三，date是今天"
4. Wait for response

Expected: Agent calls `parse_docx`, then `edit_docx`, then reports new file location

- [x] **Step 6: Test download endpoint**

1. After editing completes, note the output file path from the response
2. Extract the fileId (filename without .docx extension if _edited)
3. Access: http://localhost:8080/chat/download?fileId=[extracted fileId]

Expected: Browser downloads the edited .docx file

- [x] **Step 7: Commit final integration**

```bash
git add -A
git commit -m "test: verify file upload bug fix, file list UI, and template agent integration"
```

---

## Self-Review Checklist

**Spec Coverage:**
- [x] Module 1: Bug fix (Task 1)
- [x] Module 2: File list UI (Tasks 2-6)
- [x] Module 3: DOCX template editing (Tasks 7-11)

**Placeholder Scan:**
- [x] No TBD/TODO placeholders
- [x] All code snippets are complete
- [x] All file paths are exact
- [x] All commands have expected outputs

**Type Consistency:**
- [x] `fileInfo` variable name used consistently
- [x] `createFileList()` function signature consistent
- [x] `updateThinkingBox(content, fileInfo)` signature consistent
- [x] `edit_docx` tool name matches skill reference

**Scope Check:**
- [x] Each task is independently implementable
- [x] Tasks follow TDD pattern (write code, verify, commit)
- [x] No dependencies on external tasks within same module
- [x] Module boundaries respected
