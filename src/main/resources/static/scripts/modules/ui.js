import { renderMarkdown, escapeHtml, getTimestamp, scrollToBottom, createFileList } from './utils.js';

/* ===== DOM REFERENCES ===== */
export const chatMessages = document.getElementById('chatMessages');
export const messageInput = document.getElementById('messageInput');
export const sendBtn = document.getElementById('sendBtn');
export const chatEmpty = document.getElementById('chatEmpty');
export const chatHeaderName = document.getElementById('chatHeaderName');
export const chatHeaderDesc = document.getElementById('chatHeaderDesc');
export const debugPanel = document.getElementById('debugPanel');
export const debugRounds = document.getElementById('debugRounds');
export const debugToggle = document.getElementById('debugToggle');

/* ===== MESSAGE RENDERING ===== */
export function appendMessage(role, text, fileInfo) {
    var wrapper = document.createElement('div');
    wrapper.className = 'message ' + role;

    var avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    if (role === 'user') {
        avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/></svg>';
    } else {
        avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>';
    }

    var content = document.createElement('div');
    content.className = 'message-content';

    // Handle media info (can be fileInfo object or mediaInfo object with file/images/audio)
    if (fileInfo && role === 'user') {
        // Handle legacy fileInfo (document file)
        if (fileInfo.fileName && !fileInfo.images && !fileInfo.audio) {
            var fileListNode = document.createElement('div');
            fileListNode.innerHTML = createFileList(fileInfo);
            content.appendChild(fileListNode.firstChild);
        }
        // Handle new mediaInfo structure
        else {
            // Document file
            if (fileInfo.file) {
                var fileListNode = document.createElement('div');
                fileListNode.innerHTML = createFileList(fileInfo.file);
                content.appendChild(fileListNode.firstChild);
            }
            // Images
            if (fileInfo.images && fileInfo.images.length > 0) {
                var imagesContainer = document.createElement('div');
                imagesContainer.className = 'message-images';
                fileInfo.images.forEach(function(img) {
                    var imgWrapper = document.createElement('div');
                    imgWrapper.className = 'message-image-wrapper';
                    var imgEl = document.createElement('img');
                    imgEl.src = '/chat/download?fileId=' + img.fileId;
                    imgEl.alt = escapeHtml(img.fileName);
                    imgEl.onclick = function() {
                        showImageModal(img); // Use image from array
                    };
                    imgWrapper.appendChild(imgEl);
                    imagesContainer.appendChild(imgWrapper);
                });
                content.appendChild(imagesContainer);
            }
            // Audio
            if (fileInfo.audio) {
                var audioContainer = document.createElement('div');
                audioContainer.className = 'message-audio';
                var audioEl = document.createElement('audio');
                audioEl.controls = true;
                audioEl.src = '/chat/download?fileId=' + fileInfo.audio.fileId;
                var audioLabel = document.createElement('div');
                audioLabel.className = 'message-audio-label';
                audioLabel.textContent = escapeHtml(fileInfo.audio.fileName);
                audioContainer.appendChild(audioEl);
                audioContainer.appendChild(audioLabel);
                content.appendChild(audioContainer);
            }
        }
    }

    var bubble = document.createElement('div');
    bubble.className = 'message-bubble';
    if (role === 'agent') {
        bubble.classList.add('md-render');
        bubble.innerHTML = renderMarkdown(text);
    } else {
        bubble.textContent = text;
    }

    var time = document.createElement('div');
    time.className = 'message-time';
    time.textContent = getTimestamp();

    content.appendChild(bubble);
    content.appendChild(time);
    wrapper.appendChild(avatar);
    wrapper.appendChild(content);
    chatMessages.appendChild(wrapper);
    scrollToBottom(chatMessages);

    return bubble;
}

/* ===== THINKING BOX MANAGEMENT ===== */
export function createThinkingBox(fileInfo) {
    var wrapper = document.createElement('div');
    wrapper.className = 'message agent';
    wrapper.style.maxWidth = '100%';
    wrapper.style.width = '100%';

    var avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>';

    var content = document.createElement('div');
    content.className = 'agent-response-wrapper';

    if (fileInfo) {
        var fileListHtml = createFileList(fileInfo);
        if (fileListHtml) {
            var fileListNode = document.createElement('div');
            fileListNode.innerHTML = fileListHtml;
            if (fileListNode.firstChild) {
                content.appendChild(fileListNode.firstChild);
            }
        }
    }

    var box = document.createElement('div');
    box.className = 'thinking-box';
    box.innerHTML = '<div class="thinking-header" onclick="toggleThinking(this)">' +
        '<div class="thinking-header-icon"><div class="thinking-spinner"></div></div>' +
        '<div class="thinking-header-text">Thinking</div>' +
        '</div>' +
        '<div class="thinking-content"></div>';

    content.appendChild(box);
    wrapper.appendChild(avatar);
    wrapper.appendChild(content);
    chatMessages.appendChild(wrapper);

    window.currentAgentMessageWrapper = wrapper;
    window.currentThinkingBox = box;
    scrollToBottom(chatMessages);
    return box;
}

