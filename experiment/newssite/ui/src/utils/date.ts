/**
 * Date utility functions for formatting and manipulating dates
 */

/**
 * Format a date string or Date object to a relative time string
 * e.g., "2 hours ago", "3 days ago"
 */
export function formatRelativeTime(date: string | Date): string {
    const now = new Date();
    const then = typeof date === 'string' ? new Date(date) : date;
    const diffInSeconds = Math.floor((now.getTime() - then.getTime()) / 1000);

    if (diffInSeconds < 60) {
        return 'just now';
    }

    const diffInMinutes = Math.floor(diffInSeconds / 60);
    if (diffInMinutes < 60) {
        return `${diffInMinutes} minute${diffInMinutes !== 1 ? 's' : ''} ago`;
    }

    const diffInHours = Math.floor(diffInMinutes / 60);
    if (diffInHours < 24) {
        return `${diffInHours} hour${diffInHours !== 1 ? 's' : ''} ago`;
    }

    const diffInDays = Math.floor(diffInHours / 24);
    if (diffInDays < 30) {
        return `${diffInDays} day${diffInDays !== 1 ? 's' : ''} ago`;
    }

    const diffInMonths = Math.floor(diffInDays / 30);
    if (diffInMonths < 12) {
        return `${diffInMonths} month${diffInMonths !== 1 ? 's' : ''} ago`;
    }

    const diffInYears = Math.floor(diffInMonths / 12);
    return `${diffInYears} year${diffInYears !== 1 ? 's' : ''} ago`;
}

export const formatTimeAgo = formatRelativeTime;

/**
 * Format a date to a readable string
 * e.g., "January 15, 2024"
 */
export function formatDate(date: string | Date): string {
    const dateObj = typeof date === 'string' ? new Date(date) : date;
    return dateObj.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
    });
}

/**
 * Format a date to include time
 * e.g., "January 15, 2024 at 10:30 AM"
 */
export function formatDateTime(date: string | Date): string {
    const dateObj = typeof date === 'string' ? new Date(date) : date;
    return dateObj.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
    });
}

/**
 * Format a date to ISO 8601 string for API requests
 */
export function formatISO(date: Date): string {
    return date.toISOString();
}

/**
 * Parse an ISO 8601 date string to a Date object
 */
export function parseISO(dateString: string): Date {
    return new Date(dateString);
}

/**
 * Check if a date is valid
 */
export function isValidDate(date: any): boolean {
    return date instanceof Date && !isNaN(date.getTime());
}

/**
 * Get the start of today (00:00:00)
 */
export function getStartOfToday(): Date {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    return date;
}

/**
 * Get the end of today (23:59:59)
 */
export function getEndOfToday(): Date {
    const date = new Date();
    date.setHours(23, 59, 59, 999);
    return date;
}

/**
 * Subtract days from a date
 */
export function subtractDays(date: Date, days: number): Date {
    const result = new Date(date);
    result.setDate(result.getDate() - days);
    return result;
}

/**
 * Add days to a date
 */
export function addDays(date: Date, days: number): Date {
    const result = new Date(date);
    result.setDate(result.getDate() + days);
    return result;
}