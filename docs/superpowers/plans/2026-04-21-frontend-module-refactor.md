# Frontend Module Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor monolithic `chat.js` (1794 lines) and `chat.css` (2452 lines) into modular structure for improved maintainability while maintaining zero-build architecture.

**Architecture:** Extract code into focused ES6 modules and CSS files, preserving global state and single-entry point. Use native ES6 imports without bundler.

**Tech Stack:** Vanilla JavaScript (ES6 modules), CSS3, no build tools

---

## Task 1: Create Directory Structure

**Files:**
- Create: `src/main/resources/static/scripts/modules/`
- Create: `src/main/resources/static/styles/modules/`

- [ ] **Step 1: Create JavaScript modules directory**

Run: `mkdir -p src/main/resources/static/scripts/modules`
Expected: Directory created

- [ ] **Step 2: Create CSS modules directory**

Run: `mkdir -p src/main/resources/static/styles/modules`
Expected: Directory created

- [ ] **Step 3: Verify directory structure**

Run: `ls -la src/main/resources/static/scripts/modules/ src/main/resources/static/styles/modules/`
Expected: Both directories exist and are empty

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/scripts/modules/ src/main/resources/static/styles/modules/
git commit -m "refactor: create module directories for frontend refactoring"
```

---

## Task 2: Extract State Module (state.js)

**Files:**
- Create: `src/main/resources/static/scripts/state.js`
- Modify: `src/main/resources/static/scripts/chat.js:1-12`

- [ ] **Step 1: Create state.js with all global variables**

```javascript
/* ===== STATE ===== */
let currentAgent = null;
let isStreaming = false;
let currentAbortController = null;
let messageCount = 0;
let uploadedFile = null;
let uploadedImages = [];  // Array of {fileId, fileName, filePath, fileType}
let uploadedAudio = null;  // {fileId, fileName, filePath, fileType}
let agentRawMarkdown = '';  // Accumulated raw markdown for current agent response
let currentSessionId = null;  // Active session ID

const agents = {};

/* ===== OBSERVABILITY STATE (per-round) ===== */
var rounds = [];
var currentRound = null;
var roundNumber = 0;

/* ===== THINKING BOX STATE ===== */
var currentThinkingBox = null;
var currentAgentMessageWrapper = null;
var thinkingContent = '';
var currentFileInfo = null;

// Export for use in other modules (optional, for transparency)
export { currentAgent, isStreaming, currentAbortController, messageCount, uploadedFile, uploadedImages, uploadedAudio, agentRawMarkdown, currentSessionId, agents, rounds, currentRound, roundNumber, currentThinkingBox, currentAgentMessageWrapper, thinkingContent, currentFileInfo };
```

Expected: File created at `src/main/resources/static/scripts/state.js`

- [ ] **Step 2: Remove state variables from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 1-12 and lines 224-227 (the STATE and OBSERVABILITY STATE sections). Also remove lines 1000-1003 (thinking box state variables).

Expected: chat.js no longer contains global state variable declarations

- [ ] **Step 3: Add state import to chat.js at the top**

Add at the very top of `src/main/resources/static/scripts/chat.js`:
```javascript
import './state.js';
```

Expected: chat.js now imports state module

- [ ] **Step 4: Test in browser**

Open http://localhost:8080 and verify console shows no errors, state variables accessible

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/scripts/state.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract global state to state.js module"
```

---

## Task 3: Extract API Module (api.js)

**Files:**
- Create: `src/main/resources/static/scripts/api.js`
- Modify: `src/main/resources/static/scripts/chat.js:14-38`

- [ ] **Step 1: Create api.js with SSE parser and communication functions**

```javascript
/* ===== SSE PARSER ===== */
export function createSSEParser() {
    var buffer = '';
    return {
        parse: function(chunk, onEvent) {
            buffer += chunk;
            var lines = buffer.split('\n');
            buffer = lines.pop();
            var currentEvent = {};
            for (var i = 0; i < lines.length; i++) {
                var line = lines[i];
                if (line.startsWith('data:')) {
                    currentEvent.data = line.slice(5).trim();
                } else if (line.startsWith('event:')) {
                    currentEvent.event = line.slice(6).trim();
                } else if (line === '') {
                    if (currentEvent.data) {
                        onEvent(currentEvent);
                    }
                    currentEvent = {};
                }
            }
        }
    };
}

/* ===== FILE UPLOAD API ===== */
export async function uploadFile(file) {
    var formData = new FormData();
    formData.append('file', file);

    var response = await fetch('/chat/upload', {
        method: 'POST',
        body: formData
    });
    return await response.json();
}

/* ===== AGENT LIST API ===== */
export async function fetchAgents() {
    var response = await fetch('/api/agents');
    return await response.json();
}

/* ===== SESSION API ===== */
export async function fetchSessions() {
    var response = await fetch('/api/sessions');
    return await response.json();
}

export async function createSession(agentId) {
    var response = await fetch('/api/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ agentId: agentId })
    });
    return await response.json();
}

export async function deleteSession(sessionId) {
    return await fetch('/api/sessions/' + sessionId, { method: 'DELETE' });
}

/* ===== KNOWLEDGE API ===== */
export async function fetchKnowledgeDocs() {
    var response = await fetch('/api/knowledge/documents');
    return await response.json();
}

export async function uploadKnowledgeDoc(file) {
    var formData = new FormData();
    formData.append('file', file);
    var response = await fetch('/api/knowledge/upload', {
        method: 'POST',
        body: formData
    });
    return await response.json();
}

export async function removeKnowledgeDoc(fileName) {
    return await fetch('/api/knowledge/documents/' + encodeURIComponent(fileName), { method: 'DELETE' });
}

/* ===== SKILL/TOOL INFO API ===== */
export async function fetchSkillInfo(skillName) {
    var response = await fetch('/api/skills/' + skillName);
    if (!response.ok) {
        var err = await response.json();
        throw new Error(err.error || 'Skill not found');
    }
    return await response.json();
}

export async function fetchToolInfo(toolName) {
    var response = await fetch('/api/tools/' + toolName);
    if (!response.ok) {
        var err = await response.json();
        throw new Error(err.error || 'Tool not found');
    }
    return await response.json();
}
```

Expected: File created at `src/main/resources/static/scripts/api.js`

- [ ] **Step 2: Remove SSE parser from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 14-38 (the SSE PARSER section after state import).

Expected: chat.js no longer contains createSSEParser function

- [ ] **Step 3: Add api import to chat.js after state import**

Add after the state import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { createSSEParser, uploadFile, fetchAgents, fetchSessions, createSession, deleteSession, fetchKnowledgeDocs, uploadKnowledgeDoc, removeKnowledgeDoc, fetchSkillInfo, fetchToolInfo } from './api.js';
```

Expected: chat.js now imports api functions

- [ ] **Step 4: Update loadAgents function to use fetchAgents**

Edit `src/main/resources/static/scripts/chat.js`: Change line 232 from `const response = await fetch('/api/agents');` to `const agentList = await fetchAgents();` and remove the response.json() line.

Expected: loadAgents uses imported fetchAgents function

- [ ] **Step 5: Update loadSessions function to use fetchSessions**

Edit `src/main/resources/static/scripts/chat.js`: Change line 1566 from `var response = await fetch('/api/sessions');` to `var sessions = await fetchSessions();` and remove the response.json() line.

Expected: loadSessions uses imported fetchSessions function

- [ ] **Step 6: Update createNewSession function to use createSession**

Edit `src/main/resources/static/scripts/chat.js`: Change lines 1599-1604 to use `var data = await createSession(currentAgent);` and remove the fetch call.

Expected: createNewSession uses imported createSession function

- [ ] **Step 7: Update deleteSession function to use deleteSession API**

Edit `src/main/resources/static/scripts/chat.js`: Change line 1631 to `await deleteSession(sessionId);` and remove the fetch call.

Expected: deleteSession uses imported deleteSession function

- [ ] **Step 8: Update loadKnowledgeDocs function to use fetchKnowledgeDocs**

Edit `src/main/resources/static/scripts/chat.js`: Change line 1662 to `var docs = await fetchKnowledgeDocs();` and remove the fetch call.

Expected: loadKnowledgeDocs uses imported fetchKnowledgeDocs function

- [ ] **Step 9: Update uploadToKnowledge function to use uploadKnowledgeDoc**

Edit `src/main/resources/static/scripts/chat.js`: Change lines 1693-1697 to `var data = await uploadKnowledgeDoc(file);` and remove the fetch/formData code.

Expected: uploadToKnowledge uses imported uploadKnowledgeDoc function

- [ ] **Step 10: Update removeKnowledgeDoc function to use removeKnowledgeDoc**

Edit `src/main/resources/static/scripts/chat.js`: Change line 1712 to `await removeKnowledgeDoc(fileName);` and remove the fetch call.

Expected: removeKnowledgeDoc uses imported removeKnowledgeDoc function

- [ ] **Step 11: Update showSkillInfo function to use fetchSkillInfo**

Edit `src/main/resources/static/scripts/chat.js`: Change lines 373-379 to use `var info = await fetchSkillInfo(skillName);` and remove the fetch/error handling code.

Expected: showSkillInfo uses imported fetchSkillInfo function

- [ ] **Step 12: Update showToolInfo function to use fetchToolInfo**

Edit `src/main/resources/static/scripts/chat.js`: Change lines 413-419 to use `var info = await fetchToolInfo(toolName);` and remove the fetch/error handling code.

Expected: showToolInfo uses imported fetchToolInfo function

- [ ] **Step 13: Update handleFileSelect function to use uploadFile**

Edit `src/main/resources/static/scripts/chat.js`: Change lines 1392-1397 to `var data = await uploadFile(file);` and remove the fetch/formData code.

Expected: handleFileSelect uses imported uploadFile function

- [ ] **Step 14: Test in browser**

Open http://localhost:8080, test agent loading, session creation, knowledge upload, file upload

- [ ] **Step 15: Commit**

```bash
git add src/main/resources/static/scripts/api.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract API communication to api.js module"
```

---

## Task 4: Extract Utils Module (modules/utils.js)

**Files:**
- Create: `src/main/resources/static/scripts/modules/utils.js`
- Modify: `src/main/resources/static/scripts/chat.js:40-211, 1350-1355`

- [ ] **Step 1: Create modules/utils.js with utility functions**

```javascript
/* ===== MARKED CONFIG ===== */
if (typeof marked !== 'undefined') {
    marked.setOptions({
        breaks: true,
        gfm: true
    });
}

