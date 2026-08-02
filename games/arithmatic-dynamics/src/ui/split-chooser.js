// Split chooser dialog. Given a target number value, it offers friendly
// presets, plus a custom "a op b" picker. In 'factor' mode it offers
// multiplicative factorizations instead of additive splits.
// Every option is validated to preserve value before being offered/used.

import { el, clear, on } from '../util/dom.js';

export class SplitChooser {
  constructor(root, { onChoose } = {}) {
    this.root = root;
    this.onChoose = onChoose;
    this._offDoc = null;
    this.hide();
  }

  // mode: 'split' (additive) or 'factor' (multiplicative)
  show(value, mode = 'split') {
    clear(this.root);

    const title = mode === 'factor' ? `Factorize ${value}` : `Break apart ${value}`;
    const panel = el('div', { class: 'chooser-panel' }, [
      el('div', { class: 'chooser-title' }, title),
    ]);

    if (mode === 'factor') {
      // Factorization presets + custom a×b picker.
      const options = factorsFor(value);
      const list = el('div', { class: 'chooser-options' });
      for (const opt of options) {
        list.appendChild(
          el(
            'button',
            {
              class: 'chooser-btn',
              type: 'button',
              onClick: () => {
                this.hide();
                this.onChoose && this.onChoose(opt.into);
              },
            },
            opt.label,
          ),
        );
      }
      panel.appendChild(list);
      panel.appendChild(this._customPicker(value, mode));
    } else {
      // Additive split: a simple slider that picks the first part.
      panel.appendChild(this._sliderSplitter(value));
    }

    panel.appendChild(
      el(
        'button',
        {
          class: 'chooser-cancel',
          type: 'button',
          onClick: () => this.hide(),
        },
        'Cancel',
      ),
    );

    this.root.appendChild(panel);
    this.root.classList.add('visible');

    if (this._offDoc) this._offDoc();
    this._offDoc = on(document, 'pointerdown', (e) => {
      if (!this.root.contains(e.target)) this.hide();
    });
  }
  // Slider-based additive split. The slider chooses the first part `a`
  // (from 1..value-1) and the second part is the remainder `value - a`.
  // It initializes to rounding `value` down to the nearest multiple of 5.
  _sliderSplitter(value) {
    const wrap = el('div', { class: 'chooser-slider' });
    // Default: round down to nearest multiple of 5 (clamped into range).
    let initial = Math.floor(value / 5) * 5;
    if (initial <= 0) initial = Math.floor(value / 2) || 1;
    if (initial >= value) initial = value - 1;
    if (initial < 1) initial = 1;
    const preview = el('div', { class: 'chooser-slider-preview' }, '');
    const slider = el('input', {
      type: 'range',
      class: 'chooser-range',
      min: '1',
      max: String(value - 1),
      step: '1',
      value: String(initial),
    });
    const go = el('button', { class: 'chooser-btn', type: 'button' }, 'Use');
    const update = () => {
      const a = Number(slider.value);
      const b = value - a;
      preview.textContent = `${a} + ${b}`;
    };
    slider.addEventListener('input', update);
    go.addEventListener('click', () => {
      const a = Number(slider.value);
      const b = value - a;
      this.hide();
      this.onChoose && this.onChoose(`${a} + ${b}`);
    });
    update();
    wrap.append(preview, slider, go);
    return wrap;
  }

  _customPicker(value, mode = 'split') {
    const wrap = el('div', { class: 'chooser-custom' });
    const a = el('input', { type: 'number', class: 'chooser-input', value: String(value) });
    const addOps = [el('option', { value: '+' }, '+'), el('option', { value: '-' }, '−')];
    const mulOps = [el('option', { value: '*' }, '×'), el('option', { value: '/' }, '÷')];
    const opSel = el('select', { class: 'chooser-op' }, mode === 'factor' ? mulOps : addOps);
    const b = el('input', { type: 'number', class: 'chooser-input', value: '0' });
    const feedback = el('span', { class: 'chooser-feedback' }, '');
    const go = el('button', { class: 'chooser-btn', type: 'button' }, 'Use');

    const validate = () => {
      const av = Number(a.value);
      const bv = Number(b.value);
      const opv = opSel.value;
      let ok = false;
      if (Number.isFinite(av) && Number.isFinite(bv)) {
        if (opv === '/') {
          ok = bv !== 0 && av % bv === 0 && av / bv === value;
        } else if (opv === '*') {
          ok = av * bv === value;
        } else if (opv === '+') {
          ok = av + bv === value;
        } else if (opv === '-') {
          ok = av - bv === value;
        }
      }
      feedback.textContent = ok ? '✓ equals ' + value : '✗ not ' + value;
      feedback.className = 'chooser-feedback ' + (ok ? 'ok' : 'bad');
      go.disabled = !ok;
      return ok;
    };
    a.addEventListener('input', validate);
    b.addEventListener('input', validate);
    opSel.addEventListener('change', validate);
    go.addEventListener('click', () => {
      if (!validate()) return;
      this.hide();
      this.onChoose && this.onChoose(`${a.value} ${opSel.value} ${b.value}`);
    });
    validate();

    wrap.append(a, opSel, b, feedback, go);
    return wrap;
  }

