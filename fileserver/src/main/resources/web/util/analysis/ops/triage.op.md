---
transforms:
  - (.*)proofs.md -> $1triage.json
  - (.*)ledger.json -> $1triage.json
folder: ../../..
related:
  - ./triage_schema.ts
---

Triage every obligation row in `proofs.md` (idea.md §11). Discharged rows
become `diagnosis: "discharged"` with their exact `discharge_level`. Every
other row is classified into exactly one of:

- `real-bug` — the counterexample reproduces against the actual program
  (`counterexample.replay = "reproduced"`); repair = `file-defect` with the
  reproducing input, never a spec change;
- `missing-assumption` — unreachable in practice, corroborated by call-site
  analysis; repair = `add-assumption` (propose the ledger entry in
  `ledger_changes` and the `@assume` comment to write);
- `wrong-specification` — the behaviour is intended; repair = `revise-spec`
  with `requires_ratification: true` and a note whether the change *weakens*
  the property (monotonicity check);
- `missing-lemma` — goal true, automation stalled; repair = `add-lemma`;
- `front-end-defect` — CSIR interpretation disagrees with real execution on
  the counterexample; repair = `file-frontend-bug`, quarantine the unit;
- `open` — no classification possible within budget.

If a counterexample could not be replayed, say so (`replay: "not-replayed"`)
and do not classify it as `real-bug`. Compute `highest_unconditional_rung`
from the residual table. Every `repair` must name `target_files` relative to
the analysis root and a `description` precise enough to execute unattended
as a `FileModification` task.

Output a single JSON document strictly conforming to the `TriageReport`
schema in `triage_schema.ts` — no commentary or markdown fences.