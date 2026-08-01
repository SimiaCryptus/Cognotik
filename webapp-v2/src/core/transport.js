import {
    CONTROL_TYPES,
    decodeDataFrame,
    encodeAction,
    encodePing,
    encodePong,
    encodeUserText,
    parseControlFrame
} from './protocol.js';
import {createLogger} from '../util/logger.js';
import {WS_PORT} from '../config/env.js';

/**
 * reverse-spec §3 — the SINGLE reconnect authority (fixes §20.2).
 *
 * Public surface is §3.7: an EventTarget emitting
 *   'message'      detail: Message[]     (batched, chunked)
 *   'open' | 'close'
 *   'reconnecting' detail: attemptNumber
 *   'error'        detail: Error
 */

const log = createLogger('WebSocket');

export const CONNECT_TIMEOUT = 10_000;
export const HEARTBEAT_INTERVAL = 30_000;
export const BASE_RECONNECT_DELAY = 1_000;
export const MAX_RECONNECT_DELAY = 30_000;
export const MAX_RECONNECT_ATTEMPTS = 5;
export const SEND_QUEUE_INTERVAL = 50;

/** §3.6 buffering constants. */
const REPLAY_WINDOW = 10_000;
const REPLAY_IDLE_FLUSH = 1_000;
const AGGREGATE_DEBOUNCE = 100;
const CHUNK_SIZE = 10;
/** Diagnostics only — decoding 1006 vs 1011 is the difference between a proxy and a crash. */
const CLOSE_CODES = Object.freeze({
     1000: 'normal',
     1001: 'going away',
     1002: 'protocol error',
     1003: 'unsupported data',
     1005: 'no status received',
     1006: 'abnormal close (no close frame — network/proxy)',
     1008: 'policy violation',
     1009: 'message too big',
     1011: 'server internal error',
     1012: 'service restart',
     1013: 'try again later',
     1015: 'TLS handshake failure'
});

/** '/coding/agent' -> '/coding/' ; '/' -> '/' (§3.1) */
export function derivePath(pathname) {
    const segment = String(pathname || '/').split('/').filter(Boolean)[0];
    return segment ? `/${segment}/` : '/';
}

export class Transport extends EventTarget {
    constructor(options = {}) {
        super();
        this.config = {
            host: options.host || '',
            port: options.port || WS_PORT || '',
            path: options.path || '',
            protocol: options.protocol || ''
        };
        /** Supplied by the store so the replay cursor is real (fixes §20.1). */
        this.getLastMessageTime = options.getLastMessageTime || (() => 0);

        this.sessionId = options.sessionId || null;
        this.socket = null;

        this.forcedClose = false;
        this.reconnecting = false;
        this.reconnectAttempts = 0;
        this.connectionStartTime = 0;

        this.sendQueue = [];
        this.sendTimer = null;
        this.heartbeatTimer = null;
        this.connectTimer = null;
        this.reconnectTimer = null;

        this.replayBuffer = [];
        this.replayTimer = null;
        this.aggregateBuffer = [];
        this.aggregateTimer = null;
    }

    /* ---------------------------------------------------------------- state */

    get isConnected() {
        return this.socket?.readyState === WebSocket.OPEN;
    }

    get readyState() {
        return this.socket ? this.socket.readyState : WebSocket.CLOSED;
    }

    get queuedFrames() {
        return this.sendQueue.length;
    }

    /* ------------------------------------------------------------ lifecycle */

    connect(sessionId, configOverride) {
        if (sessionId) this.sessionId = sessionId;
        if (configOverride) this.configure(configOverride);
        this.forcedClose = false;
        this.reconnectAttempts = 0;
        this._open();
        return this;
    }

    configure(partial = {}) {
        this.config = {...this.config, ...partial};
        return this.config;
    }

    /** User-initiated: suppresses reconnection (§3.2). */
    disconnect() {
        log.info('Disconnect requested by user');
        this.forcedClose = true;
        this._clearTimer('reconnectTimer');
        this._stopHeartbeat();
        this._clearTimer('connectTimer');
        if (this.socket) {
            this.socket.onopen = this.socket.onclose = this.socket.onerror = this.socket.onmessage = null;
            try {
                this.socket.close();
            } catch (err) {
                log.warn('close() threw', err);
            }
            this.socket = null;
        }
        this._flushBuffers('disconnect');
         this._failQueue(new Error('Transport disconnected by user'));
        this._emit('close', {forced: true});
    }

