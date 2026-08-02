// Central config/state store with a simple pub/sub + dirty flags.

export class AppState {
  constructor() {
    this.config = {
      optimizer: 'qqn',
      lr: 0.05,
      base: 'bowl',
      noiseOn: false,
      latticeOn: false,
      vizMode: 'heatmap',
      colorScheme: 'viridis',
      valueScaling: 'linear',
      autofollow: true,
      showGrad: true,
      speed: 10,
      // global scaling: x/y scale input coords into noise+lattice fields,
      // z scales overall objective amplitude
      global: { sx: 1, sy: 1, sz: 1 },
      // objective params
      bowl: { kx: 0.02, ky: 0.02, cx: 0, cy: 0 },
      linear: { a: 0.5, b: 0.3 },
      // scaling amplitude for closed-form analytic landscapes
      analytic: { scale: 1 },
      noise: { amp: 4, fx: 0.15, fy: 0.15, seed: 1337 },
      lattice: { L: 6, f: 0.5, cgx: 1.2, cgy: -0.8, offset: 1, seed: 4242 },
      start: { x: -18, y: 14 },
      // per-optimizer tunables
      optParams: {
        gd: {},
        adam: { b1: 0.9, b2: 0.999, eps: 1e-8 },
        lbfgs: { m: 8 },
        qqn: {
          m: 8,
          oracle: 'lbfgs',
          momentumBeta: 0.9,
          lineSearch: 'golden',
          maxLineSearch: 30,
        },
      },
    };
    this.dirty = { loss: true, overlay: true, path: true, stats: true, ui: true };
    this.listeners = [];
  }

  subscribe(fn) {
    this.listeners.push(fn);
  }

  emit(evt) {
    this.listeners.forEach((fn) => fn(evt));
  }

  markDirty(...layers) {
    layers.forEach((l) => (this.dirty[l] = true));
  }

  markAllDirty() {
    Object.keys(this.dirty).forEach((k) => (this.dirty[k] = true));
  }

  clean(layer) {
    this.dirty[layer] = false;
  }

  set(key, value) {
    this.config[key] = value;
    this.emit({ type: 'config', key, value });
  }
}
