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
     // Fetch from the actual server endpoint, NOT from localStorage cache.
     // httpService.getUserSettings() reads localStorage which can be stale
     // and missing fields like collectSessionData.
     return fetch('userSettings/?format=json', {
             headers: { 'Accept': 'application/json' }
         })
         .then(response => {
             console.log('[loadUserSettings] Server response status:', response.status);
             if (!response.ok) {
                 throw new Error('Failed to fetch user settings: ' + response.status);
             }
             return response.text();
         })
         .then(settingsText => {
            try {
                 console.log('[loadUserSettings] Raw settingsText type:', typeof settingsText,
                     'length:', settingsText && settingsText.length);
                  console.log('[loadUserSettings] Raw settingsText FULL:',
                      typeof settingsText === 'string' ? settingsText : settingsText);
                  // Check raw text for collectSessionData presence
                  if (typeof settingsText === 'string') {
                      const hasField = settingsText.indexOf('collectSessionData') !== -1;
                      console.log('[loadUserSettings] Raw text contains "collectSessionData" substring:', hasField);
                      if (hasField) {
                          // Extract the value from raw text for diagnosis
                          const m = settingsText.match(/"collectSessionData"\s*:\s*([^,}\s]+)/);
                          console.log('[loadUserSettings] Raw text collectSessionData match:',
                              m ? m[0] : '(no match)', 'captured:', m ? m[1] : '(none)');
                      } else {
                          console.warn('[loadUserSettings] *** SERVER DID NOT RETURN collectSessionData FIELD ***');
                          console.warn('[loadUserSettings] *** This is a SERVER-SIDE bug. The frontend cannot ' +
                              'display a value the server does not send. ***');
                      }
                  }
                const settings = JSON.parse(settingsText);
                 console.log('[loadUserSettings] Parsed settings keys:', Object.keys(settings || {}));
                  console.log('[loadUserSettings] settings.hasOwnProperty("collectSessionData"):',
                      Object.prototype.hasOwnProperty.call(settings || {}, 'collectSessionData'));
                 console.log('[loadUserSettings] settings.collectSessionData =',
                     settings.collectSessionData,
                     '(type:', typeof settings.collectSessionData, ')');
                const isTruthy = (v) => v === true || v === 'true' || v === 1 || v === '1';
                 console.log('[loadUserSettings] isTruthy(settings.collectSessionData) =',
                     isTruthy(settings.collectSessionData));
                if (settings.user) {
                    appState.userInfo = settings.user;
                     console.log('[loadUserSettings] User info loaded:',
                         settings.user.name || settings.user.email || settings.user.id);
                 } else {
                     appState.userInfo = null;
                     console.log('[loadUserSettings] No user info in settings (not logged in)');
                }
                if (settings.apis && Array.isArray(settings.apis)) {
                     console.log('[loadUserSettings] Taking settings.apis branch (array, len=' +
                         settings.apis.length + ')');
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
                        collectSessionData: isTruthy(settings.collectSessionData),
                        localTools: settings.tools || [],
                        configuredApis: settings.apis,
                        user: settings.user || null
                    };
                     console.log('[loadUserSettings] After apis-branch assignment, ' +
                         'appState.apiSettings.collectSessionData =',
                         appState.apiSettings.collectSessionData,
                         '(type:', typeof appState.apiSettings.collectSessionData, ')');
                } else {
                     console.log('[loadUserSettings] Taking else branch (no settings.apis array)');
                    appState.apiSettings = settings;
                    if (appState.apiSettings) {
                        appState.apiSettings.collectSessionData =
                            isTruthy(settings.collectSessionData);
                         // Ensure apiKeys/apiBase exist to avoid downstream null-checks
                         if (!appState.apiSettings.apiKeys) appState.apiSettings.apiKeys = {};
                         if (!appState.apiSettings.apiBase) appState.apiSettings.apiBase = {};
                        // Ensure configuredApis exists (used by later code paths)
                        if (!appState.apiSettings.configuredApis) {
                            appState.apiSettings.configuredApis = [];
                        }
                        // Ensure localTools exists
                        if (!appState.apiSettings.localTools) {
                            appState.apiSettings.localTools = settings.tools || [];
                        }
                         console.log('[loadUserSettings] After else-branch assignment, ' +
                             'appState.apiSettings.collectSessionData =',
                             appState.apiSettings.collectSessionData,
                             '(type:', typeof appState.apiSettings.collectSessionData, ')');
                    }
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
                 console.log('[loadUserSettings] FINAL appState.apiSettings.collectSessionData =',
                     appState.apiSettings && appState.apiSettings.collectSessionData,
                     '(type:', typeof (appState.apiSettings && appState.apiSettings.collectSessionData), ')');
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