/* ===== MARKDOWN RENDERING ===== */
export function renderMarkdown(text) {
    // Check if text contains structured data
    var structuredData = extractStructuredData(text);
    if (structuredData) {
        return renderStructuredData(structuredData, text);
    }

    if (typeof marked !== 'undefined') {
        return marked.parse(text);
    }
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Extract structured data from text (JSON or special format)
function extractStructuredData(text) {
    // Try to find JSON in code blocks
    var jsonMatch = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
    if (jsonMatch) {
        try {
            return JSON.parse(jsonMatch[1]);
        } catch (e) {
            // Not valid JSON, continue
        }
    }

    // Try to find raw JSON
    try {
        if (text.trim().startsWith('{') && text.trim().endsWith('}')) {
            return JSON.parse(text);
        }
    } catch (e) {
        // Not valid JSON
    }

    return null;
}

// Render structured data as a table
function renderStructuredData(data, originalText) {
    var html = '';

    // Add export button
    html += '<div class="structured-data-actions">' +
        '<button class="export-btn" onclick="exportStructuredData(this)" data-data=\'' + JSON.stringify(data).replace(/'/g, "\\'") + '\'">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>' +
        '导出 JSON</button>' +
        '</div>';

    // Render as table
    if (typeof data === 'object' && data !== null) {
        if (Array.isArray(data)) {
            // Array of objects
            if (data.length > 0 && typeof data[0] === 'object') {
                html += renderObjectTable(data);
            } else {
                // Simple array
                html += '<div class="structured-data-list">' +
                    data.map(function(item) { return '<div class="structured-data-item">' + escapeHtml(String(item)) + '</div>'; }).join('') +
                    '</div>';
            }
        } else {
            // Single object
            html += renderObjectTable([data]);
        }
    }

    // Add original text as markdown if available
    if (originalText && originalText !== JSON.stringify(data)) {
        html += '<div class="structured-data-original">';
        if (typeof marked !== 'undefined') {
            html += marked.parse(originalText);
        } else {
            html += '<p>' + escapeHtml(originalText) + '</p>';
        }
        html += '</div>';
    }

    return html;
}

// Render array of objects as table
function renderObjectTable(dataArray) {
    if (!dataArray || dataArray.length === 0) {
        return '<div class="structured-data-empty">无数据</div>';
    }

    // Get all unique keys from all objects
    var keys = {};
    dataArray.forEach(function(obj) {
        if (typeof obj === 'object' && obj !== null) {
            Object.keys(obj).forEach(function(key) {
                keys[key] = true;
            });
        }
    });
    var columns = Object.keys(keys);

    if (columns.length === 0) {
        return '<div class="structured-data-empty">无数据字段</div>';
    }

    var html = '<div class="structured-data-table-wrapper"><table class="structured-data-table">';

    // Header
    html += '<thead><tr>';
    columns.forEach(function(col) {
        html += '<th>' + escapeHtml(col) + '</th>';
    });
    html += '</tr></thead>';

    // Body
    html += '<tbody>';
    dataArray.forEach(function(row) {
        html += '<tr>';
        columns.forEach(function(col) {
            var value = row[col];
            var displayValue = formatValue(value);
            html += '<td>' + displayValue + '</td>';
        });
        html += '</tr>';
    });
    html += '</tbody></table></div>';

    return html;
}

// Format value for display
function formatValue(value) {
    if (value === null || value === undefined) {
        return '<span class="value-null">-</span>';
    }
    if (typeof value === 'object') {
        return '<code class="value-object">' + escapeHtml(JSON.stringify(value)) + '</code>';
    }
    if (typeof value === 'number') {
        return '<span class="value-number">' + escapeHtml(String(value)) + '</span>';
    }
    if (typeof value === 'boolean') {
        return '<span class="value-boolean">' + (value ? '是' : '否') + '</span>';
    }
    return escapeHtml(String(value));
}

// Export structured data as JSON file (global function for onclick)
window.exportStructuredData = function(btn) {
    var dataStr = btn.getAttribute('data-data');
    try {
        var data = JSON.parse(dataStr);
        var jsonStr = JSON.stringify(data, null, 2);
        var blob = new Blob([jsonStr], { type: 'application/json' });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'structured-data-' + Date.now() + '.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch (e) {
        console.error('Export failed:', e);
    }
};

/* ===== HTML HELPERS ===== */
export function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

export function getTimestamp() {
    return new Date().toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

export function formatDuration(ms) {
    if (ms < 0) return '-';
    if (ms < 1000) return Math.round(ms) + 'ms';
    return (ms / 1000).toFixed(1) + 's';
}

export function scrollToBottom(element) {
    requestAnimationFrame(function() {
        element.scrollTop = element.scrollHeight;
    });
}

/* ===== FILE LIST HELPER ===== */
export function createFileList(fileInfo) {
    if (!fileInfo) return '';

    var lastDotIndex = fileInfo.fileName.lastIndexOf('.');
    var ext = lastDotIndex >= 0
        ? fileInfo.fileName.substring(lastDotIndex).toLowerCase()
        : '';
    var iconLabel = '';
    var iconClass = '';

    switch (ext) {
        case '.docx':
            iconLabel = 'W';
            iconClass = 'docx';
            break;
        case '.pdf':
            iconLabel = 'P';
            iconClass = 'pdf';
            break;
        case '.xlsx':
            iconLabel = 'X';
            iconClass = 'xlsx';
            break;
        default:
            iconLabel = '?';
            iconClass = '';
    }

    return '<div class="file-list">' +
        '<div class="file-list-item">' +
        '<span class="file-icon ' + iconClass + '">' + iconLabel + '</span>' +
        '<span class="file-name">' + escapeHtml(fileInfo.fileName) + '</span>' +
        '</div>' +
        '</div>';
}
```

Expected: File created at `src/main/resources/static/scripts/modules/utils.js`

- [ ] **Step 2: Remove utility functions from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 40-211 (MARKED CONFIG section), lines 1350-1355 (escapeHtml, getTimestamp), and lines 1526-1561 (createFileList).

Expected: chat.js no longer contains utility functions

- [ ] **Step 3: Add utils import to chat.js**

Add after the api import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { renderMarkdown, escapeHtml, getTimestamp, formatDuration, scrollToBottom, createFileList } from './modules/utils.js';
```

Expected: chat.js now imports utility functions

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify markdown rendering, file uploads, timestamps work

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/scripts/modules/utils.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract utility functions to modules/utils.js"
```

---

## Task 5: Extract UI Module (modules/ui.js)

**Files:**
- Create: `src/main/resources/static/scripts/modules/ui.js`
- Modify: `src/main/resources/static/scripts/chat.js:909-1123`

- [ ] **Step 1: Create modules/ui.js with UI rendering functions**

```javascript
import { renderMarkdown, escapeHtml, getTimestamp, scrollToBottom, createFileList } from './utils.js';

/* ===== DOM REFERENCES ===== */
export const chatMessages = document.getElementById('chatMessages');
export const messageInput = document.getElementById('messageInput');
export const sendBtn = document.getElementById('sendBtn');
export const chatEmpty = document.getElementById('chatEmpty');
export const chatHeaderName = document.getElementById('chatHeaderName');
export const chatHeaderDesc = document.getElementById('chatHeaderDesc');
export const debugPanel = document.getElementById('debugPanel');
export const debugRounds = document.getElementById('debugRounds');
export const debugToggle = document.getElementById('debugToggle');

/* ===== MESSAGE RENDERING ===== */
export function appendMessage(role, text, fileInfo) {
    var wrapper = document.createElement('div');
    wrapper.className = 'message ' + role;

    var avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    if (role === 'user') {
        avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/></svg>';
    } else {
        avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>';
    }

    var content = document.createElement('div');
    content.className = 'message-content';

    // Handle media info (can be fileInfo object or mediaInfo object with file/images/audio)
    if (fileInfo && role === 'user') {
        // Handle legacy fileInfo (document file)
        if (fileInfo.fileName && !fileInfo.images && !fileInfo.audio) {
            var fileListNode = document.createElement('div');
            fileListNode.innerHTML = createFileList(fileInfo);
            content.appendChild(fileListNode.firstChild);
        }
        // Handle new mediaInfo structure
        else {
            // Document file
            if (fileInfo.file) {
                var fileListNode = document.createElement('div');
                fileListNode.innerHTML = createFileList(fileInfo.file);
                content.appendChild(fileListNode.firstChild);
            }
            // Images
            if (fileInfo.images && fileInfo.images.length > 0) {
                var imagesContainer = document.createElement('div');
                imagesContainer.className = 'message-images';
                fileInfo.images.forEach(function(img) {
                    var imgWrapper = document.createElement('div');
                    imgWrapper.className = 'message-image-wrapper';
                    var imgEl = document.createElement('img');
                    imgEl.src = '/chat/download?fileId=' + img.fileId;
                    imgEl.alt = escapeHtml(img.fileName);
                    imgEl.onclick = function() {
                        showImageModal(img); // Use the image from the array
                    };
                    imgWrapper.appendChild(imgEl);
                    imagesContainer.appendChild(imgWrapper);
                });
                content.appendChild(imagesContainer);
            }
            // Audio
            if (fileInfo.audio) {
                var audioContainer = document.createElement('div');
                audioContainer.className = 'message-audio';
                var audioEl = document.createElement('audio');
                audioEl.controls = true;
                audioEl.src = '/chat/download?fileId=' + fileInfo.audio.fileId;
                var audioLabel = document.createElement('div');
                audioLabel.className = 'message-audio-label';
                audioLabel.textContent = escapeHtml(fileInfo.audio.fileName);
                audioContainer.appendChild(audioEl);
                audioContainer.appendChild(audioLabel);
                content.appendChild(audioContainer);
            }
        }
    }

    var bubble = document.createElement('div');
    bubble.className = 'message-bubble';
    if (role === 'agent') {
        bubble.classList.add('md-render');
        bubble.innerHTML = renderMarkdown(text);
    } else {
        bubble.textContent = text;
    }

    var time = document.createElement('div');
    time.className = 'message-time';
    time.textContent = getTimestamp();

    content.appendChild(bubble);
    content.appendChild(time);
    wrapper.appendChild(avatar);
    wrapper.appendChild(content);
    chatMessages.appendChild(wrapper);
    scrollToBottom(chatMessages);

    return bubble;
}

/* ===== THINKING BOX MANAGEMENT ===== */
export function createThinkingBox(fileInfo) {
    var wrapper = document.createElement('div');
    wrapper.className = 'message agent';
    wrapper.style.maxWidth = '100%';
    wrapper.style.width = '100%';

    var avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>';

    var content = document.createElement('div');
    content.className = 'agent-response-wrapper';

    if (fileInfo) {
        var fileListHtml = createFileList(fileInfo);
        if (fileListHtml) {
            var fileListNode = document.createElement('div');
            fileListNode.innerHTML = fileListHtml;
            if (fileListNode.firstChild) {
                content.appendChild(fileListNode.firstChild);
            }
        }
    }

    var box = document.createElement('div');
    box.className = 'thinking-box';
    box.innerHTML = '<div class="thinking-header" onclick="toggleThinking(this)">' +
        '<div class="thinking-header-icon"><div class="thinking-spinner"></div></div>' +
        '<div class="thinking-header-text">Thinking</div>' +
        '</div>' +
        '<div class="thinking-content"></div>';

    content.appendChild(box);
    wrapper.appendChild(avatar);
    wrapper.appendChild(content);
    chatMessages.appendChild(wrapper);

    window.currentAgentMessageWrapper = wrapper;
    window.currentThinkingBox = box;
    scrollToBottom(chatMessages);
    return box;
}

export function updateThinkingBox(content, fileInfo) {
    if (!window.currentThinkingBox) {
        createThinkingBox(fileInfo || window.currentFileInfo);
    }
    window.thinkingContent += content;
    var lines = window.thinkingContent.split('\n');
    var filteredLines = lines.filter(function(line) {
        var trimmed = line.trim();
        return trimmed !== '' && !trimmed.includes('__fragment__');
    });
    var contentEl = window.currentThinkingBox.querySelector('.thinking-content');
    contentEl.textContent = filteredLines.join('\n\n');
    contentEl.scrollTop = contentEl.scrollHeight;
    scrollToBottom(chatMessages);
}

export function collapseThinkingBox() {
    if (window.currentThinkingBox && !window.currentThinkingBox.classList.contains('collapsed')) {
        window.currentThinkingBox.classList.add('collapsed');
        window.currentThinkingBox.classList.add('completed');
    }
}

export function completeThinkingBox() {
    if (window.currentThinkingBox) {
        window.currentThinkingBox.classList.add('completed');
        window.currentThinkingBox.classList.add('collapsed');
        window.thinkingContent = '';
        window.currentThinkingBox = null;
        window.currentFileInfo = null;
    }
}

// Global function for onclick attribute
window.toggleThinking = function(header) {
    var box = header.parentElement;
    box.classList.toggle('collapsed');
};

export function addAgentBubble() {
    if (!window.currentAgentMessageWrapper) {
        createThinkingBox(window.currentFileInfo);
    }
    var content = window.currentAgentMessageWrapper.querySelector('.agent-response-wrapper');

    var bubbleContent = document.createElement('div');
    bubbleContent.className = 'message-content';

    var bubble = document.createElement('div');
    bubble.className = 'message-bubble';

    var time = document.createElement('div');
    time.className = 'message-time';
    time.textContent = getTimestamp();

    bubbleContent.appendChild(bubble);
    bubbleContent.appendChild(time);
    content.appendChild(bubbleContent);

    scrollToBottom(chatMessages);
    return bubble;
}

export function removeTypingIndicator() {
    var el = document.getElementById('typingIndicator');
    if (el) el.remove();
}

export function setStreamingState(streaming) {
    window.isStreaming = streaming;
    messageInput.disabled = streaming;
    sendBtn.disabled = streaming;
    if (!streaming) {
        messageInput.focus();
    }
}

/* ===== IMAGE MODAL ===== */
export function showImageModal(img) {
    var overlay = document.createElement('div');
    overlay.className = 'image-modal-overlay';
    overlay.id = 'imageModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) closeImageModal();
    };

    overlay.innerHTML =
        '<div class="image-modal-content">' +
            '<button class="image-modal-close" onclick="closeImageModal()">×</button>' +
            '<img src="/chat/download?fileId=' + img.fileId + '" alt="' + escapeHtml(img.fileName) + '">' +
            '<div class="image-modal-filename">' + escapeHtml(img.fileName) + '</div>' +
        '</div>';

    document.body.appendChild(overlay);
}

// Global function for onclick
window.closeImageModal = function() {
    var modal = document.getElementById('imageModal');
    if (modal) {
        modal.remove();
    }
};

/* ===== TYPING INDICATOR ===== */
export function showTypingIndicator() {
    var typingEl = document.createElement('div');
    typingEl.className = 'typing-indicator';
    typingEl.id = 'typingIndicator';
    typingEl.innerHTML = '<div class="message-avatar"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg></div><div class="dots"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>';
    chatMessages.appendChild(typingEl);
    scrollToBottom(chatMessages);
}
```

Expected: File created at `src/main/resources/static/scripts/modules/ui.js`

- [ ] **Step 2: Remove UI functions from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 213-222 (DOM REFERENCES), lines 909-1123 (HELPERS section with appendMessage, thinking box functions, etc.).

Expected: chat.js no longer contains UI rendering functions

- [ ] **Step 3: Add ui import to chat.js**

Add after the utils import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { chatMessages, messageInput, sendBtn, chatEmpty, chatHeaderName, chatHeaderDesc, debugPanel, debugRounds, debugToggle, appendMessage, createThinkingBox, updateThinkingBox, collapseThinkingBox, completeThinkingBox, addAgentBubble, removeTypingIndicator, setStreamingState, showTypingIndicator } from './modules/ui.js';
```

Expected: chat.js now imports UI functions

- [ ] **Step 4: Update sendMessage function to use showTypingIndicator**

Edit `src/main/resources/static/scripts/chat.js`: Replace the typing indicator creation code (around lines 591-596) with `showTypingIndicator();`.

Expected: sendMessage uses imported showTypingIndicator function

- [ ] **Step 5: Test in browser**

Open http://localhost:8080, test message sending, typing indicator, thinking box

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/scripts/modules/ui.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract UI rendering to modules/ui.js"
```

---

## Task 6: Extract Debug Module (modules/debug.js)

**Files:**
- Create: `src/main/resources/static/scripts/modules/debug.js`
- Modify: `src/main/resources/static/scripts/chat.js:1124-1367`

- [ ] **Step 1: Create modules/debug.js with debug panel functions**

```javascript
import { debugRounds, debugPanel, debugToggle, scrollToBottom } from './ui.js';
import { escapeHtml, formatDuration } from './utils.js';

/* ===== ROUND MANAGEMENT ===== */
export function startRound(userMessage, roundNumber, currentAgent, agents) {
    var round = {
        number: roundNumber,
        startTime: Date.now(),
        endTime: null,
        userMessage: userMessage.substring(0, 50),
        inputTokens: 0,
        outputTokens: 0,
        totalTokens: 0,
        llmTime: 0,
        toolTime: 0,
        thinkingTime: 0,
        thinkingCycleStart: null,
        llmCallCount: 0,
        toolCallCount: 0,
        totalLlmCalls: 0,
        totalToolCalls: 0,
        status: 'running',
        timelineStep: 0,
        _currentLlmRow: null,
        _currentLlmStart: null,
        _currentLlmCallNum: 0,
        _currentToolRow: null,
        _currentToolStart: null
    };
    window.rounds.push(round);
    window.currentRound = round;

    var card = document.createElement('div');
    card.className = 'round-card running';
    card.id = 'round-' + round.number;

    var preview = userMessage.length > 30 ? userMessage.substring(0, 30) + '...' : userMessage;
    var modelName = currentAgent && agents[currentAgent] ? agents[currentAgent].config.modelName : '';

    card.innerHTML =
        '<div class="round-header" onclick="toggleRoundDetails(' + round.number + ')">' +
            '<span class="round-num">#' + round.number + '</span>' +
            '<span class="round-msg">' + escapeHtml(preview) + '</span>' +
        '</div>' +
        '<div class="round-body" id="round-body-' + round.number + '">' +
            '<div class="round-metrics" id="round-metrics-' + round.number + '">' +
                (modelName ? '<div class="rm-row"><span class="rm-label">Model</span><span class="rm-value">' + escapeHtml(modelName) + '</span></div>' : '') +
            '</div>' +
            '<div class="round-timeline" id="round-timeline-' + round.number + '"></div>' +
        '</div>';

    debugRounds.appendChild(card);
    scrollToBottom(debugRounds);
    return round;
}

export function endRound(status) {
    console.log('[endRound] Called with status:', status, 'currentRound:', window.currentRound, 'rounds.length:', window.rounds.length);

    var roundNumber = window.currentRound ? window.currentRound.number : window.roundNumber;
    var round = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);

    if (!round) {
        console.error('[endRound] No round to end! rounds:', window.rounds);
        return;
    }

    roundNumber = round.number;
    console.log('[endRound] Ending round #' + roundNumber + ' with status:', status);

    if (!round.endTime) {
        round.endTime = Date.now();
    }
    round.status = status;

    // Update metrics for the round
    if (round === window.currentRound) {
        updateRoundMetrics();
    } else {
        updateRoundMetricsForRound(round);
    }

    // Stop all running timeline rows in this round
    var roundCard = document.getElementById('round-' + roundNumber);
    if (!roundCard) {
        console.error('[endRound] Round card not found: round-' + roundNumber);
        window.currentRound = null;
        return;
    }

    console.log('[endRound] Found round card, updating status');

    // Stop all running timeline rows
    roundCard.querySelectorAll('.rtl-row.status-running').forEach(function(row) {
        row.classList.remove('status-running');
        row.classList.add('status-ok');
    });

    // Update round card status
    roundCard.classList.remove('running');
    roundCard.classList.add(status === 'success' ? 'success' : 'error');
    console.log('[endRound] Updated card classes, running removed, ' + (status === 'success' ? 'success' : 'error') + ' added');

    window.currentRound = null;
    console.log('[endRound] Completed');
}

export function updateRoundMetrics() {
    if (!window.currentRound) return;
    updateRoundMetricsForRound(window.currentRound);
}

export function updateRoundMetricsForRound(r) {
    var metricsEl = document.getElementById('round-metrics-' + r.number);
    if (!metricsEl) return;

    var wallMs = (r.endTime || Date.now()) - r.startTime;
    var speed = r.llmTime > 0 ? Math.round(r.outputTokens / r.llmTime) : 0;
    var modelName = window.currentAgent && window.agents[window.currentAgent] ? window.agents[window.currentAgent].config.modelName : '';

    var html = '';
    if (modelName) {
        html += '<div class="rm-row"><span class="rm-label">Model</span><span class="rm-value">' + escapeHtml(modelName) + '</span></div>';
    }
    html += '<div class="rm-row"><span class="rm-label">Tokens</span><span class="rm-value"><span class="tokens-in"><svg class="token-icon" viewBox="0 0 16 16" width="12" height="12"><path d="M8 1v10M4 7l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>' + r.inputTokens.toLocaleString() + '</span><span class="tokens-out"><svg class="token-icon" viewBox="0 0 16 16" width="12" height="12"><path d="M8 13V3M4 7l4-4 4 4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>' + r.outputTokens.toLocaleString() + '</span></span></div>';
    html += '<div class="rm-row"><span class="rm-label">LLM</span><span class="rm-value">' + r.llmTime.toFixed(1) + 's (' + r.llmCallCount + ' calls)</span></div>';
    html += '<div class="rm-row"><span class="rm-label">Tools</span><span class="rm-value">' + formatDuration(r.toolTime) + ' (' + r.toolCallCount + ' calls)</span></div>';
    if (speed > 0) {
        html += '<div class="rm-row"><span class="rm-label">Speed</span><span class="rm-value">' + speed + ' tokens/s</span></div>';
    }

    metricsEl.innerHTML = html;
}

export function addTimelineRow(type, label, metrics, status) {
    if (!window.currentRound) return null;
    var timeline = document.getElementById('round-timeline-' + window.currentRound.number);
    if (!timeline) return null;

    window.currentRound.timelineStep = (window.currentRound.timelineStep || 0) + 1;
    var stepNum = window.currentRound.timelineStep;

    var row = document.createElement('div');
    row.className = 'rtl-row type-' + type;
    if (status) row.classList.add('status-' + status);
    row.innerHTML =
        '<span class="rtl-connector">' + getTimelineConnector(type) + '</span>' +
        '<span class="rtl-icon">' + getTimelineIcon(type) + '</span>' +
        '<span class="rtl-label">' + escapeHtml(label) + '</span>' +
        '<span class="rtl-metrics">' + (metrics || '') + '</span>' +
        '<span class="rtl-status">' + getStatusText(status) + '</span>';
    timeline.appendChild(row);

    scrollToBottom(debugRounds);
    return row;
}

function getTimelineIcon(type) {
    switch (type) {
        case 'phase': return '◆';
        case 'llm': return '↻';
        case 'skill': return '◉';
        case 'tool': return '⚡';
        case 'error': return '⚠';
        default: return '·';
    }
}

function getTimelineConnector(type) {
    switch (type) {
        case 'phase': return '─';
        case 'llm': return '├';
        case 'skill': return '├';
        case 'tool': return '│';
        default: return '│';
    }
}

function getStatusText(status) {
    switch (status) {
        case 'ok': return '✓';
        case 'fail': return '✗';
        case 'running': return '◎';
        default: return '';
    }
}

// Global function for onclick
window.toggleRoundDetails = function(roundNum) {
    var body = document.getElementById('round-body-' + roundNum);
    if (body) body.classList.toggle('collapsed');
};

export function clearDebug() {
    debugRounds.innerHTML = '';
    window.rounds = [];
    window.currentRound = null;
    window.roundNumber = 0;
}

/* ===== DEBUG PANEL TOGGLE ===== */
export function toggleDebug() {
    var isCollapsed = debugPanel.classList.contains('collapsed');
    if (isCollapsed) {
        debugPanel.classList.remove('collapsed');
        debugToggle.innerHTML = '&#x25B6;';
    } else {
        debugPanel.classList.add('collapsed');
        debugToggle.innerHTML = '&#x25C0;';
    }
}

/* ===== MULTI-AGENT EVENT HANDLERS ===== */
export function handlePipelineStart(data) {
    if (!debugRounds) return;
    var pipelineDiv = document.createElement('div');
    pipelineDiv.className = 'debug-pipeline';
    pipelineDiv.id = 'pipeline-' + Date.now();
    pipelineDiv.innerHTML = '<div class="debug-pipeline-header">' +
        '<span class="debug-pipeline-title">Pipeline: ' + escapeHtml(data.pipelineId || '') + '</span>' +
        '<span class="debug-pipeline-steps">' + (data.subAgents ? data.subAgents.length : 0) + ' steps</span>' +
        '</div>' +
        '<div class="debug-pipeline-steps-container" id="' + pipelineDiv.id + '-steps"></div>';
    debugRounds.appendChild(pipelineDiv);
}

export function handlePipelineStepStart(data) {
    var container = document.querySelector('.debug-pipeline-steps-container:last-child');
    if (!container) return;
    var stepDiv = document.createElement('div');
    stepDiv.className = 'debug-pipeline-step active';
    stepDiv.id = 'step-' + data.stepIndex;
    stepDiv.innerHTML = '<span class="debug-step-number">' + (data.stepIndex + 1) + '</span>' +
        '<span class="debug-step-agent">' + escapeHtml(data.agentId || '') + '</span>' +
        '<span class="debug-step-status">Running...</span>';
    container.appendChild(stepDiv);
}

export function handlePipelineStepEnd(data) {
    var stepDiv = document.getElementById('step-' + data.stepIndex);
    if (stepDiv) {
        stepDiv.classList.remove('active');
        stepDiv.classList.add('complete');
        var statusSpan = stepDiv.querySelector('.debug-step-status');
        if (statusSpan) statusSpan.textContent = 'Done (' + (data.duration_ms || 0) + 'ms)';
    }
}

export function handleRoutingDecision(data) {
    if (!debugRounds) return;
    var routingDiv = document.createElement('div');
    routingDiv.className = 'debug-routing';
    routingDiv.innerHTML = '<div class="debug-routing-header">' +
        '<span class="debug-routing-title">Routing Decision</span></div>' +
        '<div class="debug-routing-content">' +
        '<div class="debug-routing-selected"><span class="debug-routing-label">Selected:</span> ' +
        '<span class="debug-routing-agent">' + escapeHtml(data.selectedAgent || '') + '</span></div>' +
        '<div class="debug-routing-reasoning"><span class="debug-routing-label">Reasoning:</span> ' +
        '<span class="debug-routing-text">' + escapeHtml(data.reasoning || '') + '</span></div></div>';
    debugRounds.appendChild(routingDiv);
}

export function handleHandoffStart(data) {
    if (!debugRounds) return;
    var handoffDiv = document.createElement('div');
    handoffDiv.className = 'debug-handoff';
    handoffDiv.innerHTML = '<div class="debug-handoff-header">' +
        '<span class="debug-handoff-icon">\u2192</span>' +
        '<span class="debug-handoff-text">Handoff: <strong>' + escapeHtml(data.fromAgent || '') + '</strong> \u2192 <strong>' + escapeHtml(data.toAgent || '') + '</strong></span></div>' +
        '<div class="debug-handoff-reason">Reason: ' + escapeHtml(data.reason || '') + '</div>';
    debugRounds.appendChild(handoffDiv);
}
```

Expected: File created at `src/main/resources/static/scripts/modules/debug.js`

- [ ] **Step 2: Remove debug functions from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 1124-1367 (ROUND MANAGEMENT, DEBUG PANEL TOGGLE, and MULTI-AGENT EVENT HANDLERS sections).

Expected: chat.js no longer contains debug panel functions

- [ ] **Step 3: Add debug import to chat.js**

Add after the ui import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { startRound, endRound, addTimelineRow, clearDebug, toggleDebug, handlePipelineStart, handlePipelineStepStart, handlePipelineStepEnd, handleRoutingDecision, handleHandoffStart } from './modules/debug.js';
```

Expected: chat.js now imports debug functions

- [ ] **Step 4: Update sendMessage function to use startRound**

Edit `src/main/resources/static/scripts/chat.js`: Change line 589 from `startRound(message);` to `startRound(message, roundNumber, currentAgent, agents);`.

Expected: sendMessage passes required parameters to startRound

- [ ] **Step 5: Test in browser**

Open http://localhost:8080, test message sending and debug panel

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/scripts/modules/debug.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract debug panel to modules/debug.js"
```

---

## Task 7: Extract Agents Module (modules/agents.js)

**Files:**
- Create: `src/main/resources/static/scripts/modules/agents.js`
- Modify: `src/main/resources/static/scripts/chat.js:229-268, 270-477`

- [ ] **Step 1: Create modules/agents.js with agent management functions**

```javascript
import { fetchAgents, fetchSkillInfo, fetchToolInfo } from './api.js';
import { escapeHtml } from './utils.js';

/* ===== LOAD AGENTS ===== */
export async function loadAgents() {
    try {
        const agentList = await fetchAgents();

        const agentListEl = document.getElementById('agentList');
        agentListEl.innerHTML = '';

        agentList.forEach(function(agent) {
            window.agents[agent.agentId] = {
                name: agent.name,
                desc: agent.description,
                config: agent
            };

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

            agentListEl.appendChild(card);
        });

        if (agentList.length > 0) {
            selectAgent(agentList[0].agentId);
        }
    } catch (err) {
        // Agent loading error
    }
}

/* ===== SELECT AGENT ===== */
export function selectAgent(agentId) {
    removeFile();
    // Force cleanup streaming state to allow switching
    if (window.isStreaming) {
        window.isStreaming = false;
        setStreamingState(false);
        if (window.currentAbortController) {
            window.currentAbortController.abort();
            window.currentAbortController = null;
        }
        // Complete the current round if it exists
        if (window.currentRound) {
            var roundNum = window.currentRound.number;
            window.currentRound.endTime = Date.now();
            window.currentRound.status = 'interrupted';
            var card = document.getElementById('round-' + roundNum);
            if (card) {
                card.classList.remove('running');
                card.classList.add('error');
            }
            window.currentRound = null;
        }
        // Don't clear rounds array yet - wait for any pending events to finish
        setTimeout(function() {
            document.getElementById('debugRounds').innerHTML = '';
            window.rounds = [];
            window.roundNumber = 0;
        }, 500);
    } else {
        document.getElementById('debugRounds').innerHTML = '';
        window.rounds = [];
        window.currentRound = null;
        window.roundNumber = 0;
    }
    if (!window.agents[agentId]) return;
    window.currentAgent = agentId;

    document.querySelectorAll('.agent-card').forEach(function(card) {
        card.classList.toggle('active', card.dataset.agentId === agentId);
    });

    document.getElementById('chatHeaderName').textContent = window.agents[agentId].name;
    document.getElementById('chatHeaderDesc').textContent = window.agents[agentId].desc;

    document.getElementById('chatMessages').innerHTML = '';
    window.messageCount = 0;
    document.getElementById('chatMessages').appendChild(document.getElementById('chatEmpty'));
    document.getElementById('chatEmpty').style.display = 'flex';

    document.getElementById('messageInput').focus();

    // Reset agent message state
    window.currentThinkingBox = null;
    window.currentAgentMessageWrapper = null;
    window.thinkingContent = '';
    window.currentFileInfo = null;
    window.agentRawMarkdown = '';

    // Auto-create a new session for this agent
    createNewSession();
}

/* ===== CONFIG VIEWER ===== */
export function showAgentConfig(agentId) {
    var agent = window.agents[agentId];
    if (!agent || !agent.config) return;

    var config = agent.config;

    var skillsHtml;
    if (config.skills && config.skills.length > 0) {
        skillsHtml = '<div class="config-field-value tags">' +
            config.skills.map(function(s) { return '<span class="config-tag" onclick="showSkillInfo(\'' + String(s).replace(/'/g, "\\'").replace(/"/g, '\\"') + '\')">' + escapeHtml(s) + '</span>'; }).join('') +
            '</div>';
    } else {
        skillsHtml = '<div class="config-field-value tags"><span class="config-tag none">None</span></div>';
    }

    var userToolsHtml;
    if (config.userTools && config.userTools.length > 0) {
        userToolsHtml = '<div class="config-field-value tags">' +
            config.userTools.map(function(t) { return '<span class="config-tag tool" onclick="showToolInfo(\'' + String(t).replace(/'/g, "\\'").replace(/"/g, '\\"') + '\')">' + escapeHtml(t) + '</span>'; }).join('') +
            '</div>';
    } else {
        userToolsHtml = '<div class="config-field-value tags"><span class="config-tag none">None</span></div>';
    }

    var systemToolsHtml;
    if (config.systemTools && config.systemTools.length > 0) {
        systemToolsHtml = '<div class="config-field-value tags">' +
            config.systemTools.map(function(t) { return '<span class="config-tag system-tool" onclick="showToolInfo(\'' + String(t).replace(/'/g, "\\'").replace(/"/g, '\\"') + '\')">' + escapeHtml(t) + '</span>'; }).join('') +
            '</div>';
    } else {
        systemToolsHtml = '<div class="config-field-value tags"><span class="config-tag none">None</span></div>';
    }

    var overlay = document.createElement('div');
    overlay.className = 'config-modal-overlay';
    overlay.id = 'configModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) closeConfigModal();
    };

    overlay.innerHTML =
        '<div class="config-modal">' +
            '<div class="config-modal-header">' +
                '<div class="config-modal-title">Agent Configuration</div>' +
                '<button class="config-modal-close" onclick="closeConfigModal()">&#x2715;</button>' +
            '</div>' +
            '<div class="config-modal-body">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Agent ID</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.agentId) + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Name</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.name) + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Description</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.description) + '</div>' +
                '</div>' +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Model</div>' +
                    '<div class="config-field-value">' + escapeHtml(config.modelName) + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Streaming</div>' +
                    '<div class="config-field-value">' + (config.streaming ? 'Enabled' : 'Disabled') + '</div>' +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Thinking Mode</div>' +
                    '<div class="config-field-value">' + (config.enableThinking ? 'Enabled' : 'Disabled') + '</div>' +
                '</div>' +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">Skills</div>' +
                    skillsHtml +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">User Tools</div>' +
                    userToolsHtml +
                '</div>' +
                '<div class="config-field">' +
                    '<div class="config-field-label">System Tools</div>' +
                    systemToolsHtml +
                '</div>' +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">System Prompt</div>' +
                    '<div class="config-field-value mono">' + escapeHtml(config.systemPrompt || '') + '</div>' +
                '</div>' +
            '</div>' +
        '</div>';

    document.body.appendChild(overlay);
}

