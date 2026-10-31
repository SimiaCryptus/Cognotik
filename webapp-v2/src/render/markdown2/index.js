import {defaultHandlers, parse, renderHtml, resolveDocument, wavedromHandler} from '@markdown2/core';
import {isDarkTheme} from '../../config/theme.js';
import {createLogger} from '../../util/logger.js';
import {mathHandler, mermaidHandler} from './handlers.js';

/**
 * The single rendering entry point. Everything that turns source text into HTML
 * goes through Markdown2: parse → resolve (decode) → render the M2SS styling tree.
 *
 * No uri policy and no grants are supplied, so remote and encrypted resources stay
 * locked/unsupported: rendering never reaches the network (Markdown2 §5.5, §11).
 */

const log = createLogger('Markdown2');
const CACHE_LIMIT = 200;
/** source -> Promise<result>. Streaming re-renders hit this constantly. */
const cache = new Map();

let registry = null;

export function markdown2Handlers() {
    if (!registry) {
        registry = defaultHandlers();
        registry.register(wavedromHandler);
        registry.register(mermaidHandler);
        registry.register(mathHandler);
    }
    return registry;
}

async function renderUncached(source) {
    const handlers = markdown2Handlers();
    const doc = parse(source);
    const diagnostics = [...doc.diagnostics];
    const resolved = await resolveDocument(doc, {handledTypes: handlers.types('view'), diagnostics});
    const html = await renderHtml(resolved, {handlers});
    return {html, diagnostics, hasFallback: html.includes('<md2-fallback')};
}

/** Render a Markdown2 document to an HTML fragment (always escaped by Markdown2). */
export function renderMarkdown2(source) {
    const key = `${isDarkTheme() ? 'd' : 'l'}|${source}`;
    const hit = cache.get(key);
    if (hit) {
        cache.delete(key);
        cache.set(key, hit); // LRU touch
        return hit;
    }
    const promise = renderUncached(source).catch((err) => {
        cache.delete(key);
        log.error('Markdown2 render failed', {length: source.length, error: err});
        throw err;
    });
    cache.set(key, promise);
    while (cache.size > CACHE_LIMIT) cache.delete(cache.keys().next().value);
    return promise;
}

/** A terminator token that never appears alone on a payload line (Markdown2 §4.1). */
export function uniqueToken(body, base = 'M2') {
    const lines = new Set(String(body).split(/\r?\n/).map((l) => l.replace(/[ \t]+$/, '')));
    let token = base;
    let n = 0;
    while (lines.has(token)) token = `${base}_${++n}`;
    return token;
}

/** Wrap a payload as a single typed Markdown2 resource block. */
export function resourceSource(type, body, attributes = {}) {
    const token = uniqueToken(body);
    const attrs = Object.entries({type, ...attributes})
        .filter(([, v]) => v != null && v !== '')
        .map(([k, v]) => ` ${k}="${String(v).replace(/"/g, "'").replace(/[\r\n]+/g, ' ')}"`)
        .join('');
    return `<<<${token}${attrs}\n${String(body).replace(/\r?\n$/, '')}\n${token}\n`;
}

/** Render one payload through the handler for its declared type. */
export function renderResource(type, body, attributes) {
    return renderMarkdown2(resourceSource(type, body, attributes));
}