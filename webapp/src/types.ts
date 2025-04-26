// Define theme names
// Re-export ThemeName from theme.ts to maintain single source of truth
export type { ThemeName } from './types/theme';

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