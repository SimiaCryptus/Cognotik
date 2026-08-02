import { OptimizerLbfgs } from './js/optimizer-lbfgs.js';
import { OptimizerAdam } from './js/optimizer-adam.js';
import { OptimizerQQN } from './js/optimizer-qqn.js';
import { KnotXR } from './js/webxr.js';

/**
 * Knot Topology Lab
 *
 * Demonstrates knot structure through distance matrix visualization.
 * Uses two constraints:
 * 1. Fixed-length edges between adjacent points on the spline
 * 2. Repulsion between non-adjacent points
 */

const state = {
  isTraining: false,
  points: null,
  optimizer: null,
  step: 0,
  animationId: null,
  rotation: { x: 0.3, y: 0 },
  zoom: 1.0,
  isDragging: false,
  isOrbitingKnot: false,
  autoRotate: true,
  showEdges: true,
  solidView: false,
  hoveredPair: null,
  params: {
    n: 64,
    controlPoints: 14,
    targetEdgeLength: 0.15,
    edgeStiffness: 10.0,
    repulsionStrength: 0.5,
    repulsionCutoff: 0.5,
    lr: 0.01,
    redistributeLr: 0.05,
    redistributeEvery: 1,
    entropyWeight: 0.0,
    entropyTau: 0.1,
    c: 1.0,
    knotType: 'random',
    metricMode: 'euclidean',
    optimizerType: 'adam',
  },
  metrics: {
    totalLoss: 0,
    edgeLoss: 0,
    repulsionLoss: 0,
    entropyLoss: 0,
    entropy: 0,
    minDist: 0,
    maxDist: 0,
    avgDist: 0,
  },
  distanceMatrix: null,
  minkowskiData: null,
  redistributeOptimizer: null,
  arcLengthVar: null,
  arcLengthTable: null,
  xr: null,
};

const els = {
  knotCanvas: document.getElementById('knot-canvas'),
  matrixCanvas: document.getElementById('matrix-canvas'),
  loading: document.getElementById('loading'),
  knotSelect: document.getElementById('knot-select'),
  optimizerSelect: document.getElementById('opt-optimizer'),
  metricSelect: document.getElementById('metric-select'),
  grpC: document.getElementById('grp-c'),
  cInput: document.getElementById('param-c'),
  valC: document.getElementById('val-c'),
  nInput: document.getElementById('param-n'),
  valN: document.getElementById('val-n'),
  ctrlInput: document.getElementById('param-ctrl'),
  valCtrl: document.getElementById('val-ctrl'),
  edgeInput: document.getElementById('param-edge'),
  valEdge: document.getElementById('val-edge'),
  stiffInput: document.getElementById('param-stiff'),
  valStiff: document.getElementById('val-stiff'),
  repelInput: document.getElementById('param-repel'),
  valRepel: document.getElementById('val-repel'),
  cutoffInput: document.getElementById('param-cutoff'),
  valCutoff: document.getElementById('val-cutoff'),
  lrInput: document.getElementById('param-lr'),
  valLr: document.getElementById('val-lr'),
  redistInput: document.getElementById('param-redist'),
  valRedist: document.getElementById('val-redist'),
  redistItersInput: document.getElementById('param-redist-iters'),
  valRedistIters: document.getElementById('val-redist-iters'),
  entropyInput: document.getElementById('param-entropy'),
  valEntropy: document.getElementById('val-entropy'),
  entropyTauInput: document.getElementById('param-entropy-tau'),
  valEntropyTau: document.getElementById('val-entropy-tau'),
  metricEntropy: document.getElementById('metric-entropy'),
  chkAutoRotate: document.getElementById('chk-autorotate'),
  chkEdges: document.getElementById('chk-edges'),
  btnToggle: document.getElementById('btn-toggle'),
  chkSolid: document.getElementById('chk-solid'),
  btnExportStl: document.getElementById('btn-export-stl'),
  btnReset: document.getElementById('btn-reset'),
  btnStep: document.getElementById('btn-step'),
  btnDistribute: document.getElementById('btn-distribute'),
  btnCopy: document.getElementById('btn-copy'),
  btnPaste: document.getElementById('btn-paste'),
  btnOrbitKnot: document.getElementById('btn-orbit-knot'),
  btnOptTime: document.getElementById('btn-opt-time'),
  btnOptSpace: document.getElementById('btn-opt-space'),
  btnOptLight: document.getElementById('btn-opt-light'),
  btnAlignTime: document.getElementById('btn-align-time'),
  btnEnterVr: document.getElementById('btn-enter-vr'),
  vrStatus: document.getElementById('vr-status'),
  metricLoss: document.getElementById('metric-loss'),
  metricEdge: document.getElementById('metric-edge'),
  metricRepel: document.getElementById('metric-repel'),
  metricStep: document.getElementById('metric-step'),
  metricMinDist: document.getElementById('metric-min-dist'),
  metricMaxDist: document.getElementById('metric-max-dist'),
  metricAvgDist: document.getElementById('metric-avg-dist'),
  colorbarMax: document.getElementById('colorbar-max'),
  colorbarMid: document.getElementById('colorbar-mid'),
  colorbarMin: document.getElementById('colorbar-min'),
  colorbarGrad: document.getElementById('colorbar-grad'),
};

const knotCtx = els.knotCanvas.getContext('2d');
const matrixCtx = els.matrixCanvas.getContext('2d');
function createOptimizer() {
  if (state.params.optimizerType === 'adam') {
    return new OptimizerAdam(state.params.lr);
  } else if (state.params.optimizerType === 'qqn') {
    return new OptimizerQQN(state.params.lr);
  }
  return new OptimizerLbfgs(state.params.lr);
}
/**
 * Create an optimizer for the redistribution sub-problem,
 * using the same optimizer class as the main optimizer but with redistributeLr.
 */
function createRedistributeOptimizer() {
  const lr = state.params.redistributeLr;
  if (state.params.optimizerType === 'adam') {
    return new OptimizerAdam(lr);
  }
  if (state.params.optimizerType === 'qqn') {
    return new OptimizerQQN(lr);
  }
  return new OptimizerLbfgs(lr);
}
/**
 * Get the current knot points as a plain JS array [[x,y,z],...].
 * Used by the WebXR renderer to mirror the live optimization state.
 */
function getPointsArray() {
  if (!state.points) return [];
  const data = state.points.dataSync();
  const n = state.params.n;
  const out = [];
  for (let i = 0; i < n; i++) {
    out.push([data[i * 3], data[i * 3 + 1], data[i * 3 + 2]]);
  }
  return out;
}
/**
 * Initialize WebXR support and wire up the Enter VR button.
 */
async function setupXR() {
  if (!els.btnEnterVr) return;
  state.xr = new KnotXR({
    getPoints: getPointsArray,
    getShowEdges: () => state.showEdges,
    getSolidView: () => state.solidView,
    onSessionStart: () => {
      els.btnEnterVr.textContent = 'Exit VR';
      els.btnEnterVr.classList.add('btn-primary');
      els.btnEnterVr.classList.remove('btn-secondary');
      if (els.vrStatus)
        els.vrStatus.textContent = 'In VR session. Remove headset or press Exit VR to return.';
    },
    onSessionEnd: () => {
      els.btnEnterVr.textContent = 'Enter VR';
      els.btnEnterVr.classList.remove('btn-primary');
      els.btnEnterVr.classList.add('btn-secondary');
      if (els.vrStatus)
        els.vrStatus.textContent = 'VR ready. Click "Enter VR" with a headset connected.';
    },
  });
  const supported = await state.xr.checkSupport();
  if (supported) {
    els.btnEnterVr.disabled = false;
    if (els.vrStatus)
      els.vrStatus.textContent = 'VR ready. Click "Enter VR" with a headset connected.';
  } else {
    els.btnEnterVr.disabled = true;
    if (els.vrStatus)
      els.vrStatus.textContent = 'WebXR immersive-vr not supported in this browser/device.';
  }
  els.btnEnterVr.addEventListener('click', async () => {
    if (!state.xr) return;
    try {
      if (state.xr.session) {
        await state.xr.exit();
      } else {
        await state.xr.enter();
      }
    } catch (err) {
      console.error('XR error:', err);
      if (els.vrStatus) els.vrStatus.textContent = 'VR error: ' + err.message;
    }
  });
}
/**
 * Optimize rotation for metric properties
 */
