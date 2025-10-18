import React, { useEffect } from 'react';
import { StoryItem } from './StoryItem';
import { Pagination } from '../common/Pagination';
import { Loading } from '../common/Loading';
import { Error } from '../common/Error';
import { EmptyState } from '../common/EmptyState';
import { Story } from '../../types/api';
import { useBatchStories } from '../../hooks/useStories';
import './StoryList.css';

export interface StoryListProps {
storyIds: number[];
currentPage: number;
totalPages: number;
hasMore: boolean;
onPageChange: (page: number) => void;
loading?: boolean;
error?: Error | null;
emptyMessage?: string;
className?: string;
}

export const StoryList: React.FC<StoryListProps> = ({
 storyIds,
 currentPage,
 totalPages,
 hasMore,
 onPageChange,
 loading = false,
 error = null,
 emptyMessage = 'No stories found',
 className = '',
 }) => {
 const { stories, loading: storiesLoading, error: storiesError, fetchStories } = useBatchStories();

 useEffect(() => {
    if (storyIds.length > 0) {
      fetchStories(storyIds);
    }
  }, [storyIds.join(',')]);

if (loading || storiesLoading) {
    return <Loading size="large" text="Loading stories..." />;
  }

if (error || storiesError) {
    return (
      <Error
        message={error?.message || storiesError?.message || 'Failed to load stories'}
        onRetry={() => fetchStories(storyIds)}
      />
    );
  }

if (storyIds.length === 0) {
    return <EmptyState message={emptyMessage} />;
  }

const storyList = storyIds
    .map(id => stories.get(id))
    .filter((story): story is Story => story !== undefined);

return (
    <div className={`story-list ${className}`}>
      <div className="story-list__items">
        {storyList.map((story, index) => (
          <StoryItem
            key={story.id}
            story={story}
            rank={currentPage > 1 ? (currentPage - 1) * storyIds.length + index + 1 : index + 1}
          />
        ))}
      </div>

      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        hasMore={hasMore}
        onPageChange={onPageChange}
        className="story-list__pagination"
      />
    </div>
  );
};