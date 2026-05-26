// ===== User Settings Modal =====
function setupUserSettingsModal(deps) {
    const { appState, httpService, notificationService, modelManager,
        onSettingsSaved } = deps;

    const userSettingsBtn = document.getElementById('user-settings-btn');
    const userSettingsModal = document.getElementById('user-settings-modal');
    const closeUserSettingsModal = document.getElementById('close-user-settings-modal');

    userSettingsBtn?.addEventListener('click', () => {
         console.log('[userSettingsBtn:click] Opening user settings modal. ' +
             'Current appState.apiSettings.collectSessionData =',
             appState.apiSettings && appState.apiSettings.collectSessionData,
             '(type:', typeof (appState.apiSettings && appState.apiSettings.collectSessionData), ')');
        userSettingsModal.style.display = 'block';
        populateUserSettings(appState);
    });

    closeUserSettingsModal?.addEventListener('click', () => {
        userSettingsModal.style.display = 'none';
    });

    window.addEventListener('click', function(event) {
        if (event.target === userSettingsModal) {
            userSettingsModal.style.display = 'none';
        }
    });

    applyHostedRestrictions();

    document.querySelectorAll('#user-settings-modal .tab-button').forEach(button => {
        button.addEventListener('click', () => {
            const tabId = button.getAttribute('data-tab');
            switchTab(tabId);
        });
    });

    document.getElementById('reset-user-settings')?.addEventListener('click', () => {
        if (confirm('Are you sure you want to reset all settings?')) {
            resetUserSettings(appState);
        }
    });

    document.getElementById('save-user-settings')?.addEventListener('click', () => {
        saveUserSettings({ appState, httpService, notificationService, modelManager, onSettingsSaved });
    });
}

function isLocalhostHost() {
    const host = window.location.hostname;
    return host === 'localhost'
        || host === '127.0.0.1'
        || host === '::1'
        || host === '[::1]';
}

function applyHostedRestrictions() {
    // On hosted (non-localhost) versions, hide API Keys tab since those are
    // not relevant to the hosted version.
    if (!isLocalhostHost()) {
        const apiKeysTabBtn = document.querySelector('#user-settings-modal .tab-button[data-tab="api-keys"]');
        const apiKeysTab = document.getElementById('api-keys-tab');
        if (apiKeysTabBtn) apiKeysTabBtn.style.display = 'none';
        if (apiKeysTab) {
            apiKeysTab.classList.remove('active');
        }
        // Activate the General tab by default
        const generalTabBtn = document.querySelector('#user-settings-modal .tab-button[data-tab="general"]');
        const generalTab = document.getElementById('general-tab');
        if (generalTabBtn) generalTabBtn.classList.add('active');
        if (generalTab) generalTab.classList.add('active');
    }
}

function populateUserSettings(appState) {
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
                apiProvidersList.appendChild(createProviderInput(appState, providerId, apiKey || '', baseUrl || ''));
            });
        }

        if (appState.apiSettings.apiBase) {
            Object.keys(appState.apiSettings.apiBase).forEach(providerId => {
                if (!appState.apiSettings.apiKeys || !appState.apiSettings.apiKeys[providerId]) {
                    const baseUrl = appState.apiSettings.apiBase[providerId];
                    apiProvidersList.appendChild(createProviderInput(appState, providerId, '', baseUrl || ''));
                }
            });
        }

        apiKeysContainer.appendChild(apiProvidersList);

        const addProviderBtn = document.createElement('button');
        addProviderBtn.className = 'button secondary';
        addProviderBtn.textContent = '+ Add API Provider';
        addProviderBtn.style.marginTop = '10px';
        addProviderBtn.addEventListener('click', () => {
            apiProvidersList.appendChild(createProviderInput(appState, '', '', ''));
        });
        apiKeysContainer.appendChild(addProviderBtn);
    }

    // Populate general settings (collectSessionData checkbox)
    const collectSessionDataCheckbox = document.getElementById('collect-session-data');
     console.log('[populateUserSettings] collectSessionDataCheckbox found:',
         !!collectSessionDataCheckbox);
    if (collectSessionDataCheckbox) {
        const val = appState.apiSettings ? appState.apiSettings.collectSessionData : false;
         const checked = (val === true || val === 'true' || val === 1 || val === '1');
         console.log('[populateUserSettings] collectSessionData value:', val,
             '(type:', typeof val, ') => checked:', checked);
         console.log('[populateUserSettings] appState.apiSettings keys:',
             appState.apiSettings ? Object.keys(appState.apiSettings) : '(null)');
         collectSessionDataCheckbox.checked = checked;
         console.log('[populateUserSettings] Checkbox.checked after assignment:',
             collectSessionDataCheckbox.checked);
         // Diagnostic: verify the DOM element still reports the value
         setTimeout(() => {
             const recheck = document.getElementById('collect-session-data');
             if (recheck) {
                 console.log('[populateUserSettings] (deferred) checkbox.checked =',
                     recheck.checked,
                     'same element?', recheck === collectSessionDataCheckbox);
             } else {
                 console.warn('[populateUserSettings] (deferred) checkbox no longer in DOM!');
             }
         }, 0);
    }
}

