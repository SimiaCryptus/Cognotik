/**
 * Interactive Stories — Main App
 *
 * Pipeline:
 *   1. User writes a story idea -> story_idea.md
 *   2. ops/initial_node.md     -> story/0.md
 *   3. ops/choice_{a,b,c}.md   -> story/{path}{a|b|c}.md
 *
 * Story node paths follow the pattern: story/<sequence>.md
 *   - "0"     : initial node
 *   - "0a"    : after choosing A from 0
 *   - "0ab"   : after choosing B from 0a
 *   - etc.
 */
import { parseSessionUrl, getProxyUrl } from './utils/session.js';
import {
    loadApiProviders,
    populateModelDropdowns,
    saveModelSelections,
    loadModelSelections
} from './utils/models.js';
import { readFile, writeFile, fileExists, listFiles } from './utils/fileIO.js';
import { runDocOp, waitForTask, createStatusPoller } from './utils/docops.js';
import {
    renderMarkdown,
    escapeHtml,
    setStatus,
    setBadge,
    showToast,
    createBatchLogger
} from './utils/ui.js';
import { updateSessionLinks, createSessionLinkManager } from './utils/sessionLinks.js';

'use strict';

const APP_PREFIX = 'interactiveStories';
const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
const IDEA_FILE = 'story_idea.md';
const STORY_DIR = 'story';
const INITIAL_NODE = '0';
const AUTO_READ_KEY = `${APP_PREFIX}.autoRead`;
const VOICE_KEY = `${APP_PREFIX}.voice`;
const AUTO_IMAGE_KEY = `${APP_PREFIX}.autoImage`;
const STYLESHEET_FILE = 'stylesheet_instructions.md';

const { basePath, sessionId } = parseSessionUrl();
console.log('[app] Parsed session URL — basePath:', basePath, '| sessionId:', sessionId);

// Persistent state
const linkManager = createSessionLinkManager(getProxyUrl);
const trackedSessions = new Map();   // target -> taskInfo (kept for life of page)
let currentNode = null;              // currently displayed node id (e.g. "0", "0a")
let allNodes = new Set();            // every story node we know about
let logger = null;
let statusPoller = null;
let isSpeaking = false;
let isGeneratingImage = false;

// Timing helpers
const _timers = new Map();
function timeStart(label) {
    console.debug('[timer] START', label);
    _timers.set(label, performance.now());
}
function timeEnd(label) {
    const start = _timers.get(label);
    if (start == null) return '';
    _timers.delete(label);
    const ms = Math.round(performance.now() - start);
    const formatted = ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;
    console.debug('[timer] END', label, '→', formatted);
    return formatted;
}

// -------------------------------------------------------------------------
// Initialization
// -------------------------------------------------------------------------
document.addEventListener('DOMContentLoaded', init);

async function init() {
    try {
        logger = createBatchLogger('activity-log');
        console.group('[init] App initialization started');
        console.log('[init] sessionId:', sessionId, '| basePath:', basePath);
        log('App initializing…', 'info');

        if (!sessionId) {
            showToast('Could not determine session from URL.', 'error', 8000);
            log('Initialization aborted: no sessionId found in URL.', 'error');
            console.error('[init] Aborted — no sessionId found in URL.');
            return;
        }
        log(`Session: ${sessionId} | basePath: ${basePath}`, 'info');


        await initModels();
        initVoices();
        await loadIdea();
        await refreshTree();

        // Wire up handlers
        console.log('[init] Wiring up DOM event handlers…');
        document.getElementById('save-idea').addEventListener('click', onSaveIdea);
        document.getElementById('start-story').addEventListener('click', onStartStory);
        document.getElementById('refresh-tree').addEventListener('click', refreshTree);
        document.getElementById('clear-log').addEventListener('click', () => logger.clear());
        document.querySelectorAll('.btn-choice').forEach(btn => {
            btn.addEventListener('click', () => onChoice(btn.dataset.choice));
        });
        document.getElementById('update-stylesheet').addEventListener('click', onUpdateStylesheet);

        // Immersive mode toggle
        document.getElementById('toggle-immersive').addEventListener('click', toggleImmersive);
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && document.body.classList.contains('immersive')) {
                toggleImmersive();
            } else if (e.key === 'f' && e.target.tagName !== 'TEXTAREA' && e.target.tagName !== 'INPUT') {
                // 'f' to toggle when not typing in a field
                if (currentNode) toggleImmersive();
            }
        });
        // Read aloud controls
        const readAloudBtn = document.getElementById('read-aloud');
        if (!readAloudBtn) {
            console.error('[init] CRITICAL: #read-aloud button not found in DOM!');
            log('CRITICAL: #read-aloud button not found in DOM — read-aloud will not work.', 'error');
        } else {
            console.log('[init] Binding click handler to #read-aloud button:', readAloudBtn);
            readAloudBtn.addEventListener('click', (e) => {
                console.log('[read-aloud click] Event fired — target:', e.target, '| currentTarget:', e.currentTarget, '| isSpeaking:', isSpeaking, '| currentNode:', currentNode);
                onReadAloudToggle();
            });
        }
        const autoReadEl = document.getElementById('auto-read');
        autoReadEl.checked = localStorage.getItem(AUTO_READ_KEY) === 'true';
        console.log('[init] auto-read initial state:', autoReadEl.checked);
        autoReadEl.addEventListener('change', () => {
            localStorage.setItem(AUTO_READ_KEY, autoReadEl.checked ? 'true' : 'false');
            console.log('[init] auto-read toggled →', autoReadEl.checked);
            log(`Auto-read ${autoReadEl.checked ? 'enabled' : 'disabled'}.`, 'info');
        });
        // Image controls
        document.getElementById('generate-image').addEventListener('click', onGenerateImage);
        const autoImageEl = document.getElementById('auto-image');
        autoImageEl.checked = localStorage.getItem(AUTO_IMAGE_KEY) === 'true';
        console.log('[init] auto-image initial state:', autoImageEl.checked);
        autoImageEl.addEventListener('change', () => {
            localStorage.setItem(AUTO_IMAGE_KEY, autoImageEl.checked ? 'true' : 'false');
            console.log('[init] auto-image toggled →', autoImageEl.checked);
            log(`Auto-image ${autoImageEl.checked ? 'enabled' : 'disabled'}.`, 'info');
        });


        // Save model selections on change
        ['smart-model', 'fast-model', 'image-model'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.addEventListener('change', persistModelSelections);
        });

        // Auto-save idea on input (debounced)
        const ideaEditor = document.getElementById('idea-editor');
        let ideaTimer;
        ideaEditor.addEventListener('input', () => {
            clearTimeout(ideaTimer);
            ideaTimer = setTimeout(autoSaveIdea, 800);
        });

        // Start global status poller
        statusPoller = createStatusPoller(basePath, onStatusUpdate, 4000);
        statusPoller.start();
        log('Status poller started (interval: 4s).', 'info');
        console.log('[init] Status poller started — interval: 4000ms.');

        // If initial node already exists, load it
        console.log('[init] Checking for existing initial node at', `${STORY_DIR}/${INITIAL_NODE}.md`);
        if (await fileExists(basePath, `${STORY_DIR}/${INITIAL_NODE}.md`)) {
            setBadge('badge-tree', 'done');
            log(`Existing initial node found (${STORY_DIR}/${INITIAL_NODE}.md). Loading…`, 'info');
            console.log('[init] Existing initial node found — loading node', INITIAL_NODE);
            if (!currentNode) await loadNode(INITIAL_NODE);
        } else {
            log('No initial story node found — story not yet started.', 'info');
            console.log('[init] No initial node found — story not yet started.');
        }
        await loadStylesheetInstructions();
        console.groupEnd();
        console.log('[init] App ready ✓');


        log('App ready.', 'success');
    } catch (err) {
        console.error(err);
        log(`Init error: ${err.message}`, 'error');
        showToast(`Init error: ${err.message}`, 'error');
    }

    window.addEventListener('error', e => log(`Uncaught error: ${e.message} (${e.filename}:${e.lineno})`, 'error'));
    window.addEventListener('unhandledrejection', e => log(`Unhandled promise rejection: ${e.reason}`, 'error'));
    window.addEventListener('error', e => console.error('[window] Uncaught error:', e.message, `(${e.filename}:${e.lineno})`));
    window.addEventListener('unhandledrejection', e => console.error('[window] Unhandled promise rejection:', e.reason));
}

