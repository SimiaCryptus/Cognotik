---
transforms: (.*)target.md -> $1project.md
task_type: SubPlan
task_config_json: ingest.task.json
folder: ../../..
---

Build the **Layer 0 project model** for the analysis target described in
`target.md`. The front-matter of `target.md` gives the language (or `auto`)
and the ladder rung the user wants to reach (0 ideal … 5 deployed).

Explore the codebase rooted at the configured folder and establish, with file
and line references:

1. the language, toolchain version and **build profile** actually used
   (this decides the integer overflow policy and pointer width — it is part of
   the specification, not a footnote);
2. the units (functions / methods / modules) in scope, their entry points and
   a criticality estimate (reachable from entry points? security-relevant?);
3. the dependency graph between in-scope units and every external call whose
   effect summary will be needed for framing;
4. existing evidence usable to seed specifications: tests, docstrings,
   asserts, contracts, comments that already state assumptions;
5. constructs or units that must be reported as **unanalyzed** rather than
   approximated (reflection, inline assembly, dynamic dispatch without a known
   receiver set, unsupported concurrency primitives, …).

Do not propose assumptions or specifications yet; that is Layer 2's job.