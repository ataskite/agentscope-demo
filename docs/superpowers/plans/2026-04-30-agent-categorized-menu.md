# Agent Categorized Accordion Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the flat agent sidebar list into a categorized two-level accordion menu (Single / Expert / Collaboration) with collapsible groups.

**Architecture:** Add `category` field to backend config → frontend groups agents by category → renders accordion with collapsible sections. Only 4 files change; all existing agent-card styling and selection logic is preserved.

**Tech Stack:** Java 17 (Lombok POJO), SnakeYAML, Vanilla JS (ES6 modules), CSS (cyberpunk theme with CSS variables).

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/java/com/skloda/agentscope/agent/AgentConfig.java` | Modify | Add `category` field |
| `src/main/resources/config/agents.yml` | Modify | Add `category` to all 19 agents |
| `src/main/resources/static/scripts/modules/agents.js` | Modify | Rewrite `loadAgents()` to group + render accordion |
| `src/main/resources/static/styles/modules/sidebar.css` | Modify | Add accordion group styles (~40 lines) |

---

### Task 1: Add `category` field to AgentConfig.java

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java:37-38`

- [ ] **Step 1: Add the field**

After the `modality` field (line 38), add the `category` field:

```java
    // Modality settings
    private String modality = "text"; // text, vision, audio

    // Agent category for UI grouping
    private String category = "single"; // single, expert, collaboration
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfig.java
git commit -m "feat: add category field to AgentConfig for UI grouping"
```

---

### Task 2: Add category to all agents in agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add category to all single agents**

Add `category: single` after each `agentId` line for these 11 agents. Place it right after the `agentId` line, before `name`:

- `chat-basic` (line 3)
- `tool-test-simple` (line 29)
- `task-document-analysis` (line 60)
- `bank-invoice` (line 89)
- `rag-chat` (line 157)
- `vision-analyzer` (line 187)
- `voice-assistant` (line 219)
- `invoice-extractor` (line 241)
- `idcard-extractor` (line 271)
- `search-assistant` (line 302)
- `project-planner` (line 345)

Example for each:
```yaml
  - agentId: chat-basic
    category: single
    name: Basic Chat
```

- [ ] **Step 2: Add category to all expert agents**

Add `category: expert` to these 7 agents:

- `doc-expert` (line 393)
- `search-expert` (line 413)
- `vision-expert` (line 431)
- `sales-expert` (line 449)
- `support-agent` (line 466)
- `sales-agent` (line 483)
- `complaint-agent` (line 500)

Example:
```yaml
  - agentId: doc-expert
    category: expert
    name: 文档专家
```

- [ ] **Step 3: Add category to all collaboration agents**

Add `category: collaboration` to these 3 agents:

- `doc-analysis-pipeline` (line 519)
- `smart-router` (line 542)
- `customer-service` (line 571)

Example:
```yaml
  - agentId: doc-analysis-pipeline
    category: collaboration
    type: SEQUENTIAL
    name: 文档分析流水线
```

- [ ] **Step 4: Verify app starts**

Run: `mvn spring-boot:run` (then Ctrl+C after "Started AgentScopeDemoApplication")
Expected: No YAML parse errors, logs show "Loaded 19 agent configurations"

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add category field to all agent configs in agents.yml"
```

---

### Task 3: Add accordion CSS styles to sidebar.css

**Files:**
- Modify: `src/main/resources/static/styles/modules/sidebar.css`

- [ ] **Step 1: Add group styles after the existing `.agent-card.active` rule (after line 215)**

Insert the following CSS block. Place it after the `.agent-card.active .agent-card-desc` rule and before the `/* ===== SESSION LIST ===== */` comment:

```css
/* ===== AGENT GROUP ACCORDION ===== */
.agent-group-header {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    padding: var(--space-sm) var(--space-md);
    margin-bottom: var(--space-xs);
    cursor: pointer;
    border-radius: 6px;
    transition: background 0.2s;
    user-select: none;
}

.agent-group-header:hover {
    background: var(--bg-card-hover);
}

.agent-group-arrow {
    font-size: 10px;
    color: var(--text-muted);
    transition: transform 0.25s ease;
    flex-shrink: 0;
    width: 14px;
    text-align: center;
}

.agent-group-header.collapsed .agent-group-arrow {
    transform: rotate(-90deg);
}

.agent-group-icon {
    font-size: 14px;
    flex-shrink: 0;
}

