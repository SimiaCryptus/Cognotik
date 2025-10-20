import React from 'react';
 import './Badge.css';

 export interface BadgeProps {
  children: React.ReactNode;
  variant?: 'default' | 'primary' | 'secondary' | 'outline' | 'success' | 'warning' | 'danger' | 'info';
  size?: 'small' | 'medium' | 'large';
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'default',
  size = 'medium',
  className = '',
}) => {
  const classes = ['badge', `badge--${variant}`, `badge--${size}`, className]
    .filter(Boolean)
    .join(' ');

  return <span className={classes}>{children}</span>;
};