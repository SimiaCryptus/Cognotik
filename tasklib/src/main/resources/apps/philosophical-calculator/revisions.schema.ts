/**
 * revisions.schema.ts
 *
 * Every Analysis (and optionally Output) lens produces prose. Prose is great for
 * reading and terrible for acting on. This schema is the *normalization target*:
 * each lens artifact is ingested into a batch of `RevisionItem`s — a single,
 * uniform "suggested edit / action item" shape — so that the UI can aggregate,
 * edit, annotate, filter and prioritize suggestions from all lenses in one queue.
 *
 *     socratic@v4 artifact ─┐
 *     game-theory@v4 artifact ─┼─► ingest ─► RevisionItem[] ─► user triage ─┐
 *     dialectical@v4 artifact ─┘                                            │
 *                                                                           ▼
 *                                        Update Article(accepted items) -> draft@v5
 */

import { z } from 'zod';
import {
  Author,
  Confidence,
  DOCUMENT_TARGET,
  DraftRevisionRef,
  EFFORT_DISCOUNT,
  Effort,
  HUMAN_AUTHOR,
  Id,
  IsoDateTime,
  LensId,
  Markdown,
  PRIORITY_WEIGHT,
  Priority,
  SCHEMA_VERSION,
  SEVERITY_WEIGHT,
  Severity,
  Tag,
  Target,
  contentHash,
  describeTarget,
  newId,
  nowIso,
  targetSortKey,
} from './common.schema';

/* ------------------------------------------------------------------------- *
 * Vocabulary
 * ------------------------------------------------------------------------- */

/** The verb: what the article should *do* about this item. */
export const RevisionAction = z.enum([
  'add', // introduce new material
  'expand', // deepen something already present
  'cut', // remove redundancy / weak material
  'rewrite', // same point, better prose
  'restructure', // move / reorder / split / merge sections
  'reframe', // change the framing or thesis emphasis
  'clarify', // define a term, disambiguate, add an example
  'evidence', // add data, numbers, worked example
  'citation', // attribute a claim to a source
  'counterargument', // steel-man an objection and respond
  'tone', // register / audience fit
  'title', // headline / section-heading change
  'note', // no direct edit; context for the author
]);
export type RevisionAction = z.infer<typeof RevisionAction>;

/** Triage state of an item in the revision queue. */
export const RevisionItemStatus = z.enum([
  'proposed', // freshly ingested
  'accepted', // will be fed into the next Update Article
  'rejected', // will not be fed; kept for the audit trail
  'deferred', // good idea, not this pass
  'applied', // folded into a revision (see appliedInRevision)
  'superseded', // merged into another item (see supersededById)
]);
export type RevisionItemStatus = z.infer<typeof RevisionItemStatus>;

export const FEEDABLE_STATUSES: readonly RevisionItemStatus[] = ['accepted'];

/* ------------------------------------------------------------------------- *
 * RevisionItem — the standard action-item schema
 * ------------------------------------------------------------------------- */

export const RevisionItem = z.object({
  schemaVersion: z.number().int().positive().default(SCHEMA_VERSION),
  id: Id,

  /** Which lens artifact produced this item. */
  lensId: LensId,
  batchId: Id,
  /** The draft revision the originating lens analyzed. */
  sourceRef: DraftRevisionRef,

  /** One-line imperative summary, e.g. "Define 'refraction' before first use". */
  title: z.string().min(1).max(160),
  /** Why this matters — quoted or paraphrased from the lens artifact. */
  rationale: Markdown.default(''),
  /** Verbatim excerpt from the lens artifact this item was extracted from. */
  evidenceQuote: z.string().optional(),

  action: RevisionAction,
  /** Where in the article it applies. Defaults to whole-document. */
  target: Target,

  /** Optional concrete replacement/insertion text produced by the lens. */
  suggestedText: z.string().optional(),

  severity: Severity.default('minor'),
  effort: Effort.default('small'),
  /** Lens-reported confidence that this suggestion is correct/valuable. */
  confidence: Confidence.default(0.6),
  /** Manual override; 'auto' -> use priorityScore(). */
  priority: Priority.default('auto'),
  tags: z.array(Tag).default([]),

  status: RevisionItemStatus.default('proposed'),
  /** Revision in which this item was folded in. */
  appliedInRevision: z.number().int().positive().optional(),
  statusNote: z.string().optional(),

  /** Dedupe/merge bookkeeping. */
  similarityKey: z.string().min(1),
  mergedFromIds: z.array(Id).default([]),
  supersededById: Id.optional(),

  /** User annotations attached to the *item* (not the article). */
  annotationIds: z.array(Id).default([]),

  /** True once a human has edited any field, so re-ingestion won't clobber it. */
  userEdited: z.boolean().default(false),

  createdAt: IsoDateTime,
  updatedAt: IsoDateTime,
  createdBy: Author,
});
export type RevisionItem = z.infer<typeof RevisionItem>;

