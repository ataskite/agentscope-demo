import { uploadFile } from '../api.js';
import { escapeHtml } from './utils.js';

/* ===== FILE UPLOAD ===== */
export async function handleFileSelect(input) {
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
            console.error('Upload error:', data.error);
            return;
        }

        // Handle based on file type
        if (isDoc) {
            console.log('[upload] Document uploaded, switching to task-document-analysis');
            if (window.currentAgent !== 'task-document-analysis') {
                var { selectAgent } = await import('./agents.js');
                selectAgent('task-document-analysis');
            }
            window.uploadedFile = data;
            showFileTag(data.fileName);
        } else if (isImage) {
            console.log('[upload] Image uploaded');
            window.uploadedImages.push(data);
            showImagePreviews();
            // Auto-switch to vision agent if available
            if (window.agents['vision-analyzer']) {
                var { selectAgent } = await import('./agents.js');
                selectAgent('vision-analyzer');
            }
        } else if (isAudio) {
            console.log('[upload] Audio uploaded');
            window.uploadedAudio = data;
            showAudioPreview();
            // Auto-switch to voice agent if available
            if (window.agents['voice-assistant']) {
                var { selectAgent } = await import('./agents.js');
                selectAgent('voice-assistant');
            }
        }
    } catch (err) {
        console.error('Upload error:', err);
    }

    input.value = '';
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
