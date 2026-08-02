---
specifies: main.mac
related:
  - README.md
---

# CAS Companion Goals: The Simplest Increment (x + sin(x) for π)

This document specifies goals for an agentic coding process tasked with writing a
companion verification/experimentation script (Maxima preferred for the symbolic
derivative-engineering parts; PARI/GP or Python+gmpy2 acceptable for the exact
rational-certificate parts) for the PI_RCC essay.

The essay makes a mix of **symbolic claims** (Taylor cancellations, derivative
conditions, fixed-point structure) and **quantitative claims** (cubic convergence,
Θ(log(1/ε)) certificate bit-length, Θ(log log(1/ε)) iteration count). The script
should verify both kinds, and should treat the symbolic cancellations as the
foundation the quantitative claims rest on.

---

## 0. Infrastructure / Harness Goals

- **G0.1 — Symbolic Taylor engine.** Use a CAS that can compute Taylor series of sin
  symbolically about π and about a generic point (Maxima `taylor`). Keep coefficients
  exact.
- **G0.2 — Exact rational iteration.** Implement one step of x ↦ x + sin(x) where
  sin(x) is replaced by a truncated Taylor series at exact rational x, producing an
  exact rational next iterate plus a rigorous interval bound on the truncation.
- **G0.3 — High-precision reference π.** Hold a reference value of π to far more digits
  than any test demands, for measuring actual error e_n = x_n − π.
- **G0.4 — bits() metric.** Same canonical `bits(p/q)` definition as the RCC companion,
  for cross-essay consistency.

---

## 1. The Local Error Map (symbolic core)

- **G1.1 — Cubic cancellation.** Symbolically compute the error map
  `e_{n+1} = F(π+e_n) − π` where F(x)=x+sin(x), expand in e_n, and verify that the
  e_n¹ and e_n² coefficients vanish and the leading term is exactly `e_n³/6`.
  This is the essay's central calculation; reproduce it from scratch.
- **G1.2 — Identity check.** Verify `sin(π+e) = −sin(e)` symbolically and confirm it is
  the mechanism forcing the cancellations.
- **G1.3 — Even derivatives vanish.** Verify symbolically that `sin^(2j)(π) = 0` for
  several j, confirming the essay's "degeneracy up to k=∞ in even derivatives" claim,
  and that the next nonzero term after cubic is quintic.

---

## 2. The Three Conditions (not two)

- **G2.1 — Condition checker.** Implement a symbolic predicate that, given analytic g
  and target α, checks the three conditions: g(α)=0, g′(α)=−1, g″(α)=0. Confirm
  sin/π satisfies all three.
- **G2.2 — Quadratic-vs-cubic demonstration.** Construct an explicit g satisfying only
  g(α)=0 and g′(α)=−1 but with g″(α)≠0 (e.g. a perturbation of sin), run its iteration,
  and empirically confirm it converges only _quadratically_ — demonstrating that the
  third condition is genuinely independent and necessary.
- **G2.3 — Higher-order template probe.** For the order-(k+1) template requiring
  g^(j)(α)=0 for j=2..k, symbolically test how many conditions sin/π satisfies (it
  should satisfy all even ones), and characterize the resulting effective order.

---

## 3. Multiplier / Superattraction (correct the earlier error)

- **G3.1** Verify `F′(π) = 1 + cos(π) = 0`, confirming π is a _superattracting_ fixed
  point (the essay explicitly corrects an earlier "repelling" misstatement). The
  script should assert multiplier = 0, not |multiplier| > 1.
- **G3.2 — Basin experiment.** Empirically map the basin of attraction toward π: sweep
  seeds x₀ over an interval and classify which fixed point kπ each converges to.
  Confirm the essay's |x₀ − π| < π/2 heuristic basin and that other multiples of π are
  also fixed points (sin(kπ)=0).

---

## 4. Cubic Convergence (quantitative)

- **G4.1 — Error-cubing law.** From a good rational seed, iterate exactly and measure
  e*n at each step. Verify `e*{n+1} ≈ (1/6) e*n³`by checking that`log|e*{n+1}| ≈ 3·log|e_n| + log(1/6)`. Report the fitted slope (should be ≈ 3) and
  intercept (should be ≈ log(1/6)).
- **G4.2 — Digit-tripling.** Confirm the "3 → 9 → 27" digit progression: count correct
  digits per iteration and verify they (roughly) triple.
- **G4.3 — Iteration count.** Verify N(ε) = Θ(log log(1/ε)) by measuring iterations to
  reach a tolerance ladder and fitting against log log(1/ε).

