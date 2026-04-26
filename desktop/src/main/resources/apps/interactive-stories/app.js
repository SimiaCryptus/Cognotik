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

const { basePath, sessionId } = parseSessionUrl();

// Persistent state
const linkManager = createSessionLinkManager(getProxyUrl);
const trackedSessions = new Map();   // target -> taskInfo (kept for life of page)
let currentNode = null;              // currently displayed node id (e.g. "0", "0a")
let allNodes = new Set();            // every story node we know about
let logger = null;
let statusPoller = null;
let isSpeaking = false;
let isGeneratingImage = false;

// -------------------------------------------------------------------------
// Initialization
// -------------------------------------------------------------------------
document.addEventListener('DOMContentLoaded', init);

async function init() {
    try {
        logger = createBatchLogger('activity-log');
        log('Initializing...', 'info');

        if (!sessionId) {
            showToast('Could not determine session from URL.', 'error', 8000);
            return;
        }

        await initModels();
        initVoices();
        await loadIdea();
        await refreshTree();

        // Wire up handlers
        document.getElementById('save-idea').addEventListener('click', onSaveIdea);
        document.getElementById('start-story').addEventListener('click', onStartStory);
        document.getElementById('refresh-tree').addEventListener('click', refreshTree);
        document.getElementById('clear-log').addEventListener('click', () => logger.clear());
        document.querySelectorAll('.btn-choice').forEach(btn => {
            btn.addEventListener('click', () => onChoice(btn.dataset.choice));
        });
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
         document.getElementById('read-aloud').addEventListener('click', onReadAloudToggle);
         const autoReadEl = document.getElementById('auto-read');
         autoReadEl.checked = localStorage.getItem(AUTO_READ_KEY) === 'true';
         autoReadEl.addEventListener('change', () => {
             localStorage.setItem(AUTO_READ_KEY, autoReadEl.checked ? 'true' : 'false');
         });
          // Image controls
          document.getElementById('generate-image').addEventListener('click', onGenerateImage);
          const autoImageEl = document.getElementById('auto-image');
          autoImageEl.checked = localStorage.getItem(AUTO_IMAGE_KEY) === 'true';
          autoImageEl.addEventListener('change', () => {
              localStorage.setItem(AUTO_IMAGE_KEY, autoImageEl.checked ? 'true' : 'false');
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

        // If initial node already exists, load it
        if (await fileExists(basePath, `${STORY_DIR}/${INITIAL_NODE}.md`)) {
            setBadge('badge-tree', 'done');
            if (!currentNode) await loadNode(INITIAL_NODE);
        }

        log('Ready.', 'success');
    } catch (err) {
        console.error(err);
        log(`Init error: ${err.message}`, 'error');
        showToast(`Init error: ${err.message}`, 'error');
    }

    window.addEventListener('error', e => log(`Error: ${e.message}`, 'error'));
    window.addEventListener('unhandledrejection', e => log(`Unhandled: ${e.reason}`, 'error'));
}

// -------------------------------------------------------------------------
// Models
// -------------------------------------------------------------------------
async function initModels() {
    try {
        const available = await loadApiProviders();
        const selects = MODEL_KEYS
             .map(k => {
                 const idMap = { smartModel: 'smart-model', fastModel: 'fast-model', imageModel: 'image-model' };
                 return document.getElementById(idMap[k]);
             })
            .filter(Boolean);
        const saved = loadModelSelections(APP_PREFIX, MODEL_KEYS);
        populateModelDropdowns(available, selects, saved);
        log('Models loaded.', 'info');
    } catch (e) {
        log(`Failed to load models: ${e.message}`, 'error');
    }
}

function persistModelSelections() {
    const selections = {
        smartModel: document.getElementById('smart-model').value,
         fastModel: document.getElementById('fast-model').value,
         imageModel: document.getElementById('image-model').value
    };
    saveModelSelections(APP_PREFIX, selections);
}
// -------------------------------------------------------------------------
// Voices (browser TTS)
// -------------------------------------------------------------------------
function initVoices() {
    const select = document.getElementById('voice-select');
    if (!select) return;
    if (!('speechSynthesis' in window)) {
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
        }
    };
    populate();
    // Voices often load asynchronously
    if (typeof window.speechSynthesis.onvoiceschanged !== 'undefined') {
        window.speechSynthesis.onvoiceschanged = populate;
    }
    select.addEventListener('change', () => {
        localStorage.setItem(VOICE_KEY, select.value);
    });
}
function getSelectedVoice() {
    if (!('speechSynthesis' in window)) return null;
    const select = document.getElementById('voice-select');
    const uri = select ? select.value : '';
    if (!uri) return null;
    const voices = window.speechSynthesis.getVoices() || [];
    return voices.find(v => v.voiceURI === uri) || null;
}


function getModelOverrides() {
    const out = {};
    const smart = document.getElementById('smart-model').value;
    const fast = document.getElementById('fast-model').value;
     const image = document.getElementById('image-model').value;
    if (smart) out.smartModel = smart;
    if (fast) out.fastModel = fast;
     if (image) out.imageModel = image;
    return out;
}

// -------------------------------------------------------------------------
// Story idea
// -------------------------------------------------------------------------
async function loadIdea() {
    const content = await readFile(basePath, IDEA_FILE);
    if (content != null) {
        document.getElementById('idea-editor').value = content;
        setBadge('badge-idea', 'done');
    }
}

async function onSaveIdea() {
    const content = document.getElementById('idea-editor').value.trim();
    if (!content) {
        setStatus('idea-status', 'Idea cannot be empty.', 'error');
        return;
    }
    try {
        await writeFile(basePath, IDEA_FILE, content);
        setStatus('idea-status', 'Idea saved.', 'success');
        setBadge('badge-idea', 'done');
        log('Idea saved.', 'success');
    } catch (e) {
        setStatus('idea-status', `Save failed: ${e.message}`, 'error');
        log(`Idea save failed: ${e.message}`, 'error');
    }
}

async function autoSaveIdea() {
    const content = document.getElementById('idea-editor').value.trim();
    if (!content) return;
    try {
        await writeFile(basePath, IDEA_FILE, content);
        setStatus('idea-status', 'Auto-saved.', 'info', 2000);
        setBadge('badge-idea', 'done');
    } catch (e) {
        // Silent on auto-save failures
    }
}

// -------------------------------------------------------------------------
// Begin / continue story
// -------------------------------------------------------------------------
async function onStartStory() {
    const ideaContent = document.getElementById('idea-editor').value.trim();
    if (!ideaContent) {
        showToast('Please write a story idea first.', 'warning');
        return;
    }

    // Save idea first
    await writeFile(basePath, IDEA_FILE, ideaContent);
    setBadge('badge-idea', 'done');

    const target = `${STORY_DIR}/${INITIAL_NODE}.md`;

    // Confirm if initial node already exists
    if (await fileExists(basePath, target)) {
        if (!confirm('An initial story node already exists. Regenerate it? This will overwrite the current root node (existing branches will remain on disk).')) {
            await loadNode(INITIAL_NODE);
            return;
        }
    }

    const startBtn = document.getElementById('start-story');
    startBtn.disabled = true;
    setBadge('badge-tree', 'running');
    setStatus('idea-status', 'Generating initial node...', 'info', 0);
    log('Starting story...', 'info');

    try {
        const taskId = await runDocOp(
            sessionId,
            'ops/initial_node.md',
            target,
            getModelOverrides()
        );
         log(`DocOp started: ${formatTaskId(taskId)}`, 'info');

        await waitForTask(basePath, target, 600000, (tgt, info) => {
            trackedSessions.set(tgt, info);
            linkManager.update(tgt, info);
            updateSessionLinks(tgt, info, getProxyUrl, 'initial-links');
        });

        log('Initial node generated.', 'success');
        setBadge('badge-tree', 'done');
        setStatus('idea-status', 'Story started!', 'success');
        await refreshTree();
        await loadNode(INITIAL_NODE);
         maybeAutoGenerateImage(INITIAL_NODE);
    } catch (e) {
        log(`Failed to start story: ${e.message}`, 'error');
        setStatus('idea-status', `Error: ${e.message}`, 'error');
        setBadge('badge-tree', 'error');
        showToast(`Failed: ${e.message}`, 'error');
    } finally {
        startBtn.disabled = false;
    }
}

async function onChoice(letter) {
    if (!currentNode) {
        showToast('Select a node first.', 'warning');
        return;
    }
    const target = `${STORY_DIR}/${currentNode}${letter}.md`;
    const opPath = `ops/choice_${letter}.md`;

    // If node already exists, just load it
    if (await fileExists(basePath, target)) {
        await loadNode(`${currentNode}${letter}`);
        return;
    }

    const buttons = document.querySelectorAll('.btn-choice');
    buttons.forEach(b => b.disabled = true);
    setBadge('badge-node', 'running');
    setStatus('choice-status', `Generating branch ${letter.toUpperCase()}...`, 'info', 0);
    log(`Generating branch: ${target}`, 'info');

    try {
        const taskId = await runDocOp(
            sessionId,
            opPath,
            target,
            getModelOverrides()
        );
         log(`DocOp started: ${formatTaskId(taskId)}`, 'info');

        await waitForTask(basePath, target, 600000, (tgt, info) => {
            trackedSessions.set(tgt, info);
            linkManager.update(tgt, info);
            updateSessionLinks(tgt, info, getProxyUrl, 'node-links');
        });

        log(`Branch ${letter.toUpperCase()} generated.`, 'success');
        setBadge('badge-node', 'done');
        setStatus('choice-status', `Branch ${letter.toUpperCase()} ready.`, 'success');
        await refreshTree();
         const newNode = `${currentNode}${letter}`;
         await loadNode(newNode);
         maybeAutoGenerateImage(newNode);
    } catch (e) {
        log(`Failed to generate branch ${letter}: ${e.message}`, 'error');
        setStatus('choice-status', `Error: ${e.message}`, 'error');
        setBadge('badge-node', 'error');
        showToast(`Failed: ${e.message}`, 'error');
    } finally {
        buttons.forEach(b => b.disabled = false);
    }
}

// -------------------------------------------------------------------------
// Tree management
// -------------------------------------------------------------------------
async function refreshTree() {
    try {
        const files = await listFiles(basePath, STORY_DIR);
        allNodes = new Set();
        (files || []).forEach(f => {
            const name = (f && (f.name || f.path)) ? (f.name || f.path).split('/').pop() : null;
            if (name && /\.md$/.test(name) && !name.startsWith('_')) {
                const id = name.replace(/\.md$/, '');
                if (/^0[a-c]*$/.test(id)) allNodes.add(id);
            }
        });
        renderTree();
    } catch (e) {
        log(`Tree refresh failed: ${e.message}`, 'warning');
    }
}

function renderTree() {
    const container = document.getElementById('story-tree');
    if (allNodes.size === 0) {
        container.innerHTML = '<p class="empty-state">No story started yet. Save your idea and click "Begin Story".</p>';
        return;
    }





     // Build a parent->children map so we can do an ordered DFS traversal.
     // Children are sorted alphabetically (a, b, c) so siblings appear in
     // a stable order under their parent.
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

     // DFS traversal producing { id, ancestorIsLast[] , isLast } per node,
     // where ancestorIsLast[i] tells us whether the ancestor at depth (i+1)
     // was the last child of its parent (so we draw a space) or not (draw │).
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

         // Build proper tree connectors:
         //   - For each ancestor level (except the immediate parent), draw "│ "
         //     if that ancestor had more siblings after it, otherwise "  ".
         //   - For the node itself, draw "└ " if it's the last child of its
         //     parent, otherwise "├ ".
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
async function loadNode(nodeId) {
    const path = `${STORY_DIR}/${nodeId}.md`;
    const content = await readFile(basePath, path);
    if (content == null) {
        showToast(`Node ${nodeId} not available yet.`, 'warning');
        return;
    }

    currentNode = nodeId;
    document.getElementById('current-node-path').textContent = path;
    document.getElementById('node-content').innerHTML = renderMarkdown(content);
    document.getElementById('choice-actions').style.display = 'block';
    setBadge('badge-node', 'done');

    // Update tree active highlight
    document.querySelectorAll('.tree-node').forEach(el => {
        el.classList.toggle('active', el.dataset.nodeId === nodeId);
    });

    // Update choice button labels - mark which branches already exist
    ['a', 'b', 'c'].forEach(letter => {
        const btn = document.querySelector(`.btn-choice[data-choice="${letter}"]`);
        const childId = `${nodeId}${letter}`;
        const exists = allNodes.has(childId);
        const label = btn.querySelector('.choice-label');
        if (exists) {
            label.textContent = `Visit Branch ${letter.toUpperCase()} ✓`;
        } else {
            label.textContent = `Generate Branch ${letter.toUpperCase()}`;
        }
    });

    log(`Loaded node: ${nodeId}`, 'info');
     // Auto-read if enabled
     stopSpeaking();
     if (document.getElementById('auto-read').checked) {
         speakText(content);
     }
     // Load existing image (if any)
     await loadNodeImage(nodeId);
}
// -------------------------------------------------------------------------
// Text-to-speech
// -------------------------------------------------------------------------
function stripMarkdown(md) {
     if (!md) return '';
     return md
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
}
function speakText(markdown) {
     if (!('speechSynthesis' in window)) {
         showToast('Text-to-speech is not supported in this browser.', 'error');
         return;
     }
     const text = stripMarkdown(markdown);
     if (!text) {
         showToast('Nothing to read.', 'warning');
         return;
     }
     try {
         window.speechSynthesis.cancel();
         const utter = new SpeechSynthesisUtterance(text);
         utter.rate = 1.0;
         utter.pitch = 1.0;
         const voice = getSelectedVoice();
         if (voice) {
             utter.voice = voice;
             utter.lang = voice.lang;
         }
         utter.onstart = () => {
             isSpeaking = true;
             updateReadAloudButton();
         };
         utter.onend = () => {
             isSpeaking = false;
             updateReadAloudButton();
         };
         utter.onerror = (e) => {
             isSpeaking = false;
             updateReadAloudButton();
             // 'interrupted' fires when we cancel intentionally — don't log as error
             if (e.error && e.error !== 'interrupted' && e.error !== 'canceled') {
                 log(`Speech error: ${e.error}`, 'warning');
             }
         };
         window.speechSynthesis.speak(utter);
     } catch (e) {
         log(`Speech failed: ${e.message}`, 'error');
     }
}
function stopSpeaking() {
     if ('speechSynthesis' in window) {
         window.speechSynthesis.cancel();
     }
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
     if (isSpeaking) {
         stopSpeaking();
         return;
     }
     if (!currentNode) {
         showToast('Select a node first.', 'warning');
         return;
     }
     const path = `${STORY_DIR}/${currentNode}.md`;
     const content = await readFile(basePath, path);
     if (content == null) {
         showToast('Could not load node content.', 'warning');
         return;
     }
     speakText(content);
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
     statusEl.textContent = '';
     statusEl.className = 'status-msg';
     const exists = await fileExists(basePath, imgPath);
     if (exists) {
         // Cache-bust so newly generated images replace old ones
         const url = `${basePath}/${imgPath}?t=${Date.now()}`;
         img.src = url;
         container.style.display = 'block';
         label.textContent = 'Regenerate Image';
     } else {
         img.removeAttribute('src');
         container.style.display = 'none';
         label.textContent = 'Generate Image';
     }
     btn.disabled = isGeneratingImage;
}
async function onGenerateImage() {
     if (!currentNode) {
         showToast('Select a node first.', 'warning');
         return;
     }
     await generateImageForNode(currentNode);
}
function maybeAutoGenerateImage(nodeId) {
     const autoImageEl = document.getElementById('auto-image');
     if (autoImageEl && autoImageEl.checked) {
         // Fire-and-forget; status is reflected in UI
         generateImageForNode(nodeId).catch(err => {
             log(`Auto image generation failed: ${err.message}`, 'warning');
         });
     }
}
async function generateImageForNode(nodeId) {
     const opPath = getImageOpForNode(nodeId);
     if (!opPath) {
         showToast(`No image operation defined for node ${nodeId}.`, 'warning');
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
     log(`Generating image: ${target}`, 'info');
     try {
         const taskId = await runDocOp(
             sessionId,
             opPath,
             target,
             getModelOverrides()
         );
         log(`Image DocOp started: ${formatTaskId(taskId)}`, 'info');
         await waitForTask(basePath, target, 600000, (tgt, info) => {
             trackedSessions.set(tgt, info);
             linkManager.update(tgt, info);
             updateSessionLinks(tgt, info, getProxyUrl, 'node-links');
         });
         log(`Image generated for node ${nodeId}.`, 'success');
         statusEl.textContent = 'Image ready.';
         statusEl.className = 'status-msg success';
         // Reload only if user is still viewing the same node
         if (currentNode === nodeId) {
             await loadNodeImage(nodeId);
         }
     } catch (e) {
         log(`Image generation failed for ${nodeId}: ${e.message}`, 'error');
         statusEl.textContent = `Error: ${e.message}`;
         statusEl.className = 'status-msg error';
         showToast(`Image failed: ${e.message}`, 'error');
     } finally {
         isGeneratingImage = false;
         if (currentNode === nodeId) {
             document.getElementById('generate-image').disabled = false;
         }
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
        : 'node-links';
    updateSessionLinks(target, taskInfo, getProxyUrl, containerId);

    // Auto-refresh tree when a new node completes
    if (taskInfo.status === 'COMPLETED' && target.startsWith(`${STORY_DIR}/`)) {
        refreshTree();
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
// Immersive mode
// -------------------------------------------------------------------------
function toggleImmersive() {
     const body = document.body;
     const btn = document.getElementById('toggle-immersive');
     const isImmersive = body.classList.toggle('immersive');
     if (btn) {
         btn.textContent = isImmersive ? '✕' : '⛶';
         btn.title = isImmersive ? 'Exit immersive mode (Esc)' : 'Toggle immersive mode (F)';
     }
     // Scroll the node content into view when entering immersive mode
     if (isImmersive) {
         const card = document.getElementById('node-card');
         if (card) card.scrollTop = 0;
     }
}