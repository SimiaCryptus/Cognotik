// Define theme names
export type ThemeName = 'main' | 'night' | 'forest' | 'pony' | 'alien' | 'sunset' | 'ocean' | 'cyberpunk';

// Define log levels
export type LogLevel = 'error' | 'warn' | 'info' | 'debug' | 'trace';
// Define log priority
export type LogPriority = 'critical' | 'normal' | 'low';

// Define log message structure
export interface LogMessage {
    level: LogLevel;
    priority: LogPriority;
    message: string;
    timestamp: number;
    context?: Record<string, unknown>;
}

// Define console styles
export interface ConsoleStyle {
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