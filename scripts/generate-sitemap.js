#!/usr/bin/env node
/* ─────────────────────────────────────────────────────────────
     Sitemap generator for Mathematical Explorations
     - Walks the project tree for HTML pages
     - Emits sitemap.xml with lastmod (file mtime) + heuristic priority
     - Zero runtime dependencies (Node core only)
     Usage: node scripts/generate-sitemap.js [--base https://your.site]
     ───────────────────────────────────────────────────────────── */

import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');

// Base URL: override with `--base https://example.com`
const baseArgIdx = process.argv.indexOf('--base');
const BASE_URL = (
  baseArgIdx !== -1 && process.argv[baseArgIdx + 1]
    ? process.argv[baseArgIdx + 1]
    : 'https://math.cognotik.com'
).replace(/\/+$/, '');

// Directories we never want to crawl.
const IGNORE_DIRS = new Set([
  'node_modules',
  '.git',
  '.github',
  'test',
  'tests',
  'scripts',
  '.idea',
  '.vscode',
]);

// Only these extensions become URLs.
const HTML_EXT = new Set(['.html', '.htm']);

async function walk(dir, acc = []) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name.startsWith('.')) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (IGNORE_DIRS.has(entry.name)) continue;
      await walk(full, acc);
    } else if (HTML_EXT.has(path.extname(entry.name).toLowerCase())) {
      acc.push(full);
    }
  }
  return acc;
}

function toUrl(absFile) {
  let rel = path.relative(ROOT, absFile).split(path.sep).join('/');
  // Pretty-print: index.html -> directory URL with trailing slash
  if (rel === 'index.html') return BASE_URL + '/';
  if (rel.endsWith('/index.html')) {
    rel = rel.slice(0, -'index.html'.length);
  }
  return BASE_URL + '/' + rel;
}

function priorityFor(rel) {
  if (rel === 'index.html') return '1.0';
  if (rel.startsWith('experiments/') && rel.endsWith('/index.html')) return '0.8';
  if (rel.startsWith('essays/') && rel.endsWith('/index.html')) return '0.8';
  if (rel.startsWith('experiments/basic/')) return '0.6';
  return '0.5';
}

function changefreqFor(rel) {
  if (rel === 'index.html') return 'weekly';
  return 'monthly';
}

function xmlEscape(s) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

async function main() {
  const files = await walk(ROOT);
  const entries = [];
  for (const file of files) {
    const rel = path.relative(ROOT, file).split(path.sep).join('/');
    const stat = await fs.stat(file);
    const lastmod = stat.mtime.toISOString().split('T')[0];
    entries.push({
      loc: toUrl(file),
      lastmod,
      priority: priorityFor(rel),
      changefreq: changefreqFor(rel),
    });
  }

  // Stable, descending-priority ordering for readability.
  entries.sort(
    (a, b) => parseFloat(b.priority) - parseFloat(a.priority) || a.loc.localeCompare(b.loc)
  );

  const body = entries
    .map(
      (e) =>
        '  <url>\n' +
        `    <loc>${xmlEscape(e.loc)}</loc>\n` +
        `    <lastmod>${e.lastmod}</lastmod>\n` +
        `    <changefreq>${e.changefreq}</changefreq>\n` +
        `    <priority>${e.priority}</priority>\n` +
        '  </url>'
    )
    .join('\n');

  const xml =
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    body +
    '\n</urlset>\n';

  const outPath = path.join(ROOT, 'sitemap.xml');
  await fs.writeFile(outPath, xml, 'utf8');
  console.log(`✓ sitemap.xml written with ${entries.length} URLs (base: ${BASE_URL})`);
}

main().catch((err) => {
  console.error('Sitemap generation failed:', err);
  process.exit(1);
});
