# Git UI

A single-page, dependency-free browser UI for driving a Git repository through
a small JSON HTTP API. It is meant to be served next to a session/working
directory on the fileserver and talks to that directory's Git repo via
`<base>/.git/api/<op>` endpoints.

Everything lives in `index.html` — no build step, no external JS libraries.

## Quick start

Open `index.html` in a browser under the fileserver. By default the UI derives
its API base from its own URL (the directory containing `index.html`), but you
can point it elsewhere:

- Query string: `index.html?api=/some/session/dir` (also accepts `?base=`)
- Manually: edit the **API base** field in the sidebar and click **Apply**
- The chosen base is remembered in `localStorage` per page path

The **Repository** panel shows the resolved base, current branch and repo
state (`clean` / `modified` / `uninitialized`). If the directory isn't a git
repo yet, use **Init repo** to run `git init` + an initial commit.

## Layout

- **Sidebar**
  - *Repository* — API base configuration, refresh, init
  - *Operation in progress* — appears during an unfinished `merge` / `revert`
    / `cherry-pick` (continue / skip / abort / quit); rebase has no server
    endpoint and must be resolved from a shell
  - *Branches* — create/checkout/delete, merge a branch into the current one
  - *Stashes* — push/apply/pop/drop/show
  - *Remotes* — read-only list of configured remotes

- **Main**
  - *Working tree* — status summary + change list (click a row to diff that
    path); commit message box commits with `git add -A` (Ctrl/Cmd+Enter)
  - **Log** — recent commits with per-commit actions: show, stat, diff vs
    HEAD, checkout, branch here, tag here, cherry-pick, revert, reset
  - **Diff** — diff by revision range and/or path, staged-only, names-only
  - **Show** — show a commit (optionally a file's content at that revision),
    or stat-only
  - **Tags** — create/delete/show tags
  - **Submodules** — add/init/update/sync/deinit, update-all/sync-all
  - **Maintenance** — merge, revert/cherry-pick (batch), reset (mixed/soft/
    hard/merge/keep), clean untracked files (dry-run by default)

- **Console** — a rolling log of every action's outcome (success/warning/error)

## API contract

The UI is a thin client; it assumes:

- All requests go to `POST`/`GET` `<base>/.git/api/<action>`
- Every response is JSON, including error responses
- Mutating/query results share a loose shape: `{ success, error, output, ... }`
  — `success === false` (or an `error` with no explicit `success`) is treated
  as a failure and logged to the console, including any `conflicts` array

Actions used by the UI:

| Method | Action        | Purpose                                   |
|--------|---------------|--------------------------------------------|
| GET    | `status`      | working tree status, current op/conflicts |
| GET    | `branches`    | local/remote branch list                  |
| GET    | `log`         | commit history                            |
| GET    | `tags`        | tag list                                  |
| GET    | `stashes`     | stash list                                |
| GET    | `remotes`     | remote list                               |
| GET    | `submodules`  | submodule list/state                      |
| GET    | `diff`        | diff (range/path/staged/name-only)        |
| GET    | `show`        | show a commit or file-at-revision         |
| POST   | `init`        | `git init` + initial commit               |
| POST   | `commit`      | `git add -A && git commit`                |
| POST   | `branch`      | create/checkout/delete branch             |
| POST   | `checkout`    | checkout a ref/commit                     |
| POST   | `merge`       | merge (no-ff / squash / message)          |
| POST   | `revert`      | revert one or more commits                |
| POST   | `cherry-pick` | cherry-pick one or more commits           |
| POST   | `reset`       | reset (mode + ref)                        |
| POST   | `clean`       | clean untracked files (dry-run supported) |
| POST   | `stash`       | push/apply/pop/drop/show                  |
| POST   | `tag`         | create/delete tag                         |
| POST   | `submodule`   | add/init/update/sync/deinit               |

Continue/skip/abort/quit of an in-progress merge/revert/cherry-pick reuse the
in-progress operation's own action name with `{ sequence: "continue" | "skip"
| "abort" | "quit" }` in the body.

## Notes

- The commit box always commits *all* changes (the API stages with
  `git add -A`); there is no partial staging UI.
- Rebase has no dedicated endpoint — the operation banner tells you to resolve
  it from a shell if `status` reports `operation: "rebase"`.
- Destructive actions (`reset --hard`, deleting a branch/tag, dropping a
  stash, forced clean) ask for confirmation before calling the API.