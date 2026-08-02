// Tracks the current target in the ?url= query parameter and hooks
// popstate for back/forward navigation.

const PARAM = 'url';

export function getTargetUrl() {
  const params = new URLSearchParams(location.search);
  return params.get(PARAM);
}

// Build a proxy href for a given target URL (used for link rewriting).
export function proxyHref(targetUrl) {
  const url = new URL(location.href);
  url.search = '';
  url.hash = '';
  url.searchParams.set(PARAM, targetUrl);
  return url.href;
}

// Navigate to a target URL, pushing a new history entry.
export function navigate(targetUrl) {
  const href = proxyHref(targetUrl);
  history.pushState({ url: targetUrl }, '', href);
}

// Register a callback invoked whenever the target URL changes
// (via navigate() -> we call it directly, or via popstate).
export function onNavigate(callback) {
  window.addEventListener('popstate', () => {
    callback(getTargetUrl());
  });
}

// Intercept clicks on same-app links (?url=) so they use pushState.
export function interceptAppLinks(callback) {
  document.addEventListener('click', (e) => {
    const a = e.target.closest && e.target.closest('a[href]');
    if (!a) return;
    let href;
    try {
      href = new URL(a.getAttribute('href'), location.href);
    } catch {
      return;
    }
    // Only intercept links that point back through our proxy.
    if (
      href.origin === location.origin &&
      href.pathname === location.pathname &&
      href.searchParams.has(PARAM)
    ) {
      e.preventDefault();
      const target = href.searchParams.get(PARAM);
      navigate(target);
      callback(target);
    }
  });
}
