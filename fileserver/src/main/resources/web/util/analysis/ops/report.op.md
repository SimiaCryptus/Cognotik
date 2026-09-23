---
transforms:
  - (.*)triage.json -> $1report.md
  - (.*)ledger.json -> $1report.md
  - (.*)proofs.md -> $1report.md
folder: ../../..
---

Write the **Evidence Bundle** report (idea.md §12) for this analysis. The
output is evidence, not a verdict; verification status is always rendered as
the pair *(property, assumption set)* and never as a bare checkmark.

Order of sections is mandatory — the honest list comes first:

1. **Residual risk summary** — unratified axiomatic assumptions (list them),
   unproved residuals per rung, real bugs found (with reproducing inputs),
   unanalyzed / quarantined units, timed-out obligations, spec revisions
   awaiting ratification, SMT-attested results not reconstructed in the
   kernel.
2. **Ladder report** — per property: the highest rung proved unconditionally
   and, for each higher rung, the residual conditions and their disposition.
3. **Assumption manifest** — every ledger entry relied upon, partitioned into
   *proved*, *runtime-checked*, *externally enforced*, *axiomatic*, with
   provenance and ratification status.
4. **Obligation coverage** — counts by kind and by terminal status
   (kernel-checked / smt-attested / bounded-checked / refuted / proposed /
   timeout / open).
5. **Defects & repairs** — triage items with diagnosis, replay result and
   repair plan.
6. **Reproducibility** — inputs a third party needs (LangSem version, rung,
   files) to re-check every kernel-checked claim without this engine.

Use markdown with headers and tables.