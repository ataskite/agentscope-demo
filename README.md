# AgentScope Demo

基于 [AgentScope](https://java.agentscope.io/) 的 Java AI Agent 演示项目，支持多种 Agent 类型、工具调用、文档解析和模板生成。

## 特性

- **多种 Agent 类型**
  - **Basic Chat**: 纯对话 AI 助手
  - **Tool Calling**: 带工具调用的 AI 助手（时间、计算器、天气）
  - **Task Agent**: 支持文档解析的智能助手（.docx/.pdf/.xlsx）
  - **Template Editor**: Word 模板变量替换
  - **Tianjin Bank Invoice**: 天津银行发票自动生成

- **响应式流式架构**
  - 基于 Project Reactor 的 SSE 实时推送
  - 完整的可观测性：Agent 生命周期、LLM 调用、工具执行、Token 使用量
  - 调试面板实时显示思考过程和工具调用状态

- **技能系统 (SkillBox)**
  - 通过 ClasspathSkillRepository 加载技能
  - 支持渐进式工具披露（Progressive Tool Disclosure）
  - 纯 Java 实现，无 Python 依赖

- **文档处理**
  - DOCX 解析与生成（Apache POI）
  - PDF 解析（Apache PDFBox）
  - XLSX 解析与生成
  - Word 模板变量替换
  - 天津银行发票 Excel/Word 自动生成

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
1. 点击上传按钮（📎）
2. 选择 .docx、.pdf 或 .xlsx 文件
3. 输入指令："请总结这个文档的核心内容"
4. TaskAgent 自动调用对应解析工具
```

### Template Editor - Word 模板编辑

```
1. 上传 .docx 模板文件（包含变量占位符）
2. Agent 解析模板识别占位符
3. 提供需要填入的值
4. 自动生成填充后的文档
```

### Tianjin Bank Invoice - 发票生成

```
请帮我生成天津银行发票。
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
- **Excel**: `天津银行_张某某_240410_001.xlsx`
- **Word**: `天津银行_张某某_240410_001.docx`

## 项目结构

```
src/main/java/com/msxf/agentscope/
├── controller/
│   └── ChatController.java       # 响应式 SSE 聊天 + 文件上传
├── service/
│   └── AgentService.java         # Agent 路由，实例缓存
├── agent/
│   ├── AgentConfig.java          # Agent 配置实体
│   ├── AgentConfigService.java   # 配置加载服务
│   └── AgentFactory.java         # Agent 创建工厂
├── runtime/
│   ├── AgentRuntime.java         # 运行时容器
│   └── AgentRuntimeFactory.java  # 运行时工厂
├── hook/
│   └── ObservabilityHook.java    # 可观测性 Hook
├── model/
│   ├── ChatRequest.java          # 请求模型
│   └── ChatEvent.java            # 事件模型
└── tool/
    ├── ToolRegistry.java         # 工具注册表
    ├── SimpleTools.java          # 演示工具集
    ├── DocxParserTool.java       # DOCX 解析
    ├── PdfParserTool.java        # PDF 解析
    ├── XlsxParserTool.java       # XLSX 解析
    └── TianjinBankInvoiceTool.java # 天津银行发票生成

src/main/resources/
├── skills/                       # 技能定义
│   ├── docx/SKILL.md
│   ├── pdf/SKILL.md
│   ├── xlsx/SKILL.md
│   ├── docx-template/SKILL.md
│   └── tianjin_bank_invoice_java/
│       ├── SKILL.md
│       └── assets/               # 模板文件
├── config/
│   └── agents.yml                # Agent 配置（YAML）
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
| Spring Boot | 3.5.13 | Web 框架 |
| AgentScope | 1.0.11 | Agent 框架 |
| Java | 17 | 运行环境 |
| Apache POI | 5.5.1 | DOCX/XLSX 解析生成 |
| Apache PDFBox | 3.0.7 | PDF 解析 |
| Project Reactor | - | 响应式流 |
| Thymeleaf | - | 模板引擎 |

## 可观测性

调试面板实时显示完整的 Agent 执行过程：

| 事件 | 说明 |
|------|------|
| `agent_start` | Agent 开始处理 |
| `llm_start` | LLM 调用开始 |
| `thinking` | 思考过程流式输出 |
| `llm_end` | LLM 调用结束（含 Token 统计） |
| `tool_start` | 工具执行开始 |
| `tool_end` | 工具执行结束（含耗时） |
| `agent_end` | Agent 完成（含总耗时） |
| `text` | 响应文本流式输出 |

## Agent 配置

Agent 在 `config/agents.yml` 中定义，格式如下：

```yaml
agents:
  - agentId: my-agent
    name: My Agent
    description: Agent 描述
    systemPrompt: |
      系统提示词
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    skills: []
    userTools: []
    systemTools: []
```

### 内置 Agent

| Agent ID | 名称 | 功能 |
|----------|------|------|
| `chat-basic` | Basic Chat | 简单对话 |
| `tool-test-simple` | Tool Calling | 时间、计算器、天气 |
| `task-document-analysis` | Task Agent | 文档解析 |
| `task-template-docx-editor` | Template Editor | Word 模板编辑 |
| `tianjin-bank-invoice` | Tianjin Bank Invoice | 发票生成 |

## 添加新技能

1. 创建工具类（带 `@Tool` 注解）
2. 在 `src/main/resources/skills/` 创建 `SKILL.md`
3. 在 `ToolRegistry` 中注册技能
4. 在 `config/agents.yml` 中配置 Agent

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
- [项目开发指南](./CLAUDE.md)
