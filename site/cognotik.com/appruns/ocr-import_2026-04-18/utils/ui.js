/**
  * Common UI utilities
  */

/**
  * Render markdown to HTML
  * @param {string} md - Markdown content
  * @returns {string} HTML content
  */
export function renderMarkdown(md) {
      if (!md) return '';
     // Use the marked library loaded globally via CDN / fallback
     try {
         if (typeof window.marked !== 'undefined') {
             // marked v15+ exposes marked.parse; older versions may expose marked() directly
             if (typeof window.marked.parse === 'function') {
                  return window.marked.parse(md);
             } else if (typeof window.marked === 'function') {
                  return window.marked(md);
             }
         }
     } catch (e) {
         console.warn('marked rendering failed, using fallback:', e);
     }
     // Fallback: basic HTML escaping with line breaks preserved
     return '<pre style="white-space:pre-wrap;word-wrap:break-word;">' +
          md.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') +
         '</pre>';
  }

/**
  * Escape HTML special characters
  * @param {string} text - Text to escape
  * @returns {string} Escaped text
  */
export function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
  }

/**
  * Set status message with auto-clear
  * @param {string} elemId - Element ID
  * @param {string} message - Status message
  * @param {string} type - Message type (success, error, info, warning)
  * @param {number} autoClearMs - Auto-clear timeout in milliseconds
  */
export function setStatus(elemId, message, type = '', autoClearMs = 5000) {
        const el = document.getElementById(elemId);
        if (!el) return;
        
        el.textContent = message;
        el.className = 'status-msg' + (type ? ' ' + type : '');
        
        if ((type === 'success' || type === 'error') && autoClearMs > 0) {
            setTimeout(() => {
                el.textContent = '';
                el.className = 'status-msg';
            }, autoClearMs);
        }
  }

/**
  * Update badge state
  * @param {string} badgeId - Badge element ID
  * @param {string} state - Badge state (pending, running, done, error)
  */
export function setBadge(badgeId, state) {
        const el = document.getElementById(badgeId);
        if (!el) return;
        
        el.className = 'step-badge ' + state;
        const labels = {
            pending: 'pending',
            running: 'running…',
            done: 'done',
            error: 'error'
        };
        el.textContent = labels[state] || state;
  }

/**
  * Show toast notification
  * @param {string} message - Toast message
  * @param {string} type - Toast type (success, error, info, warning)
  * @param {number} duration - Display duration in milliseconds
  */
export function showToast(message, type = 'info', duration = 4000) {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        
        const toast = document.createElement('div');
        toast.className = 'toast toast-' + type;
        toast.textContent = message;
        container.appendChild(toast);
        
        // Trigger animation
        setTimeout(() => toast.classList.add('show'), 10);
        
        // Remove after duration
        setTimeout(() => {
            toast.classList.add('removing');
            setTimeout(() => {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 300);
        }, duration);
  }

/**
  * Create a batch logger
  * @param {string} logId - Log container element ID
  * @returns {Object} Logger object with log() and logHtml() methods
  */
export function createBatchLogger(logId) {
        const logEl = document.getElementById(logId);
        if (!logEl) {
            console.warn(`Batch log element ${logId} not found`);
            return {
                log: () => {},
                logHtml: () => {},
                clear: () => {}
            };
        }
        
        return {
            log(message, type = 'info') {
                logEl.classList.add('visible');
                const entry = document.createElement('div');
                entry.className = 'log-entry log-' + type;
                const ts = new Date().toLocaleTimeString();
                entry.textContent = `[${ts}] ${message}`;
                logEl.appendChild(entry);
                logEl.scrollTop = logEl.scrollHeight;
            },
            
            logHtml(html, type = 'info') {
                logEl.classList.add('visible');
                const entry = document.createElement('div');
                entry.className = 'log-entry log-' + type;
                const ts = new Date().toLocaleTimeString();
                entry.innerHTML = `[${ts}] ${html}`;
                logEl.appendChild(entry);
                logEl.scrollTop = logEl.scrollHeight;
            },
            
            clear() {
                logEl.innerHTML = '';
                logEl.classList.remove('visible');
            }
        };
  }

/**
  * Get file icon based on extension
  * @param {string} filename - File name
  * @returns {string} Icon emoji
  */
export function getFileIcon(filename) {
        const ext = filename.split('.').pop().toLowerCase();
        const icons = {
            'md': '📝',
            'txt': '📄',
            'html': '🌐',
            'htm': '🌐',
            'css': '🎨',
            'js': '⚡',
            'ts': '⚡',
            'json': '📋',
            'xml': '📋',
            'yaml': '📋',
            'yml': '📋',
            'py': '🐍',
            'java': '☕',
            'rb': '💎',
            'go': '🔵',
            'rs': '🦀',
            'sh': '🖥️',
            'bash': '🖥️',
            'svg': '🖼️',
            'png': '🖼️',
            'jpg': '🖼️',
            'jpeg': '🖼️',
            'gif': '🖼️',
            'pdf': '📕',
            'doc': '📘',
            'docx': '📘',
            'zip': '📦',
            'tar': '📦',
            'gz': '📦'
        };
        return icons[ext] || '📄';
  }

export const UIUtils = {
     renderMarkdown,
     escapeHtml,
     setStatus,
     setBadge,
     showToast,
     createBatchLogger,
     getFileIcon
};