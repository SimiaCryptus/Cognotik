// Main entry point - orchestrates module initialization.
// Module files (must be loaded before this script):
//   theme.js, menubar.js,
//   htmlUtils.js, appDirectory.js, apiLoader.js, quickSettings.js,
//   basicChat.js, userSettings.js, pluginManager.js,
//   cognitiveMode.js, pipelineWizard.js

// Initialize sessionId globally
let sessionId = Utils.generateSessionId();

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        apiProviders,
        availableModels,
    };
}

// Initialize services
const httpService = new HttpService();
const notificationService = { showNotification };

// Initialize app state with dependencies
let appState = new AppState({
    localStorage: window.localStorage,
    sessionId: sessionId
});
// Make available to inline onclick handlers (e.g. removeLocalTool)
window.appState = appState;

// Initialize UI manager
const uiManager = new UIManager({
    document: document,
    appState: appState,
    httpService: httpService,
    notificationService: notificationService
});

// Initialize validation service
const validationService = new ValidationService({
    appState: appState,
    notificationService: notificationService
});

// Initialize model manager
const modelManager = new ModelManager({
    appState: appState,
    document: document,
    getAvailableModels: () => availableModels
});

// Initialize task config manager
const taskConfigManager = new TaskConfigManager({
    appState: appState,
    document: document,
    httpService: httpService,
    notificationService: notificationService,
    modelManager: modelManager,
    getAvailableModels: () => availableModels
});

