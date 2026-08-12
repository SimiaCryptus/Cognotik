# Conversational Mode

**Best for:** Ongoing, chat-style collaboration where you want to talk naturally, reference earlier topics, and
occasionally fan out a request into multiple variations or a sequence of related tasks — all without restarting
context each time.

**How it works:**

1. **Capture your message** — Each message you send is queued and echoed into the conversation transcript
   immediately, so you always see what you asked.
2. **Expand shorthand syntax** — Before anything runs, your message is scanned for special expansion patterns:
    - `@[option1|option2|option3]` — runs the message once per alternative, in parallel.
    - `@{item1 -> item2 -> item3}` — runs the message once per item, in order, feeding results forward.
    - `@(1..10:2)` — expands a numeric range into a sequence of tasks.
    - `@TopicName` — automatically substitutes previously discovered entities of that type (see step 5).
3. **Choose a task** — For each expanded message, the system reviews the conversation history and proposes the
   single most suitable task type and configuration to execute. If auto-fix is disabled, you'll be asked to
   confirm or revise the proposed plan before it runs.
4. **Execute and collect** — The chosen task runs against your working directory, and its output is streamed back
   and appended to the conversation transcript.
5. **Extract topics** — After a response completes, named entities are automatically extracted from it and
   grouped by type (e.g. people, files, features). These become available for later `@Topic` references, so you
   can say "compare @Bug" and have it expand to every bug mentioned so far.
6. **Repeat** — The full exchange (your message plus the assistant's response) is added to conversation history,
   so future turns have full context.

**What you see:**

- A running **transcript** of your messages and the assistant's replies, rendered as markdown.
- A **"Plan"** tab showing the reasoning behind the chosen task and its execution configuration (as JSON), followed
  by an **"Output"** tab with the actual result.
- When you use expansion syntax, a set of **tabs** — one per alternative, sequence step, or range value — each
  showing its own plan and output, so you can compare parallel branches side by side.
- A **"Topics"** panel listing entities extracted from each response, grouped by type (e.g. `` `{Bug}` - `login-crash`, `timeout-error` ``), so you know what's now available for `@Topic` shorthand.
- If you decline auto-fix, a **discussion loop** where you can revise the proposed task before it executes.

**Key Features:**

- **Persistent conversation memory** — history carries across turns and across expansions, so context never resets.
- **Expansion syntax** — a lightweight way to multiply a single message into parallel alternatives, sequences, or
  ranges without retyping it.
- **Topic memory** — entities mentioned in responses are remembered and can be referenced again by name, letting
  conversations build on themselves.
- **Optional human-in-the-loop planning** — when auto-fix is off, you get a chance to review and refine the task
  plan before it runs.

**Quick Reference:**

Unlike single-shot or Waterfall-style modes that execute a fixed plan from start to finish, Conversational Mode is
built for back-and-forth dialogue — it picks one task per message, remembers what's been discussed, and lets you
branch a single message into many variations on demand.