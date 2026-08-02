// Color-difference metrics.
//
//   euclidean  — raw Euclidean distance in whatever 3-vector is given.
//   deltaEOK   — Euclidean distance in OKLab (perceptual, cheap).
//   deltaE76   — CIE76 Euclidean distance in CIE Lab.
//   deltaE2000 — CIEDE2000 in CIE Lab.
//
// Inputs are plain coordinate objects for the relevant space; callers are
// responsible for supplying colors already in the correct space (use
// convert() from convert.js if needed). These functions have no import
// dependencies so they stay independently tree-shakeable.

const DEG2RAD = Math.PI / 180;
const RAD2DEG = 180 / Math.PI;

// Generic Euclidean distance over the numeric fields shared by both objects.
export function euclidean(a, b) {
  const keys = Object.keys(a).filter((k) => typeof a[k] === 'number' && typeof b[k] === 'number');
  let sum = 0;
  for (const k of keys) {
    const d = a[k] - b[k];
    sum += d * d;
  }
  return Math.sqrt(sum);
}

// OKLab colors: { L, a, b }.
export function deltaEOK(a, b) {
  const dL = a.L - b.L;
  const da = a.a - b.a;
  const db = a.b - b.b;
  return Math.sqrt(dL * dL + da * da + db * db);
}

// CIE Lab colors: { L, a, b } with L in 0..100.
export function deltaE76(a, b) {
  const dL = a.L - b.L;
  const da = a.a - b.a;
  const db = a.b - b.b;
  return Math.sqrt(dL * dL + da * da + db * db);
}

// CIEDE2000. Lab colors { L, a, b } (L in 0..100).
export function deltaE2000(lab1, lab2, { kL = 1, kC = 1, kH = 1 } = {}) {
  const { L: L1, a: a1, b: b1 } = lab1;
  const { L: L2, a: a2, b: b2 } = lab2;

  const C1 = Math.hypot(a1, b1);
  const C2 = Math.hypot(a2, b2);
  const Cbar = (C1 + C2) / 2;

  const Cbar7 = Math.pow(Cbar, 7);
  const G = 0.5 * (1 - Math.sqrt(Cbar7 / (Cbar7 + Math.pow(25, 7))));

  const a1p = (1 + G) * a1;
  const a2p = (1 + G) * a2;

  const C1p = Math.hypot(a1p, b1);
  const C2p = Math.hypot(a2p, b2);

  const hp = (bp, ap) => {
    if (bp === 0 && ap === 0) return 0;
    let h = Math.atan2(bp, ap) * RAD2DEG;
    if (h < 0) h += 360;
    return h;
  };
  const h1p = hp(b1, a1p);
  const h2p = hp(b2, a2p);

  const dLp = L2 - L1;
  const dCp = C2p - C1p;

  let dhp;
  if (C1p * C2p === 0) {
    dhp = 0;
  } else if (Math.abs(h2p - h1p) <= 180) {
    dhp = h2p - h1p;
  } else if (h2p - h1p > 180) {
    dhp = h2p - h1p - 360;
  } else {
    dhp = h2p - h1p + 360;
  }
  const dHp = 2 * Math.sqrt(C1p * C2p) * Math.sin((dhp * DEG2RAD) / 2);

  const Lbarp = (L1 + L2) / 2;
  const Cbarp = (C1p + C2p) / 2;

  let hbarp;
  if (C1p * C2p === 0) {
    hbarp = h1p + h2p;
  } else if (Math.abs(h1p - h2p) <= 180) {
    hbarp = (h1p + h2p) / 2;
  } else if (h1p + h2p < 360) {
    hbarp = (h1p + h2p + 360) / 2;
  } else {
    hbarp = (h1p + h2p - 360) / 2;
  }

  const T =
    1 -
    0.17 * Math.cos((hbarp - 30) * DEG2RAD) +
    0.24 * Math.cos(2 * hbarp * DEG2RAD) +
    0.32 * Math.cos((3 * hbarp + 6) * DEG2RAD) -
    0.2 * Math.cos((4 * hbarp - 63) * DEG2RAD);

  const dTheta = 30 * Math.exp(-Math.pow((hbarp - 275) / 25, 2));
  const Cbarp7 = Math.pow(Cbarp, 7);
  const Rc = 2 * Math.sqrt(Cbarp7 / (Cbarp7 + Math.pow(25, 7)));
  const Sl = 1 + (0.015 * Math.pow(Lbarp - 50, 2)) / Math.sqrt(20 + Math.pow(Lbarp - 50, 2));
  const Sc = 1 + 0.045 * Cbarp;
  const Sh = 1 + 0.015 * Cbarp * T;
  const Rt = -Math.sin(2 * dTheta * DEG2RAD) * Rc;

  const termL = dLp / (kL * Sl);
  const termC = dCp / (kC * Sc);
  const termH = dHp / (kH * Sh);

  return Math.sqrt(termL * termL + termC * termC + termH * termH + Rt * termC * termH);
}
