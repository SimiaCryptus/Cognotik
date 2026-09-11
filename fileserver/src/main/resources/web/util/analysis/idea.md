# A Generalized Agentic Software Analysis Engine

## Design Notes for a Multi-Language, Lean-Backed Verification Pipeline

---

## 1. The Problem Statement, Stated Precisely

Formal verification of real software fails at scale for a reason that is easy to
state and hard to fix: **source code is written against an enormous body of
implicit semantics, and proof assistants accept none of it implicitly.**

Consider the canonical trivial example:

```rust
fn add(x: u32, y: u32) -> u32 {
    x + y
}
```

To a Rust programmer this function is self-evident. To Lean it is meaningless
until someone states, explicitly:

- that `u32` denotes the integers modulo 2³², not ℕ;
- that `+` on `u32` panics on overflow in debug builds and wraps in release
  builds, and that the build profile is therefore part of the specification;
- that the function is total and terminating;
- that it performs no allocation, no I/O, no observable side effect;
- that no other thread can interfere with its evaluation;
- that argument evaluation order is left-to-right and irrelevant here;
- that the calling convention preserves the abstract values of `x` and `y`.

None of this is written down. All of it is required. The distance between what
is written and what must be proven is the **semantic gap**, and every serious
verification project — Verus, Kani, μRust/Isabelle for the AWS Nitro hypervisor,
F* for HACL\* — is fundamentally an engineering answer to that gap.

Verus answers it by embedding a specification language *inside* Rust and
compiling Rust-with-specs into a verification IR discharged by an SMT solver.
Kani answers it by refusing to close the gap at all: it bounds the state space
and model-checks. μRust answers it by paying, once, the enormous cost of
formalizing a Rust subset in Isabelle/HOL and then hand-proving above it.

Each of these answers is **language-specific and largely manual**. The design
below proposes a third answer: **automate the extraction of implicit semantics
using an agentic pipeline, and use Lean as a universal, auditable backend.**

The central claim is not that this is easy. It is that the work decomposes into
two very different kinds of effort, and that only one of them scales with the
number of programs:

1. **Per-language work** — formalizing a language's defined semantics. Expensive,
   deep, done once, amortized across every program in that language.
2. **Per-program work** — surfacing the assumptions a *particular* program relies
   on. Repetitive, pattern-driven, and — this is the bet — *largely automatable*
   by agents that recognize semantic patterns rather than language syntax.

Historically these were conflated, so the cost of (1) was paid again and again.
Separating them is the design's load-bearing idea.

---

## 2. Reframing: Three Buckets of Assumptions

Not all "implicit assumptions" are the same kind of thing, and treating them
uniformly is what makes verification feel impossible. The engine explicitly
sorts every assumption into one of three buckets, and each bucket gets a
different mechanism.

### Bucket A — Language-Defined Semantics

These are already written down, just not in Lean. Integer promotion rules in C.
Borrow-checking and lifetime rules in Rust. The Python object model and
exception propagation order. Java's memory model and its happens-before edges.

**Mechanism:** a per-language Lean *semantics library* (`LangSem`), authored once
by humans with tool assistance, versioned, tested, and reused by every analysis.
This is the expensive, deep artifact. It is *not* regenerated per program and is
*not* trusted to an agent.

**Key property:** the cost is O(languages), not O(programs).

### Bucket B — Programmer-Implied Assumptions

These are in the programmer's head and nowhere else:

- "this function is only ever called with a non-empty slice";
- "this counter never exceeds the number of connected clients, so it can't
  overflow";
- "this global is written only by the initialization thread";
- "this pointer is valid for the duration of the call";
- "this loop terminates because `hi - lo` strictly decreases".

**Mechanism:** automated *assumption mining* by agents, followed by
**materialization into the source** as structured comments or annotations, and
**translation into Lean hypotheses**. Critically, these are round-tripped back
into the repository so that humans can review, correct, or reject them. An
assumption that lives only inside a proof tool is invisible; an assumption that
lives in a comment above the function is a reviewable engineering artifact.

```rust
/// @assume(caller) xs.len() > 0
/// @assume(env)    xs.len() < 2^31        // enforced by the connection limiter
/// @decreases      hi - lo
fn binary_search(xs: &[u32], key: u32) -> Option<usize> { ... }
```

**Key property:** this is where the agentic platform earns its keep. These
assumptions are *pattern-shaped*, not language-shaped — loops need variants,
recursion needs measures, mutation needs aliasing facts, arithmetic needs range
facts — and patterns generalize across languages.

### Bucket C — Idealizations

These are deliberate, knowing simplifications: "assume unbounded integers",
"assume sequential consistency", "assume no undefined behavior", "assume the
allocator never fails", "assume `f64` is ℝ".

**Mechanism:** a **semantics ladder** with a refinement relation between rungs.
Prove the algorithm correct under an idealized semantics, then *mechanically
compute what must additionally hold* for the real semantics to refine the ideal
one. Those residual conditions are exactly the interesting output: they tell you
precisely where overflow, UB, or races actually matter to your correctness
argument, as opposed to where they merely could in principle.

**Key property:** idealization converts an intractable proof into a tractable
proof plus a *precise list of the lies you told*. That list is more valuable to
an engineering team than a binary verdict.

This three-bucket decomposition is the conceptual spine of the entire system.
Everything downstream is machinery for populating, tracking, discharging, and
reporting on the buckets.

