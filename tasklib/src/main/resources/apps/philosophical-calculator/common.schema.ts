/**
 * common.schema.ts
 *
 * Shared primitives for the Philosophical Calculator data model.
 *
 * Everything in here is intentionally dependency-light (zod only) so it can be used
 * from the UI, from the ingestion passes, and from the server-side render scripts.
 *
 * See also:
 *   - annotations.schema.ts : human/agent notes attached to a draft revision
 *   - revisions.schema.ts   : normalized "action items" extracted from lens artifacts
 */

import { z } from 'zod';

/** Bump when a stored envelope needs a migration. */
export const SCHEMA_VERSION = 1;

/* ------------------------------------------------------------------------- *
 * Scalars
 * ------------------------------------------------------------------------- */

export const Id = z.string().min(1);
export type Id = z.infer<typeof Id>;

export const IsoDateTime = z.string().datetime({ offset: true });
export type IsoDateTime = z.infer<typeof IsoDateTime>;

/** Markdown source text. Kept as a named alias for readability. */
export const Markdown = z.string();
export type Markdown = z.infer<typeof Markdown>;

/** 0..1 model/heuristic confidence. */
export const Confidence = z.number().min(0).max(1);
export type Confidence = z.infer<typeof Confidence>;

export const Tag = z
  .string()
  .min(1)
  .max(48)
  .regex(/^[a-z0-9][a-z0-9\-_/]*$/, 'tags are lowercase, kebab/snake/slash only');
export type Tag = z.infer<typeof Tag>;

export function nowIso(): IsoDateTime {
  return new Date().toISOString();
}

