// These will be populated from the API
let apiProviders = [];
let availableModels = {};
let cognitiveTypes = [];
let appDirectory = [];
let pluginManagerState = {
     loadedPlugins: [],
     availableJars: []
};

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

// ===== Main Initialization =====
document.addEventListener('DOMContentLoaded', function () {
    setupBasicChatModal();
    setupSettingsSection();
    setupCustomPipelineModal();
    setupUserSettingsModal();
     setupPluginManagerModal();
     loadAppDirectory().then(() => {
         renderAppGrid();
         setupAppCards();
     }).catch(error => {
         console.error('[init] Error loading app directory:', error);
     });
});

// Load API providers and models first, then initialize everything
loadApiProviders().then(() => {
    uiManager.setupTooltips();
    return loadUserSettings();
}).then(() => {
    modelManager.populateModelSelections();
    populateQuickSettingsModels();
    return loadCognitiveTypes();
}).catch(error => {
    console.error('[init] Error during initialization:', error);
    uiManager.setupTooltips();
    loadUserSettings().then(() => {
        populateQuickSettingsModels();
    });
});
// ===== App Directory Loading =====
async function loadAppDirectory() {
     console.log('[loadAppDirectory] Loading app directory...');
     try {
         const response = await fetch('/appDirectory');
         if (response.ok) {
             appDirectory = await response.json();
             console.log('[loadAppDirectory] Loaded', appDirectory.length, 'apps');
         } else {
             console.error('[loadAppDirectory] Failed to load apps.json:', response.status);
             appDirectory = [];
         }
     } catch (error) {
         console.error('[loadAppDirectory] Error:', error);
         appDirectory = [];
     }
}
function renderAppGrid() {
     const grid = document.getElementById('app-grid');
     if (!grid) return;
     grid.innerHTML = '';
     appDirectory.forEach(app => {
         const card = document.createElement('a');
         card.href = '#';
         card.className = 'app-card' + (app.cardClass ? ' ' + app.cardClass : '');
         card.id = app.id;
         let badgeHtml = '';
         if (app.badge) {
             badgeHtml = `<div class="app-card-badge${app.badgeClass ? ' ' + app.badgeClass : ''}">${app.badge}</div>`;
         }
         card.innerHTML = `
             <div class="app-card-icon">${app.icon}</div>
             <div class="app-card-body">
                 <h3>${app.name}</h3>
                 <p>${app.description}</p>
             </div>
             ${badgeHtml}
         `;
         grid.appendChild(card);
     });
}


// ===== App Card Click Handlers =====
function setupAppCards() {
    // Basic Chat


     appDirectory.forEach(app => {
         const element = document.getElementById(app.id);
         if (!element) return;

         if (app.type === 'chat') {
             element.addEventListener('click', function (e) {
                 e.preventDefault();
                 populateBasicChatModelSelections();
                 prefillBasicChatModal();
                 document.getElementById('basic-chat-settings-modal').style.display = 'block';
             });
         } else if (app.type === 'docops') {
             element.addEventListener('click', function (e) {
                 e.preventDefault();
                 const docopsSessionId = Utils.generateSessionId();
                 console.log(`[setupAppCards] Launching ${app.id} with session:`, docopsSessionId);
                 window.location.href = `${app.path}/fileIndex/${docopsSessionId}/app.html`;
             });
         } else if (app.type === 'pipeline') {
             element.addEventListener('click', function (e) {
                 e.preventDefault();
                 document.getElementById('custom-pipeline-modal').style.display = 'block';
                 modelManager.populateModelSelections();
             });
         }
    });

}

// ===== Quick Settings Section =====
function setupSettingsSection() {
    const header = document.getElementById('settings-header-toggle');
    const body = document.getElementById('settings-body');
    const icon = document.getElementById('settings-toggle-icon');

    if (header && body && icon) {
        header.addEventListener('click', function () {
            body.classList.toggle('open');
            icon.classList.toggle('open');
        });
    }

    // Temperature slider
    const tempSlider = document.getElementById('default-temperature');
    const tempValue = document.getElementById('default-temperature-value');
    if (tempSlider && tempValue) {
        tempSlider.addEventListener('input', function () {
            tempValue.textContent = this.value;
        });
        // Load saved value
        const savedTemp = localStorage.getItem('temperature') || '0.3';
        tempSlider.value = savedTemp;
        tempValue.textContent = savedTemp;
    }

    // Budget
    const budgetInput = document.getElementById('default-budget');
    if (budgetInput) {
        budgetInput.value = localStorage.getItem('budget') || '2.0';
    }

    // Save button
    document.getElementById('save-quick-settings')?.addEventListener('click', function () {
        const smartModel = document.getElementById('default-smart-model')?.value;
        const fastModel = document.getElementById('default-fast-model')?.value;
        const imageModel = document.getElementById('default-image-model')?.value;
        const temperature = document.getElementById('default-temperature')?.value;
        const budget = document.getElementById('default-budget')?.value;

        if (smartModel) localStorage.setItem('smartModel', smartModel);
        if (fastModel) localStorage.setItem('fastModel', fastModel);
        if (imageModel) localStorage.setItem('imageModel', imageModel);
        if (temperature) localStorage.setItem('temperature', temperature);
        if (budget) localStorage.setItem('budget', budget);

        notificationService.showNotification('Default settings saved', 'success');
    });
}

function populateQuickSettingsModels() {
    const smartSelect = document.getElementById('default-smart-model');
    const fastSelect = document.getElementById('default-fast-model');
    const imageSelect = document.getElementById('default-image-model');

    if (!smartSelect || !fastSelect || !imageSelect) return;

    [smartSelect, fastSelect, imageSelect].forEach(sel => sel.innerHTML = '');

    const addedModels = new Set();

    if (appState.apiSettings && appState.apiSettings.apiKeys) {
        for (const [provider, key] of Object.entries(appState.apiSettings.apiKeys)) {
            if (key && availableModels[provider]) {
                availableModels[provider].forEach(model => {
                    if (!addedModels.has(model.id)) {
                        [smartSelect, fastSelect, imageSelect].forEach(sel => {
                            const option = document.createElement('option');
                            option.value = model.id;
                            option.textContent = `${model.name} (${provider})`;
                            sel.appendChild(option);
                        });
                        addedModels.add(model.id);
                    }
                });
            }
        }
    }

    if (smartSelect.options.length === 0) {
        [smartSelect, fastSelect, imageSelect].forEach(sel => {
            const opt = document.createElement('option');
            opt.value = '';
            opt.textContent = 'Configure API keys in Settings';
            sel.appendChild(opt);
        });
    }

    // Restore saved selections
    const savedSmart = localStorage.getItem('smartModel');
    const savedFast = localStorage.getItem('fastModel');
    const savedImage = localStorage.getItem('imageModel');

    if (savedSmart && Array.from(smartSelect.options).some(o => o.value === savedSmart)) {
        smartSelect.value = savedSmart;
    }
    if (savedFast && Array.from(fastSelect.options).some(o => o.value === savedFast)) {
        fastSelect.value = savedFast;
    }
    if (savedImage && Array.from(imageSelect.options).some(o => o.value === savedImage)) {
        imageSelect.value = savedImage;
    }
}

