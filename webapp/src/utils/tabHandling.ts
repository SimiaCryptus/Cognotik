const VERBOSE_LOGGING = false //process.env.NODE_ENV === 'development';

const errors = {
    setupErrors: 0,
    restoreErrors: 0,
    saveErrors: 0,
    updateErrors: 0
};

export interface TabState {
    containerId: string;
    activeTab: string;
}

const diagnostics = {
    saveCount: 0,
    restoreCount: 0,
    restoreSuccess: 0,
    restoreFail: 0
};
const tabStateVersions = new Map<string, number>();
let currentStateVersion = 0;

export function debounce<T extends (...args: any[]) => void>(func: T, wait: number) {
    let timeout: ReturnType<typeof setTimeout>;
    return function executedFunction(this: any, ...args: Parameters<T>) {
        const later = () => {
            clearTimeout(timeout);
            func.apply(this, args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

const tabStates = new Map<string, TabState>();
let isMutating = false;
const tabStateHistory = new Map<string, string[]>();
const tabContentCache = new Map<string, HTMLElement>();

function getCacheKey(containerId: string, tabId: string): string {
    return `${containerId}::${tabId}`;
}

function cacheTabContent(container: Element, content: Element): void {
    const containerId = container.id;
    const tabId = content.getAttribute('data-tab');
    if (containerId && tabId) {
        const key = getCacheKey(containerId, tabId);
        // Clone the node before caching to avoid reference issues
        const contentClone = content.cloneNode(true) as HTMLElement;
        tabContentCache.set(key, contentClone);
        if (VERBOSE_LOGGING) {
            console.debug('[TabSystem] Cached tab content', {
                containerId,
                tabId,
                cacheKey: key,
                contentSize: contentClone.outerHTML.length
            });
        }
    }
}

function restoreCachedContent(container: Element, tabId: string): HTMLElement | null {
    const key = getCacheKey(container.id, tabId);
    return tabContentCache.get(key) || null;
}

function getActiveTab(containerId: string): string | undefined {
    return tabStates.get(containerId)?.activeTab;
}

export const setActiveTabState = (containerId: string, tabId: string): void => {
    tabStates.set(containerId, {containerId, activeTab: tabId});
};

function trackTabStateHistory(containerId: string, activeTab: string) {
    if (!tabStateHistory.has(containerId)) {
        tabStateHistory.set(containerId, []);
    }
    const history = tabStateHistory.get(containerId)!;
    if (history[history.length - 1] !== activeTab) {
        history.push(activeTab);
        if (history.length > 10) {
            history.shift();
        }
    }
}

export function saveTabState(containerId: string, activeTab: string) {
    try {
        diagnostics.saveCount++;
        currentStateVersion++;
        tabStateVersions.set(containerId, currentStateVersion);
        const state = {containerId, activeTab};
        tabStates.set(containerId, state);
        trackTabStateHistory(containerId, activeTab);
    } catch (error) {
        errors.saveErrors++;
        console.error(`Failed to save tab state:`, {
            error,
            containerId,
            activeTab,
            totalErrors: errors.saveErrors
        });
    }
}

export const getAllTabStates = (): Map<string, TabState> => {
    return new Map(tabStates);
}

export const restoreTabStates = (states: Map<string, TabState>): void => {
    states.forEach((state) => {
        tabStates.set(state.containerId, state);
        const container = document.getElementById(state.containerId);
        if (container) {
            restoreTabState(container);
        }
    });
}


export function setActiveTab(button: Element, container: Element) {
    const forTab = button.getAttribute('data-for-tab');
    if (!forTab) {
        console.warn('[TabSystem] No "data-for-tab" attribute found on button:', button);
        return;
    }
    setActiveTabState(container.id, forTab);
    saveTabState(container.id, forTab);
    // Check if we need to restore cached content
    // Find content directly under this container, not nested ones
    const existingContent = Array.from(container.children)
        .find(el => el.matches(`.tab-content[data-tab="${forTab}"]`));
    if (!existingContent) {
        const cachedContent = restoreCachedContent(container, forTab);
        if (cachedContent) {
            // Clone the node to avoid issues with it being removed from another location
            const clonedContent = cachedContent.cloneNode(true) as HTMLElement;
            container.appendChild(clonedContent);
        }
    }
    // Find the specific tabs group containing this button
    const tabsGroup = button.closest('.tabs');
    if (!tabsGroup) return;
    // Initialize any nested tabs within the current container
    tabsGroup.querySelectorAll('.tabs-container').forEach(nestedContainer => {
        setupTabContainer(nestedContainer);
    });
    // Update only buttons in this specific tabs group
    const tabButtons = tabsGroup.querySelectorAll('.tab-button');
    tabButtons.forEach(btn => {
        if (btn.getAttribute('data-for-tab') === forTab) {
            btn.classList.add('active');
            // Force a reflow to ensure styles update immediately
            void (btn as HTMLElement).offsetWidth;
        } else {
            btn.classList.remove('active');
        }
    });
    // Initialize nested tabs within the active tab content
    const activeContent = container.querySelector(`.tab-content[data-tab="${forTab}"]`);
    if (activeContent) {
        activeContent.querySelectorAll('.tabs-container').forEach(nestedContainer => {
            setupTabContainer(nestedContainer);
        });
    }

    // Find the closest tab content container to this tabs group
    const contentContainer = tabsGroup.closest('.tabs-container');
    Array.from(contentContainer?.children || [])
        .forEach(content => {
            if (!content.matches('.tab-content')) return;


            const contentElement = content as HTMLElement;
            const contentTabId = content.getAttribute('data-tab');
            if (content.getAttribute('data-tab') === forTab) {
                content.classList.add('active');
                contentElement.style.cssText = `
                    position: relative;
                    width: 100%;
                    height: auto;
                    overflow: visible;
                    pointer-events: auto;
                    opacity: 1;
                    padding: 1rem;
                `;
                // Ensure smooth transition
                requestAnimationFrame(() => {
                    contentElement.classList.add('visible');
                });
            } else {
                content.classList.remove('active', 'visible');
                contentElement.style.cssText = `
                    position: fixed;
                    width: 0;
                    height: 0;
                    overflow: hidden;
                    pointer-events: none;
                    opacity: 0;
                    padding: 0;
                    margin: 0;
                `;
                content.classList.remove('visible');
                // Cache and remove inactive content if it's not marked to keep mounted
                if (!content.hasAttribute('data-keep-mounted') && contentTabId !== forTab) {
                    content.setAttribute('data-cached', 'true');
                    cacheTabContent(container, content);
                    // Don't remove the content if we're in the process of switching to it
                    // This prevents the last tab from disappearing
                    content.remove();
                }
                if (VERBOSE_LOGGING) {
                    console.debug('[TabSystem] Tab Content Deactivated', {
                        tab: contentTabId,
                        containerId: container.id
                    });
                }
                if ((content as any)._contentObserver) {
                    console.debug('[TabSystem] Disconnecting Observer', {
                        containerId: container.id
                    });
                    (content as any)._contentObserver.disconnect();
                    delete (content as any)._contentObserver;
                }
            }
        });
}

function restoreTabState(container: Element) {
    try {
        diagnostics.restoreCount++;
        const containerId = container.id;

        const savedTab = getActiveTab(containerId);
        if (savedTab) {
            const button = container.querySelector(`.tabs .tab-button[data-for-tab="${savedTab}"]`) as HTMLElement;

            if (button) {
                setActiveTab(button, container);
                diagnostics.restoreSuccess++;
            } else {
                diagnostics.restoreFail++;
                // Fallback: look for any tab button within a .tabs group
                const firstButton = container.querySelector('.tabs .tab-button') as HTMLElement;
                if (firstButton) {
                    setActiveTab(firstButton, container);
                }
            }
            // Verify that the active tab content exists after restoration
            const activeTabContent = container.querySelector(`.tab-content[data-tab="${savedTab}"]`);
            if (!activeTabContent) {
                console.warn(`[TabSystem] Active tab content missing after restore: ${savedTab} in container ${containerId}`);
                // Try to restore from cache again
                const cachedContent = restoreCachedContent(container, savedTab);
                if (cachedContent) {
                    const clonedContent = cachedContent.cloneNode(true) as HTMLElement;
                    container.appendChild(clonedContent);
                    console.info(`[TabSystem] Successfully restored missing tab content from cache: ${savedTab}`);
                }
            }
        } else {
            diagnostics.restoreFail++;
            // Fallback: look for any tab button within a .tabs group
            const firstButton = container.querySelector('.tabs .tab-button') as HTMLElement;
            if (firstButton) {
                setActiveTab(firstButton, container);
            }
        }
    } catch (error) {
        console.error('[TabSystem] Failed to restore tab state', {
            containerId: container.id,
            error: error,
            stack: error instanceof Error ? error.stack : new Error().stack,
            diagnostics: {restoreSuccess: diagnostics.restoreSuccess, restoreFail: diagnostics.restoreFail}
        });
        diagnostics.restoreFail++;
    }
}

export function resetTabState() {
    tabStates.clear();
    tabStateHistory.clear();
    tabStateVersions.clear();
    tabContentCache.clear();
    currentStateVersion = 0;
    isMutating = false;
}


document.addEventListener('DOMContentLoaded', function () {
    initCollapsibleElements();
});

// Initialize all collapsible elements on the page
export function initCollapsibleElements() {
    document.querySelectorAll('.expandable-header').forEach(header => {
        header.addEventListener('click', (event) => {
            const clickedHeader = event.currentTarget as HTMLElement;
            const content = clickedHeader.nextElementSibling!;
            const icon = clickedHeader.querySelector('.expand-icon')!;

            // Toggle expanded state
            content.classList.toggle('expanded');
            icon.classList.toggle('expanded');

            // Update icon text
            if (icon) {
                icon.textContent = content.classList.contains('expanded') ? '▲' : '▼';
            }
        });
    });
}

// For dynamically added collapsible elements
export function initNewCollapsibleElements() {
    document.querySelectorAll('.expandable-header:not([data-initialized])').forEach(header => {
        header.setAttribute('data-initialized', 'true');
        header.addEventListener('click', (event) => {
            const clickedHeader = event.currentTarget as HTMLElement;
            const content = clickedHeader.nextElementSibling!;
            const icon = clickedHeader.querySelector('.expand-icon');
            content.classList.toggle('expanded');
            if (icon) {
                icon.classList.toggle('expanded');
                icon.textContent = content.classList.contains('expanded') ? '▲' : '▼';
            }
        });
    });
}

export const updateTabs = debounce(() => {
    if (isMutating) {
        return;
    }

    try {
        initNewCollapsibleElements()
        const currentStates = getAllTabStates();
        const processed = new Set<string>();
        const tabsContainers = Array.from(document.querySelectorAll('.tabs-container'));
        isMutating = true;
        const visibleTabs = new Set<string>();
        tabsContainers.forEach(container => {
            if (processed.has(container.id)) return;
            processed.add(container.id);
            setupTabContainer(container);
            const activeTab = getActiveTab(container.id) ||
                currentStates.get(container.id)?.activeTab ||
                container.querySelector('.tabs .tab-button.active')?.getAttribute('data-for-tab');
            if (activeTab) {
                visibleTabs.add(getCacheKey(container.id, activeTab));
                tabStates.set(container.id, {containerId: container.id, activeTab});
                restoreTabState(container);
            } else {
                const firstButton = container.querySelector('.tabs .tab-button');
                if (firstButton instanceof HTMLElement) {
                    const firstTabId = firstButton.getAttribute('data-for-tab');
                    if (firstTabId) setActiveTab(firstButton, container);
                } else {
                    console.warn(`No active tab found for container ${container.id}`);
                }
            }
        });
        // Batch removal of off-screen tab content inside a requestAnimationFrame to reduce reflow
        requestAnimationFrame(() => {
            document.querySelectorAll('.tab-content').forEach(tab => {
                const tabId = tab.getAttribute('data-tab');
                const container = tab.closest('.tabs-container');
                const containerId = container?.id;
                const cacheKey = containerId && tabId ? getCacheKey(containerId, tabId) : null;
                // Don't remove content that's currently visible or has data-keep-mounted
                if (tabId && cacheKey && !visibleTabs.has(cacheKey) && !tab.classList.contains('active') &&
                    !tab.hasAttribute('data-keep-mounted') && !tab.hasAttribute('data-cached')) {
                    if (container) {
                        setupTabContainer(container);
                        cacheTabContent(container, tab);
                    }
                    // Mark as cached before removing
                    tab.setAttribute('data-cached', 'true');
                    tab.remove();
                }
            });
        });
        processed.clear();
    } catch (error) {
        errors.updateErrors++;
        console.error(`Error during tab update:`, {error, totalErrors: errors.updateErrors});
    } finally {
        isMutating = false;
    }
}, 250);

function setupTabContainer(container: Element) {
    try {
        if (!container.id) {
            container.id = `tab-container-${Math.random().toString(36).substring(2, 11)}`;
            console.warn(`Generated missing container ID: ${container.id}`);
        }
        const HTMLElementContainer = container as HTMLElement;
        if (HTMLElementContainer.dataset.tabSystemInitialized) {
            return;
        }
        if (VERBOSE_LOGGING) console.debug(`Initializing container`, {
            existingActiveTab: getActiveTab(container.id),
            containerId: container.id,
        })
        container.addEventListener('click', (event: Event) => {
            const button = (event.target as HTMLElement).closest('.tab-button');
            if (button && container.contains(button)) {
                // Get the parent tabs group which holds the tab buttons
                const tabsGroup = button.closest('.tabs');
                if (!tabsGroup) return;
                setActiveTab(button, container);
                // Immediately update the active state for all buttons in this tabs group
                tabsGroup.querySelectorAll('.tab-button').forEach(btn => {
                    if (btn === button) {
                        btn.classList.add('active');
                        // force a reflow on the active button to help ensure the updated style is rendered
                        void (btn as HTMLElement).offsetWidth;
                    } else {
                        btn.classList.remove('active');
                    }
                });
                // Force immediate layout update then run updateTabs
                void (button as HTMLElement).offsetWidth;
                updateTabs();
                event.stopPropagation();
                event.preventDefault(); // Prevent anchor tag navigation
            }
        });
        HTMLElementContainer.dataset.tabSystemInitialized = 'true';
    } catch (error) {
        errors.setupErrors++;
        console.error(`Failed to setup tab container`, {
            error,
            containerId: container.id,
            totalErrors: errors.setupErrors
        });
        throw error;
    }
}