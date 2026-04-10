# AgentScope Demo

基于 [AgentScope](https://java.agentscope.io/) 的 Java AI Agent 演示项目，支持多种 Agent 类型、工具调用和文档解析技能。

## 特性

- **三种 Agent 类型**
  - **Basic Chat**: 纯对话 AI 助手
  - **Tool Calling**: 带工具调用的 AI 助手（时间、计算器、天气）
  - **Task Agent**: 支持文档解析的智能助手（.docx/.pdf）

- **技能系统 (SkillBox)**
  - 通过 ClasspathSkillRepository 加载技能
  - 支持渐进式工具披露（Progressive Tool Disclosure）
  - 纯 Java 实现，无 Python 依赖

- **文档解析**
  - DOCX 解析（Apache POI）
  - PDF 解析（Apache PDFBox）
  - 保留文档结构（标题、段落、表格）

- **流式响应**
  - Server-Sent Events (SSE) 实时推送
  - 支持思考过程、工具调用状态展示

## 快速开始

### 环境要求

- Java 21+
- Maven 3.9+
- DashScope API Key ([获取地址](https://dashscope.console.aliyun.com/))

### 运行项目

```bash
# 克隆仓库
git clone https://github.com/ataskite/agentscope-demo.git
cd agentscope-demo

# 配置 API Key
export DASHSCOPE_API_KEY=your_api_key_here

# 启动应用
mvn spring-boot:run
```

启动后访问 http://localhost:8080

### Docker 运行（可选）

```bash
docker build -t agentscope-demo .
docker run -p 8080:8080 -e DASHSCOPE_API_KEY=your_key agentscope-demo
```

## 使用示例

### Basic Chat

```
你：你好，请介绍一下自己
Assistant：你好！我是一个 AI 助手，很高兴为您服务...
```

### Tool Calling

```
你：现在几点了？帮我算一下 123 + 456
ToolAgent：[调用 get_current_time] [调用 calculate_sum]
现在是北京时间 2024-04-10 14:30:00，123 + 456 = 579
```

### Task Agent - 文档解析

```
1. 点击上传按钮（📎）
2. 选择 .docx 或 .pdf 文件
3. 输入指令："请总结这个文档的核心内容"
4. TaskAgent 自动调用对应解析工具
```

## 项目结构

```
src/main/java/com/msxf/agentscope/
├── controller/
│   └── ChatController.java       # SSE 聊天 + 文件上传
├── service/
│   └── AgentService.java         # Agent 工厂，流式处理
├── model/
│   └── SimpleTools.java          # 演示工具集
└── tool/
    ├── DocxParserTool.java       # DOCX 解析工具
    └── PdfParserTool.java        # PDF 解析工具

src/main/resources/
├── skills/                       # 技能定义
│   ├── docx/SKILL.md
│   └── pdf/SKILL.md
├── templates/
│   └── chat.html                 # 单页聊天 UI
└── application.yml               # 配置文件
```

## 配置

主要配置在 `application.yml`：

```yaml
agentscope:
  model:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}    # 通过环境变量设置
      model-name: qwen-plus
      enable-thinking: true

spring:
  servlet:
    multipart:
      max-file-size: 50MB              # 文件上传限制
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.6 | Web 框架 |
| AgentScope | 1.0.11 | Agent 框架 |
| Java | 21 | 运行环境 |
| Apache POI | 5.3.0 | DOCX 解析 |
| Apache PDFBox | 3.0.4 | PDF 解析 |
| Thymeleaf | - | 模板引擎 |

## 架构设计

### SkillBox 模式

Task Agent 使用 SkillBox 实现技能与工具的绑定：

```java
SkillBox skillBox = new SkillBox(toolkit);
try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
    skillBox.registration()
        .skill(repo.getSkill("docx"))
        .tool(new DocxParserTool())
        .apply();
}
```

### SSE 事件流

| 事件类型 | 说明 |
|---------|------|
| `thinking` | Agent 思考过程 |
| `text` | 响应文本 |
| `tool_call` | 工具调用 |
| `tool_result` | 工具返回结果 |
| `done` | 流结束 |

## 添加新技能

1. 创建工具类（带 `@Tool` 注解）
2. 在 `src/main/resources/skills/` 创建 `SKILL.md`
3. 在 `AgentService` 中注册技能

详见 [CLAUDE.md](./CLAUDE.md)。

## 开发

```bash
# 编译
mvn clean compile

# 测试
mvn test

# 打包
mvn package
```

## License

[Apache License 2.0](./LICENSE)

## Links

- [AgentScope 官方文档](https://java.agentscope.io/)
- [DashScope 控制台](https://dashscope.console.aliyun.com/)
