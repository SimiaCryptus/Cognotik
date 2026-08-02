# Invariants Table: Algebraic Data vs. Geometric Realization

This document catalogs the algebraic invariants `(F, G, tau)` and the
geometric data `(P, E_A, dim)` for every example discussed in the
corpus. It is intended as a single-page reference companion to
`insights.md` §5 (the meta-insight that the framework is a functor
from algebra to geometry).

---

## 1. Reading the Table

Each row of the master table below describes one example. Columns:

- **Name**: shorthand identifier.
- **Source**: which `.md` document discusses it.
- **`F`**: coordinate field.
- **`[F:Q]`**: degree of `F` over `Q`.
- **`G`**: fiber group of sheet transitions.
- **`tau`**: vortex rule shorthand (`triv`, `every-n`, `signed-n`,
  `cap`, `2I`, etc.).
- **`P`**: underlying polygon / polytope / fractal.
- **`E_A`**: active edge subset (for pinwheels) or `all` (default).
- **`d_eff`**: predicted/measured connectivity dimension.
- **`d_spec`**: predicted/measured spectral dimension under `signed3`
  or analogue.
- **`Level`**: position in the four-level hierarchy of
  `pinwheels.md` §5.

Entries marked `??` are not yet measured; entries marked `--` are
not applicable.

---

## 2. Master Table

| Name            | Source             | `F`             | `[F:Q]` | `G`         | `tau`      | `P`          | `E_A`   | `d_eff`    | `d_spec`  | Level |
| --------------- | ------------------ | --------------- | ------- | ----------- | ---------- | ------------ | ------- | ---------- | --------- | ----- |
| Square          | idea.md, polyhedra | `Q`             | 1       | `{e}`       | triv       | square       | all     | 2          | 2         | 0     |
| Triangle        | sweep_ngon, idea   | `Q`             | 1       | `{e}`       | triv       | triangle     | all     | 2          | 2         | 0     |
| Hexagon         | idea.md            | `Q`             | 1       | `{e}`       | triv       | hexagon      | all     | 2          | 2         | 0     |
| Pentagon        | idea.md, all       | `Q(sqrt(5))`    | 2       | `Z_10`      | signed-3   | pentagon     | all     | ~2.3-2.5   | ~1.1      | 1     |
| Octagon         | sweep_ngon, idea   | `Q(sqrt(2))`    | 2       | `Z_8` (?)   | signed-3   | octagon      | all     | ~2.3?      | ~1.1?     | 1     |
| Dodecagon       | sweep_ngon         | `Q(sqrt(3))`    | 2       | `Z_12` (?)  | signed-3   | dodecagon    | all     | ~2.3?      | ~1.1?     | 1     |
| Heptagon        | sweep_ngon         | `Q(zeta_7+...)` | 3       | `Z_14` (?)  | signed-3   | heptagon     | all     | ~2.3?      | ~1.1?     | 1     |
| Tetrahedron     | polyhedra.md       | `Q(sqrt(2))`    | 2       | `2T` (24)   | 3D-tau     | tetrahedron  | all     | 3          | ??        | 0     |
| Cube            | polyhedra.md       | `Q`             | 1       | `{e}`       | triv       | cube         | all     | 3          | 3         | 0     |
| Octahedron      | polyhedra.md       | `Q`             | 1       | `{e}`       | triv       | octahedron   | all     | 3          | 3         | 0     |
| Dodecahedron    | polyhedra.md       | `Q(sqrt(5))`    | 2       | `2I` (120)  | 3D-tau     | dodecahedron | all     | (3,4)      | ??        | 1     |
| Icosahedron     | polyhedra.md       | `Q(sqrt(5))`    | 2       | `2I` (120)  | 3D-tau     | icosahedron  | all     | (3,4)      | ??        | 1     |
| Conway-Radin    | pinwheels.md       | `Q`             | 1       | `Z_2 x Z_2` | hypotenuse | right tri    | hyp     | 2          | 2         | 2     |
| Penrose (rhomb) | pinwheels.md, idea | `Q(sqrt(5))`    | 2       | `Z_10`      | inflation  | rhombus      | special | ~2.3       | ~1.1      | 1     |
| Sierpinski tri. | analysis.md, idea  | `Q(sqrt(3))`    | 2       | `Z_6`       | scale      | triangle     | --      | log3/log2  | log3/log5 | 3     |
| Sierpinski tet. | analysis.md        | `Q(sqrt(2))`    | 2       | `Z_8`       | scale      | tetrahedron  | --      | 2 (exact)  | ??        | 3     |
| Koch snowflake  | analysis.md        | `Q(sqrt(3))`    | 2       | --          | scale      | curve        | --      | log4/log3  | --        | --    |
| Menger sponge   | analysis.md        | `Q`             | 1       | --          | scale      | cube         | --      | log20/log3 | --        | --    |

