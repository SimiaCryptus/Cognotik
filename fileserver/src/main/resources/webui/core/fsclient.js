import {FsError} from './errors.js';

/**
 * The only place fetch() lives. One function per FS API v1 operation;
 * callers never inspect response.status.
 */
export const fs = {};

function qs(params) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params || {})) {
        if (value === undefined || value === null || value === false) continue;
        search.set(key, value === true ? 'true' : String(value));
    }
    const encoded = search.toString();
    return encoded ? `?${encoded}` : '';
}

export function createFsClient({base, fetchImpl} = {}) {
    const doFetch = fetchImpl || ((...args) => fetch(...args));
    const inflight = new Map();

    async function toError(response, method, path) {
        let payload = null;
        try {
            payload = await response.json();
        } catch (e) { /* not JSON */
        }
        const error = payload?.error || {};
        return new FsError(error.code || response.headers.get('X-Fs-Error') || 'EIO', {
            errno: error.errno,
            syscall: error.syscall || method.toLowerCase(),
            path: error.path || path,
            message: error.message,
            status: response.status,
        });
    }

    async function raw(path, {method = 'GET', headers = {}, body, signal} = {}) {
        const requestHeaders = {...headers};
        if (method !== 'GET' && method !== 'HEAD') requestHeaders['X-Fs-Api'] = '1';
        let response;
        try {
            response = await doFetch(base + path, {
                method, headers: requestHeaders, body, signal, credentials: 'same-origin',
            });
        } catch (e) {
            if (e && e.name === 'AbortError') throw new FsError('ECANCELED', {syscall: method, path});
            throw new FsError('ENETWORK', {syscall: method, path, message: e?.message});
        }
        if (!response.ok && response.status !== 304) throw await toError(response, method, path);
        return response;
    }

    async function json(path, options) {
        const response = await raw(path, options);
        if (response.status === 204) return null;
        try {
            return await response.json();
        } catch (e) {
            return null;
        }
    }

    /** Identical in-flight reads share one promise ("reveal file" is cheap). */
    function dedup(key, factory) {
        if (inflight.has(key)) return inflight.get(key);
        const promise = factory().finally(() => inflight.delete(key));
        inflight.set(key, promise);
        return promise;
    }

    const client = {
        base,
        url(op, params) {
            return base + op + qs(params);
        },
        meta() {
            return json('/meta');
        },
        actions() {
            return json('/actions');
        },

        stat(path, {lstat, throwIfNoEntry} = {}) {
            const url = `/stat${qs({path, lstat, throwIfNoEntry})}`;
            return dedup(`GET ${url}`, () => json(url));
        },
        statBatch(paths, {lstat} = {}) {
            return json('/stat', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({paths, lstat: !!lstat}),
            });
        },
        readdir(path, {recursive, depth, stat = true, signal} = {}) {
            const url = `/dir${qs({path, recursive, depth, stat})}`;
            return dedup(`GET ${url}`, () => json(url, {signal}));
        },

        async readFile(path, {range, ifNoneMatch, ifMatch, signal} = {}) {
            const headers = {};
            if (range) headers.Range = `bytes=${range[0]}-${range[1] ?? ''}`;
            if (ifNoneMatch) headers['If-None-Match'] = ifNoneMatch;
            if (ifMatch) headers['If-Match'] = ifMatch;
            const response = await raw(`/file${qs({path})}`, {headers, signal});
            const bytes = response.status === 304 ? null : new Uint8Array(await response.arrayBuffer());
            return {
                status: response.status,
                etag: response.headers.get('ETag'),
                mtimeMs: Number(response.headers.get('X-Fs-Mtime-Ms') || 0),
                size: Number(response.headers.get('X-Fs-Size') || bytes?.length || 0),
                mimeType: response.headers.get('X-Fs-Mime-Type') || 'application/octet-stream',
                bytes,
            };
        },
        async readText(path, options) {
            const result = await client.readFile(path, options);
            return {...result, text: result.bytes ? new TextDecoder('utf-8').decode(result.bytes) : null};
        },
        writeFile(path, bytes, {flag, position, ifMatch, ifNoneMatch} = {}) {
            const headers = {'Content-Type': 'application/octet-stream'};
            if (ifMatch) headers['If-Match'] = ifMatch;
            if (ifNoneMatch) headers['If-None-Match'] = ifNoneMatch;
            return json(`/file${qs({path, flag, position})}`, {method: 'PUT', headers, body: bytes});
        },

        mkdir(path, {recursive = true} = {}) {
            return json('/dir', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({path, recursive}),
            });
        },
        rm(path, {recursive = false, force = false} = {}) {
            return json(`/file${qs({path, recursive, force})}`, {method: 'DELETE'});
        },
        rename(from, to, {overwrite = false} = {}) {
            return json('/rename', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({from, to, overwrite}),
            });
        },
        copy(from, to, {recursive = true, force = false, preserveTimestamps = false} = {}) {
            return json('/copy', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({from, to, recursive, force, preserveTimestamps}),
            });
        },
        truncate(path, len = 0) {
            return json('/truncate', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({path, len}),
            });
        },
        utimes(path, {atimeMs, mtimeMs} = {}) {
            return json('/utimes', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({path, atimeMs, mtimeMs}),
            });
        },
        realpath(path) {
            return json(`/realpath${qs({path})}`);
        },
        resolve(request, from) {
            return json(`/resolve${qs({request, from})}`);
        },
        batch(ops, {stopOnError = false} = {}) {
            return json('/batch', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ops, stopOnError}),
            });
        },
        exec(cmd, args = [], {cwd = '/', signal} = {}) {
            return json('/exec', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({cmd, args, cwd}),
                signal,
            });
        },
        git(action, params = {}) {
            return json('/git', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({action, params}),
            });
        },

        fileUrl(path) {
            return client.url('/file', {path});
        },
        snapshotUrl(path, {maxBytes} = {}) {
            return client.url('/snapshot', {path, maxBytes});
        },
        watchUrl(path, {recursive} = {}) {
            return client.url('/watch', {path, recursive});
        },
    };
    return client;
}

/** Installs the singleton used across the app (components import `{ fs }`). */
export function initFsClient(base) {
    const client = createFsClient({base});
    for (const key of Object.keys(fs)) delete fs[key];
    Object.assign(fs, client);
    return fs;
}
