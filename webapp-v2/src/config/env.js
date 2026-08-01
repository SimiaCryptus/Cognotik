/**
 * Build-time configuration (reverse-spec §19.4.6).
 *
 * The React app scattered `process.env.NODE_ENV` / `REACT_APP_API_URL` through the
 * code base. All environment access is funnelled through this module instead so
 * the rest of the app has no build-tool coupling.
 */

const env = (typeof import.meta !== 'undefined' && import.meta.env) || {};

export const IS_DEV = env.DEV === true || env.MODE === 'development';

/** Optional absolute API base, e.g. "https://host:8083/coding/". Empty = derive from location. */
export const API_URL = env.VITE_API_URL || '';

/** Optional websocket port override. Empty = derive from location (§3.1). */
export const WS_PORT = env.VITE_WS_PORT || '';

export const APP_VERSION = env.VITE_APP_VERSION || '2.0.0';

/** 'debug' | 'info' | 'warn' | 'error' */
export const LOG_LEVEL = env.VITE_LOG_LEVEL || (IS_DEV ? 'debug' : 'warn');