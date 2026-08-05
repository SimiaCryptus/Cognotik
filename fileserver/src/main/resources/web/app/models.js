/**
 * Model management utilities
 */
import {serverUrl} from './config.js';

/**
 * Load available API providers and models
 * @returns {Promise<Object>} Available models grouped by provider
 */
export async function loadApiProviders() {
    try {
        const response = await fetch(serverUrl('/apiProviders/') + '?format=json');
        if (response.status >= 400) {
            console.warn('Could not load API providers (status ' + response.status + ')');
            return {};
        }

        const providersResponse = await response.json();
        const providers = providersResponse.configuredProviders || [];
        const availableModels = {};

        providers.forEach(provider => {
            if (provider.models && provider.models.length > 0) {
                availableModels[provider.name] = provider.models.map(model => ({
                    id: model.name,
                    name: model.name,
                }));
            }
        });

        return availableModels;
    } catch (e) {
        console.warn('Failed to load API providers:', e);
        return {};
    }
}

/**
 * Populate model dropdowns with available models
 * @param {Object} availableModels - Models grouped by provider
 * @param {Array<HTMLSelectElement>} selectElements - Select elements to populate
 * @param {Object} savedSelections - Previously saved selections
 */
export function populateModelDropdowns(availableModels, selectElements, savedSelections = {}) {
    // Clear existing options
    selectElements.forEach(sel => {
        sel.innerHTML = '';
    });

    // Check if we have any models
    const hasModels = Object.keys(availableModels).some(provider =>
        availableModels[provider] && availableModels[provider].length > 0
    );

    if (!hasModels) {
        selectElements.forEach(sel => {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No models available — configure API keys first';
            option.disabled = true;
            sel.appendChild(option);
        });
        return;
    }

    // Add default option
    selectElements.forEach(sel => {
        const defaultOpt = document.createElement('option');
        defaultOpt.value = '';
        defaultOpt.textContent = '— Select a model —';
        sel.appendChild(defaultOpt);
    });

    // Add models grouped by provider
    const addedModels = new Set();

    for (const [provider, models] of Object.entries(availableModels)) {
        if (!models || models.length === 0) continue;

        selectElements.forEach(sel => {
            const optgroup = document.createElement('optgroup');
            optgroup.label = provider;

            models.forEach(model => {
                if (!addedModels.has(model.id)) {
                    const option = document.createElement('option');
                    option.value = model.id;
                    option.textContent = model.name;
                    if (model.description) {
                        option.title = model.description;
                    }
                    optgroup.appendChild(option);
                }
            });

            if (optgroup.children.length > 0) {
                sel.appendChild(optgroup);
            }
        });

        models.forEach(model => addedModels.add(model.id));
    }

    // Restore saved selections
    selectElements.forEach((sel, index) => {
        const key = Object.keys(savedSelections)[index];
        if (key && savedSelections[key]) {
            const savedValue = savedSelections[key];
            if (Array.from(sel.options).some(opt => opt.value === savedValue)) {
                sel.value = savedValue;
            }
        }
    });
}

/**
 * Save model selections to localStorage
 * @param {string} prefix - Storage key prefix
 * @param {Object} selections - Model selections to save
 */
export function saveModelSelections(prefix, selections) {
    for (const [key, value] of Object.entries(selections)) {
        if (value) {
            localStorage.setItem(`${prefix}_${key}`, value);
        } else {
            localStorage.removeItem(`${prefix}_${key}`);
        }
    }
}

/**
 * Load model selections from localStorage
 * @param {string} prefix - Storage key prefix
 * @param {Array<string>} keys - Selection keys to load
 * @returns {Object} Loaded selections
 */
export function loadModelSelections(prefix, keys) {
    const selections = {};
    keys.forEach(key => {
        selections[key] = localStorage.getItem(`${prefix}_${key}`) || '';
    });
    return selections;
}

export const ModelUtils = {
    loadApiProviders,
    populateModelDropdowns,
    saveModelSelections,
    loadModelSelections
};