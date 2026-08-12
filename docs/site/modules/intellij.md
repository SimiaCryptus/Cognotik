# IntelliJ Plugin

*Bring Cognotik's agentic AI coding assistant directly into your IntelliJ-based IDE.*

## Overview

The Cognotik IntelliJ plugin is an open-source, "Bring Your Own Key" (BYOK) AI coding assistant that goes beyond
autocomplete. It puts Cognotik's Cognitive Task Planning Framework directly into JetBrains IDEs (IntelliJ IDEA,
GoLand, PyCharm, and more), letting you delegate complex, multi-step development tasks — file edits, code execution,
shell commands, web search, documentation generation — to an AI agent while keeping full control over your data,
model choice, and costs.

Because it's BYOK, your code and prompts go directly to the AI provider you choose (OpenAI, Anthropic, AWS Bedrock,
local/fine-tuned models, etc.) — no intermediary servers, no forced subscription.

## Key Features

* **Cognitive Task Planning Framework** — choose a plan-ahead **Waterfall Mode** for well-defined problems, an
  interactive **Conversational Mode** for exploratory work, or a flexible **Adaptive Mode** that adjusts its plan
  based on real-time feedback.
* **Intelligent Code Editing** — chat about code, apply intelligent patches, edit selections with natural language,
  and paste-and-transform code inline in the editor.
* **Project-Wide Operations** — generate documentation, scaffold new files, apply patches across multiple files, and
  perform contextual mass refactoring.
* **Deep IDE Integration** — works from the Git Log (chat with commits), Find Usages results, the Test Runner
  (analyze failures), and the Problems view.
* **Knowledge Management** — build a semantic index of your codebase for retrieval and similarity search.
* **Voice to Text** — dictate code and documentation directly into the editor.
* **Open Source & BYOK** — fully inspectable source code; use any compatible LLM provider with your own API keys.

## Example

Basic setup after installing from the JetBrains Marketplace:

```bash
# 1. Install via Settings/Preferences > Plugins > Marketplace > "Cognotik"
# 2. Configure your API key
#    Settings/Preferences > Tools > Cognotik > API Keys
```

Then, from the editor, right-click selected code and choose an `AI Tools` action:

```text
AI Tools > Edit Selection
AI Tools > Chat about Code
AI Tools > Generate Documentation
```

## Integration

The plugin is a thin IDE layer over Cognotik's core libraries, composing several modules into a single IntelliJ
experience:

* `core`, `lwcore` — foundational task and cognitive planning primitives
* `docops`, `text`, `groovy` — document, text, and script processing utilities
* `webui`, `fileserver` — embedded UI and local file-serving for agent interactions
* `providers` — pluggable LLM provider integrations (OpenAI, Anthropic, AWS Bedrock, etc.)
* `tasklib`, `stdtools` — reusable agentic task and tool implementations

Built for JVM 21+ and targets standard IntelliJ Platform plugin distribution, so it installs and updates like any
other JetBrains Marketplace plugin.