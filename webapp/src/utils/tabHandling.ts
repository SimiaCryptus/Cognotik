const VERBOSE_LOGGING = false


const DEBUG_TAB_SYSTEM = false;



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
    const containerId = container.id;
    if (!forTab) {
        console.warn('[TabSystem] No "data-for-tab" attribute found on button:', button);
        return;
    }
    // Check if this tab is already active
    const currentActiveTab = getActiveTab(container.id);
    if (currentActiveTab === forTab && container.querySelector(`.tab-content[data-tab="${forTab}"].active`)) {
        if (DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Tab already active, ignoring click:', forTab);
        }
        return;
    }

    if (DEBUG_TAB_SYSTEM) {
        console.debug('[TabSystem] Setting active tab: ' + forTab + ' in ' + container.id);
    }
    // Update state first

    setActiveTabState(container.id, forTab);
    saveTabState(container.id, forTab);






    const tabsGroup = button.closest('.tabs');
    if (!tabsGroup) return;

    tabsGroup.querySelectorAll('.tabs-container').forEach(nestedContainer => {
        setupTabContainer(nestedContainer);
    });
    // Update button styles

    const tabButtons = tabsGroup.querySelectorAll('.tab-button');
    tabButtons.forEach(btn => {
        if (btn.getAttribute('data-for-tab') === forTab) {
            btn.classList.add('active');

            void (btn as HTMLElement).offsetWidth;
        } else {
            btn.classList.remove('active');
        }
    });

    // Update content visibility using display: none for simplicity and performance



    Array.from(container.children || [])
        .forEach(content => {
            if (!content.matches('.tab-content')) return;

            const contentElement = content as HTMLElement;
            const contentTabId = content.getAttribute('data-tab');

            if (contentTabId === forTab) {
                content.classList.add('active');
                contentElement.style.display = ''; // Or 'flex', 'block' if needed, default is usually block

                requestAnimationFrame(() => {
                    // Ensure nested tabs within the newly active tab are also initialized/restored
                    contentElement.querySelectorAll('.tabs-container').forEach(nestedContainer => {
                        setupTabContainer(nestedContainer);
                        restoreTabState(nestedContainer); // Restore state for nested tabs
                    });
                });
            } else {




                content.classList.remove('active');
                contentElement.style.display = 'none';
            }
        });
}

function restoreTabState(container: Element) {
    try {
        diagnostics.restoreCount++;
        const containerId = container.id;
        // Find the correct tab to activate

        const savedTab = getActiveTab(containerId);
        if (DEBUG_TAB_SYSTEM) {
            console.debug('[TabSystem] Restoring state: ' + savedTab + ' for ' + containerId);
        }

        if (savedTab) {
            // Find the button corresponding to the saved tab *within this container's scope*
            const button = container.querySelector(`.tabs > .tab-button[data-for-tab="${savedTab}"]`) as HTMLElement;

            if (button) {
                if (DEBUG_TAB_SYSTEM) {
                    console.debug('[TabSystem] Found button for: ' + savedTab);
                }
                // Use setActiveTab to handle button styling and content visibility
                // Avoid calling setActiveTab directly if it might trigger redundant updates via its own updateTabs call

                setActiveTab(button, container);
                diagnostics.restoreSuccess++;
            } else {
                diagnostics.restoreFail++;
                console.warn('[TabSystem] Button for saved tab not found', {
                    containerId,
                    savedTab,
                    availableButtons: Array.from(container.querySelectorAll('.tabs > .tab-button'))
                        .map(btn => ({
                            forTab: btn.getAttribute('data-for-tab'),
                            text: btn.textContent?.trim() || 'unknown'
                        }))
                });

                const firstButton = container.querySelector('.tabs > .tab-button') as HTMLElement;
                if (firstButton) {
                    console.info('[TabSystem] Using first available tab button as fallback', {
                        containerId,
                        buttonForTab: firstButton.getAttribute('data-for-tab'),
                        buttonText: firstButton.textContent?.trim() || 'unknown'
                    });
                    setActiveTab(firstButton, container);
                }
            }




        } else {
            diagnostics.restoreFail++;
            console.warn('[TabSystem] No saved tab found for container', {
                containerId
            });

            const firstButton = container.querySelector('.tabs > .tab-button') as HTMLElement;
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


        console.debug('[TabSystem] Resetting tab state - clearing ' + tabStates.size + ' states');
    }

    tabStates.clear();
    tabStateHistory.clear();
    tabStateVersions.clear();
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

        tabsContainers.forEach(container => {
            if (processed.has(container.id)) return;
            processed.add(container.id);
            setupTabContainer(container);
            // Determine the tab that *should* be active

            const activeTab = getActiveTab(container.id) ||
                currentStates.get(container.id)?.activeTab ||
                container.querySelector('.tabs .tab-button.active')?.getAttribute('data-for-tab');
            if (DEBUG_TAB_SYSTEM) {
                console.debug('[TabSystem] Processing: ' + container.id +

                    (activeTab ? ' (active: ' + activeTab + ')' : ' (no active tab)'));
            }

            if (activeTab) {
                // Ensure state map is consistent
                tabStates.set(container.id, {containerId: container.id, activeTab});
                // Restore the visual state (buttons, content visibility)
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

                        // State is saved within setActiveTab
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
        // Removed the second pass that iterated through all .tab-content elements.
        // Visibility is now handled within setActiveTab/restoreTabState.







        processed.clear();
    } catch (error) {
        errors.updateErrors++;
        console.error(`Error during tab update:`, {error, totalErrors: errors.updateErrors});

        isMutating = false;
    } finally {
        isMutating = false;
    }
}, 250);

export function setupTabContainer(container: Element) {
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
            if (button && container.contains(button) && !button.classList.contains('active')) {

                const tabsGroup = button.closest('.tabs');
                if (!tabsGroup) return;
                if (DEBUG_TAB_SYSTEM) {
                    console.debug('[TabSystem] Tab clicked: ' + button.getAttribute('data-for-tab'));
                }

                setActiveTab(button, container);



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