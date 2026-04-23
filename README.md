# AgentScope Demo

基于 [AgentScope](https://java.agentscope.io/) 的 Java AI Agent 演示项目，支持多种 Agent 类型、工具调用、文档解析、多模态交互、RAG 知识库和多智能体协作。

## 特性

### 🤖 多种 Agent 类型
- **Basic Chat**: 纯对话 AI 助手
- **Tool Calling**: 带工具调用的 AI 助手（时间、计算器、天气）
- **Task Agent**: 支持文档解析的智能助手（.docx/.pdf/.xlsx）
- **Template Editor**: Word 模板变量替换
- **Bank Invoice**: 银行发票自动生成
- **Vision Analyzer**: 图片理解和 OCR 文字识别
- **Voice Assistant**: 语音交互助手
- **Search Assistant**: 网络搜索和实时信息查询
- **Project Planner**: 项目规划和任务分解

### 🎨 多模态支持
- **文本**: 自然语言对话
- **图片**: OCR 文字识别、图表分析、场景理解
- **语音**: 语音转文字、语音交互

### 🔍 RAG 知识库
- 上传文档（PDF、DOCX、TXT、MD）
- 向量相似度搜索
- 智能问答

### 🗂️ 会话管理
- 持久化会话历史
- 会话隔离
- 多会话管理

### 🤝 多智能体协作
- **Sequential Pipeline**: 串行执行多个子智能体
- **Intelligent Routing**: 自动路由到专家智能体
- **Agent Handoffs**: 智能体交接

### 📊 实时调试面板
- Agent 生命周期追踪
- LLM 调用详情（Token 使用量）
- 工具执行状态和耗时
- 思考过程可视化

## 快速开始

### 环境要求

- Java 17+
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
1. 点击上传按钮（+）
2. 选择 .docx、.pdf 或 .xlsx 文件
3. 输入指令："请总结这个文档的核心内容"
4. TaskAgent 自动调用对应解析工具
```

### Vision Analyzer - 图片分析

```
1. 点击上传按钮（+）
2. 选择图片文件（.jpg, .png, .gif, .webp）
3. 自动切换到视觉分析智能体
4. 输入问题："图片中有什么文字？"
```

### RAG Chat - 知识库问答

```
1. 点击知识库图标
2. 上传文档（PDF、DOCX、TXT）
3. 切换到 "RAG Chat" 智能体
4. 提问与文档相关的问题
```

### Bank Invoice - 发票生成

```
请帮我生成银行发票。
客户姓名：张三丰
身份证号：110101199003072316
手机：13802213478
邮箱：test@example.com
合同号：HT20240410001
借据号：JD20240410001
放款日期：2024-04-10
贷款总额：100000
银行放款金额：80000
费用类型：服务费
发票金额：5000
流水号：001
```

自动生成：
- **Excel**: `XX银行_张某某_240410_001.xlsx`
- **Word**: `XX银行_张某某_240410_001.docx`

## 项目结构

```
src/main/java/com/skloda/agentscope/
├── controller/
│   ├── ChatController.java         # 响应式 SSE 聊天 + 文件上传
│   └── KnowledgeController.java    # 知识库管理 API
├── service/
│   ├── AgentService.java           # Agent 路由，实例缓存
│   ├── SessionManagerService.java  # 会话管理
│   └── KnowledgeService.java       # RAG 知识库
├── agent/
│   ├── AgentConfig.java            # Agent 配置实体
│   ├── AgentConfigService.java     # 配置加载服务
│   └── AgentFactory.java           # Agent 创建工厂
├── runtime/
│   ├── AgentRuntime.java           # 运行时容器
│   └── AgentRuntimeFactory.java    # 运行时工厂
├── hook/
│   └── ObservabilityHook.java      # 可观测性 Hook
├── model/
│   ├── ChatRequest.java            # 请求模型
│   ├── ChatEvent.java              # 事件模型
│   ├── SessionInfo.java            # 会话信息
│   └── MultiModalMessage.java      # 多模态消息
└── tool/
    ├── ToolRegistry.java           # 工具注册表
    ├── SimpleTools.java            # 演示工具集
    ├── DocxParserTool.java         # DOCX 解析
    ├── PdfParserTool.java          # PDF 解析
    ├── XlsxParserTool.java         # XLSX 解析
    ├── BankInvoiceTool.java        # 银行发票生成
    └── WebSearchTool.java          # 网络搜索工具

src/main/resources/
├── skills/                         # 技能定义
│   ├── docx/SKILL.md
│   ├── pdf/SKILL.md
│   ├── xlsx/SKILL.md
│   ├── docx-template/SKILL.md
│   └── bank_invoice_java/
│       ├── SKILL.md
│       └── assets/                 # 模板文件
├── config/
│   └── agents.yml                  # Agent 配置（YAML）
├── templates/
│   └── chat.html                   # 单页聊天 UI
├── static/
│   ├── scripts/                    # 前端模块化 JS
│   │   ├── chat.js
│   │   ├── api.js
│   │   ├── state.js
│   │   └── modules/
│   │       ├── agents.js
│   │       ├── session.js
│   │       ├── knowledge.js
│   │       ├── upload.js
│   │       ├── debug.js
│   │       ├── ui.js
│   │       └── utils.js
│   └── styles/                     # 模块化 CSS
│       ├── chat.css
│       └── modules/
│           ├── header.css
│           ├── sidebar.css
│           ├── chat.css
│           ├── debug.css
│           ├── modal.css
│           └── upload.css
└── application.yml                 # 配置文件
```

## 配置

主要配置在 `application.yml`：

```yaml
agentscope:
  model:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}    # 通过环境变量设置
  session:
    storage-path: ${user.home}/.agentscope/demo-sessions
  knowledge:
    dimensions: 1024                   # 向量维度

