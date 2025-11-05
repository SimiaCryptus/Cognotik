// These will be populated from the API
let apiProviders = [];
let availableModels = {};

document.addEventListener('DOMContentLoaded', function () {

    // --- Basic Chat Modal Setup ---
    const basicChatBtn = document.getElementById('open-basic-chat');
    const basicChatModal = document.getElementById('basic-chat-settings-modal');
    const closeBasicChatModal = document.getElementById('close-basic-chat-modal');
    const cancelBasicChatSettings = document.getElementById('cancel-basic-chat-settings');
    const basicChatForm = document.getElementById('basic-chat-settings-form');
    const tempSlider = document.getElementById('basic-chat-temperature');
    const tempValue = document.getElementById('basic-chat-temperature-value');

    basicChatBtn.addEventListener('click', function () {
        console.log('[DOMContentLoaded] basicChatBtn clicked.');
        // Populate model selectors with available models (same as main pipeline)
        populateBasicChatModelSelections();
        // Prefill using main pipeline's preferences (shared keys), fallback to legacy basicChat* keys, then default
        const model = localStorage.getItem('defaultModel') || localStorage.getItem('basicChatModel') || 'GPT4o';
        console.log(`[DOMContentLoaded] Basic Chat Modal: model determined as ${model} (defaultModel: ${localStorage.getItem('defaultModel')}, basicChatModel: ${localStorage.getItem('basicChatModel')})`);
        const parsingModel = localStorage.getItem('parsingModel') || localStorage.getItem('basicChatParsingModel') || 'GPT4oMini';
        const temperature = localStorage.getItem('temperature') || localStorage.getItem('basicChatTemperature') || '0.3';
        const budget = localStorage.getItem('budget') || localStorage.getItem('basicChatBudget') || '2.0';
        // Ensure model selections are populated before setting values
        populateBasicChatModelSelections();

        document.getElementById('basic-chat-model').value = model;
        document.getElementById('basic-chat-parsing-model').value = parsingModel;
        document.getElementById('basic-chat-temperature').value = temperature;
        document.getElementById('basic-chat-temperature-value').textContent = temperature;
        document.getElementById('basic-chat-budget').value = budget;
        console.log('[DOMContentLoaded] Basic Chat Modal prefilled with: model:', model, 'parsingModel:', parsingModel, 'temperature:', temperature, 'budget:', budget);
        basicChatModal.style.display = "block";
    });

    // Populate the Basic Chat model selectors with available models based on API keys
    function populateBasicChatModelSelections() {
        console.log('[populateBasicChatModelSelections] Called');
        const modelSelect = document.getElementById('basic-chat-model');
        const parsingModelSelect = document.getElementById('basic-chat-parsing-model');
        if (!modelSelect || !parsingModelSelect) {
            console.warn('[populateBasicChatModelSelections] basic-chat-model or basic-chat-parsing-model element not found.');
            return;
        }

        // Save current values to try to preserve selection
        const prevModel = modelSelect.value;
        const prevParsingModel = parsingModelSelect.value;
        console.log('[populateBasicChatModelSelections] Previous selections - model:', prevModel, 'parsingModel:', prevParsingModel);

        modelSelect.innerHTML = '';
        parsingModelSelect.innerHTML = '';
        const addedModels = new Set();
        if (appState.apiSettings && appState.apiSettings.apiKeys) {
            for (const [provider, key] of Object.entries(appState.apiSettings.apiKeys)) {
                console.log(`[populateBasicChatModelSelections] Checking provider: ${provider}, key exists: ${!!key}`);
                if (key && availableModels[provider]) {
                    availableModels[provider].forEach(model => {
                        if (!addedModels.has(model.id)) {
                            console.log(`[populateBasicChatModelSelections] Adding model ${model.id} from provider ${provider}`);
                            const option = document.createElement('option');
                            option.value = model.id;
                            option.textContent = `${model.name} (${provider})`;
                            option.title = model.description;
                            modelSelect.appendChild(option);

                            const parsingOption = document.createElement('option');
                            parsingOption.value = model.id;
                            parsingOption.textContent = `${model.name} (${provider})`;
                            parsingOption.title = model.description;
                            parsingModelSelect.appendChild(parsingOption);
                            addedModels.add(model.id);
                        }
                    });
                }
            }
        }
        // If no models available, show default
        if (modelSelect.options.length === 0) {
            console.log('[populateBasicChatModelSelections] No models available from API keys, adding default OpenAI options for basic chat.');
            const defaultOption = document.createElement('option');
            defaultOption.value = 'GPT4o';
            defaultOption.textContent = 'GPT-4o (OpenAI) - Configure API key';
            modelSelect.appendChild(defaultOption);
            const defaultParsingOption = document.createElement('option');
            defaultParsingOption.value = 'GPT4oMini';
            defaultParsingOption.textContent = 'GPT-4o Mini (OpenAI) - Configure API key';
            parsingModelSelect.appendChild(defaultParsingOption);
        }
        // Try to restore previous selection
        if (prevModel && Array.from(modelSelect.options).some(opt => opt.value === prevModel)) {
            modelSelect.value = prevModel;
            console.log('[populateBasicChatModelSelections] Restored previous model selection:', prevModel);
        }
        if (prevParsingModel && Array.from(parsingModelSelect.options).some(opt => opt.value === prevParsingModel)) {
            parsingModelSelect.value = prevParsingModel;
            console.log('[populateBasicChatModelSelections] Restored previous parsing model selection:', prevParsingModel);
        }
        console.log('[populateBasicChatModelSelections] Finished. Final basic-chat-model.value:', modelSelect.value, 'basic-chat-parsing-model.value:', parsingModelSelect.value);
    }

    // Update temperature value display
    tempSlider.addEventListener('input', function () {
        console.log('[DOMContentLoaded] Basic Chat tempSlider input event. New value:', this.value);
        tempValue.textContent = this.value;
    });

    closeBasicChatModal.onclick = function () {
        console.log('[DOMContentLoaded] closeBasicChatModal clicked.');
        basicChatModal.style.display = "none";
    };
    cancelBasicChatSettings.onclick = function () {
        console.log('[DOMContentLoaded] cancelBasicChatSettings clicked.');
        basicChatModal.style.display = "none";
    };
    window.addEventListener('click', function (event) {
        if (event.target === basicChatModal) {
            console.log('[DOMContentLoaded] Window click event, target is basicChatModal. Closing modal.');
            basicChatModal.style.display = "none";
        }
    });
    // Save to localStorage for convenience
    basicChatForm.addEventListener('submit', function (e) {
        console.log('[DOMContentLoaded] basicChatForm submitted.');
        e.preventDefault();
        // Validate form data
        const model = document.getElementById('basic-chat-model').value;
        const parsingModel = document.getElementById('basic-chat-parsing-model').value;
        const temperatureInput = document.getElementById('basic-chat-temperature').value;
        const budgetInput = document.getElementById('basic-chat-budget').value;
        if (!model || !parsingModel || !temperatureInput || !budgetInput) {
            notificationService.showNotification('Please fill in all required fields', 'error');
            return;
        }

        // Gather settings
        const temperature = parseFloat(temperatureInput);
        const budget = parseFloat(budgetInput);

        if (isNaN(temperature) || isNaN(budget)) {
            notificationService.showNotification('Temperature and budget must be valid numbers', 'error');
            return;
        }

        console.log('[DOMContentLoaded] Basic Chat Form Save - model:', model, 'parsingModel:', parsingModel, 'temperature:', temperature, 'budget:', budget);

        // Save to localStorage for convenience AND sync with main pipeline preferences
        // Always use main pipeline keys for shared params
        localStorage.setItem('defaultModel', model);
        localStorage.setItem('parsingModel', parsingModel);
        localStorage.setItem('temperature', temperature);
        localStorage.setItem('budget', budget);
        // Also keep legacy basicChat* keys for backward compatibility if needed
        localStorage.setItem('basicChatModel', model);
        localStorage.setItem('basicChatParsingModel', parsingModel);
        localStorage.setItem('basicChatTemperature', temperature);
        localStorage.setItem('basicChatBudget', budget);
        console.log('[DOMContentLoaded] Saved basic chat settings to localStorage.');
        // Use the global session id to ensure consistency
        const chatSessionId = sessionId;
        console.log('[DOMContentLoaded] Generated chatSessionId for basic chat:', chatSessionId);
        // Post settings to chat app endpoint
        httpService.saveChatSettings(chatSessionId, {
            model: model,
            parsingModel: parsingModel,
            temperature: temperature,
            budget: budget
        })
            .then(response => {
                console.log('[DOMContentLoaded] Basic Chat Save - fetch response:', response);
                if (response) {
                    basicChatModal.style.display = "none";
                    console.log('[DOMContentLoaded] Basic Chat settings saved successfully. Redirecting to /chat/#', chatSessionId);
                    window.location.href = `/chat/#${chatSessionId}`;
                }
            }).catch(error => {
            console.error('[DOMContentLoaded] Error saving chat settings:', error);
            notificationService.showNotification('Error saving chat settings: ' + error.message, 'error');
        });
    });
});

