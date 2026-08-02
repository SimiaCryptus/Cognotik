// Quadratic field arithmetic over Q(sqrt(D)).
// An element is a pair [a, b] representing a + b*sqrt(D).
// Coefficients are JS numbers (we accept floating-point for the geometry,
// but the algebraic structure is preserved symbolically through the pair).

export class QuadField {
  constructor(D) {
    this.D = D;
    this.sqrtD = Math.sqrt(D);
  }

  // Construct element from rational/irrational parts.
  el(a, b) {
    return [a, b];
  }

  add(x, y) {
    return [x[0] + y[0], x[1] + y[1]];
  }
  sub(x, y) {
    return [x[0] - y[0], x[1] - y[1]];
  }
  scale(x, s) {
    return [x[0] * s, x[1] * s];
  }

  // Real-valued embedding: a + b*sqrt(D).
  toReal(x) {
    return x[0] + x[1] * this.sqrtD;
  }

  // Conjugate embedding: a - b*sqrt(D).
  conj(x) {
    return x[0] - x[1] * this.sqrtD;
  }
}
