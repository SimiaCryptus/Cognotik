import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Comment } from '../../types/api';
import { Badge } from '../common/Badge';
import { formatTimeAgo } from '../../utils/date';
import './CommentItem.css';

export interface CommentItemProps {
  comment: Comment;
  level: number;
  onToggleCollapse?: () => void;
  isCollapsed?: boolean;
}

export const CommentItem: React.FC<CommentItemProps> = ({
  comment,
  level,
  onToggleCollapse,
  isCollapsed = false,
}) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const hasChildren = comment.childCommentIds.length > 0;

  const handleToggle = () => {
    if (onToggleCollapse) {
      onToggleCollapse();
    } else {
      setIsExpanded(!isExpanded);
    }
  };

  return (
    <div className={`comment-item comment-item--level-${Math.min(level, 5)}`}>
      <div className="comment-item__header">
        <button
          className="comment-item__collapse-btn"
          onClick={handleToggle}
          aria-label={isCollapsed ? 'Expand comment' : 'Collapse comment'}
        >
          {isCollapsed ? '+' : '−'}
        </button>

        <div className="comment-item__meta">
          <Link
            to={`/user/${comment.author}`}
            className="comment-item__author"
          >
            {comment.author}
          </Link>
          <span className="comment-item__separator">•</span>
          <time
            className="comment-item__time"
            dateTime={comment.timestamp}
            title={new Date(comment.timestamp).toLocaleString()}
          >
            {formatTimeAgo(comment.timestamp)}
          </time>
          {comment.location && (
            <>
              <span className="comment-item__separator">•</span>
              <span className="comment-item__location">{comment.location}</span>
            </>
          )}
        </div>

        {comment.tags.length > 0 && (
          <div className="comment-item__tags">
            {comment.tags.slice(0, 2).map(tag => (
              <Badge key={tag} variant="secondary" size="small">
                {tag}
              </Badge>
            ))}
          </div>
        )}
      </div>

      {!isCollapsed && (
        <>
          <div
            className="comment-item__text"
            dangerouslySetInnerHTML={{ __html: comment.text }}
          />

          {hasChildren && (
            <div className="comment-item__footer">
              <span className="comment-item__reply-count">
                {comment.childCommentIds.length}{' '}
                {comment.childCommentIds.length === 1 ? 'reply' : 'replies'}
              </span>
            </div>
          )}
        </>
      )}

      {isCollapsed && hasChildren && (
        <div className="comment-item__collapsed-info">
          {comment.childCommentIds.length} hidden{' '}
          {comment.childCommentIds.length === 1 ? 'reply' : 'replies'}
        </div>
      )}
    </div>
  );
};