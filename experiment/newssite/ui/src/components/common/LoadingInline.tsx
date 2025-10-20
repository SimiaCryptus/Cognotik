import React from 'react';
 import { Loading } from './Loading';
 import './LoadingInline.css';

 export interface LoadingInlineProps {
  text?: string;
  message?: string;
  size?: 'small' | 'medium' | 'large';
  className?: string;
}

 export const LoadingInline: React.FC<LoadingInlineProps> = ({
  text,
  message,
  size = 'medium',
  className = '',
 }) => {
  return (
    <div className={`loading-inline ${className}`}>
      <Loading size={size} text={message || text} />
    </div>
  );
};