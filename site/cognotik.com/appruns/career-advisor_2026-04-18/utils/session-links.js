/**
 * Session monitoring link utilities
 */
(function() {
    'use strict';

    /**
     * Update session monitoring links
     * @param {string} target - Target path
     * @param {Object} taskInfo - Task information
     * @param {Function} getProxyUrl - Function to get proxy URL
     */
    function updateSessionLinks(target, taskInfo, getProxyUrl) {
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;
        const safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        const linkContainerId = 'session-link-' + safeTarget;
        
        let container = document.getElementById(linkContainerId);
        
        if (!container) {
            container = document.createElement('div');
            container.id = linkContainerId;
            container.className = 'session-link-container';
            
            // Try to find a suitable parent element
            const possibleParents = [
                document.getElementById('viewer-' + safeTarget),
                document.querySelector(`[data-output="${target}"]`)?.closest('.step'),
                document.querySelector(`[data-target="${target}"]`)?.closest('.card'),
                document.getElementById('batch-log')
            ];
            
            for (const parent of possibleParents) {
                if (parent && parent.parentElement) {
                    parent.parentElement.insertBefore(container, parent.nextSibling);
                    break;
                }
            }
        }
        
        if (!container || !container.parentElement) return;
        
        if (status === 'RUNNING' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML = `
                <div class="session-monitor-link">
                    <span class="monitor-pulse">●</span>
                    <span>Processing… </span>
                    <a href="${window.UIUtils.escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
                        📡 Monitor Live Session (${window.UIUtils.escapeHtml(taskSessionId.substring(0, 12))}…)
                    </a>
                </div>`;
            container.style.display = 'block';
        } else if (status === 'COMPLETED' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            const completedAt = taskInfo.completedAt 
                ? new Date(taskInfo.completedAt).toLocaleTimeString() 
                : '';
            container.innerHTML = `
                <div class="session-completed-link">
                    <span>✅ Completed${completedAt ? ' at ' + completedAt : ''} — </span>
                    <a href="${window.UIUtils.escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
                        📋 View Session Log (${window.UIUtils.escapeHtml(taskSessionId.substring(0, 12))}…)
                    </a>
                </div>`;
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            const proxyUrl = taskSessionId ? getProxyUrl(taskSessionId) : '#';
            container.innerHTML = `
                <div class="session-error-link">
                    <span>❌ Failed — </span>
                    ${taskSessionId 
                        ? `<a href="${window.UIUtils.escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">
                            🔍 View Error Log (${window.UIUtils.escapeHtml(taskSessionId.substring(0, 12))}…)
                           </a>`
                        : '<span>No session available</span>'}
                </div>`;
            container.style.display = 'block';
        } else {
            container.style.display = 'none';
        }
    }

    /**
     * Create a session link manager
     * @param {Function} getProxyUrl - Function to get proxy URL
     * @returns {Object} Link manager with update method
     */
    function createSessionLinkManager(getProxyUrl) {
        const sessionMap = new Map();
        
        return {
            update(target, taskInfo) {
                updateSessionLinks(target, taskInfo, getProxyUrl);
                if (taskInfo.sessionId) {
                    sessionMap.set(target, taskInfo.sessionId);
                }
            },
            
            getSessionId(target) {
                return sessionMap.get(target);
            },
            
            getAllSessions() {
                return Array.from(sessionMap.values());
            },
            
            clear() {
                sessionMap.clear();
            }
        };
    }

    // Export functions
    window.SessionLinkUtils = {
        updateSessionLinks,
        createSessionLinkManager
    };
})();