/** Minimal i18n: a single `en` bundle plus interpolation. RTL-ready by CSS only. */
const bundles = {
    en: {
        readOnly: 'read-only',
        unsavedChanges: 'unsaved changes',
        explorer: 'Explorer',
        files: 'Files',
    },
};

let locale = 'en';

export function setLocale(next, bundle) {
    locale = next;
    if (bundle) bundles[next] = {...(bundles[next] || {}), ...bundle};
}

export function t(key, params = {}) {
    const template = bundles[locale]?.[key] ?? bundles.en[key] ?? key;
    return template.replace(/\{(\w+)\}/g, (_, name) =>
        (Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : `{${name}}`));
}
