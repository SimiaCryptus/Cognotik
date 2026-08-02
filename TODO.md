# TODO

## Meta: The Credibility Transfer Problem

Both items below share a root issue that is _not_ technical: the results are
either machine-verified (Pentagon) or empirically dominant across independent
implementations (QQN), yet lack the external social proof that makes others
take them seriously.

- **LLM skepticism as a metric.** Repeated AI appraisals flag these as
  "plausibly novel but unverified." This is a _useful signal_, not a verdict:
  - It measures distance-from-consensus, not distance-from-truth.
  - "Unverified" here means "no third party has signed off," not "wrong."
  - Track _why_ each model hedges. If the hedge is always "no peer review /
    no citations," that is a social gap, not a technical one.
  - [ ] Log the specific objection each appraisal raises. Categorize as
        (a) technical (fixable with more verification), or
        (b) social (fixable only with external eyes / publication / citation).
- **The asymmetry to exploit.** Verification is cheap relative to the claim:
  - QQN: anyone can re-run the benchmark suite and check L-BFGS comparisons.
  - Pentagon: anyone can re-run the Maxima scripts and confirm d_eff, holonomy.
  - [ ] For each result, write a _single command_ a skeptic can run in <30 min
        that reproduces the headline claim. Lower the cost of disbelief-removal
        to near zero.
