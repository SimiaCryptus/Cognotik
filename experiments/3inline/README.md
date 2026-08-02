# No-Three-in-Line Lab

An interactive, browser-based playground for one
of my favorite deceptively-simple puzzles in geometry — the **no-three-in-line
problem** — rather than solve it the usual combinatorial way, it watches
points drift, jostle, and settle across a grid in real time, driven by a kind
of physics you can reshape with a handful of sliders.

## The Puzzle in One Breath

Picture a square grid of dots, like a checkerboard's intersections. The
challenge: place as many points as you can so that **no three of them ever
line up straight**. Not just the obvious rows, columns, and diagonals — _any_
line at all, however oddly angled, is forbidden the moment a third point falls
on it.

It sounds easy until you try it. Two points always define a line, and every
new point you add threatens to become the third on some line you hadn't even
noticed. The constraints reach across the whole board, coupling distant
corners in ways that make the puzzle genuinely hard.

## A Little Background

Mathematicians have studied this since the early twentieth century, and much
of it remains open. For an n×n grid you can never do better than **2n**
points (each column can hold at most two), and that ceiling is actually
reachable for grids up to around 46 on a side, with scattered results beyond.
For large grids, though, nobody knows the true answer — and there's a
long-standing conjecture that you eventually _can't_ quite reach 2n, with the
achievable density settling near 1.87·n instead. In short: a puzzle simple
enough to explain to a child, yet stubborn enough to resist a clean solution.

## The Idea: Let the Points Move

Here is the twist that makes this project fun to watch. Instead of testing
discrete arrangements one by one, the Lab **lets the points float freely** and
gives them something like a personality:

- Points feel a gentle pull toward the grid's integer positions, as if the
  lattice were slightly magnetic.
- Whenever three points threaten to become collinear, a **repulsive force**
  builds — mild when they're merely close, and rising sharply as they line up
  exactly. (The math measures the angle each triangle of points makes; a
  flattening triangle means a near-collinearity, and the penalty climbs.)
- An optional spacing force keeps points from piling on top of one another.

Add these up and you get an **energy landscape** — a hilly terrain where the
valleys correspond to good, valid configurations. The optimizer simply rolls
the whole arrangement downhill, and you watch it happen. It's a physical
intuition standing in for a combinatorial search; the points "feel" the
frustration of an over-crowded line before it ever becomes a hard violation.

There's also a **3D mode**, where the grid becomes a cube and the same rules
play out in three dimensions — drag to orbit the camera, scroll to zoom.

## What You'll See On Screen

The canvas shows the live arrangement as it evolves. In 2D, the tracked lines
are tinted by how crowded they are:

- 🔵 **blue** — under-populated, still inviting another point;
- 🟢 **green** — just right, holding exactly two;
- 🔴 **red** — over-crowded, a brewing violation.

A small panel of metrics tracks the running energy, the current point count
that survives a strict validity check, and the **best valid** configuration
found so far this run.

You steer the process with sliders that reshape the landscape as it runs:
how strongly the grid pulls, how aggressively near-collinearity is punished,
how much points repel, and how much random "entropic" jitter is injected to
shake the arrangement out of shallow dead-ends. You can also **drag points**
yourself to nudge the search — it politely pauses while you do — and hit
**Restart** to reroll the random starting positions, since the landscape is
bumpy and every run tells a slightly different story.

## Why It's Interesting

A few reasons I keep coming back to it:

1. **It makes an abstract constraint tangible.** Collinearity is an
   all-or-nothing algebraic fact, yet here it becomes a smooth, visible force
   you can feel through the animation.
2. **It's a case study in continuous relaxation** — turning a discrete,
   combinatorial problem into a continuous one that gradient methods can
   explore. That trick shows up all over modern optimization and machine
   learning, and this puzzle is a wonderfully visual place to see it work.
3. **The landscape is honestly frustrating**, in the technical sense: full of
   local minima, long-range coupling, and near-solutions that aren't quite
   valid. Watching it struggle and occasionally break through is oddly
   compelling.
   This puzzle sits alongside a family of related "geometric attractor" labs that
   share the same recipe — scatter points, define an energy, flow downhill. The
   **Geometric Entropy Lab** plays the _continuous_ analogue of Erdős's
   distinct-distance problem; the **Dihedral Attractors** lab climbs to
   curvature-defined energies; and the **Constrained Mesh Enclosure Lab** adds an
   exact collision wall. If you enjoy the physics-style framing here, those are
   natural next stops.

I'll be candid: this is an exploratory playground, not a record-setting
solver — the continuous approach usually lands near, not at, the known optima,
and that gap is itself part of what makes it interesting to poke at.

## Who Might Enjoy This

- **The mathematically curious**, who want to _see_ a famous open problem
  breathe rather than read about it.
- **Students and educators** looking for an intuitive, hands-on illustration
  of optimization, energy landscapes, and how continuous methods attack
  discrete problems.
- **Puzzle and recreational-math enthusiasts**, who can simply play — dragging
  points, tuning forces, and racing their own best score.
- **Anyone interested in how "physics-style" thinking** (potential wells,
  forces, annealing) can be borrowed to tackle problems that look purely
  combinatorial.

No installation, no setup — just open it in a modern browser, press **Play**,
and watch the points negotiate their way toward a solution.

Enjoy, and I'd love to hear what configurations you discover!
