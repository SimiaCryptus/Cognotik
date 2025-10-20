import React from 'react';
 import { useNewStories } from '../hooks/useStories';
 import { usePagination } from '../hooks/usePagination';
 import { StoryList } from '../components/stories/StoryList';
 import './NewStories.css';

 export const NewStories: React.FC = () => {
  const { page, pageSize, goToPage, totalPages: calculatedTotalPages } = usePagination();
  const { data, loading, error } = useNewStories(page, pageSize);

  const totalPages = data ? Math.ceil(data.totalCount / data.pageSize) : calculatedTotalPages;

  return (
    <div className="new-stories">
      <div className="new-stories__header">
        <h1 className="new-stories__title">New Stories</h1>
        <p className="new-stories__subtitle">
          The latest stories submitted to the platform
        </p>
      </div>

      <StoryList
        storyIds={data?.ids || []}
        currentPage={page}
        totalPages={totalPages}
        hasMore={data?.hasMore || false}
        onPageChange={goToPage}
        loading={loading}
        error={error}
        emptyMessage="No new stories available at the moment"
        className="new-stories__story-list"
      />
    </div>
  );
};