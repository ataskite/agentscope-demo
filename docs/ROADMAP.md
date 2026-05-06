# AgentScope Demo Roadmap

**Date**: 2026-05-06
**Scope**: AgentScope Java feature-complete demo evolution
**Status**: Milestone `p3-planning-memory` completed; next milestone `feature-complete-core` (P4 RAG ecosystem)

## Progress Snapshot

Last updated: 2026-05-07

Completed:

- [x] P0 Multi-agent foundation: unified streaming runtime, composite agent dispatch, debug events.
- [x] P1 Multi-agent showcase: routing, handoffs, supervisor, debate/review, sample prompts.
- [x] P2 Controlled workflows: HITL approval hook, structured output (invoice/ID card/contract), validation & repair, workflow snapshots.
- [x] Manual approval resume flow validated with real model credentials.
- [x] 140 unit tests passing, JaCoCo coverage configured.
- [x] P3 Planning & Memory: PlanNotebook (project-planner), AutoContextMemory demo (long-conversation), Bailian long-term memory (personal-assistant). 158 tests passing.
- [x] Cyberpunk-themed UI with categorized agent menu (Single/Expert/Collaboration).
- [x] Generic RAG with local knowledge indexing, DashScope embeddings, status tracking.
- [x] Vision (OCR, chart, scene) and audio (speech-to-text) multimodal agents.
- [x] Session persistence via AgentScope SessionManager.
- [x] ObservabilityHook with timeline, metrics, thinking, and tool-call detail events.
- [x] SSE streaming chat UI with real-time debug panel.

Current TODO:

- [ ] Start P4 RAG ecosystem demos.
- [ ] Implement remaining AgentScope features as real demo scenarios.

## 1. Positioning

This project is a **feature-complete AgentScope Java showcase**: every major capability the framework offers should have at least one runnable, visible demo with a realistic business scenario and a polished UI outcome.

The demo should:

- Demonstrate every core framework capability through concrete business scenarios.
- Make each feature observable, controllable, and reproducible via the debug panel.
- Serve as a learning reference for developers adopting AgentScope Java.
- Keep the codebase approachable — YAML-first configuration, scenario-driven features.

## 2. Feature Coverage Matrix

**Legend**: ✅ Implemented | 🔧 Partial | ❌ Not started

### Core Agent

| AgentScope Feature | Status | Demo Agent / Scenario |
|---|---|---|
| ReAct Agent (Reasoning + Acting loop) | ✅ | `chat-basic`, `tool-test-simple` |
| Tool Calling (@Tool POJO methods) | ✅ | All tool-calling agents |
| Streaming (Reactive SSE) | ✅ | `/chat/send` SSE endpoint |
| Session / State persistence | ✅ | SessionManager, JSON file storage |
| Hook System (lifecycle events) | ✅ | `ObservabilityHook` (timeline, metrics) |
| Multi-modal — Vision | ✅ | `vision-analyzer` (qwen-vl-max) |
| Multi-modal — Audio | ✅ | `voice-assistant` (qwen-audio-turbo) |
| Structured Output (TOOL_CHOICE/PROMPT) | ✅ | `invoice-extractor`, `idcard-extractor`, `contract-extractor` |
| Structured Output validation & repair | ✅ | `StructuredOutputValidator`, one-pass retry |
| Human-in-the-Loop (HITL) | ✅ | `bank-invoice`, `contract-review-workflow`, `ApprovalHook` |
| PlanNotebook (structured planning) | ✅ | `project-planner` (planEnabled, 10 built-in planning tools) |
| Agent Interrupt / Cancel | 🔧 | Partially via HITL; need runtime interrupt |

### Memory

| AgentScope Feature | Status | Demo Agent / Scenario |
|---|---|---|
| InMemoryMemory (short-term) | ✅ | All session-based agents |
| AutoContextMemory (auto-compression) | ❌ | Planned: long-conversation agent |
| Long-term Memory — Mem0 | ❌ | Planned: cross-session preference recall |
| Long-term Memory — ReMe | ❌ | Planned: cross-session knowledge retention |
| Long-term Memory — Bailian | ✅ | `personal-assistant` (STATIC_CONTROL, DASHSCOPE_API_KEY) |
| Long-term Memory Mode (STATIC/AGENT/BOTH) | ✅ | `personal-assistant` (configurable via agents.yml) |

