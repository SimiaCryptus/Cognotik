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
 * Load full narrations object
 */
function loadNarrations(narrationsPath) {
    try {
        const content = fs.readFileSync(narrationsPath, 'utf8');
        return JSON.parse(content);
    } catch (error) {
        console.error(`Error loading narrations.json: ${error.message}`);
        return {};
    }
}
/**
 * Generate markdown report with compiled narration scripts
 */
function generateMarkdownReport(narrations, usedKeys, unusedKeys, missingKeys, duplicateKeys, fileUsage, projectRoot) {
    const timestamp = new Date().toISOString();
    const usedKeysSet = new Set(usedKeys);
    let markdown = `# Narration Analysis Report
Generated on: ${timestamp}
## Summary
---
## Demo Narration Scripts

`;

    // Group narrations by demo file
    const sortedFiles = Object.keys(fileUsage).sort();
    for (const file of sortedFiles) {
        const relativePath = path.relative(projectRoot, file);
        const keys = fileUsage[file];
        
        markdown += `### ${relativePath}\n\n`;
        
        // Add each narration used in this demo
        for (const key of keys) {
            if (narrations[key]) {
                const content = narrations[key];
                markdown += `<a id="${key}"></a>\n\n`;
                
                if (typeof content === 'string') {
                    markdown += `${content}\n\n`;
                } else if (typeof content === 'object') {
                    // Convert object to readable text instead of JSON
                    markdown += `${content['text'] || ''}\n\n`;
                }
            } else {
                // Missing narration
                markdown += `#### <a id="${key}"></a>\`${key}\` ❌ MISSING\n\n`;
                markdown += `*This narration key is used but not defined in narrations.json*\n\n`;
            }
        }
        
        markdown += `\n\n`;
    }

    // Add unused narrations section
    if (unusedKeys.length > 0) {
        markdown += `### Unused Narrations (Non-displayed)\n\n`;
        markdown += `<!-- UNUSED_NARRATION_IDS: ${unusedKeys.join(', ')} -->\n\n`;
        const sortedUnusedKeys = unusedKeys.sort();
        for (const key of sortedUnusedKeys) {
            if (narrations[key]) {
                const content = narrations[key];
                markdown += `#### <a id="${key}"></a>\`${key}\` ⚠️ UNUSED\n\n`;
                if (typeof content === 'string') {
                    markdown += `${content}\n\n`;
                } else if (typeof content === 'object') {
                    const textContent = JSON.stringify(content).replace(/[{}",]/g, '').replace(/:/g, ': ');
                    markdown += `${textContent}\n\n`;
                }
                markdown += `\n\n`;
            }
        }
    }
    // Add missing keys section
    if (missingKeys.length > 0) {
        markdown += `### Missing Narrations\n\n`;
        markdown += `<!-- MISSING_NARRATION_IDS: ${missingKeys.join(', ')} -->\n\n`;
        markdown += `The following narration keys are used in code but not defined in narrations.json:\n\n`;
        for (const key of missingKeys) {
            markdown += `#### <a id="${key}"></a>\`${key}\` ❌ MISSING\n\n`;
            // Show where it's used
            const filesUsingKey = [];
            for (const [file, keys] of Object.entries(fileUsage)) {
                if (keys.includes(key)) {
                    filesUsingKey.push(path.relative(projectRoot, file));
                }
            }
            if (filesUsingKey.length > 0) {
                markdown += `*Used in:*\n`;
                filesUsingKey.forEach(file => {
                    markdown += `- ${file}\n`;
                });
                markdown += `\n`;
            }
            markdown += `\n\n`;
        }
    }
    // Add duplicate values section
    if (Object.keys(duplicateKeys).length > 0) {
        markdown += `### Duplicate Values\n\n`;
        markdown += `The following keys have identical content and could potentially be consolidated:\n\n`;
        for (const [originalKey, duplicateGroup] of Object.entries(duplicateKeys)) {
            markdown += `#### Duplicate Group\n\n`;
            // Show the content once
            if (narrations[originalKey]) {
                const content = narrations[originalKey];
                if (typeof content === 'string') {
                    markdown += `**Content:** ${content}\n\n`;
                } else if (typeof content === 'object') {
                    const textContent = JSON.stringify(content).replace(/[{}",]/g, '').replace(/:/g, ': ');
                    markdown += `**Content:** ${textContent}\n\n`;
                }
            }
            markdown += `**Keys with this content:**\n`;
            duplicateGroup.forEach(key => {
                const isUsed = usedKeysSet.has(key);
                const status = isUsed ? '✅ (used)' : '⚠️ (unused)';
                markdown += `- [\`${key}\`](#${key}) ${status}\n`;
            });
            markdown += `\n\n\n`;
        }
    }
    // Add file usage section
    if (Object.keys(fileUsage).length > 0) {
        markdown += `### Usage by File\n\n`;
        for (const [file, keys] of Object.entries(fileUsage)) {
            const relativePath = path.relative(projectRoot, file);
            markdown += `#### ${relativePath}\n\n`;
            markdown += `Uses ${keys.length} narration key(s):\n\n`;
            keys.forEach(key => {
                const isDefined = narrations[key] !== undefined;
                const status = isDefined ? '✅' : '❌';
                markdown += `- ${status} [\`${key}\`](#${key})\n`;
            });
            markdown += `\n`;
        }
    }
    // Add metadata section
    markdown += `---\n\n## Metadata\n\n`;
    markdown += `<!-- NARRATION_METADATA\n`;
    markdown += `TOTAL_DEFINED: ${Object.keys(narrations).length}\n`;
    markdown += `TOTAL_USED: ${usedKeys.length}\n`;
    markdown += `MISSING_COUNT: ${missingKeys.length}\n`;
    markdown += `UNUSED_COUNT: ${unusedKeys.length}\n`;
    markdown += `DUPLICATE_COUNT: ${Object.keys(duplicateKeys).length}\n`;
    markdown += `GENERATED: ${timestamp}\n`;
    markdown += `-->\n\n`;
    return markdown;
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
    // Load full narrations object for markdown report
    const narrations = loadNarrations(narrationsPath);
    
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
    // Generate markdown report
    const markdownReport = generateMarkdownReport(
        narrations,
        usedKeysArray,
        unusedKeys,
        missingKeys,
        duplicateKeys,
        fileUsage,
        projectRoot
    );
    const markdownPath = path.join(projectRoot, 'narration-analysis-report.md');
    fs.writeFileSync(markdownPath, markdownReport);
    console.log(`📄 Markdown report saved to: ${markdownPath}`);
}

// Run the analysis
if (require.main === module) {
    analyzeNarrationUsage();
}

module.exports = {
    analyzeNarrationUsage,
    extractNarrationKeys,
    loadNarrationKeys,
    loadNarrations,
    generateMarkdownReport,
    findDuplicateKeys,
    findFiles
};