import 'styled-components';
import type { ThemeColors, ThemeSizing, ThemeTypography, ThemeLogging, ThemeConfig as BaseThemeConfig } from './theme';

declare module 'styled-components' {
    export interface DefaultTheme {
        sizing: ThemeSizing;
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
        typography: ThemeTypography;
        colors: ThemeColors;
        name: string;
        activeTab?: string;
        config: BaseThemeConfig;
        logging: ThemeLogging;
        // _init?: () => void; // Optional, if needed for direct access in styled-components
    }
}