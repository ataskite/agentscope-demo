import './state.js';

/* ===== SSE PARSER ===== */
function createSSEParser() {
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

/* ===== MARKED CONFIG ===== */
if (typeof marked !== 'undefined') {
    marked.setOptions({
        breaks: true,
        gfm: true
    });
}

function renderMarkdown(text) {
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

// Export structured data as JSON file
function exportStructuredData(btn) {
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
}

/* ===== DOM REFERENCES ===== */
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');
const chatEmpty = document.getElementById('chatEmpty');
const chatHeaderName = document.getElementById('chatHeaderName');
const chatHeaderDesc = document.getElementById('chatHeaderDesc');
const debugPanel = document.getElementById('debugPanel');
const debugRounds = document.getElementById('debugRounds');
const debugToggle = document.getElementById('debugToggle');

/* ===== LOAD AGENTS ===== */
async function loadAgents() {
    try {
        const response = await fetch('/api/agents');
        const agentList = await response.json();

        const agentListEl = document.getElementById('agentList');
        agentListEl.innerHTML = '';

        agentList.forEach(function(agent) {
            agents[agent.agentId] = {
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

/* ===== CONFIG VIEWER ===== */
function showAgentConfig(agentId) {
    var agent = agents[agentId];
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

function closeConfigModal() {
    var modal = document.getElementById('configModal');
    if (modal) modal.remove();
}

async function showSkillInfo(skillName) {
    try {
        var response = await fetch('/api/skills/' + skillName);
        if (!response.ok) {
            var err = await response.json();
            showInfoModal('Error', err.error || 'Skill not found');
            return;
        }
        var info = await response.json();

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

async function showToolInfo(toolName) {
    try {
        var response = await fetch('/api/tools/' + toolName);
        if (!response.ok) {
            var err = await response.json();
            showInfoModal('Error', err.error || 'Tool not found');
            return;
        }
        var info = await response.json();

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

function closeInfoModal() {
    var modal = document.getElementById('infoModal');
    if (modal) modal.remove();
}

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

/* ===== SELECT AGENT ===== */
function selectAgent(agentId) {
    removeFile();
    // Force cleanup streaming state to allow switching
    if (isStreaming) {
        isStreaming = false;
        setStreamingState(false);
        if (currentAbortController) {
            currentAbortController.abort();
            currentAbortController = null;
        }
        // Complete the current round if it exists
        if (currentRound) {
            var roundNum = currentRound.number;
            currentRound.endTime = Date.now();
            currentRound.status = 'interrupted';
            var card = document.getElementById('round-' + roundNum);
            if (card) {
                card.classList.remove('running');
                card.classList.add('error');
            }
            currentRound = null;
        }
        // Don't clear rounds array yet - wait for any pending events to finish
        setTimeout(function() {
            debugRounds.innerHTML = '';
            rounds = [];
            roundNumber = 0;
        }, 500);
    } else {
        debugRounds.innerHTML = '';
        rounds = [];
        currentRound = null;
        roundNumber = 0;
    }
    if (!agents[agentId]) return;
    currentAgent = agentId;

    document.querySelectorAll('.agent-card').forEach(function(card) {
        card.classList.toggle('active', card.dataset.agentId === agentId);
    });

    chatHeaderName.textContent = agents[agentId].name;
    chatHeaderDesc.textContent = agents[agentId].desc;

    chatMessages.innerHTML = '';
    messageCount = 0;
    chatMessages.appendChild(chatEmpty);
    chatEmpty.style.display = 'flex';

    messageInput.focus();

    // Reset agent message state
    currentThinkingBox = null;
    currentAgentMessageWrapper = null;
    thinkingContent = '';
    currentFileInfo = null;
    agentRawMarkdown = '';

    // Auto-create a new session for this agent
    createNewSession();
}

async function sendMessage() {
    var input = messageInput;
    var message = input.value.trim();
    if ((!message && !uploadedFile && uploadedImages.length === 0 && !uploadedAudio) || isStreaming) return;

    chatEmpty.style.display = 'none';

    document.querySelectorAll('.round-body:not(.collapsed)').forEach(function(body) {
        body.classList.add('collapsed');
    });

    // Build user message with media info
    var mediaInfo = {
        file: uploadedFile,
        images: uploadedImages.length > 0 ? uploadedImages : null,
        audio: uploadedAudio
    };
    appendMessage('user', message, mediaInfo);
    messageCount++;

    input.value = '';
    input.style.height = 'auto';

    var fileInfo = uploadedFile;
    var imagesCopy = uploadedImages.slice();
    var audioCopy = uploadedAudio;
    clearAllMedia();
    setStreamingState(true);

    // Ensure clean state before starting new round
    if (currentRound) {
        console.warn('[sendMessage] currentRound already exists, cleaning up:', currentRound);
        currentRound = null;
    }
    startRound(message);

    var typingEl = document.createElement('div');
    typingEl.className = 'typing-indicator';
    typingEl.id = 'typingIndicator';
    typingEl.innerHTML = '<div class="message-avatar"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg></div><div class="dots"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>';
    chatMessages.appendChild(typingEl);
    scrollToBottom(chatMessages);

    currentThinkingBox = null;
    currentAgentMessageWrapper = null;
    thinkingContent = '';
    currentFileInfo = null;
    currentFileInfo = fileInfo;

    var agentBubble = null;

    if (fileInfo) {
        createThinkingBox(fileInfo);
    }

    try {
        currentAbortController = new AbortController();

        // Build request payload with multi-modal support
        var payload = {
            agentId: currentAgent,
            message: message,
            filePath: fileInfo ? fileInfo.filePath : null,
            fileName: fileInfo ? fileInfo.fileName : null,
            sessionId: currentSessionId || null
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
            signal: currentAbortController.signal
        });

        if (!response.ok) {
            removeTypingIndicator();
            appendMessage('error', '请求失败: ' + response.status);
            endRound('error');
            setStreamingState(false);
            return;
        }

        removeTypingIndicator();
        messageCount++;

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
                            if (currentRound) {
                                addTimelineRow('phase', 'Agent Start', payload.agentName || '', 'running');
                            }
                            break;

                        case 'agent_end':
                            if (currentRound) {
                                var totalDur = payload.duration_ms || 0;
                                addTimelineRow('phase', 'Agent End', formatDuration(totalDur), 'ok');
                                currentRound.totalLlmCalls = payload.totalLlmCalls || 0;
                                currentRound.totalToolCalls = payload.totalToolCalls || 0;
                            }
                            break;

                        case 'llm_start':
                            if (currentRound) {
                                var callNum = payload.callNumber || (currentRound.llmCallCount + 1);
                                currentRound._currentLlmStart = Date.now();
                                currentRound._currentLlmCallNum = callNum;
                                currentRound._currentLlmRow = addTimelineRow('llm', 'LLM #' + callNum, (payload.modelName || '') + ' ...', 'running');
                            }
                            break;

                        case 'llm_end':
                            if (currentRound) {
                                currentRound.llmCallCount++;
                                var tokens = payload.totalTokens ? payload.totalTokens.toLocaleString() + ' tokens' : '';
                                var llmTimeSec = payload.llmTime ? payload.llmTime : 0;
                                var llmDurMs = 0;
                                if (currentRound._currentLlmStart) {
                                    llmDurMs = Date.now() - currentRound._currentLlmStart;
                                }
                                var timeStr = llmDurMs > 0 ? formatDuration(llmDurMs) : (llmTimeSec > 0 ? (llmTimeSec >= 1 ? llmTimeSec.toFixed(1) + 's' : (llmTimeSec * 1000).toFixed(0) + 'ms') : '');

                                currentRound.inputTokens += (payload.inputTokens || 0);
                                currentRound.outputTokens += (payload.outputTokens || 0);
                                currentRound.totalTokens += (payload.totalTokens || 0);
                                currentRound.llmTime += llmTimeSec;

                                if (currentRound._currentLlmRow) {
                                    var row = currentRound._currentLlmRow;
                                    var metricsEl = row.querySelector('.rtl-metrics');
                                    var statusEl = row.querySelector('.rtl-status');
                                    if (metricsEl) metricsEl.textContent = [tokens, timeStr].filter(Boolean).join(' ');
                                    if (statusEl) statusEl.textContent = '✓';
                                    row.classList.remove('status-running');
                                    row.classList.add('status-ok');
                                    currentRound._currentLlmRow = null;
                                }

                                updateRoundMetrics();
                                currentRound._currentLlmStart = null;
                            }
                            break;

                        case 'thinking':
                            if (currentRound && !currentRound.thinkingCycleStart) {
                                currentRound.thinkingCycleStart = Date.now();
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
                            if (currentRound && currentRound.thinkingCycleStart) {
                                currentRound.thinkingTime += Date.now() - currentRound.thinkingCycleStart;
                                currentRound.thinkingCycleStart = null;
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

                            if (currentRound) {
                                currentRound.toolCallCount++;
                                currentRound._currentToolStart = Date.now();
                                var rowType = isSkill ? 'skill' : 'tool';
                                var rowLabel = isSkill ? ('Skill → ' + (tSkillName || '')) : ('Tool → ' + tName);
                                currentRound._currentToolRow = addTimelineRow(rowType, rowLabel, '...', 'running');
                                currentRound._currentToolIsSkill = isSkill;
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

                            var targetRound = currentRound || (rounds.length > 0 ? rounds[rounds.length - 1] : null);
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

                                var metricsEl2 = document.getElementById('round-metrics-' + targetRound.number);
                                if (metricsEl2) updateRoundMetricsForRound(targetRound);
                            } else {
                                console.warn('[tool_end] No targetRound available!', 'payload:', payload);
                            }
                            break;

                        // ===== STREAM CONTENT EVENTS =====

                        case 'text':
                            if (currentRound && currentRound.thinkingCycleStart) {
                                currentRound.thinkingTime += Date.now() - currentRound.thinkingCycleStart;
                                currentRound.thinkingCycleStart = null;
                            }
                            collapseThinkingBox();
                            if (!agentBubble) {
                                agentBubble = addAgentBubble();
                                agentRawMarkdown = '';
                            }
                            agentRawMarkdown += (payload.content || payload.text || '');
                            agentBubble.classList.add('md-render');
                            agentBubble.innerHTML = renderMarkdown(agentRawMarkdown);
                            scrollToBottom(chatMessages);
                            break;

                        case 'done':
                            console.log('[SSE] Received done event, currentRound:', currentRound ? '#' + currentRound.number : 'null');
                            completeThinkingBox();
                            isStreaming = false;
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
                            isStreaming = false;
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
            isStreaming = false;
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
        currentAbortController = null;
    }
}

function stopStreaming() {
    if (currentAbortController) {
        currentAbortController.abort();
        currentAbortController = null;
    }
    isStreaming = false;
    setStreamingState(false);
}

/* ===== HELPERS ===== */
function appendMessage(role, text, fileInfo) {
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

// Thinking box management
function createThinkingBox(fileInfo) {
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

    currentAgentMessageWrapper = wrapper;
    currentThinkingBox = box;
    scrollToBottom(chatMessages);
    return box;
}

function updateThinkingBox(content, fileInfo) {
    if (!currentThinkingBox) {
        createThinkingBox(fileInfo || currentFileInfo);
    }
    thinkingContent += content;
    var lines = thinkingContent.split('\n');
    var filteredLines = lines.filter(function(line) {
        var trimmed = line.trim();
        return trimmed !== '' && !trimmed.includes('__fragment__');
    });
    var contentEl = currentThinkingBox.querySelector('.thinking-content');
    contentEl.textContent = filteredLines.join('\n\n');
    contentEl.scrollTop = contentEl.scrollHeight;
    scrollToBottom(chatMessages);
}

function collapseThinkingBox() {
    if (currentThinkingBox && !currentThinkingBox.classList.contains('collapsed')) {
        currentThinkingBox.classList.add('collapsed');
        currentThinkingBox.classList.add('completed');
    }
}

function completeThinkingBox() {
    if (currentThinkingBox) {
        currentThinkingBox.classList.add('completed');
        currentThinkingBox.classList.add('collapsed');
        thinkingContent = '';
        currentThinkingBox = null;
        currentFileInfo = null;
    }
}

function toggleThinking(header) {
    var box = header.parentElement;
    box.classList.toggle('collapsed');
}

function addAgentBubble() {
    if (!currentAgentMessageWrapper) {
        createThinkingBox(currentFileInfo);
    }
    var content = currentAgentMessageWrapper.querySelector('.agent-response-wrapper');

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

function removeTypingIndicator() {
    var el = document.getElementById('typingIndicator');
    if (el) el.remove();
}

function setStreamingState(streaming) {
    isStreaming = streaming;
    messageInput.disabled = streaming;
    sendBtn.disabled = streaming;
    if (!streaming) {
        messageInput.focus();
    }
}

/* ===== ROUND MANAGEMENT ===== */
function startRound(userMessage) {
    roundNumber++;
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
    rounds.push(round);
    currentRound = round;

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

function endRound(status) {
    console.log('[endRound] Called with status:', status, 'currentRound:', currentRound, 'rounds.length:', rounds.length);

    var roundNumber = currentRound ? currentRound.number : roundNumber;
    var round = currentRound || (rounds.length > 0 ? rounds[rounds.length - 1] : null);

    if (!round) {
        console.error('[endRound] No round to end! rounds:', rounds);
        return;
    }

    roundNumber = round.number;
    console.log('[endRound] Ending round #' + roundNumber + ' with status:', status);

    if (!round.endTime) {
        round.endTime = Date.now();
    }
    round.status = status;

    // Update metrics for the round
    if (round === currentRound) {
        updateRoundMetrics();
    } else {
        updateRoundMetricsForRound(round);
    }

    // Stop all running timeline rows in this round
    var roundCard = document.getElementById('round-' + roundNumber);
    if (!roundCard) {
        console.error('[endRound] Round card not found: round-' + roundNumber);
        currentRound = null;
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

    currentRound = null;
    console.log('[endRound] Completed');
}

function updateRoundMetrics() {
    if (!currentRound) return;
    updateRoundMetricsForRound(currentRound);
}

function updateRoundMetricsForRound(r) {
    var metricsEl = document.getElementById('round-metrics-' + r.number);
    if (!metricsEl) return;

    var wallMs = (r.endTime || Date.now()) - r.startTime;
    var speed = r.llmTime > 0 ? Math.round(r.outputTokens / r.llmTime) : 0;
    var modelName = currentAgent && agents[currentAgent] ? agents[currentAgent].config.modelName : '';

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

function addTimelineRow(type, label, metrics, status) {
    if (!currentRound) return null;
    var timeline = document.getElementById('round-timeline-' + currentRound.number);
    if (!timeline) return null;

    currentRound.timelineStep = (currentRound.timelineStep || 0) + 1;
    var stepNum = currentRound.timelineStep;

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

function formatDuration(ms) {
    if (ms < 0) return '-';
    if (ms < 1000) return Math.round(ms) + 'ms';
    return (ms / 1000).toFixed(1) + 's';
}

function toggleRoundDetails(roundNum) {
    var body = document.getElementById('round-body-' + roundNum);
    if (body) body.classList.toggle('collapsed');
}

function clearDebug() {
    debugRounds.innerHTML = '';
    rounds = [];
    currentRound = null;
    roundNumber = 0;
}

function clearSession() {
    if (isStreaming) return;
    if (currentSessionId) {
        deleteSession(currentSessionId);
    } else {
        clearChatArea();
    }
}

function scrollToBottom(element) {
    requestAnimationFrame(function() {
        element.scrollTop = element.scrollHeight;
    });
}

function getTimestamp() {
    return new Date().toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/* ===== DEBUG PANEL TOGGLE ===== */
function toggleDebug() {
    var isCollapsed = debugPanel.classList.contains('collapsed');
    if (isCollapsed) {
        debugPanel.classList.remove('collapsed');
        debugToggle.innerHTML = '&#x25B6;';
    } else {
        debugPanel.classList.add('collapsed');
        debugToggle.innerHTML = '&#x25C0;';
    }
}

/* ===== FILE UPLOAD ===== */
function handleFileSelect(input) {
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

    var formData = new FormData();
    formData.append('file', file);

    fetch('/chat/upload', {
        method: 'POST',
        body: formData
    })
    .then(function(response) { return response.json(); })
    .then(function(data) {
        if (data.error) {
            console.error('Upload error:', data.error);
            return;
        }

        // Handle based on file type
        if (isDoc) {
            if (currentAgent !== 'task-document-analysis') {
                selectAgent('task-document-analysis');
            }
            uploadedFile = data;
            showFileTag(data.fileName);
        } else if (isImage) {
            uploadedImages.push(data);
            showImagePreviews();
            // Auto-switch to vision agent if available
            if (agents['vision-analyzer']) {
                selectAgent('vision-analyzer');
            }
        } else if (isAudio) {
            uploadedAudio = data;
            showAudioPreview();
            // Auto-switch to voice agent if available
            if (agents['voice-assistant']) {
                selectAgent('voice-assistant');
            }
        }
    })
    .catch(function(err) {
        console.error('Upload error:', err);
    });

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
    if (uploadedFile) {
        html += '<div class="file-tag">' +
            '<span class="file-tag-name">' + escapeHtml(uploadedFile.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeFile()">×</span>' +
            '</div>';
    }

    // Show images
    uploadedImages.forEach(function(img, index) {
        html += '<div class="file-tag image-tag">' +
            '<span class="file-tag-name image-preview" onclick="showImageModal(' + index + ')">' + escapeHtml(img.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeImage(' + index + ')">×</span>' +
            '</div>';
    });

    // Show audio if any
    if (uploadedAudio) {
        html += '<div class="file-tag audio-tag">' +
            '<span class="file-tag-name">' + escapeHtml(uploadedAudio.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeAudio()">×</span>' +
            '</div>';
    }

    area.innerHTML = html;
}

function showAudioPreview() {
    showImagePreviews(); // Reuse the same function
}

function removeFile() {
    uploadedFile = null;
    showImagePreviews();
}

function removeImage(index) {
    uploadedImages.splice(index, 1);
    showImagePreviews();
}

function removeAudio() {
    uploadedAudio = null;
    showImagePreviews();
}

function showImageModal(index) {
    var img = uploadedImages[index];
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

function clearAllMedia() {
    uploadedFile = null;
    uploadedImages = [];
    uploadedAudio = null;
    document.getElementById('fileTagArea').innerHTML = '';
}

/* ===== FILE LIST HELPER ===== */
function createFileList(fileInfo) {
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

/* ===== SESSION MANAGEMENT ===== */
async function loadSessions() {
    try {
        var response = await fetch('/api/sessions');
        var sessions = await response.json();
        var listEl = document.getElementById('sessionList');
        listEl.innerHTML = '';

        sessions.forEach(function(s) {
            var item = document.createElement('div');
            item.className = 'session-item' + (s.sessionId === currentSessionId ? ' active' : '');
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

async function createNewSession() {
    if (!currentAgent) return;
    try {
        var response = await fetch('/api/sessions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ agentId: currentAgent })
        });
        var data = await response.json();
        if (data.sessionId) {
            currentSessionId = data.sessionId;
            clearChatArea();
            loadSessions();
        }
    } catch (err) {
        console.error('Failed to create session', err);
    }
}

async function selectSession(sessionId, agentId) {
    if (isStreaming) return;

    // Switch agent if needed
    if (agentId && agentId !== currentAgent) {
        selectAgent(agentId);
    }

    currentSessionId = sessionId;
    clearChatArea();
    loadSessions();
}

async function deleteSession(sessionId) {
    if (isStreaming) return;
    try {
        await fetch('/api/sessions/' + sessionId, { method: 'DELETE' });
        if (currentSessionId === sessionId) {
            currentSessionId = null;
            clearChatArea();
        }
        loadSessions();
    } catch (err) {
        console.error('Failed to delete session', err);
    }
}

function clearChatArea() {
    chatMessages.innerHTML = '';
    messageCount = 0;
    agentRawMarkdown = '';
    currentThinkingBox = null;
    currentAgentMessageWrapper = null;
    thinkingContent = '';
    currentFileInfo = null;
    chatMessages.appendChild(chatEmpty);
    chatEmpty.style.display = 'flex';
    removeFile();
    debugRounds.innerHTML = '';
    rounds = [];
    currentRound = null;
    roundNumber = 0;
}

/* ===== KNOWLEDGE MANAGEMENT ===== */
async function loadKnowledgeDocs() {
    try {
        var response = await fetch('/api/knowledge/documents');
        var docs = await response.json();
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

async function uploadToKnowledge(input) {
    var file = input.files[0];
    if (!file) return;

    var formData = new FormData();
    formData.append('file', file);

    try {
        var response = await fetch('/api/knowledge/upload', {
            method: 'POST',
            body: formData
        });
        var data = await response.json();
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

async function removeKnowledgeDoc(fileName) {
    try {
        await fetch('/api/knowledge/documents/' + encodeURIComponent(fileName), { method: 'DELETE' });
        loadKnowledgeDocs();
    } catch (err) {
        console.error('Failed to remove knowledge doc', err);
    }
}

/* ===== MULTI-AGENT EVENT HANDLERS ===== */

function handlePipelineStart(data) {
    var debugRounds = document.getElementById('debugRounds');
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

function handlePipelineStepStart(data) {
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

function handlePipelineStepEnd(data) {
    var stepDiv = document.getElementById('step-' + data.stepIndex);
    if (stepDiv) {
        stepDiv.classList.remove('active');
        stepDiv.classList.add('complete');
        var statusSpan = stepDiv.querySelector('.debug-step-status');
        if (statusSpan) statusSpan.textContent = 'Done (' + (data.duration_ms || 0) + 'ms)';
    }
}

function handleRoutingDecision(data) {
    var debugRounds = document.getElementById('debugRounds');
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

function handleHandoffStart(data) {
    var debugRounds = document.getElementById('debugRounds');
    if (!debugRounds) return;
    var handoffDiv = document.createElement('div');
    handoffDiv.className = 'debug-handoff';
    handoffDiv.innerHTML = '<div class="debug-handoff-header">' +
        '<span class="debug-handoff-icon">\u2192</span>' +
        '<span class="debug-handoff-text">Handoff: <strong>' + escapeHtml(data.fromAgent || '') + '</strong> \u2192 <strong>' + escapeHtml(data.toAgent || '') + '</strong></span></div>' +
        '<div class="debug-handoff-reason">Reason: ' + escapeHtml(data.reason || '') + '</div>';
    debugRounds.appendChild(handoffDiv);
}

/* ===== INIT ===== */
loadAgents();
loadSessions();
loadKnowledgeDocs();
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeConfigModal();
        closeInfoModal();
    }
});
messageInput.focus();
