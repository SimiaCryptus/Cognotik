# Bidirectional Markov Text Analyzer

A browser-based instrument that learns the statistical "feel" of a body of text and then shows
you, token by token, how _surprising_ some other text looks through that lens.
It runs entirely in your browser—nothing is uploaded, nothing is installed—and
it turns an abstract idea (how predictable is this writing?) into something you
can literally see, colored in like a heat map.

---

## The Big Idea

Give the tool a **corpus**—any text you like: a novel, your own emails, source
code, song lyrics, a decade of diary entries. The tool studies that text and
learns its patterns; which words tend to follow which, which letters cluster,
what "normal" looks like for _that_ particular writing.

Then you hand it a second piece of text—the **test text**—and it paints every
token according to how well it fits what the corpus taught it:

- **Calm and transparent** means the model expected this; it's typical.
- **Hot and bright** means the model is surprised; this token is unusual given
  its neighbors.

Click any token and the tool tells you what it _would_ have predicted instead,
and lets you swap it in. It's a bit like having a very well-read, slightly
opinionated reader looking over your shoulder, pointing at the words that made
them raise an eyebrow.

---

## A Little Background

Under the hood sits one of the oldest and most charming ideas in text
modeling: the **Markov chain**. The intuition is simple—the next word (or
letter) depends mostly on the handful of words just before it. Count how often
each continuation follows each little context in your corpus, and you have a
model that can estimate "how likely is _this_ word, right _here_?"

This tool adds a twist worth dwelling on. Ordinary models read left to right,
the way we do. This one reads **both directions**: it builds one model that
predicts each token from the words on its left, and a second that predicts it
from the words on its _right_. Every token therefore gets two opinions—one from
its past, one from its future—which are combined into a single verdict. A token
is comfortably "expected" only when both directions agree; that turns out to be
a much richer signal than either alone.

From those probabilities the tool computes a **surprise** score for every
token, and (for the whole passage) a single summary number called
**perplexity**—loosely, the effective number of choices the model felt it faced
at each step. Lower perplexity means the text felt more predictable. It's the
standard yardstick language researchers use, and here you get to watch it move.

---

## What You Actually See

The interface is deliberately hands-on:

- **The corpus panel.** Paste text or drop in a `.txt` file. You'll see live
  stats—character count, token count, size—and a gentle warning if your corpus
  is very large (building can be slow, though it happens quietly in the
  background so the page stays responsive).
- **The heatmap.** Your test text, rendered inline, every token a colored chip.
  Hover one to see its exact probabilities; the color intensity encodes
  surprise. You can choose between a **Heat** palette (transparent when calm,
  glowing when surprising) and the perceptually even **Viridis** scale, and
  between a **linear** or **logarithmic** color scale. Log is the default,
  because natural-language probabilities span many orders of magnitude and log
  spreads out the structure you'd otherwise miss.
- **The replacement popup.** Click a token to see the model's top predictions
  for that spot, each with a little bar and its forward, backward, and combined
  probabilities. The word actually in your text is marked as the original;
  click any alternative to substitute it, and the analysis re-runs instantly.
  It's an oddly addictive way to ask, "what would make this sentence _less_
  surprising?"—one word at a time.
- **Match statistics.** Below the heatmap, a panel summarizes the whole
  passage: how often the model's top guess matched, how often the real word
  landed in its top-N, mean and geometric-mean probabilities, and perplexity.

Most settings—how scores are combined, the palette, the color scale—update
everything instantly. A few settings change _how the corpus is learned_ (the
tokenizer, the model's order, lowercasing, smoothing) and so require a rebuild;
the tool tells you when.

---

## Why It's Interesting

It turns out that "surprise" is a surprisingly expressive quantity. A few
things I find genuinely fun about watching it:

- **Anomalies light up.** Train on "normal" text, paste in something
  out-of-place, and the odd bits practically glow. It's a visceral, visual take
  on outlier detection.
- **Style becomes measurable.** Train on one author and test another; the
  heatmap and the perplexity number quietly quantify how far apart two voices
  are. Prose, code, and poetry each have their own fingerprint.
- **You can feel the trade-offs.** Nudge the model's order (how much context it
  uses) or its smoothing (how it handles things it's never seen) and watch
  predictability trade off against data sparsity in real time. It's an
  intuition pump for ideas that are usually buried in equations.
- **Rewriting becomes exploration.** The click-to-replace loop makes the model's
  preferences tangible; you're not reading _about_ a language model, you're
  poking one and watching it flinch.

None of this requires you to believe Markov models are the last word in
language—they emphatically aren't. Their charm is that they're simple enough to
understand completely and yet rich enough to reveal real structure, which makes
them a wonderful teaching and exploration instrument.

---

## Who Might Find It Useful

- **The curious reader** who's heard about "language models" and "perplexity"
  and wants to _see_ what those words mean without wading through math.
- **Writers and editors** interested in a second, statistical opinion on where
  their prose zigs when a reader expects a zag.
- **Students and teachers** of linguistics, information theory, or machine
  learning who want a live, tactile demonstration of n-grams, smoothing, and
  predictability.
- **Tinkerers and hobbyists** who enjoy feeding a tool their own corpus—emails,
  chat logs, a favorite book—and seeing what patterns fall out.
- **Analysts** who want a quick, visual first pass at "does this text look like
  that text?" before reaching for heavier tooling.

---

## Getting Your Bearings Quickly

If you want a signal fast: paste a page or two of some text you know well into
the corpus, build the model, then paste a different page into the test area and
watch the colors. Hover a few tokens to read their probabilities; click one to
see what the model wanted instead. Then try switching the color scale to log,
or bumping the model order, and notice how the picture changes.

That's the whole idea—a small, transparent model, made visible. I'm looking
forward to hearing what patterns you find in your own text. Enjoy!
