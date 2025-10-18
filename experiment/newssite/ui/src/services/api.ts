/**
 * API Client
 * Core HTTP client for making API requests
 */

import { API_CONFIG } from '../config/api.config';

/**
 * HTTP request options
 */
interface RequestOptions extends RequestInit {
    params?: Record<string, string | number | boolean | undefined>;
}

/**
 * API Error class
 */
export class ApiError extends Error {
    constructor(
        message: string,
        public status: number,
        public statusText: string,
        public data?: unknown
    ) {
        super(message);
        this.name = 'ApiError';
    }
}

/**
 * Build URL with query parameters
 */
function buildUrl(endpoint: string, params?: Record<string, string | number | boolean | undefined>): string {
    // Handle relative URLs by combining base URL and endpoint
    const baseUrl = API_CONFIG.BASE_URL.startsWith('http') 
        ? API_CONFIG.BASE_URL 
        : window.location.origin + API_CONFIG.BASE_URL;
    
    const fullPath = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    const url = new URL(fullPath, baseUrl);

    if (params) {
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null) {
                url.searchParams.append(key, String(value));
            }
        });
    }

    return url.toString();
}

/**
 * Handle API response
 */
async function handleResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
        let errorData: unknown;
        try {
            errorData = await response.json();
        } catch {
            errorData = await response.text();
        }

        throw new ApiError(
            `API request failed: ${response.statusText}`,
            response.status,
            response.statusText,
            errorData
        );
    }

    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
        return response.json();
    }

    return response.text() as Promise<T>;
}

/**
 * Make HTTP request
 */
async function request<T>(
    endpoint: string,
    options: RequestOptions = {}
): Promise<T> {
    const { params, ...fetchOptions } = options;

    const url = buildUrl(endpoint, params);

    const config: RequestInit = {
        ...fetchOptions,
        headers: {
            'Content-Type': 'application/json',
            ...fetchOptions.headers,
        },
    };

    try {
        const response = await fetch(url, config);
        return handleResponse<T>(response);
    } catch (error) {
        if (error instanceof ApiError) {
            throw error;
        }

        // Handle network errors
        throw new ApiError(
            error instanceof Error ? error.message : 'Network request failed',
            0,
            'Network Error'
        );
    }
}

/**
 * API Client
 */
export const apiClient = {
    /**
     * GET request
     */
    get<T>(
        endpoint: string,
        params?: Record<string, string | number | boolean | undefined>,
        options?: RequestOptions
    ): Promise<T> {
        return request<T>(endpoint, {
            ...options,
            method: 'GET',
            params,
        });
    },

    /**
     * POST request
     */
    post<T>(
        endpoint: string,
        data?: unknown,
        options?: RequestOptions
    ): Promise<T> {
        return request<T>(endpoint, {
            ...options,
            method: 'POST',
            body: data ? JSON.stringify(data) : undefined,
        });
    },

    /**
     * PUT request
     */
    put<T>(
        endpoint: string,
        data?: unknown,
        options?: RequestOptions
    ): Promise<T> {
        return request<T>(endpoint, {
            ...options,
            method: 'PUT',
            body: data ? JSON.stringify(data) : undefined,
        });
    },

    /**
     * PATCH request
     */
    patch<T>(
        endpoint: string,
        data?: unknown,
        options?: RequestOptions
    ): Promise<T> {
        return request<T>(endpoint, {
            ...options,
            method: 'PATCH',
            body: data ? JSON.stringify(data) : undefined,
        });
    },

    /**
     * DELETE request
     */
    delete<T>(
        endpoint: string,
        options?: RequestOptions
    ): Promise<T> {
        return request<T>(endpoint, {
            ...options,
            method: 'DELETE',
        });
    },
};

export default apiClient;