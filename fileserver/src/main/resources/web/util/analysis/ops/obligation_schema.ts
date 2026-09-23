/**
 * Schema for `obligations.json`, produced by `extract_obligations.op.md`
 * from a CSIR document (idea.md §6.1, Layer 2).
 *
 * Obligations are raised by *deterministic* recognizers over CSIR patterns.
 * They decide the coverage of the analysis. Each carries a `slot` naming the
 * kind of assumption or specification that would discharge it; agents later
 * fill slots (see ledger_schema.ts) but never invent obligations.
 */

export type ObligationKind =
  | "termination"
  | "no-overflow"
  | "in-bounds"
  | "no-trap"
  | "no-panic"
  | "aliasing"
  | "data-race-freedom"
  | "deadlock-freedom"
  | "framing"
  | "resource-bound"
  | "spec-fidelity"
  | "functional-spec"
  | string;

export type Criticality = "low" | "medium" | "high" | "critical";

/** What kind of fact would discharge the obligation (idea.md §6.3 "Kind"). */
export type SlotKind =
  | "precondition"
  | "postcondition"
  | "loop-invariant"
  | "loop-variant"
  | "termination-measure"
  | "representation-invariant"
  | "frame-condition"
  | "environmental-fact"
  | "idealization"
  | "none";

export interface SourceLocation {
  /** Path relative to the analysis root. */
  file: string;
  line?: number;
  /** Fully qualified unit name as used in `csir.md`. */
  function?: string;
}

export interface Obligation {
  /** Stable id, e.g. `obl:core::search::binary_search#mid_no_overflow`. */
  id: string;
  title?: string;
  kind: ObligationKind;
  /** The CSIR recognizer that raised it, e.g. `arith.add[overflow=trap]`. */
  pattern: string;
  location: SourceLocation;
  /** The proof goal in CSIR predicate syntax, e.g. `lo + (hi - lo) / 2 < 2^32`. */
  statement: string;
  description?: string;
  /** Lowest ladder rung (0–5) at which the obligation exists at all. */
  rung: number;
  criticality?: Criticality;
  slot: { kind: SlotKind; hint?: string };
  /** Ledger ids known to discharge it (filled in by later stages). */
  discharged_by?: string[];
  tags?: string[];
}

export interface ObligationSet {
  source_csir_file: string;
  /** The analysed unit(s), e.g. `core::search`. */
  target: string;
  language?: string;
  /** Build profile the recognizers assumed (overflow policy depends on it). */
  profile?: string;
  obligations: Obligation[];
  /** Units the front end refused to model — never silently approximated (F1). */
  unanalyzed?: { file: string; unit?: string; reason: string }[];
  generated_at?: string;
}

export const OBLIGATION_SET_JSON_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "ObligationSet",
  type: "object",
  required: ["source_csir_file", "target", "obligations"],
  additionalProperties: false,
  properties: {
    source_csir_file: { type: "string" },
    target: { type: "string" },
    language: { type: "string" },
    profile: { type: "string" },
    generated_at: { type: "string" },
    unanalyzed: {
      type: "array",
      items: {
        type: "object",
        required: ["file", "reason"],
        additionalProperties: false,
        properties: {
          file: { type: "string" },
          unit: { type: "string" },
          reason: { type: "string" },
        },
      },
    },
    obligations: {
      type: "array",
      items: {
        type: "object",
        required: ["id", "kind", "pattern", "location", "statement", "rung", "slot"],
        additionalProperties: false,
        properties: {
          id: { type: "string" },
          title: { type: "string" },
          kind: { type: "string" },
          pattern: { type: "string" },
          location: {
            type: "object",
            required: ["file"],
            additionalProperties: false,
            properties: {
              file: { type: "string" },
              line: { type: "integer" },
              function: { type: "string" },
            },
          },
          statement: { type: "string" },
          description: { type: "string" },
          rung: { type: "integer", minimum: 0, maximum: 5 },
          criticality: {
            type: "string",
            enum: ["low", "medium", "high", "critical"],
            default: "medium",
          },
          slot: {
            type: "object",
            required: ["kind"],
            additionalProperties: false,
            properties: {
              kind: {
                type: "string",
                enum: [
                  "precondition", "postcondition", "loop-invariant", "loop-variant",
                  "termination-measure", "representation-invariant", "frame-condition",
                  "environmental-fact", "idealization", "none",
                ],
              },
              hint: { type: "string" },
            },
          },
          discharged_by: { type: "array", items: { type: "string" } },
          tags: { type: "array", items: { type: "string" } },
        },
      },
    },
  },
} as const;