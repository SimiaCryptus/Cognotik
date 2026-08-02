# Arithmetic Dynamics

An HTML + modular ES6 game that teaches kids the _mechanics_ of math
through a touch-first (mouse-compatible) puzzle interface.

---

## Core Philosophy

Traditional arithmetic education leans heavily on memorization and rapid
recall. This game inverts that priority:

- **Memorization is a side effect, not a goal.** Familiarity with the number
  line 0–20 and a basic notion of what each operation _does_ is the only
  prerequisite.
- **Understanding the dynamics of math is the goal.** Kids should build an
  intuition for commutativity, associativity, composition, distribution,
  and decomposition through play.
- **Math is a puzzle, not a quiz.** A problem is a shape to be reshaped, not
  a fact to be retrieved. The reward loop comes from _transforming_ an
  expression into a simpler one, step by step.

---

## The Central Mechanic: Transformation

Every level presents an expression. The player never "types the answer."
Instead, they repeatedly apply legal transformations until the expression
collapses into its simplest form.

### Worked Example

Solve `4 + 19`:

1. Decompose each term into something friendlier:
   `(5 - 1) + (20 - 1)`
2. Flatten the grouping (associativity):
   `20 + 5 - 1 - 1`
3. Combine the easy pair:
   `25 - 2`
4. Finish:
   `23`

The lesson is not "4 + 19 = 23." The lesson is _how_ you can bend numbers
into rounder, easier shapes and recombine them.

---

## Operation Pairing

Operations are introduced and treated as inverse **pairs**:

- `+` and `-` (the first pair, always available)
- `*` and `/` (optional, unlocked later)

Pairing matters because most transformations rely on an operation and its
inverse working together:

- Decomposition: `4 → 5 - 1` (uses `+`/`-`)
- Scaling tricks: `18 → 20 - 2`, or later `6 × 5 → 30`
- Cancellation: `+3 - 3 → 0`

Treating them as pairs keeps the rule-set small and the mental model
consistent: every "build up" move has a matching "tear down" move.

---

## Player-Facing Transformations (the "verbs")

The player's toolkit is a small, discoverable set of gestures/actions:

- **Split** a number into a sum or difference (`19 → 20 - 1`).
- **Swap** two terms across a commutative operation (`5 + 3 → 3 + 5`).
- **Group / Ungroup** terms to add or remove parentheses (associativity).
- **Combine** two adjacent, compatible terms into one (`25 - 2 → 23`).
- **Cancel** an inverse pair that nets to zero (or one, for `×`/`÷`).

Each verb is _always legal only when it preserves the value._ The game
silently guarantees correctness of the transformation itself, so the child
experiments freely without fear of "breaking" the math — the only question
is whether a move gets them _closer_ to the goal.

---

## UI Design

### Layout

- **Expression Stage** (center): the live expression, rendered as chunky,
  tappable _tiles_ — one tile per number, operator, and parenthesis.
- **History Ribbon** (top or side): each completed transformation appears as
  a stacked row, showing the path taken. This makes the "steps" tangible and
  encourages reflection ("how did I get here?").
- **Toolbar / Radial Menu** (contextual): when a tile or selection is
  touched, the available verbs surface as large icons.

### Interaction Model

- **Touch-first**, mouse-compatible. Everything is a big, forgiving target.
- **Drag to combine / rearrange.** Drag one tile onto an adjacent tile to
  Combine; drag past a neighbor to Swap.
- **Tap to split.** Tapping a number opens a friendly "how do you want to
  break this apart?" chooser (e.g. presets like "nearest ten ± something").
- **Pinch / lasso to group** a run of tiles into parentheses; a reverse
  gesture ungroups.
- **Undo is first-class and unlimited.** Mistakes are cheap; exploration is
  encouraged.

### Visual Language

- Numbers that are "round" (5, 10, 20…) glow subtly, hinting they are good
  landing spots for decomposition.
- Inverse pairs share a color coding, reinforcing the `+`/`-` and `×`/`÷`
  pairings.
- Combining tiles has a satisfying merge animation; canceling pairs "pop"
  and vanish.
- No red X's, no timers by default. The tone is calm and playful.

---

## Progression & Difficulty

- **Sandbox first.** Early levels are open-ended: any valid path to the
  simplest form wins. There is rarely one "right" sequence.
- **Curated challenges.** Later levels introduce goals like "solve in the
  fewest moves" or "reach the answer using only Split and Combine."
- **Gentle unlocks.** `×`/`÷` and larger numbers appear once the additive
  pair feels comfortable.
- **Concept spotlights.** Occasional levels are designed to make one
  property obvious (a "commutativity level," an "associativity level"),
  without ever naming the jargon to the child up front.

---

## Feedback & Motivation

- **Progress is the path, not just the answer.** The History Ribbon is a
  trophy of the child's reasoning.
- **Multiple solutions celebrated.** Finishing reveals alternate paths other
  players (or the game) found, framed as "here's another way!"
- **Optional star goals** (fewest moves, specific verbs used) give replay
  value without punishing exploration.
- **No fail state.** You can always undo, reset, or wander.

---

## Learning Outcomes (implicit, never lectured)

By playing, a child internalizes:

- Numbers can be **decomposed and recomposed** freely.
- Order (**commutativity**) and grouping (**associativity**) don't change
  value.
- Complex problems **factor into simple sub-steps**.
- Every operation has an **inverse**, and they cooperate.
- Fluency (the "facts") emerges naturally from repeated, meaningful play.

---

## Non-Goals

- Not a timed drill or flashcard app.
- Not a curriculum-aligned worksheet replacement.
- Not reliant on reading heavy instructions — the verbs are discovered by
  touching things.
- Not punishing: there is no wrong exploration, only unfinished puzzles.
