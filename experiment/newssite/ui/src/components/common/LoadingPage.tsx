import React from 'react';
 import { Loading } from './Loading';
 import './LoadingPage.css';

 export interface LoadingPageProps {
  text?: string;
  message?: string;
}

export const LoadingPage: React.FC<LoadingPageProps> = ({ text = 'Loading...', message }) => {
  return (
    <div className="loading-page">
      <Loading size="large" text={message || text} fullScreen />
    </div>
  );
};