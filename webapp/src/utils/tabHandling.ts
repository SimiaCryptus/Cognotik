const VERBOSE_LOGGING = false


const DEBUG_TAB_SYSTEM = false;


const MAX_CACHE_SIZE = 1000;

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

function logTabCacheState(context: string): void {
    if (DEBUG_TAB_SYSTEM) {
        console.debug('[TabSystem] Cache state: ' + context, {
            context,
            cacheSize: tabContentCache.size,

            cacheKeysCount: tabContentCache.size,
            tabStatesSize: tabStates.size,

            containerIds: Array.from(tabStates.keys())
        });
    }
}

function getCacheKey(containerId: string, tabId: string): string {


    return `tab-${containerId}::${tabId}`;
}

function cacheTabContent(container: Element, content: Element): void {
    const containerId = container.id;
    const tabId = content.getAttribute('data-tab');
    if (containerId && tabId) {
        const key = getCacheKey(containerId, tabId);

        const contentClone = content.cloneNode(true) as HTMLElement;

        contentClone.setAttribute('data-cached-at', new Date().toISOString());
        contentClone.setAttribute('data-source-container', containerId);

        if (tabContentCache.size > MAX_CACHE_SIZE) {

            const keysToDelete = Array.from(tabContentCache.keys()).slice(0, Math.floor(MAX_CACHE_SIZE * 0.2));
            keysToDelete.forEach(k => tabContentCache.delete(k));
            console.warn('[TabSystem] Cache size limit reached, pruned oldest entries');
        }

        tabContentCache.set(key, contentClone);
        if (VERBOSE_LOGGING || DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Cached tab content for ' + tabId, {
                containerId,
                tabId,
                contentSize: contentClone.outerHTML.length < 1000 ? contentClone.outerHTML.length : '> 1KB'
            });
        }
    }
}

function restoreCachedContent(container: Element, tabId: string): HTMLElement | null {
    const key = getCacheKey(container.id, tabId);
    const cachedContent = tabContentCache.get(key);

    if (DEBUG_TAB_SYSTEM) {
        console.debug('[TabSystem] Restore cache: ' + (cachedContent ? 'hit' : 'miss') + ' for ' + tabId, {
            containerId: container.id,
            tabId,
            cachedAt: cachedContent?.getAttribute('data-cached-at')?.split('T')[0] || 'N/A'
        });
    }

    return cachedContent || null;
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
        if (DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Saved tab state for ' + containerId, {
                containerId,
                activeTab,
                version: currentStateVersion
            });
        }
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
    if (DEBUG_TAB_SYSTEM) {
        console.debug('[TabSystem] Restoring ' + states.size + ' tab states', {
            containerIds: Array.from(states.keys())
        });
    }

    states.forEach((state) => {
        tabStates.set(state.containerId, state);
        const container = document.getElementById(state.containerId);
        if (container) {
            restoreTabState(container);
        } else if (DEBUG_TAB_SYSTEM) {
            console.warn('[TabSystem] Container not found when restoring state', {
                containerId: state.containerId,
                activeTab: state.activeTab
            });
        }
    });
}

