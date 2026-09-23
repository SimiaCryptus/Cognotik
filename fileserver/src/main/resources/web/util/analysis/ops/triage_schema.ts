/**
 * Schema for `triage.json`, produced by `triage.op.md` from `proofs.md` and
 * `ledger.json` (idea.md §11, Layer 6). Every obligation gets a diagnosis;
 * non-discharged ones get a concrete repair plan that the UI can turn into
 * a per-item doc-op (`triage.<id>.md`) and execute.
 */

/** The five failure categories of idea.md §11, plus terminal states. */
export type Diagnosis =
  | "real-bug"            // counterexample reproduces against the real program
  | "missing-assumption"  // counterexample unreachable in practice
  | "wrong-specification" // counterexample is intended behaviour (dangerous)
  | "missing-lemma"       // goal true, automation stalled
  | "front-end-defect"    // CSIR disagrees with real execution
  | "discharged"          // no repair needed; see `discharge_level`
  | "open";               // budget exhausted, no classification possible

/** Trust level of a discharge — never silently merged (idea.md §10.1). */
export type DischargeLevel =
  | "kernel-checked"
  | "smt-attested"
  | "bounded-checked"
  | "refuted"
  | "proposed"
  | "timeout";

export type RepairAction =
  | "file-defect"
  | "add-assumption"
  | "revise-spec"
  | "add-lemma"
  | "file-frontend-bug"
  | "none";

export interface Counterexample {
  /** Model-level witness (variable bindings). */
  model: Record<string, string>;
  /** Source-level reproducing input, if it could be lifted. */
  input?: string;
  /** Result of replaying against the real toolchain — mandatory step. */
  replay: "reproduced" | "not-reproduced" | "not-replayed";
  replay_notes?: string;
}

export interface RepairPlan {
  action: RepairAction;
  /** docops task type used to execute it; defaults to FileModification. */
  task_type?: "FileModification" | "SubPlan" | "CodeReview" | string;
  /** Files to create/modify, relative to the analysis root. */
  target_files: string[];
  related_files?: string[];
  /** Precise enough to run unattended. */
  description: string;
  /** Spec revisions must be ratified before status may become "verified". */
  requires_ratification?: boolean;
}

export interface TriageItem {
  id: string;
  obligation_id: string;
  diagnosis: Diagnosis;
  discharge_level?: DischargeLevel;
  /** Why this diagnosis and not another one. */
  evidence: string;
  counterexample?: Counterexample;
  /** Ledger ids the verdict depends on. */
  assumptions_used?: string[];
  /** Ledger ids this item proposes to add / mark stale / refute. */
  ledger_changes?: { id: string; change: "add" | "stale" | "refuted" | "revise"; note?: string }[];
  repair?: RepairPlan;
}

export interface TriageReport {
  source_proofs_file: string;
  source_ledger_file?: string;
  target: string;
  /** Highest rung at which every property holds with no unproved residual. */
  highest_unconditional_rung?: number;
  items: TriageItem[];
  generated_at?: string;
}

export const TRIAGE_REPORT_JSON_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "TriageReport",
  type: "object",
  required: ["source_proofs_file", "target", "items"],
  additionalProperties: false,
  properties: {
    source_proofs_file: { type: "string" },
    source_ledger_file: { type: "string" },
    target: { type: "string" },
    highest_unconditional_rung: { type: "integer", minimum: 0, maximum: 5 },
    generated_at: { type: "string" },
    items: {
      type: "array",
      items: {
        type: "object",
        required: ["id", "obligation_id", "diagnosis", "evidence"],
        additionalProperties: false,
        properties: {
          id: { type: "string" },
          obligation_id: { type: "string" },
          diagnosis: {
            type: "string",
            enum: [
              "real-bug", "missing-assumption", "wrong-specification",
              "missing-lemma", "front-end-defect", "discharged", "open",
            ],
          },
          discharge_level: {
            type: "string",
            enum: ["kernel-checked", "smt-attested", "bounded-checked", "refuted", "proposed", "timeout"],
          },
          evidence: { type: "string" },
          counterexample: {
            type: "object",
            required: ["model", "replay"],
            additionalProperties: false,
            properties: {
              model: { type: "object", additionalProperties: { type: "string" } },
              input: { type: "string" },
              replay: { type: "string", enum: ["reproduced", "not-reproduced", "not-replayed"] },
              replay_notes: { type: "string" },
            },
          },
          assumptions_used: { type: "array", items: { type: "string" } },
          ledger_changes: {
            type: "array",
            items: {
              type: "object",
              required: ["id", "change"],
              additionalProperties: false,
              properties: {
                id: { type: "string" },
                change: { type: "string", enum: ["add", "stale", "refuted", "revise"] },
                note: { type: "string" },
              },
            },
          },
          repair: {
            type: "object",
            required: ["action", "target_files", "description"],
            additionalProperties: false,
            properties: {
              action: {
                type: "string",
                enum: ["file-defect", "add-assumption", "revise-spec", "add-lemma", "file-frontend-bug", "none"],
              },
              task_type: { type: "string", default: "FileModification" },
              target_files: { type: "array", items: { type: "string" } },
              related_files: { type: "array", items: { type: "string" } },
              description: { type: "string" },
              requires_ratification: { type: "boolean" },
            },
          },
        },
      },
    },
  },
} as const;