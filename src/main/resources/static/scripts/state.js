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

// Expose to window with getters/setters for sync
Object.defineProperty(window, 'agents', {
    get: function() { return agents; },
    set: function(val) { /* read-only */ }
});

Object.defineProperty(window, 'currentAgent', {
    get: function() { return currentAgent; },
    set: function(val) { currentAgent = val; }
});

Object.defineProperty(window, 'isStreaming', {
    get: function() { return isStreaming; },
    set: function(val) { isStreaming = val; }
});

Object.defineProperty(window, 'currentAbortController', {
    get: function() { return currentAbortController; },
    set: function(val) { currentAbortController = val; }
});

Object.defineProperty(window, 'messageCount', {
    get: function() { return messageCount; },
    set: function(val) { messageCount = val; }
});

Object.defineProperty(window, 'uploadedFile', {
    get: function() { return uploadedFile; },
    set: function(val) { uploadedFile = val; }
});

Object.defineProperty(window, 'uploadedImages', {
    get: function() { return uploadedImages; },
    set: function(val) { uploadedImages = val; }
});

Object.defineProperty(window, 'uploadedAudio', {
    get: function() { return uploadedAudio; },
    set: function(val) { uploadedAudio = val; }
});

Object.defineProperty(window, 'agentRawMarkdown', {
    get: function() { return agentRawMarkdown; },
    set: function(val) { agentRawMarkdown = val; }
});

Object.defineProperty(window, 'currentSessionId', {
    get: function() { return currentSessionId; },
    set: function(val) { currentSessionId = val; }
});

Object.defineProperty(window, 'rounds', {
    get: function() { return rounds; },
    set: function(val) { rounds = val; }
});

Object.defineProperty(window, 'currentRound', {
    get: function() { return currentRound; },
    set: function(val) { currentRound = val; }
});

Object.defineProperty(window, 'roundNumber', {
    get: function() { return roundNumber; },
    set: function(val) { roundNumber = val; }
});

Object.defineProperty(window, 'currentThinkingBox', {
    get: function() { return currentThinkingBox; },
    set: function(val) { currentThinkingBox = val; }
});

Object.defineProperty(window, 'currentAgentMessageWrapper', {
    get: function() { return currentAgentMessageWrapper; },
    set: function(val) { currentAgentMessageWrapper = val; }
});

Object.defineProperty(window, 'thinkingContent', {
    get: function() { return thinkingContent; },
    set: function(val) { thinkingContent = val; }
});

Object.defineProperty(window, 'currentFileInfo', {
    get: function() { return currentFileInfo; },
    set: function(val) { currentFileInfo = val; }
});

// Export for use in other modules (optional, for transparency)
export { currentAgent, isStreaming, currentAbortController, messageCount, uploadedFile, uploadedImages, uploadedAudio, agentRawMarkdown, currentSessionId, agents, rounds, currentRound, roundNumber, currentThinkingBox, currentAgentMessageWrapper, thinkingContent, currentFileInfo };