// Initialize sessionId globally - this will be used consistently throughout the app
let sessionId = Utils.generateSessionId();


if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        apiProviders,
        availableModels,
    };
}


// Initialize services
const httpService = new HttpService();
const notificationService = {showNotification};
// Initialize app state with dependencies
let appState = new AppState({
    localStorage: window.localStorage,
    sessionId: sessionId
});
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

// Initialize UI components after DOM is loaded
document.addEventListener('DOMContentLoaded', function () {
});

// Load API providers and models first
loadApiProviders().then(() => {
    // Then initialize UI components
    uiManager.setupTooltips();
    // Load user settings
    return loadUserSettings();
}).then(() => {
    // After settings are loaded, populate model selections
    modelManager.populateModelSelections();
    // Setup wizard navigation
    setupWizardNavigation();
    // Setup user settings modal
    setupUserSettingsModal();
}).catch(error => {
    console.error('[DOMContentLoaded] Error loading API providers:', error);
    // Continue with initialization even if API providers fail to load
    uiManager.setupTooltips();
    loadUserSettings();
    setupWizardNavigation();
    setupUserSettingsModal();
});

async function loadApiProviders() {
    console.log('[loadApiProviders] Loading API providers from server...');
    try {
        const providersResponse = await httpService.getApiProviders();
        console.log('[loadApiProviders] Received response:', providersResponse);

        const providers = providersResponse.configuredProviders || [];
        const availableProviders = providersResponse.availableProviders || [];

        // Transform the provider data into the format expected by the UI
        apiProviders = providers.map(provider => ({
            id: provider.name,
            name: provider.name,
            baseUrl: provider.baseUrl
        }));

        // Transform models into the expected format
        availableModels = {};
        providers.forEach(provider => {
            if (provider.models && provider.models.length > 0) {
                availableModels[provider.name] = provider.models.map(model => ({
                    id: model.name,
                    name: model.name,
                    description: model.maxTokens ? `Max tokens: ${model.maxTokens}` : 'No token limit specified'
                }));
            }
        });

        console.log('[loadApiProviders] Transformed apiProviders:', apiProviders);
        console.log('[loadApiProviders] Transformed availableModels:', availableModels);
        console.log('[loadApiProviders] Available providers (all):', availableProviders);

        // Update the global constants
        if (typeof API_PROVIDERS !== 'undefined') {
            API_PROVIDERS.length = 0;
            API_PROVIDERS.push(...apiProviders);
        }
        if (typeof AVAILABLE_MODELS !== 'undefined') {
            Object.keys(AVAILABLE_MODELS).forEach(key => delete AVAILABLE_MODELS[key]);
            Object.assign(AVAILABLE_MODELS, availableModels);
        }
        // Store available providers globally for use in settings UI
        window.allAvailableProviders = availableProviders;



    } catch (error) {
        console.error('[loadApiProviders] Error loading API providers:', error);
        // Fall back to empty arrays if loading fails
        apiProviders = [];
        availableModels = {};
        window.allAvailableProviders = [];
        throw error;
    }
}

