---
transforms: (.*)research.md -> $1followup.json
folder: ../../..
related:
  - ./followup_schema.ts
---

Analyze the following research document and identify concrete, actionable
follow-up tasks that should be implemented as docops operations. Follow-up
tasks should primarily be expressed as a series of `FileModification` tasks
(one per concrete file-level change), unless a task clearly requires a
different docops task type.

For each follow-up item, provide:

- a short, descriptive `title`
- a detailed `description` that is precise enough to be used directly as a
  task description for automated execution
- the `target_files` that should be created or modified
- any `related_files` needed for context
- an optional `priority` (`low`, `medium`, `high`, `critical`)
- an optional `task_type` (default to `FileModification` when omitted)

Output a single JSON document that strictly conforms to the `FollowupPlan`
schema defined in `followup_schema.ts` (see related file). Do not include
any commentary, markdown formatting, or explanation outside of the JSON
structure itself — the output file must be valid, parseable JSON.