/* ------------------------------------------------------------------------- *
 * RevisionBatch — one ingestion pass over one lens artifact
 * ------------------------------------------------------------------------- */

export const RevisionBatch = z.object({
  schemaVersion: z.number().int().positive().default(SCHEMA_VERSION),
  id: Id,
  lensId: LensId,
  sourceRef: DraftRevisionRef,
  /** Artifact this batch was extracted from. */
  artifactId: Id,
  /** Hash of the artifact content — makes ingestion idempotent. */
  artifactHash: z.string().min(1),

  createdAt: IsoDateTime,
  createdBy: Author,
  /** Version of the extraction prompt, so batches can be re-run on prompt changes. */
  extractorVersion: z.string().default('1'),

  items: z.array(RevisionItem).default([]),

  tokensIn: z.number().int().nonnegative().optional(),
  tokensOut: z.number().int().nonnegative().optional(),
  durationMs: z.number().int().nonnegative().optional(),
  /** Non-fatal extraction problems worth showing in the UI. */
  warnings: z.array(z.string()).default([]),
});
export type RevisionBatch = z.infer<typeof RevisionBatch>;

/** The aggregated queue persisted per draft. */
export const RevisionQueue = z.object({
  schemaVersion: z.number().int().positive().default(SCHEMA_VERSION),
  draftId: Id,
  updatedAt: IsoDateTime,
  batches: z.array(RevisionBatch).default([]),
});
export type RevisionQueue = z.infer<typeof RevisionQueue>;

/**
 * What the extractor model is asked to emit (a thin, promptable subset of
 * RevisionItem — no ids, no bookkeeping). Validate model output against this,
 * then hydrate with `hydrateItem`.
 */
export const ExtractedRevisionItem = z.object({
  title: z.string().min(1).max(160),
  rationale: z.string().default(''),
  action: RevisionAction,
  severity: Severity.default('minor'),
  effort: Effort.default('small'),
  confidence: Confidence.default(0.6),
  tags: z.array(z.string()).default([]),
  /** Verbatim article text the item applies to; used to build the anchor. */
  targetQuote: z.string().optional(),
  targetHeadingPath: z.array(z.string()).default([]),
  suggestedText: z.string().optional(),
  evidenceQuote: z.string().optional(),
});
export type ExtractedRevisionItem = z.infer<typeof ExtractedRevisionItem>;

export const ExtractedRevisionBatch = z.object({
  items: z.array(ExtractedRevisionItem).default([]),
  warnings: z.array(z.string()).default([]),
});
export type ExtractedRevisionBatch = z.infer<typeof ExtractedRevisionBatch>;

/* ------------------------------------------------------------------------- *
 * Query model (drives the revision queue UI)
 * ------------------------------------------------------------------------- */

