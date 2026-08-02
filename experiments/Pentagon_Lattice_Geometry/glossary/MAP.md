# Conceptual Map of the Multi-Sheeted N-gon Tiling Project

This document visualizes the relationships between concepts defined in `glossary.md`
using a variety of Mermaid diagrams. Each diagram highlights a different facet of
the project's intellectual architecture.

---

## 1. Top-Level Thematic Architecture

A high-level flowchart showing how the major glossary sections feed into one another.
The reconnection framework sits at the center, drawing from algebra, geometry, and
group theory, and feeding into physics, computation, and dimensional analysis.

```mermaid
flowchart TB
    A[A. Algebra & Number Theory<br/>ℚ√5, ℤφ, Galois]
B[B. Geometric Constructions<br/>polygons, polyhedra, tilings]
D[D. Group Theory<br/>O n, SU 2, Spin]
E[E. Topology & Bundles<br/>covers, holonomy, defects]
L[L. Reconnection Framework<br/>core synthesis]
F[F. Dimensions & Scaling<br/>d_eff, d_spec, d_w]
G[G. Spectral Graph Theory<br/>Δ, DOS, KPM]
H[H. Fractals]
I[I. Cellular Automata]
J[J. Physics Connections]
K[K. Extremal Geometry<br/>Erdős, unit distances]
M[M. Algorithmic]
O[O. Project Terminology<br/>signed3, vortices]

A --> L
B --> L
D --> L
E --> L
L --> O
L --> F
L --> G
L --> K
F --> J
G --> F
E --> J
D --> J
O --> I
L --> M
G --> M
H -. analogy .-> F
B --> H
```

---

## 2. The Algebraic Backbone: ℚ(√5) and Its Children

A class-style diagram showing the algebraic hierarchy that underwrites pentagonal
tilings. Each box names a concept and its key invariants.

```mermaid
classDiagram
    class Rationals {
        ℚ
        characteristic 0
    }
    class QuadraticField {
        ℚ √d
        degree 2
        discriminant d or 4d
    }
    class RealQuadraticField {
        ℚ √5
        discriminant 5
        totally real
    }
    class RingOfIntegers {
        ℤ φ
        PID and UFD
        Pell solutions
    }
    class GoldenRatio {
        φ 1+√5 /2
        φ² = φ + 1
        fundamental unit
    }
    class GaloisConjugate {
        ψ 1−√5 /2
        ψ = −1/φ
        σ φ = ψ
    }
    class UnitGroup {
        ±φ^k
        k ∈ ℤ
    }
    class NormForm {
        N a+bφ
        multiplicative
    }

    Rationals <|-- QuadraticField
    QuadraticField <|-- RealQuadraticField
    RealQuadraticField *-- RingOfIntegers
    RingOfIntegers *-- GoldenRatio
    RingOfIntegers *-- GaloisConjugate
    RingOfIntegers *-- UnitGroup
    RingOfIntegers *-- NormForm
```

---

## 3. The Four-Level Reconnection Hierarchy

The central classification of the project, displayed as a decision tree. Each branch
corresponds to a glossary "Criterion" from section L.

```mermaid
flowchart TD
    Start([Polygon / Polyhedron])
    C1{Criterion 1<br/>Orientation group<br/>finite?}
    C2{Criterion 2<br/>Single irrational<br/>base?}
    C3{Criterion 3<br/>Angular deficit<br/>zero?}
    L0[Level 0<br/>Full reconnection<br/>e.g. square, hexagon]
    L1[Level 1<br/>Multi-sheeted full<br/>e.g. regular pentagon]
    L2[Level 2<br/>Edge-restricted<br/>pinwheel polygons]
    L3[Level 3<br/>Non-reconnective<br/>infinite tree]
    Start --> C1
    C1 -- no --> L3
    C1 -- yes --> C2
    C2 -- no --> L2
    C2 -- yes --> C3
    C3 -- yes --> L0
    C3 -- no --> L1
    L1 -. requires .-> Sheets[Multi-sheeted cover<br/>fiber group G]
    L2 -. requires .-> ActiveSet[Active edge subset E_A]
```

---

## 4. From Pentagon to Multi-Sheeted Cover

