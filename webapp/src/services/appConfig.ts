import {store} from '../store';

import {setAppInfo} from '../store/slices/configSlice';
import {ThemeName} from '../types';

const LOG_PREFIX = '[AppConfig]';

const BASE_API_URL = (() => {
    const baseUrl = process.env.REACT_APP_API_URL || (window.location.origin + window.location.pathname);
    return baseUrl.endsWith('/') ? baseUrl : baseUrl + '/';
})();

interface AppConfigResponse {
    applicationName?: string;
    inputCnt?: number;
    stickyInput?: boolean;
    loadImages?: boolean;
    showMenubar?: boolean;
    [key: string]: unknown;
}

let loadConfigPromise: Promise<AppConfigResponse | null> | null = null;

export const isArchive = window.location.pathname.includes('/archive/');

const STORAGE_KEYS = {
    THEME: 'theme',
} as const;

const isValidTheme = (theme: unknown): theme is ThemeName => {
    const validThemes = ['default', 'main', 'night', 'forest', 'pony', 'alien', 'sunset', 'ocean', 'cyberpunk'] as ThemeName[];
    return typeof theme === 'string' && validThemes.includes(theme as ThemeName);
};

export const themeStorage = {
    getTheme: (): ThemeName | null => {
        const theme = localStorage.getItem(STORAGE_KEYS.THEME);
        return isValidTheme(theme) ? theme : null;
    },
    setTheme: (theme: ThemeName): void => {
        localStorage.setItem(STORAGE_KEYS.THEME, theme);
    },
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const fetchAppConfig = async (sessionId: string, endpoint = 'appInfo'): Promise<any> => {
    if (loadConfigPromise) {
        return loadConfigPromise;
    }
    console.info(`${LOG_PREFIX} Fetching app config from ${endpoint} for session: ${sessionId}`);
    loadConfigPromise = fetch(`${BASE_API_URL}${endpoint}?session=${sessionId}`, {
        headers: {
            'Accept': 'application/json'
        }
    })
    .then(response => {
        if (!response.ok) {
            throw new Error(`Failed to fetch app config: ${response.status} ${response.statusText}`);
        }
        const contentType = response.headers.get('content-type');
        if (!contentType || (!contentType.includes('application/json') && !contentType.includes('text/json'))) {
            throw new Error(`Expected JSON response but got ${contentType}`);
        }
        return response.json();
    })
    .then(config => {
        console.info(`${LOG_PREFIX} Received app config:`, config);
        store.dispatch(setAppInfo(config));
        return config;
    })
    .catch(error => {
        console.error(`${LOG_PREFIX} Failed to fetch app config:`, error);
        loadConfigPromise = null;
        return {
            applicationName: 'Chat App',
            inputCnt: 0,
            stickyInput: true,
            loadImages: true,
            showMenubar: true
        };
    });
    return loadConfigPromise;
};