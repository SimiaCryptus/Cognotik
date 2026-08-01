// ===== Quick Settings Section =====
    function setupSettingsSection(notificationService) {
        const header = document.getElementById('settings-header-toggle');
        const body = document.getElementById('settings-body');
        const icon = document.getElementById('settings-toggle-icon');

        if (header && body && icon) {
            header.addEventListener('click', function () {
                body.classList.toggle('open');
                icon.classList.toggle('open');
            });
        }

        const tempSlider = document.getElementById('default-temperature');
        const tempValue = document.getElementById('default-temperature-value');
        if (tempSlider && tempValue) {
            tempSlider.addEventListener('input', function () {
                tempValue.textContent = this.value;
            });
            const savedTemp = localStorage.getItem('temperature') || '0.3';
            tempSlider.value = savedTemp;
            tempValue.textContent = savedTemp;
        }

        const budgetInput = document.getElementById('default-budget');
        if (budgetInput) {
            budgetInput.value = localStorage.getItem('budget') || '2.0';
        }

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

    function populateQuickSettingsModels(appState, availableModels) {
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