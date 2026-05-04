// Main entry point - orchestrates module initialization.
// Module files (must be loaded before this script):
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


// ===== Main Initialization =====
document.addEventListener('DOMContentLoaded', function() {
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
        }
    });
    setupPluginManagerModal();
     setupApiKeyBanner();
    loadAppDirectory().then(() => {
        renderAppGrid();
         setupAppSearch();
        setupAppCards({
            onChat: () => {
                populateBasicChatModelSelections(appState, availableModels);
                prefillBasicChatModal();
                document.getElementById('basic-chat-settings-modal').style.display = 'block';
            },
            onPipeline: () => {
                document.getElementById('custom-pipeline-modal').style.display = 'block';
                modelManager.populateModelSelections();
            }
        });
    }).catch(error => {
        console.error('[init] Error loading app directory:', error);
    });
});

// Load API providers and models first, then initialize everything
loadApiProviders().then(() => {
    uiManager.setupTooltips();
    return loadUserSettings(httpService, appState);
}).then(() => {
    modelManager.populateModelSelections();
    populateQuickSettingsModels(appState, availableModels);
     updateApiKeyBanner();
    return loadCognitiveTypes();
}).catch(error => {
    console.error('[init] Error during initialization:', error);
    uiManager.setupTooltips();
    loadUserSettings().then(() => {
        populateQuickSettingsModels(appState, availableModels);
         updateApiKeyBanner();
    });
});