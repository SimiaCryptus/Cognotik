# Numbers as Machines

## An Essay on Treating Every Number as a Tiny, Running Program

Most of us learned numbers as _things_. A number is a value — something you write
down, store in a box, and pull out when you need it. Three is three; π is a
slightly awkward three-point-one-four-something that we round off and move on.
This essay proposes a different way of seeing them, and it is not a metaphor: what
if every number were instead a small, deterministic _machine_ — a program that,
when you ask it, hands you its next digit, and then the next, forever?

That single shift in perspective turns out to be surprisingly productive. It
reframes arithmetic, precision, randomness, and even the philosophy of what a
number _is_ — and it does so in a way that maps cleanly onto how computers
actually work. This document is a tour of that idea: the theory, a little of the
background it rests on, why it is genuinely interesting, and who might get
something out of it.

---

## The Core Idea

The whole framework balances on one tiny contract:

```
step : State → (digit, State)
```

In plain terms: a number is anything that, given its current internal state, can
produce its next digit and tell you what state to be in next. That's it. A number
is a machine you can _step_. You pull digits out one at a time, exactly as many as
you need, and no more.

From this one primitive, everything else is built as a combination of machines.
Vectors are machines arranged over numbers; matrices are machines arranged over
vectors; the nesting is uniform all the way up. Arithmetic becomes _composition_ —
adding two numbers means wiring their two machines together into a new one that
emits the sum's digits. It turns out that this is not just a cute reframing; it is
the same mathematical structure (a "final coalgebra for the digit-stream functor,"
if you want the formal name) that mathematicians have used for decades to reason
about infinite streams. The novelty here is not the underlying mathematics — it is
the _unified interface_, where a humble fraction and a transcendental constant like
π speak exactly the same protocol.

### The "UI": How You'd Actually Touch It

Because a number is a running machine rather than a stored value, the way you
interact with it has a distinct feel. A few gestures capture the spirit:

- **Pull digits on demand.** You ask for fifty digits, you get fifty; you never
  "run out of precision," because precision is generated, not pre-stored.
- **Fork a number.** You can clone a number's machine mid-computation and explore
  two continuations in parallel — useful for comparison, speculative arithmetic,
  and metrics.
- **Change the base without changing the number.** The base (decimal, hex, binary)
  is treated as a _codec_ — a lens you look through — not part of the number
  itself. Swap the lens; the underlying machine is untouched.
- **Skip ahead, when you can.** For certain numbers under certain lenses, you can
  jump directly to the billionth digit without computing the first billion — the
  famous trick behind the BBP formula for π in base 16.
- **Compare honestly.** You can ask "is x definitely less than y?" or "do these
  agree to twenty digits?" — but you cannot ask "are these exactly equal?" and get
  an honest yes. More on why below.

---

## A Little Background

None of the ingredients here are new, and the essay is refreshingly candid about
that. The idea of a number as an unfolding process has deep roots: Cauchy
sequences, Turing's 1936 computable reals, coinductive stream arithmetic, the
p-adic numbers, and working exact-real-arithmetic systems like iRRAM all
instantiate parts of this picture. What this project contributes is the
_synthesis_ — one protocol, one honest accounting of costs, and a clean separation
between a number and the base you happen to read it in.

The framing also comes with a companion body of critical analysis (perspectives
from mathematics, software engineering, and hardware, plus a long Socratic
dialogue) that stress-tests the claims and, importantly, marks where the idea
reaches its limits rather than papering over them. That honesty is part of what
makes the whole thing trustworthy.

---

## Why It Is Interesting

Several consequences fall out of the "numbers are machines" stance that are worth
dwelling on, because they are where the idea earns its keep.

### 1. Addition is secretly hard — and there's a fix.

Here is a puzzle that most people never notice: what is the first digit of
`0.4999... + 0.5000...`? You _cannot_ know it from any finite amount of looking,
because a carry from arbitrarily deep in the expansion could ripple all the way up
and flip everything. Standard digit-by-digit addition simply stalls. The classical
fix — a _signed-digit_ (redundant) representation, where digits can be negative —
makes addition local again, and the machine can commit to digits with bounded
look-ahead. It's a lovely example of a "bug" in our everyday notation that only
becomes visible when you try to make it _run_.

### 2. Some numbers are cleaner than others.

