// ===== Cognitive Mode Rendering =====
    function renderCognitiveModeSelection(cognitiveTypes, taskConfigManager) {
        let container = document.getElementById('cognitive-mode-options');
        if (!container) {
            const existing = document.querySelector('input[name="cognitive-mode"]');
            if (existing) {
                container = existing.parentElement;
                container.id = 'cognitive-mode-options';
            }
        }
        if (!container) return;
        container.innerHTML = '';

        cognitiveTypes.forEach((type, index) => {
            const div = document.createElement('div');
            div.className = 'cognitive-mode-option';
            div.style.marginBottom = '10px';

            const input = document.createElement('input');
            input.type = 'radio';
            input.name = 'cognitive-mode';
            input.id = `mode-${type.id}`;
            input.value = type.id;
            if (index === 0) input.checked = true;

            const label = document.createElement('label');
            label.htmlFor = `mode-${type.id}`;
            label.innerHTML = type.description.trim().length === 0
                ? `<strong>${type.name}</strong>`
                : `<strong>${type.name}</strong> - ${type.description}`;
            label.style.marginLeft = '8px';

            div.appendChild(input);
            div.appendChild(label);
            container.appendChild(div);

            input.addEventListener('change', () => updateCognitiveSettingsUI(type, taskConfigManager));
        });

        if (cognitiveTypes.length > 0) {
            updateCognitiveSettingsUI(cognitiveTypes[0], taskConfigManager);
        }
    }

    function updateCognitiveSettingsUI(type, taskConfigManager) {
        let container = document.getElementById('cognitive-settings-container');
        if (!container) {
            container = document.getElementById('auto-plan-settings');
            if (container) container.id = 'cognitive-settings-container';
        }
        if (!container) return;

        container.innerHTML = '';
        container.style.display = (type.configFields && type.configFields.length > 0) ? 'block' : 'none';

        if (type.configFields && type.configFields.length > 0) {
            const header = document.createElement('h4');
            header.textContent = `${type.name} Settings`;
            container.appendChild(header);

            type.configFields.forEach(field => {
                const html = taskConfigManager.createFieldHtml(field, {}, 'cognitive-field-');
                const wrapper = document.createElement('div');
                wrapper.innerHTML = html;
                container.appendChild(wrapper);
            });
        }
    }

    function collectCognitiveSettings(typeId, cognitiveTypes) {
        const type = cognitiveTypes.find(t => t.id === typeId);
        if (!type) return {type: typeId};

        const settings = {type: typeId};
        if (type.configFields) {
            type.configFields.forEach(field => {
                const elementId = `cognitive-field-${field.id}`;
                const element = document.getElementById(elementId);
                if (element) {
                    if (field.type === 'checkbox') {
                        settings[field.id] = element.checked;
                    } else if (field.type === 'number') {
                        const val = parseFloat(element.value);
                        settings[field.id] = isNaN(val) ? field.default : val;
                    } else {
                        settings[field.id] = element.value;
                    }
                }
            });
        }
        return settings;
    }