// Affine IFS Point-Set Fitting — interactive lab
// ES6 module. No deps.

// ---------- tiny seeded RNG ----------
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
let rand = mulberry32(1);
function randn() {
  // Box-Muller
  const u = Math.max(1e-12, rand());
  const v = rand();
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

// ---------- DOM ----------
const $ = (id) => document.getElementById(id);
const view = $('view');
const ctx = view.getContext('2d');
const lossCanvas = $('lossCanvas');
const lctx = lossCanvas.getContext('2d');

// ---------- world<->screen ----------
let world = { cx: 0, cy: 0, scale: 200 }; // 1 unit = 200 px
function w2s(x, y) {
  return [
    view.width / 2 + (x - world.cx) * world.scale,
    view.height / 2 - (y - world.cy) * world.scale,
  ];
}
function s2w(sx, sy) {
  return [
    (sx - view.width / 2) / world.scale + world.cx,
    -(sy - view.height / 2) / world.scale + world.cy,
  ];
}

function resizeCanvas() {
  const dpr = window.devicePixelRatio || 1;
  view.width = view.clientWidth * dpr;
  view.height = view.clientHeight * dpr;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  lossCanvas.width = lossCanvas.clientWidth * dpr;
  lossCanvas.height = lossCanvas.clientHeight * dpr;
}
window.addEventListener('resize', () => {
  resizeCanvas();
  draw();
});

// ---------- state ----------
const state = {
  K: 2,
  N: 7,
  enumeration: 'commutative',
  target: [], // [[x,y],...]
  // params: theta[k] = {A: [a11,a12,a21,a22], b:[b1,b2]}
  theta: [],
  orbit: [], // [[x,y],...]
  lossHistory: [],
  iter: 0,
  running: false,
  lastLoss: null,
  // adam state
  m: [],
  v: [],
  adamT: 0,
};

// ---------- enumeration ----------
// ordered: returns array of words, each word = array of K-indices length N
function enumerateOrdered(K, N) {
  const out = [];
  const w = new Array(N).fill(0);
  while (true) {
    out.push(w.slice());
    // increment
    let i = N - 1;
    while (i >= 0) {
      w[i]++;
      if (w[i] < K) break;
      w[i] = 0;
      i--;
    }
    if (i < 0) break;
  }
  return out;
}
// commutative: multisets (n_1,...,n_K) summing to N. Returned as a canonical word
// We apply T_1 n_1 times, then T_2 n_2 times, etc.
function enumerateCommutative(K, N) {
  const out = [];
  const counts = new Array(K).fill(0);
  function rec(k, remaining) {
    if (k === K - 1) {
      counts[k] = remaining;
      // build word
      const w = [];
      for (let j = 0; j < K; j++) for (let t = 0; t < counts[j]; t++) w.push(j);
      out.push(w);
      return;
    }
    for (let n = 0; n <= remaining; n++) {
      counts[k] = n;
      rec(k + 1, remaining - n);
    }
  }
  rec(0, N);
  return out;
}
function enumerate() {
  if (state.enumeration === 'ordered') return enumerateOrdered(state.K, state.N);
  return enumerateCommutative(state.K, state.N);
}

// ---------- transforms ----------
function newTheta(K) {
  const theta = [];
  for (let k = 0; k < K; k++) {
    theta.push({
      A: [0.5 + 0.05 * randn(), 0.05 * randn(), 0.05 * randn(), 0.5 + 0.05 * randn()],
      b: [0.2 * randn(), 0.2 * randn()],
    });
  }
  return theta;
}
function applyT(t, x, y) {
  const [a, b, c, d] = t.A;
  return [a * x + b * y + t.b[0], c * x + d * y + t.b[1]];
}

// ---------- forward pass ----------
// Returns array of points (one per word), and trace info needed for backward
function forward(words, theta) {
  const orbit = new Array(words.length);
  const traces = new Array(words.length); // intermediate x's per word
  for (let i = 0; i < words.length; i++) {
    const w = words[i];
    let x = 0,
      y = 0;
    const trace = new Array(w.length + 1);
    trace[0] = [0, 0];
    for (let s = 0; s < w.length; s++) {
      const [nx, ny] = applyT(theta[w[s]], x, y);
      x = nx;
      y = ny;
      trace[s + 1] = [x, y];
    }
    orbit[i] = [x, y];
    traces[i] = trace;
  }
  return { orbit, traces };
}

// ---------- nearest neighbors (brute force) ----------
function nearestIndex(p, set) {
  let best = -1,
    bd = Infinity;
  for (let i = 0; i < set.length; i++) {
    const dx = p[0] - set[i][0],
      dy = p[1] - set[i][1];
    const d = dx * dx + dy * dy;
    if (d < bd) {
      bd = d;
      best = i;
    }
  }
  return { idx: best, dist2: bd };
}

// ---------- loss & gradient ----------
// We accumulate grad wrt each theta[k].A (4 vals) and theta[k].b (2 vals)
function zeroGrad(K) {
  const g = [];
  for (let k = 0; k < K; k++) g.push({ A: [0, 0, 0, 0], b: [0, 0] });
  return g;
}

function computeLossAndGrad(words, theta) {
  const K = state.K;
  const Q = state.target;
  const alpha = parseFloat($('alpha').value);
  const beta = parseFloat($('beta').value);

  const { orbit, traces } = forward(words, theta);
  const P = orbit;
  const M = Q.length;
  const Np = P.length;

  const grad = zeroGrad(K);
  let loss = 0;

  // For each p in P, compute nearest q in Q.
  // Also for Q->P matching, remember per-p the q's that map to it.
  const nnP = new Array(Np); // {idx,dist2}
  for (let i = 0; i < Np; i++) nnP[i] = M > 0 ? nearestIndex(P[i], Q) : { idx: -1, dist2: 0 };

  // Forward chamfer P->Q
  if (M > 0 && alpha !== 0) {
    let sum = 0;
    for (let i = 0; i < Np; i++) sum += nnP[i].dist2;
    loss += alpha * (sum / Np);
  }

  // gradient contribution of point P[i] with target T = nearest:
  // L_i = c * ||P_i - T||^2
  // dL_i/dP_i = 2*c*(P_i - T)
  // We need a per-point upstream gradient gP[i] = dL/dP[i]
  const gP = new Array(Np);
  for (let i = 0; i < Np; i++) gP[i] = [0, 0];

  if (M > 0 && alpha !== 0) {
    const c = alpha / Np;
    for (let i = 0; i < Np; i++) {
      const q = Q[nnP[i].idx];
      gP[i][0] += 2 * c * (P[i][0] - q[0]);
      gP[i][1] += 2 * c * (P[i][1] - q[1]);
    }
  }

  // Backward chamfer Q->P
  if (M > 0 && beta !== 0 && Np > 0) {
    let sum = 0;
    const c = beta / M;
    for (let j = 0; j < M; j++) {
      const nn = nearestIndex(Q[j], P);
      sum += nn.dist2;
      // dL/dP[nn.idx] += 2c (P[nn.idx] - Q[j])
      gP[nn.idx][0] += 2 * c * (P[nn.idx][0] - Q[j][0]);
      gP[nn.idx][1] += 2 * c * (P[nn.idx][1] - Q[j][1]);
    }
    loss += beta * (sum / M);
  }

  // Backprop each P[i] through its word
  // x_{s+1} = A_{w_s} x_s + b_{w_s}
  // dL/dx_s = A_{w_s}^T (dL/dx_{s+1})
  // dL/dA_{w_s} += (dL/dx_{s+1}) outer x_s
  // dL/db_{w_s} += (dL/dx_{s+1})
  for (let i = 0; i < Np; i++) {
    let gx = gP[i][0],
      gy = gP[i][1];
    if (gx === 0 && gy === 0) continue;
    const w = words[i];
    const trace = traces[i];
    for (let s = w.length - 1; s >= 0; s--) {
      const k = w[s];
      const xs = trace[s][0],
        ys = trace[s][1];
      // dA
      grad[k].A[0] += gx * xs;
      grad[k].A[1] += gx * ys;
      grad[k].A[2] += gy * xs;
      grad[k].A[3] += gy * ys;
      // db
      grad[k].b[0] += gx;
      grad[k].b[1] += gy;
      // propagate gx,gy <- A^T g
      const [a, b, c, d] = theta[k].A;
      const ngx = a * gx + c * gy;
      const ngy = b * gx + d * gy;
      gx = ngx;
      gy = ngy;
    }
  }

  // Regularizers
  const lamA = parseFloat($('lamA').value) || 0;
  const lamb = parseFloat($('lamb').value) || 0;
  const lamC = parseFloat($('lamC').value) || 0;
  const eps = parseFloat($('eps').value) || 0;

  for (let k = 0; k < K; k++) {
    const A = theta[k].A,
      b = theta[k].b;
    if (lamA !== 0) {
      loss += lamA * (A[0] * A[0] + A[1] * A[1] + A[2] * A[2] + A[3] * A[3]);
      for (let r = 0; r < 4; r++) grad[k].A[r] += 2 * lamA * A[r];
    }
    if (lamb !== 0) {
      loss += lamb * (b[0] * b[0] + b[1] * b[1]);
      grad[k].b[0] += 2 * lamb * b[0];
      grad[k].b[1] += 2 * lamb * b[1];
    }
    // contractivity: penalize max singular value > 1 - eps
    // approximate via largest eigenvalue of A^T A:
    // Use Frobenius-based soft bound: penalize (||A||_F - sqrt(2)(1-eps))_+^2
    // (cheap and stable)
    if (lamC !== 0) {
      const frob = Math.sqrt(A[0] * A[0] + A[1] * A[1] + A[2] * A[2] + A[3] * A[3]);
      const target = Math.SQRT2 * (1 - eps);
      const ex = frob - target;
      if (ex > 0) {
        loss += lamC * ex * ex;
        // d/dA_r frob = A_r / frob
        const coef = (2 * lamC * ex) / Math.max(frob, 1e-9);
        for (let r = 0; r < 4; r++) grad[k].A[r] += coef * A[r];
      }
    }
  }

  return { loss, grad, orbit };
}

// ---------- optimizer ----------
function ensureAdamState(K) {
  if (state.m.length !== K) {
    state.m = [];
    state.v = [];
    state.adamT = 0;
    for (let k = 0; k < K; k++) {
      state.m.push({ A: [0, 0, 0, 0], b: [0, 0] });
      state.v.push({ A: [0, 0, 0, 0], b: [0, 0] });
    }
  }
}
function stepOptimizer(grad) {
  const lr = parseFloat($('lr').value);
  const optim = $('optim').value;
  if (optim === 'sgd') {
    for (let k = 0; k < state.K; k++) {
      for (let r = 0; r < 4; r++) state.theta[k].A[r] -= lr * grad[k].A[r];
      for (let r = 0; r < 2; r++) state.theta[k].b[r] -= lr * grad[k].b[r];
    }
  } else {
    ensureAdamState(state.K);
    const b1 = 0.9,
      b2 = 0.999,
      eps = 1e-8;
    state.adamT++;
    const bc1 = 1 - Math.pow(b1, state.adamT);
    const bc2 = 1 - Math.pow(b2, state.adamT);
    for (let k = 0; k < state.K; k++) {
      for (let r = 0; r < 4; r++) {
        const g = grad[k].A[r];
        state.m[k].A[r] = b1 * state.m[k].A[r] + (1 - b1) * g;
        state.v[k].A[r] = b2 * state.v[k].A[r] + (1 - b2) * g * g;
        const mh = state.m[k].A[r] / bc1;
        const vh = state.v[k].A[r] / bc2;
        state.theta[k].A[r] -= (lr * mh) / (Math.sqrt(vh) + eps);
      }
      for (let r = 0; r < 2; r++) {
        const g = grad[k].b[r];
        state.m[k].b[r] = b1 * state.m[k].b[r] + (1 - b1) * g;
        state.v[k].b[r] = b2 * state.v[k].b[r] + (1 - b2) * g * g;
        const mh = state.m[k].b[r] / bc1;
        const vh = state.v[k].b[r] / bc2;
        state.theta[k].b[r] -= (lr * mh) / (Math.sqrt(vh) + eps);
      }
    }
  }
}

// ---------- training step ----------
let cachedWords = null;
let cachedKey = '';
function getWords() {
  const key = `${state.K}|${state.N}|${state.enumeration}`;
  if (key !== cachedKey) {
    cachedWords = enumerate();
    cachedKey = key;
  }
  return cachedWords;
}
function trainStep() {
  const words = getWords();
  const { loss, grad, orbit } = computeLossAndGrad(words, state.theta);
  stepOptimizer(grad);
  state.orbit = orbit;
  state.lastLoss = loss;
  state.iter++;
  state.lossHistory.push(loss);
  if (state.lossHistory.length > 2000) state.lossHistory.shift();
}

function forwardOnly() {
  const words = getWords();
  const { orbit } = forward(words, state.theta);
  state.orbit = orbit;
}

// ---------- drawing ----------
function drawGrid() {
  ctx.fillStyle = '#0a0d12';
  ctx.fillRect(0, 0, view.width, view.height);
  ctx.strokeStyle = '#161b22';
  ctx.lineWidth = 1;
  const step = 0.25;
  const [x0, y0] = s2w(0, view.height);
  const [x1, y1] = s2w(view.width, 0);
  ctx.beginPath();
  for (let x = Math.floor(x0 / step) * step; x <= x1; x += step) {
    const [sx] = w2s(x, 0);
    ctx.moveTo(sx, 0);
    ctx.lineTo(sx, view.height);
  }
  for (let y = Math.floor(y0 / step) * step; y <= y1; y += step) {
    const [, sy] = w2s(0, y);
    ctx.moveTo(0, sy);
    ctx.lineTo(view.width, sy);
  }
  ctx.stroke();
  // axes
  ctx.strokeStyle = '#30363d';
  ctx.beginPath();
  const [, sy0] = w2s(0, 0);
  const [sx0] = w2s(0, 0);
  ctx.moveTo(0, sy0);
  ctx.lineTo(view.width, sy0);
  ctx.moveTo(sx0, 0);
  ctx.lineTo(sx0, view.height);
  ctx.stroke();
}

function drawPoints(pts, color, r) {
  ctx.fillStyle = color;
  for (let i = 0; i < pts.length; i++) {
    const [sx, sy] = w2s(pts[i][0], pts[i][1]);
    ctx.beginPath();
    ctx.arc(sx, sy, r, 0, Math.PI * 2);
    ctx.fill();
  }
}

function drawFixedPoints() {
  // fixed point of T_k: x = A x + b -> (I-A) x = b
  ctx.fillStyle = '#ff7b72';
  ctx.strokeStyle = '#ff7b72';
  for (let k = 0; k < state.theta.length; k++) {
    const { A, b } = state.theta[k];
    // I - A = [[1-a, -b], [-c, 1-d]]
    const m11 = 1 - A[0],
      m12 = -A[1],
      m21 = -A[2],
      m22 = 1 - A[3];
    const det = m11 * m22 - m12 * m21;
    if (Math.abs(det) < 1e-6) continue;
    const fx = (m22 * b[0] - m12 * b[1]) / det;
    const fy = (-m21 * b[0] + m11 * b[1]) / det;
    const [sx, sy] = w2s(fx, fy);
    ctx.beginPath();
    ctx.arc(sx, sy, 6, 0, Math.PI * 2);
    ctx.stroke();
    ctx.font = '11px ui-monospace';
    ctx.fillText('T' + (k + 1), sx + 8, sy - 6);
  }
}

function draw() {
  drawGrid();
  drawPoints(state.target, '#3fb950', 2.5);
  drawPoints(state.orbit, '#58a6ff', 1.8);
  drawFixedPoints();
}

function drawLossCurve() {
  const w = lossCanvas.width,
    h = lossCanvas.height;
  lctx.fillStyle = '#0d1117';
  lctx.fillRect(0, 0, w, h);
  const data = state.lossHistory;
  if (data.length < 2) return;
  // log scale
  const logs = data.map((v) => Math.log10(Math.max(v, 1e-10)));
  let lo = Math.min(...logs),
    hi = Math.max(...logs);
  if (hi - lo < 1e-6) hi = lo + 1e-6;
  lctx.strokeStyle = '#58a6ff';
  lctx.lineWidth = 1.5;
  lctx.beginPath();
  for (let i = 0; i < logs.length; i++) {
    const x = (i / (logs.length - 1)) * w;
    const y = h - ((logs[i] - lo) / (hi - lo)) * (h - 4) - 2;
    if (i === 0) lctx.moveTo(x, y);
    else lctx.lineTo(x, y);
  }
  lctx.stroke();
  lctx.fillStyle = '#7d8590';
  lctx.font = '10px ui-monospace';
  lctx.fillText(`log10 loss [${lo.toFixed(2)}, ${hi.toFixed(2)}]`, 4, 12);
}

// ---------- UI: transforms inspector ----------
function renderTransforms() {
  const root = $('transforms');
  root.innerHTML = '';
  for (let k = 0; k < state.theta.length; k++) {
    const t = state.theta[k];
    const card = document.createElement('div');
    card.className = 'tf';
    card.innerHTML = `
      <div class="tf-head">
        <b>T${k + 1}</b>
        <span class="small">det=${(t.A[0] * t.A[3] - t.A[1] * t.A[2]).toFixed(3)} σ̂≈${specRad(t.A).toFixed(3)}</span>
      </div>
      A =
      <div class="matrix-row">
        <input data-k="${k}" data-r="A" data-i="0" value="${t.A[0].toFixed(3)}"/>
        <input data-k="${k}" data-r="A" data-i="1" value="${t.A[1].toFixed(3)}"/>
        <input data-k="${k}" data-r="b" data-i="0" value="${t.b[0].toFixed(3)}"/>
      </div>
      <div class="matrix-row">
        <input data-k="${k}" data-r="A" data-i="2" value="${t.A[2].toFixed(3)}"/>
        <input data-k="${k}" data-r="A" data-i="3" value="${t.A[3].toFixed(3)}"/>
        <input data-k="${k}" data-r="b" data-i="1" value="${t.b[1].toFixed(3)}"/>
      </div>
      <div class="small">(col 3 = b)</div>
    `;
    root.appendChild(card);
  }
  root.querySelectorAll('input').forEach((inp) => {
    inp.addEventListener('change', (e) => {
      const k = +e.target.dataset.k;
      const r = e.target.dataset.r;
      const i = +e.target.dataset.i;
      const v = parseFloat(e.target.value);
      if (!isNaN(v)) {
        state.theta[k][r][i] = v;
        forwardOnly();
        draw();
      }
    });
  });
}

// approximate spectral radius of 2x2 A
function specRad(A) {
  const [a, b, c, d] = A;
  const tr = a + d,
    det = a * d - b * c;
  const disc = (tr * tr) / 4 - det;
  if (disc >= 0) {
    const s = Math.sqrt(disc);
    return Math.max(Math.abs(tr / 2 + s), Math.abs(tr / 2 - s));
  } else {
    return Math.sqrt(det); // |complex eigenvalues|
  }
}

// ---------- target presets ----------
function presetCircle(n = 200) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const a = (2 * Math.PI * i) / n;
    pts.push([0.6 * Math.cos(a), 0.6 * Math.sin(a)]);
  }
  return pts;
}
function presetSquare(n = 200) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const t = (i / n) * 4;
    const s = t % 1;
    if (t < 1) pts.push([-0.6 + 1.2 * s, -0.6]);
    else if (t < 2) pts.push([0.6, -0.6 + 1.2 * s]);
    else if (t < 3) pts.push([0.6 - 1.2 * s, 0.6]);
    else pts.push([-0.6, 0.6 - 1.2 * s]);
  }
  return pts;
}
function presetHeart(n = 250) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const t = (2 * Math.PI * i) / n;
    const x = 16 * Math.pow(Math.sin(t), 3);
    const y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
    pts.push([x / 20, y / 20]);
  }
  return pts;
}
function presetSpiral(n = 300) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const t = (i / n) * 6 * Math.PI;
    const r = 0.05 + (0.7 * i) / n;
    pts.push([r * Math.cos(t), r * Math.sin(t)]);
  }
  return pts;
}
function presetSierpinski(n = 1500) {
  // classic chaos game
  const verts = [
    [-0.6, -0.5],
    [0.6, -0.5],
    [0, 0.6],
  ];
  const pts = [];
  let x = 0,
    y = 0;
  for (let i = 0; i < n + 50; i++) {
    const v = verts[(Math.random() * 3) | 0];
    x = (x + v[0]) / 2;
    y = (y + v[1]) / 2;
    if (i > 50) pts.push([x, y]);
  }
  return pts;
}
function presetFern(n = 2000) {
  const pts = [];
  let x = 0,
    y = 0;
  for (let i = 0; i < n + 50; i++) {
    const r = Math.random();
    let nx, ny;
    if (r < 0.01) {
      nx = 0;
      ny = 0.16 * y;
    } else if (r < 0.86) {
      nx = 0.85 * x + 0.04 * y;
      ny = -0.04 * x + 0.85 * y + 1.6;
    } else if (r < 0.93) {
      nx = 0.2 * x - 0.26 * y;
      ny = 0.23 * x + 0.22 * y + 1.6;
    } else {
      nx = -0.15 * x + 0.28 * y;
      ny = 0.26 * x + 0.24 * y + 0.44;
    }
    x = nx;
    y = ny;
    if (i > 50) pts.push([x / 6, y / 6 - 0.7]);
  }
  return pts;
}
function presetCantor(n = 1500) {
  const pts = [];
  function rec(x, w, depth) {
    if (depth === 0) {
      for (let i = 0; i < 6; i++) pts.push([x + Math.random() * w, 0]);
      return;
    }
    rec(x, w / 3, depth - 1);
    rec(x + (2 * w) / 3, w / 3, depth - 1);
  }
  rec(-0.7, 1.4, 6);
  return pts.slice(0, n);
}
function presetGrid(n = 7) {
  const pts = [];
  for (let i = -n; i <= n; i++) {
    for (let j = -n; j <= n; j++) {
      pts.push([i / (n * 1.5), j / (n * 1.5)]);
    }
  }
  return pts;
}

