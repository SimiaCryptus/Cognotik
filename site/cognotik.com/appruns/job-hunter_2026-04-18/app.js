(function() {
    'use strict';

    // Global state
    const { basePath, sessionId, appId } = SessionUtils.parseSessionUrl();
    let availableModels = {};
    let statusPoller = null;
    let pipelineRunning = false;
    let pipelineAborted = false;
    const logger = UIUtils.createBatchLogger('batch-log');
    const linkManager = SessionLinkUtils.createSessionLinkManager(SessionUtils.getProxyUrl);

    // Initialize application
    async function init() {
        try {
            // Load available models
            availableModels = await ModelUtils.loadApiProviders();

            // Populate model dropdowns
            const modelSelects = [
                document.getElementById('smart-model'),
                document.getElementById('fast-model'),
                document.getElementById('image-model')
            ];

            const savedModels = ModelUtils.loadModelSelections('job-hunter',
                ['smartModel', 'fastModel', 'imageModel']);

            ModelUtils.populateModelDropdowns(availableModels, modelSelects, savedModels);

            // Load existing files
            await loadExistingFiles();

            // Check for existing pipeline state
            await checkPipelineState();
            // Check for existing job matches to enable copy step
            await checkJobMatchesExist();


            // Start status monitoring
            statusPoller = DocOpsUtils.createStatusPoller(basePath, handleStatusUpdate);
            statusPoller.start();

            // Setup event listeners
            setupEventListeners();

            UIUtils.showToast('Application loaded successfully', 'success');

        } catch (e) {
            console.error('Initialization error:', e);
            UIUtils.showToast('Failed to initialize application', 'error');
        }
    }

    // Load existing files
    async function loadExistingFiles() {
        // Load resume
        const resume = await FileIOUtils.readFile(basePath, 'resume.json');
        if (resume) {
            document.getElementById('resume-editor').value = resume;
        }

        // Load requirements
        const requirements = await FileIOUtils.readFile(basePath, 'requirements.md');
        if (requirements) {
            document.getElementById('requirements-editor').value = requirements;
        }

        // Load companies to avoid
        const companies = await FileIOUtils.readFile(basePath, 'companies_to_avoid.txt');
        if (companies) {
            document.getElementById('companies-editor').value = companies;
        }

        // Load job matches if they exist
        await loadJobMatches();
    }

    // Check existing pipeline state
    async function checkPipelineState() {
        const status = await DocOpsUtils.fetchDocopsStatus(basePath);
        if (status && status.tasks) {
            // Update badges based on existing tasks
            const taskMap = {
                'web_research.md': 'badge-research',
                'copy_job_matches.log.md': 'badge-copy',
                'company_research_batch.log': 'badge-company',
                'generate_applications_batch.log': 'badge-applications'
            };

            for (const [target, badgeId] of Object.entries(taskMap)) {
                const task = status.tasks[target];
                if (task) {
                    const state = task.status === 'COMPLETED' ? 'done' :
                        task.status === 'RUNNING' ? 'running' : 'error';
                    UIUtils.setBadge(badgeId, state);
                }
            }
        }
    }
    // Check if job matches exist and enable the copy step button if so
    async function checkJobMatchesExist() {
        try {
            const files = await FileIOUtils.listFiles(basePath, '.websearch/job_matches');
            if (files && files.length > 0) {
                const btn = document.getElementById('run-copy');
                if (btn) btn.disabled = false;
            }
        } catch (e) {
            // job_matches dir may not exist yet, that's fine
        }
    }

    // Handle status updates
    function handleStatusUpdate(target, taskInfo) {
        // Update session links
        linkManager.update(target, taskInfo);

        // Update specific UI elements based on target
        if (target === 'web_research.md') {
            updateStepStatus('badge-research', 'run-copy', taskInfo);
            // Even if research errors/partially completes, check for matches
            if (taskInfo.status === 'COMPLETED' || taskInfo.status === 'ERROR') {
                checkJobMatchesExist();
            }
        } else if (target === 'copy_job_matches.log.md') {
            updateStepStatus('badge-copy', 'run-company', taskInfo);
            if (taskInfo.status === 'COMPLETED') {
                loadJobMatches();
            }
        } else if (target.includes('company_research')) {
            updateStepStatus('badge-company', 'run-applications', taskInfo);
        } else if (target.includes('generate_applications')) {
            updateStepStatus('badge-applications', null, taskInfo);
            if (taskInfo.status === 'COMPLETED') {
                loadApplications();
            }
        }
    }

    // Update step status and enable next button
    function updateStepStatus(badgeId, nextButtonId, taskInfo) {
        const state = taskInfo.status === 'COMPLETED' ? 'done' :
            taskInfo.status === 'RUNNING' ? 'running' : 'error';
        UIUtils.setBadge(badgeId, state);

        if (nextButtonId && taskInfo.status === 'COMPLETED') {
            const btn = document.getElementById(nextButtonId);
            if (btn) btn.disabled = false;
        }
    }

    // Setup event listeners
    function setupEventListeners() {
         // Job details modal tab switching
         document.querySelectorAll('.modal-tab-btn').forEach(btn => {
             btn.addEventListener('click', function() {
                 const tabName = this.dataset.modalTab;
                 const modalContent = this.closest('.modal-content');
                 modalContent.querySelectorAll('.modal-tab-btn').forEach(b => b.classList.remove('active'));
                 this.classList.add('active');
                 modalContent.querySelectorAll('.modal-tab-pane').forEach(pane => pane.classList.remove('active'));
                 modalContent.querySelector(`#job-details-${tabName}`).classList.add('active');
             });
         });
         // Job details modal close
         document.querySelectorAll('[data-modal]').forEach(btn => {
             btn.addEventListener('click', function() {
                 document.getElementById(this.dataset.modal).classList.remove('show');
             });
         });
         // Copy raw button
         document.getElementById('copy-raw-btn').addEventListener('click', async function() {
             const text = document.getElementById('job-details-raw-text').value;
             try {
                 await navigator.clipboard.writeText(text);
                 UIUtils.setStatus('copy-raw-status', '✓ Copied!', 'success');
                 setTimeout(() => UIUtils.setStatus('copy-raw-status', '', ''), 2000);
             } catch (e) {
                 UIUtils.setStatus('copy-raw-status', 'Copy failed', 'error');
             }
         });

        // Tab switching
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                const tabGroup = this.parentElement;
                const tabName = this.dataset.tab;

                // Update active states
                tabGroup.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                this.classList.add('active');

                // Show corresponding pane
                document.querySelectorAll('.tab-pane').forEach(pane => {
                    pane.classList.remove('active');
                });
                const targetPane = document.getElementById(`tab-${tabName}`);
                if (targetPane) {
                    targetPane.classList.add('active');
                } else {
                    console.warn(`Tab pane not found: tab-${tabName}`);
                }
            });
        });

        // Results tab switching
        document.querySelectorAll('[data-results-tab]').forEach(btn => {
            btn.addEventListener('click', function() {
                const tabName = this.dataset.resultsTab;

                // Update active states
                document.querySelectorAll('[data-results-tab]').forEach(b => b.classList.remove('active'));
                this.classList.add('active');

                // Show corresponding pane
                document.querySelectorAll('.results-pane').forEach(pane => {
                    pane.classList.remove('active');
                });
                const targetResultsPane = document.getElementById(`results-${tabName}`);
                if (targetResultsPane) {
                    targetResultsPane.classList.add('active');
                } else {
                    console.warn(`Results pane not found: results-${tabName}`);
                }
            });
        });

        // Model configuration
        document.getElementById('save-config').addEventListener('click', saveConfiguration);

        // File operations
        document.getElementById('load-resume').addEventListener('click', () => loadFile('resume.json', 'resume-editor'));
        document.getElementById('save-resume').addEventListener('click', () => saveResume());
        document.getElementById('validate-resume').addEventListener('click', validateResume);

        document.getElementById('load-requirements').addEventListener('click', () => loadFile('requirements.md', 'requirements-editor'));
        document.getElementById('save-requirements').addEventListener('click', () => saveRequirements());

        document.getElementById('load-companies').addEventListener('click', () => loadFile('companies_to_avoid.txt', 'companies-editor'));
        document.getElementById('save-companies').addEventListener('click', () => saveCompanies());

        // Pipeline operations
        document.getElementById('run-research').addEventListener('click', runWebResearch);
        document.getElementById('run-copy').addEventListener('click', runCopyMatches);
        document.getElementById('run-full-pipeline').addEventListener('click', runFullPipeline);
        document.getElementById('stop-pipeline').addEventListener('click', stopPipeline);

        // Modal controls
        document.getElementById('btn-usage').addEventListener('click', showUsageModal);
        document.getElementById('btn-git').addEventListener('click', showGitModal);
        document.getElementById('btn-help').addEventListener('click', showHelpModal);

        document.querySelectorAll('.modal-close').forEach(btn => {
            btn.addEventListener('click', function() {
                this.closest('.modal').classList.remove('show');
            });
        });

        // Git operations
        document.getElementById('git-init').addEventListener('click', initGitRepo);
        document.getElementById('git-commit').addEventListener('click', commitChanges);
    }

    // Save configuration
    async function saveConfiguration() {
        const config = {
            smartModel: document.getElementById('smart-model').value,
            fastModel: document.getElementById('fast-model').value,
            imageModel: document.getElementById('image-model').value
        };

        ModelUtils.saveModelSelections('job-hunter', config);
        UIUtils.setStatus('config-status', 'Configuration saved', 'success');
    }

    // File operations
    async function loadFile(filename, editorId) {
        try {
            const content = await FileIOUtils.readFile(basePath, filename);
            if (content !== null) {
                document.getElementById(editorId).value = content;
                UIUtils.showToast(`Loaded ${filename}`, 'success');
            } else {
                UIUtils.showToast(`File ${filename} not found`, 'warning');
            }
        } catch (e) {
            UIUtils.showToast(`Failed to load ${filename}`, 'error');
        }
    }

    async function saveResume() {
        try {
            const content = document.getElementById('resume-editor').value;
            await FileIOUtils.writeFile(basePath, 'resume.json', content);
            UIUtils.setStatus('resume-status', 'Resume saved', 'success');
        } catch (e) {
            UIUtils.setStatus('resume-status', 'Failed to save', 'error');
        }
    }

    async function validateResume() {
        try {
            const content = document.getElementById('resume-editor').value;
            JSON.parse(content);
            UIUtils.setStatus('resume-status', 'Valid JSON', 'success');
        } catch (e) {
            UIUtils.setStatus('resume-status', 'Invalid JSON: ' + e.message, 'error');
        }
    }

    async function saveRequirements() {
        try {
            const content = document.getElementById('requirements-editor').value;
            await FileIOUtils.writeFile(basePath, 'requirements.md', content);
            UIUtils.setStatus('requirements-status', 'Requirements saved', 'success');
        } catch (e) {
            UIUtils.setStatus('requirements-status', 'Failed to save', 'error');
        }
    }

    async function saveCompanies() {
        try {
            const content = document.getElementById('companies-editor').value;
            await FileIOUtils.writeFile(basePath, 'companies_to_avoid.txt', content);
            UIUtils.setStatus('companies-status', 'Companies saved', 'success');
        } catch (e) {
            UIUtils.setStatus('companies-status', 'Failed to save', 'error');
        }
    }

    // Pipeline operations
    async function runWebResearch() {
        try {
            logger.log('Starting web research for job opportunities...');
            UIUtils.setBadge('badge-research', 'running');

            const models = getSelectedModels();
            const taskId = await DocOpsUtils.runDocOp(
                sessionId,
                'ops/job_search.md',
                'web_research.md',
                models
            );

            logger.logHtml(`Web research started. <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor progress</a>`);

        } catch (e) {
            logger.log('Failed to start web research: ' + e.message, 'error');
            UIUtils.setBadge('badge-research', 'error');
        }
    }

    async function runCopyMatches() {
        try {
            logger.log('Copying job matches...');
            UIUtils.setBadge('badge-copy', 'running');

            const models = getSelectedModels();
            const taskId = await DocOpsUtils.runDocOp(
                sessionId,
                'ops/copy_job_matches.md',
                'copy_job_matches.log.md',
                models
            );

            logger.logHtml(`Copying matches. <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor progress</a>`);

        } catch (e) {
            logger.log('Failed to copy matches: ' + e.message, 'error');
            UIUtils.setBadge('badge-copy', 'error');
        }
    }

    async function runCompanyResearch() {
        try {
            logger.log('Starting company research...');
            UIUtils.setBadge('badge-company', 'running');

            const models = getSelectedModels();




             // Get list of all companies
             const companies = await FileIOUtils.listFiles(basePath, 'job_matches');
             if (!companies || companies.filter(c => c.type === 'directory').length === 0) {
                 throw new Error('No job matches found to research');
             }

             const companyDirs = companies.filter(c => c.type === 'directory');
             logger.log(`Found ${companyDirs.length} companies to research`);

             const promises = companyDirs.map(company => {
                 const targetPath = `job_matches/${company.name}/company_research.md`;
                 return DocOpsUtils.runDocOp(sessionId, 'ops/company_research.md', targetPath, models);
             });

             await Promise.all(promises);
             logger.log('Company research started for all companies', 'success');

        } catch (e) {
            logger.log('Failed to research companies: ' + e.message, 'error');
            UIUtils.setBadge('badge-company', 'error');
        }
    }

    async function runGenerateApplications() {
        try {
            logger.log('Generating application materials...');
            UIUtils.setBadge('badge-applications', 'running');

            const models = getSelectedModels();




             const companies = await FileIOUtils.listFiles(basePath, 'job_matches');
             if (!companies || companies.filter(c => c.type === 'directory').length === 0) {
                 throw new Error('No job matches found');
             }

             const companyDirs = companies.filter(c => c.type === 'directory');
             logger.log(`Generating applications for ${companyDirs.length} companies`);

             const promises = companyDirs.map(company => {
                 const targetPath = `job_matches/${company.name}/application.md`;
                 return DocOpsUtils.runDocOp(sessionId, 'ops/generate_application.md', targetPath, models);
             });

             await Promise.all(promises);
             logger.log('Applications generation started for all companies', 'success');

        } catch (e) {
            logger.log('Failed to generate applications: ' + e.message, 'error');
            UIUtils.setBadge('badge-applications', 'error');
        }
    }

    async function runFullPipeline() {
        if (pipelineRunning) return;

        pipelineRunning = true;
        pipelineAborted = false;

        document.getElementById('run-full-pipeline').style.display = 'none';
        document.getElementById('stop-pipeline').style.display = 'block';

        logger.clear();
        logger.log('Starting full job search pipeline...');

        try {
            // Save all inputs first
            await saveResume();
            await saveRequirements();
            await saveCompanies();

            // Step 1: Web Research
            if (!pipelineAborted) {
                await runWebResearch();
                await DocOpsUtils.waitForTask(basePath, 'web_research.md', 600000, (target, info) => {
                    handleStatusUpdate(target, info);
                });
            }

            // Step 2: Copy Matches
            if (!pipelineAborted) {
                await runCopyMatches();
                await DocOpsUtils.waitForTask(basePath, 'copy_job_matches.log.md', 300000, (target, info) => {
                    handleStatusUpdate(target, info);
                });
            }

            // Step 3: Company Research
            if (!pipelineAborted) {
                await runCompanyResearch();
                // Wait for all company research tasks
                await new Promise(resolve => setTimeout(resolve, 5000)); // Give time for tasks to register
                const status = await DocOpsUtils.fetchDocopsStatus(basePath);
                const companyTasks = Object.keys(status.tasks || {}).filter(t => t.includes('company_research'));

                for (const task of companyTasks) {
                    if (pipelineAborted) break;
                    await DocOpsUtils.waitForTask(basePath, task, 300000, (target, info) => {
                        handleStatusUpdate(target, info);
                    });
                }
            }

            // Step 4: Generate Applications
            if (!pipelineAborted) {
                await runGenerateApplications();
                // Wait for all application tasks
                await new Promise(resolve => setTimeout(resolve, 5000));
                const status = await DocOpsUtils.fetchDocopsStatus(basePath);
                const appTasks = Object.keys(status.tasks || {}).filter(t => t.includes('application.md'));

                for (const task of appTasks) {
                    if (pipelineAborted) break;
                    await DocOpsUtils.waitForTask(basePath, task, 300000, (target, info) => {
                        handleStatusUpdate(target, info);
                    });
                }
            }

            if (!pipelineAborted) {
                logger.log('Pipeline completed successfully!', 'success');
                UIUtils.showToast('Job search pipeline completed!', 'success');

                // Load results
                await loadJobMatches();
                await loadApplications();

                // Switch to results tab
                document.querySelector('[data-results-tab="matches"]').click();
            }

        } catch (e) {
            logger.log('Pipeline error: ' + e.message, 'error');
            UIUtils.showToast('Pipeline failed', 'error');
        } finally {
            pipelineRunning = false;
            document.getElementById('run-full-pipeline').style.display = 'block';
            document.getElementById('stop-pipeline').style.display = 'none';
        }
    }

    function stopPipeline() {
        pipelineAborted = true;
        logger.log('Pipeline stop requested...', 'warning');
    }

    // Load job matches
    async function loadJobMatches() {
        try {
        const companies = await FileIOUtils.listFiles(basePath, 'job_matches');
            const matchesDiv = document.getElementById('job-matches-list');

        if (!companies || companies.filter(c => c.type === 'directory').length === 0) {
                matchesDiv.innerHTML = '<p class="placeholder">No job matches yet. Run the job search to find opportunities.</p>';
                return;
            }








        let html = '';

        for (const company of companies) {
            if (company.type !== 'directory') continue;
            const companyName = company.name;
            const jobFiles = await FileIOUtils.listFiles(basePath, `job_matches/${companyName}`);
            if (!jobFiles) continue;

            const jobMdFiles = jobFiles.filter(f => f.type !== 'directory' && f.name.endsWith('.md') && f.name !== 'company_research.md' && f.name !== 'application.md');
            if (jobMdFiles.length === 0) continue;

            // Check if company research already exists
            const hasResearch = jobFiles.some(f => f.name === 'company_research.md');
            const escapedCompany = companyName.replace(/'/g, "\\'");

            html += `
                <div class="company-group" id="company-group-${UIUtils.escapeHtml(companyName)}">
                    <div class="company-group-header">
                        <div class="company-group-title">
                            <span class="company-group-name">${UIUtils.escapeHtml(companyName.replace(/_/g, ' '))}</span>
                            <span class="company-job-count">${jobMdFiles.length} position${jobMdFiles.length !== 1 ? 's' : ''}</span>
                        </div>
                        <div class="company-group-actions">
                            ${hasResearch
                                ? `<button class="btn btn-secondary btn-small" onclick="viewJobDetails('job_matches/${escapedCompany}/company_research.md')">🏢 View Research</button>`
                                : `<button class="btn btn-secondary btn-small" onclick="runCompanyResearchFor('${escapedCompany}')">🏢 Research Company</button>`
                            }
                        </div>
                    </div>
                    <div class="company-jobs">
            `;

            for (const jobFile of jobMdFiles) {
                const filePath = `job_matches/${companyName}/${jobFile.name}`;
                const jobData = await FileIOUtils.readFile(basePath, filePath);
                const jobTitle = jobFile.name.replace(/\.md$/, '').replace(/_/g, ' ');
                const escapedPath = filePath.replace(/'/g, "\\'");

                // Check if application already exists for this job
                const appFileName = jobFile.name.replace(/\.md$/, '') + '_application.md';
                const hasApplication = jobFiles.some(f => f.name === appFileName || f.name === 'application.md');
                const appPath = `job_matches/${companyName}/application.md`;
                const escapedAppPath = appPath.replace(/'/g, "\\'");

                html += `
                    <div class="job-card">
                        <h4>${UIUtils.escapeHtml(jobTitle)}</h4>
                        ${jobData ? `<div class="job-details">${UIUtils.renderMarkdown(jobData.substring(0, 400))}...</div>` : ''}
                        <div class="job-actions">
                            <button class="btn-small btn-primary" onclick="viewJobDetails('${escapedPath}')">📄 View Details</button>
                            ${hasApplication
                                ? `<button class="btn-small btn-secondary" onclick="viewJobDetails('${escapedAppPath}')">✉️ View Application</button>
                                   <button class="btn-small" onclick="downloadApplication('${escapedCompany}')">⬇️ Download</button>`
                                : `<button class="btn-small btn-secondary" onclick="runGenerateApplicationFor('${escapedCompany}', '${escapedPath}')">✉️ Generate Application</button>`
                            }
                        </div>
                    </div>
                `;
            }

            html += `</div></div>`;
        }

        matchesDiv.innerHTML = html || '<p class="placeholder">No job matches yet. Run the job search to find opportunities.</p>';

        } catch (e) {
            console.error('Failed to load job matches:', e);
        }
    }

    // Load applications
    async function loadApplications() {
        try {
            const matches = await FileIOUtils.listFiles(basePath, 'job_matches');
            const appsDiv = document.getElementById('applications-list');

            let hasApplications = false;
            let html = '<div class="application-cards">';

            for (const match of matches) {
                if (match.type === 'directory') {
                    const appData = await FileIOUtils.readFile(basePath, `job_matches/${match.name}/application.md`);
                    if (appData) {
                        hasApplications = true;
                        html += `
                            <div class="application-card">
                                <h4>${UIUtils.escapeHtml(match.name.replace(/_/g, ' '))}</h4>
                                <div class="application-preview">${UIUtils.renderMarkdown(appData.substring(0, 300))}...</div>
                                <div class="application-actions">
                                    <button class="btn-small" onclick="viewApplication('${match.name}')">View Full Application</button>
                                    <button class="btn-small btn-secondary" onclick="downloadApplication('${match.name}')">Download</button>
                                </div>
                            </div>
                        `;
                    }
                }
            }

            html += '</div>';

            if (!hasApplications) {
                appsDiv.innerHTML = '<p class="placeholder">No applications generated yet.</p>';
            } else {
                appsDiv.innerHTML = html;
            }

        } catch (e) {
            console.error('Failed to load applications:', e);
        }
    }

    // Get selected models
    function getSelectedModels() {
        const models = {};
        const smartModel = document.getElementById('smart-model').value;
        const fastModel = document.getElementById('fast-model').value;
        const imageModel = document.getElementById('image-model').value;
        if (smartModel) models.smartModel = smartModel;
        if (fastModel) models.fastModel = fastModel;
        if (imageModel) models.imageModel = imageModel;
        return models;
    }

    // Modal functions
    async function showUsageModal() {
        const modal = document.getElementById('usage-modal');
        modal.classList.add('show');

        // Fetch usage data
        const sessions = linkManager.getAllSessions();
        sessions.push(sessionId); // Include main session

        const usage = await UsageUtils.aggregateUsage([...new Set(sessions)]);

        // Update summary
        UsageUtils.renderUsageSummary(usage.totals, {
            prompt: document.getElementById('usage-prompt'),
            completion: document.getElementById('usage-completion'),
            total: document.getElementById('usage-total'),
            cost: document.getElementById('usage-cost')
        });

        // Update details table
        document.getElementById('usage-details').innerHTML =
            UsageUtils.createUsageTableHtml(usage.models, usage.totals);
    }

    async function showGitModal() {
        const modal = document.getElementById('git-modal');
        modal.classList.add('show');

        // Fetch git status
        try {
            const status = await GitUtils.getStatus(basePath);
            document.getElementById('git-status').innerHTML = GitUtils.formatStatus(status);

            document.getElementById('git-init').style.display = status.initialized ? 'none' : 'block';
            document.getElementById('git-commit').disabled = !status.initialized;
            document.getElementById('git-message').disabled = !status.initialized;

        } catch (e) {
            document.getElementById('git-status').innerHTML =
                '<p class="error">Failed to fetch Git status</p>';
        }
    }

    function showHelpModal() {
        document.getElementById('help-modal').classList.add('show');
    }

    // Git operations
    async function initGitRepo() {
        try {
            await GitUtils.initRepository(basePath);
            UIUtils.showToast('Git repository initialized', 'success');
            showGitModal(); // Refresh status
        } catch (e) {
            UIUtils.showToast('Failed to initialize repository', 'error');
        }
    }

    async function commitChanges() {
        const message = document.getElementById('git-message').value.trim();
        if (!message) {
            UIUtils.showToast('Please enter a commit message', 'warning');
            return;
        }

        try {
            await GitUtils.commit(basePath, message);
            UIUtils.showToast('Changes committed successfully', 'success');
            document.getElementById('git-message').value = '';
            showGitModal(); // Refresh status
        } catch (e) {
            UIUtils.showToast('Failed to commit changes', 'error');
        }
    }

// Global functions for onclick handlers
      window.viewJobDetails = async function(filePath) {
         const modal = document.getElementById('job-details-modal');
         const renderedPane = document.getElementById('job-details-rendered');
         const rawPane = document.getElementById('job-details-raw-text');
         const titleEl = document.getElementById('job-details-title');
         const subtitleEl = document.getElementById('job-details-subtitle');

         // Parse title/company from path: job_matches/CompanyName/job_title.md
         const parts = filePath.split('/');
         const companyName = parts[1] ? parts[1].replace(/_/g, ' ') : '';
         const jobTitle = parts[2] ? parts[2].replace(/\.md$/, '').replace(/_/g, ' ') : 'Job Details';

         titleEl.textContent = jobTitle;
         subtitleEl.textContent = companyName;

         // Reset to rendered tab
         document.querySelectorAll('.modal-tab-btn').forEach(b => b.classList.remove('active'));
         document.querySelector('[data-modal-tab="rendered"]').classList.add('active');
         document.querySelectorAll('.modal-tab-pane').forEach(p => p.classList.remove('active'));
         document.getElementById('job-details-rendered').classList.add('active');

         // Show loading state
         renderedPane.innerHTML = '<div class="job-details-loading"><div class="spinner"></div> Loading...</div>';
         rawPane.value = '';
         modal.classList.add('show');

         try {
             const content = await FileIOUtils.readFile(basePath, filePath);
             if (content) {
                 renderedPane.innerHTML = UIUtils.renderMarkdown(content);
                 rawPane.value = content;
             } else {
                 renderedPane.innerHTML = '<p class="placeholder">No content found.</p>';
                 rawPane.value = '';
             }
         } catch (e) {
             renderedPane.innerHTML = '<p class="placeholder" style="color:var(--color-danger)">Failed to load job details.</p>';
             rawPane.value = '';
         }
     };

    window.viewApplication = async function(jobName) {
        const appPath = `job_matches/${jobName}/application.md`;
        const appUrl = `${basePath}/${appPath}`;
        window.open(appUrl, '_blank');
    };

    window.downloadApplication = async function(jobName) {
        try {
            const appPath = `job_matches/${jobName}/application.md`;
            const content = await FileIOUtils.readFile(basePath, appPath);

            const blob = new Blob([content], { type: 'text/markdown' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${jobName}_application.md`;
            a.click();
            URL.revokeObjectURL(url);
        } catch (e) {
            UIUtils.showToast('Failed to download application', 'error');
        }
    };
     window.runCompanyResearchFor = async function(companyName) {
         try {
             const models = getSelectedModels();
             const targetPath = `job_matches/${companyName}/company_research.md`;
             logger.log(`Starting company research for ${companyName.replace(/_/g, ' ')}...`);
             const taskId = await DocOpsUtils.runDocOp(
                 sessionId,
                 'ops/company_research.md',
                 targetPath,
                 models
             );
             UIUtils.showToast(`Research started for ${companyName.replace(/_/g, ' ')}`, 'success');
             logger.logHtml(`Research for ${UIUtils.escapeHtml(companyName.replace(/_/g, ' '))} started. <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor progress</a>`);
             // Refresh the job matches list when done
             setTimeout(() => loadJobMatches(), 2000);
         } catch (e) {
             UIUtils.showToast(`Failed to start research for ${companyName.replace(/_/g, ' ')}`, 'error');
             logger.log(`Failed to research ${companyName}: ` + e.message, 'error');
         }
     };
     window.runGenerateApplicationFor = async function(companyName, jobFilePath) {
         try {
             const models = getSelectedModels();
             const targetPath = `job_matches/${companyName}/application.md`;
             logger.log(`Generating application for ${companyName.replace(/_/g, ' ')}...`);
             const taskId = await DocOpsUtils.runDocOp(
                 sessionId,
                 'ops/generate_application.md',
                 targetPath,
                 models
             );
             UIUtils.showToast(`Application generation started for ${companyName.replace(/_/g, ' ')}`, 'success');
             logger.logHtml(`Application for ${UIUtils.escapeHtml(companyName.replace(/_/g, ' '))} started. <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor progress</a>`);
             setTimeout(() => loadJobMatches(), 2000);
         } catch (e) {
             UIUtils.showToast(`Failed to generate application for ${companyName.replace(/_/g, ' ')}`, 'error');
             logger.log(`Failed to generate application for ${companyName}: ` + e.message, 'error');
         }
     };

    // Initialize on load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();