async function optimizeRotation(target) {
  if (!state.points) return;
  // Ensure Minkowski mode
  let mode = state.params.metricMode;
  if (!mode.startsWith('minkowski')) {
    mode = 'minkowski';
    state.params.metricMode = mode;
    els.metricSelect.value = mode;
    els.grpC.style.display = 'flex';
  }
  const angles = tf.variable(tf.tensor1d([0, 0, 0]));
  const optimizer = tf.train.adam(0.05);
  const initialPoints = state.points.clone();
  els.loading.classList.remove('hidden');
  const loadingText = els.loading.querySelector('div:last-child');
  const originalText = loadingText.textContent;
  loadingText.textContent = `Optimizing Rotation (${target})...`;
  // Helper to build rotation matrix
  const getRotationMatrix = (rx, ry, rz) => {
    const c = tf.cos,
      s = tf.sin;
    const cx = c(rx),
      sx = s(rx);
    const cy = c(ry),
      sy = s(ry);
    const cz = c(rz),
      sz = s(rz);
    const r00 = tf.mul(cz, cy);
    const r01 = tf.sub(tf.mul(cz, tf.mul(sy, sx)), tf.mul(sz, cx));
    const r02 = tf.add(tf.mul(cz, tf.mul(sy, cx)), tf.mul(sz, sx));
    const r10 = tf.mul(sz, cy);
    const r11 = tf.add(tf.mul(sz, tf.mul(sy, sx)), tf.mul(cz, cx));
    const r12 = tf.sub(tf.mul(sz, tf.mul(sy, cx)), tf.mul(cz, sx));
    const r20 = tf.neg(sy);
    const r21 = tf.mul(cy, sx);
    const r22 = tf.mul(cy, cx);
    return tf.stack([
      tf.stack([r00, r01, r02]),
      tf.stack([r10, r11, r12]),
      tf.stack([r20, r21, r22]),
    ]);
  };
  for (let i = 0; i < 100; i++) {
    optimizer.minimize(() => {
      const [rx, ry, rz] = angles.unstack();
      const R = getRotationMatrix(rx, ry, rz);
      const rotatedPoints = tf.matMul(initialPoints, R);
      let spatial, temporal;
      if (mode === 'minkowski-x') {
        temporal = rotatedPoints.slice([0, 0], [-1, 1]);
        spatial = rotatedPoints.slice([0, 1], [-1, 2]);
      } else if (mode === 'minkowski-y') {
        const x = rotatedPoints.slice([0, 0], [-1, 1]);
        const z = rotatedPoints.slice([0, 2], [-1, 1]);
        temporal = rotatedPoints.slice([0, 1], [-1, 1]);
        spatial = tf.concat([x, z], 1);
      } else {
        spatial = rotatedPoints.slice([0, 0], [-1, 2]);
        temporal = rotatedPoints.slice([0, 2], [-1, 1]);
      }
      // Calculate pairwise distances
      const rS = tf.sum(tf.square(spatial), 1, true);
      const drSq = tf.add(
        tf.sub(rS, tf.mul(2, tf.matMul(spatial, spatial, false, true))),
        tf.transpose(rS)
      );
      const dr = tf.sqrt(tf.maximum(drSq, 1e-6));
      const rT = tf.square(temporal);
      const dtSq = tf.add(
        tf.sub(rT, tf.mul(2, tf.matMul(temporal, temporal, false, true))),
        tf.transpose(rT)
      );
      const dt = tf.sqrt(tf.maximum(dtSq, 1e-6));
      const n = initialPoints.shape[0];
      const mask = tf.sub(tf.ones([n, n]), tf.eye(n));
      if (target === 'timelike') {
        // Minimize c_req = dr/dt -> Maximize timelikeness
        // Use dr / (dt + eps)
        const ratio = tf.div(dr, tf.add(dt, 1e-4));
        return tf.mean(tf.mul(ratio, mask));
      } else if (target === 'lightlike') {
        // Minimize |dr - dt| -> Maximize lightlikeness
        const diff = tf.abs(tf.sub(dr, dt));
        return tf.mean(tf.mul(diff, mask));
      } else {
        // Maximize c_req = dr/dt -> Maximize spacelikeness
        // Minimize dt / (dr + eps)
        const ratio = tf.div(dt, tf.add(dr, 1e-4));
        return tf.mean(tf.mul(ratio, mask));
      }
    });
    if (i % 10 === 0) await tf.nextFrame();
  }
  // Apply final rotation
  tf.tidy(() => {
    const [rx, ry, rz] = angles.unstack();
    const R = getRotationMatrix(rx, ry, rz);
    const finalPoints = tf.matMul(initialPoints, R);
    state.points.assign(finalPoints);
  });
  // Cleanup
  angles.dispose();
  initialPoints.dispose();
  optimizer.dispose();
  loadingText.textContent = originalText;
  els.loading.classList.add('hidden');
  updateDistanceMatrix();
}

// --- Spline Generation ---

/**
 * Catmull-Rom spline interpolation
 */
function catmullRom(p0, p1, p2, p3, t) {
  const t2 = t * t;
  const t3 = t2 * t;

  const v0 = (p2[0] - p0[0]) * 0.5;
  const v1 = (p3[0] - p1[0]) * 0.5;
  const v0y = (p2[1] - p0[1]) * 0.5;
  const v1y = (p3[1] - p1[1]) * 0.5;
  const v0z = (p2[2] - p0[2]) * 0.5;
  const v1z = (p3[2] - p1[2]) * 0.5;

  const x =
    (2 * p1[0] - 2 * p2[0] + v0 + v1) * t3 +
    (-3 * p1[0] + 3 * p2[0] - 2 * v0 - v1) * t2 +
    v0 * t +
    p1[0];
  const y =
    (2 * p1[1] - 2 * p2[1] + v0y + v1y) * t3 +
    (-3 * p1[1] + 3 * p2[1] - 2 * v0y - v1y) * t2 +
    v0y * t +
    p1[1];
  const z =
    (2 * p1[2] - 2 * p2[2] + v0z + v1z) * t3 +
    (-3 * p1[2] + 3 * p2[2] - 2 * v0z - v1z) * t2 +
    v0z * t +
    p1[2];

  return [x, y, z];
}

/**
 * Generate random control points within unit sphere
 */
function generateRandomControlPoints(numPoints) {
  const points = [];
  for (let i = 0; i < numPoints; i++) {
    // Random point in unit sphere using rejection sampling
    let x, y, z;
    do {
      x = Math.random() * 2 - 1;
      y = Math.random() * 2 - 1;
      z = Math.random() * 2 - 1;
    } while (x * x + y * y + z * z > 1);

    // Scale to 0.7 radius for better visualization
    const scale = 0.7;
    points.push([x * scale, y * scale, z * scale]);
  }
  return points;
}

/**
 * Generate points along a closed spline
 */
function generateSplinePoints(controlPoints, numSamples) {
  const n = controlPoints.length;
  const points = [];

  for (let i = 0; i < numSamples; i++) {
    const t = i / numSamples;
    const segment = Math.floor(t * n);
    const localT = t * n - segment;

    const p0 = controlPoints[(segment - 1 + n) % n];
    const p1 = controlPoints[segment % n];
    const p2 = controlPoints[(segment + 1) % n];
    const p3 = controlPoints[(segment + 2) % n];

    const point = catmullRom(p0, p1, p2, p3, localT);
    points.push(point);
  }

  return points;
}

/**
 * Generate trefoil knot parametrically
 */
function generateTrefoil(numPoints) {
  const points = [];
  for (let i = 0; i < numPoints; i++) {
    const t = (i / numPoints) * 2 * Math.PI;
    const x = Math.sin(t) + 2 * Math.sin(2 * t);
    const y = Math.cos(t) - 2 * Math.cos(2 * t);
    const z = -Math.sin(3 * t);
    // Normalize to fit in unit sphere
    const scale = 0.25;
    points.push([x * scale, y * scale, z * scale]);
  }
  return points;
}

/**
 * Generate figure-eight knot parametrically
 */
function generateFigure8(numPoints) {
  const points = [];
  for (let i = 0; i < numPoints; i++) {
    const t = (i / numPoints) * 2 * Math.PI;
    const x = (2 + Math.cos(2 * t)) * Math.cos(3 * t);
    const y = (2 + Math.cos(2 * t)) * Math.sin(3 * t);
    const z = Math.sin(4 * t);
    const scale = 0.2;
    points.push([x * scale, y * scale, z * scale]);
  }
  return points;
}

/**
 * Generate cinquefoil knot parametrically
 */
function generateCinquefoil(numPoints) {
  const points = [];
  for (let i = 0; i < numPoints; i++) {
    const t = (i / numPoints) * 2 * Math.PI;
    const x = Math.cos(t) * (2 - Math.cos((2 * t) / 5));
    const y = Math.sin(t) * (2 - Math.cos((2 * t) / 5));
    const z = -Math.sin((2 * t) / 5);
    const scale = 0.35;
    points.push([x * scale, y * scale, z * scale]);
  }
  return points;
}

/**
 * Generate unknot (circle)
 */
function generateUnknot(numPoints) {
  const points = [];
  for (let i = 0; i < numPoints; i++) {
    const t = (i / numPoints) * 2 * Math.PI;
    const x = Math.cos(t) * 0.6;
    const y = Math.sin(t) * 0.6;
    const z = 0;
    points.push([x, y, z]);
  }
  return points;
}

/**
 * Randomly redistribute points along the current spline curve
 */
