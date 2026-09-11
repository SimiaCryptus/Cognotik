---
transforms:
  - (.*)ledger.json -> $1Obligations.lean
  - (.*)obligations.json -> $1Obligations.lean
  - (.*)csir.md -> $1Obligations.lean
folder: ../../..
related:
  - ./LangSem.lean
---

Emit a Lean 4 file that states every obligation as a theorem against the
shared prelude `ops/LangSem.lean` (idea.md §8). The prelude is human-authored
and must **not** be modified or re-declared; only extend `Op`/`Step` locally
(in a `namespace Emitted`) when the CSIR uses a node the prelude lacks, with a
`-- TODO(LangSem): promote` comment.

Structure of the output:

1. A header comment: source files, `langsem_version` from the ledger, the
   rung requested in the analysis, and the content hash inputs (obligation
   id, ledger ids, LangSem version, rung) so proof caching can key on it.
2. `import LangSem` and `open LangSem`.
3. For each CSIR unit, a **deep embedding** as a `Program` (name + list of
   `Op`s over its arithmetic / index nodes), so the same term can be
   interpreted at every rung.
4. For each ledger entry with `status ≠ rejected`, a named hypothesis
   (`asm_<short_id>`) rendering `statement.text` as a Lean proposition; keep
   the ledger id in a doc-comment so provenance is recoverable from the
   proof term.
5. For each obligation, `theorem obl_<short_id>` whose explicit hypotheses
   are exactly the ledger entries listed in `used_by`, stated at the lowest
   rung where it exists and again at the target rung when different. Where
   a lift is needed, add the residual `SideConditions` and an instance of
   `refine_step_schema`.
6. Proofs: use `by decide` / `by omega` / `by simp` where obviously
   sufficient; otherwise `by sorry` with a comment naming the intended
   backend. `sorry` is permitted here — Layer 5 replaces it.

Output only the Lean source (no markdown fences, no prose outside comments).