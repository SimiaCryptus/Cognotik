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
    const validThemes = ['main', 'night', 'forest', 'pony', 'alien', 'sunset', 'ocean', 'cyberpunk'] as const;
    return typeof theme === 'string' && validThemes.includes(theme as ThemeName);
};
export const themeStorage = {
    /**
     * Get theme with validation and fallback
     */
    getTheme(): ThemeName {
        try {
            const savedTheme = localStorage.getItem(STORAGE_KEYS.THEME);
            if (isValidTheme(savedTheme)) {
                return savedTheme;
            }
            console.warn(`${LOG_PREFIX} Invalid saved theme, using default`);
            return 'main';
        } catch (error) {
            console.error(`${LOG_PREFIX} Error retrieving theme:`, error);
            return 'main';
        }
    },
    /**
     * Set theme with validation and error handling
     */
    setTheme(theme: ThemeName): boolean {
        if (!isValidTheme(theme)) {
            console.error(`${LOG_PREFIX} Invalid theme:`, theme);
            return false;
        }
        try {
            localStorage.setItem(STORAGE_KEYS.THEME, theme);
            return true;
        } catch (error) {
            console.error(`${LOG_PREFIX} Failed to save theme:`, error);
            return false;
        }
    },
    /**
     * Clear theme setting
     */
    clearTheme(): void {
        try {
            localStorage.removeItem(STORAGE_KEYS.THEME);
        } catch (error) {
            console.error(`${LOG_PREFIX} Failed to clear theme:`, error);
        }
    }
};

export interface AppConfig {
    applicationName: string;
    singleInput: boolean;
    showMenubar: boolean;
}

// Add config cache
let cachedConfig: any = null;
const CONFIG_CACHE_KEY = 'app_config_cache';

export const fetchAppConfig: (sessionId: string) => Promise<AppConfig> = async (sessionId: string) => {
    try {
        // If in archive mode, return minimal config
        if (isArchive) {
            const archiveConfig = {
                applicationName: 'Chat Archive',
                singleInput: false,
                showMenubar: true,
                isArchive: true
            };
            store.dispatch(setAppInfo(archiveConfig));
            return archiveConfig;
        }

        // Return cached config if available
        if (cachedConfig) {
            console.debug(`${LOG_PREFIX} Using cached config`);
            return cachedConfig;
        }
        // Return existing promise if already loading
        if (loadConfigPromise) {
            console.info(`${LOG_PREFIX} Config fetch already in progress, reusing promise`);
            return loadConfigPromise;
        }
        loadConfigPromise = (async () => {

            console.debug(`${LOG_PREFIX} Fetching app config`);

            const url = new URL('./appInfo', BASE_API_URL);
            url.searchParams.append('session', sessionId);

            let response: Response;

            try {
                response = await fetch(url.toString(), {
                    headers: {
                        'Accept': 'application/json, text/json',
                        'Cache-Control': 'no-cache'
                    },
                    credentials: 'include'
                });
            } catch (networkError) {
                console.warn(`${LOG_PREFIX} Network request failed:`, {
                    error: networkError,
                    url: url.toString()
                });
                // Return default config for development
                if (process.env.NODE_ENV === 'development') {
                    const defaultConfig = {
                        applicationName: 'Chat App (Offline)',
                        singleInput: false,
                        stickyInput: true,
                        loadImages: true,
                        showMenubar: true
                    };
                    cachedConfig = defaultConfig;
                    store.dispatch(setAppInfo(defaultConfig));
                    // Cache the config
                    localStorage.setItem(CONFIG_CACHE_KEY, JSON.stringify({
                        config: defaultConfig,
                        timestamp: Date.now()
                    }));
                    return defaultConfig;
                }
                return null;
            }

            if (!response.ok) {
                console.warn(`${LOG_PREFIX} API error response:`, {
                    status: response.status,
                    statusText: response.statusText,
                    url: url.toString()
                });
                const errorText = await response.text();
                console.debug(`${LOG_PREFIX} Error response body:`, errorText);
                return null;
            }

            const contentType = response.headers.get('content-type');
            if (!contentType || (!contentType.includes('application/json') && !contentType.includes('text/json'))) {
                console.error(`${LOG_PREFIX} Invalid content type:`, {
                    contentType,
                    url: url.toString()
                });
                throw new Error(`Invalid content type received: ${contentType}`);
            }


            const data = await response.json();
            if (!data || typeof data !== 'object') {
                console.error(`${LOG_PREFIX} Invalid response format:`, data);
                throw new Error('Invalid response format');
            }

            console.debug(`${LOG_PREFIX} Config loaded successfully`);
            // Cache the config
            cachedConfig = data;
            // Store in localStorage with timestamp
            localStorage.setItem(CONFIG_CACHE_KEY, JSON.stringify({
                config: data,
                timestamp: Date.now()
            }));

            store.dispatch(setAppInfo(data));

            return data;
        })();
        const result = await loadConfigPromise;
        loadConfigPromise = null;
        return result;

    } catch (error) {
        console.error(`${LOG_PREFIX} Config fetch failed:`, {
            error,
            sessionId,
            url: BASE_API_URL ? `${BASE_API_URL}/appInfo` : '/appInfo',
            env: process.env.NODE_ENV
        });
        loadConfigPromise = null;
        throw error;
    }
};