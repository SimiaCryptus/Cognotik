export type ConceptDifficulty = "foundational" | "intermediate" | "advanced";

export type ConceptDomain =
  | "geometry"
  | "topology"
  | "algebra"
  | "combinatorics"
  | "physics"
  | "computation"
  | "general";

export interface ConceptReference {
  /** The unique identifier of the referenced concept */
  id: string;
  /** Optional human-readable label override (defaults to the referenced concept's term) */
  label?: string;
}

export interface ConceptRelations {
  /**
   * Concepts that MUST be understood before this one.
   * Forms a directed prerequisite graph.
   */
  requires: ConceptReference[];

  /**
   * Concepts that are thematically or structurally related,
   * but not strict prerequisites.
   */
  related: ConceptReference[];

  /**
   * Concepts that build directly on top of this one.
   * Inverse of `requires` — populated automatically or manually.
   */
  enabledBy: ConceptReference[];

  /**
   * Concepts that are definitionally equivalent or near-equivalent
   * (e.g. alternate names, dual representations).
   */
  synonyms: ConceptReference[];
}

export interface ConceptExample {
  /** Short label for the example */
  title: string;
  /** Full description or worked example */
  body: string;
  /** Optional figure, diagram, or image path */
  figure?: string;
}

export interface ConceptFormula {
  /** LaTeX or plain-text representation of the formula */
  expression: string;
  /** Human-readable description of what the formula represents */
  description: string;
  /** Variable definitions used in the formula */
  variables?: Record<string, string>;
}

export interface GlossaryConcept {
  // ── Identity ────────────────────────────────────────────────────────────────

  /** Unique slug identifier, e.g. "pentagrid-duality" */
  id: string;

  /** Canonical display name of the concept */
  term: string;

  /** Alternative names or spellings */
  aliases: string[];

  // ── Classification ───────────────────────────────────────────────────────────

  /** Primary knowledge domain */
  domain: ConceptDomain;

  /** Additional domains this concept spans */
  crossDomains: ConceptDomain[];

  /** Difficulty level relative to the overall glossary */
  difficulty: ConceptDifficulty;

  /** Free-form tags for filtering and search */
  tags: string[];

  // ── Content ──────────────────────────────────────────────────────────────────

  /** One-sentence summary shown in index views */
  summary: string;

  /** Full markdown-compatible definition */
  definition: string;

  /** Worked examples or concrete illustrations */
  examples: ConceptExample[];

  /** Key formulas associated with this concept */
  formulas: ConceptFormula[];

  /** External or internal references (URLs, paper titles, etc.) */
  references: string[];

  // ── Relations ────────────────────────────────────────────────────────────────

  relations: ConceptRelations;

  // ── Metadata ─────────────────────────────────────────────────────────────────

  /** ISO 8601 creation timestamp */
  createdAt: string;

  /** ISO 8601 last-updated timestamp */
  updatedAt: string;

  /** Whether this entry is complete and ready for display */
  published: boolean;
}

/**
 * The top-level glossary model.
 * Acts as the single source of truth for all concepts in the glossary.
 */
export interface Glossary {
  /** Schema version for forward-compatibility */
  version: string;

  /** Human-readable title of this glossary */
  title: string;

  /** Short description of the glossary's scope */
  description: string;

  /** All concepts, keyed by their `id` for O(1) lookup */
  concepts: Record<string, GlossaryConcept>;
}

// ── Helper types ──────────────────────────────────────────────────────────────

/** A lightweight view of a concept used in index/listing contexts */
export type ConceptSummary = Pick<
  GlossaryConcept,
  "id" | "term" | "aliases" | "domain" | "difficulty" | "tags" | "summary" | "published"
>;

/** Partial concept used when authoring a new entry before it is complete */
export type DraftConcept = Partial<GlossaryConcept> &
  Pick<GlossaryConcept, "id" | "term">;