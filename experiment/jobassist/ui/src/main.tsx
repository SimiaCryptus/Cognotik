import React from 'react';
import App from './App';

/**
 * Application entry point
 * Initializes React and mounts the root component
 */
const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Failed to find the root element');
}

