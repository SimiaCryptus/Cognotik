 # Cognotik Cognitive Mode Best Practices & Configuration
 
 ## 1. Introduction
 
 While **Tasks** represent the "hands" of the Cognotik system (performing actions), **Cognitive Modes** represent the "brain" (strategy and planning). Choosing and configuring the right Cognitive Mode is the single most important factor in determining the success, speed, and cost of an agentic session.
 
 This guide outlines best practices for selecting modes, configuring orchestration, and utilizing hybrid architectures via Sub-Planning.
 
 ---
 
 ## 2. Strategic Mode Selection
 
 Do not default to `AdaptivePlanningMode` for everything. While powerful, it is often overkill. Use this rubric to select the most efficient mode:
 
 | Scenario                     | Recommended Mode   | Why?                                                                                                     |
 |:-----------------------------|:-------------------|:---------------------------------------------------------------------------------------------------------|
 | **Exploration / Debugging**  | `Conversational`   | Lowest latency. Allows you to steer the AI quickly when you don't know the root cause yet.               |
 | **Greenfield Development**   | `Waterfall`        | Generates a cohesive architecture upfront. Prevents "spaghetti code" by forcing a plan before execution. |
 | **Research / Hard Problems** | `AdaptivePlanning` | The "Think-Act-Reflect" loop is necessary when the result of Step 1 determines Step 2.                   |
 | **Batch Operations**         | `Parallel`         | If you need to touch 50 files, `Adaptive` mode will take hours. `Parallel` takes minutes.                |
 | **Critical Decisions**       | `Council`          | Reduces hallucinations by forcing consensus between multiple personas.                                   |
 
 ---
 
 ## 3. Hybrid Architectures (The Sub-Planning Pattern)
 
 The most advanced usage of Cognotik involves mixing modes using the `SubPlanningTask`. This allows you to treat a complex Cognitive Mode as a single atomic task within a larger plan.
 
 ### The "Rigid Outer, Flexible Inner" Pattern
 **Best Practice:** Use a structured mode (like `Waterfall` or `Hierarchical`) for the high-level project management, and delegate difficult sub-steps to `Adaptive` or `Conversational` modes.
 
 **Example Scenario:** Building a full web application.
 1.  **Outer Loop (`WaterfallMode`):**
     *   Step 1: Setup Project Structure.
     *   Step 2: **[SubPlan]** Research and select a database library.
     *   Step 3: Implement User Auth.
 
 2.  **Inner Loop (Step 2 via `AdaptivePlanningMode`):**
     *   The sub-planner autonomously researches libraries, tests them, and returns the best choice.
     *   The Outer Loop waits for the result, then proceeds to Step 3 using the selected library.
 
 **Configuration:**
 When defining a `SubPlanningTask` in your `OrchestrationConfig` or plan, ensure you pass the relevant context but restrict the scope to prevent the sub-agent from modifying unrelated files.
 
 ---
 
 ## 4. Orchestration Configuration
 
 Your `OrchestrationConfig` controls the brain power allocated to the modes.
 
 ### 4.1 Model Selection
 *   **Planning Model (`defaultModel`):** Use your smartest available model (e.g., GPT-4o, Claude 3.5 Sonnet) for `Waterfall` planning and `Council` reasoning. These modes rely heavily on logic and context window.
 *   **Parsing Model:** You can often use a cheaper/faster model for parsing JSON outputs or handling simple `Conversational` interactions to reduce latency.
 
 ### 4.2 Auto-Fix vs. Interactive
 *   **Interactive (`autoFix = false`):**
     *   **Best for:** `Waterfall` and `Conversational`.
     *   **Why:** In Waterfall, you want to review the `plan.json` before execution. In Conversational, you are the "Human-in-the-loop."
 *   **Autonomous (`autoFix = true`):**
     *   **Best for:** `AdaptivePlanning` and `Parallel`.
     *   **Why:** Adaptive mode is designed to self-correct. Stopping for human approval on every reflection cycle defeats the purpose of the "Agent."
 
 ---
 
 ## 5. Performance & Cost Management
 
 ### 5.1 Managing Token Usage in Adaptive Mode
 `AdaptivePlanningMode` maintains a "Reasoning State" (Goals, Facts, Hypotheses). As the session grows, this context window fills up.
 *   **Tip:** If an Adaptive session is stuck in a loop, manually intervene to clear the "Facts" or restart the session with the discovered knowledge as the new prompt.
 
 ### 5.2 Parallel Concurrency
 `ParallelMode` uses a `FixedConcurrencyProcessor`.
 *   **Configuration:** Ensure your thread pool size matches your API rate limits.
 *   **Warning:** Setting concurrency too high (e.g., >10) often triggers rate limits from LLM providers, causing all tasks to fail simultaneously. A safe default is 3-5.
 
 ### 5.3 Council Mode Overhead
 `CouncilMode` triples (or quadruples) the token cost for every decision because it generates distinct personas and then runs a voting round.
 *   **Use Case:** Only use this for architectural decisions or security audits. Do not use it for writing boilerplate code.
 
 ---
 
 ## 6. Debugging Cognitive States
 
 When an agent fails, check the artifacts specific to the mode:
 
 1.  **Waterfall:** Check `plan.json` in the working directory. You can manually edit this JSON to remove bad steps and "resume" the plan.
 2.  **Adaptive:** Look at the **Thinking Status** tab in the UI. If the "Hypotheses" are wrong, the actions will be wrong.
 3.  **Protocol:** Check the **Referee** logs. If the agent keeps failing a state transition, the success criteria in the Protocol definition might be too strict or ambiguous for the LLM.
 
 ---
 
 ## 7. Prompt Engineering for Personas
 
 In `PersonaChatMode` or `CouncilMode`, the specific phrasing of the persona definition alters the tool usage.
 
 *   **"Security Auditor":** Will prioritize `AnalysisTask` and `FileSearchTask`.
 *   **"Hacker":** Will prioritize `RunShellCommandTask` and `RunCodeTask`.
 *   **"Product Manager":** Will prioritize `DecompositionSynthesisTask` (planning) over coding.
 
 **Best Practice:** If the AI isn't using the tools you expect, switch the Persona, not just the prompt.