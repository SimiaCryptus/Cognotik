# Fitting Point Clouds with Iterated Transforms

Can we approximate an arbitrary cloud of
points — the outline of a leaf, a scatter of stars, the silhouette of a
fern — using nothing more than a handful of geometric transforms applied
over and over again? It turns out that we can get surprisingly far, and
the mathematics behind it is both elegant and genuinely fun to play with.

---

## 1. The Core Idea in Plain Terms

Imagine you have a small set of simple rules — call them **transforms**.
Each rule takes a point in space and moves it somewhere else: it might
shift the point, rotate it, shrink it toward a center, or stretch it
along some direction. Individually, each rule is boring. But if you start
from a single point (say, the origin) and apply these rules in every
possible combination, up to some fixed number of steps, you generate a
whole _constellation_ of points.

That constellation is what we call the **orbit**. The remarkable thing is
that even two or three well-chosen rules, applied a handful of times, can
trace out startlingly intricate patterns — the same principle that lets a
few lines of instruction describe an entire fern in the classic theory of
**iterated function systems (IFS)**.

The project asks the inverse question, which is the more interesting one:

> Given a target shape — a picture made of points — can we _discover_ the
> small set of transforms whose orbit best reproduces it?

In other words, we are not drawing a fractal from known rules; we are
reverse-engineering the rules from a picture. This is the "inverse
problem for IFS," restricted to a manageable, fixed depth.

---

## 2. How It Works, Conceptually

There are three moving parts, and the whole thing loops until it
converges.

1. **The transforms (the unknowns).** We start with a few random
   transforms — a little scaling, a little offset, a bit of noise. These
   are the knobs the system will eventually tune.

2. **The generated shape.** From those transforms we build the orbit: the
   full set of points reachable by combining the rules. This is the
   candidate shape, and at first it looks nothing like the target.

3. **The scorecard.** We compare the generated shape against the target
   point cloud using a _nearest-neighbor_ distance — for each generated
   point, how far is the closest target point, and vice versa? (This
   symmetric measure is known as the **Chamfer distance**.) A low score
   means the two clouds overlap well; a high score means they are far
   apart.

The system then nudges every transform slightly in whatever direction
lowers the score, and repeats. Because each step is a smooth, calculus-
friendly operation, we can compute exactly how to adjust the knobs — the
same _gradient descent_ machinery that trains neural networks. Over many
iterations, the random constellation migrates, warps, and settles until
it hugs the target shape.

### 2.1 The clever bit: commuting transforms and binary powers

There is a practical catch. The number of possible rule-combinations
grows explosively with depth, and naively recomputing each one from
scratch is wasteful. The project leans on two ideas to keep this
tractable:

- **Commutativity.** If the transforms _commute_ (order does not matter —
  true for pure shifts, or for maps that share a common set of axes),
  then a combination is fully described by _how many times_ each rule was
  used, not the sequence. That collapses a huge number of ordered
  sequences down to a much smaller set of counts.

- **Binary powers.** To apply a rule, say, sixteen times, we do not step
  through it sixteen times; we repeatedly _double_ — one, two, four,
  eight, sixteen — reaching the answer in a logarithmic number of steps.
  This is the same trick that makes fast exponentiation efficient, lifted
  into the world of geometric transforms.

Together with a bit of dynamic programming (reusing partial results
across sibling combinations), these ideas turn a brute-force computation
into something practical.

---

## 3. The Interface

The intended way to _use_ this is exploratory and visual. You provide a
target — a set of points describing whatever shape interests you — and
choose two dials:

- **How many transforms** (the size of your rule alphabet), and
- **How deep** to apply them (the number of steps).

These two dials trade richness against cost; more of either yields a
denser, more expressive orbit, but a heavier computation. A gentle
strategy — and one the project recommends — is to **start small and grow**:
begin with a shallow depth for a smooth, forgiving optimization
landscape, then increase it as the fit improves. It is a bit like
sketching loosely before committing to detail.

As optimization runs, you watch the generated constellation drift toward
the target. The satisfying moment is when a cloud that began as random
noise visibly organizes itself into the outline you asked for.

---

## 4. A Little Background

This work sits at the intersection of a few well-established ideas:

- **Iterated Function Systems**, popularized by Michael Barnsley in the
  1980s, showed that fractals like the famous Barnsley fern can be
  encoded in just a few affine rules — a striking form of "fractal
  compression."
- **The inverse problem** — recovering the rules from a target — has long
  been the hard and tantalizing direction; it is generally non-convex and
  riddled with local minima.
- **Modern automatic differentiation**, the engine behind today's deep
  learning, gives us a clean way to push gradients through the entire
  generate-and-compare pipeline, making the inverse problem approachable
  with standard optimization.

What is new here is the deliberate, explicit enumeration of the orbit at
a _fixed depth_, combined with the commutative binary-power shortcut that
keeps the whole thing differentiable and efficient.

---

## 5. Why It Is Interesting

A few reasons this held my attention, and might hold yours:

- **Compression as understanding.** Encoding a complex shape in a few
  transforms is a form of insight — you are finding the _generative
  grammar_ behind an image, not just storing its pixels.
- **A clean playground for optimization.** The problem is small enough to
  reason about by hand, yet non-convex enough to be genuinely
  challenging; it is a lovely testbed for annealing, restarts, and
  curriculum tricks.
- **The bridge between fractal geometry and machine learning.** It is
  satisfying to see two traditions — 1980s fractal theory and 2010s
  gradient-based learning — click together so naturally.
- **It is visual and immediate.** Watching a point cloud self-organize is,
  frankly, just delightful.

I should be candid about the limits: the loss landscape is bumpy, so
results depend on initialization and often need multiple restarts, and
the commutative assumption is an honest modeling choice rather than a
universal truth. Generic transforms do not commute, and forcing the
simplification trades some fidelity for a great deal of efficiency. I see
these as invitations for further exploration rather than dead ends.

---

## 6. Who Might Find This Useful

- **The mathematically curious**, who enjoy fractals, dynamical systems,
  and the surprising reach of a few simple rules.
- **Students and educators** looking for a tangible, visual illustration
  of iterated function systems, inverse problems, or gradient-based
  optimization.
- **Generative artists and designers**, for whom "find the rules that
  draw this shape" is a creative tool in its own right.
- **Researchers in geometry processing or shape representation**, as a
  compact, differentiable model of point-set structure.

You do not need to be a programmer to appreciate the idea; you only need a
bit of curiosity about how compact recipes can conjure complex form.

---

I'm genuinely excited about where this could go — richer transform
families, smarter enumeration, and better ways to escape the bumpier
parts of the landscape all beckon. If any of the above sparks an idea or a
question, I would love to hear it. Enjoy!
