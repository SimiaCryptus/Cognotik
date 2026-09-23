/**
 * lensPage.js — shared runtime for the standalone output-lens pages.
 *
 * Every output lens is a small workbench with the same loop:
 *
 *     ① Generate ──► artifact ──► ② Build (optional) ──► ③ OCR Review
 *          ▲                                                   │
 *          └──────── ④ Update ◄──── <lens>-notes.md ◄──────────┘
 *
 * `<lens>-notes.md` is the single fan-in channel for iteration: the top
 * half is authored by you, the bottom half is rewritten by the vision
 * review agent. `Update` applies both to the artifact.
 *
 * Artifact revisions are appended to the same `state.json` the main
 * workbench uses, so history / staleness stay consistent across pages.
 */

import {createStatusPoller, fetchDocopsStatus, runDocOp, waitForTask} from '/app/docops.js';
import {listFiles, readFile, writeFile} from '/app/fileIO.js';
import {getProxyUrl, parseSessionUrl} from '/app/session.js';
import {updateSessionLinks} from '/app/sessionLinks.js';
import {initMenu} from '/app/menu.js';
import {escapeHtml, renderMarkdown, setStatus, showToast} from '/app/ui.js';
import {
    loadApiProviders,
    loadModelSelections,
    populateModelDropdowns,
    saveModelSelections
} from '/app/models.js';
import {ARTICLE_DRAFT_ID, agentAuthor, artifactDraftId} from './core.js';
import {appendRevision, createDraft, freezeRevision, headRef} from './drafts.js';
import {createStore} from './store.js';

const TASK_TIMEOUT_MS = 600_000;
const POLL_INTERVAL_MS = 3000;
const AUTOSAVE_MS = 800;
const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
const MODEL_STORAGE_PREFIX = 'philcalc';

/** Every standalone lens page, in navigation order. */
export const LENS_PAGES = [
    {id: 'persuasive-essay', href: 'lens-persuasive.html', label: '🎯 Persuasive'},
    {id: 'narrative-story', href: 'lens-narrative.html', label: '📖 Narrative'},
    {id: 'comic-script', href: 'lens-comic.html', label: '💬 Comic'},
    {id: 'technical-tutorial', href: 'lens-technical.html', label: '🔧 Technical'},
    {id: 'html-webpage', href: 'lens-webpage.html', label: '🌐 Webpage'},
    {id: 'pdf-document', href: 'lens-latex.html', label: '📚 LaTeX / PDF'}
];

export const NOTES_TEMPLATE = [
    '## Author notes',
    '',
    '- (your revision requests for this deliverable go here)',
    '',
    '## OCR review',
    '',
    '_Run **Review** to have the vision agent inspect the rendered output._',
    ''
].join('\n');
/** Placeholder the vision agent replaces on its first pass. */
const OCR_PLACEHOLDER = NOTES_TEMPLATE.split('## OCR review')[1].trim();
/** True once `## OCR review` holds something other than the seed placeholder. */
export const hasOcrReview = notes => {
     const body = (/##\s+OCR review\s*([\s\S]*)$/i.exec(notes ?? '')?.[1] ?? '').trim();
     return !!body && body !== OCR_PLACEHOLDER;
};


/* --------------------------------------------------------------- utils */

const $ = id => document.getElementById(id);

const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
};

const emptyState = (host, icon, title, descHtml) => {
    if (!host) return;
    const wrap = el('div', 'empty-state');
    const iconEl = el('div', 'empty-icon', icon);
    iconEl.setAttribute('aria-hidden', 'true');
    const desc = el('p', 'empty-desc');
    desc.innerHTML = descHtml;
    wrap.append(iconEl, el('p', 'empty-title', title), desc);
    host.replaceChildren(wrap);
};

const debounce = (fn, ms = AUTOSAVE_MS) => {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), ms);
    };
};

const BADGE_CLASSES = {
    idle: 'pending', queued: 'running', running: 'running',
    ready: 'done', stale: 'stale', error: 'error', skipped: 'skipped'
};
const BADGE_LABELS = {
    idle: 'pending', queued: 'queued', running: 'running',
    ready: 'done', stale: 'stale', error: 'error', skipped: 'skipped'
};

