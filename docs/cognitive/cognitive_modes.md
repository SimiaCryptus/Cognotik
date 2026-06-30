# Cognotik Cognitive Modes: User Guide

Welcome to the Cognotik User Guide. This document explains the various **Cognitive Modes** available in the system. Each
mode represents a different strategy for how the AI thinks, plans, and executes tasks to solve your requests.

---

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [Conversational Modes](#1-conversational-modes)
    * [Conversational Mode](#conversational-mode)
    * [Persona Chat Mode](#persona-chat-mode)
3. [Planning & Execution Modes](#2-planning--execution-modes)
    * [Waterfall Mode](#waterfall-mode)
    * [Adaptive Planning Mode](#adaptive-planning-mode)
    * [Hierarchical Planning Mode](#hierarchical-planning-mode)
4. [Advanced Orchestration Modes](#3-advanced-orchestration-modes)
    * [Council Mode](#council-mode)
    * [Protocol Mode](#protocol-mode)
    * [Parallel Mode](#parallel-mode)
5. [Advanced Syntax & Features](#advanced-syntax--features)

---

## Core Concepts

Before choosing a mode, understand these three elements:

* **Tasks:** Atomic actions the AI can perform (e.g., writing code, reading files, searching the web).
* **Orchestrator:** The engine that manages the working directory and executes the tasks chosen by the cognitive mode.
* **Working Directory:** The file system area where the AI reads and writes data.

---

## 1. Conversational Modes

### Conversational Mode

**Best for:** General assistance, quick questions, and interactive debugging.

* **How it works:** This is a standard chat interface. For every message you send, the AI analyzes the history and
  chooses **one** specific task to execute.
* **Key Feature:** It maintains a continuous history, allowing you to build on previous results.

### Persona Chat Mode

**Best for:** Specialized consulting (e.g., talking to a "Security Auditor" or a "Scientific Researcher").

* **How it works:** Similar to Conversational Mode, but the AI adopts a specific **Cognitive Strategy**. It maintains an
  internal "Persona State" that evolves as the conversation progresses.
* **Strategies available:** Project Manager, Scientist, Agile Developer, Critical Auditor, Creative Writer.

### Coding Mode

**Best for:** Interactive coding, data analysis, and scripting.

* **How it works:** The AI operates as a REPL (Read-Eval-Print Loop) assistant.
    1. It translates your request into executable code (e.g., Groovy).
    2. It executes the code in a runtime environment that has access to project tools.
    3. It displays the code, the output, and the result value.
* **Key Feature:** Allows direct manipulation of the environment and tools via code, with the AI handling the syntax and
  API usage.

---

## 2. Planning & Execution Modes

### Waterfall Mode

**Best for:** Well-defined, linear projects where you want to see the full plan before any work starts.

* **How it works:**
    1. The AI analyzes your request and generates a complete **JSON Plan** containing all necessary steps.
    2. You can review and discuss the plan.
    3. Once finalized, the system executes the steps sequentially.
* **Pro Tip:** It saves a `plan.json` in your directory. You can "resume" or "re-run" specific plans by referencing this
  file.

### Adaptive Planning Mode

**Best for:** Complex, "fuzzy" problems where the next step depends on the result of the previous one.

* **How it works:** This mode uses an iterative "Think-Act-Reflect" loop.
    1. **Think:** Updates its "Reasoning State" (Goals, Facts, Hypotheses).
    2. **Act:** Nominates and executes up to 5 tasks in parallel.
    3. **Reflect:** Analyzes the results and updates the plan for the next iteration.
* **Visuals:** You can track the AI's "Thinking Status" in a dedicated tab to see what it currently believes to be true.

### Hierarchical Planning Mode

**Best for:** Massive projects with many dependencies (e.g., building a full software application).

* **How it works:** It builds a **Goal Tree**.
    1. It breaks your high-level request into **Goals**.
    2. It decomposes Goals into **Sub-goals** or **Tasks**.
    3. It manages dependencies (e.g., "Don't start Task B until Goal A is complete").
* **Key Feature:** Provides a real-time visual tree of your project's progress.

---

## 3. Advanced Orchestration Modes

### Council Mode

**Best for:** High-stakes decisions or tasks requiring multiple perspectives.

* **How it works:** It simulates a meeting between different AI personas (e.g., a CEO, a CTO, and a QA Engineer).
    1. **Nomination:** Each council member suggests tasks based on their specialty.
    2. **Voting:** The council votes on which tasks are most important.
    3. **Execution:** The winning tasks are executed.
* **Benefit:** Reduces "AI hallucinations" and ensures technical feasibility and quality.

### Protocol Mode

**Best for:** Strict, multi-stage workflows or "State Machine" logic.

* **How it works:** The AI defines a "Protocol" (a set of states).
    1. **Action:** The AI performs the work required for the current state.
    2. **Referee:** A separate "Referee" agent validates the output against success criteria.
    3. **Transition:** If passed, the Referee moves the session to the next state; if failed, it triggers a retry.

### Parallel Mode

**Best for:** Batch processing and automation (e.g., "Run a security scan on every `.kt` file in this folder").

* **How it works:** You provide a template and a list of variables.
* **Combination Modes:**
    * **CrossJoin:** Runs every combination of variables (e.g., 2 files x 2 tests = 4 tasks).
    * **Zip:** Pairs items together (e.g., File A with Test A, File B with Test B).
* **Key Feature:** Uses a `FixedConcurrencyProcessor` to run many tasks simultaneously without crashing your system.

---

## Advanced Syntax & Features

### Expansion Syntax

In most modes (especially Conversational and Parallel), you can use special syntax to trigger multiple actions at once:

1. **Alternatives:** `@[option1|option2]`
    * *Example:* "Write a unit test for @[AuthService|DataService]" will trigger two parallel tasks.
2. **Sequences:** `@{Step 1 -> Step 2 -> Step 3}`
    * *Example:* "Process the data @{Clean -> Analyze -> Summarize}" will run these in order.
3. **Ranges:** `@(1..5)` or `@(1 to 10 by 2)`
    * *Example:* "Generate @(1..3) variations of the logo."
4. **Topic References:** `@Files` or `@{Source Code}`
    * If the AI has previously identified "Topics" in the chat, you can refer to them as a group using the `@` symbol.

### Transcripts

Every session generates a detailed **Markdown Transcript**.

* Look for the "Writing transcript to..." link at the start of your session.
* Transcripts include the full reasoning process, task inputs, and raw outputs.
* In many modes, you can also view these as HTML or PDF.

---

## Choosing the Right Mode: Quick Reference

| If you want to...                      | Use this Mode             |
|:---------------------------------------|:--------------------------|
| Just chat and do one thing at a time   | **Conversational**        |
| Write and run code interactively       | **Coding**                |
| Build a complex app with a strict plan | **Waterfall**             |
| Solve a hard problem step-by-step      | **Adaptive Planning**     |
| Manage a project with many sub-parts   | **Hierarchical Planning** |
| Get a second and third opinion         | **Council**               |
| Automate a task across 50 files        | **Parallel**              |
| Follow a strict "Checklist" workflow   | **Protocol**              |