/**
   * Markdown rendering.
   *
   * `marked` is loaded from the sibling `/lib/` directory (a plain UMD bundle),
   * resolved against this module's URL so it works under any mount point. Mermaid
   * (diagrams) and MathJax (formulas) are loaded the same way, but only when a
   * document actually contains a ```mermaid fence or a TeX span — a reader that
   * needs neither pays for neither.
   *
   * Monaco ships an AMD loader that owns the global `define`. A UMD bundle that
   * sees `define.amd` registers an anonymous AMD module — which Monaco's loader
   * rejects — and never touches `window.marked`. The AMD globals are therefore
   * hidden for the (synchronous) evaluation of the bundle only.
   *
   * The HTML `marked` produces is *never* trusted: it is parsed in an inert
   * document, stripped of scripts/handlers/dangerous URLs, and only then
   * adopted into the live DOM. The same holds for the SVG Mermaid answers with.
   */
  const lib = (name) => new URL(`/lib/${name}`, import.meta.url).href;
  const SRC = lib('marked.min.js');
  const MERMAID_SRC = lib('mermaid.min.js');
  const MATHJAX_SRC = lib('mathjax/tex-mml-chtml.js');
  const AMD_GLOBALS = ['define', 'require', 'module', 'exports'];

  let loading = null;
  let mermaidLoading = null;
  let mermaidTheme = null;
  let mathjaxLoading = null;

  /** Evaluates a UMD bundle with the AMD globals hidden (see the note above). */
  async function evalBundle(src) {
      const response = await fetch(src, {credentials: 'same-origin'});
      if (!response.ok) throw new Error(`${src} could not be fetched (${response.status})`);
      const source = await response.text();
      const saved = AMD_GLOBALS.map((name) => [name, window[name]]);
      for (const name of AMD_GLOBALS) window[name] = undefined;
      try {
          const script = document.createElement('script');
          script.textContent = `${source}\n//# sourceURL=${src}`;
          document.head.appendChild(script);
          script.remove();
      } finally {
          for (const [name, value] of saved) window[name] = value;
      }
  }

  export function loadMarked() {
      if (window.marked) return Promise.resolve(window.marked);
      if (loading) return loading;
      loading = (async () => {
          await evalBundle(SRC);
          if (!window.marked) throw new Error('/lib/marked.min.js loaded but exported nothing');
          return window.marked;
      })().catch((error) => {
          loading = null;
          throw error;
      });
      return loading;
  }

  /** The workspace theme, as Mermaid and the diagram palette understand it. */
  function darkMode() {
      const root = document.documentElement;
      const theme = root.dataset.theme || root.getAttribute('data-theme') || '';
      if (/dark/i.test(theme)) return true;
      if (/light/i.test(theme)) return false;
      return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }

  /** Re-initialises Mermaid when the workspace switched theme since the last render. */
  function syncMermaidTheme(mermaid) {
      const theme = darkMode() ? 'dark' : 'default';
      if (theme === mermaidTheme) return;
      mermaidTheme = theme;
      mermaid.initialize({
          startOnLoad: false,
          /* 'strict' sanitises labels; no foreignObject keeps the SVG self-contained. */
          securityLevel: 'strict',
          htmlLabels: false,
          flowchart: {htmlLabels: false, useMaxWidth: true},
          sequence: {useMaxWidth: true},
          theme,
          fontFamily: 'inherit',
      });
  }

  export function loadMermaid() {
      if (mermaidLoading) return mermaidLoading;
      mermaidLoading = (async () => {
          if (!window.mermaid) await evalBundle(MERMAID_SRC);
          /* Some builds export the ESM default under `window.mermaid.default`. */
          const mermaid = window.mermaid?.initialize ? window.mermaid : window.mermaid?.default;
          if (!mermaid?.initialize) throw new Error('/lib/mermaid.min.js loaded but exported nothing');
          syncMermaidTheme(mermaid);
          return mermaid;
      })().catch((error) => {
          mermaidLoading = null;
          throw error;
      });
      return mermaidLoading;
  }

  /**
   * MathJax is loaded through a real <script src> rather than an eval'd bundle:
   * v3 derives its own font/component base path from `document.currentScript.src`.
   */
  export function loadMathJax() {
      if (window.MathJax?.typesetPromise) return Promise.resolve(window.MathJax);
      if (mathjaxLoading) return mathjaxLoading;
      mathjaxLoading = new Promise((resolve, reject) => {
          window.MathJax = {
              tex: {
                  inlineMath: [['$', '$'], ['\\(', '\\)']],
                  displayMath: [['$$', '$$'], ['\\[', '\\]']],
                  processEscapes: true,
                  processEnvironments: true,
              },
              options: {
                  /* Typesetting is scoped to .fs-math spans anyway; belt and braces. */
                  skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'],
                  enableMenu: false,
              },
              chtml: {
                  /* Ship /lib/mathjax/output/chtml/fonts/woff-v2 for the real glyphs;
                     without it the browser falls back to a local serif face. */
                  fontURL: lib('mathjax/output/chtml/fonts/woff-v2'),
              },
              startup: {typeset: false},
          };
          const script = document.createElement('script');
          script.src = MATHJAX_SRC;
          script.async = true;
          script.onload = () => {
              (window.MathJax?.startup?.promise || Promise.resolve())
                  .then(() => resolve(window.MathJax))
                  .catch(reject);
          };
          script.onerror = () => reject(new Error(`${MATHJAX_SRC} could not be loaded`));
          document.head.appendChild(script);
      }).catch((error) => {
          mathjaxLoading = null;
          throw error;
      });
      return mathjaxLoading;
  }

  /* ------------------------------------------------------------------- math */
  /**
   * Markdown owns `_`, `*` and `\` — all of which are TeX syntax — so formulas
   * are lifted out *before* `marked` sees them and put back as inert spans
   * afterwards. Fenced and inline code is copied through untouched: a `$` in a
   * shell snippet is a prompt, not mathematics.
   */
  const PLACEHOLDER = (index) => `@@FSMATH${index}@@`;
  const PLACEHOLDER_RE = /@@FSMATH(\d+)@@/g;

  export function maskMath(source) {
      const text = String(source ?? '');
      const math = [];
      let out = '';
      let i = 0;
      const push = (raw, display) => {
          out += PLACEHOLDER(math.length);
          math.push({raw, display});
      };
      while (i < text.length) {
          const ch = text[i];
          /* Fenced code block. */
          if ((ch === '`' || ch === '~') && (i === 0 || text[i - 1] === '\n')) {
              const fence = /^([`~]{3,})[^\n]*\n?/.exec(text.slice(i));
              if (fence) {
                  const rest = text.slice(i + fence[0].length);
                  const close = new RegExp(`^[ \\t]*${fence[1][0]}{${fence[1].length},}[ \\t]*$`, 'm').exec(rest);
                  const end = close ? i + fence[0].length + close.index + close[0].length : text.length;
                  out += text.slice(i, end);
                  i = end;
                  continue;
              }
          }
          /* Inline code span. */
          if (ch === '`') {
              const run = /^`+/.exec(text.slice(i))[0];
              const close = text.indexOf(run, i + run.length);
              const end = close === -1 ? i + run.length : close + run.length;
              out += text.slice(i, end);
              i = end;
              continue;
          }
          if (ch === '\\' && (text[i + 1] === '(' || text[i + 1] === '[')) {
              const display = text[i + 1] === '[';
              const closer = display ? '\\]' : '\\)';
              const close = text.indexOf(closer, i + 2);
              if (close !== -1) {
                  push(text.slice(i, close + 2), display);
                  i = close + 2;
                  continue;
              }
          }
          if (ch === '\\') {                      /* \$ and friends stay literal */
              out += text.slice(i, i + 2);
              i += 2;
              continue;
          }
          if (ch === '$') {
              if (text[i + 1] === '$') {
                  const close = text.indexOf('$$', i + 2);
                  if (close !== -1) {
                      push(text.slice(i, close + 2), true);
                      i = close + 2;
                      continue;
                  }
              } else {
                  const close = closingDollar(text, i);
                  if (close !== -1) {
                      push(text.slice(i, close + 1), false);
                      i = close + 1;
                      continue;
                  }
              }
          }
          out += ch;
          i++;
      }
      return {text: out, math};
  }

  /** '$…$' opens on a non-space, closes on a non-space and never spans a blank line. */
  function closingDollar(text, start) {
      if (!text[start + 1] || /\s/.test(text[start + 1])) return -1;
      for (let i = start + 1; i < text.length; i++) {
          const ch = text[i];
          if (ch === '\\') {
              i++;
              continue;
          }
          if (ch === '\n' && text[i + 1] === '\n') return -1;
          if (ch === '$' && !/\s/.test(text[i - 1])) return i;
      }
      return -1;
  }

  /** Puts the lifted formulas back as inert `.fs-math` spans (still raw TeX). */
  function restoreMath(root, math) {
      if (!math.length) return;
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      const texts = [];
      while (walker.nextNode()) texts.push(walker.currentNode);
      for (const node of texts) {
          const value = node.nodeValue || '';
          if (!value.includes('@@FSMATH')) continue;
          const fragment = document.createDocumentFragment();
          let last = 0;
          let match;
          PLACEHOLDER_RE.lastIndex = 0;
          while ((match = PLACEHOLDER_RE.exec(value))) {
              const item = math[Number(match[1])];
              if (!item) continue;
              if (match.index > last) fragment.appendChild(document.createTextNode(value.slice(last, match.index)));
              const span = document.createElement('span');
              span.className = item.display ? 'fs-math fs-math--display' : 'fs-math';
              span.textContent = item.raw;
              fragment.appendChild(span);
              last = match.index + match[0].length;
          }
          if (last < value.length) fragment.appendChild(document.createTextNode(value.slice(last)));
          node.parentNode?.replaceChild(fragment, node);
      }
  }

  /** Answers a DocumentFragment ready to be appended. */
  export async function renderMarkdown(text, {resolveUrl, math = true} = {}) {
      const marked = await loadMarked();
      const parse = typeof marked.parse === 'function' ? marked.parse : marked;
      const masked = math ? maskMath(text) : {text: String(text ?? ''), math: []};
      const html = parse(masked.text, {gfm: true, breaks: false, headerIds: false, mangle: false});
      const fragment = sanitize(html, {resolveUrl});
      restoreMath(fragment, masked.math);
      return fragment;
  }

  /**
   * Second pass, once the fragment is in the live document: Mermaid measures text
   * and MathJax measures the font, so neither can run on a detached fragment.
   * Never throws — a missing library degrades to the document's own source.
   */
  export async function enhanceMarkdown(root) {
      const warnings = [];
      const diagrams = await renderDiagrams(root);
      if (diagrams.warning) warnings.push(diagrams.warning);
      const math = await typesetMath(root);
      if (math.warning) warnings.push(math.warning);
      return {diagrams: diagrams.count, math: math.count, warnings};
  }

  /* --------------------------------------------------------------- mermaid */
  export async function renderDiagrams(root) {
      const blocks = Array.from(root.querySelectorAll('pre > code.language-mermaid, pre > code.lang-mermaid'));
      if (!blocks.length) return {count: 0};
      let mermaid;
      try {
          mermaid = await loadMermaid();
          syncMermaidTheme(mermaid);
      } catch (error) {
          console.warn('mermaid unavailable', error);
          return {count: 0, warning: '/lib/mermaid.min.js is unavailable — diagrams are shown as source.'};
      }
      let count = 0;
      let failed = 0;
      for (const code of blocks) {
          const pre = code.parentElement;
          if (!pre) continue;
          const source = code.textContent || '';
          try {
              const id = `fs-mermaid-${Date.now().toString(36)}-${count}-${Math.random().toString(36).slice(2, 8)}`;
              // eslint-disable-next-line no-await-in-loop
              const result = await mermaid.render(id, source);
              const svg = typeof result === 'string' ? result : result?.svg;
              const node = sanitizeSvg(svg);
              if (!node) throw new Error('the diagram could not be parsed');
              const host = document.createElement('div');
              host.className = 'fs-mermaid';
              host.appendChild(node);
              pre.replaceWith(host);
              if (typeof result?.bindFunctions === 'function') result.bindFunctions(host);
              count++;
          } catch (error) {
              /* Keep the source visible rather than swallowing the block. */
              console.warn('mermaid diagram failed', error);
              pre.classList.add('fs-mermaid__error');
              pre.setAttribute('title', String(error?.message || error));
              failed++;
              /* mermaid leaves its scratch element behind when parsing throws. */
              document.querySelectorAll('body > [id^="dfs-mermaid-"]').forEach((el) => el.remove());
          }
      }
      return {count, warning: failed ? `${failed} diagram(s) could not be rendered` : undefined};
  }

  /** Mermaid output is derived from user text: strip scripts/handlers, keep <style>. */
  function sanitizeSvg(text) {
      if (!text) return null;
      const doc = new DOMParser().parseFromString(String(text), 'image/svg+xml');
      if (doc.getElementsByTagName('parsererror').length) return null;
      const svg = doc.documentElement;
      if (!svg || svg.nodeName.toLowerCase() !== 'svg') return null;
      const elements = [svg];
      const walker = doc.createTreeWalker(svg, NodeFilter.SHOW_ELEMENT);
      while (walker.nextNode()) elements.push(walker.currentNode);
      const doomed = [];
      for (const el of elements) {
          if (el.nodeName.toLowerCase() === 'script') {
              doomed.push(el);
              continue;
          }
          for (const attr of Array.from(el.attributes)) {
              const name = attr.name.toLowerCase();
              if (name.startsWith('on')) el.removeAttribute(attr.name);
              else if (URL_ATTRS.has(name) && safeUrl(attr.value) === null) el.removeAttribute(attr.name);
          }
      }
      for (const el of doomed) el.remove();
      return document.importNode(svg, true);
  }

  /* --------------------------------------------------------------- mathjax */
  export async function typesetMath(root) {
      const nodes = Array.from(root.querySelectorAll('.fs-math'));
      if (!nodes.length) return {count: 0};
      let mathjax;
      try {
          mathjax = await loadMathJax();
      } catch (error) {
          console.warn('MathJax unavailable', error);
          return {count: 0, warning: '/lib/mathjax/tex-mml-chtml.js is unavailable — formulas are shown as source.'};
      }
      try {
          mathjax.typesetClear?.(nodes);
          await mathjax.typesetPromise(nodes);
      } catch (error) {
          console.warn('MathJax typesetting failed', error);
          return {count: 0, warning: `Some formulas could not be typeset: ${error?.message || error}`};
      }
      return {count: nodes.length};
  }

  const BLOCKED = new Set(['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'EMBED', 'LINK', 'META', 'BASE', 'FORM', 'INPUT']);
  const URL_ATTRS = new Set(['href', 'src', 'xlink:href', 'poster']);

  function safeUrl(value, resolveUrl) {
      const raw = String(value ?? '').trim();
      if (!raw) return null;
      if (/^\s*javascript:/i.test(raw) || /^\s*vbscript:/i.test(raw)) return null;
      if (/^\s*data:/i.test(raw)) return /^\s*data:image\//i.test(raw) ? raw : null;
      if (raw.startsWith('#')) return raw;
      if (/^[a-z][a-z0-9+.-]*:/i.test(raw) || raw.startsWith('//')) return raw;
      return resolveUrl ? resolveUrl(raw) : raw;
  }

  export function sanitize(html, {resolveUrl} = {}) {
      const doc = new DOMParser().parseFromString(String(html ?? ''), 'text/html');
      const elements = [];
      const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT);
      while (walker.nextNode()) elements.push(walker.currentNode);
      const doomed = [];
      for (const el of elements) {
          if (BLOCKED.has(el.tagName)) {
              doomed.push(el);
              continue;
          }
          for (const attr of Array.from(el.attributes)) {
              const name = attr.name.toLowerCase();
              if (name.startsWith('on')) {
                  el.removeAttribute(attr.name);
              } else if (name === 'style') {
                  if (/expression|url\s*\(/i.test(attr.value)) el.removeAttribute(attr.name);
              } else if (URL_ATTRS.has(name)) {
                  const safe = safeUrl(attr.value, resolveUrl);
                  if (safe === null) el.removeAttribute(attr.name);
                  else el.setAttribute(attr.name, safe);
              }
          }
          if (el.tagName === 'A' && el.hasAttribute('href')) {
              el.setAttribute('rel', 'noopener noreferrer');
              el.setAttribute('target', '_blank');
          }
      }
      for (const el of doomed) el.remove();
      const fragment = document.createDocumentFragment();
      while (doc.body.firstChild) fragment.appendChild(document.adoptNode(doc.body.firstChild));
      return fragment;
  }