### RAG & Knowledge

| AgentScope Feature | Status | Demo Agent / Scenario |
|---|---|---|
| RAG Generic Mode (auto-inject) | ✅ | `rag-chat` |
| RAG Agentic Mode (on-demand tool) | ❌ | Planned: `rag-agent` with `retrieve_knowledge` tool |
| Local Knowledge — SimpleKnowledge | ✅ | InMemoryStore + DashScope embeddings |
| Local Knowledge — Qdrant persistent store | ❌ | Planned: production RAG demo |
| Cloud Knowledge — Bailian | ❌ | Planned: enterprise knowledge base |
| Cloud Knowledge — Dify | ❌ | Planned: Dify integration demo |
| Cloud Knowledge — RAGFlow | ❌ | Planned: RAGFlow integration demo |
| Citation display & chunk preview | ❌ | Planned: RAG UI enhancement |

### Multi-Agent Patterns

| AgentScope Pattern | Status | Demo Agent / Scenario |
|---|---|---|
| Pipeline — Sequential | ✅ | `doc-analysis-pipeline` |
| Pipeline — Parallel | ✅ | Debate prelude, parallel fanout |
| Routing (classify → specialist → merge) | ✅ | `smart-router` |
| Handoffs (state-driven agent switching) | ✅ | `customer-service` |
| Supervisor (one tool per specialist) | ✅ | `super-supervisor` |
| Debate (MsgHub + moderator) | ✅ | `debate-review` |
| Agent as Tool (sub-agent invocation) | ✅ | SubAgentTool in routing/supervisor |
| Subagents (Task/TaskOutput orchestration) | ❌ | Planned: orchestrator with sub-agent tasks |
| Skills (progressive disclosure) | ✅ | SKILL.md + `read_skill` via ToolRegistry |
| MsgHub (group conversation) | 🔧 | Debate uses MsgHub pattern; need standalone demo |
| Custom Workflow (StateGraph) | ❌ | Planned: mixed deterministic + agentic steps |

### Tool Ecosystem

| AgentScope Feature | Status | Demo Agent / Scenario |
|---|---|---|
| Custom @Tool POJO tools | ✅ | 12+ tools registered |
| System tools (file, shell) | ✅ | `view_text_file`, `write_text_file`, `execute_shell_command` |
| Skill system (SKILL.md) | ✅ | 6 skills configured |
| MCP — StdIO transport | ❌ | Planned: local MCP server connection |
| MCP — SSE transport | ❌ | Planned: remote MCP server connection |
| MCP — HTTP transport | ❌ | Planned: stateless MCP connection |
| MCP — Tool filtering (enable/disable) | ❌ | Planned: selective MCP tool registration |
| MCP — Tool Groups | ❌ | Planned: grouped tool activation |
| MCP — Higress AI Gateway | ❌ | Planned: semantic tool search |
| Tool allowlist / denylist per agent | ❌ | Planned: per-agent tool permissions |

### Interoperability

| AgentScope Feature | Status | Demo Agent / Scenario |
|---|---|---|
| A2A — Client (call remote agents) | ❌ | Planned: call remote A2A service |
| A2A — Server (expose local agents) | ❌ | Planned: expose agents as A2A service |
| A2A — Nacos discovery | ❌ | Planned: Nacos-registered agent discovery |
| AG-UI protocol | ❌ | Planned: frontend interop endpoints |
| Studio integration | ❌ | Planned: AgentScope Studio visual debug |
| JSONL Trace Exporter | ❌ | Planned: execution trace export |
| OpenTelemetry tracing | ❌ | Planned: distributed trace integration |

### CLI & OpenClaw Integration

| AgentScope Feature | Status | Demo Agent / Scenario |
|---|---|---|
| CLI-Anything wrapper (agent → CLI) | ❌ | Planned: `agentscope` CLI commands for all agents |
| HTTP API CLI adapter | ❌ | Planned: CLI calls `/chat/send` SSE endpoint |
| OpenClaw SKILL.md generation | ❌ | Planned: per-agent skill descriptors |
| OpenClaw skill packaging | ❌ | Planned: `openclaw skills install agentscope-demo` |

## 3. Guiding Principles

