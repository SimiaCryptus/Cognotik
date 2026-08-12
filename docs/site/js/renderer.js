/** Markdown -> DOM-ready HTML, plus heading ids, a TOC and link rewriting. */

  const OPTIONS = { gfm: true, breaks: false, headerIds: false, mangle: false };

  function parseMarkdown(markdown) {
    const marked = window.marked;
    if (!marked) throw new Error('marked.min.js is not loaded');
    const parse = typeof marked.parse === 'function' ? marked.parse : marked;
    return parse(markdown, OPTIONS);
  }

  /**
   * @param {string} markdown raw markdown source
   * @param {{basePath: string, resolveLink: (path: string) => (string|null)}} ctx
   * @returns {{html: string, toc: Array<{id:string, level:number, text:string}>, title: string|null}}
   */
  export function renderDocument(markdown, ctx) {
    const doc = new DOMParser().parseFromString(parseMarkdown(markdown), 'text/html');
    const toc = [];
    const used = new Set();
    let title = null;

    doc.querySelectorAll('h1, h2, h3, h4').forEach((heading) => {
      const level = Number(heading.tagName.substring(1));
      const text = heading.textContent.trim();
      const id = uniqueId(slugify(text) || `section-${used.size + 1}`, used);
      heading.id = id;
      heading.classList.add('anchored');
      const anchor = doc.createElement('a');
      anchor.className = 'heading-anchor';
      anchor.href = `#${id}`;
      anchor.setAttribute('aria-hidden', 'true');
      anchor.textContent = '#';
      heading.appendChild(anchor);
      if (level === 1 && !title) title = text;
      if (level === 2 || level === 3) toc.push({ id, level, text });
    });

    doc.querySelectorAll('a[href]').forEach((link) => rewriteLink(link, ctx));
    doc.querySelectorAll('img[src]').forEach((img) => {
      const src = img.getAttribute('src');
      if (!/^(https?:|data:)/i.test(src)) img.setAttribute('src', resolvePath(ctx.basePath, src));
      img.setAttribute('loading', 'lazy');
    });

    doc.querySelectorAll('table').forEach((table) => {
      const wrap = doc.createElement('div');
      wrap.className = 'table-wrap';
      table.parentNode.insertBefore(wrap, table);
      wrap.appendChild(table);
    });

    doc.querySelectorAll('pre').forEach((pre) => decorateCodeBlock(doc, pre));

    return { html: doc.body.innerHTML, toc, title };
  }

  function rewriteLink(link, ctx) {
    const href = link.getAttribute('href') || '';
    if (/^(https?:)?\/\//i.test(href) || /^(mailto:|tel:)/i.test(href)) {
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.classList.add('external');
      return;
    }
    if (href.startsWith('#')) return;

    const [path, fragment] = href.split('#');
    if (!path) return;

    const resolved = resolvePath(ctx.basePath, path);
    const route = /\.md$/i.test(resolved) && ctx.resolveLink ? ctx.resolveLink(resolved) : null;

    if (route) {
      link.setAttribute('href', route);
      link.classList.add('internal');
      if (fragment) link.dataset.fragment = fragment;
    } else {
      link.setAttribute('href', resolved + (fragment ? `#${fragment}` : ''));
      link.classList.add('raw-file');
    }
  }

  function decorateCodeBlock(doc, pre) {
    const wrap = doc.createElement('div');
    wrap.className = 'code-block';

    const code = pre.querySelector('code');
    const language = (code && (code.className.match(/language-([\w+#-]+)/) || [])[1]) || '';

    const bar = doc.createElement('div');
    bar.className = 'code-bar';

    const label = doc.createElement('span');
    label.className = 'code-lang';
    label.textContent = language || 'text';

    const button = doc.createElement('button');
    button.type = 'button';
    button.className = 'copy-btn';
    button.textContent = 'Copy';

    bar.append(label, button);
    pre.parentNode.insertBefore(wrap, pre);
    wrap.append(bar, pre);
  }

  /** Resolve `relative` against the directory containing `basePath`. */
  export function resolvePath(basePath, relative) {
    try {
      const base = new URL(basePath || 'index.md', 'https://site.invalid/');
      return new URL(relative, base).pathname.replace(/^\//, '');
    } catch {
      return relative;
    }
  }

  export function slugify(text) {
    return String(text)
      .toLowerCase()
      .replace(/[`*_~]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  function uniqueId(base, used) {
    let id = base;
    let n = 2;
    while (used.has(id)) id = `${base}-${n++}`;
    used.add(id);
    return id;
  }