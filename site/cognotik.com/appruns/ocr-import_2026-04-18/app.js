import { parseSessionUrl, getProxyUrl } from './utils/session.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { readFile, writeFile, fileExists, listFiles, deleteFile } from './utils/fileIO.js';
import { runDocOp, waitForTask, createStatusPoller } from './utils/docops.js';
import { renderMarkdown, setStatus, setBadge, showToast, createBatchLogger, getFileIcon, escapeHtml } from './utils/ui.js';
import { updateSessionLinks, createSessionLinkManager } from './utils/sessionLinks.js';
import { aggregateUsage, renderUsageSummary, createUsageTableHtml } from './utils/usage.js';

(async function () {
    'use strict';

    // ── Session & State ──────────────────────────────────────────────
    const { basePath, sessionId } = parseSessionUrl();
    const STORAGE_PREFIX = 'ocrImport';
    const INPUT_DIR = 'input';
    const OUTPUT_FILE = 'output.md';
    const OP_PATH = 'ops/run_ocr.md';

    let statusPoller = null;
    let ocrResults = {};       // { filename: content }
    let activeTab = null;
    let showRaw = false;
    const taskSessionIds = []; // track for usage aggregation
let inputFileMap = {};    // { baseName: { input: 'report.pdf', output: 'report.md' } }
let inputFileNames = [];  // track all input file names for mapping

    // ── DOM References ───────────────────────────────────────────────
    const smartModelSelect = document.getElementById('smart-model');
    const fastModelSelect = document.getElementById('fast-model');
    const imageModelSelect = document.getElementById('image-model');
    const fileInput = document.getElementById('file-input');
    const uploadArea = document.getElementById('upload-area');
    const browseBtn = document.getElementById('browse-btn');
    const fileListContainer = document.getElementById('file-list-container');
    const fileListEl = document.getElementById('file-list');
    const clearFilesBtn = document.getElementById('clear-files-btn');
    const refreshFilesBtn = document.getElementById('refresh-files-btn');
    const runOcrBtn = document.getElementById('run-ocr-btn');
    const batchLogContainer = document.getElementById('batch-log-container');
    const resultsSection = document.getElementById('results-section');
    const resultsTabs = document.getElementById('results-tabs');
    const resultsRendered = document.getElementById('results-rendered');
    const resultsRaw = document.getElementById('results-raw');
    const copyResultsBtn = document.getElementById('copy-results-btn');
    const downloadResultsBtn = document.getElementById('download-results-btn');
    const toggleRawBtn = document.getElementById('toggle-raw-btn');
    const saveSection = document.getElementById('save-section');
    const commitMessageInput = document.getElementById('commit-message');
    const saveCommitBtn = document.getElementById('save-commit-btn');
    const usageSection = document.getElementById('usage-section');

    const logger = createBatchLogger('batch-log');
    const linkManager = createSessionLinkManager(getProxyUrl);

    // ── Model Setup ──────────────────────────────────────────────────
    try {
        const availableModels = await loadApiProviders();
        const saved = loadModelSelections(STORAGE_PREFIX, ['smartModel', 'fastModel', 'imageModel']);
        populateModelDropdowns(availableModels, [smartModelSelect, fastModelSelect, imageModelSelect], saved);

        // Restore saved selections
        if (saved.smartModel) smartModelSelect.value = saved.smartModel;
        if (saved.fastModel) fastModelSelect.value = saved.fastModel;
        if (saved.imageModel) imageModelSelect.value = saved.imageModel;

        // Save on change
        const saveModels = () => {
            saveModelSelections(STORAGE_PREFIX, {
                smartModel: smartModelSelect.value,
                fastModel: fastModelSelect.value,
                imageModel: imageModelSelect.value
            });
        };
        smartModelSelect.addEventListener('change', saveModels);
        fastModelSelect.addEventListener('change', saveModels);
        imageModelSelect.addEventListener('change', saveModels);
    } catch (e) {
        console.warn('Failed to load models:', e);
        setStatus('upload-status', 'Warning: Could not load AI models. Check configuration.', 'warning');
    }

    // ── File Upload ──────────────────────────────────────────────────

    function formatFileSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    // Drag & drop
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.classList.add('drag-over');
    });

    uploadArea.addEventListener('dragleave', () => {
        uploadArea.classList.remove('drag-over');
    });

    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.classList.remove('drag-over');
        if (e.dataTransfer.files.length > 0) {
            handleFiles(e.dataTransfer.files);
        }
    });

    browseBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        fileInput.click();
    });

    uploadArea.addEventListener('click', (e) => {
        if (e.target !== browseBtn && !browseBtn.contains(e.target) && e.target !== fileInput) {
            fileInput.click();
        }
    });

    fileInput.addEventListener('change', () => {
        if (fileInput.files.length > 0) {
            handleFiles(fileInput.files);
            fileInput.value = '';
        }
    });

    async function handleFiles(files) {
        const allowedTypes = [
            'image/png', 'image/jpeg', 'image/gif', 'image/bmp', 'image/tiff', 'image/webp',
            'application/pdf'
        ];

        let uploadCount = 0;
        let errorCount = 0;

        setStatus('upload-status', `Uploading ${files.length} file(s)...`, 'info');

        for (const file of files) {
            if (!allowedTypes.includes(file.type)) {
                showToast(`Skipped "${file.name}" — unsupported format`, 'warning');
                errorCount++;
                continue;
            }

            try {
                const arrayBuffer = await file.arrayBuffer();
                const filePath = `${INPUT_DIR}/${file.name}`;

                // Upload as binary via PUT
                const response = await fetch(`${basePath}/${filePath}`, {
                    method: 'PUT',
                    body: arrayBuffer,
                    headers: {
                        'Content-Type': file.type
                    }
                });

                if (response.ok) {
                    uploadCount++;
                } else {
                    throw new Error(`HTTP ${response.status}`);
                }
            } catch (e) {
                console.error(`Failed to upload ${file.name}:`, e);
                showToast(`Failed to upload "${file.name}": ${e.message}`, 'error');
                errorCount++;
            }
        }

        if (uploadCount > 0) {
            setStatus('upload-status', `Uploaded ${uploadCount} file(s) successfully`, 'success');
            setBadge('badge-upload', 'done');
        }
        if (errorCount > 0 && uploadCount === 0) {
            setStatus('upload-status', `Failed to upload files`, 'error');
        }

        await refreshFileList();
    }

    async function refreshFileList() {
        try {
            const files = await listFiles(basePath, INPUT_DIR);

            if (!files || files.length === 0) {
                fileListContainer.style.display = 'none';
                clearFilesBtn.style.display = 'none';
                runOcrBtn.disabled = true;
                return;
            }

            // Filter out metadata files
            const inputFiles = files.filter(f => {
                const name = f.name || f;
                 return !name.startsWith('_') && !name.startsWith('.') && !name.endsWith('.md');
            });

            if (inputFiles.length === 0) {
                fileListContainer.style.display = 'none';
                runOcrBtn.disabled = true;
                return;
            }

            fileListContainer.style.display = 'block';
            clearFilesBtn.style.display = 'inline-flex';
            runOcrBtn.disabled = false;
            setBadge('badge-upload', 'done');
            // Build the input file map (baseName → { input, output })
            inputFileMap = {};
            inputFileNames = [];
            for (const file of inputFiles) {
                const name = file.name || file;
                inputFileNames.push(name);
                const baseName = name.replace(/\.[^/.]+$/, '');
                inputFileMap[baseName] = { input: name, output: baseName + '.md' };
            }


            fileListEl.innerHTML = '';
            for (const file of inputFiles) {
                const name = file.name || file;
                const size = file.size ? formatFileSize(file.size) : '';
                const icon = getFileIcon(name);
                const baseName = name.replace(/\.[^/.]+$/, '');
                const outputName = baseName + '.md';
                const hasOutput = ocrResults[outputName] !== undefined;

                const li = document.createElement('li');
                li.innerHTML = `
                    <span class="file-info">
                        <span class="file-icon">${icon}</span>
                        <span class="file-name">${escapeHtml(name)}</span>
                        ${size ? `<span class="file-size">(${size})</span>` : ''}
                        ${hasOutput ? `<span class="file-status-badge status-done">✅ OCR done → ${escapeHtml(outputName)}</span>` : ''}
                    </span>
                    <button class="file-delete" data-file="${escapeHtml(name)}" title="Delete">✕</button>
                `;
                fileListEl.appendChild(li);
            }

            // Attach delete handlers
            fileListEl.querySelectorAll('.file-delete').forEach(btn => {
                btn.addEventListener('click', async () => {
                    const fileName = btn.dataset.file;
                    if (confirm(`Delete "${fileName}"?`)) {
                        await deleteFile(basePath, `${INPUT_DIR}/${fileName}`);
                        showToast(`Deleted "${fileName}"`, 'info');
                        await refreshFileList();
                    }
                });
            });

        } catch (e) {
            console.error('Failed to list files:', e);
        }
    }

    clearFilesBtn.addEventListener('click', async () => {
        if (!confirm('Delete all input files?')) return;

        try {
            const files = await listFiles(basePath, INPUT_DIR);
            if (files) {
                for (const file of files) {
                    const name = file.name || file;
                     if (!name.startsWith('_') && !name.startsWith('.') && !name.endsWith('.md')) {
                        await deleteFile(basePath, `${INPUT_DIR}/${name}`);
                    }
                }
            }
            showToast('All input files cleared', 'info');
            setBadge('badge-upload', 'pending');
            await refreshFileList();
        } catch (e) {
            showToast('Failed to clear files: ' + e.message, 'error');
        }
    });

    refreshFilesBtn.addEventListener('click', refreshFileList);

    // ── OCR Execution ────────────────────────────────────────────────

    runOcrBtn.addEventListener('click', runOcr);

    async function runOcr() {
        runOcrBtn.disabled = true;
        setBadge('badge-ocr', 'running');
        batchLogContainer.style.display = 'block';
        logger.clear();
        logger.log('Starting OCR pipeline...', 'info');

        const smartModel = smartModelSelect.value;
        const fastModel = fastModelSelect.value;
        const imageModel = imageModelSelect.value;

        if (!smartModel || !fastModel) {
            setStatus('ocr-status', 'Please select AI models before running OCR', 'error');
            setBadge('badge-ocr', 'error');
            runOcrBtn.disabled = false;
            return;
        }

        try {
            // Get list of input files to process
            const files = await listFiles(basePath, INPUT_DIR);
            const inputFiles = (files || []).filter(f => {
                const name = f.name || f;
                 return !name.startsWith('_') && !name.startsWith('.') && !name.endsWith('.md');
            });

            if (inputFiles.length === 0) {
                setStatus('ocr-status', 'No input files found. Upload files first.', 'warning');
                setBadge('badge-ocr', 'error');
                runOcrBtn.disabled = false;
                return;
            }

            logger.log(`Found ${inputFiles.length} file(s) to process`, 'info');

            // Run the OCR operation
            const models = { smartModel, fastModel };
            if (imageModel) {
                models.imageModel = imageModel;
            }

            logger.log(`Using models: smart=${smartModel}, fast=${fastModel}${imageModel ? ', image=' + imageModel : ''}`, 'info');

            const taskId = await runDocOp(sessionId, OP_PATH, OUTPUT_FILE, models);
            taskSessionIds.push(taskId);

            logger.logHtml(
                `OCR task started — <a href="${getProxyUrl(taskId)}" target="_blank" style="color:#90cdf4;">Monitor Live Session</a>`,
                'info'
            );

            setStatus('ocr-status', 'OCR processing in progress...', 'info');

            // Start polling
            if (statusPoller) statusPoller.stop();
            statusPoller = createStatusPoller(basePath, (target, taskInfo) => {
                linkManager.update(target, taskInfo);
                updateSessionLinks(target, taskInfo, getProxyUrl, 'ocr-links');

                if (target === OUTPUT_FILE) {
                    if (taskInfo.status === 'RUNNING') {
                        logger.log(`Processing: ${target}...`, 'info');
                    } else if (taskInfo.status === 'COMPLETED') {
                        logger.log(`✅ OCR completed for ${target}`, 'success');
                    } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                        logger.log(`❌ OCR failed for ${target}: ${taskInfo.error || 'Unknown error'}`, 'error');
                    }
                }
            });
            statusPoller.start();

            // Wait for completion
            await waitForTask(basePath, OUTPUT_FILE, 600000, (target, task) => {
                if (target === OUTPUT_FILE) {
                    setStatus('ocr-status', `Status: ${task.status}`, 'info');
                }
            });

            // Task completed
            if (statusPoller) statusPoller.stop();
            setBadge('badge-ocr', 'done');
            setStatus('ocr-status', 'OCR completed successfully!', 'success');
            logger.log('🎉 All OCR processing complete!', 'success');
            showToast('OCR completed successfully!', 'success');

            // Load results
            await loadResults();

            // Show usage
            await loadUsage();

        } catch (e) {
            console.error('OCR failed:', e);
            setBadge('badge-ocr', 'error');
            setStatus('ocr-status', `OCR failed: ${e.message}`, 'error');
            logger.log(`❌ Error: ${e.message}`, 'error');
            showToast('OCR processing failed', 'error');
        } finally {
            runOcrBtn.disabled = false;
        }
    }

    // ── Results ──────────────────────────────────────────────────────

    async function loadResults() {
        ocrResults = {};


         // Check for .md output files in the input directory (OCR outputs appear alongside inputs)
        try {
             const outputFiles = await listFiles(basePath, INPUT_DIR);
            if (outputFiles) {
                for (const file of outputFiles) {
                    const name = file.name || file;
                    if (name.endsWith('.md') && !name.startsWith('_') && !name.startsWith('.')) {
                         const content = await readFile(basePath, `${INPUT_DIR}/${name}`);
                        if (content) {
                            ocrResults[name] = content;
                        }
                    }
                }
            }
        } catch (e) {
            // Fallback: try to read the main output file
            const mainContent = await readFile(basePath, OUTPUT_FILE);
            if (mainContent) {
                ocrResults[OUTPUT_FILE] = mainContent;
            }
        }

        if (Object.keys(ocrResults).length === 0) {
            setStatus('ocr-status', 'No OCR output found', 'warning');
            return;
        }

        // Show results section
        resultsSection.style.display = 'block';
        saveSection.style.display = 'block';
        setBadge('badge-results', 'done');

        // Build tabs
        buildResultsTabs();

        // Show first result
        const firstKey = Object.keys(ocrResults)[0];
        showResult(firstKey);
        // Refresh file list to show output status badges
        await refreshFileList();
    }

    function buildResultsTabs() {
        resultsTabs.innerHTML = '';
        const keys = Object.keys(ocrResults);

        if (keys.length <= 1) {
            // Still show a single tab so the file pair is visible
            resultsTabs.style.display = 'flex';
            if (keys.length === 1) {
                const key = keys[0];
                const baseName = key.replace(/\.md$/, '');
                const mapping = inputFileMap[baseName];
                const displayName = mapping ? `${mapping.input} → ${key}` : key;

                const tab = document.createElement('button');
                tab.className = 'results-tab active';
                tab.textContent = displayName;
                tab.title = mapping ? `Source: ${mapping.input}\nOutput: ${key}` : `Output: ${key}`;
                tab.dataset.key = key;
                resultsTabs.appendChild(tab);
            }
            return;
        }

        resultsTabs.style.display = 'flex';
        for (const key of keys) {
            // Derive a friendly display name from the .md filename
            const baseName = key.replace(/\.md$/, '');
            // Find the matching input file name if available
            const mapping = inputFileMap[baseName];
            const displayName = mapping ? `${mapping.input} → .md` : key;

            const tab = document.createElement('button');
            tab.className = 'results-tab';
            tab.textContent = displayName;
            tab.title = mapping ? `Source: ${mapping.input}\nOutput: ${key}` : `Output: ${key}`;
            tab.dataset.key = key;
            tab.addEventListener('click', () => showResult(key));
            resultsTabs.appendChild(tab);
        }
    }

    function showResult(key) {
        activeTab = key;
        const content = ocrResults[key] || '';

        // Update tabs
        resultsTabs.querySelectorAll('.results-tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.key === key);
        });
        // Derive display header
        const baseName = key.replace(/\.md$/, '');
        const mapping = inputFileMap[baseName];
        const sourceLabel = mapping ? mapping.input : key;


        // Render content
        if (showRaw) {
            resultsRendered.style.display = 'none';
            resultsRaw.style.display = 'block';
            resultsRaw.value = content;
        } else {
            resultsRendered.style.display = 'block';
            resultsRaw.style.display = 'none';
            const headerHtml = `<div class="result-file-header">
                <span class="result-source-label">📄 Source: <strong>${escapeHtml(sourceLabel)}</strong></span>
                <span class="result-output-label">→ ${escapeHtml(key)}</span>
            </div>`;
            let renderedContent;
            try {
                renderedContent = renderMarkdown(content || '');
            } catch (e) {
                console.warn('Markdown rendering failed, falling back to escaped HTML:', e);
                renderedContent = '<pre>' + escapeHtml(content || '') + '</pre>';
            }
            resultsRendered.innerHTML = headerHtml + renderedContent;
        }
    }

    toggleRawBtn.addEventListener('click', () => {
        showRaw = !showRaw;
        toggleRawBtn.textContent = showRaw ? '🔀 Show Rendered' : '🔀 Toggle Raw/Rendered';
        if (activeTab) showResult(activeTab);
    });

    copyResultsBtn.addEventListener('click', async () => {
        const allContent = Object.entries(ocrResults)
            .map(([name, content]) => {
                if (Object.keys(ocrResults).length > 1) {
                    const baseName = name.replace(/\.md$/, '');
                    const mapping = inputFileMap[baseName];
                    const displayName = mapping ? mapping.input : name;
                    return `# ${displayName}\n\n${content}`;
                }
                return content;
            })
            .join('\n\n---\n\n');

        try {
            await navigator.clipboard.writeText(allContent);
            showToast('Copied to clipboard!', 'success');
        } catch (e) {
            // Fallback
            resultsRaw.value = allContent;
            resultsRaw.style.display = 'block';
            resultsRaw.select();
            document.execCommand('copy');
            showToast('Copied to clipboard!', 'success');
        }
    });

    downloadResultsBtn.addEventListener('click', () => {
        const allContent = Object.entries(ocrResults)
            .map(([name, content]) => {
                if (Object.keys(ocrResults).length > 1) {
                    const baseName = name.replace(/\.md$/, '');
                    const mapping = inputFileMap[baseName];
                    const displayName = mapping ? mapping.input : name;
                    return `# ${displayName}\n\n${content}`;
                }
                return content;
            })
            .join('\n\n---\n\n');

        const blob = new Blob([allContent], { type: 'text/markdown' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'ocr-results.md';
        a.click();
        URL.revokeObjectURL(url);
        showToast('Download started', 'success');
    });

    // Handle edits in raw mode
    resultsRaw.addEventListener('input', () => {
        if (activeTab && showRaw) {
            ocrResults[activeTab] = resultsRaw.value;
        }
    });

    // ── Save & Commit ────────────────────────────────────────────────

    saveCommitBtn.addEventListener('click', async () => {
        saveCommitBtn.disabled = true;
        setBadge('badge-save', 'running');

        try {
            // Save any edits back
            for (const [name, content] of Object.entries(ocrResults)) {
                 await writeFile(basePath, `${INPUT_DIR}/${name}`, content);
            }

            // Try git commit
            try {
                const { initRepository, commit, getStatus, formatStatus } = await import('./utils/git.js');

                // Ensure repo is initialized
                try {
                    await initRepository(basePath);
                } catch (e) {
                    // May already be initialized
                }

                const message = commitMessageInput.value.trim() || 'OCR import results';
                await commit(basePath, message);

                const status = await getStatus(basePath);
                document.getElementById('git-status').innerHTML = formatStatus(status);

                setBadge('badge-save', 'done');
                setStatus('save-status', 'Saved and committed successfully!', 'success');
                showToast('Changes committed!', 'success');
            } catch (gitError) {
                console.warn('Git commit failed (may not be available):', gitError);
                setBadge('badge-save', 'done');
                setStatus('save-status', 'Files saved (git commit unavailable)', 'warning');
                showToast('Files saved', 'success');
            }
        } catch (e) {
            setBadge('badge-save', 'error');
            setStatus('save-status', `Save failed: ${e.message}`, 'error');
            showToast('Save failed', 'error');
        } finally {
            saveCommitBtn.disabled = false;
        }
    });

    // ── Usage Tracking ───────────────────────────────────────────────

    async function loadUsage() {
        if (taskSessionIds.length === 0) return;

        try {
            const { models, totals } = await aggregateUsage(taskSessionIds);

            if (totals && (totals.inputTokens > 0 || totals.outputTokens > 0)) {
                usageSection.style.display = 'block';

                renderUsageSummary(totals, {
                    prompt: document.getElementById('prompt-tokens'),
                    completion: document.getElementById('completion-tokens'),
                    total: document.getElementById('total-tokens'),
                    cost: document.getElementById('total-cost')
                });

                if (models && Object.keys(models).length > 0) {
                    document.getElementById('usage-table-container').innerHTML = createUsageTableHtml(models, totals);
                }
            }
        } catch (e) {
            console.warn('Failed to load usage data:', e);
        }
    }

    // ── Initialization ───────────────────────────────────────────────

    async function initialize() {
        // Load existing input files
        await refreshFileList();

        // Check for existing results

         // Load all existing .md output files from the input directory
        let hasExistingResults = false;
        try {
             const allFiles = await listFiles(basePath, INPUT_DIR);
            if (allFiles) {
                for (const file of allFiles) {
                    const name = file.name || file;
                    if (name.endsWith('.md') && !name.startsWith('_') && !name.startsWith('.')) {
                         const content = await readFile(basePath, `${INPUT_DIR}/${name}`);
                        if (content) {
                            ocrResults[name] = content;
                            hasExistingResults = true;
                        }
                    }
                }
            }
        } catch (e) {
            // Fallback to just the main output file
            const existingOutput = await readFile(basePath, OUTPUT_FILE);
            if (existingOutput) {
                ocrResults[OUTPUT_FILE] = existingOutput;
                hasExistingResults = true;
            }
        }

        if (hasExistingResults) {
            resultsSection.style.display = 'block';
            saveSection.style.display = 'block';
            setBadge('badge-results', 'done');
            setBadge('badge-ocr', 'done');
            buildResultsTabs();
            showResult(Object.keys(ocrResults)[0]);
            logger.log('Loaded existing OCR results', 'info');
        }

        // Check for any running tasks
        try {
            const { fetchDocopsStatus } = await import('./utils/docops.js');
            const status = await fetchDocopsStatus(basePath);
            if (status) {
                const tasks = Object.entries(status);
                const runningTasks = tasks.filter(([, info]) => info.status === 'RUNNING');

                if (runningTasks.length > 0) {
                    setBadge('badge-ocr', 'running');
                    batchLogContainer.style.display = 'block';
                    logger.log('Found running OCR task(s), resuming monitoring...', 'info');
                    setStatus('ocr-status', 'OCR processing in progress...', 'info');
                    runOcrBtn.disabled = true;

                    // Start polling
                    statusPoller = createStatusPoller(basePath, (target, taskInfo) => {
                        linkManager.update(target, taskInfo);
                        updateSessionLinks(target, taskInfo, getProxyUrl, 'ocr-links');

                        if (taskInfo.status === 'COMPLETED') {
                            logger.log(`✅ Completed: ${target}`, 'success');
                        } else if (taskInfo.status === 'ERROR' || taskInfo.status === 'FAILED') {
                            logger.log(`❌ Failed: ${target}`, 'error');
                        }
                    });
                    statusPoller.start();

                    // Wait for the output task
                    const outputTask = tasks.find(([t]) => t === OUTPUT_FILE);
                    if (outputTask) {
                        if (outputTask[1].sessionId) {
                            taskSessionIds.push(outputTask[1].sessionId);
                        }

                        try {
                            await waitForTask(basePath, OUTPUT_FILE, 600000);
                            setBadge('badge-ocr', 'done');
                            setStatus('ocr-status', 'OCR completed!', 'success');
                            showToast('OCR completed!', 'success');
                            await loadResults();
                            await loadUsage();
                        } catch (e) {
                            setBadge('badge-ocr', 'error');
                            setStatus('ocr-status', `OCR failed: ${e.message}`, 'error');
                        } finally {
                            runOcrBtn.disabled = false;
                            if (statusPoller) statusPoller.stop();
                        }
                    }
                }

                // Check completed tasks for usage
                const completedTasks = tasks.filter(([, info]) => info.status === 'COMPLETED');
                for (const [, info] of completedTasks) {
                    if (info.sessionId && !taskSessionIds.includes(info.sessionId)) {
                        taskSessionIds.push(info.sessionId);
                    }
                }
                if (taskSessionIds.length > 0) {
                    await loadUsage();
                }
            }
        } catch (e) {
            // No existing status — that's fine
        }
    }

    // Run initialization
    await initialize();

})();