const fs = require('fs');
const path = require('path');
const domain = 'https://cognotik.com';
const root = path.resolve(__dirname, '..');
const sitemapPath = path.join(root, 'sitemap.xml');
function walk(dir, results = []) {
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        const filePath = path.join(dir, file);
        const stat = fs.statSync(filePath);
        if (stat.isDirectory()) {
            if (file !== 'node_modules' && file !== '.git' && file !== 'scripts') {
                walk(filePath, results);
            }
        } else {
            if (file.endsWith('.html')) {
                results.push(filePath);
            }
        }
    });
    return results;
}
const files = walk(root);
const urls = files.map(file => {
    let relative = path.relative(root, file).replace(/\\/g, '/');
    if (relative === 'index.html') relative = '';
    else if (relative.endsWith('/index.html')) relative = relative.replace('/index.html', '/');
    return `${domain}/${relative}`;
});
const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map(url => `  <url>
    <loc>${url}</loc>
    <lastmod>${new Date().toISOString()}</lastmod>
  </url>`).join('\n')}
</urlset>`;
fs.writeFileSync(sitemapPath, xml);
console.log('Sitemap rebuilt!');