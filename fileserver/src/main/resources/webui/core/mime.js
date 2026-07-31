import {extname, basename} from './paths.js';

const LANGUAGES = {
    js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'javascript',
    ts: 'typescript', tsx: 'typescript', json: 'json', json5: 'json',
    html: 'html', htm: 'html', xml: 'xml', svg: 'xml',
    css: 'css', scss: 'scss', less: 'less',
    md: 'markdown', markdown: 'markdown', mdown: 'markdown', mkd: 'markdown',
    txt: 'plaintext', log: 'plaintext', csv: 'plaintext', tsv: 'plaintext', tab: 'plaintext',
    yml: 'yaml', yaml: 'yaml', toml: 'ini', ini: 'ini', cfg: 'ini', conf: 'ini', properties: 'ini',
    kt: 'kotlin', kts: 'kotlin', java: 'java', scala: 'scala', groovy: 'groovy', gradle: 'groovy',
    py: 'python', rb: 'ruby', go: 'go', rs: 'rust', php: 'php', swift: 'swift',
    c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', hpp: 'cpp', cs: 'csharp',
    sh: 'shell', bash: 'shell', zsh: 'shell', sql: 'sql', lua: 'lua', r: 'r', pl: 'perl',
    dockerfile: 'dockerfile', gitignore: 'plaintext',
};

const TEXT_MIME = /^(text\/|application\/(json|javascript|xml|x-sh|x-yaml|xhtml\+xml))/;
/** Mirrors MimeTypeResolver: extension-less names that are text in practice. */
const TEXT_FILENAMES = new Set([
     '.gitattributes', '.gitignore', '.gitmodules', '.gitkeep', '.dockerignore',
     '.editorconfig', '.env', '.npmrc', '.nvmrc', '.prettierrc', '.eslintrc', '.babelrc',
     'dockerfile', 'makefile', 'license', 'licence', 'notice', 'readme', 'changelog',
     'authors', 'contributors', 'codeowners', 'gradlew', 'procfile',
]);
/** Raster/vector images a browser renders natively. */
export const IMAGE_EXTENSIONS = new Set([
    'png', 'apng', 'jpg', 'jpeg', 'jfif', 'pjpeg', 'gif', 'webp', 'avif',
    'bmp', 'ico', 'cur', 'svg', 'tif', 'tiff',
]);
export const MARKDOWN_EXTENSIONS = new Set(['md', 'markdown', 'mdown', 'mkd', 'mkdn']);
export const TABLE_EXTENSIONS = new Set(['csv', 'tsv', 'tab']);

export function languageFor(path) {
    return LANGUAGES[extname(path)] || 'plaintext';
}

export function isTextLike(stat) {
    if (!stat) return false;
    if (stat.mimeType && TEXT_MIME.test(stat.mimeType)) return true;
     if (Object.prototype.hasOwnProperty.call(LANGUAGES, extname(stat.path || ''))) return true;
     /* Defence in depth for an older server that still answers
        application/octet-stream for '.gitattributes' & friends (note #1). */
     const name = basename(stat.path || '').toLowerCase();
     if (TEXT_FILENAMES.has(name)) return true;
     return name.startsWith('.') && !name.slice(1).includes('.');
}

export function isImage(stat) {
    if (!stat) return false;
    if (typeof stat.mimeType === 'string' && stat.mimeType.startsWith('image/')) return true;
    /* Servers that answer application/octet-stream must not cost us the viewer. */
    return IMAGE_EXTENSIONS.has(extname(stat.path || ''));
}
export function isMarkdown(stat) {
    return !!stat && MARKDOWN_EXTENSIONS.has(extname(stat.path || ''));
}
export function isTabular(stat) {
    return !!stat && TABLE_EXTENSIONS.has(extname(stat.path || ''));
}
/** TSV is tab-separated; everything else defaults to a comma. */
export function delimiterFor(path) {
    const ext = extname(path);
    return ext === 'tsv' || ext === 'tab' ? '\t' : ',';
}