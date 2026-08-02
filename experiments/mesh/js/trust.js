// js/trust.js
// Per-vertex trust-radius dynamics (math_outline.md §5).

import { scale, norm } from './vec.js';

export class TrustRadii {
  constructor(
    n,
    { r0 = 0.1, rMin = 1e-4, rMax = 1.0, grow = 2, shrink = 0.5, hi = 0.75, lo = 0.25 } = {}
  ) {
    this.r = new Float64Array(n).fill(r0);
    Object.assign(this, { rMin, rMax, grow, shrink, hi, lo });
  }

  // §5  Displacement clamp:  Dp <- Dp * min(1, r/||Dp||)  =>  ||Dp|| <= r.
  clampStep(i, dp) {
    const m = norm(dp);
    return m > this.r[i] ? scale(dp, this.r[i] / m) : dp;
  }

  // Ratio test on a clean (accepted, untruncated) step.
  onCleanStep(i, ratio) {
    if (ratio >= this.hi) this.r[i] = Math.min(this.grow * this.r[i], this.rMax);
    else if (ratio < this.lo) this.r[i] = Math.max(this.shrink * this.r[i], this.rMin);
  }

  // A truncated / projected / rejected step always shrinks.
  onTruncated(i) {
    this.r[i] = Math.max(this.shrink * this.r[i], this.rMin);
  }

  maxRadius() {
    let m = 0;
    for (const v of this.r) if (v > m) m = v;
    return m;
  }

  // §3.1 coupling: broad-phase cell size lower bound.
  //   cell >= mult * (max_i r_i + delta_safe + eps_shell)
  cellSize(deltaSafe, epsShell, mult = 2) {
    return mult * (this.maxRadius() + deltaSafe + epsShell);
  }
}
