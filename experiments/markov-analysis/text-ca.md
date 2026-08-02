# Text as a Cellular Automaton

Idea: the ability to replace a text token with another text token based on the
rules matching the neighborhood smells VERY much like 1d cellular automata.

This note brainstorms how to translate the bidirectional Markov prediction
model into a CA-style ruleset and simulator, and collects expectations,
questions, and threads worth pulling.

---

## 1. The Core Analogy

A classic **1D cellular automaton** (à la Wolfram) is a line of cells, each
holding a state from a finite alphabet. Time advances in discrete steps; every
cell is simultaneously updated by a **local rule** that reads a fixed
**neighborhood** (typically the cell and its immediate left/right neighbors)
and outputs the cell's next state.

Map this onto text:

| CA concept            | Text equivalent                                  |
| --------------------- | ------------------------------------------------ |
| Cell                  | A token position                                 |
| Cell state            | The token (from the corpus vocabulary)           |
| Alphabet              | The vocabulary (may be huge for words)           |
| Neighborhood          | The surrounding tokens (left + right context)    |
| Local rule            | "Given neighbors, what token belongs here?"      |
| Generation / timestep | One full sweep of replacements                   |
| Radius `r`            | Half the Markov order (context window on a side) |

The bidirectional Markov model _already computes_ the local rule: for a token
at position `i`, it knows the most probable token given the left context
(forward model) and right context (backward model). The **top-1 prediction**
is exactly a deterministic CA rule; the **full distribution** is a
**stochastic** CA rule.

---

## 2. Deriving the Ruleset

### 2.1 Deterministic rule

For each cell, look at its neighborhood (say radius `r` = order `n`), and
replace the center token with the model's `argmax`:

```
next[i] = argmax_w  combine( P_f(w | left_ctx), P_b(w | right_ctx) )
```

This is a giant lookup table indexed by neighborhood — precisely a CA rule,
just with an astronomically large rule space (|V|^(neighborhood size) entries)
instead of Wolfram's 256. We never materialize the full table; we query the
model on demand.

### 2.2 Stochastic rule

Instead of `argmax`, **sample** from the combined distribution:

```
next[i] ~ combine( P_f(· | left_ctx), P_b(· | right_ctx) )
```

A **temperature** knob reshapes the distribution:

- `T → 0` ⇒ deterministic (argmax), sharp, likely converges/freezes.
- `T = 1` ⇒ sample at model's native confidence.
- `T → ∞` ⇒ uniform noise, text dissolves.

### 2.3 Update policy

Which cells update, and using what neighbor states?

- **Synchronous** — every cell computes its next state from the _current_
  generation, then all flip at once. Classic CA. Uses "old" neighbors.
- **Asynchronous** — update cells one at a time (random or scan order); later
  cells in the sweep see already-updated neighbors. This resembles Gibbs
  sampling and tends to be more stable / convergent.
- **Stochastic subset** — each step, update a random fraction `ρ` of cells
  (like `asynchronous` but batched). A dial between the two extremes.

### 2.4 Boundary conditions

- **Fixed** — clamp the ends (or use sentinel start/end tokens as walls).
- **Periodic** — wrap the tape into a ring; left of position 0 is the last
  token. Nice for studying pure dynamics without edge effects.
- **Reflective** — mirror the neighborhood at the edges.

---

## 3. Building the Simulator

This slots naturally onto the existing app (`markovModel`, `bidirectional`,
`scoring`). The model already answers "top-N given context"; the CA layer just
drives repeated rewriting.

### 3.1 New module sketch

```
src/
ca/
  caEngine.js       # holds the tape, applies one generation
  caRule.js         # wraps the model into a local rule (det/stochastic)
  caView.js         # renders generations (heatmap rows / spacetime)
  caControls.js     # play/pause/step, temperature, policy, radius
```

### 3.2 `caRule` interface

```js
export function makeRule(model, opts) {
  // opts: { combine, temperature, topN, deterministic }
  return function localRule(tape, i) {
    const left = tape.slice(Math.max(0, i - opts.radius), i);
    const right = tape.slice(i + 1, i + 1 + opts.radius);
    const dist = model.combinedDistribution(left, right, opts.combine);
    return opts.deterministic ? argmax(dist) : sample(reheat(dist, opts.temperature));
  };
}
```

### 3.3 `caEngine` step

```js
step(tape, rule, policy);
{
  if (policy === 'sync') {
    const next = new Array(tape.length);
    for (let i = 0; i < tape.length; i++) next[i] = rule(tape, i);
    return next;
  }
  if (policy === 'async') {
    const order = shuffledIndices(tape.length);
    for (const i of order) tape[i] = rule(tape, i); // in place
    return tape;
  }
  // stochastic subset...
}
```

