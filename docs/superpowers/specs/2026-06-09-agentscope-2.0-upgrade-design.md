# AgentScope 2.0 Upgrade Design

**Date:** 2026-06-09
**Scope:** Upgrade from 1.1.0-RC2 to 2.0.0-RC1, fix compatibility, keep project running
**Post-upgrade:** Introduce 2.0 new features in separate planning cycle

## Background

AgentScope 2.0.0-RC1 introduces breaking API changes. The upgrade is a prerequisite before any new feature work. Strategy: **hybrid approach** — migrate what's required for compilation, keep deprecated-but-working APIs, temporarily disable pipeline-dependent multi-agent patterns.

## Breaking Changes (Part A — Must Fix)

### A.1 `ReActAgent.Builder.memory()` removed

- **Replacement:** `.session(Session).sessionKey(SessionKey)`
- **Impact:** AgentFactory.buildAgent(), CompositeAgentFactory (all agent creation methods)
- **Migration:** Use `InMemorySession` + `SessionKey.of(agentId)` as drop-in; Memory classes still exist (@Deprecated) and bridge through `saveTo/loadFrom`

### A.2 `io.agentscope.core.pipeline.*` removed

- **Removed:** `Pipeline`, `SequentialPipeline`, `FanoutPipeline`, `MsgHub`
- **Impact:** All 7 pipeline-dependent patterns cannot compile
- **Migration:** Disable patterns, remove pipeline-related code from runtime/factory layer. Configuration (agents.yml) kept for future reimplementation.

### A.3 `io.agentscope.core.session.SessionManager` removed

- **Impact:** SessionManagerService import
- **Migration:** Remove import; SessionContext drops SessionManager field. ConcurrentHashMap management stays as-is.

### A.4 `Msg` content validation stricter

- USER role: only TextBlock / DataBlock / ImageBlock / AudioBlock / VideoBlock
- SYSTEM role: only TextBlock
- **Migration:** Audit all Msg.builder().role(MsgRole.USER) calls. Our usage only sends TextBlock/ImageBlock/AudioBlock — no changes needed unless ToolUseBlock appears in USER messages.

### A.5 `io.agentscope.core.state.*` restructure

- `AgentMetaState` → `AgentState`, `StateModule`/`StatePersistence` → removed
- **Impact:** No direct imports in project — no action needed.

## Deprecated-but-Working APIs (Part B — Keep As-Is)

| API | Status | Reason to Keep |
|-----|--------|---------------|
| `io.agentscope.core.hook.*` | @Deprecated(forRemoval) | LegacyHookDispatcher preserves compatibility |
| `SkillBox` | @Deprecated(forRemoval) | Still callable; migration to skillRepository deferred |
| `Memory` / `InMemoryMemory` / `AutoContextMemory` | @Deprecated(forRemoval) | Still callable; bridge through Session |
| `RAGMode` / `Knowledge` / `RetrieveConfig` | @Deprecated(forRemoval) | v2 rewrite in progress; current API still works |
| `LongTermMemory` / `BailianLongTermMemory` | @Deprecated(forRemoval) | v2 rewrite in progress; current API still works |
| `agent.stream()` | @Deprecated(forRemoval) | Still callable; streamEvents() migration deferred |
| `ClasspathSkillRepository` | Available | Still works |

## Multi-Agent Pattern Status

### Preserved (no Pipeline dependency)

- **SINGLE** — ReActAgent directly
- **ROUTING** — ReActAgent + SubAgentTool
- **HANDOFFS** — ReActAgent + trigger rules
- **STATE_GRAPH** — OrderFulfillmentGraph (custom state machine, no Pipeline)

### Disabled (Pipeline-dependent)

- **SEQUENTIAL** — SequentialPipeline
- **PARALLEL** — FanoutPipeline
- **DEBATE** — Pipeline<Msg>
- **LOOP** — LoopPipeline extends Pipeline
- **MSG_HUB** — RoundTablePipeline extends Pipeline
- **SUBAGENT_SEQ** — TaskOrchestratorPipeline extends Pipeline
- **SUBAGENT_PAR** — TaskDispatcherPipeline extends Pipeline

## Upgrade Steps

### Step 1: pom.xml version bump
- `agentscope.version`: `1.1.0-RC2` → `2.0.0-RC1`

### Step 2: AgentFactory — memory() → session()
- Replace `.memory(memory)` with `.session(new InMemorySession()).sessionKey(SessionKey.of(agentId))`
- Keep `createMemory()` method for session context compatibility

### Step 3: SessionManagerService — drop SessionManager
- Remove `io.agentscope.core.session.SessionManager` import
- Remove `sessionManager` field from `SessionContext` inner class
- Constructor no longer accepts SessionManager

### Step 4: Disable pipeline-dependent patterns
- `AgentRuntimeFactory`: remove 7 pipeline factory methods (sequential, parallel, debate, loop, msgHub, subagentSeq, subagentPar + their *WithMemory variants)
- `AgentType` enum: mark disabled types or comment out
- `CompositeAgentFactory`: remove pipeline-dependent methods
- Delete or @Deprecated pipeline classes: `PipelineAgentRuntime`, `DebatePipeline`, `LoopPipeline`, `RoundTablePipeline`, `TaskOrchestratorPipeline`, `TaskDispatcherPipeline`
- `agents.yml`: keep disabled agent configs (commented or with `enabled: false` if supported), else leave for future

### Step 5: Msg validation audit
- Scan all `Msg.builder().role(MsgRole.USER)` — verify only compliant ContentBlock types
- MultiModalMessage uses ImageBlock/AudioBlock — safe
- AgentService uses TextBlock — safe
- Pipeline classes (being disabled) — no action needed

### Step 6: Frontend adjustments
- Filter out disabled agent types from agent list display
- `agents.js`: skip agents with disabled types

### Step 7: Test fixes
- Disable pipeline-dependent tests with `@Disabled`
- Update AgentFactory/SessionManager tests for new session API
- Verify compilation passes
- Run `mvn test` — all enabled tests should pass

## Acceptance Criteria

1. Project compiles with `mvn compile` — zero errors
2. `mvn test` passes for all non-disabled tests
3. SINGLE, ROUTING, HANDOFFS, STATE_GRAPH agents work in UI
4. Chat, file upload, RAG, session management functional
5. Disabled patterns hidden from UI, no runtime errors if accessed

## Out of Scope (Future Work)

- Hook → Middleware migration
- SkillBox → skillRepository migration
- Memory → Session/AgentState full migration
- stream() → streamEvents() migration
- RAG module v2 adoption
- Reimplementation of 7 pipeline patterns using 2.0 subagent/Middleware
- HarnessAgent full integration
- Permission system
- Workspace abstraction
- Model fault tolerance (FallbackModel)
- ContentBlock / AgentEvent new event system
