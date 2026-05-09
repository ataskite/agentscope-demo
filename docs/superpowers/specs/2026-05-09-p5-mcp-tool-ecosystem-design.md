# P5: MCP Tool Ecosystem Design

**Date**: 2026-05-09
**Phase**: P5
**Status**: Design approved

## Goal

Connect agents to the MCP (Model Context Protocol) tool ecosystem. Demonstrate all three MCP transports (StdIO, SSE, HTTP), tool filtering, and tool groups — configured from YAML with zero Java integration code needed per new MCP server.

Scope: StdIO + SSE + HTTP + Filtering + Groups. Higress gateway excluded.

## Architecture

### Approach: McpClientService centralized management

A new `McpClientService` manages all MCP client lifecycles. Agents reference servers by name from `agents.yml`, and `AgentFactory` registers MCP tools to the agent's toolkit during creation. This enables client reuse across agents (critical for StdIO which spawns subprocesses).

## Configuration

### `config/mcp-servers.yml` — Server connection definitions

```yaml
servers:
  - name: filesystem-local
    transport: STDIO
    command: npx
    args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
    timeout: 120
    initTimeout: 30

  - name: demo-remote
    transport: SSE
    url: http://localhost:9090/sse
    headers:
      Authorization: "Bearer ${MCP_API_TOKEN:}"
    queryParams:
      version: v1
    timeout: 60

  - name: demo-api
    transport: HTTP
    url: http://localhost:9091/mcp
    timeout: 60
```

Fields per server:
- `name`: unique identifier, referenced from agents.yml
- `transport`: `STDIO` | `SSE` | `HTTP`
- `command`/`args`: StdIO only, subprocess launch
- `url`: SSE/HTTP only, server endpoint
- `headers`: SSE/HTTP optional, map of key-value pairs, supports `${ENV_VAR:default}` placeholders for values
- `queryParams`: SSE/HTTP optional, map of key-value pairs appended to URL query string; individual params via `queryParam(k,v)`, batch via `queryParams(Map)`; merged with any params already in the URL (extra params take precedence)
- `timeout`: request timeout in seconds (optional, applies to all transports)
- `initTimeout`: initialization timeout in seconds (optional, applies to all transports)

> **Note**: `headers` and `queryParams` only affect SSE/HTTP transports. For StdIO they are silently ignored.

### `agents.yml` additions

New fields on agent config:

```yaml
mcpServers:                              # list of MCP server references
  - server: filesystem-local             # name from mcp-servers.yml
    enableTools: ["read_file", "list_directory", "write_file"]
    # disableTools: ["delete_file"]      # alternative to enableTools
    group: null                          # tool group name, null = no group

toolGroups:                              # tool group definitions
  - name: filesystem
    description: "文件操作工具"
    active: true
  - name: web-search
    description: "网络搜索工具"
    active: true
```

## Core Components

### New files

| File | Responsibility |
|---|---|
| `mcp/McpServerConfig.java` | Single MCP server config entity (name, transport, command/args, url, headers, queryParams, timeouts) |
| `mcp/McpClientService.java` | MCP client lifecycle: `@PostConstruct` creates all clients, caches by name, `@PreDestroy` closes all. Provides `getClient(name)` |
| `mcp/McpServerRef.java` | Agent config's MCP reference: server name, enableTools, disableTools, group |
| `mcp/ToolGroupConfig.java` | Tool group definition: name, description, active |
| `mcp/McpDemoServer.java` | Manages supergateway subprocesses to proxy StdIO MCP server to SSE (port 9090) and HTTP (port 9091) transports |

### Modified files

| File | Change |
|---|---|
| `agent/AgentConfig.java` | Add `mcpServers` (List\<McpServerRef\>) and `toolGroups` (List\<ToolGroupConfig\>) fields |
| `agent/AgentFactory.java` | Inject `McpClientService`. When `mcpServers` non-empty: create tool groups, get clients, register with filtering/grouping via `toolkit.registration().mcpClient(client).enableTools/disableTools/group().apply()` |
| `AgentConfigService.java` | Load `mcp-servers.yml` alongside `agents.yml` |

### McpClientService initialization flow

1. `@PostConstruct`: read `mcp-servers.yml`, iterate servers
2. For each server: `McpClientBuilder.create(name)` → configure transport → `buildAsync().block()`
3. StdIO: `.stdioTransport(command, args...)`
4. SSE: `.sseTransport(url).header(k,v).queryParam(k,v)...`
5. HTTP: `.streamableHttpTransport(url).header(k,v).queryParam(k,v)...`
6. Store in `Map<String, McpClientWrapper>`
7. `@PreDestroy`: close all clients

### AgentFactory MCP registration flow

