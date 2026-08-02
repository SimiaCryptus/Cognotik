// Minimal immutable-ish 2D vector helpers. Each function returns a new {x,y}.

export const vec = (x = 0, y = 0) => ({ x, y });

export const add = (a, b) => ({ x: a.x + b.x, y: a.y + b.y });
export const sub = (a, b) => ({ x: a.x - b.x, y: a.y - b.y });
export const scale = (a, s) => ({ x: a.x * s, y: a.y * s });
export const dot = (a, b) => a.x * b.x + a.y * b.y;
export const lenSq = (a) => a.x * a.x + a.y * a.y;
export const len = (a) => Math.sqrt(lenSq(a));

export const dist = (a, b) => len(sub(a, b));
export const distSq = (a, b) => lenSq(sub(a, b));

export const normalize = (a) => {
  const l = len(a);
  return l === 0 ? { x: 0, y: 0 } : { x: a.x / l, y: a.y / l };
};

export const clone = (a) => ({ x: a.x, y: a.y });

// Linear interpolation between a and b at parameter t in [0,1].
export const lerp = (a, b, t) => ({
  x: a.x + (b.x - a.x) * t,
  y: a.y + (b.y - a.y) * t,
});
