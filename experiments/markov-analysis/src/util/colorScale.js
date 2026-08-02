// colorScale.js — probability -> color

// Normalize probability to [0,1] "surprise" where 1 = most surprising (low p).
function surprise(p, scale, floor) {
  const eps = Math.max(floor, 1e-12);
  if (scale === 'log') {
    const lp = Math.log(Math.max(p, eps));
    const lo = Math.log(eps); // most surprising
    const hi = Math.log(1); // = 0, least surprising
    const t = (lp - lo) / (hi - lo); // 0..1, high p -> 1
    return 1 - Math.max(0, Math.min(1, t));
  }
  return 1 - Math.max(0, Math.min(1, p));
}

function heatColor(s) {
  // s: 0 (calm) -> 1 (hot). transparent when calm.
  const alpha = s * 0.85;
  const r = 255;
  const g = Math.round(200 * (1 - s));
  const b = Math.round(80 * (1 - s));
  return `rgba(${r},${g},${b},${alpha.toFixed(3)})`;
}

function viridisColor(s) {
  const alpha = s * 0.85;
  const r = Math.round(68 + s * (253 - 68));
  const g = Math.round(1 + s * (231 - 1));
  const b = Math.round(84 + s * (37 - 84));
  return `rgba(${r},${g},${b},${alpha.toFixed(3)})`;
}

export function colorFor(p, cfg) {
  const s = surprise(p, cfg.colorScale, cfg.floorProb);
  return cfg.palette === 'viridis' ? viridisColor(s) : heatColor(s);
}

export function legendStops(cfg, steps = 20) {
  const stops = [];
  for (let i = 0; i < steps; i++) {
    const s = i / (steps - 1);
    // s here is surprise directly for the legend
    const col = cfg.palette === 'viridis' ? viridisColor(s) : heatColor(s);
    stops.push(col);
  }
  return stops;
}
