import React from 'react';
import './Loading.css';

export interface LoadingProps {
  size?: 'small' | 'medium' | 'large';
  text?: string;
  fullScreen?: boolean;
  className?: string;
}

export const Loading: React.FC<LoadingProps> = ({
  size = 'medium',
  text,
  fullScreen = false,
  className = '',
}) => {
  const classes = [
    'loading',
    `loading--${size}`,
    fullScreen && 'loading--fullscreen',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes} role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true">
        <div className="spinner__circle"></div>
      </div>
      {text && <p className="loading__text">{text}</p>}
      <span className="sr-only">Loading...</span>
    </div>
  );
};