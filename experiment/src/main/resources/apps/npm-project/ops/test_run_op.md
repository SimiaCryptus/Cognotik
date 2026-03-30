---
task_type: AutoFix
folder: ../code/
specifies: ../code/test_log.md
related:
  - ../code/package.json
  - ../code/src/**
  - ../code/design.md
   - ../code/test.sh
---

Run tests via the project's central test entrypoint: `bash test.sh` in the project directory.

* The `test.sh` script is the canonical way to run tests — do NOT call `npm test` directly
* The script handles directory resolution, pre-flight checks, and consistent error reporting

* If tests fail, diagnose the failures and fix either the test code or the source code:
  - Assertion failures → determine if the test expectation or the source code is wrong, and fix the appropriate one
  - Import errors in tests → fix test file imports
  - Test runner configuration errors → fix the test runner config
  - Timeout errors → optimize the test or increase timeout
  - Missing test dependencies or mocks → add them
* Do NOT modify `test.sh` to work around test failures — fix the actual test or source code
* Iterate until all tests pass
* Log the results to `test_log.md` including:
  - Number of tests run
  - Number passed/failed/skipped
  - Summary of any fixes applied