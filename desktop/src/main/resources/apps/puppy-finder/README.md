# 🐶 Puppy Finder Pipeline

An AI-powered multi-stage pipeline application that helps you discover the perfect puppy breed, research reputable
breeders, and receive personalized recommendations — all through an interactive web interface.

## Overview

Puppy Finder guides you through a structured, four-step process:

1. **Breed Brainstorm** — An AI brainstorms dog breeds that match your stated requirements.
2. **Expand Breed Details** — Each brainstormed breed is expanded into its own detailed profile file.
3. **Breeder Research** — A web-crawling agent searches for reputable breeders for each breed.
4. **Final Summary** — All breed profiles and breeder research are synthesized into a comprehensive recommendation
   report.

## How It Works

### Architecture

The application is a single-page web app (`app.html`, `app.js`, `style.css`) that communicates with a backend file-index
and document-operations (DocOps) API. Each pipeline stage is defined by a declarative **operation file** (in `ops/`)
that specifies input/output file transforms and task behavior.

```
requirements.md          ← You write this
│
▼
[Breed Brainstorm]     ← ops/breed_brainstorm_op.md
│
▼
ideas.md              ← Brainstormed breed list
│
▼
[Expand Breeds]        ← ops/breed_expand_op.md
│
▼
breeds/*.md           ← Individual breed profile files
expand_status.md      ← Status confirmation
│
▼
[Breeder Research]     ← ops/breeder_research_op.md (CrawlerAgent)
│
▼
breeder_research/*.md ← Research results per breed
│
▼
[Final Summary]        ← ops/breeder_summary_op.md
│
▼
final_summary.md      ← Comprehensive recommendation report
```

### Operation Files

Each operation is a Markdown file with YAML front-matter that declares:

- **`transforms`** — A regex-based mapping from input file(s) to output file(s).
- **`related`** — Additional context files the operation can reference.
- **`task_type`** — (Optional) Specifies a specialized agent type (e.g., `Brainstorming`, `CrawlerAgent`).

  | Operation        | File                         | Description                                                     |
  |------------------|------------------------------|-----------------------------------------------------------------|
  | Breed Brainstorm | `ops/breed_brainstorm_op.md` | Reads `requirements.md`, produces `ideas.md`                    |
  | Expand Breeds    | `ops/breed_expand_op.md`     | Reads `ideas.md`, creates individual files in `breeds/`         |
  | Breeder Research | `ops/breeder_research_op.md` | For each breed file, crawls the web for breeder info            |
  | Final Summary    | `ops/breeder_summary_op.md`  | Aggregates all breed and research files into `final_summary.md` |

## User Interface

### Sections

The UI is organized into three tabbed sections:

- **📝 Requirements** — A text editor where you describe your ideal puppy (size, temperament, living situation, budget,
  location, etc.).
- **⚙️ Pipeline** — Step-by-step controls to run each stage individually or in batch, with a visual pipeline diagram
  showing real-time status.
- **📊 Results** — Browse the final summary, individual breed profiles, and breeder research in an accordion-style
  viewer.

### Features

- **Visual pipeline diagram** with live status indicators (Pending → Running → Done).
- **Real-time status polling** via `docops.status.json` — monitors task progress without page refresh.
- **Live session monitoring** — Each running task provides a clickable link to monitor the AI agent's session in real
  time.
- **Batch execution** — Run the entire pipeline end-to-end, or run stages 1–2 and 3–4 separately.
- **Inline Markdown rendering** — All generated files are rendered as formatted HTML in the browser.
- **Breed and research grids** — Visual card-based display of discovered breeds and research results.
- **Responsive design** — Works on desktop and mobile devices.

## Getting Started

### Prerequisites

- A running backend server that supports:
- **File Index API** — `GET`, `PUT` for reading/writing files; `_files.json` for directory listings.
- **DocOps API** — `POST /docops` to trigger document operations.
- **Status API** — `docops.status.json` for polling task status.
- **Proxy** — `/proxy/#<sessionId>` for live session monitoring.

### Usage

1. **Open the app** in your browser. The URL should include a valid session ID in the file-index path (e.g.,
   `/apps/puppy-finder/fileIndex/<sessionId>/app.html`).
2. **Write your requirements** in the Requirements tab. Describe what you're looking for:

   - Family size and ages
   - Living situation (apartment, house, yard size)
   - Desired size, energy level, temperament
   - Allergy concerns
   - Experience with dogs
   - Budget and location

3. **Save your requirements** by clicking 💾 Save Requirements.
4. **Run the pipeline** — either step-by-step using the individual ▶ Run buttons, or all at once with the batch
   execution buttons:

   - **▶ Run Entire Pipeline** — Runs all four steps sequentially.
   - **▶ Run Steps 1–2** — Brainstorm and expand only.
   - **▶ Run Steps 3–4** — Research and summarize only (requires breeds to exist).

5. **Monitor progress** in the pipeline diagram and batch log. Click the 📡 Monitor Live Session links to watch the AI
   work in real time.
6. **Review results** in the Results tab — read the final summary, browse individual breed profiles, and explore breeder
   research.

## File Structure

```
puppy-finder/
├── app.html                        # Main application page
├── app.js                          # Application logic (pipeline control, file I/O, status polling)
├── style.css                       # Styles (responsive, warm color theme)
├── marked.min.js                   # Lightweight Markdown-to-HTML renderer
├── README.md                       # This file
├── ops/
│   ├── breed_brainstorm_op.md      # Step 1: Brainstorm operation definition
│   ├── breed_expand_op.md          # Step 2: Expand operation definition
│   ├── breeder_research_op.md      # Step 3: Research operation definition (CrawlerAgent)
│   └── breeder_summary_op.md       # Step 4: Summary operation definition
├── requirements.md                 # User-written requirements (created at runtime)
├── ideas.md                        # Brainstormed breed list (generated)
├── expand_status.md                # Expand step status (generated)
├── breeds/                         # Individual breed profiles (generated)
│   ├── labrador_retriever.md
│   ├── golden_retriever.md
│   └── ...
├── breeder_research/               # Breeder research per breed (generated)
│   ├── labrador_retriever.md
│   ├── golden_retriever.md
│   └── ...
└── final_summary.md                # Final recommendation report (generated)
```

## Technical Notes

- **Session detection**: The app parses the browser URL to extract the `sessionId` from the `/fileIndex/<sessionId>/`
  path segment.
- **Status polling**: Polls `docops.status.json` every 3 seconds to update badge states, pipeline diagram, and session
  monitor links.
- **Task completion**: After triggering a DocOp, the app polls until the task status transitions to `COMPLETED`,
  `ERROR`, or times out (default: 10 minutes).
- **Markdown rendering**: Uses a bundled minimal Markdown parser (`marked.min.js`). For production use, replace with the
  full [marked.js](https://github.com/markedjs/marked) library.
- **Error handling**: Failed tasks display error badges and link to the session log for debugging.