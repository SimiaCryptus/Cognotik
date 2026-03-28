(function () {
    'use strict';

    // ============================================================
    // URL Parsing & Session Setup
    // ============================================================
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';
    let appId = '';

    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
        appId = pathParts[fileIndexIdx - 1] || 'npm-project';
    } else {
        console.warn('Could not determine session from URL path.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }

    const proxyBase = '/proxy/';
    function getProxyUrl(id) {
        return proxyBase + '#' + id;
    }
    // ============================================================
    // App Base Path (for settings, appInfo, etc.)
    // ============================================================
    var appBase = '';
    if (fileIndexIdx >= 2) {
        appBase = pathParts.slice(0, fileIndexIdx).join('/');
    }
    // If appBase is empty or just '/', fall back to constructing from appId
    if (!appBase || appBase === '/') {
        appBase = '/' + (appId || 'npm-project');
    }


    // ============================================================
    // File I/O
    // ============================================================
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

    async function listFiles(dirPath) {
        const url = basePath + '/' + dirPath + '/_files.json';
        const resp = await fetch(url);
        if (!resp.ok) {
            if (resp.status === 404) return [];
            throw new Error('Failed to list ' + dirPath + ': ' + resp.status);
        }
        const data = await resp.json();
        return data.entries || [];
    }

    // ============================================================
    // DocOps Execution
    // ============================================================
    async function runDocOp(opPath, targetPath) {
        var params = new URLSearchParams({
            sessionId: sessionId,
            doc: opPath,
            target: targetPath
        });

        // Add model overrides from current selections
        var models = getSelectedModels();
        if (models.smartModel) params.set('smartModel', models.smartModel);
        if (models.fastModel) params.set('fastModel', models.fastModel);
        if (models.imageModel) params.set('imageModel', models.imageModel);

        var url = '/docops?' + params.toString();
        const resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            const errText = await resp.text().catch(function () { return ''; });
            throw new Error('DocOps failed: ' + resp.status + ' ' + resp.statusText + '\n' + errText);
        }
        return await resp.text();
    }

    // ============================================================
    // Status Polling
    // ============================================================
    async function fetchDocopsStatus() {
        try {
            const resp = await fetch(basePath + '/docops.status.json');
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) {
            return null;
        }
    }

    async function waitForTask(targetPath, maxWaitMs) {
        var maxWait = maxWaitMs || 900000; // 15 minutes
        var pollInterval = 2500;
        var startTime = Date.now();

        while (Date.now() - startTime < maxWait) {
            var statusData = await fetchDocopsStatus();
            if (statusData && statusData.tasks) {
                var task = getTaskStatus(statusData, targetPath);
                if (task) {
                    if (task.status === 'COMPLETED') return task;
                    if (task.status === 'ERROR' || task.status === 'FAILED') {
                        throw new Error('Task ' + targetPath + ' failed');
                    }
                }
            }
            await new Promise(function (resolve) { setTimeout(resolve, pollInterval); });
        }
        throw new Error('Task ' + targetPath + ' timed out after ' + Math.round(maxWait / 1000) + 's');
    }

    function getTaskStatus(statusData, targetPath) {
        if (!statusData || !statusData.tasks) return null;
        // Exact match
        if (statusData.tasks[targetPath]) return statusData.tasks[targetPath];
     // Try with/without trailing slash
     var altPath = targetPath.endsWith('/') ? targetPath.slice(0, -1) : targetPath + '/';
     if (statusData.tasks[altPath]) return statusData.tasks[altPath];
        // Match by filename only
        var filename = targetPath.split('/').pop();
        if (statusData.tasks[filename]) return statusData.tasks[filename];
        // Search all tasks
        for (var key in statusData.tasks) {
            if (statusData.tasks.hasOwnProperty(key)) {
                var t = statusData.tasks[key];
                if (t.target === targetPath || t.target === filename) return t;
            }
        }
        return null;
    }

    var statusPollTimer = null;
    var STATUS_POLL_INTERVAL = 3000;

    function startStatusPolling() {
        if (statusPollTimer) return;
        statusPollTimer = setInterval(pollAllStatus, STATUS_POLL_INTERVAL);
        pollAllStatus();
    }

    function stopStatusPolling() {
        if (statusPollTimer) {
            clearInterval(statusPollTimer);
            statusPollTimer = null;
        }
    }

    // Map of target paths to badge IDs and diagram IDs
    var targetBadgeMap = {
        'code/design.md': { badge: 'badge-design', diagram: 'diagram-design' },
     'code': { badge: 'badge-scaffold', diagram: 'diagram-scaffold' },
     'code': { badge: 'badge-scaffold', diagram: 'diagram-scaffold' },
        'code/install_log.md': { badge: 'badge-install', diagram: 'diagram-install' },
     'code/src': { badge: 'badge-implement', diagram: 'diagram-implement' },
     'code': { badge: 'badge-implement', diagram: 'diagram-implement' },
        'code/build_log.md': { badge: 'badge-build', diagram: 'diagram-build' },
        'code/test_log.md': { badge: 'badge-test-run', diagram: 'diagram-test-run' }
    };

    async function pollAllStatus() {
        var statusData = await fetchDocopsStatus();
        if (!statusData || !statusData.tasks) return;

        var anyRunning = false;
        for (var target in statusData.tasks) {
            if (!statusData.tasks.hasOwnProperty(target)) continue;
            var taskInfo = statusData.tasks[target];
            if (taskInfo.status === 'RUNNING') anyRunning = true;

            // Update badges
            var mapping = targetBadgeMap[target];
         if (!mapping) {
             // Try with/without trailing slash
             var alt = target.endsWith('/') ? target.slice(0, -1) : target + '/';
             mapping = targetBadgeMap[alt];
         }
            if (mapping) {
                updateBadgeFromStatus(mapping.badge, taskInfo.status);
                updateDiagramStage(mapping.diagram, taskInfo.status);
            }

            // Update session links
            updateSessionLinks(target, taskInfo);
        }

        if (!anyRunning && statusPollTimer) {
            // Keep polling at a slower rate
        }
    }

    function updateBadgeFromStatus(badgeId, status) {
        if (status === 'RUNNING') setBadge(badgeId, 'running');
        else if (status === 'COMPLETED') setBadge(badgeId, 'done');
        else if (status === 'ERROR' || status === 'FAILED') setBadge(badgeId, 'error');
    }

    function updateDiagramStage(diagramId, status) {
        var el = document.getElementById(diagramId);
        if (!el) return;
        var stage = el.closest('.pipeline-stage');
        if (!stage) return;

        stage.classList.remove('running', 'done', 'error');
        if (status === 'RUNNING') {
            stage.classList.add('running');
            el.textContent = 'Running…';
        } else if (status === 'COMPLETED') {
            stage.classList.add('done');
            el.textContent = 'Done ✓';
        } else if (status === 'ERROR' || status === 'FAILED') {
            stage.classList.add('error');
            el.textContent = 'Error ✗';
        }
    }

    // ============================================================
    // UI Helpers
    // ============================================================
    function renderMarkdown(md) {
        if (typeof marked !== 'undefined') {
            if (typeof marked.parse === 'function') return marked.parse(md);
            return marked(md);
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

    function showLoading(text) {
        var overlay = document.getElementById('loading-overlay');
        var loadingText = document.getElementById('loading-text');
        if (loadingText) loadingText.textContent = text || 'Processing...';
        if (overlay) overlay.classList.remove('hidden');
    }

    function hideLoading() {
        var overlay = document.getElementById('loading-overlay');
        if (overlay) overlay.classList.add('hidden');
    }

    // ============================================================
    // Batch Log
    // ============================================================
    var batchLog = document.getElementById('batch-log');

    function logBatch(message, type) {
        if (!batchLog) return;
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.textContent = '[' + ts + '] ' + message;
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }

    function logBatchHtml(html, type) {
        if (!batchLog) return;
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.innerHTML = '[' + ts + '] ' + html;
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }

    function clearBatchLog() {
        if (batchLog) batchLog.innerHTML = '';
    }

    // ============================================================
    // Session Monitor Links
    // ============================================================
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
            var viewerCandidates = [
                'viewer-' + safeTarget,
                'viewer-design', 'viewer-scaffold', 'viewer-install',
                'viewer-implement', 'viewer-test-write', 'viewer-build', 'viewer-test-run'
            ];
            var inserted = false;
            // Find the matching viewer by checking target mapping
            for (var tgt in targetBadgeMap) {
                if (targetBadgeMap.hasOwnProperty(tgt) && tgt === target) {
                     var bId = targetBadgeMap[tgt].badge;
                     var viewerName = bId.replace('badge-', 'viewer-');
                     var viewer = document.getElementById(viewerName);
                     if (viewer && viewer.parentElement) {
                         viewer.parentElement.insertBefore(container, viewer);
                         inserted = true;
                     }
                     break;
                 }
             }
             // Try with/without trailing slash if not found
             if (!inserted) {
                 var altTarget = target.endsWith('/') ? target.slice(0, -1) : target + '/';
                 for (var tgt in targetBadgeMap) {
                     if (targetBadgeMap.hasOwnProperty(tgt) && tgt === altTarget) {
                    var bId = targetBadgeMap[tgt].badge;
                    var viewerName = bId.replace('badge-', 'viewer-');
                    var viewer = document.getElementById(viewerName);
                    if (viewer && viewer.parentElement) {
                        viewer.parentElement.insertBefore(container, viewer);
                        inserted = true;
                    }
                    break;
                }
                 }
            }
            if (!inserted) {
                // Fallback: append to batch log area
                var pipelineSection = document.getElementById('section-pipeline');
                if (pipelineSection) pipelineSection.appendChild(container);
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
                '📋 View Session Log (' + escapeHtml(taskSessionId.substring(0, 12)) + '…)</a>' +
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

    // ============================================================
    // Navigation
    // ============================================================
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

    // Results tabs
    document.querySelectorAll('.results-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.results-tab').forEach(function (t) { t.classList.remove('active'); });
            document.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(this.dataset.tab).classList.add('active');
        });
    });

    // ============================================================
    // Save Buttons
    // ============================================================
    document.getElementById('save-idea').addEventListener('click', async function () {
        var content = document.getElementById('idea-editor').value;
        if (!content.trim()) {
            setStatus('idea-status', '✗ Please enter an idea first', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('code/idea.md', content);
            setStatus('idea-status', '✓ Idea saved successfully', 'success');
        } catch (e) {
            setStatus('idea-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('save-notes').addEventListener('click', async function () {
        var content = document.getElementById('notes-editor').value;
        try {
            this.disabled = true;
            await writeFile('code/notes.md', content);
            setStatus('notes-status', '✓ Notes saved successfully', 'success');
        } catch (e) {
            setStatus('notes-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // ============================================================
    // View File Buttons
    // ============================================================
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
            } else if (filePath.endsWith('.json')) {
                try {
                    var formatted = JSON.stringify(JSON.parse(content), null, 2);
                    viewer.innerHTML = '<pre><code>' + escapeHtml(formatted) + '</code></pre>';
                } catch (e2) {
                    viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                }
            } else if (filePath.endsWith('.md')) {
                viewer.innerHTML = renderMarkdown(content);
            } else {
                viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
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
            var filePath = this.dataset.file;
            var viewerId = this.dataset.viewer;
            var viewer = document.getElementById(viewerId);
            if (!viewer) return;

            try {
                var content = await readFile(filePath);
                if (content === null) {
                    viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                } else if (filePath.endsWith('.md')) {
                    viewer.innerHTML = renderMarkdown(content);
                } else if (filePath.endsWith('.json')) {
                    try {
                        viewer.innerHTML = '<pre><code>' + escapeHtml(JSON.stringify(JSON.parse(content), null, 2)) + '</code></pre>';
                    } catch (e2) {
                        viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                    }
                } else {
                    viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            }
        });
    });

    // ============================================================
    // Run Operation Buttons (individual)
    // ============================================================
    document.querySelectorAll('.btn-run').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            // Auto-save idea before running design
            if (opPath === 'ops/design_op.md') {
                var ideaContent = document.getElementById('idea-editor').value;
                if (!ideaContent.trim()) {
                    alert('Please enter an application idea first.');
                    return;
                }
                await writeFile('code/idea.md', ideaContent);
            }

            // Auto-save notes before running update
            if (opPath === 'ops/update_op.md') {
                var notesContent = document.getElementById('notes-editor').value;
                if (!notesContent.trim()) {
                    alert('Please enter update notes first.');
                    return;
                }
                await writeFile('code/notes.md', notesContent);
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

                // Update diagram
                var mapping = targetBadgeMap[outputPath];
                if (mapping) updateDiagramStage(mapping.diagram, 'COMPLETED');

                // Auto-show result
                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        var content = await readFile(outputPath);
                        if (content) {
                            if (outputPath.endsWith('.md')) {
                                viewer.innerHTML = renderMarkdown(content);
                            } else {
                                viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                            }
                            viewer.classList.add('visible');
                        }
                    }
                }
            } catch (e) {
                setBadge(badgeId, 'error');
                if (targetBadgeMap[outputPath]) {
                    updateDiagramStage(targetBadgeMap[outputPath].diagram, 'ERROR');
                }
                alert('Operation failed: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    });

    // ============================================================
    // Sequential Batch Execution
    // ============================================================
    async function runSequential(steps) {
        for (var i = 0; i < steps.length; i++) {
            var step = steps[i];
            logBatch('Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');
            if (step.diagram) updateDiagramStage(step.diagram, 'RUNNING');

            try {
                var taskId = await runDocOp(step.op, step.output);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    var proxyUrl = getProxyUrl(cleanTaskId);
                    logBatchHtml('Session: <a href="' + proxyUrl + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId.substring(0, 12) + '…)</a>', 'info');
                    updateSessionLinks(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
                }

                await waitForTask(step.output);
                setBadge(step.badge, 'done');
                if (step.diagram) updateDiagramStage(step.diagram, 'COMPLETED');
                logBatch('✓ Completed: ' + step.label, 'success');

                // Auto-show result in viewer
                if (step.viewer) {
                    try {
                        var content = await readFile(step.output);
                        if (content) {
                            var viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                if (step.output.endsWith('.md')) {
                                    viewer.innerHTML = renderMarkdown(content);
                                } else {
                                    viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                                }
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }

                // Run optional post-step callback
                if (step.afterFn) await step.afterFn();

            } catch (e) {
                setBadge(step.badge, 'error');
                if (step.diagram) updateDiagramStage(step.diagram, 'ERROR');
                logBatch('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
    }

    // ============================================================
    // Run Full Pipeline
    // ============================================================
    document.getElementById('run-all').addEventListener('click', async function () {
        // Validate idea
        var ideaContent = document.getElementById('idea-editor').value;
        if (!ideaContent.trim()) {
            alert('Please enter an application idea first in the "Idea & Notes" tab.');
            return;
        }

        this.disabled = true;
        document.getElementById('run-build-cycle').disabled = true;
        document.getElementById('run-update').disabled = true;
        clearBatchLog();
        startStatusPolling();

        try {
            // Auto-save idea
            logBatch('Saving idea...', 'info');
            await writeFile('code/idea.md', ideaContent);
            logBatch('✓ Idea saved', 'success');

            await runSequential([
                {
                    op: 'ops/design_op.md',
                    output: 'code/design.md',
                    badge: 'badge-design',
                    diagram: 'diagram-design',
                    viewer: 'viewer-design',
                    label: 'Step 1: Design — Analyzing idea and choosing technologies'
                },
                {
                    op: 'ops/scaffold_op.md',
                    output: 'code',
                    badge: 'badge-scaffold',
                    diagram: 'diagram-scaffold',
                    viewer: 'viewer-scaffold',
                    label: 'Step 2: Scaffold — Generating project skeleton'
                },
                {
                    op: 'ops/install_op.md',
                    output: 'code/install_log.md',
                    badge: 'badge-install',
                    diagram: 'diagram-install',
                    viewer: 'viewer-install',
                    label: 'Step 3: Install — Running npm install'
                },
                {
                    op: 'ops/implement_op.md',
                    output: 'code',
                    badge: 'badge-implement',
                    diagram: 'diagram-implement',
                    viewer: 'viewer-implement',
                    label: 'Step 4: Implement — Writing application source code'
                },
                {
                    op: 'ops/test_write_op.md',
                    output: 'code',
                    badge: 'badge-test-write',
                    diagram: 'diagram-test-write',
                    label: 'Step 5: Write Tests — Generating test files'
                },
                {
                    op: 'ops/build_op.md',
                    output: 'code/build_log.md',
                    badge: 'badge-build',
                    diagram: 'diagram-build',
                    viewer: 'viewer-build',
                    label: 'Step 6: Build — Running npm run build (with auto-fix)'
                },
                {
                    op: 'ops/test_run_op.md',
                    output: 'code/test_log.md',
                    badge: 'badge-test-run',
                    diagram: 'diagram-test-run',
                    viewer: 'viewer-test-run',
                    label: 'Step 7: Test Run — Running npm test (with auto-fix)'
                }
            ]);

            logBatch('🎉 Full pipeline complete! Your project is built and tested.', 'success');

            // Switch to results tab
            document.querySelectorAll('.nav-link').forEach(function (l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function (s) { s.classList.remove('active'); });
            var resultsLink = document.querySelector('[data-section="section-results"]');
            if (resultsLink) resultsLink.classList.add('active');
            var resultsSection = document.getElementById('section-results');
            if (resultsSection) resultsSection.classList.add('active');

        } catch (e) {
            logBatch('Pipeline stopped due to error. You can fix the issue and re-run individual steps.', 'error');
        } finally {
            this.disabled = false;
            document.getElementById('run-build-cycle').disabled = false;
            document.getElementById('run-update').disabled = false;
        }
    });

    // ============================================================
    // Run Build & Test Only
    // ============================================================
    document.getElementById('run-build-cycle').addEventListener('click', async function () {
        this.disabled = true;
        document.getElementById('run-all').disabled = true;
        document.getElementById('run-update').disabled = true;
        clearBatchLog();
        startStatusPolling();

        try {
            logBatch('Running build & test cycle...', 'info');
            await runSequential([
                {
                    op: 'ops/build_op.md',
                    output: 'code/build_log.md',
                    badge: 'badge-build',
                    diagram: 'diagram-build',
                    viewer: 'viewer-build',
                    label: 'Build — Running npm run build (with auto-fix)'
                },
                {
                    op: 'ops/test_run_op.md',
                    output: 'code/test_log.md',
                    badge: 'badge-test-run',
                    diagram: 'diagram-test-run',
                    viewer: 'viewer-test-run',
                    label: 'Test Run — Running npm test (with auto-fix)'
                }
            ]);
            logBatch('🎉 Build & test cycle complete!', 'success');
        } catch (e) {
            logBatch('Build/test cycle stopped due to error.', 'error');
        } finally {
            this.disabled = false;
            document.getElementById('run-all').disabled = false;
            document.getElementById('run-update').disabled = false;
        }
    });

    // ============================================================
    // Run Update Cycle
    // ============================================================
    document.getElementById('run-update').addEventListener('click', async function () {
        var notesContent = document.getElementById('notes-editor').value;
        if (!notesContent.trim()) {
            alert('Please enter update notes first in the "Idea & Notes" tab.');
            return;
        }

        this.disabled = true;
        document.getElementById('run-all').disabled = true;
        document.getElementById('run-build-cycle').disabled = true;
        clearBatchLog();
        startStatusPolling();

        try {
            // Auto-save notes
            logBatch('Saving update notes...', 'info');
            await writeFile('code/notes.md', notesContent);
            logBatch('✓ Notes saved', 'success');

            await runSequential([
                {
                    op: 'ops/update_op.md',
                    output: 'code',
                    badge: 'badge-update',
                    label: 'Update — Applying changes from notes'
                },
                {
                    op: 'ops/build_op.md',
                    output: 'code/build_log.md',
                    badge: 'badge-build',
                    diagram: 'diagram-build',
                    viewer: 'viewer-build',
                    label: 'Rebuild — Running npm run build (with auto-fix)'
                },
                {
                    op: 'ops/test_run_op.md',
                    output: 'code/test_log.md',
                    badge: 'badge-test-run',
                    diagram: 'diagram-test-run',
                    viewer: 'viewer-test-run',
                    label: 'Retest — Running npm test (with auto-fix)'
                }
            ]);
            logBatch('🎉 Update cycle complete! Changes applied, built, and tested.', 'success');
        } catch (e) {
            logBatch('Update cycle stopped due to error.', 'error');
        } finally {
            this.disabled = false;
            document.getElementById('run-all').disabled = false;
            document.getElementById('run-build-cycle').disabled = false;
        }
    });

    // ============================================================
    // Browse Code Button
    // ============================================================
    document.getElementById('btn-browse-code').addEventListener('click', function () {
        // Switch to results tab, files sub-tab
        document.querySelectorAll('.nav-link').forEach(function (l) { l.classList.remove('active'); });
        document.querySelectorAll('.section').forEach(function (s) { s.classList.remove('active'); });
        var resultsLink = document.querySelector('[data-section="section-results"]');
        if (resultsLink) resultsLink.classList.add('active');
        document.getElementById('section-results').classList.add('active');

        // Activate files tab
        document.querySelectorAll('.results-tab').forEach(function (t) { t.classList.remove('active'); });
        document.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
        var filesTab = document.querySelector('[data-tab="tab-files"]');
        if (filesTab) filesTab.classList.add('active');
        document.getElementById('tab-files').classList.add('active');

        // Trigger refresh
        loadFileTree('code');
    });

    document.getElementById('btn-browse-src').addEventListener('click', function () {
        document.querySelectorAll('.nav-link').forEach(function (l) { l.classList.remove('active'); });
        document.querySelectorAll('.section').forEach(function (s) { s.classList.remove('active'); });
        var resultsLink = document.querySelector('[data-section="section-results"]');
        if (resultsLink) resultsLink.classList.add('active');
        document.getElementById('section-results').classList.add('active');

        document.querySelectorAll('.results-tab').forEach(function (t) { t.classList.remove('active'); });
        document.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
        var filesTab = document.querySelector('[data-tab="tab-files"]');
        if (filesTab) filesTab.classList.add('active');
        document.getElementById('tab-files').classList.add('active');

        loadFileTree('code/src');
    });

    // ============================================================
    // File Browser
    // ============================================================
    var currentBrowsePath = 'code';

    document.getElementById('btn-refresh-files').addEventListener('click', function () {
        loadFileTree(currentBrowsePath);
    });

    async function loadFileTree(dirPath) {
        currentBrowsePath = dirPath;
        var browser = document.getElementById('file-browser');
        if (!browser) return;

        browser.innerHTML = '<p class="placeholder">Loading...</p>';

        try {
            var entries = await listFiles(dirPath);
            if (entries.length === 0) {
                browser.innerHTML = '<p class="placeholder">No files found in ' + escapeHtml(dirPath) + '/</p>';
                return;
            }

            var html = '<div style="margin-bottom:8px;color:var(--color-text-muted);font-size:0.82rem;">';
            html += '📂 <strong>' + escapeHtml(dirPath) + '/</strong>';
            if (dirPath !== 'code') {
                html += ' &nbsp; <a href="#" class="monitor-link" id="browse-up">⬆ Up</a>';
            }
            html += '</div>';
            html += '<ul class="file-tree">';

            // Sort: directories first, then files
            var dirs = entries.filter(function (e) { return e.type === 'directory'; });
            var files = entries.filter(function (e) { return e.type === 'file'; });
            dirs.sort(function (a, b) { return a.name.localeCompare(b.name); });
            files.sort(function (a, b) { return a.name.localeCompare(b.name); });

            dirs.forEach(function (entry) {
                html += '<li class="directory" data-path="' + escapeHtml(dirPath + '/' + entry.name) + '" data-type="directory">';
                html += '<span class="file-icon">📁</span> ' + escapeHtml(entry.name) + '/';
                html += '</li>';
            });

            files.forEach(function (entry) {
                var icon = getFileIcon(entry.name);
                html += '<li data-path="' + escapeHtml(dirPath + '/' + entry.name) + '" data-type="file">';
                html += '<span class="file-icon">' + icon + '</span> ' + escapeHtml(entry.name);
                html += '</li>';
            });

            html += '</ul>';
            browser.innerHTML = html;

            // Attach click handlers
            browser.querySelectorAll('.file-tree li').forEach(function (li) {
                li.addEventListener('click', function () {
                    var path = this.dataset.path;
                    var type = this.dataset.type;
                    if (type === 'directory') {
                        loadFileTree(path);
                    } else {
                        loadFileContent(path);
                    }
                });
            });

            var upLink = document.getElementById('browse-up');
            if (upLink) {
                upLink.addEventListener('click', function (e) {
                    e.preventDefault();
                    var parts = dirPath.split('/');
                    parts.pop();
                    var parentPath = parts.join('/') || 'code';
                    loadFileTree(parentPath);
                });
            }

        } catch (e) {
            browser.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error loading files: ' + escapeHtml(e.message) + '</p>';
        }
    }

    function getFileIcon(filename) {
        var ext = filename.split('.').pop().toLowerCase();
        var icons = {
            'js': '📜', 'jsx': '⚛️', 'ts': '🔷', 'tsx': '⚛️',
            'json': '📋', 'md': '📝', 'html': '🌐', 'css': '🎨',
            'scss': '🎨', 'less': '🎨', 'svg': '🖼️', 'png': '🖼️',
            'jpg': '🖼️', 'gif': '🖼️', 'ico': '🖼️',
            'yml': '⚙️', 'yaml': '⚙️', 'toml': '⚙️',
            'lock': '🔒', 'gitignore': '🙈',
            'env': '🔐', 'sh': '🐚', 'bat': '🐚'
        };
        return icons[ext] || '📄';
    }

    async function loadFileContent(filePath) {
        var viewer = document.getElementById('result-file-content');
        if (!viewer) return;

        try {
            var content = await readFile(filePath);
            if (content === null) {
                viewer.innerHTML = '<p class="placeholder">File not found: ' + escapeHtml(filePath) + '</p>';
                return;
            }

            var header = '<div style="margin-bottom:10px;padding-bottom:8px;border-bottom:1px solid var(--color-border);">';
            header += '<strong style="color:var(--color-text-heading);">' + escapeHtml(filePath) + '</strong>';
            header += '</div>';

            if (filePath.endsWith('.md')) {
                viewer.innerHTML = header + renderMarkdown(content);
            } else if (filePath.endsWith('.json')) {
                try {
                    var formatted = JSON.stringify(JSON.parse(content), null, 2);
                    viewer.innerHTML = header + '<pre><code>' + escapeHtml(formatted) + '</code></pre>';
                } catch (e2) {
                    viewer.innerHTML = header + '<pre><code>' + escapeHtml(content) + '</code></pre>';
                }
            } else {
                viewer.innerHTML = header + '<pre><code>' + escapeHtml(content) + '</code></pre>';
            }
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    }

    // ============================================================
    // Check Existing State on Load
    // ============================================================
    async function checkExistingFiles() {
        var statusData = await fetchDocopsStatus();
        var anyRunning = false;

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                var mapping = targetBadgeMap[target];
                if (mapping) {
                    updateBadgeFromStatus(mapping.badge, taskInfo.status);
                    updateDiagramStage(mapping.diagram, taskInfo.status);
                }
                if (taskInfo.status === 'RUNNING') anyRunning = true;
                updateSessionLinks(target, taskInfo);
            }
        }

        // Fall back to file existence checks
        var fileChecks = [
            { file: 'code/design.md', badge: 'badge-design', diagram: 'diagram-design' },
            { file: 'code/package.json', badge: 'badge-scaffold', diagram: 'diagram-scaffold' },
            { file: 'code/install_log.md', badge: 'badge-install', diagram: 'diagram-install' },
            { file: 'code/build_log.md', badge: 'badge-build', diagram: 'diagram-build' },
            { file: 'code/test_log.md', badge: 'badge-test-run', diagram: 'diagram-test-run' }
        ];

        for (var i = 0; i < fileChecks.length; i++) {
            var check = fileChecks[i];
            var badge = document.getElementById(check.badge);
            if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
            try {
                var content = await readFile(check.file);
                if (content !== null && content.trim().length > 0) {
                    // Only mark as done if not a template placeholder
                    if (!content.match(/^<!--.*-->$/s) || content.trim().split('\n').length > 3) {
                        setBadge(check.badge, 'done');
                        updateDiagramStage(check.diagram, 'COMPLETED');
                    }
                }
            } catch (e) { /* leave as pending */ }
        }

        if (anyRunning) startStatusPolling();
    }

    // ============================================================
    // Load Initial Files
    // ============================================================
    async function loadInitialFiles() {
        try {
            var ideaContent = await readFile('code/idea.md');
            if (ideaContent !== null && ideaContent.trim() && !ideaContent.match(/^<!--.*-->$/s)) {
                document.getElementById('idea-editor').value = ideaContent;
            }
        } catch (e) {
            console.warn('Could not load idea.md:', e);
        }

        try {
            var notesContent = await readFile('code/notes.md');
            if (notesContent !== null && notesContent.trim() && !notesContent.match(/^<!--.*-->$/s)) {
                document.getElementById('notes-editor').value = notesContent;
            }
        } catch (e) {
            console.warn('Could not load notes.md:', e);
        }
    }

    // ============================================================
    // Initialize
    // ============================================================
    loadInitialFiles();
    checkExistingFiles();
    startStatusPolling();
    // ============================================================
    // Model Selection
    // ============================================================
    var availableModels = {};
    var modelsLoaded = false;

    async function loadApiProviders() {
        try {
            var response = await fetch('/apiProviders/?format=json');
            if (response.status >= 400) {
                console.warn('Could not load API providers (status ' + response.status + ')');
                return;
            }

            var providersResponse = await response.json();
            var providers = providersResponse.configuredProviders || [];
            availableModels = {};
            providers.forEach(function (provider) {
                if (provider.models && provider.models.length > 0) {
                    availableModels[provider.name] = provider.models.map(function (model) {
                        return {
                            id: model.name,
                            name: model.name,
                            description: model.maxTokens
                                ? 'Max tokens: ' + model.maxTokens
                                : 'No token limit specified'
                        };
                    });
                }
            });
            modelsLoaded = true;
            populateModelDropdowns();
            showProviderInfo();
            setStatus('model-status', '✓ Loaded ' + countModels() + ' models from ' + Object.keys(availableModels).length + ' providers', 'success');
        } catch (e) {
            console.warn('Failed to load API providers:', e);
            setStatus('model-status', '✗ Failed to load models: ' + e.message, 'error');
        }
    }

    function countModels() {
        var count = 0;
        for (var provider in availableModels) {
            if (availableModels.hasOwnProperty(provider)) {
                count += availableModels[provider].length;
            }
        }
        return count;
    }

    function populateModelDropdowns() {
        var smartSelect = document.getElementById('model-smart');
        var fastSelect = document.getElementById('model-fast');
        var imageSelect = document.getElementById('model-image');
        var selects = [smartSelect, fastSelect, imageSelect].filter(function (s) { return s !== null; });
        if (selects.length === 0) return;
        selects.forEach(function (sel) {
            sel.innerHTML = '';
         var defaultOpt = document.createElement('option');
         defaultOpt.value = '';
         defaultOpt.textContent = '— Select a model —';
         sel.appendChild(defaultOpt);
        });
        var hasModels = false;
        for (var provider in availableModels) {
            if (!availableModels.hasOwnProperty(provider)) continue;
            var models = availableModels[provider];
            if (!models || models.length === 0) continue;
            hasModels = true;
            selects.forEach(function (sel) {
                var optgroup = document.createElement('optgroup');
                optgroup.label = provider;
                models.forEach(function (model) {
                    var option = document.createElement('option');
                    option.value = model.id;
                    option.textContent = model.name;
                    if (model.description) {
                        option.title = model.description;
                    }

                    optgroup.appendChild(option);
                });
             sel.appendChild(optgroup.cloneNode(true));
            });
        }
        if (!hasModels) {
            selects.forEach(function (sel) {
                var option = document.createElement('option');
                option.value = '';
                option.textContent = 'No models available — configure API keys first';
                option.disabled = true;
                sel.appendChild(option);
            });
            return;
        }
        // Restore saved selections
        if (smartSelect) restoreModelSelection(smartSelect, 'smartModel');
        if (fastSelect) restoreModelSelection(fastSelect, 'fastModel');
        if (imageSelect) restoreModelSelection(imageSelect, 'imageModel');
    }

    function restoreModelSelection(selectEl, storageKey) {
        var saved = localStorage.getItem(storageKey);
        if (saved) {
            var options = Array.from(selectEl.options);
            var match = options.find(function (o) { return o.value === saved; });
            if (match) {
                selectEl.value = saved;
            }
        }
    }

    function getSelectedModels() {
        var smartSelect = document.getElementById('model-smart');
        var fastSelect = document.getElementById('model-fast');
        var imageSelect = document.getElementById('model-image');
        return {
            smartModel: smartSelect ? smartSelect.value : '',
            fastModel: fastSelect ? fastSelect.value : '',
            imageModel: imageSelect ? imageSelect.value : ''
        };
    }

    function showProviderInfo() {
        var infoEl = document.getElementById('model-info');
        if (!infoEl) return;
        var providers = Object.keys(availableModels);
        if (providers.length === 0) {
            infoEl.innerHTML = '<span class="model-default-badge">No providers configured</span>';
            infoEl.style.display = 'block';
            return;
        }
        var html = '<div class="provider-list">';
        providers.forEach(function (provider) {
            var models = availableModels[provider];
            html += '<span class="model-active-badge">' + escapeHtml(provider) + ': ' + models.length + ' model' + (models.length !== 1 ? 's' : '') + '</span> ';
        });
        html += '</div>';
        infoEl.innerHTML = html;
        infoEl.style.display = 'block';
    }

    // Save model settings
    document.getElementById('save-model').addEventListener('click', function () {
        var models = getSelectedModels();
        if (models.smartModel) localStorage.setItem('smartModel', models.smartModel);
        if (models.fastModel) localStorage.setItem('fastModel', models.fastModel);
        if (models.imageModel) localStorage.setItem('imageModel', models.imageModel);
        setStatus('model-status', '✓ Model settings saved', 'success');
    });

    // Clear model settings
    var clearModelBtn = document.getElementById('clear-model-settings');
    if (clearModelBtn) {
        clearModelBtn.addEventListener('click', function () {
            localStorage.removeItem('smartModel');
            localStorage.removeItem('fastModel');
            localStorage.removeItem('imageModel');
            var smartSelect = document.getElementById('model-smart');
            var fastSelect = document.getElementById('model-fast');
            var imageSelect = document.getElementById('model-image');
            if (smartSelect && smartSelect.options.length > 0) smartSelect.selectedIndex = 0;
            if (fastSelect && fastSelect.options.length > 0) fastSelect.selectedIndex = 0;
            if (imageSelect && imageSelect.options.length > 0) imageSelect.selectedIndex = 0;
            setStatus('model-status', '✓ Model settings reset to defaults', 'success');
        });
    }

    // Refresh models button
    document.getElementById('refresh-models').addEventListener('click', function () {
        setStatus('model-status', 'Loading models...', 'info');
        loadApiProviders();
    });

    loadApiProviders();
    // ============================================================
    // Git Operations
    // ============================================================
    var gitApiBase = basePath + '/.git/api';
    async function gitApiCall(action, method, body) {
        var url = gitApiBase + '/' + action;
        var opts = {
            method: method || 'GET',
            credentials: 'include'
        };
        if (body) {
            opts.headers = { 'Content-Type': 'application/json' };
            opts.body = JSON.stringify(body);
        }
        var resp = await fetch(url, opts);
        if (!resp.ok) {
            var errText = await resp.text().catch(function () { return ''; });
            throw new Error('Git API error: ' + resp.status + ' ' + errText);
        }
        return await resp.json();
    }
    async function refreshGitStatus() {
        var statusBar = document.getElementById('git-status-bar');
        var indicator = document.getElementById('git-status-indicator');
        var statusText = document.getElementById('git-status-text');
        var commitSection = document.getElementById('git-commit-section');
        var branchSection = document.getElementById('git-branch-section');
        var logSection = document.getElementById('git-log-section');
        var initBtn = document.getElementById('git-init');
        try {
            var data = await gitApiCall('status');
            if (!data.initialized) {
                indicator.textContent = '⚪';
                statusText.textContent = 'No Git repository. Click "Initialize Repo" to start tracking changes.';
                statusBar.className = 'git-status-bar git-not-init';
                commitSection.style.display = 'none';
                branchSection.style.display = 'none';
                logSection.style.display = 'none';
                initBtn.style.display = '';
                return;
            }
            initBtn.style.display = 'none';
            commitSection.style.display = 'block';
            branchSection.style.display = 'block';
            logSection.style.display = 'block';
            if (data.clean) {
                indicator.textContent = '🟢';
                statusText.textContent = 'Branch: ' + (data.currentBranch || 'main') + ' — Working tree clean';
                statusBar.className = 'git-status-bar git-clean';
            } else {
                indicator.textContent = '🟡';
                var changeCount = data.changes ? data.changes.length : 0;
                statusText.textContent = 'Branch: ' + (data.currentBranch || 'main') + ' — ' + changeCount + ' uncommitted change' + (changeCount !== 1 ? 's' : '');
                statusBar.className = 'git-status-bar git-dirty';
            }
            // Render changes list
            var changesList = document.getElementById('git-changes-list');
            if (data.changes && data.changes.length > 0) {
                var html = '<ul class="git-changes-ul">';
                data.changes.forEach(function (c) {
                    var statusLabel = { 'M': 'Modified', 'A': 'Added', 'D': 'Deleted', 'R': 'Renamed', '??': 'Untracked' };
                    var label = statusLabel[c.status] || c.status;
                    var cls = 'git-change-' + (c.status === '??' ? 'untracked' : c.status.toLowerCase());
                    html += '<li class="' + cls + '"><span class="git-change-status">' + escapeHtml(label) + '</span> ' + escapeHtml(c.file) + '</li>';
                });
                html += '</ul>';
                changesList.innerHTML = html;
            } else {
                changesList.innerHTML = '<p class="placeholder" style="padding:8px;">No changes to commit.</p>';
            }
            // Load branches
            refreshGitBranches();
        } catch (e) {
            indicator.textContent = '🔴';
            statusText.textContent = 'Error checking Git status: ' + e.message;
            statusBar.className = 'git-status-bar git-error';
        }
    }
    async function refreshGitBranches() {
        try {
            var data = await gitApiCall('branches');
            var list = document.getElementById('git-branches-list');
            if (!data.branches || data.branches.length === 0) {
                list.innerHTML = '<p class="placeholder" style="padding:8px;">No branches found.</p>';
                return;
            }
            var html = '<ul class="git-branches-ul">';
            data.branches.forEach(function (b) {
                var cls = b.current ? 'git-branch-current' : 'git-branch-item';
                html += '<li class="' + cls + '">';
                if (b.current) {
                    html += '<span class="git-branch-active">●</span> <strong>' + escapeHtml(b.name) + '</strong> (current)';
                } else {
                    html += '<span class="git-branch-dot">○</span> <a href="#" class="git-branch-link" data-branch="' + escapeHtml(b.name) + '">' + escapeHtml(b.name) + '</a>';
                }
                html += '</li>';
            });
            html += '</ul>';
            list.innerHTML = html;
            // Attach checkout handlers
            list.querySelectorAll('.git-branch-link').forEach(function (link) {
                link.addEventListener('click', async function (e) {
                    e.preventDefault();
                    var branchName = this.dataset.branch;
                    if (!confirm('Switch to branch "' + branchName + '"?')) return;
                    try {
                        await gitApiCall('checkout', 'POST', { branch: branchName, create: false });
                        setStatus('git-commit-status', '✓ Switched to ' + branchName, 'success');
                        refreshGitStatus();
                    } catch (err) {
                        alert('Failed to checkout: ' + err.message);
                    }
                });
            });
        } catch (e) {
            console.warn('Could not load branches:', e);
        }
    }
    // Git Init
    document.getElementById('git-init').addEventListener('click', async function () {
        try {
            this.disabled = true;
            await gitApiCall('init', 'POST');
            setStatus('git-commit-status', '✓ Repository initialized', 'success');
            refreshGitStatus();
        } catch (e) {
            alert('Failed to initialize: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });
    // Git Refresh
    document.getElementById('git-refresh-status').addEventListener('click', function () {
        refreshGitStatus();
    });
    // Git Commit
    document.getElementById('git-commit').addEventListener('click', async function () {
        var message = document.getElementById('git-commit-message').value.trim();
        if (!message) {
            message = 'Auto-commit from npm Project Builder';
        }
        try {
            this.disabled = true;
            var result = await gitApiCall('commit', 'POST', { message: message });
            if (result.success) {
                setStatus('git-commit-status', '✓ ' + (result.message || 'Committed'), 'success');
                document.getElementById('git-commit-message').value = '';
                refreshGitStatus();
            } else {
                setStatus('git-commit-status', '✗ ' + (result.error || 'Commit failed'), 'error');
            }
        } catch (e) {
            setStatus('git-commit-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // Git Create Branch
    document.getElementById('git-create-branch').addEventListener('click', async function () {
        var branchName = document.getElementById('git-new-branch').value.trim();
        if (!branchName) {
            alert('Please enter a branch name.');
            return;
        }
        try {
            this.disabled = true;
            await gitApiCall('checkout', 'POST', { branch: branchName, create: true });
            document.getElementById('git-new-branch').value = '';
            setStatus('git-commit-status', '✓ Created and switched to ' + branchName, 'success');
            refreshGitStatus();
        } catch (e) {
            alert('Failed to create branch: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });
    // Git Load Log
    document.getElementById('git-load-log').addEventListener('click', async function () {
        try {
            this.disabled = true;
            var data = await gitApiCall('log?maxCount=20');
            var logList = document.getElementById('git-log-list');
            if (!data.commits || data.commits.length === 0) {
                logList.innerHTML = '<p class="placeholder" style="padding:8px;">No commits yet.</p>';
                return;
            }
            var html = '<ul class="git-log-ul">';
            data.commits.forEach(function (c) {
                var shortHash = c.hash ? c.hash.substring(0, 8) : '?';
                var date = c.date ? new Date(c.date).toLocaleString() : '';
                html += '<li class="git-log-entry">';
                html += '<span class="git-log-hash">' + escapeHtml(shortHash) + '</span> ';
                html += '<span class="git-log-message">' + escapeHtml(c.message) + '</span>';
                if (date) html += '<span class="git-log-date">' + escapeHtml(date) + '</span>';
                html += '</li>';
            });
            html += '</ul>';
            logList.innerHTML = html;
        } catch (e) {
            document.getElementById('git-log-list').innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        } finally {
            this.disabled = false;
        }
    });
    // Initial git status check
    refreshGitStatus();
    // ============================================================
    // ZIP Downloads
    // ============================================================
    function downloadZip(path) {
        var encodedPath = encodeURIComponent(path || '/');
        var url = appBase + '/fileZip?session=' + encodeURIComponent(sessionId) + '&path=' + encodedPath;
        window.location.href = url;
    }
    document.getElementById('zip-download-all').addEventListener('click', function () {
        downloadZip('/');
    });
    document.getElementById('zip-download-code').addEventListener('click', function () {
        downloadZip('/code');
    });

})();