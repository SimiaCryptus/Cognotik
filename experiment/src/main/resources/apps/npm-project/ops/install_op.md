---
task_type: AutoFix
folder: ../code/
specifies: ../code/install_log.md
related:
  - ../code/package.json
  - ../code/design.md
   - ../code/install.sh
---

Run dependency installation via the project's central install entrypoint: `bash install.sh` in the project directory.

* The `install.sh` script is the canonical way to install dependencies — do NOT call `npm install` directly
* The script handles directory resolution, pre-flight checks, and consistent error reporting

* If `npm install` fails, diagnose the error and fix `package.json` or config files accordingly
* Common issues to handle:
  - Version conflicts between dependencies
  - Invalid package names
  - Peer dependency warnings (resolve if they cause errors)
* Do NOT modify `install.sh` to work around install errors — fix the actual package.json or config
* After successful install, `node_modules/` and `package-lock.json` should exist
* Log the results to `install_log.md`