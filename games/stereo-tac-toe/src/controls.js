// Builds the "knobs galore" control panel and wires it to live CONFIG.
// Also handles the fullscreen button and drag-to-resize handle.

import { CONFIG, DEFAULTS, resetConfig, effectivePatternWidth } from './config.js';

// Declarative spec for every control. `pattern: true` controls trigger a
// stereogram noise-ribbon rebuild; all controls trigger a redraw.
const CONTROLS = [
  {
    group: 'Stereogram',
    items: [
      {
        key: 'EYE_SEPARATION_PX',
        label: 'Eye separation',
        min: 120,
        max: 400,
        step: 1,
        pattern: true,
      },
      { key: 'AUTO_PATTERN_WIDTH', label: 'Auto ribbon width', type: 'checkbox', pattern: true },
      { key: 'PATTERN_WIDTH', label: 'Pattern width', min: 24, max: 240, step: 1, pattern: true },
      { key: 'MU', label: 'Depth factor (µ)', min: 0.05, max: 0.9, step: 0.01 },
    ],
  },
  {
    group: 'Noise',
    items: [
      {
        key: 'NOISE_MODE',
        label: 'Noise mode',
        type: 'select',
        pattern: true,
        options: [
          ['rgb', 'RGB'],
          ['grayscale', 'Grayscale'],
          ['bw', 'Black & White'],
        ],
      },
      {
        key: 'NOISE_DENSITY',
        label: 'B/W density',
        min: 0.05,
        max: 0.95,
        step: 0.01,
        pattern: true,
      },
      {
        key: 'NOISE_CONTRAST',
        label: 'Noise contrast',
        min: 0.1,
        max: 1,
        step: 0.01,
        pattern: true,
      },
      { key: 'NOISE_SEED', label: 'Noise seed', min: 1, max: 9999, step: 1, pattern: true },
    ],
  },
  {
    group: 'Texture',
    items: [
      {
        key: 'NOISE_TEXTURE',
        label: 'Ribbon texture',
        type: 'select',
        pattern: true,
        options: [
          ['none', 'None'],
          ['stripes', 'Stripes'],
          ['checker', 'Checker'],
          ['dots', 'Dots'],
          ['plasma', 'Plasma'],
        ],
      },
      {
        key: 'NOISE_TEXTURE_SCALE',
        label: 'Texture scale',
        min: 2,
        max: 64,
        step: 1,
        pattern: true,
      },
      {
        key: 'NOISE_TEXTURE_STRENGTH',
        label: 'Texture strength',
        min: 0,
        max: 1,
        step: 0.01,
        pattern: true,
      },
    ],
  },
  {
    group: 'Depth',
    items: [
      { key: 'DEPTH_CONTRAST', label: 'Depth contrast', min: 0.2, max: 1, step: 0.01 },
      { key: 'DEPTH_GRID', label: 'Grid depth', min: 0, max: 1, step: 0.01 },
      { key: 'DEPTH_MARK', label: 'Mark depth', min: 0, max: 1, step: 0.01 },
      { key: 'DEPTH_CURSOR', label: 'Cursor depth', min: 0, max: 1, step: 0.01 },
    ],
  },
  {
    group: 'Drawing',
    items: [
      { key: 'GRID_MARGIN', label: 'Board margin', min: 20, max: 160, step: 1 },
      { key: 'LINE_THICKNESS', label: 'Grid line width', min: 1, max: 30, step: 1 },
      { key: 'MARK_THICKNESS', label: 'Mark stroke width', min: 2, max: 40, step: 1 },
      { key: 'MARK_INSET', label: 'Mark inset', min: 0, max: 60, step: 1 },
    ],
  },
];

export class Controls {
  // onRedraw(): mark dirty. onPatternChange(): rebuild noise ribbon.
  // onResize(w,h): change canvas resolution. getSize(): current {w,h}.
  constructor(container, { onRedraw, onPatternChange, onResize, getSize, canvas }) {
    this.container = container;
    this.onRedraw = onRedraw;
    this.onPatternChange = onPatternChange;
    this.onResize = onResize;
    this.getSize = getSize;
    this.canvas = canvas;
    this.inputs = new Map();
    this._build();
    this._buildResizeHandle();
    this._reflectAutoPattern();
  }

