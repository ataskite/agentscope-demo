import { fetchAgents, fetchSamplePrompt, fetchSkillInfo, fetchToolInfo, fetchKnowledgeStatus } from '../api.js?v=2.5';
import { escapeHtml } from './utils.js';
import { setStreamingState } from './ui.js';
import { agents } from '../state.js?v=2.4';

/* ===== CATEGORY DEFINITIONS ===== */
const CATEGORIES = [
    { key: 'single',        label: 'Single Agent',       icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>', color: 'cyan'    },
    { key: 'expert',        label: 'Expert Agent',       icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 1v4m0 14v4M4.22 4.22l2.83 2.83m9.9 9.9l2.83 2.83M1 12h4m14 0h4M4.22 19.78l2.83-2.83m9.9-9.9l2.83-2.83"/></svg>', color: 'green'   },
    { key: 'collaboration', label: 'Multi-Agent',        icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="5" r="3"/><circle cx="5" cy="19" r="3"/><circle cx="19" cy="19" r="3"/><line x1="12" y1="8" x2="5" y2="16"/><line x1="12" y1="8" x2="19" y2="16"/><line x1="5" y1="19" x2="19" y2="19"/></svg>', color: 'magenta' },
    { key: 'harness',       label: 'Harness Agent',      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="2"/><path d="M7 12h10"/><path d="M12 7v10"/><circle cx="12" cy="12" r="3"/></svg>', color: 'orange'  },
    { key: 'demo',          label: '2.0 Feature Demo',   icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>', color: 'purple'  },
    { key: 'cs-customer-care', label: '消保客服三线两GAP', icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 18v-6a9 9 0 0 1 18 0v6"/><path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"/></svg>', color: 'pink' },
];

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

        // Render each category group
        CATEGORIES.forEach(function(category, index) {
            var agentsInGroup = grouped[category.key] || [];
            if (agentsInGroup.length === 0) return;

            var isExpanded = (index === 0);

            // Group body
            var body = document.createElement('div');
            body.className = 'agent-group-body' + (isExpanded ? '' : ' collapsed');

            // Group header
            var header = document.createElement('div');
            header.className = 'agent-group-header color-' + category.color + (isExpanded ? '' : ' collapsed');
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
                        '<div class="agent-card-name">' + escapeHtml(agent.name) +
                            (agent.harnessConfig ? ' <span class="agent-badge ' + (agent.harnessConfig.executionMode === 'BUILDER' ? 'builder' : 'claw') + '">' + (agent.harnessConfig.executionMode === 'BUILDER' ? 'Builder' : 'Claw') + '</span>' : '') +
                        '</div>' +
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

/* ===== SELECT AGENT ===== */
export async function selectAgent(agentId) {
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
    if (!agents[agentId]) return;
    window.currentAgent = agentId;
    currentAgent = agentId;

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

    document.getElementById('chatHeaderName').textContent = agents[agentId].name;
    document.getElementById('chatHeaderDesc').textContent = agents[agentId].desc;

    // Show/hide Harness controls (mode toggle + user selector)
    var harnessControls = document.getElementById('harnessControls');
    var builderSelector = document.getElementById('builderUserSelector');
    var isHarness = agents[agentId] && agents[agentId].config && agents[agentId].config.type === 'HARNESS';
    if (harnessControls) {
        harnessControls.style.display = isHarness ? 'flex' : 'none';
    }
    // Default to the agent's configured mode
    if (isHarness && agents[agentId].config.harnessConfig) {
        var defaultMode = agents[agentId].config.harnessConfig.executionMode || 'CLAW';
        window.harnessMode = defaultMode;
        // Update toggle buttons
        document.querySelectorAll('.mode-btn').forEach(function(btn) {
            btn.classList.toggle('active', btn.dataset.mode === defaultMode);
        });
        // Show/hide user selector based on default mode
        if (builderSelector) {
            builderSelector.style.display = defaultMode === 'BUILDER' ? 'flex' : 'none';
        }
    }

    // Show sample prompts if available
    showSamplePrompts(agentId);

    // Show/hide permission mode selector
    renderPermissionSelector(agents[agentId].config);

    // Show/hide session type selector
    window.renderSessionTypeSelector(agents[agentId].config);

    // Clear messages and restore empty state
    var chatEmpty = document.getElementById('chatEmpty');
    document.getElementById('chatMessages').innerHTML = '';
    window.messageCount = 0;
    if (chatEmpty) {
        document.getElementById('chatMessages').appendChild(chatEmpty);
        chatEmpty.style.display = 'flex';
    }

    // Reset agent message state
    window.currentThinkingBox = null;
    window.currentAgentMessageWrapper = null;
    window.thinkingContent = '';
    window.currentFileInfo = null;
    window.agentRawMarkdown = '';

    // Auto-create a new session for this agent (dynamic import to avoid circular dependency)
    try {
        var sessionModule = await import('./session.js?v=2.5');
        await sessionModule.createNewSession(agentId);
    } catch (err) {
        console.error('Failed to create session:', err);
    }

    // Focus input AFTER session creation to ensure DOM is stable
    setTimeout(function() {
        document.getElementById('messageInput').focus();
    }, 100);
}

/* ===== SAMPLE PROMPTS ===== */
function showSamplePrompts(agentId) {
    var agent = agents[agentId];
    if (!agent || !agent.config || !agent.config.samplePrompts || agent.config.samplePrompts.length === 0) {
        return;
    }

    var chatEmpty = document.getElementById('chatEmpty');
    if (!chatEmpty) return;

    var promptsHtml = '<div class="sample-prompts">' +
        '<div class="sample-prompts-title">示例提示</div>' +
        '<div class="sample-prompts-list">';

    agent.config.samplePrompts.forEach(function(sample, index) {
        var prompt = sample.prompt || '';
        var expectedBehavior = sample.expectedBehavior || '';
        promptsHtml += '<div class="sample-prompt-item" data-agent-id="' + escapeHtml(agentId) +
            '" data-sample-index="' + index + '" onclick="useSamplePromptFromElement(this)">' +
            '<div class="sample-prompt-text">' + escapeHtml(prompt) + '</div>' +
            '<div class="sample-prompt-hint">' + escapeHtml(expectedBehavior) + '</div>' +
            '</div>';
    });

    promptsHtml += '</div></div>';

    // Insert sample prompts after the welcome content
    var existingPrompts = chatEmpty.querySelector('.sample-prompts');
    if (existingPrompts) {
        existingPrompts.remove();
    }

    var welcomeContent = chatEmpty.querySelector('.chat-empty-content');
    if (welcomeContent) {
        welcomeContent.insertAdjacentHTML('afterend', promptsHtml);
    }
}

window.useSamplePrompt = function(prompt) {
    var input = document.getElementById('messageInput');
    if (input) {
        input.value = prompt;
        input.focus();
        // Trigger auto-resize
        input.dispatchEvent(new Event('input'));
    }
};

window.useSamplePromptFromElement = async function(element) {
    if (!element) return;
    var promptText = element.querySelector('.sample-prompt-text');
    var fallbackPrompt = promptText ? promptText.textContent : '';
    try {
        var sample = await fetchSamplePrompt(element.dataset.agentId, element.dataset.sampleIndex);
        window.useSamplePrompt(sample.prompt || fallbackPrompt);
    } catch (e) {
        console.error('Failed to load sample prompt:', e);
        window.useSamplePrompt(fallbackPrompt);
    }
};

/* ===== CONFIG VIEWER ===== */
export function showAgentConfig(agentId) {
    var agent = agents[agentId];
    if (!agent || !agent.config) return;

    var config = agent.config;

    var skillsHtml;
    if (config.skills && config.skills.length > 0) {
        skillsHtml = '<div class="config-field-value tags">' +
            config.skills.map(function(s) { return '<span class="config-tag skill" onclick="showSkillInfo(\'' + String(s).replace(/'/g, "\\'").replace(/"/g, '\\"') + '\')">' + escapeHtml(s) + '</span>'; }).join('') +
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

    // MCP Servers: render each server as a labeled block with its enabled tools.
    var mcpHtml = '';
    if (config.mcpServers && config.mcpServers.length > 0) {
        mcpHtml = config.mcpServers.map(function(s) {
            var serverName = escapeHtml(s.server || 'unknown');
            var toolsHtml;
            if (s.enableTools && s.enableTools.length > 0) {
                toolsHtml = s.enableTools.map(function(t) {
                    return '<span class="config-tag tool">' + escapeHtml(t) + '</span>';
                }).join('');
            } else if (s.disableTools && s.disableTools.length > 0) {
                toolsHtml = '<span class="config-tag none">All except: ' +
                    s.disableTools.map(function(t) { return escapeHtml(t); }).join(', ') +
                    '</span>';
            } else {
                toolsHtml = '<span class="config-tag none">All tools enabled</span>';
            }
            return '<div class="config-field" style="margin-left: 12px;">' +
                '<div class="config-field-label">Server: ' + serverName + '</div>' +
                '<div class="config-field-value tags">' + toolsHtml + '</div>' +
                '</div>';
        }).join('');
    }

    var knowledgeHtml = '';
    if (config.ragEnabled) {
        knowledgeHtml =
            '<hr class="config-divider">' +
            '<div class="config-field knowledge-status-field">' +
                '<div class="config-field-label">Knowledge</div>' +
                '<div class="knowledge-status" id="knowledgeStatus">Loading...</div>' +
            '</div>';
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
                (mcpHtml ? (
                    '<hr class="config-divider">' +
                    '<div class="config-field">' +
                    '<div class="config-field-label">MCP Servers</div>' +
                    mcpHtml +
                    '</div>'
                ) : '') +
                knowledgeHtml +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">System Prompt</div>' +
                    '<div class="config-field-value mono">' + escapeHtml(config.systemPrompt || '') + '</div>' +
                '</div>' +
            '</div>' +
        '</div>';

    document.body.appendChild(overlay);

    if (config.ragEnabled) {
        startKnowledgeStatusPolling();
    }
}

// Global functions for onclick
window.showAgentConfig = showAgentConfig;

var knowledgeStatusTimer = null;

async function startKnowledgeStatusPolling() {
    stopKnowledgeStatusPolling();
    await refreshKnowledgeStatus();
    knowledgeStatusTimer = setInterval(refreshKnowledgeStatus, 2500);
}

function stopKnowledgeStatusPolling() {
    if (knowledgeStatusTimer) {
        clearInterval(knowledgeStatusTimer);
        knowledgeStatusTimer = null;
    }
}

async function refreshKnowledgeStatus() {
    var target = document.getElementById('knowledgeStatus');
    if (!target) {
        stopKnowledgeStatusPolling();
        return;
    }
    try {
        var status = await fetchKnowledgeStatus();
        target.innerHTML = renderKnowledgeStatus(status);
        if (['READY', 'READY_WITH_ERRORS', 'EMPTY', 'FAILED'].indexOf(status.state) >= 0) {
            stopKnowledgeStatusPolling();
        }
    } catch (err) {
        target.innerHTML = '<div class="knowledge-status-error">Failed to load knowledge status</div>';
        stopKnowledgeStatusPolling();
    }
}

function renderKnowledgeStatus(status) {
    var documents = status.documents || [];
    var rows = documents.map(function(doc) {
        return '<div class="knowledge-status-row">' +
            '<span class="knowledge-status-file">' + escapeHtml(doc.relativePath || doc.fileName || '') + '</span>' +
            '<span class="knowledge-status-badge ' + escapeHtml(String(doc.status || '').toLowerCase()) + '">' +
                escapeHtml(doc.status || 'UNKNOWN') +
            '</span>' +
            '<span class="knowledge-status-chunks">' + Number(doc.chunkCount || 0) + ' chunks</span>' +
            (doc.message ? '<span class="knowledge-status-message">' + escapeHtml(doc.message) + '</span>' : '') +
            '</div>';
    }).join('');
    if (rows === '') {
        rows = '<div class="knowledge-status-empty">No knowledge files indexed</div>';
    }
    return '<div class="knowledge-status-summary">' +
            '<span class="knowledge-status-state">' + escapeHtml(status.state || 'UNKNOWN') + '</span>' +
            '<span>' + Number(status.indexedFiles || 0) + ' indexed</span>' +
            '<span>' + Number(status.skippedFiles || 0) + ' skipped</span>' +
            '<span>' + Number(status.failedFiles || 0) + ' failed</span>' +
        '</div>' +
        '<div class="knowledge-status-path">' + escapeHtml(status.knowledgePath || '') + '</div>' +
        '<div class="knowledge-status-list">' + rows + '</div>';
}

window.closeConfigModal = function() {
    stopKnowledgeStatusPolling();
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

// Export functions for global access (needed for inline onclick handlers)
// These must be set after module load
// Note: removeFile, removeImage, removeAudio, showImageModal, closeImageModal
// are defined in upload.js to avoid duplication
window.showAgentConfig = showAgentConfig;
window.showSkillInfo = showSkillInfo;
window.showToolInfo = showToolInfo;

window.setHarnessMode = function(mode) {
    window.harnessMode = mode;
    document.querySelectorAll('.mode-btn').forEach(function(btn) {
        btn.classList.toggle('active', btn.dataset.mode === mode);
    });
    var builderSelector = document.getElementById('builderUserSelector');
    if (builderSelector) {
        builderSelector.style.display = mode === 'BUILDER' ? 'flex' : 'none';
    }
};

window.renderPermissionSelector = function(agentConfig) {
    var container = document.getElementById('permissionModeContainer');
    if (!container) return;
    container.innerHTML = '';

    if (!agentConfig || !agentConfig.permissionConfig) {
        container.style.display = 'none';
        window.permissionMode = null;
        return;
    }

    container.style.display = 'flex';
    var modes = [
        { value: 'explore', label: 'EXPLORE (只读)', desc: '只允许读取操作' },
        { value: 'accept_edits', label: 'ACCEPT_EDITS (编辑)', desc: '允许读写，危险操作需审批' },
        { value: 'bypass', label: 'BYPASS (无限制)', desc: '所有工具均可使用' }
    ];

    var defaultMode = agentConfig.permissionConfig.defaultMode || 'explore';

    modes.forEach(function(m) {
        var btn = document.createElement('button');
        btn.className = 'perm-mode-btn' + (m.value === defaultMode ? ' active' : '');
        btn.dataset.mode = m.value;
        btn.title = m.desc;
        btn.textContent = m.label;
        btn.onclick = function() {
            container.querySelectorAll('.perm-mode-btn').forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            window.permissionMode = m.value;
        };
        container.appendChild(btn);
    });

    window.permissionMode = defaultMode;
};

window.renderSessionTypeSelector = function(agentConfig) {
    var container = document.getElementById('sessionTypeContainer');
    if (!container) return;
    container.innerHTML = '';

    if (!agentConfig || !agentConfig.sessionConfig) {
        container.style.display = 'none';
        window.sessionType = null;
        return;
    }

    container.style.display = 'flex';
    var types = [
        { value: 'memory', label: 'InMemory (内存)', desc: '内存会话，重启丢失' },
        { value: 'json', label: 'JsonSession (持久)', desc: '文件持久化，重启恢复' }
    ];

    var defaultType = agentConfig.sessionConfig.defaultType || 'memory';

    types.forEach(function(t) {
        var btn = document.createElement('button');
        btn.className = 'session-type-btn' + (t.value === defaultType ? ' active' : '');
        btn.dataset.type = t.value;
        btn.title = t.desc;
        btn.textContent = t.label;
        btn.onclick = function() {
            container.querySelectorAll('.session-type-btn').forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            window.sessionType = t.value;
            console.log('[Session Type] Changed to:', t.value);
        };
        container.appendChild(btn);
    });

    window.sessionType = defaultType;
};