  hide() {
    this.root.classList.remove('visible');
    clear(this.root);
    if (this._offDoc) {
      this._offDoc();
      this._offDoc = null;
    }
  }
  // Prompt the player to type the answer to a combination. Calls
  // onConfirm(value) with the entered number when submitted.
  askAnswer(question, onConfirm) {
    clear(this.root);
    const panel = el('div', { class: 'chooser-panel' }, [
      el('div', { class: 'chooser-title' }, question),
    ]);
    const input = el('input', {
      type: 'number',
      class: 'chooser-input',
      value: '',
    });
    const feedback = el('span', { class: 'chooser-feedback' }, '');
    const go = el('button', { class: 'chooser-btn', type: 'button' }, 'Check');
    const submit = () => {
      const v = Number(input.value);
      if (!Number.isFinite(v) || input.value === '') {
        feedback.textContent = '✗ enter a number';
        feedback.className = 'chooser-feedback bad';
        return;
      }
      this.hide();
      onConfirm && onConfirm(v);
    };
    go.addEventListener('click', submit);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') submit();
    });
    const row = el('div', { class: 'chooser-custom' }, [input, feedback, go]);
    panel.appendChild(row);
    panel.appendChild(
      el(
        'button',
        {
          class: 'chooser-cancel',
          type: 'button',
          onClick: () => this.hide(),
        },
        'Cancel',
      ),
    );
    this.root.appendChild(panel);
    this.root.classList.add('visible');
    input.focus();
    if (this._offDoc) this._offDoc();
    this._offDoc = on(document, 'pointerdown', (e) => {
      if (!this.root.contains(e.target)) this.hide();
    });
  }
}

// Generate a small set of value-preserving additive split presets.
function presetsFor(value) {
  const out = [];
  const seen = new Set();
  const add = (label, into) => {
    if (seen.has(into)) return;
    seen.add(into);
    out.push({ label, into });
  };

  // Nearest ten ± n.
  const lowerTen = Math.floor(value / 10) * 10;
  const upperTen = lowerTen + 10;
  if (lowerTen !== value && lowerTen > 0) {
    add(`${lowerTen} + ${value - lowerTen}`, `${lowerTen} + ${value - lowerTen}`);
  }
  if (upperTen !== value) {
    add(`${upperTen} − ${upperTen - value}`, `${upperTen} - ${upperTen - value}`);
  }

  // Make a five.
  const lowerFive = Math.floor(value / 5) * 5;
  if (lowerFive !== value && lowerFive > 0) {
    add(`${lowerFive} + ${value - lowerFive}`, `${lowerFive} + ${value - lowerFive}`);
  }

  // Halve (if even).
  if (value % 2 === 0 && value !== 0) {
    add(`${value / 2} + ${value / 2}`, `${value / 2} + ${value / 2}`);
  }

  return out;
}

// Generate multiplicative factorization presets: a × b for each
// non-trivial factor pair.
function factorsFor(value) {
  const out = [];
  const seen = new Set();
  const add = (label, into) => {
    if (seen.has(into)) return;
    seen.add(into);
    out.push({ label, into });
  };

  const av = Math.abs(value);
  if (av > 1) {
    for (let f = 2; f * f <= av; f++) {
      if (av % f === 0) {
        const other = value / f;
        add(`${f} × ${other}`, `${f} * ${other}`);
      }
    }
  }

  return out;
}
