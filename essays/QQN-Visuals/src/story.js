// Scroll driver: renders section DOM, mounts/unmounts canvases lazily.
export class Story {
  constructor(root, sections, global) {
    this.root = root;
    this.sections = sections;
    this.global = global;
    this.mounted = new Map(); // id -> { teardown }
    this._render();
    this._observe();
  }

  _render() {
    this.sections.forEach((sec) => {
      const el = document.createElement('section');
      el.className = 'section';
      el.id = 'section-' + sec.id;
      el.innerHTML = `
            <div class="prose">${sec.prose}</div>
            <div class="viz-wrap"><div class="viz"><canvas style="height:360px"></canvas></div></div>`;
      this.root.appendChild(el);
      sec._el = el;
    });
  }

  _observe() {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const sec = this.sections.find((s) => s._el === entry.target);
          if (!sec) return;
          if (entry.isIntersecting) this._mount(sec);
          else this._unmount(sec);
        });
      },
      { rootMargin: '100px 0px', threshold: 0.01 }
    );
    this.sections.forEach((s) => io.observe(s._el));
  }

  _mount(sec) {
    if (this.mounted.has(sec.id)) return;
    const canvas = sec._el.querySelector('.viz canvas');
    if (!canvas) {
      console.warn('Story._mount: no canvas found for section', sec.id);
    }
    const ctx = { container: sec._el, canvas, global: this.global };
    try {
      const handle = sec.build(ctx) || {};
      this.mounted.set(sec.id, handle);
      console.debug('Story: mounted section', sec.id);
    } catch (err) {
      console.error('Section build failed:', sec.id, err);
    }
  }

  _unmount(sec) {
    const h = this.mounted.get(sec.id);
    if (h && typeof h.teardown === 'function') {
      try {
        h.teardown();
      } catch (err) {
        console.error('Section teardown failed:', sec.id, err);
      }
    }
    this.mounted.delete(sec.id);
  }
}
