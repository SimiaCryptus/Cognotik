// Designer objective compiler (designer.md §6.3–6.4).
//
// Translates a Designer document into:
//   - a scalar objective(vec) the solver minimizes,
//   - a residuals(vec) report for the constraints panel,
//   - a varMap that packs/unpacks free OKLab coords to/from a flat vector.
//
// Points are OKLab { L, a, b } indexed positionally by node order.

import {
  anchorError,
  lengthGroupError,
  angleGroupError,
  lengthBoundError,
  axisLockError,
  linkLength,
  linkAngle,
} from '../solver/constraints.js';

const SPACE_AXES = {
  OKLch: ['lightness', 'chroma', 'hue'],
  OKLab: ['L', 'a', 'b'],
};

const RAD2DEG = 180 / Math.PI;

// Which OKLab coords are pinned by a *hard* anchor. Hard anchors on OKLch
// dimensions are approximated by freezing the whole node (they couple
// multiple OKLab coords), so we only freeze individual L/a/b for OKLab-space
// hard anchors and freeze all three otherwise.
function frozenCoords(node) {
  const frozen = { L: false, a: false, b: false };
  for (const an of node.anchors) {
    if (!an.hard) continue;
    if (an.dimension === 'L' || an.dimension === 'lightness') frozen.L = true;
    else if (an.dimension === 'a') frozen.a = true;
    else if (an.dimension === 'b') frozen.b = true;
    else {
      // chroma/hue hard anchor: freeze a & b (they jointly define both)
      frozen.a = true;
      frozen.b = true;
    }
  }
  return frozen;
}

// Build a var-map: a flat vector over the un-frozen OKLab coords of each node.
function buildVarMap(doc) {
  const slots = []; // { nodeIndex, coord: 'L'|'a'|'b' }
  doc.nodes.forEach((node, i) => {
    const frozen = frozenCoords(node);
    for (const coord of ['L', 'a', 'b']) {
      if (!frozen[coord]) slots.push({ nodeIndex: i, coord });
    }
  });

  return {
    slots,
    pack() {
      return slots.map((s) => doc.nodes[s.nodeIndex].oklab[s.coord]);
    },
    // Reconstruct positional OKLab points from a packed vector, using the
    // document's current (frozen) coords for anything not in the vector.
    points(vec) {
      const pts = doc.nodes.map((n) => ({ L: n.oklab.L, a: n.oklab.a, b: n.oklab.b }));
      slots.forEach((s, k) => {
        pts[s.nodeIndex][s.coord] = vec[k];
      });
      return pts;
    },
    // Write a solved vector back into the document.
    apply(vec) {
      slots.forEach((s, k) => {
        doc.nodes[s.nodeIndex].oklab[s.coord] = vec[k];
      });
    },
  };
}

