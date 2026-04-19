(function () {
    'use strict';
     /* ------------------------------------------------------------------ */
     /*  Markdown renderer (marked.js)                                      */
     /* ------------------------------------------------------------------ */
     function renderMarkdown(content) {
         if (typeof marked !== 'undefined') {
             return marked.parse(content);
         }
         // Fallback: plain text with line breaks
         return '<pre>' + escapeHtml(content) + '</pre>';
     }


    /* ------------------------------------------------------------------ */
    /*  Bootstrap                                                           */
    /* ------------------------------------------------------------------ */
    const { basePath, sessionId } = SessionUtils.parseSessionUrl();
    let availableModels = {};
    let statusPoller = null;
    let currentViewFile = null;
    let isRawView = false;
    let currentRound = 0;          // highest round that has been started
    let activeQuestionsFile = null; // which questions file is open for editing
    // Track task→session IDs for session link rendering (mirrors resume-customizer pattern)
    const taskSessionIds = {};
    // Map each tracked output file to its links container element ID
    const TARGET_UI = {
        'ideas.md':        { linksId: 'ideas-links' },
        'round_1/review.md': { linksId: 'review1-links' },
        'career_plan.md':  { linksId: 'plan-links' },
        'search-plan.md':       { linksId: 'search-links' },
        'resume.json':     { linksId: 'resume-gen-links' },
    };
    /** Return (and lazily register) the TARGET_UI entry for a given file path. */
    function getTargetUI(filePath) {
        if (TARGET_UI[filePath]) return TARGET_UI[filePath];
        // Dynamic round files
        const qMatch = filePath.match(/^round_(\d+)\/questions\.md$/);
        if (qMatch) {
            const id = `q-${qMatch[1]}`;
            TARGET_UI[filePath] = { linksId: `${id}-links` };
            return TARGET_UI[filePath];
        }
        const rMatch = filePath.match(/^round_(\d+)\/recommendations\.md$/);
        if (rMatch) {
            const id = `rec-${rMatch[1]}`;
            TARGET_UI[filePath] = { linksId: `${id}-links` };
            return TARGET_UI[filePath];
        }
        return null;
    }


    /* ------------------------------------------------------------------ */
    /*  DOM References                                                      */
    /* ------------------------------------------------------------------ */
    const $ = id => document.getElementById(id);

    const profileEditor   = $('profile-editor');
    const resumeEditor    = $('resume-editor');
    const goalsEditor     = $('goals-editor');
    const startBtn        = $('start-pipeline-btn');
    const saveAllBtn      = $('save-all-btn');
    const addRoundBtn     = $('add-round-btn');
    const generatePlanBtn = $('generate-plan-btn');
    const activityLog     = $('activity-log');
    const viewerContent   = $('viewer-content');
    const viewerTitle     = $('viewer-title');
    const questionsPanel  = $('questions-panel');
    const questionsEditor = $('questions-editor');
    const roundsContainer = $('rounds-container');
    const generateAssetsBtn = $('generate-assets-btn');

    /* ------------------------------------------------------------------ */
    /*  Initialisation                                                      */
    /* ------------------------------------------------------------------ */
    async function init() {
        try {
            availableModels = await ModelUtils.loadApiProviders();
            ModelUtils.populateModelDropdowns(
                availableModels,
                 [$('smart-model'), $('fast-model'), $('image-model')].filter(Boolean),
                 ModelUtils.loadModelSelections('career-advisor', ['smartModel', 'fastModel', 'imageModel'])
            );
        } catch (e) {
            log('Could not load model list: ' + e.message, 'error');
        }

        await loadInputFiles();
        await restoreState();
        bindEvents();

        statusPoller = DocOpsUtils.createStatusPoller(basePath, onTaskUpdate);
        statusPoller.start();

        log('Career Advisor ready', 'info');
    }

    /* ------------------------------------------------------------------ */
    /*  Load existing input files                                           */
    /* ------------------------------------------------------------------ */
    async function loadInputFiles() {
        const files = { profile: 'profile.md', resume: 'resume.md', goals: 'goals.md' };
        for (const [key, path] of Object.entries(files)) {
            try {
                const content = await FileIOUtils.readFile(basePath, path);
                if (content) $(`${key}-editor`).value = content;
            } catch (_) { /* file doesn't exist yet */ }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Restore pipeline state from existing files                          */
    /* ------------------------------------------------------------------ */
    async function restoreState() {
        // Check ideas
        if (await FileIOUtils.fileExists(basePath, 'ideas.md')) {
            setBadge('ideas', 'done');
            showNodeActions('ideas', 'ideas.md');
        }

        // Check review round 1
        if (await FileIOUtils.fileExists(basePath, 'round_1/review.md')) {
            setBadge('review1', 'done');
            showNodeActions('review1', 'round_1/review.md');
        }

        // Discover rounds
        let round = 1;
        while (true) {
            const qFile = `round_${round}/questions.md`;
            const rFile = `round_${round}/recommendations.md`;
            const qExists = await FileIOUtils.fileExists(basePath, qFile);
            const rExists = await FileIOUtils.fileExists(basePath, rFile);

            if (!qExists && !rExists) break;

            ensureRoundStage(round);
            currentRound = round;

            if (qExists) {
                setBadge(`q-${round}`, 'done');
                showNodeActions(`q-${round}`, qFile);
            }
            if (rExists) {
                setBadge(`rec-${round}`, 'done');
                showNodeActions(`rec-${round}`, rFile);
            }

            round++;
        }

        // Check career plan
        if (await FileIOUtils.fileExists(basePath, 'career_plan.md')) {
            setBadge('plan', 'done');
            showNodeActions('plan', 'career_plan.md');
            generatePlanBtn.disabled = false;
            generateAssetsBtn.disabled = false;
        }
        // Check search strategies
        if (await FileIOUtils.fileExists(basePath, 'search-plan.md')) {
            setBadge('search', 'done');
            showNodeActions('search', 'search-plan.md');
        }
        // Check generated resume
        if (await FileIOUtils.fileExists(basePath, 'resume.json')) {
            setBadge('resume-gen', 'done');
            showNodeActions('resume-gen', 'resume.json');
        }

        // Enable add-round if we have at least one recommendation
        if (currentRound > 0 && await FileIOUtils.fileExists(basePath, `round_${currentRound}/recommendations.md`)) {
            addRoundBtn.disabled = false;
        }

        // Enable generate plan if we have any recommendations
        if (currentRound > 0) {
            generatePlanBtn.disabled = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Event Bindings                                                      */
    /* ------------------------------------------------------------------ */
    function bindEvents() {
        // Tab switching
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                btn.classList.add('active');
                $(`tab-${btn.dataset.tab}`).classList.add('active');
            });
        });

        // Save all
        saveAllBtn.addEventListener('click', saveAllInputs);

        // Start pipeline
        startBtn.addEventListener('click', startPipeline);

        // Add round
        addRoundBtn.addEventListener('click', addNextRound);

        // Generate plan
        generatePlanBtn.addEventListener('click', generateFinalPlan);
        // Generate search & resume assets
        generateAssetsBtn.addEventListener('click', generateSearchAndResume);


        // Viewer controls
        $('toggle-raw-btn').addEventListener('click', toggleRawView);
        $('copy-output-btn').addEventListener('click', copyViewerContent);
        $('close-viewer-btn').addEventListener('click', closeViewer);
         $('expand-viewer-btn').addEventListener('click', toggleExpandViewer);

        // Questions panel
        $('save-questions-btn').addEventListener('click', saveQuestionsDraft);
        $('submit-questions-btn').addEventListener('click', submitQuestionsAndContinue);
     // Maximize questions panel
     $('maximize-questions-btn').addEventListener('click', toggleMaximizeQuestions);

        // Usage modal
        $('usage-btn').addEventListener('click', showUsageModal);
        document.querySelector('.modal-close').addEventListener('click', () => {
            $('usage-modal').classList.add('hidden');
        });
        $('usage-modal').querySelector('.modal-backdrop').addEventListener('click', () => {
            $('usage-modal').classList.add('hidden');
        });

        // Clear log
        $('clear-log-btn').addEventListener('click', () => { activityLog.innerHTML = ''; });

        // View buttons (delegated)
        document.addEventListener('click', e => {
            const viewBtn = e.target.closest('.node-view-btn');
            if (viewBtn) {
                const file = viewBtn.dataset.file;
                if (file) openViewer(file);
            }
            const answerBtn = e.target.closest('.round-answer-btn');
            if (answerBtn) {
                const round = parseInt(answerBtn.dataset.round, 10);
                openQuestionsEditor(round);
            }
        });

        // Model save on change
         [$('smart-model'), $('fast-model'), $('image-model')].filter(Boolean).forEach(sel => {
            sel.addEventListener('change', saveModelSelections);
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Save Inputs                                                         */
    /* ------------------------------------------------------------------ */
    async function saveAllInputs() {
        const saves = [
            { file: 'profile.md', value: profileEditor.value, statusId: 'profile-status' },
            { file: 'resume.md',  value: resumeEditor.value,  statusId: 'resume-status'  },
            { file: 'goals.md',   value: goalsEditor.value,   statusId: 'goals-status'   },
        ];

        let allOk = true;
        for (const s of saves) {
            if (!s.value.trim()) continue;
            try {
                await FileIOUtils.writeFile(basePath, s.file, s.value);
                UIUtils.setStatus(s.statusId, '✓ Saved', 'success');
            } catch (e) {
                UIUtils.setStatus(s.statusId, '✗ ' + e.message, 'error');
                allOk = false;
            }
        }

        if (allOk) {
            UIUtils.showToast('All inputs saved', 'success');
            log('Inputs saved', 'success');
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Pipeline: Start (Ideas + Review Round 1)                           */
    /* ------------------------------------------------------------------ */
    async function startPipeline() {
        if (!goalsEditor.value.trim()) {
            UIUtils.showToast('Please fill in your Goals before starting', 'error');
            document.querySelector('[data-tab="goals"]').click();
            return;
        }

        startBtn.disabled = true;
        log('Saving inputs...', 'info');
        await saveAllInputs();

        try {
            const models = getModels();

            // ── Step 1: Ideas ──────────────────────────────────────────
            log('Generating career ideas...', 'info');
            setBadge('ideas', 'running');
            setNodeRunning('ideas');

            const ideasTaskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/ideas.md', 'ideas.md', models
            );
            logLink('Monitor Ideas generation', ideasTaskId);
            updateNodeSessionLink('ideas', 'ideas.md', 'RUNNING', ideasTaskId);

            await DocOpsUtils.waitForTask(basePath, 'ideas.md');
            setBadge('ideas', 'done');
            setNodeDone('ideas');
            showNodeActions('ideas', 'ideas.md');
            updateNodeSessionLink('ideas', 'ideas.md', 'COMPLETED', ideasTaskId);
            log('Ideas generated ✓', 'success');

            // ── Step 2: Review (parallel with questions) ───────────────
            log('Starting materials review...', 'info');
            setBadge('review1', 'running');
            setNodeRunning('review1');

            const reviewTaskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/review.md', 'round_1/review.md', models
            );
            logLink('Monitor Review', reviewTaskId);
            updateNodeSessionLink('review1', 'round_1/review.md', 'RUNNING', reviewTaskId);

            // ── Step 3: Questions Round 1 (parallel) ───────────────────
            ensureRoundStage(1);
            currentRound = 1;
            log('Generating Round 1 questions...', 'info');
            setBadge('q-1', 'running');
            setNodeRunning('q-1');

            const q1TaskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/questions.md', 'round_1/questions.md', models
            );
            logLink('Monitor Questions R1', q1TaskId);
            updateNodeSessionLink('q-1', 'round_1/questions.md', 'RUNNING', q1TaskId);

            // Wait for both
            await Promise.all([
                DocOpsUtils.waitForTask(basePath, 'round_1/review.md').then(() => {
                    setBadge('review1', 'done');
                    setNodeDone('review1');
                    showNodeActions('review1', 'round_1/review.md');
                    updateNodeSessionLink('review1', 'round_1/review.md', 'COMPLETED', reviewTaskId);
                    log('Review complete ✓', 'success');
                }),
                DocOpsUtils.waitForTask(basePath, 'round_1/questions.md').then(() => {
                    setBadge('q-1', 'done');
                    setNodeDone('q-1');
                    showNodeActions(`q-1`, 'round_1/questions.md');
                    updateNodeSessionLink('q-1', 'round_1/questions.md', 'COMPLETED', q1TaskId);
                    log('Round 1 questions ready ✓', 'success');
                    promptAnswerQuestions(1);
                }),
            ]);

            addRoundBtn.disabled = false;
            UIUtils.showToast('Foundation analysis complete! Answer the questions to continue.', 'success');

        } catch (e) {
            log('Pipeline error: ' + e.message, 'error');
            UIUtils.showToast('Pipeline failed: ' + e.message, 'error');
        } finally {
            startBtn.disabled = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Pipeline: Add Next Round                                            */
    /* ------------------------------------------------------------------ */
    async function addNextRound() {
        const nextRound = currentRound + 1;
        const prevRecFile = `round_${currentRound}/recommendations.md`;

        // Check we have recommendations from previous round
        if (!(await FileIOUtils.fileExists(basePath, prevRecFile))) {
            UIUtils.showToast(`Complete Round ${currentRound} recommendations first`, 'error');
            return;
        }

        addRoundBtn.disabled = true;
        const models = getModels();

        try {
            ensureRoundStage(nextRound);
            currentRound = nextRound;

            log(`Generating Round ${nextRound} questions...`, 'info');
            setBadge(`q-${nextRound}`, 'running');
            setNodeRunning(`q-${nextRound}`);

            const taskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/questions.md', `round_${nextRound}/questions.md`, models
            );
            logLink(`Monitor Questions R${nextRound}`, taskId);
            updateNodeSessionLink(`q-${nextRound}`, `round_${nextRound}/questions.md`, 'RUNNING', taskId);

            await DocOpsUtils.waitForTask(basePath, `round_${nextRound}/questions.md`);
            setBadge(`q-${nextRound}`, 'done');
            setNodeDone(`q-${nextRound}`);
            showNodeActions(`q-${nextRound}`, `round_${nextRound}/questions.md`);
            updateNodeSessionLink(`q-${nextRound}`, `round_${nextRound}/questions.md`, 'COMPLETED', taskId);
            log(`Round ${nextRound} questions ready ✓`, 'success');
            promptAnswerQuestions(nextRound);

        } catch (e) {
            log('Error adding round: ' + e.message, 'error');
            UIUtils.showToast('Failed to add round: ' + e.message, 'error');
        } finally {
            addRoundBtn.disabled = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Pipeline: Run Recommendations for a Round                          */
    /* ------------------------------------------------------------------ */
    async function runRoundRecommendations(round) {
        const models = getModels();
        const recFile = `round_${round}/recommendations.md`;

        log(`Generating Round ${round} recommendations...`, 'info');
        setBadge(`rec-${round}`, 'running');
        setNodeRunning(`rec-${round}`);

        try {
            const taskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/revise.md', recFile, models
            );
            logLink(`Monitor Recommendations R${round}`, taskId);
            updateNodeSessionLink(`rec-${round}`, recFile, 'RUNNING', taskId);

            await DocOpsUtils.waitForTask(basePath, recFile);
            setBadge(`rec-${round}`, 'done');
            setNodeDone(`rec-${round}`);
            showNodeActions(`rec-${round}`, recFile);
            updateNodeSessionLink(`rec-${round}`, recFile, 'COMPLETED', taskId);
            log(`Round ${round} recommendations ready ✓`, 'success');

            addRoundBtn.disabled = false;
            generatePlanBtn.disabled = false;
            UIUtils.showToast(`Round ${round} recommendations complete!`, 'success');

        } catch (e) {
            setBadge(`rec-${round}`, 'error');
            setNodeError(`rec-${round}`);
            log(`Recommendations error: ${e.message}`, 'error');
            UIUtils.showToast('Recommendations failed: ' + e.message, 'error');
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Pipeline: Generate Final Plan                                       */
    /* ------------------------------------------------------------------ */
    async function generateFinalPlan() {
        generatePlanBtn.disabled = true;
        const models = getModels();

        log('Generating final career plan...', 'info');
        setBadge('plan', 'running');
        setNodeRunning('plan');

        try {
            const taskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/plan.md', 'career_plan.md', models
            );
            logLink('Monitor Final Plan', taskId);
            updateNodeSessionLink('plan', 'career_plan.md', 'RUNNING', taskId);

            await DocOpsUtils.waitForTask(basePath, 'career_plan.md');
            setBadge('plan', 'done');
            setNodeDone('plan');
            showNodeActions('plan', 'career_plan.md');
            updateNodeSessionLink('plan', 'career_plan.md', 'COMPLETED', taskId);
            log('Final career plan ready ✓', 'success');
            UIUtils.showToast('Your career plan is ready!', 'success');
            generateAssetsBtn.disabled = false;


            // Auto-open the plan
            openViewer('career_plan.md');

        } catch (e) {
            setBadge('plan', 'error');
            setNodeError('plan');
            log('Plan generation error: ' + e.message, 'error');
            UIUtils.showToast('Plan generation failed: ' + e.message, 'error');
        } finally {
            generatePlanBtn.disabled = false;
        }
    }
    /* ------------------------------------------------------------------ */
    /*  Pipeline: Generate Search Strategies & ATS Resume                  */
    /* ------------------------------------------------------------------ */
    async function generateSearchAndResume() {
        // Verify career plan exists
        if (!(await FileIOUtils.fileExists(basePath, 'career_plan.md'))) {
            UIUtils.showToast('Generate the career plan first', 'error');
            return;
        }
        generateAssetsBtn.disabled = true;
        const models = getModels();
        try {
            // ── Search Strategies ──────────────────────────────────────
            log('Generating job search strategies...', 'info');
            setBadge('search', 'running');
            setNodeRunning('search');
            const searchTaskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/search.md', 'search-plan.md', models
            );
            logLink('Monitor Search Strategies', searchTaskId);
            updateNodeSessionLink('search', 'search-plan.md', 'RUNNING', searchTaskId);

            // ── ATS Resume ────────────────────────────────────────────
            log('Generating ATS-optimized resume...', 'info');
            setBadge('resume-gen', 'running');
            setNodeRunning('resume-gen');
            const resumeTaskId = await DocOpsUtils.runDocOp(
                sessionId, 'ops/resume.md', 'resume.json', models
            );
            logLink('Monitor ATS Resume', resumeTaskId);
            updateNodeSessionLink('resume-gen', 'resume.json', 'RUNNING', resumeTaskId);

            // Wait for both in parallel
            await Promise.all([
                DocOpsUtils.waitForTask(basePath, 'search-plan.md').then(() => {
                    setBadge('search', 'done');
                    setNodeDone('search');
                    showNodeActions('search', 'search-plan.md');
                    updateNodeSessionLink('search', 'search-plan.md', 'COMPLETED', searchTaskId);
                    log('Search strategies ready ✓', 'success');
                }),
                DocOpsUtils.waitForTask(basePath, 'resume.json').then(() => {
                    setBadge('resume-gen', 'done');
                    setNodeDone('resume-gen');
                    showNodeActions('resume-gen', 'resume.json');
                    updateNodeSessionLink('resume-gen', 'resume.json', 'COMPLETED', resumeTaskId);
                    log('ATS resume ready ✓', 'success');
                }),
            ]);
            UIUtils.showToast('Search strategies & resume generated!', 'success');
        } catch (e) {
            log('Asset generation error: ' + e.message, 'error');
            UIUtils.showToast('Asset generation failed: ' + e.message, 'error');
        } finally {
            generateAssetsBtn.disabled = false;
        }
    }


    /* ------------------------------------------------------------------ */
    /*  Questions Editor                                                    */
    /* ------------------------------------------------------------------ */
    function promptAnswerQuestions(round) {
        const btn = $(`answer-btn-${round}`);
        if (btn) {
            btn.classList.remove('hidden');
            btn.style.animation = 'pulse-badge 1.5s 3';
        }
        UIUtils.showToast(`Round ${round} questions ready — click "Answer" to respond`, 'info');
    }

    async function openQuestionsEditor(round) {
        activeQuestionsFile = `round_${round}/questions.md`;
        $('questions-title').textContent = `Round ${round} — Answer Questions`;
        questionsPanel.classList.remove('hidden');
         // Reset maximized state when opening a fresh round
         questionsPanel.classList.remove('maximized');
         _updateMaximizeIcon(false);

        try {
            const content = await FileIOUtils.readFile(basePath, activeQuestionsFile);
            questionsEditor.value = content || '';
        } catch (e) {
            questionsEditor.value = '';
            log('Could not load questions: ' + e.message, 'error');
        }

        questionsEditor.focus();
    }

    async function saveQuestionsDraft() {
        if (!activeQuestionsFile) return;
        try {
            await FileIOUtils.writeFile(basePath, activeQuestionsFile, questionsEditor.value);
            UIUtils.setStatus('questions-status', '✓ Draft saved', 'success');
            log('Questions draft saved', 'success');
        } catch (e) {
            UIUtils.setStatus('questions-status', '✗ ' + e.message, 'error');
        }
    }

    async function submitQuestionsAndContinue() {
        if (!activeQuestionsFile) return;

        // Determine round from file path
        const match = activeQuestionsFile.match(/round_(\d+)/);
        if (!match) return;
        const round = parseInt(match[1], 10);

        $('submit-questions-btn').disabled = true;

        try {
            // Save answers
            await FileIOUtils.writeFile(basePath, activeQuestionsFile, questionsEditor.value);
            log(`Round ${round} answers saved`, 'success');
             // Always close & un-maximize before running recommendations
             questionsPanel.classList.remove('maximized');
             _updateMaximizeIcon(false);

            questionsPanel.classList.add('hidden');
            activeQuestionsFile = null;

            // Run recommendations for this round
            await runRoundRecommendations(round);

        } catch (e) {
            log('Submit error: ' + e.message, 'error');
            UIUtils.showToast('Failed to submit: ' + e.message, 'error');
        } finally {
            $('submit-questions-btn').disabled = false;
        }
    }

    /* ------------------------------------------------------------------ */
     /*  Questions Panel — Maximize / Restore                               */
     /* ------------------------------------------------------------------ */
     function toggleMaximizeQuestions() {
         const isMax = questionsPanel.classList.toggle('maximized');
         _updateMaximizeIcon(isMax);
         if (isMax) questionsEditor.focus();
     }
     function _updateMaximizeIcon(isMax) {
         const btn  = $('maximize-questions-btn');
         const icon = $('maximize-questions-icon');
         if (!btn || !icon) return;
         if (isMax) {
             btn.title = 'Restore editor';
             icon.innerHTML = `
                 <polyline points="4 14 10 14 10 20"/>
                 <polyline points="20 10 14 10 14 4"/>
                 <line x1="10" y1="14" x2="3" y2="21"/>
                 <line x1="21" y1="3" x2="14" y2="10"/>
             `;
         } else {
             btn.title = 'Maximize editor';
             icon.innerHTML = `
                 <polyline points="15 3 21 3 21 9"/>
                 <polyline points="9 21 3 21 3 15"/>
                 <line x1="21" y1="3" x2="14" y2="10"/>
                 <line x1="3" y1="21" x2="10" y2="14"/>
             `;
         }
     }

    /*  Output Viewer                                                       */
    /* ------------------------------------------------------------------ */
    async function openViewer(filePath) {
        currentViewFile = filePath;
         const isQuestions = filePath.match(/round_(\d+)\/questions\.md$/);
         // Questions files open in the editable panel, not the read-only viewer
         if (isQuestions) {
             const round = parseInt(isQuestions[1], 10);
             openQuestionsEditor(round);
             return;
         }

        const name = filePath.split('/').pop().replace(/\.(md|json)$/, '').replace(/_/g, ' ');
        viewerTitle.textContent = name.charAt(0).toUpperCase() + name.slice(1);

        viewerContent.innerHTML = '<div class="loading-spinner">Loading...</div>';
        $('right-panel').classList.remove('collapsed');

        try {
            const content = await FileIOUtils.readFile(basePath, filePath);
            renderViewer(content, filePath);
        } catch (e) {
            viewerContent.innerHTML = `<div class="viewer-empty"><p>Could not load file: ${e.message}</p></div>`;
        }
    }

    function renderViewer(content, filePath) {
        if (!content) {
            viewerContent.innerHTML = '<div class="viewer-empty"><p>File is empty</p></div>';
            return;
        }
        const isJson = filePath && filePath.endsWith('.json');
        if (isRawView || isJson) {
            let displayContent = content;
            if (isJson && !isRawView) {
                try {
                    displayContent = JSON.stringify(JSON.parse(content), null, 2);
                } catch (_) { /* show as-is if not valid JSON */ }
            }
            viewerContent.innerHTML = `<div class="raw-content">${escapeHtml(displayContent)}</div>`;
        } else {
             viewerContent.innerHTML = `<div class="markdown-body">${renderMarkdown(content)}</div>`;
        }
    }

    function toggleRawView() {
        isRawView = !isRawView;
        $('toggle-raw-btn').style.background = isRawView ? 'var(--primary-light)' : '';
        if (currentViewFile) openViewer(currentViewFile);
    }

    async function copyViewerContent() {
        if (!currentViewFile) return;
        try {
            const content = await FileIOUtils.readFile(basePath, currentViewFile);
            await navigator.clipboard.writeText(content);
            UIUtils.showToast('Copied to clipboard', 'success');
        } catch (e) {
            UIUtils.showToast('Copy failed', 'error');
        }
    }

    function closeViewer() {
        $('right-panel').classList.add('collapsed');
        currentViewFile = null;
    }
     function toggleExpandViewer() {
         const panel = $('right-panel');
         const btn   = $('expand-viewer-btn');
         const icon  = $('expand-icon');
         const isExpanded = panel.classList.toggle('expanded');
         if (isExpanded) {
             btn.title = 'Collapse viewer';
             btn.childNodes[1].textContent = ' Collapse';
             icon.innerHTML = `
                 <polyline points="4 14 10 14 10 20"/>
                 <polyline points="20 10 14 10 14 4"/>
                 <line x1="10" y1="14" x2="3" y2="21"/>
                 <line x1="21" y1="3" x2="14" y2="10"/>
             `;
         } else {
             btn.title = 'Expand viewer';
             btn.childNodes[1].textContent = ' Expand';
             icon.innerHTML = `
                 <polyline points="15 3 21 3 21 9"/>
                 <polyline points="9 21 3 21 3 15"/>
                 <line x1="21" y1="3" x2="14" y2="10"/>
                 <line x1="3" y1="21" x2="10" y2="14"/>
             `;
         }
     }

    /* ------------------------------------------------------------------ */
    /*  Round Stage Builder                                                 */
    /* ------------------------------------------------------------------ */
    function ensureRoundStage(round) {
        if ($(`round-stage-${round}`)) return; // already exists

        const stage = document.createElement('div');
        stage.className = 'round-stage';
        stage.id = `round-stage-${round}`;
        stage.innerHTML = `
            <div class="round-stage-header">
                <div class="round-stage-label">Round ${round}</div>
                <button class="btn btn-secondary btn-xs round-answer-btn hidden" 
                        id="answer-btn-${round}" 
                        data-round="${round}">
                    ✏️ Answer Questions
                </button>
            </div>
            <div class="pipeline-nodes">
                ${makeNodeHtml(`q-${round}`, '❓', `Questions R${round}`, 'Clarifying questions', `round_${round}/questions.md`)}
                <div class="pipeline-arrow">→</div>
                ${makeNodeHtml(`rec-${round}`, '📝', `Recommendations R${round}`, 'Refined career advice', `round_${round}/recommendations.md`)}
            </div>
        `;
        roundsContainer.appendChild(stage);
    }

    function makeNodeHtml(nodeId, icon, title, subtitle, file) {
        return `
            <div class="pipeline-node" id="node-${nodeId}">
                <div class="node-icon">${icon}</div>
                <div class="node-info">
                    <div class="node-title">${title}</div>
                    <div class="node-subtitle">${subtitle}</div>
                </div>
                <div class="node-badge" id="badge-${nodeId}">
                    <span class="badge badge-idle">idle</span>
                </div>
                <div class="node-actions">
                    <button class="node-view-btn hidden" id="view-${nodeId}" data-file="${file}" title="View output">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                    </button>
                </div>
                <div id="${nodeId}-links" class="session-links" aria-label="${title} session links"></div>
            </div>
        `;
    }

    /* ------------------------------------------------------------------ */
    /*  Status Poller Callback                                              */
    /* ------------------------------------------------------------------ */
    function onTaskUpdate(target, taskInfo) {
        // Map output files to node IDs
        const fileToNode = {
            'ideas.md':        'ideas',
            'round_1/review.md': 'review1',
            'career_plan.md':  'plan',
            'search-plan.md':       'search',
            'resume.json':     'resume-gen',
        };

        // Dynamic round files
        const roundQMatch  = target.match(/^round_(\d+)\/questions\.md$/);
        const roundRecMatch = target.match(/^round_(\d+)\/recommendations\.md$/);
        if (roundQMatch)   fileToNode[target] = `q-${roundQMatch[1]}`;
        if (roundRecMatch) fileToNode[target] = `rec-${roundRecMatch[1]}`;

        const nodeId = fileToNode[target];
        if (!nodeId) return;
        // Ensure round stage DOM exists for dynamic rounds
        if (roundQMatch) ensureRoundStage(parseInt(roundQMatch[1], 10));
        if (roundRecMatch) ensureRoundStage(parseInt(roundRecMatch[1], 10));
        // Store session ID when we first learn it
        if (taskInfo.taskId || taskInfo.sessionId) {
            const sid = taskInfo.taskId || taskInfo.sessionId;
            if (!taskSessionIds[target]) taskSessionIds[target] = sid;
        }
        // Update session links container
        updateNodeSessionLink(nodeId, target, taskInfo.status, taskInfo.taskId || taskInfo.sessionId);


        if (taskInfo.status === 'RUNNING') {
            setBadge(nodeId, 'running');
            setNodeRunning(nodeId);
        } else if (taskInfo.status === 'COMPLETED') {
            setBadge(nodeId, 'done');
            setNodeDone(nodeId);
            showNodeActions(nodeId, target);
        } else if (taskInfo.status === 'FAILED') {
            setBadge(nodeId, 'error');
            setNodeError(nodeId);
        }

    }

    /* ------------------------------------------------------------------ */
    /*  Node State Helpers                                                  */
    /* ------------------------------------------------------------------ */
    function setBadge(nodeId, state) {
        const container = $(`badge-${nodeId}`);
        if (!container) return;
        const labels = { idle: 'idle', running: 'running…', done: 'done', error: 'error', waiting: 'waiting' };
        container.innerHTML = `<span class="badge badge-${state}">${labels[state] || state}</span>`;
    }

    function setNodeRunning(nodeId) { setNodeClass(nodeId, 'running'); }
    function setNodeDone(nodeId)    { setNodeClass(nodeId, 'done'); }
    function setNodeError(nodeId)   { setNodeClass(nodeId, 'error'); }

    function setNodeClass(nodeId, cls) {
        const node = $(`node-${nodeId}`);
        if (!node) return;
        node.classList.remove('running', 'done', 'error');
        node.classList.add(cls);
    }

    function showNodeActions(nodeId, file) {
        const viewBtn = $(`view-${nodeId}`);
        if (viewBtn) {
            viewBtn.classList.remove('hidden');
            viewBtn.dataset.file = file;
        }
    }

    /**
     * Render session links into a container, following the resume-customizer pattern.
     * Shows a "Monitor live" link while running, or "View session" when complete/failed.
     */
    function renderSessionLinks(linksId, target, status, taskSessionId) {
        const container = $(linksId);
        if (!container) return;

        const sid = taskSessionId || taskSessionIds[target];
        if (!sid) return;

        const proxyUrl = SessionUtils.getProxyUrl(sid);
        if (!proxyUrl) return;

        // Check if we already have a link for this target
        const existing = container.querySelector(`a[data-target="${target}"]`);
        if (existing) {
            // Update label and href based on current status / session ID
            existing.textContent = status === 'RUNNING' ? '⟳ Monitor live' : '📋 View session';
            existing.href = proxyUrl;
            existing.title = `Session ${sid}`;
            return;
        }

        const label = status === 'RUNNING' ? '⟳ Monitor live' : '📋 View session';
        const a = document.createElement('a');
        a.href = proxyUrl;
        a.textContent = label;
        a.title = `Session ${sid}`;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        a.dataset.target = target;
        container.appendChild(a);
    }

    /** Convenience: render a session link for a node by its nodeId and associated file. */
    function updateNodeSessionLink(nodeId, file, status, taskId) {
        if (taskId) taskSessionIds[file] = taskId;
        const ui = getTargetUI(file);
        if (ui) {
            renderSessionLinks(ui.linksId, file, status, taskId);
        }
    }


    /* ------------------------------------------------------------------ */
    /*  Activity Log                                                        */
    /* ------------------------------------------------------------------ */
    function log(msg, type = '') {
        const entry = document.createElement('div');
        entry.className = 'log-entry';
        const now = new Date();
        const time = now.toTimeString().slice(0, 8);
         // Allow pre-escaped HTML (for links) — callers using logLink pass safe HTML
         const isHtml = msg.includes('<a ');
         entry.innerHTML = `<span class="log-time">${time}</span><span class="log-msg ${type}">${isHtml ? msg : escapeHtml(msg)}</span>`;
        activityLog.appendChild(entry);
        activityLog.scrollTop = activityLog.scrollHeight;
    }

    function logLink(label, taskId) {
        if (!taskId) return;
        const entry = document.createElement('div');
        entry.className = 'log-entry';
        const now = new Date();
        const time = now.toTimeString().slice(0, 8);
        const url = SessionUtils.getProxyUrl(taskId);
        entry.innerHTML = `<span class="log-time">${time}</span><span class="log-msg info"><a href="${url}" target="_blank">🔗 ${escapeHtml(label)}</a></span>`;
        activityLog.appendChild(entry);
        activityLog.scrollTop = activityLog.scrollHeight;
    }

    /* ------------------------------------------------------------------ */
    /*  Usage Modal                                                         */
    /* ------------------------------------------------------------------ */
    async function showUsageModal() {
        $('usage-modal').classList.remove('hidden');
        $('usage-content').innerHTML = '<div class="loading-spinner">Loading usage data...</div>';

        try {
         const usage = await UsageUtils.fetchUsageData(sessionId);
         $('usage-content').innerHTML = UsageUtils.createUsageTableHtml
             ? UsageUtils.createUsageTableHtml(
                   (usage && usage.models) ? usage.models : [],
                   (usage && usage.totals) ? usage.totals : null
               )
             : `<pre>${JSON.stringify(usage, null, 2)}</pre>`;
        } catch (e) {
            $('usage-content').innerHTML = `<p style="color:var(--danger)">Could not load usage: ${e.message}</p>`;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Model Helpers                                                       */
    /* ------------------------------------------------------------------ */
    function getModels() {
        return {
            smartModel: $('smart-model').value || undefined,
            fastModel:  $('fast-model').value  || undefined,
             imageModel: ($('image-model') && $('image-model').value) || $('smart-model').value || undefined,
           imageModel: $('smart-model').value  || undefined,
        };
    }

    function saveModelSelections() {
        ModelUtils.saveModelSelections('career-advisor', {
            smartModel: $('smart-model').value,
            fastModel:  $('fast-model').value,
             imageModel: ($('image-model') && $('image-model').value) || undefined,
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Utilities                                                           */
    /* ------------------------------------------------------------------ */
    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    /* ------------------------------------------------------------------ */
    /*  Boot                                                                */
    /* ------------------------------------------------------------------ */
    init().catch(e => console.error('Career Advisor init error:', e));

})();