// -------------------------------------------------------------------------
// Models
// -------------------------------------------------------------------------
async function initModels() {
    try {
        console.group('[initModels] Loading API providers and model lists…');
        log('Loading API providers and model lists…', 'info');
        const available = await loadApiProviders();
        console.log('[initModels] loadApiProviders() response:', available);
        const selects = MODEL_KEYS
            .map(k => {
                const idMap = { smartModel: 'smart-model', fastModel: 'fast-model', imageModel: 'image-model' };
                return document.getElementById(idMap[k]);
            })
            .filter(Boolean);
        const saved = loadModelSelections(APP_PREFIX, MODEL_KEYS);
        console.log('[initModels] Saved model selections:', saved);
        populateModelDropdowns(available, selects, saved);
        const providerCount = Array.isArray(available) ? available.length : Object.keys(available || {}).length;
        log(`Models loaded — ${providerCount} provider(s) available. Saved selections: smart="${saved.smartModel || 'none'}", fast="${saved.fastModel || 'none'}", image="${saved.imageModel || 'none'}".`, 'info');
        console.log(`[initModels] Done — ${providerCount} provider(s). smart="${saved.smartModel || 'none'}", fast="${saved.fastModel || 'none'}", image="${saved.imageModel || 'none'}".`);
        console.groupEnd();
    } catch (e) {
        log(`Failed to load models: ${e.message}`, 'error');
        console.error('[initModels]', e);
        console.groupEnd();
    }
}

function persistModelSelections() {
    const selections = {
        smartModel: document.getElementById('smart-model').value,
        fastModel: document.getElementById('fast-model').value,
        imageModel: document.getElementById('image-model').value
    };
    saveModelSelections(APP_PREFIX, selections);
    console.log('[persistModelSelections] Saved:', selections);
    log(`Model selections saved — smart="${selections.smartModel || 'none'}", fast="${selections.fastModel || 'none'}", image="${selections.imageModel || 'none'}".`, 'info');
}
// -------------------------------------------------------------------------
// Voices (browser TTS)
// -------------------------------------------------------------------------
function initVoices() {
    const select = document.getElementById('voice-select');
    if (!select) return;
    if (!('speechSynthesis' in window)) {
        log('Text-to-speech not supported in this browser.', 'warning');
        console.warn('[initVoices] speechSynthesis not available in this browser.');
        const opt = document.createElement('option');
        opt.textContent = 'Not supported';
        opt.disabled = true;
        select.appendChild(opt);
        select.disabled = true;
        return;
    }
    const populate = () => {
        const voices = window.speechSynthesis.getVoices() || [];
        const saved = localStorage.getItem(VOICE_KEY) || '';
        console.log(`[initVoices] populate() — ${voices.length} voice(s) available. Saved URI: "${saved || 'none'}".`);
        select.innerHTML = '';
        const defaultOpt = document.createElement('option');
        defaultOpt.value = '';
        defaultOpt.textContent = '— Default —';
        select.appendChild(defaultOpt);
        voices.forEach(v => {
            const opt = document.createElement('option');
            opt.value = v.voiceURI;
            opt.textContent = `${v.name} (${v.lang})${v.default ? ' [default]' : ''}`;
            select.appendChild(opt);
        });
        if (saved && voices.some(v => v.voiceURI === saved)) {
            select.value = saved;
            log(`Voice restored from preferences: "${saved}".`, 'info');
            console.log('[initVoices] Voice restored from preferences:', saved);
        } else if (voices.length > 0) {
            log(`${voices.length} voice(s) available. Using browser default.`, 'info');
            console.log(`[initVoices] Using browser default voice (${voices.length} available).`);
        }
    };
    populate();
    // Voices often load asynchronously
    if (typeof window.speechSynthesis.onvoiceschanged !== 'undefined') {
        console.log('[initVoices] Registering onvoiceschanged handler for async voice loading.');
        window.speechSynthesis.onvoiceschanged = populate;
    }
    select.addEventListener('change', () => {
        localStorage.setItem(VOICE_KEY, select.value);
        console.log('[initVoices] Voice selection changed →', select.value, '|', select.options[select.selectedIndex]?.textContent);
        log(`Voice changed to: "${select.options[select.selectedIndex]?.textContent || select.value}".`, 'info');
    });
}
function getSelectedVoice() {
    if (!('speechSynthesis' in window)) return null;
    const select = document.getElementById('voice-select');
    const uri = select ? select.value : '';
    if (!uri) return null;
    const voices = window.speechSynthesis.getVoices() || [];
    const voice = voices.find(v => v.voiceURI === uri) || null;
    console.debug('[getSelectedVoice] URI:', uri, '→ found:', voice ? voice.name : 'null');
    return voice;
}


function getModelOverrides() {
    const out = {};
    const smart = document.getElementById('smart-model').value;
    const fast = document.getElementById('fast-model').value;
    const image = document.getElementById('image-model').value;
    if (smart) out.smartModel = smart;
    if (fast) out.fastModel = fast;
    if (image) out.imageModel = image;
    console.debug('[getModelOverrides] Overrides:', out);
    return out;
}

// -------------------------------------------------------------------------
// Story idea
// -------------------------------------------------------------------------
async function loadIdea() {
    log(`Loading story idea from "${IDEA_FILE}"…`, 'info');
    console.log('[loadIdea] Reading file:', IDEA_FILE);
    const content = await readFile(basePath, IDEA_FILE);
    if (content != null) {
        document.getElementById('idea-editor').value = content;
        setBadge('badge-idea', 'done');
        log(`Story idea loaded (${content.length} chars).`, 'info');
        console.log(`[loadIdea] Loaded ${content.length} chars from "${IDEA_FILE}".`);
    } else {
        log('No existing story idea found.', 'info');
        console.log('[loadIdea] File not found or empty — no existing idea.');
    }
}

