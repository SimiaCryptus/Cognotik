import {extname} from './paths.js';

const LANGUAGES = {
    js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'javascript',
    ts: 'typescript', tsx: 'typescript', json: 'json', json5: 'json',
    html: 'html', htm: 'html', xml: 'xml', svg: 'xml',
    css: 'css', scss: 'scss', less: 'less',
    md: 'markdown', markdown: 'markdown', txt: 'plaintext', log: 'plaintext',
    yml: 'yaml', yaml: 'yaml', toml: 'ini', ini: 'ini', cfg: 'ini', conf: 'ini', properties: 'ini',
    kt: 'kotlin', kts: 'kotlin', java: 'java', scala: 'scala', groovy: 'groovy', gradle: 'groovy',
    py: 'python', rb: 'ruby', go: 'go', rs: 'rust', php: 'php', swift: 'swift',
    c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', hpp: 'cpp', cs: 'csharp',
    sh: 'shell', bash: 'shell', zsh: 'shell', sql: 'sql', lua: 'lua', r: 'r', pl: 'perl',
    dockerfile: 'dockerfile', gitignore: 'plaintext',
};

const TEXT_MIME = /^(text\/|application\/(json|javascript|xml|x-sh|x-yaml|xhtml\+xml))/;

export function languageFor(path) {
    return LANGUAGES[extname(path)] || 'plaintext';
}

export function isTextLike(stat) {
    if (!stat) return false;
    if (stat.mimeType && TEXT_MIME.test(stat.mimeType)) return true;
    return Object.prototype.hasOwnProperty.call(LANGUAGES, extname(stat.path || ''));
}

export function isImage(stat) {
    return !!stat && typeof stat.mimeType === 'string' && stat.mimeType.startsWith('image/');
}
