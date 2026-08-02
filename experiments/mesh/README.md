# Constrained Mesh Enclosure Lab

> Watch a surface shrink-wrap itself around a shape it is never allowed to touch.

A familiar physical intuition—wrapping a taut skin around an object—as a live optimization
problem you can watch unfold in your browser. A deformable triangulated
mesh flows "downhill" on an energy landscape (it wants to minimize its
surface area, match a target volume, keep its triangles well-shaped, and so
on), while a hard collision-detection wall guarantees it never intrudes into
an inner "keep-out" shape. The result is a small, tangible instrument for
studying how surfaces settle, and—more subtly—how the _choice of optimizer_
quietly decides which of many equally-good answers you end up with.

This document is a tour for the curious reader, not a developer's manual. If
you want the formulas, the derivations, or the file layout, those live in
the companion documents (`idea.md`, `math_outline.md`,
`novelty_utility.md`, and `use_cases.md`).

---

## 1. The Idea in Plain Terms

Imagine a fixed inner object—call it the _keep-out volume_, `K`. Now imagine
a stretchy, closed surface draped loosely around it. We give that surface a
set of _preferences_: maybe it wants to be as small as possible (a tight
shrink-wrap), maybe it wants to enclose a specific amount of space, maybe it
wants its facets to be uniform and its triangles to be pretty. These
preferences are encoded as an **energy**, and the surface continually nudges
itself to lower that energy.

The twist is that the surface is under a **hard rule**, not a gentle
suggestion: it may _never_ penetrate the inner object, and (approximately)
it may never pass through itself. Rather than adding a "penalty" that merely
discourages intrusion, the lab uses _continuous collision detection_
(CCD)—the same family of techniques used to keep cloth and rigid bodies from
tunneling through each other in physics simulations—to catch the exact
moment any part of the surface _would_ cross the boundary, and clips the
motion right there. It is a wall, not a spring.

So the surface behaves like a shrink-wrap that flows freely in open space
but _slides_ along the forbidden geometry when it bumps into it, creeping
around obstacles rather than stalling against them.

## 2. Why This Is Interesting

Here is the part that turns a graphics demo into a small research
instrument. Several of the surface's "preferences"—edge-length uniformity in
particular—have a curious property: **many different surfaces satisfy them
equally well.** When every edge contributes the same amount to the
length distribution, the relevant energy term saturates; a whole _family_ of
tessellations ties for first place. There is no single winner.

So what actually breaks the tie? It turns out that the **optimizer**
does—the numerical method used to walk downhill. Give the lab the identical
starting mesh and the identical energy, then switch from Adam to L-BFGS to
QQN, and you will watch it converge on _visibly different_ final surfaces
that nonetheless score identically. I find this "fingerprinting" effect
genuinely fun to watch: the optimizer's personality is written into the
geometry it leaves behind.

That phenomenon is the heart of the lab. It is a clean, reproducible way to
_see_ something usually invisible—that the path an optimizer takes, and the
way it negotiates the sharp corners of a constrained feasible region
(where several collisions happen at once), materially shapes the outcome.
This "optimizer fingerprinting" effect is not unique to the mesh; it recurs
throughout the sibling _geometric attractor_ experiments — the **Geometric
Entropy Lab**, the **Dihedral Attractors** lab, and the **No-Three-in-Line
Lab** all exhibit the same phenomenon in different guises. Wherever an energy
has a large family of tied optima, the numerical method quietly becomes the
selector. The mesh lab is simply the place where the tie-breaking is easiest
to _watch_ in three dimensions.

A second, quieter reason it is interesting: because the non-penetration rule
is exact rather than a tunable penalty, you can adjust the surface's
preferences _freely_ without also having to balance them against a barrier
penalty stiffness. The soft goals and the hard constraints stay cleanly separated,
which makes the lab a good bench for isolating optimizer behavior from the
usual penalty-tuning artifacts.
The three optimizers on offer — Adam, L-BFGS, and QQN — are the same trio
that appears across these experiments. QQN in particular (the
Quadratic-Quasi-Newton method) is documented at length in its own writeup;
its defining trait is a curved search path that begins tangent to the safe
gradient direction before bending toward the bolder quasi-Newton stride,
which gives it a characteristic personality when it meets the sharp corners
of a constrained region.

