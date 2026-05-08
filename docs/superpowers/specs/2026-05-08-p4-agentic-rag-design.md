# P4 Agentic RAG Design

**Date**: 2026-05-08
**Phase**: P4 RAG Ecosystem (subset)
**Scope**: Agentic RAG mode only

## Goal

Demonstrate AgentScope's `RAGMode.AGENTIC` by creating a `rag-agent` that autonomously decides when to retrieve from the knowledge base, contrasting with the existing `rag-chat` which uses `RAGMode.GENERIC` (automatic retrieval on every query).

## Background

The project already has:
- `rag-chat` agent with `RAGMode.GENERIC` — knowledge is injected automatically before reasoning via `GenericRAGHook`
- `KnowledgeService` managing `SimpleKnowledge` with DashScope embeddings (text-embedding-v3, 1024d)
- `AgentFactory` supporting both `ragMode: generic` and `ragMode: agentic` via `parseRagMode()`
- `ObservabilityHook` capturing tool call events (tool_start/tool_end) in the debug panel

When `RAGMode.AGENTIC` is set, the framework registers `KnowledgeRetrievalTools` as a `@Tool` method named `retrieve_knowledge`. The agent's LLM sees this tool in its schema and decides when to call it.

## Design

### 1. Agent Configuration

Add `rag-agent` to `src/main/resources/config/agents.yml`:

```yaml
- agentId: rag-agent
  category: single
  name: Agentic RAG 研究助手
  description: 自主决定何时检索知识库的研究助手。与 rag-chat 的区别：rag-chat 自动检索所有问题，而本 Agent 会判断问题是否需要检索知识库。
  systemPrompt: |
    你是一个研究助手，擅长根据用户问题自主判断是否需要从知识库中检索信息。

    判断规则：
    - 当用户的问题涉及专业知识、技术细节、或特定文档内容时，使用 retrieve_knowledge 工具检索
    - 当用户问的是常识性问题、日常闲聊、或通用编程知识时，直接回答，不需要检索
    - 如果不确定是否需要检索，优先检索

    检索到内容后，基于检索结果回答，并标注引用来源。
    如果检索结果与问题不相关，说明知识库中没有相关信息，基于自身知识回答。
  modelName: qwen-plus
  streaming: true
  enableThinking: true
  ragEnabled: true
  ragMode: agentic
  ragRetrieveLimit: 5
  ragScoreThreshold: 0.3
```

Key decisions:
- Same model (qwen-plus) as `rag-chat` for fair comparison
- `enableThinking: true` so users can see the agent's reasoning about whether to retrieve
- System prompt includes explicit decision rules to reduce false negatives

### 2. ObservabilityHook Enhancement

In `ObservabilityHook.java`, enhance the `PostActingEvent` handler to detect `retrieve_knowledge` tool calls and add RAG-specific fields to the `tool_end` event.

**Detection**: Check if the tool name equals `"retrieve_knowledge"`.

**Enhanced event payload**:
```json
{
  "type": "tool_end",
  "toolName": "retrieve_knowledge",
  "result": "...",
  "duration": 150,
  "isRagRetrieval": true,
  "ragQuery": "EML光模块技术原理",
  "ragHitCount": 3,
  "ragScoreRange": "0.65-0.89"
}
```

**Extraction logic**: Parse the tool result string to extract hit count and score range. The framework's `KnowledgeRetrievalTools.retrieveKnowledge()` returns a formatted string listing documents with relevance scores, so we can parse it with a simple regex or string matching.

### 3. Frontend Debug Panel Enhancement

In `debug.js`, detect `tool_end` events with `isRagRetrieval: true` and render them differently:

- Use a knowledge/book icon instead of the generic tool icon
- Show the retrieval query, hit count, and score range inline
- On expand, show the full retrieval result

In `debug.css`, add styling for the RAG-specific tool call rendering (e.g., a subtle blue background to distinguish from normal tool calls).

### 4. No Changes Required

- `KnowledgeService` — shared with `rag-chat`, no modifications
- `AgentFactory` — already supports `ragMode: agentic` via `parseRagMode()`
- `AgentConfig` — already has `ragMode` field with default `"generic"`
- `ChatController` — generic SSE streaming works for all agent types
- `ToolRegistry` — framework handles `retrieve_knowledge` tool registration automatically

## Files Changed

| File | Change |
|------|--------|
| `src/main/resources/config/agents.yml` | Add `rag-agent` entry |
| `src/main/java/.../hook/ObservabilityHook.java` | Detect `retrieve_knowledge` tool calls, add RAG fields |
| `src/main/resources/static/scripts/modules/debug.js` | RAG tool call special rendering |
| `src/main/resources/static/styles/modules/debug.css` | RAG tool call styling |

## Success Criteria

1. `rag-agent` appears in the agent list under "Single Agent" category
2. For common-sense questions ("你好", "什么是 Java"), the agent responds directly without calling `retrieve_knowledge`
3. For knowledge-base questions, the debug panel shows: agent thinking → `retrieve_knowledge` tool call (with query, hit count, scores) → response based on retrieved content
4. The behavioral difference between `rag-chat` (auto-retrieval, invisible in debug panel) and `rag-agent` (on-demand retrieval, visible as tool call) is clear in the debug panel

## Out of Scope

- Qdrant persistent store
- Cloud knowledge backends (Bailian, Dify, RAGFlow)
- Citation UI (chunk preview, citation links)
- Agent auto-switch logic for document uploads
