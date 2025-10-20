import { useState, useCallback, useEffect, useRef } from 'react';
 import { storiesService } from '../services/stories';
 import { StorySearchRequest, PaginatedStoryIds, StorySortOrder } from '../types/api';

 interface UseSearchState {
  results: PaginatedStoryIds | null;
  loading: boolean;
  error: Error | null;
  search: (params: StorySearchRequest) => Promise<void>;
  clearResults: () => void;
}

 export const useSearch = (debounceMs: number = 300): UseSearchState => {
  const [results, setResults] = useState<PaginatedStoryIds | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const debounceTimerRef = useRef<number | null>(null);

  const search = useCallback(async (params: StorySearchRequest) => {
    // Clear existing timer
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    // Set up debounced search
    debounceTimerRef.current = setTimeout(async () => {
      try {
        setLoading(true);
        setError(null);
        const result = await storiesService.searchStories(params);
        setResults(result);
      } catch (err) {
        setError(err instanceof Error ? err : new Error('Search failed'));
      } finally {
        setLoading(false);
      }
    }, debounceMs);
  }, [debounceMs]);

  const clearResults = useCallback(() => {
    setResults(null);
    setError(null);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  return { results, loading, error, search, clearResults };
};

// Hook for managing search form state
interface SearchFormState {
  query: string;
  tags: string[];
  topics: string[];
  location: string;
  author: string;
  minScore: number | undefined;
  after: string;
  before: string;
  sortOrder: StorySortOrder;
  page: number;
  pageSize: number;
}

interface UseSearchFormState {
  formState: SearchFormState;
  updateField: <K extends keyof SearchFormState>(field: K, value: SearchFormState[K]) => void;
  resetForm: () => void;
  toSearchRequest: () => StorySearchRequest;
}

const initialFormState: SearchFormState = {
  query: '',
  tags: [],
  topics: [],
  location: '',
  author: '',
  minScore: undefined,
  after: '',
  before: '',
  sortOrder: StorySortOrder.SCORE,
  page: 1,
  pageSize: 5,
};

export const useSearchForm = (): UseSearchFormState => {
  const [formState, setFormState] = useState<SearchFormState>(initialFormState);

  const updateField = useCallback(<K extends keyof SearchFormState>(
    field: K,
    value: SearchFormState[K]
  ) => {
    setFormState(prev => ({ ...prev, [field]: value }));
  }, []);

  const resetForm = useCallback(() => {
    setFormState(initialFormState);
  }, []);

  const toSearchRequest = useCallback((): StorySearchRequest => {
    return {
      query: formState.query || undefined,
      tags: formState.tags.length > 0 ? formState.tags : undefined,
      topics: formState.topics.length > 0 ? formState.topics : undefined,
      location: formState.location || undefined,
      author: formState.author || undefined,
      minScore: formState.minScore,
      after: formState.after || undefined,
      before: formState.before || undefined,
      sortOrder: formState.sortOrder,
      page: formState.page,
      pageSize: formState.pageSize,
    };
  }, [formState]);

  return { formState, updateField, resetForm, toSearchRequest };
};