# Chromatic — A Multispace Color Geometry Engine

## Project Specification

### 1. Vision

Most color palette tools operate at the wrong level of abstraction. They rotate hue wheels, grid HSL space, or apply
harmony heuristics inherited from print-era color theory. The results _look_ mathematically tidy but frequently _feel_
inconsistent, because HSL and HSV are not perceptually uniform: equal steps in hue, saturation, or lightness do not
correspond to equal steps in perceived color difference.

**Chromatic** is not a palette generator. It is a **color layout engine** — a library that treats a set of colors as a
_point-set with relational structure_
(distances, adjacency, symmetry, ordering) rather than as an unordered list of swatches. That structure is:

1. **Constructed** in a perceptually uniform space (OKLab / OKLch).
2. **Declared** through a small domain-specific language (DSL) that expresses topology, relational constraints,
   symmetry, and semantic intent — not raw coordinates.
3. **Solved** by a constraint/optimization engine that turns the DSL program into an objective function and produces a
   concrete point-set.
4. **Projected** into other colorspaces (Lab, Lch, HSL, HSV, sRGB), with distortion measured and, optionally, minimized
   so that the relational structure survives the projection.

The guiding principle, distilled from the design discussion this spec is based on:

> A palette is "good" when its relational invariants — ordering, adjacency,
> symmetry, and approximate distances — remain stable across colorspaces, even
> if the exact geometry warps in the process.

Chromatic exists to make that principle programmable.

### 2. Problem Statement

Given the same conceptual palette (e.g., "6 accent colors around a neutral background, evenly spaced in hue, with
alternating chroma"), different naive constructions produce inconsistent visual results:

- A hue-rotated HSL palette breaks down when converted to Lab: perceptual spacing collapses near yellows/cyans and
  stretches near blues/magentas.
- A geometrically "even" arrangement in OKLab may look uneven once rendered as HSL-driven CSS variables, because HSL's
  hue function is nonlinear relative to OKLch hue.
- Hand-tuned palettes don't scale: adding a semantic role ("warning",
  "accent", "background") requires redesigning the whole set by eye.

There is currently no widely-used tool that:

- Treats palette design as **geometry construction** in a perceptual space, followed by **projection and distortion
  analysis** into working spaces.