// ===== Basic Chat Modal =====
function setupBasicChatModal() {
    const modal = document.getElementById('basic-chat-settings-modal');
    const closeBtn = document.getElementById('close-basic-chat-modal');
    const cancelBtn = document.getElementById('cancel-basic-chat-settings');
    const form = document.getElementById('basic-chat-settings-form');
    const tempSlider = document.getElementById('basic-chat-temperature');
    const tempValue = document.getElementById('basic-chat-temperature-value');

    if (tempSlider && tempValue) {
        tempSlider.addEventListener('input', function () {
            tempValue.textContent = this.value;
        });
    }

    closeBtn?.addEventListener('click', () => modal.style.display = 'none');
    cancelBtn?.addEventListener('click', () => modal.style.display = 'none');

    window.addEventListener('click', function (event) {
        if (event.target === modal) modal.style.display = 'none';
    });

    form?.addEventListener('submit', function (e) {
        e.preventDefault();

        const model = document.getElementById('basic-chat-model').value;
        const fastModel = document.getElementById('basic-chat-parsing-model').value;
        const temperatureInput = document.getElementById('basic-chat-temperature').value;
        const budgetInput = document.getElementById('basic-chat-budget').value;

        if (!model || !fastModel || !temperatureInput || !budgetInput) {
            notificationService.showNotification('Please fill in all required fields', 'error');
            return;
        }

        const temperature = parseFloat(temperatureInput);
        const budget = parseFloat(budgetInput);

        if (isNaN(temperature) || isNaN(budget)) {
            notificationService.showNotification('Temperature and budget must be valid numbers', 'error');
            return;
        }

        // Save to localStorage
        localStorage.setItem('smartModel', model);
        localStorage.setItem('fastModel', fastModel);
        localStorage.setItem('temperature', String(temperature));
        localStorage.setItem('budget', String(budget));
        localStorage.setItem('basicChatModel', model);
        localStorage.setItem('basicChatParsingModel', fastModel);
        localStorage.setItem('basicChatTemperature', String(temperature));
        localStorage.setItem('basicChatBudget', String(budget));

        const chatSessionId = sessionId;

        httpService.saveChatSettings(chatSessionId, {
            model: model,
            fastModel: fastModel,
            temperature: temperature,
            budget: budget
        }).then(response => {
            if (response) {
                modal.style.display = 'none';
                window.location.href = `/chat/#${chatSessionId}`;
            }
        }).catch(error => {
            console.error('[BasicChat] Error saving chat settings:', error);
            notificationService.showNotification('Error saving chat settings: ' + error.message, 'error');
        });
    });
}

function prefillBasicChatModal() {
    const smartModel = localStorage.getItem('smartModel') || localStorage.getItem('basicChatModel') || 'GPT4o';
    const fastModel = localStorage.getItem('fastModel') || localStorage.getItem('basicChatParsingModel') || 'GPT4oMini';
    const temperature = localStorage.getItem('temperature') || localStorage.getItem('basicChatTemperature') || '0.3';
    const budget = localStorage.getItem('budget') || localStorage.getItem('basicChatBudget') || '2.0';

    document.getElementById('basic-chat-model').value = smartModel;
    document.getElementById('basic-chat-parsing-model').value = fastModel;
    document.getElementById('basic-chat-temperature').value = temperature;
    document.getElementById('basic-chat-temperature-value').textContent = temperature;
    document.getElementById('basic-chat-budget').value = budget;
}

function populateBasicChatModelSelections() {
    const modelSelect = document.getElementById('basic-chat-model');
    const parsingModelSelect = document.getElementById('basic-chat-parsing-model');
    if (!modelSelect || !parsingModelSelect) return;

    const prevModel = modelSelect.value;
    const prevParsingModel = parsingModelSelect.value;

    modelSelect.innerHTML = '';
    parsingModelSelect.innerHTML = '';
    const addedModels = new Set();

    if (appState.apiSettings && appState.apiSettings.apiKeys) {
        for (const [provider, key] of Object.entries(appState.apiSettings.apiKeys)) {
            if (key && availableModels[provider]) {
                availableModels[provider].forEach(model => {
                    if (!addedModels.has(model.id)) {
                        [modelSelect, parsingModelSelect].forEach(sel => {
                            const option = document.createElement('option');
                            option.value = model.id;
                            option.textContent = `${model.name} (${provider})`;
                            option.title = model.description;
                            sel.appendChild(option);
                        });
                        addedModels.add(model.id);
                    }
                });
            }
        }
    }

    if (modelSelect.options.length === 0) {
        const defaultOption = document.createElement('option');
        defaultOption.value = 'GPT4o';
        defaultOption.textContent = 'GPT-4o (OpenAI) - Configure API key';
        modelSelect.appendChild(defaultOption);
        const defaultParsingOption = document.createElement('option');
        defaultParsingOption.value = 'GPT4oMini';
        defaultParsingOption.textContent = 'GPT-4o Mini (OpenAI) - Configure API key';
        parsingModelSelect.appendChild(defaultParsingOption);
    }

    if (prevModel && Array.from(modelSelect.options).some(opt => opt.value === prevModel)) {
        modelSelect.value = prevModel;
    }
    if (prevParsingModel && Array.from(parsingModelSelect.options).some(opt => opt.value === prevParsingModel)) {
        parsingModelSelect.value = prevParsingModel;
    }
}

