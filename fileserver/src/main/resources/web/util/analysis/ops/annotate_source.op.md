---
transforms: (.*)ledger.json -> $1annotations.md
task_type: SubPlan
task_config_json: annotate.task.json
folder: ../../..
---

Round-trip the **Assumption Ledger** into the source tree (idea.md §7.2). For
every entry in `ledger.json` with `status` `candidate` or `active` and a
`source_anchor`, write the anchor's `comment_form` as a doc comment
immediately above the anchored declaration, using the host language's
comment syntax (`///` for Rust, `/** */` for C/C++/Java, `#` for Python,
`//` for Go). The ledger id in square brackets is mandatory so the comment
and the ledger entry stay linked. Update an existing comment carrying the
same id instead of duplicating it. Do not touch code, only comments.

Skip entries whose anchor no longer matches the code and list them as
`stale`. Record what was written, updated and skipped.