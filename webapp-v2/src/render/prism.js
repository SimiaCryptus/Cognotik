import Prism from './prism-manual.js';

/* Languages (§10.1) */
import 'prismjs/components/prism-markup';
import 'prismjs/components/prism-css';
import 'prismjs/components/prism-clike';
import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-jsx';
import 'prismjs/components/prism-tsx';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-diff';
import 'prismjs/components/prism-markdown';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-kotlin';
import 'prismjs/components/prism-scala';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-mermaid';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-yaml';

/* Plugins + their CSS (§10.1) */
import 'prismjs/plugins/toolbar/prism-toolbar';
import 'prismjs/plugins/toolbar/prism-toolbar.css';
import 'prismjs/plugins/copy-to-clipboard/prism-copy-to-clipboard';
import 'prismjs/plugins/show-language/prism-show-language';
import 'prismjs/plugins/line-numbers/prism-line-numbers';
import 'prismjs/plugins/line-numbers/prism-line-numbers.css';
import 'prismjs/plugins/line-highlight/prism-line-highlight';
import 'prismjs/plugins/line-highlight/prism-line-highlight.css';
import 'prismjs/plugins/diff-highlight/prism-diff-highlight';
import 'prismjs/plugins/diff-highlight/prism-diff-highlight.css';
import 'prismjs/plugins/normalize-whitespace/prism-normalize-whitespace';

import {idle, isVisible} from '../util/dom.js';
import {createLogger} from '../util/logger.js';

const log = createLogger('Prism');
const PROCESSED = 'prismjs-processed';

let observer = null;

function ensureObserver() {
    if (observer || typeof IntersectionObserver === 'undefined') return observer;
    observer = new IntersectionObserver(
        (entries) => {
            for (const entry of entries) {
                if (!entry.isIntersecting) continue;
                const target = entry.target;
                observer.unobserve(target);
                idle(() => highlightElement(target));
            }
        },
        {rootMargin: '200px 0px'}
    );
    return observer;
}

/** §10.1 — never re-tokenise (fixes double-highlighting on re-render). */
function shouldSkip(node) {
    return (
        node.classList.contains('language-none') ||
        node.classList.contains(PROCESSED) ||
        node.querySelector('.token') !== null
    );
}

export function highlightElement(node) {
    if (!node || node.classList.contains(PROCESSED)) return;
    try {
        Prism.highlightElement(node);
    } catch (err) {
        log.warn('highlightElement failed', err);
    } finally {
        node.classList.add(PROCESSED);
    }
}

/**
 * Pipeline step 4 (§6.1): observe visible, unprocessed code blocks. Invisible
 * blocks (inactive tab pane / collapsed section) are skipped and picked up when
 * the pipeline re-runs on reveal (§6.3).
 */
export function highlightWithin(root = document) {
    const scope = root || document;
    const nodes = scope.querySelectorAll(`pre code:not(.${PROCESSED})`);
    let observed = 0;
    nodes.forEach((node) => {
        if (shouldSkip(node)) return;
        if (!isVisible(node)) return;
        const obs = ensureObserver();
        if (obs) {
            obs.observe(node);
            observed += 1;
        } else {
            idle(() => highlightElement(node));
        }
    });
    if (observed) log.debug('Observing code blocks', {observed});
}

/** Modal content is highlighted eagerly (§10.1 / §14.2). */
export function highlightAllUnder(root) {
    if (!root) return;
    try {
        Prism.highlightAllUnder(root);
    } catch (err) {
        log.warn('highlightAllUnder failed', err);
    }
}

export default Prism;