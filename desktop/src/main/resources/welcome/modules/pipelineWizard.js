// ===== Custom Pipeline Modal & Task Selection =====
    function setupCustomPipelineModal(deps) {
        const { appState, modelManager, validationService, uiManager,
                notificationService, httpService, taskConfigManager, getCognitiveTypes } = deps;

        const modal = document.getElementById('custom-pipeline-modal');
        const closeBtn = document.getElementById('close-pipeline-modal');

        closeBtn?.addEventListener('click', () => modal.style.display = 'none');

        window.addEventListener('click', function (event) {
            if (event.target === modal) modal.style.display = 'none';
        });

        const tempSlider = document.getElementById('temperature');
        const tempValue = document.getElementById('temperature-value');
        if (tempSlider && tempValue) {
            tempSlider.addEventListener('input', function () {
                tempValue.textContent = this.value;
            });
        }

        document.getElementById('generate-working-dir')?.addEventListener('click', () => {
            const workingDirInput = document.getElementById('working-dir');
            if (workingDirInput) {
                workingDirInput.value = Utils.generateCognotikWorkingDir();
            }
        });

        document.getElementById('next-to-task-settings')?.addEventListener('click', () => {
            const modeInput = document.querySelector('input[name="cognitive-mode"]:checked');
            if (modeInput) {
                const mode = modeInput.value;
                appState.cognitiveSettings = collectCognitiveSettings(mode, getCognitiveTypes());
                appState.updateCognitiveMode(mode);
                navigatePipelineStep('task-settings');
            }
        });

        document.getElementById('next-to-task-selection')?.addEventListener('click', () => {
            appState.updateTaskSetting('smartModel', document.getElementById('model-selection')?.value);
            appState.updateTaskSetting('fastModel', document.getElementById('parsing-model')?.value);
            appState.updateTaskSetting('imageModel', document.getElementById('image-model')?.value);
            appState.updateTaskSetting('workingDir', document.getElementById('working-dir')?.value);
            appState.updateTaskSetting('temperature', parseFloat(document.getElementById('temperature')?.value));
            appState.updateTaskSetting('autoFix', document.getElementById('auto-fix')?.checked);
            appState.updateTaskSetting('graphFile', document.getElementById('graph-file')?.value);
            populateTaskSelection(appState, taskConfigManager, notificationService);
            navigatePipelineStep('task-selection');
        });

        document.getElementById('next-to-launch')?.addEventListener('click', () => {
            navigatePipelineStep('launch');
            uiManager.updateLaunchSummaries();
        });

        document.getElementById('back-to-cognitive-mode')?.addEventListener('click', () => navigatePipelineStep('cognitive-mode'));
        document.getElementById('back-to-task-settings')?.addEventListener('click', () => navigatePipelineStep('task-settings'));
        document.getElementById('back-to-task-selection')?.addEventListener('click', () => navigatePipelineStep('task-selection'));

        document.getElementById('launch-session')?.addEventListener('click', () => {
            if (validationService.validateConfiguration()) {
                launchSession(appState, httpService, notificationService);
            }
        });
    }

    function navigatePipelineStep(stepId) {
        const modal = document.getElementById('custom-pipeline-modal');
        if (!modal) return;

        modal.querySelectorAll('.wizard-step').forEach(step => {
            step.classList.remove('active');
            if (step.getAttribute('data-step') === stepId) {
                step.classList.add('active');
            }
        });

        modal.querySelectorAll('.wizard-content').forEach(content => {
            content.classList.remove('active');
        });

        const targetContent = document.getElementById(stepId);
        if (targetContent) {
            targetContent.classList.add('active');
        }
    }

    // ===== Task Selection (Pipeline Wizard) =====
    function populateTaskSelection(appState, taskConfigManager, notificationService) {
        const taskToggles = document.getElementById('task-toggles');
        if (!taskToggles) return;

        taskToggles.innerHTML = '';

        const categories = taskConfigManager.getTaskCategories();

        categories.forEach(category => {
            const categorySection = document.createElement('div');
            categorySection.className = 'task-category-section';

            const categoryHeader = document.createElement('h4');
            categoryHeader.textContent = category;
            categoryHeader.style.marginTop = '20px';
            categoryHeader.style.marginBottom = '10px';
            categoryHeader.style.color = '#2c3e50';
            categorySection.appendChild(categoryHeader);

            const tasksInCategory = taskConfigManager.getTasksByCategory(category);

            tasksInCategory.forEach(task => {
                const taskToggle = document.createElement('div');
                taskToggle.className = 'task-toggle';
                taskToggle.style.display = 'flex';
                taskToggle.style.justifyContent = 'space-between';
                taskToggle.style.alignItems = 'center';
                taskToggle.style.padding = '10px';
                taskToggle.style.marginBottom = '5px';
                taskToggle.style.backgroundColor = '#f8f9fa';
                taskToggle.style.borderRadius = '4px';

                const hasConfigs = appState.hasTaskConfigs(task.id);
                const configCount = Object.keys(appState.getTaskConfigs(task.id)).length;

                taskToggle.innerHTML = `
                    <div style="flex: 1;">
                        <label style="font-weight: 500;">${task.name}</label>
                        <span class="tooltip">?<span class="tooltiptext">${task.description}</span></span>
                        ${hasConfigs ? `<span style="margin-left: 10px; color: #28a745; font-size: 0.9em;">✓ ${configCount} config${configCount > 1 ? 's' : ''}</span>` : '<span style="margin-left: 10px; color: #999; font-size: 0.9em;">Not configured</span>'}
                    </div>
                    <div>
                        <button class="button secondary small configure-task-btn" data-task-id="${task.id}"
                                style="margin-left: 10px; padding: 5px 10px; font-size: 0.9em;">
                            Configure
                        </button>
                    </div>
                `;

                categorySection.appendChild(taskToggle);

                taskToggle.querySelector('.configure-task-btn').addEventListener('click', () => {
                    showTaskConfigurationDialog(task.id, appState, taskConfigManager, notificationService);
                });
            });

            taskToggles.appendChild(categorySection);
        });
    }

    function showTaskConfigurationDialog(taskId, appState, taskConfigManager, notificationService) {
        const existingConfigs = appState.getTaskConfigs(taskId);

        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.style.display = 'block';
        modal.style.zIndex = '1100';

        let configListHtml = '';
        const configEntries = Object.entries(existingConfigs);
        if (configEntries.length > 0) {
            configListHtml = '<div class="config-list" style="margin-bottom: 20px;">';
            configListHtml += '<h4>Existing Configurations:</h4>';
            configEntries.forEach(([configName, config]) => {
                const modelInfo = config.model ? ` (${config.model})` : '';
                configListHtml += `
                    <div class="config-item" style="display: flex; justify-content: space-between; align-items: center;
                         padding: 10px; margin-bottom: 5px; background: #f8f9fa; border-radius: 4px;">
                        <span><strong>${configName}</strong>${modelInfo}</span>
                        <div>
                            <button class="button secondary small edit-config-btn" data-config-name="${configName}">Edit</button>
                            <button class="button secondary small delete-config-btn" data-config-name="${configName}"
                                    style="margin-left: 5px;">Delete</button>
                        </div>
                    </div>
                `;
            });
            configListHtml += '</div>';
        }

        modal.innerHTML = `
            <div class="modal-content" style="max-width: 600px;">
                <div class="modal-header">
                    <h3>Configure ${taskConfigManager.getTaskType(taskId).name}</h3>
                    <span class="close-config-list-modal" style="float:right;cursor:pointer;font-size:28px;">&times;</span>
                </div>
                <div class="modal-body">
                    ${configListHtml}
                    <button class="button add-new-config-btn">+ Add New Configuration</button>
                </div>
                <div class="modal-footer" style="margin-top:15px;">
                    <button class="button secondary close-config-list-btn">Close</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        const closeModal = () => {
            modal.remove();
            populateTaskSelection(appState, taskConfigManager, notificationService);
        };

        modal.querySelector('.close-config-list-modal').addEventListener('click', closeModal);
        modal.querySelector('.close-config-list-btn').addEventListener('click', closeModal);

        modal.querySelector('.add-new-config-btn').addEventListener('click', () => {
            modal.remove();
            taskConfigManager.showTaskConfigDialog(taskId)
                .then(config => {
                    appState.addTaskConfig(taskId, config);
                    notificationService.showNotification('Configuration saved successfully', 'success');
                    showTaskConfigurationDialog(taskId, appState, taskConfigManager, notificationService);
                })
                .catch(error => {
                    if (error.message !== 'Cancelled') {
                        console.error('[showTaskConfigurationDialog] Error:', error);
                    }
                    showTaskConfigurationDialog(taskId, appState, taskConfigManager, notificationService);
                });
        });

        modal.querySelectorAll('.edit-config-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const configName = btn.getAttribute('data-config-name');
                const existingConfig = appState.getTaskConfig(taskId, configName);
                modal.remove();
                taskConfigManager.showTaskConfigDialog(taskId, existingConfig)
                    .then(config => {
                        appState.addTaskConfig(taskId, config);
                        notificationService.showNotification('Configuration updated successfully', 'success');
                        showTaskConfigurationDialog(taskId, appState, taskConfigManager, notificationService);
                    })
                    .catch(error => {
                        if (error.message !== 'Cancelled') {
                            console.error('[showTaskConfigurationDialog] Error:', error);
                        }
                        showTaskConfigurationDialog(taskId, appState, taskConfigManager, notificationService);
                    });
            });
        });

        modal.querySelectorAll('.delete-config-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const configName = btn.getAttribute('data-config-name');
                if (confirm(`Delete configuration "${configName}"?`)) {
                    appState.removeTaskConfig(taskId, configName);
                    notificationService.showNotification('Configuration deleted', 'success');
                    showTaskConfigurationDialog(taskId, appState, taskConfigManager, notificationService);
                }
            });
        });

        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }

    // ===== Session Launch =====
    function launchSession(appState, httpService, notificationService) {
        console.log('[launchSession] Launching session...');
        const cognitiveMode = appState.cognitiveMode || 'chat';
        const appPath = '/taskChat';

        const settings = {
            ...appState.taskSettings,
            sessionId: appState.sessionId,
            cognitiveSettings: appState.cognitiveSettings || {type: cognitiveMode}
        };

        httpService.saveSessionSettings(appState.sessionId, settings)
            .then(() => {
                window.location.href = `${appPath}/#${appState.sessionId}`;
            })
            .catch(error => {
                console.error('[launchSession] Error:', error);
                notificationService.showNotification('Error launching session: ' + error.message, 'error');
            });
    }