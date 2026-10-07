---
specifies: theme_design.html
related:
  - *.css
  - **/*.css
  - **/*.html
  - **/*.js
---

# Operation: derive a constraint-solved colour theme from the existing CSS

**Input.** Every stylesheet matched by `related`. HTML files in the same tree may be
read for context (which selectors sit on which backgrounds) but are not edited.

**Output.** One HTML file, `theme_design.html`, written next to the stylesheets.
Opening it in a browser restores the palette, the preview templates, the migration
guide and the evidence; the designer plays with anchors and rules, presses _Solve
themes_ and uses the header buttons to regenerate every derived artefact on demand — _Save CSS_
(`<project>-tokens.css`), _Save guide (.md)_ (`<project>-integration.md`,
the incorporation instructions addressed to a human or an agent), _Save tokens_, _Save .pcad_, and _Save harness (
.html)_ (the file itself with the current edits, so it
remains the source of truth).

The harness document covers, in order of priority:

1. an **inventory** of every colour the stylesheets currently use,
2. a **token map** that gives each distinct colour a role and a name,
3. a **Point-CAD sketch** (PCS, the `.pcad` dialect used by `demo/palette.pcad`) that
   restates the palette as a few _anchors_ plus _rules_, with one `theme` block per
   colour scheme found (or proposed),
4. a small **HTML + CSS demonstration** that consumes the custom properties the
   application generates from the sketch,
5. a description of what the application will **emit when the sketch is solved**,
6. a **migration guide** for rewriting the stylesheets against the new variables, and
7. the **evidence** (inventory, token map, relations, expected output) that justifies
   every rule — as fields of the same document, not as a separate markdown transcript.

The sketch must be precise enough to parse and solve without hand edits, and readable

enough that a designer understands _why_ each rule exists. §3 defines every field; the
short form of where each one surfaces:

| document field (§3)                                                              | in the UI                                | in the generated guide               |
|----------------------------------------------------------------------------------|------------------------------------------|--------------------------------------|
| `pcad` (top level, beside `doc`)                                                 | **Script** window                        | §11, and _Save .pcad_                |
| `project`, `prefix`, `format`, `scope`, `switchMode`, `defaultTheme`, `fallback` | **Handoff › Project & emitter**          | title, file names, _At a glance_, §3 |
| `summary`                                                                        | **Handoff › Migration guide › Summary**  | §1                                   |
| `replacements`, `plumbing`, `alpha`, `skip`, `order`                             | **Handoff › Migration guide**            | §5 – §9                              |
| `findings`                                                                       | **Handoff › Migration guide › Findings** | §10                                  |
| `preview.css`, `preview.html`                                                    | **Preview** window, CSS / HTML tabs      | §12                                  |
| `inventory`, `tokenMap`, `relations`, `expected`                                 | **Handoff › Evidence (working notes)**   | Appendix A – D                       |

A markdown document is no longer part of the deliverable: _Save guide (.md)_ generates
`<project>-integration.md` from these fields whenever someone needs it.

This operation does **not** modify any stylesheet. It produces the harness only.

---

## 1. Principles

- **Fidelity first, improvement second.** The sketch must reproduce the _existing_ design:
  every derived colour is seeded with the value observed in the CSS, and every rule
  written down is one that already holds in the CSS within tolerance (§2.6). Rules that
  the existing CSS _violates_ (typically contrast floors) are still written — that is the
  point of the exercise — but each one is listed as a finding (§3.9) so nobody is
  surprised when the solver moves a colour.
- **Few anchors, many derivations.** A theme should need to state only what a designer
  actually chooses: the page background, the brand colour, and one or two orientation
  points. Everything else is derived. A colour is an anchor only if it cannot be
  reproduced (ΔE_ok ≤ 0.02) from other points by a rule you can justify.
- **Perceptual units.** All reasoning is done in OKLab / OKLCH. Lightness steps, hue
  families and chroma matches are judged there, never on RGB bytes.
- **Accessibility as constraints, not comments.** Every text/background pair that occurs
  in the CSS gets a `contrast` (WCAG 2.x) floor. Use the highest standard threshold (3 / 4.5 / 7) the pair is meant to
  satisfy, not the value it happens to have.
- **Gamut-safe.** `gamut srgb` stays on; the emitter must never clip.
- **Nothing invented silently.** If a theme is proposed rather than found, if a colour
  was merged into another, or if a value was ignored (e.g. a shadow), say so.

---

## 2. Procedure

Work through the steps in order; each fills one or more fields of the harness document (§3). Draft the fields as you
go — they are markdown — and assemble the file in §2.11.

### 2.1 Inventory the colours

Scan every matched file. Extract each colour literal together with its context:

| record          | contents                                                                                                                                                        |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `literal`       | the text as written (`#3b5bdb`, `rgb(59 91 219)`, `hsl(…)`, `oklch(…)`, `rebeccapurple`, …)                                                                     |
| `file:line`     | location (all occurrences)                                                                                                                                      |
| `selector`      | the rule's selector, with pseudo-classes (`a:hover`, `.btn:disabled`)                                                                                           |
| `property`      | `color`, `background(-color)`, `border(-*-color)`, `outline-color`, `box-shadow`, `fill`, `stroke`, `text-decoration-color`, gradient stop, `--custom-property` |
| `alpha`         | separate from the colour (`rgba(0,0,0,.12)` → colour `#000`, alpha `0.12`)                                                                                      |
| `theme context` | enclosing `@media (prefers-color-scheme: …)`, `[data-theme=…]`, `.dark`, `:root.theme-x`, `color-scheme` declarations, or _none_                                |
| `state`         | `:hover`, `:focus(-visible)`, `:active`, `:disabled`, `[aria-*]`, or _rest_                                                                                     |
| `via`           | if the value arrived through `var(--x)`, the custom property chain that resolves it                                                                             |

Include colours defined in existing custom properties even if unused; include `currentColor`
and `transparent` only as a count (they are not colours to solve). Ignore vendor
resets and third-party files only if the document says which ones were skipped and why.

### 2.2 Normalise to OKLCH

Convert every literal to `oklch(L% C H)` with `L` to 3 decimals of a percent, `C` to 5
decimals, `H` to 2 decimals (this is the emitter's own formatting, so values compare
directly with solver output). Named colours go through their hex value. Keep the original
literal alongside; the PCS parser accepts `#rrggbb`, `rgb()`, `hsl()` and `oklch()`, and
the emitter round-trips the notation the sketch was written in, so anchors may be written
exactly as they appear in the CSS.

Record derived quantities used in later steps: `L`, `C`, `H`, linear-sRGB luminance `Y`,
and whether `C < 0.02` (a _near-neutral_, whose hue is numerically unreliable — see §7).

### 2.3 Cluster and count

Merge literals whose ΔE_ok (Euclidean distance in OKLab) is `≤ 0.02`; list the members
of each cluster. For each cluster count occurrences per property class (text / background / border / other) and per
theme context. The cluster, not the
literal, becomes a token candidate.

### 2.4 Detect themes

Themes are found where the same selector+property is declared twice with different
colours under different theme contexts. Pair those declarations; each pair is one token
observed in two themes. Decide:

- **Two or more schemes found** → one `theme` block each, named after the mechanism (`light`, `dark`,
  `high-contrast`, …). Note which mechanism the CSS uses (`prefers-color-scheme`, attribute, class) — the migration
  guide must preserve it.
- **One scheme found** → emit that as `theme light` (or `dark` if the canvas is dark)
  from data, and **propose** a second theme whose block contains only the anchors and
  orientation overrides, clearly marked `# proposed` in the sketch and listed as a
  finding. Do not invent per-token values for a proposed theme; let the solver derive them.

### 2.5 Assign roles and names

Name every cluster by how it is used. The vocabulary below is the one the demo, the
emitter and the sample stylesheet already use; extend it (`danger`, `success`, `warning`,
`info`, `focusRing`, `selection`, `codeBg`, …) rather than inventing synonyms.

| token         | typical evidence                                                           | usual role  |
|---------------|----------------------------------------------------------------------------|-------------|
| `canvas`      | `background` of `html`/`body`; the most common background                  | anchor      |
| `brand`       | primary button background, logo colour, the hue links share                | anchor      |
| `surface1..n` | container backgrounds that differ from `canvas` mostly in `L`              | derived     |
| `text`        | `color` on `body`; the highest-contrast foreground                         | derived     |
| `textMuted`   | secondary `color` with lower contrast (`.muted`, captions, placeholders)   | derived     |
| `border`      | `border-color`, dividers, `outline-color` at rest                          | derived     |
| `link`        | `a { color }`                                                              | derived     |
| `accent`      | call-to-action background not equal to `brand`; badges; highlights         | derived     |
| `*Hover`      | the `:hover`/`:active` variant of a token, differing mostly in `L`         | derived     |
| `warmth`      | _not in CSS_ — an orientation point capturing the hue surfaces lean toward | orientation |
| `readingGray` | _not in CSS_ — the hue and chroma shared by text, muted text and border    | orientation |

Roles, exactly as in the theme designer:

- `anchor` — fixed input, exported (`canvas`, `brand`, and only what is genuinely chosen).
- `orientation` — fixed input, **not** exported; exists to steer derived points (`warmth`, `readingGray`, a
  `neutralAxis`). Prefer an orientation point over baking a
  hue number into several rules.
- `derived` — free output, exported.
- `helper` — free, unexported scaffolding (rare in a palette).

Names are camelCase identifiers in the sketch; the emitter kebab-cases them:
`surface1 → --color-surface-1`, `textMuted → --color-text-muted`,
`accentHover → --color-accent-hover`. State the resulting custom-property name in the
token map so the migration guide and the demo CSS agree.

### 2.6 Derive the relations

For every candidate rule, compute the evidence from the normalised values and keep the
rule only if it holds within tolerance **in every theme found** (or is an accessibility
floor). Record the numbers in the document.

| relation                         | how to detect                                                     | tolerance              | PCS                                              |
|----------------------------------|-------------------------------------------------------------------|------------------------|--------------------------------------------------|
| even lightness ramp              | successive `dL` between `canvas, surface1, surface2, …` are equal | ±0.01 in `L`           | `dL A B = stepL` per step, `stepL` a variable    |
| hue family                       | hue angles within a small arc                                     | ±5° (±10° if `C<0.05`) | `lock H ref A B C`                               |
| equal chroma                     | `C` values equal                                                  | ±0.005                 | `lock C ref A B`                                 |
| equal lightness                  | `L` values equal                                                  | ±0.01                  | `lock L A B`                                     |
| complementary / analogous accent | shortest hue arc from `brand` ≈ 180 / ±30 / ±120                  | ±10°                   | `dH brand accent = 180`                          |
| chroma offset                    | `C(B) − C(A)` constant across themes                              | ±0.005                 | `dC A B = value`                                 |
| near-neutral                     | `C < 0.02` for the whole gray family                              | —                      | `lock C readingGray text textMuted border`       |
| text/background contrast         | WCAG ratio of every pair that occurs in the CSS                   | choose floor ≤ intent  | `contrast text canvas >= 7` (or 4.5, 3)          |
| "as close as allowed"            | a colour sits just past a threshold, not far beyond it            | —                      | `distance canvas text -> min weight 0.05`        |
| hover/active variant             | same `H` and `C`, `L` differs by a constant                       | ±0.01                  | `lock H a b`, `lock C a b`, `dL a b = hoverStep` |
| same chroma in all themes        | designer intent, e.g. accent saturation                           | —                      | `var accentC shared`, `C accent = accentC`       |

Contrast floors: `7` for body text (AAA), `4.5` for any other text incl. links and muted
text (AA), `3` for borders, icons, focus rings and large text. When the CSS currently
fails a floor, still write the floor and report the failure. When the CSS exceeds the
next floor by a wide margin and the colour is clearly "just dark", add the
`distance -> min` objective **only if** the designer is meant to accept a lighter result;
otherwise pin the observed lightness with `L text = 0.25` (see §7 — this is the most
common surprise when reading solver output).

### 2.7 Choose variables

Introduce a variable whenever the same number would otherwise appear in two rules or
should differ per theme:

```
var stepL   = -0.04            # per theme: dark themes usually want a smaller, positive step
var mutedC  = 4.5   locked     # contrast floor for secondary text
var accentC = 0.12  shared     # one unknown for all themes
var hoverStep = -0.06          # sign flips per theme
```

`locked` — the solver never changes it. Unmarked — free, per theme. `shared` — free, one
value across all themes. Give each variable a comment stating what it means and, if
free, why.

### 2.8 Write the sketch

Follow the layout of `demo/palette.pcad` exactly (header, variables, inputs, outputs,
rule groups, themes, solver, view) so diffs between projects stay readable.

- Header: `space oklab`, `units none deg`, `gamut srgb`.
- Seeds of derived points are the **observed light-theme values**; the solver's
  nearest-to-seed answer then stays close to today's design.
- Inside `theme` blocks, `point` may be omitted (`canvas at …`). A theme overrides
  anchors, orientation points and variables. Derived points may also appear there but **only as seeds** — say so in a
  comment. Dark themes almost always need these seeds (text on the light side, border on the dark side) or the solver
  satisfies the contrast
  floor from the wrong side.
- A trailing role word (`derived`, `anchor`, …) or `label "…"` on a line inside a
  `theme` block is accepted and **ignored**: an override only moves coordinates. Lines
  copied from the model therefore parse unchanged.
- Every derived point must be pinned on all three axes by some combination of rules,
  objectives and locks, or its slack must be listed as intentional.
- `solver gauss-newton iterations 200 tolerance 1e-6` and the two `view` lines from
  `palette.pcad` unless there is a reason to change them.

### 2.9 Write the demonstration

Provide the demonstration as two fields, `preview.css` and `preview.html` (raw CSS and
body markup, **no** markdown fences — the Preview window's CSS / HTML tabs show and edit
them and the guide prints them in §12). Requirements:

- Every exported token is used at least once, in its **typical pairing** (text tokens on
  `canvas`, `surface1..n` nested so the ramp is visible, `accent` as a filled control,
  `border` around a surface, `link` inline in body text, state tokens on `:hover`).
- Reference tokens as `var(--color-<token>, <observed value>)`; the fallback is the
  colour found in the CSS, so the demo looks right even before the first solve.
- The demo CSS is appended **after** the generated custom properties; it must not define
  `:root` colours itself.
- Keep it to a screenful. No layout framework, no images.

---

## 3. The harness document (`theme-doc`)

Field by field, in the order the generated guide prints them. Every prose field is
markdown (tables and fenced code are fine); each is edited in the Handoff window and
written verbatim into `<project>-integration.md` by _Save guide (.md)_, so write for the
reader of that guide — a developer or an agent adopting the tokens — not for yourself.

### 3.1 Summary (`summary`)

Files scanned (count and list), literals found, distinct clusters, themes found/proposed,
tokens defined, number of accessibility findings.

### 3.2 Colour inventory (`inventory`)

One table row per cluster, sorted by usage count, with: cluster id, members (original
literals), normalised `oklch()`, occurrence counts by property class, theme context (s),
states. Alpha values appear in a separate column. Shadows, overlays and gradients get
their own short table.

### 3.3 Token map (`tokenMap`)

| token | custom property | role | light (observed) | dark (observed / _derived_) | clusters merged | notes |

Every cluster from §3.2 must appear here, or in §3.9 with a reason it was not tokenised (alpha overlay, one-off
illustration colour, third-party widget, …).

### 3.4 Relations found (`relations`)

For each rule in the sketch: the rule, the tokens involved, the measured evidence per
theme (e.g. `dL canvas→surface1 = −0.041, surface1→surface2 = −0.039`), and whether it
currently _holds_, is a _floor the CSS fails_, or is a _design decision_ (orientation
point, shared variable).

### 3.5 The sketch (`pcad`)

The complete, parseable sketch as one JSON string (no fence), commented like
`demo/palette.pcad`. It appears in the Script window when the harness opens and as §11
of the guide; a designer should be able to press _Solve themes_ on it as is.

### 3.9 Findings and open questions (`findings`)

Accessibility failures (pair, measured ratio, floor), clusters that were merged
aggressively, colours whose role is ambiguous, near-neutrals whose hue was taken from an
orientation point, out-of-gamut literals, proposed themes, and any rule you chose _not_
to write and why.

---

## 4. PCS reference used by this operation

The subset of the language the theme designer accepts (see `theme-designer/README.md`
§5–§7 for the rationale).

```
space oklab                         # unknowns are [L, a, b]; L in 0..1
units none deg
gamut srgb | p3 | off               # auto-adds `gamut P <= 0` for every exported point

var NAME [= NUMBER] [locked] [shared]

point NAME at LITERAL [anchor | orientation | derived | helper] [label "…"]
  LITERAL := oklch(L% C H) | #rrggbb | rgb(r g b) | hsl(h s% l%) | (L, a, b)

# constraint kinds (arity)                         measure
dL A B        (2)   L(B) − L(A)                    signed
dC A B        (2)   C(B) − C(A)                    signed
dH A B        (2)   shortest signed hue arc A→B    (−180, 180]
L A | C A | H A (1) absolute lightness / chroma / hue
lock L|C|H A B C …  (2+)  all share the axis value of the first point
contrast A B  (2)   WCAG 2.x ratio, symmetric
apca T B      (2)   APCA Lc, text first, signed (negative = light on dark)
gamut A       (1)   0 inside sRGB, positive outside
distance A B  (2)   ΔE_ok

# targets and metadata
KIND pts… (= NUMBER | = VAR | >= NUMBER|VAR | <= NUMBER|VAR | -> min | -> max)
          [weight N] [note "…"] [disabled] [id NAME]

theme NAME {
  NAME at LITERAL                   # anchor / orientation override; derived = seed only
                                    # a trailing role word or `label "…"` is ignored here
  var NAME = NUMBER
}

solver gauss-newton iterations 200 tolerance 1e-6
view camera (x, y, z) target (x, y, z) up (0, 0, 1) perspective | orthographic
view grid none show labels axes hide seeds
```

Inequalities are hinges: satisfied ones drop out and stop fighting the objectives.
`-> min` / `-> max` are soft; give them a small `weight` (0.05–0.3) so they never
overpower an equality or a floor.

---

## 5. What the application emits

_Export CSS_ (`emitCSS(sketch, result)`) writes, in this order:

1. a header comment: `/* Point-CAD theme — <n> colours, themes: light, dark */`,
2. `:root { … }` with the **first** theme's solved values,
3. `@media (prefers-color-scheme: dark) { :root { … } }` for a theme named `dark`,
4. one `[data-theme="<name>"] { … }` block per theme.

Each block lists every exported point (anchors and derived; never orientation or
helpers) as `--color-<kebab-label>: oklch(L% C H);` with the number formatting of §2.2.
For the worked example in §6 the light block reads:

```css
/* Point-CAD theme — 10 colours, themes: light, dark */
:root {
    --color-canvas: oklch(98% 0.005 90);
    --color-brand: oklch(52.556% 0.19879 268.04);
    --color-surface-1: oklch(94% 0.0059 88.4);
    --color-surface-2: oklch(90% 0.0059 88.4);
    --color-surface-3: oklch(86% 0.0059 88.4);
    --color-text: oklch(45.075% 0.01 260);
    --color-text-muted: oklch(55.422% 0.01 260);
    --color-border: oklch(65.449% 0.01 260);
    --color-accent: oklch(52.556% 0.12 88.04);
    --color-link: oklch(52.556% 0.19879 268.04);
}

