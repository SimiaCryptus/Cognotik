/**
 * Enumerations for API types
 */

/**
 * StorySortOrder - Available sorting options for stories
 */
export enum StorySortOrder {
  SCORE = 'SCORE',
  RECENT = 'RECENT',
  COMMENTS = 'COMMENTS',
}

/**
 * HTTP Status Codes used by the API
 */
export enum HttpStatus {
  OK = 200,
  BAD_REQUEST = 400,
  NOT_FOUND = 404,
  INTERNAL_SERVER_ERROR = 500,
}

/**
 * API Endpoint paths
 */
export enum ApiEndpoint {
  STORIES_TOP = '/api/stories/top',
  STORIES_NEW = '/api/stories/new',
  STORIES_DETAIL = '/api/stories',
  STORIES_SEARCH = '/api/stories/search',
  STORIES_COMMENTS = '/api/stories/:id/comments',
  COMMENTS_DETAIL = '/api/comments',
  USERS_DETAIL = '/api/users',
  USERS_STORIES = '/api/users/:username/stories',
  USERS_COMMENTS = '/api/users/:username/comments',
}

/**
 * Route paths for the application
 */
export enum RoutePath {
  HOME = '/',
  NEW = '/new',
  STORY = '/story/:id',
  SEARCH = '/search',
  USER = '/user/:username',
  NOT_FOUND = '*',
}

/**
 * Local storage keys
 */
export enum StorageKey {
  THEME = 'news-app-theme',
  CACHED_STORIES = 'news-app-cached-stories',
  CACHED_USERS = 'news-app-cached-users',
  SEARCH_HISTORY = 'news-app-search-history',
}

/**
 * Theme options
 */
export enum Theme {
  LIGHT = 'light',
  DARK = 'dark',
  SYSTEM = 'system',
}

/**
 * Error types for better error handling
 */
export enum ErrorType {
  NETWORK = 'NETWORK',
  NOT_FOUND = 'NOT_FOUND',
  VALIDATION = 'VALIDATION',
  SERVER = 'SERVER',
  UNKNOWN = 'UNKNOWN',
}

/**
 * Loading state types
 */
export enum LoadingType {
  INITIAL = 'INITIAL',
  REFRESH = 'REFRESH',
  LOAD_MORE = 'LOAD_MORE',
}