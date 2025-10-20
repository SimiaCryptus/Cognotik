import React, { useState } from 'react';
import { Comment } from '../../types/api';
import { CommentItem } from './CommentItem';
import './CommentThread.css';

export interface CommentThreadProps {
  comment: Comment;
  allComments: Map<number, Comment>;
  level: number;
  maxLevel?: number;
}

export const CommentThread: React.FC<CommentThreadProps> = ({
  comment,
  allComments,
  level,
  maxLevel = 10,
}) => {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const hasChildren = comment.childCommentIds.length > 0;
  const shouldNest = level < maxLevel;

  return (
    <div className="comment-thread">
      <CommentItem
        comment={comment}
        level={level}
        onToggleCollapse={() => setIsCollapsed(!isCollapsed)}
        isCollapsed={isCollapsed}
      />

      {!isCollapsed && hasChildren && shouldNest && (
        <div className="comment-thread__children">
          {comment.childCommentIds.map(childId => {
            const childComment = allComments.get(childId);
            if (!childComment) return null;

            return (
              <CommentThread
                key={childId}
                comment={childComment}
                allComments={allComments}
                level={level + 1}
                maxLevel={maxLevel}
              />
            );
          })}
        </div>
      )}
    </div>
  );
};