/**
 * core.js — runtime counterpart of `common.schema.ts`.
 *
 * Dependency-free primitives shared by drafts / annotations / revision items:
 * ids, hashing, prioritization vocabulary, text anchoring and diffing.
 */

export const SCHEMA_VERSION = 1;

/** Fixed id of the main article draft. Artifact drafts use `output:<file>`. */
export const ARTICLE_DRAFT_ID = 'article';
export const artifactDraftId = target => `output:${target}`;

export const nowIso = () => new Date().toISOString();

export const newId = (prefix = 'id') => {
    const rand = typeof globalThis.crypto?.randomUUID === 'function'
        ? globalThis.crypto.randomUUID()
        : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    return `${prefix}_${rand}`;
};

/** Cheap, stable, non-cryptographic content hash (FNV-1a, hex). */
export function contentHash(input) {
    let h = 0x811c9dc5;
    const text = String(input ?? '');
    for (let i = 0; i < text.length; i++) {
        h ^= text.charCodeAt(i);
        h = Math.imul(h, 0x01000193);
    }
    return (h >>> 0).toString(16).padStart(8, '0');
}

export const countWords = md => (md?.trim() ? md.trim().split(/\s+/).length : 0);

export const esc = value => String(value ?? '').replace(/[&<>"']/g, c => (
    {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]
));

export const escapeRegExp = value => String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/* --------------------------------------------------------------------- *
 * Vocabulary
 * --------------------------------------------------------------------- */

/** Unified run-status vocabulary used by every badge in the UI. */
export const RUN_STATES = [
    'idle', 'queued', 'running', 'streaming', 'ready', 'stale', 'error', 'cancelled', 'skipped'
];

export const SEVERITIES = ['blocker', 'major', 'minor', 'nit'];
export const EFFORTS = ['trivial', 'small', 'medium', 'large'];
export const PRIORITIES = ['auto', 'p0', 'p1', 'p2', 'p3'];

export const SEVERITY_WEIGHT = {blocker: 100, major: 60, minor: 30, nit: 10};
export const EFFORT_DISCOUNT = {trivial: 1.0, small: 0.9, medium: 0.75, large: 0.5};
export const PRIORITY_WEIGHT = {p0: 1000, p1: 750, p2: 500, p3: 250};
export const SEVERITY_RANK = {blocker: 0, major: 1, minor: 2, nit: 3};

export const HUMAN_AUTHOR = Object.freeze({kind: 'human', name: 'you'});
export const agentAuthor = (modelId, lensId) => ({
    kind: 'agent', name: modelId || 'agent', modelId, lensId
});

/* --------------------------------------------------------------------- *
 * Targets
 * --------------------------------------------------------------------- */

export const DOCUMENT_TARGET = Object.freeze({scope: 'document'});

export function describeTarget(target) {
    if (!target) return 'whole article';
    if (target.scope === 'document') return 'whole article';
    if (target.scope === 'section') return target.headingPath.join(' › ');
    const {headingPath = [], quote = ''} = target.anchor ?? {};
    const where = headingPath.length ? `${headingPath.join(' › ')}: ` : '';
    const snippet = quote.length > 80 ? `${quote.slice(0, 77)}…` : quote;
    return `${where}“${snippet}”`;
}

export function targetSortKey(target) {
    if (target?.scope === 'range') return target.anchor?.start ?? Number.MAX_SAFE_INTEGER - 1;
    if (target?.scope === 'section') return Number.MAX_SAFE_INTEGER - 1;
    return Number.MAX_SAFE_INTEGER;
}

export const isOrphanedTarget = target =>
    target?.scope === 'range' && target.anchorState === 'orphaned';

/* --------------------------------------------------------------------- *
 * Text anchoring
 * --------------------------------------------------------------------- */

const ANCHOR_CONTEXT = 48;

