# File Upload Fix & DOCX Template Editing Design

**Date**: 2026-04-11
**Author**: Claude Code
**Status**: Approved

---

## Overview

Three independent modules to fix file upload bug, enhance file list UI, and add DOCX template editing capability via a new "template" agent.

## Modules

1. **Bug Fix**: File path not being sent to backend
2. **File List UI**: Display uploaded files per message with type icons
3. **DOCX Template Editing**: New agent with skill for variable replacement in Word templates

---

## Module 1: Bug Fix

### Problem

In `chat.html` `sendMessage()` function:
- Line 1349: `removeFile()` clears `uploadedFile` to null
- Line 1368-1377: Fetch request reads `uploadedFile` (already null)
- Result: `filePath` and `fileName` are always sent as null to backend

### Solution

Cache file info before clearing:

```javascript
// In sendMessage()
var fileInfo = uploadedFile;  // Cache first
removeFile();                  // Then clear UI state
setStreamingState(true);

// In fetch body
body: JSON.stringify({
    agentType: currentAgent,
    message: message,
    filePath: fileInfo ? fileInfo.filePath : null,
    fileName: fileInfo ? fileInfo.fileName : null
})
```

### Files Changed

- `src/main/resources/templates/chat.html`: Fix sendMessage() function

---

## Module 2: File List UI

### Current Message Structure

```
┌─ message.agent ─────────────────────────┐
│ [avatar] ┌─ agent-response-wrapper ──┐  │
│          │ ┌─ thinking-box ─────────┐│  │
│          │ │ ▼ Thinking ✓           ││  │
│          │ └────────────────────────┘│  │
│          │ ┌─ message-bubble ───────┐│  │
│          │ │ Response content...    ││  │
│          │ └────────────────────────┘│  │
│          └───────────────────────────┘  │
└─────────────────────────────────────────┘
```

### New Structure with File List

```
┌─ message.agent ─────────────────────────┐
│ [avatar] ┌─ agent-response-wrapper ──┐  │
│          │ ┌─ file-list ─────────────┐│  │
│          │ │ 📘 report.docx          ││  │  <-- NEW
│          │ └─────────────────────────┘│  │
│          │ ┌─ thinking-box ─────────┐│  │
│          │ │ ▼ Thinking ✓           ││  │
│          │ └────────────────────────┘│  │
│          │ ┌─ message-bubble ───────┐│  │
│          │ │ Response content...    ││  │
│          │ └────────────────────────┘│  │
│          └───────────────────────────┘  │
└─────────────────────────────────────────┘
```

### File Type Icons

| Extension | Icon HTML                                    | Icon Color | Label |
|-----------|----------------------------------------------|------------|-------|
| .docx     | `<span style="color:#2B579A">W</span>`      | #2B579A    | W     |
| .pdf      | `<span style="color:#FF4757">P</span>`      | #FF4757    | P     |
| .xlsx     | `<span style="color:#217346">X</span>`      | #217346    | X     |

### CSS for File List

```css
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

.file-list-item .file-name {
    max-width: 150px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
```

### JavaScript Changes

- Modify `createThinkingBox()` to accept and display file info
- Add `createFileList()` helper function
- Pass `fileInfo` to both user and agent message rendering

### Data Flow

```
User uploads file → stored in uploadedFile
User sends message → fileInfo cached → removeFile() → fetch
Message displayed → show file list above content/bubble
```

### Per-Message Isolation

Each message shows only the files associated with that specific message. Not a global session file list.

---

## Module 3: DOCX Template Editing

### Architecture

```
┌──────────────────────────────────────────────────┐
│ Agent: "template" (NEW)                          │
│ ┌──────────────────────────────────────────────┐ │
│ │ Skill: docx-template (NEW)                   │ │
│ │ Location: skills/docx-template/SKILL.md      │ │
│ │ Purpose: Guide LLM through template editing  │ │
│ └──────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────┐ │
│ │ Tool: DocxParserTool (ENHANCED)              │ │
│ │ - parse_docx()  (existing)                   │ │
│ │ - edit_docx()   (NEW)                        │ │
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

### Tool: edit_docx

```java
@Tool(name = "edit_docx",
    description = "Edit variables in a .docx file. Replaces placeholder text with values and saves to a new file.")
public String editDocx(
    @ToolParam(name = "filePath",
        description = "Absolute path to the source .docx file") String filePath,

    @ToolParam(name = "replacements",
        description = "JSON array of replacement objects: " +
                     "[{\"placeholder\":\"{{name}}\",\"value\":\"张三\"}]") String replacementsJson
)
```

**Implementation**:
1. Parse JSON into list of {placeholder, value} pairs
2. Load DOCX with Apache POI
3. Iterate through paragraphs and table cells
4. Replace placeholder text with values
5. Save to new file: `{name}_edited.docx`
6. Return: JSON with new file path and replacement count

**Return Format**:
```json
{
    "success": true,
    "outputFilePath": "/tmp/agentscope-uploads/uuid_edited.docx",
    "replacementsMade": 3,
    "message": "Successfully replaced 3 placeholders"
}
```

### Skill: docx-template

**File**: `src/main/resources/skills/docx-template/SKILL.md`

```yaml
---
name: docx-template
description: Use when user uploads a Word document (.docx) template and wants to fill or replace variables/placeholders. Triggers include mentions of "模板", "template", "替换变量", "fill in", "变量填充".
---

