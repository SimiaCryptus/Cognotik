---
transforms: ../plan/architecture.json -> ../tmp/tests.md
task_type: SubPlan
task_config_json: impl.task.json
folder: ../../..
related:
  - ../plan/feature.json
  - ../plan/stack.json
  - ../plan/phases.json
---

Write the test suite for the code produced by stages 6–7.

Using `StackPlan.test_framework` and the idiomatic test layout for the language:

- one test file per component in `ArchitecturePlan.components`, covering its
  `public_interface` and the failure modes named in its `responsibility`;
- at least one test per `acceptance_criteria` in the user stories of
  `plan/feature.json`, named so the criterion is recognisable in the output;
- at least one end-to-end test exercising the walking-skeleton path from phase `p0-skeleton`.

Read the source that actually exists before writing assertions — tests must reference real symbols, real signatures and
real file paths. Do not modify production source to make a test pass; record the mismatch in the run summary instead.

This stage is deliberately separate from stage 7 so it can be re-run wholesale after a plan change.