function createProviderInput(appState, providerId, apiKey, baseUrl) {
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

    const awsGroup = document.createElement('div');
    awsGroup.className = 'provider-aws-group';
    awsGroup.style.flex = '1 1 100%';
    awsGroup.style.display = 'none';
    awsGroup.style.padding = '10px';
    awsGroup.style.marginTop = '8px';
    awsGroup.style.border = '1px solid #ddd';
    awsGroup.style.borderRadius = '4px';
    awsGroup.style.background = '#f8f9fa';
    const awsHeader = document.createElement('div');
    awsHeader.style.fontWeight = '600';
    awsHeader.style.marginBottom = '8px';
    awsHeader.textContent = 'AWS Configuration';
    awsGroup.appendChild(awsHeader);

    const awsProfileLabel = document.createElement('label');
    awsProfileLabel.textContent = 'AWS Profile:';
    awsProfileLabel.style.display = 'block';
    awsProfileLabel.style.marginTop = '6px';
    const awsProfileInput = document.createElement('input');
    awsProfileInput.type = 'text';
    awsProfileInput.className = 'provider-aws-profile';
    awsProfileInput.placeholder = 'default';
    awsProfileInput.style.width = '100%';
    awsGroup.appendChild(awsProfileLabel);
    awsGroup.appendChild(awsProfileInput);

    const awsRegionLabel = document.createElement('label');
    awsRegionLabel.textContent = 'AWS Region:';
    awsRegionLabel.style.display = 'block';
    awsRegionLabel.style.marginTop = '6px';
    const awsRegionInput = document.createElement('input');
    awsRegionInput.type = 'text';
    awsRegionInput.className = 'provider-aws-region';
    awsRegionInput.placeholder = 'us-west-2';
    awsRegionInput.style.width = '100%';
    awsGroup.appendChild(awsRegionLabel);
    awsGroup.appendChild(awsRegionInput);

    const awsModelsLabel = document.createElement('label');
    awsModelsLabel.textContent = 'Custom Model IDs (JSON object, optional):';
    awsModelsLabel.style.display = 'block';
    awsModelsLabel.style.marginTop = '6px';
    const awsModelsInput = document.createElement('textarea');
    awsModelsInput.className = 'provider-aws-models';
    awsModelsInput.placeholder = '{"modelName": "custom-model-id"}';
    awsModelsInput.style.width = '100%';
    awsModelsInput.style.minHeight = '60px';
    awsModelsInput.style.fontFamily = 'monospace';
    awsModelsInput.style.fontSize = '0.9em';
    awsGroup.appendChild(awsModelsLabel);
    awsGroup.appendChild(awsModelsInput);

    const awsFlattenLabel = document.createElement('label');
    awsFlattenLabel.style.display = 'block';
    awsFlattenLabel.style.marginTop = '6px';
    awsFlattenLabel.style.fontWeight = 'normal';
    const awsFlattenSelect = document.createElement('select');
    awsFlattenSelect.className = 'provider-aws-flatten';
    awsFlattenSelect.style.marginLeft = '6px';
    ['default', 'true', 'false'].forEach(v => {
        const opt = document.createElement('option');
        opt.value = v === 'default' ? '' : v;
        opt.textContent = v === 'default' ? 'Default (unset)' : v;
        awsFlattenSelect.appendChild(opt);
    });
    awsFlattenLabel.textContent = 'Flatten Chat:';
    awsFlattenLabel.appendChild(awsFlattenSelect);
    awsGroup.appendChild(awsFlattenLabel);

    const removeBtn = document.createElement('button');
    removeBtn.className = 'button secondary';
    removeBtn.textContent = 'Remove';
    removeBtn.style.marginBottom = '0';
    removeBtn.style.flex = '0 0 auto';
    removeBtn.addEventListener('click', () => keyGroup.remove());

    keyGroup.appendChild(providerSelectGroup);
    keyGroup.appendChild(apiKeyGroup);
    keyGroup.appendChild(baseUrlGroup);
    keyGroup.appendChild(awsGroup);
    keyGroup.appendChild(removeBtn);

    const isOllama = (id) => id && id.toLowerCase() === 'ollama';
    const isAws = (id) => id && (id.toLowerCase() === 'aws' || id.toLowerCase().includes('bedrock'));

    const updateProviderUI = () => {
        const selectedId = providerSelect.value;
        if (isOllama(selectedId)) {
            apiKeyInput.value = '-';
            apiKeyInput.disabled = true;
            apiKeyInput.placeholder = 'Not required (auto-filled with "-")';
            apiKeyGroup.style.display = 'none';
            awsGroup.style.display = 'none';
        } else if (isAws(selectedId)) {
            apiKeyGroup.style.display = 'none';
            awsGroup.style.display = 'block';
            apiKeyInput.disabled = false;
            if (apiKeyInput.value && apiKeyInput.value.trim().startsWith('{')) {
                try {
                    const parsed = JSON.parse(apiKeyInput.value);
                    awsProfileInput.value = parsed.profile || '';
                    awsRegionInput.value = parsed.region || '';
                    awsModelsInput.value = parsed.models ? JSON.stringify(parsed.models, null, 2) : '';
                    awsFlattenSelect.value = (parsed.flattenChat === true) ? 'true' :
                        (parsed.flattenChat === false) ? 'false' : '';
                } catch (e) {
                    console.warn('[createProviderInput] Could not parse AWS auth JSON:', e);
                }
            }
        } else {
            apiKeyGroup.style.display = '';
            awsGroup.style.display = 'none';
            apiKeyInput.disabled = false;
            apiKeyInput.placeholder = 'Enter API key';
            if (apiKeyInput.value === '-') apiKeyInput.value = '';
        }
    };
    providerSelect.addEventListener('change', updateProviderUI);
    updateProviderUI();

    return keyGroup;
}

