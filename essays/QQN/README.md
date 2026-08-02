# QQN Optimizer: A Smoother Path to the Bottom of the Hill

📄 **[Read the Academic Paper (PDF)](paper.pdf)** — the complete mathematical foundation and theoretical
analysis, for those who want the full derivation.

http://dx.doi.org/10.13140/RG.2.2.15200.19206
---

## 🚀 Implementations

QQN is available in three languages/frameworks — pick whichever fits your stack:

- 🦀 **[qqn-optimizer](https://github.com/SimiaCryptus/qqn-optimizer)** — the original Rust
  implementation and reference for the paper.
- 🐍 **[qqn-jax](https://github.com/SimiaCryptus/qqn-jax)** — JAX / Optax (Python) port.
- 🔥 **[qqn-torch](https://github.com/SimiaCryptus/qqn-torch)** — PyTorch port.

---

## The Short Version

Optimization is the quiet engine underneath most of modern computing; it is how we teach machines to
fit curves to data, how we train neural networks, and how we find the "best" answer among a vast space
of possibilities. This project introduces **QQN** — the Quadratic-Quasi-Newton method — a new way of
steering that search, along with a rigorous benchmarking framework built to test whether the idea
actually holds up under scrutiny. (Spoiler: it turns out that it holds up rather well, but I'll get to
the honest caveats in a moment.)

This document is written for the curious reader rather than the developer. If you want to know _what_
QQN is, _why_ it might matter, and _who_ could find it useful — without wading through function
signatures — you're in the right place.

---

## A Little Background

Imagine you're standing somewhere on a foggy hillside, and your goal is to reach the lowest point in
the valley. You can't see the whole landscape; you can only feel the slope beneath your feet. This is,
in essence, what a numerical optimizer does. The "landscape" is a mathematical function — the error of
a model, the cost of a design — and the "lowest point" is the answer we're hunting for.

Over the decades, two broad strategies have emerged for how to take each step:

1. **Gradient descent** — the cautious hiker. Simply walk downhill in the direction of steepest
   descent. It is reliable and almost always makes progress, but it can be painfully slow, especially
   in long, narrow valleys where it zig-zags endlessly.
2. **Quasi-Newton methods (like L-BFGS)** — the ambitious hiker. These build up a mental model of the
   _curvature_ of the landscape and take clever, long strides toward where they _believe_ the bottom
   lies. When the model is good, they're wonderfully fast; when the model is misleading, they can
   stride confidently in entirely the wrong direction.

For years, the practical question has been an awkward either/or: do you take the safe step or the bold
one? Existing hybrid approaches typically _choose_ between the two directions, or solve an expensive
extra sub-problem to blend them.

## The Idea Behind QQN

QQN sidesteps the either/or entirely. Instead of picking a direction, it draws a smooth, curved path
that begins by heading in the safe (gradient) direction and gradually bends toward the bold
(quasi-Newton) direction. Then it simply searches _along that curve_ for the best place to stop.

The path itself is beautifully compact:

```
d(t) = t(1 - t)(-∇f) + t² d_LBFGS
```

You don't need to parse the algebra to appreciate the intuition. Think of it as a road that leaves your
current position _tangent to the safe downhill direction_ — so the very first thing it does is guarantee
you're going downhill — and then curves toward the ambitious L-BFGS destination as you travel further
along it. If the ambitious direction turns out to be nonsense, the curve gracefully keeps you near the
safe path; if it's excellent, the curve carries you swiftly toward it.

Three properties make this appealing:

- **Guaranteed descent.** Because the path starts tangent to the steepest-descent direction, that first
  infinitesimal step is _always_ downhill, no matter how badly the curvature model misbehaves.
- **No new knobs to tune.** QQN reuses the parameters already present in L-BFGS and the line search; it
  introduces no additional hyperparameters for a practitioner to fuss over.
- **Graceful degradation.** When the second-order information is unreliable, QQN quietly falls back
  toward plain gradient descent instead of failing outright.

In short: it's a method that tries to be bold when boldness is warranted and cautious when it isn't —
and it makes that decision automatically, through the geometry of the path rather than through a pile of
tuning parameters.

---

## The Benchmarking Framework — and Why It Matters

A new optimization idea is easy to _propose_ and hard to _trust_. New methods have a long history of
looking spectacular on the three problems their authors happened to try, then quietly disappointing
everyone else. So a substantial part of this project — arguably the more laborious part — is the
evaluation harness built to hold QQN honest.

The framework runs a genuine tournament:

- **62 benchmark problems**, spanning smooth convex bowls, twisting non-convex valleys (the notorious
  Rosenbrock among them), viciously multimodal landscapes riddled with false minima, and real machine
  learning tasks like regression and small neural networks.
- **25 optimizer variants**, including several flavors of QQN, L-BFGS, Trust Region methods, gradient
  descent, and Adam — so QQN is measured against strong, established competition rather than strawmen.
- **Statistical rigor** — 50 runs per problem-optimizer pairing, Welch's t-test for comparing means,
  Cohen's _d_ for effect sizes, and Bonferroni correction for the multiple-comparison problem. The aim
  is fair comparison, not a flattering highlight reel.
- **Reproducibility** — fixed random seeds and deterministic algorithms, so the numbers can be checked
  rather than merely believed.

The results are compiled automatically into readable reports (Markdown, LaTeX, CSV, and HTML), complete
with convergence plots, performance profiles, and win/loss/tie matrices.

### What the Numbers Say

Across more than 31,000 optimization runs, QQN variants won 36 of the 62 problems — a bit under 60%.
They were especially strong on the smooth and moderately difficult problems, and remained competitive
almost everywhere. In the interest of intellectual honesty (and this really is a strength worth owning),
the picture is not one of universal dominance: Adam-style methods held their own on the neural-network
and highly multimodal problems, and classic L-BFGS remained excellent on several convex and
support-vector-machine tasks. Different tools still suit different terrain.

The headline, then, is not "QQN beats everything." It's something more useful: **QQN is broadly robust,
rarely embarrassed, and competitive across a strikingly wide range of problem types — while adding no
tuning burden.** That combination of reliability and simplicity is, I'd argue, exactly what a
general-purpose optimizer should aspire to.

---

## What You'd Actually See and Do

While QQN is at its heart a mathematical method, the project is organized around a simple experience:
you point it at a problem (or a whole suite of them), let it run its tournament, and read the generated
reports. The convergence plots let you _watch_ the optimizers descend their respective hillsides — some
plunging quickly, some zig-zagging, some stalling on a false floor. The comparison tables tell you, with
statistical backing, which method reached the bottom, how quickly, and how often. It's meant to be as
much an instrument for _understanding_ optimizer behavior as a tool for running it.

---

## Why It's Interesting

A few reasons this work is worth a look, even if you never run a line of it yourself:

- **It's a genuinely elegant idea.** Replacing an either/or decision with a smooth interpolating curve
  is the kind of small conceptual move that feels obvious only in hindsight.
- **It resists the usual failure mode of new methods.** The guaranteed-descent property means QQN can't
  catastrophically march off a cliff the way an over-eager quasi-Newton step sometimes can.
- **It's tested like a hypothesis, not sold like a product.** The benchmarking framework is built to
  _risk_ disproving the method, which is what makes the positive results credible.
- **It costs the user nothing extra to try.** No new hyperparameters means adopting QQN doesn't drag in
  a fresh tuning headache.

## Who Might Find It Useful

- **Researchers in numerical optimization** who want a well-documented new method, a reproducible
  baseline, and an honest comparison harness to build on.
- **Machine learning practitioners** curious about training-time behavior beyond the usual Adam-versus-
  SGD conversation, especially on smaller or well-structured problems.
- **Engineers and scientists** who solve fitting or design-optimization problems and would value a
  robust, low-fuss solver that rarely needs babysitting.
- **Educators and students** who want a concrete, visual, statistically grounded playground for seeing
  how different optimizers actually behave on different landscapes.
- **The simply curious** who enjoy watching a clever geometric idea earn its keep against tough
  competition.
  QQN also appears "in the wild" throughout the sibling _geometric attractor_ experiments — the
  **Constrained Mesh Enclosure Lab**, the **Geometric Entropy Lab**, and the **Dihedral Attractors**
  lab all offer QQN alongside Adam and L-BFGS. Those labs put QQN's personality on visible display:
  because each has a deliberately _degenerate_ energy (many configurations tie for best), the
  optimizer's dynamics select which solution you land in, and QQN's curved, guaranteed-descent path
  leaves a distinctive geometric fingerprint. If the tournament results here interest you, those labs
  let you _watch_ the same method negotiate constrained and multi-optimum landscapes in real time.

---

## Learn More

For the complete mathematical derivation, the convergence analysis, and the full experimental write-up,
see the accompanying paper:

📄 **[Download the Full Paper (PDF)](paper.pdf)**

**"Quadratic-Quasi-Newton Optimization: Combining Gradient and Quasi-Newton Directions Through
Quadratic Interpolation"**

### Source Code & Ports

- 🦀 **[qqn-optimizer](https://github.com/SimiaCryptus/qqn-optimizer)** — original Rust paper &
  reference implementation
- 🐍 **[qqn-jax](https://github.com/SimiaCryptus/qqn-jax)** — JAX / Optax (Python) port
- 🔥 **[qqn-torch](https://github.com/SimiaCryptus/qqn-torch)** — PyTorch port

---

_A closing note, in the spirit of honesty: this is research software and a research method. The results
here are encouraging and, I think, genuinely interesting — but you should validate them against your own
problems before trusting them with anything important. If you do try it, I'd love to hear how it goes.
More soon, I hope._
