---
specifies: main.mac
related:
  - README.md
---

# CAS Companion Goals: Rational Certificate Complexity (RCC)

This document specifies goals for an agentic coding process tasked with writing
a companion verification/experimentation script (Maxima preferred; SymPy or PARI/GP
acceptable) for the RCC essay. The aim is to **empirically validate** the cost-class
claims, **stress-test** the asymptotic bounds, and **expose** the bit-length-vs-tolerance
profiles the essay derives by hand.

The overarching principle: every Θ(·) claim in the essay should become a
_measurable curve_ that the script generates, fits, and reports against the
predicted asymptotic.

---

## 0. Infrastructure / Harness Goals

- **G0.1 — Exact rational arithmetic.** Use a backend with exact bignum rationals
  (Maxima's native rationals, or `Fraction`/`gmpy2` in Python). Never introduce
  floating point into the certificate-production path; floats may be used only for
  log-log curve fitting at the reporting stage.
- **G0.2 — Bit-length instrumentation.** Implement `bits(p/q) = floor(log2|p|) +
floor(log2|q|) + 2` as the canonical metric used everywhere. Expose it as a single
  function so the definition is auditable.
- **G0.3 — Tolerance ladder.** For each engine, sweep ε over a geometric ladder
  (e.g. ε = 10^-1, 10^-2, ..., 10^-k for k up to a configurable max). Record
  (ε, N(ε), bits) triples to a table for later regression.
- **G0.4 — Asymptotic fitter.** Given a table of (ε, metric) pairs, fit candidate
  models {log(1/ε), (1/ε)^c, (1/ε)·log(1/ε), exp-growth} and report which best
  matches, with residuals. This is the script's verdict mechanism for RC₁/RC₂/RC₃.

---

## 1. Engine Implementations (the canonical five)

Implement each as a generator producing partial sums/products with exact rationals:

- **G1.1 — Gregory–Leibniz** `π = 4·Σ(−1)^k/(2k+1)`.
- **G1.2 — Nilakantha** `π = 3 + Σ(−1)^(k+1)·4/[(2k)(2k+1)(2k+2)]`.
- **G1.3 — Wallis product** `π = 2·Π(2k)²/[(2k−1)(2k+1)]`.
- **G1.4 — Binomial series for √k** `√k = Σ C(2n,n)/4^n·(1−1/k)^n`, parametrized over
  several integers k ∈ {2, 3, 5, 10}.
- **G1.5 — Taylor series for e** `e = Σ 1/n!` (the essay's "basic e-series").

For each engine, the script must, at every truncation N, emit:
the partial sum as an exact reduced rational, its bit-length, and a _rigorous_
error bound (alternating-series bound, geometric-tail bound, or product-log-tail
bound as appropriate).

---

## 2. Convergence-Rate Verification (the N(ε) column)

- **G2.1** Verify `|π − S_N| ≤ 4/(2N+3)` for Gregory–Leibniz numerically against the
  true π (Maxima `%pi` at high precision), confirming N(ε) = Θ(1/ε).
- **G2.2** Verify Nilakantha tail = O(1/N²) ⇒ N(ε) = Θ(ε^(−1/2)).
- **G2.3** Verify Wallis log-error tail = O(1/N) ⇒ N(ε) = Θ(1/ε).
- **G2.4** Verify binomial-series geometric tail with ratio ρ = 1−1/k ⇒
  N(ε) = Θ(log(1/ε)); confirm the slope of N vs log(1/ε) equals 1/log(1/ρ).
- **G2.5** Cross-check each measured N(ε) against the fitter (G0.4) and assert the
  predicted class.

---

## 3. Denominator-Growth Verification (the load-bearing PNT step)

This is the most subtle part of the essay and deserves the most experimental scrutiny.

- **G3.1 — lcm growth.** Compute `log lcm(1,3,...,2N+1)` and `log lcm(1,...,M)` directly
  and confirm linear growth `ψ(M) ~ M` (second Chebyshev function). Plot ψ(M)/M → 1.
- **G3.2 — Naive vs tight denominator bound.** For each engine, measure the _actual_
  common-denominator bit-length of the reduced partial sum and compare it against
  both the naive O(N log N) bound and the tight O(N) bound. Report which the data
  supports. (The essay claims reduction collapses N log N to N via PNT.)
- **G3.3 — Nilakantha triple-product subtlety.** The essay flags that the lcm of
  triple products (2k)(2k+1)(2k+2) is _not_ trivially the lcm of all integers.
  Empirically measure `log lcm{ (2k)(2k+1)(2k+2) : k ≤ N }` and test whether it is
  still Θ(N) (density argument). Report the constant.
- **G3.4 — Wallis O(N log N).** Confirm the Wallis product denominator grows like
  N log N (Σ log k), i.e. the one engine where reduction does _not_ save a log factor.

---

## 4. The Classification Table (reproduce it from data)

- **G4.1** Regenerate the essay's classification table entirely from measured data:
  for each engine output (N(ε), bits-vs-ε, fitted class). Assert agreement with the
  essay's hand-derived entries.
- **G4.2 — The Wallis-vs-Gregory–Leibniz distinction.** Specifically demonstrate that
  Wallis has bit-length Θ((1/ε)·log(1/ε)) while Gregory–Leibniz has Θ(1/ε) — i.e.
  Wallis is _worse in bits despite comparable iteration count_. This is the essay's
  headline "invisible to convergence analysis" result; make it a sharp, plotted,
  side-by-side comparison.
- **G4.3 — RC₂ vs RC₃ correction.** Verify the essay's own correction: Wallis is RC₂
  (bounded by (1/ε)^(1+δ) for all δ>0), _not_ RC₃. Test that bits/( (1/ε)·(1/ε)^δ ) → 0
  for small δ.

---

## 5. The Optimality / Lower-Bound Claim

- **G5.1** Confirm the information-theoretic floor: for each engine, plot bits vs
  log2(1/ε) and verify bits ≥ log2(1/ε) always holds (no engine beats the floor).
- **G5.2** Confirm the binomial series for √k _saturates_ the floor: bits/log2(1/ε)
  → constant. This is the RC₁ optimality claim made concrete.

---

## 6. The Hypergeometric Regularity Condition (decidability check)

- **G6.1 — Term-ratio rationality checker.** Implement the decidable test: given a
  symbolic term a*n, compute a*{n+1}/a_n and check (via `ratsimp`/`factor`) that it is
  a rational function of n. Run it on all five engines and confirm each is
  hypergeometric.
- **G6.2 — AGM negative test.** Symbolically exhibit that the AGM iteration's term
  ratio involves square roots and is _not_ a rational function of the index, confirming
  AGM is excluded from the hypergeometric class. (This grounds the essay's claim that
  AGM "deserves its own classification.")
- **G6.3 — Gauss-theorem spot check.** For a couple of algebraic numbers beyond √k
  (e.g. a root of a cubic, or 2^(1/3) via a hypergeometric representation), verify the
  Θ(log(1/ε)) bit-cost extends, supporting the "RC₁ is the home of the algebraics"
  claim.

---

## 7. Stretch / Research-Facing Goals

- **G7.1 — AGM as RC₁-outside-hypergeometric.** Implement the Gauss–Legendre AGM
  iteration for π with exact rationals + interval bounds, measure its bit-vs-ε profile,
  and confirm it lands in RC₁ — demonstrating the essay's "the separation collapses
  over all engines" caveat.
- **G7.2 — Open-question probe.** Search (heuristically, with explicit confidence
  annotations) for any _hypergeometric_ engine for π or e whose measured bit-cost
  dips toward log(1/ε). The essay flags this as open; the script should be able to
  _refute_ candidate fast hypergeometric π-series (e.g. confirm Chudnovsky's per-term
  bit-cost still yields a polynomial certificate bit-length when summed naively, vs.
  when binary-split).
- **G7.3 — Benchmark-suite export.** Emit the stratified constants (by RC class) as a
  machine-readable table (CSV/JSON) usable as the "benchmark generator" the essay
  proposes, with measured bit-lengths at fixed tolerances.

---

## 8. Reporting Contract

The script must produce a single report that, for each engine, states:
(engine name, hypergeometric? yes/no, measured N(ε) class, measured bits-vs-ε class,
predicted RC class, agreement verdict). Any disagreement between measured and
predicted is the headline output — exactly as the essay frames discrepancies as
_informative rather than invalidating_.
