# SimSage App Suite

A collection of AI-powered web applications built on a shared document-operations ("doc ops") platform. Each app
transforms user input through one or more AI pipeline stages to produce rich, structured outputs — all from the browser
with no local build tools required.

## Applications

| App                                                                                       | Description                                                                                                                                                                                                     |
|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [🧮 Philosophical Calculator](src/main/resources/apps/philosophical-calculator/README.md) | Multi-perspective analytical toolkit that processes content through philosophical, rhetorical, and creative lenses — dialectical analysis, Socratic dialogue, game theory, persuasive essays, comics, and more. |
| [🏥 Medical AI Diagnostic Pipeline](src/main/resources/apps/health-improvement/README.md) | Interactive, multi-round health analysis tool that guides users from symptom intake through differential diagnosis to personalized action plans and clinical handoff documents.                                 |
| [📚 Comic Serial Generator](src/main/resources/apps/comic-serial/README.md)               | Transforms story ideas into an ongoing comic book series, generating episodes that maintain consistent characters, settings, and narrative arcs.                                                                |
| [🧙 System Wizard](src/main/resources/apps/sys-wizard/README.md)                          | Describe a goal in plain language, and the wizard generates a shell script to accomplish it — then runs and auto-fixes the script until it succeeds.                                                            |
| [🏗️ Webapp Builder](src/main/resources/apps/webapp-factory/README.md)                    | AI-powered web application factory that turns natural language descriptions into complete, runnable web apps with HTML, CSS, and JavaScript.                                                                    |
| [🔮 Omega — App Factory](src/main/resources/apps/omega/README.md)                         | Meta-application that designs and produces other DocOps applications — describe the app you want in plain language and Omega analyzes, designs, generates, and reviews a complete pipeline app.                  |

## Common Architecture

All applications share a common architecture built around the **doc ops** pattern:

```
User Input  ──►  Doc Op (AI Task)  ──►  Generated Output
                      │
               Op Definition (.md)
               with YAML frontmatter
```

### Core Concepts

- **Doc Ops** — Each AI operation is defined by a Markdown file (in an `ops/` directory) with YAML frontmatter
  specifying input/output file mappings (`transforms`), context files (`related`), and the AI task type (`task_type`).
- **File-Based I/O** — All state is stored as files in a session-scoped workspace. Apps read and write files via REST
  endpoints, making the pipeline inspectable and reproducible.
- **Pipeline Orchestration** — Apps compose multiple doc ops into multi-stage pipelines. Steps can run sequentially, in
  parallel, or in iterative loops depending on the application.
- **Session Isolation** — Each user session gets an isolated file workspace identified by a session ID in the URL path.
- **Status Polling** — A shared `docops.status.json` file tracks task progress. UIs poll this file to update badges and
  indicators in real time.

### Technology Stack

| Layer                     | Technology                                                                                             |
|---------------------------|--------------------------------------------------------------------------------------------------------|
| **Frontend**              | Vanilla HTML, CSS, JavaScript (no framework dependencies)                                              |
| **Markdown Rendering**    | [marked.js](https://github.com/markedjs/marked)                                                        |
| **Backend Communication** | REST API — `GET`/`PUT` for file I/O, `POST` for doc op execution                                       |
| **AI Task Types**         | Brainstorming, MultiPerspectiveAnalysis, CrawlerAgent, ComicBookGeneration, AutoFix, SubPlan, and more |
| **Session Monitoring**    | Live proxy endpoint for real-time AI session observation                                               |

### Key API Endpoints

| Endpoint                        | Method | Purpose                                |
|---------------------------------|--------|----------------------------------------|
| `{basePath}/{file}`             | `GET`  | Read a file from the session workspace |
| `{basePath}/{file}`             | `PUT`  | Write a file to the session workspace  |
| `{basePath}/{dir}/_files.json`  | `GET`  | List directory contents                |
| `/docops`                       | `POST` | Execute a doc op (AI pipeline step)    |
| `{basePath}/docops.status.json` | `GET`  | Poll task execution status             |
| `/proxy/#{sessionId}`           | `GET`  | Monitor a live AI session              |

## Project Structure

```
src/main/resources/apps/
├── philosophical-calculator/   # 🧮 Multi-lens content analysis
│   ├── ops/                    #    Operation definitions
│   └── README.md
├── health-improvement/         # 🏥 Medical diagnostic pipeline
│   ├── ops/
│   ├── round_1/
│   ├── round_2/
│   ├── plan/
│   └── README.md
├── comic-serial/               # 📚 Serialized comic generator
│   ├── ops/
│   └── README.md
├── sys-wizard/                 # 🧙 Script generation & auto-fix
│   ├── ops/
│   ├── code/
│   └── README.md
└── webapp-factory/             # 🏗️ AI webapp builder
    ├── ops/
    ├── code/
    └── README.md
└── omega/                      # 🔮 DocOps App Factory
     ├── ops/
     └── README.md
```

## Getting Started

1. **Choose an app** from the table above.
2. **Open it** in your browser — each app is served at its own path under the application root.
3. **Provide input** — write notes, describe a goal, enter symptoms, or sketch an idea depending on the app.
4. **Run the pipeline** — click the run button(s) to trigger AI-powered doc ops.
5. **Review results** — outputs appear as Markdown files rendered directly in the UI.

Each app's README contains detailed usage instructions specific to its workflow.

## Design Principles

- **File-centric** — Everything is a file. Inputs, outputs, operation definitions, and status are all stored as readable
  files in the workspace.
- **Composable pipelines** — Complex workflows are built by chaining simple, single-purpose doc ops.
- **Iterative refinement** — Most apps support multiple rounds of analysis, letting users progressively improve outputs.
- **Transparency** — AI sessions can be monitored in real time; all intermediate artifacts are visible and editable.
- **No build step** — Apps are vanilla HTML/CSS/JS served directly from the resource directory.
- **Session isolation** — Users work in independent sandboxes with no cross-contamination.

## Disclaimer

> ⚠️ These applications use AI-generated content. Outputs should be reviewed by qualified professionals before being
> acted upon — especially for medical, legal, or safety-critical use cases. See individual app READMEs for specific
> disclaimers.