async function onSaveIdea() {
    const content = document.getElementById('idea-editor').value.trim();
    if (!content) {
        setStatus('idea-status', 'Idea cannot be empty.', 'error');
        log('Save idea aborted: editor is empty.', 'warning');
        console.warn('[onSaveIdea] Aborted — editor is empty.');
        return;
    }
    try {
        log(`Saving story idea (${content.length} chars)…`, 'info');
        console.log(`[onSaveIdea] Writing ${content.length} chars to "${IDEA_FILE}"…`);
        await writeFile(basePath, IDEA_FILE, content);
        setStatus('idea-status', 'Idea saved.', 'success');
        setBadge('badge-idea', 'done');
        log(`Story idea saved successfully (${content.length} chars).`, 'success');
        console.log('[onSaveIdea] Save successful.');
    } catch (e) {
        setStatus('idea-status', `Save failed: ${e.message}`, 'error');
        log(`Story idea save failed: ${e.message}`, 'error');
        console.error('[onSaveIdea]', e);
    }
}

async function autoSaveIdea() {
    const content = document.getElementById('idea-editor').value.trim();
    if (!content) return;
    try {
        console.debug('[autoSaveIdea] Auto-saving idea —', content.length, 'chars…');
        await writeFile(basePath, IDEA_FILE, content);
        setStatus('idea-status', 'Auto-saved.', 'info', 2000);
        setBadge('badge-idea', 'done');
        log(`Story idea auto-saved (${content.length} chars).`, 'info');
        console.debug('[autoSaveIdea] Auto-save successful.');
    } catch (e) {
        log(`Story idea auto-save failed: ${e.message}`, 'warning');
        console.warn('[autoSaveIdea] Auto-save failed:', e);
    }
}

// -------------------------------------------------------------------------
// Begin / continue story
// -------------------------------------------------------------------------
async function onStartStory() {
    const ideaContent = document.getElementById('idea-editor').value.trim();
    if (!ideaContent) {
        showToast('Please write a story idea first.', 'warning');
        log('Start story aborted: no idea content.', 'warning');
        console.warn('[onStartStory] Aborted — idea editor is empty.');
        return;
    }

    // Save idea first
    await writeFile(basePath, IDEA_FILE, ideaContent);
    setBadge('badge-idea', 'done');

    const target = `${STORY_DIR}/${INITIAL_NODE}.md`;
    console.log('[onStartStory] Target file:', target);

    // Confirm if initial node already exists
    if (await fileExists(basePath, target)) {
        log(`Initial node already exists at "${target}". Prompting user for confirmation.`, 'info');
        console.log('[onStartStory] Initial node already exists — prompting user for confirmation.');
        if (!confirm('An initial story node already exists. Regenerate it? This will overwrite the current root node (existing branches will remain on disk).')) {
            log('User cancelled regeneration of initial node.', 'info');
            console.log('[onStartStory] User cancelled regeneration.');
            await loadNode(INITIAL_NODE);
            return;
        }
        log('User confirmed regeneration of initial node.', 'info');
        console.log('[onStartStory] User confirmed regeneration of initial node.');
    }

    const startBtn = document.getElementById('start-story');
    startBtn.disabled = true;
    setBadge('badge-tree', 'running');
    setStatus('idea-status', 'Generating initial node...', 'info', 0);
    log(`Starting story — op: ops/initial_node.md → target: "${target}".`, 'info');
    timeStart('startStory');
    console.group('[onStartStory] Generating initial story node…');
    console.log('[onStartStory] op: ops/initial_node.md | target:', target, '| overrides:', getModelOverrides());

    try {
        const taskId = await runDocOp(
            sessionId,
            'ops/initial_node.md',
            target,
            getModelOverrides()
        );
        console.log('[onStartStory] runDocOp() returned taskId:', taskId);
        log(`DocOp started for initial node — task: ${formatTaskId(taskId)}.`, 'info');

        await waitForTask(basePath, target, 600000, (tgt, info) => {
            trackedSessions.set(tgt, info);
            linkManager.update(tgt, info);
            updateSessionLinks(tgt, info, getProxyUrl, 'initial-links');
            console.debug('[onStartStory] waitForTask progress — target:', tgt, '| status:', info.status, '| info:', info);
            log(`Task progress for "${tgt}": status=${info.status}.`, 'info');
        });

        log(`Initial node generated successfully in ${timeEnd('startStory')}.`, 'success');
        console.log('[onStartStory] Initial node generation complete.');
        setBadge('badge-tree', 'done');
        setStatus('idea-status', 'Story started!', 'success');
        await refreshTree();
        await loadNode(INITIAL_NODE);
        maybeAutoGenerateImage(INITIAL_NODE);
    } catch (e) {
        log(`Failed to generate initial node after ${timeEnd('startStory')}: ${e.message}`, 'error');
        console.error('[onStartStory]', e);
        setStatus('idea-status', `Error: ${e.message}`, 'error');
        setBadge('badge-tree', 'error');
        showToast(`Failed: ${e.message}`, 'error');
    } finally {
        startBtn.disabled = false;
        console.groupEnd();
    }
}

async function onChoice(letter) {
    if (!currentNode) {
        showToast('Select a node first.', 'warning');
        log('Choice aborted: no current node selected.', 'warning');
        console.warn('[onChoice] Aborted — no current node selected.');
        return;
    }
    const target = `${STORY_DIR}/${currentNode}${letter}.md`;
    const opPath = `ops/choice_${letter}.md`;
    console.log(`[onChoice] letter="${letter}" | currentNode="${currentNode}" | target="${target}" | op="${opPath}"`);

    // If node already exists, just load it
    if (await fileExists(basePath, target)) {
        log(`Branch "${currentNode}${letter}" already exists — loading from disk.`, 'info');
        console.log(`[onChoice] Branch "${currentNode}${letter}" already exists — loading from disk.`);
        await loadNode(`${currentNode}${letter}`);
        return;
    }

    const buttons = document.querySelectorAll('.btn-choice');
    buttons.forEach(b => b.disabled = true);
    setBadge('badge-node', 'running');
    setStatus('choice-status', `Generating branch ${letter.toUpperCase()}...`, 'info', 0);
    log(`Generating branch ${letter.toUpperCase()} — op: ${opPath} → target: "${target}".`, 'info');
    timeStart(`branch_${letter}`);
    console.group(`[onChoice] Generating branch ${letter.toUpperCase()}…`);
    console.log(`[onChoice] op: ${opPath} | target: ${target} | overrides:`, getModelOverrides());

    try {
        const taskId = await runDocOp(
            sessionId,
            opPath,
            target,
            getModelOverrides()
        );
        console.log(`[onChoice] runDocOp() returned taskId:`, taskId);
        log(`DocOp started for branch ${letter.toUpperCase()} — task: ${formatTaskId(taskId)}.`, 'info');

        await waitForTask(basePath, target, 600000, (tgt, info) => {
            trackedSessions.set(tgt, info);
            linkManager.update(tgt, info);
            updateSessionLinks(tgt, info, getProxyUrl, 'node-links');
            console.debug(`[onChoice] waitForTask progress — target: ${tgt} | status: ${info.status} | info:`, info);
            log(`Task progress for "${tgt}": status=${info.status}.`, 'info');
        });

        log(`Branch ${letter.toUpperCase()} generated successfully in ${timeEnd(`branch_${letter}`)}.`, 'success');
        console.log(`[onChoice] Branch ${letter.toUpperCase()} generation complete.`);
        setBadge('badge-node', 'done');
        setStatus('choice-status', `Branch ${letter.toUpperCase()} ready.`, 'success');
        await refreshTree();
        const newNode = `${currentNode}${letter}`;
        await loadNode(newNode);
        maybeAutoGenerateImage(newNode);
    } catch (e) {
        log(`Failed to generate branch ${letter.toUpperCase()} after ${timeEnd(`branch_${letter}`)}: ${e.message}`, 'error');
        console.error(`[onChoice(${letter})]`, e);
        setStatus('choice-status', `Error: ${e.message}`, 'error');
        setBadge('badge-node', 'error');
        showToast(`Failed: ${e.message}`, 'error');
    } finally {
        buttons.forEach(b => b.disabled = false);
        console.groupEnd();
    }
}

