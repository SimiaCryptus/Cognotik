/** Virtual, '/'-relative path arithmetic. Defence in depth: server is authoritative. */
const INVALID_CHARS = /[\\:~\u0000-\u001f\u007f]/;

export function normalize(path) {
    const out = [];
    for (const segment of String(path ?? '').split('/')) {
        if (!segment || segment === '.') continue;
        if (segment === '..') {
            if (out.length) out.pop();
            continue;
        }
        out.push(segment);
    }
    return '/' + out.join('/');
}

export function join(base, ...parts) {
    return normalize([base, ...parts].join('/'));
}

export function segments(path) {
    return normalize(path).split('/').filter(Boolean);
}

export function basename(path) {
    const parts = segments(path);
    return parts.length ? parts[parts.length - 1] : '/';
}

export function dirname(path) {
    const parts = segments(path);
    parts.pop();
    return '/' + parts.join('/');
}

export function extname(path) {
    const name = basename(path);
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
}

export function isAncestor(ancestor, path) {
    const a = normalize(ancestor);
    const p = normalize(path);
    if (a === '/') return p !== '/';
    return p === a || p.startsWith(a + '/');
}

export function commonAncestor(paths) {
    if (!paths || !paths.length) return '/';
    let common = segments(paths[0]).slice(0, -1);
    for (const path of paths.slice(1)) {
        const parts = segments(path).slice(0, -1);
        let i = 0;
        while (i < common.length && i < parts.length && common[i] === parts[i]) i++;
        common = common.slice(0, i);
    }
    return '/' + common.join('/');
}

/** Mirrors the server's rules so validation can be inline instead of a toast. */
export function validateName(name) {
    if (!name || !name.trim()) return 'A name is required';
    if (name !== name.trim()) return 'Leading or trailing spaces are not allowed';
    if (name === '.' || name === '..') return 'Reserved name';
    if (name === '.fsapi') return '".fsapi" is reserved';
    if (name.includes('/')) return 'A name cannot contain "/"';
    if (INVALID_CHARS.test(name)) return 'A name cannot contain \\ : ~ or control characters';
    if (name.endsWith('.')) return 'A name cannot end with "."';
    return null;
}

/** Natural, numeric-aware comparator; directories are ordered by the caller. */
const collator = new Intl.Collator(undefined, {numeric: true, sensitivity: 'base'});

export function compareNames(a, b) {
    return collator.compare(a, b);
}

export function formatBytes(bytes) {
    if (bytes === null || bytes === undefined) return '';
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
    let value = Number(bytes);
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit++;
    }
    return `${unit === 0 ? value : value.toFixed(1)} ${units[unit]}`;
}
