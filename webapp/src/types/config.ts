import {ThemeName} from './theme';
import {ConsoleStyle} from "../types";

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
    persistLogs: boolean;
    console: ConsoleConfig;
}

export interface ThemeConfig {
    current: ThemeName;
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