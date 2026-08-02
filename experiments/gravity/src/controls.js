import { presets, presetKeys } from './presets.js';

const SLIDERS = [
  { key: 'mass1', label: 'Mass 1', min: 1, max: 1000, step: 1 },
  { key: 'mass2', label: 'Mass 2', min: 1, max: 1000, step: 1 },
  { key: 'c', label: 'c (light speed)', min: 4, max: 1000, step: 1 },
  { key: 'alpha', label: 'alpha (relativity)', min: 0, max: 1, step: 0.01 },
  { key: 'G', label: 'G (coupling)', min: 0.05, max: 10, step: 0.05 },
  { key: 'dt', label: 'dt (step)', min: 0.001, max: 0.05, step: 0.001 },
  { key: 'epsilon', label: 'softening eps', min: 0.1, max: 20, step: 0.1 },
  { key: 'speed', label: 'speed (steps/frame)', min: 1, max: 60, step: 1 },
];

export class Controls {
  constructor(mount, handlers) {
    this.mount = mount;
    this.sim = handlers.simulation;
    this.handlers = handlers;
    this.inputs = {};
    this.speed = 8;
    this._build();
  }

  _row(html) {
    const div = document.createElement('div');
    div.className = 'control-row';
    div.innerHTML = html;
    return div;
  }

  _build() {
    this.mount.innerHTML = '<h2>✦ Controls</h2>';

    // preset selector
    const select = document.createElement('select');
    select.className = 'preset-select';
    presetKeys.forEach((k) => {
      const opt = document.createElement('option');
      opt.value = k;
      opt.textContent = presets[k].label;
      select.appendChild(opt);
    });
    select.addEventListener('change', () => this.handlers.onPreset(select.value));
    this.presetSelect = select;
    this.mount.appendChild(select);

    // --- camera / viewport section ---
    const camHeader = document.createElement('div');
    camHeader.className = 'section-header';
    camHeader.textContent = 'Viewport';
    this.mount.appendChild(camHeader);

    // follow mode selector
    const followRow = this._row(`
          <label>Follow <span class="val"></span></label>
          <select class="follow-select">
            <option value="fit">Free fit (auto-zoom)</option>
            <option value="free">Free (manual)</option>
            <option value="a">Body A</option>
            <option value="b">Body B</option>
            <option value="com">Center of mass</option>
          </select>
        `);
    const followSelect = followRow.querySelector('.follow-select');
    followSelect.addEventListener('change', () => {
      if (this.handlers.onFollowMode) this.handlers.onFollowMode(followSelect.value);
    });
    this.followSelect = followSelect;
    this.mount.appendChild(followRow);

    // Lock + reset rows for location / scale / velocity.
    const lockDefs = [
      { key: 'location', label: 'Location' },
      { key: 'scale', label: 'Scale' },
      { key: 'velocity', label: 'Velocity' },
    ];
    lockDefs.forEach((d) => {
      const row = this._row(`
            <div class="lock-row">
              <span class="lock-name">${d.label}</span>
              <label class="lock-toggle">
                <input type="checkbox" data-lock="${d.key}" />
                <span>lock</span>
              </label>
              <button data-resetcam="${d.key}">reset</button>
            </div>
          `);
      const cb = row.querySelector(`[data-lock="${d.key}"]`);
      cb.addEventListener('change', () => {
        if (this.handlers.onLock) this.handlers.onLock(d.key, cb.checked);
      });
      const btn = row.querySelector(`[data-resetcam="${d.key}"]`);
      btn.addEventListener('click', () => {
        if (this.handlers.onResetCam) this.handlers.onResetCam(d.key);
      });
      this.mount.appendChild(row);
    });

    // --- physics section ---
    const physHeader = document.createElement('div');
    physHeader.className = 'section-header';
    physHeader.textContent = 'Physics';
    this.mount.appendChild(physHeader);

    // sliders
    SLIDERS.forEach((cfg) => {
      const row = this._row(`
            <label>${cfg.label} <span class="val"></span></label>
            <input type="range" min="${cfg.min}" max="${cfg.max}" step="${cfg.step}" />
          `);
      const input = row.querySelector('input');
      const val = row.querySelector('.val');
      input.addEventListener('input', () => {
        val.textContent = input.value;
        this._applySlider(cfg.key, parseFloat(input.value));
      });
      this.inputs[cfg.key] = { input, val };
      this.mount.appendChild(row);
    });

    // buttons
    const btnRow = document.createElement('div');
    btnRow.className = 'button-row';
    btnRow.innerHTML = `
          <button data-act="playpause">Play</button>
          <button data-act="step">Step</button>
          <button data-act="reset">Reset</button>
          <button data-act="resetview">Reset View</button>
        `;
    btnRow.addEventListener('click', (e) => {
      const act = e.target.dataset.act;
      if (act === 'playpause') this.handlers.onPlayPause();
      else if (act === 'step') this.handlers.onStep();
      else if (act === 'reset') this.handlers.onReset();
      else if (act === 'resetview') {
        if (this.handlers.onResetView) this.handlers.onResetView();
      }
    });
    this.playBtn = btnRow.querySelector('[data-act="playpause"]');
    this.mount.appendChild(btnRow);
    // toggle row for visual overlays
    const toggleRow = this._row(`
          <label>
            <span>Retarded force vectors</span>
            <input type="checkbox" data-toggle="retarded" checked />
          </label>
        `);
    const retToggle = toggleRow.querySelector('[data-toggle="retarded"]');
    retToggle.addEventListener('change', () => {
      if (this.handlers.onToggleRetarded) this.handlers.onToggleRetarded(retToggle.checked);
    });
    this.mount.appendChild(toggleRow);

    this.sync();
  }

  _applySlider(key, value) {
    const sim = this.sim;
    if (key === 'mass1') sim.bodies[0].mass = value;
    else if (key === 'mass2') sim.bodies[1].mass = value;
    else if (key === 'speed') {
      this.speed = value;
      if (this.handlers.onSpeed) this.handlers.onSpeed(value);
    } else sim.setParams({ [key]: value });
  }

  setPlaying(playing) {
    this.playBtn.textContent = playing ? 'Pause' : 'Play';
  }

  setPreset(key) {
    this.presetSelect.value = key;
  }

  setFollowMode(mode) {
    if (this.followSelect) this.followSelect.value = mode;
  }

  // refresh slider values from simulation state
  sync() {
    const sim = this.sim;
    const map = {
      mass1: sim.bodies[0].mass,
      mass2: sim.bodies[1].mass,
      c: sim.params.c,
      alpha: sim.params.alpha,
      G: sim.params.G,
      dt: sim.params.dt,
      epsilon: sim.params.epsilon,
      speed: this.speed,
    };
    for (const [key, v] of Object.entries(map)) {
      const entry = this.inputs[key];
      if (!entry) continue;
      entry.input.value = v;
      entry.val.textContent = typeof v === 'number' ? v : v;
    }
  }
}
