/**
 * Users API Service
 * Handles all user-related API calls
 */

import { apiClient } from './api';
import { ENDPOINTS } from '../config/api.config';
import type {
  User,
  PaginatedStoryIds,
  PaginatedCommentIds,
  PaginationParams,
} from '../types/api';

/**
 * Users Service
 */
export const usersService = {
  /**
   * Get user details by username
   */
  async getUserByUsername(username: string): Promise<User> {
    return apiClient.get<User>(ENDPOINTS.USER_DETAIL(username));
  },
  /**
   * Get user details by username (alias for getUserByUsername)
   */
  async getUser(username: string): Promise<User> {
    return this.getUserByUsername(username);
  },


  /**
   * Get user's submitted stories
   */
  async getUserStories(
    username: string,
    params?: PaginationParams
  ): Promise<PaginatedStoryIds> {
    return apiClient.get<PaginatedStoryIds>(
      ENDPOINTS.USER_STORIES(username),
      params
    );
  },

  /**
   * Get user's comments
   */
  async getUserComments(
    username: string,
    params?: PaginationParams
  ): Promise<PaginatedCommentIds> {
    return apiClient.get<PaginatedCommentIds>(
      ENDPOINTS.USER_COMMENTS(username),
      params
    );
  },

  /**
   * Get multiple users by usernames
   */
  async getUsersByUsernames(usernames: string[]): Promise<User[]> {
    const promises = usernames.map(username => this.getUserByUsername(username));
    
    // Use Promise.allSettled to handle partial failures
    const results = await Promise.allSettled(promises);
    
    return results
      .filter((result): result is PromiseFulfilledResult<User> => 
        result.status === 'fulfilled'
      )
      .map(result => result.value);
  },

  /**
   * Check if user exists
   */
  async userExists(username: string): Promise<boolean> {
    try {
      await this.getUserByUsername(username);
      return true;
    } catch (error) {
      return false;
    }
  },
};

export default usersService;