const SCHEMA = 1;
const PREFIX = 'cognotik.fs.ui.v1';

let key = `${PREFIX}:default`;
let record = {schema: SCHEMA};

function read() {
    try {
        const raw = localStorage.getItem(key);
        if (!raw) return {schema: SCHEMA};
        const parsed = JSON.parse(raw);
        /* A schema bump discards unknown records rather than migrating. */
        if (!parsed || parsed.schema !== SCHEMA) return {schema: SCHEMA};
        return parsed;
    } catch (e) {
        return {schema: SCHEMA};
    }
}

export const persist = {
    init(base) {
        key = `${PREFIX}:${base || 'default'}`;
        if (new URLSearchParams(location.search).has('reset')) {
            try {
                localStorage.removeItem(key);
            } catch (e) { /* ignore */
            }
        }
        record = read();
        return record;
    },
    all() {
        return record;
    },
    get(name, fallback) {
        return Object.prototype.hasOwnProperty.call(record, name) ? record[name] : fallback;
    },
    set(name, value) {
        record[name] = value;
        this.flush();
    },
    patch(patch) {
        Object.assign(record, patch);
        this.flush();
    },
    flush() {
        try {
            localStorage.setItem(key, JSON.stringify({...record, schema: SCHEMA}));
        } catch (e) { /* full/denied */
        }
    },
    reset() {
        record = {schema: SCHEMA};
        try {
            localStorage.removeItem(key);
        } catch (e) { /* ignore */
        }
    },
    /** Global (mount-independent) preference, e.g. the chosen API base. */
    global(name, value) {
        const globalKey = `${PREFIX}:global:${name}`;
        try {
            if (value === undefined) return localStorage.getItem(globalKey);
            localStorage.setItem(globalKey, value);
            return value;
        } catch (e) {
            return null;
        }
    },
};
