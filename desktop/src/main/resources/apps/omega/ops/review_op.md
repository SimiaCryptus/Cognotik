---
task_type: MultiPerspectiveAnalysis
transforms:
  - ../generated_app/ops/[^/\.]+\.md -> ../review.md
  - ../generated_app/index\.html -> ../review.md
  - ../generated_app/README\.md -> ../review.md
related:
  - ../idea.md
  - ../analysis.md
  - ../pipeline_design.md
---

Perform a comprehensive quality review of the generated DocOps application.

Analyze from these perspectives:

## Pipeline Architect
* Is the DAG well-formed? Are there any cycles?
* Does every target file have exactly one producer?
* Are all transform regex patterns valid Java regex?
* Are all relative paths correct (relative to the ops/ directory)?
* Will transitive target discovery correctly chain all steps?
* Are task types appropriate for each step?
* Are there any missing dependencies or orphaned files?

## Prompt Engineer
* Are the op file prompts specific and detailed enough?
* Do prompts include clear output format instructions?
* Do prompts include quality criteria?
* Are prompts appropriately scoped (not too broad, not too narrow)?

## Frontend Developer
* Does the HTML UI correctly implement the REST API patterns?
* Is the session ID correctly extracted from the URL?
* Are file paths correctly computed?
* Does status polling work correctly?
* Is the UI responsive and user-friendly?
* Are errors handled gracefully?

## Documentation Reviewer
* Is the README complete and accurate?
* Does it match the actual pipeline implementation?
* Are all files and steps documented?
* Is the getting-started guide clear enough for a new user?

## Output Format

For each perspective, provide:
1. **Score**: 1-5 rating
2. **Strengths**: What's done well
3. **Issues**: Specific problems found (with file names and line references)
4. **Recommendations**: Concrete fixes

End with an **Overall Assessment** and a **Priority Fix List** ordered by severity.