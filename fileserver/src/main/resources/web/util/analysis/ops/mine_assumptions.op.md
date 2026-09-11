---
transforms:
  - (.*)obligations.json -> $1ledger.json
  - (.*)csir.md -> $1ledger.json
  - (.*)project.md -> $1ledger.json
folder: ../../..
related:
  - ./ledger_schema.ts
---

Fill the **slots** in `obligations.json` with candidate assumptions and
specifications, producing the **Assumption Ledger** (idea.md §6.2, §6.3, §7).

Use the inference portfolio in order of cost and reliability, and record
which techniques corroborate each entry in `provenance.corroborated_by`:
type/effect facts from the CSIR; interval / range reasoning over
straight-line code and simple loops; weakest-precondition residuals for the
exact condition an obligation needs; call-site aggregation (how do callers
actually invoke the unit? which call sites would violate the proposal?);
existing tests, asserts, docstrings and naming for conceptual invariants and
functional specs. Model-generated content is fine — it is a *candidate* and
will be checked — but its `provenance.source` must say `agent:...` and
`human_ratified` must be `false`.

Classify every entry: `kind`, `bucket` (A language-defined / B
programmer-implied / C idealization), `scope`, `discharge.strategy`
(`provable` | `checkable` | `enforced-elsewhere` | `axiomatic` |
`idealization`), a `confidence` in [0,1], `status: "candidate"`, and the
obligation ids it serves in `used_by`. Every entry gets a `source_anchor`
with a proposed comment of the form
`@assume(<caller|env|repr>) <statement>  [<id>]`, `@ensures ...`,
`@invariant ...` or `@decreases ...` so it can be round-tripped into source.
Prefer `provable` / `checkable` / `enforced-elsewhere` with a concrete
`justification` (file and symbol); use `axiomatic` only when nothing enforces
the fact, and say what the `residual_risk` is. Flag entries that would be
vacuous (imply false) or trivialising (make a postcondition unconditional)
in `warnings`.

Set `langsem_version` to the version stated at the top of `ops/LangSem.lean`.
Output a single JSON document that strictly conforms to the `AssumptionLedger`
schema in `ledger_schema.ts` — no commentary or markdown fences.