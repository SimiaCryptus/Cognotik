---
task_type: SubPlan
task_config_json: generate_app.json
folder: ../generated_app/
related:
  - ../idea.md
  - ../analysis.md
  - ../pipeline_design.md
---

Generate the complete set of op files for the designed DocOps application.

Using the pipeline design document as your blueprint, create every op file specified in the design.

For each op file:

* The file must be placed in the `ops/` subdirectory
* The YAML frontmatter must include the exact `transforms`, `related`, `task_type`, and any other fields specified in the design
* The markdown body must be a detailed, specific prompt that will produce high-quality AI output
* Prompts should be at least 5-10 bullet points with clear instructions
* Include formatting guidance (use markdown headers, bullet points, tables as appropriate)
* Include quality criteria (what makes good output for this step)

Also create:
* Any starter/template input files the user will need (with helpful placeholder content and instructions)
* Any JSON configuration files needed (e.g., for SubPlan task types)
* A `docops.status.json` placeholder if needed

File naming must follow the conventions established in the pipeline design.
All regex patterns in transforms must be valid Java regex.
All relative paths must be correct relative to the ops/ directory.