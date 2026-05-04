// ===== Auth Banner =====
    // Detects available authorization chains and provides one-click access
    // to auto-start and auto-open the authorization stage.

    const authBannerState = {
        availableChains: [],
        activeSessionId: null,
        activeChainName: null,
        pollingTimer: null,
        autoOpenedWindow: null
    };

    function setupAuthBanner() {
        const actionBtn = document.getElementById('auth-banner-action');
        if (actionBtn) {
            actionBtn.addEventListener('click', onAuthBannerAction);
        }
    }

    function updateAuthBanner() {
        const banner = document.getElementById('auth-banner');
        if (!banner) return;
        fetch('/pluginManager/?action=authChains', { headers: { 'Accept': 'application/json' } })
            .then(r => {
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                return r.json();
            })
            .then(data => {
                authBannerState.availableChains = data || [];
                renderAuthBanner();
            })
            .catch(e => {
                // Silently hide banner on error (e.g., endpoint unavailable)
                console.debug('[authBanner] Could not fetch auth chains:', e.message);
                banner.style.display = 'none';
            });
    }

    function renderAuthBanner() {
        const banner = document.getElementById('auth-banner');
        const titleEl = document.getElementById('auth-banner-title');
        const messageEl = document.getElementById('auth-banner-message');
        const actionBtn = document.getElementById('auth-banner-action');
        if (!banner) return;
        const chains = authBannerState.availableChains || [];
        if (chains.length === 0) {
            banner.style.display = 'none';
            return;
        }
        banner.style.display = 'block';
        if (chains.length === 1) {
            const chainName = chains[0].name;
            if (titleEl) titleEl.textContent = `Authorization required: ${chainName}`;
            if (messageEl) messageEl.textContent = 'A plugin requires authorization to function. Click to start the authorization flow.';
            if (actionBtn) actionBtn.textContent = `Authorize "${chainName}" →`;
        } else {
            if (titleEl) titleEl.textContent = `${chains.length} pending authorizations`;
            if (messageEl) {
                const names = chains.map(c => c.name).join(', ');
                messageEl.textContent = `Plugins requiring authorization: ${names}`;
            }
            if (actionBtn) actionBtn.textContent = 'Review Authorizations →';
        }
    }

    function onAuthBannerAction() {
        const chains = authBannerState.availableChains || [];
        if (chains.length === 0) return;
        if (chains.length === 1) {
            // Auto-start and auto-open the single authorization chain
            autoStartAndOpenAuth(chains[0].name);
        } else {
            // Multiple chains: open the plugin manager on the auth tab
            openPluginManagerAuthTab();
        }
    }

    function autoStartAndOpenAuth(chainName) {
        const actionBtn = document.getElementById('auth-banner-action');
        if (actionBtn) {
            actionBtn.disabled = true;
            actionBtn.textContent = 'Starting...';
        }
        fetch('/pluginManager/', {
            method: 'POST',
            body: new URLSearchParams({ action: 'startAuth', chain: chainName }),
            headers: { 'Accept': 'application/json' }
        })
            .then(r => {
                if (!r.ok) {
                    return r.text().then(text => {
                        try { return JSON.parse(text); }
                        catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
                    });
                }
                return r.json();
            })
            .then(data => {
                if (actionBtn) {
                    actionBtn.disabled = false;
                    renderAuthBanner();
                }
                if (data.success && data.sessionId && (data.status === 'IN_PROGRESS' || data.status === 'in_progress')) {
                    authBannerState.activeSessionId = data.sessionId;
                    authBannerState.activeChainName = chainName;
                    // Auto-open the authorization stage in a new window
                    const authUrl = `/pluginManager?action=authStep&sessionId=${encodeURIComponent(data.sessionId)}`;
                    authBannerState.autoOpenedWindow = window.open(
                        authUrl,
                        'cognotik-auth-' + data.sessionId,
                        'width=700,height=700,scrollbars=yes,resizable=yes'
                    );
                    if (!authBannerState.autoOpenedWindow) {
                        // Popup blocked: fall back to showing notification with manual link
                        if (typeof showNotification === 'function') {
                            showNotification(
                                `Popup blocked. Please open the authorization page manually for "${chainName}".`,
                                'error'
                            );
                        }
                        // Open the plugin manager so the user can use the manual flow
                        openPluginManagerAuthTab();
                    } else {
                        if (typeof showNotification === 'function') {
                            showNotification(`Authorization started for "${chainName}". Complete the flow in the new window.`, 'info');
                        }
                        startAuthBannerPolling();
                    }
                } else if (data.success && (data.status === 'COMPLETED' || data.status === 'completed')) {
                    if (typeof showNotification === 'function') {
                        showNotification(`Authorization completed for "${chainName}".`, 'success');
                    }
                    updateAuthBanner();
                } else if (data.status === 'FAILED' || data.status === 'failed') {
                    if (typeof showNotification === 'function') {
                        showNotification(`Authorization failed for "${chainName}": ${data.failureReason || 'Unknown reason'}`, 'error');
                    }
                } else {
                    if (typeof showNotification === 'function') {
                        showNotification('Error starting authorization: ' + (data.error || 'Unknown error'), 'error');
                    }
                }
            })
            .catch(e => {
                if (actionBtn) {
                    actionBtn.disabled = false;
                    renderAuthBanner();
                }
                if (typeof showNotification === 'function') {
                    showNotification('Failed to start authorization: ' + e.message, 'error');
                }
            });
    }

    function openPluginManagerAuthTab() {
        const modal = document.getElementById('plugin-manager-modal');
        if (!modal) return;
        modal.style.display = 'block';
        // Refresh and switch to the auth chains tab
        if (typeof pluginManagerRefreshAuthChains === 'function') {
            pluginManagerRefreshAuthChains();
        }
        if (typeof switchPluginTab === 'function') {
            switchPluginTab('auth-chains');
        }
    }

    function startAuthBannerPolling() {
        stopAuthBannerPolling();
        authBannerState.pollingTimer = setInterval(() => {
            if (!authBannerState.activeSessionId) {
                stopAuthBannerPolling();
                return;
            }
            fetch(`/pluginManager/?action=authStatus&sessionId=${encodeURIComponent(authBannerState.activeSessionId)}`, {
                headers: { 'Accept': 'application/json' }
            })
                .then(r => {
                    if (!r.ok) throw new Error(`HTTP ${r.status}`);
                    return r.json();
                })
                .then(data => {
                    if (data.isComplete) {
                        const chainName = authBannerState.activeChainName;
                        stopAuthBannerPolling();
                        // Close the auto-opened window if still open
                        if (authBannerState.autoOpenedWindow && !authBannerState.autoOpenedWindow.closed) {
                            try { authBannerState.autoOpenedWindow.close(); } catch (e) { /* ignore */ }
                        }
                        authBannerState.autoOpenedWindow = null;
                        authBannerState.activeSessionId = null;
                        authBannerState.activeChainName = null;
                        if (data.status === 'COMPLETED') {
                            if (typeof showNotification === 'function') {
                                showNotification(`Authorization "${chainName}" completed successfully!`, 'success');
                            }
                        } else {
                            if (typeof showNotification === 'function') {
                                showNotification(`Authorization "${chainName}" failed: ${data.failureReason || 'Unknown reason'}`, 'error');
                            }
                        }
                        // Refresh banner state
                        updateAuthBanner();
                    }
                })
                .catch(e => {
                    console.debug('[authBanner] Polling error:', e.message);
                });
        }, 3000);
    }

    function stopAuthBannerPolling() {
        if (authBannerState.pollingTimer) {
            clearInterval(authBannerState.pollingTimer);
            authBannerState.pollingTimer = null;
        }
    }