import React, {useEffect, useRef} from 'react';
import {ThemeProvider as StyledThemeProvider} from 'styled-components';
import {useSelector} from 'react-redux';
import {RootState} from '../store';
import {logThemeChange, ThemeName, themes} from './themes';
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
const DEFAULT_THEME: ThemeName = 'main';
const prismThemes: Record<ThemeName, string> = {
    main: 'prism',
    night: 'prism-dark',
    forest: 'prism-okaidia',
    pony: 'prism-twilight',
    alien: 'prism-tomorrow',
    sunset: 'prism-twilight',
    ocean: 'prism-okaidia',
    cyberpunk: 'prism-tomorrow',
    default: 'prism',
};

const loadPrismTheme = async (themeName: ThemeName) => {
    const prismTheme = prismThemes[themeName] || 'prism';
    try {
        await import(`prismjs/themes/${prismTheme}.css`);

    } catch (error) {
        console.error(`${LOG_PREFIX} Failed to load Prism theme: ${prismTheme}. This will affect code highlighting.`, error);
    }
};

export const ThemeProvider: React.FC<ThemeProviderProps> = ({children}) => {
    const currentTheme = useSelector((state: RootState) => state.ui.theme);
    const isInitialMount = useRef(true);
    const previousTheme = useRef(currentTheme);
    const styleElRef = useRef<HTMLStyleElement | null>(null);

    useEffect(() => {
        loadFonts();
    }, []);

    useEffect(() => {
        if (!themes[currentTheme]) {
            console.error(`${LOG_PREFIX} Theme "${currentTheme}" not found. Falling back to ${DEFAULT_THEME} theme. This indicates a potential application configuration issue.`);
            return;
        }

        if (!styleElRef.current) {
            styleElRef.current = document.createElement('style');
            document.head.appendChild(styleElRef.current);
        }
        const styleEl = styleElRef.current;
        requestAnimationFrame(() => {
            styleEl.textContent = `
        :root {
            --theme-background: ${themes[currentTheme].colors.background};
            --theme-text: ${themes[currentTheme].colors.text.primary};
            --theme-surface: ${themes[currentTheme].colors.surface};
            --theme-primary: ${themes[currentTheme].colors.primary};
            --theme-secondary: ${themes[currentTheme].colors.secondary};
            --theme-error: ${themes[currentTheme].colors.error};
            --theme-warning: ${themes[currentTheme].colors.warning};
            --theme-border: ${themes[currentTheme].colors.border};
            --theme-shadow-medium: ${themes[currentTheme].shadows.medium};
            --theme-shadow-large: ${themes[currentTheme].shadows.large};
            --theme-text-on-primary: #ffffff;
            --theme-text-on-secondary: #ffffff;
            --theme-text-on-error: #ffffff;
        }
        /* Theme-specific message content styles */
        .message-content {
            color: var(--theme-text);
            background: var(--theme-background);
        }
        .message-content pre,
        .message-content code {
            background: var(--theme-surface);
            border: 1px solid var(--theme-border);
            font-family: var(--theme-code-font);
        }
        `;
        });

        const contentElements = document.querySelectorAll('.message-content');
        contentElements.forEach(content => {
            content.classList.add('theme-transition');
        });
        if (isInitialMount.current) {
            isInitialMount.current = false;
        } else {
            logThemeChange(previousTheme.current, currentTheme);
        }

        document.body.className = `theme-${currentTheme}`;

        styleEl.textContent = `
        .message-content.theme-${currentTheme} {
            --theme-background: ${themes[currentTheme].colors.background};
            --theme-text: ${themes[currentTheme].colors.text.primary};
            --theme-surface: ${themes[currentTheme].colors.surface};
            --theme-primary: ${themes[currentTheme].colors.primary};
        }
        `;
        document.body.classList.add('theme-transition');
        const bodyElements = document.querySelectorAll('.message-body');
        bodyElements.forEach(content => {
            content.classList.add('theme-transition');
        });

        loadPrismTheme(currentTheme).then(() => {
            requestAnimationFrame(() => {
                const codeBlocks = document.querySelectorAll('pre code');
                const updates: (() => void)[] = [];
                codeBlocks.forEach(block => {
                    updates.push(() => {
                        (block as HTMLElement).style.setProperty('--theme-background', themes[currentTheme].colors.background);
                        (block as HTMLElement).style.setProperty('--theme-text', themes[currentTheme].colors.text.primary);
                        (block as HTMLElement).classList.add('theme-transition');
                    });
                });

                requestAnimationFrame(() => {
                    updates.forEach(update => update());
                    Prism.highlightAll();
                });
            });
        });
        return () => {
            if (styleElRef.current) {
                styleElRef.current.remove();
                styleElRef.current = null;
            }
        };
    }, [currentTheme]);

    const theme = themes[currentTheme] || themes.main;


    return (
        <StyledThemeProvider theme={theme}>
            <GlobalStyles theme={theme}/>{children}
        </StyledThemeProvider>);
};

export default ThemeProvider;