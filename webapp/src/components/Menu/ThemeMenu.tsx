import React from 'react';
import styled from 'styled-components';
import {useTheme} from '../../hooks/useTheme';
import {themes, layoutThemes, LayoutThemeName} from '../../themes/themes';
import {useDispatch, useSelector} from 'react-redux';
import {setModalContent, showModal, setLayoutTheme} from '../../store/slices/uiSlice';
import { RootState } from '../../store';

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
    const [isLayoutOpen, setIsLayoutOpen] = React.useState(false);
    const [isLoading, setIsLoading] = React.useState(false);
    const menuRef = React.useRef<HTMLDivElement>(null);
    const firstOptionRef = React.useRef<HTMLButtonElement>(null);
    const dispatch = useDispatch();

    React.useEffect(() => {
        if (isOpen && firstOptionRef.current) {
            // This needs to be smarter if both menus can be open
            firstOptionRef.current.focus();
        }
    }, [isOpen]);

    React.useEffect(() => {
        const handleEscapeKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
              if (isOpen) {
                setIsOpen(false);
              }
              if (isLayoutOpen) setIsLayoutOpen(false);
            }
        };
        if (isOpen || isLayoutOpen) {
            document.addEventListener('keydown', handleEscapeKey);
        }
        return () => {
            document.removeEventListener('keydown', handleEscapeKey);
        };
    }, [isOpen, isLayoutOpen]);

    React.useEffect(() => {
        const handleKeyboardShortcut = (event: KeyboardEvent) => {

            const isMac = /Mac|iPod|iPhone|iPad/.test(navigator.platform);
            const isShortcutTriggered = isMac
                ? (event.ctrlKey && event.key.toLowerCase() === 't')
                : (event.altKey && event.key.toLowerCase() === 't');
            if (isShortcutTriggered) {
                event.preventDefault();
                const themeSelectorContent = Object.keys(themes).map(themeName => `
                        <button

                            onclick="window.dispatchEvent(new CustomEvent('themeChange', {detail: '${themeName}'}))"
                            style="display: block; width: 100%; margin: 8px 0; padding: 8px; text-align: left; background-color: ${themeName === currentTheme ? '#ddd;' : 'transparent'}; border: 1px solid #ccc; border-radius: 4px; cursor: pointer;"
                        >
                            ${themeName}
                        </button>
                    `).join('');

                const layoutSelectorContent = Object.keys(layoutThemes).map(layoutName => `
                        <button
                            onclick="window.dispatchEvent(new CustomEvent('layoutThemeChange', {detail: '${layoutName}'}))"
                            style="display: block; width: 100%; margin: 8px 0; padding: 8px; text-align: left; background-color: ${layoutName === currentLayoutThemeName ? '#ddd;' : 'transparent'}; border: 1px solid #ccc; border-radius: 4px; cursor: pointer;"
                        >
                            ${layoutName}
                        </button>
                    `).join('');

                const modalContent = `
                <div style="padding: 10px;">
                    <h3 style="margin-top: 0; margin-bottom: 10px;">Color Theme</h3>
                    ${themeSelectorContent}
                    <h3 style="margin-top: 20px; margin-bottom: 10px;">Layout Theme</h3>
                    ${layoutSelectorContent}
                </div>
            `;
                dispatch(showModal('Theme & Layout Selection'));
                dispatch(setModalContent(modalContent));
                const shortcutKey = isMac ? 'Ctrl+T' : 'Alt+T';
                logDebug(`Theme & Layout modal opened via keyboard shortcut (${shortcutKey})`);
            }
        };
        document.addEventListener('keydown', handleKeyboardShortcut);
        return () => {
            document.removeEventListener('keydown', handleKeyboardShortcut);
        };
    }, [currentTheme, dispatch]); // Added currentLayoutThemeName

    const handleThemeChange = React.useCallback(async (themeName: keyof typeof themes) => {
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
    }, [currentTheme, setTheme, setIsLoading, setIsOpen]);

    React.useEffect(() => {
        const handleThemeChangeEvent = (event: CustomEvent<string>) => {
            handleThemeChange(event.detail as keyof typeof themes);
        };
        window.addEventListener('themeChange', handleThemeChangeEvent as EventListener);
        return () => {
            window.removeEventListener('themeChange', handleThemeChangeEvent as EventListener);
        };
    }, [handleThemeChange]);

    React.useEffect(() => {
        const handleLayoutThemeChangeEvent = (event: CustomEvent<string>) => {
            const layoutName = event.detail as LayoutThemeName;
            dispatch(setLayoutTheme(layoutName));
            // Modal remains open for further changes unless explicitly closed
            logDebug('Layout theme changed via modal', { layout: layoutName });
        };
        window.addEventListener('layoutThemeChange', handleLayoutThemeChangeEvent as EventListener);
        return () => {
            window.removeEventListener('layoutThemeChange', handleLayoutThemeChangeEvent as EventListener);
        };
    }, [dispatch]);

    React.useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
                if (isOpen) setIsOpen(false);
                if (isLayoutOpen) setIsLayoutOpen(false);
            }
        };
        if (isOpen || isLayoutOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isOpen, isLayoutOpen]);

    React.useEffect(() => {
        logDebug('Theme changed:', {
            theme: currentTheme,
            timestamp: new Date().toISOString()
        });
    }, [currentTheme]);





    const handleMenuToggle = () => {
        logDebug('Menu state changing', {
            action: !isOpen ? 'opening' : 'closing',
            currentTheme,
            timestamp: new Date().toISOString()
        });
        setIsOpen(!isOpen);
    };
    const handleLayoutMenuToggle = () => {
        setIsLayoutOpen(!isLayoutOpen);
    };
    const handleLayoutThemeChange = (layoutName: LayoutThemeName) => {
        dispatch(setLayoutTheme(layoutName));
        setIsLayoutOpen(false);
        logDebug('Layout theme changed', { layout: layoutName });
    };
    const currentLayoutThemeName = useSelector((state: RootState) => state.ui.layoutTheme);


    return (
        <ThemeMenuContainer ref={menuRef}>
            {/* Color Theme Selector */}
            <div style={{ position: 'relative', display: 'inline-block' }}>
                <ThemeButton
                    onClick={handleMenuToggle}
                    aria-expanded={isOpen}
                    aria-haspopup="true"
                    disabled={isLoading}
                >
                    Theme: {currentTheme}
                </ThemeButton>
                {isOpen && (
                    <ThemeList role="menu" style={{ right: 0 }}> {/* Position relative to this new div */}
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
                                    ref={index === 0 ? firstOptionRef : null}
                                    tabIndex={0}
                                >
                                    {themeName}
                                </ThemeOption>
                            );
                        })}
                    </ThemeList>
                )}
            </div>

            {/* Layout Theme Selector */}
            <div style={{ position: 'relative', display: 'inline-block', marginLeft: '0.5rem' }}>
                <ThemeButton
                    onClick={handleLayoutMenuToggle}
                    aria-expanded={isLayoutOpen}
                    aria-haspopup="true"
                >
                    Layout: {currentLayoutThemeName}
                </ThemeButton>
                {isLayoutOpen && (
                    <ThemeList role="menu" style={{ left: 0, right: 'auto' }}> {/* Position relative to this new div */}
                        {Object.keys(layoutThemes).map((layoutName, index) => (
                            <ThemeOption
                                key={layoutName}
                                onClick={() => handleLayoutThemeChange(layoutName as LayoutThemeName)}
                                role="menuitem"
                                aria-current={layoutName === currentLayoutThemeName}
                                ref={index === 0 && !isOpen ? firstOptionRef : null} // Focus if color theme menu is closed
                                tabIndex={0}
                            >
                                {layoutName}
                            </ThemeOption>
                        ))}
                    </ThemeList>
                )}
            </div>
        </ThemeMenuContainer>
    );
};