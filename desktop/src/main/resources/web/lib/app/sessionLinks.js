/**
 * Session link utilities for rendering live monitoring / view links
 * based on DocOps task status.
 */

/**
 * Create a session link <a> element
 * @param {string} url
 * @param {string} text
 * @param {string} className
 * @returns {HTMLAnchorElement}
 */
export function createSessionLink(url, text, className = 'session-link') {
    const link = document.createElement('a');
    link.href = url;
    link.target = '_blank';
    link.textContent = text;
    link.className = className;
    return link;
}

// Default mapping from well-known target filenames to container element IDs.
const DEFAULT_LINK_MAP = {
    'job-analysis.md': 'analyze-links',
    'company-research.md': 'research-links',
    'resume-custom.json': 'customize-links',
    'standard.pdf': 'render-links',
    'simple.pdf': 'render-links',
    'standard.tex': 'render-links',
    'simple.tex': 'render-links'
};

/**
 * Resolve the container element for a given target.
 * Falls back to searching for an element with data-session-links="<target>".
 * @param {string} target
 * @param {string|null} containerId
 * @returns {HTMLElement|null}
 */
function resolveContainer(target, containerId) {
    if (containerId) {
        return document.getElementById(containerId);
    }
    const mappedId = DEFAULT_LINK_MAP[target];
    if (mappedId) {
        const el = document.getElementById(mappedId);
        if (el) return el;
    }
    // Fallback: look for a data-attribute container
    return document.querySelector(`[data-session-links="${target}"]`);
}

/**
 * Update session links in a container element based on task info.
 * @param {string} target - Target file being processed
 * @param {Object} taskInfo - Task information object ({ taskId, status, ... })
 * @param {Function} getProxyUrl - Function to generate proxy URLs
 * @param {string} [containerId] - Optional explicit container element ID
 */
export function updateSessionLinks(target, taskInfo, getProxyUrl, containerId = null) {
    const container = resolveContainer(target, containerId);
    if (!container) return;

    // Clear existing links
    container.innerHTML = '';

    if (!taskInfo) return;

    const status = taskInfo.status;
    const taskId = taskInfo.taskId || taskInfo.sessionId || taskInfo.id;

    if (!taskId) return;

    // Add monitoring link if task is running or completed
    if (status === 'RUNNING' || status === 'COMPLETED') {
        const label = status === 'RUNNING' ? 'Monitor Live Session' : 'Monitor';
        const monitorLink = createSessionLink(getProxyUrl(taskId), label);
        container.appendChild(monitorLink);
    }

    // Add view link for completed tasks
    if (status === 'COMPLETED') {
        if (container.children.length > 0) {
            container.appendChild(document.createTextNode(' | '));
        }
        const viewLink = createSessionLink(getProxyUrl(taskId), 'View Session');
        container.appendChild(viewLink);
    }

    // Add error link for failed tasks
    if (status === 'ERROR' || status === 'FAILED') {
        const errorLink = createSessionLink(getProxyUrl(taskId), 'View Error Log');
        container.appendChild(errorLink);
    }
}

/**
 * Create a session link manager that tracks session IDs per target
 * and updates DOM containers whenever task status changes.
 *
 * @param {Function} getProxyUrl - Function that returns a proxy URL for a task ID
 * @returns {{
 *   update: (target: string, taskInfo: Object, containerId?: string) => void,
 *   getSessionId: (target: string) => string|null,
 *   getAllSessions: () => Object,
 *   clear: () => void
 * }}
 */
export function createSessionLinkManager(getProxyUrl) {
    const sessions = {};

    return {
        /**
         * Update the session link DOM for a given target and remember its task ID.
         */
        update(target, taskInfo, containerId = null) {
            if (!target || !taskInfo) return;
            const taskId = taskInfo.taskId || taskInfo.sessionId || taskInfo.id;
            if (taskId) {
                sessions[target] = taskId;
            }
            updateSessionLinks(target, taskInfo, getProxyUrl, containerId);
        },

        /**
         * Get the session/task ID tracked for a given target.
         */
        getSessionId(target) {
            return sessions[target] || null;
        },

        /**
         * Get a copy of all tracked target→sessionId mappings.
         */
        getAllSessions() {
            return {...sessions};
        },

        /**
         * Clear all tracked sessions.
         */
        clear() {
            Object.keys(sessions).forEach(k => delete sessions[k]);
        }
    };
}

export const SessionLinkUtils = {
    updateSessionLinks,
    createSessionLink,
    createSessionLinkManager
};