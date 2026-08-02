// pathfind.js
// Shortest-path tools over the lattice adjacency graph.
//
// Tiles are nodes; an edge exists between a tile and each of its
// non-null neighbors. Shortest paths are computed purely by HOP COUNT
// (breadth-first search): every edge has weight 1, regardless of the
// geometric distance between centroids.
//
// We report the hop count of the chosen path, the number of distinct
// minimum-hop paths, the net sheet shift, and the straight-line
// (Euclidean) distance between the endpoints.
// ── Diagnostics ─────────────────────────────────────────────────────────
// Toggle this to control how chatty the pathfinder is. You can also set
// window.PATHFIND_DEBUG = true/false at runtime from the console.
let PATHFIND_DEBUG = true;
function dbg(...args) {
  const on =
    typeof window !== 'undefined' && window.PATHFIND_DEBUG != null
      ? window.PATHFIND_DEBUG
      : PATHFIND_DEBUG;
  if (on) console.log('[pathfind]', ...args);
}
function dbgGroup(label) {
  const on =
    typeof window !== 'undefined' && window.PATHFIND_DEBUG != null
      ? window.PATHFIND_DEBUG
      : PATHFIND_DEBUG;
  if (on && console.groupCollapsed) console.groupCollapsed('[pathfind]', label);
}
function dbgGroupEnd() {
  const on =
    typeof window !== 'undefined' && window.PATHFIND_DEBUG != null
      ? window.PATHFIND_DEBUG
      : PATHFIND_DEBUG;
  if (on && console.groupEnd) console.groupEnd();
}
// Returns true if `b` appears in tile `a`'s neighbor list.
function isNeighbor(lattice, a, b) {
  const nbrs = lattice.tiles[a].neighbors;
  for (let k = 0; k < nbrs.length; k++) {
    if (nbrs[k] === b) return true;
  }
  // Treat adjacency as undirected: also check b's list for a.
  const rnbrs = lattice.tiles[b].neighbors;
  for (let k = 0; k < rnbrs.length; k++) {
    if (rnbrs[k] === a) return true;
  }
  return false;
}
// Audit the adjacency graph for the kinds of structural defects that
// silently break BFS:
//   • out-of-range neighbor indices
//   • self-loops
//   • ASYMMETRIC edges (a→b present but b→a missing) — the most common
//     cause of "adjacent tiles report the wrong hop count".
//   • duplicate neighbor entries
// Returns a report object and also logs a summary.
export function auditAdjacency(lattice) {
  const tiles = lattice.tiles;
  const n = tiles.length;
  const asymmetric = [];
  const outOfRange = [];
  const selfLoops = [];
  const duplicates = [];
  for (let a = 0; a < n; a++) {
    const nbrs = tiles[a].neighbors;
    const seen = new Set();
    for (let k = 0; k < nbrs.length; k++) {
      const b = nbrs[k];
      if (b === null) continue;
      if (b < 0 || b >= n) {
        outOfRange.push({ a, slot: k, b });
        continue;
      }
      if (b === a) {
        selfLoops.push({ a, slot: k });
        continue;
      }
      if (seen.has(b)) duplicates.push({ a, slot: k, b });
      seen.add(b);
      if (!isNeighbor(lattice, b, a)) {
        asymmetric.push({ a, b, slot: k });
      }
    }
  }
  const report = {
    tileCount: n,
    asymmetric,
    outOfRange,
    selfLoops,
    duplicates,
    ok:
      asymmetric.length === 0 &&
      outOfRange.length === 0 &&
      selfLoops.length === 0 &&
      duplicates.length === 0,
  };
  dbgGroup(`auditAdjacency (${n} tiles)`);
  if (report.ok) {
    dbg('adjacency graph looks structurally sound ✔');
  } else {
    if (asymmetric.length)
      dbg(`⚠ ${asymmetric.length} ASYMMETRIC edge(s):`, asymmetric.slice(0, 20));
    if (outOfRange.length)
      dbg(`⚠ ${outOfRange.length} out-of-range neighbor index(es):`, outOfRange.slice(0, 20));
    if (selfLoops.length) dbg(`⚠ ${selfLoops.length} self-loop(s):`, selfLoops.slice(0, 20));
    if (duplicates.length)
      dbg(`⚠ ${duplicates.length} duplicate neighbor entr(ies):`, duplicates.slice(0, 20));
  }
  dbgGroupEnd();
  return report;
}