function buildNav(currentId) {
    const nav = $('lens-nav');
    if (!nav) return;
    const back = el('a', 'nav-link', '← Workbench');
    back.href = 'app.html';
    nav.append(back);
    for (const page of LENS_PAGES) {
        const link = el('a', `nav-link${page.id === currentId ? ' active' : ''}`, page.label);
        link.href = page.href;
        nav.append(link);
    }
}

/* ---------------------------------------------------------------- init */

/**
 * @param {object} config
 * @param {string} config.lensId         unified LensId (common.schema.ts)
 * @param {string} config.icon
 * @param {string} config.title
 * @param {string} [config.blurb]
 * @param {{file:string, mono?:boolean}} config.source   the editable artifact
 * @param {string} config.notesFile      revision notes (user + OCR review)
 * @param {string} [config.buildLog]     enables the build step + log card
 * @param {{kind:'pdf'|'html'|'markdown', file?:string}} [config.preview]
 * @param {{generate:string, build?:string, review?:string, update?:string}} config.ops
 */
export function initLensPage(config) {
    const {basePath, sessionId} = parseSessionUrl();
    const store = createStore(basePath);
    const preview = config.preview ?? {kind: 'markdown'};
    const previewFile = preview.file ?? config.source.file;

    try {
        initMenu({appName: `Philosophical Calculator · ${config.title}`});
    } catch (err) {
        console.warn('[lens] menu init failed', err);
    }

    document.title = `${config.icon} ${config.title} · Philosophical Calculator`;
    if ($('lens-title')) $('lens-title').textContent = `${config.icon} ${config.title}`;
    if ($('lens-blurb')) $('lens-blurb').textContent = config.blurb ?? '';
    buildNav(config.lensId);

    const root = $('lens-root');
    if (!root) throw new Error('lensPage: #lens-root is missing');

    /* ---------------- step definitions ---------------- */

    const stepDefs = [];
    const addStep = def => stepDefs.push({...def, number: stepDefs.length + 1});

    addStep({
        key: 'generate',
        title: 'Generate',
        label: '▶ Generate',
        variant: 'btn-primary',
        op: config.ops.generate,
        target: config.source.file,
        origin: 'lens-output',
        desc: `Render <code>${escapeHtml(config.source.file)}</code> from the current article revision.`
    });
    if (config.ops.build) {
        addStep({
            key: 'build',
            title: 'Build',
            label: '🔨 Build',
            variant: 'btn-primary',
            op: config.ops.build,
            target: config.buildLog,
            desc: `Compile <code>${escapeHtml(config.source.file)}</code> → ` +
                `<code>${escapeHtml(previewFile)}</code>, writing <code>${escapeHtml(config.buildLog)}</code>.`
        });
    }
    if (config.ops.review) {
        addStep({
            key: 'review',
            title: 'OCR Review',
            label: '🔍 Review',
            variant: 'btn-secondary',
            op: config.ops.review,
            target: config.notesFile,
            desc: `Have the vision agent inspect the rendered output and rewrite the ` +
                `<em>OCR review</em> half of <code>${escapeHtml(config.notesFile)}</code>.`
        });
    }
    if (config.ops.update) {
        addStep({
            key: 'update',
            title: 'Update',
            label: '✏️ Update',
            variant: 'btn-secondary',
            op: config.ops.update,
            target: config.source.file,
            origin: 'revise-artifact',
            desc: `Apply <code>${escapeHtml(config.notesFile)}</code> — your notes plus the OCR review — ` +
                `to <code>${escapeHtml(config.source.file)}</code>.`
        });
    }

    const stepsByTarget = new Map();
    for (const step of stepDefs) {
        stepsByTarget.set(step.target, [...(stepsByTarget.get(step.target) ?? []), step]);
    }

    /* ---------------- model bar ---------------- */

    const modelSelects = {};
    const selectedModels = () => {
        const out = {};
        for (const key of MODEL_KEYS) {
            const value = modelSelects[key]?.value;
            if (value) out[key] = value;
        }
        return out;
    };

    const modelCard = el('div', 'card');
    {
        const head = el('div', 'card-header');
        const status = el('span', 'status-msg');
        status.id = 'model-status';
        status.setAttribute('aria-live', 'polite');
        head.append(el('h3', null, '🤖 Models'), status);

        const grid = el('div', 'model-selection-grid');
        const meta = {
            smartModel: ['⚡ Smart Model', 'Complex reasoning & long-form generation'],
            fastModel: ['🚀 Fast Model', 'Parsing and lighter sub-tasks'],
            imageModel: ['🖼️ Image Model', 'Illustrations and vision / OCR review']
        };
        for (const key of MODEL_KEYS) {
            const field = el('div', 'model-field');
            const label = el('label');
            label.htmlFor = `${key}-select`;
            label.append(el('span', 'model-label-row', meta[key][0]),
                el('span', 'model-label-hint', meta[key][1]));
            const select = el('select', 'model-select');
            select.id = `${key}-select`;
            select.append(el('option', null, 'Loading models…'));
            modelSelects[key] = select;
            field.append(label, select);
            grid.append(field);
        }

        const row = el('div', 'button-row');
        const save = el('button', 'btn btn-primary btn-sm', '💾 Save Models');
        save.type = 'button';
        save.addEventListener('click', () => {
            saveModelSelections(MODEL_STORAGE_PREFIX,
                Object.fromEntries(MODEL_KEYS.map(k => [k, modelSelects[k].value ?? ''])));
            setStatus('model-status', '✓ Saved', 'success');
        });
        const reload = el('button', 'btn btn-secondary btn-sm', '🔄 Reload');
        reload.type = 'button';
        reload.addEventListener('click', () => reloadModels(true));
        row.append(save, reload);

        modelCard.append(head, grid, row);
    }

    const reloadModels = async (announce = false) => {
        try {
            const providers = await loadApiProviders();
            populateModelDropdowns(providers, MODEL_KEYS.map(k => modelSelects[k]),
                loadModelSelections(MODEL_STORAGE_PREFIX, MODEL_KEYS));
            if (announce) setStatus('model-status', '✓ Models reloaded', 'success');
        } catch (err) {
            console.warn('[lens] model load failed', err);
            setStatus('model-status', `✗ ${err.message}`, 'error');
        }
    };

    /* ---------------- steps ---------------- */

    const stepsSection = el('section', 'section active');
    {
        const header = el('div', 'section-header');
        header.append(el('h2', null, '⚙️ Pipeline'),
            el('p', 'section-subtitle',
                'Generate once, then loop Review → Update until the rendering is right.'));
        stepsSection.append(header);
    }

    for (const step of stepDefs) {
        const card = el('div', 'card step-card');
        const head = el('div', 'step-header');
        const badge = el('span', 'step-badge pending', 'pending');
        badge.id = `badge-${step.key}`;
        badge.dataset.state = 'idle';
        head.append(el('span', 'step-number', String(step.number)),
            el('span', 'step-title', step.title), badge);

        const desc = el('p', 'step-desc');
        desc.innerHTML = step.desc;

        const row = el('div', 'button-row');
        const button = el('button', `btn ${step.variant} btn-run`, step.label);
        button.type = 'button';
        button.addEventListener('click', () => runStep(step));
        row.append(button);

        const links = el('div', 'session-link-container');
        links.id = `links-${step.key}`;
        links.dataset.sessionLinks = step.target;

        step.badge = badge;
        step.button = button;
        step.linksId = links.id;

        card.append(head, desc, row, links);
        stepsSection.append(card);
    }

    /* ---------------- panels ---------------- */

    const panels = el('section', 'section active');
    {
        const header = el('div', 'section-header');
        header.append(el('h2', null, '📄 Artifact'),
            el('p', 'section-subtitle',
                'Notes drive the next update; the source is fully editable at any time.'));
        panels.append(header);
    }

    /* notes card */
    const notesEditor = el('textarea', 'editor');
    notesEditor.id = 'notes-editor';
    notesEditor.rows = 10;
    notesEditor.placeholder = 'Revision notes for this deliverable…';
    {
        const card = el('div', 'card');
        const head = el('div', 'card-header');
        const status = el('span', 'status-msg');
        status.id = 'status-notes';
        status.setAttribute('aria-live', 'polite');
        const conflict = el('span', 'status-msg status-warning is-hidden',
            '⚠ the agent rewrote this file while you had unsaved edits');
        conflict.id = 'notes-conflict';
        head.append(el('h3', null, `📝 Revision notes — ${config.notesFile}`), conflict, status);

        const hint = el('p', 'hint');
        hint.innerHTML = 'Keep your own requests under <code>## Author notes</code>; the review agent ' +
            'only rewrites <code>## OCR review</code>.';

        const row = el('div', 'button-row');
        const save = el('button', 'btn btn-primary btn-sm', '💾 Save');
        save.type = 'button';
        save.addEventListener('click', () => saveNotes());
        const reload = el('button', 'btn btn-secondary btn-sm', '🔄 Reload');
        reload.type = 'button';
        reload.addEventListener('click', () => loadNotes({force: true}));
        row.append(save, reload);

        card.append(head, hint, notesEditor, row);
        panels.append(card);
    }

    /* source card */
    const sourceEditor = el('textarea', `editor${config.source.mono === false ? '' : ' editor-mono'}`);
    sourceEditor.id = 'source-editor';
    sourceEditor.rows = 18;
    sourceEditor.placeholder = `${config.source.file} will appear here after you run Generate…`;
    {
        const card = el('div', 'card');
        const head = el('div', 'card-header');
        const status = el('span', 'status-msg');
        status.id = 'status-source';
        status.setAttribute('aria-live', 'polite');
         const conflict = el('span', 'status-msg status-warning is-hidden',
             '⚠ the agent rewrote this file while you had unsaved edits — Refresh to load it');
         conflict.id = 'source-conflict';
         head.append(el('h3', null, `🧾 ${config.source.file}`), conflict, status);

        const row = el('div', 'button-row');
        const save = el('button', 'btn btn-primary btn-sm', '💾 Save');
        save.type = 'button';
        save.addEventListener('click', () => saveSource());
        const reload = el('button', 'btn btn-secondary btn-sm', '🔄 Refresh');
        reload.type = 'button';
        reload.addEventListener('click', () => loadSource({force: true}));
        const download = el('button', 'btn btn-secondary btn-sm', '⬇ Download');
        download.type = 'button';
        download.addEventListener('click', () => downloadSource());
        row.append(save, reload, download);

        card.append(head, sourceEditor, row);
        panels.append(card);
    }

    /* preview card */
    const previewHost = el('div', 'viewer visible');
    previewHost.id = 'preview-host';
    {
        const card = el('div', 'card');
        const head = el('div', 'card-header');
        head.append(el('h3', null, `👁 Preview — ${previewFile}`));

        const row = el('div', 'button-row');
        const refresh = el('button', 'btn btn-secondary btn-sm', '🔄 Refresh');
        refresh.type = 'button';
        refresh.addEventListener('click', () => refreshPreview(true));
        row.append(refresh);
        if (preview.kind === 'pdf' || preview.kind === 'html') {
            const open = el('a', 'btn btn-secondary btn-sm', '🚀 Open in New Tab');
            open.id = 'preview-open';
            open.target = '_blank';
            open.rel = 'noopener';
            open.href = `${basePath}/${previewFile}`;
            row.append(open);
        }
        card.append(head, row, previewHost);
        panels.append(card);
    }

    /* build log card */
    let logHost = null;
    if (config.buildLog) {
        const card = el('div', 'card');
        const head = el('div', 'card-header');
        head.append(el('h3', null, `📋 Build log — ${config.buildLog}`));
        const row = el('div', 'button-row');
        const refresh = el('button', 'btn btn-secondary btn-sm', '🔄 Refresh');
        refresh.type = 'button';
        refresh.addEventListener('click', () => refreshBuildLog());
        row.append(refresh);
        logHost = el('div', 'log-container');
        logHost.id = 'log-host';
        card.append(head, row, logHost);
        panels.append(card);
    }

    root.append(modelCard, stepsSection, panels);

    /* ---------------- files ---------------- */

    const dirty = new Set();

    const loadNotes = async ({force = false} = {}) => {
        if (!force && dirty.has(config.notesFile)) {
            $('notes-conflict')?.classList.remove('is-hidden');
            return;
        }
        let content = null;
        try {
            content = await readFile(basePath, config.notesFile);
        } catch (err) {
            console.warn('[lens] notes read failed', err);
        }
        if (!content?.trim()) {
            content = NOTES_TEMPLATE;
            try {
                await writeFile(basePath, config.notesFile, content);
            } catch (err) {
                console.warn('[lens] notes seed failed', err);
            }
        }
        notesEditor.value = content;
        dirty.delete(config.notesFile);
        $('notes-conflict')?.classList.add('is-hidden');
    };

    const saveNotes = async ({silent = false} = {}) => {
        try {
            await writeFile(basePath, config.notesFile, notesEditor.value);
            dirty.delete(config.notesFile);
            $('notes-conflict')?.classList.add('is-hidden');
            if (!silent) setStatus('status-notes', '✓ Saved', 'success');
            return true;
        } catch (err) {
            setStatus('status-notes', `✗ ${err.message}`, 'error');
            return false;
        }
    };

    const loadSource = async ({force = false} = {}) => {
         if (!force && dirty.has(config.source.file)) {
             $('source-conflict')?.classList.remove('is-hidden');
             return;
         }
        try {
            const content = await readFile(basePath, config.source.file);
            if (content != null) sourceEditor.value = content;
            dirty.delete(config.source.file);
             $('source-conflict')?.classList.add('is-hidden');
        } catch (err) {
            console.warn('[lens] source read failed', err);
        }
    };

     /**
      * `record` snapshots the edit as a `manual-edit` artifact revision. Autosave
      * passes `record: false` so a typing pause does not inflate history; an
      * explicit Save, Ctrl/⌘+S and every step run record the checkpoint.
      */
     let sourceUnrecorded = false;
     const saveSource = async ({silent = false, record = true} = {}) => {
        try {
            await writeFile(basePath, config.source.file, sourceEditor.value);
            dirty.delete(config.source.file);
             $('source-conflict')?.classList.add('is-hidden');
             if (record) {
                 await recordArtifact('manual-edit');
                 sourceUnrecorded = false;
             } else {
                 sourceUnrecorded = true;
             }
            if (!silent) setStatus('status-source', '✓ Saved', 'success');
            await refreshPreview(true);
            return true;
        } catch (err) {
            setStatus('status-source', `✗ ${err.message}`, 'error');
            return false;
        }
    };

    const downloadSource = () => {
        const blob = new Blob([sourceEditor.value], {type: 'text/plain'});
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = config.source.file;
        link.click();
        URL.revokeObjectURL(url);
    };

    notesEditor.addEventListener('input', () => {
        dirty.add(config.notesFile);
        autosaveNotes();
    });
    sourceEditor.addEventListener('input', () => {
        dirty.add(config.source.file);
        autosaveSource();
    });
    const autosaveNotes = debounce(() => saveNotes({silent: true}));
     const autosaveSource = debounce(() => saveSource({silent: true, record: false}));

    const saveAll = async () => {
        if (dirty.has(config.notesFile)) await saveNotes({silent: true});
         if (dirty.has(config.source.file) || sourceUnrecorded) await saveSource({silent: true});
    };

    /* ---------------- preview / log ---------------- */

    const fileStamp = async name => {
        try {
            const result = await listFiles(basePath);
            const entries = Array.isArray(result) ? result : (result?.entries ?? []);
            const hit = entries.find(e =>
                (e === name) || e?.name === name || e?.path === name || e?.fileName === name);
            if (!hit) return null;
            return hit.lastModified ?? Date.now();
        } catch {
            return Date.now();
        }
    };

    const refreshPreview = async (force = false) => {
        if (preview.kind === 'pdf') {
            const stamp = await fileStamp(previewFile);
            if (stamp == null) {
                emptyState(previewHost, '📄', 'No PDF yet',
                    `Run <strong>Build</strong> to compile <code>${escapeHtml(previewFile)}</code>.`);
                return;
            }
            const frame = el('iframe', 'pdf-frame');
            frame.title = `${previewFile} preview`;
            frame.src = `${basePath}/${previewFile}?t=${force ? Date.now() : stamp}`;
            previewHost.replaceChildren(frame);
            const open = $('preview-open');
            if (open) open.href = `${basePath}/${previewFile}?t=${Date.now()}`;
            return;
        }

        let content = null;
        try {
            content = await readFile(basePath, previewFile);
        } catch (err) {
            console.warn('[lens] preview read failed', err);
        }
        if (!content?.trim()) {
            emptyState(previewHost, '📭', 'Nothing generated yet',
                `Run <strong>Generate</strong> to produce <code>${escapeHtml(previewFile)}</code>.`);
            return;
        }
        if (preview.kind === 'html') {
            const frame = el('iframe', 'html-preview-iframe');
            frame.setAttribute('sandbox', 'allow-same-origin');
            frame.title = 'Generated webpage preview';
            previewHost.replaceChildren(frame);
            try {
                const doc = frame.contentDocument ?? frame.contentWindow.document;
                doc.open();
                doc.write(content);
                doc.close();
            } catch (err) {
                console.warn('[lens] iframe write failed', err);
            }
            const open = $('preview-open');
            if (open) open.href = `${basePath}/${previewFile}?t=${Date.now()}`;
            return;
        }
        const html = renderMarkdown(content);
        if (html?.trim()) {
            previewHost.innerHTML = html;
        } else {
            const pre = el('pre', 'markdown-source', content);
            previewHost.replaceChildren(pre);
        }
    };

    const refreshBuildLog = async () => {
        if (!logHost) return;
        let content = null;
        try {
            content = await readFile(basePath, config.buildLog);
        } catch (err) {
            console.warn('[lens] build log read failed', err);
        }
        if (content?.trim()) {
            const pre = el('pre', 'log-viewer', content);
            logHost.replaceChildren(pre);
            return;
        }
        emptyState(logHost, '📋', 'No build log yet', 'Run <strong>Build</strong>.');
    };

    /* ---------------- revisions ---------------- */

    const recordArtifact = async (origin = 'lens-output') => {
        let content = null;
        try {
            content = await readFile(basePath, config.source.file);
        } catch {
            return;
        }
        if (!content?.trim()) return;
        const id = artifactDraftId(config.source.file);
        const createdBy = agentAuthor(selectedModels().smartModel ?? 'agent', config.lensId);
        store.update(state => {
            const article = state.drafts[ARTICLE_DRAFT_ID];
            const sourceRef = article ? headRef(article) : undefined;
            const existing = state.drafts[id];
            if (!existing) {
                state.drafts[id] = createDraft({
                    id, kind: config.lensId, title: config.source.file,
                    content, origin, sourceRef, createdBy
                });
            } else {
                const {draft} = appendRevision(existing, {content, origin, createdBy});
                state.drafts[id] = {...draft, sourceRef};
            }
            if (sourceRef && article) {
                state.drafts[ARTICLE_DRAFT_ID] = freezeRevision(article, sourceRef.revision);
            }
        });
        await store.flush().catch(err => console.warn('[lens] store flush', err));
    };

    /* ---------------- status ---------------- */

    const setStepBadge = (step, state) => {
        step.badge.className = `step-badge ${BADGE_CLASSES[state] ?? 'pending'}`;
        step.badge.dataset.state = state;
        step.badge.textContent = BADGE_LABELS[state] ?? state;
    };

    const runningTargets = new Set();
    let active = 0;
    let polling = false;

    const handleStatus = (target, info) => {
        const status = info?.status;
        const state = status === 'RUNNING' ? 'running'
            : status === 'COMPLETED' ? 'ready'
                : (status === 'ERROR' || status === 'FAILED') ? 'error' : null;
        for (const step of stepsByTarget.get(target) ?? []) {
            if (state) setStepBadge(step, state);
            updateSessionLinks(target, info, getProxyUrl, step.linksId);
        }
        if (status === 'RUNNING') runningTargets.add(target); else runningTargets.delete(target);
        maybeStopPolling();
    };

    const poller = createStatusPoller(basePath, handleStatus, POLL_INTERVAL_MS);
    const startPolling = () => {
        if (polling) return;
        polling = true;
        poller.start();
    };
    const maybeStopPolling = () => {
        if (polling && active === 0 && runningTargets.size === 0) {
            polling = false;
            poller.stop();
        }
    };

    const afterStep = async step => {
        if (step.target === config.source.file) {
             await loadSource();
            await recordArtifact(step.origin ?? 'revise-artifact');
            await refreshPreview(true);
        } else if (step.target === config.notesFile) {
            await loadNotes();
        }
        if (config.buildLog && step.target === config.buildLog) {
            await refreshBuildLog();
            await refreshPreview(true);
        }
    };

    async function runStep(step) {
        step.button.disabled = true;
        step.button.classList.add('is-busy');
        setStepBadge(step, 'running');
        active += 1;
        startPolling();
        try {
            await saveAll();
            const taskId = String(await runDocOp(sessionId, step.op, step.target, selectedModels()) ?? '').trim();
            if (/^[a-zA-Z0-9-]+$/.test(taskId)) {
                updateSessionLinks(step.target, {status: 'RUNNING', sessionId: taskId},
                    getProxyUrl, step.linksId);
            }
            await waitForTask(basePath, step.target, TASK_TIMEOUT_MS, handleStatus);
            setStepBadge(step, 'ready');
            await afterStep(step);
            showToast(`${step.title} complete`, 'success');
        } catch (err) {
            console.error('[lens] step failed', step.key, err);
            setStepBadge(step, 'error');
            showToast(`${step.title} failed: ${err.message}`, 'error');
        } finally {
            active -= 1;
            runningTargets.delete(step.target);
            step.button.disabled = false;
            step.button.classList.remove('is-busy');
            maybeStopPolling();
        }
    }

    const restoreStatuses = async () => {
        let anyRunning = false;
        try {
            const data = await fetchDocopsStatus(basePath);
            for (const [target, info] of Object.entries(data?.tasks ?? data ?? {})) {
                if (!info?.status) continue;
                handleStatus(target, info);
                if (info.status === 'RUNNING') anyRunning = true;
            }
        } catch (err) {
            console.warn('[lens] status fetch failed', err);
        }
         // Infer "done" from what is on disk — but only for the *first* step that
         // produces each target (Generate, not Update), and only count the notes
         // file once the agent has actually replaced the OCR placeholder.
         const seen = new Set();
        for (const step of stepDefs) {
             if (seen.has(step.target)) continue;
             seen.add(step.target);
            if (step.badge.dataset.state !== 'idle') continue;
            try {
                const content = await readFile(basePath, step.target);
                 const produced = step.target === config.notesFile
                     ? hasOcrReview(content)
                     : !!content?.trim();
                 if (produced) setStepBadge(step, 'ready');
            } catch {
                /* leave pending */
            }
        }
        if (anyRunning) startPolling(); else maybeStopPolling();
    };

    /* ---------------- boot ---------------- */

    document.addEventListener('keydown', async event => {
        if (!(event.ctrlKey || event.metaKey) || event.key !== 's') return;
        event.preventDefault();
        await saveAll();
        showToast('Saved', 'success');
    });
    window.addEventListener('beforeunload', () => {
        store.flush().catch(() => {
        });
    });

    (async () => {
        await reloadModels();
        await store.load();
        await loadNotes();
        await loadSource();
        await refreshBuildLog();
        await refreshPreview();
        await restoreStatuses();
    })().catch(err => console.error('[lens] boot failed', err));
}