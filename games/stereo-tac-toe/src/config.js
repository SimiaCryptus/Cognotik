// Tunable constants for the whole game. These are now LIVE (mutable) so the
// control panel can tweak them at runtime. `boardGeometry()` recomputes from
// current values each call.

// Default values, used to (re)initialize the live CONFIG object.
export const DEFAULTS = Object.freeze({
  WIDTH: 640,
  HEIGHT: 480,

  // Autostereogram tuning.
  EYE_SEPARATION_PX: 280,
  PATTERN_WIDTH: 96,
  // Max horizontal shift a near depth introduces. Smaller = subtler.
  MU: 1 / 3, // depth of field factor (fraction of eye separation)

  // Noise / pattern tuning.
  NOISE_MODE: 'rgb', // 'rgb' | 'grayscale' | 'bw'
  NOISE_DENSITY: 0.5, // for 'bw': fraction of white dots
  NOISE_CONTRAST: 1.0, // 0..1 scales dot brightness spread around mid
  NOISE_SEED: 1, // reseed to regenerate the ribbon
  // Ribbon texture applied on top of the base noise.
  // 'none' | 'stripes' | 'checker' | 'plasma' | 'dots'
  NOISE_TEXTURE: 'none',
  NOISE_TEXTURE_SCALE: 16, // feature size (px) for textures
  NOISE_TEXTURE_STRENGTH: 0.6, // 0..1 blend of texture over base noise
  // When true, PATTERN_WIDTH is derived from eye separation & depth.
  AUTO_PATTERN_WIDTH: true,

  // Named depth levels (0 = far background, 1 = near foreground).
  // These are derived from a base + contrast so they can be scaled live.
  DEPTH_BACKGROUND: 0.0,
  DEPTH_GRID: 0.35,
  DEPTH_MARK: 0.65,
  DEPTH_CURSOR: 0.85,
  DEPTH_CONTRAST: 1.0, // multiplies all non-background depths

  // Board layout metrics (in pixels, within the canvas).
  GRID_MARGIN: 80, // margin around the 3x3 board
  CELL_GAP: 8, // gap between cells (drawn as grid lines)
  LINE_THICKNESS: 6, // grid line thickness in px
  MARK_THICKNESS: 10, // stroke thickness for X / O
  MARK_INSET: 22, // inset of a mark from its cell edge
});

// Live, mutable config. Start as a shallow copy of DEFAULTS.
export const CONFIG = { ...DEFAULTS };

// DEPTH_LEVELS is a computed accessor so depth contrast applies live.
Object.defineProperty(CONFIG, 'DEPTH_LEVELS', {
  enumerable: true,
  get() {
    const k = CONFIG.DEPTH_CONTRAST;
    const clamp = (v) => Math.max(0, Math.min(1, v));
    return {
      BACKGROUND: CONFIG.DEPTH_BACKGROUND,
      GRID: clamp(CONFIG.DEPTH_GRID * k),
      MARK: clamp(CONFIG.DEPTH_MARK * k),
      CURSOR: clamp(CONFIG.DEPTH_CURSOR * k),
    };
  },
});

// Reset the live CONFIG back to defaults (in place, preserving identity).
export function resetConfig() {
  for (const key of Object.keys(DEFAULTS)) {
    CONFIG[key] = DEFAULTS[key];
  }
}

// Derived board geometry helper, computed from current CONFIG each call.
export function boardGeometry() {
  const { WIDTH, HEIGHT, GRID_MARGIN } = CONFIG;
  const boardSize = Math.min(WIDTH, HEIGHT) - GRID_MARGIN * 2;
  const originX = (WIDTH - boardSize) / 2;
  const originY = (HEIGHT - boardSize) / 2;
  const cellSize = boardSize / 3;
  return { boardSize, originX, originY, cellSize };
}
// The maximum separation is produced at the background depth (z = 0).
// With separation(z) = (1 - mu*z) * E / (2 - mu*z), z=0 gives E/2.
// A ribbon that matches this background separation tiles cleanly so the
// far field looks like a stable repeating pattern.
export function backgroundSeparation() {
  return Math.round(CONFIG.EYE_SEPARATION_PX / 2);
}
// Auto ribbon width: use the background separation so unconstrained
// anchors line up with the stereo period. Clamped to a sane range.
export function effectivePatternWidth() {
  if (!CONFIG.AUTO_PATTERN_WIDTH) return CONFIG.PATTERN_WIDTH;
  const sep = backgroundSeparation();
  return Math.max(24, Math.min(240, sep));
}
