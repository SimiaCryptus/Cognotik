---
task_type: Brainstorming
transforms: ../idea\.md -> ../analysis.md
---

Analyze the user's app idea and produce a structured analysis document. You are an expert DocOps pipeline architect.

* **Identify the core workflow**: What are the logical stages from input to output?
* **Classify each stage** by the most appropriate task type:
  - `Brainstorming` — divergent ideation, generating options
  - `MultiPerspectiveAnalysis` — evaluative analysis from multiple viewpoints
  - `CrawlerAgent` — web research to gather external information
  - `SubPlan` — complex multi-file generation needing internal planning
  - `FileModification` — structured file creation/editing (default)
  - `CodeReview` — quality review and critique
  - `AutoFix` — run and iteratively fix scripts
* **Identify data flow**: What files does each stage read and produce?
* **Identify fan-out points**: Where does one input produce many outputs?
* **Identify fan-in points**: Where do many inputs converge into one output?
* **Identify iteration opportunities**: Would multiple rounds improve quality?
* **Identify human-in-the-loop checkpoints**: Where should the user review/edit before continuing?
* **List all files** that will exist in the final app (inputs, intermediates, outputs, ops, config, UI)

Produce the analysis as a well-structured markdown document with clear sections and bullet points.