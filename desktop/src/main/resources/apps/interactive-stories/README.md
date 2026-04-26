# 📖 Interactive Stories

Craft branching, AI-powered narratives one choice at a time. Write a premise, generate the
opening scene, then explore the story tree by choosing one of three branches (A / B / C) at
every node.

## How it works

The app drives a small DocOps pipeline that turns markdown templates into story nodes:

1. **Story Idea** — You write a premise into `story_idea.md`.
2. **Initial Node** — `ops/initial_node.md` reads the idea and produces `story/0.md`,
   the root of your story, ending with three labeled choices (A, B, C).
3. **Branching** — Each subsequent click on a choice button runs the corresponding op
   (`ops/choice_a.md`, `ops/choice_b.md`, or `ops/choice_c.md`), which transforms the
   current node `story/<path>.md` into a child node `story/<path>{a|b|c}.md`.

Every story node again ends with three new choices, so the tree can grow indefinitely.

## Node naming

Story nodes are stored as flat markdown files in the `story/` directory, with an id that
encodes the path from the root:

- `0`     — initial node (root)
- `0a`    — after choosing A from the root
- `0ab`   — after choosing B from `0a`
- `0abc`  — after choosing C from `0ab`
- …and so on.

This makes the entire branching history visible at a glance from filenames alone.

## Files

- `app.html` / `app.js` / `style.css` — the UI (story idea editor, tree view, node viewer,
  choice buttons, activity log).
- `story_idea.md` — your saved premise (auto-saved as you type).
- `ops/initial_node.md` — generates the root story node.
- `ops/choice_a.md`, `ops/choice_b.md`, `ops/choice_c.md` — generate child nodes from any
  existing node via a regex `transforms` rule.
- `story/*.md` — generated story nodes.

## Usage

1. Pick a **Smart Model** and **Fast Model** at the top of the page (saved between visits).
2. Type a premise into the **Story Idea** box and click **✨ Begin Story** (or **💾 Save Idea**
   to just persist it).
3. Once the root node renders, click **A**, **B**, or **C** under the node to generate that
   branch.
4. Use the **Story Tree** to jump back to any previously generated node and explore a
   different path. Branches that already exist show a ✓ — clicking them just navigates;
   new branches are generated on demand.
5. The **Activity Log** shows DocOp progress; the 🔄 button refreshes the tree from disk.

## Notes

- Existing branches are never overwritten by navigating; only the root node prompts for
  confirmation if you click **Begin Story** again.
- Story generation is asynchronous — the app polls task status and updates the UI when
  each node completes.