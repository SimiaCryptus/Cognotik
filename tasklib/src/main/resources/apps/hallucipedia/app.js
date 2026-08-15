/**
 * Hallucipedia — a wikipedia that does not exist until you look at it.
 *
 * Navigation model:
 *   location.hash === '#/<path>'   where <path> is relative to articles/
 *   articles/<path> is the file on disk in the session workspace.
 *
 * When a path has no (or an empty) file behind it we create the file and run
 * spec/main.op.md against it via DocOps, then render the result.
 */
import {getMenuContext, initMenu} from '/app/menu.js';
import {createStatusPoller, runDocOp, waitForTask} from '/app/docops.js';
import {readFile, writeFile} from '/app/fileIO.js';
import {
    loadApiProviders,
    loadModelSelections,
    populateModelDropdowns,
    saveModelSelections
} from '/app/models.js';

/* ------------------------------------------------------------------ config */

const ARTICLES_DIR = 'articles';
const LINKS_FILE = 'links.json';
const MAIN_OP = 'spec/main.op.md';
const OPS_DIR = 'control/ops';
const NOTES_FILE = 'control/notes.md';
const ILLUSTRATE_OP = 'spec/illustrate.op.md';
const SEARCH_OP = 'spec/search.op.md';
const SETUP_NOTES_OP = 'spec/setup.op.md';
const SETUP_ROOT_OP = 'spec/front_page.op.md';
const SEED_FILE = 'control/seed.md';
const SETUP_FLAG = 'control/setup.json';
const SEARCH_DIR = 'search';
const HOME_PAGE = 'root.md';
const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
const STORE_PREFIX = 'hallucipedia';
const TASK_TIMEOUT_MS = 15 * 60 * 1000;

const ctx = getMenuContext();
const basePath = ctx.basePath || '.';
const sessionId = ctx.sessionId || '';

/* --------------------------------------------------------------------- dom */

const $ = id => document.getElementById(id);
const dom = {
    content: $('content'),
    pathForm: $('path-form'),
    pathInput: $('path-input'),
    status: $('status-bar'),
    statusText: $('status-text'),
    toc: $('toc'),
    outlinks: $('outlinks'),
    backlinks: $('backlinks'),
    allpages: $('allpages'),
    pagecount: $('pagecount'),
    illustrate: $('btn-illustrate'),
    regenerate: $('btn-regenerate'),
    settingsBtn: $('btn-settings'),
    settings: $('settings-panel'),
    home: $('nav-home'),
    back: $('nav-back'),
    forward: $('nav-forward'),
    searchForm: $('search-form'),
    searchInput: $('search-input'),
    searchResults: $('search-results'),
    setupBtn: $('btn-setup'),
    setupOverlay: $('setup-overlay'),
    setupForm: $('setup-form'),
    setupPrompt: $('setup-prompt'),
    setupExamples: $('setup-examples'),
    setupSkip: $('setup-skip'),
    setupStatus: $('setup-status')
};

/* ------------------------------------------------------------------- state */

let linksIndex = {version: 1, updated: null, pages: {}};
let currentPath = HOME_PAGE;
let navToken = 0;
let busy = false;
let busyMessage = '';
const runningTasks = new Map();

/* ------------------------------------------------------------------- utils */

const escapeHtml = s => String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function titleFromPath(path) {
    const base = String(path).split('/').pop().replace(/\.md$/i, '');
    return base.replace(/[_-]+/g, ' ').replace(/\s+/g, ' ').trim()
        .replace(/\b\w/g, c => c.toUpperCase());
}

function slugify(text) {
    return String(text).toLowerCase().trim()
        .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'section';
}

/** "the long thaw" -> "the_long_thaw" — the slug a search page is filed under. */
function querySlug(text) {
    return String(text).toLowerCase().trim()
        .replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 64) || 'everything';
}

const searchPathFor = q => `${SEARCH_DIR}/${querySlug(q)}.md`;
const isSearchPath = p => p.startsWith(`${SEARCH_DIR}/`) && !/(^|\/)index\.md$/i.test(p);
const queryFromPath = p =>
    p.slice(SEARCH_DIR.length + 1).replace(/\.md$/i, '').replace(/_+/g, ' ').trim();