export const RevisionQuery = z.object({
  lensIds: z.array(LensId).default([]),
  actions: z.array(RevisionAction).default([]),
  statuses: z.array(RevisionItemStatus).default(['proposed', 'accepted', 'deferred']),
  severities: z.array(Severity).default([]),
  tags: z.array(Tag).default([]),
  search: z.string().default(''),
  minConfidence: Confidence.default(0),
  /** Only items whose anchor no longer resolves against head. */
  orphanedOnly: z.boolean().default(false),
  /** Hide items generated from a revision older than head. */
  hideStale: z.boolean().default(false),
  headRevision: z.number().int().positive().optional(),
  groupBy: z.enum(['none', 'lens', 'section', 'action', 'status']).default('none'),
  sortBy: z.enum(['priority', 'document-order', 'severity', 'lens', 'created']).default('priority'),
  sortDir: z.enum(['asc', 'desc']).default('desc'),
});
export type RevisionQuery = z.infer<typeof RevisionQuery>;

/* ------------------------------------------------------------------------- *
 * Scoring & helpers
 * ------------------------------------------------------------------------- */

/**
 * Sortable priority. Manual `priority` wins; otherwise derive from
 * severity × effort-discount × confidence so the default ordering is useful
 * with zero triage.
 */
export function priorityScore(item: RevisionItem): number {
  if (item.priority !== 'auto') return PRIORITY_WEIGHT[item.priority];
  const base = SEVERITY_WEIGHT[item.severity];
  const discount = EFFORT_DISCOUNT[item.effort];
  return Math.round(base * discount * (0.4 + 0.6 * item.confidence));
}

/** Stable key used to detect duplicate suggestions across lenses / re-runs. */
export function similarityKeyFor(input: {
  action: RevisionAction;
  title: string;
  target: Target;
}): string {
  const normalizedTitle = input.title
    .toLowerCase()
    .replace(/[^a-z0-9 ]+/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 3)
    .sort()
    .join(' ');
  const where =
    input.target.scope === 'range'
      ? input.target.anchor.quote.slice(0, 40).toLowerCase()
      : input.target.scope === 'section'
        ? input.target.headingPath.join('/').toLowerCase()
        : 'document';
  return contentHash(`${input.action}|${where}|${normalizedTitle}`);
}

function buildTarget(extracted: ExtractedRevisionItem): Target {
  if (extracted.targetQuote && extracted.targetQuote.trim().length > 0) {
    return {
      scope: 'range',
      anchorState: 'anchored',
      anchor: {
        quote: extracted.targetQuote,
        prefix: '',
        suffix: '',
        headingPath: extracted.targetHeadingPath,
      },
    };
  }
  if (extracted.targetHeadingPath.length > 0) {
    return { scope: 'section', headingPath: extracted.targetHeadingPath };
  }
  return DOCUMENT_TARGET;
}

/** Turn raw model output into a fully-formed, persistable RevisionItem. */
export function hydrateItem(
  extracted: ExtractedRevisionItem,
  ctx: { batchId: Id; lensId: LensId; sourceRef: DraftRevisionRef; createdBy?: Author },
): RevisionItem {
  const ts = nowIso();
  const target = buildTarget(extracted);
  return RevisionItem.parse({
    id: newId('rev'),
    lensId: ctx.lensId,
    batchId: ctx.batchId,
    sourceRef: ctx.sourceRef,
    title: extracted.title.trim(),
    rationale: extracted.rationale,
    evidenceQuote: extracted.evidenceQuote,
    action: extracted.action,
    target,
    suggestedText: extracted.suggestedText,
    severity: extracted.severity,
    effort: extracted.effort,
    confidence: extracted.confidence,
    tags: extracted.tags
      .map((t) => t.toLowerCase().trim().replace(/\s+/g, '-'))
      .filter((t) => /^[a-z0-9][a-z0-9\-_/]*$/.test(t)),
    similarityKey: similarityKeyFor({ action: extracted.action, title: extracted.title, target }),
    createdAt: ts,
    updatedAt: ts,
    createdBy: ctx.createdBy ?? HUMAN_AUTHOR,
  });
}

