import './state.js?v=2.4';
import { createSSEParser, uploadFile, fetchAgents, fetchSessions, createSession as createSessionApi, deleteSession as deleteSessionApi, fetchKnowledgeDocs, uploadKnowledgeDoc, removeKnowledgeDoc as removeKnowledgeDocApi, fetchSkillInfo, fetchToolInfo } from './api.js?v=2.5';
import { renderMarkdown, escapeHtml, getTimestamp, formatDuration, scrollToBottom, createFileList } from './modules/utils.js?v=2.4';
import { chatMessages, messageInput, sendBtn, chatEmpty, chatHeaderName, chatHeaderDesc, debugPanel, debugRounds, debugToggle, appendMessage, createThinkingBox, updateThinkingBox, collapseThinkingBox, completeThinkingBox, addAgentBubble, addAgentBubbleAfter, removeTypingIndicator, setStreamingState, showTypingIndicator, createAgentMessageWrapper } from './modules/ui.js?v=2.4';
import { startRound, endRound, addTimelineRow, addTimelineRowForRound, clearDebug, toggleDebug, handlePipelineStart, handlePipelineStepStart, handlePipelineStepEnd, handleRoutingDecision, handleHandoffStart, updateRoundMetrics, updateRoundMetricsForRound, handleLoopStart, handleLoopEnd, handleLoopIterationResult, handleGraphTransition, handleRoundtableStart, handleRoundMessage, handleTaskDelegate, handleTaskEnd } from './modules/debug.js?v=2.6';
import { loadAgents, selectAgent, showAgentConfig, showSkillInfo, showToolInfo } from './modules/agents.js?v=2.5';
import { loadSessions, createNewSession, selectSession, deleteSession, clearSession as clearSessionFn } from './modules/session.js?v=2.5';
import { loadKnowledgeDocs, uploadToKnowledge, removeKnowledgeDoc } from './modules/knowledge.js?v=2.4';
import { initUpload } from './modules/upload.js?v=2.5';

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
    roundNumber++;
    startRound(message, roundNumber, currentAgent, agents);

    showTypingIndicator();

    currentThinkingBox = null;
    currentAgentMessageWrapper = null;
    thinkingContent = '';
    currentFileInfo = null;
    currentFileInfo = fileInfo;

    var enableThinking = !!(window.agents[currentAgent] && window.agents[currentAgent].config && window.agents[currentAgent].config.enableThinking);
    var agentBubble = null;

    if (fileInfo && enableThinking) {
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
                            console.log('[SSE] agent_start received, currentRound:', currentRound ? '#' + currentRound.number : 'null');
                            if (currentRound) {
                                addTimelineRow('phase', 'Agent Start', payload.agentName || '', 'running');
                            } else {
                                console.warn('[agent_start] No currentRound available!');
                            }
                            break;

                        case 'agent_end':
                            console.log('[SSE] agent_end received, currentRound:', currentRound ? '#' + currentRound.number : 'null', 'payload:', payload);
                            var targetRound = currentRound || (rounds.length > 0 ? rounds[rounds.length - 1] : null);
                            if (targetRound) {
                                var totalDur = payload.duration_ms || 0;
                                addTimelineRowForRound(targetRound, 'phase', 'Agent End', formatDuration(totalDur), 'ok');
                                targetRound.totalLlmCalls = payload.totalLlmCalls || 0;
                                targetRound.totalToolCalls = payload.totalToolCalls || 0;
                                updateRoundMetricsForRound(targetRound);
                            } else {
                                console.error('[agent_end] No round available! payload:', payload);
                            }
                            break;

                        case 'memory_compression':
                            if (currentRound) {
                                var memType = payload.eventType || 'compression';
                                var reduction = payload.tokenReduction ? ('-' + payload.tokenReduction + ' tokens') : '';
                                var count = payload.compressedMessageCount ? (payload.compressedMessageCount + ' messages') : '';
                                addTimelineRow('memory', 'Memory → ' + memType, [count, reduction].filter(Boolean).join(' '), 'ok');
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
                            if (enableThinking) {
                                if (currentRound && !currentRound.thinkingCycleStart) {
                                    currentRound.thinkingCycleStart = Date.now();
                                }
                                var thinkingText = payload.content || 'Processing...';
                                updateThinkingBox(thinkingText, fileInfo);
                            }
                            break;

                        case 'reasoning_text':
                            if (enableThinking) {
                                var rText = payload.content || '';
                                if (rText) {
                                    updateThinkingBox(rText, fileInfo);
                                }
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
                            var isRag = payload.name === 'retrieve_knowledge';
                            var tSkillName = payload.displayName || '';

                            if (enableThinking) {
                                if (isRag) {
                                    var ragQuery = '';
                                    try {
                                        var params = JSON.parse(payload.params || '{}');
                                        ragQuery = params.query || '';
                                    } catch(e) {
                                        ragQuery = payload.paramsPreview || '';
                                    }
                                    updateThinkingBox('🔍 RAG检索: ' + ragQuery, fileInfo);
                                } else if (isSkill) {
                                    updateThinkingBox('📖 Loading skill: ' + (tSkillName || '...'), fileInfo);
                                } else {
                                    updateThinkingBox('⚡ ' + tName + '(' + tParamsPreview + ')', fileInfo);
                                }
                            }

                            if (currentRound) {
                                currentRound.toolCallCount++;
                                currentRound._currentToolStart = Date.now();
                                var rowType = isRag ? 'rag' : (isSkill ? 'skill' : 'tool');
                                var rowLabel = isRag ? 'RAG → retrieve_knowledge' : (isSkill ? ('Skill → ' + (tSkillName || '')) : ('Tool → ' + tName));
                                currentRound._currentToolRow = addTimelineRow(rowType, rowLabel, '...', 'running');
                                currentRound._currentToolIsSkill = isSkill;
                                currentRound._currentToolIsRag = isRag;
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
                                    if (tmEl) {
                                        if (targetRound._currentToolIsRag && payload.isRagRetrieval) {
                                            var ragInfo = (payload.ragHitCount || 0) + ' hits';
                                            if (payload.ragScoreRange) ragInfo += ' (' + payload.ragScoreRange + ')';
                                            tmEl.textContent = ragInfo;
                                            tRow.title = 'Query: ' + (payload.ragQuery || '');
                                        } else {
                                            tmEl.textContent = durStr;
                                        }
                                    }
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
                            if (enableThinking) {
                                collapseThinkingBox();
                            }
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
                            removeTypingIndicator();
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

                        // ===== P6 ADVANCED PATTERN EVENTS =====

                        case 'loop_start':
                            handleLoopStart(payload);
                            break;
                        case 'loop_end':
                            handleLoopEnd(payload);
                            break;
                        case 'loop_iteration_result':
                            handleLoopIterationResult(payload);
                            break;
                        case 'graph_transition':
                            handleGraphTransition(payload);
                            break;
                        case 'graph_agent_call':
                            if (currentRound) {
                                addTimelineRow('phase', 'Agent @ ' + (payload.state || ''),
                                    payload.agent || '', 'running');
                            }
                            break;
                        case 'roundtable_start':
                            handleRoundtableStart(payload);
                            break;
                        case 'round_start':
                            if (currentRound) {
                                addTimelineRow('phase', 'Round ' + (payload.round || '?'), '', 'running');
                            }
                            break;
                        case 'round_end':
                            if (currentRound) {
                                addTimelineRow('phase', 'Round End', '', 'ok');
                            }
                            break;
                        case 'round_message':
                            handleRoundMessage(payload);
                            break;
                        case 'roundtable_summary':
                            if (currentRound) {
                                addTimelineRow('phase', 'Summary',
                                    truncate(payload.content || '', 80), 'ok');
                            }
                            break;
                        case 'task_delegate':
                            handleTaskDelegate(payload);
                            break;
                        case 'task_start':
                            if (currentRound) {
                                addTimelineRow('phase', 'Task: ' + (payload.agent || ''), '...', 'running');
                            }
                            break;
                        case 'task_end':
                            handleTaskEnd(payload);
                            break;
                        case 'task_aggregate':
                            if (currentRound) {
                                addTimelineRow('phase', 'Aggregate',
                                    (payload.totalTasks || 0) + ' tasks', 'ok');
                            }
                            break;

                        // ===== HITL APPROVAL EVENT =====

                        case 'pending_approval':
                            completeThinkingBox();
                            isStreaming = false;
                            setStreamingState(false);
                            endRound('pending_approval');

                            var approvalCard = createApprovalCard(payload);
                            chatMessages.appendChild(approvalCard);
                            scrollToBottom(chatMessages);
                            break;

                        // ===== STRUCTURED OUTPUT EVENT =====

                        case 'structured_data':
                            if (currentRound) {
                                addTimelineRow('tool', 'Structured Output',
                                    (payload.schemaClass || '').split('.').pop(), 'ok');
                            }
                            var dataCard = createStructuredDataCard(
                                payload.schemaClass || '', payload.data || '{}');
                            if (agentBubble) {
                                var msgWrapper = agentBubble.closest('.message');
                                if (msgWrapper) {
                                    msgWrapper.querySelector('.message-content').appendChild(dataCard);
                                }
                            } else {
                                agentBubble = addAgentBubble();
                                agentBubble.style.display = 'none';
                                var msgWrapper2 = agentBubble.closest('.message');
                                if (msgWrapper2) {
                                    msgWrapper2.querySelector('.message-content').appendChild(dataCard);
                                }
                            }
                            scrollToBottom(chatMessages);
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

function clearAllMedia() {
    window.uploadedFile = null;
    window.uploadedImages = [];
    window.uploadedAudio = null;
    document.getElementById('fileTagArea').innerHTML = '';
}

function stopStreaming() {
    if (currentAbortController) {
        currentAbortController.abort();
        currentAbortController = null;
    }
    isStreaming = false;
    setStreamingState(false);
}

/* ===== HITL APPROVAL ===== */

window.submitApproval = async function(approvalId, approved) {
    var statusEl = document.getElementById('approval-status-' + approvalId);
    if (statusEl) {
        statusEl.innerHTML = '<span class="approval-status-dot"></span>' + (approved ? '已批准，正在继续执行' : '已拒绝');
    }

    // Disable buttons
    var card = document.getElementById('approval-card-' + approvalId);
    if (card) {
        card.classList.add('approval-collapsed');
        card.classList.add(approved ? 'approval-approved' : 'approval-rejected');
        var header = card.querySelector('.approval-header');
        if (header) {
            header.setAttribute('aria-expanded', 'false');
        }
        var btns = card.querySelectorAll('.approval-btn');
        btns.forEach(function(b) { b.disabled = true; });
    }

    currentAbortController = new AbortController();
    setStreamingState(true);
    roundNumber++;
    startRound(approved ? 'Approval: Approve' : 'Approval: Reject', roundNumber, currentAgent, agents);
    showTypingIndicator();
    window.currentAgentMessageWrapper = null;
    window.currentThinkingBox = null;
    window.thinkingContent = '';

    try {
        var response = await fetch('/chat/approve', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ approvalId: approvalId, approved: approved }),
            signal: currentAbortController.signal
        });

        var reader = response.body.getReader();
        var decoder = new TextDecoder();
        var parser = createSSEParser();
        var agentBubble = null;
        var agentRawMarkdown = '';

        while (true) {
            var result = await reader.read();
            if (result.done) break;

            parser.parse(decoder.decode(result.value), function(evt) {
                if (evt.event === 'message' && evt.data) {
                    var payload;
                    try { payload = JSON.parse(evt.data); } catch (e) { return; }

                    switch (payload.type) {
                        case 'text':
                            if (!agentBubble) {
                                removeTypingIndicator();
                                agentBubble = card ? addAgentBubbleAfter(card) : addAgentBubble();
                                agentRawMarkdown = '';
                            }
                            agentRawMarkdown += (payload.content || '');
                            agentBubble.classList.add('md-render');
                            agentBubble.innerHTML = renderMarkdown(agentRawMarkdown);
                            scrollToBottom(chatMessages);
                            break;
                        case 'done':
                            completeThinkingBox();
                            removeTypingIndicator();
                            if (approved && statusEl) {
                                statusEl.innerHTML = '<span class="approval-status-dot"></span>已批准';
                            }
                            isStreaming = false;
                            setStreamingState(false);
                            endRound('success');
                            scrollToBottom(chatMessages);
                            break;
                        case 'error':
                            completeThinkingBox();
                            removeTypingIndicator();
                            if (!agentBubble) agentBubble = card ? addAgentBubbleAfter(card) : addAgentBubble();
                            agentBubble.textContent += '\n\n[ERROR] ' + (payload.message || 'Unknown error');
                            endRound('error');
                            isStreaming = false;
                            setStreamingState(false);
                            break;
                        case 'structured_data':
                            var dataCard = createStructuredDataCard(
                                payload.schemaClass || '', payload.data || '{}');
                            if (agentBubble) {
                                var mw = agentBubble.closest('.message');
                                if (mw) mw.querySelector('.message-content').appendChild(dataCard);
                            }
                            scrollToBottom(chatMessages);
                            break;
                    }
                }
            });
        }
    } catch (err) {
        completeThinkingBox();
        removeTypingIndicator();
        endRound('error');
        setStreamingState(false);
    } finally {
        currentAbortController = null;
    }
};

window.toggleApprovalCard = function(approvalId) {
    var card = document.getElementById('approval-card-' + approvalId);
    if (!card) return;
    card.classList.toggle('approval-collapsed');
    var header = card.querySelector('.approval-header');
    if (header) {
        header.setAttribute('aria-expanded', String(!card.classList.contains('approval-collapsed')));
    }
};

/* ===== UI COMPONENTS ===== */

function createApprovalCard(data) {
    var card = document.createElement('div');
    card.className = 'message agent approval-card-wrapper';
    card.id = 'approval-card-' + data.approvalId;

    var toolListHtml = (data.toolCalls || []).map(function(tc) {
        var inputParams = getApprovalInputParams(tc);
        return '<div class="approval-tool-item">' +
            '<div class="approval-tool-title">' +
                '<span class="approval-tool-name">' + escapeHtml(getApprovalToolLabel(tc.name || '')) + '</span>' +
                '<span class="approval-tool-action">等待人工确认</span>' +
            '</div>' +
            renderApprovalSummary(tc.name || '', inputParams) +
            '<details class="approval-raw">' +
                '<summary>查看原始参数</summary>' +
                '<pre class="approval-tool-params">' + escapeHtml(formatApprovalRawParams(inputParams, tc.input || '{}')) + '</pre>' +
            '</details>' +
            '</div>';
    }).join('');

    card.innerHTML =
        '<div class="message-content">' +
            '<button class="approval-header" type="button" onclick="toggleApprovalCard(\'' +
                data.approvalId + '\')" aria-expanded="true" aria-controls="approval-body-' + data.approvalId + '">' +
                '<span class="approval-icon">&#9888;</span>' +
                '<span class="approval-header-text">请求人工审批</span>' +
                '<span class="approval-toggle" aria-hidden="true">&#9660;</span>' +
            '</button>' +
            '<div class="approval-body" id="approval-body-' + data.approvalId + '">' +
                '<div class="approval-tools">' + toolListHtml + '</div>' +
                '<div class="approval-actions">' +
                    '<button class="approval-btn approve" onclick="submitApproval(\'' +
                        data.approvalId + '\', true)">批准执行</button>' +
                    '<button class="approval-btn reject" onclick="submitApproval(\'' +
                        data.approvalId + '\', false)">拒绝</button>' +
                '</div>' +
            '</div>' +
            '<div class="approval-status" id="approval-status-' + data.approvalId + '"></div>' +
        '</div>';

    return card;
}

function getApprovalInputParams(toolCall) {
    if (toolCall.inputParams && typeof toolCall.inputParams === 'object') {
        return toolCall.inputParams;
    }
    return parseApprovalInputString(toolCall.input || '{}');
}

function parseApprovalInputString(input) {
    if (!input || typeof input !== 'string') return {};
    var trimmed = input.trim();
    if (!trimmed) return {};
    try {
        return JSON.parse(trimmed);
    } catch (e) {
        // AgentScope Map#toString fallback: {key=value, another=value}
    }
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
        trimmed = trimmed.slice(1, -1);
    }
    var result = {};
    var matches = trimmed.matchAll(/(?:^|,\s*)([A-Za-z][A-Za-z0-9_]*)=/g);
    var positions = Array.from(matches);
    positions.forEach(function(match, index) {
        var key = match[1];
        var valueStart = match.index + match[0].length;
        var valueEnd = index + 1 < positions.length ? positions[index + 1].index : trimmed.length;
        result[key] = trimmed.slice(valueStart, valueEnd).trim();
    });
    return result;
}

