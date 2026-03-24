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
        appId = pathParts[fileIndexIdx - 1] || 'puppy-finder';
    } else {
        console.warn('Could not determine session from URL path. File operations may fail.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }
    // Base URL for proxy links (root-relative)
    const proxyBase = '/proxy/';

    // === Model management state ===
    let availableModels = {};

    function getSelectedSmartModel() {
        return localStorage.getItem('pf-smartModel') || '';
    }
    function getSelectedFastModel() {
        return localStorage.getItem('pf-fastModel') || '';
    }

    // === Status polling state ===
    let statusPollTimer = null;
    const STATUS_POLL_INTERVAL = 3000; // ms
    const taskTargetToBadge = {
        'ideas.md': 'badge-brainstorm',
        'expand_status.md': 'badge-expand',
        'final_summary.md': 'badge-summary',
    };
    // Track active task sessions for display
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
    async function runDocOp(opPath, targetPath) {
        const params = new URLSearchParams({
            sessionId: sessionId,
            doc: opPath,
            target: targetPath
        });
        // Add model overrides if selected
        const smartModel = getSelectedSmartModel();
        const fastModel = getSelectedFastModel();
        if (smartModel) params.set('smartModel', smartModel);
        if (fastModel) params.set('fastModel', fastModel);

        const url = `/docops?${params.toString()}`;
        const resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            const errText = await resp.text().catch(() => '');
            throw new Error(`DocOps failed for ${opPath}: ${resp.status} ${resp.statusText}\n${errText}`);
        }
        return await resp.text();
    }

    // === Model loading ===
    async function loadApiProviders() {
        try {
            const response = await fetch('/apiProviders/?format=json');
            if (response.status >= 400) {
                console.warn('Could not load API providers:', response.status);
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
                            ? `Max tokens: ${model.maxTokens.toLocaleString()}`
                            : ''
                    }));
                }
            });

            populateModelDropdowns();
            showProviderInfo(providers);
        } catch (e) {
            console.warn('Failed to load API providers:', e);
        }
    }

    function populateModelDropdowns() {
        const smartSelect = document.getElementById('smart-model-select');
        const fastSelect = document.getElementById('fast-model-select');
        if (!smartSelect || !fastSelect) return;

        smartSelect.innerHTML = '<option value="">(Server Default)</option>';
        fastSelect.innerHTML = '<option value="">(Server Default)</option>';

        const addedModels = new Set();

        for (const [provider, models] of Object.entries(availableModels)) {
            models.forEach(model => {
                if (!addedModels.has(model.id)) {
                    [smartSelect, fastSelect].forEach(sel => {
                        const option = document.createElement('option');
                        option.value = model.id;
                        option.textContent = model.description
                            ? `${model.name} (${provider}) — ${model.description}`
                            : `${model.name} (${provider})`;
                        sel.appendChild(option);
                    });
                    addedModels.add(model.id);
                }
            });
        }

        // Restore saved selections
        const savedSmart = localStorage.getItem('pf-smartModel');
        if (savedSmart && Array.from(smartSelect.options).some(o => o.value === savedSmart)) {
            smartSelect.value = savedSmart;
        }
        const savedFast = localStorage.getItem('pf-fastModel');
        if (savedFast && Array.from(fastSelect.options).some(o => o.value === savedFast)) {
            fastSelect.value = savedFast;
        }
    }

    function showProviderInfo(providers) {
        const box = document.getElementById('model-info-box');
        const list = document.getElementById('provider-list');
        if (!box || !list) return;

        const configured = providers.filter(p => p.models && p.models.length > 0);
        if (configured.length === 0) {
            box.style.display = 'none';
            return;
        }

        box.style.display = 'block';
        list.innerHTML = '';
        configured.forEach(provider => {
            const item = document.createElement('div');
            item.className = 'provider-item';
            item.innerHTML = `
                <span class="provider-badge">✓</span>
                <strong>${escapeHtml(provider.name)}</strong>
                <span class="model-count">${provider.models.length} model(s)</span>
            `;
            list.appendChild(item);
        });
    }

    // Save model settings
    document.getElementById('save-models').addEventListener('click', function() {
        const smartModel = document.getElementById('smart-model-select').value;
        const fastModel = document.getElementById('fast-model-select').value;
        localStorage.setItem('pf-smartModel', smartModel);
        localStorage.setItem('pf-fastModel', fastModel);
        setStatus('models-status', '✓ Model settings saved', 'success');
    });

    // === Full-window modal ===
    function openFullscreenModal(title, htmlContent) {
        const modal = document.getElementById('fullscreen-modal');
        const modalTitle = document.getElementById('fullscreen-modal-title');
        const modalBody = document.getElementById('fullscreen-modal-body');
        modalTitle.textContent = title;
        modalBody.innerHTML = htmlContent;
        modal.classList.remove('hidden');
        document.body.style.overflow = 'hidden';
    }
    function closeFullscreenModal() {
        const modal = document.getElementById('fullscreen-modal');
        modal.classList.add('hidden');
        document.body.style.overflow = '';
    }
    document.getElementById('fullscreen-modal-close').addEventListener('click', closeFullscreenModal);
    // Close on Escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            const modal = document.getElementById('fullscreen-modal');
            if (!modal.classList.contains('hidden')) {
                closeFullscreenModal();
            }
        }
    });

    async function openFileInFullscreen(filePath, title) {
        try {
            const content = await readFile(filePath);
            if (content === null) {
                openFullscreenModal(title || filePath, '<p class="placeholder">File not found.</p>');
            } else {
                openFullscreenModal(title || filePath, renderMarkdown(content));
            }
        } catch (e) {
            openFullscreenModal(title || filePath, '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>');
        }
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
        // Update badge for known targets
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
        // Track active sessions
        if (status === 'RUNNING') {
            activeTaskSessions[target] = taskSessionId;
        } else {
            delete activeTaskSessions[target];
        }
        // Update session link display
        updateSessionLinks(target, taskInfo);
        // Update per-breed research status in breed cards
     if (target.startsWith('research/')) {
            updateBreedResearchStatus(target, taskInfo);
        }
    }

    function updateBreedResearchStatus(target, taskInfo) {
     const breedName = target.replace('research/', '').replace('.md', '');
        const statusEls = document.querySelectorAll(`[data-breed-research-status="${breedName}"]`);
        statusEls.forEach(el => {
            el.className = 'breed-research-status';
            if (taskInfo.status === 'RUNNING') {
                el.textContent = '⏳ Researching…';
                el.classList.add('running');
            } else if (taskInfo.status === 'COMPLETED') {
                el.textContent = '✅ Research complete';
                el.classList.add('done');
            } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                el.textContent = '❌ Research failed';
                el.classList.add('error');
            } else {
                el.textContent = '';
            }
        });
        // Enable/disable per-breed research buttons
        const btns = document.querySelectorAll(`[data-breed-research="${breedName}"]`);
        btns.forEach(btn => {
            btn.disabled = taskInfo.status === 'RUNNING';
        });
    }

    function updateSessionLinks(target, taskInfo) {
        const status = taskInfo.status;
        const taskSessionId = taskInfo.sessionId;
        let linkContainerId = null;
        if (target === 'ideas.md') {
            linkContainerId = 'session-link-brainstorm';
        } else if (target === 'expand_status.md') {
            linkContainerId = 'session-link-expand';
        } else if (target === 'final_summary.md') {
            linkContainerId = 'session-link-summary';
     } else if (target.startsWith('research/')) {
            linkContainerId = 'session-link-research';
        }
        if (!linkContainerId) return;
        let container = document.getElementById(linkContainerId);
        if (!container) {
            container = document.createElement('div');
            container.id = linkContainerId;
            container.className = 'session-link-container';
            let parentStep = null;
            if (linkContainerId === 'session-link-brainstorm') {
                parentStep = document.getElementById('viewer-brainstorm')?.parentElement;
            } else if (linkContainerId === 'session-link-expand') {
                parentStep = document.getElementById('viewer-expand')?.parentElement;
            } else if (linkContainerId === 'session-link-summary') {
                parentStep = document.getElementById('viewer-summary')?.parentElement;
            } else if (linkContainerId === 'session-link-research') {
                parentStep = document.getElementById('viewer-research')?.parentElement;
            }
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
         const breedLabel = target.startsWith('research/')
             ? ' (' + target.replace('research/', '').replace('.md', '').replace(/_/g, ' ') + ')'
                : '';
            container.innerHTML = `<div class="session-monitor-link">
                <span class="monitor-pulse">●</span>
                <span>Processing${breedLabel}… </span>
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
        let anyResearchRunning = false;
        let allResearchDone = true;
        let anyResearchTask = false;
        for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
            updateTaskStatusUI(target, taskInfo);
            if (taskInfo.status === 'RUNNING') {
                anyRunning = true;
            }
     if (target.startsWith('research/')) {
         anyResearchTask = true;
         if (taskInfo.status === 'RUNNING') {
             anyResearchRunning = true;
             allResearchDone = false;
         } else if (taskInfo.status !== 'COMPLETED') {
             allResearchDone = false;
         }
            }
        }
        if (anyResearchTask) {
            if (anyResearchRunning) {
                setBadge('badge-research', 'running');
            } else if (allResearchDone) {
                setBadge('badge-research', 'done');
            }
        }
        updatePipelineDiagram(statusData.tasks);
        return anyRunning;
    }
    function updatePipelineDiagram(tasks) {
        const stageMap = {
            'input': { targets: ['requirements.md'], el: null },
            'brainstorm': { targets: ['ideas.md'], el: null },
            'expand': { targets: ['expand_status.md'], el: null },
            'research': { targets: [], el: null },
            'summary': { targets: ['final_summary.md'], el: null },
        };
        for (const target of Object.keys(tasks)) {
         if (target.startsWith('research/')) {
                stageMap.research.targets.push(target);
            }
        }
        for (const [stage, info] of Object.entries(stageMap)) {
            const el = document.querySelector(`.pipeline-stage[data-stage="${stage}"]`);
            if (!el) continue;
            let stageStatus = 'pending';
            let anyRunning = false;
            let allDone = true;
            let anyTarget = false;
            let activeTaskId = null;
            for (const t of info.targets) {
                const task = tasks[t];
                if (!task) { allDone = false; continue; }
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
            'action': 'action needed'
        };
        el.textContent = labels[state] || state;
    }
    function showLoading(text) {
        const overlay = document.getElementById('loading-overlay');
        const loadingText = document.getElementById('loading-text');
        if (overlay && loadingText) {
            loadingText.textContent = text || 'Processing...';
            overlay.classList.remove('hidden');
        }
    }
    function hideLoading() {
        const overlay = document.getElementById('loading-overlay');
        if (overlay) overlay.classList.add('hidden');
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
    function logBatchHtml(html, type) {
        batchLog.classList.add('visible');
        const entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        const ts = new Date().toLocaleTimeString();
        entry.innerHTML = `[${ts}] ${html}`;
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
            const content = await readFile('requirements.md');
            if (content !== null) {
                document.getElementById('requirements-editor').value = content;
            }
        } catch (e) {
            console.warn('Could not load requirements.md:', e);
        }
    }
    // === Save requirements ===
    document.getElementById('save-requirements').addEventListener('click', async function() {
        const content = document.getElementById('requirements-editor').value;
        try {
            this.disabled = true;
            await writeFile('requirements.md', content);
            setStatus('requirements-status', '✓ Saved successfully', 'success');
        } catch (e) {
            setStatus('requirements-status', '✗ ' + e.message, 'error');
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
                viewer.innerHTML = renderMarkdown(content) +
                    `<div class="button-row" style="margin-top:12px; border-top:1px solid var(--color-border); padding-top:12px;">
                        <button class="btn btn-expand" onclick="window.__pfExpandFile('${escapeHtml(filePath)}', '${escapeHtml(filePath)}')">🔎 Full Window View</button>
                    </div>`;
            }
            viewer.classList.add('visible');
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error loading file: ' + escapeHtml(e.message) + '</p>';
            viewer.classList.add('visible');
        }
    }
    // Expose for inline onclick
    window.__pfExpandFile = function(filePath, title) {
        openFileInFullscreen(filePath, title);
    };

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
                    viewer.innerHTML = renderMarkdown(content) +
                        `<div class="button-row" style="margin-top:12px; border-top:1px solid var(--color-border); padding-top:12px;">
                            <button class="btn btn-expand" onclick="window.__pfExpandFile('${escapeHtml(filePath)}', '${escapeHtml(filePath)}')">🔎 Full Window View</button>
                        </div>`;
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            }
        });
    });
    // === Helper: wait for task completion by polling status ===
    async function waitForTask(targetPath, maxWaitMs) {
        const maxWait = maxWaitMs || 600000;
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
            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();
            try {
                const taskId = await runDocOp(opPath, outputPath);
                const cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(outputPath);
                setBadge(badgeId, 'done');
                if (viewerId) {
                    const viewer = document.getElementById(viewerId);
                    if (viewer) {
                        try {
                            const content = await readFile(outputPath);
                            if (content) {
                                viewer.innerHTML = renderMarkdown(content) +
                                    `<div class="button-row" style="margin-top:12px; border-top:1px solid var(--color-border); padding-top:12px;">
                                        <button class="btn btn-expand" onclick="window.__pfExpandFile('${escapeHtml(outputPath)}', '${escapeHtml(outputPath)}')">🔎 Full Window View</button>
                                    </div>`;
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
    // === Breed list management ===
    async function refreshBreedList(containerId, gridId, showViewButtons) {
        try {
            const files = await listFiles('breeds');
            const container = document.getElementById(containerId);
            const grid = document.getElementById(gridId);
            if (files.length === 0) {
                container.style.display = 'none';
                return [];
            }
            container.style.display = 'block';
            grid.innerHTML = '';

            // Check existing research files
            let researchFiles = [];
            try {
                 researchFiles = await listFiles('research');
            } catch (e) { /* ignore */ }
            const researchFileNames = new Set(researchFiles.map(f => f.name));

            files.forEach(file => {
                const card = document.createElement('div');
                card.className = 'breed-card';
                const breedKey = file.name.replace(/\.md$/, '');
                const name = breedKey.replace(/_/g, ' ').replace(/-/g, ' ');
                const displayName = name.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
                const hasResearch = researchFileNames.has(file.name);
                const researchStatusText = hasResearch ? '✅ Research complete' : '';
                const researchStatusClass = hasResearch ? 'done' : '';

                card.innerHTML = `
                    <div class="breed-card-icon">🐕</div>
                    <div class="breed-card-name">${escapeHtml(displayName)}</div>
                    <div class="breed-card-file">${escapeHtml(file.name)}</div>
                    ${showViewButtons ? `
                        <button class="btn btn-view btn-sm" data-breed-file="breeds/${file.name}" data-breed-name="${escapeHtml(displayName)}">👁 View</button>
                        <button class="btn btn-expand btn-sm" data-breed-expand="breeds/${file.name}" data-breed-name="${escapeHtml(displayName)}">🔎 Full View</button>
                        <button class="btn btn-research btn-sm" data-breed-research="${escapeHtml(breedKey)}" data-breed-name="${escapeHtml(displayName)}">🔍 Research Breeders</button>
                        <div class="breed-research-status ${researchStatusClass}" data-breed-research-status="${escapeHtml(breedKey)}">${researchStatusText}</div>
                    ` : ''}
                `;
                grid.appendChild(card);
            });
            // Attach view handlers
            if (showViewButtons) {
                grid.querySelectorAll('[data-breed-file]').forEach(btn => {
                    btn.addEventListener('click', async function() {
                        const filePath = this.dataset.breedFile;
                        const existingViewer = this.closest('.breed-card').querySelector('.breed-viewer');
                        if (existingViewer) {
                            existingViewer.remove();
                            return;
                        }
                        try {
                            const content = await readFile(filePath);
                            const viewer = document.createElement('div');
                            viewer.className = 'breed-viewer';
                            viewer.innerHTML = content ? renderMarkdown(content) : '<p class="placeholder">File is empty.</p>';
                            this.closest('.breed-card').appendChild(viewer);
                        } catch (e) {
                            alert('Error loading file: ' + e.message);
                        }
                    });
                });
                // Attach full-view handlers
                grid.querySelectorAll('[data-breed-expand]').forEach(btn => {
                    btn.addEventListener('click', function() {
                        openFileInFullscreen(this.dataset.breedExpand, this.dataset.breedName || 'Breed Profile');
                    });
                });
                // Attach per-breed research handlers
                grid.querySelectorAll('[data-breed-research]').forEach(btn => {
                    btn.addEventListener('click', async function() {
                        const breedKey = this.dataset.breedResearch;
                        const breedName = this.dataset.breedName || breedKey;
                     const outputPath = 'research/' + breedKey + '.md';
                        this.disabled = true;
                        const statusEl = this.closest('.breed-card').querySelector(`[data-breed-research-status="${breedKey}"]`);
                        if (statusEl) {
                            statusEl.textContent = '⏳ Researching…';
                            statusEl.className = 'breed-research-status running';
                        }
                        startStatusPolling();
                        try {
                            const taskId = await runDocOp('ops/breeder_research_op.md', outputPath);
                            const cleanTaskId = taskId ? taskId.trim() : '';
                            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                                updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                            }
                            await waitForTask(outputPath);
                            if (statusEl) {
                                statusEl.textContent = '✅ Research complete';
                                statusEl.className = 'breed-research-status done';
                            }
                        } catch (e) {
                            if (statusEl) {
                                statusEl.textContent = '❌ Research failed';
                                statusEl.className = 'breed-research-status error';
                            }
                            alert(`Research failed for ${breedName}: ${e.message}`);
                        } finally {
                            this.disabled = false;
                        }
                    });
                });
            }
            return files;
        } catch (e) {
            console.warn('Could not list breeds:', e);
            return [];
        }
    }
    document.getElementById('btn-refresh-breeds').addEventListener('click', function() {
        refreshBreedList('breed-list-container', 'breed-grid', true);
    });
    // === Research list management ===
    async function refreshResearchList(containerId, gridId, showViewButtons) {
        try {
         const files = await listFiles('research');
            const container = document.getElementById(containerId);
            const grid = document.getElementById(gridId);
            if (files.length === 0) {
                container.style.display = 'none';
                return [];
            }
            container.style.display = 'block';
            grid.innerHTML = '';
            files.forEach(file => {
                const card = document.createElement('div');
                card.className = 'breed-card research-card';
                const name = file.name.replace(/\.md$/, '').replace(/_/g, ' ').replace(/-/g, ' ');
                const displayName = name.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
                card.innerHTML = `
                    <div class="breed-card-icon">🔍</div>
                    <div class="breed-card-name">${escapeHtml(displayName)}</div>
                    <div class="breed-card-file">${escapeHtml(file.name)}</div>
                    ${showViewButtons ? `
                     <button class="btn btn-view btn-sm" data-research-file="research/${file.name}" data-research-name="${escapeHtml(displayName)}">👁 View</button>
                     <button class="btn btn-expand btn-sm" data-research-expand="research/${file.name}" data-research-name="${escapeHtml(displayName)}">🔎 Full View</button>
                    ` : ''}
                `;
                grid.appendChild(card);
            });
            if (showViewButtons) {
                grid.querySelectorAll('[data-research-file]').forEach(btn => {
                    btn.addEventListener('click', async function() {
                        const filePath = this.dataset.researchFile;
                        const existingViewer = this.closest('.breed-card').querySelector('.breed-viewer');
                        if (existingViewer) {
                            existingViewer.remove();
                            return;
                        }
                        try {
                            const content = await readFile(filePath);
                            const viewer = document.createElement('div');
                            viewer.className = 'breed-viewer';
                            viewer.innerHTML = content ? renderMarkdown(content) : '<p class="placeholder">File is empty.</p>';
                            this.closest('.breed-card').appendChild(viewer);
                        } catch (e) {
                            alert('Error loading file: ' + e.message);
                        }
                    });
                });
                // Attach full-view handlers for research
                grid.querySelectorAll('[data-research-expand]').forEach(btn => {
                    btn.addEventListener('click', function() {
                        openFileInFullscreen(this.dataset.researchExpand, '🔍 ' + (this.dataset.researchName || 'Breeder Research'));
                    });
                });
            }
            return files;
        } catch (e) {
            console.warn('Could not list breeder research:', e);
            return [];
        }
    }
    document.getElementById('btn-refresh-research').addEventListener('click', function() {
        refreshResearchList('research-list-container', 'research-grid', true);
    });
    // === Run breeder research for all breeds ===
    document.getElementById('btn-run-research').addEventListener('click', async function() {
        this.disabled = true;
        setBadge('badge-research', 'running');
        startStatusPolling();
        batchLog.innerHTML = '';
        try {
            const breedFiles = await listFiles('breeds');
            if (breedFiles.length === 0) {
                alert('No breed files found. Run Steps 1 and 2 first.');
                setBadge('badge-research', 'error');
                return;
            }
            logBatch(`Found ${breedFiles.length} breed(s) to research`, 'info');
            let successCount = 0;
            let errorCount = 0;
            for (const file of breedFiles) {
                const breedName = file.name.replace(/\.md$/, '');
             const outputPath = 'research/' + breedName + '.md';
                logBatch(`Researching breeders for: ${breedName}`, 'info');
                try {
                    const taskId = await runDocOp('ops/breeder_research_op.md', outputPath);
                    const cleanTaskId = taskId ? taskId.trim() : '';
                    if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                        const proxyUrl = getProxyUrl(cleanTaskId);
                        logBatchHtml(`Session started: <a href="${proxyUrl}" target="_blank" class="monitor-link">Monitor Live Session (${cleanTaskId})</a>`, 'info');
                        updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                    }
                    await waitForTask(outputPath);
                    logBatch(`✓ Completed research for: ${breedName}`, 'success');
                    successCount++;
                } catch (e) {
                    logBatch(`✗ Failed research for ${breedName}: ${e.message}`, 'error');
                    errorCount++;
                }
            }
            if (errorCount === 0) {
                setBadge('badge-research', 'done');
                logBatch(`All ${successCount} breed(s) researched successfully!`, 'success');
            } else {
                setBadge('badge-research', errorCount === breedFiles.length ? 'error' : 'done');
                logBatch(`Completed: ${successCount} succeeded, ${errorCount} failed`, errorCount > 0 ? 'warn' : 'success');
            }
            await refreshResearchList('research-list-container', 'research-grid', true);
        } catch (e) {
            setBadge('badge-research', 'error');
            logBatch('Research failed: ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // === Batch execution helpers ===
    async function runSequential(steps) {
        for (const step of steps) {
            logBatch(`Starting: ${step.label}`, 'info');
            const btn = document.querySelector(`.btn-run[data-op="${step.op}"]`);
            if (btn) {
                setBadge(step.badge, 'running');
                btn.disabled = true;
            }
            try {
                const taskId = await runDocOp(step.op, step.output);
                const cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    const proxyUrl = getProxyUrl(cleanTaskId);
                    logBatchHtml(`Session started: <a href="${proxyUrl}" target="_blank" class="monitor-link">Monitor Live Session (${cleanTaskId})</a>`, 'info');
                    updateSessionLinks(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(step.output);
                if (btn) setBadge(step.badge, 'done');
                logBatch(`✓ Completed: ${step.label}`, 'success');
                if (step.viewer) {
                    try {
                        const content = await readFile(step.output);
                        if (content) {
                            const viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                viewer.innerHTML = renderMarkdown(content) +
                                    `<div class="button-row" style="margin-top:12px; border-top:1px solid var(--color-border); padding-top:12px;">
                                        <button class="btn btn-expand" onclick="window.__pfExpandFile('${escapeHtml(step.output)}', '${escapeHtml(step.output)}')">🔎 Full Window View</button>
                                    </div>`;
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }
                if (step.afterFn) await step.afterFn();
            } catch (e) {
                if (btn) setBadge(step.badge, 'error');
                logBatch(`✗ Failed: ${step.label} — ${e.message}`, 'error');
                throw e;
            } finally {
                if (btn) btn.disabled = false;
            }
        }
    }
    // Run Steps 1-2
    document.getElementById('run-brainstorm-expand').addEventListener('click', async function() {
        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';
        try {
            await runSequential([
                {
                    op: 'ops/breed_brainstorm_op.md', output: 'ideas.md',
                    badge: 'badge-brainstorm', viewer: 'viewer-brainstorm', label: 'Breed Brainstorm'
                },
                {
                    op: 'ops/breed_expand_op.md', output: 'expand_status.md',
                    badge: 'badge-expand', viewer: 'viewer-expand', label: 'Expand Breed Details',
                    afterFn: () => refreshBreedList('breed-list-container', 'breed-grid', true)
                },
            ]);
            logBatch('Steps 1–2 complete!', 'success');
        } catch (e) {
            logBatch('Pipeline stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });
    // Run Steps 3-4
    document.getElementById('run-research-summary').addEventListener('click', async function() {
        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';
        try {
            logBatch('Starting: Breeder Research (all breeds)', 'info');
            setBadge('badge-research', 'running');
            const breedFiles = await listFiles('breeds');
            if (breedFiles.length === 0) {
                logBatch('No breed files found. Run Steps 1–2 first.', 'error');
                setBadge('badge-research', 'error');
                return;
            }
            for (const file of breedFiles) {
                const breedName = file.name.replace(/\.md$/, '');
                const outputPath = 'breeder_research/' + breedName + '.md';
                logBatch(`  Researching: ${breedName}`, 'info');
                try {
                    await runDocOp('ops/breeder_research_op.md', outputPath);
                    await waitForTask(outputPath);
                    logBatch(`  ✓ ${breedName}`, 'success');
                } catch (e) {
                    logBatch(`  ✗ ${breedName}: ${e.message}`, 'error');
                }
            }
            setBadge('badge-research', 'done');
            logBatch('✓ Breeder Research complete', 'success');
            await runSequential([
                {
                    op: 'ops/breeder_summary_op.md', output: 'final_summary.md',
                    badge: 'badge-summary', viewer: 'viewer-summary', label: 'Final Summary'
                },
            ]);
            logBatch('Steps 3–4 complete!', 'success');
        } catch (e) {
            logBatch('Pipeline stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });
    // Run All
    document.getElementById('run-all').addEventListener('click', async function() {
        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';
        try {
            await runSequential([
                {
                    op: 'ops/breed_brainstorm_op.md', output: 'ideas.md',
                    badge: 'badge-brainstorm', viewer: 'viewer-brainstorm', label: 'Breed Brainstorm'
                },
                {
                    op: 'ops/breed_expand_op.md', output: 'expand_status.md',
                    badge: 'badge-expand', viewer: 'viewer-expand', label: 'Expand Breed Details',
                    afterFn: () => refreshBreedList('breed-list-container', 'breed-grid', true)
                },
            ]);
            logBatch('Starting: Breeder Research (all breeds)', 'info');
            setBadge('badge-research', 'running');
            const breedFiles = await listFiles('breeds');
            for (const file of breedFiles) {
                const breedName = file.name.replace(/\.md$/, '');
                const outputPath = 'breeder_research/' + breedName + '.md';
                logBatch(`  Researching: ${breedName}`, 'info');
                try {
                    await runDocOp('ops/breeder_research_op.md', outputPath);
                    await waitForTask(outputPath);
                    logBatch(`  ✓ ${breedName}`, 'success');
                } catch (e) {
                    logBatch(`  ✗ ${breedName}: ${e.message}`, 'error');
                }
            }
            setBadge('badge-research', 'done');
            logBatch('✓ Breeder Research complete', 'success');
            await runSequential([
                {
                    op: 'ops/breeder_summary_op.md', output: 'final_summary.md',
                    badge: 'badge-summary', viewer: 'viewer-summary', label: 'Final Summary'
                },
            ]);
            logBatch('🎉 Entire pipeline complete!', 'success');
        } catch (e) {
            logBatch('Pipeline stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });
    // === Results section: refresh breed profiles ===
    document.getElementById('btn-refresh-breeds-results').addEventListener('click', async function() {
        const container = document.getElementById('breeds-results-container');
        try {
            const files = await listFiles('breeds');
            if (files.length === 0) {
                container.innerHTML = '<p class="placeholder">No breed profiles found. Run the pipeline first.</p>';
                return;
            }
            container.innerHTML = '';
            for (const file of files) {
                const name = file.name.replace(/\.md$/, '').replace(/_/g, ' ').replace(/-/g, ' ');
                const displayName = name.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
                const section = document.createElement('div');
                section.className = 'result-breed-section';
                const header = document.createElement('div');
                header.className = 'result-breed-header';
                header.innerHTML = `
                    <span class="result-breed-icon">🐕</span>
                    <span style="flex:1">${escapeHtml(displayName)}</span>
                    <button class="btn btn-expand btn-sm" data-result-expand="breeds/${file.name}" data-result-name="${escapeHtml(displayName)}">🔎 Full View</button>
                `;
                header.style.cursor = 'pointer';
                const body = document.createElement('div');
                body.className = 'result-breed-body';
                body.style.display = 'none';
                // Click on text area toggles accordion, click on button opens fullscreen
                header.addEventListener('click', async function(e) {
                    if (e.target.closest('[data-result-expand]')) {
                        e.stopPropagation();
                        const btn = e.target.closest('[data-result-expand]');
                        openFileInFullscreen(btn.dataset.resultExpand, '🐕 ' + (btn.dataset.resultName || 'Breed Profile'));
                        return;
                    }
                    if (body.style.display === 'none') {
                        if (!body.dataset.loaded) {
                            try {
                                const content = await readFile('breeds/' + file.name);
                                body.innerHTML = content ? renderMarkdown(content) : '<p class="placeholder">Empty file.</p>';
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
    // === Results section: refresh research ===
    document.getElementById('btn-refresh-research-results').addEventListener('click', async function() {
        const container = document.getElementById('research-results-container');
        try {
         const files = await listFiles('research');
            if (files.length === 0) {
                container.innerHTML = '<p class="placeholder">No breeder research found. Run the pipeline first.</p>';
                return;
            }
            container.innerHTML = '';
            for (const file of files) {
                const name = file.name.replace(/\.md$/, '').replace(/_/g, ' ').replace(/-/g, ' ');
                const displayName = name.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
                const section = document.createElement('div');
                section.className = 'result-breed-section';
                const header = document.createElement('div');
                header.className = 'result-breed-header';
                header.innerHTML = `
                    <span class="result-breed-icon">🔍</span>
                    <span style="flex:1">${escapeHtml(displayName)}</span>
                 <button class="btn btn-expand btn-sm" data-result-expand="research/${file.name}" data-result-name="${escapeHtml(displayName)}">🔎 Full View</button>
                `;
                header.style.cursor = 'pointer';
                const body = document.createElement('div');
                body.className = 'result-breed-body';
                body.style.display = 'none';
                header.addEventListener('click', async function(e) {
                    if (e.target.closest('[data-result-expand]')) {
                        e.stopPropagation();
                        const btn = e.target.closest('[data-result-expand]');
                        openFileInFullscreen(btn.dataset.resultExpand, '🔍 ' + (btn.dataset.resultName || 'Breeder Research'));
                        return;
                    }
                    if (body.style.display === 'none') {
                        if (!body.dataset.loaded) {
                            try {
                             const content = await readFile('research/' + file.name);
                                body.innerHTML = content ? renderMarkdown(content) : '<p class="placeholder">Empty file.</p>';
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
        const checks = [
            { file: 'ideas.md', badge: 'badge-brainstorm' },
            { file: 'expand_status.md', badge: 'badge-expand' },
            { file: 'final_summary.md', badge: 'badge-summary' },
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
        try {
            const breedFiles = await listFiles('breeds');
            if (breedFiles.length > 0) {
                await refreshBreedList('breed-list-container', 'breed-grid', true);
            }
        } catch (e) { /* ignore */ }
        try {
         const researchFiles = await listFiles('research');
            if (researchFiles.length > 0) {
                setBadge('badge-research', 'done');
                await refreshResearchList('research-list-container', 'research-grid', true);
            }
        } catch (e) { /* ignore */ }
        if (anyRunning) {
            startStatusPolling();
        }
    }
    // === Initialize ===
    loadInitialFiles();
    loadApiProviders();
    checkExistingFiles();
    startStatusPolling();
})();