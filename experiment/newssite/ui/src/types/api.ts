/**
 * API Type Definitions
 * These types match the News API data models
 */

import { StorySortOrder } from './enums';
export { StorySortOrder };

/**
 * Story - Represents a news story
 */
export interface Story {
  id: number;
  title: string;
  url: string | null;
  text: string | null;
  author: string;
  score: number;
  timestamp: string; // ISO 8601 DateTime
  commentIds: number[];
  commentCount: number;
  tags: string[];
  topics: string[];
  location: string | null;
}

/**
 * Comment - Represents a comment on a story or another comment
 */
export interface Comment {
  id: number;
  author: string;
  text: string; // HTML content
  timestamp: string; // ISO 8601 DateTime
  parentId: number;
  childCommentIds: number[];
  tags: string[];
  topics: string[];
  location: string | null;
}

/**
 * User - Represents a user account
 */
export interface User {
  id: string; // username
  created: string; // ISO 8601 DateTime
  karma: number;
  about: string | null;
  submittedIds: number[];
  location: string | null;
  topics: string[];
  tags: string[];
}

/**
 * PaginatedStoryIds - Paginated list of story identifiers
 */
export interface PaginatedStoryIds {
  ids: number[];
  page: number;
  pageSize: number;
  totalCount: number;
  hasMore: boolean;
}

/**
 * PaginatedCommentIds - Paginated list of comment identifiers
 */
export interface PaginatedCommentIds {
  ids: number[];
  page: number;
  pageSize: number;
  totalCount: number;
  hasMore: boolean;
}

/**
 * PaginationParams - Parameters for controlling pagination
 */
 export interface PaginationParams {
  page?: number; // Default: 1
  pageSize?: number; // Default: 5, Max: 100
  [key: string]: string | number | boolean | undefined;
}

/**
 * StorySearchRequest - Request body for story search
 */
export interface StorySearchRequest {
  query?: string;
  tags?: string[];
  topics?: string[];
  location?: string;
  author?: string;
  minScore?: number;
  after?: string; // ISO 8601 DateTime
  before?: string; // ISO 8601 DateTime
  sortOrder?: StorySortOrder;
  page?: number;
  pageSize?: number;
}

/**
 * API Error Response
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

/**
 * Generic API Response wrapper for error handling
 */
export interface ApiResponse<T> {
  data?: T;
  error?: ApiError;
}

/**
 * Loading state for async operations
 */
export interface LoadingState {
  isLoading: boolean;
  error: string | null;
}

/**
 * Paginated data with loading state
 */
export interface PaginatedData<T> extends LoadingState {
  data: T[];
  page: number;
  pageSize: number;
  totalCount: number;
  hasMore: boolean;
}

/**
 * Story with full details (for detail view)
 */
export interface StoryDetail extends Story {
  comments?: Comment[];
}

/**
 * User with activity data
 */
export interface UserWithActivity extends User {
  stories?: Story[];
  comments?: Comment[];
}