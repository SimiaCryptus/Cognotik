import { useState, useCallback, useMemo } from 'react';

interface UsePaginationProps {
  initialPage?: number;
  initialPageSize?: number;
  totalCount?: number;
}

interface UsePaginationReturn {
  page: number;
  pageSize: number;
  totalPages: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
  goToPage: (page: number) => void;
  nextPage: () => void;
  previousPage: () => void;
  setPageSize: (size: number) => void;
  reset: () => void;
}

export const usePagination = ({
  initialPage = 1,
  initialPageSize = 5,
  totalCount = 0,
}: UsePaginationProps = {}): UsePaginationReturn => {
  const [page, setPage] = useState(initialPage);
  const [pageSize, setPageSize] = useState(initialPageSize);

  const totalPages = useMemo(() => {
    return Math.ceil(totalCount / pageSize) || 1;
  }, [totalCount, pageSize]);

  const hasNextPage = useMemo(() => {
    return page < totalPages;
  }, [page, totalPages]);

  const hasPreviousPage = useMemo(() => {
    return page > 1;
  }, [page]);

  const goToPage = useCallback((newPage: number) => {
    setPage(Math.max(1, Math.min(newPage, totalPages)));
  }, [totalPages]);

  const nextPage = useCallback(() => {
    if (hasNextPage) {
      setPage(prev => prev + 1);
    }
  }, [hasNextPage]);

  const previousPage = useCallback(() => {
    if (hasPreviousPage) {
      setPage(prev => prev - 1);
    }
  }, [hasPreviousPage]);

  const handleSetPageSize = useCallback((size: number) => {
    setPageSize(size);
    setPage(1); // Reset to first page when changing page size
  }, []);

  const reset = useCallback(() => {
    setPage(initialPage);
    setPageSize(initialPageSize);
  }, [initialPage, initialPageSize]);

  return {
    page,
    pageSize,
    totalPages,
    hasNextPage,
    hasPreviousPage,
    goToPage,
    nextPage,
    previousPage,
    setPageSize: handleSetPageSize,
    reset,
  };
};

// Hook for infinite scroll pagination
interface UseInfiniteScrollProps {
  initialPageSize?: number;
  onLoadMore: (page: number, pageSize: number) => Promise<{ hasMore: boolean }>;
}

interface UseInfiniteScrollReturn {
  page: number;
  pageSize: number;
  loading: boolean;
  hasMore: boolean;
  loadMore: () => Promise<void>;
  reset: () => void;
}

export const useInfiniteScroll = ({
  initialPageSize = 5,
  onLoadMore,
}: UseInfiniteScrollProps): UseInfiniteScrollReturn => {
  const [page, setPage] = useState(1);
  const [pageSize] = useState(initialPageSize);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);

  const loadMore = useCallback(async () => {
    if (loading || !hasMore) return;

    try {
      setLoading(true);
      const result = await onLoadMore(page + 1, pageSize);
      setHasMore(result.hasMore);
      setPage(prev => prev + 1);
    } catch (err) {
      console.error('Failed to load more:', err);
    } finally {
      setLoading(false);
    }
  }, [loading, hasMore, page, pageSize, onLoadMore]);

  const reset = useCallback(() => {
    setPage(1);
    setHasMore(true);
  }, []);

  return {
    page,
    pageSize,
    loading,
    hasMore,
    loadMore,
    reset,
  };
};