function showNotification(message, type = 'info') {
    return uiManager.showNotification(message, type);
}

// Add missing functions
function loadUserSettings() {
    console.log('[loadUserSettings] Loading user settings...');
    return httpService.getUserSettings()
        .then(settingsText => {
            try {
                const settings = JSON.parse(settingsText);
                // Transform new format to old format for backward compatibility
                if (settings.apis && Array.isArray(settings.apis)) {
                    const apiKeys = {};
                    const apiBase = {};
                    settings.apis.forEach(api => {
                        if (api.provider) {
                            apiKeys[api.provider] = api.key || '';
                            if (api.baseUrl) {
                                apiBase[api.provider] = api.baseUrl;
                            }
                        }
                    });
                    appState.apiSettings = {
                        apiKeys: apiKeys,
                        apiBase: apiBase,
                        localTools: settings.tools || [],
                        configuredApis: settings.apis
                    };
                } else {
                    // Fallback to old format
                    appState.apiSettings = settings;
                }
                console.log('[loadUserSettings] User settings loaded:', settings);
                // Ensure all configured APIs from user settings are available in the UI
                // even if they don't have models in the /apiProviders response
                if (appState.apiSettings.configuredApis && Array.isArray(appState.apiSettings.configuredApis)) {
                    appState.apiSettings.configuredApis.forEach(api => {
                        // Add to apiProviders if not already present
                        if (!apiProviders.find(p => p.id === api.provider)) {
                            apiProviders.push({
                                id: api.provider,
                                name: api.provider,
                                baseUrl: api.baseUrl
                            });
                            console.log(`[loadUserSettings] Added provider from user settings: ${api.provider}`);
                        }
                        // Ensure apiBase is populated
                        if (!appState.apiSettings.apiBase) {
                            appState.apiSettings.apiBase = {};
                        }
                        if (api.baseUrl) {
                            appState.apiSettings.apiBase[api.provider] = api.baseUrl;
                        }
                    });
                }
            } catch (error) {
                console.error('[loadUserSettings] Error parsing settings:', error);
                appState.apiSettings = {apiKeys: {}, apiBase: {}, localTools: []};
            }
        })
        .catch(error => {
            console.error('[loadUserSettings] Error loading settings:', error);
            appState.apiSettings = {apiKeys: {}, apiBase: {}, localTools: []};
        });
}

