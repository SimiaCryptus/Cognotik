# Waterfall Mode

**Best for:** well-defined tasks where you want to see the full plan up front, review or edit it, and then let it run start-to-finish without further intervention.

Waterfall Mode is a classic "plan-then-execute" workflow: it builds one complete task breakdown before any work begins, and then executes that plan in order.

**How it works:**

1. **Gather context.** The mode scans your project's code files and any additional contextual data to understand what it's working with.
2. **Generate a plan.** It sends this context (plus your request) to a planning model, which returns a structured breakdown of tasks — what needs to be done, in what order, and how the pieces depend on each other.
3. **Review and revise (optional).** Unless auto-fix is enabled, the generated plan is presented to you for discussion. You can ask for changes, and the mode will regenerate a revised plan based on your feedback until you're satisfied.
4. **Save the plan.** The finalized plan is written to a timestamped file in your project's logs, so it can be inspected, reused, or replayed later.
5. **Execute the plan.** The task orchestrator runs through every step of the plan in sequence, coordinating any sub-tasks it defines.
6. **Report results.** As execution proceeds, progress and any errors are streamed to a transcript so you can follow along or debug issues after the fact.

Waterfall Mode also supports a **pre-planned** variant: instead of generating a new plan, you can point it at an existing plan file (with `{{variable}}` placeholders), and it will parse your request to figure out which file to use and what values to substitute before executing it directly.

**What you see:**

- A transcript of your original message, followed by a summary of the code files discovered in your project.
- The generated plan, shown in three tabs — **Text** (human-readable plan description), **JSON** (the structured task data), and **Diagram** (a Mermaid flowchart of task dependencies).
- If review is enabled, an interactive discussion thread where you can request changes to the plan before it runs.
- A confirmation that the plan was saved to a log file, with a link to view it.
- An "Executing Plan" section showing step-by-step task execution status.
- Errors, if any occur, are reported clearly with a stack trace in the transcript.

**Key Features:**

- **Plan-first design:** the entire task breakdown is finalized before execution starts, giving you full visibility and control ahead of time.
- **Optional human-in-the-loop review:** revise the plan conversationally before committing to it (unless auto-fix mode is on).
- **Reusable plans:** every generated plan is saved to disk and can be replayed later via the pre-planned mode with variable substitution.
- **Dependency visualization:** plans are rendered as Mermaid diagrams so you can see task relationships at a glance.

**Quick Reference:**

Unlike more adaptive or iterative cognitive modes that re-plan or reconsider their approach after each step, Waterfall Mode commits to a single plan upfront and executes it linearly — making it the most predictable and auditable choice, at the cost of flexibility if circumstances change mid-execution.