function saveUserSettings(deps) {
      const { appState, httpService, notificationService, modelManager, onSettingsSaved } = deps;
      const apiKeys = {};
      const apiBase = {};

    document.querySelectorAll('.provider-group').forEach(group => {
        const selectInput = group.querySelector('.provider-select');
        const keyInput = group.querySelector('.provider-key');
        const baseUrlInput = group.querySelector('.provider-base-url');
        if (!selectInput || !selectInput.value) return;

        const providerId = selectInput.value;
        const providerLower = providerId.toLowerCase();
        const isOllama = providerLower === 'ollama';
        const isAws = providerLower === 'aws' || providerLower.includes('bedrock');

        let keyValue;
        if (isOllama) {
            keyValue = '-';
        } else if (isAws) {
            const profileInput = group.querySelector('.provider-aws-profile');
            const regionInput = group.querySelector('.provider-aws-region');
            const modelsInput = group.querySelector('.provider-aws-models');
            const flattenSelect = group.querySelector('.provider-aws-flatten');

            const awsAuth = {
                profile: (profileInput && profileInput.value.trim()) || 'default',
                region: (regionInput && regionInput.value.trim()) || 'us-west-2',
                models: {}
            };

            if (modelsInput && modelsInput.value.trim()) {
                try {
                    const parsed = JSON.parse(modelsInput.value);
                    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
                        awsAuth.models = parsed;
                    } else {
                        notificationService.showNotification(
                            `AWS Custom Models must be a JSON object for ${providerId}`, 'error');
                        return;
                    }
                } catch (e) {
                    notificationService.showNotification(
                        `Invalid JSON in AWS Custom Models for ${providerId}: ${e.message}`, 'error');
                    return;
                }
            }

            if (flattenSelect && flattenSelect.value !== '') {
                awsAuth.flattenChat = flattenSelect.value === 'true';
            }

            keyValue = JSON.stringify(awsAuth);
        } else {
            if (!keyInput) return;
            keyValue = keyInput.value;
            if (!keyValue) return;
        }

        apiKeys[providerId] = keyValue;
        if (baseUrlInput && baseUrlInput.value) {
            apiBase[providerId] = baseUrlInput.value;
        }
    });

    appState.apiSettings.apiKeys = apiKeys;
    appState.apiSettings.apiBase = apiBase;

    // Collect general settings
    const collectSessionDataCheckbox = document.getElementById('collect-session-data');
    const collectSessionData = collectSessionDataCheckbox ? !!collectSessionDataCheckbox.checked : false;
     console.log('[saveUserSettings] collectSessionDataCheckbox found:', !!collectSessionDataCheckbox,
         'checked:', collectSessionDataCheckbox ? collectSessionDataCheckbox.checked : '(no checkbox)',
         '=> will save collectSessionData:', collectSessionData);
    appState.apiSettings.collectSessionData = collectSessionData;

    const apisArray = Object.keys(apiKeys).map(provider => ({
        provider: provider,
        key: apiKeys[provider],
        baseUrl: apiBase[provider] || ''
    }));

    const settingsToSave = {
        apis: apisArray,
        collectSessionData: collectSessionData,
        etc: {}
    };
     console.log('[saveUserSettings] Sending settings to server:',
         JSON.stringify(settingsToSave));

    httpService.saveUserSettings(settingsToSave)
        .then(() => {
             console.log('[saveUserSettings] Server accepted save. Verifying by re-reading...');
             // Verify the server actually persisted the value by re-fetching
             // from the server endpoint (NOT localStorage cache).
             return fetch('userSettings/?format=json', {
                     headers: { 'Accept': 'application/json' }
                 })
                 .then(r => r.text())
                 .then(verifyText => {
                 console.log('[saveUserSettings] VERIFY - server returned after save:',
                     typeof verifyText === 'string' ? verifyText : verifyText);
                 if (typeof verifyText === 'string') {
                     const has = verifyText.indexOf('collectSessionData') !== -1;
                     console.log('[saveUserSettings] VERIFY - response contains collectSessionData:', has);
                     if (!has) {
                         console.error('[saveUserSettings] *** SERVER BUG: We sent collectSessionData=' +
                             collectSessionData + ' but server did NOT persist/return it ***');
                     }
                 }
             }).catch(e => console.warn('[saveUserSettings] Verify fetch failed:', e));
         })
         .then(() => {
            notificationService.showNotification('Settings saved successfully', 'success');
            document.getElementById('user-settings-modal').style.display = 'none';
            modelManager.populateModelSelections();
            if (onSettingsSaved) onSettingsSaved();
        })
        .catch(error => {
            console.error('[saveUserSettings] Error:', error);
            notificationService.showNotification('Error saving settings: ' + error.message, 'error');
        });
}

function switchTab(tabId) {
    // Only switch tabs within the user settings modal
    const modal = document.getElementById('user-settings-modal');
    if (!modal) return;
    modal.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    modal.querySelectorAll('.tab-button').forEach(b => b.classList.remove('active'));

    const selectedContent = document.getElementById(`${tabId}-tab`);
    if (selectedContent) selectedContent.classList.add('active');

    const selectedButton = modal.querySelector(`[data-tab="${tabId}"]`);
    if (selectedButton) selectedButton.classList.add('active');
}

function resetUserSettings(appState) {
    appState.apiSettings = { apiKeys: {}, apiBase: {}, collectSessionData: false };
    populateUserSettings(appState);
}