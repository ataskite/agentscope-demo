import { debugRounds, debugPanel, debugToggle } from './ui.js';
import { escapeHtml, formatDuration, scrollToBottom } from './utils.js';

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
    return addTimelineRowForRound(window.currentRound, type, label, metrics, status);
}

export function addTimelineRowForRound(round, type, label, metrics, status) {
    if (!round) return null;
    var timeline = document.getElementById('round-timeline-' + round.number);
    if (!timeline) {
        console.warn('[addTimelineRowForRound] Timeline not found for round #' + round.number);
        return null;
    }

    round.timelineStep = (round.timelineStep || 0) + 1;
    var stepNum = round.timelineStep;

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
        case 'running': return '◎ <span class="stream-dots"><span></span><span></span><span></span></span>';
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
    console.log('[handlePipelineStart] data:', data);
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) {
        console.warn('[handlePipelineStart] No round available');
        return;
    }

    var timeline = document.getElementById('round-timeline-' + targetRound.number);
    if (!timeline) {
        console.warn('[handlePipelineStart] Timeline not found for round #' + targetRound.number);
        return;
    }

    // Add pipeline start to timeline
    addTimelineRowForRound(targetRound, 'phase', 'Pipeline Start', escapeHtml(data.pipelineId || ''), 'running');

    // Also add a dedicated pipeline card for detailed step tracking
    var pipelineDiv = document.createElement('div');
    pipelineDiv.className = 'debug-pipeline';
    pipelineDiv.id = 'pipeline-' + Date.now();
    pipelineDiv.innerHTML = '<div class="debug-pipeline-header">' +
        '<span class="debug-pipeline-title">Pipeline: ' + escapeHtml(data.pipelineId || '') + '</span>' +
        '<span class="debug-pipeline-steps">' + (data.subAgents ? data.subAgents.length : 0) + ' steps</span>' +
        '</div>' +
        '<div class="debug-pipeline-steps-container" id="' + pipelineDiv.id + '-steps"></div>';

    // Insert after the round card
    var roundCard = document.getElementById('round-' + targetRound.number);
    if (roundCard && roundCard.parentNode) {
        roundCard.parentNode.insertBefore(pipelineDiv, roundCard.nextSibling);
    }

    targetRound._currentPipelineDiv = pipelineDiv;
    scrollToBottom(debugRounds);
}

export function handlePipelineStepStart(data) {
    console.log('[handlePipelineStepStart] data:', data);
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) {
        console.warn('[handlePipelineStepStart] No round available');
        return;
    }

    // Add step start to timeline
    addTimelineRowForRound(targetRound, 'phase', 'Step ' + (data.stepIndex + 1), escapeHtml(data.agentId || ''), 'running');

    // Find the pipeline container
    var container = targetRound._currentPipelineDiv ?
        targetRound._currentPipelineDiv.querySelector('.debug-pipeline-steps-container') :
        document.querySelector('.debug-pipeline-steps-container:last-child');

    if (!container) {
        console.warn('[handlePipelineStepStart] No pipeline container found');
        return;
    }

    var stepDiv = document.createElement('div');
    stepDiv.className = 'debug-pipeline-step active';
    stepDiv.id = 'step-' + data.stepIndex;
    stepDiv.innerHTML = '<span class="debug-step-number">' + (data.stepIndex + 1) + '</span>' +
        '<span class="debug-step-agent">' + escapeHtml(data.agentId || '') + '</span>' +
        '<span class="debug-step-status">Running...</span>';
    container.appendChild(stepDiv);
    targetRound._currentStepDiv = stepDiv;
}

export function handlePipelineStepEnd(data) {
    console.log('[handlePipelineStepEnd] data:', data);
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) {
        console.warn('[handlePipelineStepEnd] No round available');
        return;
    }

    // Update step in timeline
    var stepTimelineRow = targetRound._currentStepDiv;
    if (stepTimelineRow) {
        stepTimelineRow.classList.remove('active');
        stepTimelineRow.classList.add('complete');
        var statusSpan = stepTimelineRow.querySelector('.debug-step-status');
        if (statusSpan) statusSpan.textContent = 'Done (' + (data.duration_ms || 0) + 'ms)';
        targetRound._currentStepDiv = null;
    }

    // Add step completion to timeline
    addTimelineRowForRound(targetRound, 'phase', 'Step ' + (data.stepIndex + 1) + ' End', formatDuration(data.duration_ms || 0), 'ok');
}

