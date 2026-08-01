import {h} from '../../core/dom.js';
  
  /**
   * Renders a document (HTML, SVG, PDF…) inside the normal tabbed editor area.
   *
   * It is reached through a *virtual* tab whose stat carries `previewUrl`
   * (see `ui.openPreview`), so the very same file can be open twice: once for
   * editing (Monaco) and once for viewing. The iframe is sandboxed without
  * Sandboxing note
  * ---------------
  * The frame keeps `allow-same-origin` by default. Dropping it puts the frame
  * in an *opaque* origin, which the browser treats as cross-site: session
  * cookies are no longer sent with the requests for the peer .css/.js/.png
  * files (SameSite=Lax/Strict), same-origin `fetch()` is blocked by CORS and
  * `contentWindow` is inaccessible — i.e. the page renders unstyled.
  *
  * Because `allow-scripts` + `allow-same-origin` lets a same-origin document
  * remove its own sandbox, real isolation must come from serving previews
  * from a *separate origin* (e.g. `preview.<host>` or a random subdomain).
  * When that is the case the flag is harmless, and callers that really want
  * the old opaque-origin behaviour can set `stat.previewIsolated = true`.
   */
  export class HtmlPreview {
      static id = 'preview';
     /** Flags that are always applied. */
     static BASE_SANDBOX = [
         'allow-scripts',
         'allow-forms',
         'allow-popups',
         'allow-modals',
         'allow-downloads',
     ];
     /** Query parameters commonly used by path-carrying preview endpoints. */
     static PATH_PARAMS = ['path', 'file', 'f', 'src', 'p'];
  

      static canOpen(stat) {
          return !!stat?.previewUrl;
      }
     /**
      * Relative `href`/`src` inside the previewed document are resolved against
      * the *iframe URL*. A query-shaped endpoint such as
      * `/api/preview?path=docs/index.html` therefore turns `style.css` into
      * `/api/style.css` → 404.
      *
      * If the caller sets `stat.previewPathStyle` we rewrite the URL into a
      * path-shaped one (`/api/preview/docs/index.html`) so the directory of the
      * document mirrors the workspace layout; otherwise we only warn, since
      * rewriting would break servers that only understand the query form.
      */
     static resolveUrl(stat) {
         const url = new URL(stat.previewUrl, location.href);
         const key = HtmlPreview.PATH_PARAMS.find((k) => url.searchParams.has(k));
         if (!key) return url.href;
         if (stat.previewPathStyle) {
             const rel = url.searchParams.get(key).replace(/^\/+/, '');
             const dir = url.pathname.replace(/\/+$/, '');
             url.searchParams.delete(key);
             const rest = url.searchParams.toString();
             return new URL(`${dir}/${rel}${rest ? `?${rest}` : ''}${url.hash}`, url.origin).href;
         }
         console.warn(
             `[HtmlPreview] "${url.href}" carries the document path in a query parameter; ` +
             'relative CSS/JS/images will resolve against ' + `"${url.pathname.replace(/[^/]*$/, '')}" ` +
             'and 404. Serve previews from a path-shaped URL, emit a <base> header/tag, ' +
             'or set stat.previewPathStyle = true.',
         );
         return url.href;
     }
  

      constructor(ctx) {
          this.ctx = ctx;
         const stat = ctx.tab.stat;
         const source = stat.sourcePath || ctx.tab.name;
         this.url = HtmlPreview.resolveUrl(stat);

         const sandbox = [...HtmlPreview.BASE_SANDBOX];
         if (!stat.previewIsolated) sandbox.unshift('allow-same-origin');

          this.frame = h('iframe', {
              class: 'fs-preview__frame', src: this.url, title: `Preview of ${source}`,
             sandbox: sandbox.join(' '),
             // keep the Referer on same-origin asset/API requests the page makes
             referrerpolicy: 'same-origin',
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
         // Re-assigning the *same* src is a no-op in some browsers; bounce
         // through about:blank so the document is really re-fetched.
         try {
             this.frame.contentWindow?.location.reload();
             return;
         } catch {
             /* opaque origin (previewIsolated) — fall through */
         }
         this.frame.src = 'about:blank';
         requestAnimationFrame(() => { this.frame.src = this.url; });
      }
  
      focus() {
          this.frame.focus();
      }
  
      dispose() {
          this.frame.src = 'about:blank';
      }
  }