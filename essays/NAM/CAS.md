---
specifies: main.mac
related:
  - README.md
---

# CAS Companion Goals: Numbers as Machines (NAM)

This document specifies goals for an agentic coding process tasked with writing a
companion verification/experimentation script for the NAM essay. NAM is an
_architecture_ essay rather than a theorem essay, so the companion's job is to build a
**small, honest reference prototype** of the generator-VM substrate and to use it to
**empirically confirm or falsify** the essay's structural claims.

A CAS (Maxima) is useful for the symbolic/number-theoretic checks (periodicity,
valuations, BBP modular structure, state-dimension recurrences). A systems language
is not required for the prototype — a high-level language with exact bignums
(Python+gmpy2, or Maxima's own Lisp layer) suffices to demonstrate every claim. The
LLVM/ABI claims are validated _structurally_ (state layout, fork cost) rather than by
actually emitting machine code.

---

## 0. Infrastructure / Harness Goals

- **G0.1 — The step primitive.** Implement the core interface
  `step : State → (digit, State)` as the single contract every number satisfies.
  Provide a driver that pulls N digits and a `take(vm, N)` helper.
- **G0.2 — Fork primitive with cost accounting.** Implement `fork(vm)` and instrument
  it to report whether the fork was an O(1) value copy (automaton tier) or required a
  deep copy of growing accumulators (series tier). Cost accounting is the point of
  the two-tier ABI; make it measurable.
- **G0.3 — Base/codec separation.** Implement `in_base(vm, b)` as a _projection_
  wrapper, demonstrating that changing base does not change the underlying generator.

---

## 1. The MUX / Coalgebra Foundation

- **G1.1 — MUX combinator.** Implement `MUX(selector, left, right)` as a generator
  over generators and demonstrate it builds: a number (digit function), a vector
  (MUX over numbers), and a matrix (MUX over vectors) — the uniform nesting claim.
- **G1.2 — Final-coalgebra sanity.** Demonstrate the digit-stream functor framing by
  showing two VMs are bisimilar iff they emit identical streams on a finite prefix
  (necessarily prefix-only, since full equality is undecidable — see G7).

---

## 2. Signed-Digit Arithmetic (the carry problem)

- **G2.1 — Carry non-locality demonstration.** Implement _naive_ standard-base addition
  and exhibit the failure: show that the first digit of `0.4999... + 0.5000...` cannot
  be committed from any finite prefix (the combinator stalls). This is the essay's
  motivating pathology; reproduce it concretely.
- **G2.2 — Avizienis signed-digit fix.** Implement signed-digit (redundant)
  representation with digits in {−(b−1),...,b−1} and show that addition becomes
  digit-local with bounded look-ahead — the same input now commits digits.
- **G2.3 — Streaming arithmetic.** Confirm addition, subtraction, and multiplication
  are genuinely streaming (online) under signed digits, with the codec layer
  projecting back to a non-redundant alphabet only on emission.

---

## 3. p-Adics as Periodic Automata (the clean case)

- **G3.1 — Periodic generator.** Implement a p-adic rational as a finite-state machine
  and verify its digit expansion is ultimately periodic with a finite state orbit.
- **G3.2 — Local digit commitment.** Demonstrate the essay's key contrast: p-adic
  digit commitment is _local_ (no tail oracle, no interval refinement) because a term
  of valuation v affects only positions ≤ v. Implement `valuation(a_n)` and show
  low-order digits computed from a finite term subset.
- **G3.3 — Metric as product automaton.** Implement the p-adic distance
  `p^{−v_p(x−y)}` for two periodic generators as a product automaton over their state
  spaces, and confirm it is itself a finite periodic machine.

---

## 4. The "Odd Primitives" (series tier)

- **G4.1 — Series stepper + tail-bound oracle.** Implement `series_step` and
  `tail_bound` for at least e (Σ1/n!) and one of {π via a known series, log 2}.
  Verify the tail bound is rigorous: the emitted digit never gets revised once
  committed.
- **G4.2 — Interval refinement engine.** Implement `refine_interval` /
  `emit_digit_if_safe` and demonstrate online digit extraction as the interval
  collapses.
- **G4.3 — Exact-boundary honesty.** Demonstrate the documented caveat: a value that
  is exactly 0.5 in base 10 may _never_ commit a leading digit in finite time. Show
  the engine correctly returns "pending" rather than lying — and that the
  interval-based predicates (G7) handle it gracefully.

---

## 5. State-Dimension Complexity Tiers (the central conjecture)

The essay's headline table claims minimal VM state dimension grows: rationals (1),
quadratic irrationals (2), classical transcendentals (3–4), higher transcendentals
(4–6+), inaccessible (unbounded).

- **G5.1 — Minimal recurrence orders.** For rationals and quadratic irrationals, build
  the minimal-state generator and confirm the logical register count (1 and 2
  respectively) and O(log n) bit-width growth. Symbolically derive the order-2
  recurrence for √2, φ, √3.
- **G5.2 — Transcendental state tier.** Build generators for π and e and _measure_
  live state (number of coupled accumulators + index/error state). Report the observed
  dimension. The script should explicitly flag that this is an _upper witness_, not a
  proven lower bound (the essay is honest that minimality is an open problem).
- **G5.3 — Non-minimal engine cross-check (ties to PI_RCC).** Implement the x+sin(x)
  cubic engine from the PI*RCC companion \_as a generator VM* and confirm it carries
  MORE live state than the 3–4 minimal figure for π — directly illustrating the
  essay's "the table tracks the floor; specific engines sit on or above it" remark.
  This is the key cross-essay consistency check.
- **G5.4 — Inaccessible = O(n).** Demonstrate that a "random"/encrypted stream
  generator requires state growing with digits emitted (O(n)), contrasting with the
  O(log n) bit-width of accessible numbers.

---

## 6. BBP / Skip-Ahead (automaton-codec resonance)

- **G6.1 — Base-16 π digit extraction.** Implement the BBP formula and a `skip(n)`
  primitive that computes the n-th hex digit of π via repeated-squaring modular
  exponentiation, _without_ computing predecessors. Verify against a reference π in
  base 16.
- **G6.2 — Fast-forward is O(log n).** Confirm skip cost scales as O(log n) (modular
  exponentiation), not O(n), by timing/operation-counting against n.
- **G6.3 — Codec dependence.** Demonstrate the essay's "property of the projection, not
  the number" claim: show the base-16 generator admits skip while the base-10
  generator does not (no _known_ fast-forward). Frame the base-10 absence honestly as
  conjectural, per the essay.
- **G6.4 — Genuine periodicity vs fast-forwardable.** Contrast a truly periodic case
  (a rational, or a periodic p-adic) where skip is exact finite-orbit jumping, against
  π-in-base-16 where skip is the repeated-squaring structure — confirming the essay's
  flagged distinction that "periodicity" is shorthand that overstates the symmetry in
  the transcendental case.

---

## 7. Equality / Comparison (interval-honest, undecidable)

- **G7.1 — Predicate family.** Implement `definitely_less_than`,
  `agrees_with(digits=N)`, and `compare(max_digits=N) → {Less|Greater|Indistinguishable}`.
  Show they never claim exact equality.
- **G7.2 — Undecidability witness.** Demonstrate concretely (e.g. two different
  generators for the same constant) that `agrees_with` can only ever confirm a finite
  prefix, never decide true equality — the Rice's-theorem boundary made operational.

---

## 8. Randomness as Inaccessible Determinism

- **G8.1 — PRNG-as-number.** Wrap a small deterministic PRNG (e.g. a counter through a
  hash) as a `step`-conforming generator and show it is a perfectly valid number whose
  generator is small but _cryptographically inaccessible_ from the output.
- **G8.2 — Accessibility classification.** Provide a function/report that classifies a
  generator as accessible / cryptographically-inaccessible / computably-inaccessible,
  and emphasize (per the essay) that the distinction is epistemic, not ontological —
  the digit-driver behaves identically for all three.

---

## 9. The Two-Tier ABI (structural validation)

- **G9.1 — Struct-layout mirror.** Mirror the essay's `AutomatonVM` (fixed inline
  state[4]) and `SeriesVM` (immutable spec + mutable accumulators) as data structures,
  and verify the fork-cost claims structurally: AutomatonVM fork is a pure value copy;
  SeriesVM fork must deep-copy the accumulators.
- **G9.2 — Codec-in-payload.** Verify that the base lives in a payload/flag field and
  that swapping it does not alter the ABI or the underlying generator (re-confirms
  G0.3 at the struct level).

---

## 10. Cross-Essay PNT Consistency

- **G10.1** Confirm the same number-theoretic input `log lcm(1,...,M) = ψ(M) ~ M`
  appears in NAM's series-tier bit-width accounting exactly as in the RCC and PI_RCC
  companions. Run the shared ψ(M)/M → 1 check and assert the constant is consistent
  across all three companion scripts. This is the "load-bearing number-theoretic
  input recurs in all three" thread from the trilogy README.

---

## 11. Reporting Contract

Emit a report that, per claim, states: (claim, prototype-verified? yes/no, measured
quantity, honest caveat where the essay flags one). The headline verdicts are:
signed-digit makes addition local (G2.2), p-adic commitment is local (G3.2), the
state-dimension table holds as an _upper-witness_ hierarchy (G5), BBP skip is O(log n)
and codec-dependent (G6), equality is only prefix-decidable (G7.2), and the two-tier
fork-cost split is real (G9.1). Every "open problem" the essay flags (state
minimality, base-10 BBP absence, randomness metaphysics) must be reported as
_probed but not settled_, never as proven.
