# Markov Analysis Webapp — Specification

## 1. Overview

A fully client-side, static HTML web application (no backend) that lets a user
upload or paste a text corpus, builds **bidirectional Markov models** from it,
and then analyzes a second "test" text by scoring each character (or token)
with a probability derived from those models. Results are rendered inline as a
color-coded heatmap, with interactive popups showing the top-N predicted
replacements at each position.

The app is delivered as **static HTML with modular ES6** (native ES modules,
no build step required). It must remain functional when served from a plain
static file server or opened via a local dev server.

---

## 2. Goals & Non-Goals

### 2.1 Goals

- Pure static delivery: `index.html` + ES6 modules + CSS. No server-side code.
- Handle reasonably large corpora (~1 MB of text) without freezing the UI.
- Build forward and backward (bidirectional) Markov models.
- Score arbitrary test text against the models per character/token.
- Visualize scores as an inline heatmap over the analyzed text.
- Offer interactive popups showing top-N predicted replacements per position.
- Support both character-level and simple token-level models.
- Expose all key parameters via a configuration UI.

### 2.2 Non-Goals

- No neural / ML-heavy models (Markov n-grams only).
- No persistence beyond optional `localStorage` for settings.
- No multi-user / collaboration features.
- No dependency on a bundler (though one may optionally be added later).

---

## 3. User Stories

1. As a user, I can **upload a `.txt` file** or **paste text** to define a corpus.
2. As a user, I can **configure model parameters** (order, tokenization, top-N,
   scoring options) before or after building the model.
3. As a user, I can **build the model** and see progress feedback for large inputs.
4. As a user, I can **paste test text** into an analysis area and run analysis.
5. As a user, I can **see a heatmap** coloring each character/token by its
   modeled probability (low probability = "surprising" = highlighted).
6. As a user, I can **click a highlighted position** to open a popup listing the
   top-N most probable replacements with their probabilities.
7. As a user, I can **switch between character-level and token-level** analysis.

---

## 4. Functional Requirements

### 4.1 Corpus Input

- **File upload** via `<input type="file" accept=".txt,text/plain">`.
- **Paste/type** into a large `<textarea>`.
- Display corpus size (characters, tokens, estimated memory).
- Warn (but allow) if corpus exceeds a soft limit (e.g. 2 MB).
- Normalize line endings to `\n`; optional lowercasing (config).

### 4.2 Tokenization Strategies

The app supports pluggable tokenizers. Minimum set:

- **Character**: each Unicode code point is a token.
- **Whitespace word**: split on `\s+`, keep punctuation attached.
- **Word + punctuation**: words and punctuation as separate tokens
  (regex-based, e.g. `[\p{L}\p{N}]+|[^\s\p{L}\p{N}]`).
- Tokenizers are ES modules exposing a common interface:
  ```js
  export const tokenizer = {
    id: 'char',
    label: 'Character',
    tokenize(text) {
      /* returns Array<string> */
    },
    // spans: map tokens back to [start,end) offsets in source text
    tokenizeWithSpans(text) {
      /* returns Array<{token, start, end}> */
    },
  };
  ```

### 4.3 Markov Model Construction

- Configurable **order** `n` (context length), default `n = 2`.
- Build **forward model**: `P(tokenᵢ | tokenᵢ₋ₙ … tokenᵢ₋₁)`.
- Build **backward model**: `P(tokenᵢ | tokenᵢ₊₁ … tokenᵢ₊ₙ)`.
- Store counts as nested maps: `context → { nextToken → count }`.
- Precompute or lazily compute totals per context for probability lookup.
- Optional **smoothing**: add-k (Laplace) smoothing with configurable `k`
  (default `k = 0` = no smoothing; unseen = fallback probability).
- Handle sequence boundaries with sentinel start/end tokens.

### 4.4 Scoring / Analysis

For each position `i` in the test text:

- Compute **forward probability** `P_f(tokenᵢ | left context)`.
- Compute **backward probability** `P_b(tokenᵢ | right context)`.
- Combine into a single score via a configurable strategy:
  - `forward`, `backward`, `average`, `min`, `max`, `geometric-mean`.
- Handle unseen contexts via **backoff**: reduce order until a known
  context is found, or fall back to a floor probability.
- Produce, per position:
  ```js
  {
    index, token, start, end,
    pForward, pBackward, pCombined,
    topReplacements: [{ token, p }, ...] // length ≤ N
  }
  ```

### 4.5 Top-N Replacements

- For each position, compute the top-N most probable tokens given context.
- `N` is configurable (default 5).
- Combine forward+backward candidate rankings using the chosen combine
  strategy (rank the union of forward and backward candidates).

### 4.6 Visualization (Heatmap)

- Render analyzed text inline, wrapping each token in a `<span>`.
- Background color derived from `pCombined`:
  - High probability → neutral / transparent.
  - Low probability → strong highlight (e.g. red/orange).
- Color scale configurable (linear vs log probability, palette choice).
- Hovering a span shows a lightweight tooltip with the probability.

### 4.7 Replacement Popups

- Clicking a span opens a popup anchored near it, listing top-N replacements:
  - Each entry: candidate token + probability (bar + numeric).
  - The original token is marked.
- Popup is keyboard-accessible (focus trap, `Esc` to close).

### 4.8 Configuration Panel

Exposed, live-editable parameters:

| Parameter          | Type     | Default   | Notes                          |
| ------------------ | -------- | --------- | ------------------------------ |
| Tokenizer          | select   | character | char / whitespace / word+punct |
| Model order `n`    | int 1–8  | 2         | context length                 |
| Lowercase corpus   | bool     | false     |                                |
| Smoothing `k`      | float    | 0         | add-k Laplace                  |
| Combine strategy   | select   | average   | fwd/bwd/avg/min/max/geo        |
| Backoff enabled    | bool     | true      |                                |
| Floor probability  | float    | 1e-6      | for unseen                     |
| Top-N replacements | int 1–20 | 5         |                                |
| Color scale        | select   | log       | linear / log                   |
| Palette            | select   | heat      |                                |