// -------------------------------------------------------------------------
// Tree management
// -------------------------------------------------------------------------
async function refreshTree() {
    try {
        log(`Refreshing story tree from "${STORY_DIR}/"…`, 'info');
        timeStart('refreshTree');
        const files = await listFiles(basePath, STORY_DIR);
        allNodes = new Set();
        (files || []).forEach(f => {
            const name = (f && (f.name || f.path)) ? (f.name || f.path).split('/').pop() : null;
            if (name && /\.md$/.test(name) && !name.startsWith('_')) {
                const id = name.replace(/\.md$/, '');
                if (/^0[a-c]*$/.test(id)) allNodes.add(id);
            }
        });
         log(`Tree refreshed in ${timeEnd('refreshTree')} — ${allNodes.size} node(s): [${Array.from(allNodes).sort().join(', ')}].`, 'info');
         console.log('[refreshTree] Done —', allNodes.size, 'node(s):', Array.from(allNodes).sort());
        renderTree();
    } catch (e) {
         timeEnd('refreshTree'); // ensure timer is always cleared
        log(`Tree refresh failed: ${e.message}`, 'warning');
        console.warn('[refreshTree]', e);
    }
}

function renderTree() {
    const container = document.getElementById('story-tree');
    if (allNodes.size === 0) {
        container.innerHTML = '<p class="empty-state">No story started yet. Save your idea and click "Begin Story".</p>';
        return;
    }

    const childrenOf = new Map();
    allNodes.forEach(id => childrenOf.set(id, []));
    allNodes.forEach(id => {
        if (id === INITIAL_NODE) return;
        const parent = id.slice(0, -1);
        if (childrenOf.has(parent)) {
            childrenOf.get(parent).push(id);
        }
    });
    childrenOf.forEach(arr => arr.sort((a, b) => a.localeCompare(b)));

    const ordered = [];
    function walk(id, ancestorIsLast) {
        const kids = childrenOf.get(id) || [];
        ordered.push({ id, ancestorIsLast: ancestorIsLast.slice() });
        kids.forEach((kid, i) => {
            const isLast = i === kids.length - 1;
            walk(kid, ancestorIsLast.concat(isLast));
        });
    }
    if (allNodes.has(INITIAL_NODE)) {
        walk(INITIAL_NODE, []);
    } else {
        // Fallback: render any orphaned nodes alphabetically
        Array.from(allNodes).sort().forEach(id => ordered.push({ id, ancestorIsLast: [] }));
    }

    const frag = document.createDocumentFragment();
    ordered.forEach(({ id, ancestorIsLast }) => {
        const depth = ancestorIsLast.length;
        const div = document.createElement('div');
        div.className = 'tree-node' + (id === currentNode ? ' active' : '');
        div.dataset.nodeId = id;

        let label = '📖 Root';
        if (id !== INITIAL_NODE) {
            const lastChoice = id.charAt(id.length - 1).toUpperCase();
            label = `↳ Branch ${lastChoice} <span style="opacity:0.6;">(${id})</span>`;
        }

        let indent = '';
        if (depth > 0) {
            for (let i = 0; i < depth - 1; i++) {
                indent += ancestorIsLast[i] ? '  ' : '│ ';
            }
            indent += ancestorIsLast[depth - 1] ? '└ ' : '├ ';
        }
        div.innerHTML = `<span class="tree-indent">${escapeHtml(indent)}</span><span>${label}</span>`;
        div.addEventListener('click', () => loadNode(id));
        frag.appendChild(div);
    });

    container.innerHTML = '';
    container.appendChild(frag);
}

// -------------------------------------------------------------------------
// Node loading
// -------------------------------------------------------------------------
/**
  * Parse a story node's markdown to extract:
  *   - choices: { a: string, b: string, c: string } (may be partial or empty)
  *   - isEndState: true if no choices block is present (story ending)
  *   - choicePrompt: the heading text above the choices (if any)
  *
  * Expected markdown pattern (graceful fallback if absent):
  *   ### Some heading
  *   * **Choice A** - Description text
  *   * **Choice B** - Description text
  *   * **Choice C** - Description text
  */