// Global function for onclick
window.closeConfigModal = function() {
    var modal = document.getElementById('configModal');
    if (modal) modal.remove();
};

export async function showSkillInfo(skillName) {
    try {
        var info = await fetchSkillInfo(skillName);

        var content = '<div class="config-field">' +
            '<div class="config-field-label">Name</div>' +
            '<div class="config-field-value">' + escapeHtml(info.name) + '</div>' +
            '</div>' +
            '<div class="config-field">' +
            '<div class="config-field-label">Type</div>' +
            '<div class="config-field-value">Skill</div>' +
            '</div>' +
            '<div class="config-field">' +
            '<div class="config-field-label">Description</div>' +
            '<div class="config-field-value">' + escapeHtml(info.description) + '</div>' +
            '</div>';

        if (info.tools && info.tools.length > 0) {
            content += '<hr class="config-divider">' +
                '<div class="config-field">' +
                '<div class="config-field-label">Available Tools</div>' +
                '<div class="config-field-value tags">';
            info.tools.forEach(function(t) {
                content += '<span class="config-tag tool" onclick="showToolInfo(\'' + String(t.name).replace(/'/g, "\\'").replace(/"/g, '\\"') + '\')">' + escapeHtml(t.name) + '</span>';
            });
            content += '</div></div>';
        }

        showInfoModal('Skill Details', content);
    } catch (err) {
        showInfoModal('Error', 'Failed to load skill info: ' + err.message);
    }
}

