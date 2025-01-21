export type LogLevel =
    | 'debug'    // Detailed debugging info
    | 'info'     // General operational info
    | 'warn'     // Warning messages for potentially problematic situations
    | 'error'    // Error messages for serious problems
    | 'critical' // Critical errors that need immediate attention
    | 'none';    // No logging


// Explicitly export MessageType
export type MessageType =
    | 'user'
    | 'assistant'
    | 'system'
    | 'error'
    | 'loading'
    | 'reference'
    | 'response'
    | 'log';      // Specific type for log messages

export interface Message {
    id: string;
    content: string;
    type: MessageType;
    version: number;
    timestamp: number;
    parentId?: string;
    logLevel?: LogLevel;
    logCategory?: string;    // Optional category for grouping logs
    logPriority?: number;    // Optional priority level 1-5
    isHtml: boolean;
    rawHtml: string | null;
    sanitized: boolean;
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