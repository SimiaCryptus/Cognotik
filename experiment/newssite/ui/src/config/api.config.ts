/**
 * API Configuration
 * Centralized configuration for API endpoints and settings
 */

export const API_CONFIG = {
// Base URL - can be overridden by environment variable
  BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/',
  
  // Default pagination settings
  DEFAULT_PAGE_SIZE: 5,
  MAX_PAGE_SIZE: 100,
  
  // Request timeout in milliseconds
  TIMEOUT: 30000,
  
  // Retry configuration
  MAX_RETRIES: 3,
  RETRY_DELAY: 1000,
  
  // Cache configuration (in milliseconds)
  CACHE_TTL: {
    STORIES: 5 * 60 * 1000, // 5 minutes
    COMMENTS: 10 * 60 * 1000, // 10 minutes
    USERS: 15 * 60 * 1000, // 15 minutes
  },
} as const;

/**
 * API Endpoints
 */
export const ENDPOINTS = {
  // Stories
  STORIES_TOP: '/api/stories/top',
  STORIES_NEW: '/api/stories/new',
  STORY_DETAIL: (id: number) => `/api/stories/${id}`,
  STORY_COMMENTS: (id: number) => `/api/stories/${id}/comments`,
  STORIES_SEARCH: '/api/stories/search',
  
  // Comments
  COMMENT_DETAIL: (id: number) => `/api/comments/${id}`,
  
  // Users
  USER_DETAIL: (username: string) => `/api/users/${username}`,
  USER_STORIES: (username: string) => `/api/users/${username}/stories`,
  USER_COMMENTS: (username: string) => `/api/users/${username}/comments`,
} as const;

/**
 * HTTP Methods
 */
export enum HttpMethod {
  GET = 'GET',
  POST = 'POST',
  PUT = 'PUT',
  DELETE = 'DELETE',
  PATCH = 'PATCH',
}

/**
 * Request configuration
 */
export interface RequestConfig {
  method?: HttpMethod;
  headers?: Record<string, string>;
  body?: unknown;
  signal?: AbortSignal;
  timeout?: number;
}

/**
 * Default headers for all requests
 */
export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
} as const;