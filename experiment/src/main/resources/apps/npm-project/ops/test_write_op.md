---
task_type: SubPlan
task_config_json: implement_project.json
folder: ../code/
related:
  - ../code/design.md
  - ../code/package.json
  - ../code/src/**
---

* Read the design document in `design.md` to determine the chosen test runner and testing conventions
* Review the existing source code in `code/src/` to understand what needs testing
* Write comprehensive test files:

## Test Coverage
  - Unit tests for utility functions and pure logic modules
  - Component tests for UI components (if using a component framework)
  - Integration tests for key user flows where practical
  - Edge cases and error conditions

## Test Organization
  - Place test files according to the convention in the design (e.g., `__tests__/`, `*.test.js`, `*.spec.ts`)
  - Use the test runner and assertion library specified in the design
  - Include proper setup/teardown where needed
  - Mock external dependencies and APIs appropriately

## Quality Standards
  - Tests should be meaningful, not just boilerplate
  - Each test should have a clear description of what it verifies
  - Tests should be independent and not rely on execution order
  - Do NOT modify source code — only create/modify test files