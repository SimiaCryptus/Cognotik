/**
 * revisions.js — runtime counterpart of `revisions.schema.ts` plus the
 * ingestion pass that turns a prose lens artifact into action items.
 *
 * The extractor here is deterministic and heuristic (no extra model call):
 * markdown list items under headings become `RevisionItem`s. Ingestion is
 * idempotent per `(lensId, artifactHash)` and preserves user-edited items via
 * `similarityKey` matching.
 */

import {
    DOCUMENT_TARGET,
    EFFORT_DISCOUNT,
    HUMAN_AUTHOR,
    PRIORITY_WEIGHT,
    SCHEMA_VERSION,
    SEVERITY_RANK,
    SEVERITY_WEIGHT,
    contentHash,
    describeTarget,
    isOrphanedTarget,
    newId,
    nowIso,
    reanchorTarget,
    resolveAnchor,
    targetSortKey
} from './core.js';

export const REVISION_ACTIONS = [
    'add', 'expand', 'cut', 'rewrite', 'restructure', 'reframe', 'clarify',
    'evidence', 'citation', 'counterargument', 'tone', 'title', 'note'
];

export const REVISION_STATUSES =
    ['proposed', 'accepted', 'rejected', 'deferred', 'applied', 'superseded'];
export const FEEDABLE_STATUSES = ['accepted'];

export const EXTRACTOR_VERSION = '1';

/* ------------------------------------------------------------------ *
 * Scoring / identity
 * ------------------------------------------------------------------ */

export function priorityScore(item) {
    if (item.priority && item.priority !== 'auto') return PRIORITY_WEIGHT[item.priority];
    const base = SEVERITY_WEIGHT[item.severity] ?? 30;
    const discount = EFFORT_DISCOUNT[item.effort] ?? 0.9;
    return Math.round(base * discount * (0.4 + 0.6 * (item.confidence ?? 0.6)));
}

export function similarityKeyFor({action, title, target}) {
    const normalizedTitle = title.toLowerCase()
        .replace(/[^a-z0-9 ]+/g, ' ')
        .split(/\s+/)
        .filter(w => w.length > 3)
        .sort()
        .join(' ');
    const where = target?.scope === 'range'
        ? target.anchor.quote.slice(0, 40).toLowerCase()
        : target?.scope === 'section'
            ? target.headingPath.join('/').toLowerCase()
            : 'document';
    return contentHash(`${action}|${where}|${normalizedTitle}`);
}

export const isFeedableItem = item => FEEDABLE_STATUSES.includes(item.status);
export const isOrphanedItem = item => isOrphanedTarget(item.target);

export function setItemStatus(item, status, opts = {}) {
    return {
        ...item,
        status,
        appliedInRevision: opts.appliedInRevision ?? item.appliedInRevision,
        statusNote: opts.note ?? item.statusNote,
        supersededById: opts.supersededById ?? item.supersededById,
        updatedAt: nowIso()
    };
}

/* ------------------------------------------------------------------ *
 * Hydration
 * ------------------------------------------------------------------ */

function buildTarget(extracted, articleContent) {
    const quote = extracted.targetQuote?.trim();
    if (quote && quote.length > 12) {
        const resolved = articleContent ? resolveAnchor(articleContent, {quote, headingPath: []}) : null;
        return {
            scope: 'range',
            anchorState: resolved?.state ?? 'anchored',
            anchor: {
                quote,
                prefix: '',
                suffix: '',
                headingPath: extracted.targetHeadingPath ?? [],
                start: resolved?.start,
                end: resolved?.end
            }
        };
    }
    if (extracted.targetHeadingPath?.length) {
        return {scope: 'section', headingPath: extracted.targetHeadingPath};
    }
    return DOCUMENT_TARGET;
}

export function hydrateItem(extracted, ctx) {
    const ts = nowIso();
    const target = buildTarget(extracted, ctx.articleContent);
    return {
        schemaVersion: SCHEMA_VERSION,
        id: newId('rev'),
        lensId: ctx.lensId,
        batchId: ctx.batchId,
        sourceRef: ctx.sourceRef,
        title: extracted.title.trim().slice(0, 160),
        rationale: extracted.rationale ?? '',
        evidenceQuote: extracted.evidenceQuote,
        action: extracted.action,
        target,
        suggestedText: extracted.suggestedText,
        severity: extracted.severity ?? 'minor',
        effort: extracted.effort ?? 'small',
        confidence: extracted.confidence ?? 0.6,
        priority: 'auto',
        tags: (extracted.tags ?? [])
            .map(t => t.toLowerCase().trim().replace(/\s+/g, '-'))
            .filter(t => /^[a-z0-9][a-z0-9\-_/]*$/.test(t)),
        status: 'proposed',
        similarityKey: similarityKeyFor({action: extracted.action, title: extracted.title, target}),
        mergedFromIds: [],
        annotationIds: [],
        userEdited: false,
        createdAt: ts,
        updatedAt: ts,
        createdBy: ctx.createdBy ?? HUMAN_AUTHOR
    };
}

