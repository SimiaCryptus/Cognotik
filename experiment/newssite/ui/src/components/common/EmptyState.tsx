import React from 'react';
 import { Button } from './Button';
 import './EmptyState.css';

 export interface EmptyStateProps {
  icon?: React.ReactNode;
  title?: string;
  description?: string;
  message?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
  className?: string;
}

 export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  message,
  action,
  className = '',
 }) => {
  const displayTitle = title || message || 'No items found';
  
  return (
    <div className={`empty-state ${className}`}>
      {icon && <div className="empty-state__icon">{icon}</div>}
      <h3 className="empty-state__title">{displayTitle}</h3>
      {description && <p className="empty-state__description">{description}</p>}
      {action && (
        <div className="empty-state__action">
          <Button onClick={action.onClick} variant="primary">
            {action.label}
          </Button>
        </div>
      )}
    </div>
  );
};