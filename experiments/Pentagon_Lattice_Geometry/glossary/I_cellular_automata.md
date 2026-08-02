# I. Cellular Automata — Detailed Reference

This document expands the **cellular automaton** vocabulary used in the project,
including the specialized **isometric** and **causal** variants relevant to discrete
spacetime models.

---

## Foundations

### Cellular Automaton (CA)

A **discrete dynamical system** on a lattice or graph, with:

- A set of **cells** (lattice/graph vertices), each in one of finitely many **states**.
- A **local update rule** determining each cell's new state from the states of itself and
  its neighbors.
- **Synchronous updates** at discrete time steps (in the basic version).

CAs are simultaneously:

- **Mathematical structures** with rich combinatorial behavior.
- **Computational models** (some are Turing-universal).
- **Physical models** (idealizing local interactions, conservation laws).

### Cell State

The value stored at a vertex. Most common: **binary** ($\sigma \in \{0, 1\}$), but can be
multi-valued ($\sigma \in \{0, 1, \ldots, k-1\}$).

### Neighborhood

The set of cells whose states influence the update of a given cell. Examples:

- **von Neumann neighborhood** in 2D: 4 nearest neighbors.
- **Moore neighborhood** in 2D: 8 neighbors (including diagonals).
- On a general graph: typically the graph-theoretic 1-neighborhood.

---

## Rule Types

### Outer-Totalistic Rule

A rule depending only on:

- The current cell's state.
- The **count** of neighbors in each state (not their specific positions).

For binary CA on a degree-$k$ graph, an outer-totalistic rule is specified by:

- **Birth set** $B \subseteq \{0, 1, \ldots, k\}$: dead cells with $b \in B$ live
  neighbors become alive.
- **Survival set** $S \subseteq \{0, 1, \ldots, k\}$: alive cells with $s \in S$ live
  neighbors stay alive.

Notation: "B/S" or "B$\ldots$/S$\ldots$" (e.g., Conway's Life is **B3/S23**).

### Birth/Survival Set

The pair $(B, S)$ specifying an outer-totalistic rule. The full rule is determined by
this data plus the underlying graph.

### Conway's Game of Life

The canonical CA: binary, 2D square lattice, Moore neighborhood, rule **B3/S23**:

- A dead cell with exactly 3 live neighbors becomes alive.
- A live cell with 2 or 3 live neighbors survives.
- All other cells become dead.

Despite this simplicity, Game of Life is **Turing-universal** and supports a vast
zoology of stable, periodic, and moving patterns.

---

## Patterns in CA

### Still Life

A pattern **unchanged by the update rule**. In Life: blocks, beehives, loaves, etc.

### Glider

A **stable moving pattern** that translates itself across the lattice at a fixed speed
and direction. The glider in Life is the canonical example — a 5-cell pattern moving
diagonally at speed $c/4$ (4 generations per cell).

Gliders are the "particles" of CA universe — used to encode information and construct
logic gates.

### Phase Transition

A **sharp qualitative change** in CA behavior as rule parameters (or graph parameters)
vary. Examples:

- Wolfram's **Class 1–4** classification by long-time behavior.
- **Edge of chaos**: rules near the boundary between order and disorder, often the most
  computationally interesting.
- **Percolation transitions** in spreading rules.

Phase transitions in CAs mirror those in statistical mechanics, providing combinatorial
models of critical phenomena.

---

## Computational Universality

### Turing-Universal

A CA is **Turing-universal** if it can simulate any Turing machine, hence any algorithm.
Conway's Game of Life is Turing-universal (proved by Berlekamp, Conway, Guy, and refined
by many others) via glider-gun-based computer constructions.

Implication: long-term behavior of universal CAs is **undecidable** — no algorithm can
predict the future state in general without simulating step by step.

---

## CA on Graphs

### Isometric Cellular Automaton (ICA)

A CA whose **update rule depends only on local graph geometry**, not on coordinates or
embedding. Equivalently: the rule is invariant under graph isomorphisms.

ICAs are the natural CA framework for **graphs that aren't regular lattices** — such as
multi-sheeted tilings. The rule must "see" only:

