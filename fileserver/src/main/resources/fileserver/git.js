/**
  * Git API utilities
  */
import { escapeHtml } from './ui.js';

/**
  * Make a Git API call
  * @param {string} basePath - Base path for the session
  * @param {string} endpoint - API endpoint
  * @param {Object} options - Fetch options
  * @returns {Promise<Object>} API response
  */
export async function gitApiCall(basePath, endpoint, options = {}) {
        const url = basePath + '/.git/api/' + endpoint;
        const resp = await fetch(url, {
            credentials: 'include',
            ...options
        });
        
        if (!resp.ok) {
            const errText = await resp.text().catch(() => '');
            throw new Error(`Git API error (${resp.status}): ${errText}`);
        }
        
        const data = await resp.json();
        if (data.success === false) {
            throw new Error(data.error || `Git ${endpoint} failed`);
        }
        
        return data;
  }

/**
  * Get Git repository status
  * @param {string} basePath - Base path for the session
  * @returns {Promise<Object>} Status data
  */
export async function getStatus(basePath) {
        return await gitApiCall(basePath, 'status');
  }

/**
  * Initialize Git repository
  * @param {string} basePath - Base path for the session
  * @returns {Promise<Object>} Init result
  */
export async function initRepository(basePath) {
        return await gitApiCall(basePath, 'init', { method: 'POST' });
  }

/**
  * Commit changes
  * @param {string} basePath - Base path for the session
  * @param {string} message - Commit message
  * @returns {Promise<Object>} Commit result
  */
export async function commit(basePath, message) {
        return await gitApiCall(basePath, 'commit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message })
        });
  }

/**
  * Get branches
  * @param {string} basePath - Base path for the session
  * @returns {Promise<Object>} Branches data
  */
export async function getBranches(basePath) {
        return await gitApiCall(basePath, 'branches');
  }

/**
  * Checkout branch
  * @param {string} basePath - Base path for the session
  * @param {string} branch - Branch name
  * @param {boolean} create - Create new branch
  * @returns {Promise<Object>} Checkout result
  */
export async function checkout(basePath, branch, create = false) {
        return await gitApiCall(basePath, 'checkout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ branch, create })
        });
  }

/**
  * Get commit log
  * @param {string} basePath - Base path for the session
  * @param {number} maxCount - Maximum number of commits
  * @returns {Promise<Object>} Log data
  */
export async function getLog(basePath, maxCount = 20) {
        return await gitApiCall(basePath, `log?maxCount=${maxCount}`);
  }

/**
  * Format Git status for display
  * @param {Object} statusData - Git status data
  * @returns {string} HTML formatted status
  */
export function formatStatus(statusData) {
        if (!statusData.initialized) {
            return `
                <div class="git-status-box">
                    <div class="git-status-header">
                        <span class="git-status-indicator uninit">⚪ Not Initialized</span>
                    </div>
                    <p style="color:var(--color-text-muted); font-size:0.9rem;">
                         ${escapeHtml(statusData.message || 'No Git repository found.')}
                    </p>
                </div>`;
        }
        
        const cleanClass = statusData.clean ? 'clean' : 'dirty';
        const cleanLabel = statusData.clean ? '✅ Clean' : '⚠️ Uncommitted Changes';
        
        let changesHtml = '';
        if (statusData.changes && statusData.changes.length > 0) {
            const changeItems = statusData.changes.map(change => {
                const badgeClass = getChangeBadgeClass(change.status);
                const label = getChangeLabel(change.status);
                return `<li>
                     <span class="git-change-badge ${badgeClass}">${escapeHtml(change.status)}</span>
                     <span>${escapeHtml(change.file)}</span>
                     <span style="color:var(--color-text-muted);font-size:0.75rem;">${escapeHtml(label)}</span>
                </li>`;
            }).join('');
            
            changesHtml = `
                <div>
                    <strong style="font-size:0.88rem; color:var(--color-text-muted);">Changes (${statusData.changes.length}):</strong>
                    <ul class="git-changes-list" style="margin-top:0.4rem;">${changeItems}</ul>
                </div>`;
        }
        
        return `
            <div class="git-status-box">
                <div class="git-status-header">
                    <span class="git-status-indicator ${cleanClass}">${cleanLabel}</span>
                     <span class="git-branch-badge">🌿 ${escapeHtml(statusData.currentBranch || 'unknown')}</span>
                </div>
                ${changesHtml}
            </div>`;
  }

function getChangeBadgeClass(status) {
        switch (status) {
            case 'M': return 'modified';
            case 'A': return 'added';
            case 'D': return 'deleted';
            case 'R': return 'renamed';
            case '??': return 'untracked';
            default: return 'modified';
        }
  }

function getChangeLabel(status) {
        switch (status) {
            case 'M': return 'Modified';
            case 'A': return 'Added';
            case 'D': return 'Deleted';
            case 'R': return 'Renamed';
            case '??': return 'Untracked';
            default: return status;
        }
  }

export const GitUtils = {
     gitApiCall,
     getStatus,
     initRepository,
     commit,
     getBranches,
     checkout,
     getLog,
     formatStatus
};