// ===== Notification (used by global notificationService) =====
function showNotification(message, type = 'info') {
    return uiManager.showNotification(message, type);
}
// ===== API Key Banner =====
function hasConfiguredApiKeys(state) {
    if (!state || !state.apiSettings) return false;
    const keys = state.apiSettings.apiKeys;
    if (!keys || typeof keys !== 'object') return false;
    // Consider it configured if there is at least one non-empty key value
    return Object.keys(keys).some(k => {
        const v = keys[k];
        return v !== null && v !== undefined && String(v).trim() !== '';
    });
}
function updateApiKeyBanner() {
    const banner = document.getElementById('api-key-banner');
    if (!banner) return;
    if (hasConfiguredApiKeys(appState)) {
        banner.style.display = 'none';
    } else {
        banner.style.display = 'block';
    }
}
function setupApiKeyBanner() {
    const actionBtn = document.getElementById('api-key-banner-action');
    if (actionBtn) {
        actionBtn.addEventListener('click', () => {
            const settingsBtn = document.getElementById('user-settings-btn');
            if (settingsBtn) {
                settingsBtn.click();
            }
        });
    }
}
// ===== Localhost detection: hide User Settings button when not on localhost =====
function isLocalhost() {
    const host = window.location.hostname;
    return host === 'localhost'
        || host === '127.0.0.1'
        || host === '::1'
        || host === '[::1]';
}
function applyLocalhostRestrictions() {
    if (!isLocalhost()) {
        const settingsBtn = document.getElementById('user-settings-btn');
        if (settingsBtn) settingsBtn.style.display = 'none';
        const pluginManagerBtn = document.getElementById('plugin-manager-btn');
        if (pluginManagerBtn) pluginManagerBtn.style.display = 'none';
    }
}
// ===== Update logout button label with user name =====
function updateLogoutButtonLabel() {
    try {
        const user = appState && appState.userInfo;
        const btn = document.getElementById('auth-btn');
        const labelEl = document.getElementById('auth-btn-label');
        if (!btn || !labelEl) return;
        if (!user) {
            // Not logged in
            btn.setAttribute('aria-label', 'Login');
            btn.setAttribute('title', 'Login');
            btn.innerHTML = '<span class="btn-icon" aria-hidden="true">🔑</span> Login';
            btn.onclick = () => { window.location.href = '/login/'; };
        } else {
            const label = user.name || user.email || user.id || 'Logout';
            btn.setAttribute('aria-label', 'Logout ' + label);
            btn.setAttribute('title', 'Logout (' + label + ')');
            btn.innerHTML = '<span class="btn-icon" aria-hidden="true">🚪</span> ' +
                escapeHtmlSafe(label);
            btn.onclick = () => {
                fetch('/login/?action=logout', { method: 'POST' }).then(() => location.reload());
            };
        }
    } catch (e) {
        console.warn('[init] Unable to update logout button label:', e);
    }
}
function escapeHtmlSafe(s) {
    if (typeof HtmlUtils !== 'undefined' && HtmlUtils && typeof HtmlUtils.escapeHtml === 'function') {
        return HtmlUtils.escapeHtml(s);
    }
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}
// ===== Usage modal =====
// Generic helper to wire up a modal that displays a given URL in an iframe.
function setupIframeModal({ buttonId, modalId, iframeId, closeBtnId, url }) {
    const btn = document.getElementById(buttonId);
    const modal = document.getElementById(modalId);
    const iframe = document.getElementById(iframeId);
    const closeBtn = closeBtnId ? document.getElementById(closeBtnId) : null;
    if (!btn || !modal || !iframe) return;
    const close = () => {
        modal.style.display = 'none';
        iframe.src = 'about:blank';
    };
    btn.addEventListener('click', () => {
        // (Re)load each time it's opened so data is fresh
        iframe.src = url;
        modal.style.display = 'block';
    });
    if (closeBtn) {
        closeBtn.addEventListener('click', close);
    }
    // Close when clicking outside modal-content
    modal.addEventListener('click', (e) => {
        if (e.target === modal) close();
    });
    // Close on Escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modal.style.display === 'block') close();
    });
}
// ===== Sessions button =====
function setupSessionsButton() {
    setupIframeModal({
        buttonId: 'sessions-btn',
        modalId: 'sessions-modal',
        iframeId: 'sessions-iframe',
        closeBtnId: 'close-sessions-modal',
        url: '/sessions/'
    });
}
// ===== Budget / Usage button (unified) =====
function formatBudget(amount) {
    if (typeof amount !== 'number' || isNaN(amount)) return '—';
    const sign = amount < 0 ? '-' : '';
    const abs = Math.abs(amount);
    return sign + '$' + abs.toFixed(2);
}
function updateBudgetDisplay() {
    const span = document.getElementById('budget-amount');
    if (!span) return;
     const btn = document.getElementById('budget-btn');
    fetch('/usage/?format=json', {
        headers: { 'Accept': 'application/json' }
    }).then(response => {
        if (!response.ok) {
            throw new Error('Failed to fetch usage: ' + response.status);
        }
        return response.json();
    }).then(data => {
        const budget = (data && typeof data.available_budget === 'number')
            ? data.available_budget : null;
        if (budget === null) {
            span.textContent = 'Budget';
             if (btn) {
                 btn.removeAttribute('data-budget');
                 btn.classList.remove('budget-warning', 'budget-critical');
             }
            return;
        }
        span.textContent = formatBudget(budget);
        if (btn) {
            btn.setAttribute('title', 'Available budget: ' + formatBudget(budget));
             btn.setAttribute('data-budget', String(budget));
             btn.classList.remove('budget-warning', 'budget-critical');
             if (budget < 0.01) {
                 btn.classList.add('budget-critical');
                 span.textContent = formatBudget(budget);
             } else if (budget < 1.00) {
                 btn.classList.add('budget-warning');
             }
        }
         updateBudgetWarningBanner(budget);
         // Re-render app grid so launch buttons reflect the new budget state
         renderAppGrid();
         setupAppCards();
    }).catch(err => {
        console.warn('[updateBudgetDisplay] Error:', err);
        span.textContent = 'Budget';
         if (btn) {
             btn.removeAttribute('data-budget');
             btn.classList.remove('budget-warning', 'budget-critical');
         }
         updateBudgetWarningBanner(null);
    });
}
function setupBudgetButton() {
    setupIframeModal({
        buttonId: 'budget-btn',
         modalId: 'usage-modal',
         iframeId: 'usage-iframe',
         closeBtnId: 'close-usage-modal',
         url: '/usage/'
    });
    updateBudgetDisplay();
    // Refresh budget every 60 seconds
    setInterval(updateBudgetDisplay, 60000);
}
function updateBudgetWarningBanner(budget) {
     const banner = document.getElementById('budget-warning-banner');
     if (!banner) return;
     if (typeof budget !== 'number' || isNaN(budget)) {
         banner.classList.remove('visible', 'budget-critical-banner');
         banner.innerHTML = '';
         return;
     }
     if (budget < 0.01) {
         banner.classList.add('visible', 'budget-critical-banner');
         banner.innerHTML = `<strong>🚫 Insufficient credits (${formatBudget(budget)}).</strong>
             You need credits to launch AI sessions.
             <a href="/usage/">Add credits now →</a>`;
     } else if (budget < 1.00) {
         banner.classList.add('visible');
         banner.classList.remove('budget-critical-banner');
         banner.innerHTML = `<strong>⚠️ Low balance: ${formatBudget(budget)}.</strong>
             Your available credits are running low.
             <a href="/usage/">Top up credits →</a>`;
     } else {
         banner.classList.remove('visible', 'budget-critical-banner');
         banner.innerHTML = '';
     }
}


