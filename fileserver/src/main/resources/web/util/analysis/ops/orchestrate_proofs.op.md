---
transforms: (.*)Obligations.lean -> $1proofs.md
task_type: SubPlan
task_config_json: proofs.task.json
folder: ../../..
related:
  - ./LangSem.lean
---

Run the **proof portfolio** (idea.md §10) over every theorem in
`Obligations.lean`, cheapest first: syntactic discharge (`decide`, `simp`,
`omega`, `bv_decide`) → SMT-shaped reasoning → cheap refutation by bounded
enumeration / property testing → library lemma search → proposed tactic
scripts → human escalation. Replace `sorry` placeholders in
`Obligations.lean` with the proposed proofs where you can.

Record one row per theorem with a trust level that is **never inflated**:
`kernel-checked` only if a Lean toolchain was actually executed and accepted
the file without `sorry`; otherwise `proposed`. `smt-attested`,
`bounded-checked`, `refuted` and `timeout` are distinct statuses and stay
distinct in the report. For every refutation give the model-level
counterexample **and** the source-level input to replay it with — replay is
Layer 6's non-negotiable step. For every rung lift list the residual side
conditions and their disposition. Propose reusable lemmas for the library.