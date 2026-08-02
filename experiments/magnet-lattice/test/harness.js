// Minimal zero-dependency test harness (Node ESM or browser).
let passed = 0,
    failed = 0;
const failures = [];

export function describe(name, fn) {
    console.log(`\n# ${name}`);
    fn();
}

export function it(name, fn) {
    try {
        fn();
        passed++;
        console.log(`  ✓ ${name}`);
    } catch (e) {
        failed++;
        failures.push({name, error: e});
        console.log(`  ✗ ${name}\n    ${e.message}`);
    }
}

export function assert(cond, msg = 'assertion failed') {
    if (!cond) throw new Error(msg);
}

export function assertClose(a, b, tol = 1e-6, msg = '') {
    if (Math.abs(a - b) > tol) {
        throw new Error(`${msg} expected ${b}, got ${a} (|Δ|=${Math.abs(a - b)} > ${tol})`);
    }
}

export function assertRelClose(a, b, rtol = 1e-6, msg = '') {
    const denom = Math.max(Math.abs(a), Math.abs(b), 1e-12);
    if (Math.abs(a - b) / denom > rtol) {
        throw new Error(`${msg} expected ${b}, got ${a} (rel=${Math.abs(a - b) / denom})`);
    }
}

export function summary() {
    console.log(`\n${passed} passed, ${failed} failed`);
    if (failed > 0 && typeof process !== 'undefined') process.exitCode = 1;
    return {passed, failed, failures};
}
