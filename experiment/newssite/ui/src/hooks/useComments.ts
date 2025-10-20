import { useState, useEffect, useCallback } from 'react';
import { commentsService } from '../services/comments';
import { Comment } from '../types/api';

interface UseCommentState {
  data: Comment | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export const useComment = (id: number | null): UseCommentState => {
  const [data, setData] = useState<Comment | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    if (!id) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await commentsService.getComment(id);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch comment'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};

// Hook for batch fetching comment details
interface UseBatchCommentsState {
  comments: Map<number, Comment>;
  loading: boolean;
  error: Error | null;
  fetchComments: (ids: number[]) => Promise<void>;
  clearComments: () => void;
}

export const useBatchComments = (): UseBatchCommentsState => {
  const [comments, setComments] = useState<Map<number, Comment>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchComments = useCallback(async (ids: number[]) => {
    if (ids.length === 0) return;

    try {
      setLoading(true);
      setError(null);
      
      const results = await Promise.allSettled(
        ids.map(id => commentsService.getComment(id))
      );

      setComments(prevComments => {
        const newComments = new Map(prevComments);
        results.forEach((result, index) => {
          if (result.status === 'fulfilled') {
            newComments.set(ids[index], result.value);
          }
        });
        return newComments;
      });
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch comments'));
    } finally {
      setLoading(false);
    }
  }, []);

  const clearComments = useCallback(() => {
    setComments(new Map());
  }, []);

  return { comments, loading, error, fetchComments, clearComments };
};

// Hook for fetching comment thread (parent + all children recursively)
interface UseCommentThreadState {
  comments: Map<number, Comment>;
  loading: boolean;
  error: Error | null;
  fetchThread: (rootCommentId: number) => Promise<void>;
}

export const useCommentThread = (): UseCommentThreadState => {
  const [comments, setComments] = useState<Map<number, Comment>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchThread = useCallback(async (rootCommentId: number) => {
    try {
      setLoading(true);
      setError(null);
      
      const fetchedComments = new Map<number, Comment>();
      const toFetch: number[] = [rootCommentId];

      while (toFetch.length > 0) {
        const currentId = toFetch.shift()!;
        
        if (fetchedComments.has(currentId)) continue;

        try {
          const comment = await commentsService.getComment(currentId);
          fetchedComments.set(currentId, comment);
          
          // Add child comments to fetch queue
          if (comment.childCommentIds && comment.childCommentIds.length > 0) {
            toFetch.push(...comment.childCommentIds);
          }
        } catch (err) {
          console.error(`Failed to fetch comment ${currentId}:`, err);
        }
      }

      setComments(fetchedComments);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch comment thread'));
    } finally {
      setLoading(false);
    }
  }, []);

  return { comments, loading, error, fetchThread };
};