#!/usr/bin/env node
'use strict';
/**
 * generate-related-md.js
 *
 * Recursively scans for symbol-index JSON files (e.g. `.data/Foo.kt.json`) and
 * generates a peer `Foo.related.md` file containing YAML frontmatter of the form:
 *
 *   ---
 *   specifies: ../Foo.kt
 *   related:
 *     - ../../../../bar/Bar.kt
 *     - ...every entry from relatedFiles...
 *   ---
 *
 * Usage:
 *   node tools/generate-related-md.js [roots...] [options]
 *
 * Options:
 *   --data-dir <name>   Only scan JSON inside directories with this name (default: ".data")
 *   --any-dir           Scan every *.json file, not just those in --data-dir
 *   --suffix <s>        Output suffix (default: ".related.md")
 *   --min-score <n>     Drop relatedFiles entries with score < n (default: 0 = keep all)
 *   --max <n>           Keep at most n related entries (default: unlimited)
 *   --with-scores       Append `# score=..` YAML comments to each related entry
 *   --verbatim          Emit relatedFiles paths exactly as found in the JSON
 *                       (default: re-base them relative to the generated .md file)
 *   --stamp             Add a `generated: <ISO timestamp>` frontmatter key
 *   --ignore <name>     Extra directory name to skip (repeatable)
 *   --check             Do not write; exit 1 if any file is missing/out of date
 *   --dry-run           Do not write; just report what would change
 *   --quiet             Only print the summary
 *   -h, --help          Show this help
 */

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');

const DEFAULT_IGNORES = [
    'node_modules', '.git', '.hg', '.svn', '.gradle', '.idea', '.vscode',
    'build', 'out', 'target', 'dist', 'bin', '.venv', 'venv', '__pycache__',
];

/* ------------------------------------------------------------------ */
/* CLI                                                                 */

/* ------------------------------------------------------------------ */

function parseArgs(argv) {
    const opts = {
        roots: [],
        dataDirName: '.data',
        anyDir: false,
        suffix: '.related.md',
        minScore: 0,
        max: Infinity,
        withScores: false,
        verbatim: false,
        stamp: false,
        check: false,
        dryRun: false,
        quiet: false,
        ignore: new Set(DEFAULT_IGNORES),
    };
    for (let i = 2; i < argv.length; i++) {
        const a = argv[i];
        const next = () => {
            const v = argv[++i];
            if (v === undefined) throw new Error(`Missing value for ${a}`);
            return v;
        };
        switch (a) {
            case '--data-dir':
                opts.dataDirName = next();
                break;
            case '--any-dir':
                opts.anyDir = true;
                break;
            case '--suffix':
                opts.suffix = next();
                break;
            case '--min-score':
                opts.minScore = Number(next());
                break;
            case '--max':
                opts.max = Number(next());
                break;
            case '--with-scores':
                opts.withScores = true;
                break;
            case '--verbatim':
                opts.verbatim = true;
                break;
            case '--stamp':
                opts.stamp = true;
                break;
            case '--ignore':
                opts.ignore.add(next());
                break;
            case '--check':
                opts.check = true;
                break;
            case '--dry-run':
                opts.dryRun = true;
                break;
            case '--quiet':
                opts.quiet = true;
                break;
            case '-h':
            case '--help':
                opts.help = true;
                break;
            default:
                if (a.startsWith('-')) throw new Error(`Unknown option: ${a}`);
                opts.roots.push(a);
        }
    }
    if (opts.roots.length === 0) opts.roots.push(process.cwd());
    return opts;
}