function edgeLen(lattice, a, b) {
  const ca = lattice.tiles[a].centroidF;
  const cb = lattice.tiles[b].centroidF;
  return Math.hypot(ca[0] - cb[0], ca[1] - cb[1]);
}
// Build (and cache) a reverse-adjacency list so BFS can treat the graph
// as UNDIRECTED. This protects hop counts against any residual
// asymmetric edges (e.g. one-directional fractal links): if a→b exists
// but b→a does not, b can still reach a in one hop.
function getReverseAdj(lattice) {
  if (lattice._reverseAdj) return lattice._reverseAdj;
  const tiles = lattice.tiles;
  const rev = new Array(tiles.length);
  for (let i = 0; i < tiles.length; i++) rev[i] = [];
  for (let a = 0; a < tiles.length; a++) {
    const nbrs = tiles[a].neighbors;
    for (let k = 0; k < nbrs.length; k++) {
      const b = nbrs[k];
      if (b === null || b < 0 || b >= tiles.length || b === a) continue;
      if (!rev[b].includes(a)) rev[b].push(a);
    }
  }
  lattice._reverseAdj = rev;
  return rev;
}

// BFS from `start`. Every edge counts as one hop.
// Returns:
//   dist:  Int32Array of hop distance to each node (-1 if unreachable).
//   preds: Array<number[]> of predecessor nodes that lie on SOME
//          minimum-hop path.
function bfsAllShortest(lattice, start) {
  const tiles = lattice.tiles;
  const n = tiles.length;
  const dist = new Int32Array(n);
  dist.fill(-1);
  const preds = new Array(n);
  for (let i = 0; i < n; i++) preds[i] = [];
  const rev = getReverseAdj(lattice);

  dist[start] = 0;
  const queue = [start];
  let head = 0;
  let visited = 1;
  let edgesRelaxed = 0;

  while (head < queue.length) {
    const u = queue[head++];
    const du = dist[u];
    const nbrs = tiles[u].neighbors;
    // Relax forward neighbors AND reverse neighbors so the graph is
    // walked as undirected (one-directional links still count as a hop).
    const relax = (v) => {
      if (v === null) return;
      edgesRelaxed++;
      if (dist[v] === -1) {
        // First time we reach v: it's on a shortest path through u.
        dist[v] = du + 1;
        preds[v] = [u];
        queue.push(v);
        visited++;
      } else if (dist[v] === du + 1) {
        // Tied minimum-hop route: record u as an alternative predecessor.
        if (!preds[v].includes(u)) preds[v].push(u);
      }
    };
    for (let k = 0; k < nbrs.length; k++) relax(nbrs[k]);
    const rnbrs = rev[u];
    for (let k = 0; k < rnbrs.length; k++) relax(rnbrs[k]);
  }
  dbg(`bfs from #${start}: visited ${visited}/${n} tiles, relaxed ${edgesRelaxed} edges`);
  return { dist, preds };
}

// Reconstruct all minimum-hop paths from start to end as arrays
// of tile indices. Capped at `maxPaths` to avoid combinatorial explosion.
function reconstructAll(preds, start, end, maxPaths = 64) {
  if (end === start) return [[start]];
  const paths = [];
  const stack = [[end]];
  while (stack.length > 0 && paths.length < maxPaths) {
    const path = stack.pop();
    const node = path[path.length - 1];
    if (node === start) {
      paths.push([...path].reverse());
      continue;
    }
    const ps = preds[node];
    if (!ps || ps.length === 0) {
      // Dead-end branch that does not reach `start`; discard it so it
      // can never become the representative path.
      continue;
    }
    for (const p of ps) {
      // Guard against revisiting a node within the same partial path
      // (defensive; BFS dist should already preclude cycles).
      if (!path.includes(p)) stack.push([...path, p]);
    }
  }
  return paths;
}

// Count the number of distinct minimum-hop paths to `end`.
function countShortest(preds, start, end) {
  const memo = new Map();
  function count(node) {
    if (node === start) return 1;
    if (memo.has(node)) return memo.get(node);
    let total = 0;
    for (const p of preds[node]) total += count(p);
    memo.set(node, total);
    return total;
  }
  if (end === start) return 1;
  if (preds[end].length === 0) return 0;
  return count(end);
}

// Set of all tile indices that lie on SOME minimum-hop path.
function shortestPathTiles(preds, start, end) {
  const onPath = new Set();
  if (end === start) {
    onPath.add(start);
    return onPath;
  }
  const stack = [end];
  onPath.add(end);
  while (stack.length > 0) {
    const node = stack.pop();
    if (node === start) continue;
    for (const p of preds[node]) {
      if (!onPath.has(p)) {
        onPath.add(p);
        stack.push(p);
      }
    }
  }
  return onPath;
}

// Net sheet shift along a path (sum of neighborSheetDeltas), mod group.
function pathSheetShift(lattice, path) {
  let shift = 0;
  for (let i = 0; i + 1 < path.length; i++) {
    const t = lattice.tiles[path[i]];
    const next = path[i + 1];
    for (let k = 0; k < t.neighbors.length; k++) {
      if (t.neighbors[k] === next) {
        shift += t.neighborSheetDeltas[k] | 0;
        break;
      }
    }
  }
  const g = Math.max(lattice.groupOrder, 1);
  return ((shift % g) + g) % g;
}

