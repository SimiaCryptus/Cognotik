import {HTML_PATTERN} from './protocol.js';
import {createLogger} from '../util/logger.js';

/**
 * reverse-spec §4 — message store, versioning and the reference dependency index (§5.2).
 *
 * Fixes §20.1 (lastMessageTime high-water mark lives here),
 *       §20.8 (type is derived exactly once, here; ids are never rewritten),
 *       §20.11 (reverse dependency index instead of O(all messages) re-expansion).
 */

const log = createLogger('MessageStore');
const EMPTY = Object.freeze(new Set());
const REFERENCE_PREFIX = 'z';

/** §4.2 — type derivation from the id prefix, and nowhere else. */
export function deriveType(id) {
    if (!id) return 'assistant';
    switch (id[0]) {
        case 'u':
            return 'user';
        case 's':
            return 'system';
        case 'z':
            return 'reference';
        default:
            return 'assistant';
    }
}

export function isReferenceId(id) {
    return typeof id === 'string' && id.startsWith(REFERENCE_PREFIX);
}
/**
  * §3.4 — "pending" is a property of the delivered content: the server embeds
  * `<div class="spinner-border" role="status">…</div>` while it is still working on a
  * message and removes it (by publishing a new version) when the task completes.
  */
export const SPINNER_PATTERN = /class\s*=\s*["'][^"']*\bspinner-border\b/i;
export function isPendingContent(content) {
     return typeof content === 'string' && SPINNER_PATTERN.test(content);
}

/** §4.3 — render filter. */
export function isRenderable(message) {
    return !!(message && message.id) && !isReferenceId(message.id) && (message.content?.length || 0) > 0;
}

export class MessageStore extends EventTarget {
    constructor() {
        super();
        /** id -> record. Map preserves first-sighting insertion order; re-set never reorders (§4.1). */
        this.records = new Map();
        /** id -> version */
        this.versions = new Map();
        /** refId -> Set<hostId> */
        this.hostsByRef = new Map();
        /** hostId -> Set<refId> */
        this.refsByHost = new Map();
        /** §3.1 high-water mark, 0 on cold start. */
        this.lastMessageTime = 0;
    }

    get size() {
        return this.records.size;
    }

    get(id) {
        return this.records.get(id) || null;
    }

    version(id) {
        return this.versions.get(id);
    }

    all() {
        return Array.from(this.records.values());
    }

    rendered() {
        return this.all().filter(isRenderable);
    }

    renderedCount() {
        let count = 0;
        for (const record of this.records.values()) if (isRenderable(record)) count++;
        return count;
    }
     /** Number of visible messages the server is still working on. */
     pendingCount() {
         let count = 0;
         for (const record of this.records.values()) {
             if (record.isPending && isRenderable(record)) count++;
         }
         return count;
     }
     get isPending() {
         return this.pendingCount() > 0;
     }

    /**
     * §4.1 upsert. Replaces wholesale (never merges). `z*` writes bump the version to
     * now() so every host embedding them is invalidated.
     */
    upsert(input) {
        const id = input?.id;
        if (!id) {
            log.warn('Ignoring message without id', input);
            return null;
        }

        const isRef = isReferenceId(id);
        let version = Number.isFinite(input.version) ? input.version : Date.now();
        if (isRef) version = Date.now();

        const content = input.content ?? '';
        const isHtml = typeof input.isHtml === 'boolean' ? input.isHtml : HTML_PATTERN.test(content);

        const record = {
            id,
            version,
            content,
            isHtml,
            rawHtml: isHtml ? content : null,
             // Derived once, here — the render layer must never re-sniff the HTML.
             isPending: typeof input.isPending === 'boolean' ? input.isPending : isPendingContent(content),
            timestamp: Number.isFinite(input.timestamp) ? input.timestamp : Date.now(),
            // 'response' was a legacy transport alias — never propagate it (§4.2, §20.8).
            type: input.type && input.type !== 'response' ? input.type : deriveType(id),
            sanitized: false
        };

        const existed = this.records.has(id);
        const previousVersion = this.versions.get(id);

        this.records.set(id, record);
        this.versions.set(id, version);
        this._touchHighWater(record);

        return {
            record,
            isNew: !existed,
            changed: !existed || previousVersion !== version
        };
    }

    /**
     * Batch upsert. Emits a single `change` event with the changed ids and the set of
     * host messages made dirty by reference updates.
     */
    upsertMany(list) {
        const changed = new Set();
        const dirtyHosts = new Set();

        for (const input of list) {
            const result = this.upsert(input);
            if (!result || !result.changed) continue;
            changed.add(result.record.id);
            if (isReferenceId(result.record.id)) {
                for (const hostId of this.dependentsOf(result.record.id)) dirtyHosts.add(hostId);
            }
        }

        if (changed.size === 0) return {changed, dirtyHosts};
        this.dispatchEvent(new CustomEvent('change', {detail: {changed, dirtyHosts}}));
        return {changed, dirtyHosts};
    }

    /** Archive hydration / local injection (§13). */
    hydrate(list) {
        return this.upsertMany(list || []);
    }

    /** §5.2 — record which references a host message expanded. */
    setDependencies(hostId, refIds) {
        const previous = this.refsByHost.get(hostId);
        if (previous) {
            for (const refId of previous) {
                const hosts = this.hostsByRef.get(refId);
                if (!hosts) continue;
                hosts.delete(hostId);
                if (hosts.size === 0) this.hostsByRef.delete(refId);
            }
        }

        const next = new Set(refIds || []);
        if (next.size === 0) this.refsByHost.delete(hostId);
        else this.refsByHost.set(hostId, next);

        for (const refId of next) {
            let hosts = this.hostsByRef.get(refId);
            if (!hosts) {
                hosts = new Set();
                this.hostsByRef.set(refId, hosts);
            }
            hosts.add(hostId);
        }
    }

    dependentsOf(refId) {
        return this.hostsByRef.get(refId) || EMPTY;
    }

    forget(id) {
        this.records.delete(id);
        this.versions.delete(id);
        this.setDependencies(id, []);
    }

    clear() {
        this.records.clear();
        this.versions.clear();
        this.hostsByRef.clear();
        this.refsByHost.clear();
        this.lastMessageTime = 0;
    }

    /**
     * Server versions are usually epoch-ms; when they are (>= ~2001) they are the most
     * useful replay cursor. Otherwise fall back to the client receipt time.
     */
    _touchHighWater(record) {
        const candidate = record.version > 1e12 ? record.version : record.timestamp;
        if (candidate > this.lastMessageTime) this.lastMessageTime = candidate;
    }
}