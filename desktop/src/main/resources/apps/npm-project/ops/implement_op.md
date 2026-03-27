---
task_type: SubPlan
task_config_json: implement_project.json
folder: ../code/
related:
  - ../code/design.md
  - ../code/idea.md
  - ../code/package.json
  - ../code/src/**
---

* Read the design document in `design.md` for architecture and technology decisions
* Read the original idea in `idea.md` for feature requirements
* Review the existing scaffold files in `code/` to understand the current structure
* Implement the full application:

## Implementation Guidelines
  - Write all source files using the language and framework specified in the design
  - Implement all features described in the idea document
  - Follow the component/module structure defined in the design
  - Include proper imports, exports, and module organization
  - Add inline comments for complex logic
  - Ensure the code is consistent with the chosen framework's conventions and best practices
  - Create all necessary assets (CSS/styles, images references, etc.)
  - Ensure `index.html` (or equivalent entry point) properly loads the application

## Quality Standards
  - Code should be modular and maintainable
  - Error handling should be present where appropriate
  - The application should be fully functional after build
  - Do NOT modify `package.json` dependencies — if new deps are needed, note them for a follow-up install