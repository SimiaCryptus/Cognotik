// Define theme names
// Re-export ThemeName from theme.ts to maintain single source of truth
export type { ThemeName } from './types/theme';

// Define log levels
export type LogLevel = 'critical' | 'error' | 'warn' | 'info';
// Define log priority
export type LogPriority = 'high' | 'normal' | 'low';

// Define log message structure
export interface LogMessage {
    level: LogLevel;
    priority: LogPriority;
    source: string;
    message: string;
    timestamp: number;
    context: {
        userId?: string;
        errorCode?: string;
        stackTrace?: string;
        additionalInfo?: Record<string, unknown>;
    };
}

// Define console styles
export interface ConsoleStyle {
    // Add specific styles for critical logs
    isCritical?: boolean;
    color?: string;
    background?: string;
    bold?: boolean;
    italic?: boolean;
    underline?: boolean;
}

// UserInfo type
export interface UserInfo {
    name: string;
    isAuthenticated: boolean;
    preferences?: Record<string, unknown>;
}