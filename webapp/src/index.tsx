import {Provider} from 'react-redux';
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import {store} from './store';
import './index.css';
import mermaid from 'mermaid';

console.log('[App] Application initializing...');

const rootElement = document.getElementById('root');
if (!rootElement) {
    console.error('[App] Critical Error: Failed to find root element in DOM');
    throw new Error('Failed to find the root element');
}

const root = ReactDOM.createRoot(rootElement);
mermaid.initialize({startOnLoad: true});

try {
    root.render(
        <React.StrictMode>
        <Provider store={store}>
            <App/>
        </Provider>
        </React.StrictMode>
    );
    console.log('[App] Application started successfully ✅');
} catch (error) {

    const err = error as Error;
    console.error('[App] Critical Error: Failed to render application:', {
        error: err,
        errorMessage: err.message,
        errorStack: err.stack
    });
    throw error;
}