- **Feature = Demo**: every framework capability must have a runnable demo with a visible UI outcome.
- **YAML-first**: agents, tools, skills, RAG, and multi-agent topology are configured from `agents.yml`.
- **Observable by default**: every autonomous action should be inspectable in the debug panel.
- **Scenario-driven**: each demo tells a story (e.g., "upload a contract → extract risks → approve → generate report").
- **Keep it runnable**: production-oriented additions are optional; the repo runs locally with `mvn spring-boot:run`.

## 4. Roadmap Overview

| Phase | Theme | Outcome |
|---|---|---|
| P0 | Multi-agent foundation | ✅ Composite agents stream through `/chat/send` with observable events. |
| P1 | Multi-agent showcase | ✅ Customer service, document pipeline, routing, supervisor, and debate demos. |
| P2 | Controlled workflows | ✅ HITL approval, structured output, validation, workflow snapshots, manual approval resume validation. |
| P3 | Planning & memory | PlanNotebook demo, AutoContextMemory, long-term memory (Mem0/ReMe). |
| P4 | RAG ecosystem | Agentic RAG, Qdrant persistent store, cloud knowledge (Bailian/Dify/RAGFlow), citation UI. |
| P5 | MCP tool ecosystem | MCP transports (StdIO/SSE/HTTP), tool filtering, groups, Higress gateway. |
| P6 | Advanced multi-agent | Subagents orchestration, MsgHub standalone, Custom Workflow (StateGraph). |
| P7 | Interoperability & observability | A2A client/server, Nacos discovery, AG-UI, Studio, OTEL tracing. |
| P8 | CLI & OpenClaw integration | CLI-Anything wrapper, OpenClaw skills, agent capabilities callable from CLI/assistant. |

## 5. Phase Details

### P0: Multi-agent Foundation ✅

**Status**: Completed.

Unified runtime dispatch for all agent types (SINGLE, SEQUENTIAL, PARALLEL, ROUTING, HANDOFFS, DEBATE). Debug panel shows multi-agent timeline events. 90+ tests passing.

### P1: Multi-agent Showcase ✅

**Status**: Completed.

Polished showcase prompts for every agent. Supervisor agent, debate/review, customer-service handoffs. Categorized accordion menu UI.

### P2: Controlled Workflows ✅

**Status**: Completed.

Completed:

- [x] HITL approval: `ApprovalHook`, `ApprovalService`, `/chat/approve`, UI approval cards.
- [x] Approval resume: pending tool calls resume after human approval, tool result is streamed below the approval card, and the approved card remains expandable.
- [x] Structured output: invoice, ID card, contract metadata schemas.
- [x] Validation & repair loops: `StructuredOutputValidator`, one-pass retry.
- [x] Contract review workflow: upload → extract metadata → approval gate → generate report.
- [x] Workflow run snapshots with event capture and replay APIs.

### P3: Planning & Memory ✅

**Status**: Completed.

**Goal**: Demonstrate AgentScope's planning and memory capabilities with real scenarios.

**Recommended work**:

- **PlanNotebook demo**: Upgrade `project-planner` to use `enablePlan()`. Scenario: "Plan and execute a multi-step project" — the agent creates a structured plan, the user confirms, then the agent executes subtasks step by step with visible progress in the debug panel.
- **AutoContextMemory demo**: Create a `long-conversation` agent with `AutoContextMemory`. Scenario: "Extended technical consultation" — a 50+ turn conversation where the agent auto-compresses context, offloads large content, and reloads on demand.
- **Long-term memory — Mem0 demo**: Add `Mem0LongTermMemory` to a personal assistant agent. Scenario: "Remember my preferences" — the agent recalls user preferences (language, format, domain expertise) across sessions.
- **Long-term memory — ReMe demo**: Add `ReMeLongTermMemory` as an alternative. Scenario: "Knowledge retention across sessions" — accumulated insights are preserved.
- **Memory mode comparison**: Show `STATIC_CONTROL` vs `AGENT_CONTROL` vs `BOTH` modes in a single agent with UI toggle.

**Success criteria**:

- PlanNotebook: agent creates plans, user confirms, subtasks execute with visible status.
- AutoContextMemory: long conversation completes without context overflow.
- Long-term memory: user preferences persist across new sessions.

### P4: RAG Ecosystem

