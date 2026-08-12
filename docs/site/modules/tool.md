# Tool

*Bring AI-powered code review, auto-fixing, and issue resolution directly into your CI/CD pipeline.*

## Overview

The **Tool** module is Cognotik's build and automation integration layer. It packages Cognotik's AI agents — Code
Reviewer, Code Fixer, Code Implementer, and Documentation Processor — as ready-to-use Gradle tasks and GitHub Actions
workflows. Instead of running AI coding assistants manually, teams wire them directly into pull requests, build
pipelines, and issue trackers, so builds fix themselves and issues get resolved with generated PRs — no extra tooling
required beyond Gradle and a supported LLM API key.

## Key Features

- 🔧 **Auto-Fixing Build Validator** — runs your test suite, feeds failure logs to an AI agent, and commits fixes
  automatically when the build goes red.
- 🎫 **Agentic Issue Handler** — watches for issues labeled `agent-help`, analyzes the description, and opens a pull
  request with a proposed fix or implementation.
- 📝 **Automated Code Review** — reviews source files against best practices and coding standards, file by file.
- 🏗️ **Natural-Language Code Generation** — describe a feature in plain English and get working implementation code.
- 📚 **Bidirectional Docs Sync** — markdown frontmatter (`specifies`, `documents`, `transforms`) keeps code and docs
  in sync automatically, with configurable overwrite/patch strategies.
- 🤖 **Multi-Model Support** — works with OpenAI, Google Gemini, Anthropic Claude, and Groq; swap providers via API
  key, no code changes.

## Example

Fix a failing build automatically inside CI:

```yaml
name: Auto-Fixing Build Validator

on:
  pull_request:
    branches: [ main ]

jobs:
  build-and-fix:
    runs-on: ubuntu-latest
    steps:
      - name: Run Tests
        id: run_tests
        run: ./gradlew test 2>&1 | tee build.log
        continue-on-error: true

      - name: Agentic Auto-Fix
        if: steps.run_tests.outcome == 'failure'
        run: |
          ./gradlew codeFixer \
            -PfixPrompt="Fix the build errors" \
            -PfixLog="build.log"
```

Or run any of the agents locally:

```bash
export GOOGLE_API_KEY="your-google-api-key"

./gradlew codeReview \
  -PreviewPrompt="Review and improve code quality in file (%s)" \
  -PreviewSrc="src/main/java" \
  -PreviewThreads=4

./gradlew codeImplementer \
  -PimplPrompt="Create a REST API endpoint for user management" \
  -PimplHeadless=true

./gradlew docProcessor \
  -PoverwriteMode="PatchToUpdate" \
  -ProotDir="." \
  -Pthreads=4
```

Sync docs and code with frontmatter:

```markdown
---
specifies:
  - "../src/main/kotlin/**/*.kt"
---
# API Design Guidelines
All service classes should follow these patterns...
```

## Integration

- Requires at least one LLM provider key (`GOOGLE_API_KEY` recommended; `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, or
  `GROQ_API_KEY` also supported) exposed as a GitHub Actions secret or environment variable.
- Ships as plain Gradle tasks (`codeReview`, `codeFixer`, `codeImplementer`, `docProcessor`), so it drops into any
  JVM project's existing build without new toolchains.
- Designed to run standalone or alongside other Cognotik modules — it consumes the same agent framework used
  elsewhere in the platform, just triggered from CI events instead of an IDE or CLI session.