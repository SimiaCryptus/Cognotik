#!/usr/bin/env node
'use strict';

/**
 * Vendors the front-end libraries used by the embedded web UI.
 *
 * Downloads the latest release of each pinned major version from jsDelivr,
 * e.g. https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js
 *
 * Usage:
 *   node download.js                    # download missing files
 *   node download.js --force            # re-download everything
 *   node download.js --only=mermaid,prismjs
  *   node download.js --only=monaco      # just the Monaco editor bundle
 *   node download.js --fonts            # also fetch the MathJax web fonts
 *   node download.js --check            # offline integrity check (no network)
 *   node download.js --list             # show what would be downloaded
 *   node download.js --tags             # print the <script>/<link> snippet
 *   node download.js --out=../vendor --concurrency=8 --timeout=60000
 *
 * No third-party dependencies: only Node core modules are used.
 */

const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const http = require('node:http');
const https = require('node:https');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const { Transform } = require('node:stream');
const { pipeline } = require('node:stream/promises');

const CDN = 'https://cdn.jsdelivr.net/npm';
const DATA_API = 'https://data.jsdelivr.com/v1/packages/npm';
const MANIFEST = 'manifest.json';
const MAX_REDIRECTS = 5;
const USER_AGENT = 'web-lib-downloader/1.0 (+node)';

/* ------------------------------------------------------------------ *
 * Package versions (major ranges - resolved to exact versions at run) *
 * ------------------------------------------------------------------ */

const VERSIONS = {
  mermaid: '11',
  marked: '15',
  mathjax: '3',
  prismjs: '1',
  dompurify: '3',
  // Monaco has no stable major yet - pin the minor so a 0.x bump is deliberate.
  'monaco-editor': '0.52',
};

const PRISM_THEME = 'prism-tomorrow';

const PRISM_LANGUAGES = [
  'bash', 'c', 'cpp', 'csharp', 'css', 'diff', 'docker', 'go', 'groovy',
  'java', 'javascript', 'json', 'kotlin', 'markdown', 'markup', 'python',
  'rust', 'scala', 'sql', 'typescript', 'yaml',
];

// [name, hasCss]
const PRISM_PLUGINS = [
  ['line-numbers', true],
  ['toolbar', true],
  ['copy-to-clipboard', false],
];
/* ----------------------------- Monaco ----------------------------- *
* Monaco ships as an AMD bundle: only `min/vs/loader.js` is added to
* the page, everything else is pulled in at runtime by the loader from
* `lib/monaco/vs`. Keep the on-disk layout identical to the package.  *
* ------------------------------------------------------------------ */
const MONACO_SRC_BASE = 'min/vs';
const MONACO_OUT_BASE = 'monaco/vs';
// [path relative to min/vs, tag or null]
const MONACO_CORE = [
  ['loader.js', 'js'],
  ['editor/editor.main.js', null],
  ['editor/editor.main.css', null],
  ['editor/editor.main.nls.js', null],
  ['base/worker/workerMain.js', null],
  ['base/browser/ui/codicons/codicon/codicon.ttf', null],
];
// Tokenizer-only languages (min/vs/basic-languages/<name>/<name>.js)
const MONACO_BASIC_LANGUAGES = [
  'bat', 'clojure', 'coffee', 'cpp', 'csharp', 'css', 'dart', 'dockerfile',
  'go', 'graphql', 'groovy', 'handlebars', 'html', 'ini', 'java',
  'javascript', 'julia', 'kotlin', 'less', 'lua', 'markdown', 'objective-c',
  'perl', 'php', 'powershell', 'protobuf', 'python', 'r', 'ruby', 'rust',
  'scala', 'scss', 'shell', 'sql', 'swift', 'typescript', 'xml', 'yaml',
];
// Rich language services incl. their web workers (min/vs/language/<dir>/...)
const MONACO_LANGUAGE_SERVICES = [
  ['json', ['jsonMode.js', 'jsonWorker.js']],
  ['css', ['cssMode.js', 'cssWorker.js']],
  ['html', ['htmlMode.js', 'htmlWorker.js']],
  ['typescript', ['tsMode.js', 'tsWorker.js']],
];


