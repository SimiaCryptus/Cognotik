import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';
import { isArchive } from './services/appConfig';

console.log('[App] Application initializing at', new Date().toISOString(), window.location.href);
// Configure console styling for compatibility with old code
const logStyles = {
    startup: 'color: #4CAF50; font-weight: bold',
    error: 'color: #f44336; font-weight: bold',
    warning: 'color: #ff9800; font-weight: bold',
    info: 'color: #2196f3; font-weight: bold'
};
// Check if we're in archive mode
const isArchiveMode = isArchive();
console.log(`%c[Chat App] ${isArchiveMode ? 'Starting application in archive mode...' : 'Starting application...'}`, logStyles.startup);


const rootElement = document.getElementById('root');
if (!rootElement) {
    console.error('[App] Critical Error: Failed to find root element in DOM');
    throw new Error('Failed to find the root element');
}

// Function to load and execute tab_fallback.js for archive mode
function loadTabFallback() {
    console.log('%c[Chat App] Loading tab fallback functionality...', logStyles.info);
    // Prevent React from trying to render in archive mode
    if (rootElement) {
        // Clear any existing content to prevent React from trying to hydrate it
        rootElement.innerHTML = '';
    }
    
    console.log('%c[Chat App] Current DOM state:', logStyles.info, {
        tabButtons: document.querySelectorAll('.tab-button').length,
        tabContainers: document.querySelectorAll('.tabs-container').length,
        timestamp: new Date().toISOString()
    });

    // Import the tab fallback code from the old index.js
    // This is a simplified version that just handles the essential functionality
    try {
        console.log('%c[Chat App] Initializing tabs...', logStyles.info, {
            timestamp: new Date().toISOString(),
            documentReady: document.readyState
        });
        
        // Initialize tabs when DOM is ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', initializeTabs);
        } else {
            initializeTabs();
        }
    } catch (error) {
        console.error('%c[Chat App] Failed to initialize tabs:', logStyles.error, {
            error: error instanceof Error ? error.message : String(error),
            stack: error instanceof Error ? error.stack : '',
            timestamp: new Date().toISOString()
        });
    }
}

function initializeTabs() {
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', (event) => {
            try {
                event.stopPropagation();
                const forTab = button.getAttribute('data-for-tab');
                const tabsContainerId = button.closest('.tabs-container')?.id;
                
                if (tabsContainerId) {
                    localStorage.setItem(`selectedTab_${tabsContainerId}`, forTab || '');
                    const tabsParent = button.closest('.tabs-container');
                    
                    if (tabsParent) {
                        tabsParent.querySelectorAll('.tab-button').forEach(tabButton => {
                            if (tabButton.closest('.tabs-container') === tabsParent) 
                                tabButton.classList.remove('active');
                        });
                        
                        button.classList.add('active');
                        
                        tabsParent.querySelectorAll('.tab-content').forEach(content => {
                            if (content.closest('.tabs-container') === tabsParent) {
                                if (content.getAttribute('data-tab') === forTab) {
                                    content.classList.add('active');
                                    (content as HTMLElement).style.display = 'block';
                                } else {
                                    content.classList.remove('active');
                                    (content as HTMLElement).style.display = 'none';
                                }
                            }
                        });
                    }
                }
            } catch (error) {
                console.error('%c[Chat App] Error in tab click handler:', logStyles.error, {
                    error: error instanceof Error ? error.message : String(error),
                    stack: error instanceof Error ? error.stack : ''
                });
            }
        });
    });
}

// Handle different initialization paths based on archive mode
if (isArchiveMode) {
    console.log('[App] Archive mode detected, using tab fallback instead of React rendering');
    loadTabFallback();
    // Add a message to the root element to indicate archive mode
    if (rootElement) {
        rootElement.innerHTML = `
            <div style="padding: 20px; text-align: center;">
                <h2>Archive Mode</h2>
                <p>This chat is being displayed in archive mode.</p>
                <p>The interactive React application is disabled.</p>
            </div>
        `;
    }
} else {
    // Add a simple check to ensure the DOM is ready for React rendering
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            console.log('[App] DOM content loaded, now rendering app');
            renderApp();
        });
    } else {
        console.log('[App] DOM already loaded, rendering app immediately');
        renderApp();
    }
}

function renderApp() {
    // Check if root already has content (might be from a previous render attempt)
    if (rootElement && rootElement.innerHTML && rootElement.innerHTML.includes('Application Error')) {
        console.log('[App] Clearing previous error message before rendering');
        rootElement.innerHTML = '';
    }
    
    // We've already checked rootElement is not null above, so we can safely assert it here
    const root = createRoot(rootElement!);
    try {
        console.log('[App] Attempting to render application...');
        root.render(<App isArchive={isArchiveMode} />);
        console.log('[App] Application render called successfully ✅');
        
        // Add a check to verify the app is actually rendering
        setTimeout(() => {
            const appElement = document.querySelector('.App');
            if (!appElement || appElement.children.length === 0) {
                console.error('[App] Warning: App container appears empty after rendering');
                // Try to diagnose the issue
                console.log('[App] DOM structure:', document.body.innerHTML.substring(0, 500) + '...');
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
        
        // Add fallback UI in case of render failure
        // We've already checked rootElement is not null at the beginning of the file
        // but let's make sure it's still valid here
        if (rootElement) {
            rootElement.innerHTML = `
            <div style="padding: 20px; text-align: center;">
                <h2>Application Error</h2>
                <p>The application failed to load properly. Please check the console for details.</p>
                <p>Error: ${err.message}</p>
                <p><button onclick="window.location.reload()">Refresh Page</button></p>
            </div>
        `;
        }
    }
}