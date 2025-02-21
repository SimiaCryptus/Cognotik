const VERBOSE_LOGGING = process.env.NODE_ENV === 'development';

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

const tabContentCache = new Map<string, HTMLElement>();

function getCacheKey(containerId: string, tabId: string): string {
    return `${containerId}:${tabId}`;
}

function cacheTabContent(container: Element, tabContent: Element) {
    const containerId = container.id;
    const tabId = tabContent.getAttribute('data-tab');
    if (tabId) {
        const clonedContent = tabContent.cloneNode(true) as HTMLElement;
        clonedContent.setAttribute('data-cached', 'true');
        // Remove the initialization flag from nested tab containers
        clonedContent.querySelectorAll('.tabs-container').forEach(nested => {
            nested.removeAttribute('data-tab-system-initialized');
        });
        const cacheKey = getCacheKey(containerId, tabId);
        tabContentCache.set(cacheKey, clonedContent);
    }
}

function restoreCachedContent(container: Element, tabId: string): HTMLElement | null {
    const cacheKey = getCacheKey(container.id, tabId);
    const cachedContent = tabContentCache.get(cacheKey);
    if (cachedContent) {
        const restoredContent = cachedContent.cloneNode(true) as HTMLElement;
        restoredContent.setAttribute('data-cached', 'true');
        return restoredContent;
    }
    return null;
}

export function setActiveTab(button: Element, container: Element) {
    const forTab = button.getAttribute('data-for-tab');
    if (!forTab) return;
    setActiveTabState(container.id, forTab);
    saveTabState(container.id, forTab);
    // Check if we need to restore cached content
    // Find content directly under this container, not nested ones
    const existingContent = Array.from(container.children)
        .find(el => el.matches(`.tab-content[data-tab="${forTab}"]`));
    if (!existingContent) {
        const cachedContent = restoreCachedContent(container, forTab);
        if (cachedContent) {
            container.appendChild(cachedContent);
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
    tabsGroup.querySelectorAll('.tab-button').forEach(btn => {
        if (!btn.matches('.tab-button')) return;
        if (btn.getAttribute('data-for-tab') === forTab) {
            btn.classList.add('active');
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
                contentElement.style.display = 'block';
                // Ensure smooth transition
                requestAnimationFrame(() => {
                    contentElement.classList.add('visible');
                    contentElement.style.opacity = '1';
                });
            } else {
                content.classList.remove('active');
                content.classList.remove('visible');
                // Cache and remove inactive content if it's not marked to keep mounted
                if (!content.hasAttribute('data-keep-mounted')) {
                    content.setAttribute('data-cached', 'true');
                    cacheTabContent(container, content);
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
            const tabsContainer = container.querySelector(':scope > .tabs');
            const button = tabsContainer?.querySelector(`.tab-button[data-for-tab="${savedTab}"]`) as HTMLElement;
            if (button) {
                setActiveTab(button, container);
                diagnostics.restoreSuccess++;
            } else {
                diagnostics.restoreFail++;
            }
        } else {
            diagnostics.restoreFail++;
            const firstButton = container.querySelector('.tab-button') as HTMLElement;
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

export const updateTabs = debounce(() => {
    if (isMutating) {
        return;
    }


    try {
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
                tabStates.set(container.id, { containerId: container.id, activeTab });
                restoreTabState(container);
            } else {
                const firstButton = container.querySelector(':scope > .tabs > .tab-button');
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
                if (tabId && cacheKey && !visibleTabs.has(cacheKey) &&
                    !tab.hasAttribute('data-keep-mounted') && !tab.hasAttribute('data-cached')) {
                    if (container) {
                        setupTabContainer(container);
                        cacheTabContent(container, tab);
                    }
                    tab.remove();
                }
            });
        });
        processed.clear();
    } catch (error) {
        errors.updateErrors++;
        console.error(`Error during tab update:`, { error, totalErrors: errors.updateErrors });
    } finally {
        isMutating = false;
    }
}, 250);
                // Wrap updateTabs in requestAnimationFrame to batch DOM updates
                requestAnimationFrame(() => updateTabs());


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
                // Get the specific tabs-container for this button's tab group
                const buttonContainer = button.closest('.tabs-container');
                if (!buttonContainer) return;
                setActiveTab(button, container);
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