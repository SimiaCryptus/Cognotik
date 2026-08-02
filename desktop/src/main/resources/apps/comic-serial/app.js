// Comic Serial App — shared front-end library at /app/
    import { parseSessionUrl, getProxyUrl } from '/app/session.js';
    import { readFile, writeFile, fileExists } from '/app/fileIO.js';
    import { runDocOp, waitForTask, fetchDocopsStatus, createStatusPoller } from '/app/docops.js';
    import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from '/app/models.js';
    import { updateSessionLinks } from '/app/sessionLinks.js';
    import { initMenu } from '/app/menu.js';
    import {
        renderMarkdown, escapeHtml, setStatus, setBadge, showToast, createBatchLogger
    } from '/app/ui.js';

    /* ========================================
     * Constants
     * ======================================== */
    const FILES = Object.freeze({
        idea: 'idea.md',
        comic1: 'comic_1.md',
        book: 'comicbook.html'
    });
    const OPS = Object.freeze({
        comic: 'ops/comic_op.md',
        sequel: 'ops/sequel_op.md',
        book: 'ops/html_book_op.md'
    });
    const BADGES = Object.freeze({
        comic1: 'badge-comic-1',
        sequel: 'badge-sequel',
        book: 'badge-htmlbook'
    });
    const TASK_TIMEOUT_MS = 600_000;
    const MAX_EPISODE_SCAN = 100;
    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const MODEL_PREFIX = 'comic';

    const episodeMd = n => `comic_${n}.md`;
    const episodeHtml = n => `comic_${n}.html`;

    /* ========================================
     * Session / bootstrap
     * ======================================== */
    const { basePath, sessionId } = parseSessionUrl();
    if (!sessionId) console.warn('[bootstrap] Could not determine session from URL path.');

    try {
        initMenu({ appName: 'Comic Serial Generator' });
    } catch (e) {
        console.warn('[bootstrap] Failed to init shared menu:', e);
    }

    window.addEventListener('error', e => console.error('[uncaught]', e.error ?? e.message));
    window.addEventListener('unhandledrejection', e => console.error('[rejection]', e.reason));

    const $ = id => document.getElementById(id);

    /* ========================================
     * Models
     * ======================================== */
    let availableModels = {};

    const modelSelects = () => ({
        smartModel: $('comic-smart-model'),
        fastModel: $('comic-fast-model'),
        imageModel: $('comic-image-model')
    });

    function getSelectedModels() {
        const sel = modelSelects();
        return Object.fromEntries(MODEL_KEYS.map(k => [k, sel[k]?.value ?? '']));
    }

    /** Only non-empty keys are sent to the backend. */
    function getModelOverrides() {
        return Object.fromEntries(Object.entries(getSelectedModels()).filter(([, v]) => v));
    }

    function updateModelSummary() {
        const target = $('model-summary');
        if (!target) return;
        const models = getSelectedModels();
        const rows = [
            ['🧠 Smart Model', models.smartModel],
            ['⚡ Fast Model', models.fastModel],
            ['🎨 Image Model', models.imageModel]
        ];
        target.innerHTML = rows.map(([label, value]) => `
            <div class="model-summary-row">
                <span class="model-summary-label">${label}</span>
                <span class="model-summary-value${value ? '' : ' not-set'}">${escapeHtml(value || 'Not set')}</span>
                <span class="model-indicator${value ? '' : ' no-model'}"><span class="model-dot"></span>${value ? 'Active' : 'None'}</span>
            </div>`).join('');
    }

    function populateModels() {
        const sel = modelSelects();
        const elements = MODEL_KEYS.map(k => sel[k]).filter(Boolean);
        if (elements.length === 0) return;
        const saved = loadModelSelections(MODEL_PREFIX, MODEL_KEYS) ?? {};
        populateModelDropdowns(availableModels, elements, saved);
        // Defensive restore in case the helper signature differs.
        for (const key of MODEL_KEYS) {
            const el = sel[key];
            const value = saved[key];
            if (el && value && [...el.options].some(o => o.value === value)) el.value = value;
        }
        updateModelSummary();
    }

    async function initModels() {
        try {
            availableModels = await loadApiProviders();
        } catch (e) {
            console.warn('[initModels] Failed to load API providers:', e);
        }
        populateModels();
    }

    $('save-model-settings')?.addEventListener('click', () => {
        saveModelSelections(MODEL_PREFIX, getSelectedModels());
        updateModelSummary();
        setStatus('model-status', '✓ Model settings saved', 'success');
    });

    $('reset-model-settings')?.addEventListener('click', () => {
        saveModelSelections(MODEL_PREFIX, Object.fromEntries(MODEL_KEYS.map(k => [k, ''])));
        const sel = modelSelects();
        for (const key of MODEL_KEYS) {
            const el = sel[key];
            if (el?.options.length) el.selectedIndex = 0;
        }
        updateModelSummary();
        setStatus('model-status', '✓ Model settings reset to defaults', 'success');
    });

    for (const key of MODEL_KEYS) {
        modelSelects()[key]?.addEventListener('change', updateModelSummary);
    }

    /* ========================================
     * Task execution + inline session links
     * ======================================== */
    const trackedTasks = new Map();      // target -> taskInfo
    const activeTasks = new Set();       // targets with a running task
    let statusPoller = null;

    const sanitizeTaskId = id => {
        const s = id == null ? '' : String(id).trim();
        return s && s.length < 200 && /^[a-zA-Z0-9-]+$/.test(s) ? s : '';
    };

    function linkContainerFor(target) {
        if (target === FILES.book) return 'links-htmlbook';
        if (target === FILES.comic1) return 'links-comic-1';
        return 'links-sequel';
    }

    function trackSession(target, taskInfo) {
        if (!target || !taskInfo) return;
        trackedTasks.set(target, taskInfo);
        try {
            updateSessionLinks(target, taskInfo, getProxyUrl, linkContainerFor(target));
        } catch (e) {
            console.warn('[trackSession] Failed to render session links:', e);
        }
    }

    function getStatusPoller() {
        statusPoller ??= createStatusPoller(basePath, (target, taskInfo) => {
            trackSession(target, taskInfo);
            if (['COMPLETED', 'ERROR', 'FAILED'].includes(taskInfo?.status)) unregisterActiveTask(target);
        }, 3000);
        return statusPoller;
    }

    function registerActiveTask(target) {
        activeTasks.add(target);
        try { getStatusPoller().start(); } catch (e) { console.warn('[registerActiveTask]', e); }
    }

    function unregisterActiveTask(target) {
        activeTasks.delete(target);
        if (activeTasks.size === 0) {
            try { getStatusPoller().stop(); } catch (e) { console.warn('[unregisterActiveTask]', e); }
        }
    }

    /**
     * Run a DocOp against a target and wait for completion.
     * @returns {Promise<string>} the child session id (may be empty)
     */
    async function runTask({ op, target, badgeId, onSession }) {
        setBadge(badgeId, 'running');
        registerActiveTask(target);
        try {
            const taskId = sanitizeTaskId(await runDocOp(sessionId, op, target, getModelOverrides()));
            if (taskId) {
                trackSession(target, { status: 'RUNNING', sessionId: taskId });
                onSession?.(taskId);
            }
            await waitForTask(basePath, target, TASK_TIMEOUT_MS, (t, info) => trackSession(t, info));
            setBadge(badgeId, 'done');
            if (taskId) trackSession(target, { status: 'COMPLETED', sessionId: taskId });
            return taskId;
        } catch (e) {
            setBadge(badgeId, 'error');
            console.error(`[runTask] ${op} → ${target} failed:`, e);
            throw e;
        } finally {
            unregisterActiveTask(target);
        }
    }

    /* ========================================
     * Comic display (iframe with toolbar)
     * ======================================== */
    const RESIZE_ICON = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M14 14H10L14 10V14ZM14 6L6 14H2L14 2V6Z"/></svg>';

    function createComicDisplay(filePath) {
        const el = document.createElement('div');
        el.className = 'comic-display';
        el.innerHTML = `
            <div class="comic-display-toolbar">
                <span class="size-label" data-size-label></span>
                <button type="button" class="icon-btn" data-action="size-down" aria-label="Decrease preview height">−</button>
                <button type="button" class="icon-btn" data-action="size-up" aria-label="Increase preview height">+</button>
                <button type="button" class="icon-btn" data-action="fullscreen" aria-label="Toggle fullscreen">⛶</button>
            </div>
            <iframe class="comic-iframe" title="Comic preview" sandbox="allow-same-origin allow-scripts"></iframe>
            <span class="resize-hint" aria-hidden="true">${RESIZE_ICON}</span>`;
        el.querySelector('iframe').src = `${basePath}/${filePath}?t=${Date.now()}`;
        return el;
    }

    let activeFullscreen = null;
    let backdrop = null;

    function getBackdrop() {
        if (!backdrop) {
            backdrop = document.createElement('div');
            backdrop.className = 'fullscreen-backdrop hidden';
            backdrop.addEventListener('click', () => activeFullscreen && exitFullscreen(activeFullscreen));
            document.body.append(backdrop);
        }
        return backdrop;
    }

    function updateSizeLabel(el) {
        const label = el.querySelector('[data-size-label]');
        if (!label) return;
        label.textContent = el.classList.contains('fullscreen')
            ? 'fullscreen'
            : `${Math.round(el.offsetWidth)} × ${Math.round(el.offsetHeight)}`;
    }

    function enterFullscreen(el) {
        el.classList.add('fullscreen');
        activeFullscreen = el;
        if (el.requestFullscreen) {
            el.requestFullscreen().catch(() => getBackdrop().classList.remove('hidden'));
        } else {
            getBackdrop().classList.remove('hidden');
        }
        updateSizeLabel(el);
    }

    function exitFullscreen(el) {
        if (document.fullscreenElement === el) document.exitFullscreen().catch(() => {});
        el.classList.remove('fullscreen');
        getBackdrop().classList.add('hidden');
        activeFullscreen = null;
        updateSizeLabel(el);
    }

    document.addEventListener('fullscreenchange', () => {
        if (!document.fullscreenElement && activeFullscreen) exitFullscreen(activeFullscreen);
    });

    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && activeFullscreen && !document.fullscreenElement) exitFullscreen(activeFullscreen);
    });

    document.addEventListener('click', e => {
        const btn = e.target.closest('.comic-display-toolbar button');
        const display = btn?.closest('.comic-display');
        if (!display) return;
        const { action } = btn.dataset;
        if (action === 'fullscreen') {
            display.classList.contains('fullscreen') ? exitFullscreen(display) : enterFullscreen(display);
            return;
        }
        if (display.classList.contains('fullscreen')) return;
        const delta = action === 'size-up' ? 150 : -150;
        display.style.height = `${Math.min(Math.max(display.offsetHeight + delta, 200), 2000)}px`;
        updateSizeLabel(display);
    });

    const sizeObserver = new ResizeObserver(entries => {
        for (const { target } of entries) updateSizeLabel(target);
    });
    new MutationObserver(mutations => {
        for (const mutation of mutations) {
            for (const node of mutation.addedNodes) {
                if (node.nodeType !== Node.ELEMENT_NODE) continue;
                const displays = node.matches?.('.comic-display') ? [node] : [...node.querySelectorAll?.('.comic-display') ?? []];
                for (const el of displays) {
                    sizeObserver.observe(el);
                    updateSizeLabel(el);
                }
            }
        }
    }).observe(document.body, { childList: true, subtree: true });

    /* ========================================
     * Viewers & empty states
     * ======================================== */
    const emptyState = (icon, title, desc) => `
        <div class="empty-state">
            <div class="empty-icon" aria-hidden="true">${icon}</div>
            <p class="empty-title">${escapeHtml(title)}</p>
            <p class="empty-desc">${desc}</p>
        </div>`;

    /** Render `target` (md or html) into a viewer, preferring the rendered HTML. */
    async function renderInto(viewer, target) {
        if (!viewer) return false;
        const htmlPath = target.endsWith('.html') ? target : target.replace(/\.md$/, '.html');
        if (await fileExists(basePath, htmlPath)) {
            viewer.replaceChildren(createComicDisplay(htmlPath));
            viewer.classList.add('visible');
            return true;
        }
        if (!target.endsWith('.html')) {
            const md = await readFile(basePath, target);
            if (md !== null) {
                viewer.innerHTML = renderMarkdown(md);
                viewer.classList.add('visible');
                return true;
            }
        }
        viewer.innerHTML = emptyState('📄', 'Nothing here yet', 'Run the step above to generate this file.');
        viewer.classList.add('visible');
        return false;
    }

    async function viewFile(filePath, viewerId) {
        const viewer = $(viewerId);
        if (!viewer) return;
        if (viewer.classList.contains('visible')) {
            viewer.classList.remove('visible');
            viewer.replaceChildren();
            return;
        }
        try {
            await renderInto(viewer, filePath);
        } catch (e) {
            console.error('[viewFile]', e);
            viewer.innerHTML = emptyState('⚠️', 'Could not load file', escapeHtml(e.message));
            viewer.classList.add('visible');
        }
    }

    for (const btn of document.querySelectorAll('.btn-view')) {
        btn.addEventListener('click', () => viewFile(btn.dataset.file, btn.dataset.viewer));
    }

    /* ========================================
     * Idea editor
     * ======================================== */
    const ideaEditor = $('idea-editor');

    async function saveIdea({ silent = false } = {}) {
        const content = ideaEditor?.value ?? '';
        if (!content.trim()) {
            if (!silent) setStatus('idea-status', '✗ Please enter an idea first', 'error');
            return false;
        }
        try {
            await writeFile(basePath, FILES.idea, content);
            setStatus('idea-status', silent ? '✓ Auto-saved' : '✓ Saved successfully', 'success');
            return true;
        } catch (e) {
            console.error('[saveIdea]', e);
            setStatus('idea-status', `✗ ${e.message}`, 'error');
            return false;
        }
    }

    $('save-idea')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        try { await saveIdea(); } finally { btn.disabled = false; }
    });

    let ideaTimer;
    ideaEditor?.addEventListener('input', () => {
        clearTimeout(ideaTimer);
        ideaTimer = setTimeout(() => saveIdea({ silent: true }), 800);
    });

    /** Ensure the idea is present on disk before running an op. */
    async function requireIdea() {
        if (!ideaEditor?.value.trim()) {
            showToast('Enter your story idea on the Idea tab first.', 'error');
            return false;
        }
        return saveIdea({ silent: true });
    }

    /* ========================================
     * Episode counting
     * ======================================== */
    async function countEpisodes() {
        let count = 0;
        for (let i = 1; i <= MAX_EPISODE_SCAN; i++) {
            const exists = await fileExists(basePath, episodeMd(i)) || await fileExists(basePath, episodeHtml(i));
            if (!exists) break;
            count = i;
        }
        return count;
    }

    async function updateEpisodeCount() {
        const count = await countEpisodes();
        const countEl = $('episode-count');
        const nextEl = $('next-episode-label');
        if (countEl) countEl.textContent = String(count);
        if (nextEl) nextEl.textContent = `#${count + 1}`;
        return count;
    }

    /* ========================================
     * Pipeline actions
     * ======================================== */
    for (const btn of document.querySelectorAll('.btn-run')) {
        btn.addEventListener('click', async () => {
            const { op, output, badge, viewer } = btn.dataset;
            if (!await requireIdea()) return;
            btn.disabled = true;
            try {
                await runTask({ op, target: output, badgeId: badge });
                await renderInto($(viewer), output);
                await updateEpisodeCount();
                showToast('Comic #1 generated', 'success');
            } catch (e) {
                showToast(`Generation failed: ${e.message}`, 'error');
            } finally {
                btn.disabled = false;
            }
        });
    }

    async function generateSequel() {
        const count = await countEpisodes();
        if (count === 0) {
            showToast('No episodes yet — generate Comic #1 first.', 'error');
            return null;
        }
        const next = count + 1;
        const target = episodeMd(next);
        await runTask({ op: OPS.sequel, target, badgeId: BADGES.sequel });
        const viewer = $('viewer-sequel');
        if (viewer) {
            viewer.replaceChildren();
            const heading = document.createElement('h4');
            heading.textContent = `Comic #${next}`;
            viewer.append(heading);
            const holder = document.createElement('div');
            viewer.append(holder);
            await renderInto(holder, target);
            viewer.classList.add('visible');
        }
        await updateEpisodeCount();
        return next;
    }

    $('generate-sequel')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        try {
            const num = await generateSequel();
            if (num) showToast(`Comic #${num} generated`, 'success');
        } catch (e) {
            showToast(`Failed to generate sequel: ${e.message}`, 'error');
        } finally {
            btn.disabled = false;
        }
    });

    $('refresh-count')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        try { await updateEpisodeCount(); } finally { btn.disabled = false; }
    });

    async function generateHtmlBook() {
        if (await countEpisodes() === 0) {
            showToast('Generate at least one comic before compiling the book.', 'error');
            return false;
        }
        await runTask({ op: OPS.book, target: FILES.book, badgeId: BADGES.book });
        await renderInto($('viewer-htmlbook'), FILES.book);
        return true;
    }

    $('generate-htmlbook')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        try {
            if (await generateHtmlBook()) showToast('HTML comicbook compiled', 'success');
        } catch (e) {
            showToast(`Failed to compile book: ${e.message}`, 'error');
        } finally {
            btn.disabled = false;
        }
    });

    async function openBook() {
        if (!await fileExists(basePath, FILES.book)) {
            showToast('The HTML book has not been generated yet.', 'error');
            return;
        }
        window.open(`${basePath}/${FILES.book}?t=${Date.now()}`, '_blank', 'noopener');
    }

    $('open-htmlbook-tab')?.addEventListener('click', openBook);
    $('series-view-book')?.addEventListener('click', openBook);

    /* ========================================
     * Batch generation
     * ======================================== */
    const batchLogger = createBatchLogger('batch-log');
    const monitorLink = id =>
        `Session: <a href="${escapeHtml(getProxyUrl(id))}" target="_blank" rel="noopener" class="monitor-link">📡 Monitor (${escapeHtml(id)})</a>`;

    function logSession(id) {
        if (typeof batchLogger.logHtml === 'function') batchLogger.logHtml(monitorLink(id), 'info');
        else batchLogger.log(`Session: ${id}`, 'info');
    }

    $('run-batch')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        const requested = Number.parseInt($('batch-count')?.value ?? '', 10);
        if (!Number.isFinite(requested) || requested < 1) {
            showToast('Enter a valid number of episodes (1 or more).', 'error');
            return;
        }
        if (!await requireIdea()) return;

        btn.disabled = true;
        batchLogger.clear?.();
        $('batch-log')?.replaceChildren();

        try {
            let current = await countEpisodes();
            let remaining = requested;
            batchLogger.log(`Current episodes: ${current}`, 'info');
            batchLogger.log(`Target: generate ${remaining} new episode(s)`, 'info');

            if (current === 0) {
                batchLogger.log('Generating Comic #1 from idea…', 'info');
                await runTask({ op: OPS.comic, target: FILES.comic1, badgeId: BADGES.comic1, onSession: logSession });
                batchLogger.log('✓ Comic #1 generated', 'success');
                current = 1;
                remaining -= 1;
                await updateEpisodeCount();
            }

            for (let i = 0; i < remaining; i++) {
                const num = current + 1 + i;
                const target = episodeMd(num);
                batchLogger.log(`Generating Comic #${num}…`, 'info');
                await runTask({ op: OPS.sequel, target, badgeId: BADGES.sequel, onSession: logSession });
                batchLogger.log(`✓ Comic #${num} generated`, 'success');
                await updateEpisodeCount();
            }

            batchLogger.log('🎉 Series generation complete!', 'success');
            batchLogger.log('Compiling HTML comicbook…', 'info');
            try {
                await runTask({ op: OPS.book, target: FILES.book, badgeId: BADGES.book, onSession: logSession });
                batchLogger.log('✓ HTML comicbook compiled', 'success');
                await renderInto($('viewer-htmlbook'), FILES.book);
            } catch (bookErr) {
                batchLogger.log(`⚠ Could not compile HTML book: ${bookErr.message}`, 'warn');
            }
            showToast('Batch generation finished', 'success');
        } catch (e) {
            console.error('[batch]', e);
            batchLogger.log(`✗ Error: ${e.message}`, 'error');
            showToast(`Batch failed: ${e.message}`, 'error');
        } finally {
            btn.disabled = false;
        }
    });

    /* ========================================
     * Series view
     * ======================================== */
    async function loadSeries() {
        const container = $('series-container');
        if (!container) return;
        const count = await countEpisodes();

        if (count === 0) {
            container.innerHTML = `<div class="card">${emptyState(
                '📚', 'No episodes yet',
                'Go to the <strong>Pipeline</strong> tab and generate Comic #1.')}</div>`;
            return;
        }

        const fragment = document.createDocumentFragment();
        for (let i = 1; i <= count; i++) {
            const card = document.createElement('article');
            card.className = 'episode-card';
            card.innerHTML = `
                <button type="button" class="episode-header" data-episode="${i}"
                        aria-expanded="false" aria-controls="episode-content-${i}">
                    <span class="episode-title">
                        <span class="episode-number">${i}</span>
                        <span class="episode-label">Episode #${i}</span>
                    </span>
                    <span class="episode-toggle" aria-hidden="true">▼</span>
                </button>
                <div class="episode-content" id="episode-content-${i}"></div>`;
            fragment.append(card);
        }
        container.replaceChildren(fragment);

        for (const header of container.querySelectorAll('.episode-header')) {
            header.addEventListener('click', () => toggleEpisode(Number(header.dataset.episode)));
        }
        await toggleEpisode(count);
    }

    async function toggleEpisode(num) {
        const content = $(`episode-content-${num}`);
        const header = document.querySelector(`.episode-header[data-episode="${num}"]`);
        if (!content) return;

        if (content.classList.contains('visible')) {
            content.classList.remove('visible');
            header?.setAttribute('aria-expanded', 'false');
            header?.querySelector('.episode-toggle')?.classList.remove('open');
            return;
        }

        if (content.dataset.loaded !== 'true') {
            try {
                await renderInto(content, episodeMd(num));
                content.dataset.loaded = 'true';
            } catch (e) {
                console.error('[toggleEpisode]', e);
                content.innerHTML = emptyState('⚠️', 'Could not load episode', escapeHtml(e.message));
            }
        }
        content.classList.add('visible');
        header?.setAttribute('aria-expanded', 'true');
        header?.querySelector('.episode-toggle')?.classList.add('open');
    }

    $('refresh-series')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        try { await loadSeries(); } finally { btn.disabled = false; }
    });

    $('series-add-episode')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        try {
            if (await generateSequel()) await loadSeries();
        } catch (e) {
            showToast(`Failed to generate episode: ${e.message}`, 'error');
        } finally {
            btn.disabled = false;
        }
    });

    $('series-compile-book')?.addEventListener('click', async ev => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        try {
            if (await generateHtmlBook()) setStatus('series-status', '✓ Book compiled', 'success');
        } catch (e) {
            setStatus('series-status', `✗ ${e.message}`, 'error');
        } finally {
            btn.disabled = false;
        }
    });

    /* ========================================
     * Tabs
     * ======================================== */
    const tabs = [...document.querySelectorAll('.nav-link')];

    function activateTab(tab) {
        for (const t of tabs) {
            const selected = t === tab;
            t.classList.toggle('active', selected);
            t.setAttribute('aria-selected', String(selected));
            t.tabIndex = selected ? 0 : -1;
        }
        for (const section of document.querySelectorAll('.section')) {
            const active = section.id === tab.dataset.section;
            section.classList.toggle('active', active);
            section.hidden = !active;
        }
        if (tab.dataset.section === 'section-series') loadSeries();
        if (tab.dataset.section === 'section-pipeline') updateEpisodeCount();
    }

    tabs.forEach((tab, index) => {
        tab.addEventListener('click', () => activateTab(tab));
        tab.addEventListener('keydown', e => {
            const offset = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
            if (!offset) return;
            e.preventDefault();
            const next = tabs[(index + offset + tabs.length) % tabs.length];
            activateTab(next);
            next.focus();
        });
    });

    /* ========================================
     * Initial state (filesystem is the source of truth)
     * ======================================== */
    async function restoreState() {
        try {
            const idea = await readFile(basePath, FILES.idea);
            if (idea !== null && ideaEditor) ideaEditor.value = idea;
        } catch (e) {
            console.warn('[restoreState] Could not load idea.md:', e);
        }

        try {
            if (await fileExists(basePath, FILES.comic1)) setBadge(BADGES.comic1, 'done');
            if (await updateEpisodeCount() > 1) setBadge(BADGES.sequel, 'done');
            if (await fileExists(basePath, FILES.book)) setBadge(BADGES.book, 'done');
        } catch (e) {
            console.warn('[restoreState] File probing failed:', e);
        }

        try {
            const status = await fetchDocopsStatus(basePath);
            for (const [target, info] of Object.entries(status?.tasks ?? {})) {
                if (info?.status === 'RUNNING') registerActiveTask(target);
                trackSession(target, info);
            }
        } catch (e) {
            console.warn('[restoreState] Failed to fetch docops status:', e);
        }
    }

    await restoreState();
    await initModels();