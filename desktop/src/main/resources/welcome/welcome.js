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
        const providers = await httpService.getApiProviders();
        console.log('[loadApiProviders] Received providers:', providers);

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

        // Update the global constants
        if (typeof API_PROVIDERS !== 'undefined') {
            API_PROVIDERS.length = 0;
            API_PROVIDERS.push(...apiProviders);
        }
        if (typeof AVAILABLE_MODELS !== 'undefined') {
            Object.keys(AVAILABLE_MODELS).forEach(key => delete AVAILABLE_MODELS[key]);
            Object.assign(AVAILABLE_MODELS, availableModels);
        }
    } catch (error) {
        console.error('[loadApiProviders] Error loading API providers:', error);
        // Fall back to empty arrays if loading fails
        apiProviders = [];
        availableModels = {};
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
                appState.apiSettings = settings;
                console.log('[loadUserSettings] User settings loaded:', settings);
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

        apiProviders.forEach(provider => {
            const keyGroup = document.createElement('div');
            keyGroup.className = 'form-group';

            const label = document.createElement('label');
            label.textContent = `${provider.name} API Key:`;
            label.setAttribute('for', `api-key-${provider.id}`);

            const input = document.createElement('input');
            input.type = 'password';
            input.id = `api-key-${provider.id}`;
            input.placeholder = `Enter ${provider.name} API key`;
            input.value = (appState.apiSettings.apiKeys && appState.apiSettings.apiKeys[provider.id]) || '';

            keyGroup.appendChild(label);
            keyGroup.appendChild(input);
            apiKeysContainer.appendChild(keyGroup);
        });
    }

    // Populate local tools
    populateLocalTools();
}

function saveUserSettings() {
    console.log('[saveUserSettings] Saving user settings...');

    // Collect API keys
    const apiKeys = {};
    apiProviders.forEach(provider => {
        const input = document.getElementById(`api-key-${provider.id}`);
        if (input && input.value) {
            apiKeys[provider.id] = input.value;
        }
    });

    // Update app state
    appState.apiSettings.apiKeys = apiKeys;

    // Save to server
    httpService.saveUserSettings(appState.apiSettings)
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

    const taskTypes = [
        {id: 'Analysis', name: 'Analysis Task', description: 'Analyze code and provide detailed explanations'},
        {id: 'CommandSession', name: 'Command Session Task', description: 'Execute a series of commands in a session'},
        {id: 'CrawlerAgent', name: 'Web Crawler Task', description: 'Crawl and extract information from websites'},
        {id: 'FileModification', name: 'File Modification Task', description: 'Create or modify files with AI assistance'},
        {id: 'FileSearch', name: 'File Search Task', description: 'Search and analyze files in the project'},
        {id: 'SelfHealing', name: 'Self-Healing Task', description: 'Automatically fix issues in code based on AI suggestions'},
        {id: 'RunShellCommand', name: 'Run Shell Command Task', description: 'Execute shell commands and process the output'},
        {id: 'RunCode', name: 'Run Code Task', description: 'Execute code snippets and return the results'},
        {id: 'SeleniumSession', name: 'Selenium Session Task', description: 'Automate web browser interactions using Selenium'},
        {id: 'SelfHealing', name: 'Self-Healing Task', description: 'Automatically fix build errors based on AI suggestions'},
        {id: 'KnowledgeIndexing', name: 'Knowledge Indexing Task', description: 'Index and search knowledge bases for information retrieval'},
        {id: 'VectorSearch', name: 'Vector Search Task', description: 'Perform vector-based searches for similar items or documents'},
    ];

    taskTypes.forEach(task => {
        const taskToggle = document.createElement('div');
        taskToggle.className = 'task-toggle';

        const taskSettings = appState.taskSettings.taskSettings || {};
        const isEnabled = taskSettings[task.id] || false;
        console.log(`[populateTaskSelection] Task ${task.id} enabled:`, isEnabled);

        taskToggle.innerHTML = `
            <div>
                <input type="checkbox" id="${task.id}" ${isEnabled ? 'checked' : ''}>
                <label for="${task.id}">${task.name}</label>
                <span class="tooltip">?<span class="tooltiptext">${task.description}</span></span>
            </div>
        `;

        taskToggles.appendChild(taskToggle);

        // Add event listener
        const checkbox = taskToggle.querySelector(`#${task.id}`);
        checkbox.addEventListener('change', function () {
            const currentSettings = appState.taskSettings.taskSettings || {};
            if (this.checked) {
                if (!currentSettings[task.id]) {
                    currentSettings[task.id] = {};
                }
                currentSettings[task.id].task_type = task.id;
            } else {
                if (currentSettings[task.id]) {
                    delete currentSettings[task.id];
                }
            }
            console.log(`[populateTaskSelection] Updated task ${task.id} to enabled`);
            appState.saveTaskSettings(currentSettings);
        });
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