/** Turn any link/typed value into a canonical path relative to articles/. */
function resolvePath(raw, fromPath) {
    let p = String(raw || '').trim();
    p = p.split('#')[0].split('?')[0];
    if (!p) return fromPath || HOME_PAGE;
    p = p.replace(/\s+/g, '_');

    let abs;
    if (p.startsWith('/')) {
        abs = p.slice(1);
    } else if (/^articles\//i.test(p)) {
        abs = p.replace(/^articles\//i, '');
    } else {
        const dir = String(fromPath || '').split('/').slice(0, -1).join('/');
        abs = dir ? `${dir}/${p}` : p;
    }
    abs = abs.replace(/^articles\//i, '');

    const out = [];
    for (const seg of abs.split('/')) {
        if (!seg || seg === '.') continue;
        if (seg === '..') {
            out.pop();
            continue;
        }
        out.push(seg);
    }
    let result = out.join('/');
    if (!result) return HOME_PAGE;
    if (!/\.[a-z0-9]{1,6}$/i.test(result)) result += '.md';
    return result;
}

/** Resolve an asset (image) reference into a fetchable URL. */
function assetUrl(src, fromPath) {
    if (/^(https?:)?\/\//i.test(src) || src.startsWith('data:')) return src;
    const rel = resolvePath(src, fromPath);
    return `${basePath}/${ARTICLES_DIR}/${rel}`;
}

const isExternal = href => /^(https?:|mailto:|tel:|data:|\/\/)/i.test(href);

/* -------------------------------------------------------------- links.json */

async function loadLinksIndex() {
    try {
        const raw = await readFile(basePath, LINKS_FILE);
        if (raw) {
            const parsed = JSON.parse(raw);
            if (parsed && parsed.pages && typeof parsed.pages === 'object') {
                linksIndex = {version: 1, updated: parsed.updated || null, pages: parsed.pages};
            }
        }
    } catch (e) {
        console.warn('links.json could not be read; starting a fresh index', e);
    }
}

async function persistLinksIndex() {
    linksIndex.updated = new Date().toISOString();
    try {
        await writeFile(basePath, LINKS_FILE, JSON.stringify(linksIndex, null, 2));
    } catch (e) {
        console.warn('Failed to persist links.json', e);
    }
}

async function recordPage(path, {title, summary, links, exists = true, illustrated} = {}) {
    const entry = linksIndex.pages[path] || {};
    entry.title = title || entry.title || titleFromPath(path);
    if (summary) entry.summary = summary;
    entry.exists = exists;
    entry.links = Array.isArray(links) ? Array.from(new Set(links)) : (entry.links || []);
    entry.updated = new Date().toISOString();
    if (illustrated !== undefined) entry.illustrated = illustrated;
    linksIndex.pages[path] = entry;

    for (const target of entry.links) {
        if (!linksIndex.pages[target]) {
            linksIndex.pages[target] = {
                title: titleFromPath(target), exists: false, links: [], updated: null
            };
        }
    }
    await persistLinksIndex();
}

const pageExists = path => Boolean(linksIndex.pages[path] && linksIndex.pages[path].exists);

function backlinksFor(path) {
    return Object.entries(linksIndex.pages)
        .filter(([p, e]) => p !== path && Array.isArray(e.links) && e.links.includes(path))
        .map(([p, e]) => ({path: p, title: e.title || titleFromPath(p), summary: e.summary || ''}))
        .sort((a, b) => a.path.localeCompare(b.path));
}

/* --------------------------------------------------------------- rendering */

function splitFrontMatter(md) {
    const m = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(md);
    if (!m) return {meta: {}, body: md};
    const meta = {};
    for (const line of m[1].split(/\r?\n/)) {
        const kv = /^([A-Za-z0-9_-]+)\s*:\s*(.*)$/.exec(line);
        if (kv) meta[kv[1]] = kv[2].trim().replace(/^["']|["']$/g, '');
    }
    return {meta, body: md.slice(m[0].length)};
}

function expandWikiLinks(md) {
    return md.replace(/\[\[([^\][|]+)(?:\|([^\]]+))?]]/g, (_m, target, label) => {
        const text = (label || target).trim();
        const href = target.trim().replace(/\s+/g, '_');
        return `[${text}](${/\.[a-z0-9]{1,6}$/i.test(href) ? href : href + '.md'})`;
    });
}

/**
 * Mask code, expand [[wiki links]], then lift math out of the markdown so
 * marked never mangles it. Math is re-inserted after sanitisation.
 */
function preprocess(md) {
    const code = [];
    const math = [];
    let s = md.replace(/```[\s\S]*?(?:```|$)|~~~[\s\S]*?(?:~~~|$)|`[^`\n]*`/g,
        m => `%%CODE${code.push(m) - 1}%%`);
    s = s.replace(
        /\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\]|\\\([\s\S]+?\\\)|\$(?!\s)(?:\\.|[^$\\\n])+?\$/g,
        m => `%%MATH${math.push(m) - 1}%%`);
    s = expandWikiLinks(s);
    s = s.replace(/%%CODE(\d+)%%/g, (_m, i) => code[Number(i)]);
    return {text: s, math};
}

function decorate(root, fromPath) {
    const outlinks = [];

    // mermaid fences -> <div class="mermaid">
    root.querySelectorAll('pre > code.language-mermaid').forEach(codeEl => {
        const div = document.createElement('div');
        div.className = 'mermaid';
        div.textContent = codeEl.textContent;
        codeEl.parentElement.replaceWith(div);
    });

    // headings get stable ids for the table of contents
    const used = new Set();
    root.querySelectorAll('h1,h2,h3,h4').forEach(h => {
        let id = h.id || slugify(h.textContent);
        let n = 2;
        while (used.has(id)) id = `${slugify(h.textContent)}-${n++}`;
        used.add(id);
        h.id = id;
    });

    // images live next to the article
    root.querySelectorAll('img[src]').forEach(img => {
        img.setAttribute('src', assetUrl(img.getAttribute('src'), fromPath));
        img.loading = 'lazy';
    });

    // links: external open in a new tab, internal become wiki routes
    root.querySelectorAll('a[href]').forEach(a => {
        const href = a.getAttribute('href') || '';
        if (isExternal(href)) {
            a.target = '_blank';
            a.rel = 'noopener noreferrer';
            a.classList.add('external');
            return;
        }
        if (href.startsWith('#')) return; // in-page anchor
        const target = resolvePath(href, fromPath);
        a.dataset.path = target;
        a.setAttribute('href', `#/${target}`);
        a.classList.add('wikilink');
        if (!pageExists(target)) {
            a.classList.add('new');
            a.title = `${titleFromPath(target)} — not written yet (click to generate)`;
        } else {
            a.title = titleFromPath(target);
        }
        outlinks.push(target);
    });

    return Array.from(new Set(outlinks));
}

function buildToc(root) {
    dom.toc.innerHTML = '';
    const heads = root.querySelectorAll('h2, h3');
    if (!heads.length) {
        dom.toc.innerHTML = '<p class="empty">—</p>';
        return;
    }
    heads.forEach(h => {
        const a = document.createElement('a');
        a.href = `#${h.id}`;
        a.textContent = h.textContent;
        a.className = h.tagName === 'H3' ? 'sub' : '';
        a.addEventListener('click', ev => {
            ev.preventDefault();
            h.scrollIntoView({behavior: 'smooth', block: 'start'});
        });
        dom.toc.appendChild(a);
    });
}

function renderBreadcrumbs(path) {
    const nav = document.createElement('nav');
    nav.className = 'breadcrumbs';
    const segments = path.split('/');
    let acc = '';
    segments.forEach((seg, i) => {
        const last = i === segments.length - 1;
        acc = acc ? `${acc}/${seg}` : seg;
        if (last) {
            const span = document.createElement('span');
            span.textContent = seg;
            nav.appendChild(span);
        } else {
            const a = document.createElement('a');
            a.href = `#/${acc}/index.md`;
            a.textContent = seg;
            nav.appendChild(a);
            nav.appendChild(document.createTextNode(' / '));
        }
    });
    return nav;
}

function renderArticle(markdown, path) {
    const {meta, body} = splitFrontMatter(markdown || '');
    const {text, math} = preprocess(body);

    let html = window.marked ? window.marked.parse(text) : `<pre>${escapeHtml(text)}</pre>`;
    if (window.DOMPurify) html = window.DOMPurify.sanitize(html, {ADD_ATTR: ['target', 'rel', 'id']});
    html = html.replace(/%%MATH(\d+)%%/g, (_m, i) => escapeHtml(math[Number(i)] ?? ''));

    const bodyEl = document.createElement('div');
    bodyEl.className = 'article-body';
    bodyEl.innerHTML = html;

    // hoist a leading <h1> into the page header
    let title = meta.title || '';
    const firstH1 = bodyEl.querySelector('h1');
    if (firstH1 && bodyEl.firstElementChild === firstH1) {
        if (!title) title = firstH1.textContent.trim();
        firstH1.remove();
    }
    if (!title) title = titleFromPath(path);

    const header = document.createElement('header');
    header.className = 'article-header';
    header.appendChild(renderBreadcrumbs(path));
    const h1 = document.createElement('h1');
    h1.textContent = title;
    header.appendChild(h1);
    if (meta.summary) {
        const p = document.createElement('p');
        p.className = 'summary';
        p.textContent = meta.summary;
        header.appendChild(p);
    }
    if (meta.tags) {
        const tags = document.createElement('div');
        tags.className = 'tags';
        meta.tags.replace(/^\[|]$/g, '').split(',').map(t => t.trim()).filter(Boolean)
            .forEach(t => {
                const s = document.createElement('span');
                s.textContent = t;
                tags.appendChild(s);
            });
        header.appendChild(tags);
    }

    dom.content.innerHTML = '';
    dom.content.append(header, bodyEl);
    dom.content.scrollTop = 0;

    const links = decorate(bodyEl, path);
    buildToc(bodyEl);

    try {
        if (window.Prism) window.Prism.highlightAllUnder(bodyEl);
    } catch (e) {
        console.warn('prism', e);
    }
    try {
        const nodes = bodyEl.querySelectorAll('.mermaid');
        if (window.mermaid && nodes.length) window.mermaid.run({nodes});
    } catch (e) {
        console.warn('mermaid', e);
    }
    try {
        if (window.MathJax && window.MathJax.typesetPromise) {
            window.MathJax.typesetPromise([bodyEl]).catch(e => console.warn('mathjax', e));
        }
    } catch (e) {
        console.warn('mathjax', e);
    }

    return {title, links, meta};
}

function renderPlaceholder(path, message) {
    dom.content.innerHTML = '';
    const box = document.createElement('div');
    box.className = 'placeholder';
    box.innerHTML =
        `<h1>${escapeHtml(titleFromPath(path))}</h1>` +
        `<p class="path">articles/${escapeHtml(path)}</p>` +
        `<p>${escapeHtml(message)}</p>` +
        `<div class="ghost"><span></span><span></span><span></span><span></span></div>`;
    dom.content.appendChild(box);
}

/* ----------------------------------------------------------------- sidebar */

/** Sidebar entries are labelled with the *full path* — titles collide (every
 *  folder overview is called "Index"), paths never do. */
function linkItem(path, title, extraClass) {
    const li = document.createElement('li');
    const a = document.createElement('a');
    a.href = `#/${path}`;
    a.textContent = path;
    a.title = title && title !== path
        ? `${title} — ${ARTICLES_DIR}/${path}`
        : `${ARTICLES_DIR}/${path}`;
    a.className = `wikilink pathlink ${extraClass || ''}`.trim();
    if (!pageExists(path)) a.classList.add('new');
    if (path === currentPath) a.classList.add('current');
    li.appendChild(a);
    return li;
}

function renderSidebar(outlinks) {
    dom.outlinks.innerHTML = '';
    (outlinks || []).forEach(p => dom.outlinks.appendChild(
        linkItem(p, (linksIndex.pages[p] || {}).title || titleFromPath(p))));
    if (!dom.outlinks.children.length) dom.outlinks.innerHTML = '<li class="empty">—</li>';

    dom.backlinks.innerHTML = '';
    backlinksFor(currentPath).forEach(b => dom.backlinks.appendChild(linkItem(b.path, b.title)));
    if (!dom.backlinks.children.length) dom.backlinks.innerHTML = '<li class="empty">—</li>';

    const all = Object.entries(linksIndex.pages)
        .sort((a, b) => Number(b[1].exists) - Number(a[1].exists) || a[0].localeCompare(b[0]));
    dom.allpages.innerHTML = '';
    all.forEach(([p, e]) => dom.allpages.appendChild(linkItem(p, e.title || titleFromPath(p))));
    if (!dom.allpages.children.length) dom.allpages.innerHTML = '<li class="empty">—</li>';
    const written = all.filter(([, e]) => e.exists).length;
    dom.pagecount.textContent = `${written}/${all.length}`;
}

/* ------------------------------------------------------------------ status */

function paintStatus() {
    const parts = [];
    if (busy && busyMessage) parts.push(busyMessage);
    for (const [target, state] of runningTasks) parts.push(`${target}: ${state}`);
    if (!parts.length) {
        dom.status.hidden = true;
        dom.statusText.textContent = '';
        return;
    }
    dom.status.hidden = false;
    dom.statusText.textContent = parts.join('   •   ');
}

function setBusy(on, message) {
    busy = on;
    busyMessage = on ? (message || 'Working…') : '';
    document.body.classList.toggle('busy', on);
    dom.illustrate.disabled = on;
    dom.regenerate.disabled = on;
    if (dom.setupStatus && !dom.setupOverlay.hidden) dom.setupStatus.textContent = busyMessage;
    paintStatus();
}

/* ------------------------------------------------------------------ models */

function collectModels() {
    const saved = loadModelSelections(STORE_PREFIX, MODEL_KEYS);
    const models = {};
    for (const key of MODEL_KEYS) if (saved[key]) models[key] = saved[key];
    return models;
}

async function initModels() {
    const selects = {
        smartModel: $('smart-model'),
        fastModel: $('fast-model'),
        imageModel: $('image-model')
    };
    const presentKeys = MODEL_KEYS.filter(k => selects[k]);
    const selectArray = presentKeys.map(k => selects[k]);
    const savedAll = loadModelSelections(STORE_PREFIX, MODEL_KEYS);
    const savedByKey = Object.fromEntries(presentKeys.map(k => [k, savedAll[k]]));

    const available = await loadApiProviders();
    populateModelDropdowns(available, selectArray, savedByKey);

    presentKeys.forEach(key => {
        selects[key].addEventListener('change', () => {
            const current = loadModelSelections(STORE_PREFIX, MODEL_KEYS);
            current[key] = selects[key].value;
            saveModelSelections(STORE_PREFIX, current);
        });
    });
}

/* -------------------------------------------------------------- generation */
/* Every article is written through its own docops node, generated just before
 * the run, so the operation can reference the pages that already exist. */
/** control/ops/<path>.op.md */
const opPathFor = path => `${OPS_DIR}/${path.replace(/\.md$/i, '')}.op.md`;
/** '../' repeated often enough to climb from a file back to the workspace root. */
const upTo = file => '../'.repeat(file.split('/').length - 1);

function describePage(path) {
    const e = linksIndex.pages[path] || {};
    const title = e.title || titleFromPath(path);
    return `\`${path}\` — **${title}**${e.summary ? ` — ${e.summary}` : ''}`;
}

/** Compose the docops node for one article: frontmatter + canon briefing. */
function buildOpNode(path) {
    const up = upTo(opPathFor(path));
    const title = titleFromPath(path);
    const dir = path.split('/').slice(0, -1).join('/');
    const entries = Object.entries(linksIndex.pages);
    const inbound = backlinksFor(path).filter(b => pageExists(b.path));
    const written = entries.filter(([p, e]) => e.exists && p !== path)
        .map(([p]) => p).sort((a, b) => a.localeCompare(b));
    const promised = entries.filter(([p, e]) => !e.exists && p !== path)
        .map(([p]) => p).sort((a, b) => a.localeCompare(b));
    const neighbours = written.filter(p => p.split('/').slice(0, -1).join('/') === dir);
    const related = [`${up}${NOTES_FILE}`, `${up}${MAIN_OP}`]
        .concat(inbound.map(b => `${up}${ARTICLES_DIR}/${b.path}`))
        .concat(neighbours.map(p => `${up}${ARTICLES_DIR}/${p}`));
    const L = [];
    L.push('---');
    L.push(`specifies: ${up}${ARTICLES_DIR}/${path}`);
    L.push('related:');
    Array.from(new Set(related)).slice(0, 40).forEach(r => L.push(`  - ${r}`));
    L.push('---');
    L.push('');
    L.push(`# Write: ${title}`);
    L.push('');
    L.push(`Target: \`${ARTICLES_DIR}/${path}\`${dir ? `, filed under \`${dir}/\`` : ''}.`);
    L.push('');
    L.push(`Write this article exactly as specified by [main.op.md](${up}${MAIN_OP}), under the`);
    L.push(`canon defined in [notes.md](${up}${NOTES_FILE}). Everything listed below already exists`);
    L.push('in this encyclopedia: treat it as canon, never contradict it, and reuse these exact');
    L.push('paths instead of minting a synonym for a concept that already has a page.');
    L.push('');
    L.push('## Why this page is being written');
    L.push('');
    if (inbound.length) {
        L.push('It is linked from:');
        L.push('');
        inbound.forEach(b => L.push(`- ${describePage(b.path)}`));
        L.push('');
        L.push('Read those pages and stay consistent with how they already describe the subject.');
    } else {
        L.push('Nothing links here yet — the reader typed the path directly. Derive the subject');
        L.push('from the path alone.');
    }
    L.push('');
    L.push('## Pages already written (canon)');
    L.push('');
    if (written.length) written.slice(0, 200).forEach(p => L.push(`- ${describePage(p)}`));
    else L.push('- None: this is the first article in the encyclopedia.');
    L.push('');
    L.push('## Paths already promised, not yet written');
    L.push('');
    L.push('Link to these by their existing path when they fit; they are the growth frontier.');
    L.push('');
    if (promised.length) promised.slice(0, 200).forEach(p => L.push(`- \`${p}\``));
    else L.push('- None yet.');
    L.push('');
    return L.join('\n');
}

