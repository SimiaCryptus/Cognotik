#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

/**
 * Recursively find all files with given extensions in a directory
 */
function findFiles(dir, extensions, files = []) {
    if (!fs.existsSync(dir)) {
        return files;
    }

    const items = fs.readdirSync(dir);

    for (const item of items) {
        const fullPath = path.join(dir, item);
        const stat = fs.statSync(fullPath);

        if (stat.isDirectory()) {
            // Skip node_modules and other common directories
            if (!['node_modules', '.git', 'dist', 'build'].includes(item)) {
                findFiles(fullPath, extensions, files);
            }
        } else if (extensions.some(ext => item.endsWith(ext))) {
            files.push(fullPath);
        }
    }

    return files;
}

/**
 * Extract narration keys from file content
 */
function extractNarrationKeys(content) {
    const keys = new Set();

    // Pattern 1: cy.narrate('key')
    const narratePattern = /cy\.narrate\(['"`]([^'"`]+)['"`]\)/g;
    let match;
    while ((match = narratePattern.exec(content)) !== null) {
        keys.add(match[1]);
    }

    // Pattern 2: narrate('key') - in case cy. is omitted
    const narratePattern2 = /narrate\(['"`]([^'"`]+)['"`]\)/g;
    while ((match = narratePattern2.exec(content)) !== null) {
        keys.add(match[1]);
    }

    // Pattern 3: Any other patterns you might use
    // Add more patterns here if needed

    return Array.from(keys);
}

/**
 * Load narrations.json and extract defined keys
 */
function loadNarrationKeys(narrationsPath) {
    try {
        const content = fs.readFileSync(narrationsPath, 'utf8');
        const narrations = JSON.parse(content);
        return Object.keys(narrations);
    } catch (error) {
        console.error(`Error loading narrations.json: ${error.message}`);
        return [];
    }
}
/**
 * Find duplicate keys in narrations.json
 */
function findDuplicateKeys(narrationsPath) {
    try {
        const content = fs.readFileSync(narrationsPath, 'utf8');
        const narrations = JSON.parse(content);
        const duplicates = {};
        const seen = new Map();
        for (const [key, value] of Object.entries(narrations)) {
            const valueStr = JSON.stringify(value);
            if (seen.has(valueStr)) {
                const originalKey = seen.get(valueStr);
                if (!duplicates[originalKey]) {
                    duplicates[originalKey] = [originalKey];
                }
                duplicates[originalKey].push(key);
            } else {
                seen.set(valueStr, key);
            }
        }
        return duplicates;
    } catch (error) {
        console.error(`Error analyzing duplicates in narrations.json: ${error.message}`);
        return {};
    }
}


/**
 * Main analysis function
 */
function analyzeNarrationUsage() {
    const projectRoot = process.cwd();
    const narrationsPath = path.join(projectRoot, 'cypress/fixtures/narrations.json');

    console.log('🔍 Analyzing narration key usage...\n');
    console.log(`Project root: ${projectRoot}`);
    console.log(`Narrations file: ${narrationsPath}\n`);

    // Load defined narration keys
    const definedKeys = loadNarrationKeys(narrationsPath);
    console.log(`📚 Found ${definedKeys.length} defined narration keys\n`);
    // Find duplicate keys
    const duplicateKeys = findDuplicateKeys(narrationsPath);
    const duplicateCount = Object.keys(duplicateKeys).length;
    if (duplicateCount > 0) {
        console.log(`🔄 Found ${duplicateCount} sets of duplicate narration values\n`);
    }


    // Find all relevant files
    const extensions = ['.js', '.ts', '.jsx', '.tsx', '.cy.js', '.spec.js'];
    const files = findFiles(path.join(projectRoot, 'cypress/e2e/'), extensions);
    console.log(`📁 Scanning ${files.length} files for narration usage...\n`);

    // Extract used keys from all files
    const usedKeys = new Set();
    const fileUsage = {};

    for (const file of files) {
        try {
            const content = fs.readFileSync(file, 'utf8');
            const keysInFile = extractNarrationKeys(content);

            if (keysInFile.length > 0) {
                fileUsage[file] = keysInFile;
                keysInFile.forEach(key => usedKeys.add(key));
            }
        } catch (error) {
            console.warn(`⚠️  Could not read file ${file}: ${error.message}`);
        }
    }

    const usedKeysArray = Array.from(usedKeys);

    // Find missing keys (used but not defined)
   const missingKeys = usedKeysArray.filter(key => !definedKeys.includes(key));

    // Find unused keys (defined but not used)
    const unusedKeys = definedKeys.filter(key => !usedKeys.has(key));

    // Display results
    console.log('📊 ANALYSIS RESULTS');
    console.log('='.repeat(50));
    console.log(`Total defined keys: ${definedKeys.length}`);
    console.log(`Total used keys: ${usedKeysArray.length}`);
    console.log(`Missing keys: ${missingKeys.length}`);
    console.log(`Unused keys: ${unusedKeys.length}\n`);
    console.log(`Duplicate values: ${duplicateCount}\n`);
    // Show duplicate keys
    if (duplicateCount > 0) {
        console.log('🔄 DUPLICATE VALUES (same content, different keys):');
        console.log('-'.repeat(40));
        for (const [originalKey, duplicateGroup] of Object.entries(duplicateKeys)) {
            console.log(`  📝 Duplicate group (${duplicateGroup.length} keys):`);
            duplicateGroup.forEach((key, index) => {
                const isUsed = usedKeys.has(key);
                const status = isUsed ? '✅ (used)' : '⚠️  (unused)';
                const prefix = index === 0 ? '    ├─' : '    ├─';
                console.log(`${prefix} ${key} ${status}`);
            });
            console.log();
        }
    }


    // Show missing keys
    if (missingKeys.length > 0) {
        console.log('❌ MISSING KEYS (used but not defined):');
        console.log('-'.repeat(40));
        missingKeys.forEach(key => {
            console.log(`  • ${key}`);
            // Show where it's used
           const filesUsingKey = [];
            for (const [file, keys] of Object.entries(fileUsage)) {
                if (keys.includes(key)) {
                   filesUsingKey.push(path.relative(projectRoot, file));
                }
            }
           filesUsingKey.forEach((file, index) => {
               const prefix = index === filesUsingKey.length - 1 ? '    └─' : '    ├─';
               console.log(`${prefix} Used in: ${file}`);
           });
        });
        console.log();
    }

    // Show unused keys
    if (unusedKeys.length > 0) {
        console.log('⚠️  UNUSED KEYS (defined but not used):');
        console.log('-'.repeat(40));
        unusedKeys.forEach(key => {
            console.log(`  • ${key}`);
        });
        console.log();
    }

    // Show usage by file
    if (Object.keys(fileUsage).length > 0) {
        console.log('📋 USAGE BY FILE:');
        console.log('-'.repeat(40));
        for (const [file, keys] of Object.entries(fileUsage)) {
            const relativePath = path.relative(projectRoot, file);
            console.log(`📄 ${relativePath} (${keys.length} keys):`);
            keys.forEach(key => {
                const status = definedKeys.includes(key) ? '✅' : '❌';
                console.log(`    ${status} ${key}`);
            });
            console.log();
        }
    }

    // Summary
    console.log('📈 SUMMARY:');
    console.log('-'.repeat(40));
    if (missingKeys.length === 0 && unusedKeys.length === 0 && duplicateCount === 0) {
        console.log('🎉 Perfect! All narration keys are properly defined and used.');
    } else {
        if (missingKeys.length > 0) {
            console.log(`❌ ${missingKeys.length} keys need to be added to narrations.json`);
        }
        if (unusedKeys.length > 0) {
            console.log(`⚠️  ${unusedKeys.length} keys are defined but never used`);
        }
        if (duplicateCount > 0) {
            console.log(`🔄 ${duplicateCount} sets of keys have duplicate values (consider consolidating)`);
        }
    }

    // Generate JSON report
    const report = {
        timestamp: new Date().toISOString(),
        summary: {
            totalDefined: definedKeys.length,
            totalUsed: usedKeysArray.length,
            missing: missingKeys.length,
            unused: unusedKeys.length,
            duplicates: duplicateCount
        },
        definedKeys,
        usedKeys: usedKeysArray,
        missingKeys,
        unusedKeys,
        duplicateKeys,
        fileUsage
    };

    const reportPath = path.join(projectRoot, 'narration-analysis-report.json');
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
    console.log(`\n📄 Detailed report saved to: ${reportPath}`);
}

// Run the analysis
if (require.main === module) {
    analyzeNarrationUsage();
}

module.exports = {
    analyzeNarrationUsage,
    extractNarrationKeys,
    loadNarrationKeys,
    findDuplicateKeys,
    findFiles
};