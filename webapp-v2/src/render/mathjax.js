import {createLogger} from '../util/logger.js';

/** reverse-spec §10.3 — MathJax v3, configured BEFORE the script loads. */

const log = createLogger('MathJax');
const SCRIPT_ID = 'MathJax-script';
const SCRIPT_SRC = 'https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js';

export function initMathJax() {
    if (document.getElementById(SCRIPT_ID)) return;

    window.MathJax = {
        tex: {
            inlineMath: [['$', '$'], ['\\(', '\\)']],
            displayMath: [['$$', '$$'], ['\\[', '\\]']],
            processEscapes: true,
            processEnvironments: true,
            tags: 'ams'
        },
        options: {
            // pre/code are excluded so Prism and MathJax never fight (§10.3).
            skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'],
            ignoreHtmlClass: 'tex2jax_ignore',
            processHtmlClass: 'tex2jax_process',
            renderActions: {addMenu: [0, '', '']}
        },
        svg: {fontCache: 'global'},
        startup: {
            ready() {
                window.MathJax.startup.defaultReady();
                window.dispatchEvent(new Event('mathjax-ready'));
            }
        }
    };

    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.async = true;
    script.src = SCRIPT_SRC;
    script.onerror = () => log.warn('Failed to load MathJax bundle', {src: SCRIPT_SRC});
    document.head.appendChild(script);
    log.debug('Loader injected');
}

function whenReady() {
    if (window.MathJax?.typesetPromise) return Promise.resolve();
    return new Promise((resolve) => {
        let settled = false;
        const finish = () => {
            if (settled) return;
            settled = true;
            window.removeEventListener('mathjax-ready', finish);
            clearInterval(poll);
            resolve();
        };
        window.addEventListener('mathjax-ready', finish);
        // 1000ms poll fallback in case 'mathjax-ready' already fired (§10.3).
        const poll = setInterval(() => {
            if (window.MathJax?.typesetPromise) finish();
        }, 1000);
    });
}

/** typesetClear first so re-rendered messages are re-typeset (§18 renderers). */
export function typesetMath(container = document.body) {
    const target = container || document.body;
    return whenReady()
        .then(() => {
            window.MathJax.typesetClear?.([target]);
            return window.MathJax.typesetPromise([target]);
        })
        .catch((err) => log.warn('Typeset failed', err));
}