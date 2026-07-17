# 📦 npm Project Builder

AI-powered pipeline for building client-side web applications from an idea to a working, tested project.

## Overview

npm Project Builder is a hybrid AI/automation pipeline that takes a natural language description of a web application
and produces a fully scaffolded, implemented, built, and tested npm project. Technology choices (framework, bundler,
test runner, language, etc.) are **not hardcoded** — they are decided during the Design phase and all subsequent stages
follow those decisions.

## Pipeline Stages

| # | Stage              | Operation              | Output                | Description                                                                                                                       |
|---|--------------------|------------------------|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| 1 | 📐 **Design**      | `ops/design_op.md`     | `design.md`           | Analyzes the idea and produces a comprehensive design document capturing technology choices, architecture, and project structure. |
| 2 | 🏗️ **Scaffold**   | `ops/scaffold_op.md`   | `code/`               | Generates the project skeleton: `package.json`, config files, directory structure, and minimal entry points.                      |
| 3 | 📥 **Install**     | `ops/install_op.md`    | `code/install_log.md` | Runs `npm install` to fetch and resolve all dependencies. Auto-fixes version conflicts and peer dependency issues.                |
| 4 | 💻 **Implement**   | `ops/implement_op.md`  | `code/src/`           | Writes the full application source code — components, styles, assets, and entry points — according to the design.                 |
| 5 | 🧪 **Write Tests** | `ops/test_write_op.md` | `code/src/`           | Generates comprehensive test files — unit tests, component tests, and integration tests.                                          |
| 6 | 🔨 **Build**       | `ops/build_op.md`      | `code/build_log.md`   | Runs `npm run build` and auto-fixes any errors (TypeScript issues, import errors, bundler config problems).                       |
| 7 | ✅ **Test Run**     | `ops/test_run_op.md`   | `code/test_log.md`    | Runs `npm test` and auto-fixes any failures (assertion errors, missing mocks, configuration issues).                              |
| 8 | 🔧 **Update**      | `ops/update_op.md`     | `code/`               | Applies iterative changes from user feedback/notes, then re-builds and re-tests.                                                  |

## Execution Modes

### ▶ Full Pipeline

Runs all stages sequentially from Design through Test Run. Best for initial project creation.

### 🔄 Build & Test Only

Runs just the Build and Test Run stages. Useful after manual code edits or when re-validating an existing project.

### 🔧 Apply Updates

Reads update notes, applies changes to the codebase, then re-builds and re-tests. Used for iterative development after
the initial build.

## Key Files

| File                  | Purpose                                                                               |
|-----------------------|---------------------------------------------------------------------------------------|
| `README.md`             | User-provided description of the desired web application                              |
| `notes.md`            | User feedback, bug reports, feature requests, and design changes for the Update stage |
| `design.md`           | AI-generated design document with all architecture and technology decisions           |
| `code/`               | The generated npm project directory                                                   |
| `code/package.json`   | Project manifest with dependencies and scripts                                        |
| `code/src/`           | Application source code and test files                                                |
| `code/build_log.md`   | Output log from the build process                                                     |
| `code/test_log.md`    | Output log from the test runner                                                       |
| `code/install_log.md` | Output log from npm install                                                           |

## Key Principle

Technology-specific decisions (TypeScript vs JavaScript, React vs Vue vs Vanilla, Webpack vs Vite vs esbuild, Jest vs
Vitest vs Mocha, etc.) are **not hardcoded** in the pipeline. They are captured in `design.md` during the Design phase,
and all subsequent stages read from that document. This makes the pipeline flexible enough to produce any kind of
client-side web application.

## Getting Started

1. Open the app in your browser
2. Go to the **💡 Idea & Notes** tab
3. Describe the web application you want to build in the idea editor
4. Switch to the **⚙️ Pipeline** tab
5. Click **▶ Run Full Pipeline** to build the entire project, or run stages individually
6. View results in the **📊 Results** tab, including the design document, build/test logs, and a file browser

## Iterative Development

After the initial build:

1. Review the generated code and test results
2. Add feedback, bug reports, or feature requests in the **📝 Update Notes** editor
3. Click **🔧 Apply Updates** to modify the code and re-validate with build + test
4. Repeat as needed

