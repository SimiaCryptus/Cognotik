# Chromatic Designer — Pre-DSL Interactive Layout Tool

## Design Specification

### 1. Purpose

Before the full DSL (§6 of `idea.md`) and its compilation pipeline exist,
Chromatic needs a way to _feel out_ the core thesis by hand: that arranging
colors as a constrained point-set in a perceptual colorspace produces
palettes whose relational structure is legible and controllable. The
**Designer** is that tool — a manual, direct-manipulation UI that sits on top
of the same conceptual machinery the DSL will later automate:

- **points** (`PaletteColor`s) placed by hand in a viewable colorspace slice,
- **links** (edges) drawn between points to declare relationships,
- **constraints** attached to points and links,
- a **numerical optimizer** that nudges points to minimize constraint error.

The Designer is deliberately _pre-DSL_. It is not a compiler front-end and it
does not parse text. It is a spatial, visual sandbox whose output — a set of
points, edges, and constraints — is exactly the kind of artifact the DSL
compiler is specified to _produce_ (`idea.md` §6.4). This makes the Designer
both a usability probe and a source of golden fixtures: hand-built layouts
that the eventual DSL should be able to reproduce.

### 2. Relationship to the Core Library

The Designer consumes the **Colorspace Core** and **Geometry Kernel**
(`idea.md` §3, §5) and the **Solver/Optimizer** primitives, but stays strictly
_below_ the DSL layer:

| Library layer      | Designer uses it for                                  |
| ------------------ | ----------------------------------------------------- |
| Colorspace Core    | Rendering the spectrum viewer; coloring points; gamut |
| Geometry Kernel    | `PaletteColor` / `Palette` point-set representation   |
| Solver / Optimizer | Minimizing constraint error over point positions      |
| DSL                | **Not used.** The Designer is the manual alternative. |

The Designer's internal state model must be a strict subset of, or trivially
convertible into, the compiled artifacts the DSL emits: a **constraint list**,
an **adjacency graph**, and (later) a **projection configuration**. This keeps
the Designer honest as a proving ground — anything it can express, the DSL
must eventually be able to express too, and vice versa.

### 3. Core Concepts

#### 3.1 Node

A **node** is a named point corresponding to a `PaletteColor` in OKLab/OKLch
space. Each node has:

- a stable **id** and a human-facing **name/label**,
- a **position** in the working colorspace (the full 3D coordinate, even
  though the viewer shows a 2D slice at any given time),
- an **anchor set** — zero or more color dimensions locked to fixed values
  (see §5.1),
- a **display color** — the node's actual color, always rendered accurately
  regardless of the current slice (see §4.2).

Nodes are the optimizer's free variables: any coordinate not anchored is
something the optimizer may move.

#### 3.2 Link

A **link** is an undirected edge between two nodes. It represents a declared
_relationship_ whose geometric realization (length and/or angle in the
working space) can be constrained. Links are the visual stand-in for the
metric and symmetry invariants of `idea.md` §4.2.

Each link has:

- the two node ids it connects,
- an optional **length constraint group** (see §5.2),
- an optional **angle constraint group** (see §5.2).

Links carry no color of their own; they are relational scaffolding.

#### 3.3 Constraint

A **constraint** is a soft target the optimizer tries to satisfy. Constraints
fall into two families in the Designer:

- **point constraints** (anchors): pin a node's dimension value.
- **link constraints**: relate lengths and/or angles across links.