@media (prefers-color-scheme: dark) {
    :root {
        /* dark theme, same ten properties */
    }
}

[data-theme='light'] {
    /* same as :root */
}

[data-theme='dark'] {
    /* same as the media block */
}
```

Numbers are illustrative; the real ones come from the solve. **Read the output against
the sketch** before accepting it:

- `text` at `L ≈ 45%` rather than the seeded `25%` is the `distance canvas text -> min`
  objective doing exactly what it says — the lightest colour that still passes 7:1. If
  that is not wanted, remove the objective or add `L text <= 0.30`.
- Surfaces must be ordered in `L` by `stepL` and carry chroma close to the canvas (`dC canvas surface3 = 0.01`). A
  surface with `C > 0.05` means a hue or chroma rule is
  missing or a dark-theme seed was omitted; fix the sketch, do not hand-edit the CSS.
- `link` shares `brand`'s hue and lightness by construction; if it collapses onto
  `brand` exactly, `distance link brand -> min` is winning over `contrast link canvas`,
  which means the brand colour already passes 4.5:1 — acceptable, but say so.
- The log line must show `0 unmet` per theme, or the unmet constraints must be
  explained in §3.9.

_Export tokens_ writes the same data as W3C Design Tokens JSON, one group per theme; _Export .pcad_ writes the sketch
back with solved seeds if _Adopt_ was used. _Save guide (.md)_ writes `<project>-integration.md`: the token table
(token → custom
property → value per theme), the CSS above, numbered adoption steps addressed to a human
or an agent, the six migration-guide fields verbatim, the sketch, the demonstration, the
solver report and the evidence fields as an appendix. _Save harness (.html)_ writes a standalone page whose
`<script type="application/json" id="theme-doc">` block carries the sketch and every
field — the same format this operation writes in §2.11 — so the palette can be re-opened (_Open…_ or drag-and-drop),
re-coloured and re-exported later.

---

## 6. Worked example

The starting point of this repository: a light UI with a blue brand, three nested
surfaces, gray text and a warm accent. This is what the `pcad` field should contain for
such a stylesheet, shown unescaped — in the harness it is one JSON string (comments
explain the evidence that justified each rule).

```pcad
# theme_design — one colour model, two themes (light found, dark proposed)
space oklab
units none deg
gamut srgb

