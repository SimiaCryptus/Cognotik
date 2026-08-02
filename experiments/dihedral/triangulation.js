// triangulation.js
// Delaunay triangulation in 2D (Bowyer-Watson) over projected points.
// Returns { triangles: [[i,j,k],...], edges: [{a,b,t1,t2}], adjacency }.
// For 3D manifolds we project onto a parameter plane or use convex-hull-ish
// projection; a simple and robust approach here is to triangulate the
// 2D parametric/projected coordinates and lift the connectivity to 3D.

// --- Bowyer-Watson Delaunay ---------------------------------------------
function circumcircle(ax, ay, bx, by, cx, cy) {
  const d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
  if (Math.abs(d) < 1e-12) return null;
  const ux =
    ((ax * ax + ay * ay) * (by - cy) +
      (bx * bx + by * by) * (cy - ay) +
      (cx * cx + cy * cy) * (ay - by)) /
    d;
  const uy =
    ((ax * ax + ay * ay) * (cx - bx) +
      (bx * bx + by * by) * (ax - cx) +
      (cx * cx + cy * cy) * (bx - ax)) /
    d;
  const r2 = (ax - ux) ** 2 + (ay - uy) ** 2;
  return { x: ux, y: uy, r2 };
}

export function delaunay2D(pts2d) {
  const n = pts2d.length;
  if (n < 3) return { triangles: [], edges: [] };

  // Super-triangle covering all points
  let minX = Infinity,
    minY = Infinity,
    maxX = -Infinity,
    maxY = -Infinity;
  for (const [x, y] of pts2d) {
    if (x < minX) minX = x;
    if (y < minY) minY = y;
    if (x > maxX) maxX = x;
    if (y > maxY) maxY = y;
  }
  const dx = maxX - minX || 1;
  const dy = maxY - minY || 1;
  const dmax = Math.max(dx, dy) * 10;
  const midX = (minX + maxX) / 2;
  const midY = (minY + maxY) / 2;

  const p = pts2d.map((q) => [q[0], q[1]]);
  const iA = p.length;
  p.push([midX - 2 * dmax, midY - dmax]);
  const iB = p.length;
  p.push([midX, midY + 2 * dmax]);
  const iC = p.length;
  p.push([midX + 2 * dmax, midY - dmax]);

  let tris = [[iA, iB, iC]];

  for (let i = 0; i < n; i++) {
    const [px, py] = p[i];
    const bad = [];
    for (let t = 0; t < tris.length; t++) {
      const [a, b, c] = tris[t];
      const cc = circumcircle(p[a][0], p[a][1], p[b][0], p[b][1], p[c][0], p[c][1]);
      if (cc && (px - cc.x) ** 2 + (py - cc.y) ** 2 <= cc.r2 + 1e-9) bad.push(t);
    }
    // find boundary of the polygonal hole
    const edgeCount = new Map();
    for (const t of bad) {
      const [a, b, c] = tris[t];
      for (const [u, v] of [
        [a, b],
        [b, c],
        [c, a],
      ]) {
        const key = u < v ? `${u},${v}` : `${v},${u}`;
        edgeCount.set(key, (edgeCount.get(key) || 0) + 1);
      }
    }
    const badSet = new Set(bad);
    tris = tris.filter((_, t) => !badSet.has(t));
    for (const [key, cnt] of edgeCount) {
      if (cnt === 1) {
        const [u, v] = key.split(',').map(Number);
        tris.push([u, v, i]);
      }
    }
  }

  // remove triangles that touch super-triangle vertices
  tris = tris.filter(([a, b, c]) => a < n && b < n && c < n);

  // build edge -> triangles map (interior edges have 2)
  const edgeMap = new Map();
  tris.forEach((tri, ti) => {
    const [a, b, c] = tri;
    for (const [u, v] of [
      [a, b],
      [b, c],
      [c, a],
    ]) {
      const key = u < v ? `${u},${v}` : `${v},${u}`;
      if (!edgeMap.has(key)) edgeMap.set(key, { a: Math.min(u, v), b: Math.max(u, v), tris: [] });
      edgeMap.get(key).tris.push(ti);
    }
  });

  const edges = [];
  for (const e of edgeMap.values()) {
    if (e.tris.length === 2) {
      edges.push({ a: e.a, b: e.b, t1: e.tris[0], t2: e.tris[1] });
    }
  }

  return { triangles: tris, edges };
}