All Designer constraints are _soft_ by default — expressed as error terms in
the objective (§6). Anchors may optionally be promoted to _hard_ (enforced by
construction, i.e. the coordinate is simply held fixed and removed from the
optimizer's variable set), mirroring the hard/soft split in `idea.md` §7.3.

### 4. The Spectrum Viewer

#### 4.1 Colorspace slice

The viewer presents a **2D slice** of a chosen 3D colorspace. The user picks:

- the **colorspace** (OKLab or OKLch primarily; Lab/Lch/HSL/HSV available for
  comparison),
- which two dimensions form the **plane** (e.g. OKLab `a`/`b`, or OKLch
  hue/chroma),
- the **value of the third (depth) dimension** for the slice plane (e.g.
  lightness = 0.6).

The plane is filled with a rendered gradient of the colorspace at that slice,
giving the user a perceptual backdrop to place points against. Out-of-gamut
regions are visually marked (hatched or desaturated) so the user can see where
sRGB clipping (`idea.md` §4.3) would bite.

#### 4.2 Depth handling via accurate point color

The viewer is 2D, but nodes live in 3D. Rather than hide the depth axis, the
Designer **colors every node with its true color** — the exact color of its
full 3D coordinate, not the color of the slice at its 2D projection. A node
whose depth (third-dimension) value differs from the current slice will
therefore _visually stand out_ against the slice backdrop: it will look "too
light," "too dark," or off-hue relative to its surroundings, which is exactly
the cue that tells the user the node is "in front of" or "behind" the current
slice plane.

This is the tool's answer to visualizing the z-axis without a second view: the
discrepancy between a node's rendered color and the slice color it sits over is
a direct, perceptual readout of its depth. No numeric depth readout is
_required_, though one is shown on selection (§7.3).

#### 4.3 Placement and interaction

- Users **place** a node by clicking an empty spot on the slice; its two
  in-plane coordinates come from the click position, and its depth coordinate
  defaults to the current slice value (so a freshly placed node initially
  matches its backdrop and then drifts as it's edited or optimized).
- Users **drag** a node to change its in-plane coordinates.
- Depth is adjusted either by (a) changing the slice value and re-dragging, or
  (b) a scroll/modifier-drag gesture that moves the node along the depth axis,
  with the node's color updating live to reflect the new depth.
- All movement is clamped or flagged against the sRGB gamut per the viewer's
  gamut display.

### 5. Constraints in Detail

#### 5.1 Point anchors

A node may anchor any subset of its color dimensions. In OKLch, that is any of
`{ lightness, chroma, hue }`; in OKLab, `{ L, a, b }`. Each anchor names:

- the **dimension**,
- the **target value**,
- a **strength** (soft weight) or a **hard** flag.

Semantics:

- A **hard** anchor removes that dimension from the optimizer's free variables
  for that node; the value is held exactly.
- A **soft** anchor adds an error term `w · (value − target)²` to the
  objective, letting the optimizer trade it off against other constraints.

Anchors are how the user says "this node's lightness is 0.6, no matter what"
or "this node should be roughly this hue." They correspond directly to the
semantic-role priors the DSL will later apply automatically (`idea.md` §6.5),
but here they are set by hand.

#### 5.2 Link constraint groups

Links express relationships through **shared constraint groups**. Two kinds:

- **length group**: all links in the group should have the _same length_ in
  the working space. (Length = Euclidean distance between the two nodes'
  current coordinates.)
- **angle group**: all links in the group should have the _same angle_ in the
  working plane. (Angle is measured in the current viewing plane; the
  depth-axis contribution is either ignored or included per a group setting —
  default: measured in-plane, since angle is a 2D notion in the viewer.)

A single link may belong to a length group, an angle group, both, or neither.
A group with only one member is inert.

UI affordances:

- Selecting two or more links and clicking **"same length"** puts them in a
  shared length group.
- Selecting two or more links and clicking **"same angle"** puts them in a
  shared angle group.
- Clicking **"same length & angle"** puts them in both — the fastest way to
  express "these edges are congruent," which is the building block of the
  symmetry invariants the DSL later declares wholesale (`idea.md` §4.2).

Each group's target may be:

- **free** (the optimizer picks the common value that minimizes total error —
  i.e. the group members should agree, but on what, is left open), or
- **fixed** (the user pins the shared length/angle to a specific value).

#### 5.3 Why length + angle, not raw coordinates

Constraining lengths and angles rather than positions is what makes the layout
_relational_ rather than absolute — the same philosophical stance as the DSL
(`idea.md` §1). A congruent-edge chain, for example, expresses "evenly spaced"
without the user ever typing a coordinate, and it survives being dragged around
as a whole.

### 6. The Optimizer

#### 6.1 Objective

The Designer builds a single scalar objective `J` as the weighted sum of all
active constraint error terms, exactly analogous to `idea.md` §7.1–7.2 but
assembled from the manual constraints rather than a compiled DSL program:

- **soft anchors** → `w · (value − target)²` per anchored dimension.
- **length group** → sum over group members of `(len_i − L*)²`, where `L*` is
  the group's fixed target or, for a free group, the mean length (so the term
  reduces to the variance of member lengths).
- **angle group** → analogous, using angular difference (wrapped to `[−π, π]`)
  against the group's fixed target or circular-mean angle.

Hard anchors contribute no term; they are enforced by construction by simply
not exposing that coordinate to the solver.

#### 6.2 Free variables

The optimizer's variables are the **unanchored coordinates of every node**.
A node with a hard lightness anchor in OKLch, for instance, contributes only
its `chroma` and `hue` as free variables.

#### 6.3 Solver behavior

- The solver reuses the library's default numerical optimizer (`idea.md` §7.3):
  gradient-based via finite differences, with a simulated-annealing fallback
  available for the non-smooth angle-wrapping terms.
- It runs **interactively**: the user can trigger a solve, and (optionally)
  watch points animate toward a lower-error configuration in real time as the
  solver iterates, with node colors updating live.
- It is **interruptible**: the user can stop, grab a node, drag it, and resume
  — treating the solver as an assistant rather than an authority.
- Every solve is **gamut-aware**: points pushed out of sRGB during a step are
  softly compressed back (or flagged), consistent with the viewer's gamut
  display.

#### 6.4 Error readout

After (and during) a solve, the Designer reports a breakdown of residual
error:

- total objective `J`,
- per-constraint residuals (which anchors / groups are still unsatisfied and
  by how much),
- a highlight overlay in the viewer marking the worst-offending nodes and
  links (e.g. a link far from its group's target length drawn in a warning
  color).

This makes over-constrained, unsatisfiable setups legible: the user sees
_which_ constraints are fighting, rather than getting a silently averaged
mush — the same design value stated for the DSL's conflict check
(`idea.md` §6.4).

### 7. UI Layout & Interaction Model

#### 7.1 Panels

- **Viewer** (center): the spectrum slice with nodes and links.
- **Slice controls** (top): colorspace picker, plane-axis pickers, depth-value
  slider, gamut-overlay toggle.
- **Inspector** (side): properties of the current selection (node or link) —
  name, coordinates, anchors, group memberships.
- **Constraint list** (side): all anchors and groups, each toggleable and
  weight-adjustable, with its live residual shown.
- **Solver controls** (bottom): run / step / stop, iteration cap, convergence
  threshold, live-animation toggle, and the total-`J` readout.

#### 7.2 Selection & multi-select

- Click selects a node or link; shift-click adds to selection.
- Multi-selecting links enables the "same length / angle / both" group
  actions (§5.2).
- Multi-selecting nodes enables batch anchor operations (e.g. "anchor all to
  lightness = 0.6").

#### 7.3 Node inspector detail

For a selected node, the inspector shows its full 3D coordinate (including the
depth value the viewer can't spatially show), its display color swatch, its
anchors with per-dimension hard/soft toggles and weights, and the list of
links it participates in.

### 8. State Model & Persistence

The Designer's document is a plain-data structure:

- `nodes`: id, name, coordinates (in a named working space), anchors.
- `links`: id, endpoints, group memberships.
- `groups`: id, kind (length | angle), target mode (free | fixed), target
  value, member link ids.
- `viewer`: current colorspace, plane axes, depth value, gamut-overlay flag.

This document is serializable to JSON and is explicitly designed to be the
**input the DSL compiler could also emit** (`idea.md` §6.4): a constraint list
plus an adjacency graph. A future tool can therefore either (a) load a
Designer document and re-solve it, or (b) _lift_ it into DSL source as a
starting point for text-based editing — closing the loop between the manual
tool and the declarative language.

### 9. Scope & Non-Goals

In scope:

- Manual node placement in a viewable colorspace slice.
- Manual link creation.
- Point anchors and link length/angle groups.
- Interactive, interruptible, gamut-aware optimization with error readout.
- JSON persistence convertible toward the DSL's compiled artifacts.

Out of scope (deferred to the DSL / later tools, per `idea.md` §6, §10):

- Text/DSL editing and parsing.
- Automatic semantic-role priors (`accent()`, `neutral()`, …); the Designer's
  equivalent is manual anchoring.
- Symmetry-group generators (`orbit`, `rotational k`); the Designer's
  equivalent is manually congruent link groups.
- Cross-space projection/distortion optimization (`optimize_for`); the
  Designer optimizes in a single working space only.
- Full export ecosystem (Sass/Tailwind/SVG); minimal JSON/CSS export only.

### 10. Success Criteria

The Designer succeeds if a user can, without writing any code or DSL:

1. Place a handful of named colors in OKLab/OKLch,
2. Declare "these edges are the same length" and "this node's lightness is
   fixed,"
3. Hit **solve** and watch the point-set relax into a configuration that
   honors those relations,
4. Read off _which_ constraints remain unsatisfied and by how much,

and thereby experience, hands-on, the central claim of Chromatic: that a
palette is best understood and built as **constrained relational geometry in a
perceptual space**, not as a list of hand-picked swatches.
