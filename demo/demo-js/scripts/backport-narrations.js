#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

/**
 * Utility script to backport narration changes from the markdown report
 * back to the narrations.json file
 */

class NarrationBackporter {
  constructor(reportPath, jsonPath) {
    this.reportPath = reportPath;
    this.jsonPath = jsonPath;
    this.narrations = {};
    this.updatedCount = 0;
    this.addedCount = 0;
  }

  /**
   * Load the existing narrations JSON file
   */
  loadNarrations() {
    try {
      const jsonContent = fs.readFileSync(this.jsonPath, 'utf8');
      this.narrations = JSON.parse(jsonContent);
      console.log(`✓ Loaded ${Object.keys(this.narrations).length} existing narrations`);
    } catch (error) {
      console.error(`Error loading narrations file: ${error.message}`);
      process.exit(1);
    }
  }

  /**
   * Parse the markdown report and extract narration entries
   */
  parseReport() {
    try {
      const reportContent = fs.readFileSync(this.reportPath, 'utf8');
      const entries = this.extractNarrationEntries(reportContent);
      console.log(`✓ Found ${entries.length} narration entries in report`);
      return entries;
    } catch (error) {
      console.error(`Error reading report file: ${error.message}`);
      process.exit(1);
    }
  }

  /**
   * Extract narration entries from markdown content
   */
  extractNarrationEntries(content) {
    const entries = [];
    const lines = content.split('\n');
    let currentId = null;
    let currentText = [];
    let inNarrationSection = false;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // Check for narration ID anchor
      const idMatch = line.match(/^<a id="([^"]+)"><\/a>$/);
      if (idMatch) {
        // Save previous entry if exists
        if (currentId && currentText.length > 0) {
          entries.push({
            id: currentId,
            text: this.cleanText(currentText.join(' '))
          });
        }
        
        // Start new entry
        currentId = idMatch[1];
        currentText = [];
        inNarrationSection = true;
        continue;
      }

      // Check for section headers that end narration sections
      if (line.startsWith('### ') || line.startsWith('## ')) {
        if (currentId && currentText.length > 0) {
          entries.push({
            id: currentId,
            text: this.cleanText(currentText.join(' '))
          });
        }
        currentId = null;
        currentText = [];
        inNarrationSection = false;
        continue;
      }

      // Collect text for current narration
      if (inNarrationSection && currentId && line.trim()) {
        // Skip markdown formatting lines
        if (!line.startsWith('```') && !line.startsWith('---')) {
          currentText.push(line.trim());
        }
      }
    }

    // Handle last entry
    if (currentId && currentText.length > 0) {
      entries.push({
        id: currentId,
        text: this.cleanText(currentText.join(' '))
      });
    }

    return entries;
  }

  /**
   * Clean and normalize text content
   */
  cleanText(text) {
    return text
      .replace(/\s+/g, ' ') // Normalize whitespace
      .replace(/^\s+|\s+$/g, '') // Trim
      .replace(/\*\*(.*?)\*\*/g, '$1') // Remove bold markdown
      .replace(/\*(.*?)\*/g, '$1') // Remove italic markdown
      .replace(/`(.*?)`/g, '$1'); // Remove inline code markdown
  }

  /**
   * Update narrations with entries from report
   */
  updateNarrations(entries) {
    for (const entry of entries) {
      const { id, text } = entry;
      
      if (this.narrations[id]) {
        // Update existing entry
        if (this.narrations[id].text !== text) {
          console.log(`📝 Updating: ${id}`);
          console.log(`   Old: ${this.narrations[id].text.substring(0, 80)}...`);
          console.log(`   New: ${text.substring(0, 80)}...`);
          this.narrations[id].text = text;
          this.updatedCount++;
        }
      } else {
        // Add new entry
        console.log(`➕ Adding new: ${id}`);
        console.log(`   Text: ${text.substring(0, 80)}...`);
        this.narrations[id] = {
          text: text,
          audio: `${id}.mp3`
        };
        this.addedCount++;
      }
    }
  }

  /**
   * Save the updated narrations back to JSON file
   */
  saveNarrations() {
    try {
      // Sort keys alphabetically for consistent output
      const sortedNarrations = {};
      Object.keys(this.narrations)
        .sort()
        .forEach(key => {
          sortedNarrations[key] = this.narrations[key];
        });

      const jsonContent = JSON.stringify(sortedNarrations, null, 2);
      fs.writeFileSync(this.jsonPath, jsonContent, 'utf8');
      console.log(`✓ Saved updated narrations to ${this.jsonPath}`);
    } catch (error) {
      console.error(`Error saving narrations file: ${error.message}`);
      process.exit(1);
    }
  }

  /**
   * Create a backup of the original file
   */
  createBackup() {
    const backupPath = this.jsonPath + '.backup.' + Date.now();
    try {
      fs.copyFileSync(this.jsonPath, backupPath);
      console.log(`✓ Created backup: ${backupPath}`);
    } catch (error) {
      console.warn(`Warning: Could not create backup: ${error.message}`);
    }
  }

  /**
   * Run the backport process
   */
  run() {
    console.log('🔄 Starting narration backport process...\n');

    // Create backup
    this.createBackup();

    // Load existing data
    this.loadNarrations();

    // Parse report
    const entries = this.parseReport();

    // Update narrations
    console.log('\n📋 Processing updates...');
    this.updateNarrations(entries);

    // Save results
    this.saveNarrations();

    // Summary
    console.log('\n📊 Summary:');
    console.log(`   Updated entries: ${this.updatedCount}`);
    console.log(`   Added entries: ${this.addedCount}`);
    console.log(`   Total entries: ${Object.keys(this.narrations).length}`);
    console.log('\n✅ Backport completed successfully!');
  }
}

/**
 * Validate command line arguments and file paths
 */
function validateArgs() {
  const args = process.argv.slice(2);
  
  if (args.length < 2) {
    console.error('Usage: node backport-narrations.js <report-file> <narrations-json>');
    console.error('');
    console.error('Example:');
    console.error('  node backport-narrations.js narration-analysis-report.md cypress/fixtures/narrations.json');
    process.exit(1);
  }

  const [reportPath, jsonPath] = args;

  // Check if files exist
  if (!fs.existsSync(reportPath)) {
    console.error(`Error: Report file not found: ${reportPath}`);
    process.exit(1);
  }

  if (!fs.existsSync(jsonPath)) {
    console.error(`Error: Narrations JSON file not found: ${jsonPath}`);
    process.exit(1);
  }
   // Additional validation to ensure correct file types
   if (!reportPath.endsWith('.md')) {
     console.error(`Error: Report file should be a markdown file (.md): ${reportPath}`);
     process.exit(1);
   }
   if (!jsonPath.endsWith('.json')) {
     console.error(`Error: Narrations file should be a JSON file (.json): ${jsonPath}`);
     process.exit(1);
   }

  return { reportPath, jsonPath };
}

/**
 * Main execution
 */
function main() {
  const { reportPath, jsonPath } = validateArgs();
  const backporter = new NarrationBackporter(reportPath, jsonPath);
  backporter.run();
}

// Run if called directly
if (require.main === module) {
  main();
}

module.exports = { NarrationBackporter };