1. Check `agentConfig.mcpServers` is non-empty
2. If `toolGroups` defined, call `toolkit.createToolGroup(name, description, active)` for each
3. For each `McpServerRef`:
   - `McpClientWrapper client = mcpClientService.getClient(ref.server)`
   - `toolkit.registration().mcpClient(client)`
   - Apply `enableTools` / `disableTools` if present
   - Apply `group` if present
   - `.apply()`
4. Continue with existing agent build (toolkit → ReActAgent)

## Demo Agents

### 1. `mcp-filesystem` — StdIO + Tool Filtering

- Transport: StdIO → `@modelcontextprotocol/server-filesystem /tmp`
- Scenario: file management assistant
- Filtering: enable `read_file`, `list_directory`, `write_file`; disable `delete_file`
- Sample prompts: "列出 /tmp 目录下的文件" / "在 /tmp 下创建 test.txt 写入 hello world"

### 2. `mcp-remote-sse` — SSE Transport

- Transport: SSE → embedded supergateway proxy at `localhost:9090/sse`
- Scenario: remote service integration (echo, add, sampleLLM from server-everything)
- Sample prompts: "调用远程 echo 工具" / "用 add 工具计算 3+5"

### 3. `mcp-api-http` — HTTP Transport

- Transport: HTTP → embedded supergateway proxy at `localhost:9091/mcp`
- Scenario: stateless API tool integration
- Sample prompts: "通过 HTTP 传输调用 echo 工具"

### 4. `mcp-multi-mode` — Tool Groups (single agent, multiple groups)

- Connections: filesystem-local (group: filesystem) + demo-remote (group: web-search)
- Tool groups: filesystem + web-search, both active by default
- Scenario: multi-mode assistant with grouped tool activation
- Sample prompts: "查看 /tmp 文件列表" / "生成一个 UUID"

### 5. `mcp-readonly` — Shared MCP client with restricted filtering

- Connection: filesystem-local, only `read_file` + `list_directory` enabled
- Scenario: read-only file viewer sharing the same MCP client as mcp-filesystem
- Sample prompts: "读取 /tmp/test.txt 的内容"

## Embedded Demo MCP Server

`McpDemoServer.java` uses `supergateway` (npm) to proxy a StdIO MCP server (`@modelcontextprotocol/server-everything`) to SSE and HTTP transports. This avoids writing a custom Java MCP server while providing realistic SSE/HTTP endpoints.

- Implementation: `ProcessBuilder` launches two `supergateway` subprocess instances
  - SSE proxy: `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 9090 --transport sse`
  - HTTP proxy: `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 9091 --transport streamable-http`
- Lifecycle: `@PostConstruct` starts processes, waits for ports to become available (TCP connect check with retry), `@PreDestroy` destroys processes
- Startup ordering: `McpDemoServer` must be initialized BEFORE `McpClientService`. Enforce via `@DependsOn("mcpDemoServer")` on `McpClientService`
- Built-in demo tools provided by `server-everything`: `echo`, `add`, `longRunningOperation`, `sampleLLM`, etc.
- Purpose: self-contained SSE/HTTP demo without external server dependencies
- Prerequisite: Node.js 18+ must be installed for `npx`

`mcp-servers.yml` URLs:
- SSE: `http://localhost:9090/sse`
- HTTP: `http://localhost:9091/mcp`

Fallback: if `supergateway` is unavailable or Node.js is not installed, the SSE/HTTP demo agents will fail gracefully with a clear error message in the chat response. StdIO agents are unaffected.

## Frontend Impact

- No UI changes required. MCP tools appear as `tool_start`/`tool_end` events in the debug panel automatically.
- New agents appear in the sidebar via existing category-based menu.
- Optional enhancement: show MCP server connection status and available tools in agent config modal (deferred).

## Dependencies

No new Maven dependencies needed. MCP client functionality is included in `agentscope-core` 1.0.12.

## Out of Scope (Future)

- **Elicitation**: AgentScope supports `asyncElicitation`/`syncElicitation` for interactive information collection during MCP tool calls. This requires UI integration (dialog prompts) and is deferred to a later phase.
- **Higress AI Gateway**: Semantic tool search via Higress gateway (`HigressMcpClientBuilder`/`HigressToolkit`). Excluded per user decision.
- **MCP client removal at runtime**: `toolkit.removeMcpClient(name)` for dynamic unregistration. Not needed for static config demo.
- **Sync client mode**: `buildSync()` alternative. P5 uses `buildAsync().block()` exclusively as recommended by docs.

## Success Criteria

- `mcp-filesystem` agent can read/write/list files in /tmp via StdIO MCP
- `mcp-remote-sse` agent calls demo tools via SSE transport
- `mcp-api-http` agent calls demo tools via HTTP transport
- Tool filtering works: `mcp-filesystem` cannot delete files, `mcp-readonly` cannot write
- Tool groups work: `mcp-multi-mode` has grouped filesystem + web-search tools
- MCP clients are shared across agents referencing the same server
- New MCP servers can be added by editing `mcp-servers.yml` + `agents.yml` with zero Java code
