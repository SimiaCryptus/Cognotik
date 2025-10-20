import React from 'react';
import { User } from '../../types/api';
import { Badge } from '../common/Badge';
import { formatDate } from '../../utils/date';
import './UserInfo.css';

export interface UserInfoProps {
  user: User;
}

export const UserInfo: React.FC<UserInfoProps> = ({ user }) => {
  return (
    <div className="user-info">
      <div className="user-info__header">
        <h1 className="user-info__username">{user.id}</h1>
        <div className="user-info__meta">
          <span className="user-info__joined">
            Joined {formatDate(user.created)}
          </span>
        </div>
      </div>

      {user.about && (
        <div className="user-info__about">
          <p>{user.about}</p>
        </div>
      )}

      {user.location && (
        <div className="user-info__location">
          <svg
            className="user-info__location-icon"
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
          <span>{user.location}</span>
        </div>
      )}

      {user.topics.length > 0 && (
        <div className="user-info__topics">
          <h3 className="user-info__section-title">Interests</h3>
          <div className="user-info__badges">
            {user.topics.map((topic) => (
              <Badge key={topic} variant="secondary" size="small">
                {topic}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {user.tags.length > 0 && (
        <div className="user-info__tags">
          <h3 className="user-info__section-title">Tags</h3>
          <div className="user-info__badges">
            {user.tags.map((tag) => (
              <Badge key={tag} variant="outline" size="small">
                {tag}
              </Badge>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};