import { useState, useEffect, useCallback } from 'react';
import { usersService } from '../services/users';
import { User, PaginatedStoryIds, PaginatedCommentIds } from '../types/api';

interface UseUserState {
  data: User | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export const useUser = (username: string | null): UseUserState => {
  const [data, setData] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    if (!username) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await usersService.getUserByUsername(username);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch user'));
    } finally {
      setLoading(false);
    }
  }, [username]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};

interface UseUserStoriesState {
  data: PaginatedStoryIds | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export const useUserStories = (
  username: string | null,
  page: number = 1,
  pageSize: number = 5
): UseUserStoriesState => {
  const [data, setData] = useState<PaginatedStoryIds | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    if (!username) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await usersService.getUserStories(username, { page, pageSize });
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch user stories'));
    } finally {
      setLoading(false);
    }
  }, [username, page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};

interface UseUserCommentsState {
  data: PaginatedCommentIds | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export const useUserComments = (
  username: string | null,
  page: number = 1,
  pageSize: number = 5
): UseUserCommentsState => {
  const [data, setData] = useState<PaginatedCommentIds | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    if (!username) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await usersService.getUserComments(username, { page, pageSize });
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch user comments'));
    } finally {
      setLoading(false);
    }
  }, [username, page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};