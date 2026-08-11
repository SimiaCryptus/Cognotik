/**
 * Schema definitions for the structured code-review analysis documents
 * produced by the `reviewer` pipeline (see ../idea.md).
 *
 * Unlike the `coder` pipeline's free-form research documents, the reviewer
 * pipeline is schema-driven: every stage emits JSON conforming to a fixed,
 * code-defined schema (this file) rather than a schema derived from the
 * focus query itself.
 *
 * Pipeline stages that produce documents conforming to these types:
  *  - process_files.op.md     -> FileAnalysis    (.review/<source-path>.json)
  *  - process_packages.op.md  -> PackageAnalysis (.review/<package-path>.json)
  *  - plan_followup_*.op.md   -> FollowupPlan    (.tasks/**.json, tasks.json —
  *                               see followup_schema.ts)
  *
  * A single review document normally holds one `FileAnalysis` (per-file review)
  * or one `PackageAnalysis` (folder rollup); the `*Plan` wrappers remain valid
  * for documents that aggregate several of them.
 */

/** Severity/urgency hint for a single finding. */
export type FindingSeverity = "info" | "low" | "medium" | "high" | "critical";

/**
 * Free-form category for a finding (e.g. "error-handling", "dead-code",
 * "security", "style", "performance", "correctness", "test-coverage",
 * "docs"). Not a closed enum, since the review categories relevant to a
 * given focus query can vary widely.
 */
export type FindingCategory = string;

/**
 * A single observation/issue identified while reviewing a file against the
 * focus query.
 */
export interface Finding {
  /** Optional stable identifier for this finding within its file. */
  id?: string;

  /** Short category label for this finding (see `FindingCategory`). */
  category: FindingCategory;

  /** Severity/urgency of this finding. Defaults to "medium". */
  severity?: FindingSeverity;

  /** Human-readable description of the issue/observation. */
  message: string;

  /**
   * Optional line number(s) or line range (e.g. "42" or "42-58") that this
   * finding refers to within `FileAnalysis.file`.
   */
  location?: string;

  /** Optional concrete suggestion for how to address this finding. */
  suggested_fix?: string;

  /**
   * Confidence that this finding is accurate/actionable, expressed as a
   * value between 0 and 1.
   */
  confidence?: number;

  /** Free-form tags for categorizing/filtering findings. */
  tags?: string[];
}

/**
 * Structured, schema'd analysis of a single file against a focus query.
 */
export interface FileAnalysis {
  /** Path to the file that was reviewed, relative to the project root. */
  file: string;

  /** Short summary of how this file relates to the focus query. */
  summary: string;

  /** Findings identified in this file relevant to the focus query. */
  findings: Finding[];

  /** ISO-8601 timestamp of when this analysis was generated. */
  generated_at?: string;
}

/**
 * Root structure of the `analysis.json` document produced by
 * `process_focus_query.op.md` from a `focus.md` query.
 */
export interface FileAnalysisPlan {
  /** The original focus query text/description this plan was derived from. */
  focus_query: string;

  /** Short summary of the overall review scope and headline findings. */
  summary: string;

  /** Per-file analyses produced while reviewing files relevant to the query. */
  files: FileAnalysis[];

  /** ISO-8601 timestamp of when this plan was generated. */
  generated_at?: string;
}

/**
 * Structured, schema'd rollup of the per-file findings within a single
 * package/folder.
 */
export interface PackageAnalysis {
  /** Path to the package/folder this summary covers, relative to the project root. */
  package: string;

  /** Files (from `FileAnalysisPlan.files`) that contributed to this rollup. */
  files: string[];

  /** Short summary of the shared/recurring findings across this package. */
  summary: string;

  /**
   * Findings that are package-wide in nature (i.e. not specific to a single
   * file), such as inconsistent patterns across files in the package.
   */
  findings: Finding[];

  /** Overall severity for this package, typically the max of its findings. */
  overall_severity?: FindingSeverity;

  /** ISO-8601 timestamp of when this summary was generated. */
  generated_at?: string;
}

/**
 * Root structure of the `packages.json` document produced by
 * `summarize_packages.op.md` from an `analysis.json` document.
 */
export interface PackageAnalysisPlan {
  /** Path (relative) to the `analysis.json` this plan was derived from. */
  source_analysis_file: string;

  /** Short summary of the overall review scope and headline findings. */
  summary: string;

  /** Package-level rollups derived from the per-file analyses. */
  packages: PackageAnalysis[];

  /** ISO-8601 timestamp of when this plan was generated. */
  generated_at?: string;
}

const FINDING_JSON_SCHEMA = {
  type: "object",
  required: ["category", "message"],
  additionalProperties: false,
  properties: {
    id: { type: "string" },
    category: { type: "string" },
    severity: {
      type: "string",
      enum: ["info", "low", "medium", "high", "critical"],
      default: "medium",
    },
    message: { type: "string" },
    location: { type: "string" },
    suggested_fix: { type: "string" },
    confidence: { type: "number", minimum: 0, maximum: 1 },
    tags: { type: "array", items: { type: "string" } },
  },
} as const;

/**
 * JSON Schema (draft-07) representation of `FileAnalysisPlan`, suitable for
 * runtime validation (e.g. with ajv) of generated `analysis.json` files.
 */
export const FILE_ANALYSIS_PLAN_JSON_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "FileAnalysisPlan",
  type: "object",
  required: ["focus_query", "summary", "files"],
  additionalProperties: false,
  properties: {
    focus_query: { type: "string" },
    summary: { type: "string" },
    generated_at: { type: "string" },
    files: {
      type: "array",
      items: {
        type: "object",
        required: ["file", "summary", "findings"],
        additionalProperties: false,
        properties: {
          file: { type: "string" },
          summary: { type: "string" },
          findings: { type: "array", items: FINDING_JSON_SCHEMA },
          generated_at: { type: "string" },
        },
      },
    },
  },
} as const;

/**
 * JSON Schema (draft-07) representation of `PackageAnalysisPlan`, suitable
 * for runtime validation (e.g. with ajv) of generated `packages.json` files.
 */
export const PACKAGE_ANALYSIS_PLAN_JSON_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "PackageAnalysisPlan",
  type: "object",
  required: ["source_analysis_file", "summary", "packages"],
  additionalProperties: false,
  properties: {
    source_analysis_file: { type: "string" },
    summary: { type: "string" },
    generated_at: { type: "string" },
    packages: {
      type: "array",
      items: {
        type: "object",
        required: ["package", "files", "summary", "findings"],
        additionalProperties: false,
        properties: {
          package: { type: "string" },
          files: { type: "array", items: { type: "string" } },
          summary: { type: "string" },
          findings: { type: "array", items: FINDING_JSON_SCHEMA },
          overall_severity: {
            type: "string",
            enum: ["info", "low", "medium", "high", "critical"],
          },
          generated_at: { type: "string" },
        },
      },
    },
  },
} as const;