var stepL   = -0.04                      # surfaces: measured −0.041 / −0.039 / −0.040
var mutedC  = 4.5   locked               # .muted currently 4.9:1 → AA floor
var accentC = 0.12  shared               # accent saturation identical in every theme

# ---- inputs -------------------------------------------------------------
point canvas      at oklch(98% 0.005 90)  anchor           # body background
point brand       at #3b5bdb              anchor           # = oklch(52.556% 0.19879 268.04)
point warmth      at oklch(70% 0.06 70)   orientation      # surfaces drift toward h≈85
point readingGray at oklch(50% 0.01 260)  orientation      # text/border hue 258–262, C≈0.01

# ---- outputs (seeds = observed light values) ----------------------------
point surface1  at oklch(94% 0.006 88)  derived
point surface2  at oklch(90% 0.007 86)  derived
point surface3  at oklch(86% 0.008 84)  derived
point text      at oklch(25% 0.01 260)  derived
point textMuted at oklch(45% 0.01 260)  derived
point border    at oklch(70% 0.01 260)  derived
point accent    at oklch(60% 0.12 80)   derived
point link      at oklch(45% 0.15 262)  derived

# surfaces: even lightness steps, warm hue, a touch more chroma than the canvas
dL canvas surface1 = stepL
dL surface1 surface2 = stepL
dL surface2 surface3 = stepL
lock H warmth surface1 surface2 surface3 weight 0.3
lock C surface1 surface2 surface3
dC canvas surface3 = 0.01