- Exposes a **declarative language** for relational intent ("these two should contrast in lightness", "this row is a hue
  cycle", "this palette has 6-fold rotational symmetry") rather than literal coordinates.
- Supports **optional numerical optimization**, driven by the same declarative constraints, to reduce cross-space
  distortion without requiring the author to hand-tune anything.

Chromatic fills this gap.

### 3. Scope

Chromatic is an **ES6 library** (framework-agnostic, zero required runtime dependencies for the core, tree-shakeable)
consisting of four cooperating layers:

| Layer                  | Responsibility                                                                                                                                   |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Colorspace Core**    | Conversions between sRGB, HSL, HSV, Lab, Lch, OKLab, OKLch. Distance/metric utilities.                                                           |
| **Geometry Kernel**    | Primitives for point-sets, graphs, manifolds, and group actions in a perceptual space.                                                           |
| **DSL**                | A small declarative language (and its parser/AST/compiler) for expressing topology, constraints, symmetry, semantics, and projection tolerances. |
| **Solver / Optimizer** | Compiles a DSL program into an objective function + constraint set and numerically solves for a concrete OKLab point-set.                        |

A thin **rendering/export layer** converts the final point-set into usable output formats (CSS custom properties, JSON
tokens, Sass maps, Tailwind config fragments, SVG swatches).

Out of scope for v1: a GUI/visual editor, gamut mapping for non-sRGB displays (P3/Rec.2020), and accessibility contrast
auditing (WCAG) — these are natural follow-on projects but are noted as **future work** (§10).

### 4. Core Concepts

#### 4.1 Point-set

A `PaletteColor` is a point in OKLab space (`L`, `a`, `b`), optionally tagged with a semantic `role` (`"accent"`,
`"background"`, `"neutral"`, etc.) and a stable `id` used for referencing it from the DSL and from constraints.

A `Palette` is an ordered collection of `PaletteColor`s plus:

- an **adjacency graph** (which colors are considered "related")
- a **topology descriptor** (grid, cycle, chain, lattice, manifold sample, or group orbit) used to generate default
  adjacency and default constraints
- a **constraint set** (see §6)

#### 4.2 Relational invariants

Chromatic explicitly models four invariant families, each with a metric:

1. **Metric invariants** — pairwise perceptual distance (ΔEOK, ΔE2000, or raw Euclidean distance in OKLab),
   triangle-inequality checks, k-NN graphs.
2. **Symmetry invariants** — cyclic/dihedral group actions applied to a base point or curve; checked via residual error
   after applying the inverse transform.
3. **Topological invariants** — adjacency graph structure (connectedness, cycle rank, planarity) computed via minimum
   spanning tree, Delaunay triangulation, or explicit k-NN graphs.
4. **Ordering invariants** — the _rank order_ of lightness, chroma, and hue across colors. Empirically the most robust
   invariant under projection (per the design discussion): even when absolute distances warp under projection, preserved
   **ordering** is what makes a palette still "read"
   as coherent.

#### 4.3 Projection & distortion

A `Projector` maps a `Palette` (defined in OKLab) into a target colorspace (HSL, HSV, Lab, Lch, sRGB). A
`DistortionReport` quantifies, per invariant family:

- pairwise distance distortion (ratio / correlation of distance matrices)
- symmetry residual (how far a nominally symmetric relation deviates after projection)
- adjacency-graph edit distance (edges gained/lost when recomputing the adjacency graph in the target space)
- ordering violations (count of pairs whose relative order flips)
- gamut clipping incidence (fraction of points that fall outside sRGB and had to be clipped/mapped back in)

This report is the feedback signal used both by the human author (as a diagnostic) and by the optimizer (as a loss
function component).

### 5. Architecture

```
src/
  colorspace/
    srgb.js          # sRGB <-> linear RGB
    hsl.js           # RGB <-> HSL
    hsv.js           # RGB <-> HSV
    lab.js           # XYZ <-> CIE Lab, Lab <-> Lch
    oklab.js         # RGB <-> OKLab, OKLab <-> OKLch
    convert.js       # unified `convert(color, from, to)` graph-based router
    distance.js       # deltaE76, deltaE2000, deltaEOK, euclidean

  geometry/
    point-set.js      # PaletteColor, Palette primitives
    manifold.js        # curve/surface parameterizations (spiral, geodesic, bezier, torus)
    group.js           # cyclic/dihedral/affine group action generators
    graph.js            # adjacency graph builders: MST, k-NN, Delaunay, cycle, hypercube
    sampling.js         # uniform / adaptive / curvature-based sampling along manifolds

  dsl/
    lexer.js
    parser.js
    ast.js
    compiler.js         # AST -> {topology, constraints, objective, projection-config}
    semantics.js        # semantic role -> prior constraint mapping (accent, neutral, ...)

  solver/
    objective.js        # builds scalar loss function from compiled constraints
    constraints.js       # constraint primitives: contrast, similar, balance, symmetry, order
    optimize.js          # numerical solver (gradient-based / simulated annealing fallback)
    distortion.js         # DistortionReport computation

  project/
    projector.js         # OKLab -> {HSL, HSV, Lab, Lch, sRGB}
    gamut.js              # sRGB gamut clipping / soft compression

  export/
    css.js                # CSS custom properties
    json.js               # design-token JSON
    sass.js               # Sass map
    tailwind.js            # Tailwind config fragment
    svg.js                 # SVG swatch sheet

  index.js                 # public API surface
```

All modules are authored as native ES modules (`import`/`export`), targeting evergreen browsers and Node ≥ 18. No build
step is required to consume the library from source; a bundled/minified distribution is produced via Rollup for
convenience.

### 6. The DSL

The DSL is the layer most exposed to an open strategic question: is declarative syntax the right _primary_ interface, or
does most real usage end up on the programmatic geometry API (§8) — cyclic/dihedral group generators, manifold sampling,
direct point construction — with the DSL as a convenience layer for a handful of common topologies? This spec does not
resolve that question by architectural fiat; it resolves it by architectural _hedge_. The DSL compiler is built strictly
on top of a set of public, independently usable artifacts — a constraint list, a symmetry group descriptor, a projection
configuration — that a programmatic caller can also construct by hand. Because the DSL has no privileged wiring into the
solver or geometry kernel that the programmatic path lacks, it can be demoted to a secondary, sugar-layer status later
without touching either, if adoption data ends up favoring JS-native construction. The roadmap (§10) treats this as a
question to be answered empirically, not assumed.

#### 6.1 Design goals

- **Declarative**: describe _what relations should hold_, not _what the RGB values are_.
- **Composable**: topology, constraints, semantics, and projection tolerances are independent, orthogonal blocks.
- **Two-mode**: the same syntax can be interpreted either as pure geometric layout (constraints solved directly, no
  cross-space concern) or as a full optimization problem (constraints + projection tolerances become a loss function).

#### 6.2 Grammar (informal EBNF)

```ebnf
palette      = "palette" "{" topology { statement } "}" ;

topology     = grid | cycle | chain | lattice | manifold | orbit ;
grid         = "grid" INT "x" INT ;
cycle        = "cycle" INT ;
chain        = "chain" INT ;
lattice      = "lattice" INT "x" INT "x" INT ;
manifold     = "manifold" IDENT "(" args ")" ;
orbit        = "orbit" "group" "(" IDENT { "," IDENT } ")" ;

statement    = assignment | constraint | symmetryDecl
             | projectionBlock | semanticDecl ;

assignment   = "cell" "(" INT { "," INT } ")" ":" semanticCall ";" ;
semanticCall = IDENT "(" [ args ] ")" ;

constraint   = ("contrast" | "similar" | "balance")
               "(" ref ["," ref] "," predicate ")" ";" ;
predicate    = PROPERTY COMPARATOR NUMBER
             | "equal_" PROPERTY ;

symmetryDecl = "symmetry" ("rotational" INT | "reflection" AXIS) ";" ;

projectionBlock =
  "optimize_for" "(" SPACE ")" "{" { projectionStatement } "}" ;
projectionStatement =
    "preserve_order" "(" PROPERTY ")" ";"
  | "limit_distortion" "(" PROPERTY "," "max" "=" NUMBER ")" ";"
  | "avoid_gamut_clipping" "(" "in" "=" SPACE ")" ";" ;

ref          = "cell" "(" INT { "," INT } ")" | IDENT ;
PROPERTY     = "lightness" | "chroma" | "hue" ;
SPACE        = "HSL" | "HSV" | "Lab" | "Lch" | "RGB" ;
```

#### 6.3 Example program

```
palette {
  grid 3x3

  symmetry rotational 3
  balance(equal_chroma)

  cell(0,0): neutral()
  cell(1,1): primary()
  cell(2,2): accent()

  contrast(cell(0,0), cell(1,1), lightness > 0.2)
  contrast(cell(1,1), cell(2,2), chroma > 0.3)

  optimize_for(HSL) {
    preserve_order(lightness)
    limit_distortion(hue, max=0.1)
    avoid_gamut_clipping(in=RGB)
  }
}
```

#### 6.4 Compilation pipeline

1. **Lex/parse** the source text into an AST (`dsl/lexer.js`, `dsl/parser.js`).
2. **Resolve topology** into an initial adjacency graph and a default set of coordinates on the chosen manifold/lattice
   (`geometry/*`).
3. **Compile statements** into three artifacts (`dsl/compiler.js`):
   - a **constraint list** (hard/soft, per `solver/constraints.js` primitives)
   - a **symmetry group descriptor** (per `geometry/group.js`)
   - a **projection configuration** (target space + tolerances)
     This step also runs a lightweight conflict check: the grammar does not itself prevent declaring, say,
     `rotational 3` symmetry alongside three role assignments that cannot lie on a single rotational orbit. Hard
     constraints (topology, declared symmetry) take precedence; semantic-role priors and soft constraints are adjusted
     to accommodate them; and any statement that still cannot be satisfied is surfaced as a compile-time diagnostic
     rather than silently averaged away during resolution.
4. **Apply semantic priors**: each `semanticCall` (`accent()`, `neutral()`, etc.) contributes soft constraints on
   chroma/lightness ranges (`dsl/semantics.js`).
5. Hand the compiled program to the **solver**.

#### 6.5 Semantic role priors (defaults, overridable)

| Role           | OKLch prior                                      |
| -------------- | ------------------------------------------------ |
| `background()` | low chroma (≤ 0.03), high lightness (≥ 0.85)     |
| `neutral()`    | low chroma (≤ 0.02), mid lightness (0.4–0.7)     |
| `primary()`    | mid chroma (0.1–0.18), mid lightness (0.45–0.65) |
| `accent()`     | high chroma (≥ 0.18), mid lightness (0.5–0.7)    |
| `warning()`    | high chroma, warm hue band (30°–70°)             |

These are plain data, defined in `dsl/semantics.js`, and are intended to be user-overridable via a config object passed
to the compiler. Concretely,
`compileDSL(source, config)` accepts the same shape of object that
`dsl/semantics.js` exports as its default, and deep-merges any
`config.semantics` roles into that default table — callers overriding
`accent()`'s chroma floor don't need to redeclare the other four roles. This keeps the override path described here
consistent with the compiler signature shown in §8, rather than leaving the two loosely implied to agree.

### 7. Solver & Optimization

#### 7.1 Two operating modes

- **Layout mode** (default, no `optimize_for` block): the compiler resolves coordinates directly from topology +
  constraints using a direct constraint-satisfaction pass (projection/relaxation on the manifold). No numerical
  optimizer is invoked. This is the "geometric layout is 80% of the win" path from the design discussion — fast,
  deterministic, no solver dependency.
- **Optimization mode** (one or more `optimize_for` blocks present): the compiler additionally builds a scalar
  **objective function**
  `J(C) = Σ w_i · constraint_violation_i(C) + Σ v_j · distortion_j(C)` over the point-set `C`, and a numerical solver
  adjusts point positions in OKLab to minimize `J`.

#### 7.2 Objective construction

Each DSL constraint/projection statement contributes one term:

| DSL statement                   | Objective term                                           |
| ------------------------------- | -------------------------------------------------------- |
| `contrast(a, b, lightness > t)` | `max(0, t - (L(a) - L(b)))²` (or symmetric)              |
| `similar(a, b, hue < t)`        | `max(0,                                                  | H(a) - H(b)    | - t)²` |
| `balance(equal_chroma)`         | variance of chroma across referenced set                 |
| `symmetry(rotational k)`        | Σ‖cᵢ₊ₖ − Rₖ(cᵢ)‖² over the orbit                         |
| `preserve_order(lightness)`     | count/penalty for rank-order inversions after projection |
| `limit_distortion(hue, max=x)`  | `max(0,                                                  | Δhue_projected | - x)²` |
| `avoid_gamut_clipping(in=RGB)`  | penalty proportional to out-of-gamut distance            |

#### 7.3 Solver implementation

- Default solver: gradient-based local optimization (numerical gradient via finite differences over OKLab coordinates),
  since colorspace conversions are not trivially differentiable in closed form for all pairs (notably HSL's piecewise
  hue function). Implemented in `solver/optimize.js` with a pluggable strategy interface so alternative solvers
  (simulated annealing, CMA-ES) can be substituted for non-smooth objectives (e.g., discrete ordering penalties).
- Hard constraints (declared without tolerance, e.g., fixed topology) are enforced by construction (project back onto
  manifold/lattice after each optimizer step) rather than penalized.
- The solver is capped by iteration count and a convergence threshold on
  `J`; it returns both the optimized `Palette` and a final
  `DistortionReport` for transparency.

#### 7.4 Distortion measurement

`solver/distortion.js` and `project/projector.js` cooperate to:

1. Project the current OKLab point-set into each target space named in an
   `optimize_for(...)` block.
2. Recompute distance matrices, adjacency graphs (k-NN/MST), symmetry residuals, and rank orders in that target space.
3. Compare against the OKLab-space versions to produce distortion scores consumed both by the objective function (during
   optimization) and exposed to the caller as a diagnostic report (always).

### 8. Public API (ES6)

```js
import { Palette, compileDSL, project, exportCSS } from 'chromatic';

// 1. Parse + compile a DSL program
const program = compileDSL(`
  palette {
    grid 3x3
    symmetry rotational 3
    cell(0,0): neutral()
    cell(1,1): primary()
    cell(2,2): accent()
    contrast(cell(0,0), cell(1,1), lightness > 0.2)
    optimize_for(HSL) {
      preserve_order(lightness)
      limit_distortion(hue, max=0.1)
    }
  }
`);

// 2. Resolve into a concrete palette (runs solver if optimize_for present)
const { palette, report } = program.resolve();

// 3. Inspect distortion
console.log(report.summary());
// { orderingViolations: 0, avgHueDistortion: 0.06, gamutClipped: 0 }

// 4. Project into a working colorspace directly
const hslPalette = project(palette, 'HSL');

// 5. Export
const css = exportCSS(hslPalette, { prefix: '--color-' });
```

Programmatic (non-DSL) construction is also supported for library users who prefer JS-native geometry building:

```js
import { Palette, manifold, group } from 'chromatic/geometry';

const orbit = group.cyclic({ order: 6 }).apply({ L: 0.6, C: 0.15, H: 30 }); // OKLch base point

const palette = Palette.fromPoints(orbit, { space: 'OKLch' });
```

### 9. Testing & Validation Strategy

- **Unit tests** for every colorspace conversion, validated against reference implementations (e.g., known OKLab/sRGB
  round-trip fixtures, culori or colorjs.io test vectors used only as validation data, not as a dependency).
- **Property tests** for the geometry kernel: group actions must be closed (applying the full orbit returns to start),
  manifold sampling must be monotonic in its parameter, adjacency graph builders must produce connected graphs for
  connected inputs.
- **DSL round-trip tests**: parse → compile → resolve → re-serialize, ensuring stable AST structure and deterministic
  layout-mode output.
- **Solver regression tests**: fixed DSL programs with known-good optimized outputs (snapshotted `DistortionReport`
  values) to catch regressions in objective construction.
- **Golden palette fixtures**: a small curated set of DSL programs (grid, cycle, spiral, symmetry-group) with expected
  qualitative properties (ordering preserved, symmetry residual below threshold) checked on every CI run.
- **Bundle-size regression tests**: importing a single pairwise conversion function (e.g. `oklab.rgbToOklab`) must not
  pull in unrelated colorspace modules through `convert.js`'s routing graph. This keeps the tree-shaking contract
  described in §5 enforced by CI rather than merely asserted in prose.

### 10. Roadmap

The ordering below deliberately pulls tangible, shareable proof of the core thesis — geometry constructed in OKLab looks
and reads better than hue-rotated HSL, and that difference is measurable — forward into v0.1, rather than leaving it
until the export ecosystem lands in v0.5. It also resolves the DSL-primacy question raised in §6 by sequencing: the
programmatic geometry API is stabilized (and typed) before the DSL is built on top of it, so DSL investment can be
scaled up or down based on how that API is actually used, rather than committed to up front. Solver sophistication is
deliberately kept to a straightforward finite-difference/penalty implementation through v0.3; deeper solver investment
competes for the same engineering budget as export/adoption features, and this roadmap resolves that tradeoff in favor
of shipping a smaller, correct solver early and revisiting its internals only if the golden-fixture regression suite
(§9) demonstrates it's the actual bottleneck.

**v0.1 — Colorspace Core & Geometry Kernel**
Conversions, distance metrics, manifold/group/graph primitives. No DSL, no solver. Includes a minimal CSS/JSON export
and a terminal/ASCII swatch preview — enough to build the flagship demonstration this project is premised on: the same
conceptual palette laid out in OKLab and projected into HSL, side by side with a naive HSL-rotated equivalent, with the
`DistortionReport`-style metrics (ordering violations, hue distortion)
made concrete rather than asserted in prose.

**v0.2 — Programmatic geometry API**
`Palette`, `manifold`, `group`, `graph` stabilized as the primary, tree-shakeable, TypeScript-typed public surface (§8).
This is the layer the DSL compiler is built on, and stabilizing it first means real usage data can inform how much
further investment the DSL actually warrants.

**v0.3 — DSL (layout mode only) & Solver/Optimization mode**
Lexer/parser/compiler, direct constraint resolution on manifolds/lattices;
`optimize_for` blocks, objective construction, gradient-based solver, hard-constraint gamut handling (§7.3),
`solverStatus`, and
`DistortionReport`. Shipped together because layout mode and optimization mode share the same compiled artifacts (§6.4)
and neither is validated as
"done" without exercising the other against the golden fixtures.

**v0.4 — Semantic roles & priors**
`accent()`, `neutral()`, `primary()`, etc., overridable prior configuration, revised to be hue-aware rather than flat
scalar thresholds (§7.3).

**v0.5 — Export ecosystem**
Sass, Tailwind, SVG swatch sheet exporters; documentation site with live DSL playground — building on, not replacing,
the minimal export/preview tooling already shipped in v0.1.

**Future work (post-v1, explicitly out of scope for this spec)**

- Wide-gamut (P3/Rec.2020) aware gamut mapping.
- WCAG contrast-ratio constraints as first-class DSL predicates.
- Visual/interactive DSL editor with live distortion visualization.
- Additional solver backends (CMA-ES, simulated annealing) for non-smooth objectives.
- Animation/interpolation between two resolved palettes along the same manifold (palette "morphing").

### 11. Non-Goals

- Chromatic does not attempt to encode aesthetic "harmony rules" (Itten, Munsell) as first-class primitives; any such
  heuristic can be expressed as a semantic-role prior or a user-defined constraint, but the engine itself is agnostic to
  color theory tradition and concerns itself only with relational geometry.
- Chromatic does not replace perceptual color-difference research; it consumes existing metrics (ΔE2000, ΔEOK) rather
  than inventing new ones.
- Chromatic is not a device color-management system; gamut handling is limited to sRGB clipping/soft-compression in v1.

### 12. Summary

Chromatic reframes palette design as **constrained geometry construction in a perceptual colorspace**, followed by
**measured projection** into the colorspaces designers actually use (HSL, RGB, CSS). Its declarative DSL lets authors
state relational _intent_ — contrast, similarity, symmetry, semantic role — and lets an optional numerical solver
reconcile that intent with the distortions inherent in projecting OKLab geometry into less-uniform spaces. The result is
a small, composable ES6 library that turns "pick some nice colors" into "declare a color geometry and let the engine
make it coherent everywhere it's used."
