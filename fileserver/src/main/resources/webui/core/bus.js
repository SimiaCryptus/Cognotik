/** Pub/sub for *facts* ("fs:changed", "tab:opened"), never for commands. */
const listeners = new Map();

function add(type, fn) {
    if (!listeners.has(type)) listeners.set(type, new Set());
    listeners.get(type).add(fn);
    return () => off(type, fn);
}

function off(type, fn) {
    listeners.get(type)?.delete(fn);
}

export const bus = {
    on: add,
    off,
    once(type, fn) {
        const un = add(type, (payload) => {
            un();
            fn(payload);
        });
        return un;
    },
    emit(type, payload) {
        for (const fn of listeners.get(type) || []) {
            try {
                fn(payload, type);
            } catch (e) {
                console.error(`bus listener for ${type} failed`, e);
            }
        }
        for (const fn of listeners.get('*') || []) {
            try {
                fn(payload, type);
            } catch (e) {
                console.error('bus wildcard listener failed', e);
            }
        }
    },
};
