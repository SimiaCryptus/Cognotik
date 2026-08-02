// ─────────────────────────────────────────────
//  Symmetry Diffusion App
// ─────────────────────────────────────────────

(function () {
  'use strict';
  // Available rotation angles (degrees). Each is an independent toggle.
  const ROT_ANGLES = [15, 30, 45, 60, 72, 90, 120, 135, 144, 180, 225, 270];

  // ── State ──────────────────────────────────
  let GRID_W = 64;
  let GRID_H = 64;
  let pixels = null; // Float32Array, length GRID_W*GRID_H*3  (r,g,b in [0,1])
  let displayScale = 1;

  const state = {
    tool: 'draw',
    color: [1, 0.27, 0.27],
    brushSize: 2,
    showGrid: false,
    diffRate: 0.3,
    diffRenorm: 1.0,
    neighborhood: 8,
    playing: false,
    fps: 15,
    gridSize: 64,
    aspectRatio: 1.0, // width / height  (e.g. 2.0 = landscape)
    // Diffusion mode: 'local' | 'spectral' | 'reaction'
    diffMode: 'local',
    // Spectral parameters
    spectralFilter: 'invsqrt', // 'heat' | 'tikhonov' | 'invsqrt' | 'wave'
    spectralT: 0.5, // time/strength parameter
    spectralK: 64, // number of eigenvectors
    // Reaction-diffusion parameters (Gray-Scott-ish, applied per channel pair)
    rdFeed: 0.055,
    rdKill: 0.062,
    // Lattice geometry: 'euclidean' | 'circular' | 'hyperbolic' | 'spherical'
    lattice: 'euclidean',
    // For non-euclidean modes, the grid is bounded by a disk; cells outside
    // the disk are inactive (masked). Radius is in cells, defaults to half
    // the smaller grid dimension.
    latticeRadius: 0, // 0 = auto (min(W,H)/2 - 1)
    latticeCurvature: 1, // scale factor on the metric (k); larger = more curved
    // Global signed color permutation applied to symmetry-induced linkages.
    // Each entry is { src: 0|1|2, sign: +1|-1 } meaning the output channel
    // takes ±(value of source channel). The default identity is r,g,b.
    // A negated channel means "flipped around 0.5": -c -> 1 - c.
    colorPerm: [
      { src: 0, sign: 1 },
      { src: 1, sign: 1 },
      { src: 2, sign: 1 },
    ],
    symmetry: {
      translationX: false,
      translationY: false,
      mirrorX: false,
      mirrorY: false,
      diagonal: false,
      // Per-angle rotation toggles, keyed by integer degree
      rotations: {},
      // Lattice translation symmetries: array of {x, y} in relative
      // coordinates where (1,0) = full grid width, (0,1) = full grid height.
      // Each vector adds peers at (px + tx*W, py + ty*H) and its negative.
      latticeTranslations: [],
    },
  };
  for (const a of ROT_ANGLES) state.symmetry.rotations[a] = false;
  // ── Color-permutation helpers ──────────────
  // The permutation P is a signed 3x3 monomial matrix acting on RGB values.
  // Negation is interpreted as flip-around-0.5, so the affine action is:
  //   out[i] = sign_i * (in[src_i] - 0.5) + 0.5
  // For composition we work in centered space (value - 0.5).
  const IDENTITY_PERM = [
    { src: 0, sign: 1 },
    { src: 1, sign: 1 },
    { src: 2, sign: 1 },
  ];
  function applyPerm(perm, rgb) {
    const r = rgb[0] - 0.5,
      g = rgb[1] - 0.5,
      b = rgb[2] - 0.5;
    const c = [r, g, b];
    return [
      perm[0].sign * c[perm[0].src] + 0.5,
      perm[1].sign * c[perm[1].src] + 0.5,
      perm[2].sign * c[perm[2].src] + 0.5,
    ];
  }
  // Compose perms: (a ∘ b)(x) = a(b(x))
  function composePerm(a, b) {
    const out = new Array(3);
    for (let i = 0; i < 3; i++) {
      const ai = a[i];
      const bi = b[ai.src];
      out[i] = { src: bi.src, sign: ai.sign * bi.sign };
    }
    return out;
  }
  function permPow(p, k) {
    let out = IDENTITY_PERM.map((e) => ({ ...e }));
    if (k === 0) return out;
    const sign = k > 0 ? 1 : -1;
    const n = Math.abs(k);
    let base = sign === 1 ? p : invertPerm(p);
    for (let i = 0; i < n; i++) out = composePerm(out, base);
    return out;
  }
  function invertPerm(p) {
    const out = new Array(3);
    for (let i = 0; i < 3; i++) {
      // p sends src -> i with sign s; inverse sends i -> src with sign s
      out[p[i].src] = { src: i, sign: p[i].sign };
    }
    return out;
  }
  function isIdentityPerm(p) {
    for (let i = 0; i < 3; i++) {
      if (p[i].src !== i || p[i].sign !== 1) return false;
    }
    return true;
  }
  // Channel-by-channel application: returns sign and source channel for output `c`.
  // Used inside hot diffusion loops to avoid array allocation.
  function permEntry(perm, c) {
    return perm[c];
  }
  function permSignature(p) {
    return p.map((e) => `${e.sign > 0 ? '+' : '-'}${e.src}`).join(',');
  }

  let animHandle = null;
  let lastFrameTime = 0;
  // ── Lattice mask ───────────────────────────
  // active[y*W + x] = 1 if the cell participates in the lattice. For
  // 'euclidean' all cells are active. For disk-based geometries, only
  // cells inside the disk are active.
  let activeMask = null;
  function isActive(x, y) {
    return activeMask[y * GRID_W + x] === 1;
  }
  function latticeRadius() {
    if (state.latticeRadius > 0) return state.latticeRadius;
    return Math.min(GRID_W, GRID_H) / 2 - 1;
  }
  function latticeCenter() {
    return [(GRID_W - 1) / 2, (GRID_H - 1) / 2];
  }
  // Disk-normalised coordinates: maps cell (x,y) into the open unit disk
  // (used by hyperbolic & spherical metrics). Returns [u, v, r] where r=√(u²+v²).
  function diskCoords(x, y) {
    const [cx, cy] = latticeCenter();
    const R = latticeRadius();
    const u = (x - cx) / R;
    const v = (y - cy) / R;
    return [u, v, Math.sqrt(u * u + v * v)];
  }
  // Distance between two cells under the current lattice geometry.
  // Returns Infinity if either endpoint is inactive.
  function latticeDist(x1, y1, x2, y2) {
    if (!isActive(x1, y1) || !isActive(x2, y2)) return Infinity;
    const k = state.latticeCurvature || 1;
    switch (state.lattice) {
      case 'circular': {
        // Plain Euclidean distance (the disk is just a clipped Cartesian grid).
        const dx = x2 - x1,
          dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
      }
      case 'hyperbolic': {
        // Poincaré disk distance: d = arccosh(1 + 2|a-b|² / ((1-|a|²)(1-|b|²)))
        const [u1, v1] = diskCoords(x1, y1);
        const [u2, v2] = diskCoords(x2, y2);
        const a2 = u1 * u1 + v1 * v1;
        const b2 = u2 * u2 + v2 * v2;
        const du = u2 - u1,
          dv = v2 - v1;
        const num = 2 * (du * du + dv * dv);
        const den = Math.max(1e-9, (1 - a2) * (1 - b2));
        const arg = 1 + num / den;
        return k * Math.acosh(Math.max(1, arg));
      }
      case 'spherical': {
        // Map disk → sphere via stereographic projection (north-pole based):
        //   (u,v) ∈ unit disk → 3D point on unit sphere.
        // Then return the great-circle (geodesic) distance.
        const [u1, v1] = diskCoords(x1, y1);
        const [u2, v2] = diskCoords(x2, y2);
        const s1 = 1 + u1 * u1 + v1 * v1;
        const s2 = 1 + u2 * u2 + v2 * v2;
        const p1 = [(2 * u1) / s1, (2 * v1) / s1, (s1 - 2) / s1];
        const p2 = [(2 * u2) / s2, (2 * v2) / s2, (s2 - 2) / s2];
        let dot = p1[0] * p2[0] + p1[1] * p2[1] + p1[2] * p2[2];
        if (dot > 1) dot = 1;
        if (dot < -1) dot = -1;
        return k * Math.acos(dot);
      }
      case 'euclidean':
      default: {
        const dx = x2 - x1,
          dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
      }
    }
  }
  function rebuildLatticeMask() {
    const N = GRID_W * GRID_H;
    activeMask = new Uint8Array(N);
    if (state.lattice === 'euclidean') {
      activeMask.fill(1);
      return;
    }
    const [cx, cy] = latticeCenter();
    const R = latticeRadius();
    for (let y = 0; y < GRID_H; y++) {
      for (let x = 0; x < GRID_W; x++) {
        const dx = x - cx,
          dy = y - cy;
        if (dx * dx + dy * dy <= R * R) activeMask[y * GRID_W + x] = 1;
      }
    }
    // Zero out inactive pixels so they don't contribute stale colour
    if (pixels) {
      for (let i = 0; i < N; i++) {
        if (!activeMask[i]) {
          pixels[i * 3] = 0;
          pixels[i * 3 + 1] = 0;
          pixels[i * 3 + 2] = 0;
        }
      }
    }
  }
  // ── Spectral cache ─────────────────────────
  // Eigendecomposition of the (negative) graph Laplacian for the current
  // grid + symmetry settings. Recomputed lazily when invalidated.
  const spectralCache = {
    valid: false,
    N: 0,
    K: 0,
    eigvals: null, // Float32Array length K
    eigvecs: null, // Float32Array length N*K  (column-major: vec k at offset k*N)
    signature: '',
  };
  function invalidateSpectral() {
    spectralCache.valid = false;
  }

  // ── Canvas setup ───────────────────────────
  const canvas = document.getElementById('main-canvas');
  const ctx = canvas.getContext('2d');
  let imageData = null;

  function recomputeGridDims() {
    // GRID_W is the "base" size; GRID_H is derived from aspect ratio
    // We keep GRID_W = gridSize and GRID_H = round(gridSize / aspectRatio)
    GRID_W = state.gridSize;
    GRID_H = Math.max(1, Math.round(state.gridSize / state.aspectRatio));
  }

  function initGrid() {
    recomputeGridDims();
    pixels = new Float32Array(GRID_W * GRID_H * 3);
    imageData = ctx.createImageData(GRID_W, GRID_H);
    resizeCanvas();
    rebuildLatticeMask();
    clearPixels();
    invalidateSpectral();
  }

  function resizeCanvas() {
    const area = document.getElementById('canvas-area');
    const maxW = area.clientWidth - 20;
    const maxH = area.clientHeight - 20;
    // Fit the grid (GRID_W x GRID_H) into the available area
    const scaleX = Math.floor(maxW / GRID_W);
    const scaleY = Math.floor(maxH / GRID_H);
    displayScale = Math.max(1, Math.min(scaleX, scaleY));
    canvas.width = GRID_W;
    canvas.height = GRID_H;
    canvas.style.width = GRID_W * displayScale + 'px';
    canvas.style.height = GRID_H * displayScale + 'px';
  }

  // ── Pixel helpers ──────────────────────────
  function idx(x, y) {
    return (y * GRID_W + x) * 3;
  }

  function getPixel(x, y) {
    const i = idx(x, y);
    return [pixels[i], pixels[i + 1], pixels[i + 2]];
  }

  function setPixel(x, y, r, g, b) {
    if (x < 0 || x >= GRID_W || y < 0 || y >= GRID_H) return;
    if (activeMask && !activeMask[y * GRID_W + x]) return;
    const i = idx(x, y);
    pixels[i] = Math.max(0, Math.min(1, r));
    pixels[i + 1] = Math.max(0, Math.min(1, g));
    pixels[i + 2] = Math.max(0, Math.min(1, b));
  }

  function clearPixels() {
    pixels.fill(0);
    render();
  }

  function randomPixels() {
    const N = GRID_W * GRID_H;
    for (let i = 0; i < N; i++) {
      if (activeMask && !activeMask[i]) {
        pixels[i * 3] = 0;
        pixels[i * 3 + 1] = 0;
        pixels[i * 3 + 2] = 0;
      } else {
        pixels[i * 3] = Math.random();
        pixels[i * 3 + 1] = Math.random();
        pixels[i * 3 + 2] = Math.random();
      }
    }
    render();
  }

  // ── Symmetry helpers ───────────────────────
  function wrapX(x) {
    return ((x % GRID_W) + GRID_W) % GRID_W;
  }
  function wrapY(y) {
    return ((y % GRID_H) + GRID_H) % GRID_H;
  }

  // Clamp-or-wrap a position depending on translation symmetry flags
  function addPos(positions, px, py) {
    // Round fractional positions (lattice translations may produce non-integers)
    const ix = Math.round(px),
      iy = Math.round(py);
    const cx = state.symmetry.translationX ? wrapX(ix) : ix;
    const cy = state.symmetry.translationY ? wrapY(iy) : iy;
    if (cx < 0 || cx >= GRID_W || cy < 0 || cy >= GRID_H) return;
    if (activeMask && !activeMask[cy * GRID_W + cx]) return;
    positions.add(cx + ',' + cy);
  }

  // Returns list of [x,y] mirror positions for a given (x,y)
  function symmetryPositions(x, y) {
    // Returns array of { x, y, perm } where perm is the color permutation to
    // apply to the source colour before depositing at (x,y).
    const seen = new Map(); // key "x,y" -> perm (first one wins)

    const sym = state.symmetry;
    const W = GRID_W,
      H = GRID_H;
    const mx = W - 1 - x;
    const my = H - 1 - y;

    // Centre of grid (for rotational symmetry)
    const cx = (W - 1) / 2;
    const cy = (H - 1) / 2;

    // seeds: [px, py, perm] where perm is the permutation applied to the
    // *source* colour to obtain the colour deposited at (px, py). The
    // identity seed is (x, y, identity).
    const P = state.colorPerm;
    const ID = IDENTITY_PERM;
    const seeds = [[x, y, ID]];

    if (sym.mirrorX) seeds.push([mx, y, P]);
    if (sym.mirrorY) seeds.push([x, my, P]);
    if (sym.mirrorX && sym.mirrorY) seeds.push([mx, my, composePerm(P, P)]);

    // Per-angle rotations: each enabled angle adds a single rotated seed.
    {
      const dx = x - cx,
        dy = y - cy;
      for (const angDeg of ROT_ANGLES) {
        if (!sym.rotations[angDeg]) continue;
        const ang = (angDeg * Math.PI) / 180;
        const cos = Math.cos(ang),
          sin = Math.sin(ang);
        seeds.push([Math.round(cx + dx * cos - dy * sin), Math.round(cy + dx * sin + dy * cos), P]);
      }
    }

    if (sym.diagonal) {
      seeds.push([y, x, P]);
      // anti-diagonal reflection: (x,y) -> (H-1-y, W-1-x) — only add when both mirrors active
      if (sym.mirrorX && sym.mirrorY) seeds.push([my, mx, composePerm(P, P)]);
    }
    // Lattice translation symmetries (fractional vectors in [0,1]).
    // For each (tx,ty) we add seeds at integer multiples ±k of the vector,
    // for k=1..maxK, as long as they remain potentially in-grid.
    const latTrans = sym.latticeTranslations || [];
    if (latTrans.length) {
      const baseSeeds = seeds.slice(); // snapshot before extending
      for (const t of latTrans) {
        const dxCells = t.x * W;
        const dyCells = t.y * H;
        if (Math.abs(dxCells) < 1e-6 && Math.abs(dyCells) < 1e-6) continue;
        // Replicate up to a reasonable number of multiples
        const maxK = 8;
        for (const [bx, by, bp] of baseSeeds) {
          for (let k = 1; k <= maxK; k++) {
            seeds.push([bx + dxCells * k, by + dyCells * k, composePerm(bp, permPow(P, k))]);
            seeds.push([bx - dxCells * k, by - dyCells * k, composePerm(bp, permPow(P, -k))]);
          }
        }
      }
    }

    const tryAdd = (sx, sy, perm) => {
      const ix = Math.round(sx),
        iy = Math.round(sy);
      const cxw = state.symmetry.translationX ? wrapX(ix) : ix;
      const cyw = state.symmetry.translationY ? wrapY(iy) : iy;
      if (cxw < 0 || cxw >= GRID_W || cyw < 0 || cyw >= GRID_H) return;
      if (activeMask && !activeMask[cyw * GRID_W + cxw]) return;
      const key = cxw + ',' + cyw;
      if (!seen.has(key)) seen.set(key, perm);
    };
    for (const [sx, sy, perm] of seeds) {
      tryAdd(sx, sy, perm);
      if (sym.translationX) {
        tryAdd(sx + Math.floor(W / 2), sy, perm);
        tryAdd(sx - Math.floor(W / 2), sy, perm);
      }
      if (sym.translationY) {
        tryAdd(sx, sy + Math.floor(H / 2), perm);
        tryAdd(sx, sy - Math.floor(H / 2), perm);
      }
    }

    const result = [];
    for (const [key, perm] of seen) {
      const [xs, ys] = key.split(',').map(Number);
      result.push({ x: xs, y: ys, perm });
    }
    return result;
  }

  // ── Drawing ────────────────────────────────
  function paintAt(x, y) {
    const r = state.color[0],
      g = state.color[1],
      b = state.color[2];
    const bs = state.brushSize;

    for (let dy = -bs + 1; dy < bs; dy++) {
      for (let dx = -bs + 1; dx < bs; dx++) {
        if (dx * dx + dy * dy < bs * bs) {
          const tx = (((x + dx) % GRID_W) + GRID_W) % GRID_W;
          const ty = (((y + dy) % GRID_H) + GRID_H) % GRID_H;
          if (state.tool === 'erase') {
            setPixel(tx, ty, 0, 0, 0);
          } else {
            setPixel(tx, ty, r, g, b);
          }
        }
      }
    }
  }

  function floodFill(x, y) {
    const [tr, tg, tb] = getPixel(x, y);
    const [fr, fg, fb] = state.color;
    const eps = 0.01;
    if (Math.abs(tr - fr) < eps && Math.abs(tg - fg) < eps && Math.abs(tb - fb) < eps) return;

    const stack = [[x, y]];
    const visited = new Uint8Array(GRID_W * GRID_H);

    while (stack.length) {
      const [cx, cy] = stack.pop();
      if (cx < 0 || cx >= GRID_W || cy < 0 || cy >= GRID_H) continue;
      if (visited[cy * GRID_W + cx]) continue;
      const [pr, pg, pb] = getPixel(cx, cy);
      if (Math.abs(pr - tr) > 0.05 || Math.abs(pg - tg) > 0.05 || Math.abs(pb - tb) > 0.05)
        continue;
      visited[cy * GRID_W + cx] = 1;
      setPixel(cx, cy, fr, fg, fb);
      stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
    }
  }

  // ── Neighborhood / Diffusion ───────────────
  function getNeighbors(x, y) {
    const neighbors = [];
    if (activeMask && !activeMask[y * GRID_W + x]) return neighbors;
    const n = state.neighborhood;

    const offsets4 = [
      [1, 0],
      [-1, 0],
      [0, 1],
      [0, -1],
    ];
    const offsets8 = [...offsets4, [1, 1], [1, -1], [-1, 1], [-1, -1]];
    const offsets12 = [...offsets8, [2, 0], [-2, 0], [0, 2], [0, -2]];

    const offsets = n === 4 ? offsets4 : n === 8 ? offsets8 : offsets12;

    for (const [dx, dy] of offsets) {
      const nx = x + dx,
        ny = y + dy;

      const wx = state.symmetry.translationX ? wrapX(nx) : nx;
      const wy = state.symmetry.translationY ? wrapY(ny) : ny;

      if (wx < 0 || wx >= GRID_W || wy < 0 || wy >= GRID_H) continue;
      if (activeMask && !activeMask[wy * GRID_W + wx]) continue;

      // Use the lattice metric to compute a geometric distance, then
      // weight inversely. For non-Euclidean lattices this means cells
      // near the disk boundary effectively become much further apart
      // (hyperbolic) or closer (spherical), shaping diffusion accordingly.
      const dist = latticeDist(x, y, wx, wy);
      if (!isFinite(dist) || dist <= 0) continue;
      const weight = 1 / dist;
      neighbors.push({ x: wx, y: wy, w: weight, perm: IDENTITY_PERM });
    }

    // Symmetry-induced extra connections
    const sym = state.symmetry;
    const W = GRID_W,
      H = GRID_H;
    const mx = W - 1 - x,
      my = H - 1 - y;
    const cx = (W - 1) / 2,
      cy = (H - 1) / 2;
    const symPeers = [];
    // Each symPeer is [px, py, perm] — perm is the colour permutation to
    // apply to the peer's value before it acts on this cell.
    const P = state.colorPerm;
    const ID = IDENTITY_PERM;

    if (sym.mirrorX) symPeers.push([mx, y, P]);
    if (sym.mirrorY) symPeers.push([x, my, P]);
    // Per-angle rotational symmetry peers
    {
      const dx = x - cx,
        dy = y - cy;
      for (const angDeg of ROT_ANGLES) {
        if (!sym.rotations[angDeg]) continue;
        const ang = (angDeg * Math.PI) / 180;
        const cos = Math.cos(ang),
          sin = Math.sin(ang);
        symPeers.push([cx + dx * cos - dy * sin, cy + dx * sin + dy * cos, P]);
      }
    }
    if (sym.diagonal) symPeers.push([y, x, P]); // already integer
    if (sym.diagonal && sym.mirrorX && sym.mirrorY) symPeers.push([my, mx, composePerm(P, P)]);
    // Lattice translation symmetry peers: ±k * (tx*W, ty*H)
    {
      const latTrans = sym.latticeTranslations || [];
      for (const t of latTrans) {
        const dxCells = t.x * W;
        const dyCells = t.y * H;
        if (Math.abs(dxCells) < 1e-6 && Math.abs(dyCells) < 1e-6) continue;
        const maxK = 4;
        for (let k = 1; k <= maxK; k++) {
          symPeers.push([x + dxCells * k, y + dyCells * k, permPow(P, k)]);
          symPeers.push([x - dxCells * k, y - dyCells * k, permPow(P, -k)]);
        }
      }
    }
    // Helper: add bilinear (nearest-4) weighted connections for a fractional peer position
    function addBilinearNeighbors(fx, fy, baseWeight, perm) {
      const x0 = Math.floor(fx),
        y0 = Math.floor(fy);
      const x1 = x0 + 1,
        y1 = y0 + 1;
      const tx = fx - x0,
        ty = fy - y0;
      const corners = [
        { cx: x0, cy: y0, w: (1 - tx) * (1 - ty) },
        { cx: x1, cy: y0, w: tx * (1 - ty) },
        { cx: x0, cy: y1, w: (1 - tx) * ty },
        { cx: x1, cy: y1, w: tx * ty },
      ];
      for (const { cx, cy, w } of corners) {
        if (w < 1e-6) continue;
        if (cx < 0 || cx >= GRID_W || cy < 0 || cy >= GRID_H) continue;
        if (cx === x && cy === y) continue;
        neighbors.push({ x: cx, y: cy, w: baseWeight * w, perm });
      }
    }

    for (const [sx, sy, perm] of symPeers) {
      addBilinearNeighbors(sx, sy, 1.0, perm);
    }

    return neighbors;
  }

  function diffuseStep() {
    const rate = state.diffRate;
    const renorm = state.diffRenorm;
    const N = GRID_W * GRID_H;
    const next = new Float32Array(pixels.length);

    // ── Compute per-channel mean and variance of current frame ──
    // Used for moment-preserving renormalisation.
    const mean = [0, 0, 0];
    const variance = [0, 0, 0];
    for (let i = 0; i < N; i++) {
      mean[0] += pixels[i * 3];
      mean[1] += pixels[i * 3 + 1];
      mean[2] += pixels[i * 3 + 2];
    }
    mean[0] /= N;
    mean[1] /= N;
    mean[2] /= N;
    for (let i = 0; i < N; i++) {
      variance[0] += (pixels[i * 3] - mean[0]) ** 2;
      variance[1] += (pixels[i * 3 + 1] - mean[1]) ** 2;
      variance[2] += (pixels[i * 3 + 2] - mean[2]) ** 2;
    }
    variance[0] /= N;
    variance[1] /= N;
    variance[2] /= N;
    const std = [
      Math.sqrt(variance[0]) || 0,
      Math.sqrt(variance[1]) || 0,
      Math.sqrt(variance[2]) || 0,
    ];

    // ── Diffuse ──
    for (let y = 0; y < GRID_H; y++) {
      for (let x = 0; x < GRID_W; x++) {
        const neighbors = getNeighbors(x, y);
        let totalW = 0;
        for (const nb of neighbors) totalW += nb.w;

        const i = idx(x, y);
        let nr = pixels[i],
          ng = pixels[i + 1],
          nb2 = pixels[i + 2];

        for (const nb of neighbors) {
          const j = idx(nb.x, nb.y);
          const wn = nb.w / (totalW || 1);
          const perm = nb.perm || IDENTITY_PERM;
          // Permuted peer value (in centered space, then re-shifted):
          //   pv[c] = sign_c * (pixels[j + src_c] - 0.5) + 0.5
          const pe0 = perm[0],
            pe1 = perm[1],
            pe2 = perm[2];
          const pv0 = pe0.sign * (pixels[j + pe0.src] - 0.5) + 0.5;
          const pv1 = pe1.sign * (pixels[j + pe1.src] - 0.5) + 0.5;
          const pv2 = pe2.sign * (pixels[j + pe2.src] - 0.5) + 0.5;
          nr += rate * wn * (pv0 - pixels[i]);
          ng += rate * wn * (pv1 - pixels[i + 1]);
          nb2 += rate * wn * (pv2 - pixels[i + 2]);
        }

        next[i] = nr;
        next[i + 1] = ng;
        next[i + 2] = nb2;
      }
    }

    // ── Moment-preserving renormalisation ──
    // After diffusion the distribution has shifted mean and std.
    // We compute the new moments and blend back toward the original ones.
    if (renorm > 0) {
      const newMean = [0, 0, 0];
      for (let i = 0; i < N; i++) {
        newMean[0] += next[i * 3];
        newMean[1] += next[i * 3 + 1];
        newMean[2] += next[i * 3 + 2];
      }
      newMean[0] /= N;
      newMean[1] /= N;
      newMean[2] /= N;

      const newVariance = [0, 0, 0];
      for (let i = 0; i < N; i++) {
        newVariance[0] += (next[i * 3] - newMean[0]) ** 2;
        newVariance[1] += (next[i * 3 + 1] - newMean[1]) ** 2;
        newVariance[2] += (next[i * 3 + 2] - newMean[2]) ** 2;
      }
      newVariance[0] /= N;
      newVariance[1] /= N;
      newVariance[2] /= N;
      const newStd = [
        Math.sqrt(newVariance[0]) || 1e-9,
        Math.sqrt(newVariance[1]) || 1e-9,
        Math.sqrt(newVariance[2]) || 1e-9,
      ];

      for (let i = 0; i < N; i++) {
        for (let c = 0; c < 3; c++) {
          const raw = next[i * 3 + c];
          // Standardise with new moments, then rescale to original moments
          const rescaled =
            std[c] > 1e-9 ? mean[c] + (raw - newMean[c]) * (std[c] / newStd[c]) : mean[c];
          // Blend: renorm=0 → keep raw diffused; renorm=1 → fully moment-matched
          next[i * 3 + c] = Math.max(0, Math.min(1, raw * (1 - renorm) + rescaled * renorm));
        }
      }
    } else {
      // Just clamp
      for (let i = 0; i < next.length; i++) {
        next[i] = Math.max(0, Math.min(1, next[i]));
      }
    }

    pixels = next;
  }
  // ───────────────────────────────────────────
  //  Spectral diffusion
  // ───────────────────────────────────────────
  // Build a sparse representation of the symmetric normalised Laplacian L
  // implicitly defined by getNeighbors(). We symmetrise by averaging weights:
  //   W_sym(i,j) = (w(i,j) + w(j,i)) / 2
  // L = D - W_sym  (with row sums of W_sym as D)
  //
  // Returns { rowPtr, colIdx, vals, deg } in CSR form for a symmetric matrix.
  function buildLaplacian() {
    const N = GRID_W * GRID_H;
    // Map (i -> list of {j, w})
    const adj = new Array(N);
    for (let i = 0; i < N; i++) adj[i] = new Map();
    for (let y = 0; y < GRID_H; y++) {
      for (let x = 0; x < GRID_W; x++) {
        const i = y * GRID_W + x;
        const nbs = getNeighbors(x, y);
        // Normalise neighbor weights so each row sums to 1
        let totalW = 0;
        for (const nb of nbs) totalW += nb.w;
        if (totalW <= 0) continue;
        for (const nb of nbs) {
          const j = nb.y * GRID_W + nb.x;
          if (j === i) continue;
          const w = nb.w / totalW;
          // accumulate
          adj[i].set(j, (adj[i].get(j) || 0) + w);
        }
      }
    }
    // Symmetrise: average forward/backward weights
    for (let i = 0; i < N; i++) {
      for (const [j, w] of adj[i]) {
        if (j > i) {
          const wji = adj[j].get(i) || 0;
          const wsym = 0.5 * (w + wji);
          adj[i].set(j, wsym);
          adj[j].set(i, wsym);
        }
      }
    }
    // Build CSR for L = D - W
    const rowPtr = new Int32Array(N + 1);
    let nnz = 0;
    for (let i = 0; i < N; i++) {
      rowPtr[i] = nnz;
      nnz += adj[i].size + 1; // +1 for diagonal
    }
    rowPtr[N] = nnz;
    const colIdx = new Int32Array(nnz);
    const vals = new Float32Array(nnz);
    const deg = new Float32Array(N);
    for (let i = 0; i < N; i++) {
      let p = rowPtr[i];
      let dsum = 0;
      // off-diagonal entries
      const entries = [...adj[i].entries()].sort((a, b) => a[0] - b[0]);
      // we'll insert diagonal at the right sorted position
      let inserted = false;
      for (const [j, w] of entries) {
        if (!inserted && j > i) {
          colIdx[p] = i;
          vals[p] = 0; // placeholder, fill after
          p++;
          inserted = true;
        }
        colIdx[p] = j;
        vals[p] = -w;
        dsum += w;
        p++;
      }
      if (!inserted) {
        colIdx[p] = i;
        vals[p] = 0;
        p++;
      }
      deg[i] = dsum;
      // fill diagonal value
      for (let q = rowPtr[i]; q < rowPtr[i + 1]; q++) {
        if (colIdx[q] === i) {
          vals[q] = dsum;
          break;
        }
      }
    }
    return { N, rowPtr, colIdx, vals, deg };
  }
  // y = L * x  for CSR matrix L
  function spmv(L, x, y) {
    const { N, rowPtr, colIdx, vals } = L;
    for (let i = 0; i < N; i++) {
      let s = 0;
      const end = rowPtr[i + 1];
      for (let p = rowPtr[i]; p < end; p++) {
        s += vals[p] * x[colIdx[p]];
      }
      y[i] = s;
    }
  }
  // Lanczos iteration to find K smallest eigenvalues/vectors of L (symmetric PSD).
  // L is the CSR Laplacian. Returns { eigvals: Float32Array(K), eigvecs: Float32Array(N*K) }
  //
  // We run M >= K Lanczos steps with full reorthogonalisation, build the M x M
  // tridiagonal T, diagonalise it (Jacobi), pick the K smallest Ritz values, and
  // form the corresponding Ritz vectors.
  function lanczosSmallest(L, K, maxIters) {
    const N = L.N;
    const M = Math.min(maxIters, N);
    // Storage for Lanczos vectors V (N x M), column-major
    const V = new Float32Array(N * M);
    const alpha = new Float64Array(M);
    const beta = new Float64Array(M + 1);
    // Initial random vector
    const v = new Float32Array(N);
    for (let i = 0; i < N; i++) v[i] = Math.random() - 0.5;
    // normalize
    let nrm = 0;
    for (let i = 0; i < N; i++) nrm += v[i] * v[i];
    nrm = Math.sqrt(nrm);
    for (let i = 0; i < N; i++) v[i] /= nrm;
    // place into V[:, 0]
    for (let i = 0; i < N; i++) V[0 * N + i] = v[i];
    const w = new Float32Array(N);
    const vPrev = new Float32Array(N);
    let mActual = 0;
    for (let j = 0; j < M; j++) {
      // w = L * v
      spmv(L, v, w);
      // w -= alpha_j * v
      let a = 0;
      for (let i = 0; i < N; i++) a += w[i] * v[i];
      alpha[j] = a;
      for (let i = 0; i < N; i++) w[i] -= a * v[i] + (j > 0 ? beta[j] * vPrev[i] : 0);
      // Full reorthogonalisation against all previous V columns
      for (let r = 0; r <= j; r++) {
        let dot = 0;
        for (let i = 0; i < N; i++) dot += w[i] * V[r * N + i];
        for (let i = 0; i < N; i++) w[i] -= dot * V[r * N + i];
      }
      // beta_{j+1}
      let bnorm = 0;
      for (let i = 0; i < N; i++) bnorm += w[i] * w[i];
      bnorm = Math.sqrt(bnorm);
      beta[j + 1] = bnorm;
      mActual = j + 1;
      if (bnorm < 1e-10) break;
      // shift
      for (let i = 0; i < N; i++) {
        vPrev[i] = v[i];
        v[i] = w[i] / bnorm;
      }
      if (j + 1 < M) {
        for (let i = 0; i < N; i++) V[(j + 1) * N + i] = v[i];
      }
    }
    // Diagonalise the m x m tridiagonal matrix T = tridiag(beta, alpha, beta)
    const m = mActual;
    // T as dense (small) matrix
    const T = new Float64Array(m * m);
    for (let i = 0; i < m; i++) {
      T[i * m + i] = alpha[i];
      if (i + 1 < m) {
        T[i * m + (i + 1)] = beta[i + 1];
        T[(i + 1) * m + i] = beta[i + 1];
      }
    }
    const Q = new Float64Array(m * m);
    for (let i = 0; i < m; i++) Q[i * m + i] = 1;
    jacobiEig(T, Q, m);
    // eigenvalues on diag(T); eigenvectors are columns of Q
    // Sort by eigenvalue ascending
    const order = new Array(m);
    for (let i = 0; i < m; i++) order[i] = i;
    order.sort((a, b) => T[a * m + a] - T[b * m + b]);
    const Keff = Math.min(K, m);
    const eigvals = new Float32Array(Keff);
    const eigvecs = new Float32Array(N * Keff);
    for (let k = 0; k < Keff; k++) {
      const idxK = order[k];
      eigvals[k] = T[idxK * m + idxK];
      // Ritz vector = V * Q[:, idxK]
      for (let i = 0; i < N; i++) {
        let s = 0;
        for (let r = 0; r < m; r++) {
          s += V[r * N + i] * Q[r * m + idxK];
        }
        eigvecs[k * N + i] = s;
      }
      // re-normalise just in case
      let nrm2 = 0;
      for (let i = 0; i < N; i++) nrm2 += eigvecs[k * N + i] ** 2;
      nrm2 = Math.sqrt(nrm2) || 1;
      for (let i = 0; i < N; i++) eigvecs[k * N + i] /= nrm2;
    }
    return { eigvals, eigvecs, K: Keff };
  }
  // Cyclic Jacobi eigendecomposition for a small symmetric m x m matrix.
  // T is overwritten so that diag(T) holds eigenvalues, off-diagonals -> 0.
  // Q starts as identity and ends as the orthogonal matrix of eigenvectors (columns).
  function jacobiEig(T, Q, m) {
    const maxSweeps = 50;
    for (let sweep = 0; sweep < maxSweeps; sweep++) {
      let off = 0;
      for (let p = 0; p < m - 1; p++) {
        for (let q = p + 1; q < m; q++) off += T[p * m + q] ** 2;
      }
      if (off < 1e-20) break;
      for (let p = 0; p < m - 1; p++) {
        for (let q = p + 1; q < m; q++) {
          const apq = T[p * m + q];
          if (Math.abs(apq) < 1e-14) continue;
          const app = T[p * m + p];
          const aqq = T[q * m + q];
          const theta = (aqq - app) / (2 * apq);
          let t;
          if (Math.abs(theta) > 1e15) t = 1 / (2 * theta);
          else t = Math.sign(theta) / (Math.abs(theta) + Math.sqrt(1 + theta * theta));
          if (theta === 0) t = 1;
          const c = 1 / Math.sqrt(1 + t * t);
          const s = t * c;
          // update T
          T[p * m + p] = app - t * apq;
          T[q * m + q] = aqq + t * apq;
          T[p * m + q] = 0;
          T[q * m + p] = 0;
          for (let i = 0; i < m; i++) {
            if (i !== p && i !== q) {
              const aip = T[i * m + p];
              const aiq = T[i * m + q];
              T[i * m + p] = c * aip - s * aiq;
              T[p * m + i] = T[i * m + p];
              T[i * m + q] = s * aip + c * aiq;
              T[q * m + i] = T[i * m + q];
            }
          }
          // update Q
          for (let i = 0; i < m; i++) {
            const qip = Q[i * m + p];
            const qiq = Q[i * m + q];
            Q[i * m + p] = c * qip - s * qiq;
            Q[i * m + q] = s * qip + c * qiq;
          }
        }
      }
    }
  }
  function symmetrySignature() {
    const s = state.symmetry;
    const rotKey = ROT_ANGLES.map((a) => (s.rotations[a] ? a : '')).join(',');
    const latKey = (s.latticeTranslations || [])
      .map((t) => `${t.x.toFixed(4)},${t.y.toFixed(4)}`)
      .join('|');
    return [
      GRID_W,
      GRID_H,
      state.neighborhood,
      s.translationX | 0,
      s.translationY | 0,
      s.mirrorX | 0,
      s.mirrorY | 0,
      s.diagonal | 0,
      rotKey,
      latKey,
      permSignature(state.colorPerm),
      state.spectralK,
    ].join(':');
  }
  function ensureSpectral() {
    const sig = symmetrySignature();
    if (spectralCache.valid && spectralCache.signature === sig) return;
    const N = GRID_W * GRID_H;
    const K = Math.min(state.spectralK, N - 1);
    const M = Math.min(N, Math.max(K * 2 + 10, 32));
    const t0 = performance.now();
    const L = buildLaplacian();
    const { eigvals, eigvecs, K: Keff } = lanczosSmallest(L, K, M);
    const t1 = performance.now();
    spectralCache.valid = true;
    spectralCache.signature = sig;
    spectralCache.N = N;
    spectralCache.K = Keff;
    spectralCache.eigvals = eigvals;
    spectralCache.eigvecs = eigvecs;
    const info = document.getElementById('info-text');
    if (info)
      info.textContent =
        `Spectral: ${Keff} eigvecs of ${N}-node graph in ${(t1 - t0).toFixed(0)} ms\n` +
        `λ range: [${eigvals[0].toFixed(4)}, ${eigvals[Keff - 1].toFixed(4)}]`;
  }
  function applySpectralFilter(filterFn) {
    ensureSpectral();
    const { N, K, eigvals, eigvecs } = spectralCache;
    const next = new Float32Array(pixels.length);
    // Pre-compute filter values
    const fvals = new Float32Array(K);
    for (let k = 0; k < K; k++) fvals[k] = filterFn(eigvals[k]);
    // For each colour channel: project onto eigenbasis, scale, project back.
    //   u' = sum_k f(λ_k) * <u, v_k> * v_k
    // Components outside the computed K-dimensional subspace are dropped
    // (low-rank approximation).  Coefficients are computed once per channel
    // and accumulated into `next`.
    for (let c = 0; c < 3; c++) {
      // Compute all K coefficients first
      const coefs = new Float32Array(K);
      for (let k = 0; k < K; k++) {
        const vk = eigvecs.subarray(k * N, k * N + N);
        let coef = 0;
        for (let i = 0; i < N; i++) coef += pixels[i * 3 + c] * vk[i];
        coefs[k] = coef * fvals[k];
      }
      // Reconstruct: next[i,c] = Σ_k coefs[k] * v_k[i]
      for (let i = 0; i < N; i++) next[i * 3 + c] = 0;
      for (let k = 0; k < K; k++) {
        const vk = eigvecs.subarray(k * N, k * N + N);
        const a = coefs[k];
        if (Math.abs(a) < 1e-9) continue;
        for (let i = 0; i < N; i++) next[i * 3 + c] += a * vk[i];
      }
      // Clamp
      for (let i = 0; i < N; i++) {
        const j = i * 3 + c;
        if (next[j] < 0) next[j] = 0;
        else if (next[j] > 1) next[j] = 1;
      }
    }
    pixels = next;
  }
  function spectralFilterFn() {
    const t = state.spectralT;
    switch (state.spectralFilter) {
      // Multiply t by a constant so the slider's 0.01-5 range covers a useful
      // span for each filter shape.
      case 'heat':
        return (lam) => Math.exp(-10 * t * lam);
      case 'tikhonov':
        return (lam) => 1 / (1 + 10 * t * lam);
      case 'invsqrt':
        return (lam) => (lam < 1e-6 ? 1 : Math.pow(lam, -0.5 * t));
      case 'wave': {
        return (lam) => {
          if (lam < 1e-6) return 1;
          const s = t * Math.sqrt(lam);
          return Math.sin(s) / s;
        };
      }
      default:
        return (lam) => Math.exp(-10 * t * lam);
    }
  }
  function spectralStep() {
    applySpectralFilter(spectralFilterFn());
  }
  // ── Reaction-diffusion via matrix exponential ──
  // We use the heat-kernel filter exp(-δL) for the diffusion half-step,
  // then add a Gray-Scott style nonlinearity coupling channels R (=u) and G (=v).
  function reactionDiffusionStep() {
    // 1) diffuse with heat kernel exp(-δL)
    const t = state.spectralT;
    applySpectralFilter((lam) => Math.exp(-10 * t * lam));
    // 2) Gray-Scott reaction on R (u) and G (v) channels
    //    du/dt = -uv^2 + F(1-u)
    //    dv/dt =  uv^2 - (F+k)v
    const F = state.rdFeed,
      k = state.rdKill;
    const N = GRID_W * GRID_H;
    const dt = 1.0;
    for (let i = 0; i < N; i++) {
      const j = i * 3;
      const u = pixels[j];
      const v = pixels[j + 1];
      const uv2 = u * v * v;
      let nu = u + dt * (-uv2 + F * (1 - u));
      let nv = v + dt * (uv2 - (F + k) * v);
      if (nu < 0) nu = 0;
      else if (nu > 1) nu = 1;
      if (nv < 0) nv = 0;
      else if (nv > 1) nv = 1;
      pixels[j] = nu;
      pixels[j + 1] = nv;
      // B channel: visualise |u-v|
      pixels[j + 2] = Math.abs(nu - nv);
    }
  }
  function step() {
    switch (state.diffMode) {
      case 'spectral':
        spectralStep();
        break;
      case 'reaction':
        reactionDiffusionStep();
        break;
      default:
        diffuseStep();
    }
  }

  // ── Render ─────────────────────────────────
  function render() {
    const data = imageData.data;
    for (let i = 0; i < GRID_W * GRID_H; i++) {
      const pi = i * 3;
      const di = i * 4;
      if (activeMask && !activeMask[i]) {
        // Inactive cells: dim background to indicate the lattice boundary
        data[di] = 18;
        data[di + 1] = 18;
        data[di + 2] = 36;
        data[di + 3] = 255;
      } else {
        data[di] = Math.round(pixels[pi] * 255);
        data[di + 1] = Math.round(pixels[pi + 1] * 255);
        data[di + 2] = Math.round(pixels[pi + 2] * 255);
        data[di + 3] = 255;
      }
    }
    ctx.putImageData(imageData, 0, 0);

    if (state.showGrid && displayScale >= 4) {
      ctx.save();
      ctx.strokeStyle = 'rgba(255,255,255,0.08)';
      ctx.lineWidth = 1 / displayScale;
      for (let x = 0; x <= GRID_W; x++) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, GRID_H);
        ctx.stroke();
      }
      for (let y = 0; y <= GRID_H; y++) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(GRID_W, y);
        ctx.stroke();
      }
      ctx.restore();
    }
  }

  // ── Animation loop ─────────────────────────
  function animLoop(ts) {
    if (!state.playing) return;
    const interval = 1000 / state.fps;
    if (ts - lastFrameTime >= interval) {
      lastFrameTime = ts;
      step();
      render();
    }
    animHandle = requestAnimationFrame(animLoop);
  }

  function startPlay() {
    if (state.playing) return;
    state.playing = true;
    lastFrameTime = 0;
    animHandle = requestAnimationFrame(animLoop);
  }

  function stopPlay() {
    state.playing = false;
    if (animHandle) cancelAnimationFrame(animHandle);
  }

  // ── Mouse interaction ──────────────────────
  let isDrawing = false;
  let lastDrawPos = null;

  function canvasPos(e) {
    const rect = canvas.getBoundingClientRect();
    const scaleX = GRID_W / rect.width;
    const scaleY = GRID_H / rect.height;
    const x = Math.floor((e.clientX - rect.left) * scaleX);
    const y = Math.floor((e.clientY - rect.top) * scaleY);
    return [Math.max(0, Math.min(GRID_W - 1, x)), Math.max(0, Math.min(GRID_H - 1, y))];
  }

  function lerp2D(x0, y0, x1, y1, callback) {
    const dx = Math.abs(x1 - x0),
      dy = Math.abs(y1 - y0);
    const steps = Math.max(dx, dy, 1);
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      callback(Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t));
    }
  }

  canvas.addEventListener('mousedown', (e) => {
    isDrawing = true;
    const [x, y] = canvasPos(e);
    lastDrawPos = [x, y];
    if (state.tool === 'fill') {
      floodFill(x, y);
    } else {
      paintAt(x, y);
    }
    render();
  });

  canvas.addEventListener('mousemove', (e) => {
    const [x, y] = canvasPos(e);
    const [r, g, b] = getPixel(x, y);
    document.getElementById('info-text').textContent =
      `Pos: (${x}, ${y})\nRGB: (${(r * 255) | 0}, ${(g * 255) | 0}, ${(b * 255) | 0})`;

    if (!isDrawing || state.tool === 'fill') return;
    if (lastDrawPos) {
      lerp2D(lastDrawPos[0], lastDrawPos[1], x, y, (lx, ly) => paintAt(lx, ly));
    }
    lastDrawPos = [x, y];
    render();
  });

  canvas.addEventListener('mouseup', () => {
    isDrawing = false;
    lastDrawPos = null;
  });
  canvas.addEventListener('mouseleave', () => {
    isDrawing = false;
    lastDrawPos = null;
  });

  // Touch support
  canvas.addEventListener(
    'touchstart',
    (e) => {
      e.preventDefault();
      const touch = e.touches[0];
      canvas.dispatchEvent(
        new MouseEvent('mousedown', {
          clientX: touch.clientX,
          clientY: touch.clientY,
        })
      );
    },
    { passive: false }
  );

  canvas.addEventListener(
    'touchmove',
    (e) => {
      e.preventDefault();
      const touch = e.touches[0];
      canvas.dispatchEvent(
        new MouseEvent('mousemove', {
          clientX: touch.clientX,
          clientY: touch.clientY,
        })
      );
    },
    { passive: false }
  );

  canvas.addEventListener(
    'touchend',
    (e) => {
      e.preventDefault();
      canvas.dispatchEvent(new MouseEvent('mouseup'));
    },
    { passive: false }
  );

  // ── UI wiring ──────────────────────────────
  function hexToRgb(hex) {
    const r = parseInt(hex.slice(1, 3), 16) / 255;
    const g = parseInt(hex.slice(3, 5), 16) / 255;
    const b = parseInt(hex.slice(5, 7), 16) / 255;
    return [r, g, b];
  }

  // Tool buttons
  document.querySelectorAll('.tool-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tool-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      state.tool = btn.dataset.tool;
    });
  });

  // Color picker
  const colorPicker = document.getElementById('color-picker');
  colorPicker.addEventListener('input', () => {
    state.color = hexToRgb(colorPicker.value);
    document.querySelectorAll('.swatch').forEach((s) => s.classList.remove('active'));
  });

  // Palette swatches
  document.querySelectorAll('.swatch').forEach((swatch) => {
    swatch.addEventListener('click', () => {
      const hex = swatch.dataset.color;
      colorPicker.value = hex;
      state.color = hexToRgb(hex);
      document.querySelectorAll('.swatch').forEach((s) => s.classList.remove('active'));
      swatch.classList.add('active');
    });
  });

  // Brush size
  const brushSlider = document.getElementById('brush-size');
  brushSlider.addEventListener('input', () => {
    state.brushSize = parseInt(brushSlider.value);
    document.getElementById('brush-size-val').textContent = state.brushSize;
  });

  // Symmetry checkboxes
  const symMap = {
    'sym-translation-x': 'translationX',
    'sym-translation-y': 'translationY',
    'sym-mirror-x': 'mirrorX',
    'sym-mirror-y': 'mirrorY',
    'sym-diagonal': 'diagonal',
  };
  Object.entries(symMap).forEach(([id, key]) => {
    document.getElementById(id).addEventListener('change', (e) => {
      state.symmetry[key] = e.target.checked;
      invalidateSpectral();
    });
  });
  // Build per-angle rotation toggles
  const rotContainer = document.getElementById('rot-toggles');
  const rotCheckboxes = {};
  for (const angDeg of ROT_ANGLES) {
    const label = document.createElement('label');
    label.className = 'rot-toggle';
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.dataset.angle = angDeg;
    cb.addEventListener('change', () => {
      state.symmetry.rotations[angDeg] = cb.checked;
      label.classList.toggle('active', cb.checked);
      invalidateSpectral();
    });
    rotCheckboxes[angDeg] = cb;
    label.appendChild(cb);
    const span = document.createElement('span');
    span.textContent = angDeg + '°';
    label.appendChild(span);
    rotContainer.appendChild(label);
  }
  function setRotations(anglesSet) {
    for (const a of ROT_ANGLES) {
      const on = anglesSet.has(a);
      state.symmetry.rotations[a] = on;
      rotCheckboxes[a].checked = on;
      rotCheckboxes[a].parentElement.classList.toggle('active', on);
    }
    invalidateSpectral();
  }
  // Preset buttons: cyclic group Cn = rotations by 360/n * k for k=1..n-1
  function cyclicAngles(n) {
    const out = new Set();
    for (let k = 1; k < n; k++) {
      const a = Math.round((360 * k) / n);
      if (ROT_ANGLES.includes(a)) out.add(a);
    }
    return out;
  }
  document.getElementById('btn-rot-clear').addEventListener('click', () => setRotations(new Set()));
  document
    .getElementById('btn-rot-c4')
    .addEventListener('click', () => setRotations(cyclicAngles(4)));
  document
    .getElementById('btn-rot-c6')
    .addEventListener('click', () => setRotations(cyclicAngles(6)));
  document
    .getElementById('btn-rot-c8')
    .addEventListener('click', () => setRotations(cyclicAngles(8)));
  document
    .getElementById('btn-rot-c12')
    .addEventListener('click', () => setRotations(cyclicAngles(12)));
  // ── Lattice translation symmetries ────────
  const latTransListEl = document.getElementById('lat-trans-list');
  function renderLatTransList() {
    latTransListEl.innerHTML = '';
    const list = state.symmetry.latticeTranslations;
    if (!list.length) {
      const empty = document.createElement('div');
      empty.style.cssText = 'opacity:0.5; font-size:11px; padding:2px 0;';
      empty.textContent = '(none)';
      latTransListEl.appendChild(empty);
      return;
    }
    list.forEach((t, i) => {
      const row = document.createElement('div');
      row.className = 'lat-trans-item';
      row.style.cssText =
        'display:flex; gap:6px; align-items:center; font-size:11px; padding:1px 0;';
      const lab = document.createElement('span');
      lab.style.flex = '1';
      lab.textContent = `(${t.x.toFixed(3)}, ${t.y.toFixed(3)})`;
      const rm = document.createElement('button');
      rm.className = 'mini-btn';
      rm.textContent = '✕';
      rm.addEventListener('click', () => {
        state.symmetry.latticeTranslations.splice(i, 1);
        invalidateSpectral();
        renderLatTransList();
      });
      row.appendChild(lab);
      row.appendChild(rm);
      latTransListEl.appendChild(row);
    });
  }
  function addLatTrans(x, y) {
    if (!isFinite(x) || !isFinite(y)) return;
    // Avoid duplicates within tolerance
    const eps = 1e-4;
    const list = state.symmetry.latticeTranslations;
    for (const t of list) {
      if (Math.abs(t.x - x) < eps && Math.abs(t.y - y) < eps) return;
    }
    list.push({ x, y });
    invalidateSpectral();
    renderLatTransList();
  }
  function setLatTrans(vectors) {
    state.symmetry.latticeTranslations = vectors.map(([x, y]) => ({ x, y }));
    invalidateSpectral();
    renderLatTransList();
  }
  document.getElementById('btn-lat-trans-add').addEventListener('click', () => {
    const x = parseFloat(document.getElementById('lat-trans-x').value);
    const y = parseFloat(document.getElementById('lat-trans-y').value);
    addLatTrans(x, y);
  });
  document.getElementById('btn-lat-trans-clear').addEventListener('click', () => setLatTrans([]));
  document.getElementById('btn-lat-half').addEventListener('click', () =>
    setLatTrans([
      [0.5, 0],
      [0, 0.5],
    ])
  );
  document.getElementById('btn-lat-third').addEventListener('click', () =>
    setLatTrans([
      [1 / 3, 0],
      [0, 1 / 3],
    ])
  );
  document.getElementById('btn-lat-quarter').addEventListener('click', () =>
    setLatTrans([
      [0.25, 0],
      [0, 0.25],
    ])
  );
  // Hexagonal-ish: two vectors at 60° apart
  document.getElementById('btn-lat-hex').addEventListener('click', () =>
    setLatTrans([
      [0.5, 0],
      [0.25, Math.sqrt(3) / 4],
    ])
  );
  renderLatTransList();
  // ── Color permutation UI ──────────────────
  const PERM_OPTIONS = [
    { v: '+0', label: '+r' },
    { v: '-0', label: '−r' },
    { v: '+1', label: '+g' },
    { v: '-1', label: '−g' },
    { v: '+2', label: '+b' },
    { v: '-2', label: '−b' },
  ];
  const permSelects = document.querySelectorAll('.perm-sel');
  permSelects.forEach((sel) => {
    for (const opt of PERM_OPTIONS) {
      const o = document.createElement('option');
      o.value = opt.v;
      o.textContent = opt.label;
      sel.appendChild(o);
    }
    sel.addEventListener('change', () => {
      const out = parseInt(sel.dataset.out);
      const v = sel.value;
      const sign = v[0] === '-' ? -1 : 1;
      const src = parseInt(v.slice(1));
      state.colorPerm[out] = { src, sign };
      invalidateSpectral();
    });
  });
  function syncPermUI() {
    permSelects.forEach((sel) => {
      const out = parseInt(sel.dataset.out);
      const e = state.colorPerm[out];
      sel.value = (e.sign > 0 ? '+' : '-') + e.src;
    });
  }
  function setPerm(arr) {
    state.colorPerm = arr.map((e) => ({ ...e }));
    syncPermUI();
    invalidateSpectral();
  }
  syncPermUI();
  document.getElementById('btn-perm-id').addEventListener('click', () =>
    setPerm([
      { src: 0, sign: 1 },
      { src: 1, sign: 1 },
      { src: 2, sign: 1 },
    ])
  );
  document.getElementById('btn-perm-negate').addEventListener('click', () =>
    setPerm([
      { src: 0, sign: -1 },
      { src: 1, sign: -1 },
      { src: 2, sign: -1 },
    ])
  );
  document.getElementById('btn-perm-rgb-rot').addEventListener('click', () =>
    setPerm([
      { src: 1, sign: 1 },
      { src: 2, sign: 1 },
      { src: 0, sign: 1 },
    ])
  );
  document.getElementById('btn-perm-swap-rg').addEventListener('click', () =>
    setPerm([
      { src: 1, sign: 1 },
      { src: 0, sign: 1 },
      { src: 2, sign: 1 },
    ])
  );
  document.getElementById('btn-perm-neg-g').addEventListener('click', () =>
    setPerm([
      { src: 0, sign: 1 },
      { src: 1, sign: -1 },
      { src: 2, sign: 1 },
    ])
  );

  // Diffusion controls
  const diffRateSlider = document.getElementById('diff-rate');
  diffRateSlider.addEventListener('input', () => {
    state.diffRate = parseInt(diffRateSlider.value) / 100;
    document.getElementById('diff-rate-val').textContent = state.diffRate.toFixed(2);
  });

  const diffRenormSlider = document.getElementById('diff-renorm');
  diffRenormSlider.addEventListener('input', () => {
    state.diffRenorm = parseInt(diffRenormSlider.value) / 100;
    document.getElementById('diff-renorm-val').textContent = state.diffRenorm.toFixed(2);
  });

  document.getElementById('diff-neighborhood').addEventListener('change', (e) => {
    state.neighborhood = parseInt(e.target.value);
    invalidateSpectral();
  });

  document.getElementById('btn-step').addEventListener('click', () => {
    step();
    render();
  });

  document.getElementById('btn-play').addEventListener('click', startPlay);
  document.getElementById('btn-stop').addEventListener('click', stopPlay);

  const speedSlider = document.getElementById('diff-speed');
  speedSlider.addEventListener('input', () => {
    state.fps = parseInt(speedSlider.value);
    document.getElementById('diff-speed-val').textContent = state.fps;
  });

  // ── Spectral / mode controls ──────────────
  document.getElementById('diff-mode').addEventListener('change', (e) => {
    state.diffMode = e.target.value;
    document.getElementById('spectral-controls').style.display =
      state.diffMode === 'spectral' ? '' : 'none';
    document.getElementById('reaction-controls').style.display =
      state.diffMode === 'reaction' ? '' : 'none';
    // Local-mode controls (rate, renorm, neighborhood) are hidden in spectral mode
    document.getElementById('local-controls').style.display =
      state.diffMode === 'local' ? '' : 'none';
  });

  document.getElementById('spectral-filter').addEventListener('change', (e) => {
    state.spectralFilter = e.target.value;
  });

  const spectralTSlider = document.getElementById('spectral-t');
  spectralTSlider.addEventListener('input', () => {
    state.spectralT = parseInt(spectralTSlider.value) / 100;
    document.getElementById('spectral-t-val').textContent = state.spectralT.toFixed(2);
  });

  const spectralKSlider = document.getElementById('spectral-k');
  spectralKSlider.addEventListener('input', () => {
    state.spectralK = parseInt(spectralKSlider.value);
    document.getElementById('spectral-k-val').textContent = state.spectralK;
    invalidateSpectral();
  });

  document.getElementById('btn-recompute-spectral').addEventListener('click', () => {
    invalidateSpectral();
    ensureSpectral();
  });

  const rdFeedSlider = document.getElementById('rd-feed');
  rdFeedSlider.addEventListener('input', () => {
    state.rdFeed = parseInt(rdFeedSlider.value) / 1000;
    document.getElementById('rd-feed-val').textContent = state.rdFeed.toFixed(3);
  });
  const rdKillSlider = document.getElementById('rd-kill');
  rdKillSlider.addEventListener('input', () => {
    state.rdKill = parseInt(rdKillSlider.value) / 1000;
    document.getElementById('rd-kill-val').textContent = state.rdKill.toFixed(3);
  });

  // Canvas / grid controls
  document.getElementById('grid-size').addEventListener('change', (e) => {
    stopPlay();
    state.gridSize = parseInt(e.target.value);
    initGrid();
  });

  // Aspect ratio
  const aspectSlider = document.getElementById('aspect-ratio');
  const aspectVal = document.getElementById('aspect-ratio-val');
  aspectSlider.addEventListener('input', () => {
    // Slider range 25–400, value = ratio * 100
    state.aspectRatio = parseInt(aspectSlider.value) / 100;
    aspectVal.textContent = state.aspectRatio.toFixed(2);
    stopPlay();
    initGrid();
  });

  document.getElementById('btn-clear').addEventListener('click', clearPixels);
  document.getElementById('btn-random').addEventListener('click', randomPixels);
  // ── Lattice geometry controls ──────────────
  const latticeSelect = document.getElementById('lattice-mode');
  latticeSelect.addEventListener('change', (e) => {
    state.lattice = e.target.value;
    rebuildLatticeMask();
    invalidateSpectral();
    render();
  });
  const latticeCurvSlider = document.getElementById('lattice-curvature');
  if (latticeCurvSlider) {
    latticeCurvSlider.addEventListener('input', () => {
      state.latticeCurvature = parseInt(latticeCurvSlider.value) / 100;
      document.getElementById('lattice-curvature-val').textContent =
        state.latticeCurvature.toFixed(2);
      invalidateSpectral();
    });
  }
  const latticeRadSlider = document.getElementById('lattice-radius');
  if (latticeRadSlider) {
    latticeRadSlider.addEventListener('input', () => {
      state.latticeRadius = parseInt(latticeRadSlider.value);
      document.getElementById('lattice-radius-val').textContent =
        state.latticeRadius === 0 ? 'auto' : state.latticeRadius;
      rebuildLatticeMask();
      invalidateSpectral();
      render();
    });
  }

  document.getElementById('btn-save').addEventListener('click', () => {
    const link = document.createElement('a');
    link.download = 'symmetry_diffusion.png';
    link.href = canvas.toDataURL('image/png');
    link.click();
  });

  document.getElementById('show-grid').addEventListener('change', (e) => {
    state.showGrid = e.target.checked;
    render();
  });

  window.addEventListener('resize', () => {
    resizeCanvas();
    render();
  });

  // ── Boot ───────────────────────────────────
  initGrid();
  render();
})();
