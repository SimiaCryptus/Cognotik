---
task_type: SubPlan
task_config_json: generate_pipeline.json
folder: ../generated_app/ops/
related:
  - ../requirements.md
  - ../idea.md
  - ../docs/PIPELINE.md
  - ../generated_app/ops/*.md
  - ../generated_app/ops/*.json
  - ../pipeline_notes.md
---

Update the existing pipeline op files for the DocOps application based on user feedback and change requests.

## Context

The related file `PIPELINE.md` is the authoritative developer guide for writing DocOps pipelines. Follow its conventions exactly.

The `pipeline_notes.md` file contains user feedback, bug reports, feature requests, and design changes for the pipeline.

The existing `ops/*.md` and `ops/*.json` files show the current pipeline implementation.

## What to Do

* Read the update notes in `pipeline_notes.md` which contain user feedback, bug reports, feature requests, and design changes for the pipeline
* Review the original idea in `idea.md` and requirements in `requirements.md` for context on the project's goals
* Review the existing pipeline op files in `ops/` to understand the current implementation
* Apply the requested changes from `pipeline_notes.md` to the existing pipeline files
* Preserve existing pipeline steps and functionality that are not mentioned in the notes
* When adding new pipeline steps:
  - Follow the same conventions as existing op files
  - Ensure YAML frontmatter includes correct `transforms`, `related`, `task_type`, and other required fields
  - Write detailed AI prompts (5-15 bullet points) in the markdown body
* When modifying existing steps:
  - Preserve the file's overall structure
  - Update only the parts that need changing
  - Ensure regex patterns remain valid Java regex
* When removing pipeline steps:
  - Verify no other steps depend on the removed step's outputs
  - Update any downstream steps that referenced removed files
* Update the `README.md` if the changes are significant enough to warrant documentation updates
* Ensure all relative paths remain correct relative to the `ops/` directory
* Verify the DAG has no cycles after changes
* Ensure every target file still has exactly one producer

## Quality Requirements

* All regex patterns must be valid Java regex (escape dots, proper capture groups)
* All relative paths must be correct relative to the `ops/` directory
* Every target file must have exactly one producer
* The DAG must have no cycles
* Transform patterns must match the file naming conventions from the requirements