function euclidean(lattice, a, b) {
  const ca = lattice.tiles[a].centroidF;
  const cb = lattice.tiles[b].centroidF;
  return Math.hypot(ca[0] - cb[0], ca[1] - cb[1]);
}

// Total geometric length of a path.
function pathLength(lattice, path) {
  let len = 0;
  for (let i = 0; i + 1 < path.length; i++) {
    len += edgeLen(lattice, path[i], path[i + 1]);
  }
  return len;
}

// Top-level: compute everything needed for the UI + rendering.
// Returns null if inputs are invalid.
export function computePath(lattice, start, end) {
  if (
    start == null ||
    end == null ||
    start < 0 ||
    end < 0 ||
    start >= lattice.tiles.length ||
    end >= lattice.tiles.length
  ) {
    dbg('computePath: invalid inputs', { start, end, n: lattice && lattice.tiles.length });
    return null;
  }
  dbgGroup(`computePath #${start} → #${end}`);
  // Audit the graph once; asymmetric edges are the usual culprit behind
  // "adjacent tiles give the wrong hop count".
  const audit = auditAdjacency(lattice);
  // Ground-truth adjacency check for the specific endpoints.
  const startHasEnd = isNeighbor(lattice, start, end);
  const endHasStart = isNeighbor(lattice, end, start);
  if (start !== end) {
    dbg('direct adjacency:', {
      'start→end': startHasEnd,
      'end→start': endHasStart,
    });
    if (startHasEnd !== endHasStart) {
      dbg(
        `⚠ ASYMMETRIC endpoint edge: #${start}` +
          `${startHasEnd ? '→' : '↛'}#${end} but #${end}` +
          `${endHasStart ? '→' : '↛'}#${start}. ` +
          `BFS from #${start} will only "see" the edge if start→end exists.`
      );
    }
  }

  const { dist, preds } = bfsAllShortest(lattice, start);
  if (dist[end] === -1) {
    dbg(`#${end} is UNREACHABLE from #${start}`);
    dbgGroupEnd();
    return {
      start,
      end,
      reachable: false,
      euclid: euclidean(lattice, start, end),
    };
  }
  const onPath = shortestPathTiles(preds, start, end);
  const samplePaths = reconstructAll(preds, start, end, 64);
  const numPaths = countShortest(preds, start, end);
  // Use a representative path for sheet-shift / length reporting. Only
  // accept a reconstructed path whose hop count actually matches the BFS
  // distance; otherwise fall back to the trivial single-node path.
  let rep = [start];
  for (const p of samplePaths) {
    if (p.length - 1 === dist[end] && p[0] === start && p[p.length - 1] === end) {
      rep = p;
      break;
    }
  }
  // Sanity check: if the endpoints are directly adjacent, the hop count
  // MUST be 1. Anything else means the graph the BFS walked disagrees
  // with the neighbor list the rest of the app uses.
  if (start !== end && (startHasEnd || endHasStart) && dist[end] !== 1) {
    dbg(
      `⚠ BUG: #${start} and #${end} are adjacent but BFS hop count is ` +
        `${dist[end]} (expected 1). This usually means a stale/asymmetric ` +
        `neighbor list. Audit ok=${audit.ok}.`
    );
  }
  // Sanity check: representative path must actually start/end correctly.
  if (rep.length > 1) {
    if (rep[0] !== start || rep[rep.length - 1] !== end) {
      dbg('⚠ representative path endpoints mismatch', {
        repFirst: rep[0],
        repLast: rep[rep.length - 1],
        start,
        end,
      });
    }
    if (rep.length - 1 !== dist[end]) {
      dbg('⚠ representative path length disagrees with BFS dist', {
        repHops: rep.length - 1,
        bfsHops: dist[end],
      });
    }
    // Verify every consecutive pair in rep is truly adjacent.
    for (let i = 0; i + 1 < rep.length; i++) {
      if (!isNeighbor(lattice, rep[i], rep[i + 1])) {
        dbg(
          `⚠ representative path contains a NON-EDGE: ` + `#${rep[i]} → #${rep[i + 1]} (step ${i})`
        );
      }
    }
  }

  const sheetShift = pathSheetShift(lattice, rep);
  const result = {
    start,
    end,
    reachable: true,
    hops: dist[end],
    pathLength: rep.length > 1 ? pathLength(lattice, rep) : 0,
    numPaths,
    onPath, // Set<index>
    samplePaths, // up to 64 explicit minimum-hop paths
    sheetShift,
    euclid: euclidean(lattice, start, end),
  };
  dbg('result:', {
    hops: result.hops,
    numPaths: result.numPaths,
    onPathSize: onPath.size,
    samplePaths: samplePaths.length,
    sheetShift: result.sheetShift,
    euclid: result.euclid,
    rep,
  });
  dbgGroupEnd();
  return result;
}
