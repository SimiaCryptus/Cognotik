/**
 * ui.js — control panel, playback controls, painting, presets (§7.7, §9).
 *
 * Controls are generated from the schema in config.js, so a new parameter
 * needs no UI code: declare it in SCHEMA and it appears here.
 */

import { SCHEMA, GROUPS, toHashString } from './config.js';
import { STATE_COLORS, MEMBRANE_COLORS } from './renderer.js';

function el(tag, cls, text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
}

export class UI {
  constructor({
    config,
    simulation,
    renderer,
    canvas,
    panelRoot,
    toolbarRoot,
    statusRoot,
    legendRoot,
    presets = [],
  }) {
    this.config = config;
    this.sim = simulation;
    this.renderer = renderer;
    this.canvas = canvas;
    this.panel = panelRoot;
    this.toolbar = toolbarRoot;
    this.statusRoot = statusRoot;
    this.legendRoot = legendRoot;
    this.presets = presets;

    this.controls = new Map();
    this.sections = new Map();
    this._statusFields = {};
    this._statusMode = null;
    this._applying = false;

    this._buildToolbar();
    this._buildStatus();
    this._buildPanel();
    this._bindPainting();
    this._bindKeys();

    this.config.subscribe(() => this.syncFromConfig());
    this.sim.on('running', () => this._updatePlayButton());
    this.sim.on('reset', () => this.updateStatus());

    this.syncFromConfig();
    this._updatePlayButton();
    this.updateStatus();
  }

  // -------------------------------------------------------------- toolbar
  _buildToolbar() {
    this.toolbar.textContent = '';

    this.playButton = el('button', 'primary', 'Play');
    this.playButton.addEventListener('click', () => this.sim.toggle());

    const stepBtn = el('button', null, 'Step');
    stepBtn.addEventListener('click', () => {
      this.sim.pause();
      this.sim.step();
    });

    const resetBtn = el('button', null, 'Reset');
    resetBtn.addEventListener('click', () => this.sim.reset());

    const clearBtn = el('button', null, 'Clear');
    clearBtn.addEventListener('click', () => this.sim.clear());

    const reseedBtn = el('button', null, 'Reseed');
    reseedBtn.addEventListener('click', () => {
      this.config.set('seed', Math.floor(Math.random() * 1000000));
      this.sim.reset();
    });

    const fullscreenBtn = el('button', null, 'Fullscreen');
    fullscreenBtn.addEventListener('click', () => this._toggleFullscreen());
    document.addEventListener('fullscreenchange', () => {
      const active = Boolean(document.fullscreenElement);
      fullscreenBtn.textContent = active ? 'Exit fullscreen' : 'Fullscreen';
    });
    this.fullscreenButton = fullscreenBtn;

    this.toolbar.append(this.playButton, stepBtn, resetBtn, clearBtn, reseedBtn, fullscreenBtn);
  }
  _toggleFullscreen() {
    const stage = this.canvas.closest('.stage') || this.canvas.parentElement;
    if (!stage) return;
    if (!document.fullscreenElement) {
      const request =
        stage.requestFullscreen || stage.webkitRequestFullscreen || stage.msRequestFullscreen;
      if (request) request.call(stage);
    } else {
      const exit =
        document.exitFullscreen || document.webkitExitFullscreen || document.msExitFullscreen;
      if (exit) exit.call(document);
    }
  }

  _updatePlayButton() {
    this.playButton.textContent = this.sim.running ? 'Pause' : 'Play';
  }

  // --------------------------------------------------------------- status
  /** Status read-outs differ per domain (PID error metrics vs. gate census). */
  _statusFieldSpec(mode) {
    if (mode === 'pid') {
      return [
        ['step', 'step'],
        ['rate', 'steps/s'],
        ['active', 'active'],
        ['err', 'mean |e|'],
        ['energy', 'Σe²'],
        ['integral', 'mean I'],
      ];
    }
    const fields = [
      ['step', 'step'],
      ['rate', 'steps/s'],
      ['firing', 'firing'],
      ['refractory', 'refractory'],
      ['meanV', 'mean V'],
    ];
    if (mode === 'pid-homeostat') fields.push(['integral', 'mean I']);
    return fields;
  }

