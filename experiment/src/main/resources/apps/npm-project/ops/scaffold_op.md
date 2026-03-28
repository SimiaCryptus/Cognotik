---
task_type: SubPlan
task_config_json: scaffold_project.json
folder: ../code/
related:
  - ../code/design.md
  - ../code/idea.md
---

* Read the design document in `design.md` for all technology and architecture decisions
* Generate the project skeleton in the `code/` folder:

## Required Files
  - `package.json` with all dependencies, devDependencies, and scripts as specified in the design
  - Configuration files for the chosen bundler (e.g., `vite.config.js`, `webpack.config.js`, `tsconfig.json` if TypeScript)
  - Configuration files for the chosen test runner (e.g., `vitest.config.js`, `jest.config.js`)
  - Linter/formatter configs if specified in design (e.g., `.eslintrc.json`, `.prettierrc`)
  - `.gitignore` appropriate for the chosen stack
  - `README.md` documenting the project, setup instructions, and available scripts

## Directory Structure
  - Create the directory structure as specified in the design document
  - Create placeholder/minimal entry point files (e.g., `src/index.js` or `src/main.ts`, `index.html`)
  - Create a minimal working example that builds and runs successfully
## Shell Script Entrypoints
   - The project includes pre-existing shell scripts (`compile.sh`, `test.sh`, `install.sh`) that serve as canonical entrypoints for build, test, and install commands
   - Do NOT overwrite or remove these scripts — they are managed by the pipeline infrastructure
   - Ensure `package.json` scripts (build, test) are compatible with what these shell scripts invoke


* All file extensions, syntax, and imports must match the language choice in the design
* Do NOT install dependencies — that is handled by a separate stage
* Do NOT write full application logic — only the minimal scaffold