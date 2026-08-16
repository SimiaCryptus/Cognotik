/**
 * Model management utilities
 */
import {serverUrl} from './config.js';
/** Local-storage key used to cache preferred model selections. */
const PREFERRED_MODELS_STORAGE_KEY = 'cognotik_preferred_models';
/**
  * Fields on the server-side `UserSettings` record that hold preferred models.
  * Keep in sync with platform-db/.../UserSettingsInterface.kt
  */
export const PREFERRED_MODEL_FIELDS = ['smartModel', 'fastModel'];
/** In-memory cache of the last user-settings payload we fetched. */
let userSettingsCache = null;


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
/* ===================================================================== */
/* Preferred models (persisted in UserSettings.smartModel / .fastModel)   */
/* ===================================================================== */
/**
  * Read the cached preferred models from localStorage.
  * @returns {Object} e.g. `{smartModel: 'GPT4o', fastModel: 'GPT4oMini'}`
  */
function readLocalPreferredModels() {
     try {
         const raw = localStorage.getItem(PREFERRED_MODELS_STORAGE_KEY);
         const parsed = raw ? JSON.parse(raw) : {};
         return (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) ? parsed : {};
     } catch (e) {
         console.warn('Could not read cached preferred models:', e);
         return {};
     }
}
/**
  * Write the preferred models cache to localStorage.
  * @param {Object} models
  */
function writeLocalPreferredModels(models) {
     try {
         const cleaned = {};
         PREFERRED_MODEL_FIELDS.forEach(field => {
             if (models && models[field]) cleaned[field] = models[field];
         });
         localStorage.setItem(PREFERRED_MODELS_STORAGE_KEY, JSON.stringify(cleaned));
     } catch (e) {
         console.warn('Could not cache preferred models:', e);
     }
}
/**
  * Fetch the current user settings from the server.
  * Falls back to the last successful response (or `{}`) on error.
  *
  * @param {Object}  [options]
  * @param {boolean} [options.force=false] Bypass the in-memory cache
  * @returns {Promise<Object>} The user settings object
  */
export async function fetchUserSettings({force = false} = {}) {
     if (!force && userSettingsCache) return userSettingsCache;
     try {
         const response = await fetch(serverUrl('/userSettings/') + '?format=json', {
             headers: {'Accept': 'application/json'}
         });
         if (response.status >= 400) {
             console.warn('Could not load user settings (status ' + response.status + ')');
             return userSettingsCache || {};
         }
         userSettingsCache = (await response.json()) || {};
         return userSettingsCache;
     } catch (e) {
         console.warn('Failed to load user settings:', e);
         return userSettingsCache || {};
     }
}
/**
  * Merge a partial patch into the user settings and persist it to the server.
  * Existing fields (apis, collectSessionData, ...) are preserved.
  *
  * @param {Object} patch Partial settings to merge
  * @returns {Promise<Object>} The merged settings that were sent
  */
export async function saveUserSettings(patch = {}) {
     const current = await fetchUserSettings({force: true});
     const merged = {...(current || {}), ...patch};
     // The server resolves the user itself; don't echo it back.
     delete merged.user;
     const response = await fetch(serverUrl('/userSettings/'), {
         method: 'POST',
         headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
         body: JSON.stringify(merged)
     });
     if (response.status >= 400) {
         throw new Error('Failed to save user settings (status ' + response.status + ')');
     }
     userSettingsCache = merged;
     return merged;
}
/**
  * Get the user's preferred models, preferring the server value and falling
  * back to the localStorage cache (useful before the request resolves or when
  * the user is not authenticated).
  *
  * @param {Object}  [options]
  * @param {boolean} [options.force=false] Bypass the in-memory settings cache
  * @returns {Promise<Object>} `{smartModel, fastModel}`
  */
export async function loadPreferredModels({force = false} = {}) {
     const cached = readLocalPreferredModels();
     const settings = await fetchUserSettings({force});
     const result = {};
     PREFERRED_MODEL_FIELDS.forEach(field => {
         result[field] = (settings && settings[field]) || cached[field] || '';
     });
     writeLocalPreferredModels(result);
     return result;
}
/**
  * Synchronously read the cached preferred models (no network round-trip).
  * @returns {Object} `{smartModel, fastModel}`
  */
export function getCachedPreferredModels() {
     const cached = readLocalPreferredModels();
     const result = {};
     PREFERRED_MODEL_FIELDS.forEach(field => {
         result[field] = cached[field] || '';
     });
     return result;
}
/**
  * Persist the preferred models to user settings (and the local cache).
  * Only recognised fields are sent; an empty value clears the preference.
  *
  * @param {Object} models e.g. `{smartModel: 'GPT4o', fastModel: 'GPT4oMini'}`
  * @returns {Promise<boolean>} true if the server accepted the update
  */
export async function savePreferredModels(models = {}) {
     const patch = {};
     PREFERRED_MODEL_FIELDS.forEach(field => {
         if (Object.prototype.hasOwnProperty.call(models, field)) {
             patch[field] = models[field] || null;
         }
     });
     if (Object.keys(patch).length === 0) return false;
     writeLocalPreferredModels({...readLocalPreferredModels(), ...patch});
     try {
         await saveUserSettings(patch);
         return true;
     } catch (e) {
         console.warn('Failed to persist preferred models to user settings:', e);
         return false;
     }
}
/**
  * Apply preferred models to select elements.
  *
  * @param {Object} selectMap Map of preferred-model field -> HTMLSelectElement
  *                           e.g. `{smartModel: smartSel, fastModel: fastSel}`
  * @param {Object} [models]  Models to apply; loaded from settings when omitted
  * @returns {Promise<Object>} The models that were applied
  */
export async function applyPreferredModels(selectMap, models = null) {
     const preferred = models || await loadPreferredModels();
     Object.entries(selectMap || {}).forEach(([field, sel]) => {
         if (!sel) return;
         const value = preferred[field];
         if (value && Array.from(sel.options).some(opt => opt.value === value)) {
             sel.value = value;
         }
     });
     return preferred;
}
/**
  * Read the current values out of the given select elements.
  *
  * @param {Object} selectMap Map of preferred-model field -> HTMLSelectElement
  * @returns {Object} `{smartModel, fastModel}`
  */
export function collectPreferredModels(selectMap) {
     const result = {};
     Object.entries(selectMap || {}).forEach(([field, sel]) => {
         if (sel) result[field] = sel.value || '';
     });
     return result;
}
/**
  * Wire up select elements so that changing them persists the preference.
  *
  * @param {Object}   selectMap  Map of preferred-model field -> HTMLSelectElement
  * @param {Function} [onSaved]  Optional callback invoked with the saved models
  * @returns {Function} Unbind function that removes the listeners
  */
export function bindPreferredModelSelects(selectMap, onSaved) {
     const listeners = [];
     Object.entries(selectMap || {}).forEach(([field, sel]) => {
         if (!sel) return;
         const handler = async () => {
             const models = {[field]: sel.value || ''};
             const ok = await savePreferredModels(models);
             if (onSaved) onSaved(models, ok);
         };
         sel.addEventListener('change', handler);
         listeners.push(() => sel.removeEventListener('change', handler));
     });
     return () => listeners.forEach(unbind => unbind());
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
     loadModelSelections,
     fetchUserSettings,
     saveUserSettings,
     loadPreferredModels,
     getCachedPreferredModels,
     savePreferredModels,
     applyPreferredModels,
     collectPreferredModels,
     bindPreferredModelSelects,
     PREFERRED_MODEL_FIELDS
};