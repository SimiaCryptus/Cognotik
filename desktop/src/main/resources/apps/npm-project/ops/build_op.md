---
task_type: AutoFix
folder: ../code/
specifies: ../code/build_log.md
related:
  - ../code/package.json
  - ../code/src/**
  - ../code/design.md
   - ../code/compile.sh
---

Run the build via the project's central build entrypoint: `bash compile.sh` in the project directory.

* The `compile.sh` script is the canonical way to invoke the build — do NOT call `npm run build` directly
* The script handles directory resolution, pre-flight checks, and consistent error reporting

* If the build fails, diagnose the error and fix the source code or configuration:
  - TypeScript type errors → fix the type annotations or add type declarations
  - Import/export errors → fix module references and paths
  - Missing dependencies → add to package.json and re-run npm install, then retry build
  - Bundler configuration errors → fix the bundler config file
  - Syntax errors → fix the source code
* Do NOT modify `compile.sh` to work around build errors — fix the actual source or config
* Iterate until the build succeeds
* Log the results to `build_log.md`
* After successful build, verify that output files exist in the expected output directory (e.g., `dist/`)