// ---------- init presets ----------
function initSmall() {
  rand = mulberry32(parseInt($('seed').value) || 1);
  state.theta = newTheta(state.K);
}
function initRotations() {
  rand = mulberry32(parseInt($('seed').value) || 1);
  state.theta = [];
  for (let k = 0; k < state.K; k++) {
    const a = (2 * Math.PI * k) / state.K + 0.1 * randn();
    const s = 0.5;
    state.theta.push({
      A: [s * Math.cos(a), -s * Math.sin(a), s * Math.sin(a), s * Math.cos(a)],
      b: [0.3 * Math.cos(a), 0.3 * Math.sin(a)],
    });
  }
}
function initShrink() {
  rand = mulberry32(parseInt($('seed').value) || 1);
  state.theta = [];
  for (let k = 0; k < state.K; k++) {
    const cx = 0.5 * Math.cos((2 * Math.PI * k) / state.K);
    const cy = 0.5 * Math.sin((2 * Math.PI * k) / state.K);
    // T(x) = 0.5 x + 0.5 c -> fixed point at c
    state.theta.push({ A: [0.5, 0, 0, 0.5], b: [0.5 * cx, 0.5 * cy] });
  }
}
function initSierpinski3() {
  state.K = 3;
  $('K').value = 3;
  const verts = [
    [-0.6, -0.5],
    [0.6, -0.5],
    [0, 0.6],
  ];
  state.theta = verts.map((v) => ({
    A: [0.5, 0, 0, 0.5],
    b: [0.5 * v[0], 0.5 * v[1]],
  }));
  cachedKey = '';
}
function initBarnsley() {
  state.K = 4;
  $('K').value = 4;
  state.theta = [
    { A: [0, 0, 0, 0.16], b: [0, -0.7] },
    { A: [0.85, 0.04, -0.04, 0.85], b: [0, 0.27] },
    { A: [0.2, -0.26, 0.23, 0.22], b: [0, 0.27] },
    { A: [-0.15, 0.28, 0.26, 0.24], b: [0, -0.07] },
  ];
  cachedKey = '';
}

