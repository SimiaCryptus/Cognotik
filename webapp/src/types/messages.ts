export type LogLevel =
    | 'critical' // Critical errors requiring immediate attention (e.g. system failures, security issues)
    | 'error'    // Significant errors that impact functionality but aren't critical
    | 'none';    // Logging disabled


// Explicitly export MessageType
export type MessageType =
    | 'user'
    | 'assistant'
    | 'system'
    | 'error'
    | 'loading'
    | 'reference'
    | 'response'
    | 'log'       // Critical system events and errors
    | 'alert';    // Time-sensitive notifications requiring attention

export interface Message {
    id: string;
    content: string;
    type: MessageType;
    version: number;
    timestamp: number;
    parentId?: string;
    logLevel?: LogLevel;
    logCategory?: 'security' | 'system' | 'performance' | 'data';  // Strict categories for critical events
    severity?: 1 | 2;        // 1: Immediate action required, 2: Action required soon
    isHtml: boolean;
    rawHtml: string | null;
    sanitized: boolean;
    resolved?: boolean;      // Track if critical issues were addressed
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