/** Write the node and return its path (falling back to the plain spec). */
async function ensureOpNode(path) {
    const opPath = opPathFor(path);
    try {
        await writeFile(basePath, opPath, buildOpNode(path));
        return opPath;
    } catch (e) {
        console.warn('Could not write the op node; falling back to main.op.md', e);
        return MAIN_OP;
    }
}

/* ---------------------------------------------------------------- search */

/* Two halves: a local match over links.json (instant, for pages we know of)
 * and a simulated search — a results page hallucinated for the query. */


/** Rank known pages against a query; every term must appear somewhere. */
function searchPages(query) {
    const terms = String(query).toLowerCase().split(/\s+/).filter(Boolean);
    if (!terms.length) return [];
    const hits = [];
    for (const [p, e] of Object.entries(linksIndex.pages)) {
        if (isSearchPath(p)) continue;              // don't search the searches
        const title = e.title || titleFromPath(p);
        const hay = `${p} ${title} ${e.summary || ''}`.toLowerCase();
        let score = 0, all = true;
        for (const t of terms) {
            if (!hay.includes(t)) {
                all = false;
                break;
            }
            score += 1
                + (p.toLowerCase().includes(t) ? 3 : 0)
                + (title.toLowerCase().includes(t) ? 2 : 0);
        }
        if (!all) continue;
        if (e.exists) score += 5;
        hits.push({path: p, title, summary: e.summary || '', exists: Boolean(e.exists), score});
    }
    return hits
        .sort((a, b) => b.score - a.score || a.path.localeCompare(b.path))
        .slice(0, 12);
}

