import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {AppConfig, WebSocketConfig} from '../../types/config';
import {ThemeName} from '../../types/theme';

const isValidTheme = (theme: string | null): theme is ThemeName => {
    return theme === 'default' || theme === 'main' || theme === 'night' ||
        theme === 'forest' || theme === 'pony' || theme === 'alien' ||
        theme === 'sunset' || theme === 'ocean' || theme === 'cyberpunk';
};

const loadSavedTheme = (): ThemeName => {
    const savedTheme = localStorage.getItem('theme');
    return isValidTheme(savedTheme) ? savedTheme : 'main';
};

const loadWebSocketConfig = () => {

    if (process.env.NODE_ENV !== 'development') {
        return {
            url: window.location.hostname,
            port: window.location.port || (window.location.protocol === 'https:' ? '443' : '80'),
            protocol: window.location.protocol === 'https:' ? 'wss:' : 'ws:',
            retryAttempts: 3,
            timeout: 5000
        };
    }

    try {
        const savedConfig = localStorage.getItem('websocketConfig');
        if (savedConfig) {
            return JSON.parse(savedConfig);
        }
    } catch (error) {
        console.error('[ConfigSlice] Failed to load WebSocket config:', {
            source: 'localStorage',
            error
        });
    }
    return {
        url: window.location.hostname,
        port: window.location.port,
        protocol: window.location.protocol === 'https:' ? 'wss:' : 'ws:',
        retryAttempts: 3,
        timeout: 5000
    };
};

const initialState: AppConfig = {
    singleInput: false,
    stickyInput: true,
    loadImages: true,
    showMenubar: true,
    applicationName: 'Chat App',
    isArchive: document.documentElement.hasAttribute('data-archive'),
    websocket: loadWebSocketConfig(),
    logging: {
        enabled: true,
        maxEntries: 1000,
        persistLogs: false,
        minLogLevel: 'info',
        console: {
            enabled: true,
            showTimestamp: true,
            showLevel: true,
            showSource: true,
            logLevel: 'info',

            styles: {
                debug: {color: '#6c757d'},
                info: {color: '#17a2b8'},
                warn: {color: '#ffc107', bold: true},
                error: {color: '#dc3545', bold: true}
            }
        }
    },
    theme: {
        current: loadSavedTheme(),
        autoSwitch: false
    }
};

const configSlice = createSlice({
    name: 'config',
    initialState,
    reducers: {
        setAppInfo: (state, action: PayloadAction<any>) => {
            if (action.payload) {
                if (action.payload.applicationName) {
                    state.applicationName = action.payload.applicationName;
                    document.title = action.payload.applicationName;
                }
                if (action.payload.singleInput !== undefined) {
                    state.singleInput = action.payload.singleInput;
                }
                if (action.payload.stickyInput !== undefined) {
                    state.stickyInput = action.payload.stickyInput;
                }
                if (action.payload.loadImages !== undefined) {
                    state.loadImages = action.payload.loadImages;
                }
                if (action.payload.websocket) {
                    state.websocket = {...state.websocket, ...action.payload.websocket};
                }
                if (action.payload.showMenubar !== undefined) {
                    state.showMenubar = action.payload.showMenubar;
                    applyMenubarConfig(state.showMenubar);
                }
            }
        },
        setTheme: (state, action: PayloadAction<ThemeName>) => {
            state.theme.current = action.payload;
            localStorage.setItem('theme', action.payload);
        },
        updateWebSocketConfig: (state, action: PayloadAction<Partial<WebSocketConfig>>) => {

            if (process.env.NODE_ENV !== 'development') {
                console.warn('[ConfigSlice] WebSocket config updates are only allowed in development mode');
                return;
            }

            state.websocket = {...state.websocket, ...action.payload};

            try {
                localStorage.setItem('websocketConfig', JSON.stringify(state.websocket));
            } catch (error) {
                console.error('[ConfigSlice] Failed to persist WebSocket config:', {
                    source: 'localStorage',
                    error
                });
            }
        },
    },
});

function applyMenubarConfig(showMenubar: boolean) {
    if (showMenubar === false) {
        const menubar = document.getElementById('toolbar');
        if (menubar) menubar.style.display = 'none';
        const namebar = document.getElementById('namebar');
        if (namebar) namebar.style.display = 'none';
        const mainInput = document.getElementById('main-input');
        if (mainInput) {
            mainInput.style.top = '0px';
        }
        const session = document.getElementById('session');
        if (session) {
            session.style.top = '0px';
            session.style.width = '100%';
            session.style.position = 'absolute';
        }
    }
}

export const {
    updateWebSocketConfig,
    setAppInfo,
} = configSlice.actions;

export default configSlice.reducer;