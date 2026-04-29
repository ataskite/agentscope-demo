import { fetchAgents, fetchSkillInfo, fetchToolInfo, fetchKnowledgeStatus } from '../api.js';
import { escapeHtml } from './utils.js';
import { setStreamingState } from './ui.js';
import { agents } from '../state.js';

/* ===== CATEGORY DEFINITIONS ===== */
const CATEGORIES = [
    { key: 'single',        label: '单Agent',       icon: '⚡', color: 'cyan'    },
    { key: 'expert',        label: '专家Agent',      icon: '🎯', color: 'green'   },
    { key: 'collaboration', label: '多智能体协作',   icon: '🔗', color: 'magenta' },
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

    // Show sample prompts if available
    showSamplePrompts(agentId);

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
        var sessionModule = await import('./session.js');
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

    agent.config.samplePrompts.forEach(function(sample) {
        promptsHtml += '<div class="sample-prompt-item" onclick="useSamplePrompt(\'' +
            sample.prompt.replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, '\\n') +
            '\')">' +
            '<div class="sample-prompt-text">' + escapeHtml(sample.prompt) + '</div>' +
            '<div class="sample-prompt-hint">' + escapeHtml(sample.expectedBehavior) + '</div>' +
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
