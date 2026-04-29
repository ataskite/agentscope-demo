# Agent Categorized Accordion Menu Design

## Summary

Redesign the sidebar agent list from a flat card list into a categorized two-level accordion menu with three groups: Single Agent, Expert Agent, and Multi-Agent Collaboration. Each group is collapsible with a default state of only the first group expanded.

## Background

The demo currently has 19 agents displayed as a flat list in the sidebar. These agents naturally fall into three categories based on AgentScope's multi-agent architecture:

- **Single Agent**: Standalone ReActAgents that users interact with directly (e.g., chat-basic, search-assistant)
- **Expert Agent**: Agents specifically designed as sub-agents for multi-agent patterns, with focused system prompts and tool sets (e.g., doc-expert, search-expert)
- **Multi-Agent Collaboration**: Orchestration-layer agents that coordinate expert agents via Pipeline, Routing, or Handoffs patterns (e.g., smart-router, customer-service)

Expert agents cannot be improvised from single agents; they require purpose-built system prompts and tool sets matching their role in the orchestration pattern.

## Design Decisions

- **Category source**: New `category` field in `agents.yml` (backend-driven, extensible)
- **Accordion style**: Non-exclusive collapsible groups (multiple groups can be open simultaneously)
- **Default state**: Only the first group (Single Agent) expanded on page load
- **Active agent visibility**: If the selected agent belongs to a collapsed group, that group auto-expands
- **Category key naming**: `collaboration` instead of `multi` to better convey coordination semantics

## Data Model

### YAML Configuration

Add `category` field to each agent in `agents.yml`:

```yaml
agents:
  - agentId: chat-basic
    category: single        # single / expert / collaboration
    name: Basic Chat
    ...

  - agentId: doc-expert
    category: expert
    name: 文档专家
    ...

  - agentId: smart-router
    category: collaboration
    type: ROUTING
    name: 智能路由器
    ...
```

Values: `single` (default), `expert`, `collaboration`.

### Backend

`AgentConfig.java` adds `private String category` field. The `/api/agents` endpoint automatically includes it in JSON responses. No other backend changes needed.

### Frontend Category Definitions

Group metadata hardcoded in frontend (only 3 groups, rarely changes):

```js
const CATEGORIES = [
  { key: 'single',        label: '单Agent',       icon: '⚡', color: 'cyan'    },
  { key: 'expert',        label: '专家Agent',      icon: '🎯', color: 'green'   },
  { key: 'collaboration', label: '多智能体协作',   icon: '🔗', color: 'magenta' },
];
```

## UI Design

### ASCII Wireframe

```
┌──────────────────────────────┐
│  AGENTS                      │  ← sidebar-header (unchanged)
├──────────────────────────────┤
│                              │
│  ▼ ⚡ 单Agent            11  │  ← group header (expanded)
│  ┌────────────────────────┐  │
│  │ [CH] Basic Chat        │  │  ← agent-card (unchanged)
│  │ [TO] Tool Calling      │  │
│  │ ...                    │  │
│  └────────────────────────┘  │
│                              │
│  ▶ 🎯 专家Agent          7  │  ← group header (collapsed)
│                              │
│  ▶ 🔗 多智能体协作       3  │  ← group header (collapsed)
│                              │
└──────────────────────────────┘
```

### Group Header

- Clickable title bar toggles expand/collapse
- Left: expand arrow (`▼`/`▶`) with transition + category icon (category color) + label
- Right: agent count badge (neon-blue)
- Hover: `bg-card-hover` background
- No active/selected state on the header itself

### Agent Cards

Completely unchanged. Existing `.agent-card` styles and selection logic are reused as-is.

### Animation

Collapsed state uses `max-height: 0; overflow: hidden` with CSS transition for smooth expand/collapse. Arrow rotates via `transform: rotate()` transition.

## Technical Changes

### Files Modified (4 total)

| File | Change |
|------|--------|
| `AgentConfig.java` | Add `category` field (1 line) |
| `agents.yml` | Add `category` to each of 19 agents (19 lines) |
| `agents.js` | Rewrite `loadAgents()` to group by category and render accordion |
| `sidebar.css` | Add ~40 lines of group header/body styles |

### agents.js loadAgents() Logic

1. Fetch agent list from API
2. Group agents by `category` field
3. Iterate CATEGORIES in order (single → expert → collaboration)
4. For each group: render `.agent-group-header` + `.agent-group-body` containing agent-cards
5. Set first group as expanded, others as collapsed
6. Attach click handlers on headers to toggle `.collapsed` class
7. Preserve existing agent selection, config modal, and sample prompt logic

### No Changes Required

- `selectAgent()` — operates on `.agent-card` class, unaffected
- `state.js` — no state changes needed
- `api.js` — no API changes needed
- Other modules — session, knowledge, upload, debug, ui, utils all untouched
- chat.css, header.css, debug.css, upload.css, modal.css — untouched

## Agent Category Assignment

| Category | Agents |
|----------|--------|
| single (11) | chat-basic, tool-test-simple, task-document-analysis, bank-invoice, rag-chat, vision-analyzer, voice-assistant, invoice-extractor, idcard-extractor, search-assistant, project-planner |
| expert (7) | doc-expert, search-expert, vision-expert, sales-expert, support-agent, sales-agent, complaint-agent |
| collaboration (3) | doc-analysis-pipeline, smart-router, customer-service |