function setupWizardNavigation() {
    console.log('[setupWizardNavigation] Setting up wizard navigation...');
    // Setup cognitive mode change handlers
    document.querySelectorAll('input[name="cognitive-mode"]').forEach(radio => {
        radio.addEventListener('change', function () {
            const autoplanSettings = document.getElementById('auto-plan-settings');
            if (this.value === 'auto-plan') {
                autoplanSettings.style.display = 'block';
            } else {
                autoplanSettings.style.display = 'none';
            }
        });
    });
    // Setup temperature slider
    const tempSlider = document.getElementById('temperature');
    const tempValue = document.getElementById('temperature-value');
    if (tempSlider && tempValue) {
        tempSlider.addEventListener('input', function () {
            tempValue.textContent = this.value;
        });
    }
    // Setup working directory generator
    document.getElementById('generate-working-dir')?.addEventListener('click', () => {
        const workingDirInput = document.getElementById('working-dir');
        if (workingDirInput) {
            workingDirInput.value = Utils.generateCognotikWorkingDir();
        }
    });

    // Next buttons
    document.getElementById('next-to-task-settings')?.addEventListener('click', () => {
        const mode = document.querySelector('input[name="cognitive-mode"]:checked')?.value;
        if (mode) {
            appState.updateCognitiveMode(mode);
            // Save auto-plan specific settings
            if (mode === 'auto-plan') {
                appState.updateTaskSetting('maxTaskHistoryChars', parseInt(document.getElementById('max-task-history')?.value) || 20000);
                appState.updateTaskSetting('maxTasksPerIteration', parseInt(document.getElementById('max-tasks-per-iteration')?.value) || 3);
                appState.updateTaskSetting('maxIterations', parseInt(document.getElementById('max-iterations')?.value) || 100);
            }
            uiManager.navigateToStep('task-settings');
        }
    });
    document.getElementById('next-to-task-selection')?.addEventListener('click', () => {
        // Save task settings
        appState.updateTaskSetting('defaultModel', document.getElementById('model-selection')?.value);
        appState.updateTaskSetting('parsingModel', document.getElementById('parsing-model')?.value);
        appState.updateTaskSetting('imageModel', document.getElementById('image-model')?.value);
        appState.updateTaskSetting('workingDir', document.getElementById('working-dir')?.value);
        appState.updateTaskSetting('temperature', parseFloat(document.getElementById('temperature')?.value));
        appState.updateTaskSetting('autoFix', document.getElementById('auto-fix')?.checked);
        appState.updateTaskSetting('graphFile', document.getElementById('graph-file')?.value);
        // Populate task selection
        populateTaskSelection();
        uiManager.navigateToStep('task-selection');
    });
    document.getElementById('next-to-launch')?.addEventListener('click', () => {
        uiManager.navigateToStep('launch');
        uiManager.updateLaunchSummaries();
    });
    // Back buttons
    document.getElementById('back-to-cognitive-mode')?.addEventListener('click', () => {
        uiManager.navigateToStep('cognitive-mode');
    });
    document.getElementById('back-to-task-settings')?.addEventListener('click', () => {
        uiManager.navigateToStep('task-settings');
    });
    document.getElementById('back-to-task-selection')?.addEventListener('click', () => {
        uiManager.navigateToStep('task-selection');
    });
    // Launch button
    document.getElementById('launch-session')?.addEventListener('click', () => {
        if (validationService.validateConfiguration()) {
            launchSession();
        }
    });
}

function setupUserSettingsModal() {
    console.log('[setupUserSettingsModal] Setting up user settings modal...');
    const userSettingsBtn = document.getElementById('user-settings-btn');
    const userSettingsModal = document.getElementById('user-settings-modal');
    const closeUserSettingsModal = document.getElementById('close-user-settings-modal');

    userSettingsBtn?.addEventListener('click', () => {
        userSettingsModal.style.display = 'block';
        populateUserSettings();
    });

    closeUserSettingsModal?.addEventListener('click', () => {
        userSettingsModal.style.display = 'none';
    });
    // Setup tab switching
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', () => {
            const tabId = button.getAttribute('data-tab');
            switchTab(tabId);
        });
    });
    // Setup local tools
    document.getElementById('add-local-tool')?.addEventListener('click', () => {
        const toolPath = document.getElementById('new-tool-path')?.value;
        if (toolPath) {
            addLocalTool(toolPath);
            document.getElementById('new-tool-path').value = '';
        }
    });
    // Reset settings
    document.getElementById('reset-user-settings')?.addEventListener('click', () => {
        if (confirm('Are you sure you want to reset all settings?')) {
            resetUserSettings();
        }
    });

    // Save settings
    document.getElementById('save-user-settings')?.addEventListener('click', () => {
        saveUserSettings();
    });
}

