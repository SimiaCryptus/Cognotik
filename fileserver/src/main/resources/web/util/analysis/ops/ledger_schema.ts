/**
 * Schema for `ledger.json`, the Assumption Ledger (idea.md §7), produced by
 * `mine_assumptions.op.md` and edited by humans through the UI (ratify /
 * reject). The ledger is the unit of trust: a verification claim is always
 * "property P holds *relative to* this assumption set".
 */

/** idea.md §2 — which mechanism owns the assumption. */
export type Bucket = "A-language-defined" | "B-programmer-implied" | "C-idealization";

export type AssumptionKind =
  | "precondition"
  | "postcondition"
  | "loop-invariant"
  | "loop-variant"
  | "termination-measure"
  | "representation-invariant"
  | "frame-condition"
  | "environmental-fact"
  | "idealization";

/** idea.md §6.3 — how the assumption is disposed of downstream. */
export type DischargeStrategy =
  | "provable"           // should become a theorem
  | "checkable"          // insert a runtime assertion
  | "enforced-elsewhere" // guaranteed by validator / config / type at a boundary
  | "axiomatic"          // genuinely assumed — must be human-ratified
  | "idealization";      // tracked on the semantics ladder

export type Scope = "call-site" | "function" | "module" | "deployment";

export type AssumptionStatus = "candidate" | "active" | "stale" | "refuted" | "rejected";

export interface Provenance {
  /** e.g. `agent:assumption-miner@v1`, `abstract-interpretation:interval`, `human`, `language-spec`. */
  source: string;
  corroborated_by?: string[];
  human_ratified: boolean;
  ratified_by?: string;
  /** ISO-8601. */
  ratified_at?: string;
}

export interface Assumption {
  /** Stable id, e.g. `asm:core::search::binary_search#len_bound`. */
  id: string;
  kind: AssumptionKind;
  bucket: Bucket;
  scope: Scope;
  /** Qualified unit the assumption is about. */
  target: string;
  statement: { lang: "csir-pred" | "lean" | "text"; text: string };
  provenance: Provenance;
  discharge: {
    strategy: DischargeStrategy;
    justification?: string;
    residual_risk?: string;
    /** Ladder rung an idealization is lifted at, if applicable. */
    rung?: number;
  };
  /** 0–1, provenance-weighted prior. */
  confidence?: number;
  status: AssumptionStatus;
  /** Obligation ids (obligation_schema.ts) that cite this assumption. */
  used_by?: string[];
  /** Where the round-tripped comment lives (idea.md §7.2). */
  source_anchor?: {
    file: string;
    line?: number;
    /** e.g. `/// @assume(env) xs.len() < 2^31  [asm:...#len_bound]` */
    comment_form: string;
  };
  /** Vacuity / trivialisation flags (idea.md F2). */
  warnings?: string[];
}

export interface AssumptionLedger {
  source_obligations_file: string;
  /** e.g. `LangSem@0.1` — a version bump invalidates every dependent proof. */
  langsem_version: string;
  target: string;
  assumptions: Assumption[];
  generated_at?: string;
}

export const ASSUMPTION_LEDGER_JSON_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "AssumptionLedger",
  type: "object",
  required: ["source_obligations_file", "langsem_version", "target", "assumptions"],
  additionalProperties: false,
  properties: {
    source_obligations_file: { type: "string" },
    langsem_version: { type: "string" },
    target: { type: "string" },
    generated_at: { type: "string" },
    assumptions: {
      type: "array",
      items: {
        type: "object",
        required: ["id", "kind", "bucket", "scope", "target", "statement", "provenance", "discharge", "status"],
        additionalProperties: false,
        properties: {
          id: { type: "string" },
          kind: {
            type: "string",
            enum: [
              "precondition", "postcondition", "loop-invariant", "loop-variant",
              "termination-measure", "representation-invariant", "frame-condition",
              "environmental-fact", "idealization",
            ],
          },
          bucket: {
            type: "string",
            enum: ["A-language-defined", "B-programmer-implied", "C-idealization"],
          },
          scope: { type: "string", enum: ["call-site", "function", "module", "deployment"] },
          target: { type: "string" },
          statement: {
            type: "object",
            required: ["lang", "text"],
            additionalProperties: false,
            properties: {
              lang: { type: "string", enum: ["csir-pred", "lean", "text"] },
              text: { type: "string" },
            },
          },
          provenance: {
            type: "object",
            required: ["source", "human_ratified"],
            additionalProperties: false,
            properties: {
              source: { type: "string" },
              corroborated_by: { type: "array", items: { type: "string" } },
              human_ratified: { type: "boolean" },
              ratified_by: { type: "string" },
              ratified_at: { type: "string" },
            },
          },
          discharge: {
            type: "object",
            required: ["strategy"],
            additionalProperties: false,
            properties: {
              strategy: {
                type: "string",
                enum: ["provable", "checkable", "enforced-elsewhere", "axiomatic", "idealization"],
              },
              justification: { type: "string" },
              residual_risk: { type: "string" },
              rung: { type: "integer", minimum: 0, maximum: 5 },
            },
          },
          confidence: { type: "number", minimum: 0, maximum: 1 },
          status: { type: "string", enum: ["candidate", "active", "stale", "refuted", "rejected"] },
          used_by: { type: "array", items: { type: "string" } },
          source_anchor: {
            type: "object",
            required: ["file", "comment_form"],
            additionalProperties: false,
            properties: {
              file: { type: "string" },
              line: { type: "integer" },
              comment_form: { type: "string" },
            },
          },
          warnings: { type: "array", items: { type: "string" } },
        },
      },
    },
  },
} as const;