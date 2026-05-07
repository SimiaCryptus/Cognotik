/**
 * DocOps execution and status polling utilities
 */

'use strict';

/**
 * Run a DocOps operation
 * @param {string} sessionId - Session ID
 * @param {string} opPath - Path to the operation document
 * @param {string} targetPath - Target path for the output
 * @param {Object} models - Model configuration (smartModel, fastModel, imageModel, audioModel)
  * @param {Object} [templateVars] - Template variable overrides (e.g. { PROJECT_NAME: 'Foo' }
  *                                  becomes &var.PROJECT_NAME=Foo, replacing {{PROJECT_NAME}} in the doc)
 * @returns {Promise<string>} Task/session ID
 */
export async function runDocOp(sessionId, opPath, targetPath, models = {}, templateVars = {}) {
    const params = new URLSearchParams({
        sessionId: sessionId,
        doc: opPath,
        target: targetPath
    });

    // Add model overrides if provided
    if (models.smartModel) params.set('smartModel', models.smartModel);
    if (models.fastModel) params.set('fastModel', models.fastModel);
    if (models.imageModel) params.set('imageModel', models.imageModel);
    if (models.audioModel) params.set('audioModel', models.audioModel);
     // Add template variable overrides as var.<NAME>=<VALUE>
     // e.g. { PROJECT_NAME: 'Foo' } -> &var.PROJECT_NAME=Foo
     // Server-side substitutes {{PROJECT_NAME}} in the op document with "Foo".
     if (templateVars && typeof templateVars === 'object') {
         for (const [key, value] of Object.entries(templateVars)) {
             if (key == null || value == null) continue;
             const k = String(key).trim();
             if (!k) continue;
             // Only allow safe variable name characters to avoid query-string surprises.
             if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(k)) {
                 console.warn('[runDocOp] Skipping invalid template var name:', k);
                 continue;
             }
             params.set(`var.${k}`, String(value));
         }
     }


    const url = '/docops?' + params.toString();
    const resp = await fetch(url, { method: 'POST' });

    if (!resp.ok) {
        const errText = await resp.text().catch(() => '');
        throw new Error(`DocOps failed: ${resp.status}\n${errText}`);
    }

    const responseText = await resp.text();

    // Try to parse JSON response
    try {
        const jsonResp = JSON.parse(responseText);
        if (jsonResp.sessionId) return jsonResp.sessionId;
        if (jsonResp.taskId) return jsonResp.taskId;
        if (jsonResp.sessions && typeof jsonResp.sessions === 'object') {
            const sessionKeys = Object.keys(jsonResp.sessions);
            if (sessionKeys.length > 0) return sessionKeys[0];
        }
        return '';
    } catch (e) {
        // Not JSON - treat as plain text session ID
        return responseText.trim();
    }
}

/**
 * Fetch DocOps status
 * @param {string} basePath - Base path for the session
 * @returns {Promise<Object|null>} Status data or null
 */
export async function fetchDocopsStatus(basePath) {
    try {
        const resp = await fetch(basePath + '/docops.status.json');
        if (!resp.ok) return null;
        return await resp.json();
    } catch (e) {
        return null;
    }
}

/**
 * Wait for a task to complete
 * @param {string} basePath - Base path for the session
 * @param {string} targetPath - Target path to monitor
 * @param {number} maxWaitMs - Maximum wait time in milliseconds
 * @param {Function} onStatusUpdate - Optional callback for status updates
 * @returns {Promise<Object>} Task result
 */
export async function waitForTask(basePath, targetPath, maxWaitMs = 600000, onStatusUpdate = null) {
    const pollInterval = 2000;
    const startTime = Date.now();

    // Normalize target path variations
    const altTargetPath = targetPath.endsWith('/')
        ? targetPath.slice(0, -1)
        : targetPath + '/';

    while (Date.now() - startTime < maxWaitMs) {
        const statusData = await fetchDocopsStatus(basePath);

        if (statusData && statusData.tasks) {
            // Try exact match first
            let task = statusData.tasks[targetPath];

            // Try alternate path
            if (!task) task = statusData.tasks[altTargetPath];

            // Try filename only
            if (!task) {
                const filename = targetPath.split('/').pop();
                task = statusData.tasks[filename];
            }

            if (task) {
                if (onStatusUpdate) onStatusUpdate(targetPath, task);

                if (task.status === 'COMPLETED') {
                    return task;
                }
                if (task.status === 'ERROR' || task.status === 'FAILED') {
                    throw new Error(`Task ${targetPath} failed`);
                }
            }
        }

        await new Promise(resolve => setTimeout(resolve, pollInterval));
    }

    throw new Error(`Task ${targetPath} timed out after ${maxWaitMs / 1000}s`);
}

/**
 * Create a status poller
 * @param {string} basePath - Base path for the session
 * @param {Function} onUpdate - Callback for status updates
 * @param {number} interval - Poll interval in milliseconds
 * @returns {Object} Poller object with start() and stop() methods
 */
export function createStatusPoller(basePath, onUpdate, interval = 3000) {
    let timer = null;

    async function poll() {
        const statusData = await fetchDocopsStatus(basePath);
        if (statusData && statusData.tasks) {
            for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
                onUpdate(target, taskInfo);
            }
        }
    }

    return {
        start() {
            if (!timer) {
                timer = setInterval(poll, interval);
                poll(); // Initial poll
            }
        },
        stop() {
            if (timer) {
                clearInterval(timer);
                timer = null;
            }
        },
        isRunning() {
            return timer !== null;
        }
    };
}

// Named exports above; also provide a namespace export for convenience
export const DocOpsUtils = {
    runDocOp,
    fetchDocopsStatus,
    waitForTask,
    createStatusPoller
};