**Goal**: Demonstrate all RAG modes and knowledge backends.

**Recommended work**:

- **Agentic RAG demo**: Create `rag-agent` with `RAGMode.AGENTIC`. Scenario: "Research assistant" — the agent decides when to retrieve from the knowledge base using `retrieve_knowledge` tool, only querying when relevant.
- **Persistent RAG — Qdrant**: Replace `InMemoryStore` with `QdrantStore` for a production RAG demo. Knowledge survives app restarts.
- **Cloud RAG — Bailian**: Create `enterprise-knowledge` agent using `BailianKnowledge`. Scenario: "Enterprise document Q&A" with reranking, query rewriting, and multi-turn retrieval.
- **Cloud RAG — Dify**: Create `dify-rag` agent using `DifyKnowledge`. Scenario: "Dify-powered knowledge base" with hybrid search.
- **Cloud RAG — RAGFlow**: Create `ragflow-rag` agent using `RAGFlowKnowledge`. Scenario: "OCR-enhanced document retrieval" with knowledge graph.
- **Citation UI**: Add chunk preview, citation links, and retrieval score display to the RAG chat UI.

**Success criteria**:

- Both Generic and Agentic RAG modes are demoable.
- At least one persistent store (Qdrant) and one cloud knowledge backend work.
- Users can see which chunks were retrieved, their scores, and source documents.

### P5: MCP Tool Ecosystem

**Goal**: Connect agents to the MCP tool ecosystem.

**Recommended work**:

- **MCP StdIO demo**: Create `mcp-filesystem` agent connecting to `@modelcontextprotocol/server-filesystem` via StdIO. Scenario: "File management assistant" — the agent reads, writes, and lists files on the local filesystem through MCP tools.
- **MCP SSE demo**: Create `mcp-remote` agent connecting to a remote MCP server via SSE. Scenario: "Remote service integration" — agent calls external tools through an MCP gateway.
- **MCP HTTP demo**: Create `mcp-api` agent using streamable HTTP transport. Scenario: "Stateless API tool integration".
- **Tool filtering demo**: Show enable/disable specific MCP tools per agent. Scenario: "Restricted file agent" — only `read_file` and `list_directory` enabled, `write_file` disabled.
- **Tool Groups demo**: Create an agent with grouped MCP tools that can be activated/deactivated. Scenario: "Multi-mode agent" — filesystem tools active in one mode, web tools in another.
- **Higress AI Gateway demo**: Use `HigressMcpClientBuilder` with semantic tool search. Scenario: "Smart tool discovery" — the agent automatically finds the most relevant tools for a given query.

**Success criteria**:

- At least one MCP transport (StdIO) works with a real MCP server.
- Tool filtering and groups are configurable from `agents.yml`.
- New MCP tools can be added without writing Java integration code.

### P6: Advanced Multi-Agent Patterns

**Goal**: Demonstrate the remaining multi-agent collaboration patterns.

**Recommended work**:

- **Subagents orchestration**: Create `task-orchestrator` using Task/TaskOutput pattern. Scenario: "Research report generator" — orchestrator delegates research, analysis, and writing to specialized sub-agents, each running in isolation with its own context.
- **MsgHub standalone demo**: Create `round-table` agent using MsgHub for group conversation. Scenario: "Expert round-table discussion" — multiple experts share messages and debate in a shared context, visible as a multi-agent conversation thread.
- **Custom Workflow (StateGraph)**: Create `approval-workflow` using StateGraph. Scenario: "Document approval pipeline" — mixed deterministic (classify → validate) and agentic (analyze → review) steps with explicit state transitions.
- **Loop Pipeline**: Create `refinement-loop` using loop pipeline. Scenario: "Iterative content refinement" — content goes through writer → critic → revision loop until quality threshold is met.

**Success criteria**:

- All 10 multi-agent patterns from the AgentScope docs have at least one demo.
- Each demo has a repeatable prompt and visible multi-agent orchestration in the debug panel.

### P7: Interoperability & Observability

**Goal**: Connect to external systems and demonstrate framework observability capabilities.

**Recommended work**:

