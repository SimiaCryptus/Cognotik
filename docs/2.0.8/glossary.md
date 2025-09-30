# Cognotik Glossary

## Core Concepts

* **Agent Types** - Functional augmentations to the LLM Chat interface that enable specialized behavior:
    * **`ChatAgent`** - Basic text input and output for general conversation
    * **`CodeAgent`** - Code generation, compilation, and execution with syntax highlighting
    * **`ProxyAgent`** - Use an LLM to simulate a Java interface, with both structured input and output
    * **`ParsedAgent`** - Extract structured data responses from the LLM into typed objects
    * **`ImageAgent`** - Create images from text prompts using AI image models

* **Task Types** - Specific invocable procedures that may or may not utilize an LLM, designed for automated execution:
    * **`FileModificationTask`** - Create, modify, or delete files in the project
    * **`AnalysisTask`** - Analyze code and generate insights or documentation
    * **`SelfHealingTask`** - Execute commands and automatically fix issues that arise
    * **`RunShellCommandTask`** - Execute shell commands safely with output capture
    * **`VectorSearchTask`** - Perform semantic search using AI embeddings
    * **`GitHubSearchTask`** - Search GitHub repositories, code, and issues

* **Cognitive Modes** - Control flow strategies for agentic behavior and task execution:
    * **`AdaptivePlanningMode`** - Iterative planning with adaptive task selection and learning
    * **`WaterfallMode`** - Traditional waterfall-style planning with upfront task definition
    * **`HierarchicalPlanningMode`** - Hierarchical goal decomposition with dependency management
    * **`ConversationalMode`** - Conversational task execution with maintained context
    * **`DependencyGraphMode`** - Software graph-based planning with dependency awareness

* **API Providers** - Service integrations for various AI and external services:
    * LLM Model Providers:
      * **Anthropic** - Claude models and related services
      * **AWS** - Amazon's AI services and models
      * **Gemini** - Google's AI models
      * **Groq** - High-speed inference models
      * **OpenAI** - GPT models and other OpenAI services
      * **DeepSeek** - Advanced reasoning and coding models
      * **Mistral** - Mistral AI models for multilingual capabilities
      * **Perplexity** - Perplexity AI models for search and reasoning
      * **ModelsLab** - Specialized AI models and services
    * Other API Providers:
      * **Google** - Google Search
      * **GitHub** - GitHub API for repository and code search
      * **SearchAPI** - Various web search APIs