A flowchart tracing how the 36° angular deficit of the regular pentagon forces the
multi-sheeted construction, ultimately giving rise to spinor-like holonomy.

```mermaid
flowchart LR
    P[Regular pentagon<br/>interior 108°]
D[Angular deficit<br/>δ = 36°]
V[Vortex at vertex<br/>topological defect]
S[Sheet transitions<br/>τ e ∈ G]
B[Discrete principal<br/>G-bundle]
H[Holonomy around<br/>vortex loop]
Sp[Spinor-like<br/>holonomy<br/>loop² = identity]
SR[signed3 rule<br/>shifts in −1,0,+1<br/>vortex fraction 2/3]

P --> D --> V --> S --> B --> H --> Sp
S --> SR
```

---

## 5. Group-Theoretic Web

A graph of the finite and continuous groups appearing in the project, showing the
chain of covers and inclusions.

```mermaid
graph LR
    On[O n]
    SOn[SO n]
    Spin[Spin n]
    SU2[SU 2]
    SO3[SO 3]
    I[Icosahedral I ≅ A5]
BI[Binary icosahedral 2I]
O[Octahedral O ≅ S4]
T[Tetrahedral T ≅ A4]
Dn[Dihedral D_n]
Cn[Cyclic ℤ_n]
Sn[Symmetric S_n]
An[Alternating A_n]
Q[Quaternion group]
K4[Klein four-group]

On --> SOn
Spin --> SOn
SU2 --> SO3
SO3 --> I
SO3 --> O
SO3 --> T
SO3 --> Dn
Dn --> Cn
SU2 --> BI
BI --> I
Sn --> An
An --> I
An --> T
Q --> K4
```

---

## 6. Topology Stack Behind Sheet Transitions

A class diagram of the topology section: how covering spaces, bundles, and
connections combine to formalize "sheets".

```mermaid
classDiagram
    class CoveringSpace {
        π M to X
        local homeomorphism
        sheets above each point
    }
    class BranchedCover {
        ramification points
        multi-valued
    }
    class DoubleCover {
        2-fold
        SU2 to SO3
    }
    class PrincipalGBundle {
        fiber group G
        G-action on fibers
    }
    class DiscreteBundle {
        combinatorial version
        tiles as sheets
    }
    class Connection {
        parallel transport
    }
    class GaugeConnection {
        lattice gauge theory
        edge group elements
    }
    class Holonomy {
        loop transport
        element of G
    }
    class Monodromy {
        holonomy around loop
    }

    CoveringSpace <|-- BranchedCover
    CoveringSpace <|-- DoubleCover
    CoveringSpace <|-- PrincipalGBundle
    PrincipalGBundle <|-- DiscreteBundle
    Connection <|-- GaugeConnection
    DiscreteBundle *-- GaugeConnection
    GaugeConnection --> Holonomy
    Holonomy <|-- Monodromy
```

---

## 7. Dimensions: Three Numbers, One Relation

A focused diagram on the Alexander–Orbach relation tying the three dimensional
exponents together.

```mermaid
flowchart TB
    Geo[Adjacency graph 𝒢]
    BFS[BFS volume V r ~ r^d_eff]
RW[Random walk return P_0 t]
MSD[Mean squared displacement<br/>⟨Δx²⟩ ~ t^ 2/d_w]

Geo --> BFS
Geo --> RW
Geo --> MSD
BFS --> Deff[d_eff<br/>effective / connectivity]
RW --> Dspec[d_spec<br/>spectral]
MSD --> Dw[d_w<br/>walk]

Deff --- AO[Alexander–Orbach<br/>d_spec = 2 d_eff / d_w]
Dspec --- AO
Dw --- AO

AO --> Flow[Dimensional flow<br/>UV ≠ IR]
Flow --> Phys[Asymptotic safety<br/>CDT analogies]
```

---

## 8. Spectral Pipeline (Computational)

Sequence diagram showing how an adjacency graph becomes a density of states and
thence a dimensional exponent.

