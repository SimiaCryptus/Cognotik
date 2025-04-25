import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';



// Configure console styling
const logStyles = {
    startup: 'color: #4CAF50; font-weight: bold',
    error: 'color: #f44336; font-weight: bold',
    warning: 'color: #ff9800; font-weight: bold',
    info: 'color: #2196f3; font-weight: bold'
};


// Application initialization timestamp
const startTime = performance.now();
console.log('%c[Chat App] Application initialization started at:', logStyles.info, new Date().toISOString());

// Function to load and execute tab_fallback.js
function loadTabFallback() {
    console.log('%c[Chat App] Loading tab fallback functionality...', logStyles.info);
    console.log('%c[Chat App] Current DOM state:', logStyles.info, {
        tabButtons: document.querySelectorAll('.tab-button').length,
        tabContainers: document.querySelectorAll('.tabs-container').length,
        timestamp: new Date().toISOString()
    });

    function updateTabs() {
        try {
            document.querySelectorAll('.tab-button').forEach(button => {
                const tabId = button.getAttribute('data-for-tab');
                console.log('%c[Chat App] Adding click listener to tab:', logStyles.info, {
                    tabId,
                    buttonElement: button,
                    containerElement: button.closest('.tabs-container'),
                    timestamp: new Date().toISOString()
                });
                button.addEventListener('click', (event) => {
                    try {
                        console.log('%c[Chat App] Tab clicked:', logStyles.info, {
                            tab: button.getAttribute('data-for-tab'),
                            element: button,
                            container: button.closest('.tabs-container').id,
                            timestamp: new Date().toISOString()
                        });
                        event.stopPropagation();
                        const forTab = button.getAttribute('data-for-tab');
                        const tabsContainerId = button.closest('.tabs-container').id;
                        console.log('%c[Chat App] Saving tab state:', logStyles.info, {
                            containerId: tabsContainerId,
                            selectedTab: forTab,
                            previousTab: localStorage.getItem(`selectedTab_${tabsContainerId}`),
                            timestamp: new Date().toISOString()
                        });
                        localStorage.setItem(`selectedTab_${tabsContainerId}`, forTab);
                        let tabsParent = button.closest('.tabs-container');
                        const activeButtons = Array.from(tabsParent.querySelectorAll('.tab-button.active'))
                            .map(btn => btn.getAttribute('data-for-tab'));
                        console.log('%c[Chat App] Currently active buttons:', logStyles.info, activeButtons);

                        tabsParent.querySelectorAll('.tab-button').forEach(tabButton => {
                            if (tabButton.closest('.tabs-container') === tabsParent) tabButton.classList.remove('active');
                        });
                        button.classList.add('active');
                        console.log('%c[Chat App] Tab activated:', logStyles.info, {
                            tab: forTab,
                            container: tabsContainerId,
                            timestamp: new Date().toISOString()
                        });
                        let selectedContent = null;
                        tabsParent.querySelectorAll('.tab-content').forEach(content => {
                            if (content.closest('.tabs-container') === tabsParent) {
                                if (content.getAttribute('data-tab') === forTab) {
                                    content.classList.add('active');
                                    content.style.display = 'block'; // Ensure the content is displayed
                                    console.log('%c[Chat App] Tab content displayed:', logStyles.info, {
                                        tab: forTab,
                                        content: content.innerHTML.substring(0, 100) + '...',
                                        timestamp: new Date().toISOString()
                                    });
                                    selectedContent = content;
                                } else {
                                    content.classList.remove('active');
                                    content.style.display = 'none'; // Ensure the content is hidden
                                    console.log('%c[Chat App] Tab content hidden:', logStyles.info, {
                                        tab: content.getAttribute('data-tab'),
                                        timestamp: new Date().toISOString()
                                    });
                                }
                            }
                        });
                        if (selectedContent !== null) updateNestedTabs(selectedContent);
                    } catch (error) {
                        console.error('%c[Chat App] Error in tab click handler:', logStyles.error, {
                            error: error.message,
                            stack: error.stack,
                            tab: button.getAttribute('data-for-tab'),
                            container: button.closest('.tabs-container')?.id
                        });
                    }
                });
                // Check if the current button should be activated based on localStorage
                const savedTab = localStorage.getItem(`selectedTab_${button.closest('.tabs-container').id}`);
                console.log('%c[Chat App] Checking saved tab state:', logStyles.info, {
                    container: button.closest('.tabs-container').id,
                    savedTab: savedTab,
                    buttonTab: button.getAttribute('data-for-tab'),
                    timestamp: new Date().toISOString()
                });
                if (button.getAttribute('data-for-tab') === savedTab) {
                    button.dispatchEvent(new Event('click'));
                }
            });
        } catch (error) {
            console.error('%c[Chat App] Fatal error in updateTabs:', logStyles.error, {
                error: error.message,
                stack: error.stack,
                timestamp: new Date().toISOString()
            });
        }
    }

    function updateNestedTabs(element) {
        console.log('%c[Chat App] Updating nested tabs for element:', logStyles.info, element);
        element.querySelectorAll('.tabs-container').forEach(tabsContainer => {
            try {
                console.log('%c[Chat App] Processing nested tab container:', logStyles.info, tabsContainer.id);
                let hasActiveButton = false;
                tabsContainer.querySelectorAll('.tab-button').forEach(nestedButton => {
                    if (nestedButton.classList.contains('active')) {
                        hasActiveButton = true;
                        console.log('%c[Chat App] Found active nested button:', logStyles.info, nestedButton.getAttribute('data-for-tab'));
                    }
                });
                if (!hasActiveButton) {
                    /* Determine if a tab-content element in this tabs-container has the active class. If so, use its data-tab value to find the matching button and ensure it is marked active */
                    const activeContent = tabsContainer.querySelector('.tab-content.active');
                    if (activeContent) {
                        const activeTab = activeContent.getAttribute('data-tab');
                        const activeButton = tabsContainer.querySelector(`.tab-button[data-for-tab="${activeTab}"]`);
                        if (activeButton !== null) {
                            activeButton.classList.add('active');
                            console.log('%c[Chat App] Activated nested button:', logStyles.info, activeTab);
                        }
                    } else {
                        /* Add 'active' to the class list of the first button */
                        const firstButton = tabsContainer.querySelector('.tab-button');
                        if (firstButton !== null) {
                            firstButton.classList.add('active');
                            console.log('%c[Chat App] Activated first nested button:', logStyles.info, firstButton.getAttribute('data-for-tab'));
                        }
                    }
                }
                const savedTab = localStorage.getItem(`selectedTab_${tabsContainer.id}`);
                // console.log(`Retrieved saved tab from localStorage: selectedTab_${tabsContainer.id} = ${savedTab}`);
                if (savedTab) {
                    const savedButton = tabsContainer.querySelector(`.tab-button[data-for-tab="${savedTab}"]`);
                    if (savedButton) {
                        savedButton.classList.add('active');
                        const forTab = savedButton.getAttribute('data-for-tab');
                        const selectedContent = tabsContainer.querySelector(`.tab-content[data-tab="${forTab}"]`);
                        if (selectedContent) {
                            selectedContent.classList.add('active');
                            selectedContent.style.display = 'block';
                        }
                        // console.log(`Restored saved tab: ${savedTab}`);
                    }
                }
            } catch (e) {
                console.error('%c[Chat App] Error updating nested tabs:', logStyles.error, {
                    error: e.message,
                    stack: e.stack,
                    container: tabsContainer.id
                });
            }
        });
    }

    try {
        console.log('%c[Chat App] Initializing tabs...', logStyles.info, {
            timestamp: new Date().toISOString(),
            documentReady: document.readyState
        });
        updateTabs();
    } catch (error) {
        console.error('%c[Chat App] Failed to initialize tabs:', logStyles.error, {
            error: error.message,
            stack: error.stack,
            timestamp: new Date().toISOString()
        });
    }
}


// Check if we're loading from an archive based on current document length
const isArchive = document.documentElement.outerHTML.length > 60000;

if (!isArchive) {
    console.log('%c[Chat App] Starting application...', logStyles.startup);
} else {
    console.log('%c[Chat App] Starting application in archive mode...', logStyles.startup);
    loadTabFallback();
}


if (typeof document !== 'undefined') {
    if (!isArchive) {
        console.log('%c[Chat App] Initializing React root element...', logStyles.info);
        const root = ReactDOM.createRoot(document.getElementById('root'));
        try {
            root.render(
                <React.StrictMode>
                    <App isArchive={isArchive}/>
                </React.StrictMode>
            );
            const renderTime = (performance.now() - startTime).toFixed(2);
            console.log(
                '%c[Chat App] Application rendered successfully in %cms',
                logStyles.startup,
                renderTime
            );
        } catch (error) {
            console.log(
                '%c[Chat App] Failed to render application:',
                logStyles.error,
                '\nError:', error,
                '\nStack:', error.stack
            );
        }
    }
} else {
    console.log(
        '%c[Chat App] Document is undefined - application may be running in a non-browser environment',
        logStyles.warning
    );
}