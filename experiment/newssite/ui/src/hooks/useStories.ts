import { useState, useEffect, useCallback } from 'react';
import { storiesService } from '../services/stories';
import { Story, PaginatedStoryIds, StorySearchRequest } from '../types/api';

interface UseStoriesState {
  data: PaginatedStoryIds | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

interface UseStoryState {
  data: Story | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

export const useTopStories = (page: number = 1, pageSize: number = 5): UseStoriesState => {
  const [data, setData] = useState<PaginatedStoryIds | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await storiesService.getTopStories({ page, pageSize });
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch top stories'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};

export const useNewStories = (page: number = 1, pageSize: number = 5): UseStoriesState => {
  const [data, setData] = useState<PaginatedStoryIds | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await storiesService.getNewStories({ page, pageSize });
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch new stories'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};

export const useStory = (id: number | null): UseStoryState => {
  const [data, setData] = useState<Story | null>(null);
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
      const result = await storiesService.getStory(id);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch story'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};

export const useStoryComments = (
  storyId: number | null,
  page: number = 1,
  pageSize: number = 5
): UseStoriesState => {
  const [data, setData] = useState<PaginatedStoryIds | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    if (!storyId) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const result = await storiesService.getStoryComments(storyId, { page, pageSize });
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch story comments'));
    } finally {
      setLoading(false);
    }
  }, [storyId, page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { data, loading, error, refetch: fetchData };
};

interface UseSearchStoriesState {
  data: PaginatedStoryIds | null;
  loading: boolean;
  error: Error | null;
  search: (request: StorySearchRequest) => Promise<void>;
}

export const useSearchStories = (): UseSearchStoriesState => {
  const [data, setData] = useState<PaginatedStoryIds | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const search = useCallback(async (request: StorySearchRequest) => {
    try {
      setLoading(true);
      setError(null);
      const result = await storiesService.searchStories(request);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to search stories'));
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, search };
};

// Hook for batch fetching story details
interface UseBatchStoriesState {
  stories: Map<number, Story>;
  loading: boolean;
  error: Error | null;
  fetchStories: (ids: number[]) => Promise<void>;
}

export const useBatchStories = (): UseBatchStoriesState => {
  const [stories, setStories] = useState<Map<number, Story>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchStories = useCallback(async (ids: number[]) => {
    try {
      setLoading(true);
      setError(null);
      
      const results = await Promise.allSettled(
        ids.map(id => storiesService.getStory(id))
      );

        const newStories = new Map(stories);
        results.forEach((result, index) => {
            if (result.status === 'fulfilled') {
                newStories.set(ids[index], result.value);
            }
        });

        setStories(newStories);
    } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') {
            return;
        }
        setError(err instanceof Error ? err : new Error('Failed to fetch stories'));
    } finally {
        setLoading(false);
    }
  }, [stories]);

    return { stories, loading, error, fetchStories };
};