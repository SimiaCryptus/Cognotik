import { parseSessionUrl, getProxyUrl, getAppRoot } from './utils/session.js';
import {
    loadApiProviders,
    populateModelDropdowns,
    saveModelSelections,
    loadModelSelections
} from './utils/models.js';
import {
    readFile,
    writeFile,
    fileExists,
    listFiles
} from './utils/fileIO.js';
import {
    runDocOp,
    waitForTask,
    createStatusPoller
} from './utils/docops.js';
import {
    renderMarkdown,
    setStatus,
    setBadge,
    showToast,
    createBatchLogger,
    getFileIcon,
    escapeHtml
} from './utils/ui.js';

(async function main() {
    'use strict';

    // ---------- Session bootstrap ----------
    const { basePath, sessionId } = parseSessionUrl();
    if (!sessionId) {
        document.body.innerHTML = '<h1 style="padding:2rem">⚠️ Could not determine session from URL.</h1>';
        return;
    }

    const APP_PREFIX = 'quizWhiz';
    const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
    const logger = createBatchLogger('batch-log');

    // ---------- Surface unhandled errors ----------
    window.addEventListener('error', e => logger.log(`Uncaught: ${e.message}`, 'error'));
    window.addEventListener('unhandledrejection', e => logger.log(`Promise rejection: ${e.reason}`, 'error'));

    logger.log(`Session ID: ${sessionId}`);
    logger.log(`Base path: ${basePath}`);

    // ---------- Step / target configuration ----------
    // Targets are folder paths (or file paths) the docops will write to.
    const STEPS = {
        design: {
            op: 'ops/design_op.md',
            target: 'design',
            badge: 'badge-design',
            linksContainer: 'design-links',
            model: 'smart'
        },
        gamedata: {
            op: 'ops/gamedata_op.md',
            target: 'code/gamedata',
            badge: 'badge-gamedata',
            linksContainer: 'gamedata-links',
            model: 'fast'
        },
        impl: {
            op: 'ops/impl_op.md',
            target: 'code/',
            badge: 'badge-impl',
            linksContainer: 'impl-links',
            model: 'smart'
        },
        test: {
            op: 'ops/test_op.md',
            target: 'code/test.md',
            badge: 'badge-test',
            linksContainer: 'test-links',
            model: 'fast'
        },
        review: {
            op: 'ops/review_op.md',
            target: 'notes.md',
            badge: 'badge-review',
            linksContainer: 'review-links',
            model: 'smart'
        },
        update: {
            op: 'ops/update_op.md',
            target: 'code',
            badge: 'badge-update',
            linksContainer: 'update-links',
            model: 'smart'
        }
    };

    // Persistent map of every task we've ever seen, keyed by target.
    const sessionTracker = new Map();

    // ---------- Model dropdowns ----------
    const smartSelect = document.getElementById('smart-model');
    const fastSelect = document.getElementById('fast-model');
    const imageSelect = document.getElementById('image-model');

    try {
        const availableModels = await loadApiProviders();
        const saved = loadModelSelections(APP_PREFIX, MODEL_KEYS);
        const selects = { smartModel: smartSelect, fastModel: fastSelect, imageModel: imageSelect };
        const arr = MODEL_KEYS.map(k => selects[k]);
        populateModelDropdowns(availableModels, arr, saved);
        logger.log('Loaded AI providers.', 'success');
    } catch (e) {
        logger.log(`Failed to load AI providers: ${e.message}`, 'error');
    }

    function persistModels() {
        saveModelSelections(APP_PREFIX, {
            smartModel: smartSelect.value,
            fastModel: fastSelect.value,
            imageModel: imageSelect.value
        });
    }
    smartSelect.addEventListener('change', persistModels);
    fastSelect.addEventListener('change', persistModels);
    imageSelect.addEventListener('change', persistModels);

    function buildModelOverrides(role) {
        const overrides = {};
        const smart = smartSelect.value;
        const fast = fastSelect.value;
        const image = imageSelect.value;
        if (smart) overrides.smartModel = smart;
        if (fast) overrides.fastModel = fast;
        if (image) overrides.imageModel = image;
        // All keys included regardless of role; servlet picks based on op definition.
        return overrides;
    }

    // ---------- Idea editor ----------
    const ideaEditor = document.getElementById('idea-editor');
    const saveIdeaBtn = document.getElementById('save-idea');

    async function loadIdea() {
        const content = await readFile(basePath, 'idea.md');
        if (content != null) {
            ideaEditor.value = content;
            setBadge('badge-idea', 'done');
            logger.log('Loaded existing idea.md');
        }
    }

    saveIdeaBtn.addEventListener('click', async () => {
        const value = ideaEditor.value.trim();
        if (!value) {
            setStatus('idea-status', 'Please enter an idea before saving.', 'warning');
            return;
        }
        try {
            await writeFile(basePath, 'idea.md', value);
            setStatus('idea-status', 'Saved ✔', 'success');
            setBadge('badge-idea', 'done');
            logger.log('Saved idea.md', 'success');
        } catch (e) {
            setStatus('idea-status', `Save failed: ${e.message}`, 'error');
            logger.log(`Failed to save idea.md: ${e.message}`, 'error');
        }
    });

    // ---------- Notes editor ----------
    const notesEditor = document.getElementById('notes-editor');
    const saveNotesBtn = document.getElementById('save-notes');

    async function loadNotes() {
        const content = await readFile(basePath, 'notes.md');
        if (content != null) notesEditor.value = content;
    }

    saveNotesBtn.addEventListener('click', async () => {
        try {
            await writeFile(basePath, 'notes.md', notesEditor.value);
            setStatus('notes-status', 'Saved ✔', 'success');
            logger.log('Saved notes.md', 'success');
        } catch (e) {
            setStatus('notes-status', `Save failed: ${e.message}`, 'error');
        }
    });

    // ---------- Session tracking & rendering ----------
    function trackSession(target, taskInfo) {
        if (!taskInfo) return;
        // Merge with any existing entry to keep latest data.
        const existing = sessionTracker.get(target) || {};
        sessionTracker.set(target, { ...existing, ...taskInfo });
        renderActiveSessionsPanel();
        renderTargetLinks(target, taskInfo);
    }

    function renderTargetLinks(target, taskInfo) {
        // Map target path -> container id
        const containerByTarget = {
            'design': 'design-links',
            'code/gamedata': 'gamedata-links',
            'code': 'impl-links', // also reused for update + test
            'notes.md': 'review-links'
        };
        const containerId = containerByTarget[target];
        if (!containerId) return;
        const container = document.getElementById(containerId);
        if (!container) return;

        const taskId = taskInfo.sessionId || taskInfo.taskId || taskInfo.id;
        if (!taskId) return;

        // Avoid duplicates: re-render entire container with all known sessions for this target.
        // For 'code/' impl + update + test may run; show all.
        const allForTarget = [];
        for (const [t, info] of sessionTracker.entries()) {
            if (t === target) allForTarget.push(info);
        }

        container.innerHTML = '';
        allForTarget.forEach((info, idx) => {
            const id = info.sessionId || info.taskId || info.id;
            if (!id) return;
            const status = (info.status || 'unknown').toUpperCase();
            const a = document.createElement('a');
            a.href = getProxyUrl(id);
            a.target = '_blank';
            a.textContent = `🔗 ${target} (${status})`;
            container.appendChild(a);
        });
    }
    // For containers that may host multiple targets (e.g. impl-links shows impl/test/update),
    // we also render here so all of them appear together.
    function renderImplCombined() {
        const container = document.getElementById('impl-links');
        if (!container) return;
        container.innerHTML = '';
        for (const [t, info] of sessionTracker.entries()) {
            if (t !== 'code') continue;
            const id = info.sessionId || info.taskId || info.id;
            if (!id) continue;
            const status = (info.status || 'unknown').toUpperCase();
            const a = document.createElement('a');
            a.href = getProxyUrl(id);
            a.target = '_blank';
            a.textContent = `🔗 ${t} (${status})`;
            container.appendChild(a);
        }
    }

    function renderActiveSessionsPanel() {
        const panel = document.getElementById('active-sessions');
        if (!panel) return;
        if (sessionTracker.size === 0) {
            panel.innerHTML = '<p class="muted-small">No sessions started yet.</p>';
            return;
        }
        const rows = [];
        for (const [target, info] of sessionTracker.entries()) {
            const id = info.sessionId || info.taskId || info.id || '';
            const status = (info.status || 'unknown').toUpperCase();
            const statusClass = status === 'COMPLETED' ? 'status-success' :
                (status === 'ERROR' || status === 'FAILED') ? 'status-error' :
                    status === 'RUNNING' ? 'status-info' : 'status-warning';
            const proxyUrl = id ? getProxyUrl(id) : '#';
            rows.push(`
                    <div class="session-row">
                        <span class="target-name">${escapeHtml(target)}</span>
                        <span class="session-status ${statusClass}">${escapeHtml(status)}</span>
                        <span class="links">
                            ${id ? `<a href="${proxyUrl}" target="_blank">Monitor</a>` : ''}
                        </span>
                    </div>
                `);
        }
        panel.innerHTML = rows.join('');
    }

    // ---------- Polling existing tasks ----------
    const poller = createStatusPoller(basePath, (target, taskInfo) => {
        trackSession(target, taskInfo);
        updateBadgeFromTaskInfo(target, taskInfo);
    });
    poller.start();

    function updateBadgeFromTaskInfo(target, taskInfo) {
        const badgeMap = {
            'design': 'badge-design',
            'code/gamedata': 'badge-gamedata',
            // 'code/' is shared by impl/test/update — don't auto-update from poller.
            'notes.md': 'badge-review'
        };
        const badgeId = badgeMap[target];
        if (!badgeId) return;
        const status = (taskInfo.status || '').toUpperCase();
        if (status === 'COMPLETED') setBadge(badgeId, 'done');
        else if (status === 'ERROR' || status === 'FAILED') setBadge(badgeId, 'error');
        else if (status === 'RUNNING') setBadge(badgeId, 'running');
    }

    // ---------- File listing helpers ----------
    async function refreshDesignFiles() {
        const files = await listFiles(basePath, 'design');
        const btn = document.getElementById('view-design');
        const preview = document.getElementById('design-preview');
        if (files && files.length) {
            btn.disabled = false;
            // Try to preview game_flow.md if present.
            const flowEntry = files.find(f => /game_flow\.md$/i.test(f.name));
            if (flowEntry) {
                const content = await readFile(basePath, `design/${flowEntry.name}`);
                if (content) {
                    preview.classList.remove('hidden');
                    preview.innerHTML = renderMarkdown(content);
                }
            }
        }
    }

    async function refreshGamedataFiles() {
        const files = await listFiles(basePath, 'code/gamedata');
        const btn = document.getElementById('view-gamedata');
        const list = document.getElementById('gamedata-list');
        if (!files) { list.innerHTML = ''; return; }
        const realFiles = files.filter(f => f.type === 'file' && !f.name.startsWith('_'));
        if (realFiles.length) {
            btn.disabled = false;
            list.innerHTML = realFiles.map(f =>
                `<div class="file-row">
                       <a href="${basePath}/code/gamedata/${encodeURIComponent(f.name)}" target="_blank">${getFileIcon(f.name)} ${escapeHtml(f.name)}</a>
                        <span class="file-size">${(f.size || 0).toLocaleString()} B</span>
                    </div>`
            ).join('');
        } else {
            list.innerHTML = '';
        }
    }

    async function refreshCodeFiles() {
        const files = await listFiles(basePath, 'code');
        const btn = document.getElementById('view-code');
        const list = document.getElementById('code-list');
        const openGame = document.getElementById('open-game');
        const testBtn = document.getElementById('view-test');
        const testList = document.getElementById('test-list');
        if (!files) { list.innerHTML = ''; return; }
        // Exclude the gamedata sub-folder which is now nested under code/.
        const allFiles = files.filter(f =>
            f.type === 'file' &&
            !f.name.startsWith('_') &&
            f.name !== 'gamedata'
        );
        // Separate test artifacts (test.*) from regular code files.
        const testFiles = allFiles.filter(f => /^test\b/i.test(f.name) || /^README\.md$/i.test(f.name) && false);
        const realFiles = allFiles.filter(f => !/^test\b/i.test(f.name));
        // Always check for index.html and toggle the Open Game link,
        // regardless of whether other code files exist.
        const idx = realFiles.find(f => /^index\.html?$/i.test(f.name));
        if (idx) {
            openGame.href = `${basePath}/code/${encodeURIComponent(idx.name)}`;
            openGame.classList.remove('disabled-link');
            openGame.removeAttribute('aria-disabled');
            // Mark implementation step as done if we have a working index.html.
            setBadge('badge-impl', 'done');
        } else {
            openGame.href = '#';
            openGame.classList.add('disabled-link');
            openGame.setAttribute('aria-disabled', 'true');
        }


        if (realFiles.length) {
            btn.disabled = false;
            list.innerHTML = realFiles.map(f =>
                `<div class="file-row">
                        <a href="${basePath}/code/${encodeURIComponent(f.name)}" target="_blank">${getFileIcon(f.name)} ${escapeHtml(f.name)}</a>
                        <span class="file-size">${(f.size || 0).toLocaleString()} B</span>
                    </div>`
            ).join('');

        } else {
            list.innerHTML = '';
        }
        if (testList) {
            if (testFiles.length) {
                testBtn.disabled = false;
                testList.innerHTML = testFiles.map(f =>
                    `<div class="file-row">
                            <a href="${basePath}/code/${encodeURIComponent(f.name)}" target="_blank">${getFileIcon(f.name)} ${escapeHtml(f.name)}</a>
                            <span class="file-size">${(f.size || 0).toLocaleString()} B</span>
                        </div>`
                ).join('');
                setBadge('badge-test', 'done');
            } else {
                testList.innerHTML = '';
            }
        }
    }

    // ---------- View buttons ----------
    document.getElementById('view-design').addEventListener('click', () => {
        window.open(`${basePath}/design/`, '_blank');
    });
    document.getElementById('view-gamedata').addEventListener('click', () => {
        window.open(`${basePath}/code/gamedata/`, '_blank');
    });
    document.getElementById('view-code').addEventListener('click', () => {
        window.open(`${basePath}/code/`, '_blank');
    });
    document.getElementById('view-test').addEventListener('click', () => {
        window.open(`${basePath}/code/`, '_blank');
    });

    // ---------- Run a step ----------
    async function runStep(stepKey, opts = {}) {
        const step = STEPS[stepKey];
        if (!step) return;

        // Pre-flight: ensure idea.md exists for first three steps.
        if (['design', 'gamedata', 'impl'].includes(stepKey)) {
            if (!(await fileExists(basePath, 'idea.md'))) {
                showToast('Please save an Idea first.', 'warning');
                logger.log('Aborted: idea.md missing.', 'error');
                return;
            }
        }
        if (stepKey === 'test') {
            if (!(await fileExists(basePath, 'code/index.html'))) {
                showToast('Build the code first (no index.html found).', 'warning');
                logger.log('Aborted: code/index.html missing.', 'error');
                return;
            }
        }
        if (stepKey === 'review') {
            // Need at least one test artifact to review.
            const consoleLog = await fileExists(basePath, 'code/test.console.log');
            const networkLog = await fileExists(basePath, 'code/test.network.log');
            const testHtml = await fileExists(basePath, 'code/test.html');
            if (!consoleLog && !networkLog && !testHtml) {
                showToast('Run tests first — no test artifacts found.', 'warning');
                logger.log('Aborted: no test artifacts to review.', 'error');
                return;
            }
        }
        if (stepKey === 'update') {
            if (!(await fileExists(basePath, 'notes.md'))) {
                showToast('Please save update notes first.', 'warning');
                return;
            }
        }

        setBadge(step.badge, 'running');
        logger.log(`▶ Running ${stepKey} (${step.op} → ${step.target})`);

        try {
            const overrides = buildModelOverrides(step.model);
            const taskId = await runDocOp(sessionId, step.op, step.target, overrides);
            logger.log(`Started task ${taskId}. Monitor: ${getProxyUrl(taskId)}`, 'info');

            // Pre-register so the link appears immediately even before status poll catches up.
            trackSession(step.target, { sessionId: taskId, status: 'RUNNING' });

            await waitForTask(basePath, step.target, 600000, (t, info) => {
                trackSession(t, info);
                updateBadgeFromTaskInfo(t, info);
            });

            setBadge(step.badge, 'done');
            logger.log(`✔ ${stepKey} completed.`, 'success');
            showToast(`${stepKey} completed`, 'success');

            // Refresh artifacts after the step.
            if (stepKey === 'design') await refreshDesignFiles();
            if (stepKey === 'gamedata') await refreshGamedataFiles();
            if (stepKey === 'impl' ||
                stepKey === 'test' ||
                stepKey === 'update') await refreshCodeFiles();
            if (stepKey === 'review') await loadNotes();

        } catch (e) {
            setBadge(step.badge, 'error');
            logger.log(`✖ ${stepKey} failed: ${e.message}`, 'error');
            showToast(`${stepKey} failed`, 'error');
        }
    }

    document.getElementById('run-design').addEventListener('click', () => runStep('design'));
    document.getElementById('run-gamedata').addEventListener('click', () => runStep('gamedata'));
    document.getElementById('run-impl').addEventListener('click', () => runStep('impl'));
    document.getElementById('run-test').addEventListener('click', () => runStep('test'));
    document.getElementById('run-review').addEventListener('click', () => runStep('review'));
    document.getElementById('run-update').addEventListener('click', () => runStep('update'));

    // ---------- Initial state hydration ----------
    await loadIdea();
    await loadNotes();
    await refreshDesignFiles().catch(e => logger.log(`Design refresh: ${e.message}`, 'warning'));
    await refreshGamedataFiles().catch(e => logger.log(`Gamedata refresh: ${e.message}`, 'warning'));
    await refreshCodeFiles().catch(e => logger.log(`Code refresh: ${e.message}`, 'warning'));

    // Periodic refresh so the UI stays in sync even after reloads.
    setInterval(() => {
        refreshDesignFiles().catch(() => { });
        refreshGamedataFiles().catch(() => { });
        refreshCodeFiles().catch(() => { });
    }, 15000);

    logger.log('Quiz Wiz UI ready.', 'success');
})();