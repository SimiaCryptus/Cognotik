export interface WebSocketLike {

    readonly CONNECTING: number;
    readonly OPEN: number;
    readonly CLOSING: number;
    readonly CLOSED: number;

    readyState: number;
    binaryType: string;
    bufferedAmount: number;
    extensions: string;
    protocol: string;
    url: string;

    onopen: ((this: WebSocket, ev: Event) => any) | null;
    onclose: ((this: WebSocket, ev: CloseEvent) => any) | null;
    onerror: ((this: WebSocket, ev: Event) => any) | null;
    onmessage: ((this: WebSocket, ev: MessageEvent) => any) | null;

    send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void;

    close(code?: number, reason?: string): void;
}