---

## 5. Inner Engine: Taylor Truncation Cost

- **G5.1 — Inner convergence.** For fixed x near π, verify the Taylor series for sin
  converges super-geometrically: term ratio `|x|²/((2m+3)(2m+2)) → 0`. Confirm
  m = O(log(1/η)/log log(1/η)) terms suffice for inner tolerance η.
- **G5.2 — Denominator linearity via PNT.** Measure `log lcm(1,...,2m+1)` and confirm
  the linear (ψ(M)~M) bound, reproducing the essay's claim that the certificate
  denominator bit-length is O(m) not O(m log m). This is the SAME PNT input the RCC
  and NAM companions use — verify the constant is consistent across all three.
- **G5.3 — Inner certificate bit-length.** Confirm `bits(η) = O(log(1/η))` for the
  inner Taylor approximant: that the "log log" overhead is absorbed by the tightened
  denominator analysis. Plot and fit.

---

## 6. Composite Certificate Profile (the RC₁ claim)

- **G6.1 — Inner/outer coupling.** Implement the full engine: choose inner tolerance
  η = Θ(ε) per step, run the outer cubic recurrence, and measure the _composite_
  certificate bit-length vs ε. Verify `bits(ε) = O(log(1/ε))` — i.e. composite cost is
  in RC₁ despite the outer recurrence being non-hypergeometric.
- **G6.2 — Non-hypergeometricity of the outer recurrence.** Symbolically demonstrate
  that the outer term ratio (in the iteration index n) is NOT a rational function of n
  — confirming the essay's central RCC-vocabulary claim that this engine sits _outside_
  the hypergeometric class yet attains RC₁ composite cost.
- **G6.3 — 100-digit sanity.** Reproduce the essay's worked figure: for ε ≈ 10^−100
  with a ~20-digit Machin-style seed, confirm certificates on the order of a few
  thousand bits and ≈ log₃(100/d₀) outer iterations.

---

## 7. Comparison Against Classical Engines

- **G7.1 — Not Newton.** Implement Newton for sin(x)=0, giving x\_{n+1}=x_n−tan(x_n),
  and contrast: confirm it needs both sin and cos and a division, whereas x+sin(x)
  needs neither derivative evaluation nor division. Compare convergence orders.
- **G7.2 — Chudnovsky gap.** Implement (or invoke) Chudnovsky's series and confirm the
  essay's honest claim that x+sin(x) is a polynomial factor slower in practice for
  large-scale computation, despite both being optimal-bit-length per certificate.

---

## 8. The Continua Tower (illustrative / symbolic)

These are more conceptual; the script should provide _concrete witnesses_ where possible.

- **G8.1 — Closure-operator demos.** Implement small concrete instances of the
  least-fixed-point promotion operators:
  - ℤ ⇒ ℚ via inversion ι: close {a few integers} under inversion + ring ops and
    watch it saturate.
  - ℚ ⇒ ℚ̄ via root extraction: adjoin √2, then a root of a cubic, showing the
    Φⁿ chain.
    The goal is an executable illustration of "saturation = fixed point of a monotone
    operator," not a full algebraic-closure implementation.
- **G8.2 — Step-rule-as-design-surface.** Demonstrate the essay's "derivative
  engineering" thesis: hold the analytic layer fixed and show that _tuning_ g (not
  enlarging the alphabet) changes convergence order — directly tying §2 to §8.

---

## 9. Research-Facing / Open Questions

- **G9.1 — Template instantiation search.** Attempt to find analytic g with rational
  Taylor coefficients satisfying the three conditions for a _different_ constant
  (e/2, ζ(3), Γ(1/3), or a chosen algebraic number). Report success/failure honestly;
  the essay flags this as open, so a negative result is a valid, informative output.
- **G9.2 — Certificate-vs-cohabitation probe.** Operationalize the "certifies vs merely
  converges" distinction: for a smooth map with a fixed point at an arithmetically
  interesting location but with no rational step rule, show that no controlled-bit-cost
  rational certificate emerges — contrasting with x+sin(x) where it does.

---

## 10. Reporting Contract

Emit a report stating, per claim: (claim, symbolic-or-numeric, verified? yes/no,
measured value vs predicted, residual). The headline verdicts are:
cubic cancellation confirmed (G1.1), superattraction multiplier = 0 (G3.1),
composite cost in RC₁ (G6.1), and outer recurrence non-hypergeometric (G6.2).