/* ------------------------------------------------------------------ *
 * Heuristic extraction
 * ------------------------------------------------------------------ */

const ACTION_PATTERNS = [
    [/\b(counter-?argument|objection|rebut|steel-?man)\b/i, 'counterargument'],
    [/\b(cite|citation|sourc\w*|attribut\w*)\b/i, 'citation'],
    [/\b(evidence|data|statistic\w*|benchmark|worked example)\b/i, 'evidence'],
    [/\b(clarif\w*|defin\w*|disambiguat\w*|explain)\b/i, 'clarify'],
    [/\b(restructur\w*|reorder|reorganiz\w*|split|merge|move)\b/i, 'restructure'],
    [/\b(reframe|framing|thesis)\b/i, 'reframe'],
    [/\b(rewrite|rephrase|reword|tighten)\b/i, 'rewrite'],
    [/\b(cut|remove|delete|trim|drop|redundant)\b/i, 'cut'],
    [/\b(expand|deepen|elaborat\w*|develop|explore)\b/i, 'expand'],
    [/\b(tone|voice|register|audience)\b/i, 'tone'],
    [/\b(title|headline|heading)\b/i, 'title'],
    [/\b(add|introduce|includ\w*|incorporat\w*|consider)\b/i, 'add']
];

const SEVERITY_PATTERNS = [
    [/\b(contradict\w*|fatal|incorrect|wrong|unsupported|critical|blocker|must)\b/i, 'major'],
    [/\b(nit|typo|polish|cosmetic)\b/i, 'nit']
];

const EFFORT_PATTERNS = [
    [/\b(rewrite|restructure|new section|chapter|research)\b/i, 'large'],
    [/\b(paragraph|example|table|diagram)\b/i, 'medium'],
    [/\b(sentence|word|term|footnote)\b/i, 'trivial']
];

const firstMatch = (patterns, text, fallback) => {
    for (const [re, value] of patterns) if (re.test(text)) return value;
    return fallback;
};

const stripMarkup = text => text
    .replace(/!\[[^\]]*]\([^)]*\)/g, '')
    .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
    .replace(/[*_`>]+/g, '')
    .replace(/\s+/g, ' ')
    .trim();

const QUOTE_RE = /[“"']([^“”"']{15,180})[”"']/;

/**
 * Turn a markdown lens artifact into `ExtractedRevisionItem`s.
 * @returns {{items: object[], warnings: string[]}}
 */
export function extractItemsFromMarkdown(markdown, {maxItems = 40} = {}) {
    const items = [];
    const warnings = [];
    const headingPath = [];
    let inFence = false;
    let pending = null;

    const flush = () => {
        if (!pending) return;
        const text = stripMarkup(pending.text);
        pending = null;
        if (text.length < 16 || items.length >= maxItems) return;

        const sentenceEnd = text.search(/(?<=[.;:!?])\s/);
        const title = (sentenceEnd > 12 ? text.slice(0, sentenceEnd) : text).slice(0, 160).trim();
        const rationale = sentenceEnd > 12 ? text.slice(sentenceEnd).trim() : '';
        const quoted = QUOTE_RE.exec(text);

        items.push({
            title,
            rationale,
            evidenceQuote: text.slice(0, 400),
            action: firstMatch(ACTION_PATTERNS, text, 'note'),
            severity: firstMatch(SEVERITY_PATTERNS, text, 'minor'),
            effort: firstMatch(EFFORT_PATTERNS, text, 'small'),
            confidence: 0.6,
            tags: [],
            targetQuote: quoted?.[1],
            targetHeadingPath: [...headingPath]
        });
    };

    for (const rawLine of markdown.split(/\r?\n/)) {
        const line = rawLine.trimEnd();
        if (/^\s*(```|~~~)/.test(line)) {
            inFence = !inFence;
            continue;
        }
        if (inFence) continue;

        const heading = /^(#{1,6})[ \t]+(.+)$/.exec(line);
        if (heading) {
            flush();
            const level = heading[1].length;
            headingPath.length = Math.min(headingPath.length, level - 1);
            headingPath[level - 1] = heading[2].trim();
            continue;
        }

        const bullet = /^\s{0,6}(?:[-*+]|\d+[.)])\s+(.*)$/.exec(line);
        if (bullet) {
            flush();
            pending = {text: bullet[1]};
            continue;
        }
        if (pending && line.trim() && /^\s{2,}/.test(rawLine)) {
            pending.text += ` ${line.trim()}`;
            continue;
        }
        flush();
    }
    flush();

    if (!items.length) warnings.push('No list-shaped suggestions found in this artifact.');
    if (items.length >= maxItems) warnings.push(`Truncated at ${maxItems} items.`);
    return {items: items.map(i => ({...i, tags: i.tags.filter(Boolean)})), warnings};
}

