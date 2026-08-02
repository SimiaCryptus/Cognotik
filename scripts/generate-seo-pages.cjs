#!/usr/bin/env node
  /* ─────────────────────────────────────────────────────────────
     generate-seo-pages.js

     Builds an SEO-optimized companion static-page folder.

     For every lab / experiment / essay it produces:
       - a README-based landing page (Markdown rendered to HTML at
         BUILD TIME — no browser-side marked.js), with full
         <head> SEO metadata, OpenGraph, Twitter, JSON-LD, canonical.
       - a dedicated video landing page (when a video is declared)
         with VideoObject structured data.

     It also emits:
       - companion/index.html          (hub linking to all pages)
       - sitemap.xml                    (all generated URLs)

     Usage:
       node scripts/generate-seo-pages.js
       node scripts/generate-seo-pages.js --out site/seo --base https://math.cognotik.com

     Dependencies (devDependencies):
       marked            — Markdown → HTML
       marked-gfm-heading-id (optional) — stable heading ids
       sanitize-html (optional) — not required; READMEs are trusted

     This script has NO runtime dependencies for the generated pages.
     ───────────────────────────────────────────────────────────── */

  'use strict';

  const fs = require('fs');
  const path = require('path');
  const { marked } = require('marked');

  /* ── CLI args ──────────────────────────────────────────────── */

  function parseArgs(argv) {
    const args = {
      root: process.cwd(),
      out: 'companion',
      base: 'https://math.cognotik.com',
      siteName: 'Mathematical Explorations',
      author: 'SimiaCryptus',
    };
    for (let i = 2; i < argv.length; i++) {
      const a = argv[i];
      if (a === '--out') args.out = argv[++i];
      else if (a === '--base') args.base = argv[++i];
      else if (a === '--root') args.root = argv[++i];
      else if (a === '--site-name') args.siteName = argv[++i];
      else if (a === '--author') args.author = argv[++i];
      else if (a === '--help' || a === '-h') {
        printHelp();
        process.exit(0);
      }
    }
    // Normalize base (no trailing slash).
    args.base = args.base.replace(/\/+$/, '');
    return args;
  }

  function printHelp() {
    console.log(`generate-seo-pages.js

  Options:
    --root <dir>        Project root (default: cwd)
    --out <dir>         Output folder, relative to root (default: companion)
    --base <url>        Canonical base URL (default: https://math.cognotik.com)
    --site-name <str>   Site name for OG / titles
    --author <str>      Author meta value
    -h, --help          Show this help`);
  }

  /* ── Small utilities ───────────────────────────────────────── */

  function readJSONIfExists(file) {
    try {
      if (!fs.existsSync(file)) return null;
      return JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch (err) {
      console.warn('Warning: failed to parse', file, '-', err.message);
      return null;
    }
  }

  function ensureDir(dir) {
    fs.mkdirSync(dir, { recursive: true });
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // Strip HTML tags + collapse whitespace, for meta descriptions.
  function plainText(html, maxLen) {
    let t = String(html || '')
      .replace(/<script[\s\S]*?<\/script>/gi, ' ')
      .replace(/<style[\s\S]*?<\/style>/gi, ' ')
      .replace(/<[^>]+>/g, ' ')
      .replace(/&nbsp;/g, ' ')
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/\s+/g, ' ')
      .trim();
    if (maxLen && t.length > maxLen) {
      t = t.slice(0, maxLen - 1).replace(/\s+\S*$/, '') + '…';
    }
    return t;
  }

  function slugify(s) {
    return String(s || '')
      .toLowerCase()
      .normalize('NFKD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 80);
  }

  /* ── Markdown rendering (build-time) ───────────────────────── */

  // Rewrite relative img/href so they still resolve from the generated
  // page's location back into the original lab folder. Generated pages
  // live under <out>/<slug>/index.html, so we prefix relative URLs with
  // a path back to the lab's source directory.
  function rewriteRelativeLinks(html, relPrefix) {
    return html
      // src="..."
      .replace(/(\s(?:src|href))=("|')([^"']+)(\2)/gi, (m, attr, q, url) => {
        if (/^([a-z]+:)?\/\//i.test(url) || url.startsWith('/') || url.startsWith('#') || url.startsWith('data:')) {
          return m;
        }
        return `${attr}=${q}${relPrefix}${url}${q}`;
      });
  }

  function configureMarked() {
    marked.setOptions({
      gfm: true,
      breaks: false,
      headerIds: true,
      mangle: false,
    });
    const renderer = new marked.Renderer();
    const defaultCode = renderer.code.bind(renderer);
    // Preserve mermaid blocks as <div class="mermaid"> so an optional
    // CDN mermaid script (loaded conditionally) can render them.
    renderer.code = function (code, infostring) {
      const lang = (infostring || '').trim().split(/\s+/)[0];
      if (lang === 'mermaid') {
        return '<div class="mermaid">' + esc(code) + '</div>';
      }
      return defaultCode(code, infostring);
    };
    marked.use({ renderer });
  }

  function renderMarkdown(md) {
    return marked.parse(md || '');
  }

  // Extract the first H1 as a title fallback.
  function firstHeading(html) {
    const m = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
    return m ? plainText(m[1]) : '';
  }

  /* ── HTML templates ────────────────────────────────────────── */

  function headBlock({
    title,
    description,
    canonical,
    image,
    type = 'article',
    siteName,
    author,
    keywords,
    jsonLd,
    extraHead = '',
  }) {
    const kw = Array.isArray(keywords) ? keywords.join(', ') : keywords || '';
    return `  <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="robots" content="index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1" />
    <meta name="author" content="${esc(author)}" />
    ${kw ? `<meta name="keywords" content="${esc(kw)}" />` : ''}
    <meta name="description" content="${esc(description)}" />
    <title>${esc(title)}</title>
    <link rel="canonical" href="${esc(canonical)}" />
    <meta name="theme-color" content="#0a0a14" />
    <meta name="color-scheme" content="dark light" />
    <link rel="icon" type="image/png" href="${esc(rootRel(canonical))}icon.png" />
    <meta property="og:type" content="${esc(type)}" />
    <meta property="og:site_name" content="${esc(siteName)}" />
    <meta property="og:title" content="${esc(title)}" />
    <meta property="og:description" content="${esc(description)}" />
    <meta property="og:url" content="${esc(canonical)}" />
    ${image ? `<meta property="og:image" content="${esc(image)}" />` : ''}
    <meta property="og:locale" content="en_US" />
    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content="${esc(title)}" />
    <meta name="twitter:description" content="${esc(description)}" />
    ${image ? `<meta name="twitter:image" content="${esc(image)}" />` : ''}
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Fraunces:ital,opsz,wght@0,9..144,400;0,9..144,600;0,9..144,800;1,9..144,400&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet" />
    <style>${BASE_CSS}</style>
    ${jsonLd ? `<script type="application/ld+json">${JSON.stringify(jsonLd)}</script>` : ''}
    ${extraHead}`;
  }

  // crude: companion pages live one or two dirs deep; compute "../"
  // back to site root based on canonical path depth under base.
  function rootRel(canonical) {
    try {
      const u = new URL(canonical);
      const segs = u.pathname.split('/').filter(Boolean);
      // last segment is the file (index.html) -> ignore it
      const depth = Math.max(0, segs.length - 1);
      return '../'.repeat(depth) || './';
    } catch {
      return './';
    }
  }

  const BASE_CSS = `
  :root{--bg:#0a0a14;--panel:#12121f;--ink:#e8e8f0;--muted:#a0a0b8;--accent:#7aa2ff;--accent2:#b78aff;--border:#23233a}
  *{box-sizing:border-box}
  html{scroll-behavior:smooth}
  body{margin:0;background:var(--bg);color:var(--ink);font-family:'Inter',system-ui,sans-serif;line-height:1.65;font-size:17px}
  a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
  .arrow{font-size:.9em}
  nav{display:flex;align-items:center;gap:1rem;padding:1rem 1.5rem;border-bottom:1px solid var(--border);position:sticky;top:0;background:rgba(10,10,20,.82);backdrop-filter:blur(8px);z-index:10}
  nav .brand{display:flex;align-items:center;gap:.5rem;font-weight:700}
  nav .brand-glyph{font-size:1.3rem;color:var(--accent2)}
  nav .spacer{flex:1}
  nav .nav-link{color:var(--muted);font-size:.95rem}
  main{max-width:820px;margin:0 auto;padding:2.5rem 1.5rem 5rem}
  .crumbs{font-size:.85rem;color:var(--muted);margin-bottom:1.5rem}
  .crumbs a{color:var(--muted)}
  .page-head{display:flex;align-items:center;gap:1rem;margin-bottom:.5rem}
  .page-icon{width:54px;height:54px;flex:none;border-radius:14px;display:grid;place-items:center;font-family:'JetBrains Mono',monospace;font-weight:600;font-size:1.05rem;background:linear-gradient(135deg,#1c2440,#2a1c40);border:1px solid var(--border);color:var(--accent)}
  h1{font-family:'Fraunces',serif;font-weight:800;font-size:2.1rem;line-height:1.1;margin:0}
  .subtitle{color:var(--muted);margin:.4rem 0 1.5rem}
  .cta{display:inline-flex;align-items:center;gap:.4rem;background:linear-gradient(135deg,var(--accent),var(--accent2));color:#0a0a14;font-weight:700;padding:.7rem 1.2rem;border-radius:10px;margin:0 .6rem 1.5rem 0}
  .cta:hover{text-decoration:none;filter:brightness(1.08)}
  .cta.ghost{background:transparent;border:1px solid var(--border);color:var(--ink)}
  .media{margin:1.5rem 0;border-radius:14px;overflow:hidden;border:1px solid var(--border);background:#000}
  .media video{display:block;width:100%;height:auto}
  article{font-size:1.02rem}
  article h1,article h2,article h3,article h4{font-family:'Fraunces',serif;line-height:1.2;margin-top:2rem}
  article h2{font-size:1.5rem;border-bottom:1px solid var(--border);padding-bottom:.3rem}
  article h3{font-size:1.2rem}
  article code{font-family:'JetBrains Mono',monospace;font-size:.9em;background:#1a1a2a;padding:.15em .4em;border-radius:5px}
  article pre{background:#0d0d18;border:1px solid var(--border);border-radius:10px;padding:1rem;overflow:auto}
  article pre code{background:none;padding:0}
  article img{max-width:100%;height:auto;border-radius:10px}
  article blockquote{border-left:3px solid var(--accent2);margin:1rem 0;padding:.2rem 0 .2rem 1rem;color:var(--muted)}
  article table{border-collapse:collapse;width:100%;margin:1rem 0}
  article th,article td{border:1px solid var(--border);padding:.5rem .7rem;text-align:left}
  .mermaid{background:#0d0d18;border:1px solid var(--border);border-radius:10px;padding:1rem;margin:1rem 0}
  .related{margin-top:3rem;border-top:1px solid var(--border);padding-top:1.5rem}
  .related h2{font-family:'Fraunces',serif;font-size:1.2rem}
  .related ul{list-style:none;padding:0;display:grid;gap:.4rem}
  footer{border-top:1px solid var(--border);padding:2rem 1.5rem;text-align:center;color:var(--muted);font-size:.9rem}
  .footer-mark{font-size:1.6rem;color:var(--accent2);margin-top:1rem}
  .hub-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:1rem;margin-top:1.5rem}
  .hub-card{display:block;background:var(--panel);border:1px solid var(--border);border-radius:14px;padding:1.2rem}
  .hub-card:hover{border-color:var(--accent);text-decoration:none}
  .hub-card h3{margin:.2rem 0 .4rem;font-family:'Fraunces',serif}
  .hub-card p{margin:0;color:var(--muted);font-size:.92rem}
  .badge{display:inline-block;font-size:.72rem;letter-spacing:.04em;text-transform:uppercase;color:var(--accent2);border:1px solid var(--border);border-radius:999px;padding:.15rem .6rem;margin-bottom:.5rem}
  `;

  function navBlock(rootPrefix) {
    return `<nav>
      <a class="brand" href="${rootPrefix}index.html">
        <span class="brand-glyph">∑</span>
        <span>Mathematical Explorations</span>
      </a>
      <span class="spacer"></span>
      <a class="nav-link" href="${rootPrefix}index.html">Interactive home <span class="arrow">↗</span></a>
      <a class="nav-link" href="https://github.com/SimiaCryptus/Fun_With_Math" target="_blank" rel="noopener">GitHub <span class="arrow">↗</span></a>
    </nav>`;
  }

  function footerBlock(rootPrefix) {
    return `<footer>
      <p>Part of <a href="${rootPrefix}index.html">Mathematical Explorations</a> — original interactive experiments in geometry, number theory, and dynamical systems.</p>
      <p class="footer-mark">∑</p>
    </footer>`;
  }

  /* ── Page builders ─────────────────────────────────────────── */

  // ctx: { base, siteName, author, root }
  // item: a lab/essay/demo entry (normalized with .kind)
  function buildLandingPage(item, ctx, related) {
    const slug = item.slug;
    const canonical = `${ctx.base}/${ctx.out}/${slug}/index.html`;
    // Output: <out>/<slug>/index.html  → depth from site root.
    // Relative prefix from this page back to site root: ../../  (out/slug)
    const rootPrefix = '../../';
    // Relative prefix from this page back to the lab's source dir, so
    // relative README images/links keep working:
    //   page at out/slug/  ->  lab source at <readmeDir>/
    const readmeDir = item.readme ? path.posix.dirname(item.readme) : '';
    const linkPrefix = rootPrefix + (readmeDir ? readmeDir + '/' : '');

    let articleHtml = '';
    let description = item.pitch ? plainText(item.pitch, 300) : '';
    if (item.readmeHtml) {
      articleHtml = rewriteRelativeLinks(item.readmeHtml, linkPrefix);
      if (!description) description = plainText(articleHtml, 300);
    }
    const title = `${item.title} — ${ctx.siteName}`;
    const image = item.video ? `${ctx.base}/og-image.png` : `${ctx.base}/og-image.png`;
    const hasMermaid = /class="mermaid"/.test(articleHtml);

    const jsonLd = {
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': item.kind === 'essay' ? 'Article' : 'TechArticle',
          headline: item.title,
          description: description,
          url: canonical,
          inLanguage: 'en',
          author: { '@type': 'Person', name: ctx.author },
          publisher: { '@type': 'Organization', name: ctx.siteName, url: ctx.base + '/' },
          mainEntityOfPage: canonical,
        },
        {
          '@type': 'BreadcrumbList',
          itemListElement: [
            { '@type': 'ListItem', position: 1, name: ctx.siteName, item: ctx.base + '/' },
            {
              '@type': 'ListItem',
              position: 2,
              name: item.title,
              item: canonical,
            },
          ],
        },
      ],
    };
    if (item.video) {
      jsonLd['@graph'].push({
        '@type': 'VideoObject',
        name: item.title + ' — demonstration',
        description: description,
        thumbnailUrl: image,
        contentUrl: `${ctx.base}/${item.video}`,
        uploadDate: ctx.buildDate,
      });
    }

    const launchHref = rootPrefix + item.href;
    const videoLandingHref = item.video ? './video.html' : '';

    const relatedBlock = related && related.length
      ? `<section class="related">
        <h2>More from Mathematical Explorations</h2>
        <ul>
          ${related
            .map(
              (r) =>
                `<li><a href="../${r.slug}/index.html">${esc(r.title)}</a>${
                  r.pitch ? ' — ' + esc(plainText(r.pitch, 110)) : ''
                }</li>`
            )
            .join('\n')}
        </ul>
      </section>`
      : '';

    const head = headBlock({
      title,
      description,
      canonical,
      image,
      type: 'article',
      siteName: ctx.siteName,
      author: ctx.author,
      keywords: item.keywords,
      jsonLd,
      extraHead: hasMermaid
        ? `<script type="module">import m from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';m.initialize({startOnLoad:true,theme:'dark'});</script>`
        : '',
    });

    const body = `${navBlock(rootPrefix)}
    <main>
      <div class="crumbs">
        <a href="${rootPrefix}index.html">Home</a> ›
        <a href="../index.html">Companion pages</a> ›
        <span>${esc(item.title)}</span>
      </div>
      <div class="page-head">
        <div class="page-icon" aria-hidden="true">${esc(item.icon || item.glyph || '∑')}</div>
        <div>
          <h1>${esc(item.title)}</h1>
          ${item.subtitle ? `<p class="subtitle">${esc(item.subtitle)}</p>` : ''}
        </div>
      </div>
      ${item.pitch ? `<p class="subtitle">${item.pitch}</p>` : ''}
      <p>
        <a class="cta" href="${esc(launchHref)}">Open the interactive lab <span class="arrow">→</span></a>
        ${videoLandingHref ? `<a class="cta ghost" href="${esc(videoLandingHref)}">Watch the video <span class="arrow">→</span></a>` : ''}
      </p>
      ${
        item.video
          ? `<div class="media">
        <video controls preload="metadata" playsinline aria-label="${esc(item.title)} demonstration video">
          <source src="${esc(rootPrefix + item.video)}" type="video/mp4" />
        </video>
      </div>`
          : ''
      }
      <article>${articleHtml || '<p>Open the lab to explore this experiment interactively.</p>'}</article>
      ${relatedBlock}
    </main>
    ${footerBlock(rootPrefix)}`;

    return `<!doctype html>
  <html lang="en">
  <head>
  ${head}
  </head>
  <body>
  ${body}
  </body>
  </html>`;
  }

  function buildVideoPage(item, ctx) {
    const slug = item.slug;
    const canonical = `${ctx.base}/${ctx.out}/${slug}/video.html`;
    const rootPrefix = '../../';
    const description = item.pitch
      ? plainText(item.pitch, 280)
      : `Demonstration video for ${item.title}.`;
    const title = `${item.title} — Video Demonstration · ${ctx.siteName}`;
    const image = `${ctx.base}/og-image.png`;
    const contentUrl = `${ctx.base}/${item.video}`;

    const jsonLd = {
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': 'VideoObject',
          name: `${item.title} — Demonstration`,
          description: description,
          thumbnailUrl: [image],
          uploadDate: ctx.buildDate,
          contentUrl: contentUrl,
          embedUrl: canonical,
          publisher: { '@type': 'Organization', name: ctx.siteName, url: ctx.base + '/' },
        },
        {
          '@type': 'BreadcrumbList',
          itemListElement: [
            { '@type': 'ListItem', position: 1, name: ctx.siteName, item: ctx.base + '/' },
            { '@type': 'ListItem', position: 2, name: item.title, item: `${ctx.base}/${ctx.out}/${slug}/index.html` },
            { '@type': 'ListItem', position: 3, name: 'Video', item: canonical },
          ],
        },
      ],
    };

    const head = headBlock({
      title,
      description,
      canonical,
      image,
      type: 'video.other',
      siteName: ctx.siteName,
      author: ctx.author,
      keywords: item.keywords,
      jsonLd,
      extraHead: `<meta property="og:video" content="${esc(contentUrl)}" />
      <meta property="og:video:type" content="video/mp4" />
      <meta name="twitter:player" content="${esc(canonical)}" />`,
    });

    const body = `${navBlock(rootPrefix)}
    <main>
      <div class="crumbs">
        <a href="${rootPrefix}index.html">Home</a> ›
        <a href="./index.html">${esc(item.title)}</a> ›
        <span>Video</span>
      </div>
      <div class="page-head">
        <div class="page-icon" aria-hidden="true">${esc(item.icon || '▶')}</div>
        <div>
          <h1>${esc(item.title)} — Video</h1>
          <p class="subtitle">A short demonstration of the interactive lab.</p>
        </div>
      </div>
      <div class="media">
        <video controls preload="metadata" playsinline autoplay muted loop
               aria-label="${esc(item.title)} demonstration video">
          <source src="${esc(rootPrefix + item.video)}" type="video/mp4" />
        </video>
      </div>
      <p>
        <a class="cta" href="${esc(rootPrefix + item.href)}">Open the interactive lab <span class="arrow">→</span></a>
        <a class="cta ghost" href="./index.html">Read the full notes <span class="arrow">→</span></a>
      </p>
      ${item.pitch ? `<article><p>${item.pitch}</p></article>` : ''}
    </main>
    ${footerBlock(rootPrefix)}`;

    return `<!doctype html>
  <html lang="en">
  <head>
  ${head}
  </head>
  <body>
  ${body}
  </body>
  </html>`;
  }

  function buildHubPage(items, ctx) {
    const canonical = `${ctx.base}/${ctx.out}/index.html`;
    const rootPrefix = '../';
    const description =
      'Companion reference pages for every Mathematical Explorations lab and essay — full write-ups, demonstration videos, and links to the interactive experiments.';
    const title = `Companion Pages — ${ctx.siteName}`;

    const groups = [
      { key: 'featured', label: 'Featured Laboratories' },
      { key: 'essay', label: 'Essays' },
      { key: 'demo', label: 'Short Demonstrations' },
    ];

    const sections = groups
      .map((g) => {
        const list = items.filter((i) => i.group === g.key);
        if (!list.length) return '';
        const cards = list
          .map(
            (i) => `<a class="hub-card" href="${esc(i.slug)}/index.html">
            ${i.subtitle ? `<span class="badge">${esc(i.subtitle)}</span>` : ''}
            <h3>${esc(i.title)}</h3>
            <p>${esc(plainText(i.pitch, 150))}</p>
            ${i.video ? `<p><a href="${esc(i.slug)}/video.html">▶ Watch video</a></p>` : ''}
          </a>`
          )
          .join('\n');
        return `<h2 style="font-family:'Fraunces',serif;margin-top:2.5rem">${esc(g.label)}</h2>
        <div class="hub-grid">${cards}</div>`;
      })
      .join('\n');

    const jsonLd = {
      '@context': 'https://schema.org',
      '@type': 'CollectionPage',
      url: canonical,
      name: title,
      description,
      isPartOf: { '@type': 'WebSite', url: ctx.base + '/', name: ctx.siteName },
    };

    const head = headBlock({
      title,
      description,
      canonical,
      image: `${ctx.base}/og-image.png`,
      type: 'website',
      siteName: ctx.siteName,
      author: ctx.author,
      jsonLd,
    });

    const body = `${navBlock(rootPrefix)}
    <main>
      <h1>Companion Reference Pages</h1>
      <p class="subtitle">Build-time rendered write-ups and video pages for every lab and essay. Each links back to the live interactive experiment.</p>
      ${sections}
    </main>
    ${footerBlock(rootPrefix)}`;

    return `<!doctype html>
  <html lang="en">
  <head>
  ${head}
  </head>
  <body>
  ${body}
  </body>
  </html>`;
  }

  function buildSitemap(items, ctx) {
    const urls = [];
    urls.push({ loc: `${ctx.base}/${ctx.out}/index.html`, priority: '0.6' });
    for (const i of items) {
      urls.push({ loc: `${ctx.base}/${ctx.out}/${i.slug}/index.html`, priority: '0.8' });
      if (i.video) {
        urls.push({ loc: `${ctx.base}/${ctx.out}/${i.slug}/video.html`, priority: '0.6' });
      }
    }
    const body = urls
      .map(
        (u) =>
          `  <url>\n    <loc>${esc(u.loc)}</loc>\n    <lastmod>${ctx.buildDate}</lastmod>\n    <changefreq>monthly</changefreq>\n    <priority>${u.priority}</priority>\n  </url>`
      )
      .join('\n');
    return `<?xml version="1.0" encoding="UTF-8"?>
  <urlset xmlns="http://www.sitemap.org/schemas/sitemap/0.9">
  ${body}
  </urlset>`;
  }

  /* ── Data loading + normalization ──────────────────────────── */

  function loadAllItems(root) {
    // Prefer the split files; fall back to labs.json.
    const experiments = readJSONIfExists(path.join(root, 'experiments.json'));
    const essays = readJSONIfExists(path.join(root, 'essays.json'));
    const labs = readJSONIfExists(path.join(root, 'labs.json')) || {};

    const featured = (experiments && experiments.featured) || labs.featured || [];
    const demos = (experiments && experiments.demos) || labs.demos || [];
    const essayList = (essays && essays.essays) || labs.essays || [];

    const items = [];
    const seen = new Set();

    function add(entry, group, kind) {
      if (!entry || !entry.title) return;
      let slug = slugify(entry.title);
      let n = 2;
      while (seen.has(slug)) slug = slugify(entry.title) + '-' + n++;
      seen.add(slug);
      items.push({ ...entry, slug, group, kind });
    }

    featured.forEach((e) => add(e, 'featured', 'lab'));
    essayList.forEach((e) => add(e, 'essay', 'essay'));
    demos.forEach((e) => add({ ...e, icon: e.glyph }, 'demo', 'demo'));

    return items;
  }

  function loadReadme(root, item) {
    if (!item.readme) return;
    const file = path.join(root, item.readme);
    if (!fs.existsSync(file)) {
      console.warn('  README not found:', item.readme);
      return;
    }
    const md = fs.readFileSync(file, 'utf8');
    item.readmeHtml = renderMarkdown(md);
    if (!item.subtitle) {
      const h = firstHeading(item.readmeHtml);
      // keep subtitle empty; title already present
    }
  }

  /* ── Main ──────────────────────────────────────────────────── */

  function main() {
    const args = parseArgs(process.argv);
    configureMarked();

    const ctx = {
      base: args.base,
      out: args.out.replace(/^\/+|\/+$/g, ''),
      siteName: args.siteName,
      author: args.author,
      root: args.root,
      buildDate: new Date().toISOString().slice(0, 10),
    };

    const outRoot = path.join(args.root, ctx.out);
    ensureDir(outRoot);

    console.log('Generating SEO companion pages → ' + outRoot);

    const items = loadAllItems(args.root);
    if (!items.length) {
      console.error('No items found in experiments.json / essays.json / labs.json');
      process.exit(1);
    }

    // Render READMEs.
    for (const item of items) {
      loadReadme(args.root, item);
    }

    // Per-item pages.
    let pageCount = 0;
    let videoCount = 0;
    for (const item of items) {
      // Skip pure demos with no readme & no pitch from getting a thin page?
      // We still generate them — even a thin page links back to the demo.
      const related = items
        .filter((r) => r !== item && r.group === item.group)
        .slice(0, 5);

      const dir = path.join(outRoot, item.slug);
      ensureDir(dir);

      fs.writeFileSync(path.join(dir, 'index.html'), buildLandingPage(item, ctx, related), 'utf8');
      pageCount++;

      if (item.video) {
        fs.writeFileSync(path.join(dir, 'video.html'), buildVideoPage(item, ctx), 'utf8');
        videoCount++;
      }
    }

    // Hub + sitemap.
    fs.writeFileSync(path.join(outRoot, 'index.html'), buildHubPage(items, ctx), 'utf8');
    fs.writeFileSync(path.join(outRoot, 'sitemap.xml'), buildSitemap(items, ctx), 'utf8');

    console.log(`  ${pageCount} landing pages`);
    console.log(`  ${videoCount} video pages`);
    console.log(`  1 hub page + sitemap.xml`);
    console.log('Done.');
  }

  main();