/** The docops node behind one simulated-search page. */
function buildSearchNode(path) {
    const query = queryFromPath(path);
    const up = upTo(opPathFor(path));
    const hits = searchPages(query);
    const existing = hits.filter(h => h.exists);
    const entries = Object.entries(linksIndex.pages).filter(([p]) => !isSearchPath(p));
    const written = entries.filter(([, e]) => e.exists).map(([p]) => p).sort();
    const promised = entries.filter(([, e]) => !e.exists).map(([p]) => p).sort();
    const related = [`${up}${NOTES_FILE}`, `${up}${SEARCH_OP}`]
        .concat(existing.map(h => `${up}${ARTICLES_DIR}/${h.path}`));
    const L = [];
    L.push('---');
    L.push(`specifies: ${up}${ARTICLES_DIR}/${path}`);
    L.push('related:');
    Array.from(new Set(related)).slice(0, 40).forEach(r => L.push(`  - ${r}`));
    L.push('---');
    L.push('');
    L.push(`# Search: ${query}`);
    L.push('');
    L.push(`The reader searched for **“${query}”**. Write the results page at`);
    L.push(`\`${ARTICLES_DIR}/${path}\` exactly as specified by [search.op.md](${up}${SEARCH_OP}),`);
    L.push(`under the canon defined in [notes.md](${up}${NOTES_FILE}).`);
    L.push('');
    L.push('## Matches among pages that already exist');
    L.push('');
    if (existing.length) {
        existing.forEach(h => L.push(`- ${describePage(h.path)}`));
        L.push('');
        L.push('List these first, at these exact paths, with snippets that agree with the summaries above.');
    } else {
        L.push('- None. Every result will be a page nobody has written yet.');
    }
    L.push('');
    L.push('## Everything written so far (canon)');
    L.push('');
    if (written.length) written.slice(0, 200).forEach(p => L.push(`- ${describePage(p)}`));
    else L.push('- Nothing yet.');
    L.push('');
    L.push('## Paths already promised, not yet written');
    L.push('');
    L.push('Prefer these over inventing a near-synonym for the same subject.');
    L.push('');
    if (promised.length) promised.slice(0, 200).forEach(p => L.push(`- \`${p}\``));
    else L.push('- None yet.');
    L.push('');
    return L.join('\n');
}

