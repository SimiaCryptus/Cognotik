---
transforms: (.*)project.md -> $1csir.md
task_type: SubPlan
task_config_json: normalize.task.json
folder: ../../..
related:
  - ./csir_reference.md
---

Normalise every unit listed in `project.md` into **CSIR** using the notation
in `csir_reference.md`. CSIR is a verification IR: if a proof obligation could
depend on something, it must be a syntactic node.

Make explicit, for each unit:

- control flow as labelled blocks with explicit edges, including unwind /
  trap / exception edges;
- data flow as SSA with φ-nodes; memory as explicit `mem.load` / `mem.store`
  on named regions;
- fully elaborated types; every coercion as a `cast` node with its
  truncate / trap behaviour;
- the overflow policy of **every** arithmetic node as an operand, taken from
  the build profile recorded in `project.md`;
- an effect row on every operation and on the unit as a whole;
- `decreases:` and `invariant:` slots on every loop and recursive unit
  (write `?` when unknown — the slot must exist so Layer 2 can see it);
- concurrency as explicit `fork` / `join` / `sync.*` / `atomic.*` nodes.

Record which guarantees the language front end already provides (so Layer 2
can discharge those obligations without an assumption) and which constructs
were refused. Never silently approximate an unmodeled construct.