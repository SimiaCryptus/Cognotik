import {WebSocketLike} from '../types/websocket';
import {store} from '../store';
import {Message} from "../types/messages";
import {WebSocketConfig} from "../types/config";
import {debounce} from "../utils/tabHandling";

export class WebSocketService implements WebSocketLike {
    // Implement required WebSocket-like interface properties
    public readonly CONNECTING = 0;
    public readonly OPEN = 1;
    public readonly CLOSING = 2;
    public readonly CLOSED = 3;
    public readyState: number = WebSocket.CLOSED;
    public binaryType = 'blob';
    public bufferedAmount = 0;
    public extensions = '';
    public protocol = '';
    public url = '';
    public onopen: ((this: WebSocket, ev: Event) => any) | null = null;
    public onclose: ((this: WebSocket, ev: CloseEvent) => any) | null = null;
    public onerror: ((this: WebSocket, ev: Event) => any) | null = null;
    public onmessage: ((this: WebSocket, ev: MessageEvent) => any) | null = null;
    public ws: WebSocket | null = null;
    // Add event emitter functionality
    private eventListeners: { [key: string]: ((...args: any[]) => void)[] } = {};
    // Constants for connection management
    private readonly HEARTBEAT_INTERVAL = 30000; // 30 seconds
    private readonly HEARTBEAT_TIMEOUT = 5000; // 5 seconds
    private readonly CONNECTION_TIMEOUT = 10000; // 10 seconds
    private readonly BASE_RECONNECT_DELAY = 1000; // 1 second
    private readonly MAX_RECONNECT_DELAY = 30000; // 30 seconds
    // Connection state tracking
    private lastHeartbeatResponse = 0;
    private lastHeartbeatSent = 0;
    private connectionState: 'connecting' | 'connected' | 'disconnected' | 'error' = 'disconnected';
    private forcedClose = false;
    private timers = {
        heartbeat: null as NodeJS.Timeout | null,
        reconnect: null as NodeJS.Timeout | null,
        connection: null as NodeJS.Timeout | null
    };
    private messageQueue: string[] = [];
    private isProcessingQueue = false;
    private readonly QUEUE_PROCESS_INTERVAL = 50; // ms
    private readonly DEBUG = process.env.NODE_ENV === 'development';
    private maxReconnectAttempts = 5;
    private reconnectAttempts = 0;
    private sessionId = '';
    private messageHandlers: ((data: Message) => void)[] = [];
    private connectionHandlers: ((connected: boolean) => void)[] = [];
    private errorHandlers: ((error: Error) => void)[] = [];
    private isReconnecting = false;
    private connectionTimeout: NodeJS.Timeout | null = null;
    private connectionStartTime = 0;
    private messageBuffer: Message[] = [];
    private bufferTimeout: NodeJS.Timeout | null = null;
    private aggregateBuffer: Message[] = [];
    private aggregateTimeout: NodeJS.Timeout | null = null;
    private readonly AGGREGATE_INTERVAL = 100; // 100ms aggregation interval

    public close(code?: number, reason?: string): void {
        this.forcedClose = true;
        if (this.ws) {
            this.ws.close(code, reason);
        }
        this.clearTimers();
        this.isReconnecting = false;
        this.reconnectAttempts = 0;
        this.connectionState = 'disconnected';
        this.ws = null;
    }

    public on(event: string, callback: (...args: any[]) => void): void {
        if (!this.eventListeners[event]) {
            this.eventListeners[event] = [];
        }
        this.eventListeners[event].push(callback);
    }

    public off(event: string, callback: (...args: any[]) => void): void {
        if (!this.eventListeners[event]) return;
        this.eventListeners[event] = this.eventListeners[event].filter(cb => cb !== callback);
    }

    // Add reconnect method
    public reconnect(): void {
        if (this.isReconnecting) {
            // Skip duplicate reconnect attempts silently
            return;
        }
        this.forcedClose = false;
        this.disconnect();
        this.reconnectAttempts = 0;
        this.isReconnecting = false;
        this.connect(this.sessionId);
    }

    public disconnect(): void {
        if (this.ws) {
            this.forcedClose = true;
            this.isReconnecting = false;
            this.reconnectAttempts = 0;
            this.clearTimers();
            this.ws.close();
            this.ws = null;
        }
    }

    public getSessionId(): string {
        return this.sessionId;
    }

    public addErrorHandler(handler: (error: Error) => void): void {
        this.errorHandlers.push(handler);
    }

    public removeErrorHandler(handler: (error: Error) => void): void {
        this.errorHandlers = this.errorHandlers.filter(h => h !== handler);
    }

