import {ColorThemeName, LayoutThemeName} from './theme';
import {ConsoleStyle} from "../types";

export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'critical';

export interface LogFilter {
    include?: string[];
    exclude?: string[];
}

export interface WebSocketConfig {
    url: string;
    port: string;
    protocol: 'ws:' | 'wss:';
    retryAttempts?: number;
    timeout?: number;
}

export interface ConsoleConfig {
    enabled: boolean;
    showTimestamp: boolean;
    showLevel: boolean;
    showSource: boolean;
    logLevel: LogLevel;
    filter?: LogFilter;
    styles: {
        debug: ConsoleStyle;
        info: ConsoleStyle;
        warn: ConsoleStyle;
        error: ConsoleStyle;
    };
}

export interface LoggingConfig {
    enabled: boolean;
    maxEntries: number;
    minLogLevel: LogLevel;
    persistLogs: boolean;
    criticalEventsOnly?: boolean;
    filter?: LogFilter;
    console: ConsoleConfig;
}

export interface ThemeConfig {
    currentTheme: ColorThemeName; // Renamed from 'current'
    currentLayout: LayoutThemeName;
    autoSwitch: boolean;
}

export interface AppConfig {
    singleInput: boolean;
    stickyInput: boolean;
    loadImages: boolean;
    showMenubar: boolean;
    applicationName: string;
    isArchive?: boolean;
    websocket: WebSocketConfig;
    logging: LoggingConfig;
    theme: ThemeConfig;
}