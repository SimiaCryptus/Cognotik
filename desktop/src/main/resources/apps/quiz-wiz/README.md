# 🧠 Quiz Wiz

**AI-powered quiz game generator pipeline**

Quiz Wiz is a DocOps-driven application that turns a plain-English quiz idea into a fully working, playable web-based
quiz game. It orchestrates a multi-step AI pipeline that designs, populates, implements, and refines a quiz game
codebase — all from a single browser UI.

---

## ✨ What It Does

Starting from a one-paragraph description of a quiz, Quiz Wiz will:

1. **Capture** your quiz idea.
2. **Design** the data schemas and game flow.
3. **Generate** a JSON file of quiz questions, answers, and explanations.
4. **Implement** a working web app codebase that plays the quiz.
5. **Refine** the result iteratively based on your feedback notes.

Each step is powered by a configurable AI model and produces real artifacts you can browse, download, and play.

---

## 🗺️ Pipeline Overview

| Step | Name            | Op File              | Output Folder | Model |
|------|-----------------|----------------------|---------------|-------|
| ①    | Quiz Idea       | *(user input)*       | `idea.md`     | —     |
| ②    | Design          | `ops/design_op.md`   | `design/`     | smart |
| ③    | Game Data       | `ops/gamedata_op.md` | `gamedata/`   | fast  |
| ④    | Implementation  | `ops/impl_op.md`     | `code/`       | smart |
| ⑤    | Update / Refine | `ops/update_op.md`   | `code/`       | smart |

### Step Details

- **② Design** — Produces `question_data_schema.ts`, `result_schema.ts`, and `game_flow.md` describing the data
  structures and gameplay flow.
- **③ Game Data** — Writes a uniquely-named `.json` file under `gamedata/` containing all quiz questions,
  multiple-choice answers, and explanations.
- **④ Implementation** — Renders the design docs and game data into a modular, maintainable web codebase under `code/`.
  Reads game data from `../gamedata` and writes user results back to `../results/`.
- **⑤ Update** — Reads `notes.md` (your feedback / bug reports / feature requests) plus the existing `code/` files and
  applies the requested changes while preserving untouched functionality.

---

## 🖥️ The UI

Quiz Wiz presents each pipeline stage as a card in a single-page app (`app.html` + `app.js`):

- **⚙️ AI Models** — Pick a *Smart* model (design / code) and a *Fast* model (game data / edits). Selections are
  persisted in `localStorage` under the `quizWhiz` prefix.
- **① Quiz Idea** — A textarea editor for `idea.md` with save support.
- **② Design** — Run the design step, view generated files, and preview `game_flow.md` inline as rendered Markdown.
- **③ Game Data** — Generate quiz JSON and browse the resulting files.
- **④ Implement Game Code** — Build the playable codebase. When an `index.html` is produced, the **▶️ Open Game** button
  becomes active.
- **⑤ Update / Refine** — Save feedback into `notes.md` and re-run to apply changes.
- **📡 Active Sessions** — Live status of every DocOps task started during the page-load, with monitor links.
- **📜 Activity Log** — A scrolling log of every UI action, success, warning, and error.

Status badges (`pending` → `running` → `done` / `error`) appear on each step and update automatically via background
polling.

---

## 📁 Project Layout

```
quiz-wiz/
├── app.html              # Main UI
├── app.js                # UI logic, pipeline orchestration
├── style.css             # Styling
├── README.md             # This file
├── utils/                # Shared client utilities (session, models, fileIO, docops, ui)
└── ops/                  # DocOps step definitions
    ├── design_op.md
    ├── gamedata_op.md
    ├── impl_op.md
    └── update_op.md
```

At runtime, a session folder also contains:

```
<session>/
├── idea.md               # Your quiz idea
├── notes.md              # Your update / refinement notes
├── design/               # Generated schemas + game flow doc
├── gamedata/             # Generated quiz JSON files
├── code/                 # Generated playable web app
└── results/              # User quiz results (written by the game)
```

---

## 🚀 Quick Start

1. Open the Quiz Wiz app in a valid session URL (the session ID is parsed from the URL).
2. Pick your **Smart** and **Fast** models.
3. Write your quiz idea in **Step ①** and click **💾 Save Idea**.
4. Click **🎨 Generate Design** in **Step ②** and wait for the badge to turn green.
5. Click **📚 Generate Quiz Data** in **Step ③**.
6. Click **🛠️ Build Code** in **Step ④**, then **▶️ Open Game** when it appears.
7. Try the game. If you want changes, write them in **Step ⑤**, save the notes, and click **♻️ Apply Updates**.

Repeat step 7 as many times as needed.

---

## 💡 Idea Prompt Tips

A good `idea.md` typically describes:

- **Topic(s)** — what the quiz is about.
- **Audience** — casual, expert, kids, etc.
- **Difficulty** — easy / medium / hard, or a mix.
- **Number of questions**.
- **Question style** — multiple choice, true/false, etc.
- **Special rules** — timers, scoring, hints, themes, accessibility needs.

Example:

> A 10-question multiple-choice quiz about classic 1980s arcade games, aimed at casual gamers. Include fun trivia and
> explanations for each answer.

---

## 🔄 Update Notes Tips

`notes.md` should be a concise, actionable list. The update op preserves anything you don't mention, so be specific
about what to change. Example:

```
- Fix scoring bug on the last question
- Add a 20-second timer per question
- Use a darker color theme
- Show an explanation after each answer is submitted
```

---

## 🔧 Technical Notes

- **Session bootstrap** — `parseSessionUrl()` extracts `basePath` and `sessionId` from the current URL; the app refuses
  to start without them.
- **DocOps execution** — Each step calls `runDocOp(sessionId, opFile, target, modelOverrides)` and then
  `waitForTask(...)` (10-minute timeout) while a `createStatusPoller` keeps badges and the Active Sessions panel in
  sync.
- **Model overrides** — Both `smartModel` and `fastModel` are sent on every run; the servlet selects the appropriate one
  based on the op definition.
- **Pre-flight checks** — Steps ②–④ require `idea.md`; step ⑤ requires `notes.md`. Missing prerequisites trigger a toast
  and abort the run.
- **Auto-refresh** — File listings for `design/`, `gamedata/`, and `code/` refresh every 15 seconds so the UI stays
  current across reloads.
- **Markdown preview** — `game_flow.md` is rendered inline using a vendored `marked.min.js`.

---

## 🧩 Extending Quiz Wiz

- **Add a new step** — Drop a new `ops/<name>_op.md` file with appropriate front-matter (`folder:`, `related:`), then
  add a matching entry to the `STEPS` map in `app.js` and a corresponding card in `app.html`.
- **Change output formats** — Edit the relevant op file under `ops/` to adjust schemas, file names, or behavioral
  instructions.
- **Theme the UI** — All visual styling lives in `style.css`.

---

## ⚠️ Troubleshooting

- **"Could not determine session from URL"** — Open the app via its proper session URL.
- **Step badge stuck on `running`** — Check the **📡 Active Sessions** panel for a monitor link, and the **📜 Activity Log
  ** for errors.
- **No "Open Game" button** — The implementation step did not produce an `index.html`. Inspect `code/` and use the
  Update step to ask for one.
- **Models not loading** — A failure to load AI providers is logged in the Activity Log; verify the backend
  `/api/providers` endpoint is reachable.

---

## 📜 License

Part of the larger DocOps apps suite. See the parent repository for licensing details.