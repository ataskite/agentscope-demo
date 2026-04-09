# AgentScope Demo Chat UI 设计文档

## 概述

将现有的两个命令行 Demo（BasicChatExample、ToolCallingExample）迁移到 Web 页面，使用 Spring Boot + Thymeleaf 实现三栏布局的实时聊天界面。

## 技术选型

| 层 | 技术 | 说明 |
|---|---|---|
| 模板引擎 | Thymeleaf | Spring Boot 原生支持，页面内联 CSS/JS |
| 流式通信 | SSE (Server-Sent Events) | 后端 SseEmitter 推送，前端 EventSource 接收 |
| 会话存储 | ConcurrentHashMap（内存） | Demo 阶段方案，后续迁移至数据库/Redis |
| 前端 | 原生 HTML/CSS/JS | 不引入前端框架 |

## 页面布局

固定三栏布局，右侧 debug 栏可折叠：

```
┌──────────────────────────────────────────────────┐
│  AgentScope Demo                                  │
├────────┬──────────────────────┬───────────────────┤
│        │  Agent 标题栏        │  Debug     [▶]    │
│ Agent  │                      │                   │
│ 列表   │   聊天消息区         │  [Thinking] ...   │
│        │                      │  [Tool Call] ...  │
│ 💬基础 │                      │  [Tool Result]... │
│ 🔧工具 │                      │  [Response] ...   │
│        ├──────────────────────┤                   │
│        │  [输入框]  [发送]    │                   │
└────────┴──────────────────────┴───────────────────┘
```

折叠状态：右侧收缩为 28px 窄条，显示 "◀ Debug" 竖排文字，点击展开。

## 后端架构

### 新增文件

| 文件 | 职责 |
|---|---|
| `controller/ChatController.java` | 页面渲染、消息接收、SSE 推送 |
| `service/AgentService.java` | Agent 实例管理、会话存储、流式调用 |
| `model/SimpleTools.java` | 自定义工具（从 ToolCallingExample 提取） |
| `templates/chat.html` | Thymeleaf 模板，内联 CSS + JS |

### 修改文件

| 文件 | 变更 |
|---|---|
| `pom.xml` | 添加 `spring-boot-starter-thymeleaf` 依赖 |

### 保留文件（不修改）

- `BasicChatExample.java` — 保留作为参考
- `ToolCallingExample.java` — 保留作为参考
- `ExampleUtils.java` — 保留
- `application.yml` — 不变

## 数据流

```
1. 用户选择 Agent → 前端记录 currentAgent，清空聊天区
2. 用户输入消息 → POST /chat/send { agentType, message }
3. 后端创建 SseEmitter → 存入 Map，返回 emitter ID
4. 前端建立 EventSource("/chat/stream?sessionId=xxx")
5. 后端调用 AgentService.stream(agentType, message, emitter)
   → Agent 流式处理
   → 推送 SSE 事件到 emitter
6. 前端按事件类型渲染到聊天区或 debug 栏
7. 收到 done 事件 → 关闭 EventSource
```

## SSE 事件格式

所有事件为 JSON 格式：

| type | 字段 | 说明 |
|---|---|---|
| `text` | `content` | 聊天回复文本片段 |
| `thinking` | `content` | Thinking 推理过程 |
| `tool_call` | `name`, `params` | 工具调用请求 |
| `tool_result` | `name`, `result` | 工具返回结果 |
| `done` | — | 回复结束 |
| `error` | `message` | 错误信息 |

## ChatController 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 返回 chat.html 页面 |
| POST | `/chat/send` | 接收消息，参数：`agentType`(String), `message`(String) |
| GET | `/chat/stream` | SSE 端点，参数：`sessionId`(String) |

## AgentService 设计

- `ConcurrentHashMap<String, ReActAgent>` 按 agentType 存储 Agent 实例
- 两种 Agent 类型：
  - `basic` — 基础聊天（无工具），对应 BasicChatExample
  - `tool` — 工具调用（3 个工具），对应 ToolCallingExample
- `stream(agentType, message, emitter)` 方法：调用 Agent 流式接口，将事件写入 SseEmitter
- Agent 实例在首次请求时懒初始化

## SimpleTools 工具列表

从 ToolCallingExample 提取的 3 个工具：

| 工具 | 参数 | 说明 |
|---|---|---|
| `get_current_time` | `timezone`(String) | 获取指定时区的当前时间 |
| `calculate_sum` | `a`(double), `b`(double) | 计算两数之和 |
| `get_weather` | `city`(String) | 获取城市天气（模拟数据） |

## 会话管理

- Demo 阶段使用内存存储（ConcurrentHashMap）
- 每个 agentType 维护独立的 InMemoryMemory 实例
- 后续迁移计划：引入数据库持久化 + Redis 缓存

## 前端交互细节

- Agent 切换时清空聊天区，保留 debug 日志
- 消息发送后禁用输入框和发送按钮，收到 done 后恢复
- 聊天气泡区分用户消息（蓝色右对齐）和 Agent 回复（灰色左对齐）
- Debug 栏日志按时间线展示，不同类型用颜色区分：
  - Thinking → 蓝色
  - Tool Call → 绿色
  - Tool Result → 绿色
  - Response → 橙色
  - Error → 红色
- 页面不需要登录，直接访问即可使用
