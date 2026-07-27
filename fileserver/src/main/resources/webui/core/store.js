export function createStore(initial) {
    let state = initial;
    const subscribers = new Set();
    const notify = () => subscribers.forEach((fn) => {
        try {
            fn(state);
        } catch (e) {
            console.error(e);
        }
    });
    return {
        get() {
            return state;
        },
        set(patch) {
            state = typeof patch === 'function' ? patch(state) : {...state, ...patch};
            notify();
        },
        /** For nested models that mutate in place (tree/tabs) and then announce. */
        touch() {
            notify();
        },
        subscribe(fn) {
            subscribers.add(fn);
            return () => subscribers.delete(fn);
        },
        select(selector, cb) {
            let previous = selector(state);
            return this.subscribe((next) => {
                const value = selector(next);
                if (value !== previous) {
                    previous = value;
                    cb(value);
                }
            });
        },
    };
}

export const store = createStore({
    base: null,
    classicBase: null,
    caps: null,
    panels: {sidebar: 'explorer', bottom: null, sidebarWidth: 280},
    watcher: {state: 'off', lastEventAt: 0, reason: null},
    tasks: [],
    notifications: [],
    context: null,
});
