import React from 'react';
import styled from 'styled-components';
import {useDispatch, useSelector} from 'react-redux';
import {useModal} from '../../hooks/useModal';
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome';
import {faCog, faHome, faSignInAlt, faSignOutAlt} from '@fortawesome/free-solid-svg-icons';
import {ThemeMenu} from "./ThemeMenu";
import {WebSocketMenu} from "./WebSocketMenu";
import {RootState} from "../../store/index";
import {toggleVerbose} from '../../store/slices/uiSlice';

interface MenuContainerProps {
    $hidden?: boolean;
}

const isDevelopment = process.env.NODE_ENV === 'development';

function long64(): string {
    const buffer = new ArrayBuffer(8);
    const view = new DataView(buffer);
    view.setBigInt64(0, BigInt(Math.floor(Math.random() * Number.MAX_SAFE_INTEGER)));
    return btoa(String.fromCharCode(...new Uint8Array(buffer)))
        .replace(/=/g, '')
        .replace(/\//g, '.')
        .replace(/\+/g, '-');
}

function id2() {
    return Array.from(long64())
        .filter((it) => {
            if (it >= 'a' && it <= 'z') return true;
            if (it >= 'A' && it <= 'Z') return true;
            if (it >= '0' && it <= '9') return true;
            return false;
        })
        .slice(0, 4)
        .join('');
}

function newGlobalID(): string {
    const yyyyMMdd = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    return (`G-${yyyyMMdd}-${id2()}`);
}

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
    gap: ${({theme}) => theme.sizing.spacing.md};
`;

const Dropdown = styled.div`
    color: ${({theme}) => theme.colors.text.primary};
    padding: ${({theme}) => theme.sizing.spacing.sm};
    text-decoration: none;
    cursor: pointer;
    position: relative;

    &:hover {
        color: white;

    }
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
    display: none;
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

    ${Dropdown}:hover & {
        display: block;
    }

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

const DropdownItem = styled.a`
    color: ${({theme}) => theme.colors.text.primary};
    padding: ${({theme}) => theme.sizing.spacing.sm};
    text-decoration: none;
    display: block;
    cursor: pointer;

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
    const handleVerboseToggle = () => {
        console.log('[Menu] Verbose mode toggled to:', !verboseMode);
        dispatch(toggleVerbose());
    };

    const handleMenuClick = (modalType: string) => {
        console.debug('[Menu] Opening modal:', modalType);
        openModal(modalType);
    };

    const handleLogout = () => {
        console.log('[Menu] User initiated logout');
    };


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

                <Dropdown
                    data-testid="app-menu-button"
                    id="app-menu-button">
                    <DropButton
                        data-testid="sessions-button"
                        id="sessions-button">App</DropButton>
                    <DropdownContent>
                        <DropdownItem onClick={() => openModal('sessions')}>Session List</DropdownItem>
                        <DropdownItem as="a" href={"./#" + newGlobalID()}>New</DropdownItem>
                    </DropdownContent>
                </Dropdown>

                <Dropdown>
                    <DropButton>
                        <FontAwesomeIcon icon={faCog}/> Session
                    </DropButton>
                    <DropdownContent>
                        <DropdownItem onClick={() => handleMenuClick('settings')}>Settings</DropdownItem>
{/*
                        <DropdownItem onClick={() => handleMenuClick('fileIndex/')}>Files</DropdownItem>
*/}
                        <DropdownItem onClick={() => handleMenuClick('usage')}>Usage</DropdownItem>
                        <DropdownItem onClick={() => handleMenuClick('threads')}>Threads</DropdownItem>
{/*
                        <DropdownItem onClick={() => handleMenuClick('share')}>Share</DropdownItem>
*/}
                        <DropdownItem onClick={() => handleMenuClick('cancel')}>Cancel</DropdownItem>
{/*
                        <DropdownItem onClick={() => handleMenuClick('delete')}>Delete</DropdownItem>
*/}
                        <DropdownItem onClick={handleVerboseToggle}>
                            {verboseMode ? 'Hide Verbose' : 'Show Verbose'}
                        </DropdownItem>
                    </DropdownContent>
                </Dropdown>

                <ThemeMenu/>
{/*

                <Dropdown>
                    <DropButton>About</DropButton>
                    <DropdownContent>
                        <DropdownItem onClick={() => handleMenuClick('/privacy.html')}>Privacy Policy</DropdownItem>
                        <DropdownItem onClick={() => handleMenuClick('/tos.html')}>Terms of Service</DropdownItem>
                    </DropdownContent>
                </Dropdown>
*/}

                {isDevelopment && (
                    <Dropdown>
                        <DropButton>
                            Config
                        </DropButton>
                        <DropdownContent>
                            <WebSocketMenu/>
                        </DropdownContent>
                    </Dropdown>
                )}
            </ToolbarLeft>

            <Dropdown>
                <DropButton>
                    <FontAwesomeIcon icon={faSignInAlt}/> Login
                </DropButton>
                <DropdownContent>
                    <DropdownItem onClick={() => handleMenuClick('/userSettings')}>Settings</DropdownItem>
                    <DropdownItem onClick={() => handleMenuClick('/usage')}>Usage</DropdownItem>
                    <DropdownItem onClick={handleLogout}>
                        <FontAwesomeIcon icon={faSignOutAlt}/> Logout
                    </DropdownItem>
                </DropdownContent>
            </Dropdown>
        </MenuContainer>
    );
};