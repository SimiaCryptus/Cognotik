import React from 'react';
 import { CommentThread } from './CommentThread';
 import { LoadingInline } from '../common/LoadingInline';
 import { useBatchComments } from '../../hooks/useComments';
 import './CommentList.css';

 export interface CommentListProps {
  commentIds: number[];
  currentPage?: number;
  totalPages?: number;
  hasMore?: boolean;
  onPageChange?: (page: number) => void;
  showParentContext?: boolean;
  className?: string;
}

 export const CommentList: React.FC<CommentListProps> = ({
  commentIds,
  className = '',
 }) => {
  const { comments, loading, error, fetchComments } = useBatchComments();

  React.useEffect(() => {
    if (commentIds.length > 0) {
      fetchComments(commentIds);
    }
  }, [commentIds, fetchComments]);

  if (loading && comments.size === 0) {
    return <LoadingInline message="Loading comments..." />;
  }

  if (error && comments.size === 0) {
    return (
      <div className="comment-list__error">
        Failed to load comments. Please try again.
      </div>
    );
  }

  return (
    <div className={`comment-list ${className}`}>
      {commentIds.map(commentId => {
        const comment = comments.get(commentId);
        if (!comment) return null;

        return (
          <CommentThread
            key={commentId}
            comment={comment}
            allComments={comments}
            level={0}
          />
        );
      })}
    </div>
  );
};