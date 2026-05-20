(function () {
    'use strict';

    // === URL Parsing & Session Setup ===
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';
    let appId = '';

    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
        appId = pathParts[fileIndexIdx - 1] || 'health-improvement';
    } else {
        console.warn('Could not determine session from URL path. File operations may fail.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }

    const proxyBase = '/proxy/';

    function getProxyUrl(taskSessionId) {
        return proxyBase + '#' + taskSessionId;
    }

    // === Available models (loaded from server) ===
    let availableModels = {};

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
            providers.forEach(function (provider) {
                if (provider.models && provider.models.length > 0) {
                    availableModels[provider.name] = provider.models.map(function (model) {
                        return {
                            id: model.name,
                            name: model.name,
                        };
                    });
                }
            });
        } catch (e) {
            console.warn('Failed to load API providers:', e);
        }
    }

    function populateModelDropdowns() {
        var selects = [
            document.getElementById('smart-model-select'),
            document.getElementById('fast-model-select'),
            document.getElementById('settings-smart-model'),
            document.getElementById('settings-fast-model')
        ];
        selects.forEach(function (sel) {
            if (!sel) return;
            var currentVal = sel.value;
            sel.innerHTML = '';
            // Add default option
            var defaultOpt = document.createElement('option');
            defaultOpt.value = '';
            defaultOpt.textContent = '— Server Default —';
            sel.appendChild(defaultOpt);

            var addedModels = new Set();
            for (var provider in availableModels) {
                if (!availableModels.hasOwnProperty(provider)) continue;
                availableModels[provider].forEach(function (model) {
                    if (!addedModels.has(model.id)) {
                        var option = document.createElement('option');
                        option.value = model.id;
                        option.textContent = model.name + ' (' + provider + ')';
                        option.title = model.description;
                        sel.appendChild(option);
                        addedModels.add(model.id);
                    }
                });
            }
            // Restore previous selection
            if (currentVal && Array.from(sel.options).some(function (o) { return o.value === currentVal; })) {
                sel.value = currentVal;
            }
        });
    }

    function getSelectedModels() {
        var smartSelect = document.getElementById('smart-model-select');
        var fastSelect = document.getElementById('fast-model-select');
        return {
            smartModel: (smartSelect && smartSelect.value) ? smartSelect.value : (localStorage.getItem('healthApp_smartModel') || ''),
            fastModel: (fastSelect && fastSelect.value) ? fastSelect.value : (localStorage.getItem('healthApp_fastModel') || '')
        };
    }

    // === File I/O ===
    async function readFile(filePath) {
        var url = basePath + '/' + filePath;
        var resp = await fetch(url);
        if (!resp.ok) {
            if (resp.status === 404) return null;
            throw new Error('Failed to read ' + filePath + ': ' + resp.status + ' ' + resp.statusText);
        }
        return await resp.text();
    }

    async function writeFile(filePath, content) {
        var url = basePath + '/' + filePath;
        var resp = await fetch(url, {
            method: 'PUT',
            headers: {'Content-Type': 'text/plain; charset=utf-8'},
            body: content
        });
        if (!resp.ok) {
            throw new Error('Failed to write ' + filePath + ': ' + resp.status + ' ' + resp.statusText);
        }
        return true;
    }

    // === DocOps Execution ===
    async function runDocOp(opPath, targetPath) {
        var models = getSelectedModels();
        var params = new URLSearchParams({
            sessionId: sessionId,
            doc: opPath,
            target: targetPath
        });
        if (models.smartModel) params.set('smartModel', models.smartModel);
        if (models.fastModel) params.set('fastModel', models.fastModel);

        var url = '/docops?' + params.toString();
        var resp = await fetch(url, {method: 'POST'});
        if (!resp.ok) {
            var errText = await resp.text().catch(function () { return ''; });
            throw new Error('DocOps failed for ' + opPath + ': ' + resp.status + ' ' + resp.statusText + '\n' + errText);
        }
        return await resp.text();
    }

    // === Status Polling ===
    async function fetchDocopsStatus() {
        try {
            var resp = await fetch(basePath + '/docops.status.json');
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) {
            return null;
        }
    }

    function getTaskStatus(statusData, targetPath) {
        if (!statusData || !statusData.tasks) return null;
        // Exact match
        if (statusData.tasks[targetPath]) return statusData.tasks[targetPath];
        // Match by filename only
        var filename = targetPath.split('/').pop();
        if (statusData.tasks[filename]) return statusData.tasks[filename];
        // Search all tasks by target field
        for (var key in statusData.tasks) {
            if (!statusData.tasks.hasOwnProperty(key)) continue;
            var task = statusData.tasks[key];
            if (task.target === targetPath || task.target === filename) return task;
        }
        return null;
    }

    async function waitForTask(targetPath, maxWaitMs) {
        var maxWait = maxWaitMs || 600000; // 10 minutes
        var pollInterval = 2500;
        var startTime = Date.now();

        while (Date.now() - startTime < maxWait) {
            await new Promise(function (resolve) { setTimeout(resolve, pollInterval); });
            var statusData = await fetchDocopsStatus();
            var task = getTaskStatus(statusData, targetPath);
            if (task) {
                if (task.status === 'COMPLETED') return task;
                if (task.status === 'ERROR' || task.status === 'FAILED') {
                    throw new Error('Task failed for ' + targetPath + ': ' + task.status);
                }
                // Update session link while running
                if (task.status === 'RUNNING' && task.sessionId) {
                    updateSessionLink(targetPath, task);
                }
            }
        }
        throw new Error('Timeout waiting for ' + targetPath + ' to complete');
    }

    // Background status polling
    var statusPollTimer = null;
    var STATUS_POLL_INTERVAL = 3000;

    function startStatusPolling() {
        if (statusPollTimer) return;
        statusPollTimer = setInterval(function () { pollAllStatus(); }, STATUS_POLL_INTERVAL);
        pollAllStatus();
    }

    function stopStatusPolling() {
        if (statusPollTimer) {
            clearInterval(statusPollTimer);
            statusPollTimer = null;
        }
    }

    // Map of target paths to badge IDs
    var targetBadgeMap = {
        'round_1/brainstorm.md': 'badge-brainstorm1',
        'round_1/perspectives.md': 'badge-perspectives1',
        'round_1/research.md': 'badge-research1',
        'round_2/questions_for_patient.md': 'badge-questions1',
        'round_2/brainstorm.md': 'badge-brainstorm2',
        'round_2/perspectives.md': 'badge-perspectives2',
        'plan/doctor.md': 'badge-doctor',
        'plan/patient.md': 'badge-patient',
        'plan/lifestyle.md': 'badge-lifestyle',
        'plan/inner.md': 'badge-inner'
    };

    async function pollAllStatus() {
        var statusData = await fetchDocopsStatus();
        if (!statusData || !statusData.tasks) return;

        var anyRunning = false;
        for (var target in statusData.tasks) {
            if (!statusData.tasks.hasOwnProperty(target)) continue;
            var taskInfo = statusData.tasks[target];
            // Update badge
            var badgeId = targetBadgeMap[target];
            if (badgeId) {
                if (taskInfo.status === 'RUNNING') {
                    setBadge(badgeId, 'running');
                    anyRunning = true;
                } else if (taskInfo.status === 'COMPLETED') {
                    setBadge(badgeId, 'done');
                } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                    setBadge(badgeId, 'error');
                }
            }
            // Update session link
            updateSessionLink(target, taskInfo);
        }

        // Stop polling if nothing is running
        if (!anyRunning && statusPollTimer) {
            // Keep polling at a slower rate for external changes
        }
    }

    // === Session Link Display ===
    function updateSessionLink(target, taskInfo) {
        var safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        var containerId = 'session-link-' + safeTarget;
        var container = document.getElementById(containerId);

        if (!container) {
            container = document.createElement('div');
            container.id = containerId;
            container.className = 'session-link-container';
            // Find the step that contains this target and insert the link
            var btn = document.querySelector('.btn-run[data-output="' + target + '"]');
            if (btn) {
                var buttonRow = btn.closest('.button-row');
                if (buttonRow) {
                    buttonRow.parentElement.insertBefore(container, buttonRow.nextSibling);
                }
            }
        }
        if (!container) return;

        var status = taskInfo.status;
        var taskSessionId = taskInfo.sessionId;

        if (status === 'RUNNING' && taskSessionId) {
            var proxyUrl = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-monitor-link">' +
                '<span class="monitor-pulse">●</span> Processing… ' +
                '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '📡 Monitor Live Session (' + escapeHtml(taskSessionId.substring(0, 12)) + '…)</a>' +
                '</div>';
            container.style.display = 'block';
        } else if (status === 'COMPLETED' && taskSessionId) {
            var proxyUrl2 = getProxyUrl(taskSessionId);
            container.innerHTML =
                '<div class="session-completed-link">' +
                '✅ Completed — ' +
                '<a href="' + escapeHtml(proxyUrl2) + '" target="_blank" rel="noopener" class="monitor-link">' +
                '📋 View Session Log</a>' +
                '</div>';
            container.style.display = 'block';
        } else if (status === 'ERROR' || status === 'FAILED') {
            var proxyUrl3 = taskSessionId ? getProxyUrl(taskSessionId) : '#';
            container.innerHTML =
                '<div class="session-error-link">' +
                '❌ Failed — ' +
                (taskSessionId
                    ? '<a href="' + escapeHtml(proxyUrl3) + '" target="_blank" class="monitor-link">🔍 View Error Log</a>'
                    : '<span>No session available</span>') +
                '</div>';
            container.style.display = 'block';
        } else {
            container.style.display = 'none';
        }
    }

    // === Markdown Rendering ===
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

    // === UI Helpers ===
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
            'pending': 'pending',
            'running': 'running…',
            'done': 'done',
            'error': 'error',
            'action': 'action needed'
        };
        el.textContent = labels[state] || state;
    }

    function showLoading(text) {
        var overlay = document.getElementById('loading-overlay');
        var loadingText = document.getElementById('loading-text');
        loadingText.innerHTML = escapeHtml(text || 'Processing...');
        overlay.classList.remove('hidden');
    }

    function hideLoading() {
        var overlay = document.getElementById('loading-overlay');
        overlay.classList.add('hidden');
        var loadingText = document.getElementById('loading-text');
        if (loadingText) loadingText.innerHTML = '';
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
            var sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(function (l) { l.classList.remove('active'); });
            this.classList.add('active');
            document.querySelectorAll('.section').forEach(function (s) { s.classList.remove('active'); });
            document.getElementById(sectionId).classList.add('active');
        });
    });

    // === Results Tabs ===
    document.querySelectorAll('.results-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.results-tab').forEach(function (t) { t.classList.remove('active'); });
            this.classList.add('active');
            document.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
            document.getElementById(this.dataset.tab).classList.add('active');
        });
    });

    // === Load Initial Files ===
    async function loadInitialFiles() {
        try {
            var symptomsContent = await readFile('symptoms.md');
            if (symptomsContent !== null) {
                document.getElementById('symptoms-editor').value = symptomsContent;
            }
        } catch (e) {
            console.warn('Could not load symptoms.md:', e);
        }
        try {
            var notesContent = await readFile('notes.json');
            if (notesContent !== null) {
                document.getElementById('notes-editor').value = notesContent;
            }
        } catch (e) {
            console.warn('Could not load notes.json:', e);
        }
    }

    // === Save Symptoms ===
    document.getElementById('save-symptoms').addEventListener('click', async function () {
        var content = document.getElementById('symptoms-editor').value;
        try {
            this.disabled = true;
            await writeFile('symptoms.md', content);
            setStatus('symptoms-status', '✓ Saved successfully', 'success');
        } catch (e) {
            setStatus('symptoms-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Save Notes ===
    document.getElementById('save-notes').addEventListener('click', async function () {
        var content = document.getElementById('notes-editor').value;
        try {
            JSON.parse(content);
        } catch (e) {
            setStatus('notes-status', '✗ Invalid JSON: ' + e.message, 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('notes.json', content);
            setStatus('notes-status', '✓ Saved successfully', 'success');
        } catch (e) {
            setStatus('notes-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Format JSON ===
    document.getElementById('format-notes').addEventListener('click', function () {
        var editor = document.getElementById('notes-editor');
        try {
            var parsed = JSON.parse(editor.value);
            editor.value = JSON.stringify(parsed, null, 2);
            setStatus('notes-status', '✓ Formatted', 'success');
        } catch (e) {
            setStatus('notes-status', '✗ Invalid JSON: ' + e.message, 'error');
        }
    });

    // === View File ===
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
            viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error loading file: ' + escapeHtml(e.message) + '</p>';
            viewer.classList.add('visible');
        }
    }

    document.querySelectorAll('.btn-view').forEach(function (btn) {
        btn.addEventListener('click', function () {
            viewFile(this.dataset.file, this.dataset.viewer);
        });
    });

    // Results section refresh buttons
    document.querySelectorAll('.results-content .btn-secondary[data-file]').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var filePath = this.dataset.file;
            var viewerId = this.dataset.viewer;
            var viewer = document.getElementById(viewerId);
            if (!viewer) return;
            try {
                var content = await readFile(filePath);
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

    // === Run Operation Buttons ===
    document.querySelectorAll('.btn-run').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();

            try {
                var taskId = await runDocOp(opPath, outputPath);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9_-]+$/.test(cleanTaskId)) {
                    updateSessionLink(outputPath, {status: 'RUNNING', sessionId: cleanTaskId});
                }

                // Wait for completion
                await waitForTask(outputPath);
                setBadge(badgeId, 'done');

                // Auto-show result
                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        try {
                            var content = await readFile(outputPath);
                            if (content) {
                                viewer.innerHTML = renderMarkdown(content);
                                viewer.classList.add('visible');
                            }
                        } catch (e) {
                            // Non-critical
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

    // === Load Questions for Answering ===
    document.getElementById('load-questions').addEventListener('click', async function () {
        try {
            var content = await readFile('round_2/questions_for_patient.md');
            if (content === null) {
                setStatus('answers-status', 'Questions not generated yet. Run step 3b first.', 'error');
                return;
            }
            document.getElementById('answers-editor').value = content;
            setBadge('badge-answers', 'action');
            setStatus('answers-status', '✓ Questions loaded. Add your answers and save.', 'success');
        } catch (e) {
            setStatus('answers-status', '✗ ' + e.message, 'error');
        }
    });

    // === Save Answers ===
    document.getElementById('save-answers').addEventListener('click', async function () {
        var content = document.getElementById('answers-editor').value;
        if (!content.trim()) {
            setStatus('answers-status', 'Nothing to save.', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('round_2/questions_for_patient.md', content);
            setBadge('badge-answers', 'done');
            setStatus('answers-status', '✓ Answers saved', 'success');
        } catch (e) {
            setStatus('answers-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Sequential Batch Execution ===
    async function runSequential(steps) {
        for (var i = 0; i < steps.length; i++) {
            var step = steps[i];
            logBatch('Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');

            try {
                var taskId = await runDocOp(step.op, step.output);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9_-]+$/.test(cleanTaskId)) {
                    var proxyUrl = getProxyUrl(cleanTaskId);
                    logBatchHtml('📡 <a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" class="monitor-link">Monitor: ' + escapeHtml(step.label) + ' (' + escapeHtml(cleanTaskId.substring(0, 12)) + '…)</a>', 'info');
                    updateSessionLink(step.output, {status: 'RUNNING', sessionId: cleanTaskId});
                }

                // Wait for completion
                await waitForTask(step.output);
                setBadge(step.badge, 'done');
                logBatch('✓ Completed: ' + step.label, 'success');

                // Auto-show result in viewer
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
            } catch (e) {
                setBadge(step.badge, 'error');
                logBatch('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
    }

    // === Batch Buttons ===
    document.getElementById('run-round1').addEventListener('click', async function () {
        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';
        logBatch('Starting Round 1 pipeline…', 'info');
        try {
            await runSequential([
                {op: 'ops/initial_brainstorm_op.md', output: 'round_1/brainstorm.md', badge: 'badge-brainstorm1', viewer: 'viewer-brainstorm1', label: 'Initial Brainstorm'},
                {op: 'ops/initial_perspectives_op.md', output: 'round_1/perspectives.md', badge: 'badge-perspectives1', viewer: 'viewer-perspectives1', label: 'Multi-Perspective Analysis'},
                {op: 'ops/opt_research_op.md', output: 'round_1/research.md', badge: 'badge-research1', viewer: 'viewer-research1', label: 'Web Research'},
                {op: 'ops/opt_generate_questions_op.md', output: 'round_2/questions_for_patient.md', badge: 'badge-questions1', viewer: 'viewer-questions1', label: 'Generate Follow-Up Questions'},
            ]);
            logBatch('🎉 Round 1 complete!', 'success');
        } catch (e) {
            logBatch('Round 1 stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('run-round2').addEventListener('click', async function () {
        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';
        logBatch('Starting Round 2 pipeline…', 'info');
        try {
            await runSequential([
                {op: 'ops/opt_brainstorm_op.md', output: 'round_2/brainstorm.md', badge: 'badge-brainstorm2', viewer: 'viewer-brainstorm2', label: 'Supplemental Brainstorm'},
                {op: 'ops/refine_perspectives_op.md', output: 'round_2/perspectives.md', badge: 'badge-perspectives2', viewer: 'viewer-perspectives2', label: 'Refined Perspectives'},
            ]);
            logBatch('🎉 Round 2 complete!', 'success');
        } catch (e) {
            logBatch('Round 2 stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('run-finals').addEventListener('click', async function () {
        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';
        logBatch('Generating Final Reports…', 'info');
        try {
            await runSequential([
                {op: 'ops/final_report_doctor_op.md', output: 'plan/doctor.md', badge: 'badge-doctor', viewer: 'viewer-doctor', label: 'Clinical Handoff Report'},
                {op: 'ops/final_report_patient_op.md', output: 'plan/patient.md', badge: 'badge-patient', viewer: 'viewer-patient', label: 'Patient Action Plan'},
                {op: 'ops/plan_lifestyle_op.md', output: 'plan/lifestyle.md', badge: 'badge-lifestyle', viewer: 'viewer-lifestyle', label: 'Lifestyle Plan'},
                {op: 'ops/plan_inner_development_op.md', output: 'plan/inner.md', badge: 'badge-inner', viewer: 'viewer-inner', label: 'Inner Development Plan'},
            ]);
            logBatch('🎉 All final reports generated!', 'success');
        } catch (e) {
            logBatch('Final report generation stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Model Selection Persistence ===
    var smartModelSelect = document.getElementById('smart-model-select');
    var fastModelSelect = document.getElementById('fast-model-select');
    var settingsSmartSelect = document.getElementById('settings-smart-model');
    var settingsFastSelect = document.getElementById('settings-fast-model');
    function syncSmartModel(value) {
        if (smartModelSelect) smartModelSelect.value = value;
        if (settingsSmartSelect) settingsSmartSelect.value = value;
        localStorage.setItem('healthApp_smartModel', value);
    }
    function syncFastModel(value) {
        if (fastModelSelect) fastModelSelect.value = value;
        if (settingsFastSelect) settingsFastSelect.value = value;
        localStorage.setItem('healthApp_fastModel', value);
    }

    if (smartModelSelect) {
        smartModelSelect.addEventListener('change', function () {
            syncSmartModel(this.value);
        });
    }
    if (fastModelSelect) {
        fastModelSelect.addEventListener('change', function () {
            syncFastModel(this.value);
        });
    }
    if (settingsSmartSelect) {
        settingsSmartSelect.addEventListener('change', function () {
            syncSmartModel(this.value);
        });
    }
    if (settingsFastSelect) {
        settingsFastSelect.addEventListener('change', function () {
            syncFastModel(this.value);
        });
    }

    // === Check Existing Files on Load ===
    async function checkExistingFiles() {
        var statusData = await fetchDocopsStatus();
        var anyRunning = false;

        // First pass: check status file for running/completed tasks
        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                var badgeId = targetBadgeMap[target];
                if (badgeId) {
                    if (taskInfo.status === 'RUNNING') {
                        setBadge(badgeId, 'running');
                        anyRunning = true;
                    } else if (taskInfo.status === 'COMPLETED') {
                        setBadge(badgeId, 'done');
                    } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                        setBadge(badgeId, 'error');
                    }
                }
                // Show session links for all known tasks
                updateSessionLink(target, taskInfo);
            }
        }

        // Second pass: check file existence for any badges still pending
        var checks = Object.keys(targetBadgeMap);
        for (var i = 0; i < checks.length; i++) {
            var filePath = checks[i];
            var badge = document.getElementById(targetBadgeMap[filePath]);
            // Skip if already set from status data
            if (badge && (badge.classList.contains('running') || badge.classList.contains('done') || badge.classList.contains('error'))) {
                continue;
            }
            try {
                var content = await readFile(filePath);
                if (content !== null && content.trim().length > 0) {
                    setBadge(targetBadgeMap[filePath], 'done');
                }
            } catch (e) {
                // File doesn't exist, leave as pending
            }
        }

        if (anyRunning) {
            startStatusPolling();
        }
    }

    // === Initialize ===
    async function init() {
        await loadApiProviders();
        populateModelDropdowns();

        // Restore saved model selections after populating
        var savedSmart2 = localStorage.getItem('healthApp_smartModel');
        if (savedSmart2) {
            var smartAvailable = smartModelSelect && Array.from(smartModelSelect.options).some(function (o) { return o.value === savedSmart2; });
            if (smartAvailable) syncSmartModel(savedSmart2);
        }
        var savedFast2 = localStorage.getItem('healthApp_fastModel');
        if (savedFast2) {
            var fastAvailable = fastModelSelect && Array.from(fastModelSelect.options).some(function (o) { return o.value === savedFast2; });
            if (fastAvailable) syncFastModel(savedFast2);
        }

        await loadInitialFiles();
        await checkExistingFiles();
        startStatusPolling();
    }

    init();
})();