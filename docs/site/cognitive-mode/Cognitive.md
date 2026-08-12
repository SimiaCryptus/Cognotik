# Cognitive Mode

**Best for:** Understanding the shared foundation that every Cognotik workflow — Waterfall, Adaptive Planning, Task
Voting, and any future mode — is built on. This isn't a mode you select directly; it's the common "engine" that
powers all of them.

Cognitive Mode is the general contract that every specific workflow follows: receive a message from you, decide what
to do about it, do it, and keep a record of what happened along the way.

**How it works:**

1. **Initialize** — When a session starts, the active mode sets up whatever internal state it needs (task lists,
   planning trees, voting queues, etc.).
2. **Listen** — Your message is handed to the mode, along with a live transcript file that will capture everything
   that happens next.
3. **Decide & Act** — The mode interprets your message using its own strategy (this is where modes diverge — one
   might build a full plan up front, another might negotiate step-by-step, another might let tasks vote on
   priority).
4. **Record** — Every mode writes a timestamped Markdown transcript of its reasoning and actions, so you always have
   a readable log of what the AI did and why.
5. **Accumulate Context** — Results and intermediate findings are captured so that if the mode spawns sub-tasks or
   sub-plans, those child processes inherit the right context automatically.

**What you see:**

* A **"Transcript" link** appears near the start of every session — this opens a live-updating Markdown log named
  after the active mode (e.g. `AdaptivePlanningMode_20240101120000.md`), so you can always trace the mode's reasoning
  after the fact.
* Which **tasks are available** to the mode (coding, search, file edits, etc.) is determined by your project's
  configuration — you'll only see actions the mode is actually permitted to take.
* The **mode's name** is surfaced in the UI so you always know which cognitive strategy is currently steering the
  session.

**Key Feature:** Every mode shares the same transcript and context-tracking mechanics, so switching between modes
(e.g. from Waterfall to Adaptive Planning) doesn't change how you review history — only how decisions get made.

**Quick Reference:**

* This is the **base contract**, not a selectable mode — you'll choose from concrete modes like Waterfall Mode,
  Adaptive Planning Mode, or Task Voting Mode, each of which implements this same lifecycle differently.
* Unlike any single concrete mode, Cognitive Mode has no opinion about *how* planning or execution happens — it only
  guarantees that whatever mode you pick will listen to you, act, and leave behind a readable trail.