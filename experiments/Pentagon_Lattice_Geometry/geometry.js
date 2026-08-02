// geometry.js
// Pentagon geometry built on the exact field defined in field.js.
// - 2D points are { x: KElt, y: KElt } where KElt is the algebraic
//   field element type from field.js.
// - The rotation by 72 deg is exact:
//     cos(72) = (sqrt(5) - 1)/4
//     sin(72) = S/4         where S = sqrt(10 + 2 sqrt(5))

import { K, ZERO, ONE, add, sub, neg, scale, mul, toFloat, key, toAlg, eq } from './field.js';

// ----- Vec2 over the field -----
export function V(x, y) {
  return { x, y };
}
export function vAdd(p, q) {
  return V(add(p.x, q.x), add(p.y, q.y));
}
export function vSub(p, q) {
  return V(sub(p.x, q.x), sub(p.y, q.y));
}
export function vScale(p, r) {
  return V(scale(p.x, r), scale(p.y, r));
}
export function vEq(p, q) {
  return eq(p.x, q.x) && eq(p.y, q.y);
}
export function vKey(p) {
  return key(p.x) + '|' + key(p.y);
}
export function vFloat(p) {
  return [toFloat(p.x), toFloat(p.y)];
}
export function vAlg(p) {
  return `( ${toAlg(p.x)} , ${toAlg(p.y)} )`;
}

// ----- Pentagon constants (unit-edge regular pentagon) -----
//
// Circumradius R = 1/(2 sin(36 deg)).
//   sin(36) = sqrt(10 - 2 sqrt(5))/4    -- different nested radical.
// To keep arithmetic inside Q(sqrt(5), S) where S = sqrt(10+2 sqrt(5)),
// we instead build the pentagon from a *known vertex offset*:
//   take the apothem direction and use angular addition with 72-deg
//   rotations of the offset vector (R, 0). For the field to stay closed
//   we therefore work with edge length s such that R lives in the same
//   ring; the cleanest choice is to *define* the edge length so that
//     2 sin(36) = something inside Q(sqrt(5), S).
//
// It turns out that  4 sin(36) * 4 sin(72) = 4 * sqrt(5),  i.e.
//   S * sqrt(10-2 sqrt(5)) = 4 sqrt(5)
// so sqrt(10 - 2 sqrt(5)) = 4 sqrt(5) / S
// and 1/(2 sin 36) = 1 / ( sqrt(10-2 sqrt(5)) / 2 )
//                  = 2 / sqrt(10-2 sqrt(5))
//                  = 2 * S / (4 sqrt(5))
//                  = S / (2 sqrt(5))
//                  = S * sqrt(5) / 10
//
// i.e. R = (S * sqrt(5)) / 10, which is exactly an element of our ring
// (the "d" component, scaled by 1/10).
//
// So R = K(0, 0, 0, 1/10).
export const R_CIRCUM = K(0, 0, 0, 1 / 10);

// Rotation by 72 deg in the field.
// cos72 = (sqrt(5) - 1)/4
// sin72 = S/4
export const COS72 = K(-1 / 4, 1 / 4, 0, 0);
export const SIN72 = K(0, 0, 1 / 4, 0);

export function rotate(p, cos, sin) {
  // (x,y) -> (cos*x - sin*y, sin*x + cos*y)
  return V(sub(mul(cos, p.x), mul(sin, p.y)), add(mul(sin, p.x), mul(cos, p.y)));
}

// Vertex k (0..4) of pentagon at centroid `c` with orientation index `o`
// in 0..4 meaning rotated by o*72 deg from the canonical orientation.
// Canonical orientation: vertex 0 points "up" (+y) when sigma=0, and
// "down" (-y) when sigma=1. Sigma encodes the reflection bit needed
// for edge-to-edge tiling of regular pentagons: any two pentagons
// sharing an edge must be reflections of each other (since C_5 alone
// can't tile the plane edge-to-edge). Without this flip, neighbors
// would visually look "the same orientation" instead of inverted.
export function pentVertex(centroid, orient, k, sigma = 0) {
  // start with v0 = (0, R) for sigma=0, or (0, -R) for sigma=1.
  let v = sigma & 1 ? V(ZERO, neg(R_CIRCUM)) : V(ZERO, R_CIRCUM);
  const steps = (orient + k) % 5;
  for (let i = 0; i < steps; i++) v = rotate(v, COS72, SIN72);
  return vAdd(centroid, v);
}