function renderApprovalSummary(toolName, params) {
    if (toolName === 'generate_contract_review_report') {
        return renderContractReviewApproval(params);
    }

    var rows = Object.keys(params).map(function(key) {
        return renderApprovalField(key, key, params[key]);
    }).join('');
    return '<div class="approval-field-grid">' + rows + '</div>';
}

function renderContractReviewApproval(params) {
    var riskLevel = String(params.overallRiskLevel || '').toUpperCase();
    var riskClass = riskLevel ? ' risk-' + riskLevel.toLowerCase() : '';
    var summary = params.summary || params.risksSummary || '';

    return '<div class="approval-review">' +
        '<div class="approval-decision-strip">' +
            '<span class="approval-decision-label">即将生成报告</span>' +
            (riskLevel ? '<span class="approval-risk-badge' + riskClass + '">' + escapeHtml(riskLevel) + '</span>' : '') +
        '</div>' +
        renderApprovalSection('基本信息', [
            ['合同名称', params.contractTitle],
            ['合同编号', params.contractNumber],
            ['甲方', params.partyA],
            ['乙方', params.partyB],
            ['生效日期', params.effectiveDate],
            ['到期日期', params.expiryDate],
            ['合同金额', formatApprovalAmount(params.totalAmount, params.currency)]
        ]) +
        renderApprovalSection('审查结论', [
            ['摘要', summary],
            ['关键条款', params.keyClausesSummary],
            ['风险与建议', params.risksSummary]
        ], true) +
        '</div>';
}

