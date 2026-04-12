# Debug Panel Observability Redesign

**Date:** 2026-04-12
**Status:** Approved

## Problem

The right-side System Log (debug panel) currently dumps raw thinking content as scrolling text, which is redundant with the thinking box already shown in the chat area. The panel provides no actionable runtime metrics — no token consumption, no execution timing, no tool call statistics — making it useless for debugging and optimization.

## Goal

Transform the debug panel into a structured observability view that shows agent runtime metrics: token usage per LLM call, tool call timing, session-level summaries, and a compact event timeline. Remove all thinking content duplication.

## Design

### 1. Data Collection (Backend)

Leverage existing AgentScope framework data in `AgentService.handleEvent()`:

**New SSE event types** (pushed through the existing SSE connection, no new endpoints):

| Event | Trigger | Payload |
|---|---|---|
| `usage` | After each LLM response, when `Msg.getChatUsage()` is non-null | `{inputTokens, outputTokens, totalTokens, time}` |
| `tool_call` | Tool invocation starts (existing, enhanced) | `{name, params, timestamp}` |
| `tool_result` | Tool execution completes (existing, enhanced) | `{name, result_preview, duration_ms, timestamp}` |
| `done` | Session ends (existing, enhanced) | `{totalTokens, totalTime, totalToolCalls, iterations}` |

**Removed:** `thinking` events will no longer be sent to the debug panel stream. Thinking content remains in the chat-area thinking box only.

**Data sources from AgentScope framework:**
- `Msg.getChatUsage()` → `ChatUsage` with `inputTokens`, `outputTokens`, `totalTokens`, `time` (seconds)
- `ToolUseBlock` → tool name and input params (already captured)
- `ToolResultBlock` → tool output (already captured)
- Timestamp tracking in `AgentService` for tool call duration calculation

**Implementation approach:**
- In `handleEvent()`, after processing each event block, check if the `Msg` has `ChatUsage` data and emit a `usage` event
- Track tool call start times in a `ConcurrentHashMap<String, Long>` keyed by tool call ID (from `ToolUseBlock.getId()`) to calculate duration when the corresponding `ToolResultBlock` arrives
- In the `done` handler, compute and emit session totals

### 2. Debug Panel Layout (Frontend)

Three zones, top to bottom:

**Zone A — Session Summary Card (fixed, non-scrolling):**

```
┌──────────────────────────────┐
│ TOKENS    1,234 → 567        │   input tokens → output tokens
│ TIME      3.2s               │   cumulative LLM time
│ TOOLS     2 calls            │   total tool invocations
│ STEPS     3/10               │   current iteration / max
└──────────────────────────────┘
```

- Real-time updates as events arrive
- Token numbers color-coded: input in `--neon-blue`, output in `--neon-cyan`
- Time and tool count in `--text-primary`
- Compact single-line-per-metric layout, fits in ~60px height

**Zone B — Structured Event Timeline (scrollable):**

Compact one-line entries, no raw text dumps:

```
🔄 LLM call #1          832 tok   1.2s
🔧 parse_docx           0.8s     ✓
🔧 analyze_content      0.3s     ✓
🔄 LLM call #2          412 tok   0.9s
⚠ ERROR: file not found           ✗
```

- **LLM call row**: `🔄` icon + call sequence number + token count + duration
- **Tool row**: `🔧` icon + tool name + duration + status (✓ success / ✗ failure)
- **Error row**: `⚠` icon + error summary (truncated), red highlight
- Each row has: left-aligned label, right-aligned metrics, monospace font
- No thinking content, no full tool result text

**Zone C — Clear Log button (fixed at bottom):**

Unchanged from current design.

### 3. Data Flow

```
AgentScope Event stream
  ↓
AgentService.handleEvent()
  ├─ Extract ChatUsage from Msg → emit "usage" SSE event
  ├─ Track tool start timestamps → compute duration → emit enhanced "tool_result"
  ├─ Skip "thinking" events to debug panel (only chat-area thinking box)
  └─ On done → emit session summary
  ↓
Frontend JS (existing EventSource handler)
  ├─ "usage" → update sessionSummary counters + append LLM call entry to timeline
  ├─ "tool_call" → append tool start entry to timeline, record startTime
  ├─ "tool_result" → update tool entry with duration and status
  ├─ "done" → finalize session summary
  └─ "error" → append error entry to timeline
  ↓
Debug Panel renders from state
```

### 4. Frontend State Model

```javascript
let sessionSummary = {
    inputTokens: 0,
    outputTokens: 0,
    totalTime: 0,
    toolCallCount: 0,
    stepCount: 0,
    maxSteps: 10
};
```

- Reset on agent switch or new conversation
- Updated incrementally as SSE events arrive
- Drives both the summary card and the event timeline

### 5. Files Changed

| File | Change |
|---|---|
| `AgentService.java` | Extract `ChatUsage`, compute tool durations, emit `usage`/enhanced events, suppress `thinking` to debug |
| `chat.html` — CSS | New `.debug-summary` card styles, `.debug-event` row styles, remove old `.debug-entry` text-dump styles |
| `chat.html` — JS | New `sessionSummary` state, `updateSummaryCard()`, `addTimelineEntry()`, handle new SSE events, remove `addDebugEntry()` for thinking |

### 6. What We're NOT Doing

- No cost/pricing calculation (framework doesn't provide pricing data)
- No persistent metrics storage or historical comparison
- No chart visualizations (numbers + timeline are sufficient for debugging)
- No new REST API endpoints (all data through existing SSE)
- No changes to the chat area or thinking box behavior