function redistributePoints() {
  if (!state.points) return;
  const controlPoints = state.points.arraySync();
  const n = controlPoints.length;
  const newPoints = [];
  // Generate random sorted parameters to preserve topology
  const tValues = Array.from({ length: n }, () => Math.random()).sort((a, b) => a - b);
  for (let i = 0; i < n; i++) {
    const t = tValues[i];
    const segment = Math.floor(t * n);
    const localT = t * n - segment;
    const p0 = controlPoints[(segment - 1 + n) % n];
    const p1 = controlPoints[segment % n];
    const p2 = controlPoints[(segment + 1) % n];
    const p3 = controlPoints[(segment + 2) % n];
    newPoints.push(catmullRom(p0, p1, p2, p3, localT));
  }
  state.points.dispose();
  state.points = tf.variable(tf.tensor2d(newPoints));
  // Reset optimizer to clear history
  state.optimizer = createOptimizer();
  if (state.arcLengthVar) {
    state.arcLengthVar.dispose();
    state.arcLengthVar = null;
  }
  state.redistributeOptimizer = createRedistributeOptimizer();
  state.arcLengthTable = null;
  updateDistanceMatrix();
}
/**
 * Initialize knot points based on selected type
 */
function initializeKnot() {
  let pointsArray;

  switch (state.params.knotType) {
    case 'trefoil':
      pointsArray = generateTrefoil(state.params.n);
      break;
    case 'figure8':
      pointsArray = generateFigure8(state.params.n);
      break;
    case 'cinquefoil':
      pointsArray = generateCinquefoil(state.params.n);
      break;
    case 'unknot':
      pointsArray = generateUnknot(state.params.n);
      break;
    case 'random':
    default:
      const controlPoints = generateRandomControlPoints(state.params.controlPoints);
      pointsArray = generateSplinePoints(controlPoints, state.params.n);
      break;
  }

  // Convert to tensor
  if (state.points) state.points.dispose();
  state.points = tf.variable(tf.tensor2d(pointsArray));

  // Reset optimizer
  state.optimizer = createOptimizer();
  // Reset redistribution optimizer + arc-length variable
  if (state.arcLengthVar) {
    state.arcLengthVar.dispose();
    state.arcLengthVar = null;
  }
  state.redistributeOptimizer = createRedistributeOptimizer();
  state.arcLengthTable = null;
  state.step = 0;
}

// --- Physics Simulation ---

/**
 * Compute pairwise distance matrix
 */
function computeDistanceMatrix(points) {
  return tf.tidy(() => {
    const r = tf.sum(tf.square(points), 1, true);
    const distSq = tf.add(
      tf.sub(r, tf.mul(2, tf.matMul(points, points, false, true))),
      tf.transpose(r)
    );
    return tf.sqrt(tf.maximum(distSq, 1e-10));
  });
}
/**
 * Compute Minkowski components (dr, dt) where z is time
 */
function computeMinkowskiComponents(points) {
  return tf.tidy(() => {
    let spatial, temporal;

    if (state.params.metricMode === 'minkowski-x') {
      temporal = points.slice([0, 0], [-1, 1]);
      spatial = points.slice([0, 1], [-1, 2]);
    } else if (state.params.metricMode === 'minkowski-y') {
      const x = points.slice([0, 0], [-1, 1]);
      const z = points.slice([0, 2], [-1, 1]);
      temporal = points.slice([0, 1], [-1, 1]);
      spatial = tf.concat([x, z], 1);
    } else {
      // Default (Z=Time)
      spatial = points.slice([0, 0], [-1, 2]);
      temporal = points.slice([0, 2], [-1, 1]);
    }
    // Scale temporal component by c
    const c = state.params.c;
    const scaledTemporal = tf.mul(temporal, c);

    // Spatial distance squared
    const rS = tf.sum(tf.square(spatial), 1, true);
    const drSq = tf.add(
      tf.sub(rS, tf.mul(2, tf.matMul(spatial, spatial, false, true))),
      tf.transpose(rS)
    );
    const dr = tf.sqrt(tf.maximum(drSq, 0));
    // Temporal distance squared
    const rT = tf.square(scaledTemporal);
    const dtSq = tf.add(
      tf.sub(rT, tf.mul(2, tf.matMul(scaledTemporal, scaledTemporal, false, true))),
      tf.transpose(rT)
    );
    const dt = tf.sqrt(tf.maximum(dtSq, 0));

    // Signed time difference (t_i - t_j)
    const tDiff = tf.sub(temporal, tf.transpose(temporal));
    return [dr, dt, tDiff];
  });
}

/**
 * Compute edge constraint loss (adjacent points should have target length)
 */
function computeEdgeLoss(points, targetLength, stiffness) {
  return tf.tidy(() => {
    const n = points.shape[0];

    // Get adjacent pairs (circular)
    const p1 = points;
    const p2 = tf.concat([points.slice([1], [-1]), points.slice([0], [1])], 0);

    // Compute edge lengths
    const diff = tf.sub(p1, p2);
    const lengths = tf.norm(diff, 'euclidean', 1);

    // Squared difference from target
    const deviation = tf.sub(lengths, targetLength);
    return tf.mul(stiffness, tf.mean(tf.square(deviation)));
  });
}

/**
 * Compute repulsion loss (non-adjacent points repel)
 */
function computeRepulsionLoss(points, strength, cutoff) {
  return tf.tidy(() => {
    const n = points.shape[0];

    // Compute all pairwise distances
    const r = tf.sum(tf.square(points), 1, true);
    const distSq = tf.add(
      tf.sub(r, tf.mul(2, tf.matMul(points, points, false, true))),
      tf.transpose(r)
    );

    // Create adjacency mask (1 for non-adjacent, 0 for adjacent and self)
    const indices = tf.range(0, n, 1, 'int32');
    const i = tf.expandDims(indices, 1);
    const j = tf.expandDims(indices, 0);

    // Adjacent if |i - j| <= 1 or |i - j| >= n-1 (circular)
    const diff = tf.abs(tf.sub(i, j));
    const isAdjacent = tf.logicalOr(tf.lessEqual(diff, 1), tf.greaterEqual(diff, n - 1));
    const mask = tf.cast(tf.logicalNot(isAdjacent), 'float32');

    // Soft cutoff using sigmoid
    const cutoffSq = cutoff * cutoff;
    const softMask = tf.sigmoid(tf.mul(10, tf.sub(cutoffSq, distSq)));

    // Repulsion potential: 1 / (distSq + epsilon)
    const potential = tf.div(1.0, tf.add(distSq, 0.001));

    // Apply masks
    const maskedPotential = tf.mul(tf.mul(potential, mask), softMask);

    return tf.mul(strength, tf.mean(maskedPotential));
  });
}
/**
 * Compute Shannon entropy of the pairwise-distance distribution.
 *
 * We build a soft histogram of all non-adjacent pairwise distances using a
 * Gaussian (RBF) soft-assignment to a set of fixed bin centers, giving a
 * differentiable probability distribution p_k over distance bins. The Shannon
 * entropy H = -Σ p_k log p_k measures the *diversity* of distances present in
 * the knot (an Erdős-distance style reframing for a 1-manifold in 3-space).
 *
 * Returns { entropyLoss, entropy } where:
 *   entropy     = the (positive) Shannon entropy, for reporting
 *   entropyLoss = -weight * entropy  (so minimizing increases diversity)
 */
function computeEntropyLoss(points, weight, tau) {
  return tf.tidy(() => {
    const n = points.shape[0];
    // Pairwise distances
    const r = tf.sum(tf.square(points), 1, true);
    const distSq = tf.add(
      tf.sub(r, tf.mul(2, tf.matMul(points, points, false, true))),
      tf.transpose(r)
    );
    const dist = tf.sqrt(tf.maximum(distSq, 1e-10));
    // Mask out self and adjacent pairs (those are fixed by the edge constraint)
    const indices = tf.range(0, n, 1, 'int32');
    const i = tf.expandDims(indices, 1);
    const j = tf.expandDims(indices, 0);
    const idiff = tf.abs(tf.sub(i, j));
    const isAdjacent = tf.logicalOr(tf.lessEqual(idiff, 1), tf.greaterEqual(idiff, n - 1));
    const mask = tf.cast(tf.logicalNot(isAdjacent), 'float32');
    // Flatten distances and weights
    const flatDist = tf.reshape(dist, [-1]); // [n*n]
    const flatMask = tf.reshape(mask, [-1]); // [n*n]
    // Fixed bin centers spanning [0, 2] (knots are normalized into a unit-ish ball)
    const numBins = 32;
    const maxRange = 2.0;
    const centers = tf.linspace(0, maxRange, numBins); // [numBins]
    // Soft assignment: kernel[pair, bin] = exp(-((d - c)^2) / (2*tau^2))
    const dExp = tf.expandDims(flatDist, 1); // [P, 1]
    const cExp = tf.expandDims(centers, 0); // [1, numBins]
    const diff = tf.sub(dExp, cExp);
    const kernel = tf.exp(tf.div(tf.neg(tf.square(diff)), 2 * tau * tau)); // [P, numBins]
    // Weight each pair by the mask, then sum into bins
    const wExp = tf.expandDims(flatMask, 1); // [P, 1]
    const weighted = tf.mul(kernel, wExp); // [P, numBins]
    const binCounts = tf.sum(weighted, 0); // [numBins]
    // Normalize to a probability distribution
    const total = tf.add(tf.sum(binCounts), 1e-10);
    const probs = tf.div(binCounts, total);
    // Shannon entropy H = -Σ p log p
    const logProbs = tf.log(tf.add(probs, 1e-12));
    const entropy = tf.neg(tf.sum(tf.mul(probs, logProbs)));
    // Loss is negative entropy scaled by weight (minimizing => maximizing diversity)
    const entropyLoss = tf.mul(tf.neg(entropy), weight);
    return { entropyLoss, entropy };
  });
}

