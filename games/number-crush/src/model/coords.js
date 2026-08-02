// Axial hex coordinate helpers (pointy-top hexagons).
//
// We use axial coordinates (q, r). Rendering uses pointy-top layout.
// All hex geometry lives here so it can be tested in isolation.

// Six neighbor directions in axial coordinates.
export const AXIAL_DIRECTIONS = [
  { q: +1, r: 0 },
  { q: +1, r: -1 },
  { q: 0, r: -1 },
  { q: -1, r: 0 },
  { q: -1, r: +1 },
  { q: 0, r: +1 },
];

export function keyOf(q, r) {
  return `${q},${r}`;
}

export function coordKey(coord) {
  return keyOf(coord.q, coord.r);
}

export function neighbors(coord) {
  return AXIAL_DIRECTIONS.map((d) => ({ q: coord.q + d.q, r: coord.r + d.r }));
}

export function areNeighbors(a, b) {
  return AXIAL_DIRECTIONS.some((d) => a.q + d.q === b.q && a.r + d.r === b.r);
}

export function equals(a, b) {
  return a.q === b.q && a.r === b.r;
}

// Convert axial coordinate to pixel position (pointy-top layout).
// `size` is the hex radius (center to corner).
export function axialToPixel(q, r, size) {
  const x = size * Math.sqrt(3) * (q + r / 2);
  const y = size * (3 / 2) * r;
  return { x, y };
}

// Convert pixel position back to the nearest axial coordinate.
export function pixelToAxial(x, y, size) {
  const q = ((Math.sqrt(3) / 3) * x - (1 / 3) * y) / size;
  const r = ((2 / 3) * y) / size;
  return axialRound(q, r);
}

// Round fractional axial coordinates to the nearest hex.
export function axialRound(q, r) {
  // Convert to cube for rounding.
  let x = q;
  let z = r;
  let y = -x - z;

  let rx = Math.round(x);
  let ry = Math.round(y);
  let rz = Math.round(z);

  const dx = Math.abs(rx - x);
  const dy = Math.abs(ry - y);
  const dz = Math.abs(rz - z);

  if (dx > dy && dx > dz) {
    rx = -ry - rz;
  } else if (dy > dz) {
    ry = -rx - rz;
  } else {
    rz = -rx - ry;
  }

  return { q: rx, r: rz };
}
