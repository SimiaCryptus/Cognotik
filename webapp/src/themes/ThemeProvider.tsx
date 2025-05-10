import React, {useEffect, useRef} from 'react';
import {ThemeProvider as StyledThemeProvider} from 'styled-components';
import {useSelector} from 'react-redux';
import {RootState} from '../store';
import {logThemeChange, ColorThemeName, themes, layoutThemes, LayoutThemeName, defaultLayoutTheme} from './themes';
import { initNewCollapsibleElements } from '../utils/tabHandling'; // Import the function
import Prism from 'prismjs';
import {GlobalStyles} from "../styles/GlobalStyles";

const loadFonts = () => {
    const fontUrls = [
        'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap',
        'https://fonts.googleapis.com/css2?family=Poppins:wght@500;600;700;800&family=Raleway:wght@600;700;800&display=swap',
        'https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600&display=swap',
        'https://fonts.googleapis.com/css2?family=Montserrat:wght@600;700;800&display=swap'
    ];
    fontUrls.forEach(url => {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = url;
        document.head.appendChild(link);
    });
};

interface ThemeProviderProps {
    children: React.ReactNode;
}

const LOG_PREFIX = '[ThemeProvider]';
const DEFAULT_COLOR_THEME: ColorThemeName = 'main';
const prismThemes: Record<ColorThemeName, string> = {
    main: 'prism',
    night: 'prism-dark',
    forest: 'prism-okaidia',
    pony: 'prism-twilight',
    alien: 'prism-tomorrow',
    sunset: 'prism-twilight',
    ocean: 'prism-okaidia',
    cyberpunk: 'prism-tomorrow',
    default: 'prism',
    synthwave: 'prism-tomorrow',
    paper: 'prism'
};

const loadPrismTheme = async (themeName: ColorThemeName) => {
    const prismTheme = prismThemes[themeName] || 'prism';
    try {
        await import(`prismjs/themes/${prismTheme}.css`);

    } catch (error) {
        console.error(`${LOG_PREFIX} Failed to load Prism theme: ${prismTheme}. This will affect code highlighting.`, error);
    }
};

