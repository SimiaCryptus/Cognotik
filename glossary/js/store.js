/**
 * Minimal observable store + persistence for user knowledge state.
 */

const KNOWN_KEY = 'ce.known';
const UNKNOWN_KEY = 'ce.unknown';

function loadSet(key) {
  try {
    const raw = localStorage.getItem(key);
    return new Set(raw ? JSON.parse(raw) : []);
  } catch {
    return new Set();
  }
}

function saveSet(key, set) {
  try {
    localStorage.setItem(key, JSON.stringify([...set]));
  } catch {
    /* ignore */
  }
}

export class Store {
  constructor() {
    this.state = {
      mode: 'explore',
      selectedId: null,
      search: '',
      filterDomain: '',
      filterDifficulty: '',
      known: loadSet(KNOWN_KEY),
      unknown: loadSet(UNKNOWN_KEY),
    };
    this._listeners = new Set();
  }

  subscribe(fn) {
    this._listeners.add(fn);
    return () => this._listeners.delete(fn);
  }

  _emit() {
    for (const fn of this._listeners) fn(this.state);
  }

  set(patch) {
    Object.assign(this.state, patch);
    this._emit();
  }

  markKnown(id) {
    this.state.known.add(id);
    this.state.unknown.delete(id);
    saveSet(KNOWN_KEY, this.state.known);
    saveSet(UNKNOWN_KEY, this.state.unknown);
    this._emit();
  }

  markUnknown(id) {
    this.state.unknown.add(id);
    this.state.known.delete(id);
    saveSet(KNOWN_KEY, this.state.known);
    saveSet(UNKNOWN_KEY, this.state.unknown);
    this._emit();
  }

  resetProgress() {
    this.state.known.clear();
    this.state.unknown.clear();
    saveSet(KNOWN_KEY, this.state.known);
    saveSet(UNKNOWN_KEY, this.state.unknown);
    this._emit();
  }
}