    /** Manual reconnect: resets the attempt counter. */
    reconnect() {
        this._clearTimer('reconnectTimer');
        this.reconnecting = false;
        this.forcedClose = false;
        this.reconnectAttempts = 0;
        if (this.socket) {
            try {
                this.socket.close();
            } catch {
                /* ignore */
            }
            this.socket = null;
        }
        this._open();
    }

    _buildUrl() {
        const loc = window.location;
        const secure = (this.config.protocol || loc.protocol) === 'https:' || this.config.protocol === 'wss:';
        const protocol = secure ? 'wss:' : 'ws:';
        const host = this.config.host || loc.hostname;
        const port = String(this.config.port || loc.port || (secure ? '443' : '8083'));
        const isDefaultPort = (protocol === 'ws:' && port === '80') || (protocol === 'wss:' && port === '443');
        const path = this.config.path || derivePath(loc.pathname);
        const authority = `${protocol}//${host}` + (!port || isDefaultPort ? '' : `:${port}`);
        const lastMessageTime = this.getLastMessageTime() || 0;
        return `${authority}${path}ws?sessionId=${encodeURIComponent(this.sessionId)}` +
            `&lastMessageTime=${lastMessageTime}`;
    }

    _open() {
        if (!this.sessionId) {
            log.error('Cannot open socket without a sessionId');
            return;
        }
        if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
            return;
        }

        const url = this._buildUrl();
        log.info('Opening socket', {url, attempt: this.reconnectAttempts});

        let socket;
        try {
            socket = new WebSocket(url);
        } catch (err) {
            log.error('WebSocket constructor failed', err);
            this._emit('error', err instanceof Error ? err : new Error(String(err)));
            this._scheduleReconnect();
            return;
        }
        this.socket = socket;

        this._clearTimer('connectTimer');
        this.connectTimer = setTimeout(() => {
            if (socket.readyState !== WebSocket.OPEN) {
                log.warn(`No open within ${CONNECT_TIMEOUT}ms — forcing close`);
                try {
                    socket.close();
                } catch {
                    /* ignore */
                }
            }
        }, CONNECT_TIMEOUT);