```mermaid
sequenceDiagram
    participant Tile as Tile expansion
    participant Oracle as Adjacency oracle
    participant Graph as Sparse adjacency A
    participant Lap as Laplacian Δ = D − A
    participant KPM as KPM Chebyshev
    participant DOS as Density of states ρ λ
    participant Fit as Power-law fit
    Tile ->> Oracle: generator application
    Oracle ->> Graph: lazy neighbor lookup
    Graph ->> Lap: assemble D and subtract
    Lap ->> KPM: matrix-vector products
    KPM ->> DOS: Chebyshev moments
    DOS ->> Fit: low-λ scaling
    Fit -->> Tile: d_spec estimate
```

---

## 9. State Machine: Random Walk on the Tiling

A state diagram capturing the diffusion regimes referenced by walk dimension.

```mermaid
stateDiagram-v2
    [*] --> Initialized
    Initialized --> Diffusing: start walk at seed
    Diffusing --> SubDiffusion: d_w > 2
    Diffusing --> Brownian: d_w = 2
    Diffusing --> SuperDiffusion: d_w < 2
    SubDiffusion --> Mixing: t to infinity
    Brownian --> Mixing: t to infinity
    SuperDiffusion --> Mixing: t to infinity
    Mixing --> Stationary: reach π
    Stationary --> [*]
```

---

## 10. Erdős & Extremal Distance Geometry

How the pentagonal lattice plugs into classical combinatorial-geometry problems.

```mermaid
flowchart LR
    Lat[Projected lattice 𝓛<br/>vertices in ℤφ]
    Norm[Norm-multiplicativity<br/>UFD factorization]
    Ring[Distance rings<br/>high multiplicity]
    Web[Distance web]
    Eng[Degeneracy engine]
    Pin[Pinwheel phenomenon]
    Lat --> Norm --> Ring --> Web
    Norm --> Eng --> Pin
    Web --> Erdos[Erdős distinct<br/>distances problem]
    Web --> Unit[Unit distance<br/>problem]
    Erdos --> GK[Guth–Katz<br/>Ω n/log n]
Unit --> SzT[Spencer–Szemerédi–Trotter<br/>O n^4/3]
Web -. fractal analogue .-> Falc[Falconer distance<br/>problem]
```

---

## 11. Physics Mind-Map

A mind-map view of the physics connections.

```mermaid
mindmap
  root((Physics<br/>Connections))
    Quasicrystals
      Shechtman 1984
      Penrose tiling
      Cut and project
      AKN 3D tiling
      Diffraction 5-fold
    Gauge theory
      Sheet transitions as gauge
      BF theory
      Aharonov–Bohm
      Lattice gauge
    Quantum gravity
      Asymptotic safety
      CDT
      Loop quantum gravity
      Spin networks
      Dimensional reduction
    Topological matter
      Anyons
      Braid statistics
      Topological insulator
      Topological computation
    Symmetry
      Mermin–Wagner
      CFT
      Bloch theorem
    Frustration
      Pyrochlore lattice
      Geometric frustration
```

---

## 12. Fractal Dimensions Reference

A small comparison graph for fractals mentioned in section H.

```mermaid
flowchart LR
    IFS[Iterated function system] --> Frac[Fractal]
    Frac --> Cantor[Cantor set<br/>log2/log3]
    Frac --> Koch[Koch snowflake<br/>log4/log3]
    Frac --> KochT[Koch tetrahedron<br/>log6/log3]
    Frac --> Sier[Sierpiński triangle<br/>log3/log2]
    Frac --> Carp[Sierpiński carpet<br/>log8/log3]
    Frac --> Tetrix[Sierpiński tetrahedron<br/>exactly 2]
    Frac --> Menger[Menger sponge<br/>log20/log3]
    Frac --> Box[Box-counting<br/>dimension]
    Frac --> Haus[Hausdorff<br/>dimension]
```

---

## 13. Cellular Automata on Sheeted Tilings

How CA concepts connect to the project's topological structure.

```mermaid
flowchart TB
    Tiling[Sheeted tiling] --> ICA[Isometric CA<br/>local geometric rule]
    ICA --> OT[Outer-totalistic rule<br/>birth/survival sets]
    OT --> Life[Conway's Game of Life]
    Life --> Glider
    Life --> Still[Still life]
    Glider --> VS[Vortex scattering<br/>at sheet defects]
    ICA --> CCA[Causal CA] --> CDT[Causal dynamical<br/>triangulation]
    ICA --> Lazy[Lazy graph generation]
    ICA --> Univ[Turing-universal?]
    OT --> Phase[Phase transition]
```