function populateUserSettings() {
    console.log('[populateUserSettings] Populating user settings...');

    // Populate API keys
    const apiKeysContainer = document.getElementById('api-keys-container');
    if (apiKeysContainer) {
        apiKeysContainer.innerHTML = '';

        // Create a single section for all API providers
        const apiProvidersTitle = document.createElement('h4');
        apiProvidersTitle.textContent = 'Configure your API keys for different AI providers:';
        apiProvidersTitle.style.marginBottom = '15px';
        apiKeysContainer.appendChild(apiProvidersTitle);

        const apiProvidersDescription = document.createElement('p');
        apiProvidersDescription.textContent = 'Add API keys for the providers you want to use. You can add multiple providers from the available list.';
        apiProvidersDescription.style.fontSize = '0.9em';
        apiProvidersDescription.style.color = '#666';
        apiProvidersDescription.style.marginBottom = '15px';
        apiKeysContainer.appendChild(apiProvidersDescription);

        const apiProvidersList = document.createElement('div');
        apiProvidersList.id = 'api-providers-list';

        // Show existing API keys from appState (includes providers without models)
        if (appState.apiSettings.apiKeys) {
            Object.keys(appState.apiSettings.apiKeys).forEach(providerId => {
                const apiKey = appState.apiSettings.apiKeys[providerId];
                const baseUrl = appState.apiSettings.apiBase ? appState.apiSettings.apiBase[providerId] : '';
                const providerGroup = createProviderInput(providerId, apiKey || '', baseUrl || '');
                apiProvidersList.appendChild(providerGroup);
            });
        }
        // Also show providers from apiBase that might not be in apiKeys yet
        if (appState.apiSettings.apiBase) {
            Object.keys(appState.apiSettings.apiBase).forEach(providerId => {
                // Only add if not already shown from apiKeys
                if (!appState.apiSettings.apiKeys || !appState.apiSettings.apiKeys[providerId]) {
                    const baseUrl = appState.apiSettings.apiBase[providerId];
                    const providerGroup = createProviderInput(providerId, '', baseUrl || '');
                    apiProvidersList.appendChild(providerGroup);
                }
            });
        }

        apiKeysContainer.appendChild(apiProvidersList);

        // Add button to add new provider
        const addProviderBtn = document.createElement('button');
        addProviderBtn.className = 'button secondary';
        addProviderBtn.textContent = '+ Add API Provider';
        addProviderBtn.style.marginTop = '10px';
        addProviderBtn.addEventListener('click', () => {
            const newProviderGroup = createProviderInput('', '', '');
            apiProvidersList.appendChild(newProviderGroup);
        });
        apiKeysContainer.appendChild(addProviderBtn);
    }

    // Populate local tools
    populateLocalTools();

    function createProviderInput(providerId, apiKey, baseUrl) {
        const keyGroup = document.createElement('div');
        keyGroup.className = 'form-group provider-group';
        keyGroup.style.display = 'flex';
        keyGroup.style.gap = '10px';
        keyGroup.style.alignItems = 'flex-end';
        keyGroup.style.marginBottom = '10px';
        keyGroup.style.flexWrap = 'wrap';
        
        const providerSelectGroup = document.createElement('div');
        providerSelectGroup.style.flex = '1 1 200px';
        providerSelectGroup.style.minWidth = '150px';
        const providerSelectLabel = document.createElement('label');
        providerSelectLabel.textContent = 'Provider:';
        const providerSelect = document.createElement('select');
        providerSelect.className = 'provider-select';
        // Add empty option
        const emptyOption = document.createElement('option');
        emptyOption.value = '';
        emptyOption.textContent = 'Select a provider...';
        providerSelect.appendChild(emptyOption);

        // Build a comprehensive list of all providers from multiple sources
        const providersToShow = window.allAvailableProviders || apiProviders;
        const providerMap = new Map();
        
        // Add all available providers
        providersToShow.forEach(provider => {
            providerMap.set(provider.id, provider.name);
        });
        // Add providers from configuredApis (from user settings)
        if (appState.apiSettings.configuredApis && Array.isArray(appState.apiSettings.configuredApis)) {
            appState.apiSettings.configuredApis.forEach(api => {
                if (!providerMap.has(api.provider)) {
                    providerMap.set(api.provider, api.provider);
                }
            });
        }
        
        
        // Add providers from saved API keys that might not be in available providers
        if (appState.apiSettings.apiKeys) {
            Object.keys(appState.apiSettings.apiKeys).forEach(savedProviderId => {
                if (!providerMap.has(savedProviderId)) {
                    providerMap.set(savedProviderId, savedProviderId);
                }
            });
        }
        // Add providers from apiBase that might not be in available providers
        if (appState.apiSettings.apiBase) {
            Object.keys(appState.apiSettings.apiBase).forEach(savedProviderId => {
                if (!providerMap.has(savedProviderId)) {
                    providerMap.set(savedProviderId, savedProviderId);
                }
            });
        }
        // Add providers from configured providers (from /apiProviders response)
        if (apiProviders && Array.isArray(apiProviders)) {
            apiProviders.forEach(provider => {
                if (!providerMap.has(provider.id)) {
                    providerMap.set(provider.id, provider.name);
                }
            });
        }
        
        // Create options from the merged provider map
        Array.from(providerMap.entries()).sort((a, b) => a[1].localeCompare(b[1])).forEach(([id, name]) => {
            const option = document.createElement('option');
            option.value = id;
            option.textContent = name;
            if (id === providerId) {
                option.selected = true;
            }
            providerSelect.appendChild(option);
        });

providerSelectGroup.appendChild(providerSelectLabel);
        providerSelectGroup.appendChild(providerSelect);
        
        const apiKeyGroup = document.createElement('div');
        apiKeyGroup.style.flex = '2 1 300px';
        apiKeyGroup.style.minWidth = '200px';
        const apiKeyLabel = document.createElement('label');
        apiKeyLabel.textContent = 'API Key:';
        const apiKeyInput = document.createElement('input');
        apiKeyInput.type = 'password';
        apiKeyInput.className = 'provider-key';
        apiKeyInput.placeholder = 'Enter API key';
        apiKeyInput.value = apiKey;
        apiKeyGroup.appendChild(apiKeyLabel);
        apiKeyGroup.appendChild(apiKeyInput);
        const baseUrlGroup = document.createElement('div');
        baseUrlGroup.style.flex = '2 1 300px';
        baseUrlGroup.style.minWidth = '200px';
        const baseUrlLabel = document.createElement('label');
        baseUrlLabel.textContent = 'Base URL (optional):';
        const baseUrlInput = document.createElement('input');
        baseUrlInput.type = 'text';
        baseUrlInput.className = 'provider-base-url';
        baseUrlInput.placeholder = 'Enter base URL';
        baseUrlInput.value = baseUrl || '';
        baseUrlGroup.appendChild(baseUrlLabel);
        baseUrlGroup.appendChild(baseUrlInput);
        
        const removeBtn = document.createElement('button');
        removeBtn.className = 'button secondary';
        removeBtn.textContent = 'Remove';
        removeBtn.style.marginBottom = '0';
        removeBtn.style.flex = '0 0 auto';
        removeBtn.addEventListener('click', () => {
            keyGroup.remove();
        });
        keyGroup.appendChild(providerSelectGroup);
        keyGroup.appendChild(apiKeyGroup);
        keyGroup.appendChild(baseUrlGroup);
        keyGroup.appendChild(removeBtn);
        return keyGroup;
    }
}

