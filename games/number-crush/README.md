# Number Crush

I recently started building **Number Crush**, a match-and-clear puzzle
game that swaps the usual color-matching for something a little more
cerebral: arithmetic. Where the "candy-crush" genre asks you to line up
three of the same color, Number Crush asks you to find short lines of
tiles that satisfy a mathematical relationship — a sum, a shared factor,
a run of consecutive values — and rewards you for spotting them quickly.
It is, in a nutshell, the satisfying loop of a matching game wearing the
quiet costume of a math tutor.

---

## What It Is

The board is a grid of tiles, each showing a single integer, arranged in
a **hexagonal packing** so that every interior tile touches up to six
neighbors. You play by drawing a connected line across adjacent tiles —
two to four of them — and if that line satisfies the level's active rule,
the tiles clear, you score, and the tiles above fall down to fill the
gaps (with fresh tiles spawning from the top). It is the familiar rhythm
of select, clear, cascade; the twist is that _what counts as a match_ is
a piece of arithmetic rather than a color.

A few rules give you a sense of the range:

- **Sum equals a target** — the values you select must add up to, say, 10.
- **Sum is a multiple** — the total must be divisible by some number.
- **Shared factor** — every selected tile shares a common factor greater
  than one.
- **Consecutive run** — the values form a sequence like 4-5-6.
- **Same value** — the simplest case, all tiles matching.

Each level leans on one primary rule, stated in plain language before you
start, so you always know what you are hunting for.

---

## The Experience

The interface is deliberately calm and uncluttered. Tiles are clean and
readable; the current selection is highlighted as a path, and a **running
indicator** (for example, the sum so far) helps you reason mid-drag rather
than counting in your head. Get it right and the tiles animate away with a
satisfying clear; get it wrong and the selection gives a gentle shake and
releases — no harsh penalty, just a nudge to try again.

Because the tiles fall and refill after every clear, quick consecutive
matches build into **cascades and combos**, and combos raise your score
multiplier. There is a real pleasure in setting up a board so that one
clever line topples into several more.

Crucially, the game keeps itself playable: after every change to the
board it checks that **at least one valid move still exists**, and if the
board ever deadlocks, it reshuffles rather than stranding you. You should
never find yourself staring at an unsolvable grid.

Planned modes cover the usual comfortable spread — a relaxed **Zen** mode
with no timer, a **Timed** mode for score-chasing, **Puzzle** levels with
targets and move limits, and a seeded **Daily Challenge** where everyone
plays the same board.

---

## A Little Background

Matching puzzles earned their popularity honestly: they are simple to
learn, endlessly re-playable, and immediately legible. Number Crush keeps
those virtues but asks a slightly different question of the player. Instead
of _"which colors line up?"_ it asks _"which numbers relate?"_ — and that
small substitution turns idle pattern-matching into light, genuine mental
arithmetic. It turns out the two impulses fit together rather naturally;
the eye scans for candidates, and the mind confirms them.

Because the game relies on numbers rather than color, it is also, almost
incidentally, **colorblind-friendly** — the information you need lives in
the digits, not the palette.

---

## Why It's Interesting

- **It sneaks in practice.** Every clear reinforces a numeric relationship
  — a sum, a multiple, a factor — without ever feeling like a worksheet.
  The math is the fun, not a tax on it.
- **The hex grid adds depth.** Six neighbors per tile (rather than four)
  means more possible lines and richer board states; there is more to see
  and more to plan.
- **Rule variety keeps it fresh.** One core action — draw a line — supports
  a whole family of rules, from simple sums to consecutive runs, so the
  game can grow in difficulty and flavor without adding new controls to
  learn.
- **It is honest about difficulty.** Levels ramp gradually — wider value
  ranges, tighter targets, smaller budgets, the occasional obstacle tile —
  and telegraph new mechanics before leaning on them.

---

## Who Might Find It Useful

- **Casual puzzle players** who enjoy the match-and-cascade loop but want
  a little more to chew on than color-matching.
- **Learners and parents/teachers** looking for arithmetic practice that
  feels like play; mental addition, multiples, and factors all get a
  workout, and the plain-language rules make each level self-explanatory.
- **The mathematically curious**, who will appreciate that the challenge
  scales from friendly sums to trickier factor and sequence relationships.

In short, if you like puzzles and don't mind (or actively enjoy) a bit of
arithmetic, Number Crush is aimed squarely at you. It is still early, and
there is plenty ahead — more rules, more modes, special tiles, and polish —
but the core idea is simple and, I think, quite satisfying. More soon!
