# Cognotik

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Cognotik Demo** is a demonstration project showcasing how to integrate [Cognotik](https://github.com/SimiaCryptus/Cognotik) as a build and automation tool using GitHub Actions. This repository provides working examples of AI-powered code review, automated bug fixing, and intelligent issue resolution integrated directly into your CI/CD pipeline.

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Usage](#usage)
- [What is Cognotik?](#what-is-cognotik)
- [GitHub Actions Workflows](#github-actions-workflows)
  - [Auto-Fixing Build Validator](#auto-fixing-build-validator)
  - [Agentic Issue Handler](#agentic-issue-handler)
- [Configuration](#configuration)
  - [Code Reviewer](#code-reviewer)
  - [Code Fixer](#code-fixer)
  - [Code Implementer](#code-implementer)
- [API Providers](#api-providers)
- [Contributing](#contributing)
- [License](#license)

## Features

- 🔄 **Ready-to-Use GitHub Actions**: Pre-configured workflows for immediate integration
- 🔧 **Automated Build Fixing**: Automatically fix failing builds in pull requests
- 🎫 **Intelligent Issue Resolution**: AI agents that create PRs to resolve GitHub issues
- 🤖 **Multi-Model Support**: Works with OpenAI, Google Gemini, Anthropic Claude, and Groq
- 📝 **Code Review Automation**: Review code against best practices and standards
- 🏗️ **Code Generation**: Generate implementations from natural language descriptions

## Quick Start
### 1. Fork or Clone This Repository

```bash
git clone https://github.com/SimiaCryptus/CognotikDemo.git
cd CognotikDemo
```

### 2. Configure Repository Secrets

Add the following secrets to your GitHub repository (Settings → Secrets and variables → Actions):

- `OPENAI_API_KEY` - Your OpenAI API key
- `GOOGLE_API_KEY` - Your Google Gemini API key (recommended)

### 3. Enable GitHub Actions

The workflows in `.github/workflows/` will automatically:
- Fix failing builds in pull requests
- Create PRs to resolve issues labeled with `agent-help`

### 4. Local Development (Optional)

```bash
export GOOGLE_API_KEY="your-google-api-key"

./gradlew codeReview -PreviewPrompt="Review and improve code quality in file (%s)"
```


## What is Cognotik?

[Cognotik](https://github.com/SimiaCryptus/Cognotik) is a powerful AI-powered code automation framework that enables intelligent code review, generation, and modification using large language models (LLMs). This demo project shows how to leverage Cognotik's capabilities within GitHub Actions workflows.

### Key Cognotik Components Used

- **Code Reviewer**: Analyzes source files against standards and suggests improvements
- **Code Fixer**: Parses build logs and automatically fixes compilation/test errors
- **Code Implementer**: Generates new code from natural language descriptions



## GitHub Actions Workflows

This demo includes two pre-configured GitHub Actions workflows that showcase Cognotik's automation capabilities.

### Auto-Fixing Build Validator

**File:** `.github/workflows/autofix_build.yml`

Automatically detects and fixes failing builds in pull requests.

#### How It Works

1. Triggered on pull requests to `main`
2. Runs the test suite and captures output to `build.log`
3. If tests fail, invokes Cognotik's Code Fixer agent
4. Agent analyzes the build log and fixes implementation files
5. Verifies the fix by re-running tests
6. Commits the fixes automatically

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

### Agentic Issue Handler

**File:** `.github/workflows/issue_handler.yml`

Automatically creates pull requests to resolve GitHub issues labeled with `agent-help`.

#### How It Works

1. Triggered when an issue is opened or labeled with `agent-help`
2. Analyzes the issue title and description
3. Invokes Cognotik's Code Implementer agent
4. Agent reviews relevant source files and generates fixes
5. Creates a pull request with the proposed changes
6. Comments on the issue if no changes were needed

#### Usage

1. Create a new issue describing the bug or feature
2. Add the `agent-help` label
3. Wait for the agent to analyze and create a PR

## Configuration

### Required Repository Secrets

| Secret            | Description              | Required |
|-------------------|--------------------------|----------|
| `GOOGLE_API_KEY`  | Google Gemini API key    | Yes*     |
| `OPENAI_API_KEY`  | OpenAI API key           | Yes*     |

      
*At least one API key is required. Google Gemini is recommended for best results.

### Optional Secrets

| Secret              | Description              |
|---------------------|--------------------------|
| `ANTHROPIC_API_KEY` | Anthropic Claude API key |
| `GROQ_API_KEY`      | Groq API key             |

## Usage

### Gradle Tasks

This demo provides three main Gradle tasks:

#### Code Reviewer

```bash
./gradlew codeReview \
  -PreviewPrompt="Update implementation file (%s) according to best practices" \
  -PreviewSrc="src/main/java" \
  -PreviewThreads=4
```

#### Code Fixer

```bash
./gradlew codeFixer \
  -PfixPrompt="Fix the build errors reported in build.log" \
  -PfixLog="build.log"
```

#### Code Implementer

```bash
./gradlew codeImplementer \
  -PimplPrompt="Create a REST API endpoint for user management" \
  -PimplHeadless=true
```

## API Providers

Cognotik supports multiple AI providers. Configure your preferred provider:

| Provider  | Model Example | Environment Variable |
|-----------|---------------|----------------------|
| Google    | Gemini 3.0    | `GOOGLE_API_KEY`     |
| OpenAI    | GPT-4o        | `OPENAI_API_KEY`     |
| Anthropic | Claude 4.5    | `ANTHROPIC_API_KEY`  |
| Groq      | Llama 3 70B   | `GROQ_API_KEY`       |

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.


## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## Support

- 📖 [Cognotik Documentation](https://github.com/SimiaCryptus/Cognotik/wiki)
- 🐛 [Issue Tracker](https://github.com/SimiaCryptus/CognotikDemo/issues)
- 💬 [Discussions](https://github.com/SimiaCryptus/CognotikDemo/discussions)

---

Made with ❤️ by [SimiaCryptus](https://github.com/SimiaCryptus) | Powered by [Cognotik](https://github.com/SimiaCryptus/Cognotik)