    send(message: string): void {
        if (this.ws?.readyState === WebSocket.OPEN) {
            this.queueMessage(message);
        } else {
            console.warn('[WebSocket] Connection not open, attempting reconnect before sending');
            this.reconnectAndSend(message);
        }
    }

    public addConnectionHandler(handler: (connected: boolean) => void): void {
        this.connectionHandlers.push(handler);
    }

    public removeConnectionHandler(handler: (connected: boolean) => void): void {
        this.connectionHandlers = this.connectionHandlers.filter(h => h !== handler);
    }

    public isConnected(): boolean {
        return this.ws?.readyState === WebSocket.OPEN;
    }

    connect(config: string | WebSocketConfig): void {
        try {
            if (!config) {
                throw new Error('[WebSocket] Connection config is required');
            }
            let wsConfig: WebSocketConfig;

            // If string is passed, treat it as sessionId and use stored config
            if (typeof config === 'string') {
                this.sessionId = config;
                wsConfig = this.getConfig();
            } else {
                // If object is passed, use it as config
                this.sessionId = 'default';
                wsConfig = config;
            }

            // Clear any existing connection timeout
            if (this.connectionTimeout) {
                clearTimeout(this.connectionTimeout);
            }

            const path = this.getWebSocketPath();
            // Only create new connection if not already connected or reconnecting
            if (!this.isConnected() && !this.isReconnecting) {
                const lastMessageTime = Math.max(...this.messageHandlers
                    .map(h => (h as any).lastMessageTime || 0)
                    .filter(t => t > 0));
                // Construct URL with proper handling of default ports
                let wsUrl = `${wsConfig.protocol}//${wsConfig.url}`;
                // Only add port if it's non-standard
                if ((wsConfig.protocol === 'ws:' && wsConfig.port !== '80') ||
                    (wsConfig.protocol === 'wss:' && wsConfig.port !== '443')) {
                    wsUrl += `:${wsConfig.port}`;
                }
                wsUrl += `${path}ws?sessionId=${this.sessionId}&lastMessageTime=${lastMessageTime}`;
                console.info(`[WebSocket] Establishing connection to ${wsUrl}`);
                this.ws = new WebSocket(wsUrl);
                this.setupEventHandlers();
                // Set connection timeout
                this.connectionTimeout = setTimeout(() => {
                    if (this.ws?.readyState !== WebSocket.OPEN) {
                        console.warn('[WebSocket] Connection timeout reached, attempting to reconnect');
                        this.ws?.close();
                        this.attemptReconnect();
                    }
                }, 10000); // Increase timeout to 10 seconds
            }
        } catch (error) {
            console.error('[WebSocket] Failed to establish connection:', error);
            this.attemptReconnect();
        }
    }

    removeMessageHandler(handler: (data: any) => void): void {
        this.messageHandlers = this.messageHandlers.filter((h) => h !== handler);
    }

    addMessageHandler(handler: (data: any) => void): void {
        this.messageHandlers.push(handler);
    }

    private handleConnectionFailure(error: Error): void {
        console.error('[WebSocket] Connection failure:', error);
        this.connectionState = 'error';
        this.errorHandlers.forEach(handler => handler(error));
        if (!this.isReconnecting) {
            this.attemptReconnect();
        }
    }

    private emit(event: string, ...args: any[]): void {
        if (!this.eventListeners[event]) return;
        this.eventListeners[event].forEach(callback => callback(...args));
    }

    private clearTimers(): void {
        Object.values(this.timers).forEach(timer => {
            if (timer) clearTimeout(timer);
        });
        this.timers = {
            heartbeat: null,
            reconnect: null,
            connection: null
        };
    }

    private startHeartbeat(): void {
        if (this.timers.heartbeat) return;
        this.lastHeartbeatResponse = Date.now();
        this.timers.heartbeat = setInterval(() => {
            if (this.ws?.readyState === WebSocket.OPEN) {
                this.lastHeartbeatSent = Date.now();
                try {
                    this.ws.send(JSON.stringify({type: 'ping', timestamp: Date.now()}));
                } catch (error) {
                    console.error('[WebSocket] Failed to send heartbeat:', error);
                    this.handleConnectionFailure(new Error('Failed to send heartbeat'));
                }
            }
        }, this.HEARTBEAT_INTERVAL);
    }

    private queueMessage(message: string): void {
        this.messageQueue.push(message);
        if (!this.isProcessingQueue) {
            this.processMessageQueue().then(r => {
                this.debugLog('[WebSocket] Message queue processed:');
            });
        }
    }