async function ensureSearchNode(path) {
    const opPath = opPathFor(path);
    try {
        await writeFile(basePath, opPath, buildSearchNode(path));
        return opPath;
    } catch (e) {
        console.warn('Could not write the search node; falling back to search.op.md', e);
        return SEARCH_OP;
    }
}

/** Run a docop against any workspace file (article, notes, front page…). */
async function runOpTarget(target, opPath, label) {
    //const target = `${ARTICLES_DIR}/${path}`;
    setBusy(true, `${label} ${target}…`);
    try {
        const taskId = await runDocOp(sessionId, opPath, target, collectModels());
        if (!taskId) console.warn('DocOps returned no task id for', target);
        await waitForTask(basePath, target, TASK_TIMEOUT_MS, (t, task) => {
            setBusy(true, `${label} ${t} — ${task && task.status ? task.status : 'working'}`);
        });
        return true;
    } catch (e) {
        console.error(e);
        return false;
    } finally {
        setBusy(false);
    }
}

async function runOp(path, opPath, label) {
    const ok = await runOpTarget(`${ARTICLES_DIR}/${path}`, opPath, label);
    if (!ok) renderPlaceholder(path, 'Generation failed — see the console for details.');
    return ok;
}


/** Create the file if missing, generate its op node, then run that node. */
async function generateArticle(path) {
    const target = `${ARTICLES_DIR}/${path}`;
    const search = isSearchPath(path);
    renderPlaceholder(path, search
        ? `Searching the unwritten for “${queryFromPath(path)}”…`
        : 'This page does not exist yet — hallucinating it now…');
    try {
        await writeFile(basePath, target, '');
    } catch (e) {
        console.warn('Could not pre-create the target file', e);
    }
    await recordPage(path, {exists: false, links: linksIndex.pages[path]?.links || []});
    renderSidebar([]);
    if (search) return runOp(path, await ensureSearchNode(path), 'Searching');
    const opPath = await ensureOpNode(path);
    return runOp(path, opPath, 'Writing');
}

