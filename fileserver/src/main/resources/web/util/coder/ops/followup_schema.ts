/**
 * Schema definitions for the "followup tasks" JSON file produced from a
 * completed research document (see research_followup.op.md).
 *
 * The generated JSON file lists concrete, actionable follow-up work items
 * that were identified while researching a topic. Each item is intended to
 * be turned into (or directly consumed as) a docops task -- primarily a
 * `FileModification` task -- so that it can be picked up and executed by
 * downstream tooling (e.g. DocProcessor / UnifiedHarness) without further
 * clarification.
 */

/**
 * The docops task type that should be used to implement this followup item.
 * `FileModification` is the default/primary type since most followups are
 * concrete edits to one or more existing or new files. Other task types are
 * supported for followups that don't map cleanly onto a single-file edit
 * (e.g. a followup that itself needs to be broken down further would use
 * `SubPlan`).
 */
export type FollowupTaskType =
  | "FileModification"
  | "SubPlan"
  | "CodeReview"
  | string;

/**
 * Priority/urgency hint for ordering or triaging followup work.
 */
export type FollowupPriority = "low" | "medium" | "high" | "critical";

/**
 * A single actionable followup item extracted from a research document.
 */
export interface FollowupTask {
  /**
   * Optional stable identifier for this task, used for `depends_on`
   * references between tasks within the same followup file.
   */
  id?: string;

  /** Short, human-readable title summarizing the followup task. */
  title: string;

  /**
   * Full description of the work to be done. This should be detailed enough
   * to be used directly as the `task_description` for a docops
   * ModificationTask (or equivalent) without requiring additional context
   * beyond `target_files` / `related_files`.
   */
  description: string;

  /**
   * The docops task type that should be used to implement this followup.
   * Defaults to "FileModification" when omitted.
   */
  task_type?: FollowupTaskType;

  /**
   * Primary target file(s) that this followup task is expected to create or
   * modify. Paths are relative to the project/repo root unless otherwise
   * noted in `notes`.
   */
  target_files: string[];

  /**
   * Additional files that provide context but are not directly modified
   * (e.g. related source, config, or documentation files).
   */
  related_files?: string[];

  /** Priority/urgency of this followup item. Defaults to "medium". */
  priority?: FollowupPriority;

  /**
   * Free-form tags for categorizing followup items (e.g. "bug", "refactor",
   * "docs", "test-coverage").
   */
  tags?: string[];

  /**
   * IDs of other followup tasks (referencing `id`) that this task depends
   * on and should be completed after.
   */
  depends_on?: string[];

  /**
   * Any additional free-form notes, caveats, or open questions relevant to
   * implementing this followup task.
   */
  notes?: string;
}

/**
 * Root structure of a `*.followup.json` file generated from a research
 * document via `research_followup.op.md`.
 */
export interface FollowupPlan {
  /** Path (relative) to the research document this plan was derived from. */
  source_research_file: string;

  /** Short summary of the overall research topic/question. */
  summary: string;

  /**
   * The list of concrete followup tasks identified during research. This is
   * the primary payload consumed by downstream docops tooling to schedule
   * FileModification (or other) tasks.
   */
  tasks: FollowupTask[];

  /** ISO-8601 timestamp of when this followup plan was generated. */
  generated_at?: string;
}

/**
 * JSON Schema (draft-07) representation of `FollowupPlan`, suitable for
 * runtime validation (e.g. with ajv) of generated followup JSON files.
 */
export const FOLLOWUP_PLAN_JSON_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "FollowupPlan",
  type: "object",
  required: ["source_research_file", "summary", "tasks"],
  additionalProperties: false,
  properties: {
    source_research_file: { type: "string" },
    summary: { type: "string" },
    generated_at: { type: "string" },
    tasks: {
      type: "array",
      items: {
        type: "object",
        required: ["title", "description", "target_files"],
        additionalProperties: false,
        properties: {
          id: { type: "string" },
          title: { type: "string" },
          description: { type: "string" },
          task_type: { type: "string", default: "FileModification" },
          target_files: {
            type: "array",
            items: { type: "string" },
            minItems: 1,
          },
          related_files: {
            type: "array",
            items: { type: "string" },
          },
          priority: {
            type: "string",
            enum: ["low", "medium", "high", "critical"],
            default: "medium",
          },
          tags: {
            type: "array",
            items: { type: "string" },
          },
          depends_on: {
            type: "array",
            items: { type: "string" },
          },
          notes: { type: "string" },
        },
      },
    },
  },
} as const;