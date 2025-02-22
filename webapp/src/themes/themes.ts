// Import and re-export ThemeName type
import type {BaseTheme, ThemeColors, ThemeName} from '../types/theme';

export type {ThemeName};

// Enhanced logger configuration
const themeLogger = {
    styles: {
        theme: 'color: #4CAF50; font-weight: bold',
        action: 'color: #2196F3; font-weight: bold',
    },
    log(action: string, themeName: string) {
        console.groupCollapsed(
            `%cTheme %c${action} %c${themeName}`,
            this.styles.theme,
            this.styles.action,
            this.styles.theme
        );
        console.groupEnd();
    }
};



interface ThemeSizing {
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

interface ThemeTypography {
    fontFamily: string;
    fontSize: {
        xs: string;
        sm: string;
        md: string;
        lg: string;
        xl: string;
    };
    fontWeight: {
        regular: number;
        medium: number;
        bold: number;
    };
    console: {
        fontFamily: string;
        fontSize: string;
        lineHeight: string;
    };
}

// Use BaseTheme directly instead of ExtendedTheme
type ExtendedTheme = BaseTheme;

const baseTheme: Omit<BaseTheme, 'name' | 'colors'> = {
    _init() {
        themeLogger.log('initialized', 'base');
    },
    shadows: {
        small: '0 1px 3px rgba(0, 0, 0, 0.12)',
        medium: '0 4px 6px rgba(0, 0, 0, 0.15)',
        large: '0 10px 20px rgba(0, 0, 0, 0.20)'
    },
    transitions: {
        default: '0.3s ease',
        fast: '0.15s ease',
        slow: '0.5s ease'
    },
    config: {
        stickyInput: true,
        singleInput: false
    },
    logging: {
        colors: {
            error: '#FF3B30',
            warning: '#FF9500',
            info: '#007AFF',
            debug: '#5856D6',
            success: '#34C759',
            trace: '#8E8E93',
            verbose: '#C7C7CC',
            system: '#48484A',
            critical: '#FF3B30'
        },
        fontSize: {
            normal: '0.9rem',
            large: '1.1rem',
            small: '0.8rem',
            system: '0.85rem',
            critical: '1.2rem'
        },
        padding: {
            message: '0.5rem',
            container: '1rem',
            timestamp: '0.25rem'
        },
        background: {
            error: '#FFE5E5',
            warning: '#FFF3E0',
            info: '#E3F2FD',
            debug: '#F3E5F5',
            success: '#E8F5E9',
            system: '#FAFAFA',
            critical: '#FFEBEE'
        },
        border: {
            radius: '4px',
            style: 'solid',
            width: '1px'
        },
        timestamp: {
            format: 'HH:mm:ss',
            color: '#8E8E93',
            show: true
        },
        display: {
            maxLines: 0,
        }
    },
    sizing: {
        spacing: {
            xs: '0.25rem',
            sm: '0.5rem',
            md: '1rem',
            lg: '1.5rem',
            xl: '2rem',
        },
        borderRadius: {
            sm: '0.25rem',
            md: '0.5rem',
            lg: '1rem',
        },
        console: {
            minHeight: '200px',
            maxHeight: '500px',
            padding: '1rem',
        },
    },
    typography: {
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif",
        monoFontFamily: "'Fira Code', 'Consolas', monospace",
        fontSize: {
            xs: '0.75rem',    // 12px
            sm: '0.875rem',   // 14px
            md: '1rem',       // 16px
            lg: '1.125rem',   // 18px
            xl: '1.25rem',    // 20px
        },
        fontWeight: {
            regular: 400,
            medium: 500,
            bold: 700,
        },
        console: {
            fontFamily: "'Fira Code', Consolas, Monaco, 'Courier New', monospace",
            fontSize: '0.9rem',
            lineHeight: '1.6',
        },
    },
};

export const mainTheme: BaseTheme = {
    name: 'main',
    colors: {
        primary: '#007AFF',
        secondary: '#5856D6',
        background: '#FFFFFF',
        surface: '#F2F2F7',
        text: {
            primary: '#000000',
            secondary: '#6E6E73',
        },
        border: '#C6C6C8',
        error: '#FF3B30',
        success: '#34C759',
        warning: '#FF9500',
        info: '#5856D6',
        secondaryDark: '#4240aa',
        errorDark: '#cc2f26',
        successDark: '#2a9f47',
        critical: '#FF3B30', // Adding critical color (same as error for consistency)
        disabled: '#E5E5EA', // Make sure this is defined in all themes
        primaryDark: '#0056b3', // Add darker shade of primary
        hover: '#2C5282', // Add hover color
    },
    ...baseTheme,
};

export const nightTheme: ExtendedTheme = {
    name: 'night',
    colors: {
        primary: '#0A84FF',
        secondary: '#5E5CE6',
        background: '#000000',
        surface: '#1C1C1E',
        text: {
            primary: '#FFFFFF',
            secondary: '#98989F',
        },
        border: '#38383A',
        error: '#FF453A',
        success: '#32D74B',
        warning: '#FF9F0A',
        info: '#5E5CE6',
        primaryDark: '#0066cc',
        secondaryDark: '#4b49b8',
        errorDark: '#cc372e',
        successDark: '#28ac3c',
        critical: '#FF453A', // Adding critical color
        disabled: '#2C2C2E', // Ensure consistent property definition
    },
    ...baseTheme,
};

export const forestTheme: ExtendedTheme = {
    name: 'forest',
    colors: {
        primary: '#2D6A4F',
        secondary: '#40916C',
        background: '#081C15',
        surface: '#1B4332',
        text: {
            primary: '#D8F3DC',
            secondary: '#95D5B2',
        },
        border: '#2D6A4F',
        error: '#D62828',
        success: '#52B788',
        warning: '#F77F00',
        info: '#4895EF',
        primaryDark: '#1b4332',
        secondaryDark: '#337456',
        errorDark: '#ab2020',
        successDark: '#42926d',
        critical: '#D62828', // Adding critical color
        disabled: '#2D3B35', // Ensure consistent property definition
    },
    ...baseTheme,
};

export const ponyTheme: ExtendedTheme = {
    name: 'pony',
    colors: {
        primary: '#FF69B4',
        secondary: '#FFB6C1',
        background: '#FFF0F5',
        surface: '#FFE4E1',
        text: {
            primary: '#DB7093',
            secondary: '#C71585',
        },
        border: '#FFB6C1',
        error: '#FF1493',
        success: '#FF69B4',
        warning: '#FFB6C1',
        info: '#DB7093',
        primaryDark: '#ff1493',
        critical: '#FF1493', // Adding critical color
        disabled: '#F8E1E7', // Ensure consistent property definition
    },
    ...baseTheme,
};

export const alienTheme: ExtendedTheme = {
    name: 'alien',
    colors: {
        primary: '#39FF14',
        secondary: '#00FF00',
        background: '#0A0A0A',
        surface: '#1A1A1A',
        text: {
            primary: '#39FF14',
            secondary: '#00FF00',
        },
        border: '#008000',
        error: '#FF0000',
        success: '#39FF14',
        warning: '#FFFF00',
        info: '#00FFFF',
        primaryDark: '#2bbb0e',
        critical: '#FF0000', // Adding critical color
        disabled: '#1C1C1C', // Ensure consistent property definition
    },
    ...baseTheme,
};

export const themes = {
    default: {
        ...mainTheme,
        name: 'default' as ThemeName,
        colors: {
            ...mainTheme.colors,
        }
    },
    main: mainTheme,
    night: nightTheme,
    forest: forestTheme,
    pony: ponyTheme,
    alien: alienTheme,
    sunset: {
        name: 'sunset',
        colors: {
            primary: '#FF6B6B',
            secondary: '#FFA07A',
            background: '#2C3E50',
            surface: '#34495E',
            text: {
                primary: '#ECF0F1',
                secondary: '#BDC3C7',
            },
            border: '#95A5A6',
            error: '#E74C3C',
            success: '#2ECC71',
            warning: '#F1C40F',
            info: '#3498DB',
            primaryDark: '#E74C3C',
            disabled: '#7F8C8D',
            critical: '#E74C3C', // Adding critical color
        },
        ...baseTheme,
    },
    ocean: {
        name: 'ocean',
        colors: {
            primary: '#00B4D8',
            secondary: '#48CAE4',
            background: '#03045E',
            surface: '#023E8A',
            text: {
                primary: '#CAF0F8',
                secondary: '#90E0EF',
            },
            border: '#0077B6',
            error: '#FF6B6B',
            success: '#2ECC71',
            warning: '#FFB703',
            info: '#48CAE4',
            primaryDark: '#0096C7',
            disabled: '#415A77',
             hover: '#0077B6',
            critical: '#FF6B6B', // Adding critical color
        },
        ...baseTheme,
    },
    cyberpunk: {
        name: 'cyberpunk',
        colors: {
            primary: '#FF00FF',
            secondary: '#00FFFF',
            background: '#0D0221',
            surface: '#1A1A2E',
            text: {
                primary: '#FF00FF',
                secondary: '#00FFFF',
            },
            border: '#FF00FF',
            error: '#FF0000',
            success: '#00FF00',
            warning: '#FFD700',
            info: '#00FFFF',
            primaryDark: '#CC00CC',
            disabled: '#4A4A4A',
             hover: '#FF69B4',
            critical: '#FF0000', // Adding critical color
        },
        ...baseTheme,
    },
};

// Export a helper function to log theme changes
export const logThemeChange = (from: ThemeName, to: ThemeName) => {
    themeLogger.log('changed', `${from} → ${to}`);
};