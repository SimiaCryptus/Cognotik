// localStorage-backed keychain of { label, password } entries.
// Ordered: most-recently-successful first for faster decryption.

const STORAGE_KEY = 'static-secrets:keychain';

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function save(entries) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
}

export function getAll() {
  return load();
}

export function getPasswords() {
  return load().map((e) => e.password);
}

export function add(label, password) {
  const entries = load();
  // Avoid exact duplicate passwords.
  if (entries.some((e) => e.password === password)) {
    return entries;
  }
  entries.push({
    label: label || `Key ${entries.length + 1}`,
    password,
  });
  save(entries);
  return entries;
}

export function remove(index) {
  const entries = load();
  if (index >= 0 && index < entries.length) {
    entries.splice(index, 1);
    save(entries);
  }
  return entries;
}

// Move the entry with a given password to the front.
export function promote(password) {
  const entries = load();
  const idx = entries.findIndex((e) => e.password === password);
  if (idx > 0) {
    const [entry] = entries.splice(idx, 1);
    entries.unshift(entry);
    save(entries);
  }
  return entries;
}

export function moveUp(index) {
  const entries = load();
  if (index > 0 && index < entries.length) {
    [entries[index - 1], entries[index]] = [entries[index], entries[index - 1]];
    save(entries);
  }
  return entries;
}

export function exportJson() {
  return JSON.stringify(load(), null, 2);
}

export function importJson(json) {
  const parsed = JSON.parse(json);
  if (!Array.isArray(parsed)) throw new Error('Expected a JSON array');
  const clean = parsed
    .filter((e) => e && typeof e.password === 'string')
    .map((e) => ({ label: String(e.label || 'Imported'), password: e.password }));
  save(clean);
  return clean;
}

export function clear() {
  save([]);
}