---

## 3. Design Principles

Before the architecture, the constraints it must satisfy.

**P1 — Assumptions are first-class artifacts.** Every assumption has an
identity, a provenance (inferred / human-authored / language-spec), a
justification, a scope, a confidence, and a status. They live in a versioned
ledger and are synchronized with source comments. A verification result is
meaningless without the assumption set it was relative to.

**P2 — Agents propose; the kernel disposes.** No agent output is trusted.
Agents generate candidate IR, candidate assumptions, candidate specifications,
candidate proof scripts. Every one of those is checked by a deterministic
artifact — the Lean kernel, a differential test oracle, a translation validator.
The trusted computing base must not include a language model.

**P3 — Partial results are the normal case.** The engine must produce useful
output when it cannot close a proof: "verified modulo these 4 assumptions",
"here is a concrete counterexample", "this obligation reduced to this residual
condition". A tool that only says *yes* or *timeout* is unusable in practice.

**P4 — Everything is incremental and cacheable.** Proof obligations are keyed by
a content hash of the IR fragment plus the assumption context. A one-line change
must not re-verify the module.

**P5 — Language-specific work is quarantined.** All language knowledge lives in
front-end plugins and `LangSem` libraries. The core — IR, extraction patterns,
obligation generation, orchestration, repair loop — is language-agnostic.

**P6 — Refinement over monolithic proof.** Always prefer proving a small thing
in a clean model plus a refinement, over proving a big thing in a dirty model.

**P7 — The output is evidence, not a verdict.** The deliverable is an auditable
bundle: IR, assumptions, obligations, proofs, counterexamples, and the gaps.

---

## 4. Architecture Overview

The engine is a layered pipeline with a feedback loop. Data flows down; failures
flow back up.

```
┌──────────────────────────────────────────────────────────────────────┐
│  L0  Ingestion & Project Model                                       │
│      repos, build config, dependency graph, entry points, profiles   │
└──────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────────────────────────────────────────┐
│  L1  Front Ends → CSIR (Canonical Semantic IR)                       │
│      Rust / C / C++ / Python / Java / Go front ends                  │
│      SSA, explicit effects, explicit types, explicit control flow    │
│      ── translation validator ──                                     │
└──────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────────────────────────────────────────┐
│  L2  Semantic Extraction Engine                                      │
│      pattern recognizers · abstract interpretation · dynamic         │
│      invariant mining · agentic proposal · assumption typing         │
└──────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────────────────────────────────────────┐
│  L3  Assumption Ledger + Specification Store                         │
│      identity · provenance · scope · confidence · status             │
│      round-trips to source as @assume / @ensures / @decreases        │
└──────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────────────────────────────────────────┐
│  L4  Lean Emission                                                   │
│      LangSem_<lang>  (ideal ⊑ relaxed ⊑ real semantics ladder)       │
│      program embedding · obligation generation · refinement side     │
│      conditions                                                      │
└──────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────────────────────────────────────────┐
│  L5  Proof Orchestration                                             │
│      Lean tactics · SMT · bounded model checking · property testing  │
│      portfolio scheduling · budget control · proof cache             │
└──────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────────────────────────────────────────┐
│  L6  Counterexample Triage & Agentic Repair                          │
│      classify failure · lift model to source · propose fix           │
│      (spec? assumption? code? lemma?) · re-enter pipeline            │
└──────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────────────────────────────────────────┐
│  L7  Evidence Bundle & Reporting                                     │
│      proof artifacts · assumption manifest · residual risk report    │
└──────────────────────────────────────────────────────────────────────┘
```

Two cross-cutting services sit beside the pipeline:

- **The Differential Oracle** (§9) — maintains semantically paired
  implementations across languages and uses disagreement as a signal.
- **The Proof Cache & Lemma Library** — content-addressed storage of discharged
  obligations and reusable lemmas, shared across projects.

---

## 5. Layer 1: The Canonical Semantic IR

Everything depends on the IR, so it deserves the most care.

### 5.1 What CSIR Must Make Explicit

CSIR is not a compiler IR optimized for codegen. It is a **verification IR
optimized for making implicit things visible**. Its design rule: *if a proof
obligation could depend on it, it must be a syntactic node, not a convention.*

| Concern | Implicit in source | Explicit in CSIR |
|---|---|---|
| Control flow | `for`, `while`, `try`, `break`, exceptions | labeled basic blocks, explicit edges, explicit unwind edges |
| Data flow | mutation, shadowing | SSA with φ-nodes; memory as explicit state |
| Types | inference, coercion, promotion | fully elaborated; every coercion an explicit node |
| Arithmetic | `a + b` | `add<u32, wrap>` / `add<u32, trap>` / `add<int>` |
| Memory | pointers, references | typed regions, explicit load/store on a heap value |
| Effects | I/O, allocation, panics | effect row on every operation |
| Concurrency | threads, locks, atomics | explicit fork/join, acquire/release, atomic ordering |
| Termination | none | explicit `decreases` slot (possibly empty) |
| Errors | exceptions, `?`, `panic!` | explicit alternate control edges |

Concretely, `x + y` on `u32` in a Rust debug profile becomes something like:

```
%t = arith.add %x, %y : u32 [overflow = trap, on_trap -> ^panic_bb]
```