## 3. A Little Background

None of the individual ingredients here are new, and I want to be honest
about that. Continuous collision detection comes from cloth and rigid-body
simulation. The surface energies (area, volume, Laplacian smoothing,
triangle-quality terms) are textbook geometry processing. Trust-region
descent—taking cautious steps where the landscape is stiff and bold strides
where it is calm—is classical numerical optimization. Even the entropy-based
"many optima tie" flavor is borrowed from a sibling **Geometric Entropy
Lab**.

What is new is the _combination_: using an exact collision wall to constrain
an **optimizer** (not a physics simulation), and then treating the
optimizer's identity as the mechanism that selects among tied solutions.
One specific, defensible detail matters here—when a step gets truncated or
projected by a collision, the stateful optimizers (L-BFGS, QQN) have their
accumulated "memory" of the landscape's curvature **reset** for the affected
vertices, because curvature estimates measured across a discontinuous
correction are simply garbage. That reset discipline is the same lesson,
transported into a collision setting, that shows up elsewhere in this
collection of experiments.

## 4. Using the Lab

The lab is a single web page. You press **Run** and watch, or **Step**
through one iteration at a time, or **Reset** to rebuild the initial shapes.
The controls let you conduct your own little experiments:

- **Optimizer** — switch between Adam, L-BFGS, and QQN. This is the dial to
  turn if you want to see the fingerprinting effect for yourself.
- **Energy weights** — a set of sliders (the "λ" values) that set how much
  the surface cares about area, volume, fidelity to landmark points,
  edge-length uniformity, triangle quality, and smoothness. There is also a
  **Target Volume** to aim for when the volume preference is active.
- **δ_safe** — how much clearance to keep from the inner keep-out shape.
- **Self-collision** — toggle an approximate check that keeps the surface
  from passing through itself.
- **Reset optimizer on truncation** — the curvature-reset discipline
  described above (recommended on).
- **Retriangulate** — a conservative pass that improves the mesh's
  connectivity, applied only well away from the forbidden geometry.

In the viewport you can **drag** to orbit around the scene and use the
**mouse wheel** to zoom in and out for a closer look at how the surface
meets the constraint.

A panel of live **metrics** reports what the surface is doing moment to
moment: its surface area, enclosed volume, how uniform its edges have
become, its triangle-quality and smoothness energies, the smallest gap to
the keep-out shape (which should never drop below your clearance setting),
how many active contacts are in play, and how many steps have been taken or
rejected.

## 5. Who Might Find This Useful

- **The curious and the visual learners.** If you have ever wanted to _see_
  the difference between a "hard wall" constraint and a "soft penalty," or
  to understand why an optimizer that remembers curvature can get confused
  when it slams into a boundary, this lab makes those abstractions concrete
  and three-dimensional.
- **Educators.** It is a compact teaching vehicle for several ideas at once:
  projection versus penalty constraint handling, why stateful optimizers
  need resets across discontinuities, how continuous collision detection
  prevents tunneling, and the intuition behind degenerate optima and
  optimizer fingerprinting—all with a tangible artifact on screen.
- **Researchers and practitioners in optimization and geometry.** As a
  controlled instrument, it isolates constrained-descent dynamics from
  penalty-tuning noise, and it surfaces (deliberately) the stiff
  multi-contact situations where naive descent plus naive collision response
  tend to fail.

A candid caveat, in the interest of setting expectations: this is a research
and teaching instrument, not a production geometry tool. Dedicated
shrink-wrap and remeshing pipelines will produce cleaner results faster for
real-world models, and the hand-rolled collision code is tuned for
tractability on modest meshes rather than raw scale. What it offers instead
is _clarity_—a place to watch constrained optimization think out loud.

## 6. Where to Go Next

If this piqued your interest, the companion documents go deeper: `idea.md`
lays out the full concept, `math_outline.md` collects the formulas and the
open analysis questions, and `novelty_utility.md` and `use_cases.md` place
the lab in context and sketch the scenarios—uniform tessellations, faceted
panels, volume-matched enclosures, minimal-area wraps—where each combination
of preferences becomes interesting.

I'm looking forward to seeing what people notice when they start switching
optimizers and watching the surfaces diverge. Enjoy!

## License

Part of the experiments collection. See the repository root for license
details.