---

## 14. Spinor-Like Holonomy Cycle (Sequence View)

A sequence diagram showing why one loop around a vortex returns the "wrong" sheet
and two loops restore identity — a discrete spinor.

```mermaid
sequenceDiagram
    participant Walker
    participant Vertex as Vortex vertex
    participant Sheet0
    participant Sheet1
    Walker ->> Sheet0: start on sheet 0
    Walker ->> Vertex: traverse loop 1 around vortex
    Vertex -->> Walker: apply τ shift
    Walker ->> Sheet1: now on sheet 1<br/>analogue of −1
    Walker ->> Vertex: traverse loop 2 around vortex
    Vertex -->> Walker: apply τ shift again
    Walker ->> Sheet0: back to sheet 0<br/>identity restored
    Note over Walker, Sheet0: Discrete SU 2 → SO 3 analogue
```

---

## 15. The Computational Toolkit

The algorithmic stack used to actually evaluate reconnection and dimensions.

```mermaid
flowchart LR
    Sym[Symbolic computation<br/>Maxima] --> Exact[Exact arithmetic<br/>over ℤφ]
    Exact --> Oracle[Adjacency oracle]
    Oracle --> Hash[Spatial hashing]
    Hash --> Memo[Memoization]
    Memo --> Sparse[Sparse adjacency A]
    Sparse --> SparseLA[Sparse linear algebra]
    SparseLA --> Lanc[Lanczos algorithm]
    SparseLA --> ShiftInv[Shift-invert]
    SparseLA --> KPM2[KPM Chebyshev DOS]
    Exact -. avoids .-> FP[Floating-point<br/>topological tearing]
```

---

## 16. Quick Symbol Lookup

A class-like reference for the symbols of section N, grouped by role.

```mermaid
classDiagram
    class AlgebraSymbols {
        ℚ rationals
        ℚ √5 quadratic field
        ℤφ ring of integers
        φ golden ratio
        ψ Galois conjugate
        ζ_n root of unity
        N · field norm
        σ Galois automorphism
        α algebraic number
    }
    class GeometrySymbols {
        θ interior angle
        δ angular deficit
        𝓛 projected lattice
        𝓜 covering space
        π projection
        χ Euler characteristic
    }
    class GroupBundleSymbols {
        Γ orientation group
        G fiber group
        2I binary icosahedral
        τ e sheet transition
    }
    class GraphSpectrumSymbols {
        𝒢 adjacency graph
        Δ graph Laplacian
        ρ λ density of states
        k_close loop closure count
    }
    class DimensionSymbols {
        d_eff effective
        d_spec spectral
        d_w walk
    }
    class FamilySymbols {
        𝓕_A restricted family
        𝔽 algebraic field
        ℤ^n hypercubic lattice
    }
```

---

## 17. Cross-Document Citation Graph

Which project documents primarily develop which clusters of glossary concepts.

```mermaid
flowchart LR
    idea[idea.md]
    affine[affine.md]
    poly[polyhedra.md]
    pin[pinwheels.md]
    erdos[erdos.md]
    ins[insights.md]
    sweep[sweep_ngon.md]
    ana[analysis.md]
    exp[experiment.md]
    idea --> Frame[Reconnection framework L]
    affine --> Frame
    affine --> Alg[Algebra A]
    poly --> Geom[3D Geometry B,C]
    poly --> Grp[Group theory D]
    pin --> Pinwheel[Pinwheel & extremal K]
    erdos --> Pinwheel
    ins --> Topo[Topology & bundles E]
    ins --> Phys[Physics J]
    sweep --> Comp[Computation M]
    ana --> Dim[Dimensions F]
    ana --> Spec[Spectral G]
    exp --> Comp
    exp --> Spec
```

---

## Reading Suggestions

- For a **first pass**, read diagrams 1, 3, and 4 to get the macro story.
- For an **algebraic deep-dive**, follow 2, 10, and 16.
- For the **physics/topology side**, read 4, 5, 6, 11, and 14.
- For a **computational orientation**, read 7, 8, 9, 13, and 15.
- Diagram 17 is a map back into the project's other markdown files.