export async function showToolInfo(toolName) {
    try {
        var info = await fetchToolInfo(toolName);

        var content = '<div class="config-field">' +
            '<div class="config-field-label">Name</div>' +
            '<div class="config-field-value">' + escapeHtml(info.name) + '</div>' +
            '</div>' +
            '<div class="config-field">' +
            '<div class="config-field-label">Type</div>' +
            '<div class="config-field-value">Tool</div>' +
            '</div>' +
            '<div class="config-field">' +
            '<div class="config-field-label">Description</div>' +
            '<div class="config-field-value">' + escapeHtml(info.description) + '</div>' +
            '</div>';

        if (info.parameters && info.parameters.length > 0) {
            content += '<hr class="config-divider">' +
                '<div class="config-field">' +
                '<div class="config-field-label">Parameters</div>' +
                '</div>';
            info.parameters.forEach(function(p) {
                content += '<div class="config-field" style="margin-left: 12px;">' +
                    '<div class="config-field-label">' + escapeHtml(p.name) + ' <span style="color: var(--neon-blue);">(' + escapeHtml(p.type) + ')</span></div>' +
                    '<div class="config-field-value">' + escapeHtml(p.description) + '</div>' +
                    '</div>';
            });
        }

        showInfoModal('Tool Details', content);
    } catch (err) {
        showInfoModal('Error', 'Failed to load tool info: ' + err.message);
    }
}

