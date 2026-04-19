import { parseSessionUrl, getProxyUrl } from './utils/session.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { readFile, writeFile, fileExists } from './utils/fileIO.js';
import { runDocOp, waitForTask, createStatusPoller } from './utils/docops.js';
import { renderMarkdown, setStatus, setBadge, showToast } from './utils/ui.js';
import { getStatus as gitGetStatus, initRepository, commit, formatStatus as gitFormatStatus } from './utils/git.js';
import { fetchUsageData, renderUsageSummary, createUsageTableHtml } from './utils/usage.js';

(async function () {
    'use strict';

    // ── Session & State ──────────────────────────────────────────────
    const { basePath, sessionId } = parseSessionUrl();
    document.getElementById('session-id-display').textContent = `Session: ${sessionId}`;

    const MODEL_PREFIX = 'texWizard';
    const trackedSessions = [];
    let lastPdfModified = null;  // Track PDF file's lastModified to avoid flicker

    // ── Models ───────────────────────────────────────────────────────
    const smartSelect = document.getElementById('smart-model-select');
    const fastSelect = document.getElementById('fast-model-select');
     const imageSelect = document.getElementById('image-model-select');

    try {
        const availableModels = await loadApiProviders();
         const saved = loadModelSelections(MODEL_PREFIX, ['smartModel', 'fastModel', 'imageModel']);
         populateModelDropdowns(availableModels, [smartSelect, fastSelect, imageSelect], saved);

        // Restore saved selections
        if (saved.smartModel) smartSelect.value = saved.smartModel;
        if (saved.fastModel) fastSelect.value = saved.fastModel;
         if (saved.imageModel) imageSelect.value = saved.imageModel;
    } catch (e) {
        console.warn('Failed to load models:', e);
    }

    function getModels() {
        const models = {
            smartModel: smartSelect.value,
             fastModel: fastSelect.value,
             imageModel: imageSelect.value
        };
        saveModelSelections(MODEL_PREFIX, models);
        return models;
    }

    // Save model selections on change
     smartSelect.addEventListener('change', () => saveModelSelections(MODEL_PREFIX, { smartModel: smartSelect.value, fastModel: fastSelect.value, imageModel: imageSelect.value }));
     fastSelect.addEventListener('change', () => saveModelSelections(MODEL_PREFIX, { smartModel: smartSelect.value, fastModel: fastSelect.value, imageModel: imageSelect.value }));
     imageSelect.addEventListener('change', () => saveModelSelections(MODEL_PREFIX, { smartModel: smartSelect.value, fastModel: fastSelect.value, imageModel: imageSelect.value }));

    // ── File Paths ───────────────────────────────────────────────────
    const FILES = {
        notes: 'notes.md',
        updateNotes: 'update-notes.md',
        style: 'style.md',
        tex: 'doc.tex',
        pdf: 'doc.pdf',
        buildLog: 'build.log.md'
    };

    const OPS = {
        renderTex: 'ops/render_tex.md',
        updateTex: 'ops/update_tex.md',
        renderPdf: 'ops/render_pdf.md'
    };

    // ── DOM References ───────────────────────────────────────────────
    const editorNotes = document.getElementById('editor-notes');
    const editorUpdateNotes = document.getElementById('editor-update-notes');
    const editorStyle = document.getElementById('editor-style');
    const editorTex = document.getElementById('editor-tex');
    const buildLogContent = document.getElementById('build-log-content');
    const pdfContainer = document.getElementById('pdf-preview-container');
    const gitStatusDisplay = document.getElementById('git-status-display');

    // ── Load Existing Files ──────────────────────────────────────────
    async function loadFile(filePath, editor, placeholder) {
        try {
            const content = await readFile(basePath, filePath);
            if (content !== null) {
                editor.value = content;
            }
        } catch (e) {
            console.warn(`Could not load ${filePath}:`, e);
        }
    }

    async function loadAllFiles() {
        await Promise.all([
            loadFile(FILES.notes, editorNotes),
            loadFile(FILES.updateNotes, editorUpdateNotes),
            loadFile(FILES.style, editorStyle),
            loadFile(FILES.tex, editorTex),
        ]);
        await refreshBuildLog();
        await refreshPdf();
    }

    // ── Save Helpers ─────────────────────────────────────────────────
    async function saveFile(filePath, content, statusId) {
        try {
            await writeFile(basePath, filePath, content);
            setStatus(statusId, '✓ Saved', 'success');
        } catch (e) {
            setStatus(statusId, `Error: ${e.message}`, 'error');
        }
    }

    document.getElementById('btn-save-notes').addEventListener('click', () =>
        saveFile(FILES.notes, editorNotes.value, 'status-notes'));

    document.getElementById('btn-save-update-notes').addEventListener('click', () =>
        saveFile(FILES.updateNotes, editorUpdateNotes.value, 'status-update-notes'));
    document.getElementById('btn-save-style').addEventListener('click', () =>
        saveFile(FILES.style, editorStyle.value, 'status-style'));


    document.getElementById('btn-save-tex').addEventListener('click', () =>
        saveFile(FILES.tex, editorTex.value, 'status-tex'));

    // ── Refresh Helpers ──────────────────────────────────────────────
    document.getElementById('btn-refresh-tex').addEventListener('click', async () => {
        await loadFile(FILES.tex, editorTex);
        showToast('TeX source refreshed', 'info', 2000);
    });

    async function refreshBuildLog() {
        try {
            const log = await readFile(basePath, FILES.buildLog);
            buildLogContent.textContent = log || 'No build log yet.';
        } catch (e) {
            buildLogContent.textContent = 'Could not load build log.';
        }
    }

    document.getElementById('btn-refresh-build-log').addEventListener('click', async () => {
        await refreshBuildLog();
        showToast('Build log refreshed', 'info', 2000);
    });

    // ── File Index Helper ────────────────────────────────────────────
    async function getFileLastModified(fileName) {
        try {
            const indexUrl = `${basePath}/_files.json`;
            const resp = await fetch(indexUrl);
            if (!resp.ok) return null;
            const data = await resp.json();
            const entry = (data.entries || []).find(e => e.name === fileName && e.type === 'file');
            return entry ? entry.lastModified : null;
        } catch (e) {
            console.warn('Could not fetch file index:', e);
            return null;
        }
    }

    async function refreshPdf() {
        try {
            const modified = await getFileLastModified(FILES.pdf);
            if (modified === null) {
                // File doesn't exist (yet)
                return;
            }
            if (modified !== lastPdfModified) {
                lastPdfModified = modified;
                const pdfUrl = `${basePath}/${FILES.pdf}?t=${modified}`;
                pdfContainer.innerHTML = `<iframe src="${pdfUrl}" title="PDF Preview"></iframe>`;
            }
        } catch (e) {
            console.warn('Could not check PDF:', e);
        }
    }

    document.getElementById('btn-refresh-pdf').addEventListener('click', async () => {
        await refreshPdf();
        showToast('PDF refreshed', 'info', 2000);
    });

    // ── Download Helpers ─────────────────────────────────────────────
    document.getElementById('btn-download-tex').addEventListener('click', () => {
        const blob = new Blob([editorTex.value], { type: 'application/x-tex' });
        downloadBlob(blob, 'doc.tex');
    });

    document.getElementById('btn-download-pdf').addEventListener('click', () => {
        const pdfUrl = `${basePath}/${FILES.pdf}`;
        window.open(pdfUrl, '_blank');
    });

    function downloadBlob(blob, filename) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    // ── Status Poller ────────────────────────────────────────────────

const targetBadgeMap = {
        [FILES.tex]: 'badge-render-tex',
        [FILES.buildLog]: 'badge-render-pdf',
    };

    const targetStepMap = {
        [FILES.tex]: 'step-render-tex',
        [FILES.buildLog]: 'step-render-pdf',
    };

    const targetLinkMap = {
        [FILES.tex]: 'render-tex-links',
        [FILES.buildLog]: 'render-pdf-links',
    };

    function updateStepClass(target, status) {
        const stepId = targetStepMap[target];
        if (!stepId) return;
        const el = document.getElementById(stepId);
        if (!el) return;
        el.classList.remove('active', 'completed', 'error');
        if (status === 'RUNNING') el.classList.add('active');
        else if (status === 'COMPLETED') el.classList.add('completed');
        else if (status === 'ERROR' || status === 'FAILED') el.classList.add('error');
    }

    function handleStatusUpdate(target, taskInfo) {
        // Update badge
        const badgeId = targetBadgeMap[target];
        if (badgeId) {
            const state = taskInfo.status === 'COMPLETED' ? 'done' :
                taskInfo.status === 'RUNNING' ? 'running' :
                    (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') ? 'error' : 'pending';
            setBadge(badgeId, state);
        }

        // Update step styling
        updateStepClass(target, taskInfo.status);

        // Update session links
        const containerId = targetLinkMap[target];
        if (containerId && taskInfo.sessionId) {
            const container = document.getElementById(containerId);
            if (container) {
                if (taskInfo.status === 'RUNNING') {
                    container.innerHTML = `<a href="${getProxyUrl(taskInfo.sessionId)}" target="_blank">🔴 Monitor Live Session</a>`;
                } else if (taskInfo.status === 'COMPLETED') {
                    container.innerHTML = `<a href="${getProxyUrl(taskInfo.sessionId)}" target="_blank">✅ View Session Log</a>`;
                } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                    container.innerHTML = `<a href="${getProxyUrl(taskInfo.sessionId)}" target="_blank">❌ View Error Log</a>`;
                }
            }
        }

        // Track session IDs
        if (taskInfo.sessionId && !trackedSessions.includes(taskInfo.sessionId)) {
            trackedSessions.push(taskInfo.sessionId);
        }

        // Auto-refresh outputs on completion
        if (taskInfo.status === 'COMPLETED') {
            if (target === FILES.tex || target === FILES.tex) {
                loadFile(FILES.tex, editorTex);
            }
            if (target === FILES.buildLog) {
                refreshBuildLog();
                refreshPdf();
            }
        }
    }

    const statusPoller = createStatusPoller(basePath, handleStatusUpdate);
    statusPoller.start();

    // ── DocOp Runners ────────────────────────────────────────────────
    async function runStep(opPath, targetPath, badgeId, statusElId, btnEl) {
        const models = getModels();
        btnEl.disabled = true;
        setBadge(badgeId, 'running');

        try {
            // Auto-save inputs before running
            await writeFile(basePath, FILES.notes, editorNotes.value);
            await writeFile(basePath, FILES.updateNotes, editorUpdateNotes.value);
            if (editorStyle.value.trim()) {
                await writeFile(basePath, FILES.style, editorStyle.value);
            }
            if (editorTex.value.trim()) {
                await writeFile(basePath, FILES.tex, editorTex.value);
            }

            const taskId = await runDocOp(sessionId, opPath, targetPath, models);

            if (taskId && !trackedSessions.includes(taskId)) {
                trackedSessions.push(taskId);
            }

            const statusEl = document.getElementById(statusElId);
            if (statusEl) statusEl.textContent = 'Running...';

            await waitForTask(basePath, targetPath, 600000, (target, task) => {
                handleStatusUpdate(target, task);
                if (statusEl) statusEl.textContent = `Status: ${task.status}`;
            });

            setBadge(badgeId, 'done');
            if (statusEl) statusEl.textContent = 'Completed ✓';

            // Refresh relevant outputs
            if (targetPath === FILES.tex) {
                await loadFile(FILES.tex, editorTex);
            }
            if (targetPath === FILES.buildLog) {
                await refreshBuildLog();
                await refreshPdf();
            }

            return true;
        } catch (e) {
            setBadge(badgeId, 'error');
            const statusEl = document.getElementById(statusElId);
            if (statusEl) statusEl.textContent = `Error: ${e.message}`;
            showToast(`Step failed: ${e.message}`, 'error');
            return false;
        } finally {
            btnEl.disabled = false;
        }
    }

    // Step buttons
    document.getElementById('btn-render-tex').addEventListener('click', function () {
        runStep(OPS.renderTex, FILES.tex, 'badge-render-tex', 'status-render-tex', this);
    });

    document.getElementById('btn-update-tex').addEventListener('click', function () {
        runStep(OPS.updateTex, FILES.tex, 'badge-update-tex', 'status-update-tex', this);
    });

    document.getElementById('btn-render-pdf').addEventListener('click', function () {
        runStep(OPS.renderPdf, FILES.buildLog, 'badge-render-pdf', 'status-render-pdf', this);
    });








    // ── Git ──────────────────────────────────────────────────────────
    document.getElementById('btn-git-init').addEventListener('click', async () => {
        try {
            await initRepository(basePath);
            showToast('Git repository initialized', 'success');
            await refreshGitStatus();
        } catch (e) {
            showToast(`Git init failed: ${e.message}`, 'error');
        }
    });

    document.getElementById('btn-git-commit').addEventListener('click', async () => {
        const message = prompt('Commit message:', 'Update TeX document');
        if (!message) return;
        try {
            await commit(basePath, message);
            showToast('Changes committed', 'success');
            await refreshGitStatus();
        } catch (e) {
            showToast(`Commit failed: ${e.message}`, 'error');
        }
    });

    document.getElementById('btn-git-status').addEventListener('click', refreshGitStatus);

    async function refreshGitStatus() {
        try {
            const status = await gitGetStatus(basePath);
            gitStatusDisplay.innerHTML = gitFormatStatus(status);
        } catch (e) {
            gitStatusDisplay.textContent = `Git not initialized or error: ${e.message}`;
        }
    }

    // ── Usage ────────────────────────────────────────────────────────
    document.getElementById('btn-refresh-usage').addEventListener('click', refreshUsage);

    async function refreshUsage() {
        try {
            const allSessions = [sessionId, ...trackedSessions];
            const unique = [...new Set(allSessions)];

            // Fetch usage for the main session at minimum
            const usage = await fetchUsageData(sessionId);
            if (usage) {
                renderUsageSummary(usage.totals || usage, {
                    prompt: document.getElementById('usage-prompt'),
                    completion: document.getElementById('usage-completion'),
                    total: document.getElementById('usage-total'),
                    cost: document.getElementById('usage-cost')
                });

                if (usage.models) {
                    document.getElementById('usage-table-container').innerHTML =
                        createUsageTableHtml(usage.models, usage.totals || usage);
                }
            }
        } catch (e) {
            console.warn('Usage fetch failed:', e);
        }
    }

    // ── Initialize ───────────────────────────────────────────────────
    await loadAllFiles();
    await refreshGitStatus();

    // Keyboard shortcut: Ctrl+S to save all
    document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            e.preventDefault();
            Promise.all([
                saveFile(FILES.notes, editorNotes.value, 'status-notes'),
                saveFile(FILES.updateNotes, editorUpdateNotes.value, 'status-update-notes'),
                saveFile(FILES.style, editorStyle.value, 'status-style'),
                saveFile(FILES.tex, editorTex.value, 'status-tex'),
            ]).then(() => showToast('All files saved', 'success', 2000));
        }
    });

})();