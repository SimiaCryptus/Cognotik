# Relativistic 2-Body Gravity Simulator

## What This Is

An interactive simulation of two bodies orbiting each other under
gravity—but with a twist that most textbook orbit demos leave out: gravity
that takes _time_ to travel, and a tunable dial that blends smooth Newtonian
motion into something closer to Einstein's picture of the world. The result
is a small, hands-on playground where you can watch orbits do things they
"aren't supposed to" do; they slowly rotate, wobble, and occasionally fling
off into chaos, all in response to sliders you control in real time.

This is meant to be _seen and played with_, not read about. If you have ever
wondered why Mercury's orbit doesn't quite close on itself the way Newton
promised, this is a way to build that intuition with your own hands.

## A Little Background

Isaac Newton gave us a wonderfully simple rule: every mass pulls on every
other mass, with a force that weakens with the square of the distance. That
rule is astonishingly good—good enough to fly spacecraft across the solar
system—and it predicts that two bodies trace out perfect, endlessly repeating
ellipses.

But the universe is subtler than that in two important ways:

- **Nothing is instantaneous.** Gravity, like light, travels at a finite
  speed. So a body doesn't respond to where its partner _is_; it responds to
  where its partner _was_, a moment ago, when the signal left. Astronomers
  call this the "retarded" position (a delightfully old-fashioned term for a
  time-delayed one).
- **Motion changes gravity.** In Einstein's relativity, fast-moving, massive,
  tightly-bound systems feel corrections that Newton never accounted for.

Individually, these effects are tiny for everyday orbits. But they accumulate.
The most famous fingerprint is the slow _precession_ of Mercury's orbit—its
ellipse rotates by a mere 43 arcseconds per century beyond what Newton
predicts, and explaining that sliver was one of general relativity's first
great triumphs. This simulator lets you crank those normally-invisible effects
up until they become impossible to miss.

## What You'll See and Control

On screen: two bodies, their glowing trails, and optional overlays showing
velocity and force directions. Because gravity here is time-delayed and the
forces are no longer perfectly balanced, the system genuinely evolves on its
own—there's no fixed center, and the orbits are free to drift, rotate, and
surprise you.

The controls are where the fun lives:

| Control                | What it does                                        |
| ---------------------- | --------------------------------------------------- |
| **Mass 1 / Mass 2**    | How heavy each body is (and thus how hard it pulls) |
| **Initial velocity**   | Drag to set how fast and which way each body starts |
| **Speed of light `c`** | Lower it to exaggerate the time-delay effects       |
| **Relativity `alpha`** | Blend from pure Newton (0) to fully-corrected (1)   |
| **Gravity `G`**        | Overall strength of the pull                        |
| **Presets**            | Load a stable binary, a precessing orbit, or chaos  |

The two dials I find most rewarding are the **speed of light** and the
**relativity strength**. Turn them both toward their "off" settings and you
recover Newton's tidy, closed ellipses—a reassuring sanity check. Nudge them
the other way and watch the orbit slowly rotate around, trace out flower-petal
rosettes, and eventually tip into unpredictable, chaotic paths.

## Why It's Interesting

A few reasons this captured my attention enough to build it:

1. **It makes the invisible visible.** The precession that took decades of
   careful astronomy to detect becomes something you can dial up and watch in
   seconds.
2. **It rewards curiosity.** Small changes to the starting conditions can
   produce wildly different long-term behavior; this is a gentle, visual
   doorway into chaos and sensitivity to initial conditions.
3. **It bridges two worlds.** Classical mechanics and general relativity are
   usually taught in separate courses, years apart. Here they sit on the same
   screen, connected by a single slider.

A caveat worth stating plainly, in the spirit of honesty: this is an
_illustrative_ tool, not a precise solver of Einstein's equations. The
relativistic corrections are qualitative and deliberately tunable—chosen to
build intuition and delight the eye, not to publish in an astrophysics
journal. Think of it as a well-made physical analogy rather than a telescope.

## Who Might Enjoy It

- **The simply curious**, who've heard that "gravity bends space and time" and
  want to poke at what that actually looks like.
- **Students** meeting orbits, energy conservation, or relativity for the
  first time, who benefit from seeing a concept move before pinning it down
  with equations.
- **Educators** looking for a live, tweakable demo to spark a classroom
  conversation about why Newton isn't quite the whole story.
- **Tinkerers and armchair physicists** who like turning knobs until the
  pretty pictures break in instructive ways.

No prior physics is required—just a willingness to grab a slider and see what
happens.

I'm looking forward to hearing which orbits you manage to break. Enjoy!
