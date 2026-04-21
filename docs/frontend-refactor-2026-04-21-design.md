# Frontend Module Refactoring Design

**Date:** 2026-04-21
**Status:** Approved
**Author:** Claude Code

## Overview

Refactor the monolithic `chat.js` (1794 lines) and `chat.css` (2452 lines) into a modular structure for improved maintainability while maintaining zero-build architecture.

## Goals

- **Primary:** Maintainability — clear module responsibilities for easier development
- **Constraint:** Zero-build — use native ES6 modules, no bundler required
- **Compatibility:** Keep single-entry point — HTML file unchanged
- **Risk:** Minimal changes to global state management

## Directory Structure

```
static/
├── scripts/
│   ├── chat.js              # Main entry point (preserved)
│   ├── state.js             # Core: Global state management
│   ├── api.js               # Core: API communication (SSE, upload)
│   └── modules/
│       ├── ui.js            # UI operations (DOM, rendering)
│       ├── agents.js        # Agent loading and selection
│       ├── debug.js         # Debug panel
│       ├── session.js       # Session management
│       ├── knowledge.js     # Knowledge base management
│       └── utils.js         # Utility functions
│
└── styles/
    ├── chat.css             # Main entry point (preserved)
    ├── base.css             # Core: Base styles (variables, reset, layout)
    └── modules/
        ├── header.css       # Header styles
        ├── sidebar.css      # Sidebar styles
        ├── chat.css         # Chat area styles
        ├── debug.css        # Debug panel styles
        ├── upload.css       # File upload styles
        ├── modal.css        # Modal styles
        └── utils.css        # Utility class styles
```

## Module Specifications

### JavaScript Modules

#### Core Modules (Root Directory)

**`state.js`** — Global State Management
- All global variable definitions
- `agents` object
- `currentAgent`, `isStreaming`, `currentAbortController`
- `uploadedFile`, `uploadedImages`, `uploadedAudio`
- `agentRawMarkdown`, `currentSessionId`
- Export state for other modules to import

**`api.js`** — API Communication Layer
- `sendMessage()` — SSE communication with backend
- `uploadFile()` — File upload handler
- `createSSEParser()` — SSE event parser
- All backend interaction logic

#### Auxiliary Modules (modules/)

**`modules/ui.js`** — UI Rendering and Operations
- DOM reference retrieval
- Message rendering
- Markdown rendering (marked.js integration)
- Structured data table rendering
- Avatar/bubble styling logic

**`modules/agents.js`** — Agent Management
- `loadAgents()` — Load agent list from backend
- `selectAgent()` — Switch active agent
- Agent config viewer modal

**`modules/debug.js`** — Debug Panel
- Debug panel toggle
- Round rendering
- Timeline and metrics display
- Clear debug information

**`modules/session.js`** — Session Management
- `createNewSession()` — Create new chat session
- `loadSessions()` — Load session list
- `switchSession()` — Switch between sessions
- `clearSession()` — Clear current session

**`modules/knowledge.js`** — Knowledge Base Management
- `uploadToKnowledge()` — Upload document to knowledge base
- `loadKnowledgeDocs()` — Load knowledge documents

**`modules/utils.js`** — Utility Functions
- `escapeHtml()` — HTML escaping
- `formatValue()` — Value formatting
- Generic helper functions

### CSS Modules

#### Core Module (Root Directory)

**`base.css`** — Base Styles
- CSS variable definitions (colors, spacing, typography)
- Global reset (box-sizing, margins)
- Scanline overlay
- Grid background
- App layout framework

#### Auxiliary Modules (modules/)

**`modules/header.css`** — Header Styles
- `.app-header`
- `.header-logo`, `.header-title`
- HUD corner decorations

**`modules/sidebar.css`** — Sidebar Styles
- `.sidebar`
- Agent list cards
- Session list items
- Knowledge document items
- Sidebar headers and dividers

**`modules/chat.css`** — Chat Area Styles
- `.chat-area`
- Message bubbles (user/agent)
- Markdown rendering styles
- Thinking box
- Input area

**`modules/debug.css`** — Debug Panel Styles
- `.debug-panel`
- Round cards
- Timeline events
- Metrics display

**`modules/upload.css`** — File Upload Styles
- File tags
- Upload button
- Multi-media content display
- File list items

**`modules/modal.css`** — Modal Styles
- Agent config viewer modal
- Overlay and container

**`modules/utils.css`** — Utility Class Styles
- Structured data table styles
- Responsive styles
- Animation effects (glitch, pulse)

## Entry Points

### JavaScript Entry (`chat.js`)

```javascript
// Import all modules (execution order matters)
import './state.js';
import './api.js';
import './modules/agents.js';
import './modules/session.js';
import './modules/knowledge.js';
import './modules/ui.js';
import './modules/debug.js';

// Initialize application
document.addEventListener('DOMContentLoaded', init);
```

### CSS Entry (`chat.css`)

```css
/* Import all module styles */
@import url('base.css');
@import url('modules/header.css');
@import url('modules/sidebar.css');
@import url('modules/chat.css');
@import url('modules/debug.css');
@import url('modules/upload.css');
@import url('modules/modal.css');
@import url('modules/utils.css');
```

## Migration Strategy

1. Create new directory structure
2. Extract code from original files into modules
3. Update entry point files with imports
4. Test functionality incrementally
5. No HTML changes required

## Benefits

- ✅ Zero-build — native ES6 modules
- ✅ Single entry point — HTML unchanged
- ✅ Preserved global state — minimal refactoring risk
- ✅ Clear module responsibilities — easier maintenance
- ✅ Hybrid structure — core modules prominent, auxiliary organized

## Testing Checklist

- [ ] Agent selection works
- [ ] Message sending and streaming
- [ ] Debug panel displays correctly
- [ ] Session management functional
- [ ] File upload works
- [ ] Knowledge base upload works
- [ ] Markdown rendering works
- [ ] Structured data tables display
- [ ] All styles applied correctly
