// Import and re-export ThemeName type

import type {BaseTheme, ColorThemeName, LayoutTheme, LayoutThemeName} from '../types/theme';

export type { ColorThemeName, LayoutThemeName };

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
        families: {
            primary: "'Outfit', system-ui, -apple-system, BlinkMacSystemFont, sans-serif",
            heading: "'Space Grotesk', system-ui, sans-serif",
            secondary: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif", // Example secondary
            mono: "'IBM Plex Mono', 'Fira Code', monospace",
            display: "'Syne', system-ui, sans-serif",
        },
        monoFontFamily: "'Fira Code', 'Consolas', monospace",
        fontSize: {
            '2xl': '1.75rem', // clamp(2.5rem, 5vw, 3.5rem) - Adjusted base for 2xl
            xs: '0.75rem',

            sm: '0.875rem',

            md: '1rem',

            lg: '1.125rem',

            xl: '1.25rem',

        },
        fontWeight: {
            light: 300,
            regular: 400,
            medium: 500,
            semibold: 600,
            bold: 700,
            extrabold: 800,
        },
        lineHeight: {
            tight: '1.15',
            normal: '1.65',
            relaxed: '1.85',
        },
        letterSpacing: {
            tight: '-0.04em',
            normal: '-0.02em',
            wide: '0.04em',
            wider: '0.08em',
        },
        console: {
            fontFamily: "'Fira Code', Consolas, Monaco, 'Courier New', monospace",
            fontSize: '0.9rem',
            lineHeight: '1.6',
        },
    },
};

export const mainTheme: BaseTheme = {
    name: 'main' as ColorThemeName,
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
        info: '#007AFF', // Adjusted to be same as primary for this theme
        primaryDark: '#0056b3',
        secondaryDark: '#4240aa', // Darker purple
        errorDark: '#D9362B', // Darker red
        successDark: '#28A745', // Darker green
        critical: '#FF3B30',

        disabled: '#E5E5EA',


        hover: '#0056b3', // Using primaryDark for hover

    },
    ...baseTheme,
};

export const nightTheme: ExtendedTheme = {
    name: 'night' as ColorThemeName,
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
        primaryDark: '#0063cc', // Darker blue
        secondaryDark: '#4b49b8', // Darker purple
        errorDark: '#E53E30', // Darker red
        successDark: '#27C13F', // Darker green
        critical: '#FF453A',

        disabled: '#2C2C2E',
        hover: '#0063cc',

    },
    ...baseTheme,
};

export const forestTheme: ExtendedTheme = {
    name: 'forest' as ColorThemeName,
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
        primaryDark: '#1E4D38', // Darker green
        secondaryDark: '#2F6D50', // Darker secondary green
        errorDark: '#B82323', // Darker red
        successDark: '#3E8E6A', // Darker success green
        critical: '#D62828',

        disabled: '#2D3B35',
        hover: '#1E4D38',

    },
    ...baseTheme,
};

export const ponyTheme: ExtendedTheme = {
    name: 'pony' as ColorThemeName,
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
        primaryDark: '#E55EA4', // Darker pink
        secondaryDark: '#E5A0AD', // Darker light pink
        errorDark: '#D9127F', // Darker deep pink
        successDark: '#E55EA4', // Darker pink (same as primaryDark for this theme)
        critical: '#FF1493',

        disabled: '#F8E1E7',
        hover: '#E55EA4',

    },
    ...baseTheme,
};

export const alienTheme: ExtendedTheme = {
    name: 'alien' as ColorThemeName,
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
        primaryDark: '#2ECF0F', // Darker green
        secondaryDark: '#00CF00', // Darker bright green
        errorDark: '#CF0000', // Darker red
        successDark: '#2ECF0F', // Darker success green
        critical: '#FF0000',

        disabled: '#1C1C1C',
        hover: '#2ECF0F',

    },
    ...baseTheme,
};

