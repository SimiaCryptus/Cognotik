export type LogLevel =
    | 'critical'

    | 'error'

    | 'none';


export type MessageType =
    | 'user'
    | 'assistant'
    | 'system'
    | 'error'
    | 'loading'
    | 'reference'
    | 'response'
    | 'log'

    | 'alert';


export interface Message {
    id: string;
    content: string;
    type: MessageType;
    version: number;
    timestamp: number;
    parentId?: string;
    logLevel?: LogLevel;
    logCategory?: 'security' | 'system' | 'performance' | 'data';

    severity?: 1 | 2;

    isHtml: boolean;
    rawHtml: string | null;
    sanitized: boolean;
    resolved?: boolean;

}

export interface MessageUpdate {
    id: string;
    updates: Partial<Message>;
}

export interface MessageState {
    messages: Message[];
    pendingMessages: Message[];
    messageQueue: Message[];
    isProcessing: boolean;
    messageVersions: Record<string, number>;
    pendingUpdates?: Message[];
}