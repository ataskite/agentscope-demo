# AGENTSCOPE.md — Software-Specific SOP

## Software: AgentScope Demo

Spring Boot 3.5.13 + Java 17 web application providing a chat UI for AgentScope AI agents.

## Architecture

- **Backend**: Spring Boot REST API with SSE streaming
- **Frontend**: Vanilla JS + HTML chat UI
- **Agent Engine**: AgentScope v1.0.11 (Java, LLM-backed ReAct agents)
- **Data Model**: JSON REST API (no local project files)
- **Session Storage**: JSON files in `~/.agentscope/demo-sessions/`
- **Knowledge Base**: Vector similarity search with DashScope embeddings

## Backend Access

Unlike GUI apps, AgentScope Demo exposes HTTP REST APIs. The CLI acts as an HTTP client:

- **Server URL**: `http://localhost:8080` (configurable)
- **No subprocess rendering** — all operations are HTTP requests
- **SSE streaming** for chat responses

## Key API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/agents` | List agent configs |
| GET | `/api/agents/{id}` | Get agent config |
| POST | `/chat/send` | Send message (SSE stream) |
| POST | `/chat/upload` | Upload file |
| GET | `/chat/download?fileId=` | Download file |
| GET | `/api/sessions` | List sessions |
| POST | `/api/sessions` | Create session |
| DELETE | `/api/sessions/{id}` | Delete session |
| GET | `/api/knowledge/documents` | List knowledge docs |
| POST | `/api/knowledge/upload` | Upload knowledge doc |
| POST | `/api/knowledge/search` | Search knowledge base |

## Agent Types

| Type | Description |
|------|-------------|
| SINGLE | Standard ReAct agent with tools/skills |
| SEQUENTIAL | Sub-agents executed in series |
| ROUTING | LLM routes to appropriate sub-agent |
| HANDOFFS | Intent-based agent switching |

## Session-Based vs Stateless

- **With session**: Agent maintains conversation memory across messages
- **Without session**: Fresh agent per request, no memory persistence

## CLI Design Decisions

1. **Stateless one-shot** — No local project files; all state is server-side
2. **REPL for interactive** — Maintain session context across messages
3. **SSE parsing** — Parse Server-Sent Events for streaming chat responses
4. **No auto-save** — Server manages all persistence; `--dry-run` not applicable
5. **HTTP backend** — Uses `requests` library; no subprocess invocation