export function pentVertices(centroid, orient, sigma = 0) {
  const out = [];
  for (let k = 0; k < 5; k++) out.push(pentVertex(centroid, orient, k, sigma));
  return out;
}

// Edge k connects vertex k and vertex k+1.
export function pentEdge(centroid, orient, k, sigma = 0) {
  return [pentVertex(centroid, orient, k, sigma), pentVertex(centroid, orient, (k + 1) % 5, sigma)];
}

// ----- Edge-mate computation -----
//
// For pentagon P at centroid c with orientation o, edge k has midpoint
// m_k and outward normal n_k. The neighbor across edge k is a pentagon
// P' whose centroid is c + 2*(m_k - c) reflected... easier:
//   c' = c + 2 * (m_k - c) projected outward,
// but simpler still: the neighbor sharing edge k has its centroid on
// the opposite side of that edge, at the same perpendicular distance
// (the apothem) from the edge midpoint. Its orientation is rotated by
// 180 deg relative to P about the edge normal, which is equivalent to
// o' = o + (something) mod 5 *together with a flip*.
//
// For an edge-to-edge match of two regular pentagons:
//   - the neighbor's sigma flips (necessary reflection)
//   - the neighbor's orient shifts by a CONSTANT 3 (mod 5), not by a
//     k-dependent amount. The constant choice is what makes "press the
//     same edge key repeatedly" walk in a roughly straight line — each
//     step then rotates the walking direction by only +36 deg (the
//     smallest possible offset with 5-fold symmetry).
//   - the centroid is c' = c + 2*(m_k - c).
//
// Derivation: edge k of (c, o, s) has midpoint at world angle
//   phi_k = 126 + 180*s + 72*(o + k) deg from c.
// The neighbor (c', o', s') has edge k' midpoint at angle
//   psi_k' = 126 + 180*s' + 72*(o' + k').
// Shared edge requires psi_k' = phi_k + 180, giving
//   180*(s'-s) + 72*(o'+k'-o-k) = 180 (mod 360).
// Set s' = s+1 (mod 2); then o' + k' = o + k (mod 5).
// To make pressing the same k continue in the same world direction,
// we want psi_k - phi_k ~= 0, i.e. 180 + 72*(o'-o) ~= 0 (mod 360).
// The closest 72-deg multiple to -180 is -216 (= +144), giving
// o' = o + 2 (mod 5), or +3, both with |offset| = 36 deg. We pick +3.
// Then matchEdge = (k + 2) mod 5.
//
// We still verify matchEdge constructively below from shared vertices.
//
// sheet_fix.md (NORMATIVE): The FIBER of the covering space is Z₂ and is
// carried ENTIRELY by the orientation bit `sigma`. Crossing any edge flips
// orientation exactly once (the single non-trivial element of Z₂). There is
// no Z5 / Z10 fiber and no `signed3` sheet-shift rule. The `orient` value is
// a base-graph (vertex-labeling) quantity, NOT a fiber coordinate. Because
// the vertex loop has even length (10), the product of edge flips around it
// is identity, so the holonomy around a pentagon vertex is TRIVIAL (0).

export function neighborOf(centroid, orient, sigma, k) {
  // edge midpoint
  const [v0, v1] = pentEdge(centroid, orient, k, sigma);
  const mid = vScale(vAdd(v0, v1), 0.5);
  // c' = c + 2*(mid - c)
  const newC = vAdd(centroid, vScale(vSub(mid, centroid), 2));
  const newSigma = 1 - (sigma & 1);
  const newO = (orient + 3) % 5;
  // determine which edge of the neighbor matches: the neighbor edge
  // whose vertex set equals {v0, v1}.
  let matchEdge = -1;
  for (let kk = 0; kk < 5; kk++) {
    const [w0, w1] = pentEdge(newC, newO, kk, newSigma);
    if ((vEq(w0, v0) && vEq(w1, v1)) || (vEq(w0, v1) && vEq(w1, v0))) {
      matchEdge = kk;
      break;
    }
  }
  // The Z₂ fiber element for crossing this edge is the orientation flip.
  // sheetDelta ∈ {0,1}; here it is always 1 (every edge flips orientation).
  const sheetDelta = (newSigma - (sigma & 1)) & 1;
  return { centroid: newC, orient: newO, sigma: newSigma, matchEdge, sheetDelta };
}
