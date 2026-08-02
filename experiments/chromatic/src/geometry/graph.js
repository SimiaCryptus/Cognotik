// Adjacency graph builders over OKLab point-sets.
//
// All builders take an array of OKLab points ({ L, a, b }) and return an
// adjacency list: `adj[i]` is an array of neighbor indices. Graphs are
// undirected (edges appear in both endpoints' lists).

import { deltaEOK } from '../colorspace/distance.js';

function pairwiseDistances(points) {
  const n = points.length;
  const d = Array.from({ length: n }, () => new Array(n).fill(0));
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const dist = deltaEOK(points[i], points[j]);
      d[i][j] = dist;
      d[j][i] = dist;
    }
  }
  return d;
}

function emptyAdj(n) {
  return Array.from({ length: n }, () => []);
}

function addEdge(adj, i, j) {
  if (i === j) return;
  if (!adj[i].includes(j)) adj[i].push(j);
  if (!adj[j].includes(i)) adj[j].push(i);
}

// k-nearest-neighbor graph (symmetrized).
export function knn(points, k = 2) {
  const n = points.length;
  const adj = emptyAdj(n);
  if (n < 2) return adj;
  const d = pairwiseDistances(points);
  for (let i = 0; i < n; i++) {
    const order = [...Array(n).keys()].filter((j) => j !== i).sort((a, b) => d[i][a] - d[i][b]);
    for (let m = 0; m < Math.min(k, order.length); m++) {
      addEdge(adj, i, order[m]);
    }
  }
  return adj;
}

// Minimum spanning tree (Prim's algorithm) over the complete perceptual graph.
export function mst(points) {
  const n = points.length;
  const adj = emptyAdj(n);
  if (n < 2) return adj;
  const d = pairwiseDistances(points);
  const inTree = new Array(n).fill(false);
  const best = new Array(n).fill(Infinity);
  const parent = new Array(n).fill(-1);
  best[0] = 0;
  for (let iter = 0; iter < n; iter++) {
    let u = -1;
    for (let v = 0; v < n; v++) {
      if (!inTree[v] && (u === -1 || best[v] < best[u])) u = v;
    }
    inTree[u] = true;
    if (parent[u] !== -1) addEdge(adj, u, parent[u]);
    for (let v = 0; v < n; v++) {
      if (!inTree[v] && d[u][v] < best[v]) {
        best[v] = d[u][v];
        parent[v] = u;
      }
    }
  }
  return adj;
}

// A closed cycle 0-1-2-...-(n-1)-0 (topology from a `cycle` descriptor).
export function cycle(points) {
  const n = points.length;
  const adj = emptyAdj(n);
  if (n < 2) return adj;
  for (let i = 0; i < n; i++) {
    addEdge(adj, i, (i + 1) % n);
  }
  return adj;
}

// An open chain 0-1-2-...-(n-1).
export function chain(points) {
  const n = points.length;
  const adj = emptyAdj(n);
  for (let i = 0; i < n - 1; i++) {
    addEdge(adj, i, i + 1);
  }
  return adj;
}

// A hypercube-style grid adjacency for a rows x cols layout (row-major).
export function grid(points, rows, cols) {
  const adj = emptyAdj(points.length);
  const idx = (r, c) => r * cols + c;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if (c + 1 < cols) addEdge(adj, idx(r, c), idx(r, c + 1));
      if (r + 1 < rows) addEdge(adj, idx(r, c), idx(r + 1, c));
    }
  }
  return adj;
}

// Graph diagnostics used by topological invariants (§4.2).
export function isConnected(adj) {
  const n = adj.length;
  if (n === 0) return true;
  const seen = new Set([0]);
  const stack = [0];
  while (stack.length) {
    const u = stack.pop();
    for (const v of adj[u]) {
      if (!seen.has(v)) {
        seen.add(v);
        stack.push(v);
      }
    }
  }
  return seen.size === n;
}

export function edgeCount(adj) {
  let e = 0;
  for (const neighbors of adj) e += neighbors.length;
  return e / 2;
}

// Cycle rank (first Betti number) = E - V + C, where C = #components.
export function cycleRank(adj) {
  const n = adj.length;
  if (n === 0) return 0;
  const seen = new Set();
  let components = 0;
  for (let start = 0; start < n; start++) {
    if (seen.has(start)) continue;
    components++;
    const stack = [start];
    seen.add(start);
    while (stack.length) {
      const u = stack.pop();
      for (const v of adj[u]) {
        if (!seen.has(v)) {
          seen.add(v);
          stack.push(v);
        }
      }
    }
  }
  return edgeCount(adj) - n + components;
}

// Edge-set edit distance between two adjacency lists over the same nodes:
// number of edges present in one but not the other.
export function edgeEditDistance(a, b) {
  const key = (i, j) => (i < j ? `${i}-${j}` : `${j}-${i}`);
  const setOf = (adj) => {
    const s = new Set();
    adj.forEach((neighbors, i) => neighbors.forEach((j) => s.add(key(i, j))));
    return s;
  };
  const sa = setOf(a);
  const sb = setOf(b);
  let diff = 0;
  for (const e of sa) if (!sb.has(e)) diff++;
  for (const e of sb) if (!sa.has(e)) diff++;
  return diff;
}
