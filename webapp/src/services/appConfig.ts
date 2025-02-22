import {store} from '../store';

import {setAppInfo} from '../store/slices/configSlice';
import {ThemeName} from '../types';

const LOG_PREFIX = '[AppConfig]';
// Add archive detection and export
export const isArchive = document.documentElement.hasAttribute('data-archive');

const BASE_API_URL = process.env.REACT_APP_API_URL || (window.location.origin + window.location.pathname);

let loadConfigPromise: Promise<any> | null = null;
const STORAGE_KEYS = {
    THEME: 'theme',
} as const;
// Type guard for theme validation
const isValidTheme = (theme: unknown): theme is ThemeName => {
     const validThemes = ['default', 'main', 'night', 'forest', 'pony', 'alien', 'sunset', 'ocean', 'cyberpunk'] as const;
     return typeof theme === 'string' && validThemes.includes(theme as ThemeName);
 };
// Add theme storage functionality
export const themeStorage = {
    getTheme: (): ThemeName | null => {
        const theme = localStorage.getItem(STORAGE_KEYS.THEME);
        return isValidTheme(theme) ? theme : null;
    },
    setTheme: (theme: ThemeName): void => {
        localStorage.setItem(STORAGE_KEYS.THEME, theme);
    }
};
// Add fetchAppConfig function
export const fetchAppConfig = async (sessionId: string): Promise<any> => {
    if (loadConfigPromise) {
        return loadConfigPromise;
    }
    loadConfigPromise = fetch(`${BASE_API_URL}/config?session=${sessionId}`)
        .then(response => response.json())
        .then(config => {
            store.dispatch(setAppInfo(config));
            return config;
        })
        .catch(error => {
            console.error(`${LOG_PREFIX} Failed to fetch app config:`, error);
            loadConfigPromise = null;
            throw error;
        });
    return loadConfigPromise;
};