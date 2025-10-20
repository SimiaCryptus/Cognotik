# ![Cognotik Logo](https://share.simiacrypt.us/cognotik/images/public/icons/icon-64x64.png) Cognotik AI Coding Assistant ![Cognotik Logo](https://share.simiacrypt.us/cognotik/images/public/icons/icon-64x64.png)

## Open Source Generative & Agentic Tools for IntelliJ

[![Build](https://github.com/SimiaCryptus/Cognotik/workflows/Build/badge.svg)](https://github.com/SimiaCryptus/Cognotik/actions)
[![Version](https://img.shields.io/jetbrains/plugin/v/27289-cognotik.svg)](https://plugins.jetbrains.com/plugin/27289-cognotik)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/27289-cognotik.svg)](https://plugins.jetbrains.com/plugin/27289-cognotik)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

<!-- Plugin description -->

**Cognotik** is an open-source AI coding assistant for IntelliJ IDEs, designed to go beyond simple code completion. It provides a suite of powerful, high-level **agentic tools** to automate complex development workflows. At its core is a sophisticated **Cognitive Task Planning Framework** that understands your high-level goals, breaks them down into executable steps, and intelligently carries out the plan using a "Bring Your Own Key" (BYOK) model for maximum control over data, costs, and model choice.

### 🔑 Open Source & Bring Your Own Key (BYOK)

Cognotik embraces transparency and control. As open-source software, you can inspect, modify, and contribute to its development. The BYOK model means you connect your preferred AI service providers (like OpenAI, Anthropic, etc.) using your own API keys. This gives you:

*   **Full Data Control:** Your code and prompts are sent directly to the AI provider you choose, not through intermediary servers.
*   **Cost Management:** You pay the AI provider directly, benefiting from competitive pricing and usage tiers.
*   **Model Flexibility:** Use the latest and most suitable AI models available from your chosen provider.
*   **Enhanced Privacy:** Reduces third-party exposure of your sensitive code.

Our focus on providing robust *tools* rather than a managed *service* ensures a stable, customizable, and cost-effective AI experience tailored to your needs.

### 🤖 High-Level Agentic Tools (Beyond Autocomplete)

Cognotik differentiates itself by focusing on **high-level agentic capabilities** that automate complex workflows. Its "brain" is a powerful **Cognitive Task Planning Framework** that can formulate, present, and execute multi-step plans to achieve your goals. You can choose from different strategies, such as a plan-ahead **Waterfall Mode** for well-defined problems or an interactive, step-by-step **Conversational Mode** for exploratory tasks.

## Key Features

*   **Advanced Task Automation:** Leverage the Cognitive Task Planning Framework to tackle complex goals. The AI agent can perform file operations, execute code, run shell commands, search the web, and even employ advanced reasoning techniques to solve problems from start to finish.
*   **Intelligent Code Editing:** Chat about your code, apply intelligent patches, edit selections with natural language, and paste code with automatic language and style transformation, all directly within the editor.
*   **Project-Wide Operations:** Work across multiple files simultaneously. Generate comprehensive documentation, create new files from a description, apply patches across your codebase, and perform mass refactoring with contextual awareness.
*   **Deep IDE Integration:** Cognotik integrates seamlessly into your workflow. Interact with AI directly from the **Git Log** (to chat with commits), **Find Usages** results, the **Test Runner** (to analyze failures), and the **Problems** view.
*   **Knowledge Management:** Build a semantic index of your codebase, enabling powerful knowledge retrieval and similarity-based searches to quickly find relevant information.
*   **Voice to Text:** Dictate code and documents using built-in voice-to-text capabilities.

<!-- Plugin description end -->

## 🚀 Getting Started

### Installation

1.  Open IntelliJ IDEA, GoLand, PyCharm, or other compatible JetBrains IDE.
2.  Go to `Settings/Preferences` > `Plugins`.
3.  Select the `Marketplace` tab.
4.  Search for "AI Coding Assistant" or "Cognotik".
5.  Click `Install` and restart the IDE when prompted.

### Configuration

1.  After installation, open `Settings/Preferences`.
2.  Navigate to `Tools` > `Cognotik`.
3.  Enter your API keys for the desired AI service providers (e.g., OpenAI API Key).
4.  Configure other settings like preferred models and agent behavior as needed.

### Basic Usage

*   **Editor Actions:** Right-click within the code editor or select code and use the `AI Tools` context menu (or keyboard shortcuts) to access actions like "Edit Selection", "Chat about Code", etc.
*   **File Actions:** Right-click on files or folders in the Project view and look for the `AI Tools` menu for operations like "Generate Documentation", "Create Unit Tests", etc.
*   **Agentic Tools:** Access agentic features through the `AI Tools` menu or dedicated tool windows. Follow the prompts, which may involve interaction in a separate browser window for complex tasks.
*   **Voice Control:** Activate voice input (check settings for keybindings/activation method) and speak commands or dictate code.

---

*Keywords: IntelliJ Plugin, AI Coding Assistant, Generative AI, Agentic AI, Open Source, BYOK, Developer Tools, Code Generation, Code Explanation, Refactoring, Voice Coding, JetBrains, OpenAI, Anthropic, Large Language Models (LLM)*