- The local adjacency structure.
- The states of local cells.

On a regular lattice with constant degree, outer-totalistic rules are automatically
isometric. On the project's adjacency graphs $\mathcal{G}$ for multi-sheeted tilings, the
ICA framework provides the right tool: rules can be specified by birth/survival sets
even when the graph isn't a simple lattice.

### Lazy Graph Generation

**On-demand construction** of graph vertices and edges as the CA dynamics expand. Used
when:

- The graph is infinite or very large.
- Only the active region matters at any given time.

The implementation: start with a seed (an initial vertex or small cluster), then expand
the graph step-by-step as the active CA region grows. New vertices are added with their
adjacency information cached.

This is essential for multi-sheeted tiling CAs, where the full graph may be enormous but
only the active region needs storage.

### Adjacency Oracle (recap)

An algorithmic procedure returning **neighbors of a vertex on demand**, with exact
arithmetic. The lazy graph generation relies on the adjacency oracle to compute each
new vertex's neighbors as needed.

---

## CA in Physics: Discrete Spacetime Models

### Causal Cellular Automaton

A CA whose update respects a **causal ordering**: information propagates only forward in
some discrete time direction, and the value of a cell at time $t+1$ depends only on
cells in its discrete causal past.

Causal CAs serve as **toy models for discrete spacetime**:

- The dependency graph has the structure of a directed acyclic graph (DAG).
- Information cannot propagate faster than a discrete "speed of light."
- Conservation laws can be built in by carefully designing the rule.

### Causal Dynamical Triangulation (CDT)

A quantum gravity approach using **causal triangulated spacetimes**: the dynamical
variables are triangulated 4-manifolds equipped with a causal/time foliation.

CDT shows **dimensional reduction**: macroscopic spacetime is 4D, but at the Planck
scale the **spectral dimension** flows to 2. This phenomenon — dimensional flow from a
discrete combinatorial structure — closely parallels what may happen in multi-sheeted
tiling models.

The connection: both CDT and multi-sheeted pentagonal tilings are **discrete causal
structures** (or quasi-causal: pentagonal tilings have a static graph but vortex
holonomy plays a similar role to causal structure) where the **emergent dimension**
differs from the embedding dimension.

---

## Vortex Effects in CAs

### Vortex Scattering

A **topology-induced rotation** of CA glider trajectories at vortex defects in the
underlying graph. Phenomenology:

- A glider moving toward a vortex passes by and emerges with **rotated direction**.
- The rotation angle equals the **sheet shift accumulated** around the vortex (e.g.,
  $36°$ per pentagon vortex).
- Multiple passes around the vortex accumulate phase — **classical analogue of
  Aharonov–Bohm**.

This phenomenon is a direct signature of the multi-sheeted structure: gliders couple to
the sheet-transition gauge field and acquire holonomy as they propagate.

---

## Why CAs Are a Natural Probe

The project uses CAs as a **diagnostic tool** for multi-sheeted tilings because:

1. **CAs are sensitive to graph structure**: behavior changes between, e.g., square
   lattice, hexagonal lattice, and Penrose tiling. Comparing CA dynamics on
   multi-sheeted pentagon graphs vs. flat-pentagon attempts vs. Penrose tiling reveals
   structural differences.

2. **CAs detect topological defects**: vortex scattering of gliders provides a direct,
   visual demonstration of the sheet-transition gauge field.

3. **CAs probe dynamical dimensions**: light-cone growth of CA patterns gives an
   independent estimate of the effective propagation dimension, complementing
   random-walk and spectral measurements.

4. **CAs connect to physics**: the parallel to CDT's dimensional flow suggests
   multi-sheeted tilings may exhibit analogous quantum-gravity-like behavior in their
   CA dynamics.

5. **CAs are computationally tractable**: they require only neighbor lookups, making
   them ideal for matrix-free computation on the project's lazy adjacency graphs.

The combination of **isometric** (geometry-only) rule specification, **lazy graph
generation** (efficient computation), and **vortex-aware dynamics** (sensitive to sheet
structure) makes CAs an essential tool in the project's diagnostic toolkit.
