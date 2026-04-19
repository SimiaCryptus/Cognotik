import { SessionUtils } from './utils/session.js';
import { FileIOUtils } from './utils/fileIO.js';
import { DocOpsUtils } from './utils/docops.js';
import { ModelUtils } from './utils/models.js';
import { UIUtils } from './utils/ui.js';
import { UsageUtils } from './utils/usage.js';

(function() {
    'use strict';

    // Global state
    const { basePath, sessionId, appId } = SessionUtils.parseSessionUrl();
    let availableModels = {};
    let statusPoller = null;
    let masterResume = null;
    // Track task→session IDs and completion state for render badge
    const taskSessionIds = {};
    const renderTargets = new Set(['standard.tex', 'simple.tex']);
    const renderCompleted = new Set();
     const refineTargets = new Set(['standard.tex', 'simple.tex']);
    // Map each tracked target to its links container element ID and badge element ID
const TARGET_UI = {
          'resume-custom.json': { badgeId: 'badge-customize', linksId: 'customize-links' },
          'standard.tex': { badgeId: 'badge-render', linksId: 'render-links' },
          'simple.tex': { badgeId: 'badge-render', linksId: 'render-links' },
          'standard.pdf': { badgeId: 'badge-render', linksId: 'render-links' },
          'simple.pdf': { badgeId: 'badge-render', linksId: 'render-links' },
          'build.log.md': { badgeId: 'badge-build', linksId: 'build-links' },
          'rebuild.log.md': { badgeId: 'badge-rebuild', linksId: 'rebuild-links' },
      };

    // Initialize application
    async function init() {
        try {
            // Display session info
            document.getElementById('session-id').textContent = sessionId;

            // Load available models
            availableModels = await ModelUtils.loadApiProviders();

            // Populate model dropdowns
            const savedModels = ModelUtils.loadModelSelections('resume-customizer', ['smartModel', 'fastModel', 'imageModel']);
            ModelUtils.populateModelDropdowns(
                availableModels,
                [
                    document.getElementById('smart-model'),
                    document.getElementById('fast-model'),
                    document.getElementById('image-model')
                ],
                savedModels
            );

            // Load existing files
            await loadExistingFiles();

            // Start status monitoring
            statusPoller = DocOpsUtils.createStatusPoller(basePath, onStatusPoll);

            // Setup event listeners
            setupEventListeners();

            // Check for existing pipeline state
            await checkExistingState(); // will start poller only if active tasks exist

            UIUtils.setStatus('app-status', 'Ready', 'success');
        } catch (error) {
            console.error('Initialization error:', error);
            UIUtils.setStatus('app-status', 'Initialization failed', 'error');
            UIUtils.showToast('Failed to initialize application', 'error');
        }
    }

    // Load existing files
    async function loadExistingFiles() {
        try {
             // Load job description
            const jobDesc = await FileIOUtils.readFile(basePath, 'job_description.md');
            if (jobDesc) {
                document.getElementById('job-description').value = jobDesc;
            }

            // Load company info
            const companyInfo = await FileIOUtils.readFile(basePath, 'company-info.md');
            if (companyInfo) {
                document.getElementById('company-info').value = companyInfo;
            }

            // Load master resume
            await loadMasterResume();

            // Load custom resume if exists
            const customResume = await FileIOUtils.readFile(basePath, 'resume-custom.json');
            if (customResume) {
                displayResumeJson(customResume);
                 try {
                     generateResumePreview(JSON.parse(customResume));
                 } catch (e) {
                     console.warn('Could not generate resume preview:', e);
                 }
            }
             // Load feedback files
             const feedbackStandard = await FileIOUtils.readFile(basePath, 'feedback-standard.md');
             if (feedbackStandard) {
                 document.getElementById('feedback-standard').value = feedbackStandard;
             }
             const feedbackSimple = await FileIOUtils.readFile(basePath, 'feedback-simple.md');
             if (feedbackSimple) {
                 document.getElementById('feedback-simple').value = feedbackSimple;
             }

             // Update download links for any already-generated files
             await updateDownloadLinks();
             // Load usage stats
             await updateUsageStats();
        } catch (error) {
            console.error('Error loading files:', error);
        }
    }

    // Load master resume
    async function loadMasterResume() {
        try {
            const resumeJson = await FileIOUtils.readFile(basePath, 'resume.json');
            if (resumeJson) {
                masterResume = JSON.parse(resumeJson);
                UIUtils.setBadge('master-resume-status', 'Loaded', 'success');
                displayMasterResumePreview();
            } else {
                UIUtils.setBadge('master-resume-status', 'Not Found', 'error');
            }
        } catch (error) {
            console.error('Error loading master resume:', error);
            UIUtils.setBadge('master-resume-status', 'Error', 'error');
        }
    }

    // Display master resume preview
    function displayMasterResumePreview() {
        if (!masterResume) return;

        const preview = document.getElementById('master-resume-preview');
        let html = '<div class="resume-summary">';

        if (masterResume.personal) {
            html += `<h4>${masterResume.personal.name || 'No Name'}</h4>`;
            html += `<p class="title">${masterResume.personal.title || 'No Title'}</p>`;
        }

        if (masterResume.summary) {
            html += `<div class="summary-text">${UIUtils.renderMarkdown(masterResume.summary)}</div>`;
        }

        html += '<div class="stats">';
        html += `<span>Experience: ${masterResume.experience?.length || 0} positions</span>`;
        html += `<span>Skills: ${Object.keys(masterResume.skills || {}).length} categories</span>`;
        html += `<span>Projects: ${masterResume.projects?.length || 0}</span>`;
        html += '</div>';

        html += '</div>';
        preview.innerHTML = html;
    }

    // Wrapper around updateTaskStatus that also stops the poller when all tasks are done
    function onStatusPoll(target, taskInfo) {
        updateTaskStatus(target, taskInfo);
    }

    // Start the status poller if not already running
    function ensurePollerRunning() {
        if (statusPoller && !statusPoller._running) {
            statusPoller.start();
        }
    }

    // Stop the status poller
    function stopPoller() {
        if (statusPoller && statusPoller.stop) {
            statusPoller.stop();
        }
    }

    // Check existing pipeline state
    async function checkExistingState() {
        let statusData;
        try {
            statusData = await DocOpsUtils.fetchDocopsStatus(basePath);
        } catch (e) {
            // File doesn't exist yet — no tasks have been run; don't start poller
            return;
        }
        if (!statusData) return;
        if (statusData && statusData.tasks && Object.keys(statusData.tasks).length > 0) {
            const hasActiveTasks = Object.values(statusData.tasks).some(
                t => t.status === 'RUNNING' || t.status === 'QUEUED' || t.status === 'PENDING'
            );
            // Update badges based on existing status
            Object.entries(statusData.tasks).forEach(([target, taskInfo]) => {
                updateTaskStatus(target, taskInfo);
            });
            // Only start polling if there are active (non-terminal) tasks
            if (hasActiveTasks) {
                ensurePollerRunning();
            }
        }
    }

    // Update task status in UI
    function updateTaskStatus(target, taskInfo) {






        const ui = TARGET_UI[target];
        if (!ui) return;

        const { badgeId, linksId } = ui;
        const { status, sessionId: taskSessionId } = taskInfo;
        // Skip update if nothing has changed for this target
        const prevStatus = updateTaskStatus._prev || (updateTaskStatus._prev = {});
        const prevKey = `${target}:${status}:${taskSessionId || ''}`;
        if (prevStatus[target] === prevKey) return;
        prevStatus[target] = prevKey;


        // Store session ID when we first learn it (from RUNNING or COMPLETED)
        if (taskSessionId && !taskSessionIds[target]) {
            taskSessionIds[target] = taskSessionId;
        }

        // Update the session links container for this target
        renderSessionLinks(linksId, target, taskInfo);

        // Compute badge state — render badge reflects all render targets collectively
        if (renderTargets.has(target)) {
            if (status === 'COMPLETED') {
                renderCompleted.add(target);
            } else if (status === 'RUNNING') {
                renderCompleted.delete(target);
            }
            // Only mark render as 'done' when every render target that has been
            // seen is completed; show 'running' if any is still running.
            const allDone = [...renderTargets].every(t => renderCompleted.has(t));
            const anyRunning = [...renderTargets].some(
                t => !renderCompleted.has(t) && taskSessionIds[t]
            );
            const badgeState = allDone ? 'done' : anyRunning ? 'running' : 'error';
            UIUtils.setBadge(badgeId, badgeState);
        } else {
            const badgeState = status === 'COMPLETED' ? 'done'
                : status === 'RUNNING' ? 'running'
                    : 'error';
            UIUtils.setBadge(badgeId, badgeState);
        }

        // Side-effects on completion
        if (status === 'COMPLETED') {
            if (target === 'resume-custom.json') loadCustomResume();
            if (target === 'build.log.md' || target === 'rebuild.log.md' ||
                target === 'standard.pdf' || target === 'simple.pdf' ||
                target === 'standard.tex' || target === 'simple.tex') {
                updateDownloadLinks();
            }
            // Check if all known tasks are now in a terminal state; if so, stop polling
            checkAndStopPoller();
        }
    }
    // Stop the poller if no tasks are still active
    function checkAndStopPoller() {
        DocOpsUtils.fetchDocopsStatus(basePath).then(statusData => {
            if (!statusData || !statusData.tasks) {
                stopPoller();
                return;
            }
            const hasActive = Object.values(statusData.tasks).some(
                t => t.status === 'RUNNING' || t.status === 'QUEUED' || t.status === 'PENDING'
            );
            if (!hasActive) {
                stopPoller();
            }
        }).catch(() => {
            // status file gone or unreadable — stop polling
            stopPoller();
        });
    }

    /**
     * Rebuild the session-links container for a given target.
     * Shows a "Monitor" link (proxy session) and a "Session" link when available.
     */
    function renderSessionLinks(linksId, target, taskInfo) {
        const container = document.getElementById(linksId);
        if (!container) return;

        const { status, sessionId: taskSessionId } = taskInfo;
        const sid = taskSessionId || taskSessionIds[target];

        // Build link list — accumulate across all targets sharing this container
        // by reading existing anchors and merging new ones by href.
        const existingHrefs = new Set(
            [...container.querySelectorAll('a')].map(a => a.getAttribute('href'))
        );

        const newLinks = [];

        if (sid) {
            const proxyUrl = SessionUtils.getProxyUrl(sid);
            if (proxyUrl && !existingHrefs.has(proxyUrl)) {
                const label = status === 'RUNNING' ? '⟳ Monitor live' : '📋 View session';
                newLinks.push({ href: proxyUrl, label, title: `Session ${sid}` });
            }
        }

        if (newLinks.length === 0 && container.children.length > 0) {
            // Nothing new to add; just update labels on existing links if status changed
            container.querySelectorAll('a[data-target]').forEach(a => {
                if (a.dataset.target === target) {
                    a.textContent = status === 'RUNNING' ? '⟳ Monitor live' : '📋 View session';
                }
            });
            return;
        }

        newLinks.forEach(({ href, label, title }) => {
            const a = document.createElement('a');
            a.href = href;
            a.textContent = label;
            a.title = title;
            a.target = '_blank';
            a.rel = 'noopener noreferrer';
            a.dataset.target = target;
            container.appendChild(a);
        });
    }

    // Load custom resume
    async function loadCustomResume() {
        try {
            const customResume = await FileIOUtils.readFile(basePath, 'resume-custom.json');
            if (customResume) {
                displayResumeJson(customResume);
                generateResumePreview(JSON.parse(customResume));
            }
        } catch (error) {
            console.error('Error loading custom resume:', error);
        }
    }

    // Display resume JSON in editor
    function displayResumeJson(jsonString) {
        const editor = document.getElementById('resume-json-editor');
        try {
            const formatted = JSON.stringify(JSON.parse(jsonString), null, 2);
            editor.innerHTML = `<pre><code>${UIUtils.escapeHtml(formatted)}</code></pre>`;
        } catch (error) {
            editor.innerHTML = `<pre><code>${UIUtils.escapeHtml(jsonString)}</code></pre>`;
        }
    }

    // Generate resume preview
    function generateResumePreview(resume) {
        const preview = document.getElementById('resume-preview');
        let html = '<div class="resume-content">';

        // Header
        if (resume.personal) {
            html += '<div class="resume-header">';
            html += `<h1>${resume.personal.name || 'Your Name'}</h1>`;
            html += `<p class="title">${resume.personal.title || 'Professional Title'}</p>`;

            html += '<div class="contact-info">';
            if (resume.personal.email) html += `<span>📧 ${resume.personal.email}</span>`;
            if (resume.personal.location) html += `<span>📍 ${resume.personal.location}</span>`;
            if (resume.personal.linkedin) html += `<span>💼 LinkedIn</span>`;
            if (resume.personal.github) html += `<span>🐙 GitHub</span>`;
            html += '</div></div>';
        }

        // Summary
        if (resume.summary) {
            html += '<div class="resume-section">';
            html += '<h2>Executive Summary</h2>';
            html += UIUtils.renderMarkdown(resume.summary);
            html += '</div>';
        }

        // Core Competencies
        if (resume.coreCompetencies && resume.coreCompetencies.length > 0) {
            html += '<div class="resume-section">';
            html += '<h2>Core Competencies</h2>';
            html += '<ul>';
            resume.coreCompetencies.forEach(comp => {
                html += `<li>${UIUtils.renderMarkdown(comp)}</li>`;
            });
            html += '</ul></div>';
        }

        // Experience
        if (resume.experience && resume.experience.length > 0) {
            html += '<div class="resume-section">';
            html += '<h2>Professional Experience</h2>';
            resume.experience.forEach(job => {
                html += '<div class="job-entry">';
                html += `<h3>${job.position} at ${job.company}</h3>`;
                html += `<p class="job-meta">${job.startDate || ''} - ${job.endDate || 'Present'}`;
                if (job.location) html += ` | ${job.location}`;
                html += '</p>';

                if (job.highlights && job.highlights.length > 0) {
                    html += '<ul>';
                    job.highlights.forEach(highlight => {
                        if (typeof highlight === 'string') {
                            html += `<li>${UIUtils.renderMarkdown(highlight)}</li>`;
                        } else if (highlight.description) {
                            html += `<li>${UIUtils.renderMarkdown(highlight.description)}</li>`;
                        }
                    });
                    html += '</ul>';
                }
                html += '</div>';
            });
            html += '</div>';
        }

        html += '</div>';
        preview.innerHTML = html;
    }

    // Update download links
    async function updateDownloadLinks() {
        const formats = ['standard', 'simple'];

        for (const format of formats) {
            const pdfExists = await FileIOUtils.fileExists(basePath, `${format}.pdf`);
            const texExists = await FileIOUtils.fileExists(basePath, `${format}.tex`);

            const pdfLink = document.getElementById(`download-${format}-pdf`);
            const texLink = document.getElementById(`download-${format}-tex`);

            if (pdfExists) {
                pdfLink.href = `${basePath}/${format}.pdf`;
                pdfLink.classList.remove('disabled');
            } else {
                pdfLink.removeAttribute('href');
                pdfLink.classList.add('disabled');
            }

            if (texExists) {
                texLink.href = `${basePath}/${format}.tex`;
                texLink.classList.remove('disabled');
            } else {
                texLink.removeAttribute('href');
                texLink.classList.add('disabled');
            }
        }

        // Load build log if exists
        const buildLog = await FileIOUtils.readFile(basePath, 'build.log.md');
        if (buildLog) {
             const section = document.getElementById('build-log-section');
             document.getElementById('build-log').innerHTML = UIUtils.renderMarkdown(buildLog);
             section.style.display = 'block';
        }
         // Load rebuild log if exists (show in same section, appended)
         const rebuildLog = await FileIOUtils.readFile(basePath, 'rebuild.log.md');
         if (rebuildLog) {
              const section = document.getElementById('build-log-section');
              const existing = document.getElementById('build-log').innerHTML;
              const rebuildHtml = '<hr style="margin:12px 0"><h4>Rebuild Log</h4>' + UIUtils.renderMarkdown(rebuildLog);
              document.getElementById('build-log').innerHTML = existing + rebuildHtml;
              section.style.display = 'block';
         }
    }

    // Setup event listeners
    function setupEventListeners() {
        // Save buttons
        document.getElementById('save-job-desc').addEventListener('click', saveJobDescription);
        document.getElementById('save-company-info').addEventListener('click', saveCompanyInfo);
        document.getElementById('reload-master').addEventListener('click', loadMasterResume);
         document.getElementById('edit-master').addEventListener('click', openMasterEditor);
         document.getElementById('cancel-master-edit').addEventListener('click', closeMasterEditor);
         document.getElementById('save-master-resume').addEventListener('click', saveMasterResume);
         document.getElementById('upload-master').addEventListener('change', handleMasterUpload);

        // Pipeline controls
        document.getElementById('run-pipeline').addEventListener('click', runFullPipeline);
        document.getElementById('run-selected').addEventListener('click', runSelectedSteps);
         // Feedback save buttons
         document.getElementById('save-feedback-standard').addEventListener('click', () => saveFeedback('standard'));
         document.getElementById('save-feedback-simple').addEventListener('click', () => saveFeedback('simple'));
         // Individual refinement buttons
         document.getElementById('run-refine-standard').addEventListener('click', () => runSingleRefinement('standard'));
         document.getElementById('run-refine-simple').addEventListener('click', () => runSingleRefinement('simple'));
         document.getElementById('run-refine-both').addEventListener('click', runRefineBothAndRebuild);
         document.getElementById('run-rebuild').addEventListener('click', runRebuildOnly);


        // Tab switching
        document.querySelectorAll('.tab-button').forEach(button => {
            button.addEventListener('click', (e) => {
                const tabName = e.target.dataset.tab;
                switchTab(tabName);
            });
        });

        // Model selection save
        document.getElementById('smart-model').addEventListener('change', saveModelSelections);
        document.getElementById('fast-model').addEventListener('change', saveModelSelections);
        document.getElementById('image-model').addEventListener('change', saveModelSelections);
        // Step checkboxes — enable/disable "Run Selected Steps" button
        document.querySelectorAll('.step-checkbox').forEach(checkbox => {
            checkbox.addEventListener('change', updateRunSelectedButton);
        });
    }

    // Save job description
    async function saveJobDescription() {
        const content = document.getElementById('job-description').value;
        try {
            await FileIOUtils.writeFile(basePath, 'job_description.md', content);
            UIUtils.setStatus('job-desc-status', 'Saved', 'success');
        } catch (error) {
            UIUtils.setStatus('job-desc-status', 'Save failed', 'error');
            console.error('Error saving job description:', error);
        }
    }

    // Save company info
    async function saveCompanyInfo() {
        const content = document.getElementById('company-info').value;
        try {
            await FileIOUtils.writeFile(basePath, 'company-info.md', content);
            UIUtils.setStatus('company-info-status', 'Saved', 'success');
        } catch (error) {
            UIUtils.setStatus('company-info-status', 'Save failed', 'error');
            console.error('Error saving company info:', error);
        }
    }
     // Open master resume inline editor
     function openMasterEditor() {
         const editorWrap = document.getElementById('master-resume-editor-wrap');
         const textarea = document.getElementById('master-resume-editor');
         // Pre-populate with current content if available
         if (masterResume) {
             textarea.value = JSON.stringify(masterResume, null, 2);
         }
         editorWrap.style.display = 'block';
         document.getElementById('master-resume-preview').style.display = 'none';
         textarea.focus();
     }
     // Close master resume inline editor
     function closeMasterEditor() {
         document.getElementById('master-resume-editor-wrap').style.display = 'none';
         document.getElementById('master-resume-preview').style.display = '';
         document.getElementById('master-save-status').textContent = '';
     }
     // Save master resume from editor textarea
     async function saveMasterResume() {
         const textarea = document.getElementById('master-resume-editor');
         const statusEl = document.getElementById('master-save-status');
         const raw = textarea.value.trim();
         if (!raw) {
             UIUtils.setStatus('master-save-status', 'Content is empty', 'error');
             return;
         }
         let parsed;
         try {
             parsed = JSON.parse(raw);
         } catch (e) {
             UIUtils.setStatus('master-save-status', `Invalid JSON: ${e.message}`, 'error');
             return;
         }
         try {
             await FileIOUtils.writeFile(basePath, 'resume.json', JSON.stringify(parsed, null, 2));
             masterResume = parsed;
             UIUtils.setStatus('master-save-status', 'Saved!', 'success');
             displayMasterResumePreview();
             UIUtils.setBadge('master-resume-status', 'Loaded', 'success');
             setTimeout(closeMasterEditor, 800);
         } catch (error) {
             UIUtils.setStatus('master-save-status', 'Save failed', 'error');
             console.error('Error saving master resume:', error);
         }
     }
     // Handle file upload for master resume
     function handleMasterUpload(event) {
         const file = event.target.files[0];
         if (!file) return;
         const reader = new FileReader();
         reader.onload = (e) => {
             const content = e.target.result;
             // Open editor and populate with uploaded content
             openMasterEditor();
             document.getElementById('master-resume-editor').value = content;
             document.getElementById('master-save-status').textContent = '';
             UIUtils.showToast(`Loaded "${file.name}" — review and click Save`, 'success');
         };
         reader.onerror = () => UIUtils.showToast('Failed to read file', 'error');
         reader.readAsText(file);
         // Reset input so the same file can be re-uploaded if needed
         event.target.value = '';
     }
     // Save feedback for a variant
     async function saveFeedback(variant) {
         const content = document.getElementById(`feedback-${variant}`).value;
         try {
             await FileIOUtils.writeFile(basePath, `feedback-${variant}.md`, content);
             UIUtils.setStatus(`feedback-${variant}-status`, 'Saved', 'success');
         } catch (error) {
             UIUtils.setStatus(`feedback-${variant}-status`, 'Save failed', 'error');
             console.error(`Error saving ${variant} feedback:`, error);
         }
     }
     // Run a single variant refinement (refine tex + rebuild PDF)
     async function runSingleRefinement(variant) {
         const logger = UIUtils.createBatchLogger('refinement-log');
         const button = document.getElementById(`run-refine-${variant}`);
         button.disabled = true;
         try {
             // Save feedback first
             await saveFeedback(variant);
             const models = {
                 smartModel: document.getElementById('smart-model').value,
                 fastModel: document.getElementById('fast-model').value,
                 imageModel: document.getElementById('image-model').value ||
                     document.getElementById('fast-model').value ||
                     document.getElementById('smart-model').value
             };
             if (!models.smartModel || !models.fastModel) {
                 throw new Error('Please select AI models before running');
             }
             const badgeId = `badge-refine-${variant}`;
             const opFile = `ops/finetune_resume_${variant}.md`;
             const output = `${variant}.tex`;
             logger.log(`Refining ${variant} format...`);
             UIUtils.setBadge(badgeId, 'running');
             const taskId = await DocOpsUtils.runDocOp(sessionId, opFile, output, models);
            ensurePollerRunning();
             if (taskId) {
                 logger.logHtml(`Refine ${variant}: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor</a>`);
             }
             await DocOpsUtils.waitForTask(basePath, output);
             UIUtils.setBadge(badgeId, 'done');
             logger.log(`✓ ${variant} refinement completed`, 'success');
             // Rebuild PDF
             logger.log(`Rebuilding ${variant} PDF...`);
             UIUtils.setBadge('badge-rebuild', 'running');
             const rebuildTaskId = await DocOpsUtils.runDocOp(sessionId, 'ops/rebuild_tex.md', 'rebuild.log.md', models);
            ensurePollerRunning();
             if (rebuildTaskId) {
                 logger.logHtml(`Rebuild: <a href="${SessionUtils.getProxyUrl(rebuildTaskId)}" target="_blank">Monitor</a>`);
             }
             await DocOpsUtils.waitForTask(basePath, 'rebuild.log.md');
             UIUtils.setBadge('badge-rebuild', 'done');
             logger.log('✓ PDF rebuilt', 'success');
             await updateDownloadLinks();
             await updateUsageStats();
             UIUtils.showToast(`${variant} resume refined!`, 'success');
         } catch (error) {
             logger.log(`❌ Refinement failed: ${error.message}`, 'error');
             UIUtils.showToast('Refinement failed', 'error');
         } finally {
             button.disabled = false;
         }
     }
     // Refine both variants and rebuild
     async function runRefineBothAndRebuild() {
         const logger = UIUtils.createBatchLogger('refinement-log');
         const button = document.getElementById('run-refine-both');
         button.disabled = true;
         try {
             // Save both feedback files
             await saveFeedback('standard');
             await saveFeedback('simple');
             const models = {
                 smartModel: document.getElementById('smart-model').value,
                 fastModel: document.getElementById('fast-model').value,
                 imageModel: document.getElementById('image-model').value ||
                     document.getElementById('fast-model').value ||
                     document.getElementById('smart-model').value
             };
             if (!models.smartModel || !models.fastModel) {
                 throw new Error('Please select AI models before running');
             }
             // Run both refinements in parallel
             logger.log('Refining both resume variants...');
             UIUtils.setBadge('badge-refine-standard', 'running');
             UIUtils.setBadge('badge-refine-simple', 'running');
             const refinePromises = [
                 { variant: 'standard', op: 'ops/finetune_resume_standard.md', output: 'standard.tex', badge: 'badge-refine-standard' },
                 { variant: 'simple', op: 'ops/finetune_resume_simple.md', output: 'simple.tex', badge: 'badge-refine-simple' }
             ].map(async (step) => {
                 const taskId = await DocOpsUtils.runDocOp(sessionId, step.op, step.output, models);
                ensurePollerRunning();
                 if (taskId) {
                     logger.logHtml(`Refine ${step.variant}: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor</a>`);
                 }
                 await DocOpsUtils.waitForTask(basePath, step.output);
                 UIUtils.setBadge(step.badge, 'done');
                 logger.log(`✓ ${step.variant} refinement completed`, 'success');
             });
             await Promise.all(refinePromises);
             // Rebuild PDFs
             logger.log('Rebuilding PDFs...');
             UIUtils.setBadge('badge-rebuild', 'running');
             const rebuildTaskId = await DocOpsUtils.runDocOp(sessionId, 'ops/rebuild_tex.md', 'rebuild.log.md', models);
            ensurePollerRunning();
             if (rebuildTaskId) {
                 logger.logHtml(`Rebuild: <a href="${SessionUtils.getProxyUrl(rebuildTaskId)}" target="_blank">Monitor</a>`);
             }
             await DocOpsUtils.waitForTask(basePath, 'rebuild.log.md');
             UIUtils.setBadge('badge-rebuild', 'done');
             logger.log('✓ PDFs rebuilt', 'success');
             logger.log('✓ Refinement complete!', 'success');
             await updateDownloadLinks();
             await updateUsageStats();
             UIUtils.showToast('Both resumes refined!', 'success');
         } catch (error) {
             logger.log(`❌ Refinement failed: ${error.message}`, 'error');
             UIUtils.showToast('Refinement failed', 'error');
         } finally {
             button.disabled = false;
         }
     }
     // Rebuild PDFs only (no refinement)
     async function runRebuildOnly() {
         const logger = UIUtils.createBatchLogger('refinement-log');
         const button = document.getElementById('run-rebuild');
         button.disabled = true;
         try {
             const models = {
                 smartModel: document.getElementById('smart-model').value,
                 fastModel: document.getElementById('fast-model').value,
                 imageModel: document.getElementById('image-model').value ||
                     document.getElementById('fast-model').value ||
                     document.getElementById('smart-model').value
             };
             if (!models.smartModel || !models.fastModel) {
                 throw new Error('Please select AI models before running');
             }
             logger.log('Rebuilding PDFs...');
             UIUtils.setBadge('badge-rebuild', 'running');
             const rebuildTaskId = await DocOpsUtils.runDocOp(sessionId, 'ops/rebuild_tex.md', 'rebuild.log.md', models);
            ensurePollerRunning();
             if (rebuildTaskId) {
                 logger.logHtml(`Rebuild: <a href="${SessionUtils.getProxyUrl(rebuildTaskId)}" target="_blank">Monitor</a>`);
             }
             await DocOpsUtils.waitForTask(basePath, 'rebuild.log.md');
             UIUtils.setBadge('badge-rebuild', 'done');
             logger.log('✓ PDFs rebuilt', 'success');
             await updateDownloadLinks();
             await updateUsageStats();
             UIUtils.showToast('PDFs rebuilt!', 'success');
         } catch (error) {
             logger.log(`❌ Rebuild failed: ${error.message}`, 'error');
             UIUtils.showToast('Rebuild failed', 'error');
         } finally {
             button.disabled = false;
         }
     }


    // Save model selections
    function saveModelSelections() {
        ModelUtils.saveModelSelections('resume-customizer', {
            smartModel: document.getElementById('smart-model').value,
            fastModel: document.getElementById('fast-model').value,
            imageModel: document.getElementById('image-model').value
        });
    }

    // Update "Run Selected Steps" button state based on checkbox selection
    function updateRunSelectedButton() {
        const anyChecked = [...document.querySelectorAll('.step-checkbox')].some(cb => cb.checked);
        document.getElementById('run-selected').disabled = !anyChecked;
    }

    // Run only the checked pipeline steps
    async function runSelectedSteps() {
        const allSteps = [
            {
                key: 'customize',
                name: 'Customize Resume',
                op: 'ops/customize_resume.md',
                output: 'resume-custom.json',
                badge: 'badge-customize'
            },
            {
                key: 'render',
                name: 'Render Standard Format',
                op: 'ops/render_standard.md',
                output: 'standard.tex',
                badge: 'badge-render',
                parallel: true
            },
            {
                key: 'render',
                name: 'Render Simple Format',
                op: 'ops/render_simple.md',
                output: 'simple.tex',
                badge: 'badge-render',
                parallel: true
            },
            {
                key: 'build',
                name: 'Build PDFs',
                op: 'ops/render_tex.md',
                output: 'build.log.md',
                badge: 'badge-build'
             },
             {
                 key: 'refine-standard',
                 name: 'Refine Standard',
                 op: 'ops/finetune_resume_standard.md',
                 output: 'standard.tex',
                 badge: 'badge-refine-standard',
                 preSave: 'standard'
             },
             {
                 key: 'refine-simple',
                 name: 'Refine Simple',
                 op: 'ops/finetune_resume_simple.md',
                 output: 'simple.tex',
                 badge: 'badge-refine-simple',
                 preSave: 'simple'
             },
             {
                 key: 'rebuild',
                 name: 'Rebuild PDFs',
                 op: 'ops/rebuild_tex.md',
                 output: 'rebuild.log.md',
                 badge: 'badge-rebuild'
             }
        ];

        const checkedKeys = new Set(
            [...document.querySelectorAll('.step-checkbox:checked')].map(cb => cb.dataset.step)
        );

         // Auto-include build after render, rebuild after refine
        const selectedSteps = allSteps.filter(s => {
            if (checkedKeys.has(s.key)) return true;
            if (s.key === 'build' && checkedKeys.has('render')) return true;
             if (s.key === 'rebuild' && (checkedKeys.has('refine-standard') || checkedKeys.has('refine-simple'))) return true;
            return false;
        });

        const logger = UIUtils.createBatchLogger('pipeline-log');
        const button = document.getElementById('run-selected');
        button.disabled = true;

        try {
            await saveJobDescription();
            await saveCompanyInfo();

            const models = {
                smartModel: document.getElementById('smart-model').value,
                fastModel: document.getElementById('fast-model').value,
                imageModel: document.getElementById('image-model').value ||
                    document.getElementById('fast-model').value ||
                    document.getElementById('smart-model').value
            };

            if (!models.smartModel || !models.fastModel) {
                throw new Error('Please select AI models before running');
            }

             const sequential = selectedSteps.filter(s => !s.parallel && s.key !== 'build' && s.key !== 'rebuild');
             const parallelRender = selectedSteps.filter(s => s.parallel);
             const refineSteps = selectedSteps.filter(s => s.key === 'refine-standard' || s.key === 'refine-simple');

            for (const step of sequential) {
                 // Save feedback if this is a refine step
                 if (step.preSave) {
                     await saveFeedback(step.preSave);
                 }
                logger.log(`Running: ${step.name}...`);
                UIUtils.setBadge(step.badge, 'running');
                try {
                    const taskId = await DocOpsUtils.runDocOp(sessionId, step.op, step.output, models);
                    logger.logHtml(`${step.name}: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor</a>`);
                    await DocOpsUtils.waitForTask(basePath, step.output);
                    UIUtils.setBadge(step.badge, 'done');
                    logger.log(`✓ ${step.name} completed`, 'success');
                } catch (error) {
                    UIUtils.setBadge(step.badge, 'error');
                    throw new Error(`${step.name} failed: ${error.message}`);
                }
            }

             if (parallelRender.length > 0) {
                logger.log('Rendering documents...');
                UIUtils.setBadge('badge-render', 'running');
                 const renderPromises = parallelRender.map(async step => {
                    const taskId = await DocOpsUtils.runDocOp(sessionId, step.op, step.output, models);
                   ensurePollerRunning();
                    if (taskId) {
                        logger.logHtml(`${step.name}: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor</a>`);
                    } else {
                        logger.log(`${step.name}: started (monitor link unavailable)`);
                    }
                    return DocOpsUtils.waitForTask(basePath, step.output);
                });
                await Promise.all(renderPromises);
                UIUtils.setBadge('badge-render', 'done');
            }

            // Run build step last (after any parallel render steps complete)
            const buildStep = selectedSteps.find(s => s.key === 'build');
            if (buildStep) {
                logger.log('Building PDF files...');
                UIUtils.setBadge('badge-build', 'running');
                try {
                    const buildTaskId = await DocOpsUtils.runDocOp(sessionId, buildStep.op, buildStep.output, models);
                    if (buildTaskId) {
                        logger.logHtml(`${buildStep.name}: <a href="${SessionUtils.getProxyUrl(buildTaskId)}" target="_blank">Monitor</a>`);
                    } else {
                        logger.log(`${buildStep.name}: started (monitor link unavailable)`);
                    }
                    await DocOpsUtils.waitForTask(basePath, buildStep.output);
                    UIUtils.setBadge('badge-build', 'done');
                    logger.log('✓ Build PDFs completed', 'success');
                } catch (error) {
                    UIUtils.setBadge('badge-build', 'error');
                    throw new Error(`${buildStep.name} failed: ${error.message}`);
                }
            }
             // Run refine steps (can be parallel since they target different files)
             if (refineSteps.length > 0) {
                 // Save feedback before refining
                 for (const step of refineSteps) {
                     if (step.preSave) await saveFeedback(step.preSave);
                 }
                 logger.log('Refining resume variants...');
                 const refinePromises = refineSteps.map(async step => {
                     UIUtils.setBadge(step.badge, 'running');
                     const taskId = await DocOpsUtils.runDocOp(sessionId, step.op, step.output, models);
                     ensurePollerRunning();
                     if (taskId) {
                         logger.logHtml(`${step.name}: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor</a>`);
                     }
                     await DocOpsUtils.waitForTask(basePath, step.output);
                     UIUtils.setBadge(step.badge, 'done');
                     logger.log(`✓ ${step.name} completed`, 'success');
                 });
                 await Promise.all(refinePromises);
             }
             // Run rebuild step after refinement
             const rebuildStep = selectedSteps.find(s => s.key === 'rebuild');
             if (rebuildStep) {
                 logger.log('Rebuilding PDFs after refinement...');
                 UIUtils.setBadge('badge-rebuild', 'running');
                 try {
                     const rebuildTaskId = await DocOpsUtils.runDocOp(sessionId, rebuildStep.op, rebuildStep.output, models);
                     ensurePollerRunning();
                     if (rebuildTaskId) {
                         logger.logHtml(`${rebuildStep.name}: <a href="${SessionUtils.getProxyUrl(rebuildTaskId)}" target="_blank">Monitor</a>`);
                     }
                     await DocOpsUtils.waitForTask(basePath, rebuildStep.output);
                     UIUtils.setBadge('badge-rebuild', 'done');
                     logger.log('✓ Rebuild completed', 'success');
                 } catch (error) {
                     UIUtils.setBadge('badge-rebuild', 'error');
                     throw new Error(`${rebuildStep.name} failed: ${error.message}`);
                 }
             }


            logger.log('✓ Selected steps completed!', 'success');
            UIUtils.showToast('Selected steps complete!', 'success');
            await updateUsageStats();
             await updateDownloadLinks();

        } catch (error) {
            logger.log(`❌ Failed: ${error.message}`, 'error');
            UIUtils.showToast('Selected steps failed', 'error');
        } finally {
            // Re-evaluate button state based on remaining checked boxes
            updateRunSelectedButton();
        }
    }

    // Switch tabs
    function switchTab(tabName) {
        document.querySelectorAll('.tab-button').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });

        document.querySelectorAll('.tab-pane').forEach(pane => {
            pane.classList.toggle('active', pane.id === `${tabName}-tab`);
        });
    }

    // Clear session link containers and reset render tracking before a new run
    function resetPipelineUI(targets) {
        targets.forEach(target => {
            const ui = TARGET_UI[target];
            if (!ui) return;
            delete taskSessionIds[target];
            renderCompleted.delete(target);
        });

        // Clear link containers (deduplicate by linksId)
        const clearedLinks = new Set();
        targets.forEach(target => {
            const ui = TARGET_UI[target];
            if (!ui || clearedLinks.has(ui.linksId)) return;
            clearedLinks.add(ui.linksId);
            const el = document.getElementById(ui.linksId);
            if (el) el.innerHTML = '';
        });

        // Reset badges to 'Ready' for active targets
        const clearedBadges = new Set();
        targets.forEach(target => {
            const ui = TARGET_UI[target];
            if (!ui || clearedBadges.has(ui.badgeId)) return;
            clearedBadges.add(ui.badgeId);
            UIUtils.setBadge(ui.badgeId, 'Ready');
        });
    }

    // Run full pipeline
    async function runFullPipeline() {
        const logger = UIUtils.createBatchLogger('pipeline-log');
        const button = document.getElementById('run-pipeline');
        button.disabled = true;

        try {
            // Save inputs first
            logger.log('Saving inputs...');
            await saveJobDescription();
            await saveCompanyInfo();
            // Reset UI for all pipeline targets
            resetPipelineUI(['resume-custom.json', 'standard.tex', 'simple.tex', 'build.log.md']);
            // Ensure poller is running for the pipeline
            ensurePollerRunning();


            // Get model selections
            const models = {
                smartModel: document.getElementById('smart-model').value,
                fastModel: document.getElementById('fast-model').value,
                imageModel: document.getElementById('image-model').value ||
                    document.getElementById('fast-model').value ||
                    document.getElementById('smart-model').value
            };

            if (!models.smartModel || !models.fastModel) {
                throw new Error('Please select AI models before running pipeline');
            }

            // Pipeline steps
            const steps = [
                {
                    name: 'Customize Resume',
                    op: 'ops/customize_resume.md',
                    output: 'resume-custom.json',
                    badge: 'badge-customize'
                },
                {
                    name: 'Render Standard Format',
                    op: 'ops/render_standard.md',
                    output: 'standard.tex',
                    badge: 'badge-render',
                    parallel: true
                },
                {
                    name: 'Render Simple Format',
                    op: 'ops/render_simple.md',
                    output: 'simple.tex',
                    badge: 'badge-render',
                    parallel: true
                },
                {
                    name: 'Build PDFs',
                    op: 'ops/render_tex.md',
                    output: 'build.log.md',
                    badge: 'badge-build'
                }
            ];

            // Run sequential steps
            for (const step of steps.filter(s => !s.parallel && s.output !== 'build.log.md')) {
                logger.log(`Running: ${step.name}...`);
                UIUtils.setBadge(step.badge, 'running');

                try {
                    const taskId = await DocOpsUtils.runDocOp(sessionId, step.op, step.output, models);
                    logger.logHtml(`${step.name}: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor</a>`);

                    await DocOpsUtils.waitForTask(basePath, step.output);
                    UIUtils.setBadge(step.badge, 'done');
                    logger.log(`✓ ${step.name} completed`, 'success');
                } catch (error) {
                    UIUtils.setBadge(step.badge, 'error');
                    throw new Error(`${step.name} failed: ${error.message}`);
                }
            }

            // Run parallel rendering steps
            const parallelSteps = steps.filter(s => s.parallel);
            if (parallelSteps.length > 0) {
                logger.log('Rendering documents...');
                UIUtils.setBadge('badge-render', 'running');

                const renderPromises = parallelSteps.map(async step => {
                    const taskId = await DocOpsUtils.runDocOp(sessionId, step.op, step.output, models);
                    ensurePollerRunning();
                    if (taskId) {
                        logger.logHtml(`${step.name}: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">Monitor</a>`);
                    } else {
                        logger.log(`${step.name}: started (monitor link unavailable)`);
                    }
                    return DocOpsUtils.waitForTask(basePath, step.output);
                });

                await Promise.all(renderPromises);
                UIUtils.setBadge('badge-render', 'done');
                logger.log('✓ Documents rendered', 'success');
            }

            // Build PDFs
            logger.log('Building PDF files...');
            UIUtils.setBadge('badge-build', 'running');
            const buildStep = steps.find(s => s.output === 'build.log.md');
            const buildTaskId = await DocOpsUtils.runDocOp(sessionId, buildStep.op, buildStep.output, models);
            if (buildTaskId) {
                logger.logHtml(`${buildStep.name}: <a href="${SessionUtils.getProxyUrl(buildTaskId)}" target="_blank">Monitor</a>`);
            } else {
                logger.log('Building PDFs: started (monitor link unavailable)');
            }

            await DocOpsUtils.waitForTask(basePath, 'build.log.md');
            UIUtils.setBadge('badge-build', 'done');
            logger.log('✓ PDFs built', 'success');

            logger.log('✓ Pipeline completed successfully!', 'success');
            UIUtils.showToast('Resume customization complete!', 'success');
            // Refresh download links and build log
            await updateDownloadLinks();


            // Update usage stats
            await updateUsageStats();

        } catch (error) {
            logger.log(`❌ Pipeline failed: ${error.message}`, 'error');
            UIUtils.showToast('Pipeline failed', 'error');
        } finally {
            button.disabled = false;
        }
    }

    // Update usage statistics
    async function updateUsageStats() {
        try {
            const usage = await UsageUtils.fetchUsageData(sessionId);
            const statsDiv = document.getElementById('usage-stats');
            statsDiv.innerHTML = UsageUtils.createUsageTableHtml(usage.models, usage.totals);
        } catch (error) {
            console.error('Error fetching usage stats:', error);
        }
    }

    // Initialize on load
    init();
})();
            ensurePollerRunning();