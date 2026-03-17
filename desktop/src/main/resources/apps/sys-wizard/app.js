(function () {
    'use strict';

    // === URL Parsing & Session Setup ===
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';

    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
    } else {
        console.warn('Could not determine session from URL path.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }

    const proxyBase = '/proxy/';
    function getProxyUrl(id) { return proxyBase + '#' + id; }

    // === File I/O ===
    async function readFile(filePath) {
        const resp = await fetch(basePath + '/' + filePath);
        if (!resp.ok) {
            if (resp.status === 404) return null;
            throw new Error('Failed to read ' + filePath + ': ' + resp.status);
        }
        return await resp.text();
    }

    async function writeFile(filePath, content) {
        const resp = await fetch(basePath + '/' + filePath, {
            method: 'PUT',
            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
            body: content
        });
        if (!resp.ok) throw new Error('Failed to write ' + filePath + ': ' + resp.status);
        return true;
    }

    async function runDocOp(opPath, targetPath) {
        const url = '/docops?sessionId=' + encodeURIComponent(sessionId) +
            '&doc=' + encodeURIComponent(opPath) +
            '&target=' + encodeURIComponent(targetPath);
        const resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            const errText = await resp.text().catch(function () { return ''; });
            throw new Error('DocOps failed: ' + resp.status + '\n' + errText);
        }
        return await resp.text();
    }

    // === Status Polling ===
    async function fetchDocopsStatus() {
        try {
            const resp = await fetch(basePath + '/docops.status.json');
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) { return null; }
    }

    async function waitForTask(targetPath, maxWaitMs) {
        var maxWait = maxWaitMs || 600000;
        var pollInterval = 2000;
        var startTime = Date.now();
        while (Date.now() - startTime < maxWait) {
            var statusData = await fetchDocopsStatus();
            if (statusData && statusData.tasks && statusData.tasks[targetPath]) {
                var task = statusData.tasks[targetPath];
                if (task.status === 'COMPLETED') return task;
                if (task.status === 'ERROR' || task.status === 'FAILED') {
                    throw new Error('Task ' + targetPath + ' failed');
                }
            }
            await new Promise(function (r) { setTimeout(r, pollInterval); });
        }
        throw new Error('Task ' + targetPath + ' timed out');
    }

    var statusPollTimer = null;
    var STATUS_POLL_INTERVAL = 3000;

    var badgeMap = {
        'code/script.sh': 'badge-codegen',
        'code/fix_log.md': 'badge-run'
    };

    var stageMap = {
        'code/script.sh': 'stage-codegen-status',
        'code/fix_log.md': 'stage-run-status'
    };

    function startStatusPolling() {
        if (statusPollTimer) return;
        statusPollTimer = setInterval(function () { pollStatus(); }, STATUS_POLL_INTERVAL);
        pollStatus();
    }

    function stopStatusPolling() {
        if (statusPollTimer) {
            clearInterval(statusPollTimer);
            statusPollTimer = null;
        }
    }

    async function pollStatus() {
        var statusData = await fetchDocopsStatus();
        if (!statusData || !statusData.tasks) return;
        for (var target in statusData.tasks) {
            if (!statusData.tasks.hasOwnProperty(target)) continue;
            var taskInfo = statusData.tasks[target];
            var badgeId = badgeMap[target];
            var stageId = stageMap[target];
            if (badgeId) {
                if (taskInfo.status === 'RUNNING') setBadge(badgeId, 'running');
                else if (taskInfo.status === 'COMPLETED') setBadge(badgeId, 'done');
                else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') setBadge(badgeId, 'error');
            }
            if (stageId) {
                var stageEl = document.getElementById(stageId);
                if (stageEl) {
                    if (taskInfo.status === 'RUNNING') { stageEl.textContent = 'Running…'; stageEl.className = 'stage-status running'; }
                    else if (taskInfo.status === 'COMPLETED') { stageEl.textContent = 'Done'; stageEl.className = 'stage-status done'; }
                    else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') { stageEl.textContent = 'Error'; stageEl.className = 'stage-status error'; }
                }
            }
            updateSessionLinks(target, taskInfo);
        }
    }

    // === UI Helpers ===
    function renderMarkdown(md) {
        if (typeof marked !== 'undefined') {
            return typeof marked.parse === 'function' ? marked.parse(md) : marked(md);
        }
        return '<pre>' + escapeHtml(md) + '</pre>';
    }

    function renderScript(code) {
        return '<pre class="script-code"><code>' + escapeHtml(code) + '</code></pre>';
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function setStatus(elemId, message, type) {
        var el = document.getElementById(elemId);
        if (!el) return;
        el.textContent = message;
        el.className = 'status-msg' + (type ? ' ' + type : '');
        if (type === 'success' || type === 'error') {
            setTimeout(function () { el.textContent = ''; el.className = 'status-msg'; }, 5000);
        }
    }

    function setBadge(badgeId, state) {
        var el = document.getElementById(badgeId);
        if (!el) return;
        el.className = 'step-badge ' + state;
        var labels = { pending: 'pending', running: 'running…', done: 'done', error: 'error' };
        el.textContent = labels[state] || state;
    }

    // === Session Monitoring Links ===
    function updateSessionLinks(target, taskInfo) {
        var status = taskInfo.status;
        var taskSessionId = taskInfo.sessionId;
        var safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        var linkContainerId = 'session-link-' + safeTarget;
        var container = document.getElementById(linkContainerId);
        if (!container) {
            container = document.createElement('div');
            container.id = linkContainerId;
            container.className = 'session-link-container';
            // Try to insert near the relevant viewer
            var viewerIds = {
                'code/script.sh': 'viewer-codegen',
                'code/fix_log.md': 'viewer-run'
            };
            var viewerId = viewerIds[target];
            var viewer = viewerId ? document.getElementById(viewerId) : null;
            if (viewer && viewer.parentElement) {
                viewer.parentElement.insertBefore(container, viewer);
            } else {
                // Fallback: append to batch log area
                var batchLog = document.getElementById('batch-log');
                if (batchLog && batchLog.parentElement) {
                    batchLog.parentElement.insertBefore(container, batchLog);
                }
            }
        }
        if (!container) return;

        if (status === 'RUNNING' && taskSessionId) {
            var proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-monitor-link">' +
                '<span class="monitor-pulse">●</span> ' +
                '<span>Processing… </span>' +
                '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '📡 Monitor Live Session (' + escapeHtml(taskSessionId.substring(0, 12)) + '…)</a>' +
                '</div>';
            container.style.display = 'block';
        } else if (status === 'COMPLETED' && taskSessionId) {
            var proxyUrl2 = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-completed-link">' +
                '<span>✅ Completed — </span>' +
                '<a href="' + escapeHtml(proxyUrl2) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '📋 View Session Log</a>' +
                '</div>';
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            var proxyUrl3 = taskSessionId ? getProxyUrl(taskSessionId) : '#';
            container.innerHTML =
                '<div class="session-error-link">' +
                '<span>❌ Failed — </span>' +
                (taskSessionId
                    ? '<a href="' + escapeHtml(proxyUrl3) + '" target="_blank" class="monitor-link">🔍 View Error Log</a>'
                    : '<span>No session available</span>') +
                '</div>';
            container.style.display = 'block';
        } else {
            container.style.display = 'none';
        }
    }

    // === Batch Log ===
    var batchLog = document.getElementById('batch-log');

    function logBatch(message, type) {
        batchLog.classList.add('visible');
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.textContent = '[' + ts + '] ' + message;
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }

    function logBatchHtml(html, type) {
        batchLog.classList.add('visible');
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.innerHTML = '[' + ts + '] ' + html;
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }

    // === Navigation ===
    document.querySelectorAll('.nav-link').forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            document.querySelectorAll('.nav-link').forEach(function (l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function (s) { s.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(this.dataset.section).classList.add('active');
        });
    });

    // === Results Tabs ===
    document.querySelectorAll('.results-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.results-tab').forEach(function (t) { t.classList.remove('active'); });
            document.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(this.dataset.tab).classList.add('active');
        });
    });

    // === Save Goal ===
    document.getElementById('save-goal').addEventListener('click', async function () {
        var content = document.getElementById('goal-editor').value;
        if (!content.trim()) {
            setStatus('goal-status', '✗ Please enter a goal first', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('goal.md', content);
            setStatus('goal-status', '✓ Goal saved', 'success');
            var stageEl = document.getElementById('stage-goal-status');
            if (stageEl) { stageEl.textContent = 'Saved'; stageEl.className = 'stage-status done'; }
        } catch (e) {
            setStatus('goal-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === View File Buttons ===
    function isScriptFile(filePath) {
        return /\.(sh|bash|zsh|py|rb|pl|js)$/i.test(filePath);
    }

    async function viewFile(filePath, viewerId) {
        var viewer = document.getElementById(viewerId);
        if (!viewer) return;
        if (viewer.classList.contains('visible')) {
            viewer.classList.remove('visible');
            return;
        }
        try {
            var content = await readFile(filePath);
            if (content === null) {
                viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
            } else if (isScriptFile(filePath)) {
                viewer.innerHTML = renderScript(content);
            } else {
                viewer.innerHTML = renderMarkdown(content);
            }
            viewer.classList.add('visible');
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            viewer.classList.add('visible');
        }
    }

    document.querySelectorAll('.btn-view').forEach(function (btn) {
        btn.addEventListener('click', function () {
            viewFile(this.dataset.file, this.dataset.viewer);
        });
    });

    // === Refresh Buttons (Results section) ===
    document.querySelectorAll('.btn-refresh').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var viewerId = this.dataset.viewer;
            var filePath = this.dataset.file;
            var viewer = document.getElementById(viewerId);
            if (!viewer) return;
            try {
                var content = await readFile(filePath);
                if (content === null) {
                    viewer.innerHTML = '<p class="placeholder">File not found.</p>';
                } else if (isScriptFile(filePath)) {
                    viewer.innerHTML = renderScript(content);
                } else {
                    viewer.innerHTML = renderMarkdown(content);
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            }
        });
    });

    // === Copy Script Button ===
    document.getElementById('copy-script').addEventListener('click', async function () {
        try {
            var content = await readFile('code/script.sh');
            if (content === null) {
                alert('No script generated yet. Run the pipeline first.');
                return;
            }
            await navigator.clipboard.writeText(content);
            this.textContent = '✓ Copied!';
            var self = this;
            setTimeout(function () { self.textContent = '📋 Copy to Clipboard'; }, 2000);
        } catch (e) {
            alert('Failed to copy: ' + e.message);
        }
    });

    // === Run Operation Buttons ===
    document.querySelectorAll('.btn-run').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            // Auto-save goal before running
            var goalContent = document.getElementById('goal-editor').value;
            if (!goalContent.trim()) {
                alert('Please enter a goal first.');
                return;
            }
            await writeFile('goal.md', goalContent);

            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();

            try {
                var taskId = await runDocOp(opPath, outputPath);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(outputPath);
                setBadge(badgeId, 'done');

                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        var content = await readFile(outputPath);
                        if (content) {
                            viewer.innerHTML = isScriptFile(outputPath) ? renderScript(content) : renderMarkdown(content);
                            viewer.classList.add('visible');
                        }
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

    // === Batch Execution ===
    async function runSequential(steps) {
        for (var i = 0; i < steps.length; i++) {
            var step = steps[i];
            logBatch('Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');

            try {
                var taskId = await runDocOp(step.op, step.output);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    logBatchHtml('Session: <a href="' + getProxyUrl(cleanTaskId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId.substring(0, 12) + '…)</a>', 'info');
                    updateSessionLinks(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(step.output);
                setBadge(step.badge, 'done');
                logBatch('✓ Completed: ' + step.label, 'success');

                if (step.viewer) {
                    try {
                        var content = await readFile(step.output);
                        if (content) {
                            var viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                viewer.innerHTML = isScriptFile(step.output) ? renderScript(content) : renderMarkdown(content);
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }

                if (step.afterFn) await step.afterFn();
            } catch (e) {
                setBadge(step.badge, 'error');
                logBatch('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
    }

    document.getElementById('run-all').addEventListener('click', async function () {
        // Auto-save goal
        var goalContent = document.getElementById('goal-editor').value;
        if (!goalContent.trim()) {
            alert('Please enter a goal first.');
            return;
        }

        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';

        try {
            await writeFile('goal.md', goalContent);
            logBatch('✓ Goal saved', 'success');
            var stageEl = document.getElementById('stage-goal-status');
            if (stageEl) { stageEl.textContent = 'Saved'; stageEl.className = 'stage-status done'; }

            await runSequential([
                {
                    op: 'ops/code_op.md',
                    output: 'code/script.sh',
                    badge: 'badge-codegen',
                    viewer: 'viewer-codegen',
                    label: 'Generate Shell Script'
                },
                {
                    op: 'ops/run_op.md',
                    output: 'code/fix_log.md',
                    badge: 'badge-run',
                    viewer: 'viewer-run',
                    label: 'Run & Auto-Fix',
                    afterFn: async function () {
                        // Refresh the script viewer too since auto-fix may have modified it
                        try {
                            var scriptContent = await readFile('code/script.sh');
                            if (scriptContent) {
                                var sv = document.getElementById('viewer-codegen');
                                if (sv && sv.classList.contains('visible')) {
                                    sv.innerHTML = renderScript(scriptContent);
                                }
                            }
                        } catch (e) { /* non-critical */ }
                    }
                }
            ]);
            logBatch('🎉 Pipeline complete!', 'success');

            // Auto-switch to results tab
            document.querySelectorAll('.nav-link').forEach(function (l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function (s) { s.classList.remove('active'); });
            var resultsLink = document.querySelector('[data-section="section-results"]');
            if (resultsLink) resultsLink.classList.add('active');
            document.getElementById('section-results').classList.add('active');

            // Auto-load results
            await loadResults();
        } catch (e) {
            logBatch('Pipeline stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Load Results ===
    async function loadResults() {
        // Load script
        try {
            var scriptContent = await readFile('code/script.sh');
            var scriptViewer = document.getElementById('result-script');
            if (scriptViewer && scriptContent) {
                scriptViewer.innerHTML = renderScript(scriptContent);
            }
        } catch (e) { /* ignore */ }

        // Load fix log
        try {
            var logContent = await readFile('code/fix_log.md');
            var logViewer = document.getElementById('result-log');
            if (logViewer && logContent) {
                logViewer.innerHTML = renderMarkdown(logContent);
            }
        } catch (e) { /* ignore */ }

        // Load goal
        try {
            var goalContent = await readFile('goal.md');
            var goalViewer = document.getElementById('result-goal');
            if (goalViewer && goalContent) {
                goalViewer.innerHTML = renderMarkdown(goalContent);
            }
        } catch (e) { /* ignore */ }
    }

    // === Check Existing Files on Load ===
    async function checkExistingFiles() {
        var statusData = await fetchDocopsStatus();
        var anyRunning = false;

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                var badgeId = badgeMap[target];
                if (badgeId) {
                    if (taskInfo.status === 'RUNNING') { setBadge(badgeId, 'running'); anyRunning = true; }
                    else if (taskInfo.status === 'COMPLETED') setBadge(badgeId, 'done');
                    else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') setBadge(badgeId, 'error');
                }
                updateSessionLinks(target, taskInfo);
            }
        }

        // Fall back to file existence checks
        var checks = [
            { file: 'code/script.sh', badge: 'badge-codegen' },
            { file: 'code/fix_log.md', badge: 'badge-run' }
        ];
        for (var i = 0; i < checks.length; i++) {
            var check = checks[i];
            var badge = document.getElementById(check.badge);
            if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
            try {
                var content = await readFile(check.file);
                if (content !== null && content.trim().length > 0) setBadge(check.badge, 'done');
            } catch (e) { /* leave as pending */ }
        }

        // Check goal
        try {
            var goalContent = await readFile('goal.md');
            if (goalContent !== null && goalContent.trim().length > 0) {
                var stageEl = document.getElementById('stage-goal-status');
                if (stageEl) { stageEl.textContent = 'Saved'; stageEl.className = 'stage-status done'; }
            }
        } catch (e) { /* ignore */ }

        if (anyRunning) startStatusPolling();
    }

    // === Load Initial Files ===
    async function loadInitialFiles() {
        try {
            var content = await readFile('goal.md');
            if (content !== null) {
                document.getElementById('goal-editor').value = content;
            }
        } catch (e) {
            console.warn('Could not load goal.md:', e);
        }
    }

    // === Initialize ===
    loadInitialFiles();
    checkExistingFiles();
    startStatusPolling();

})();