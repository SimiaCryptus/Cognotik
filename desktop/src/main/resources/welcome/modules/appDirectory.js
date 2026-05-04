// ===== App Directory =====
    let appDirectory = [];

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
        appDirectory.forEach(app => {
            const card = document.createElement('a');
            card.href = '#';
            card.className = 'app-card' + (app.cardClass ? ' ' + app.cardClass : '');
            card.id = app.id;
            let badgeHtml = '';
            if (app.badge) {
                badgeHtml = `<div class="app-card-badge${app.badgeClass ? ' ' + app.badgeClass : ''}">${app.badge}</div>`;
            }
            card.innerHTML = `
                <div class="app-card-icon">${app.icon}</div>
                <div class="app-card-body">
                    <h3>${app.name}</h3>
                    <p>${app.description}</p>
                </div>
                ${badgeHtml}
            `;
            grid.appendChild(card);
        });
    }

    function setupAppCards(handlers) {
        appDirectory.forEach(app => {
            const element = document.getElementById(app.id);
            if (!element) return;

            if (app.type === 'chat') {
                element.addEventListener('click', function (e) {
                    e.preventDefault();
                    handlers.onChat();
                });
            } else if (app.type === 'docops') {
                element.addEventListener('click', function (e) {
                    e.preventDefault();
                    const docopsSessionId = Utils.generateSessionId();
                    console.log(`[setupAppCards] Launching ${app.id} with session:`, docopsSessionId);
                    window.location.href = `${app.path}/fileIndex/${docopsSessionId}/app.html`;
                });
            } else if (app.type === 'pipeline') {
                element.addEventListener('click', function (e) {
                    e.preventDefault();
                    handlers.onPipeline();
                });
            }
        });
    }