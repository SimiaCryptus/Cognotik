# How to Extend the Multi-Sheeted Tiling Framework

    This document is a practical, code-first guide for extending the
    framework. It assumes you have read `README.md` and have run
    `experiment.mac` successfully at least once. Each section answers
    a "how do I..." question with explicit code snippets and pointers
    into the existing source files.

    ---

    ## 1. How do I add a new polygon type?

    The polygon is controlled by `N_GON` at the top of
    `experiment.mac`. Changing the value is sufficient for any regular
    `n`-gon with `n >= 3`. The geometry kernel
    (`spoke`, `edge_mid_rel`, `reflected_neighbor_center`) is fully
    parameterized by `n_int`.

    For irregular polygons or polygons with marked active edges (the
    "pinwheel polygons" of `pinwheels.md`), more work is needed.
    Here is the minimum patch:

    ```maxima
    /* Replace spoke() with explicit vertex list. */
    custom_vertices : [
         [1, 0],
         [1/2, sqrt(3)/2],
         [-1, 0],          /* irregular: vertex displaced */
         [0, -1]
    ] $
    spoke(k) := custom_vertices[mod(k, length(custom_vertices)) + 1] $
    n_int : length(custom_vertices) $

    /* Restrict to active edges: define an active-set predicate. */
    edge_active(k) := member(k, [0, 2]) $   /* only edges 0, 2 active */

    /* Patch add_neighbor() to skip inactive edges. */
    add_neighbor_orig : add_neighbor $
    add_neighbor(i, k) :=
         if edge_active(k) then add_neighbor_orig(i, k) else 0 $
    ```

    After this patch, the cluster will only expand along active edges
    -- exactly the restricted-expansion-family construction of
    `pinwheels.md` §3.

    ---

    ## 2. How do I add a new vortex (tau) rule?

    Define a new Maxima function `tau_rule_my_rule(i, k)` returning an
    integer (shift in `Z`). Then extend the dispatcher:

    ```maxima
    /* In Section 3 of experiment.mac, after the existing rules: */
    tau_rule_my_rule(i, k) := block(
         [c1 : cell_center[i], cx, cy],
         cx : float(c1[1]),
         cy : float(c1[2]),
         /* Your rule here. Example: shift +1 if center is in upper half. */
         if cy > 0 then 1 else 0) $

    /* Then patch the dispatcher: */
    tau_rule(i, k) :=
         if TAU_MODE = "my_rule" then tau_rule_my_rule(i, k)
         elseif TAU_MODE = "signed3" then tau_rule_signed3(i, k)
         elseif TAU_MODE = "cap"     then tau_rule_cap(i, k)
         elseif TAU_MODE = "everyn"  then tau_rule_everyn(i, k)
         elseif TAU_MODE = "signedn" then tau_rule_signedn(i, k)
         else (if n_int = 5 then tau_rule_every3(i, k)
               else tau_rule_everyn(i, k)) $

    /* And select it. */
    TAU_MODE : "my_rule" $
    ```

    Run `experiment.mac` and check that:

    1. The cluster expands as expected (no infinite recursion).
    2. The "vortex fraction" reported in Section 8 is non-zero.
    3. The holonomy distribution (Section 12) is non-trivial.

    A pathological rule may produce a degenerate cluster (e.g. all
    cells on sheet 0). The vortex fraction is the canary.

    ---

    ## 3. How do I sweep a new parameter?

    The `sweep_ngon.mac` template uses three override globals:

    ```
    EXPERIMENT_N_GON_OVERRIDE
    EXPERIMENT_PRESET_OVERRIDE
    EXPERIMENT_TAU_OVERRIDE
    ```

    To add a fourth (e.g. `BFS_DEPTH`), add a corresponding override
    in `experiment.mac` Section 0:

    ```maxima
    if ?boundp('EXPERIMENT_BFS_DEPTH_OVERRIDE) then
         BFS_DEPTH : EXPERIMENT_BFS_DEPTH_OVERRIDE $
    ```

    And in `sweep_ngon.mac`, set it inside the loop:

    ```maxima
    for depth in [3, 4, 5, 6] do block(
         EXPERIMENT_BFS_DEPTH_OVERRIDE : depth,
         batchload("experiment.mac"),
         /* harvest results */ ) $
    ```

    ---

    ## 4. How do I add a new measurement to the summary table?

    Three steps:

    1. **In `experiment.mac`**, compute the measurement and store it
       in a globally-named variable. By convention these are named
       `my_metric_value`.

    2. **In Section 22** (summary report), add a print line:

       ```maxima
       print("my_metric =", my_metric_value)$
       ```

    3. **In `sweep_ngon.mac`**, extend the row capture:

       ```maxima
       row : [
            nval, ...,
            if ?boundp('my_metric_value) then my_metric_value else 0,
            ...
       ] $
       ```

       And update the column header and CSV dump accordingly.

    ---

    ## 5. How do I run experiments at huge cluster sizes?

    The bottleneck for large clusters is the dense eigendecomposition
    in Section 4. Set:

    ```maxima
    EXPERIMENT_PRESET : "huge" $   /* or "xhuge" */
    ```

    The `huge` and `xhuge` presets automatically set `SKIP_EIG : true`,
    so Section 4 is replaced with the KPM approximation in Section 18.
    KPM scales as `O(N * M)` with `M ~ 256-384` moments, which is
    tractable for clusters up to `N ~ 10^5`.

    The cap on BFS depth is controlled by your machine's RAM. As a
    rule of thumb:

    | Preset  | Depth | Approx N | Approx RAM |
    |---------|-------|----------|------------|
    | tiny    | 1     | 20       | <100 MB    |
    | small   | 2     | 60       | 100 MB     |
    | medium  | 3     | 200      | 200 MB     |
    | large   | 4     | 1000     | 1 GB       |
    | huge    | 5     | 5000     | 4 GB       |
    | xhuge   | 6     | 20000    | 16 GB      |

    For `xhuge` runs you may want to disable Section 24 (distance webs)
    by setting `WEB_CLASSES : 0` -- the `O(N^2)` web construction
    dominates runtime at that scale.

    ---

    ## 6. How do I add 3D polyhedra?

    This is `research_program.md` Ticket C1 and is not yet implemented.
    The required changes:

    1. Replace `cell_center` from 2D `[x, y]` to 3D `[x, y, z]`.
    2. Replace `edge_mid_rel(k)` (edge midpoints in 2D) with
       `face_centroid_rel(k)` (face centroids in 3D). Each face is
       indexed by `k = 0..F-1` where `F` is the number of faces.
    3. Replace `reflected_neighbor_center` with a face reflection:
       given a face with normal vector `n` and signed distance `d`
       from the center, the neighbor's center is
       `c - 2 * (c . n - d) * n`. For a regular polyhedron, this
       reduces to a finite table.
    4. Extend `cell_fingerprint` to use three coordinate buckets.
    5. Replace the chirality bit `{0, 1}` with the orientation group
       index in `O(3) / SO(3)` (which is again `{0, 1}` for the regular
       polyhedra) for now.

    A skeleton of the data structures:

    ```maxima
    /* For the dodecahedron, vertices in Q(sqrt(5))^3. */
    dodec_vertices : [
         /* twenty vertices as exact triples */
         [1, 1, 1], [1, 1, -1], ... /* fill in */
    ] $

    /* The twelve face centroids relative to the body center. */
    dodec_face_centroids : [
         /* compute as the mean of the five vertices of each face */
    ] $

    dodec_face_normals : [
         /* outward unit normal vectors */
    ] $

    /* Reflection of center c across face k. */
    reflected_polyhedron_center(c, chir, k) := block(
         [normal : dodec_face_normals[k + 1],
          d : 1],  /* signed distance to face k */
         [c[1] - 2*(c[1]*normal[1] + c[2]*normal[2]
                    + c[3]*normal[3] - d) * normal[1],
          c[2] - 2*(c[1]*normal[1] + c[2]*normal[2]
                    + c[3]*normal[3] - d) * normal[2],
          c[3] - 2*(c[1]*normal[1] + c[2]*normal[2]
                    + c[3]*normal[3] - d) * normal[3]]) $
    ```

    This is approximately 200 lines of new code, all of which is
    structurally analogous to the 2D case.

    ---

    ## 7. How do I check whether my construction is reconnective?

    Three diagnostics from the existing code suffice:

    1. **Vortex fraction** (Section 8 output): if zero, the
       construction is Level 0 (full flat reconnection). If non-zero,
       it is Level 1 or higher.

    2. **Sheet set size** (Section 8 output): if exactly `1`, the
       sheet structure collapsed (you may have a bug). If `> 1` and
       finite at depth 5+, you have a finite-cover Level 1 case. If
       linearly growing in BFS depth, you may have Level 3 (tree-like
       structure).

    3. **Holonomy distribution** (Section 12 output): the residue
       distribution over `Z_n` should be approximately uniform for a
       reconnective construction. A peaked distribution (e.g. all
       residues `= 0`) indicates the construction is silently
       collapsing to Level 0.

    A passing example: pentagon with `signed3`, depth 4, produces
    vortex fraction `~ 0.67`, sheet set `{-2, -1, 0, 1, 2}`, and
    roughly uniform `Z_5` residue distribution.

    ---

    ## 8. How do I export results for plotting?

    The `sweep_ngon.mac` script writes a CSV-style block at the end of
    its output. Pipe its output through `grep` to extract:

    ```bash
    maxima -b sweep_ngon.mac > sweep.log
    sed -n '/^--- CSV ---$/,$p' sweep.log | tail -n +2 > sweep.csv
    ```

    Then plot with Python:

    ```python
    import pandas as pd
    import matplotlib.pyplot as plt

    df = pd.read_csv("sweep.csv")
    plt.scatter(df['n'], df['d_eff'])
    plt.xlabel('n (polygon)')
    plt.ylabel('d_eff')
    plt.savefig('d_eff_vs_n.pdf')
    ```

    For per-experiment internals (e.g. the full distance ring catalog
    of Section 23), grep for the section header and parse manually.
    A more structured export would require adding a `write_data` step
    at the end of `experiment.mac`, e.g.

    ```maxima
    with_stdout("ring_catalog.csv",
         print("k,sq_dist_r,sq_dist_s,ring_size,multiplicity"),
         for r in erdos_ring_data do
             print(apply(sconcat,
                 [r[1], ",", r[2][1], ",", r[2][2], ",",
                  r[4], ",", r[5]]))) $
    ```

    ---

    ## 9. How do I test my changes?

    The minimum smoke test:

    ```maxima
    EXPERIMENT_PRESET : "tiny" $
    N_GON : 5 $
    TAU_MODE : "signed3" $
    batchload("experiment.mac") $
    ```

    This runs in 5-10 seconds and produces output that you can
    inspect. The values to spot-check:

    - Bipartite check passes (for odd `n`).
    - Vortex fraction is in `(0.3, 0.7)`.
    - `d_eff_est in (1.5, 3.0)` (loose bound for tiny clusters).
    - No `error()` calls fired.
    - No `WARNING` messages about KPM bounds.

    For regression testing, save the summary line:

    ```
    grep "^d_eff estimate" tiny.log
    ```

    and diff against a reference value. Tolerances should be loose
    (`~ 0.1`) because random walks have stochastic variation.

    ---

    ## 10. Where do I look first when something breaks?

    Three high-yield diagnostic patterns:

    1. **Cluster size explodes**: the `tau_rule` you defined may be
       producing unbounded sheet indices, causing `cell_fingerprint`
       to never match an existing cell. Fix: reduce sheet index modulo
       a fiber group size.

    2. **Cluster size collapses (`N` very small)**: your `tau_rule`
       is producing collisions, or `reflected_neighbor_center` is
       returning the same cell for distinct `(i, k)` pairs. Fix: print
       neighbor centers and check for duplicates.

    3. **Holonomy is always zero**: your `tau_rule` is identically
       zero, or you forgot to set `TAU_MODE`. Fix: print
       `tau_rule(1, 0)` and check.

    For any other bug, instrument with `print()` statements in the
    relevant section. Maxima's interpreter prints everything between
    `print()` and the next `$` immediately, so this is the canonical
    debugging approach.
