/**
 * Text utility functions for formatting and manipulating text
 */

/**
 * Truncate text to a specified length with ellipsis
 */
export function truncate(text: string, maxLength: number): string {
  if (text.length <= maxLength) {
    return text;
  }
  return text.slice(0, maxLength).trim() + '...';
}

/**
 * Capitalize the first letter of a string
 */
export function capitalize(text: string): string {
  if (!text) return '';
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();
}

/**
 * Convert a string to title case
 */
export function toTitleCase(text: string): string {
  return text
    .toLowerCase()
    .split(' ')
    .map(word => capitalize(word))
    .join(' ');
}

/**
 * Pluralize a word based on count
 */
export function pluralize(count: number, singular: string, plural?: string): string {
  if (count === 1) {
    return singular;
  }
  return plural || `${singular}s`;
}

/**
 * Format a number with commas
 * e.g., 1234567 -> "1,234,567"
 */
export function formatNumber(num: number): string {
  return num.toLocaleString('en-US');
}

/**
 * Format a large number with abbreviations
 * e.g., 1234 -> "1.2K", 1234567 -> "1.2M"
 */
export function formatCompactNumber(num: number): string {
  if (num < 1000) {
    return num.toString();
  }
  
  if (num < 1000000) {
    return `${(num / 1000).toFixed(1)}K`;
  }
  
  return `${(num / 1000000).toFixed(1)}M`;
}

/**
 * Strip HTML tags from a string
 */
export function stripHtml(html: string): string {
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  return tmp.textContent || tmp.innerText || '';
}

/**
 * Escape HTML special characters
 */
export function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * Generate a slug from a string
 * e.g., "Hello World!" -> "hello-world"
 */
export function slugify(text: string): string {
  return text
    .toLowerCase()
    .trim()
    .replace(/[^\w\s-]/g, '')
    .replace(/[\s_-]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/**
 * Highlight search terms in text
 */
export function highlightText(text: string, query: string): string {
  if (!query.trim()) {
    return escapeHtml(text);
  }

  const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(`(${escapedQuery})`, 'gi');
  
  return escapeHtml(text).replace(
    regex,
    '<mark>$1</mark>'
  );
}

/**
 * Extract excerpt from text
 */
export function extractExcerpt(text: string, maxLength: number = 200): string {
  const stripped = stripHtml(text);
  return truncate(stripped, maxLength);
}

/**
 * Count words in text
 */
export function countWords(text: string): number {
  const stripped = stripHtml(text);
  return stripped.trim().split(/\s+/).filter(Boolean).length;
}

/**
 * Estimate reading time in minutes
 */
export function estimateReadingTime(text: string, wordsPerMinute: number = 200): number {
  const wordCount = countWords(text);
  return Math.ceil(wordCount / wordsPerMinute);
}