- **A2A Client demo**: Create `remote-caller` agent using `A2aAgent`. Scenario: "Call a remote agent" — the local agent discovers and calls a remote A2A service (e.g., a deployed AgentScope agent) and returns the result.
- **A2A Server demo**: Expose local agents as A2A services using `agentscope-a2a-spring-boot-starter`. Scenario: "Agent as a service" — external clients can call the demo's agents via A2A protocol.
- **Nacos discovery**: Register A2A services with Nacos. Scenario: "Service mesh of agents" — agents discover each other through Nacos registry.
- **AG-UI endpoints**: Add AG-UI-compatible streaming endpoints. Scenario: "Frontend protocol compatibility" — external frontends (CopilotKit, etc.) can drive the agents.
- **AgentScope Studio**: Integrate `StudioMessageHook` and `StudioUserAgent`. Scenario: "Visual debugging" — agent runs are visualized in AgentScope Studio's web UI alongside the local debug panel.
- **JSONL Trace**: Add `JsonlTraceExporter` as a configurable hook. Scenario: "Execution trace export" — all agent actions are logged to a JSONL file for offline analysis.
- **OpenTelemetry**: Add OTEL tracing via Tracer SPI. Scenario: "Distributed tracing" — agent calls are traced end-to-end with spans for LLM, tool, and pipeline steps.

**Success criteria**:

- Local agents can call and be called by remote agents via A2A.
- At least one external frontend can drive agents via AG-UI.
- Agent runs produce exportable traces (JSONL and/or OTEL).

### P8: CLI & OpenClaw Integration

**Goal**: Make agent capabilities callable from CLI and OpenClaw assistant via CLI-Anything generated CLIs and SKILL.md skills.

**Recommended work**:

- **CLI-Anything wrapper**: Use CLI-Anything methodology to generate CLI harnesses for the project's core agent capabilities. Each CLI command maps to an agent or workflow:
  - `agentscope chat --agent chat-basic --message "你好"` — single agent conversation
  - `agentscope extract --agent invoice-extractor --file invoice.jpg` — structured extraction
  - `agentscope pipeline --agent doc-analysis-pipeline --file report.pdf` — pipeline execution
  - `agentscope search --agent search-assistant --query "今日新闻"` — web search
  - `agentscope rag --agent rag-chat --query "知识库内容"` — RAG Q&A
  - `agentscope approve --run-id <id> --action approve` — HITL approval
  - All commands support `--json` for machine-readable output, `--session <id>` for session continuity
- **HTTP API CLI adapter**: Create a lightweight CLI adapter that calls the existing `/chat/send` and `/chat/upload` HTTP endpoints, enabling CLI usage without modifying the Spring Boot backend:
  - The CLI reads `agents.yml` to discover available agents and their descriptions
  - Streams SSE responses to the terminal with real-time text rendering
  - Supports file upload, session management, and structured output extraction
- **OpenClaw SKILL.md generation**: Generate SKILL.md files for each agent capability following the CLI-Anything Phase 6 standard. These skills describe:
  - Agent ID, description, input parameters, output format
  - Example commands and expected behavior
  - File types supported, approval requirements, streaming behavior
- **OpenClaw skill packaging**: Bundle all SKILL.md files into an OpenClaw-installable skill package:
  - `openclaw skills install agentscope-demo`
  - After installation, OpenClaw assistant can directly invoke any agent: `@agentscope 用 invoice-extractor 提取这张发票信息`
- **Skill-based agent dispatch**: The OpenClaw skill reads the CLI output and returns structured results back to the assistant's conversation context, enabling multi-turn CLI-driven agent workflows.

**Success criteria**:

- `agentscope chat --agent chat-basic --message "你好"` returns a streaming response in the terminal.
- `agentscope extract --agent invoice-extractor --file invoice.jpg --json` outputs structured invoice JSON.
- `openclaw skills install agentscope-demo` succeeds and all agent skills are discoverable.
- OpenClaw assistant can invoke agents and receive structured results via the installed skills.

## 6. Feature Backlog

### High Priority (P3–P4)

- PlanNotebook demo with structured planning.
- AutoContextMemory for long conversations.
- Long-term memory (Mem0 or ReMe).
- Agentic RAG mode.
- Persistent RAG store (Qdrant).
- Cloud knowledge backend (Bailian or Dify).

### Medium Priority (P5–P6)

- MCP StdIO transport demo.
- MCP tool filtering and groups.
- Subagents orchestration (Task/TaskOutput).
- Custom Workflow (StateGraph).
- Loop pipeline.

