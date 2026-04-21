import './state.js';
import { createSSEParser, uploadFile, fetchAgents, fetchSessions, createSession as createSessionApi, deleteSession as deleteSessionApi, fetchKnowledgeDocs, uploadKnowledgeDoc, removeKnowledgeDoc as removeKnowledgeDocApi, fetchSkillInfo, fetchToolInfo } from './api.js';
import { renderMarkdown, escapeHtml, getTimestamp, formatDuration, scrollToBottom, createFileList } from './modules/utils.js';
import { chatMessages, messageInput, sendBtn, chatEmpty, chatHeaderName, chatHeaderDesc, debugPanel, debugRounds, debugToggle, appendMessage, createThinkingBox, updateThinkingBox, collapseThinkingBox, completeThinkingBox, addAgentBubble, removeTypingIndicator, setStreamingState, showTypingIndicator } from './modules/ui.js';
import { startRound, endRound, addTimelineRow, clearDebug, toggleDebug, handlePipelineStart, handlePipelineStepStart, handlePipelineStepEnd, handleRoutingDecision, handleHandoffStart } from './modules/debug.js';
import { loadAgents, selectAgent, showAgentConfig, showSkillInfo, showToolInfo } from './modules/agents.js';
import { loadSessions, createNewSession, selectSession, deleteSession, clearSession as clearSessionFn } from './modules/session.js';
import { loadKnowledgeDocs, uploadToKnowledge, removeKnowledgeDoc } from './modules/knowledge.js';
import { handleFileSelect } from './modules/upload.js';

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
    startRound(message, roundNumber, currentAgent, agents);

    showTypingIndicator();

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
