/* Philosophical Calculator — Cognotik DocOps app */
    import {createStatusPoller, fetchDocopsStatus, runDocOp, waitForTask} from '/app/docops.js';
    import {deleteFile, listFiles, readFile, writeFile} from '/app/fileIO.js';
    import {getProxyUrl, parseSessionUrl} from '/app/session.js';
    import {createSessionLinkManager, updateSessionLinks} from '/app/sessionLinks.js';
    import {initMenu} from '/app/menu.js';
    import {escapeHtml, getFileIcon, renderMarkdown, setBadge, setStatus, showToast} from '/app/ui.js';
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
       // content.md is produced by three separate steps (draft / update / illustrate),
       // so a status update for this target must fan out to all of their badges.
       [CONTENT_FILE, {
           badge: 'badge-content',
           viewer: 'viewer-content',
           extraBadges: ['badge-update', 'badge-illustration']
       }],
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
   const badgesFor = target => {
       const entry = OUTPUTS.get(target);
       if (!entry) return [];
       return [entry.badge, ...(entry.extraBadges ?? [])].filter(Boolean);
   };
    const viewerFor = target => OUTPUTS.get(target)?.viewer;

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
    // Model management (Input tab)
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

    const reloadApiProviders = async () => {
        try {
            availableModels = await loadApiProviders();
            refreshModelDropdowns();
            const providerCount = Object.keys(availableModels).length;
            if (!providerCount) {
                setStatus('model-status', '⚠ No API providers configured', 'error');
            }
        } catch (err) {
            console.warn('[reloadApiProviders] failed:', err);
            setStatus('model-status', `✗ ${err.message}`, 'error');
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

    $('save-model-settings')?.addEventListener('click', () => {
        const selected = getSelectedModels();
        const toSave = Object.fromEntries(MODEL_KEYS.map(key => [key, selected[key] ?? '']));
        saveModelSelections(MODEL_STORAGE_PREFIX, toSave);
        setStatus('model-status', '✓ Saved', 'success');
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
    // Viewer state
    // ========================================================================
    const viewerRawContent = new Map();  // viewerId -> raw text
    const viewerModes = new Map();       // viewerId -> 'rendered' | 'markdown'
    let zoomedViewerId = null;

    const isHtmlViewer = viewerId => viewerId === 'viewer-webpage';

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
       const badgeState =
           status === 'RUNNING' ? 'running'
               : status === 'COMPLETED' ? 'done'
                   : (status === 'ERROR' || status === 'FAILED') ? 'error'
                       : null;
       if (badgeState) {
           for (const badgeId of badgesFor(target)) setBadge(badgeId, badgeState);
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
            window.scrollTo({top: 0, behavior: 'smooth'});
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
                $(`toolbar-${viewerId}`)?.classList.add('is-hidden');
                return;
            }
            btn.disabled = true;
            try {
                await loadIntoViewer(file, viewerId);
                viewer.classList.add('visible');
                $(`toolbar-${viewerId}`)?.classList.remove('is-hidden');
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

        $('zoom-overlay-title').textContent = viewerId.replace(/^viewer-/, '').replace(/-/g, ' ');
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
        if (loaded) {
            $(viewerId)?.classList.add('visible');
            $(`toolbar-${viewerId}`)?.classList.remove('is-hidden');
        }
    };

    for (const btn of document.querySelectorAll('.btn-run')) {
        btn.addEventListener('click', async () => {
            const {op, badge: badgeId, output, viewer: viewerId} = btn.dataset;
            if (!notesEditor?.value.trim()) {
                showToast('Please enter your notes first (Input tab).', 'warning');
                return;
            }
            await saveInputsBeforeRun();

            setBadge(badgeId, 'running');
            btn.disabled = true;
            btn.classList.add('is-busy');
            activeOperations += 1;
            startStatusPolling();

            try {
                const taskId = String(await execDocOp(op, output) ?? '').trim();
                if (/^[a-zA-Z0-9-]+$/.test(taskId)) {
                    updateLinks(output, {status: 'RUNNING', sessionId: taskId});
                }
                await waitForTask(basePath, output, TASK_TIMEOUT_MS, handleStatusUpdate);
               setBadge(badgeId, 'done');
               // Sibling steps that share this target (e.g. content.md) are no longer running.
               for (const sibling of badgesFor(output)) {
                   if (sibling !== badgeId && $(sibling)?.classList.contains('running')) {
                       setBadge(sibling, 'done');
                   }
               }
                await showViewerAfterRun(viewerId, output);
            } catch (err) {
                console.error('[runOperation]', op, err);
                setBadge(badgeId, 'error');
                showToast(`Operation failed: ${err.message}`, 'error');
            } finally {
                btn.disabled = false;
                btn.classList.remove('is-busy');
                activeOperations -= 1;
                maybeStopPolling();
            }
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