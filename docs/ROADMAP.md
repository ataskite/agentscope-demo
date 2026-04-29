# AgentScope Demo Roadmap

**Date**: 2026-04-29
**Scope**: AgentScope Java demo evolution roadmap
**Status**: Milestone `multi-agent-runtime-showcase` completed

## Progress Snapshot

Last updated: 2026-04-29

Completed:

- [x] Created this roadmap document and captured the long-term AgentScope Demo direction.
- [x] Implemented the first P0 foundation task: unified streaming runtime dispatch for single and composite agents.
- [x] Added `StreamingAgentRuntime` so `AgentRuntime` and `PipelineAgentRuntime` share one service-facing contract.
- [x] Updated `AgentRuntimeFactory` and `AgentService` so `SINGLE`, `SEQUENTIAL`, `PARALLEL`, `ROUTING`, and `HANDOFFS` all flow through the normal chat runtime path.
- [x] Added runtime dispatch tests for single, sequential, parallel, routing, handoffs, and session-memory composite dispatch.
- [x] Expanded unit test coverage across model DTOs, multimodal message creation, file upload/download handling, `AgentService` message assembly, document parser tools, basic tools, web search guardrails, and bank invoice generation.
- [x] Added frontend debug panel rendering improvements for multi-agent events (pipeline, routing, handoff).
- [x] Added polished showcase prompts for every configured agent.
- [x] Added JaCoCo coverage reporting configuration.

Verification:

- [x] `mvn -Dtest=AgentRuntimeFactoryTest test` passed with 6 tests.
- [x] `mvn test` passed with 90 tests, 0 failures, 0 errors.
- [x] JaCoCo coverage report generated at `target/site/jacoco/index.html`.

Current TODO:

- [ ] Validate `doc-analysis-pipeline`, `smart-router`, and `customer-service` manually in the browser with real model credentials.
- [ ] Add P1 showcase demos (supervisor agent, debate/review, etc.).
- [ ] Add P2 controlled workflows (human-in-the-loop, structured outputs).

## 1. Positioning

This project is already more than a basic AgentScope Java demo. It has single-agent chat, tool calling, document parsing, template document generation, RAG, multimodal input, session persistence, and an observability/debug panel.

The next stage should turn it into a clear AgentScope Java showcase:

- Demonstrate the framework's core agent patterns.
- Provide realistic business scenarios instead of isolated toy agents.
- Make agent execution observable, controllable, and reproducible.
- Keep the codebase approachable for developers who want to learn AgentScope Java.

## 2. Current Baseline

Implemented or partially implemented capabilities:

- Single ReAct agents configured from `src/main/resources/config/agents.yml`.
- Tool calling with custom Java POJO tools and AgentScope system tools.
- Skill-backed document workflows for DOCX, PDF, XLSX, and bank invoice generation.
- RAG knowledge base with `SimpleKnowledge`, DashScope embeddings, and in-memory store.
- Multimodal agents for image and audio inputs.
- Session persistence through AgentScope `SessionManager`.
- SSE streaming chat UI with a runtime debug panel.
- Initial multi-agent configuration for sequential pipeline, routing, and handoffs.
- `CompositeAgentFactory` already contains creation logic for sequential, parallel, routing, and handoff-style agents.

Known gap:

- The generic chat path still creates runtimes through the single-agent path. Composite agents are configured, but the default runtime entry currently does not fully dispatch `SEQUENTIAL`, `PARALLEL`, `ROUTING`, or `HANDOFFS` agents end-to-end.

## 3. Guiding Principles

- Prefer scenario-first features: each new capability should have a demo conversation and a visible UI outcome.
- Keep YAML configuration as the primary extension point for agents, tools, skills, memory, RAG, and multi-agent topology.
- Make every autonomous action inspectable in the debug panel.
- Add guardrails before adding powerful tools.
- Keep production-oriented additions optional so the repository remains easy to run locally.

## 4. Roadmap Overview

| Phase | Theme | Outcome |
| --- | --- | --- |
| P0 | Multi-agent foundation | Existing composite agent configs run through `/chat/send` and stream observable events. |
| P1 | Multi-agent showcase | A complete customer-service and document-analysis showcase demonstrates routing, pipelines, handoffs, and supervisor behavior. |
| P2 | Controlled workflows | Human approval, structured outputs, and custom workflows make agent behavior safer and more deterministic. |
| P3 | Tool and knowledge ecosystem | MCP connectors, persistent RAG, and tool governance turn the demo into an extensible agent platform. |
| P4 | Business workflow expansion | Multimodal document, invoice, ID card, and banking workflows become reusable end-to-end demos. |
| P5 | Productization and interop | AG-UI, A2A, evaluation, replay, auth, and deployment support make the project closer to a production reference app. |

## 5. Phase Details

### P0: Multi-agent Foundation

Goal: make the current multi-agent architecture work reliably before adding more patterns.

Recommended work:

- Introduce a shared runtime abstraction for both `AgentRuntime` and `PipelineAgentRuntime`.
- Update `AgentService` to dispatch by `AgentType`.
- Support stateless and session-aware execution for `SINGLE`, `SEQUENTIAL`, `PARALLEL`, `ROUTING`, and `HANDOFFS`.
- Ensure pipeline runtimes emit `pipeline_start`, `pipeline_step_start`, `pipeline_step_end`, and `pipeline_end`.
- Ensure routing and handoff runtimes emit useful `routing_decision` and `handoff_start` events.
- Add backend tests for each composite type.
- Add one frontend smoke path for each composite type.

Success criteria:

- Selecting `doc-analysis-pipeline`, `smart-router`, or `customer-service` from the UI no longer fails at runtime.
- Debug panel shows which agent or sub-agent handled each step.
- Existing single-agent behavior remains unchanged.

