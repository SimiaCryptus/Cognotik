import {FsError} from './errors.js';
import {bus} from './bus.js';

export const caps = {
    ready: false,
    apiVersion: 0,
    platform: 'unknown',
    readOnly: false,
    limits: {
        maxFileSize: 50 * 1024 * 1024,
        maxBatchOps: 64,
        maxDirEntries: 50000,
        maxDepth: 32,
        maxContextResources: 500,
        maxSelectionBytes: 256 * 1024,
    },
    capabilities: {},
    actions: null,

    /** 'watch' answers 'sse' | 'poll' | 'none'; booleans answer true/false. */
    has(name) {
        const value = caps.capabilities[name];
        if (Array.isArray(value)) return value.length > 0;
        if (typeof value === 'string') return value !== 'none' ? value : false;
        return !!value;
    },
    value(name) {
        return caps.capabilities[name];
    },
    require(name) {
        if (!caps.has(name)) {
            throw new FsError('ENOSYS', {syscall: name, message: `This server does not support "${name}"`});
        }
        return caps.capabilities[name];
    },
    get watch() {
        return caps.capabilities.watch || 'none';
    },
};

export async function initCapabilities(fs) {
    const meta = await fs.meta();
    caps.apiVersion = meta.apiVersion;
    caps.platform = meta.platform;
    caps.readOnly = !!meta.readOnly;
    caps.capabilities = meta.capabilities || {};
    caps.limits = {...caps.limits, ...(meta.limits || {})};
    caps.actions = meta.actions || null;
    caps.ready = true;
    bus.emit('caps:ready', caps);
    return caps;
}