/**
 * Sample a point on a closed Catmull-Rom spline defined by controlPoints at parameter t in [0,1)
 */
function sampleSpline(controlPoints, t) {
  const n = controlPoints.length;
  const scaled = t * n;
  const segment = Math.floor(scaled);
  const localT = scaled - segment;
  const p0 = controlPoints[(segment - 1 + n) % n];
  const p1 = controlPoints[segment % n];
  const p2 = controlPoints[(segment + 1) % n];
  const p3 = controlPoints[(segment + 2) % n];
  return catmullRom(p0, p1, p2, p3, localT);
}
/**
 * Compute cumulative arc length along the closed spline (sampled finely)
 * Returns {samples: [[x,y,z],...], cumLen: [...], totalLen}
 */
function buildArcLengthTable(controlPoints, resolution) {
  const samples = [];
  const cumLen = [0];
  const tValues = [0];
  let prev = sampleSpline(controlPoints, 0);
  samples.push(prev);
  for (let i = 1; i <= resolution; i++) {
    const t = i / resolution;
    const p = sampleSpline(controlPoints, t % 1);
    samples.push(p);
    tValues.push(t);
    const d = Math.sqrt((p[0] - prev[0]) ** 2 + (p[1] - prev[1]) ** 2 + (p[2] - prev[2]) ** 2);
    cumLen.push(cumLen[i - 1] + d);
    prev = p;
  }
  return { samples, cumLen, tValues, totalLen: cumLen[cumLen.length - 1] };
}
/**
 * Find the point on the frozen spline at arc-length distance `s` from start
 * Uses 2nd-order (quadratic, 3-point) interpolation of the parameter t along
 * the arc-length table, then evaluates the underlying cubic Catmull-Rom spline
 * at that t. This makes the *position* come from the cubic spline rather than
 * from chord interpolation between samples.
 */
function pointAtArcLength(table, s, controlPoints) {
  const { samples, cumLen, totalLen, tValues } = table;
  // wrap
  s = ((s % totalLen) + totalLen) % totalLen;
  // Binary search for bracketing samples
  let lo = 0,
    hi = cumLen.length - 1;
  while (lo < hi - 1) {
    const mid = (lo + hi) >> 1;
    if (cumLen[mid] <= s) lo = mid;
    else hi = mid;
  }
  // If we have a controlPoints curve, use quadratic interpolation of t
  // then evaluate the cubic spline at that t.
  if (controlPoints && tValues) {
    // Pick 3 points around (lo, hi) for quadratic interp.
    // Prefer (lo-1, lo, hi) or (lo, hi, hi+1) depending on which side s lies.
    const N = cumLen.length;
    let i0, i1, i2;
    const midpoint = (cumLen[lo] + cumLen[hi]) * 0.5;
    if (s < midpoint && lo > 0) {
      i0 = lo - 1;
      i1 = lo;
      i2 = hi;
    } else if (hi < N - 1) {
      i0 = lo;
      i1 = hi;
      i2 = hi + 1;
    } else if (lo > 0) {
      i0 = lo - 1;
      i1 = lo;
      i2 = hi;
    } else {
      // Fall back to linear
      const segLen = cumLen[hi] - cumLen[lo];
      const frac = segLen > 1e-9 ? (s - cumLen[lo]) / segLen : 0;
      const tLin = tValues[lo] + (tValues[hi] - tValues[lo]) * frac;
      return sampleSpline(controlPoints, ((tLin % 1) + 1) % 1);
    }
    // Lagrange quadratic interpolation: t(s) through (cumLen[i_k], tValues[i_k])
    // Handle the seam: tValues are in [0,1), but near wrap they could jump.
    // Unwrap relative to t at i1.
    const tRef = tValues[i1];
    const unwrap = (t) => {
      let dt = t - tRef;
      if (dt > 0.5) dt -= 1;
      else if (dt < -0.5) dt += 1;
      return tRef + dt;
    };
    const x0 = cumLen[i0],
      x1 = cumLen[i1],
      x2 = cumLen[i2];
    const y0 = unwrap(tValues[i0]);
    const y1 = unwrap(tValues[i1]);
    const y2 = unwrap(tValues[i2]);
    const L0 = ((s - x1) * (s - x2)) / ((x0 - x1) * (x0 - x2) + 1e-20);
    const L1 = ((s - x0) * (s - x2)) / ((x1 - x0) * (x1 - x2) + 1e-20);
    const L2 = ((s - x0) * (s - x1)) / ((x2 - x0) * (x2 - x1) + 1e-20);
    let t = y0 * L0 + y1 * L1 + y2 * L2;
    t = ((t % 1) + 1) % 1;
    return sampleSpline(controlPoints, t);
  }
  // Fallback: linear interp between sampled positions
  const segLen = cumLen[hi] - cumLen[lo];
  const frac = segLen > 1e-9 ? (s - cumLen[lo]) / segLen : 0;
  const a = samples[lo],
    b = samples[hi];
  return [a[0] + (b[0] - a[0]) * frac, a[1] + (b[1] - a[1]) * frac, a[2] + (b[2] - a[2]) * frac];
}
/**
 * Step 2 of optimization: freeze the spline implied by current points,
 * project each point to its arc-length position on the frozen curve,
 * then take ONE gradient step of the optimizer on the arc-length parameters
 * to minimize the variance-of-gaps loss:
 *
 *     L(s) = mean_i ((s_{i+1} - s_i) - L/n)^2     (with circular wrap)
 *
 * Finally, resample the points at the updated arc-length positions using a
 * 2nd-order (quadratic-in-t, cubic-in-position) interpolant.
 */
function redistributeAlongFrozenSpline() {
  if (!state.points) return;
  const controlPoints = state.points.arraySync();
  const n = controlPoints.length;
  // Freeze spline: build arc-length lookup table
  const table = buildArcLengthTable(controlPoints, n * 8);
  state.arcLengthTable = table;
  const totalLen = table.totalLen;
  if (totalLen < 1e-6) return;
  // Project each point to its current arc-length position (nearest sample)
  const currentArc = new Array(n);
  for (let i = 0; i < n; i++) {
    const p = controlPoints[i];
    let bestIdx = 0,
      bestD = Infinity;
    for (let k = 0; k < table.samples.length; k++) {
      const s = table.samples[k];
      const d = (s[0] - p[0]) ** 2 + (s[1] - p[1]) ** 2 + (s[2] - p[2]) ** 2;
      if (d < bestD) {
        bestD = d;
        bestIdx = k;
      }
    }
    currentArc[i] = table.cumLen[bestIdx];
  }
  // Initialize (or reset) the arc-length tf.variable to the projected values.
  // Reset if size differs (n changed) or it doesn't exist yet.
  if (!state.arcLengthVar || state.arcLengthVar.shape[0] !== n) {
    if (state.arcLengthVar) state.arcLengthVar.dispose();
    state.arcLengthVar = tf.variable(tf.tensor1d(currentArc));
    state.redistributeOptimizer = createRedistributeOptimizer();
  } else {
    // Sync the variable to the projected positions each call,
    // because the underlying curve has changed since the last call.
    tf.tidy(() => {
      state.arcLengthVar.assign(tf.tensor1d(currentArc));
    });
  }
  // ONE gradient step on variance-of-gaps loss.
  // To handle the circular topology, we treat s as living on a circle of
  // circumference totalLen and compute wrapped neighbor differences.
  const L = totalLen;
  const targetGap = L / n;
  const lossFn = () => {
    // Wrapped gap: ((s_{i+1} - s_i + L/2) mod L) - L/2
    const sNext = tf.concat([
      state.arcLengthVar.slice([1], [n - 1]),
      state.arcLengthVar.slice([0], [1]),
    ]);
    const raw = tf.sub(sNext, state.arcLengthVar);
    // mod into (-L/2, L/2]
    const shifted = tf.add(raw, L / 2);
    const modded = tf.sub(shifted, tf.mul(tf.floor(tf.div(shifted, L)), L));
    const gaps = tf.sub(modded, L / 2);
    const dev = tf.sub(gaps, targetGap);
    return tf.mean(tf.square(dev));
  };
  try {
    const { grads } = state.redistributeOptimizer.computeGradients(lossFn);
    state.redistributeOptimizer.applyGradients(grads, lossFn);
  } catch (e) {
    // If the optimizer chokes (e.g. LBFGS on a degenerate problem), skip silently.
    console.warn('Redistribute optimizer step failed:', e);
  }
  // Read back updated arc-length positions
  const updatedArc = state.arcLengthVar.dataSync();
  // Resample points at new arc-length positions on the frozen spline,
  // using 2nd-order quadratic-in-t interpolation + cubic spline evaluation.
  const newPoints = new Array(n);
  for (let i = 0; i < n; i++) {
    newPoints[i] = pointAtArcLength(table, updatedArc[i], controlPoints);
  }
  // Write back into the variable in-place (preserve optimizer state where possible)
  tf.tidy(() => {
    state.points.assign(tf.tensor2d(newPoints));
  });
}
/**
 * Single optimization step
 */
