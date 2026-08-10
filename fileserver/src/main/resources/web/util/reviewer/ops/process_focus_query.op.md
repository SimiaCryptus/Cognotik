---
transforms: (.*)focus.md -> $1analysis.json
task_type: SubPlan
task_config_json: analysis.task.json
folder: ../../..
related:
  - ./analysis_schema.ts
---

Review the codebase against the focus query below. Use `FileSearch` to
locate every file relevant to the focus query, then use `FileReview` on
each candidate file to produce a structured, schema'd analysis of that
file with respect to the focus query.

For each reviewed file, capture:

- a short `summary` of how the file relates to the focus query
- a list of `findings`, each with a `category`, `severity`, `message`, and
  (where applicable) `location`, `suggested_fix`, and `confidence`

Output a single JSON document that strictly conforms to the
`FileAnalysisPlan` schema defined in `analysis_schema.ts` (see related
file). Do not include any commentary, markdown formatting, or explanation
outside of the JSON structure itself — the output file must be valid,
parseable JSON.