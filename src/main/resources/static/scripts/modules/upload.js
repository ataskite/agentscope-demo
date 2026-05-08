import { uploadFile } from '../api.js?v=2.5';
import { escapeHtml } from './utils.js';

/* ===== INIT: bind upload events programmatically ===== */
export function initUpload() {
    var uploadBtn = document.getElementById('uploadBtn');
    var fileInput = document.getElementById('fileInput');
    if (!uploadBtn || !fileInput) {
        console.error('[upload] uploadBtn or fileInput not found');
        return;
    }
    uploadBtn.addEventListener('click', function() {
        fileInput.click();
    });
    fileInput.addEventListener('change', function() {
        handleFileSelect(fileInput);
    });
    console.log('[upload] events bound');
}

/* ===== FILE UPLOAD ===== */
async function handleFileSelect(input) {
    console.log('[upload] handleFileSelect called', input);
    var file = input.files[0];
    if (!file) {
        console.log('[upload] No file selected');
        return;
    }

    console.log('[upload] File selected:', file.name, file.size, file.type);
    var lowerName = file.name.toLowerCase();

    // Check file type
    var isDoc = lowerName.endsWith('.docx') || lowerName.endsWith('.pdf') || lowerName.endsWith('.xlsx');
    var isImage = lowerName.endsWith('.jpg') || lowerName.endsWith('.jpeg') ||
                  lowerName.endsWith('.png') || lowerName.endsWith('.gif') ||
                  lowerName.endsWith('.webp');
    var isAudio = lowerName.endsWith('.wav') || lowerName.endsWith('.mp3') ||
                  lowerName.endsWith('.m4a') || lowerName.endsWith('.mp4');

    if (!isDoc && !isImage && !isAudio) {
        input.value = '';
        return;
    }

    try {
        console.log('[upload] Starting upload for:', file.name);
        var data = await uploadFile(file);
        console.log('[upload] Upload response:', data);
        if (data.error) {
            console.error('[upload] Server error:', data.error);
            return;
        }

        // Handle based on file type — set data FIRST, then suggest agent if needed
        if (isDoc) {
            console.log('[upload] Document uploaded:', data.fileName);
            window.uploadedFile = data;
            showFileTag(data.fileName);

            // Check if current agent has document processing capabilities
            var currentAgentConfig = window.agents[window.currentAgent];
            var hasDocSkill = currentAgentConfig &&
                (currentAgentConfig.skills || []).some(function(s) {
                    return s === 'docx' || s === 'pdf' || s === 'xlsx';
                });

            if (!hasDocSkill) {
                console.log('[upload] Current agent lacks document skills, suggesting task-document-analysis');
                showAgentSuggestionModal('task-document-analysis', '文档分析助手',
                    '当前选择的智能体不支持文档处理，是否切换到「文档分析助手」来处理此文档？');
            } else {
                console.log('[upload] Current agent has document skills, ready to process');
            }
        } else if (isImage) {
            console.log('[upload] Image uploaded:', data.fileName);
            window.uploadedImages.push(data);
            showImagePreviews();

            // Check if current agent supports vision
            var currentAgentConfig = window.agents[window.currentAgent];
            var supportsVision = currentAgentConfig &&
                (currentAgentConfig.modality === 'vision' || currentAgentConfig.modality === 'multi');

            if (!supportsVision && window.agents['vision-analyzer']) {
                console.log('[upload] Current agent does not support vision, suggesting vision-analyzer');
                showAgentSuggestionModal('vision-analyzer', '图片识别助手',
                    '当前选择的智能体不支持图片处理，是否切换到「图片识别助手」来处理此图片？');
            }
        } else if (isAudio) {
            console.log('[upload] Audio uploaded:', data.fileName);
            window.uploadedAudio = data;
            showAudioPreview();

            // Check if current agent supports audio
            var currentAgentConfig = window.agents[window.currentAgent];
            var supportsAudio = currentAgentConfig &&
                (currentAgentConfig.modality === 'audio' || currentAgentConfig.modality === 'multi');

            if (!supportsAudio && window.agents['voice-assistant']) {
                console.log('[upload] Current agent does not support audio, suggesting voice-assistant');
                showAgentSuggestionModal('voice-assistant', '语音助手',
                    '当前选择的智能体不支持语音处理，是否切换到「语音助手」来处理此音频？');
            }
        }
    } catch (err) {
        console.error('[upload] Error:', err);
    } finally {
        input.value = '';
    }
}