export function setActiveTab(button: Element, container: Element) {
    const forTab = button.getAttribute('data-for-tab');
    if (!forTab) {
        console.warn('[TabSystem] No "data-for-tab" attribute found on button:', button);
        return;
    }
    if (DEBUG_TAB_SYSTEM) {
        console.debug('[TabSystem] Setting active tab: ' + forTab + ' in ' + container.id);
    }

    setActiveTabState(container.id, forTab);
    saveTabState(container.id, forTab);


    const existingContent = Array.from(container.children)
        .find(el => el.matches(`.tab-content[data-tab="${forTab}"]`));

    if (!existingContent) {
        if (DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Restoring ' + forTab + ' from cache for ' + container.id);
        }

        const cachedContent = restoreCachedContent(container, forTab);
        if (cachedContent) {

            const clonedContent = cachedContent.cloneNode(true) as HTMLElement;
            clonedContent.setAttribute('data-restored-at', new Date().toISOString());
            clonedContent.setAttribute('data-restored-from-cache', 'true');
            container.appendChild(clonedContent);
            if (DEBUG_TAB_SYSTEM) {
                console.debug('[TabSystem] Restored ' + forTab + ' from cache');
            }
        } else {
            console.warn('[TabSystem] Failed to restore tab content - not found in cache', {
                containerId: container.id,
                tabId: forTab,
                availableCacheKeys: Array.from(tabContentCache.keys())
            });
        }
    }

    const tabsGroup = button.closest('.tabs');
    if (!tabsGroup) return;

    tabsGroup.querySelectorAll('.tabs-container').forEach(nestedContainer => {
        setupTabContainer(nestedContainer);
    });

    const tabButtons = tabsGroup.querySelectorAll('.tab-button');
    tabButtons.forEach(btn => {
        if (btn.getAttribute('data-for-tab') === forTab) {
            btn.classList.add('active');

            void (btn as HTMLElement).offsetWidth;
        } else {
            btn.classList.remove('active');
        }
    });

    const activeContent = container.querySelector(`.tab-content[data-tab="${forTab}"]`);
    if (activeContent) {
        activeContent.querySelectorAll('.tabs-container').forEach(nestedContainer => {
            setupTabContainer(nestedContainer);
        });
    }



    Array.from(container.children || [])
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

                if (!content.hasAttribute('data-keep-mounted') && contentTabId && contentTabId !== forTab) {
                    content.setAttribute('data-cached', 'true');
                    cacheTabContent(container, content);


                    content.remove();
                    if (DEBUG_TAB_SYSTEM) {
                        console.debug('[TabSystem] Removed inactive tab: ' + contentTabId);
                    }
                }
                if (VERBOSE_LOGGING || DEBUG_TAB_SYSTEM) {
                    console.debug('[TabSystem] Deactivated: ' + contentTabId);
                }
                if ((content as any)._contentObserver) {
                    console.debug('[TabSystem] Disconnecting Observer');
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
        if (DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Restoring state: ' + savedTab + ' for ' + containerId);
        }

        if (savedTab) {
            const button = container.querySelector(`.tabs .tab-button[data-for-tab="${savedTab}"]`) as HTMLElement;

            if (button) {
                if (DEBUG_TAB_SYSTEM) {
                    console.debug('[TabSystem] Found button for: ' + savedTab);
                }

                setActiveTab(button, container);
                diagnostics.restoreSuccess++;
            } else {
                diagnostics.restoreFail++;
                console.warn('[TabSystem] Button for saved tab not found', {
                    containerId,
                    savedTab,
                    availableButtons: Array.from(container.querySelectorAll('.tabs .tab-button'))
                        .map(btn => ({
                            forTab: btn.getAttribute('data-for-tab'),
                            text: btn.textContent?.trim() || 'unknown'
                        }))
                });

                const firstButton = container.querySelector('.tabs .tab-button') as HTMLElement;
                if (firstButton) {
                    console.info('[TabSystem] Using first available tab button as fallback', {
                        containerId,
                        buttonForTab: firstButton.getAttribute('data-for-tab'),
                        buttonText: firstButton.textContent?.trim() || 'unknown'
                    });
                    setActiveTab(firstButton, container);
                }
            }

            const activeTabContent = container.querySelector(`.tab-content[data-tab="${savedTab}"]`);
            if (!activeTabContent) {
                console.warn(`[TabSystem] Active tab content missing after restore`, {
                    tabId: savedTab,
                    containerId,
                    availableTabContents: Array.from(container.querySelectorAll('.tab-content'))
                        .map(content => content.getAttribute('data-tab'))
                });

                const cachedContent = restoreCachedContent(container, savedTab);
                if (cachedContent) {
                    const clonedContent = cachedContent.cloneNode(true) as HTMLElement;
                    clonedContent.setAttribute('data-emergency-restore', 'true');
                    clonedContent.setAttribute('data-restored-at', new Date().toISOString());
                    container.appendChild(clonedContent);
                    console.info(`[TabSystem] Successfully restored missing tab content from cache in emergency restore`, {
                        tabId: savedTab,
                        containerId,
                        contentSize: clonedContent.outerHTML.length
                    });
                } else {

                    console.error(`[TabSystem] Failed to restore tab content from cache - creating placeholder`, {
                        tabId: savedTab,
                        containerId,
                        availableCacheKeys: Array.from(tabContentCache.keys())
                    });

                    const placeholderContent = document.createElement('div');
                    placeholderContent.className = 'tab-content active visible';
                    placeholderContent.setAttribute('data-tab', savedTab);
                    placeholderContent.setAttribute('data-placeholder', 'true');
                    placeholderContent.innerHTML = `
                        <div class="tab-content-placeholder">
                            <h3>Tab Content Unavailable</h3>
                            <p>The content for this tab could not be loaded.</p>
                        </div>
                    `;
                    container.appendChild(placeholderContent);
                }
            }
        } else {
            diagnostics.restoreFail++;
            console.warn('[TabSystem] No saved tab found for container', {
                containerId
            });

            const firstButton = container.querySelector('.tabs .tab-button') as HTMLElement;
            if (firstButton) {
                console.info('[TabSystem] Using first available tab button as fallback (no saved state)', {
                    containerId,
                    buttonForTab: firstButton.getAttribute('data-for-tab'),
                    buttonText: firstButton.textContent?.trim() || 'unknown'
                });
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
    if (DEBUG_TAB_SYSTEM) {
        console.debug('[TabSystem] Resetting tab state - clearing ' +

            tabStates.size + ' states, ' +

            tabContentCache.size + ' cached items');
    }

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

export function initCollapsibleElements() {
    document.querySelectorAll('.expandable-header').forEach(header => {
        header.addEventListener('click', (event) => {
            const clickedHeader = event.currentTarget as HTMLElement;
            const content = clickedHeader.nextElementSibling!;
            const icon = clickedHeader.querySelector('.expand-icon')!;

            content.classList.toggle('expanded');
            icon.classList.toggle('expanded');

            if (icon) {
                icon.textContent = content.classList.contains('expanded') ? '▲' : '▼';
            }
        });
    });
}

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
        if (DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Skip update: mutation in progress');
        }
        return;
    }

    try {
        if (DEBUG_TAB_SYSTEM) {
            logTabCacheState('before-update');
        }

        initNewCollapsibleElements()
        const currentStates = getAllTabStates();
        const processed = new Set<string>();
        const tabsContainers = Array.from(document.querySelectorAll('.tabs-container'));
        if (DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Found ' + tabsContainers.length + ' tab containers');
        }

        if (tabsContainers.length === 0) {
            return;
        }

        isMutating = true;
        const visibleTabs = new Set<string>();

        tabsContainers.forEach(container => {
            if (processed.has(container.id)) return;
            processed.add(container.id);
            setupTabContainer(container);

            const activeTab = getActiveTab(container.id) ||
                currentStates.get(container.id)?.activeTab ||
                container.querySelector('.tabs .tab-button.active')?.getAttribute('data-for-tab');
            if (DEBUG_TAB_SYSTEM) {
                console.debug('[TabSystem] Processing: ' + container.id +

                    (activeTab ? ' (active: ' + activeTab + ')' : ' (no active tab)'));
            }

            if (activeTab) {
                visibleTabs.add(getCacheKey(container.id, activeTab));
                tabStates.set(container.id, {containerId: container.id, activeTab});
                restoreTabState(container);
            } else {
                const firstButton = container.querySelector('.tabs .tab-button');
                if (firstButton instanceof HTMLElement) {
                    const firstTabId = firstButton.getAttribute('data-for-tab');

                    if (firstTabId) {
                        if (DEBUG_TAB_SYSTEM) {
                            console.debug('[TabSystem] Using first tab: ' + firstTabId);
                        }

                        setActiveTab(firstButton, container);

                        visibleTabs.add(getCacheKey(container.id, firstTabId));
                    }
                } else {
                    console.warn(`[TabSystem] No active tab or buttons found for container`, {
                        containerId: container.id,
                        childrenCount: container.children.length,
                        childrenTypes: Array.from(container.children).map(c => c.nodeName)
                    });
                }
            }
        });

        requestAnimationFrame(() => {
            if (DEBUG_TAB_SYSTEM) {
                console.debug('[TabSystem] Processing ' +

                    document.querySelectorAll('.tab-content').length +

                    ' tab content elements');
            }

            document.querySelectorAll('.tab-content').forEach(tab => {
                const tabId = tab.getAttribute('data-tab');
                const container = tab.closest('.tabs-container');
                const containerId = container?.id;
                const cacheKey = containerId && tabId ? getCacheKey(containerId, tabId) : null;

                if (tabId && cacheKey && !visibleTabs.has(cacheKey) && !tab.classList.contains('active') &&
                    !tab.hasAttribute('data-keep-mounted') && !tab.hasAttribute('data-cached')) {
                    if (DEBUG_TAB_SYSTEM) {
                        console.debug('[TabSystem] Caching inactive: ' + tabId);
                    }

                    if (container) {
                        setupTabContainer(container);
                        cacheTabContent(container, tab);
                    }

                    tab.setAttribute('data-cached', 'true');
                    tab.remove();
                }
            });
            if (DEBUG_TAB_SYSTEM) {
                logTabCacheState('after-update');
            }
        });
        processed.clear();
    } catch (error) {
        errors.updateErrors++;
        console.error(`Error during tab update:`, {error, totalErrors: errors.updateErrors});

        isMutating = false;
    } finally {
        isMutating = false;
    }
}, 250);

function setupTabContainer(container: Element) {
    try {
        if (!container.id) {

            const timestamp = Date.now();
            const randomId = Math.random().toString(36).substring(2, 6);
            container.id = `tab-container-${timestamp}-${randomId}`;
            console.warn(`[TabSystem] Generated missing container ID: ${container.id}`);
        }

        const HTMLElementContainer = container as HTMLElement;
        if (HTMLElementContainer.dataset.tabSystemInitialized) {
            return;
        }

        if (VERBOSE_LOGGING || DEBUG_TAB_SYSTEM) console.debug(`[TabSystem] Initializing container`, {
            existingActiveTab: getActiveTab(container.id),
            containerId: container.id
        })

        container.addEventListener('click', (event: Event) => {
            const button = (event.target as HTMLElement).closest('.tab-button');
            if (button && container.contains(button)) {

                const tabsGroup = button.closest('.tabs');
                if (!tabsGroup) return;
                if (DEBUG_TAB_SYSTEM) {
                    console.debug('[TabSystem] Tab clicked: ' + button.getAttribute('data-for-tab'));
                }

                setActiveTab(button, container);

                tabsGroup.querySelectorAll('.tab-button').forEach(btn => {
                    if (btn === button) {
                        btn.classList.add('active');

                        void (btn as HTMLElement).offsetWidth;
                    } else {
                        btn.classList.remove('active');
                    }
                });

                void (button as HTMLElement).offsetWidth;
                updateTabs();
                event.stopPropagation();
                event.preventDefault();

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