/* ------------------------------------------------------------------ setup */

/* First run: a prompt from the reader is expanded by two docops into the
 * world bible and the front page. Everything else grows from those. */
function buildSetupNode(kind, prompt) {
    const opPath = `${OPS_DIR}/setup/${kind}.op.md`;
    const up = upTo(opPath);
    const notes = kind === 'notes';
    const target = notes ? NOTES_FILE : `${ARTICLES_DIR}/${HOME_PAGE}`;
    const spec = notes ? SETUP_NOTES_OP : SETUP_ROOT_OP;
    const related = [`${up}${SEED_FILE}`, `${up}${spec}`];
    if (!notes) related.push(`${up}${NOTES_FILE}`, `${up}${MAIN_OP}`);
    const L = ['---', `specifies: ${up}${target}`, 'related:'];
    Array.from(new Set(related)).forEach(r => L.push(`  - ${r}`));
    L.push('---', '');
    L.push(notes ? '# Define the world' : '# Write the front page');
    L.push('');
    L.push('The reader asked for this encyclopedia:');
    L.push('');
    String(prompt).trim().split(/\n+/).forEach(line => L.push(`> ${line.trim()}`));
    L.push('');
    if (notes) {
        L.push(`Expand that prompt into \`${NOTES_FILE}\`, exactly as specified by`);
        L.push(`[setup.op.md](${up}${spec}). Every later article is bound by this file, so be`);
        L.push('specific: fix the departments, the anchor dates, the units and a first vocabulary of');
        L.push('proper nouns, and state the tone in terms a writer can obey without asking questions.');
    } else {
        L.push(`Write \`${ARTICLES_DIR}/${HOME_PAGE}\` exactly as specified by`);
        L.push(`[front_page.op.md](${up}${spec}), under the canon just fixed in`);
        L.push(`[notes.md](${up}${NOTES_FILE}). Use the department folders that file names and no`);
        L.push('others, and leave the subject links unwritten — the front page is the seed of the');
        L.push('whole link graph.');
    }
    L.push('');
    return {opPath, target, text: L.join('\n')};
}

async function needsSetup() {
    try {
        const raw = await readFile(basePath, SETUP_FLAG);
        if (raw && raw.trim()) return false;
    } catch (e) { /* no marker yet */
    }
    return !Object.values(linksIndex.pages).some(e => e.exists);
}

async function markSetupDone(payload) {
    try {
        await writeFile(basePath, SETUP_FLAG,
            JSON.stringify({created: new Date().toISOString(), ...payload}, null, 2));
    } catch (e) {
        console.warn('Could not write the setup marker', e);
    }
}

