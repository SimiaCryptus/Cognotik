/* Philosophical Calculator — Cognotik DocOps app */
    import {createStatusPoller, fetchDocopsStatus, runDocOp, waitForTask} from '/app/docops.js';
    import {deleteFile, listFiles, readFile, writeFile} from '/app/fileIO.js';
    import {getProxyUrl, parseSessionUrl} from '/app/session.js';
    import {createSessionLinkManager, updateSessionLinks} from '/app/sessionLinks.js';
    import {initMenu} from '/app/menu.js';
    import {
        createBatchLogger,
        escapeHtml,
        getFileIcon,
        renderMarkdown,
        setBadge,
        setStatus,
        showToast
    } from '/app/ui.js';
    import {
        loadApiProviders,
        loadModelSelections,
        populateModelDropdowns,
        saveModelSelections
    } from '/app/models.js';
    
    // ========================================================================
    // Global error reporting
    // ========================================================================
    window.addEventListener('error', e => console.error('[uncaught]', e.error ?? e.message));
    window.addEventListener('unhandledrejection', e => console.error('[rejection]', e.reason));
    
    // ========================================================================
    // Session & menubar
    // ========================================================================
    const {basePath, sessionId} = parseSessionUrl();
    if (!sessionId) console.warn('[init] Could not determine session from URL path.');
    
    try {
        initMenu({appName: 'Philosophical Calculator'});
    } catch (err) {
        console.warn('[init] Menu initialization failed:', err);
    }
    
    // ========================================================================
    // Constants — all target paths live here
    // ========================================================================
    const NOTES_DIR = 'notes';
    const NOTES_FILE = 'notes/notes.md';
    const INSTRUCT_FILE = 'instruct.md';
    const CONTENT_FILE = 'content.md';
    const WEBPAGE_FILE = 'page.html';
    const TASK_TIMEOUT_MS = 600_000;
    const POLL_INTERVAL_MS = 3000;
    const AUTOSAVE_MS = 800;
    
    /** target path -> { badge, viewer } */
    const OUTPUTS = new Map([
        ['summary.md', {badge: 'badge-summary', viewer: 'viewer-summary'}],
        [CONTENT_FILE, {badge: 'badge-content', viewer: 'viewer-content'}],
        ['brainstorm.md', {badge: 'badge-brainstorm', viewer: 'viewer-brainstorm'}],
        ['dialectical.md', {badge: 'badge-dialectical', viewer: 'viewer-dialectical'}],
        ['socratic.md', {badge: 'badge-socratic', viewer: 'viewer-socratic'}],
        ['perspectives.md', {badge: 'badge-perspectives', viewer: 'viewer-perspectives'}],
        ['persuasive.md', {badge: 'badge-persuasive', viewer: 'viewer-persuasive'}],
        ['gametheory.md', {badge: 'badge-gametheory', viewer: 'viewer-gametheory'}],
        ['narrative.md', {badge: 'badge-narrative', viewer: 'viewer-narrative'}],
        ['debate.md', {badge: 'badge-debate', viewer: 'viewer-debate'}],
        ['protocol.md', {badge: 'badge-protocol', viewer: 'viewer-protocol'}],
        ['comic.md', {badge: 'badge-comic', viewer: 'viewer-comic'}],
        ['technical_explanation.md', {badge: 'badge-technical', viewer: 'viewer-technical'}],
        [WEBPAGE_FILE, {badge: 'badge-webpage', viewer: 'viewer-webpage'}]
    ]);
    
    const badgeFor = target => OUTPUTS.get(target)?.badge;
    const viewerFor = target => OUTPUTS.get(target)?.viewer;
    
    const LENSES = [
        {key: 'brainstorm', category: 'analysis', op: 'ops/brainstorm_op.md', output: 'brainstorm.md', label: 'Brainstorm'},
        {key: 'dialectical', category: 'analysis', op: 'ops/dialectical_op.md', output: 'dialectical.md', label: 'Dialectical Analysis'},
        {key: 'socratic', category: 'analysis', op: 'ops/socratic_op.md', output: 'socratic.md', label: 'Socratic Dialogue'},
        {key: 'perspectives', category: 'analysis', op: 'ops/perspectives_op.md', output: 'perspectives.md', label: 'Multi-Perspective Analysis'},
        {key: 'gametheory', category: 'analysis', op: 'ops/gametheory_op.md', output: 'gametheory.md', label: 'Game Theory Analysis'},
        {key: 'debate', category: 'analysis', op: 'ops/debate_op.md', output: 'debate.md', label: 'Historical Figure Debate'},
        {key: 'protocol', category: 'analysis', op: 'ops/protocol_op.md', output: 'protocol.md', label: 'Unrunnable Protocol Analysis'},
        {key: 'persuasive', category: 'output', op: 'ops/persuasive_op.md', output: 'persuasive.md', label: 'Persuasive Essay'},
        {key: 'narrative', category: 'output', op: 'ops/narrative_op.md', output: 'narrative.md', label: 'Narrative Dramatization'},
        {key: 'comic', category: 'output', op: 'ops/comic_op.md', output: 'comic.md', label: 'Comic Book Generation'},
        {key: 'technical', category: 'output', op: 'ops/technical_explanation_op.md', output: 'technical_explanation.md', label: 'Technical Tutorial'},
        {key: 'webpage', category: 'output', op: 'ops/webpage_op.md', output: WEBPAGE_FILE, label: 'HTML Webpage Generation'}
    ];
    
    // ========================================================================
    // Small helpers
    // ========================================================================
    const $ = id => document.getElementById(id);
    
    const debounce = (fn, ms = AUTOSAVE_MS) => {
        let timer;
        return (...args) => {
            clearTimeout(timer);
            timer = setTimeout(() => fn(...args), ms);
        };
    };
    
    const confirmDialogEl = $('confirm-dialog');
    
    /** In-page replacement for window.confirm(). */
    const confirmAction = message => new Promise(resolve => {
        if (typeof confirmDialogEl?.showModal !== 'function') {
            resolve(true);
            return;
        }
        $('confirm-dialog-message').textContent = message;
        const onClose = () => {
            confirmDialogEl.removeEventListener('close', onClose);
            resolve(confirmDialogEl.returnValue === 'confirm');
        };
        confirmDialogEl.addEventListener('close', onClose);
        confirmDialogEl.returnValue = 'cancel';
        confirmDialogEl.showModal();
    });
    
    const renderEmptyState = (host, icon, title, descHtml) => {
        if (!host) return;
        const wrap = document.createElement('div');
        wrap.className = 'empty-state';
        const iconEl = document.createElement('div');
        iconEl.className = 'empty-icon';
        iconEl.setAttribute('aria-hidden', 'true');
        iconEl.textContent = icon;
        const titleEl = document.createElement('p');
        titleEl.className = 'empty-title';
        titleEl.textContent = title;
        const descEl = document.createElement('p');
        descEl.className = 'empty-desc';
        descEl.innerHTML = descHtml;
        wrap.append(iconEl, titleEl, descEl);
        host.replaceChildren(wrap);
    };
    
    /** Wrap generated markdown tables so they scroll instead of overflowing. */
    const wrapTables = host => {
        for (const table of host.querySelectorAll('table')) {
            if (table.parentElement?.classList.contains('table-scroll')) continue;
            const scroller = document.createElement('div');
            scroller.className = 'table-scroll';
            table.replaceWith(scroller);
            scroller.appendChild(table);
        }
    };
    
    // ========================================================================
    // Model management
    // ========================================================================
    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const MODEL_STORAGE_PREFIX = 'philcalc';
    let availableModels = {};
    
    const modelSelects = () => ({
        smartModel: $('smart-model-select'),
        fastModel: $('fast-model-select'),
        imageModel: $('image-model-select')
    });
    
    const refreshModelDropdowns = () => {
        const map = modelSelects();
        const selects = MODEL_KEYS.map(key => map[key]).filter(Boolean);
        if (!selects.length) return;
        populateModelDropdowns(availableModels, selects, loadModelSelections(MODEL_STORAGE_PREFIX, MODEL_KEYS));
    };
    
    const renderProviderInfo = () => {
        const container = $('provider-info');
        if (!container) return;
        const names = Object.keys(availableModels);
        if (!names.length) {
            renderEmptyState(container, '🔌', 'No API providers configured',
                'Set up API keys in the main application settings, then press <strong>Reload Models</strong>.');
            return;
        }
        const list = document.createElement('div');
        list.className = 'provider-list';
        for (const name of names) {
            const models = availableModels[name] ?? [];
            const item = document.createElement('div');
            item.className = 'provider-item';
    
            const nameEl = document.createElement('div');
            nameEl.className = 'provider-name';
            nameEl.textContent = `🔌 ${name} `;
            const count = document.createElement('span');
            count.className = 'provider-model-count';
            count.textContent = `(${models.length} model${models.length === 1 ? '' : 's'})`;
            nameEl.appendChild(count);
    
            const tags = document.createElement('div');
            tags.className = 'provider-models';
            for (const model of models) {
                const tag = document.createElement('span');
                tag.className = 'provider-model-tag';
                tag.title = model.description ?? '';
                tag.textContent = model.name;
                tags.appendChild(tag);
            }
            item.append(nameEl, tags);
            list.appendChild(item);
        }
        container.replaceChildren(list);
    };
    
    const reloadApiProviders = async () => {
        try {
            availableModels = await loadApiProviders();
            refreshModelDropdowns();
            renderProviderInfo();
        } catch (err) {
            console.warn('[reloadApiProviders] failed:', err);
            renderEmptyState($('provider-info'), '⚠️', 'Could not load providers', escapeHtml(err.message));
        }
    };
    
    const getSelectedModels = () => {
        const map = modelSelects();
        const result = {};
        for (const key of MODEL_KEYS) {
            const value = map[key]?.value;
            if (value) result[key] = value;
        }
        return result;
    };
    
    // ========================================================================
    // Viewer state
    // ========================================================================
    const viewerRawContent = new Map();  // viewerId -> raw text
    const viewerModes = new Map();       // viewerId -> 'rendered' | 'markdown'
    let zoomedViewerId = null;
    
    const isHtmlViewer = viewerId => viewerId === 'viewer-webpage' || viewerId === 'result-webpage';
    
    // ========================================================================
    // Session links (inline, per step)
    // ========================================================================
    const linkManager = createSessionLinkManager(getProxyUrl);
    
    const ensureSessionLinkContainer = target => {
        const containerId = `session-link-${target.replace(/[^a-zA-Z0-9]/g, '-')}`;
        if ($(containerId)) return containerId;
        const container = document.createElement('div');
        container.id = containerId;
        container.className = 'session-link-container';
        container.dataset.sessionLinks = target;
        const viewer = $(viewerFor(target) ?? `viewer-${target.replace(/\.[a-z]+$/, '')}`);
        viewer?.parentElement?.insertBefore(container, viewer);
        return containerId;
    };
    
    const updateLinks = (target, taskInfo) => {
        const containerId = ensureSessionLinkContainer(target);
        linkManager.update(target, taskInfo);
        updateSessionLinks(target, taskInfo, getProxyUrl, containerId);
    };
    
    // ========================================================================
    // Status polling — started on demand, stopped when idle
    // ========================================================================
    const runningTargets = new Set();
    let activeOperations = 0;
    let polling = false;
    
    const startStatusPolling = () => {
        if (polling) return;
        polling = true;
        statusPoller.start();
    };
    
    const stopStatusPolling = () => {
        if (!polling) return;
        polling = false;
        statusPoller.stop();
    };
    
    const maybeStopPolling = () => {
        if (activeOperations === 0 && runningTargets.size === 0) stopStatusPolling();
    };
    
    const handleStatusUpdate = (target, taskInfo) => {
        const status = taskInfo?.status;
        const badgeId = badgeFor(target);
        if (badgeId) {
            if (status === 'RUNNING') setBadge(badgeId, 'running');
            else if (status === 'COMPLETED') setBadge(badgeId, 'done');
            else if (status === 'ERROR' || status === 'FAILED') setBadge(badgeId, 'error');
        }
        if (status === 'RUNNING') runningTargets.add(target);
        else runningTargets.delete(target);
        updateLinks(target, taskInfo);
        maybeStopPolling();
    };
    
    const statusPoller = createStatusPoller(basePath, handleStatusUpdate, POLL_INTERVAL_MS);
    
    const execDocOp = (opPath, targetPath) => runDocOp(sessionId, opPath, targetPath, getSelectedModels());
    
    // ========================================================================
    // Navigation (main tabs)
    // ========================================================================
    for (const link of document.querySelectorAll('.nav-link')) {
        link.addEventListener('click', () => {
            for (const other of document.querySelectorAll('.nav-link')) {
                other.classList.remove('active');
                other.setAttribute('aria-selected', 'false');
            }
            for (const section of document.querySelectorAll('.section')) section.classList.remove('active');
            link.classList.add('active');
            link.setAttribute('aria-selected', 'true');
            $(link.dataset.section)?.classList.add('active');
        });
    }
    
    // ========================================================================
    // Results tabs (+ lazy load)
    // ========================================================================
    for (const tab of document.querySelectorAll('.results-tab')) {
        tab.addEventListener('click', () => {
            for (const other of document.querySelectorAll('.results-tab')) {
                other.classList.remove('active');
                other.setAttribute('aria-selected', 'false');
            }
            for (const panel of document.querySelectorAll('.tab-panel')) panel.classList.remove('active');
            tab.classList.add('active');
            tab.setAttribute('aria-selected', 'true');
            const panel = $(tab.dataset.tab);
            panel?.classList.add('active');
            const refreshBtn = panel?.querySelector('.btn-refresh[data-file]');
            if (refreshBtn && !viewerRawContent.has(refreshBtn.dataset.viewer)) refreshBtn.click();
        });
    }
    
    // ========================================================================
    // Notes & instructions
    // ========================================================================
    const notesEditor = $('notes-editor');
    const instructEditor = $('instruct-editor');
    
    const persistNotes = async () => {
        await writeFile(basePath, NOTES_FILE, notesEditor?.value ?? '');
    };
    const persistInstructions = async () => {
        await writeFile(basePath, INSTRUCT_FILE, instructEditor?.value ?? '');
    };
    
    const autosaveNotes = debounce(async () => {
        try {
            await persistNotes();
            setStatus('notes-status', '✓ Auto-saved', 'success');
        } catch (err) {
            console.warn('[autosaveNotes]', err);
            setStatus('notes-status', `✗ ${err.message}`, 'error');
        }
    });
    
    const autosaveInstructions = debounce(async () => {
        try {
            await persistInstructions();
            setStatus('instruct-status', '✓ Auto-saved', 'success');
        } catch (err) {
            console.warn('[autosaveInstructions]', err);
            setStatus('instruct-status', `✗ ${err.message}`, 'error');
        }
    });
    
    notesEditor?.addEventListener('input', autosaveNotes);
    instructEditor?.addEventListener('input', autosaveInstructions);
    
    $('save-notes')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        if (!notesEditor.value.trim()) {
            setStatus('notes-status', '✗ Notes cannot be empty', 'error');
            return;
        }
        button.disabled = true;
        try {
            await persistNotes();
            setStatus('notes-status', '✓ Notes saved', 'success');
        } catch (err) {
            console.error('[saveNotes]', err);
            setStatus('notes-status', `✗ ${err.message}`, 'error');
        } finally {
            button.disabled = false;
        }
    });
    
    $('save-instruct')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
            await persistInstructions();
            setStatus('instruct-status', '✓ Instructions saved', 'success');
        } catch (err) {
            console.error('[saveInstructions]', err);
            setStatus('instruct-status', `✗ ${err.message}`, 'error');
        } finally {
            button.disabled = false;
        }
    });
    
    const saveInputsBeforeRun = async () => {
        try {
            if (notesEditor?.value.trim()) await persistNotes();
            if (instructEditor?.value.trim()) await persistInstructions();
        } catch (err) {
            console.warn('[saveInputsBeforeRun]', err);
        }
    };
    
    // ========================================================================
    // Viewers
    // ========================================================================
    const renderViewerContent = viewerId => {
        const viewer = $(viewerId);
        const raw = viewerRawContent.get(viewerId);
        if (!viewer || raw == null) return;
        const mode = viewerModes.get(viewerId) ?? 'rendered';
    
        if (isHtmlViewer(viewerId)) {
            if (mode === 'markdown') {
                const pre = document.createElement('pre');
                pre.className = 'markdown-source';
                pre.textContent = raw;
                viewer.replaceChildren(pre);
                return;
            }
            const iframe = document.createElement('iframe');
            iframe.className = 'html-preview-iframe';
            iframe.setAttribute('sandbox', 'allow-same-origin');
            iframe.title = 'Generated webpage preview';
            viewer.replaceChildren(iframe);
            try {
                const doc = iframe.contentDocument ?? iframe.contentWindow.document;
                doc.open();
                doc.write(raw);
                doc.close();
            } catch (err) {
                console.warn('[renderViewerContent] iframe write failed:', err);
                const pre = document.createElement('pre');
                pre.className = 'markdown-source';
                pre.textContent = raw;
                viewer.replaceChildren(pre);
            }
            return;
        }
    
        if (mode === 'markdown') {
            const pre = document.createElement('pre');
            pre.className = 'markdown-source';
            pre.textContent = raw;
            viewer.replaceChildren(pre);
            return;
        }
    
        const html = renderMarkdown(raw);
        if (html?.trim()) {
            viewer.innerHTML = html;
            wrapTables(viewer);
        } else {
            const pre = document.createElement('pre');
            pre.className = 'markdown-source';
            pre.textContent = raw;
            viewer.replaceChildren(pre);
        }
    };
    
    const modeLabel = (viewerId, mode) => isHtmlViewer(viewerId)
        ? (mode === 'rendered' ? '📄 Source' : '🌐 Rendered')
        : (mode === 'rendered' ? '📝 Markdown' : '🌐 Rendered');
    
    const ensureViewerToolbar = viewerId => {
        const existing = $(`toolbar-${viewerId}`);
        if (existing) return existing;
        const viewer = $(viewerId);
        if (!viewer?.parentElement) return null;
    
        const toolbar = document.createElement('div');
        toolbar.id = `toolbar-${viewerId}`;
        toolbar.className = 'viewer-toolbar';
    
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'btn btn-sm btn-toolbar btn-toggle-mode';
        toggle.title = 'Toggle source / rendered';
        toggle.textContent = modeLabel(viewerId, viewerModes.get(viewerId) ?? 'rendered');
        toggle.addEventListener('click', () => {
            const next = (viewerModes.get(viewerId) ?? 'rendered') === 'rendered' ? 'markdown' : 'rendered';
            viewerModes.set(viewerId, next);
            toggle.textContent = modeLabel(viewerId, next);
            renderViewerContent(viewerId);
            if (zoomedViewerId === viewerId) refreshZoomBody(viewerId);
        });
    
        const zoom = document.createElement('button');
        zoom.type = 'button';
        zoom.className = 'btn btn-sm btn-toolbar btn-zoom';
        zoom.title = 'Zoom / fullscreen';
        zoom.textContent = '🔍 Zoom';
        zoom.addEventListener('click', () => openZoomOverlay(viewerId));
    
        toolbar.append(toggle, zoom);
    
        if (isHtmlViewer(viewerId)) {
            const open = document.createElement('a');
            open.className = 'btn btn-sm btn-toolbar btn-open-newtab';
            open.href = `${basePath}/${WEBPAGE_FILE}?t=${Date.now()}`;
            open.target = '_blank';
            open.rel = 'noopener';
            open.textContent = '🚀 Open in New Tab';
            toolbar.appendChild(open);
        }
    
        viewer.parentElement.insertBefore(toolbar, viewer);
        return toolbar;
    };
    
    const loadIntoViewer = async (filePath, viewerId) => {
        const viewer = $(viewerId);
        if (!viewer) return false;
        try {
            const content = await readFile(basePath, filePath);
            if (content == null || !content.trim()) {
                viewerRawContent.delete(viewerId);
                renderEmptyState(viewer, '📭', 'Nothing generated yet',
                    `Run the operation that produces <code>${escapeHtml(filePath)}</code>.`);
                return false;
            }
            viewerRawContent.set(viewerId, content);
            if (!viewerModes.has(viewerId)) viewerModes.set(viewerId, 'rendered');
            renderViewerContent(viewerId);
            ensureViewerToolbar(viewerId);
            return true;
        } catch (err) {
            console.error('[loadIntoViewer]', filePath, err);
            viewerRawContent.delete(viewerId);
            renderEmptyState(viewer, '⚠️', 'Could not load file', escapeHtml(err.message));
            return false;
        }
    };
    
    for (const btn of document.querySelectorAll('.btn-view')) {
        btn.addEventListener('click', async () => {
            const {file, viewer: viewerId} = btn.dataset;
            const viewer = $(viewerId);
            if (!viewer) return;
            if (viewer.classList.contains('visible')) {
                viewer.classList.remove('visible');
                return;
            }
            btn.disabled = true;
            try {
                await loadIntoViewer(file, viewerId);
                viewer.classList.add('visible');
            } finally {
                btn.disabled = false;
            }
        });
    }
    
    for (const btn of document.querySelectorAll('.btn-refresh[data-file]')) {
        btn.addEventListener('click', async () => {
            btn.disabled = true;
            try {
                await loadIntoViewer(btn.dataset.file, btn.dataset.viewer);
            } finally {
                btn.disabled = false;
            }
        });
    }
    
    // ========================================================================
    // Zoom overlay
    // ========================================================================
    const refreshZoomBody = viewerId => {
        const body = $('zoom-overlay-body');
        if (!body) return;
        const raw = viewerRawContent.get(viewerId);
        if (raw == null) return;
        const mode = viewerModes.get(viewerId) ?? 'rendered';
    
        if (mode === 'markdown') {
            const pre = document.createElement('pre');
            pre.className = 'markdown-source';
            pre.textContent = raw;
            body.replaceChildren(pre);
            return;
        }
        if (isHtmlViewer(viewerId)) {
            const iframe = document.createElement('iframe');
            iframe.className = 'zoom-preview-iframe';
            iframe.setAttribute('sandbox', 'allow-same-origin');
            iframe.title = 'Generated webpage preview';
            body.replaceChildren(iframe);
            try {
                const doc = iframe.contentDocument ?? iframe.contentWindow.document;
                doc.open();
                doc.write(raw);
                doc.close();
            } catch (err) {
                console.warn('[refreshZoomBody] iframe write failed:', err);
            }
            return;
        }
        body.innerHTML = renderMarkdown(raw);
        wrapTables(body);
    };
    
    const closeZoomOverlay = () => {
        $('zoom-overlay')?.classList.remove('visible');
        document.body.classList.remove('scroll-locked');
        zoomedViewerId = null;
    };
    
    const buildZoomOverlay = () => {
        const overlay = document.createElement('div');
        overlay.id = 'zoom-overlay';
        overlay.className = 'zoom-overlay';
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');
    
        const header = document.createElement('div');
        header.className = 'zoom-overlay-header';
    
        const title = document.createElement('span');
        title.className = 'zoom-title';
        title.id = 'zoom-overlay-title';
    
        const buttons = document.createElement('div');
        buttons.className = 'zoom-header-buttons';
    
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'btn btn-sm btn-toolbar';
        toggle.id = 'zoom-toggle-mode';
        toggle.textContent = '📝 Markdown';
        toggle.addEventListener('click', () => {
            if (!zoomedViewerId) return;
            const next = (viewerModes.get(zoomedViewerId) ?? 'rendered') === 'rendered' ? 'markdown' : 'rendered';
            viewerModes.set(zoomedViewerId, next);
            toggle.textContent = modeLabel(zoomedViewerId, next);
            renderViewerContent(zoomedViewerId);
            refreshZoomBody(zoomedViewerId);
            const inline = document.querySelector(`#toolbar-${zoomedViewerId} .btn-toggle-mode`);
            if (inline) inline.textContent = modeLabel(zoomedViewerId, next);
        });
    
        const openNewTab = document.createElement('a');
        openNewTab.className = 'btn btn-sm btn-toolbar is-hidden';
        openNewTab.id = 'zoom-open-newtab';
        openNewTab.href = '#';
        openNewTab.target = '_blank';
        openNewTab.rel = 'noopener';
        openNewTab.textContent = '🚀 Open in New Tab';
    
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn btn-sm btn-toolbar';
        close.id = 'zoom-close-btn';
        close.textContent = '✕ Close';
        close.addEventListener('click', closeZoomOverlay);
    
        buttons.append(toggle, openNewTab, close);
        header.append(title, buttons);
    
        const body = document.createElement('div');
        body.className = 'zoom-overlay-body';
        body.id = 'zoom-overlay-body';
    
        overlay.append(header, body);
        document.body.appendChild(overlay);
    
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && zoomedViewerId) closeZoomOverlay();
        });
        return overlay;
    };
    
    function openZoomOverlay(viewerId) {
        if (!viewerRawContent.has(viewerId)) return;
        zoomedViewerId = viewerId;
        const overlay = $('zoom-overlay') ?? buildZoomOverlay();
        const mode = viewerModes.get(viewerId) ?? 'rendered';
    
        $('zoom-overlay-title').textContent = viewerId.replace(/^(viewer-|result-)/, '').replace(/-/g, ' ');
        $('zoom-toggle-mode').textContent = modeLabel(viewerId, mode);
    
        const openNewTab = $('zoom-open-newtab');
        if (openNewTab) {
            openNewTab.classList.toggle('is-hidden', !isHtmlViewer(viewerId));
            if (isHtmlViewer(viewerId)) openNewTab.href = `${basePath}/${WEBPAGE_FILE}?t=${Date.now()}`;
        }
    
        refreshZoomBody(viewerId);
        overlay.classList.add('visible');
        document.body.classList.add('scroll-locked');
        $('zoom-close-btn')?.focus();
    }
    
    // ========================================================================
    // Run a single operation
    // ========================================================================
    const showViewerAfterRun = async (viewerId, outputPath) => {
        if (!viewerId) return;
        const loaded = await loadIntoViewer(outputPath, viewerId);
        if (loaded) $(viewerId)?.classList.add('visible');
    };
    
    for (const btn of document.querySelectorAll('.btn-run')) {
        btn.addEventListener('click', async () => {
            const {op, badge: badgeId, output, viewer: viewerId} = btn.dataset;
            await saveInputsBeforeRun();
    
            setBadge(badgeId, 'running');
            btn.disabled = true;
            activeOperations += 1;
            startStatusPolling();
    
            try {
                const taskId = String(await execDocOp(op, output) ?? '').trim();
                if (/^[a-zA-Z0-9-]+$/.test(taskId)) {
                    updateLinks(output, {status: 'RUNNING', sessionId: taskId});
                }
                await waitForTask(basePath, output, TASK_TIMEOUT_MS, handleStatusUpdate);
                setBadge(badgeId, 'done');
                await showViewerAfterRun(viewerId, output);
            } catch (err) {
                console.error('[runOperation]', op, err);
                setBadge(badgeId, 'error');
                showToast(`Operation failed: ${err.message}`, 'error');
            } finally {
                btn.disabled = false;
                activeOperations -= 1;
                maybeStopPolling();
            }
        });
    }
    
    // ========================================================================
    // Sequential batch execution
    // ========================================================================
    const runSequential = async (steps, logId) => {
        const batchLog = createBatchLogger(logId);
        for (const step of steps) {
            batchLog.log(`Starting: ${step.label}`, 'info');
            setBadge(step.badge, 'running');
            try {
                const taskId = String(await execDocOp(step.op, step.output) ?? '').trim();
                if (/^[a-zA-Z0-9-]+$/.test(taskId)) {
                    const proxyUrl = getProxyUrl(taskId);
                    batchLog.logHtml(
                        `Session: <a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">📡 Monitor (${escapeHtml(taskId.slice(0, 12))}…)</a>`,
                        'info');
                    updateLinks(step.output, {status: 'RUNNING', sessionId: taskId});
                }
                await waitForTask(basePath, step.output, TASK_TIMEOUT_MS, handleStatusUpdate);
                setBadge(step.badge, 'done');
                batchLog.log(`✓ Completed: ${step.label}`, 'success');
                await showViewerAfterRun(step.viewer, step.output);
            } catch (err) {
                console.error('[runSequential]', step.label, err);
                setBadge(step.badge, 'error');
                batchLog.log(`✗ Failed: ${step.label} — ${err.message}`, 'error');
                throw err;
            }
        }
    };
    
    $('run-core-pipeline')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        if (!notesEditor?.value.trim()) {
            showToast('Please enter your notes first.', 'warning');
            return;
        }
        await saveInputsBeforeRun();
    
        $('batch-log')?.replaceChildren();
        button.disabled = true;
        activeOperations += 1;
        startStatusPolling();
    
        const logger = createBatchLogger('batch-log');
        try {
            await runSequential([
                {op: 'ops/summarize_op.md', output: 'summary.md', badge: 'badge-summary', viewer: 'viewer-summary', label: 'Summarize Notes'},
                {op: 'ops/draft_article_op.md', output: CONTENT_FILE, badge: 'badge-content', viewer: 'viewer-content', label: 'Draft Article'}
            ], 'batch-log');
            logger.log('🎉 Core pipeline complete!', 'success');
            showToast('Core pipeline complete', 'success');
        } catch (err) {
            logger.log('Pipeline stopped due to error.', 'error');
            showToast(`Pipeline failed: ${err.message}`, 'error');
        } finally {
            button.disabled = false;
            activeOperations -= 1;
            maybeStopPolling();
        }
    });
    
    // ========================================================================
    // Lens batch
    // ========================================================================
    $('run-selected-lenses')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        const selected = new Set([...document.querySelectorAll('.lens-check:checked')].map(cb => cb.value));
        if (!selected.size) {
            showToast('Please select at least one lens to run.', 'warning');
            return;
        }
        await saveInputsBeforeRun();
    
        $('lens-batch-log')?.replaceChildren();
        button.disabled = true;
        activeOperations += 1;
        startStatusPolling();
    
        const steps = LENSES.filter(lens => selected.has(lens.key)).map(lens => ({
            op: lens.op,
            output: lens.output,
            badge: badgeFor(lens.output),
            viewer: viewerFor(lens.output),
            label: lens.label
        }));
    
        const logger = createBatchLogger('lens-batch-log');
        try {
            await runSequential(steps, 'lens-batch-log');
            logger.log('🎉 All selected lenses complete!', 'success');
            showToast('All selected lenses complete', 'success');
        } catch (err) {
            logger.log(`Lens execution stopped due to error: ${err.message}`, 'error');
            showToast(`Lens run failed: ${err.message}`, 'error');
        } finally {
            button.disabled = false;
            activeOperations -= 1;
            maybeStopPolling();
        }
    });
    
    const setLensChecks = predicate => {
        for (const cb of document.querySelectorAll('.lens-check')) cb.checked = predicate(cb.value);
    };
    const keysOfCategory = category => new Set(LENSES.filter(l => l.category === category).map(l => l.key));
    
    $('select-all-lenses')?.addEventListener('click', () => setLensChecks(() => true));
    $('deselect-all-lenses')?.addEventListener('click', () => setLensChecks(() => false));
    $('select-all-analysis')?.addEventListener('click', () => {
        const keys = keysOfCategory('analysis');
        setLensChecks(value => keys.has(value));
    });
    $('select-all-outputs')?.addEventListener('click', () => {
        const keys = keysOfCategory('output');
        setLensChecks(value => keys.has(value));
    });
    
    // ========================================================================
    // Model settings buttons
    // ========================================================================
    $('save-model-settings')?.addEventListener('click', () => {
        const selected = getSelectedModels();
        const toSave = Object.fromEntries(MODEL_KEYS.map(key => [key, selected[key] ?? '']));
        saveModelSelections(MODEL_STORAGE_PREFIX, toSave);
        setStatus('model-status', '✓ Model settings saved', 'success');
    });
    
    $('reload-models')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        setStatus('model-status', 'Loading models…', '');
        try {
            await reloadApiProviders();
            setStatus('model-status', '✓ Models reloaded', 'success');
        } catch (err) {
            setStatus('model-status', `✗ ${err.message}`, 'error');
        } finally {
            button.disabled = false;
        }
    });
    
    // ========================================================================
    // Content editor (Results tab)
    // ========================================================================
    const contentEditorEl = $('content-editor');
    const contentEditorContainer = $('content-editor-container');
    
    $('edit-content-btn')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
            contentEditorEl.value = (await readFile(basePath, CONTENT_FILE)) ?? '';
        } catch (err) {
            console.warn('[editContent]', err);
            contentEditorEl.value = '';
        } finally {
            button.disabled = false;
        }
        contentEditorContainer.classList.add('visible');
        contentEditorEl.focus();
    });
    
    $('save-content-btn')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
            await saveContent(contentEditorEl.value);
            setStatus('content-editor-status', '✓ Saved content.md', 'success');
        } catch (err) {
            console.error('[saveContent]', err);
            setStatus('content-editor-status', `✗ ${err.message}`, 'error');
        } finally {
            button.disabled = false;
        }
    });
    
    $('close-content-editor-btn')?.addEventListener('click', () => {
        contentEditorContainer.classList.remove('visible');
    });
    
    // ========================================================================
    // Pipeline manual article editor
    // ========================================================================
    const pipelineEditorEl = $('pipeline-content-editor');
    const pipelineEditorContainer = $('pipeline-content-editor-container');
    let pipelineEditorDirty = false;
    
    const CONTENT_VIEWERS = ['viewer-content', 'viewer-update', 'viewer-illustration', 'viewer-manual-content', 'result-content', 'result-illustration'];
    
    async function saveContent(content) {
        await writeFile(basePath, CONTENT_FILE, content);
        if (content.trim()) setBadge('badge-content', 'done');
        for (const viewerId of CONTENT_VIEWERS) {
            const viewer = $(viewerId);
            if (!viewer) continue;
            viewerRawContent.set(viewerId, content);
            if (!viewerModes.has(viewerId)) viewerModes.set(viewerId, 'rendered');
            if (viewer.classList.contains('visible') || viewerId.startsWith('result-')) renderViewerContent(viewerId);
        }
        if (contentEditorEl) contentEditorEl.value = content;
        if (pipelineEditorEl && pipelineEditorEl.value !== content) pipelineEditorEl.value = content;
    }
    
    const updatePipelineWordCount = () => {
        const el = $('pipeline-editor-wordcount');
        if (!el || !pipelineEditorEl) return;
        const text = pipelineEditorEl.value.trim();
        el.textContent = text
            ? `${text.split(/\s+/).length.toLocaleString()} words · ${text.length.toLocaleString()} chars`
            : '0 words';
    };
    
    const savePipelineContent = async closeAfter => {
        if (!pipelineEditorEl) return;
        try {
            await saveContent(pipelineEditorEl.value);
            pipelineEditorDirty = false;
            setStatus('pipeline-editor-status', '✓ Saved content.md', 'success');
            setStatus('pipeline-editor-status-bottom', '✓ Saved content.md', 'success');
            if (closeAfter) pipelineEditorContainer.classList.remove('visible');
        } catch (err) {
            console.error('[savePipelineContent]', err);
            setStatus('pipeline-editor-status', `✗ ${err.message}`, 'error');
            setStatus('pipeline-editor-status-bottom', `✗ ${err.message}`, 'error');
        }
    };
    
    const autosavePipelineContent = debounce(() => {
        if (pipelineEditorDirty) savePipelineContent(false);
    });
    
    $('pipeline-edit-content-btn')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        if (pipelineEditorContainer.classList.contains('visible')) {
            if (pipelineEditorDirty && !await confirmAction('You have unsaved changes. Close without saving?')) return;
            pipelineEditorContainer.classList.remove('visible');
            pipelineEditorDirty = false;
            return;
        }
        button.disabled = true;
        try {
            pipelineEditorEl.value = (await readFile(basePath, CONTENT_FILE)) ?? '';
        } catch (err) {
            console.warn('[openPipelineEditor]', err);
            pipelineEditorEl.value = '';
        } finally {
            button.disabled = false;
        }
        pipelineEditorContainer.classList.add('visible');
        pipelineEditorDirty = false;
        updatePipelineWordCount();
        pipelineEditorEl.focus();
    });
    
    pipelineEditorEl?.addEventListener('input', () => {
        pipelineEditorDirty = true;
        updatePipelineWordCount();
        autosavePipelineContent();
    });
    
    pipelineEditorEl?.addEventListener('keydown', event => {
        if ((event.ctrlKey || event.metaKey) && event.key === 's') {
            event.preventDefault();
            savePipelineContent(false);
        }
    });
    
    $('pipeline-save-content-btn')?.addEventListener('click', () => savePipelineContent(false));
    $('pipeline-save-content-btn-bottom')?.addEventListener('click', () => savePipelineContent(false));
    $('pipeline-save-close-content-btn')?.addEventListener('click', () => savePipelineContent(true));
    $('pipeline-save-close-content-btn-bottom')?.addEventListener('click', () => savePipelineContent(true));
    
    $('pipeline-close-editor-btn')?.addEventListener('click', async () => {
        if (pipelineEditorDirty && !await confirmAction('You have unsaved changes. Close without saving?')) return;
        pipelineEditorContainer.classList.remove('visible');
        pipelineEditorDirty = false;
    });
    
    const MARKDOWN_SNIPPETS = {
        heading: selected => ({text: `\n## ${selected || 'Heading'}\n`, offset: selected ? null : 4}),
        bold: selected => ({text: `**${selected || 'bold text'}**`, offset: selected ? null : 2}),
        italic: selected => ({text: `*${selected || 'italic text'}*`, offset: selected ? null : 1}),
        bullet: selected => ({
            text: selected ? selected.split('\n').map(line => `- ${line}`).join('\n') : '\n- Item 1\n- Item 2\n- Item 3\n',
            offset: null
        }),
        quote: selected => ({
            text: selected ? selected.split('\n').map(line => `> ${line}`).join('\n') : '\n> Quote text here\n',
            offset: null
        }),
        code: selected => ({text: `\n\`\`\`\n${selected || 'code here'}\n\`\`\`\n`, offset: selected ? null : 5}),
        hr: () => ({text: '\n---\n', offset: null})
    };
    
    for (const tool of document.querySelectorAll('.pipeline-editor-tool')) {
        tool.addEventListener('click', () => {
            const ta = pipelineEditorEl;
            const snippet = MARKDOWN_SNIPPETS[tool.dataset.action];
            if (!ta || !snippet) return;
            const {selectionStart: start, selectionEnd: end, value} = ta;
            const selected = value.slice(start, end);
            const {text, offset} = snippet(selected);
            ta.value = value.slice(0, start) + text + value.slice(end);
            const caret = start + (offset ?? text.length);
            ta.setSelectionRange(caret, caret);
            ta.focus();
            pipelineEditorDirty = true;
            updatePipelineWordCount();
            autosavePipelineContent();
        });
    }
    
    // ========================================================================
    // File upload
    // ========================================================================
    const uploadZone = $('upload-zone');
    const fileInput = $('file-input');
    const uploadedFilesList = $('uploaded-files-list');
    
    for (const eventName of ['dragenter', 'dragover', 'dragleave', 'drop']) {
        document.body.addEventListener(eventName, event => {
            event.preventDefault();
            event.stopPropagation();
        }, false);
    }
    
    if (uploadZone) {
        for (const eventName of ['dragenter', 'dragover']) {
            uploadZone.addEventListener(eventName, () => uploadZone.classList.add('drag-over'));
        }
        for (const eventName of ['dragleave', 'drop']) {
            uploadZone.addEventListener(eventName, () => uploadZone.classList.remove('drag-over'));
        }
        uploadZone.addEventListener('drop', event => {
            const files = event.dataTransfer?.files;
            if (files?.length) handleFileUpload(files);
        });
    }
    
    fileInput?.addEventListener('change', () => {
        if (fileInput.files?.length) handleFileUpload(fileInput.files);
        fileInput.value = '';
    });
    
    const uploadSingleFile = async file => {
        const fileName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_');
        const response = await fetch(`${basePath}/${NOTES_DIR}/${fileName}`, {
            method: 'PUT',
            headers: {'Content-Type': file.type || 'application/octet-stream'},
            body: file
        });
        if (!response.ok) throw new Error(`Upload failed for ${fileName}: ${response.status}`);
    };
    
    async function handleFileUpload(fileList) {
        const files = [...fileList];
        let completed = 0;
        let failed = 0;
        setStatus('notes-status', `Uploading ${files.length} file(s)…`, '');
        for (const file of files) {
            try {
                await uploadSingleFile(file);
                completed += 1;
                setStatus('notes-status', `Uploaded ${completed}/${files.length}…`, '');
            } catch (err) {
                failed += 1;
                console.error('[handleFileUpload]', file.name, err);
            }
        }
        if (failed === 0) {
            setStatus('notes-status', `✓ Uploaded ${completed} file(s)`, 'success');
        } else {
            setStatus('notes-status', `⚠ Uploaded ${completed}, failed ${failed}`, 'error');
            showToast(`${failed} file(s) failed to upload`, 'error');
        }
        await refreshUploadedFileList();
    }
    
    const renderFileList = files => {
        if (!uploadedFilesList) return;
        if (!files.length) {
            renderEmptyState(uploadedFilesList, '📂', 'No source files uploaded',
                `Drop files above to add them to <code>${NOTES_DIR}/</code>.`);
            uploadedFilesList.classList.add('visible');
            return;
        }
        const header = document.createElement('div');
        header.className = 'file-list-header';
        header.textContent = `📂 Files in ${NOTES_DIR}/ (${files.length})`;
    
        const items = document.createElement('div');
        items.className = 'file-list-items';
        for (const name of files) {
            const row = document.createElement('div');
            row.className = 'file-list-item';
    
            const icon = document.createElement('span');
            icon.className = 'file-item-icon';
            icon.setAttribute('aria-hidden', 'true');
            icon.textContent = getFileIcon(name);
    
            const label = document.createElement('span');
            label.className = 'file-item-name';
            label.textContent = name;
    
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'btn btn-sm btn-danger-ghost file-delete-btn';
            remove.title = `Delete ${name}`;
            remove.setAttribute('aria-label', `Delete ${name}`);
            remove.textContent = '✕';
            remove.addEventListener('click', async () => {
                if (!await confirmAction(`Delete ${NOTES_DIR}/${name}?`)) return;
                remove.disabled = true;
                try {
                    await deleteFile(basePath, `${NOTES_DIR}/${name}`);
                    setStatus('notes-status', `✓ Deleted ${name}`, 'success');
                    await refreshUploadedFileList();
                } catch (err) {
                    console.error('[deleteUploadedFile]', err);
                    setStatus('notes-status', `✗ ${err.message}`, 'error');
                } finally {
                    remove.disabled = false;
                }
            });
    
            row.append(icon, label, remove);
            items.appendChild(row);
        }
    
        uploadedFilesList.replaceChildren(header, items);
        uploadedFilesList.classList.add('visible');
    };
    
    async function refreshUploadedFileList() {
        if (!uploadedFilesList) return;
        try {
            const entries = await listFiles(basePath, NOTES_DIR);
            const files = (Array.isArray(entries) ? entries : [])
                .map(entry => (typeof entry === 'string' ? entry : entry?.name ?? entry?.fileName ?? ''))
                .filter(name => name && !['.', '..', '_files.json', '.gitignore'].includes(name));
            renderFileList(files);
        } catch (err) {
            console.warn('[refreshUploadedFileList]', err);
            renderEmptyState(uploadedFilesList, '📂', 'No source files uploaded',
                `Drop files above to add them to <code>${NOTES_DIR}/</code>.`);
            uploadedFilesList.classList.add('visible');
        }
    }
    
    $('refresh-file-list')?.addEventListener('click', () => refreshUploadedFileList());
    
    // ========================================================================
    // Boot: restore state from the filesystem
    // ========================================================================
    const loadInitialFiles = async () => {
        try {
            const notes = await readFile(basePath, NOTES_FILE);
            if (notes != null && notesEditor) notesEditor.value = notes;
        } catch (err) {
            console.warn('[loadInitialFiles] notes:', err);
        }
        try {
            const instruct = await readFile(basePath, INSTRUCT_FILE);
            if (instruct != null && instructEditor) instructEditor.value = instruct;
        } catch (err) {
            console.warn('[loadInitialFiles] instructions:', err);
        }
    };
    
    const checkExistingFiles = async () => {
        let anyRunning = false;
        try {
            const statusData = await fetchDocopsStatus(basePath);
            for (const [target, taskInfo] of Object.entries(statusData?.tasks ?? {})) {
                handleStatusUpdate(target, taskInfo);
                if (taskInfo?.status === 'RUNNING') anyRunning = true;
            }
        } catch (err) {
            console.warn('[checkExistingFiles] status fetch failed:', err);
        }
    
        for (const [target, {badge: badgeId}] of OUTPUTS) {
            const badge = $(badgeId);
            if (badge?.classList.contains('running') || badge?.textContent === 'done') continue;
            try {
                const content = await readFile(basePath, target);
                if (content?.trim()) setBadge(badgeId, 'done');
            } catch {
                /* leave as pending */
            }
        }
    
        if (anyRunning) startStatusPolling(); else stopStatusPolling();
    };
    
    await loadInitialFiles();
    await refreshUploadedFileList();
    reloadApiProviders();
    checkExistingFiles();