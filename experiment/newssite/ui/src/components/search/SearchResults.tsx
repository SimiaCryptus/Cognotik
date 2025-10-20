import React from 'react';
import { StoryList } from '../stories/StoryList';
import { Loading } from '../common/Loading';
import { Error } from '../common/Error';
import { EmptyState } from '../common/EmptyState';
import { PaginatedStoryIds, StorySortOrder } from '../../types/api';
import './SearchResults.css';

interface SearchResultsProps {
  results: PaginatedStoryIds | null;
  loading: boolean;
  error: Error | null;
  sortOrder: StorySortOrder;
  onSortChange: (sortOrder: StorySortOrder) => void;
  onPageChange: (page: number) => void;
  onRetry: () => void;
}

export const SearchResults: React.FC<SearchResultsProps> = ({
  results,
  loading,
  error,
  sortOrder,
  onSortChange,
  onPageChange,
  onRetry,
}) => {
  if (loading && !results) {
    return (
      <div className="search-results">
        <Loading size="large" text="Searching..." />
      </div>
    );
  }

  if (error) {
    return (
      <div className="search-results">
        <Error
          message={error.message || 'Search failed'}
          onRetry={onRetry}
        />
      </div>
    );
  }

  if (!results) {
    return (
      <div className="search-results">
        <EmptyState
          message="Enter search criteria to find stories"
          icon="🔍"
        />
      </div>
    );
  }

  const totalPages = Math.ceil(results.totalCount / results.pageSize);

  return (
    <div className="search-results">
      <div className="search-results__header">
        <div className="search-results__info">
          <h2 className="search-results__title">
            {results.totalCount} {results.totalCount === 1 ? 'result' : 'results'} found
          </h2>
          {results.totalCount > 0 && (
            <p className="search-results__subtitle">
              Showing {((results.page - 1) * results.pageSize) + 1} - {Math.min(results.page * results.pageSize, results.totalCount)} of {results.totalCount}
            </p>
          )}
        </div>

        {results.totalCount > 0 && (
          <div className="search-results__sort">
            <label htmlFor="results-sort" className="search-results__sort-label">
              Sort by:
            </label>
            <select
              id="results-sort"
              className="search-results__sort-select"
              value={sortOrder}
              onChange={(e) => onSortChange(e.target.value as StorySortOrder)}
            >
              <option value={StorySortOrder.SCORE}>Highest Score</option>
              <option value={StorySortOrder.RECENT}>Most Recent</option>
              <option value={StorySortOrder.COMMENTS}>Most Comments</option>
            </select>
          </div>
        )}
      </div>

      {results.ids.length === 0 ? (
        <EmptyState
          message="No stories match your search criteria"
          icon="📭"
        />
      ) : (
        <StoryList
          storyIds={results.ids}
          currentPage={results.page}
          totalPages={totalPages}
          hasMore={results.hasMore}
          onPageChange={onPageChange}
          loading={loading}
          emptyMessage="No stories found"
        />
      )}
    </div>
  );
};