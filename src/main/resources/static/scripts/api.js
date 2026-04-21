/* ===== SSE PARSER ===== */
export function createSSEParser() {
    var buffer = '';
    return {
        parse: function(chunk, onEvent) {
            buffer += chunk;
            var lines = buffer.split('\n');
            buffer = lines.pop();
            var currentEvent = {};
            for (var i = 0; i < lines.length; i++) {
                var line = lines[i];
                if (line.startsWith('data:')) {
                    currentEvent.data = line.slice(5).trim();
                } else if (line.startsWith('event:')) {
                    currentEvent.event = line.slice(6).trim();
                } else if (line === '') {
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
    var formData = new FormData();
    formData.append('file', file);

    var response = await fetch('/chat/upload', {
        method: 'POST',
        body: formData
    });
    return await response.json();
}

/* ===== AGENT LIST API ===== */
export async function fetchAgents() {
    var response = await fetch('/api/agents');
    return await response.json();
}

/* ===== SESSION API ===== */
export async function fetchSessions() {
    var response = await fetch('/api/sessions');
    return await response.json();
}

export async function createSession(agentId) {
    var response = await fetch('/api/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ agentId: agentId })
    });
    return await response.json();
}

export async function deleteSession(sessionId) {
    return await fetch('/api/sessions/' + sessionId, { method: 'DELETE' });
}

/* ===== KNOWLEDGE API ===== */
export async function fetchKnowledgeDocs() {
    var response = await fetch('/api/knowledge/documents');
    return await response.json();
}

export async function uploadKnowledgeDoc(file) {
    var formData = new FormData();
    formData.append('file', file);
    var response = await fetch('/api/knowledge/upload', {
        method: 'POST',
        body: formData
    });
    return await response.json();
}

export async function removeKnowledgeDoc(fileName) {
    return await fetch('/api/knowledge/documents/' + encodeURIComponent(fileName), { method: 'DELETE' });
}

/* ===== SKILL/TOOL INFO API ===== */
export async function fetchSkillInfo(skillName) {
    var response = await fetch('/api/skills/' + skillName);
    if (!response.ok) {
        var err = await response.json();
        throw new Error(err.error || 'Skill not found');
    }
    return await response.json();
}

export async function fetchToolInfo(toolName) {
    var response = await fetch('/api/tools/' + toolName);
    if (!response.ok) {
        var err = await response.json();
        throw new Error(err.error || 'Tool not found');
    }
    return await response.json();
}
