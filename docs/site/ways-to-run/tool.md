# Running Cognotik as a Build Tool

Cognotik can be wired directly into your build and CI/CD pipeline, giving you AI-powered code review, automated
build fixing, and issue resolution as part of your normal Gradle workflow and GitHub Actions.

## Getting Started

1. Fork or clone the integration demo repository:
   ```bash
   git clone https://github.com/SimiaCryptus/CognotikDemo.git
   cd CognotikDemo
   ```
2. Add your API key(s) as repository secrets (Settings → Secrets and variables → Actions) — at minimum
   `GOOGLE_API_KEY` or `OPENAI_API_KEY` (Google Gemini is recommended).
3. Enable GitHub Actions. The included workflows will automatically fix failing builds on pull requests and
   create PRs to resolve issues labeled `agent-help`.
4. For local use, export your API key and run the provided Gradle tasks directly, for example:
   ```bash
   export GOOGLE_API_KEY="your-google-api-key"
   ./gradlew codeReview -PreviewPrompt="Review and improve code quality in file (%s)"
   ```

## Packaging & Launch

Cognotik isn't installed as a standalone app in this mode — it's invoked as Gradle tasks (`codeReview`,
`codeFixer`, `codeImplementer`, `docProcessor`) from your existing build. Two ready-to-use GitHub Actions
workflows are provided:

- **Auto-Fixing Build Validator** — runs on pull requests, executes your test suite, and if it fails, invokes
  Cognotik to analyze the build log and automatically commit a fix.
- **Agentic Issue Handler** — triggers when an issue is labeled `agent-help`, then generates and opens a pull
  request with a proposed fix, or comments on the issue if none is needed.

There's no separate installer or download step: as long as your project has Gradle and the workflow files, running
Cognotik this way is just a matter of triggering a build or invoking a task.

## See Also

Cognotik can also be run as a desktop app, from the command line, or as an IntelliJ plugin — see the other
"Ways to Run" pages for details.