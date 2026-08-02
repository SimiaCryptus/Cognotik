// field.js
// Exact arithmetic in Q(sqrt(5), S) where S = sqrt(10 + 2*sqrt(5)),
// i.e. S = 4*sin(72 deg). This field contains everything we need to
// represent pentagon vertices and centroids under rotations by 72 deg.
//
// An element is encoded as a 4-tuple {a, b, c, d} representing
//     a + b*sqrt(5) + c*S + d*S*sqrt(5)
// with a, b, c, d in Q (we use JS numbers; for the radii used in this
// lab no denominator blow-up occurs because we use small rationals).
//
// Multiplication rules:
//   sqrt(5)^2          = 5
//   S^2                = 10 + 2*sqrt(5)
//   S^2 * sqrt(5)      = 10*sqrt(5) + 2*5 = 10 + 10*sqrt(5)  [error]
//   correctly:           = sqrt(5)*(10 + 2*sqrt(5)) = 10*sqrt(5) + 2*5
//                      = 10 + 10*sqrt(5)
// We implement these explicitly below.

// Construct from rational parts.
export function K(a = 0, b = 0, c = 0, d = 0) {
  return { a, b, c, d };
}

export const ZERO = K(0, 0, 0, 0);
export const ONE = K(1, 0, 0, 0);
export const SQRT5 = K(0, 1, 0, 0);
// S = sqrt(10 + 2*sqrt(5)) = 4*sin(72 deg)
export const S_SYM = K(0, 0, 1, 0);

export function add(x, y) {
  return K(x.a + y.a, x.b + y.b, x.c + y.c, x.d + y.d);
}
export function sub(x, y) {
  return K(x.a - y.a, x.b - y.b, x.c - y.c, x.d - y.d);
}
export function neg(x) {
  return K(-x.a, -x.b, -x.c, -x.d);
}
export function scale(x, r) {
  // r is a plain rational JS number
  return K(x.a * r, x.b * r, x.c * r, x.d * r);
}

// Multiplication.
// (a + b s5 + c S + d S s5)(A + B s5 + C S + D S s5)
//   s5 * s5 = 5
//   S  * S  = 10 + 2 s5
//   S * s5 * S * s5 = S^2 * 5 = 50 + 10 s5
//   S * s5 * S      = (10 + 2 s5) * s5 = 10 s5 + 10  = 10 + 10 s5
export function mul(x, y) {
  const { a, b, c, d } = x;
  const { a: A, b: B, c: C, d: D } = y;
  // rational + sqrt5 parts of plain (no S) terms
  let pa = a * A + 5 * b * B;
  let pb = a * B + b * A;
  // pure S terms: a*C*S + b*D*(sqrt5*S*sqrt5? no)
  //   a * C * S            -> contributes to c part
  //   a * D * (S*sqrt5)    -> contributes to d part
  //   b*sqrt5 * C*S = b*C*S*sqrt5 -> d part
  //   b*sqrt5 * D*S*sqrt5 = b*D*S*5 -> c part
  let pc = a * C + 5 * b * D;
  let pd = a * D + b * C;
  // S terms from x.cS, x.d*S*sqrt5 times the *plain* parts of y already
  pc += c * A + 5 * d * B;
  pd += c * B + d * A;
  // Now the genuinely-S^2 terms.
  //   (c*S) * (C*S)            = c*C*S^2 = c*C*(10 + 2 s5)
  //   (c*S) * (D*S*sqrt5)      = c*D*S^2*sqrt5 = c*D*(10 + 2 s5)*sqrt5
  //                            = c*D*(10 s5 + 2*5) = c*D*(10 + 10 s5)
  //   (d*S*sqrt5) * (C*S)      = d*C*S^2*sqrt5 = d*C*(10 + 10 s5)
  //   (d*S*sqrt5) * (D*S*sqrt5)= d*D*S^2*5    = d*D*(50 + 10 s5)
  pa += 10 * c * C + 10 * c * D + 10 * d * C + 50 * d * D;
  pb += 2 * c * C + 10 * c * D + 10 * d * C + 10 * d * D;
  return K(pa, pb, pc, pd);
}

// Numeric evaluation.
const SQRT5_NUM = Math.sqrt(5);
const S_NUM = Math.sqrt(10 + 2 * SQRT5_NUM); // = 4*sin(72 deg)
export function toFloat(x) {
  return x.a + x.b * SQRT5_NUM + x.c * S_NUM + x.d * S_NUM * SQRT5_NUM;
}

// Approximate equality, used only as fallback (exact equality preferred).
export function approxEq(x, y, eps = 1e-12) {
  return Math.abs(toFloat(sub(x, y))) < eps;
}

// Exact equality on rational coefficients.
export function eq(x, y) {
  return x.a === y.a && x.b === y.b && x.c === y.c && x.d === y.d;
}

// Canonical key for hashing (used to look up vertices/edges).
// We snap to a high-precision string of rational coords. Since we only
// do additions/subtractions/multiplications by rational scalars and
// rotations by 72 deg, coefficients remain "clean" rationals; rounding
// them slightly is safe.
function round(q) {
  // Snap to 12 decimal digits to absorb floating-point dust that comes
  // from chained 72-deg rotations of S^2 expansions.
  return Math.round(q * 1e12) / 1e12;
}
export function key(x) {
  return `${round(x.a)},${round(x.b)},${round(x.c)},${round(x.d)}`;
}

// Pretty-printing in human-readable algebraic form.
function fmtRat(q, opts = {}) {
  const r = round(q);
  if (r === 0) return '0';
  // Try to express as small rational a/b.
  for (let den = 1; den <= 64; den++) {
    const num = r * den;
    if (Math.abs(num - Math.round(num)) < 1e-9) {
      const n = Math.round(num);
      if (den === 1) return `${n}`;
      return `${n}/${den}`;
    }
  }
  return r.toFixed(6);
}
function fmtTerm(coef, sym) {
  if (coef === 0) return '';
  if (sym === '') return fmtRat(coef);
  const r = round(coef);
  if (r === 1) return sym;
  if (r === -1) return `-${sym}`;
  return `${fmtRat(coef)}·${sym}`;
}
export function toAlg(x) {
  const parts = [];
  const add = (t) => {
    if (t !== '') parts.push(t);
  };
  add(fmtTerm(x.a, ''));
  add(fmtTerm(x.b, '√5'));
  add(fmtTerm(x.c, 'S'));
  add(fmtTerm(x.d, 'S√5'));
  if (parts.length === 0) return '0';
  let out = parts[0];
  for (let i = 1; i < parts.length; i++) {
    const p = parts[i];
    if (p.startsWith('-')) out += ' − ' + p.slice(1);
    else out += ' + ' + p;
  }
  return out;
}