export function buildBatch(input: {
  lensId: LensId;
  sourceRef: DraftRevisionRef;
  artifactId: Id;
  artifactContent: string;
  extracted: ExtractedRevisionBatch;
  createdBy?: Author;
  extractorVersion?: string;
}): RevisionBatch {
  const batchId = newId('batch');
  const createdBy = input.createdBy ?? HUMAN_AUTHOR;
  return RevisionBatch.parse({
    id: batchId,
    lensId: input.lensId,
    sourceRef: input.sourceRef,
    artifactId: input.artifactId,
    artifactHash: contentHash(input.artifactContent),
    createdAt: nowIso(),
    createdBy,
    extractorVersion: input.extractorVersion ?? '1',
    warnings: input.extracted.warnings,
    items: input.extracted.items.map((e) =>
      hydrateItem(e, {
        batchId,
        lensId: input.lensId,
        sourceRef: input.sourceRef,
        createdBy,
      }),
    ),
  });
}

export function allItems(queue: RevisionQueue): RevisionItem[] {
  return queue.batches.flatMap((b) => b.items);
}

export function isOrphanedItem(item: RevisionItem): boolean {
  return item.target.scope === 'range' && item.target.anchorState === 'orphaned';
}

export function isFeedableItem(item: RevisionItem): boolean {
  return FEEDABLE_STATUSES.includes(item.status);
}

export function setStatus(
  item: RevisionItem,
  status: RevisionItemStatus,
  opts: { appliedInRevision?: number; note?: string; supersededById?: Id } = {},
): RevisionItem {
  return {
    ...item,
    status,
    appliedInRevision: opts.appliedInRevision ?? item.appliedInRevision,
    statusNote: opts.note ?? item.statusNote,
    supersededById: opts.supersededById ?? item.supersededById,
    updatedAt: nowIso(),
  };
}

/** Merge `losers` into `winner`, preserving provenance on both sides. */
export function mergeItems(
  winner: RevisionItem,
  losers: RevisionItem[],
): { winner: RevisionItem; losers: RevisionItem[] } {
  const merged: RevisionItem = {
    ...winner,
    mergedFromIds: [...new Set([...winner.mergedFromIds, ...losers.map((l) => l.id)])],
    tags: [...new Set([...winner.tags, ...losers.flatMap((l) => l.tags)])],
    confidence: Math.max(winner.confidence, ...losers.map((l) => l.confidence)),
    updatedAt: nowIso(),
  };
  return {
    winner: merged,
    losers: losers.map((l) =>
      setStatus(l, 'superseded', { supersededById: winner.id, note: 'merged as duplicate' }),
    ),
  };
}

/** Group items by similarityKey; highest-priority item wins each group. */
export function findDuplicates(items: RevisionItem[]): RevisionItem[][] {
  const groups = new Map<string, RevisionItem[]>();
  for (const item of items) {
    if (item.status === 'superseded') continue;
    const bucket = groups.get(item.similarityKey);
    if (bucket) bucket.push(item);
    else groups.set(item.similarityKey, [item]);
  }
  return [...groups.values()]
    .filter((g) => g.length > 1)
    .map((g) => [...g].sort((a, b) => priorityScore(b) - priorityScore(a)));
}

