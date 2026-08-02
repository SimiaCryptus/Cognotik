/* Webapp Builder — Cognotik DocOps app */
    import { parseSessionUrl, getProxyUrl } from '/lib/app/session.js';
    import { readFile, writeFile, fileExists, listFiles } from '/lib/app/fileIO.js';
    import { runDocOp, fetchDocopsStatus, waitForTask, createStatusPoller } from '/lib/app/docops.js';
    import {
        loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections
    } from '/lib/app/models.js';
    import {
        renderMarkdown, escapeHtml, setStatus, setBadge, showToast, createBatchLogger, getFileIcon
    } from '/lib/app/ui.js';
    import { updateSessionLinks } from '/lib/app/sessionLinks.js';
    import { getStatus as gitGetStatus, initRepository as gitInit, commit as gitCommit } from '/lib/app/git.js';
    import { initMenu } from '/lib/app/menu.js';

    /* ── Constants ─────────────────────────────────────────────── */

    const OPS = {
        renderFull: 'ops/render_op.md',
        renderSimple: 'ops/render_simple_op.md',
        update: 'ops/update_op.md',
        test: 'ops/test_op.md',
        review: 'ops/review_op.md',
    };
    const TARGETS = {
        idea: 'idea.md',
        notes: 'notes.md',
        code: 'code/',
        codeReadme: 'code/README.md',
        codeIndex: 'code/index.html',
        testDoc: 'code/test.md',
    };
    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const VALID_RENDER_OPS = new Set([OPS.renderFull, OPS.renderSimple]);
    const TASK_ID_PATTERN = /^[a-zA-Z0-9-]+$/;

    const { basePath, sessionId, appId } = parseSessionUrl();
    const STORAGE_PREFIX = appId ?? 'webapp-factory';

    const withBase = (path) => `${basePath}${basePath.endsWith('/') ? '' : '/'}${path}`;
    const appIndexUrl = withBase(TARGETS.codeIndex);
    const codeBaseUrl = new URL(withBase('code/'), window.location.origin).href;

    const $ = (id) => document.getElementById(id);
    const normalizeTarget = (target) => (target ?? '').replace(/\/+$/, '');

    /* ── Mutable state ─────────────────────────────────────────── */

    let availableModels = {};
    let activeCodeBadge = 'badge-render';
    let activeCodeTaskSessionId = null;
    let poller = null;
    let diagramTimer = null;
    let inFlight = 0;
    let capturedLogs = [];
    let consoleStat = { logs: 0, warnings: 0, errors: 0 };

    const batchLogger = createBatchLogger('batch-log');

    function logBatch(message, type) {
        $('batch-log')?.classList.add('visible');
        batchLogger.log(message, type);
    }
    function logBatchHtml(html, type) {
        $('batch-log')?.classList.add('visible');
        batchLogger.logHtml(html, type);
    }

    /* ── Tabs ──────────────────────────────────────────────────── */

    function activateSection(sectionId) {
        for (const tab of document.querySelectorAll('.tab-nav .tab')) {
            const on = tab.dataset.section === sectionId;
            tab.classList.toggle('active', on);
            tab.setAttribute('aria-selected', String(on));
        }
        for (const section of document.querySelectorAll('main > .section')) {
            section.classList.toggle('active', section.id === sectionId);
        }
        if (sectionId === 'section-results') loadPreview();
        if (sectionId === 'section-test') loadTestArtifacts();
    }

    function activateResultTab(tabId) {
        for (const tab of document.querySelectorAll('.results-tab')) {
            const on = tab.dataset.tab === tabId;
            tab.classList.toggle('active', on);
            tab.setAttribute('aria-selected', String(on));
        }
        for (const panel of document.querySelectorAll('.tab-panel')) {
            panel.classList.toggle('active', panel.id === tabId);
        }
    }

    document.querySelector('.tab-nav')?.addEventListener('click', (event) => {
        const tab = event.target.closest('.tab');
        if (tab?.dataset.section) activateSection(tab.dataset.section);
    });
    document.querySelector('.results-tabs')?.addEventListener('click', (event) => {
        const tab = event.target.closest('.results-tab');
        if (tab?.dataset.tab) activateResultTab(tab.dataset.tab);
    });

    /* ── Launch links ──────────────────────────────────────────── */

    function updateLaunchLinks(available) {
        for (const id of ['app-launch-link', 'banner-launch-link', 'results-launch-link', 'preview-launch-link']) {
            const el = $(id);
            if (!el) continue;
            el.href = appIndexUrl;
            el.classList.toggle('visible', available);
        }
    }

    async function refreshAppAvailability() {
        const available = await fileExists(basePath, TARGETS.codeIndex);
        updateLaunchLinks(available);
        $('app-preview-banner')?.classList.toggle('visible', available);
        return available;
    }

    /* ── Models ────────────────────────────────────────────────── */

    function modelSelects() {
        return [$('model-smart'), $('model-fast'), $('model-image')].filter(Boolean);
    }

    async function initApiProviders() {
        try {
            availableModels = await loadApiProviders();
            refreshModelDropdowns();
        } catch (e) {
            console.warn('[initApiProviders] failed:', e);
            for (const select of modelSelects()) {
                select.replaceChildren(new Option('Failed to load models — configure API keys first', ''));
            }
        }
    }

    function refreshModelDropdowns() {
        const selects = modelSelects();
        if (selects.length === 0) return;
        const saved = loadModelSelections(STORAGE_PREFIX, MODEL_KEYS);
        try {
            populateModelDropdowns(availableModels, selects, saved);
        } catch (e) {
            console.warn('[refreshModelDropdowns] populateModelDropdowns failed:', e);
        }
        const restore = (select, value) => {
            if (select && value && [...select.options].some((o) => o.value === value)) select.value = value;
        };
        restore($('model-smart'), saved.smartModel);
        restore($('model-fast'), saved.fastModel);
        restore($('model-image'), saved.imageModel);
    }

    function getSelectedModels() {
        const models = {};
        const smart = $('model-smart')?.value;
        const fast = $('model-fast')?.value;
        const image = $('model-image')?.value;
        if (smart) models.smartModel = smart;
        if (fast) models.fastModel = fast;
        if (image) models.imageModel = image;
        return models;
    }

    $('save-models')?.addEventListener('click', () => {
        const models = getSelectedModels();
        saveModelSelections(STORAGE_PREFIX, {
            smartModel: models.smartModel ?? '',
            fastModel: models.fastModel ?? '',
            imageModel: models.imageModel ?? '',
        });
        setStatus('model-status', '✓ Model settings saved', 'success');
    });

    $('refresh-models')?.addEventListener('click', async (event) => {
        const button = event.currentTarget;
        button.disabled = true;
        setStatus('model-status', 'Loading models…', '');
        try {
            await initApiProviders();
            setStatus('model-status', '✓ Models refreshed', 'success');
        } finally {
            button.disabled = false;
        }
    });

    /* ── Render mode ───────────────────────────────────────────── */

    const renderOpStorageKey = `${STORAGE_PREFIX}.renderOp`;

    function getSelectedRenderOp() {
        const value = document.querySelector('input[name="render-mode"]:checked')?.value ?? '';
        return VALID_RENDER_OPS.has(value) ? value : OPS.renderFull;
    }

    function applyRenderOp(op) {
        const runButton = $('btn-run-render');
        if (runButton) runButton.dataset.op = op;
    }

    function initRenderMode() {
        const saved = localStorage.getItem(renderOpStorageKey);
        const op = VALID_RENDER_OPS.has(saved) ? saved : OPS.renderFull;
        const radio = document.querySelector(`input[name="render-mode"][value="${op}"]`);
        if (radio) radio.checked = true;
        applyRenderOp(op);

        for (const input of document.querySelectorAll('input[name="render-mode"]')) {
            input.addEventListener('change', (event) => {
                if (!event.currentTarget.checked) return;
                const selected = event.currentTarget.value;
                localStorage.setItem(renderOpStorageKey, selected);
                applyRenderOp(selected);
                const label = selected === OPS.renderSimple ? 'Simple Mode' : 'Full Pipeline (SubPlan)';
                setStatus('render-mode-status', `✓ Render mode: ${label}`, 'success');
            });
        }
    }

    /* ── Session links & badges ────────────────────────────────── */

    function setSessionLinks(containerId, target, info) {
        if (!containerId || !$(containerId)) return;
        try {
            updateSessionLinks(target, info, getProxyUrl, containerId);
        } catch (e) {
            console.warn('[setSessionLinks] failed:', e);
        }
    }

    function badgeStateFor(status) {
        if (status === 'RUNNING') return 'running';
        if (status === 'COMPLETED') return 'done';
        if (status === 'ERROR' || status === 'FAILED') return 'error';
        return 'pending';
    }

    function handleTaskUpdate(target, info) {
        const key = normalizeTarget(target);
        if (key === 'code') {
            if (activeCodeTaskSessionId && info.sessionId && info.sessionId !== activeCodeTaskSessionId) return;
            setBadge(activeCodeBadge, badgeStateFor(info.status));
            setSessionLinks('session-links-render', TARGETS.code, info);
            setSessionLinks('session-links-results', TARGETS.code, info);
        } else if (key === 'code/test.md') {
            setBadge('badge-test', badgeStateFor(info.status));
            setSessionLinks('session-links-test', TARGETS.testDoc, info);
        } else if (key === 'notes.md') {
            setSessionLinks('session-links-review', TARGETS.notes, info);
        }
    }

    /* ── Pipeline diagram ──────────────────────────────────────── */

    function findTask(tasks, target) {
        const key = normalizeTarget(target);
        return tasks[target] ?? tasks[key] ?? tasks[`${key}/`] ?? null;
    }

    function setStage(stage, state, label, taskSessionId) {
        const el = document.querySelector(`.pipeline-stage[data-stage="${stage}"]`);
        if (!el) return;
        el.classList.remove('running', 'done');
        if (state !== 'pending') el.classList.add(state);
        const statusEl = el.querySelector('.stage-status');
        if (!statusEl) return;
        if (state === 'running' && taskSessionId) {
            statusEl.replaceChildren(Object.assign(document.createElement('a'), {
                href: getProxyUrl(taskSessionId),
                target: '_blank',
                rel: 'noopener',
                className: 'monitor-link',
                textContent: label,
            }));
        } else {
            statusEl.textContent = label;
        }
    }

    function updatePipelineDiagram(tasks) {
        const codeTask = findTask(tasks, TARGETS.code);
        if (!codeTask) return;
        if (codeTask.status === 'RUNNING') {
            setStage('render', 'running', 'Running…', codeTask.sessionId);
            setStage('output', 'pending', 'Pending');
        } else if (codeTask.status === 'COMPLETED') {
            setStage('render', 'done', 'Done');
            setStage('output', 'done', 'Done');
        } else if (codeTask.status === 'ERROR' || codeTask.status === 'FAILED') {
            setStage('render', 'pending', 'Failed');
            setStage('output', 'pending', 'Pending');
        }
    }

    /* ── Status polling (stopped when idle) ────────────────────── */

    function startPolling() {
        if (poller) return;
        poller = createStatusPoller(basePath, (target, info) => {
            handleTaskUpdate(target, info);
        }, 3000);
        poller.start();
        refreshDiagram();
        diagramTimer = setInterval(refreshDiagram, 3000);
    }

    function stopPolling() {
        poller?.stop();
        poller = null;
        if (diagramTimer) {
            clearInterval(diagramTimer);
            diagramTimer = null;
        }
    }

    async function refreshDiagram() {
        const status = await fetchDocopsStatus(basePath).catch((e) => {
            console.warn('[refreshDiagram] status fetch failed:', e);
            return null;
        });
        const tasks = status?.tasks ?? {};
        updatePipelineDiagram(tasks);
        const anyRunning = Object.values(tasks).some((t) => t.status === 'RUNNING');
        if (!anyRunning && inFlight === 0) stopPolling();
    }

    /* ── Generic operation runner ──────────────────────────────── */

    async function runOperation({ button, op, output, badgeId, linksId, onComplete }) {
        button.disabled = true;
        setBadge(badgeId, 'running');
        inFlight += 1;
        startPolling();
        try {
            const rawId = await runDocOp(sessionId, op, output, getSelectedModels());
            const taskId = (rawId ?? '').trim();
            if (TASK_ID_PATTERN.test(taskId)) {
                if (normalizeTarget(output) === 'code') activeCodeTaskSessionId = taskId;
                setSessionLinks(linksId, output, { status: 'RUNNING', sessionId: taskId });
                logBatchHtml(
                    `Session started: <a class="monitor-link" href="${escapeHtml(getProxyUrl(taskId))}" ` +
                    `target="_blank" rel="noopener">📡 Monitor live session (${escapeHtml(taskId)})</a>`,
                    'info',
                );
            }
            await waitForTask(basePath, output, undefined, handleTaskUpdate);
            setBadge(badgeId, 'done');
            await onComplete?.();
            return true;
        } catch (e) {
            console.error('[runOperation] failed:', e);
            setBadge(badgeId, 'error');
            showToast(`Operation failed: ${e.message}`, 'error');
            logBatch(`Operation failed: ${e.message}`, 'error');
            return false;
        } finally {
            inFlight -= 1;
            button.disabled = false;
        }
    }

    /* ── Editors: load, save, auto-save ────────────────────────── */

    function debounce(fn, delay = 800) {
        let handle = null;
        return (...args) => {
            clearTimeout(handle);
            handle = setTimeout(() => fn(...args), delay);
        };
    }

    async function loadEditors() {
        for (const [path, id] of [[TARGETS.idea, 'idea-editor'], [TARGETS.notes, 'notes-editor']]) {
            try {
                const content = await readFile(basePath, path);
                if (content !== null && $(id)) $(id).value = content;
            } catch (e) {
                console.warn(`[loadEditors] could not load ${path}:`, e);
            }
        }
    }

    async function saveEditor(path, editorId, statusId) {
        const content = $(editorId)?.value ?? '';
        await writeFile(basePath, path, content);
        if (statusId) setStatus(statusId, '✓ Saved', 'success');
    }

    $('save-idea')?.addEventListener('click', async (event) => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
            await saveEditor(TARGETS.idea, 'idea-editor', 'idea-status');
        } catch (e) {
            console.error('[save-idea]', e);
            setStatus('idea-status', `✗ ${e.message}`, 'error');
        } finally {
            button.disabled = false;
        }
    });

    $('save-notes')?.addEventListener('click', async (event) => {
        const button = event.currentTarget;
        button.disabled = true;
        try {
            await saveEditor(TARGETS.notes, 'notes-editor', 'notes-status');
        } catch (e) {
            console.error('[save-notes]', e);
            setStatus('notes-status', `✗ ${e.message}`, 'error');
        } finally {
            button.disabled = false;
        }
    });

    const autoSaveIdea = debounce(async () => {
        try {
            await saveEditor(TARGETS.idea, 'idea-editor', 'idea-status');
        } catch (e) { console.warn('[autoSaveIdea]', e); }
    });
    const autoSaveNotes = debounce(async () => {
        try {
            await saveEditor(TARGETS.notes, 'notes-editor', 'notes-status');
        } catch (e) { console.warn('[autoSaveNotes]', e); }
    });
    $('idea-editor')?.addEventListener('input', autoSaveIdea);
    $('notes-editor')?.addEventListener('input', autoSaveNotes);

    /* ── Console capture & live preview ────────────────────────── */

    const CONSOLE_CAPTURE_SCRIPT = `
    <script>
    (() => {
        const original = { log: console.log, warn: console.warn, error: console.error, info: console.info, debug: console.debug };
        const send = (level, args) => {
            try {
                const parts = Array.from(args, (arg) => {
                    try { return typeof arg === 'object' ? JSON.stringify(arg, null, 2) : String(arg); }
                    catch (e) { return String(arg); }
                });
                window.parent.postMessage({ type: 'console-capture', level, message: parts.join(' '), timestamp: Date.now() }, '*');
            } catch (e) { /* ignore */ }
        };
        console.log = (...a) => { send('log', a); original.log(...a); };
        console.warn = (...a) => { send('warn', a); original.warn(...a); };
        console.error = (...a) => { send('error', a); original.error(...a); };
        console.info = (...a) => { send('info', a); original.info(...a); };
        console.debug = (...a) => { send('log', a); original.debug(...a); };
        window.onerror = (msg, source, lineno, colno, error) => {
            let detail = msg;
            if (source) detail += '\\n  at ' + source + ':' + lineno + ':' + colno;
            if (error && error.stack) detail += '\\n' + error.stack;
            window.parent.postMessage({ type: 'console-capture', level: 'exception', message: detail, source, lineno, colno, timestamp: Date.now() }, '*');
        };
        window.addEventListener('unhandledrejection', (event) => {
            const reason = event.reason;
            let msg = 'Unhandled Promise Rejection: ';
            if (reason instanceof Error) msg += reason.message + (reason.stack ? '\\n' + reason.stack : '');
            else { try { msg += JSON.stringify(reason); } catch (e) { msg += String(reason); } }
            window.parent.postMessage({ type: 'console-capture', level: 'exception', message: msg, timestamp: Date.now() }, '*');
        });
        window.parent.postMessage({ type: 'console-capture', level: 'info', message: 'App loaded successfully.', timestamp: Date.now() }, '*');
    })();
    <\/script>`;

    function clearConsolePanel() {
        capturedLogs = [];
        consoleStat = { logs: 0, warnings: 0, errors: 0 };
        const output = $('console-output');
        if (output) {
            const entry = document.createElement('div');
            entry.className = 'console-entry console-info';
            entry.textContent = 'Waiting for app to load…';
            output.replaceChildren(entry);
        }
        updateConsoleCounts();
        $('btn-fix-errors')?.classList.remove('visible');
    }

    function updateConsoleCounts() {
        const counts = $('console-counts');
        if (!counts) return;
        const nodes = [];
        const add = (className, text) => {
            const span = document.createElement('span');
            span.className = className;
            span.textContent = text;
            nodes.push(span);
        };
        if (consoleStat.errors > 0) add('console-count-errors', `❌ ${consoleStat.errors} error${consoleStat.errors === 1 ? '' : 's'}`);
        if (consoleStat.warnings > 0) add('console-count-warnings', `⚠️ ${consoleStat.warnings} warning${consoleStat.warnings === 1 ? '' : 's'}`);
        add('console-count-logs', `📝 ${consoleStat.logs} log${consoleStat.logs === 1 ? '' : 's'}`);
        counts.replaceChildren(...nodes);
    }

    function addConsoleEntry(level, message, source = '') {
        const timestamp = new Date().toLocaleTimeString();
        capturedLogs.push({ level, message, source, timestamp });
        if (level === 'error' || level === 'exception') {
            consoleStat.errors += 1;
            $('btn-fix-errors')?.classList.add('visible');
        } else if (level === 'warn') {
            consoleStat.warnings += 1;
        } else {
            consoleStat.logs += 1;
        }
        updateConsoleCounts();

        const output = $('console-output');
        if (!output) return;
        const placeholder = output.querySelector('.console-info');
        if (placeholder?.textContent.includes('Waiting for app')) placeholder.remove();

        const cssClass = {
            exception: 'console-exception',
            error: 'console-error',
            warn: 'console-warn',
            info: 'console-info',
        }[level] ?? 'console-log';

        const entry = document.createElement('div');
        entry.className = `console-entry ${cssClass}`;
        const time = document.createElement('span');
        time.className = 'console-timestamp';
        time.textContent = timestamp;
        entry.append(time, document.createTextNode(message));
        if (source) {
            const src = document.createElement('span');
            src.className = 'console-source';
            src.textContent = source;
            entry.append(src);
        }
        output.append(entry);
        output.scrollTop = output.scrollHeight;
    }

    window.addEventListener('message', (event) => {
        if (event.data?.type !== 'console-capture') return;
        const { level, message, source, lineno, colno } = event.data;
        let info = source ?? '';
        if (info && lineno) info += `:${lineno}`;
        if (info && colno) info += `:${colno}`;
        addConsoleEntry(level ?? 'log', message ?? '', info);
    });

    function injectPreviewHead(html) {
        const head = `<base href="${codeBaseUrl}">\n${CONSOLE_CAPTURE_SCRIPT}`;
        const stripped = html.replace(/<base\s[^>]*>/gi, '');
        if (/<head[^>]*>/i.test(stripped)) return stripped.replace(/<head[^>]*>/i, (m) => m + head);
        if (/<html[^>]*>/i.test(stripped)) return stripped.replace(/<html[^>]*>/i, (m) => `${m}<head>${head}</head>`);
        return `<head>${head}</head>${stripped}`;
    }

    async function loadPreview() {
        const iframe = $('preview-iframe');
        const placeholder = $('preview-placeholder');
        if (!iframe) return;
        const available = await refreshAppAvailability();
        if (!available) {
            placeholder?.classList.remove('hidden');
            iframe.classList.remove('visible');
            return;
        }
        clearConsolePanel();
        try {
            const response = await fetch(`${appIndexUrl}?t=${Date.now()}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            placeholder?.classList.add('hidden');
            iframe.classList.add('visible');
            iframe.srcdoc = injectPreviewHead(await response.text());
        } catch (e) {
            console.warn('[loadPreview] falling back to direct load:', e);
            addConsoleEntry('error', `Failed to load preview: ${e.message}`);
            placeholder?.classList.add('hidden');
            iframe.classList.add('visible');
            iframe.removeAttribute('srcdoc');
            iframe.src = `${appIndexUrl}?t=${Date.now()}`;
        }
    }

    $('btn-preview-refresh')?.addEventListener('click', loadPreview);
    $('btn-preview-clear-console')?.addEventListener('click', clearConsolePanel);

    $('btn-fix-errors')?.addEventListener('click', async () => {
        const errors = capturedLogs.filter((e) => e.level === 'error' || e.level === 'exception');
        if (errors.length === 0) {
            showToast('No errors captured to fix.', 'info');
            return;
        }
        const warnings = capturedLogs.filter((e) => e.level === 'warn');
        const notes = [
            '# 🐛 Auto-detected Errors to Fix',
            '',
            'The following JavaScript errors/exceptions were captured from the running app.',
            'Please fix all of these issues:',
            '',
            ...errors.flatMap((err, i) => [
                `## Error ${i + 1}`,
                '```',
                err.message,
                '```',
                err.source ? `Source: \`${err.source}\`` : '',
                '',
            ]),
            ...(warnings.length > 0
                ? ['## Warnings (lower priority)', '', ...warnings.map((w) => `- ${w.message}`), '']
                : []),
            '## Instructions',
            '- Fix all the errors listed above',
            '- Make sure the app loads without any JavaScript exceptions',
            '- Test that all interactive features work correctly',
        ].join('\n');

        const editor = $('notes-editor');
        if (editor) editor.value = notes;
        try {
            await writeFile(basePath, TARGETS.notes, notes);
        } catch (e) {
            console.warn('[btn-fix-errors] could not save notes:', e);
        }
        activateSection('section-update');
        showToast('Errors copied into your update notes.', 'success');
    });

    /* ── Project file list ─────────────────────────────────────── */

    function emptyState(icon, title, description) {
        const wrapper = document.createElement('div');
        wrapper.className = 'empty-state';
        const iconEl = document.createElement('div');
        iconEl.className = 'empty-icon';
        iconEl.setAttribute('aria-hidden', 'true');
        iconEl.textContent = icon;
        const titleEl = document.createElement('p');
        titleEl.className = 'empty-title';
        titleEl.textContent = title;
        const descEl = document.createElement('p');
        descEl.className = 'empty-desc';
        descEl.textContent = description;
        wrapper.append(iconEl, titleEl, descEl);
        return wrapper;
    }

    async function refreshFileList() {
        const container = $('files-container');
        if (!container) return;
        try {
            const entries = (await listFiles(basePath, 'code')).filter((e) => e.type === 'file' || !e.type);
            if (entries.length === 0) {
                container.replaceChildren(emptyState('📁', 'No project files yet', 'Run the build from the Pipeline tab.'));
                return;
            }
            const fragment = document.createDocumentFragment();
            for (const entry of entries) {
                const section = document.createElement('div');
                section.className = 'result-file-section';

                const header = document.createElement('button');
                header.type = 'button';
                header.className = 'result-file-header';
                header.setAttribute('aria-expanded', 'false');
                const icon = document.createElement('span');
                icon.className = 'result-file-icon';
                icon.setAttribute('aria-hidden', 'true');
                icon.textContent = getFileIcon(entry.name);
                const name = document.createElement('span');
                name.className = 'result-file-name';
                name.textContent = entry.name;
                header.append(icon, name);

                const body = document.createElement('div');
                body.className = 'result-file-body';

                header.addEventListener('click', async () => {
                    const open = body.classList.toggle('visible');
                    header.setAttribute('aria-expanded', String(open));
                    if (!open || body.dataset.loaded === 'true') return;
                    body.dataset.loaded = 'true';
                    try {
                        const content = await readFile(basePath, `code/${entry.name}`);
                        if (!content) {
                            body.replaceChildren(emptyState('📄', 'Empty file', 'This file has no content.'));
                        } else if (entry.name.endsWith('.md')) {
                            body.innerHTML = renderMarkdown(content);
                        } else {
                            const pre = document.createElement('pre');
                            const code = document.createElement('code');
                            code.textContent = content;
                            pre.append(code);
                            body.replaceChildren(pre);
                        }
                    } catch (e) {
                        console.warn('[refreshFileList] read failed:', e);
                        body.replaceChildren(emptyState('⚠️', 'Could not load file', e.message));
                    }
                });

                section.append(header, body);
                fragment.append(section);
            }
            container.replaceChildren(fragment);
        } catch (e) {
            console.error('[refreshFileList]', e);
            container.replaceChildren(emptyState('⚠️', 'Could not list files', e.message));
        }
    }

    $('btn-refresh-files')?.addEventListener('click', refreshFileList);

    async function refreshReadme() {
        const viewer = $('result-readme');
        if (!viewer) return;
        try {
            const content = await readFile(basePath, TARGETS.codeReadme);
            if (content) viewer.innerHTML = renderMarkdown(content);
            else viewer.replaceChildren(emptyState('📄', 'No README generated yet', 'Run the build from the Pipeline tab.'));
        } catch (e) {
            console.warn('[refreshReadme]', e);
            viewer.replaceChildren(emptyState('⚠️', 'Could not load README', e.message));
        }
    }

    $('btn-refresh-readme')?.addEventListener('click', refreshReadme);

    /* ── Git auto-commit (silent; no Git UI — menubar owns that) ─ */

    async function autoCommit(message) {
        try {
            let status = await gitGetStatus(basePath);
            if (!status.initialized) {
                await gitInit(basePath);
                logBatch('📌 Auto-initialised Git repository', 'info');
                status = await gitGetStatus(basePath);
            }
            if (status.initialized && !status.clean) {
                await gitCommit(basePath, message);
                logBatch(`📌 Auto-committed changes: ${message}`, 'success');
            }
        } catch (e) {
            console.warn('[autoCommit] failed:', e);
        }
    }

    const shortTime = () => new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }).toLowerCase();

    /* ── Completion handlers ───────────────────────────────────── */

    async function showViewerReadme(viewerId) {
        const viewer = $(viewerId);
        if (!viewer) return;
        try {
            const content = await readFile(basePath, TARGETS.codeReadme);
            if (content) {
                viewer.innerHTML = renderMarkdown(content);
                viewer.classList.add('visible');
            }
        } catch (e) {
            console.warn('[showViewerReadme]', e);
        }
    }

    async function afterBuild() {
        await showViewerReadme('viewer-render');
        await refreshAppAvailability();
        await loadPreview();
        await refreshFileList();
        await refreshReadme();
        await autoCommit(`Build at ${shortTime()}`);
        logBatch('🎉 Pipeline complete — check the Results tab.', 'success');
    }

    async function afterUpdate() {
        await showViewerReadme('viewer-update');
        await refreshAppAvailability();
        await loadPreview();
        await refreshFileList();
        await refreshReadme();
        await autoCommit(`Update at ${shortTime()}`);
        showToast('Update applied.', 'success');
    }

    async function afterTest() {
        await loadTestArtifacts();
        setStatus('test-status', '✓ Test complete — artifacts loaded', 'success');
    }

    async function afterReview() {
        setStatus('review-status', '✓ Review complete — findings written to notes.md', 'success');
        try {
            const notes = await readFile(basePath, TARGETS.notes);
            if (notes !== null) {
                const viewer = $('viewer-review');
                if (viewer) {
                    viewer.innerHTML = renderMarkdown(notes);
                    viewer.classList.add('visible');
                }
                const editor = $('notes-editor');
                if (editor) editor.value = notes;
            }
        } catch (e) {
            console.warn('[afterReview] could not load notes:', e);
        }
    }

    const COMPLETION_HANDLERS = new Map([
        ['btn-run-render', afterBuild],
        ['btn-run-update', afterUpdate],
        ['run-test', afterTest],
        ['run-review', afterReview],
    ]);

    /* ── Pre-run validation ────────────────────────────────────── */

    async function ensureIdeaSaved() {
        const idea = $('idea-editor')?.value.trim() ?? '';
        if (!idea) {
            showToast('Please describe your webapp idea first.', 'error');
            activateSection('section-input');
            $('idea-editor')?.focus();
            return false;
        }
        try {
            await writeFile(basePath, TARGETS.idea, $('idea-editor').value);
        } catch (e) {
            console.warn('[ensureIdeaSaved] could not save idea:', e);
        }
        return true;
    }

    async function ensureNotesSaved() {
        const notes = $('notes-editor')?.value.trim() ?? '';
        if (!notes) {
            showToast('Write some update notes describing what to change.', 'error');
            $('notes-editor')?.focus();
            return false;
        }
        try {
            await writeFile(basePath, TARGETS.notes, $('notes-editor').value);
        } catch (e) {
            console.warn('[ensureNotesSaved] could not save notes:', e);
        }
        return true;
    }

    async function validateFor(buttonId) {
        if (buttonId === 'btn-run-render') return ensureIdeaSaved();
        if (buttonId === 'btn-run-update') return ensureNotesSaved();
        if (buttonId === 'run-test' || buttonId === 'run-review') {
            if (await fileExists(basePath, TARGETS.codeIndex)) return true;
            showToast('No generated webapp found — build the project first.', 'error');
            return false;
        }
        return true;
    }

    /* ── Run buttons ───────────────────────────────────────────── */

    for (const button of document.querySelectorAll('.btn-run')) {
        button.addEventListener('click', async () => {
            const { op, output, badge, links } = button.dataset;
            if (!(await validateFor(button.id))) return;
            if (normalizeTarget(output) === 'code') {
                activeCodeBadge = badge;
                activeCodeTaskSessionId = null;
            }
            if (button.id === 'run-test') setStatus('test-status', 'Test running…', '');
            if (button.id === 'run-review') setStatus('review-status', 'Reviewing test results…', '');
            await runOperation({
                button,
                op,
                output,
                badgeId: badge,
                linksId: links,
                onComplete: COMPLETION_HANDLERS.get(button.id),
            });
        });
    }

    $('run-all')?.addEventListener('click', async (event) => {
        const button = event.currentTarget;
        if (!(await ensureIdeaSaved())) return;
        $('batch-log')?.replaceChildren();
        activeCodeBadge = 'badge-render';
        activeCodeTaskSessionId = null;
        const op = getSelectedRenderOp();
        logBatch(`Render mode: ${op === OPS.renderSimple ? 'Simple Mode' : 'Full Pipeline (SubPlan)'} (${op})`, 'info');
        logBatch('Starting: Build Project', 'info');
        await runOperation({
            button,
            op,
            output: TARGETS.code,
            badgeId: 'badge-render',
            linksId: 'session-links-render',
            onComplete: afterBuild,
        });
    });

    /* ── Test artifacts ────────────────────────────────────────── */

    async function listTestArtifacts() {
        try {
            const entries = await listFiles(basePath, 'code');
            return entries.filter((e) => (e.type === 'file' || !e.type) && /^test\./i.test(e.name));
        } catch (e) {
            console.warn('[listTestArtifacts]', e);
            return [];
        }
    }

    function renderPre(container, content, mono = true) {
        const pre = document.createElement('pre');
        if (mono) pre.className = 'code-block';
        pre.textContent = content;
        const scroll = document.createElement('div');
        scroll.className = 'table-scroll';
        scroll.append(pre);
        container.replaceChildren(scroll);
    }

    async function loadTestArtifacts() {
        const entries = await listTestArtifacts();
        if (entries.length === 0) return;

        const screenshot = entries.find((e) => /^test\.png$/i.test(e.name));
        const consoleLog = entries.find((e) => /^test\.console\.log$/i.test(e.name));
        const networkLog = entries.find((e) => /^test\.network\.log$/i.test(e.name));
        const htmlFile = entries.find((e) => /^test\.html?$/i.test(e.name));

        const screenshotContainer = $('test-screenshot-container');
        if (screenshotContainer) {
            if (screenshot) {
                const url = `${withBase(`code/${screenshot.name}`)}?t=${Date.now()}`;
                screenshotContainer.innerHTML =
                    `<a class="screenshot-link" href="${escapeHtml(url)}" target="_blank" rel="noopener">` +
                    `<img class="screenshot-img" src="${escapeHtml(url)}" alt="Screenshot of the rendered page"></a>` +
                    `<p class="hint hint-sm">📁 <code>${escapeHtml(screenshot.name)}</code></p>`;
            } else {
                screenshotContainer.replaceChildren(
                    emptyState('📸', 'No screenshot found', 'Run the test to capture one.'),
                );
            }
        }

        const consoleEl = $('test-console-output');
        if (consoleEl) {
            if (consoleLog) {
                try {
                    const content = await readFile(basePath, `code/${consoleLog.name}`);
                    const lines = (content ?? '').split('\n').filter((line) => line.trim());
                    if (lines.length === 0) {
                        const entry = document.createElement('div');
                        entry.className = 'console-entry console-info';
                        entry.textContent = 'Console log was empty.';
                        consoleEl.replaceChildren(entry);
                    } else {
                        const fragment = document.createDocumentFragment();
                        for (const line of lines) {
                            const lower = line.toLowerCase();
                            let cssClass = 'console-log';
                            if (/\b(error|exception|severe)\b/.test(lower)) cssClass = 'console-error';
                            else if (/\b(warn|warning)\b/.test(lower)) cssClass = 'console-warn';
                            else if (/\b(info|debug)\b/.test(lower)) cssClass = 'console-info';
                            const entry = document.createElement('div');
                            entry.className = `console-entry ${cssClass}`;
                            entry.textContent = line;
                            fragment.append(entry);
                        }
                        consoleEl.replaceChildren(fragment);
                    }
                } catch (e) {
                    console.warn('[loadTestArtifacts] console log:', e);
                    const entry = document.createElement('div');
                    entry.className = 'console-entry console-error';
                    entry.textContent = `Failed to load console log: ${e.message}`;
                    consoleEl.replaceChildren(entry);
                }
            } else {
                const entry = document.createElement('div');
                entry.className = 'console-entry console-info';
                entry.textContent = 'No console log captured.';
                consoleEl.replaceChildren(entry);
            }
        }

        const networkContainer = $('test-network-container');
        if (networkContainer) {
            if (networkLog) {
                try {
                    const content = await readFile(basePath, `code/${networkLog.name}`);
                    if (content?.trim()) renderPre(networkContainer, content);
                    else networkContainer.replaceChildren(emptyState('🌐', 'Network log empty', 'No requests were recorded.'));
                } catch (e) {
                    networkContainer.replaceChildren(emptyState('⚠️', 'Could not load network log', e.message));
                }
            } else {
                networkContainer.replaceChildren(emptyState('🌐', 'No network log yet', 'Run the test to capture requests.'));
            }
        }

        const htmlContainer = $('test-html-container');
        if (htmlContainer) {
            if (htmlFile) {
                try {
                    const content = await readFile(basePath, `code/${htmlFile.name}`);
                    if (content?.trim()) renderPre(htmlContainer, content);
                    else htmlContainer.replaceChildren(emptyState('📄', 'Rendered HTML empty', 'Nothing was captured.'));
                } catch (e) {
                    htmlContainer.replaceChildren(emptyState('⚠️', 'Could not load HTML', e.message));
                }
            } else {
                htmlContainer.replaceChildren(emptyState('📄', 'No rendered HTML yet', 'Run the test to capture the DOM.'));
            }
        }
    }

    $('btn-refresh-test-artifacts')?.addEventListener('click', async (event) => {
        const button = event.currentTarget;
        button.disabled = true;
        setStatus('test-status', 'Loading artifacts…', '');
        try {
            await loadTestArtifacts();
            setStatus('test-status', '✓ Artifacts refreshed', 'success');
        } catch (e) {
            console.error('[btn-refresh-test-artifacts]', e);
            setStatus('test-status', `✗ ${e.message}`, 'error');
        } finally {
            button.disabled = false;
        }
    });

    /* ── Restore state on load ─────────────────────────────────── */

    async function restoreState() {
        const status = await fetchDocopsStatus(basePath).catch(() => null);
        const tasks = status?.tasks ?? {};
        let anyRunning = false;
        for (const [target, info] of Object.entries(tasks)) {
            if (info.status === 'RUNNING') {
                anyRunning = true;
                if (normalizeTarget(target) === 'code') activeCodeTaskSessionId = info.sessionId ?? null;
            }
            handleTaskUpdate(target, info);
        }
        updatePipelineDiagram(tasks);

        if ((await readFile(basePath, TARGETS.idea).catch(() => null))?.trim()) {
            setStage('input', 'done', 'Done');
        }
        if (await fileExists(basePath, TARGETS.codeIndex)) {
            if (!anyRunning) {
                setBadge('badge-render', 'done');
                setStage('render', 'done', 'Done');
                setStage('output', 'done', 'Done');
            }
            await refreshFileList();
            await refreshReadme();
        }
        if (anyRunning) startPolling();
    }

    /* ── Boot ──────────────────────────────────────────────────── */

    window.addEventListener('error', (e) => console.error('[uncaught]', e.error ?? e.message));
    window.addEventListener('unhandledrejection', (e) => console.error('[rejection]', e.reason));

    initMenu({ appName: '🏗️ Webapp Builder' });
    initRenderMode();
    await loadEditors();
    await refreshAppAvailability();
    await restoreState();
    await initApiProviders();