    private async processMessageQueue(): Promise<void> {
        if (this.isProcessingQueue || this.messageQueue.length === 0) return;
        this.isProcessingQueue = true;
        while (this.messageQueue.length > 0) {
            const message = this.messageQueue.shift();
            if (message && this.ws?.readyState === WebSocket.OPEN) {
                this.ws.send(message);
                await new Promise(resolve => setTimeout(resolve, this.QUEUE_PROCESS_INTERVAL));
            }
        }
        this.isProcessingQueue = false;
    }

    private reconnectAndSend(message: string): void {
        if (this.isReconnecting) {
            this.queueMessage(message); // Queue message to be sent after reconnection
            return;
        }
        console.warn('[WebSocket] Connection lost - initiating reconnect before sending message');
        const onConnect = (connected: boolean) => {
            if (connected) {
                console.log('[WebSocket] Reconnected successfully, sending queued message');
                this.removeConnectionHandler(onConnect);
                this.send(message);
            }
        };
        this.addConnectionHandler(onConnect);
        this.forcedClose = false;
        this.connect(this.sessionId);
    }

    private debugLog(message: string, ...args: any[]) {
        if (this.DEBUG) {
            console.debug(`[WebSocket] ${message}`, ...args);
        }
    }

    private stopHeartbeat() {
        if (this.timers.heartbeat) {
            clearInterval(this.timers.heartbeat);
            this.timers.heartbeat = null;
        }
    }

    private getConfig() {
        const state = store.getState();
        // Load from localStorage as fallback if store is not yet initialized
        if (!state.config?.websocket) {
            try {
                const savedConfig = localStorage.getItem('websocketConfig');
                if (savedConfig) {
                    const config = JSON.parse(savedConfig);
                    this.debugLog('Using config from localStorage:', config);
                    // Ensure protocol is correct based on window.location.protocol
                    config.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                    return config;
                }
            } catch (error) {
                console.error('[WebSocket] Error reading config from localStorage:', error);
            }
        }
        const defaultPort = window.location.protocol === 'https:' ? '443' : '8083';
        return {
            url: window.location.hostname,
            port: state.config?.websocket?.port || window.location.port || defaultPort,
            protocol: window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        };
    }

    private getWebSocketPath(): string {
        const path = window.location.pathname;
        // Simplify path handling to use the base path
        let wsPath = '/';
        // If we're in a subdirectory, use that as the base path
        if (path !== '/' && path.length > 0) {
            // Extract the first path segment
            const match = path.match(/^\/([^/]+)/);
            if (match && match[1]) {
                wsPath = `/${match[1]}/`;
            }
        }
        return wsPath;
    }