The overflow policy is not a footnote; it is an operand. This one decision
eliminates an entire class of "the proof was about a different program than the
one that runs" failures.

### 5.2 Effect Rows

Every operation carries an effect row drawn from a small algebra: `pure`,
`alloc`, `read(region)`, `write(region)`, `diverge`, `panic`, `io`,
`sync(order)`. Effect rows are what allow the engine to say "this subexpression
is pure, therefore evaluation order is irrelevant, therefore I need not
axiomatize it" — an enormous automatic simplification that in hand-written
formalizations is usually a manual argument.

### 5.3 Translation Validation

The front end is a compiler, and compilers have bugs. Worse, if any part of the
front end is agent-assisted, it is *unreliable by construction*. CSIR therefore
ships with **translation validation** rather than translation trust:

1. **Executable CSIR.** A reference interpreter for CSIR exists. For any test
   input, `interpret(CSIR(P))` must agree with `run(P)` under the real language
   toolchain. Existing test suites become translation-validation suites for free.
2. **Differential fuzzing.** Generate inputs; compare source execution against
   CSIR interpretation; report divergence as a front-end bug, not a program bug.
3. **Structural invariants.** Type preservation, SSA dominance, effect-row
   soundness (an operation may not under-declare effects), CFG well-formedness —
   all checked mechanically.

This is the concrete answer to "you can always translate syntax, you cannot
translate meaning": you cannot *guarantee* meaning is preserved, but you can
*continuously test* that it is, and you can make the tests cheap.

---

## 6. Layer 2: The Semantic Extraction Engine

This is the layer that does not exist in today's tools and is the reason the
system needs to be agentic.

### 6.1 The Central Insight: Patterns, Not Languages

Assumption extraction is **pattern-specific, not language-specific**. The
question "what invariant does this loop maintain?" has the same shape whether the
loop is written in Rust, Python, or Go, once both are in CSIR. That is why one
extraction engine can serve many front ends.

A partial catalogue of recognizers:

| CSIR pattern | Obligation raised | Assumption to mine |
|---|---|---|
| back edge in CFG | termination | loop variant (`decreases`), loop invariant |
| self/mutual recursion | termination | well-founded measure |
| `arith.add [overflow=trap]` | no-panic | operand range bound |
| indexing / slicing | in-bounds | index vs. length relation |
| `load` after `store` via distinct pointers | soundness | aliasing / separation fact |
| division, modulo | no-trap | divisor ≠ 0 |
| `unwrap`, `expect`, non-null cast | no-panic | definedness precondition |
| shared mutable state + fork | data-race freedom | ownership discipline / lock protects region |
| lock acquire ordering | deadlock freedom | global lock order |
| float comparison | spec fidelity | tolerance or exactness assumption |
| external call | framing | effect summary of callee |
| unbounded input | resource | size bound from environment |

Each recognizer is a small, testable, deterministic program that emits an
*obligation* and a *slot* for the assumption or specification that would
discharge it. The agents fill slots. They never invent the slots.

That distinction matters enormously for trust: the *coverage* of the analysis is
determined by deterministic code, so the engine knows what it has not
considered. Only the *content* of proposed facts comes from a model, and every
such fact is subsequently checked.

### 6.2 A Portfolio of Inference Techniques

Assumption mining is not one algorithm; it is a portfolio, ordered by cost and
reliability:

1. **Type and effect inference** (cheap, sound). Ranges from types, nullability,
   mutability, purity.
2. **Abstract interpretation** (cheap, sound, imprecise). Interval, octagon, and
   congruence domains give overflow bounds and index bounds for a large fraction
   of straight-line and simple-loop code — often the majority of obligations in
   real code.
3. **Symbolic execution / weakest preconditions** (medium cost, precise, path-
   explosive). Used to compute the exact residual condition for an obligation
   when abstract interpretation is too coarse.
4. **Dynamic invariant mining** (Daikon-style; cheap, *unsound*, high recall).
   Run the existing test suite against the CSIR interpreter, observe candidate
   relations, propose them. Unsound proposals are fine — they are candidates.
5. **Agentic proposal** (expensive, unsound, highest expressive power). An LLM
   agent reads the function, its callers, its docstrings, its tests, its commit
   history and its neighbors, and proposes invariants and preconditions in a
   structured schema. This is the only technique that can produce *conceptual*
   invariants — "this is a red-black tree, so black-height is uniform" — from
   naming, comments, and idiom.
6. **Call-site aggregation.** Collect the actual argument patterns at every call
   site to propose preconditions the callee can rely on, and to flag call sites
   that would violate a proposed precondition.
7. **Human authoring.** Always available; always highest priority.

The critical architectural point: **techniques 4 and 5 are unsound and that is
acceptable**, because their output enters the ledger as a *candidate assumption*
with provenance, and is then either (a) discharged into a theorem by the prover,
(b) surfaced to a human for ratification, or (c) refuted by a counterexample.
Unsound proposal followed by sound checking is the pattern that makes the whole
system tractable.

### 6.3 Assumption Typing

Every candidate assumption is classified along several axes, because how it is
handled downstream depends entirely on its type:

- **Kind:** precondition · postcondition · loop invariant · representation
  invariant · frame condition · termination measure · environmental fact ·
  idealization.
- **Discharge strategy:** *provable* (should become a theorem — try to prove it),
  *checkable* (insert a runtime assertion), *enforced elsewhere* (guaranteed by a
  validator, config, or type at a boundary), *axiomatic* (genuinely assumed —
  must be human-ratified), *idealization* (tracked on the semantics ladder).
