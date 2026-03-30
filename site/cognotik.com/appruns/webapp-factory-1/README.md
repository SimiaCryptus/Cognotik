# 🏗️ Webapp Builder

**Webapp Builder** is an AI-powered web application factory that transforms natural language descriptions into complete,
runnable web applications. Describe your idea, and the AI generates a full project with HTML, CSS, and JavaScript —
ready to launch in your browser.

## Overview

Webapp Builder provides a streamlined pipeline for going from concept to working prototype:

1. **Describe** your webapp idea in plain text or Markdown
2. **Run** the AI-powered build pipeline
3. **Launch** your generated webapp directly in the browser
   No backend setup, no build tools, no configuration — just describe what you want and let the AI handle the rest.

## Features

### 💡 Idea Editor

- Rich textarea for describing your webapp in detail
- Supports Markdown formatting for structured descriptions
- Auto-saves before pipeline execution
- Persists your idea across sessions

### ⚙️ AI Build Pipeline

- **Single-step pipeline**: Takes your idea and renders a complete project
- Uses a `Waterfall` cognitive mode for structured, sequential code generation
- Generates game design docs, spec documents, and full implementation
- Real-time status polling with live session monitoring
- Visual pipeline diagram showing progress through each stage

### 🚀 Live Preview & Launch

- **Embedded iframe preview** of the generated webapp directly in the Results tab
- **One-click launch** to open the webapp in a new browser tab
- Preview banner appears automatically when the app is ready
- Refresh controls for updating the preview after regeneration

### 📊 Results Dashboard

- **Live Preview** tab with embedded iframe of the generated app
- **README** tab showing the generated project documentation
- **Project Files** tab with an expandable file browser
- Syntax-highlighted code viewing for all generated files
- Directory browsing with file-type icons
- Inline file viewer for quick inspection

### 📡 Session Monitoring

- Links to monitor live AI sessions in real-time
- Session logs for completed and failed tasks
- Error diagnostics with direct links to error logs

## Architecture

### File Structure

```
webapp-factory/
├── app.html              # Main application UI
├── app.js                # Client-side application logic
├── style.css             # Dark-themed UI styles
├── marked.min.js         # Markdown rendering library
├── ops/
│   ├── render_op.md      # Pipeline operation definition
│   └── render_project.json  # Task configuration for the SubPlan
├── idea.md               # User's webapp description (created on save)
├── code/                 # Generated webapp output directory
│   ├── index.html        # Generated app entry point
│   ├── README.md         # Generated project documentation
│   └── ...               # Other generated files (CSS, JS, etc.)
└── README.md             # This file
```

### Pipeline Flow

```
💡 Idea (idea.md)  →  🏗️ Render Project (SubPlan)  →  📄 Output (code/)
```

1. **Input**: The user's idea is saved to `idea.md`
2. **Render**: The `render_op.md` operation triggers a `SubPlan` task that:

- Reads the idea from `idea.md`
- Plans the project architecture
- Generates design/spec documents
- Implements all HTML, CSS, and JavaScript files
- Produces a `README.md` for the generated project

3. **Output**: All generated files are written to the `code/` directory

### Technology Stack

- **Frontend**: Vanilla HTML, CSS, JavaScript (no framework dependencies)
- **Styling**: Custom dark theme with CSS custom properties
- **Markdown**: [marked.js](https://marked.js.org/) for rendering Markdown content
- **Backend Integration**: DocOps API for file I/O and AI task execution
- **AI Engine**: SubPlan task type with Waterfall cognitive mode

### Key API Endpoints Used

| Endpoint                        | Method | Purpose                      |
|---------------------------------|--------|------------------------------|
| `{basePath}/{file}`             | `GET`  | Read file contents           |
| `{basePath}/{file}`             | `PUT`  | Write file contents          |
| `{basePath}/{dir}/_files.json`  | `GET`  | List directory contents      |
| `/docops`                       | `POST` | Execute a pipeline operation |
| `{basePath}/docops.status.json` | `GET`  | Poll task execution status   |
| `/proxy/#{sessionId}`           | `GET`  | Monitor live AI sessions     |

## UI Design

- **Dark theme** with a modern, minimal aesthetic
- **Sticky navigation** with section tabs: Idea, Pipeline, Results, and Launch
- **Responsive layout** that works on both desktop and mobile
- **Visual feedback** throughout:
- Animated badges for task status (pending, running, done, error)
- Pulsing indicators for active sessions
- Gradient accents and glow effects for key actions
- **Color palette**:
- Primary: `#6c8cff` (blue)
- Accent: `#f59e42` (orange)
- Success: `#4ade80` (green)
- Launch: `#38bdf8` (sky blue)

## Usage

1. **Open** the Webapp Builder in your browser
2. **Write** a detailed description of your webapp in the Idea tab

- Include target users, key features, design preferences, and tech requirements
- Use Markdown headers and bullet points for clarity

3. **Click** "💾 Save Idea" or go directly to the Pipeline tab
4. **Click** "▶ Run Pipeline" to start the AI build process
5. **Monitor** progress via the pipeline diagram and session links
6. **View** results in the Results tab once the build completes
7. **Launch** your webapp using the 🚀 Launch App button in the navigation bar

## Tips for Best Results

- **Be specific**: The more detail you provide about features, layout, and behavior, the better the output
- **Describe the UI**: Mention specific UI patterns (e.g., "Kanban board", "sidebar navigation", "modal dialogs")
- **Specify constraints**: Note if you want vanilla JS only, specific color schemes, or responsive breakpoints
- **Include examples**: Reference well-known apps or design patterns for clarity
- **Iterate**: Run the pipeline multiple times with refined descriptions to improve results