export function handleRoutingDecision(data) {
    console.log('[handleRoutingDecision] data:', data);
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) {
        console.warn('[handleRoutingDecision] No round available');
        return;
    }

    var timeline = document.getElementById('round-timeline-' + targetRound.number);
    if (!timeline) {
        console.warn('[handleRoutingDecision] Timeline not found for round #' + targetRound.number);
        return;
    }

    // Add routing decision to timeline
    addTimelineRowForRound(targetRound, 'phase', 'Routing', '\u2192 ' + escapeHtml(data.selectedAgent || ''), 'ok');

    // Add detailed routing card
    var routingDiv = document.createElement('div');
    routingDiv.className = 'debug-routing';
    routingDiv.innerHTML = '<div class="debug-routing-header">' +
        '<span class="debug-routing-title">Routing Decision</span></div>' +
        '<div class="debug-routing-content">' +
        '<div class="debug-routing-selected"><span class="debug-routing-label">Selected:</span> ' +
        '<span class="debug-routing-agent">' + escapeHtml(data.selectedAgent || '') + '</span></div>';

    if (data.reasoning) {
        routingDiv.querySelector('.debug-routing-content').innerHTML +=
            '<div class="debug-routing-reasoning"><span class="debug-routing-label">Reasoning:</span> ' +
            '<span class="debug-routing-text">' + escapeHtml(data.reasoning) + '</span></div>';
    }
    routingDiv.querySelector('.debug-routing-content').innerHTML += '</div>';

    // Insert after the round card
    var roundCard = document.getElementById('round-' + targetRound.number);
    if (roundCard && roundCard.parentNode) {
        // Find the next sibling that is not already a multi-agent card
        var nextSibling = roundCard.nextSibling;
        while (nextSibling && (nextSibling.classList.contains('debug-pipeline') ||
                               nextSibling.classList.contains('debug-routing') ||
                               nextSibling.classList.contains('debug-handoff'))) {
            nextSibling = nextSibling.nextSibling;
        }
        roundCard.parentNode.insertBefore(routingDiv, nextSibling);
    }

    scrollToBottom(debugRounds);
}

export function handleHandoffStart(data) {
    console.log('[handleHandoffStart] data:', data);
    var targetRound = window.currentRound || (window.rounds.length > 0 ? window.rounds[window.rounds.length - 1] : null);
    if (!targetRound) {
        console.warn('[handleHandoffStart] No round available');
        return;
    }

    var timeline = document.getElementById('round-timeline-' + targetRound.number);
    if (!timeline) {
        console.warn('[handleHandoffStart] Timeline not found for round #' + targetRound.number);
        return;
    }

    // Add handoff to timeline
    addTimelineRowForRound(targetRound, 'phase', 'Handoff', escapeHtml(data.fromAgent || '') + ' \u2192 ' + escapeHtml(data.toAgent || ''), 'ok');

    // Add detailed handoff card
    var handoffDiv = document.createElement('div');
    handoffDiv.className = 'debug-handoff';
    handoffDiv.innerHTML = '<div class="debug-handoff-header">' +
        '<span class="debug-handoff-icon">\u2192</span>' +
        '<span class="debug-handoff-text">Handoff: <strong>' + escapeHtml(data.fromAgent || '') + '</strong> \u2192 <strong>' + escapeHtml(data.toAgent || '') + '</strong></span></div>';

    if (data.reason) {
        handoffDiv.innerHTML += '<div class="debug-handoff-reason">Reason: ' + escapeHtml(data.reason) + '</div>';
    }

    // Insert after the round card
    var roundCard = document.getElementById('round-' + targetRound.number);
    if (roundCard && roundCard.parentNode) {
        // Find the next sibling that is not already a multi-agent card
        var nextSibling = roundCard.nextSibling;
        while (nextSibling && (nextSibling.classList.contains('debug-pipeline') ||
                               nextSibling.classList.contains('debug-routing') ||
                               nextSibling.classList.contains('debug-handoff'))) {
            nextSibling = nextSibling.nextSibling;
        }
        roundCard.parentNode.insertBefore(handoffDiv, nextSibling);
    }

    scrollToBottom(debugRounds);
}
