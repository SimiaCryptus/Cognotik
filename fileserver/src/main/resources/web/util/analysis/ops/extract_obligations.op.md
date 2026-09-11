---
transforms: (.*)csir.md -> $1obligations.json
folder: ../../..
related:
  - ./obligation_schema.ts
  - ./csir_reference.md
---

Apply the **recognizer catalogue** from `csir_reference.md` (idea.md §6.1) to
every CSIR unit in the input and raise one obligation per matched pattern.
Work mechanically: back edge → termination; `arith.* [overflow=trap]` →
no-overflow; `slice.index [bounds=trap]` → in-bounds; `arith.div` → no-trap;
`unwrap`/non-null cast → no-panic; distinct-pointer load-after-store →
aliasing; shared mutable region + `fork` → data-race-freedom; nested
`sync.acquire` → deadlock-freedom; `fp.cmp` → spec-fidelity; `call` with
unknown effects → framing; unbounded input → resource-bound; a named unit
with docs or tests → functional-spec.

For each obligation give: a stable `id`
(`obl:<qualified::unit>#<short-name>`), `kind`, the `pattern` that raised it,
`location`, the goal as a CSIR predicate in `statement`, the lowest ladder
`rung` at which it exists (e.g. overflow obligations do not exist at rung 0),
a `criticality`, and the `slot` naming the kind of assumption or specification
that would discharge it. Obligations already discharged by front-end
guarantees recorded in `csir.md` still appear, with `slot.kind = "none"` and
a `discharged_by` note, so coverage is visible. Copy the `unanalyzed` list
from the CSIR document.

Do **not** invent assumptions here — only slots. Output a single JSON document
that strictly conforms to the `ObligationSet` schema in `obligation_schema.ts`.
No commentary, no markdown fences: the file must be valid, parseable JSON.