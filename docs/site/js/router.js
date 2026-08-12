/**
   * Minimal hash router.
   *
   *   #/                        -> home
   *   #/<section>               -> section index
   *   #/<section>/<slug>        -> page
   */

  export function parseRoute(hash = window.location.hash) {
    const parts = String(hash || '')
      .replace(/^#\/?/, '')
      .split('/')
      .filter(Boolean)
      .map(decodeURIComponent);

    if (parts.length === 0) return { view: 'home' };
    if (parts.length === 1) return { view: 'section', section: parts[0] };
    return { view: 'page', section: parts[0], slug: parts.slice(1).join('/') };
  }

  export function navigate(hash) {
    if (window.location.hash === hash) {
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    } else {
      window.location.hash = hash;
    }
  }

  export function onRouteChange(handler) {
    window.addEventListener('hashchange', () => handler(parseRoute()));
    handler(parseRoute());
  }