// ===== Custom Pipeline Modal =====
function setupCustomPipelineModal() {
    const modal = document.getElementById('custom-pipeline-modal');
    const closeBtn = document.getElementById('close-pipeline-modal');

    closeBtn?.addEventListener('click', () => modal.style.display = 'none');

    window.addEventListener('click', function (event) {
        if (event.target === modal) modal.style.display = 'none';
    });

    // Temperature slider in wizard
    const tempSlider = document.getElementById('temperature');
    const tempValue = document.getElementById('temperature-value');
    if (tempSlider && tempValue) {
        tempSlider.addEventListener('input', function () {
            tempValue.textContent = this.value;
        });
    }

    // Working directory generator
    document.getElementById('generate-working-dir')?.addEventListener('click', () => {
        const workingDirInput = document.getElementById('working-dir');
        if (workingDirInput) {
            workingDirInput.value = Utils.generateCognotikWorkingDir();
        }
    });

    // Wizard navigation
    document.getElementById('next-to-task-settings')?.addEventListener('click', () => {
        const modeInput = document.querySelector('input[name="cognitive-mode"]:checked');
        if (modeInput) {
            const mode = modeInput.value;
            appState.cognitiveSettings = collectCognitiveSettings(mode);
            appState.updateCognitiveMode(mode);
            navigatePipelineStep('task-settings');
        }
    });

    document.getElementById('next-to-task-selection')?.addEventListener('click', () => {
        appState.updateTaskSetting('smartModel', document.getElementById('model-selection')?.value);
        appState.updateTaskSetting('fastModel', document.getElementById('parsing-model')?.value);
        appState.updateTaskSetting('imageModel', document.getElementById('image-model')?.value);
        appState.updateTaskSetting('workingDir', document.getElementById('working-dir')?.value);
        appState.updateTaskSetting('temperature', parseFloat(document.getElementById('temperature')?.value));
        appState.updateTaskSetting('autoFix', document.getElementById('auto-fix')?.checked);
        appState.updateTaskSetting('graphFile', document.getElementById('graph-file')?.value);
        populateTaskSelection();
        navigatePipelineStep('task-selection');
    });

    document.getElementById('next-to-launch')?.addEventListener('click', () => {
        navigatePipelineStep('launch');
        uiManager.updateLaunchSummaries();
    });

    document.getElementById('back-to-cognitive-mode')?.addEventListener('click', () => navigatePipelineStep('cognitive-mode'));
    document.getElementById('back-to-task-settings')?.addEventListener('click', () => navigatePipelineStep('task-settings'));
    document.getElementById('back-to-task-selection')?.addEventListener('click', () => navigatePipelineStep('task-selection'));

    document.getElementById('launch-session')?.addEventListener('click', () => {
        if (validationService.validateConfiguration()) {
            launchSession();
        }
    });
}

function navigatePipelineStep(stepId) {
    // Update wizard nav
    const modal = document.getElementById('custom-pipeline-modal');
    if (!modal) return;

    modal.querySelectorAll('.wizard-step').forEach(step => {
        step.classList.remove('active');
        if (step.getAttribute('data-step') === stepId) {
            step.classList.add('active');
        }
    });

    modal.querySelectorAll('.wizard-content').forEach(content => {
        content.classList.remove('active');
    });

    const targetContent = document.getElementById(stepId);
    if (targetContent) {
        targetContent.classList.add('active');
    }
}

// ===== User Settings Modal =====
function setupUserSettingsModal() {
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

    window.addEventListener('click', function (event) {
        if (event.target === userSettingsModal) {
            userSettingsModal.style.display = 'none';
        }
    });

    // Tab switching
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', () => {
            const tabId = button.getAttribute('data-tab');
            switchTab(tabId);
        });
    });

    // Local tools
    document.getElementById('add-local-tool')?.addEventListener('click', () => {
        const toolPath = document.getElementById('new-tool-path')?.value;
        if (toolPath) {
            addLocalTool(toolPath);
            document.getElementById('new-tool-path').value = '';
        }
    });

    // Reset
    document.getElementById('reset-user-settings')?.addEventListener('click', () => {
        if (confirm('Are you sure you want to reset all settings?')) {
            resetUserSettings();
        }
    });

    // Save
    document.getElementById('save-user-settings')?.addEventListener('click', () => {
        saveUserSettings();
    });
}

// ===== Notification =====
function showNotification(message, type = 'info') {
    return uiManager.showNotification(message, type);
}

// ===== API Loading =====
async function loadApiProviders() {
    console.log('[loadApiProviders] Loading API providers from server...');
    try {
         const response = await fetch('apiProviders/?format=json');
          if (response.redirected || response.status >= 300) {
              console.warn('[loadApiProviders] Detected redirect (redirected:', response.redirected, ', status:', response.status, ', url:', response.url, ') - redirecting to login');
             window.location.href = 'login/';
             return;
         }
         const providersResponse = await response.json();

        const providers = providersResponse.configuredProviders || [];
        const availableProvidersList = providersResponse.availableProviders || [];

        apiProviders = providers.map(provider => ({
            id: provider.name,
            name: provider.name,
            baseUrl: provider.baseUrl
        }));

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

        if (typeof API_PROVIDERS !== 'undefined') {
            API_PROVIDERS.length = 0;
            API_PROVIDERS.push(...apiProviders);
        }
        if (typeof AVAILABLE_MODELS !== 'undefined') {
            Object.keys(AVAILABLE_MODELS).forEach(key => delete AVAILABLE_MODELS[key]);
            Object.assign(AVAILABLE_MODELS, availableModels);
        }

        window.allAvailableProviders = availableProvidersList;
    } catch (error) {
        console.error('[loadApiProviders] Error:', error);
        apiProviders = [];
        availableModels = {};
        window.allAvailableProviders = [];
        throw error;
    }
}

