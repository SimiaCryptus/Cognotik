import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../components/common/Button';
import './NotFound.css';

export const NotFound: React.FC = () => {
  return (
    <div className="not-found">
      <div className="not-found__content">
        <div className="not-found__icon">
          <svg
            width="120"
            height="120"
            viewBox="0 0 120 120"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <circle
              cx="60"
              cy="60"
              r="50"
              stroke="currentColor"
              strokeWidth="4"
              fill="none"
            />
            <path
              d="M40 50C40 47.7909 41.7909 46 44 46H46C48.2091 46 50 47.7909 50 50V52C50 54.2091 48.2091 56 46 56H44C41.7909 56 40 54.2091 40 52V50Z"
              fill="currentColor"
            />
            <path
              d="M70 50C70 47.7909 71.7909 46 74 46H76C78.2091 46 80 47.7909 80 50V52C80 54.2091 78.2091 56 76 56H74C71.7909 56 70 54.2091 70 52V50Z"
              fill="currentColor"
            />
            <path
              d="M40 75C40 75 45 70 60 70C75 70 80 75 80 75"
              stroke="currentColor"
              strokeWidth="4"
              strokeLinecap="round"
            />
          </svg>
        </div>

        <h1 className="not-found__title">404</h1>
        <h2 className="not-found__subtitle">Page Not Found</h2>
        <p className="not-found__message">
          Sorry, the page you're looking for doesn't exist or has been moved.
        </p>

        <div className="not-found__actions">
          <Link to="/">
            <Button variant="primary" size="large">
              Go to Home
            </Button>
          </Link>
          <Button
            variant="secondary"
            size="large"
            onClick={() => window.history.back()}
          >
            Go Back
          </Button>
        </div>
      </div>
    </div>
  );
};