  _buildStatus(mode = this.config.get('mode')) {
    this._statusMode = mode;
    this._statusFields = {};
    const fields = this._statusFieldSpec(mode);
    this.statusRoot.textContent = '';
    for (const [key, label] of fields) {
      const item = el('div', 'stat');
      item.appendChild(el('span', 'stat-label', label));
      const value = el('span', 'stat-value', '–');
      item.appendChild(value);
      this._statusFields[key] = value;
      this.statusRoot.appendChild(item);
    }
  }

  updateStatus() {
    const s = this.sim.stats;
    const f = this._statusFields;
    if (f.step) f.step.textContent = String(s.step);
    if (f.rate) f.rate.textContent = this.sim.running ? this.sim.measuredRate.toFixed(1) : '0.0';
    if (f.active) f.active.textContent = (s.activeFraction * 100).toFixed(1) + '%';
    if (f.err) f.err.textContent = s.meanAbsError.toFixed(3);
    if (f.energy) f.energy.textContent = s.energy.toFixed(0);
    if (f.integral) f.integral.textContent = s.meanIntegral.toFixed(3);
    if (f.firing) f.firing.textContent = ((s.firingFraction || 0) * 100).toFixed(2) + '%';
    if (f.refractory)
      f.refractory.textContent = ((s.refractoryFraction || 0) * 100).toFixed(2) + '%';
    if (f.meanV) f.meanV.textContent = (s.meanV || 0).toFixed(2);
  }

