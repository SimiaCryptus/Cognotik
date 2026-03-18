
(function() {
    'use strict';

    // ══════════════════════════════════════════════════════════════
    // URL Parsing & Session Setup
    // ══════════════════════════════════════════════════════════════
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';

    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
    } else {
        // Fallback: try to find 'omega' in path for legacy URLs
        const omegaIdx = pathParts.indexOf('omega');
        if (omegaIdx >= 0) {
            basePath = pathParts.slice(0, omegaIdx + 1).join('/');
            sessionId = pathParts[omegaIdx - 1] || '';
        } else {
            console.warn('Could not determine session from URL path.');
            basePath = window.location.pathname.replace(/\/[^/]*$/, '');
        }
    }

    const proxyBase = '/proxy/';
    function getProxyUrl(id) { return proxyBase + '#' + id; }

    console.log('Omega initialized — basePath:', basePath, 'sessionId:', sessionId);

    // ══════════════════════════════════════════════════════════════
    // File I/O
    // ══════════════════════════════════════════════════════════════
    async function readFile(filePath) {
        try {
            const resp = await fetch(basePath + '/' + filePath);
            if (!resp.ok) {
                if (resp.status === 404) return null;
                throw new Error(`HTTP ${resp.status}`);
            }
            return await resp.text();
        } catch (e) {
            return null;
        }
    }

    async function writeFile(filePath, content) {
        const resp = await fetch(basePath + '/' + filePath, {
            method: 'PUT',
            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
            body: content
        });
        if (!resp.ok) throw new Error(`Failed to write ${filePath}: ${resp.status}`);
        return true;
    }

    async function listFiles(dirPath) {
        try {
            const resp = await fetch(basePath + '/' + dirPath + '/_files.json');
            if (!resp.ok) return [];
            const data = await resp.json();
            // Handle both array and {entries: [...]} formats
            if (Array.isArray(data)) return data;
            if (data.entries) return data.entries;
            return [];
        } catch (e) {
            return [];
        }
    }

    async function runDocOp(opPath, targetPath) {
        const url = '/docops?sessionId=' + encodeURIComponent(sessionId) +
                    '&doc=' + encodeURIComponent(opPath) +
                    '&target=' + encodeURIComponent(targetPath);
        const resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            const errText = await resp.text().catch(() => '');
            throw new Error('DocOps failed: ' + resp.status + ' ' + errText);
        }
        return await resp.text();
    }

    // ══════════════════════════════════════════════════════════════
    // Status Polling
    // ══════════════════════════════════════════════════════════════
    async function fetchDocopsStatus() {
        try {
            const resp = await fetch(basePath + '/docops.status.json');
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) {
            return null;
        }
    }

    // Flexible task lookup — handles different key formats
    function getTaskStatus(statusData, targetPath) {
        if (!statusData) return null;
        const tasks = statusData.tasks || statusData;

        // Exact match
        if (tasks[targetPath]) return tasks[targetPath];

        // Try with and without leading slash
        const alt = targetPath.startsWith('/') ? targetPath.slice(1) : '/' + targetPath;
        if (tasks[alt]) return tasks[alt];

        // Match by filename
        const filename = targetPath.split('/').pop();
        if (tasks[filename]) return tasks[filename];

        // Search all tasks for partial match
        for (const [key, task] of Object.entries(tasks)) {
            if (key.includes(targetPath) || targetPath.includes(key)) return task;
            if (task.target && (task.target === targetPath || task.target.includes(targetPath))) return task;
        }
        return null;
    }

    function normalizeStatus(taskInfo) {
        if (!taskInfo) return null;
        const s = (taskInfo.status || taskInfo.state || '').toUpperCase();
        if (s.includes('RUN') || s.includes('PEND')) return 'RUNNING';
        if (s.includes('COMPL') || s.includes('DONE')) return 'COMPLETED';
        if (s.includes('FAIL') || s.includes('ERR')) return 'ERROR';
        return s;
    }

    async function waitForTask(targetPath, maxWaitMs) {
        const maxWait = maxWaitMs || 600000;
        const interval = 2500;
        const startTime = Date.now();

        while (Date.now() - startTime < maxWait) {
            const statusData = await fetchDocopsStatus();
            const task = getTaskStatus(statusData, targetPath);
            const status = normalizeStatus(task);

            if (status === 'COMPLETED') return task;
            if (status === 'ERROR') throw new Error('Task failed: ' + targetPath);

            await new Promise(r => setTimeout(r, interval));
        }
        throw new Error('Task timed out: ' + targetPath);
    }

    // Step definitions for status mapping
    const STEPS = [
        { id: 'analyze',  output: 'analysis.md',              op: 'ops/analyze_op.md' },
        { id: 'design',   output: 'pipeline_design.md',       op: 'ops/design_pipeline_op.md' },
        { id: 'generate', output: 'generated_app/',            op: 'ops/generate_ops_op.md' },
        { id: 'ui',       output: 'generated_app/index.html',  op: 'ops/generate_ui_op.md' },
        { id: 'readme',   output: 'generated_app/README.md',   op: 'ops/generate_readme_op.md' },
        { id: 'review',   output: 'review.md',                op: 'ops/review_op.md' },
    ];

    let statusPollTimer = null;
    const STATUS_POLL_INTERVAL = 3000;

    function startStatusPolling() {
        if (statusPollTimer) return;
        statusPollTimer = setInterval(() => pollStatus(), STATUS_POLL_INTERVAL);
        pollStatus();
    }

    function stopStatusPolling() {
        if (statusPollTimer) {
            clearInterval(statusPollTimer);
            statusPollTimer = null;
        }
    }

    async function pollStatus() {
        const statusData = await fetchDocopsStatus();
        if (!statusData) return;

        let anyRunning = false;
        let anyError = false;
        let allDone = true;
        let anyDone = false;

        for (const step of STEPS) {
            const task = getTaskStatus(statusData, step.output);
            const status = normalizeStatus(task);

            if (status === 'RUNNING') {
                setBadge('badge-' + step.id, 'running');
                setStageState(step.id, 'active', 'Running…');
                anyRunning = true;
                allDone = false;
                if (task && task.sessionId) {
                    updateSessionLink(step.id, 'RUNNING', task.sessionId);
                }
            } else if (status === 'COMPLETED') {
                setBadge('badge-' + step.id, 'done');
                setStageState(step.id, 'complete', 'Done');
                anyDone = true;
                if (task && task.sessionId) {
                    updateSessionLink(step.id, 'COMPLETED', task.sessionId);
                }
            } else if (status === 'ERROR') {
                setBadge('badge-' + step.id, 'error');
                setStageState(step.id, 'error', 'Error');
                anyError = true;
                allDone = false;
                if (task && task.sessionId) {
                    updateSessionLink(step.id, 'ERROR', task.sessionId);
                }
            } else {
                allDone = false;
            }
        }

        // Update global status badge
        const badge = document.getElementById('globalStatus');
        if (anyRunning) {
            badge.className = 'status-badge status-running';
            badge.textContent = 'Running';
        } else if (anyError) {
            badge.className = 'status-badge status-error';
            badge.textContent = 'Error';
        } else if (allDone && anyDone) {
            badge.className = 'status-badge status-complete';
            badge.textContent = 'Complete';
            document.getElementById('btnOpenApp').disabled = false;
        } else if (anyDone) {
            badge.className = 'status-badge status-complete';
            badge.textContent = 'Partial';
        } else {
            badge.className = 'status-badge status-idle';
            badge.textContent = 'Idle';
        }
    }

    // ══════════════════════════════════════════════════════════════
    // UI Helpers
    // ══════════════════════════════════════════════════════════════
    function renderMarkdown(md) {
        if (typeof marked !== 'undefined') {
            return typeof marked.parse === 'function' ? marked.parse(md) : marked(md);
        }
        return '<pre>' + escapeHtml(md) + '</pre>';
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function setStatus(elemId, message, type) {
        const el = document.getElementById(elemId);
        if (!el) return;
        el.textContent = message;
        el.className = 'status-msg' + (type ? ' ' + type : '');
        if (type === 'success' || type === 'error') {
            setTimeout(() => { el.textContent = ''; el.className = 'status-msg'; }, 5000);
        }
    }

    function setBadge(badgeId, state) {
        const el = document.getElementById(badgeId);
        if (!el) return;
        el.className = 'step-badge ' + state;
        const labels = { pending: 'pending', running: 'running…', done: 'done', error: 'error' };
        el.textContent = labels[state] || state;
    }

    function setStageState(stageId, state, statusText) {
        const stage = document.getElementById('stage-' + stageId);
        if (stage) {
            stage.className = 'pipeline-stage' + (state ? ' ' + state : '');
        }
        const statusEl = document.getElementById('stage-' + stageId + '-status');
        if (statusEl) {
            statusEl.textContent = statusText || '';
        }
    }

    function updateSessionLink(stepId, status, taskSessionId) {
        const container = document.getElementById('session-link-' + stepId);
        if (!container) return;

        if (status === 'RUNNING' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-monitor-link">' +
                    '<span class="monitor-pulse">●</span>' +
                    '<span>Processing… </span>' +
                    '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                        '📡 Monitor Live Session (' + escapeHtml(taskSessionId.substring(0, 12)) + '…)' +
                    '</a>' +
                '</div>';
        } else if (status === 'COMPLETED' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-completed-link">' +
                    '<span>✅ Completed — </span>' +
                    '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                        '📋 View Session Log' +
                    '</a>' +
                '</div>';
        } else if (status === 'ERROR' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-error-link">' +
                    '<span>❌ Failed — </span>' +
                    '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                        '🔍 View Error Log' +
                    '</a>' +
                '</div>';
        } else {
            container.innerHTML = '';
        }
    }

    function showToast(message, type) {
        const toast = document.createElement('div');
        toast.className = 'toast toast-' + (type || 'info');
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 4000);
    }

    // ══════════════════════════════════════════════════════════════
    // Batch Log
    // ══════════════════════════════════════════════════════════════
    const batchLog = document.getElementById('batch-log');

    function logBatch(message, type) {
        batchLog.classList.add('visible');
        const entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        entry.textContent = '[' + new Date().toLocaleTimeString() + '] ' + message;
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }

    function logBatchHtml(html, type) {
        batchLog.classList.add('visible');
        const entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        entry.innerHTML = '[' + new Date().toLocaleTimeString() + '] ' + html;
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }

    // ══════════════════════════════════════════════════════════════
    // Navigation
    // ══════════════════════════════════════════════════════════════
    document.querySelectorAll('.tab-nav .nav-link').forEach(function(link) {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const sectionId = this.dataset.section;
            document.querySelectorAll('.tab-nav .nav-link').forEach(function(l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function(s) { s.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(sectionId).classList.add('active');

            // Refresh data when switching to results or files tabs
            if (sectionId === 'section-results') refreshCurrentResult();
            if (sectionId === 'section-files') refreshFileTree();
        });
    });

    // ══════════════════════════════════════════════════════════════
    // Save Idea
    // ══════════════════════════════════════════════════════════════
    async function saveIdea() {
        const content = document.getElementById('ideaEditor').value;
        if (!content.trim()) {
            setStatus('idea-status', '✗ Please enter an app idea first', 'error');
            return false;
        }
        try {
            await writeFile('idea.md', content);
            setStatus('idea-status', '✓ Saved', 'success');
            setStageState('input', 'complete', 'Saved');
            return true;
        } catch (e) {
            setStatus('idea-status', '✗ ' + e.message, 'error');
            return false;
        }
    }

    document.getElementById('btnSaveIdea').addEventListener('click', saveIdea);

    // ══════════════════════════════════════════════════════════════
    // Individual Step Run Buttons
    // ══════════════════════════════════════════════════════════════
    document.querySelectorAll('.btn-run').forEach(function(btn) {
        btn.addEventListener('click', async function() {
            const opFile = this.dataset.op;
            const outputPath = this.dataset.output;
            const badgeId = this.dataset.badge;
            const viewerId = this.dataset.viewer;
            const stageId = this.dataset.stage;

            // Auto-save idea if running analyze step
            if (opFile === 'analyze_op.md') {
                const saved = await saveIdea();
                if (!saved) return;
            }

            setBadge(badgeId, 'running');
            setStageState(stageId, 'active', 'Running…');
            this.disabled = true;
            startStatusPolling();

            try {
                const taskId = await runDocOp('ops/' + opFile, outputPath);
                const cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9_-]+$/.test(cleanTaskId)) {
                    updateSessionLink(stageId, 'RUNNING', cleanTaskId);
                }

                await waitForTask(outputPath);
                setBadge(badgeId, 'done');
                setStageState(stageId, 'complete', 'Done');
                showToast('Step completed: ' + stageId, 'success');

                // Auto-show result
                if (viewerId && !outputPath.endsWith('/')) {
                    const content = await readFile(outputPath);
                    if (content) {
                        const viewer = document.getElementById(viewerId);
                        if (viewer) {
                            viewer.innerHTML = '<div class="rendered-md">' + renderMarkdown(content) + '</div>';
                            viewer.classList.add('visible');
                        }
                    }
                }

                // Enable Open App button if UI was generated
                if (stageId === 'ui' || stageId === 'generate') {
                    document.getElementById('btnOpenApp').disabled = false;
                }
            } catch (e) {
                setBadge(badgeId, 'error');
                setStageState(stageId, 'error', 'Error');
                showToast('Step failed: ' + e.message, 'error');
            } finally {
                this.disabled = false;
            }
        });
    });

    // ══════════════════════════════════════════════════════════════
    // View File Buttons
    // ══════════════════════════════════════════════════════════════
    document.querySelectorAll('.btn-view').forEach(function(btn) {
        btn.addEventListener('click', async function() {
            const filePath = this.dataset.file;
            const viewerId = this.dataset.viewer;
            const isDir = this.dataset.isDir === 'true';
            const viewer = document.getElementById(viewerId);
            if (!viewer) return;

            if (viewer.classList.contains('visible')) {
                viewer.classList.remove('visible');
                return;
            }

            try {
                if (isDir) {
                    // List directory contents
                    const dirPath = filePath.replace('/_files.json', '');
                    const files = await listFiles(dirPath);
                    if (files.length === 0) {
                        viewer.innerHTML = '<p class="placeholder">No files generated yet. Run the operation first.</p>';
                    } else {
                        let html = '<div class="file-tree">';
                        for (const f of files) {
                            const name = typeof f === 'string' ? f : (f.name || f.path || JSON.stringify(f));
                            html += '<div class="file-tree-item">📄 ' + escapeHtml(name) + '</div>';
                        }
                        html += '</div>';
                        viewer.innerHTML = html;
                    }
                } else {
                    const content = await readFile(filePath);
                    if (content === null) {
                        viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                    } else if (filePath.endsWith('.html')) {
                        viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                    } else if (filePath.endsWith('.json')) {
                        viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                    } else {
                        viewer.innerHTML = '<div class="rendered-md">' + renderMarkdown(content) + '</div>';
                    }
                }
                viewer.classList.add('visible');
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--error);">Error: ' + escapeHtml(e.message) + '</p>';
                viewer.classList.add('visible');
            }
        });
    });

    // ══════════════════════════════════════════════════════════════
    // Batch Pipeline Execution
    // ══════════════════════════════════════════════════════════════
    async function runFullPipeline() {
        const saved = await saveIdea();
        if (!saved) return;

        const btnRunAll = document.getElementById('btnRunAll');
        btnRunAll.disabled = true;
        batchLog.innerHTML = '';
        startStatusPolling();

        const steps = [
            { op: 'ops/analyze_op.md',          output: 'analysis.md',              badge: 'badge-analyze',  stage: 'analyze',  viewer: 'viewer-analyze',  label: 'Analyze Idea' },
            { op: 'ops/design_pipeline_op.md',   output: 'pipeline_design.md',       badge: 'badge-design',   stage: 'design',   viewer: 'viewer-design',   label: 'Design Pipeline' },
            { op: 'ops/generate_ops_op.md',      output: 'generated_app/',            badge: 'badge-generate', stage: 'generate', viewer: null,              label: 'Generate Op Files' },
            { op: 'ops/generate_ui_op.md',       output: 'generated_app/index.html',  badge: 'badge-ui',       stage: 'ui',       viewer: 'viewer-ui',       label: 'Generate UI' },
            { op: 'ops/generate_readme_op.md',   output: 'generated_app/README.md',   badge: 'badge-readme',   stage: 'readme',   viewer: 'viewer-readme',   label: 'Generate Documentation' },
            { op: 'ops/review_op.md',            output: 'review.md',                badge: 'badge-review',   stage: 'review',   viewer: 'viewer-review',   label: 'Quality Review' },
        ];

        try {
            for (const step of steps) {
                logBatch('Starting: ' + step.label, 'info');
                setBadge(step.badge, 'running');
                setStageState(step.stage, 'active', 'Running…');

                try {
                    const taskId = await runDocOp(step.op, step.output);
                    const cleanTaskId = taskId ? taskId.trim() : '';

                    if (cleanTaskId && /^[a-zA-Z0-9_-]+$/.test(cleanTaskId)) {
                        const proxyUrl = getProxyUrl(cleanTaskId);
                        logBatchHtml('Session: <a href="' + proxyUrl + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId.substring(0, 12) + '…)</a>', 'info');
                        updateSessionLink(step.stage, 'RUNNING', cleanTaskId);
                    }

                    await waitForTask(step.output);
                    setBadge(step.badge, 'done');
                    setStageState(step.stage, 'complete', 'Done');
                    logBatch('✓ Completed: ' + step.label, 'success');

                    // Auto-show result in viewer
                    if (step.viewer && !step.output.endsWith('/')) {
                        try {
                            const content = await readFile(step.output);
                            if (content) {
                                const viewer = document.getElementById(step.viewer);
                                if (viewer) {
                                    if (step.output.endsWith('.html')) {
                                        viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                                    } else {
                                        viewer.innerHTML = '<div class="rendered-md">' + renderMarkdown(content) + '</div>';
                                    }
                                    viewer.classList.add('visible');
                                }
                            }
                        } catch (e) { /* non-critical */ }
                    }
                } catch (e) {
                    setBadge(step.badge, 'error');
                    setStageState(step.stage, 'error', 'Error');
                    logBatch('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                    throw e;
                }
            }

            logBatch('🎉 Pipeline complete! Your app has been generated.', 'success');
            showToast('App generated successfully!', 'success');
            document.getElementById('btnOpenApp').disabled = false;

            // Refresh file tree
            refreshFileTree();
        } catch (e) {
            logBatch('Pipeline stopped due to error. You can retry individual steps.', 'error');
            showToast('Pipeline failed: ' + e.message, 'error');
        } finally {
            btnRunAll.disabled = false;
        }
    }

    document.getElementById('btnRunAll').addEventListener('click', runFullPipeline);
    document.getElementById('btnSaveAndRun').addEventListener('click', runFullPipeline);

    document.getElementById('btnClearLog').addEventListener('click', function() {
        batchLog.innerHTML = '';
        batchLog.classList.remove('visible');
    });

    // ══════════════════════════════════════════════════════════════
    // Results Section
    // ══════════════════════════════════════════════════════════════
    let currentResult = 'analysis';

    const RESULT_MAP = {
        'analysis':   { file: 'analysis.md',         title: '📊 Analysis',        type: 'md' },
        'design':     { file: 'pipeline_design.md',  title: '🏗️ Pipeline Design', type: 'md' },
        'readme':     { file: 'generated_app/README.md', title: '📖 README',      type: 'md' },
        'review':     { file: 'review.md',           title: '🔍 Review',          type: 'md' },
        'ui-preview': { file: 'generated_app/index.html', title: '🎨 UI Preview', type: 'html' },
    };

    window.showResult = async function(resultId) {
        currentResult = resultId;

        // Update sidebar active state
        document.querySelectorAll('#resultsSidebar .results-sidebar-item').forEach(function(item) {
            item.classList.toggle('active', item.dataset.result === resultId);
        });

        await refreshCurrentResult();
    };

    window.refreshCurrentResult = async function() {
        const config = RESULT_MAP[currentResult];
        if (!config) return;

        document.getElementById('resultsTitle').textContent = config.title;
        const body = document.getElementById('resultsBody');

        try {
            const content = await readFile(config.file);
            if (content === null) {
                body.innerHTML =
                    '<div class="empty-state">' +
                        '<div class="icon">📄</div>' +
                        '<div class="title">Not yet generated</div>' +
                        '<div class="subtitle">Run the pipeline to generate this document</div>' +
                    '</div>';
                return;
            }

            if (config.type === 'html') {
                body.innerHTML =
                    '<div style="margin-bottom: 12px;">' +
                        '<button class="btn btn-sm btn-success" onclick="openGeneratedApp()">🌐 Open in New Tab</button>' +
                        '<span style="margin-left: 10px; color: var(--text-dim); font-size: 0.8rem;">Source code preview below</span>' +
                    '</div>' +
                    '<pre style="background: var(--bg); padding: 14px; border-radius: var(--radius); border: 1px solid var(--border); overflow-x: auto; font-size: 0.8rem; max-height: 600px; overflow-y: auto;"><code>' +
                    escapeHtml(content) + '</code></pre>';
            } else {
                body.innerHTML = '<div class="rendered-md">' + renderMarkdown(content) + '</div>';
            }
        } catch (e) {
            body.innerHTML =
                '<div class="empty-state">' +
                    '<div class="icon">⚠️</div>' +
                    '<div class="title">Error loading file</div>' +
                    '<div class="subtitle">' + escapeHtml(e.message) + '</div>' +
                '</div>';
        }
    };

    // ══════════════════════════════════════════════════════════════
    // Files Section
    // ══════════════════════════════════════════════════════════════
    let currentPreviewFile = null;

    window.refreshFileTree = async function() {
        const sidebar = document.getElementById('filesSidebar');

        // Load root generated_app files
        const rootFiles = await listFiles('generated_app');
        const opsFiles = await listFiles('generated_app/ops');

        if (rootFiles.length === 0 && opsFiles.length === 0) {
            sidebar.innerHTML =
                '<div class="empty-state" style="padding: 30px 10px;">' +
                    '<div class="icon">📁</div>' +
                    '<div class="subtitle">No generated files yet</div>' +
                '</div>';
            return;
        }

        let html = '';

        // Root files
        if (rootFiles.length > 0) {
            html += '<div class="file-tree-section">';
            html += '<div class="file-tree-section-label">📁 generated_app/</div>';
            for (const f of rootFiles) {
                const name = typeof f === 'string' ? f : (f.name || '');
                const type = typeof f === 'object' ? (f.type || 'file') : 'file';
                if (type === 'directory') continue; // Skip dirs, we handle ops/ separately
                const icon = name.endsWith('.html') ? '🌐' : name.endsWith('.json') ? '⚙️' : name.endsWith('.md') ? '📄' : '📎';
                html += '<div class="results-sidebar-item" onclick="previewFile(\'generated_app/' + escapeHtml(name) + '\')" data-filepath="generated_app/' + escapeHtml(name) + '">' +
                        icon + ' ' + escapeHtml(name) + '</div>';
            }
            html += '</div>';
        }

        // Ops files
        if (opsFiles.length > 0) {
            html += '<div class="file-tree-section">';
            html += '<div class="file-tree-section-label">📁 generated_app/ops/</div>';
            for (const f of opsFiles) {
                const name = typeof f === 'string' ? f : (f.name || '');
                const type = typeof f === 'object' ? (f.type || 'file') : 'file';
                if (type === 'directory') continue;
                const icon = name.endsWith('.md') ? '⚙️' : name.endsWith('.json') ? '🔧' : '📎';
                html += '<div class="results-sidebar-item" onclick="previewFile(\'generated_app/ops/' + escapeHtml(name) + '\')" data-filepath="generated_app/ops/' + escapeHtml(name) + '">' +
                        icon + ' ' + escapeHtml(name) + '</div>';
            }
            html += '</div>';
        }

        sidebar.innerHTML = html;
    };

    window.previewFile = async function(filePath) {
        currentPreviewFile = filePath;
        const fileName = filePath.split('/').pop();

        // Update sidebar active state
        document.querySelectorAll('#filesSidebar .results-sidebar-item').forEach(function(item) {
            item.classList.toggle('active', item.dataset.filepath === filePath);
        });

        document.getElementById('filePreviewTitle').textContent = '📄 ' + filePath;
        const body = document.getElementById('filePreviewBody');

        try {
            const content = await readFile(filePath);
            if (content === null) {
                body.innerHTML = '<p class="placeholder">File not found.</p>';
                return;
            }

            if (fileName.endsWith('.html')) {
                body.innerHTML = '<pre style="background: var(--bg); padding: 14px; border-radius: var(--radius); border: 1px solid var(--border); overflow-x: auto; font-size: 0.8rem;"><code>' + escapeHtml(content) + '</code></pre>';
            } else if (fileName.endsWith('.json')) {
                let formatted = content;
                try { formatted = JSON.stringify(JSON.parse(content), null, 2); } catch(e) {}
                body.innerHTML = '<pre style="background: var(--bg); padding: 14px; border-radius: var(--radius); border: 1px solid var(--border); overflow-x: auto; font-size: 0.8rem;"><code>' + escapeHtml(formatted) + '</code></pre>';
            } else {
                // Show both raw and rendered for markdown
                body.innerHTML =
                    '<div class="rendered-md">' + renderMarkdown(content) + '</div>' +
                    '<details style="margin-top: 16px;">' +
                        '<summary style="cursor: pointer; color: var(--text-dim); font-size: 0.82rem;">📝 View Raw Source</summary>' +
                        '<pre style="background: var(--bg); padding: 14px; border-radius: var(--radius); border: 1px solid var(--border); overflow-x: auto; font-size: 0.8rem; margin-top: 8px;"><code>' + escapeHtml(content) + '</code></pre>' +
                    '</details>';
            }
        } catch (e) {
            body.innerHTML = '<p class="placeholder" style="color: var(--error);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    };

    // ══════════════════════════════════════════════════════════════
    // Open Generated App
    // ══════════════════════════════════════════════════════════════
    window.openGeneratedApp = function() {
        window.open(basePath + '/generated_app/index.html', '_blank');
    };

    // ══════════════════════════════════════════════════════════════
    // Example Ideas
    // ══════════════════════════════════════════════════════════════
    const EXAMPLES = {
        pitch: '# App Idea: Pitch Deck Generator\n\n## Purpose\nTakes a business idea and produces a complete pitch deck outline with competitive analysis and executive summary.\n\n## User Input\nA description of the business idea, target market, and key differentiators.\n\n## Desired Output\n- Structured pitch deck outline (10-12 slides)\n- Competitive landscape analysis (via web research)\n- One-page executive summary\n- Key metrics and market sizing estimates\n\n## Pipeline Steps\n1. Brainstorm key value propositions and angles\n2. Research competitors and market landscape (CrawlerAgent)\n3. Design pitch deck structure\n4. Generate each slide\'s content\n5. Create executive summary\n\n## Special Requirements\n- Use CrawlerAgent for competitive research\n- Human-in-the-loop checkpoint after brainstorming to select best angle',

        blog: '# App Idea: Blog Post Pipeline\n\n## Purpose\nTransforms a topic and key points into a polished, SEO-optimized blog post with images suggestions and social media snippets.\n\n## User Input\n- Blog topic\n- Target audience\n- Key points to cover\n- Desired tone (professional, casual, technical)\n\n## Desired Output\n- Full blog post (1500-2000 words) in markdown\n- SEO metadata (title, description, keywords)\n- 3 social media snippets (Twitter, LinkedIn, Instagram)\n- Image suggestions with alt text\n\n## Pipeline Steps\n1. Brainstorm angles and outline\n2. Research trending content on the topic\n3. Write first draft\n4. Review and improve\n5. Generate SEO metadata and social snippets',

        research: '# App Idea: Research Assistant\n\n## Purpose\nHelps researchers explore a topic by gathering sources, summarizing findings, identifying gaps, and producing a literature review outline.\n\n## User Input\n- Research question or topic\n- Scope constraints (time period, field, etc.)\n- Known relevant papers or sources\n\n## Desired Output\n- Curated list of relevant sources with summaries\n- Thematic analysis of findings\n- Gap analysis identifying under-explored areas\n- Literature review outline\n- Suggested next steps\n\n## Special Requirements\n- Use CrawlerAgent to find and summarize web sources\n- Multiple rounds of research refinement\n- Human-in-the-loop after initial source gathering to guide focus',

        code: '# App Idea: Code Review Pipeline\n\n## Purpose\nAnalyzes a codebase description and produces a comprehensive code review with security analysis, performance suggestions, and refactoring recommendations.\n\n## User Input\n- Code snippets or architecture description\n- Programming language and framework\n- Specific concerns or focus areas\n\n## Desired Output\n- Code quality assessment\n- Security vulnerability analysis\n- Performance optimization suggestions\n- Refactoring recommendations with examples\n- Test coverage suggestions\n- Overall score and priority fix list\n\n## Pipeline Steps\n1. Analyze code structure and patterns\n2. Multi-perspective review (security, performance, maintainability)\n3. Generate specific recommendations\n4. Produce final report with prioritized action items\n\n## Special Requirements\n- Use CodeReview task type for the main review\n- MultiPerspectiveAnalysis for the multi-angle assessment'
    };

    window.loadExample = function(key) {
        if (EXAMPLES[key]) {
            document.getElementById('ideaEditor').value = EXAMPLES[key];
            showToast('Example loaded — edit as needed', 'info');
        }
    };

    // ══════════════════════════════════════════════════════════════
    // Check Existing State on Load
    // ══════════════════════════════════════════════════════════════
    async function checkExistingFiles() {
        // Check status file first
        const statusData = await fetchDocopsStatus();
        let anyRunning = false;

        if (statusData) {
            for (const step of STEPS) {
                const task = getTaskStatus(statusData, step.output);
                const status = normalizeStatus(task);

                if (status === 'RUNNING') {
                    setBadge('badge-' + step.id, 'running');
                    setStageState(step.id, 'active', 'Running…');
                    anyRunning = true;
                    if (task && task.sessionId) {
                        updateSessionLink(step.id, 'RUNNING', task.sessionId);
                    }
                } else if (status === 'COMPLETED') {
                    setBadge('badge-' + step.id, 'done');
                    setStageState(step.id, 'complete', 'Done');
                    if (task && task.sessionId) {
                        updateSessionLink(step.id, 'COMPLETED', task.sessionId);
                    }
                } else if (status === 'ERROR') {
                    setBadge('badge-' + step.id, 'error');
                    setStageState(step.id, 'error', 'Error');
                    if (task && task.sessionId) {
                        updateSessionLink(step.id, 'ERROR', task.sessionId);
                    }
                }
            }
        }

        // Fall back to file existence checks for steps without status
        const fileChecks = [
            { file: 'analysis.md',              badge: 'badge-analyze',  stage: 'analyze' },
            { file: 'pipeline_design.md',       badge: 'badge-design',   stage: 'design' },
            { file: 'generated_app/index.html',  badge: 'badge-ui',       stage: 'ui' },
            { file: 'generated_app/README.md',   badge: 'badge-readme',   stage: 'readme' },
            { file: 'review.md',                badge: 'badge-review',   stage: 'review' },
        ];

        for (const check of fileChecks) {
            const badge = document.getElementById(check.badge);
            if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
            try {
                const content = await readFile(check.file);
                if (content !== null && content.trim().length > 0) {
                    setBadge(check.badge, 'done');
                    setStageState(check.stage, 'complete', 'Done');
                }
            } catch (e) { /* leave as pending */ }
        }

        // Check if generated_app/ops has files
        try {
            const opsFiles = await listFiles('generated_app/ops');
            if (opsFiles.length > 0) {
                const badge = document.getElementById('badge-generate');
                if (badge && !badge.classList.contains('running')) {
                    setBadge('badge-generate', 'done');
                    setStageState('generate', 'complete', 'Done');
                }
            }
        } catch (e) { /* ignore */ }

        // Check if idea.md exists
        try {
            const idea = await readFile('idea.md');
            if (idea && idea.trim().length > 0) {
                setStageState('input', 'complete', 'Saved');
            }
        } catch (e) { /* ignore */ }

        // Enable Open App button if index.html exists
        try {
            const html = await readFile('generated_app/index.html');
            if (html) {
                document.getElementById('btnOpenApp').disabled = false;
            }
        } catch (e) { /* ignore */ }

        if (anyRunning) startStatusPolling();
    }

    // ══════════════════════════════════════════════════════════════
    // Initialize
    // ══════════════════════════════════════════════════════════════
    async function init() {
        // Load existing idea
        try {
            const idea = await readFile('idea.md');
            if (idea !== null) {
                document.getElementById('ideaEditor').value = idea;
            }
        } catch (e) {
            console.warn('Could not load idea.md:', e);
        }

        // Check existing state
        await checkExistingFiles();

        // Start background polling
        startStatusPolling();
    }

    init();
})();