export const ThemeProvider: React.FC<ThemeProviderProps> = ({children}) => {
    const currentColorThemeName = useSelector((state: RootState) => state.ui.theme);
    const currentLayoutThemeName = useSelector((state: RootState) => state.ui.layoutTheme || 'default' as LayoutThemeName);
    const isInitialMount = useRef(true);
    const previousThemeRef = useRef({ color: currentColorThemeName, layout: currentLayoutThemeName });
    const styleElRef = useRef<HTMLStyleElement | null>(null);

    useEffect(() => {
        loadFonts();
    }, []);

    useEffect(() => {
        const selectedColorTheme = themes[currentColorThemeName] || themes[DEFAULT_COLOR_THEME];
        const selectedLayoutTheme = layoutThemes[currentLayoutThemeName] || defaultLayoutTheme;

        if (!themes[currentColorThemeName]) {
            console.error(`${LOG_PREFIX} Color Theme "${currentColorThemeName}" not found. Falling back to ${DEFAULT_COLOR_THEME}.`);
        }
        if (!layoutThemes[currentLayoutThemeName]) {
            console.error(`${LOG_PREFIX} Layout Theme "${currentLayoutThemeName}" not found. Falling back to default layout.`);
        }


        if (!styleElRef.current) {
            styleElRef.current = document.createElement('style');
            document.head.appendChild(styleElRef.current);
        }
        const styleEl = styleElRef.current;

        requestAnimationFrame(() => {
            if (!styleEl) return;
            styleEl.textContent = `
            :root {
                /* Color Theme Variables */
                --theme-background: ${selectedColorTheme.colors.background};
                --theme-text: ${selectedColorTheme.colors.text.primary};
                --theme-text-secondary-color: ${selectedColorTheme.colors.text.secondary};
                --theme-surface: ${selectedColorTheme.colors.surface};
                --theme-primary: ${selectedColorTheme.colors.primary};
                --theme-secondary: ${selectedColorTheme.colors.secondary};
                --theme-warning: ${selectedColorTheme.colors.warning};
                --theme-success: ${selectedColorTheme.colors.success};
                --theme-info: ${selectedColorTheme.colors.info};
                --theme-border: ${selectedColorTheme.colors.border};
                --theme-disabled: ${selectedColorTheme.colors.disabled};
                --theme-hover: ${selectedColorTheme.colors.hover};
                --theme-primary-dark: ${selectedColorTheme.colors.primaryDark || selectedColorTheme.colors.primary};
                --theme-shadow-small: ${selectedColorTheme.shadows.small};
                --theme-shadow-medium: ${selectedColorTheme.shadows.medium};
                --theme-shadow-large: ${selectedColorTheme.shadows.large};
                --theme-text-on-primary: #ffffff; /* Assuming white, make dynamic if needed */
                --theme-text-on-secondary: #ffffff; /* Assuming white, make dynamic if needed */
                --theme-text-on-error: #ffffff; /* Assuming white, make dynamic if needed */

                /* Layout Theme Variables - Typography */





                --font-primary: ${selectedLayoutTheme.typography.families.primary};
                --font-heading: ${selectedLayoutTheme.typography.families.heading};
                --font-mono: ${selectedLayoutTheme.typography.families.mono};
                --font-display: ${selectedLayoutTheme.typography.families.display};

                --font-weight-light: ${selectedLayoutTheme.typography.fontWeight.light};
                --font-weight-regular: ${selectedLayoutTheme.typography.fontWeight.regular};
                --font-weight-medium: ${selectedLayoutTheme.typography.fontWeight.medium};
                --font-weight-semibold: ${selectedLayoutTheme.typography.fontWeight.semibold};
                --font-weight-bold: ${selectedLayoutTheme.typography.fontWeight.bold};
                ${selectedLayoutTheme.typography.fontWeight.extrabold ? `--font-weight-extrabold: ${selectedLayoutTheme.typography.fontWeight.extrabold};` : ''}

                --font-size-xs: ${selectedLayoutTheme.typography.fontSize.xs};
                --font-size-sm: ${selectedLayoutTheme.typography.fontSize.sm};
                --font-size-md: ${selectedLayoutTheme.typography.fontSize.md};
                --font-size-lg: ${selectedLayoutTheme.typography.fontSize.lg};
                --font-size-xl: ${selectedLayoutTheme.typography.fontSize.xl};
                ${selectedLayoutTheme.typography.fontSize['2xl'] ? `--font-size-2xl: ${selectedLayoutTheme.typography.fontSize['2xl']};` : ''}

                --line-height-tight: ${selectedLayoutTheme.typography.lineHeight.tight};
                --line-height-normal: ${selectedLayoutTheme.typography.lineHeight.normal};
                --line-height-relaxed: ${selectedLayoutTheme.typography.lineHeight.relaxed};

                --letter-spacing-tight: ${selectedLayoutTheme.typography.letterSpacing.tight};
                --letter-spacing-normal: ${selectedLayoutTheme.typography.letterSpacing.normal};
                --letter-spacing-wide: ${selectedLayoutTheme.typography.letterSpacing.wide};
                ${selectedLayoutTheme.typography.letterSpacing.wider ? `--letter-spacing-wider: ${selectedLayoutTheme.typography.letterSpacing.wider};` : ''}

                /* Layout Theme Variables - Sizing */
                --spacing-xs: ${selectedLayoutTheme.sizing.spacing.xs};
                --spacing-sm: ${selectedLayoutTheme.sizing.spacing.sm};
                --spacing-md: ${selectedLayoutTheme.sizing.spacing.md};
                --spacing-lg: ${selectedLayoutTheme.sizing.spacing.lg};
                --spacing-xl: ${selectedLayoutTheme.sizing.spacing.xl};
                --border-radius-sm: ${selectedLayoutTheme.sizing.borderRadius.sm};
                --border-radius-md: ${selectedLayoutTheme.sizing.borderRadius.md};
                --border-radius-lg: ${selectedLayoutTheme.sizing.borderRadius.lg};
                --console-max-height: ${selectedLayoutTheme.sizing.console.maxHeight};
            }

            /* Theme-specific message content styles (already good) */
            .message-content {
                color: var(--theme-text);
                background: var(--theme-background);
            }
            .message-content pre,
            .message-content code {
                background: var(--theme-surface);
                border: 1px solid var(--theme-border);
                font-family: var(--font-mono); /* Use CSS var for code font */
            }
            `;
        });

        // Manage body class for broad styling hooks
        document.body.className = `theme-color-${currentColorThemeName} theme-layout-${currentLayoutThemeName}`;

        if (isInitialMount.current) {
            isInitialMount.current = false;
        } else {
            if (previousThemeRef.current.color !== currentColorThemeName) {
                logThemeChange(previousThemeRef.current.color, currentColorThemeName);
            }
            if (previousThemeRef.current.layout !== currentLayoutThemeName) {
                // Could add specific logging for layout theme changes if desired
                console.log(`${LOG_PREFIX} Layout theme changed from ${previousThemeRef.current.layout} to ${currentLayoutThemeName}`);
            }
        }
        previousThemeRef.current = { color: currentColorThemeName, layout: currentLayoutThemeName };


        loadPrismTheme(currentColorThemeName).then(() => {
            // Prism highlighting logic (ensure it uses the new CSS vars or is re-triggered)
            return;
        })





        loadPrismTheme(currentColorThemeName).then(() => {
            requestAnimationFrame(() => {
                const codeBlocks = document.querySelectorAll('pre code');
                const updates: (() => void)[] = [];
                codeBlocks.forEach(block => {
                    updates.push(() => {
                        // These direct style manipulations might be less necessary if Prism themes adapt to CSS vars
                        (block as HTMLElement).classList.add('theme-transition');
                    });
                });

                requestAnimationFrame(() => {
                    updates.forEach(update => update());
                    Prism.highlightAll();
                });
            });
            // Re-initialize collapsible elements after theme change and potential DOM updates
            // Use requestAnimationFrame to ensure it runs after rendering updates
            requestAnimationFrame(initNewCollapsibleElements);
        });
        return () => {
            if (styleElRef.current) {
                styleElRef.current.remove();
                styleElRef.current = null;
            }
        };

    }, [currentColorThemeName, currentLayoutThemeName]);
    // The theme passed to StyledThemeProvider should be the merged one,
    // so styled-components have access to all properties if needed directly.
    // However, GlobalStyles will primarily use CSS variables for layout/typography.
    const finalMergedTheme = {
        ...(themes[currentColorThemeName] || themes.main), // Color theme properties
        sizing: (layoutThemes[currentLayoutThemeName] || defaultLayoutTheme).sizing, // Layout sizing
        typography: (layoutThemes[currentLayoutThemeName] || defaultLayoutTheme).typography, // Layout typography
        name: `${currentColorThemeName}-${currentLayoutThemeName}`, // Composite name
    };


    return (
        <StyledThemeProvider theme={finalMergedTheme}>
            <GlobalStyles theme={finalMergedTheme}/>{children}
        </StyledThemeProvider>);
};

export default ThemeProvider;