# text family: one hue and chroma, lightness decided by contrast
lock H readingGray text textMuted border
lock C readingGray text textMuted border
contrast text canvas      >= 7             # AAA body text (currently 15.2:1)
contrast textMuted canvas >= mutedC        # AA
contrast border canvas    >= 3             # UI component minimum (currently 2.4:1 — FAILS)
distance canvas text      -> min weight 0.05   # no further from the canvas than needed
distance canvas textMuted -> min weight 0.05
distance canvas border    -> min weight 0.05

# accents
dH brand accent = 180                      # measured 179.6° from brand
lock L brand accent
C accent = accentC
contrast accent canvas >= 3
lock H brand link                          # link hue 262 vs brand 268 → treated as one family
contrast link canvas >= 4.5
distance link brand -> min

# ---- themes -------------------------------------------------------------
theme light {
  canvas at oklch(98% 0.005 90)
  brand  at #3b5bdb
}
theme dark {                               # proposed: no dark scheme exists in the CSS
  canvas      at oklch(16% 0.01 260)
  brand       at oklch(72% 0.14 262)
  warmth      at oklch(40% 0.03 260)
  readingGray at oklch(85% 0.01 260)
  var stepL = 0.03
  text      at oklch(92% 0.01 260)         # seeds only: put the outputs on the light side
  textMuted at oklch(75% 0.01 260)
  border    at oklch(40% 0.01 260)
  link      at oklch(80% 0.12 262)
}

