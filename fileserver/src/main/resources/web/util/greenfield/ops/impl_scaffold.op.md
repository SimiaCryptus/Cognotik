---
transforms: ../plan/stack.json -> ../tmp/scaffold.md
task_type: SubPlan
task_config_json: impl.task.json
folder: ../../..
related:
  - ../plan/feature.json
  - ../plan/architecture.json
  - ../plan/phases.json
---

Create the project skeleton described by `plan/stack.json` and
`plan/architecture.json`, under `StackPlan.target_root` (relative to the folder above).

This stage runs first and alone. Nothing else can be verified until it exists. Produce, using the idioms of the chosen
build tool:

1. The manifest / build script with the declared dependencies pinned.
2. Lint & format configuration matching `StackPlan.lint_format`.
3. A `.gitignore` appropriate to the language and build tool.
4. A CI workflow matching `StackPlan.ci` that installs, lints, builds and tests.
5. The directory tree from `ArchitecturePlan.directory_layout`, each directory made real by a placeholder or a genuine
   entry point.
6. A `dev` entry point (script/target) that runs the application, plus a
   `test` target that runs the test framework.

Write **no feature logic** — that is stage 7. Placeholders must compile and the test target must pass with zero or one
trivial test.