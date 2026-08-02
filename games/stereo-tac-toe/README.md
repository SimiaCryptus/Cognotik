# Stereo-Tac-Toe

**A game of tic-tac-toe hidden inside a Magic Eye picture.**

I recently built a small but genuinely strange experiment: a fully playable
game of tic-tac-toe whose entire interface lives inside an autostereogram —
one of those "Magic Eye" images that dissolves into 3D shapes once you relax
your eyes and let it fuse. There is no visible board, no visible X's and O's,
no visible cursor. Everything you need to play is encoded in the depth of a
field of shimmering noise; you simply have to _see_ it.

---

## What it is

Open the page and you are greeted by what looks like a rectangle of colored
static. Nothing obviously game-like about it. But if you soften your gaze the
way you would for any Magic Eye poster, the noise resolves into layers at
different depths: a tic-tac-toe grid floats up out of the background, a bright
block hovers over whichever cell your mouse is near, and the marks that have
been played stand at their own distinct depth. Click a cell, and a new shape
quietly rises into the scene.

In short, the interface _is_ the point. The game underneath is deliberately
trivial — familiar, solved, universally understood — precisely so that all
your attention goes to the perception-bending way you interact with it.

---

## A little background

Autostereograms became a pop-culture phenomenon in the 1990s, but the idea is
older and rather elegant: a single image can carry depth information if you
repeat a pattern horizontally and subtly shift that repetition based on how
"near" or "far" each point is meant to appear. Each of your eyes locks onto a
slightly different copy of the pattern, your brain reconciles the mismatch as
distance, and a hidden 3D surface emerges from what looks like flat noise.

Stereo-Tac-Toe treats that hidden surface as a _depth map_ — a grayscale
height-field where the background sits far away, the grid sits a little
closer, the played marks closer still, and the cursor nearest of all. The
game never draws pixels you can see directly; it draws _depth_, and the
stereogram translates that depth into the fusible image in front of you. (If
you ever want to peek behind the curtain, pressing <kbd>D</kbd> shows the raw
depth map in plain grayscale — a nice way to confirm that yes, there really
was a board in there all along.)

---

## Why it is interesting

A few things make this more than a novelty, at least to me:

- **The medium is the interface.** Most games render a picture and ask you to
  look at it. This one renders a _perceptual puzzle_ and asks you to look
  _into_ it. Playing becomes a small act of seeing differently.
- **Everything derives from one source of truth.** The board, the cursor, the
  marks, and even the winning line are all just values in a single depth
  buffer. That constraint is oddly satisfying: there is exactly one
  description of the scene, viewed two ways (the stereogram, and the
  diagnostic grayscale).
- **Stability matters in a way it usually doesn't.** For a fused Magic Eye
  image to stay comfortable, the background noise has to hold still while only
  the meaningful parts shift. Getting that "steadiness" right — so that moving
  your cursor ripples the image locally instead of scrambling the whole thing
  — turned out to be the most interesting design problem, and the most
  rewarding to solve.
- **It is endlessly tweakable.** A panel of controls lets you play with eye
  separation, pattern width, noise style and texture, depth contrast, and
  more. Autostereograms are famously personal — everyone's eyes fuse a little
  differently — so being able to dial the image to your own comfort turns the
  whole thing into a small instrument as much as a game.

---

## Who might find it useful

This is, honestly, a curiosity first — but a few audiences may get real value
from it:

- **The perceptually curious**, who enjoy optical illusions, Magic Eye images,
  and the general delight of watching their own visual system do something
  surprising.
- **Educators and students** looking for a hands-on, interactive way to
  explain how autostereograms encode depth; the live controls and the depth-map
  peek make an abstract idea tangible.
- **Designers and interaction tinkerers** interested in unconventional
  interfaces — cases where the _way_ you perceive a system is the experience,
  rather than an afterthought.
- **Anyone who just wants to play a weird, quiet game of tic-tac-toe** and see
  it float out of the noise.

---

## How to view it

If Magic Eye images are new to you: let your eyes relax as though you are
looking _through_ the screen at something far behind it, rather than focusing
on the surface. Give it a few seconds; the grid will drift forward and settle.
Once it clicks, hover over a cell to see the cursor rise, and click to play.
Your mileage may vary — fusing takes a little practice — but the moment the
board first snaps into depth is a genuinely lovely one.

Enjoy, and I'd love to hear how it fuses for you.
