# Adaptive Planning Mode

  **Best for:** Open-ended or exploratory problems where you don't know the full plan up front — Adaptive Planning
  Mode figures out what to do next based on what's already happened, rather than committing to a rigid upfront plan.

  Adaptive Planning Mode is a cognitive loop that continuously reasons about the current state of the task, selects
  the next best actions to take, executes them in parallel, and then updates its understanding before repeating —
  iterating until the goal is reached or a maximum number of iterations is hit.

  **How it works:**

  1. **Initialize reasoning state** — The mode builds an initial "thinking status" from your prompt and any project
     context, capturing goals, open questions, and known facts.
  2. **Select next tasks** — On each iteration, it reviews the current thinking status and the history of completed
     work, then chooses up to a configurable number of tasks (task types plus specific instructions) that seem most
     useful right now. It can also explore multiple alternative task framings side-by-side using expandable
     "option" branches before settling on a final selection.
  3. **Execute in parallel** — The chosen tasks are dispatched concurrently (not sequentially), since they're treated
     as independent contributions rather than a strict step-by-step plan.
  4. **Reflect and update** — After the tasks complete, their results are folded back into the reasoning state,
     updating goals, knowledge, and confidence for the next round.
  5. **Repeat or stop** — The loop continues until no further tasks are proposed, the maximum iteration count is
     reached, or an unrecoverable error occurs.

  **What you see:**

  - A running transcript of the session, including the initial prompt and a labeled section per iteration.
  - Tabbed views per iteration, showing:
    - **Inputs** — project info, prior task results, and the current thinking status.
    - **Task Execution** — one tab per selected task, showing its configuration and the live output as it runs.
    - **Thinking Status** — a readable rendering of the updated goals/knowledge/state after each iteration.
  - If alternative task framings are explored, a set of nested "option" tabs showing each branch being considered.
  - A final **Summary** panel showing the last known thinking status when the session concludes.
  - Optionally, an interactive review step where you can discuss and revise the proposed task selection before it
    runs, if automatic approval isn't enabled.

  **Key Features:**

  - **Configurable parallelism per iteration** — limits how many tasks are proposed and run at once, keeping the
    loop focused rather than overwhelming.
  - **Bounded iteration count** — a safety limit prevents runaway looping on problems that never converge.
  - **History-aware context window** — task history is included in every reasoning step, trimmed to a configurable
    character budget so older records are summarized/truncated rather than dropped abruptly.
  - **Human-in-the-loop review (optional)** — task selections can be discussed and revised with you before execution
    when automatic approval is turned off.

  **Quick Reference:**

  Unlike a fixed, upfront Waterfall-style plan, Adaptive Planning Mode re-evaluates and re-plans after every batch of
  work, making it better suited to problems that reveal new information as you go. If you already know the full
  sequence of steps needed, a more structured planning mode may get you there with less overhead; Adaptive Planning
  Mode trades some predictability for flexibility.