export function filterItems(items: RevisionItem[], query: RevisionQuery): RevisionItem[] {
  const q = RevisionQuery.parse(query);
  const needle = q.search.trim().toLowerCase();
  const severityRank: Record<Severity, number> = { blocker: 0, major: 1, minor: 2, nit: 3 };

  const matched = items.filter((item) => {
    if (q.lensIds.length && !q.lensIds.includes(item.lensId)) return false;
    if (q.actions.length && !q.actions.includes(item.action)) return false;
    if (q.statuses.length && !q.statuses.includes(item.status)) return false;
    if (q.severities.length && !q.severities.includes(item.severity)) return false;
    if (q.tags.length && !q.tags.some((t) => item.tags.includes(t))) return false;
    if (item.confidence < q.minConfidence) return false;
    if (q.orphanedOnly && !isOrphanedItem(item)) return false;
    if (q.hideStale && q.headRevision != null && item.sourceRef.revision < q.headRevision) {
      return false;
    }
    if (needle) {
      const haystack = [
        item.title,
        item.rationale,
        item.suggestedText ?? '',
        item.evidenceQuote ?? '',
        describeTarget(item.target),
        item.tags.join(' '),
        item.lensId,
      ]
        .join('\n')
        .toLowerCase();
      if (!haystack.includes(needle)) return false;
    }
    return true;
  });

  const cmp = (a: RevisionItem, b: RevisionItem): number => {
    switch (q.sortBy) {
      case 'document-order':
        return targetSortKey(a.target) - targetSortKey(b.target);
      case 'severity':
        return severityRank[a.severity] - severityRank[b.severity];
      case 'lens':
        return a.lensId.localeCompare(b.lensId);
      case 'created':
        return a.createdAt.localeCompare(b.createdAt);
      case 'priority':
      default:
        return priorityScore(a) - priorityScore(b);
    }
  };

  matched.sort((a, b) => (q.sortDir === 'desc' ? -cmp(a, b) : cmp(a, b)));
  return matched;
}

export function groupItems(
  items: RevisionItem[],
  groupBy: RevisionQuery['groupBy'],
): Array<{ key: string; items: RevisionItem[] }> {
  if (groupBy === 'none') return [{ key: 'all', items }];
  const groups = new Map<string, RevisionItem[]>();
  for (const item of items) {
    const key =
      groupBy === 'lens'
        ? item.lensId
        : groupBy === 'action'
          ? item.action
          : groupBy === 'status'
            ? item.status
            : item.target.scope === 'section'
              ? item.target.headingPath.join(' › ')
              : item.target.scope === 'range'
                ? item.target.anchor.headingPath.join(' › ') || '(unsectioned)'
                : '(whole article)';
    const bucket = groups.get(key);
    if (bucket) bucket.push(item);
    else groups.set(key, [item]);
  }
  return [...groups.entries()].map(([key, grouped]) => ({ key, items: grouped }));
}

/**
 * Deterministic serialization of the accepted items for the Update Article prompt.
 * Ordered by document position, then priority, so identical triage yields an
 * identical prompt.
 */
export function itemsToPromptBlock(items: RevisionItem[]): string {
  const feedable = items
    .filter(isFeedableItem)
    .sort(
      (a, b) =>
        targetSortKey(a.target) - targetSortKey(b.target) || priorityScore(b) - priorityScore(a),
    );

  if (feedable.length === 0) return '';

  const lines: string[] = ['## Accepted revision items', ''];
  for (const item of feedable) {
    lines.push(
      `- **${item.action.toUpperCase()}** (${item.severity}, from \`${item.lensId}\`) — ${item.title}`,
    );
    lines.push(`  - where: ${describeTarget(item.target)}`);
    if (item.rationale.trim()) {
      lines.push(`  - why: ${item.rationale.replace(/\n+/g, ' ').trim()}`);
    }
    if (item.suggestedText) {
      lines.push(`  - suggested text: ${JSON.stringify(item.suggestedText)}`);
    }
    if (isOrphanedItem(item)) {
      lines.push('  - ⚠ anchor no longer resolves; apply by intent, not by position');
    }
  }
  return lines.join('\n').trimEnd();
}

export function queueStats(items: RevisionItem[]): {
  total: number;
  byStatus: Record<RevisionItemStatus, number>;
  byLens: Record<string, number>;
  accepted: number;
  duplicates: number;
} {
  const byStatus = {
    proposed: 0,
    accepted: 0,
    rejected: 0,
    deferred: 0,
    applied: 0,
    superseded: 0,
  } as Record<RevisionItemStatus, number>;
  const byLens: Record<string, number> = {};

  for (const item of items) {
    byStatus[item.status] += 1;
    byLens[item.lensId] = (byLens[item.lensId] ?? 0) + 1;
  }

  return {
    total: items.length,
    byStatus,
    byLens,
    accepted: byStatus.accepted,
    duplicates: findDuplicates(items).reduce((n, g) => n + g.length - 1, 0),
  };
}