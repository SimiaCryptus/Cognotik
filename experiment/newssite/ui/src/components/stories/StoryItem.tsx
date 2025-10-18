import React from 'react';
import { Link } from 'react-router-dom';
import { Story } from '../../types/api';
import { StoryMeta } from './StoryMeta';
import { Badge } from '../common/Badge';
import { formatUrl } from '../../utils/url';
import './StoryItem.css';

export interface StoryItemProps {
  story: Story;
  rank?: number;
  showFullContent?: boolean;
  className?: string;
}

export const StoryItem: React.FC<StoryItemProps> = ({
  story,
  rank,
  showFullContent = false,
  className = '',
}) => {
  const isExternalLink = story.url !== null;
  const domain = story.url ? formatUrl(story.url) : null;

  return (
    <article className={`story-item ${className}`}>
      <div className="story-item__content">
        {rank && <div className="story-item__rank">{rank}.</div>}

        <div className="story-item__main">
          <div className="story-item__header">
            <h2 className="story-item__title">
              {isExternalLink ? (
                <>
                  <a
                    href={story.url!}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="story-item__link story-item__link--external"
                  >
                    {story.title}
                  </a>
                  {domain && (
                    <span className="story-item__domain">({domain})</span>
                  )}
                </>
              ) : (
                <Link
                  to={`/story/${story.id}`}
                  className="story-item__link"
                >
                  {story.title}
                </Link>
              )}
            </h2>

            {story.tags.length > 0 && (
              <div className="story-item__tags">
                {story.tags.slice(0, 3).map(tag => (
                  <Badge key={tag} variant="secondary" size="small">
                    {tag}
                  </Badge>
                ))}
              </div>
            )}
          </div>

          {showFullContent && story.text && (
            <div
              className="story-item__text"
              dangerouslySetInnerHTML={{ __html: story.text }}
            />
          )}

          <StoryMeta story={story} />
        </div>
      </div>
    </article>
  );
};