function showInfoModal(title, contentHtml) {
    var overlay = document.createElement('div');
    overlay.className = 'config-modal-overlay';
    overlay.id = 'infoModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) closeInfoModal();
    };

    overlay.innerHTML =
        '<div class="config-modal">' +
            '<div class="config-modal-header">' +
                '<div class="config-modal-title">' + escapeHtml(title) + '</div>' +
                '<button class="config-modal-close" onclick="closeInfoModal()">&#x2715;</button>' +
            '</div>' +
            '<div class="config-modal-body">' + contentHtml + '</div>' +
        '</div>';

    document.body.appendChild(overlay);
}

// Global function for onclick
window.closeInfoModal = function() {
    var modal = document.getElementById('infoModal');
    if (modal) modal.remove();
};

// Helper functions for selectAgent
function removeFile() {
    window.uploadedFile = null;
    showImagePreviews();
}

function showImagePreviews() {
    var area = document.getElementById('fileTagArea');
    var html = '';

    // Show document file if any
    if (window.uploadedFile) {
        html += '<div class="file-tag">' +
            '<span class="file-tag-name">' + escapeHtml(window.uploadedFile.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeFile()">×</span>' +
            '</div>';
    }

    // Show images
    window.uploadedImages.forEach(function(img, index) {
        html += '<div class="file-tag image-tag">' +
            '<span class="file-tag-name image-preview" onclick="showImageModal(' + index + ')">' + escapeHtml(img.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeImage(' + index + ')">×</span>' +
            '</div>';
    });

    // Show audio if any
    if (window.uploadedAudio) {
        html += '<div class="file-tag audio-tag">' +
            '<span class="file-tag-name">' + escapeHtml(window.uploadedAudio.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeAudio()">×</span>' +
            '</div>';
    }

    area.innerHTML = html;
}

function setStreamingState(streaming) {
    window.isStreaming = streaming;
    document.getElementById('messageInput').disabled = streaming;
    document.getElementById('sendBtn').disabled = streaming;
    if (!streaming) {
        document.getElementById('messageInput').focus();
    }
}

// Import createNewSession from session module (circular dependency handled by dynamic import)
async function createNewSession() {
    if (!window.currentAgent) return;
    try {
        var { createSession } = await import('./session.js');
        var data = await createSession(window.currentAgent);
        if (data.sessionId) {
            window.currentSessionId = data.sessionId;
            clearChatArea();
            loadSessions();
        }
    } catch (err) {
        console.error('Failed to create session', err);
    }
}

async function loadSessions() {
    var { loadSessions } = await import('./session.js');
    await loadSessions();
}

function clearChatArea() {
    document.getElementById('chatMessages').innerHTML = '';
    window.messageCount = 0;
    window.agentRawMarkdown = '';
    window.currentThinkingBox = null;
    window.currentAgentMessageWrapper = null;
    window.thinkingContent = '';
    window.currentFileInfo = null;
    document.getElementById('chatMessages').appendChild(document.getElementById('chatEmpty'));
    document.getElementById('chatEmpty').style.display = 'flex';
    removeFile();
    document.getElementById('debugRounds').innerHTML = '';
    window.rounds = [];
    window.currentRound = null;
    window.roundNumber = 0;
}

// Global functions for onclick
window.removeImage = function(index) {
    window.uploadedImages.splice(index, 1);
    showImagePreviews();
};

window.removeAudio = function() {
    window.uploadedAudio = null;
    showImagePreviews();
};

window.showImageModal = function(index) {
    var img = window.uploadedImages[index];
    if (!img) return;

    var overlay = document.createElement('div');
    overlay.className = 'image-modal-overlay';
    overlay.id = 'imageModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) window.closeImageModal();
    };

    overlay.innerHTML =
        '<div class="image-modal-content">' +
            '<button class="image-modal-close" onclick="window.closeImageModal()">×</button>' +
            '<img src="/chat/download?fileId=' + img.fileId + '" alt="' + escapeHtml(img.fileName) + '">' +
            '<div class="image-modal-filename">' + escapeHtml(img.fileName) + '</div>' +
        '</div>';

    document.body.appendChild(overlay);
};

window.closeImageModal = function() {
    var modal = document.getElementById('imageModal');
    if (modal) {
        modal.remove();
    }
};
```

Expected: File created at `src/main/resources/static/scripts/modules/agents.js`

- [ ] **Step 2: Remove agent functions from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 229-268 (LOAD AGENTS), lines 270-477 (CONFIG VIEWER), and lines 492-553 (SELECT AGENT).

Expected: chat.js no longer contains agent management functions

- [ ] **Step 3: Add agents import to chat.js**

Add after the debug import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { loadAgents, selectAgent, showAgentConfig, showSkillInfo, showToolInfo } from './modules/agents.js';
```

Expected: chat.js now imports agent functions

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, test agent loading, selection, config viewer

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/scripts/modules/agents.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract agent management to modules/agents.js"
```

---

## Task 8: Extract Session Module (modules/session.js)

**Files:**
- Create: `src/main/resources/static/scripts/modules/session.js`
- Modify: `src/main/resources/static/scripts/chat.js:1563-1657`

- [ ] **Step 1: Create modules/session.js with session management functions**

```javascript
import { fetchSessions, createSession, deleteSession as deleteSessionApi } from './api.js';
import { escapeHtml } from './utils.js';

/* ===== SESSION MANAGEMENT ===== */
export async function loadSessions() {
    try {
        var sessions = await fetchSessions();
        var listEl = document.getElementById('sessionList');
        listEl.innerHTML = '';

        sessions.forEach(function(s) {
            var item = document.createElement('div');
            item.className = 'session-item' + (s.sessionId === window.currentSessionId ? ' active' : '');
            item.dataset.sessionId = s.sessionId;
            item.onclick = function() { selectSession(s.sessionId, s.agentId); };

            var preview = s.agentName || s.agentId || 'Unknown';
            if (s.messageCount > 0) {
                preview += ' <span class="session-msg-count">(' + s.messageCount + ')</span>';
            }

            item.innerHTML =
                '<div class="session-item-info">' +
                    '<div class="session-item-name">' + preview + '</div>' +
                    '<div class="session-item-time">' + (s.lastAccessedAt || '') + '</div>' +
                '</div>' +
                '<button class="session-item-delete" onclick="event.stopPropagation(); deleteSession(\'' + s.sessionId + '\')">×</button>';

            listEl.appendChild(item);
        });
    } catch (err) {
        console.error('Failed to load sessions', err);
    }
}

export async function createNewSession(agentId) {
    if (!agentId) return;
    try {
        var data = await createSession(agentId);
        if (data.sessionId) {
            window.currentSessionId = data.sessionId;
            clearChatArea();
            loadSessions();
        }
    } catch (err) {
        console.error('Failed to create session', err);
    }
}

export async function selectSession(sessionId, agentId) {
    if (window.isStreaming) return;

    // Switch agent if needed
    var { selectAgent } = await import('./agents.js');
    if (agentId && agentId !== window.currentAgent) {
        selectAgent(agentId);
    }

    window.currentSessionId = sessionId;
    clearChatArea();
    loadSessions();
}

export async function deleteSession(sessionId) {
    if (window.isStreaming) return;
    try {
        await deleteSessionApi(sessionId);
        if (window.currentSessionId === sessionId) {
            window.currentSessionId = null;
            clearChatArea();
        }
        loadSessions();
    } catch (err) {
        console.error('Failed to delete session', err);
    }
}

export function clearSession() {
    if (window.isStreaming) return;
    if (window.currentSessionId) {
        deleteSession(window.currentSessionId);
    } else {
        clearChatArea();
    }
}

function clearChatArea() {
    document.getElementById('chatMessages').innerHTML = '';
    window.messageCount = 0;
    window.agentRawMarkdown = '';
    window.currentThinkingBox = null;
    window.currentAgentMessageWrapper = null;
    window.thinkingContent = '';
    window.currentFileInfo = null;
    document.getElementById('chatMessages').appendChild(document.getElementById('chatEmpty'));
    document.getElementById('chatEmpty').style.display = 'flex';
    
    // Clear file state
    window.uploadedFile = null;
    document.getElementById('fileTagArea').innerHTML = '';
    
    document.getElementById('debugRounds').innerHTML = '';
    window.rounds = [];
    window.currentRound = null;
    window.roundNumber = 0;
}

// Global function for onclick
window.deleteSession = deleteSession;
```

Expected: File created at `src/main/resources/static/scripts/modules/session.js`

- [ ] **Step 2: Remove session functions from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 1563-1657 (SESSION MANAGEMENT section).

Expected: chat.js no longer contains session management functions

- [ ] **Step 3: Add session import to chat.js**

Add after the agents import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { loadSessions, createNewSession, selectSession, clearSession } from './modules/session.js';
```

Expected: chat.js now imports session functions

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, test session creation, switching, deletion

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/scripts/modules/session.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract session management to modules/session.js"
```

---

## Task 9: Extract Knowledge Module (modules/knowledge.js)

**Files:**
- Create: `src/main/resources/static/scripts/modules/knowledge.js`
- Modify: `src/main/resources/static/scripts/chat.js:1659-1717`

- [ ] **Step 1: Create modules/knowledge.js with knowledge management functions**

```javascript
import { fetchKnowledgeDocs, uploadKnowledgeDoc, removeKnowledgeDoc as removeKnowledgeDocApi } from './api.js';
import { escapeHtml } from './utils.js';

/* ===== KNOWLEDGE MANAGEMENT ===== */
export async function loadKnowledgeDocs() {
    try {
        var docs = await fetchKnowledgeDocs();
        var docsEl = document.getElementById('knowledgeDocs');
        docsEl.innerHTML = '';

        if (docs.length === 0) {
            docsEl.innerHTML = '<div class="knowledge-empty">No documents</div>';
            return;
        }

        docs.forEach(function(name) {
            var item = document.createElement('div');
            item.className = 'knowledge-doc-item';
            item.innerHTML =
                '<span class="knowledge-doc-name">' + escapeHtml(name) + '</span>' +
                '<button class="session-item-delete" onclick="removeKnowledgeDoc(\'' + escapeHtml(name) + '\')">×</button>';
            docsEl.appendChild(item);
        });
    } catch (err) {
        console.error('Failed to load knowledge docs', err);
    }
}

export async function uploadToKnowledge(input) {
    var file = input.files[0];
    if (!file) return;

    try {
        var data = await uploadKnowledgeDoc(file);
        if (data.error) {
            console.error('Knowledge upload error:', data.error);
            return;
        }
        loadKnowledgeDocs();
    } catch (err) {
        console.error('Knowledge upload failed', err);
    }

    input.value = '';
}

export async function removeKnowledgeDoc(fileName) {
    try {
        await removeKnowledgeDocApi(fileName);
        loadKnowledgeDocs();
    } catch (err) {
        console.error('Failed to remove knowledge doc', err);
    }
}

// Global function for onclick
window.removeKnowledgeDoc = removeKnowledgeDoc;
```

Expected: File created at `src/main/resources/static/scripts/modules/knowledge.js`

- [ ] **Step 2: Remove knowledge functions from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 1659-1717 (KNOWLEDGE MANAGEMENT section).

Expected: chat.js no longer contains knowledge management functions

- [ ] **Step 3: Add knowledge import to chat.js**

Add after the session import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { loadKnowledgeDocs, uploadToKnowledge } from './modules/knowledge.js';
```

Expected: chat.js now imports knowledge functions

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, test knowledge document upload, removal

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/scripts/modules/knowledge.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract knowledge management to modules/knowledge.js"
```

---

## Task 10: Extract File Upload Module (modules/upload.js)

**Files:**
- Create: `src/main/resources/static/scripts/modules/upload.js`
- Modify: `src/main/resources/static/scripts/chat.js:1369-1524`

- [ ] **Step 1: Create modules/upload.js with file upload functions**

```javascript
import { uploadFile } from './api.js';
import { escapeHtml } from './utils.js';

/* ===== FILE UPLOAD HANDLING ===== */
export async function handleFileSelect(input) {
    var file = input.files[0];
    if (!file) return;

    var lowerName = file.name.toLowerCase();

    // Check file type
    var isDoc = lowerName.endsWith('.docx') || lowerName.endsWith('.pdf') || lowerName.endsWith('.xlsx');
    var isImage = lowerName.endsWith('.jpg') || lowerName.endsWith('.jpeg') ||
                  lowerName.endsWith('.png') || lowerName.endsWith('.gif') ||
                  lowerName.endsWith('.webp');
    var isAudio = lowerName.endsWith('.wav') || lowerName.endsWith('.mp3') ||
                  lowerName.endsWith('.m4a') || lowerName.endsWith('.mp4');

    if (!isDoc && !isImage && !isAudio) {
        input.value = '';
        return;
    }

    try {
        var data = await uploadFile(file);
        if (data.error) {
            console.error('Upload error:', data.error);
            return;
        }

        // Handle based on file type
        if (isDoc) {
            if (window.currentAgent !== 'task-document-analysis') {
                var { selectAgent } = await import('./agents.js');
                selectAgent('task-document-analysis');
            }
            window.uploadedFile = data;
            showFileTag(data.fileName);
        } else if (isImage) {
            window.uploadedImages.push(data);
            showImagePreviews();
            // Auto-switch to vision agent if available
            if (window.agents['vision-analyzer']) {
                var { selectAgent } = await import('./agents.js');
                selectAgent('vision-analyzer');
            }
        } else if (isAudio) {
            window.uploadedAudio = data;
            showAudioPreview();
            // Auto-switch to voice agent if available
            if (window.agents['voice-assistant']) {
                var { selectAgent } = await import('./agents.js');
                selectAgent('voice-assistant');
            }
        }
    } catch (err) {
        console.error('Upload error:', err);
    }

    input.value = '';
}

function showFileTag(fileName) {
    var area = document.getElementById('fileTagArea');
    area.innerHTML = '<div class="file-tag">' +
        '<span class="file-tag-name">' + escapeHtml(fileName) + '</span>' +
        '<span class="file-tag-remove" onclick="removeFile()">×</span>' +
        '</div>';
}

function showImagePreviews() {
    var area = document.getElementById('fileTagArea');
    var html = '';

    // Show document file if any
    if (window.uploadedFile) {
        html += '<div class="file-tag">' +
            '<span class="file-tag-name">' + escapeHtml(window.uploadedFile.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeFile()">×</span>' +
            '</div>';
    }

    // Show images
    window.uploadedImages.forEach(function(img, index) {
        html += '<div class="file-tag image-tag">' +
            '<span class="file-tag-name image-preview" onclick="showImageModal(' + index + ')">' + escapeHtml(img.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeImage(' + index + ')">×</span>' +
            '</div>';
    });

    // Show audio if any
    if (window.uploadedAudio) {
        html += '<div class="file-tag audio-tag">' +
            '<span class="file-tag-name">' + escapeHtml(window.uploadedAudio.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeAudio()">×</span>' +
            '</div>';
    }

    area.innerHTML = html;
}

function showAudioPreview() {
    showImagePreviews(); // Reuse the same function
}

export function removeFile() {
    window.uploadedFile = null;
    showImagePreviews();
}

export function removeImage(index) {
    window.uploadedImages.splice(index, 1);
    showImagePreviews();
}

export function removeAudio() {
    window.uploadedAudio = null;
    showImagePreviews();
}

export function clearAllMedia() {
    window.uploadedFile = null;
    window.uploadedImages = [];
    window.uploadedAudio = null;
    document.getElementById('fileTagArea').innerHTML = '';
}

// Global functions for onclick
window.removeFile = removeFile;
window.removeImage = removeImage;
window.removeAudio = removeAudio;

async function showImageModal(index) {
    var img = window.uploadedImages[index];
    if (!img) return;

    var overlay = document.createElement('div');
    overlay.className = 'image-modal-overlay';
    overlay.id = 'imageModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) closeImageModal();
    };

    overlay.innerHTML =
        '<div class="image-modal-content">' +
            '<button class="image-modal-close" onclick="closeImageModal()">×</button>' +
            '<img src="/chat/download?fileId=' + img.fileId + '" alt="' + escapeHtml(img.fileName) + '">' +
            '<div class="image-modal-filename">' + escapeHtml(img.fileName) + '</div>' +
        '</div>';

    document.body.appendChild(overlay);
}

function closeImageModal() {
    var modal = document.getElementById('imageModal');
    if (modal) {
        modal.remove();
    }
}

window.showImageModal = showImageModal;
window.closeImageModal = closeImageModal;
```

Expected: File created at `src/main/resources/static/scripts/modules/upload.js`

- [ ] **Step 2: Remove file upload functions from chat.js**

Edit `src/main/resources/static/scripts/chat.js`: Remove lines 1369-1524 (FILE UPLOAD section).

Expected: chat.js no longer contains file upload functions

- [ ] **Step 3: Add upload import to chat.js**

Add after the knowledge import in `src/main/resources/static/scripts/chat.js`:
```javascript
import { handleFileSelect, clearAllMedia } from './modules/upload.js';
```

Expected: chat.js now imports upload functions

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, test file upload (document, image, audio)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/scripts/modules/upload.js src/main/resources/static/scripts/chat.js
git commit -m "refactor: extract file upload to modules/upload.js"
```

---

## Task 11: Create Refactored chat.js Entry Point

**Files:**
- Modify: `src/main/resources/static/scripts/chat.js`

- [ ] **Step 1: Replace chat.js content with new entry point**

Replace entire content of `src/main/resources/static/scripts/chat.js` with:

```javascript
/* ===== MODULE IMPORTS ===== */
import './state.js';
import { createSSEParser } from './api.js';
import { renderMarkdown, escapeHtml, getTimestamp, formatDuration, scrollToBottom } from './modules/utils.js';
import { chatMessages, messageInput, sendBtn, chatEmpty, appendMessage, createThinkingBox, updateThinkingBox, collapseThinkingBox, completeThinkingBox, addAgentBubble, removeTypingIndicator, setStreamingState, showTypingIndicator } from './modules/ui.js';
import { startRound, endRound, addTimelineRow, clearDebug, toggleDebug, handlePipelineStart, handlePipelineStepStart, handlePipelineStepEnd, handleRoutingDecision, handleHandoffStart } from './modules/debug.js';
import { loadAgents, selectAgent, showAgentConfig, showSkillInfo, showToolInfo } from './modules/agents.js';
import { loadSessions, createNewSession, selectSession, clearSession } from './modules/session.js';
import { loadKnowledgeDocs, uploadToKnowledge } from './modules/knowledge.js';
import { handleFileSelect, clearAllMedia } from './modules/upload.js';

/* ===== INPUT HANDLING ===== */
messageInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

messageInput.addEventListener('input', function() {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 120) + 'px';
});

/* ===== SEND MESSAGE ===== */
async function sendMessage() {
    var input = messageInput;
    var message = input.value.trim();
    if ((!message && !window.uploadedFile && window.uploadedImages.length === 0 && !window.uploadedAudio) || window.isStreaming) return;

    chatEmpty.style.display = 'none';

    document.querySelectorAll('.round-body:not(.collapsed)').forEach(function(body) {
        body.classList.add('collapsed');
    });

    // Build user message with media info
    var mediaInfo = {
        file: window.uploadedFile,
        images: window.uploadedImages.length > 0 ? window.uploadedImages : null,
        audio: window.uploadedAudio
    };
    appendMessage('user', message, mediaInfo);
    window.messageCount++;

    input.value = '';
    input.style.height = 'auto';

    var fileInfo = window.uploadedFile;
    var imagesCopy = window.uploadedImages.slice();
    var audioCopy = window.uploadedAudio;
    clearAllMedia();
    setStreamingState(true);

    // Ensure clean state before starting new round
    if (window.currentRound) {
        console.warn('[sendMessage] currentRound already exists, cleaning up:', window.currentRound);
        window.currentRound = null;
    }
    window.roundNumber++;
    startRound(message, window.roundNumber, window.currentAgent, window.agents);

    showTypingIndicator();

    window.currentThinkingBox = null;
    window.currentAgentMessageWrapper = null;
    window.thinkingContent = '';
    window.currentFileInfo = null;
    window.currentFileInfo = fileInfo;

    var agentBubble = null;

    if (fileInfo) {
        createThinkingBox(fileInfo);
    }

    try {
        window.currentAbortController = new AbortController();

        // Build request payload with multi-modal support
        var payload = {
            agentId: window.currentAgent,
            message: message,
            filePath: fileInfo ? fileInfo.filePath : null,
            fileName: fileInfo ? fileInfo.fileName : null,
            sessionId: window.currentSessionId || null
        };

        // Add images if any
        if (imagesCopy.length > 0) {
            payload.images = imagesCopy.map(function(img) {
                return { path: img.filePath, fileName: img.fileName };
            });
        }

        // Add audio if any
        if (audioCopy) {
            payload.audio = { path: audioCopy.filePath, fileName: audioCopy.fileName };
        }

        var response = await fetch('/chat/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            signal: window.currentAbortController.signal
        });

        if (!response.ok) {
            removeTypingIndicator();
            appendMessage('error', '请求失败: ' + response.status);
            endRound('error');
            setStreamingState(false);
            return;
        }

        removeTypingIndicator();
        window.messageCount++;

        var reader = response.body.getReader();
        var decoder = new TextDecoder();
        var parser = createSSEParser();

        while (true) {
            var result = await reader.read();
            if (result.done) break;

            parser.parse(decoder.decode(result.value, { stream: true }), function(event) {
                if (event.event === 'message' && event.data) {
                    var payload;
                    try {
                        payload = JSON.parse(event.data);
                    } catch (e) {
                        console.error('[SSE] Failed to parse payload:', event.data, e);
                        return;
                    }

                    // Log all payload types for debugging
                    if (payload.type === 'tool_start' || payload.type === 'tool_end') {
                        console.log('[SSE] Received', payload.type, 'payload:', payload);
                    }

                    switch (payload.type) {

                        // ===== HOOK LIFECYCLE EVENTS (from ObservabilityHook) =====

                        case 'agent_start':
                            if (window.currentRound) {
                                addTimelineRow('phase', 'Agent Start', payload.agentName || '', 'running');
                            }
                            break;

                        case 'agent_end':
                            if (window.currentRound) {
                                var totalDur = payload.duration_ms || 0;
                                addTimelineRow('phase', 'Agent End', formatDuration(totalDur), 'ok');
                                window.currentRound.totalLlmCalls = payload.totalLlmCalls || 0;
                                window.currentRound.totalToolCalls = payload.totalToolCalls || 0;
                            }
                            break;

                        case 'llm_start':
                            if (window.currentRound) {
                                var callNum = payload.callNumber || (window.currentRound.llmCallCount + 1);
                                window.currentRound._currentLlmStart = Date.now();
                                window.currentRound._currentLlmCallNum = callNum;
                                window.currentRound._currentLlmRow = addTimelineRow('llm', 'LLM #' + callNum, (payload.modelName || '') + ' ...', 'running');
                            }
                            break;

                        case 'llm_end':
                            if (window.currentRound) {
                                window.currentRound.llmCallCount++;
                                var tokens = payload.totalTokens ? payload.totalTokens.toLocaleString() + ' tokens' : '';
                                var llmTimeSec = payload.llmTime ? payload.llmTime : 0;
                                var llmDurMs = 0;
                                if (window.currentRound._currentLlmStart) {
                                    llmDurMs = Date.now() - window.currentRound._currentLlmStart;
                                }
                                var timeStr = llmDurMs > 0 ? formatDuration(llmDurMs) : (llmTimeSec > 0 ? (llmTimeSec >= 1 ? llmTimeSec.toFixed(1) + 's' : (llmTimeSec * 1000).toFixed(0) + 'ms') : '');

                                window.currentRound.inputTokens += (payload.inputTokens || 0);
                                window.currentRound.outputTokens += (payload.outputTokens || 0);
                                window.currentRound.totalTokens += (payload.totalTokens || 0);
                                window.currentRound.llmTime += llmTimeSec;

                                if (window.currentRound._currentLlmRow) {
                                    var row = window.currentRound._currentLlmRow;
                                    var metricsEl = row.querySelector('.rtl-metrics');
                                    var statusEl = row.querySelector('.rtl-status');
                                    if (metricsEl) metricsEl.textContent = [tokens, timeStr].filter(Boolean).join(' ');
                                    if (statusEl) statusEl.textContent = '✓';
                                    row.classList.remove('status-running');
                                    row.classList.add('status-ok');
                                    window.currentRound._currentLlmRow = null;
                                }

                                var { updateRoundMetrics } = await import('./modules/debug.js');
                                updateRoundMetrics();
                                window.currentRound._currentLlmStart = null;
                            }
                            break;

                        case 'thinking':
                            if (window.currentRound && !window.currentRound.thinkingCycleStart) {
                                window.currentRound.thinkingCycleStart = Date.now();
                            }
                            var thinkingText = payload.content || 'Processing...';
                            updateThinkingBox(thinkingText, fileInfo);
                            break;

                        case 'reasoning_text':
                            var rText = payload.content || '';
                            if (rText) {
                                updateThinkingBox(rText, fileInfo);
                            }
                            break;

                        case 'tool_start':
                            if (window.currentRound && window.currentRound.thinkingCycleStart) {
                                window.currentRound.thinkingTime += Date.now() - window.currentRound.thinkingCycleStart;
                                window.currentRound.thinkingCycleStart = null;
                            }
                            var tName = payload.name || 'unknown';
                            var tParams = payload.params || '{}';
                            var tParamsPreview = payload.paramsPreview || tParams.substring(0, 50);
                            var isSkill = payload.isSkill === true;
                            var tSkillName = payload.displayName || '';

                            if (isSkill) {
                                updateThinkingBox('📖 Loading skill: ' + (tSkillName || '...'), fileInfo);
                            } else {
                                updateThinkingBox('⚡ ' + tName + '(' + tParamsPreview + ')', fileInfo);
                            }

                            if (window.currentRound) {
                                window.currentRound.toolCallCount++;
                                window.currentRound._currentToolStart = Date.now();
                                var rowType = isSkill ? 'skill' : 'tool';
                                var rowLabel = isSkill ? ('Skill → ' + (tSkillName || '')) : ('Tool → ' + tName);
                                window.currentRound._currentToolRow = addTimelineRow(rowType, rowLabel, '...', 'running');
                                window.currentRound._currentToolIsSkill = isSkill;
                                var { updateRoundMetrics } = await import('./modules/debug.js');
                                updateRoundMetrics();
                            } else {
                                console.warn('[tool_start] No currentRound available!', payload);
                            }
                            break;

                        case 'tool_end':
                            var teName = payload.name || 'unknown';
                            var teResult = payload.result || '';
                            var teDurMs = payload.duration_ms || -1;
                            var tePreview = payload.resultPreview || (teResult.length > 100 ? teResult.substring(0, 100) + '...' : teResult);
                            var teIsSkill = payload.isSkill === true;
                            updateThinkingBox('✓ ' + teName + ': ' + tePreview, fileInfo);

                            var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
                            if (targetRound) {
                                if (teDurMs > 0) {
                                    targetRound.toolTime += teDurMs;
                                } else if (targetRound._currentToolStart) {
                                    targetRound.toolTime += Date.now() - targetRound._currentToolStart;
                                }
                                targetRound._currentToolStart = null;

                                if (targetRound._currentToolRow) {
                                    var tRow = targetRound._currentToolRow;
                                    var tmEl = tRow.querySelector('.rtl-metrics');
                                    var tsEl = tRow.querySelector('.rtl-status');
                                    var durStr = teDurMs >= 0 ? formatDuration(teDurMs) : '';
                                    if (tmEl) tmEl.textContent = durStr;
                                    if (tsEl) tsEl.textContent = teDurMs >= 0 ? '✓' : '✗';
                                    tRow.classList.remove('status-running');
                                    tRow.classList.add(teDurMs >= 0 ? 'status-ok' : 'status-fail');
                                    targetRound._currentToolRow = null;
                                } else {
                                    console.warn('[tool_end] No _currentToolRow for round #' + targetRound.number, 'payload:', payload);
                                }

                                var { updateRoundMetricsForRound } = await import('./modules/debug.js');
                                var metricsEl2 = document.getElementById('round-metrics-' + targetRound.number);
                                if (metricsEl2) updateRoundMetricsForRound(targetRound);
                            } else {
                                console.warn('[tool_end] No targetRound available!', 'payload:', payload);
                            }
                            break;

                        // ===== STREAM CONTENT EVENTS =====

                        case 'text':
                            if (window.currentRound && window.currentRound.thinkingCycleStart) {
                                window.currentRound.thinkingTime += Date.now() - window.currentRound.thinkingCycleStart;
                                window.currentRound.thinkingCycleStart = null;
                            }
                            collapseThinkingBox();
                            if (!agentBubble) {
                                agentBubble = addAgentBubble();
                                window.agentRawMarkdown = '';
                            }
                            window.agentRawMarkdown += (payload.content || payload.text || '');
                            agentBubble.classList.add('md-render');
                            agentBubble.innerHTML = renderMarkdown(window.agentRawMarkdown);
                            scrollToBottom(chatMessages);
                            break;

                        case 'done':
                            console.log('[SSE] Received done event, currentRound:', window.currentRound ? '#' + window.currentRound.number : 'null');
                            completeThinkingBox();
                            window.isStreaming = false;
                            setStreamingState(false);
                            endRound('success');
                            scrollToBottom(chatMessages);
                            break;

                        case 'error':
                            completeThinkingBox();
                            if (!agentBubble) {
                                agentBubble = addAgentBubble();
                            }
                            agentBubble.classList.remove('md-render');
                            agentBubble.style.whiteSpace = 'pre-wrap';
                            agentBubble.textContent += '\n\n[ERROR] ' + (payload.message || payload.content || 'Unknown error');
                            agentBubble.closest('.message').classList.add('error');
                            endRound('error');
                            window.isStreaming = false;
                            setStreamingState(false);
                            scrollToBottom(chatMessages);
                            break;

                        // ===== MULTI-AGENT EVENTS =====

                        case 'pipeline_start':
                            handlePipelineStart(payload);
                            break;
                        case 'pipeline_step_start':
                            handlePipelineStepStart(payload);
                            break;
                        case 'pipeline_step_end':
                            handlePipelineStepEnd(payload);
                            break;
                        case 'routing_decision':
                            handleRoutingDecision(payload);
                            break;
                        case 'handoff_start':
                            handleHandoffStart(payload);
                            break;
                    }
                }
            });
        }

    } catch (err) {
        if (err.name === 'AbortError') {
            completeThinkingBox();
            window.isStreaming = false;
            setStreamingState(false);
            endRound('error');
            return;
        }
        completeThinkingBox();
        removeTypingIndicator();
        appendMessage('error', '[NETWORK ERROR] ' + err.message);
        endRound('error');
        setStreamingState(false);
    } finally {
        window.currentAbortController = null;
    }
}

function stopStreaming() {
    if (window.currentAbortController) {
        window.currentAbortController.abort();
        window.currentAbortController = null;
    }
    window.isStreaming = false;
    setStreamingState(false);
}

/* ===== GLOBAL FUNCTIONS ===== */
window.sendMessage = sendMessage;
window.toggleDebug = toggleDebug;
window.clearDebug = clearDebug;
window.clearSession = clearSession;
window.createNewSession = function() { createNewSession(window.currentAgent); };
window.uploadToKnowledge = uploadToKnowledge;
window.handleFileSelect = handleFileSelect;

/* ===== INIT ===== */
loadAgents();
loadSessions();
loadKnowledgeDocs();
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        var modal = document.getElementById('configModal');
        if (modal) modal.remove();
        modal = document.getElementById('infoModal');
        if (modal) modal.remove();
    }
});
messageInput.focus();
```

Expected: chat.js is now a clean entry point that imports all modules

- [ ] **Step 2: Test in browser**

Open http://localhost:8080 and test all functionality:
- Agent selection
- Message sending
- Debug panel
- File upload
- Session management
- Knowledge upload

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/scripts/chat.js
git commit -m "refactor: create new chat.js entry point with ES6 module imports"
```

---

## Task 12: Extract Base CSS (base.css)

**Files:**
- Create: `src/main/resources/static/styles/base.css`
- Modify: `src/main/resources/static/styles/chat.css:1-108`

- [ ] **Step 1: Create base.css with base styles**

Extract lines 1-108 from chat.css to `src/main/resources/static/styles/base.css`:
- CYBERPUNK TERMINAL UI (CSS variables, reset)
- SCANLINE OVERLAY
- GRID BACKGROUND
- APP SHELL
- BODY LAYOUT

Expected: File created at `src/main/resources/static/styles/base.css`

- [ ] **Step 2: Remove base styles from chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove lines 1-188 (up to and including BODY LAYOUT section).

Expected: chat.css no longer contains base styles

- [ ] **Step 3: Add base import to chat.css at the top**

Add at the very top of `src/main/resources/static/styles/chat.css`:
```css
/* Import base styles */
@import url('base.css');
```

Expected: chat.css now imports base.css

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify basic layout and styles work

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/styles/base.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract base styles to base.css"
```

---

## Task 13: Extract Header CSS (modules/header.css)

**Files:**
- Create: `src/main/resources/static/styles/modules/header.css`
- Modify: `src/main/resources/static/styles/chat.css:110-186`

- [ ] **Step 1: Create modules/header.css with header styles**

Extract lines 110-186 from chat.css (HEADER section) to `src/main/resources/static/styles/modules/header.css`.

Expected: File created at `src/main/resources/static/styles/modules/header.css`

- [ ] **Step 2: Remove header styles from chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove lines 110-186 (HEADER section).

Expected: chat.css no longer contains header styles

- [ ] **Step 3: Add header import to chat.css**

Add after the base import in `src/main/resources/static/styles/chat.css`:
```css
@import url('modules/header.css');
```

Expected: chat.css now imports header.css

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify header styling works

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/styles/modules/header.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract header styles to modules/header.css"
```

---

## Task 14: Extract Sidebar CSS (modules/sidebar.css)

**Files:**
- Create: `src/main/resources/static/styles/modules/sidebar.css`
- Modify: `src/main/resources/static/styles/chat.css:195-365, 1919-2028`

- [ ] **Step 1: Create modules/sidebar.css with sidebar styles**

Extract from chat.css to `src/main/resources/static/styles/modules/sidebar.css`:
- LEFT SIDEBAR section (lines 195-365)
- SESSION LIST section (lines 1919-2028)

Expected: File created at `src/main/resources/static/styles/modules/sidebar.css`

- [ ] **Step 2: Remove sidebar styles from chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove LEFT SIDEBAR and SESSION LIST sections.

Expected: chat.css no longer contains sidebar styles

- [ ] **Step 3: Add sidebar import to chat.css**

Add after the header import in `src/main/resources/static/styles/chat.css`:
```css
@import url('modules/sidebar.css');
```

Expected: chat.css now imports sidebar.css

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify sidebar and session list styling work

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/styles/modules/sidebar.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract sidebar styles to modules/sidebar.css"
```

---

## Task 15: Extract Chat CSS (modules/chat.css)

**Files:**
- Create: `src/main/resources/static/styles/modules/chat.css`
- Modify: `src/main/resources/static/styles/chat.css:367-473, 475-508, 560-680, 750-958`

- [ ] **Step 1: Create modules/chat.css with chat area styles**

Extract from chat.css to `src/main/resources/static/styles/modules/chat.css`:
- CHAT AREA section (lines 367-473)
- MESSAGE AVATARS section (lines 475-508)
- MARKDOWN STYLES section (lines 560-680)
- THINKING BOX section (lines 750-877)
- Empty state, typing indicator, input area (lines 922-958)

Expected: File created at `src/main/resources/static/styles/modules/chat.css`

- [ ] **Step 2: Remove chat area styles from root chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove CHAT AREA, MESSAGE AVATARS, MARKDOWN STYLES, THINKING BOX, and related sections.

Expected: Root chat.css no longer contains chat area styles

- [ ] **Step 3: Add chat module import to root chat.css**

Add after the sidebar import in `src/main/resources/static/styles/chat.css`:
```css
@import url('modules/chat.css');
```

Expected: Root chat.css now imports modules/chat.css

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify chat area styling works

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/styles/modules/chat.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract chat area styles to modules/chat.css"
```

---

## Task 16: Extract Debug CSS (modules/debug.css)

**Files:**
- Create: `src/main/resources/static/styles/modules/debug.css`
- Modify: `src/main/resources/static/styles/chat.css:1064-1530, 2325-2452`

- [ ] **Step 1: Create modules/debug.css with debug panel styles**

Extract from chat.css to `src/main/resources/static/styles/modules/debug.css`:
- DEBUG PANEL section (lines 1064-1157)
- ROUNDS CONTAINER section (lines 1158-1186)
- ROUND CARD section (lines 1188-1500)
- CLEAR BUTTON section (lines 1501-1530)
- MULTI-AGENT DEBUG PANEL STYLES section (lines 2325-2452)

Expected: File created at `src/main/resources/static/styles/modules/debug.css`

- [ ] **Step 2: Remove debug styles from root chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove DEBUG PANEL, ROUNDS CONTAINER, ROUND CARD, CLEAR BUTTON, and MULTI-AGENT DEBUG PANEL STYLES sections.

Expected: Root chat.css no longer contains debug panel styles

- [ ] **Step 3: Add debug import to root chat.css**

Add after the chat module import in `src/main/resources/static/styles/chat.css`:
```css
@import url('modules/debug.css');
```

Expected: Root chat.css now imports modules/debug.css

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify debug panel styling works

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/styles/modules/debug.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract debug panel styles to modules/debug.css"
```

---

## Task 17: Extract Upload CSS (modules/upload.css)

**Files:**
- Create: `src/main/resources/static/styles/modules/upload.css`
- Modify: `src/main/resources/static/styles/chat.css:1539-1650, 2071-2197`

- [ ] **Step 1: Create modules/upload.css with file upload styles**

Extract from chat.css to `src/main/resources/static/styles/modules/upload.css`:
- FILE UPLOAD section (lines 1539-1599)
- FILE LIST section (lines 1600-1650)
- MULTI-MEDIA STYLES section (lines 2071-2197)

Expected: File created at `src/main/resources/static/styles/modules/upload.css`

- [ ] **Step 2: Remove upload styles from root chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove FILE UPLOAD, FILE LIST, and MULTI-MEDIA STYLES sections.

Expected: Root chat.css no longer contains file upload styles

- [ ] **Step 3: Add upload import to root chat.css**

Add after the debug import in `src/main/resources/static/styles/chat.css`:
```css
@import url('modules/upload.css');
```

Expected: Root chat.css now imports modules/upload.css

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify file upload styling works

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/styles/modules/upload.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract file upload styles to modules/upload.css"
```

---

## Task 18: Extract Modal CSS (modules/modal.css)

**Files:**
- Create: `src/main/resources/static/styles/modules/modal.css`
- Modify: `src/main/resources/static/styles/chat.css:1696-1918`

- [ ] **Step 1: Create modules/modal.css with modal styles**

Extract lines 1696-1918 from chat.css (CONFIG VIEWER MODAL section) to `src/main/resources/static/styles/modules/modal.css`.

Expected: File created at `src/main/resources/static/styles/modules/modal.css`

- [ ] **Step 2: Remove modal styles from root chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove CONFIG VIEWER MODAL section.

Expected: Root chat.css no longer contains modal styles

- [ ] **Step 3: Add modal import to root chat.css**

Add after the upload import in `src/main/resources/static/styles/chat.css`:
```css
@import url('modules/modal.css');
```

Expected: Root chat.css now imports modules/modal.css

- [ ] **Step 4: Test in browser**

Open http://localhost:8080, verify config modal styling works

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/styles/modules/modal.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract modal styles to modules/modal.css"
```

---

## Task 19: Extract Utils CSS (modules/utils.css)

**Files:**
- Create: `src/main/resources/static/styles/modules/utils.css`
- Modify: `src/main/resources/static/styles/chat.css:1651-1681, 1682-1695, 2198-2324`

- [ ] **Step 1: Create modules/utils.css with utility styles**

Extract from chat.css to `src/main/resources/static/styles/modules/utils.css`:
- RESPONSIVE section (lines 1651-1681)
- GLITCH EFFECT section (lines 1682-1695)
- STRUCTURED DATA STYLES section (lines 2198-2324)

Expected: File created at `src/main/resources/static/styles/modules/utils.css`

- [ ] **Step 2: Remove utility styles from root chat.css**

Edit `src/main/resources/static/styles/chat.css`: Remove RESPONSIVE, GLITCH EFFECT, and STRUCTURED DATA STYLES sections.

Expected: Root chat.css no longer contains utility styles

- [ ] **Step 3: Add utils import to root chat.css**

Add after the modal import in `src/main/resources/static/styles/chat.css`:
```css
@import url('modules/utils.css');
```

Expected: Root chat.css now imports modules/utils.css

- [ ] **Step 4: Add knowledge base styles to utils.css**

Add the KNOWLEDGE BASE section (lines 2029-2070 from original chat.css) to `src/main/resources/static/styles/modules/utils.css`.

Expected: utils.css now contains knowledge base styles

- [ ] **Step 5: Test in browser**

Open http://localhost:8080, verify responsive, structured data, and knowledge base styling work

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/styles/modules/utils.css src/main/resources/static/styles/chat.css
git commit -m "refactor: extract utility styles to modules/utils.css"
```

---

## Task 20: Create Refactored chat.css Entry Point

**Files:**
- Modify: `src/main/resources/static/styles/chat.css`

- [ ] **Step 1: Replace chat.css content with new entry point**

Replace entire content of `src/main/resources/static/styles/chat.css` with:

```css
/* ===== CHAT STYLES ENTRY POINT ===== */
/* Import all module styles */

/* Base styles */
@import url('base.css');

/* Component styles */
@import url('modules/header.css');
@import url('modules/sidebar.css');
@import url('modules/chat.css');
@import url('modules/debug.css');
@import url('modules/upload.css');
@import url('modules/modal.css');
@import url('modules/utils.css');
```

Expected: chat.css is now a clean entry point that imports all CSS modules

- [ ] **Step 2: Verify all CSS modules exist**

Run: `ls -la src/main/resources/static/styles/modules/`
Expected: All CSS module files exist (header.css, sidebar.css, chat.css, debug.css, upload.css, modal.css, utils.css)

- [ ] **Step 3: Test in browser**

Open http://localhost:8080 and verify all styling works correctly

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/styles/chat.css
git commit -m "refactor: create new chat.css entry point with CSS module imports"
```

---

## Task 21: Update HTML for ES6 Modules

**Files:**
- Modify: `src/main/resources/templates/chat.html:85`

- [ ] **Step 1: Add type="module" to chat.js script tag**

Edit `src/main/resources/templates/chat.html`: Change line 85 from:
```html
<script src="/scripts/chat.js"></script>
```
To:
```html
<script type="module" src="/scripts/chat.js"></script>
```

Expected: Script tag now includes type="module" attribute

- [ ] **Step 2: Test in browser**

Open http://localhost:8080, verify ES6 modules load correctly

- [ ] **Step 3: Verify no console errors**

Open browser console and verify no module loading errors

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/chat.html
git commit -m "refactor: add type=module to script tag for ES6 module support"
```

---

## Task 22: Final Testing and Verification

**Files:**
- Test: All functionality in browser

- [ ] **Step 1: Start application**

Run: `mvn spring-boot:run`
Expected: Application starts on http://localhost:8080

- [ ] **Step 2: Test agent selection**

- Click different agent cards in sidebar
- Verify agent info updates in header
- Verify active state styling

- [ ] **Step 3: Test message sending**

- Send a text message
- Verify typing indicator shows
- Verify agent response displays
- Verify markdown rendering works

- [ ] **Step 4: Test debug panel**

- Send a message
- Verify round card appears
- Verify timeline events show
- Verify metrics display correctly
- Test expand/collapse functionality
- Test clear button

- [ ] **Step 5: Test file upload**

- Upload a document file (.docx, .pdf, or .xlsx)
- Verify file tag appears
- Send message with file
- Verify agent processes file

- [ ] **Step 6: Test image upload**

- Upload an image file (.jpg, .png, etc.)
- Verify image preview shows
- Test image modal (click to preview)

- [ ] **Step 7: Test session management**

- Create new session
- Switch between sessions
- Delete a session
- Verify clear session button

- [ ] **Step 8: Test knowledge base**

- Upload a document to knowledge base
- Verify document appears in list
- Remove document from knowledge base

- [ ] **Step 9: Test config viewer**

- Click info button on agent card
- Verify config modal displays
- Test skill/tool info links
- Test modal close (X button and escape key)

- [ ] **Step 10: Test responsive design**

- Resize browser window
- Verify sidebar collapses at 900px
- Verify sidebar collapses further at 600px

- [ ] **Step 11: Check browser console for errors**

Open browser console and verify no errors

- [ ] **Step 12: Verify all CSS modules load**

Open browser Network tab, verify all CSS files load

- [ ] **Step 13: Verify all JS modules load**

Open browser Network tab, verify all JS modules load

- [ ] **Step 14: Final commit**

```bash
git add .
git commit -m "refactor: complete frontend module refactoring - all tests passing"
```

---

## Task 23: Update Documentation

**Files:**
- Modify: `README.md` or create `FRONTEND.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Create FRONTEND.md with new structure documentation**

Create `docs/FRONTEND.md` with:
- Overview of new modular structure
- Directory structure diagram
- Module descriptions
- How to add new features
- Module dependencies

- [ ] **Step 2: Update CLAUDE.md with frontend structure**

Update the relevant section in `CLAUDE.md` to reflect the new modular structure.

- [ ] **Step 3: Commit documentation**

```bash
git add docs/FRONTEND.md CLAUDE.md
git commit -m "docs: add frontend module refactoring documentation"
```

---

## Summary

This plan refactors 4246 lines of monolithic frontend code into 22 focused modules:

**JavaScript Modules (9 files):**
1. `state.js` - Global state
2. `api.js` - API communication
3. `modules/utils.js` - Utility functions
4. `modules/ui.js` - UI rendering
5. `modules/debug.js` - Debug panel
6. `modules/agents.js` - Agent management
7. `modules/session.js` - Session management
8. `modules/knowledge.js` - Knowledge base
9. `modules/upload.js` - File upload
10. `chat.js` - Entry point

**CSS Modules (9 files):**
1. `base.css` - Base styles
2. `modules/header.css` - Header
3. `modules/sidebar.css` - Sidebar
4. `modules/chat.css` - Chat area
5. `modules/debug.css` - Debug panel
6. `modules/upload.css` - File upload
7. `modules/modal.css` - Modals
8. `modules/utils.css` - Utilities
9. `chat.css` - Entry point

**Benefits:**
- ✅ Zero-build architecture maintained
- ✅ Single entry point preserved
- ✅ Global state unchanged
- ✅ Clear module responsibilities
- ✅ Easier maintenance and development
