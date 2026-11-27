/* ===== API TOKEN AUTH (P0-1 security fix) =====
 * The backend now requires a shared API token on /chat/**, /api/**, /channel/**,
 * /a2a/** and /ag-ui/** (agentscope.security.*). The token is read from
 * localStorage (key: agentscopeApiToken). When it is missing or rejected (401),
 * the user is prompted once and it is stored for subsequent requests.
 * For a stable token, start the server with AGENTSCOPE_SECURITY_API_TOKEN=<value>;
 * otherwise the server generates one and logs it at startup.
 */
export function getApiToken() {
    return localStorage.getItem('agentscopeApiToken') || '';
}

export function storeApiToken(token) {
    if (token) {
        localStorage.setItem('agentscopeApiToken', token);
    }
}

function promptForToken() {
    var token = window.prompt(
        '服务端已启用 API 认证（P0-1 修复）。\n' +
        '请输入 API Token（见服务端启动日志中的 "generated a random token" 或 AGENTSCOPE_SECURITY_API_TOKEN）：', '') || '';
    storeApiToken(token.trim());
    return getApiToken();
}

export function authHeaders(extra) {
    var headers = extra ? Object.assign({}, extra) : {};
    var token = getApiToken();
    if (token) {
        headers['X-API-Token'] = token;
    }
    return headers;
}

/**
 * Browser-navigation download URL (img/audio src): the token must travel as a
 * query parameter because <img>/<audio> requests cannot set custom headers.
 */
export function downloadUrl(fileId) {
    var url = '/chat/download?fileId=' + encodeURIComponent(fileId);
    var token = getApiToken();
    if (token) {
        url += '&apiToken=' + encodeURIComponent(token);
    }
    return url;
}

/** fetch wrapper: attaches the token and re-prompts once on 401. */
export async function authFetch(url, options, retried) {
    options = options ? Object.assign({}, options) : {};
    options.headers = authHeaders(options.headers);
    var response = await fetch(url, options);
    if (response.status === 401 && !retried) {
        var token = promptForToken();
        if (token) {
            return await authFetch(url, options, true);
        }
    }
    return response;
}

/* ===== SSE PARSER ===== */
export function createSSEParser() {
    var buffer = '';
    var currentEvent = {};
    return {
        parse: function(chunk, onEvent) {
            buffer += chunk;
            var lines = buffer.split('\n');
            buffer = lines.pop();
            for (var i = 0; i < lines.length; i++) {
                var line = lines[i];
                if (line.startsWith('data:')) {
                    currentEvent.data = line.slice(5).trim();
                } else if (line.startsWith('event:')) {
                    currentEvent.event = line.slice(6).trim();
                } else if (line === '' || line === '\r') {
                    if (currentEvent.data) {
                        onEvent(currentEvent);
                    }
                    currentEvent = {};
                }
            }
        }
    };
}

/* ===== FILE UPLOAD API ===== */
export async function uploadFile(file) {
    console.log('[api] uploadFile called for:', file.name);
    var formData = new FormData();
    formData.append('file', file);

    var response = await authFetch('/chat/upload', {
        method: 'POST',
        body: formData
    });
    console.log('[api] upload response status:', response.status);
    var result = await response.json();
    console.log('[api] upload response data:', result);
    return result;
}

/* ===== AGENT LIST API ===== */
export async function fetchAgents() {
    var response = await authFetch('/api/agents');
    return await response.json();
}

export async function fetchSamplePrompt(agentId, index) {
    var response = await authFetch('/api/agents/' + encodeURIComponent(agentId) + '/sample-prompts/' + index);
    if (!response.ok) {
        var err = await response.json();
        throw new Error(err.error || 'Sample prompt not found');
    }
    return await response.json();
}

/* ===== SESSION API ===== */
export async function fetchSessions() {
    var response = await authFetch('/api/sessions');
    return await response.json();
}

export async function createSession(agentId) {
    var response = await authFetch('/api/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ agentId: agentId })
    });
    return await response.json();
}

export async function deleteSession(sessionId) {
    return await authFetch('/api/sessions/' + sessionId, { method: 'DELETE' });
}

export async function fetchAgentMessages(agentId) {
    var response = await authFetch('/api/agents/' + encodeURIComponent(agentId) + '/messages');
    return await response.json();
}

/* ===== KNOWLEDGE API ===== */
export async function fetchKnowledgeDocs() {
    var response = await authFetch('/api/knowledge/documents');
    return await response.json();
}

export async function uploadKnowledgeDoc(file) {
    var formData = new FormData();
    formData.append('file', file);
    var response = await authFetch('/api/knowledge/upload', {
        method: 'POST',
        body: formData
    });
    return await response.json();
}

export async function removeKnowledgeDoc(fileName) {
    return await authFetch('/api/knowledge/documents/' + encodeURIComponent(fileName), { method: 'DELETE' });
}

export async function fetchKnowledgeStatus() {
    var response = await authFetch('/api/knowledge/status');
    return await response.json();
}

/* ===== SKILL/TOOL INFO API ===== */
export async function fetchSkillInfo(skillName) {
    var response = await authFetch('/api/skills/' + skillName);
    if (!response.ok) {
        var err = await response.json();
        throw new Error(err.error || 'Skill not found');
    }
    return await response.json();
}

export async function fetchToolInfo(toolName) {
    var response = await authFetch('/api/tools/' + toolName);
    if (!response.ok) {
        var err = await response.json();
        throw new Error(err.error || 'Tool not found');
    }
    return await response.json();
}