function saveUserSettings() {
    console.log('[saveUserSettings] Saving user settings...');

    // Collect API keys
    const apiKeys = {};
    const apiBase = {};

    // Collect all provider API keys
    const providerGroups = document.querySelectorAll('.provider-group');
    providerGroups.forEach(group => {
        const selectInput = group.querySelector('.provider-select');
        const keyInput = group.querySelector('.provider-key');
        const baseUrlInput = group.querySelector('.provider-base-url');
        if (selectInput && keyInput && selectInput.value) {
            // Save even empty keys to preserve provider configuration
            apiKeys[selectInput.value] = keyInput.value;
            if (baseUrlInput && baseUrlInput.value) {
                apiBase[selectInput.value] = baseUrlInput.value;
            }
        }
    });

    // Update app state
    appState.apiSettings.apiKeys = apiKeys;
    appState.apiSettings.apiBase = apiBase;
    // Transform to new format for server
    const apisArray = Object.keys(apiKeys).map(provider => ({
        provider: provider,
        key: apiKeys[provider],
        baseUrl: apiBase[provider] || ''
    }));
    const settingsToSave = {
        apis: apisArray,
        tools: appState.apiSettings.localTools || [],
        etc: {}
    };

    // Save to server
    httpService.saveUserSettings(settingsToSave)
        .then(() => {
            notificationService.showNotification('Settings saved successfully', 'success');
            document.getElementById('user-settings-modal').style.display = 'none';
            // Refresh model selections
            modelManager.populateModelSelections();
            if (typeof populateBasicChatModelSelections === 'function') {
                populateBasicChatModelSelections();
            }
        })
        .catch(error => {
            console.error('[saveUserSettings] Error saving settings:', error);
            notificationService.showNotification('Error saving settings: ' + error.message, 'error');
        });
}