/* ------------------------------------------------------------------ *
 * Batches & queue
 * ------------------------------------------------------------------ */

export const emptyQueue = draftId => ({
    schemaVersion: SCHEMA_VERSION, draftId, updatedAt: nowIso(), batches: []
});

export const allItems = queue => (queue?.batches ?? []).flatMap(b => b.items);

export function buildBatch(input) {
    const batchId = newId('batch');
    const createdBy = input.createdBy ?? HUMAN_AUTHOR;
    const extracted = input.extracted ?? extractItemsFromMarkdown(input.artifactContent);
    return {
        schemaVersion: SCHEMA_VERSION,
        id: batchId,
        lensId: input.lensId,
        sourceRef: input.sourceRef,
        artifactId: input.artifactId ?? newId('art'),
        artifactHash: contentHash(input.artifactContent),
        createdAt: nowIso(),
        createdBy,
        extractorVersion: input.extractorVersion ?? EXTRACTOR_VERSION,
        warnings: extracted.warnings ?? [],
        items: (extracted.items ?? []).map(e => hydrateItem(e, {
            batchId,
            lensId: input.lensId,
            sourceRef: input.sourceRef,
            articleContent: input.articleContent,
            createdBy
        }))
    };
}

/**
 * Idempotent ingestion: replaces the batch for `lensId`, carrying user edits
 * and triage state across by `similarityKey`.
 */
export function ingestArtifact(queue, input) {
    const current = queue ?? emptyQueue(input.sourceRef.draftId);
    const previous = current.batches.find(b => b.lensId === input.lensId);
    if (previous && previous.artifactHash === contentHash(input.artifactContent)
        && previous.extractorVersion === EXTRACTOR_VERSION) {
        return {queue: current, batch: previous, changed: false};
    }

    const batch = buildBatch(input);
    const carry = new Map((previous?.items ?? []).map(i => [i.similarityKey, i]));
    batch.items = batch.items.map(item => {
        const old = carry.get(item.similarityKey);
        if (!old) return item;
        return {
            ...item,
            id: old.id,
            status: old.status,
            priority: old.priority,
            tags: [...new Set([...item.tags, ...old.tags])],
            annotationIds: old.annotationIds,
            appliedInRevision: old.appliedInRevision,
            statusNote: old.statusNote,
            userEdited: old.userEdited,
            title: old.userEdited ? old.title : item.title,
            rationale: old.userEdited ? old.rationale : item.rationale,
            suggestedText: old.userEdited ? old.suggestedText : item.suggestedText,
            severity: old.userEdited ? old.severity : item.severity,
            createdAt: old.createdAt
        };
    });

    return {
        changed: true,
        batch,
        queue: {
            ...current,
            updatedAt: nowIso(),
            batches: [...current.batches.filter(b => b.lensId !== input.lensId), batch]
        }
    };
}

export const dropLensBatches = (queue, lensId) => (!queue ? queue : {
    ...queue,
    updatedAt: nowIso(),
    batches: queue.batches.filter(b => b.lensId !== lensId)
});

export function reanchorItems(queue, content) {
    if (!queue) return queue;
    return {
        ...queue,
        batches: queue.batches.map(b => ({
            ...b,
            items: b.items.map(i => ({...i, target: reanchorTarget(i.target, content)}))
        }))
    };
}

export function updateItem(queue, itemId, mutate) {
    if (!queue) return queue;
    return {
        ...queue,
        updatedAt: nowIso(),
        batches: queue.batches.map(b => ({
            ...b,
            items: b.items.map(i => (i.id === itemId ? mutate(i) : i))
        }))
    };
}

/* ------------------------------------------------------------------ *
 * Query
 * ------------------------------------------------------------------ */