function renderApprovalSection(title, fields, allowLongText) {
    var rows = fields
        .filter(function(field) { return field[1] !== undefined && field[1] !== null && String(field[1]).trim() !== ''; })
        .map(function(field) { return renderApprovalField(field[0], field[0], field[1], allowLongText); })
        .join('');
    if (!rows) return '';
    return '<section class="approval-section">' +
        '<div class="approval-section-title">' + escapeHtml(title) + '</div>' +
        '<div class="approval-field-grid">' + rows + '</div>' +
        '</section>';
}

function renderApprovalField(label, key, value, allowLongText) {
    var text = typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
    var longClass = allowLongText || text.length > 80 ? ' long' : '';
    return '<div class="approval-field' + longClass + '">' +
        '<div class="approval-field-label">' + escapeHtml(label || key) + '</div>' +
        '<div class="approval-field-value">' + escapeHtml(text) + '</div>' +
        '</div>';
}

function formatApprovalAmount(amount, currency) {
    if (amount === undefined || amount === null || amount === '') return '';
    return String(amount) + (currency ? ' ' + String(currency) : '');
}

function getApprovalToolLabel(toolName) {
    var labels = {
        generate_contract_review_report: '生成合同审查报告',
        generate_bank_invoice: '生成银行发票'
    };
    return labels[toolName] || toolName;
}

