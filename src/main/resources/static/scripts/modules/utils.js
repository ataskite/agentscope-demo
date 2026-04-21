/* ===== MARKED CONFIG ===== */
if (typeof marked !== 'undefined') {
    marked.setOptions({
        breaks: true,
        gfm: true
    });
}

/* ===== MARKDOWN RENDERING ===== */
export function renderMarkdown(text) {
    // Check if text contains structured data
    var structuredData = extractStructuredData(text);
    if (structuredData) {
        return renderStructuredData(structuredData, text);
    }

    if (typeof marked !== 'undefined') {
        return marked.parse(text);
    }
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Extract structured data from text (JSON or special format)
function extractStructuredData(text) {
    // Try to find JSON in code blocks
    var jsonMatch = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
    if (jsonMatch) {
        try {
            return JSON.parse(jsonMatch[1]);
        } catch (e) {
            // Not valid JSON, continue
        }
    }

    // Try to find raw JSON
    try {
        if (text.trim().startsWith('{') && text.trim().endsWith('}')) {
            return JSON.parse(text);
        }
    } catch (e) {
        // Not valid JSON
    }

    return null;
}

// Render structured data as a table
function renderStructuredData(data, originalText) {
    var html = '';

    // Add export button
    html += '<div class="structured-data-actions">' +
        '<button class="export-btn" onclick="exportStructuredData(this)" data-data=\'' + JSON.stringify(data).replace(/'/g, "\\'") + '\'">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>' +
        '导出 JSON</button>' +
        '</div>';

    // Render as table
    if (typeof data === 'object' && data !== null) {
        if (Array.isArray(data)) {
            // Array of objects
            if (data.length > 0 && typeof data[0] === 'object') {
                html += renderObjectTable(data);
            } else {
                // Simple array
                html += '<div class="structured-data-list">' +
                    data.map(function(item) { return '<div class="structured-data-item">' + escapeHtml(String(item)) + '</div>'; }).join('') +
                    '</div>';
            }
        } else {
            // Single object
            html += renderObjectTable([data]);
        }
    }

    // Add original text as markdown if available
    if (originalText && originalText !== JSON.stringify(data)) {
        html += '<div class="structured-data-original">';
        if (typeof marked !== 'undefined') {
            html += marked.parse(originalText);
        } else {
            html += '<p>' + escapeHtml(originalText) + '</p>';
        }
        html += '</div>';
    }

    return html;
}

// Render array of objects as table
function renderObjectTable(dataArray) {
    if (!dataArray || dataArray.length === 0) {
        return '<div class="structured-data-empty">无数据</div>';
    }

    // Get all unique keys from all objects
    var keys = {};
    dataArray.forEach(function(obj) {
        if (typeof obj === 'object' && obj !== null) {
            Object.keys(obj).forEach(function(key) {
                keys[key] = true;
            });
        }
    });
    var columns = Object.keys(keys);

    if (columns.length === 0) {
        return '<div class="structured-data-empty">无数据字段</div>';
    }

    var html = '<div class="structured-data-table-wrapper"><table class="structured-data-table">';

    // Header
    html += '<thead><tr>';
    columns.forEach(function(col) {
        html += '<th>' + escapeHtml(col) + '</th>';
    });
    html += '</tr></thead>';

    // Body
    html += '<tbody>';
    dataArray.forEach(function(row) {
        html += '<tr>';
        columns.forEach(function(col) {
            var value = row[col];
            var displayValue = formatValue(value);
            html += '<td>' + displayValue + '</td>';
        });
        html += '</tr>';
    });
    html += '</tbody></table></div>';

    return html;
}

// Format value for display
function formatValue(value) {
    if (value === null || value === undefined) {
        return '<span class="value-null">-</span>';
    }
    if (typeof value === 'object') {
        return '<code class="value-object">' + escapeHtml(JSON.stringify(value)) + '</code>';
    }
    if (typeof value === 'number') {
        return '<span class="value-number">' + escapeHtml(String(value)) + '</span>';
    }
    if (typeof value === 'boolean') {
        return '<span class="value-boolean">' + (value ? '是' : '否') + '</span>';
    }
    return escapeHtml(String(value));
}

// Export structured data as JSON file (global function for onclick)
window.exportStructuredData = function(btn) {
    var dataStr = btn.getAttribute('data-data');
    try {
        var data = JSON.parse(dataStr);
        var jsonStr = JSON.stringify(data, null, 2);
        var blob = new Blob([jsonStr], { type: 'application/json' });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'structured-data-' + Date.now() + '.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch (e) {
        console.error('Export failed:', e);
    }
};

/* ===== HTML HELPERS ===== */
export function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

export function getTimestamp() {
    return new Date().toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

export function formatDuration(ms) {
    if (ms < 0) return '-';
    if (ms < 1000) return Math.round(ms) + 'ms';
    return (ms / 1000).toFixed(1) + 's';
}

export function scrollToBottom(element) {
    requestAnimationFrame(function() {
        element.scrollTop = element.scrollHeight;
    });
}

/* ===== FILE LIST HELPER ===== */
export function createFileList(fileInfo) {
    if (!fileInfo) return '';

    var lastDotIndex = fileInfo.fileName.lastIndexOf('.');
    var ext = lastDotIndex >= 0
        ? fileInfo.fileName.substring(lastDotIndex).toLowerCase()
        : '';
    var iconLabel = '';
    var iconClass = '';

    switch (ext) {
        case '.docx':
            iconLabel = 'W';
            iconClass = 'docx';
            break;
        case '.pdf':
            iconLabel = 'P';
            iconClass = 'pdf';
            break;
        case '.xlsx':
            iconLabel = 'X';
            iconClass = 'xlsx';
            break;
        default:
            iconLabel = '?';
            iconClass = '';
    }

    return '<div class="file-list">' +
        '<div class="file-list-item">' +
        '<span class="file-icon ' + iconClass + '">' + iconLabel + '</span>' +
        '<span class="file-name">' + escapeHtml(fileInfo.fileName) + '</span>' +
        '</div>' +
        '</div>';
}