function trainStep() {
  if (!state.points) return;

  tf.tidy(() => {
    const lossFunction = () => {
      const edgeLoss = computeEdgeLoss(
        state.points,
        state.params.targetEdgeLength,
        state.params.edgeStiffness
      );
      const repulsionLoss = computeRepulsionLoss(
        state.points,
        state.params.repulsionStrength,
        state.params.repulsionCutoff
      );
      let total = tf.add(edgeLoss, repulsionLoss);
      if (state.params.entropyWeight > 0) {
        const { entropyLoss } = computeEntropyLoss(
          state.points,
          state.params.entropyWeight,
          state.params.entropyTau
        );
        total = tf.add(total, entropyLoss);
      }
      return total;
    };

    const { value, grads } = state.optimizer.computeGradients(lossFunction);
    state.optimizer.applyGradients(grads, lossFunction);
  });

  // Step 2 (strict alternation): one gradient step of redistribution,
  // every `redistributeEvery` main steps.
  const every = Math.max(1, Math.floor(state.params.redistributeEvery));
  if (state.params.redistributeLr > 0 && state.step % every === 0) {
    redistributeAlongFrozenSpline();
  }
  // Recompute reported losses AFTER the redistribution step so the metrics
  // reflect what is actually drawn.
  tf.tidy(() => {
    const edgeLoss = computeEdgeLoss(
      state.points,
      state.params.targetEdgeLength,
      state.params.edgeStiffness
    );
    const repulsionLoss = computeRepulsionLoss(
      state.points,
      state.params.repulsionStrength,
      state.params.repulsionCutoff
    );
    const total = tf.add(edgeLoss, repulsionLoss);
    state.metrics.edgeLoss = edgeLoss.dataSync()[0];
    state.metrics.repulsionLoss = repulsionLoss.dataSync()[0];
    let totalVal = total.dataSync()[0];
    if (state.params.entropyWeight > 0) {
      const { entropyLoss, entropy } = computeEntropyLoss(
        state.points,
        state.params.entropyWeight,
        state.params.entropyTau
      );
      state.metrics.entropyLoss = entropyLoss.dataSync()[0];
      state.metrics.entropy = entropy.dataSync()[0];
      totalVal += state.metrics.entropyLoss;
    } else {
      // Still report the raw entropy for inspection
      const { entropy } = computeEntropyLoss(state.points, 0, state.params.entropyTau);
      state.metrics.entropy = entropy.dataSync()[0];
      state.metrics.entropyLoss = 0;
    }
    state.metrics.totalLoss = totalVal;
  });
  state.step++;

  // Update distance matrix
  updateDistanceMatrix();
}

/**
 * Update distance matrix for visualization
 */
function updateDistanceMatrix() {
  if (!state.points) return;

  tf.tidy(() => {
    const n = state.params.n;

    for (let i = 0; i < n; i++) {
      for (let j = 0; j < n; j++) {}
    }

    if (state.params.metricMode.startsWith('minkowski')) {
      const [dr, dt, tDiff] = computeMinkowskiComponents(state.points);
      const drData = dr.dataSync();
      const dtData = dt.dataSync();
      const tDiffData = tDiff.dataSync();
      state.minkowskiData = { dr: drData, dt: dtData, tDiff: tDiffData };

      // Use max spatial distance for scaling reference
      let max = 0;
      for (let i = 0; i < drData.length; i++) if (drData[i] > max) max = drData[i];
      state.metrics.maxDist = max;
      state.metrics.minDist = 0;
      state.metrics.avgDist = 0;
    } else {
      const distMatrix = computeDistanceMatrix(state.points);
      const data = distMatrix.dataSync();
      const n = state.params.n;

      state.distanceMatrix = new Float32Array(data);

      // Compute statistics (excluding diagonal)
      let min = Infinity,
        max = -Infinity,
        sum = 0,
        count = 0;
      for (let i = 0; i < n; i++) {
        for (let j = 0; j < n; j++) {
          if (i !== j) {
            const d = data[i * n + j];
            if (d < min) min = d;
            if (d > max) max = d;
            sum += d;
            count++;
          }
        }
      }

      state.metrics.minDist = min;
      state.metrics.maxDist = max;
      state.metrics.avgDist = sum / count;
    }
  });
}

// --- Visualization ---

function resizeCanvases() {
  const knotContainer = els.knotCanvas.parentElement;
  els.knotCanvas.width = knotContainer.clientWidth;
  els.knotCanvas.height = knotContainer.clientHeight;

  const matrixContainer = els.matrixCanvas.parentElement;
  els.matrixCanvas.width = matrixContainer.clientWidth;
  els.matrixCanvas.height = matrixContainer.clientHeight;
}

function project3D(x, y, z, width, height, scale) {
  const rotY = state.rotation.y;
  const rotX = state.rotation.x;

  // Rotate Y
  let x1 = x * Math.cos(rotY) - z * Math.sin(rotY);
  let z1 = x * Math.sin(rotY) + z * Math.cos(rotY);

  // Rotate X
  let y2 = y * Math.cos(rotX) - z1 * Math.sin(rotX);
  let z2 = y * Math.sin(rotX) + z1 * Math.cos(rotX);

  // Perspective
  const fov = 4;
  const dist = 4;
  const p = fov / (dist - z2);

  return {
    x: width * 0.5 + x1 * scale * p,
    y: height * 0.5 - y2 * scale * p,
    z: z2,
    scale: p,
  };
}

/**
 * Color mapping for distance matrix (viridis-like)
 */