- **Scope:** this call site · this function · this module · this deployment.
- **Confidence:** with provenance-weighted priors.

An assumption whose strategy is *axiomatic* and whose confidence is
model-generated is a **red-flag item** in the final report. The engine's honesty
depends on never letting such items become invisible.

---

## 7. Layer 3: The Assumption Ledger

The ledger is the system's memory and its audit trail. It is a versioned,
content-addressed store, checked into the repository alongside code.

### 7.1 Schema

```yaml
- id: asm:core::search::binary_search#len_bound
  kind: precondition
  scope: function
  target: core::search::binary_search
  statement:
    lang: csir-pred
    text: "xs.len < 2^31"
  provenance:
    source: agent:assumption-miner@v3
    corroborated_by:
      - abstract-interpretation:interval    # bound holds at 41/43 call sites
      - callsite-aggregation
    human_ratified: true
    ratified_by: "a.charneski"
    ratified_at: "2025-03-14"
  discharge:
    strategy: enforced-elsewhere
    justification: >
      Input length is bounded by MAX_CONNECTIONS (2^16) in the
      connection limiter; see net::limiter::MAX_CONNECTIONS.
    residual_risk: "Holds only if the limiter is not bypassed."
  status: active
  used_by:
    - obl:core::search::binary_search#mid_no_overflow
    - obl:core::search::binary_search#idx_in_bounds
  source_anchor:
    file: src/core/search.rs
    line: 44
    comment_form: "/// @assume(env) xs.len() < 2^31  [asm:...#len_bound]"
```

### 7.2 Round-Tripping to Source

The ledger synchronizes bidirectionally with source comments. This is not
cosmetic. It produces three real benefits:

- **Reviewability.** Assumptions appear in pull requests. A reviewer who sees
  `@assume(env) xs.len() < 2^31` appear in a diff can object *at review time*,
  which is the only time objections are cheap.
- **Locality.** The assumption lives where the code lives, so it moves when the
  code moves and rots visibly when the code changes.
- **Drift detection.** If code changes and an anchored assumption's justification
  no longer type-checks against the new CSIR, the assumption is marked *stale*
  and every proof depending on it is invalidated.

This directly implements the intuition that many implicit assumptions "should be
added as code comments." The comment is not documentation *about* the proof; the
comment *is* the proof input, and the ledger is its index.

### 7.3 The Ledger as the Unit of Trust

The final verification claim is never "this program is correct." It is:

> Under `LangSem_rust@v2.1` at rung `real-release`, module `core::search`
> satisfies its specification, **relative to** assumption set `{asm:…#len_bound,
> asm:…#nonempty}`, of which 1 is human-ratified environmental and 0 are
> unratified model-generated axioms.

That sentence is the product.

---

## 8. Layer 4: Lean Emission and the Semantics Ladder

### 8.1 The `LangSem` Library

For each supported language, a Lean library defines a state model and an
operational (or, where feasible, denotational/weakest-precondition) semantics
for CSIR *as instantiated by that language*. Because CSIR already makes effects,
overflow policy, and memory explicit, `LangSem_rust` and `LangSem_c` differ far
less than Rust and C do — they differ mainly in which CSIR nodes are reachable
and what invariants the front end guarantees.

A sketch:

```lean
namespace LangSem

structure Region where
  base : Addr
  size : Nat
  perm : Perm

structure Heap where
  regions : Finmap Addr Region
  bytes   : Addr → Option Byte

structure State where
  heap    : Heap
  locals  : Finmap Var Value
  effects : EffectLog          -- I/O trace, allocation log, panic flag
  threads : ThreadPool

/-- Big-step evaluation of a CSIR operation, parameterized by the
    semantics rung `S`. -/
inductive Step (S : Rung) : State → Op → Outcome State → Prop
  | addWrap  : ∀ σ x y,  S.intModel = .wrapping →
      Step S σ (.add .u32 x y) (.ok σ (Value.u32 ((x + y) % 2^32)))
  | addTrap  : ∀ σ x y,  S.intModel = .trapping → x + y ≥ 2^32 →
      Step S σ (.add .u32 x y) (.panic σ .overflow)
  | addIdeal : ∀ σ x y,  S.intModel = .unbounded →
      Step S σ (.add .u32 x y) (.ok σ (Value.int (x + y)))
  -- …

end LangSem
```

Note the parameterization by `Rung`. This is the mechanism for Bucket C.

### 8.2 The Semantics Ladder

Rather than one semantics per language, the engine defines a *ladder* of
semantics ordered by a refinement relation:

```
Rung 0  ideal        unbounded ints · no UB · no panics · sequential ·
                     total allocator · reals for floats
Rung 1  bounded      machine ints, wrapping · still no UB · sequential
Rung 2  trapping     machine ints with defined panic-on-overflow ·
                     panics are observable outcomes
Rung 3  memory       real memory model · aliasing · UB is a distinguished
                     outcome
Rung 4  concurrent   real memory model with weak/atomic orderings
Rung 5  deployed     plus allocator failure, resource exhaustion, clock,
                     external world
```

with, for each adjacent pair, a Lean-level theorem schema:

