import { store } from '../store';

import { setAppInfo } from '../store/slices/configSlice';
import { ThemeName } from '../types';

const LOG_PREFIX = '[AppConfig]';
// Add archive detection and export
// Check for archive mode in both URL and localStorage for persistence
export const isArchive = () => {
  // Check for archive mode in URL, localStorage, or detect if the old index.js is running
  // Disable the archive mode detection for now
  const isArchiveMode = false && (window.location.search.includes('archive=true') ||
         localStorage.getItem('archive_mode') === 'true' ||
         // Check if the old index.js has already started in archive mode
         (typeof window !== 'undefined' &&
          window.console &&
          document.documentElement.outerHTML.length > 60000));
  // Log the detection result for debugging
  console.log(`${LOG_PREFIX} Archive mode detection:`, {
    fromURL: window.location.search.includes('archive=true'),
    fromStorage: localStorage.getItem('archive_mode') === 'true',
    fromDocSize: document.documentElement.outerHTML.length > 60000,
    finalDecision: isArchiveMode
  });
  return isArchiveMode;
};

// Store archive mode in localStorage if it's in the URL
if (window.location.search.includes('archive=true')) {
  localStorage.setItem('archive_mode', 'true');
}

console.log(`${LOG_PREFIX} Running in ${isArchive() ? 'archive' : 'live'} mode`);

// Compute base API URL so that it works for subpaths (non-root deploys)
function getBaseApiUrl() {
  if (process.env.REACT_APP_API_URL) return process.env.REACT_APP_API_URL;
  // Remove trailing slash from pathname if present
  let basePath = window.location.pathname.replace(/\/$/, '');
  // If basePath is just '', set to '/'
  if (!basePath) basePath = '/';
  return window.location.origin + basePath + (basePath.endsWith('/') ? '' : '/');
}

const BASE_API_URL = getBaseApiUrl();

let loadConfigPromise: Promise<any> | null = null;
const STORAGE_KEYS = {
  THEME: 'theme',
} as const;
// Type guard for theme validation
const isValidTheme = (theme: unknown): theme is ThemeName => {
  const validThemes = ['default', 'main', 'night', 'forest', 'pony', 'alien', 'sunset', 'ocean', 'cyberpunk'] as ThemeName[];
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
  },
};
// Add fetchAppConfig function
export const fetchAppConfig = async (sessionId: string, endpoint = 'appInfo') : Promise<any> => {
  if (loadConfigPromise) {
    return loadConfigPromise;
  }
  console.info(`${LOG_PREFIX} Fetching app config from ${endpoint} for session: ${sessionId}`);
  loadConfigPromise = fetch(`${BASE_API_URL}${endpoint}?session=${sessionId}`)
    .then(response => response.json())
    .then(config => {
      console.info(`${LOG_PREFIX} Received app config:`, config);
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