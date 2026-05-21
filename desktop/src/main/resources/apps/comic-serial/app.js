// Comic Serial App — refactored to use utils/ modules
import { parseSessionUrl, getProxyUrl, getAppRoot } from './utils/session.js';
import { readFile, writeFile, fileExists, listFiles } from './utils/fileIO.js';
import { runDocOp, fetchDocopsStatus, waitForTask, createStatusPoller } from './utils/docops.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { updateSessionLinks, createSessionLinkManager } from './utils/sessionLinks.js';
import {
    renderMarkdown,
    escapeHtml,
    setStatus,
    setBadge,
    showToast,
    createBatchLogger
} from './utils/ui.js';
import {
    fetchUsageData,
    formatTokenCount,
    formatCost,
    aggregateUsage,
    renderUsageSummary,
    createUsageTableHtml
} from './utils/usage.js';

(function() {
    'use strict';

    // ========================================
    // Session Setup
    // ========================================
    const { basePath, sessionId, appId } = parseSessionUrl();
    if (!sessionId) {
        console.warn('Could not determine session from URL path.');
    }

    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const MODEL_PREFIX = 'comic';
    // Legacy localStorage keys used by the old app — we'll migrate from these if present
    const LEGACY_MODEL_KEYS = {
        smartModel: 'comicSmartModel',
        fastModel: 'comicFastModel',
        imageModel: 'comicImageModel'
    };

    // Migrate legacy keys to the new prefix-based storage if necessary
    (function migrateLegacyModelKeys() {
        try {
            const existing = loadModelSelections(MODEL_PREFIX, MODEL_KEYS);
            const migrated = {};
            let didMigrate = false;
            MODEL_KEYS.forEach(function(k) {
                if (!existing[k]) {
                    const legacy = localStorage.getItem(LEGACY_MODEL_KEYS[k]);
                    if (legacy) {
                        migrated[k] = legacy;
                        didMigrate = true;
                    }
                } else {
                    migrated[k] = existing[k];
                }
            });
            if (didMigrate) {
                saveModelSelections(MODEL_PREFIX, migrated);
            }
        } catch (e) { /* ignore */ }
    })();

    // ========================================
    // Model Management
    // ========================================
    var availableModels = {};

    async function initModels() {
        try {
            availableModels = await loadApiProviders();
            populateModels();
        } catch (e) {
            console.warn('Failed to load API providers:', e);
        }
    }

    function getModelSelectElements() {
        return {
            smartModel: document.getElementById('comic-smart-model'),
            fastModel: document.getElementById('comic-fast-model'),
            imageModel: document.getElementById('comic-image-model')
        };
    }

    function populateModels() {
        var selects = getModelSelectElements();
        var selectArray = MODEL_KEYS.map(function(k) { return selects[k]; }).filter(Boolean);
        if (selectArray.length === 0) return;
        var saved = loadModelSelections(MODEL_PREFIX, MODEL_KEYS);
        // populateModelDropdowns iterates the array & assigns saved values by key index in some impls;
        // we explicitly build a savedByKey map matching MODEL_KEYS order.
        var savedByKey = {};
        MODEL_KEYS.forEach(function(k) { if (saved[k]) savedByKey[k] = saved[k]; });
        populateModelDropdowns(availableModels, selectArray, savedByKey);

        // Fallback: if the helper didn't restore selections (older signature),
        // restore them manually.
        MODEL_KEYS.forEach(function(k) {
            var sel = selects[k];
            if (sel && savedByKey[k]) {
                var has = Array.from(sel.options).some(function(o) { return o.value === savedByKey[k]; });
                if (has) sel.value = savedByKey[k];
            }
        });

        updateModelSummary();
    }

    function getSelectedModels() {
        var selects = getModelSelectElements();
        return {
            smartModel: selects.smartModel ? selects.smartModel.value : '',
            fastModel: selects.fastModel ? selects.fastModel.value : '',
            imageModel: selects.imageModel ? selects.imageModel.value : ''
        };
    }

    // Build an object with ONLY non-empty model keys (per utils README best practice).
    function getNonEmptyModels() {
        var all = getSelectedModels();
        var out = {};
        MODEL_KEYS.forEach(function(k) {
            if (all[k]) out[k] = all[k];
        });
        return out;
    }

    function updateModelSummary() {
        var summaryEl = document.getElementById('model-summary');
        if (!summaryEl) return;
        var models = getSelectedModels();
        var rows = [
            { label: '🧠 Smart Model', value: models.smartModel },
            { label: '⚡ Fast Model', value: models.fastModel },
            { label: '🎨 Image Model', value: models.imageModel }
        ];
        var html = '';
        rows.forEach(function(row) {
            var valueClass = row.value ? 'model-summary-value' : 'model-summary-value not-set';
            var valueText = row.value || 'Not set';
            var indicatorClass = row.value ? 'model-indicator' : 'model-indicator no-model';
            html +=
                '<div class="model-summary-row">' +
                '<span class="model-summary-label">' + row.label + '</span>' +
                '<span class="' + valueClass + '">' + escapeHtml(valueText) + '</span>' +
                '<span class="' + indicatorClass + '"><span class="model-dot"></span>' + (row.value ? 'Active' : 'None') + '</span>' +
                '</div>';
        });
        summaryEl.innerHTML = html;
    }

    // Save model settings
    var saveModelBtn = document.getElementById('save-model-settings');
    if (saveModelBtn) {
        saveModelBtn.addEventListener('click', function() {
            var models = getSelectedModels();
            saveModelSelections(MODEL_PREFIX, models);
            updateModelSummary();
            setStatus('model-status', '✓ Model settings saved', 'success');
        });
    }

    // Reset model settings
    var resetModelBtn = document.getElementById('reset-model-settings');
    if (resetModelBtn) {
        resetModelBtn.addEventListener('click', function() {
            saveModelSelections(MODEL_PREFIX, { smartModel: '', fastModel: '', imageModel: '' });
            // Also clean up legacy keys
            Object.values(LEGACY_MODEL_KEYS).forEach(function(k) {
                try { localStorage.removeItem(k); } catch (e) { /* ignore */ }
            });
            var selects = getModelSelectElements();
            MODEL_KEYS.forEach(function(k) {
                var sel = selects[k];
                if (sel && sel.options.length > 0) sel.selectedIndex = 0;
            });
            updateModelSummary();
            setStatus('model-status', '✓ Model settings reset to defaults', 'success');
        });
    }

    // Update summary when dropdowns change
    ['comic-smart-model', 'comic-fast-model', 'comic-image-model'].forEach(function(id) {
        var el = document.getElementById(id);
        if (el) {
            el.addEventListener('change', function() {
                updateModelSummary();
            });
        }
    });

    // ========================================
    // DocOps Wrapper
    // ========================================
    async function runOp(opPath, targetPath) {
        var models = getNonEmptyModels();
        return await runDocOp(sessionId, opPath, targetPath, models);
    }

    // ========================================
    // Session Link Tracking (persistent — see utils/README best practices)
    // ========================================
    var linkManager = createSessionLinkManager(getProxyUrl);
    // Persistent map of every task we've ever seen, so links stay visible
    // even after tasks complete.
    var trackedTasks = {}; // target -> taskInfo

    // Legacy task->session storage (used by usage tracking)
    var taskSessionMap = {};
    try {
        var savedTaskSessions = sessionStorage.getItem('comicTaskSessions');
        if (savedTaskSessions) {
            taskSessionMap = JSON.parse(savedTaskSessions);
        }
    } catch (e) { /* ignore */ }

    function trackSession(target, taskInfo) {
        if (!target || !taskInfo) return;
        trackedTasks[target] = taskInfo;
        if (taskInfo.sessionId) {
            taskSessionMap[target] = taskInfo.sessionId;
            try {
                sessionStorage.setItem('comicTaskSessions', JSON.stringify(taskSessionMap));
            } catch (e) { /* ignore */ }
        }
        // Render via sessionLinks util (handles its own container resolution)
        updateSessionLinks(target, taskInfo, getProxyUrl);
        linkManager.update(target, taskInfo);
        // Determine an appropriate container near the relevant viewer
        ensureContainerNearViewer(target);
    }

    function ensureContainerNearViewer(target) {
        var safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        var containerId = 'session-link-' + safeTarget;
        // The sessionLinks util may have already created/used a container with a
        // different id. We additionally place a data-session-links container near
        // the relevant viewer for this app, if one doesn't already exist.
        if (document.querySelector('[data-session-links="' + target + '"]')) return;
        if (document.getElementById(containerId)) return;

        var viewer = null;
        if (target === 'comicbook.html') {
            viewer = document.getElementById('viewer-htmlbook');
        } else {
            var episodeMatch = target.match(/comic_(\d+)/);
            if (episodeMatch) {
                var epNum = episodeMatch[1];
                viewer = document.getElementById('viewer-comic-' + epNum) || document.getElementById('viewer-sequel');
            } else {
                viewer = document.getElementById('viewer-sequel');
            }
        }
        if (viewer && viewer.parentElement) {
            var container = document.createElement('div');
            container.id = containerId;
            container.className = 'session-link-container';
            container.setAttribute('data-session-links', target);
            viewer.parentElement.insertBefore(container, viewer);
            // Re-render now that the container exists
            updateSessionLinks(target, trackedTasks[target], getProxyUrl);
        }
    }

    // ========================================
    // Status Polling
    // ========================================
    var activeTasks = {};
    var statusPoller = null;

    function getStatusPoller() {
        if (!statusPoller) {
            statusPoller = createStatusPoller(basePath, function(target, taskInfo) {
                trackSession(target, taskInfo);
                // Auto-unregister completed/failed tasks
                if (activeTasks[target] && (taskInfo.status === 'COMPLETED' || taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED')) {
                    unregisterActiveTask(target);
                }
            }, 3000);
        }
        return statusPoller;
    }

    function registerActiveTask(targetPath) {
        activeTasks[targetPath] = true;
        try {
            getStatusPoller().start();
        } catch (e) { /* ignore */ }
    }

    function unregisterActiveTask(targetPath) {
        delete activeTasks[targetPath];
        if (Object.keys(activeTasks).length === 0) {
            try {
                getStatusPoller().stop();
            } catch (e) { /* ignore */ }
        }
    }

    // ========================================
    // Comic Display (HTML iframe with toolbar)
    // ========================================
    var comicDisplayCounter = 0;

    function renderHtmlFile(filePath) {
        var url = basePath + '/' + filePath + '?t=' + Date.now();
        var id = 'comic-display-' + (++comicDisplayCounter);
        return '<div class="comic-display" id="' + id + '">' +
            '<div class="comic-display-toolbar">' +
            '<span class="size-label" data-size-label></span>' +
            '<button type="button" data-action="size-down" title="Decrease height">−</button>' +
            '<button type="button" data-action="size-up" title="Increase height">+</button>' +
            '<button type="button" data-action="fullscreen" title="Toggle fullscreen">⛶</button>' +
            '</div>' +
            '<iframe class="comic-iframe" src="' + escapeHtml(url) + '" frameborder="0" sandbox="allow-same-origin allow-scripts"></iframe>' +
            '<div class="resize-hint"><svg viewBox="0 0 16 16"><path d="M14 14H10L14 10V14ZM14 6L6 14H2L14 2V6Z"/></svg></div>' +
            '</div>';
    }

    // ========================================
    // Comic Display Controls (resize / fullscreen)
    // ========================================
    var activeFullscreenDisplay = null;
    var fullscreenBackdrop = null;

    function getFullscreenBackdrop() {
        if (!fullscreenBackdrop) {
            fullscreenBackdrop = document.createElement('div');
            fullscreenBackdrop.className = 'fullscreen-backdrop';
            fullscreenBackdrop.style.display = 'none';
            document.body.appendChild(fullscreenBackdrop);
            fullscreenBackdrop.addEventListener('click', function() {
                if (activeFullscreenDisplay) {
                    exitFullscreen(activeFullscreenDisplay);
                }
            });
        }
        return fullscreenBackdrop;
    }

    function enterFullscreen(displayEl) {
        if (displayEl.requestFullscreen) {
            displayEl.requestFullscreen().catch(function() {
                fallbackFullscreen(displayEl);
            });
            displayEl.classList.add('fullscreen');
            activeFullscreenDisplay = displayEl;
            updateSizeLabel(displayEl);
            return;
        }
        fallbackFullscreen(displayEl);
    }

    function fallbackFullscreen(displayEl) {
        var backdrop = getFullscreenBackdrop();
        backdrop.style.display = 'block';
        displayEl.classList.add('fullscreen');
        activeFullscreenDisplay = displayEl;
        updateSizeLabel(displayEl);
    }

    function exitFullscreen(displayEl) {
        if (document.fullscreenElement === displayEl) {
            document.exitFullscreen().catch(function() { });
        }
        displayEl.classList.remove('fullscreen');
        var backdrop = getFullscreenBackdrop();
        backdrop.style.display = 'none';
        activeFullscreenDisplay = null;
        updateSizeLabel(displayEl);
    }

    function toggleFullscreen(displayEl) {
        if (displayEl.classList.contains('fullscreen')) {
            exitFullscreen(displayEl);
        } else {
            enterFullscreen(displayEl);
        }
    }

    function updateSizeLabel(displayEl) {
        var label = displayEl.querySelector('[data-size-label]');
        if (!label) return;
        if (displayEl.classList.contains('fullscreen')) {
            label.textContent = 'fullscreen';
        } else {
            label.textContent = Math.round(displayEl.offsetWidth) + ' × ' + Math.round(displayEl.offsetHeight);
        }
    }

    document.addEventListener('fullscreenchange', function() {
        if (!document.fullscreenElement && activeFullscreenDisplay) {
            activeFullscreenDisplay.classList.remove('fullscreen');
            updateSizeLabel(activeFullscreenDisplay);
            activeFullscreenDisplay = null;
        }
    });

    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && activeFullscreenDisplay && !document.fullscreenElement) {
            exitFullscreen(activeFullscreenDisplay);
        }
    });

    document.addEventListener('click', function(e) {
        var btn = e.target.closest('.comic-display-toolbar button');
        if (!btn) return;
        var displayEl = btn.closest('.comic-display');
        if (!displayEl) return;
        var action = btn.dataset.action;
        if (action === 'fullscreen') {
            toggleFullscreen(displayEl);
        } else if (action === 'size-up') {
            if (!displayEl.classList.contains('fullscreen')) {
                var h = displayEl.offsetHeight;
                displayEl.style.height = Math.min(h + 150, 2000) + 'px';
                updateSizeLabel(displayEl);
            }
        } else if (action === 'size-down') {
            if (!displayEl.classList.contains('fullscreen')) {
                var h2 = displayEl.offsetHeight;
                displayEl.style.height = Math.max(h2 - 150, 200) + 'px';
                updateSizeLabel(displayEl);
            }
        }
    });

    var resizeObserver = new ResizeObserver(function(entries) {
        for (var i = 0; i < entries.length; i++) {
            var el = entries[i].target;
            if (el.classList.contains('comic-display')) {
                updateSizeLabel(el);
            }
        }
    });

    var bodyObserver = new MutationObserver(function(mutations) {
        for (var i = 0; i < mutations.length; i++) {
            var added = mutations[i].addedNodes;
            for (var j = 0; j < added.length; j++) {
                if (added[j].nodeType !== 1) continue;
                if (added[j].classList && added[j].classList.contains('comic-display')) {
                    resizeObserver.observe(added[j]);
                    updateSizeLabel(added[j]);
                }
                var nested = added[j].querySelectorAll ? added[j].querySelectorAll('.comic-display') : [];
                for (var k = 0; k < nested.length; k++) {
                    resizeObserver.observe(nested[k]);
                    updateSizeLabel(nested[k]);
                }
            }
        }
    });
    bodyObserver.observe(document.body, { childList: true, subtree: true });

    // ========================================
    // Loading Overlay
    // ========================================
    function showLoading(text) {
        var overlay = document.getElementById('loading-overlay');
        var loadingText = document.getElementById('loading-text');
        if (loadingText) loadingText.textContent = text || 'Processing...';
        if (overlay) overlay.classList.remove('hidden');
    }

    function hideLoading() {
        var overlay = document.getElementById('loading-overlay');
        if (overlay) overlay.classList.add('hidden');
    }

    // ========================================
    // Batch Logger
    // ========================================
    var batchLogger = createBatchLogger('batch-log');
    function logBatch(message, type) {
        // Also ensure the log element is visible (the util may not handle this).
        var logEl = document.getElementById('batch-log');
        if (logEl) logEl.classList.add('visible');
        batchLogger.log(message, type);
    }
    function logBatchHtml(html, type) {
        var logEl = document.getElementById('batch-log');
        if (logEl) logEl.classList.add('visible');
        if (typeof batchLogger.logHtml === 'function') {
            batchLogger.logHtml(html, type);
        } else {
            // Fallback if util doesn't expose logHtml
            if (!logEl) return;
            var entry = document.createElement('div');
            entry.className = 'log-entry log-' + (type || 'info');
            var ts = new Date().toLocaleTimeString();
            entry.innerHTML = '[' + ts + '] ' + html;
            logEl.appendChild(entry);
            logEl.scrollTop = logEl.scrollHeight;
        }
    }

    // ========================================
    // Episode Counting
    // ========================================
    async function countEpisodes() {
        var count = 0;
        for (var i = 1; i <= 100; i++) {
            var exists = await fileExists(basePath, 'comic_' + i + '.md');
            if (!exists) {
                exists = await fileExists(basePath, 'comic_' + i + '.html');
            }
            if (exists) {
                count = i;
            } else {
                break;
            }
        }
        return count;
    }

    async function updateEpisodeCount() {
        var count = await countEpisodes();
        var countEl = document.getElementById('episode-count');
        var nextEl = document.getElementById('next-episode-label');
        if (countEl) countEl.textContent = count.toString();
        if (nextEl) nextEl.textContent = '#' + (count + 1);
        return count;
    }

    // ========================================
    // Navigation
    // ========================================
    document.querySelectorAll('.nav-link').forEach(function(link) {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            var sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(function(l) {
                l.classList.remove('active');
            });
            document.querySelectorAll('.section').forEach(function(s) {
                s.classList.remove('active');
            });
            this.classList.add('active');
            var section = document.getElementById(sectionId);
            if (section) section.classList.add('active');

            if (sectionId === 'section-series') {
                loadSeries();
            }
            if (sectionId === 'section-pipeline') {
                updateEpisodeCount();
            }
            if (sectionId === 'section-usage') {
                refreshUsage();
            }
        });
    });

    // ========================================
    // Save Idea
    // ========================================
    var saveIdeaBtn = document.getElementById('save-idea');
    if (saveIdeaBtn) {
        saveIdeaBtn.addEventListener('click', async function() {
            var content = document.getElementById('idea-editor').value;
            if (!content.trim()) {
                setStatus('idea-status', '✗ Please enter an idea first', 'error');
                return;
            }
            try {
                this.disabled = true;
                await writeFile(basePath, 'idea.md', content);
                setStatus('idea-status', '✓ Saved successfully', 'success');
            } catch (e) {
                setStatus('idea-status', '✗ ' + e.message, 'error');
            } finally {
                this.disabled = false;
            }
        });
    }

    // ========================================
    // View File Buttons
    // ========================================
    async function viewFile(filePath, viewerId) {
        var viewer = document.getElementById(viewerId);
        if (!viewer) return;

        if (viewer.classList.contains('visible') && viewer.innerHTML.trim()) {
            viewer.classList.remove('visible');
            viewer.innerHTML = '';
            return;
        }

        try {
            var htmlPath = filePath.replace(/\.md$/, '.html');
            var isHtml = filePath.endsWith('.html');
            var exists = isHtml ? await fileExists(basePath, filePath) : await fileExists(basePath, htmlPath);
            if (exists) {
                viewer.innerHTML = renderHtmlFile(isHtml ? filePath : htmlPath);
            } else {
                if (!isHtml) {
                    var mdContent = await readFile(basePath, filePath);
                    if (mdContent !== null) {
                        viewer.innerHTML = renderMarkdown(mdContent);
                    } else {
                        viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                    }
                } else {
                    viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                }
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

    // ========================================
    // Run Single Operation (Comic #1)
    // ========================================
    document.querySelectorAll('.btn-run').forEach(function(btn) {
        btn.addEventListener('click', async function() {
            var opPath = this.dataset.op;
            var badgeId = this.dataset.badge;
            var outputPath = this.dataset.output;
            var viewerId = this.dataset.viewer;

            var ideaContent = document.getElementById('idea-editor').value;
            if (!ideaContent.trim()) {
                alert('Please enter and save your idea first!');
                return;
            }
            await writeFile(basePath, 'idea.md', ideaContent);

            setBadge(badgeId, 'running');
            this.disabled = true;
            registerActiveTask(outputPath);

            try {
                var taskId = await runOp(opPath, outputPath);
                var cleanTaskId = taskId ? String(taskId).trim() : '';
                if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    trackSession(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
                }

                await waitForTask(basePath, outputPath, 600000, function(target, taskInfo) {
                    trackSession(target, taskInfo);
                });
                setBadge(badgeId, 'done');

                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        var htmlPath = outputPath.replace(/\.md$/, '.html');
                        var exists = await fileExists(basePath, htmlPath);
                        if (exists) {
                            viewer.innerHTML = renderHtmlFile(htmlPath);
                            viewer.classList.add('visible');
                        }
                    }
                }

                await updateEpisodeCount();
            } catch (e) {
                setBadge(badgeId, 'error');
                unregisterActiveTask(outputPath);
                alert('Operation failed: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    });

    // ========================================
    // Generate Sequel
    // ========================================
    async function generateSequel() {
        var count = await countEpisodes();
        if (count === 0) {
            alert('No episodes exist yet. Please generate Comic #1 first!');
            return null;
        }

        var nextNum = count + 1;
        var outputPath = 'comic_' + nextNum + '.md';

        setBadge('badge-sequel', 'running');
        registerActiveTask(outputPath);

        try {
            var taskId = await runOp('ops/sequel_op.md', outputPath);
            var cleanTaskId = taskId ? String(taskId).trim() : '';
            if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                trackSession(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
            }

            await waitForTask(basePath, outputPath, 600000, function(target, taskInfo) {
                trackSession(target, taskInfo);
            });
            setBadge('badge-sequel', 'done');

            var viewer = document.getElementById('viewer-sequel');
            if (viewer) {
                var htmlPath = outputPath.replace(/\.md$/, '.html');
                var exists = await fileExists(basePath, htmlPath);
                if (exists) {
                    viewer.innerHTML = '<h4>Comic #' + nextNum + '</h4>' + renderHtmlFile(htmlPath);
                    viewer.classList.add('visible');
                } else {
                    var mdContent = await readFile(basePath, outputPath);
                    if (mdContent) {
                        viewer.innerHTML = renderMarkdown(mdContent);
                        viewer.classList.add('visible');
                    }
                }
            }

            await updateEpisodeCount();
            return nextNum;
        } catch (e) {
            setBadge('badge-sequel', 'error');
            unregisterActiveTask(outputPath);
            throw e;
        }
    }

    var generateSequelBtn = document.getElementById('generate-sequel');
    if (generateSequelBtn) {
        generateSequelBtn.addEventListener('click', async function() {
            this.disabled = true;
            try {
                await generateSequel();
            } catch (e) {
                alert('Failed to generate sequel: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    }

    var refreshCountBtn = document.getElementById('refresh-count');
    if (refreshCountBtn) {
        refreshCountBtn.addEventListener('click', async function() {
            this.disabled = true;
            try {
                await updateEpisodeCount();
            } finally {
                this.disabled = false;
            }
        });
    }

    // ========================================
    // Generate HTML Book
    // ========================================
    async function generateHtmlBook() {
        var count = await countEpisodes();
        if (count === 0) {
            alert('No episodes exist yet. Please generate at least one comic first!');
            return false;
        }
        setBadge('badge-htmlbook', 'running');
        registerActiveTask('comicbook.html');
        try {
            var taskId = await runOp('ops/html_book_op.md', 'comicbook.html');
            var cleanTaskId = taskId ? String(taskId).trim() : '';
            if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                trackSession('comicbook.html', { status: 'RUNNING', sessionId: cleanTaskId });
            }
            await waitForTask(basePath, 'comicbook.html', 600000, function(target, taskInfo) {
                trackSession(target, taskInfo);
            });
            setBadge('badge-htmlbook', 'done');
            unregisterActiveTask('comicbook.html');
            if (cleanTaskId) {
                trackSession('comicbook.html', { status: 'COMPLETED', sessionId: cleanTaskId });
            }
            var viewer = document.getElementById('viewer-htmlbook');
            if (viewer) {
                var exists = await fileExists(basePath, 'comicbook.html');
                if (exists) {
                    viewer.innerHTML = renderHtmlFile('comicbook.html');
                    viewer.classList.add('visible');
                }
            }
            return true;
        } catch (e) {
            setBadge('badge-htmlbook', 'error');
            throw e;
        }
    }

    var generateHtmlbookBtn = document.getElementById('generate-htmlbook');
    if (generateHtmlbookBtn) {
        generateHtmlbookBtn.addEventListener('click', async function() {
            this.disabled = true;
            try {
                await generateHtmlBook();
            } catch (e) {
                alert('Failed to generate HTML book: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    }

    var openHtmlbookBtn = document.getElementById('open-htmlbook-tab');
    if (openHtmlbookBtn) {
        openHtmlbookBtn.addEventListener('click', async function() {
            try {
                var exists = await fileExists(basePath, 'comicbook.html');
                if (exists) {
                    var url = basePath + '/comicbook.html?t=' + Date.now();
                    window.open(url, '_blank');
                } else {
                    alert('HTML book has not been generated yet. Click "Generate HTML Book" first.');
                }
            } catch (e) {
                var url = basePath + '/comicbook.html?t=' + Date.now();
                window.open(url, '_blank');
            }
        });
    }

    // ========================================
    // Batch Generation
    // ========================================
    var batchRunBtn = document.getElementById('run-batch');
    if (batchRunBtn) {
        batchRunBtn.addEventListener('click', async function() {
            var batchCountInput = document.getElementById('batch-count');
            var totalEpisodes = parseInt(batchCountInput.value, 10);
            if (isNaN(totalEpisodes) || totalEpisodes < 1) {
                alert('Please enter a valid number of episodes (1 or more).');
                return;
            }

            var ideaContent = document.getElementById('idea-editor').value;
            if (!ideaContent.trim()) {
                alert('Please enter and save your idea first!');
                return;
            }
            await writeFile(basePath, 'idea.md', ideaContent);

            this.disabled = true;
            var logEl = document.getElementById('batch-log');
            if (logEl) logEl.innerHTML = '';

            try {
                var currentCount = await countEpisodes();
                logBatch('Current episodes: ' + currentCount, 'info');
                logBatch('Target: generate ' + totalEpisodes + ' new episode(s)', 'info');

                if (currentCount === 0) {
                    logBatch('Generating Comic #1 from idea...', 'info');
                    setBadge('badge-comic-1', 'running');
                    registerActiveTask('comic_1.md');

                    var taskId = await runOp('ops/comic_op.md', 'comic_1.md');
                    var cleanTaskId = taskId ? String(taskId).trim() : '';
                    if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                        logBatchHtml('Session: <a href="' + getProxyUrl(cleanTaskId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId + ')</a>', 'info');
                        trackSession('comic_1.md', { status: 'RUNNING', sessionId: cleanTaskId });
                    }

                    await waitForTask(basePath, 'comic_1.md', 600000, function(target, taskInfo) {
                        trackSession(target, taskInfo);
                    });
                    setBadge('badge-comic-1', 'done');
                    unregisterActiveTask('comic_1.md');
                    logBatch('✓ Comic #1 generated', 'success');
                    currentCount = 1;
                    totalEpisodes--;

                    await updateEpisodeCount();
                }

                for (var i = 0; i < totalEpisodes; i++) {
                    var nextNum = currentCount + 1 + i;
                    var outputPath = 'comic_' + nextNum + '.md';

                    logBatch('Generating Comic #' + nextNum + '...', 'info');
                    setBadge('badge-sequel', 'running');
                    registerActiveTask(outputPath);

                    var seqTaskId = await runOp('ops/sequel_op.md', outputPath);
                    var cleanSeqId = seqTaskId ? String(seqTaskId).trim() : '';
                    if (cleanSeqId && cleanSeqId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanSeqId)) {
                        logBatchHtml('Session: <a href="' + getProxyUrl(cleanSeqId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanSeqId + ')</a>', 'info');
                        trackSession(outputPath, { status: 'RUNNING', sessionId: cleanSeqId });
                    }

                    await waitForTask(basePath, outputPath, 600000, function(target, taskInfo) {
                        trackSession(target, taskInfo);
                    });
                    setBadge('badge-sequel', 'done');
                    unregisterActiveTask(outputPath);
                    logBatch('✓ Comic #' + nextNum + ' generated', 'success');

                    await updateEpisodeCount();
                }

                logBatch('🎉 Series generation complete!', 'success');
                logBatch('Compiling HTML comicbook...', 'info');
                try {
                    setBadge('badge-htmlbook', 'running');
                    registerActiveTask('comicbook.html');
                    var bookTaskId = await runOp('ops/html_book_op.md', 'comicbook.html');
                    var cleanBookId = bookTaskId ? String(bookTaskId).trim() : '';
                    if (cleanBookId && cleanBookId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanBookId)) {
                        logBatchHtml('Session: <a href="' + getProxyUrl(cleanBookId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanBookId + ')</a>', 'info');
                        trackSession('comicbook.html', { status: 'RUNNING', sessionId: cleanBookId });
                    }
                    await waitForTask(basePath, 'comicbook.html', 600000, function(target, taskInfo) {
                        trackSession(target, taskInfo);
                    });
                    setBadge('badge-htmlbook', 'done');
                    unregisterActiveTask('comicbook.html');
                    logBatch('✓ HTML comicbook compiled', 'success');
                } catch (bookErr) {
                    setBadge('badge-htmlbook', 'error');
                    unregisterActiveTask('comicbook.html');
                    logBatch('⚠ Could not compile HTML book: ' + bookErr.message, 'warn');
                }
            } catch (e) {
                logBatch('✗ Error: ' + e.message, 'error');
            } finally {
                this.disabled = false;
            }
        });
    }

    // ========================================
    // Series Display
    // ========================================
    async function loadSeries() {
        var container = document.getElementById('series-container');
        if (!container) return;
        var count = await countEpisodes();

        if (count === 0) {
            container.innerHTML =
                '<div class="card">' +
                '<p class="placeholder">No episodes yet. Go to the Pipeline tab to start generating your comic series!</p>' +
                '</div>';
            return;
        }

        var html = '';
        for (var i = 1; i <= count; i++) {
            html +=
                '<div class="episode-card" data-episode="' + i + '">' +
                '<div class="episode-header" data-episode="' + i + '">' +
                '<div class="episode-title">' +
                '<span class="episode-number">' + i + '</span>' +
                '<span class="episode-label">Episode #' + i + '</span>' +
                '</div>' +
                '<span class="episode-toggle" id="toggle-ep-' + i + '">▼</span>' +
                '</div>' +
                '<div class="episode-content" id="episode-content-' + i + '"></div>' +
                '</div>';
        }

        container.innerHTML = html;

        container.querySelectorAll('.episode-header').forEach(function(header) {
            header.addEventListener('click', function() {
                var epNum = this.dataset.episode;
                toggleEpisode(parseInt(epNum, 10));
            });
        });

        toggleEpisode(count);
    }

    async function toggleEpisode(num) {
        var contentEl = document.getElementById('episode-content-' + num);
        var toggleEl = document.getElementById('toggle-ep-' + num);
        if (!contentEl) return;

        if (contentEl.classList.contains('visible')) {
            contentEl.classList.remove('visible');
            if (toggleEl) toggleEl.classList.remove('open');
            return;
        }

        if (!contentEl.innerHTML.trim()) {
            try {
                var htmlPath = 'comic_' + num + '.html';
                var exists = await fileExists(basePath, htmlPath);
                if (exists) {
                    contentEl.innerHTML = renderHtmlFile(htmlPath);
                } else {
                    var mdContent = await readFile(basePath, 'comic_' + num + '.md');
                    if (mdContent !== null) {
                        contentEl.innerHTML = renderMarkdown(mdContent);
                    } else {
                        contentEl.innerHTML = '<p class="placeholder">Episode content not found.</p>';
                    }
                }
            } catch (e) {
                contentEl.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">Error loading episode: ' + escapeHtml(e.message) + '</p>';
            }
        }

        contentEl.classList.add('visible');
        if (toggleEl) toggleEl.classList.add('open');
    }

    var refreshSeriesBtn = document.getElementById('refresh-series');
    if (refreshSeriesBtn) {
        refreshSeriesBtn.addEventListener('click', function() {
            loadSeries();
        });
    }

    var seriesAddEpisodeBtn = document.getElementById('series-add-episode');
    if (seriesAddEpisodeBtn) {
        seriesAddEpisodeBtn.addEventListener('click', async function() {
            this.disabled = true;
            try {
                var result = await generateSequel();
                if (result) {
                    await loadSeries();
                }
            } catch (e) {
                alert('Failed to generate episode: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    }

    var seriesCompileBookBtn = document.getElementById('series-compile-book');
    if (seriesCompileBookBtn) {
        seriesCompileBookBtn.addEventListener('click', async function() {
            this.disabled = true;
            try {
                await generateHtmlBook();
                setStatus('idea-status', '✓ Book compiled', 'success');
            } catch (e) {
                alert('Failed to compile book: ' + e.message);
            } finally {
                this.disabled = false;
            }
        });
    }

    var seriesViewBookBtn = document.getElementById('series-view-book');
    if (seriesViewBookBtn) {
        seriesViewBookBtn.addEventListener('click', async function() {
            try {
                var exists = await fileExists(basePath, 'comicbook.html');
                if (exists) {
                    var url = basePath + '/comicbook.html?t=' + Date.now();
                    window.open(url, '_blank');
                } else {
                    alert('HTML book has not been generated yet. Click "Compile Book" first.');
                }
            } catch (e) {
                var url = basePath + '/comicbook.html?t=' + Date.now();
                window.open(url, '_blank');
            }
        });
    }

    // ========================================
    // Usage Tracking
    // ========================================
    var usageJsonMode = false;
    var lastUsageData = null;

    function renderUsageJson(data) {
        var jsonEl = document.getElementById('usage-json-output');
        if (!jsonEl) return;
        if (data) {
            jsonEl.textContent = JSON.stringify(data, null, 2);
        } else {
            jsonEl.textContent = '// No usage data available';
        }
    }

    function renderTaskSessions(taskUsageMap) {
        var container = document.getElementById('task-sessions-container');
        if (!container) return;
        var entries = Object.keys(taskSessionMap);
        if (entries.length === 0) {
            container.innerHTML = '<p class="placeholder">No task sessions recorded yet.</p>';
            return;
        }
        var html = '<div class="task-sessions-list">';
        entries.forEach(function(target) {
            var sid = taskSessionMap[target];
            var usage = taskUsageMap[sid];
            var proxyUrl = getProxyUrl(sid);
            var usageUrl = '/proxy/usage?sessionId=' + encodeURIComponent(sid);
            html += '<div class="task-session-row">';
            html += '<div class="task-session-info">';
            html += '<span class="task-session-target">' + escapeHtml(target) + '</span>';
            html += '<span class="task-session-id">' + escapeHtml(sid) + '</span>';
            html += '</div>';
            html += '<div class="task-session-usage">';
            if (usage && usage.totals) {
                var t = usage.totals;
                var prompt = t.prompt_tokens || 0;
                var completion = t.completion_tokens || 0;
                html += '<span class="task-token-badge" title="Prompt tokens">📤 ' + formatTokenCount(prompt) + '</span>';
                html += '<span class="task-token-badge" title="Completion tokens">📥 ' + formatTokenCount(completion) + '</span>';
                html += '<span class="task-cost-badge" title="Estimated cost">' + formatCost(t.cost) + '</span>';
            } else {
                html += '<span class="task-token-badge dim">No data</span>';
            }
            html += '</div>';
            html += '<div class="task-session-links">';
            html += '<a href="' + escapeHtml(proxyUrl) + '" target="_blank" class="btn btn-secondary btn-sm" title="View session log">📋 Log</a>';
            html += '<a href="' + escapeHtml(usageUrl) + '" target="_blank" class="btn btn-secondary btn-sm" title="View usage details">💰 Usage</a>';
            html += '</div>';
            html += '</div>';
        });
        html += '</div>';
        container.innerHTML = html;
    }

    async function refreshUsage() {
        setStatus('usage-status', 'Loading…', '');

        // Collect unique child task session IDs only (avoid parent double-count)
        var seenSessions = new Set();
        var sessionIds = [];
        for (var target in taskSessionMap) {
            if (!taskSessionMap.hasOwnProperty(target)) continue;
            var sid = taskSessionMap[target];
            if (!seenSessions.has(sid)) {
                seenSessions.add(sid);
                sessionIds.push(sid);
            }
        }
        // Fall back to parent session only if there are no child sessions
        if (sessionIds.length === 0 && sessionId) {
            sessionIds.push(sessionId);
        }

        if (sessionIds.length === 0) {
            renderUsageSummary(null, {
                prompt: document.getElementById('usage-total-prompt'),
                completion: document.getElementById('usage-total-completion'),
                total: document.getElementById('usage-total-tokens'),
                cost: document.getElementById('usage-total-cost')
            });
            var tableContainer = document.getElementById('usage-table-container');
            if (tableContainer) tableContainer.innerHTML = '<p class="placeholder">No model usage data available.</p>';
            renderUsageJson(null);
            renderTaskSessions({});
            setStatus('usage-status', 'No sessions to query', '');
            return;
        }

        var aggregated;
        try {
            aggregated = await aggregateUsage(sessionIds);
        } catch (e) {
            console.warn('aggregateUsage failed, falling back to per-session fetch:', e);
            aggregated = await fallbackAggregate(sessionIds);
        }

        var models = aggregated.models || [];
        var totals = aggregated.totals || { prompt_tokens: 0, completion_tokens: 0, cost: 0 };
        var sessionUsageMap = aggregated.sessionUsageMap || {};

        // Sort by cost descending
        models.sort(function(a, b) { return (b.cost || 0) - (a.cost || 0); });

        lastUsageData = { models: models, totals: totals };

        renderUsageSummary(totals, {
            prompt: document.getElementById('usage-total-prompt'),
            completion: document.getElementById('usage-total-completion'),
            total: document.getElementById('usage-total-tokens'),
            cost: document.getElementById('usage-total-cost')
        });

        var tableContainer = document.getElementById('usage-table-container');
        if (tableContainer) {
            if (models.length === 0) {
                tableContainer.innerHTML = '<p class="placeholder">No model usage data available.</p>';
            } else {
                tableContainer.innerHTML = createUsageTableHtml(models, totals);
            }
        }

        renderUsageJson(lastUsageData);
        renderTaskSessions(sessionUsageMap);

        setStatus('usage-status', '✓ Loaded from ' + sessionIds.length + ' session(s), ' + models.length + ' model(s)', 'success');
    }

    // Fallback aggregator in case aggregateUsage is unavailable or fails
    async function fallbackAggregate(sessionIds) {
        var allModels = {};
        var sessionUsageMap = {};
        var results = await Promise.all(sessionIds.map(function(sid) {
            return fetchUsageData(sid).then(function(data) {
                return { sid: sid, data: data };
            }).catch(function() {
                return { sid: sid, data: null };
            });
        }));
        results.forEach(function(result) {
            if (!result.data) return;
            sessionUsageMap[result.sid] = result.data;
            if (result.data.models) {
                result.data.models.forEach(function(m) {
                    var key = m.model || 'unknown';
                    if (!allModels[key]) {
                        allModels[key] = { model: key, prompt_tokens: 0, completion_tokens: 0, cost: 0 };
                    }
                    allModels[key].prompt_tokens += (m.prompt_tokens || 0);
                    allModels[key].completion_tokens += (m.completion_tokens || 0);
                    allModels[key].cost += (m.cost || 0);
                });
            }
        });
        var modelList = Object.values(allModels);
        var totalPrompt = 0, totalCompletion = 0, totalCost = 0;
        modelList.forEach(function(m) {
            totalPrompt += m.prompt_tokens;
            totalCompletion += m.completion_tokens;
            totalCost += m.cost;
        });
        return {
            models: modelList,
            totals: { prompt_tokens: totalPrompt, completion_tokens: totalCompletion, cost: totalCost },
            sessionUsageMap: sessionUsageMap
        };
    }

    var refreshUsageBtn = document.getElementById('refresh-usage');
    if (refreshUsageBtn) {
        refreshUsageBtn.addEventListener('click', function() {
            this.disabled = true;
            var self = this;
            refreshUsage().finally(function() {
                self.disabled = false;
            });
        });
    }

    var toggleJsonBtn = document.getElementById('toggle-usage-format');
    if (toggleJsonBtn) {
        toggleJsonBtn.addEventListener('click', function() {
            usageJsonMode = !usageJsonMode;
            var tableCard = document.getElementById('usage-table-card');
            var jsonCard = document.getElementById('usage-json-card');
            if (usageJsonMode) {
                if (tableCard) tableCard.style.display = 'none';
                if (jsonCard) jsonCard.style.display = 'block';
                this.textContent = '📊 Table View';
            } else {
                if (tableCard) tableCard.style.display = 'block';
                if (jsonCard) jsonCard.style.display = 'none';
                this.textContent = '{ } JSON View';
            }
        });
    }

    // ========================================
    // Initialization
    // ========================================
    async function loadInitialFiles() {
        try {
            var content = await readFile(basePath, 'idea.md');
            if (content !== null) {
                var editor = document.getElementById('idea-editor');
                if (editor) editor.value = content;
            }
        } catch (e) {
            console.warn('Could not load idea.md:', e);
        }
    }

    async function checkExistingFiles() {
        try {
            var comic1Exists = await fileExists(basePath, 'comic_1.md');
            if (comic1Exists) {
                setBadge('badge-comic-1', 'done');
            }
        } catch (e) { /* leave as pending */ }

        var episodeCount = await updateEpisodeCount();
        if (episodeCount > 1) {
            setBadge('badge-sequel', 'done');
        }
        try {
            var bookExists = await fileExists(basePath, 'comicbook.html');
            if (bookExists) {
                setBadge('badge-htmlbook', 'done');
            }
        } catch (e) { /* leave as pending */ }

        // Pull initial docops status & register running tasks
        try {
            var statusData = await fetchDocopsStatus(basePath);
            if (statusData && statusData.tasks) {
                for (var target in statusData.tasks) {
                    if (!statusData.tasks.hasOwnProperty(target)) continue;
                    var taskInfo = statusData.tasks[target];
                    if (taskInfo.status === 'RUNNING') {
                        registerActiveTask(target);
                    }
                    trackSession(target, taskInfo);
                }
            }
        } catch (e) {
            console.warn('Failed to fetch initial docops status:', e);
        }
    }

    // Periodic recovery poll — mirrors goal-planner best practice
    function startRecoveryPoll() {
        setInterval(async function() {
            try {
                var statusData = await fetchDocopsStatus(basePath);
                if (statusData && statusData.tasks) {
                    for (var target in statusData.tasks) {
                        if (!statusData.tasks.hasOwnProperty(target)) continue;
                        trackSession(target, statusData.tasks[target]);
                    }
                }
            } catch (e) { /* ignore */ }
        }, 15000);
    }

    // Global error logging
    window.addEventListener('error', function(ev) {
        console.error('Uncaught error:', ev.error || ev.message);
    });
    window.addEventListener('unhandledrejection', function(ev) {
        console.error('Unhandled rejection:', ev.reason);
    });

    // Bootstrap
    loadInitialFiles();
    checkExistingFiles();
    initModels();
    startRecoveryPoll();

})();