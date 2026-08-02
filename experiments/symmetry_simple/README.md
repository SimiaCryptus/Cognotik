# Symmetry Diffusion

**Symmetry Diffusion**, a browser-based
canvas where you paint with symmetry and then watch the colours slowly diffuse across the
grid, swirling into kaleidoscopic patterns that never quite settle.

## What It Is

Picture a small square of graph paper — anywhere from 32 to 256 cells on a side — where
every cell is a coloured dot. That grid is the whole world. You paint on it with a brush,
and two things happen that ordinary paint programs don't do:

1. **Everything you draw is mirrored, rotated, and repeated** according to whichever
   symmetries you've switched on, so a single dab of colour can bloom into a snowflake, a
   mandala, or a seamless tile.
2. **The colours slowly bleed into their neighbours**, the way heat spreads through metal
   or a drop of ink disperses in water — except that here the symmetry axes act as extra
   shortcuts, so colour flows along the pattern's lines of reflection as well as across
   physical space.

The result is a living pattern: you set up the initial conditions with a few strokes, press
**Play**, and watch a self-organising kaleidoscope evolve in real time.

## A Little Background

Two very old ideas meet in this project.

The first is **symmetry**. When mathematicians talk about the symmetry of a shape, they
mean the set of operations — reflections, rotations, translations — that leave it looking
unchanged. These operations form what's called a _group_, and groups are the language
behind everything from snowflakes and wallpaper patterns to the structure of crystals and
the fundamental particles of physics. In this toy, each symmetry switch adds one of those
operations to the mix; turning several on at once composes them into richer groups, exactly
as nature does when it grows a six-fold snowflake or a tiled honeycomb.

The second is **diffusion**, the physics of things spreading out. The same simple rule —
every point drifts a little toward the average of its neighbours — describes heat moving
through a rod, smells wafting across a room, and pixels blurring in an image. Left to run
forever, plain diffusion is relentlessly boring: it smooths everything into a uniform grey.
The interesting behaviour lives in the transient, the slow journey from structure to
sameness.

## Why It's Interesting

Here is the part I find genuinely delightful. On its own, diffusion is a dead end — it
always converges to flat grey, because each step quietly drains a little variety out of the
image. So I borrowed a trick from the world of machine learning called **moment-preserving
renormalisation**: after each diffusion step, the toy measures the average brightness and
the spread of each colour channel and gently restores them to their original values. The
fine detail still blurs and rearranges, but the overall vividness is held constant.

The effect is that the colours never fade. Instead of dying out, the pattern circulates
indefinitely — mixing, swirling, and reforming like a slow tie-dye that refuses to settle.
A **Renorm** slider lets you dial this preservation from zero (pure physics; watch it fade
to grey) to one (perpetual motion), and everything in between. It's a small, satisfying
demonstration of how one modest correction can turn a dissipative system into an endlessly
generative one.
This "one modest correction rescues a dying system" flavour recurs elsewhere in the
collection: it is a close cousin of the _moment-preserving_ idea, and it rhymes with the
**Algebraic Colored Lattice Fields** experiment, where a bounded algebraic perturbation
rescues a sterile lattice from uniformity without resorting to randomness. Both take a
structure that "wants" to collapse into sameness and hold it, deliberately, in a
generative middle ground.

Layer the symmetry on top of that and you get patterns with the visual grammar of stained
glass, Islamic tilework, or frost on a window — but alive and flowing rather than frozen.

## Finding Your Way Around

The interface is deliberately simple; everything is a click or a slider away.

### Drawing

- **Draw / Erase / Fill** — paint with a colour, paint black, or flood a region.
- **Brush size** — a 1-to-10-cell radius.
- **Colour picker** — a full colour wheel plus eight quick palette swatches.

### Symmetry

A row of toggles you can combine freely:

- **Mirror X / Mirror Y** — reflect left-to-right and top-to-bottom.
- **Rotation 90° / 60° / 30°** — four-, six-, or twelve-fold rotational symmetry around the
  centre (the snowflake and mandala makers).
- **Diagonal Mirror** — reflect across the diagonal.
- **Translation X / Y** — wrap the canvas into a seamless tile, so a pattern drawn near one
  edge continues on the opposite side.

Whatever you switch on shapes both your brushstrokes _and_ the way the colours diffuse.

### Diffusion

- **Rate** — how quickly colours blend toward their neighbours each step.
- **Renorm** — the anti-fade control described above; keep it near 1 for perpetual swirl.
- **Neighbourhood** — how far each cell "reaches" when it mixes (tighter or fluffier flow).
- **Play / Stop / Step / Speed** — run the simulation, pause it, nudge it one frame, or set
  the pace.

### Canvas

- **Grid size and Aspect** — from chunky 32-pixel art to a wide 256-cell field.
- **Clear / Random** — start from black or from a field of random colour.
- **Save PNG** — keep a snapshot you like.

## Things Worth Trying

A few recipes that reliably produce something lovely:

1. Switch on **Mirror X + Mirror Y + Rotation 90°**, scribble a few coloured blobs, then
   press **Play** with **Rate ≈ 0.30** and **Renorm = 1.00** — instant kaleidoscope.
2. Hit **Random**, then **Play** at high speed for organic, tie-dye textures.
3. Turn on **Rotation 60°**, place a single off-centre dot, and grow yourself a snowflake.
4. Lower **Renorm** toward zero and watch a busy image melt gracefully into a single tone —
   a surprisingly meditative thing to leave running.

## Who Might Enjoy It

- **The simply curious**, who want a calming, generative pattern to play with — no
  instructions required, just paint and press Play.
- **Teachers and students**, as a hands-on illustration of symmetry groups and the
  heat/diffusion equation; you can _see_ what "converging to the mean" means, and _see_ how
  restoring a couple of statistics prevents it.
- **Artists and designers** hunting for seamless tiles, mandalas, or textures they can
  export and reuse.
- **Anyone who likes a good idea rendered visible** — the moment-preservation trick, in
  particular, is the kind of small insight that's much easier to appreciate when it's
  swirling in front of you than when it's written as an equation.

It's a modest toy, but it was more rewarding to build — and more absorbing to watch — than
I expected. Open it, draw something, and let it run. Enjoy!
