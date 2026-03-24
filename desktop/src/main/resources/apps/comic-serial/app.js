(function() {
    'use strict';

    // ========================================
    // URL Parsing & Session Setup
    // ========================================
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';
    let appId = '';

    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
        appId = pathParts[fileIndexIdx - 1] || 'comic-serial';
    } else {
        console.warn('Could not determine session from URL path.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }

    const proxyBase = '/proxy/';

    function getProxyUrl(id) {
        return proxyBase + '#' + id;
    }
     // ========================================
     // Model Management
     // ========================================
     var availableModels = {};
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
             populateModelDropdowns();
         } catch (e) {
             console.warn('Failed to load API providers:', e);
         }
     }
     function populateModelDropdowns() {
         var smartSelect = document.getElementById('comic-smart-model');
         var fastSelect = document.getElementById('comic-fast-model');
         var imageSelect = document.getElementById('comic-image-model');
         if (!smartSelect || !fastSelect || !imageSelect) return;
         [smartSelect, fastSelect, imageSelect].forEach(function(sel) {
             sel.innerHTML = '';
         });
         var addedModels = new Set();
         var hasModels = false;
         for (var provider in availableModels) {
             if (!availableModels.hasOwnProperty(provider)) continue;
             availableModels[provider].forEach(function(model) {
                 if (!addedModels.has(model.id)) {
                     [smartSelect, fastSelect, imageSelect].forEach(function(sel) {
                         var option = document.createElement('option');
                         option.value = model.id;
                         option.textContent = model.name + ' (' + provider + ')';
                         if (model.description) {
                             option.title = model.description;
                         }
                         sel.appendChild(option);
                     });
                     addedModels.add(model.id);
                     hasModels = true;
                 }
             });
         }
         if (!hasModels) {
             [smartSelect, fastSelect, imageSelect].forEach(function(sel) {
                 var option = document.createElement('option');
                 option.value = '';
                 option.textContent = 'No models available — check API provider settings';
                 sel.appendChild(option);
             });
         }
         // Restore saved selections
         var savedSmart = localStorage.getItem('comicSmartModel');
         var savedFast = localStorage.getItem('comicFastModel');
         var savedImage = localStorage.getItem('comicImageModel');
         if (savedSmart && Array.from(smartSelect.options).some(function(o) { return o.value === savedSmart; })) {
             smartSelect.value = savedSmart;
         }
         if (savedFast && Array.from(fastSelect.options).some(function(o) { return o.value === savedFast; })) {
             fastSelect.value = savedFast;
         }
         if (savedImage && Array.from(imageSelect.options).some(function(o) { return o.value === savedImage; })) {
             imageSelect.value = savedImage;
         }
         updateModelSummary();
     }
     function getSelectedModels() {
         var smartSelect = document.getElementById('comic-smart-model');
         var fastSelect = document.getElementById('comic-fast-model');
         var imageSelect = document.getElementById('comic-image-model');
         return {
             smartModel: smartSelect ? smartSelect.value : '',
             fastModel: fastSelect ? fastSelect.value : '',
             imageModel: imageSelect ? imageSelect.value : ''
         };
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
             if (models.smartModel) localStorage.setItem('comicSmartModel', models.smartModel);
             else localStorage.removeItem('comicSmartModel');
             if (models.fastModel) localStorage.setItem('comicFastModel', models.fastModel);
             else localStorage.removeItem('comicFastModel');
             if (models.imageModel) localStorage.setItem('comicImageModel', models.imageModel);
             else localStorage.removeItem('comicImageModel');
             updateModelSummary();
             setStatus('model-status', '✓ Model settings saved', 'success');
         });
     }
     // Reset model settings
     var resetModelBtn = document.getElementById('reset-model-settings');
     if (resetModelBtn) {
         resetModelBtn.addEventListener('click', function() {
             localStorage.removeItem('comicSmartModel');
             localStorage.removeItem('comicFastModel');
             localStorage.removeItem('comicImageModel');
             var smartSelect = document.getElementById('comic-smart-model');
             var fastSelect = document.getElementById('comic-fast-model');
             var imageSelect = document.getElementById('comic-image-model');
             if (smartSelect && smartSelect.options.length > 0) smartSelect.selectedIndex = 0;
             if (fastSelect && fastSelect.options.length > 0) fastSelect.selectedIndex = 0;
             if (imageSelect && imageSelect.options.length > 0) imageSelect.selectedIndex = 0;
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
    // File I/O
    // ========================================
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

    async function fileExists(filePath) {
        const resp = await fetch(basePath + '/' + filePath, { method: 'HEAD' });
        return resp.ok;
    }

    async function listFiles(dirPath) {
        const url = basePath + '/' + dirPath + '/_files.json';
        const resp = await fetch(url);
        if (!resp.ok) {
            if (resp.status === 404) return [];
            throw new Error('Failed to list ' + dirPath + ': ' + resp.status);
        }
        const data = await resp.json();
        return (data.entries || []).filter(function(e) {
            return e.type === 'file';
        });
    }

    async function runDocOp(opPath, targetPath) {
         var params = new URLSearchParams({
             sessionId: sessionId,
             doc: opPath,
             target: targetPath
         });

         // Add model overrides
         var models = getSelectedModels();
         if (models.smartModel) params.set('smartModel', models.smartModel);
         if (models.fastModel) params.set('fastModel', models.fastModel);
         if (models.imageModel) params.set('imageModel', models.imageModel);

         var url = '/docops?' + params.toString();
        var resp = await fetch(url, { method: 'POST' });
        if (!resp.ok) {
            var errText = await resp.text().catch(function() {
                return '';
            });
            throw new Error('DocOps failed: ' + resp.status + '\n' + errText);
        }
        return await resp.text();
    }

    // ========================================
    // Status Polling
    // ========================================
    async function fetchDocopsStatus() {
        try {
            var resp = await fetch(basePath + '/docops.status.json');
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) {
            return null;
        }
    }

    async function waitForTask(targetPath, maxWaitMs) {
        var maxWait = maxWaitMs || 600000;
        var pollInterval = 2000;
        var startTime = Date.now();

        while (Date.now() - startTime < maxWait) {
            var outExists = await fileExists(targetPath);
            if (outExists) {
                return { status: 'COMPLETED', detectedByFile: true };
            }
            var htmlVariant = targetPath.replace(/\.md$/, '.html');
            if (htmlVariant !== targetPath) {
                var htmlExists = await fileExists(htmlVariant);
                if (htmlExists) {
                    return { status: 'COMPLETED', detectedByFile: true };
                }
            }

            await new Promise(function(r) {
                setTimeout(r, pollInterval);
            });
        }
        throw new Error('Task ' + targetPath + ' timed out');
    }

    var statusPollTimer = null;
    var STATUS_POLL_INTERVAL = 3000;

    function startStatusPolling() {
        if (statusPollTimer) return;
        statusPollTimer = setInterval(function() {
            pollStatus();
        }, STATUS_POLL_INTERVAL);
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

        // Status file is only used for session detail links, not for state

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];


                // Update session links
                updateSessionLinks(target, taskInfo);
            }
        }
    }



    // ========================================
    // UI Helpers
    // ========================================
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

    var comicDisplayCounter = 0;

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
        // Try native Fullscreen API first
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

    // Listen for native fullscreen exit (e.g. pressing Escape)
    document.addEventListener('fullscreenchange', function() {
        if (!document.fullscreenElement && activeFullscreenDisplay) {
            activeFullscreenDisplay.classList.remove('fullscreen');
            updateSizeLabel(activeFullscreenDisplay);
            activeFullscreenDisplay = null;
        }
    });

    // Escape key handler for fallback fullscreen
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && activeFullscreenDisplay && !document.fullscreenElement) {
            exitFullscreen(activeFullscreenDisplay);
        }
    });

    // Delegate click events for comic display toolbar buttons
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

    // Update size labels on resize (handles manual CSS resize drag)
    var resizeObserver = new ResizeObserver(function(entries) {
        for (var i = 0; i < entries.length; i++) {
            var el = entries[i].target;
            if (el.classList.contains('comic-display')) {
                updateSizeLabel(el);
            }
        }
    });

    // Observe new comic displays as they appear
    var bodyObserver = new MutationObserver(function(mutations) {
        for (var i = 0; i < mutations.length; i++) {
            var added = mutations[i].addedNodes;
            for (var j = 0; j < added.length; j++) {
                if (added[j].nodeType !== 1) continue;
                if (added[j].classList && added[j].classList.contains('comic-display')) {
                    resizeObserver.observe(added[j]);
                    updateSizeLabel(added[j]);
                }
                // Also check children
                var nested = added[j].querySelectorAll ? added[j].querySelectorAll('.comic-display') : [];
                for (var k = 0; k < nested.length; k++) {
                    resizeObserver.observe(nested[k]);
                    updateSizeLabel(nested[k]);
                }
            }
        }
    });
    bodyObserver.observe(document.body, { childList: true, subtree: true });


    function setStatus(elemId, message, type) {
        var el = document.getElementById(elemId);
        if (!el) return;
        el.textContent = message;
        el.className = 'status-msg' + (type ? ' ' + type : '');
        if (type === 'success' || type === 'error') {
            setTimeout(function() {
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
    // Session Monitor Links
    // ========================================
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
            // Try to insert near a relevant viewer
            var viewerId = 'viewer-comic-' + (target.match(/comic_(\d+)/) || [0, ''])[1];
            var viewer = document.getElementById(viewerId) || document.getElementById('viewer-sequel');
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

    // ========================================
    // Batch Log
    // ========================================
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

    // ========================================
    // Episode Counting
    // ========================================
    async function countEpisodes() {
        var count = 0;
        // Check comic_1.md (or comic_1.html), comic_2.md, ... up to a reasonable limit
        // Canonical state: the file exists on disk
        for (var i = 1; i <= 100; i++) {
            var exists = await fileExists('comic_' + i + '.md');
            if (!exists) {
                // Also check for .html variant in case .md was cleaned up
                exists = await fileExists('comic_' + i + '.html');
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
            document.getElementById(sectionId).classList.add('active');

            // Refresh series when switching to that tab
            if (sectionId === 'section-series') {
                loadSeries();
            }
            // Refresh count when switching to pipeline
            if (sectionId === 'section-pipeline') {
                updateEpisodeCount();
            }
        });
    });

    // ========================================
    // Save Idea
    // ========================================
    document.getElementById('save-idea').addEventListener('click', async function() {
        var content = document.getElementById('idea-editor').value;
        if (!content.trim()) {
            setStatus('idea-status', '✗ Please enter an idea first', 'error');
            return;
        }
        try {
            this.disabled = true;
            await writeFile('idea.md', content);
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
        var viewer = document.getElementById(viewerId);
        if (!viewer) return;

        if (viewer.classList.contains('visible')) {
            viewer.classList.remove('visible');
            return;
        }

        try {
            var htmlPath = filePath.replace(/\.md$/, '.html');
            var exists = await fileExists(htmlPath);
            if (exists) {
                viewer.innerHTML = renderHtmlFile(htmlPath);
            } else {
                // Fallback: try the .md file directly
                var mdContent = await readFile(filePath);
                if (mdContent !== null) {
                    viewer.innerHTML = renderMarkdown(mdContent);
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

            // Auto-save idea first
            var ideaContent = document.getElementById('idea-editor').value;
            if (!ideaContent.trim()) {
                alert('Please enter and save your idea first!');
                return;
            }
            await writeFile('idea.md', ideaContent);

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
                if (viewerId) {
                    var viewer = document.getElementById(viewerId);
                    if (viewer) {
                        var htmlPath = outputPath.replace(/\.md$/, '.html');
                        var exists = await fileExists(htmlPath);
                        if (exists) {
                            viewer.innerHTML = renderHtmlFile(htmlPath);
                            viewer.classList.add('visible');
                        }
                    }
                }

                await updateEpisodeCount();
            } catch (e) {
                setBadge(badgeId, 'error');
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
            // No comics yet — need to generate the first one
            alert('No episodes exist yet. Please generate Comic #1 first!');
            return null;
        }

        var nextNum = count + 1;
        var outputPath = 'comic_' + nextNum + '.md';

        setBadge('badge-sequel', 'running');
        startStatusPolling();

        try {
            var taskId = await runDocOp('ops/sequel_op.md', outputPath);
            var cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
            }

            await waitForTask(outputPath);
            setBadge('badge-sequel', 'done');

            // Show in viewer
            var viewer = document.getElementById('viewer-sequel');
            if (viewer) {
                var htmlPath = outputPath.replace(/\.md$/, '.html');
                var exists = await fileExists(htmlPath);
                if (exists) {
                    viewer.innerHTML = '<h4>Comic #' + nextNum + '</h4>' + renderHtmlFile(htmlPath);
                    viewer.classList.add('visible');
                } else {
                    // Fallback: try the .md file
                    var mdContent = await readFile(outputPath);
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
    // Batch Generation
    // ========================================
    document.getElementById('run-batch').addEventListener('click', async function() {
        var batchCountInput = document.getElementById('batch-count');
        var totalEpisodes = parseInt(batchCountInput.value, 10);
        if (isNaN(totalEpisodes) || totalEpisodes < 1) {
            alert('Please enter a valid number of episodes (1 or more).');
            return;
        }

        // Auto-save idea
        var ideaContent = document.getElementById('idea-editor').value;
        if (!ideaContent.trim()) {
            alert('Please enter and save your idea first!');
            return;
        }
        await writeFile('idea.md', ideaContent);

        this.disabled = true;
        batchLog.innerHTML = '';
        startStatusPolling();

        try {
            var currentCount = await countEpisodes();
            logBatch('Current episodes: ' + currentCount, 'info');
            logBatch('Target: generate ' + totalEpisodes + ' new episode(s)', 'info');

            // Step 1: Ensure comic_1 exists
            if (currentCount === 0) {
                logBatch('Generating Comic #1 from idea...', 'info');
                setBadge('badge-comic-1', 'running');

                var taskId = await runDocOp('ops/comic_op.md', 'comic_1.md');
                var cleanTaskId = taskId ? taskId.trim() : '';
                if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    logBatchHtml('Session: <a href="' + getProxyUrl(cleanTaskId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId + ')</a>', 'info');
                    updateSessionLinks('comic_1.md', { status: 'RUNNING', sessionId: cleanTaskId });
                }

                await waitForTask('comic_1.md');
                setBadge('badge-comic-1', 'done');
                logBatch('✓ Comic #1 generated', 'success');
                currentCount = 1;
                totalEpisodes--; // One less sequel needed

                await updateEpisodeCount();
            }

            // Step 2: Generate sequels
            for (var i = 0; i < totalEpisodes; i++) {
                var nextNum = currentCount + 1 + i;
                var outputPath = 'comic_' + nextNum + '.md';

                logBatch('Generating Comic #' + nextNum + '...', 'info');
                setBadge('badge-sequel', 'running');

                var seqTaskId = await runDocOp('ops/sequel_op.md', outputPath);
                var cleanSeqId = seqTaskId ? seqTaskId.trim() : '';
                if (cleanSeqId && /^[a-zA-Z0-9-]+$/.test(cleanSeqId)) {
                    logBatchHtml('Session: <a href="' + getProxyUrl(cleanSeqId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanSeqId + ')</a>', 'info');
                    updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanSeqId });
                }

                await waitForTask(outputPath);
                setBadge('badge-sequel', 'done');
                logBatch('✓ Comic #' + nextNum + ' generated', 'success');

                await updateEpisodeCount();
            }

            logBatch('🎉 Series generation complete!', 'success');
        } catch (e) {
            logBatch('✗ Error: ' + e.message, 'error');
        } finally {
            this.disabled = false;
        }
    });

    // ========================================
    // Series Display
    // ========================================
    async function loadSeries() {
        var container = document.getElementById('series-container');
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

        // Attach toggle handlers
        container.querySelectorAll('.episode-header').forEach(function(header) {
            header.addEventListener('click', function() {
                var epNum = this.dataset.episode;
                toggleEpisode(parseInt(epNum, 10));
            });
        });

        // Auto-expand the latest episode
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

        // Load content if empty
        if (!contentEl.innerHTML.trim()) {
            try {
                var htmlPath = 'comic_' + num + '.html';
                var exists = await fileExists(htmlPath);
                if (exists) {
                    contentEl.innerHTML = renderHtmlFile(htmlPath);
                } else {
                    // Fallback: try the .md file
                    var mdContent = await readFile('comic_' + num + '.md');
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

    // ========================================
    // Initialization
    // ========================================
    async function loadInitialFiles() {
        try {
            var content = await readFile('idea.md');
            if (content !== null) {
                document.getElementById('idea-editor').value = content;
            }
        } catch (e) {
            console.warn('Could not load idea.md:', e);
        }
    }

    async function checkExistingFiles() {
        // Check for existing comic files and update badges accordingly

        try {
            var comic1Exists = await fileExists('comic_1.md');
            if (comic1Exists) {
                setBadge('badge-comic-1', 'done');
            }
        } catch (e) { /* leave as pending */ }

        var episodeCount = await updateEpisodeCount();
        if (episodeCount > 1) {
            setBadge('badge-sequel', 'done');
        }

        var statusData = await fetchDocopsStatus();
        var anyRunning = false;

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                if (taskInfo.status === 'RUNNING') anyRunning = true;
                // Status file is only used for session detail links
                updateSessionLinks(target, taskInfo);
            }
        }



        if (anyRunning) startStatusPolling();
    }

    // Run initialization
    loadInitialFiles();
    checkExistingFiles();
    startStatusPolling();
     loadApiProviders();

})();