import {h, clear} from '../../core/dom.js';
  import {fs} from '../../core/fsclient.js';
  import {ui} from '../../core/ui.js';
  import {bus} from '../../core/bus.js';
import {renderMarkdown, enhanceMarkdown} from '../../core/markdown.js';
  import {publicUrl} from '../../core/urls.js';
  import {basename, dirname, join} from '../../core/paths.js';

  /**
   * Rendered view of a Markdown document.
   *
   * Reached through a *virtual* tab whose stat carries `previewKind: 'markdown'`
   * (see `ui.openPreview`), so the same file can be open twice: once for editing
   * (Monaco) and once for reading. Relative links and images resolve against the
   * document's own folder, through the clean file URL.
  *
  * ```mermaid fences become diagrams and TeX spans ($…$, $$…$$, \(…\), \[…\])
  * are typeset by MathJax — both in a second pass, once the rendered fragment is
  * in the document, because both libraries need real layout to measure.
   */
  export class MarkdownPreview {
      static id = 'markdown-preview';

      static canOpen(stat) {
          return stat?.previewKind === 'markdown';
      }

      constructor(ctx) {
          this.ctx = ctx;
          this.path = ctx.tab.stat.sourcePath || ctx.tab.path;
         /* Guards against a slow render landing after a newer one (reload/watch). */
         this.renderToken = 0;
          this.body = h('div', {class: 'fs-markdown__body'});
          this.scroller = h('article', {
              class: 'fs-markdown', tabindex: '0', 'aria-label': `Preview of ${basename(this.path)}`,
          }, [this.body]);
          this.status = h('span', {class: 'fs-preview__status', role: 'status'});
          this.el = h('div', {class: 'fs-preview'}, [
              h('div', {class: 'fs-preview__toolbar', role: 'toolbar', 'aria-label': 'Markdown preview'}, [
                  h('span', {text: basename(this.path)}),
                  h('span', {style: {flex: '1'}}),
                  this.status,
                  h('button', {type: 'button', text: '⟳ Reload', onclick: () => this.reload()}),
                  h('button', {
                      type: 'button', text: '✎ Edit',
                      onclick: () => ui.openPath(this.path, {pinned: true}),
                  }),
                  h('a', {href: publicUrl(this.path), target: '_blank', rel: 'noopener', text: '↗ Source'}),
              ]),
              this.scroller,
          ]);
          /* Re-render whenever the document changes on disk. */
          this.offEvent = bus.on('fs:event', (event) => {
              if (event?.path === this.path) this.reload();
          });
      }

      async load() {
          await this.reload();
      }

      async reload() {
         const token = ++this.renderToken;
          this.status.textContent = 'Rendering…';
          let text = '';
          try {
              text = (await fs.readText(this.path)).text ?? '';
          } catch (error) {
             if (token !== this.renderToken) return;
              clear(this.body);
              this.status.textContent = '';
              this.body.appendChild(h('p', {text: `Could not read ${basename(this.path)}: ${error?.message || error}`}));
              return;
          }
         if (token !== this.renderToken) return;
          try {
              const fragment = await renderMarkdown(text, {resolveUrl: (value) => this.resolveUrl(value)});
             if (token !== this.renderToken) return;
              clear(this.body);
              this.body.appendChild(fragment);
          } catch (error) {
              /* No lib/marked.min.js (air-gapped install?): show the source. */
              console.warn('markdown renderer unavailable', error);
             if (token !== this.renderToken) return;
              clear(this.body);
              this.body.append(
                  h('p', {class: 'fs-preview__status', text: '/lib/marked.min.js is unavailable — showing the source.'}),
                  h('pre', {text}),
              );
             this.status.textContent = '';
             return;
          }
         /* Diagrams and formulas: additive, and never fatal — a missing library
            leaves the fence/TeX visible as source and says so in the toolbar. */
         let warnings = [];
         try {
             ({warnings = []} = (await enhanceMarkdown(this.body)) || {});
         } catch (error) {
             console.warn('markdown enhancement failed', error);
         }
         if (token !== this.renderToken) return;
         this.status.textContent = warnings.join(' · ');
      }

      /** Relative links/images resolve against the document's folder. */
      resolveUrl(value) {
          const raw = String(value ?? '').trim();
          const [pathPart, hash] = raw.split('#');
          if (!pathPart) return raw;
          const absolute = pathPart.startsWith('/') ? pathPart : join(dirname(this.path), pathPart);
          return publicUrl(absolute) + (hash ? `#${hash}` : '');
      }

      focus() {
          this.scroller.focus();
      }

      dispose() {
         /* Cancels any render still in flight. */
         this.renderToken++;
          this.offEvent?.();
      }
  }