// MathJax 3 CHTML fonts (only fetched with --fonts / --only=fonts)
const MATHJAX_FONTS = [
  'MathJax_AMS-Regular', 'MathJax_Calligraphic-Bold', 'MathJax_Calligraphic-Regular',
  'MathJax_Fraktur-Bold', 'MathJax_Fraktur-Regular', 'MathJax_Main-Bold',
  'MathJax_Main-Italic', 'MathJax_Main-Regular', 'MathJax_Math-BoldItalic',
  'MathJax_Math-Italic', 'MathJax_Math-Regular', 'MathJax_SansSerif-Bold',
  'MathJax_SansSerif-Italic', 'MathJax_SansSerif-Regular', 'MathJax_Script-Regular',
  'MathJax_Size1-Regular', 'MathJax_Size2-Regular', 'MathJax_Size3-Regular',
  'MathJax_Size4-Regular', 'MathJax_Typewriter-Regular', 'MathJax_Vector-Bold',
  'MathJax_Vector-Regular', 'MathJax_Zero',
];

/**
 * @returns {Array<{pkg:string, src:string, file:string, group:string,
*                  optional?:boolean, tag?:'js'|'css', defer?:boolean}>}
 */
function buildAssets() {
  /** @type {any[]} */
  const assets = [
    { pkg: 'mermaid',   src: 'dist/mermaid.min.js',  file: 'mermaid.min.js',        group: 'mermaid',   tag: 'js' },
    { pkg: 'marked',    src: 'marked.min.js',        file: 'marked.min.js',         group: 'marked',    tag: 'js' },
    { pkg: 'dompurify', src: 'dist/purify.min.js',   file: 'purify.min.js',         group: 'dompurify', tag: 'js' },
    { pkg: 'mathjax',   src: 'es5/tex-mml-chtml.js', file: 'mathjax/tex-mml-chtml.js', group: 'mathjax', tag: 'js' },

    { pkg: 'prismjs', src: 'prism.min.js', file: 'prism/prism.min.js', group: 'prismjs', tag: 'js' },
    { pkg: 'prismjs', src: `themes/${PRISM_THEME}.min.css`, file: `prism/${PRISM_THEME}.min.css`, group: 'prismjs', tag: 'css' },
  ];

  for (const lang of PRISM_LANGUAGES) {
    assets.push({
      pkg: 'prismjs',
      src: `components/prism-${lang}.min.js`,
      file: `prism/components/prism-${lang}.min.js`,
      group: 'prismjs',
    });
  }

  for (const [plugin, hasCss] of PRISM_PLUGINS) {
    assets.push({
      pkg: 'prismjs',
      src: `plugins/${plugin}/prism-${plugin}.min.js`,
      file: `prism/plugins/prism-${plugin}.min.js`,
      group: 'prismjs',
    });
    if (hasCss) {
      assets.push({
        pkg: 'prismjs',
        src: `plugins/${plugin}/prism-${plugin}.min.css`,
        file: `prism/plugins/prism-${plugin}.min.css`,
        group: 'prismjs',
      });
    }
  }
  for (const [file, tag] of MONACO_CORE) {
    assets.push({
      pkg: 'monaco-editor',
      src: `${MONACO_SRC_BASE}/${file}`,
      file: `${MONACO_OUT_BASE}/${file}`,
      group: 'monaco',
      // Only the AMD loader goes into the HTML, and it must not be deferred
      // so that `require.config(...)` can run right after it.
      ...(tag ? { tag, defer: false } : {}),
    });
  }
  for (const lang of MONACO_BASIC_LANGUAGES) {
    assets.push({
      pkg: 'monaco-editor',
      src: `${MONACO_SRC_BASE}/basic-languages/${lang}/${lang}.js`,
      file: `${MONACO_OUT_BASE}/basic-languages/${lang}/${lang}.js`,
      group: 'monaco',
    });
  }
  for (const [dir, files] of MONACO_LANGUAGE_SERVICES) {
    for (const file of files) {
      assets.push({
        pkg: 'monaco-editor',
        src: `${MONACO_SRC_BASE}/language/${dir}/${file}`,
        file: `${MONACO_OUT_BASE}/language/${dir}/${file}`,
        group: 'monaco',
      });
    }
  }


  for (const font of MATHJAX_FONTS) {
    assets.push({
      pkg: 'mathjax',
      src: `es5/output/chtml/fonts/woff-v2/${font}.woff`,
      file: `mathjax/output/chtml/fonts/woff-v2/${font}.woff`,
      group: 'fonts',
      optional: true,
    });
  }

  return assets;
}

