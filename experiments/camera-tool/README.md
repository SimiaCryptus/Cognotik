# Laser-Damage Camera Correction: Giving Damaged Sensors a Second Life

## The Problem, in Everyday Terms

A digital camera sensor is a grid of millions of tiny light detectors —
one per pixel. Under normal wear, or after a bright laser strikes the
sensor, some of those detectors stop behaving:

- **Dead pixels** — detectors that read black no matter how much light
  arrives; think of a light bulb that simply won't turn on.
- **Stuck pixels** — detectors frozen at one value, always bright or
  always the same color, ignoring the scene entirely.
- **Hot pixels** — detectors that glow far brighter than they should,
  especially visible in dark photos as stray sparkles.
- **Noisy pixels** — detectors that flicker unpredictably, out of step
  with their calm neighbors.

A laser is particularly cruel here because it can wipe out a whole
_contiguous patch_ of neighboring detectors at once — a small burn scar on
the sensor — rather than a lone speck. That clustering is part of what
makes the problem interesting, and part of what makes it hard.

## The Core Idea

The tool works in two acts, and the split matters.

### Act One — Profiling the Camera

First, you teach the app about _your specific_ camera's injuries. It walks
you through capturing a short sequence of frames under a few different
conditions:

1. **Dark frames** (lens covered) — the best way to catch pixels that glow
   when they shouldn't.
2. **Bright, even frames** (point at a uniform surface) — the best way to
   catch pixels that stay dark when they should light up.
3. **Mixed scenes** — to confirm which pixels simply never respond to the
   world changing around them.

As the frames stream in, the app keeps running statistics for every single
pixel — its average, its swing between brightest and darkest, its
variability. From these it builds a **defect map**: a list of which pixels
are broken, how they're broken, and a _confidence score_ saying how sure it
is. Each suspect pixel earns its place on the map only if the evidence is
strong enough, which keeps healthy pixels from being falsely accused.

### Act Two — Correcting Your Photos

Once the map exists, the second act is repair. Whenever the camera captures
an image, the app looks up each flagged pixel and _infills_ it — it
reconstructs the missing value by borrowing from the healthy pixels nearby.
A simple average of good neighbors works well for lone defects; larger
burn scars need the tool to reach further outward, since a defect's
immediate neighbors may themselves be damaged.

In short: profile once, then let the camera quietly fix itself on every
shot thereafter.

## Walking Through the Interface

The app is designed to feel like a guided wizard rather than a control
panel, so you never need to understand the statistics underneath.

- **Camera selection & live preview** — pick which camera to use and see
  what it sees, right in the browser.
- **The profiling wizard** — plain prompts ("Cover the lens", "Point at a
  bright even surface", "Capturing dark frames… 12 of 30"), a progress bar,
  and a running count of defects discovered.
- **A defect overlay** — the detected broken pixels are highlighted
  directly on top of the live image, so the damage becomes visible instead
  of abstract.
- **Adjustable sensitivity** — sliders let you tune how aggressive the
  detection is and re-run it instantly, without recapturing anything.
- **A before/after capture view** (as the project matures) — toggle
  between the raw and corrected image to judge the repair for yourself.

## Why This Is Interesting

A few things drew me to this project, and I suspect they're what make it
worth a look:

- **It runs entirely on your device.** No photos are uploaded, nothing
  touches a server, and it works offline once loaded. Your images — and
  your camera's quirks — stay private.
- **It's installable like an app** but is really just a web page; there's
  nothing to compile and nothing to trust beyond the browser you already
  have.
- **It treats "broken" hardware as recoverable.** There is something
  genuinely satisfying about reclaiming a camera that would otherwise be
  thrown away — repair over replacement, done in software.
- **The math is intuitive, not intimidating.** The whole approach rests on
  a simple premise: a pixel that disagrees with both its neighbors and its
  own past behavior is probably broken, and a broken pixel can be estimated
  from the healthy ones around it.

I'll be honest about the limits, because they're part of the story. The
browser hands us only 8-bit brightness values, so the very faintest hot
pixels in dark frames can be hard to distinguish from ordinary noise; this
is a known constraint I'm continuing to investigate. Profiling also asks a
little patience from you up front — the more frames you capture, the more
trustworthy the map.

## Who Might Find This Useful

- **Anyone with a laser-damaged camera** — the original motivation:
  phones, webcams, or lab cameras that took a hit and now show persistent
  specks or dead spots.
- **Photographers and videographers** fighting hot pixels in long-exposure
  or low-light work, who want a repeatable fix rather than manual retouching.
- **Researchers and lab technicians** working around optics benches where
  stray laser light is an occupational hazard for imaging equipment.
- **Tinkerers and the repair-minded** who would rather rehabilitate a
  damaged sensor than replace it — and who enjoy seeing the invisible
  damage made visible.
- **The merely curious**, who want to peek at how a camera actually breaks
  and how software can compensate.

## Where It's Headed

Today the tool can profile a camera and visualize its defects; the
roadmap adds saving and sharing profiles, applying corrections to
full-resolution stills, and eventually real-time corrected preview using
the graphics hardware. I'm building it in the open and would genuinely
welcome feedback from anyone who tries it on a camera of their own.

More soon, I hope — and if you have a damaged camera gathering dust, I'd
love to hear how it fares. Enjoy!