spring:
  servlet:
    multipart:
      max-file-size: 50MB              # 文件上传限制
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.13 | Web 框架 |
| AgentScope | 1.0.11 | Agent 框架 |
| Java | 17 | 运行环境 |
| Apache POI | 5.5.1 | DOCX/XLSX 解析生成 |
| Apache PDFBox | 3.0.7 | PDF 解析 |
| Project Reactor | - | 响应式流 |
| Thymeleaf | - | 模板引擎 |

## 可观测性

调试面板实时显示完整的 Agent 执行过程：

| 事件 | 说明 | 显示位置 |
|------|------|----------|
| `agent_start` | Agent 开始处理 | 调试面板 |
| `llm_start` | LLM 调用开始 | 调试面板 |
| `thinking` | 思考过程流式输出 | 调试面板 |
| `llm_end` | LLM 调用结束（含 Token 统计） | 调试面板 |
| `tool_start` | 工具执行开始 | 调试面板 |
| `tool_end` | 工具执行结束（含耗时） | 调试面板 |
| `agent_end` | Agent 完成（含总耗时） | 调试面板 |
| `text` | 响应文本流式输出 | 主聊天区 |
| `error` | 错误信息 | 警告提示 |

## 内置 Agent

| Agent ID | 名称 | 功能 | 模型 |
|----------|------|------|------|
| `chat-basic` | Basic Chat | 简单对话 | qwen-plus |
| `tool-test-simple` | Tool Calling | 时间、计算器、天气 | qwen-plus |
| `task-document-analysis` | Task Agent | 文档解析 | qwen-plus |
| `task-template-docx-editor` | Template Editor | Word 模板编辑 | qwen-plus |
| `bank-invoice` | Bank Invoice | 发票生成 | qwen-plus |
| `rag-chat` | RAG Chat | 知识库问答 | qwen-plus |
| `vision-analyzer` | Vision Analyzer | 图片理解和 OCR | qwen-vl-max |
| `voice-assistant` | Voice Assistant | 语音交互 | qwen-audio-turbo |
| `invoice-extractor` | Invoice Extractor | 发票信息提取 | qwen-vl-max |
| `idcard-extractor` | ID Card Extractor | 身份证信息提取 | qwen-vl-max |
| `search-assistant` | Search Assistant | 网络搜索 | qwen-plus |
| `project-planner` | Project Planner | 项目规划 | qwen-plus |
| `smart-router` | Smart Router | 智能路由 | qwen-plus |
| `customer-service` | Smart Customer Service | 智能客服 | qwen-plus |

## API 端点

| 方法 | 路径 | 描述 |
|------|------|-------------|
| GET | `/` | Chat UI 页面 |
| POST | `/chat/send` | 发送消息，返回 SSE 流 |
| POST | `/chat/upload` | 上传文件（multipart） |
| GET | `/chat/download` | 下载上传的文件 |
| GET | `/api/agents` | 列出所有 Agent 配置 |
| GET | `/api/agents/{agentId}` | 获取指定 Agent 配置 |
| GET | `/api/sessions` | 列出所有会话 |
| POST | `/api/sessions` | 创建新会话 |
| DELETE | `/api/sessions/{sessionId}` | 删除会话 |
| GET | `/api/knowledge/documents` | 列出知识库文档 |
| POST | `/api/knowledge/upload` | 上传文档到知识库 |
| DELETE | `/api/knowledge/documents/{fileName}` | 从知识库删除文档 |

## 添加新 Agent

1. 在 `src/main/resources/config/agents.yml` 中添加配置
2. 如果使用新工具类，在 `tool/` 目录创建带 `@Tool` 注解的方法
3. 在 `ToolRegistry` 构造函数中注册工具
4. 如果使用技能，创建 `skills/<name>/SKILL.md`
5. 重启应用 — Agent 自动出现在 UI 中

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
- [项目开发指南](./CLAUDE.md)
