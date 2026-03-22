---
folder: ../generated_app/ops/
related:
  - ../requirements.md
  - ../idea.md
  - ../docs/PIPELINE.md
  - ../generated_app/*.md
---

Generate the complete set of pipeline op files for the DocOps application described in the requirements document.

## IMPORTANT

* DO NOT edit the files in the parent /ops/ directory (i.e. `../../ops/`)
* ONLY create/edit new op files in the current directory (which is implicitly `generated_app/ops/`)
* After generating the pipeline, generate a brief `README.md`

## Context

The related file `PIPELINE.md` is the authoritative developer guide for writing DocOps pipelines. Follow its conventions exactly.

## What to Generate

Based on the requirements document, create for each pipeline step specified in the requirements:

* Create files with descriptive names ending in `_op.md`
* Mainly operating on files in the parent `../` directory (which is the root of the generated app)
* YAML frontmatter must include:
  - `transforms`: exact regex source → destination mappings (Java regex syntax, paths relative to `ops/`, so generally prefixed with `../`)
  - `related`: any supplementary context files (again paths relative to `ops/`)
  - `task_type`: appropriate task type for the step
  - `task_config_json`: if using SubPlan, reference the config file
  - `folder`: if using SubPlan, specify the working directory
* Markdown body must be a detailed AI prompt (5-15 bullet points) with:
  - Clear instructions for what to produce
  - Output format guidance
  - Quality criteria

### Supporting Files

* Any JSON configuration files needed (e.g., for SubPlan task types)
* Starter/template input files with helpful placeholder content and instructions for the user
* A `README.md` documenting the generated app's purpose, pipeline flow, and usage

### Quality Requirements

* All regex patterns must be valid Java regex (escape dots, proper capture groups)
* All relative paths must be correct relative to the `ops/` directory (most start with `../`)
* Every target file must have exactly one producer
* The DAG must have no cycles
* Transform patterns must match the file naming conventions from the requirements