/** Heading breadcrumbs (outermost first) in effect at `index`. */
export function headingPathAt(content, index) {
    const path = [];
    const re = /^(#{1,6})[ \t]+(.+)$/gm;
    let match;
    while ((match = re.exec(content)) !== null) {
        if (match.index >= index) break;
        const level = match[1].length;
        path.length = Math.min(path.length, level - 1);
        path[level - 1] = match[2].trim();
    }
    return path.filter(Boolean);
}

export function makeAnchor(content, start, end) {
    return {
        headingPath: headingPathAt(content, start),
        quote: content.slice(start, end),
        prefix: content.slice(Math.max(0, start - ANCHOR_CONTEXT), start),
        suffix: content.slice(end, end + ANCHOR_CONTEXT),
        start,
        end
    };
}

/**
 * Revision-tolerant anchor resolution.
 *   1. unique exact quote            -> anchored
 *   2. prefix+quote+suffix           -> anchored
 *   3. whitespace-flexible match     -> relocated
 *   4. heading fallback              -> relocated
 *   5. nothing                       -> orphaned
 */
export function resolveAnchor(content, anchor) {
    if (!content || !anchor?.quote) return {state: 'orphaned'};
    const quote = anchor.quote;

    const first = content.indexOf(quote);
    if (first !== -1) {
        const second = content.indexOf(quote, first + 1);
        if (second === -1) return {state: 'anchored', start: first, end: first + quote.length};
        const ctx = `${anchor.prefix ?? ''}${quote}${anchor.suffix ?? ''}`;
        const ctxAt = content.indexOf(ctx);
        if (ctxAt !== -1) {
            const start = ctxAt + (anchor.prefix?.length ?? 0);
            return {state: 'anchored', start, end: start + quote.length};
        }
        return {state: 'relocated', start: first, end: first + quote.length};
    }

    const flexible = new RegExp(escapeRegExp(quote.trim()).replace(/\\?\s+/g, '\\s+'), 'i');
    const fuzzy = flexible.exec(content);
    if (fuzzy) return {state: 'relocated', start: fuzzy.index, end: fuzzy.index + fuzzy[0].length};

    const heading = anchor.headingPath?.[anchor.headingPath.length - 1];
    if (heading) {
        const re = new RegExp(`^#{1,6}[ \\t]+${escapeRegExp(heading)}\\s*$`, 'm');
        const at = re.exec(content);
        if (at) return {state: 'relocated', start: at.index, end: at.index + at[0].length};
    }
    return {state: 'orphaned'};
}

/** Recompute a target against new content, never dropping it. */
export function reanchorTarget(target, content) {
    if (target?.scope !== 'range') return target;
    const resolved = resolveAnchor(content, target.anchor);
    if (resolved.state === 'orphaned') {
        return {...target, anchorState: 'orphaned'};
    }
    return {
        ...target,
        anchorState: resolved.state,
        anchor: {...target.anchor, start: resolved.start, end: resolved.end}
    };
}

/* --------------------------------------------------------------------- *
 * Diffing (word-level with a line-level fallback for huge documents)
 * --------------------------------------------------------------------- */

const MAX_DP_CELLS = 4_000_000;

const tokenizeWords = text => text.match(/\n|[ \t]+|[^\s]+/g) ?? [];
const tokenizeLines = text => text.split(/(?<=\n)/);

function lcsDiff(a, b) {
    const n = a.length;
    const m = b.length;
    const width = m + 1;
    const dp = new Int32Array((n + 1) * width);
    for (let i = n - 1; i >= 0; i--) {
        for (let j = m - 1; j >= 0; j--) {
            dp[i * width + j] = a[i] === b[j]
                ? dp[(i + 1) * width + j + 1] + 1
                : Math.max(dp[(i + 1) * width + j], dp[i * width + j + 1]);
        }
    }
    const out = [];
    const push = (type, text) => {
        const last = out[out.length - 1];
        if (last && last.type === type) last.text += text;
        else out.push({type, text});
    };
    let i = 0;
    let j = 0;
    while (i < n && j < m) {
        if (a[i] === b[j]) push('eq', a[i++]), j++;
        else if (dp[(i + 1) * width + j] >= dp[i * width + j + 1]) push('del', a[i++]);
        else push('add', b[j++]);
    }
    while (i < n) push('del', a[i++]);
    while (j < m) push('add', b[j++]);
    return out;
}

/** @returns {{type:'eq'|'add'|'del', text:string}[]} */
export function diffText(oldText = '', newText = '') {
    let a = tokenizeWords(oldText);
    let b = tokenizeWords(newText);
    if ((a.length + 1) * (b.length + 1) > MAX_DP_CELLS) {
        a = tokenizeLines(oldText);
        b = tokenizeLines(newText);
    }
    if ((a.length + 1) * (b.length + 1) > MAX_DP_CELLS) {
        return [{type: 'del', text: oldText}, {type: 'add', text: newText}];
    }
    return lcsDiff(a, b);
}

/** Render a diff as HTML. `onlyChanged` collapses long unchanged runs. */
export function renderDiffHtml(oldText, newText, {onlyChanged = false} = {}) {
    const parts = diffText(oldText, newText);
    const html = parts.map((part, index) => {
        if (part.type === 'add') return `<ins class="diff-add">${esc(part.text)}</ins>`;
        if (part.type === 'del') return `<del class="diff-del">${esc(part.text)}</del>`;
        if (!onlyChanged || part.text.length < 320) return esc(part.text);
        const head = index === 0 ? '' : esc(part.text.slice(0, 120));
        const tail = index === parts.length - 1 ? '' : esc(part.text.slice(-120));
        return `${head}<span class="diff-gap">  ⋯ unchanged ⋯  </span>${tail}`;
    }).join('');
    return `<pre class="diff-view">${html}</pre>`;
}

export function diffStats(oldText, newText) {
    let added = 0;
    let removed = 0;
    for (const part of diffText(oldText, newText)) {
        if (part.type === 'add') added += countWords(part.text);
        if (part.type === 'del') removed += countWords(part.text);
    }
    return {added, removed};
}

export const formatRevisionRef = ref => (ref ? `${ref.draftId}@v${ref.revision}` : '—');
export const sameRevision = (a, b) => !!a && !!b && a.draftId === b.draftId && a.revision === b.revision;