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
        return proxyBase + '?session=' + id;
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
     var responseText = await resp.text();
     // The response may be JSON with session info, or a plain session ID
     try {
         var jsonResp = JSON.parse(responseText);
         // Extract session ID from JSON response if available
         if (jsonResp.sessionId) return jsonResp.sessionId;
         if (jsonResp.sessions && typeof jsonResp.sessions === 'object') {
             // If sessions is an array or has entries, try to get the first one
             var sessionKeys = Object.keys(jsonResp.sessions);
             if (sessionKeys.length > 0) return sessionKeys[0];
         }
         // If JSON has a taskId field
         if (jsonResp.taskId) return jsonResp.taskId;
         // Could not find a session ID in the JSON
         return '';
     } catch (e) {
         // Not JSON — treat as plain text (possibly a session ID)
         return responseText;
     }
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
            // Check status endpoint first
            var statusData = await fetchDocopsStatus();
            if (statusData && statusData.tasks && statusData.tasks[targetPath]) {
                var taskInfo = statusData.tasks[targetPath];
                updateSessionLinks(targetPath, taskInfo);
                if (taskInfo.status === 'COMPLETED') {
                    return { status: 'COMPLETED', detectedByStatus: true };
                }
                if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                    throw new Error('Task ' + targetPath + ' failed');
                }
            }
            // Also check file existence as fallback
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
    var activeTasks = {};

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
    function registerActiveTask(targetPath) {
        activeTasks[targetPath] = true;
        startStatusPolling();
    }
    function unregisterActiveTask(targetPath) {
        delete activeTasks[targetPath];
        // Stop polling if no active tasks remain
        if (Object.keys(activeTasks).length === 0) {
            stopStatusPolling();
        }
    }


    async function pollStatus() {
        var statusData = await fetchDocopsStatus();


        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];


                updateSessionLinks(target, taskInfo);
                // Auto-unregister completed/failed tasks
                if (activeTasks[target] && (taskInfo.status === 'COMPLETED' || taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED')) {
                    unregisterActiveTask(target);
                }
            }
        }
        // If no active tasks remain, stop polling
        if (Object.keys(activeTasks).length === 0) {
            stopStatusPolling();
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
    var taskSessionMap = {};
    try {
        var savedTaskSessions = sessionStorage.getItem('comicTaskSessions');
        if (savedTaskSessions) {
            taskSessionMap = JSON.parse(savedTaskSessions);
        }
    } catch (e) { /* ignore */ }
    var taskSessionMap = {}; // target -> sessionId (for usage tracking)
    // Load persisted task sessions
    try {
        var savedTaskSessions = sessionStorage.getItem('comicTaskSessions');
        if (savedTaskSessions) {
            taskSessionMap = JSON.parse(savedTaskSessions);
        }
    } catch (e) { /* ignore */ }


    function updateSessionLinks(target, taskInfo) {
        var status = taskInfo.status;
        var taskSessionId = taskInfo.sessionId;
        // Record session for usage tracking
        if (taskSessionId) {
            taskSessionMap[target] = taskSessionId;
            try {
                sessionStorage.setItem('comicTaskSessions', JSON.stringify(taskSessionMap));
            } catch (e) { /* ignore */ }
        }
        // Record session for usage tracking
        if (taskSessionId) {
            try {
                sessionStorage.setItem('comicTaskSessions', JSON.stringify(taskSessionMap));
            } catch (e) { /* ignore */ }
        }


        var safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
        var linkContainerId = 'session-link-' + safeTarget;
        var container = document.getElementById(linkContainerId);

        if (!container) {
            container = document.createElement('div');
            container.id = linkContainerId;
            container.className = 'session-link-container';
            // Try to insert near a relevant viewer based on the target
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
            var proxyUrl3 = taskSessionId ? getProxyUrl(taskSessionId) : '?session=';
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
            // Refresh usage when switching to usage tab
            if (sectionId === 'section-usage') {
                refreshUsage();
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

        if (viewer.classList.contains('visible') && viewer.innerHTML.trim()) {
            viewer.classList.remove('visible');
            viewer.innerHTML = '';
            return;
        }

        try {
            var htmlPath = filePath.replace(/\.md$/, '.html');
            var isHtml = filePath.endsWith('.html');
            var exists = isHtml ? await fileExists(filePath) : await fileExists(htmlPath);
            if (exists) {
                viewer.innerHTML = renderHtmlFile(isHtml ? filePath : htmlPath);
            } else {
                if (!isHtml) {
                    // Fallback: try the .md file directly
                    var mdContent = await readFile(filePath);
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

            // Auto-save idea first
            var ideaContent = document.getElementById('idea-editor').value;
            if (!ideaContent.trim()) {
                alert('Please enter and save your idea first!');
                return;
            }
            await writeFile('idea.md', ideaContent);

            setBadge(badgeId, 'running');
            this.disabled = true;
            registerActiveTask(outputPath);

            try {
                var taskId = await runDocOp(opPath, outputPath);
                var cleanTaskId = taskId ? taskId.trim() : '';
             if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
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
            // No comics yet — need to generate the first one
            alert('No episodes exist yet. Please generate Comic #1 first!');
            return null;
        }

        var nextNum = count + 1;
        var outputPath = 'comic_' + nextNum + '.md';

        setBadge('badge-sequel', 'running');
        registerActiveTask(outputPath);

        try {
            var taskId = await runDocOp('ops/sequel_op.md', outputPath);
            var cleanTaskId = taskId ? taskId.trim() : '';
         if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
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
        var count = await countEpisodes();
        if (count === 0) {
            alert('No episodes exist yet. Please generate at least one comic first!');
            return false;
        }
        setBadge('badge-htmlbook', 'running');
        registerActiveTask('comicbook.html');
        try {
            var taskId = await runDocOp('ops/html_book_op.md', 'comicbook.html');
            var cleanTaskId = taskId ? taskId.trim() : '';
         if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                updateSessionLinks('comicbook.html', { status: 'RUNNING', sessionId: cleanTaskId });
            }
            await waitForTask('comicbook.html', 600000);
            setBadge('badge-htmlbook', 'done');
            unregisterActiveTask('comicbook.html');
            if (cleanTaskId) {
                updateSessionLinks('comicbook.html', { status: 'COMPLETED', sessionId: cleanTaskId });
            }
            // Auto-show result
            var viewer = document.getElementById('viewer-htmlbook');
            if (viewer) {
                var exists = await fileExists('comicbook.html');
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
            var exists = await fileExists('comicbook.html');
            if (exists) {
                var url = basePath + '/comicbook.html?t=' + Date.now();
                window.open(url, '_blank');
            } else {
                alert('HTML book has not been generated yet. Click "Generate HTML Book" first.');
            }
        } catch (e) {
            // Fallback: just try to open it directly
            var url = basePath + '/comicbook.html?t=' + Date.now();
            window.open(url, '_blank');
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

        try {
            var currentCount = await countEpisodes();
            logBatch('Current episodes: ' + currentCount, 'info');
            logBatch('Target: generate ' + totalEpisodes + ' new episode(s)', 'info');

            // Step 1: Ensure comic_1 exists
            if (currentCount === 0) {
                logBatch('Generating Comic #1 from idea...', 'info');
                setBadge('badge-comic-1', 'running');
                registerActiveTask('comic_1.md');

                var taskId = await runDocOp('ops/comic_op.md', 'comic_1.md');
                var cleanTaskId = taskId ? taskId.trim() : '';
             if (cleanTaskId && cleanTaskId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                    logBatchHtml('Session: <a href="' + getProxyUrl(cleanTaskId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanTaskId + ')</a>', 'info');
                    updateSessionLinks('comic_1.md', { status: 'RUNNING', sessionId: cleanTaskId });
                }

                await waitForTask('comic_1.md');
                setBadge('badge-comic-1', 'done');
                unregisterActiveTask('comic_1.md');
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
                registerActiveTask(outputPath);

                var seqTaskId = await runDocOp('ops/sequel_op.md', outputPath);
                var cleanSeqId = seqTaskId ? seqTaskId.trim() : '';
             if (cleanSeqId && cleanSeqId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanSeqId)) {
                    logBatchHtml('Session: <a href="' + getProxyUrl(cleanSeqId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanSeqId + ')</a>', 'info');
                    updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanSeqId });
                }

                await waitForTask(outputPath);
                setBadge('badge-sequel', 'done');
                unregisterActiveTask(outputPath);
                logBatch('✓ Comic #' + nextNum + ' generated', 'success');

                await updateEpisodeCount();
            }

            logBatch('🎉 Series generation complete!', 'success');
            // Offer to compile the book
            logBatch('Compiling HTML comicbook...', 'info');
            try {
                setBadge('badge-htmlbook', 'running');
                registerActiveTask('comicbook.html');
                var bookTaskId = await runDocOp('ops/html_book_op.md', 'comicbook.html');
                var cleanBookId = bookTaskId ? bookTaskId.trim() : '';
             if (cleanBookId && cleanBookId.length < 200 && /^[a-zA-Z0-9-]+$/.test(cleanBookId)) {
                    logBatchHtml('Session: <a href="' + getProxyUrl(cleanBookId) + '" target="_blank" class="monitor-link">📡 Monitor (' + cleanBookId + ')</a>', 'info');
                    updateSessionLinks('comicbook.html', { status: 'RUNNING', sessionId: cleanBookId });
                }
                await waitForTask('comicbook.html', 600000);
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
            var exists = await fileExists('comicbook.html');
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
        try {
            var bookExists = await fileExists('comicbook.html');
            if (bookExists) {
                setBadge('badge-htmlbook', 'done');
            }
        } catch (e) { /* leave as pending */ }


        var statusData = await fetchDocopsStatus();

        if (statusData && statusData.tasks) {
            for (var target in statusData.tasks) {
                if (!statusData.tasks.hasOwnProperty(target)) continue;
                var taskInfo = statusData.tasks[target];
                if (taskInfo.status === 'RUNNING') {
                    registerActiveTask(target);
                }
                updateSessionLinks(target, taskInfo);
            }
        }



    }

    // Run initialization
    loadInitialFiles();
    checkExistingFiles();
     loadApiProviders();
    // ========================================
    // Usage Tracking
    // ========================================
    var usageJsonMode = false;
    var lastUsageData = null;
    // Note: fetchUsageForSession, formatTokenCount, formatCost, renderUsageSummary,
    // renderUsageTable, renderUsageJson, renderTaskSessions, and refreshUsage are
    // defined once below. The duplicate block has been removed.
    async function fetchUsageForSession(sid) {
        try {
            var resp = await fetch('/proxy/usage?sessionId=' + encodeURIComponent(sid) + '&format=json');
            if (!resp.ok) return null;
            return await resp.json();
        } catch (e) {
            console.warn('Failed to fetch usage for session ' + sid + ':', e);
            return null;
        }
    }
    function formatTokenCount(n) {
        if (n === null || n === undefined || isNaN(n)) return '—';
        if (n >= 1000000) return (n / 1000000).toFixed(2) + 'M';
        if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
        return n.toLocaleString();
    }
    function formatCost(c) {
        if (c === null || c === undefined || isNaN(c)) return '—';
        if (c === 0) return '$0.00';
        if (c < 0.01) return '$' + c.toFixed(4);
        return '$' + c.toFixed(4);
    }
    function renderUsageSummary(totals) {
        var promptEl = document.getElementById('usage-total-prompt');
        var completionEl = document.getElementById('usage-total-completion');
        var totalTokensEl = document.getElementById('usage-total-tokens');
        var costEl = document.getElementById('usage-total-cost');
        if (totals) {
            var prompt = totals.prompt_tokens || 0;
            var completion = totals.completion_tokens || 0;
            if (promptEl) promptEl.textContent = formatTokenCount(prompt);
            if (completionEl) completionEl.textContent = formatTokenCount(completion);
            if (totalTokensEl) totalTokensEl.textContent = formatTokenCount(prompt + completion);
            if (costEl) costEl.textContent = formatCost(totals.cost);
        } else {
            if (promptEl) promptEl.textContent = '—';
            if (completionEl) completionEl.textContent = '—';
            if (totalTokensEl) totalTokensEl.textContent = '—';
            if (costEl) costEl.textContent = '—';
        }
    }
    function renderUsageTable(models) {
        var container = document.getElementById('usage-table-container');
        if (!container) return;
        if (!models || models.length === 0) {
            container.innerHTML = '<p class="placeholder">No model usage data available.</p>';
            return;
        }
        var html = '<table class="usage-table">';
        html += '<thead><tr>';
        html += '<th>Model</th>';
        html += '<th class="num-col">Prompt Tokens</th>';
        html += '<th class="num-col">Completion Tokens</th>';
        html += '<th class="num-col">Total Tokens</th>';
        html += '<th class="num-col">Cost</th>';
        html += '</tr></thead><tbody>';
        models.forEach(function(m) {
            var prompt = m.prompt_tokens || 0;
            var completion = m.completion_tokens || 0;
            html += '<tr>';
            html += '<td class="model-name-cell">' + escapeHtml(m.model || 'Unknown') + '</td>';
            html += '<td class="num-col">' + formatTokenCount(prompt) + '</td>';
            html += '<td class="num-col">' + formatTokenCount(completion) + '</td>';
            html += '<td class="num-col">' + formatTokenCount(prompt + completion) + '</td>';
            html += '<td class="num-col cost-cell">' + formatCost(m.cost) + '</td>';
            html += '</tr>';
        });
        html += '</tbody></table>';
        container.innerHTML = html;
    }
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
        var allModels = {};
        var taskUsageMap = {};
        var sessionIds = [];
        // Collect unique child task session IDs only.
        // We intentionally exclude the parent sessionId to avoid double-counting,
        // since the parent session's usage typically includes all child session usage.
        var seenSessions = new Set();
        for (var target in taskSessionMap) {
            if (!taskSessionMap.hasOwnProperty(target)) continue;
            var sid = taskSessionMap[target];
            if (!seenSessions.has(sid)) {
                seenSessions.add(sid);
                sessionIds.push(sid);
            }
        }
        // Only fall back to the parent session if we have no child task sessions at all.
        // This avoids double-counting since parent usage includes child usage.
        if (sessionIds.length === 0 && sessionId) {
             seenSessions.add(sessionId);
             sessionIds.push(sessionId);
        }
        if (sessionIds.length === 0) {
            renderUsageSummary(null);
            renderUsageTable([]);
            renderUsageJson(null);
            renderTaskSessions({});
            setStatus('usage-status', 'No sessions to query', '');
            return;
        }
        var fetchPromises = sessionIds.map(function(sid) {
            return fetchUsageForSession(sid).then(function(data) {
                return { sid: sid, data: data };
            });
        });
        var results = await Promise.all(fetchPromises);
        results.forEach(function(result) {
            if (!result.data) return;
            taskUsageMap[result.sid] = result.data;
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
        modelList.sort(function(a, b) { return b.cost - a.cost; });
        var totalPrompt = 0, totalCompletion = 0, totalCost = 0;
        modelList.forEach(function(m) {
            totalPrompt += m.prompt_tokens;
            totalCompletion += m.completion_tokens;
            totalCost += m.cost;
        });
        var aggregated = {
            models: modelList,
            totals: {
                prompt_tokens: totalPrompt,
                completion_tokens: totalCompletion,
                cost: totalCost
            }
        };
        lastUsageData = aggregated;
        renderUsageSummary(aggregated.totals);
        renderUsageTable(aggregated.models);
        renderUsageJson(aggregated);
        renderTaskSessions(taskUsageMap);
        var sessionCount = sessionIds.length;
        var modelCount = modelList.length;
        setStatus('usage-status', '✓ Loaded from ' + sessionCount + ' session(s), ' + modelCount + ' model(s)', 'success');
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

})();