function formatApprovalRawParams(params, fallback) {
    if (params && Object.keys(params).length > 0) {
        return JSON.stringify(params, null, 2);
    }
    return fallback;
}

function createStructuredDataCard(schemaClass, dataJson) {
    var card = document.createElement('div');
    card.className = 'structured-data-card';

    var data;
    try { data = JSON.parse(dataJson); } catch(e) { data = {}; }

    var className = schemaClass.split('.').pop();

    var tableHtml = '<table class="structured-data-table">';
    for (var key in data) {
        if (data.hasOwnProperty(key)) {
            var value = data[key];
            if (Array.isArray(value)) {
                value = '<pre>' + escapeHtml(JSON.stringify(value, null, 2)) + '</pre>';
            } else if (typeof value === 'object' && value !== null) {
                value = '<pre>' + escapeHtml(JSON.stringify(value, null, 2)) + '</pre>';
            } else {
                value = escapeHtml(String(value != null ? value : ''));
            }
            tableHtml += '<tr><td class="sdk-key">' + escapeHtml(key) + '</td><td>' + value + '</td></tr>';
        }
    }
    tableHtml += '</table>';

    card.innerHTML =
        '<div class="structured-data-header" onclick="this.parentElement.classList.toggle(\'collapsed\')">' +
            '<span class="structured-data-icon">{ }</span>' +
            '<span class="structured-data-label">' + escapeHtml(className) + '</span>' +
            '<span class="structured-data-toggle">&#9660;</span>' +
        '</div>' +
        '<div class="structured-data-body">' + tableHtml + '</div>';

    return card;
}

/* ===== UTILITY FUNCTIONS ===== */
function truncate(str, maxLen) {
    if (!str) return '';
    return str.length > maxLen ? str.substring(0, maxLen) + '...' : str;
}

/* ===== GLOBAL FUNCTIONS FOR ONCLICK ===== */
window.sendMessage = sendMessage;
window.clearSession = clearSessionFn;
window.toggleDebug = toggleDebug;
window.clearDebug = clearDebug;

/* ===== INIT ===== */
initUpload();
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