function switchTab(tabId) {
    // Hide all tab contents
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });

    // Remove active class from all tab buttons
    document.querySelectorAll('.tab-button').forEach(button => {
        button.classList.remove('active');
    });

    // Show selected tab content
    const selectedContent = document.getElementById(`${tabId}-tab`);
    if (selectedContent) {
        selectedContent.classList.add('active');
    }

    // Add active class to selected tab button
    const selectedButton = document.querySelector(`[data-tab="${tabId}"]`);
    if (selectedButton) {
        selectedButton.classList.add('active');
    }
}

function populateTaskSelection() {
    console.log('[populateTaskSelection] Populating task selection...');

    const taskToggles = document.getElementById('task-toggles');
    if (!taskToggles) return;

    taskToggles.innerHTML = '';

    // Group tasks by category
    const categories = taskConfigManager.getTaskCategories();
    
    categories.forEach(category => {
        const categorySection = document.createElement('div');
        categorySection.className = 'task-category-section';
        
        const categoryHeader = document.createElement('h4');
        categoryHeader.textContent = category;
        categoryHeader.style.marginTop = '20px';
        categoryHeader.style.marginBottom = '10px';
        categoryHeader.style.color = '#2c3e50';
        categorySection.appendChild(categoryHeader);
        
        const tasksInCategory = taskConfigManager.getTasksByCategory(category);
        
        tasksInCategory.forEach(task => {
            const taskToggle = document.createElement('div');
            taskToggle.className = 'task-toggle';
            taskToggle.style.display = 'flex';
            taskToggle.style.justifyContent = 'space-between';
            taskToggle.style.alignItems = 'center';
            taskToggle.style.padding = '10px';
            taskToggle.style.marginBottom = '5px';
            taskToggle.style.backgroundColor = '#f8f9fa';
            taskToggle.style.borderRadius = '4px';

            const hasConfigs = appState.hasTaskConfigs(task.id);
            const configCount = Object.keys(appState.getTaskConfigs(task.id)).length;

            taskToggle.innerHTML = `
                <div style="flex: 1;">
                    <label style="font-weight: 500;">${task.name}</label>
                    <span class="tooltip">?<span class="tooltiptext">${task.description}</span></span>
                    ${hasConfigs ? `<span style="margin-left: 10px; color: #28a745; font-size: 0.9em;">✓ ${configCount} config${configCount > 1 ? 's' : ''}</span>` : '<span style="margin-left: 10px; color: #999; font-size: 0.9em;">Not configured</span>'}
                </div>
                <div>
                    <button class="button secondary small configure-task-btn" data-task-id="${task.id}" 
                            style="margin-left: 10px; padding: 5px 10px; font-size: 0.9em;">
                        Configure
                    </button>
                </div>
            `;

            categorySection.appendChild(taskToggle);

            // Add event listener for configure button
            const configureBtn = taskToggle.querySelector('.configure-task-btn');
            configureBtn.addEventListener('click', () => {
                showTaskConfigurationDialog(task.id);
            });
        });
        
        taskToggles.appendChild(categorySection);
    });
}