function distanceToColor(d, minD, maxD) {
  const t = Math.max(0, Math.min(1, (d - minD) / (maxD - minD + 0.001)));

  // Viridis-inspired colormap
  const r = Math.floor(255 * (0.267 + t * (0.993 - 0.267)));
  const g = Math.floor(255 * (0.004 + t * 0.5 * (1 - t) * 4 + t * 0.906));
  const b = Math.floor(255 * (0.329 + (1 - t) * 0.5));

  return `rgb(${r}, ${g}, ${b})`;
}
function minkowskiToColor(dr, dt, tDiff) {
  const diff = dr - dt;
  const sum = dr + dt + 0.001;
  const intensity = Math.min(1, sum * 0.8);

  // Gaussian for lightcone
  const light = Math.exp(-diff * diff * 20);

  // Timelike factor (1 when diff < 0)
  const isTimelike = 1 / (1 + Math.exp(10 * diff));
  // Spacelike factor (1 when diff > 0)
  const isSpacelike = 1 / (1 + Math.exp(-10 * diff));

  // Future vs Past in timelike region
  // tDiff > 0 is Future (Red), tDiff < 0 is Past (Blue)
  const isFuture = 1 / (1 + Math.exp(-10 * tDiff));
  const isPast = 1 - isFuture;

  // Color mixing
  // Spacelike: Green
  // Timelike Future: Red
  // Timelike Past: Blue

  const r = Math.floor(255 * (isTimelike * isFuture + light * 0.5) * intensity);
  const g = Math.floor(255 * (isSpacelike * 0.8 + light * 0.5) * intensity);
  const b = Math.floor(255 * (isTimelike * isPast + isSpacelike * 0.2 + light * 0.5) * intensity);

  return `rgb(${Math.min(255, r)}, ${Math.min(255, g)}, ${Math.min(255, b)})`;
}
// --- Mesh Generation for Solid View & STL ---
const vec3 = {
  sub: (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]],
  add: (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]],
  cross: (a, b) => [
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
  ],
  normalize: (a) => {
    const l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    return l > 0 ? [a[0] / l, a[1] / l, a[2] / l] : [0, 0, 0];
  },
  dot: (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2],
  scale: (a, s) => [a[0] * s, a[1] * s, a[2] * s],
  len: (a) => Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]),
};
function generateTubeMesh(points, radius, segments) {
  const n = points.length;
  const frames = [];
  // Compute tangents
  const tangents = points.map((p, i) => {
    const next = points[(i + 1) % n];
    const prev = points[(i - 1 + n) % n];
    return vec3.normalize(vec3.sub(next, prev));
  });
  // Compute frames (Parallel Transport)
  let normal = vec3.cross(tangents[0], [0, 1, 0]);
  if (vec3.len(normal) < 0.001) normal = vec3.cross(tangents[0], [1, 0, 0]);
  normal = vec3.normalize(normal);
  let binormal = vec3.normalize(vec3.cross(tangents[0], normal));
  frames.push({ t: tangents[0], n: normal, b: binormal });
  for (let i = 1; i < n; i++) {
    const prevFrame = frames[i - 1];
    const t = tangents[i];
    // Project previous normal onto plane perpendicular to new tangent
    let n_new = vec3.sub(prevFrame.n, vec3.scale(t, vec3.dot(prevFrame.n, t)));
    n_new = vec3.normalize(n_new);
    const b_new = vec3.normalize(vec3.cross(t, n_new));
    frames.push({ t, n: n_new, b: b_new });
  }
  // Correct twist (distribute error)
  const lastFrame = frames[n - 1];
  const t0 = tangents[0];
  // Transport last frame to start
  let n_end = vec3.sub(lastFrame.n, vec3.scale(t0, vec3.dot(lastFrame.n, t0)));
  n_end = vec3.normalize(n_end);
  // Calculate angle between transported last normal and first normal
  let totalTwist = Math.atan2(vec3.dot(n_end, frames[0].b), vec3.dot(n_end, frames[0].n));
  // Generate vertices
  const meshVertices = []; // [ring][segment]
  for (let i = 0; i < n; i++) {
    const p = points[i];
    const frame = frames[i];
    const twist = -(totalTwist * (i / n)); // Distribute twist
    // Rotate frame basis by twist
    const c = Math.cos(twist);
    const s = Math.sin(twist);
    const N = vec3.add(vec3.scale(frame.n, c), vec3.scale(frame.b, -s));
    const B = vec3.add(vec3.scale(frame.n, s), vec3.scale(frame.b, c));
    const ring = [];
    for (let j = 0; j < segments; j++) {
      const theta = (j / segments) * 2 * Math.PI;
      const sin = Math.sin(theta);
      const cos = Math.cos(theta);
      // v = p + radius * (cos * N + sin * B)
      const offset = vec3.add(vec3.scale(N, cos), vec3.scale(B, sin));
      const v = vec3.add(p, vec3.scale(offset, radius));
      ring.push({ pos: v, normal: offset });
    }
    meshVertices.push(ring);
  }
  // Generate faces (quads)
  const faces = [];
  for (let i = 0; i < n; i++) {
    const nextI = (i + 1) % n;
    for (let j = 0; j < segments; j++) {
      const nextJ = (j + 1) % segments;
      const v0 = meshVertices[i][j];
      const v1 = meshVertices[nextI][j];
      const v2 = meshVertices[nextI][nextJ];
      const v3 = meshVertices[i][nextJ];
      faces.push([v0, v1, v2, v3]);
    }
  }
  return faces;
}
function exportSTL() {
  if (!state.points) return;
  const points = [];
  const data = state.points.dataSync();
  for (let i = 0; i < state.params.n; i++)
    points.push([data[i * 3], data[i * 3 + 1], data[i * 3 + 2]]);
  const faces = generateTubeMesh(points, 0.08, 16);
  let stl = 'solid knot\n';
  faces.forEach((quad) => {
    const tris = [
      [quad[0], quad[1], quad[2]],
      [quad[0], quad[2], quad[3]],
    ];
    tris.forEach((tri) => {
      const u = vec3.sub(tri[1].pos, tri[0].pos);
      const v = vec3.sub(tri[2].pos, tri[0].pos);
      const n = vec3.normalize(vec3.cross(u, v));
      stl += `facet normal ${n[0].toExponential()} ${n[1].toExponential()} ${n[2].toExponential()}\n`;
      stl += '  outer loop\n';
      tri.forEach(
        (vert) =>
          (stl += `    vertex ${vert.pos[0].toExponential()} ${vert.pos[1].toExponential()} ${vert.pos[2].toExponential()}\n`)
      );
      stl += '  endloop\nendfacet\n';
    });
  });
  stl += 'endsolid knot';
  const blob = new Blob([stl], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'knot.stl';
  a.click();
  URL.revokeObjectURL(url);
}

function drawKnot() {
  const w = els.knotCanvas.width;
  const h = els.knotCanvas.height;

  knotCtx.fillStyle = '#1a1d24';
  knotCtx.fillRect(0, 0, w, h);

  if (!state.points) return;

  const pointsArr = state.points.dataSync();
  const n = state.params.n;
  const scale = Math.min(w, h) * 0.4 * state.zoom;
  const pointsVec = [];
  for (let i = 0; i < n; i++)
    pointsVec.push([pointsArr[i * 3], pointsArr[i * 3 + 1], pointsArr[i * 3 + 2]]);
  if (state.solidView) {
    const faces = generateTubeMesh(pointsVec, 0.05, 8);
    const projectedFaces = faces.map((quad) => {
      const proj = quad.map((v) => project3D(v.pos[0], v.pos[1], v.pos[2], w, h, scale));
      const z = (proj[0].z + proj[1].z + proj[2].z + proj[3].z) / 4;
      const n0 = quad[0].normal;
      const rotY = state.rotation.y;
      const rotX = state.rotation.x;
      let nx = n0[0] * Math.cos(rotY) - n0[2] * Math.sin(rotY);
      let nz = n0[0] * Math.sin(rotY) + n0[2] * Math.cos(rotY);
      let ny = n0[1] * Math.cos(rotX) - nz * Math.sin(rotX);
      nz = n0[1] * Math.sin(rotX) + nz * Math.cos(rotX);
      const light = Math.max(0.1, nz * 0.5 + 0.5);
      return { verts: proj, z, light };
    });
    projectedFaces.sort((a, b) => a.z - b.z);
    projectedFaces.forEach((face) => {
      const l = Math.floor(face.light * 255);
      knotCtx.fillStyle = `rgb(${0}, ${Math.floor(l * 0.8)}, ${l})`;
      knotCtx.strokeStyle = `rgb(${0}, ${Math.floor(l * 0.8)}, ${l})`;
      knotCtx.lineWidth = 1;
      knotCtx.beginPath();
      knotCtx.moveTo(face.verts[0].x, face.verts[0].y);
      knotCtx.lineTo(face.verts[1].x, face.verts[1].y);
      knotCtx.lineTo(face.verts[2].x, face.verts[2].y);
      knotCtx.lineTo(face.verts[3].x, face.verts[3].y);
      knotCtx.closePath();
      knotCtx.fill();
      knotCtx.stroke();
    });
    return;
  }

  // Project all points
  const projected = [];
  for (let i = 0; i < n; i++) {
    const x = pointsArr[i * 3];
    const y = pointsArr[i * 3 + 1];
    const z = pointsArr[i * 3 + 2];
    projected.push({ ...project3D(x, y, z, w, h, scale), idx: i });
  }

  // Draw edges (sorted by depth for proper occlusion)
  if (state.showEdges) {
    const edges = [];
    for (let i = 0; i < n; i++) {
      const j = (i + 1) % n;
      const p1 = projected[i];
      const p2 = projected[j];
      const avgZ = (p1.z + p2.z) / 2;
      edges.push({ p1, p2, z: avgZ });
    }
    edges.sort((a, b) => a.z - b.z);

    edges.forEach((edge) => {
      const alpha = 0.3 + (edge.z + 1) * 0.35;
      knotCtx.strokeStyle = `rgba(0, 210, 255, ${alpha})`;
      knotCtx.lineWidth = 2 * edge.p1.scale;
      knotCtx.beginPath();
      knotCtx.moveTo(edge.p1.x, edge.p1.y);
      knotCtx.lineTo(edge.p2.x, edge.p2.y);
      knotCtx.stroke();
    });
  }

  // Sort points by depth
  projected.sort((a, b) => a.z - b.z);

  // Draw points
  projected.forEach((p) => {
    const alpha = 0.4 + (p.z + 1) * 0.3;
    const size = 4 * p.scale;

    // Color based on position along curve
    const t = p.idx / n;
    const r = Math.floor(255 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2)));
    const g = Math.floor(255 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2 + (Math.PI * 2) / 3)));
    const b = Math.floor(255 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2 + (Math.PI * 4) / 3)));

    knotCtx.fillStyle = `rgba(${r}, ${g}, ${b}, ${alpha})`;
    knotCtx.beginPath();
    knotCtx.arc(p.x, p.y, size, 0, Math.PI * 2);
    knotCtx.fill();

    // Glow for front points
    if (p.z > 0) {
      knotCtx.shadowColor = `rgba(${r}, ${g}, ${b}, 0.5)`;
      knotCtx.shadowBlur = 10;
      knotCtx.fill();
      knotCtx.shadowBlur = 0;
    }
  });
  // Highlight hovered pair from matrix
  if (state.hoveredPair) {
    const { i, j } = state.hoveredPair;
    const p1 = projected.find((p) => p.idx === i);
    const p2 = projected.find((p) => p.idx === j);
    if (p1 && p2) {
      // Draw connecting line
      knotCtx.strokeStyle = '#ffffff';
      knotCtx.lineWidth = 2;
      knotCtx.setLineDash([4, 4]);
      knotCtx.beginPath();
      knotCtx.moveTo(p1.x, p1.y);
      knotCtx.lineTo(p2.x, p2.y);
      knotCtx.stroke();
      knotCtx.setLineDash([]);
      // Highlight endpoints
      [p1, p2].forEach((p) => {
        knotCtx.fillStyle = '#ffffff';
        knotCtx.beginPath();
        knotCtx.arc(p.x, p.y, 6 * p.scale, 0, Math.PI * 2);
        knotCtx.fill();
        // Label
        knotCtx.fillStyle = '#ffffff';
        knotCtx.font = '12px JetBrains Mono';
        knotCtx.fillText(p.idx, p.x + 10, p.y - 10);
      });
    }
  }

  // Draw coordinate axes
  const axisLength = 0.3;
  const axes = [
    { dir: [axisLength, 0, 0], color: '#ff4444', label: 'X' },
    { dir: [0, axisLength, 0], color: '#44ff44', label: 'Y' },
    { dir: [0, 0, axisLength], color: '#4444ff', label: 'Z' },
  ];

  const origin = project3D(0, 0, 0, w, h, scale);
  axes.forEach((axis) => {
    const end = project3D(axis.dir[0], axis.dir[1], axis.dir[2], w, h, scale);
    knotCtx.strokeStyle = axis.color;
    knotCtx.lineWidth = 1;
    knotCtx.beginPath();
    knotCtx.moveTo(origin.x, origin.y);
    knotCtx.lineTo(end.x, end.y);
    knotCtx.stroke();

    knotCtx.fillStyle = axis.color;
    knotCtx.font = '10px JetBrains Mono';
    knotCtx.fillText(axis.label, end.x + 5, end.y);
  });
}

