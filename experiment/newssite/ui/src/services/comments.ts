/**
 * Comments API Service
 * Handles all comment-related API calls
 */

import { apiClient } from './api';
import { ENDPOINTS } from '../config/api.config';
import type { Comment } from '../types/api';

/**
 * Comments Service
 */
export const commentsService = {
  /**
   * Get comment details by ID
   */
  async getCommentById(id: number): Promise<Comment> {
    return apiClient.get<Comment>(ENDPOINTS.COMMENT_DETAIL(id));
  },
  /**
   * Get comment details by ID (alias for getCommentById)
   */
  async getComment(id: number): Promise<Comment> {
    return this.getCommentById(id);
  },


  /**
   * Get multiple comments by IDs
   */
  async getCommentsByIds(ids: number[]): Promise<Comment[]> {
    const promises = ids.map(id => this.getCommentById(id));
    return Promise.all(promises);
  },

  /**
   * Get comment with all nested children (recursive)
   */
  async getCommentThread(id: number): Promise<Comment> {
    const comment = await this.getCommentById(id);
    
    if (comment.childCommentIds.length > 0) {
      const childComments = await this.getCommentsByIds(comment.childCommentIds);
      // Attach children to comment object (extending the type)
      return {
        ...comment,
        children: childComments,
      } as Comment & { children: Comment[] };
    }
    
    return comment;
  },

  /**
   * Get comments with details (helper method)
   */
  async getCommentsWithDetails(
    ids: number[],
    signal?: AbortSignal
  ): Promise<Comment[]> {
    const promises = ids.map(id =>
      apiClient.get<Comment>(ENDPOINTS.COMMENT_DETAIL(id), undefined, { signal })
    );
    
    // Use Promise.allSettled to handle partial failures
    const results = await Promise.allSettled(promises);
    
    return results
      .filter((result): result is PromiseFulfilledResult<Comment> => 
        result.status === 'fulfilled'
      )
      .map(result => result.value);
  },

  /**
   * Build comment tree structure
   */
  buildCommentTree(comments: Comment[]): Comment[] {
    const commentMap = new Map<number, Comment & { children?: Comment[] }>();
    const rootComments: Comment[] = [];

    // First pass: create map of all comments
    comments.forEach(comment => {
      commentMap.set(comment.id, { ...comment, children: [] });
    });

    // Second pass: build tree structure
    comments.forEach(comment => {
      const commentWithChildren = commentMap.get(comment.id);
      if (!commentWithChildren) return;

      // If this comment has a parent in our set, add it as a child
      const parent = commentMap.get(comment.parentId);
      if (parent && parent.children) {
        parent.children.push(commentWithChildren);
      } else {
        // Otherwise, it's a root comment
        rootComments.push(commentWithChildren);
      }
    });

    return rootComments;
  },
};

export default commentsService;