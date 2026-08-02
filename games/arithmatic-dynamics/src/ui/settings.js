// Settings menu — configures game mode and puzzle constraints.
//
// Options:
//   allowMultiply : boolean  (enable ×/÷)
//   difficulty    : 'easy' | 'medium' | 'hard'
//
// Emits changes through the onChange callback with the full settings.

import { el, clear, on } from '../util/dom.js';

export const DEFAULT_SETTINGS = {
  allowMultiply: true,
  difficulty: 'easy',
  minTerm: 1,
  maxTerm: 30,
  ops: 2,
  // Custom-difficulty parameters (used when difficulty === 'custom').
  customThreshold: 10,
  customFactors: [2, 5],
};

export class SettingsMenu {
  constructor(root, { onChange, settings } = {}) {
    this.root = root;
    this.onChange = onChange;
    this.settings = { ...DEFAULT_SETTINGS, ...(settings || {}) };
    this._offDoc = null;
    this._renderButton();
  }

  _renderButton() {
    clear(this.root);
    this._toggle = el(
      'button',
      {
        class: 'tool-btn',
        type: 'button',
        onClick: (e) => {
          e.stopPropagation();
          this.toggle();
        },
      },
      '⚙ Settings',
    );
    this.root.appendChild(this._toggle);
  }

  toggle() {
    if (this._panel) {
      this.close();
    } else {
      this.open();
    }
  }

  open() {
    const panel = el('div', { class: 'settings-panel' });

    // Multiply/divide toggle.
    const mulChk = el('input', {
      type: 'checkbox',
      id: 'settings-allow-multiply',
      checked: this.settings.allowMultiply,
    });
    mulChk.addEventListener('change', () => {
      this.settings.allowMultiply = mulChk.checked;
      this._emit();
    });
    const mulRow = el('label', { class: 'settings-row', for: 'settings-allow-multiply' }, [
      mulChk,
      el('span', {}, ' Enable × ÷'),
    ]);

    // Difficulty selector.
    const diffSel = el(
      'select',
      {
        class: 'settings-difficulty',
        onChange: () => {
          this.settings.difficulty = diffSel.value;
          this._emit();
        },
      },
      [
        el('option', { value: 'easy' }, 'Easy — any combination'),
        el('option', { value: 'medium' }, 'Medium — each < 5 or 2·5 factorable'),
        el('option', { value: 'hard' }, 'Hard — type every answer'),
        el('option', { value: 'custom' }, 'Custom — each < X or factorable'),
      ],
    );
    diffSel.value = this.settings.difficulty;
    const diffRow = el('div', { class: 'settings-row' }, [
      el('span', { class: 'settings-label' }, 'Difficulty:'),
      diffSel,
    ]);
    // Custom-difficulty parameters: threshold + allowed factors.
    const thresholdInput = el('input', {
      type: 'number',
      class: 'settings-input',
      id: 'settings-custom-threshold',
      value: String(this.settings.customThreshold),
      min: '1',
    });
    const factorsInput = el('input', {
      type: 'text',
      class: 'settings-input',
      id: 'settings-custom-factors',
      value: (this.settings.customFactors || []).join(', '),
    });
    const parseFactors = (str) =>
      String(str)
        .split(/[\s,]+/)
        .map((s) => Math.floor(Number(s)))
        .filter((n) => Number.isFinite(n) && n > 1);
    const syncCustom = () => {
      const t = Math.max(1, Math.floor(Number(thresholdInput.value) || 1));
      thresholdInput.value = String(t);
      this.settings.customThreshold = t;
      const f = parseFactors(factorsInput.value);
      this.settings.customFactors = f.length ? f : [2, 5];
      this._emit();
    };
    thresholdInput.addEventListener('change', syncCustom);
    factorsInput.addEventListener('change', syncCustom);
    const customRow = el('div', { class: 'settings-row', id: 'settings-custom-row' }, [
      el('span', { class: 'settings-label' }, 'Custom:'),
      el('span', {}, ' below '),
      thresholdInput,
      el('span', {}, ' or factors '),
      factorsInput,
    ]);
    const updateCustomVisibility = () => {
      customRow.style.display = diffSel.value === 'custom' ? '' : 'none';
    };
    diffSel.addEventListener('change', updateCustomVisibility);
    updateCustomVisibility();

    // Min / max spawned number.
    const minInput = el('input', {
      type: 'number',
      class: 'settings-input',
      id: 'settings-min-term',
      value: String(this.settings.minTerm),
      min: '1',
    });
    const maxInput = el('input', {
      type: 'number',
      class: 'settings-input',
      id: 'settings-max-term',
      value: String(this.settings.maxTerm),
      min: '1',
    });
    const clampRange = () => {
      const mn = Math.max(1, Math.floor(Number(minInput.value) || 1));
      let mx = Math.max(1, Math.floor(Number(maxInput.value) || 1));
      if (mn > mx) mx = mn;
      minInput.value = String(mn);
      maxInput.value = String(mx);
      this.settings.minTerm = mn;
      this.settings.maxTerm = mx;
      this._emit();
    };
    minInput.addEventListener('change', clampRange);
    maxInput.addEventListener('change', clampRange);
    const rangeRow = el('div', { class: 'settings-row' }, [
      el('span', { class: 'settings-label' }, 'Numbers:'),
      minInput,
      el('span', {}, ' to '),
      maxInput,
    ]);

    // Number of operations to spawn.
    const opsInput = el('input', {
      type: 'number',
      class: 'settings-input',
      id: 'settings-ops',
      value: String(this.settings.ops),
      min: '1',
      max: '8',
    });
    opsInput.addEventListener('change', () => {
      const n = Math.max(1, Math.min(8, Math.floor(Number(opsInput.value) || 1)));
      opsInput.value = String(n);
      this.settings.ops = n;
      this._emit();
    });
    const opsRow = el('div', { class: 'settings-row' }, [
      el('span', { class: 'settings-label' }, 'Operations:'),
      opsInput,
    ]);

    panel.append(
      el('div', { class: 'settings-title' }, 'Settings'),
      mulRow,
      diffRow,
      customRow,
      rangeRow,
      opsRow,
    );
    this.root.appendChild(panel);
    this._panel = panel;

    if (this._offDoc) this._offDoc();
    this._offDoc = on(document, 'pointerdown', (e) => {
      if (!this.root.contains(e.target)) this.close();
    });
  }

  close() {
    if (this._panel) {
      this._panel.remove();
      this._panel = null;
    }
    if (this._offDoc) {
      this._offDoc();
      this._offDoc = null;
    }
  }

  getSettings() {
    return { ...this.settings };
  }

  _emit() {
    this.onChange && this.onChange(this.getSettings());
  }
}
