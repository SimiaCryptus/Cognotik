// ===== HTML Utilities =====
function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

// Expose as global namespace for use across modules
window.HtmlUtils = {
    escapeHtml: escapeHtml
};