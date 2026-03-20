---
task_type: MultiPerspectiveAnalysis
transforms: ../analysis\.md -> ../pipeline_design.md
related:
  - ../idea.md
---

Design the complete pipeline architecture for the DocOps application described in the analysis.

You are designing a pipeline that will be implemented as a set of markdown op files with YAML frontmatter. Your design must be precise enough to directly translate into working op files.

Analyze from these perspectives:

- **Pipeline Architect**: Design the optimal DAG structure. Ensure no cycles. Maximize parallelism where possible. Ensure every file has exactly one producer. Verify that transitive target discovery will correctly chain all steps.
- **Prompt Engineer**: For each op file, outline what the markdown body prompt should instruct the AI to do. Ensure prompts are specific, detailed, and produce well-structured output.
- **UX Designer**: Design the user experience flow. Where does the user provide input? Where are human-in-the-loop checkpoints? How should the UI present results? What buttons and panels are needed?
- **Quality Engineer**: Identify where things could go wrong. Are regex patterns correct? Are relative paths consistent? Are task types appropriate for each step?

## Required Output Format

For each op file, specify:

### `ops/{name}_op.md`
- **Purpose**: One-line description
- **task_type**: The AI task type
- **transforms**: The exact YAML transforms line(s), with correct regex and relative paths
- **related**: Any related context files (with correct relative paths)
- **Prompt outline**: Key bullet points for the markdown body
- **Produces**: What file(s) this step creates
- **Depends on**: What file(s) must exist before this step runs

Also specify:
- **Directory structure**: Complete tree of the generated app
- **File naming conventions**: Regex-friendly naming rules
- **UI layout**: Panels, buttons, and their behaviors
- **Data flow diagram**: ASCII art showing the complete DAG

Ensure all transform regex patterns use Java regex syntax (escape dots, use proper capture groups).
Ensure all paths are relative to the `ops/` directory (most start with `../`).