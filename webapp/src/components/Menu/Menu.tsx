import React from 'react';
import styled from 'styled-components';
import {useDispatch, useSelector} from 'react-redux';
import {useModal} from '../../hooks/useModal';
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome';
import {faCog, faHome} from '@fortawesome/free-solid-svg-icons';
import {ThemeMenu} from "./ThemeMenu";
import {WebSocketMenu} from "./WebSocketMenu";
import {RootState} from "../../store/index";
import {toggleVerbose} from '../../store/slices/uiSlice';

interface MenuContainerProps {
    $hidden?: boolean;
}

const isDevelopment = process.env.NODE_ENV === 'development';
const MenuContainer = styled.div<MenuContainerProps>`
    display: flex;
    justify-content: space-between;
    /* Add test id */

    &[data-testid] {
        outline: none;
    }

    border-bottom: 1px solid ${({theme}) => theme.colors.border};
    max-height: 5vh;
    display: ${({$hidden}) => $hidden ? 'none' : 'flex'};
    box-shadow: 0 2px 8px ${({theme}) => `${theme.colors.primary}20`};
    position: sticky;
    top: 0;
    z-index: 100;
    /* Use composite properties for better performance */
    transform: translate3d(0, 0, 0);
    backface-visibility: hidden;
    background: ${({theme}) => `
        linear-gradient(135deg,

            ${theme.colors.surface}f0,
            ${theme.colors.background}f8,
            ${theme.colors.surface}f0
        )
    `};
    backdrop-filter: blur(8px);
    /* Specific transitions instead of 'all' */
    transition: transform 0.3s ease, box-shadow 0.3s ease;

    @media (max-width: 768px) {
        padding: ${({theme}) => theme.sizing.spacing.xs};
        gap: ${({theme}) => theme.sizing.spacing.xs};
    }
`;

const ToolbarLeft = styled.div`
    display: flex;
    align-items: center; /* Ensure all items are vertically centered and don't stretch unevenly */
    gap: ${({theme}) => theme.sizing.spacing.md};
`;

const DropButton = styled.button`
    color: ${({theme}) => theme.colors.text.primary};
    padding: ${({theme}) => theme.sizing.spacing.sm};
    cursor: pointer;
    display: flex;
    align-items: center;
    border-radius: ${({theme}) => theme.sizing.borderRadius.sm};
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;
    overflow: hidden;
    font-weight: ${({theme}) => theme.typography.fontWeight.medium};
    min-width: 140px;
    font-size: ${({theme}) => theme.typography.fontSize.sm};
    letter-spacing: 0.5px;
    text-transform: capitalize;
    background: ${({theme}) => `${theme.colors.surface}90`};
    border: 0px solid ${({theme}) => `${theme.colors.border}40`};
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    text-decoration: none;
    /* Styles for when used as a link */

    &[href] {
        appearance: none;
        -webkit-appearance: none;
        -moz-appearance: none;
        border: none;
        gap: ${({theme}) => theme.sizing.spacing.sm};
    }

    &:hover {
        background: ${({theme}) => `linear-gradient(
            135deg,
            ${theme.colors.primary},
            ${theme.colors.secondary}
        )`};
        color: ${({theme}) => theme.colors.background};
        transform: translateY(-2px);
        box-shadow: 0 4px 16px ${({theme}) => `${theme.colors.primary}40`},
        0 0 0 1px ${({theme}) => `${theme.colors.primary}40`};

        &::before {
            content: '';
            position: absolute;
            top: -50%;
            left: -50%;
            width: 200%;
            height: 200%;
            background: radial-gradient(
                    circle,
                    rgba(255, 255, 255, 0.2) 0%,
                    transparent 70%
            );
            transform: rotate(45deg);
            animation: shimmer 2s linear infinite;
        }

        @keyframes shimmer {
            from {
                transform: rotate(0deg);
            }
            to {
                transform: rotate(360deg);
            }
        }

        &:after {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: linear-gradient(rgba(255, 255, 255, 0.2), transparent);
            pointer-events: none;
        }
    }

    &:active {
        transform: translateY(0);
    }

    &:disabled {
        cursor: not-allowed;
    }
`;

const DropdownContent = styled.div`
    position: absolute;
    background-color: ${({theme}) => theme.colors.surface};
    min-width: 160px;
    box-shadow: 0 8px 24px ${({theme}) => `${theme.colors.primary}15`};
    z-index: 1;
    top: 100%;
    left: 0;
    border-radius: ${({theme}) => theme.sizing.borderRadius.md};
    border: 1px solid ${({theme}) => theme.colors.border};
    backdrop-filter: blur(12px);
    transform-origin: top;
    animation: dropdownSlide 0.2s ease-out;
    /* Prevent clicks from bubbling up */
    pointer-events: auto;


    @keyframes dropdownSlide {
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

const Dropdown = styled.div`
    color: ${({theme}) => theme.colors.text.primary};
    padding: ${({theme}) => theme.sizing.spacing.sm};
    text-decoration: none;
    cursor: pointer;
    position: relative;
    /* Ensure dropdown container doesn't interfere with clicks */
    pointer-events: auto;

    &:hover {
        color: white;
    }