function loadUserSettings() {
    console.log('[loadUserSettings] Loading user settings...');
    return httpService.getUserSettings()
        .then(settingsText => {
            try {
                const settings = JSON.parse(settingsText);
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
                    appState.apiSettings = settings;
                }

                if (appState.apiSettings.configuredApis && Array.isArray(appState.apiSettings.configuredApis)) {
                    appState.apiSettings.configuredApis.forEach(api => {
                        if (!apiProviders.find(p => p.id === api.provider)) {
                            apiProviders.push({
                                id: api.provider,
                                name: api.provider,
                                baseUrl: api.baseUrl
                            });
                        }
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

async function loadCognitiveTypes() {
    console.log('[loadCognitiveTypes] Loading cognitive types...');
    try {
        const response = await fetch('/cognitiveConfig/');
        if (response.ok) {
            cognitiveTypes = await response.json();
            renderCognitiveModeSelection();
        }
    } catch (e) {
        console.error('Error loading cognitive types:', e);
    }
}

// ===== User Settings Helpers =====
function populateUserSettings() {
    const apiKeysContainer = document.getElementById('api-keys-container');
    if (apiKeysContainer) {
        apiKeysContainer.innerHTML = '';

        const desc = document.createElement('p');
        desc.textContent = 'Add API keys for the providers you want to use.';
        desc.style.fontSize = '0.9em';
        desc.style.color = '#666';
        desc.style.marginBottom = '15px';
        apiKeysContainer.appendChild(desc);

        const apiProvidersList = document.createElement('div');
        apiProvidersList.id = 'api-providers-list';

        if (appState.apiSettings.apiKeys) {
            Object.keys(appState.apiSettings.apiKeys).forEach(providerId => {
                const apiKey = appState.apiSettings.apiKeys[providerId];
                const baseUrl = appState.apiSettings.apiBase ? appState.apiSettings.apiBase[providerId] : '';
                apiProvidersList.appendChild(createProviderInput(providerId, apiKey || '', baseUrl || ''));
            });
        }

        if (appState.apiSettings.apiBase) {
            Object.keys(appState.apiSettings.apiBase).forEach(providerId => {
                if (!appState.apiSettings.apiKeys || !appState.apiSettings.apiKeys[providerId]) {
                    const baseUrl = appState.apiSettings.apiBase[providerId];
                    apiProvidersList.appendChild(createProviderInput(providerId, '', baseUrl || ''));
                }
            });
        }

        apiKeysContainer.appendChild(apiProvidersList);

        const addProviderBtn = document.createElement('button');
        addProviderBtn.className = 'button secondary';
        addProviderBtn.textContent = '+ Add API Provider';
        addProviderBtn.style.marginTop = '10px';
        addProviderBtn.addEventListener('click', () => {
            apiProvidersList.appendChild(createProviderInput('', '', ''));
        });
        apiKeysContainer.appendChild(addProviderBtn);
    }

    populateLocalTools();
}

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

    const emptyOption = document.createElement('option');
    emptyOption.value = '';
    emptyOption.textContent = 'Select a provider...';
    providerSelect.appendChild(emptyOption);

    const providersToShow = window.allAvailableProviders || apiProviders;
    const providerMap = new Map();

    providersToShow.forEach(provider => providerMap.set(provider.id, provider.name));

    if (appState.apiSettings.configuredApis && Array.isArray(appState.apiSettings.configuredApis)) {
        appState.apiSettings.configuredApis.forEach(api => {
            if (!providerMap.has(api.provider)) providerMap.set(api.provider, api.provider);
        });
    }

    if (appState.apiSettings.apiKeys) {
        Object.keys(appState.apiSettings.apiKeys).forEach(id => {
            if (!providerMap.has(id)) providerMap.set(id, id);
        });
    }

    if (appState.apiSettings.apiBase) {
        Object.keys(appState.apiSettings.apiBase).forEach(id => {
            if (!providerMap.has(id)) providerMap.set(id, id);
        });
    }

    if (apiProviders && Array.isArray(apiProviders)) {
        apiProviders.forEach(provider => {
            if (!providerMap.has(provider.id)) providerMap.set(provider.id, provider.name);
        });
    }

    Array.from(providerMap.entries()).sort((a, b) => a[1].localeCompare(b[1])).forEach(([id, name]) => {
        const option = document.createElement('option');
        option.value = id;
        option.textContent = name;
        if (id === providerId) option.selected = true;
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
    removeBtn.addEventListener('click', () => keyGroup.remove());

    keyGroup.appendChild(providerSelectGroup);
    keyGroup.appendChild(apiKeyGroup);
    keyGroup.appendChild(baseUrlGroup);
    keyGroup.appendChild(removeBtn);
    return keyGroup;
}

function saveUserSettings() {
    const apiKeys = {};
    const apiBase = {};

    document.querySelectorAll('.provider-group').forEach(group => {
        const selectInput = group.querySelector('.provider-select');
        const keyInput = group.querySelector('.provider-key');
        const baseUrlInput = group.querySelector('.provider-base-url');
        if (selectInput && keyInput && selectInput.value) {
            apiKeys[selectInput.value] = keyInput.value;
            if (baseUrlInput && baseUrlInput.value) {
                apiBase[selectInput.value] = baseUrlInput.value;
            }
        }
    });

    appState.apiSettings.apiKeys = apiKeys;
    appState.apiSettings.apiBase = apiBase;

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

    httpService.saveUserSettings(settingsToSave)
        .then(() => {
            notificationService.showNotification('Settings saved successfully', 'success');
            document.getElementById('user-settings-modal').style.display = 'none';
            modelManager.populateModelSelections();
            populateQuickSettingsModels();
            populateBasicChatModelSelections();
        })
        .catch(error => {
            console.error('[saveUserSettings] Error:', error);
            notificationService.showNotification('Error saving settings: ' + error.message, 'error');
        });
}

function switchTab(tabId) {
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    document.querySelectorAll('.tab-button').forEach(b => b.classList.remove('active'));

    const selectedContent = document.getElementById(`${tabId}-tab`);
    if (selectedContent) selectedContent.classList.add('active');

    const selectedButton = document.querySelector(`[data-tab="${tabId}"]`);
    if (selectedButton) selectedButton.classList.add('active');
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
    if (!appState.apiSettings.localTools) appState.apiSettings.localTools = [];
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
// ===== Plugin Manager =====
function setupPluginManagerModal() {
     const pluginManagerBtn = document.getElementById('plugin-manager-btn');
     const modal = document.getElementById('plugin-manager-modal');
     const closeBtn = document.getElementById('close-plugin-manager-modal');
     pluginManagerBtn?.addEventListener('click', () => {
         modal.style.display = 'block';
         pluginManagerRefreshLoaded();
         pluginManagerRefreshAuthChains();
     });
     closeBtn?.addEventListener('click', () => {
         modal.style.display = 'none';
     });
     window.addEventListener('click', function (event) {
         if (event.target === modal) modal.style.display = 'none';
     });
     // Tab switching
     document.querySelectorAll('[data-plugin-tab]').forEach(button => {
         button.addEventListener('click', () => {
             const tabId = button.getAttribute('data-plugin-tab');
             switchPluginTab(tabId);
         });
     });
     // Refresh loaded plugins
     document.getElementById('refresh-loaded-plugins')?.addEventListener('click', pluginManagerRefreshLoaded);
     // Scan directory
     document.getElementById('scan-plugin-directory')?.addEventListener('click', pluginManagerScanDirectory);
     // Load all from directory
     document.getElementById('load-all-plugins')?.addEventListener('click', pluginManagerLoadDirectory);
     // Upload plugin
     document.getElementById('upload-plugin-btn')?.addEventListener('click', pluginManagerUpload);
     // Load by path
     document.getElementById('load-plugin-by-path-btn')?.addEventListener('click', pluginManagerLoadByPath);
     // Authorization chains
     document.getElementById('refresh-auth-chains')?.addEventListener('click', pluginManagerRefreshAuthChains);
}
function switchPluginTab(tabId) {
     document.querySelectorAll('#plugin-manager-modal .tab-content').forEach(c => c.classList.remove('active'));
     document.querySelectorAll('#plugin-manager-modal [data-plugin-tab]').forEach(b => b.classList.remove('active'));
     const content = document.getElementById(`${tabId}-tab`);
     if (content) content.classList.add('active');
     const btn = document.querySelector(`[data-plugin-tab="${tabId}"]`);
     if (btn) btn.classList.add('active');
}
function showPluginMessage(message, type = 'info') {
     const el = document.getElementById('plugin-manager-message');
     if (!el) return;
     el.textContent = message;
     el.className = '';
     el.style.display = 'block';
     el.style.padding = '10px 14px';
     el.style.borderRadius = '6px';
     el.style.marginBottom = '14px';
     el.style.fontWeight = '500';
     if (type === 'success') {
         el.style.background = '#d4edda';
         el.style.color = '#155724';
         el.style.border = '1px solid #c3e6cb';
     } else if (type === 'error') {
         el.style.background = '#f8d7da';
         el.style.color = '#721c24';
         el.style.border = '1px solid #f5c6cb';
     } else {
         el.style.background = '#d1ecf1';
         el.style.color = '#0c5460';
         el.style.border = '1px solid #bee5eb';
     }
     clearTimeout(el._hideTimer);
     el._hideTimer = setTimeout(() => { el.style.display = 'none'; }, 6000);
}
function pluginManagerRefreshLoaded() {
     const container = document.getElementById('loaded-plugins-content');
     if (!container) return;
     container.innerHTML = '<em>Loading…</em>';
    fetch('/pluginManager/?action=list', { headers: { 'Accept': 'application/json' } })
         .then(r => {
             if (!r.ok) throw new Error(`HTTP ${r.status}`);
             return r.json();
         })
         .then(data => {
             pluginManagerState.loadedPlugins = data || [];
             if (!data || data.length === 0) {
                 container.innerHTML = '<p style="color:#888; text-align:center; padding:20px;">No plugins currently loaded.</p>';
                 return;
             }
             let html = `
                 <table style="width:100%; border-collapse:collapse;">
                     <thead>
                         <tr style="background:#f0f0f0;">
                             <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">JAR File</th>
                             <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">Plugins</th>
                             <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Actions</th>
                         </tr>
                     </thead>
                     <tbody>
             `;
             data.forEach(entry => {
                 const pluginList = (entry.plugins || [])
                     .map(p => `<div><strong>${escapeHtml(p.name)}</strong> <small style="color:#888;">${escapeHtml(p.class)}</small></div>`)
                     .join('') || '<em style="color:#aaa;">none</em>';
                 const jarName = entry.jar ? entry.jar.split(/[\\/]/).pop() : entry.jar;
                 html += `
                     <tr style="border-bottom:1px solid #eee;">
                         <td style="padding:10px 12px; font-family:monospace; font-size:0.9em;" title="${escapeHtml(entry.jar || '')}">
                             ${escapeHtml(jarName || '')}
                         </td>
                         <td style="padding:10px 12px;">${pluginList}</td>
                         <td style="padding:10px 12px; text-align:center;">
                             <button class="button secondary" style="font-size:0.85em; padding:5px 12px; margin:2px;"
                                 data-jar-path="${escapeHtml(entry.jar || '')}" data-action="unload">
                                 Unload
                             </button>
                             <button class="button secondary" style="font-size:0.85em; padding:5px 12px; margin:2px; background:#dc3545; color:#fff;"
                                 data-jar-path="${escapeHtml(entry.jar || '')}" data-action="delete">
                                 Delete
                             </button>
                         </td>
                     </tr>
                 `;
             });
             html += '</tbody></table>';
             container.innerHTML = html;
             container.querySelectorAll('button[data-action="unload"]').forEach(btn => {
                 btn.addEventListener('click', () => pluginManagerUnload(btn.getAttribute('data-jar-path')));
             });
             container.querySelectorAll('button[data-action="delete"]').forEach(btn => {
                 btn.addEventListener('click', () => pluginManagerDelete(btn.getAttribute('data-jar-path')));
             });
         })
         .catch(e => {
             container.innerHTML = `<p style="color:#c0392b;">Error loading plugins: ${escapeHtml(e.message)}</p>`;
         });
}
function pluginManagerScanDirectory() {
     const container = document.getElementById('available-jars-content');
     if (!container) return;
     container.innerHTML = '<em>Scanning…</em>';
    fetch('/pluginManager/?action=scan', { headers: { 'Accept': 'application/json' } })
         .then(r => {
             if (!r.ok) throw new Error(`HTTP ${r.status}`);
             return r.json();
         })
         .then(data => {
             pluginManagerState.availableJars = data || [];
             if (!data || data.length === 0) {
                 container.innerHTML = '<p style="color:#888; text-align:center; padding:20px;">No JAR files found in plugin directory.</p>';
                 return;
             }
             let html = `
                 <table style="width:100%; border-collapse:collapse;">
                     <thead>
                         <tr style="background:#f0f0f0;">
                             <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">File</th>
                             <th style="text-align:right; padding:8px 12px; border-bottom:2px solid #ddd;">Size</th>
                             <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Status</th>
                             <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Actions</th>
                         </tr>
                     </thead>
                     <tbody>
             `;
             data.forEach(entry => {
                 const sizeKb = (entry.size / 1024).toFixed(1);
                 const statusBadge = entry.loaded
                     ? '<span style="background:#d4edda;color:#155724;padding:2px 8px;border-radius:10px;font-size:0.8em;font-weight:600;">Loaded</span>'
                     : '<span style="background:#f8d7da;color:#721c24;padding:2px 8px;border-radius:10px;font-size:0.8em;font-weight:600;">Not Loaded</span>';
                 const loadBtn = !entry.loaded
                     ? `<button class="button" style="font-size:0.85em;padding:5px 12px;"
                             data-jar-name="${escapeHtml(entry.name || '')}" data-action="load">Load</button>`
                     : '';
                 const unloadBtn = entry.loaded
                     ? `<button class="button secondary" style="font-size:0.85em;padding:5px 12px;margin:2px;"
                             data-jar-path="${escapeHtml(entry.path || '')}" data-action="unload">Unload</button>`
                     : '';
                 const deleteBtn = `<button class="button secondary" style="font-size:0.85em;padding:5px 12px;margin:2px;background:#dc3545;color:#fff;"
                         data-jar-path="${escapeHtml(entry.path || '')}" data-action="delete">Delete</button>`;
                 html += `
                     <tr style="border-bottom:1px solid #eee;">
                         <td style="padding:10px 12px; font-family:monospace; font-size:0.9em;">${escapeHtml(entry.name || '')}</td>
                         <td style="padding:10px 12px; text-align:right; color:#666;">${sizeKb} KB</td>
                         <td style="padding:10px 12px; text-align:center;">${statusBadge}</td>
                         <td style="padding:10px 12px; text-align:center;">${loadBtn}${unloadBtn}${deleteBtn}</td>
                     </tr>
                 `;
             });
             html += '</tbody></table>';
             container.innerHTML = html;
             container.querySelectorAll('button[data-action="load"]').forEach(btn => {
                 btn.addEventListener('click', () => pluginManagerLoadJar(btn.getAttribute('data-jar-name')));
             });
             container.querySelectorAll('button[data-action="unload"]').forEach(btn => {
                 btn.addEventListener('click', () => pluginManagerUnload(btn.getAttribute('data-jar-path')));
             });
             container.querySelectorAll('button[data-action="delete"]').forEach(btn => {
                 btn.addEventListener('click', () => pluginManagerDelete(btn.getAttribute('data-jar-path')));
             });
         })
         .catch(e => {
             container.innerHTML = `<p style="color:#c0392b;">Error scanning directory: ${escapeHtml(e.message)}</p>`;
         });
}
function pluginManagerLoadJar(jarName) {
     if (!jarName) return;
    fetch('/pluginManager/', {
         method: 'POST',
          body: new URLSearchParams({ action: 'load', jar: jarName }),
          headers: { 'Accept': 'application/json' }
     })
          .then(r => {
              if (!r.ok) {
                  return r.text().then(text => {
                      try { return JSON.parse(text); } catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
                  });
              }
              return r.json();
          })
         .then(data => {
             if (data.success) {
                 showPluginMessage(`Loaded ${data.pluginsLoaded} plugin(s): ${(data.plugins || []).join(', ')}`, 'success');
                 pluginManagerRefreshLoaded();
                 pluginManagerScanDirectory();
             } else {
                 showPluginMessage('Error: ' + data.error, 'error');
             }
         })
         .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
}
function pluginManagerUnload(jarPath) {
     if (!jarPath) return;
     const jarName = jarPath.split(/[\\/]/).pop();
     if (!confirm(`Unload plugin "${jarName}"? This may affect running sessions.`)) return;
    fetch('/pluginManager/', {
         method: 'POST',
          body: new URLSearchParams({ action: 'unload', jar: jarPath }),
          headers: { 'Accept': 'application/json' }
     })
          .then(r => {
              if (!r.ok) {
                  return r.text().then(text => {
                      try { return JSON.parse(text); } catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
                  });
              }
              return r.json();
          })
         .then(data => {
             if (data.success) {
                 showPluginMessage(`Plugin unloaded: ${jarName}`, 'success');
                 pluginManagerRefreshLoaded();
                 pluginManagerScanDirectory();
             } else {
                 showPluginMessage('Error: ' + data.error, 'error');
             }
         })
         .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
}
function pluginManagerDelete(jarPath) {
     if (!jarPath) return;
     const jarName = jarPath.split(/[\\/]/).pop();
     if (!confirm(`Delete plugin "${jarName}"?\nThis will permanently remove the file from disk. If loaded, it will be unloaded first.`)) return;
    fetch('/pluginManager/', {
         method: 'POST',
          body: new URLSearchParams({ action: 'delete', jar: jarPath }),
          headers: { 'Accept': 'application/json' }
     })
          .then(r => {
              if (!r.ok) {
                  return r.text().then(text => {
                      try { return JSON.parse(text); } catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
                  });
              }
              return r.json();
          })
         .then(data => {
             if (data.success) {
                 showPluginMessage(`Plugin deleted: ${jarName}`, 'success');
                 pluginManagerRefreshLoaded();
                 pluginManagerScanDirectory();
             } else {
                 showPluginMessage('Error: ' + data.error, 'error');
             }
         })
         .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
}
function pluginManagerLoadDirectory() {
    fetch('/pluginManager/', {
         method: 'POST',
          body: new URLSearchParams({ action: 'loadDirectory' }),
          headers: { 'Accept': 'application/json' }
     })
          .then(r => {
              if (!r.ok) {
                  return r.text().then(text => {
                      try { return JSON.parse(text); } catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
                  });
              }
              return r.json();
          })
         .then(data => {
             if (data.success) {
                 showPluginMessage(`Processed ${data.jarsProcessed} JAR(s) from ${data.directory}`, 'success');
                 pluginManagerRefreshLoaded();
                 pluginManagerScanDirectory();
             } else {
                 showPluginMessage('Error: ' + data.error, 'error');
             }
         })
         .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
}
function pluginManagerUpload() {
     const fileInput = document.getElementById('plugin-jar-file');
     const autoLoad = document.getElementById('plugin-auto-load')?.checked || false;
     const progressEl = document.getElementById('upload-progress');
     const uploadBtn = document.getElementById('upload-plugin-btn');
     if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
         showPluginMessage('Please select a JAR file to upload.', 'error');
         return;
     }
     const file = fileInput.files[0];
     if (!file.name.endsWith('.jar')) {
         showPluginMessage('Only JAR files are supported.', 'error');
         return;
     }
     const formData = new FormData();
     formData.append('action', 'upload');
     formData.append('jarFile', file);
     formData.append('autoLoad', autoLoad ? 'true' : 'false');
     if (progressEl) progressEl.style.display = 'block';
     if (uploadBtn) uploadBtn.disabled = true;
    fetch('/pluginManager/', {
          method: 'POST',
          body: formData,
          headers: { 'Accept': 'application/json' }
      })
          .then(r => {
              if (!r.ok) {
                  return r.text().then(text => {
                      try {
                          return JSON.parse(text);
                      } catch (e) {
                          throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`);
                      }
                  });
              }
              return r.json();
          })
         .then(data => {
             if (progressEl) progressEl.style.display = 'none';
             if (uploadBtn) uploadBtn.disabled = false;
             if (data.success) {
                 let msg = `Uploaded: ${data.file}`;
                 if (data.autoLoaded) msg += ` — loaded ${data.pluginsLoaded} plugin(s): ${(data.plugins || []).join(', ')}`;
                 showPluginMessage(msg, 'success');
                 fileInput.value = '';
                 pluginManagerRefreshLoaded();
                 pluginManagerScanDirectory();
                 switchPluginTab('loaded-plugins');
             } else {
                 showPluginMessage('Error: ' + data.error, 'error');
             }
         })
         .catch(e => {
             if (progressEl) progressEl.style.display = 'none';
             if (uploadBtn) uploadBtn.disabled = false;
             showPluginMessage('Upload failed: ' + e.message, 'error');
         });
}
function pluginManagerLoadByPath() {
     const jarPath = document.getElementById('plugin-jar-path')?.value.trim();
     const entryPoint = document.getElementById('plugin-entry-point')?.value.trim();
     if (!jarPath) {
         showPluginMessage('Please enter a JAR path.', 'error');
         return;
     }
     const params = new URLSearchParams({ action: 'load', jar: jarPath });
     if (entryPoint) params.append('entryPoint', entryPoint);
    fetch('/pluginManager/', {
          method: 'POST',
          body: params,
          headers: { 'Accept': 'application/json' }
      })
          .then(r => {
              if (!r.ok) {
                  return r.text().then(text => {
                      try { return JSON.parse(text); } catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
                  });
              }
              return r.json();
          })
         .then(data => {
             if (data.success) {
                 showPluginMessage(`Loaded ${data.pluginsLoaded} plugin(s): ${(data.plugins || []).join(', ')}`, 'success');
                 document.getElementById('plugin-jar-path').value = '';
                 document.getElementById('plugin-entry-point').value = '';
                 pluginManagerRefreshLoaded();
                 switchPluginTab('loaded-plugins');
             } else {
                 showPluginMessage('Error: ' + data.error, 'error');
             }
         })
         .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
}
// ===== Plugin Manager: Authorization Chains =====
let authPollingTimer = null;
function pluginManagerRefreshAuthChains() {
     const container = document.getElementById('auth-chains-content');
     if (!container) return;
     container.innerHTML = '<em>Loading…</em>';
     fetch('/pluginManager/?action=authChains', { headers: { 'Accept': 'application/json' } })
         .then(r => {
             if (!r.ok) throw new Error(`HTTP ${r.status}`);
             return r.json();
         })
         .then(data => {
             if (!data || data.length === 0) {
                 container.innerHTML = '<p style="color:#888; text-align:center; padding:20px;">No authorization chains registered. Plugins can register authorization flows when loaded.</p>';
                 return;
             }
             let html = `
                 <table style="width:100%; border-collapse:collapse;">
                     <thead>
                         <tr style="background:#f0f0f0;">
                             <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">Chain Name</th>
                             <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Actions</th>
                         </tr>
                     </thead>
                     <tbody>
             `;
             data.forEach(entry => {
                 html += `
                     <tr style="border-bottom:1px solid #eee;">
                         <td style="padding:10px 12px;">
                             <strong>🔐 ${escapeHtml(entry.name)}</strong>
                         </td>
                         <td style="padding:10px 12px; text-align:center;">
                             <button class="button" style="font-size:0.85em; padding:5px 14px;"
                                 data-chain-name="${escapeHtml(entry.name)}" data-action="start-auth">
                                 Start Authorization
                             </button>
                         </td>
                     </tr>
                 `;
             });
             html += '</tbody></table>';
             container.innerHTML = html;
             container.querySelectorAll('button[data-action="start-auth"]').forEach(btn => {
                 btn.addEventListener('click', () => pluginManagerStartAuth(btn.getAttribute('data-chain-name')));
             });
         })
         .catch(e => {
             container.innerHTML = `<p style="color:#c0392b;">Error loading authorization chains: ${escapeHtml(e.message)}</p>`;
         });
}
function pluginManagerStartAuth(chainName) {
     if (!chainName) return;
     showPluginMessage(`Starting authorization for "${chainName}"...`, 'info');
     fetch('/pluginManager/', {
         method: 'POST',
         body: new URLSearchParams({ action: 'startAuth', chain: chainName }),
         headers: { 'Accept': 'application/json' }
     })
         .then(r => {
             if (!r.ok) {
                 return r.text().then(text => {
                     try { return JSON.parse(text); } catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
                 });
             }
             return r.json();
         })
         .then(data => {
             if (data.success && data.sessionId && data.status === 'IN_PROGRESS') {
                 // Show the auth status panel and start polling, or open in new window
                 pluginManagerShowAuthProgress(data.sessionId, chainName, data.currentStep, data.totalSteps);
             } else if (data.success && (data.status === 'completed' || data.status === 'COMPLETED')) {
                 showPluginMessage(`Authorization completed for "${chainName}": No steps required.`, 'success');
                 pluginManagerHideAuthProgress();
             } else if (data.status === 'FAILED') {
                 showPluginMessage(`Authorization failed for "${chainName}": ${data.failureReason || 'Unknown reason'}`, 'error');
                 pluginManagerHideAuthProgress();
             } else {
                 showPluginMessage('Error: ' + (data.error || 'Unknown error'), 'error');
                 pluginManagerHideAuthProgress();
             }
         })
         .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
}
function pluginManagerShowAuthProgress(sessionId, chainName, currentStep, totalSteps) {
     const panel = document.getElementById('auth-status-panel');
     const progressFill = document.getElementById('auth-progress-fill');
     const statusText = document.getElementById('auth-status-text');
     const actionArea = document.getElementById('auth-action-area');
     if (!panel) return;
     panel.style.display = 'block';
     const pct = totalSteps > 0 ? Math.round((currentStep / totalSteps) * 100) : 0;
     progressFill.style.width = pct + '%';
     statusText.textContent = `Authorization "${chainName}" in progress — Step ${currentStep} of ${totalSteps}`;
     actionArea.innerHTML = `
         <button class="button" id="auth-open-flow-btn" style="margin-right:8px;">Open Authorization Page</button>
         <button class="button secondary" id="auth-check-status-btn" style="margin-right:8px;">Check Status</button>
         <button class="button secondary" id="auth-cancel-btn">Close</button>
     `;
     document.getElementById('auth-open-flow-btn')?.addEventListener('click', () => {
         window.open(`/pluginManager?action=authStep&sessionId=${encodeURIComponent(sessionId)}`, '_blank',
             'width=700,height=600,scrollbars=yes,resizable=yes');
     });
     document.getElementById('auth-check-status-btn')?.addEventListener('click', () => {
         pluginManagerCheckAuthStatus(sessionId, chainName);
     });
     document.getElementById('auth-cancel-btn')?.addEventListener('click', () => {
         pluginManagerHideAuthProgress();
         pluginManagerStopAuthPolling();
     });
     // Start polling for status updates
     pluginManagerStartAuthPolling(sessionId, chainName);
}
function pluginManagerHideAuthProgress() {
     const panel = document.getElementById('auth-status-panel');
     if (panel) panel.style.display = 'none';
     pluginManagerStopAuthPolling();
}
function pluginManagerStartAuthPolling(sessionId, chainName) {
     pluginManagerStopAuthPolling();
     authPollingTimer = setInterval(() => {
         pluginManagerCheckAuthStatus(sessionId, chainName, true);
     }, 3000);
}
function pluginManagerStopAuthPolling() {
     if (authPollingTimer) {
         clearInterval(authPollingTimer);
         authPollingTimer = null;
     }
}
function pluginManagerCheckAuthStatus(sessionId, chainName, silent) {
     fetch(`/pluginManager/?action=authStatus&sessionId=${encodeURIComponent(sessionId)}`, {
         headers: { 'Accept': 'application/json' }
     })
         .then(r => {
             if (!r.ok) throw new Error(`HTTP ${r.status}`);
             return r.json();
         })
         .then(data => {
             const progressFill = document.getElementById('auth-progress-fill');
             const statusText = document.getElementById('auth-status-text');
             if (data.isComplete) {
                 pluginManagerStopAuthPolling();
                 if (data.status === 'COMPLETED') {
                     if (progressFill) progressFill.style.width = '100%';
                     if (statusText) statusText.textContent = `Authorization "${chainName}" completed successfully!`;
                     showPluginMessage(`Authorization "${chainName}" completed successfully!`, 'success');
                     // Auto-hide after a delay
                     setTimeout(() => pluginManagerHideAuthProgress(), 4000);
                     // Refresh loaded plugins in case auth enabled something
                     pluginManagerRefreshLoaded();
                 } else {
                     if (progressFill) {
                         progressFill.style.width = '100%';
                         progressFill.style.background = '#dc3545';
                     }
                     if (statusText) statusText.textContent = `Authorization "${chainName}" failed: ${data.failureReason || 'Unknown reason'}`;
                     showPluginMessage(`Authorization "${chainName}" failed: ${data.failureReason || 'Unknown reason'}`, 'error');
                 }
             } else {
                 const pct = data.totalSteps > 0 ? Math.round((data.currentStep / data.totalSteps) * 100) : 0;
                 if (progressFill) {
                     progressFill.style.width = pct + '%';
                     progressFill.style.background = '#007bff';
                 }
                 if (statusText) statusText.textContent = `Authorization "${chainName}" in progress — Step ${data.currentStep} of ${data.totalSteps}`;
                 if (!silent) {
                     showPluginMessage(`Authorization in progress: Step ${data.currentStep} of ${data.totalSteps}`, 'info');
                 }
             }
         })
         .catch(e => {
             if (!silent) {
                 showPluginMessage('Error checking auth status: ' + e.message, 'error');
             }
             // Session may have expired/been cleaned up
             pluginManagerStopAuthPolling();
         });
}

function escapeHtml(str) {
     return String(str)
         .replace(/&/g, '&amp;')
         .replace(/</g, '&lt;')
         .replace(/>/g, '&gt;')
         .replace(/"/g, '&quot;');
}

// ===== Task Selection (Pipeline Wizard) =====
function populateTaskSelection() {
    const taskToggles = document.getElementById('task-toggles');
    if (!taskToggles) return;

    taskToggles.innerHTML = '';

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

            taskToggle.querySelector('.configure-task-btn').addEventListener('click', () => {
                showTaskConfigurationDialog(task.id);
            });
        });

        taskToggles.appendChild(categorySection);
    });
}

function showTaskConfigurationDialog(taskId) {
    const existingConfigs = appState.getTaskConfigs(taskId);

    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.style.display = 'block';
    modal.style.zIndex = '1100';

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
                <span class="close-config-list-modal" style="float:right;cursor:pointer;font-size:28px;">&times;</span>
            </div>
            <div class="modal-body">
                ${configListHtml}
                <button class="button add-new-config-btn">+ Add New Configuration</button>
            </div>
            <div class="modal-footer" style="margin-top:15px;">
                <button class="button secondary close-config-list-btn">Close</button>
            </div>
        </div>
    `;

    document.body.appendChild(modal);

    const closeModal = () => {
        modal.remove();
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
                showTaskConfigurationDialog(taskId);
            })
            .catch(error => {
                if (error.message !== 'Cancelled') {
                    console.error('[showTaskConfigurationDialog] Error:', error);
                }
                showTaskConfigurationDialog(taskId);
            });
    });

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
                        console.error('[showTaskConfigurationDialog] Error:', error);
                    }
                    showTaskConfigurationDialog(taskId);
                });
        });
    });

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

    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });
}

// ===== Session Launch =====
function launchSession() {
    console.log('[launchSession] Launching session...');
    const cognitiveMode = appState.cognitiveMode || 'chat';
    const appPath = '/taskChat';

    const settings = {
        ...appState.taskSettings,
        sessionId: appState.sessionId,
        cognitiveSettings: appState.cognitiveSettings || {type: cognitiveMode}
    };

    httpService.saveSessionSettings(appState.sessionId, settings)
        .then(() => {
            window.location.href = `${appPath}/#${appState.sessionId}`;
        })
        .catch(error => {
            console.error('[launchSession] Error:', error);
            notificationService.showNotification('Error launching session: ' + error.message, 'error');
        });
}

// ===== Cognitive Mode Rendering =====
function renderCognitiveModeSelection() {
    let container = document.getElementById('cognitive-mode-options');
    if (!container) {
        const existing = document.querySelector('input[name="cognitive-mode"]');
        if (existing) {
            container = existing.parentElement;
            container.id = 'cognitive-mode-options';
        }
    }
    if (!container) return;
    container.innerHTML = '';

    cognitiveTypes.forEach((type, index) => {
        const div = document.createElement('div');
        div.className = 'cognitive-mode-option';
        div.style.marginBottom = '10px';

        const input = document.createElement('input');
        input.type = 'radio';
        input.name = 'cognitive-mode';
        input.id = `mode-${type.id}`;
        input.value = type.id;
        if (index === 0) input.checked = true;

        const label = document.createElement('label');
        label.htmlFor = `mode-${type.id}`;
        label.innerHTML = type.description.trim().length === 0
            ? `<strong>${type.name}</strong>`
            : `<strong>${type.name}</strong> - ${type.description}`;
        label.style.marginLeft = '8px';

        div.appendChild(input);
        div.appendChild(label);
        container.appendChild(div);

        input.addEventListener('change', () => updateCognitiveSettingsUI(type));
    });

    if (cognitiveTypes.length > 0) {
        updateCognitiveSettingsUI(cognitiveTypes[0]);
    }
}

function updateCognitiveSettingsUI(type) {
    let container = document.getElementById('cognitive-settings-container');
    if (!container) {
        container = document.getElementById('auto-plan-settings');
        if (container) container.id = 'cognitive-settings-container';
    }
    if (!container) return;

    container.innerHTML = '';
    container.style.display = (type.configFields && type.configFields.length > 0) ? 'block' : 'none';

    if (type.configFields && type.configFields.length > 0) {
        const header = document.createElement('h4');
        header.textContent = `${type.name} Settings`;
        container.appendChild(header);

        type.configFields.forEach(field => {
            const html = taskConfigManager.createFieldHtml(field, {}, 'cognitive-field-');
            const wrapper = document.createElement('div');
            wrapper.innerHTML = html;
            container.appendChild(wrapper);
        });
    }
}

function collectCognitiveSettings(typeId) {
    const type = cognitiveTypes.find(t => t.id === typeId);
    if (!type) return {type: typeId};

    const settings = {type: typeId};
    if (type.configFields) {
        type.configFields.forEach(field => {
            const elementId = `cognitive-field-${field.id}`;
            const element = document.getElementById(elementId);
            if (element) {
                if (field.type === 'checkbox') {
                    settings[field.id] = element.checked;
                } else if (field.type === 'number') {
                    const val = parseFloat(element.value);
                    settings[field.id] = isNaN(val) ? field.default : val;
                } else {
                    settings[field.id] = element.value;
                }
            }
        });
    }
    return settings;
}