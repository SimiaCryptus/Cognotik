import {encodeBase64, escapeHtml} from '@markdown2/core';
import {isDarkTheme} from '../../config/theme.js';
import {createLogger} from '../../util/logger.js';

/**
 * App-specific Markdown2 handlers (Markdown2 core §6). Both produce a standalone
 * SVG and show it through <img>, a context in which nothing can run — the same
 * policy the Markdown2 reference reader applies to SVG and maths.
 */

const log = createLogger('Markdown2Handlers');
const MAX_DIAGRAM = 50_000;
const MAX_MATH = 20_000;

/** Throws on invalid UTF-8 so the handler rejects and Markdown2 shows its fallback. */
function strictUtf8(bytes) {
    return new TextDecoder('utf-8', {fatal: true}).decode(bytes);
}

function svgImage(svg, className, alt) {
    let doc = svg.includes('xmlns="http://www.w3.org/2000/svg"')
        ? svg
        : svg.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"');
    // HTML-isms are not valid XML inside an image.
    doc = doc.replace(/<br\s*>/gi, '<br/>');
    const data = encodeBase64(new TextEncoder().encode(doc));
    return `<img class="${className}" alt="${escapeHtml(alt)}" src="data:image/svg+xml;base64,${data}">`;
}

let mermaidSeq = 0;

/** `text/vnd.mermaid` and ```mermaid fences (Markdown2 §7.2 alias). */
export const mermaidHandler = {
    types: ['text/vnd.mermaid'],
    capabilities: ['view'],
    async render(bytes, ctx) {
        const src = strictUtf8(bytes).trim();
        if (!src) throw new Error('empty diagram');
        if (src.length > MAX_DIAGRAM) throw new Error('diagram too large');
        const {default: mermaid} = await import('mermaid');
        mermaid.initialize({
            startOnLoad: false,
            securityLevel: 'strict',
            theme: isDarkTheme() ? 'dark' : 'default',
            // foreignObject labels do not render inside <img>.
            htmlLabels: false,
            flowchart: {htmlLabels: false},
            logLevel: 3
        });
        const id = `m2-mermaid-${Date.now()}-${mermaidSeq++}`;
        try {
            const {svg} = await mermaid.render(id, src);
            const title = ctx.node?.resource.attributes.get('title') ?? 'Mermaid diagram';
            return `<div class="m2-diagram-block">${svgImage(svg, 'm2-mermaid', title)}</div>`;
        } catch (err) {
            log.warn('Mermaid render failed', {id, error: err?.message});
            throw err;
        } finally {
            // Mermaid leaves its scratch/error element behind on failure.
            document.getElementById(`d${id}`)?.remove();
            document.getElementById(id)?.remove();
        }
    }
};

/** `application/x-tex`, `application/mathml+xml` and ```math fences. MathJax loads on first use. */
export const mathHandler = {
    types: ['application/x-tex', 'application/mathml+xml'],
    capabilities: ['view'],
    async render(bytes, ctx) {
        const src = strictUtf8(bytes);
        if (src.length > MAX_MATH) throw new Error('formula too large');
        const {mathToSvg} = await import('./math.js');
        const svg = mathToSvg(src, ctx.type === 'application/mathml+xml' ? 'mathml' : 'tex');
        return `<div class="m2-math-block">${svgImage(svg, 'm2-math', src)}</div>`;
    }
};