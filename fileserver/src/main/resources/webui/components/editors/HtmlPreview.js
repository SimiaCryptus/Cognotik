import {h} from '../../core/dom.js';
  
  /**
   * Renders a document (HTML, SVG, PDF…) inside the normal tabbed editor area.
   *
   * It is reached through a *virtual* tab whose stat carries `previewUrl`
   * (see `ui.openPreview`), so the very same file can be open twice: once for
   * editing (Monaco) and once for viewing. The iframe is sandboxed without
   * `allow-same-origin`, so a previewed page cannot reach the workspace's
   * storage or DOM; relative CSS/JS/images still load normally.
   */
  export class HtmlPreview {
      static id = 'preview';
  
      static canOpen(stat) {
          return !!stat?.previewUrl;
      }
  
      constructor(ctx) {
          this.ctx = ctx;
          const source = ctx.tab.stat.sourcePath || ctx.tab.name;
          this.url = ctx.tab.stat.previewUrl;
          this.frame = h('iframe', {
              class: 'fs-preview__frame', src: this.url, title: `Preview of ${source}`,
              sandbox: 'allow-scripts allow-forms allow-popups allow-modals',
          });
          this.el = h('div', {class: 'fs-preview'}, [
              h('div', {class: 'fs-preview__toolbar', role: 'toolbar', 'aria-label': 'Preview'}, [
                  h('span', {text: source}),
                  h('span', {style: {flex: '1'}}),
                  h('button', {type: 'button', text: '⟳ Reload', onclick: () => this.reload()}),
                  h('a', {href: this.url, target: '_blank', rel: 'noopener', text: '↗ New browser tab'}),
              ]),
              this.frame,
          ]);
      }
  
      reload() {
          this.frame.src = this.url;
      }
  
      focus() {
          this.frame.focus();
      }
  
      dispose() {
          this.frame.src = 'about:blank';
      }
  }