// ===== Main Initialization =====
document.addEventListener('DOMContentLoaded', function() {
     // Render top menubar via reusable component
     renderMenubar();

    setupBasicChatModal({ httpService, notificationService, sessionId });
    setupSettingsSection(notificationService);
    setupCustomPipelineModal({
        appState, modelManager, validationService, uiManager,
        notificationService, httpService, taskConfigManager,
        getCognitiveTypes: () => cognitiveTypes
    });
    setupUserSettingsModal({
        appState, httpService, notificationService, modelManager,
        onSettingsSaved: () => {
            populateQuickSettingsModels(appState, availableModels);
            populateBasicChatModelSelections(appState, availableModels);
            updateApiKeyBanner();
            updateLogoutButtonLabel();
            updateBudgetDisplay();
        }
    });
    setupPluginManagerModal();
    setupApiKeyBanner();
    setupSessionsButton();
    setupBudgetButton();
    applyLocalhostRestrictions();
    if (typeof setupAuthBanner === 'function') setupAuthBanner();
    if (typeof updateAuthBanner === 'function') {
        updateAuthBanner();
        // Periodically refresh the auth banner every 30 seconds (localhost only)
        if (isLocalhost()) {
            setInterval(() => updateAuthBanner(), 30000);
        }
    }
    loadAppDirectory().then(() => {
        renderAppGrid();
        setupAppSearch();
        setupAppCards();
    }).catch(error => {
        console.error('[init] Error loading app directory:', error);
    });
});
function renderMenubar() {
     if (typeof Menubar === 'undefined') {
         console.warn('[renderMenubar] Menubar component not loaded');
         return;
     }
     const appGridSection = document.getElementById('app-grid-section');
     Menubar.render('#menubar-container', {
         title: 'Cognotik',
         titleAriaLabel: 'About Cognotik',
         titleClickable: true,
         onTitleClick: function () {
             if (typeof window.__openAboutModal === 'function') {
                 window.__openAboutModal();
             }
         },
         showLayoutSelector: true,
         showThemeSelector: true,
         onLayoutChange: function (layout) {
             if (appGridSection) appGridSection.setAttribute('data-layout', layout);
         },
         buttons: [
             { id: 'user-settings-btn', icon: '⚙️', label: 'Settings', ariaLabel: 'Open Settings' },
             { id: 'plugin-manager-btn', icon: '🔌', label: 'Plugins', ariaLabel: 'Open Plugin Manager' },
             { id: 'sessions-btn', icon: '📁', label: 'Sessions', ariaLabel: 'Open Sessions' },
             {
                 id: 'budget-btn',
                 icon: '📊',
                 label: 'Budget',
                 labelId: 'budget-amount',
                 ariaLabel: 'Open Usage & Credits',
                 title: 'Usage and available credit balance'
             },
             {
                 id: 'auth-btn',
                 icon: '🔑',
                 label: 'Login',
                 labelId: 'auth-btn-label',
                 ariaLabel: 'Login',
                 onClick: function () { window.location.href = '/login/'; }
             }
         ]
     });
}

// Load API providers and models first, then initialize everything
loadApiProviders().then(() => {
    uiManager.setupTooltips();
    return loadUserSettings(httpService, appState);
}).then(() => {
    modelManager.populateModelSelections();
    populateQuickSettingsModels(appState, availableModels);
    updateApiKeyBanner();
    updateLogoutButtonLabel();
    // Re-render app grid now that login state is known
    renderAppGrid();
    setupAppCards();
    return loadCognitiveTypes();
}).catch(error => {
    console.error('[init] Error during initialization:', error);
    uiManager.setupTooltips();
    loadUserSettings().then(() => {
        populateQuickSettingsModels(appState, availableModels);
        updateApiKeyBanner();
        updateLogoutButtonLabel();
        renderAppGrid();
        setupAppCards();
    });
});