/**
   * Markdown rendering.
   *
   * `marked` is loaded from the sibling `/lib/` directory (a plain UMD bundle),
   * resolved against this module's URL so it works under any mount point.
   *
   * Monaco ships an AMD loader that owns the global `define`. A UMD bundle that
   * sees `define.amd` registers an anonymous AMD module — which Monaco's loader
   * rejects — and never touches `window.marked`. The AMD globals are therefore
   * hidden for the (synchronous) evaluation of the bundle only.
   *
   * The HTML `marked` produces is *never* trusted: it is parsed in an inert
   * document, stripped of scripts/handlers/dangerous URLs, and only then
   * adopted into the live DOM.
   */
  const SRC = new URL('../lib/marked.min.js', import.meta.url).href;
  const AMD_GLOBALS = ['define', 'require', 'module', 'exports'];

  let loading = null;

  export function loadMarked() {
      if (window.marked) return Promise.resolve(window.marked);
      if (loading) return loading;
      loading = (async () => {
          const response = await fetch(SRC, {credentials: 'same-origin'});
          if (!response.ok) throw new Error(`lib/marked.min.js could not be fetched (${response.status})`);
          const source = await response.text();
          const saved = AMD_GLOBALS.map((name) => [name, window[name]]);
          for (const name of AMD_GLOBALS) window[name] = undefined;
          try {
              const script = document.createElement('script');
              script.textContent = `${source}\n//# sourceURL=${SRC}`;
              document.head.appendChild(script);
              script.remove();
          } finally {
              for (const [name, value] of saved) window[name] = value;
          }
          if (!window.marked) throw new Error('marked.min.js loaded but exported nothing');
          return window.marked;
      })().catch((error) => {
          loading = null;
          throw error;
      });
      return loading;
  }

  /** Answers a DocumentFragment ready to be appended. */
  export async function renderMarkdown(text, {resolveUrl} = {}) {
      const marked = await loadMarked();
      const parse = typeof marked.parse === 'function' ? marked.parse : marked;
      const html = parse(String(text ?? ''), {gfm: true, breaks: false, headerIds: false, mangle: false});
      return sanitize(html, {resolveUrl});
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