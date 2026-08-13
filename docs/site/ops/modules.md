---
transforms:
- ../../../([^/]*)/build.gradle.kts -> ../modules/$1.md
- ../../../([^/]*)/README.md -> ../modules/$1.md
---

# Task: Module Feature Page

You are transforming a Cognotik **module** (its `build.gradle.kts` and/or `README.md`) into a public-facing,
marketing-style **feature page** for the Cognotik product website. The audience is a new visitor evaluating whether
Cognotik fits their needs — assume no prior familiarity with the codebase.

## Goal

Convert internal, technical module documentation into an engaging, benefit-driven page that:

* Explains **what the module does** in one clear sentence (the "elevator pitch").
* Explains **why it matters** to a developer or team evaluating the platform.
* Lists **key features** and capabilities as scannable bullet points.
* Shows **how the module fits** into the broader Cognotik architecture (reference related modules where relevant).
* Includes at least one **concrete example** (a code snippet, CLI command, or config file) proving the module is real
  and functional — not vaporware.

## Style Guide

* **Tone:** Confident, technical, and concise. Avoid hype-without-substance ("revolutionary", "game-changing").
  Prefer specific, verifiable claims ("JVM-native, zero Python dependency").
* **Audience:** Developers and engineering leads. Assume technical literacy but zero project-specific knowledge.
* **Format:** Output **must be valid Markdown**, using:
    * A single `#` H1 title (the module name).
    * A short italic tagline directly under the title.
    * `##` sections for Overview, Key Features, Example, and (if relevant) Integration/Dependencies.
    * Fenced code blocks with language hints (`kotlin`, `bash`, `json`, etc.) for all code/config examples.
    * Bullet lists rather than dense paragraphs wherever possible.

## Source-to-Output Mapping

| Source Signal (from `build.gradle.kts` / `README.md`)   | Output Section        |
|-----------------------------------------------------------|------------------------|
| Module name / artifact id                                 | H1 Title               |
| Dependencies (`implementation(project(...))`, libs)       | Integration section    |
| README description/intro                                  | Overview               |
| README usage examples, plugin/task blocks                 | Example section        |
| Notable Gradle plugins or custom tasks                     | Key Features           |

## Constraints

* Do **not** copy internal implementation details verbatim (class names, private APIs) unless they are part of the
  module's public-facing usage contract.
* Do **not** include TODOs, internal issue references, or unfinished/experimental disclaimers unless they materially
  affect a user's decision to adopt the module.
* Keep the page short enough to read in under two minutes — this is a feature page, not a spec.