`;

const DropdownItem = styled.a`
    color: ${({theme}) => theme.colors.text.primary};
    padding: ${({theme}) => theme.sizing.spacing.sm};
    text-decoration: none;
    display: block;
    cursor: pointer;
    /* Ensure dropdown items are clickable */
    pointer-events: auto;
    user-select: none;

    &:hover {
        background-color: ${({theme}) => theme.colors.primary};
        color: white;
    }
`;

export const Menu: React.FC = () => {
    useSelector((state: RootState) => state.config.websocket);
    const showMenubar = useSelector((state: RootState) => state.config.showMenubar);
    const {openModal} = useModal();
    const dispatch = useDispatch();
    const verboseMode = useSelector((state: RootState) => state.ui.verboseMode);
    const [openDropdown, setOpenDropdown] = React.useState<string | null>(null);

    const handleVerboseToggle = () => {
        console.log('[Menu] Verbose mode toggled to:', !verboseMode);
        dispatch(toggleVerbose());
    };

    const handleMenuClick = (modalType: string, event?: React.MouseEvent) => {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        console.debug('[Menu] Opening modal:', modalType);
        setOpenDropdown(null); // Close dropdown when opening modal
        openModal(modalType);
        setOpenDropdown(null); // Close dropdown after action
    };

    const toggleDropdown = (dropdownId: string, event?: React.MouseEvent) => {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        setOpenDropdown(openDropdown === dropdownId ? null : dropdownId);
    };
    const closeDropdowns = () => {
        setOpenDropdown(null);
    };
    React.useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            const target = event.target as Element;
            if (!target.closest('[data-dropdown]')) {
                closeDropdowns();
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);


    return (
        <MenuContainer $hidden={!showMenubar}
                       data-testid="main-menu"
                       id="main-menu">
            <ToolbarLeft>
                <DropButton as="a" href="/" onClick={() => console.debug('[Menu] Home navigation')}
                            data-testid="home-button"
                            id="home-button">
                    <FontAwesomeIcon icon={faHome}/> Home
                </DropButton>

                <Dropdown> {/* Removed style={{display: 'contents'}} */}
                    <DropButton 
                        id="session-menu-button"
                       onClick={() => toggleDropdown('session')}
                        data-dropdown="session"
                    >
                        <FontAwesomeIcon icon={faCog}/> Session
                    </DropButton>
                    <DropdownContent 
                        style={{ display: openDropdown === 'session' ? 'block' : 'none' }}
                        data-dropdown="session"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <DropdownItem id="settings-menu-button" onClick={(e) => { e.stopPropagation(); handleMenuClick('settings'); }}>Settings</DropdownItem>
                        <DropdownItem id="files-menu-button" onClick={(e) => { e.stopPropagation(); handleMenuClick('fileIndex/'); }}>Files</DropdownItem>
                        <DropdownItem id="usage-menu-button" onClick={(e) => { e.stopPropagation(); handleMenuClick('usage'); }}>Usage</DropdownItem>
                        <DropdownItem id="threads-menu-button" onClick={(e) => { e.stopPropagation(); handleMenuClick('threads'); }}>Threads</DropdownItem>
                        {/*
                        <DropdownItem onClick={() => handleMenuClick('share')}>Share</DropdownItem>
*/}
                        <DropdownItem id="cancel-menu-button" onClick={(e) => handleMenuClick('cancel', e)}>Cancel</DropdownItem>
                        {/*
                        <DropdownItem onClick={() => handleMenuClick('delete')}>Delete</DropdownItem>
*/}
                        <DropdownItem id="verbose-menu-button" onClick={(e) => { e.stopPropagation(); handleVerboseToggle(); setOpenDropdown(null); }}>
                            {verboseMode ? 'Hide Verbose' : 'Show Verbose'}
                        </DropdownItem>
                    </DropdownContent>
                </Dropdown>

                <ThemeMenu/>

                {isDevelopment && (
                    <Dropdown> {/* Removed style={{display: 'contents'}} if it was there, ensure consistency */}
                        <DropButton
                           onClick={() => toggleDropdown('config')}
                            data-dropdown="config"
                        >
                            Config
                        </DropButton>
                        <DropdownContent style={{ display: openDropdown === 'config' ? 'block' : 'none' }}>
                            <WebSocketMenu/>
                        </DropdownContent>
                    </Dropdown>
                )}
            </ToolbarLeft>
        </MenuContainer>
    );
};