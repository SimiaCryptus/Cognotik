# Geometric Entropy Lab

> **A continuous analogue of the Erdős distinct-distance problem** — points
> arranged on a manifold to extremize the entropy of their pairwise distances,
> yielding highly symmetric, self-organizing configurations you can watch
> assemble in real time.

Scatter a handful of points across a shape — a sphere, a torus, a cube, or any mesh you
care to upload — and let them rearrange themselves until the _diversity_ of
their pairwise distances is as large (or as small) as the geometry allows.
The result is a small, meditative instrument for watching order emerge from
randomness.

---

## The Idea, in Plain Terms

There is a classical question from the mathematician Paul Erdős: if you
scatter $n$ points on a flat plane, how many _distinct_ distances between
pairs must you unavoidably create? It's a counting problem with a discrete,
combinatorial flavor — you tally distances and ask for a lower bound.
That same Erdős problem surfaces, wearing very different clothes, elsewhere in
this collection: the **Pentagon Lattice Geometry** work connects it to
golden-ratio arithmetic acting as a "degeneracy engine," and the **No-Three-in-Line
Lab** plays a cousin of it as a live optimization. Here we take the _continuous_
route.

This lab plays the _continuous_ version of the same game. Instead of counting
how many different distances appear, we treat the whole collection of
pairwise distances as a probability distribution — a smeared-out histogram of
"how far apart are things, typically?" — and then we ask the points to
rearrange themselves so that this distribution is as spread-out and diverse
as possible. The mathematical measure of that spread is **Shannon entropy**,
the same quantity information theory uses to describe uncertainty or surprise.

In short: high entropy means the points have found a maximally
_distance-diverse_ arrangement, one where the manifold's geometry is being
used to its fullest. Low entropy means they've huddled together into
something monotonous and clustered. You can dial the target anywhere in
between.

It turns out that maximizing this quantity spreads points apart into elegant,
even packings; minimizing it pulls them into tight knots; and asking for a
specific target value lets you settle the system at a chosen level of
"interesting-ness." Watching that happen — points nudging one another across
the surface of a torus until they lock into a lattice — is genuinely
hypnotic.

---

## What You Actually Do

The interface is deliberately hands-on, and most of the fun comes from
fiddling. A few of the knobs worth knowing about:

- **Pick a geometry.** A sphere is the natural starting point, but there's a
  whole menagerie: spherical shells, cubes, cylinders, cones, saddles
  (those Pringles-chip hyperbolic surfaces), and tori. You can even upload an
  arbitrary 3D model as an STL file and let the points colonize its surface.
- **Choose a goal.** Maximize the entropy, minimize it, match a specific
  target value, or run in a neutral mode where the points simply respect the
  shape without any entropy pressure at all.
- **Set the population.** Anywhere from a couple of points to several
  thousand — small counts reveal crisp symmetry, large counts reveal texture.
- **Add a force, if you like.** An optional interaction lets points repel one
  another (like charged particles) or attract (like gravity), layered on top
  of the entropy objective for extra character. The adventurous can even type
  in their own custom formula.
- **Press Start, and watch.** The points glow from cyan (isolated,
  lonely) to magenta (crowded, clustered), so the emerging structure paints
  itself as it settles. You can drag to rotate, scroll to zoom, and let the
  view auto-spin while a live histogram tracks the distribution underneath.

When you find an arrangement you like, you can copy the coordinates or export
the whole thing as a 3D-printable mesh.

---

## Why It's Interesting

Here is the part that genuinely delighted me, and the reason I think the lab
is worth more than a passing glance.

The maximum achievable entropy for $N$ points is exactly $\ln N$ — a clean,
universal ceiling from information theory, reached when every point sees the
same "crowdedness" as every other. Empirically, the maximizer climbs to this
value on _every_ geometry I've tried, from spheres to saddles to uploaded
meshes. So far, so tidy.

But the _condition_ for reaching that ceiling is wildly underdetermined. The
objective only insists that every point be locally as crowded as its
neighbors; it says nothing about _where_ the points actually sit. On a curved
surface there is typically a vast, continuous family of arrangements — a
whole landscape of them — all achieving the identical maximum entropy. The
problem, in optimization terms, is _sparse_: countless distinct-looking
configurations sit at the same summit.

And this is where it gets charming. Because the optimum is so degenerate, the
_route_ the solver takes decides _which_ summit it lands on. The lab offers
three different optimization strategies, and each one, run on the very same
problem, reliably produces a visibly different arrangement:

- one tends toward noisy, isotropic, lattice-like packings;
- another carves out smoother, curvature-aligned structures;
- the third snaps quickly into crystalline, near-perfect symmetry.

In other words, the final shape is a kind of _fingerprint_ of the method that
produced it. The entropy number itself is uninformative — every optimizer
reports the same $\ln N$ — but the _picture_ gives the game away. You can
identify the optimizer just by looking at the point cloud. It is, I'll freely
admit, a completely useless property; it's also one of the most delightful
small surprises I've stumbled into, and the lab is a convenient place to play
with it.
This is the same "optimizer fingerprinting" effect that animates the sibling
**Constrained Mesh Enclosure Lab** and the **Dihedral Attractors** lab. All
three are built on the same recipe — scatter points, define an energy, flow
downhill — and all three exploit the fact that when many configurations tie for
best, the optimizer's dynamics become the tiebreaker. If you find the effect
intriguing here, the mesh lab shows it in a _constrained_ setting, where an
exact collision wall adds sharp corners that further distinguish the methods.

---

## Who Might Enjoy This

I built it for the joy of it, but a few audiences may find it genuinely
useful:

- **The mathematically curious**, who want an intuitive, visual feel for
  entropy, distance distributions, and the surprising Erdős-flavored geometry
  lurking behind them — no equations required to appreciate the show.
- **Students and educators**, as a hands-on demonstration of optimization,
  symmetry, and the difference between a well-posed and a sparse objective.
- **Artists and designers**, who want even, organic point distributions
  draped over an arbitrary surface — stipple patterns, sampling schemes, or
  simply attractive procedural geometry to export and print.
- **Practitioners of optimization**, for whom the "optimizer fingerprinting"
  behavior is a vivid, tactile illustration of how algorithm dynamics shape
  the solutions they find on a degenerate landscape.

None of it requires installing anything; it runs entirely in a modern
browser. Pick a shape, press Start, and see what falls out.

I'm looking forward to feedback, and I have some more interesting plans for
this tool. More soon, I hope — enjoy!
