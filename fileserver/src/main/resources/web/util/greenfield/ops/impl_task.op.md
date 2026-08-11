---
  transforms:
     - ../tasks/(.*).json -> ../tmp/$1.impl.md
  task_type: SubPlan
  task_config_json: impl.task.json
  folder: ../../..
  related:
    - task_schema.ts
    - ../plan/architecture.json
    - ../plan/stack.json
  ---

  Execute every task specified in the `BuildPlan` document, in order,
  respecting `depends_on`.

  For each task: create or modify `target_files[0]` (using `target_files[1..]`
  and `related_files` as context) so that the task `description` is fully
  satisfied. Modify, do not clobber: if a target file already exists, patch it
  in place and preserve unrelated content.

  Note: the UI (`../index.html`) normally runs tasks **individually**, by
  synthesizing a temporary doc-op per task under `../tmp/`. This op is the
  whole-plan fallback for running a phase unattended.