function drawDistanceMatrix() {
  const w = els.matrixCanvas.width;
  const h = els.matrixCanvas.height;

  matrixCtx.fillStyle = '#1a1d24';
  matrixCtx.fillRect(0, 0, w, h);

  if (state.params.metricMode === 'euclidean' && !state.distanceMatrix) return;
  if (state.params.metricMode.startsWith('minkowski') && !state.minkowskiData) return;

  const n = state.params.n;
  const margin = 40;
  const size = Math.min(w - margin * 2, h - margin * 2);
  const cellSize = size / n;
  const offsetX = (w - size) / 2;
  const offsetY = (h - size) / 2;

  const minD = state.metrics.minDist;
  const maxD = state.metrics.maxDist;

  // Draw matrix cells
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      let color;
      if (state.params.metricMode.startsWith('minkowski')) {
        const dr = state.minkowskiData.dr[i * n + j];
        const dt = state.minkowskiData.dt[i * n + j];
        const tDiff = state.minkowskiData.tDiff[i * n + j];
        color = minkowskiToColor(dr, dt, tDiff);
      } else {
        const d = state.distanceMatrix[i * n + j];
        color = distanceToColor(d, minD, maxD);
      }

      matrixCtx.fillStyle = color;
      matrixCtx.fillRect(
        offsetX + j * cellSize,
        offsetY + i * cellSize,
        cellSize + 0.5,
        cellSize + 0.5
      );
    }
  }
  // Highlight hovered cell
  if (state.hoveredPair) {
    const { i, j } = state.hoveredPair;
    // Crosshairs
    matrixCtx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    matrixCtx.lineWidth = 1;
    matrixCtx.beginPath();
    matrixCtx.moveTo(offsetX, offsetY + i * cellSize + cellSize / 2);
    matrixCtx.lineTo(offsetX + size, offsetY + i * cellSize + cellSize / 2);
    matrixCtx.moveTo(offsetX + j * cellSize + cellSize / 2, offsetY);
    matrixCtx.lineTo(offsetX + j * cellSize + cellSize / 2, offsetY + size);
    matrixCtx.stroke();
    // Cell highlight
    matrixCtx.strokeStyle = '#ffffff';
    matrixCtx.lineWidth = 2;
    matrixCtx.strokeRect(offsetX + j * cellSize, offsetY + i * cellSize, cellSize, cellSize);
  }

  // Draw diagonal line indicator
  matrixCtx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
  matrixCtx.lineWidth = 1;
  matrixCtx.setLineDash([4, 4]);
  matrixCtx.beginPath();
  matrixCtx.moveTo(offsetX, offsetY);
  matrixCtx.lineTo(offsetX + size, offsetY + size);
  matrixCtx.stroke();
  matrixCtx.setLineDash([]);

  // Draw axis labels
  matrixCtx.fillStyle = '#6b7280';
  matrixCtx.font = '10px JetBrains Mono';
  matrixCtx.textAlign = 'center';

  // X axis label
  matrixCtx.fillText('Point Index j', offsetX + size / 2, offsetY + size + 25);

  // Y axis label (rotated)
  matrixCtx.save();
  matrixCtx.translate(offsetX - 25, offsetY + size / 2);
  matrixCtx.rotate(-Math.PI / 2);
  matrixCtx.fillText('Point Index i', 0, 0);
  matrixCtx.restore();

  // Update colorbar

  if (state.params.metricMode.startsWith('minkowski')) {
    els.colorbarMax.textContent = 'Space';
    els.colorbarMid.textContent = 'Light';
    els.colorbarMin.textContent = 'Time';
    els.colorbarGrad.style.background = `linear-gradient(to bottom, 
                rgb(0, 200, 50), 
                rgb(255, 255, 255), 
                rgb(255, 0, 0), rgb(0, 0, 255))`;
  } else {
    els.colorbarMax.textContent = maxD.toFixed(2);
    els.colorbarMid.textContent = ((minD + maxD) / 2).toFixed(2);
    els.colorbarMin.textContent = minD.toFixed(2);

    // Create gradient for colorbar
    els.colorbarGrad.style.background = `linear-gradient(to bottom, 
                ${distanceToColor(maxD, minD, maxD)}, 
                ${distanceToColor((minD + maxD) / 2, minD, maxD)}, 
                ${distanceToColor(minD, minD, maxD)})`;
  }
}

function updateUI() {
  els.metricLoss.textContent = state.metrics.totalLoss.toFixed(5);
  els.metricEdge.textContent = state.metrics.edgeLoss.toFixed(5);
  els.metricRepel.textContent = state.metrics.repulsionLoss.toFixed(5);
  els.metricStep.textContent = state.step;
  if (els.metricEntropy) els.metricEntropy.textContent = state.metrics.entropy.toFixed(4);
  els.metricMinDist.textContent = state.metrics.minDist.toFixed(4);
  els.metricMaxDist.textContent = state.metrics.maxDist.toFixed(4);
  els.metricAvgDist.textContent = state.metrics.avgDist.toFixed(4);
}

function animate() {
  if (state.isTraining) {
    for (let i = 0; i < 3; i++) trainStep();
  }

  if (state.autoRotate && !state.isDragging) {
    state.rotation.y += 0.005;
  }

  drawKnot();
  drawDistanceMatrix();
  updateUI();

  state.animationId = requestAnimationFrame(animate);
}
function rotateKnot(dx, dy) {
  if (!state.points) return;
  tf.tidy(() => {
    const sensitivity = 0.01;
    const rotY = dx * sensitivity;
    const rotX = dy * sensitivity;
    // Rotate around Y axis
    const cY = Math.cos(rotY);
    const sY = Math.sin(rotY);
    const matY = tf.tensor2d([
      [cY, 0, -sY],
      [0, 1, 0],
      [sY, 0, cY],
    ]);
    // Rotate around X axis
    const cX = Math.cos(rotX);
    const sX = Math.sin(rotX);
    const matX = tf.tensor2d([
      [1, 0, 0],
      [0, cX, -sX],
      [0, sX, cX],
    ]);
    const rotMat = tf.matMul(matY, matX);
    const newPoints = tf.matMul(state.points, rotMat);
    state.points.assign(newPoints);
  });
  updateDistanceMatrix();
}
function alignViewToTime() {
  const mode = state.params.metricMode;
  state.autoRotate = false;
  els.chkAutoRotate.checked = false;
  if (mode === 'minkowski-x') {
    // Time is X. Look along X (X becomes depth)
    state.rotation.x = 0;
    state.rotation.y = -Math.PI / 2;
  } else if (mode === 'minkowski-y') {
    // Time is Y. Look along Y (Y becomes depth)
    state.rotation.x = Math.PI / 2;
    state.rotation.y = 0;
  } else {
    // Time is Z. Look along Z (Z is depth)
    state.rotation.x = 0;
    state.rotation.y = 0;
  }
}

// --- Event Handlers ---