  _build() {
    const panel = document.createElement('div');
    panel.className = 'panel';

    for (const { group, items } of CONTROLS) {
      const section = document.createElement('fieldset');
      section.className = 'knob-group';
      const legend = document.createElement('legend');
      legend.textContent = group;
      section.appendChild(legend);

      for (const item of items) {
        section.appendChild(this._buildItem(item));
      }
      panel.appendChild(section);
    }

    // Resolution controls.
    const resSection = document.createElement('fieldset');
    resSection.className = 'knob-group';
    const resLegend = document.createElement('legend');
    resLegend.textContent = 'Resolution';
    resSection.appendChild(resLegend);
    resSection.appendChild(this._buildResolutionRow());
    panel.appendChild(resSection);

    // Action buttons row.
    const actions = document.createElement('div');
    actions.className = 'panel-actions';

    const resetKnobs = document.createElement('button');
    resetKnobs.type = 'button';
    resetKnobs.textContent = 'Reset knobs';
    resetKnobs.addEventListener('click', () => {
      resetConfig();
      this.syncFromConfig();
      this._reflectAutoPattern();
      this.onPatternChange();
      this.onRedraw();
    });

    const fullscreen = document.createElement('button');
    fullscreen.type = 'button';
    fullscreen.textContent = 'Fullscreen ⤢';
    fullscreen.addEventListener('click', () => this._toggleFullscreen());

    actions.appendChild(resetKnobs);
    actions.appendChild(fullscreen);
    panel.appendChild(actions);

    this.container.appendChild(panel);
  }

  _buildResolutionRow() {
    const wrap = document.createElement('div');
    wrap.className = 'res-row';

    const { w, h } = this.getSize();

    const wIn = document.createElement('input');
    wIn.type = 'number';
    wIn.min = 160;
    wIn.max = 2048;
    wIn.step = 1;
    wIn.value = w;
    wIn.className = 'res-input';

    const hIn = document.createElement('input');
    hIn.type = 'number';
    hIn.min = 120;
    hIn.max = 2048;
    hIn.step = 1;
    hIn.value = h;
    hIn.className = 'res-input';

    const apply = document.createElement('button');
    apply.type = 'button';
    apply.textContent = 'Apply';
    apply.addEventListener('click', () => {
      this.onResize(Number(wIn.value), Number(hIn.value));
      this._reflectAutoPattern();
    });

    const times = document.createElement('span');
    times.textContent = '×';

    wrap.appendChild(wIn);
    wrap.appendChild(times);
    wrap.appendChild(hIn);
    wrap.appendChild(apply);

    this.resWidthInput = wIn;
    this.resHeightInput = hIn;
    return wrap;
  }

  _buildItem(item) {
    const wrap = document.createElement('label');
    wrap.className = 'knob';

    const name = document.createElement('span');
    name.className = 'knob-label';
    name.textContent = item.label;

    let input;
    let valueOut = null;

    if (item.type === 'select') {
      input = document.createElement('select');
      for (const [val, text] of item.options) {
        const opt = document.createElement('option');
        opt.value = val;
        opt.textContent = text;
        input.appendChild(opt);
      }
      input.value = CONFIG[item.key];
    } else if (item.type === 'checkbox') {
      input = document.createElement('input');
      input.type = 'checkbox';
      input.checked = !!CONFIG[item.key];
    } else {
      input = document.createElement('input');
      input.type = 'range';
      input.min = item.min;
      input.max = item.max;
      input.step = item.step;
      input.value = CONFIG[item.key];

      valueOut = document.createElement('output');
      valueOut.className = 'knob-value';
      valueOut.textContent = this._fmt(CONFIG[item.key]);
    }

    input.addEventListener('input', () => {
      let v;
      if (item.type === 'select') v = input.value;
      else if (item.type === 'checkbox') v = input.checked;
      else v = Number(input.value);
      CONFIG[item.key] = v;
      if (valueOut) valueOut.textContent = this._fmt(v);
      if (item.key === 'AUTO_PATTERN_WIDTH') this._reflectAutoPattern();
      if (item.pattern) this.onPatternChange();
      this.onRedraw();
      // Auto width depends on eye separation; keep its readout current.
      if (item.key === 'EYE_SEPARATION_PX') this._reflectAutoPattern();
    });

    this.inputs.set(item.key, { input, valueOut, item });

    wrap.appendChild(name);
    wrap.appendChild(input);
    if (valueOut) wrap.appendChild(valueOut);
    return wrap;
  }

