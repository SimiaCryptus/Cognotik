// kdtree.js
// Minimal 2D kd-tree over tile centroids (float coords) for fast
// nearest-neighbor picking and radius queries. Built once per lattice
// and cached on the lattice object.

export class KDTree {
  constructor(points) {
    // points: array of { x, y, idx }
    this.points = points;
    this.root = this._build(points.slice(), 0);
  }

  _build(pts, depth) {
    if (pts.length === 0) return null;
    const axis = depth % 2;
    pts.sort((a, b) => (axis === 0 ? a.x - b.x : a.y - b.y));
    const mid = pts.length >> 1;
    const node = pts[mid];
    return {
      point: node,
      axis,
      left: this._build(pts.slice(0, mid), depth + 1),
      right: this._build(pts.slice(mid + 1), depth + 1),
    };
  }

  // Return the idx of the nearest centroid to (x,y).
  nearest(x, y) {
    let best = { node: null, dist: Infinity };
    const search = (n) => {
      if (!n) return;
      const p = n.point;
      const d = (p.x - x) ** 2 + (p.y - y) ** 2;
      if (d < best.dist) best = { node: p, dist: d };
      const diff = n.axis === 0 ? x - p.x : y - p.y;
      const near = diff < 0 ? n.left : n.right;
      const far = diff < 0 ? n.right : n.left;
      search(near);
      if (diff * diff < best.dist) search(far);
    };
    search(this.root);
    return best.node ? best.node.idx : null;
  }

  // All idxs within radius r of (x,y).
  withinRadius(x, y, r) {
    const r2 = r * r;
    const out = [];
    const search = (n) => {
      if (!n) return;
      const p = n.point;
      const d = (p.x - x) ** 2 + (p.y - y) ** 2;
      if (d <= r2) out.push(p.idx);
      const diff = n.axis === 0 ? x - p.x : y - p.y;
      const near = diff < 0 ? n.left : n.right;
      const far = diff < 0 ? n.right : n.left;
      search(near);
      if (diff * diff <= r2) search(far);
    };
    search(this.root);
    return out;
  }
}

// Build (and cache) a kd-tree of tile centroids on a lattice object.
export function buildCentroidIndex(lattice) {
  if (lattice._kdtree) return lattice._kdtree;
  const pts = lattice.tiles.map((t) => ({
    x: t.centroidF[0],
    y: t.centroidF[1],
    idx: t.index,
  }));
  lattice._kdtree = new KDTree(pts);
  return lattice._kdtree;
}
