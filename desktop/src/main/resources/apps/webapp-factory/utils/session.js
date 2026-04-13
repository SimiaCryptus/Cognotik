/**
  * Session and URL utilities
  */

/**
  * Parse the current URL to extract session information
  * @returns {Object} Object containing basePath, sessionId, and appId
  */
export function parseSessionUrl() {
        const pathParts = window.location.pathname.split('/');
        const fileIndexIdx = pathParts.indexOf('fileIndex');
        let basePath = '';
        let sessionId = '';
        let appId = '';

        if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
            sessionId = pathParts[fileIndexIdx + 1];
            basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
            appId = pathParts[fileIndexIdx - 1] || '';
        } else {
            console.warn('Could not determine session from URL path.');
            basePath = window.location.pathname.replace(/\/[^/]*$/, '');
        }

        return { basePath, sessionId, appId };
  }

/**
  * Get the proxy URL for a given session ID
  * @param {string} id - Session ID
  * @returns {string} Proxy URL
  */
export function getProxyUrl(id) {
        return '/proxy/#' + id;
  }

/**
  * Get the app root path (for ZIP/Git endpoints)
  * @returns {string} App root path
  */
export function getAppRoot() {
        const pathParts = window.location.pathname.split('/');
        const fileIndexIdx = pathParts.indexOf('fileIndex');
        return fileIndexIdx >= 0 ? pathParts.slice(0, fileIndexIdx).join('/') : '';
  }

export const SessionUtils = {
     parseSessionUrl,
     getProxyUrl,
     getAppRoot
};