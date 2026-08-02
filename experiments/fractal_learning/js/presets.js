// Target point-set presets.

export function presetCircle(n = 200) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const a = (2 * Math.PI * i) / n;
    pts.push([0.6 * Math.cos(a), 0.6 * Math.sin(a)]);
  }
  return pts;
}
export function presetSquare(n = 200) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const t = (i / n) * 4,
      s = t % 1;
    if (t < 1) pts.push([-0.6 + 1.2 * s, -0.6]);
    else if (t < 2) pts.push([0.6, -0.6 + 1.2 * s]);
    else if (t < 3) pts.push([0.6 - 1.2 * s, 0.6]);
    else pts.push([-0.6, 0.6 - 1.2 * s]);
  }
  return pts;
}
export function presetHeart(n = 250) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const t = (2 * Math.PI * i) / n;
    const x = 16 * Math.pow(Math.sin(t), 3);
    const y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
    pts.push([x / 20, y / 20]);
  }
  return pts;
}
export function presetSpiral(n = 300) {
  const pts = [];
  for (let i = 0; i < n; i++) {
    const t = (i / n) * 6 * Math.PI;
    const r = 0.05 + (0.7 * i) / n;
    pts.push([r * Math.cos(t), r * Math.sin(t)]);
  }
  return pts;
}
export function presetSierpinski(n = 1500) {
  const verts = [
    [-0.6, -0.5],
    [0.6, -0.5],
    [0, 0.6],
  ];
  const pts = [];
  let x = 0,
    y = 0;
  for (let i = 0; i < n + 50; i++) {
    const v = verts[(Math.random() * 3) | 0];
    x = (x + v[0]) / 2;
    y = (y + v[1]) / 2;
    if (i > 50) pts.push([x, y]);
  }
  return pts;
}
export function presetFern(n = 2000) {
  const pts = [];
  let x = 0,
    y = 0;
  for (let i = 0; i < n + 50; i++) {
    const r = Math.random();
    let nx, ny;
    if (r < 0.01) {
      nx = 0;
      ny = 0.16 * y;
    } else if (r < 0.86) {
      nx = 0.85 * x + 0.04 * y;
      ny = -0.04 * x + 0.85 * y + 1.6;
    } else if (r < 0.93) {
      nx = 0.2 * x - 0.26 * y;
      ny = 0.23 * x + 0.22 * y + 1.6;
    } else {
      nx = -0.15 * x + 0.28 * y;
      ny = 0.26 * x + 0.24 * y + 0.44;
    }
    x = nx;
    y = ny;
    if (i > 50) pts.push([x / 6, y / 6 - 0.7]);
  }
  return pts;
}
export function presetCantor(n = 1500) {
  const pts = [];
  function rec(x, w, depth) {
    if (depth === 0) {
      for (let i = 0; i < 6; i++) pts.push([x + Math.random() * w, 0]);
      return;
    }
    rec(x, w / 3, depth - 1);
    rec(x + (2 * w) / 3, w / 3, depth - 1);
  }
  rec(-0.7, 1.4, 6);
  return pts.slice(0, n);
}
export function presetGrid(n = 7) {
  const pts = [];
  for (let i = -n; i <= n; i++)
    for (let j = -n; j <= n; j++) pts.push([i / (n * 1.5), j / (n * 1.5)]);
  return pts;
}

export const PRESETS = {
  circle: presetCircle,
  square: presetSquare,
  heart: presetHeart,
  spiral: presetSpiral,
  sierpinski: presetSierpinski,
  fern: presetFern,
  cantor: presetCantor,
  grid: presetGrid,
};