// ---------- UI wiring ----------
function refreshK() {
  const newK = Math.max(1, Math.min(6, parseInt($('K').value) || 2));
  if (newK !== state.K) {
    state.K = newK;
    cachedKey = '';
    initSmall();
  }
}
function refreshN() {
  const newN = Math.max(1, Math.min(12, parseInt($('N').value) || 5));
  if (newN !== state.N) {
    state.N = newN;
    cachedKey = '';
  }
}
function updateOrbitSize() {
  const w = getWords();
  $('orbitSize').textContent = `orbit size: ${w.length} words`;
  $('orbitCount').textContent = w.length;
}

$('K').addEventListener('change', () => {
  refreshK();
  afterParamChange();
});
$('N').addEventListener('change', () => {
  refreshN();
  afterParamChange();
});
$('enum').addEventListener('change', () => {
  state.enumeration = $('enum').value;
  cachedKey = '';
  afterParamChange();
});
$('initSmall').addEventListener('click', () => {
  initSmall();
  afterParamChange();
});
$('initRot').addEventListener('click', () => {
  initRotations();
  afterParamChange();
});
$('initShrink').addEventListener('click', () => {
  initShrink();
  afterParamChange();
});
$('initSierp').addEventListener('click', () => {
  initSierpinski3();
  afterParamChange();
});
$('initBarnsley').addEventListener('click', () => {
  initBarnsley();
  afterParamChange();
});
$('reset').addEventListener('click', () => {
  initSmall();
  state.iter = 0;
  state.lossHistory = [];
  state.adamT = 0;
  state.m = [];
  state.v = [];
  afterParamChange();
});

