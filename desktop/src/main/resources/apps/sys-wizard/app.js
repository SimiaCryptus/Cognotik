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
     // === Display Session ID ===
     var sessionDisplay = document.getElementById('session-id-display');
     if (sessionDisplay && sessionId) {
         sessionDisplay.textContent = 'Session: ' + sessionId;
     }


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
     // === Character Count ===
     var goalEditor = document.getElementById('goal-editor');
     var charCount = document.getElementById('char-count');
     if (goalEditor && charCount) {
         goalEditor.addEventListener('input', function () {
             var len = this.value.length;
             charCount.textContent = len + ' character' + (len !== 1 ? 's' : '');
         });
     }
     // === Example Prompts ===
     var examplePrompts = {
         docker: 'List all running Docker containers, find any using more than 1GB of memory, stop idle containers that have been running for more than 7 days, and write a summary report to /tmp/docker-report.txt',
         logs: 'Analyze the last 1000 lines of /var/log/syslog, identify the top 10 most frequent error messages, group them by service name, and write a summary report to /tmp/log-analysis.txt',
         backup: 'Create a compressed backup of the /home directory (excluding .cache and node_modules), save it to /tmp/home-backup-$(date +%Y%m%d).tar.gz, and verify the archive integrity',
         sysinfo: 'Collect comprehensive system information including OS version, CPU, memory, disk usage, network interfaces, running services, and open ports. Write a formatted report to /tmp/system-info.txt'
     };
     document.querySelectorAll('.btn-example').forEach(function (btn) {
         btn.addEventListener('click', function () {
             var key = this.dataset.example;
             if (examplePrompts[key] && goalEditor) {
                 goalEditor.value = examplePrompts[key];
                 goalEditor.focus();
                 if (charCount) {
                     charCount.textContent = goalEditor.value.length + ' characters';
                 }
             }
         });
     });


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
         var content = goalEditor.value;
        if (!content.trim()) {
            setStatus('goal-status', '✗ Please enter a goal first', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('goal.md', content);
            setStatus('goal-status', '✓ Goal saved', 'success');
             updatePipelineStage('goal', 'completed');
        } catch (e) {
            setStatus('goal-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });
     // === Save & Run Pipeline ===
     document.getElementById('save-and-run').addEventListener('click', function () {
         document.getElementById('run-all').click();
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

     // === Copy Script ===
    document.getElementById('copy-script').addEventListener('click', async function () {
        try {
            var content = await readFile('code/script.sh');
            if (content === null) {
                 showToast('No script generated yet. Run the pipeline first.', 'error');
                return;
            }
            await navigator.clipboard.writeText(content);
             this.innerHTML = '<span class="btn-icon">✓</span> Copied!';
            var self = this;
             setTimeout(function () { self.innerHTML = '<span class="btn-icon">📋</span> Copy'; }, 2000);
        } catch (e) {
             showToast('Failed to copy: ' + e.message, 'error');
        }
    });
     // === Download Script ===
     document.getElementById('download-script').addEventListener('click', async function () {
         try {
             var content = await readFile('code/script.sh');
             if (content === null) {
                 showToast('No script generated yet. Run the pipeline first.', 'error');
                 return;
             }
             var blob = new Blob([content], { type: 'text/x-shellscript' });
             var url = URL.createObjectURL(blob);
             var a = document.createElement('a');
             a.href = url;
             a.download = 'script.sh';
             document.body.appendChild(a);
             a.click();
             document.body.removeChild(a);
             URL.revokeObjectURL(url);
             showToast('Script downloaded!', 'success');
         } catch (e) {
             showToast('Failed to download: ' + e.message, 'error');
         }
     });
     // === Toast Notifications ===
     function showToast(message, type) {
         var container = document.getElementById('toast-container');
         if (!container) return;
         var toast = document.createElement('div');
         toast.className = 'toast toast-' + (type || 'info');
         toast.textContent = message;
         container.appendChild(toast);
         setTimeout(function () {
             toast.classList.add('removing');
             setTimeout(function () {
                 if (toast.parentNode) toast.parentNode.removeChild(toast);
             }, 300);
         }, 4000);
     }
     // === Progress Bar ===
     function showProgress(label, percent) {
         var bar = document.getElementById('global-progress');
         var fill = document.getElementById('progress-fill');
         var labelEl = document.getElementById('progress-label');
         if (!bar || !fill || !labelEl) return;
         bar.classList.add('visible');
         labelEl.textContent = label || '';
         if (percent === undefined || percent === null || percent < 0) {
             fill.style.width = '';
             fill.classList.add('indeterminate');
         } else {
             fill.classList.remove('indeterminate');
             fill.style.width = Math.min(100, Math.max(0, percent)) + '%';
         }
     }
     function hideProgress() {
         var bar = document.getElementById('global-progress');
         var fill = document.getElementById('progress-fill');
         if (bar) bar.classList.remove('visible');
         if (fill) { fill.classList.remove('indeterminate'); fill.style.width = '0%'; }
     }
     // === Pipeline Stage Diagram ===
     function updatePipelineStage(stage, state) {
         var stageEl = document.getElementById('pipeline-stage-' + stage);
         var statusEl = document.getElementById('stage-' + stage + '-status');
         if (stageEl) {
             stageEl.classList.remove('active', 'completed', 'error');
             if (state === 'active') stageEl.classList.add('active');
             else if (state === 'completed') stageEl.classList.add('completed');
             else if (state === 'error') stageEl.classList.add('error');
         }
         if (statusEl) {
             if (state === 'active') { statusEl.textContent = 'Running…'; statusEl.className = 'stage-status running'; }
             else if (state === 'completed') { statusEl.textContent = 'Done'; statusEl.className = 'stage-status done'; }
             else if (state === 'error') { statusEl.textContent = 'Error'; statusEl.className = 'stage-status error'; }
             else if (state === 'pending') { statusEl.textContent = 'Pending'; statusEl.className = 'stage-status'; }
             else { statusEl.textContent = state; statusEl.className = 'stage-status done'; }
         }
         // Update connectors
         updateConnectors();
     }
     function updateConnectors() {
         var stageGoal = document.getElementById('pipeline-stage-goal');
         var stageCodegen = document.getElementById('pipeline-stage-codegen');
         var stageRun = document.getElementById('pipeline-stage-run');
         var conn1 = document.getElementById('connector-1');
         var conn2 = document.getElementById('connector-2');
         if (conn1) {
             conn1.classList.remove('active', 'completed');
             if (stageGoal && stageGoal.classList.contains('completed')) {
                 if (stageCodegen && (stageCodegen.classList.contains('active'))) conn1.classList.add('active');
                 else if (stageCodegen && stageCodegen.classList.contains('completed')) conn1.classList.add('completed');
             }
         }
         if (conn2) {
             conn2.classList.remove('active', 'completed');
             if (stageCodegen && stageCodegen.classList.contains('completed')) {
                 if (stageRun && stageRun.classList.contains('active')) conn2.classList.add('active');
                 else if (stageRun && stageRun.classList.contains('completed')) conn2.classList.add('completed');
             }
         }
     }
     // === Results Summary ===
     function updateResultsSummary(scriptStatus, executionStatus) {
         var summary = document.getElementById('results-summary');
         if (!summary) return;
         summary.style.display = 'block';
         var scriptStat = document.getElementById('summary-script-status');
         var execStat = document.getElementById('summary-execution-status');
         if (scriptStat) {
             var val = scriptStat.querySelector('.stat-value');
             if (val) {
                 val.textContent = scriptStatus || '—';
                 val.className = 'stat-value';
                 if (scriptStatus === 'Generated') val.classList.add('success');
                 else if (scriptStatus === 'Error') val.classList.add('error');
             }
         }
         if (execStat) {
             var val2 = execStat.querySelector('.stat-value');
             if (val2) {
                 val2.textContent = executionStatus || '—';
                 val2.className = 'stat-value';
                 if (executionStatus === 'Success') val2.classList.add('success');
                 else if (executionStatus === 'Failed') val2.classList.add('error');
                 else if (executionStatus === 'Running…') val2.classList.add('running');
             }
         }
     }


    // === Run Operation Buttons ===
    document.querySelectorAll('.btn-run').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            // Auto-save goal before running
             var goalContent = goalEditor.value;
            if (!goalContent.trim()) {
                 showToast('Please enter a goal first.', 'error');
                return;
            }
            await writeFile('goal.md', goalContent);
             updatePipelineStage('goal', 'completed');

            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();
             showProgress('Running ' + opPath + '…', -1);

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
                 showToast('Operation failed: ' + e.message, 'error');
            } finally {
                this.disabled = false;
                 hideProgress();
            }
        });
    });

    // === Batch Execution ===
    async function runSequential(steps) {
         var totalSteps = steps.length;
         for (var i = 0; i < totalSteps; i++) {
            var step = steps[i];
            logBatch('Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');
             if (step.stage) updatePipelineStage(step.stage, 'active');
             showProgress(step.label + '…', Math.round((i / totalSteps) * 100));

            try {
                var taskId = await runDocOp(step.op, step.output);
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    logBatchHtml('Session: <a href="' + getProxyUrl(cleanTaskId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId.substring(0, 12) + '…)</a>', 'info');
                    updateSessionLinks(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(step.output);
                setBadge(step.badge, 'done');
                 if (step.stage) updatePipelineStage(step.stage, 'completed');
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
                 if (step.stage) updatePipelineStage(step.stage, 'error');
                logBatch('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
         showProgress('Complete', 100);
    }

    document.getElementById('run-all').addEventListener('click', async function () {
        // Auto-save goal
         var goalContent = goalEditor.value;
        if (!goalContent.trim()) {
             showToast('Please enter a goal first.', 'error');
            return;
        }

        this.disabled = true;
        startStatusPolling();
        batchLog.innerHTML = '';

        try {
            await writeFile('goal.md', goalContent);
            logBatch('✓ Goal saved', 'success');
             updatePipelineStage('goal', 'completed');

            await runSequential([
                {
                    op: 'ops/code_op.md',
                    output: 'code/script.sh',
                    badge: 'badge-codegen',
                    viewer: 'viewer-codegen',
                     label: 'Generate Shell Script',
                     stage: 'codegen'
                },
                {
                    op: 'ops/run_op.md',
                    output: 'code/fix_log.md',
                    badge: 'badge-run',
                    viewer: 'viewer-run',
                    label: 'Run & Auto-Fix',
                     stage: 'run',
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
             showToast('Pipeline complete!', 'success');
             updateResultsSummary('Generated', 'Success');

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
             showToast('Pipeline stopped due to error.', 'error');
             updateResultsSummary(
                 document.getElementById('badge-codegen').classList.contains('done') ? 'Generated' : 'Error',
                 'Failed'
             );
        } finally {
            this.disabled = false;
             hideProgress();
             stopStatusPolling();
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
                 // Update pipeline diagram from status
                 var stageMapDiagram = {
                     'code/script.sh': 'codegen',
                     'code/fix_log.md': 'run'
                 };
                 var diagramStage = stageMapDiagram[target];
                 if (diagramStage) {
                     if (taskInfo.status === 'RUNNING') updatePipelineStage(diagramStage, 'active');
                     else if (taskInfo.status === 'COMPLETED') updatePipelineStage(diagramStage, 'completed');
                     else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') updatePipelineStage(diagramStage, 'error');
                 }
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
                 if (content !== null && content.trim().length > 0) {
                     setBadge(check.badge, 'done');
                     var fileStageMap = { 'code/script.sh': 'codegen', 'code/fix_log.md': 'run' };
                     var fStage = fileStageMap[check.file];
                     if (fStage) updatePipelineStage(fStage, 'completed');
                 }
            } catch (e) { /* leave as pending */ }
        }

        // Check goal
        try {
            var goalContent = await readFile('goal.md');
            if (goalContent !== null && goalContent.trim().length > 0) {
                 updatePipelineStage('goal', 'completed');
            }
        } catch (e) { /* ignore */ }

         if (anyRunning) {
             startStatusPolling();
         }
         // Update results summary if files exist
         try {
             var scriptExists = await readFile('code/script.sh');
             var logExists = await readFile('code/fix_log.md');
             if (scriptExists || logExists) {
                 updateResultsSummary(
                     scriptExists ? 'Generated' : '—',
                     logExists ? 'Success' : '—'
                 );
             }
         } catch (e) { /* ignore */ }
    }

    // === Load Initial Files ===
    async function loadInitialFiles() {
        try {
            var content = await readFile('goal.md');
            if (content !== null) {
                 goalEditor.value = content;
                 if (charCount) {
                     charCount.textContent = content.length + ' character' + (content.length !== 1 ? 's' : '');
                 }
            }
        } catch (e) {
            console.warn('Could not load goal.md:', e);
        }
    }

    // === Initialize ===
    loadInitialFiles();
    checkExistingFiles();

})();