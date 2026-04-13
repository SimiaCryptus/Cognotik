import { parseSessionUrl, getProxyUrl } from './utils/session.js';
import { readFile, writeFile, fileExists, listFiles } from './utils/fileIO.js';
import { runDocOp, waitForTask, fetchDocopsStatus, createStatusPoller } from './utils/docops.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { createSessionLinkManager } from './utils/sessionLinks.js';
import { renderMarkdown, escapeHtml, setStatus, setBadge, showToast, createBatchLogger } from './utils/ui.js';
import { aggregateUsage, renderUsageSummary, createUsageTableHtml, formatTokenCount, formatCost, fetchUsageData } from './utils/usage.js';

(function() {
    'use strict';

    // ========================================
    // Session Setup
    // ========================================
    const { basePath, sessionId, appId } = parseSessionUrl();

    // ========================================
    // Model Management
    // ========================================
    const MODEL_PREFIX = 'comic';
    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const MODEL_SELECT_IDS = {
        smartModel: 'comic-smart-model',
        fastModel: 'comic-fast-model',
        imageModel: 'comic-image-model'
    };

    let availableModels = {};

    async function initModels() {
        try {
            availableModels = await loadApiProviders();
            const selectElements = {};
            for (const key in MODEL_SELECT_IDS) {
                const el = document.getElementById(MODEL_SELECT_IDS[key]);
                if (el) selectElements[key] = el;
            }
            const savedSelections = loadModelSelections(MODEL_PREFIX, MODEL_KEYS);
            populateModelDropdowns(availableModels, selectElements, savedSelections);
            updateModelSummary();
        } catch (e) {
            console.warn('Failed to load API providers:', e);
        }
    }

    function getSelectedModels() {
        const result = {};
        for (const key in MODEL_SELECT_IDS) {
            const el = document.getElementById(MODEL_SELECT_IDS[key]);
            result[key] = el ? el.value : '';
        }
        return result;
    }

    function updateModelSummary() {
        const summaryEl = document.getElementById('model-summary');
        if (!summaryEl) return;
        const models = getSelectedModels();
        const rows = [
            { label: '🧠 Smart Model', value: models.smartModel },
            { label: '⚡ Fast Model', value: models.fastModel },
            { label: '🎨 Image Model', value: models.imageModel }
        ];
        let html = '';
        rows.forEach(function(row) {
            const valueClass = row.value ? 'model-summary-value' : 'model-summary-value not-set';
            const valueText = row.value || 'Not set';
            const indicatorClass = row.value ? 'model-indicator' : 'model-indicator no-model';
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
    const saveModelBtn = document.getElementById('save-model-settings');
    if (saveModelBtn) {
        saveModelBtn.addEventListener('click', function() {
            const models = getSelectedModels();
            saveModelSelections(MODEL_PREFIX, models);
            updateModelSummary();
            setStatus('model-status', '✓ Model settings saved', 'success');
        });
    }

    // Reset model settings
    const resetModelBtn = document.getElementById('reset-model-settings');
    if (resetModelBtn) {
        resetModelBtn.addEventListener('click', function() {
            saveModelSelections(MODEL_PREFIX, { smartModel: '', fastModel: '', imageModel: '' });
            for (const key in MODEL_SELECT_IDS) {
                const el = document.getElementById(MODEL_SELECT_IDS[key]);
                if (el && el.options.length > 0) el.selectedIndex = 0;
            }
            updateModelSummary();
            setStatus('model-status', '✓ Model settings reset to defaults', 'success');
        });
    }

    // Update summary when dropdowns change
    Object.values(MODEL_SELECT_IDS).forEach(function(id) {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('change', function() {
                updateModelSummary();
            });
        }
    });

    // ========================================
    // Session Link Manager
    // ========================================
    const linkManager = createSessionLinkManager(getProxyUrl);

    // ========================================
    // Status Polling
    // ========================================
    let activeTasks = {};

    const statusPoller = createStatusPoller(basePath, function(target, taskInfo) {
        linkManager.update(target, taskInfo);
        if (activeTasks[target] && (taskInfo.status === 'COMPLETED' || taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED')) {
            delete activeTasks[target];
            if (Object.keys(activeTasks).length === 0) {
                statusPoller.stop();
            }
        }
    }, 3000);

    function registerActiveTask(targetPath) {
        activeTasks[targetPath] = true;
        statusPoller.start();
    }

    function unregisterActiveTask(targetPath) {
        delete activeTasks[targetPath];
        if (Object.keys(activeTasks).length === 0) {
            statusPoller.stop();
        }
    }

    // ========================================
    // Comic Display Rendering
    // ========================================
    let comicDisplayCounter = 0;

    function renderHtmlFile(filePath) {
        const url = basePath + '/' + filePath + '?t=' + Date.now();
        const id = 'comic-display-' + (++comicDisplayCounter);
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
    let activeFullscreenDisplay = null;
    let fullscreenBackdrop = null;

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
        const backdrop = getFullscreenBackdrop();
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
        const backdrop = getFullscreenBackdrop();
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
        const label = displayEl.querySelector('[data-size-label]');
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
        const btn = e.target.closest('.comic-display-toolbar button');
        if (!btn) return;
        const displayEl = btn.closest('.comic-display');
        if (!displayEl) return;
        const action = btn.dataset.action;
        if (action === 'fullscreen') {
            toggleFullscreen(displayEl);
        } else if (action === 'size-up') {
            if (!displayEl.classList.contains('fullscreen')) {
                const h = displayEl.offsetHeight;
                displayEl.style.height = Math.min(h + 150, 2000) + 'px';
                updateSizeLabel(displayEl);
            }
        } else if (action === 'size-down') {
            if (!displayEl.classList.contains('fullscreen')) {
                const h = displayEl.offsetHeight;
                displayEl.style.height = Math.max(h - 150, 200) + 'px';
                updateSizeLabel(displayEl);
            }
        }
    });

    const resizeObserver = new ResizeObserver(function(entries) {
        for (let i = 0; i < entries.length; i++) {
            const el = entries[i].target;
            if (el.classList.contains('comic-display')) {
                updateSizeLabel(el);
            }
        }
    });

    const bodyObserver = new MutationObserver(function(mutations) {
        for (let i = 0; i < mutations.length; i++) {
            const added = mutations[i].addedNodes;
            for (let j = 0; j < added.length; j++) {
                if (added[j].nodeType !== 1) continue;
                if (added[j].classList && added[j].classList.contains('comic-display')) {
                    resizeObserver.observe(added[j]);
                    updateSizeLabel(added[j]);
                }
                const nested = added[j].querySelectorAll ? added[j].querySelectorAll('.comic-display') : [];
                for (let k = 0; k < nested.length; k++) {
                    resizeObserver.observe(nested[k]);
                    updateSizeLabel(nested[k]);
                }
            }
        }
    });
    bodyObserver.observe(document.body, { childList: true, subtree: true });

    // ========================================
    // Batch Log
    // ========================================
    const batchLogger = createBatchLogger('batch-log');

    // ========================================
    // Episode Counting
    // ========================================
    async function countEpisodes() {
        let count = 0;
        for (let i = 1; i <= 100; i++) {
            let exists = await fileExists(basePath, 'comic_' + i + '.md');
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
        const count = await countEpisodes();
        const countEl = document.getElementById('episode-count');
        const nextEl = document.getElementById('next-episode-label');
        if (countEl) countEl.textContent = count.toString();
        if (nextEl) nextEl.textContent = '#' + (count + 1);
        return count;
    }

    // ========================================
    // Helper: run a docop with current model selections
    // ========================================
    async function runComicDocOp(opPath, targetPath) {
        const models = getSelectedModels();
        const taskId = await runDocOp(sessionId, opPath, targetPath, models);
        return taskId;
    }

    function isCleanTaskId(taskId) {
        const cleaned = taskId ? taskId.trim() : '';
        return cleaned && cleaned.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleaned);
    }

    // ========================================
    // Navigation
    // ========================================
    document.querySelectorAll('.nav-link').forEach(function(link) {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const sectionId = this.dataset.section;
            document.querySelectorAll('.nav-link').forEach(function(l) {
                l.classList.remove('active');
            });
            document.querySelectorAll('.section').forEach(function(s) {
                s.classList.remove('active');
            });
            this.classList.add('active');
            document.getElementById(sectionId).classList.add('active');

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
    document.getElementById('save-idea').addEventListener('click', async function() {
        const content = document.getElementById('idea-editor').value;
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

    // ========================================
    // View File Buttons
    // ========================================
    async function viewFile(filePath, viewerId) {
        const viewer = document.getElementById(viewerId);
        if (!viewer) return;

        if (viewer.classList.contains('visible') && viewer.innerHTML.trim()) {
            viewer.classList.remove('visible');
            viewer.innerHTML = '';
            return;
        }

        try {
            const htmlPath = filePath.replace(/\.md$/, '.html');
            const isHtml = filePath.endsWith('.html');
            const exists = isHtml
                ? await fileExists(basePath, filePath)
                : await fileExists(basePath, htmlPath);

            if (exists) {
                viewer.innerHTML = renderHtmlFile(isHtml ? filePath : htmlPath);
            } else if (!isHtml) {
                const mdContent = await readFile(basePath, filePath);
                if (mdContent !== null) {
                    viewer.innerHTML = renderMarkdown(mdContent);
                } else {
                    viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
                }
            } else {
                viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
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
            const opPath = this.dataset.op;
            const badgeId = this.dataset.badge;
            const outputPath = this.dataset.output;
            const viewerId = this.dataset.viewer;

            const ideaContent = document.getElementById('idea-editor').value;
            if (!ideaContent.trim()) {
                alert('Please enter and save your idea first!');
                return;
            }
            await writeFile(basePath, 'idea.md', ideaContent);

            setBadge(badgeId, 'running');
            this.disabled = true;
            registerActiveTask(outputPath);

            try {
                const taskId = await runComicDocOp(opPath, outputPath);
                if (isCleanTaskId(taskId)) {
                    linkManager.update(outputPath, { status: 'RUNNING', sessionId: taskId.trim() });
                }

                await waitForTask(basePath, outputPath, 600000, function(target, taskInfo) {
                    linkManager.update(target, taskInfo);
                });
                setBadge(badgeId, 'done');

                if (viewerId) {
                    const viewer = document.getElementById(viewerId);
                    if (viewer) {
                        const htmlPath = outputPath.replace(/\.md$/, '.html');
                        const exists = await fileExists(basePath, htmlPath);
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
        const count = await countEpisodes();

        if (count === 0) {
            alert('No episodes exist yet. Please generate Comic #1 first!');
            return null;
        }

        const nextNum = count + 1;
        const outputPath = 'comic_' + nextNum + '.md';

        setBadge('badge-sequel', 'running');
        registerActiveTask(outputPath);

        try {
            const taskId = await runComicDocOp('ops/sequel_op.md', outputPath);
            if (isCleanTaskId(taskId)) {
                linkManager.update(outputPath, { status: 'RUNNING', sessionId: taskId.trim() });
            }

            await waitForTask(basePath, outputPath, 600000, function(target, taskInfo) {
                linkManager.update(target, taskInfo);
            });
            setBadge('badge-sequel', 'done');

            const viewer = document.getElementById('viewer-sequel');
            if (viewer) {
                const htmlPath = outputPath.replace(/\.md$/, '.html');
                const exists = await fileExists(basePath, htmlPath);
                if (exists) {
                    viewer.innerHTML = '<h4>Comic #' + nextNum + '</h4>' + renderHtmlFile(htmlPath);
                    viewer.classList.add('visible');
                } else {
                    const mdContent = await readFile(basePath, outputPath);
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

    document.getElementById('generate-sequel').addEventListener('click', async function() {
        this.disabled = true;
        try {
            await generateSequel();
        } catch (e) {
            alert('Failed to generate sequel: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('refresh-count').addEventListener('click', async function() {
        this.disabled = true;
        try {
            await updateEpisodeCount();
        } finally {
            this.disabled = false;
        }
    });

    // ========================================
    // Generate HTML Book
    // ========================================
    async function generateHtmlBook() {
        const count = await countEpisodes();
        if (count === 0) {
            alert('No episodes exist yet. Please generate at least one comic first!');
            return false;
        }
        setBadge('badge-htmlbook', 'running');
        registerActiveTask('comicbook.html');
        try {
            const taskId = await runComicDocOp('ops/html_book_op.md', 'comicbook.html');
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (isCleanTaskId(taskId)) {
                linkManager.update('comicbook.html', { status: 'RUNNING', sessionId: cleanTaskId });
            }
            await waitForTask(basePath, 'comicbook.html', 600000, function(target, taskInfo) {
                linkManager.update(target, taskInfo);
            });
            setBadge('badge-htmlbook', 'done');
            unregisterActiveTask('comicbook.html');
            if (cleanTaskId) {
                linkManager.update('comicbook.html', { status: 'COMPLETED', sessionId: cleanTaskId });
            }

            const viewer = document.getElementById('viewer-htmlbook');
            if (viewer) {
                const exists = await fileExists(basePath, 'comicbook.html');
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

    document.getElementById('generate-htmlbook').addEventListener('click', async function() {
        this.disabled = true;
        try {
            await generateHtmlBook();
        } catch (e) {
            alert('Failed to generate HTML book: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('open-htmlbook-tab').addEventListener('click', async function() {
        try {
            const exists = await fileExists(basePath, 'comicbook.html');
            if (exists) {
                window.open(basePath + '/comicbook.html?t=' + Date.now(), '_blank');
            } else {
                alert('HTML book has not been generated yet. Click "Generate HTML Book" first.');
            }
        } catch (e) {
            window.open(basePath + '/comicbook.html?t=' + Date.now(), '_blank');
        }
    });

    // ========================================
    // Batch Generation
    // ========================================
    document.getElementById('run-batch').addEventListener('click', async function() {
        const batchCountInput = document.getElementById('batch-count');
        let totalEpisodes = parseInt(batchCountInput.value, 10);
        if (isNaN(totalEpisodes) || totalEpisodes < 1) {
            alert('Please enter a valid number of episodes (1 or more).');
            return;
        }

        const ideaContent = document.getElementById('idea-editor').value;
        if (!ideaContent.trim()) {
            alert('Please enter and save your idea first!');
            return;
        }
        await writeFile(basePath, 'idea.md', ideaContent);

        this.disabled = true;
        batchLogger.clear();

        try {
            let currentCount = await countEpisodes();
            batchLogger.log('Current episodes: ' + currentCount, 'info');
            batchLogger.log('Target: generate ' + totalEpisodes + ' new episode(s)', 'info');

            // Step 1: Ensure comic_1 exists
            if (currentCount === 0) {
                batchLogger.log('Generating Comic #1 from idea...', 'info');
                setBadge('badge-comic-1', 'running');
                registerActiveTask('comic_1.md');

                const taskId = await runComicDocOp('ops/comic_op.md', 'comic_1.md');
                if (isCleanTaskId(taskId)) {
                    const cleanId = taskId.trim();
                    batchLogger.logHtml('Session: <a href="' + getProxyUrl(cleanId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanId + ')</a>', 'info');
                    linkManager.update('comic_1.md', { status: 'RUNNING', sessionId: cleanId });
                }

                await waitForTask(basePath, 'comic_1.md', 600000, function(target, taskInfo) {
                    linkManager.update(target, taskInfo);
                });
                setBadge('badge-comic-1', 'done');
                unregisterActiveTask('comic_1.md');
                batchLogger.log('✓ Comic #1 generated', 'success');
                currentCount = 1;
                totalEpisodes--;

                await updateEpisodeCount();
            }

            // Step 2: Generate sequels
            for (let i = 0; i < totalEpisodes; i++) {
                const nextNum = currentCount + 1 + i;
                const outputPath = 'comic_' + nextNum + '.md';

                batchLogger.log('Generating Comic #' + nextNum + '...', 'info');
                setBadge('badge-sequel', 'running');
                registerActiveTask(outputPath);

                const seqTaskId = await runComicDocOp('ops/sequel_op.md', outputPath);
                if (isCleanTaskId(seqTaskId)) {
                    const cleanSeqId = seqTaskId.trim();
                    batchLogger.logHtml('Session: <a href="' + getProxyUrl(cleanSeqId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanSeqId + ')</a>', 'info');
                    linkManager.update(outputPath, { status: 'RUNNING', sessionId: cleanSeqId });
                }

                await waitForTask(basePath, outputPath, 600000, function(target, taskInfo) {
                    linkManager.update(target, taskInfo);
                });
                setBadge('badge-sequel', 'done');
                unregisterActiveTask(outputPath);
                batchLogger.log('✓ Comic #' + nextNum + ' generated', 'success');

                await updateEpisodeCount();
            }

            batchLogger.log('🎉 Series generation complete!', 'success');

            // Compile the book
            batchLogger.log('Compiling HTML comicbook...', 'info');
            try {
                setBadge('badge-htmlbook', 'running');
                registerActiveTask('comicbook.html');
                const bookTaskId = await runComicDocOp('ops/html_book_op.md', 'comicbook.html');
                if (isCleanTaskId(bookTaskId)) {
                    const cleanBookId = bookTaskId.trim();
                    batchLogger.logHtml('Session: <a href="' + getProxyUrl(cleanBookId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanBookId + ')</a>', 'info');
                    linkManager.update('comicbook.html', { status: 'RUNNING', sessionId: cleanBookId });
                }
                await waitForTask(basePath, 'comicbook.html', 600000, function(target, taskInfo) {
                    linkManager.update(target, taskInfo);
                });
                setBadge('badge-htmlbook', 'done');
                unregisterActiveTask('comicbook.html');
                batchLogger.log('✓ HTML comicbook compiled', 'success');
            } catch (bookErr) {
                setBadge('badge-htmlbook', 'error');
                unregisterActiveTask('comicbook.html');
                batchLogger.log('⚠ Could not compile HTML book: ' + bookErr.message, 'warn');
            }
        } catch (e) {
            batchLogger.log('✗ Error: ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // ========================================
    // Series Display
    // ========================================
    async function loadSeries() {
        const container = document.getElementById('series-container');
        const count = await countEpisodes();

        if (count === 0) {
            container.innerHTML =
                '<div class="card">' +
                '<p class="placeholder">No episodes yet. Go to the Pipeline tab to start generating your comic series!</p>' +
                '</div>';
            return;
        }

        let html = '';
        for (let i = 1; i <= count; i++) {
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
                const epNum = this.dataset.episode;
                toggleEpisode(parseInt(epNum, 10));
            });
        });

        // Auto-expand the latest episode
        toggleEpisode(count);
    }

    async function toggleEpisode(num) {
        const contentEl = document.getElementById('episode-content-' + num);
        const toggleEl = document.getElementById('toggle-ep-' + num);
        if (!contentEl) return;

        if (contentEl.classList.contains('visible')) {
            contentEl.classList.remove('visible');
            if (toggleEl) toggleEl.classList.remove('open');
            return;
        }

        if (!contentEl.innerHTML.trim()) {
            try {
                const htmlPath = 'comic_' + num + '.html';
                const exists = await fileExists(basePath, htmlPath);
                if (exists) {
                    contentEl.innerHTML = renderHtmlFile(htmlPath);
                } else {
                    const mdContent = await readFile(basePath, 'comic_' + num + '.md');
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

    document.getElementById('refresh-series').addEventListener('click', function() {
        loadSeries();
    });

    document.getElementById('series-add-episode').addEventListener('click', async function() {
        this.disabled = true;
        try {
            const result = await generateSequel();
            if (result) {
                await loadSeries();
            }
        } catch (e) {
            alert('Failed to generate episode: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });

    document.getElementById('series-compile-book').addEventListener('click', async function() {
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

    document.getElementById('series-view-book').addEventListener('click', async function() {
        try {
            const exists = await fileExists(basePath, 'comicbook.html');
            if (exists) {
                window.open(basePath + '/comicbook.html?t=' + Date.now(), '_blank');
            } else {
                alert('HTML book has not been generated yet. Click "Compile Book" first.');
            }
        } catch (e) {
            window.open(basePath + '/comicbook.html?t=' + Date.now(), '_blank');
        }
    });

    // ========================================
    // Usage Tracking
    // ========================================
    let usageJsonMode = false;
    let lastUsageData = null;

    function renderUsageTableLocal(models) {
        const container = document.getElementById('usage-table-container');
        if (!container) return;
        if (!models || models.length === 0) {
            container.innerHTML = '<p class="placeholder">No model usage data available.</p>';
            return;
        }
        container.innerHTML = createUsageTableHtml(models);
    }

    function renderUsageJson(data) {
        const jsonEl = document.getElementById('usage-json-output');
        if (!jsonEl) return;
        if (data) {
            jsonEl.textContent = JSON.stringify(data, null, 2);
        } else {
            jsonEl.textContent = '// No usage data available';
        }
    }

    function renderTaskSessions(taskUsageMap) {
        const container = document.getElementById('task-sessions-container');
        if (!container) return;
        const allSessions = linkManager.getAllSessions();
        const entries = Object.keys(allSessions);
        if (entries.length === 0) {
            container.innerHTML = '<p class="placeholder">No task sessions recorded yet.</p>';
            return;
        }
        let html = '<div class="task-sessions-list">';
        entries.forEach(function(target) {
            const sid = allSessions[target];
            const usage = taskUsageMap[sid];
            const proxyUrl = getProxyUrl(sid);
            const usageUrl = '/proxy/usage?sessionId=' + encodeURIComponent(sid);
            html += '<div class="task-session-row">';
            html += '<div class="task-session-info">';
            html += '<span class="task-session-target">' + escapeHtml(target) + '</span>';
            html += '<span class="task-session-id">' + escapeHtml(sid) + '</span>';
            html += '</div>';
            html += '<div class="task-session-usage">';
            if (usage && usage.totals) {
                const t = usage.totals;
                const prompt = t.prompt_tokens || 0;
                const completion = t.completion_tokens || 0;
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

        // Collect unique session IDs from the link manager
        const allSessions = linkManager.getAllSessions();
        const seenSessions = new Set();
        const sessionIds = [];

        for (const target in allSessions) {
            if (!allSessions.hasOwnProperty(target)) continue;
            const sid = allSessions[target];
            if (!seenSessions.has(sid)) {
                seenSessions.add(sid);
                sessionIds.push(sid);
            }
        }

        // Fall back to parent session if no child task sessions
        if (sessionIds.length === 0 && sessionId) {
            seenSessions.add(sessionId);
            sessionIds.push(sessionId);
        }

        if (sessionIds.length === 0) {
            renderUsageSummary(null, {
                prompt: document.getElementById('usage-total-prompt'),
                completion: document.getElementById('usage-total-completion'),
                total: document.getElementById('usage-total-tokens'),
                cost: document.getElementById('usage-total-cost')
            });
            renderUsageTableLocal([]);
            renderUsageJson(null);
            renderTaskSessions({});
            setStatus('usage-status', 'No sessions to query', '');
            return;
        }

        try {
            const aggregated = await aggregateUsage(sessionIds);
            lastUsageData = aggregated;

            renderUsageSummary(aggregated.totals, {
                prompt: document.getElementById('usage-total-prompt'),
                completion: document.getElementById('usage-total-completion'),
                total: document.getElementById('usage-total-tokens'),
                cost: document.getElementById('usage-total-cost')
            });
            renderUsageTableLocal(aggregated.models);
            renderUsageJson(aggregated);
            renderTaskSessions(aggregated.sessionUsageMap || {});

            const sessionCount = sessionIds.length;
            const modelCount = aggregated.models ? aggregated.models.length : 0;
            setStatus('usage-status', '✓ Loaded from ' + sessionCount + ' session(s), ' + modelCount + ' model(s)', 'success');
        } catch (e) {
            setStatus('usage-status', '✗ Failed to load usage: ' + e.message, 'error');
        }
    }

    const refreshUsageBtn = document.getElementById('refresh-usage');
    if (refreshUsageBtn) {
        refreshUsageBtn.addEventListener('click', function() {
            this.disabled = true;
            const self = this;
            refreshUsage().finally(function() {
                self.disabled = false;
            });
        });
    }

    const toggleJsonBtn = document.getElementById('toggle-usage-format');
    if (toggleJsonBtn) {
        toggleJsonBtn.addEventListener('click', function() {
            usageJsonMode = !usageJsonMode;
            const tableCard = document.getElementById('usage-table-card');
            const jsonCard = document.getElementById('usage-json-card');
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
            const content = await readFile(basePath, 'idea.md');
            if (content !== null) {
                document.getElementById('idea-editor').value = content;
            }
        } catch (e) {
            console.warn('Could not load idea.md:', e);
        }
    }

    async function checkExistingFiles() {
        try {
            const comic1Exists = await fileExists(basePath, 'comic_1.md');
            if (comic1Exists) {
                setBadge('badge-comic-1', 'done');
            }
        } catch (e) { /* leave as pending */ }

        const episodeCount = await updateEpisodeCount();
        if (episodeCount > 1) {
            setBadge('badge-sequel', 'done');
        }

        try {
            const bookExists = await fileExists(basePath, 'comicbook.html');
            if (bookExists) {
                setBadge('badge-htmlbook', 'done');
            }
        } catch (e) { /* leave as pending */ }

        const statusData = await fetchDocopsStatus(basePath);
        if (statusData && statusData.tasks) {
            for (const target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                const taskInfo = statusData.tasks[target];
                if (taskInfo.status === 'RUNNING') {
                    registerActiveTask(target);
                }
                linkManager.update(target, taskInfo);
            }
        }
    }

    // Run initialization
    loadInitialFiles();
    checkExistingFiles();
    initModels();

})();