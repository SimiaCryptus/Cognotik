/** drafts.js — immutable, append-only draft revisions (M1.2 keystone). */

import {
    HUMAN_AUTHOR,
    SCHEMA_VERSION,
    contentHash,
    countWords,
    newId,
    nowIso
} from './core.js';

export const DRAFT_ORIGINS = [
    'pipeline', 'update-article', 'manual-edit', 'raw-import',
    'lens-output', 'revise-artifact', 'rollback'
];

export function createDraft(input) {
    const id = input.id ?? newId('draft');
    const createdAt = nowIso();
    const revision = {
        schemaVersion: SCHEMA_VERSION,
        draftId: id,
        revision: 1,
        parentRevision: null,
        createdAt,
        createdBy: input.createdBy ?? HUMAN_AUTHOR,
        origin: input.origin ?? 'raw-import',
        label: input.label,
        content: input.content ?? '',
        contentHash: contentHash(input.content ?? ''),
        wordCount: countWords(input.content ?? ''),
        appliedRevisionItemIds: [],
        appliedAnnotationIds: [],
        frozen: false
    };
    return {
        schemaVersion: SCHEMA_VERSION,
        id,
        kind: input.kind ?? 'article',
        title: input.title ?? 'Untitled',
        createdAt,
        updatedAt: createdAt,
        headRevision: 1,
        revisions: [revision],
        sourceRef: input.sourceRef
    };
}

export const getRevision = (draft, revision) =>
    draft?.revisions.find(r => r.revision === revision);

export function headRevision(draft) {
    const head = getRevision(draft, draft.headRevision);
    if (!head) throw new Error(`draft ${draft?.id} has no head revision`);
    return head;
}

export const refOf = rev => ({draftId: rev.draftId, revision: rev.revision});
export const headRef = draft => refOf(headRevision(draft));

export const isStale = (draft, ref) =>
    !!ref && ref.draftId === draft?.id && ref.revision < draft.headRevision;

/**
 * Append an immutable revision. Content identical to head is a no-op so that
 * re-running a lens that did not change anything does not inflate history.
 */
export function appendRevision(draft, input) {
    const parent = headRevision(draft);
    const hash = contentHash(input.content);
    if (hash === parent.contentHash) return {draft, revision: parent, created: false};

    const revision = {
        schemaVersion: SCHEMA_VERSION,
        draftId: draft.id,
        revision: parent.revision + 1,
        parentRevision: parent.revision,
        createdAt: nowIso(),
        createdBy: input.createdBy ?? HUMAN_AUTHOR,
        origin: input.origin ?? 'manual-edit',
        label: input.label,
        content: input.content,
        contentHash: hash,
        wordCount: countWords(input.content),
        changelog: input.changelog,
        appliedRevisionItemIds: input.appliedRevisionItemIds ?? [],
        appliedAnnotationIds: input.appliedAnnotationIds ?? [],
        instruction: input.instruction,
        frozen: false
    };
    return {
        created: true,
        revision,
        draft: {
            ...draft,
            updatedAt: revision.createdAt,
            headRevision: revision.revision,
            revisions: [...draft.revisions, revision]
        }
    };
}

/** Restore is never destructive: it appends a copy with origin `rollback`. */
export function restoreRevision(draft, revision, createdBy = HUMAN_AUTHOR) {
    const source = getRevision(draft, revision);
    if (!source) throw new Error(`revision v${revision} not found`);
    return appendRevision(draft, {
        content: source.content,
        origin: 'rollback',
        createdBy,
        label: `restore of v${revision}`,
        changelog: `Restored the content of v${revision}.`
    });
}

/** Mark a revision as referenced by an artifact/annotation so it is never pruned. */
export function freezeRevision(draft, revision) {
    return {
        ...draft,
        revisions: draft.revisions.map(r =>
            r.revision === revision ? {...r, frozen: true} : r)
    };
}

/** Retention: keep every frozen revision + head + the newest `keep` others. */
export function pruneRevisions(draft, keep = 40) {
    if (draft.revisions.length <= keep) return draft;
    const ordered = [...draft.revisions].sort((a, b) => b.revision - a.revision);
    const kept = new Set();
    let budget = keep;
    for (const rev of ordered) {
        if (rev.frozen || rev.revision === draft.headRevision || budget-- > 0) kept.add(rev.revision);
    }
    return {...draft, revisions: draft.revisions.filter(r => kept.has(r.revision))};
}

export function draftStats(draft) {
    const head = headRevision(draft);
    const parent = head.parentRevision ? getRevision(draft, head.parentRevision) : null;
    return {
        revisions: draft.revisions.length,
        head: head.revision,
        words: head.wordCount,
        wordDelta: parent ? head.wordCount - parent.wordCount : head.wordCount
    };
}