function setupEventListeners() {
  els.knotSelect.addEventListener('change', (e) => {
    state.params.knotType = e.target.value;
    initializeKnot();
    updateDistanceMatrix();
  });
  els.metricSelect.addEventListener('change', (e) => {
    state.params.metricMode = e.target.value;
    if (state.params.metricMode.startsWith('minkowski')) {
      els.grpC.style.display = 'flex';
    } else {
      els.grpC.style.display = 'none';
    }
    updateDistanceMatrix();
  });

  // Slider/input pairs
  const setupSlider = (slider, input, param, transform = (v) => v) => {
    slider.addEventListener('input', (e) => {
      const val = transform(parseFloat(e.target.value));
      state.params[param] = val;
      input.value = val;
    });
    input.addEventListener('change', (e) => {
      const val = parseFloat(e.target.value);
      if (!isNaN(val)) {
        state.params[param] = val;
        slider.value = val;
      }
    });
  };

  setupSlider(els.nInput, els.valN, 'n', (v) => Math.floor(v));
  setupSlider(els.ctrlInput, els.valCtrl, 'controlPoints', (v) => Math.floor(v));
  setupSlider(els.edgeInput, els.valEdge, 'targetEdgeLength');
  setupSlider(els.stiffInput, els.valStiff, 'edgeStiffness');
  setupSlider(els.repelInput, els.valRepel, 'repulsionStrength');
  setupSlider(els.cutoffInput, els.valCutoff, 'repulsionCutoff');
  setupSlider(els.lrInput, els.valLr, 'lr');
  setupSlider(els.cInput, els.valC, 'c');
  if (els.redistInput && els.valRedist) {
    setupSlider(els.redistInput, els.valRedist, 'redistributeLr');
    els.redistInput.addEventListener('input', () => {
      if (
        state.redistributeOptimizer &&
        typeof state.redistributeOptimizer.setLearningRate === 'function'
      ) {
        state.redistributeOptimizer.setLearningRate(state.params.redistributeLr);
      } else {
        state.redistributeOptimizer = createRedistributeOptimizer();
      }
    });
  }
  if (els.redistItersInput && els.valRedistIters) {
    setupSlider(els.redistItersInput, els.valRedistIters, 'redistributeEvery', (v) =>
      Math.floor(v)
    );
  }
  if (els.entropyInput && els.valEntropy) {
    setupSlider(els.entropyInput, els.valEntropy, 'entropyWeight');
  }
  if (els.entropyTauInput && els.valEntropyTau) {
    setupSlider(els.entropyTauInput, els.valEntropyTau, 'entropyTau');
  }

  // N change requires reinitialization
  els.nInput.addEventListener('change', () => {
    if (!state.isTraining) initializeKnot();
    updateDistanceMatrix();
  });
  els.cInput.addEventListener('input', updateDistanceMatrix);
  els.valC.addEventListener('change', updateDistanceMatrix);
  els.valN.addEventListener('change', () => {
    if (!state.isTraining) initializeKnot();
    updateDistanceMatrix();
  });

  els.ctrlInput.addEventListener('change', () => {
    if (!state.isTraining && state.params.knotType === 'random') {
      initializeKnot();
      updateDistanceMatrix();
    }
  });
  els.optimizerSelect.addEventListener('change', (e) => {
    state.params.optimizerType = e.target.value;
    state.optimizer = createOptimizer();
    state.redistributeOptimizer = createRedistributeOptimizer();
  });

  els.lrInput.addEventListener('input', () => {
    if (state.optimizer && typeof state.optimizer.setLearningRate === 'function') {
      state.optimizer.setLearningRate(state.params.lr);
    } else {
      state.optimizer = createOptimizer();
    }
  });

  els.chkAutoRotate.addEventListener('change', (e) => {
    state.autoRotate = e.target.checked;
  });

  els.chkEdges.addEventListener('change', (e) => {
    state.showEdges = e.target.checked;
  });
  els.chkSolid.addEventListener('change', (e) => {
    state.solidView = e.target.checked;
  });
  els.btnExportStl.addEventListener('click', () => {
    exportSTL();
  });

  els.btnToggle.addEventListener('click', () => {
    state.isTraining = !state.isTraining;
    els.btnToggle.textContent = state.isTraining ? 'Stop' : 'Start';
    els.btnToggle.classList.toggle('btn-primary', !state.isTraining);
    els.btnToggle.classList.toggle('btn-danger', state.isTraining);
  });

  els.btnReset.addEventListener('click', () => {
    state.isTraining = false;
    els.btnToggle.textContent = 'Start';
    els.btnToggle.classList.add('btn-primary');
    els.btnToggle.classList.remove('btn-danger');
    initializeKnot();
    updateDistanceMatrix();
  });

  els.btnStep.addEventListener('click', () => {
    trainStep();
  });
  els.btnDistribute.addEventListener('click', () => {
    redistributePoints();
  });

  els.btnCopy.addEventListener('click', () => {
    if (!state.points) return;
    const data = state.points.arraySync();
    const text = JSON.stringify(data, null, 2);
    navigator.clipboard.writeText(text).then(() => {
      const original = els.btnCopy.textContent;
      els.btnCopy.textContent = 'Copied!';
      setTimeout(() => (els.btnCopy.textContent = original), 1500);
    });
  });
  els.btnOrbitKnot.addEventListener('click', () => {
    state.isOrbitingKnot = !state.isOrbitingKnot;
    els.btnOrbitKnot.classList.toggle('btn-primary', state.isOrbitingKnot);
    els.btnOrbitKnot.classList.toggle('btn-secondary', !state.isOrbitingKnot);
  });
  els.btnPaste.addEventListener('click', async () => {
    try {
      const text = await navigator.clipboard.readText();
      const data = JSON.parse(text);
      if (
        Array.isArray(data) &&
        data.length > 0 &&
        Array.isArray(data[0]) &&
        data[0].length === 3
      ) {
        if (state.points) state.points.dispose();
        state.points = tf.variable(tf.tensor2d(data));
        state.params.n = data.length;
        els.valN.value = data.length;
        els.nInput.value = data.length;
        state.optimizer = createOptimizer();
        if (state.arcLengthVar) {
          state.arcLengthVar.dispose();
          state.arcLengthVar = null;
        }
        state.redistributeOptimizer = createRedistributeOptimizer();
        state.arcLengthTable = null;
        state.step = 0;
        updateDistanceMatrix();
        const original = els.btnPaste.textContent;
        els.btnPaste.textContent = 'Pasted!';
        setTimeout(() => (els.btnPaste.textContent = original), 1500);
      } else {
        alert('Invalid knot data format');
      }
    } catch (err) {
      console.error(err);
      alert('Failed to paste: ' + err.message);
    }
  });
  els.btnOptTime.addEventListener('click', () => optimizeRotation('timelike'));
  els.btnOptSpace.addEventListener('click', () => optimizeRotation('spacelike'));
  els.btnOptLight.addEventListener('click', () => optimizeRotation('lightlike'));
  els.btnAlignTime.addEventListener('click', alignViewToTime);

  window.addEventListener('resize', resizeCanvases);

  // Mouse interaction for rotation
  let lastX = 0,
    lastY = 0;

  els.knotCanvas.addEventListener('mousedown', (e) => {
    state.isDragging = true;
    lastX = e.clientX;
    lastY = e.clientY;
  });

  window.addEventListener('mousemove', (e) => {
    if (state.isDragging) {
      const dx = e.clientX - lastX;
      const dy = e.clientY - lastY;

      if (state.isOrbitingKnot) {
        rotateKnot(dx, dy);
      } else {
        state.rotation.y += dx * 0.01;
        state.rotation.x += dy * 0.01;
      }

      lastX = e.clientX;
      lastY = e.clientY;
    }
  });

  window.addEventListener('mouseup', () => {
    state.isDragging = false;
  });

  els.knotCanvas.addEventListener(
    'wheel',
    (e) => {
      e.preventDefault();
      const delta = e.deltaY * -0.001;
      state.zoom = Math.max(0.3, Math.min(3.0, state.zoom + delta));
    },
    { passive: false }
  );
  // Matrix hover interaction
  els.matrixCanvas.addEventListener('mousemove', (e) => {
    const rect = els.matrixCanvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const w = els.matrixCanvas.width;
    const h = els.matrixCanvas.height;
    const n = state.params.n;
    const margin = 40;
    const size = Math.min(w - margin * 2, h - margin * 2);
    const cellSize = size / n;
    const offsetX = (w - size) / 2;
    const offsetY = (h - size) / 2;
    if (x >= offsetX && x <= offsetX + size && y >= offsetY && y <= offsetY + size) {
      const j = Math.floor((x - offsetX) / cellSize);
      const i = Math.floor((y - offsetY) / cellSize);
      if (i >= 0 && i < n && j >= 0 && j < n) {
        state.hoveredPair = { i, j };
      } else {
        state.hoveredPair = null;
      }
    } else {
      state.hoveredPair = null;
    }
  });
  els.matrixCanvas.addEventListener('mouseleave', () => {
    state.hoveredPair = null;
  });
}

async function init() {
  try {
    await tf.ready();
    els.loading.classList.add('hidden');
    setupEventListeners();
    resizeCanvases();
    initializeKnot();
    updateDistanceMatrix();
    setupXR();
    animate();
  } catch (err) {
    console.error(err);
    els.loading.innerHTML = `<div style="color:var(--danger)">Error: ${err.message}</div>`;
  }
}

init();
