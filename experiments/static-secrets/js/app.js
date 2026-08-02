// Bootstrap + top-level wiring.
import * as router from './router.js';
import * as fetcher from './fetcher.js';
import * as crypto from './crypto.js';
import * as keychain from './keychain.js';
import * as renderer from './renderer.js';
import * as ui from './ui.js';

const state = {
  currentUrl: null,
  busy: false,
};

let contentEl;

function showContent(show) {
  const welcome = document.getElementById('welcome');
  contentEl.style.display = show ? 'block' : 'none';
  if (welcome) welcome.style.display = show ? 'none' : 'block';
}

async function loadUrl(targetUrl) {
  if (!targetUrl) {
    showContent(false);
    ui.setStatus('Enter a URL to load an encrypted blob.', 'info');
    return;
  }
  if (state.busy) return;
  state.busy = true;
  state.currentUrl = targetUrl;

  try {
    showContent(true);
    ui.setStatus(`Fetching ${targetUrl}…`, 'info');
    const buffer = await fetcher.fetchBlob(targetUrl);

    ui.setStatus('Parsing blob…', 'info');
    let parsed;
    try {
      parsed = crypto.parseBlob(buffer);
    } catch (e) {
      ui.setStatus('Malformed blob: ' + e.message, 'error');
      state.busy = false;
      return;
    }

    // Try stored keys, then prompt as needed.
    let result = null;
    ui.setStatus('Attempting decryption with stored keys…', 'decrypting');
    const passwords = keychain.getPasswords();
    result = await crypto.tryAllKeys(parsed, passwords, (i) => {
      ui.setStatus(`Trying key ${i + 1}/${passwords.length}…`, 'decrypting');
    });

    while (!result) {
      const entry = await ui.promptPassword('No stored key worked. Enter a password to try:');
      if (!entry) {
        ui.setStatus('Decryption cancelled.', 'info');
        state.busy = false;
        return;
      }
      ui.setStatus('Trying entered password…', 'decrypting');
      const inner = await crypto.tryDecrypt(parsed, entry.password);
      if (inner) {
        keychain.add(entry.label, entry.password);
        result = { inner, password: entry.password };
      } else {
        console.warn('Decrypt failed for entered password.', {
          version: parsed.version,
          kdfId: parsed.kdfId,
          iterations: parsed.iterations,
          recipients: parsed.recipients?.length,
        });
        ui.toast('That password did not work.', 'error');
      }
    }

    // Promote successful key to the front of the keychain.
    keychain.promote(result.password);

    ui.setStatus('Rendering…', 'rendering');
    renderer.render(contentEl, result.inner, {
      baseUrl: targetUrl,
      proxyBase: router.proxyHref,
    });
    ui.setStatus(`Loaded (${result.inner.contentTypeName})`, 'info');
  } catch (e) {
    ui.setStatus('Error: ' + e.message, 'error');
    ui.toast(e.message, 'error');
  } finally {
    state.busy = false;
  }
}

function wireControls() {
  const form = document.getElementById('url-form');
  const input = document.getElementById('url-input');
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const url = input.value.trim();
    if (!url) return;
    router.navigate(url);
    loadUrl(url);
  });

  // Keychain manager toggle.
  const kcToggle = document.getElementById('keychain-toggle');
  const kcPanel = document.getElementById('keychain-panel');
  kcToggle.addEventListener('click', () => {
    const open = kcPanel.classList.toggle('open');
    if (open) ui.renderKeychainManager(kcPanel);
  });
}

async function main() {
  contentEl = document.getElementById('content');
  ui.init();
  wireControls();

  router.onNavigate((url) => {
    const input = document.getElementById('url-input');
    if (input) input.value = url || '';
    loadUrl(url);
  });
  router.interceptAppLinks(() => {
    /* handled by onNavigate path */
  });

  const initial = router.getTargetUrl();
  const input = document.getElementById('url-input');
  if (initial && input) input.value = initial;
  loadUrl(initial);
}

main();
