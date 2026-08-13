---
transforms:
  - ../../../desktop/README.md -> ../ways-to-run/desktop.md
  - ../../../desktop/build.gradle.kts -> ../ways-to-run/desktop.md
  - ../../../cli/README.md -> ../ways-to-run/cli.md
  - ../../../cli/bin/fileserver -> ../ways-to-run/cli.md
  - ../../../intellij/README.md -> ../ways-to-run/intellij.md
  - ../../../intellij/build.gradle.kts -> ../ways-to-run/intellij.md
---

# Task: "Ways to Run Cognotik" — Per-Surface Page

This task is run **once per surface** (Desktop, CLI, IntelliJ Plugin) and produces a single, standalone page for
that one surface. Each run synthesizes two source files for the surface at hand — its `README.md` (setup/usage)
and its build/launch file (`build.gradle.kts` or a launcher script like `cli/bin/fileserver`) — into one page
describing how to run Cognotik that way. Do not attempt to cover the other surfaces or produce a comparison; other
ops runs handle those separately.

## Goal

For the surface being processed in this run, produce a page that:

1. **Frames the surface** — an H1 naming this way of running Cognotik (e.g. "Running Cognotik on Desktop",
    "Running Cognotik from the CLI", "Cognotik IntelliJ Plugin") and a one-line intro, drawn from the README's
    intro, explaining what this surface is and who it's for.
2. **Getting Started** — a short numbered list of install/launch steps distilled from the README's usage
    instructions.
3. **Packaging/launch notes** — any detail from the build/launch file that materially affects an end user's
    experience (e.g. a native installer/DMG/EXE is produced, a launcher script is provided, the plugin is
    distributed as a `.zip`/published to the JetBrains Marketplace) — described in plain language, not as raw
    Gradle config or shell internals.
4. **(Optional) See Also** — a brief closing pointer noting that Cognotik can also be run other ways, without
    describing them; keep this to a single sentence so the page stays focused on its one surface.

## Style Guide

* **Tone:** Friendly and practical — a focused how-to for this one surface, not a sales pitch or a comparison.
* **Format:** Valid Markdown:
     * Single `#` H1 title + one-line intro.
     * A `## Getting Started` section with a numbered list of steps.
     * A short section (e.g. `## Packaging & Launch`) covering any user-facing packaging/launch detail from the
       build file, if applicable.
     * Optionally, a brief closing sentence pointing to the fact that other ways to run Cognotik exist.

## Source-to-Output Mapping

| Source Signal                                          | Output Section                |
|---------------------------------------------------------|--------------------------------|
| Surface's `README.md` intro                              | H1 intro / one-liner           |
| Surface's `README.md` usage instructions                 | `## Getting Started`           |
| Surface's `build.gradle.kts` packaging tasks/plugins      | `## Packaging & Launch`         |
| `cli/bin/fileserver` launch behavior (CLI run only)       | `## Getting Started` / launch note |

## Constraints

* Do not invent install/run steps that aren't present in the surface's README — if the README is thin, keep the
   page thin rather than padding it with guesses.
* Do not dump Gradle internals (task names, plugin ids, dependency coordinates) — only mention build/packaging
   facts that change what an end user does or receives (e.g. "ships as a double-clickable installer" vs. "run via
   `./gradlew run`").
* Keep the page skimmable in under a minute — this is a focused how-to for one surface, not a manual for every
   feature of that surface.
* Do not produce a comparison table or reference the other surfaces' setup steps — that belongs on a separate
   overview page, not here.