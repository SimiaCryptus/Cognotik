---
transforms:
  - (.*).followup.json -> $1.followup.md
  - (.*).summary.md -> $1.followup.md
task_type: SubPlan
task_config_json: followup.task.json
folder: ..
---

Execute the tasks specifies in the `followup.json`