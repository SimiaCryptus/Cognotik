// Deterministic hash-based value noise with bilinear interpolation,
// plus lattice helpers. All functions are differentiable (C1) so the
// composite objective has continuous gradients.

function hash2(ix, iy, seed) {
  // integer hash -> [0,1)
  let h = ix * 374761393 + iy * 668265263 + seed * 2246822519;
  h = (h ^ (h >>> 13)) >>> 0;
  h = Math.imul(h, 1274126177) >>> 0;
  h = (h ^ (h >>> 16)) >>> 0;
  return (h % 100000) / 100000;
}

// smoothstep and its derivative
function smooth(t) {
  return t * t * (3 - 2 * t);
}

function smoothD(t) {
  return 6 * t * (1 - t);
}

// value noise at continuous coords; returns {v, dvx, dvy}
export function valueNoise(x, y, seed) {
  const x0 = Math.floor(x),
    y0 = Math.floor(y);
  const x1 = x0 + 1,
    y1 = y0 + 1;
  const fx = x - x0,
    fy = y - y0;

  const v00 = hash2(x0, y0, seed);
  const v10 = hash2(x1, y0, seed);
  const v01 = hash2(x0, y1, seed);
  const v11 = hash2(x1, y1, seed);

  const sx = smooth(fx),
    sy = smooth(fy);
  const dsx = smoothD(fx),
    dsy = smoothD(fy);

  const a = v00 + (v10 - v00) * sx;
  const b = v01 + (v11 - v01) * sx;
  const v = a + (b - a) * sy;

  // derivatives (map from [0,1] to [-1,1] outside)
  const da_dx = (v10 - v00) * dsx;
  const db_dx = (v11 - v01) * dsx;
  const dv_dx = da_dx + (db_dx - da_dx) * sy;

  const dv_dy = (b - a) * dsy;

  // remap to [-1,1]
  return { v: v * 2 - 1, dvx: dv_dx * 2, dvy: dv_dy * 2 };
}

// Smooth lattice mask in [0,1] with derivatives.
// Cells of size L, active fraction f. Uses a smoothstep transition band.
// A per-cell pseudorandom activation (seeded) softly selects ~f of cells as active.
// `offset` scales the per-cell gate strength (0 disables, >1 amplifies).
export function latticeMask(x, y, L, f, seed = 0, offset = 1) {
  // 1D periodic bump per axis; product gives cell activity.
  const m = (u) => {
    const p = ((u % L) + L) % L; // 0..L
    const c = p / L; // 0..1
    // active region occupies central fraction f with smooth edges
    const edge = (1 - f) / 2;
    const band = Math.max(0.001, edge * 0.6);
    let val, dval;
    if (c < edge) {
      const t = c / band;
      if (t < 1) {
        val = smooth(t);
        dval = smoothD(t) / band;
      } else {
        val = 1;
        dval = 0;
      }
    } else if (c > 1 - edge) {
      const t = (1 - c) / band;
      if (t < 1) {
        val = smooth(t);
        dval = -smoothD(t) / band;
      } else {
        val = 1;
        dval = 0;
      }
    } else {
      val = 1;
      dval = 0;
    }
    return { val, dval: dval / L };
  };
  const mx = m(x),
    my = m(y);
  // per-cell pseudorandom gate. Instead of a hard binary threshold
  // (which introduces per-cell discontinuities), map the hash through a
  // smooth activation centered on f, then scale by `offset`.
  const cx = Math.floor(x / L),
    cy = Math.floor(y / L);
  const h = hash2(cx, cy, seed);
  // soft gate: ~1 when h < f, ~0 when h > f, with a smooth ramp region.
  const gw = 0.15; // gate softness width
  let gate;
  if (h <= f - gw) gate = 1;
  else if (h >= f + gw) gate = 0;
  else gate = 1 - smooth((h - (f - gw)) / (2 * gw));
  gate *= offset;
  const val = mx.val * my.val * gate;
  return {
    val,
    dx: mx.dval * my.val * gate,
    dy: mx.val * my.dval * gate,
  };
}
