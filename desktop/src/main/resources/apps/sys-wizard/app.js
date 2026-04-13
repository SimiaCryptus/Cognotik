import { parseSessionUrl, getProxyUrl } from './utils/session.js';
import { readFile, writeFile } from './utils/fileIO.js';
import { runDocOp, fetchDocopsStatus, waitForTask, createStatusPoller } from './utils/docops.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { createSessionLinkManager } from './utils/sessionLinks.js';
import {
    renderMarkdown, escapeHtml, setStatus, setBadge,
    showToast, createBatchLogger
} from './utils/ui.js';
import { aggregateUsage, formatTokenCount, formatCost, renderUsageSummary, createUsageTableHtml } from './utils/usage.js';

(function () {
    'use strict';

    // === Session Setup ===
    const { basePath, sessionId } = parseSessionUrl();

    // === Platform Mode (sh vs cmd) ===
    var currentPlatform = 'sh';

    function getScriptFilename() {
        return currentPlatform === 'cmd' ? 'code/script.cmd' : 'code/script.sh';
    }
    function getCodegenOp() {
        return currentPlatform === 'cmd' ? 'ops/code_cmd.md' : 'ops/code_sh.md';
    }
    function getRunOp() {
        return currentPlatform === 'cmd' ? 'ops/run_cmd.md' : 'ops/run_sh.md';
    }
    function getScriptMimeType() {
        return currentPlatform === 'cmd' ? 'application/x-bat' : 'text/x-shellscript';
    }
    function getScriptDownloadName() {
        return currentPlatform === 'cmd' ? 'script.cmd' : 'script.sh';
    }
    function getEditorPlaceholder() {
        if (currentPlatform === 'cmd') {
            return '@echo off\r\nREM Paste or write your PowerShell/CMD script here…\r\n\r\necho Hello, World!';
        }
        return '#!/bin/bash\n\n# Paste or write your script here…\n\necho "Hello, World!"';
    }

    // === Available Models & Child Sessions ===
    var availableModels = {};
    var childSessionIds = new Set();

    // === Session Link Manager ===
    var linkManager = createSessionLinkManager(getProxyUrl);

    // === Batch Logger ===
    var batchLogger = createBatchLogger('batch-log');

    // === Status Poller ===
    var statusPoller = null;

    // === Display Session ID ===
    var sessionDisplay = document.getElementById('session-id-display');
    if (sessionDisplay && sessionId) {
        sessionDisplay.textContent = 'Session: ' + sessionId;
    }

    // === Path Normalization ===
    function normalizePath(p) {
        return p ? p.replace(/\\/g, '/') : p;
    }

    // === Badge & Stage Maps ===
    var badgeMap = {};
    var stageMap = {};
    // Track targets confirmed completed so polling doesn't regress them
    var completedTargets = {};

    function rebuildMaps() {
        var scriptFile = getScriptFilename();
        badgeMap = {};
        badgeMap[scriptFile] = 'badge-codegen';
        badgeMap['code/fix_log.md'] = 'badge-run';
        stageMap = {};
        stageMap[scriptFile] = 'stage-codegen-status';
        stageMap['code/fix_log.md'] = 'stage-run-status';
    }

    // === Script Rendering ===
    function renderScript(code) {
        return '<pre class="script-code"><code>' + escapeHtml(code) + '</code></pre>';
    }

    function isScriptFile(filePath) {
        return /\.(sh|bash|zsh|py|rb|pl|js|cmd|bat|ps1)$/i.test(filePath);
    }

    function renderFileContent(filePath, content) {
        return isScriptFile(filePath) ? renderScript(content) : renderMarkdown(content);
    }

    // === Platform UI Update ===
    function updatePlatformUI() {
        var scriptFile = getScriptFilename();
        var codegenOp = getCodegenOp();
        var runOp = getRunOp();

        var filenameDisplay = document.getElementById('script-filename-display');
        if (filenameDisplay) filenameDisplay.textContent = scriptFile;

        var btnGenerate = document.getElementById('btn-generate');
        if (btnGenerate) {
            btnGenerate.dataset.op = codegenOp;
            btnGenerate.dataset.output = scriptFile;
        }

        var btnViewGenerated = document.getElementById('btn-view-generated');
        if (btnViewGenerated) {
            btnViewGenerated.dataset.file = scriptFile;
        }

        var btnRunFix = document.getElementById('btn-run-fix');
        if (btnRunFix) {
            btnRunFix.dataset.op = runOp;
        }

        var btnViewFinal = document.getElementById('btn-view-final-script');
        if (btnViewFinal) {
            btnViewFinal.dataset.file = scriptFile;
        }

        rebuildMaps();

        if (scriptEditor) {
            scriptEditor.placeholder = getEditorPlaceholder();
        }

        document.querySelectorAll('.platform-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.platform === currentPlatform);
        });

        localStorage.setItem('wizardPlatform', currentPlatform);
    }

    // === Platform Toggle ===
    document.querySelectorAll('.platform-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var newPlatform = this.dataset.platform;
            if (newPlatform === currentPlatform) return;
            currentPlatform = newPlatform;
            updatePlatformUI();
            showToast('Switched to ' + (currentPlatform === 'cmd' ? 'Windows (CMD/PowerShell)' : 'Shell (Linux/macOS)') + ' mode', 'info');
        });
    });

    var savedPlatform = localStorage.getItem('wizardPlatform');
    if (savedPlatform === 'cmd' || savedPlatform === 'sh') {
        currentPlatform = savedPlatform;
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

    // === Script Editor Mode Toggle ===
    var scriptEditor = document.getElementById('script-editor');
    var panelGenerate = document.getElementById('panel-generate');
    var panelManual = document.getElementById('panel-manual');
    document.querySelectorAll('.mode-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var mode = this.dataset.mode;
            document.querySelectorAll('.mode-btn').forEach(function (b) {
                b.classList.remove('active');
            });
            this.classList.add('active');
            if (mode === 'generate') {
                panelGenerate.classList.add('active');
                panelManual.classList.remove('active');
            } else {
                panelGenerate.classList.remove('active');
                panelManual.classList.add('active');
                if (scriptEditor && !scriptEditor.value.trim()) {
                    readFile(basePath, getScriptFilename()).then(function (content) {
                        if (content !== null && content.trim().length > 0) {
                            scriptEditor.value = content;
                        }
                    }).catch(function () { /* ignore */ });
                }
            }
        });
    });

    // === Save Script Manually ===
    document.getElementById('save-script-manual').addEventListener('click', async function () {
        var content = scriptEditor.value;
        if (!content.trim()) {
            setStatus('script-editor-status', '✗ Please enter a script first', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile(basePath, getScriptFilename(), content);
            setStatus('script-editor-status', '✓ Script saved', 'success');
            setBadge('badge-codegen', 'done');
            updatePipelineStage('codegen', 'completed');
            showToast('Script saved — you can now Run & Fix', 'success');
            var viewer = document.getElementById('viewer-codegen');
            if (viewer) {
                viewer.innerHTML = renderScript(content);
                viewer.classList.add('visible');
            }
        } catch (e) {
            setStatus('script-editor-status', '✗ ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Load Existing Script into Editor ===
    document.getElementById('load-script-into-editor').addEventListener('click', async function () {
        try {
            this.disabled = true;
            var content = await readFile(basePath, getScriptFilename());
            if (content !== null && content.trim().length > 0) {
                scriptEditor.value = content;
                showToast('Script loaded into editor', 'success');
            } else {
                showToast('No existing script found', 'info');
            }
        } catch (e) {
            showToast('Failed to load script: ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // === Model Management ===
    var MODEL_PREFIX = 'wizard';

    function getSelectedSmartModel() {
        var sel = document.getElementById('setting-smart-model');
        var val = sel ? sel.value : '';
        if (val && val.trim().length > 0) return val.trim();
        var saved = loadModelSelections(MODEL_PREFIX, ['SmartModel']);
        return saved.SmartModel || '';
    }

    function getSelectedFastModel() {
        var sel = document.getElementById('setting-fast-model');
        var val = sel ? sel.value : '';
        if (val && val.trim().length > 0) return val.trim();
        var saved = loadModelSelections(MODEL_PREFIX, ['FastModel']);
        return saved.FastModel || '';
    }

    function getSelectedImageModel() {
        return getSelectedSmartModel();
    }

    function getModelSettings() {
        return {
            smartModel: getSelectedSmartModel(),
            fastModel: getSelectedFastModel(),
            imageModel: getSelectedImageModel()
        };
    }

    function validateModelSettings() {
        var smart = getSelectedSmartModel();
        var fast = getSelectedFastModel();
        return !!(smart && fast);
    }

    function promptForModelSettings() {
        showToast('⚠️ Please select both Smart and Fast models in Settings before running the pipeline.', 'error');
        navigateToSection('section-settings');
        var smartSelect = document.getElementById('setting-smart-model');
        var fastSelect = document.getElementById('setting-fast-model');
        if (smartSelect && !smartSelect.value) smartSelect.classList.add('model-select-required');
        if (fastSelect && !fastSelect.value) fastSelect.classList.add('model-select-required');
        setTimeout(function () {
            if (smartSelect) smartSelect.classList.remove('model-select-required');
            if (fastSelect) fastSelect.classList.remove('model-select-required');
        }, 3000);
    }

    async function loadModels() {
        try {
            var models = await loadApiProviders();
            availableModels = models || {};

            var smartSelect = document.getElementById('setting-smart-model');
            var fastSelect = document.getElementById('setting-fast-model');

            if (smartSelect && fastSelect) {
                var savedSelections = loadModelSelections(MODEL_PREFIX, ['SmartModel', 'FastModel']);
                populateModelDropdowns(availableModels, {
                    smart: smartSelect,
                    fast: fastSelect
                }, {
                    smart: savedSelections.SmartModel || '',
                    fast: savedSelections.FastModel || ''
                });
            }

            updateModelInfo();
            updateModelLoadStatus('success', 'Models loaded');
        } catch (e) {
            console.error('Failed to load API providers:', e);
            updateModelLoadStatus('error', 'Error loading models');
        }
    }

    function updateModelInfo() {
        var providerNames = Object.keys(availableModels);
        var totalModels = 0;
        providerNames.forEach(function (p) {
            totalModels += availableModels[p].length;
        });

        var infoProviders = document.getElementById('info-providers');
        var infoTotal = document.getElementById('info-total-models');
        var infoSmart = document.getElementById('info-smart-model');
        var infoFast = document.getElementById('info-fast-model');

        if (infoProviders) infoProviders.textContent = providerNames.length > 0 ? providerNames.join(', ') : 'None';
        if (infoTotal) infoTotal.textContent = totalModels.toString();
        if (infoSmart) infoSmart.textContent = getSelectedSmartModel() || '⚠️ Not configured (required)';
        if (infoFast) infoFast.textContent = getSelectedFastModel() || '⚠️ Not configured (required)';
    }

    function updateModelLoadStatus(type, message) {
        var el = document.getElementById('model-load-status');
        if (!el) return;
        el.textContent = message;
        el.className = 'model-status model-status-' + type;
        if (type === 'success') {
            setTimeout(function () {
                el.textContent = '';
                el.className = 'model-status';
            }, 3000);
        }
    }

    function updatePipelineModelIndicator() {
        var smartEl = document.getElementById('pipeline-smart-model');
        var fastEl = document.getElementById('pipeline-fast-model');
        var smart = getSelectedSmartModel();
        var fast = getSelectedFastModel();
        if (smartEl) {
            smartEl.textContent = smart ? truncateModelName(smart) : '⚠️ Not set';
            smartEl.className = 'model-indicator-value' + (smart ? '' : ' model-not-set');
        }
        if (fastEl) {
            fastEl.textContent = fast ? truncateModelName(fast) : '⚠️ Not set';
            fastEl.className = 'model-indicator-value' + (fast ? '' : ' model-not-set');
        }
    }

    function truncateModelName(name) {
        if (name.length > 20) return name.substring(0, 18) + '…';
        return name;
    }

    // Model settings event listeners
    document.getElementById('save-model-settings').addEventListener('click', function () {
        var smartSelect = document.getElementById('setting-smart-model');
        var fastSelect = document.getElementById('setting-fast-model');
        var smartVal = smartSelect ? smartSelect.value.trim() : '';
        var fastVal = fastSelect ? fastSelect.value.trim() : '';

        if (!smartVal || !fastVal) {
            setStatus('model-settings-status', '✗ Both Smart and Fast models are required', 'error');
            if (smartSelect && !smartVal) smartSelect.classList.add('model-select-required');
            if (fastSelect && !fastVal) fastSelect.classList.add('model-select-required');
            setTimeout(function () {
                if (smartSelect) smartSelect.classList.remove('model-select-required');
                if (fastSelect) fastSelect.classList.remove('model-select-required');
            }, 3000);
            return;
        }

        saveModelSelections(MODEL_PREFIX, { SmartModel: smartVal, FastModel: fastVal });
        updateModelInfo();
        updatePipelineModelIndicator();
        setStatus('model-settings-status', '✓ Settings saved', 'success');
        showToast('Model settings saved', 'success');
    });

    document.getElementById('reload-models').addEventListener('click', function () {
        this.disabled = true;
        var self = this;
        loadModels().finally(function () {
            self.disabled = false;
        });
    });

    var smartSelect = document.getElementById('setting-smart-model');
    var fastSelect = document.getElementById('setting-fast-model');
    if (smartSelect) {
        smartSelect.addEventListener('change', function () {
            updatePipelineModelIndicator();
            updateModelInfo();
        });
    }
    if (fastSelect) {
        fastSelect.addEventListener('change', function () {
            updatePipelineModelIndicator();
            updateModelInfo();
        });
    }

    document.getElementById('change-models-link').addEventListener('click', function (e) {
        e.preventDefault();
        navigateToSection('section-settings');
    });

    // === Navigation ===
    function navigateToSection(sectionId) {
        document.querySelectorAll('.nav-link').forEach(function (l) {
            l.classList.remove('active');
        });
        document.querySelectorAll('.section').forEach(function (s) {
            s.classList.remove('active');
        });
        var link = document.querySelector('[data-section="' + sectionId + '"]');
        if (link) link.classList.add('active');
        var section = document.getElementById(sectionId);
        if (section) section.classList.add('active');
    }

    document.querySelectorAll('.nav-link').forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            navigateToSection(this.dataset.section);
        });
    });

    // === Results Tabs ===
    document.querySelectorAll('.results-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.results-tab').forEach(function (t) {
                t.classList.remove('active');
            });
            document.querySelectorAll('.tab-panel').forEach(function (p) {
                p.classList.remove('active');
            });
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
            await writeFile(basePath, 'goal.md', content);
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
    async function viewFile(filePath, viewerId) {
        var viewer = document.getElementById(viewerId);
        if (!viewer) return;
        if (viewer.classList.contains('visible')) {
            viewer.classList.remove('visible');
            return;
        }
        try {
            var content = await readFile(basePath, filePath);
            if (content === null) {
                viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
            } else {
                viewer.innerHTML = renderFileContent(filePath, content);
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
                var content = await readFile(basePath, filePath);
                if (content === null) {
                    viewer.innerHTML = '<p class="placeholder">File not found.</p>';
                } else {
                    viewer.innerHTML = renderFileContent(filePath, content);
                }
            } catch (e) {
                viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error: ' + escapeHtml(e.message) + '</p>';
            }
        });
    });

    // === Copy Script ===
    document.getElementById('copy-script').addEventListener('click', async function () {
        try {
            var content = await readFile(basePath, getScriptFilename());
            if (content === null) {
                showToast('No script generated yet. Run the pipeline first.', 'error');
                return;
            }
            await navigator.clipboard.writeText(content);
            this.innerHTML = '<span class="btn-icon">✓</span> Copied!';
            var self = this;
            setTimeout(function () {
                self.innerHTML = '<span class="btn-icon">📋</span> Copy';
            }, 2000);
        } catch (e) {
            showToast('Failed to copy: ' + e.message, 'error');
        }
    });

    // === Download Script ===
    document.getElementById('download-script').addEventListener('click', async function () {
        try {
            var content = await readFile(basePath, getScriptFilename());
            if (content === null) {
                showToast('No script generated yet. Run the pipeline first.', 'error');
                return;
            }
            var blob = new Blob([content], { type: getScriptMimeType() });
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = getScriptDownloadName();
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            showToast('Script downloaded!', 'success');
        } catch (e) {
            showToast('Failed to download: ' + e.message, 'error');
        }
    });

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
        if (fill) {
            fill.classList.remove('indeterminate');
            fill.style.width = '0%';
        }
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
            if (state === 'active') {
                statusEl.textContent = 'Running…';
                statusEl.className = 'stage-status running';
            } else if (state === 'completed') {
                statusEl.textContent = 'Done';
                statusEl.className = 'stage-status done';
            } else if (state === 'error') {
                statusEl.textContent = 'Error';
                statusEl.className = 'stage-status error';
            } else if (state === 'pending') {
                statusEl.textContent = 'Pending';
                statusEl.className = 'stage-status';
            } else {
                statusEl.textContent = state;
                statusEl.className = 'stage-status done';
            }
        }
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
                if (stageCodegen && stageCodegen.classList.contains('active')) conn1.classList.add('active');
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

    // === Session Links via linkManager ===
    function handleSessionLinkUpdate(target, taskInfo) {
        linkManager.update(target, taskInfo);
        if (taskInfo.sessionId) {
            childSessionIds.add(taskInfo.sessionId);
        }
    }

    // === Status Polling ===
    function startStatusPolling() {
        if (statusPoller) return;
        statusPoller = createStatusPoller(basePath, function (target, taskInfo) {
            var normalizedTarget = normalizePath(target);

            if (taskInfo.sessionId) {
                childSessionIds.add(taskInfo.sessionId);
            }

            // Don't regress targets already confirmed completed
            if (completedTargets[normalizedTarget] && taskInfo.status === 'RUNNING') {
                return;
            }

            var badgeId = badgeMap[normalizedTarget];
            var stageId = stageMap[normalizedTarget];

            if (badgeId) {
                if (taskInfo.status === 'RUNNING') setBadge(badgeId, 'running');
                else if (taskInfo.status === 'COMPLETED') setBadge(badgeId, 'done');
                else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') setBadge(badgeId, 'error');
            }
            if (stageId) {
                var stageEl = document.getElementById(stageId);
                if (stageEl) {
                    if (taskInfo.status === 'RUNNING') {
                        stageEl.textContent = 'Running…';
                        stageEl.className = 'stage-status running';
                    } else if (taskInfo.status === 'COMPLETED') {
                        stageEl.textContent = 'Done';
                        stageEl.className = 'stage-status done';
                    } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                        stageEl.textContent = 'Error';
                        stageEl.className = 'stage-status error';
                    }
                }
            }

            handleSessionLinkUpdate(normalizedTarget, taskInfo);
        }, 3000);
        statusPoller.start();
    }

    function stopStatusPolling() {
        if (statusPoller) {
            statusPoller.stop();
            statusPoller = null;
        }
    }

    // === Run Operation Buttons ===
    document.querySelectorAll('.btn-run').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            if (!validateModelSettings()) {
                promptForModelSettings();
                return;
            }

            var goalContent = goalEditor.value;
            if (!goalContent.trim()) {
                showToast('Please enter a goal first.', 'error');
                return;
            }
            await writeFile(basePath, 'goal.md', goalContent);
            updatePipelineStage('goal', 'completed');
            delete completedTargets[outputPath];

            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();
            showProgress('Running ' + opPath + '…', -1);

            try {
                var taskId = await runDocOp(sessionId, opPath, outputPath, getModelSettings());
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    handleSessionLinkUpdate(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(basePath, outputPath);
                completedTargets[normalizePath(outputPath)] = true;
                setBadge(badgeId, 'done');

                var btnStageMap = {};
                btnStageMap[getScriptFilename()] = 'codegen';
                btnStageMap['code/fix_log.md'] = 'run';
                var btnStage = btnStageMap[outputPath];
                if (btnStage) updatePipelineStage(btnStage, 'completed');

                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        var content = await readFile(basePath, outputPath);
                        if (content) {
                            viewer.innerHTML = renderFileContent(outputPath, content);
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
                stopStatusPolling();
            }
        });
    });

    // === Batch Execution ===
    async function runSequential(steps) {
        var totalSteps = steps.length;
        for (var i = 0; i < totalSteps; i++) {
            var step = steps[i];
            batchLogger.log('Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');
            if (step.stage) updatePipelineStage(step.stage, 'active');
            showProgress(step.label + '…', Math.round((i / totalSteps) * 100));

            try {
                var taskId = await runDocOp(sessionId, step.op, step.output, getModelSettings());
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    batchLogger.logHtml('Session: <a href="' + getProxyUrl(cleanTaskId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId.substring(0, 12) + '…)</a>', 'info');
                    handleSessionLinkUpdate(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
                }
                await waitForTask(basePath, step.output);
                completedTargets[normalizePath(step.output)] = true;
                setBadge(step.badge, 'done');
                if (step.stage) updatePipelineStage(step.stage, 'completed');
                batchLogger.log('✓ Completed: ' + step.label, 'success');

                if (step.viewer) {
                    try {
                        var content = await readFile(basePath, step.output);
                        if (content) {
                            var viewer = document.getElementById(step.viewer);
                            if (viewer) {
                                viewer.innerHTML = renderFileContent(step.output, content);
                                viewer.classList.add('visible');
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }

                if (step.afterFn) await step.afterFn();
            } catch (e) {
                setBadge(step.badge, 'error');
                if (step.stage) updatePipelineStage(step.stage, 'error');
                batchLogger.log('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
        showProgress('Complete', 100);
    }

    document.getElementById('run-all').addEventListener('click', async function () {
        var goalContent = goalEditor.value;

        if (!validateModelSettings()) {
            promptForModelSettings();
            return;
        }

        this.disabled = true;
        startStatusPolling();
        batchLogger.clear();
        completedTargets = {};

        try {
            if (goalContent.trim()) {
                await writeFile(basePath, 'goal.md', goalContent);
                batchLogger.log('✓ Goal saved', 'success');
            }
            updatePipelineStage('goal', 'completed');

            var skipGeneration = false;
            var modeManualBtn = document.getElementById('mode-manual');
            var isManualMode = modeManualBtn && modeManualBtn.classList.contains('active');

            if (isManualMode) {
                var manualScript = scriptEditor ? scriptEditor.value.trim() : '';
                if (manualScript) {
                    await writeFile(basePath, getScriptFilename(), manualScript);
                    batchLogger.log('✓ Manual script saved (skipping AI generation)', 'success');
                    setBadge('badge-codegen', 'done');
                    updatePipelineStage('codegen', 'completed');
                    skipGeneration = true;
                }
            }

            if (!skipGeneration) {
                var codegenBadge = document.getElementById('badge-codegen');
                var codegenAlreadyDone = codegenBadge && codegenBadge.classList.contains('done');
                if (codegenAlreadyDone && isManualMode) {
                    batchLogger.log('✓ Using existing script (skipping AI generation)', 'info');
                    skipGeneration = true;
                }
            }

            if (!skipGeneration && !goalContent.trim()) {
                showToast('Please enter a goal or paste a script first.', 'error');
                batchLogger.log('✗ No goal and no script provided', 'error');
                this.disabled = false;
                hideProgress();
                stopStatusPolling();
                return;
            }

            var steps = [];

            if (!skipGeneration) {
                steps.push({
                    op: getCodegenOp(),
                    output: getScriptFilename(),
                    badge: 'badge-codegen',
                    viewer: 'viewer-codegen',
                    label: currentPlatform === 'cmd' ? 'Generate CMD Script' : 'Generate Shell Script',
                    stage: 'codegen'
                });
            }

            steps.push({
                op: getRunOp(),
                output: 'code/fix_log.md',
                badge: 'badge-run',
                viewer: 'viewer-run',
                label: 'Run & Auto-Fix',
                stage: 'run',
                afterFn: async function () {
                    try {
                        var scriptContent = await readFile(basePath, getScriptFilename());
                        if (scriptContent) {
                            var sv = document.getElementById('viewer-codegen');
                            if (sv && sv.classList.contains('visible')) {
                                sv.innerHTML = renderScript(scriptContent);
                            }
                            if (scriptEditor && panelManual && panelManual.classList.contains('active')) {
                                scriptEditor.value = scriptContent;
                            }
                        }
                    } catch (e) { /* non-critical */ }
                }
            });

            await runSequential(steps);
            batchLogger.log('🎉 Pipeline complete!', 'success');
            showToast('Pipeline complete!', 'success');
            updateResultsSummary('Generated', 'Success');

            // Refresh usage data in background
            collectChildSessionIds();
            loadUsageDataAsync().catch(function () { /* non-critical */ });

            // Auto-switch to results tab
            navigateToSection('section-results');
            await loadResults();
        } catch (e) {
            batchLogger.log('Pipeline stopped due to error.', 'error');
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
        try {
            var scriptContent = await readFile(basePath, getScriptFilename());
            var scriptViewer = document.getElementById('result-script');
            if (scriptViewer && scriptContent) {
                scriptViewer.innerHTML = renderScript(scriptContent);
            }
        } catch (e) { /* ignore */ }

        try {
            var logContent = await readFile(basePath, 'code/fix_log.md');
            var logViewer = document.getElementById('result-log');
            if (logViewer && logContent) {
                logViewer.innerHTML = renderMarkdown(logContent);
            }
        } catch (e) { /* ignore */ }

        try {
            var goalContent = await readFile(basePath, 'goal.md');
            var goalViewer = document.getElementById('result-goal');
            if (goalViewer && goalContent) {
                goalViewer.innerHTML = renderMarkdown(goalContent);
            }
        } catch (e) { /* ignore */ }
    }

    // === Check Existing Files on Load ===
    async function checkExistingFiles() {
        var statusData = await fetchDocopsStatus(basePath);
        var anyRunning = false;

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                var normalizedTarget = normalizePath(target);

                if (taskInfo.sessionId) {
                    childSessionIds.add(taskInfo.sessionId);
                }

                var badgeId = badgeMap[normalizedTarget];
                if (badgeId) {
                    if (taskInfo.status === 'RUNNING') {
                        setBadge(badgeId, 'running');
                        anyRunning = true;
                    } else if (taskInfo.status === 'COMPLETED') setBadge(badgeId, 'done');
                    else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') setBadge(badgeId, 'error');
                }

                handleSessionLinkUpdate(normalizedTarget, taskInfo);

                var stageMapDiagram = {};
                stageMapDiagram[getScriptFilename()] = 'codegen';
                stageMapDiagram['code/fix_log.md'] = 'run';
                var otherScript = currentPlatform === 'cmd' ? 'code/script.sh' : 'code/script.cmd';
                stageMapDiagram[otherScript] = 'codegen';
                var diagramStage = stageMapDiagram[normalizedTarget];
                if (diagramStage) {
                    if (taskInfo.status === 'RUNNING') updatePipelineStage(diagramStage, 'active');
                    else if (taskInfo.status === 'COMPLETED') updatePipelineStage(diagramStage, 'completed');
                    else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') updatePipelineStage(diagramStage, 'error');
                }
            }
        }

        // Fall back to file existence checks
        var checks = [
            { file: getScriptFilename(), badge: 'badge-codegen' },
            { file: 'code/fix_log.md', badge: 'badge-run' }
        ];
        for (var i = 0; i < checks.length; i++) {
            var check = checks[i];
            var badge = document.getElementById(check.badge);
            if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
            try {
                var content = await readFile(basePath, check.file);
                if (content !== null && content.trim().length > 0) {
                    setBadge(check.badge, 'done');
                    var fileStageMap = {};
                    fileStageMap[getScriptFilename()] = 'codegen';
                    fileStageMap['code/fix_log.md'] = 'run';
                    var fStage = fileStageMap[check.file];
                    if (fStage) updatePipelineStage(fStage, 'completed');
                }
            } catch (e) { /* leave as pending */ }
        }

        try {
            var goalContent = await readFile(basePath, 'goal.md');
            if (goalContent !== null && goalContent.trim().length > 0) {
                updatePipelineStage('goal', 'completed');
            }
        } catch (e) { /* ignore */ }

        if (anyRunning) {
            startStatusPolling();
        }

        try {
            var scriptExists = await readFile(basePath, getScriptFilename());
            var logExists = await readFile(basePath, 'code/fix_log.md');
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
            var content = await readFile(basePath, 'goal.md');
            if (content !== null) {
                goalEditor.value = content;
                if (charCount) {
                    charCount.textContent = content.length + ' character' + (content.length !== 1 ? 's' : '');
                }
            }
        } catch (e) {
            console.warn('Could not load goal.md:', e);
        }

        try {
            var scriptContent = await readFile(basePath, getScriptFilename());
            if (scriptContent !== null && scriptContent.trim().length > 0 && scriptEditor) {
                scriptEditor.value = scriptContent;
            }
        } catch (e) { /* ignore */ }
    }

    // === Usage Tracking ===
    function collectChildSessionIds() {
        fetchDocopsStatus(basePath).then(function (statusData) {
            if (!statusData || !statusData.tasks) return;
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                if (taskInfo.sessionId) {
                    childSessionIds.add(taskInfo.sessionId);
                }
            }
        }).catch(function () { /* ignore */ });
    }

    async function loadUsageDataAsync() {
        collectChildSessionIds();

        var allSessionIds = [];
        if (sessionId) allSessionIds.push(sessionId);
        childSessionIds.forEach(function (cid) {
            allSessionIds.push(cid);
        });

        try {
            var result = await aggregateUsage(allSessionIds);

            // Update totals display
            renderUsageSummary(result.totals, {
                prompt: document.getElementById('usage-total-prompt'),
                completion: document.getElementById('usage-total-completion'),
                total: document.getElementById('usage-total-tokens'),
                cost: document.getElementById('usage-total-cost')
            });

            // Render aggregated model table
            var tableContainer = document.getElementById('usage-table-container');
            if (tableContainer && result.models && result.models.length > 0) {
                tableContainer.innerHTML = createUsageTableHtml(result.models, result.totals);
            }

            // Render child session details
            renderChildSessionUsage(result.sessionUsageMap);
        } catch (e) {
            console.warn('Failed to load usage data:', e);
        }
    }

    function renderChildSessionUsage(sessionUsageMap) {
        var container = document.getElementById('usage-children-container');
        if (!container) return;
        if (!sessionUsageMap || Object.keys(sessionUsageMap).length === 0) return;

        container.innerHTML = '';
        for (var sid in sessionUsageMap) {
            if (!sessionUsageMap.hasOwnProperty(sid)) continue;
            // Skip the main session
            if (sid === sessionId) continue;

            var childUsage = sessionUsageMap[sid];
            if (!childUsage || !childUsage.models || childUsage.models.length === 0) continue;

            var wrapper = document.createElement('div');
            wrapper.className = 'usage-child-session';

            var header = document.createElement('div');
            header.className = 'usage-child-header';

            var label = document.createElement('span');
            label.className = 'usage-child-label';
            label.textContent = 'Session: ' + sid.substring(0, 16) + '…';

            var costBadge = document.createElement('span');
            costBadge.className = 'usage-child-cost';
            costBadge.textContent = formatCost(childUsage.totals ? childUsage.totals.cost : 0);

            var link = document.createElement('a');
            link.href = getProxyUrl(sid);
            link.target = '_blank';
            link.rel = 'noopener';
            link.className = 'usage-child-link';
            link.textContent = '📡 View Session';

            header.appendChild(label);
            header.appendChild(costBadge);
            header.appendChild(link);
            wrapper.appendChild(header);

            if (childUsage.models && childUsage.models.length > 0) {
                var tableDiv = document.createElement('div');
                tableDiv.innerHTML = createUsageTableHtml(childUsage.models, childUsage.totals);
                wrapper.appendChild(tableDiv);
            }

            container.appendChild(wrapper);
        }
    }

    // Refresh usage button
    document.getElementById('refresh-usage').addEventListener('click', function () {
        var self = this;
        self.disabled = true;
        loadUsageDataAsync().then(function () {
            showToast('Usage data refreshed', 'success');
        }).catch(function (e) {
            showToast('Failed to refresh usage: ' + e.message, 'error');
        }).finally(function () {
            self.disabled = false;
        });
    });

    // === Initialize ===
    if (!savedPlatform) {
        var ua = navigator.userAgent || '';
        if (/Windows/i.test(ua)) {
            currentPlatform = 'cmd';
        }
    }
    updatePlatformUI();
    loadInitialFiles();
    checkExistingFiles();
    loadModels();

    // Load usage when navigating to usage tab
    document.querySelectorAll('.nav-link').forEach(function (link) {
        link.addEventListener('click', function () {
            if (this.dataset.section === 'section-usage') {
                loadUsageDataAsync();
            }
        });
    });

})();