### Later (P7–P8)

- A2A client/server demo.
- Nacos service discovery.
- AG-UI protocol endpoints.
- AgentScope Studio integration.
- JSONL Trace and OpenTelemetry.
- CLI-Anything wrapper and OpenClaw skill packaging.

## 7. Suggested Next Milestone

**Milestone name**: `feature-complete-core`

**Scope** (P3 completed + P4 core):

- [x] PlanNotebook demo: upgrade `project-planner` to use `enablePlan()`.
- [x] AutoContextMemory demo: create `long-conversation` agent.
- [x] Long-term memory demo: BailianLongTermMemory in `personal-assistant`.
- [ ] Agentic RAG demo: create `rag-agent` with `RAGMode.AGENTIC`.
- [ ] Persistent RAG: add Qdrant store option.

**Why this next**:

- P3 planning & memory demos are complete (PlanNotebook, AutoContextMemory, Bailian LTM).
- Agentic RAG is the most requested feature after Generic RAG.
- Persistent RAG (Qdrant) completes the RAG story for production use.
- These features complete the "core agent capabilities" story before moving to MCP and interop.

**Suggested acceptance checks**:

- [x] `mvn test` passes with 0 failures.
- [x] `project-planner` creates multi-step plans with PlanNotebook tool calls visible in Debug Panel.
- [x] `long-conversation` agent configured with AutoContextMemory for long sessions.
- [x] `personal-assistant` has Bailian long-term memory configured.
- [ ] `rag-agent` uses `retrieve_knowledge` tool only when relevant.
- [ ] Debug panel shows plan status, memory compression events, and RAG tool calls.

## 8. AgentScope Feature → Demo Scenario Quick Reference

