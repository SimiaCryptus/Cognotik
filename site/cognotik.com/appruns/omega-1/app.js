(function() {
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
    // === Derive app base for ZIP/Git endpoints ===
    // basePath is like /myapp/fileIndex/SESSION_ID
    // We need the app root (e.g., /myapp) for fileZip endpoint
    var appRoot = '';
    if (fileIndexIdx >= 0) {
        appRoot = pathParts.slice(0, fileIndexIdx).join('/');
    }

    // === Model Management ===
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
            providers.forEach(function(provider) {
                if (provider.models && provider.models.length > 0) {
                    availableModels[provider.name] = provider.models.map(function(model) {
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
            setStatus('models-status', '✓ Loaded ' + countModels() + ' models from ' + Object.keys(availableModels).length + ' providers', 'success');
        } catch (e) {
            console.warn('Failed to load API providers:', e);
            setStatus('models-status', '✗ Failed to load models: ' + e.message, 'error');
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
        if (!smartSelect || !fastSelect || !imageSelect) return;
        [smartSelect, fastSelect, imageSelect].forEach(function(sel) {
            sel.innerHTML = '';
        });
        var hasModels = false;
        for (var provider in availableModels) {
            if (!availableModels.hasOwnProperty(provider)) continue;
            var models = availableModels[provider];
            if (!models || models.length === 0) continue;
            hasModels = true;
            [smartSelect, fastSelect, imageSelect].forEach(function(sel) {
                var optgroup = document.createElement('optgroup');
                optgroup.label = provider;
                models.forEach(function(model) {
                    var option = document.createElement('option');
                    option.value = model.id;
                    option.textContent = model.name;
                    if (model.description) {
                        option.title = model.description;
                    }
                    optgroup.appendChild(option);
                });
                sel.appendChild(optgroup);
            });
        }
        if (!hasModels) {
            [smartSelect, fastSelect, imageSelect].forEach(function(sel) {
                var option = document.createElement('option');
                option.value = '';
                option.textContent = 'No models available — configure API keys first';
                option.disabled = true;
                sel.appendChild(option);
            });
            return;
        }
        // Restore saved selections
        restoreModelSelection(smartSelect, 'smartModel');
        restoreModelSelection(fastSelect, 'fastModel');
        restoreModelSelection(imageSelect, 'imageModel');
    }
    function restoreModelSelection(selectEl, storageKey) {
        var saved = localStorage.getItem(storageKey);
        if (saved) {
            var options = Array.from(selectEl.options);
            var match = options.find(function(o) { return o.value === saved; });
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
        var infoCard = document.getElementById('model-info-card');
        var infoContainer = document.getElementById('model-provider-info');
        if (!infoCard || !infoContainer) return;
        var providers = Object.keys(availableModels);
        if (providers.length === 0) {
            infoCard.style.display = 'none';
            return;
        }
        var html = '<div class="provider-list">';
        providers.forEach(function(provider) {
            var models = availableModels[provider];
            html += '<div class="provider-item">' +
                    '<span class="provider-name">' + escapeHtml(provider) + '</span>' +
                    '<span class="provider-count">' + models.length + ' model' + (models.length !== 1 ? 's' : '') + '</span>' +
                    '</div>';
        });
        html += '</div>';
        infoContainer.innerHTML = html;
        infoCard.style.display = 'block';
    }
    // Save model settings
    document.getElementById('save-model-settings').addEventListener('click', function() {
        var models = getSelectedModels();
        if (models.smartModel) localStorage.setItem('smartModel', models.smartModel);
        if (models.fastModel) localStorage.setItem('fastModel', models.fastModel);
        if (models.imageModel) localStorage.setItem('imageModel', models.imageModel);
        setStatus('model-save-status', '✓ Model settings saved', 'success');
    });
    // Clear model settings
    document.getElementById('clear-model-settings').addEventListener('click', function() {
        localStorage.removeItem('smartModel');
        localStorage.removeItem('fastModel');
        localStorage.removeItem('imageModel');
        // Reset dropdowns to first option
        var smartSelect = document.getElementById('model-smart');
        var fastSelect = document.getElementById('model-fast');
        var imageSelect = document.getElementById('model-image');
        if (smartSelect && smartSelect.options.length > 0) smartSelect.selectedIndex = 0;
        if (fastSelect && fastSelect.options.length > 0) fastSelect.selectedIndex = 0;
        if (imageSelect && imageSelect.options.length > 0) imageSelect.selectedIndex = 0;
        setStatus('model-save-status', '✓ Model settings reset to defaults', 'success');
    });
    // Refresh models button
    document.getElementById('refresh-models').addEventListener('click', function() {
        setStatus('models-status', 'Loading models...', 'info');
        loadApiProviders();
    });


    // Display session info
    const sessionInfoEl = document.getElementById('session-info');
    if (sessionId) {
        sessionInfoEl.textContent = 'Session: ' + sessionId;
    }

    // === File I/O ===
    async function readFile(filePath) {
        const resp = await fetch(basePath + '/' + filePath);
        if (!resp.ok) {
            if (resp.status === 404 || resp.status === 400) return null;
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
        } catch (e) { return false; }
    }

    async function listFiles(dirPath) {
        const url = basePath + '/' + dirPath + '/_files.json';
        try {
            const resp = await fetch(url);
            if (!resp.ok) return [];
            try {
                const data = await resp.json();
                return data.entries || [];
            } catch (parseErr) {
                return [];
            }
        } catch (e) { return []; }
    }

    // === DocOps Execution ===
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
            const errText = await resp.text().catch(function() { return ''; });
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

    function getTaskStatus(statusData, targetPath) {
        if (!statusData || !statusData.tasks) return null;
        if (statusData.tasks[targetPath]) return statusData.tasks[targetPath];
        var filename = targetPath.split('/').pop();
        if (statusData.tasks[filename]) return statusData.tasks[filename];
        for (var key in statusData.tasks) {
            if (statusData.tasks.hasOwnProperty(key)) {
                var task = statusData.tasks[key];
                if (task.target === targetPath || task.target === filename) return task;
                // Match if the key ends with the target or vice versa (handle trailing slashes, relative paths)
                var normalizedKey = key.replace(/\/+$/, '');
                var normalizedTarget = targetPath.replace(/\/+$/, '');
                if (normalizedKey === normalizedTarget) return task;
                if (normalizedKey.endsWith('/' + normalizedTarget) || normalizedTarget.endsWith('/' + normalizedKey)) return task;
                // Also match task.target with normalization
                if (task.target) {
                    var normalizedTaskTarget = task.target.replace(/\/+$/, '');
                    if (normalizedTaskTarget === normalizedTarget) return task;
                    if (normalizedTaskTarget.endsWith('/' + normalizedTarget) || normalizedTarget.endsWith('/' + normalizedTaskTarget)) return task;
                }
            }
        }
        return null;
    }

    async function waitForTask(targetPath, maxWaitMs) {
        var maxWait = maxWaitMs || 900000; // 15 minutes
        var pollInterval = 3000;
        var startTime = Date.now();
        var foundTask = false;
        var missCount = 0;
        // Also try without trailing slash for matching
        var altTargetPath = targetPath.endsWith('/')
            ? targetPath.replace(/\/+$/, '')
            : targetPath + '/';
        while (Date.now() - startTime < maxWait) {
            var statusData = await fetchDocopsStatus();
            var task = getTaskStatus(statusData, targetPath);
            if (!task) task = getTaskStatus(statusData, altTargetPath);
            if (task) {
                foundTask = true;
                if (task.status === 'COMPLETED') return task;
                if (task.status === 'ERROR' || task.status === 'FAILED') {
                    throw new Error('Task ' + targetPath + ' failed');
                }
            } else {
                missCount++;
                // If we've never found the task and it's been a while, the server may not have registered it yet
                // Keep waiting - but if we found it before and now it's gone, that's unexpected
                if (foundTask) {
                    // Task disappeared from status - treat as completed (server may have cleaned up)
                    return { status: 'COMPLETED', target: targetPath };
                }
            }
            await new Promise(function(r) { setTimeout(r, pollInterval); });
        }
        throw new Error('Task ' + targetPath + ' timed out');
    }

    var statusPollTimer = null;
    function startStatusPolling() {
        if (statusPollTimer) return;
        statusPollTimer = setInterval(pollAndUpdateStatus, 4000);
        pollAndUpdateStatus();
    }

    function stopStatusPolling() {
        if (statusPollTimer) {
            clearInterval(statusPollTimer);
            statusPollTimer = null;
        }
    }

    // Badge mapping: target path -> badge ID
    var targetBadgeMap = {
        'requirements.md': 'badge-requirements',
         'requirements_review.md': 'badge-review',
        'generated_app/ops': 'badge-pipeline',
        'generated_app': 'badge-ui'
    };

    // Pipeline node mapping
    var targetNodeMap = {
        'requirements.md': 'pnode-requirements',
         'requirements_review.md': 'pnode-review',
        'generated_app/ops': 'pnode-pipeline',
        'generated_app': 'pnode-ui'
    };

    async function pollAndUpdateStatus() {
        var statusData = await fetchDocopsStatus();
        if (!statusData || !statusData.tasks) return;
        for (var target in statusData.tasks) {
            if (!statusData.tasks.hasOwnProperty(target)) continue;
            var taskInfo = statusData.tasks[target];
            var badgeId = targetBadgeMap[target] || targetBadgeMap[target + '/'] || targetBadgeMap[target.replace(/\/+$/, '')] || findBadgeForTarget(target);
            if (badgeId) {
                var nodeId = targetNodeMap[target] || targetNodeMap[target + '/'] || targetNodeMap[target.replace(/\/+$/, '')] || findNodeForTarget(target);
                if (taskInfo.status === 'RUNNING') {
                    setBadge(badgeId, 'running');
                    updatePipelineNode(nodeId, 'active-node');
                } else if (taskInfo.status === 'COMPLETED') {
                    setBadge(badgeId, 'done');
                    updatePipelineNode(nodeId, 'done-node');
                } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                    setBadge(badgeId, 'error');
                    updatePipelineNode(nodeId, 'error-node');
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
            setTimeout(function() { el.textContent = ''; el.className = 'status-msg'; }, 5000);
        }
    }

    function setBadge(badgeId, state) {
        var el = document.getElementById(badgeId);
        if (!el) return;
        el.className = 'step-badge ' + state;
        var labels = { pending: 'pending', running: 'running…', done: 'done', error: 'error' };
        el.textContent = labels[state] || state;
    }

    function updatePipelineNode(nodeId, className) {
        if (!nodeId) return;
        var el = document.getElementById(nodeId);
        if (!el) return;
        el.classList.remove('active-node', 'done-node', 'error-node');
        if (className) el.classList.add(className);
    }

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
            var viewer = document.getElementById('viewer-' + safeTarget);
            if (viewer && viewer.parentElement) {
                viewer.parentElement.insertBefore(container, viewer);
            } else {
                // Try badge-based lookup
                var badgeId = targetBadgeMap[target];
                if (badgeId) {
                    var badge = document.getElementById(badgeId);
                    if (badge) {
                        var step = badge.closest('.step') || badge.closest('.card');
                        if (step) step.appendChild(container);
                    }
                }
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
                '📡 Monitor Live Session (' + escapeHtml(taskSessionId) + ')' +
                '</a></div>';
            container.style.display = 'block';
        } else if (status === 'COMPLETED' && taskSessionId) {
            var proxyUrl2 = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-completed-link">' +
                '<span>✅ Completed — </span>' +
                '<a href="' + escapeHtml(proxyUrl2) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '📋 View Session Log (' + escapeHtml(taskSessionId) + ')' +
                '</a></div>';
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            var proxyUrl3 = taskSessionId ? getProxyUrl(taskSessionId) : '#';
            container.innerHTML =
                '<div class="session-error-link">' +
                '<span>❌ Failed — </span>' +
                (taskSessionId
                    ? '<a href="' + escapeHtml(proxyUrl3) + '" target="_blank" class="monitor-link">🔍 View Error Log (' + escapeHtml(taskSessionId) + ')</a>'
                    : '<span>No session available</span>') +
                '</div>';
            container.style.display = 'block';
        } else {
            container.style.display = 'none';
        }
    }

    // === Batch Log ===
    function getBatchLog(logId) {
        return document.getElementById(logId);
    }

    function logBatch(logId, message, type) {
        var log = getBatchLog(logId);
        if (!log) return;
        log.classList.add('visible');
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.textContent = '[' + ts + '] ' + message;
        log.appendChild(entry);
        log.scrollTop = log.scrollHeight;
    }

    function logBatchHtml(logId, html, type) {
        var log = getBatchLog(logId);
        if (!log) return;
        log.classList.add('visible');
        var entry = document.createElement('div');
        entry.className = 'log-entry log-' + (type || 'info');
        var ts = new Date().toLocaleTimeString();
        entry.innerHTML = '[' + ts + '] ' + html;
        log.appendChild(entry);
        log.scrollTop = log.scrollHeight;
    }

    // === Tab Navigation ===
    document.querySelectorAll('.nav-link').forEach(function(link) {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            var sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(function(l) { l.classList.remove('active'); });
            document.querySelectorAll('.section').forEach(function(s) { s.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(sectionId).classList.add('active');
        });
    });

    // === Results Tabs ===
    document.querySelectorAll('.results-tab').forEach(function(tab) {
        tab.addEventListener('click', function() {
            document.querySelectorAll('.results-tab').forEach(function(t) { t.classList.remove('active'); });
            document.querySelectorAll('.tab-panel').forEach(function(p) { p.classList.remove('active'); });
            this.classList.add('active');
            document.getElementById(this.dataset.tab).classList.add('active');
        });
    });

    // === Save Idea ===
    document.getElementById('save-idea').addEventListener('click', async function() {
        var content = document.getElementById('idea-editor').value;
        if (!content.trim()) {
            setStatus('idea-status', '✗ Please enter your app idea first.', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('idea.md', content);
            setStatus('idea-status', '✓ Idea saved successfully', 'success');
        } catch (e) {
            setStatus('idea-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Save Pipeline Notes ===
    document.getElementById('save-pipeline-notes').addEventListener('click', async function() {
        var content = document.getElementById('pipeline-notes-editor').value;
        try {
            this.disabled = true;
            await writeFile('pipeline_notes.md', content);
            setStatus('pipeline-notes-status', '✓ Saved', 'success');
        } catch (e) {
            setStatus('pipeline-notes-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Save UI Notes ===
    document.getElementById('save-ui-notes').addEventListener('click', async function() {
        var content = document.getElementById('ui-notes-editor').value;
        try {
            this.disabled = true;
            await writeFile('ui_notes.md', content);
            setStatus('ui-notes-status', '✓ Saved', 'success');
        } catch (e) {
            setStatus('ui-notes-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === View File (toggle) ===
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
            } else if (filePath.endsWith('.html') || filePath.endsWith('.json') || filePath.endsWith('.js')) {
                viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
            } else {
                viewer.innerHTML = renderMarkdown(content);
            }
            viewer.classList.add('visible');
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            viewer.classList.add('visible');
        }
    }

    document.querySelectorAll('.btn-view').forEach(function(btn) {
        btn.addEventListener('click', function() {
            viewFile(this.dataset.file, this.dataset.viewer);
        });
    });

    // === Run Operation Buttons ===
    document.querySelectorAll('.btn-run').forEach(function(btn) {
        btn.addEventListener('click', async function() {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            // Auto-save relevant files before running
            await autoSaveBeforeRun(opPath);

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

                // Auto-show result
             // Update "Open App" buttons if this was a UI generation step
             if (outputPath.indexOf('generated_app') >= 0) {
                 updateOpenAppVisibility();
             }

                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        try {
                            var content;
                            if (outputPath.endsWith('/')) {
                                // Directory output - show README if available
                                content = await readFile(outputPath.replace(/\/$/, '') + '/../README.md');
                                if (!content) content = await readFile(outputPath + 'README.md');
                            } else {
                                content = await readFile(outputPath);
                            }
                            if (content) {
                                if (outputPath.endsWith('.html') || outputPath.endsWith('.json')) {
                                    viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                                } else {
                                    viewer.innerHTML = renderMarkdown(content);
                                }
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

    // Auto-save relevant files before running an operation
    async function autoSaveBeforeRun(opPath) {
         if (opPath.indexOf('requirements') >= 0 || opPath.indexOf('review_requirements') >= 0 || opPath.indexOf('refine_requirements') >= 0 || opPath.indexOf('generate_pipeline') >= 0 || opPath.indexOf('generate_ui') >= 0) {
            var ideaContent = document.getElementById('idea-editor').value;
            if (ideaContent.trim()) {
                await writeFile('idea.md', ideaContent);
            }
        }
        if (opPath.indexOf('update_pipeline') >= 0) {
            var pipelineNotes = document.getElementById('pipeline-notes-editor').value;
            if (pipelineNotes.trim()) {
                await writeFile('pipeline_notes.md', pipelineNotes);
            }
        }
        if (opPath.indexOf('update_ui') >= 0) {
            var uiNotes = document.getElementById('ui-notes-editor').value;
            if (uiNotes.trim()) {
                await writeFile('ui_notes.md', uiNotes);
            }
        }
    }

    // === Sequential Batch Execution ===
    async function runSequential(logId, steps) {
        for (var i = 0; i < steps.length; i++) {
            var step = steps[i];
            logBatch(logId, 'Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');
            if (step.nodeId) updatePipelineNode(step.nodeId, 'active-node');

            try {
                // Auto-save
                if (step.preSave) await step.preSave();

                var taskId = await runDocOp(step.op, step.output);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    logBatchHtml(logId,
                        'Session: <a href="' + getProxyUrl(cleanTaskId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId + ')</a>',
                        'info');
                    updateSessionLinks(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(step.output);
                setBadge(step.badge, 'done');
                if (step.nodeId) updatePipelineNode(step.nodeId, 'done-node');
                logBatch(logId, '✓ Completed: ' + step.label, 'success');

                if (step.viewer) {
                    try {
                        var content;
                        if (step.output.endsWith('/')) {
                            content = await readFile('generated_app/README.md');
                        } else {
                            content = await readFile(step.output);
                        }
                        if (content !== null && content !== undefined) {
                            var viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                if (step.output.endsWith('.html') || step.output.endsWith('.json')) {
                                    viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                                } else {
                                    viewer.innerHTML = renderMarkdown(content);
                                }
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) {
                        // Non-critical: file may not exist yet, which is expected
                    }
                }

                if (step.afterFn) await step.afterFn();
            } catch (e) {
                setBadge(step.badge, 'error');
                if (step.nodeId) updatePipelineNode(step.nodeId, 'error-node');
                logBatch(logId, '✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
    }

    // === Run All Generate ===
    document.getElementById('run-all-generate').addEventListener('click', async function() {
        var ideaContent = document.getElementById('idea-editor').value;
        if (!ideaContent.trim()) {
            alert('Please enter your app idea first on the Idea tab.');
            return;
        }

        this.disabled = true;
        startStatusPolling();
        var log = getBatchLog('batch-log-generate');
        if (log) log.innerHTML = '';

        try {
            await runSequential('batch-log-generate', [
                {
                    op: 'ops/requirements_op.md',
                    output: 'requirements.md',
                    badge: 'badge-requirements',
                    nodeId: 'pnode-requirements',
                    viewer: 'viewer-requirements',
                    label: 'Generate Requirements',
                    preSave: async function() { await writeFile('idea.md', ideaContent); }
                },
                {
                     op: 'ops/review_requirements_op.md',
                     output: 'requirements_review.md',
                     badge: 'badge-review',
                     nodeId: 'pnode-review',
                     viewer: 'viewer-review',
                     label: 'Review Requirements'
                 },
                 {
                     op: 'ops/refine_requirements_op.md',
                     output: 'requirements.md',
                     badge: 'badge-refine',
                     nodeId: 'pnode-refine',
                     viewer: 'viewer-refine',
                     label: 'Refine Requirements'
                 },
                 {
                    op: 'ops/generate_pipeline_op.md',
                    output: 'generated_app/ops/',
                    badge: 'badge-pipeline',
                    nodeId: 'pnode-pipeline',
                    viewer: 'viewer-pipeline',
                    label: 'Generate Pipeline Ops'
                },
                {
                    op: 'ops/generate_ui_op.md',
                    output: 'generated_app/',
                    badge: 'badge-ui',
                    nodeId: 'pnode-ui',
                    viewer: 'viewer-ui',
                    label: 'Generate UI'
                }
            ]);
            logBatch('batch-log-generate', '🎉 App generation complete!', 'success');
             await updateOpenAppVisibility();
        } catch (e) {
            logBatch('batch-log-generate', 'Pipeline stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Run All Updates ===
    document.getElementById('run-all-update').addEventListener('click', async function() {
        this.disabled = true;
        startStatusPolling();
        var log = getBatchLog('batch-log-update');
        if (log) log.innerHTML = '';

        var pipelineNotes = document.getElementById('pipeline-notes-editor').value;
        var uiNotes = document.getElementById('ui-notes-editor').value;

        var steps = [];

        if (pipelineNotes.trim()) {
            steps.push({
                op: 'ops/update_pipeline_op.md',
                output: 'generated_app/ops/',
                badge: 'badge-update-pipeline',
                viewer: 'viewer-update-pipeline',
                label: 'Update Pipeline',
                preSave: async function() { await writeFile('pipeline_notes.md', pipelineNotes); }
            });
        }

        if (uiNotes.trim()) {
            steps.push({
                op: 'ops/update_ui_op.md',
                output: 'generated_app/',
                badge: 'badge-update-ui',
                viewer: 'viewer-update-ui',
                label: 'Update UI',
                preSave: async function() { await writeFile('ui_notes.md', uiNotes); }
            });
        }

        if (steps.length === 0) {
            logBatch('batch-log-update', 'No update notes provided. Write notes in the fields above.', 'warn');
            this.disabled = false;
            return;
        }

        try {
            await runSequential('batch-log-update', steps);
            logBatch('batch-log-update', '🎉 Updates complete!', 'success');
             await updateOpenAppVisibility();
        } catch (e) {
            logBatch('batch-log-update', 'Update stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Results: Refresh README ===
    document.getElementById('refresh-readme').addEventListener('click', async function() {
        var viewer = document.getElementById('result-readme');
        try {
            var content = await readFile('generated_app/README.md');
            viewer.innerHTML = content ? renderMarkdown(content) : '<p class="placeholder">README not found. Generate the app first.</p>';
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    });

    // === Results: Refresh Requirements ===
    document.getElementById('refresh-requirements').addEventListener('click', async function() {
        var viewer = document.getElementById('result-requirements');
        try {
            var content = await readFile('requirements.md');
            viewer.innerHTML = content ? renderMarkdown(content) : '<p class="placeholder">Requirements not generated yet.</p>';
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    });
     // === Results: Refresh Review ===
     document.getElementById('refresh-review').addEventListener('click', async function() {
         var viewer = document.getElementById('result-review');
         try {
             var content = await readFile('requirements_review.md');
             viewer.innerHTML = content ? renderMarkdown(content) : '<p class="placeholder">Requirements review not generated yet.</p>';
         } catch (e) {
             viewer.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
         }
     });


    // === Results: Refresh Ops List ===
    document.getElementById('refresh-ops-list').addEventListener('click', async function() {
        await loadOpsList();
    });

    async function loadOpsList() {
        var container = document.getElementById('ops-list');
        try {
            var entries = await listFiles('generated_app/ops');
            var files = entries.filter(function(e) { return e.type === 'file'; });
            if (files.length === 0) {
                container.innerHTML = '<p class="placeholder">No op files found. Generate the pipeline first.</p>';
                return;
            }
            var html = '<div class="file-tree">';
            files.forEach(function(f) {
                html += '<div class="file-tree-item file-tree-file" data-path="generated_app/ops/' + escapeHtml(f.name) + '">' +
                        (f.name.endsWith('.md') ? '📄 ' : f.name.endsWith('.json') ? '⚙️ ' : '📎 ') +
                        escapeHtml(f.name) + '</div>';
            });
            html += '</div>';
            html += '<div class="viewer" id="viewer-ops-file" style="margin-top:1rem;"></div>';
            container.innerHTML = html;

            // Attach click handlers
            container.querySelectorAll('.file-tree-item').forEach(function(item) {
                item.addEventListener('click', async function() {
                    var path = this.dataset.path;
                    var viewer = document.getElementById('viewer-ops-file');
                    if (!viewer) return;
                    try {
                        var content = await readFile(path);
                        if (content) {
                            if (path.endsWith('.json')) {
                                viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                            } else {
                                viewer.innerHTML = '<pre><code>' + escapeHtml(content) + '</code></pre>';
                            }
                            viewer.classList.add('visible');
                        }
                    } catch (e) {
                        viewer.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error loading file.</p>';
                        viewer.classList.add('visible');
                    }
                });
            });
        } catch (e) {
            container.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    }

    // === Results: Refresh UI Source ===
    document.getElementById('refresh-ui-source').addEventListener('click', async function() {
        var viewer = document.getElementById('result-ui-source');
        try {
            var content = await readFile('generated_app/index.html');
            viewer.innerHTML = content
                ? '<pre><code>' + escapeHtml(content) + '</code></pre>'
                : '<p class="placeholder">UI not generated yet.</p>';
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    });

    // === Results: Refresh All Files ===
    document.getElementById('refresh-all-files').addEventListener('click', async function() {
        await loadAllFiles();
    });

    document.getElementById('refresh-file-tree').addEventListener('click', async function() {
        // Refresh whichever tab is active
        var activeTab = document.querySelector('.results-tab.active');
        if (activeTab) {
            var tabId = activeTab.dataset.tab;
            if (tabId === 'tab-readme') document.getElementById('refresh-readme').click();
            else if (tabId === 'tab-requirements') document.getElementById('refresh-requirements').click();
             else if (tabId === 'tab-review') document.getElementById('refresh-review').click();
            else if (tabId === 'tab-ops') document.getElementById('refresh-ops-list').click();
            else if (tabId === 'tab-ui-preview') document.getElementById('refresh-ui-source').click();
            else if (tabId === 'tab-files') await loadAllFiles();
        }
    });

    async function loadAllFiles() {
        var container = document.getElementById('all-files-tree');
        try {
            var html = '<div class="file-tree">';

            // Root level generated_app files
            var rootEntries = await listFiles('generated_app');
            var rootFiles = rootEntries.filter(function(e) { return e.type === 'file'; });
            var rootDirs = rootEntries.filter(function(e) { return e.type === 'directory'; });

            html += '<div class="file-tree-item file-tree-dir">📁 generated_app/</div>';
            rootFiles.forEach(function(f) {
                html += '<div class="file-tree-item file-tree-file" data-path="generated_app/' + escapeHtml(f.name) + '" style="padding-left:1.5rem;">' +
                        getFileIcon(f.name) + ' ' + escapeHtml(f.name) + '</div>';
            });

            // Subdirectories
            for (var i = 0; i < rootDirs.length; i++) {
                var dir = rootDirs[i];
                html += '<div class="file-tree-item file-tree-dir" style="padding-left:1.5rem;">📁 ' + escapeHtml(dir.name) + '/</div>';
                var subEntries = await listFiles('generated_app/' + dir.name);
                var subFiles = subEntries.filter(function(e) { return e.type === 'file'; });
                subFiles.forEach(function(f) {
                    html += '<div class="file-tree-item file-tree-file" data-path="generated_app/' + escapeHtml(dir.name) + '/' + escapeHtml(f.name) + '" style="padding-left:3rem;">' +
                            getFileIcon(f.name) + ' ' + escapeHtml(f.name) + '</div>';
                });
            }

            html += '</div>';
            container.innerHTML = html;

            // Attach click handlers
            container.querySelectorAll('.file-tree-file').forEach(function(item) {
                item.addEventListener('click', async function() {
                    var path = this.dataset.path;
                    var viewer = document.getElementById('viewer-file-content');
                    if (!viewer) return;
                    try {
                        var content = await readFile(path);
                        if (content) {
                            if (path.endsWith('.md')) {
                                viewer.innerHTML = '<h4 style="margin-bottom:0.5rem;color:var(--text-muted);">' + escapeHtml(path) + '</h4>' + renderMarkdown(content);
                            } else {
                                viewer.innerHTML = '<h4 style="margin-bottom:0.5rem;color:var(--text-muted);">' + escapeHtml(path) + '</h4><pre><code>' + escapeHtml(content) + '</code></pre>';
                            }
                            viewer.classList.add('visible');
                        }
                    } catch (e) {
                        viewer.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error loading file.</p>';
                        viewer.classList.add('visible');
                    }
                });
            });

            if (rootEntries.length === 0) {
                container.innerHTML = '<p class="placeholder">No generated files found. Run the generation pipeline first.</p>';
            }
        } catch (e) {
            container.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    }

    function getFileIcon(name) {
        if (name.endsWith('.md')) return '📄';
        if (name.endsWith('.html')) return '🌐';
        if (name.endsWith('.json')) return '⚙️';
        if (name.endsWith('.js')) return '📜';
        if (name.endsWith('.css')) return '🎨';
        return '📎';
    }

    // === Load Initial Files ===
    async function loadInitialFiles() {
        // Load idea
        try {
            var ideaContent = await readFile('idea.md');
            if (ideaContent !== null) {
                document.getElementById('idea-editor').value = ideaContent;
            }
        } catch (e) { /* File may not exist yet - expected */ }

        // Load pipeline notes
        try {
            var pipelineNotes = await readFile('pipeline_notes.md');
            if (pipelineNotes !== null) {
                document.getElementById('pipeline-notes-editor').value = pipelineNotes;
            }
        } catch (e) { /* ok */ }

        // Load UI notes
        try {
            var uiNotes = await readFile('ui_notes.md');
            if (uiNotes !== null) {
                document.getElementById('ui-notes-editor').value = uiNotes;
            }
        } catch (e) { /* ok */ }
    }

    // === Check Existing State ===
    async function checkExistingFiles() {
        var statusData = await fetchDocopsStatus();
        var anyRunning = false;

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                // Try direct match first, then normalized match
                var badgeId = targetBadgeMap[target] || findBadgeForTarget(target);
                if (badgeId) {
                    if (taskInfo.status === 'RUNNING') {
                        setBadge(badgeId, 'running');
                        var nodeId = targetNodeMap[target] || findNodeForTarget(target);
                        if (nodeId) updatePipelineNode(nodeId, 'active-node');
                        anyRunning = true;
                    } else if (taskInfo.status === 'COMPLETED') {
                        setBadge(badgeId, 'done');
                        var nodeId2 = targetNodeMap[target] || findNodeForTarget(target);
                        if (nodeId2) updatePipelineNode(nodeId2, 'done-node');
                    } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                        setBadge(badgeId, 'error');
                        var nodeId3 = targetNodeMap[target] || findNodeForTarget(target);
                        if (nodeId3) updatePipelineNode(nodeId3, 'error-node');
                    }
                    updateSessionLinks(target, taskInfo);
                }
            }
        }

        // Fall back to file existence checks
        var checks = [
            { file: 'requirements.md', badge: 'badge-requirements', node: 'pnode-requirements' },
         { file: 'requirements_review.md', badge: 'badge-review', node: 'pnode-review' },
            { file: 'generated_app/README.md', badge: 'badge-pipeline', node: 'pnode-pipeline' },
            { file: 'generated_app/index.html', badge: 'badge-ui', node: 'pnode-ui' }
        ];

        for (var i = 0; i < checks.length; i++) {
            var check = checks[i];
            var badge = document.getElementById(check.badge);
            if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
            try {
                var content = await readFile(check.file);
                if (content !== null && content.trim().length > 0) {
                    setBadge(check.badge, 'done');
                    if (check.node) updatePipelineNode(check.node, 'done-node');
                }
            } catch (e) { /* leave as pending */ }
        }

        if (anyRunning) startStatusPolling();
         // Update "Open App" button visibility
         updateOpenAppVisibility();
    }
    // Helper: find badge ID for a target that may not exactly match our map keys
    function findBadgeForTarget(target) {
        var normalized = target.replace(/\/+$/, '');
        for (var key in targetBadgeMap) {
            if (!targetBadgeMap.hasOwnProperty(key)) continue;
            var normalizedKey = key.replace(/\/+$/, '');
            if (normalizedKey === normalized || normalized.endsWith('/' + normalizedKey) || normalizedKey.endsWith('/' + normalized)) {
                return targetBadgeMap[key];
            }
        }
        return null;
    }
    // Helper: find pipeline node ID for a target
    function findNodeForTarget(target) {
        var normalized = target.replace(/\/+$/, '');
        for (var key in targetNodeMap) {
            if (!targetNodeMap.hasOwnProperty(key)) continue;
            var normalizedKey = key.replace(/\/+$/, '');
            if (normalizedKey === normalized || normalized.endsWith('/' + normalizedKey) || normalizedKey.endsWith('/' + normalized)) {
                return targetNodeMap[key];
            }
        }
        return null;
    }


    // === Initialize ===
    loadInitialFiles();
    checkExistingFiles();
    startStatusPolling();
    loadApiProviders();
     // === Open Generated App ===
     function getGeneratedAppUrl(filename) {
         // basePath points to the session file index root, e.g. /myapp/fileIndex/SESSION_ID
         // The generated app files live under generated_app/
         var file = filename || 'app.html';
         return basePath + '/generated_app/' + file;
     }
     function openGeneratedApp(filename) {
         var url = getGeneratedAppUrl(filename);
         window.open(url, '_blank', 'noopener');
     }
     // Show/hide "Open App" buttons based on whether the generated app exists
     async function updateOpenAppVisibility() {
         var appExists = await fileExists('generated_app/app.html');
         var indexExists = !appExists ? await fileExists('generated_app/index.html') : false;
         var hasApp = appExists || indexExists;
         var navBtn = document.getElementById('nav-open-app');
         var generateBtn = document.getElementById('open-app-generate');
         var resultsCard = document.getElementById('open-app-card');
         var indexHtmlBtn = document.getElementById('open-app-index-html');
         if (navBtn) navBtn.style.display = hasApp ? '' : 'none';
         if (generateBtn) generateBtn.style.display = hasApp ? '' : 'none';
         if (resultsCard) resultsCard.style.display = hasApp ? '' : 'none';
         // Store which file to open
         if (navBtn) navBtn.dataset.appFile = appExists ? 'app.html' : 'index.html';
         if (generateBtn) generateBtn.dataset.appFile = appExists ? 'app.html' : 'index.html';
         // Show the index.html button only if app.html also exists (so user can choose)
         if (indexHtmlBtn) {
             if (appExists) {
                 var alsoHasIndex = await fileExists('generated_app/index.html');
                 indexHtmlBtn.style.display = alsoHasIndex ? '' : 'none';
             } else {
                 indexHtmlBtn.style.display = 'none';
             }
         }
     }
     // Attach click handlers for all "Open App" buttons
     document.getElementById('nav-open-app').addEventListener('click', function(e) {
         e.preventDefault();
         openGeneratedApp(this.dataset.appFile || 'app.html');
     });
     document.getElementById('open-app-generate').addEventListener('click', function() {
         openGeneratedApp(this.dataset.appFile || 'app.html');
     });
     document.getElementById('open-app-results').addEventListener('click', function() {
         // Prefer app.html, fall back to index.html
         var navBtn = document.getElementById('nav-open-app');
         openGeneratedApp((navBtn && navBtn.dataset.appFile) || 'app.html');
     });
     document.getElementById('open-app-index-html').addEventListener('click', function() {
         openGeneratedApp('index.html');
     });
     // Check on initial load
     updateOpenAppVisibility();

    // === Git API Functions ===
    var gitApiBase = basePath + '/.git/api';
    async function gitApiCall(endpoint, options) {
        options = options || {};
        var url = gitApiBase + '/' + endpoint;
        var resp = await fetch(url, Object.assign({ credentials: 'include' }, options));
        if (!resp.ok) {
            var errText = '';
            try { errText = await resp.text(); } catch(e) {}
            throw new Error('Git API error (' + resp.status + '): ' + errText);
        }
        return await resp.json();
    }
    // --- Git: Refresh Status ---
    document.getElementById('git-refresh-status').addEventListener('click', async function() {
        await refreshGitStatus();
    });
    async function refreshGitStatus() {
        var display = document.getElementById('git-status-display');
        var statusMsg = document.getElementById('git-status-msg');
        try {
            setStatus('git-status-msg', 'Loading…', 'info');
            var data = await gitApiCall('status');
            if (!data.initialized) {
                display.innerHTML =
                    '<div class="git-status-summary">' +
                    '<span class="git-status-tag tag-uninit">Not Initialized</span>' +
                    '</div>' +
                    '<p style="color:var(--text-muted);">No Git repository found. Click "Initialize Repository" to start tracking changes.</p>';
                setStatus('git-status-msg', '', '');
                return;
            }
            var html = '<div class="git-status-summary">';
            html += '<span class="git-status-tag tag-branch">🌿 ' + escapeHtml(data.currentBranch || 'unknown') + '</span>';
            if (data.clean) {
                html += '<span class="git-status-tag tag-clean">✓ Clean</span>';
            } else {
                html += '<span class="git-status-tag tag-dirty">● Uncommitted changes</span>';
            }
            html += '</div>';
            if (data.changes && data.changes.length > 0) {
                html += '<ul class="git-changes-list">';
                data.changes.forEach(function(change) {
                    var code = change.status || '??';
                    var codeClass = 'code-untracked';
                    if (code === 'M') codeClass = 'code-M';
                    else if (code === 'A') codeClass = 'code-A';
                    else if (code === 'D') codeClass = 'code-D';
                    else if (code === 'R') codeClass = 'code-R';
                    html += '<li><span class="git-change-code ' + codeClass + '">' + escapeHtml(code) + '</span>' +
                            '<span>' + escapeHtml(change.file) + '</span></li>';
                });
                html += '</ul>';
            } else if (!data.clean) {
                html += '<p style="color:var(--text-muted);">Changes detected but no details available.</p>';
            }
            display.innerHTML = html;
            setStatus('git-status-msg', '✓ Status loaded', 'success');
        } catch (e) {
            display.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            setStatus('git-status-msg', '✗ ' + e.message, 'error');
        }
    }
    // --- Git: Initialize ---
    document.getElementById('git-init').addEventListener('click', async function() {
        this.disabled = true;
        try {
            setStatus('git-status-msg', 'Initializing…', 'info');
            var data = await gitApiCall('init', { method: 'POST' });
            if (data.success) {
                setStatus('git-status-msg', '✓ ' + (data.message || 'Repository initialized'), 'success');
                await refreshGitStatus();
            } else {
                setStatus('git-status-msg', '✗ ' + (data.error || 'Init failed'), 'error');
            }
        } catch (e) {
            setStatus('git-status-msg', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // --- Git: Commit ---
    document.getElementById('git-commit').addEventListener('click', async function() {
        var messageInput = document.getElementById('git-commit-message');
        var message = messageInput.value.trim() || 'Auto-commit';
        this.disabled = true;
        try {
            setStatus('git-commit-status', 'Committing…', 'info');
            var data = await gitApiCall('commit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: message })
            });
            if (data.success) {
                var detail = data.commitHash ? ' (' + data.commitHash.substring(0, 7) + ')' : '';
                setStatus('git-commit-status', '✓ ' + (data.message || 'Committed') + detail, 'success');
                messageInput.value = '';
                await refreshGitStatus();
            } else {
                setStatus('git-commit-status', '✗ ' + (data.error || 'Commit failed'), 'error');
            }
        } catch (e) {
            setStatus('git-commit-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // --- Git: Refresh Branches ---
    document.getElementById('git-refresh-branches').addEventListener('click', async function() {
        await refreshGitBranches();
    });
    async function refreshGitBranches() {
        var display = document.getElementById('git-branches-display');
        try {
            var data = await gitApiCall('branches');
            if (!data.branches || data.branches.length === 0) {
                display.innerHTML = '<p class="placeholder">No branches found. Initialize the repository first.</p>';
                return;
            }
            var html = '<ul class="git-branch-list">';
            data.branches.forEach(function(branch) {
                var isCurrent = branch.current;
                html += '<li class="git-branch-item' + (isCurrent ? ' current-branch' : '') + '" data-branch="' + escapeHtml(branch.name) + '">';
                html += '<span class="branch-indicator">' + (isCurrent ? '●' : '○') + '</span>';
                html += '<span>' + escapeHtml(branch.name) + '</span>';
                if (isCurrent) html += '<span style="font-size:0.75rem; color:var(--color-success);"> (current)</span>';
                html += '</li>';
            });
            html += '</ul>';
            display.innerHTML = html;
            // Click to populate branch name input
            display.querySelectorAll('.git-branch-item').forEach(function(item) {
                item.addEventListener('click', function() {
                    var branchName = this.dataset.branch;
                    document.getElementById('git-branch-name').value = branchName;
                    document.getElementById('git-create-branch').checked = false;
                });
            });
        } catch (e) {
            display.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    }
    // --- Git: Checkout ---
    document.getElementById('git-checkout').addEventListener('click', async function() {
        var branchName = document.getElementById('git-branch-name').value.trim();
        var createNew = document.getElementById('git-create-branch').checked;
        if (!branchName) {
            setStatus('git-branch-status', '✗ Please enter a branch name.', 'error');
            return;
        }
        this.disabled = true;
        try {
            setStatus('git-branch-status', 'Checking out…', 'info');
            var data = await gitApiCall('checkout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ branch: branchName, create: createNew })
            });
            if (data.success) {
                setStatus('git-branch-status', '✓ ' + (data.message || 'Checked out ' + branchName), 'success');
                await refreshGitStatus();
                await refreshGitBranches();
            } else {
                setStatus('git-branch-status', '✗ ' + (data.error || 'Checkout failed'), 'error');
            }
        } catch (e) {
            setStatus('git-branch-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    // --- Git: Refresh Log ---
    document.getElementById('git-refresh-log').addEventListener('click', async function() {
        await refreshGitLog();
    });
    async function refreshGitLog() {
        var display = document.getElementById('git-log-display');
        var maxCount = document.getElementById('git-log-count').value || '20';
        try {
            var data = await gitApiCall('log?maxCount=' + maxCount);
            if (!data.commits || data.commits.length === 0) {
                display.innerHTML = '<p class="placeholder">No commits found. Make a commit first.</p>';
                return;
            }
            var html = '';
            data.commits.forEach(function(commit) {
                html += '<div class="git-commit-entry">';
                html += '<div class="git-commit-header">';
                html += '<span class="git-commit-hash">' + escapeHtml((commit.hash || '').substring(0, 10)) + '</span>';
                if (commit.date) {
                    var d = new Date(commit.date);
                    html += '<span class="git-commit-date">' + escapeHtml(d.toLocaleString()) + '</span>';
                }
                if (commit.author && commit.author !== 'SessionFileServlet') {
                    html += '<span class="git-commit-author">by ' + escapeHtml(commit.author) + '</span>';
                }
                html += '</div>';
                html += '<div class="git-commit-msg">' + escapeHtml(commit.message || '(no message)') + '</div>';
                html += '</div>';
            });
            display.innerHTML = html;
        } catch (e) {
            display.innerHTML = '<p class="placeholder" style="color:var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
        }
    }
    // --- ZIP Download ---
    document.getElementById('zip-download-all').addEventListener('click', function() {
        if (!sessionId) {
            alert('No session ID found.');
            return;
        }
        var url = appRoot + '/fileZip?session=' + encodeURIComponent(sessionId) + '&path=' + encodeURIComponent('/');
        window.location.href = url;
    });
    document.getElementById('zip-download-app').addEventListener('click', function() {
        if (!sessionId) {
            alert('No session ID found.');
            return;
        }
        var url = appRoot + '/fileZip?session=' + encodeURIComponent(sessionId) + '&path=' + encodeURIComponent('/generated_app');
        window.location.href = url;
    });
    // === Usage Section ===
    var usageAutoRefreshTimer = null;
    async function fetchUsageData() {
        var url = basePath + '/usage.json';
        var resp = await fetch(url);
        if (!resp.ok) {
            throw new Error('Failed to fetch usage data: ' + resp.status);
        }
        return await resp.json();
    }
    function formatNumber(num) {
        if (num === null || num === undefined) return '0';
        return num.toLocaleString();
    }
    function formatCost(cost) {
        if (cost === null || cost === undefined || cost === 0) return '$0.00';
        if (cost < 0.01) return '$' + cost.toFixed(4);
        return '$' + cost.toFixed(4);
    }
    function renderUsageData(data) {
        var summaryCards = document.getElementById('usage-summary-cards');
        var tableContainer = document.getElementById('usage-table-container');
        var detailsCard = document.getElementById('usage-details-card');
        var rawJson = document.getElementById('usage-raw-json');
        if (!data || !data.models || data.models.length === 0) {
            summaryCards.style.display = 'none';
            tableContainer.innerHTML = '<div class="usage-empty">No usage data recorded yet. Run some operations to see token usage here.</div>';
            detailsCard.style.display = 'none';
            return;
        }
        // Update totals
        var totals = data.totals || {};
        var totalPrompt = totals.prompt_tokens || 0;
        var totalCompletion = totals.completion_tokens || 0;
        var totalTokens = totalPrompt + totalCompletion;
        var totalCost = totals.cost || 0;
        document.getElementById('usage-total-prompt').textContent = formatNumber(totalPrompt);
        document.getElementById('usage-total-completion').textContent = formatNumber(totalCompletion);
        document.getElementById('usage-total-tokens').textContent = formatNumber(totalTokens);
        document.getElementById('usage-total-cost').textContent = formatCost(totalCost);
        summaryCards.style.display = 'block';
        // Find max tokens for bar chart scaling
        var maxTokens = 0;
        data.models.forEach(function(m) {
            var modelTotal = (m.prompt_tokens || 0) + (m.completion_tokens || 0);
            if (modelTotal > maxTokens) maxTokens = modelTotal;
        });
        // Build table
        var html = '<table class="usage-table">';
        html += '<thead><tr>';
        html += '<th>Model</th>';
        html += '<th style="text-align:right;">Prompt Tokens</th>';
        html += '<th style="text-align:right;">Completion Tokens</th>';
        html += '<th>Token Distribution</th>';
        html += '<th style="text-align:right;">Cost</th>';
        html += '</tr></thead>';
        html += '<tbody>';
        // Sort models by cost descending
        var sortedModels = data.models.slice().sort(function(a, b) {
            return (b.cost || 0) - (a.cost || 0);
        });
        sortedModels.forEach(function(model) {
            var prompt = model.prompt_tokens || 0;
            var completion = model.completion_tokens || 0;
            var modelTotal = prompt + completion;
            var promptPct = maxTokens > 0 ? (prompt / maxTokens * 100) : 0;
            var completionPct = maxTokens > 0 ? (completion / maxTokens * 100) : 0;
            html += '<tr>';
            html += '<td class="usage-cell-model">' + escapeHtml(model.model || 'unknown') + '</td>';
            html += '<td class="usage-cell-number">' + formatNumber(prompt) + '</td>';
            html += '<td class="usage-cell-number">' + formatNumber(completion) + '</td>';
            html += '<td>';
            html += '<div class="usage-bar-container">';
            html += '<div class="usage-bar" title="Prompt: ' + formatNumber(prompt) + ' / Completion: ' + formatNumber(completion) + '">';
            html += '<div class="usage-bar-fill bar-prompt" style="width:' + promptPct.toFixed(1) + '%; display:inline-block;"></div>';
            html += '<div class="usage-bar-fill bar-completion" style="width:' + completionPct.toFixed(1) + '%; display:inline-block;"></div>';
            html += '</div>';
            html += '<span class="usage-bar-label">' + formatNumber(modelTotal) + '</span>';
            html += '</div>';
            html += '</td>';
            html += '<td class="usage-cell-cost">' + formatCost(model.cost) + '</td>';
            html += '</tr>';
        });
        // Totals row
        html += '<tr class="usage-row-total">';
        html += '<td class="usage-cell-model" style="color:var(--text-primary);">Total</td>';
        html += '<td class="usage-cell-number">' + formatNumber(totalPrompt) + '</td>';
        html += '<td class="usage-cell-number">' + formatNumber(totalCompletion) + '</td>';
        html += '<td>';
        html += '<div class="usage-bar-container">';
        var totalPromptPct = totalTokens > 0 ? (totalPrompt / totalTokens * 100) : 0;
        var totalCompletionPct = totalTokens > 0 ? (totalCompletion / totalTokens * 100) : 0;
        html += '<div class="usage-bar">';
        html += '<div class="usage-bar-fill bar-prompt" style="width:' + totalPromptPct.toFixed(1) + '%; display:inline-block;"></div>';
        html += '<div class="usage-bar-fill bar-completion" style="width:' + totalCompletionPct.toFixed(1) + '%; display:inline-block;"></div>';
        html += '</div>';
        html += '<span class="usage-bar-label">' + formatNumber(totalTokens) + '</span>';
        html += '</div>';
        html += '</td>';
        html += '<td class="usage-cell-cost">' + formatCost(totalCost) + '</td>';
        html += '</tr>';
        html += '</tbody></table>';
        // Legend
        html += '<div style="display:flex; gap:1.5rem; margin-top:0.75rem; font-size:0.8rem; color:var(--text-muted);">';
        html += '<span><span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:var(--color-accent);margin-right:0.3rem;vertical-align:middle;"></span>Prompt tokens</span>';
        html += '<span><span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:var(--color-purple);margin-right:0.3rem;vertical-align:middle;"></span>Completion tokens</span>';
        html += '</div>';
        // Last updated
        html += '<div class="usage-last-updated">Last updated: ' + new Date().toLocaleTimeString() + '</div>';
        tableContainer.innerHTML = html;
        // Show raw JSON
        rawJson.textContent = JSON.stringify(data, null, 2);
        detailsCard.style.display = 'block';
    }
    // Refresh usage button
    document.getElementById('usage-refresh').addEventListener('click', async function() {
        this.disabled = true;
        setStatus('usage-status', 'Loading usage data…', 'info');
        try {
            var data = await fetchUsageData();
            renderUsageData(data);
            setStatus('usage-status', '✓ Usage data loaded', 'success');
        } catch (e) {
            setStatus('usage-status', '✗ ' + e.message, 'error');
            document.getElementById('usage-table-container').innerHTML =
                '<div class="usage-empty" style="color:var(--color-danger);">Failed to load usage data: ' + escapeHtml(e.message) + '</div>';
        } finally {
            this.disabled = false;
        }
    });
    // Auto-refresh toggle
    document.getElementById('usage-auto-refresh').addEventListener('change', function() {
        if (this.checked) {
            // Immediately refresh, then set interval
            document.getElementById('usage-refresh').click();
            usageAutoRefreshTimer = setInterval(async function() {
                try {
                    var data = await fetchUsageData();
                    renderUsageData(data);
                } catch (e) {
                    // Silently fail on auto-refresh
                }
            }, 30000);
        } else {
            if (usageAutoRefreshTimer) {
                clearInterval(usageAutoRefreshTimer);
                usageAutoRefreshTimer = null;
            }
        }
    });
    // Raw JSON collapsible toggle
    document.getElementById('usage-raw-toggle').addEventListener('click', function() {
        this.classList.toggle('open');
        document.getElementById('usage-raw-body').classList.toggle('open');
    });


})();