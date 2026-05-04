// ===== App Directory =====
let appDirectory = [];
let appLaunchHandlers = null;
let appSearchQuery = '';

async function loadAppDirectory() {
    console.log('[loadAppDirectory] Loading app directory...');
    try {
        const response = await fetch('/appDirectory');
        if (response.ok) {
            appDirectory = await response.json();
            console.log('[loadAppDirectory] Loaded', appDirectory.length, 'apps');
        } else {
            console.error('[loadAppDirectory] Failed to load apps.json:', response.status);
            appDirectory = [];
        }
    } catch (error) {
        console.error('[loadAppDirectory] Error:', error);
        appDirectory = [];
    }
}

function renderAppGrid() {
    const grid = document.getElementById('app-grid');
    if (!grid) return;
    grid.innerHTML = '';
     const filteredApps = filterApps(appDirectory, appSearchQuery);
     filteredApps.forEach(app => {
        const card = document.createElement('a');
        card.href = '#';
        card.className = 'app-card' + (app.cardClass ? ' ' + app.cardClass : '');
        card.id = app.id;
        let badgeHtml = '';
        if (app.badge) {
            badgeHtml = `<div class="app-card-badge${app.badgeClass ? ' ' + app.badgeClass : ''}">${app.badge}</div>`;
        }
        const hasReadme = !!app.readme;
        const launchBtnHtml = `<button class="button app-card-launch-btn" data-app-id="${app.id}">Launch Session</button>`;
        const readmeHintHtml = hasReadme
            ? `<div class="app-card-readme-hint">Click card for details</div>`
            : '';
        card.innerHTML = `
            <div class="app-card-icon">${app.icon}</div>
            <div class="app-card-body">
                <h3>${app.name}</h3>
                <p>${app.description}</p>
                <div class="app-card-actions">
                    ${launchBtnHtml}
                    ${readmeHintHtml}
                </div>
            </div>
            ${badgeHtml}
        `;
        grid.appendChild(card);
    });
     updateNoResultsMessage(filteredApps.length);
    ensureReadmeModal();
}
function filterApps(apps, query) {
     const q = (query || '').trim().toLowerCase();
     if (!q) return apps;
     return apps.filter(app => {
         const fields = [
             app.name,
             app.description,
             app.badge,
             app.id,
             app.type,
             app.readme
         ];
         return fields.some(f => f && String(f).toLowerCase().includes(q));
     });
}
function updateNoResultsMessage(count) {
     const noResults = document.getElementById('app-search-no-results');
     if (!noResults) return;
     noResults.style.display = (count === 0 && appSearchQuery.trim()) ? 'block' : 'none';
}
function setupAppSearch() {
     const input = document.getElementById('app-search-input');
     const clearBtn = document.getElementById('app-search-clear');
     if (!input) return;
     const handleInput = () => {
         appSearchQuery = input.value;
         if (clearBtn) {
             clearBtn.style.display = appSearchQuery ? 'inline-block' : 'none';
         }
         renderAppGrid();
         // Re-attach card handlers since DOM was rebuilt
         if (appLaunchHandlers) {
             setupAppCards(appLaunchHandlers);
         }
     };
     input.addEventListener('input', handleInput);
     input.addEventListener('keydown', (e) => {
         if (e.key === 'Escape') {
             input.value = '';
             handleInput();
         }
     });
     if (clearBtn) {
         clearBtn.addEventListener('click', () => {
             input.value = '';
             input.focus();
             handleInput();
         });
     }
}


function ensureReadmeModal() {
    if (document.getElementById('app-readme-modal')) return;
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.id = 'app-readme-modal';
    modal.innerHTML = `
        <div class="modal-content modal-content-wide app-readme-modal-content">
            <span class="close" id="close-app-readme-modal">&times;</span>
            <div class="app-readme-header">
                <div class="app-readme-icon" id="app-readme-icon"></div>
                <div class="app-readme-title-block">
                    <h2 id="app-readme-title"></h2>
                    <p id="app-readme-description" class="app-readme-description"></p>
                </div>
            </div>
            <div class="app-readme-body" id="app-readme-body"></div>
            <div class="button-group app-readme-actions">
                <button class="button secondary" id="app-readme-close-btn">Close</button>
                <button class="button" id="app-readme-launch-btn">Launch Session</button>
            </div>
        </div>
    `;
    document.body.appendChild(modal);

    const closeModal = () => { modal.style.display = 'none'; };
    modal.querySelector('#close-app-readme-modal').addEventListener('click', closeModal);
    modal.querySelector('#app-readme-close-btn').addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });
    modal.querySelector('#app-readme-launch-btn').addEventListener('click', () => {
        const appId = modal.dataset.currentAppId;
        closeModal();
        if (appId) launchApp(appId);
    });
}

function showAppReadme(app) {
    ensureReadmeModal();
    const modal = document.getElementById('app-readme-modal');
    modal.dataset.currentAppId = app.id;
    document.getElementById('app-readme-icon').textContent = app.icon || '';
    document.getElementById('app-readme-title').textContent = app.name || '';
    document.getElementById('app-readme-description').textContent = app.description || '';
    const body = document.getElementById('app-readme-body');
    const readmeContent = app.readme || '_No additional details available._';
    try {
        body.innerHTML = (typeof marked !== 'undefined' && marked.parse)
            ? marked.parse(readmeContent)
            : readmeContent;
    } catch (e) {
        console.error('[showAppReadme] Error rendering markdown:', e);
        body.textContent = readmeContent;
    }
    modal.style.display = 'block';
}

function launchApp(appId) {
    const app = appDirectory.find(a => a.id === appId);
    if (!app || !appLaunchHandlers) return;
    if (app.type === 'chat') {
        appLaunchHandlers.onChat();
    } else if (app.type === 'docops') {
        const docopsSessionId = Utils.generateSessionId();
        console.log(`[launchApp] Launching ${app.id} with session:`, docopsSessionId);
        window.location.href = `${app.path}/fileIndex/${docopsSessionId}/app.html`;
    } else if (app.type === 'pipeline') {
        appLaunchHandlers.onPipeline();
    }
}

function setupAppCards(handlers) {
    appLaunchHandlers = handlers;
     const filteredApps = filterApps(appDirectory, appSearchQuery);
     filteredApps.forEach(app => {
        const element = document.getElementById(app.id);
        if (!element) return;

        const launchBtn = element.querySelector('.app-card-launch-btn');
        if (launchBtn) {
            launchBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                launchApp(app.id);
            });
        }

        element.addEventListener('click', function(e) {
            e.preventDefault();
            if (e.target.closest('.app-card-launch-btn')) return;
            if (app.readme) {
                showAppReadme(app);
            } else {
                launchApp(app.id);
            }
        });
    });
}