### P1: Multi-agent Showcase

Goal: make multi-agent collaboration obvious and compelling to a first-time user.

Recommended work:

- Build a "Smart Customer Service" demo using routing plus handoffs.
- Build a "Document Research Pipeline" demo: parse document, extract facts, search supplements, generate report.
- Add a supervisor-style agent that can call document, search, vision, and invoice experts as sub-agent tools.
- Add a debate/review demo where multiple expert agents critique a proposal and a judge agent summarizes the decision.
- Add predefined sample prompts in the UI for each showcase.

Success criteria:

- A user can understand the difference between pipeline, routing, handoffs, supervisor, and debate from the UI alone.
- Each demo has a repeatable prompt and expected behavior.

### P2: Controlled Workflows

Goal: make agent behavior safer, more deterministic, and easier to integrate.

Recommended work:

- Add Human-in-the-Loop approval before sensitive actions such as shell execution, file writing, invoice generation, or external API calls.
- Add structured output schemas for invoice extraction, ID card extraction, contract metadata, and bank invoice parameters.
- Add validation and repair loops for structured outputs.
- Add a custom workflow demo such as `Contract Review Workflow`: upload contract, extract clauses, identify risks, request approval, generate report.
- Store workflow state and intermediate outputs for replay.

Success criteria:

- Sensitive tool calls pause for approval before execution.
- Extraction agents return machine-readable JSON that passes schema validation.
- Workflow runs can be inspected after completion.

### P3: Tool and Knowledge Ecosystem

Goal: evolve from hard-coded demo tools to an extensible tool and knowledge platform.

Recommended work:

- Add MCP server registration and tool discovery.
- Support MCP transport options where practical: StdIO, HTTP, and SSE.
- Add tool allowlist/denylist, tool grouping, and per-agent tool permissions.
- Replace or supplement in-memory RAG with a persistent vector store option.
- Add document deletion, re-indexing, chunk preview, citation display, and retrieval debugging.
- Add source-grounded answer mode for RAG Chat.

Success criteria:

- New tools can be attached without writing Java code for every integration.
- RAG knowledge survives app restarts.
- Users can see which chunks were retrieved and cited.

### P4: Business Workflow Expansion

Goal: turn individual agents into reusable business workflows.

Recommended work:

- Create an invoice workflow: image upload, OCR, structured extraction, validation, export.
- Create an ID verification workflow: ID card extraction, masking, validation, and audit log.
- Create a bank invoice workflow: collect missing fields, validate parameters, generate Excel and Word, expose download links.
- Create a document comparison workflow for contracts or policy documents.
- Add batch upload and batch extraction for document-heavy demos.

Success criteria:

- Each business workflow has a clear start, intermediate state, final artifact, and downloadable output.
- Workflows can be demoed with sample files from the repository or generated fixtures.

### P5: Productization and Interop

Goal: make the demo useful as a reference architecture for real AgentScope Java applications.

Recommended work:

- Add AG-UI-compatible agent endpoints for frontend interoperability.
- Add A2A server/client experiments so local agents can call remote agents and expose themselves as remote agents.
- Add evaluation datasets for routing, extraction, RAG, and tool-use behavior.
- Add conversation replay and run comparison.
- Add auth, tenant isolation, file retention policy, and audit logging.
- Add Docker Compose and deployment docs.
- Add OpenTelemetry-friendly tracing or structured run logs.

Success criteria:

- The app can be used as a local teaching demo and a reference implementation.
- Agent runs are replayable, testable, and auditable.
- External clients can integrate without depending on the current vanilla JS chat UI.

## 6. Feature Backlog

High priority:

- Unified runtime abstraction for single and composite agents.
- Multi-agent UI visualization.
- Supervisor agent showcase.
- Human approval checkpoints.
- Structured output schemas for extraction agents.
- Persistent RAG store option.

Medium priority:

- MCP connector management.
- Tool permission model.
- Workflow state persistence.
- Debate/reviewer agent demo.
- Batch document workflows.
- Retrieval citation UI.

Later:

- AG-UI compatibility.
- A2A interoperability.
- Evaluation dashboard.
- Multi-tenant auth.
- Deployment hardening.
- Plugin or marketplace-style tool catalog.

## 7. Suggested First Milestone

Milestone name: `multi-agent-runtime-showcase`

Status: ✅ Completed

Scope:

- [x] Fix runtime dispatch for composite agents.
- [x] Add tests for sequential, parallel, routing, and handoffs.
- [x] Improve debug panel rendering for multi-agent events.
- [x] Add polished showcase prompts for every configured agent.

Why this first:

- It unlocks features already present in configuration and factory code.
- It creates the strongest visible improvement with the least conceptual churn.
- It gives future phases a stable execution model.

Suggested acceptance checks:

- [x] `mvn test` passes (90 tests, 0 failures, 0 errors).
- [x] Frontend shows multi-agent timeline events (pipeline, routing, handoff).
- [x] Showcase prompts added for every configured agent.
- [x] JaCoCo coverage reporting configured.
- [ ] Manual browser testing with real model credentials (requires API key).

## 8. References

- AgentScope Java multi-agent overview: https://java.agentscope.io/zh/multi-agent/overview.html
- AgentScope Java MCP integration: https://java.agentscope.io/zh/task/mcp.html
- AgentScope Java Human-in-the-Loop: https://java.agentscope.io/zh/task/human-in-the-loop.html
- AgentScope Java structured output: https://java.agentscope.io/zh/task/structured-output.html
- AgentScope Java AG-UI integration: https://java.agentscope.io/zh/task/ag-ui.html
- AgentScope Java A2A protocol: https://java.agentscope.io/zh/task/a2a.html