export function filterItems(items, query = {}) {
    const needle = (query.search ?? '').trim().toLowerCase();
    const matched = items.filter(item => {
        if (query.lensIds?.length && !query.lensIds.includes(item.lensId)) return false;
        if (query.actions?.length && !query.actions.includes(item.action)) return false;
        if (query.statuses?.length && !query.statuses.includes(item.status)) return false;
        if (query.severities?.length && !query.severities.includes(item.severity)) return false;
        if (query.tags?.length && !query.tags.some(t => item.tags.includes(t))) return false;
        if (item.confidence < (query.minConfidence ?? 0)) return false;
        if (query.orphanedOnly && !isOrphanedItem(item)) return false;
        if (query.hideStale && query.headRevision != null
            && item.sourceRef.revision < query.headRevision) return false;
        if (needle) {
            const haystack = [
                item.title, item.rationale, item.suggestedText ?? '', item.evidenceQuote ?? '',
                describeTarget(item.target), item.tags.join(' '), item.lensId
            ].join('\n').toLowerCase();
            if (!haystack.includes(needle)) return false;
        }
        return true;
    });

    const cmp = (a, b) => {
        switch (query.sortBy) {
            case 'document-order':
                return targetSortKey(a.target) - targetSortKey(b.target);
            case 'severity':
                return SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity];
            case 'lens':
                return a.lensId.localeCompare(b.lensId);
            case 'created':
                return a.createdAt.localeCompare(b.createdAt);
            default:
                return priorityScore(a) - priorityScore(b);
        }
    };
    matched.sort((a, b) => ((query.sortDir ?? 'desc') === 'desc' ? -cmp(a, b) : cmp(a, b)));
    return matched;
}

export function groupItems(items, groupBy = 'none') {
    if (groupBy === 'none') return [{key: 'all', items}];
    const groups = new Map();
    for (const item of items) {
        const key = groupBy === 'lens' ? item.lensId
            : groupBy === 'action' ? item.action
                : groupBy === 'status' ? item.status
                    : item.target.scope === 'section' ? item.target.headingPath.join(' › ')
                        : item.target.scope === 'range'
                            ? (item.target.anchor.headingPath.join(' › ') || '(unsectioned)')
                            : '(whole article)';
        const bucket = groups.get(key);
        if (bucket) bucket.push(item); else groups.set(key, [item]);
    }
    return [...groups.entries()].map(([key, grouped]) => ({key, items: grouped}));
}

export function findDuplicates(items) {
    const groups = new Map();
    for (const item of items) {
        if (item.status === 'superseded') continue;
        const bucket = groups.get(item.similarityKey);
        if (bucket) bucket.push(item); else groups.set(item.similarityKey, [item]);
    }
    return [...groups.values()]
        .filter(g => g.length > 1)
        .map(g => [...g].sort((a, b) => priorityScore(b) - priorityScore(a)));
}

export function mergeItems(winner, losers) {
    const merged = {
        ...winner,
        mergedFromIds: [...new Set([...winner.mergedFromIds, ...losers.map(l => l.id)])],
        tags: [...new Set([...winner.tags, ...losers.flatMap(l => l.tags)])],
        confidence: Math.max(winner.confidence, ...losers.map(l => l.confidence)),
        updatedAt: nowIso()
    };
    return {
        winner: merged,
        losers: losers.map(l => setItemStatus(l, 'superseded', {
            supersededById: winner.id, note: 'merged as duplicate'
        }))
    };
}

/** Deterministic serialization of accepted items for Update Article. */
export function itemsToPromptBlock(items) {
    const feedable = items.filter(isFeedableItem).sort((a, b) =>
        targetSortKey(a.target) - targetSortKey(b.target) || priorityScore(b) - priorityScore(a));
    if (!feedable.length) return '';

    const lines = ['## Accepted revision items', ''];
    for (const item of feedable) {
        lines.push(`- **${item.action.toUpperCase()}** (${item.severity}, from \`${item.lensId}\`) — ${item.title}`);
        lines.push(`  - where: ${describeTarget(item.target)}`);
        if (item.rationale?.trim()) lines.push(`  - why: ${item.rationale.replace(/\n+/g, ' ').trim()}`);
        if (item.suggestedText) lines.push(`  - suggested text: ${JSON.stringify(item.suggestedText)}`);
        if (isOrphanedItem(item)) {
            lines.push('  - ⚠ anchor no longer resolves; apply by intent, not by position');
        }
    }
    return lines.join('\n').trimEnd();
}

export function queueStats(items) {
    const byStatus = {proposed: 0, accepted: 0, rejected: 0, deferred: 0, applied: 0, superseded: 0};
    const byLens = {};
    for (const item of items) {
        byStatus[item.status] = (byStatus[item.status] ?? 0) + 1;
        byLens[item.lensId] = (byLens[item.lensId] ?? 0) + 1;
    }
    return {
        total: items.length,
        byStatus,
        byLens,
        accepted: byStatus.accepted,
        duplicates: findDuplicates(items).reduce((n, g) => n + g.length - 1, 0)
    };
}