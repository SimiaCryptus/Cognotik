// API_PROVIDERS and AVAILABLE_MODELS will be loaded dynamically from /apiProviders
let API_PROVIDERS = [];
let AVAILABLE_MODELS = {};

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        API_PROVIDERS,
        AVAILABLE_MODELS
    };
}