export const themes = {
    default: {
        ...mainTheme,
        name: 'default' as ColorThemeName,
        colors: {
            ...mainTheme.colors,
        }
    },
    main: mainTheme,
    night: nightTheme,
    forest: forestTheme,
    pony: ponyTheme,
    alien: alienTheme,
    // New themes will be added below
    synthwave: {} as ExtendedTheme, // Placeholder
    paper: {} as ExtendedTheme, // Placeholder
    sunset: {
        name: 'sunset' as ColorThemeName,
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
            primaryDark: '#D65B5B', // Darker red
            secondaryDark: '#E08A6A', // Darker light red
            errorDark: '#C0392B', // Darker error red
            successDark: '#27AE60', // Darker green
            disabled: '#7F8C8D',
            critical: '#E74C3C',
            hover: '#D65B5B',

        },
        ...baseTheme,
    },
    ocean: {
        name: 'ocean' as ColorThemeName,
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
            primaryDark: '#0093C0', // Darker blue
            secondaryDark: '#3EAFC7', // Darker light blue
            errorDark: '#D65B5B', // Darker red
            successDark: '#27AE60', // Darker green
            disabled: '#415A77',
            hover: '#0077B6',
            critical: '#FF6B6B',

        },
        ...baseTheme,
    },
    cyberpunk: {
        name: 'cyberpunk' as ColorThemeName,
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
            primaryDark: '#D100D1', // Darker magenta
            secondaryDark: '#00D1D1', // Darker cyan
            errorDark: '#D10000', // Darker red
            successDark: '#00D100', // Darker green
            disabled: '#4A4A4A',
            hover: '#FF69B4',
            critical: '#FF0000',

        },
        ...baseTheme,
    },
};
// Add new themes to the export
themes.synthwave = {
    name: 'synthwave' as ColorThemeName,
    colors: {
        primary: '#FF00FF', // Magenta
        secondary: '#00FFFF', // Cyan
        background: '#1A1A2E', // Dark Indigo
        surface: '#2A2A3E', // Slightly Lighter Indigo
        text: {
            primary: '#00FFFF', // Cyan
            secondary: '#FF00FF', // Magenta
        },
        border: '#FF00FF', // Magenta
        error: '#FF3366', // Hot Pink
        success: '#00FF7F', // Spring Green
        warning: '#FFFF66', // Canary Yellow
        info: '#3399FF', // Bright Blue
        primaryDark: '#CC00CC',
        secondaryDark: '#00CCCC',
        errorDark: '#D92B58',
        successDark: '#00CC66',
        critical: '#FF3366',
        disabled: '#4A4A5E',
        hover: '#CC00CC',
    },
    ...baseTheme,
};
themes.paper = {
    name: 'paper' as ColorThemeName,
    colors: {
        primary: '#5D737E', // Desaturated Blue/Grey
        secondary: '#8C7A6B', // Muted Brown
        background: '#FDFBF7', // Off-white, parchment like
        surface: '#F5F2EB', // Slightly darker off-white
        text: {
            primary: '#4A4A4A', // Dark Grey
            secondary: '#7B7B7B', // Medium Grey
        },
        border: '#DCDCDC', // Light Grey
        error: '#C94E4E', // Muted Red
        success: '#6A994E', // Muted Green
        warning: '#D4A26A', // Muted Orange
        info: '#7E9CB9', // Muted Blue
        primaryDark: '#4A5C66',
        secondaryDark: '#706053',
        errorDark: '#A84040',
        successDark: '#537A3E',
        critical: '#C94E4E',
        disabled: '#E0E0E0',
        hover: '#4A5C66',
    },
    ...baseTheme,
};


export const defaultLayoutTheme: LayoutTheme = {
    name: 'default',
    // Base layout settings inherited from baseTheme
    sizing: baseTheme.sizing,
    typography: baseTheme.typography,
};

export const compactLayoutTheme: LayoutTheme = {
    name: 'compact',
    // Inherit base sizing and typography, then override for compactness
    sizing: {
        ...baseTheme.sizing,
        spacing: {
            xs: '0.125rem',
            sm: '0.25rem',
            md: '0.5rem',
            lg: '1rem',
            xl: '1.5rem',
        },
    },
    typography: {
        ...baseTheme.typography,
        fontSize: {
            xs: '0.65rem',
            sm: '0.75rem',
            md: '0.875rem',
            lg: '1rem',
            xl: '1.125rem',
            '2xl': '1.5rem',
        },
        lineHeight: {
            tight: '1.1',
            normal: '1.5',
            relaxed: '1.7',
        }
    }
};

export const spaciousLayoutTheme: LayoutTheme = {
    name: 'spacious',
    sizing: {
        ...baseTheme.sizing,
        spacing: {
            xs: '0.5rem',
            sm: '0.75rem',
            md: '1.25rem',
            lg: '2rem',
            xl: '2.5rem',
        },
    },
    typography: {
        ...baseTheme.typography,
        fontSize: {
            xs: '0.875rem',
            sm: '1rem',
            md: '1.125rem',
            lg: '1.375rem',
            xl: '1.625rem',
            '2xl': '2rem',
        },
    }
};
export const ultraCompactLayoutTheme: LayoutTheme = {
    name: 'ultra-compact',
    sizing: {
        ...baseTheme.sizing,
        spacing: {
            xs: '0.0625rem', // 1px
            sm: '0.125rem',  // 2px
            md: '0.25rem',   // 4px
            lg: '0.5rem',    // 8px
            xl: '0.75rem',   // 12px
        },
    },
    typography: {
        ...baseTheme.typography,
        fontSize: {
            xs: '0.6rem',
            sm: '0.7rem',
            md: '0.8rem',
            lg: '0.9rem',
            xl: '1rem',
            '2xl': '1.25rem',
        },
        lineHeight: {
            tight: '1.0',
            normal: '1.3',
            relaxed: '1.5',
        }
    }
};
export const contentFocusedLayoutTheme: LayoutTheme = {
    name: 'content-focused',
    sizing: {
        ...baseTheme.sizing,
        spacing: { // Slightly more generous than default for readability
            xs: '0.3rem',
            sm: '0.6rem',
            md: '1.1rem',
            lg: '1.6rem',
            xl: '2.2rem',
        },
        console: {
            ...baseTheme.sizing.console,
            maxHeight: '600px', // Allow more console content
        }
    },
    typography: {
        ...baseTheme.typography,
        fontSize: { // Slightly larger base for readability
            xs: '0.8rem',
            sm: '0.9rem',
            md: '1.05rem',
            lg: '1.2rem',
            xl: '1.35rem',
            '2xl': '1.85rem',
        },
        lineHeight: { // More generous line height for readability
            tight: '1.2',
            normal: '1.7',
            relaxed: '1.9',
        }
    }
};


export const layoutThemes: Record<LayoutThemeName, LayoutTheme> = {
    default: defaultLayoutTheme,
    compact: compactLayoutTheme,
    spacious: spaciousLayoutTheme,
    'ultra-compact': ultraCompactLayoutTheme,
    'content-focused': contentFocusedLayoutTheme,
};


export const logThemeChange = (from: ColorThemeName, to: ColorThemeName) => {
    themeLogger.log('changed', `${from} → ${to}`);
};