export function compileObjective(doc) {
  const nodeIndex = new Map(doc.nodes.map((n, i) => [n.id, i]));
  const spaceAxes = SPACE_AXES[doc.viewer.space] ?? SPACE_AXES.OKLch;
  // Lengths measured in full 3D to match the UI's linkLength readout:
  const lengthAxes = undefined;

  const varMap = buildVarMap(doc);
  const buildPoints = (vec) => varMap.points(vec);

  // Pre-translate constraints into index-based descriptors -------------

  const anchorConstraints = [];
  doc.nodes.forEach((node) => {
    node.anchors.forEach((a) => {
      anchorConstraints.push({
        node: nodeIndex.get(node.id),
        dimension: a.dimension,
        target: a.target,
        weight: a.weight ?? 1,
        hard: !!a.hard,
      });
    });
  });

  // per-link length bounds
  const boundConstraints = [];
  doc.links.forEach((link) => {
    if (link.minLength == null && link.maxLength == null) return;
    const ia = nodeIndex.get(link.a);
    const ib = nodeIndex.get(link.b);
    if (ia == null || ib == null) return;
    boundConstraints.push({
      a: ia,
      b: ib,
      minLength: link.minLength ?? null,
      maxLength: link.maxLength ?? null,
      weight: 1,
    });
  });

  // per-link axis locks
  const lockConstraints = [];
  doc.links.forEach((link) => {
    if (!link.lockAxis) return;
    const ia = nodeIndex.get(link.a);
    const ib = nodeIndex.get(link.b);
    if (ia == null || ib == null) return;
    lockConstraints.push({ a: ia, b: ib, lockAxis: link.lockAxis, weight: 1 });
  });

  // group descriptors
  const groupConstraints = doc.groups.map((g) => ({
    id: g.id,
    kind: g.kind,
    mode: g.mode,
    // angle groups store target in degrees in the UI; convert to radians.
    target:
      g.mode === 'fixed' && g.target != null
        ? g.kind === 'angle'
          ? g.target / RAD2DEG
          : g.target
        : g.target,
    weight: g.weight ?? 1,
    links: g.linkIds
      .map((lid) => doc.links.find((l) => l.id === lid))
      .filter(Boolean)
      .map((l) => [nodeIndex.get(l.a), nodeIndex.get(l.b)])
      .filter(([i, j]) => i != null && j != null),
  }));

  // Hard constraints get a large multiplier so the descent respects them.
  const HARD_W = 1e4;

  // Scalar objective ---------------------------------------------------
  function objective(vec) {
    const points = buildPoints(vec);
    let J = 0;

    for (const c of anchorConstraints) {
      const e = anchorError(points, c);
      J += c.hard ? HARD_W * e : e;
    }
    for (const c of boundConstraints) {
      J += lengthBoundError(points, c, lengthAxes);
    }
    for (const c of lockConstraints) {
      J += axisLockError(points, c, spaceAxes);
    }
    for (const g of groupConstraints) {
      if (g.kind === 'length') J += lengthGroupError(points, g, lengthAxes);
      else if (g.kind === 'angle') J += angleGroupError(points, g, doc.viewer.planeAxes);
    }

    return J;
  }

  // Residual report ----------------------------------------------------
  function residuals(vec) {
    const points = buildPoints(vec);

    let worstNode = null;
    let worstNodeErr = -1;
    const anchors = anchorConstraints.map((c) => {
      const err = Math.sqrt(anchorError(points, { ...c, weight: 1 }));
      if (err > worstNodeErr) {
        worstNodeErr = err;
        worstNode = doc.nodes[c.node].id;
      }
      return {
        node: doc.nodes[c.node].id,
        dimension: c.dimension,
        error: Math.abs(err),
      };
    });

    let worstGroup = null;
    let worstGroupErr = -1;
    const groups = groupConstraints.map((g) => {
      const err =
        g.kind === 'length'
          ? Math.sqrt(lengthGroupError(points, g, lengthAxes))
          : Math.sqrt(angleGroupError(points, g, doc.viewer.planeAxes));
      if (err > worstGroupErr) {
        worstGroupErr = err;
        worstGroup = g.id;
      }
      const members =
        g.kind === 'length'
          ? g.links.map(([i, j]) => linkLength(points[i], points[j], lengthAxes))
          : g.links.map(
              ([i, j]) => linkAngle(points[i], points[j], doc.viewer.planeAxes) * RAD2DEG
            );
      return { group: g.id, error: err, members };
    });

    const bounds = boundConstraints.map((c) => ({
      a: doc.nodes[c.a].id,
      b: doc.nodes[c.b].id,
      error: Math.sqrt(lengthBoundError(points, c, lengthAxes)),
    }));
    const locks = lockConstraints.map((c) => ({
      a: doc.nodes[c.a].id,
      b: doc.nodes[c.b].id,
      error: Math.sqrt(axisLockError(points, c, spaceAxes)),
    }));

    const total = objective(vec);

    return { total, anchors, groups, bounds, locks, worstNode, worstGroup };
  }

  return { objective, residuals, varMap };
}

// Write a solved vector back into the document via the compiled var-map.
export function applyVector(doc, varMap, vector) {
  varMap.apply(vector);
}
