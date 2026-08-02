# Dihedral Attractors: Sculpting Geometry by Optimizing the Angles Between Faces

This particular lab reaches for the richest geometric quantity yet — the **dihedral angle**, the fold between two adjacent faces of a mesh.

## The Big Idea

Imagine you have a handful of points floating in space. Now imagine you can
define a single number — call it an "energy" — that measures something about
how those points are arranged. If you let the points slowly drift in
whatever direction lowers that energy, they will eventually settle into a
special configuration: a low point, an _attractor_. The fascinating part is
that these attractors are frequently the most symmetric, orderly shapes the
space allows — regular polygons, spheres of evenly spaced points, and, in
this lab, folded surfaces reminiscent of the Platonic solids.

What makes each experiment in this collection different is _which geometric
ingredient_ the energy is built from. There's a natural ladder here, each
rung coupling more points together and capturing richer structure:

1. **Point pairs** — the simplest case, where every pair of points pushes or
   pulls on the others by distance alone (think magnets or springs).
2. **The distribution of distances** — instead of individual forces, you
   treat the whole collection of pairwise distances as a statistical
   distribution and reward _diversity_ among them.
3. **Triangles** — move from pairs to triplets, scoring the shape of each
   triangle by its interior angles.
4. **Dihedral angles** — the newest rung, and the subject of this lab.
   Rungs one and two are the province of the **Geometric Entropy Lab** (which
   maximizes the diversity of the _distance distribution_); the **No-Three-in-Line
   Lab** is a close relative that uses a triangle-angle penalty to forbid
   collinearity. This lab climbs to the top rung, dihedral angles, but shares the
   same solver, the same manifold constraints, and the same underlying
   philosophy with all of them.

## What's New Here: The Fold Between Faces

A dihedral angle is the angle you'd measure at the crease where two flat
surfaces meet — like the fold in a piece of paper, or the seam between two
panels of a soccer ball. To use it, this lab first connects the points into a
**triangulation** (a mesh of triangles), and then, for every internal edge
where two triangles share a border, it measures the fold angle between them.

Why bother reaching all the way up to dihedrals? Because, as it turns out,
these fold angles are the language geometry uses to talk about **curvature**.
A flat sheet has no folds; a sharply creased or curved surface has many.
Optimizing over dihedral angles therefore lets us aim directly at
curvature-defined targets:

- Rewarding _flatness_ tends to flatten the mesh into developable,
  paper-like surfaces.
- Rewarding _equal folds everywhere_ drives the points toward the regular
  and semi-regular polyhedra — the Platonic and Archimedean solids are
  precisely the shapes whose folds are all identical.
- Rewarding _fold diversity_ (maximizing the variety of angles) produces
  maximally irregular-yet-balanced crinkled structures.

That last variant is a nice closing of the loop: it's the same "maximize
diversity" idea from rung #2, but applied to folds instead of distances.

## The Interface

The lab is meant to be played with, not just read about. In broad strokes,
the experience works like this:

- **Choose your points and your space.** You decide how many points to start
  with, and whether they roam freely or are pinned to a surface — a sphere, a
  torus, a cube, a saddle, or even a custom shape.
- **Pick what to reward.** A menu of dihedral "functionals" lets you select
  the goal: flatness, equal folds, crease formation, minimal bending, or
  maximal fold diversity. Each one steers the system toward a different
  family of attractors.
- **Watch it flow.** Press go and the points begin their descent, the mesh
  re-knitting itself periodically as the points migrate. You watch order
  emerge in real time as the shape settles into its attractor.

Under the hood everything is differentiable, which is a technical way of
saying the system always knows _which way is downhill_ — and it shares its
solver machinery, its manifold constraints, and its angle-based philosophy
with the sibling labs, so results feel of a piece with the rest of the
collection.

## A Little Background

This sits at the intersection of a few well-worn ideas. The
"spread points evenly on a sphere" problem is a classic (the Thomson
problem, for the physically inclined); the notion of maximizing distinct
distances traces back to Erdős; and the study of fold angles as carriers of
curvature is the heart of _discrete differential geometry_, the field that
lets computers do calculus on meshes. What this program contributes is a
unifying frame — one recipe, four rungs — and, on the top rung, the
still-underexplored move of optimizing directly over dihedral angles.

## Why It's Interesting

There's a genuine sense of surprise in watching high symmetry appear from a
simple rule and a random start; you're essentially discovering the "natural
resting shapes" a space wants to hold. It's also a compact illustration of a
profound principle — that much of the order we see in nature, from crystals
to soap films to viral capsids, arises from exactly this kind of energy
minimization. Turning an abstract mathematical idea into something you can
nudge and watch unfold makes that principle tangible in a way equations
alone rarely do.
As with the sibling **Geometric Entropy** and **Constrained Mesh** labs, the
"equal folds everywhere" objective is deliberately degenerate — many
tessellations satisfy it equally well — so the choice of optimizer again
leaves its fingerprint on which attractor you land in. Switching between Adam,
L-BFGS, and QQN is the quickest way to see that effect on curvature-defined
targets.

## Who Might Find It Useful

- **The mathematically curious**, who enjoy watching order emerge from
  simple rules and want an intuition for optimization and symmetry.
- **Students and educators** looking for a hands-on way to make curvature,
  triangulations, and gradient descent concrete rather than abstract.
- **Artists and designers** hunting for algorithmically generated forms —
  the folded, faceted attractors have a real aesthetic appeal.
- **Researchers in geometry and machine learning**, for whom this is a small
  but honest sandbox for experimenting with mesh-based energies.

I'll be candid: this is an exploratory lab, and not every functional
produces a clean, recognizable attractor — some settle into messy local
minima, and the interplay between the moving points and the
re-computed mesh can be finicky. But that unpredictability is part of the
charm; there's always another combination to try. I'm looking forward to
seeing what shapes people coax out of it. Enjoy!
