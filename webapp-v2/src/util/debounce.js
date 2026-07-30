/** Trailing-edge-only debounce (leading edge off), as used by every §6/§8/§15 timer. */
export function debounce(fn, wait) {
    let timer = null;
    let lastArgs = null;

    function wrapped(...args) {
        lastArgs = args;
        if (timer) clearTimeout(timer);
        timer = setTimeout(() => {
            timer = null;
            const args2 = lastArgs;
            lastArgs = null;
            fn(...args2);
        }, wait);
    }

    wrapped.cancel = () => {
        if (timer) clearTimeout(timer);
        timer = null;
        lastArgs = null;
    };
    wrapped.flush = () => {
        if (!timer) return;
        clearTimeout(timer);
        timer = null;
        const args = lastArgs;
        lastArgs = null;
        fn(...(args || []));
    };
    return wrapped;
}