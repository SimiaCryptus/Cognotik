# Ω Omega — DocOps App Generator

Omega is an AI-powered application generator built on the Cognotik DocOps platform. Describe your app idea in plain language, and Omega will automatically generate a complete, working DocOps application — including requirements, pipeline operation files, and a full single-page UI.

---

## What It Does

Omega takes a natural-language description of an application idea and produces:

1. **Requirements Document** — A structured specification covering inputs, pipeline steps, outputs, file naming conventions, and UI requirements.
2. **Pipeline Op Files** — A complete set of DocOps operation files (`.md`) that define the AI processing pipeline for the generated app.
3. **Single-Page UI** — A fully functional HTML/CSS/JS application with input editors, pipeline controls, status badges, and result viewers — tailored to the generated pipeline.

---

## How to Use

### 1. Describe Your Idea

Navigate to the **💡 Idea** tab and write a description of the application you want to build. Be specific about:

- What the user provides as input
- What processing steps should happen (brainstorming, research, analysis, code generation, etc.)
- What the final outputs should be

**Example:**
```
I want an app that takes a business idea and produces a complete pitch deck outline,
competitive analysis (via web research), and a one-page executive summary.

User Input: A markdown file describing the business idea and target market
Desired Output:
  - Pitch deck outline (10-12 slides)
  - Competitive landscape analysis
  - One-page executive summary
```

Click **💾 Save Idea** to persist your description.

---

### 2. Configure Models (Optional)

Navigate to the **🤖 Models** tab to select which AI models to use for generation:

- **Smart Model** — Used for complex reasoning tasks (requirements analysis, pipeline generation, UI creation)
- **Fast Model** — Used for simpler tasks (parsing, formatting, quick transformations)
- **Image Model** — Used for image generation and processing

Click **💾 Save Model Settings** to persist your selections across sessions.

---

### 3. Generate Your App

Navigate to the **⚙️ Generate** tab. You can run each step individually or use **▶ Run Entire Generation Pipeline** to generate everything automatically.

#### Step 1 — Generate Requirements
Analyzes your idea and produces a structured `requirements.md` document with inputs, pipeline steps, outputs, file naming conventions, and UI requirements.

#### Step 2 — Generate Pipeline Ops
Creates all pipeline operation files in `generated_app/ops/`, including JSON configs, starter templates, and a README. Uses a SubPlan to produce multiple files in one pass.

#### Step 3 — Generate UI
Builds a complete single-page HTML/CSS/JS application in `generated_app/`, tailored to the generated pipeline.

Each step shows a **status badge** (pending → running → done / error) and a **live session monitoring link** so you can watch the AI work in real time.

---

### 4. Iterate and Refine

Navigate to the **🔄 Iterate** tab to refine the generated app based on your feedback.

#### Pipeline Update Notes
Describe changes to the pipeline — new steps, bug fixes, modified prompts, removed steps. Click **▶ Update Pipeline** to apply changes.

#### UI Update Notes
Describe changes to the UI — new tabs, bug fixes, layout changes, new viewers. Click **▶ Update UI** to apply changes.

Use **▶ Run All Updates** to apply both pipeline and UI updates sequentially.

---

### 5. Browse Results

Navigate to the **📁 Results** tab to inspect the generated files:

| Sub-tab | Contents |
|---|---|
| 📄 README | The generated app's README |
| 📋 Requirements | The structured requirements document |
| ⚙️ Pipeline Ops | Browse and view all generated op files |
| 🎨 UI Source | The generated `index.html` source |
| 📂 All Files | Full file tree browser for `generated_app/` |

---

### 6. Version Control & Export

Navigate to the **🔀 Git** tab to track changes and export your work:

- **Initialize Repository** — Start tracking changes with Git
- **Commit Changes** — Snapshot the current state with a commit message
- **Branches** — Create and switch branches to experiment with different versions
- **Commit History** — Browse the full commit log
- **Download as ZIP** — Export the entire session or just the generated app as a ZIP archive

---

## Generation Pipeline

```
Your Idea (idea.md)
      │
      ▼
[requirements_op]  ──►  requirements.md
      │
      ▼
[generate_pipeline_op]  ──►  generated_app/ops/*.md
      │
      ▼
[generate_ui_op]  ──►  generated_app/index.html
```

---

## File Structure

```
{session}/
├── idea.md                    # Your app description (you write this)
├── requirements.md            # Generated requirements document
├── pipeline_notes.md          # Your pipeline update feedback
├── ui_notes.md                # Your UI update feedback
├── ops/                       # Omega's own operation files
│   ├── requirements_op.md
│   ├── generate_pipeline_op.md
│   ├── generate_ui_op.md
│   ├── update_pipeline_op.md
│   └── update_ui_op.md
└── generated_app/             # Your generated application
    ├── index.html             # The generated UI
    ├── README.md              # Generated app documentation
    └── ops/                   # Generated pipeline op files
        ├── step_one_op.md
        ├── step_two_op.md
        └── ...
```

---

## Tips for Best Results

- **Be specific in your idea.** The more detail you provide about inputs, outputs, and processing steps, the better the generated pipeline will be.
- **Name your pipeline steps.** If you describe steps like "Step 1: Brainstorm → Step 2: Research → Step 3: Summarize", the generator will follow that structure.
- **Mention special capabilities.** If you need web research, say "use CrawlerAgent". If you need multi-perspective analysis, say so explicitly.
- **Iterate freely.** The Update Pipeline and Update UI operations are designed for incremental refinement — use them often.
- **Commit before major changes.** Use the Git tab to snapshot working versions before running updates.
- **Monitor live sessions.** Click the 📡 Monitor links that appear while operations are running to watch the AI work in real time.

---

## Platform Requirements

Omega runs on the Cognotik DocOps platform and requires:

- At least one configured AI provider with API keys (OpenAI, Anthropic, etc.)
- Access to the `/docops`, `/apiProviders/`, and `/proxy/` endpoints
- A valid authenticated session

Configure API providers in the platform settings before using Omega.