export function updateThinkingBox(content, fileInfo) {
    if (!window.currentThinkingBox) {
        createThinkingBox(fileInfo || window.currentFileInfo);
    }
    window.thinkingContent += content;
    var lines = window.thinkingContent.split('\n');
    var filteredLines = lines.filter(function(line) {
        var trimmed = line.trim();
        return trimmed !== '' && !trimmed.includes('__fragment__');
    });
    var contentEl = window.currentThinkingBox.querySelector('.thinking-content');
    contentEl.textContent = filteredLines.join('\n\n');
    contentEl.scrollTop = contentEl.scrollHeight;
    scrollToBottom(chatMessages);
}

export function collapseThinkingBox() {
    if (window.currentThinkingBox && !window.currentThinkingBox.classList.contains('collapsed')) {
        window.currentThinkingBox.classList.add('collapsed');
        window.currentThinkingBox.classList.add('completed');
    }
}

export function completeThinkingBox() {
    if (window.currentThinkingBox) {
        window.currentThinkingBox.classList.add('completed');
        window.currentThinkingBox.classList.add('collapsed');
        window.thinkingContent = '';
        window.currentThinkingBox = null;
        window.currentFileInfo = null;
    }
}

// Global function for onclick attribute
window.toggleThinking = function(header) {
    var box = header.parentElement;
    box.classList.toggle('collapsed');
};

export function createAgentMessageWrapper() {
    var wrapper = document.createElement('div');
    wrapper.className = 'message agent';
    wrapper.style.maxWidth = '100%';
    wrapper.style.width = '100%';

    var avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>';

    var content = document.createElement('div');
    content.className = 'agent-response-wrapper';

    wrapper.appendChild(avatar);
    wrapper.appendChild(content);
    chatMessages.appendChild(wrapper);

    window.currentAgentMessageWrapper = wrapper;
    scrollToBottom(chatMessages);
    return wrapper;
}

export function addAgentBubble() {
    if (!window.currentAgentMessageWrapper) {
        createAgentMessageWrapper();
    }
    var content = window.currentAgentMessageWrapper.querySelector('.agent-response-wrapper');

    var bubbleContent = document.createElement('div');
    bubbleContent.className = 'message-content';

    var bubble = document.createElement('div');
    bubble.className = 'message-bubble';

    var time = document.createElement('div');
    time.className = 'message-time';
    time.textContent = getTimestamp();

    bubbleContent.appendChild(bubble);
    bubbleContent.appendChild(time);
    content.appendChild(bubbleContent);

    scrollToBottom(chatMessages);
    return bubble;
}

export function removeTypingIndicator() {
    var el = document.getElementById('typingIndicator');
    if (el) el.remove();
}

export function setStreamingState(streaming) {
    window.isStreaming = streaming;
    messageInput.disabled = streaming;
    sendBtn.disabled = streaming;
    if (!streaming) {
        messageInput.focus();
    }
}

/* ===== IMAGE MODAL ===== */
export function showImageModal(img) {
    var overlay = document.createElement('div');
    overlay.className = 'image-modal-overlay';
    overlay.id = 'imageModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) closeImageModal();
    };

    overlay.innerHTML =
        '<div class="image-modal-content">' +
            '<button class="image-modal-close" onclick="closeImageModal()">×</button>' +
            '<img src="/chat/download?fileId=' + img.fileId + '" alt="' + escapeHtml(img.fileName) + '">' +
            '<div class="image-modal-filename">' + escapeHtml(img.fileName) + '</div>' +
        '</div>';

    document.body.appendChild(overlay);
}

// Global function for onclick
window.closeImageModal = function() {
    var modal = document.getElementById('imageModal');
    if (modal) {
        modal.remove();
    }
};

/* ===== TYPING INDICATOR ===== */
export function showTypingIndicator() {
    var typingEl = document.createElement('div');
    typingEl.className = 'typing-indicator';
    typingEl.id = 'typingIndicator';
    typingEl.innerHTML = '<div class="message-avatar"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg></div><div class="dots"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>';
    chatMessages.appendChild(typingEl);
    scrollToBottom(chatMessages);
}
