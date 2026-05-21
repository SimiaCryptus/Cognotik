import {
    parseSessionUrl,
    getProxyUrl,
    getAppRoot
} from './utils/session.js';
import {
    readFile,
    writeFile,
    fileExists,
    listFiles
} from './utils/fileIO.js';
import {
    runDocOp,
    fetchDocopsStatus,
    waitForTask,
    createStatusPoller
} from './utils/docops.js';
import {
    loadApiProviders,
    populateModelDropdowns,
    saveModelSelections,
    loadModelSelections
} from './utils/models.js';
import {
    renderMarkdown,
    escapeHtml,
    setStatus,
    setBadge,
    createBatchLogger,
    getFileIcon
} from './utils/ui.js';
import {
    gitApiCall,
    getStatus as gitGetStatus,
    initRepository as gitInit,
    commit as gitCommit,
    getBranches as gitGetBranches,
    checkout as gitCheckout,
    getLog as gitGetLog
} from './utils/git.js';
import {
    fetchUsageData,
    aggregateUsage,
    formatTokenCount,
    formatCost,
    renderUsageSummary,
    createUsageTableHtml
} from './utils/usage.js';
import {
    createSessionLinkManager
} from './utils/sessionLinks.js';

(function() {
    'use strict';

    // === Session / URL setup ===
    const { basePath, sessionId, appId } = parseSessionUrl();
    const appBase = getAppRoot();
    const proxyBase = '/proxy/';

    // === Computed URLs ===
    const appIndexUrl = basePath + (basePath.endsWith('/') ? '' : '/') + 'code/index.html';
    const codeBaseAbsoluteUrl = window.location.origin + basePath + (basePath.endsWith('/') ? '' : '/') + 'code/';

    // === App state ===
    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const MODEL_STORAGE_PREFIX = 'webappFactory';
    let availableModels = {};
    let knownTaskSessionIds = new Set();
    let lastUsageData = null;

    // === Render mode state ===
    const DEFAULT_RENDER_OP = 'ops/render_op.md';
    const VALID_RENDER_OPS = ['ops/render_op.md', 'ops/render_simple_op.md'];

    // === Session link manager ===
    const linkManager = createSessionLinkManager(getProxyUrl);

    // === Batch logger ===
    const batchLogger = createBatchLogger('batch-log');
    const batchLog = document.getElementById('batch-log');
    function logBatch(message, type) {
        if (batchLog) batchLog.classList.add('visible');
        batchLogger.log(message, type);
    }
    function logBatchHtml(html, type) {
        if (batchLog) batchLog.classList.add('visible');
        batchLogger.logHtml(html, type);
    }

    // === Status tracking for code/ target ===
    let activeCodeBadge = 'badge-render';
    let activeCodeTaskSessionId = null;
    let codeOperationInFlight = false;
    let activeTestTaskSessionId = null;
    let statusPoller = null;

    // ==================================================
    // === Render Mode helpers ===
    // ==================================================
    function getSelectedRenderOp() {
        const checked = document.querySelector('input[name="render-mode"]:checked');
        const val = checked ? checked.value : '';
        if (VALID_RENDER_OPS.indexOf(val) >= 0) return val;
        return DEFAULT_RENDER_OP;
    }
    function loadRenderModePreference() {
        const saved = localStorage.getItem('webappFactory_renderOp');
        const opToUse = (saved && VALID_RENDER_OPS.indexOf(saved) >= 0) ? saved : DEFAULT_RENDER_OP;
        const radio = document.querySelector('input[name="render-mode"][value="' + opToUse + '"]');
        if (radio) radio.checked = true;
        updateRenderModeUI(opToUse);
    }
    function updateRenderModeUI(opPath) {
        const runBtn = document.querySelector('.btn-run[data-badge="badge-render"]');
        if (runBtn) runBtn.dataset.op = opPath;
    }
    function setupRenderModeListeners() {
        document.querySelectorAll('input[name="render-mode"]').forEach(radio => {
            radio.addEventListener('change', function() {
                if (this.checked) {
                    const op = this.value;
                    localStorage.setItem('webappFactory_renderOp', op);
                    updateRenderModeUI(op);
                    const label = op === 'ops/render_simple_op.md' ? 'Simple Mode' : 'Full Pipeline (SubPlan)';
                    setStatus('render-mode-status', '✓ Render mode: ' + label, 'success');
                }
            });
        });
    }

    // ==================================================
    // === Model management ===
    // ==================================================
    async function initApiProviders() {
        try {
            availableModels = await loadApiProviders();
            refreshModelDropdowns();
        } catch (e) {
            console.warn('Failed to load API providers:', e);
            setModelDropdownsError('Failed to load models');
        }
    }
    function refreshModelDropdowns() {
        const smartSelect = document.getElementById('model-smart');
        const fastSelect = document.getElementById('model-fast');
        const imageSelect = document.getElementById('model-image');
        const selects = [smartSelect, fastSelect, imageSelect].filter(Boolean);
        if (selects.length === 0) return;
        const saved = loadModelSelections(MODEL_STORAGE_PREFIX, MODEL_KEYS);
        // Convert saved object keyed by role -> object keyed by select id so each
        // dropdown picks up its own saved value.
        const savedByElement = {
            smartModel: saved.smartModel,
            fastModel: saved.fastModel,
            imageModel: saved.imageModel
        };
        try {
            populateModelDropdowns(availableModels, selects, savedByElement);
        } catch (e) {
            console.warn('populateModelDropdowns failed, falling back to manual fill:', e);
            manualPopulateModelDropdowns(smartSelect, fastSelect, imageSelect, saved);
        }
        // After population, restore selections manually (defensive — populateModelDropdowns
        // may key by element ID rather than role).
        if (smartSelect && saved.smartModel &&
            Array.from(smartSelect.options).some(o => o.value === saved.smartModel)) {
            smartSelect.value = saved.smartModel;
        }
        if (fastSelect && saved.fastModel &&
            Array.from(fastSelect.options).some(o => o.value === saved.fastModel)) {
            fastSelect.value = saved.fastModel;
        }
        if (imageSelect && saved.imageModel &&
            Array.from(imageSelect.options).some(o => o.value === saved.imageModel)) {
            imageSelect.value = saved.imageModel;
        }
    }
    function manualPopulateModelDropdowns(smartSelect, fastSelect, imageSelect, saved) {
        [smartSelect, fastSelect, imageSelect].filter(Boolean).forEach(sel => { sel.innerHTML = ''; });
        const addedModels = new Set();
        let totalModels = 0;
        for (const [provider, models] of Object.entries(availableModels)) {
            models.forEach(model => {
                if (addedModels.has(model.id)) return;
                [smartSelect, fastSelect, imageSelect].filter(Boolean).forEach(sel => {
                    const option = document.createElement('option');
                    option.value = model.id;
                    option.textContent = `${model.name} (${provider})`;
                    sel.appendChild(option);
                });
                addedModels.add(model.id);
                totalModels++;
            });
        }
        if (totalModels === 0) {
            [smartSelect, fastSelect, imageSelect].filter(Boolean).forEach(sel => {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = 'No models available — configure API keys first';
                sel.appendChild(option);
            });
        }
    }
    function setModelDropdownsError(message) {
        ['model-smart', 'model-fast', 'model-image'].forEach(id => {
            const sel = document.getElementById(id);
            if (!sel) return;
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
        const models = {};
        // Only include non-empty values — empty-string optional model keys
        // are rejected by the server.
        if (smartSelect && smartSelect.value) models.smartModel = smartSelect.value;
        if (fastSelect && fastSelect.value) models.fastModel = fastSelect.value;
        if (imageSelect && imageSelect.value) models.imageModel = imageSelect.value;
        return models;
    }

    // ==================================================
    // === Launch link wiring ===
    // ==================================================
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

    // ==================================================
    // === Console Capture & Live Preview ===
    // ==================================================
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
    <\/script>`;

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
        const entry = { level, message, source: source || '', timestamp: ts, raw: message };
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

    window.addEventListener('message', function(event) {
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
            html = html.replace(/<base\s[^>]*>/gi, '');
            const baseTag = '<base href="' + codeBaseAbsoluteUrl + '">';
            const injectedHead = baseTag + '\n' + CONSOLE_CAPTURE_SCRIPT;
            if (html.includes('<head>')) {
                html = html.replace('<head>', '<head>' + injectedHead);
            } else if (html.includes('<HEAD>')) {
                html = html.replace('<HEAD>', '<HEAD>' + injectedHead);
            } else if (html.includes('<html>')) {
                html = html.replace('<html>', '<html><head>' + injectedHead + '</head>');
            } else if (html.includes('<HTML>')) {
                html = html.replace('<HTML>', '<HTML><head>' + injectedHead + '</head>');
            } else {
                html = '<head>' + injectedHead + '</head>' + html;
            }
            if (previewPlaceholder) previewPlaceholder.style.display = 'none';
            previewIframe.style.display = 'block';
            previewIframe.style.minHeight = '600px';
            if ('srcdoc' in HTMLIFrameElement.prototype) {
                if (previewIframe._blobUrl) {
                    URL.revokeObjectURL(previewIframe._blobUrl);
                    previewIframe._blobUrl = null;
                }
                previewIframe.removeAttribute('src');
                previewIframe.srcdoc = html;
            } else {
                const blob = new Blob([html], { type: 'text/html; charset=utf-8' });
                const blobUrl = URL.createObjectURL(blob);
                if (previewIframe._blobUrl) URL.revokeObjectURL(previewIframe._blobUrl);
                previewIframe._blobUrl = blobUrl;
                previewIframe.removeAttribute('srcdoc');
                previewIframe.src = blobUrl;
            }
        } catch (e) {
            console.warn('Failed to load preview:', e);
            addConsoleEntry('error', 'Failed to load preview: ' + e.message);
            if (previewPlaceholder) previewPlaceholder.style.display = 'none';
            previewIframe.style.display = 'block';
            previewIframe.style.minHeight = '600px';
            previewIframe.src = appIndexUrl;
        }
    }

    // === Preview controls ===
    document.getElementById('btn-preview-refresh')?.addEventListener('click', loadPreviewIframe);
    document.getElementById('btn-preview-launch')?.addEventListener('click', function() {
        this.href = appIndexUrl;
    });
    document.getElementById('btn-preview-clear-console')?.addEventListener('click', clearConsolePanel);

    // === Fix Errors ===
    document.getElementById('btn-fix-errors')?.addEventListener('click', function() {
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
            if (err.source) notes += `Source: \`${err.source}\`\n`;
            notes += '\n';
        });
        const warnings = capturedLogs.filter(e => e.level === 'warn');
        if (warnings.length > 0) {
            notes += '## Warnings (lower priority)\n\n';
            warnings.forEach(w => { notes += `- ${w.message}\n`; });
            notes += '\n';
        }
        notes += '## Instructions\n';
        notes += '- Fix all the errors listed above\n';
        notes += '- Make sure the app loads without any JavaScript exceptions\n';
        notes += '- Test that all interactive features work correctly\n';
        const notesEditor = document.getElementById('notes-editor');
        if (notesEditor) notesEditor.value = notes;
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        const updateLink = document.querySelector('[data-section="section-update"]');
        if (updateLink) updateLink.classList.add('active');
        document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
        document.getElementById('section-update')?.classList.add('active');
    });

    // ==================================================
    // === App availability / preview banner ===
    // ==================================================
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

    // ==================================================
    // === Target normalization ===
    // ==================================================
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

    // ==================================================
    // === Task session tracking ===
    // ==================================================
    function trackTaskSession(taskSessionId) {
        if (taskSessionId && /^[a-zA-Z0-9-]+$/.test(taskSessionId)) {
            knownTaskSessionIds.add(taskSessionId);
        }
    }

    // ==================================================
    // === Status UI updates ===
    // ==================================================
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
            const completedAt = taskInfo.completedAt ? new Date(taskInfo.completedAt).toLocaleTimeString() : '';
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
            const completedAt = taskInfo.completedAt ? new Date(taskInfo.completedAt).toLocaleTimeString() : '';
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

    // ==================================================
    // === Pipeline diagram updates ===
    // ==================================================
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
            if (anyRunning) stageStatus = 'running';
            else if (anyTarget && allDone) stageStatus = 'done';
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

    // ==================================================
    // === Status polling (using utils/docops.js poller) ===
    // ==================================================
    function startStatusPolling() {
        if (statusPoller) return;
        statusPoller = createStatusPoller(basePath, async (target, taskInfo) => {
            // Track any task session IDs we encounter
            if (taskInfo.sessionId) trackTaskSession(taskInfo.sessionId);
            const effectiveTarget = (target === 'code' || target === 'code/') ? 'code/' : target;
            updateTaskStatusUI(effectiveTarget, taskInfo);
            // Update results section link if applicable
            if ((target === 'code/' || target === 'code') && taskInfo.sessionId &&
                (!activeCodeTaskSessionId || taskInfo.sessionId === activeCodeTaskSessionId)) {
                updateResultsSessionLink(taskInfo);
            }
        }, 3000);
        statusPoller.start();
        // Also do an immediate pull to refresh pipeline diagram which needs full task map
        refreshPipelineDiagram();
        // Refresh diagram on an interval as well
        if (!statusPoller._diagInterval) {
            statusPoller._diagInterval = setInterval(refreshPipelineDiagram, 3000);
        }
    }
    function stopStatusPolling() {
        if (statusPoller) {
            statusPoller.stop();
            if (statusPoller._diagInterval) {
                clearInterval(statusPoller._diagInterval);
                statusPoller._diagInterval = null;
            }
            statusPoller = null;
        }
    }
    async function refreshPipelineDiagram() {
        const statusData = await fetchDocopsStatus(basePath);
        if (statusData && statusData.tasks) {
            updatePipelineDiagram(statusData.tasks);
        }
    }

    // ==================================================
    // === Navigation ===
    // ==================================================
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function(e) {
            const sectionId = this.dataset.section;
            if (!sectionId) return;
            e.preventDefault();
            document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
            document.getElementById(sectionId).classList.add('active');
            if (sectionId === 'section-results') {
                loadPreviewIframe();
            }
        });
    });

    // === Results Tabs ===
    document.querySelectorAll('.results-tab').forEach(tab => {
        tab.addEventListener('click', function() {
            document.querySelectorAll('.results-tab').forEach(t => t.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
            document.getElementById(this.dataset.tab).classList.add('active');
        });
    });

    // ==================================================
    // === Initial file loading ===
    // ==================================================
    async function loadInitialFiles() {
        try {
            const content = await readFile(basePath, 'idea.md');
            if (content !== null) {
                const editor = document.getElementById('idea-editor');
                if (editor) editor.value = content;
            }
        } catch (e) { console.warn('Could not load idea.md:', e); }
        try {
            const notesContent = await readFile(basePath, 'notes.md');
            if (notesContent !== null) {
                const editor = document.getElementById('notes-editor');
                if (editor) editor.value = notesContent;
            }
        } catch (e) { console.warn('Could not load notes.md:', e); }
    }

    // ==================================================
    // === Save handlers ===
    // ==================================================
    document.getElementById('save-idea')?.addEventListener('click', async function() {
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

    document.getElementById('save-notes')?.addEventListener('click', async function() {
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

    // ==================================================
    // === Update operation ===
    // ==================================================
    document.getElementById('run-update')?.addEventListener('click', async function() {
        const notesContent = document.getElementById('notes-editor').value;
        if (!notesContent.trim()) {
            alert('Please write some update notes first describing what you\'d like to change.');
            return;
        }
        try { await writeFile(basePath, 'notes.md', notesContent); }
        catch (e) { console.warn('Could not auto-save notes.md:', e); }
        const ideaContent = document.getElementById('idea-editor').value;
        if (ideaContent.trim()) {
            try { await writeFile(basePath, 'idea.md', ideaContent); }
            catch (e) { console.warn('Could not auto-save idea.md:', e); }
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
            const taskId = await runDocOp(sessionId, 'ops/update_op.md', outputPath, getSelectedModels());
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                activeCodeTaskSessionId = cleanTaskId;
                updateSessionLinksUI(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                logBatchHtml(`Update session started: <a href="${getProxyUrl(cleanTaskId)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                trackTaskSession(cleanTaskId);
            }
            await waitForTask(basePath, outputPath, undefined, (target, task) => {
                updateTaskStatusUI(target, task);
            });
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

    // ==================================================
    // === Model settings ===
    // ==================================================
    document.getElementById('save-models')?.addEventListener('click', function() {
        const models = getSelectedModels();
        // Always store under all model keys (omitted ones become empty)
        const toSave = {
            smartModel: models.smartModel || '',
            fastModel: models.fastModel || '',
            imageModel: models.imageModel || ''
        };
        saveModelSelections(MODEL_STORAGE_PREFIX, toSave);
        setStatus('model-status', '✓ Model settings saved', 'success');
    });
    document.getElementById('refresh-models')?.addEventListener('click', async function() {
        this.disabled = true;
        setStatus('model-status', 'Loading models…', '');
        await initApiProviders();
        setStatus('model-status', '✓ Models refreshed', 'success');
        this.disabled = false;
    });

    // ==================================================
    // === View file buttons ===
    // ==================================================
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
        btn.addEventListener('click', function() {
            viewFile(this.dataset.file, this.dataset.viewer);
        });
    });
    document.querySelectorAll('.results-content .btn-secondary[data-file]').forEach(btn => {
        btn.addEventListener('click', async function() {
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

    // ==================================================
    // === Generic run buttons ===
    // ==================================================
    document.querySelectorAll('.btn-run').forEach(btn => {
        btn.addEventListener('click', async function() {
            const opPath = this.dataset.op;
            const badgeId = this.dataset.badge;
            const outputPath = this.dataset.output;
            const viewerId = this.dataset.viewer;
            if (outputPath === 'code/') {
                activeCodeBadge = badgeId;
                codeOperationInFlight = true;
                activeCodeTaskSessionId = null;
            }
            const ideaContent = document.getElementById('idea-editor')?.value || '';
            if (ideaContent.trim()) {
                try { await writeFile(basePath, 'idea.md', ideaContent); }
                catch (e) { console.warn('Could not auto-save idea.md:', e); }
            } else if (this.id !== 'run-test' && this.id !== 'run-review') {
                alert('Please describe your webapp idea first.');
                return;
            }
            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();
            try {
                const taskId = await runDocOp(sessionId, opPath, outputPath, getSelectedModels());
                const cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    if (outputPath === 'code/') activeCodeTaskSessionId = cleanTaskId;
                    updateSessionLinksUI(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                    logBatchHtml(`Session started: <a href="${getProxyUrl(cleanTaskId)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                    trackTaskSession(cleanTaskId);
                }
                await waitForTask(basePath, outputPath, undefined, (target, task) => {
                    updateTaskStatusUI(target, task);
                });
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

    // ==================================================
    // === Run All ===
    // ==================================================
    document.getElementById('run-all')?.addEventListener('click', async function() {
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
            const renderOp = getSelectedRenderOp();
            const modeLabel = renderOp === 'ops/render_simple_op.md' ? 'Simple Mode' : 'Full Pipeline (SubPlan)';
            logBatch('Render mode: ' + modeLabel + ' (' + renderOp + ')', 'info');
            const taskId = await runDocOp(sessionId, renderOp, 'code/', getSelectedModels());
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                activeCodeTaskSessionId = cleanTaskId;
                const proxyUrl = getProxyUrl(cleanTaskId);
                logBatchHtml(`Session started: <a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                updateSessionLinksUI('code/', { status: 'RUNNING', sessionId: cleanTaskId });
                trackTaskSession(cleanTaskId);
            }
            await waitForTask(basePath, 'code/', undefined, (target, task) => {
                updateTaskStatusUI(target, task);
            });
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
                    if (resultReadme) resultReadme.innerHTML = renderMarkdown(readmeContent);
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

    // ==================================================
    // === Project file listing (Results tab) ===
    // ==================================================
    document.getElementById('btn-refresh-files-results')?.addEventListener('click', async function() {
        const container = document.getElementById('files-results-container');
        if (!container) return;
        try {
            const allEntries = await listFiles(basePath, 'code');
            const projectFiles = allEntries.filter(e => e.type === 'file' || !e.type);
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
                header.addEventListener('click', async function() {
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

    // ==================================================
    // === Existing file/state check on load ===
    // ==================================================
    async function checkExistingFiles() {
        const statusData = await fetchDocopsStatus(basePath);
        let anyRunning = false;
        if (statusData && statusData.tasks) {
            for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
                if (taskInfo.sessionId) trackTaskSession(taskInfo.sessionId);
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
                    if (taskInfo.sessionId) updateResultsSessionLink(taskInfo);
                } else {
                    updateTaskStatusUI(target, taskInfo);
                    if (taskInfo.status === 'RUNNING') anyRunning = true;
                }
            }
            updatePipelineDiagram(statusData.tasks);
        }
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
        if (anyRunning) startStatusPolling();
    }

    // ==================================================
    // === ZIP Download Helpers ===
    // ==================================================
    function downloadZip(path) {
        if (!sessionId) {
            alert('No session ID available. Cannot download ZIP.');
            return;
        }
        const encodedPath = encodeURIComponent(path || '/');
        window.location.href = `${appBase}/fileZip?session=${encodeURIComponent(sessionId)}&path=${encodedPath}`;
    }
    document.getElementById('btn-zip-project-pipeline')?.addEventListener('click', () => downloadZip('/code'));
    document.getElementById('btn-zip-from-results')?.addEventListener('click', () => downloadZip('/code'));
    document.getElementById('btn-zip-whole-project')?.addEventListener('click', () => downloadZip('/'));
    document.getElementById('btn-zip-code-only')?.addEventListener('click', () => downloadZip('/code'));
    document.getElementById('btn-zip-custom')?.addEventListener('click', function() {
        const customPath = document.getElementById('zip-custom-path')?.value?.trim() || '/';
        downloadZip(customPath);
    });

    // ==================================================
    // === Git Operations (using utils/git.js) ===
    // ==================================================
    async function refreshGitStatus() {
        const display = document.getElementById('git-status-display');
        if (!display) return null;
        display.style.display = 'block';
        display.innerHTML = '<p class="placeholder">Loading status…</p>';
        try {
            const data = await gitGetStatus(basePath);
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
    document.getElementById('btn-git-init')?.addEventListener('click', async function() {
        this.disabled = true;
        setStatus('git-status-msg', 'Initializing repository…', '');
        try {
            const data = await gitInit(basePath);
            setStatus('git-status-msg', '✓ ' + (data.message || 'Repository initialized'), 'success');
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-status-msg', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    document.getElementById('btn-git-commit')?.addEventListener('click', async function() {
        const messageInput = document.getElementById('git-commit-message');
        const message = messageInput?.value?.trim() || '';
        this.disabled = true;
        setStatus('git-commit-status', 'Committing…', '');
        try {
            const data = await gitCommit(basePath, message);
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
    async function refreshGitBranches() {
        const display = document.getElementById('git-branches-display');
        if (!display) return null;
        display.style.display = 'block';
        display.innerHTML = '<p class="placeholder">Loading branches…</p>';
        try {
            const data = await gitGetBranches(basePath);
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
                item.addEventListener('click', async function() {
                    const branchName = this.dataset.branch;
                    if (!branchName) return;
                    if (!confirm(`Switch to branch "${branchName}"?`)) return;
                    setStatus('git-branch-status', 'Switching…', '');
                    try {
                        await gitCheckout(basePath, branchName, false);
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
    document.getElementById('btn-git-checkout')?.addEventListener('click', async function() {
        const branchName = document.getElementById('git-branch-name')?.value?.trim();
        if (!branchName) {
            alert('Please enter a branch name.');
            return;
        }
        this.disabled = true;
        setStatus('git-branch-status', 'Switching…', '');
        try {
            const data = await gitCheckout(basePath, branchName, false);
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
    document.getElementById('btn-git-create-branch')?.addEventListener('click', async function() {
        const branchName = document.getElementById('git-branch-name')?.value?.trim();
        if (!branchName) {
            alert('Please enter a name for the new branch.');
            return;
        }
        this.disabled = true;
        setStatus('git-branch-status', 'Creating branch…', '');
        try {
            const data = await gitCheckout(basePath, branchName, true);
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
    async function refreshGitLog() {
        const display = document.getElementById('git-log-display');
        if (!display) return;
        display.style.display = 'block';
        display.innerHTML = '<p class="placeholder">Loading commit history…</p>';
        const maxCount = parseInt(document.getElementById('git-log-count')?.value, 10) || 20;
        try {
            const data = await gitGetLog(basePath, maxCount);
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

    // === Git Quick Actions ===
    document.getElementById('quick-snapshot')?.addEventListener('click', async function() {
        setStatus('git-quick-status', 'Taking snapshot…', '');
        try {
            const status = await gitGetStatus(basePath);
            if (!status.initialized) await gitInit(basePath);
            const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19);
            const data = await gitCommit(basePath, 'Snapshot ' + timestamp);
            const commitHash = data.commitHash ? ` (${data.commitHash.substring(0, 8)})` : '';
            setStatus('git-quick-status', '✓ Snapshot taken' + commitHash, 'success');
            await refreshGitStatus();
            await refreshGitLog();
        } catch (e) {
            setStatus('git-quick-status', '✗ ' + e.message, 'error');
        }
    });
    document.getElementById('quick-branch-experiment')?.addEventListener('click', async function() {
        setStatus('git-quick-status', 'Setting up experiment…', '');
        try {
            const status = await gitGetStatus(basePath);
            if (!status.initialized) await gitInit(basePath);
            if (!status.clean) {
                await gitCommit(basePath, 'Save before experiment');
            }
            const expName = 'experiment-' + Date.now().toString(36);
            await gitCheckout(basePath, expName, true);
            setStatus('git-quick-status', '✓ Switched to branch: ' + expName, 'success');
            await refreshGitBranches();
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-quick-status', '✗ ' + e.message, 'error');
        }
    });
    document.getElementById('quick-backup-zip')?.addEventListener('click', async function() {
        setStatus('git-quick-status', 'Backing up…', '');
        try {
            const status = await gitGetStatus(basePath);
            if (!status.initialized) await gitInit(basePath);
            const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19);
            await gitCommit(basePath, 'Backup ' + timestamp);
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

    // === Auto-commit after build ===
    async function autoGitCommitAfterBuild(message) {
        try {
            const status = await gitGetStatus(basePath);
            if (!status.initialized) {
                await gitInit(basePath);
                logBatch('📌 Auto-initialized Git repository', 'info');
            }
            const currentStatus = status.initialized ? status : await gitGetStatus(basePath);
            if (currentStatus.initialized && !currentStatus.clean) {
                await gitCommit(basePath, message || 'Auto-commit after build');
                logBatch('📌 Auto-committed changes to Git: ' + message, 'success');
            }
        } catch (e) {
            console.warn('Auto-commit failed:', e);
        }
    }

    // ==================================================
    // === Usage Tracking (using utils/usage.js) ===
    // ==================================================
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
        // Discover any new task session IDs from status file
        const statusData = await fetchDocopsStatus(basePath);
        if (statusData && statusData.tasks) {
            for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
                if (taskInfo.sessionId) trackTaskSession(taskInfo.sessionId);
            }
        }
        if (knownTaskSessionIds.size === 0) {
            setStatus('usage-status', 'No task sessions found yet.', '');
            return;
        }
        const sessionIds = Array.from(knownTaskSessionIds);
        let aggregated;
        try {
            const result = await aggregateUsage(sessionIds);
            aggregated = {
                models: result.models || [],
                totals: result.totals || { prompt_tokens: 0, completion_tokens: 0, cost: 0 }
            };
            // Build per-task list from sessionUsageMap if provided
            const taskUsageList = [];
            if (result.sessionUsageMap) {
                for (const [sid, usage] of Object.entries(result.sessionUsageMap)) {
                    if (usage) taskUsageList.push({ sessionId: sid, usage });
                }
            }
            renderTaskUsageList(taskUsageList);
        } catch (e) {
            console.warn('aggregateUsage failed, falling back to per-session fetches:', e);
            // Fallback: manual aggregation
            const allModels = {};
            const taskUsageList = [];
            let totalPrompt = 0, totalCompletion = 0, totalCost = 0;
            for (const taskId of sessionIds) {
                const usage = await fetchUsageData(taskId);
                if (usage && usage.models) {
                    taskUsageList.push({ sessionId: taskId, usage });
                    usage.models.forEach(m => {
                        if (!allModels[m.model]) {
                            allModels[m.model] = { model: m.model, prompt_tokens: 0, completion_tokens: 0, cost: 0 };
                        }
                        allModels[m.model].prompt_tokens += (m.prompt_tokens || 0);
                        allModels[m.model].completion_tokens += (m.completion_tokens || 0);
                        allModels[m.model].cost += (m.cost || 0);
                    });
                }
                if (usage && usage.totals) {
                    totalPrompt += (usage.totals.prompt_tokens || 0);
                    totalCompletion += (usage.totals.completion_tokens || 0);
                    totalCost += (usage.totals.cost || 0);
                }
            }
            aggregated = {
                models: Object.values(allModels),
                totals: {
                    prompt_tokens: totalPrompt,
                    completion_tokens: totalCompletion,
                    cost: totalCost
                }
            };
            renderTaskUsageList(taskUsageList);
        }
        lastUsageData = aggregated;
        renderUsageTable(aggregated, 'usage-table-container');
        updateUsageSummaryBanner(aggregated);
        renderUsageTable(aggregated, 'usage-tab-container');
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
    document.getElementById('btn-refresh-usage')?.addEventListener('click', refreshAllUsage);
    document.getElementById('btn-refresh-task-usage')?.addEventListener('click', refreshAllUsage);
    document.getElementById('btn-refresh-usage-tab')?.addEventListener('click', refreshAllUsage);
    document.getElementById('btn-usage-json')?.addEventListener('click', function() {
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

    // ==================================================
    // === Test (Selenium) artifacts ===
    // ==================================================
    async function listTestArtifacts() {
        try {
            const entries = await listFiles(basePath, 'code');
            return entries.filter(e =>
                (e.type === 'file' || !e.type) && /^test\./i.test(e.name)
            );
        } catch (e) {
            return [];
        }
    }
    async function loadTestArtifacts() {
        const screenshotContainer = document.getElementById('test-screenshot-container');
        const consoleOutputEl = document.getElementById('test-console-output');
        const networkContainer = document.getElementById('test-network-container');
        const htmlContainer = document.getElementById('test-html-container');
        const entries = await listTestArtifacts();
        if (entries.length === 0) return;
        const screenshot = entries.find(e => /^test\.png$/i.test(e.name));
        const consoleLog = entries.find(e => /^test\.console\.log$/i.test(e.name));
        const networkLog = entries.find(e => /^test\.network\.log$/i.test(e.name));
        const htmlFile = entries.find(e => /^test\.html?$/i.test(e.name));
        if (screenshot && screenshotContainer) {
            const imgUrl = basePath + '/code/' + screenshot.name + '?t=' + Date.now();
            screenshotContainer.innerHTML = `
                    <a href="${escapeHtml(imgUrl)}" target="_blank" rel="noopener">
                        <img src="${escapeHtml(imgUrl)}" alt="Page screenshot"
                             style="max-width:100%; border:1px solid var(--color-border); border-radius:var(--radius); display:block;">
                    </a>
                    <p class="help-text" style="margin-top:0.5rem;">
                        📁 <code>${escapeHtml(screenshot.name)}</code>
                        — <a href="${escapeHtml(imgUrl)}" target="_blank" class="monitor-link">open full size</a>
                    </p>`;
        } else if (screenshotContainer) {
            screenshotContainer.innerHTML = '<p class="placeholder">No screenshot found.</p>';
        }
        if (consoleLog && consoleOutputEl) {
            try {
                const content = await readFile(basePath, 'code/' + consoleLog.name);
                if (content && content.trim().length > 0) {
                    consoleOutputEl.innerHTML = '';
                    content.split('\n').forEach(line => {
                        if (!line.trim()) return;
                        let cssClass = 'console-log';
                        const lower = line.toLowerCase();
                        if (/\b(error|exception|severe)\b/.test(lower) || /^\[error/.test(lower)) {
                            cssClass = 'console-error';
                        } else if (/\b(warn|warning)\b/.test(lower) || /^\[warn/.test(lower)) {
                            cssClass = 'console-warn';
                        } else if (/\b(info|debug)\b/.test(lower) || /^\[info/.test(lower)) {
                            cssClass = 'console-info';
                        }
                        const div = document.createElement('div');
                        div.className = 'console-entry ' + cssClass;
                        div.textContent = line;
                        consoleOutputEl.appendChild(div);
                    });
                } else {
                    consoleOutputEl.innerHTML = '<div class="console-entry console-info">Console log was empty.</div>';
                }
            } catch (e) {
                consoleOutputEl.innerHTML = '<div class="console-entry console-error">Failed to load console log: ' + escapeHtml(e.message) + '</div>';
            }
        } else if (consoleOutputEl) {
            consoleOutputEl.innerHTML = '<div class="console-entry console-info">No console log captured.</div>';
        }
        if (networkLog && networkContainer) {
            try {
                const content = await readFile(basePath, 'code/' + networkLog.name);
                if (content && content.trim().length > 0) {
                    networkContainer.innerHTML = '<pre style="white-space:pre-wrap; font-family:\'JetBrains Mono\', \'Fira Code\', monospace; font-size:0.82rem; max-height:400px; overflow:auto;">'
                        + escapeHtml(content) + '</pre>';
                } else {
                    networkContainer.innerHTML = '<pre class="placeholder">Network log was empty.</pre>';
                }
            } catch (e) {
                networkContainer.innerHTML = '<pre class="placeholder" style="color:var(--color-danger);">Failed to load network log: ' + escapeHtml(e.message) + '</pre>';
            }
        } else if (networkContainer) {
            networkContainer.innerHTML = '<pre class="placeholder">No network log captured.</pre>';
        }
        if (htmlFile && htmlContainer) {
            try {
                const content = await readFile(basePath, 'code/' + htmlFile.name);
                if (content && content.trim().length > 0) {
                    htmlContainer.innerHTML = '<pre style="white-space:pre-wrap; font-family:\'JetBrains Mono\', \'Fira Code\', monospace; font-size:0.82rem; max-height:500px; overflow:auto;"><code>'
                        + escapeHtml(content) + '</code></pre>';
                } else {
                    htmlContainer.innerHTML = '<pre class="placeholder">Rendered HTML was empty.</pre>';
                }
            } catch (e) {
                htmlContainer.innerHTML = '<pre class="placeholder" style="color:var(--color-danger);">Failed to load HTML: ' + escapeHtml(e.message) + '</pre>';
            }
        } else if (htmlContainer) {
            htmlContainer.innerHTML = '<pre class="placeholder">No rendered HTML captured.</pre>';
        }
    }

    document.getElementById('run-test')?.addEventListener('click', async function() {
        const indexResp = await fetch(appIndexUrl, { method: 'HEAD' }).catch(() => null);
        if (!indexResp || !indexResp.ok) {
            alert('No generated webapp found. Build the project first using the Pipeline tab.');
            return;
        }
        const checkAndLoad = async () => {
            const maxWait = 600000;
            const start = Date.now();
            while (Date.now() - start < maxWait) {
                const statusData = await fetchDocopsStatus(basePath);
                if (statusData && statusData.tasks) {
                    const found = findTaskByTarget(statusData.tasks, 'code/');
                    if (found) {
                        const task = found.task;
                        if (task.sessionId) {
                            activeTestTaskSessionId = task.sessionId;
                            trackTaskSession(task.sessionId);
                            updateSessionLinksUI('code/test', task);
                        }
                        if (task.status === 'COMPLETED') {
                            await loadTestArtifacts();
                            setStatus('test-status', '✓ Test complete — artifacts loaded', 'success');
                            return;
                        } else if (task.status === 'ERROR' || task.status === 'FAILED') {
                            setStatus('test-status', '✗ Test failed', 'error');
                            return;
                        }
                    }
                }
                await new Promise(r => setTimeout(r, 2000));
            }
        };
        setStatus('test-status', 'Test running…', '');
        setTimeout(checkAndLoad, 500);
    });
    document.getElementById('btn-refresh-test-artifacts')?.addEventListener('click', async function() {
        this.disabled = true;
        setStatus('test-status', 'Loading artifacts…', '');
        try {
            await loadTestArtifacts();
            setStatus('test-status', '✓ Artifacts refreshed', 'success');
        } catch (e) {
            setStatus('test-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    document.querySelectorAll('.nav-link[data-section="section-test"]').forEach(link => {
        link.addEventListener('click', () => setTimeout(loadTestArtifacts, 100));
    });

    // === Review Test Results ===
    document.getElementById('run-review')?.addEventListener('click', async function() {
        const artifacts = await listTestArtifacts();
        if (artifacts.length === 0) {
            alert('No test artifacts found. Please run the test first.');
            return;
        }
        const badgeId = 'badge-review';
        setBadge(badgeId, 'running');
        this.disabled = true;
        setStatus('review-status', 'Reviewing test results…', '');
        startStatusPolling();
        try {
            const taskId = await runDocOp(sessionId, 'ops/review_op.md', 'notes.md', getSelectedModels());
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                trackTaskSession(cleanTaskId);
                const linkContainer = document.getElementById('review-session-link');
                if (linkContainer) {
                    linkContainer.innerHTML = `<div class="session-monitor-link">
    <span class="monitor-pulse">●</span>
    <span>Reviewing… </span>
    <a href="${escapeHtml(getProxyUrl(cleanTaskId))}" target="_blank" rel="noopener" class="monitor-link">
    📡 Monitor Live Session (${escapeHtml(cleanTaskId)})
    </a>
    </div>`;
                    linkContainer.style.display = 'block';
                }
            }
            await waitForTask(basePath, 'notes.md', undefined, (target, task) => {
                updateTaskStatusUI(target, task);
            });
            setBadge(badgeId, 'done');
            setStatus('review-status', '✓ Review complete — findings written to notes.md', 'success');
            if (cleanTaskId) {
                const linkContainer = document.getElementById('review-session-link');
                if (linkContainer) {
                    const completedAt = new Date().toLocaleTimeString();
                    linkContainer.innerHTML = `<div class="session-completed-link">
    <span>✅ Completed at ${escapeHtml(completedAt)} — </span>
    <a href="${escapeHtml(getProxyUrl(cleanTaskId))}" target="_blank" rel="noopener" class="monitor-link">
    📋 View Session Log (${escapeHtml(cleanTaskId)})
    </a>
    </div>`;
                }
            }
            try {
                const notesContent = await readFile(basePath, 'notes.md');
                if (notesContent !== null) {
                    const viewer = document.getElementById('viewer-review');
                    if (viewer) {
                        viewer.innerHTML = renderMarkdown(notesContent);
                        viewer.classList.add('visible');
                    }
                    const notesEditor = document.getElementById('notes-editor');
                    if (notesEditor) notesEditor.value = notesContent;
                }
            } catch (e) {
                console.warn('Could not load notes.md:', e);
            }
            await refreshAllUsage();
        } catch (e) {
            setBadge(badgeId, 'error');
            setStatus('review-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // ==================================================
    // === Initialize ===
    // ==================================================
    updateLaunchLinks();
    loadInitialFiles();
    checkExistingFiles();
    showAppPreview();
    startStatusPolling();
    initApiProviders();
    setupRenderModeListeners();
    loadRenderModePreference();
    setTimeout(refreshAllUsage, 2000);

    // Surface unhandled errors in batch log for debugging
    window.addEventListener('error', function(e) {
        console.error('Unhandled error:', e.error || e.message);
    });
    window.addEventListener('unhandledrejection', function(e) {
        console.error('Unhandled promise rejection:', e.reason);
    });
})();