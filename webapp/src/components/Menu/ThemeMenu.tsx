import React from 'react';
import styled from 'styled-components';
import {useTheme} from '../../hooks/useTheme';
import {themes} from '../../themes/themes';
import {useDispatch} from 'react-redux';
import {setModalContent, showModal} from '../../store/slices/uiSlice';

const LOG_PREFIX = '[ThemeMenu Component]';
const logWithPrefix = (message: string, ...args: any[]) => {
    console.log(`${LOG_PREFIX} ${message}`, ...args);
};
const logDebug = (message: string, ...args: any[]) => {
    if (process.env.NODE_ENV === 'development') {
        logWithPrefix(`[DEBUG] ${message}`, ...args);
    }
};

const ThemeMenuContainer = styled.div`
    position: relative;
    display: inline-block;
    padding: 0.5rem;
`;

const ThemeButton = styled.button`
    padding: ${({theme}) => theme.sizing.spacing.sm};
    color: ${({theme}) => theme.colors.text.primary};
    background: ${({theme}) => `${theme.colors.surface}90`};
    border: 0px solid ${({theme}) => `${theme.colors.border}40`};
    border-radius: ${({theme}) => theme.sizing.borderRadius.sm};
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;
    overflow: hidden;
    backdrop-filter: blur(8px);
    font-weight: ${({theme}) => theme.typography.fontWeight.medium};
    min-width: 140px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    font-size: ${({theme}) => theme.typography.fontSize.sm};
    letter-spacing: 0.5px;
    text-transform: capitalize;

    &:hover {
        background: ${({theme}) => `linear-gradient(
            135deg,
            ${theme.colors.primary},
            ${theme.colors.secondary}
        )`};
        color: ${({theme}) => theme.colors.background};
        transform: translateY(-2px);
        box-shadow:

            0 4px 16px ${({theme}) => `${theme.colors.primary}40`},
            0 0 0 1px ${({theme}) => `${theme.colors.primary}40`};
        /* Enhanced hover effect */
        &::before {
            content: '';
            position: absolute;
            top: -50%;
            left: -50%;
            width: 200%;
            height: 200%;
            background: radial-gradient(
                circle,
                rgba(255,255,255,0.2) 0%,
                transparent 70%
            );
            transform: rotate(45deg);
            animation: shimmer 2s linear infinite;
        }
        @keyframes shimmer {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
        }
        &:after {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: linear-gradient(rgba(255,255,255,0.2), transparent);
            pointer-events: none;
        }
    }
    &:active {
        transform: translateY(0);
    }
    &:disabled {
        background: ${({theme}) => theme.colors.disabled};
        cursor: not-allowed;
    }
`;

const ThemeList = styled.div`
    position: absolute;
    top: 100%;
    right: 0;
    background: ${({theme}) => `${theme.colors.surface}f0`};
    border: 1px solid ${({theme}) => theme.colors.border};
    border-radius: ${({theme}) => theme.sizing.borderRadius.sm};
    padding: ${({theme}) => theme.sizing.spacing.xs};
    z-index: 10;
    min-width: 200px;
    box-shadow: 0 4px 16px ${({theme}) => `${theme.colors.primary}20`},
    0 0 0 1px ${({theme}) => `${theme.colors.border}40`};
    backdrop-filter: blur(8px);
    transform-origin: top;
    animation: slideIn 0.2s ease-out;
    /* Improved glass effect */
    background: ${({theme}) => `linear-gradient(
        to bottom,
        ${theme.colors.surface}f8,
        ${theme.colors.surface}e8
    )`};
    /* Add glass effect */

    &::before {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        backdrop-filter: blur(8px);
        z-index: -1;
    }

    @keyframes slideIn {
        from {
            opacity: 0;
            transform: translateY(-10px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
`;

const ThemeOption = styled.button`
    width: 100%;
    padding: ${({theme}) => theme.sizing.spacing.sm};
    text-align: left;
    color: ${({theme}) => theme.colors.text.primary};
    background: none;
    border: none;
    border-radius: ${({theme}) => theme.sizing.borderRadius.sm};
    cursor: pointer;
    outline: none;

    &:hover {
        background: ${({theme}) => theme.colors.primary};
        color: ${({theme}) => theme.colors.background};
    }
    &:focus-visible {
        box-shadow: 0 0 0 2px ${({theme}) => theme.colors.primary};
    }
`;

