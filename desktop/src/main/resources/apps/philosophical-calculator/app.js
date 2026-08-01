import {runDocOp, fetchDocopsStatus, waitForTask, createStatusPoller} from '/lib/app/docops.js';
import {readFile, writeFile, deleteFile, listFiles} from '/lib/app/fileIO.js';
import {parseSessionUrl, getProxyUrl} from '/lib/app/session.js';
import {updateSessionLinks, createSessionLinkManager} from '/lib/app/sessionLinks.js';
import {serverUrl} from '/lib/app/config.js';
import {initMenu} from '/lib/app/menu.js';
import {
    renderMarkdown,
    escapeHtml,
    setStatus,
    setBadge,
    createBatchLogger,
    getFileIcon
} from '/lib/app/ui.js';
import {
    loadApiProviders,
    populateModelDropdowns,
    saveModelSelections,
    loadModelSelections
} from '/lib/app/models.js';
import {
    fetchUsageData,
    renderUsageSummary,
    createUsageTableHtml
} from '/lib/app/usage.js';

(function () {
    'use strict';

    // ========================================================================
    // Session & URL Setup
    // ========================================================================
    const {basePath, sessionId} = parseSessionUrl();
    if (!sessionId) {
        console.warn('Could not determine session from URL path.');
    }
    // ========================================================================
    // Shared Menubar (/lib/app/menu.js)
    // ========================================================================
    try {
        initMenu({appName: 'Philosophical Calculator'});
    } catch (e) {
        console.warn('Menu initialization failed:', e);
    }

    // ========================================================================
    // Model Management
    // ========================================================================
    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const MODEL_STORAGE_PREFIX = 'philcalc';
    var availableModels = {};

    function getModelSelectElements() {
        return {
            smartModel: document.getElementById('smart-model-select'),
            fastModel: document.getElementById('fast-model-select'),
            imageModel: document.getElementById('image-model-select')
        };
    }

    async function reloadApiProviders() {
        try {
            availableModels = await loadApiProviders();
            refreshModelDropdowns();
            renderProviderInfo();
        } catch (e) {
            console.warn('Failed to load API providers:', e);
        }
    }

    function refreshModelDropdowns() {
        var selectMap = getModelSelectElements();
        var selectArray = MODEL_KEYS.map(function (k) {
            return selectMap[k];
        }).filter(Boolean);
        if (selectArray.length === 0) return;
        var saved = loadModelSelections(MODEL_STORAGE_PREFIX, MODEL_KEYS);
        populateModelDropdowns(availableModels, selectArray, saved);
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
                var desc = model.description || '';
                html += '<span class="provider-model-tag" title="' + escapeHtml(desc) + '">' + escapeHtml(model.name) + '</span>';
            });
            html += '</div>';
            html += '</div>';
        });
        html += '</div>';
        container.innerHTML = html;
    }

    function getSelectedModels() {
        var selectMap = getModelSelectElements();
        // Per best practice: only include non-empty model keys
        var result = {};
        MODEL_KEYS.forEach(function (k) {
            var el = selectMap[k];
            if (el && el.value) result[k] = el.value;
        });
        return result;
    }

    // ========================================================================
    // Viewer State (declared early so all handlers can access)
    // ========================================================================
    var viewerRawContent = {}; // viewerId -> raw markdown string
    var viewerModes = {};      // viewerId -> 'rendered' | 'markdown'
    var zoomedViewerId = null;

    // ========================================================================
    // Session Link Manager
    // ========================================================================
    var linkManager = createSessionLinkManager(getProxyUrl);

    // Map of output file -> viewer ID for session-link container injection
    var targetToViewer = {
        'summary.md': 'viewer-summary',
        'content.md': 'viewer-content',
        'brainstorm.md': 'viewer-brainstorm',
        'dialectical.md': 'viewer-dialectical',
        'socratic.md': 'viewer-socratic',
        'perspectives.md': 'viewer-perspectives',
        'persuasive.md': 'viewer-persuasive',
        'gametheory.md': 'viewer-gametheory',
        'narrative.md': 'viewer-narrative',
        'debate.md': 'viewer-debate',
        'protocol.md': 'viewer-protocol',
        'comic.md': 'viewer-comic',
        'technical_explanation.md': 'viewer-technical',
        'illustrated_content.md': 'viewer-illustration',
        'page.html': 'viewer-webpage'
    };

    function ensureSessionLinkContainer(target) {
        var safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        var containerId = 'session-link-' + safeTarget;
        var container = document.getElementById(containerId);
        if (container) return containerId;
        container = document.createElement('div');
        container.id = containerId;
        container.className = 'session-link-container';
        container.setAttribute('data-session-links', target);
        var viewerIdGuess = targetToViewer[target] ||
            ('viewer-' + target.replace(/\.(md|html)$/, '').replace(/[^a-zA-Z0-9]/g, '-'));
        var viewer = document.getElementById(viewerIdGuess);
        if (viewer && viewer.parentElement) {
            viewer.parentElement.insertBefore(container, viewer);
        }
        return containerId;
    }

    function updateLinks(target, taskInfo) {
        var containerId = ensureSessionLinkContainer(target);
        linkManager.update(target, taskInfo);
        // Also explicitly call updateSessionLinks targeting our container
        updateSessionLinks(target, taskInfo, getProxyUrl, containerId);
    }

    // ========================================================================
    // DocOps Wrapper
    // ========================================================================
    async function execDocOp(opPath, targetPath) {
        var models = getSelectedModels();
        return await runDocOp(sessionId, opPath, targetPath, models);
    }

    // ========================================================================
    // Status Polling & Badge Mapping
    // ========================================================================
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
        'debate.md': 'badge-debate',
        'protocol.md': 'badge-protocol',
        'comic.md': 'badge-comic',
        'technical_explanation.md': 'badge-technical',
        'illustrated_content.md': 'badge-illustration',
        'page.html': 'badge-webpage'
    };

    function handleStatusUpdate(target, taskInfo) {
        var bid = badgeMap[target];
        if (bid) {
            if (taskInfo.status === 'RUNNING') setBadge(bid, 'running');
            else if (taskInfo.status === 'COMPLETED') setBadge(bid, 'done');
            else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') setBadge(bid, 'error');
        }
        updateLinks(target, taskInfo);
    }

    var statusPoller = createStatusPoller(basePath, handleStatusUpdate, 3000);

    function startStatusPolling() {
        statusPoller.start();
    }

    function stopStatusPolling() {
        statusPoller.stop();
    }

    // ========================================================================
    // Navigation
    // ========================================================================
    document.querySelectorAll('.nav-link').forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            var sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(function (l) {
                l.classList.remove('active');
            });
            document.querySelectorAll('.section').forEach(function (s) {
                s.classList.remove('active');
            });
            this.classList.add('active');
            var section = document.getElementById(sectionId);
            if (section) section.classList.add('active');
        });
    });

    // ========================================================================
    // Results Tabs
    // ========================================================================
    document.querySelectorAll('.results-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            document.querySelectorAll('.results-tab').forEach(function (t) {
                t.classList.remove('active');
            });
            document.querySelectorAll('.tab-panel').forEach(function (p) {
                p.classList.remove('active');
            });
            this.classList.add('active');
            var panel = document.getElementById(this.dataset.tab);
            if (panel) panel.classList.add('active');
        });
    });

    // ========================================================================
    // Save Buttons - Models
    // ========================================================================
    var saveModelBtn = document.getElementById('save-model-settings');
    if (saveModelBtn) {
        saveModelBtn.addEventListener('click', function () {
            var models = getSelectedModels();
            // Make sure to also persist empty selections (server default)
            var toSave = {};
            MODEL_KEYS.forEach(function (k) {
                toSave[k] = models[k] || '';
            });
            saveModelSelections(MODEL_STORAGE_PREFIX, toSave);
            setStatus('model-status', '✓ Model settings saved', 'success');
        });
    }

    var reloadModelBtn = document.getElementById('reload-models');
    if (reloadModelBtn) {
        reloadModelBtn.addEventListener('click', async function () {
            this.disabled = true;
            setStatus('model-status', 'Loading models…', '');
            try {
                await reloadApiProviders();
                setStatus('model-status', '✓ Models reloaded', 'success');
            } catch (e) {
                setStatus('model-status', '✗ ' + e.message, 'error');
            } finally {
                this.disabled = false;
            }
        });
    }

    // ========================================================================
    // Save Notes / Instruct
    // ========================================================================
    document.getElementById('save-notes').addEventListener('click', async function () {
        var content = document.getElementById('notes-editor').value;
        if (!content.trim()) {
            setStatus('notes-status', '✗ Notes cannot be empty', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile(basePath, 'notes/notes.md', content);
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
            await writeFile(basePath, 'instruct.md', content);
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
            var content = await readFile(basePath, filePath);
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
                var content = await readFile(basePath, this.dataset.file);
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

            // Auto-save notes/instruct
            var notesContent = document.getElementById('notes-editor').value;
            if (notesContent.trim()) {
                await writeFile(basePath, 'notes/notes.md', notesContent);
            }
            var instructContent = document.getElementById('instruct-editor').value;
            if (instructContent.trim()) {
                await writeFile(basePath, 'instruct.md', instructContent);
            }

            setBadge(badgeId, 'running');
            this.disabled = true;
            startStatusPolling();

            try {
                var taskId = await execDocOp(opPath, outputPath);
                var cleanTaskId = taskId ? String(taskId).trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    updateLinks(outputPath, {status: 'RUNNING', sessionId: cleanTaskId});
                }
                await waitForTask(basePath, outputPath, 600000, function (target, taskInfo) {
                    handleStatusUpdate(target, taskInfo);
                });
                setBadge(badgeId, 'done');

                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        var content = await readFile(basePath, outputPath);
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
        var batchLog = createBatchLogger(logId);
        for (var i = 0; i < steps.length; i++) {
            var step = steps[i];
            batchLog.log('Starting: ' + step.label, 'info');
            setBadge(step.badge, 'running');

            try {
                var taskId = await execDocOp(step.op, step.output);
                var cleanTaskId = taskId ? String(taskId).trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    var proxyUrl = getProxyUrl(cleanTaskId);
                    batchLog.logHtml('Session: <a href="' + escapeHtml(proxyUrl) + '" target="_blank" class="monitor-link">📡 Monitor (' + escapeHtml(cleanTaskId.substring(0, 12)) + '…)</a>', 'info');
                    updateLinks(step.output, {status: 'RUNNING', sessionId: cleanTaskId});
                }
                await waitForTask(basePath, step.output, 600000, function (target, taskInfo) {
                    handleStatusUpdate(target, taskInfo);
                });
                setBadge(step.badge, 'done');
                batchLog.log('✓ Completed: ' + step.label, 'success');

                if (step.viewer) {
                    try {
                        var content = await readFile(basePath, step.output);
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
                    } catch (e) { /* non-critical */
                    }
                }

                if (step.afterFn) await step.afterFn();
            } catch (e) {
                setBadge(step.badge, 'error');
                batchLog.log('✗ Failed: ' + step.label + ' — ' + e.message, 'error');
                throw e;
            }
        }
    }

    // ========================================================================
    // Core Pipeline (Summarize → Draft)
    // ========================================================================
    document.getElementById('run-core-pipeline').addEventListener('click', async function () {
        var notesContent = document.getElementById('notes-editor').value;
        if (!notesContent.trim()) {
            alert('Please enter your notes first.');
            return;
        }
        await writeFile(basePath, 'notes/notes.md', notesContent);
        var instructContent = document.getElementById('instruct-editor').value;
        if (instructContent.trim()) {
            await writeFile(basePath, 'instruct.md', instructContent);
        }

        this.disabled = true;
        var batchLog = document.getElementById('batch-log');
        if (batchLog) batchLog.innerHTML = '';
        startStatusPolling();

        var logger = createBatchLogger('batch-log');
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
            logger.log('🎉 Core pipeline complete!', 'success');
        } catch (e) {
            logger.log('Pipeline stopped due to error.', 'error');
        } finally {
            this.disabled = false;
        }
    });

    var lensDefinitions = [
        // Analysis Lenses
        {
            key: 'brainstorm',
            category: 'analysis',
            op: 'ops/brainstorm_op.md',
            output: 'brainstorm.md',
            badge: 'badge-brainstorm',
            viewer: 'viewer-brainstorm',
            label: 'Brainstorm'
        },
        {
            key: 'dialectical',
            category: 'analysis',
            op: 'ops/dialectical_op.md',
            output: 'dialectical.md',
            badge: 'badge-dialectical',
            viewer: 'viewer-dialectical',
            label: 'Dialectical Analysis'
        },
        {
            key: 'socratic',
            category: 'analysis',
            op: 'ops/socratic_op.md',
            output: 'socratic.md',
            badge: 'badge-socratic',
            viewer: 'viewer-socratic',
            label: 'Socratic Dialogue'
        },
        {
            key: 'perspectives',
            category: 'analysis',
            op: 'ops/perspectives_op.md',
            output: 'perspectives.md',
            badge: 'badge-perspectives',
            viewer: 'viewer-perspectives',
            label: 'Multi-Perspective Analysis'
        },
        {
            key: 'gametheory',
            category: 'analysis',
            op: 'ops/gametheory_op.md',
            output: 'gametheory.md',
            badge: 'badge-gametheory',
            viewer: 'viewer-gametheory',
            label: 'Game Theory Analysis'
        },
        {
            key: 'debate',
            category: 'analysis',
            op: 'ops/debate_op.md',
            output: 'debate.md',
            badge: 'badge-debate',
            viewer: 'viewer-debate',
            label: 'Historical Figure Debate'
        },
        {
            key: 'protocol',
            category: 'analysis',
            op: 'ops/protocol_op.md',
            output: 'protocol.md',
            badge: 'badge-protocol',
            viewer: 'viewer-protocol',
            label: 'Unrunnable Protocol Analysis'
        },
        // Output Lenses
        {
            key: 'persuasive',
            category: 'output',
            op: 'ops/persuasive_op.md',
            output: 'persuasive.md',
            badge: 'badge-persuasive',
            viewer: 'viewer-persuasive',
            label: 'Persuasive Essay'
        },
        {
            key: 'narrative',
            category: 'output',
            op: 'ops/narrative_op.md',
            output: 'narrative.md',
            badge: 'badge-narrative',
            viewer: 'viewer-narrative',
            label: 'Narrative Dramatization'
        },
        {
            key: 'comic',
            category: 'output',
            op: 'ops/comic_op.md',
            output: 'comic.md',
            badge: 'badge-comic',
            viewer: 'viewer-comic',
            label: 'Comic Book Generation'
        },
        {
            key: 'technical',
            category: 'output',
            op: 'ops/technical_explanation_op.md',
            output: 'technical_explanation.md',
            badge: 'badge-technical',
            viewer: 'viewer-technical',
            label: 'Technical Tutorial'
        },
        {
            key: 'webpage',
            category: 'output',
            op: 'ops/webpage_op.md',
            output: 'page.html',
            badge: 'badge-webpage',
            viewer: 'viewer-webpage',
            label: 'HTML Webpage Generation'
        }
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

        var notesContent = document.getElementById('notes-editor').value;
        if (notesContent.trim()) {
            await writeFile(basePath, 'notes/notes.md', notesContent);
        }
        var instructContent = document.getElementById('instruct-editor').value;
        if (instructContent.trim()) {
            await writeFile(basePath, 'instruct.md', instructContent);
        }

        this.disabled = true;
        var lensLog = document.getElementById('lens-batch-log');
        if (lensLog) lensLog.innerHTML = '';
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

        var logger = createBatchLogger('lens-batch-log');
        try {
            await runSequential(steps, 'lens-batch-log');
            logger.log('🎉 All selected lenses complete!', 'success');
        } catch (e) {
            logger.log('Lens execution stopped due to error: ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // Select/Deselect All
    document.getElementById('select-all-lenses').addEventListener('click', function () {
        document.querySelectorAll('.lens-check').forEach(function (cb) {
            cb.checked = true;
        });
    });

    document.getElementById('deselect-all-lenses').addEventListener('click', function () {
        document.querySelectorAll('.lens-check').forEach(function (cb) {
            cb.checked = false;
        });
    });
    var selectAllAnalysisBtn = document.getElementById('select-all-analysis');
    if (selectAllAnalysisBtn) {
        selectAllAnalysisBtn.addEventListener('click', function () {
            var analysisKeys = lensDefinitions
                .filter(function (l) {
                    return l.category === 'analysis';
                })
                .map(function (l) {
                    return l.key;
                });
            document.querySelectorAll('.lens-check').forEach(function (cb) {
                cb.checked = analysisKeys.indexOf(cb.value) >= 0;
            });
        });
    }
    var selectAllOutputsBtn = document.getElementById('select-all-outputs');
    if (selectAllOutputsBtn) {
        selectAllOutputsBtn.addEventListener('click', function () {
            var outputKeys = lensDefinitions
                .filter(function (l) {
                    return l.category === 'output';
                })
                .map(function (l) {
                    return l.key;
                });
            document.querySelectorAll('.lens-check').forEach(function (cb) {
                cb.checked = outputKeys.indexOf(cb.value) >= 0;
            });
        });
    }


    // ========================================================================
    // Check Existing Files on Load
    // ========================================================================
    async function checkExistingFiles() {
        var statusData = await fetchDocopsStatus(basePath);
        var anyRunning = false;

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                handleStatusUpdate(target, taskInfo);
                if (taskInfo.status === 'RUNNING') anyRunning = true;
            }
        }

        // Fall back to file existence checks
        var fileChecks = [
            {file: 'summary.md', badge: 'badge-summary'},
            {file: 'content.md', badge: 'badge-content'},
            {file: 'brainstorm.md', badge: 'badge-brainstorm'},
            {file: 'dialectical.md', badge: 'badge-dialectical'},
            {file: 'socratic.md', badge: 'badge-socratic'},
            {file: 'perspectives.md', badge: 'badge-perspectives'},
            {file: 'persuasive.md', badge: 'badge-persuasive'},
            {file: 'gametheory.md', badge: 'badge-gametheory'},
            {file: 'narrative.md', badge: 'badge-narrative'},
            {file: 'debate.md', badge: 'badge-debate'},
            {file: 'protocol.md', badge: 'badge-protocol'},
            {file: 'comic.md', badge: 'badge-comic'},
            {file: 'technical_explanation.md', badge: 'badge-technical'},
            {file: 'illustrated_content.md', badge: 'badge-illustration'},
            {file: 'page.html', badge: 'badge-webpage'}
        ];

        for (var i = 0; i < fileChecks.length; i++) {
            var check = fileChecks[i];
            var badge = document.getElementById(check.badge);
            if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
            try {
                var content = await readFile(basePath, check.file);
                if (content !== null && content.trim().length > 0) {
                    setBadge(check.badge, 'done');
                }
            } catch (e) { /* leave as pending */
            }
        }

        if (anyRunning) startStatusPolling();
    }

    // ========================================================================
    // Load Initial Files
    // ========================================================================
    async function loadInitialFiles() {
        try {
            var notes = await readFile(basePath, 'notes/notes.md');
            if (notes !== null) {
                document.getElementById('notes-editor').value = notes;
            }
        } catch (e) {
            console.warn('Could not load notes:', e);
        }

        try {
            var instruct = await readFile(basePath, 'instruct.md');
            if (instruct !== null) {
                document.getElementById('instruct-editor').value = instruct;
            }
        } catch (e) {
            console.warn('Could not load instruct.md:', e);
        }
    }

    // ========================================================================
    // Initialize
    // ========================================================================
    loadInitialFiles();
    checkExistingFiles();
    startStatusPolling();
    reloadApiProviders();

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

    if (uploadZone) {
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
        uploadZone.addEventListener('drop', function (e) {
            var files = e.dataTransfer.files;
            if (files && files.length > 0) {
                handleFileUpload(files);
            }
        }, false);
    }

    if (fileInput) {
        fileInput.addEventListener('change', function () {
            if (this.files && this.files.length > 0) {
                handleFileUpload(this.files);
            }
            this.value = '';
        });
    }

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
        var contentType = file.type || 'application/octet-stream';
        var resp = await fetch(basePath + '/' + filePath, {
            method: 'PUT',
            headers: {'Content-Type': contentType},
            body: file
        });
        if (!resp.ok) {
            throw new Error('Upload failed for ' + fileName + ': ' + resp.status);
        }
        return true;
    }

    async function refreshUploadedFileList() {
        if (!uploadedFilesList) return;
        try {
            var entries = await listFiles(basePath, 'notes');
            var files = [];
            if (Array.isArray(entries)) {
                files = entries.filter(function (e) {
                    if (typeof e === 'string') return true;
                    if (e && e.type === 'file') return true;
                    if (e && !e.type && e.name) return true;
                    return false;
                }).map(function (e) {
                    if (typeof e === 'string') return e;
                    return e.name || e.fileName || String(e);
                });
            }
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
        if (!uploadedFilesList) return;
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
            var icon = getFileIcon(files[i]);
            html += '<div class="file-list-item">' +
                '<span class="file-item-icon">' + icon + '</span>' +
                '<span class="file-item-name">' + fname + '</span>' +
                '<button class="btn btn-sm btn-danger-ghost file-delete-btn" data-filename="' + fname + '" title="Delete file">✕</button>' +
                '</div>';
        }
        html += '</div>';
        uploadedFilesList.innerHTML = html;

        uploadedFilesList.querySelectorAll('.file-delete-btn').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                var filename = this.dataset.filename;
                if (!confirm('Delete notes/' + filename + '?')) return;
                try {
                    await deleteFile(basePath, 'notes/' + filename);
                    setStatus('notes-status', '✓ Deleted ' + filename, 'success');
                    await refreshUploadedFileList();
                } catch (e) {
                    setStatus('notes-status', '✗ ' + e.message, 'error');
                }
            });
        });
    }

    var refreshFileListBtn = document.getElementById('refresh-file-list');
    if (refreshFileListBtn) {
        refreshFileListBtn.addEventListener('click', function () {
            refreshUploadedFileList();
        });
    }
    refreshUploadedFileList();

    // ========================================================================
    // Viewer Mode Toggle (Markdown source vs Rendered HTML) & Zoom
    // ========================================================================
    function isHtmlViewer(viewerId) {
        return viewerId === 'viewer-webpage' || viewerId === 'result-webpage';
    }

    function renderViewerContent(viewerId) {
        var viewer = document.getElementById(viewerId);
        if (!viewer) return;
        var raw = viewerRawContent[viewerId];
        if (raw === undefined || raw === null) return;
        var mode = viewerModes[viewerId] || 'rendered';

        if (isHtmlViewer(viewerId)) {
            if (mode === 'markdown' || mode === 'source') {
                viewer.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
            } else {
                var iframe = document.createElement('iframe');
                iframe.className = 'html-preview-iframe';
                iframe.setAttribute('sandbox', 'allow-same-origin');
                iframe.style.width = '100%';
                iframe.style.minHeight = '600px';
                iframe.style.border = '1px solid var(--color-border, #ccc)';
                iframe.style.background = '#fff';
                viewer.innerHTML = '';
                viewer.appendChild(iframe);
                try {
                    var doc = iframe.contentDocument || iframe.contentWindow.document;
                    doc.open();
                    doc.write(raw);
                    doc.close();
                } catch (e) {
                    viewer.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
                }
            }
            return;
        }

        if (mode === 'markdown') {
            viewer.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
        } else {
            var html = renderMarkdown(raw);
            if (!html || html.trim() === '') {
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
        var isHtml = isHtmlViewer(viewerId);
        var btnLabel;
        if (isHtml) {
            btnLabel = currentMode === 'rendered' ? '📄 Source' : '🌐 Rendered';
        } else {
            btnLabel = currentMode === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
        }
        var toolbarHtml =
            '<button class="btn btn-sm btn-toolbar btn-toggle-mode" data-viewer="' + viewerId + '" title="Toggle source / Rendered">' + btnLabel + '</button>' +
            '<button class="btn btn-sm btn-toolbar btn-zoom" data-viewer="' + viewerId + '" title="Zoom / Fullscreen">🔍 Zoom</button>';
        if (isHtml) {
            toolbarHtml +=
                '<a class="btn btn-sm btn-toolbar btn-open-newtab" href="' + escapeHtml(basePath + '/page.html') + '" target="_blank" rel="noopener" title="Open in new tab">🚀 Open in New Tab</a>';
        }
        toolbar.innerHTML = toolbarHtml;
        viewer.parentElement.insertBefore(toolbar, viewer);

        toolbar.querySelector('.btn-toggle-mode').addEventListener('click', function () {
            var vid = this.dataset.viewer;
            var current = viewerModes[vid] || 'rendered';
            viewerModes[vid] = current === 'rendered' ? 'markdown' : 'rendered';
            var isH = isHtmlViewer(vid);
            if (isH) {
                this.textContent = viewerModes[vid] === 'rendered' ? '📄 Source' : '🌐 Rendered';
            } else {
                this.textContent = viewerModes[vid] === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
            }
            renderViewerContent(vid);
            if (zoomedViewerId === vid) {
                refreshZoomBody(vid);
            }
        });

        toolbar.querySelector('.btn-zoom').addEventListener('click', function () {
            var vid = this.dataset.viewer;
            openZoomOverlay(vid);
        });
        return toolbar;
    }

    function refreshZoomBody(vid) {
        var zoomBody = document.getElementById('zoom-overlay-body');
        if (!zoomBody) return;
        var raw = viewerRawContent[vid];
        var isH = isHtmlViewer(vid);
        if (isH) {
            if (viewerModes[vid] === 'markdown') {
                zoomBody.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
            } else {
                zoomBody.innerHTML = '';
                var zIframe = document.createElement('iframe');
                zIframe.className = 'html-preview-iframe';
                zIframe.setAttribute('sandbox', 'allow-same-origin');
                zIframe.style.width = '100%';
                zIframe.style.height = '100%';
                zIframe.style.minHeight = '600px';
                zIframe.style.border = 'none';
                zIframe.style.background = '#fff';
                zoomBody.appendChild(zIframe);
                try {
                    var zDoc = zIframe.contentDocument || zIframe.contentWindow.document;
                    zDoc.open();
                    zDoc.write(raw);
                    zDoc.close();
                } catch (e) { /* ignore */
                }
            }
        } else if (viewerModes[vid] === 'markdown') {
            zoomBody.innerHTML = '<pre class="markdown-source">' + escapeHtml(raw) + '</pre>';
        } else {
            zoomBody.innerHTML = renderMarkdown(raw);
        }
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
                '<a class="btn btn-sm btn-toolbar" id="zoom-open-newtab" href="#" target="_blank" rel="noopener" style="display:none;">🚀 Open in New Tab</a>' +
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
                var isH = isHtmlViewer(zoomedViewerId);
                if (isH) {
                    this.textContent = viewerModes[zoomedViewerId] === 'rendered' ? '📄 Source' : '🌐 Rendered';
                } else {
                    this.textContent = viewerModes[zoomedViewerId] === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
                }
                renderViewerContent(zoomedViewerId);
                refreshZoomBody(zoomedViewerId);
                var inlineBtn = document.querySelector('#toolbar-' + zoomedViewerId + ' .btn-toggle-mode');
                if (inlineBtn) {
                    if (isH) {
                        inlineBtn.textContent = viewerModes[zoomedViewerId] === 'rendered' ? '📄 Source' : '🌐 Rendered';
                    } else {
                        inlineBtn.textContent = viewerModes[zoomedViewerId] === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
                    }
                }
            });
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape' && zoomedViewerId) closeZoomOverlay();
            });
        }
        var mode = viewerModes[viewerId] || 'rendered';
        var zoomTitle = document.getElementById('zoom-overlay-title');
        var zoomToggle = document.getElementById('zoom-toggle-mode');
        var zoomOpenNewtab = document.getElementById('zoom-open-newtab');
        zoomTitle.textContent = viewerId.replace(/^(viewer-|result-)/, '').replace(/-/g, ' ');
        var isHtml = isHtmlViewer(viewerId);
        if (isHtml) {
            zoomToggle.textContent = mode === 'rendered' ? '📄 Source' : '🌐 Rendered';
            if (zoomOpenNewtab) {
                zoomOpenNewtab.href = basePath + '/page.html';
                zoomOpenNewtab.style.display = '';
            }
        } else {
            zoomToggle.textContent = mode === 'rendered' ? '📝 Markdown' : '🌐 Rendered';
            if (zoomOpenNewtab) zoomOpenNewtab.style.display = 'none';
        }
        refreshZoomBody(viewerId);
        overlay.classList.add('visible');
        document.body.style.overflow = 'hidden';
    }

    function closeZoomOverlay() {
        var overlay = document.getElementById('zoom-overlay');
        if (overlay) overlay.classList.remove('visible');
        document.body.style.overflow = '';
        zoomedViewerId = null;
    }

    // ========================================================================
    // Content Editor (content.md)
    // ========================================================================
    var contentEditorEl = document.getElementById('content-editor');
    var contentEditorContainer = document.getElementById('content-editor-container');

    var editContentBtn = document.getElementById('edit-content-btn');
    if (editContentBtn) {
        editContentBtn.addEventListener('click', async function () {
            try {
                var content = await readFile(basePath, 'content.md');
                contentEditorEl.value = content || '';
            } catch (e) {
                contentEditorEl.value = '';
            }
            contentEditorContainer.classList.add('visible');
        });
    }

    var saveContentBtn = document.getElementById('save-content-btn');
    if (saveContentBtn) {
        saveContentBtn.addEventListener('click', async function () {
            var content = contentEditorEl.value;
            try {
                this.disabled = true;
                await writeFile(basePath, 'content.md', content);
                setStatus('content-editor-status', '✓ Saved content.md', 'success');
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
    }

    var closeContentEditorBtn = document.getElementById('close-content-editor-btn');
    if (closeContentEditorBtn) {
        closeContentEditorBtn.addEventListener('click', function () {
            contentEditorContainer.classList.remove('visible');
        });
    }

    // ========================================================================
    // Pipeline Manual Article Editor
    // ========================================================================
    var pipelineEditorEl = document.getElementById('pipeline-content-editor');
    var pipelineEditorContainer = document.getElementById('pipeline-content-editor-container');
    var pipelineEditorDirty = false;

    var pipelineEditBtn = document.getElementById('pipeline-edit-content-btn');
    if (pipelineEditBtn) {
        pipelineEditBtn.addEventListener('click', async function () {
            if (pipelineEditorContainer.classList.contains('visible')) {
                if (pipelineEditorDirty && !confirm('You have unsaved changes. Close without saving?')) return;
                pipelineEditorContainer.classList.remove('visible');
                pipelineEditorDirty = false;
                return;
            }
            try {
                var content = await readFile(basePath, 'content.md');
                pipelineEditorEl.value = content || '';
            } catch (e) {
                pipelineEditorEl.value = '';
            }
            pipelineEditorContainer.classList.add('visible');
            pipelineEditorDirty = false;
            updatePipelineWordCount();
            pipelineEditorEl.focus();
        });
    }

    if (pipelineEditorEl) {
        pipelineEditorEl.addEventListener('input', function () {
            pipelineEditorDirty = true;
            updatePipelineWordCount();
        });
    }

    function updatePipelineWordCount() {
        var el = document.getElementById('pipeline-editor-wordcount');
        if (!el || !pipelineEditorEl) return;
        var text = pipelineEditorEl.value.trim();
        if (!text) {
            el.textContent = '0 words';
            return;
        }
        var words = text.split(/\s+/).length;
        var chars = text.length;
        el.textContent = words.toLocaleString() + ' words · ' + chars.toLocaleString() + ' chars';
    }

    async function savePipelineContent(closeAfter) {
        if (!pipelineEditorEl) return;
        var content = pipelineEditorEl.value;
        try {
            await writeFile(basePath, 'content.md', content);
            pipelineEditorDirty = false;
            setStatus('pipeline-editor-status', '✓ Saved content.md', 'success');
            setStatus('pipeline-editor-status-bottom', '✓ Saved content.md', 'success');
            if (content.trim().length > 0) {
                setBadge('badge-content', 'done');
            }
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

    var pipelineSaveBtn = document.getElementById('pipeline-save-content-btn');
    if (pipelineSaveBtn) pipelineSaveBtn.addEventListener('click', function () {
        savePipelineContent(false);
    });
    var pipelineSaveBtnBottom = document.getElementById('pipeline-save-content-btn-bottom');
    if (pipelineSaveBtnBottom) pipelineSaveBtnBottom.addEventListener('click', function () {
        savePipelineContent(false);
    });
    var pipelineSaveCloseBtn = document.getElementById('pipeline-save-close-content-btn');
    if (pipelineSaveCloseBtn) pipelineSaveCloseBtn.addEventListener('click', function () {
        savePipelineContent(true);
    });
    var pipelineSaveCloseBtnBottom = document.getElementById('pipeline-save-close-content-btn-bottom');
    if (pipelineSaveCloseBtnBottom) pipelineSaveCloseBtnBottom.addEventListener('click', function () {
        savePipelineContent(true);
    });

    var pipelineCloseBtn = document.getElementById('pipeline-close-editor-btn');
    if (pipelineCloseBtn) {
        pipelineCloseBtn.addEventListener('click', function () {
            if (pipelineEditorDirty && !confirm('You have unsaved changes. Close without saving?')) return;
            pipelineEditorContainer.classList.remove('visible');
            pipelineEditorDirty = false;
        });
    }

    // Markdown formatting toolbar
    document.querySelectorAll('.pipeline-editor-tool').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var action = this.dataset.action;
            var ta = pipelineEditorEl;
            if (!ta) return;
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

    if (pipelineEditorEl) {
        pipelineEditorEl.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                e.preventDefault();
                savePipelineContent(false);
            }
        });
    }

    // ========================================================================
    // Usage Tracking
    // ========================================================================
    var usagePollTimer = null;
    var USAGE_POLL_INTERVAL = 10000;

    function getUsageUrls() {
        if (!sessionId) return {html: null, json: null};
        return {
            html: serverUrl('/proxy/usage?sessionId=' + encodeURIComponent(sessionId)),
            json: serverUrl('/proxy/usage?sessionId=' + encodeURIComponent(sessionId) + '&format=json')
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

    function renderUsageData(data) {
        var tableContainer = document.getElementById('usage-table-container');
        var elements = {
            prompt: document.getElementById('usage-total-prompt'),
            completion: document.getElementById('usage-total-completion'),
            cost: document.getElementById('usage-total-cost')
        };
        if (!data) {
            if (elements.prompt) elements.prompt.textContent = '—';
            if (elements.completion) elements.completion.textContent = '—';
            if (elements.cost) elements.cost.textContent = '—';
            if (tableContainer) tableContainer.innerHTML = '<p class="placeholder">No usage data available yet. Run some operations first.</p>';
            return;
        }
        var totals = data.totals || {};
        renderUsageSummary(totals, elements);

        var models = data.models || [];
        if (models.length === 0) {
            if (tableContainer) tableContainer.innerHTML = '<p class="placeholder">No model usage recorded yet.</p>';
            return;
        }
        var html = createUsageTableHtml(models, totals);
        html += '<div class="usage-last-updated">Last updated: ' + new Date().toLocaleTimeString() + '</div>';
        if (tableContainer) tableContainer.innerHTML = html;
    }

    async function refreshUsage() {
        setStatus('usage-status', 'Loading…', '');
        try {
            var data = await fetchUsageData(sessionId);
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
            var auto = document.getElementById('usage-auto-refresh');
            if (auto && !auto.checked) {
                stopUsagePolling();
                return;
            }
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

    var refreshUsageBtn = document.getElementById('refresh-usage');
    if (refreshUsageBtn) {
        refreshUsageBtn.addEventListener('click', function () {
            refreshUsage();
        });
    }

    var usageAutoRefresh = document.getElementById('usage-auto-refresh');
    if (usageAutoRefresh) {
        usageAutoRefresh.addEventListener('change', function () {
            if (this.checked) startUsagePolling();
            else stopUsagePolling();
        });
    }

    document.querySelectorAll('.nav-link').forEach(function (link) {
        link.addEventListener('click', function () {
            if (this.dataset.section === 'section-usage') {
                refreshUsage();
                startUsagePolling();
            }
        });
    });

    updateUsageLinks();

    var webpageOpenLink = document.getElementById('webpage-open-link');
    if (webpageOpenLink) {
        webpageOpenLink.href = basePath + '/page.html';
    }

})();