### 3.4 Visualization: the spacetime diagram

The killer visual for 1D CA is the **spacetime diagram**: each generation is a
row, time flows downward. For text:

- Each row = the tape at generation `t`.
- Each cell = a token chip, colored by _something_:
- **change** — did it flip this step? (great for spotting gliders/activity)
- **surprise** — its probability under the model (reuse existing heatmap).
- **stability** — how many steps since it last changed.
- Hover/click a cell to inspect its neighborhood and the rule's distribution.

Character-level tokenization makes the tightest, most CA-like picture (fixed
cell width, dense grid). Word-level is more readable but ragged.

---

## 4. Expectations & Hypotheses

What might we actually see when we let text "evolve"?

- **Fixed points (still lifes).** With a deterministic rule the tape should
  quickly fall into a configuration where every token is already the argmax of
  its neighbors — a self-consistent text. It may be gibberish that the model
  nonetheless finds locally unsurprising: a "least-surprising" attractor.
- **Limit cycles (blinkers).** Positions that oscillate between two tokens
  because each makes the _other_ the preferred choice. Bidirectional
  combination could easily produce such 2-cycles.
- **Melting toward corpus clichés.** Low temperature should smear the test
  text toward the most common n-grams of the corpus — stock phrases, filler
  words, the corpus's "gravity wells."
- **Domains and walls.** Regions locked into different stable patterns,
  separated by boundaries that may drift — analogous to CA domain walls.
- **Edge of chaos.** Somewhere between frozen (low `T`) and noisy (high `T`)
  there may be a critical temperature where structured, glider-like activity
  persists longest. Finding it would be the most interesting result.
- **Convergence speed vs. order.** Higher order `n` = more constrained rule =
  faster freezing but into more corpus-like text.

### Quantities to track over time

- Mean combined probability / perplexity of the whole tape (should _rise_ /
  _fall_ as it self-consistifies).
- Fraction of cells that changed this step ("activity").
- Edit distance from the original test text ("how far has it wandered?").
- Vocabulary entropy of the tape.

Plot these against generation number to characterize the dynamics.

---

## 5. Interesting Directions

- **Seed with noise.** Start from random tokens and watch whether the rule
  _organizes_ them into corpus-like text — CA as a generative model / denoiser.
  This is essentially masked-language-model Gibbs sampling wearing a CA hat.
- **Seed with a single token** on an otherwise empty/sentinel tape and watch
  it grow outward, like a 1D crystal.
- **Rule mixing.** Different regions governed by models trained on different
  corpora — a "reaction" at the interface where two styles meet.
- **Perturbation / Lyapunov.** Flip one token, run two copies, and measure how
  fast they diverge — a text analog of CA sensitivity / chaos classification.
- **Wolfram-style classification.** Bucket the observed behavior into the four
  classes (homogeneous, periodic, chaotic, complex) as a function of
  temperature, order, and update policy.
- **Totalistic-ish rules.** Instead of exact neighborhood matching, use the
  model's backoff as a natural "totalistic" softening — coarser contexts act
  like summarized neighborhoods.

---

## 6. Open Questions

- **Combine strategy as rule character.** How differently do `min`, `max`,
  `average`, and `geometric-mean` behave as CA rules? `min` (both directions
  must agree) feels like it enforces strong local consistency and may freeze
  fast; `max` is permissive and may stay lively.
- **What is "the" neighborhood?** Forward uses left context, backward uses
  right — the _combined_ rule reads both sides, radius `n` each way. Should the
  two radii be independent?
- **Sampling reproducibility.** A seeded PRNG is essential for repeatable
  spacetime diagrams and for perturbation experiments.
- **Performance.** One generation = one model query per cell. For a 1000-token
  tape over 500 generations that's 500k queries. The existing worker
  infrastructure should handle it; consider batching a whole generation into a
  single worker message and streaming rows back for animation.
- **Do fixed points mean anything linguistically?** Is a self-consistent tape
  a meaningful "summary" of the corpus's local statistics, or just mush?
- **Reversibility.** Standard text CA here is irreversible (many neighborhoods
  map to the same token). Is there a construction that's reversible, and would
  it be interesting?

---

## 7. Minimal First Experiment

To get a signal fast, before building the full spacetime UI:

1. Reuse the char tokenizer + an order-2..4 model on a smallish corpus.
2. Take a short test string as the initial tape (periodic boundaries).
3. Deterministic argmax rule, synchronous update.
4. Run 50 generations; log each as a text line.
5. Eyeball for freezing vs. oscillation; measure per-step change fraction.

If that shows any structure, add temperature + the spacetime heatmap view and
start hunting for the edge of chaos.
