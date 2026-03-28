---
specifies: ../code/design.md
related:
  - ../code/idea.md
---

* Read the application idea from `idea.md`
* Produce a comprehensive design document in `design.md` that captures:

## Technology Decisions (choose based on the idea's requirements)
  - **Language**: JavaScript or TypeScript
  - **Framework/Library**: React, Vue, Svelte, Vanilla, or other (pick what best fits the idea)
  - **Bundler/Build Tool**: Vite, Webpack, esbuild, Parcel, or other
  - **Test Runner**: Vitest, Jest, Mocha, or other
  - **CSS Approach**: Plain CSS, CSS Modules, Tailwind, Sass/SCSS, or other
  - **Package Manager**: npm (fixed for this pipeline)

## Architecture Decisions
  - Project directory structure
  - Entry point(s) and routing approach (if applicable)
  - State management approach (if applicable)
  - Component hierarchy (if applicable)
  - Build output target (dist/ folder structure)

## Project Metadata
  - Project name (derived from idea)
  - Version: 0.1.0
  - Description
  - npm scripts to define (dev, build, test, lint, etc.)

* The design document should be specific enough that subsequent pipeline stages can generate all files without ambiguity
* Do NOT generate any code files — only the design document
* Justify each technology choice briefly based on the project requirements