function setSetupDisabled(on) {
    Array.from(dom.setupForm.querySelectorAll('button, textarea'))
        .forEach(el => {
            el.disabled = on;
        });
}

function showSetup() {
    dom.setupOverlay.hidden = false;
    dom.setupStatus.textContent = '';
    setTimeout(() => dom.setupPrompt.focus(), 0);
}

async function runSetup(prompt) {
    const seed = [
        '---',
        'kind: seed',
        `created: ${new Date().toISOString().slice(0, 10)}`,
        '---',
        '',
        '# Reader prompt',
        '',
        'The world this encyclopedia describes, as asked for by the reader:',
        '',
        ...prompt.trim().split(/\n+/).map(l => `> ${l.trim()}`),
        ''
    ].join('\n');
    try {
        await writeFile(basePath, SEED_FILE, seed);
    } catch (e) {
        console.error('Could not write the seed file', e);
        return false;
    }
    for (const kind of ['notes', 'root']) {
        const node = buildSetupNode(kind, prompt);
        try {
            await writeFile(basePath, node.opPath, node.text);
        } catch (e) {
            console.error('Could not write the setup node', e);
            return false;
        }
        const label = kind === 'notes' ? 'Defining the world —' : 'Writing the front page —';
        if (!await runOpTarget(node.target, node.opPath, label)) return false;
    }
    await markSetupDone({prompt});
    linksIndex = {version: 1, updated: null, pages: {}};   // a new world, a new graph
    await persistLinksIndex();
    return true;
}


/* ------------------------------------------------------------- navigation  */

function pathFromHash() {
    const raw = decodeURIComponent((location.hash || '').replace(/^#\/?/, ''));
    return resolvePath(raw || HOME_PAGE, '');
}

async function openPath(path, {force = false, push = true} = {}) {
    const token = ++navToken;
    currentPath = path;
    dom.pathInput.value = path;
    document.title = `${titleFromPath(path)} — Hallucipedia`;

    const desiredHash = `#/${path}`;
    if (push && location.hash !== desiredHash) {
        location.hash = desiredHash;   // triggers hashchange -> re-entry guarded below
    }

    let content = null;
    try {
        content = await readFile(basePath, `${ARTICLES_DIR}/${path}`);
    } catch (e) {
        console.warn('read failed', e);
    }
    if (token !== navToken) return;

    if (force || content === null || !content.trim()) {
        const ok = await generateArticle(path);
        if (token !== navToken) return;
        if (!ok) return;
        try {
            content = await readFile(basePath, `${ARTICLES_DIR}/${path}`);
        } catch (e) {
            content = null;
        }
        if (token !== navToken) return;
    }

    if (content === null || !content.trim()) {
        renderPlaceholder(path, 'The generator produced no content for this page. Try “Regenerate”.');
        return;
    }

    const {title, links, meta} = renderArticle(content, path);
    await recordPage(path, {title, summary: meta.summary, links, exists: true});
    if (token !== navToken) return;
    renderSidebar(links);
    // re-tint red links now that the index may have grown
    dom.content.querySelectorAll('a.wikilink[data-path]').forEach(a => {
        a.classList.toggle('new', !pageExists(a.dataset.path));
    });
}

function onHashChange() {
    const path = pathFromHash();
    if (path === currentPath && dom.content.children.length) return;
    openPath(path, {push: false});
}

/* ------------------------------------------------------------------ wiring */

dom.pathForm.addEventListener('submit', ev => {
    ev.preventDefault();
    openPath(resolvePath(dom.pathInput.value, currentPath));
});

dom.home.addEventListener('click', () => openPath(HOME_PAGE));
dom.back.addEventListener('click', () => history.back());
dom.forward.addEventListener('click', () => history.forward());

dom.regenerate.addEventListener('click', () => {
    if (confirm(`Rewrite articles/${currentPath} from scratch?`)) {
        openPath(currentPath, {force: true, push: false});
    }
});

dom.illustrate.addEventListener('click', async () => {
    const path = currentPath;
    const ok = await runOp(path, ILLUSTRATE_OP, 'Illustrating');
    if (!ok) return;
    let content = null;
    try {
        content = await readFile(basePath, `${ARTICLES_DIR}/${path}`);
    } catch (e) {
        console.warn(e);
    }
    if (content) {
        const {title, links, meta} = renderArticle(content, path);
        await recordPage(path, {title, summary: meta.summary, links, exists: true, illustrated: true});
        renderSidebar(links);
    }
});

dom.settingsBtn.addEventListener('click', () => {
    dom.settings.hidden = !dom.settings.hidden;
});

/* ------------------------------------------------------------ search wiring */
function hideSearchResults() {
    dom.searchResults.hidden = true;
    dom.searchResults.innerHTML = '';
}

function renderSearchResults(query) {
    const q = String(query || '').trim();
    if (!q) {
        hideSearchResults();
        return;
    }
    const list = document.createElement('ul');
    const hits = searchPages(q);
    hits.forEach(h => {
        const li = document.createElement('li');
        const a = document.createElement('a');
        a.href = `#/${h.path}`;
        a.dataset.path = h.path;
        a.className = `wikilink${h.exists ? '' : ' new'}`;
        const strong = document.createElement('strong');
        strong.textContent = h.title;
        const code = document.createElement('code');
        code.textContent = h.path;
        a.append(strong, code);
        li.appendChild(a);
        if (h.summary) {
            const p = document.createElement('p');
            p.textContent = h.summary;
            li.appendChild(p);
        }
        list.appendChild(li);
    });
    if (!hits.length) {
        const li = document.createElement('li');
        li.className = 'empty';
        li.textContent = 'Nothing in the index matches that.';
        list.appendChild(li);
    }
    const ask = document.createElement('li');
    ask.className = 'ask';
    const askLink = document.createElement('a');
    askLink.href = `#/${searchPathFor(q)}`;
    askLink.dataset.path = searchPathFor(q);
    askLink.className = 'wikilink';
    askLink.textContent = `🔍 Search the unwritten for “${q}”`;
    ask.appendChild(askLink);
    list.appendChild(ask);
    dom.searchResults.innerHTML = '';
    dom.searchResults.appendChild(list);
    dom.searchResults.hidden = false;
}

let searchTimer = 0;
dom.searchInput.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => renderSearchResults(dom.searchInput.value), 120);
});
dom.searchInput.addEventListener('focus', () => renderSearchResults(dom.searchInput.value));
dom.searchForm.addEventListener('submit', ev => {
    ev.preventDefault();
    const q = dom.searchInput.value.trim();
    if (!q) return;
    hideSearchResults();
    openPath(searchPathFor(q));
});
dom.searchResults.addEventListener('click', ev => {
    const a = ev.target.closest('a[data-path]');
    if (!a) return;
    ev.preventDefault();
    hideSearchResults();
    openPath(a.dataset.path);
});
document.addEventListener('click', ev => {
    if (!dom.searchResults.hidden && !ev.target.closest('.search-wrap')) hideSearchResults();
});
/* ------------------------------------------------------------- setup wiring */
dom.setupBtn.addEventListener('click', () => showSetup());
dom.setupExamples.addEventListener('click', ev => {
    const b = ev.target.closest('button[data-prompt]');
    if (!b) return;
    dom.setupPrompt.value = b.dataset.prompt;
    dom.setupPrompt.focus();
});
dom.setupSkip.addEventListener('click', async () => {
    dom.setupOverlay.hidden = true;
    await markSetupDone({skipped: true});
    if (!dom.content.children.length) await openPath(pathFromHash(), {push: false});
});
dom.setupForm.addEventListener('submit', async ev => {
    ev.preventDefault();
    const prompt = dom.setupPrompt.value.trim();
    if (!prompt) {
        dom.setupPrompt.focus();
        return;
    }
    setSetupDisabled(true);
    const ok = await runSetup(prompt);
    setSetupDisabled(false);
    if (!ok) {
        dom.setupStatus.textContent = 'Setup failed — see the console. You can try again.';
        return;
    }
    dom.setupOverlay.hidden = true;
    renderSidebar([]);
    await openPath(HOME_PAGE, {push: false});
});