solver gauss-newton iterations 200 tolerance 1e-6
view camera (300, 250, 400) target (0, 0, 200) up (0, 0, 1) perspective
view grid none show labels axes hide seeds
```

`preview.css` (appended after the generated custom properties; fallbacks are the
observed values):

```css
body {
    margin: 0;
    padding: 20px;
    font: 14px/1.5 system-ui,
    sans-serif;
    background: var(--color-canvas, #fdfcf9);
    color: var(--color-text, #222);
}

h2 {
    margin: 0 0 4px;
    font-size: 20px;
}

.muted {
    color: var(--color-text-muted, #666);
}

a {
    color: var(--color-link, #36c);
}

.card {
    background: var(--color-surface-1, #f1efe9);
    border: 1px solid var(--color-border, #ccc);
    border-radius: 8px;
    padding: 12px;
    margin: 12px 0;
}

.card .card {
    background: var(--color-surface-2, #e4e1d9);
}

.card .card .card {
    background: var(--color-surface-3, #d7d3c9);
}

.btn {
    display: inline-block;
    background: var(--color-accent, #c60);
    color: var(--color-canvas, #fff);
    border-radius: 6px;
    padding: 6px 12px;
    font-weight: 600;
}
```

```html
<h2>Sample page</h2>
<p>
    Body text on <code>canvas</code>.
    <span class="muted">Secondary text uses <code>textMuted</code>.</span> A <a href="#">link</a> sits
    on the brand hue.
</p>
<div class="card">
    <b>surface1</b>
    <div class="card">
        <b>surface2</b>
        <div class="card"><b>surface3</b> — three even lightness steps.</div>
    </div>
</div>
<p><span class="btn">Accent action</span></p>
<p class="muted">
    Every colour above is a solved point in OKLab; edit an anchor or a rule in the script window and
    re-solve.
</p>
```

`preview.html`:

```html
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>... — theme designer harness</title>
    <style>
        html, body {
            margin: 0;
            height: 100%;
            background: #15181f;
            color: #e8ecf3;
            font: 14px/1.4 system-ui, sans-serif;
        }

        point-cad {
            display: block;
            width: 100%;
            height: 100%;
        }
    </style>
</head>
<body>
<point-cad
        id="cad"
        panels="swatches,preview,handoff,variables,points,constraints,script,solve"
></point-cad>
<pre id="boot-error"></pre>

<script type="application/json" id="theme-doc">
    {
      "generator": "analyze.op",
      "generated": "2025-01-01T00:00:00Z",
      "pcad": "# theme_design — one colour model, two themes\nspace oklab\nunits none deg\ngamut srgb\n…",
      "doc": {
        "project": "My app",
        "prefix": "color-",
        "format": "oklch",
        "scope": ":root",
        "switchMode": "media,attribute",
        "defaultTheme": "",
        "fallback": false,
        "preview": {
          "html": "<h2>Sample page</h2>…",
          "css": "body { background: var(--color-canvas, #fdfcf9); … }"
        }
      }
    }
</script>

<script type="module">
    // Adjust this single constant if the Point-CAD library lives elsewhere.
    const HARNESS_URL =
            'https://math.cognotik.com/experiments/point-cad/extensions/theme/ui/harness.js';
    try {
        const {bootHarness} = await import(HARNESS_URL);
        await bootHarness(document.getElementById('cad'));
    } catch (err) {
        const el = document.getElementById('boot-error');
        el.style.display = 'block';
        el.textContent =
                'Could not load the theme-designer harness from "' +
                HARNESS_URL +
                '".\n' +
                'Edit HARNESS_URL at the bottom of this file to point at\n' +
                'extensions/theme/ui/harness.js, then reload.\n\n' +
                err;
    }
</script>
</body>
</html>
```

## 7. Pitfalls to check before finishing

- **Hue of near-neutrals.** `lock H` against a colour with `C < 0.02` is numerically
  meaningless. Take the hue from an orientation point (`readingGray`), never from the
  gray itself, and say so in §3.9.
- **Objectives moving colours.** `-> min` objectives make text as light as the floor
  allows. Only add them when the intent really is "as close to the canvas as allowed";
  otherwise pin `L`.
- **Dark-theme seeds.** Without seeds on the correct side of the canvas, contrast floors
  can be satisfied from the wrong side (dark text on a dark canvas). Always seed.
- **Missing chroma rule on a ramp.** `lock H` alone lets surfaces wander in chroma; pair
  it with `lock C` and one `dC canvas …`.
- **Anchors vs. tokens.** An anchor is still exported; do not duplicate it as a derived
  point.~~~~
- **Out-of-gamut anchors.** A brand colour outside sRGB cannot be an anchor with
  `gamut srgb`; report it and use its gamut-mapped value.
- **Everything must parse.** Constraint names, arities and target forms are exactly
- **Raw templates.** `preview.css` and `preview.html` are CSS and body markup, not fenced
  markdown; a fence in either shows up verbatim in the preview.
- **Valid JSON.** One bad escape in the `theme-doc` block and the harness opens empty (the log then shows no
  `loaded embedded sketch` line). Escape `"`, `\` and newlines in
  `pcad`, and `</` as `<\/`.
  those in §4; `index.html` must open with no errors in the log.

