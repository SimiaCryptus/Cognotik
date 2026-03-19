(function() {
    'use strict';

    // =========================================================================
    // URL Parsing & Session Setup
    // =========================================================================
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';
    let appId = '';

    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
        appId = pathParts[fileIndexIdx - 1] || 'vacation-planner';
    } else {
        console.warn('Could not determine session from URL path.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }

    const proxyBase = '/proxy/';
    function getProxyUrl(id) { return proxyBase + '#' + id; }

    // Display session ID in header
    const sessionDisplay = document.getElementById('session-display');
    if (sessionDisplay && sessionId) {
        sessionDisplay.textContent = 'Session: ' + sessionId.substring(0, 12) + '…';
        sessionDisplay.title = sessionId;
    }

    // =========================================================================
    // File I/O
    // =========================================================================
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

    async function fileExists(filePath) {
        try {
            const resp = await fetch(basePath + '/' + filePath, { method: 'HEAD' });
            return resp.ok;
        } catch (e) {
            return false;
        }
    }

    async function runDocOp(opPath, targetPath) {
        const url = '/docops?sessionId=' + encodeURIComponent(sessionId) +
            '&doc=' + encodeURIComponent(opPath) +
            '&target=' + encodeURIComponent(targetPath);
        const resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            const errText = await resp.text().catch(function() { return ''; });
            throw new Error('DocOps failed: ' + resp.status + ' ' + resp.statusText + '\n' + errText);
        }
        return await resp.text();
    }

    // =========================================================================
    // Status Polling
    // =========================================================================
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
            await new Promise(function(r) { setTimeout(r, pollInterval); });
        }
        throw new Error('Task ' + targetPath + ' timed out after ' + Math.round(maxWait / 1000) + 's');
    }

    function getTaskStatus(statusData, targetPath) {
        if (!statusData || !statusData.tasks) return null;
        // Exact match
        if (statusData.tasks[targetPath]) return statusData.tasks[targetPath];
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
        statusPollTimer = setInterval(pollAndUpdateStatus, STATUS_POLL_INTERVAL);
        pollAndUpdateStatus();
    }

    function stopStatusPolling() {
        if (statusPollTimer) {
            clearInterval(statusPollTimer);
            statusPollTimer = null;
        }
    }

    // Map of output file -> { badge, diagram, tableBadge, tableSession }
    var OUTPUT_MAP = {
        'brainstorm_output.md': {
            badge: 'badge-brainstorm',
            diagram: 'diagram-brainstorm',
            tableBadge: 'table-badge-brainstorm',
            tableSession: 'table-session-brainstorm',
            sessionLink: 'session-link-brainstorm_output-md'
        },
        'analysis_output.md': {
            badge: 'badge-analysis',
            diagram: 'diagram-analysis',
            tableBadge: 'table-badge-analysis',
            tableSession: 'table-session-analysis',
            sessionLink: 'session-link-analysis_output-md'
        },
        'research.md': {
            badge: 'badge-crawler',
            diagram: 'diagram-crawler',
            tableBadge: 'table-badge-crawler',
            tableSession: 'table-session-crawler',
            sessionLink: 'session-link-data-crawler_latest-json'
        },
        'itinerary.md': {
            badge: 'badge-plan',
            diagram: 'diagram-plan',
            tableBadge: 'table-badge-plan',
            tableSession: 'table-session-plan',
            sessionLink: 'session-link-itinerary-md'
        },
        'vacation_plan.html': {
            badge: 'badge-generate',
            diagram: 'diagram-generate',
            tableBadge: 'table-badge-generate',
            tableSession: 'table-session-generate',
            sessionLink: 'session-link-vacation_plan-html'
        },
        'validation/validation_report.md': {
            badge: 'badge-validate',
            diagram: '',
            tableBadge: '',
            tableSession: '',
            sessionLink: 'session-link-validation-validation_report-md'
        }
    };

    async function pollAndUpdateStatus() {
        var statusData = await fetchDocopsStatus();
        if (!statusData || !statusData.tasks) return;

        var anyRunning = false;

        for (var target in OUTPUT_MAP) {
            if (!OUTPUT_MAP.hasOwnProperty(target)) continue;
            var mapping = OUTPUT_MAP[target];
            var task = getTaskStatus(statusData, target);

            if (task) {
                var status = task.status;
                if (status === 'RUNNING') {
                    anyRunning = true;
                    setBadge(mapping.badge, 'running');
                    setDiagramStage(mapping.diagram, 'running');
                    setTableBadge(mapping.tableBadge, 'running');
                    updateTableSession(mapping.tableSession, task);
                    updateSessionLinkContainer(mapping.sessionLink, task);
                } else if (status === 'COMPLETED') {
                    setBadge(mapping.badge, 'done');
                    setDiagramStage(mapping.diagram, 'done');
                    setTableBadge(mapping.tableBadge, 'done');
                    updateTableSession(mapping.tableSession, task);
                    updateSessionLinkContainer(mapping.sessionLink, task);
                } else if (status === 'ERROR' || status === 'FAILED') {
                    setBadge(mapping.badge, 'error');
                    setDiagramStage(mapping.diagram, 'error');
                    setTableBadge(mapping.tableBadge, 'error');
                    updateTableSession(mapping.tableSession, task);
                    updateSessionLinkContainer(mapping.sessionLink, task);
                }
            }
        }

        // Update global status indicator
        updateGlobalStatus(anyRunning, statusData);
    }

    function updateGlobalStatus(anyRunning, statusData) {
        var dot = document.querySelector('#global-status .status-dot');
        var text = document.getElementById('global-status-text');
        if (!dot || !text) return;

        if (anyRunning) {
            dot.className = 'status-dot running';
            text.textContent = 'Processing…';
        } else {
            // Check if any tasks completed
            var hasCompleted = false;
            var hasError = false;
            if (statusData && statusData.tasks) {
                for (var key in statusData.tasks) {
                    if (statusData.tasks[key].status === 'COMPLETED') hasCompleted = true;
                    if (statusData.tasks[key].status === 'ERROR' || statusData.tasks[key].status === 'FAILED') hasError = true;
                }
            }
            if (hasError) {
                dot.className = 'status-dot error';
                text.textContent = 'Error';
            } else if (hasCompleted) {
                dot.className = 'status-dot complete';
                text.textContent = 'Complete';
            } else {
                dot.className = 'status-dot idle';
                text.textContent = 'Ready';
            }
        }
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================
    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function renderMarkdown(md) {
        if (typeof marked !== 'undefined') {
            if (typeof marked.parse === 'function') return marked.parse(md);
            return marked(md);
        }
        return '<pre>' + escapeHtml(md) + '</pre>';
    }

    function renderContent(content, filePath) {
        if (!content) return '<p class="placeholder">No content available.</p>';

        // JSON files: pretty-print
        if (filePath && filePath.endsWith('.json')) {
            try {
                var parsed = JSON.parse(content);
                return '<pre><code>' + escapeHtml(JSON.stringify(parsed, null, 2)) + '</code></pre>';
            } catch (e) {
                return '<pre><code>' + escapeHtml(content) + '</code></pre>';
            }
        }

        // HTML files: show source
        if (filePath && filePath.endsWith('.html')) {
            return '<pre><code>' + escapeHtml(content) + '</code></pre>';
        }

        // Markdown files
        return renderMarkdown(content);
    }

    function setStatus(elemId, message, type) {
        var el = document.getElementById(elemId);
        if (!el) return;
        el.textContent = message;
        el.className = 'status-msg' + (type ? ' ' + type : '');
        if (type === 'success' || type === 'error') {
            setTimeout(function() {
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
            'pending': 'pending',
            'running': 'running…',
            'done': 'done',
            'error': 'error',
            'action': 'action needed'
        };
        el.textContent = labels[state] || state;
    }

    function setDiagramStage(diagId, state) {
        if (!diagId) return;
        var el = document.getElementById(diagId);
        if (!el) return;
        var stage = el.closest('.pipeline-stage');
        if (stage) {
            stage.className = 'pipeline-stage' + (state ? ' ' + state : '');
        }
        var labels = {
            'pending': 'Pending',
            'running': 'Running…',
            'done': 'Done ✓',
            'error': 'Error ✗',
            'active': 'Active'
        };
        el.textContent = labels[state] || state || 'Pending';
    }

    function setTableBadge(tbId, state) {
        if (!tbId) return;
        var el = document.getElementById(tbId);
        if (!el) return;
        el.className = 'table-badge ' + state;
        var labels = {
            'pending': 'pending',
            'running': 'running…',
            'done': 'done',
            'error': 'error'
        };
        el.textContent = labels[state] || state;
    }

    function updateTableSession(tsId, task) {
        if (!tsId) return;
        var el = document.getElementById(tsId);
        if (!el) return;
        if (task && task.sessionId) {
            var proxyUrl = getProxyUrl(task.sessionId);
            var label = task.status === 'RUNNING' ? '📡 Monitor' : '📋 View Log';
            el.innerHTML = '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener">' +
                label + '</a>';
        } else {
            el.textContent = '—';
        }
    }

    function updateSessionLinkContainer(containerId, task) {
        if (!containerId) return;
        var container = document.getElementById(containerId);
        if (!container) return;

        if (!task || !task.sessionId) {
            container.style.display = 'none';
            return;
        }

        var proxyUrl = getProxyUrl(task.sessionId);
        var status = task.status;

        if (status === 'RUNNING') {
            container.innerHTML =
                '<div class="session-monitor-link">' +
                '<span class="monitor-pulse">●</span>' +
                '<span>Processing… </span>' +
                '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '📡 Monitor Live Session (' + escapeHtml(task.sessionId.substring(0, 12)) + '…)</a>' +
                '</div>';
            container.style.display = 'block';
        } else if (status === 'COMPLETED') {
            container.innerHTML =
                '<div class="session-completed-link">' +
                '<span>✅ Completed — </span>' +
                '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '📋 View Session Log</a>' +
                '</div>';
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            container.innerHTML =
                '<div class="session-error-link">' +
                '<span>❌ Failed — </span>' +
                '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '🔍 View Error Log</a>' +
                '</div>';
            container.style.display = 'block';
        } else {
            container.style.display = 'none';
        }
    }

    // =========================================================================
    // Batch Log
    // =========================================================================
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

    // =========================================================================
    // Navigation
    // =========================================================================
    document.querySelectorAll('.nav-link').forEach(function(link) {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            var sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(function(l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function(s) { s.classList.remove('active'); });
            this.classList.add('active');
            var section = document.getElementById(sectionId);
            if (section) section.classList.add('active');
        });
    });

    // Results tabs
    document.querySelectorAll('.results-tab').forEach(function(tab) {
        tab.addEventListener('click', function() {
            document.querySelectorAll('.results-tab').forEach(function(t) { t.classList.remove('active'); });
            document.querySelectorAll('.tab-panel').forEach(function(p) { p.classList.remove('active'); });
            this.classList.add('active');
            var panel = document.getElementById(this.dataset.tab);
            if (panel) panel.classList.add('active');
        });
    });

    // =========================================================================
    // Save Input
    // =========================================================================
    document.getElementById('save-input').addEventListener('click', async function() {
        var content = document.getElementById('input-editor').value;
        if (!content.trim()) {
            setStatus('input-status', '✗ Please enter your vacation preferences first.', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('user_preferences.md', content);
            setStatus('input-status', '✓ Preferences saved', 'success');
            // Update diagram
            setDiagramStage('diagram-input', 'done');
        } catch (e) {
            setStatus('input-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // Save & Start Planning
    document.getElementById('save-and-run').addEventListener('click', async function() {
        var content = document.getElementById('input-editor').value;
        if (!content.trim()) {
            setStatus('input-status', '✗ Please enter your vacation preferences first.', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('user_preferences.md', content);
            setStatus('input-status', '✓ Saved — starting pipeline…', 'success');
            setDiagramStage('diagram-input', 'done');

            // Switch to pipeline tab
            document.querySelectorAll('.nav-link').forEach(function(l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function(s) { s.classList.remove('active'); });
            var pipelineLink = document.querySelector('[data-section="section-pipeline"]');
            if (pipelineLink) pipelineLink.classList.add('active');
            var pipelineSection = document.getElementById('section-pipeline');
            if (pipelineSection) pipelineSection.classList.add('active');

            // Trigger full pipeline
            document.getElementById('run-all').click();
        } catch (e) {
            setStatus('input-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // =========================================================================
    // View File Buttons
    // =========================================================================
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
                viewer.innerHTML = renderContent(content, filePath);
            }
            viewer.classList.add('visible');
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' +
                escapeHtml(e.message) + '</p>';
            viewer.classList.add('visible');
        }
    }

    document.querySelectorAll('.btn-view').forEach(function(btn) {
        btn.addEventListener('click', function() {
            viewFile(this.dataset.file, this.dataset.viewer);
        });
    });

    // Refresh buttons in results section
    document.querySelectorAll('.btn-refresh').forEach(function(btn) {
        btn.addEventListener('click', async function() {
            var filePath = this.dataset.file;
            var viewerId = this.dataset.viewer;
            var viewer = document.getElementById(viewerId);
            if (!viewer) return;

            try {
                var content = await readFile(filePath);
                if (content === null) {
                    viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                } else {
                    viewer.innerHTML = renderContent(content, filePath);
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' +
                    escapeHtml(e.message) + '</p>';
            }
        });
    });

    // =========================================================================
    // Run Operation Buttons
    // =========================================================================
    document.querySelectorAll('.btn-run').forEach(function(btn) {
        btn.addEventListener('click', async function() {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;
            var diagramId = this.dataset.diagram;

            setBadge(badgeId, 'running');
            if (diagramId) setDiagramStage(diagramId, 'running');
            this.disabled = true;
            startStatusPolling();

            try {
                var taskId = await runDocOp(opPath, outputPath);
                var cleanTaskId = taskId ? taskId.trim() : '';

                if (cleanTaskId && /^[a-zA-Z0-9_-]+$/.test(cleanTaskId)) {
                    // Update session link
                    var mapping = OUTPUT_MAP[outputPath];
                    if (mapping && mapping.sessionLink) {
                        updateSessionLinkContainer(mapping.sessionLink, {
                            status: 'RUNNING',
                            sessionId: cleanTaskId
                        });
                    }
                }

                await waitForTask(outputPath);
                setBadge(badgeId, 'done');
                if (diagramId) setDiagramStage(diagramId, 'done');

                // Auto-show result
                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        var content = await readFile(outputPath);
                        if (content) {
                            viewer.innerHTML = renderContent(content, outputPath);
                            viewer.classList.add('visible');
                        }
                    }
                }
            } catch (e) {
                setBadge(badgeId, 'error');
                if (diagramId) setDiagramStage(diagramId, 'error');
                alert('Operation failed: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    });

    // =========================================================================
    // Batch Execution
    // =========================================================================
    async function runSequential(steps) {
        for (var i = 0; i < steps.length; i++) {
            var step = steps[i];
            logBatch('Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');
            if (step.diagram) setDiagramStage(step.diagram, 'running');

            try {
                var taskId = await runDocOp(step.op, step.output);
                var cleanTaskId = taskId ? taskId.trim() : '';

                if (cleanTaskId && /^[a-zA-Z0-9_-]+$/.test(cleanTaskId)) {
                    var proxyUrl = getProxyUrl(cleanTaskId);
                    logBatchHtml('Session: <a href="' + proxyUrl + '" target="_blank" class="monitor-link">📡 Monitor (' +
                        cleanTaskId.substring(0, 12) + '…)</a>', 'info');

                    var mapping = OUTPUT_MAP[step.output];
                    if (mapping && mapping.sessionLink) {
                        updateSessionLinkContainer(mapping.sessionLink, {
                            status: 'RUNNING',
                            sessionId: cleanTaskId
                        });
                    }
                }

                await waitForTask(step.output);
                setBadge(step.badge, 'done');
                if (step.diagram) setDiagramStage(step.diagram, 'done');
                logBatch('✓ Completed: ' + step.label, 'success');

                // Auto-show result in viewer
                if (step.viewer) {
                    try {
                        var content = await readFile(step.output);
                        if (content) {
                            var viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                viewer.innerHTML = renderContent(content, step.output);
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }

                // Run optional post-step callback
                if (step.afterFn) await step.afterFn();

            } catch (e) {
                setBadge(step.badge, 'error');
                if (step.diagram) setDiagramStage(step.diagram, 'error');
                logBatch('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
    }

    // Run Entire Pipeline
    document.getElementById('run-all').addEventListener('click', async function() {
        // Auto-save input first
        var inputContent = document.getElementById('input-editor').value;
        if (!inputContent.trim()) {
            alert('Please enter your vacation preferences in the Input tab first.');
            return;
        }

        this.disabled = true;
        document.getElementById('run-to-analysis').disabled = true;
        document.getElementById('run-from-research').disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';

        try {
            // Save preferences
            await writeFile('user_preferences.md', inputContent);
            setDiagramStage('diagram-input', 'done');
            logBatch('✓ Preferences saved', 'success');

            await runSequential([
                {
                    op: 'ops/brainstorm_op.md',
                    output: 'brainstorm_output.md',
                    badge: 'badge-brainstorm',
                    diagram: 'diagram-brainstorm',
                    viewer: 'viewer-brainstorm',
                    label: 'Step 1: Brainstorm Vacation Concepts'
                },
                {
                    op: 'ops/analysis_op.md',
                    output: 'analysis_output.md',
                    badge: 'badge-analysis',
                    diagram: 'diagram-analysis',
                    viewer: 'viewer-analysis',
                    label: 'Step 2: Multi-Perspective Analysis'
                },
                {
                    op: 'ops/crawler_op.md',
                    output: 'research.md',
                    badge: 'badge-crawler',
                    diagram: 'diagram-crawler',
                    viewer: 'viewer-crawler',
                    label: 'Step 3: Research & Data Gathering'
                },
                {
                    op: 'ops/plan_op.md',
                    output: 'itinerary.md',
                    badge: 'badge-plan',
                    diagram: 'diagram-plan',
                    viewer: 'viewer-plan',
                    label: 'Step 4: Itinerary Planning'
                },
                {
                    op: 'ops/generate_op.md',
                    output: 'vacation_plan.html',
                    badge: 'badge-generate',
                    diagram: 'diagram-generate',
                    viewer: 'viewer-generate',
                    label: 'Step 5: Generate Vacation Plan UI'
                }
            ]);

            logBatch('🎉 Pipeline complete! Your vacation plan is ready.', 'success');

            // Auto-load results
            loadAllResults();

        } catch (e) {
            logBatch('Pipeline stopped due to error: ' + e.message, 'error');
        } finally {
            this.disabled = false;
            document.getElementById('run-to-analysis').disabled = false;
            document.getElementById('run-from-research').disabled = false;
        }
    });

    // Run Through Analysis (Steps 1-2)
    document.getElementById('run-to-analysis').addEventListener('click', async function() {
        var inputContent = document.getElementById('input-editor').value;
        if (!inputContent.trim()) {
            alert('Please enter your vacation preferences in the Input tab first.');
            return;
        }

        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';

        try {
            await writeFile('user_preferences.md', inputContent);
            setDiagramStage('diagram-input', 'done');
            logBatch('✓ Preferences saved', 'success');

            await runSequential([
                {
                    op: 'ops/brainstorm_op.md',
                    output: 'brainstorm_output.md',
                    badge: 'badge-brainstorm',
                    diagram: 'diagram-brainstorm',
                    viewer: 'viewer-brainstorm',
                    label: 'Step 1: Brainstorm Vacation Concepts'
                },
                {
                    op: 'ops/analysis_op.md',
                    output: 'analysis_output.md',
                    badge: 'badge-analysis',
                    diagram: 'diagram-analysis',
                    viewer: 'viewer-analysis',
                    label: 'Step 2: Multi-Perspective Analysis'
                }
            ]);

            logBatch('✓ Analysis complete. Review results before continuing.', 'success');
        } catch (e) {
            logBatch('Pipeline stopped: ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // Run From Research (Steps 3-5)
    document.getElementById('run-from-research').addEventListener('click', async function() {
        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';

        try {
            // Check prerequisites
            var brainstormExists = await fileExists('brainstorm_output.md');
            var analysisExists = await fileExists('analysis_output.md');
            if (!brainstormExists || !analysisExists) {
                logBatch('✗ Prerequisites missing. Run Steps 1-2 first.', 'error');
                alert('Please run the Brainstorm and Analysis steps first.');
                return;
            }

            await runSequential([
                {
                    op: 'ops/crawler_op.md',
                    output: 'research.md',
                    badge: 'badge-crawler',
                    diagram: 'diagram-crawler',
                    viewer: 'viewer-crawler',
                    label: 'Step 3: Research & Data Gathering'
                },
                {
                    op: 'ops/plan_op.md',
                    output: 'itinerary.md',
                    badge: 'badge-plan',
                    diagram: 'diagram-plan',
                    viewer: 'viewer-plan',
                    label: 'Step 4: Itinerary Planning'
                },
                {
                    op: 'ops/generate_op.md',
                    output: 'vacation_plan.html',
                    badge: 'badge-generate',
                    diagram: 'diagram-generate',
                    viewer: 'viewer-generate',
                    label: 'Step 5: Generate Vacation Plan UI'
                }
            ]);

            logBatch('🎉 Pipeline complete! Your vacation plan is ready.', 'success');
            loadAllResults();
        } catch (e) {
            logBatch('Pipeline stopped: ' + e.message, 'error');

         } finally {
             this.disabled = false;
         }
     });

     // =========================================================================
     // Preview Buttons
     // =========================================================================
     document.getElementById('btn-preview-plan').addEventListener('click', function() {
         var url = basePath + '/vacation_plan.html';
         window.open(url, '_blank');
     });

     document.getElementById('btn-load-preview').addEventListener('click', async function() {
         var container = document.getElementById('preview-container');
         if (!container) return;
         try {
             var exists = await fileExists('vacation_plan.html');
             if (!exists) {
                 container.innerHTML = '<p class="placeholder">File not found. Generate the vacation plan UI first.</p>';
                 return;
             }
             var url = basePath + '/vacation_plan.html';
             container.innerHTML = '<iframe src="' + escapeHtml(url) + '" ' +
                 'style="width:100%;height:700px;border:1px solid var(--color-border);border-radius:8px;" ' +
                 'sandbox="allow-scripts allow-same-origin"></iframe>';
         } catch (e) {
             container.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' +
                 escapeHtml(e.message) + '</p>';
         }
     });

     document.getElementById('btn-open-preview-tab').addEventListener('click', function() {
         var url = basePath + '/vacation_plan.html';
         window.open(url, '_blank');
     });

     // =========================================================================
     // Results Section — Load All Results
     // =========================================================================
     async function loadAllResults() {
         var resultFiles = [
             { file: 'brainstorm_output.md', viewer: 'result-brainstorm' },
             { file: 'analysis_output.md', viewer: 'result-analysis' },
             { file: 'research.md', viewer: 'result-data' },
             { file: 'itinerary.md', viewer: 'result-itinerary' }
         ];

         for (var i = 0; i < resultFiles.length; i++) {
             var rf = resultFiles[i];
             try {
                 var content = await readFile(rf.file);
                 var viewer = document.getElementById(rf.viewer);
                 if (viewer && content !== null) {
                     viewer.innerHTML = renderContent(content, rf.file);
                 }
             } catch (e) {
                 // Non-critical — leave placeholder
             }
         }
     }

     // =========================================================================
     // Dashboard
     // =========================================================================
     document.getElementById('btn-generate-dashboard').addEventListener('click', async function() {
         this.disabled = true;
         startStatusPolling();

         try {
             logBatch('Generating dashboard insights…', 'info');

             var taskId = await runDocOp('ops/dashboard_op.md', 'continuous_outputs/dashboard_insights.json');
             var cleanTaskId = taskId ? taskId.trim() : '';

             if (cleanTaskId && /^[a-zA-Z0-9_-]+$/.test(cleanTaskId)) {
                 logBatchHtml('Dashboard session: <a href="' + getProxyUrl(cleanTaskId) +
                     '" target="_blank" class="monitor-link">📡 Monitor (' +
                     cleanTaskId.substring(0, 12) + '…)</a>', 'info');
             }

             await waitForTask('continuous_outputs/dashboard_insights.json');
             logBatch('✓ Dashboard insights generated', 'success');

             await loadDashboardData();
         } catch (e) {
             logBatch('✗ Dashboard generation failed: ' + e.message, 'error');
             alert('Dashboard generation failed: ' + e.message);
         } finally {
             this.disabled = false;
         }
     });

     document.getElementById('btn-refresh-dashboard').addEventListener('click', async function() {
         await loadDashboardData();
     });

     async function loadDashboardData() {
         try {
             var content = await readFile('continuous_outputs/dashboard_insights.json');
             if (content === null) {
                 return;
             }

             var data;
             try {
                 data = JSON.parse(content);
             } catch (e) {
                 var viewer = document.getElementById('result-dashboard');
                 if (viewer) {
                     viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                 }
                 return;
             }

             // Update metric cards
             if (data.summary) {
                 var costEl = document.getElementById('metric-cost-value');
                 if (costEl && data.summary.cost_per_person != null) {
                     costEl.textContent = '$' + data.summary.cost_per_person;
                 }
             }

             if (data.perspectives) {
                 var expEl = document.getElementById('metric-experience-value');
                 if (expEl && data.perspectives.experience_maximizer &&
                     data.perspectives.experience_maximizer.experience_quality_score != null) {
                     expEl.textContent = data.perspectives.experience_maximizer.experience_quality_score + '/5';
                 }

                 var feasEl = document.getElementById('metric-feasibility-value');
                 if (feasEl && data.perspectives.logistics_optimizer &&
                     data.perspectives.logistics_optimizer.feasibility_score != null) {
                     feasEl.textContent = data.perspectives.logistics_optimizer.feasibility_score + '/5';
                 }
             }

             if (data.confidence_scores) {
                 var confEl = document.getElementById('metric-confidence-value');
                 if (confEl && data.confidence_scores.overall_confidence != null) {
                     confEl.textContent = data.confidence_scores.overall_confidence + '/5';
                 }
             }

             // Render full JSON in dashboard viewer
             var dashViewer = document.getElementById('result-dashboard');
             if (dashViewer) {
                 // Try to render a nice summary, fall back to JSON
                 var html = '';

                 if (data.vacation_concept) {
                     html += '<h3>🌴 ' + escapeHtml(data.vacation_concept) + '</h3>';
                 }
                 if (data.destination) {
                     html += '<p><strong>Destination:</strong> ' + escapeHtml(data.destination) + '</p>';
                 }
                 if (data.summary && data.summary.overall_recommendation) {
                     html += '<p>' + escapeHtml(data.summary.overall_recommendation) + '</p>';
                 }

                 // Recommendations
                 if (data.recommendations && data.recommendations.next_steps &&
                     data.recommendations.next_steps.length > 0) {
                     html += '<h4>📋 Next Steps</h4><ol>';
                     for (var i = 0; i < data.recommendations.next_steps.length; i++) {
                         var step = data.recommendations.next_steps[i];
                         html += '<li><strong>' + escapeHtml(step.action) + '</strong>';
                         if (step.priority) {
                             html += ' <span class="meta-tag">' + escapeHtml(step.priority) + '</span>';
                         }
                         if (step.reason) {
                             html += '<br><small>' + escapeHtml(step.reason) + '</small>';
                         }
                         html += '</li>';
                     }
                     html += '</ol>';
                 }

                 // Concerns
                 if (data.recommendations && data.recommendations.concerns_to_address &&
                     data.recommendations.concerns_to_address.length > 0) {
                     html += '<h4>⚠️ Concerns</h4><ul>';
                     for (var j = 0; j < data.recommendations.concerns_to_address.length; j++) {
                         var concern = data.recommendations.concerns_to_address[j];
                         html += '<li><strong>' + escapeHtml(concern.concern) + '</strong>';
                         if (concern.recommendation) {
                             html += ' — ' + escapeHtml(concern.recommendation);
                         }
                         html += '</li>';
                     }
                     html += '</ul>';
                 }

                 // Data freshness
                 if (data.data_freshness) {
                     html += '<h4>📡 Data Freshness</h4>';
                     if (data.data_freshness.stale_data_warnings &&
                         data.data_freshness.stale_data_warnings.length > 0) {
                         html += '<ul>';
                         for (var k = 0; k < data.data_freshness.stale_data_warnings.length; k++) {
                             var warning = data.data_freshness.stale_data_warnings[k];
                             html += '<li>⚠️ ' + escapeHtml(warning.data_point) +
                                 ' (' + warning.age_days + ' days old)</li>';
                         }
                         html += '</ul>';
                     } else {
                         html += '<p>✅ All data is current.</p>';
                     }
                 }

                 // Full JSON collapsible
                 html += '<details style="margin-top:1rem;"><summary>📄 View Raw JSON</summary>';
                 html += '<pre><code>' + escapeHtml(JSON.stringify(data, null, 2)) + '</code></pre>';
                 html += '</details>';

                 dashViewer.innerHTML = html;
             }

         } catch (e) {
             console.warn('Could not load dashboard data:', e);
         }
     }

     // =========================================================================
     // Check Existing Files on Load
     // =========================================================================
     async function checkExistingFiles() {
         // Check docops.status.json for running/completed tasks
         var statusData = await fetchDocopsStatus();
         var anyRunning = false;

         if (statusData && statusData.tasks) {
             for (var target in OUTPUT_MAP) {
                 if (!OUTPUT_MAP.hasOwnProperty(target)) continue;
                 var mapping = OUTPUT_MAP[target];
                 var task = getTaskStatus(statusData, target);

                 if (task) {
                     if (task.status === 'RUNNING') {
                         anyRunning = true;
                         setBadge(mapping.badge, 'running');
                         setDiagramStage(mapping.diagram, 'running');
                         setTableBadge(mapping.tableBadge, 'running');
                         updateTableSession(mapping.tableSession, task);
                         updateSessionLinkContainer(mapping.sessionLink, task);
                     } else if (task.status === 'COMPLETED') {
                         setBadge(mapping.badge, 'done');
                         setDiagramStage(mapping.diagram, 'done');
                         setTableBadge(mapping.tableBadge, 'done');
                         updateTableSession(mapping.tableSession, task);
                         updateSessionLinkContainer(mapping.sessionLink, task);
                     } else if (task.status === 'ERROR' || task.status === 'FAILED') {
                         setBadge(mapping.badge, 'error');
                         setDiagramStage(mapping.diagram, 'error');
                         setTableBadge(mapping.tableBadge, 'error');
                         updateTableSession(mapping.tableSession, task);
                         updateSessionLinkContainer(mapping.sessionLink, task);
                     }
                 }
             }
         }

         // Fall back to file existence checks for files not tracked in status
         var fileChecks = [
             { file: 'brainstorm_output.md', badge: 'badge-brainstorm', diagram: 'diagram-brainstorm', tableBadge: 'table-badge-brainstorm' },
             { file: 'analysis_output.md', badge: 'badge-analysis', diagram: 'diagram-analysis', tableBadge: 'table-badge-analysis' },
             { file: 'research.md', badge: 'badge-crawler', diagram: 'diagram-crawler', tableBadge: 'table-badge-crawler' },
             { file: 'itinerary.md', badge: 'badge-plan', diagram: 'diagram-plan', tableBadge: 'table-badge-plan' },
             { file: 'vacation_plan.html', badge: 'badge-generate', diagram: 'diagram-generate', tableBadge: 'table-badge-generate' }
         ];

         for (var i = 0; i < fileChecks.length; i++) {
             var check = fileChecks[i];
             var badge = document.getElementById(check.badge);
             // Don't override running or done badges from status check
             if (badge && (badge.classList.contains('running') || badge.classList.contains('done'))) continue;

             try {
                 var content = await readFile(check.file);
                 if (content !== null && content.trim().length > 0) {
                     setBadge(check.badge, 'done');
                     setDiagramStage(check.diagram, 'done');
                     setTableBadge(check.tableBadge, 'done');
                 }
             } catch (e) {
                 // Leave as pending
             }
         }

         // Check user_preferences.md for input stage
         try {
             var prefs = await readFile('user_preferences.md');
             if (prefs !== null && prefs.trim().length > 0) {
                 setDiagramStage('diagram-input', 'done');
             }
         } catch (e) {
             // Leave as ready
         }

         if (anyRunning) {
             startStatusPolling();
         }
     }

     // =========================================================================
     // Load Initial Files
     // =========================================================================
     async function loadInitialFiles() {
         // Load user preferences into editor
         try {
             var content = await readFile('user_preferences.md');
             if (content !== null) {
                 document.getElementById('input-editor').value = content;
             }
         } catch (e) {
             console.warn('Could not load user_preferences.md:', e);
         }
     }

     // =========================================================================
     // Initialize
     // =========================================================================
     loadInitialFiles();
     checkExistingFiles();
     startStatusPolling();

})();