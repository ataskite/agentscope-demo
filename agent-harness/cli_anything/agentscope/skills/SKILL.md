---
name: "cli-anything-agentscope"
description: "CLI harness for AgentScope Demo — agent chat, session management, knowledge base, file operations, and HITL approval"
---

# cli-anything-agentscope

Command-line interface for the AgentScope Demo server. Provides agent chat, session management, knowledge base, file upload/download operations, and human-in-the-loop (HITL) approval flows.

## Prerequisites

- AgentScope Demo server running (`mvn spring-boot:run` on http://localhost:8080)
- Python 3.10+
- pip install cli-anything-agentscope

## Installation

```bash
pip install -e .
# or from PyPI:
pip install cli-anything-agentscope
```

## Global Options

| Flag | Description |
|------|-------------|
| `--json` | Output all results as JSON (for agent consumption) |
| `--server URL` | Override server URL (default: http://localhost:8080) |
| `AGENTSCOPE_BASE_URL` | Env var for server URL |

## Command Groups

### server — Server connection and status

| Command | Description |
|---------|-------------|
| `server status` | Check server health and connectivity |
| `server info` | Show detailed server information |

### agent — Agent listing and configuration

| Command | Description |
|---------|-------------|
| `agent list` | List all available agents |
| `agent info <agent_id>` | Show detailed agent configuration |
| `agent messages <agent_id>` | List chat history for an agent |
| `agent sample-prompt <agent_id> <index>` | Get sample prompt by index |
| `agent skill-info <skill_name>` | Show skill details |
| `agent tool-info <tool_name>` | Show tool details |

### session — Session management

| Command | Description |
|---------|-------------|
| `session list` | List all sessions |
| `session create [-a AGENT_ID]` | Create new session |
| `session delete <session_id>` | Delete a session |
| `session use <session_id>` | Set active session for REPL |

### chat — Send messages to agents

| Command | Description |
|---------|-------------|
| `chat send <message> [-a AGENT_ID] [-s SESSION_ID] [--file PATH] [--stream/--no-stream]` | Send message |
| `chat metrics <message>` | Send message and show debug metrics |
| `chat approve <approval_id> [--reject] [--reason TEXT]` | Approve/reject HITL request |

### knowledge — Knowledge base management

| Command | Description |
|---------|-------------|
| `knowledge list` | List indexed documents |
| `knowledge status` | Show indexing status |
| `knowledge upload <file_path>` | Upload document to knowledge base |
| `knowledge remove <file_name>` | Remove document from knowledge base |
| `knowledge search <query> [-n LIMIT] [-t THRESHOLD]` | Search knowledge base |

### upload — File upload and download

| Command | Description |
|---------|-------------|
| `upload file <file_path>` | Upload file to server |
| `upload download <file_id> -o <output>` | Download file from server |

## REPL Mode

Running with no arguments enters interactive REPL mode:

```
$ cli-anything-agentscope
```

REPL commands:
- `chat <message>` — Send message (streaming)
- `chat! <message>` — Send message with metrics
- `approve <approval_id> [--reject]` — Approve/reject HITL request
- `agent list|use <id>|info <id>|messages <id>|skill-info <name>|tool-info <name>`
- `session list|create [agent]|use <id>|delete <id>`
- `knowledge list|upload <path>|search <query>|status`
- `upload <path>`
- `server status`
- `help` — Show available commands
- `quit` / `exit` — Exit REPL

## Usage Examples

### One-shot commands (JSON output)

```bash
# Check server status
cli-anything-agentscope --json server status

# List all agents
cli-anything-agentscope --json agent list

# Send a chat message
cli-anything-agentscope --json chat send --no-stream "What is 2+2?"

# Create a session and send messages
cli-anything-agentscope --json session create -a chat-basic
cli-anything-agentscope --json chat send -s <session_id> "Hello"

# Upload and search knowledge base
cli-anything-agentscope --json knowledge upload report.pdf
cli-anything-agentscope --json knowledge search "quarterly revenue"

# Check knowledge indexing status
cli-anything-agentscope --json knowledge status

# Upload a file
cli-anything-agentscope --json upload file document.docx

# Get agent chat history
cli-anything-agentscope --json agent messages chat-basic

# Get skill/tool details
cli-anything-agentscope --json agent skill-info docx
cli-anything-agentscope --json agent tool-info parse_docx
```

### Agent-specific usage patterns

```bash
# Chat with a specific agent
cli-anything-agentscope chat send -a tool-test-simple "What time is it?"

# Use vision agent with an image
cli-anything-agentscope chat send -a vision-analyzer --file photo.jpg "Describe this image"

# Multi-agent pipeline (sequential)
cli-anything-agentscope --json agent info doc-analysis-pipeline

# Routing agent
cli-anything-agentscope --json agent info smart-router

# Loop pattern (write-review-revise)
cli-anything-agentscope chat send -a copywriter-refiner "Write a product description"

# HITL approval
cli-anything-agentscope chat approve <approval_id>
cli-anything-agentscope chat approve <approval_id> --reject --reason "Unsafe operation"
```

## JSON Output Format

All commands support `--json` for machine-readable output:

```json
// server status
{"status": "ok", "url": "http://localhost:8080", "agent_count": 30}

// agent list
[{"agentId": "chat-basic", "name": "Basic Chat", "type": "SINGLE", ...}]

// chat send --no-stream
{"text": "response text", "events": [...]}

// knowledge search
{"query": "...", "count": 2, "results": [{"content": "...", "score": "0.85"}]}

// chat approve
{"text": "response after approval", "events": [...]}
```

## Error Handling

- Server unreachable: Clear error message with start instructions
- HTTP errors: Status code and message included in output
- All errors exit with code 1 in one-shot mode
