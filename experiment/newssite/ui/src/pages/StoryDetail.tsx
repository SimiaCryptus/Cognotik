import React, { useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useStory, useStoryComments } from '../hooks/useStories';
import { StoryContent } from '../components/stories/StoryContent';
import { CommentList } from '../components/comments/CommentList';
import { LoadingPage } from '../components/common/LoadingPage';
import { Error } from '../components/common/Error';
import { Button } from '../components/common/Button';
import './StoryDetail.css';

export const StoryDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const storyId = id ? parseInt(id, 10) : null;

  const {
    data: story,
    loading: storyLoading,
    error: storyError,
    refetch: refetchStory,
  } = useStory(storyId);

  const {
    data: commentsData,
    loading: commentsLoading,
    error: commentsError,
    refetch: refetchComments,
  } = useStoryComments(storyId, 1, 100);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [id]);

  if (storyLoading) {
    return <LoadingPage message="Loading story..." />;
  }

  if (storyError || !story) {
    return (
      <div className="story-detail__error-container">
        <Error
          message={storyError?.message || 'Story not found'}
          onRetry={refetchStory}
        />
        <Link to="/" className="story-detail__back-link">
          <Button variant="secondary">← Back to Home</Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="story-detail">
      <div className="story-detail__header">
        <Link to="/" className="story-detail__back-link">
          <Button variant="ghost" size="small">
            ← Back
          </Button>
        </Link>
      </div>

      <StoryContent story={story} />

      <div className="story-detail__comments-section">
        <div className="story-detail__comments-header">
          <h2 className="story-detail__comments-title">
            {story.commentCount} {story.commentCount === 1 ? 'Comment' : 'Comments'}
          </h2>
          {commentsError && (
            <Button
              variant="secondary"
              size="small"
              onClick={refetchComments}
            >
              Retry Loading Comments
            </Button>
          )}
        </div>

        {commentsError ? (
          <Error
            message="Failed to load comments"
            onRetry={refetchComments}
          />
        ) : commentsLoading ? (
          <div className="story-detail__comments-loading">
            <LoadingPage message="Loading comments..." />
          </div>
        ) : commentsData && commentsData.ids.length > 0 ? (
          <CommentList commentIds={commentsData.ids} />
        ) : (
          <div className="story-detail__no-comments">
            <p>No comments yet. Be the first to comment!</p>
          </div>
        )}
      </div>
    </div>
  );
};