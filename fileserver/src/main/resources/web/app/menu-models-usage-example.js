import {initMenu} from './menu.js';

// 1) Default: Models panel enabled (smartModel + fastModel)
const menu = initMenu({appName: 'Resume Customizer'});

// 2) Disable the model selection UI entirely
initMenu({appName: 'Docs Viewer', showModels: false});

// 3) Customize fields/labels and react to changes
initMenu({
    appName: 'Planner',
    modelFields: ['smartModel'],
    modelLabels: {smartModel: 'Planning Model'},
    onModelsChanged: (models, savedOk) =>
        console.log('models now', models, 'persisted:', savedOk)
});

// 4) Read/write the current selection from page code
const {smartModel, fastModel} = menu.getSelectedModels();
await menu.setModels({smartModel: 'GPT4o'});