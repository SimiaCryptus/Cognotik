const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, '.websearch', 'job_matches');
const destBaseDir = path.join(__dirname, 'job_matches');

if (!fs.existsSync(srcDir)) {
console.log(`Source directory not found: ${srcDir}`);
return;
}

const files = fs.readdirSync(srcDir).filter(f => f.endsWith('.md'));

files.forEach(file => {
// Extract company name from filename (e.g., "Toast_Principal_Engineer..." -> "Toast")
const company = file.split('_')[0];
if (!company) return;

const targetDir = path.join(destBaseDir, company);

// Ensure target directory exists
if (!fs.existsSync(targetDir)) {
  fs.mkdirSync(targetDir, { recursive: true });
}

const srcPath = path.join(srcDir, file);
const destPath = path.join(targetDir, file);

try {
  fs.copyFileSync(srcPath, destPath);
  console.log(`Copied: ${file} -> job_matches/${company}/`);
} catch (err) {
  console.error(`Failed to copy ${file}:`, err.message);
}
});