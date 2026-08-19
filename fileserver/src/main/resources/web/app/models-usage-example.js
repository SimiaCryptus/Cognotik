import {
    loadApiProviders,
    populateModelDropdowns,
    loadPreferredModels,
    applyPreferredModels,
    bindPreferredModelSelects
} from './models.js';

const smartSel = document.getElementById('smart-model');
const fastSel = document.getElementById('fast-model');
const selectMap = {smartModel: smartSel, fastModel: fastSel};

const [availableModels, preferred] = await Promise.all([
    loadApiProviders(),
    loadPreferredModels()
]);

populateModelDropdowns(availableModels, [smartSel, fastSel], preferred);
await applyPreferredModels(selectMap, preferred);

// Persist to UserSettings.smartModel / UserSettings.fastModel on change
bindPreferredModelSelects(selectMap, (models, ok) =>
    console.log('Saved preferred models', models, 'success:', ok));