# DOCX Template Variable Replacement

## Purpose
Edit Word document templates by replacing placeholder variables with actual values.

## Workflow
1. **Understand the template**: Call `parse_docx` to read the document content and identify placeholder variables (e.g., {{name}}, {{date}})

2. **Clarify values**: Ask the user to provide values for each identified placeholder if not already provided

3. **Execute replacement**: Call `edit_docx` with the file path and replacements array in JSON format

4. **Report result**: Inform the user of the new file location and number of replacements made

## Placeholder Format
Common patterns include:
- Double braces: `{{variable_name}}`
- Dollar format: `$variable_name`
- Custom markers: User may specify any pattern

## Example
User: "Fill in this contract template"
→ parse_docx to find {{甲方}}, {{乙方}}, {{日期}}
→ Ask for values
→ edit_docx with replacements
→ Report completion with new file path
```

### Agent: template

**File**: `src/main/java/com/msxf/agentscope/service/AgentService.java`

Add new case to `createAgent()`:

```java
case "template" -> {
    SkillBox skillBox = new SkillBox(toolkit);
    try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
        skillBox.registration()
                .skill(repo.getSkill("docx-template"))
                .tool(new DocxParserTool())
                .apply();
    }
    builder.name("TemplateEditor")
           .sysPrompt("You are a Word document template editor. " +
                     "When users upload a .docx template, help them fill in variables. " +
                     "First parse the document to identify placeholders, " +
                     "then ask for values if needed, then replace them.")
           .toolkit(toolkit)
           .skillBox(skillBox);
}
```

### Frontend: New Agent Card

**File**: `src/main/resources/templates/chat.html`

Add to agent list and agents object:

```html
<div class="agent-card" data-agent="template" onclick="selectAgent('template')">
    <div class="agent-card-icon">TE</div>
    <div class="agent-card-info">
        <div class="agent-card-name">Template Editor</div>
        <div class="agent-card-desc">Word template variable replacement</div>
    </div>
</div>
```

```javascript
template: { name: 'Template Editor', desc: 'Word template variable replacement' }
```

### Interaction Flow Example

```
User: [上传 contract_template.docx] 帮我把合同里的变量填一下

Agent (thinking): 文档包含 {{甲方}}、{{乙方}}、{{日期}} 等变量
                我需要先解析文档，确认变量位置

→ Tool: parse_docx(filePath)
← Tool Result: 文档内容解析成功，包含以下变量：
     - {{甲方}} (第1段)
     - {{乙方}} (第1段)
     - {{日期}} (第2段)

Agent: 我看到模板中有以下变量需要填写：
     - {{甲方}}
     - {{乙方}}
     - {{日期}}
     请提供替换值。

User: 甲方是美的集团，乙方是格力电器，日期是2026年4月11日

Agent (thinking): 用户提供了所有变量值，调用 edit_docx 替换

→ Tool: edit_docx(filePath, replacements: [
      {"placeholder": "{{甲方}}", "value": "美的集团"},
      {"placeholder": "{{乙方}}", "value": "格力电器"},
      {"placeholder": "{{日期}}", "value": "2026年4月11日"}
    ])
← Tool Result: 编辑完成！新文件: /tmp/agentscope-uploads/xxx_edited.docx
               替换了 3 处变量

Agent: 已完成模板变量替换！新文件已保存到: contract_template_edited.docx
      共替换了 3 处变量。
```

---

## File Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `chat.html` | Modify | Bug fix: cache fileInfo before removeFile(); Add file list UI; Add template agent card |
| `AgentService.java` | Modify | Add "template" agent case |
| `DocxParserTool.java` | Modify | Add edit_docx() method |
| `ChatController.java` | Add | `/chat/download` endpoint for serving uploaded/edited files |
| `skills/docx-template/SKILL.md` | Create | New skill for template editing workflow |
| `chat.html` CSS | Add | File list styles and file type icon colors |

---

## Dependencies

No new dependencies required. Uses existing:
- Apache POI 5.3.0 (already in pom.xml)
- AgentScope 1.0.11 (already in pom.xml)

---

## Success Criteria

1. **Bug Fix**: File upload correctly passes filePath to backend
2. **UI**: Uploaded files display with appropriate icons above message content
3. **Template Agent**: New "Template Editor" agent appears in sidebar
4. **Edit Tool**: edit_docx successfully replaces variables and saves new file
5. **End-to-End**: User can upload template, request edits, and get edited file

---

## Open Questions

None at this time.