- Settings persisted to `localStorage`.
- Changing tokenizer/order/smoothing requires model rebuild (prompt user).
- Changing scoring/visualization params re-runs analysis only.

---

## 5. Non-Functional Requirements

### 5.1 Performance

- Model building for ~1 MB corpus should complete in a few seconds.
- Heavy work (model build, scoring) runs in a **Web Worker** to keep the
  UI responsive; communicate via structured messages.
- Provide progress events (percentage) for long operations.
- Use typed structures / interned token IDs where beneficial for memory.

### 5.2 Compatibility

- Modern evergreen browsers (Chrome, Firefox, Safari, Edge).
- Native ES modules (`<script type="module">`), no transpilation required.
- Graceful degradation message if `Worker` / modules unsupported.

### 5.3 Accessibility

- Keyboard navigable controls and popups.
- Sufficient color contrast; heatmap not the _only_ signal (title/tooltip
  also conveys probability).
- ARIA roles for popups and interactive spans.

### 5.4 Code Quality

- Modular ES6, one responsibility per module.
- No global state leakage; explicit imports/exports.
- Documented public module interfaces.

---

## 6. Architecture

### 6.1 Module Layout

```
experiments/markov-analysis/
  index.html
  styles/
    main.css
  src/
    main.js               # app bootstrap, wires UI + workers
    config.js             # config schema, defaults, persistence
    ui/
      corpusPanel.js      # upload/paste + stats
      configPanel.js      # parameter controls
      analysisView.js     # heatmap rendering + spans
      popup.js            # replacement popup component
    tokenizers/
      index.js            # registry
      charTokenizer.js
      whitespaceTokenizer.js
      wordPunctTokenizer.js
    model/
      markovModel.js      # build + probability + top-N
      bidirectional.js    # wraps forward+backward models
      scoring.js          # combine strategies, backoff
    workers/
      modelWorker.js      # builds model off-thread
      analyzeWorker.js    # scores test text off-thread
    util/
      colorScale.js       # probability → color
      dom.js              # small DOM helpers
```

### 6.2 Data Flow

1. User provides corpus → `corpusPanel` emits text.
2. `main` sends corpus + config to `modelWorker`.
3. Worker builds bidirectional model, returns serializable model (or keeps
   it in the worker and answers scoring queries).
4. User provides test text → `main` sends text + model handle to
   `analyzeWorker` (or reuses `modelWorker`).
5. Worker returns per-position scoring array.
6. `analysisView` renders heatmap; clicks open `popup` with top-N.

### 6.3 Worker Strategy

- Preferred: **keep the model inside a single worker**; the main thread sends
  analysis requests and receives results, avoiding costly model serialization.
- Fallback: build model on main thread if workers unavailable.

---

## 7. Core Data Structures

```js
// Interned tokens
// tokenId: number, tokenTable: string[]

// Forward counts:
//   Map<contextKey, Map<tokenId, count>>
// contextKey = tokenIds joined (e.g. "12,7") or a nested map.

// Model summary returned to UI (small):
{
  tokenizerId, order, tokenCount, uniqueTokens,
  corpusLength, builtAt
}

// Per-position analysis result (array):
{
  index, token, start, end,
  pForward, pBackward, pCombined,
  topReplacements: [{ token, p }]
}
```

---

## 8. Algorithms

### 8.1 Build (per direction)

- Iterate tokens with a sliding window of size `n`.
- Increment `counts[context][next]`.
- Track per-context totals.

### 8.2 Probability with Backoff

```
function prob(context, token):
    for len in n down to 0:
        ctx = last `len` tokens of context
        if counts has ctx:
            total = totals[ctx]
            c = counts[ctx][token] or 0
            if smoothing k > 0:
                return (c + k) / (total + k * V)
            if c > 0:
                return c / total
            # else continue backing off
    return floorProbability
```

### 8.3 Combine

- `average`: `(pF + pB) / 2`
- `geometric-mean`: `sqrt(pF * pB)`
- `min` / `max`: elementwise
- `forward` / `backward`: single direction

### 8.4 Top-N

- Gather candidate next-tokens from forward context and previous-tokens from
  backward context; compute combined score for each candidate; sort desc;
  take N. Always include the actual token in the returned metadata.

---

## 9. UI / UX Details

- **Layout**: two-column (corpus + config on left, analysis on right) or
  stacked on narrow screens.
- **Status bar**: model state (not built / building… / ready), token count.
- **Heatmap legend**: shows probability→color mapping.
- **Popup**: appears on click, dismiss on outside click / `Esc`.
- **Responsiveness**: debounce re-analysis on config changes.

---

## 10. Milestones

1. **M1 — Scaffold**: static HTML, module layout, config panel, persistence.
2. **M2 — Tokenizers**: char + whitespace + word/punct with spans.
3. **M3 — Model**: forward/backward build, probability + backoff (main thread).
4. **M4 — Analysis + Heatmap**: scoring, inline rendering, color scale.
5. **M5 — Popups + Top-N**: interactive replacement popups.
6. **M6 — Workers**: move build/scoring off-thread with progress.
7. **M7 — Polish**: accessibility, large-file handling, legend, docs.

---

## 11. Open Questions

- Should the model be serializable for save/load, or worker-resident only?
- How to rank top-N when forward and backward disagree strongly?
- Sentence segmentation for boundary sentinels in token mode?
- Memory ceiling strategy for very high `n` on large corpora?
