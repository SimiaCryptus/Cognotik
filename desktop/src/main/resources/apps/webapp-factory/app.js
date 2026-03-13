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
        console.warn('Could not determine session from URL path. File operations may fail.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }
    const proxyBase = '/proxy/';
    // === Status polling state ===
    let statusPollTimer = null;
    const STATUS_POLL_INTERVAL = 3000;
    const taskTargetToBadge = {
        'code/README.md': 'badge-render',
    };
    const activeTaskSessions = {};
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
        const url = `/docops?sessionId=${encodeURIComponent(sessionId)}&doc=${encodeURIComponent(opPath)}&target=${encodeURIComponent(targetPath)}`;
        const resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            const errText = await resp.text().catch(() => '');
            throw new Error(`DocOps failed for ${opPath}: ${resp.status} ${resp.statusText}\n${errText}`);
        }
        return await resp.text();
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
        const badgeId = taskTargetToBadge[target];
        if (badgeId) {
            if (status === 'RUNNING') {
                setBadge(badgeId, 'running');
            } else if (status === 'COMPLETED') {
                setBadge(badgeId, 'done');
            } else if (status === 'ERROR' || status === 'FAILED') {
                setBadge(badgeId, 'error');
            }
        }
        if (status === 'RUNNING') {
            activeTaskSessions[target] = taskSessionId;
        } else {
            delete activeTaskSessions[target];
        }
        updateSessionLinks(target, taskInfo);
    }
    function updateSessionLinks(target, taskInfo) {
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;
        let linkContainerId = null;
        if (target === 'code/README.md') {
            linkContainerId = 'session-link-render';
        }
        if (!linkContainerId) return;
        let container = document.getElementById(linkContainerId);
        if (!container) {
            container = document.createElement('div');
            container.id = linkContainerId;
            container.className = 'session-link-container';
            const parentStep = document.getElementById('viewer-render')?.parentElement;
            if (parentStep) {
                const viewer = parentStep.querySelector('.viewer');
                if (viewer) {
                    parentStep.insertBefore(container, viewer);
                } else {
                    parentStep.appendChild(container);
                }
            }
        }
        if (!container) return;
        if (status === 'RUNNING' && taskSessionId) {
            const proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML = `<div class="session-monitor-link">
<span class="monitor-pulse">●</span>
<span>Rendering project… </span>
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
View Session Log (${escapeHtml(taskSessionId)})
</a>
</div>`;
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            const proxyUrl = taskSessionId ? getProxyUrl(taskSessionId) : '#';
            container.innerHTML = `<div class="session-error-link">
<span>❌ Failed — </span>
${taskSessionId ? `<a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
View Error Log (${escapeHtml(taskSessionId)})
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
            updateTaskStatusUI(target, taskInfo);
            if (taskInfo.status === 'RUNNING') {
                anyRunning = true;
            }
        }
        updatePipelineDiagram(statusData.tasks);
        return anyRunning;
    }
    function updatePipelineDiagram(tasks) {
        const stageMap = {
            'input': { targets: ['idea.md'], el: null },
            'render': { targets: ['code/README.md'], el: null },
            'output': { targets: ['code/README.md'], el: null },
        };
        for (const [stage, info] of Object.entries(stageMap)) {
            const el = document.querySelector(`.pipeline-stage[data-stage="${stage}"]`);
            if (!el) continue;
            let stageStatus = 'pending';
            let anyRunning = false;
            let allDone = true;
            let anyTarget = false;
            for (const t of info.targets) {
                const task = tasks[t];
                if (!task) { allDone = false; continue; }
                anyTarget = true;
                if (task.status === 'RUNNING') anyRunning = true;
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
                if (statusEl) statusEl.textContent = 'Running…';
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
    function logBatch(message, type) {
        batchLog.classList.add('visible');
        const entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        const ts = new Date().toLocaleTimeString();
        entry.textContent = `[${ts}] ${message}`;
        batchLog.appendChild(entry);
        batchLog.scrollTop = batchLog.scrollHeight;
    }
    // === Navigation ===
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
            document.getElementById(sectionId).classList.add('active');
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
            if (statusData && statusData.tasks && statusData.tasks[targetPath]) {
                const task = statusData.tasks[targetPath];
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
            // Auto-save idea before running
            const ideaContent = document.getElementById('idea-editor').value;
            if (ideaContent.trim()) {
                try {
                    await writeFile('idea.md', ideaContent);
                } catch (e) {
                    console.warn('Could not auto-save idea.md:', e);
                }
            }
            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();
            try {
                await runDocOp(opPath, outputPath);
                await waitForTask(outputPath);
                setBadge(badgeId, 'done');
                if (viewerId) {
                    const viewer = document.getElementById(viewerId);
                    if (viewer) {
                        try {
                            const content = await readFile(outputPath);
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
            setBadge('badge-render', 'running');
            await runDocOp('ops/render_op.md', 'code/README.md');
            await waitForTask('code/README.md');
            setBadge('badge-render', 'done');
            logBatch('✓ Completed: Render Project', 'success');
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
        } catch (e) {
            setBadge('badge-render', 'error');
            logBatch('Pipeline failed: ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // === Results section: refresh project files ===
    document.getElementById('btn-refresh-files-results').addEventListener('click', async function() {
        const container = document.getElementById('files-results-container');
        try {
            const entries = await listAllFiles();
            // Filter out system/config files, show project output files
            const skipFiles = new Set(['idea.md', 'app.html', 'app.js', 'style.css', 'marked.min.js', 'docops.status.json', '_files.json']);
            const projectFiles = entries.filter(e => {
                if (e.type === 'directory') return true;
                if (skipFiles.has(e.name)) return false;
                if (e.name.startsWith('.')) return false;
                return true;
            });
            if (projectFiles.length === 0) {
                container.innerHTML = '<p class="placeholder">No project files found. Run the pipeline first.</p>';
                return;
            }
            container.innerHTML = '';
            for (const entry of projectFiles) {
                const section = document.createElement('div');
                section.className = 'result-file-section';
                const icon = entry.type === 'directory' ? '📁' : getFileIcon(entry.name);
                const header = document.createElement('div');
                header.className = 'result-file-header';
                header.innerHTML = `<span class="result-file-icon">${icon}</span> <span>${escapeHtml(entry.name)}</span>`;
                header.style.cursor = 'pointer';
                const body = document.createElement('div');
                body.className = 'result-file-body';
                body.style.display = 'none';
                if (entry.type === 'file') {
                    header.addEventListener('click', async function() {
                        if (body.style.display === 'none') {
                            if (!body.dataset.loaded) {
                                try {
                                    const content = await readFile(entry.name);
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
                } else if (entry.type === 'directory') {
                    header.addEventListener('click', async function() {
                        if (body.style.display === 'none') {
                            if (!body.dataset.loaded) {
                                try {
                                    const dirFiles = await listFiles(entry.name);
                                    if (dirFiles.length === 0) {
                                        body.innerHTML = '<p class="placeholder">Empty directory.</p>';
                                    } else {
                                        let html = '<div class="file-grid">';
                                        for (const f of dirFiles) {
                                            html += `<div class="file-card">
<div class="file-card-icon">${getFileIcon(f.name)}</div>
<div class="file-card-name">${escapeHtml(f.name)}</div>
<button class="btn btn-view btn-sm" data-dir-file="${escapeHtml(entry.name + '/' + f.name)}">👁 View</button>
</div>`;
                                        }
                                        html += '</div>';
                                        body.innerHTML = html;
                                        // Attach view handlers for directory files
                                        body.querySelectorAll('[data-dir-file]').forEach(btn => {
                                            btn.addEventListener('click', async function(e) {
                                                e.stopPropagation();
                                                const filePath = this.dataset.dirFile;
                                                const card = this.closest('.file-card');
                                                const existingViewer = card.querySelector('.file-viewer');
                                                if (existingViewer) {
                                                    existingViewer.remove();
                                                    return;
                                                }
                                                try {
                                                    const content = await readFile(filePath);
                                                    const viewer = document.createElement('div');
                                                    viewer.className = 'file-viewer';
                                                    if (content) {
                                                        if (filePath.endsWith('.md')) {
                                                            viewer.innerHTML = renderMarkdown(content);
                                                        } else {
                                                            viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                                                        }
                                                    } else {
                                                        viewer.innerHTML = '<p class="placeholder">Empty file.</p>';
                                                    }
                                                    card.appendChild(viewer);
                                                } catch (err) {
                                                    alert('Error loading file: ' + err.message);
                                                }
                                            });
                                        });
                                    }
                                } catch (e) {
                                    body.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error listing directory.</p>';
                                }
                                body.dataset.loaded = 'true';
                            }
                            body.style.display = 'block';
                        } else {
                            body.style.display = 'none';
                        }
                    });
                }
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
                updateTaskStatusUI(target, taskInfo);
                if (taskInfo.status === 'RUNNING') {
                    anyRunning = true;
                }
            }
        }
        // Fall back to file existence checks
        const checks = [
            { file: 'code/README.md', badge: 'badge-render' },
        ];
        for (const check of checks) {
            const badge = document.getElementById(check.badge);
            if (badge && badge.classList.contains('running')) continue;
            if (badge && badge.textContent === 'done') continue;
            try {
                const content = await readFile(check.file);
                if (content !== null && content.trim().length > 0) {
                    setBadge(check.badge, 'done');
                }
            } catch (e) { /* leave as pending */ }
        }
        if (anyRunning) {
            startStatusPolling();
        }
    }
    // === Initialize ===
    loadInitialFiles();
    checkExistingFiles();
    startStatusPolling();
})();