  // ---------------------------------------------------------------- panel
  _buildPanel() {
    this.panel.textContent = '';
    this.sections.clear();
    this.panel.appendChild(this._buildPresetSection());

    for (const group of GROUPS) {
      const keys = Object.keys(SCHEMA).filter((k) => SCHEMA[k].group === group);
      if (!keys.length) continue;
      const section = el('section', 'group');
      const header = el('h2', null, group);
      const body = el('div', 'group-body');
      for (const key of keys) body.appendChild(this._buildControl(key, SCHEMA[key]));
      section.append(header, body);
      this._makeCollapsible(section, header);
      this.sections.set(group, section);
      this.panel.appendChild(section);
    }

    this.errorBox = el('p', 'errors');
    this.panel.appendChild(this.errorBox);
  }
  /** Wire a group header to toggle a `.collapsed` class on its section (§9). */
  _makeCollapsible(section, header) {
    header.classList.add('collapsible');
    header.setAttribute('role', 'button');
    header.setAttribute('tabindex', '0');
    header.setAttribute('aria-expanded', 'true');
    const toggle = () => {
      const collapsed = section.classList.toggle('collapsed');
      header.setAttribute('aria-expanded', String(!collapsed));
    };
    header.addEventListener('click', toggle);
    header.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        toggle();
      }
    });
  }

  _buildPresetSection() {
    const section = el('section', 'group');
    const header = el('h2', null, 'Presets & sharing');
    const body = el('div', 'group-body');

    const select = el('select');
    this.presets.forEach((preset, index) => {
      const option = el('option');
      option.value = String(index);
      option.textContent = preset.name;
      select.appendChild(option);
    });
    body.appendChild(select);

    const description = el(
      'p',
      'hint',
      this.presets.length ? this.presets[0].description || '' : ''
    );
    select.addEventListener('change', () => {
      const preset = this.presets[Number(select.value)];
      description.textContent = preset ? preset.description || '' : '';
    });
    body.appendChild(description);

    const row = el('div', 'row');
    const loadBtn = el('button', 'primary', 'Load preset');
    loadBtn.addEventListener('click', () => {
      const preset = this.presets[Number(select.value)];
      if (!preset) return;
      // Presets that do not name a `mode` are PID-domain presets.
      const patch = { ...preset.config };
      if (patch.mode === undefined) patch.mode = 'pid';
      this.config.patch(patch);
      this.sim.reset();
    });

    const shareBtn = el('button', null, 'Copy share link');
    shareBtn.addEventListener('click', async () => {
      const hash = toHashString(this.config.toJSON());
      history.replaceState(null, '', '#' + hash);
      const url = location.href;
      try {
        await navigator.clipboard.writeText(url);
        shareBtn.textContent = 'Copied!';
      } catch (err) {
        shareBtn.textContent = 'URL updated';
      }
      setTimeout(() => {
        shareBtn.textContent = 'Copy share link';
      }, 1400);
    });
    row.append(loadBtn, shareBtn);
    body.appendChild(row);

    const textarea = el('textarea');
    textarea.rows = 7;
    textarea.spellcheck = false;
    textarea.placeholder = 'Configuration JSON (export / import)';
    body.appendChild(textarea);

    const ioRow = el('div', 'row');
    const exportBtn = el('button', null, 'Export JSON');
    exportBtn.addEventListener('click', () => {
      textarea.value = JSON.stringify(this.config.toJSON(), null, 2);
    });
    const importBtn = el('button', null, 'Import JSON');
    importBtn.addEventListener('click', () => {
      try {
        const parsed = JSON.parse(textarea.value);
        const errors = this.config.loadJSON(parsed);
        this.sim.reset();
        this._showErrors(errors);
      } catch (err) {
        this._showErrors(['could not parse JSON: ' + err.message]);
      }
    });
    ioRow.append(exportBtn, importBtn);
    body.appendChild(ioRow);

    section.append(header, body);
    this._makeCollapsible(section, header);
    return section;
  }

  _buildControl(key, spec) {
    const row = el('div', 'ctl');
    const label = el('label', 'ctl-label', spec.label);
    label.setAttribute('for', 'ctl-' + key);
    row.appendChild(label);

    const field = el('div', 'ctl-field');
    let setValue;

    if (spec.type === 'bool') {
      const box = el('input');
      box.type = 'checkbox';
      box.id = 'ctl-' + key;
      box.addEventListener('change', () => this._push(key, box.checked));
      field.appendChild(box);
      setValue = (v) => {
        box.checked = Boolean(v);
      };
    } else if (spec.type === 'enum') {
      const select = el('select');
      select.id = 'ctl-' + key;
      for (const option of spec.options) {
        const opt = el('option');
        opt.value = String(option);
        opt.textContent =
          (spec.optionLabels && spec.optionLabels[String(option)]) || String(option);
        select.appendChild(opt);
      }
      select.addEventListener('change', () => {
        const match = spec.options.find((o) => String(o) === select.value);
        this._push(key, match);
      });
      field.appendChild(select);
      setValue = (v) => {
        select.value = String(v);
      };
    } else {
      const range = el('input', 'ctl-range');
      range.type = 'range';
      range.id = 'ctl-' + key;
      range.min = spec.min;
      range.max = spec.max;
      range.step = spec.step || 1;

      const number = el('input', 'ctl-num');
      number.type = 'number';
      number.min = spec.min;
      number.max = spec.max;
      number.step = spec.step || 1;

      range.addEventListener('input', () => {
        number.value = range.value;
        this._push(key, Number(range.value));
      });
      number.addEventListener('change', () => {
        range.value = number.value;
        this._push(key, Number(number.value));
      });

      field.append(range, number);
      setValue = (v) => {
        range.value = v;
        number.value = v;
      };
    }

    row.appendChild(field);
    if (spec.hint) row.appendChild(el('div', 'ctl-hint', spec.hint));

    this.controls.set(key, { row, setValue, spec });
    return row;
  }

  _push(key, value) {
    if (this._applying || value === undefined) return;
    this.config.set(key, value);
    this._showErrors(this.config.errors);
  }

  _showErrors(errors) {
    if (!this.errorBox) return;
    this.errorBox.textContent = errors && errors.length ? errors.join(' · ') : '';
  }

  syncFromConfig() {
    const cfg = this.config.all();
    if (this._statusMode !== cfg.mode) this._buildStatus(cfg.mode);
    this._applying = true;
    for (const [key, control] of this.controls) {
      control.setValue(cfg[key]);
      control.row.hidden =
        typeof control.spec.visible === 'function' ? !control.spec.visible(cfg) : false;
    }
    this._applying = false;
    for (const [group, section] of this.sections) {
      section.hidden = !this._groupVisible(group, cfg);
    }
    this._renderLegend();
    this.updateStatus();
  }
  /** Whole-group visibility: the two domains share the panel (§6.1). */
  _groupVisible(group, cfg) {
    if (group === 'Membrane (bioelectrical)') return cfg.mode !== 'pid';
    if (cfg.mode === 'pid') return true;
    if (group === 'Target' || group === 'State expression') return false;
    if (group === 'PID gains') return cfg.mode === 'pid-homeostat';
    return true;
  }

  _renderLegend() {
    const cfg = this.config.all();
    const root = this.legendRoot;
    if (!root) return;
    root.textContent = '';

    const membrane = cfg.mode !== 'pid';
    let names;
    let palette = STATE_COLORS;
    let count = cfg.stateCardinality;

    if (membrane) {
      palette = MEMBRANE_COLORS;
      count = 3;
      names = ['polarized (gate closed)', 'firing (gate open)', 'refractory'];
    } else if (cfg.stateCardinality === 2) {
      names = ['inactive', 'active'];
    } else if (cfg.expression === 'semantic') {
      names = ['inactive', 'integral-dominant (stabilising)', 'P/D-dominant (driving)'];
    } else {
      names = ['state 0', 'state 1', 'state 2'];
    }

    for (let s = 0; s < count; s++) {
      const item = el('div', 'legend-item');
      const swatch = el('span', 'swatch');
      const c = palette[s] || palette[0];
      swatch.style.background = 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')';
      item.append(swatch, el('span', null, names[s]));
      root.appendChild(item);
    }

    if (cfg.overlay !== 'none') {
      const item = el('div', 'legend-item');
      const label =
        cfg.overlay === 'voltage'
          ? 'overlay: V (' + cfg.vMin + ' → ' + cfg.vRest + ' → ' + cfg.vMax + ')'
          : 'overlay: ' + cfg.overlay + ' (blue −, red +), ±' + cfg.overlayScale;
      item.append(el('span', 'swatch ramp'), el('span', null, label));
      root.appendChild(item);
    }
  }

  // -------------------------------------------------------------- painting
  _bindPainting() {
    let painting = false;
    let paintValue = 1;
    let fillStart = null;

    const cellOf = (event) => this.renderer.cellFromEvent(event, this.sim.grid);
    const paintBrush = (cx, cy, value) => {
      const cfg = this.config.all();
      const size = Math.max(1, cfg.brushSize | 0);
      const half = (size - 1) / 2;
      const grid = this.sim.grid;
      const x0 = Math.round(cx - half);
      const y0 = Math.round(cy - half);
      for (let dy = 0; dy < size; dy++) {
        for (let dx = 0; dx < size; dx++) {
          const x = x0 + dx;
          const y = y0 + dy;
          if (x < 0 || y < 0 || x >= grid.width || y >= grid.height) continue;
          this._paint(x, y, value);
        }
      }
    };
    const applyFill = (a, b, value) => {
      const cfg = this.config.all();
      const grid = this.sim.grid;
      const x0 = Math.max(0, Math.min(a.x, b.x));
      const x1 = Math.min(grid.width - 1, Math.max(a.x, b.x));
      const y0 = Math.max(0, Math.min(a.y, b.y));
      const y1 = Math.min(grid.height - 1, Math.max(a.y, b.y));
      const density = Math.max(0, Math.min(1, cfg.fillDensity / 100));
      for (let y = y0; y <= y1; y++) {
        for (let x = x0; x <= x1; x++) {
          let paint;
          switch (cfg.fillPattern) {
            case 'stripesH':
              paint = (y - y0) % 2 === 0;
              break;
            case 'stripesV':
              paint = (x - x0) % 2 === 0;
              break;
            case 'checker':
              paint = (x - x0 + (y - y0)) % 2 === 0;
              break;
            case 'random':
            default:
              paint = Math.random() < density;
              break;
          }
          if (paint) this._paint(x, y, value);
          else if (cfg.fillPattern !== 'random') this._paint(x, y, 0);
        }
      }
    };

    this.canvas.addEventListener('pointerdown', (event) => {
      const cell = cellOf(event);
      if (!cell) return;
      const cfg = this.config.all();
      const erase = event.button === 2 || event.shiftKey;
      if (cfg.mode !== 'pid') {
        paintValue = erase ? 0 : 1;
      } else {
        const current = this.sim.grid.getState(cell.x, cell.y);
        paintValue = erase ? 0 : current > 0 ? 0 : cfg.stateCardinality - 1;
      }
      painting = true;
      try {
        this.canvas.setPointerCapture(event.pointerId);
      } catch (err) {
        /* ignore */
      }
      if (cfg.paintTool === 'fill') {
        fillStart = cell;
      } else {
        paintBrush(cell.x, cell.y, paintValue);
      }
      event.preventDefault();
    });

    this.canvas.addEventListener('pointermove', (event) => {
      if (!painting) return;
      const cell = cellOf(event);
      if (!cell) return;
      const cfg = this.config.all();
      if (cfg.paintTool !== 'fill') paintBrush(cell.x, cell.y, paintValue);
    });

    const stop = (event) => {
      if (painting && this.config.get('paintTool') === 'fill' && fillStart) {
        const cell = cellOf(event) || fillStart;
        applyFill(fillStart, cell, paintValue);
      }
      painting = false;
      fillStart = null;
    };
    this.canvas.addEventListener('pointerup', stop);
    this.canvas.addEventListener('pointercancel', stop);
    this.canvas.addEventListener('pointerleave', stop);
    this.canvas.addEventListener('contextmenu', (event) => event.preventDefault());
  }
  /**
   * Domain-aware paint dispatch. In PID mode this writes discrete states; in
   * membrane mode it writes the stimulus field, the potential, or the clamp
   * (bioelectrical.md §7 "UI additions"). `value === 0` means erase.
   */
  _paint(x, y, value) {
    const cfg = this.config.all();
    if (cfg.mode === 'pid') {
      this.sim.paintCell(x, y, value);
      return;
    }
    const erase = !value;
    switch (cfg.membraneTool) {
      case 'clamp':
        this.sim.paintClamp(x, y, !erase, cfg.clampVoltage);
        break;
      case 'depolarize':
        this.sim.paintVoltage(
          x,
          y,
          erase
            ? cfg.vRest
            : cfg.polarity === 'hyperpolarizing'
              ? cfg.vThreshold - 1
              : cfg.vThreshold + 1
        );
        break;
      case 'stimulus':
      default:
        this.sim.paintStimulus(x, y, erase ? 0 : cfg.stimulusAmplitude);
        break;
    }
  }

  _bindKeys() {
    window.addEventListener('keydown', (event) => {
      const tag = (event.target && event.target.tagName) || '';
      if (/^(INPUT|SELECT|TEXTAREA)$/.test(tag)) return;
      switch (event.key) {
        case ' ':
          event.preventDefault();
          this.sim.toggle();
          break;
        case 's':
        case 'S':
          this.sim.pause();
          this.sim.step();
          break;
        case 'r':
        case 'R':
          this.sim.reset();
          break;
        case 'c':
        case 'C':
          this.sim.clear();
          break;
        default:
          break;
      }
    });
  }
}
