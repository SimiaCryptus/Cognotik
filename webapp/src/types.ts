// Define theme names

export type {ThemeName} from './types/theme';

export interface ConsoleStyle {

    isCritical?: boolean;
    color?: string;
    background?: string;
    bold?: boolean;
    italic?: boolean;
    underline?: boolean;
}

export interface UserInfo {
    name: string;
    isAuthenticated: boolean;
    preferences?: Record<string, unknown>;
}