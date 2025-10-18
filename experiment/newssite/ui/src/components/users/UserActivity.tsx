import React from 'react';
import { PaginatedStoryIds, PaginatedCommentIds } from '../../types/api';
import { StoryList } from '../stories/StoryList';
import { CommentList } from '../comments/CommentList';
import { Loading } from '../common/Loading';
import { Error } from '../common/Error';
import { EmptyState } from '../common/EmptyState';
import './UserActivity.css';

export interface UserActivityProps {
  type: 'stories' | 'comments';
  data: PaginatedStoryIds | PaginatedCommentIds | null;
  loading: boolean;
  error: Error | null;
  currentPage: number;
  onPageChange: (page: number) => void;
  onRetry: () => void;
}

export const UserActivity: React.FC<UserActivityProps> = ({
  type,
  data,
  loading,
  error,
  currentPage,
  onPageChange,
  onRetry,
}) => {
  if (loading) {
    return <Loading size="medium" text={`Loading ${type}...`} />;
  }

  if (error) {
    return (
      <Error
        message={error.message || `Failed to load ${type}`}
        onRetry={onRetry}
      />
    );
  }

  if (!data || data.ids.length === 0) {
    return (
      <EmptyState
        message={type === 'stories' ? 'No stories submitted yet' : 'No comments posted yet'}
      />
    );
  }

  const totalPages = Math.ceil(data.totalCount / data.pageSize);

  return (
    <div className="user-activity">
      {type === 'stories' ? (
        <StoryList
          storyIds={data.ids}
          currentPage={currentPage}
          totalPages={totalPages}
          hasMore={data.hasMore}
          onPageChange={onPageChange}
          emptyMessage="No stories found"
        />
      ) : (
        <CommentList
          commentIds={data.ids}
          currentPage={currentPage}
          totalPages={totalPages}
          hasMore={data.hasMore}
          onPageChange={onPageChange}
          showParentContext={true}
        />
      )}
    </div>
  );
};