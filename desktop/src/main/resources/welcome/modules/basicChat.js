// ===== Basic Chat Modal =====
    function setupBasicChatModal(deps) {
        const { httpService, notificationService, sessionId } = deps;
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

    function populateBasicChatModelSelections(appState, availableModels) {
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