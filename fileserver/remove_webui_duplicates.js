// Compare src and ../webui/src and find files that have been moved from webui into our new module, and remove the duplicates in webui

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const SRC_DIR = path.resolve(__dirname, 'src');
const WEBUI_SRC_DIR = path.resolve(__dirname, '..', 'webui', 'src');

const DRY_RUN = process.argv.includes('--dry-run');

/**
 * Recursively walk a directory and return a list of file paths (absolute).
 */
function walkDir(dir) {
    let results = [];
    if (!fs.existsSync(dir)) {
        return results;
    }

    const entries = fs.readdirSync(dir, {withFileTypes: true});
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            results = results.concat(walkDir(fullPath));
        } else if (entry.isFile()) {
            results.push(fullPath);
        }
    }

    return results;
}

/**
 * Compute a sha256 hash of a file's contents.
 */
function hashFile(filePath) {
    const contents = fs.readFileSync(filePath);
    return crypto.createHash('sha256').update(contents).digest('hex');
}

/**
 * Build a map of relative path -> hash for all files under baseDir.
 */
function buildFileMap(baseDir) {
    const files = walkDir(baseDir);
    const map = new Map();
    for (const filePath of files) {
        const relPath = path.relative(baseDir, filePath);
        map.set(relPath, hashFile(filePath));
    }
    return map;
}

function main() {
    console.log(`Comparing:\n  src:      ${SRC_DIR}\n  webui/src: ${WEBUI_SRC_DIR}\n`);

    if (!fs.existsSync(SRC_DIR)) {
        console.error(`Source directory not found: ${SRC_DIR}`);
        process.exit(1);
    }

    if (!fs.existsSync(WEBUI_SRC_DIR)) {
        console.error(`Webui source directory not found: ${WEBUI_SRC_DIR}`);
        process.exit(1);
    }

    const srcMap = buildFileMap(SRC_DIR);
    const webuiMap = buildFileMap(WEBUI_SRC_DIR);

    const duplicates = [];
    const movedOrChanged = [];

    for (const [relPath, webuiHash] of webuiMap.entries()) {
        if (srcMap.has(relPath)) {
            const srcHash = srcMap.get(relPath);
            if (srcHash === webuiHash) {
                duplicates.push(relPath);
            } else {
                movedOrChanged.push(relPath);
            }
        }
    }

    if (movedOrChanged.length > 0) {
        console.log('Files present in both locations but with DIFFERENT contents (left alone):');
        for (const relPath of movedOrChanged) {
            console.log(`  ~ ${relPath}`);
        }
        console.log('');
    }

    if (duplicates.length === 0) {
        console.log('No duplicate files found. Nothing to remove.');
        return;
    }

    console.log(`Found ${duplicates.length} duplicate file(s) to remove from webui/src:`);
    for (const relPath of duplicates) {
        const targetPath = path.join(WEBUI_SRC_DIR, relPath);
        if (DRY_RUN) {
            console.log(`  [dry-run] would remove: ${targetPath}`);
        } else {
            fs.unlinkSync(targetPath);
            console.log(`  removed: ${targetPath}`);
        }
    }

    if (!DRY_RUN) {
        removeEmptyDirs(WEBUI_SRC_DIR);
    }

    console.log('\nDone.');
}

/**
 * Recursively remove empty directories under a given directory.
 */
function removeEmptyDirs(dir) {
    if (!fs.existsSync(dir)) {
        return;
    }

    const entries = fs.readdirSync(dir, {withFileTypes: true});
    for (const entry of entries) {
        if (entry.isDirectory()) {
            const fullPath = path.join(dir, entry.name);
            removeEmptyDirs(fullPath);
        }
    }

    const remaining = fs.readdirSync(dir);
    if (remaining.length === 0 && dir !== WEBUI_SRC_DIR) {
        fs.rmdirSync(dir);
    }
}

main();