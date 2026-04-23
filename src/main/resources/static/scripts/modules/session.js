import { fetchSessions, createSession, deleteSession as deleteSessionApi } from '../api.js';
import { escapeHtml } from './utils.js';

/* ===== SESSION MANAGEMENT ===== */
export async function loadSessions() {
    try {
        var sessions = await fetchSessions();
        var listEl = document.getElementById('sessionList');
        if (!listEl) return;  // Element not in current page

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

    window.currentSessionId = sessionId;
    clearChatArea();
    loadSessions();

    // Switch agent if needed (dynamic import to avoid circular dependency)
    if (agentId && agentId !== window.currentAgent) {
        var { selectAgent } = await import('./agents.js');
        await selectAgent(agentId);
    } else {
        // Focus input if not switching agents
        setTimeout(function() {
            document.getElementById('messageInput').focus();
        }, 100);
    }
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
    // Save chatEmpty reference before clearing
    var chatEmpty = document.getElementById('chatEmpty');
    document.getElementById('chatMessages').innerHTML = '';
    window.messageCount = 0;
    window.agentRawMarkdown = '';
    window.currentThinkingBox = null;
    window.currentAgentMessageWrapper = null;
    window.thinkingContent = '';
    window.currentFileInfo = null;
    if (chatEmpty) {
        document.getElementById('chatMessages').appendChild(chatEmpty);
        chatEmpty.style.display = 'flex';
    }

    // Clear file tags
    window.uploadedFile = null;
    window.uploadedImages = [];
    window.uploadedAudio = null;
    document.getElementById('fileTagArea').innerHTML = '';

    // Clear debug rounds
    document.getElementById('debugRounds').innerHTML = '';
    window.rounds = [];
    window.currentRound = null;
    window.roundNumber = 0;
}
