/* Homepage controller for the CLI file server.
   *   /userSettings   - API keys, base URLs, model selection (UserSettingsServlet)
   *   /apiProviders   - provider + model catalog            (ApiProviderServlet)
   * The page holds no state the server does not own; every action round-trips.
   */
(function () {
    'use strict';

    var USER_SETTINGS = '/userSettings/';
    var PROVIDERS = '/apiProviders/';

    var state = {
        settings: {},        /* raw /userSettings payload, so we can round-trip unknown fields */
        rows: [],            /* provider key editor rows */
        knownProviders: [],  /* provider ids this build understands */
        catalog: {}          /* provider -> [model id] */
    };

    function $(id) {
        return document.getElementById(id);
    }

    function esc(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
            return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c];
        });
    }

    function toast(message, kind) {
        var el = $('toast');
        el.textContent = message;
        el.className = 'toast' + (kind ? ' ' + kind : '');
        el.hidden = false;
        clearTimeout(el._timer);
        el._timer = setTimeout(function () {
            el.hidden = true;
        }, 4000);
    }

    function busy(button, running) {
        if (button) button.disabled = !!running;
    }

    function json(response) {
        return response.text().then(function (text) {
            var parsed;
            try {
                parsed = text ? JSON.parse(text) : {};
            } catch (e) {
                throw new Error('Server returned ' + response.status + ': ' + text.substring(0, 160));
            }
            if (!response.ok && !parsed.error) {
                throw new Error('Server returned ' + response.status);
            }
            return parsed;
        });
    }

    /* UserSettingsServlet answers with JSON, HTML or nothing depending on the build. */
    function postForm(url, params) {
        return fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                Accept: 'application/json'
            },
            body: params.toString()
        }).then(function (response) {
            return response.text().then(function (text) {
                var parsed = {};
                try {
                    parsed = text ? JSON.parse(text) : {};
                } catch (e) { /* not JSON: fine */
                }
                if (!response.ok) throw new Error(parsed.error || ('Server returned ' + response.status));
                if (parsed.error) throw new Error(parsed.error);
                return parsed;
            });
        });
    }

    /* ------------------------------------------------------------------ docs */

    function renderSnippet() {
        var base = window.location.origin;
        $('api-snippet').textContent =
            'curl "' + base + '/userSettings/?format=json"\n' +
            'curl "' + base + '/apiProviders/?format=json"\n' +
            'curl -X POST ' + base + '/userSettings/ \\\n' +
            '  --data-urlencode action=save \\\n' +
            '  --data-urlencode \'settings={"apis":[{"provider":"OpenAI","key":"sk-…"}],' +
            '"smartModel":"gpt-4o","fastModel":"gpt-4o-mini","collectSessionData":false}\'\n';
    }

    /* ---------------------------------------------------------------- models */

    function modelIds() {
        var ids = {};
        Object.keys(state.catalog).forEach(function (provider) {
            (state.catalog[provider] || []).forEach(function (id) {
                ids[id] = true;
            });
        });
        [state.settings.smartModel, state.settings.fastModel].forEach(function (id) {
            if (id) ids[id] = true;
        });
        return Object.keys(ids).sort();
    }

    function renderModels() {
        var available = modelIds();
        $('model-list').innerHTML = available.map(function (id) {
            return '<option value="' + esc(id) + '"></option>';
        }).join('');
        if (document.activeElement !== $('smart-model')) {
            $('smart-model').value = state.settings.smartModel || '';
        }
        if (document.activeElement !== $('fast-model')) {
            $('fast-model').value = state.settings.fastModel || '';
        }
        var providers = Object.keys(state.catalog).filter(function (p) {
            return (state.catalog[p] || []).length > 0;
        });
        $('model-summary').textContent = available.length
            ? available.length + ' model' + (available.length === 1 ? '' : 's') +
            ' from ' + providers.length + ' provider' + (providers.length === 1 ? '' : 's')
            : '';
        $('models-empty').hidden = available.length > 0;
        renderCatalog();
    }

    function renderCatalog() {
        var host = $('model-catalog');
        var groups = Object.keys(state.catalog).filter(function (p) {
            return (state.catalog[p] || []).length > 0;
        }).sort();
        if (!groups.length) {
            host.innerHTML = '';
            host.hidden = true;
            $('catalog-hint').hidden = true;
            return;
        }
        host.hidden = false;
        $('catalog-hint').hidden = false;
        host.innerHTML = groups.map(function (provider) {
            var chips = state.catalog[provider].map(function (id) {
                return '<button type="button" class="chip" data-model="' + esc(id) + '">' +
                    esc(id) + '</button>';
            }).join('');
            return '<div class="catalog-group"><h4>' + esc(provider) + '</h4>' +
                '<div class="chips">' + chips + '</div></div>';
        }).join('');

        Array.prototype.forEach.call(host.querySelectorAll('.chip'), function (chip) {
            chip.addEventListener('click', function (event) {
                var fast = event.shiftKey || event.altKey;
                var field = fast ? $('fast-model') : $('smart-model');
                field.value = chip.getAttribute('data-model');
                field.focus();
                toast('Staged as ' + (fast ? 'fast' : 'smart') + ' model — press "Save models"');
            });
        });
    }

    /* ------------------------------------------------------- providers / keys */

    function nameOf(entry) {
        if (!entry) return '';
        return typeof entry === 'string' ? entry : (entry.name || entry.id || String(entry));
    }

    function providerOptions() {
        var set = {};
        state.knownProviders.forEach(function (p) {
            if (p) set[p] = true;
        });
        Object.keys(state.catalog).forEach(function (p) {
            set[p] = true;
        });
        state.rows.forEach(function (r) {
            if (r.provider) set[r.provider] = true;
        });
        return Object.keys(set).sort();
    }

    function renderProviderList() {
        $('provider-list').innerHTML = providerOptions().map(function (p) {
            return '<option value="' + esc(p) + '"></option>';
        }).join('');
    }

    function applyProviderQuirks(row, wrap) {
        var id = (row.provider || '').toLowerCase();
        var key = wrap.querySelector('.row-key');
        if (id === 'ollama') {
            if (!row.key) {
                row.key = '-';
                key.value = '-';
            }
            key.disabled = true;
            key.title = 'Ollama needs no key; "-" is sent for you.';
        } else {
            key.disabled = false;
            key.title = '';
        }
    }

    function rowElement(row) {
        var wrap = document.createElement('div');
        wrap.className = 'key-row';
        wrap.innerHTML =
            '<div class="field compact">' +
            '<label>Provider</label>' +
            '<input class="row-provider" list="provider-list" autocomplete="off" placeholder="OpenAI"' +
            ' value="' + esc(row.provider) + '"></div>' +
            '<div class="field compact grow">' +
            '<label>API key</label>' +
            '<div class="key-input">' +
            '<input class="row-key" type="password" autocomplete="off" placeholder="sk-…"' +
            ' value="' + esc(row.key) + '">' +
            '<button type="button" class="button ghost row-reveal" title="Show / hide">👁</button>' +
            '</div></div>' +
            '<div class="field compact grow">' +
            '<label>Base URL <small>(optional)</small></label>' +
            '<input class="row-base" autocomplete="off" placeholder="https://api.openai.com/v1"' +
            ' value="' + esc(row.baseUrl) + '"></div>' +
            '<button type="button" class="button danger row-remove" title="Remove provider">✕</button>';

        var provider = wrap.querySelector('.row-provider');
        var key = wrap.querySelector('.row-key');
        var base = wrap.querySelector('.row-base');

        provider.addEventListener('input', function () {
            row.provider = provider.value;
            applyProviderQuirks(row, wrap);
        });
        key.addEventListener('input', function () {
            row.key = key.value;
        });
        base.addEventListener('input', function () {
            row.baseUrl = base.value;
        });
        wrap.querySelector('.row-reveal').addEventListener('click', function () {
            key.type = key.type === 'password' ? 'text' : 'password';
        });
        wrap.querySelector('.row-remove').addEventListener('click', function () {
            var index = state.rows.indexOf(row);
            if (index >= 0) state.rows.splice(index, 1);
            renderRows();
        });

        applyProviderQuirks(row, wrap);
        return wrap;
    }

    function renderRows() {
        var host = $('key-rows');
        host.innerHTML = '';
        state.rows.forEach(function (row) {
            host.appendChild(rowElement(row));
        });
        $('providers-empty').hidden = state.rows.length > 0;
        $('providers-summary').textContent = state.rows.length
            ? state.rows.length + ' provider' + (state.rows.length === 1 ? '' : 's') + ' configured'
            : '';
        renderProviderList();
    }

    /* ------------------------------------------------------------- persistence */

    /* One settings object owns keys *and* models, so every save sends the whole thing. */
    function collectApis() {
        var apis = [];
        var seen = {};
        var problem = null;
        state.rows.forEach(function (row) {
            var provider = (row.provider || '').trim();
            if (!provider) return;
            if (seen[provider]) {
                problem = problem || ('Duplicate provider: ' + provider);
                return;
            }
            seen[provider] = true;
            var key = (row.key || '').trim();
            if (provider.toLowerCase() === 'ollama' && !key) key = '-';
            if (!key) {
                problem = problem || ('Missing API key for ' + provider);
                return;
            }
            apis.push({provider: provider, key: key, baseUrl: (row.baseUrl || '').trim()});
        });
        return {apis: apis, problem: problem};
    }

    function buildPayload() {
        var collected = collectApis();
        if (collected.problem) return {error: collected.problem};
        var payload = {};
        /* keep passwordHash, internalToken and anything else this build stores */
        Object.keys(state.settings || {}).forEach(function (k) {
            payload[k] = state.settings[k];
        });
        payload.apis = collected.apis;
        payload.collectSessionData = !!$('collect-session-data').checked;
        payload.smartModel = $('smart-model').value.trim() || null;
        payload.fastModel = $('fast-model').value.trim() || null;
        return {payload: payload};
    }

    function saveSettings(button, okMessage) {
        var built = buildPayload();
        if (built.error) {
            toast(built.error, 'error');
            return Promise.resolve(false);
        }
        var params = new URLSearchParams();
        params.append('action', 'save');
        params.append('settings', JSON.stringify(built.payload));

        busy(button, true);
        return postForm(USER_SETTINGS, params)
            .then(function () {
                state.settings = built.payload;
                toast(okMessage, 'ok');
                return true;
            })
            .catch(function (e) {
                toast('Could not save: ' + e.message, 'error');
                return false;
            })
            .then(function (ok) {
                busy(button, false);
                return ok;
            });
    }

    function saveModels() {
        if (!$('smart-model').value.trim() && !$('fast-model').value.trim()) {
            toast('Enter at least one model id', 'error');
            return;
        }
        saveSettings($('save-models'), 'Models updated').then(function (ok) {
            if (ok) renderModels();
        });
    }

    function saveKeys() {
        saveSettings($('save-keys'), 'API settings saved').then(function (ok) {
            /* New keys can expose new models: re-read the catalog. */
            if (ok) return loadUserSettings().then(loadProviders);
        });
    }

    /* ----------------------------------------------------------------- loading */

    function loadProviders() {
        return fetch(PROVIDERS + '?format=json', {headers: {Accept: 'application/json'}})
            .then(json)
            .then(function (data) {
                state.knownProviders = (data.availableProviders || []).map(nameOf);
                state.catalog = {};
                (data.configuredProviders || []).forEach(function (provider) {
                    var name = nameOf(provider);
                    if (!name) return;
                    state.catalog[name] = (provider.models || []).map(nameOf).filter(Boolean).sort();
                });
                renderProviderList();
                renderModels();
            })
            .catch(function (e) {
                /* Not fatal: the model inputs still accept hand-typed ids. */
                console.warn('[home] provider catalog unavailable:', e.message);
            });
    }

    function loadUserSettings() {
        return fetch(USER_SETTINGS + '?format=json', {headers: {Accept: 'application/json'}})
            .then(json)
            .then(function (data) {
                state.settings = data || {};
                state.rows = (state.settings.apis || []).map(function (api) {
                    return {
                        provider: api.provider || '',
                        key: api.key || '',
                        baseUrl: api.baseUrl || ''
                    };
                });
                var collect = state.settings.collectSessionData;
                $('collect-session-data').checked =
                    collect === true || collect === 'true' || collect === 1 || collect === '1';
                renderRows();
                renderModels();
            })
            .catch(function (e) {
                toast('Could not load settings: ' + e.message, 'error');
                state.rows = [];
                renderRows();
            });
    }

    function loadAll() {
        return Promise.all([loadUserSettings(), loadProviders()]);
    }

    document.addEventListener('DOMContentLoaded', function () {
        renderSnippet();

        $('save-models').addEventListener('click', saveModels);
        $('refresh-models').addEventListener('click', function () {
            busy($('refresh-models'), true);
            loadProviders().then(function () {
                busy($('refresh-models'), false);
                toast('Model catalog refreshed', 'ok');
            });
        });
        [$('smart-model'), $('fast-model')].forEach(function (input) {
            input.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    saveModels();
                }
            });
        });

        $('add-provider').addEventListener('click', function () {
            state.rows.push({provider: '', key: '', baseUrl: ''});
            renderRows();
            var inputs = $('key-rows').querySelectorAll('.row-provider');
            if (inputs.length) inputs[inputs.length - 1].focus();
        });
        $('save-keys').addEventListener('click', saveKeys);
        $('reload-all').addEventListener('click', function () {
            busy($('reload-all'), true);
            loadAll().then(function () {
                busy($('reload-all'), false);
                toast('Reloaded from server', 'ok');
            });
        });

        loadAll();
    });
})();