| # | Framework Feature | Demo Scenario | Agent ID | Phase |
|---|---|---|---|---|
| 1 | ReAct Agent | General AI chat with thinking | `chat-basic` | ✅ P0 |
| 2 | Tool Calling | Calculator, time, weather tools | `tool-test-simple` | ✅ P0 |
| 3 | Document Parsing | Upload DOCX/PDF/XLSX for analysis | `task-document-analysis` | ✅ P0 |
| 4 | Template Generation | Bank invoice Excel + Word generation | `bank-invoice` | ✅ P0 |
| 5 | RAG (Generic) | Knowledge base Q&A with auto-inject | `rag-chat` | ✅ P0 |
| 6 | Vision | Image OCR, chart analysis, scene understanding | `vision-analyzer` | ✅ P0 |
| 7 | Audio | Speech-to-text voice interaction | `voice-assistant` | ✅ P0 |
| 8 | Web Search | News, weather, stock queries | `search-assistant` | ✅ P0 |
| 9 | Session Persistence | Multi-turn conversation with save/restore | All agents | ✅ P0 |
| 10 | Streaming SSE | Real-time incremental response display | All agents | ✅ P0 |
| 11 | Hook System | Timeline, metrics, tool detail in debug panel | All agents | ✅ P0 |
| 12 | Pipeline Sequential | Document analysis → supplementary search | `doc-analysis-pipeline` | ✅ P1 |
| 13 | Routing | Smart dispatch to doc/search/vision/sales experts | `smart-router` | ✅ P1 |
| 14 | Handoffs | Customer service → sales/complaint switching | `customer-service` | ✅ P1 |
| 15 | Supervisor | Super-supervisor coordinating experts | `super-supervisor` | ✅ P1 |
| 16 | Debate | Multi-expert debate with judge synthesis | `debate-review` | ✅ P1 |
| 17 | Structured Output | Invoice/ID card/contract extraction to JSON | `invoice-extractor` etc. | ✅ P2 |
| 18 | Structured Validation | Auto-repair invalid extraction output | All extractors | ✅ P2 |
| 19 | Human-in-the-Loop | Approval gate before invoice generation | `bank-invoice`, `contract-review-workflow` | ✅ P2 |
| 20 | Workflow Snapshots | Capture and replay workflow runs | `WorkflowRunService` | ✅ P2 |
| 21 | PlanNotebook | Multi-step project planning with user confirm | `project-planner` | P3 |
| 22 | AutoContextMemory | Long conversation with auto-compression | `long-conversation` | P3 |
| 23 | Long-term Memory (Mem0) | Cross-session user preference recall | `personal-assistant` | P3 |
| 24 | Long-term Memory (ReMe) | Cross-session knowledge retention | `knowledge-assistant` | P3 |
| 25 | RAG (Agentic) | On-demand knowledge retrieval via tool | `rag-agent` | P4 |
| 26 | RAG (Qdrant) | Persistent vector store across restarts | `rag-chat` upgrade | P4 |
| 27 | RAG (Bailian) | Enterprise cloud knowledge base | `enterprise-knowledge` | P4 |
| 28 | RAG (Dify) | Dify-managed knowledge base | `dify-rag` | P4 |
| 29 | RAG (RAGFlow) | OCR-enhanced document retrieval | `ragflow-rag` | P4 |
| 30 | MCP (StdIO) | Local filesystem MCP server connection | `mcp-filesystem` | P5 |
| 31 | MCP (SSE) | Remote MCP server streaming connection | `mcp-remote` | P5 |
| 32 | MCP (HTTP) | Stateless MCP API connection | `mcp-api` | P5 |
| 33 | MCP Tool Filtering | Selective tool enable/disable per agent | Config in `agents.yml` | P5 |
| 34 | MCP Tool Groups | Grouped tool activation modes | `mcp-multi-mode` | P5 |
| 35 | Higress Gateway | Semantic tool search via AI gateway | `higress-agent` | P5 |
| 36 | Subagents | Orchestrator delegating to isolated sub-agents | `task-orchestrator` | P6 |
| 37 | MsgHub Standalone | Group conversation round-table | `round-table` | P6 |
| 38 | Custom Workflow | StateGraph mixed deterministic + agentic | `approval-workflow` | P6 |
| 39 | Loop Pipeline | Iterative refinement until quality threshold | `refinement-loop` | P6 |
| 40 | A2A Client | Call remote Agent2Agent services | `remote-caller` | P7 |
| 41 | A2A Server | Expose local agents as A2A services | A2A starter config | P7 |
| 42 | A2A Nacos | Nacos-based agent service discovery | `nacos-discovery` | P7 |
| 43 | AG-UI | Frontend protocol compatibility endpoints | AG-UI controller | P7 |
| 44 | Studio | AgentScope Studio visual debugging | StudioMessageHook | P7 |
| 45 | JSONL Trace | Execution trace file export | JsonlTraceExporter | P7 |
| 46 | OpenTelemetry | Distributed tracing integration | OTEL Tracer SPI | P7 |
| 47 | CLI-Anything Wrapper | Agent capabilities as CLI commands | `agentscope` CLI binary | P8 |
| 48 | HTTP API CLI Adapter | CLI → `/chat/send` SSE streaming | CLI adapter module | P8 |
| 49 | OpenClaw SKILL.md | Per-agent skill descriptors for OpenClaw | `~/.openclaw/skills/` | P8 |
| 50 | OpenClaw Skill Package | Installable skill: `openclaw skills install agentscope-demo` | Skill package | P8 |

## 9. References

- AgentScope Java intro: https://java.agentscope.io/zh/intro.html
- AgentScope Java multi-agent overview: https://java.agentscope.io/zh/multi-agent/overview.html
- AgentScope Java MCP integration: https://java.agentscope.io/zh/task/mcp.html
- AgentScope Java A2A protocol: https://java.agentscope.io/zh/task/a2a.html
- AgentScope Java structured output: https://java.agentscope.io/zh/task/structured-output.html
- AgentScope Java RAG: https://java.agentscope.io/zh/task/rag.html
- AgentScope Java memory: https://java.agentscope.io/zh/task/memory.html
- AgentScope Java hook system: https://java.agentscope.io/zh/task/hook.html
- AgentScope Java state management: https://java.agentscope.io/zh/task/state.html
- AgentScope Java plan: https://java.agentscope.io/zh/task/plan.html
- AgentScope Java studio: https://java.agentscope.io/zh/task/studio.html
- AgentScope GitHub: https://github.com/agentscope-ai/agentscope-java
- CLI-Anything: https://github.com/HKUDS/CLI-Anything
- OpenClaw: https://github.com/open-claw/openclaw
- OpenCLI: https://github.com/jackwener/opencli
