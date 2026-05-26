// HTTP service module
class HttpService {
    constructor(dependencies = {}) {
        this.fetch = dependencies.fetch || (typeof window !== 'undefined' ? window.fetch.bind(window) : null);
        if (!this.fetch) {
            throw new Error('Fetch API not available');
        }
    }

    async getUserSettings() {
        try {
           const response = await this.fetch('/userSettings/?format=json', {
                headers: {
                    'Accept': 'application/json'
                }
            });
            if (!response.ok) {
                // If endpoint doesn't exist, return empty settings
                if (response.status === 404) {
                    console.warn('[getUserSettings] Settings endpoint not found, returning empty settings');
                    return JSON.stringify({apiKeys: {}, apiBase: {}, localTools: []});
                }
                throw new Error(`Failed to get user settings: ${response.status}`);
            }
            const text = await response.text();
            // Validate that response is JSON
            try {
                const parsed = JSON.parse(text);
                // Transform the response to match expected format if needed
                if (parsed.apis && !parsed.apiKeys) {
                   // Preserve all top-level fields from the server response
                   // (e.g. collectSessionData, etc) and add the derived
                   // apiKeys/apiBase/localTools/configuredApis fields.
                   const transformed = Object.assign({}, parsed, {
                       apiKeys: {},
                       apiBase: {},
                       localTools: parsed.tools || [],
                       configuredApis: parsed.apis || [],
                       user: parsed.user || null
                   });
                   // Convert apis array to apiKeys / apiBase objects.
                   // Note: providers like Ollama use '-' as their key and
                   // AWS uses a JSON blob, so we accept any non-empty key.
                   if (Array.isArray(parsed.apis)) {
                       parsed.apis.forEach(api => {
                           if (!api || !api.provider) return;
                           if (api.key !== undefined && api.key !== null && String(api.key) !== '') {
                               transformed.apiKeys[api.provider] = api.key;
                           }
                           if (api.baseUrl) {
                               transformed.apiBase[api.provider] = api.baseUrl;
                           }
                       });
                   }
                   return JSON.stringify(transformed);
                }
                return text;
            } catch (parseError) {
                console.warn('[getUserSettings] Response is not valid JSON, returning empty settings');
                return JSON.stringify({apiKeys: {}, apiBase: {}, localTools: []});
            }
        } catch (error) {
            console.error('[getUserSettings] Error:', error);
            // Return empty settings instead of throwing to prevent app crash
            console.warn('[getUserSettings] Returning empty settings due to error');
            return JSON.stringify({apiKeys: {}, apiBase: {}, localTools: []});
        }
    }

    async saveUserSettings(settings) {
        const response = await this.fetch('/userSettings/', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
                action: 'save',
                settings: JSON.stringify(settings)
            })
        });

        if (!response.ok) {
            throw new Error(`Failed to save user settings: ${response.status}`);
        }
        return response;
    }

    async saveSessionSettings(sessionId, settings) {
        const response = await this.fetch('/taskChat/settings', {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
                sessionId: sessionId,
                action: 'save',
                settings: JSON.stringify(settings),
            })
        });

        if (!response.ok) {
            throw new Error(`Failed to save session settings: ${response.status}`);
        }
        return response;
    }

    async saveChatSettings(sessionId, settings) {
        const response = await this.fetch('/chat/settings', {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
                sessionId: sessionId,
                action: 'save',
                settings: JSON.stringify(settings)
            })
        });

        if (!response.ok) {
            throw new Error(`Failed to save chat settings: ${response.status}`);
        }
        return response;
    }

    async getApiProviders() {
        try {
            const response = await this.fetch('/apiProviders/', {
                headers: {
                    'Accept': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`Failed to get API providers: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('[getApiProviders] Error:', error);
            throw error;
        }
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {HttpService};
}