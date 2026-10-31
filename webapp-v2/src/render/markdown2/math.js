import {liteAdaptor} from 'mathjax-full/js/adaptors/liteAdaptor.js';
import {RegisterHTMLHandler} from 'mathjax-full/js/handlers/html.js';
import {MathML} from 'mathjax-full/js/input/mathml.js';
import {TeX} from 'mathjax-full/js/input/tex.js';
import 'mathjax-full/js/input/tex/ams/AmsConfiguration.js';
import 'mathjax-full/js/input/tex/base/BaseConfiguration.js';
import {mathjax} from 'mathjax-full/js/mathjax.js';
import {SVG} from 'mathjax-full/js/output/svg.js';

/**
 * MathJax without a DOM: TeX or MathML in, standalone SVG out. Only the `base`
 * and `ams` TeX packages are loaded (no \require, no \def). Bundled — no CDN.
 */
const adaptor = liteAdaptor();
RegisterHTMLHandler(adaptor);

let texDoc;
let mmlDoc;

export function mathToSvg(source, input) {
    const doc =
        input === 'tex'
            ? (texDoc ??= mathjax.document('', {
                  InputJax: new TeX({packages: ['base', 'ams']}),
                  OutputJax: new SVG({fontCache: 'none'})
              }))
            : (mmlDoc ??= mathjax.document('', {InputJax: new MathML(), OutputJax: new SVG({fontCache: 'none'})}));
    const node = doc.convert(source, {display: true});
    const svg = adaptor.innerHTML(node);
    if (!svg.startsWith('<svg')) throw new Error('MathJax produced no SVG');
    return svg;
}