The p-adic numbers — an alternate number system beloved by number theorists — turn
out to be _easier_ machines than the ordinary reals. Their digits commit
_locally_: you can compute low-order digits from a finite handful of terms, with no
"tail oracle" and no anxious interval-narrowing. The reals, by contrast, sometimes
have to approximate their way to a digit. Seeing this asymmetry so starkly is one
of the framework's quiet pleasures.

### 3. Complexity gets a memory-shaped ruler.

In this model, the "hardness" of a number is the _size of its machine's state_ —
how many coupled registers it needs to keep straight. Rationals need essentially
one; quadratic irrationals like √2 need two; π and e seem to need a few more; and
genuinely random-looking streams need state that grows without bound. This gives a
concrete, implementation-level hierarchy of numerical complexity. The essay is
admirably careful to call this a _conjecture_ — an upper witness rather than a
proven floor — but it is a compelling way to make "some numbers are more
complicated than others" precise.
This state-size hierarchy is a close cousin of the _cost_ hierarchy developed in
the companion essay **Rational Certificate Complexity (RCC)**, which ranks
numbers not by how much _state_ their machine carries but by how many _bits_ its
certificates cost as you demand more precision. Both essays reach the same
verdict from different angles: algebraic irrationals like √2 are genuinely
cheaper — smaller state, optimal bit-cost — than the classical series-engines for
π. And both are made tangible in the **`nam` interactive lab**, where you can
watch these machines emit digits, fork, and refuse to lie about equality.

### 4. Randomness might just be hidden determinism.

If every number is a deterministic machine, then "random" numbers are simply
numbers whose _generator is inaccessible_ — hidden behind a one-way function
(cryptography) or provably uncomputable (Chaitin's Ω). To someone reading the
digit stream, all three cases look identical. The essay offers this as a _working
stance_, not a settled truth — it openly flags that it conflates distinct
philosophical categories — but it is a genuinely thought-provoking way to organize
the zoo of "unpredictable" numbers.

### 5. Equality becomes honestly undecidable.

Perhaps the most philosophically bracing consequence: once a number is a
non-halting machine, asking "are these two numbers _exactly_ equal?" becomes
undecidable in general (this is Rice's theorem, made operational). The system
refuses to lie about it — it offers "less than," "agrees to N digits," and
"indistinguishable so far," but never a false claim of exact equality. There is a
Futurama joke the essay leans on here: you can always _translate_ the problem, but
only into a language where the thing you actually wanted has become harder. That's
the honest bill for the elegance.
This principled refusal to fabricate an answer is exactly what the **`nam`
interactive calculator** puts on screen: a candid three-way verdict and a
`pending (null)` where a digit genuinely cannot be established. The theory here
and the calculator there are two faces of the same commitment to honest
computation.

---

## Who Might Find This Useful

This is an _architecture and ideas_ piece more than a shipping product, so its
audience is correspondingly broad:

- **Curious technically-minded readers** who enjoy a good "what if we looked at
  this completely differently?" essay and want their assumptions about numbers
  gently upended.
- **Mathematicians and computer scientists** interested in computable analysis,
  coalgebra, p-adics, or the theory of exact real arithmetic — who will recognize
  the roots and may enjoy the unifying framing.
- **Numerics and systems practitioners** frustrated by the fragmentation of
  exact-arithmetic tools (MPFR, iRRAM, mpmath living in separate worlds), who will
  appreciate the argument for a single composable protocol.
- **Philosophers of mathematics**, for whom the undecidability of equality and the
  "randomness as encrypted determinism" stance are live, unresolved test cases —
  the accompanying dialogue treats these with real seriousness.
- **Educators**, who may find the "numbers are little machines" framing a
  surprisingly effective intuition pump for teaching carries, precision, and the
  limits of computation.

---

## What to Take Away

The honest verdict — and the essay works hard to be honest — is that "numbers as
machines" is not a wholesale replacement for the classical idea of a number so much
as a _computational elaboration_ of it. It makes the procedural content of numbers
explicit: the cost of each digit, the locality (or non-locality) of arithmetic, the
cheapness or expense of forking, and the precise point at which questions like "are
these equal?" stop having computable answers.

That last point is worth sitting with. By pushing the computational view of numbers
as far as it will go and finding undecidability waiting at the core, the framework
delivers a genuinely interesting _negative_ result: the abstract, timeless
conception of numbers keeps re-emerging whenever we try to eliminate it. Which is,
perhaps, the most reassuring thing a radical reframing could tell us.

Enjoy the tour — and if any of these threads pull at you, the deeper essay and its
critical companions are where the real fun begins.
