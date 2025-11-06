// Model management module
class ModelManager {
    constructor(dependencies = {}) {
        this.appState = dependencies.appState;
        this.document = dependencies.document || document;
        // Reference the global availableModels object
        this.getAvailableModels = dependencies.getAvailableModels || (() => availableModels);
    }


    clearModelSelections(modelSelect, parsingModelSelect, imageModelSelect) {
        modelSelect.innerHTML = '';
        parsingModelSelect.innerHTML = '';
        imageModelSelect.innerHTML = '';
    }

    addAvailableModels(modelSelect, parsingModelSelect, imageModelSelect) {
        const addedModels = new Set();
        const currentModels = this.getAvailableModels();

        if (this.appState.apiSettings && this.appState.apiSettings.apiKeys) {
            for (const [provider, key] of Object.entries(this.appState.apiSettings.apiKeys)) {
                console.log(`[addAvailableModels] Checking provider: ${provider}, has key: ${!!key}, has models: ${!!currentModels[provider]}`);
                if (key && currentModels[provider]) {
                    currentModels[provider].forEach(model => {
                        if (!addedModels.has(model.id)) {
                            this.addModelOption(modelSelect, parsingModelSelect, imageModelSelect, model, provider);
                            addedModels.add(model.id);
                        }
                    });
                }
            }
        }

        if (modelSelect.options.length === 0) {
            this.addDefaultOptions(modelSelect, parsingModelSelect, imageModelSelect);
        }
    }

    populateModelSelections() {
        console.log('[populateModelSelections] Called');

        const modelSelect = this.document.getElementById('model-selection');
        const parsingModelSelect = this.document.getElementById('parsing-model');
        const imageModelSelect = this.document.getElementById('image-model');

        if (!modelSelect || !parsingModelSelect) {
            console.warn('[populateModelSelections] Model select elements not found.');
            return;
        }
        // Ensure we have appState and availableModels
        const currentModels = this.getAvailableModels();
        if (!this.appState || !currentModels) {
            console.warn('[populateModelSelections] Missing required dependencies.');
            return;
        }


        const savedDefaultModel = this.appState.taskSettings.defaultModel;
        const savedParsingModel = this.appState.taskSettings.parsingModel;
        const savedImageModel = this.appState.taskSettings.imageModel;

        this.clearModelSelections(modelSelect, parsingModelSelect, imageModelSelect);
        this.addAvailableModels(modelSelect, parsingModelSelect, imageModelSelect);

        this.setSelectedModels(modelSelect, parsingModelSelect, savedDefaultModel, imageModelSelect, savedParsingModel, savedImageModel);
    }

    addModelOption(modelSelect, parsingModelSelect, imageModelSelect, model, provider) {
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

        const imageOption = document.createElement('option');
        imageOption.value = model.id;
        imageOption.textContent = `${model.name} (${provider})`;
        imageOption.title = model.description;
        imageModelSelect.appendChild(imageOption);
    }

    addDefaultOptions(modelSelect, parsingModelSelect, imageModelSelect) {
        const defaultOption = document.createElement('option');
        defaultOption.value = 'GPT4o';
        defaultOption.textContent = 'GPT-4o (OpenAI) - Configure API key';
        modelSelect.appendChild(defaultOption);

        const defaultParsingOption = document.createElement('option');
        defaultParsingOption.value = 'GPT4oMini';
        defaultParsingOption.textContent = 'GPT-4o Mini (OpenAI) - Configure API key';
        parsingModelSelect.appendChild(defaultParsingOption);

        const defaultImageOption = document.createElement('option');
        defaultImageOption.value = 'DALL-E-3';
        defaultImageOption.textContent = 'DALL-E 3 (OpenAI) - Configure API key';
        imageModelSelect.appendChild(defaultImageOption);
    }

    setSelectedModels(modelSelect, parsingModelSelect, savedDefaultModel, imageModelSelect, savedParsingModel, savedImageModel) {
        if (savedDefaultModel && Array.from(modelSelect.options).some(opt => opt.value === savedDefaultModel)) {
            modelSelect.value = savedDefaultModel;
        } else if (modelSelect.options.length > 0) {
            modelSelect.selectedIndex = 0;
            this.appState.updateTaskSetting('defaultModel', modelSelect.value);
        }

        if (savedParsingModel && Array.from(parsingModelSelect.options).some(opt => opt.value === savedParsingModel)) {
            parsingModelSelect.value = savedParsingModel;
        } else if (parsingModelSelect.options.length > 0) {
            parsingModelSelect.selectedIndex = 0;
            this.appState.updateTaskSetting('parsingModel', parsingModelSelect.value);
        }

        if (savedImageModel && Array.from(imageModelSelect.options).some(opt => opt.value === savedImageModel)) {
            imageModelSelect.value = savedImageModel;
        } else if (imageModelSelect.options.length > 0) {
            imageModelSelect.selectedIndex = 0;
            this.appState.updateTaskSetting('parsingModel', imageModelSelect.value);
        }

    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {ModelManager};
}