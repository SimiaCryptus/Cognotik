// Core theme types
export type ColorThemeName = 'default' | 'main' | 'night' | 'forest' | 'pony' | 'alien' | 'sunset' | 'ocean' | 'cyberpunk' | 'synthwave' | 'paper';
export type LayoutThemeName = 'default' | 'compact' | 'spacious' | 'ultra-compact' | 'content-focused';
export type ThemeName = ColorThemeName; // This might need to be re-evaluated or used specifically for color theme contexts.

export interface ThemeColors {
    primary: string;
    secondary: string;
    background: string;
    surface: string;
    text: {
        primary: string;
        secondary: string;
    };
    border: string;
    error: string;
    success: string;
    warning: string;
    info: string;
    disabled: string;
    critical: string;
    primaryDark?: string;
    secondaryDark?: string;
    errorDark?: string;
    successDark?: string;
    hover: string;
}

export interface ThemeSizing {
    spacing: {
        xs: string;
        sm: string;
        md: string;
        lg: string;
        xl: string;
    };
    borderRadius: {
        sm: string;
        md: string;
        lg: string;
    };
    console: {
        minHeight: string;
        maxHeight: string;
        padding: string;
    };
}

export interface ThemeTypography {
    fontFamily: string;
    families: {
        primary: string;
        secondary?: string; // Optional secondary body font
        heading: string;
        mono: string;
        display: string;
    };
    fontSize: {
        xs: string;
        sm: string;
        md: string;
        lg: string;
        xl: string;
        '2xl'?: string; // Added for larger heading
    };
    fontWeight: {
        light: number;
        regular: number;
        medium: number;
        semibold: number;
        bold: number;
        extrabold?: number;
    };
    lineHeight: {
        tight: string;
        normal: string;
        relaxed: string;
    };
    letterSpacing: {
        tight: string;
        normal: string;
        wide: string;
        wider?: string;
    };
    console: {
        fontFamily: string;
        fontSize: string;
        lineHeight: string;
    };
    monoFontFamily?: string; // Retained for specific mono font usage if needed outside families.mono
}

export interface ThemeLogging {
    colors: {
        error: string;
        warning: string;
        info: string;
        debug: string;
        success: string;
        trace: string;
        verbose: string;
        system: string;
        critical: string;

    };
    fontSize: {
        normal: string;
        large: string;
        small: string;
        system: string;
        critical: string;

    };
    padding: {
        message: string;
        container: string;
        timestamp: string;
    };
    background: {
        error: string;
        warning: string;
        info: string;
        debug: string;
        success: string;
        system: string;
        critical: string;

    };
    border: {
        radius: string;
        style: string;
        width: string;
    };
    timestamp: {
        format: string;
        color: string;
        show: boolean;
    };
    display: {
        maxLines: number;
    };
}

export interface ThemeConfig {
    stickyInput: boolean;
    singleInput: boolean;
}

export interface BaseTheme {
    name: ThemeName | LayoutThemeName; // Can be a color theme name or layout theme name
    colors: ThemeColors;
    sizing: ThemeSizing;
    typography: ThemeTypography;
    logging: ThemeLogging;
    config: ThemeConfig;
    shadows: {
        small: string;
        medium: string;
        large: string;
    };
    transitions: {
        default: string;
        fast: string;
        slow: string;
    };
    _init?: () => void;
}

export interface LayoutTheme {
    name: LayoutThemeName;
    sizing: ThemeSizing;
    typography: ThemeTypography;
}