export const ThemeMenu: React.FC = () => {
    const [currentTheme, setTheme] = useTheme();
    const [isOpen, setIsOpen] = React.useState(false);
    const [isLoading, setIsLoading] = React.useState(false);
    const menuRef = React.useRef<HTMLDivElement>(null);
    const firstOptionRef = React.useRef<HTMLButtonElement>(null);
    const dispatch = useDispatch();

    React.useEffect(() => {
        if (isOpen && firstOptionRef.current) {
            firstOptionRef.current.focus();
        }
    }, [isOpen]);

    React.useEffect(() => {
        const handleEscapeKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && isOpen) {
                setIsOpen(false);
            }
        };
        if (isOpen) {
            document.addEventListener('keydown', handleEscapeKey);
        }
        return () => {
            document.removeEventListener('keydown', handleEscapeKey);
        };
    }, [isOpen]);

    React.useEffect(() => {
        const handleKeyboardShortcut = (event: KeyboardEvent) => {

            const isMac = /Mac|iPod|iPhone|iPad/.test(navigator.platform);
            const isShortcutTriggered = isMac
                ? (event.ctrlKey && event.key.toLowerCase() === 't')
                : (event.altKey && event.key.toLowerCase() === 't');
            if (isShortcutTriggered) {
                event.preventDefault();
                const themeContent = `
                <div>
                    ${Object.keys(themes).map(themeName => `
                        <button

                            onclick="window.dispatchEvent(new CustomEvent('themeChange', {detail: '${themeName}'}))"
                            style="display: block; width: 100%; margin: 8px 0; padding: 8px; text-align: left; ${themeName === currentTheme ? 'background: #eee;' : ''}"
                        >
                            ${themeName}
                        </button>
                    `).join('')}
                </div>
            `;
                dispatch(showModal('Theme Selection'));
                dispatch(setModalContent(themeContent));
                const shortcutKey = isMac ? 'Ctrl+T' : 'Alt+T';
                logDebug(`Theme modal opened via keyboard shortcut (${shortcutKey})`);
            }
        };
        document.addEventListener('keydown', handleKeyboardShortcut);
        return () => {
            document.removeEventListener('keydown', handleKeyboardShortcut);
        };
    }, [currentTheme, dispatch]);
    React.useEffect(() => {
        const handleThemeChangeEvent = (event: CustomEvent<string>) => {
            handleThemeChange(event.detail as keyof typeof themes);
        };
        window.addEventListener('themeChange', handleThemeChangeEvent as EventListener);
        return () => {
            window.removeEventListener('themeChange', handleThemeChangeEvent as EventListener);
        };
    }, []);
    React.useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isOpen]);

    React.useEffect(() => {
        logDebug('Theme changed:', {
            theme: currentTheme,
            timestamp: new Date().toISOString()
        });
    }, [currentTheme]);

    const handleThemeChange = async (themeName: keyof typeof themes) => {
        logDebug('Theme change initiated', {
            from: currentTheme,
            to: themeName,
            timestamp: new Date().toISOString(),
            isDefaultTheme: themeName === 'main'

        });

        setIsLoading(true);
        setIsOpen(false);
        setTheme(themeName);

        await new Promise(resolve => setTimeout(resolve, 300));
        setIsLoading(false);
        logDebug('Theme change completed', {
            theme: themeName,
            loadTime: '300ms',
            timestamp: new Date().toISOString()
        });
    };

    const handleMenuToggle = () => {
        logDebug('Menu state changing', {
            action: !isOpen ? 'opening' : 'closing',
            currentTheme,
            timestamp: new Date().toISOString()
        });
        setIsOpen(!isOpen);
    };

    return (
        <ThemeMenuContainer ref={menuRef}>
            <ThemeButton
                onClick={handleMenuToggle}
                aria-expanded={isOpen}
                aria-haspopup="true"
                disabled={isLoading}
            >
                Theme: {currentTheme}
            </ThemeButton>
            {isOpen && (
                <ThemeList role="menu">
                    {Object.keys(themes).map((themeName, index) => {
                        logDebug('Rendering theme option', {
                            theme: themeName,
                            isCurrentTheme: themeName === currentTheme
                        });
                        return (
                            <ThemeOption
                                key={themeName}
                                onClick={() => handleThemeChange(themeName as keyof typeof themes)}
                                role="menuitem"
                                aria-current={themeName === currentTheme}
                                ref={index === 0 ? firstOptionRef : undefined}
                                tabIndex={0}
                            >
                                {themeName}
                            </ThemeOption>
                        );
                    })}
                </ThemeList>
            )}
        </ThemeMenuContainer>
    );
};