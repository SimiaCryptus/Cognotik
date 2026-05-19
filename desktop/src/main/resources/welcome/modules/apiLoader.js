// ===== API Loading =====
let apiProviders = [];
let availableModels = {};
let cognitiveTypes = [];

async function loadApiProviders() {
    console.log('[loadApiProviders] Loading API providers from server...');
    try {
        const response = await fetch('apiProviders/?format=json');
        const providersResponse = await response.json();
        const providers = providersResponse.configuredProviders || [];
        const availableProvidersList = providersResponse.availableProviders || [];

        apiProviders.length = 0;
        providers.forEach(provider => apiProviders.push({
            id: provider.name,
            name: provider.name,
            baseUrl: provider.baseUrl
        }));

        Object.keys(availableModels).forEach(k => delete availableModels[k]);
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
        apiProviders.length = 0;
        Object.keys(availableModels).forEach(k => delete availableModels[k]);
        window.allAvailableProviders = [];
        throw error;
    }
}

function loadUserSettings(httpService, appState) {
    console.log('[loadUserSettings] Loading user settings...');
    return httpService.getUserSettings()
        .then(settingsText => {
            try {
                const settings = JSON.parse(settingsText);
               if (settings.user) {
                   appState.userInfo = settings.user;
               }
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
                       configuredApis: settings.apis,
                       user: settings.user || null
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
                appState.apiSettings = { apiKeys: {}, apiBase: {}, localTools: [] };
            }
        })
        .catch(error => {
            console.error('[loadUserSettings] Error loading settings:', error);
            appState.apiSettings = { apiKeys: {}, apiBase: {}, localTools: [] };
        });
}

async function loadCognitiveTypes(onLoaded) {
    console.log('[loadCognitiveTypes] Loading cognitive types...');
    try {
        const response = await fetch('/cognitiveConfig/');
        if (response.ok) {
            const types = await response.json();
            cognitiveTypes.length = 0;
            types.forEach(t => cognitiveTypes.push(t));
            if (onLoaded) onLoaded();
        }
    } catch (e) {
        console.error('Error loading cognitive types:', e);
    }
}