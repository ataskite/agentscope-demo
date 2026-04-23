import { fetchKnowledgeDocs, uploadKnowledgeDoc, removeKnowledgeDoc as removeKnowledgeDocApi } from '../api.js';
import { escapeHtml } from './utils.js';

/* ===== KNOWLEDGE MANAGEMENT ===== */
export async function loadKnowledgeDocs() {
    try {
        var docs = await fetchKnowledgeDocs();
        var docsEl = document.getElementById('knowledgeDocs');
        if (!docsEl) return;  // Element not in current page

        docsEl.innerHTML = '';

        if (docs.length === 0) {
            docsEl.innerHTML = '<div class="knowledge-empty">No documents</div>';
            return;
        }

        docs.forEach(function(name) {
            var item = document.createElement('div');
            item.className = 'knowledge-doc-item';
            item.innerHTML =
                '<span class="knowledge-doc-name">' + escapeHtml(name) + '</span>' +
                '<button class="session-item-delete" onclick="removeKnowledgeDoc(\'' + escapeHtml(name).replace(/'/g, "\\'") + '\')">×</button>';
            docsEl.appendChild(item);
        });
    } catch (err) {
        console.error('Failed to load knowledge docs', err);
    }
}

export async function uploadToKnowledge(input) {
    var file = input.files[0];
    if (!file) return;

    try {
        var data = await uploadKnowledgeDoc(file);
        if (data.error) {
            console.error('Knowledge upload error:', data.error);
            return;
        }
        loadKnowledgeDocs();
    } catch (err) {
        console.error('Knowledge upload failed', err);
    }

    input.value = '';
}

export async function removeKnowledgeDoc(fileName) {
    try {
        await removeKnowledgeDocApi(fileName);
        loadKnowledgeDocs();
    } catch (err) {
        console.error('Failed to remove knowledge doc', err);
    }
}

// Global function for onclick
window.removeKnowledgeDoc = removeKnowledgeDoc;
