# The Simplest Increment

## A cubic-speed engine for π, and what it reveals about the architecture of numbers

There is an iteration so simple it looks like a typo:

```
x → x + sin(x)
```

Feed it a number reasonably close to π, apply it a few times, and the error
collapses at a rate proportional to its own cube. Three correct digits become
nine; nine become twenty-seven. The convergence is violent once it begins — and
the surprising part is not _that_ it works, but _what kind of machine it is_, and
what that machine tells us about the hidden structure of the real numbers.

This project is a written exploration of that iteration together with a companion
verification harness that checks every quantitative and symbolic claim the essay
makes. This document is the reader's orientation to both.

---

## What you are looking at

The material comes in two layers, and it helps to know which is which before you
dive in.

- **The essay** (the main narrative) is a piece of mathematical exposition. It
  develops the x + sin(x) iteration from first principles, situates it in a
  "tower" of number systems, and argues — carefully, and with its own honest
  caveats — that it represents a small but genuine new kind of computational
  object.
- **The companion script** is the essay's conscience. It reproduces the symbolic
  cancellations from scratch in a computer algebra system, iterates the map in
  exact rational arithmetic, and measures the convergence rates, certificate
  sizes, and iteration counts the essay predicts. Where the prose says "cubic,"
  the script fits a slope and reports whether it actually came out near 3.

You do not need to run anything to get the ideas; the essay stands on its own.
But if you are the sort of reader who trusts a claim more once a machine has
checked it, the harness is there, and its final report is organized as a plain
table of _claim, prediction, measured value, verified?_ — no ceremony, just
verdicts.

---

## The idea, in one breath

Write the error at step n as e = x − π. One application of the map gives, after
using the identity sin(π + e) = −sin(e) and the Taylor expansion of sine:

```
e_next = e + sin(π + e) = e − (e − e³/6 + …) = e³/6 + …
```

The linear term cancels exactly; the quadratic term is absent by symmetry; what
survives is a cubic. That single cancellation is the whole story. It is not a
numerical trick — it is a consequence of the symmetry of the sine function about
π, the same symmetry that makes every even derivative of sine vanish there.

The essay's central claim is that this cubic behavior follows from _three_
independent conditions on the update function g (here g = sin):

1. g(π) = 0 — π is a fixed point,
2. g′(π) = −1 — the linear error term cancels,
3. g″(π) = 0 — the quadratic error term cancels.

Miss the third and you get a merely _quadratic_ engine. Sine at π satisfies all
three at once, and the companion script includes an explicit counter-example — a
perturbed sine that keeps the first two conditions but breaks the third — to show
the convergence quietly drop from cubic to quadratic. That demonstration is, to
me, the most persuasive single experiment in the whole project.

---

## Why it is interesting

A few reasons, in ascending order of ambition.

- **It is minimal.** Cubic convergence usually costs you something — a derivative
  evaluation, a division, an elliptic-integral step. This map needs none of them.
  It is arguably the simplest closed-form iteration that converges to π faster
  than quadratically.
- **It is a "design principle," not an accident.** The essay reframes the whole
  thing as _derivative engineering_: you don't stumble onto cubic convergence,
  you _sculpt_ it by choosing an update function whose value, slope, and
  curvature line up with the target in a prescribed way. The convergence order
  becomes a theorem about Taylor coefficients rather than a lucky observation.
- **It sits at a boundary.** The most speculative and (I think) most rewarding
  part of the essay places this iteration in a tower of number systems — the
  rationals built from the integers by inversion, the algebraic numbers built by
  root-extraction, the analytic numbers where π lives — and reads the map as the
  _constructive witness_ for inverting the sine function at its zero. π, on this
  reading, is precisely the object you are forced to adjoin when you close the
  rationals under sine and then ask to run that operation backwards.
  That "tower of number systems" is the explicit subject of the companion essay
  **The Extension Ladder**, which develops the same climb — integers → rationals →
  algebraic numbers → transcendentals like π → complex numbers — as a general
  mechanism rather than a single example. Reading the two together, this iteration
  becomes the constructive _witness_ for exactly the rung The Extension Ladder
  describes in the abstract: the step from algebraic numbers to the analytic
  constants that require limits and series to reach.

It is worth being clear about what the project does _not_ claim. It is not a
practical way to compute π — Chudnovsky's series remains the champion by a
polynomial factor, and the essay says so plainly. The value here is
_theoretical_: a clean, precise example of how mathematical machinery grows by
the smallest mutation that existing classifications can't absorb.

---

## A note on intellectual honesty

One thing I want to flag, because it shaped how this material is organized: the
project argues _against itself_ in places. An accompanying Socratic dialogue
presses hard on the question of whether this iteration truly "certifies" anything
or merely "cohabits" with an interesting number, and it lands on a genuinely open
verdict. The essay incorporates that pressure rather than hiding from it. If you
enjoy watching a claim get stress-tested until only the defensible core remains,
you will find that thread running throughout.

---

## Who might find this useful

- **The mathematically curious reader** who likes a short, self-contained result
  with surprising depth — the cubic-cancellation argument fits on a napkin and
  still rewards a second reading.
- **Students of numerical analysis and fixed-point iteration**, for whom the
  three-conditions framing and the quadratic-vs-cubic counter-example are a
  compact, memorable lesson in why convergence order is _engineered_.
- **People interested in the philosophy and structure of the number line** — the
  "tower of continua" and the reading of π as an inverse-of-sine will appeal to
  anyone who thinks about where numbers _come from_.
- **Anyone who wants claims checked.** If you distrust prose that asserts "cubic"
  without evidence, the companion harness exists precisely for you; its report is
  designed to be read top-to-bottom as a verdict sheet.

---

## Where to go next

Start with the essay and read it straight through; the payoff builds. Then, if
you're curious how much of it survives contact with an exact-arithmetic machine,
turn to the companion report and read the verdict table. The four headline
verdicts to look for are: cubic cancellation confirmed, the fixed point is
superattracting (multiplier exactly 0, not repelling), the composite certificate
cost lands in the optimal logarithmic regime, and the outer recurrence is
genuinely non-classical.

It is not the last word on the structure of the continuum — several floors of
that tower are still under construction, and the essay is candid about it. But it
is a clean, precise, and surprisingly deep example of how mathematical mechanisms
grow, one increment at a time. Enjoy.