/* ------------------------------------------------------------------ *
 * CLI                                                                 *
 * ------------------------------------------------------------------ */

function parseArgs(argv) {
  const opts = {
    outDir: __dirname,
    force: false,
    fonts: false,
    dryRun: false,
    check: false,
    list: false,
    tags: false,
    quiet: false,
    resolve: true,
    only: /** @type {Set<string>|null} */ (null),
    concurrency: 6,
    timeout: 30_000,
    retries: 3,
  };

  for (const arg of argv) {
    const [key, value] = arg.includes('=') ? [arg.slice(0, arg.indexOf('=')), arg.slice(arg.indexOf('=') + 1)] : [arg, undefined];
    switch (key) {
      case '-h': case '--help': printHelp(); process.exit(0); break;
      case '-f': case '--force': opts.force = true; break;
      case '-q': case '--quiet': opts.quiet = true; break;
      case '-n': case '--dry-run': opts.dryRun = true; break;
      case '--fonts': opts.fonts = true; break;
      case '--check': opts.check = true; break;
      case '--list': opts.list = true; break;
      case '--tags': opts.tags = true; break;
      case '--no-resolve': opts.resolve = false; break;
      case '--out': case '--out-dir': opts.outDir = path.resolve(required(key, value)); break;
      case '--only': opts.only = new Set(required(key, value).split(',').map((s) => s.trim()).filter(Boolean)); break;
      case '--concurrency': opts.concurrency = Math.max(1, Number(required(key, value))); break;
      case '--timeout': opts.timeout = Math.max(1000, Number(required(key, value))); break;
      case '--retries': opts.retries = Math.max(0, Number(required(key, value))); break;
      default:
        fail(`unknown option: ${arg} (try --help)`);
    }
  }
  return opts;
}

function required(key, value) {
  if (value === undefined || value === '') fail(`option ${key} requires a value, e.g. ${key}=<value>`);
  return value;
}

function printHelp() {
  process.stdout.write(`
Download the web UI's third-party libraries from jsDelivr.

  node download.js [options]

Options:
  --out=DIR          target directory (default: this script's directory)
  --only=A,B         only download these groups/packages (${[...new Set(buildAssets().map((a) => a.group))].join(', ')})
  --fonts            include the MathJax CHTML web fonts
  -f, --force        re-download files that already exist
  -n, --dry-run      show what would happen, write nothing
  --check            verify the local files against ${MANIFEST} (offline)
  --list             list the resolved download URLs and exit
  --tags             print the HTML <script>/<link> snippet and exit
  --no-resolve       keep the major-version specifier instead of pinning
  --concurrency=N    parallel downloads (default 6)
  --timeout=MS       per-request timeout (default 30000)
  --retries=N        retries per file (default 3)
  -q, --quiet        only print errors and the final summary
  -h, --help         this text
`);
}

/* ------------------------------------------------------------------ *
 * HTTP helpers                                                        *
 * ------------------------------------------------------------------ */