---

## 3. Reading the Patterns

Three patterns emerge from the table.

### 3.1 Field-Universality (`Q(sqrt(5))` cluster)

The pentagon, dodecahedron, icosahedron, and Penrose rhombus
tilings **all live over `Q(sqrt(5))`**. This is the single most
striking algebraic coincidence in the corpus, and it confirms
`insights.md` Insight 1.2: `Q(sqrt(5))` is the natural substrate
for any 5-fold-symmetric construction in any dimension.

What this means computationally: any optimization or testing
machinery written for the pentagon (e.g. `experiment.mac` Section
23 on the Erdős distance catalog) can be adapted to the
dodecahedron and Penrose tilings essentially without modification
of the arithmetic kernel.

### 3.2 Quadratic Field is the Generic Case

Of the 18 examples, **14 live over a real quadratic field**.
Higher-degree fields appear only for `n in {7, 9, 11, ...}` and
are essentially absent from the geometric examples (no regular
polytope has degree-3 coordinates over `Q`). Practical
implication: the `qsqrt5_add` / `qsqrt5_mul` machinery in
`erdos.mac` is essentially **sufficient** for the framework
in its currently-realized form.

### 3.3 The Hierarchy Levels are Sparse

Of the entries:

- **Level 0**: 6 examples (all the "boring" cases),
- **Level 1**: 7 examples (the heart of the framework),
- **Level 2**: 1 example (Conway-Radin),
- **Level 3**: 2 examples (Sierpinski).

This sparsity at Levels 2 and 3 is striking: it suggests that
the pinwheel polygon catalog (Ticket C2 in
`research_program.md`) is **a substantial, currently-unexplored
territory**. Almost every pinwheel polygon found will be a new
example.

---

## 4. Cross-References to the Code

Each row of the master table corresponds to specific code paths:

| Row family        | Code path                                    |
| ----------------- | -------------------------------------------- |
| `Q` (Level 0)     | `experiment.mac` with `TAU_MODE = "every3"`, |
|                   | `N_GON in {3, 4, 6}`                         |
| `Q(sqrt(5))` 2D   | `experiment.mac` with `N_GON = 5`,           |
|                   | `TAU_MODE = "signed3"`                       |
| `Q(sqrt(2))` 2D   | `experiment.mac` with `N_GON = 8`,           |
|                   | `TAU_MODE = "signedn"`                       |
| `Q(sqrt(3))` 2D   | `experiment.mac` with `N_GON = 12`           |
| Erdős catalog     | `erdos.mac`                                  |
| 3D polyhedra      | **Not yet implemented**; ticket C1 of        |
|                   | `research_program.md`                        |
| Pinwheel polygons | **Not yet implemented**; ticket C2 of        |
|                   | `research_program.md`                        |
| Sweeps            | `sweep_ngon.mac`                             |

---

## 5. Missing Entries (Action Items)

The `??` entries above are the explicit measurement targets of
the research program. In priority order:

1. **Tetrahedron `d_spec`**: requires 3D implementation
   (`research_program.md` Ticket C1).
2. **Dodecahedron, icosahedron `d_spec`**: same.
3. **Octagon, dodecagon, heptagon `d_eff` confirmation**: requires
   larger sweep (already plausible with existing
   `sweep_ngon.mac`).
4. **Sierpinski tetrahedron `d_spec`**: pure analysis, no new
   code needed.
5. **Fiber group `G` for octagon and dodecagon**: requires
   analysis of the closure of the orientation group, which is
   conjecturally `Z_8` and `Z_12` by analogy with the pentagon
   (`Z_10`).

---

## 6. The Functor Picture (insights.md §5 made precise)

The functor `Phi: (F, G, tau) |-> AdjacencyGraph` claimed by
`insights.md` §5 acts as follows:

- Objects of the source category: triples `(F, G, tau)` with `F`
  a number field, `G` a finite group, and `tau: E -> G` a vortex
  rule satisfying the global consistency axiom of `idea.md` §5.
- Morphisms of the source: pairs `(iota, rho)` where
  `iota: F -> F'` is a field embedding and `rho: G -> G'` is a
  group homomorphism, with `tau' compatible with tau` under
  `(iota, rho)`.
- Objects of the target: locally-finite graphs.
- The functor: `Phi(F, G, tau)` is the multi-sheeted adjacency
  graph constructed by the BFS oracle of `experiment.mac`.

The framework's central claim is that **`Phi` factors through
the quotient `(F, G, tau) / (Galois action)`**, so isomorphism
classes of triples up to Galois conjugation are in bijection
with isomorphism classes of multi-sheeted graphs.

Testing this claim explicitly is `research_program.md` Ticket D2
(the Galois lift question).