function showFileTag(fileName) {
    var area = document.getElementById('fileTagArea');
    area.innerHTML = '<div class="file-tag">' +
        '<span class="file-tag-name">' + escapeHtml(fileName) + '</span>' +
        '<span class="file-tag-remove" onclick="removeFile()">×</span>' +
        '</div>';
}

function showImagePreviews() {
    var area = document.getElementById('fileTagArea');
    var html = '';

    // Show document file if any
    if (window.uploadedFile) {
        html += '<div class="file-tag">' +
            '<span class="file-tag-name">' + escapeHtml(window.uploadedFile.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeFile()">×</span>' +
            '</div>';
    }

    // Show images
    window.uploadedImages.forEach(function(img, index) {
        html += '<div class="file-tag image-tag">' +
            '<span class="file-tag-name image-preview" onclick="showImageModal(' + index + ')">' + escapeHtml(img.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeImage(' + index + ')">×</span>' +
            '</div>';
    });

    // Show audio if any
    if (window.uploadedAudio) {
        html += '<div class="file-tag audio-tag">' +
            '<span class="file-tag-name">' + escapeHtml(window.uploadedAudio.fileName) + '</span>' +
            '<span class="file-tag-remove" onclick="removeAudio()">×</span>' +
            '</div>';
    }

    area.innerHTML = html;
}

function showAudioPreview() {
    showImagePreviews();
}

/* ===== AGENT SUGGESTION MODAL ===== */
function showAgentSuggestionModal(targetAgentId, targetAgentName, message) {
    // Remove existing modal if any
    var existingModal = document.getElementById('agentSuggestionModal');
    if (existingModal) {
        existingModal.remove();
    }

    var modal = document.createElement('div');
    modal.id = 'agentSuggestionModal';
    modal.className = 'modal-overlay';
    modal.innerHTML =
        '<div class="modal-content" style="max-width: 400px;">' +
            '<div class="modal-header">' +
                '<h3>智能体切换建议</h3>' +
                '<button class="modal-close" onclick="closeAgentSuggestionModal()">×</button>' +
            '</div>' +
            '<div class="modal-body">' +
                '<p style="margin-bottom: 15px; line-height: 1.6;">' + message + '</p>' +
                '<div class="modal-actions">' +
                    '<button class="btn btn-primary" id="switchAgentBtn" style="margin-right: 10px;">切换到「' + targetAgentName + '」</button>' +
                    '<button class="btn btn-secondary" id="keepCurrentAgentBtn">保持当前智能体</button>' +
                '</div>' +
            '</div>' +
        '</div>';

    document.body.appendChild(modal);

    // Bind button events
    document.getElementById('switchAgentBtn').addEventListener('click', function() {
        closeAgentSuggestionModal();
        switchToAgent(targetAgentId);
    });

    document.getElementById('keepCurrentAgentBtn').addEventListener('click', function() {
        closeAgentSuggestionModal();
    });
}

window.closeAgentSuggestionModal = function() {
    var modal = document.getElementById('agentSuggestionModal');
    if (modal) {
        modal.remove();
    }
};

async function switchToAgent(agentId) {
    try {
        var { selectAgent } = await import('./agents.js?v=2.5');
        await selectAgent(agentId);
    } catch (err) {
        console.error('[upload] Failed to switch agent:', err);
    }
};


// Global functions for onclick
window.removeFile = function() {
    window.uploadedFile = null;
    showImagePreviews();
};

window.removeImage = function(index) {
    window.uploadedImages.splice(index, 1);
    showImagePreviews();
};

window.removeAudio = function() {
    window.uploadedAudio = null;
    showImagePreviews();
};

window.showImageModal = function(index) {
    var img = window.uploadedImages[index];
    if (!img) return;

    var overlay = document.createElement('div');
    overlay.className = 'image-modal-overlay';
    overlay.id = 'imageModal';
    overlay.onclick = function(e) {
        if (e.target === overlay) window.closeImageModal();
    };

    overlay.innerHTML =
        '<div class="image-modal-content">' +
            '<button class="image-modal-close" onclick="window.closeImageModal()">×</button>' +
            '<img src="/chat/download?fileId=' + img.fileId + '" alt="' + escapeHtml(img.fileName) + '">' +
            '<div class="image-modal-filename">' + escapeHtml(img.fileName) + '</div>' +
        '</div>';

    document.body.appendChild(overlay);
};

window.closeImageModal = function() {
    var modal = document.getElementById('imageModal');
    if (modal) {
        modal.remove();
    }
};