$('step').addEventListener('click', () => {
  const steps = parseInt($('steps').value) || 1;
  for (let i = 0; i < steps; i++) trainStep();
  updateHUD();
  draw();
  renderTransforms();
  drawLossCurve();
});

$('run').addEventListener('click', () => {
  state.running = !state.running;
  $('run').textContent = state.running ? '⏸ Pause' : '▶ Run';
  if (state.running) loop();
});

let lastFrame = 0;
function loop() {
  if (!state.running) return;
  const now = performance.now();
  const fps = parseFloat($('fps').value) || 60;
  const minDt = 1000 / fps;
  if (now - lastFrame >= minDt) {
    const steps = parseInt($('steps').value) || 1;
    for (let i = 0; i < steps; i++) trainStep();
    updateHUD();
    draw();
    renderTransforms();
    drawLossCurve();
    lastFrame = now;
  }
  requestAnimationFrame(loop);
}

function afterParamChange() {
  ensureAdamState(state.K);
  forwardOnly();
  updateOrbitSize();
  updateHUD();
  draw();
  renderTransforms();
}

function updateHUD() {
  $('targCount').textContent = state.target.length;
  $('orbitCount').textContent = state.orbit.length;
  $('iterVal').textContent = state.iter;
  $('lossVal').textContent = state.lastLoss == null ? '—' : state.lastLoss.toExponential(3);
}

