import React from 'react';
 import { Button } from './Button';
 import './Error.css';

 export interface ErrorProps {
  title?: string;
  message: string;
  onRetry?: () => void;
  retryText?: string;
  className?: string;
  variant?: 'inline' | 'page';
}

 export const Error: React.FC<ErrorProps> = ({
  title = 'Something went wrong',
  message,
  onRetry,
  retryText = 'Try Again',
  className = '',
  variant = 'inline',
 }) => {
  const classes = ['error', `error--${variant}`, className].filter(Boolean).join(' ');

  return (
    <div className={classes} role="alert">
      <div className="error__icon" aria-hidden="true">
        <svg
          width="48"
          height="48"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      </div>
      <div className="error__content">
        <h3 className="error__title">{title}</h3>
        <p className="error__message">{message}</p>
        {onRetry && (
          <div className="error__actions">
            <Button onClick={onRetry} variant="primary">
              {retryText}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};