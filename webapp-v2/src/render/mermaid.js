import mermaid from 'mermaid';
import {createLogger} from '../util/logger.js';
import {isVisible} from '../util/dom.js';

/** reverse-spec §10.2 — initialised EXACTLY once, from here (fixes §20.3). */

const log = createLogger('Mermaid');
let initialized = false;
let counter = 0;

export function initMermaid() {
    if (initialized) return;
    mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'loose',
        theme: 'default',
        logLevel: 3
    });
    initialized = true;
    log.debug('Initialised');
}

export async function renderMermaid(container = document) {
    initMermaid();
    const nodes = (container || document).querySelectorAll('.mermaid:not(.mermaid-processed)');
    if (!nodes.length) return;

    for (const node of nodes) {
        // Not visible yet (inactive tab / collapsed section) — retried on reveal (§6.3).
        if (!isVisible(node)) continue;

        const source = (node.textContent || '').trim();
        if (!source) {
            node.classList.add('mermaid-error', 'mermaid-empty');
            continue;
        }

        const id = `mermaid-${Date.now()}-${counter++}`;
        node.innerHTML = '';
        try {
            const {svg} = await mermaid.render(id, source);
            node.innerHTML = svg;
            node.classList.add('mermaid-processed');
        } catch (err) {
            // Non-fatal: restore the source so it stays debuggable (§16).
            log.warn('Diagram render failed', {id, error: err?.message});
            node.classList.add('mermaid-error');
            node.textContent = source;
        }
    }
}