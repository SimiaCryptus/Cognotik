(function () {
    'use strict';
     // ========================================================================
     // Model Management
     // ========================================================================
     var availableModels = {};
     async function loadApiProviders() {
         try {
             var response = await fetch('/apiProviders/?format=json');
             if (response.status >= 400) {
                 console.warn('Could not load API providers:', response.status);
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

             populateModelDropdowns();
             renderProviderInfo();
         } catch (e) {
             console.warn('Failed to load API providers:', e);
         }
     }

     function populateModelDropdowns() {
         var smartSelect = document.getElementById('smart-model-select');
         var fastSelect = document.getElementById('fast-model-select');
         var imageSelect = document.getElementById('image-model-select');

         if (!smartSelect || !fastSelect || !imageSelect) return;

         var selects = [smartSelect, fastSelect, imageSelect];
         selects.forEach(function (sel) { sel.innerHTML = ''; });

         // Add default/empty option
         selects.forEach(function (sel) {
             var opt = document.createElement('option');
             opt.value = '';
             opt.textContent = '— Server Default —';
             sel.appendChild(opt);
         });

         var addedModels = {};
         var totalModels = 0;

         for (var provider in availableModels) {
             if (!availableModels.hasOwnProperty(provider)) continue;
             var models = availableModels[provider];

             // Create optgroup for each provider
             var groups = selects.map(function () {
                 var group = document.createElement('optgroup');
                 group.label = provider;
                 return group;
             });

             var groupHasModels = false;

             models.forEach(function (model) {
                 if (addedModels[model.id]) return;
                 addedModels[model.id] = true;
                 groupHasModels = true;
                 totalModels++;

                 groups.forEach(function (group) {
                     var option = document.createElement('option');
                     option.value = model.id;
                     option.textContent = model.name;
                     if (model.description) {
                         option.title = model.description;
                     }
                     group.appendChild(option);
                 });
             });

             if (groupHasModels) {
                 selects.forEach(function (sel, idx) {
                     sel.appendChild(groups[idx]);
                 });
             }
         }

         if (totalModels === 0) {
             selects.forEach(function (sel) {
                 var opt = document.createElement('option');
                 opt.value = '';
                 opt.textContent = 'No models available — configure API keys';
                 opt.disabled = true;
                 sel.appendChild(opt);
             });
         }

         // Restore saved selections
         var savedSmart = localStorage.getItem('philcalc_smartModel');
         var savedFast = localStorage.getItem('philcalc_fastModel');
         var savedImage = localStorage.getItem('philcalc_imageModel');

         if (savedSmart && hasOption(smartSelect, savedSmart)) smartSelect.value = savedSmart;
         if (savedFast && hasOption(fastSelect, savedFast)) fastSelect.value = savedFast;
         if (savedImage && hasOption(imageSelect, savedImage)) imageSelect.value = savedImage;
     }

     function hasOption(selectEl, value) {
         for (var i = 0; i < selectEl.options.length; i++) {
             if (selectEl.options[i].value === value) return true;
         }
         return false;
     }

     function renderProviderInfo() {
         var container = document.getElementById('provider-info');
         if (!container) return;

         var providerNames = Object.keys(availableModels);
         if (providerNames.length === 0) {
             container.innerHTML = '<p class="placeholder">No API providers configured. Please set up API keys in the main application settings.</p>';
             return;
         }

         var html = '<div class="provider-list">';
         providerNames.forEach(function (name) {
             var models = availableModels[name];
             html += '<div class="provider-item">';
             html += '<div class="provider-name">🔌 ' + escapeHtml(name) + ' <span class="provider-model-count">(' + models.length + ' model' + (models.length !== 1 ? 's' : '') + ')</span></div>';
             html += '<div class="provider-models">';
             models.forEach(function (model) {
                 html += '<span class="provider-model-tag" title="' + escapeHtml(model.description) + '">' + escapeHtml(model.name) + '</span>';
             });
             html += '</div>';
             html += '</div>';
         });
         html += '</div>';
         container.innerHTML = html;
     }

     function getSelectedModels() {
         var smartSelect = document.getElementById('smart-model-select');
         var fastSelect = document.getElementById('fast-model-select');
         var imageSelect = document.getElementById('image-model-select');
         return {
             smartModel: smartSelect ? smartSelect.value : '',
             fastModel: fastSelect ? fastSelect.value : '',
             imageModel: imageSelect ? imageSelect.value : ''
         };
     }

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
    // Viewer State (declared early so all handlers can access)
    // ========================================================================
    var viewerRawContent = {}; // viewerId -> raw markdown string
    var viewerModes = {};      // viewerId -> 'rendered' | 'markdown'
    var zoomedViewerId = null;


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
         var models = getSelectedModels();
         var url = '/docops?sessionId=' + encodeURIComponent(sessionId) +
            '&doc=' + encodeURIComponent(opPath) +
            '&target=' + encodeURIComponent(targetPath);
         if (models.smartModel) url += '&smartModel=' + encodeURIComponent(models.smartModel);
         if (models.fastModel) url += '&fastModel=' + encodeURIComponent(models.fastModel);
         if (models.imageModel) url += '&imageModel=' + encodeURIComponent(models.imageModel);
        var resp = await fetch(url, { method: 'POST' });
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
        'technical_explanation.md': 'badge-technical',
         'illustrated_content.md': 'badge-illustration',
         'page.html': 'badge-webpage'
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
     document.getElementById('save-model-settings').addEventListener('click', function () {
         var models = getSelectedModels();
         localStorage.setItem('philcalc_smartModel', models.smartModel);
         localStorage.setItem('philcalc_fastModel', models.fastModel);
         localStorage.setItem('philcalc_imageModel', models.imageModel);
         setStatus('model-status', '✓ Model settings saved', 'success');
     });
     document.getElementById('reload-models').addEventListener('click', async function () {
         this.disabled = true;
         setStatus('model-status', 'Loading models…', '');
         try {
             await loadApiProviders();
             setStatus('model-status', '✓ Models reloaded', 'success');
         } catch (e) {
             setStatus('model-status', '✗ ' + e.message, 'error');
         } finally {
             this.disabled = false;
         }
     });

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
                viewerRawContent[viewerId] = null;
            } else {
                viewerRawContent[viewerId] = content;
                viewerModes[viewerId] = viewerModes[viewerId] || 'rendered';
                renderViewerContent(viewerId);
                ensureViewerToolbar(viewerId);
            }
            viewer.classList.add('visible');
        } catch (e) {
            viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            viewerRawContent[viewerId] = null;
            viewer.classList.add('visible');
        }
    }

    document.querySelectorAll('.btn-view').forEach(function (btn) {
        btn.addEventListener('click', function () {
            viewFile(this.dataset.file, this.dataset.viewer);
        });
    });

    // Results refresh buttons
    document.querySelectorAll('.btn-refresh[data-file]').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var viewerId = this.dataset.viewer;
            var viewer = document.getElementById(viewerId);
            if (!viewer) return;
            try {
                var content = await readFile(this.dataset.file);
                if (content === null) {
                    viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                    viewerRawContent[viewerId] = null;
                } else {
                    viewerRawContent[viewerId] = content;
                    viewerModes[viewerId] = viewerModes[viewerId] || 'rendered';
                    renderViewerContent(viewerId);
                    ensureViewerToolbar(viewerId);
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
                viewerRawContent[viewerId] = null;
            }
        });
    });
    // Auto-load results when switching to Results tab
    document.querySelectorAll('.results-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            // After switching tab, auto-refresh the active panel's viewer if it has no content
            var tabId = this.dataset.tab;
            var panel = document.getElementById(tabId);
            if (!panel) return;
            var refreshBtn = panel.querySelector('.btn-refresh[data-file]');
            if (refreshBtn) {
                var viewerId = refreshBtn.dataset.viewer;
                var viewer = document.getElementById(viewerId);
                if (viewer && (!viewerRawContent[viewerId])) {
                    refreshBtn.click();
                }
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
                            viewerRawContent[viewerId] = content;
                            viewerModes[viewerId] = viewerModes[viewerId] || 'rendered';
                            renderViewerContent(viewerId);
                            ensureViewerToolbar(viewerId);
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
                                viewerRawContent[step.viewer] = content;
                                viewerModes[step.viewer] = viewerModes[step.viewer] || 'rendered';
                                renderViewerContent(step.viewer);
                                ensureViewerToolbar(step.viewer);
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

    var lensDefinitions = [
        { key: 'brainstorm', op: 'ops/brainstorm_op.md', output: 'brainstorm.md', badge: 'badge-brainstorm', viewer: 'viewer-brainstorm', label: 'Brainstorm' },
        { key: 'dialectical', op: 'ops/dialectical_op.md', output: 'dialectical.md', badge: 'badge-dialectical', viewer: 'viewer-dialectical', label: 'Dialectical Analysis' },
        { key: 'socratic', op: 'ops/socratic_op.md', output: 'socratic.md', badge: 'badge-socratic', viewer: 'viewer-socratic', label: 'Socratic Dialogue' },
        { key: 'perspectives', op: 'ops/perspectives_op.md', output: 'perspectives.md', badge: 'badge-perspectives', viewer: 'viewer-perspectives', label: 'Multi-Perspective Analysis' },
        { key: 'persuasive', op: 'ops/persuasive_op.md', output: 'persuasive.md', badge: 'badge-persuasive', viewer: 'viewer-persuasive', label: 'Persuasive Essay' },
        { key: 'gametheory', op: 'ops/gametheory_op.md', output: 'gametheory.md', badge: 'badge-gametheory', viewer: 'viewer-gametheory', label: 'Game Theory Analysis' },
        { key: 'narrative', op: 'ops/narrative_op.md', output: 'narrative.md', badge: 'badge-narrative', viewer: 'viewer-narrative', label: 'Narrative Dramatization' },
        { key: 'comic', op: 'ops/comic_op.md', output: 'comic.md', badge: 'badge-comic', viewer: 'viewer-comic', label: 'Comic Book Generation' },
        { key: 'technical', op: 'ops/technical_explanation_op.md', output: 'technical_explanation.md', badge: 'badge-technical', viewer: 'viewer-technical', label: 'Technical Explanation' },
         { key: 'webpage', op: 'ops/webpage_op.md', output: 'page.html', badge: 'badge-webpage', viewer: 'viewer-webpage', label: 'Webpage Generation' }
    ];







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
            { file: 'technical_explanation.md', badge: 'badge-technical' },
             { file: 'illustrated_content.md', badge: 'badge-illustration' },
             { file: 'page.html', badge: 'badge-webpage' }
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
     loadApiProviders();
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
            var resp = await fetch(basePath + '/notes/_files.json');
            if (!resp.ok) {
                uploadedFilesList.innerHTML = '';
                return;
            }
            var files = [];
            try {
                var json = await resp.json();
                var entries = json.entries || json.files || json.children || [];
                if (Array.isArray(entries)) {
                    files = entries.filter(function (e) {
                        // Only include files, not directories
                        if (typeof e === 'string') return true;
                        if (e && e.type === 'file') return true;
                        if (e && !e.type && e.name) return true; // assume file if no type
                        return false;
                    }).map(function (e) {
                        if (typeof e === 'string') return e;
                        return e.name || e.fileName || String(e);
                    });
                }
            } catch (e) {
                console.warn('Could not parse _files.json response:', e);
            }
            // Filter out meta files
            files = files.filter(function (f) {
                return f && f !== '.' && f !== '..' && f !== '_files.json' && f !== '.gitignore';
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
    // ========================================================================
    // Viewer Mode Toggle (Markdown source vs Rendered HTML) & Zoom
    // ========================================================================
    function renderViewerContent(viewerId) {
        var viewer = document.getElementById(viewerId);
        if (!viewer) return;
        var raw = viewerRawContent[viewerId];
        if (raw === undefined || raw === null) return;
        var mode = viewerModes[viewerId] || 'rendered';
        if (mode === 'markdown') {
            viewer.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
        } else {
            var html = renderMarkdown(raw);
            if (!html || html.trim() === '') {
                // Fallback: if renderMarkdown returns empty, show as pre-formatted text
                viewer.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
            } else {
                viewer.innerHTML = html;
            }
        }
    }
    function ensureViewerToolbar(viewerId) {
        var toolbar = document.getElementById('toolbar-' + viewerId);
        if (toolbar) return toolbar;
        var viewer = document.getElementById(viewerId);
        if (!viewer || !viewer.parentElement) return null;
        toolbar = document.createElement('div');
        toolbar.id = 'toolbar-' + viewerId;
        toolbar.className = 'viewer-toolbar';
        var currentMode = viewerModes[viewerId] || 'rendered';
        var btnLabel = currentMode === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
        toolbar.innerHTML =
            '<button class="btn btn-sm btn-toolbar btn-toggle-mode" data-viewer="' + viewerId + '" title="Toggle Markdown / Rendered">' + btnLabel + '</button>' +
            '<button class="btn btn-sm btn-toolbar btn-zoom" data-viewer="' + viewerId + '" title="Zoom / Fullscreen">' +
            '🔍 Zoom</button>';
        viewer.parentElement.insertBefore(toolbar, viewer);
        // Attach toggle handler
        toolbar.querySelector('.btn-toggle-mode').addEventListener('click', function () {
            var vid = this.dataset.viewer;
            var current = viewerModes[vid] || 'rendered';
            viewerModes[vid] = current === 'rendered' ? 'markdown' : 'rendered';
            this.textContent = viewerModes[vid] === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
            renderViewerContent(vid);
            // Also update zoom overlay if open
            if (zoomedViewerId === vid) {
                var zoomBody = document.getElementById('zoom-overlay-body');
                if (zoomBody) {
                    var raw = viewerRawContent[vid];
                    if (viewerModes[vid] === 'markdown') {
                        zoomBody.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
                    } else {
                        zoomBody.innerHTML = renderMarkdown(raw);
                    }
                }
            }
        });
        // Attach zoom handler
        toolbar.querySelector('.btn-zoom').addEventListener('click', function () {
            var vid = this.dataset.viewer;
            openZoomOverlay(vid);
        });
        return toolbar;
    }
    function openZoomOverlay(viewerId) {
        var raw = viewerRawContent[viewerId];
        if (raw === undefined || raw === null) return;
        zoomedViewerId = viewerId;
        var overlay = document.getElementById('zoom-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'zoom-overlay';
            overlay.className = 'zoom-overlay';
            overlay.innerHTML =
                '<div class="zoom-overlay-header">' +
                '<span class="zoom-title" id="zoom-overlay-title"></span>' +
                '<div class="zoom-header-buttons">' +
                '<button class="btn btn-sm btn-toolbar" id="zoom-toggle-mode">📝 Markdown</button>' +
                '<button class="btn btn-sm btn-toolbar" id="zoom-close-btn">✕ Close</button>' +
                '</div>' +
                '</div>' +
                '<div class="zoom-overlay-body" id="zoom-overlay-body"></div>';
            document.body.appendChild(overlay);
            document.getElementById('zoom-close-btn').addEventListener('click', function () {
                closeZoomOverlay();
            });
            document.getElementById('zoom-toggle-mode').addEventListener('click', function () {
                if (!zoomedViewerId) return;
                var current = viewerModes[zoomedViewerId] || 'rendered';
                viewerModes[zoomedViewerId] = current === 'rendered' ? 'markdown' : 'rendered';
                this.textContent = viewerModes[zoomedViewerId] === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
                // Update both zoom and inline viewer
                renderViewerContent(zoomedViewerId);
                var zoomBody = document.getElementById('zoom-overlay-body');
                var raw2 = viewerRawContent[zoomedViewerId];
                if (viewerModes[zoomedViewerId] === 'markdown') {
                    zoomBody.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw2) + '</pre>';
                } else {
                    zoomBody.innerHTML = renderMarkdown(raw2);
                }
                // Sync inline toolbar button text
                var inlineBtn = document.querySelector('#toolbar-' + zoomedViewerId + ' .btn-toggle-mode');
                if (inlineBtn) {
                    inlineBtn.textContent = viewerModes[zoomedViewerId] === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
                }
            });
            // Close on Escape
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape' && zoomedViewerId) closeZoomOverlay();
            });
        }
        var mode = viewerModes[viewerId] || 'rendered';
        var zoomBody = document.getElementById('zoom-overlay-body');
        var zoomTitle = document.getElementById('zoom-overlay-title');
        var zoomToggle = document.getElementById('zoom-toggle-mode');
        zoomTitle.textContent = viewerId.replace(/^(viewer-|result-)/, '').replace(/-/g, ' ');
        zoomToggle.textContent = mode === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
        if (mode === 'markdown') {
            zoomBody.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
        } else {
            zoomBody.innerHTML = renderMarkdown(raw);
        }
        overlay.classList.add('visible');
        document.body.style.overflow = 'hidden';
    }
    function closeZoomOverlay() {
        var overlay = document.getElementById('zoom-overlay');
        if (overlay) {
            overlay.classList.remove('visible');
        }
        document.body.style.overflow = '';
        zoomedViewerId = null;
    }
    // ========================================================================
    // Content Editor (content.md)
    // ========================================================================
    var contentEditorEl = document.getElementById('content-editor');
    var contentEditorContainer = document.getElementById('content-editor-container');
    document.getElementById('edit-content-btn').addEventListener('click', async function () {
        // Load current content.md into editor
        try {
            var content = await readFile('content.md');
            contentEditorEl.value = content || '';
        } catch (e) {
            contentEditorEl.value = '';
        }
        contentEditorContainer.classList.add('visible');
    });
    document.getElementById('save-content-btn').addEventListener('click', async function () {
        var content = contentEditorEl.value;
        try {
            this.disabled = true;
            await writeFile('content.md', content);
            setStatus('content-editor-status', '✓ Saved content.md', 'success');
            // Refresh any open viewers showing content.md
            var viewerIds = ['viewer-content', 'viewer-update', 'result-content'];
            for (var i = 0; i < viewerIds.length; i++) {
                var vid = viewerIds[i];
                viewerRawContent[vid] = content;
                var viewer = document.getElementById(vid);
                if (viewer && viewer.classList.contains('visible')) {
                    renderViewerContent(vid);
                }
            }
        } catch (e) {
            setStatus('content-editor-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
    document.getElementById('close-content-editor-btn').addEventListener('click', function () {
        contentEditorContainer.classList.remove('visible');
    });
    // ========================================================================
    // Pipeline Manual Article Editor
    // ========================================================================
    var pipelineEditorEl = document.getElementById('pipeline-content-editor');
    var pipelineEditorContainer = document.getElementById('pipeline-content-editor-container');
    var pipelineEditorDirty = false;
    // Open editor
    document.getElementById('pipeline-edit-content-btn').addEventListener('click', async function () {
        if (pipelineEditorContainer.classList.contains('visible')) {
            // Toggle off
            if (pipelineEditorDirty && !confirm('You have unsaved changes. Close without saving?')) return;
            pipelineEditorContainer.classList.remove('visible');
            pipelineEditorDirty = false;
            return;
        }
        try {
            var content = await readFile('content.md');
            pipelineEditorEl.value = content || '';
        } catch (e) {
            pipelineEditorEl.value = '';
        }
        pipelineEditorContainer.classList.add('visible');
        pipelineEditorDirty = false;
        updatePipelineWordCount();
        pipelineEditorEl.focus();
    });
    // Track dirty state
    pipelineEditorEl.addEventListener('input', function () {
        pipelineEditorDirty = true;
        updatePipelineWordCount();
    });
    // Word count
    function updatePipelineWordCount() {
        var el = document.getElementById('pipeline-editor-wordcount');
        if (!el) return;
        var text = pipelineEditorEl.value.trim();
        if (!text) {
            el.textContent = '0 words';
            return;
        }
        var words = text.split(/\s+/).length;
        var chars = text.length;
        el.textContent = words.toLocaleString() + ' words · ' + chars.toLocaleString() + ' chars';
    }
    // Save function (shared)
    async function savePipelineContent(closeAfter) {
        var content = pipelineEditorEl.value;
        try {
            await writeFile('content.md', content);
            pipelineEditorDirty = false;
            setStatus('pipeline-editor-status', '✓ Saved content.md', 'success');
            setStatus('pipeline-editor-status-bottom', '✓ Saved content.md', 'success');
            // Update badge
            if (content.trim().length > 0) {
                setBadge('badge-content', 'done');
            }
            // Sync all viewers that show content.md
            var viewerIds = ['viewer-content', 'viewer-update', 'viewer-manual-content', 'result-content'];
            for (var i = 0; i < viewerIds.length; i++) {
                var vid = viewerIds[i];
                viewerRawContent[vid] = content;
                var viewer = document.getElementById(vid);
                if (viewer && (viewer.classList.contains('visible') || vid === 'result-content')) {
                    viewerModes[vid] = viewerModes[vid] || 'rendered';
                    renderViewerContent(vid);
                }
            }
            // Also sync the Results tab editor if it exists
            if (contentEditorEl) {
                contentEditorEl.value = content;
            }
            if (closeAfter) {
                pipelineEditorContainer.classList.remove('visible');
            }
        } catch (e) {
            setStatus('pipeline-editor-status', '✗ ' + e.message, 'error');
            setStatus('pipeline-editor-status-bottom', '✗ ' + e.message, 'error');
        }
    }
    // Save buttons (top and bottom)
    document.getElementById('pipeline-save-content-btn').addEventListener('click', function () {
        savePipelineContent(false);
    });
    document.getElementById('pipeline-save-content-btn-bottom').addEventListener('click', function () {
        savePipelineContent(false);
    });
    document.getElementById('pipeline-save-close-content-btn').addEventListener('click', function () {
        savePipelineContent(true);
    });
    document.getElementById('pipeline-save-close-content-btn-bottom').addEventListener('click', function () {
        savePipelineContent(true);
    });
    // Close button
    document.getElementById('pipeline-close-editor-btn').addEventListener('click', function () {
        if (pipelineEditorDirty && !confirm('You have unsaved changes. Close without saving?')) return;
        pipelineEditorContainer.classList.remove('visible');
        pipelineEditorDirty = false;
    });
    // Markdown formatting toolbar
    document.querySelectorAll('.pipeline-editor-tool').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var action = this.dataset.action;
            var ta = pipelineEditorEl;
            var start = ta.selectionStart;
            var end = ta.selectionEnd;
            var selected = ta.value.substring(start, end);
            var before = ta.value.substring(0, start);
            var after = ta.value.substring(end);
            var insert = '';
            var cursorOffset = 0;
            switch (action) {
                case 'heading':
                    insert = '\n## ' + (selected || 'Heading') + '\n';
                    cursorOffset = selected ? insert.length : 4;
                    break;
                case 'bold':
                    insert = '**' + (selected || 'bold text') + '**';
                    cursorOffset = selected ? insert.length : 2;
                    break;
                case 'italic':
                    insert = '*' + (selected || 'italic text') + '*';
                    cursorOffset = selected ? insert.length : 1;
                    break;
                case 'bullet':
                    if (selected) {
                        insert = selected.split('\n').map(function (line) {
                            return '- ' + line;
                        }).join('\n');
                    } else {
                        insert = '\n- Item 1\n- Item 2\n- Item 3\n';
                    }
                    cursorOffset = insert.length;
                    break;
                case 'quote':
                    if (selected) {
                        insert = selected.split('\n').map(function (line) {
                            return '> ' + line;
                        }).join('\n');
                    } else {
                        insert = '\n> Quote text here\n';
                    }
                    cursorOffset = insert.length;
                    break;
                case 'code':
                    insert = '\n```\n' + (selected || 'code here') + '\n```\n';
                    cursorOffset = selected ? insert.length : 5;
                    break;
                case 'hr':
                    insert = '\n---\n';
                    cursorOffset = insert.length;
                    break;
            }
            ta.value = before + insert + after;
            ta.selectionStart = start + cursorOffset;
            ta.selectionEnd = start + cursorOffset;
            ta.focus();
            pipelineEditorDirty = true;
            updatePipelineWordCount();
        });
    });
    // Keyboard shortcut: Ctrl+S / Cmd+S to save
    pipelineEditorEl.addEventListener('keydown', function (e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            e.preventDefault();
            savePipelineContent(false);
        }
    });
    // ========================================================================
    // Usage Tracking
    // ========================================================================
    var usagePollTimer = null;
    var USAGE_POLL_INTERVAL = 10000;
    function getUsageUrls() {
        if (!sessionId) return { html: null, json: null };
        return {
            html: '/proxy/usage?sessionId=' + encodeURIComponent(sessionId),
            json: '/proxy/usage?sessionId=' + encodeURIComponent(sessionId) + '&format=json'
        };
    }
    function updateUsageLinks() {
        var urls = getUsageUrls();
        var htmlLink = document.getElementById('usage-html-link');
        var jsonLink = document.getElementById('usage-json-link');
        if (htmlLink) {
            if (urls.html) {
                htmlLink.href = urls.html;
                htmlLink.classList.remove('btn-disabled');
            } else {
                htmlLink.href = '#';
                htmlLink.classList.add('btn-disabled');
            }
        }
        if (jsonLink) {
            if (urls.json) {
                jsonLink.href = urls.json;
                jsonLink.classList.remove('btn-disabled');
            } else {
                jsonLink.href = '#';
                jsonLink.classList.add('btn-disabled');
            }
        }
    }
    async function fetchUsageData() {
        var urls = getUsageUrls();
        if (!urls.json) {
            setStatus('usage-status', 'No session ID available', 'error');
            return null;
        }
        try {
            var resp = await fetch(urls.json, {
                headers: { 'Accept': 'application/json' }
            });
            if (!resp.ok) {
                if (resp.status === 404) return null;
                throw new Error('HTTP ' + resp.status);
            }
            return await resp.json();
        } catch (e) {
            console.warn('Failed to fetch usage data:', e);
            return null;
        }
    }
    function formatTokenCount(n) {
        if (n === undefined || n === null) return '—';
        return Number(n).toLocaleString();
    }
    function formatCost(n) {
        if (n === undefined || n === null) return '—';
        if (n === 0) return '$0.00';
        if (n < 0.01) return '$' + n.toFixed(4);
        return '$' + n.toFixed(4);
    }
    function renderUsageData(data) {
        var promptEl = document.getElementById('usage-total-prompt');
        var completionEl = document.getElementById('usage-total-completion');
        var costEl = document.getElementById('usage-total-cost');
        var tableContainer = document.getElementById('usage-table-container');
        if (!data) {
            if (promptEl) promptEl.textContent = '—';
            if (completionEl) completionEl.textContent = '—';
            if (costEl) costEl.textContent = '—';
            if (tableContainer) tableContainer.innerHTML = '<p class="placeholder">No usage data available yet. Run some operations first.</p>';
            return;
        }
        // Update summary cards
        var totals = data.totals || {};
        if (promptEl) promptEl.textContent = formatTokenCount(totals.prompt_tokens);
        if (completionEl) completionEl.textContent = formatTokenCount(totals.completion_tokens);
        if (costEl) costEl.textContent = formatCost(totals.cost);
        // Update table
        var models = data.models || [];
        if (models.length === 0) {
            if (tableContainer) tableContainer.innerHTML = '<p class="placeholder">No model usage recorded yet.</p>';
            return;
        }
        var html = '<table class="usage-table">';
        html += '<thead><tr>';
        html += '<th>Model</th>';
        html += '<th>Prompt Tokens</th>';
        html += '<th>Completion Tokens</th>';
        html += '<th>Total Tokens</th>';
        html += '<th>Cost</th>';
        html += '</tr></thead>';
        html += '<tbody>';
        for (var i = 0; i < models.length; i++) {
            var m = models[i];
            var totalTokens = (m.prompt_tokens || 0) + (m.completion_tokens || 0);
            html += '<tr>';
            html += '<td class="usage-model-cell">' + escapeHtml(m.model || 'Unknown') + '</td>';
            html += '<td class="usage-number-cell">' + formatTokenCount(m.prompt_tokens) + '</td>';
            html += '<td class="usage-number-cell">' + formatTokenCount(m.completion_tokens) + '</td>';
            html += '<td class="usage-number-cell">' + formatTokenCount(totalTokens) + '</td>';
            html += '<td class="usage-cost-cell">' + formatCost(m.cost) + '</td>';
            html += '</tr>';
        }
        // Totals row
        var totalAllTokens = (totals.prompt_tokens || 0) + (totals.completion_tokens || 0);
        html += '<tr class="usage-totals-row">';
        html += '<td class="usage-model-cell"><strong>Total</strong></td>';
        html += '<td class="usage-number-cell"><strong>' + formatTokenCount(totals.prompt_tokens) + '</strong></td>';
        html += '<td class="usage-number-cell"><strong>' + formatTokenCount(totals.completion_tokens) + '</strong></td>';
        html += '<td class="usage-number-cell"><strong>' + formatTokenCount(totalAllTokens) + '</strong></td>';
        html += '<td class="usage-cost-cell"><strong>' + formatCost(totals.cost) + '</strong></td>';
        html += '</tr>';
        html += '</tbody></table>';
        // Add last-updated timestamp
        html += '<div class="usage-last-updated">Last updated: ' + new Date().toLocaleTimeString() + '</div>';
        if (tableContainer) tableContainer.innerHTML = html;
    }
    async function refreshUsage() {
        setStatus('usage-status', 'Loading…', '');
        try {
            var data = await fetchUsageData();
            renderUsageData(data);
            if (data) {
                setStatus('usage-status', '✓ Updated', 'success');
            } else {
                setStatus('usage-status', 'No data yet', '');
            }
        } catch (e) {
            setStatus('usage-status', '✗ ' + e.message, 'error');
        }
    }
    function startUsagePolling() {
        if (usagePollTimer) return;
        var autoRefresh = document.getElementById('usage-auto-refresh');
        if (autoRefresh && !autoRefresh.checked) return;
        usagePollTimer = setInterval(function () {
            var autoRefresh = document.getElementById('usage-auto-refresh');
            if (autoRefresh && !autoRefresh.checked) {
                stopUsagePolling();
                return;
            }
            // Only refresh if the usage section is visible
            var section = document.getElementById('section-usage');
            if (section && section.classList.contains('active')) {
                refreshUsage();
            }
        }, USAGE_POLL_INTERVAL);
    }
    function stopUsagePolling() {
        if (usagePollTimer) {
            clearInterval(usagePollTimer);
            usagePollTimer = null;
        }
    }
    // Refresh button
    document.getElementById('refresh-usage').addEventListener('click', function () {
        refreshUsage();
    });
    // Auto-refresh checkbox
    document.getElementById('usage-auto-refresh').addEventListener('change', function () {
        if (this.checked) {
            startUsagePolling();
        } else {
            stopUsagePolling();
        }
    });
    // Load usage when switching to Usage tab
    document.querySelectorAll('.nav-link').forEach(function (link) {
        link.addEventListener('click', function () {
            if (this.dataset.section === 'section-usage') {
                refreshUsage();
                startUsagePolling();
            }
        });
    });
    // Initialize usage links
    updateUsageLinks();
     // Initialize webpage open link
     var webpageOpenLink = document.getElementById('webpage-open-link');
     if (webpageOpenLink) {
         webpageOpenLink.href = basePath + '/page.html';
     }


})();