.agent-group-icon.color-cyan {
    filter: drop-shadow(0 0 4px rgba(0, 240, 255, 0.6));
}

.agent-group-icon.color-green {
    filter: drop-shadow(0 0 4px rgba(0, 255, 136, 0.6));
}

.agent-group-icon.color-magenta {
    filter: drop-shadow(0 0 4px rgba(255, 0, 204, 0.6));
}

.agent-group-label {
    font-size: 11px;
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 1px;
    flex: 1;
}

.agent-group-count {
    font-size: 10px;
    color: var(--neon-blue);
    background: rgba(0, 184, 255, 0.1);
    padding: 1px 6px;
    border-radius: 10px;
    text-shadow: 0 0 5px rgba(0, 184, 255, 0.3);
}

.agent-group-body {
    overflow: hidden;
    transition: max-height 0.3s ease, opacity 0.25s ease;
    max-height: 2000px;
    opacity: 1;
}

.agent-group-body.collapsed {
    max-height: 0;
    opacity: 0;
    transition: max-height 0.25s ease, opacity 0.15s ease;
}
```

- [ ] **Step 2: Verify no syntax errors**

Open `http://localhost:8080` in browser DevTools console. Expected: No CSS-related errors (page should load normally with existing flat list still visible).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/styles/modules/sidebar.css
git commit -m "feat: add accordion group CSS styles for categorized agent menu"
```

---

### Task 4: Rewrite loadAgents() in agents.js for accordion rendering

**Files:**
- Modify: `src/main/resources/static/scripts/modules/agents.js:1-48`

This is the core change. The `loadAgents()` function is completely rewritten to group agents by category and render accordion sections. All other functions in the file (`selectAgent`, `showSamplePrompts`, `showAgentConfig`, etc.) remain unchanged.

- [ ] **Step 1: Add CATEGORIES constant at the top of the file**

Add this constant after the import statements (after line 4):

```js
import { escapeHtml } from './utils.js';
import { setStreamingState } from './ui.js';
import { agents } from '../state.js';

/* ===== CATEGORY DEFINITIONS ===== */
const CATEGORIES = [
    { key: 'single',        label: '单Agent',       icon: '⚡', color: 'cyan'    },
    { key: 'expert',        label: '专家Agent',      icon: '🎯', color: 'green'   },
    { key: 'collaboration', label: '多智能体协作',   icon: '🔗', color: 'magenta' },
];
```

- [ ] **Step 2: Replace the loadAgents() function**

Replace the entire `loadAgents()` function (lines 7-49) with:

```js
/* ===== LOAD AGENTS ===== */
export async function loadAgents() {
    try {
        const agentList = await fetchAgents();

        const agentListEl = document.getElementById('agentList');
        agentListEl.innerHTML = '';

        // Group agents by category
        var grouped = {};
        agentList.forEach(function(agent) {
            agents[agent.agentId] = {
                name: agent.name,
                desc: agent.description,
                config: agent
            };

            var cat = agent.category || 'single';
            if (!grouped[cat]) {
                grouped[cat] = [];
            }
            grouped[cat].push(agent);
        });

        window.agents = agents;

        // Render each category group
        CATEGORIES.forEach(function(category, index) {
            var agentsInGroup = grouped[category.key] || [];
            if (agentsInGroup.length === 0) return;

            var isExpanded = (index === 0);

            // Group header
            var header = document.createElement('div');
            header.className = 'agent-group-header' + (isExpanded ? '' : ' collapsed');
            header.dataset.category = category.key;
            header.innerHTML =
                '<span class="agent-group-arrow">▼</span>' +
                '<span class="agent-group-icon color-' + category.color + '">' + category.icon + '</span>' +
                '<span class="agent-group-label">' + category.label + '</span>' +
                '<span class="agent-group-count">' + agentsInGroup.length + '</span>';

            header.onclick = function() {
                header.classList.toggle('collapsed');
                body.classList.toggle('collapsed');
            };

            // Group body
            var body = document.createElement('div');
            body.className = 'agent-group-body' + (isExpanded ? '' : ' collapsed');

            agentsInGroup.forEach(function(agent) {
                var card = document.createElement('div');
                card.className = 'agent-card';
                card.dataset.agentId = agent.agentId;
                card.onclick = function() { selectAgent(agent.agentId); };

                var namespace = agent.agentId.split('.')[0].toUpperCase();
                card.innerHTML =
                    '<button class="agent-card-info-btn" onclick="event.stopPropagation(); showAgentConfig(\'' + agent.agentId + '\')" title="View config"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg></button>' +
                    '<div class="agent-card-icon">' + namespace.substring(0, 2) + '</div>' +
                    '<div class="agent-card-info">' +
                        '<div class="agent-card-name">' + escapeHtml(agent.name) + '</div>' +
                        '<div class="agent-card-desc">' + escapeHtml(agent.description) + '</div>' +
                    '</div>';

                body.appendChild(card);
            });

            agentListEl.appendChild(header);
            agentListEl.appendChild(body);
        });

        // Auto-select first agent
        if (agentList.length > 0) {
            selectAgent(agentList[0].agentId);
        }

        window.agents = agents;
    } catch (err) {
        console.error('Failed to load agents:', err);
    }
}
```

- [ ] **Step 3: Verify in browser**

Run: `mvn spring-boot:run`

Open `http://localhost:8080` and verify:
1. Three group headers appear in sidebar (单Agent, 专家Agent, 多智能体协作)
2. Only "单Agent" is expanded, others collapsed
3. Clicking a group header toggles expand/collapse with animation
4. Clicking an agent card selects it (cyan border, header updates)
5. Clicking the gear icon opens the config modal
6. The first agent (Basic Chat) is auto-selected on load

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/scripts/modules/agents.js
git commit -m "feat: categorize agent list into accordion groups (single/expert/collaboration)"
```

---

### Task 5: Auto-expand collapsed group when selecting agent programmatically

This handles the edge case where `selectAgent()` is called for an agent in a collapsed group (e.g., when switching sessions or on page reload with a saved selection).

**Files:**
- Modify: `src/main/resources/static/scripts/modules/agents.js` — in the `selectAgent()` function

- [ ] **Step 1: Add auto-expand logic to selectAgent()**

Inside `selectAgent()`, right after the line that toggles `.active` on agent cards (the `document.querySelectorAll('.agent-card')` block around line 100 in the current file), add auto-expand logic:

Find this block:
```js
    document.querySelectorAll('.agent-card').forEach(function(card) {
        card.classList.toggle('active', card.dataset.agentId === agentId);
    });
