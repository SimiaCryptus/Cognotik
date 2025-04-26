import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';

console.log('[App] Application initializing at', new Date().toISOString());

const rootElement = document.getElementById('root');
if (!rootElement) {
    console.error('[App] Critical Error: Failed to find root element in DOM');
    throw new Error('Failed to find the root element');
}
// Add a simple check to ensure the DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        console.log('[App] DOM content loaded, now rendering app');
        renderApp();
    });
} else {
    renderApp();
}
function renderApp() {

const root = (rootElement) ? createRoot(rootElement) : null;
try {
    console.log('[App] Attempting to render application...');
    root?.render(<App />);
    console.log('[App] Application render called successfully ✅');
    
    // Add a check to verify the app is actually rendering
    setTimeout(() => {
        const appElement = document.querySelector('.App');
        if (!appElement || appElement.children.length === 0) {
            console.error('[App] Warning: App container appears empty after rendering');
        } else {
            console.log('[App] Verified App container has content');
        }
    }, 100);
} catch (error) {
    // Type guard to check if error is an Error object
    const err = error as Error;
    console.error('[App] Critical Error: Failed to render application:', {
        error: err,
        errorMessage: err.message,
        errorStack: err.stack
    });
    throw error;
}
}