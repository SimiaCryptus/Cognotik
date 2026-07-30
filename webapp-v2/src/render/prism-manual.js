import Prism from 'prismjs';

/**
 * reverse-spec §1.2 / §10.1: `Prism.manual = true` MUST be set before any Prism
 * plugin module is evaluated. ES module imports are hoisted, so the assignment
 * lives in its own module that `prism.js` imports first.
 */
Prism.manual = true;

export default Prism;