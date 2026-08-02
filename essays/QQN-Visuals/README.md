# Which Direction? → Where on the Curve?

_An interactive tutorial on the Quadratic Quasi-Newton (QQN) optimizer._

Most optimizers pick a direction and hope. This one draws a little curve and
_searches_ it. That single reframing — turning "which direction should I
move?" into "where on this curve should I stop?" — is the idea this tutorial
is built to help you _feel_, not just read about.

---

## What this is

This is an interactive, scroll-driven tutorial — a kind of visual essay — that
teaches how optimization algorithms actually work, from first principles, and
uses the QQN (Quadratic Quasi-Newton) method as a way into the surprisingly
rich world of _line search_. It is deliberately **not** a developer's project
README; there is no build-and-run walkthrough here. Instead, this document
explains what you are looking at, why it is interesting, and who might get
something out of it.

The experience is a single scrolling page. As you read the prose, a pinned
visualization on the side responds: contour maps light up, gradient arrows
appear, a small parabola blossoms out from the current point, and a line
search walks along it probing for a good step. You can drag the start point,
scrub a slider, swap the landscape beneath your feet, and — in the later
sections — literally reshape the algorithm's search curve with your mouse.

---

## A little background

Numerical optimization has long been shaped by a single, stubborn trade-off
between two families of methods:

- **First-order methods** (gradient descent, momentum, Adam) follow the
  downhill slope `-∇f`. They are cheap, memory-light, and _robust_ — the
  negative gradient always points somewhere useful — but they can be
  painfully slow, zig-zagging down long narrow valleys.
- **Second-order / quasi-Newton methods** (Newton, BFGS, L-BFGS) use
  curvature information to take big, well-aimed strides. They are _fast_ near
  a solution but _fragile_: their step can point uphill on the wrong kind of
  landscape.

The classical fix is to pick one direction and then run a line search along
it — accept a big step if it works, backtrack if it doesn't. But that still
commits to a single direction per step.

QQN's insight is to refuse the binary choice. Instead of selecting _either_
the gradient direction _or_ the quasi-Newton direction, it builds a smooth
**quadratic path** that connects them:

```
d(t) = t(1 − t)(−∇f) + t²(−H∇f),   t ∈ [0, 1]
```

This curve has three decisive properties. It _starts_ where you are; it
_begins_ tangent to the gradient (so a small step always goes downhill); and
it _ends_ exactly at the quasi-Newton step. The line search then simply walks
`t` from 0 toward 1 and discovers the right blend on its own — robust when it
needs to be, aggressive when it can afford to be, with no manual tuning.

---

## What the tutorial shows you

The narrative builds up in the same order the ideas historically emerged, so
each concept has somewhere to land before the next arrives:

1. **The landscape** — height is loss; we want to get low. A 3D surface and a
   2D contour of the same function, linked so hovering one lights up the
   other.
2. **What an optimizer _is_** — a loop: look around, pick a direction, pick a
   step, move, repeat.
3. **Gradient descent** — watch a learning-rate knob take you from crawling to
   smooth to diverging.
4. **Momentum & Adam** — a heavy ball rolling through the valley; per-axis
   adaptive steps. (Hold onto Adam — later it reappears not as a rival but as
   a _component_ of QQN.)
5. **L-BFGS** — curvature ellipses, big confident strides, and the moment of
   fragility where the step points the wrong way.
6. **The trade-off** — a side-by-side race that crystallizes _robust-but-slow_
   versus _fast-but-fragile_.
7. **QQN's blend** — the centerpiece: drag the oracle endpoint and watch the
   search curve deform live; scrub `t` and read off `f(x + d(t))`.
8. **The path's three properties** — annotated and verifiable on screen.
9. **Line search** — a one-dimensional slice showing how Armijo, Wolfe, and
   backtracking searches actually accept or reject a step.
10. **Spline search** — reusing the slope at every probe to fit a cubic curve
    and find better steps with fewer evaluations.
11. **The four axes** — a config rack (gradient / oracle / search / region)
    you can rewire and re-run.
12. **Equivalences** — presets named after classical methods, showing that
    many of them are just QQN with one or two knobs fixed.

Throughout, the geometry is trustworthy: gradients and curvature come from
closed-form landscapes, not finite-differenced approximations, so what you see
is what the math actually does. The tutorial also takes pains to reproduce the
_caveats_ honestly — where a heuristic is only a heuristic, or where a strong
condition over-restricts the step — rather than showing only the wins.

---

## Why it's interesting

Optimizers are usually presented as opaque update rules — a line of pseudocode
you take on faith. QQN adds firm **geometric foundations** that happen to be
unusually teachable, and slightly alien to current expectations: the
optimizer's move is a _curve_, not an arrow. Seeing that curve, reshaping it,
and watching a search walk along it turns a stack of equations into something
you can develop physical intuition for.

There is also a pleasing unification lurking here. Once you see optimization
factored into four orthogonal pieces — the gradient anchor, a swappable
_oracle_ that supplies curvature, a _search_ that walks the curve, and an
optional _region_ that enforces constraints — a whole zoo of classical methods
collapses into "points in one configuration space." Gradient descent is the
`t → 0` regime; L-BFGS is the `t = 1` corner; momentum, trust regions, and
OWL-QN are particular oracle or region choices. That reframing is genuinely
clarifying, and it is far more convincing when you can _watch_ one method morph
into another.

---

## Who might find it useful

- **ML practitioners and students** who call `optimizer.step()` every day but
  have never _seen_ what that step does on a loss surface — the primary
  audience, and the reason the tutorial starts from the very idea of a
  landscape.
- **Numerical-optimization newcomers** who are comfortable with calculus but
  have not met the quasi-Newton or line-search literature; this is a gentle,
  intuition-first on-ramp.
- **Experienced optimization people** curious about QQN specifically, who want
  an intuition pump for its four-axis factoring and its equivalences to
  classical methods.
- **Educators** looking for a worked example of "scrollytelling" applied to a
  genuinely mathematical subject.

You do not need to be a developer, and you certainly do not need to read any
source code, to get the payoff. If you have ever wondered what "the optimizer"
is really doing under the hood — or found the robustness-versus-speed tension
intriguing — this is built for you.

---

## A note on the deeper documentation

This tutorial rests on a set of companion technical documents for readers who
want to go further: the comprehensive algorithm reference, the background and
motivation, the oracle abstraction, the projective regions, and the
information-reusing spline search. They are optional. The whole point of the
interactive version is that you can build a working mental model _first_, and
reach for the formal treatment only when your curiosity outruns the visuals.

Enjoy — and I'm looking forward to feedback on where the intuition lands and
where it still needs work.
