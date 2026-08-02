// Curve/surface parameterizations in OKLab / OKLch space.
//
// Each manifold exposes `at(t)` for t in [0,1] returning an OKLab point, so
// that sampling.js can drive them uniformly. Constructors take OKLch-friendly
// parameters (lightness/chroma/hue) where that is more natural.

import { oklchToOklab } from '../colorspace/oklab.js';

function lerp(a, b, t) {
  return a + (b - a) * t;
}

// A hue spiral: hue winds `turns` times while lightness/chroma interpolate
// between start and end. Returns OKLab points.
export function spiral({ turns = 1, L = [0.4, 0.8], C = [0.1, 0.1], H0 = 0 } = {}) {
  return {
    type: 'spiral',
    at(t) {
      const H = H0 + 360 * turns * t;
      return oklchToOklab({
        L: lerp(L[0], L[1], t),
        C: lerp(C[0], C[1], t),
        H: ((H % 360) + 360) % 360,
      });
    },
  };
}

// A straight geodesic (Euclidean segment) in OKLab between two endpoints.
export function geodesic({ from, to }) {
  return {
    type: 'geodesic',
    at(t) {
      return {
        L: lerp(from.L, to.L, t),
        a: lerp(from.a, to.a, t),
        b: lerp(from.b, to.b, t),
      };
    },
  };
}

// Quadratic/cubic Bezier through OKLab control points.
export function bezier({ points }) {
  if (!Array.isArray(points) || points.length < 2) {
    throw new Error('bezier: need at least 2 control points');
  }
  const deCasteljau = (pts, t) => {
    let cur = pts;
    while (cur.length > 1) {
      const next = [];
      for (let i = 0; i < cur.length - 1; i++) {
        next.push({
          L: lerp(cur[i].L, cur[i + 1].L, t),
          a: lerp(cur[i].a, cur[i + 1].a, t),
          b: lerp(cur[i].b, cur[i + 1].b, t),
        });
      }
      cur = next;
    }
    return cur[0];
  };
  return {
    type: 'bezier',
    at(t) {
      return deCasteljau(points, t);
    },
  };
}

// A torus loop in OKLch: hue cycles fully while chroma oscillates around a
// mean, at fixed lightness. Returns OKLab points.
export function torus({ L = 0.6, Cmean = 0.12, Camp = 0.04, H0 = 0 } = {}) {
  return {
    type: 'torus',
    at(t) {
      const H = H0 + 360 * t;
      const C = Cmean + Camp * Math.sin(2 * Math.PI * t);
      return oklchToOklab({
        L,
        C,
        H: ((H % 360) + 360) % 360,
      });
    },
  };
}
