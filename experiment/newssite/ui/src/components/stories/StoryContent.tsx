import React from 'react';
import { Story } from '../../types/api';
import { StoryMeta } from './StoryMeta';
import { Badge } from '../common/Badge';
import { formatUrl } from '../../utils/url';
import './StoryContent.css';

export interface StoryContentProps {
  story: Story;
}

export const StoryContent: React.FC<StoryContentProps> = ({ story }) => {
  const isExternalLink = story.url !== null;
  const domain = story.url ? formatUrl(story.url) : null;

  return (
    <article className="story-content">
      <header className="story-content__header">
        <h1 className="story-content__title">
          {isExternalLink ? (
            <>
              <a
                href={story.url!}
                target="_blank"
                rel="noopener noreferrer"
                className="story-content__link"
              >
                {story.title}
              </a>
              {domain && (
                <span className="story-content__domain">({domain})</span>
              )}
            </>
          ) : (
            story.title
          )}
        </h1>

        {(story.tags.length > 0 || story.topics.length > 0) && (
          <div className="story-content__badges">
            {story.tags.map(tag => (
              <Badge key={tag} variant="primary" size="medium">
                {tag}
              </Badge>
            ))}
            {story.topics.map(topic => (
              <Badge key={topic} variant="secondary" size="medium">
                {topic}
              </Badge>
            ))}
          </div>
        )}

        <StoryMeta story={story} showCommentCount={false} />
      </header>

      {story.text && (
        <div
          className="story-content__text"
          dangerouslySetInnerHTML={{ __html: story.text }}
        />
      )}

      {story.location && (
        <div className="story-content__location">
          <svg
            className="story-content__location-icon"
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M8 0C5.24 0 3 2.24 3 5C3 8.5 8 14 8 14C8 14 13 8.5 13 5C13 2.24 10.76 0 8 0ZM8 6.5C7.17 6.5 6.5 5.83 6.5 5C6.5 4.17 7.17 3.5 8 3.5C8.83 3.5 9.5 4.17 9.5 5C9.5 5.83 8.83 6.5 8 6.5Z"
              fill="currentColor"
            />
          </svg>
          {story.location}
        </div>
      )}
    </article>
  );
};