```lean
/-- Rung `n+1` refines rung `n` provided the residual conditions `C` hold. -/
theorem refine_step
    (p : Program) (C : SideConditions)
    (h : SideConditionsHold C p) :
    Refines (Rung.succ n) (Rung n) p
```

### 8.3 Prove Ideal, Compute the Delta

The workflow this enables is the practically important one, and it is the direct
formalization of "assume this language behaves ideally with respect to X, then
show whether that was required":

1. **Prove the algorithm at Rung 0.** Binary search returns the index of `key`
   iff `key ∈ xs`, assuming `xs` sorted and integers unbounded. This proof is
   about the *algorithm*, is short, is reusable, and is often within reach of
   automation or a library lemma.

2. **Attempt to lift to Rung 1.** The refinement obligation generates residual
   side conditions, mechanically:
   ```
   residual: ∀ lo hi, lo ≤ hi < 2^32 → lo + (hi - lo) / 2 < 2^32
   ```
   This is exactly the famous binary-search overflow bug, produced not by
   inspiration but by a mechanical delta computation. If the code were written
   `(lo + hi) / 2`, the residual would be `lo + hi < 2^32`, which is *false* in
   general — and the engine reports the counterexample, the failing rung, and
   the exact source expression responsible.

3. **Attempt to lift to Rung 3.** Residuals about slice validity and aliasing
   appear. In safe Rust most are discharged by front-end guarantees; in C they
   become real obligations.

4. **Report the ladder.** The output states the highest rung at which the
   property holds unconditionally, and for each higher rung, the exact residual
   conditions and their disposition (proved / assumed / refuted / runtime-checked).

This has an important consequence for the economics of the whole enterprise:
**a partially-lifted proof is a genuinely valuable artifact.** "Correct at Rung 2,
with two unproved aliasing residuals listed here" is enormously more informative
than either "verified" (against what model?) or "timed out."

It also localizes hard problems. Concurrency reasoning is only invoked where a
Rung 3 → Rung 4 lift actually fails; purely sequential modules never pay for it.

### 8.4 Program Embedding and Obligation Generation

Program embedding is a deterministic, non-agentic translation from CSIR to a
Lean term in a deep embedding:

```lean
def binary_search : Program :=
  .fn "binary_search" [("xs", .slice .u32), ("key", .u32)] (.opt .usize)
    (.block #[
       .assign "lo" (.const 0),
       .assign "hi" (.len (.var "xs")),
       .loop
         (.lt (.var "lo") (.var "hi"))
         (.decreases (.sub (.var "hi") (.var "lo")))
         (.block #[ … ])
     ])
```

Deep embedding (as opposed to compiling into native Lean functions) is chosen
deliberately: it lets the same program term be interpreted at *every* rung of the
ladder, which is precisely what the refinement machinery needs. It costs proof
automation ergonomics, which is bought back with custom simp sets, a
weakest-precondition calculus over CSIR, and rung-specific tactics.

Obligations are then generated as Lean goals with explicit assumption context:

```lean
theorem obl_mid_no_overflow
    (asm_len_bound : xs.length < 2^31)          -- from the ledger
    (asm_sorted    : Sorted xs) :
    WP (Rung.trapping) binary_search
       (fun outcome => outcome ≠ .panic) := by
  …
```

The ledger entries appear as named hypotheses. Provenance is thereby preserved
*inside the Lean proof term*, which means the evidence bundle can mechanically
answer "which unratified assumptions does this theorem depend on?" by walking the
proof term's dependencies. This is a strong auditability property and is worth
significant engineering to preserve.

---

## 9. Cross-Language Differential Analysis

The observation that agentic platforms make cross-language translation cheap
opens a capability that a single-language verifier cannot have.

### 9.1 Paired Implementations as an Oracle

Given a function in language A, the engine can generate a semantically-intended
port in language B, lower both to CSIR, and then compare. Disagreement is
informative in a way that is hard to obtain otherwise:

- **Behavioral divergence under differential fuzzing** indicates either a bad
  translation *or* a place where the two languages' implicit semantics differ —
  which is exactly a place where an implicit assumption is load-bearing. A Python
  port that gives a different answer than the C original on large inputs has
  found the C program's reliance on wrapping arithmetic.

- **Structural divergence in extracted obligations.** If the Rust version raises
  an in-bounds obligation and the Python version does not, that is because
  Python's list semantics raise an exception instead — the obligation did not
  disappear, it changed *kind*. Comparing obligation sets across ports is a
  productive way to discover obligations a single front end forgot to raise.

- **Assumption transfer.** Invariants mined in one language transfer to the port
  as high-prior candidates, so mining effort compounds across languages.

### 9.2 Equivalence as a Proof Tactic

More ambitiously, the ports become a *proof strategy*. Prove that the optimized
production implementation refines a clean reference implementation, and prove the
reference implementation correct at Rung 0. This is the classical two-step
refinement structure, but the agentic layer supplies the reference implementation
automatically rather than requiring a human to write one:

```
optimized_rust  ⊑  reference_impl  ⊨  Spec
```

Bit-twiddling, cache-friendly, or SIMD code is often hopeless to verify directly
against a specification, but quite tractable to verify against a naive reference
— particularly with an SMT-backed equivalence check on bounded inputs plus an
inductive argument for the general case.

### 9.3 The Discipline Required