function parseNodeContent(markdown) {
     const result = { isEndState: false, choices: {}, choicePrompt: null };
     try {
         if (!markdown) return result;
         // Match list items that look like choice entries.
         // Handles both:
         //   * **Choice A** - Description
         //   * **A** - Description
         //   * **Choice A:** Description
          //   * **Choice A** - **Title** — Body text
         const choiceLineRe = /^\s*[*-]\s+\*\*(?:Choice\s+)?([ABC])\**[:\s*-]+\s*(.+)$/gim;
         let match;
         while ((match = choiceLineRe.exec(markdown)) !== null) {
             const letter = match[1].toLowerCase();
              // Strip any leading/trailing ** artifacts left by partial markdown stripping
              const description = match[2].trim()
                  .replace(/^\*+/, '')           // strip leading **
                  .replace(/\*+\s*([—–-])/, ' $1') // strip ** immediately before an em/en-dash
                  .replace(/\*+$/, '')            // strip trailing **
                  .trim();
             result.choices[letter] = description;
         }
         // Detect end-state: no choices found AND the node contains an epitaph
         // marker (italic line at the end) or simply has no choice list at all.
         const hasChoices = Object.keys(result.choices).length > 0;
         if (!hasChoices) {
             result.isEndState = true;
         }
         // Extract the choice prompt heading (### …) that precedes the list, if any.
         if (hasChoices) {
             const promptRe = /^#{2,4}\s+(.+?)\s*$/m;
             // Find the last heading before the first choice line
             const firstChoiceIdx = markdown.search(/^\s*[*-]\s+\*\*(?:Choice\s+)?[ABC]/im);
             const beforeChoices = firstChoiceIdx > 0 ? markdown.slice(0, firstChoiceIdx) : markdown;
             const headings = [...beforeChoices.matchAll(/^#{2,4}\s+(.+?)\s*$/gm)];
             if (headings.length > 0) {
                 result.choicePrompt = headings[headings.length - 1][1].trim();
             }
         }
         console.debug('[parseNodeContent] result:', result);
     } catch (e) {
         console.warn('[parseNodeContent] Parsing failed — using defaults:', e);
         // Return safe defaults; UI will fall back to generic labels.
         result.isEndState = false;
         result.choices = {};
         result.choicePrompt = null;
     }
     return result;
}
/**
  * Apply parsed choice data to the choice-actions UI block.
  * Handles three states:
  *   1. End-state  — hide choice buttons, show "The End" panel.
  *   2. Choices found — populate button labels with story text.
  *   3. Fallback   — show generic "Generate Branch X" labels.
  */
function applyParsedChoicesToUI(nodeId, parsed) {
     const choiceActions = document.getElementById('choice-actions');
     const choiceButtons = document.getElementById('choice-buttons');
     const choicePromptEl = document.querySelector('#choice-actions .choice-prompt');
     let endPanel = document.getElementById('end-state-panel');
     // Remove any previous end-state panel
     if (endPanel) endPanel.remove();
     if (parsed.isEndState) {
         // Hide the choice buttons, show an end-state message
         choiceActions.style.display = 'block';
         if (choiceButtons) choiceButtons.style.display = 'none';
         if (choicePromptEl) choicePromptEl.style.display = 'none';
         endPanel = document.createElement('div');
         endPanel.id = 'end-state-panel';
         endPanel.className = 'end-state-panel';
         endPanel.innerHTML = `
             <div class="end-state-icon">📜</div>
             <p class="end-state-title">The story has reached its end.</p>
             <p class="end-state-hint">Select another branch from the tree, or start a new story.</p>
         `;
         // Insert before the session-link container
         const nodeLinks = document.getElementById('node-links');
         choiceActions.insertBefore(endPanel, nodeLinks || null);
         console.log('[applyParsedChoicesToUI] End-state panel shown for node:', nodeId);
         return;
     }
     // Not an end-state — show choice buttons
     if (choiceButtons) choiceButtons.style.display = '';
     if (choicePromptEl) {
         choicePromptEl.style.display = '';
         choicePromptEl.textContent = parsed.choicePrompt || 'Continue the story by choosing a path:';
     }
     ['a', 'b', 'c'].forEach(letter => {
         const btn = document.querySelector(`.btn-choice[data-choice="${letter}"]`);
         if (!btn) return;
         const childId = `${nodeId}${letter}`;
         const exists = allNodes.has(childId);
         const labelEl = btn.querySelector('.choice-label');
         const descEl = btn.querySelector('.choice-desc');
         const storyText = parsed.choices[letter] || null;
         if (exists) {
              labelEl.textContent = `✓ Already explored`;
         } else {
              labelEl.textContent = '';
         }
         if (storyText) {
              // Split into title (before em-dash) and body, render both as markdown
              const dashIdx = storyText.search(/\s*[—–-]{1,2}\s+/);
              let titleHtml, bodyHtml;
              if (dashIdx > 0) {
                  const title = storyText.slice(0, dashIdx).trim();
                  const body = storyText.slice(dashIdx).replace(/^\s*[—–-]+\s*/, '').trim();
                  // Wrap in ** so renderMarkdown bolds it if not already marked up
                  const titleMd = title.startsWith('**') ? title : `**${title.replace(/\*+/g, '')}**`;
                  titleHtml = renderMarkdown(titleMd);
                  bodyHtml = renderMarkdown(body);
              } else {
                  titleHtml = '';
                  bodyHtml = renderMarkdown(storyText);
              }
              // Set label to the title (bold short phrase)
              if (titleHtml && !exists) {
                  labelEl.innerHTML = titleHtml;
              } else if (!exists) {
                  labelEl.textContent = '';
              }
             if (descEl) {
                  descEl.innerHTML = bodyHtml;
                 descEl.style.display = '';
             } else {
                 const newDesc = document.createElement('span');
                 newDesc.className = 'choice-desc';
                  newDesc.innerHTML = bodyHtml;
                 btn.appendChild(newDesc);
             }
         } else {
             // Fallback: no parsed text — hide description if present
             if (descEl) descEl.style.display = 'none';
         }
     });
}

async function loadNode(nodeId) {
    const path = `${STORY_DIR}/${nodeId}.md`;
    log(`Loading node "${nodeId}" from "${path}"…`, 'info');
    timeStart(`loadNode_${nodeId}`);
    console.group(`[loadNode] Loading node "${nodeId}" from "${path}"…`);
    const content = await readFile(basePath, path);
    if (content == null) {
        showToast(`Node ${nodeId} not available yet.`, 'warning');
        log(`Node "${nodeId}" could not be read — file may not exist yet.`, 'warning');
        console.warn(`[loadNode] Could not read "${path}" — file may not exist yet.`);
        console.groupEnd();
        return;
    }
    console.log(`[loadNode] Read ${content.length} chars. Previous currentNode: "${currentNode}" → new: "${nodeId}".`);

    currentNode = nodeId;
    document.getElementById('current-node-path').textContent = path;
    document.getElementById('node-content').innerHTML = renderMarkdown(content);
    document.getElementById('choice-actions').style.display = 'block';
    setBadge('badge-node', 'done');

    document.querySelectorAll('.tree-node').forEach(el => {
        el.classList.toggle('active', el.dataset.nodeId === nodeId);
    });


     // Parse choices and detect end-state from markdown content
     const parsed = parseNodeContent(content);
     console.log(`[loadNode] Parsed node content — isEndState: ${parsed.isEndState} | choices:`, parsed.choices);
     log(`Node "${nodeId}" parsed — isEndState: ${parsed.isEndState}, choices found: ${Object.keys(parsed.choices).length}.`, 'info');

     applyParsedChoicesToUI(nodeId, parsed);

     const existingBranches = ['a', 'b', 'c'].filter(l => allNodes.has(`${nodeId}${l}`));
     log(`Node "${nodeId}" loaded in ${timeEnd(`loadNode_${nodeId}`)} — ${content.length} chars. Existing branches: [${existingBranches.join(', ') || 'none'}]. End-state: ${parsed.isEndState}.`, 'success');
     console.log(`[loadNode] Node "${nodeId}" ready. Existing branches: [${existingBranches.join(', ') || 'none'}]. End-state: ${parsed.isEndState}.`);
    console.groupEnd();

    // Auto-read if enabled
    stopSpeaking();
     console.log('[loadNode] After stopSpeaking() — isSpeaking:', isSpeaking, '| speechSynthesis.speaking:', ('speechSynthesis' in window) ? window.speechSynthesis.speaking : 'N/A');
    if (document.getElementById('auto-read').checked) {
        log(`Auto-read enabled — starting speech for node "${nodeId}".`, 'info');
        console.log(`[loadNode] Auto-read enabled — triggering speech for node "${nodeId}".`);
         setTimeout(() => speakText(content), 150);
    }
    // Load existing image (if any)
    await loadNodeImage(nodeId);
}
// -------------------------------------------------------------------------
// Text-to-speech
// -------------------------------------------------------------------------
function stripMarkdown(md) {
    if (!md) return '';
     const result = md
        // Remove code blocks
        .replace(/```[\s\S]*?```/g, '')
        // Remove inline code
        .replace(/`([^`]+)`/g, '$1')
        // Remove images ![alt](url)
        .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
        // Convert links [text](url) -> text
        .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
        // Remove headings markers
        .replace(/^#{1,6}\s+/gm, '')
        // Remove emphasis markers
        .replace(/(\*\*|__)(.*?)\1/g, '$2')
        .replace(/(\*|_)(.*?)\1/g, '$2')
        // Remove blockquote markers
        .replace(/^>\s?/gm, '')
        // Remove list markers
        .replace(/^\s*[-*+]\s+/gm, '')
        .replace(/^\s*\d+\.\s+/gm, '')
        // Collapse extra whitespace
        .replace(/\n{3,}/g, '\n\n')
        .trim();
     console.debug('[stripMarkdown] Input length:', md.length, '→ Output length:', result.length);
     return result;
}
function speakText(markdown) {
     console.log('[speakText] Called — markdown length:', markdown ? markdown.length : 0, '| isSpeaking:', isSpeaking);
     console.log('[speakText] speechSynthesis state — available:', 'speechSynthesis' in window,
         '| speaking:', ('speechSynthesis' in window) ? window.speechSynthesis.speaking : 'N/A',
         '| pending:', ('speechSynthesis' in window) ? window.speechSynthesis.pending : 'N/A',
         '| paused:', ('speechSynthesis' in window) ? window.speechSynthesis.paused : 'N/A');
     log(`speakText() called — markdown: ${markdown ? markdown.length : 0} chars, isSpeaking: ${isSpeaking}.`, 'info');
     if (isSpeaking) {
         console.warn('[speakText] Already speaking — ignoring duplicate call. Call stopSpeaking() first.');
         log('speakText() called while already speaking — ignoring. Use stopSpeaking() first.', 'warning');
         return;
     }
    if (!('speechSynthesis' in window)) {
        showToast('Text-to-speech is not supported in this browser.', 'error');
        log('Speech synthesis not available in this browser.', 'error');
        console.error('[speakText] speechSynthesis not available.');
        return;
    }
    const text = stripMarkdown(markdown);
     console.log('[speakText] Stripped text length:', text.length, '| first 100 chars:', text.slice(0, 100));
    if (!text) {
        showToast('Nothing to read.', 'warning');
        log('speakText: stripped text is empty — nothing to read.', 'warning');
        console.warn('[speakText] Stripped text is empty — nothing to speak.');
        return;
    }
    try {
        window.speechSynthesis.cancel();
         console.log('[speakText] speechSynthesis.cancel() called. State — speaking:', window.speechSynthesis.speaking, '| pending:', window.speechSynthesis.pending, '| paused:', window.speechSynthesis.paused);
          // Chrome bug: speak() called too soon after cancel() is silently ignored.
          // Use a short delay to let the cancel settle before queuing chunks.
          setTimeout(() => doSpeakChunked(text), 100);
    } catch (e) {
        log(`Speech failed: ${e.message}`, 'error');
        console.error('[speakText]', e);
    }
}
// Chrome Web Speech API silently kills utterances longer than ~15 seconds.
// Work around this by splitting the text into sentence-sized chunks and
// queuing them as separate SpeechSynthesisUtterance objects.
function splitIntoChunks(text, maxLen = 200) {
     const sentences = text.match(/[^.!?\n]+[.!?\n]*/g) || [text];
     const chunks = [];
     let current = '';
     for (const sentence of sentences) {
         if (current.length + sentence.length > maxLen && current.length > 0) {
             chunks.push(current.trim());
             current = sentence;
         } else {
             current += sentence;
         }
     }
     if (current.trim()) chunks.push(current.trim());
     return chunks;
}
function doSpeakChunked(text) {
     console.log('[doSpeakChunked] state before queuing: speaking:', window.speechSynthesis.speaking, '| pending:', window.speechSynthesis.pending);
     const voice = getSelectedVoice();
     const chunks = splitIntoChunks(text, 200);
     console.log(`[doSpeakChunked] Queuing ${chunks.length} chunk(s) with voice "${voice ? voice.name : 'default'}".`);
     log(`Speaking ${chunks.length} chunk(s) with voice "${voice ? voice.name : 'browser default'}".`, 'info');
     // Mark as speaking immediately so the button updates before onstart fires.
     isSpeaking = true;
     updateReadAloudButton();
     chunks.forEach((chunk, idx) => {
         const utter = new SpeechSynthesisUtterance(chunk);
         utter.rate = 1.0;
         utter.pitch = 1.0;
         if (voice) {
             utter.voice = voice;
             utter.lang = voice.lang;
         }
         if (idx === 0) {
             utter.onstart = () => {
                 isSpeaking = true;
                 updateReadAloudButton();
                 console.log('[doSpeakChunked] Speech started (chunk 0).');
                 log('Speech playback started.', 'info');
             };
         }
         if (idx === chunks.length - 1) {
             utter.onend = () => {
                 isSpeaking = false;
                 updateReadAloudButton();
                 log('Speech playback finished.', 'info');
                 console.log('[doSpeakChunked] Speech ended (final chunk).');
             };
         }
         utter.onerror = (e) => {
             // Only clear isSpeaking on the last chunk or a non-interruption error.
             if (e.error && e.error !== 'interrupted' && e.error !== 'canceled') {
                 isSpeaking = false;
                 updateReadAloudButton();
                 log(`Speech error on chunk ${idx}: ${e.error}`, 'warning');
                 console.warn(`[doSpeakChunked] Speech error on chunk ${idx}:`, e.error);
             } else {
                 isSpeaking = false;
                 updateReadAloudButton();
                 console.log(`[doSpeakChunked] Chunk ${idx} cancelled/interrupted (expected):`, e.error);
             }
         };
         window.speechSynthesis.speak(utter);
     });
     console.log(`[doSpeakChunked] All ${chunks.length} chunk(s) queued. speaking:`, window.speechSynthesis.speaking, '| pending:', window.speechSynthesis.pending);
}

function stopSpeaking() {
     console.log('[stopSpeaking] Called — isSpeaking:', isSpeaking, '| speechSynthesis.speaking:', ('speechSynthesis' in window) ? window.speechSynthesis.speaking : 'N/A');
    if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
    }
    if (isSpeaking) log('Speech stopped by user or navigation.', 'info');
    if (isSpeaking) console.log('[stopSpeaking] Cancelling active speech.');
    isSpeaking = false;
    updateReadAloudButton();
}
function updateReadAloudButton() {
    const btn = document.getElementById('read-aloud');
    const label = document.getElementById('read-aloud-label');
    if (!btn || !label) return;
    if (isSpeaking) {
        btn.firstChild.textContent = '⏹ ';
        label.textContent = 'Stop Reading';
    } else {
        btn.firstChild.textContent = '🔊 ';
        label.textContent = 'Read Aloud';
    }
}
async function onReadAloudToggle() {
     console.log('[onReadAloudToggle] Called — isSpeaking:', isSpeaking, '| currentNode:', currentNode);
     log(`Read-aloud toggle clicked — isSpeaking: ${isSpeaking}, currentNode: "${currentNode}".`, 'info');
    if (isSpeaking) {
        stopSpeaking();
        return;
    }
    if (!currentNode) {
        showToast('Select a node first.', 'warning');
         console.warn('[onReadAloudToggle] Aborted — no current node.');
        return;
    }
    const path = `${STORY_DIR}/${currentNode}.md`;
     console.log('[onReadAloudToggle] Reading file:', path);
    const content = await readFile(basePath, path);
     console.log('[onReadAloudToggle] readFile result — content length:', content != null ? content.length : 'null (file not found)');
     log(`Read file "${path}" — result: ${content != null ? content.length + ' chars' : 'null'}.`, 'info');
    if (content == null) {
        showToast('Could not load node content.', 'warning');
         console.warn('[onReadAloudToggle] readFile returned null for path:', path);
        return;
    }
     // Capture the node at the time of the click; guard against navigation during the async readFile.
     const nodeAtClick = currentNode;
     console.log('[onReadAloudToggle] Scheduling speakText() in 150ms for node:', nodeAtClick, '(captured at click time)');
     log(`Scheduling speakText() for node "${nodeAtClick}" (${content.length} chars) in 150ms.`, 'info');
     setTimeout(() => {
         if (currentNode !== nodeAtClick) {
             console.warn(`[onReadAloudToggle] Node changed from "${nodeAtClick}" to "${currentNode}" during readFile — aborting speak.`);
             log(`Node changed during readFile (${nodeAtClick} → ${currentNode}) — aborting speak.`, 'warning');
             return;
         }
         if (isSpeaking) {
             console.warn('[onReadAloudToggle] isSpeaking=true when setTimeout fired — duplicate speak prevented.');
             log('Duplicate speakText() call prevented in setTimeout (isSpeaking already true).', 'warning');
             return;
         }
         speakText(content);
     }, 150);
}
// -------------------------------------------------------------------------
// Image generation
// -------------------------------------------------------------------------
function getImageOpForNode(nodeId) {
    // Initial node uses initial_image.md, branches use choice_{a|b|c}_image.md
    if (nodeId === INITIAL_NODE) return 'ops/initial_image.md';
    const lastChar = nodeId.charAt(nodeId.length - 1).toLowerCase();
    if (lastChar === 'a' || lastChar === 'b' || lastChar === 'c') {
        return `ops/choice_${lastChar}_image.md`;
    }
    return null;
}
async function loadNodeImage(nodeId) {
    const container = document.getElementById('node-image-container');
    const img = document.getElementById('node-image');
    const statusEl = document.getElementById('node-image-status');
    const btn = document.getElementById('generate-image');
    const label = document.getElementById('generate-image-label');
    const imgPath = `${STORY_DIR}/${nodeId}.png`;
    console.log(`[loadNodeImage] Checking for image at "${imgPath}"…`);
    statusEl.textContent = '';
    statusEl.className = 'status-msg';
    const exists = await fileExists(basePath, imgPath);
    if (exists) {
        // Cache-bust so newly generated images replace old ones
        const url = `${basePath}/${imgPath}?t=${Date.now()}`;
        img.src = url;
        container.style.display = 'block';
        label.textContent = 'Regenerate Image';
        log(`Image found for node "${nodeId}" — loaded from "${imgPath}".`, 'info');
        console.log(`[loadNodeImage] Image found for node "${nodeId}" — src: ${url}`);
    } else {
        img.removeAttribute('src');
        container.style.display = 'none';
        label.textContent = 'Generate Image';
        log(`No image found for node "${nodeId}" (${imgPath}).`, 'info');
        console.log(`[loadNodeImage] No image found for node "${nodeId}" at "${imgPath}".`);
    }
    btn.disabled = isGeneratingImage;
}
async function onGenerateImage() {
    if (!currentNode) {
        showToast('Select a node first.', 'warning');
        log('Generate image aborted: no current node selected.', 'warning');
        console.warn('[onGenerateImage] Aborted — no current node selected.');
        return;
    }
    log(`User requested image generation for node "${currentNode}".`, 'info');
    console.log(`[onGenerateImage] User requested image for node "${currentNode}".`);
    await generateImageForNode(currentNode);
}
function maybeAutoGenerateImage(nodeId) {
    const autoImageEl = document.getElementById('auto-image');
    if (autoImageEl && autoImageEl.checked) {
        log(`Auto-image enabled — triggering image generation for node "${nodeId}".`, 'info');
        console.log(`[maybeAutoGenerateImage] Auto-image enabled — firing for node "${nodeId}".`);
        // Fire-and-forget; status is reflected in UI
        generateImageForNode(nodeId).catch(err => {
            log(`Auto image generation failed: ${err.message}`, 'warning');
            console.warn(`[maybeAutoGenerateImage] Auto image generation failed for "${nodeId}":`, err);
        });
    } else {
        log(`Auto-image disabled — skipping image generation for node "${nodeId}".`, 'info');
        console.log(`[maybeAutoGenerateImage] Auto-image disabled — skipping node "${nodeId}".`);
    }
}
async function generateImageForNode(nodeId) {
    const opPath = getImageOpForNode(nodeId);
    if (!opPath) {
        showToast(`No image operation defined for node ${nodeId}.`, 'warning');
        log(`generateImageForNode: no op path for node "${nodeId}" — skipping.`, 'warning');
        console.warn(`[generateImageForNode] No op path for node "${nodeId}" — skipping.`);
        return;
    }
    const target = `${STORY_DIR}/${nodeId}.png`;
    const btn = document.getElementById('generate-image');
    const statusEl = document.getElementById('node-image-status');
    const container = document.getElementById('node-image-container');
    isGeneratingImage = true;
    btn.disabled = true;
    container.style.display = 'block';
    statusEl.textContent = `Generating image for node ${nodeId}...`;
    statusEl.className = 'status-msg info';
    log(`Generating image for node "${nodeId}" — op: ${opPath} → target: "${target}".`, 'info');
    timeStart(`image_${nodeId}`);
    console.group(`[generateImageForNode] Generating image for node "${nodeId}"…`);
    console.log(`[generateImageForNode] op: ${opPath} | target: ${target} | overrides:`, getModelOverrides());
    try {
        const taskId = await runDocOp(
            sessionId,
            opPath,
            target,
            getModelOverrides()
        );
        console.log(`[generateImageForNode] runDocOp() returned taskId:`, taskId);
        log(`Image DocOp started for node "${nodeId}" — task: ${formatTaskId(taskId)}.`, 'info');
        await waitForTask(basePath, target, 600000, (tgt, info) => {
            trackedSessions.set(tgt, info);
            linkManager.update(tgt, info);
            updateSessionLinks(tgt, info, getProxyUrl, 'node-links');
            console.debug(`[generateImageForNode] waitForTask progress — target: ${tgt} | status: ${info.status} | info:`, info);
            log(`Image task progress for "${tgt}": status=${info.status}.`, 'info');
        });
        log(`Image generated for node "${nodeId}" in ${timeEnd(`image_${nodeId}`)}.`, 'success');
        console.log(`[generateImageForNode] Image generation complete for node "${nodeId}".`);
        statusEl.textContent = 'Image ready.';
        statusEl.className = 'status-msg success';
        // Reload only if user is still viewing the same node
        if (currentNode === nodeId) {
            await loadNodeImage(nodeId);
        } else {
            log(`Image ready for "${nodeId}" but user has navigated to "${currentNode}" — skipping reload.`, 'info');
            console.log(`[generateImageForNode] Image ready for "${nodeId}" but user is now on "${currentNode}" — skipping reload.`);
        }
    } catch (e) {
        log(`Image generation failed for node "${nodeId}" after ${timeEnd(`image_${nodeId}`)}: ${e.message}`, 'error');
        console.error(`[generateImageForNode(${nodeId})]`, e);
        statusEl.textContent = `Error: ${e.message}`;
        statusEl.className = 'status-msg error';
        showToast(`Image failed: ${e.message}`, 'error');
    } finally {
        isGeneratingImage = false;
        if (currentNode === nodeId) {
            document.getElementById('generate-image').disabled = false;
        }
        console.groupEnd();
    }
}

// -------------------------------------------------------------------------
// Status updates
// -------------------------------------------------------------------------
function onStatusUpdate(target, taskInfo) {
    trackedSessions.set(target, taskInfo);
    linkManager.update(target, taskInfo);

    // Pick container based on target
    const containerId = target === `${STORY_DIR}/${INITIAL_NODE}.md`
        ? 'initial-links'
        : target === 'style.css'
            ? 'stylesheet-links'
            : 'node-links';
    updateSessionLinks(target, taskInfo, getProxyUrl, containerId);

    // Auto-refresh tree when a new node completes
    if (taskInfo.status === 'COMPLETED' && target.startsWith(`${STORY_DIR}/`)) {
        log(`Poller: task for "${target}" completed — refreshing tree.`, 'info');
        console.log(`[onStatusUpdate] Task COMPLETED for "${target}" — triggering tree refresh.`);
        refreshTree();
    } else if (taskInfo.status === 'COMPLETED' && target === 'style.css') {
        log(`Poller: stylesheet update completed.`, 'info');
        console.log('[onStatusUpdate] Stylesheet task COMPLETED.');
        setBadge('badge-stylesheet', 'done');
        setStatus('stylesheet-status', 'Stylesheet updated! Reload the page to see changes.', 'success');
    } else if (taskInfo.status === 'FAILED') {
        log(`Poller: task for "${target}" FAILED — ${taskInfo.error || 'no details'}.`, 'error');
        console.error(`[onStatusUpdate] Task FAILED for "${target}":`, taskInfo.error || taskInfo);
    }
}

// -------------------------------------------------------------------------
// Logging
// -------------------------------------------------------------------------
function formatTaskId(taskId) {
    // taskId may be a string id, or a JSON string / object containing a binary
    // "content" field (e.g. PNG bytes) which we must NOT dump into the log.
    if (taskId == null) return '';
    let obj = taskId;
    if (typeof taskId === 'string') {
        // If it looks like JSON, try to parse and sanitize; otherwise return as-is.
        const trimmed = taskId.trim();
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
            try {
                obj = JSON.parse(trimmed);
            } catch {
                return taskId.length > 200 ? taskId.slice(0, 200) + '…' : taskId;
            }
        } else {
            return taskId;
        }
    }
    if (typeof obj !== 'object') return String(obj);
    // Strip large/binary fields
    const safe = {};
    for (const [k, v] of Object.entries(obj)) {
        if (k === 'content') continue;
        if (typeof v === 'string' && v.length > 200) {
            safe[k] = v.slice(0, 200) + '…';
        } else {
            safe[k] = v;
        }
    }
    try {
        return JSON.stringify(safe);
    } catch {
        return '[unserializable task info]';
    }
}

function log(msg, type = 'info') {
    if (logger) {
        logger.log(msg, type);
    } else {
        console.log(`[${type}]`, msg);
    }
}
// -------------------------------------------------------------------------
// Stylesheet updater
// -------------------------------------------------------------------------
async function loadStylesheetInstructions() {
    log(`Loading stylesheet instructions from "${STYLESHEET_FILE}"…`, 'info');
    console.log(`[loadStylesheetInstructions] Reading "${STYLESHEET_FILE}"…`);
    const content = await readFile(basePath, STYLESHEET_FILE);
    if (content != null) {
        document.getElementById('stylesheet-instructions').value = content;
        setBadge('badge-stylesheet', 'done');
        log(`Stylesheet instructions loaded (${content.length} chars).`, 'info');
        console.log(`[loadStylesheetInstructions] Loaded ${content.length} chars.`);
    } else {
        log('No existing stylesheet instructions found.', 'info');
        console.log('[loadStylesheetInstructions] File not found — no existing instructions.');
    }
}
async function onUpdateStylesheet() {
    const instructions = document.getElementById('stylesheet-instructions').value.trim();
    if (!instructions) {
        setStatus('stylesheet-status', 'Please describe the changes you want.', 'error');
        log('Update stylesheet aborted: no instructions provided.', 'warning');
        console.warn('[onUpdateStylesheet] Aborted — no instructions provided.');
        return;
    }
    // Persist instructions to disk so the op can read them
    try {
        await writeFile(basePath, STYLESHEET_FILE, instructions);
        log(`Stylesheet instructions saved (${instructions.length} chars).`, 'info');
        console.log(`[onUpdateStylesheet] Instructions saved (${instructions.length} chars).`);
    } catch (e) {
        setStatus('stylesheet-status', `Could not save instructions: ${e.message}`, 'error');
        log(`Failed to save stylesheet instructions: ${e.message}`, 'error');
        console.error('[onUpdateStylesheet] Failed to save instructions:', e);
        return;
    }
    const btn = document.getElementById('update-stylesheet');
    btn.disabled = true;
    setBadge('badge-stylesheet', 'running');
    setStatus('stylesheet-status', 'Updating stylesheet…', 'info', 0);
    log('Starting stylesheet update — op: ops/update_stylesheet.md → target: style.css.', 'info');
    timeStart('updateStylesheet');
    console.group('[onUpdateStylesheet] Updating stylesheet…');
    console.log('[onUpdateStylesheet] op: ops/update_stylesheet.md | target: style.css | overrides:', getModelOverrides());
    try {
        const taskId = await runDocOp(
            sessionId,
            'ops/update_stylesheet.md',
            'style.css',
            getModelOverrides()
        );
        log(`DocOp started for stylesheet update — task: ${formatTaskId(taskId)}.`, 'info');
        console.log('[onUpdateStylesheet] runDocOp() returned taskId:', taskId);
        await waitForTask(basePath, 'style.css', 600000, (tgt, info) => {
            trackedSessions.set(tgt, info);
            linkManager.update(tgt, info);
            updateSessionLinks(tgt, info, getProxyUrl, 'stylesheet-links');
            log(`Task progress for "${tgt}": status=${info.status}.`, 'info');
            console.debug(`[onUpdateStylesheet] waitForTask progress — target: ${tgt} | status: ${info.status} | info:`, info);
        });
        log(`Stylesheet updated successfully in ${timeEnd('updateStylesheet')}.`, 'success');
        console.log('[onUpdateStylesheet] Stylesheet update complete.');
        setBadge('badge-stylesheet', 'done');
        setStatus('stylesheet-status', 'Stylesheet updated! Reload the page to see changes.', 'success');
        showToast('Stylesheet updated — reload to apply changes.', 'success', 6000);
    } catch (e) {
        log(`Stylesheet update failed after ${timeEnd('updateStylesheet')}: ${e.message}`, 'error');
        console.error('[onUpdateStylesheet]', e);
        setStatus('stylesheet-status', `Error: ${e.message}`, 'error');
        setBadge('badge-stylesheet', 'error');
        showToast(`Stylesheet update failed: ${e.message}`, 'error');
    } finally {
        btn.disabled = false;
        console.groupEnd();
    }
}
// -------------------------------------------------------------------------
// Immersive mode
// -------------------------------------------------------------------------
function toggleImmersive() {
    const body = document.body;
    const btn = document.getElementById('toggle-immersive');
    const isImmersive = body.classList.toggle('immersive');
    console.log('[toggleImmersive] Immersive mode →', isImmersive ? 'ON' : 'OFF');
    if (btn) {
        btn.textContent = isImmersive ? '✕' : '⛶';
        btn.title = isImmersive ? 'Exit immersive mode (Esc)' : 'Toggle immersive mode (F)';
    }
    // Scroll the node content into view when entering immersive mode
    if (isImmersive) {
        const card = document.getElementById('node-card');
        if (card) card.scrollTop = 0;
    }
    log(`Immersive mode ${isImmersive ? 'enabled' : 'disabled'}.`, 'info');
}