# Arithmetic Dynamics

A touch-first puzzle game that teaches kids the _mechanics_ of arithmetic —
not by drilling facts, but by letting them bend, break, and recombine
numbers until a problem collapses into its simplest shape.

---

## What This Is

Most early math education leans heavily on memorization and rapid recall:
flashcards, timed quizzes, the anxious hunt for the "right answer."
Arithmetic Dynamics inverts that priority. Here, a problem is not a fact to
be retrieved; it is a shape to be _reshaped_. The child never types an
answer — instead, they repeatedly apply small, playful transformations
until the expression simplifies on its own.

Consider `4 + 19`. Rather than reaching for a memorized sum, a player might:

1. Round the awkward numbers into friendlier ones — `(5 - 1) + (20 - 1)`;
2. Flatten the grouping — `20 + 5 - 1 - 1`;
3. Combine the easy pair — `25 - 2`;
4. Finish — `23`.

The lesson is not "4 + 19 = 23." The lesson is _how_ you can nudge numbers
into rounder, easier forms and recombine them at will. Fluency — the "facts"
everyone worries about — emerges quietly as a side effect of meaningful,
repeated play.

---

## How It Feels to Play

Every level presents an expression rendered as chunky, tappable **tiles** —
one tile per number, operator, and parenthesis. The child manipulates them
directly:

- **Split** a number into a friendlier pair (`19 → 20 - 1`).
- **Swap** two terms across an operation (`5 + 3 → 3 + 5`).
- **Group / Ungroup** terms to add or remove parentheses.
- **Combine** two neighbors into one (`25 - 2 → 23`).
- **Cancel** an inverse pair that nets to nothing (`+3 - 3 → 0`).

The interaction is deliberately forgiving. Tap a number to open a friendly
"how do you want to break this apart?" chooser; drag one tile onto its
neighbor to combine them; drag past a neighbor to swap. A history ribbon
along the edge stacks each completed step, turning the child's reasoning
into a visible trail — a small trophy of _how they got here_.

Crucially, **every available move is guaranteed to preserve the value.** The
engine silently enforces correctness of the transformation itself, so the
only open question is ever whether a move gets you _closer_ to the goal. A
child can experiment freely, without fear of "breaking" the math.

The visual language reinforces the ideas without lecturing: round numbers
(5, 10, 20…) glow subtly to hint that they make good landing spots;
inverse operation pairs (`+`/`-` and, later, `×`/`÷`) share color coding;
combining tiles merge with a satisfying animation, and canceled pairs pop
and vanish. There are no red X's and no timers by default — the tone is
calm and playful. Undo is first-class and unlimited; mistakes are cheap and
exploration is the point.

---

## Why It's Interesting

What makes this approach compelling, I think, is that it makes several deep
ideas _tangible_ rather than abstract. By treating operations as inverse
**pairs** — `+` with `-`, `×` with `÷` — the rule-set stays small while the
mental model stays consistent: every "build up" move has a matching "tear
down" move. Playing with those pairs, a child internalizes a surprising
amount without ever hearing the jargon:

- Numbers can be **decomposed and recomposed** freely.
- Order (**commutativity**) and grouping (**associativity**) don't change
  the value.
- Complex problems **factor into simple sub-steps**.
- Every operation has an **inverse**, and the two cooperate.

It turns out that framing arithmetic as a puzzle of _transformation_ rather
than _recall_ changes the reward loop entirely. Progress becomes the path,
not just the destination; multiple solutions are celebrated ("here's another
way!"); and there is no fail state to shrink away from. Optional star goals —
solve in the fewest moves, or reach the answer using only certain verbs —
add replay value for the curious without ever punishing a wanderer.

---

## Who Might Find It Useful

- **Kids** comfortable with the number line 0–20 and the rough idea of what
  each operation _does_ — that familiarity is the only prerequisite. From
  there, the game is discovered by touching things rather than reading
  instructions.
- **Parents and educators** looking for a calm, exploratory complement to
  traditional practice — something that builds number sense and flexible
  strategy rather than speed. It's touch-first and tablet-friendly, but works
  with a mouse too.
- **Anyone curious about math pedagogy** who wants to see what it looks like
  to teach the _dynamics_ of arithmetic — commutativity, associativity,
  decomposition, inverse operations — through play instead of drills.

A few honest caveats worth stating up front: this is **not** a timed drill or
flashcard app, **not** a curriculum-aligned worksheet replacement, and **not**
reliant on heavy reading. It won't hand a child their multiplication tables on
a schedule. What it offers instead is intuition — the sense that numbers are
pliable, that there is rarely one "right" sequence, and that math can be a
place to explore rather than a quiz to survive.

Enjoy — and I'm looking forward to seeing what kinds of puzzles kids invent
for themselves along the way.