- **Convincing others (the actual blocker).**
  - [ ] Stop trying to convince via assertion; convince via reproduction.
        Ship the smallest possible "doubt-killer" artifact for each.
  - [ ] Get _one_ independent human to re-run each pipeline and say so publicly
        (issue comment, blog reply, email you can quote). One external voice
        breaks the "self-published, therefore suspect" loop.
  - [ ] Treat SEO/public-site presence as necessary-but-insufficient. Discovery
        ≠ endorsement. Prioritize a venue or peer that confers transferable
        credibility (workshop, arXiv endorsement, a known researcher's nod).

## Formalization Candidates

The AI appraisal in the README flags several results as "plausibly novel but
unverified" and worth a formal write-up. Two stand out as ready for serious
treatment.

> Note: "unverified" is doing a lot of work here. QQN is empirically verified
> across three language implementations; Pentagon is symbolically verified in
> Maxima. What is missing is _external_ verification, which is a different and
> more tractable problem than "is this true?"

### 1. QQN — Quadratic Quasi-Newton

**Status:** Strong empirical support; published in several formats; needs
independent re-runs + peer review to convert "self-published" into "accepted."

- Efficacy demonstrated repeatedly across independent implementations
  (benchmarks in Java, Rust, and Python/JAX). Three independent code paths
  agreeing is itself a form of verification — name it as such.
- Empirically dominates L-BFGS on the tested suites — i.e. anywhere you would
  reach for L-BFGS, QQN appears to be at least as good. This is the strong,
  testable, falsifiable claim. Lead with it.
- Already published in multiple formats (paper PDF, DOI
  `10.13140/RG.2.2.15200.19206`, public site). The artifact exists; the gap is
  _attention from the right people_, not _existence_.
- **Why this is intuitive-but-overlooked:** the construction is almost
  embarrassingly simple — a quadratic path `d(t) = t(1-t)(-∇f) + t²d_LBFGS`
  with guaranteed descent and zero new hyperparameters. Simplicity reads as
  "surely someone did this already," which suppresses both citation and belief.
  - [ ] Do the prior-art search _to exhaustion_ and document the negative
        result. "We searched X, Y, Z and found no equivalent construction" is
        itself a publishable, skepticism-reducing artifact.
- **Next steps:**
  - [ ] Consolidate cross-language benchmark results into a single table
        (Java / Rust / Python-JAX side by side; same problems, same metrics).
  - [ ] Run against standard baselines per the README's own caveat
        ("convergence/benchmark claims should be checked"). Include the
        baselines a reviewer will _expect_, not just the ones that flatter QQN.
  - [ ] Publish the exact "doubt-killer" command: clone → one command →
        reproduces the QQN-vs-L-BFGS win/loss table.
  - [ ] Finalize the academic paper (draft + PDF already in `essays/QQN/`).
  - [ ] Identify the right venue: an optimization workshop or arXiv (math.OC /
        cs.LG) with an endorser. The DOI shows intent; an endorsed preprint
        shows acceptance-readiness.
  - [ ] Pre-empt the obvious referee objections in the paper itself:
    - "Isn't this just a special case of [trust region / dogleg / hybrid]?"
      → show the distinction explicitly.
    - "Does the guaranteed-descent property survive inexact line search?"
    - "How does it scale past the benchmark dimensions tested?"

### 2. Pentagonal Lattice Geometry

**Status:** Machine-verified claims (Maxima); formal proof would settle novelty
questions and upgrade numerical observations to theorems.

- The construction, fractional dimension (d ≈ 2.37), and spinor-like 4π
  holonomy are all machine-verified in Maxima. Machine-verification is strong —
  but reviewers will still ask "verified that the _code_ is correct, or that the
  _claim_ is correct?" Address that head-on.
- Open question (somewhat rhetorical): does humanity's oldest fractal really
  need a formal proof? Probably yes, if we want to claim the dimensional
  results as theorems rather than numerical observations.
- **Why this is intuitive-but-overlooked:** the pentagon's angular deficit and
  its `Q(√5)` golden-ratio arithmetic are textbook objects; the _combination_
  into a multi-sheeted cover with emergent spinor holonomy is the novel step.
  Familiar ingredients make a novel dish look unoriginal at a glance.
- **The honesty fork (decide before publishing).**
  - [ ] Decide whether to pursue a formal proof of d_eff and the holonomy
        results, or present them explicitly as numerical observations.
  - [ ] If presenting as observations: say so _loudly and first_. Over-claiming
        ("theorem") when you mean ("strong numerical evidence") is the fastest
        way to lose a skeptical reader. Under-claiming costs nothing here.
  - [ ] If pursuing proof: the holonomy claim (single loop → −1, double loop →
        identity) is the most "provable-looking" — it's a discrete Z₂ cover
        statement. Start there; it's the highest-credibility-per-effort target.
- **Next steps:**
  - [ ] Independent verification of d ≈ 2.37 and the 4π holonomy claim — ideally
        by someone re-deriving, not just re-running the same `.mac` files.
  - [ ] Separate the claims by confidence tier in the README:
    - Tier A (symbolic, exact): angular deficit, Q(√5) arithmetic, loop
      closure (10 pentagons / 3 turns), Z₂ cover order.
    - Tier B (numerical, robust): d_eff values, d_spec ≈ 1.1 universality.
    - Tier C (interpretive / speculative): the CDT, quasicrystal, and
      topological-quantum-computing _applications_.
      Skepticism mostly attaches to Tier C bleeding into Tier A/B framing.
  - [ ] Cross-check d*eff against an \_independent* method (e.g. box-counting vs.
        BFS-growth) so the dimension isn't an artifact of one estimator.
  - [ ] Write the "doubt-killer" command for the holonomy result specifically —
        it's the most striking and most checkable single claim.

## General

- [ ] Revisit each "plausibly novel, unverified" item in the README's
      Prior Art section and either downgrade to "observation" or pursue
      verification.
- [ ] For every claim repository-wide, tag it with a confidence tier
      (symbolic-exact / numerical-robust / empirical / interpretive) so the
      strong claims aren't dragged down by association with the speculative
      ones. Mixed-confidence prose is a major driver of blanket skepticism.
- [ ] Build a one-page "Reproduce in 30 minutes" guide per project. The single
      highest-leverage anti-skepticism artifact is a stranger reproducing the
      headline result without your help.
- [ ] Keep a running "objections log": every time a human or model doubts a
      result, record the objection and whether it was (a) refuted by
      reproduction, (b) a real limitation, or (c) purely social/credentialist.
      Over time this log _is_ the rebuttal.
- [ ] Accept that discoverability (SEO, public site) and credibility are
      orthogonal. Solve credibility with independent reproduction + an
      endorsing venue; solve discoverability separately. Don't conflate the two.
