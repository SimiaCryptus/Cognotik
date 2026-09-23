/** annotations.js — runtime counterpart of `annotations.schema.ts`. */

import {
    DOCUMENT_TARGET,
    HUMAN_AUTHOR,
    SCHEMA_VERSION,
    SEVERITY_RANK,
    describeTarget,
    isOrphanedTarget,
    newId,
    nowIso,
    reanchorTarget,
    targetSortKey
} from './core.js';

export const ANNOTATION_KINDS =
    ['comment', 'question', 'suggestion', 'correction', 'todo', 'praise', 'flag'];
export const ANNOTATION_RESOLUTIONS =
    ['open', 'accepted', 'rejected', 'applied', 'obsolete'];
export const FEEDABLE_RESOLUTIONS = ['open', 'accepted'];

export function createAnnotation(input) {
    const ts = nowIso();
    return {
        schemaVersion: SCHEMA_VERSION,
        id: newId('ann'),
        sourceRef: input.sourceRef,
        resolvedAgainstRevision: input.sourceRef?.revision,
        target: input.target ?? DOCUMENT_TARGET,
        kind: input.kind ?? 'comment',
        body: input.body ?? '',
        suggestedText: input.suggestedText || undefined,
        severity: input.severity ?? 'minor',
        priority: 'auto',
        tags: input.tags ?? [],
        resolution: 'open',
        author: input.author ?? HUMAN_AUTHOR,
        createdAt: ts,
        updatedAt: ts,
        replies: [],
        derivedFromRevisionItemId: input.derivedFromRevisionItemId,
        confidence: input.confidence,
        pinned: false
    };
}

export const isOrphaned = annotation => isOrphanedTarget(annotation.target);
export const isFeedable = annotation => FEEDABLE_RESOLUTIONS.includes(annotation.resolution);

export function resolveAnnotation(annotation, resolution, opts = {}) {
    return {
        ...annotation,
        resolution,
        appliedInRevision: opts.appliedInRevision ?? annotation.appliedInRevision,
        resolutionNote: opts.note ?? annotation.resolutionNote,
        updatedAt: nowIso()
    };
}

export function addReply(annotation, body, author = HUMAN_AUTHOR, generated = false) {
    const reply = {id: newId('rep'), createdAt: nowIso(), author, body, generated};
    return {...annotation, replies: [...annotation.replies, reply], updatedAt: reply.createdAt};
}

/** Recompute every anchor against a new revision; nothing is ever dropped. */
export function reanchorAnnotations(annotations, content, revision) {
    return annotations.map(annotation => {
        const target = reanchorTarget(annotation.target, content);
        return target === annotation.target
            ? {...annotation, resolvedAgainstRevision: revision}
            : {...annotation, target, resolvedAgainstRevision: revision};
    });
}

export function filterAnnotations(annotations, query = {}) {
    const needle = (query.search ?? '').trim().toLowerCase();
    const resolutions = query.resolutions?.length ? query.resolutions : null;
    const kinds = query.kinds?.length ? query.kinds : null;

    const matched = annotations.filter(a => {
        if (kinds && !kinds.includes(a.kind)) return false;
        if (resolutions && !resolutions.includes(a.resolution)) return false;
        if (query.severities?.length && !query.severities.includes(a.severity)) return false;
        if (query.orphanedOnly && !isOrphaned(a)) return false;
        if (needle) {
            const haystack = [
                a.body,
                a.suggestedText ?? '',
                describeTarget(a.target),
                a.tags.join(' ')
            ].join('\n').toLowerCase();
            if (!haystack.includes(needle)) return false;
        }
        return true;
    });

    const cmp = (a, b) => {
        switch (query.sortBy) {
            case 'created':
                return a.createdAt.localeCompare(b.createdAt);
            case 'updated':
                return a.updatedAt.localeCompare(b.updatedAt);
            case 'severity':
                return SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity];
            default:
                return targetSortKey(a.target) - targetSortKey(b.target);
        }
    };
    matched.sort((a, b) => (query.sortDir === 'desc' ? -cmp(a, b) : cmp(a, b)));
    return matched;
}

/** Deterministic prompt serialization, grouped by section, document-ordered. */
export function annotationsToPromptBlock(annotations) {
    const feedable = annotations
        .filter(isFeedable)
        .sort((a, b) => targetSortKey(a.target) - targetSortKey(b.target));
    if (!feedable.length) return '';

    const groups = new Map();
    for (const a of feedable) {
        const key = a.target.scope === 'document'
            ? '(whole article)'
            : a.target.scope === 'section'
                ? a.target.headingPath.join(' › ')
                : a.target.anchor.headingPath.join(' › ') || '(unsectioned)';
        const bucket = groups.get(key);
        if (bucket) bucket.push(a); else groups.set(key, [a]);
    }

    const lines = ['## Reader annotations', ''];
    for (const [section, items] of groups) {
        lines.push(`### ${section}`, '');
        for (const a of items) {
            const flags = [a.kind, a.severity, a.resolution === 'accepted' ? 'accepted' : null]
                .filter(Boolean).join(', ');
            lines.push(`- **[${flags}]** ${describeTarget(a.target)}`);
            lines.push(`  - note: ${a.body.replace(/\n+/g, ' ').trim()}`);
            if (a.suggestedText) lines.push(`  - replace with: ${JSON.stringify(a.suggestedText)}`);
            if (isOrphaned(a)) {
                lines.push('  - ⚠ anchor text no longer present; apply by intent, not by position');
            }
            for (const reply of a.replies) {
                lines.push(`  - reply (${reply.author.kind}): ${reply.body.replace(/\n+/g, ' ').trim()}`);
            }
        }
        lines.push('');
    }
    return lines.join('\n').trimEnd();
}

export function annotationStats(annotations) {
    const byResolution = {open: 0, accepted: 0, rejected: 0, applied: 0, obsolete: 0};
    let orphaned = 0;
    let feedable = 0;
    for (const a of annotations) {
        byResolution[a.resolution] = (byResolution[a.resolution] ?? 0) + 1;
        if (isOrphaned(a)) orphaned += 1;
        if (isFeedable(a)) feedable += 1;
    }
    return {total: annotations.length, byResolution, orphaned, feedable};
}