Differential analysis is a *signal generator*, not a proof. The architecture must
be strict about this: generated ports are never trusted, never shipped, and never
appear in the trusted computing base. They exist to produce candidate
assumptions, candidate obligations, and counterexamples — all of which are then
subjected to the same checking as anything else.

---

## 10. Layer 5: Proof Orchestration

### 10.1 A Portfolio, Not a Prover

Each obligation is dispatched to a portfolio of backends, ordered by cost:

1. **Proof cache lookup** — content-addressed by (obligation, assumption context,
   `LangSem` version, rung). Free.
2. **Syntactic discharge** — `decide`, `simp` with rung-specific simp sets,
   `omega` for linear arithmetic, `bv_decide` for bitvector goals. Milliseconds.
3. **SMT** — export to Z3/CVC5 via a checked bridge. When the SMT solver
   succeeds, the engine attempts to reconstruct a Lean proof term; if
   reconstruction fails, the result is recorded as *SMT-attested*, a strictly
   weaker status that is reported as such. Trust levels are never silently
   merged.
4. **Bounded model checking / property testing** — for obligations that are not
   proved, *refute cheaply*. A counterexample within bounds is far more useful
   than a timeout, and often the correct outcome is "this obligation is false and
   here is the input".
5. **Lemma library search** — retrieval over a corpus of previously proved
   obligations and standard mathematical lemmas.
6. **Agentic proof search** — an agent proposes a Lean tactic script or a lemma
   decomposition. Output is checked by the kernel, so failure is free and success
   is sound. This is the only place where an LLM touches proof content, and the
   kernel makes it safe.
7. **Human escalation** — with full context: the goal, the failed attempts, the
   nearest library lemmas, the counterexample if any.

### 10.2 Budget Control

Every obligation carries a budget in wall-clock and token cost, scaled by
criticality (derived from module annotations, reachability from entry points, and
whether the code is security-relevant). The scheduler is a straightforward
priority queue over (criticality × probability-of-success ÷ expected-cost). This
is unglamorous and is the difference between a system that finishes and one that
does not.

### 10.3 Incrementality

Proof state is keyed by content hashes of:

- the CSIR fragment (post-normalization, so formatting changes are free),
- the transitive assumption context,
- the `LangSem` version and rung,
- the specification.

A change to one function invalidates only obligations whose keys involve it.
Assumption edits invalidate precisely the obligations that cite them — which the
ledger's `used_by` field makes an O(1) lookup. `LangSem` version bumps invalidate
everything, which is correct and is why `LangSem` releases are deliberate.

---

## 11. Layer 6: Counterexample Triage and the Repair Loop

A failed obligation is the beginning of the interesting work, not the end. The
triage agent classifies failure into one of five categories, each with a
different repair action:

| Diagnosis | Evidence | Repair |
|---|---|---|
| **Real bug** | concrete counterexample that survives replay against the *actual* compiled program | file a defect with the reproducing input; do not touch specs |
| **Missing assumption** | counterexample is unreachable in practice; call-site analysis confirms | mine and propose a precondition; propagate to callers as an obligation |
| **Wrong specification** | counterexample is intended behavior | revise spec; flag for human ratification (this is the most dangerous category) |
| **Missing lemma** | goal is true but automation stalled | propose decomposition; add lemma to library |
| **Front-end defect** | CSIR interpretation disagrees with real execution on the counterexample | file front-end bug; quarantine the module |

The **counterexample replay step is essential and non-negotiable**. Every
model-level counterexample is lifted back to concrete source-level inputs and
executed against the real program under the real toolchain. This closes the loop
on the deepest risk in the entire design: that the engine is proving things about
a model that does not correspond to reality. A counterexample that reproduces is
a real bug; one that does not reproduce is evidence of a modeling defect and is
routed to the front-end team rather than to the application team.

The specification-revision path deserves particular suspicion. An agent that is
allowed to weaken specifications until proofs succeed will do exactly that. The
architecture therefore enforces:

- specification changes require human ratification before the proof status can
  become "verified";
- the report always shows specification churn per module;
- a monotonicity check flags any specification edit that strictly weakens the
  property, and such edits are surfaced prominently rather than folded into the
  diff.

---

## 12. Layer 7: The Evidence Bundle

The engine's output is a signed, reproducible bundle:

- **CSIR** for every analyzed unit, with translation-validation results and
  coverage.
- **The assumption manifest** — every assumption relied upon, with provenance,
  ratification status, and discharge strategy, partitioned into *proved*,
  *runtime-checked*, *externally enforced*, and *axiomatic*.
- **Lean proof terms** for discharged obligations, kernel-checkable
  independently of the engine.
- **Attestation records** for anything discharged by SMT without proof
  reconstruction, clearly distinguished from kernel-checked results.
- **Counterexamples**, with reproducing inputs and replay results.
- **The ladder report** — for each property, the highest rung proved and the
  residual conditions at each higher rung.
- **Residual risk summary** — the honest, front-page list: unratified
  assumptions, unproved residuals, unanalyzed code, timed-out obligations,
  quarantined modules.

The bundle must be independently checkable: a third party with Lean, the
`LangSem` library, and the bundle can re-verify every kernel-checked claim
without running the engine, and can read exactly what was assumed.

---

## 13. Agent Roles

The "agentic" character of the system is best understood as a set of narrow,
schema-constrained roles rather than a general assistant. Each has a typed input,
a typed output, and a mechanical checker.

