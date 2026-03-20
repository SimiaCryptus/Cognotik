(function () {
    'use strict';

    // ========================================================================
    // URL Parsing & Session Setup
    // ========================================================================
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

    // ========================================================================
    // File I/O
    // ========================================================================
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

    // ========================================================================
    // Status Polling
    // ========================================================================
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
            if (statusData && statusData.tasks) {
                var task = statusData.tasks[targetPath];
                if (!task) {
                    // Try matching by filename only
                    var filename = targetPath.split('/').pop();
                    task = statusData.tasks[filename];
                }
                if (task) {
                    if (task.status === 'COMPLETED') return task;
                    if (task.status === 'ERROR' || task.status === 'FAILED') {
                        throw new Error('Task ' + targetPath + ' failed');
                    }
                }
            }
            await new Promise(function (r) { setTimeout(r, pollInterval); });
        }
        throw new Error('Task ' + targetPath + ' timed out');
    }

    var statusPollTimer = null;
    var STATUS_POLL_INTERVAL = 3000;

    // Map of output file -> badge ID
    var badgeMap = {
        'summary.md': 'badge-summary',
        'content.md': 'badge-content',
        'brainstorm.md': 'badge-brainstorm',
        'dialectical.md': 'badge-dialectical',
        'socratic.md': 'badge-socratic',
        'perspectives.md': 'badge-perspectives',
        'persuasive.md': 'badge-persuasive',
        'gametheory.md': 'badge-gametheory',
        'narrative.md': 'badge-narrative',
        'comic.md': 'badge-comic',
        'technical_explanation.md': 'badge-technical'
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
        var anyRunning = false;
        for (var target in statusData.tasks) {
            if (!statusData.tasks.hasOwnProperty(target)) continue;
            var taskInfo = statusData.tasks[target];
            var bid = badgeMap[target];
            if (bid) {
                if (taskInfo.status === 'RUNNING') { setBadge(bid, 'running'); anyRunning = true; }
                else if (taskInfo.status === 'COMPLETED') setBadge(bid, 'done');
                else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') setBadge(bid, 'error');
            }
            updateSessionLinks(target, taskInfo);
        }
        if (!anyRunning) {
            // Check if we should stop polling
            var allDone = true;
            for (var t in statusData.tasks) {
                if (statusData.tasks[t].status === 'RUNNING' || statusData.tasks[t].status === 'PENDING') {
                    allDone = false;
                    break;
                }
            }
            // Keep polling anyway for responsiveness
        }
    }

    // ========================================================================
    // UI Helpers
    // ========================================================================
    function renderMarkdown(md) {
        if (typeof marked !== 'undefined') {
            return typeof marked.parse === 'function' ? marked.parse(md) : marked(md);
        }
        return '<pre>' + escapeHtml(md) + '</pre>';
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
            setTimeout(function () {
                el.textContent = '';
                el.className = 'status-msg';
            }, 5000);
        }
    }

    function setBadge(badgeId, state) {
        var el = document.getElementById(badgeId);
        if (!el) return;
        el.className = 'step-badge ' + state;
        var labels = {
            pending: 'pending',
            running: 'running…',
            done: 'done',
            error: 'error'
        };
        el.textContent = labels[state] || state;
    }

    // ========================================================================
    // Session Monitor Links
    // ========================================================================
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
            // Try to find the viewer for this target and insert before it
            var viewerIdGuess = 'viewer-' + target.replace(/\.md$/, '').replace(/[^a-zA-Z0-9]/g, '-');
            var viewer = document.getElementById(viewerIdGuess);
            if (viewer && viewer.parentElement) {
                viewer.parentElement.insertBefore(container, viewer);
            }
        }
        if (!container) return;

        if (status === 'RUNNING' && taskSessionId) {
            var proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-monitor-link">' +
                '<span class="monitor-pulse">●</span>' +
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

    // ========================================================================
    // Batch Logging
    // ========================================================================
    function getLogEl(logId) {
        return document.getElementById(logId || 'batch-log');
    }

    function logBatch(message, type, logId) {
        var logEl = getLogEl(logId);
        if (!logEl) return;
        logEl.classList.add('visible');
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.textContent = '[' + ts + '] ' + message;
        logEl.appendChild(entry);
        logEl.scrollTop = logEl.scrollHeight;
    }

    function logBatchHtml(html, type, logId) {
        var logEl = getLogEl(logId);
        if (!logEl) return;
        logEl.classList.add('visible');
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.innerHTML = '[' + ts + '] ' + html;
        logEl.appendChild(entry);
        logEl.scrollTop = logEl.scrollHeight;
    }

    // ========================================================================
    // Navigation
    // ========================================================================
    document.querySelectorAll('.nav-link').forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            var sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(function (l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function (s) { s.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(sectionId).classList.add('active');
        });
    });

    // ========================================================================
    // Results Tabs
    // ========================================================================
    document.querySelectorAll('.results-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.results-tab').forEach(function (t) { t.classList.remove('active'); });
            document.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(this.dataset.tab).classList.add('active');
        });
    });

    // ========================================================================
    // Save Buttons
    // ========================================================================
    document.getElementById('save-notes').addEventListener('click', async function () {
        var content = document.getElementById('notes-editor').value;
        if (!content.trim()) {
            setStatus('notes-status', '✗ Notes cannot be empty', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('notes/notes.md', content);
            setStatus('notes-status', '✓ Notes saved', 'success');
        } catch (e) {
            setStatus('notes-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('save-instruct').addEventListener('click', async function () {
        var content = document.getElementById('instruct-editor').value;
        try {
            this.disabled = true;
            await writeFile('instruct.md', content);
            setStatus('instruct-status', '✓ Instructions saved', 'success');
        } catch (e) {
            setStatus('instruct-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // ========================================================================
    // View File (toggle)
    // ========================================================================
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

    // Results refresh buttons
    document.querySelectorAll('.btn-refresh').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var viewerId = this.dataset.viewer;
            var viewer = document.getElementById(viewerId);
            if (!viewer) return;
            try {
                var content = await readFile(this.dataset.file);
                if (content === null) {
                    viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                } else {
                    viewer.innerHTML = renderMarkdown(content);
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            }
        });
    });

    // ========================================================================
    // Run Single Operation
    // ========================================================================
    document.querySelectorAll('.btn-run').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            // Auto-save notes before running
            var notesContent = document.getElementById('notes-editor').value;
            if (notesContent.trim()) {
                await writeFile('notes/notes.md', notesContent);
            }
            var instructContent = document.getElementById('instruct-editor').value;
            if (instructContent.trim()) {
                await writeFile('instruct.md', instructContent);
            }

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
                            viewer.innerHTML = renderMarkdown(content);
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

    // ========================================================================
    // Sequential Batch Execution
    // ========================================================================
    async function runSequential(steps, logId) {
        for (var i = 0; i < steps.length; i++) {
            var step = steps[i];
            logBatch('Starting: ' + step.label, 'info', logId);
            setBadge(step.badge, 'running');

            try {
                var taskId = await runDocOp(step.op, step.output);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    var proxyUrl = getProxyUrl(cleanTaskId);
                    logBatchHtml('Session: <a href="' + proxyUrl + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId.substring(0, 12) + '…)</a>', 'info', logId);
                    updateSessionLinks(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(step.output);
                setBadge(step.badge, 'done');
                logBatch('✓ Completed: ' + step.label, 'success', logId);

                if (step.viewer) {
                    try {
                        var content = await readFile(step.output);
                        if (content) {
                            var viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                viewer.innerHTML = renderMarkdown(content);
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }

                if (step.afterFn) await step.afterFn();
            } catch (e) {
                setBadge(step.badge, 'error');
                logBatch('✗ Failed: ' + step.label + ' — ' + e.message, 'error', logId);
                throw e;
            }
        }
    }

    // ========================================================================
    // Core Pipeline (Summarize → Draft)
    // ========================================================================
    document.getElementById('run-core-pipeline').addEventListener('click', async function () {
        // Auto-save
        var notesContent = document.getElementById('notes-editor').value;
        if (!notesContent.trim()) {
            alert('Please enter your notes first.');
            return;
        }
        await writeFile('notes/notes.md', notesContent);
        var instructContent = document.getElementById('instruct-editor').value;
        if (instructContent.trim()) {
            await writeFile('instruct.md', instructContent);
        }

        this.disabled = true;
        var batchLog = document.getElementById('batch-log');
        batchLog.innerHTML = '';
        startStatusPolling();

        try {
            await runSequential([
                {
                    op: 'ops/summarize_op.md',
                    output: 'summary.md',
                    badge: 'badge-summary',
                    viewer: 'viewer-summary',
                    label: 'Summarize Notes'
                },
                {
                    op: 'ops/draft_article_op.md',
                    output: 'content.md',
                    badge: 'badge-content',
                    viewer: 'viewer-content',
                    label: 'Draft Article'
                }
            ], 'batch-log');
            logBatch('🎉 Core pipeline complete!', 'success', 'batch-log');
        } catch (e) {
            logBatch('Pipeline stopped due to error.', 'error', 'batch-log');
        } finally {
            this.disabled = false;
        }
    });

    // ========================================================================
    // Full Pipeline (Core + All Lenses + Update)
    // ========================================================================
    var lensDefinitions = [
        { key: 'brainstorm', op: 'ops/brainstorm_op.md', output: 'brainstorm.md', badge: 'badge-brainstorm', viewer: 'viewer-brainstorm', label: 'Brainstorm' },
        { key: 'dialectical', op: 'ops/dialectical_op.md', output: 'dialectical.md', badge: 'badge-dialectical', viewer: 'viewer-dialectical', label: 'Dialectical Analysis' },
        { key: 'socratic', op: 'ops/socratic_op.md', output: 'socratic.md', badge: 'badge-socratic', viewer: 'viewer-socratic', label: 'Socratic Dialogue' },
        { key: 'perspectives', op: 'ops/perspectives_op.md', output: 'perspectives.md', badge: 'badge-perspectives', viewer: 'viewer-perspectives', label: 'Multi-Perspective Analysis' },
        { key: 'persuasive', op: 'ops/persuasive_op.md', output: 'persuasive.md', badge: 'badge-persuasive', viewer: 'viewer-persuasive', label: 'Persuasive Essay' },
        { key: 'gametheory', op: 'ops/gametheory_op.md', output: 'gametheory.md', badge: 'badge-gametheory', viewer: 'viewer-gametheory', label: 'Game Theory Analysis' },
        { key: 'narrative', op: 'ops/narrative_op.md', output: 'narrative.md', badge: 'badge-narrative', viewer: 'viewer-narrative', label: 'Narrative Dramatization' },
        { key: 'comic', op: 'ops/comic_op.md', output: 'comic.md', badge: 'badge-comic', viewer: 'viewer-comic', label: 'Comic Book Generation' },
        { key: 'technical', op: 'ops/technical_explanation_op.md', output: 'technical_explanation.md', badge: 'badge-technical', viewer: 'viewer-technical', label: 'Technical Explanation' }
    ];

    document.getElementById('run-full-pipeline').addEventListener('click', async function () {
        var notesContent = document.getElementById('notes-editor').value;
        if (!notesContent.trim()) {
            alert('Please enter your notes first.');
            return;
        }
        await writeFile('notes/notes.md', notesContent);
        var instructContent = document.getElementById('instruct-editor').value;
        if (instructContent.trim()) {
            await writeFile('instruct.md', instructContent);
        }

        this.disabled = true;
        document.getElementById('run-core-pipeline').disabled = true;
        var batchLog = document.getElementById('batch-log');
        batchLog.innerHTML = '';
        startStatusPolling();

        try {
            // Phase 1: Core pipeline
            logBatch('═══ Phase 1: Core Pipeline ═══', 'info', 'batch-log');
            await runSequential([
                { op: 'ops/summarize_op.md', output: 'summary.md', badge: 'badge-summary', viewer: 'viewer-summary', label: 'Summarize Notes' },
                { op: 'ops/draft_article_op.md', output: 'content.md', badge: 'badge-content', viewer: 'viewer-content', label: 'Draft Article' }
            ], 'batch-log');

            // Phase 2: All lenses
            logBatch('═══ Phase 2: Analytical Lenses ═══', 'info', 'batch-log');
            var lensSteps = lensDefinitions.map(function (lens) {
                return {
                    op: lens.op,
                    output: lens.output,
                    badge: lens.badge,
                    viewer: lens.viewer,
                    label: lens.label
                };
            });
            await runSequential(lensSteps, 'batch-log');

            // Phase 3: Update article
            logBatch('═══ Phase 3: Synthesis ═══', 'info', 'batch-log');
            await runSequential([
                { op: 'ops/update_article_op.md', output: 'content.md', badge: 'badge-update', viewer: 'viewer-update', label: 'Update Article (Synthesize All Lenses)' }
            ], 'batch-log');

            logBatch('🎉 Full pipeline complete! Your article has been enhanced with all analytical lenses.', 'success', 'batch-log');
        } catch (e) {
            logBatch('Pipeline stopped due to error: ' + e.message, 'error', 'batch-log');
        } finally {
            this.disabled = false;
            document.getElementById('run-core-pipeline').disabled = false;
        }
    });

    // ========================================================================
    // Run Selected Lenses
    // ========================================================================
    document.getElementById('run-selected-lenses').addEventListener('click', async function () {
        var selectedKeys = [];
        document.querySelectorAll('.lens-check:checked').forEach(function (cb) {
            selectedKeys.push(cb.value);
        });

        if (selectedKeys.length === 0) {
            alert('Please select at least one lens to run.');
            return;
        }

        // Auto-save
        var notesContent = document.getElementById('notes-editor').value;
        if (notesContent.trim()) {
            await writeFile('notes/notes.md', notesContent);
        }
        var instructContent = document.getElementById('instruct-editor').value;
        if (instructContent.trim()) {
            await writeFile('instruct.md', instructContent);
        }

        this.disabled = true;
        var lensLog = document.getElementById('lens-batch-log');
        lensLog.innerHTML = '';
        startStatusPolling();

        var steps = [];
        for (var i = 0; i < lensDefinitions.length; i++) {
            var lens = lensDefinitions[i];
            if (selectedKeys.indexOf(lens.key) >= 0) {
                steps.push({
                    op: lens.op,
                    output: lens.output,
                    badge: lens.badge,
                    viewer: lens.viewer,
                    label: lens.label
                });
            }
        }

        try {
            await runSequential(steps, 'lens-batch-log');
            logBatch('🎉 All selected lenses complete!', 'success', 'lens-batch-log');
        } catch (e) {
            logBatch('Lens execution stopped due to error: ' + e.message, 'error', 'lens-batch-log');
        } finally {
            this.disabled = false;
        }
    });

    // Select/Deselect All
    document.getElementById('select-all-lenses').addEventListener('click', function () {
        document.querySelectorAll('.lens-check').forEach(function (cb) { cb.checked = true; });
    });

    document.getElementById('deselect-all-lenses').addEventListener('click', function () {
        document.querySelectorAll('.lens-check').forEach(function (cb) { cb.checked = false; });
    });

    // ========================================================================
    // Check Existing Files on Load
    // ========================================================================
    async function checkExistingFiles() {
        var statusData = await fetchDocopsStatus();
        var anyRunning = false;

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                var bid = badgeMap[target];
                if (bid) {
                    if (taskInfo.status === 'RUNNING') { setBadge(bid, 'running'); anyRunning = true; }
                    else if (taskInfo.status === 'COMPLETED') setBadge(bid, 'done');
                    else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') setBadge(bid, 'error');
                }
                updateSessionLinks(target, taskInfo);
            }
        }

        // Fall back to file existence checks
        var fileChecks = [
            { file: 'summary.md', badge: 'badge-summary' },
            { file: 'content.md', badge: 'badge-content' },
            { file: 'brainstorm.md', badge: 'badge-brainstorm' },
            { file: 'dialectical.md', badge: 'badge-dialectical' },
            { file: 'socratic.md', badge: 'badge-socratic' },
            { file: 'perspectives.md', badge: 'badge-perspectives' },
            { file: 'persuasive.md', badge: 'badge-persuasive' },
            { file: 'gametheory.md', badge: 'badge-gametheory' },
            { file: 'narrative.md', badge: 'badge-narrative' },
            { file: 'comic.md', badge: 'badge-comic' },
            { file: 'technical_explanation.md', badge: 'badge-technical' }
        ];

        for (var i = 0; i < fileChecks.length; i++) {
            var check = fileChecks[i];
            var badge = document.getElementById(check.badge);
            if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
            try {
                var content = await readFile(check.file);
                if (content !== null && content.trim().length > 0) {
                    setBadge(check.badge, 'done');
                }
            } catch (e) { /* leave as pending */ }
        }

        if (anyRunning) startStatusPolling();
    }

    // ========================================================================
    // Load Initial Files
    // ========================================================================
    async function loadInitialFiles() {
        try {
            var notes = await readFile('notes/notes.md');
            if (notes !== null) {
                document.getElementById('notes-editor').value = notes;
            }
        } catch (e) { console.warn('Could not load notes:', e); }

        try {
            var instruct = await readFile('instruct.md');
            if (instruct !== null) {
                document.getElementById('instruct-editor').value = instruct;
            }
        } catch (e) { console.warn('Could not load instruct.md:', e); }
    }

    // ========================================================================
    // Initialize
    // ========================================================================
    loadInitialFiles();
    checkExistingFiles();
    startStatusPolling();
    // ========================================================================
    // File Upload
    // ========================================================================
    var uploadZone = document.getElementById('upload-zone');
    var fileInput = document.getElementById('file-input');
    var uploadedFilesList = document.getElementById('uploaded-files-list');
    // Prevent default drag behaviors on the whole document
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (eventName) {
        document.body.addEventListener(eventName, function (e) {
            e.preventDefault();
            e.stopPropagation();
        }, false);
    });
    // Highlight drop zone on drag
    ['dragenter', 'dragover'].forEach(function (eventName) {
        uploadZone.addEventListener(eventName, function () {
            uploadZone.classList.add('drag-over');
        }, false);
    });
    ['dragleave', 'drop'].forEach(function (eventName) {
        uploadZone.addEventListener(eventName, function () {
            uploadZone.classList.remove('drag-over');
        }, false);
    });
    // Handle drop
    uploadZone.addEventListener('drop', function (e) {
        var files = e.dataTransfer.files;
        if (files && files.length > 0) {
            handleFileUpload(files);
        }
    }, false);
    // Handle file input change
    fileInput.addEventListener('change', function () {
        if (this.files && this.files.length > 0) {
            handleFileUpload(this.files);
        }
        // Reset so the same file can be re-uploaded
        this.value = '';
    });
    async function handleFileUpload(files) {
        var totalFiles = files.length;
        var completed = 0;
        var failed = 0;
        setStatus('notes-status', 'Uploading ' + totalFiles + ' file(s)…', '');
        for (var i = 0; i < files.length; i++) {
            var file = files[i];
            try {
                await uploadSingleFile(file);
                completed++;
                setStatus('notes-status', 'Uploaded ' + completed + '/' + totalFiles + '…', '');
            } catch (e) {
                failed++;
                console.error('Failed to upload ' + file.name + ':', e);
            }
        }
        if (failed === 0) {
            setStatus('notes-status', '✓ Uploaded ' + completed + ' file(s)', 'success');
        } else {
            setStatus('notes-status', '⚠ Uploaded ' + completed + ', failed ' + failed, 'error');
        }
        await refreshUploadedFileList();
    }
    async function uploadSingleFile(file) {
        var fileName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_');
        var filePath = 'notes/' + fileName;
        // Determine content type
        var contentType = file.type || 'application/octet-stream';
        var resp = await fetch(basePath + '/' + filePath, {
            method: 'PUT',
            headers: { 'Content-Type': contentType },
            body: file
        });
        if (!resp.ok) {
            throw new Error('Upload failed for ' + fileName + ': ' + resp.status);
        }
        return true;
    }
    async function refreshUploadedFileList() {
        try {
            var resp = await fetch(basePath + '/notes/');
            if (!resp.ok) {
                uploadedFilesList.innerHTML = '';
                return;
            }
            var text = await resp.text();
            // Try to parse as JSON directory listing
            var files = [];
            try {
                var json = JSON.parse(text);
                if (Array.isArray(json)) {
                    files = json;
                } else if (json.files && Array.isArray(json.files)) {
                    files = json.files;
                } else if (json.children && Array.isArray(json.children)) {
                    files = json.children;
                }
            } catch (e) {
                // Try to parse as HTML directory listing
                var parser = new DOMParser();
                var doc = parser.parseFromString(text, 'text/html');
                var links = doc.querySelectorAll('a');
                links.forEach(function (a) {
                    var href = a.getAttribute('href');
                    if (href && href !== '../' && href !== './' && href !== '/') {
                        var name = decodeURIComponent(href.replace(/\/$/, '').split('/').pop());
                        if (name && name !== '.' && name !== '..') {
                            files.push(name);
                        }
                    }
                });
            }
            // Normalize file entries to strings
            files = files.map(function (f) {
                if (typeof f === 'string') return f;
                if (f && f.name) return f.name;
                if (f && f.fileName) return f.fileName;
                return String(f);
            }).filter(function (f) {
                return f && f !== '.' && f !== '..';
            });
            renderFileList(files);
        } catch (e) {
            console.warn('Could not list notes directory:', e);
            uploadedFilesList.innerHTML = '';
        }
    }
    function renderFileList(files) {
        if (!files || files.length === 0) {
            uploadedFilesList.innerHTML = '';
            uploadedFilesList.style.display = 'none';
            return;
        }
        uploadedFilesList.style.display = 'block';
        var html = '<div class="file-list-header">📂 Files in <code>notes/</code> (' + files.length + ')</div>';
        html += '<div class="file-list-items">';
        for (var i = 0; i < files.length; i++) {
            var fname = escapeHtml(files[i]);
            var ext = files[i].split('.').pop().toLowerCase();
            var icon = getFileIcon(ext);
            html += '<div class="file-list-item">' +
                '<span class="file-item-icon">' + icon + '</span>' +
                '<span class="file-item-name">' + fname + '</span>' +
                '<button class="btn btn-sm btn-danger-ghost file-delete-btn" data-filename="' + fname + '" title="Delete file">✕</button>' +
                '</div>';
        }
        html += '</div>';
        uploadedFilesList.innerHTML = html;
        // Attach delete handlers
        uploadedFilesList.querySelectorAll('.file-delete-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var filename = this.dataset.filename;
                if (!confirm('Delete notes/' + filename + '?')) return;
                try {
                    var resp = await fetch(basePath + '/notes/' + encodeURIComponent(filename), {
                        method: 'DELETE'
                    });
                    if (!resp.ok && resp.status !== 404) {
                        throw new Error('Delete failed: ' + resp.status);
                    }
                    setStatus('notes-status', '✓ Deleted ' + filename, 'success');
                    await refreshUploadedFileList();
                } catch (e) {
                    setStatus('notes-status', '✗ ' + e.message, 'error');
                }
            });
        });
    }
    function getFileIcon(ext) {
        var icons = {
            'md': '📝', 'txt': '📄', 'text': '📄',
            'pdf': '📕', 'doc': '📘', 'docx': '📘',
            'rtf': '📃', 'odt': '📃',
            'html': '🌐', 'htm': '🌐',
            'json': '📋', 'xml': '📋', 'csv': '📊',
            'jpg': '🖼️', 'jpeg': '🖼️', 'png': '🖼️', 'gif': '🖼️', 'svg': '🖼️',
            'mp3': '🎵', 'wav': '🎵', 'mp4': '🎬',
            'zip': '📦', 'tar': '📦', 'gz': '📦'
        };
        return icons[ext] || '📎';
    }
    // Refresh file list button
    document.getElementById('refresh-file-list').addEventListener('click', function () {
        refreshUploadedFileList();
    });
    // Load file list on startup
    refreshUploadedFileList();

})();