# Chromatic

## A color engine that thinks in perception, not in hue wheels

---

## The core theory

Most color tools operate on spaces — HSL and HSV — that are convenient for
computers but not honest about human perception. Equal steps in HSL hue,
saturation, or lightness do _not_ correspond to equal steps in what your
eye actually perceives. So when you rotate evenly around an HSL hue wheel,
the perceptual spacing collapses in some regions and balloons in others.
The math is even; the _seeing_ is not.

Chromatic starts from a different premise. It builds palettes in a
**perceptually uniform space** (OKLab/OKLch), where a step of a given size
means roughly the same amount of perceived color difference everywhere.
Then it treats a palette not as a bag of swatches but as a **point-set
with structure** — distances between colors, which colors are neighbors,
what symmetry the arrangement has, and, crucially, the _ordering_ of
colors by lightness, chroma, and hue.

Here's the guiding principle, and it's worth stating plainly:

> A palette is "good" when its relational invariants — ordering,
> adjacency, symmetry, and approximate distances — stay stable across
> colorspaces, even if the exact geometry warps along the way.

That last clause matters. When you take a beautifully arranged palette out
of OKLab and project it into HSL or sRGB (the spaces our tools and screens
actually use), some distortion is unavoidable. The interesting question is
not "can we avoid distortion?" but "_which properties survive?_" It turns
out that preserved **ordering** — colors keeping their relative rank in
lightness and hue — is what makes a palette still read as coherent, even
when absolute distances shift. Chromatic makes that survival measurable.

---

## What the demo shows you

The flagship demonstration is deliberately simple, because the whole point
is to make an argument you can _see_ rather than one I merely assert. It
puts two rows of swatches side by side:

1. A palette laid out as an even orbit in **OKLch**, then projected into
   HSL for display.
2. A naive **HSL hue-rotation** — the "correct" tidy approach most tools
   use.

The difference is immediate. The OKLch orbit steps evenly across your
perception; the naive HSL row bunches near yellow and cyan and stretches
through blue and magenta, exactly the failure mode I described above.

### The controls

A handful of sliders let you drive the arrangement in real time:

- **Color count** — how many colors sit around the orbit
- **Lightness** — how bright the whole set sits
- **Chroma** — how saturated/vivid the colors are
- **Start hue** — where on the wheel the arrangement begins

### The metrics

Below the swatches, Chromatic reports a small **distortion report** with
concrete numbers, so the comparison is quantitative rather than a matter
of opinion:

- **Ordering violations** — how many pairs of colors flip their relative
  order when projected (the invariant that matters most)
- **Average hue distortion** — how much hues drift during projection
- **Gamut clipping** — how many colors fall outside what your screen can
  actually display and had to be pulled back in

Nudge a slider and watch the numbers respond. That feedback loop — intent
in, measured consequence out — is the difference this project is premised
on, made tangible.

---

## A little background

The names to know are short. **OKLab** and its polar cousin **OKLch** are
relatively recent perceptually-uniform color models designed to fix the
non-uniformity that older working spaces (HSL, HSV, even the venerable CIE
Lab in places) suffer from. They give us a coordinate system where
distance means something close to _perceived difference_.

The broader idea — treating color arrangement as constrained geometry in a
perceptual space, then projecting and measuring the damage — is the thread
that ties Chromatic together. Longer term, the project envisions a small
declarative language for stating relational _intent_ ("these two should
contrast in lightness," "this row is a hue cycle," "give this palette
six-fold symmetry") and an optional solver that reconciles that intent
with the distortions of real colorspaces. But the demonstration here is
the foundational claim, standing on its own: geometry built in OKLab
genuinely looks and reads better than hue-rotated HSL, and that advantage
can be measured.

---

## Why it's interesting

A few reasons I find this genuinely fun to think about:

- **It reframes an aesthetic complaint as a geometry problem.** "This
  palette feels off" becomes "these ordering invariants didn't survive
  projection" — a statement you can test.
- **It's honest about tradeoffs.** Chromatic doesn't pretend projection is
  lossless; it measures the loss and tells you which structure held.
- **It's the kind of thing you can argue with.** Because the metrics are
  concrete, you can disagree with a specific number rather than a vibe.

There's a modest intellectual pleasure in taking something usually left to
intuition — "pick some nice colors" — and giving it a coordinate system, a
measurement, and a control panel.

---

## Who might find this useful

- **Designers and design-system authors** who've been burned by palettes
  that looked coherent in one tool and fell apart in another, and who want
  to understand _why_.
- **Front-end and product engineers** wiring up theme tokens and CSS color
  variables, curious about what happens to their carefully chosen colors
  in the spaces the browser actually uses.
- **Data-visualization practitioners**, for whom ordered, perceptually
  even color scales are not a luxury but a correctness requirement — a
  misleading color ramp is a misleading chart.
- **The color-curious** — anyone who enjoys seeing a familiar intuition
  ("blues and greens feel differently spaced than reds and yellows") made
  precise and interactive.

You don't need to write a line of code to get the point; you just need to
move a slider and watch the naive row fall apart while the perceptual row
holds together.

---

## Running the demo

The one practical note: because the demo loads modern browser modules
directly, it needs to be served over HTTP rather than opened as a bare
file. Any simple static server will do — for instance, from the
`experiments/chromatic` directory, `npx serve .` (or
`python3 -m http.server`) and then visit the demo in your browser.

---

I built this to make a specific argument visible, and I'm looking forward
to hearing where it holds up and where it doesn't. If a palette that
"should" work still feels wrong to you, I'd love to know whether Chromatic
can tell you why. More soon — enjoy!
