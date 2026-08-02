// UI helpers: status messages, password prompt modal, keychain manager.
import * as keychain from './keychain.js';

let statusEl;
let modalRoot;

export function init() {
  statusEl = document.getElementById('status');
  modalRoot = document.getElementById('modal-root');
}

export function setStatus(message, kind = 'info') {
  if (!statusEl) return;
  statusEl.textContent = message || '';
  statusEl.className = `ss-status ss-status-${kind}`;
}

export function toast(message, kind = 'info', ms = 3000) {
  const t = document.createElement('div');
  t.className = `ss-toast ss-toast-${kind}`;
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => {
    t.classList.add('ss-toast-out');
    setTimeout(() => t.remove(), 400);
  }, ms);
}

// Show a password prompt. Resolves to { label, password } or null if
// the user cancels.
export function promptPassword(message = 'Enter a password to try:') {
  return new Promise((resolve) => {
    const overlay = document.createElement('div');
    overlay.className = 'ss-modal-overlay';
    overlay.innerHTML = `
          <div class="ss-modal" role="dialog" aria-modal="true">
            <h2>Password required</h2>
            <p>${message}</p>
            <label>Label (optional)
              <input type="text" class="ss-input" id="pw-label" placeholder="e.g. Blog key">
            </label>
            <label>Password
              <input type="password" class="ss-input" id="pw-value" autofocus>
            </label>
            <div class="ss-modal-actions">
              <button class="ss-btn" id="pw-cancel">Cancel</button>
              <button class="ss-btn ss-btn-primary" id="pw-ok">Try key</button>
            </div>
          </div>`;
    modalRoot.appendChild(overlay);

    const labelInput = overlay.querySelector('#pw-label');
    const valueInput = overlay.querySelector('#pw-value');
    const cleanup = () => overlay.remove();

    overlay.querySelector('#pw-cancel').addEventListener('click', () => {
      cleanup();
      resolve(null);
    });
    const submit = () => {
      const password = valueInput.value;
      if (!password) {
        valueInput.focus();
        return;
      }
      cleanup();
      resolve({ label: labelInput.value.trim(), password });
    };
    overlay.querySelector('#pw-ok').addEventListener('click', submit);
    valueInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') submit();
      if (e.key === 'Escape') {
        cleanup();
        resolve(null);
      }
    });
    setTimeout(() => valueInput.focus(), 0);
  });
}

// Render the keychain manager into a given container.
export function renderKeychainManager(container) {
  const entries = keychain.getAll();
  container.innerHTML = '';

  const list = document.createElement('ul');
  list.className = 'ss-keylist';
  if (entries.length === 0) {
    const li = document.createElement('li');
    li.className = 'ss-key-empty';
    li.textContent = 'No keys stored yet.';
    list.appendChild(li);
  }
  entries.forEach((entry, i) => {
    const li = document.createElement('li');
    li.className = 'ss-key-item';
    const label = document.createElement('span');
    label.className = 'ss-key-label';
    label.textContent = entry.label;
    const controls = document.createElement('span');
    controls.className = 'ss-key-controls';

    const up = document.createElement('button');
    up.className = 'ss-btn ss-btn-small';
    up.textContent = '↑';
    up.title = 'Move up';
    up.disabled = i === 0;
    up.addEventListener('click', () => {
      keychain.moveUp(i);
      renderKeychainManager(container);
    });

    const del = document.createElement('button');
    del.className = 'ss-btn ss-btn-small ss-btn-danger';
    del.textContent = 'Remove';
    del.addEventListener('click', () => {
      keychain.remove(i);
      renderKeychainManager(container);
    });

    controls.appendChild(up);
    controls.appendChild(del);
    li.appendChild(label);
    li.appendChild(controls);
    list.appendChild(li);
  });
  container.appendChild(list);

  // Add-key form.
  const form = document.createElement('form');
  form.className = 'ss-key-add';
  form.innerHTML = `
        <input type="text" class="ss-input" id="ss-new-label" placeholder="Label">
        <input type="password" class="ss-input" id="ss-new-pw" placeholder="Password">
        <button type="submit" class="ss-btn ss-btn-primary">Add key</button>`;
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const label = form.querySelector('#ss-new-label').value.trim();
    const pw = form.querySelector('#ss-new-pw').value;
    if (!pw) return;
    keychain.add(label, pw);
    renderKeychainManager(container);
  });
  container.appendChild(form);

  // Import / export.
  const io = document.createElement('div');
  io.className = 'ss-key-io';
  const exportBtn = document.createElement('button');
  exportBtn.className = 'ss-btn ss-btn-small';
  exportBtn.textContent = 'Export JSON';
  exportBtn.addEventListener('click', () => {
    const json = keychain.exportJson();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'static-secrets-keychain.json';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
    toast('Exported keychain (contains plaintext passwords!)', 'warn');
  });
  const importBtn = document.createElement('button');
  importBtn.className = 'ss-btn ss-btn-small';
  importBtn.textContent = 'Import JSON';
  const importInput = document.createElement('input');
  importInput.type = 'file';
  importInput.accept = 'application/json';
  importInput.style.display = 'none';
  importInput.addEventListener('change', async () => {
    const file = importInput.files[0];
    if (!file) return;
    try {
      const text = await file.text();
      keychain.importJson(text);
      renderKeychainManager(container);
      toast('Keychain imported', 'info');
    } catch (e) {
      toast('Import failed: ' + e.message, 'error');
    }
  });
  importBtn.addEventListener('click', () => importInput.click());
  io.appendChild(exportBtn);
  io.appendChild(importBtn);
  io.appendChild(importInput);
  container.appendChild(io);
}