function httpGet(url, opts, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > MAX_REDIRECTS) {
      reject(new Error(`too many redirects for ${url}`));
      return;
    }
    const target = new URL(url);
    const mod = target.protocol === 'http:' ? http : https;
    const req = mod.get(
      target,
      { headers: { 'user-agent': USER_AGENT, 'accept-encoding': 'gzip, deflate, br' } },
      (res) => {
        const status = res.statusCode || 0;
        if (status >= 300 && status < 400 && res.headers.location) {
          res.resume();
          const next = new URL(res.headers.location, url).toString();
          httpGet(next, opts, redirects + 1).then(resolve, reject);
          return;
        }
        if (status !== 200) {
          res.resume();
          const err = new Error(`HTTP ${status} ${res.statusMessage || ''} for ${url}`.trim());
          // 4xx (except 429) will not get better by retrying
          err.permanent = status >= 400 && status < 500 && status !== 429;
          reject(err);
          return;
        }
        resolve({ res, finalUrl: url });
      },
    );
    req.setTimeout(opts.timeout, () => req.destroy(new Error(`timeout after ${opts.timeout} ms: ${url}`)));
    req.on('error', reject);
  });
}

function decode(res) {
  const encoding = String(res.headers['content-encoding'] || '').toLowerCase();
  if (encoding === 'gzip') return res.pipe(zlib.createGunzip());
  if (encoding === 'deflate') return res.pipe(zlib.createInflate());
  if (encoding === 'br') return res.pipe(zlib.createBrotliDecompress());
  return res;
}

async function fetchText(url, opts) {
  const { res } = await httpGet(url, opts);
  const chunks = [];
  for await (const chunk of decode(res)) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf8');
}

/* ------------------------------------------------------------------ *
 * Download                                                            *
 * ------------------------------------------------------------------ */

function hashingTap(state) {
  return new Transform({
    transform(chunk, _enc, cb) {
      state.sha256.update(chunk);
      state.sha384.update(chunk);
      state.bytes += chunk.length;
      cb(null, chunk);
    },
  });
}

async function downloadToFile(url, dest, opts) {
  const { res, finalUrl } = await httpGet(url, opts);
  const state = { sha256: crypto.createHash('sha256'), sha384: crypto.createHash('sha384'), bytes: 0 };
  const tmp = `${dest}.${process.pid}.tmp`;

  await fsp.mkdir(path.dirname(dest), { recursive: true });
  try {
    await pipeline(decode(res), hashingTap(state), fs.createWriteStream(tmp));
    if (state.bytes === 0) throw new Error(`empty response body for ${url}`);
    await fsp.rename(tmp, dest);
  } catch (err) {
    await fsp.rm(tmp, { force: true }).catch(() => {});
    throw err;
  }

  return {
    bytes: state.bytes,
    sha256: state.sha256.digest('hex'),
    integrity: `sha384-${state.sha384.digest('base64')}`,
    finalUrl,
  };
}

async function hashFile(file) {
  const state = { sha256: crypto.createHash('sha256'), sha384: crypto.createHash('sha384'), bytes: 0 };
  await pipeline(fs.createReadStream(file), hashingTap(state), new Transform({ transform: (c, e, cb) => cb() }));
  return {
    bytes: state.bytes,
    sha256: state.sha256.digest('hex'),
    integrity: `sha384-${state.sha384.digest('base64')}`,
  };
}

async function withRetry(fn, { retries, onRetry }) {
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await fn(attempt);
    } catch (err) {
      lastError = err;
      if (err && err.permanent) break;
      if (attempt === retries) break;
      const delay = Math.min(8000, 400 * 2 ** attempt) + Math.floor(Math.random() * 250);
      if (onRetry) onRetry(err, attempt + 1, delay);
      await sleep(delay);
    }
  }
  throw lastError;
}

async function pool(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    for (;;) {
      const index = cursor++;
      if (index >= items.length) return;
      results[index] = await worker(items[index], index);
    }
  });
  await Promise.all(workers);
  return results;
}

