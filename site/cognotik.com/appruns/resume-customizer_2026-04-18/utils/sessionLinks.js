export const SessionLinkUtils = {
    /**
     * Update session links in a container element
     * @param {string} target - Target file being processed
     * @param {Object} taskInfo - Task information object
     * @param {Function} getProxyUrl - Function to generate proxy URLs
     * @param {string} containerId - ID of the container element (optional)
     */
    updateSessionLinks(target, taskInfo, getProxyUrl, containerId = null) {
        // Determine container ID based on target if not provided
        if (!containerId) {
            const linkMap = {
                'job-analysis.md': 'analyze-links',
                'company-research.md': 'research-links',
                'resume-custom.json': 'customize-links',
                'standard.pdf': 'render-links',
                'simple.pdf': 'render-links',
                'standard.tex': 'render-links',
                'simple.tex': 'render-links'
            };
            containerId = linkMap[target];
        }

        if (!containerId) return;

        const container = document.getElementById(containerId);
        if (!container) return;

        // Clear existing links
        container.innerHTML = '';

        // Add monitoring link if task is running or completed
        if (taskInfo.taskId && (taskInfo.status === 'RUNNING' || taskInfo.status === 'COMPLETED')) {
            const monitorLink = document.createElement('a');
            monitorLink.href = getProxyUrl(taskInfo.taskId);
            monitorLink.target = '_blank';
            monitorLink.textContent = 'Monitor';
            monitorLink.className = 'session-link';
            container.appendChild(monitorLink);
        }

        // Add view link for completed tasks
        if (taskInfo.status === 'COMPLETED' && taskInfo.taskId) {
            const viewLink = document.createElement('a');
            viewLink.href = getProxyUrl(taskInfo.taskId);
            viewLink.target = '_blank';
            viewLink.textContent = 'View Session';
            viewLink.className = 'session-link';
            
            if (container.children.length > 0) {
                container.appendChild(document.createTextNode(' | '));
            }
            container.appendChild(viewLink);
        }
    },

    /**
     * Create a session link element
     * @param {string} url - URL for the link
     * @param {string} text - Link text
     * @param {string} className - CSS class name
     * @returns {HTMLAnchorElement}
     */
    createSessionLink(url, text, className = 'session-link') {
        const link = document.createElement('a');
        link.href = url;
        link.target = '_blank';
        link.textContent = text;
        link.className = className;
        return link;
    }
};