        socket.onopen = () => this._onOpen();
        socket.onclose = (event) => this._onClose(event);
        socket.onerror = () => this._onError();
        socket.onmessage = (event) => this._onMessage(event);
    }

    _onOpen() {
        this._clearTimer('connectTimer');
        this.reconnectAttempts = 0;
        this.reconnecting = false;
        this.forcedClose = false;
        this.connectionStartTime = Date.now();
        log.info('Socket open', {sessionId: this.sessionId, queued: this.sendQueue.length});
        this._startHeartbeat();
        this._emit('open', {sessionId: this.sessionId});
        this._flushQueue();
    }

    _onClose(event) {
        this._clearTimer('connectTimer');
        this._stopHeartbeat();
        // Deliver anything already buffered rather than dropping it, then clear (§3.6).
        this._flushBuffers('close');
         log.count('closes');
         log.warn('Socket closed', {
             code: event?.code,
             codeMeaning: CLOSE_CODES[event?.code] || 'unknown',
             reason: event?.reason || '(none)',
             wasClean: event?.wasClean,
             forced: this.forcedClose,
             uptimeMs: this.connectionStartTime ? Date.now() - this.connectionStartTime : 0,
             queued: this.sendQueue.length
         });
        this._emit('close', {code: event?.code, reason: event?.reason, forced: this.forcedClose});
        if (!this.forcedClose) this._scheduleReconnect();
    }

    _onError() {
        const error = new Error('WebSocket transport error');
         log.count('errors');
         log.error(error.message, {
             readyState: this.readyState,
             url: this.socket?.url,
             attempt: this.reconnectAttempts,
             queued: this.sendQueue.length
         });
        this._emit('error', error);
        // A close event follows; reconnection is scheduled there.
    }

    _scheduleReconnect() {
        if (this.forcedClose || this.reconnecting) return;

        if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            this.forcedClose = true;
            const error = new Error(`Maximum reconnection attempts (${MAX_RECONNECT_ATTEMPTS}) reached`);
             log.error(error.message, {sessionId: this.sessionId, queued: this.sendQueue.length});
             // Nothing will ever flush these; reject so callers can surface it (§3.2).
             this._failQueue(error);
            this._emit('error', error);
            return;
        }

        const delay = Math.min(BASE_RECONNECT_DELAY * 2 ** this.reconnectAttempts, MAX_RECONNECT_DELAY);
        this.reconnectAttempts += 1;
        this.reconnecting = true;
        log.warn('Scheduling reconnect', {attempt: this.reconnectAttempts, delay});
        this._emit('reconnecting', this.reconnectAttempts);

        this._clearTimer('reconnectTimer');
        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            this.reconnecting = false;
            this._open();
        }, delay);
    }

    /* ------------------------------------------------------------ heartbeat */

    _startHeartbeat() {
        this._stopHeartbeat();
        this.heartbeatTimer = setInterval(() => {
            if (this.readyState !== WebSocket.OPEN) return;
            try {
                this.socket.send(encodePing());
            } catch (err) {
                log.warn('Heartbeat send failed', err);
            }
        }, HEARTBEAT_INTERVAL);
    }

    _stopHeartbeat() {
        if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
        this.heartbeatTimer = null;
    }

    /* ---------------------------------------------------------------- inbound */

    _onMessage(event) {
        const raw = typeof event.data === 'string' ? event.data : '';
         log.count('frames-in');

        const control = parseControlFrame(raw);
        if (control) {
            switch (control.type) {
                case CONTROL_TYPES.PONG:
                case CONTROL_TYPES.CONNECT:
                    return;
                case CONTROL_TYPES.PING:
                    this.send(encodePong());
                    return;
                default:
                    log.debug('Ignoring unknown control frame', {type: control.type});
                    return;
            }
        }

        const message = decodeDataFrame(raw);
        if (!message) {
             log.count('frames-dropped');
             log.warn('Dropping malformed frame', {
                 preview: raw.slice(0, 120),
                 length: raw.length,
                 expected: '<id>,<version>,<content>'
             });
            return;
        }
         log.debug('Frame decoded', {id: message.id, version: message.version, bytes: message.content.length});
        this._buffer(message);
    }

    /** §3.6 — cold-start replay burst vs steady-state aggregation. */
    _buffer(message) {
        const coldStart = this.connectionStartTime > 0 &&
            Date.now() - this.connectionStartTime < REPLAY_WINDOW;

        if (coldStart) {
            this.replayBuffer.push(message);
            this._clearTimer('replayTimer');
            this.replayTimer = setTimeout(() => {
                this.replayTimer = null;
                const batch = this.replayBuffer;
                this.replayBuffer = [];
                this._drain(batch);
            }, REPLAY_IDLE_FLUSH);
            return;
        }

        this.aggregateBuffer.push(message);
        this._clearTimer('aggregateTimer');
        this.aggregateTimer = setTimeout(() => {
            this.aggregateTimer = null;
            const batch = this.aggregateBuffer;
            this.aggregateBuffer = [];
            this._drain(batch);
        }, AGGREGATE_DEBOUNCE);
    }

    _flushBuffers(reason) {
        this._clearTimer('replayTimer');
        this._clearTimer('aggregateTimer');
        const pending = this.replayBuffer.concat(this.aggregateBuffer);
        this.replayBuffer = [];
        this.aggregateBuffer = [];
        if (pending.length) {
            log.debug('Flushing buffered messages', {reason, count: pending.length});
            this._drain(pending);
        }
    }

    /** Chunks of 10, yielding to the event loop between chunks (§3.6). */
    _drain(batch) {
        if (!batch.length) return;
        const chunks = [];
        for (let i = 0; i < batch.length; i += CHUNK_SIZE) chunks.push(batch.slice(i, i + CHUNK_SIZE));

        const step = () => {
            const chunk = chunks.shift();
            if (!chunk) return;
            try {
                this._emit('message', chunk);
            } catch (err) {
                log.error('Message handler threw', {
                    error: err,
                    ids: chunk.map((m) => m.id),
                    types: chunk.map((m) => m.type)
                });
            }
            if (chunks.length) setTimeout(step, 0);
        };
        step();
    }

    /* --------------------------------------------------------------- outbound */

    /**
     * Queues when not OPEN and kicks a reconnect. Resolves once the frame has
     * actually been written to the socket — no frame is ever silently dropped (§3.2).
     */
    send(text) {
        return new Promise((resolve, reject) => {
             if (typeof text !== 'string' || text.length === 0) {
                 const error = new Error('Refusing to send an empty/non-string frame');
                 log.error(error.message, {type: typeof text});
                 reject(error);
                 return;
             }
            this.sendQueue.push({text, resolve, reject});
            if (!this.isConnected) {
                 log.count('frames-queued');
                 log.warn('Socket not open — frame queued', {
                     queued: this.sendQueue.length,
                     readyState: this.readyState,
                     preview: text.slice(0, 80)
                 });
                this._ensureConnection();
                return;
            }
            this._flushQueue();
        });
    }

    sendAction(messageId, action) {
         const frame = encodeAction(messageId, action);
         log.debug('Encoding action frame', {messageId, action, frame});
         return this.send(frame);
    }

    sendUserText(messageId, text) {
         log.debug('Encoding userTxt frame', {messageId, length: text?.length || 0});
         return this.send(encodeUserText(messageId, text));
    }

    /** Composer submissions are raw text with NO `!` prefix (§3.5). */
    sendMessage(text) {
        return this.send(text);
    }

    _ensureConnection() {
        if (this.isConnected) return;
        if (this.socket && this.socket.readyState === WebSocket.CONNECTING) return;
        if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS || this.forcedClose) {
            // A user action beats a previously exhausted backoff budget.
            this.reconnect();
            return;
        }
        this._scheduleReconnect();
    }

    _flushQueue() {
        if (this.sendTimer) return;
        const step = () => {
            this.sendTimer = null;
            if (!this.sendQueue.length) return;
            if (!this.isConnected) return; // resumed from _onOpen

            const entry = this.sendQueue.shift();
            try {
                this.socket.send(entry.text);
                 log.count('frames-out');
                 log.debug('Frame sent', {preview: entry.text.slice(0, 120), remaining: this.sendQueue.length});
                entry.resolve(true);
            } catch (err) {
                 log.count('send-failures');
                 log.error('Frame send failed; re-queueing', {
                     error: err,
                     readyState: this.readyState,
                     preview: entry.text.slice(0, 120)
                 });
                this.sendQueue.unshift(entry);
                this._ensureConnection();
                return;
            }
            if (this.sendQueue.length) this.sendTimer = setTimeout(step, SEND_QUEUE_INTERVAL);
        };
        step();
    }
     /** Terminal failure: never leave a caller awaiting a frame that can never be written. */
     _failQueue(error) {
         if (!this.sendQueue.length) return;
         const pending = this.sendQueue;
         this.sendQueue = [];
         log.error('Failing queued frames', {count: pending.length, reason: error.message});
         for (const entry of pending) {
             try {
                 entry.reject(error);
             } catch (err) {
                 log.warn('Queued frame rejection handler threw', err);
             }
         }
     }

    /* ----------------------------------------------------------------- utils */
     /** One-call snapshot for triage: `app.transport.stats()` (§16). */
     stats() {
         return {
             sessionId: this.sessionId,
             url: this.socket?.url || null,
             readyState: this.readyState,
             connected: this.isConnected,
             forcedClose: this.forcedClose,
             reconnecting: this.reconnecting,
             reconnectAttempts: this.reconnectAttempts,
             queued: this.sendQueue.length,
             buffered: this.replayBuffer.length + this.aggregateBuffer.length,
             uptimeMs: this.connectionStartTime ? Date.now() - this.connectionStartTime : 0,
             counters: log.counters()
         };
     }

    on(type, handler) {
        this.addEventListener(type, handler);
        return () => this.removeEventListener(type, handler);
    }

    off(type, handler) {
        this.removeEventListener(type, handler);
    }

    _emit(type, detail) {
        this.dispatchEvent(new CustomEvent(type, {detail}));
    }

    _clearTimer(name) {
        if (this[name]) clearTimeout(this[name]);
        this[name] = null;
    }
}