import { sub, add, scale, dot, len, normalize } from './vector.js';

const GAMMA_CAP = 0.9999; // clamp v/c to avoid singular gamma

export function gamma(velocity, c) {
  const v2 = velocity.x ** 2 + velocity.y ** 2;
  let beta2 = v2 / (c * c);
  if (beta2 > GAMMA_CAP * GAMMA_CAP) beta2 = GAMMA_CAP * GAMMA_CAP;
  return 1 / Math.sqrt(1 - beta2);
}

// Softened Newtonian acceleration on a body at posA due to a mass at posB.
export function newtonianAccel(posA, posB, massB, params) {
  const { G, epsilon } = params;
  const dx = posB.x - posA.x;
  const dy = posB.y - posA.y;
  const r2 = dx * dx + dy * dy + epsilon * epsilon;
  const r = Math.sqrt(r2);
  // magnitude / r folds the normalization into the force scale.
  const m = (G * massB) / (r2 * r);
  return { x: dx * m, y: dy * m };
}

// Solve retarded time via fixed-point iteration, returning the past
// position (and velocity) of the source body as seen from observer position.
export function solveRetardedPosition(observerPos, sourceHistory, t, params) {
  const { c, hermite } = params;
  let tRet = t;
  let sourcePos = sourceHistory.interpolate(tRet, hermite);
  if (!sourcePos) return null;
  const invC = 1 / c;
  for (let i = 0; i < 6; i++) {
    const dx = sourcePos.x - observerPos.x;
    const dy = sourcePos.y - observerPos.y;
    const distance = Math.sqrt(dx * dx + dy * dy);
    const newTRet = t - distance * invC;
    if (Math.abs(newTRet - tRet) < 1e-9) {
      tRet = newTRet;
      sourcePos = sourceHistory.interpolate(newTRet, hermite);
      if (!sourcePos) return null;
      break;
    }
    tRet = newTRet;
    sourcePos = sourceHistory.interpolate(newTRet, hermite);
    if (!sourcePos) return null;
  }
  // Final combined fetch (single search) for consistent pos+vel at tRet.
  const state = sourceHistory.interpolateState(tRet, hermite);
  if (!state) return null;
  return { position: state.position, velocity: state.velocity, tRet };
}

// EIH-style velocity-dependent correction (qualitative, tunable by alpha).
// Adds a (v/c)^2 order term that produces perihelion precession.
function relativisticCorrection(posA, posB, velA, velB, massB, params) {
  const { G, c, epsilon, alpha } = params;
  const d = sub(posB, posA);
  const r2 = dot(d, d) + epsilon * epsilon;
  const r = Math.sqrt(r2);
  const n = scale(d, 1 / r);

  const vRel = sub(velA, velB);
  const v2 = dot(vRel, vRel);
  const vr = dot(vRel, n);

  // Simplified 1PN-like term: (G m / r^2) * (4 G m / r - v^2 + 4 vr^2) / c^2
  const gm = G * massB;
  const factor = ((gm / r2) * ((4 * gm) / r - v2 + 4 * vr * vr)) / (c * c);
  const along = scale(n, factor);

  // velocity-coupling term: 4 (G m / r^2) vr * vRel / c^2
  const couple = scale(vRel, (((4 * gm) / r2) * vr) / (c * c));

  return scale(add(along, couple), alpha);
}

// Full acceleration on bodyA due to bodyB, using retarded source position
// when c is finite, plus alpha-blended relativistic corrections.
export function computeAcceleration(bodyA, bodyB, t, params) {
  const { c, alpha } = params;

  let sourcePos = bodyB.position;
  let sourceVel = bodyB.velocity;

  if (isFinite(c) && bodyB.history.count > 1) {
    const ret = solveRetardedPosition(bodyA.position, bodyB.history, t, params);
    if (ret) {
      sourcePos = ret.position;
      // Use the velocity interpolated at the retarded time, not the
      // current velocity, so the velocity-dependent corrections are
      // evaluated consistently with the retarded position.
      if (ret.velocity) sourceVel = ret.velocity;
      // Cache for the renderer so it need not re-solve the retarded time.
      bodyA._retardedCache = ret.position;
    }
  }

  let accel = newtonianAccel(bodyA.position, sourcePos, bodyB.mass, params);

  if (alpha > 0 && isFinite(c)) {
    const corr = relativisticCorrection(
      bodyA.position,
      sourcePos,
      bodyA.velocity,
      sourceVel,
      bodyB.mass,
      params
    );
    accel = add(accel, corr);

    // Blend in gamma-scaled effective coupling.
    const g = gamma(bodyA.velocity, c);
    accel = scale(accel, 1 + alpha * (g - 1) * 0.5);
  }

  return accel;
}