export function newId(prefix = 'id'): Id {
  const rand =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}_${rand}`;
}

/** Cheap, stable, non-cryptographic content hash (FNV-1a, hex). */
export function contentHash(input: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

/* ------------------------------------------------------------------------- *
 * Lenses & pipeline steps
 * ------------------------------------------------------------------------- */

export const PipelineStepId = z.enum([
  'draft-article',
  'update-article',
  'illustrate-article',
]);
export type PipelineStepId = z.infer<typeof PipelineStepId>;

export const AnalysisLensId = z.enum([
  'brainstorm',
  'dialectical',
  'socratic',
  'perspectives',
  'game-theory',
  'historical-debate',
  'unrunnable-protocol',
]);
export type AnalysisLensId = z.infer<typeof AnalysisLensId>;

export const OutputLensId = z.enum([
  'persuasive-essay',
  'narrative-story',
  'comic-script',
  'technical-tutorial',
  'html-webpage',
  'pdf-document',
]);
export type OutputLensId = z.infer<typeof OutputLensId>;

/** Any producer of an artifact. */
export const LensId = z.union([PipelineStepId, AnalysisLensId, OutputLensId]);
export type LensId = z.infer<typeof LensId>;

export const LENS_IDS: readonly LensId[] = [
  ...PipelineStepId.options,
  ...AnalysisLensId.options,
  ...OutputLensId.options,
];

export function isAnalysisLens(id: string): id is AnalysisLensId {
  return AnalysisLensId.safeParse(id).success;
}

export function isOutputLens(id: string): id is OutputLensId {
  return OutputLensId.safeParse(id).success;
}

/** Unified run-status vocabulary used by every badge in the UI. */
export const RunStatus = z.enum([
  'idle',
  'queued',
  'running',
  'streaming',
  'ready',
  'stale',
  'error',
  'cancelled',
   'skipped', // step deliberately bypassed (e.g. Summarize after a raw draft import)
]);
export type RunStatus = z.infer<typeof RunStatus>;

/* ------------------------------------------------------------------------- *
 * Authorship
 * ------------------------------------------------------------------------- */

export const AuthorKind = z.enum(['human', 'agent', 'import', 'system']);
export type AuthorKind = z.infer<typeof AuthorKind>;

export const Author = z.object({
  kind: AuthorKind,
  /** Display name: "you", a model label, or a filename for imports. */
  name: z.string().min(1).default('you'),
  /** Model identifier when kind === 'agent'. */
  modelId: z.string().min(1).optional(),
  /** Which lens/step the agent was acting as, when applicable. */
  lensId: LensId.optional(),
});
export type Author = z.infer<typeof Author>;

export const HUMAN_AUTHOR: Author = { kind: 'human', name: 'you' };

export function agentAuthor(modelId: string, lensId?: LensId): Author {
  return { kind: 'agent', name: modelId, modelId, lensId };
}

/* ------------------------------------------------------------------------- *
 * Prioritization vocabulary (shared by annotations & revision items)
 * ------------------------------------------------------------------------- */

export const Severity = z.enum(['blocker', 'major', 'minor', 'nit']);
export type Severity = z.infer<typeof Severity>;

export const Effort = z.enum(['trivial', 'small', 'medium', 'large']);
export type Effort = z.infer<typeof Effort>;

/** Explicit user override for ordering. `auto` defers to priorityScore(). */
export const Priority = z.enum(['auto', 'p0', 'p1', 'p2', 'p3']);
export type Priority = z.infer<typeof Priority>;

export const SEVERITY_WEIGHT: Record<Severity, number> = {
  blocker: 100,
  major: 60,
  minor: 30,
  nit: 10,
};

export const EFFORT_DISCOUNT: Record<Effort, number> = {
  trivial: 1.0,
  small: 0.9,
  medium: 0.75,
  large: 0.5,
};

export const PRIORITY_WEIGHT: Record<Exclude<Priority, 'auto'>, number> = {
  p0: 1000,
  p1: 750,
  p2: 500,
  p3: 250,
};

/* ------------------------------------------------------------------------- *
 * Text anchoring
 * ------------------------------------------------------------------------- */

/**
 * Robust, revision-tolerant pointer into a markdown document.
 *
 * Resolution order when re-anchoring against a new revision:
 *   1. exact `quote` match that is unique in the document
 *   2. `prefix`+`quote`+`suffix` match
 *   3. fuzzy `quote` match (>= 0.8 similarity) inside `blockId` / `headingPath`
 *   4. section-level fallback (headingPath only) -> anchorState 'relocated'
 *   5. give up -> anchorState 'orphaned'
 */
export const TextAnchor = z
  .object({
    /** Stable id assigned to the markdown block at import/normalization time. */
    blockId: z.string().min(1).optional(),
    /** Heading breadcrumbs, outermost first, e.g. ["Why it matters", "Tradeoffs"]. */
    headingPath: z.array(z.string()).default([]),
    /** The quoted text this anchor points at. */
    quote: z.string().min(1),
    /** Up to ~48 chars of context immediately before the quote. */
    prefix: z.string().default(''),
    /** Up to ~48 chars of context immediately after the quote. */
    suffix: z.string().default(''),
    /** Character offsets in the revision the anchor was created against (advisory). */
    start: z.number().int().nonnegative().optional(),
    end: z.number().int().nonnegative().optional(),
  })
  .refine((a) => a.start == null || a.end == null || a.end >= a.start, {
    message: '`end` must be >= `start`',
    path: ['end'],
  });
export type TextAnchor = z.infer<typeof TextAnchor>;

export const AnchorState = z.enum(['anchored', 'relocated', 'orphaned']);
export type AnchorState = z.infer<typeof AnchorState>;

/** What a note/action item is attached to. */
export const Target = z.discriminatedUnion('scope', [
  z.object({ scope: z.literal('document') }),
  z.object({
    scope: z.literal('section'),
    headingPath: z.array(z.string()).min(1),
    blockId: z.string().min(1).optional(),
  }),
  z.object({
    scope: z.literal('range'),
    anchor: TextAnchor,
    anchorState: AnchorState.default('anchored'),
  }),
]);
export type Target = z.infer<typeof Target>;

export const DOCUMENT_TARGET: Target = { scope: 'document' };

/** Coarse document-order key for sorting notes/items top-to-bottom. */
export function targetSortKey(target: Target): number {
  if (target.scope === 'range') return target.anchor.start ?? Number.MAX_SAFE_INTEGER - 1;
  if (target.scope === 'section') return Number.MAX_SAFE_INTEGER - 1;
  return Number.MAX_SAFE_INTEGER;
}

export function describeTarget(target: Target): string {
  switch (target.scope) {
    case 'document':
      return 'whole article';
    case 'section':
      return target.headingPath.join(' › ');
    case 'range': {
      const { headingPath, quote } = target.anchor;
      const where = headingPath.length ? `${headingPath.join(' › ')}: ` : '';
      const snippet = quote.length > 80 ? `${quote.slice(0, 77)}…` : quote;
      return `${where}“${snippet}”`;
    }
  }
}

/* ------------------------------------------------------------------------- *
 * Drafts & revisions  (M1.2 "draft # awareness")
 * ------------------------------------------------------------------------- */

/** Pointer to an exact revision of an exact draft. Stamped on every artifact. */
export const DraftRevisionRef = z.object({
  draftId: Id,
  revision: z.number().int().positive(),
});
export type DraftRevisionRef = z.infer<typeof DraftRevisionRef>;

export function formatRevisionRef(ref: DraftRevisionRef): string {
  return `${ref.draftId}@v${ref.revision}`;
}

export function sameRevision(a: DraftRevisionRef, b: DraftRevisionRef): boolean {
  return a.draftId === b.draftId && a.revision === b.revision;
}

/** What produced a revision. */
export const DraftOrigin = z.enum([
  'pipeline', // draft-article / illustrate-article
  'update-article', // agentic merge of annotations + accepted items
  'manual-edit', // user typed in the editor
  'raw-import', // M2.1 "I already have a draft"
  'lens-output', // an output-lens artifact revision (M4.2)
  'revise-artifact', // M4.2 "✎ Revise"
  'rollback', // restore of an earlier revision (appended, never destructive)
]);
export type DraftOrigin = z.infer<typeof DraftOrigin>;

export const DraftRevision = z.object({
  schemaVersion: z.number().int().positive().default(SCHEMA_VERSION),
  draftId: Id,
  /** Monotonic, 1-based. Displayed as "v3". */
  revision: z.number().int().positive(),
  parentRevision: z.number().int().positive().nullable().default(null),

  createdAt: IsoDateTime,
  createdBy: Author,
  origin: DraftOrigin,
  /** Optional short user/agent label, e.g. "post-socratic pass". */
  label: z.string().max(120).optional(),

  content: Markdown,
  contentHash: z.string().min(1),
  wordCount: z.number().int().nonnegative().default(0),

  /** Agent-authored "what changed and why", rendered in the history drawer. */
  changelog: Markdown.optional(),

  /** Provenance: exactly what was folded in to produce this revision. */
  appliedRevisionItemIds: z.array(Id).default([]),
  appliedAnnotationIds: z.array(Id).default([]),
  /** Free-text instruction that drove the mutation, if any. */
  instruction: z.string().optional(),

  /**
   * True when an artifact/annotation references this revision; frozen revisions are
   * never pruned by the retention policy.
   */
  frozen: z.boolean().default(false),
});
export type DraftRevision = z.infer<typeof DraftRevision>;

export const Draft = z
  .object({
    schemaVersion: z.number().int().positive().default(SCHEMA_VERSION),
    id: Id,
    /** 'article' for the main draft; an OutputLensId for revisable artifacts (M4.2). */
    kind: z.union([z.literal('article'), OutputLensId]).default('article'),
    title: z.string().default('Untitled'),
    createdAt: IsoDateTime,
    updatedAt: IsoDateTime,
    /** Revision number currently shown as "current". */
    headRevision: z.number().int().positive(),
    revisions: z.array(DraftRevision).min(1),
    /** For artifact drafts: which article revision this artifact was generated from. */
    sourceRef: DraftRevisionRef.optional(),
  })
  .superRefine((draft, ctx) => {
    const seen = new Set<number>();
    for (const rev of draft.revisions) {
      if (rev.draftId !== draft.id) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `revision v${rev.revision} has mismatched draftId`,
          path: ['revisions'],
        });
      }
      if (seen.has(rev.revision)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `duplicate revision number v${rev.revision}`,
          path: ['revisions'],
        });
      }
      seen.add(rev.revision);
    }
    if (!seen.has(draft.headRevision)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `headRevision v${draft.headRevision} does not exist`,
        path: ['headRevision'],
      });
    }
  });
export type Draft = z.infer<typeof Draft>;

export function countWords(markdown: string): number {
  return markdown.trim().length === 0 ? 0 : markdown.trim().split(/\s+/).length;
}

export function getRevision(draft: Draft, revision: number): DraftRevision | undefined {
  return draft.revisions.find((r) => r.revision === revision);
}

export function headRevision(draft: Draft): DraftRevision {
  const head = getRevision(draft, draft.headRevision);
  if (!head) throw new Error(`draft ${draft.id} has no head revision`);
  return head;
}

export function refOf(rev: DraftRevision): DraftRevisionRef {
  return { draftId: rev.draftId, revision: rev.revision };
}

/** True when an artifact was generated against something older than head. */
export function isStale(draft: Draft, ref: DraftRevisionRef): boolean {
  return ref.draftId === draft.id && ref.revision < draft.headRevision;
}

/** Append an immutable revision. Returns the new draft + the new revision. */
export function appendRevision(
  draft: Draft,
  input: {
    content: Markdown;
    origin: DraftOrigin;
    createdBy?: Author;
    label?: string;
    changelog?: Markdown;
    instruction?: string;
    appliedRevisionItemIds?: Id[];
    appliedAnnotationIds?: Id[];
  },
): { draft: Draft; revision: DraftRevision } {
  const parent = headRevision(draft);
  const revision: DraftRevision = {
    schemaVersion: SCHEMA_VERSION,
    draftId: draft.id,
    revision: parent.revision + 1,
    parentRevision: parent.revision,
    createdAt: nowIso(),
    createdBy: input.createdBy ?? HUMAN_AUTHOR,
    origin: input.origin,
    label: input.label,
    content: input.content,
    contentHash: contentHash(input.content),
    wordCount: countWords(input.content),
    changelog: input.changelog,
    appliedRevisionItemIds: input.appliedRevisionItemIds ?? [],
    appliedAnnotationIds: input.appliedAnnotationIds ?? [],
    instruction: input.instruction,
    frozen: false,
  };
  return {
    revision,
    draft: {
      ...draft,
      updatedAt: revision.createdAt,
      headRevision: revision.revision,
      revisions: [...draft.revisions, revision],
    },
  };
}

export function createDraft(input: {
  id?: Id;
  kind?: Draft['kind'];
  title?: string;
  content: Markdown;
  origin?: DraftOrigin;
  createdBy?: Author;
  sourceRef?: DraftRevisionRef;
}): Draft {
  const id = input.id ?? newId('draft');
  const createdAt = nowIso();
  const revision: DraftRevision = {
    schemaVersion: SCHEMA_VERSION,
    draftId: id,
    revision: 1,
    parentRevision: null,
    createdAt,
    createdBy: input.createdBy ?? HUMAN_AUTHOR,
    origin: input.origin ?? 'raw-import',
    content: input.content,
    contentHash: contentHash(input.content),
    wordCount: countWords(input.content),
    appliedRevisionItemIds: [],
    appliedAnnotationIds: [],
    frozen: false,
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
    sourceRef: input.sourceRef,
  };
}

/* ------------------------------------------------------------------------- *
 * Artifact envelope (what a lens run produces)
 * ------------------------------------------------------------------------- */

export const ArtifactFormat = z.enum(['markdown', 'html', 'latex', 'pdf', 'json', 'text']);
export type ArtifactFormat = z.infer<typeof ArtifactFormat>;

export const LensArtifact = z.object({
  schemaVersion: z.number().int().positive().default(SCHEMA_VERSION),
  id: Id,
  lensId: LensId,
  /** The exact draft revision this artifact was generated from. */
  sourceRef: DraftRevisionRef,
  format: ArtifactFormat.default('markdown'),
  content: z.string(),
  contentHash: z.string().min(1),
  createdAt: IsoDateTime,
  createdBy: Author,
  status: RunStatus.default('ready'),
  /** Populated on status === 'error'. */
  error: z.string().optional(),
  /** Render/tool log (e.g. LaTeX output for the PDF lens). */
  log: z.string().optional(),
  /** Ids of RevisionBatch records derived from this artifact. */
  ingestedBatchIds: z.array(Id).default([]),
  tokensIn: z.number().int().nonnegative().optional(),
  tokensOut: z.number().int().nonnegative().optional(),
  durationMs: z.number().int().nonnegative().optional(),
});
export type LensArtifact = z.infer<typeof LensArtifact>;