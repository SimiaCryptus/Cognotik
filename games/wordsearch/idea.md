# Predictive Markov Wordsearch Generator

## 1. Overview

A Progressive Web App (PWA) that generates wordsearch puzzles whose
filler letters are not random, but are predicted by a Markov model
trained on a reference text. The goal is to produce grids whose
background letters read like plausible fragments of natural language
in **every** direction (horizontal, vertical, diagonal; forwards and
backwards), making the hidden target words harder to spot.

## 2. Goals & Non-Goals

### Goals

- Train an order-N Markov model from arbitrary reference text.
- Place a user-supplied set of target words on the lattice.
- Fill remaining cells using directional Markov predictions combined
  via configurable strategies.
- Ship as a static, installable PWA (HTML + modular ES6, no build step
  required).

### Non-Goals

- Server-side processing (everything runs client-side).
- Solving / auto-finding words (generation only, for now).
- Multi-language morphology (we operate on raw character sequences).

## 3. Definitions

| Term            | Meaning                                                        |
| --------------- | -------------------------------------------------------------- |
| Lattice / Grid  | 2D array of cells, each holding a single character.            |
| Direction       | One of 8 unit vectors: N, S, E, W, NE, NW, SE, SW.             |
| Order (N)       | Number of preceding characters used as Markov context.         |
| Context         | The string of up to N known neighbouring characters in a line. |
| Prediction      | A probability distribution over the next character.            |
| Adjacency score | Count of already-filled neighbours around an empty cell.       |

## 4. Architecture

```
src/
  markov/
    MarkovModel.js      # train + predict next-char distributions
    textPipeline.js     # normalise / tokenise reference text
  grid/
    Grid.js             # lattice data structure + cell access
    directions.js       # 8 direction vectors + helpers
    placement.js        # place target words randomly w/o conflict
  fill/
    adjacency.js        # order cells by adjacency score
    combiners.js        # combine multiple distributions
    filler.js           # main fill loop
  ui/
    app.js              # bootstrap, event wiring
    render.js           # draw grid to DOM/canvas
    controls.js         # config form (order, combiner, size, words)
  pwa/
    sw.js               # service worker (offline cache)
  index.js              # entry point
index.html
manifest.webmanifest
```

## 5. Component Specifications

### 5.1 MarkovModel

- `train(text, order)`: builds nested frequency maps
  `context -> { char -> count }`.
- `predict(context)`: returns a normalised `Map<char, prob>`.
  Falls back to shorter contexts (back-off) when an exact context is
  unseen, down to the order-0 unigram distribution.
- Serialisable to/from JSON for caching.

### 5.2 Grid & Directions

- `Grid(width, height)` stores cells; `get/set(x, y)`, `inBounds(x, y)`.
- `directions.js` exports the 8 vectors and a helper to read the
  context string of length ≤ N preceding a cell along a direction.

### 5.3 Placement

- Randomly position each target word along a random direction,
  rejecting placements that conflict with already-placed letters
  (unless overlapping letters match).
- Mark placed cells as **locked** (never overwritten by the filler).

### 5.4 Fill Order (Adjacency)

- Maintain a priority queue of empty cells keyed by adjacency score.
- Cells with more filled neighbours are filled first; ties broken
  randomly. Re-score neighbours after each fill.

### 5.5 Prediction Combination

For a target empty cell, for each of the 8 directions that has a
defined (non-empty) preceding context, request a prediction. Then
combine the resulting distributions using a configurable method:

| Combiner  | Description                                         |
| --------- | --------------------------------------------------- |
| `product` | Multiply probabilities (logarithmic, AND-like).     |
| `sum`     | Average / weighted sum (OR-like).                   |
| `max`     | Take the strongest single directional vote.         |
| `vote`    | Each direction votes for its argmax; majority wins. |

The combined distribution is sampled (or argmax-selected per config)
to choose the cell's letter.

### 5.6 UI

- Controls: grid size, Markov order, combiner method, sampling mode,
  reference text input/upload, target word list.
- Render grid (highlight locked target letters in debug mode).
- Regenerate / export (text, PNG) actions.

### 5.7 PWA

- `manifest.webmanifest` + service worker caching app shell for
  offline use. Installable on desktop/mobile.

## 6. Algorithm (Fill Loop)

```
place target words (locked cells)
compute initial adjacency scores for empty cells
while empty cells remain:
    cell = highest adjacency score (random tie-break)
    dists = []
    for each direction d:
        ctx = read context length<=N before cell along d
        if ctx not empty:
            dists.push(model.predict(ctx))
    combined = combine(dists, config.combiner)
    cell.char = select(combined, config.sampling)
    update adjacency scores of cell's neighbours
```

## 7. Configuration Defaults

| Option    | Default    |
| --------- | ---------- |
| grid size | 15 × 15    |
| order (N) | 3          |
| combiner  | `product`  |
| sampling  | `weighted` |
| back-off  | enabled    |

## 8. Implementation Plan (Milestones)

1. **M1 — Core model**: `MarkovModel` train/predict + back-off, with
   unit tests.
2. **M2 — Grid primitives**: `Grid`, `directions`, context reading.
3. **M3 — Placement**: random non-conflicting word placement + locks.
4. **M4 — Fill engine**: adjacency ordering + combiners + filler loop.
5. **M5 — UI**: controls, rendering, regenerate/export.
6. **M6 — PWA**: manifest + service worker + install flow.
7. **M7 — Polish**: presets, sample reference texts, accessibility.

## 9. Testing Strategy

- Unit tests for model back-off, combiner math, placement conflicts.
- Property test: every locked target word is readable post-fill.
- Visual/manual QA for generated grid plausibility.

## 10. Stretch Goals

- Per-direction weighting of contributions.
- Difficulty estimation heuristics.
- Shareable puzzle URLs (encode grid + word list).