  // Disable manual pattern-width slider while auto is on, and show the
  // derived width in its readout.
  _reflectAutoPattern() {
    const pw = this.inputs.get('PATTERN_WIDTH');
    const auto = !!CONFIG.AUTO_PATTERN_WIDTH;
    if (!pw) return;
    pw.input.disabled = auto;
    if (auto) {
      const eff = effectivePatternWidth();
      if (pw.valueOut) pw.valueOut.textContent = `${eff} (auto)`;
    } else if (pw.valueOut) {
      pw.valueOut.textContent = this._fmt(CONFIG.PATTERN_WIDTH);
    }
  }

  // A small handle at the canvas's bottom-right corner to drag-resize
  // the *resolution* (not just CSS scale).
  _buildResizeHandle() {
    const canvas = this.canvas;
    const parent = canvas.parentElement;
    // Ensure a positioned wrapper so the handle can be absolutely placed.
    const wrapper = document.createElement('div');
    wrapper.className = 'canvas-wrap';
    parent.insertBefore(wrapper, canvas);
    wrapper.appendChild(canvas);

    const handle = document.createElement('div');
    handle.className = 'resize-handle';
    handle.title = 'Drag to resize resolution';
    wrapper.appendChild(handle);

    let dragging = false;
    let startX = 0;
    let startY = 0;
    let startW = 0;
    let startH = 0;
    let cssScaleX = 1;
    let cssScaleY = 1;

    const onDown = (e) => {
      dragging = true;
      const rect = canvas.getBoundingClientRect();
      startX = e.clientX;
      startY = e.clientY;
      startW = canvas.width;
      startH = canvas.height;
      // Ratio of backing-store px to CSS px, so drag distance maps to
      // resolution change 1:1 with the pointer.
      cssScaleX = canvas.width / rect.width;
      cssScaleY = canvas.height / rect.height;
      e.preventDefault();
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    };

    const onMove = (e) => {
      if (!dragging) return;
      const dw = (e.clientX - startX) * cssScaleX;
      const dh = (e.clientY - startY) * cssScaleY;
      const w = Math.round(startW + dw);
      const h = Math.round(startH + dh);
      this.onResize(w, h);
      this.resWidthInput.value = CONFIG.WIDTH;
      this.resHeightInput.value = CONFIG.HEIGHT;
      this._reflectAutoPattern();
    };

    const onUp = () => {
      dragging = false;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };

    handle.addEventListener('mousedown', onDown);
  }

  _fmt(v) {
    if (typeof v === 'boolean') return v ? 'on' : 'off';
    return typeof v === 'number' && !Number.isInteger(v) ? v.toFixed(2) : String(v);
  }

  // Push current CONFIG values back into the inputs (after a reset).
  syncFromConfig() {
    for (const [key, { input, valueOut, item }] of this.inputs) {
      if (item && item.type === 'checkbox') {
        input.checked = !!CONFIG[key];
      } else {
        input.value = CONFIG[key];
      }
      if (valueOut) valueOut.textContent = this._fmt(CONFIG[key]);
    }
    const { w, h } = this.getSize();
    if (this.resWidthInput) this.resWidthInput.value = w;
    if (this.resHeightInput) this.resHeightInput.value = h;
  }

  _toggleFullscreen() {
    const el = this.canvas;
    if (!document.fullscreenElement) {
      (el.requestFullscreen || el.webkitRequestFullscreen).call(el);
    } else {
      (document.exitFullscreen || document.webkitExitFullscreen).call(document);
    }
  }
}
