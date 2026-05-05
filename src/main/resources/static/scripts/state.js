/* ===== STATE ===== */
const state = window.__agentScopeState || (window.__agentScopeState = {
    currentAgent: null,
    isStreaming: false,
    currentAbortController: null,
    messageCount: 0,
    uploadedFile: null,
    uploadedImages: [],  // Array of {fileId, fileName, filePath, fileType}
    uploadedAudio: null,  // {fileId, fileName, filePath, fileType}
    agentRawMarkdown: '',  // Accumulated raw markdown for current agent response
    currentSessionId: null,  // Active session ID
    agents: {},
    rounds: [],
    currentRound: null,
    roundNumber: 0,
    currentThinkingBox: null,
    currentAgentMessageWrapper: null,
    thinkingContent: '',
    currentFileInfo: null
});

let currentAgent = state.currentAgent;
let isStreaming = state.isStreaming;
let currentAbortController = state.currentAbortController;
let messageCount = state.messageCount;
let uploadedFile = state.uploadedFile;
let uploadedImages = state.uploadedImages;
let uploadedAudio = state.uploadedAudio;
let agentRawMarkdown = state.agentRawMarkdown;
let currentSessionId = state.currentSessionId;

const agents = state.agents;

/* ===== OBSERVABILITY STATE (per-round) ===== */
var rounds = state.rounds;
var currentRound = state.currentRound;
var roundNumber = state.roundNumber;

/* ===== THINKING BOX STATE ===== */
var currentThinkingBox = state.currentThinkingBox;
var currentAgentMessageWrapper = state.currentAgentMessageWrapper;
var thinkingContent = state.thinkingContent;
var currentFileInfo = state.currentFileInfo;

// Expose to window with getters/setters for sync
function defineWindowStateProperty(name, descriptor) {
    var existing = Object.getOwnPropertyDescriptor(window, name);
    if (existing && existing.configurable === false) {
        return;
    }
    Object.defineProperty(window, name, Object.assign({ configurable: true }, descriptor));
}

defineWindowStateProperty('agents', {
    get: function() { return agents; },
    set: function(val) { /* read-only */ }
});

defineWindowStateProperty('currentAgent', {
    get: function() { return state.currentAgent; },
    set: function(val) { state.currentAgent = val; currentAgent = val; }
});

defineWindowStateProperty('isStreaming', {
    get: function() { return state.isStreaming; },
    set: function(val) { state.isStreaming = val; isStreaming = val; }
});

defineWindowStateProperty('currentAbortController', {
    get: function() { return state.currentAbortController; },
    set: function(val) { state.currentAbortController = val; currentAbortController = val; }
});

defineWindowStateProperty('messageCount', {
    get: function() { return state.messageCount; },
    set: function(val) { state.messageCount = val; messageCount = val; }
});

defineWindowStateProperty('uploadedFile', {
    get: function() { return state.uploadedFile; },
    set: function(val) { state.uploadedFile = val; uploadedFile = val; }
});

defineWindowStateProperty('uploadedImages', {
    get: function() { return state.uploadedImages; },
    set: function(val) { state.uploadedImages = val; uploadedImages = val; }
});

defineWindowStateProperty('uploadedAudio', {
    get: function() { return state.uploadedAudio; },
    set: function(val) { state.uploadedAudio = val; uploadedAudio = val; }
});

defineWindowStateProperty('agentRawMarkdown', {
    get: function() { return state.agentRawMarkdown; },
    set: function(val) { state.agentRawMarkdown = val; agentRawMarkdown = val; }
});

defineWindowStateProperty('currentSessionId', {
    get: function() { return state.currentSessionId; },
    set: function(val) { state.currentSessionId = val; currentSessionId = val; }
});

defineWindowStateProperty('rounds', {
    get: function() { return state.rounds; },
    set: function(val) { state.rounds = val; rounds = val; }
});

defineWindowStateProperty('currentRound', {
    get: function() { return state.currentRound; },
    set: function(val) { state.currentRound = val; currentRound = val; }
});

defineWindowStateProperty('roundNumber', {
    get: function() { return state.roundNumber; },
    set: function(val) { state.roundNumber = val; roundNumber = val; }
});

defineWindowStateProperty('currentThinkingBox', {
    get: function() { return state.currentThinkingBox; },
    set: function(val) { state.currentThinkingBox = val; currentThinkingBox = val; }
});

defineWindowStateProperty('currentAgentMessageWrapper', {
    get: function() { return state.currentAgentMessageWrapper; },
    set: function(val) { state.currentAgentMessageWrapper = val; currentAgentMessageWrapper = val; }
});

defineWindowStateProperty('thinkingContent', {
    get: function() { return state.thinkingContent; },
    set: function(val) { state.thinkingContent = val; thinkingContent = val; }
});

defineWindowStateProperty('currentFileInfo', {
    get: function() { return state.currentFileInfo; },
    set: function(val) { state.currentFileInfo = val; currentFileInfo = val; }
});

// Export for use in other modules (optional, for transparency)
export { currentAgent, isStreaming, currentAbortController, messageCount, uploadedFile, uploadedImages, uploadedAudio, agentRawMarkdown, currentSessionId, agents, rounds, currentRound, roundNumber, currentThinkingBox, currentAgentMessageWrapper, thinkingContent, currentFileInfo };