// in-document navigation
dom.content.addEventListener('click', ev => {
    const a = ev.target.closest('a.wikilink[data-path]');
    if (!a) return;
    ev.preventDefault();
    openPath(a.dataset.path);
});

document.addEventListener('keydown', ev => {
    const el = document.activeElement;
    const typing = el && /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName);
    if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'k') {
        ev.preventDefault();
        dom.pathInput.focus();
        dom.pathInput.select();
        return;
    }
    if (ev.key === '/' && !typing) {
        ev.preventDefault();
        dom.searchInput.focus();
        dom.searchInput.select();
        return;
    }
    if (ev.key === 'Escape') {
        hideSearchResults();
        if (!dom.setupOverlay.hidden && !busy) dom.setupOverlay.hidden = true;
    }
});

window.addEventListener('hashchange', onHashChange);

/* ------------------------------------------------------------------- boot  */

const poller = createStatusPoller(basePath, (target, task) => {
    try {
        const state = String(task && task.status || '').toUpperCase();
        if (['RUNNING', 'PENDING', 'QUEUED', 'IN_PROGRESS'].includes(state)) runningTasks.set(target, state);
        else runningTasks.delete(target);
        paintStatus();
    } catch (e) {
        console.warn(e);
    }
}, 3000);

window.addEventListener('beforeunload', () => poller.stop());

(async function boot() {
    try {
        initMenu({
            appName: 'Hallucipedia',
            extraLinks: [{href: `#/${HOME_PAGE}`, label: 'Front page'}]
        });
    } catch (e) {
        console.warn('menu unavailable', e);
    }

    if (window.mermaid) {
        window.mermaid.initialize({startOnLoad: false, theme: 'neutral', securityLevel: 'strict'});
    }
    if (window.marked && window.marked.setOptions) {
        window.marked.setOptions({gfm: true, breaks: false});
    }

    await loadLinksIndex();
    renderSidebar([]);
    initModels().catch(e => console.warn('models unavailable', e));
    poller.start();

    if (!location.hash) location.hash = `#/${HOME_PAGE}`;
    if (await needsSetup()) {
        currentPath = pathFromHash();
        dom.pathInput.value = currentPath;
        renderPlaceholder(currentPath, 'Waiting for the world to be defined…');
        showSetup();
        return;
    }
    await openPath(pathFromHash(), {push: false});
})();