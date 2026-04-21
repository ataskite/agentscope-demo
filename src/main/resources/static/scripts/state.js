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
