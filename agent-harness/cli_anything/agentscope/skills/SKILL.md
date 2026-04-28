---
name: "cli-anything-agentscope"
description: "CLI harness for AgentScope Demo — agent chat, session management, knowledge base, and file operations"
---

# cli-anything-agentscope

Command-line interface for the AgentScope Demo server. Provides agent chat, session management, knowledge base, and file upload/download operations.

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

### knowledge — Knowledge base management

| Command | Description |
|---------|-------------|
| `knowledge list` | List indexed documents |
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
- `agent list|use <id>|info <id>`
- `session list|create [agent]|use <id>|delete <id>`
- `knowledge list|upload <path>|search <query>`
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

# Upload a file
cli-anything-agentscope --json upload file document.docx
```

### Agent-specific usage patterns

```bash
# Chat with a specific agent
cli-anything-agentscope chat send -a tool-test-simple "What time is it?"

# Use vision agent with an image
cli-anything-agentscope chat send -a vision-analyzer --file photo.jpg "Describe this image"

# Multi-agent pipeline
cli-anything-agentscope --json agent info doc-analysis-pipeline

# Bank invoice generation
cli-anything-agentscope chat send -a bank-invoice "Generate invoice for 张三丰, loan amount 500000"
```

## JSON Output Format

All commands support `--json` for machine-readable output:

```json
// server status
{"status": "ok", "url": "http://localhost:8080", "agent_count": 19}

// agent list
[{"agentId": "chat-basic", "name": "Basic Chat", "type": "SINGLE", ...}]

// chat send --no-stream
{"text": "response text", "events": [...]}

// knowledge search
{"query": "...", "count": 2, "results": [{"content": "...", "score": "0.85"}]}
```

## Error Handling

- Server unreachable: Clear error message with start instructions
- HTTP errors: Status code and message included in output
- All errors exit with code 1 in one-shot mode
