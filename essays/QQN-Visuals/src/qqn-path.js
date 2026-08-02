// The QQN quadratic path and its properties.
import { scale, add, dot, neg } from './mathlib.js';

// d(t) = t(1-t)(-∇f) + t^2 (-H∇f)
export function pathPoint(gradDir, oracleDir, t) {
  const a = t * (1 - t);
  const b = t * t;
  return [a * gradDir[0] + b * oracleDir[0], a * gradDir[1] + b * oracleDir[1]];
}

// d'(t) = (1-2t)(-∇f) + 2t(-H∇f). At t=0 => -∇f.
export function pathTangent(gradDir, oracleDir, t) {
  const a = 1 - 2 * t;
  const b = 2 * t;
  return [a * gradDir[0] + b * oracleDir[0], a * gradDir[1] + b * oracleDir[1]];
}

// Build a polyline (in world coords, offset from x) for rendering.
export function pathPolyline(x, gradDir, oracleDir, n = 60) {
  const pts = [];
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    const d = pathPoint(gradDir, oracleDir, t);
    pts.push([x[0] + d[0], x[1] + d[1]]);
  }
  return pts;
}

// The directional derivative at origin: <∇f, d'(0)> = -||∇f||^2
export function originDirectional(grad) {
  return -(grad[0] * grad[0] + grad[1] * grad[1]);
}

// Line-search along the path: return probes and accepted t.
export function armijoSearch(
  land,
  x,
  grad,
  gradDir,
  oracleDir,
  { c1 = 1e-4, shrink = 0.5, tStart = 1.0, maxIter = 20 } = {}
) {
  const f0 = land.f(x[0], x[1]);
  // phi'(0) = <grad, d'(0)> = <grad, gradDir> = -||grad||^2
  const phi0p = dot(grad, gradDir);
  const probes = [];
  let t = tStart;
  let accepted = null;
  for (let k = 0; k < maxIter; k++) {
    const d = pathPoint(gradDir, oracleDir, t);
    const p = [x[0] + d[0], x[1] + d[1]];
    const fp = land.f(p[0], p[1]);
    const armijoRhs = f0 + c1 * t * phi0p;
    probes.push({ t, f: fp, rhs: armijoRhs, ok: fp <= armijoRhs });
    if (fp <= armijoRhs) {
      accepted = t;
      break;
    }
    t *= shrink;
  }
  if (accepted === null) accepted = t;
  return { probes, accepted, f0, phi0p };
}

// One QQN iteration using exact-inverse-Hessian oracle.
export function qqnStep(land, x, oracleFn, opts = {}) {
  const { g, dir } = oracleFn(land, x);
  const gradDir = neg(g); // -∇f
  const oracleDir = dir; // -H∇f
  const { probes, accepted } = armijoSearch(land, x, g, gradDir, oracleDir, opts);
  const d = pathPoint(gradDir, oracleDir, accepted);
  return {
    next: [x[0] + d[0], x[1] + d[1]],
    gradDir,
    oracleDir,
    probes,
    t: accepted,
  };
}