/* ------------------------------------------------------------------ *
 * Version resolution                                                  *
 * ------------------------------------------------------------------ */

async function resolveVersions(packages, opts, log) {
  const resolved = {};
  for (const pkg of packages) {
    const specifier = VERSIONS[pkg] || 'latest';
    if (!opts.resolve) {
      resolved[pkg] = specifier;
      continue;
    }
    const url = `${DATA_API}/${pkg}/resolved?specifier=${encodeURIComponent(specifier)}`;
    try {
      const json = JSON.parse(await fetchText(url, opts));
      resolved[pkg] = json && json.version ? json.version : specifier;
      log(`  ${pkg.padEnd(12)} ${specifier} -> ${resolved[pkg]}`);
    } catch (err) {
      warn(`could not resolve ${pkg}@${specifier} (${err.message}); using the range as-is`);
      resolved[pkg] = specifier;
    }
  }
  return resolved;
}

/* ------------------------------------------------------------------ *
 * Output helpers                                                      *
 * ------------------------------------------------------------------ */

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const warn = (msg) => process.stderr.write(`warn: ${msg}\n`);
function fail(msg) {
  process.stderr.write(`error: ${msg}\n`);
  process.exit(2);
}

function humanSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MiB`;
}

function htmlTags(assets) {
  const lines = [];
  for (const asset of assets) {
    const href = `lib/${asset.file.split(path.sep).join('/')}`;
    if (asset.tag === 'css') lines.push(`<link rel="stylesheet" href="${href}">`);
    else if (asset.tag === 'js') lines.push(`<script src="${href}"${asset.defer === false ? '' : ' defer'}></script>`);
  }
  if (assets.some((a) => a.group === 'monaco')) {
    lines.push(
      '<script>',
      '  // Monaco is an AMD bundle: point the loader at the vendored copy.',
      "  require.config({ paths: { vs: 'lib/monaco/vs' } });",
      "  self.MonacoEnvironment = { getWorkerUrl: () => 'lib/monaco/vs/base/worker/workerMain.js' };",
      "  require(['vs/editor/editor.main'], () => { /* window.monaco is ready */ });",
      '</script>',
    );
  }
  return lines.join('\n');
}

/* ------------------------------------------------------------------ *
 * Modes                                                               *
 * ------------------------------------------------------------------ */

async function checkMode(opts) {
  const manifestPath = path.join(opts.outDir, MANIFEST);
  let manifest;
  try {
    manifest = JSON.parse(await fsp.readFile(manifestPath, 'utf8'));
  } catch (err) {
    fail(`cannot read ${manifestPath}: ${err.message} (run the downloader first)`);
  }

  let bad = 0;
  for (const entry of manifest.assets) {
    const file = path.join(opts.outDir, entry.file);
    try {
      const actual = await hashFile(file);
      if (actual.sha256 !== entry.sha256) {
        bad++;
        process.stderr.write(`MISMATCH ${entry.file}\n  expected ${entry.sha256}\n  actual   ${actual.sha256}\n`);
      } else if (!opts.quiet) {
        process.stdout.write(`ok       ${entry.file}\n`);
      }
    } catch (err) {
      bad++;
      process.stderr.write(`MISSING  ${entry.file} (${err.code || err.message})\n`);
    }
  }
  process.stdout.write(bad === 0
    ? `\n${manifest.assets.length} file(s) verified against ${MANIFEST}.\n`
    : `\n${bad} of ${manifest.assets.length} file(s) failed verification.\n`);
  return bad === 0 ? 0 : 1;
}

async function main(argv) {
  const opts = parseArgs(argv);
  const log = opts.quiet ? () => {} : (msg) => process.stdout.write(`${msg}\n`);

  if (opts.check) return checkMode(opts);

  let assets = buildAssets();
  if (opts.only) {
    assets = assets.filter((a) => opts.only.has(a.group) || opts.only.has(a.pkg));
    if (assets.length === 0) fail(`--only matched no assets (groups: ${[...new Set(buildAssets().map((a) => a.group))].join(', ')})`);
  } else if (!opts.fonts) {
    assets = assets.filter((a) => !a.optional);
  }

  const packages = [...new Set(assets.map((a) => a.pkg))];
  log(`Resolving versions from ${DATA_API} ...`);
  const versions = await resolveVersions(packages, opts, log);

  for (const asset of assets) {
    asset.version = versions[asset.pkg];
    asset.url = `${CDN}/${asset.pkg}@${asset.version}/${asset.src}`;
    asset.dest = path.join(opts.outDir, ...asset.file.split('/'));
  }

  if (opts.list) {
    for (const a of assets) process.stdout.write(`${a.url}\n  -> ${path.relative(process.cwd(), a.dest)}\n`);
    return 0;
  }
  if (opts.tags) {
    process.stdout.write(`${htmlTags(assets)}\n`);
    return 0;
  }

  log(`\nTarget: ${opts.outDir}`);
  log(`${assets.length} file(s), concurrency ${opts.concurrency}${opts.dryRun ? ' (dry run)' : ''}\n`);

  let downloaded = 0;
  let skipped = 0;
  let failed = 0;
  let totalBytes = 0;

  const results = await pool(assets, opts.concurrency, async (asset) => {
    const label = asset.file;
    try {
      if (opts.dryRun) {
        log(`plan     ${label}  <- ${asset.url}`);
        return { asset, status: 'planned' };
      }

      const exists = fs.existsSync(asset.dest);
      if (exists && !opts.force) {
        const info = await hashFile(asset.dest);
        skipped++;
        totalBytes += info.bytes;
        log(`skip     ${label} (${humanSize(info.bytes)})`);
        return { asset, status: 'skipped', ...info };
      }

      const info = await withRetry(
        () => downloadToFile(asset.url, asset.dest, opts),
        {
          retries: opts.retries,
          onRetry: (err, attempt, delay) => warn(`${label}: ${err.message} - retry ${attempt}/${opts.retries} in ${delay} ms`),
        },
      );
      downloaded++;
      totalBytes += info.bytes;
      log(`download ${label} (${humanSize(info.bytes)})`);
      return { asset, status: 'downloaded', ...info };
    } catch (err) {
      failed++;
      process.stderr.write(`FAILED   ${label}: ${err.message}\n`);
      return { asset, status: 'failed', error: err.message };
    }
  });

  if (!opts.dryRun) {
    const ok = results.filter((r) => r.status === 'downloaded' || r.status === 'skipped');
    const manifest = {
      generatedAt: new Date().toISOString(),
      cdn: CDN,
      packages: Object.fromEntries(packages.map((p) => [p, { specifier: VERSIONS[p] || 'latest', version: versions[p] }])),
      assets: ok
        .map((r) => ({
          name: r.asset.pkg,
          group: r.asset.group,
          file: r.asset.file,
          url: r.asset.url,
          version: r.asset.version,
          bytes: r.bytes,
          sha256: r.sha256,
          integrity: r.integrity,
        }))
        .sort((a, b) => a.file.localeCompare(b.file)),
    };
    await fsp.writeFile(path.join(opts.outDir, MANIFEST), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
    log(`\nWrote ${MANIFEST} (${manifest.assets.length} entries)`);
  }

  process.stdout.write(
    `\nDone: ${downloaded} downloaded, ${skipped} up-to-date, ${failed} failed, ${humanSize(totalBytes)} total\n`,
  );
  return failed === 0 ? 0 : 1;
}

if (require.main === module) {
  main(process.argv.slice(2))
    .then((code) => { process.exitCode = code; })
    .catch((err) => {
      process.stderr.write(`error: ${err && err.stack ? err.stack : err}\n`);
      process.exitCode = 2;
    });
}

module.exports = { buildAssets, VERSIONS, main };