```

Replace with:
```js
    document.querySelectorAll('.agent-card').forEach(function(card) {
        card.classList.toggle('active', card.dataset.agentId === agentId);
    });

    // Auto-expand the group containing the selected agent
    var activeCard = document.querySelector('.agent-card.active');
    if (activeCard) {
        var groupBody = activeCard.closest('.agent-group-body');
        if (groupBody && groupBody.classList.contains('collapsed')) {
            groupBody.classList.remove('collapsed');
            var groupHeader = groupBody.previousElementSibling;
            if (groupHeader && groupHeader.classList.contains('agent-group-header')) {
                groupHeader.classList.remove('collapsed');
            }
        }
    }
```

- [ ] **Step 2: Verify auto-expand works**

1. Collapse the first group (单Agent)
2. Open browser console and run: `selectAgent('doc-expert')` — the expert group should auto-expand
3. Verify the agent card has the active (cyan border) state

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/scripts/modules/agents.js
git commit -m "feat: auto-expand collapsed group when selecting agent programmatically"
```

---

### Task 6: Final verification and cleanup

- [ ] **Step 1: Full manual test**

Open `http://localhost:8080` and walk through:
1. Page loads with 单Agent expanded, others collapsed — PASS
2. Click 专家Agent header → expands, 单Agent stays expanded — PASS
3. Click 专家Agent header again → collapses with animation — PASS
4. Select "文档专家" → active state, chat header updates, session created — PASS
5. Click gear icon on any agent → config modal opens — PASS
6. Switch to a different agent in a collapsed group → group auto-expands — PASS
7. Sample prompts appear correctly when switching agents — PASS
8. Upload a file → agent switches correctly — PASS
9. Send a message → SSE streaming works, debug panel shows events — PASS

- [ ] **Step 2: Verify `/api/agents` returns category**

Open `http://localhost:8080/api/agents` in browser. Verify each agent object includes `"category":"single"` (or `"expert"` / `"collaboration"`).

- [ ] **Step 3: Commit (if any final adjustments were made)**

```bash
git add -A
git commit -m "fix: final adjustments for categorized agent accordion menu"
```