| Agent | Input | Output | Checked by |
|---|---|---|---|
| **Normalizer** | source AST + language plugin | CSIR fragment | interpreter differential + structural invariants |
| **Assumption Miner** | CSIR + obligation slots + context | candidate assumptions (schema) | prover / counterexample / human ratification |
| **Spec Synthesizer** | CSIR + docs + tests + naming | candidate specifications | human ratification + test agreement |
| **Porter** | CSIR + target language | ported source | differential fuzzing |
| **Proof Engineer** | Lean goal + context + library | tactic script | Lean kernel |
| **Triage** | failed obligation + counterexample | diagnosis + repair plan | replay against real program |
| **Lemma Librarian** | proof corpus | generalized reusable lemmas | Lean kernel |
| **Reviewer** | proposed ledger diff | risk annotations, questions | human |

Every one of these is *unreliable*, and every one is *harnessed by a checker that
is not*. That is the entire trust architecture, and it is what distinguishes this
design from "ask a model whether the code is correct."

---

## 14. Worked Micro-Example

To make the pipeline concrete, trace a single function end to end.

**Source (Rust):**

```rust
fn mean(xs: &[u32]) -> u32 {
    let mut total = 0u32;
    for &x in xs { total += x; }
    total / xs.len() as u32
}
```

**L1 — CSIR** makes explicit: a loop with a back edge; `arith.add<u32, trap>`
inside it (debug profile); a `div<u32>` with a trapping zero-divisor edge; a
`cast<usize→u32>` that may truncate; a slice iteration whose bounds are
guaranteed by the front end.

**L2 — Obligations raised** (deterministically, five of them):
`O1` loop termination; `O2` `total + x` does not overflow; `O3` `xs.len() ≠ 0`;
`O4` `xs.len()` fits in `u32`; `O5` the result matches the specification of
"mean".

**L2 — Assumptions mined:**
- `O1` discharged automatically: iteration over a finite slice, measure =
  remaining elements. No assumption needed.
- `O2`: abstract interpretation yields `total ≤ Σxs`, which is unbounded. The
  miner proposes `asm:sum_bound : Σ xs < 2^32`, corroborated by call-site
  analysis showing all callers pass length ≤ 1024 arrays of values < 2^20.
- `O3`: proposes `asm:nonempty : xs.len() > 0`, corroborated by two of three call
  sites guarding on emptiness — and flags the third call site as a potential
  panic.
- `O4`: discharged from `asm:nonempty` plus the length bound.
- `O5`: the spec synthesizer proposes `result = ⌊(Σ xs) / |xs|⌋`, drawn from the
  function name and the single test case.

**L3 — Ledger** records all four, source-anchors them as comments, and marks
`asm:sum_bound` as requiring ratification because its justification rests on
call-site aggregation rather than an enforced invariant.

**L4 — Lean, Rung 0** (unbounded integers): `O5` is proved in a handful of
tactic steps; `O2`, `O3`, `O4` do not exist at this rung.

**Rung 0 → 2 lift:** residuals are exactly `Σ xs < 2^32` and `|xs| > 0` — the
two mined assumptions, recovered mechanically. This is the confirmation signal:
the delta computation independently rediscovers the assumptions the miner
proposed, which raises confidence in both.

**L6 — Triage:** the unguarded third call site is replayed with an empty slice
and panics against the real binary. That is a **real bug**, reported with a
reproducing input — and note that it was found by the *assumption* machinery, not
by the specification.

**L7 — Report:** "`mean` is correct at Rung 2 relative to `asm:sum_bound`
(unratified, environmental) and `asm:nonempty` (violated at
`stats.rs:87` — defect filed)."

That report is the shape of the product: a proof, a precisely bounded set of
assumptions, and a real defect, all from a function nobody wrote a specification
for.

---

## 15. Failure Modes and Mitigations

An honest design names the ways it breaks.

**F1 — Model/reality divergence.** The proof is about a CSIR program that is not
the shipped program. *Mitigations:* translation validation against existing test
suites; differential fuzzing; mandatory counterexample replay; conservative
front-end quarantine of unsupported constructs (never silently approximate — if
a construct is unmodeled, the enclosing unit is marked unanalyzed).

**F2 — Assumption laundering.** The system proves everything by assuming
everything. *Mitigations:* assumption budget per module, reported as a headline
metric; ratification requirement for axiomatic assumptions; prominent display of
unratified-assumption counts; automatic detection of assumptions that are
*vacuous* (imply False) or *trivializing* (make the postcondition
unconditionally true).

**F3 — Specification drift.** Specs get weakened until they pass. *Mitigations:*
monotonicity checking on spec edits; human ratification; spec churn metrics;
mutation testing — deliberately inject bugs and confirm the specification catches
them. A specification that survives no mutants is not a specification.

**F4 — Automation cliff.** Everything works until it hits real code with
pointers, callbacks, and reflection. *Mitigations:* explicit rung reporting so
partial results remain valuable; effect-row-driven modularity so pure code is
cheap; aggressive use of the reference-implementation refinement pattern;
honest "unanalyzed" reporting.

**F5 — Cost explosion.** Agentic proof search is expensive. *Mitigations:*
portfolio ordering with cheap methods first; hard budgets; caching; lemma
library growth; concentrating spend on high-criticality code.

