import { parseSessionUrl, getProxyUrl, getAppRoot } from './utils/session.js';
import { readFile, writeFile, listFiles, fileExists } from './utils/fileIO.js';
import { runDocOp, fetchDocopsStatus, waitForTask, createStatusPoller } from './utils/docops.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { renderMarkdown, escapeHtml, setStatus, setBadge, showToast, createBatchLogger, getFileIcon } from './utils/ui.js';
import { initRepository, commit, getStatus, getBranches, checkout, getLog, formatStatus, gitApiCall } from './utils/git.js';
import { fetchUsageData, formatTokenCount, formatCost, aggregateUsage, renderUsageSummary, createUsageTableHtml } from './utils/usage.js';
import { createSessionLinkManager } from './utils/sessionLinks.js';

(function () {
    'use strict';

    // === Session & path setup ===
    const { basePath, sessionId, appId } = parseSessionUrl();
    if (!sessionId) {
        console.warn('Could not determine session from URL path. File operations may fail.');
    }
    const appBase = getAppRoot();
    const appIndexUrl = basePath + '/code/index.html';

    // === Model management state ===
    let availableModels = {};

    // === Usage tracking state ===
    let knownTaskSessionIds = new Set();
    let lastUsageData = null;

    // === Session link manager ===
    const linkManager = createSessionLinkManager(getProxyUrl);

    // === Batch logger ===
    const batchLog = document.getElementById('batch-log');
    const logger = createBatchLogger('batch-log');

    function logBatch(message, type) {
        if (batchLog) batchLog.classList.add('visible');
        logger.log(message, type);
    }

    function logBatchHtml(html, type) {
        if (batchLog) batchLog.classList.add('visible');
        logger.logHtml(html, type);
    }

    // === Status polling state ===
    let statusPollTimer = null;
    const STATUS_POLL_INTERVAL = 3000;
    let activeCodeBadge = 'badge-render';
    let activeCodeTaskSessionId = null;
    let codeOperationInFlight = false;

    // === Model loading and selection ===
    async function loadModels() {
        try {
            availableModels = await loadApiProviders();
            if (!availableModels || Object.keys(availableModels).length === 0) {
                setModelDropdownsError('No models available — configure API keys first');
                return;
            }
            const smartSelect = document.getElementById('model-smart');
            const fastSelect = document.getElementById('model-fast');
            const imageSelect = document.getElementById('model-image');
            const selects = {};
            if (smartSelect) selects.smartModel = smartSelect;
            if (fastSelect) selects.fastModel = fastSelect;
            if (imageSelect) selects.imageModel = imageSelect;

            const saved = loadModelSelections('webappFactory', ['smartModel', 'fastModel', 'imageModel']);
            populateModelDropdowns(availableModels, selects, saved);
        } catch (e) {
            console.warn('Failed to load API providers:', e);
            setModelDropdownsError('Failed to load models');
        }
    }

    function setModelDropdownsError(message) {
        const smartSelect = document.getElementById('model-smart');
        const fastSelect = document.getElementById('model-fast');
        const imageSelect = document.getElementById('model-image');
        [smartSelect, fastSelect, imageSelect].filter(Boolean).forEach(sel => {
            sel.innerHTML = '';
            const option = document.createElement('option');
            option.value = '';
            option.textContent = message;
            sel.appendChild(option);
        });
    }

    function getSelectedModels() {
        const smartSelect = document.getElementById('model-smart');
        const fastSelect = document.getElementById('model-fast');
        const imageSelect = document.getElementById('model-image');
        return {
            smartModel: smartSelect ? smartSelect.value : '',
            fastModel: fastSelect ? fastSelect.value : '',
            imageModel: imageSelect ? imageSelect.value : '',
        };
    }

    // === Launch link management ===
    function updateLaunchLinks() {
        const links = [
            document.getElementById('nav-launch-app'),
            document.getElementById('btn-launch-app-banner'),
            document.getElementById('btn-open-app-results'),
            document.getElementById('btn-preview-launch'),
        ];
        links.forEach(el => {
            if (el) el.href = appIndexUrl;
        });
    }
    updateLaunchLinks();

    // === Console Capture & Live Preview ===
    const consoleOutput = document.getElementById('console-output');
    const consoleCounts = document.getElementById('console-counts');
    const previewIframe = document.getElementById('preview-iframe');
    const previewPlaceholder = document.getElementById('preview-placeholder');
    const btnFixErrors = document.getElementById('btn-fix-errors');
    let capturedLogs = [];
    let consoleStat = { logs: 0, warnings: 0, errors: 0 };

    const CONSOLE_CAPTURE_SCRIPT = `
<script>
(function() {
    var _origConsole = {
        log: console.log,
        warn: console.warn,
        error: console.error,
        info: console.info,
        debug: console.debug
    };
    function send(level, args) {
        try {
            var parts = [];
            for (var i = 0; i < args.length; i++) {
                try {
                    parts.push(typeof args[i] === 'object' ? JSON.stringify(args[i], null, 2) : String(args[i]));
                } catch(e) {
                    parts.push(String(args[i]));
                }
            }
            window.parent.postMessage({
                type: 'console-capture',
                level: level,
                message: parts.join(' '),
                timestamp: Date.now()
            }, '*');
        } catch(e) {}
    }
    console.log = function() { send('log', arguments); _origConsole.log.apply(console, arguments); };
    console.warn = function() { send('warn', arguments); _origConsole.warn.apply(console, arguments); };
    console.error = function() { send('error', arguments); _origConsole.error.apply(console, arguments); };
    console.info = function() { send('info', arguments); _origConsole.info.apply(console, arguments); };
    console.debug = function() { send('log', arguments); _origConsole.debug.apply(console, arguments); };
    window.onerror = function(msg, source, lineno, colno, error) {
        var detail = msg;
        if (source) detail += '\\n  at ' + source + ':' + lineno + ':' + colno;
        if (error && error.stack) detail += '\\n' + error.stack;
        window.parent.postMessage({
            type: 'console-capture',
            level: 'exception',
            message: detail,
            source: source,
            lineno: lineno,
            colno: colno,
            timestamp: Date.now()
        }, '*');
    };
    window.addEventListener('unhandledrejection', function(event) {
        var reason = event.reason;
        var msg = 'Unhandled Promise Rejection: ';
        if (reason instanceof Error) {
            msg += reason.message + (reason.stack ? '\\n' + reason.stack : '');
        } else {
            try { msg += JSON.stringify(reason); } catch(e) { msg += String(reason); }
        }
        window.parent.postMessage({
            type: 'console-capture',
            level: 'exception',
            message: msg,
            timestamp: Date.now()
        }, '*');
    });
    window.parent.postMessage({ type: 'console-capture', level: 'info', message: 'App loaded successfully.', timestamp: Date.now() }, '*');
})();
</' + 'script>`;

    function clearConsolePanel() {
        capturedLogs = [];
        consoleStat = { logs: 0, warnings: 0, errors: 0 };
        if (consoleOutput) consoleOutput.innerHTML = '<div class="console-entry console-info">Waiting for app to load…</div>';
        updateConsoleCounts();
        if (btnFixErrors) btnFixErrors.style.display = 'none';
    }

    function updateConsoleCounts() {
        if (!consoleCounts) return;
        let parts = [];
        if (consoleStat.errors > 0) parts.push(`<span class="console-count-errors">❌ ${consoleStat.errors} error${consoleStat.errors !== 1 ? 's' : ''}</span>`);
        if (consoleStat.warnings > 0) parts.push(`<span class="console-count-warnings">⚠️ ${consoleStat.warnings} warning${consoleStat.warnings !== 1 ? 's' : ''}</span>`);
        parts.push(`<span class="console-count-logs">📝 ${consoleStat.logs} log${consoleStat.logs !== 1 ? 's' : ''}</span>`);
        consoleCounts.innerHTML = parts.join('');
    }

    function addConsoleEntry(level, message, source) {
        const ts = new Date().toLocaleTimeString();
        const entry = {
            level: level,
            message: message,
            source: source || '',
            timestamp: ts,
            raw: message
        };
        capturedLogs.push(entry);

        if (level === 'error' || level === 'exception') {
            consoleStat.errors++;
            if (btnFixErrors) btnFixErrors.style.display = '';
        } else if (level === 'warn') {
            consoleStat.warnings++;
        } else {
            consoleStat.logs++;
        }
        updateConsoleCounts();

        if (!consoleOutput) return;
        const placeholder = consoleOutput.querySelector('.console-info');
        if (placeholder && placeholder.textContent.includes('Waiting for app')) {
            placeholder.remove();
        }
        const cssClass = level === 'exception' ? 'console-exception' :
            level === 'error' ? 'console-error' :
                level === 'warn' ? 'console-warn' :
                    level === 'info' ? 'console-info' : 'console-log';
        const div = document.createElement('div');
        div.className = 'console-entry ' + cssClass;
        div.innerHTML = `<span class="console-timestamp">${escapeHtml(ts)}</span>${escapeHtml(message)}`;
        if (source) {
            div.innerHTML += `<span class="console-source">${escapeHtml(source)}</span>`;
        }
        consoleOutput.appendChild(div);
        consoleOutput.scrollTop = consoleOutput.scrollHeight;
    }

    window.addEventListener('message', function (event) {
        if (!event.data || event.data.type !== 'console-capture') return;
        const { level, message, source, lineno, colno } = event.data;
        let sourceInfo = '';
        if (source) {
            sourceInfo = source;
            if (lineno) sourceInfo += ':' + lineno;
            if (colno) sourceInfo += ':' + colno;
        }
        addConsoleEntry(level || 'log', message || '', sourceInfo);
    });

    async function loadPreviewIframe() {
        if (!previewIframe) return;
        const available = await checkAppAvailable();
        if (!available) {
            if (previewPlaceholder) previewPlaceholder.style.display = 'flex';
            previewIframe.style.display = 'none';
            return;
        }
        clearConsolePanel();
        try {
            const resp = await fetch(appIndexUrl);
            if (!resp.ok) throw new Error('Could not fetch app');
            let html = await resp.text();

            if (html.includes('<head>')) {
                html = html.replace('<head>', '<head>' + CONSOLE_CAPTURE_SCRIPT);
            } else if (html.includes('<html>')) {
                html = html.replace('<html>', '<html><head>' + CONSOLE_CAPTURE_SCRIPT + '</head>');
            } else {
                html = CONSOLE_CAPTURE_SCRIPT + html;
            }

            const codeBaseUrl = basePath + '/code/';
            if (html.includes('<head>')) {
                html = html.replace('<head>', '<head><base href="' + codeBaseUrl + '">');
            }

            if (previewPlaceholder) previewPlaceholder.style.display = 'none';
            previewIframe.style.display = 'block';
            previewIframe.srcdoc = html;
        } catch (e) {
            console.warn('Failed to load preview:', e);
            addConsoleEntry('error', 'Failed to load preview: ' + e.message);
            if (previewPlaceholder) previewPlaceholder.style.display = 'none';
            previewIframe.style.display = 'block';
            previewIframe.src = appIndexUrl;
        }
    }

    // Preview controls
    document.getElementById('btn-preview-refresh')?.addEventListener('click', function () {
        loadPreviewIframe();
    });
    document.getElementById('btn-preview-launch')?.addEventListener('click', function () {
        this.href = appIndexUrl;
    });
    document.getElementById('btn-preview-clear-console')?.addEventListener('click', function () {
        clearConsolePanel();
    });

    // "Fix Errors" button
    document.getElementById('btn-fix-errors')?.addEventListener('click', function () {
        const errors = capturedLogs.filter(e => e.level === 'error' || e.level === 'exception');
        if (errors.length === 0) {
            alert('No errors captured to fix.');
            return;
        }
        let notes = '# 🐛 Auto-detected Errors to Fix\n\n';
        notes += 'The following JavaScript errors/exceptions were captured from the running app.\n';
        notes += 'Please fix all of these issues:\n\n';
        errors.forEach((err, i) => {
            notes += `## Error ${i + 1}\n`;
            notes += '```\n' + err.message + '\n```\n';
            if (err.source) {
                notes += `Source: \`${err.source}\`\n`;
            }
            notes += '\n';
        });
        const warnings = capturedLogs.filter(e => e.level === 'warn');
        if (warnings.length > 0) {
            notes += '## Warnings (lower priority)\n\n';
            warnings.forEach((w) => {
                notes += `- ${w.message}\n`;
            });
            notes += '\n';
        }
        notes += '## Instructions\n';
        notes += '- Fix all the errors listed above\n';
        notes += '- Make sure the app loads without any JavaScript exceptions\n';
        notes += '- Test that all interactive features work correctly\n';

        const notesEditor = document.getElementById('notes-editor');
        if (notesEditor) {
            notesEditor.value = notes;
        }
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        const updateLink = document.querySelector('[data-section="section-update"]');
        if (updateLink) updateLink.classList.add('active');
        document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
        document.getElementById('section-update')?.classList.add('active');
    });

    // === Show/hide the app preview banner and iframe ===
    async function checkAppAvailable() {
        return await fileExists(basePath, 'code/index.html');
    }

    async function showAppPreview() {
        const available = await checkAppAvailable();
        const banner = document.getElementById('app-preview-banner');
        const navLaunch = document.getElementById('nav-launch-app');
        if (available) {
            if (banner) banner.style.display = 'flex';
            if (navLaunch) navLaunch.classList.add('visible');
            loadPreviewIframe();
        } else {
            if (banner) banner.style.display = 'none';
            if (navLaunch) navLaunch.classList.remove('visible');
        }
    }

    // === DocOps wrapper using selected models ===
    async function runDocOpWithModels(opPath, targetPath) {
        const models = getSelectedModels();
        return await runDocOp(sessionId, opPath, targetPath, models);
    }

    // === Normalize target path to match server status keys ===
    function normalizeTarget(target) {
        return target.replace(/\/+$/, '') || target;
    }

    function findTaskByTarget(tasks, target) {
        if (!tasks) return null;
        if (tasks[target]) return { key: target, task: tasks[target] };
        const normalized = normalizeTarget(target);
        if (tasks[normalized]) return { key: normalized, task: tasks[normalized] };
        const withSlash = target.endsWith('/') ? target : target + '/';
        if (tasks[withSlash]) return { key: withSlash, task: tasks[withSlash] };
        return null;
    }

    // === Task status UI updates ===
    function updateTaskStatusUI(target, taskInfo) {
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;
        let badgeId = null;

        if (target === 'code/') {
            if (codeOperationInFlight && activeCodeTaskSessionId && taskSessionId !== activeCodeTaskSessionId) {
                return;
            }
            badgeId = activeCodeBadge;
        }

        if (badgeId) {
            if (status === 'RUNNING') {
                setBadge(badgeId, 'running');
            } else if (status === 'COMPLETED') {
                setBadge(badgeId, 'done');
                if (target === 'code/') codeOperationInFlight = false;
            } else if (status === 'ERROR' || status === 'FAILED') {
                setBadge(badgeId, 'error');
                if (target === 'code/') codeOperationInFlight = false;
            }
        }
        updateSessionLinksUI(target, taskInfo);
    }

    function updateSessionLinksUI(target, taskInfo) {
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;

        const safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        const linkContainerId = 'session-link-' + safeTarget;

        let container = document.getElementById(linkContainerId);
        if (!container) {
            container = document.createElement('div');
            container.id = linkContainerId;
            container.className = 'session-link-container';

            const runBtn = document.querySelector(`button[data-output="${CSS.escape(target)}"]`);
            if (runBtn) {
                const buttonRow = runBtn.closest('.button-row');
                if (buttonRow) {
                    buttonRow.parentNode.insertBefore(container, buttonRow.nextSibling);
                }
            } else {
                const viewer = document.getElementById('viewer-render');
                if (viewer && viewer.parentElement) {
                    viewer.parentElement.insertBefore(container, viewer);
                } else {
                    return;
                }
            }
        }

        let actionText = 'Processing…';
        const runBtn = document.querySelector(`button[data-output="${CSS.escape(target)}"]`);
        if (runBtn) {
            const stepTitle = runBtn.closest('.step')?.querySelector('.step-title')?.textContent;
            if (stepTitle) actionText = stepTitle + '…';
        } else if (target.includes('README')) {
            actionText = 'Rendering project…';
        }

        if (status === 'RUNNING' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML = `<div class="session-monitor-link">
<span class="monitor-pulse">●</span>
<span>${escapeHtml(actionText)} </span>
<a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
📡 Monitor Live Session (${escapeHtml(taskSessionId)})
</a>
</div>`;
            container.style.display = 'block';
        } else if (status === 'COMPLETED' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            const completedAt = taskInfo.completedAt
                ? new Date(taskInfo.completedAt).toLocaleTimeString()
                : '';
            container.innerHTML = `<div class="session-completed-link">
<span>✅ Completed${completedAt ? ' at ' + completedAt : ''} — </span>
<a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
📋 View Session Log (${escapeHtml(taskSessionId)})
</a>
</div>`;
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            const proxyUrl = taskSessionId ? getProxyUrl(taskSessionId) : '#';
            container.innerHTML = `<div class="session-error-link">
<span>❌ Failed — </span>
${taskSessionId ? `<a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
🔍 View Error Log (${escapeHtml(taskSessionId)})
</a>` : '<span>No session available</span>'}
</div>`;
            container.style.display = 'block';
        } else {
            container.style.display = 'none';
        }
    }

    // === Status polling ===
    async function pollStatus() {
        const statusData = await fetchDocopsStatus(basePath);
        if (!statusData || !statusData.tasks) return;
        let anyRunning = false;
        for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
            if (taskInfo.sessionId) {
                trackTaskSession(taskInfo.sessionId);
            }
            const effectiveTarget = (target === 'code' || target === 'code/') ? 'code/' : target;
            updateTaskStatusUI(effectiveTarget, taskInfo);
            if (taskInfo.status === 'RUNNING') {
                anyRunning = true;
            }
            if ((target === 'code/' || target === 'code') && taskInfo.sessionId &&
                (!activeCodeTaskSessionId || taskInfo.sessionId === activeCodeTaskSessionId)) {
                updateResultsSessionLink(taskInfo);
            }
        }
        updatePipelineDiagram(statusData.tasks);
        return anyRunning;
    }

    function updatePipelineDiagram(tasks) {
        const stageMap = {
            'input': { targets: ['idea.md'] },
            'render': { targets: ['code/'] },
            'output': { targets: ['code/'] },
        };
        for (const [stage, info] of Object.entries(stageMap)) {
            const el = document.querySelector(`.pipeline-stage[data-stage="${stage}"]`);
            if (!el) continue;
            let stageStatus = 'pending';
            let anyRunning = false;
            let allDone = true;
            let anyTarget = false;
            let activeTaskId = null;
            for (const t of info.targets) {
                const found = findTaskByTarget(tasks, t);
                if (!found) { allDone = false; continue; }
                const task = found.task;
                if (t === 'code/' && activeCodeTaskSessionId && task.sessionId !== activeCodeTaskSessionId) {
                    allDone = false;
                    continue;
                }
                anyTarget = true;
                if (task.status === 'RUNNING') {
                    anyRunning = true;
                    activeTaskId = task.sessionId;
                }
                if (task.status !== 'COMPLETED') allDone = false;
            }
            if (anyRunning) {
                stageStatus = 'running';
            } else if (anyTarget && allDone) {
                stageStatus = 'done';
            }
            el.classList.remove('active', 'done');
            const statusEl = el.querySelector('.stage-status');
            if (stageStatus === 'running') {
                el.classList.add('active');
                if (statusEl) {
                    if (activeTaskId) {
                        const proxyUrl = getProxyUrl(activeTaskId);
                        statusEl.innerHTML = `<a href="${proxyUrl}" target="_blank" class="monitor-link" style="color: inherit; text-decoration: underline;">Running…</a>`;
                    } else {
                        statusEl.textContent = 'Running…';
                    }
                }
            } else if (stageStatus === 'done') {
                el.classList.add('done');
                if (statusEl) statusEl.textContent = 'Done';
            }
        }
    }

    function startStatusPolling() {
        if (statusPollTimer) return;
        statusPollTimer = setInterval(async () => {
            await pollStatus();
        }, STATUS_POLL_INTERVAL);
        pollStatus();
    }

    function stopStatusPolling() {
        if (statusPollTimer) {
            clearInterval(statusPollTimer);
            statusPollTimer = null;
        }
    }

    // === Helper: wait for task completion by polling status ===
    async function waitForTaskCompletion(targetPath, maxWaitMs) {
        const maxWait = maxWaitMs || 600000;
        const pollInterval = 2000;
        const startTime = Date.now();
        while (Date.now() - startTime < maxWait) {
            const statusData = await fetchDocopsStatus(basePath);
            if (statusData && statusData.tasks) {
                const found = findTaskByTarget(statusData.tasks, targetPath);
                if (!found) {
                    await new Promise(resolve => setTimeout(resolve, pollInterval));
                    continue;
                }
                const task = found.task;
                if (task.status === 'COMPLETED') {
                    return task;
                } else if (task.status === 'ERROR' || task.status === 'FAILED') {
                    throw new Error(`Task ${targetPath} failed (session: ${task.sessionId || 'unknown'})`);
                }
            }
            await new Promise(resolve => setTimeout(resolve, pollInterval));
        }
        throw new Error(`Task ${targetPath} timed out after ${maxWait / 1000}s`);
    }

    // === Navigation ===
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function (e) {
            const sectionId = this.dataset.section;
            if (!sectionId) return;
            e.preventDefault();
            document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
            document.getElementById(sectionId).classList.add('active');
        });
    });

    // === Results Tabs ===
    document.querySelectorAll('.results-tab').forEach(tab => {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.results-tab').forEach(t => t.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
            document.getElementById(this.dataset.tab).classList.add('active');
        });
    });

    // === Load initial files ===
    async function loadInitialFiles() {
        try {
            const content = await readFile(basePath, 'idea.md');
            if (content !== null) {
                document.getElementById('idea-editor').value = content;
            }
        } catch (e) {
            console.warn('Could not load idea.md:', e);
        }
        try {
            const notesContent = await readFile(basePath, 'notes.md');
            if (notesContent !== null) {
                document.getElementById('notes-editor').value = notesContent;
            }
        } catch (e) {
            console.warn('Could not load notes.md:', e);
        }
    }

    // === Save idea ===
    document.getElementById('save-idea').addEventListener('click', async function () {
        const content = document.getElementById('idea-editor').value;
        try {
            this.disabled = true;
            await writeFile(basePath, 'idea.md', content);
            setStatus('idea-status', '✓ Saved successfully', 'success');
        } catch (e) {
            setStatus('idea-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Save notes ===
    document.getElementById('save-notes')?.addEventListener('click', async function () {
        const content = document.getElementById('notes-editor').value;
        try {
            this.disabled = true;
            await writeFile(basePath, 'notes.md', content);
            setStatus('notes-status', '✓ Saved successfully', 'success');
        } catch (e) {
            setStatus('notes-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Run Update ===
    document.getElementById('run-update')?.addEventListener('click', async function () {
        const notesContent = document.getElementById('notes-editor').value;
        if (!notesContent.trim()) {
            alert('Please write some update notes first describing what you\'d like to change.');
            return;
        }
        try {
            await writeFile(basePath, 'notes.md', notesContent);
        } catch (e) {
            console.warn('Could not auto-save notes.md:', e);
        }
        const ideaContent = document.getElementById('idea-editor').value;
        if (ideaContent.trim()) {
            try {
                await writeFile(basePath, 'idea.md', ideaContent);
            } catch (e) {
                console.warn('Could not auto-save idea.md:', e);
            }
        }
        const badgeId = 'badge-update';
        const outputPath = 'code/';
        setBadge(badgeId, 'running');
        activeCodeBadge = badgeId;
        codeOperationInFlight = true;
        activeCodeTaskSessionId = null;
        this.disabled = true;
        startStatusPolling();
        try {
            const taskId = await runDocOpWithModels('ops/update_op.md', outputPath);
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                activeCodeTaskSessionId = cleanTaskId;
                updateSessionLinksUI(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                logBatchHtml(`Update session started: <a href="${getProxyUrl(cleanTaskId)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                trackTaskSession(cleanTaskId);
            }
            await waitForTaskCompletion(outputPath);
            setBadge(badgeId, 'done');
            const viewer = document.getElementById('viewer-update');
            if (viewer) {
                try {
                    const content = await readFile(basePath, 'code/README.md');
                    if (content) {
                        viewer.innerHTML = renderMarkdown(content);
                        viewer.classList.add('visible');
                    }
                } catch (e) { /* non-critical */ }
            }
            await showAppPreview();
            await loadPreviewIframe();
            document.getElementById('btn-refresh-files-results')?.click();
            const updateTime = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }).toLowerCase();
            await autoGitCommitAfterBuild('update at ' + updateTime);
            await refreshAllUsage();
        } catch (e) {
            setBadge(badgeId, 'error');
            alert('Update failed: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });

    // === Save model settings ===
    document.getElementById('save-models')?.addEventListener('click', function () {
        const models = getSelectedModels();
        saveModelSelections('webappFactory', models);
        setStatus('model-status', '✓ Model settings saved', 'success');
    });

    // === Refresh models ===
    document.getElementById('refresh-models')?.addEventListener('click', async function () {
        this.disabled = true;
        setStatus('model-status', 'Loading models…', '');
        await loadModels();
        setStatus('model-status', '✓ Models refreshed', 'success');
        this.disabled = false;
    });

    // === View file buttons ===
    async function viewFile(filePath, viewerId) {
        const viewer = document.getElementById(viewerId);
        if (!viewer) return;
        if (viewer.classList.contains('visible')) {
            viewer.classList.remove('visible');
            return;
        }
        try {
            const content = await readFile(basePath, filePath);
            if (content === null) {
                viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
            } else {
                viewer.innerHTML = renderMarkdown(content);
            }
            viewer.classList.add('visible');
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error loading file: ' + escapeHtml(e.message) + '</p>';
            viewer.classList.add('visible');
        }
    }

    document.querySelectorAll('.btn-view').forEach(btn => {
        btn.addEventListener('click', function () {
            viewFile(this.dataset.file, this.dataset.viewer);
        });
    });

    document.querySelectorAll('.results-content .btn-secondary[data-file]').forEach(btn => {
        btn.addEventListener('click', async function () {
            const filePath = this.dataset.file;
            const viewerId = this.dataset.viewer;
            const viewer = document.getElementById(viewerId);
            if (!viewer) return;
            try {
                const content = await readFile(basePath, filePath);
                if (content === null) {
                    viewer.innerHTML = '<p class="placeholder">File not found. Run the pipeline first.</p>';
                } else {
                    viewer.innerHTML = renderMarkdown(content);
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            }
        });
    });

    // === Run operation buttons ===
    document.querySelectorAll('.btn-run').forEach(btn => {
        btn.addEventListener('click', async function () {
            const opPath = this.dataset.op;
            const badgeId = this.dataset.badge;
            const outputPath = this.dataset.output;
            const viewerId = this.dataset.viewer;

            if (outputPath === 'code/') {
                activeCodeBadge = badgeId;
                codeOperationInFlight = true;
                activeCodeTaskSessionId = null;
            }

            const ideaContent = document.getElementById('idea-editor').value;
            if (ideaContent.trim()) {
                try {
                    await writeFile(basePath, 'idea.md', ideaContent);
                } catch (e) {
                    console.warn('Could not auto-save idea.md:', e);
                }
            } else {
                alert('Please describe your webapp idea first.');
                return;
            }
            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();
            try {
                const taskId = await runDocOpWithModels(opPath, outputPath);
                const cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    if (outputPath === 'code/') activeCodeTaskSessionId = cleanTaskId;
                    updateSessionLinksUI(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                    logBatchHtml(`Session started: <a href="${getProxyUrl(cleanTaskId)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                    trackTaskSession(cleanTaskId);
                }
                await waitForTaskCompletion(outputPath);
                setBadge(badgeId, 'done');
                if (viewerId) {
                    const viewer = document.getElementById(viewerId);
                    if (viewer) {
                        try {
                            const content = await readFile(basePath, outputPath + 'README.md');
                            if (content) {
                                viewer.innerHTML = renderMarkdown(content);
                                viewer.classList.add('visible');
                            }
                        } catch (e) { /* non-critical */ }
                    }
                }
            } catch (e) {
                setBadge(badgeId, 'error');
                alert('Operation failed: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    });

    // === Run All ===
    document.getElementById('run-all').addEventListener('click', async function () {
        this.disabled = true;
        startStatusPolling();
        activeCodeBadge = 'badge-render';
        codeOperationInFlight = true;
        activeCodeTaskSessionId = null;
        if (batchLog) batchLog.innerHTML = '';

        const ideaContent = document.getElementById('idea-editor').value;
        if (ideaContent.trim()) {
            try {
                await writeFile(basePath, 'idea.md', ideaContent);
                logBatch('Saved idea.md', 'success');
            } catch (e) {
                logBatch('Warning: Could not save idea.md: ' + e.message, 'warn');
            }
        } else {
            logBatch('Warning: Idea is empty. Please describe your webapp first.', 'warn');
            this.disabled = false;
            return;
        }
        try {
            logBatch('Starting: Render Project', 'info');
            setBadge('badge-render', 'running');
            const taskId = await runDocOpWithModels('ops/render_op.md', 'code/');
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                activeCodeTaskSessionId = cleanTaskId;
                const proxyUrl = getProxyUrl(cleanTaskId);
                logBatchHtml(`Session started: <a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                updateSessionLinksUI('code/', { status: 'RUNNING', sessionId: cleanTaskId });
                trackTaskSession(cleanTaskId);
            }
            await waitForTaskCompletion('code/');
            setBadge('badge-render', 'done');

            const finalStatus = await fetchDocopsStatus(basePath);
            const completedTaskFound = finalStatus?.tasks ? findTaskByTarget(finalStatus.tasks, 'code/') : null;
            const completedTask = completedTaskFound?.task;
            if (completedTask && completedTask.sessionId) {
                const proxyUrl = getProxyUrl(completedTask.sessionId);
                logBatchHtml(`✓ Completed: Build Project — <a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">📋 View Session Log (${escapeHtml(completedTask.sessionId)})</a>`, 'success');
            } else {
                logBatch('✓ Completed: Build Project', 'success');
            }

            try {
                const content = await readFile(basePath, 'code/README.md');
                if (content) {
                    const viewer = document.getElementById('viewer-render');
                    if (viewer) {
                        viewer.innerHTML = renderMarkdown(content);
                        viewer.classList.add('visible');
                    }
                }
            } catch (e) { /* non-critical */ }

            logBatch('🎉 Pipeline complete! Check the Results tab for output.', 'success');
            await showAppPreview();
            logBatch('🖥️ Live preview loaded — check the Results tab to see your app and console output.', 'info');
            document.getElementById('btn-refresh-files-results')?.click();

            const buildTime = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }).toLowerCase();
            await autoGitCommitAfterBuild('initial build at ' + buildTime);
            await refreshAllUsage();

            try {
                const readmeContent = await readFile(basePath, 'code/README.md');
                if (readmeContent) {
                    const resultReadme = document.getElementById('result-readme');
                    if (resultReadme) {
                        resultReadme.innerHTML = renderMarkdown(readmeContent);
                    }
                }
            } catch (e) { /* non-critical */ }
        } catch (e) {
            setBadge('badge-render', 'error');
            const failStatus = await fetchDocopsStatus(basePath).catch(() => null);
            const failedTaskFound = failStatus?.tasks ? findTaskByTarget(failStatus.tasks, 'code/') : null;
            const failedTask = failedTaskFound?.task;
            if (failedTask && failedTask.sessionId) {
                const proxyUrl = getProxyUrl(failedTask.sessionId);
                logBatchHtml(`Pipeline failed: ${escapeHtml(e.message)} — <a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">🔍 View Error Log (${escapeHtml(failedTask.sessionId)})</a>`, 'error');
            } else {
                logBatch('Pipeline failed: ' + e.message, 'error');
            }
        } finally {
            this.disabled = false;
        }
    });

    // === Results section: refresh project files ===
    document.getElementById('btn-refresh-files-results')?.addEventListener('click', async function () {
        const container = document.getElementById('files-results-container');
        try {
            const projectFiles = await listFiles(basePath, 'code');
            if (projectFiles.length === 0) {
                container.innerHTML = '<p class="placeholder">No project files found. Run the pipeline first.</p>';
                return;
            }
            container.innerHTML = '';
            for (const entry of projectFiles) {
                const section = document.createElement('div');
                section.className = 'result-file-section';
                const icon = getFileIcon(entry.name);
                const header = document.createElement('div');
                header.className = 'result-file-header';
                header.innerHTML = `<span class="result-file-icon">${icon}</span> <span>${escapeHtml(entry.name)}</span>`;
                header.style.cursor = 'pointer';
                const body = document.createElement('div');
                body.className = 'result-file-body';
                body.style.display = 'none';
                header.addEventListener('click', async function () {
                    if (body.style.display === 'none') {
                        if (!body.dataset.loaded) {
                            try {
                                const content = await readFile(basePath, 'code/' + entry.name);
                                if (content) {
                                    if (entry.name.endsWith('.md')) {
                                        body.innerHTML = renderMarkdown(content);
                                    } else {
                                        body.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                                    }
                                } else {
                                    body.innerHTML = '<p class="placeholder">Empty file.</p>';
                                }
                            } catch (e) {
                                body.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error loading.</p>';
                            }
                            body.dataset.loaded = 'true';
                        }
                        body.style.display = 'block';
                    } else {
                        body.style.display = 'none';
                    }
                });
                section.appendChild(header);
                section.appendChild(body);
                container.appendChild(section);
            }
        } catch (e) {
            container.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    });

    // === Check existing files on load ===
    async function checkExistingFiles() {
        const statusData = await fetchDocopsStatus(basePath);
        let anyRunning = false;
        if (statusData && statusData.tasks) {
            for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
                if (target === 'code/' || target === 'code') {
                    const status = taskInfo.status;
                    if (status === 'RUNNING') {
                        anyRunning = true;
                        codeOperationInFlight = true;
                        activeCodeTaskSessionId = taskInfo.sessionId || null;
                        setBadge(activeCodeBadge, 'running');
                    } else if (status === 'COMPLETED') {
                        setBadge('badge-render', 'done');
                    } else if (status === 'ERROR' || status === 'FAILED') {
                        setBadge('badge-render', 'error');
                    }
                    updateSessionLinksUI('code/', taskInfo);
                    if (taskInfo.sessionId) {
                        updateResultsSessionLink(taskInfo);
                    }
                } else {
                    updateTaskStatusUI(target, taskInfo);
                    if (taskInfo.status === 'RUNNING') {
                        anyRunning = true;
                    }
                }
            }
        }

        // Check if idea.md exists and mark input stage accordingly
        try {
            const ideaContent = await readFile(basePath, 'idea.md');
            if (ideaContent !== null && ideaContent.trim().length > 0) {
                const inputStage = document.querySelector('.pipeline-stage[data-stage="input"]');
                if (inputStage) {
                    inputStage.classList.add('done');
                    const statusEl = inputStage.querySelector('.stage-status');
                    if (statusEl) statusEl.textContent = 'Done';
                }
            }
        } catch (e) { /* ignore */ }

        // Fall back to file existence checks
        const checks = [
            { files: ['code/index.html', 'code/README.md'], badge: 'badge-render' },
        ];
        for (const check of checks) {
            const badge = document.getElementById(check.badge);
            if (badge && badge.classList.contains('running')) continue;
            if (badge && badge.textContent === 'done') continue;
            try {
                let found = false;
                for (const file of check.files) {
                    const content = await readFile(basePath, file);
                    if (content !== null && content.trim().length > 0) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    setBadge(check.badge, 'done');
                    const renderStage = document.querySelector('.pipeline-stage[data-stage="render"]');
                    if (renderStage) {
                        renderStage.classList.add('done');
                        const statusEl = renderStage.querySelector('.stage-status');
                        if (statusEl) statusEl.textContent = 'Done';
                    }
                    const outputStage = document.querySelector('.pipeline-stage[data-stage="output"]');
                    if (outputStage) {
                        outputStage.classList.add('done');
                        const statusEl = outputStage.querySelector('.stage-status');
                        if (statusEl) statusEl.textContent = 'Done';
                    }
                }
            } catch (e) { /* leave as pending */ }
        }
        if (anyRunning) {
            startStatusPolling();
        }
    }

    // === Update results section session link ===
    function updateResultsSessionLink(taskInfo) {
        const container = document.getElementById('result-session-link');
        if (!container) return;
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;
        if (!taskSessionId) {
            container.style.display = 'none';
            return;
        }
        const proxyUrl = getProxyUrl(taskSessionId);
        if (status === 'RUNNING') {
            container.innerHTML = `<div class="session-monitor-link">
<span class="monitor-pulse">●</span>
<span>Rendering project… </span>
<a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
📡 Monitor Live Session (${escapeHtml(taskSessionId)})
</a>
</div>`;
            container.style.display = 'block';
        } else if (status === 'COMPLETED') {
            const completedAt = taskInfo.completedAt
                ? new Date(taskInfo.completedAt).toLocaleTimeString()
                : '';
            container.innerHTML = `<div class="session-completed-link">
<span>✅ Generated${completedAt ? ' at ' + completedAt : ''} — </span>
<a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
📋 View Generation Session (${escapeHtml(taskSessionId)})
</a>
</div>`;
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            container.innerHTML = `<div class="session-error-link">
<span>❌ Generation failed — </span>
<a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
🔍 View Error Log (${escapeHtml(taskSessionId)})
</a>
</div>`;
            container.style.display = 'block';
        } else {
            container.style.display = 'none';
        }
    }

    // =========================================================
    // === ZIP Download Helpers ===
    // =========================================================
    function downloadZip(path) {
        if (!sessionId) {
            alert('No session ID available. Cannot download ZIP.');
            return;
        }
        const encodedPath = encodeURIComponent(path || '/');
        window.location.href = `${appBase}/fileZip?session=${encodeURIComponent(sessionId)}&path=${encodedPath}`;
    }

    document.getElementById('btn-zip-project-pipeline')?.addEventListener('click', function () {
        downloadZip('/code');
    });
    document.getElementById('btn-zip-from-results')?.addEventListener('click', function () {
        downloadZip('/code');
    });
    document.getElementById('btn-zip-whole-project')?.addEventListener('click', function () {
        downloadZip('/');
    });
    document.getElementById('btn-zip-code-only')?.addEventListener('click', function () {
        downloadZip('/code');
    });
    document.getElementById('btn-zip-custom')?.addEventListener('click', function () {
        const customPath = document.getElementById('zip-custom-path')?.value?.trim() || '/';
        downloadZip(customPath);
    });

    // =========================================================
    // === Git Operations (using git.js utility) ===
    // =========================================================

    async function refreshGitStatus() {
        const display = document.getElementById('git-status-display');
        if (!display) return;
        display.style.display = 'block';
        display.innerHTML = '<p class="placeholder">Loading status…</p>';
        try {
            const data = await getStatus(basePath);
            if (!data.initialized) {
                display.innerHTML = `
                    <div class="git-status-box">
                        <div class="git-status-header">
                            <span class="git-status-indicator uninit">⚪ Not Initialized</span>
                        </div>
                        <p style="color:var(--color-text-muted); font-size:0.9rem;">
                            ${escapeHtml(data.message || 'No Git repository found.')}
                            Click <strong>Initialize Repository</strong> to start tracking changes.
                        </p>
                    </div>`;
                return data;
            }
            const cleanClass = data.clean ? 'clean' : 'dirty';
            const cleanLabel = data.clean ? '✅ Clean' : '⚠️ Uncommitted Changes';
            let changesHtml = '';
            if (data.changes && data.changes.length > 0) {
                const changeItems = data.changes.map(c => {
                    const badgeClass = getChangeBadgeClass(c.status);
                    const label = getChangeLabel(c.status);
                    return `<li><span class="git-change-badge ${badgeClass}">${escapeHtml(c.status)}</span> <span>${escapeHtml(c.file)}</span> <span style="color:var(--color-text-muted);font-size:0.75rem;">${escapeHtml(label)}</span></li>`;
                }).join('');
                changesHtml = `
                    <div>
                        <strong style="font-size:0.88rem; color:var(--color-text-muted);">Changes (${data.changes.length}):</strong>
                        <ul class="git-changes-list" style="margin-top:0.4rem;">${changeItems}</ul>
                    </div>`;
            }
            display.innerHTML = `
                <div class="git-status-box">
                    <div class="git-status-header">
                        <span class="git-status-indicator ${cleanClass}">${cleanLabel}</span>
                        <span class="git-branch-badge">🌿 ${escapeHtml(data.currentBranch || 'unknown')}</span>
                    </div>
                    ${changesHtml}
                </div>`;
            return data;
        } catch (e) {
            display.innerHTML = `<p class="placeholder" style="color:var(--color-danger);">Error: ${escapeHtml(e.message)}</p>`;
            return null;
        }
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

    document.getElementById('btn-git-status')?.addEventListener('click', refreshGitStatus);

    // --- Git Init ---
    document.getElementById('btn-git-init')?.addEventListener('click', async function () {
        this.disabled = true;
        setStatus('git-status-msg', 'Initializing repository…', '');
        try {
            const data = await initRepository(basePath);
            setStatus('git-status-msg', '✓ ' + (data.message || 'Repository initialized'), 'success');
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-status-msg', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // --- Git Commit ---
    document.getElementById('btn-git-commit')?.addEventListener('click', async function () {
        const messageInput = document.getElementById('git-commit-message');
        const message = messageInput?.value?.trim() || '';
        this.disabled = true;
        setStatus('git-commit-status', 'Committing…', '');
        try {
            const data = await commit(basePath, message || undefined);
            const commitHash = data.commitHash ? ` (${data.commitHash.substring(0, 8)})` : '';
            setStatus('git-commit-status', '✓ ' + (data.message || 'Committed') + commitHash, 'success');
            if (messageInput) messageInput.value = '';
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-commit-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // --- Git Branches ---
    async function refreshGitBranches() {
        const display = document.getElementById('git-branches-display');
        if (!display) return;
        display.style.display = 'block';
        display.innerHTML = '<p class="placeholder">Loading branches…</p>';
        try {
            const data = await getBranches(basePath);
            if (!data.branches || data.branches.length === 0) {
                display.innerHTML = '<p class="placeholder">No branches found. Initialize the repository first.</p>';
                return data;
            }
            const branchItems = data.branches.map(b => {
                const currentClass = b.current ? ' current' : '';
                const currentTag = b.current ? '<span class="branch-current-tag">● current</span>' : '';
                return `<li class="git-branch-item${currentClass}" data-branch="${escapeHtml(b.name)}">
                    <span>🌿</span>
                    <span class="branch-name">${escapeHtml(b.name)}</span>
                    ${currentTag}
                </li>`;
            }).join('');
            display.innerHTML = `<ul class="git-branch-list">${branchItems}</ul>`;

            display.querySelectorAll('.git-branch-item:not(.current)').forEach(item => {
                item.addEventListener('click', async function () {
                    const branchName = this.dataset.branch;
                    if (!branchName) return;
                    if (!confirm(`Switch to branch "${branchName}"?`)) return;
                    setStatus('git-branch-status', 'Switching…', '');
                    try {
                        await checkout(basePath, branchName, false);
                        setStatus('git-branch-status', '✓ Switched to ' + branchName, 'success');
                        await refreshGitBranches();
                        await refreshGitStatus();
                    } catch (e) {
                        setStatus('git-branch-status', '✗ ' + e.message, 'error');
                    }
                });
            });
            return data;
        } catch (e) {
            display.innerHTML = `<p class="placeholder" style="color:var(--color-danger);">Error: ${escapeHtml(e.message)}</p>`;
            return null;
        }
    }

    document.getElementById('btn-git-branches')?.addEventListener('click', refreshGitBranches);

    document.getElementById('btn-git-checkout')?.addEventListener('click', async function () {
        const branchName = document.getElementById('git-branch-name')?.value?.trim();
        if (!branchName) {
            alert('Please enter a branch name.');
            return;
        }
        this.disabled = true;
        setStatus('git-branch-status', 'Switching…', '');
        try {
            const data = await checkout(basePath, branchName, false);
            setStatus('git-branch-status', '✓ ' + (data.message || 'Switched to ' + branchName), 'success');
            document.getElementById('git-branch-name').value = '';
            await refreshGitBranches();
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-branch-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('btn-git-create-branch')?.addEventListener('click', async function () {
        const branchName = document.getElementById('git-branch-name')?.value?.trim();
        if (!branchName) {
            alert('Please enter a name for the new branch.');
            return;
        }
        this.disabled = true;
        setStatus('git-branch-status', 'Creating branch…', '');
        try {
            const data = await checkout(basePath, branchName, true);
            setStatus('git-branch-status', '✓ ' + (data.message || 'Created and switched to ' + branchName), 'success');
            document.getElementById('git-branch-name').value = '';
            await refreshGitBranches();
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-branch-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // --- Git Log ---
    async function refreshGitLog() {
        const display = document.getElementById('git-log-display');
        if (!display) return;
        display.style.display = 'block';
        display.innerHTML = '<p class="placeholder">Loading commit history…</p>';
        const maxCount = parseInt(document.getElementById('git-log-count')?.value || '20', 10);
        try {
            const data = await getLog(basePath, maxCount);
            if (!data.commits || data.commits.length === 0) {
                display.innerHTML = '<p class="placeholder">No commits found. Make your first commit!</p>';
                return;
            }
            const commitItems = data.commits.map(c => {
                const shortHash = (c.hash || '').substring(0, 8);
                const date = c.date ? new Date(c.date).toLocaleString() : '';
                return `<div class="git-commit-entry">
                    <span class="git-commit-hash" title="${escapeHtml(c.hash || '')}">${escapeHtml(shortHash)}</span>
                    <div class="git-commit-info">
                        <div class="git-commit-message">${escapeHtml(c.message || '(no message)')}</div>
                        <div class="git-commit-meta">${escapeHtml(c.author || '')} · ${escapeHtml(date)}</div>
                    </div>
                </div>`;
            }).join('');
            display.innerHTML = `<div class="git-commit-list">${commitItems}</div>`;
        } catch (e) {
            display.innerHTML = `<p class="placeholder" style="color:var(--color-danger);">Error: ${escapeHtml(e.message)}</p>`;
        }
    }

    document.getElementById('btn-git-log')?.addEventListener('click', refreshGitLog);

    // =========================================================
    // === Git Quick Actions ===
    // =========================================================

    document.getElementById('quick-snapshot')?.addEventListener('click', async function () {
        setStatus('git-quick-status', 'Taking snapshot…', '');
        try {
            const status = await getStatus(basePath);
            if (!status.initialized) {
                await initRepository(basePath);
            }
            const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19);
            const data = await commit(basePath, 'Snapshot ' + timestamp);
            const commitHash = data.commitHash ? ` (${data.commitHash.substring(0, 8)})` : '';
            setStatus('git-quick-status', '✓ Snapshot taken' + commitHash, 'success');
            await refreshGitStatus();
            await refreshGitLog();
        } catch (e) {
            setStatus('git-quick-status', '✗ ' + e.message, 'error');
        }
    });

    document.getElementById('quick-branch-experiment')?.addEventListener('click', async function () {
        setStatus('git-quick-status', 'Setting up experiment…', '');
        try {
            const status = await getStatus(basePath);
            if (!status.initialized) {
                await initRepository(basePath);
            }
            if (!status.clean) {
                await commit(basePath, 'Save before experiment');
            }
            const expName = 'experiment-' + Date.now().toString(36);
            await checkout(basePath, expName, true);
            setStatus('git-quick-status', '✓ Switched to branch: ' + expName, 'success');
            await refreshGitBranches();
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-quick-status', '✗ ' + e.message, 'error');
        }
    });

    document.getElementById('quick-backup-zip')?.addEventListener('click', async function () {
        setStatus('git-quick-status', 'Backing up…', '');
        try {
            const status = await getStatus(basePath);
            if (!status.initialized) {
                await initRepository(basePath);
            }
            const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19);
            await commit(basePath, 'Backup ' + timestamp);
            setStatus('git-quick-status', '✓ Committed. Downloading ZIP…', 'success');
            downloadZip('/');
        } catch (e) {
            if (e.message && e.message.includes('Nothing to commit')) {
                setStatus('git-quick-status', '✓ Already up to date. Downloading ZIP…', 'success');
                downloadZip('/');
            } else {
                setStatus('git-quick-status', '✗ ' + e.message, 'error');
            }
        }
    });

    // =========================================================
    // === Auto-commit after successful pipeline runs ===
    // =========================================================
    async function autoGitCommitAfterBuild(message) {
        try {
            let status = await getStatus(basePath);
            if (!status.initialized) {
                await initRepository(basePath);
                logBatch('📌 Auto-initialized Git repository', 'info');
                status = await getStatus(basePath);
            }
            if (status.initialized && !status.clean) {
                await commit(basePath, message || 'Auto-commit after build');
                logBatch('📌 Auto-committed changes to Git: ' + message, 'success');
            }
        } catch (e) {
            console.warn('Auto-commit failed:', e);
        }
    }

    // =========================================================
    // === Usage Tracking (using usage.js utility) ===
    // =========================================================

    function renderUsageTable(usageData, containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;

        if (!usageData || !usageData.models || usageData.models.length === 0) {
            container.innerHTML = '<p class="placeholder">No usage data available.</p>';
            return;
        }

        container.innerHTML = createUsageTableHtml(usageData.models, usageData.totals);
    }

    function updateUsageSummaryBanner(usageData) {
        const banner = document.getElementById('usage-summary-banner');
        if (!banner) return;

        if (!usageData || !usageData.totals) {
            banner.style.display = 'none';
            return;
        }

        banner.style.display = 'flex';
        renderUsageSummary(usageData.totals, {
            prompt: document.getElementById('usage-total-prompt'),
            completion: document.getElementById('usage-total-completion'),
            cost: document.getElementById('usage-total-cost')
        });
    }

    async function refreshAllUsage() {
        setStatus('usage-status', 'Loading usage data…', '');

        const statusData = await fetchDocopsStatus(basePath);
        if (statusData && statusData.tasks) {
            for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
                if (taskInfo.sessionId) {
                    knownTaskSessionIds.add(taskInfo.sessionId);
                }
            }
        }

        if (knownTaskSessionIds.size === 0) {
            setStatus('usage-status', 'No task sessions found yet.', '');
            return;
        }

        const sessionIds = Array.from(knownTaskSessionIds);
        const { models: allModels, totals, sessionUsageMap } = await aggregateUsage(sessionIds);

        const aggregated = {
            models: allModels,
            totals: totals
        };

        lastUsageData = aggregated;

        renderUsageTable(aggregated, 'usage-table-container');
        updateUsageSummaryBanner(aggregated);
        renderUsageTable(aggregated, 'usage-tab-container');

        // Render per-task usage
        const taskUsageList = [];
        for (const [sid, usage] of Object.entries(sessionUsageMap)) {
            if (usage && usage.models) {
                taskUsageList.push({ sessionId: sid, usage: usage });
            }
        }
        renderTaskUsageList(taskUsageList);

        if (aggregated.models.length > 0) {
            setStatus('usage-status', `✓ Loaded usage from ${knownTaskSessionIds.size} session(s)`, 'success');
        } else {
            setStatus('usage-status', 'No usage data recorded yet.', '');
        }
    }

    function renderTaskUsageList(taskUsageList) {
        const container = document.getElementById('task-usage-container');
        if (!container) return;

        if (taskUsageList.length === 0) {
            container.innerHTML = '<p class="placeholder">No task usage data available.</p>';
            return;
        }

        let html = '';
        taskUsageList.forEach(item => {
            const proxyUrl = getProxyUrl(item.sessionId);
            const totals = item.usage.totals || {};
            html += `<div class="task-usage-entry">
                <div class="task-usage-header">
                    <a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">📡 ${escapeHtml(item.sessionId)}</a>
                    <span class="task-usage-cost">${formatCost(totals.cost)}</span>
                </div>
                <div class="task-usage-details">`;

            if (item.usage.models) {
                item.usage.models.forEach(m => {
                    html += `<span class="task-usage-model">
                        <code>${escapeHtml(m.model)}</code>:
                        ${formatTokenCount((m.prompt_tokens || 0) + (m.completion_tokens || 0))} tokens,
                        ${formatCost(m.cost)}
                    </span>`;
                });
            }

            html += `</div></div>`;
        });

        container.innerHTML = html;
    }

    function trackTaskSession(taskSessionId) {
        if (taskSessionId && /^[a-zA-Z0-9-]+$/.test(taskSessionId)) {
            knownTaskSessionIds.add(taskSessionId);
        }
    }

    // Usage button handlers
    document.getElementById('btn-refresh-usage')?.addEventListener('click', refreshAllUsage);
    document.getElementById('btn-refresh-task-usage')?.addEventListener('click', refreshAllUsage);
    document.getElementById('btn-refresh-usage-tab')?.addEventListener('click', refreshAllUsage);

    document.getElementById('btn-usage-json')?.addEventListener('click', async function () {
        if (lastUsageData) {
            const jsonStr = JSON.stringify(lastUsageData, null, 2);
            const win = window.open('', '_blank');
            if (win) {
                win.document.write('<pre>' + escapeHtml(jsonStr) + '</pre>');
                win.document.title = 'Usage Data (JSON)';
            }
        } else {
            alert('No usage data loaded yet. Click "Refresh Usage" first.');
        }
    });

    // === Initialize ===
    loadInitialFiles();
    checkExistingFiles();
    showAppPreview();
    startStatusPolling();
    loadModels();
    setTimeout(refreshAllUsage, 2000);
})();