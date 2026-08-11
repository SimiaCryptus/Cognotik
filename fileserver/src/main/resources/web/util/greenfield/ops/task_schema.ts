/**
   * Schema definitions for the **build task plans** produced by the
   * `greenfield` pipeline (one plan per phase: `tasks/<phase-id>.json`).
   *
   * This shape is deliberately a *copy* of `reviewer/ops/followup_schema.ts`'s
   * `FollowupTask`, not an import: these are wire formats and coupling two
   * apps' schemas will hurt later (see ../idea.md, "Schema reuse"). The only
   * additions are `source_components` and `phase_id` back-references into the
   * planning artifacts described by ./plan_schema.ts.
   *
   * Producers:
   *  - plan_tasks.op.md — one BuildPlan per phase in `plan/phases.json`.
   *
   * Consumers:
   *  - impl_task.op.md — executes a whole plan through a `SubPlan` task.
   *  - ../index.html   — lists every task under `tasks/**.json` and executes
   *    them individually by synthesizing a temporary doc-op file in `tmp/`
   *    whose front-matter is derived from `target_files`, `related_files` and
   *    `task_type`.
   */

  /** Relative urgency of a build task. Defaults to "medium". */
  export type TaskPriority = "low" | "medium" | "high" | "critical";

  /**
   * Cognotik task type used to execute the task. `FileModification` is the
   * default and by far the most common; the string is intentionally open so new
   * task types can be referenced without changing this file.
   */
  export type BuildTaskType =
    | "FileModification"
    | "Inquiry"
    | "Search"
    | "RunShellCommand"
    | "SubPlan"
    | (string & {});

  /**
   * A single, independently executable unit of work: normally "create or
   * modify this file so that it does X".
   */
  export interface BuildTask {
    /**
     * Stable identifier, unique within the plan. Used for `depends_on` links,
     * for the name of the generated doc-op file, and for run-status bookkeeping
     * in the UI. Prefer short kebab-case ids (e.g. "create-csv-reader").
     */
    id: string;

    /** Short imperative title (a single line). */
    title: string;

    /**
     * Full instruction handed to the executing task. Must be self-contained:
     * what to create/change, where, what interfaces to expose, and what the
     * result must satisfy. Do not assume the executor has read the plans.
     */
    description: string;

    /** Cognotik task type used to execute this task. Defaults to "FileModification". */
    task_type?: BuildTaskType;

    /** Relative urgency. Defaults to "medium". */
    priority?: TaskPriority;

    /**
     * Files this task is expected to create or modify, relative to the analysis
     * root (i.e. what `folder:` points at). The first entry becomes the doc-op's
     * `specifies:` target, so it must be the primary file; any further entries
     * are passed as `related:` context. Must contain at least one entry,
     * otherwise the task cannot be executed.
     */
    target_files: string[];

    /**
     * Additional read-only context files (relative to the analysis root) that
     * the executing task should see.
     */
    related_files?: string[];

    /**
     * Ids of other tasks *in the same plan* that should be completed first.
     * Advisory only: the UI highlights unmet dependencies but still allows
     * out-of-order execution.
     */
    depends_on?: string[];

    /** Free-form tags for filtering/grouping (e.g. "scaffold", "test", "docs"). */
    tags?: string[];

    /** Optional extra notes, caveats or verification hints. */
    notes?: string;

    /** `Component.id`s from `plan/architecture.json` that motivated this task. */
    source_components?: string[];

    /** `UserStory.id`s from `plan/feature.json` this task helps satisfy. */
    source_stories?: string[];

    /** Rough effort hint (e.g. "small", "medium", "large"). */
    estimated_effort?: string;
  }

  /** Root structure of a `tasks/<phase-id>.json` document. */
  export interface BuildPlan {
    /** `Phase.id` from `plan/phases.json` this plan implements. */
    phase_id: string;

    /** `Phase.title`, copied so the plan reads on its own. */
    phase_title?: string;

    /** Planning documents this plan was derived from. */
    source_plan_files?: string[];

    /** Short summary of the plan: what will be built and why. */
    summary: string;

    /** The build tasks, ideally already in a sensible execution order. */
    tasks: BuildTask[];

    /** ISO-8601 timestamp of when this plan was generated. */
    generated_at?: string;
  }

  const BUILD_TASK_JSON_SCHEMA = {
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
      source_components: { type: "array", items: { type: "string" } },
      source_stories: { type: "array", items: { type: "string" } },
      estimated_effort: { type: "string" },
    },
  } as const;

  /**
   * JSON Schema (draft-07) representation of `BuildPlan`, suitable for runtime
   * validation (e.g. with ajv) of generated `tasks/**.json` files.
   */
  export const BUILD_PLAN_JSON_SCHEMA = {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "BuildPlan",
    type: "object",
    required: ["phase_id", "summary", "tasks"],
    additionalProperties: false,
    properties: {
      phase_id: { type: "string", minLength: 1 },
      phase_title: { type: "string" },
      source_plan_files: { type: "array", items: { type: "string" } },
      summary: { type: "string" },
      generated_at: { type: "string" },
      tasks: { type: "array", minItems: 1, items: BUILD_TASK_JSON_SCHEMA },
    },
  } as const;