function showTaskConfigurationDialog(taskId) {
    console.log('[showTaskConfigurationDialog] Opening configuration for task:', taskId);
    
    // Get existing configurations for this task
    const existingConfigs = appState.getTaskConfigs(taskId);
    
    // Create a dialog to manage configurations
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.style.display = 'block';
    
    let configListHtml = '';
    const configEntries = Object.entries(existingConfigs);
    if (configEntries.length > 0) {
        configListHtml = '<div class="config-list" style="margin-bottom: 20px;">';
        configListHtml += '<h4>Existing Configurations:</h4>';
        configEntries.forEach(([configName, config]) => {
            const modelInfo = config.model ? ` (${config.model})` : '';
            configListHtml += `
                <div class="config-item" style="display: flex; justify-content: space-between; align-items: center; 
                     padding: 10px; margin-bottom: 5px; background: #f8f9fa; border-radius: 4px;">
                    <span><strong>${configName}</strong>${modelInfo}</span>
                    <div>
                        <button class="button secondary small edit-config-btn" data-config-name="${configName}">Edit</button>
                        <button class="button secondary small delete-config-btn" data-config-name="${configName}" 
                                style="margin-left: 5px;">Delete</button>
                    </div>
                </div>
            `;
        });
        configListHtml += '</div>';
    }
    
    modal.innerHTML = `
        <div class="modal-content" style="max-width: 600px;">
            <div class="modal-header">
                <h3>Configure ${taskConfigManager.getTaskType(taskId).name}</h3>
                <span class="close-config-list-modal">&times;</span>
            </div>
            <div class="modal-body">
                ${configListHtml}
                <button class="button primary add-new-config-btn">+ Add New Configuration</button>
            </div>
            <div class="modal-footer">
                <button class="button secondary close-config-list-btn">Close</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
    
    // Setup event listeners
    const closeModal = () => {
        modal.remove();
        // Refresh task selection to show updated config status
        populateTaskSelection();
    };
    
    modal.querySelector('.close-config-list-modal').addEventListener('click', closeModal);
    modal.querySelector('.close-config-list-btn').addEventListener('click', closeModal);
    
    modal.querySelector('.add-new-config-btn').addEventListener('click', () => {
        modal.remove();
        taskConfigManager.showTaskConfigDialog(taskId)
            .then(config => {
                appState.addTaskConfig(taskId, config);
                notificationService.showNotification('Configuration saved successfully', 'success');
                showTaskConfigurationDialog(taskId); // Reopen to show updated list
            })
            .catch(error => {
                if (error.message !== 'Cancelled') {
                    console.error('[showTaskConfigurationDialog] Error saving config:', error);
                }
                showTaskConfigurationDialog(taskId); // Reopen even if cancelled
            });
    });
    
    // Edit config buttons
    modal.querySelectorAll('.edit-config-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const configName = btn.getAttribute('data-config-name');
            const existingConfig = appState.getTaskConfig(taskId, configName);
            modal.remove();
            taskConfigManager.showTaskConfigDialog(taskId, existingConfig)
                .then(config => {
                    appState.addTaskConfig(taskId, config);
                    notificationService.showNotification('Configuration updated successfully', 'success');
                    showTaskConfigurationDialog(taskId);
                })
                .catch(error => {
                    if (error.message !== 'Cancelled') {
                        console.error('[showTaskConfigurationDialog] Error updating config:', error);
                    }
                    showTaskConfigurationDialog(taskId);
                });
        });
    });
    
    // Delete config buttons
    modal.querySelectorAll('.delete-config-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const configName = btn.getAttribute('data-config-name');
            if (confirm(`Delete configuration "${configName}"?`)) {
                appState.removeTaskConfig(taskId, configName);
                notificationService.showNotification('Configuration deleted', 'success');
                showTaskConfigurationDialog(taskId);
            }
        });
    });
    
    // Close on outside click
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeModal();
        }
    });
}

function populateLocalTools() {
    const localToolsList = document.getElementById('local-tools-list');
    if (!localToolsList) return;

    localToolsList.innerHTML = '';

    if (appState.apiSettings.localTools && appState.apiSettings.localTools.length > 0) {
        appState.apiSettings.localTools.forEach((tool, index) => {
            const toolItem = document.createElement('div');
            toolItem.className = 'tool-item';
            toolItem.innerHTML = `
                <span>${tool}</span>
                <button class="button secondary small" onclick="removeLocalTool(${index})">Remove</button>
            `;
            localToolsList.appendChild(toolItem);
        });
    } else {
        localToolsList.innerHTML = '<p>No local tools configured</p>';
    }
}

function addLocalTool(toolPath) {
    if (!appState.apiSettings.localTools) {
        appState.apiSettings.localTools = [];
    }

    if (!appState.apiSettings.localTools.includes(toolPath)) {
        appState.apiSettings.localTools.push(toolPath);
        populateLocalTools();
    }
}

function removeLocalTool(index) {
    if (appState.apiSettings.localTools && index >= 0 && index < appState.apiSettings.localTools.length) {
        appState.apiSettings.localTools.splice(index, 1);
        populateLocalTools();
    }
}

function resetUserSettings() {
    appState.apiSettings = {apiKeys: {}, apiBase: {}, localTools: []};
    populateUserSettings();
}

function launchSession() {
    console.log('[launchSession] Launching session...');
    const cognitiveMode = appState.cognitiveMode || 'chat';
    const appPath = '/taskChat';

    const settings = {
        ...appState.taskSettings,
        sessionId: appState.sessionId,
        cognitiveMode: cognitiveMode
    };
    httpService.saveSessionSettings(appState.sessionId, settings)
        .then(() => {
            console.log(`[launchSession] Session settings saved, redirecting to ${appPath}...`);
            window.location.href = `${appPath}/#${appState.sessionId}`;
        })
        .catch(error => {
            console.error('[launchSession] Error launching session:', error);
            notificationService.showNotification('Error launching session: ' + error.message, 'error');
        });
}