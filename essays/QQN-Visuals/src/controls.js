// Builds the global control panel and dispatches shared events.
import { LANDSCAPES, LANDSCAPE_KEYS } from './landscape.js';
import { Scene2D } from './scene2d.js';

export class GlobalControls extends EventTarget {
  constructor() {
    super();
    this.landscapeKey = 'rosenbrock';
    this.start = LANDSCAPES.rosenbrock.start.slice();
    this.seed = 1;
    this.layers = { gradient: true, oracle: true, path: true, bracket: true };
    this._wire();
  }

  _emit(type, detail) {
    this.dispatchEvent(new CustomEvent(type, { detail }));
  }

  _wire() {
    // panel toggle
    const toggle = document.getElementById('global-panel-toggle');
    const body = document.getElementById('global-panel-body');
    toggle.addEventListener('click', () => {
      const collapsed = body.classList.toggle('collapsed');
      toggle.setAttribute('aria-expanded', String(!collapsed));
    });

    // landscape thumbnails
    const thumbs = document.getElementById('landscape-thumbs');
    LANDSCAPE_KEYS.forEach((key) => {
      const c = document.createElement('canvas');
      c.title = LANDSCAPES[key].label;
      c.dataset.key = key;
      if (key === this.landscapeKey) c.classList.add('selected');
      thumbs.appendChild(c);
      // draw thumbnail once the element has been laid out and has a size.
      const drawThumb = (attempt = 0) => {
        const rect = c.getBoundingClientRect();
        if ((rect.width < 2 || rect.height < 2) && attempt < 10) {
          // Not laid out yet; retry on the next frame.
          requestAnimationFrame(() => drawThumb(attempt + 1));
          return;
        }
        try {
          const sc = new Scene2D(c, LANDSCAPES[key].domain);
          sc.clear();
          sc.drawContours(LANDSCAPES[key], { levels: 8 });
        } catch (err) {
          console.error('Thumbnail draw failed for', key, err);
        }
      };
      requestAnimationFrame(() => drawThumb());
      c.addEventListener('click', () => {
        [...thumbs.children].forEach((ch) => ch.classList.remove('selected'));
        c.classList.add('selected');
        this.landscapeKey = key;
        this.start = LANDSCAPES[key].start.slice();
        this._syncStartInputs();
        this._emit('landscape', { key });
      });
    });

    // start inputs
    this.sx = document.getElementById('start-x');
    this.sy = document.getElementById('start-y');
    this._syncStartInputs();
    const onStart = () => {
      this.start = [parseFloat(this.sx.value), parseFloat(this.sy.value)];
      this._emit('start', { start: this.start });
    };
    this.sx.addEventListener('change', onStart);
    this.sy.addEventListener('change', onStart);

    // transport
    document
      .getElementById('transport-step')
      .addEventListener('click', () => this._emit('transport', { action: 'step' }));
    document
      .getElementById('transport-play')
      .addEventListener('click', () => this._emit('transport', { action: 'play' }));
    document
      .getElementById('transport-reset')
      .addEventListener('click', () => this._emit('transport', { action: 'reset' }));

    // layers
    ['gradient', 'oracle', 'path', 'bracket'].forEach((k) => {
      const el = document.getElementById('layer-' + k);
      el.addEventListener('change', () => {
        this.layers[k] = el.checked;
        this._emit('layers', { layers: this.layers });
      });
    });

    // seed
    const seed = document.getElementById('seed');
    const seedVal = document.getElementById('seed-val');
    seed.addEventListener('input', () => {
      this.seed = parseInt(seed.value, 10);
      seedVal.textContent = this.seed;
      this._emit('seed', { seed: this.seed });
    });
  }

  _syncStartInputs() {
    if (this.sx) this.sx.value = this.start[0].toFixed(2);
    if (this.sy) this.sy.value = this.start[1].toFixed(2);
  }

  setStart(start) {
    this.start = start.slice();
    this._syncStartInputs();
    this._emit('start', { start: this.start });
  }
}
