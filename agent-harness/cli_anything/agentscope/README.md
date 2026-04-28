# cli-anything-agentscope

CLI harness for the AgentScope Demo server. Provides command-line and REPL access to agent chat, session management, knowledge base, and file operations.

## Prerequisites

1. **AgentScope Demo server** running on http://localhost:8080
   ```bash
   cd /path/to/agentscope-demo
   export DASHSCOPE_API_KEY=your_key_here
   mvn spring-boot:run
   ```

2. **Python 3.10+**

## Installation

```bash
cd agent-harness
pip install -e .
```

Verify:
```bash
which cli-anything-agentscope
cli-anything-agentscope --help
```

## Quick Start

```bash
# Check server
cli-anything-agentscope server status

# List agents
cli-anything-agentscope agent list

# Send a message
cli-anything-agentscope chat send "Hello, who are you?"

# Interactive REPL
cli-anything-agentscope
```

## Running Tests

```bash
# Unit tests (no server needed)
python -m pytest cli_anything/agentscope/tests/test_core.py -v

# E2E tests (requires running server)
python -m pytest cli_anything/agentscope/tests/test_full_e2e.py -v -s

# All tests
python -m pytest cli_anything/agentscope/tests/ -v

# Force installed CLI (after pip install)
CLI_ANYTHING_FORCE_INSTALLED=1 python -m pytest cli_anything/agentscope/tests/ -v -s
```

## Configuration

| Env Var | Default | Description |
|---------|---------|-------------|
| `AGENTSCOPE_BASE_URL` | `http://localhost:8080` | Server URL |
| `DASHSCOPE_API_KEY` | — | Required by the server |

## Command Reference

| Group | Commands |
|-------|----------|
| `server` | `status`, `info` |
| `agent` | `list`, `info <id>` |
| `session` | `list`, `create`, `delete <id>`, `use <id>` |
| `chat` | `send <msg>`, `metrics <msg>` |
| `knowledge` | `list`, `upload <path>`, `remove <name>`, `search <query>` |
| `upload` | `file <path>`, `download <id> -o <path>` |

All commands support `--json` for machine-readable output.
