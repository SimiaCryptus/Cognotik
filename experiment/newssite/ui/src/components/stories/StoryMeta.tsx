import React from 'react';
 import { Link } from 'react-router-dom';
 import { Story } from '../../types/api';
 import { formatTimeAgo } from '../../utils/date';
 import './StoryMeta.css';

 export interface StoryMetaProps {
  story: Story;
  showCommentCount?: boolean;
  className?: string;
}

export const StoryMeta: React.FC<StoryMetaProps> = ({ 
  story, 
  showCommentCount = true,
  className = '' 
}) => {
  const timeAgo = formatTimeAgo(story.timestamp);

  return (
    <div className={`story-meta ${className}`}>
      <span className="story-meta__item story-meta__score">
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
        </svg>
        <span>{story.score} points</span>
      </span>

      <span className="story-meta__separator">•</span>

      <Link to={`/user/${story.author}`} className="story-meta__item story-meta__author">
        by {story.author}
      </Link>

      <span className="story-meta__separator">•</span>

      <time className="story-meta__item story-meta__time" dateTime={story.timestamp}>
        {timeAgo}
      </time>

      {showCommentCount && (
        <>
          <span className="story-meta__separator">•</span>

          <Link to={`/story/${story.id}`} className="story-meta__item story-meta__comments">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            <span>
              {story.commentCount} {story.commentCount === 1 ? 'comment' : 'comments'}
            </span>
          </Link>
        </>
      )}

      {story.location && (
        <>
          <span className="story-meta__separator">•</span>
          <span className="story-meta__item story-meta__location">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
              <circle cx="12" cy="10" r="3" />
            </svg>
            <span>{story.location}</span>
          </span>
        </>
      )}

      {story.topics.length > 0 && (
        <>
          <span className="story-meta__separator">•</span>
          <span className="story-meta__item story-meta__topics">
            {story.topics.slice(0, 2).join(', ')}
          </span>
        </>
      )}
    </div>
  );
};