#!/usr/bin/env node
'use strict';

/**
 * For every file under ./src (relative to this script), delete the file with the
 * same relative path under ../core/src.
 *
 * e.g.  ./src/main/kotlin/com/simiacryptus/cognotik/diff/FileValidators.kt
 *   ->  ../core/src/main/kotlin/com/simiacryptus/cognotik/diff/FileValidators.kt
 *
 * Usage:
 *   node cleanup.js [--dry-run|-n] [--verbose|-v] [--keep-empty-dirs]
 */

const fs = require('fs');
const path = require('path');

const SRC_DIR = path.resolve(__dirname, 'src');
const TARGET_DIR = path.resolve(__dirname, '..', 'core', 'src');

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run') || args.includes('-n');
const verbose = args.includes('--verbose') || args.includes('-v');
const pruneEmptyDirs = !args.includes('--keep-empty-dirs');

/** Recursively yield every file path under `dir`. */
function* walk(dir) {
    let entries;
    try {
        entries = fs.readdirSync(dir, {withFileTypes: true});
    } catch (err) {
        if (err.code === 'ENOENT') return;
        throw err;
    }
    for (const entry of entries) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            yield* walk(full);
        } else if (entry.isFile()) {
            yield full;
        }
    }
}

/** Remove `dir` and its parents (up to `stopAt`) while they are empty. */
function pruneEmptyParents(dir, stopAt) {
    let current = path.resolve(dir);
    const stop = path.resolve(stopAt);
    while (current.startsWith(stop) && current !== stop) {
        let entries;
        try {
            entries = fs.readdirSync(current);
        } catch (err) {
            if (err.code === 'ENOENT') {
                current = path.dirname(current);
                continue;
            }
            throw err;
        }
        if (entries.length > 0) break;
        if (dryRun) {
            console.log(`[dry-run] rmdir  ${path.relative(process.cwd(), current)}`);
        } else {
            try {
                fs.rmdirSync(current);
                if (verbose) console.log(`rmdir  ${path.relative(process.cwd(), current)}`);
            } catch (err) {
                if (err.code !== 'ENOENT' && err.code !== 'ENOTEMPTY') throw err;
                break;
            }
        }
        current = path.dirname(current);
    }
}

function main() {
    if (!fs.existsSync(SRC_DIR)) {
        console.error(`Source directory not found: ${SRC_DIR}`);
        process.exitCode = 1;
        return;
    }
    if (!fs.existsSync(TARGET_DIR)) {
        console.error(`Target directory not found: ${TARGET_DIR}`);
        process.exitCode = 1;
        return;
    }

    let scanned = 0;
    let deleted = 0;
    let missing = 0;
    const failures = [];
    const touchedDirs = new Set();

    for (const srcFile of walk(SRC_DIR)) {
        scanned++;
        const relative = path.relative(SRC_DIR, srcFile);
        const targetFile = path.join(TARGET_DIR, relative);

        if (!fs.existsSync(targetFile)) {
            missing++;
            if (verbose) console.log(`skip   ${relative} (not present in target)`);
            continue;
        }

        if (dryRun) {
            console.log(`[dry-run] delete ${path.relative(process.cwd(), targetFile)}`);
            deleted++;
        } else {
            try {
                fs.unlinkSync(targetFile);
                deleted++;
                if (verbose) console.log(`delete ${path.relative(process.cwd(), targetFile)}`);
            } catch (err) {
                failures.push({file: targetFile, error: err.message});
                continue;
            }
        }
        touchedDirs.add(path.dirname(targetFile));
    }

    if (pruneEmptyDirs) {
        // Deepest directories first so parents can also become empty.
        const dirs = Array.from(touchedDirs).sort((a, b) => b.length - a.length);
        for (const dir of dirs) pruneEmptyParents(dir, TARGET_DIR);
    }

    console.log('');
    console.log(`Source : ${SRC_DIR}`);
    console.log(`Target : ${TARGET_DIR}`);
    console.log(`Scanned: ${scanned} file(s)`);
    console.log(`${dryRun ? 'Would delete' : 'Deleted'}: ${deleted} file(s)`);
    console.log(`Skipped: ${missing} file(s) (no counterpart in target)`);
    if (failures.length) {
        console.log(`Failed : ${failures.length} file(s)`);
        for (const f of failures) console.error(`  ${f.file}: ${f.error}`);
        process.exitCode = 1;
    }
}

main();