/**
 * Local storage utility functions with type safety and error handling
 */

/**
 * Storage keys enum for type safety
 */
export enum StorageKey {
  THEME = 'theme',
  RECENT_SEARCHES = 'recent_searches',
  FAVORITES = 'favorites',
  READ_STORIES = 'read_stories',
  USER_PREFERENCES = 'user_preferences',
}

/**
 * Get an item from local storage
 */
export function getItem<T>(key: StorageKey): T | null {
  try {
    const item = localStorage.getItem(key);
    if (item === null) {
      return null;
    }
    return JSON.parse(item) as T;
  } catch (error) {
    console.error(`Error reading from localStorage (${key}):`, error);
    return null;
  }
}

/**
 * Set an item in local storage
 */
export function setItem<T>(key: StorageKey, value: T): boolean {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch (error) {
    console.error(`Error writing to localStorage (${key}):`, error);
    return false;
  }
}

/**
 * Remove an item from local storage
 */
export function removeItem(key: StorageKey): boolean {
  try {
    localStorage.removeItem(key);
    return true;
  } catch (error) {
    console.error(`Error removing from localStorage (${key}):`, error);
    return false;
  }
}

/**
 * Clear all items from local storage
 */
export function clear(): boolean {
  try {
    localStorage.clear();
    return true;
  } catch (error) {
    console.error('Error clearing localStorage:', error);
    return false;
  }
}

/**
 * Check if local storage is available
 */
export function isStorageAvailable(): boolean {
  try {
    const testKey = '__storage_test__';
    localStorage.setItem(testKey, 'test');
    localStorage.removeItem(testKey);
    return true;
  } catch {
    return false;
  }
}

/**
 * Get storage size in bytes
 */
export function getStorageSize(): number {
  let total = 0;
  for (const key in localStorage) {
    if (localStorage.hasOwnProperty(key)) {
      total += localStorage[key].length + key.length;
    }
  }
  return total;
}

/**
 * Add an item to an array in storage
 */
export function addToArray<T>(key: StorageKey, item: T, maxLength?: number): boolean {
  const array = getItem<T[]>(key) || [];
  array.unshift(item);
  
  if (maxLength && array.length > maxLength) {
    array.splice(maxLength);
  }
  
  return setItem(key, array);
}

/**
 * Remove an item from an array in storage
 */
export function removeFromArray<T>(
  key: StorageKey,
  predicate: (item: T) => boolean
): boolean {
  const array = getItem<T[]>(key) || [];
  const filtered = array.filter(item => !predicate(item));
  return setItem(key, filtered);
}

/**
 * Check if an item exists in an array in storage
 */
export function existsInArray<T>(
  key: StorageKey,
  predicate: (item: T) => boolean
): boolean {
  const array = getItem<T[]>(key) || [];
  return array.some(predicate);
}

/**
 * Update an item in storage with a partial update
 */
export function updateItem<T extends Record<string, any>>(
  key: StorageKey,
  updates: Partial<T>
): boolean {
  const current = getItem<T>(key);
  if (!current) {
    return false;
  }
  
  const updated = { ...current, ...updates };
  return setItem(key, updated);
}

/**
 * Get item with expiration
 */
export function getItemWithExpiry<T>(key: StorageKey): T | null {
  try {
    const itemStr = localStorage.getItem(key);
    if (!itemStr) {
      return null;
    }

    const item = JSON.parse(itemStr);
    const now = new Date().getTime();

    if (item.expiry && now > item.expiry) {
      localStorage.removeItem(key);
      return null;
    }

    return item.value as T;
  } catch (error) {
    console.error(`Error reading from localStorage with expiry (${key}):`, error);
    return null;
  }
}

/**
 * Set item with expiration (in milliseconds)
 */
export function setItemWithExpiry<T>(
  key: StorageKey,
  value: T,
  ttl: number
): boolean {
  try {
    const now = new Date().getTime();
    const item = {
      value,
      expiry: now + ttl,
    };
    localStorage.setItem(key, JSON.stringify(item));
    return true;
  } catch (error) {
    console.error(`Error writing to localStorage with expiry (${key}):`, error);
    return false;
  }
}