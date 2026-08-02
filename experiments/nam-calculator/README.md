# nam — the interactive numbers-as-machines lab

An interactive lab that treats every number not as a value sitting in a register, but as a tiny, living machine.

---

## The idea in a nutshell

We usually think of a number as a thing you _have_: a quantity written down,
stored, and read back. **Numbers as Machines (`nam`)** flips that intuition
on its head. Here, a number is a little program — a deterministic machine
that, when you ask it politely, hands you one more digit; ask again, and it
hands you the next; and so on, forever, on demand.

That sounds like a small philosophical distinction, but it turns out to
change almost everything downstream. If a number is a machine that emits
digits, then all the familiar operations become _combinators_ — ways of
wiring machines together:

- **Changing base** (from decimal to binary, say) isn't creating a new
  number; it's just looking at the _same_ machine through a different lens.
  The base is a codec, not a property of the number itself.
- **Copying a number** ("forking") has an honest, visible cost. Some numbers
  are cheap to duplicate; others carry growing internal machinery that must
  be copied carefully. The lab always tells you which.
- **Comparing two numbers** becomes surprisingly subtle — and this is the
  heart of the whole thing.

---

## Why comparison is the interesting part

Here is the catch that makes this project worth your attention. If a number
is an endless stream of digits, then asking "are these two numbers equal?"
means asking "will these two streams _ever_ disagree, all the way to
infinity?" And in general, you cannot know. You can watch a hundred digits
agree, a thousand, a million — and still the very next digit might differ.

Most software, faced with this, quietly fakes an answer. This lab refuses.
Instead of pretending, it gives you an honest, three-way verdict: _less_,
_greater_, or **"indistinguishable so far — I cannot prove it either way."**
When a digit genuinely cannot be established, the screen shows a candid
`pending (null)` rather than a fabricated value.

I find this genuinely delightful. The machinery _could_ keep streaming
digits forever, but it will not claim a definite answer it cannot actually
justify. It's a small act of intellectual honesty baked into arithmetic
itself.

---

## What the lab looks like

The lab is a browser-based calculator (with a matching text console for
people who prefer typing). It's built to make the abstract ideas above
tangible and clickable. The screen is organized into a few coordinated
parts:

- **A display** that shows your most recent result, which base it's being
  viewed in, and how expensive it would be to copy.
- **A keypad** for one-tap operations on whichever number you're focused on.
- **Panels** for building new numbers (fractions, square roots, famous
  constants like π and _e_), inspecting them, comparing them, and doing
  arithmetic.
- **A console** where every operation can also be typed as a short command —
  anything you can click, you can type, and vice versa.
- **Registers**: named slots holding your numbers, like variables on a
  graphing calculator. A special slot named `_` always holds your last
  result.

Crucially, the calculator never lies about cost or certainty. Copying is
tagged with its price; comparison is three-way; and unprovable digits are
shown as honest blanks. Every screen carries the same promise: _nothing the
calculator shows you is a comfortable lie._

---

## A little background

Classical numerics stores a number as a value in a register and moves on.
That's efficient and it's usually what you want. But it hides something:
many numbers — π, _e_, the square root of two, one-third written out in full
— are _infinite_ objects that we only ever approximate. `nam` takes those
infinite objects seriously by representing them as processes rather than
snapshots.

This lineage runs through ideas in computable analysis and exact real
arithmetic — the notion that a real number can be _the recipe that produces
it_. What this lab adds is a hands-on, visual surface for playing with those
recipes, watching their costs, and confronting the honest limits of what can
be decided. The companion essay (`THEORY.md`) goes deeper into the design
philosophy; a reference map (`LIBRARY.md`) documents the underlying engine
for the curious.

---

## Why it's interesting

A few things make this more than a novelty, at least to my eye:

1. **It makes an abstract truth concrete.** The undecidability of equality
   is usually a footnote in a logic course. Here you can _watch_ it: two
   numbers that agree for thirty digits, with the lab steadfastly declining
   to call them equal.
2. **It surfaces hidden costs.** We rarely think about what it _costs_ to
   copy or fast-forward a number. The lab annotates these, turning invisible
   trade-offs into something you can see and reason about.
3. **It models honesty.** In an era where software confidently asserts
   answers it hasn't earned, there's something refreshing about a calculator
   that says "I don't know, and here's exactly why."

---

## Who might find it useful

- **The mathematically curious** — anyone who has wondered what it means for
  a number to be "infinite," and wants to poke at the idea directly.
- **Students and educators** — it's a vivid teaching aid for topics like
  computability, exact real arithmetic, base conversion, and the difference
  between "unknown" and "false."
- **Working technologists** thinking about honesty in computation — how to
  represent uncertainty faithfully rather than papering over it.
- **The simply playful** — you can open it and start clicking; the good
  ideas reveal themselves as you go.

You'll need to open it through a small local web server rather than by
double-clicking the file, since the underlying engine loads as a web module
— but once it's up, everything happens right in the browser.

---

I built this as much to think clearly about honest computation as to have
something fun to click on, and it delivered on both counts more than I
expected. Open it, poke at the boundaries, and watch it refuse to lie to
you. I'm looking forward to hearing what you find. Enjoy!
