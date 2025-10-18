import React from 'react';
import { User } from '../../types/api';
import { Card } from '../common/Card';
import './UserStats.css';

export interface UserStatsProps {
  user: User;
}

export const UserStats: React.FC<UserStatsProps> = ({ user }) => {
  const stats = [
    {
      label: 'Karma',
      value: user.karma.toLocaleString(),
      icon: (
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"
            fill="currentColor"
          />
        </svg>
      ),
    },
    {
      label: 'Submissions',
      value: user.submittedIds.length.toLocaleString(),
      icon: (
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path
            d="M19 3H5C3.9 3 3 3.9 3 5V19C3 20.1 3.9 21 5 21H19C20.1 21 21 20.1 21 19V5C21 3.9 20.1 3 19 3ZM14 17H7V15H14V17ZM17 13H7V11H17V13ZM17 9H7V7H17V9Z"
            fill="currentColor"
          />
        </svg>
      ),
    },
  ];

  return (
    <div className="user-stats">
      {stats.map((stat) => (
        <Card key={stat.label} className="user-stats__card">
          <div className="user-stats__icon">{stat.icon}</div>
          <div className="user-stats__content">
            <div className="user-stats__value">{stat.value}</div>
            <div className="user-stats__label">{stat.label}</div>
          </div>
        </Card>
      ))}
    </div>
  );
};