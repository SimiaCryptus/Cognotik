(function() {
    'use strict';
    // === Utility: Determine base path from current URL ===
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';
    let appId = '';
    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
        appId = pathParts[fileIndexIdx - 1] || 'webapp-factory';
    } else {
        // Fallback: try to detect basePath from the current directory
        // Remove trailing filename (e.g. /app.html) to get the directory
        const currentPath = window.location.pathname;
        basePath = currentPath.replace(/\/[^/]*\.[^/]*$/, '') || currentPath.replace(/\/+$/, '');
        console.warn('Could not determine session from URL path. Using basePath:', basePath);
    }
    const proxyBase = '/proxy/';
     // === Model management state ===
     let availableModels = {};
    // === Compute app base path (e.g. /webapp-factory) for ZIP/Git endpoints ===
    const appBase = fileIndexIdx >= 2 ? pathParts.slice(0, fileIndexIdx).join('/') : '';
    // === Usage tracking state ===
    let knownTaskSessionIds = new Set();
    let lastUsageData = null;

    // === Compute the URL for the generated app's index.html ===
    const appIndexUrl = basePath + (basePath.endsWith('/') ? '' : '/') + 'code/index.html';
    // Compute the absolute base URL for the code directory (used in iframe <base> tag)
    const codeBaseAbsoluteUrl = window.location.origin + basePath + (basePath.endsWith('/') ? '' : '/') + 'code/';
     // === Model loading and selection ===
     async function loadApiProviders() {
         try {
             const response = await fetch('/apiProviders/?format=json');
             if (response.status >= 400) {
                 console.warn('Could not load API providers (status ' + response.status + ')');
                 return;
             }
             const providersResponse = await response.json();
             const providers = providersResponse.configuredProviders || [];
             availableModels = {};
             providers.forEach(provider => {
                 if (provider.models && provider.models.length > 0) {
                     availableModels[provider.name] = provider.models.map(model => ({
                         id: model.name,
                         name: model.name,
                         description: model.maxTokens
                             ? `Max tokens: ${model.maxTokens}`
                             : ''
                     }));
                 }
             });
             populateModelDropdowns();
         } catch (e) {
             console.warn('Failed to load API providers:', e);
             setModelDropdownsError('Failed to load models');
         }
     }
     function populateModelDropdowns() {
         const smartSelect = document.getElementById('model-smart');
         const fastSelect = document.getElementById('model-fast');
         const imageSelect = document.getElementById('model-image');
         if (!smartSelect || !fastSelect) return;
         [smartSelect, fastSelect].forEach(sel => { sel.innerHTML = ''; });
         if (imageSelect) imageSelect.innerHTML = '';
         const addedModels = new Set();
         let totalModels = 0;
         for (const [provider, models] of Object.entries(availableModels)) {
             models.forEach(model => {
                 if (!addedModels.has(model.id)) {
                     [smartSelect, fastSelect].forEach(sel => {
                         const option = document.createElement('option');
                         option.value = model.id;
                         option.textContent = `${model.name} (${provider})`;
                         sel.appendChild(option);
                     });
                     if (imageSelect) {
                         const option = document.createElement('option');
                         option.value = model.id;
                         option.textContent = `${model.name} (${provider})`;
                         imageSelect.appendChild(option);
                     }
                     addedModels.add(model.id);
                     totalModels++;
                 }
             });
         }
         if (totalModels === 0) {
             [smartSelect, fastSelect, imageSelect].filter(Boolean).forEach(sel => {
                 const option = document.createElement('option');
                 option.value = '';
                 option.textContent = 'No models available — configure API keys first';
                 sel.appendChild(option);
             });
             return;
         }
         // Restore saved selections
         const savedSmart = localStorage.getItem('webappFactory_smartModel');
         const savedFast = localStorage.getItem('webappFactory_fastModel');
         const savedImage = localStorage.getItem('webappFactory_imageModel');
         if (savedSmart && Array.from(smartSelect.options).some(o => o.value === savedSmart)) {
             smartSelect.value = savedSmart;
         }
         if (savedFast && Array.from(fastSelect.options).some(o => o.value === savedFast)) {
             fastSelect.value = savedFast;
         }
         if (savedImage && imageSelect && Array.from(imageSelect.options).some(o => o.value === savedImage)) {
             imageSelect.value = savedImage;
         }
     }
     function setModelDropdownsError(message) {
         const smartSelect = document.getElementById('model-smart');
         const fastSelect = document.getElementById('model-fast');
         const imageSelect = document.getElementById('model-image');
         [smartSelect, fastSelect, imageSelect].filter(Boolean).forEach(sel => {
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
         return {
             smartModel: smartSelect ? smartSelect.value : '',
             fastModel: fastSelect ? fastSelect.value : '',
             imageModel: imageSelect ? imageSelect.value : '',
         };
     }

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
    // The script we inject into the iframe to capture console output
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
        const entry = {
            level: level,
            message: message,
            source: source || '',
            timestamp: ts,
            raw: message
        };
        capturedLogs.push(entry);
        // Update counts
        if (level === 'error' || level === 'exception') {
            consoleStat.errors++;
            if (btnFixErrors) btnFixErrors.style.display = '';
        } else if (level === 'warn') {
            consoleStat.warnings++;
        } else {
            consoleStat.logs++;
        }
        updateConsoleCounts();
        // Add to DOM
        if (!consoleOutput) return;
        // Remove the "waiting" placeholder if present
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
    // Listen for messages from the iframe
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
    // Load the app into the iframe with injected console capture
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
            // Fetch the generated index.html
            const resp = await fetch(appIndexUrl);
            if (!resp.ok) throw new Error('Could not fetch app');
            let html = await resp.text();
            // Remove any existing <base> tags to avoid conflicts
            html = html.replace(/<base\s[^>]*>/gi, '');
            // Inject <base> tag with an absolute URL so that relative resource
            // references (CSS, JS, images) resolve correctly even inside a
            // blob: URL document where relative paths would otherwise break.
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
            // Try using srcdoc first — it works well when the <base> tag has
            // an absolute URL.  Fall back to blob URL if srcdoc is unavailable.
            if ('srcdoc' in HTMLIFrameElement.prototype) {
                // Revoke any previous blob URL
                if (previewIframe._blobUrl) {
                    URL.revokeObjectURL(previewIframe._blobUrl);
                    previewIframe._blobUrl = null;
                }
                previewIframe.removeAttribute('src');
                previewIframe.srcdoc = html;
            } else {
                // Fallback: blob URL approach
                const blob = new Blob([html], { type: 'text/html; charset=utf-8' });
                const blobUrl = URL.createObjectURL(blob);
                if (previewIframe._blobUrl) {
                    URL.revokeObjectURL(previewIframe._blobUrl);
                }
                previewIframe._blobUrl = blobUrl;
                previewIframe.removeAttribute('srcdoc');
                previewIframe.src = blobUrl;
            }
        } catch (e) {
            console.warn('Failed to load preview:', e);
            addConsoleEntry('error', 'Failed to load preview: ' + e.message);
            // Fallback: just set src directly (no console capture)
            if (previewPlaceholder) previewPlaceholder.style.display = 'none';
            previewIframe.style.display = 'block';
            previewIframe.style.minHeight = '600px';
            previewIframe.src = appIndexUrl;
        }
    }
    // Preview controls
    document.getElementById('btn-preview-refresh')?.addEventListener('click', function() {
        loadPreviewIframe();
    });
    document.getElementById('btn-preview-launch')?.addEventListener('click', function() {
        this.href = appIndexUrl;
    });
    document.getElementById('btn-preview-clear-console')?.addEventListener('click', function() {
        clearConsolePanel();
    });
    // "Fix Errors" button: collect errors and populate update notes
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
            if (err.source) {
                notes += `Source: \`${err.source}\`\n`;
            }
            notes += '\n';
        });
        // Also include warnings if any
        const warnings = capturedLogs.filter(e => e.level === 'warn');
        if (warnings.length > 0) {
            notes += '## Warnings (lower priority)\n\n';
            warnings.forEach((w, i) => {
                notes += `- ${w.message}\n`;
            });
            notes += '\n';
        }
        notes += '## Instructions\n';
        notes += '- Fix all the errors listed above\n';
        notes += '- Make sure the app loads without any JavaScript exceptions\n';
        notes += '- Test that all interactive features work correctly\n';
        // Navigate to Update tab and populate notes
        const notesEditor = document.getElementById('notes-editor');
        if (notesEditor) {
            notesEditor.value = notes;
        }
        // Switch to Update section
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        const updateLink = document.querySelector('[data-section="section-update"]');
        if (updateLink) updateLink.classList.add('active');
        document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
        document.getElementById('section-update')?.classList.add('active');
    });
    function getErrorsAndWarningsSummary() {
        const errors = capturedLogs.filter(e => e.level === 'error' || e.level === 'exception');
        const warnings = capturedLogs.filter(e => e.level === 'warn');
        return { errors, warnings, hasIssues: errors.length > 0 || warnings.length > 0 };
    }

    // === Show/hide the app preview banner and iframe ===
    async function checkAppAvailable() {
        try {
            const resp = await fetch(appIndexUrl, { method: 'HEAD' });
            return resp.ok;
        } catch (e) {
            return false;
        }
    }
    async function showAppPreview() {
        const available = await checkAppAvailable();
        const banner = document.getElementById('app-preview-banner');
        const navLaunch = document.getElementById('nav-launch-app');
        if (available) {
            if (banner) banner.style.display = 'flex';
            if (navLaunch) navLaunch.classList.add('visible');
            // Auto-load the iframe preview
            loadPreviewIframe();
        } else {
            if (banner) banner.style.display = 'none';
            if (navLaunch) navLaunch.classList.remove('visible');
        }
    }

    // === Status polling state ===
    let statusPollTimer = null;
    const STATUS_POLL_INTERVAL = 3000;
    // Track which badge to update for code/ target (render vs update)
    let activeCodeBadge = 'badge-render';
    // Track the task session ID we initiated, so we can distinguish our task from stale status
    let activeCodeTaskSessionId = null;
    // Track whether any operation is currently in flight for code/
    let codeOperationInFlight = false;
    // === File I/O helpers ===
    async function readFile(filePath) {
        const url = basePath + '/' + filePath;
        const resp = await fetch(url);
        if (!resp.ok) {
            if (resp.status === 404) return null;
            throw new Error(`Failed to read ${filePath}: ${resp.status} ${resp.statusText}`);
        }
        return await resp.text();
    }
    async function writeFile(filePath, content) {
        const url = basePath + '/' + filePath;
        const resp = await fetch(url, {
            method: 'PUT',
            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
            body: content
        });
        if (!resp.ok) {
            throw new Error(`Failed to write ${filePath}: ${resp.status} ${resp.statusText}`);
        }
        return true;
    }
    async function listFiles(dirPath) {
        const url = basePath + '/' + dirPath + '/_files.json';
        const resp = await fetch(url);
        if (!resp.ok) {
            if (resp.status === 404) return [];
            throw new Error(`Failed to list ${dirPath}: ${resp.status} ${resp.statusText}`);
        }
        const data = await resp.json();
        return (data.entries || []).filter(e => e.type === 'file');
    }
    async function listAllFiles() {
        // List files at root level
        const url = basePath + '/_files.json';
        const resp = await fetch(url);
        if (!resp.ok) {
            if (resp.status === 404) return [];
            throw new Error(`Failed to list root: ${resp.status} ${resp.statusText}`);
        }
        const data = await resp.json();
        return data.entries || [];
    }
    async function runDocOp(opPath, targetPath) {
         const params = new URLSearchParams({
             sessionId: sessionId,
             doc: opPath,
             target: targetPath
         });
         const models = getSelectedModels();
         if (models.smartModel) params.set('smartModel', models.smartModel);
         if (models.fastModel) params.set('fastModel', models.fastModel);
         if (models.imageModel) params.set('imageModel', models.imageModel);
         const url = `/docops?${params.toString()}`;
        const resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            const errText = await resp.text().catch(() => '');
            throw new Error(`DocOps failed for ${opPath}: ${resp.status} ${resp.statusText}\n${errText}`);
        }
        return await resp.text();
    }
     // === Normalize target path to match server status keys ===
     // The server may strip trailing slashes from target keys
     function normalizeTarget(target) {
         // Remove trailing slash for comparison, but keep at least the base name
         return target.replace(/\/+$/, '') || target;
     }
     function findTaskByTarget(tasks, target) {
         if (!tasks) return null;
         // Try exact match first
         if (tasks[target]) return { key: target, task: tasks[target] };
         // Try without trailing slash
         const normalized = normalizeTarget(target);
         if (tasks[normalized]) return { key: normalized, task: tasks[normalized] };
         // Try with trailing slash
         const withSlash = target.endsWith('/') ? target : target + '/';
         if (tasks[withSlash]) return { key: withSlash, task: tasks[withSlash] };
         return null;
     }

    // === Status polling ===
    async function fetchDocopsStatus() {
        try {
            const url = basePath + '/docops.status.json';
            const resp = await fetch(url);
            if (!resp.ok) {
                if (resp.status === 404) return null;
                return null;
            }
            return await resp.json();
        } catch (e) {
            console.warn('Could not fetch docops status:', e);
            return null;
        }
    }
    function getProxyUrl(taskSessionId) {
        return proxyBase + '#' + taskSessionId;
    }
    function updateTaskStatusUI(target, taskInfo) {
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;
        let badgeId = null;
        if (target === 'code/') {
            // If we have an active operation in flight, only update if the session matches
            // or if we don't have a known session yet
            if (codeOperationInFlight && activeCodeTaskSessionId && taskSessionId !== activeCodeTaskSessionId) {
                // This is a stale status from a previous operation, ignore it
                return;
            }
            badgeId = activeCodeBadge;
        }
        if (!badgeId) {
            // Unknown target, skip badge update
        }
        if (badgeId) {
            if (status === 'RUNNING') {
                setBadge(badgeId, 'running');
            } else if (status === 'COMPLETED') {
                setBadge(badgeId, 'done');
                if (target === 'code/') {
                    codeOperationInFlight = false;
                }
            } else if (status === 'ERROR' || status === 'FAILED') {
                setBadge(badgeId, 'error');
                if (target === 'code/') {
                    codeOperationInFlight = false;
                }
            }
        }
        updateSessionLinks(target, taskInfo);
    }
    function updateSessionLinks(target, taskInfo) {
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;
        
        const safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        const linkContainerId = 'session-link-' + safeTarget;
        
        let container = document.getElementById(linkContainerId);
        if (!container) {
            container = document.createElement('div');
            container.id = linkContainerId;
            container.className = 'session-link-container';
            
            // Try to find the run button for this target
            const runBtn = document.querySelector(`button[data-output="${CSS.escape(target)}"]`);
            if (runBtn) {
                const buttonRow = runBtn.closest('.button-row');
                if (buttonRow) {
                    buttonRow.parentNode.insertBefore(container, buttonRow.nextSibling);
                }
            } else {
                // Fallback: insert into the batch log area or the step area
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
            if (stepTitle) {
                actionText = stepTitle + '…';
            }
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
    async function pollStatus() {
        const statusData = await fetchDocopsStatus();
        if (!statusData || !statusData.tasks) return;
        let anyRunning = false;
        for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
            if (taskInfo.sessionId) {
                trackTaskSession(taskInfo.sessionId);
            }
             // Normalize: check if this target matches 'code/' or 'code'
             const effectiveTarget = (target === 'code' || target === 'code/') ? 'code/' : target;
             updateTaskStatusUI(effectiveTarget, taskInfo);
            if (taskInfo.status === 'RUNNING') {
                anyRunning = true;
            }
            // Keep results section session link updated
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
            'input': { targets: ['idea.md'], el: null },
            'render': { targets: ['code/'], el: null },
            'output': { targets: ['code/'], el: null },
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
                // Skip stale sessions
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
    // === Markdown rendering ===
    function renderMarkdown(md) {
        if (typeof marked !== 'undefined') {
            if (typeof marked.parse === 'function') return marked.parse(md);
            return marked(md);
        }
        return '<pre>' + escapeHtml(md) + '</pre>';
    }
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    // === Status helpers ===
    function setStatus(elemId, message, type) {
        const el = document.getElementById(elemId);
        if (!el) return;
        el.textContent = message;
        el.className = 'status-msg' + (type ? ' ' + type : '');
        if (type === 'success' || type === 'error') {
            setTimeout(() => {
                el.textContent = '';
                el.className = 'status-msg';
            }, 5000);
        }
    }
    function setBadge(badgeId, state) {
        const el = document.getElementById(badgeId);
        if (!el) return;
        el.className = 'step-badge ' + state;
        const labels = {
            'pending': 'pending',
            'running': 'running…',
            'done': 'done',
            'error': 'error',
        };
        el.textContent = labels[state] || state;
    }
    // === Batch log ===
    const batchLog = document.getElementById('batch-log');
    function logBatch(message, type, isHtml = false) {
        batchLog.classList.add('visible');
        const entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        const ts = new Date().toLocaleTimeString();
        if (isHtml) {
            entry.innerHTML = `[${ts}] ${message}`;
        } else {
            entry.textContent = `[${ts}] ${message}`;
        }
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }
    function logBatchHtml(html, type) {
        logBatch(html, type, true);
    }
    // === Navigation ===
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function(e) {
            const sectionId = this.dataset.section;
            if (!sectionId) return; // skip links without a section (e.g. launch app)
            e.preventDefault();
            document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
            document.getElementById(sectionId).classList.add('active');
            // Auto-load preview when switching to Results section
            if (sectionId === 'section-results') {
                // Always reload the preview when switching to Results
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
    // === Load initial files ===
    async function loadInitialFiles() {
        try {
            const content = await readFile('idea.md');
            if (content !== null) {
                document.getElementById('idea-editor').value = content;
            }
        } catch (e) {
            console.warn('Could not load idea.md:', e);
        }
        try {
            const notesContent = await readFile('notes.md');
            if (notesContent !== null) {
                document.getElementById('notes-editor').value = notesContent;
            }
        } catch (e) {
            console.warn('Could not load notes.md:', e);
        }
    }
    // === Save idea ===
    document.getElementById('save-idea').addEventListener('click', async function() {
        const content = document.getElementById('idea-editor').value;
        try {
            this.disabled = true;
            await writeFile('idea.md', content);
            setStatus('idea-status', '✓ Saved successfully', 'success');
        } catch (e) {
            setStatus('idea-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // === Save notes ===
    document.getElementById('save-notes')?.addEventListener('click', async function() {
        const content = document.getElementById('notes-editor').value;
        try {
            this.disabled = true;
            await writeFile('notes.md', content);
            setStatus('notes-status', '✓ Saved successfully', 'success');
        } catch (e) {
            setStatus('notes-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // === Run Update ===
    document.getElementById('run-update')?.addEventListener('click', async function() {
        const notesContent = document.getElementById('notes-editor').value;
        if (!notesContent.trim()) {
            alert('Please write some update notes first describing what you\'d like to change.');
            return;
        }
        // Auto-save notes before running
        try {
            await writeFile('notes.md', notesContent);
        } catch (e) {
            console.warn('Could not auto-save notes.md:', e);
        }
        // Also auto-save idea if present
        const ideaContent = document.getElementById('idea-editor').value;
        if (ideaContent.trim()) {
            try {
                await writeFile('idea.md', ideaContent);
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
            const taskId = await runDocOp('ops/update_op.md', outputPath);
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                activeCodeTaskSessionId = cleanTaskId;
                updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                logBatchHtml(`Update session started: <a href="${getProxyUrl(cleanTaskId)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                trackTaskSession(cleanTaskId);
            }
            await waitForTask(outputPath);
            setBadge(badgeId, 'done');
            // Show updated README
            const viewer = document.getElementById('viewer-update');
            if (viewer) {
                try {
                    const content = await readFile('code/README.md');
                    if (content) {
                        viewer.innerHTML = renderMarkdown(content);
                        viewer.classList.add('visible');
                    }
                } catch (e) { /* non-critical */ }
            }
            // Refresh app preview
            await showAppPreview();
            // Reload the iframe preview to check for remaining errors
            await loadPreviewIframe();
            // Auto-refresh the project files list
            document.getElementById('btn-refresh-files-results')?.click();
            // Auto-commit to Git if repo is initialized
            const updateTime = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }).toLowerCase();
            await autoGitCommitAfterBuild('update at ' + updateTime);
            // Refresh usage data
            await refreshAllUsage();
        } catch (e) {
            setBadge(badgeId, 'error');
            alert('Update failed: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });
     // === Save model settings ===
     document.getElementById('save-models')?.addEventListener('click', function() {
         const models = getSelectedModels();
         if (models.smartModel) localStorage.setItem('webappFactory_smartModel', models.smartModel);
         if (models.fastModel) localStorage.setItem('webappFactory_fastModel', models.fastModel);
         if (models.imageModel) localStorage.setItem('webappFactory_imageModel', models.imageModel);
         setStatus('model-status', '✓ Model settings saved', 'success');
     });
     // === Refresh models ===
     document.getElementById('refresh-models')?.addEventListener('click', async function() {
         this.disabled = true;
         setStatus('model-status', 'Loading models…', '');
         await loadApiProviders();
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
            const content = await readFile(filePath);
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
    // Refresh buttons in results section
    document.querySelectorAll('.results-content .btn-secondary[data-file]').forEach(btn => {
        btn.addEventListener('click', async function() {
            const filePath = this.dataset.file;
            const viewerId = this.dataset.viewer;
            const viewer = document.getElementById(viewerId);
            if (!viewer) return;
            try {
                const content = await readFile(filePath);
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
    // === Helper: wait for task completion by polling status ===
    async function waitForTask(targetPath, maxWaitMs) {
        const maxWait = maxWaitMs || 600000; // 10 min default
        const pollInterval = 2000;
        const startTime = Date.now();
        while (Date.now() - startTime < maxWait) {
            const statusData = await fetchDocopsStatus();
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
    // === Run operation buttons ===
    document.querySelectorAll('.btn-run').forEach(btn => {
        btn.addEventListener('click', async function() {
            const opPath = this.dataset.op;
            const badgeId = this.dataset.badge;
            const outputPath = this.dataset.output;
            const viewerId = this.dataset.viewer;
            // Track which badge is active for this target
            if (outputPath === 'code/') {
                activeCodeBadge = badgeId;
                codeOperationInFlight = true;
                activeCodeTaskSessionId = null;
            }
            // Auto-save idea before running
            const ideaContent = document.getElementById('idea-editor').value;
            if (ideaContent.trim()) {
                try {
                    await writeFile('idea.md', ideaContent);
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
                const taskId = await runDocOp(opPath, outputPath);
                const cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    if (outputPath === 'code/') activeCodeTaskSessionId = cleanTaskId;
                    updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                    logBatchHtml(`Session started: <a href="${getProxyUrl(cleanTaskId)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                    trackTaskSession(cleanTaskId);
                }
                await waitForTask(outputPath);
                setBadge(badgeId, 'done');
                if (viewerId) {
                    const viewer = document.getElementById(viewerId);
                    if (viewer) {
                        try {
                            const content = await readFile(outputPath + 'README.md');
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
    document.getElementById('run-all').addEventListener('click', async function() {
        this.disabled = true;
        startStatusPolling();
        activeCodeBadge = 'badge-render';
        codeOperationInFlight = true;
        activeCodeTaskSessionId = null;
        batchLog.innerHTML = '';
        // Auto-save idea
        const ideaContent = document.getElementById('idea-editor').value;
        if (ideaContent.trim()) {
            try {
                await writeFile('idea.md', ideaContent);
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
            // Note: "Render Project" -> "Build Project" in UI, but op path unchanged
            setBadge('badge-render', 'running');
            const taskId = await runDocOp('ops/render_op.md', 'code/');
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                activeCodeTaskSessionId = cleanTaskId;
                const proxyUrl = getProxyUrl(cleanTaskId);
                logBatchHtml(`Session started: <a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">📡 Monitor Live Session (${escapeHtml(cleanTaskId)})</a>`, 'info');
                updateSessionLinks('code/', { status: 'RUNNING', sessionId: cleanTaskId });
                trackTaskSession(cleanTaskId);
            }
            await waitForTask('code/');
            setBadge('badge-render', 'done');
            // Fetch final status to get session ID for completed link
            const finalStatus = await fetchDocopsStatus();
             const completedTaskFound = finalStatus?.tasks ? findTaskByTarget(finalStatus.tasks, 'code/') : null;
             const completedTask = completedTaskFound?.task;
            if (completedTask && completedTask.sessionId) {
                const proxyUrl = getProxyUrl(completedTask.sessionId);
                logBatchHtml(`✓ Completed: Build Project — <a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">📋 View Session Log (${escapeHtml(completedTask.sessionId)})</a>`, 'success');
            } else {
                logBatch('✓ Completed: Build Project', 'success');
            }
            // Show the README
            try {
                const content = await readFile('code/README.md');
                if (content) {
                    const viewer = document.getElementById('viewer-render');
                    if (viewer) {
                        viewer.innerHTML = renderMarkdown(content);
                        viewer.classList.add('visible');
                    }
                }
            } catch (e) { /* non-critical */ }
            logBatch('🎉 Pipeline complete! Check the Results tab for output.', 'success');
            // Show the app preview
            await showAppPreview();
            // Switch to preview tab in results if we're on results section
            logBatch('🖥️ Live preview loaded — check the Results tab to see your app and console output.', 'info');
            // Auto-refresh the project files list
            document.getElementById('btn-refresh-files-results')?.click();
            // Auto-commit to Git if repo is initialized
            const buildTime = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }).toLowerCase();
            await autoGitCommitAfterBuild('initial build at ' + buildTime);
            // Refresh usage data
            await refreshAllUsage();
            // Auto-load the README in results
            try {
                const readmeContent = await readFile('code/README.md');
                if (readmeContent) {
                    const resultReadme = document.getElementById('result-readme');
                    if (resultReadme) {
                        resultReadme.innerHTML = renderMarkdown(readmeContent);
                    }
                }
            } catch (e) { /* non-critical */ }
        } catch (e) {
            setBadge('badge-render', 'error');
            // Try to get session link for the failed task
            const failStatus = await fetchDocopsStatus().catch(() => null);
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
    document.getElementById('btn-refresh-files-results')?.addEventListener('click', async function() {
        const container = document.getElementById('files-results-container');
        try {
            const projectFiles = await listFiles('code');
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
                                    const content = await readFile('code/' + entry.name);
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
    function getFileIcon(filename) {
        const ext = filename.split('.').pop().toLowerCase();
        const icons = {
            'md': '📝',
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
            'kt': '☕',
            'rb': '💎',
            'go': '🔵',
            'rs': '🦀',
            'sh': '🖥️',
            'bash': '🖥️',
            'txt': '📄',
            'svg': '🖼️',
            'png': '🖼️',
            'jpg': '🖼️',
            'gif': '🖼️',
            'toml': '⚙️',
            'ini': '⚙️',
            'cfg': '⚙️',
            'dockerfile': '🐳',
        };
        return icons[ext] || '📄';
    }
    // === Check existing files on load ===
    async function checkExistingFiles() {
        const statusData = await fetchDocopsStatus();
        let anyRunning = false;
        if (statusData && statusData.tasks) {
            for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
                 if (target === 'code/' || target === 'code') {
                    // On page load, accept whatever status is there
                    // since we don't know if it was render or update
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
                     updateSessionLinks('code/', taskInfo);
                    if (taskInfo.sessionId) {
                        updateResultsSessionLink(taskInfo);
                    }
                } else {
                     const effectiveTarget = target;
                     updateTaskStatusUI(effectiveTarget, taskInfo);
                    if (taskInfo.status === 'RUNNING') {
                        anyRunning = true;
                    }
                }
            }
        }
        // Check if idea.md exists and mark input stage accordingly
        try {
            const ideaContent = await readFile('idea.md');
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
                    const content = await readFile(file);
                    if (content !== null && content.trim().length > 0) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    setBadge(check.badge, 'done');
                    // Also update pipeline diagram stage
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

    // ZIP buttons in Pipeline section
    document.getElementById('btn-zip-project-pipeline')?.addEventListener('click', function() {
        downloadZip('/code');
    });

    // ZIP button in Results > Files tab
    document.getElementById('btn-zip-from-results')?.addEventListener('click', function() {
        downloadZip('/code');
    });

    // ZIP buttons in Results > Download tab
    document.getElementById('btn-zip-whole-project')?.addEventListener('click', function() {
        downloadZip('/');
    });
    document.getElementById('btn-zip-code-only')?.addEventListener('click', function() {
        downloadZip('/code');
    });
    document.getElementById('btn-zip-custom')?.addEventListener('click', function() {
        const customPath = document.getElementById('zip-custom-path')?.value?.trim() || '/';
        downloadZip(customPath);
    });

    // =========================================================
    // === Git REST API Helpers ===
    // =========================================================
    const gitApiBase = basePath + '/.git/api';

    async function gitApiCall(action, options) {
        const url = gitApiBase + '/' + action;
        const resp = await fetch(url, {
            credentials: 'include',
            ...options
        });
        if (!resp.ok) {
            const text = await resp.text().catch(() => '');
            throw new Error(`Git API ${action} failed: ${resp.status} ${resp.statusText}\n${text}`);
        }
        const data = await resp.json();
        if (data.success === false) {
            throw new Error(data.error || `Git ${action} failed`);
        }
        return data;
    }

    // --- Git Status ---
    async function refreshGitStatus() {
        const display = document.getElementById('git-status-display');
        if (!display) return;
        display.style.display = 'block';
        display.innerHTML = '<p class="placeholder">Loading status…</p>';
        try {
            const data = await gitApiCall('status');
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
    document.getElementById('btn-git-init')?.addEventListener('click', async function() {
        this.disabled = true;
        setStatus('git-status-msg', 'Initializing repository…', '');
        try {
            const data = await gitApiCall('init', { method: 'POST' });
            setStatus('git-status-msg', '✓ ' + (data.message || 'Repository initialized'), 'success');
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-status-msg', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // --- Git Commit ---
    document.getElementById('btn-git-commit')?.addEventListener('click', async function() {
        const messageInput = document.getElementById('git-commit-message');
        const message = messageInput?.value?.trim() || '';
        this.disabled = true;
        setStatus('git-commit-status', 'Committing…', '');
        try {
            const body = {};
            if (message) body.message = message;
            const data = await gitApiCall('commit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
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
            const data = await gitApiCall('branches');
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
            // Click to switch branch
            display.querySelectorAll('.git-branch-item:not(.current)').forEach(item => {
                item.addEventListener('click', async function() {
                    const branchName = this.dataset.branch;
                    if (!branchName) return;
                    if (!confirm(`Switch to branch "${branchName}"?`)) return;
                    setStatus('git-branch-status', 'Switching…', '');
                    try {
                        await gitApiCall('checkout', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ branch: branchName, create: false })
                        });
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

    // Switch to existing branch
    document.getElementById('btn-git-checkout')?.addEventListener('click', async function() {
        const branchName = document.getElementById('git-branch-name')?.value?.trim();
        if (!branchName) {
            alert('Please enter a branch name.');
            return;
        }
        this.disabled = true;
        setStatus('git-branch-status', 'Switching…', '');
        try {
            const data = await gitApiCall('checkout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ branch: branchName, create: false })
            });
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

    // Create and switch to new branch
    document.getElementById('btn-git-create-branch')?.addEventListener('click', async function() {
        const branchName = document.getElementById('git-branch-name')?.value?.trim();
        if (!branchName) {
            alert('Please enter a name for the new branch.');
            return;
        }
        this.disabled = true;
        setStatus('git-branch-status', 'Creating branch…', '');
        try {
            const data = await gitApiCall('checkout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ branch: branchName, create: true })
            });
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
        const maxCount = document.getElementById('git-log-count')?.value || '20';
        try {
            const data = await gitApiCall('log?maxCount=' + encodeURIComponent(maxCount));
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

    // Quick Snapshot: init if needed, then commit with timestamp
    document.getElementById('quick-snapshot')?.addEventListener('click', async function() {
        setStatus('git-quick-status', 'Taking snapshot…', '');
        try {
            // Ensure initialized
            const status = await gitApiCall('status');
            if (!status.initialized) {
                await gitApiCall('init', { method: 'POST' });
            }
            // Commit
            const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19);
            const data = await gitApiCall('commit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: 'Snapshot ' + timestamp })
            });
            const commitHash = data.commitHash ? ` (${data.commitHash.substring(0, 8)})` : '';
            setStatus('git-quick-status', '✓ Snapshot taken' + commitHash, 'success');
            await refreshGitStatus();
            await refreshGitLog();
        } catch (e) {
            setStatus('git-quick-status', '✗ ' + e.message, 'error');
        }
    });

    // Quick Experiment: commit current, create experiment branch
    document.getElementById('quick-branch-experiment')?.addEventListener('click', async function() {
        setStatus('git-quick-status', 'Setting up experiment…', '');
        try {
            // Ensure initialized
            const status = await gitApiCall('status');
            if (!status.initialized) {
                await gitApiCall('init', { method: 'POST' });
            }
            // Commit current work
            if (!status.clean) {
                await gitApiCall('commit', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: 'Save before experiment' })
                });
            }
            // Create experiment branch
            const expName = 'experiment-' + Date.now().toString(36);
            await gitApiCall('checkout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ branch: expName, create: true })
            });
            setStatus('git-quick-status', '✓ Switched to branch: ' + expName, 'success');
            await refreshGitBranches();
            await refreshGitStatus();
        } catch (e) {
            setStatus('git-quick-status', '✗ ' + e.message, 'error');
        }
    });

    // Quick Backup & Download: commit then ZIP
    document.getElementById('quick-backup-zip')?.addEventListener('click', async function() {
        setStatus('git-quick-status', 'Backing up…', '');
        try {
            // Ensure initialized
            const status = await gitApiCall('status');
            if (!status.initialized) {
                await gitApiCall('init', { method: 'POST' });
            }
            // Commit
            const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19);
            await gitApiCall('commit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: 'Backup ' + timestamp })
            });
            setStatus('git-quick-status', '✓ Committed. Downloading ZIP…', 'success');
            // Download ZIP
            downloadZip('/');
        } catch (e) {
            // If "nothing to commit", still download
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
            const status = await gitApiCall('status');
            if (!status.initialized) {
                await gitApiCall('init', { method: 'POST' });
                logBatch('📌 Auto-initialized Git repository', 'info');
            }
            // Re-check status after potential init
            const currentStatus = status.initialized ? status : await gitApiCall('status');
            if (currentStatus.initialized && !currentStatus.clean) {
                await gitApiCall('commit', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: message || 'Auto-commit after build' })
                });
                logBatch('📌 Auto-committed changes to Git: ' + message, 'success');
            }
        } catch (e) {
            // Non-critical, just log
            console.warn('Auto-commit failed:', e);
        }
    }

    // === Usage Tracking ===
    // =========================================================

    async function fetchUsageData(taskSessionId) {
        try {
            const url = `/proxy/usage?sessionId=${encodeURIComponent(taskSessionId)}&format=json`;
            const resp = await fetch(url);
            if (!resp.ok) {
                if (resp.status === 404) return null;
                return null;
            }
            return await resp.json();
        } catch (e) {
            console.warn('Could not fetch usage for session ' + taskSessionId + ':', e);
            return null;
        }
    }

    function formatCost(cost) {
        if (cost === null || cost === undefined) return '$0.00';
        if (cost < 0.01) return '$' + cost.toFixed(4);
        return '$' + cost.toFixed(4);
    }

    function formatTokens(tokens) {
        if (!tokens) return '0';
        if (tokens >= 1000000) return (tokens / 1000000).toFixed(1) + 'M';
        if (tokens >= 1000) return (tokens / 1000).toFixed(1) + 'K';
        return tokens.toLocaleString();
    }

    function renderUsageTable(usageData, containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;

        if (!usageData || !usageData.models || usageData.models.length === 0) {
            container.innerHTML = '<p class="placeholder">No usage data available.</p>';
            return;
        }

        let html = `<table class="usage-table">
            <thead>
                <tr>
                    <th>Model</th>
                    <th>Prompt Tokens</th>
                    <th>Completion Tokens</th>
                    <th>Total Tokens</th>
                    <th>Cost</th>
                </tr>
            </thead>
            <tbody>`;

        usageData.models.forEach(model => {
            const totalTokens = (model.prompt_tokens || 0) + (model.completion_tokens || 0);
            html += `<tr>
                <td><code>${escapeHtml(model.model)}</code></td>
                <td>${formatTokens(model.prompt_tokens)}</td>
                <td>${formatTokens(model.completion_tokens)}</td>
                <td>${formatTokens(totalTokens)}</td>
                <td class="usage-cost-cell">${formatCost(model.cost)}</td>
            </tr>`;
        });

        if (usageData.totals) {
            const totalTokens = (usageData.totals.prompt_tokens || 0) + (usageData.totals.completion_tokens || 0);
            html += `<tr class="usage-totals-row">
                <td><strong>Total</strong></td>
                <td><strong>${formatTokens(usageData.totals.prompt_tokens)}</strong></td>
                <td><strong>${formatTokens(usageData.totals.completion_tokens)}</strong></td>
                <td><strong>${formatTokens(totalTokens)}</strong></td>
                <td class="usage-cost-cell"><strong>${formatCost(usageData.totals.cost)}</strong></td>
            </tr>`;
        }

        html += '</tbody></table>';
        container.innerHTML = html;
    }

    function updateUsageSummaryBanner(usageData) {
        const banner = document.getElementById('usage-summary-banner');
        if (!banner) return;

        if (!usageData || !usageData.totals) {
            banner.style.display = 'none';
            return;
        }

        banner.style.display = 'flex';
        const promptEl = document.getElementById('usage-total-prompt');
        const completionEl = document.getElementById('usage-total-completion');
        const costEl = document.getElementById('usage-total-cost');

        if (promptEl) promptEl.textContent = formatTokens(usageData.totals.prompt_tokens);
        if (completionEl) completionEl.textContent = formatTokens(usageData.totals.completion_tokens);
        if (costEl) costEl.textContent = formatCost(usageData.totals.cost);
    }

    async function refreshAllUsage() {
        setStatus('usage-status', 'Loading usage data…', '');

        // Collect all known task session IDs
        const statusData = await fetchDocopsStatus();
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

        // Aggregate usage across all task sessions
        const allModels = {};
        const taskUsageList = [];
        let totalPrompt = 0;
        let totalCompletion = 0;
        let totalCost = 0;

        for (const taskId of knownTaskSessionIds) {
            const usage = await fetchUsageData(taskId);
            if (usage && usage.models) {
                taskUsageList.push({ sessionId: taskId, usage: usage });
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

        const aggregated = {
            models: Object.values(allModels),
            totals: {
                prompt_tokens: totalPrompt,
                completion_tokens: totalCompletion,
                cost: totalCost
            }
        };

        lastUsageData = aggregated;

        // Render the main usage table
        renderUsageTable(aggregated, 'usage-table-container');
        updateUsageSummaryBanner(aggregated);

        // Render the tab version too
        renderUsageTable(aggregated, 'usage-tab-container');

        // Render per-task usage
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
            const totalTokens = (totals.prompt_tokens || 0) + (totals.completion_tokens || 0);
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
                        ${formatTokens((m.prompt_tokens || 0) + (m.completion_tokens || 0))} tokens,
                        ${formatCost(m.cost)}
                    </span>`;
                });
            }

            html += `</div></div>`;
        });

        container.innerHTML = html;
    }

    // Track task session IDs as they appear
    function trackTaskSession(taskSessionId) {
        if (taskSessionId && /^[a-zA-Z0-9-]+$/.test(taskSessionId)) {
            knownTaskSessionIds.add(taskSessionId);
        }
    }

    // Usage button handlers
    document.getElementById('btn-refresh-usage')?.addEventListener('click', refreshAllUsage);
    document.getElementById('btn-refresh-task-usage')?.addEventListener('click', refreshAllUsage);
    document.getElementById('btn-refresh-usage-tab')?.addEventListener('click', refreshAllUsage);

    document.getElementById('btn-usage-json')?.addEventListener('click', async function() {
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
     loadApiProviders();
    // Delay usage refresh slightly to allow status polling to discover task sessions first
    setTimeout(refreshAllUsage, 2000);
})();