// ---------- target drawing ----------
let drawing = false;
view.addEventListener('mousedown', (e) => {
  drawing = true;
  addTargetAt(e);
});
view.addEventListener('mousemove', (e) => {
  if (drawing) addTargetAt(e);
});
window.addEventListener('mouseup', () => {
  drawing = false;
});
function addTargetAt(e) {
  const rect = view.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  const sx = (e.clientX - rect.left) * dpr;
  const sy = (e.clientY - rect.top) * dpr;
  const [wx, wy] = s2w(sx, sy);
  // throttle: only add if distance > 0.01 from last
  const last = state.target[state.target.length - 1];
  if (last) {
    const d = Math.hypot(wx - last[0], wy - last[1]);
    if (d < 0.015) return;
  }
  state.target.push([wx, wy]);
  updateHUD();
  draw();
}
$('targetClear').addEventListener('click', () => {
  state.target = [];
  updateHUD();
  draw();
});
$('loadPreset').addEventListener('click', () => {
  const v = $('targetPreset').value;
  if (!v) return;
  const map = {
    circle: presetCircle,
    square: presetSquare,
    heart: presetHeart,
    spiral: presetSpiral,
    sierpinski: presetSierpinski,
    fern: presetFern,
    cantor: presetCantor,
    grid: presetGrid,
  };
  state.target = map[v]();
  updateHUD();
  draw();
});