**F6 — `LangSem` bugs.** The formalization of the language is wrong.
*Mitigations:* test `LangSem` against the language's own conformance suite via
the CSIR interpreter; independent review; versioning with full invalidation on
change; treating `LangSem` as the highest-assurance component in the codebase,
with human authorship and no agentic generation.

**F7 — False confidence in the report.** A green dashboard hides a mountain of
assumptions. *Mitigation:* the residual-risk summary is the *first* page, not an
appendix, and verification status is always rendered as a pair (property, assumption
set), never as a bare checkmark.

---

## 16. Evaluation

The system should be measured on:

- **Obligation coverage** — fraction of deterministically raised obligations that
  reach a terminal status (proved, refuted, ratified-assumed) rather than
  timing out.
- **Assumption quality** — human ratification rate for mined assumptions
  (proposals that survive review), and refutation rate (proposals shown false).
  Both matter: high refutation with high ratification means the miner is
  productive; high proposal with low ratification means it is generating noise.
- **Bug yield** — real defects found per thousand lines, with replay
  confirmation, benchmarked against fuzzing and static analysis on the same
  corpus.
- **Rung distribution** — what fraction of properties reach Rung 2, 3, 4.
- **Mutation-detection rate** — the strongest single indicator that
  specifications are meaningful.
- **Incremental cost** — CPU and token cost of re-verification after a
  representative one-line change. This determines whether the system can live in
  CI.
- **Front-end fidelity** — differential-fuzzing divergence rate per language.

---

## 17. Phasing

**Phase 1 — Single language, sequential, Rung 0–2.** Rust or a C subset. CSIR,
front end, interpreter, translation validation, abstract interpretation, the
ledger, `LangSem` for rungs 0–2, obligation generation, SMT portfolio. Target
memory-safety and panic-freedom obligations, which are numerous, mechanical, and
where automation is strongest. Deliver the ledger and comment round-trip early —
the assumption artifacts are valuable even before proofs are.

**Phase 2 — Agentic mining and specification synthesis.** Add the miner and spec
synthesizer, the ratification workflow, dynamic invariant mining against existing
test suites, and mutation testing for specification quality.

**Phase 3 — The ladder.** Formalize the refinement theorems, add Rung 3 (memory),
and ship the ladder report. This is where the "prove ideal, compute the delta"
capability becomes real and where the system starts producing insight rather than
just verdicts.

**Phase 4 — Second front end and differential analysis.** A second language
validates the language-agnosticism claim and enables paired-implementation
analysis, reference-implementation refinement, and cross-language assumption
transfer.

**Phase 5 — Concurrency (Rung 4) and agentic proof search.** The hardest rung
and the most expensive backend, deliberately last, applied selectively where the
Rung 3 → 4 lift actually fails.

---

## 18. Relationship to Existing Work

This design does not replace Verus, Kani, or Isabelle-based formalization; it
generalizes and composes their strategies.

- **From Verus:** the insight that specifications belong in the source language
  and that fast, automated, SMT-backed feedback is what makes verification
  usable. The engine's ledger-to-comment round trip is the same instinct, applied
  to *assumptions* rather than only to *specifications*, and extended to
  languages that will never get a Verus.
- **From Kani:** the insight that refutation is often more valuable than proof,
  and that bounded checking with no annotation burden is the right first
  interaction. The engine keeps bounded model checking in its portfolio precisely
  for this reason.
- **From μRust/Isabelle:** the insight that a real formal semantics is
  irreducibly expensive and must be amortized. The engine's answer is to pay that
  cost once per language in `LangSem` and to attack the *per-program* cost —
  which is the cost that actually scales — with automation.
- **From the Rust standard library verification contest:** the empirical lesson
  that manual proof engineering plateaus and that automation is not a
  convenience but a precondition for scale.

The genuinely new elements are three: (1) the systematic separation of
language-defined, programmer-implied, and idealized assumptions with a distinct
mechanism for each; (2) the semantics ladder with mechanical delta computation,
which turns idealization from a cheat into a diagnostic instrument; and (3) the
disciplined use of unreliable agents as *proposers* inside a pipeline where every
proposal is disposed of by a deterministic checker.

---

## 19. Why This Is Plausible Now

Three things changed.

**Translation is cheap.** Agentic platforms make syntactic conversion and
normalization across languages nearly free. That does not close the semantic gap,
but it removes the front-end tax that used to dominate multi-language projects
and it enables differential techniques that were previously impractical.

**Proposal is cheap.** The bottleneck in verification was never checking; it was
*writing down* invariants, preconditions, measures, and frame conditions. That is
a generation problem, and generation with unreliable output is acceptable when a
kernel is doing the checking. The asymmetry between the cost of proposing an
invariant and the cost of checking one is now enormous, and it points in the
favorable direction.

**Checking is mature.** Lean's kernel, its mathematical library, and modern SMT
solvers provide a checking substrate strong enough that unreliable proposal is
safe. The trusted computing base can be kept small and legible.

The remaining hard problem is the one this design centers rather than avoids:
**formalizing what languages actually mean.** That work is real, expensive, and
irreducibly human. But it is O(languages), and the pipeline around it is
O(programs) with a small constant. That is the trade that makes the whole thing
worth building.

The honest summary of the ambition: not "verify all software," but *"make the
implicit assumptions in software explicit, reviewable, and — wherever
automation can reach — proved."* Even the first two clauses, delivered without
the third, would be worth the engine.
