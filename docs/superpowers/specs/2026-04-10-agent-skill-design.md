# Agent Skill 文档解析功能设计

## 概述

在现有 AgentScope Demo 项目中集成 Agent Skill 机制，支持用户上传 DOCX/PDF 文件，通过 Task Agent 自动加载对应技能，使用纯 Java 工具（Apache POI / Apache PDFBox）解析文件内容，完成摘要、提取关键信息等任务。

## 架构

```
用户上传文件 + 输入指令
    ↓
ChatController (/chat/upload → /chat/send)
    ↓
文件保存到 java.io.tmpdir
    ↓
AgentService 创建 TaskAgent (ReActAgent + SkillBox)
    ↓
SkillBox 注册 docx/pdf 技能 + 对应 Java Tool
    ↓
LLM 识别文件类型 → load_skill → 调用 Java Tool
    ↓
DocxParserTool (POI) / PdfParserTool (PDFBox) 解析文件
    ↓
LLM 基于解析内容生成回答，SSE 流式返回
```

## 技能改造

原始 docx/pdf 技能（来自 `~/.agents/skills/`）大量依赖 Python/pandoc/npm。需要重写 SKILL.md，使其指导 LLM 调用 Java Tool。

### 改写原则

- SKILL.md 只保留概念说明和 Tool 调用指引
- 删除所有 Python/bash/JavaScript 代码示例
- 替换为 Java Tool 的调用说明（工具名、参数、返回格式）
- 不复制 scripts/、references/ 目录，不需要 Python 脚本和 XSD schema
- 技能文件存放于 `src/main/resources/skills/` 下，使用 ClasspathSkillRepository 加载

### 改写后的 docx SKILL.md

存放路径：`src/main/resources/skills/docx/SKILL.md`

```markdown
---
name: docx
description: Use this skill when the user wants to read, analyze, or extract
  content from Word documents (.docx files). Triggers include: uploading a
  .docx file and asking to summarize, extract key points, translate, etc.
---

# DOCX Document Analysis

## Overview
A .docx file is a ZIP archive containing XML files. This skill provides
a Java tool to parse .docx files and extract their content.

## Available Tool: parse_docx

parse_docx(filePath: String) -> String

- filePath: absolute path to the uploaded .docx file
- Returns: full text content including headings, paragraphs, and tables

## Usage
1. User uploads a .docx file
2. Call parse_docx with the file path
3. Based on extracted content, perform the user's requested task
   (summarize, extract key points, translate, etc.)
```

### 改写后的 pdf SKILL.md

存放路径：`src/main/resources/skills/pdf/SKILL.md`

```markdown
---
name: pdf
description: Use this skill when the user wants to read or extract
  text/tables from PDF files. If the user mentions a .pdf file or asks
  to analyze an uploaded PDF, use this skill.
---

# PDF Document Analysis

## Available Tool: parse_pdf

parse_pdf(filePath: String) -> String

- filePath: absolute path to the uploaded PDF file
- Returns: extracted text content from all pages

## Usage
1. User uploads a .pdf file
2. Call parse_pdf with the file path
3. Perform the requested task on the extracted content
```

## 新增 Java 组件

### DocxParserTool

- 位置：`com.msxf.agentscope.tool.DocxParserTool`
- 使用 Apache POI (`XWPFDocument`) 解析 .docx
- 提取内容：标题层级、段落文本、表格内容
- 注册为 `@Tool(name = "parse_docx")` 的方法

### PdfParserTool

- 位置：`com.msxf.agentscope.tool.PdfParserTool`
- 使用 Apache PDFBox (`PDDocument`) 解析 .pdf
- 提取内容：页面文本、元数据（标题/作者）
- 注册为 `@Tool(name = "parse_pdf")` 的方法

### AgentService 变更

在 `createAgent` 方法中新增 `"task"` 类型：

```java
case "task" -> {
    Toolkit toolkit = new Toolkit();
    SkillBox skillBox = new SkillBox(toolkit);

    // 从 classpath 加载技能
    ClasspathSkillRepository repo = new ClasspathSkillRepository("skills");
    AgentSkill docxSkill = repo.getSkill("docx");
    AgentSkill pdfSkill = repo.getSkill("pdf");

    // 注册技能 + 绑定 Tool（渐进式披露）
    skillBox.registration()
        .skill(docxSkill)
        .tool(new DocxParserTool())
        .apply();
    skillBox.registration()
        .skill(pdfSkill)
        .tool(new PdfParserTool())
        .apply();

    builder.name("TaskAgent")
        .sysPrompt("You are a document analysis assistant...")
        .toolkit(toolkit)
        .skillBox(skillBox);
}
```

### ChatController 变更

新增端点：

- `POST /chat/upload`：接收 `MultipartFile`，保存到临时目录，返回文件信息
- `POST /chat/send`：请求体新增 `filePath` 和 `fileName` 字段

### streamToEmitter 变更

当 agentType 为 "task" 且有 filePath 时，将文件信息拼接到用户消息中：

```
用户上传了文件: /tmp/xxx.docx
用户指令: 摘要这个文件
```

## 新增 Maven 依赖

```xml
<!-- Apache POI for DOCX -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- Apache PDFBox for PDF -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.4</version>
</dependency>
```

## 前端改造

### 输入区改造

在 textarea 左侧添加上传按钮（📎 图标），点击触发隐藏的 `<input type="file" accept=".docx,.pdf">`。

上传成功后在输入框上方显示已选文件标签（文件名 + 删除按钮）。

### 交互流程

1. 用户点击 📎 → 选择文件
2. 前端调用 `POST /chat/upload` → 获取 fileId 和 filePath
3. 文件标签显示在输入框上方
4. 用户输入指令（如"摘要"）→ 发送
5. 消息体携带 agentType、message、filePath
6. 后端 Task Agent 处理，SSE 流式返回

### 侧边栏新增 Task Agent 卡片

在 Basic Chat 和 Tool Calling 下方新增：

```html
<div class="agent-card" data-agent="task">
    <div class="agent-card-icon">📄</div>
    <div class="agent-card-name">Task Agent</div>
    <div class="agent-card-desc">Document analysis with skill-based file parsing</div>
</div>
```

## 文件清单

| 操作 | 文件路径 |
|------|----------|
| 新增 | `src/main/resources/skills/docx/SKILL.md` |
| 新增 | `src/main/resources/skills/pdf/SKILL.md` |
| 新增 | `src/main/java/com/msxf/agentscope/tool/DocxParserTool.java` |
| 新增 | `src/main/java/com/msxf/agentscope/tool/PdfParserTool.java` |
| 修改 | `pom.xml` — 添加 POI、PDFBox 依赖 |
| 修改 | `src/main/java/com/msxf/agentscope/service/AgentService.java` — 新增 task agent |
| 修改 | `src/main/java/com/msxf/agentscope/controller/ChatController.java` — 新增 upload 端点 |
| 修改 | `src/main/resources/templates/chat.html` — 上传按钮 + Task Agent 卡片 |
| 修改 | `src/main/resources/application.yml` — 临时目录配置（可选） |

## 错误处理

- 上传文件大小超限：Spring Boot 默认 1MB，配置 `spring.servlet.multipart.max-file-size=50MB`
- 不支持的文件类型：前端 accept 限制 + 后端校验
- 文件解析失败：Tool 内 try-catch，返回错误信息让 LLM 告知用户
- Skill 加载失败：启动时日志告警，不影响其他 agent 类型

## 不做的事

- 不支持文件编辑/创建（只做读取解析）
- 不集成 Python 环境
- 不做文件持久化存储（临时目录，JVM 退出清理）
- 不做用户认证/文件权限
