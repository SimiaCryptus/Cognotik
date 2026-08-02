# Optimization Mechanics Visualizer

A browser-based playground that lets you drop a handful of
the field's most influential optimizers onto the same tricky landscape and
watch, step by step, how each one finds its way downhill. It runs entirely in
your browser; there's nothing to install, and nothing is sent anywhere.

This is a tool for building intuition. If you've ever read that "Adam adapts
its learning rate per-parameter" or that "L-BFGS approximates the inverse
Hessian" and nodded along without quite _seeing_ what that means, this is meant
for you.

---

## The Big Idea

Most of modern machine learning — and a great deal of engineering and science
besides — comes down to one deceptively simple question: given some measure of
"how wrong am I," how do I change my parameters to be less wrong? That measure
is called a **loss**, and the process of minimizing it is **optimization**.

You can picture the loss as a landscape. Every possible setting of your
parameters is a location on a map, and the loss is the _altitude_ at that
location. Training a model, then, is a hike downhill toward the lowest valley
you can find. The optimizer is your hiking strategy.

The trouble is that real loss landscapes are not gentle, tidy bowls. They are
rugged; they have false valleys, misleading slopes, and noisy, confounding
terrain that can send a naive hiker wandering in circles. Different optimizers
cope with this ruggedness in genuinely different ways — and those differences
are exactly what this project makes visible.

---

## What You're Looking At

The screen is dominated by a **2D map of the loss landscape**, drawn as a
heatmap (or contour lines, or hidden entirely if you'd rather judge the
optimizers "blind"). Dark and light regions correspond to low and high loss;
think of it as a topographic map viewed from directly overhead.

On top of that map, the visualizer plots the **path** an optimizer takes as it
steps, one iteration at a time. Older points fade into transparency, so you get
a natural sense of history and momentum — where the algorithm has been, and how
decisively it's moving. At each point, a small **arrow** shows the local
gradient: the direction of steepest ascent, which is the fundamental piece of
information nearly every optimizer relies on.

Two **statistics bars** run along the left and bottom edges of the map. These
are projections of the optimizer's "state of mind" onto the vertical and
horizontal axes: the current gradient, a short history of recent gradients, and
the running accumulations of gradient and squared-gradient that algorithms like
Adam quietly maintain under the hood. Watching these bars fill and drain is,
I think, one of the more illuminating ways to understand _why_ an adaptive
optimizer behaves the way it does.

Finally, the map can **pan infinitely**. Rather than confining the action to a
fixed window, the view follows the active optimizer wherever it goes, so a path
can wander as far as it likes without ever running off the edge.

---

## The Landscape Is Configurable

Half the fun is building your own terrain. The loss surface is assembled from
composable layers, each of which you can toggle and tune:

- **A base field** — either a simple global slope or a quadratic "bowl" with an
  obvious minimum.
- **High-frequency noise** — smooth, rolling bumps that add texture and make
  the landscape harder to read.
- **A confounding lattice** — a grid of regions that quietly push in a
  _different_ direction than the true descent, laid down to mimic the sort of
  misleading local structure that trips up real optimizers.

Crucially, everything is built to stay smooth (differentiable, if you like the
jargon), because abrupt cliffs would break the more sophisticated methods in
ways that would tell us nothing interesting.

---

## The Contestants

Four optimizers, each driven by the same underlying loop so the comparison is
fair:

1. **Gradient Descent (GD)** — the honest baseline. Take a step downhill,
   repeat. Simple, and instructive precisely because of its limitations.
2. **Adam** — the workhorse of modern deep learning. It keeps running averages
   of the gradient and its square, letting it adapt its stride to the terrain.
3. **L-BFGS** — a quasi-Newton method that builds up a picture of the
   landscape's _curvature_ from its recent history, often taking dramatically
   smarter steps as a result.
4. **QQN** — a quadratic-path method that blends the steepest-descent direction
   with the L-BFGS direction along a curved path, then performs a line search
   to pick the best point along it.

What makes the visualizer more than a race is that it surfaces the _internal
mechanics_. Step through QQN, for instance, and you can watch it propose an
"oracle" point, sketch the quadratic path it's considering, animate the
line-search probes it evaluates, and finally commit to a step. These are the
moving parts that papers describe in equations and that you rarely get to
simply _watch_.

---

## Why It's Interesting

Optimization is one of those topics where the mathematics is well-documented
but the _intuition_ is hard-won. It turns out that seeing an algorithm hesitate
at a saddle point, or watching Adam's accumulated statistics slowly steer it out
of a noisy region, teaches something that a page of update rules struggles to
convey. A few things I find genuinely fun to observe:

- How plain gradient descent gets _stuck_ or oscillates where curvature-aware
  methods glide through.
- How the confounding lattice can seduce a shortsighted optimizer into taking a
  scenic detour.
- How the statistics bars reveal that "momentum" and "adaptive learning rates"
  aren't magic — they're just carefully maintained running sums.

There's also a pleasingly experimental quality to it: crank up the noise, shrink
the bowl, flip on the lattice, and see which methods stay robust. It's a small
laboratory for developing taste about _when_ a given optimizer is the right
tool.

---

## Who Might Find This Useful

- **Students** learning optimization, numerical methods, or machine learning,
  who want to connect the formulas to behavior they can actually see.
- **Educators** looking for a live demonstration to anchor a lecture — the
  step-by-step controls and QQN micro-steps are built with teaching in mind.
- **Practitioners** who use these optimizers daily and want a sharper feel for
  their failure modes and personalities.
- **The simply curious** — anyone who enjoys watching an abstract process
  unfold as motion on a map.

No advanced background is required. If you understand the idea of walking
downhill on a landscape, you have everything you need to start exploring.

---

## A Note on Scope

This is deliberately a _conceptual_ instrument, not a training framework. The
landscapes are synthetic and analytic rather than the loss surfaces of real
neural networks, and everything is strictly 2D and top-down — no 3D flythroughs,
no real-model training. That constraint is a feature: by stripping the problem
down to two dimensions, the mechanics that are ordinarily buried in
million-parameter opacity become something you can point at.

I'm looking forward to seeing what patterns people notice, and there's plenty I
still want to add — a side-by-side "compare all" mode, more optimizers, and
difficulty presets among them. More soon, I hope. Enjoy!
