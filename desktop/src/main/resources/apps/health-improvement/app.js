(function() {
    'use strict';
    // === Utility: Determine base path from current URL ===
    // URL pattern: /health-improvement/fileIndex/{sessionId}/app.html
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';
    let appId = '';
    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
        // appId is the segment before fileIndex
        appId = pathParts[fileIndexIdx - 1] || 'health-improvement';
    } else {
        // Fallback: try to parse from hash or use defaults
        console.warn('Could not determine session from URL path. File operations may fail.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }
    // Docops servlet base (at the server root)
    const docopsBase = '/' + pathParts.slice(1, fileIndexIdx - 1).join('/');
    // === Status polling helpers ===
    async function readStatusFile() {
        try {
            const resp = await fetch(basePath + '/docops.status.json');
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) {
            return null;
        }
    }
    function getTaskStatus(statusData, targetPath) {
        if (!statusData || !statusData.tasks) return null;
        // Try exact match first, then try matching by filename
        if (statusData.tasks[targetPath]) return statusData.tasks[targetPath];
        // Try just the filename portion
        const filename = targetPath.split('/').pop();
        if (statusData.tasks[filename]) return statusData.tasks[filename];
        // Try all tasks to find one whose target matches
        for (const key of Object.keys(statusData.tasks)) {
            const task = statusData.tasks[key];
            if (task.target === targetPath || task.target === filename) return task;
        }
        return null;
    }
    function makeProxyLink(taskSessionId) {
        return '/proxy/#' + taskSessionId;
    }
    async function pollForCompletion(targetPath, onStatusUpdate) {
        const pollInterval = 2000; // 2 seconds
        const maxWait = 10 * 60 * 1000; // 10 minutes
        const startTime = Date.now();
        while (Date.now() - startTime < maxWait) {
            await new Promise(resolve => setTimeout(resolve, pollInterval));
            const statusData = await readStatusFile();
            const task = getTaskStatus(statusData, targetPath);
            if (task) {
                if (onStatusUpdate) onStatusUpdate(task);
                if (task.status === 'COMPLETED') return task;
                if (task.status === 'ERROR' || task.status === 'FAILED') {
                    throw new Error(`Task failed for ${targetPath}: ${task.status}`);
                }
                // Still RUNNING or PENDING — continue polling
            }
        }
        throw new Error(`Timeout waiting for ${targetPath} to complete`);
        // The POST may return immediately (async) or after completion.
        // We poll the status file to track progress.
        const respText = await resp.text();

        // Poll for completion, updating the loading overlay with session link
        const task = await pollForCompletion(targetPath, function(taskInfo) {
            if (taskInfo.status === 'RUNNING' && taskInfo.sessionId) {
                updateLoadingWithLink(
                    'Processing: ' + targetPath,
                    taskInfo.sessionId
                );
            }
        });

        return respText;
    }

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
    async function runDocOp(opPath, targetPath) {
        // The docops servlet URL pattern
        const url = `/docops?sessionId=${encodeURIComponent(sessionId)}&doc=${encodeURIComponent(opPath)}&target=${encodeURIComponent(targetPath)}`;
        const resp = await fetch(url, {
            method: 'POST'
        });
        if (!resp.ok) {
            const errText = await resp.text().catch(() => '');
            throw new Error(`DocOps failed for ${opPath}: ${resp.status} ${resp.statusText}\n${errText}`);
        }
    }
    // === Markdown rendering ===
    function renderMarkdown(md) {
        if (typeof marked !== 'undefined') {
            if (typeof marked.parse === 'function') {
                return marked.parse(md);
            }
            return marked(md);
        }
        // Fallback: basic escaping and line breaks
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
        loadingText.innerHTML = escapeHtml(text || 'Processing...');
        overlay.classList.remove('hidden');
    }
    function updateLoadingWithLink(text, taskSessionId) {
        const loadingText = document.getElementById('loading-text');
        if (!loadingText) return;
        const proxyUrl = makeProxyLink(taskSessionId);
        loadingText.innerHTML = escapeHtml(text) +
            '<br><a href="' + escapeHtml(proxyUrl) + '" target="_blank" rel="noopener" ' +
            'style="color: #93c5fd; text-decoration: underline; font-size: 0.85rem;">' +
            '🔍 Monitor live processing (' + escapeHtml(taskSessionId) + ')</a>';
    }

    function hideLoading() {
        document.getElementById('loading-overlay').classList.add('hidden');
        // Reset loading text to remove any links
        const loadingText = document.getElementById('loading-text');
        if (loadingText) loadingText.innerHTML = '';
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
            const symptomsContent = await readFile('symptoms.md');
            if (symptomsContent !== null) {
                document.getElementById('symptoms-editor').value = symptomsContent;
            }
        } catch (e) {
            console.warn('Could not load symptoms.md:', e);
        }
        try {
            const notesContent = await readFile('notes.json');
            if (notesContent !== null) {
                document.getElementById('notes-editor').value = notesContent;
            }
        } catch (e) {
            console.warn('Could not load notes.json:', e);
        }
    }
    // === Save symptoms ===
    document.getElementById('save-symptoms').addEventListener('click', async function() {
        const content = document.getElementById('symptoms-editor').value;
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
    // === Save notes ===
    document.getElementById('save-notes').addEventListener('click', async function() {
        const content = document.getElementById('notes-editor').value;
        try {
            JSON.parse(content); // Validate JSON
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
    document.getElementById('format-notes').addEventListener('click', function() {
        const editor = document.getElementById('notes-editor');
        try {
            const parsed = JSON.parse(editor.value);
            editor.value = JSON.stringify(parsed, null, 2);
            setStatus('notes-status', '✓ Formatted', 'success');
        } catch (e) {
            setStatus('notes-status', '✗ Invalid JSON: ' + e.message, 'error');
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
    // Also handle refresh buttons in results section
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
    // === Run operation buttons ===
    document.querySelectorAll('.btn-run').forEach(btn => {
        btn.addEventListener('click', async function() {
            const opPath = this.dataset.op;
            const badgeId = this.dataset.badge;
            const outputPath = this.dataset.output;
            const viewerId = this.dataset.viewer;
            setBadge(badgeId, 'running');
            this.disabled = true;
            showLoading('Running: ' + opPath.split('/').pop().replace('_op.md', '').replace(/_/g, ' '));
            try {
                // Fire the doc op — runDocOp now polls for completion internally
                await runDocOp(opPath, outputPath);
                setBadge(badgeId, 'done');
                // Auto-show result
                if (viewerId) {
                    const viewer = document.getElementById(viewerId);
                    if (viewer) {
                        try {
                            const content = await readFile(outputPath);
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
                hideLoading();
            }
        });
    });
    // === Load questions for answering ===
    document.getElementById('load-questions').addEventListener('click', async function() {
        try {
            const content = await readFile('round_2/questions_for_patient.md');
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
    // === Save answers ===
    document.getElementById('save-answers').addEventListener('click', async function() {
        const content = document.getElementById('answers-editor').value;
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
    // === Batch execution ===
    async function runSequential(steps) {
        for (const step of steps) {
            logBatch(`Starting: ${step.label}`, 'info');
            const btn = document.querySelector(`.btn-run[data-op="${step.op}"]`);
            if (btn) {
                setBadge(step.badge, 'running');
                btn.disabled = true;
            }
            try {
                // Fire the POST
                const postUrl = `/docops?sessionId=${encodeURIComponent(sessionId)}&doc=${encodeURIComponent(step.op)}&target=${encodeURIComponent(step.output)}`;
                const resp = await fetch(postUrl, { method: 'POST' });
                if (!resp.ok) {
                    const errText = await resp.text().catch(() => '');
                    throw new Error(`DocOps failed for ${step.op}: ${resp.status} ${resp.statusText}\n${errText}`);
                }
                await resp.text();

                // Poll for completion with logging
                const task = await pollForCompletion(step.output, function(taskInfo) {
                    if (taskInfo.status === 'RUNNING' && taskInfo.sessionId) {
                        updateLoadingWithLink('Processing: ' + step.label, taskInfo.sessionId);
                        // Log the monitoring link once
                        const logLink = document.querySelector(`[data-monitor-target="${step.output}"]`);
                        if (!logLink) {
                            const entry = document.createElement('div');
                            entry.className = 'log-entry log-info';
                            entry.dataset.monitorTarget = step.output;
                            const ts = new Date().toLocaleTimeString();
                            entry.innerHTML = `[${ts}] 🔍 <a href="${escapeHtml(makeProxyLink(taskInfo.sessionId))}" target="_blank" rel="noopener" style="color: #93c5fd; text-decoration: underline;">Monitor: ${escapeHtml(step.label)} (${escapeHtml(taskInfo.sessionId)})</a>`;
                            batchLog.appendChild(entry);
                            batchLog.scrollTop = batchLog.scrollHeight;
                        }
                    }
                });

                if (btn) setBadge(step.badge, 'done');
                logBatch(`✓ Completed: ${step.label}`, 'success');
                // Try to load result into viewer
                if (step.viewer) {
                    try {
                        const content = await readFile(step.output);
                        if (content) {
                            const viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                viewer.innerHTML = renderMarkdown(content);
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }
            } catch (e) {
                if (btn) setBadge(step.badge, 'error');
                logBatch(`✗ Failed: ${step.label} — ${e.message}`, 'error');
                throw e;
            } finally {
                if (btn) btn.disabled = false;
            }
        }
    }
    document.getElementById('run-round1').addEventListener('click', async function() {
        this.disabled = true;
        showLoading('Running Round 1...');
        batchLog.innerHTML = '';
        try {
            await runSequential([
                { op: 'ops/initial_brainstorm_op.md', output: 'round_1/brainstorm.md', badge: 'badge-brainstorm1', viewer: 'viewer-brainstorm1', label: 'Initial Brainstorm' },
                { op: 'ops/initial_perspectives_op.md', output: 'round_1/perspectives.md', badge: 'badge-perspectives1', viewer: 'viewer-perspectives1', label: 'Multi-Perspective Analysis' },
                { op: 'ops/opt_research_op.md', output: 'round_1/research.md', badge: 'badge-research1', viewer: 'viewer-research1', label: 'Web Research' },
                { op: 'ops/opt_generate_questions_op.md', output: 'round_2/questions_for_patient.md', badge: 'badge-questions1', viewer: 'viewer-questions1', label: 'Generate Follow-Up Questions' },
            ]);
            logBatch('Round 1 complete!', 'success');
        } catch (e) {
            logBatch('Round 1 stopped due to error.', 'error');
        } finally {
            this.disabled = false;
            hideLoading();
        }
    });
    document.getElementById('run-round2').addEventListener('click', async function() {
        this.disabled = true;
        showLoading('Running Round 2...');
        batchLog.innerHTML = '';
        try {
            await runSequential([
                { op: 'ops/opt_brainstorm_op.md', output: 'round_2/brainstorm.md', badge: 'badge-brainstorm2', viewer: 'viewer-brainstorm2', label: 'Supplemental Brainstorm' },
                { op: 'ops/refine_perspectives_op.md', output: 'round_2/perspectives.md', badge: 'badge-perspectives2', viewer: 'viewer-perspectives2', label: 'Refined Perspectives' },
            ]);
            logBatch('Round 2 complete!', 'success');
        } catch (e) {
            logBatch('Round 2 stopped due to error.', 'error');
        } finally {
            this.disabled = false;
            hideLoading();
        }
    });
    document.getElementById('run-finals').addEventListener('click', async function() {
        this.disabled = true;
        showLoading('Generating Final Reports...');
        batchLog.innerHTML = '';
        try {
            await runSequential([
                { op: 'ops/final_report_doctor_op.md', output: 'plan/doctor.md', badge: 'badge-doctor', viewer: 'viewer-doctor', label: 'Clinical Handoff Report' },
                { op: 'ops/final_report_patient_op.md', output: 'plan/patient.md', badge: 'badge-patient', viewer: 'viewer-patient', label: 'Patient Action Plan' },
                { op: 'ops/plan_lifestyle_op.md', output: 'plan/lifestyle.md', badge: 'badge-lifestyle', viewer: 'viewer-lifestyle', label: 'Lifestyle Plan' },
                { op: 'ops/plan_inner_development_op.md', output: 'plan/inner.md', badge: 'badge-inner', viewer: 'viewer-inner', label: 'Inner Development Plan' },
            ]);
            logBatch('All final reports generated!', 'success');
        } catch (e) {
            logBatch('Final report generation stopped due to error.', 'error');
        } finally {
            this.disabled = false;
            hideLoading();
        }
    });
    // === Check existing files on load ===
    async function checkExistingFiles() {
        // Also read the status file for richer state
        const statusData = await readStatusFile();

        const checks = [
            { file: 'round_1/brainstorm.md', badge: 'badge-brainstorm1' },
            { file: 'round_1/perspectives.md', badge: 'badge-perspectives1' },
            { file: 'round_1/research.md', badge: 'badge-research1' },
            { file: 'round_2/questions_for_patient.md', badge: 'badge-questions1' },
            { file: 'round_2/brainstorm.md', badge: 'badge-brainstorm2' },
            { file: 'round_2/perspectives.md', badge: 'badge-perspectives2' },
            { file: 'plan/doctor.md', badge: 'badge-doctor' },
            { file: 'plan/patient.md', badge: 'badge-patient' },
            { file: 'plan/lifestyle.md', badge: 'badge-lifestyle' },
            { file: 'plan/inner.md', badge: 'badge-inner' },
        ];
        for (const check of checks) {
            try {
                // Check if task is currently running
                const task = getTaskStatus(statusData, check.file);
                if (task && task.status === 'RUNNING') {
                    setBadge(check.badge, 'running');
                    // Start background polling for this task
                    pollAndUpdateBadge(check.file, check.badge, task.sessionId);
                    continue;
                }

                const content = await readFile(check.file);
                if (content !== null && content.trim().length > 0) {
                    setBadge(check.badge, 'done');
                }
            } catch (e) {
                // File doesn't exist, leave as pending
            }
        }
    }
    // Background poll for tasks that were already running when page loaded
    async function pollAndUpdateBadge(targetPath, badgeId, taskSessionId) {
        try {
            updateLoadingWithLink('Resuming: ' + targetPath, taskSessionId);
            showLoading('Task in progress: ' + targetPath);
            await pollForCompletion(targetPath, function(taskInfo) {
                if (taskInfo.status === 'RUNNING' && taskInfo.sessionId) {
                    updateLoadingWithLink('Processing: ' + targetPath, taskInfo.sessionId);
                }
            });
            setBadge(badgeId, 'done');
        } catch (e) {
            setBadge(badgeId, 'error');
        } finally {
            hideLoading();
        }
    }

    // === Initialize ===
    loadInitialFiles();
    checkExistingFiles();
})();