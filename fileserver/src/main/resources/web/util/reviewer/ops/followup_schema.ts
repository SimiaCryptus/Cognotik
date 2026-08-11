/**
   * Schema definitions for the follow-up **task plans** produced by the
   * `reviewer` pipeline (see ../notes.md and ../idea.md).
   *
   * The reviewer pipeline is schema-driven: every stage emits JSON conforming to
   * a fixed, code-defined schema rather than a schema derived from the focus
   * query itself. Analysis documents are described by `analysis_schema.ts`; this
   * file describes the *actionable* output derived from them.
   *
   * Pipeline stages that produce documents conforming to these types:
   *  - plan_followup_multi.op.md  -> FollowupPlan (tasks/<review-path>.json,
   *                                  one plan per `review/<review-path>.json`)
   *  - plan_followup_single.op.md -> FollowupPlan (tasks.json, one aggregate plan)
   *
   * Consumers:
   *  - process_followup.op.md — executes a plan through a `SubPlan` task.
   *  - ../index.html — lists every task under `tasks.json` / `tasks/**.json` and
   *    executes them individually by generating a temporary doc-op file whose
   *    front-matter is derived from `target_files`, `related_files` and
   *    `task_type`.
   */
  
  /** Relative urgency of a follow-up task. Defaults to "medium". */
  export type TaskPriority = "low" | "medium" | "high" | "critical";
  
  /**
   * Cognotik task type used to execute the task. `FileModification` is the
   * default and by far the most common; the string is intentionally open so new
   * task types can be referenced without changing this file.
   */
  export type FollowupTaskType =
    | "FileModification"
    | "Inquiry"
    | "Search"
    | "RunShellCommand"
    | "SubPlan"
    | (string & {});
  
  /** Back-reference from a task to the finding(s) that motivated it. */
  export interface FindingReference {
    /** Path (relative to the project root) of the reviewed file. */
    file?: string;
  
    /** `Finding.id` within that file's analysis, when the finding carried one. */
    finding_id?: string;
  
    /** Optional line number(s)/range, mirroring `Finding.location`. */
    location?: string;
  
    /** Short restatement of the finding, so the task is readable on its own. */
    message?: string;
  }
  
  /**
   * A single, independently executable unit of work derived from the review
   * findings.
   */
  export interface FollowupTask {
    /**
     * Stable identifier, unique within the plan. Used for `depends_on` links, for
     * the name of the generated doc-op file, and for run-status bookkeeping in the
     * UI. Prefer short kebab-case ids (e.g. "guard-null-config").
     */
    id: string;
  
    /** Short imperative title (a single line). */
    title: string;
  
    /**
     * Full instruction handed to the executing task. Should be self-contained:
     * what to change, where, and what the result must satisfy.
     */
    description: string;
  
    /** Cognotik task type used to execute this task. Defaults to "FileModification". */
    task_type?: FollowupTaskType;
  
    /** Relative urgency. Defaults to "medium". */
    priority?: TaskPriority;
  
    /**
     * Files this task is expected to create or modify, relative to the project
     * root. The first entry becomes the doc-op's `specifies:` target, so it must
     * be the primary file; any further entries are passed as `related:` context.
     * Must contain at least one entry, otherwise the task cannot be executed.
     */
    target_files: string[];
  
    /**
     * Additional read-only context files (relative to the project root) that the
     * executing task should see.
     */
    related_files?: string[];
  
    /**
     * Ids of other tasks *in the same plan* that should be completed first.
     * Advisory only: the UI highlights unmet dependencies but still allows
     * out-of-order execution.
     */
    depends_on?: string[];
  
    /** Free-form tags for filtering/grouping (e.g. "error-handling", "cleanup"). */
    tags?: string[];
  
    /** Optional extra notes, caveats or verification hints. */
    notes?: string;
  
    /** Findings that motivated this task. */
    source_findings?: FindingReference[];
  
    /** Rough effort hint (e.g. "small", "medium", "large"). */
    estimated_effort?: string;
  }
  
  /**
   * Root structure of a task-plan document: either `tasks.json` (aggregate) or
   * `tasks/<review-path>.json` (one plan per review document).
   */
  export interface FollowupPlan {
    /**
     * Path (relative to the project root) of the review document this plan was
     * derived from, e.g. `cognotik-tools/reviewer/review/src/Foo.kt.json`.
     * Omitted/replaced by `source_review_files` for aggregate plans.
     */
    source_analysis_file?: string;
  
    /** For aggregate plans: every review document that contributed tasks. */
    source_review_files?: string[];
  
    /** The focus query (contents of `focus.md`) this plan was produced under. */
    focus_query?: string;
  
    /** Short summary of the plan: what will change and why. */
    summary: string;
  
    /** The follow-up tasks, ideally already in a sensible execution order. */
    tasks: FollowupTask[];
  
    /** ISO-8601 timestamp of when this plan was generated. */
    generated_at?: string;
  }
  
  const FINDING_REFERENCE_JSON_SCHEMA = {
    type: "object",
    additionalProperties: false,
    properties: {
      file: { type: "string" },
      finding_id: { type: "string" },
      location: { type: "string" },
      message: { type: "string" },
    },
  } as const;
  
  const FOLLOWUP_TASK_JSON_SCHEMA = {
    type: "object",
    required: ["id", "title", "description", "target_files"],
    additionalProperties: false,
    properties: {
      id: { type: "string", minLength: 1 },
      title: { type: "string", minLength: 1 },
      description: { type: "string", minLength: 1 },
      task_type: { type: "string", default: "FileModification" },
      priority: {
        type: "string",
        enum: ["low", "medium", "high", "critical"],
        default: "medium",
      },
      target_files: { type: "array", minItems: 1, items: { type: "string" } },
      related_files: { type: "array", items: { type: "string" } },
      depends_on: { type: "array", items: { type: "string" } },
      tags: { type: "array", items: { type: "string" } },
      notes: { type: "string" },
      source_findings: { type: "array", items: FINDING_REFERENCE_JSON_SCHEMA },
      estimated_effort: { type: "string" },
    },
  } as const;
  
  /**
   * JSON Schema (draft-07) representation of `FollowupPlan`, suitable for runtime
   * validation (e.g. with ajv) of generated `tasks.json` / `tasks/**.json` files.
   */
  export const FOLLOWUP_PLAN_JSON_SCHEMA = {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "FollowupPlan",
    type: "object",
    required: ["summary", "tasks"],
    additionalProperties: false,
    properties: {
      source_analysis_file: { type: "string" },
      source_review_files: { type: "array", items: { type: "string" } },
      focus_query: { type: "string" },
      summary: { type: "string" },
      generated_at: { type: "string" },
      tasks: { type: "array", items: FOLLOWUP_TASK_JSON_SCHEMA },
    },
  } as const;