function printHelp() {
    const header = fs.readFileSync(__filename, 'utf8').split('*/')[0];
    console.log(header.replace(/^#!.*\n/, '').replace(/^\/\*\*?/, '').replace(/^\s*\*ular?/gm, '')
        .split('\n').map(l => l.replace(/^\s*\*ic?\s?/, '').replace(/^\s*\* ?/, '')).join('\n').trim());
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

const toPosix = p => p.split(path.sep).join('/');
/** Find the source directory corresponding to a json file inside the consolidated .data folder. */
function resolveSourceDir(jsonPath, dataDirName) {
    const dataDir = path.dirname(jsonPath);
    const parts = dataDir.split(path.sep);
    const idx = parts.lastIndexOf(dataDirName);
    if (idx !== -1) {
        const projectRoot = parts.slice(0, idx).join(path.sep) || path.sep;
        const relDir = parts.slice(idx + 1).join(path.sep);
        return path.join(projectRoot, relDir);
    }
    return path.dirname(dataDir);
}

/** Relative POSIX path from `fromDir` to `targetAbs`, always explicitly relative. */
function relFrom(fromDir, targetAbs) {
    let r = toPosix(path.relative(fromDir, targetAbs));
    if (r === '') r = '.';
    if (!r.startsWith('.')) r = './' + r;
    return r;
}

/** Quote a scalar only when YAML would otherwise misread it. */
function yamlScalar(s) {
    const safe = /^[A-Za-z0-9._~\/\\+@][A-Za-z0-9._~\/\\+@ ()\-]*$/.test(s)
        && !/^[-?:,\[\]{}#&*!|>'"%`]/.test(s)
        && !/:\s/.test(s)
        && !/\s#/.test(s)
        && s.trim() === s;
    return safe ? s : JSON.stringify(s);
}

/** Split an existing markdown file into (frontmatter text | null, body). */
function splitFrontmatter(text) {
    const m = /^---[ \t]*\r?\n([\s\S]*?)\r?\n---[ \t]*(?:\r?\n|$)/.exec(text);
    if (!m) return {fm: null, body: text};
    return {fm: m[1], body: text.slice(m[0].length)};
}

/**
 * Remove the keys this script owns (`specifies`, `related`, `generated`) from an
 * existing frontmatter block so user-authored keys survive regeneration.
 */
function preservedFrontmatterLines(fmText) {
    if (!fmText) return [];
    const managed = /^(specifies|related|generated)\s*:/;
    const out = [];
    let inManagedBlock = false;
    for (const line of fmText.split(/\r?\n/)) {
        if (inManagedBlock) {
            // list items / nested block scalars belonging to the managed key
            if (/^\s+\S/.test(line) || line.trim() === '') continue;
            inManagedBlock = false;
        }
        if (managed.test(line)) {
            inManagedBlock = true;
            continue;
        }
        if (line.trim() === '') continue;
        out.push(line);
    }
    return out;
}

/* ------------------------------------------------------------------ */
/* Scanning                                                            */

/* ------------------------------------------------------------------ */

async function* walk(dir, opts, inDataDir = false) {
    let entries;
    try {
        entries = await fsp.readdir(dir, {withFileTypes: true});
    } catch (e) {
        if (!opts.quiet) console.warn(`! cannot read ${dir}: ${e.message}`);
        return;
    }
    const isCurrentDataDir = inDataDir || path.basename(dir) === opts.dataDirName || dir.split(path.sep).includes(opts.dataDirName);
    for (const entry of entries) {
        const full = path.join(dir, entry.name);
        if (entry.isSymbolicLink()) continue;
        const isData = isCurrentDataDir || entry.name === opts.dataDirName;
        if (entry.isDirectory()) {
            if (opts.ignore.has(entry.name)) continue;
            yield* walk(full, opts, isData);
        } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.json')) {
            if (!opts.anyDir && !isData) continue;
            yield full;
        }
    }
}

/* ------------------------------------------------------------------ */
/* Generation                                                          */

/* ------------------------------------------------------------------ */

function outputPathFor(jsonPath, data, opts) {
    // Foo.kt.json -> Foo.kt -> Foo -> Foo.related.md
    const stripped = path.basename(jsonPath).replace(/\.json$/i, '');
    const sourceName = data && typeof data.name === 'string' && data.name ? data.name : stripped;
    const base = path.basename(sourceName, path.extname(sourceName)) || sourceName;
    return path.join(path.dirname(jsonPath), base + opts.suffix);
}

function buildMarkdown(jsonPath, data, opts, existing) {
    const dataDir = path.dirname(jsonPath);          // .../.data
    const sourceDir = resolveSourceDir(jsonPath, opts.dataDirName);

    const sourceRel = typeof data.path === 'string' && data.path
        ? data.path
        : path.basename(jsonPath).replace(/\.json$/i, '');
    const sourceAbs = path.resolve(sourceDir, sourceRel);
    const specifies = opts.verbatim ? toPosix(sourceRel) : relFrom(dataDir, sourceAbs);

    const seen = new Set();
    const related = [];
    for (const rf of Array.isArray(data.relatedFiles) ? data.relatedFiles : []) {
        if (!rf || typeof rf.path !== 'string' || !rf.path) continue;
        if (typeof rf.score === 'number' && rf.score < opts.minScore) continue;
        const abs = path.resolve(sourceDir, rf.path);
        if (abs === sourceAbs) continue;                 // never relate a file to itself
        const rel = opts.verbatim ? toPosix(rf.path) : relFrom(dataDir, abs);
        if (seen.has(rel)) continue;
        seen.add(rel);
        related.push({rel, score: rf.score, abs});
        if (related.length >= opts.max) break;
    }

    const extras = preservedFrontmatterLines(existing ? splitFrontmatter(existing).fm : null);

    const lines = ['---'];
    lines.push(`specifies: ${yamlScalar(specifies)}`);
    if (related.length === 0) {
        lines.push('related: []');
    } else {
        lines.push('related:');
        for (const r of related) {
            const comment = opts.withScores && typeof r.score === 'number' ? ` # score=${r.score}` : '';
            lines.push(`  - ${yamlScalar(r.rel)}${comment}`);
        }
    }
    if (opts.stamp) lines.push(`generated: ${new Date().toISOString()}`);
    lines.push(...extras);
    lines.push('---');

    let body = existing ? splitFrontmatter(existing).body : '';
    body = body.replace(/^\s*\n/, '');   // collapse leading blank lines; we add exactly one
    body = body + ''


    return {content: lines.join('\n') + '\n\n' + body, related, sourceAbs};
}

async function processJson(jsonPath, opts, stats) {
    let raw;
    try {
        raw = await fsp.readFile(jsonPath, 'utf8');
    } catch (e) {
        stats.errors.push(`${jsonPath}: ${e.message}`);
        return;
    }

    let data;
    try {
        data = JSON.parse(raw);
    } catch (e) {
        stats.skipped++;
        return; // not our kind of JSON
    }
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
        stats.skipped++;
        return;
    }
    if (typeof data.path !== 'string' && !Array.isArray(data.relatedFiles)) {
        stats.skipped++;
        return;
    }

    stats.scanned++;
    const outPath = outputPathFor(jsonPath, data, opts);
    let existing = null;
    try {
        existing = await fsp.readFile(outPath, 'utf8');
    } catch (e) {
        if (e.code !== 'ENOENT') {
            stats.errors.push(`${outPath}: ${e.message}`);
            return;
        }
    }

    const {content, related, sourceAbs} = buildMarkdown(jsonPath, data, opts, existing);

    if (!fs.existsSync(sourceAbs)) {
        stats.warnings.push(`source not found for ${toPosix(path.relative(process.cwd(), jsonPath))}: ${toPosix(path.relative(process.cwd(), sourceAbs))}`);
    }

    const rel = toPosix(path.relative(process.cwd(), outPath));
    if (existing === content) {
        stats.unchanged++;
        if (!opts.quiet) console.log(`= ${rel} (${related.length} related)`);
        return;
    }

    stats.stale.push(rel);
    if (opts.check || opts.dryRun) {
        if (!opts.quiet) console.log(`${existing === null ? '+' : '~'} ${rel} (${related.length} related)${opts.dryRun ? ' [dry-run]' : ''}`);
        if (existing === null) stats.created++; else stats.updated++;
        return;
    }

    await fsp.mkdir(path.dirname(outPath), {recursive: true});
    await fsp.writeFile(outPath, content, 'utf8');
    if (existing === null) stats.created++; else stats.updated++;
    if (!opts.quiet) console.log(`${existing === null ? '+' : '~'} ${rel} (${related.length} related)`);
}

/* ------------------------------------------------------------------ */
/* Main                                                                */

/* ------------------------------------------------------------------ */

async function main() {
    let opts;
    try {
        opts = parseArgs(process.argv);
    } catch (e) {
        console.error(String(e.message));
        process.exit(2);
    }
    if (opts.help) {
        printHelp();
        return;
    }

    const stats = {
        scanned: 0, skipped: 0, created: 0, updated: 0, unchanged: 0,
        stale: [], errors: [], warnings: [],
    };

    for (const root of opts.roots) {
        const abs = path.resolve(root);
        const st = await fsp.stat(abs).catch(() => null);
        if (!st) {
            stats.errors.push(`root not found: ${root}`);
            continue;
        }
        if (st.isFile()) {
            await processJson(abs, opts, stats);
        } else {
            for await (const jsonPath of walk(abs, opts)) {
                await processJson(jsonPath, opts, stats);
            }
        }
    }

    for (const w of stats.warnings) console.warn(`! ${w}`);
    for (const e of stats.errors) console.error(`x ${e}`);

    console.log(
        `\n${stats.scanned} index file(s); ` +
        `${stats.created} created, ${stats.updated} updated, ${stats.unchanged} unchanged` +
        (stats.skipped ? `, ${stats.skipped} non-index json skipped` : '') +
        (opts.dryRun ? ' (dry-run)' : '')
    );

    if (stats.errors.length) process.exit(1);
    if (opts.check && stats.stale.length) {
        console.error(`\n${stats.stale.length} .related.md file(s) are out of date. Run: node tools/generate-related-md.js`);
        process.exit(1);
    }
}

main().catch(err => {
    console.error(err && err.stack || err);
    process.exit(1);
});