    private setupEventHandlers(): () => void {
        if (!this.ws) {
            console.error('[WebSocket] Failed to setup handlers - WebSocket not initialized');
            return () => {
                // Cleanup not needed since WebSocket was never initialized
                console.debug('[WebSocket] No cleanup needed - WebSocket was never initialized');
            };
        }
        let isDestroyed = false;
        // Store original handlers to properly clean them up later
        const originalOnOpen = this.ws.onopen;
        const originalOnMessage = this.ws.onmessage;
        const originalOnClose = this.ws.onclose;
        const originalOnError = this.ws.onerror;


        // Combined onopen handler
        this.ws.onopen = () => {
            if (isDestroyed) return;
            console.info('[WebSocket] Connection established');
            this.connectionState = 'connected';
            this.reconnectAttempts = 0;
            this.isReconnecting = false;
            this.connectionStartTime = Date.now();
            this.lastHeartbeatResponse = Date.now();
            this.startHeartbeat();
            this.connectionHandlers.forEach(handler => handler(true));
            if (this.connectionTimeout) {
                clearTimeout(this.connectionTimeout);
            }
        };
        const debouncedProcessMessages = debounce((messages: Message[]) => {
            const batch = [...messages];
            this.aggregateBuffer = [];
            // Process messages in chunks to avoid blocking the main thread
            const processChunk = (startIndex: number, chunkSize: number) => {
                const endIndex = Math.min(startIndex + chunkSize, batch.length);
                for (let i = startIndex; i < endIndex; i++) {
                    const msg = batch[i];
                    this.messageHandlers.forEach(handler => handler(msg));
                }
                if (endIndex < batch.length) {
                    setTimeout(() => processChunk(endIndex, chunkSize), 0);
                }
            };
            processChunk(0, 10); // Process 10 messages at a time
        }, this.AGGREGATE_INTERVAL);
        this.ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'pong') {
                    this.lastHeartbeatResponse = Date.now();
                    return;
                }
                if (data.type === 'ping') {
                    // Reply with pong when a ping is received and log the event
                    this.ws?.send(JSON.stringify({type: 'pong'}));
                    return;
                }
            } catch (e) {
                // Not a JSON message, process normally
            }
            // Process incoming message – splitting at the first two commas.
            this.forcedClose = false;
            const data = event.data;
            // Fix message parsing to properly handle commas in content
            const firstCommaIndex = data.indexOf(',');
            const secondCommaIndex = firstCommaIndex > -1 ? data.indexOf(',', firstCommaIndex + 1) : -1;

            if (firstCommaIndex === -1 || secondCommaIndex === -1) {
                console.error('[WebSocket] Invalid message format received:', data);
                return;
            }
            const id = data.substring(0, firstCommaIndex);
            const version = data.substring(firstCommaIndex + 1, secondCommaIndex);
            const content = data.substring(secondCommaIndex + 1);
            const timeSinceConnection = Date.now() - this.connectionStartTime;
            const shouldBuffer = timeSinceConnection < 10000; // Buffer for first 10 seconds

            if (!id || !version) {
                console.error('[WebSocket] Missing required message fields:', event.data);
                return;
            }

            const isHtml = typeof content === 'string' && (/<[a-z][\s\S]*>/i.test(content));

            const message: Message = {
                id,
                type: 'response',
                version,
                content,
                isHtml,
                rawHtml: content,
                timestamp: Date.now(),
                sanitized: false
            };


            if (shouldBuffer) {
                this.messageBuffer.push(message);
                if (this.bufferTimeout) {
                    clearTimeout(this.bufferTimeout);
                }
                this.bufferTimeout = setTimeout(() => {
                    const messages = [...this.messageBuffer];
                    this.messageBuffer = [];
                    debouncedProcessMessages(messages);
                }, 1000);
            } else {
                // After warmup period, use message aggregation
                this.aggregateBuffer.push(message);
                if (this.aggregateBuffer.length === 1) {
                    debouncedProcessMessages(this.aggregateBuffer);
                }
            }
        };

        this.ws.onclose = () => {
            if (isDestroyed) return;
            console.info('[WebSocket] Connection closed');
            if (this.bufferTimeout) {
                clearTimeout(this.bufferTimeout);
                this.bufferTimeout = null;
            }
            if (this.aggregateTimeout) {
                clearTimeout(this.aggregateTimeout);
                this.aggregateTimeout = null;
            }
            this.messageBuffer = [];
            this.stopHeartbeat();
            this.connectionHandlers.forEach(handler => handler(false));
            if (!this.isReconnecting) {
                this.attemptReconnect();
            }
        };

        this.ws.onerror = (error) => {
            if (isDestroyed) return;
            this.connectionState = 'error';
            console.error('[WebSocket] Connection error:', error);
            this.errorHandlers.forEach(handler => handler(new Error('WebSocket connection error')));
            if (!this.isReconnecting) {
                this.attemptReconnect();
            }
        };
        // Add cleanup method
        return () => {
            isDestroyed = true;
            // Properly remove event handlers to prevent memory leaks
            if (this.ws) {
                this.ws.onopen = originalOnOpen;
                this.ws.onmessage = originalOnMessage;
                this.ws.onclose = originalOnClose;
                this.ws.onerror = originalOnError;
            }
            this.clearTimers();
        };
    }

    private attemptReconnect(): void {
        if (this.forcedClose) {
            return;
        }

        const backoffDelay = Math.min(
            this.BASE_RECONNECT_DELAY * Math.pow(2, this.reconnectAttempts),
            this.MAX_RECONNECT_DELAY
        );

        const maxAttempts = this.maxReconnectAttempts;
        if (this.reconnectAttempts >= maxAttempts) {
            this.isReconnecting = false;
            this.reconnectAttempts = 0;
            this.forcedClose = true;
            console.error(`[WebSocket] Connection failed after ${maxAttempts} attempts`);
            this.errorHandlers.forEach(handler =>
                handler(new Error(`Maximum reconnection attempts (${maxAttempts}) reached`))
            );
        } else {
            this.isReconnecting = true;
            console.info(`[WebSocket] Reconnect attempt ${this.reconnectAttempts + 1}/${maxAttempts}`);
            this.emit('reconnecting', this.reconnectAttempts + 1);
            this.connectionHandlers.forEach(handler => handler(false));
            if (null != this.timers.reconnect) {
                clearTimeout(this.timers.reconnect);
            }
            this.timers.reconnect = setTimeout(() => {
                if (this.ws) {
                    this.ws.close();
                    this.ws = null;
                }
                this.reconnectAttempts++;
                this.connect(this.sessionId);
            }, backoffDelay);
        }
    }
}

export default new WebSocketService();