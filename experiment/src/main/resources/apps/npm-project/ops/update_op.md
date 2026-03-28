---
task_type: SubPlan
task_config_json: implement_project.json
folder: ../code/
related:
  - ../code/**
  - ../code/notes.md
  - ../code/idea.md
  - ../code/design.md
---

* Read the update notes in `notes.md` which contain user feedback, bug reports, feature requests, and design changes
* Review the original idea in `idea.md` and design in `design.md` for context
* Review the existing project files in the `code/` folder to understand the current implementation
* Apply the requested changes from `notes.md` to the existing project files:

## Update Guidelines
  - Preserve existing functionality that is not mentioned in the notes
  - If new dependencies are needed, update `package.json`
  - If the changes affect the build configuration, update config files accordingly
  - Update or add tests to cover the changes
  - Update `README.md` if the changes are significant
  - If the design decisions change (e.g., adding TypeScript to a JS project), update `design.md` as well
  - Maintain consistency with the existing code style and architecture
  - Note any changes that require a fresh `npm install` or rebuild