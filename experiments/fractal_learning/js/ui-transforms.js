// UI for the transforms inspector, including per-transform freeze toggles.

import { specRad } from './view.js';

export class TransformsPanel {
  constructor(rootEl, model, { onChange } = {}) {
    this.root = rootEl;
    this.model = model;
    this.onChange = onChange || (() => {});
  }

  render() {
    const root = this.root;
    root.innerHTML = '';
    const ts = this.model.readTransforms();
    for (let k = 0; k < ts.length; k++) {
      const t = ts[k];
      const det = (t.A[0] * t.A[3] - t.A[1] * t.A[2]).toFixed(3);
      const sr = specRad(t.A).toFixed(3);
      const frob = Math.sqrt(t.A.reduce((a, v) => a + v * v, 0)).toFixed(3);

      const card = document.createElement('div');
      card.className = 'tf' + (t.frozen ? ' frozen' : '');
      card.innerHTML = `
            <div class="tf-head">
              <div class="left">
                <b>T${k + 1}</b>
                <span class="badge ${t.frozen ? 'frozen' : ''}">${t.frozen ? '❄ frozen' : 'active'}</span>
              </div>
              <div class="right">
                <label class="checkbox" title="Hold this transform constant during optimization">
                  <input type="checkbox" data-act="freeze" data-k="${k}" ${t.frozen ? 'checked' : ''}/>
                  <span class="small">freeze</span>
                </label>
              </div>
            </div>
            <div class="matrix-row">
              <input data-k="${k}" data-r="A" data-i="0" value="${t.A[0].toFixed(3)}"/>
              <input data-k="${k}" data-r="A" data-i="1" value="${t.A[1].toFixed(3)}"/>
              <input data-k="${k}" data-r="b" data-i="0" value="${t.b[0].toFixed(3)}"/>
            </div>
            <div class="matrix-row">
              <input data-k="${k}" data-r="A" data-i="2" value="${t.A[2].toFixed(3)}"/>
              <input data-k="${k}" data-r="A" data-i="3" value="${t.A[3].toFixed(3)}"/>
              <input data-k="${k}" data-r="b" data-i="1" value="${t.b[1].toFixed(3)}"/>
            </div>
            <div class="meta small">
              <span>det=${det}</span>
              <span>σ̂≈${sr}</span>
              <span>||A||_F=${frob}</span>
            </div>
            <div class="tf-actions">
              <button data-act="reset" data-k="${k}">reset</button>
              <button data-act="identity" data-k="${k}">→ I·½</button>
              <button data-act="zero-b" data-k="${k}">b=0</button>
            </div>
          `;
      root.appendChild(card);
    }

    // Wire inputs.
    root.querySelectorAll('input[type="text"], input:not([type])').forEach((inp) => {
      if (inp.type === 'checkbox') return;
      inp.addEventListener('change', (e) => {
        const k = +e.target.dataset.k;
        const r = e.target.dataset.r;
        const i = +e.target.dataset.i;
        const v = parseFloat(e.target.value);
        if (!isNaN(v)) {
          const arr = this.model.transforms[k][r].arraySync();
          arr[i] = v;
          this.model.transforms[k][r].assign(tf.tensor1d(arr));
          this.onChange({ type: 'value', k });
        }
      });
    });

    // Freeze toggles.
    root.querySelectorAll('input[type="checkbox"][data-act="freeze"]').forEach((inp) => {
      inp.addEventListener('change', (e) => {
        const k = +e.target.dataset.k;
        this.model.setFrozen(k, e.target.checked);
        this.onChange({ type: 'freeze', k });
      });
    });

    // Actions.
    root.querySelectorAll('button[data-act]').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const k = +e.target.dataset.k;
        const act = e.target.dataset.act;
        const t = this.model.transforms[k];
        if (act === 'reset') {
          t.A.assign(tf.tensor1d([0.5, 0, 0, 0.5]));
          t.b.assign(tf.tensor1d([0, 0]));
        } else if (act === 'identity') {
          t.A.assign(tf.tensor1d([0.5, 0, 0, 0.5]));
        } else if (act === 'zero-b') {
          t.b.assign(tf.tensor1d([0, 0]));
        }
        this.onChange({ type: 'value', k });
      });
    });
  }
}
