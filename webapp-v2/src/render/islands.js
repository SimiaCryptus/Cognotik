import {createLogger} from '../util/logger.js';
import {highlightAllUnder} from './prism.js';
import {renderMarkdown2, renderResource} from './markdown2/index.js';

/**
 * Server-emitted HTML cannot be parsed as Markdown2 (raw HTML is escaped there),
 * so typed "islands" inside it — diagrams, maths, Markdown2 source — are lifted
 * into Markdown2 resources and rendered by the matching Markdown2 handler.
 *
 * Replaces the former hand-wired Mermaid/MathJax passes. Inline `$…$` maths is
 * intentionally not supported: Markdown2 maths is block-level only.
 */

const log = createLogger('Islands');

function displayMath(node) {
    if (Array.from(node.children).some((c) => c.tagName !== 'BR')) return null;
    const text = (node.textContent || '').trim();
    const match = /^\$\$([\s\S]+?)\$\$$/.exec(text) || /^\\\[([\s\S]+?)\\\]$/.exec(text);
    return match ? match[1].trim() : null;
}

const SPECS = [
    {selector: 'pre > code.language-markdown2', code: true, document: true},
    {selector: '[data-markdown2]', document: true},
    {selector: '.mermaid', type: 'text/vnd.mermaid'},
    {selector: 'pre > code.language-mermaid', code: true, type: 'text/vnd.mermaid'},
    {
        selector: 'pre > code.language-math, pre > code.language-tex, pre > code.language-latex',
        code: true,
        type: 'application/x-tex'
    },
    {selector: '.math-display, .math.display', type: 'application/x-tex'},
    {selector: 'p', type: 'application/x-tex', extract: displayMath},
    {selector: 'pre > code.language-mathml', code: true, type: 'application/mathml+xml'},
    {selector: 'pre > code.language-wavedrom', code: true, type: 'text/vnd.wavedrom'}
];

async function renderHost(host, node, spec, source) {
    try {
        const result = spec.document ? await renderMarkdown2(source) : await renderResource(spec.type, source);
        if (!host.isConnected) return;
        if (!spec.document && result.hasFallback) {
            // The handler rejected the bytes: keep the server markup, flag it.
            host.dataset.m2Island = 'error';
            host.title = `Markdown2 could not render this ${spec.type}`;
            log.count('island-errors');
            return;
        }
        const wrap = document.createElement('div');
        wrap.className = 'm2-island';
        wrap.dataset.m2Island = 'done';
        wrap.innerHTML = result.html;
        host.replaceWith(wrap);
        if (spec.document) highlightAllUnder(wrap);
        log.count('islands');
    } catch (err) {
        if (host.isConnected) host.dataset.m2Island = 'error';
        log.warn('Island render failed', {type: spec.type || 'text/markdown', error: err?.message});
    }
}

/** Idempotent: hosts are flagged once claimed and output is never re-scanned. */
export function renderIslands(root = document) {
    const scope = root || document;
    const jobs = [];
    for (const spec of SPECS) {
        for (const node of scope.querySelectorAll(spec.selector)) {
            const host = spec.code ? node.parentElement : node;
            if (!host || host.dataset.m2Island) continue;
            if (host.closest('md2-document, [data-m2-island]')) continue;
            const source = spec.extract ? spec.extract(node) : node.textContent || '';
            if (source == null) continue;
            if (!source.trim()) {
                host.dataset.m2Island = 'empty';
                continue;
            }
            host.dataset.m2Island = 'pending';
            jobs.push(renderHost(host, node, spec, source));
        }
    }
    return Promise.allSettled(jobs);
}