// ---------- keyboard ----------
window.addEventListener('keydown', (e) => {
  if (e.code === 'Space' && e.target.tagName !== 'INPUT') {
    e.preventDefault();
    $('step').click();
  }
});

// ---------- zoom/pan ----------
view.addEventListener(
  'wheel',
  (e) => {
    e.preventDefault();
    const rect = view.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const sx = (e.clientX - rect.left) * dpr;
    const sy = (e.clientY - rect.top) * dpr;
    const [wx, wy] = s2w(sx, sy);
    const factor = Math.exp(-e.deltaY * 0.001);
    world.scale *= factor;
    // re-anchor zoom on cursor
    const [nwx, nwy] = s2w(sx, sy);
    world.cx += wx - nwx;
    world.cy += wy - nwy;
    draw();
  },
  { passive: false }
);

// right-click drag pan
let panning = false,
  panLast = null;
view.addEventListener('contextmenu', (e) => e.preventDefault());
view.addEventListener('mousedown', (e) => {
  if (e.button === 2) {
    panning = true;
    panLast = [e.clientX, e.clientY];
    drawing = false;
  }
});
window.addEventListener('mousemove', (e) => {
  if (panning) {
    const dx = e.clientX - panLast[0];
    const dy = e.clientY - panLast[1];
    const dpr = window.devicePixelRatio || 1;
    world.cx -= (dx * dpr) / world.scale;
    world.cy += (dy * dpr) / world.scale;
    panLast = [e.clientX, e.clientY];
    draw();
  }
});
window.addEventListener('mouseup', (e) => {
  if (e.button === 2) panning = false;
});

// ---------- boot ----------
resizeCanvas();
state.K = parseInt($('K').value);
state.N = parseInt($('N').value);
state.enumeration = $('enum').value;
rand = mulberry32(parseInt($('seed').value) || 1);
initSmall();
state.target = presetCircle(150);
$('targetPreset').value = 'circle';
ensureAdamState(state.K);
forwardOnly();
updateOrbitSize();
updateHUD();
draw();
renderTransforms();
